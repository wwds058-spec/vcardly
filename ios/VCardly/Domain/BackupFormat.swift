import Foundation

// "vcardly-backup-v1": the same file format as the Android app (domain/backup/BackupModels.kt), so a backup made on one
// platform restores on the other. Ids are the integers used inside the file only; they never become database ids.
// Every optional field decodes with a default and unknown keys are ignored, like Android's `ignoreUnknownKeys`.

enum BackupFormat {
    static let id = "vcardly-backup-v1"
    static let formatVersion = 1
    static let manifest = "manifest.json"
    static let data = "data.json"
    static let imagesDir = "images/"
    static let fileExtension = "vcbackup"
    static let minPasswordLength = 8

    static let maxEntries = 50_000
    static let maxManifestBytes = 1 * 1024 * 1024
    static let maxDataBytes = 128 * 1024 * 1024
    static let maxImageBytes = 30 * 1024 * 1024
    static let maxTotalBytes: Int64 = 4 * 1024 * 1024 * 1024

    static func isSafeImageEntry(_ name: String) -> Bool {
        guard name.hasPrefix(imagesDir) else { return false }
        let file = name.dropFirst(imagesDir.count)
        return (1...100).contains(file.count) && file.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "_" || $0 == "-") }
    }
}

/// Why a backup could not be made or read. Each maps to its own message; nothing is changed on failure.
enum BackupError: Error, Equatable {
    case notABackup, newerVersion, needsPassword, wrongPasswordOrCorrupt, corrupt, tooLarge, io

    var messageKey: String {
        switch self {
        case .notABackup: "backup.error.not_a_backup"
        case .newerVersion: "backup.error.newer"
        case .needsPassword: "backup.password_prompt"
        case .wrongPasswordOrCorrupt: "backup.error.wrong_password"
        case .corrupt: "backup.error.corrupt"
        case .tooLarge: "backup.error.too_large"
        case .io: "backup.error.io"
        }
    }
}

private extension KeyedDecodingContainer {
    func v<T: Decodable>(_ key: Key, _ fallback: T) throws -> T { try decodeIfPresent(T.self, forKey: key) ?? fallback }
}

struct BackupCounts: Codable, Equatable {
    var contacts = 0, categories = 0, tags = 0, followUps = 0, images = 0

    init(contacts: Int = 0, categories: Int = 0, tags: Int = 0, followUps: Int = 0, images: Int = 0) {
        self.contacts = contacts; self.categories = categories; self.tags = tags; self.followUps = followUps; self.images = images
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        contacts = try c.v(.contacts, 0); categories = try c.v(.categories, 0); tags = try c.v(.tags, 0)
        followUps = try c.v(.followUps, 0); images = try c.v(.images, 0)
    }
}

struct BackupManifest: Codable, Equatable {
    var format: String
    var formatVersion: Int
    var createdAt: Int64 = 0
    var appVersion = ""
    var encrypted = false
    var counts = BackupCounts()
    /// Entry name -> lowercase hex SHA-256 of that entry's bytes.
    var files: [String: String] = [:]

    init(format: String, formatVersion: Int, createdAt: Int64, appVersion: String, encrypted: Bool, counts: BackupCounts, files: [String: String]) {
        self.format = format; self.formatVersion = formatVersion; self.createdAt = createdAt; self.appVersion = appVersion
        self.encrypted = encrypted; self.counts = counts; self.files = files
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        format = try c.decode(String.self, forKey: .format)
        formatVersion = try c.decode(Int.self, forKey: .formatVersion)
        createdAt = try c.v(.createdAt, 0); appVersion = try c.v(.appVersion, ""); encrypted = try c.v(.encrypted, false)
        counts = try c.v(.counts, BackupCounts()); files = try c.v(.files, [:])
    }

    var createdDate: Date? { createdAt > 0 ? Date(millis: createdAt) : nil }
}

