import XCTest
@testable import VCardly

@MainActor
final class RepositoryTests: XCTestCase {
    private var env: AppEnvironment!

    override func setUp() async throws {
        env = AppEnvironment(inMemory: true, defaults: UserDefaults(suiteName: "tests-\(UUID().uuidString)")!)
    }

    func testSeedsSystemCategoriesOnce() {
        XCTAssertEqual(env.contacts.categories().count, SystemCategory.allCases.count)
        env.contacts.seedSystemCategories()
        XCTAssertEqual(env.contacts.categories().count, SystemCategory.allCases.count)
    }

    func testSaveFilterAndDeleteCascades() throws {
        let tag = try XCTUnwrap(env.contacts.findOrCreateTag("VIP"))
        XCTAssertEqual(env.contacts.findOrCreateTag("vip")?.id, tag.id)
        let cat = env.contacts.categories().first { $0.system == .supplier }!
        let id = try env.contacts.save(Contact(fullName: "Asha Rao", company: "Acme", categoryId: cat.id), tagIds: [tag.id])
        try env.contacts.save(Contact(fullName: "Ben Ito", company: "Globex"), tagIds: [])
        XCTAssertEqual(env.contacts.contacts(ContactFilter(query: "acme")).map(\.contact.fullName), ["Asha Rao"])
        XCTAssertEqual(env.contacts.contacts(ContactFilter(categoryId: cat.id)).count, 1)
        XCTAssertEqual(env.contacts.contacts(ContactFilter(tagIds: [tag.id])).count, 1)
        XCTAssertEqual(env.contacts.contacts(ContactFilter(sort: .nameDesc)).first?.contact.fullName, "Ben Ito")

        try env.followUps.save(FollowUp(contactId: id, title: "Send quote", dueAt: Date().addingTimeInterval(3600 * 30)))
        XCTAssertEqual(env.followUps.forContact(id).count, 1)
        try env.contacts.delete(id)
        XCTAssertNil(env.contacts.contact(id))
        XCTAssertTrue(env.followUps.all().isEmpty)
    }

    func testBucketsStatusesAndReschedule() throws {
        let now = Date()
        let cid = try env.contacts.save(Contact(fullName: "Asha"), tagIds: [])
        let overdue = FollowUp(contactId: cid, title: "Late", dueAt: now.addingTimeInterval(-3 * 86400))
        let upcoming = FollowUp(contactId: cid, title: "Later", dueAt: now.addingTimeInterval(3 * 86400))
        try env.followUps.save(overdue)
        try env.followUps.save(upcoming)
        XCTAssertEqual(env.followUps.bucket(.overdue).map(\.followUp.title), ["Late"])
        XCTAssertEqual(env.followUps.bucket(.upcoming).map(\.followUp.title), ["Later"])

        var moved = env.followUps.get(overdue.id)!.followUp
        moved.dueAt = now.addingTimeInterval(5 * 86400)
        try env.followUps.save(moved)
        XCTAssertEqual(env.followUps.get(overdue.id)?.followUp.status, .rescheduled)
        XCTAssertEqual(env.followUps.counts().upcoming, 2)

        env.followUps.setStatus(upcoming.id, .cancelled)
        env.followUps.setStatus(overdue.id, .completed)
        XCTAssertNotNil(env.followUps.get(overdue.id)?.followUp.completedAt)
        XCTAssertEqual(env.followUps.counts().completed, 2)
        XCTAssertEqual(env.followUps.counts().pending, 0)
    }

    func testReportsIgnoreNothingInvented() throws {
        let cat = env.contacts.categories().first { $0.system == .customer }!
        try env.contacts.save(Contact(fullName: "A", categoryId: cat.id, source: .scan), tagIds: [])
        try env.contacts.save(Contact(fullName: "B"), tagIds: [])
        let r = ReportsState.build(contacts: env.contacts.contacts(), followUps: env.followUps.all())
        XCTAssertEqual(r.total, 2)
        XCTAssertEqual(r.scanned, 1)
        XCTAssertEqual(r.byCategory.map(\.count).reduce(0, +), 2)
        XCTAssertEqual(r.growth.count, 6)
        XCTAssertEqual(r.growth.last?.count, 2)
    }
}
