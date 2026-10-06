#if DEBUG
import Foundation

/// Debug builds only: launched with `-uiTestSampleData`, the app starts with an in-memory store, onboarding done, and a few
/// fictional contacts and follow-ups, so UI tests (the accessibility audit) can visit real screens. Never in release builds.
enum UITestSeed {
    static var isActive: Bool { ProcessInfo.processInfo.arguments.contains("-uiTestSampleData") }

    @MainActor
    static func makeEnvironment() -> AppEnvironment {
        let defaults = UserDefaults(suiteName: "ui-test-\(UUID().uuidString)")!
        let env = AppEnvironment(inMemory: true, defaults: defaults)
        env.prefs.setOnboardingCompleted(true)
        env.prefs.setMyCard(MyCard(fullName: "Sam Lee", jobTitle: "Founder", company: "Northwind", phone: "+44 20 7946 0958",
                                   email: "sam@northwind.example"))
        let cats = env.contacts.categories()
        let customer = cats.first { $0.system == .customer }?.id
        let supplier = cats.first { $0.system == .supplier }?.id
        let vip = env.contacts.findOrCreateTag("VIP")
        let people: [(Contact, Set<UUID>)] = [
            (Contact(fullName: "Asha Rao", jobTitle: "Senior Software Engineer", company: "Acme Technologies", phone: "+91 98765 43210",
                     email: "asha.rao@acme.example", website: "acme.example", address: "Plot 12, Banjara Hills, Hyderabad",
                     notes: "Met at the expo.", categoryId: customer, isFavorite: true, source: .scan), Set([vip?.id].compactMap { $0 })),
            (Contact(fullName: "Ben Ito", jobTitle: "Procurement Lead", company: "Globex", phone: "+1 555 123 4567",
                     email: "ben@globex.example", categoryId: supplier), []),
            (Contact(fullName: "Carmen Diaz", jobTitle: "Founder", company: "Brightpath Studio", email: "carmen@brightpath.example",
                     isFavorite: true), []),
        ]
        var ids: [UUID] = []
        for (c, tags) in people { if let id = try? env.contacts.save(c, tagIds: tags) { ids.append(id) } }
        let today = Calendar.current.date(bySettingHour: 16, minute: 30, second: 0, of: Date()) ?? Date()
        if ids.count == 3 {
            _ = try? env.followUps.save(FollowUp(contactId: ids[0], type: .quotation, title: "Send revised quotation", dueAt: today))
            _ = try? env.followUps.save(FollowUp(contactId: ids[1], type: .call, title: "Call about delivery dates",
                                                 dueAt: today.addingTimeInterval(86_400)))
            _ = try? env.followUps.save(FollowUp(contactId: ids[2], type: .meeting, title: "Design review", dueAt: today.addingTimeInterval(-2 * 86_400)))
        }
        return env
    }
}
#endif
