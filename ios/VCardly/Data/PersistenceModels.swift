import Foundation
import SwiftData

// SwiftData schema. Mirrors Android's Room tables:
// categories --< contacts >--< tags,  contacts --< follow_ups (cascade on delete).
// Enums are stored by raw name so unknown values from newer data fall back safely.

@Model
final class CategoryEntity {
    @Attribute(.unique) var id: UUID
    var name: String
    var colorARGB: Int64
    /// Set for seeded categories; unique so seeding new ones on launch is idempotent.
    var systemKey: String?
    var sortOrder: Int
    var createdAt: Date
    @Relationship(deleteRule: .nullify, inverse: \ContactEntity.category) var contacts: [ContactEntity] = []

    init(id: UUID = UUID(), name: String, colorARGB: Int64, systemKey: String?, sortOrder: Int, createdAt: Date = Date()) {
        self.id = id; self.name = name; self.colorARGB = colorARGB; self.systemKey = systemKey; self.sortOrder = sortOrder; self.createdAt = createdAt
    }
}

@Model
final class TagEntity {
    @Attribute(.unique) var id: UUID
    var name: String
    var createdAt: Date
    @Relationship(deleteRule: .nullify, inverse: \ContactEntity.tags) var contacts: [ContactEntity] = []

    init(id: UUID = UUID(), name: String, createdAt: Date = Date()) { self.id = id; self.name = name; self.createdAt = createdAt }
}

@Model
final class ContactEntity {
    @Attribute(.unique) var id: UUID
    var fullName: String
    var jobTitle: String
    var company: String
    var phone: String
    var phoneAlt: String
    var email: String
    var emailAlt: String
    var website: String
    var address: String
    var notes: String
    var isFavorite: Bool
    /// Relative paths inside Application Support/cards (never absolute, never derived from contact data).
    var frontImagePath: String?
    var backImagePath: String?
    var sourceRaw: String
    var createdAt: Date
    var updatedAt: Date
    var category: CategoryEntity?
    var tags: [TagEntity] = []
    @Relationship(deleteRule: .cascade, inverse: \FollowUpEntity.contact) var followUps: [FollowUpEntity] = []

    init(id: UUID, fullName: String) {
        self.id = id; self.fullName = fullName
        jobTitle = ""; company = ""; phone = ""; phoneAlt = ""; email = ""; emailAlt = ""; website = ""; address = ""; notes = ""
        isFavorite = false; sourceRaw = ContactSource.manual.rawValue; createdAt = Date(); updatedAt = Date()
    }
}

@Model
final class FollowUpEntity {
    @Attribute(.unique) var id: UUID
    var typeRaw: String
    var statusRaw: String
    var title: String
    var notes: String
    var dueAt: Date
    var reminderEnabled: Bool
    var reminderOffsetMinutes: Int
    var completedAt: Date?
    var createdAt: Date
    var updatedAt: Date
    var contact: ContactEntity?

    init(id: UUID, title: String, dueAt: Date) {
        self.id = id; self.title = title; self.dueAt = dueAt
        typeRaw = FollowUpType.call.rawValue; statusRaw = FollowUpStatus.pending.rawValue; notes = ""
        reminderEnabled = true; reminderOffsetMinutes = 0; createdAt = Date(); updatedAt = Date()
    }
}

extension CategoryEntity {
    var domain: Category {
        Category(id: id, name: name, colorARGB: UInt32(truncatingIfNeeded: colorARGB), system: systemKey.flatMap(SystemCategory.init(rawValue:)), sortOrder: sortOrder)
    }
}

extension TagEntity {
    var domain: Tag { Tag(id: id, name: name) }
}

extension ContactEntity {
    var domain: Contact {
        Contact(id: id, fullName: fullName, jobTitle: jobTitle, company: company, phone: phone, phoneAlt: phoneAlt, email: email,
                emailAlt: emailAlt, website: website, address: address, notes: notes, categoryId: category?.id, isFavorite: isFavorite,
                frontImagePath: frontImagePath, backImagePath: backImagePath, source: ContactSource(rawValue: sourceRaw) ?? .manual,
                createdAt: createdAt, updatedAt: updatedAt)
    }

    var details: ContactDetails {
        ContactDetails(contact: domain, category: category?.domain, tags: tags.map(\.domain).sorted { $0.name.lowercased() < $1.name.lowercased() })
    }

    func apply(_ c: Contact) {
        fullName = c.fullName.trimmed; jobTitle = c.jobTitle.trimmed; company = c.company.trimmed; phone = c.phone.trimmed
        phoneAlt = c.phoneAlt.trimmed; email = c.email.trimmed; emailAlt = c.emailAlt.trimmed; website = c.website.trimmed
        address = c.address.trimmed; notes = c.notes.trimmed; isFavorite = c.isFavorite
        frontImagePath = c.frontImagePath; backImagePath = c.backImagePath; sourceRaw = c.source.rawValue
    }
}

extension FollowUpEntity {
    var domain: FollowUp {
        FollowUp(id: id, contactId: contact?.id ?? UUID(), type: FollowUpType(rawValue: typeRaw) ?? .other,
                 status: FollowUpStatus(rawValue: statusRaw) ?? .pending, title: title, notes: notes, dueAt: dueAt,
                 reminderEnabled: reminderEnabled, reminderOffsetMinutes: reminderOffsetMinutes, completedAt: completedAt,
                 createdAt: createdAt, updatedAt: updatedAt)
    }

    var withContact: FollowUpWithContact {
        FollowUpWithContact(followUp: domain, contactName: contact?.fullName ?? "", contactCompany: contact?.company ?? "")
    }
}
