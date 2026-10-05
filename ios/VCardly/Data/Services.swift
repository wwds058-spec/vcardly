import Foundation
import UIKit
import UserNotifications

/// Card photos in Application Support/cards (app-private, excluded from iCloud backup, random UUID names).
/// Writes are atomic (temp file then rename via `.atomic`). Only relative paths are stored in the database.
final class CardImageStore {
    private let root: URL

    init(root: URL? = nil) {
        let base = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.root = base.appendingPathComponent("cards", isDirectory: true)
        try? FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var dir = self.root
        try? dir.setResourceValues(values)
    }

    /// Saves a JPEG and returns its relative path, or nil when it could not be written.
    func save(_ image: UIImage) -> String? {
        guard let data = image.jpegData(compressionQuality: 0.88) else { return nil }
        let name = "\(UUID().uuidString).jpg"
        do {
            try data.write(to: root.appendingPathComponent(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return "cards/\(name)"
        } catch {
            return nil
        }
    }

    func load(_ relativePath: String?, maxDimension: CGFloat = 1400) -> UIImage? {
        guard let url = resolve(relativePath), let image = UIImage(contentsOfFile: url.path) else { return nil }
        let longest = max(image.size.width, image.size.height)
        guard longest > maxDimension else { return image }
        let scale = maxDimension / longest
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        return UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    /// Absolute location of a stored photo (for backups), or nil when the path is not one of ours.
    func fileURL(_ relativePath: String?) -> URL? { resolve(relativePath) }

    /// Copies a verified file (from a restore) into the store under a new random name; returns its relative path.
    func install(_ file: URL) -> String? {
        let name = "\(UUID().uuidString).jpg"
        do {
            try FileManager.default.copyItem(at: file, to: root.appendingPathComponent(name))
            return "cards/\(name)"
        } catch {
            return nil
        }
    }

    func delete(_ relativePath: String?) {
        guard let url = resolve(relativePath) else { return }
        try? FileManager.default.removeItem(at: url)
    }

    /// Refuses anything that escapes the cards directory.
    private func resolve(_ relativePath: String?) -> URL? {
        guard let p = relativePath, p.hasPrefix("cards/"), !p.contains("..") else { return nil }
        let url = root.appendingPathComponent(String(p.dropFirst("cards/".count)))
        return url.standardizedFileURL.path.hasPrefix(root.standardizedFileURL.path) ? url : nil
    }
}

/// Local notifications for follow-up reminders. iOS keeps scheduled notifications across reboots and time changes
/// (calendar triggers follow the device clock and zone); the app re-arms everything at launch as a safety net.
/// iOS allows 64 pending notifications per app, so the soonest 60 are kept.
final class ReminderScheduler {
    private let center = UNUserNotificationCenter.current()
    private static let limit = 60

    func requestPermission() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func schedule(_ item: FollowUpWithContact) {
        let f = item.followUp
        cancel(f.id)
        guard f.status.isActive, f.reminderEnabled else { return }
        let fire = f.dueAt.addingTimeInterval(TimeInterval(-f.reminderOffsetMinutes * 60))
        guard fire > Date() else { return }
        let content = UNMutableNotificationContent()
        // The lock-screen text names the task type and contact only; notes are never included.
        content.title = L10n.s("notif.title")
        content.body = L10n.s("notif.body.\(f.type.rawValue.lowercased())", item.contactName)
        content.sound = .default
        content.userInfo = ["contactId": f.contactId.uuidString]
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
        let request = UNNotificationRequest(identifier: f.id.uuidString, content: content,
                                            trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false))
        center.add(request)
    }

    func cancel(_ id: UUID) {
        center.removePendingNotificationRequests(withIdentifiers: [id.uuidString])
        center.removeDeliveredNotifications(withIdentifiers: [id.uuidString])
    }

    func replaceAll(_ items: [FollowUpWithContact]) {
        center.removeAllPendingNotificationRequests()
        items.filter { $0.followUp.reminderEnabled && $0.followUp.dueAt > Date() }
            .sorted { $0.followUp.dueAt < $1.followUp.dueAt }
            .prefix(Self.limit)
            .forEach(schedule)
    }
}

/// Settings and the user's own card, in UserDefaults (on this device only). Change values through the setters so they persist.
@Observable
final class Preferences {
    @ObservationIgnored private let defaults: UserDefaults

    private(set) var themeMode: ThemeMode
    private(set) var onboardingCompleted: Bool
    private(set) var appLockEnabled: Bool
    private(set) var autoLockSeconds: Int
    private(set) var myCard: MyCard
    private(set) var lastBackupAt: Date?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        themeMode = ThemeMode(rawValue: defaults.string(forKey: "themeMode") ?? "") ?? .system
        onboardingCompleted = defaults.bool(forKey: "onboardingCompleted")
        appLockEnabled = defaults.bool(forKey: "appLockEnabled")
        autoLockSeconds = defaults.object(forKey: "autoLockSeconds") as? Int ?? 60
        lastBackupAt = defaults.object(forKey: "lastBackupAt") as? Date
        myCard = defaults.data(forKey: "myCard").flatMap { try? JSONDecoder().decode(MyCard.self, from: $0) } ?? MyCard()
    }

    func setThemeMode(_ v: ThemeMode) { themeMode = v; defaults.set(v.rawValue, forKey: "themeMode") }
    func setOnboardingCompleted(_ v: Bool) { onboardingCompleted = v; defaults.set(v, forKey: "onboardingCompleted") }
    func setAppLockEnabled(_ v: Bool) { appLockEnabled = v; defaults.set(v, forKey: "appLockEnabled") }
    func setAutoLockSeconds(_ v: Int) { autoLockSeconds = v; defaults.set(v, forKey: "autoLockSeconds") }
    func setLastBackupAt(_ v: Date) { lastBackupAt = v; defaults.set(v, forKey: "lastBackupAt") }
    func setMyCard(_ v: MyCard) { myCard = v; defaults.set(try? JSONEncoder().encode(v), forKey: "myCard") }
}
