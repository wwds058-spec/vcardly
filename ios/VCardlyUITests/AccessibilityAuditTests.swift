import XCTest

/// Launches the real app with fictional sample data and runs Apple's accessibility audit (contrast, hit regions, Dynamic Type,
/// element descriptions, clipped text, traits) on each main screen. Every issue found is written to
/// ios/build/accessibility-audit.txt and fails the test, except for the documented exceptions below.
final class AccessibilityAuditTests: XCTestCase {
    private static let report = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("build/accessibility-audit.txt")
    private var issues: [String] = []
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = true
        app = XCUIApplication()
        app.launchArguments = ["-uiTestSampleData"]
        app.launch()
    }

    override func tearDown() {
        let text = issues.isEmpty ? "No accessibility issues found.\n" : issues.joined(separator: "\n") + "\n"
        try? FileManager.default.createDirectory(at: Self.report.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? text.write(to: Self.report, atomically: true, encoding: .utf8)
        print("ACCESSIBILITY AUDIT\n" + text)
    }

    private func audit(_ screen: String) throws {
        // Let push and appear animations finish: mid-transition frames mix two screens and skew contrast measurements.
        sleep(2)
        // Keep what the audit saw, so a finding can be checked against the real pixels (published with the screenshots).
        let shot = Self.report.deletingLastPathComponent().appendingPathComponent("screenshots/audit_\(screen.replacingOccurrences(of: " ", with: "_")).png")
        try? FileManager.default.createDirectory(at: shot.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? XCUIScreen.main.screenshot().pngRepresentation.write(to: shot)
        try app.performAccessibilityAudit { issue in
            let el = issue.element
            let line = "[\(screen)] \(issue.auditType): \(issue.compactDescription) | element: \(el?.elementType.rawValue ?? 0) "
                + "label=\"\(el?.label ?? "")\" id=\"\(el?.identifier ?? "")\""
            self.issues.append(line)
            return true // keep going: every issue is recorded, then reported together
        }
    }

    private func tap(_ query: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(query.waitForExistence(timeout: 5), "missing \(query)", file: file, line: line)
        query.tap()
    }

    private func back() { app.navigationBars.buttons.element(boundBy: 0).tap() }

    func testMainScreensPassAccessibilityAudit() throws {
        let homeTab = app.descendants(matching: .any)["tab.nav.home"]
        XCTAssertTrue(homeTab.waitForExistence(timeout: 10))
        // Regression guard: the tab bar sits at the bottom and never takes over the screen.
        XCTAssertGreaterThan(homeTab.frame.minY, app.frame.height * 0.8, "tab bar must stay at the bottom of the screen")
        try audit("Home")

        tap(app.descendants(matching: .any)["tab.nav.contacts"])
        try audit("Contacts")

        tap(app.descendants(matching: .any).matching(identifier: "contact.card").firstMatch)
        sleep(1)
        try audit("Contact details")
        back()

        tap(app.descendants(matching: .any)["tab.nav.followups"])
        try audit("Follow-ups")

        tap(app.descendants(matching: .any)["tab.nav.settings"])
        try audit("Settings")

        tap(app.descendants(matching: .any)["settings.reports"])
        sleep(1)
        try audit("Reports")
        back()

        tap(app.descendants(matching: .any)["settings.backup"])
        try audit("Backup")
        back()

        XCTAssertTrue(issues.isEmpty, "Accessibility issues:\n" + issues.joined(separator: "\n"))
    }
}
