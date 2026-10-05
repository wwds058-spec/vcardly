import Foundation

/// What the reader found in one card. Phones are ordered mobile-first with fax numbers removed.
struct ParsedVCard: Equatable {
    var fullName = ""
    var jobTitle = ""
    var company = ""
    var phones: [String] = []
    var emails: [String] = []
    var website = ""
    var address = ""
    var notes = ""
    var categories: [String] = []
}

/// Tolerant vCard reader (2.1, 3.0, 4.0 flavours), a port of Android's VCardParser: unfolds lines, handles quoted-printable
/// and groups, ignores what it does not understand (photos, birthdays, X- fields). Never throws on bad input.
enum VCardParser {
    static let maxCards = 5000

    private struct Prop {
        let name: String
        let types: Set<String>
        let value: String
    }

    static func parse(_ input: String) -> [ParsedVCard] {
        var text = input
        if text.hasPrefix("\u{FEFF}") { text.removeFirst() }
        var cards: [ParsedVCard] = []
        var props: [Prop]?
        for line in unfold(text) {
            let upper = line.trimmed.uppercased()
            if upper == "BEGIN:VCARD" {
                props = []
            } else if upper == "END:VCARD" {
                if let p = props { cards.append(build(p)) }
                props = nil
                if cards.count >= maxCards { break }
            } else if props != nil, let p = parseProp(line) {
                props?.append(p)
            }
        }
        return cards.filter { $0 != ParsedVCard() }
    }

    /// RFC folding (continuation lines start with a space or tab) plus quoted-printable soft breaks (line ends with "=").
    private static func unfold(_ text: String) -> [String] {
        let raw = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        var unfolded: [String] = []
        for line in raw {
            if (line.hasPrefix(" ") || line.hasPrefix("\t")), !unfolded.isEmpty {
                unfolded[unfolded.count - 1] += String(line.dropFirst())
            } else {
                unfolded.append(line)
            }
        }
        var joined: [String] = []
        var i = 0
        while i < unfolded.count {
            var line = unfolded[i]
            let isQP = line.range(of: "QUOTED-PRINTABLE", options: .caseInsensitive) != nil
            while isQP && line.hasSuffix("=") && i + 1 < unfolded.count {
                i += 1
                line = String(line.dropLast()) + unfolded[i]
            }
            joined.append(line)
            i += 1
        }
        return joined
    }

    private static func parseProp(_ line: String) -> Prop? {
        // Split "NAME;PARAMS:VALUE" at the first colon outside double quotes.
        var inQuotes = false
        var colon: String.Index?
        for idx in line.indices {
            let ch = line[idx]
            if ch == "\"" { inQuotes.toggle() }
            if ch == ":" && !inQuotes { colon = idx; break }
        }
        guard let colon, colon > line.startIndex else { return nil }
        let head = String(line[..<colon])
        var value = String(line[line.index(after: colon)...])
        let parts = head.split(separator: ";", omittingEmptySubsequences: false).map(String.init)
        let name = (parts[0].split(separator: ".").last.map(String.init) ?? parts[0]).trimmed.uppercased() // drop "item1." groups
        var types = Set<String>()
        var qp = false
        var encoding = String.Encoding.utf8
        for param in parts.dropFirst() {
            if let eq = param.firstIndex(of: "=") {
                let key = String(param[..<eq]).trimmed.uppercased()
                let v = String(param[param.index(after: eq)...]).trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                switch key {
                case "TYPE": v.split(separator: ",").forEach { types.insert(String($0).trimmed.uppercased()) }
                case "ENCODING": if v.caseInsensitiveCompare("QUOTED-PRINTABLE") == .orderedSame || v.caseInsensitiveCompare("Q") == .orderedSame { qp = true }
                case "CHARSET": encoding = charset(v) ?? .utf8
                default: break
                }
            } else {
                // Bare v2.1 parameter such as TEL;CELL;VOICE
                let bare = param.trimmed.uppercased()
                if bare == "QUOTED-PRINTABLE" { qp = true } else if !bare.isEmpty { types.insert(bare) }
            }
        }
        if qp { value = decodeQuotedPrintable(value, encoding) }
        return Prop(name: name, types: types, value: value)
    }

