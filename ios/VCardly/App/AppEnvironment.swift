import Foundation
import LocalAuthentication
import SwiftData
import SwiftUI

/// Everything screens need, created once at launch and passed down with `.environment(env)`.
/// The iOS counterpart of Android's Hilt graph.
@MainActor
@Observable
final class AppEnvironment {
    @ObservationIgnored let container: ModelContainer
    @ObservationIgnored let contacts: ContactRepository
    @ObservationIgnored let followUps: FollowUpRepository
    @ObservationIgnored let images: CardImageStore
    @ObservationIgnored let reminders: ReminderScheduler
    let revision: DataRevision
    let prefs: Preferences
    let lock: AppLock

    init(inMemory: Bool = false, defaults: UserDefaults = .standard) {
        let schema = Schema([ContactEntity.self, CategoryEntity.self, TagEntity.self, FollowUpEntity.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        // The store lives in app-private Application Support; there is no destructive fallback (contacts are irreplaceable).
        container = try! ModelContainer(for: schema, configurations: [config])
        revision = DataRevision()
        images = CardImageStore()
        reminders = ReminderScheduler()
        prefs = Preferences(defaults: defaults)
        lock = AppLock()
        contacts = ContactRepository(context: container.mainContext, revision: revision, images: images)
        followUps = FollowUpRepository(context: container.mainContext, revision: revision, reminders: reminders)
        contacts.seedSystemCategories()
    }
}

/// App lock with auto-lock after time away. Uses the system clock's monotonic uptime so changing the date cannot bypass it.
@Observable
final class AppLock {
    private(set) var locked = true
    @ObservationIgnored private var backgroundedAt: TimeInterval?

    func unlock() { locked = false; backgroundedAt = nil }

    func didEnterBackground() { backgroundedAt = ProcessInfo.processInfo.systemUptime }

    func willEnterForeground(enabled: Bool, timeoutSeconds: Int) {
        guard enabled else { locked = false; return }
        if let t = backgroundedAt, ProcessInfo.processInfo.systemUptime - t >= Double(timeoutSeconds) { locked = true }
    }

    /// Face ID / Touch ID with the device passcode as fallback. Biometric data never reaches the app.
    func authenticate(reason: String) async -> Bool {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { return false }
        return (try? await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }

    var deviceCanAuthenticate: Bool { LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) }
}
