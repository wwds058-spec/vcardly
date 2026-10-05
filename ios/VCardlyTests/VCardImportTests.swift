import Contacts
import XCTest
@testable import VCardly

/// Ports of Android's VCardTest and VCardImporterTest, so both apps read and import vCards the same way.
final class VCardImportTests: XCTestCase {
    private func p(_ name: String, phones: [String] = [], emails: [String] = [], company: String = "", notes: String = "", website: String = "") -> ParsedVCard {
        ParsedVCard(fullName: name, company: company, phones: phones, emails: emails, website: website, notes: notes)
    }

    func testRoundTripPreservesEveryFieldIncludingEscapes() {
        let text = VCardWriter.write(fullName: "Asha Rao", jobTitle: "Senior Engineer, R&D", company: "Acme; Inc",
                                     phones: [("+91 98765 43210", "CELL"), ("040 2345 6789", "WORK")],
                                     emails: ["asha@acme.com", "asha.rao@home.org"], website: "https://acme.com/a,b",
                                     address: "Plot 12, Road 5\nBanjara Hills; Hyderabad", notes: "Met at Expo; likes tea, no sugar",
                                     categories: ["Client", "VIP, Gold"], revision: Date(timeIntervalSince1970: 1_767_607_200))
        XCTAssertTrue(text.hasPrefix("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        XCTAssertTrue(text.contains("REV:20260105T100000Z"))
        let c = VCardParser.parse(text)
        XCTAssertEqual(c.count, 1)
        XCTAssertEqual(c[0].fullName, "Asha Rao")
        XCTAssertEqual(c[0].jobTitle, "Senior Engineer, R&D")
        XCTAssertEqual(c[0].company, "Acme; Inc")
        XCTAssertEqual(c[0].phones, ["+91 98765 43210", "040 2345 6789"])
        XCTAssertEqual(c[0].emails, ["asha@acme.com", "asha.rao@home.org"])
        XCTAssertEqual(c[0].website, "https://acme.com/a,b")
        XCTAssertEqual(c[0].address, "Plot 12, Road 5\nBanjara Hills; Hyderabad")
        XCTAssertEqual(c[0].notes, "Met at Expo; likes tea, no sugar")
        XCTAssertEqual(c[0].categories, ["Client", "VIP, Gold"])
    }

    func testFoldedNonLatinAndMultipleCards() {
        let telugu = String(repeating: "హరి", count: 40)
        let text = VCardWriter.write(fullName: telugu, notes: String(repeating: "x", count: 300))
            + VCardWriter.write(fullName: "रमेश कुमार", company: "اردو کمپنی") + VCardWriter.write(fullName: "Three")
        for line in text.components(separatedBy: "\r\n") { XCTAssertLessThanOrEqual(line.utf8.count, 75) }
        let cards = VCardParser.parse(text)
        XCTAssertEqual(cards.map(\.fullName), [telugu, "रमेश कुमार", "Three"])
        XCTAssertEqual(cards[0].notes, String(repeating: "x", count: 300))
        XCTAssertEqual(cards[1].company, "اردو کمپنی")
    }

    func testVCard21BareTypesQuotedPrintableGroupsAndFax() {
        let text = "BEGIN:VCARD\nVERSION:2.1\nN:Müller;Jörg;;Dr.;\nFN;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:J=C3=B6rg M=C3=BCller\n" +
            "item1.TEL;WORK;FAX:111 222 333\nTEL;VOICE:020 7946 0958\nTEL;CELL;VOICE:07911 123456\n" +
            "EMAIL;INTERNET:jorg@example.de\nNOTE;ENCODING=QUOTED-PRINTABLE:line one=0Aline =\ntwo\nEND:VCARD\n"
        let c = VCardParser.parse(text)[0]
        XCTAssertEqual(c.fullName, "Jörg Müller")
        XCTAssertEqual(c.phones, ["07911 123456", "020 7946 0958"]) // mobile first, fax dropped
        XCTAssertEqual(c.emails, ["jorg@example.de"])
        XCTAssertEqual(c.notes, "line one\nline two")
        // Structured name is used when FN is missing.
        XCTAssertEqual(VCardParser.parse("BEGIN:VCARD\nN:Rao;Asha;;Dr.;\nEND:VCARD")[0].fullName, "Dr. Asha Rao")
        XCTAssertTrue(VCardParser.parse("garbage\nBEGIN:VCARD\nEND:VCARD").isEmpty)
    }

    func testDuplicatesByEmailPhoneOrNameAndCompany() {
        let existing = [Contact(fullName: "Asha Rao", company: "Acme", phone: "+91 98765 43210", email: "Asha@Acme.com")]
        let out = VCardImporter.prepare([
            p("Someone Else", emails: ["asha@acme.com"]), p("Other Name", phones: ["098765 43210"]),
            p("  asha   rao ", company: "ACME"), p("Brand New", emails: ["new@x.com"]),
        ], existing: existing)
        XCTAssertEqual(out.map(\.status), [.duplicate, .duplicate, .duplicate, .new])
        XCTAssertEqual(VCardImporter.prepare([p("Asha Rao", company: "Globex")], existing: existing).map(\.status), [.new])
        XCTAssertEqual(VCardImporter.prepare([p("A", emails: ["a@x.com"]), p("B", emails: ["a@x.com"])], existing: []).map(\.status), [.new, .duplicate])
    }

    func testInvalidAndOverflowValuesMoveToNotes() throws {
        let c = try XCTUnwrap(VCardImporter.toContact(p("Bob", phones: ["+1 555 123 4567 ext 9", "12345678", "3333333"],
                                                        emails: ["not-an-email", "c@d.co", "e@f.co"], notes: "hello", website: "nowhere")))
        XCTAssertEqual(c.phoneAlt, "12345678")
        XCTAssertEqual(c.email, ""); XCTAssertEqual(c.website, "")
        for s in ["hello", "ext 9", "not-an-email", "nowhere", "3333333", "e@f.co"] { XCTAssertTrue(c.notes.contains(s), s) }
        XCTAssertEqual(c.source, .importFile)
        XCTAssertTrue(ContactValidator.validate(c).isEmpty, "imported contacts always pass the form's rules")

        let long = try XCTUnwrap(VCardImporter.toContact(ParsedVCard(fullName: "Kim", jobTitle: String(repeating: "t", count: 150))))
        XCTAssertEqual(long.jobTitle.count, 100)
        XCTAssertTrue(long.notes.contains(String(repeating: "t", count: 150)))
    }

    func testNameFallbacksAndUnusable() {
        XCTAssertEqual(VCardImporter.toContact(p("", company: "Acme"))?.fullName, "Acme")
        XCTAssertEqual(VCardImporter.toContact(p("", emails: ["jane@x.org"]))?.fullName, "jane")
        XCTAssertNil(VCardImporter.toContact(p("", phones: ["123456789"])))
        XCTAssertEqual(VCardImporter.prepare([p("")], existing: []).map(\.status), [.unusable])
        XCTAssertEqual(VCardImporter.toContact(p(String(repeating: "n", count: 500)))?.fullName.count, 100)
    }

    func testExportedContactReimportsWithCategoryAndTags() {
        let d = ContactDetails(contact: Contact(fullName: "Asha Rao", company: "Acme", phone: "+91 98765 43210", notes: "private"),
                               category: Category(name: "", colorARGB: 0, system: .supplier), tags: [Tag(name: "VIP")])
        let c = VCardParser.parse(VCardWriter.write(d))[0]
        XCTAssertEqual(c.categories, [L10n.s("category.supplier"), "VIP"])
        XCTAssertEqual(c.notes, "private")
    }

    func testIPhoneContactConversion() {
        let m = CNMutableContact()
        m.givenName = "Asha"; m.familyName = "Rao"; m.organizationName = "Acme"; m.jobTitle = "CTO"
        m.phoneNumbers = [CNLabeledValue(label: CNLabelWork, value: CNPhoneNumber(stringValue: "040 2345 6789")),
                          CNLabeledValue(label: CNLabelPhoneNumberWorkFax, value: CNPhoneNumber(stringValue: "040 1111 1111")),
                          CNLabeledValue(label: CNLabelPhoneNumberMobile, value: CNPhoneNumber(stringValue: "+91 98765 43210"))]
        m.emailAddresses = [CNLabeledValue(label: CNLabelWork, value: "asha@acme.com" as NSString)]
        let a = CNMutablePostalAddress(); a.street = "Plot 12"; a.city = "Hyderabad"
        m.postalAddresses = [CNLabeledValue(label: CNLabelWork, value: a)]
        let p = ContactsPickerPresenter.parsed(m)
        XCTAssertEqual(p.fullName, "Asha Rao")
        XCTAssertEqual(p.phones, ["+91 98765 43210", "040 2345 6789"])
        XCTAssertEqual(p.emails, ["asha@acme.com"])
        XCTAssertEqual(p.address, "Plot 12, Hyderabad")
    }
}
