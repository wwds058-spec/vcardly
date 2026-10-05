import Foundation

/// One recognised line of text. `top` and `height` are geometry used to guess prominence (larger = more prominent).
struct OcrLine: Equatable {
    var text: String
    var top: Int = 0
    var height: Int = 0
}

/// Best-effort guesses from OCR. Nothing here is trusted: every value pre-fills an editable form the user reviews.
struct ParsedCard: Equatable {
    var fullName = "", jobTitle = "", company = ""
    var phone = "", phoneAlt = "", email = "", emailAlt = "", website = "", address = ""
    var unmatched: [String] = []

    /// How many fields were filled (for "We found N details").
    var foundCount: Int { [fullName, jobTitle, company, phone, phoneAlt, email, emailAlt, website, address].filter { !$0.isEmpty }.count }
}

/// Port of Android's BusinessCardParser (same rules and order). Pure, no logging, no storage: the text is personal data.
/// Unambiguous patterns first (email, phone, website), then keyword classes (title, company, address), then the name.
enum BusinessCardParser {
    private final class Work {
        var text: String
        let top: Int
        let height: Int
        var used = false
        init(_ text: String, _ top: Int, _ height: Int) { self.text = text; self.top = top; self.height = height }
    }

    private static func rx(_ p: String, _ ci: Bool = false) -> NSRegularExpression {
        try! NSRegularExpression(pattern: p, options: ci ? [.caseInsensitive] : [])
    }

    private static let email = rx("[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.\\p{L}{2,}")
    private static let tlds = "com|net|org|edu|gov|io|co|in|biz|info|app|dev|ai|me|us|uk|de|fr|au|ca|ae|sg|tech|online|store|xyz|pro|tv|cc|eu|asia"
    private static let url = rx("(?<![@\\w.])(?:https?://)?(?:www\\.)?(?:[\\p{L}\\p{N}][\\p{L}\\p{N}\\-]*\\.)+(?:\(tlds))\\b(?:/[^\\s,;]*)?", true)
    private static let phone = rx("(?<!\\d)\\+?\\(?\\d[\\d\\s().\\-]{5,}\\d(?!\\d)")
    private static let segmentSplit = rx("\\s+(?=(?:tel|phone|ph|mob|mobile|cell|fax|f|t|m|e|w|o|d)\\s*:)", true)
    private static let labelPrefix = rx(
        "^(?:(?:e-?mail|email|web(?:site)?|url|telephone|tel|phone|ph|mobile|mob|cell|fax|direct|office|address|addr)\\s*[:.\\-–]?\\s+|(?:[a-z])\\s*:\\s*)", true)
    private static let faxLine = rx("^\\s*(?:fax|f)\\b\\s*[:.\\-]?", true)
    private static let mobileLine = rx("^\\s*(?:mobile|mob|cell|m)\\b\\s*[:.\\-]?", true)
    private static let title = rx(
        "\\b(?:manager|director|ceo|cto|cfo|coo|cmo|founder|co-?founder|engineer|developer|president|vice|vp|head|lead|" +
            "consultant|officer|partner|associate|analyst|designer|executive|sales|marketing|owner|proprietor|principal|" +
            "architect|specialist|supervisor|chairman|chairperson|md|advisor|adviser|coordinator|administrator|" +
            "attorney|lawyer|doctor|professor|scientist|researcher|recruiter|accountant)\\b", true)
    private static let company = rx(
        "\\b(?:pvt|private|ltd|limited|llc|llp|inc|incorporated|corp|corporation|co|company|gmbh|plc|technologies|technology|" +
            "solutions|systems|group|industries|enterprises|associates|studio|studios|labs|consulting|services|" +
            "software|holdings|ventures|partners|agency|foundation|university|institute|bank)\\b\\.?", true)
    private static let addressWords = rx(
        "\\b(?:street|st|road|rd|avenue|ave|lane|ln|floor|flr|suite|ste|block|plot|nagar|colony|sector|phase|building|" +
            "bldg|tower|plaza|marg|chowk|highway|hwy|boulevard|blvd|city|district|po box|p\\.o\\.)\\b\\.?", true)
    private static let postal = rx("(?<!\\d)\\d{5,6}(?!\\d)")
    private static let nameChars = rx("^[\\p{L}][\\p{L}.'\\-\\s]*$")
    private static let trimJunk = rx("^[\\s,;:|•·\\-–—/]+|[\\s,;:|•·\\-–—/]+$")
    private static let spaces = rx("\\s+")
    private static let genericMailHosts: Set<String> = ["gmail", "yahoo", "outlook", "hotmail", "icloud", "proton", "protonmail", "live", "aol", "rediffmail"]

