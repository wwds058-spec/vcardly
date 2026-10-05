import Foundation
import SwiftData

enum RestoreMode { case merge, replace }

struct RestoreSummary: Equatable {
    let contactsAdded: Int
    let duplicatesSkipped: Int
    let followUps: Int
    let images: Int
}

/// A backup that has been fully read and verified into a private staging folder. Nothing on the device has changed yet.
struct PreparedBackup {
    let manifest: BackupManifest
    let data: BackupData
    let images: Set<String>
    let workDir: URL
    var stage: URL { workDir.appendingPathComponent("stage", isDirectory: true) }
}

/// Creates and restores `vcardly-backup-v1` files (the Android format). Nothing is logged and nothing leaves the device:
/// the user chooses where the file goes with the system file picker. Restore is staged: the whole file is read and verified
/// first, so a damaged or wrong-password file changes nothing; the database part is then saved in one go, and rolled back
/// (with any copied photos removed) if that fails.
@MainActor
final class BackupService {
    private let context: ModelContext
    private let images: CardImageStore
    private let prefs: Preferences
    private let followUps: FollowUpRepository
    private let revision: DataRevision

    init(context: ModelContext, images: CardImageStore, prefs: Preferences, followUps: FollowUpRepository, revision: DataRevision) {
        self.context = context; self.images = images; self.prefs = prefs; self.followUps = followUps; self.revision = revision
    }

