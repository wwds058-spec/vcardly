import XCTest
@testable import VCardly

/// Backup format, encryption and restore. The fixtures in testdata/backup are shared with the Android tests, so passing here
/// proves that iPhone restores what Android writes; CI then checks the files written by `testWritesBackupsForCrossCheck`
/// with tools/backup_reference.py.
@MainActor
final class BackupTests: XCTestCase {
    private static let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    private static let fixtures = repo.appendingPathComponent("testdata/backup")
    private let password = "correct horse"

    private func makeEnv() -> AppEnvironment {
        AppEnvironment(inMemory: true, defaults: UserDefaults(suiteName: "backup-tests-\(UUID().uuidString)")!)
    }

    private func tempDir() throws -> URL {
        let d = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: d) }
        return d
    }

    private func readArchive(_ url: URL, password: String?) throws -> BackupArchive.Contents {
        let work = try tempDir()
        let stage = work.appendingPathComponent("stage")
        try FileManager.default.createDirectory(at: stage, withIntermediateDirectories: true)
        return try BackupArchive.read(url, password: password, workDir: work, stage: stage)
    }

    private func expectError(_ expected: BackupError, _ body: () throws -> Void, file: StaticString = #filePath, line: UInt = #line) {
        do {
            try body()
            XCTFail("expected \(expected)", file: file, line: line)
        } catch let e as BackupError {
            XCTAssertEqual(e, expected, file: file, line: line)
        } catch {
            XCTFail("unexpected \(error)", file: file, line: line)
        }
    }

    // MARK: encryption

    func testCryptoRoundTripsAcrossChunkBoundaries() throws {
        for size in [0, 1, BackupCrypto.chunk - 1, BackupCrypto.chunk, BackupCrypto.chunk + 1, 3 * BackupCrypto.chunk] {
            let plain = Data((0..<size).map { UInt8(truncatingIfNeeded: $0 &* 31) })
            let blob = try BackupCrypto.encrypt(plain, password: "pass-1234", iterations: 10_000)
            XCTAssertTrue(BackupCrypto.isEncrypted(blob))
            XCTAssertEqual(try BackupCrypto.decrypt(blob, password: "pass-1234"), plain, "size \(size)")
        }
    }

    func testCryptoDetectsWrongPasswordTamperingAndTruncation() throws {
        let plain = Data(repeating: 7, count: BackupCrypto.chunk + 500)
        let blob = try BackupCrypto.encrypt(plain, password: "pass-1234", iterations: 10_000)
        XCTAssertThrowsError(try BackupCrypto.decrypt(blob, password: "pass-1235"))
        var tampered = blob
        tampered[BackupCrypto.headerLength + 10] ^= 0x01
        XCTAssertThrowsError(try BackupCrypto.decrypt(tampered, password: "pass-1234"))
        // Dropping the final chunk must fail too (the first chunk is not marked "last").
        XCTAssertThrowsError(try BackupCrypto.decrypt(blob.prefix(BackupCrypto.headerLength + BackupCrypto.chunk + 16), password: "pass-1234"))
    }

    // MARK: zip

    func testZipRoundTripStoredAndDeflated() throws {
        let url = try tempDir().appendingPathComponent("t.zip")
        let text = Data(String(repeating: "hello vcardly ", count: 500).utf8)
        let noise = Data((0..<5000).map { UInt8(truncatingIfNeeded: $0 &* 2_654_435_761 >> 7) })
        let w = try ZipArchive.Writer(url: url)
        try w.add("a.txt", text, compress: true)
        try w.add("images/b.jpg", noise, compress: false)
        try w.add("empty", Data(), compress: true)
        try w.finish()
        let zip = try Data(contentsOf: url)
        let entries = try ZipArchive.entries(zip)
        XCTAssertEqual(entries.map(\.name), ["a.txt", "images/b.jpg", "empty"])
        XCTAssertEqual(entries[0].method, 8)
        XCTAssertEqual(try ZipArchive.read(entries[0], from: zip, maxSize: 1 << 20), text)
        XCTAssertEqual(try ZipArchive.read(entries[1], from: zip, maxSize: 1 << 20), noise)
        XCTAssertEqual(try ZipArchive.read(entries[2], from: zip, maxSize: 1 << 20), Data())
        XCTAssertThrowsError(try ZipArchive.read(entries[0], from: zip, maxSize: 10)) // size cap applies before inflating
        XCTAssertEqual(CRC32.checksum(Data("123456789".utf8)), 0xCBF4_3926)
    }

    // MARK: shared fixtures (written like Android's java.util.zip output)

    func testReadsAndroidStyleFixtures() throws {
        let plain = try readArchive(Self.fixtures.appendingPathComponent("sample-plain.vcbackup"), password: nil)
        XCTAssertEqual(plain.data.contacts.count, 2)
        XCTAssertEqual(plain.data.contacts.first { $0.id == 11 }?.notes, "హరి — met at the expo\nsecond line")
        XCTAssertEqual(plain.data.followUps.first { $0.id == 21 }?.status, "RESCHEDULED")
        XCTAssertEqual(plain.images, ["front-11.jpg"])
        XCTAssertFalse(plain.manifest.encrypted)

        let enc = try readArchive(Self.fixtures.appendingPathComponent("sample-encrypted.vcbackup"), password: password)
        XCTAssertEqual(enc.data, plain.data)
        let multi = try readArchive(Self.fixtures.appendingPathComponent("sample-multichunk.vcbackup"), password: password)
        XCTAssertEqual(multi.images, ["front-11.jpg", "back-12.jpg"])

        let locked = Self.fixtures.appendingPathComponent("sample-encrypted.vcbackup")
        expectError(.needsPassword) { _ = try readArchive(locked, password: nil) }
        expectError(.wrongPasswordOrCorrupt) { _ = try readArchive(locked, password: "wrong password") }
    }

    func testRejectsBadFiles() throws {
        let dir = try tempDir()
        let notZip = dir.appendingPathComponent("x.vcbackup")
        try Data("hello world".utf8).write(to: notZip)
        expectError(.notABackup) { _ = try readArchive(notZip, password: nil) }

        func zip(_ name: String, _ entries: [(String, Data)]) throws -> URL {
            let url = dir.appendingPathComponent(name)
            let w = try ZipArchive.Writer(url: url)
            for (n, d) in entries { try w.add(n, d, compress: true) }
            try w.finish()
            return url
        }
        func manifest(version: Int = 1, files: [String: String]) -> Data {
            try! JSONEncoder().encode(BackupManifest(format: BackupFormat.id, formatVersion: version, createdAt: 1, appVersion: "t",
                                                     encrypted: false, counts: BackupCounts(), files: files))
        }
        let data = Data(#"{"contacts":[{"id":1,"fullName":"A"}]}"#.utf8)
        let good = BackupArchive.sha256(data)

        let newer = try zip("newer.zip", [("manifest.json", manifest(version: 2, files: ["data.json": good])), ("data.json", data)])
        expectError(.newerVersion) { _ = try readArchive(newer, password: nil) }

        let tampered = try zip("tampered.zip", [("manifest.json", manifest(files: ["data.json": String(repeating: "0", count: 64)])), ("data.json", data)])
        expectError(.corrupt) { _ = try readArchive(tampered, password: nil) }

        let manifestLast = try zip("order.zip", [("data.json", data), ("manifest.json", manifest(files: ["data.json": good]))])
        expectError(.notABackup) { _ = try readArchive(manifestLast, password: nil) }

        // Path traversal names are never extracted; a missing promised image is caught by the checksum list.
        let slip = try zip("slip.zip", [("manifest.json", manifest(files: ["data.json": good, "images/a.jpg": good])), ("data.json", data),
                                        ("images/../../evil.jpg", data)])
        expectError(.corrupt) { _ = try readArchive(slip, password: nil) }

        let ok = try readArchive(try zip("ok.zip", [("manifest.json", manifest(files: ["data.json": good])), ("data.json", data),
                                                    ("images/../x.jpg", data), ("notes.txt", data)]), password: nil)
        XCTAssertEqual(ok.data.contacts.map(\.fullName), ["A"])
        XCTAssertTrue(ok.images.isEmpty)
    }

    // MARK: restore

    func testReplaceRestoresAndroidFixture() async throws {
        let env = makeEnv()
        try env.contacts.save(Contact(fullName: "Old Contact"), tagIds: [])
        let prepared = try await env.backup.prepare(Self.fixtures.appendingPathComponent("sample-encrypted.vcbackup"), password: password)
        let summary = try env.backup.restore(prepared, mode: .replace)
        XCTAssertEqual(summary.contactsAdded, 2)
        XCTAssertEqual(summary.followUps, 2)
        XCTAssertEqual(summary.images, 1)
        XCTAssertFalse(FileManager.default.fileExists(atPath: prepared.workDir.path), "staging is cleaned up")

        let all = env.contacts.contacts()
        XCTAssertEqual(all.map(\.contact.fullName).sorted(), ["Asha Rao", "Ben Ito"])
        let asha = try XCTUnwrap(all.first { $0.contact.fullName == "Asha Rao" })
        XCTAssertEqual(asha.category?.system, .customer)
        XCTAssertEqual(asha.tags.map(\.name), ["expo", "VIP"])
        XCTAssertTrue(asha.contact.isFavorite)
        XCTAssertEqual(asha.contact.source, .scan)
        XCTAssertNotNil(env.images.load(asha.contact.frontImagePath), "the restored photo decodes")
        let ben = try XCTUnwrap(all.first { $0.contact.fullName == "Ben Ito" })
        XCTAssertEqual(ben.category?.displayName, "Investors")
        XCTAssertNil(ben.category?.system)
        // Every system category exists again, plus the custom one.
        XCTAssertEqual(env.contacts.categories().count, SystemCategory.allCases.count + 1)

        let ups = env.followUps.all()
        XCTAssertEqual(ups.first { $0.followUp.title == "Send revised quotation" }?.followUp.status, .rescheduled)
        XCTAssertEqual(ups.first { $0.followUp.title == "Send revised quotation" }?.followUp.type, .quotation)
        XCTAssertNotNil(ups.first { $0.followUp.title == "Call about delivery" }?.followUp.completedAt)
        XCTAssertEqual(env.prefs.myCard.fullName, "Sam Lee")
    }

    func testMergeSkipsDuplicatesAndKeepsExistingData() async throws {
        let env = makeEnv()
        try env.contacts.save(Contact(fullName: "Someone Else", email: "asha.rao@acme.example"), tagIds: [])
        env.prefs.setMyCard(MyCard(fullName: "Me Already"))
        let prepared = try await env.backup.prepare(Self.fixtures.appendingPathComponent("sample-plain.vcbackup"), password: nil)
        let summary = try env.backup.restore(prepared, mode: .merge)
        XCTAssertEqual(summary.contactsAdded, 1)
        XCTAssertEqual(summary.duplicatesSkipped, 1)
        XCTAssertEqual(summary.followUps, 1)
        XCTAssertEqual(env.contacts.contacts().map(\.contact.fullName).sorted(), ["Ben Ito", "Someone Else"])
        XCTAssertEqual(env.prefs.myCard.fullName, "Me Already", "merge never overwrites your card")

        // Restoring the same file again adds nothing.
        let again = try await env.backup.prepare(Self.fixtures.appendingPathComponent("sample-plain.vcbackup"), password: nil)
        XCTAssertEqual(try env.backup.restore(again, mode: .merge).contactsAdded, 0)
        XCTAssertEqual(env.contacts.categories().filter { $0.system == nil }.count, 1, "the custom category is created once")
    }

    func testIPhoneBackupRoundTrip() async throws {
        let source = makeEnv()
        let photo = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 24)).image { ctx in
            UIColor.systemBlue.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 40, height: 24))
        }
        let path = try XCTUnwrap(source.images.save(photo))
        let tag = try XCTUnwrap(source.contacts.findOrCreateTag("VIP"))
        let cat = try XCTUnwrap(source.contacts.categories().first { $0.system == .supplier })
        let id = try source.contacts.save(Contact(fullName: "Asha Rao", company: "Acme", phone: "+91 98765 43210", notes: "హరి",
                                                  categoryId: cat.id, isFavorite: true, frontImagePath: path), tagIds: [tag.id])
        try source.followUps.save(FollowUp(contactId: id, type: .payment, title: "Invoice", dueAt: Date().addingTimeInterval(86_400)))
        source.prefs.setMyCard(MyCard(fullName: "Sam Lee"))

        let created = try await source.backup.create(password: "pass-1234")
        defer { BackupService.cleanUp(created.workDir) }
        XCTAssertEqual(created.counts, BackupCounts(contacts: 1, categories: SystemCategory.allCases.count, tags: 1, followUps: 1, images: 1))
        XCTAssertEqual(created.file.pathExtension, "vcbackup")
        XCTAssertNotNil(source.prefs.lastBackupAt)

        let target = makeEnv()
        let prepared = try await target.backup.prepare(created.file, password: "pass-1234")
        XCTAssertTrue(prepared.manifest.encrypted)
        _ = try target.backup.restore(prepared, mode: .replace)
        let restored = try XCTUnwrap(target.contacts.contacts().first)
        XCTAssertEqual(restored.contact.fullName, "Asha Rao")
        XCTAssertEqual(restored.contact.notes, "హరి")
        XCTAssertEqual(restored.category?.system, .supplier)
        XCTAssertEqual(restored.tags.map(\.name), ["VIP"])
        XCTAssertNotNil(target.images.load(restored.contact.frontImagePath))
        XCTAssertEqual(target.followUps.all().first?.followUp.type, .payment)
        XCTAssertEqual(target.prefs.myCard.fullName, "Sam Lee")
    }

    /// Writes iPhone-made backups to ios/build/backups; CI verifies them with the Python reference reader.
    func testWritesBackupsForCrossCheck() async throws {
        let env = makeEnv()
        let prepared = try await env.backup.prepare(Self.fixtures.appendingPathComponent("sample-multichunk.vcbackup"), password: password)
        _ = try env.backup.restore(prepared, mode: .replace)
        let out = Self.repo.appendingPathComponent("ios/build/backups", isDirectory: true)
        try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        for (name, pw) in [("ios-plain.vcbackup", nil), ("ios-encrypted.vcbackup", password)] as [(String, String?)] {
            let created = try await env.backup.create(password: pw)
            let target = out.appendingPathComponent(name)
            try? FileManager.default.removeItem(at: target)
            try FileManager.default.copyItem(at: created.file, to: target)
            BackupService.cleanUp(created.workDir)
        }
    }
}
