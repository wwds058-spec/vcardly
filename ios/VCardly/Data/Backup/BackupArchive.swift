import CryptoKit
import Foundation

/// Reads and writes the backup container, following the same rules as Android's `BackupArchive`: `manifest.json` first,
/// `data.json`, then `images/<name>`; the manifest lists a SHA-256 for every other entry, and every one is checked on restore.
/// Reading is defensive: entry names are whitelisted (no path traversal), sizes are capped before anything is inflated
/// (no zip bombs), and nothing is trusted before it is verified.
enum BackupArchive {
    /// A card photo to include: the bare file name inside `images/` and where to read it from.
    struct ImageSource {
        let name: String
        let url: URL
    }

    struct Contents {
        let manifest: BackupManifest
        let data: BackupData
        /// Verified image files, written into the staging folder under their bare names.
        let images: Set<String>
    }

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.withoutEscapingSlashes]
        return e
    }()

    // MARK: write

    static func write(_ data: BackupData, images: [ImageSource], appVersion: String, createdAt: Date, to output: URL,
                      password: String?, workDir: URL) throws {
        let dataBytes = try encoder.encode(data)
        var files = [BackupFormat.data: sha256(dataBytes)]
        for img in images {
            // Read once to hash; read again below while writing, so only one photo is in memory at a time.
            files[BackupFormat.imagesDir + img.name] = sha256(try Data(contentsOf: img.url))
        }
        let manifest = BackupManifest(
            format: BackupFormat.id, formatVersion: BackupFormat.formatVersion, createdAt: createdAt.millis, appVersion: appVersion,
            encrypted: password != nil,
            counts: BackupCounts(contacts: data.contacts.count, categories: data.categories.count, tags: data.tags.count,
                                 followUps: data.followUps.count, images: images.count),
            files: files)

        let zipURL = password == nil ? output : workDir.appendingPathComponent("plain.zip")
        let zip = try ZipArchive.Writer(url: zipURL)
        try zip.add(BackupFormat.manifest, try encoder.encode(manifest), compress: true)
        try zip.add(BackupFormat.data, dataBytes, compress: true)
        for img in images {
            try zip.add(BackupFormat.imagesDir + img.name, try Data(contentsOf: img.url), compress: false)
        }
        try zip.finish()
        if let password {
            defer { try? FileManager.default.removeItem(at: zipURL) }
            try BackupCrypto.encrypt(file: zipURL, to: output, password: password)
        }
    }

    // MARK: read

    /// Decrypts if needed, then reads and verifies the whole backup, writing image files into `stage`.
    /// Nothing outside `workDir` and `stage` is touched.
    static func read(_ input: URL, password: String?, workDir: URL, stage: URL) throws -> Contents {
        let zipURL = try plainZip(input, password: password, workDir: workDir)
        let zip: Data
        do { zip = try Data(contentsOf: zipURL, options: .alwaysMapped) } catch { throw BackupError.io }

        let entries: [ZipArchive.Entry]
        do { entries = try ZipArchive.entries(zip) } catch { throw BackupError.notABackup }
        guard entries.count <= BackupFormat.maxEntries else { throw BackupError.tooLarge }

        var manifest: BackupManifest?
        var dataBytes: Data?
        var seen: [String: String] = [:]
        var images = Set<String>()
        var total: Int64 = 0
        do {
            for e in entries where !e.name.hasSuffix("/") {
                if e.name == BackupFormat.manifest {
                    manifest = try parseManifest(try ZipArchive.read(e, from: zip, maxSize: BackupFormat.maxManifestBytes))
                } else if manifest == nil {
                    throw BackupError.notABackup // the manifest must come first
                } else if e.name == BackupFormat.data {
                    let bytes = try ZipArchive.read(e, from: zip, maxSize: BackupFormat.maxDataBytes)
                    total += Int64(bytes.count)
                    dataBytes = bytes
                    seen[e.name] = sha256(bytes)
                } else if BackupFormat.isSafeImageEntry(e.name) {
                    let bytes = try ZipArchive.read(e, from: zip, maxSize: BackupFormat.maxImageBytes)
                    total += Int64(bytes.count)
                    let name = String(e.name.dropFirst(BackupFormat.imagesDir.count))
                    do { try bytes.write(to: stage.appendingPathComponent(name), options: [.atomic, .completeFileProtection]) } catch { throw BackupError.io }
                    seen[e.name] = sha256(bytes)
                    images.insert(name)
                }
                // Unknown entries are ignored, never extracted.
                if total > BackupFormat.maxTotalBytes { throw BackupError.tooLarge }
            }
        } catch let error as BackupError {
            throw error
        } catch ZipArchive.ZipError.tooLarge {
            throw BackupError.tooLarge
        } catch {
            throw BackupError.corrupt
        }

        guard let m = manifest else { throw BackupError.notABackup }
        guard let bytes = dataBytes else { throw BackupError.corrupt }
        // Integrity: everything the manifest promises must be present and unchanged.
        for (name, expected) in m.files where seen[name] != expected { throw BackupError.corrupt }
        guard m.files[BackupFormat.data] != nil else { throw BackupError.corrupt }
        let data: BackupData
        do { data = try JSONDecoder().decode(BackupData.self, from: bytes) } catch { throw BackupError.corrupt }
        return Contents(manifest: m, data: data, images: images)
    }

    private static func plainZip(_ input: URL, password: String?, workDir: URL) throws -> URL {
        let head: Data
        do {
            let h = try FileHandle(forReadingFrom: input)
            defer { try? h.close() }
            head = try h.read(upToCount: 4) ?? Data()
        } catch {
            throw BackupError.io
        }
        guard head.count == 4 else { throw BackupError.notABackup }
        if BackupCrypto.isEncrypted(head) {
            guard let password, !password.isEmpty else { throw BackupError.needsPassword }
            let out = workDir.appendingPathComponent("decrypted.zip")
            do { try BackupCrypto.decrypt(file: input, to: out, password: password) } catch { throw BackupError.wrongPasswordOrCorrupt }
            return out
        }
        guard head.prefix(2) == Data("PK".utf8) else { throw BackupError.notABackup }
        return input
    }

    private static func parseManifest(_ bytes: Data) throws -> BackupManifest {
        guard let m = try? JSONDecoder().decode(BackupManifest.self, from: bytes), m.format == BackupFormat.id else { throw BackupError.notABackup }
        guard m.formatVersion <= BackupFormat.formatVersion else { throw BackupError.newerVersion }
        return m
    }

    static func sha256(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }
}