    private var appVersion: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "" }

    private static func newWorkDir() throws -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("backup-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Removes a work folder (the created file once it has been saved elsewhere, or a staged restore).
    nonisolated static func cleanUp(_ dir: URL) { try? FileManager.default.removeItem(at: dir) }

    // MARK: create

    struct CreatedBackup {
        let file: URL
        let workDir: URL
        let counts: BackupCounts
    }

    /// Writes the backup into a private temporary folder; the caller hands it to the file exporter, then calls `cleanUp`.
    func create(password: String?, now: Date = Date()) async throws -> CreatedBackup {
        let (data, sources) = snapshot()
        let version = appVersion
        let dir: URL
        do { dir = try Self.newWorkDir() } catch { throw BackupError.io }
        let day = now.formatted(.iso8601.year().month().day())
        let file = dir.appendingPathComponent("vcardly-backup-\(day).\(BackupFormat.fileExtension)")
        do {
            // Key derivation and file writing run off the main thread.
            try await Task.detached(priority: .userInitiated) {
                try BackupArchive.write(data, images: sources, appVersion: version, createdAt: now, to: file, password: password, workDir: dir)
            }.value
        } catch {
            Self.cleanUp(dir)
            throw (error as? BackupError) ?? .io
        }
        prefs.setLastBackupAt(now)
        return CreatedBackup(file: file, workDir: dir, counts: BackupCounts(
            contacts: data.contacts.count, categories: data.categories.count, tags: data.tags.count,
            followUps: data.followUps.count, images: sources.count))
    }

    /// One consistent copy of everything, with ids that exist only inside the file.
    func snapshot() -> (BackupData, [BackupArchive.ImageSource]) {
        let categories = (try? context.fetch(FetchDescriptor<CategoryEntity>(sortBy: [SortDescriptor(\.sortOrder)]))) ?? []
        let tags = (try? context.fetch(FetchDescriptor<TagEntity>(sortBy: [SortDescriptor(\.name)]))) ?? []
        let contacts = (try? context.fetch(FetchDescriptor<ContactEntity>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []
        let ups = (try? context.fetch(FetchDescriptor<FollowUpEntity>(sortBy: [SortDescriptor(\.dueAt)]))) ?? []

        var categoryIds: [UUID: Int64] = [:], tagIds: [UUID: Int64] = [:], contactIds: [UUID: Int64] = [:]
        let bCategories = categories.enumerated().map { i, c -> BCategory in
            categoryIds[c.id] = Int64(i + 1)
            return BCategory(id: Int64(i + 1), name: c.name, colorArgb: c.colorARGB, systemKey: c.systemKey, sortOrder: c.sortOrder, createdAt: c.createdAt.millis)
        }
        let bTags = tags.enumerated().map { i, t -> BTag in
            tagIds[t.id] = Int64(i + 1)
            return BTag(id: Int64(i + 1), name: t.name, createdAt: t.createdAt.millis)
        }

        // Only photos that really exist are referenced, so a backup never points at a missing file.
        var sources: [String: BackupArchive.ImageSource] = [:]
        func imageName(_ path: String?) -> String? {
            guard let url = images.fileURL(path), FileManager.default.fileExists(atPath: url.path) else { return nil }
            let name = url.lastPathComponent
            guard BackupFormat.isSafeImageEntry(BackupFormat.imagesDir + name) else { return nil }
            sources[name] = BackupArchive.ImageSource(name: name, url: url)
            return name
        }

        let bContacts = contacts.enumerated().map { i, c -> BContact in
            contactIds[c.id] = Int64(i + 1)
            var b = BContact(id: Int64(i + 1), fullName: c.fullName)
            b.jobTitle = c.jobTitle; b.company = c.company; b.phone = c.phone; b.phoneAlt = c.phoneAlt; b.email = c.email
            b.emailAlt = c.emailAlt; b.website = c.website; b.address = c.address; b.notes = c.notes
            b.categoryId = c.category.flatMap { categoryIds[$0.id] }; b.isFavorite = c.isFavorite
            b.frontImage = imageName(c.frontImagePath); b.backImage = imageName(c.backImagePath)
            b.source = c.sourceRaw; b.createdAt = c.createdAt.millis; b.updatedAt = c.updatedAt.millis
            b.tagIds = c.tags.compactMap { tagIds[$0.id] }.sorted()
            return b
        }
        let bFollowUps = ups.enumerated().compactMap { i, f -> BFollowUp? in
            guard let cid = f.contact.flatMap({ contactIds[$0.id] }) else { return nil }
            var b = BFollowUp(id: Int64(i + 1), contactId: cid, title: f.title)
            b.type = f.typeRaw; b.status = f.statusRaw; b.notes = f.notes; b.dueAt = f.dueAt.millis
            b.reminderEnabled = f.reminderEnabled; b.reminderOffsetMinutes = f.reminderOffsetMinutes
            b.completedAt = f.completedAt?.millis; b.createdAt = f.createdAt.millis; b.updatedAt = f.updatedAt.millis
            return b
        }
        let card = prefs.myCard
        let data = BackupData(categories: bCategories, tags: bTags, contacts: bContacts, followUps: bFollowUps,
                              myCard: card.isEmpty ? nil : BMyCard(card))
        return (data, sources.values.sorted { $0.name < $1.name })
    }

    // MARK: restore

    /// Reads and verifies the whole file into a private staging folder. Call `restore` or `discard` afterwards.
    func prepare(_ url: URL, password: String?) async throws -> PreparedBackup {
        let dir: URL
        do { dir = try Self.newWorkDir() } catch { throw BackupError.io }
        let stage = dir.appendingPathComponent("stage", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: stage, withIntermediateDirectories: true)
            let contents = try await Task.detached(priority: .userInitiated) {
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                return try BackupArchive.read(url, password: password, workDir: dir, stage: stage)
            }.value
            return PreparedBackup(manifest: contents.manifest, data: contents.data, images: contents.images, workDir: dir)
        } catch {
            Self.cleanUp(dir)
            throw (error as? BackupError) ?? .io
        }
    }

    func discard(_ p: PreparedBackup) { Self.cleanUp(p.workDir) }

    func restore(_ p: PreparedBackup, mode: RestoreMode) throws -> RestoreSummary {
        defer { discard(p) }
        let summary: RestoreSummary
        switch mode {
        case .replace: summary = try replaceAll(p)
        case .merge: summary = try merge(p)
        }
        followUps.rescheduleAll()
        revision.bump()
        return summary
    }

    /// Copies a staged photo into the card store under a new name. Returns nil when the backup has no such photo.
    private func install(_ name: String?, from p: PreparedBackup, into installed: inout [String]) -> String? {
        guard let name, p.images.contains(name) else { return nil }
        let path = images.install(p.stage.appendingPathComponent(name))
        if let path { installed.append(path) }
        return path
    }

    private func replaceAll(_ p: PreparedBackup) throws -> RestoreSummary {
        let d = p.data
        let oldContacts = (try? context.fetch(FetchDescriptor<ContactEntity>())) ?? []
        let oldPaths = oldContacts.flatMap { [$0.frontImagePath, $0.backImagePath] }
        var installed: [String] = []
        var paths: [Int64: (String?, String?)] = [:]
        for c in d.contacts { paths[c.id] = (install(c.frontImage, from: p, into: &installed), install(c.backImage, from: p, into: &installed)) }

        var followUpCount = 0
        do {
            for f in (try context.fetch(FetchDescriptor<FollowUpEntity>())) { context.delete(f) }
            for c in oldContacts { context.delete(c) }
            for t in (try context.fetch(FetchDescriptor<TagEntity>())) { context.delete(t) }
            for c in (try context.fetch(FetchDescriptor<CategoryEntity>())) { context.delete(c) }

            var categories: [Int64: CategoryEntity] = [:]
            var keys = Set<String>()
            for b in d.categories {
                let e = categoryEntity(b, sortOrder: b.sortOrder, keepSystemKey: { key in keys.insert(key).inserted })
                context.insert(e)
                categories[b.id] = e
            }
            var order = (d.categories.map(\.sortOrder).max() ?? -1) + 1
            for s in SystemCategory.allCases where !keys.contains(s.rawValue) {
                context.insert(CategoryEntity(name: "", colorARGB: Int64(s.colorARGB), systemKey: s.rawValue, sortOrder: order))
                order += 1
            }
            var tags: [Int64: TagEntity] = [:]
            for b in d.tags {
                let e = TagEntity(name: b.name.trimmed.capped(80), createdAt: b.createdAt > 0 ? Date(millis: b.createdAt) : Date())
                context.insert(e)
                tags[b.id] = e
            }
            var contacts: [Int64: ContactEntity] = [:]
            for b in d.contacts {
                let e = contactEntity(b, front: paths[b.id]?.0, back: paths[b.id]?.1)
                context.insert(e)
                e.category = b.categoryId.flatMap { categories[$0] }
                e.tags = uniqueTags(b.tagIds.compactMap { tags[$0] })
                contacts[b.id] = e
            }
            for b in d.followUps {
                guard let owner = contacts[b.contactId] else { continue }
                let e = followUpEntity(b)
                context.insert(e)
                e.contact = owner
                followUpCount += 1
            }
            try context.save()
        } catch {
            context.rollback()
            installed.forEach { images.delete($0) }
            throw BackupError.corrupt
        }
        // The database now points at the new photos only; the old files can go.
        oldPaths.forEach { images.delete($0) }
        if let card = d.myCard { prefs.setMyCard(card.myCard) }
        return RestoreSummary(contactsAdded: d.contacts.count, duplicatesSkipped: 0, followUps: followUpCount, images: installed.count)
    }

    private func merge(_ p: PreparedBackup) throws -> RestoreSummary {
        let existingContacts = ((try? context.fetch(FetchDescriptor<ContactEntity>())) ?? []).map(\.domain)
        let existingCategoryEntities = (try? context.fetch(FetchDescriptor<CategoryEntity>())) ?? []
        let plan = RestorePlanner.planMerge(p.data, existingContacts: existingContacts, existingCategories: existingCategoryEntities.map(\.domain))

        var installed: [String] = []
        let prepared = plan.contacts.map { pc in
            (pc, install(pc.contact.frontImage, from: p, into: &installed), install(pc.contact.backImage, from: p, into: &installed))
        }
        var followUpCount = 0
        do {
            var categoriesById = Dictionary(existingCategoryEntities.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            var created: [Int64: CategoryEntity] = [:]
            var nextOrder = (existingCategoryEntities.map(\.sortOrder).max() ?? -1) + 1
            var tagsByName = Dictionary(((try? context.fetch(FetchDescriptor<TagEntity>())) ?? []).map { ($0.name.lowercased(), $0) },
                                        uniquingKeysWith: { a, _ in a })
            for (pc, front, back) in prepared {
                let category: CategoryEntity?
                switch pc.category {
                case .none: category = nil
                case .existing(let id): category = categoriesById[id]
                case .create(let b):
                    if let made = created[b.id] {
                        category = made
                    } else {
                        let e = categoryEntity(b, sortOrder: nextOrder, keepSystemKey: { _ in false })
                        nextOrder += 1
                        context.insert(e)
                        created[b.id] = e
                        categoriesById[e.id] = e
                        category = e
                    }
                }
                let e = contactEntity(pc.contact, front: front, back: back)
                context.insert(e)
                e.category = category
                e.tags = pc.tagNames.map { name in
                    let key = name.trimmed.capped(80).lowercased()
                    if let t = tagsByName[key] { return t }
                    let t = TagEntity(name: name.trimmed.capped(80))
                    context.insert(t)
                    tagsByName[key] = t
                    return t
                }
                for b in pc.followUps {
                    let f = followUpEntity(b)
                    context.insert(f)
                    f.contact = e
                    followUpCount += 1
                }
            }
            try context.save()
        } catch {
            context.rollback()
            installed.forEach { images.delete($0) }
            throw BackupError.corrupt
        }
        if let card = p.data.myCard, prefs.myCard.isEmpty { prefs.setMyCard(card.myCard) }
        return RestoreSummary(contactsAdded: plan.contacts.count, duplicatesSkipped: plan.skippedDuplicates, followUps: followUpCount, images: installed.count)
    }

    // MARK: mapping (field sizes are bounded like on Android)

    /// A known system key stays a system category (once); anything else becomes a custom category with a readable name.
    private func categoryEntity(_ b: BCategory, sortOrder: Int, keepSystemKey: (String) -> Bool) -> CategoryEntity {
        let created = b.createdAt > 0 ? Date(millis: b.createdAt) : Date()
        if let key = b.systemKey, SystemCategory(rawValue: key) != nil, keepSystemKey(key) {
            return CategoryEntity(name: "", colorARGB: b.colorArgb, systemKey: key, sortOrder: sortOrder, createdAt: created)
        }
        let fallback = b.systemKey.flatMap { SystemCategory(rawValue: $0).map { L10n.s("category.\($0.rawValue)") } ?? $0.capitalized } ?? ""
        let name = b.name.trimmed.isEmpty ? fallback : b.name.trimmed
        return CategoryEntity(name: name.capped(80), colorARGB: b.colorArgb, systemKey: nil, sortOrder: sortOrder, createdAt: created)
    }

    private func contactEntity(_ b: BContact, front: String?, back: String?) -> ContactEntity {
        let name = b.fullName.trimmed.capped(200)
        let e = ContactEntity(id: UUID(), fullName: name.isEmpty ? "?" : name)
        e.jobTitle = b.jobTitle.capped(300); e.company = b.company.capped(300); e.phone = b.phone.capped(100); e.phoneAlt = b.phoneAlt.capped(100)
        e.email = b.email.capped(300); e.emailAlt = b.emailAlt.capped(300); e.website = b.website.capped(500)
        e.address = b.address.capped(1000); e.notes = b.notes.capped(20_000); e.isFavorite = b.isFavorite
        e.frontImagePath = front; e.backImagePath = back
        e.sourceRaw = (ContactSource(rawValue: b.source) ?? .importFile).rawValue
        e.createdAt = b.createdAt > 0 ? Date(millis: b.createdAt) : Date()
        e.updatedAt = b.updatedAt > 0 ? Date(millis: b.updatedAt) : e.createdAt
        return e
    }

    private func followUpEntity(_ b: BFollowUp) -> FollowUpEntity {
        let title = b.title.trimmed.capped(300)
        let e = FollowUpEntity(id: UUID(), title: title.isEmpty ? "?" : title, dueAt: Date(millis: b.dueAt))
        e.typeRaw = (FollowUpType(rawValue: b.type) ?? .other).rawValue
        e.statusRaw = (FollowUpStatus(rawValue: b.status) ?? .pending).rawValue
        e.notes = b.notes.capped(5_000); e.reminderEnabled = b.reminderEnabled
        e.reminderOffsetMinutes = max(0, min(b.reminderOffsetMinutes, 10_080)) // at most a week before
        e.completedAt = b.completedAt.map(Date.init(millis:))
        e.createdAt = b.createdAt > 0 ? Date(millis: b.createdAt) : Date()
        e.updatedAt = b.updatedAt > 0 ? Date(millis: b.updatedAt) : e.createdAt
        return e
    }

    private func uniqueTags(_ tags: [TagEntity]) -> [TagEntity] {
        var seen = Set<UUID>()
        return tags.filter { seen.insert($0.id).inserted }
    }
}
