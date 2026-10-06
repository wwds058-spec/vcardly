import PDFKit
import XCTest
@testable import VCardly

/// CSV, Excel and PDF exports. Writes sample files to ios/build/exports; CI opens them with Python's csv module and openpyxl.
final class ExportTests: XCTestCase {
    private static let out = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("build/exports", isDirectory: true)

    private func sample(_ n: Int = 3) -> [ContactDetails] {
        let base = [
            ContactDetails(contact: Contact(fullName: "Asha Rao", jobTitle: "CTO", company: "Acme, Inc", phone: "+91 98765 43210",
                                            email: "asha@acme.example", notes: "Line one\nline \"two\"", isFavorite: true, source: .scan),
                           category: Category(name: "", colorARGB: SystemCategory.customer.colorARGB, system: .customer),
                           tags: [Tag(name: "VIP"), Tag(name: "expo")]),
            ContactDetails(contact: Contact(fullName: "=HYPERLINK(\"http://x\")", company: "<b>Globex</b> & Co"), category: nil, tags: []),
            ContactDetails(contact: Contact(fullName: "रमेश कुमार", company: "اردو کمپنی", phone: "-123 456"), category: nil, tags: [Tag(name: "VIP")]),
        ]
        guard n > base.count else { return base }
        return base + (base.count..<n).map { ContactDetails(contact: Contact(fullName: "Sample Person \($0)", company: "Company \($0)"), category: nil, tags: []) }
    }

    private var followUps: [FollowUpWithContact] {
        [FollowUpWithContact(followUp: FollowUp(contactId: UUID(), type: .quotation, status: .completed, title: "Send quote",
                                                dueAt: Date(timeIntervalSince1970: 1_767_607_200), completedAt: Date(timeIntervalSince1970: 1_767_610_800)),
                             contactName: "Asha Rao", contactCompany: "Acme, Inc")]
    }

    func testCsvQuotingAndFormulaProtection() {
        XCTAssertEqual(CsvWriter.cell("=HYPERLINK(\"x\")"), "\"'=HYPERLINK(\"\"x\"\")\"")
        XCTAssertEqual(CsvWriter.cell("@SUM(A1)"), "'@SUM(A1)")
        XCTAssertEqual(CsvWriter.cell("+91 98765 43210"), "+91 98765 43210", "real phone numbers stay intact")
        XCTAssertEqual(CsvWriter.cell("-123 456"), "-123 456")
        XCTAssertEqual(CsvWriter.cell("a,b"), "\"a,b\"")
        XCTAssertEqual(CsvWriter.cell("two\nlines"), "\"two\nlines\"")
        XCTAssertEqual(CsvWriter.cell(" padded"), "\" padded\"")
        let csv = CsvWriter.write(ExportTables.contacts(sample()))
        XCTAssertTrue(csv.hasPrefix("\u{FEFF}"))
        XCTAssertEqual(csv.components(separatedBy: "\r\n").first?.dropFirst().split(separator: ",").count, 15)
        XCTAssertTrue(csv.contains("Asha Rao,CTO,\"Acme, Inc\""))
        XCTAssertTrue(csv.contains("VIP; expo"))
    }

    func testXlsxHelpers() {
        XCTAssertEqual([0, 25, 26, 701, 702].map(XlsxWriter.column), ["A", "Z", "AA", "ZZ", "AAA"])
        XCTAssertEqual(XlsxWriter.uniqueSheetNames(["Contacts", "contacts", "a/b:c", "", String(repeating: "x", count: 40)]),
                       ["Contacts", "contacts (2)", "a b c", "Sheet4", String(repeating: "x", count: 31)])
        XCTAssertEqual(XlsxWriter.esc("a<b>&\"'\u{1}z"), "a&lt;b&gt;&amp;&quot;&apos;z")
    }

    func testXlsxStructure() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = dir.appendingPathComponent("t.xlsx")
        try XlsxWriter.write([ExportTables.contacts(sample()), ExportTables.followUps(followUps)], to: url)
        let zip = try Data(contentsOf: url)
        let entries = try ZipArchive.entries(zip)
        XCTAssertEqual(entries.first?.name, "[Content_Types].xml")
        XCTAssertTrue(entries.contains { $0.name == "xl/worksheets/sheet2.xml" })
        let sheet = String(decoding: try ZipArchive.read(entries.first { $0.name == "xl/worksheets/sheet1.xml" }!, from: zip, maxSize: 1 << 22), as: UTF8.self)
        XCTAssertTrue(sheet.contains("&lt;b&gt;Globex&lt;/b&gt; &amp; Co"))
        XCTAssertFalse(sheet.contains("<f>"), "no formulas, only inline strings")
        XCTAssertTrue(sheet.contains("state=\"frozen\""))
    }

    func testPdfContentAndRendering() throws {
        let contacts = sample(120)
        let report = ReportsState.build(contacts: contacts, followUps: followUps)
        let doc = ReportPdfContent.build(report, contacts: contacts)
        XCTAssertEqual(doc.title, L10n.s("report.pdf.title"))
        XCTAssertTrue(doc.blocks.contains(.heading(L10n.s("report.pdf.directory"))))
        XCTAssertTrue(doc.blocks.contains(.heading(L10n.s("reports.top_tags"))))
        guard case .bars(let tags)? = doc.blocks.first(where: { if case .bars(let rows) = $0 { return rows.first?.label.hasPrefix("#") == true }; return false }) else {
            return XCTFail("top tags bars")
        }
        XCTAssertEqual(tags.first, BarRow(label: "#VIP", value: "2", fraction: 1))

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let url = try ReportExporter.write(.pdf, report: report, contacts: contacts, followUps: followUps, dir: dir)
        let pdf = try XCTUnwrap(PDFDocument(url: url))
        XCTAssertGreaterThanOrEqual(pdf.pageCount, 3, "120 directory rows need several pages")
        XCTAssertTrue(pdf.string?.contains("Asha Rao") == true)
    }

    func testWritesExportsForCrossCheck() throws {
        try FileManager.default.createDirectory(at: Self.out, withIntermediateDirectories: true)
        let contacts = sample()
        let report = ReportsState.build(contacts: contacts, followUps: followUps)
        for f in ReportExportFormat.allCases {
            let url = try ReportExporter.write(f, report: report, contacts: contacts, followUps: followUps, dir: Self.out)
            XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        }
    }
}
