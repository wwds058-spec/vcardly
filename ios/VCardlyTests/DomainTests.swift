import XCTest
@testable import VCardly

final class BusinessCardParserTests: XCTestCase {
    private func l(_ text: String, _ top: Int, _ h: Int = 20) -> OcrLine { OcrLine(text: text, top: top, height: h) }

    func testIndianCardWithLabelsAndFax() {
        let p = BusinessCardParser.parse([
            l("ACME TECHNOLOGIES PVT LTD", 10, 40), l("Asha Rao", 60, 36), l("Senior Software Engineer", 100),
            l("M: +91 98765 43210", 140), l("T: 040 2345 6789  F: 040 2345 6780", 170),
            l("asha.rao@acme.com", 200), l("www.acme.com", 230),
            l("Plot 12, Road No 5, Banjara Hills", 260), l("Hyderabad - 500034", 290),
        ])
        XCTAssertEqual(p.fullName, "Asha Rao")
        XCTAssertEqual(p.jobTitle, "Senior Software Engineer")
        XCTAssertEqual(p.company, "ACME TECHNOLOGIES PVT LTD")
        XCTAssertEqual(p.phone, "+91 98765 43210")
        XCTAssertEqual(p.phoneAlt, "040 2345 6789")
        XCTAssertEqual(p.email, "asha.rao@acme.com")
        XCTAssertEqual(p.website, "www.acme.com")
        XCTAssertEqual(p.address, "Plot 12, Road No 5, Banjara Hills, Hyderabad - 500034")
        XCTAssertTrue(p.unmatched.contains { $0.contains("2345 6780") })
    }

    func testWesternCardWithoutLabels() {
        let p = BusinessCardParser.parse([
            l("Jane Doe", 20, 44), l("Marketing Director", 70), l("Globex Corporation", 100, 28),
            l("jane@globex.io", 140), l("+1 (555) 123-4567", 170), l("globex.io", 200),
            l("123 Main Street, Springfield, IL 62704", 230),
        ])
        XCTAssertEqual(p.fullName, "Jane Doe")
        XCTAssertEqual(p.jobTitle, "Marketing Director")
        XCTAssertEqual(p.company, "Globex Corporation")
        XCTAssertEqual(p.phone, "+1 (555) 123-4567")
        XCTAssertEqual(p.email, "jane@globex.io")
        XCTAssertEqual(p.website, "globex.io")
        XCTAssertEqual(p.address, "123 Main Street, Springfield, IL 62704")
        XCTAssertTrue(p.unmatched.isEmpty)
    }

    func testCompanyFromEmailDomain() {
        let p = BusinessCardParser.parse([l("BrightPath", 10, 30), l("Rahul Verma", 50, 40), l("Founder", 90), l("rahul@brightpath.in", 130), l("+91 99999 12345", 160)])
        XCTAssertEqual(p.company, "BrightPath")
        XCTAssertEqual(p.fullName, "Rahul Verma")
        XCTAssertEqual(p.jobTitle, "Founder")
    }

    func testAbbreviationsAreNotWebsites() {
        let p = BusinessCardParser.parse([l("Dr.Rao", 10, 30), l("Acme Pvt.Ltd", 50), l("rao@acme.com", 90)])
        XCTAssertEqual(p.website, "")
        XCTAssertEqual(p.company, "Acme Pvt.Ltd")
    }

    func testLabelledMobileWins() {
        let p = BusinessCardParser.parse([l("a@x.com", 10), l("b@y.com", 20), l("+44 20 7946 0958", 30), l("Mob: 07911 123456", 40)])
        XCTAssertEqual(p.email, "a@x.com"); XCTAssertEqual(p.emailAlt, "b@y.com")
        XCTAssertEqual(p.phone, "07911 123456")
        XCTAssertEqual(p.phoneAlt, "+44 20 7946 0958")
    }

    func testEmptyAndGarbage() {
        XCTAssertEqual(BusinessCardParser.parse([]), ParsedCard())
        XCTAssertEqual(BusinessCardParser.parse([l("   ", 0), l("•••", 10), l("---", 20)]).fullName, "")
    }
}

final class ValidatorTests: XCTestCase {
    func testNameRequiredAndFormats() {
        var c = Contact(fullName: " ")
        XCTAssertEqual(ContactValidator.validate(c)[.fullName], .required)
        c.fullName = "Asha"
        c.phone = "12"
        c.email = "nope"
        c.website = "not a site"
        let e = ContactValidator.validate(c)
        XCTAssertEqual(e[.phone], .invalidFormat)
        XCTAssertEqual(e[.email], .invalidFormat)
        XCTAssertEqual(e[.website], .invalidFormat)
        c.phone = "+91 98765 43210"; c.email = "asha@acme.com"; c.website = "acme.com"
        XCTAssertTrue(ContactValidator.validate(c).isEmpty)
    }

    func testWhatsAppDigits() {
        XCTAssertEqual(whatsAppDigits("+91 98765 43210"), "919876543210")
        XCTAssertEqual(whatsAppDigits("0091 98765 43210"), "919876543210")
        XCTAssertNil(whatsAppDigits("12345"))
        XCTAssertNil(whatsAppDigits(""))
    }
}

final class VCardTests: XCTestCase {
    func testOnlySelectedFieldsAndEscaping() {
        let card = ShareCard.of(Contact(fullName: "Asha Rao", company: "Acme; Inc", phone: "+91 98765 43210", email: "asha@acme.com", notes: "secret"))
        let text = card.vCard([.phone])
        XCTAssertTrue(text.hasPrefix("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        XCTAssertTrue(text.contains("FN:Asha Rao\r\n"))
        XCTAssertTrue(text.contains("N:Rao;Asha;;;\r\n"))
        XCTAssertTrue(text.contains("TEL;TYPE=CELL:+91 98765 43210"))
        XCTAssertFalse(text.contains("EMAIL"))
        XCTAssertFalse(text.contains("ORG"))
        XCTAssertFalse(text.contains("secret"))
        XCTAssertTrue(ShareCard.of(Contact(fullName: "A", company: "Acme; Inc")).vCard([.company]).contains("ORG:Acme\; Inc"))
    }

    func testFoldingNeverSplitsCharacters() {
        let long = "NOTE:" + String(repeating: "తెలుగు ", count: 30)
        let folded = VCardWriter.fold(long)
        for line in folded.components(separatedBy: "\r\n") { XCTAssertLessThanOrEqual(line.utf8.count, 75) }
        XCTAssertEqual(folded.replacingOccurrences(of: "\r\n ", with: ""), long)
    }
}

final class ResourceTests: XCTestCase {
    func testFontsAreBundled() {
        for w in ["Regular", "Medium", "SemiBold", "Bold"] {
            XCTAssertNotNil(UIFont(name: "PlusJakartaSans-\(w)", size: 12), w)
        }
    }

    func testStringsResolve() {
        for key in ["app.name", "nav.home", "scan.action", "followup.type.quotation", "category.supplier", "contacts.count.other"] {
            XCTAssertNotEqual(L10n.s(key), key, key)
        }
        XCTAssertEqual(L10n.plural("contacts.count", 1), "1 contact")
        XCTAssertEqual(L10n.plural("contacts.count", 3), "3 contacts")
    }

    func testInitials() {
        XCTAssertEqual(Initials.of("asha rao"), "AR")
        XCTAssertEqual(Initials.of("Cher"), "C")
        XCTAssertEqual(Initials.of(""), "")
    }
}
