import Foundation
import SwiftData

/// Bumped after every write so screens reload (SwiftUI `.task(id:)`), the iOS counterpart of Room's reactive Flows.
@Observable
final class DataRevision {
    private(set) var value = 0
    func bump() { value &+= 1 }
}

/// Contacts, categories and tags. All writes go through here; screens never touch SwiftData directly.
@MainActor
final class ContactRepository {
    private let context: ModelContext
    private let revision: DataRevision
    private let images: CardImageStore

    init(context: ModelContext, revision: DataRevision, images: CardImageStore) {
        self.context = context; self.revision = revision; self.images = images
    }

    // MARK: reads

    func contacts(_ filter: ContactFilter = ContactFilter()) -> [ContactDetails] {
        let all = ((try? context.fetch(FetchDescriptor<ContactEntity>())) ?? []).map(\.details)
        let tokens = filter.query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        let filtered = all.filter { d in
            if filter.favoritesOnly && !d.contact.isFavorite { return false }
            if let cat = filter.categoryId, d.category?.id != cat { return false }
            if !filter.tagIds.isSubset(of: Set(d.tags.map(\.id))) { return false }
            guard !tokens.isEmpty else { return true }
            let c = d.contact
            let haystack = ([c.fullName, c.company, c.jobTitle, c.email, c.emailAlt, c.phone, c.phoneAlt, c.notes, d.category?.displayName ?? ""]
                + d.tags.map(\.name)).joined(separator: " ").lowercased()
            return tokens.allSatisfy { haystack.contains($0) }
        }
        return filtered.sorted(by: Self.comparator(filter.sort))
    }

    func contact(_ id: UUID) -> ContactDetails? { entity(id)?.details }