struct BCategory: Codable, Equatable {
    var id: Int64
    var name = ""
    var colorArgb: Int64 = 0xFF607D8B
    var systemKey: String?
    var sortOrder = 0
    var createdAt: Int64 = 0

    init(id: Int64, name: String, colorArgb: Int64, systemKey: String?, sortOrder: Int, createdAt: Int64) {
        self.id = id; self.name = name; self.colorArgb = colorArgb; self.systemKey = systemKey; self.sortOrder = sortOrder; self.createdAt = createdAt
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        name = try c.v(.name, ""); colorArgb = try c.v(.colorArgb, 0xFF607D8B); systemKey = try c.decodeIfPresent(String.self, forKey: .systemKey)
        sortOrder = try c.v(.sortOrder, 0); createdAt = try c.v(.createdAt, 0)
    }

    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(name, forKey: .name); try c.encode(colorArgb, forKey: .colorArgb)
        try c.encode(systemKey, forKey: .systemKey); try c.encode(sortOrder, forKey: .sortOrder); try c.encode(createdAt, forKey: .createdAt)
    }
}

struct BTag: Codable, Equatable {
    var id: Int64
    var name: String
    var colorArgb: Int64?
    var createdAt: Int64 = 0

    init(id: Int64, name: String, createdAt: Int64) { self.id = id; self.name = name; self.createdAt = createdAt }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id); name = try c.decode(String.self, forKey: .name)
        colorArgb = try c.decodeIfPresent(Int64.self, forKey: .colorArgb); createdAt = try c.v(.createdAt, 0)
    }

    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(name, forKey: .name); try c.encode(colorArgb, forKey: .colorArgb); try c.encode(createdAt, forKey: .createdAt)
    }
}

struct BContact: Codable, Equatable {
    var id: Int64
    var fullName: String
    var jobTitle = "", company = "", phone = "", phoneAlt = "", email = "", emailAlt = "", website = "", address = "", notes = ""
    var categoryId: Int64?
    var isFavorite = false
    /// File name under images/ (never a device path).
    var frontImage: String?
    var backImage: String?
    var source = "MANUAL"
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0
    var tagIds: [Int64] = []

    init(id: Int64, fullName: String) { self.id = id; self.fullName = fullName }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id); fullName = try c.decode(String.self, forKey: .fullName)
        jobTitle = try c.v(.jobTitle, ""); company = try c.v(.company, ""); phone = try c.v(.phone, ""); phoneAlt = try c.v(.phoneAlt, "")
        email = try c.v(.email, ""); emailAlt = try c.v(.emailAlt, ""); website = try c.v(.website, ""); address = try c.v(.address, "")
        notes = try c.v(.notes, ""); categoryId = try c.decodeIfPresent(Int64.self, forKey: .categoryId); isFavorite = try c.v(.isFavorite, false)
        frontImage = try c.decodeIfPresent(String.self, forKey: .frontImage); backImage = try c.decodeIfPresent(String.self, forKey: .backImage)
        source = try c.v(.source, "MANUAL"); createdAt = try c.v(.createdAt, 0); updatedAt = try c.v(.updatedAt, 0); tagIds = try c.v(.tagIds, [])
    }

    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(fullName, forKey: .fullName); try c.encode(jobTitle, forKey: .jobTitle)
        try c.encode(company, forKey: .company); try c.encode(phone, forKey: .phone); try c.encode(phoneAlt, forKey: .phoneAlt)
        try c.encode(email, forKey: .email); try c.encode(emailAlt, forKey: .emailAlt); try c.encode(website, forKey: .website)
        try c.encode(address, forKey: .address); try c.encode(notes, forKey: .notes); try c.encode(categoryId, forKey: .categoryId)
        try c.encode(isFavorite, forKey: .isFavorite); try c.encode(frontImage, forKey: .frontImage); try c.encode(backImage, forKey: .backImage)
        try c.encode(source, forKey: .source); try c.encode(createdAt, forKey: .createdAt); try c.encode(updatedAt, forKey: .updatedAt)
        try c.encode(tagIds, forKey: .tagIds)
    }
}

