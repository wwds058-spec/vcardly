import Foundation

/// Fields a user can choose to share. Private notes are deliberately not shareable.
enum ShareField: String, CaseIterable { case name, jobTitle, company, phone, phoneAlt, email, emailAlt, website, address }

/// The values of a card that can be shared (the user's own card or a saved contact). Same rules as Android's ShareCard.
struct ShareCard: Equatable {
    var values: [ShareField: String]

    /// Only fields that have a value are offered.
    var available: [ShareField] { ShareField.allCases.filter { !(values[$0] ?? "").trimmed.isEmpty } }

    /// The usual business-card fields that exist. The name is always included (a vCard needs it).
    var defaultSelection: Set<ShareField> {
        Set([ShareField.name, .jobTitle, .company, .phone, .email, .website].filter { available.contains($0) || $0 == .name })
    }

    /// vCard 3.0 text containing only the selected fields (plus the name).
    func vCard(_ selected: Set<ShareField>) -> String {
        func v(_ f: ShareField) -> String { (selected.contains(f) || f == .name) ? (values[f] ?? "").trimmed : "" }
        return VCardWriter.write(
            fullName: v(.name), jobTitle: v(.jobTitle), company: v(.company),
            phones: [(v(.phone), "CELL"), (v(.phoneAlt), "WORK")].filter { !$0.0.isEmpty },
            emails: [v(.email), v(.emailAlt)].filter { !$0.isEmpty },
            website: v(.website), address: v(.address)
        )
    }

    static func of(_ c: Contact) -> ShareCard {
        ShareCard(values: [.name: c.fullName, .jobTitle: c.jobTitle, .company: c.company, .phone: c.phone, .phoneAlt: c.phoneAlt,
                           .email: c.email, .emailAlt: c.emailAlt, .website: c.website, .address: c.address])
    }

    static func of(_ c: MyCard) -> ShareCard {
        ShareCard(values: [.name: c.fullName, .jobTitle: c.jobTitle, .company: c.company, .phone: c.phone, .phoneAlt: c.phoneAlt,
                           .email: c.email, .emailAlt: c.emailAlt, .website: c.website, .address: c.address])
    }
}

/// Writes vCard 3.0 with CRLF line ends and 75-octet folding (never splitting a character). Port of Android's VCardWriter.
enum VCardWriter {
    static func write(fullName: String, jobTitle: String = "", company: String = "", phones: [(String, String)] = [],
                      emails: [String] = [], website: String = "", address: String = "", notes: String = "") -> String {
        var lines = ["BEGIN:VCARD", "VERSION:3.0", "FN:\(text(fullName))", "N:\(structuredName(fullName))"]
        if !company.trimmed.isEmpty { lines.append("ORG:\(text(company))") }
        if !jobTitle.trimmed.isEmpty { lines.append("TITLE:\(text(jobTitle))") }
        for (number, type) in phones where !number.trimmed.isEmpty { lines.append("TEL;TYPE=\(type):\(plain(number))") }
        for e in emails where !e.trimmed.isEmpty { lines.append("EMAIL;TYPE=INTERNET:\(plain(e))") }
        if !website.trimmed.isEmpty { lines.append("URL:\(plain(website))") }
        // ADR = PO box;extended;street;city;region;postal code;country. Free text goes in "street".
        if !address.trimmed.isEmpty { lines.append("ADR;TYPE=WORK:;;\(text(address));;;;") }
        if !notes.trimmed.isEmpty { lines.append("NOTE:\(text(notes))") }
        lines.append("END:VCARD")
        return lines.map { fold($0) + "\r\n" }.joined()
    }

    /// Last word = family name, the rest = given names.
    static func structuredName(_ fullName: String) -> String {
        let words = fullName.trimmed.split(whereSeparator: \.isWhitespace).map(String.init)
        switch words.count {
        case 0: return ";;;;"
        case 1: return ";\(text(words[0]));;;"
        default: return "\(text(words.last!));\(text(words.dropLast().joined(separator: " ")));;;"
        }
    }

    /// TEXT value: escape backslash, semicolon, comma and newlines.
    static func text(_ value: String) -> String {
        value.trimmed
            .replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: ";", with: "\\;").replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\r\n", with: "\\n").replacingOccurrences(of: "\n", with: "\\n").replacingOccurrences(of: "\r", with: "\\n")
    }

    private static func plain(_ value: String) -> String {
        value.trimmed.components(separatedBy: CharacterSet.newlines).filter { !$0.isEmpty }.joined(separator: " ")
    }

    static func fold(_ line: String) -> String {
        guard line.utf8.count > 75 else { return line }
        var out = ""
        var octets = 0
        var limit = 75
        for ch in line {
            let size = String(ch).utf8.count
            if octets + size > limit {
                out += "\r\n "
                octets = 0
                limit = 74 // the leading space counts toward the 75
            }
            out.append(ch)
            octets += size
        }
        return out
    }
}