    private static func charset(_ name: String) -> String.Encoding? {
        let cf = CFStringConvertIANACharSetNameToEncoding(name as CFString)
        guard cf != kCFStringEncodingInvalidId else { return nil }
        return String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(cf))
    }

    private static func build(_ props: [Prop]) -> ParsedVCard {
        func first(_ n: String) -> Prop? { props.first { $0.name == n } }

        let formatted = first("FN").map { unescape($0.value).trimmed } ?? ""
        let structured = first("N").map { p -> String in
            let c = splitUnescaped(p.value, ";").map(\.trimmed)
            func at(_ i: Int) -> String { i < c.count ? c[i] : "" }
            // family;given;additional;prefix;suffix
            return [at(3), at(1), at(2), at(0), at(4)].filter { !$0.isEmpty }.joined(separator: " ")
        } ?? ""
        let company = first("ORG").flatMap { splitUnescaped($0.value, ";").first { !$0.trimmed.isEmpty } } ?? ""

        var seenPhones = Set<String>()
        let phones = props.filter { $0.name == "TEL" && !$0.types.contains("FAX") }
            .enumerated()
            .sorted { a, b in
                let ra = a.element.types.contains("CELL") || a.element.types.contains("MOBILE") ? 0 : 1
                let rb = b.element.types.contains("CELL") || b.element.types.contains("MOBILE") ? 0 : 1
                return ra != rb ? ra < rb : a.offset < b.offset // stable, like Kotlin's sortedBy
            }
            .map { p -> String in
                var v = p.element.value.trimmed
                for prefix in ["tel:", "TEL:"] where v.hasPrefix(prefix) { v = String(v.dropFirst(prefix.count)) }
                return v.trimmed
            }
            .filter { !$0.isEmpty && seenPhones.insert($0).inserted }

        var seenEmails = Set<String>()
        let emails = props.filter { $0.name == "EMAIL" }
            .map { p -> String in
                var v = p.value.trimmed
                if v.lowercased().hasPrefix("mailto:") { v = String(v.dropFirst(7)) }
                return v.trimmed
            }
            .filter { !$0.isEmpty && seenEmails.insert($0.lowercased()).inserted }

        let address = props.first { $0.name == "ADR" }.map { p in
            splitUnescaped(p.value, ";").map(\.trimmed).filter { !$0.isEmpty }.joined(separator: ", ")
        } ?? ""

        var seenCats = Set<String>()
        let categories = props.filter { $0.name == "CATEGORIES" }
            .flatMap { splitUnescaped($0.value, ",") }.map(\.trimmed)
            .filter { !$0.isEmpty && seenCats.insert($0.lowercased()).inserted }

        return ParsedVCard(
            fullName: formatted.isEmpty ? structured : formatted,
            jobTitle: first("TITLE").map { unescape($0.value).trimmed } ?? "",
            company: company,
            phones: phones,
            emails: emails,
            website: first("URL")?.value.trimmed ?? "",
            address: address,
            notes: first("NOTE").map { unescape($0.value).trimmed } ?? "",
            categories: categories)
    }

    /// Splits on `sep` unless escaped with a backslash, then unescapes each piece.
    static func splitUnescaped(_ value: String, _ sep: Character) -> [String] {
        var parts: [String] = []
        var cur = ""
        var it = Array(value).makeIterator()
        while let ch = it.next() {
            if ch == "\\", let n = it.next() {
                cur.append(ch); cur.append(n)
            } else if ch == sep {
                parts.append(unescape(cur)); cur = ""
            } else {
                cur.append(ch)
            }
        }
        parts.append(unescape(cur))
        return parts
    }

    static func unescape(_ value: String) -> String {
        var out = ""
        var it = Array(value).makeIterator()
        while let ch = it.next() {
            if ch == "\\", let n = it.next() {
                out.append(n == "n" || n == "N" ? "\n" : n) // \\ \; \,
            } else {
                out.append(ch)
            }
        }
        return out
    }

    static func decodeQuotedPrintable(_ value: String, _ encoding: String.Encoding) -> String {
        var bytes: [UInt8] = []
        let chars = Array(value)
        var i = 0
        while i < chars.count {
            if chars[i] == "=", i + 2 < chars.count, let b = UInt8(String(chars[(i + 1)...(i + 2)]), radix: 16) {
                bytes.append(b); i += 3
            } else {
                bytes.append(contentsOf: Array(String(chars[i]).data(using: encoding) ?? Data()))
                i += 1
            }
        }
        return String(data: Data(bytes), encoding: encoding) ?? String(decoding: bytes, as: UTF8.self)
    }
}