struct BFollowUp: Codable, Equatable {
    var id: Int64
    var contactId: Int64
    var title: String
    var type = "OTHER"
    var status = "PENDING"
    var notes = ""
    var dueAt: Int64 = 0
    var reminderEnabled = true
    var reminderOffsetMinutes = 0
    var completedAt: Int64?
    var notifiedAt: Int64?
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0

    init(id: Int64, contactId: Int64, title: String) { self.id = id; self.contactId = contactId; self.title = title }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id); contactId = try c.decode(Int64.self, forKey: .contactId); title = try c.decode(String.self, forKey: .title)
        type = try c.v(.type, "OTHER"); status = try c.v(.status, "PENDING"); notes = try c.v(.notes, ""); dueAt = try c.v(.dueAt, 0)
        reminderEnabled = try c.v(.reminderEnabled, true); reminderOffsetMinutes = try c.v(.reminderOffsetMinutes, 0)
        completedAt = try c.decodeIfPresent(Int64.self, forKey: .completedAt); notifiedAt = try c.decodeIfPresent(Int64.self, forKey: .notifiedAt)
        createdAt = try c.v(.createdAt, 0); updatedAt = try c.v(.updatedAt, 0)
    }

    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(contactId, forKey: .contactId); try c.encode(title, forKey: .title)
        try c.encode(type, forKey: .type); try c.encode(status, forKey: .status); try c.encode(notes, forKey: .notes)
        try c.encode(dueAt, forKey: .dueAt); try c.encode(reminderEnabled, forKey: .reminderEnabled)
        try c.encode(reminderOffsetMinutes, forKey: .reminderOffsetMinutes); try c.encode(completedAt, forKey: .completedAt)
        try c.encode(notifiedAt, forKey: .notifiedAt); try c.encode(createdAt, forKey: .createdAt); try c.encode(updatedAt, forKey: .updatedAt)
    }
}

struct BMyCard: Codable, Equatable {
    var fullName = "", jobTitle = "", company = "", phone = "", phoneAlt = "", email = "", emailAlt = "", website = "", address = ""

    init(_ m: MyCard) {
        fullName = m.fullName; jobTitle = m.jobTitle; company = m.company; phone = m.phone; phoneAlt = m.phoneAlt
        email = m.email; emailAlt = m.emailAlt; website = m.website; address = m.address
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        fullName = try c.v(.fullName, ""); jobTitle = try c.v(.jobTitle, ""); company = try c.v(.company, ""); phone = try c.v(.phone, "")
        phoneAlt = try c.v(.phoneAlt, ""); email = try c.v(.email, ""); emailAlt = try c.v(.emailAlt, ""); website = try c.v(.website, "")
        address = try c.v(.address, "")
    }

    var myCard: MyCard {
        MyCard(fullName: fullName.capped(200), jobTitle: jobTitle.capped(300), company: company.capped(300), phone: phone.capped(100),
               phoneAlt: phoneAlt.capped(100), email: email.capped(300), emailAlt: emailAlt.capped(300), website: website.capped(500),
               address: address.capped(1000))
    }
}

struct BackupData: Codable, Equatable {
    var categories: [BCategory] = []
    var tags: [BTag] = []
    var contacts: [BContact] = []
    var followUps: [BFollowUp] = []
    var myCard: BMyCard?

    init(categories: [BCategory] = [], tags: [BTag] = [], contacts: [BContact] = [], followUps: [BFollowUp] = [], myCard: BMyCard? = nil) {
        self.categories = categories; self.tags = tags; self.contacts = contacts; self.followUps = followUps; self.myCard = myCard
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        categories = try c.v(.categories, []); tags = try c.v(.tags, []); contacts = try c.v(.contacts, [])
        followUps = try c.v(.followUps, []); myCard = try c.decodeIfPresent(BMyCard.self, forKey: .myCard)
    }

    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: CodingKeys.self)
        try c.encode(categories, forKey: .categories); try c.encode(tags, forKey: .tags); try c.encode(contacts, forKey: .contacts)
        try c.encode(followUps, forKey: .followUps); try c.encode(myCard, forKey: .myCard)
    }
}

