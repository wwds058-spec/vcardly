import Foundation

// Plain domain values used by every screen. Persistence types (SwiftData) never leave the Data layer.
// Mirrors the Android domain layer (app/src/main/kotlin/.../domain/model).

enum ContactSource: String, CaseIterable, Codable { case manual = "MANUAL", scan = "SCAN", importFile = "IMPORT" }

enum ContactSort: String, CaseIterable { case nameAsc, nameDesc, company, recentlyAdded, recentlyUpdated }

/// Stored by raw name, like on Android. `message` is kept for data from older versions and not offered in pickers.
enum FollowUpType: String, CaseIterable, Codable {
    case call = "CALL", whatsapp = "WHATSAPP", email = "EMAIL", meeting = "MEETING"
    case quotation = "QUOTATION", payment = "PAYMENT", message = "MESSAGE", other = "OTHER"

    static let pickable: [FollowUpType] = [.call, .whatsapp, .email, .meeting, .quotation, .payment, .other]
}

/// Active statuses appear in Today / Upcoming / Overdue and get reminders; closed ones go to Completed.
enum FollowUpStatus: String, CaseIterable, Codable {
    case pending = "PENDING", rescheduled = "RESCHEDULED", completed = "COMPLETED", cancelled = "CANCELLED"
    var isActive: Bool { self == .pending || self == .rescheduled }
}

enum FollowUpBucket: CaseIterable { case today, upcoming, overdue, completed }

enum ThemeMode: String, CaseIterable { case system, light, dark }

/// Seeded categories; display names come from Localizable.strings. Same keys and colours as Android.
enum SystemCategory: String, CaseIterable {
    case business, customer, client, supplier, vendor, partner, colleague, friend, other

    var colorARGB: UInt32 {
        switch self {
        case .business: 0xFF0277BD
        case .customer: 0xFF2E7D32
        case .client: 0xFF3F51B5
        case .supplier: 0xFFA0522D
        case .vendor: 0xFFF57C00
        case .partner: 0xFF00897B
        case .colleague: 0xFF7B1FA2
        case .friend: 0xFFD81B60
        case .other: 0xFF607D8B
        }
    }
}

struct Contact: Identifiable, Equatable {
    var id: UUID = UUID()
    var fullName: String
    var jobTitle: String = ""
    var company: String = ""
    var phone: String = ""
    var phoneAlt: String = ""
    var email: String = ""
    var emailAlt: String = ""
    var website: String = ""
    var address: String = ""
    var notes: String = ""
    var categoryId: UUID?
    var isFavorite: Bool = false
    var frontImagePath: String?
    var backImagePath: String?
    var source: ContactSource = .manual
    var createdAt: Date = Date()
    var updatedAt: Date = Date()
}

struct Category: Identifiable, Equatable, Hashable {
    var id: UUID = UUID()
    /// Blank for system categories (the UI shows the localized name).
    var name: String
    var colorARGB: UInt32
    var system: SystemCategory?
    var sortOrder: Int = 0

    var displayName: String { system.map { L10n.s("category.\($0.rawValue)") } ?? name }
}

struct Tag: Identifiable, Equatable, Hashable {
    var id: UUID = UUID()
    var name: String
}

struct ContactDetails: Identifiable, Equatable {
    var contact: Contact
    var category: Category?
    var tags: [Tag]
    var id: UUID { contact.id }
}

struct ContactFilter: Equatable {
    var query: String = ""
    var categoryId: UUID?
    var tagIds: Set<UUID> = []
    var favoritesOnly = false
    var sort: ContactSort = .nameAsc
    var hasActiveFilters: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty || categoryId != nil || !tagIds.isEmpty || favoritesOnly }
}

struct FollowUp: Identifiable, Equatable {
    var id: UUID = UUID()
    var contactId: UUID
    var type: FollowUpType = .call
    var status: FollowUpStatus = .pending
    var title: String
    var notes: String = ""
    var dueAt: Date
    var reminderEnabled = true
    var reminderOffsetMinutes = 0
    var completedAt: Date?
    var createdAt: Date = Date()
    var updatedAt: Date = Date()
}

struct FollowUpWithContact: Identifiable, Equatable {
    var followUp: FollowUp
    var contactName: String
    var contactCompany: String
    var id: UUID { followUp.id }
}

struct FollowUpCounts: Equatable {
    var today = 0, upcoming = 0, overdue = 0, completed = 0
    var pending: Int { today + upcoming + overdue }
}

/// The user's own card (what they hand out). Stored on this device only.
struct MyCard: Equatable, Codable {
    var fullName = ""
    var jobTitle = ""
    var company = ""
    var phone = ""
    var phoneAlt = ""
    var email = ""
    var emailAlt = ""
    var website = ""
    var address = ""
    var isEmpty: Bool { fullName.trimmingCharacters(in: .whitespaces).isEmpty }
}

enum ReminderOffsets { static let options = [0, 15, 60, 1440] }
