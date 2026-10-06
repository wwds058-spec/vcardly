import Foundation

/// On Android, PDF and Excel exports need Pro. On iPhone purchases are not set up yet, so they are free for everyone (the
/// owner's decision); when StoreKit purchases are added, set this to true and gate the PDF and Excel tiles like Android.
enum ProFeatures {
    static let exportsRequirePro = false
}

/// A sheet / CSV file: header row plus string cells.
struct ExportTable: Equatable {
    let name: String
    let header: [String]
    let rows: [[String]]
}

/// Same columns as Android's ExportTables; every word comes from Localizable.strings.
enum ExportTables {
    private static let stamp: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f
    }()

    static func format(_ d: Date) -> String { stamp.string(from: d) }

    static func contacts(_ list: [ContactDetails]) -> ExportTable {
        ExportTable(
            name: L10n.s("export.sheet.contacts"),
            header: ["field.name", "field.job_title", "field.company", "field.phone", "field.phone_alt", "field.email", "field.email_alt",
                     "field.website", "field.address", "field.category", "field.tags", "export.col.favorite", "export.col.source",
                     "field.notes", "export.col.added"].map { L10n.s($0) },
            rows: list.map { d in
                let c = d.contact
                return [c.fullName, c.jobTitle, c.company, c.phone, c.phoneAlt, c.email, c.emailAlt, c.website, c.address,
                        d.category?.displayName ?? "", d.tags.map(\.name).joined(separator: "; "),
                        L10n.s(c.isFavorite ? "common.yes" : "common.no"), L10n.s("source.\(c.source.rawValue.lowercased())"),
                        c.notes, format(c.createdAt)]
            })
    }

    static func followUps(_ list: [FollowUpWithContact]) -> ExportTable {
        ExportTable(
            name: L10n.s("export.sheet.followups"),
            header: ["followup.contact", "field.company", "followup.title_field", "followup.type", "export.col.status",
                     "export.col.due", "export.col.completed", "field.notes"].map { L10n.s($0) },
            rows: list.map { f in
                let x = f.followUp
                return [f.contactName, f.contactCompany, x.title, x.type.label, x.status.label, format(x.dueAt),
                        x.completedAt.map(format) ?? "", x.notes]
            })
    }
}

/// RFC 4180 CSV (CRLF, quoted when needed) with protection against spreadsheet formula injection: a name such as
/// `=HYPERLINK(...)` must not run when the file is opened in Excel or Numbers. Port of Android's CsvWriter.
enum CsvWriter {
    /// UTF-8 byte-order mark; without it Excel mis-reads non-Latin text.
    static let bom = "\u{FEFF}"
    private static let formulaStart: Set<Character> = ["=", "+", "-", "@", "\t", "\r"]
    /// Digits and phone punctuation only can never form a formula, so "+91 98765 43210" stays intact.
    private static let phoneLike = try! NSRegularExpression(pattern: "^[+\\-]?[0-9 ()./\\-]+$")

    static func write(_ t: ExportTable) -> String {
        bom + ([t.header] + t.rows).map { $0.map(cell).joined(separator: ",") + "\r\n" }.joined()
    }

    static func cell(_ raw: String) -> String {
        let safe = neutralise(raw)
        let needsQuotes = safe.contains { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" || $0 == "\r\n" } || safe.hasPrefix(" ") || safe.hasSuffix(" ")
        return needsQuotes ? "\"" + safe.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : safe
    }

    static func neutralise(_ value: String) -> String {
        guard let first = value.unicodeScalars.first.map(Character.init), formulaStart.contains(first), !phoneLike.matches(value) else { return value }
        return "'" + value
    }
}

/// Minimal .xlsx writer (Office Open XML), a port of Android's XlsxWriter. Every cell is an inline string, so nothing a
/// contact contains can be read as a formula. The header row is bold and frozen; column widths fit the content.
enum XlsxWriter {
    private static let maxCell = 32_000 // Excel's limit is 32,767 characters
    private static let maxWidth = 60
    private static let main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private static let rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private static let pkg = "http://schemas.openxmlformats.org/package/2006/relationships"