enum ImportStatus: Equatable { case new, duplicate, unusable }

/// One card from a file, ready to import. `contact` is nil only when the card is unusable.
struct ImportCandidate: Identifiable, Equatable {
    let index: Int
    let status: ImportStatus
    let contact: Contact?
    let tags: [String]
    var id: Int { index }
}

/// Port of Android's VCardImporter: turns parsed cards into contacts without losing data. A value the form would reject
/// (an odd phone format, a long note) is kept in the notes instead of being dropped. Duplicates are detected against
/// existing contacts and against earlier cards in the same file.
enum VCardImporter {
    static func prepare(_ parsed: [ParsedVCard], existing: [Contact]) -> [ImportCandidate] {
        var accepted: [Contact] = []
        return parsed.enumerated().map { index, p in
            guard let contact = toContact(p) else { return ImportCandidate(index: index, status: .unusable, contact: nil, tags: []) }
            if DuplicateDetector.isDuplicate(contact, existing + accepted) {
                return ImportCandidate(index: index, status: .duplicate, contact: contact, tags: p.categories)
            }
            accepted.append(contact)
            return ImportCandidate(index: index, status: .new, contact: contact, tags: p.categories)
        }
    }

    static func toContact(_ p: ParsedVCard) -> Contact? {
        var name = p.fullName.trimmed
        if name.isEmpty { name = p.company.trimmed }
        if name.isEmpty, let email = p.emails.first { name = String(email.split(separator: "@").first ?? "") }
        guard !name.trimmed.isEmpty else { return nil }

        var overflow: [String] = []
        func fit(_ value: String, _ max: Int) -> String {
            let v = value.trimmed
            if v.count <= max { return v }
            overflow.append(v)
            return String(v.prefix(max))
        }
        var phone = p.phones.count > 0 ? p.phones[0] : ""
        var phoneAlt = p.phones.count > 1 ? p.phones[1] : ""
        var email = p.emails.count > 0 ? p.emails[0] : ""
        var emailAlt = p.emails.count > 1 ? p.emails[1] : ""
        var website = p.website.trimmed

        // Keep anything the form would reject, in notes, so nothing silently disappears.
        let bad = ContactValidator.validate(Contact(fullName: name, phone: phone, phoneAlt: phoneAlt, email: email, emailAlt: emailAlt, website: website))
        func rescue(_ field: ContactField, _ value: String) -> String {
            if bad[field] != nil && !value.trimmed.isEmpty { overflow.append(value); return "" }
            return value
        }
        phone = rescue(.phone, phone); phoneAlt = rescue(.phoneAlt, phoneAlt)
        email = rescue(.email, email); emailAlt = rescue(.emailAlt, emailAlt)
        website = rescue(.website, website)
        overflow += p.phones.dropFirst(2)
        overflow += p.emails.dropFirst(2)

        // Fit the free-text fields first so anything cut off also lands in the notes.
        let jobTitle = fit(p.jobTitle, ContactValidator.maxShort)
        let company = fit(p.company, ContactValidator.maxShort)
        let address = fit(p.address, ContactValidator.maxAddress)

        var seen = Set<String>()
        let extra = overflow.filter { seen.insert($0).inserted }.joined(separator: "\n")
        let notes = String([p.notes.trimmed, extra].filter { !$0.isEmpty }.joined(separator: "\n").prefix(ContactValidator.maxNotes))

        return Contact(fullName: String(name.prefix(ContactValidator.maxName)), jobTitle: jobTitle,
                       company: company, phone: phone, phoneAlt: phoneAlt, email: email,
                       emailAlt: emailAlt, website: website, address: address, notes: notes,
                       source: .importFile)
    }
}
