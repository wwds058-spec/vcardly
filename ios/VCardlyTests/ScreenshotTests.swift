import SwiftUI
import XCTest
@testable import VCardly

/// Renders each screen's stateless content with fictional sample data on an iPhone-sized window and writes PNGs to
/// ios/build/screenshots (CI publishes them to the ios-screenshots branch for design review). Not pixel assertions.
@MainActor
final class ScreenshotTests: XCTestCase {
    private static let outDir: URL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("build/screenshots", isDirectory: true)

    private func snap<V: View>(_ name: String, dark: Bool = false, textSize: DynamicTypeSize = .large, @ViewBuilder _ view: () -> V) throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        window.overrideUserInterfaceStyle = dark ? .dark : .light
        let host = UIHostingController(rootView: view().environment(\.colorScheme, dark ? .dark : .light).environment(\.dynamicTypeSize, textSize))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.6))
        let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        window.isHidden = true
        try FileManager.default.createDirectory(at: Self.outDir, withIntermediateDirectories: true)
        try XCTUnwrap(image.pngData()).write(to: Self.outDir.appendingPathComponent("\(name).png"))
    }

    // MARK: fictional sample data

    private let business = Category(name: "", colorARGB: SystemCategory.business.colorARGB, system: .business)
    private let customer = Category(name: "", colorARGB: SystemCategory.customer.colorARGB, system: .customer)
    private let supplier = Category(name: "", colorARGB: SystemCategory.supplier.colorARGB, system: .supplier)

    private var contacts: [ContactDetails] {
        let now = Date()
        return [
            ContactDetails(contact: Contact(fullName: "Asha Rao", jobTitle: "Senior Software Engineer", company: "Acme Technologies", phone: "+91 98765 43210",
                                            email: "asha.rao@acme.example", website: "acme.example", address: "Plot 12, Banjara Hills, Hyderabad",
                                            notes: "Met at the Bengaluru expo. Interested in the annual plan.", isFavorite: true, source: .scan,
                                            createdAt: now.addingTimeInterval(-86400 * 20), updatedAt: now.addingTimeInterval(-86400 * 2)),
                           category: customer, tags: [Tag(name: "VIP"), Tag(name: "expo")]),
            ContactDetails(contact: Contact(fullName: "Ben Ito", jobTitle: "Procurement Lead", company: "Globex", phone: "+1 555 123 4567", email: "ben@globex.example"),
                           category: supplier, tags: []),
            ContactDetails(contact: Contact(fullName: "Carmen Diaz", jobTitle: "Founder", company: "Brightpath Studio", email: "carmen@brightpath.example", isFavorite: true),
                           category: business, tags: [Tag(name: "design")]),
            ContactDetails(contact: Contact(fullName: "Dev Malhotra", jobTitle: "Director", company: "Northwind Traders", phone: "+91 99999 12345"),
                           category: customer, tags: []),
        ]
    }

    private var followUps: [FollowUpWithContact] {
        let cal = Calendar.current
        let today = cal.date(bySettingHour: 16, minute: 30, second: 0, of: Date())!
        let c = contacts
        return [
            FollowUpWithContact(followUp: FollowUp(contactId: c[0].id, type: .quotation, title: "Send revised quotation", dueAt: today),
                                contactName: c[0].contact.fullName, contactCompany: c[0].contact.company),
            FollowUpWithContact(followUp: FollowUp(contactId: c[1].id, type: .call, status: .rescheduled, title: "Call about delivery dates",
                                                   dueAt: cal.date(byAdding: .day, value: 1, to: today)!),
                                contactName: c[1].contact.fullName, contactCompany: c[1].contact.company),
            FollowUpWithContact(followUp: FollowUp(contactId: c[2].id, type: .meeting, title: "Design review meeting", dueAt: cal.date(byAdding: .day, value: 4, to: today)!),
                                contactName: c[2].contact.fullName, contactCompany: c[2].contact.company),
        ]
    }

    private var myCard: MyCard {
        MyCard(fullName: "Yasin Khan", jobTitle: "Business Development", company: "VCardly Labs", phone: "+91 90000 00000",
               email: "yasin@vcardly.example", website: "vcardly.example", address: "Hyderabad, India")
    }

    private var homeState: HomeState {
        HomeState(greeting: .morning, firstName: "Yasin", myCardName: myCard.fullName, total: 128, addedThisMonth: 12, favorites: 24,
                  counts: FollowUpCounts(today: 3, upcoming: 8, overdue: 2, completed: 40), upcoming: followUps, recent: contacts)
    }

    private var reports: ReportsState {
        var many: [ContactDetails] = []
        for i in 0..<40 {
            let cat = [customer, customer, supplier, business, nil][i % 5]
            many.append(ContactDetails(contact: Contact(fullName: "Sample \(i)", source: i % 3 == 0 ? .scan : .manual,
                                                        createdAt: Calendar.current.date(byAdding: .day, value: -i * 4, to: Date())!),
                                       category: cat, tags: []))
        }
        var fus = followUps
        fus.append(FollowUpWithContact(followUp: FollowUp(contactId: UUID(), status: .completed, title: "x", dueAt: Date()), contactName: "", contactCompany: ""))
        return ReportsState.build(contacts: many, followUps: fus)
    }

    // MARK: screens

    func testOnboarding() throws { try snap("01_onboarding") { OnboardingView(onFinish: {}) } }

    func testHome() throws {
        try snap("02_home") { HomeContent(state: homeState, actions: HomeActions()) }
        try snap("02_home_dark", dark: true) { HomeContent(state: homeState, actions: HomeActions()) }
        try snap("02_home_empty") { HomeContent(state: HomeState(), actions: HomeActions()) }
    }

    func testContacts() throws {
        let state = ContactsState(contacts: contacts, categories: [business, customer, supplier], tags: [Tag(name: "VIP")], loaded: true)
        try snap("03_contacts") { ContactsContent(state: state, actions: ContactsActions()) }
        try snap("03_contacts_dark", dark: true) { ContactsContent(state: state, actions: ContactsActions()) }
        try snap("03_contacts_empty") { ContactsContent(state: ContactsState(loaded: true), actions: ContactsActions()) }
    }

    func testContactDetail() throws {
        let d = contacts[0]
        let fus = followUps.prefix(1).map(\.followUp)
        try snap("04_contact_detail") { NavigationStack { ContactDetailContent(details: d, followUps: fus, actions: ContactDetailActions()) } }
        try snap("04_contact_detail_dark", dark: true) { NavigationStack { ContactDetailContent(details: d, followUps: fus, actions: ContactDetailActions()) } }
    }

    func testContactEdit() throws {
        var s = ContactEditState(contact: contacts[0].contact, categories: [business, customer, supplier])
        try snap("05_contact_add") { NavigationStack { ContactEditContent(state: .constant(ContactEditState()), actions: ContactEditActions()) } }
        s.review = true
        s.foundCount = 7
        s.unmatched = ["F: 040 2345 6780"]
        try snap("06_scan_review") { NavigationStack { ContactEditContent(state: .constant(s), actions: ContactEditActions()) } }
    }

    func testScanStart() throws {
        try snap("07_scan") { ScanStartContent(phase: .start, cameraAvailable: true, scan: {}, pickerItem: .constant(nil), manual: {}, close: {}) }
    }

    func testFollowUps() throws {
        let s = FollowUpsState(bucket: .today, items: followUps, counts: FollowUpCounts(today: 3, upcoming: 8, overdue: 2, completed: 40), loaded: true)
        try snap("08_followups") { FollowUpsContent(state: s, actions: FollowUpsActions()) }
        try snap("08_followups_dark", dark: true) { FollowUpsContent(state: s, actions: FollowUpsActions()) }
        var e = FollowUpEditState()
        e.followUp = followUps[0].followUp
        e.contactChosen = true
        e.contactName = followUps[0].contactName
        try snap("09_followup_edit") { NavigationStack { FollowUpEditContent(state: .constant(e), actions: FollowUpEditActions()) } }
    }

    func testMyCard() throws {
        try snap("10_my_card") { NavigationStack { MyCardContent(card: myCard, edit: {}) } }
        try snap("10_my_card_dark", dark: true) { NavigationStack { MyCardContent(card: myCard, edit: {}) } }
    }

    func testReports() throws {
        try snap("11_reports") { NavigationStack { ReportsContent(state: reports) } }
    }

    func testSettings() throws {
        let s = SettingsState(myCard: myCard, theme: .system, appLock: true, autoLockSeconds: 60, notificationsAllowed: true, version: "0.1.0")
        try snap("12_settings") { SettingsContent(state: s, actions: SettingsActions()) }
        try snap("12_settings_dark", dark: true) { SettingsContent(state: s, actions: SettingsActions()) }
    }

    func testBackup() throws {
        try snap("16_backup") { NavigationStack { BackupContent(state: BackupState(), actions: BackupActions()) } }
        let manifest = BackupManifest(format: BackupFormat.id, formatVersion: 1, createdAt: Date().addingTimeInterval(-86_400 * 3).millis,
                                      appVersion: "1.0.0", encrypted: true, counts: BackupCounts(contacts: 128, categories: 10, tags: 6, followUps: 42, images: 97),
                                      files: [:])
        let ready = BackupState(lastBackupAt: Date().addingTimeInterval(-86_400 * 3),
                                restore: .ready(PreparedBackup(manifest: manifest, data: BackupData(), images: [], workDir: FileManager.default.temporaryDirectory)))
        try snap("16_backup_restore") { NavigationStack { BackupContent(state: ready, actions: BackupActions()) } }
        try snap("16_backup_restore_dark", dark: true) { NavigationStack { BackupContent(state: ready, actions: BackupActions()) } }
    }

    func testTransfer() throws {
        try snap("17_transfer") { NavigationStack { TransferContent(state: TransferState(), actions: TransferActions()) } }
        let entries = VCardImporter.prepare([
            ParsedVCard(fullName: "Asha Rao", company: "Acme Technologies"), ParsedVCard(fullName: "Ben Ito", emails: ["ben@globex.example"]),
            ParsedVCard(fullName: "Carmen Diaz", company: "Brightpath Studio"), ParsedVCard(phones: ["12345"]),
        ], existing: [Contact(fullName: "Ben Ito", email: "ben@globex.example")])
        let preview = TransferState(importState: .preview(entries, selected: [0, 2]))
        try snap("17_transfer_preview") { NavigationStack { TransferContent(state: preview, actions: TransferActions()) } }
    }

    /// The largest accessibility text size (AX5 is the maximum; AX3 is a common real-world setting).
    func testLargeText() throws {
        let ax = DynamicTypeSize.accessibility3
        try snap("20_ax_home", textSize: ax) { HomeContent(state: homeState, actions: HomeActions()) }
        let contacts = ContactsState(contacts: contacts, categories: [business, customer, supplier], tags: [Tag(name: "VIP")], loaded: true)
        try snap("20_ax_contacts", textSize: ax) { ContactsContent(state: contacts, actions: ContactsActions()) }
        try snap("20_ax_contact_detail", textSize: ax) {
            NavigationStack { ContactDetailContent(details: self.contacts[0], followUps: followUps.prefix(1).map(\.followUp), actions: ContactDetailActions()) }
        }
        let fs = FollowUpsState(bucket: .today, items: followUps, counts: FollowUpCounts(today: 3, upcoming: 8, overdue: 2, completed: 40), loaded: true)
        try snap("20_ax_followups", textSize: ax) { FollowUpsContent(state: fs, actions: FollowUpsActions()) }
        try snap("20_ax_add_contact", textSize: ax) { NavigationStack { ContactEditContent(state: .constant(ContactEditState()), actions: ContactEditActions()) } }
        let settings = SettingsState(myCard: myCard, theme: .system, appLock: true, autoLockSeconds: 60, notificationsAllowed: true, version: "0.1.0")
        try snap("20_ax_settings", textSize: ax) { SettingsContent(state: settings, actions: SettingsActions()) }
        try snap("20_ax_my_card", textSize: ax) { NavigationStack { MyCardContent(card: myCard, edit: {}) } }
        try snap("20_ax_reports", textSize: ax) { NavigationStack { ReportsContent(state: reports) } }
    }

    func testOtherScreens() throws {
        try snap("13_lock") { LockContent(failed: false, unlock: {}) }
        try snap("14_privacy") { NavigationStack { PrivacyScreen() } }
        try snap("15_pro") { NavigationStack { ProScreen() } }
    }
}
