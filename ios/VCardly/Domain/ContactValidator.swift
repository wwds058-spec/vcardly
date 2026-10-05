import Foundation

enum ContactField: CaseIterable { case fullName, jobTitle, company, phone, phoneAlt, email, emailAlt, website, address, notes }

enum ValidationReason: Equatable { case required, tooLong, invalidFormat }

/// Same rules as Android's ContactValidator: every optional field may be blank; a non-blank value must be valid.
/// Lenient about style (OCR and international cards vary), strict only about what would break calling or mailing.
enum ContactValidator {
    static let maxName = 100, maxShort = 100, maxPhone = 30, maxEmail = 254, maxWebsite = 200, maxAddress = 300, maxNotes = 2000

    private static let email = try! NSRegularExpression(pattern: "^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")
    private static let phoneChars = try! NSRegularExpression(pattern: "^[0-9+()\\-. ]+$")
    private static let website = try! NSRegularExpression(
        pattern: "^(https?://)?([\\p{L}\\p{N}-]+\\.)+[\\p{L}\\p{N}-]{2,}(:\\d+)?([/?#]\\S*)?$", options: [.caseInsensitive])

    static func validate(_ c: Contact) -> [ContactField: ValidationReason] {
        var errors: [ContactField: ValidationReason] = [:]
        let name = c.fullName.trimmed
        if name.isEmpty { errors[.fullName] = .required } else if name.count > maxName { errors[.fullName] = .tooLong }
        length(&errors, .jobTitle, c.jobTitle, maxShort)
        length(&errors, .company, c.company, maxShort)
        length(&errors, .address, c.address, maxAddress)
        length(&errors, .notes, c.notes, maxNotes)
        phone(&errors, .phone, c.phone)
        phone(&errors, .phoneAlt, c.phoneAlt)
        mail(&errors, .email, c.email)
        mail(&errors, .emailAlt, c.emailAlt)
        let site = c.website.trimmed
        if !site.isEmpty {
            if site.count > maxWebsite { errors[.website] = .tooLong } else if !website.matches(site) { errors[.website] = .invalidFormat }
        }
        return errors
    }

    private static func length(_ e: inout [ContactField: ValidationReason], _ f: ContactField, _ v: String, _ max: Int) {
        if v.trimmed.count > max { e[f] = .tooLong }
    }

    private static func phone(_ e: inout [ContactField: ValidationReason], _ f: ContactField, _ raw: String) {
        let v = raw.trimmed
        guard !v.isEmpty else { return }
        let digits = v.filter(\.isNumber).count
        if v.count > maxPhone { e[f] = .tooLong } else if !phoneChars.matches(v) || !(5...15).contains(digits) { e[f] = .invalidFormat }
    }

    private static func mail(_ e: inout [ContactField: ValidationReason], _ f: ContactField, _ raw: String) {
        let v = raw.trimmed
        guard !v.isEmpty else { return }
        if v.count > maxEmail { e[f] = .tooLong } else if !email.matches(v) { e[f] = .invalidFormat }
    }
}

/// Digits for a wa.me link (7 to 15 digits, leading zeros dropped), or nil when the number cannot be used.
func whatsAppDigits(_ number: String) -> String? {
    var digits = number.filter(\.isASCIIDigit)
    while digits.first == "0" { digits.removeFirst() }
    return (7...15).contains(digits.count) ? digits : nil
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}

extension NSRegularExpression {
    func matches(_ s: String) -> Bool { firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }

    func allMatches(_ s: String) -> [String] {
        matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { Range($0.range, in: s).map { String(s[$0]) } }
    }

    func replacing(_ s: String, with template: String) -> String {
        stringByReplacingMatches(in: s, range: NSRange(s.startIndex..., in: s), withTemplate: template)
    }
}
