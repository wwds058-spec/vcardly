import CommonCrypto
import CryptoKit
import Foundation

/// Password encryption for backups, byte-compatible with Android's `ChunkedAesGcm`:
/// PBKDF2-HMAC-SHA256 key derivation, then AES-256-GCM over 64 KiB chunks. Each chunk's nonce is a random 8-byte prefix plus
/// the chunk counter, and each chunk authenticates the header plus a "last chunk" flag, so tampering, reordering and
/// truncation are all detected. Works file to file, so memory stays flat however large the backup is.
///
/// Layout: "VCBK" | version(1) | kdf(1) | iterations(u32 BE) | salt(16) | noncePrefix(8) | chunks (ciphertext + 16-byte tag)
enum BackupCrypto {
    static let magic = Data("VCBK".utf8)
    static let chunk = 64 * 1024
    static let defaultIterations: UInt32 = 310_000
    static let headerLength = 4 + 1 + 1 + 4 + 16 + 8
    private static let tagLength = 16

    enum CryptoError: Error { case badHeader, wrongPasswordOrCorrupt, io }

    static func isEncrypted(_ firstBytes: Data) -> Bool { firstBytes.prefix(4) == magic }

    static func encrypt(file input: URL, to output: URL, password: String, iterations: UInt32 = defaultIterations) throws {
        var salt = Data(count: 16), prefix = Data(count: 8)
        guard randomFill(&salt), randomFill(&prefix) else { throw CryptoError.io }
        var header = magic
        header.append(1); header.append(1); header.be32(iterations); header.append(salt); header.append(prefix)
        let key = try derive(password, salt: salt, iterations: iterations)

        let size = (try FileManager.default.attributesOfItem(atPath: input.path)[.size] as? NSNumber)?.intValue ?? 0
        let chunks = max(1, (size + chunk - 1) / chunk)
        let reader = try FileHandle(forReadingFrom: input)
        defer { try? reader.close() }
        FileManager.default.createFile(atPath: output.path, contents: nil, attributes: [.protectionKey: FileProtectionType.complete])
        let writer = try FileHandle(forWritingTo: output)
        defer { try? writer.close() }
        try writer.write(contentsOf: header)
        for n in 0..<chunks {
            let plain = try reader.read(upToCount: chunk) ?? Data()
            try writer.write(contentsOf: try seal(plain, key: key, prefix: prefix, counter: UInt32(n), header: header, last: n == chunks - 1))
        }
    }

    /// Reads the header and decrypts into `output`. The password is checked by the first chunk; any failure means
    /// "wrong password or damaged" (AES-GCM cannot tell them apart).
    static func decrypt(file input: URL, to output: URL, password: String) throws {
        let reader = try FileHandle(forReadingFrom: input)
        defer { try? reader.close() }
        guard let header = try reader.read(upToCount: headerLength), header.count == headerLength,
              header.prefix(4) == magic, header[header.startIndex + 4] == 1, header[header.startIndex + 5] == 1 else { throw CryptoError.badHeader }
        let h = Data(header)
        let iterations = UInt32(h[6]) << 24 | UInt32(h[7]) << 16 | UInt32(h[8]) << 8 | UInt32(h[9])
        guard (10_000...5_000_000).contains(iterations) else { throw CryptoError.badHeader }
        let salt = h.subdata(in: 10..<26), prefix = h.subdata(in: 26..<34)
        let key = try derive(password, salt: salt, iterations: iterations)

        let size = (try FileManager.default.attributesOfItem(atPath: input.path)[.size] as? NSNumber)?.intValue ?? 0
        let body = size - headerLength
        let piece = chunk + tagLength
        guard body >= tagLength else { throw CryptoError.wrongPasswordOrCorrupt }
        let chunks = (body + piece - 1) / piece
        FileManager.default.createFile(atPath: output.path, contents: nil, attributes: [.protectionKey: FileProtectionType.complete])
        let writer = try FileHandle(forWritingTo: output)
        defer { try? writer.close() }
        for n in 0..<chunks {
            guard let enc = try reader.read(upToCount: piece), enc.count >= tagLength else { throw CryptoError.wrongPasswordOrCorrupt }
            try writer.write(contentsOf: try open(Data(enc), key: key, prefix: prefix, counter: UInt32(n), header: h, last: n == chunks - 1))
        }
    }

    // MARK: in-memory helpers (tests and small payloads)

    static func encrypt(_ plain: Data, password: String, iterations: UInt32 = defaultIterations) throws -> Data {
        try roundTripThroughFiles(plain) { try encrypt(file: $0, to: $1, password: password, iterations: iterations) }
    }

    static func decrypt(_ blob: Data, password: String) throws -> Data {
        try roundTripThroughFiles(blob) { try decrypt(file: $0, to: $1, password: password) }
    }

    private static func roundTripThroughFiles(_ data: Data, _ body: (URL, URL) throws -> Void) throws -> Data {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let a = dir.appendingPathComponent("in"), b = dir.appendingPathComponent("out")
        try data.write(to: a)
        try body(a, b)
        return try Data(contentsOf: b)
    }

    // MARK: primitives

    private static func derive(_ password: String, salt: Data, iterations: UInt32) throws -> SymmetricKey {
        let pw = Array(password.utf8)
        guard !pw.isEmpty else { throw CryptoError.wrongPasswordOrCorrupt }
        var key = [UInt8](repeating: 0, count: 32)
        let status = salt.withUnsafeBytes { s in
            pw.withUnsafeBufferPointer { p in
                p.baseAddress!.withMemoryRebound(to: Int8.self, capacity: pw.count) { pp in
                    CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pp, pw.count, s.bindMemory(to: UInt8.self).baseAddress!, salt.count,
                                         CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), iterations, &key, key.count)
                }
            }
        }
        guard status == Int32(kCCSuccess) else { throw CryptoError.io }
        defer { key.withUnsafeMutableBufferPointer { _ = memset_s($0.baseAddress, $0.count, 0, $0.count) } }
        return SymmetricKey(data: key)
    }

    private static func nonce(_ prefix: Data, _ counter: UInt32) throws -> AES.GCM.Nonce {
        var n = prefix
        n.be32(counter)
        return try AES.GCM.Nonce(data: n)
    }

    private static func aad(_ header: Data, _ last: Bool) -> Data {
        var a = header
        a.append(last ? 1 : 0)
        return a
    }

    private static func seal(_ plain: Data, key: SymmetricKey, prefix: Data, counter: UInt32, header: Data, last: Bool) throws -> Data {
        let box = try AES.GCM.seal(plain, using: key, nonce: try nonce(prefix, counter), authenticating: aad(header, last))
        return box.ciphertext + box.tag
    }

    private static func open(_ enc: Data, key: SymmetricKey, prefix: Data, counter: UInt32, header: Data, last: Bool) throws -> Data {
        do {
            let box = try AES.GCM.SealedBox(nonce: try nonce(prefix, counter), ciphertext: enc.dropLast(tagLength), tag: enc.suffix(tagLength))
            return try AES.GCM.open(box, using: key, authenticating: aad(header, last))
        } catch {
            throw CryptoError.wrongPasswordOrCorrupt
        }
    }

    private static func randomFill(_ d: inout Data) -> Bool {
        let count = d.count
        return d.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) } == errSecSuccess
    }
}