    static func parse(_ lines: [OcrLine]) -> ParsedCard {
        let work: [Work] = lines.flatMap { l -> [Work] in
            split(l.text.trimmed).map { Work(spaces.replacing($0, with: " ").trimmed, l.top, l.height) }
        }.filter { !$0.text.isEmpty }
        guard !work.isEmpty else { return ParsedCard() }

        let emails = extract(work, email)
        let websites = extract(work, url).sorted { a, b in rank(a) < rank(b) }
        let phones = extractPhones(work)

        for w in work {
            w.text = trimJunk.replacing(labelPrefix.replacing(w.text, with: ""), with: "")
            if !w.text.contains(where: { $0.isLetter || $0.isNumber }) { w.used = true }
        }
        func free() -> [Work] { work.filter { !$0.used } }

        let titleLine = free().first { title.matches($0.text) }
        titleLine?.used = true

        var companyLine = free().first { company.matches($0.text) }
        if companyLine == nil {
            let labels = (emails.map { String($0.split(separator: "@").last ?? "") } + websites)
                .map(domainLabel).filter { $0.count >= 3 && !genericMailHosts.contains($0) }
            for label in labels {
                if let hit = free().first(where: { $0.text.lowercased().contains(label) && $0.text.contains(where: \.isLetter) }) {
                    companyLine = hit
                    break
                }
            }
        }
        companyLine?.used = true

        let addressLines = free().filter { isAddress($0.text) }
        addressLines.forEach { $0.used = true }

        let nameLine = pickName(free(), primaryEmail: emails.first)
        nameLine?.used = true

        if companyLine == nil {
            companyLine = free().filter { $0.text.contains(where: \.isLetter) && !$0.text.contains(where: \.isNumber) }
                .max { $0.height < $1.height }
            companyLine?.used = true
        }

        return ParsedCard(
            fullName: nameLine?.text ?? "",
            jobTitle: titleLine?.text ?? "",
            company: companyLine?.text ?? "",
            phone: phones.count > 0 ? phones[0] : "",
            phoneAlt: phones.count > 1 ? phones[1] : "",
            email: emails.count > 0 ? emails[0] : "",
            emailAlt: emails.count > 1 ? emails[1] : "",
            website: websites.first ?? "",
            address: addressLines.map { $0.text.trimmingCharacters(in: CharacterSet(charactersIn: ", ")) }.joined(separator: ", "),
            unmatched: free().map(\.text)
        )
    }

    /// Splits "T: 1 M: 2" into separate segments at colon labels.
    private static func split(_ text: String) -> [String] {
        let ns = text as NSString
        var parts: [String] = []
        var start = 0
        for m in segmentSplit.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            parts.append(ns.substring(with: NSRange(location: start, length: m.range.location - start)))
            start = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: start))
        return parts
    }

    private static func rank(_ site: String) -> Int {
        let l = site.lowercased()
        return l.hasPrefix("http") || l.hasPrefix("www.") ? 0 : 1
    }

    private static func extract(_ work: [Work], _ re: NSRegularExpression) -> [String] {
        var found: [String] = []
        for w in work {
            found += re.allMatches(w.text).map { $0.trimmingCharacters(in: CharacterSet(charactersIn: ".,")) }
            w.text = re.replacing(w.text, with: " ").trimmed
        }
        var seen = Set<String>()
        return found.filter { seen.insert($0.lowercased()).inserted }
    }

    /// Mobile numbers first; fax lines are skipped (they surface as unmatched text instead).
    private static func extractPhones(_ work: [Work]) -> [String] {
        var mobile: [String] = [], other: [String] = []
        for w in work where !faxLine.matches(w.text) {
            let isMobile = mobileLine.matches(w.text)
            let hits = phone.allMatches(w.text).filter { (7...15).contains($0.filter(\.isASCIIDigit).count) }
            for h in hits {
                if isMobile { mobile.append(h.trimmed) } else { other.append(h.trimmed) }
                w.text = w.text.replacingOccurrences(of: h, with: " ")
            }
            w.text = w.text.trimmed
        }
        var seen = Set<String>()
        return (mobile + other).filter { seen.insert($0).inserted }
    }

    private static func isAddress(_ text: String) -> Bool {
        postal.matches(text) || addressWords.matches(text) ||
            (text.contains(where: \.isNumber) && text.contains(",") && text.contains(where: \.isLetter))
    }

    private static func pickName(_ candidates: [Work], primaryEmail: String?) -> Work? {
        let eligible = candidates.filter { w in
            let words = w.text.split(separator: " ")
            return nameChars.matches(w.text) && (1...4).contains(words.count) && w.text.count <= 40
        }
        guard !eligible.isEmpty else { return nil }
        let maxHeight = max(candidates.map(\.height).max() ?? 1, 1)
        let maxTop = max(candidates.map(\.top).max() ?? 1, 1)
        let local = primaryEmail.flatMap { $0.split(separator: "@").first.map(String.init) }?.lowercased() ?? ""
        let emailTokens = local.split(whereSeparator: { "._-0123456789".contains($0) }).map(String.init).filter { $0.count >= 3 }

        func score(_ w: Work) -> Double {
            let words = w.text.split(separator: " ").map(String.init)
            var s: Double = switch words.count { case 2, 3: 3.0; case 4: 1.0; default: 0.0 }
            s += 4.0 * Double(w.height) / Double(maxHeight)
            s += 2.0 * (1.0 - Double(w.top) / Double(maxTop))
            if words.contains(where: { word in emailTokens.contains(word.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))) }) { s += 5 }
            return s
        }
        // Keep the first of equal scores (the earlier line), like Kotlin's maxByOrNull.
        var best: Work?
        var bestScore = -Double.infinity
        for w in eligible {
            let sc = score(w)
            if sc > bestScore { best = w; bestScore = sc }
        }
        return best
    }

    private static func domainLabel(_ hostOrUrl: String) -> String {
        var h = hostOrUrl.lowercased()
        for p in ["https://", "http://", "www."] where h.hasPrefix(p) { h.removeFirst(p.count) }
        h = String(h.split(separator: "/").first ?? "")
        let parts = h.split(separator: ".").map(String.init)
        guard !parts.isEmpty else { return "" }
        let tlds: Set<String> = ["com", "co", "in", "org", "net", "uk", "io", "ac", "edu", "gov"]
        let tldCount = parts.reversed().prefix { tlds.contains($0) }.count
        let i = parts.count - tldCount - 1
        return i >= 0 && i < parts.count ? parts[i] : parts[0]
    }
}