    static func write(_ tables: [ExportTable], to url: URL) throws {
        precondition(!tables.isEmpty, "at least one sheet")
        let names = uniqueSheetNames(tables.map(\.name))
        let zip = try ZipArchive.Writer(url: url)
        func put(_ path: String, _ xml: String) throws {
            try zip.add(path, Data(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + xml).utf8), compress: true)
        }
        try put("[Content_Types].xml", contentTypes(tables.count))
        try put("_rels/.rels", "<Relationships xmlns=\"\(pkg)\"><Relationship Id=\"rId1\" Type=\"\(rel)/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        try put("xl/workbook.xml", "<workbook xmlns=\"\(main)\" xmlns:r=\"\(rel)\"><sheets>"
            + names.enumerated().map { "<sheet name=\"\(esc($0.element))\" sheetId=\"\($0.offset + 1)\" r:id=\"rId\($0.offset + 1)\"/>" }.joined()
            + "</sheets></workbook>")
        try put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"\(pkg)\">"
            + tables.indices.map { "<Relationship Id=\"rId\($0 + 1)\" Type=\"\(rel)/worksheet\" Target=\"worksheets/sheet\($0 + 1).xml\"/>" }.joined()
            + "<Relationship Id=\"rId\(tables.count + 1)\" Type=\"\(rel)/styles\" Target=\"styles.xml\"/></Relationships>")
        try put("xl/styles.xml", styles)
        for (i, t) in tables.enumerated() { try put("xl/worksheets/sheet\(i + 1).xml", sheet(t)) }
        try zip.finish()
    }

    private static func contentTypes(_ sheets: Int) -> String {
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + (1...sheets).map { "<Override PartName=\"/xl/worksheets/sheet\($0).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }.joined()
            + "</Types>"
    }

    /// cellXfs 0 = normal, 1 = bold header.
    private static let styles = "<styleSheet xmlns=\"\(main)\">"
        + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
        + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>"

    private static func sheet(_ t: ExportTable) -> String {
        let all = [t.header] + t.rows
        let widths = (0..<t.header.count).map { c -> Int in
            let longest = all.map { r -> Int in
                guard c < r.count else { return 0 }
                return r[c].split(separator: "\n", omittingEmptySubsequences: false).map(\.count).max() ?? 0
            }.max() ?? 0
            return min(max(longest + 2, 8), maxWidth)
        }
        var s = "<worksheet xmlns=\"\(main)\">"
        s += "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
        s += "<cols>" + widths.enumerated().map { "<col min=\"\($0.offset + 1)\" max=\"\($0.offset + 1)\" width=\"\($0.element)\" customWidth=\"1\"/>" }.joined() + "</cols>"
        s += "<sheetData>"
        for (r, row) in all.enumerated() {
            s += "<row r=\"\(r + 1)\">"
            for (c, value) in row.enumerated() where !value.isEmpty {
                let style = r == 0 ? " s=\"1\"" : ""
                s += "<c r=\"\(column(c))\(r + 1)\" t=\"inlineStr\"\(style)><is><t xml:space=\"preserve\">\(esc(String(value.prefix(maxCell))))</t></is></c>"
            }
            s += "</row>"
        }
        return s + "</sheetData></worksheet>"
    }

    /// 0 -> A, 25 -> Z, 26 -> AA.
    static func column(_ index: Int) -> String {
        var n = index
        var out = ""
        repeat {
            out = String(UnicodeScalar(UInt8(65 + n % 26))) + out
            n = n / 26 - 1
        } while n >= 0
        return out
    }

    /// Sheet names: at most 31 characters, none of []:*?/\ , not blank, unique ignoring case.
    static func uniqueSheetNames(_ raw: [String]) -> [String] {
        var used = Set<String>()
        return raw.enumerated().map { i, name in
            var base = String(name.map { "[]:*?/\\".contains($0) ? " " : $0 }).trimmed
            base = String(base.prefix(31))
            if base.isEmpty { base = "Sheet\(i + 1)" }
            var candidate = base
            var n = 2
            while !used.insert(candidate.lowercased()).inserted {
                let suffix = " (\(n))"
                candidate = String(base.prefix(31 - suffix.count)) + suffix
                n += 1
            }
            return candidate
        }
    }

    /// Escapes XML and drops characters that are illegal in XML 1.0 (they would corrupt the whole file).
    static func esc(_ s: String) -> String {
        var out = ""
        out.unicodeScalars.reserveCapacity(s.unicodeScalars.count)
        for u in s.unicodeScalars {
            let v = u.value
            let legal = v == 0x9 || v == 0xA || v == 0xD || (0x20...0xD7FF).contains(v) || (0xE000...0xFFFD).contains(v) || (0x10000...0x10FFFF).contains(v)
            guard legal else { continue }
            switch u {
            case "&": out += "&amp;"
            case "<": out += "&lt;"
            case ">": out += "&gt;"
            case "\"": out += "&quot;"
            case "'": out += "&apos;"
            default: out.unicodeScalars.append(u)
            }
        }
        return out
    }
}