extension String {
    /// Bounds field sizes so a crafted file cannot store megabytes in a name field (same limits as Android).
    func capped(_ max: Int) -> String { count <= max ? self : String(prefix(max)) }
}

extension Date {
    init(millis: Int64) { self.init(timeIntervalSince1970: TimeInterval(millis) / 1000) }
    var millis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
}

// MARK: - Merge planning (port of Android's RestorePlanner and DuplicateDetector)

enum DuplicateDetector {
    /// Same email (case-insensitive), or same phone (last 9 digits, so country prefixes do not matter), or same name + company.
    static func isDuplicate(_ candidate: Contact, _ existing: [Contact]) -> Bool {
        let e = emails(candidate), p = phones(candidate), id = identity(candidate)
        return existing.contains { x in
            (!e.isEmpty && !e.isDisjoint(with: emails(x))) || (!p.isEmpty && !p.isDisjoint(with: phones(x))) || (id != nil && id == identity(x))
        }
    }

    private static func emails(_ c: Contact) -> Set<String> {
        Set([c.email, c.emailAlt].map { $0.trimmed.lowercased() }.filter { !$0.isEmpty })
    }

    private static func phones(_ c: Contact) -> Set<String> {
        Set([c.phone, c.phoneAlt].map { $0.filter(\.isASCIIDigit) }.filter { $0.count >= 7 }.map { String($0.suffix(9)) })
    }

    private static func identity(_ c: Contact) -> String? {
        let name = c.fullName.trimmed.lowercased().split(whereSeparator: \.isWhitespace).joined(separator: " ")
        return name.isEmpty ? nil : name + "|" + c.company.trimmed.lowercased()
    }
}

enum CategoryTarget: Equatable {
    case none
    case existing(UUID)
    case create(BCategory)
}

struct PlannedContact: Equatable {
    let contact: BContact
    let category: CategoryTarget
    let tagNames: [String]
    let followUps: [BFollowUp]
}

struct MergePlan: Equatable {
    let contacts: [PlannedContact]
    let skippedDuplicates: Int
}

/// "Merge" restore: add what is missing, never overwrite or delete what is on the device.
enum RestorePlanner {
    static func planMerge(_ data: BackupData, existingContacts: [Contact], existingCategories: [Category]) -> MergePlan {
        let backupCategories = Dictionary(data.categories.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let tagNames = Dictionary(data.tags.map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })
        let followUpsByContact = Dictionary(grouping: data.followUps, by: \.contactId)

        func target(_ categoryId: Int64?) -> CategoryTarget {
            guard let id = categoryId, let c = backupCategories[id] else { return .none }
            let match = existingCategories.first { e in
                if let key = c.systemKey { return e.system?.rawValue == key }
                return e.system == nil && e.name.trimmed.lowercased() == c.name.trimmed.lowercased()
            }
            return match.map { .existing($0.id) } ?? .create(c)
        }

        var accepted: [Contact] = []
        var skipped = 0
        var planned: [PlannedContact] = []
        for b in data.contacts {
            let probe = Contact(fullName: b.fullName, company: b.company, phone: b.phone, phoneAlt: b.phoneAlt, email: b.email, emailAlt: b.emailAlt)
            if DuplicateDetector.isDuplicate(probe, existingContacts + accepted) {
                skipped += 1
                continue
            }
            accepted.append(probe)
            var seen = Set<String>()
            let names = b.tagIds.compactMap { tagNames[$0] }.filter { seen.insert($0.lowercased()).inserted }
            planned.append(PlannedContact(contact: b, category: target(b.categoryId), tagNames: names, followUps: followUpsByContact[b.id] ?? []))
        }
        return MergePlan(contacts: planned, skippedDuplicates: skipped)
    }
}
