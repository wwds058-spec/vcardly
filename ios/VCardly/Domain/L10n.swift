import Foundation

/// All user-facing text lives in Localizable.strings; nothing is hard-coded in Swift.
enum L10n {
    static func s(_ key: String) -> String { NSLocalizedString(key, comment: "") }

    static func s(_ key: String, _ args: CVarArg...) -> String {
        String(format: NSLocalizedString(key, comment: ""), locale: Locale.current, arguments: args)
    }

    /// Simple English-style plural: looks up `key.one` or `key.other`.
    static func plural(_ key: String, _ count: Int) -> String {
        s(count == 1 ? "\(key).one" : "\(key).other", count)
    }
}