    func recent(limit: Int) -> [ContactDetails] {
        var d = FetchDescriptor<ContactEntity>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)])
        d.fetchLimit = limit
        return ((try? context.fetch(d)) ?? []).map(\.details)
    }

    func categories() -> [Category] {
        ((try? context.fetch(FetchDescriptor<CategoryEntity>(sortBy: [SortDescriptor(\.sortOrder)]))) ?? []).map(\.domain)
    }

    func tags() -> [Tag] {
        ((try? context.fetch(FetchDescriptor<TagEntity>(sortBy: [SortDescriptor(\.name)]))) ?? []).map(\.domain)
    }

    // MARK: writes

    /// Inserts or updates the contact and replaces its tag links in one save.
    @discardableResult
    func save(_ contact: Contact, tagIds: Set<UUID>) throws -> UUID {
        let e = entity(contact.id) ?? {
            let n = ContactEntity(id: contact.id, fullName: contact.fullName)
            n.createdAt = Date()
            context.insert(n)
            return n
        }()
        e.apply(contact)
        e.updatedAt = Date()
        e.category = contact.categoryId.flatMap(categoryEntity)
        e.tags = tagIds.compactMap(tagEntity)
        try context.save()
        revision.bump()
        return e.id
    }

    func setFavorite(_ id: UUID, _ favorite: Bool) {
        guard let e = entity(id) else { return }
        e.isFavorite = favorite
        e.updatedAt = Date()
        try? context.save()
        revision.bump()
    }

    /// Deletes the contact (follow-ups cascade), then its card images, so a failed delete never orphans a record.
    func delete(_ id: UUID) throws {
        guard let e = entity(id) else { return }
        let paths = [e.frontImagePath, e.backImagePath]
        context.delete(e)
        try context.save()
        paths.forEach { images.delete($0) }
        revision.bump()
    }

    /// Case-insensitive find-or-create. Returns nil for blank names.
    func findOrCreateTag(_ name: String) -> Tag? {
        let n = name.trimmed
        guard !n.isEmpty else { return nil }
        if let existing = ((try? context.fetch(FetchDescriptor<TagEntity>())) ?? []).first(where: { $0.name.lowercased() == n.lowercased() }) {
            return existing.domain
        }
        let t = TagEntity(name: String(n.prefix(40)))
        context.insert(t)
        try? context.save()
        revision.bump()
        return t.domain
    }

    /// Seeds the system categories that are missing (fresh install, or new ones added by an update).
    func seedSystemCategories() {
        let existing = (try? context.fetch(FetchDescriptor<CategoryEntity>())) ?? []
        let keys = Set(existing.compactMap(\.systemKey))
        var order = (existing.map(\.sortOrder).max() ?? -1) + 1
        for c in SystemCategory.allCases where !keys.contains(c.rawValue) {
            context.insert(CategoryEntity(name: "", colorARGB: Int64(c.colorARGB), systemKey: c.rawValue, sortOrder: order))
            order += 1
        }
        try? context.save()
    }

    // MARK: helpers

    private func entity(_ id: UUID) -> ContactEntity? {
        try? context.fetch(FetchDescriptor<ContactEntity>(predicate: #Predicate { $0.id == id })).first
    }

    private func categoryEntity(_ id: UUID) -> CategoryEntity? {
        try? context.fetch(FetchDescriptor<CategoryEntity>(predicate: #Predicate { $0.id == id })).first
    }

    private func tagEntity(_ id: UUID) -> TagEntity? {
        try? context.fetch(FetchDescriptor<TagEntity>(predicate: #Predicate { $0.id == id })).first
    }

    private static func comparator(_ sort: ContactSort) -> (ContactDetails, ContactDetails) -> Bool {
        switch sort {
        case .nameAsc: { $0.contact.fullName.localizedCaseInsensitiveCompare($1.contact.fullName) == .orderedAscending }
        case .nameDesc: { $0.contact.fullName.localizedCaseInsensitiveCompare($1.contact.fullName) == .orderedDescending }
        case .company: { $0.contact.company.localizedCaseInsensitiveCompare($1.contact.company) == .orderedAscending }
        case .recentlyAdded: { $0.contact.createdAt > $1.contact.createdAt }
        case .recentlyUpdated: { $0.contact.updatedAt > $1.contact.updatedAt }
        }
    }
}

/// Follow-ups. Every write also (re)schedules or cancels the local notification, so the stored state and the
/// reminder can never disagree (the iOS counterpart of Android's FollowUpManager).
@MainActor
final class FollowUpRepository {
    private let context: ModelContext
    private let revision: DataRevision
    private let reminders: ReminderScheduler

    init(context: ModelContext, revision: DataRevision, reminders: ReminderScheduler) {
        self.context = context; self.revision = revision; self.reminders = reminders
    }

    func all() -> [FollowUpWithContact] {
        ((try? context.fetch(FetchDescriptor<FollowUpEntity>(sortBy: [SortDescriptor(\.dueAt)]))) ?? []).map(\.withContact)
    }

    func forContact(_ id: UUID) -> [FollowUp] {
        all().map(\.followUp).filter { $0.contactId == id }.sorted { $0.dueAt > $1.dueAt }
    }

    func get(_ id: UUID) -> FollowUpWithContact? { entity(id)?.withContact }

    /// Buckets are derived from the clock: Overdue < start of today <= Today < start of tomorrow <= Upcoming.
    func bucket(_ b: FollowUpBucket, now: Date = Date(), calendar: Calendar = .current) -> [FollowUpWithContact] {
        let start = calendar.startOfDay(for: now)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: start)!
        let items = all()
        switch b {
        case .overdue: return items.filter { $0.followUp.status.isActive && $0.followUp.dueAt < start }
        case .today: return items.filter { $0.followUp.status.isActive && $0.followUp.dueAt >= start && $0.followUp.dueAt < tomorrow }
        case .upcoming: return items.filter { $0.followUp.status.isActive && $0.followUp.dueAt >= tomorrow }
        case .completed:
            return items.filter { !$0.followUp.status.isActive }
                .sorted { ($0.followUp.completedAt ?? $0.followUp.updatedAt) > ($1.followUp.completedAt ?? $1.followUp.updatedAt) }
        }
    }

    func counts(now: Date = Date()) -> FollowUpCounts {
        FollowUpCounts(today: bucket(.today, now: now).count, upcoming: bucket(.upcoming, now: now).count,
                       overdue: bucket(.overdue, now: now).count, completed: bucket(.completed, now: now).count)
    }

    @discardableResult
    func save(_ f: FollowUp) throws -> UUID {
        let existing = entity(f.id)
        let e = existing ?? FollowUpEntity(id: f.id, title: f.title, dueAt: f.dueAt)
        if existing == nil { context.insert(e) }
        // Moving an active follow-up to a new time marks it rescheduled (it stays active and keeps its reminder).
        let rescheduled = existing.map { FollowUpStatus(rawValue: $0.statusRaw)?.isActive == true && $0.dueAt != f.dueAt } ?? false
        e.title = f.title.trimmed; e.notes = f.notes.trimmed; e.typeRaw = f.type.rawValue; e.dueAt = f.dueAt
        e.statusRaw = rescheduled ? FollowUpStatus.rescheduled.rawValue : f.status.rawValue
        e.reminderEnabled = f.reminderEnabled; e.reminderOffsetMinutes = f.reminderOffsetMinutes
        let contactId = f.contactId
        e.contact = try context.fetch(FetchDescriptor<ContactEntity>(predicate: #Predicate { $0.id == contactId })).first
        e.updatedAt = Date()
        try context.save()
        reminders.schedule(e.withContact)
        revision.bump()
        return e.id
    }

    func setStatus(_ id: UUID, _ status: FollowUpStatus) {
        guard let e = entity(id) else { return }
        e.statusRaw = status.rawValue
        e.completedAt = status == .completed ? Date() : nil
        e.updatedAt = Date()
        try? context.save()
        if status.isActive { reminders.schedule(e.withContact) } else { reminders.cancel(id) }
        revision.bump()
    }

    func delete(_ id: UUID) {
        guard let e = entity(id) else { return }
        context.delete(e)
        try? context.save()
        reminders.cancel(id)
        revision.bump()
    }

    /// Re-arms every active reminder (called at launch; iOS keeps scheduled notifications across reboots).
    func rescheduleAll() { reminders.replaceAll(all().filter { $0.followUp.status.isActive }) }

    private func entity(_ id: UUID) -> FollowUpEntity? {
        try? context.fetch(FetchDescriptor<FollowUpEntity>(predicate: #Predicate { $0.id == id })).first
    }
}
