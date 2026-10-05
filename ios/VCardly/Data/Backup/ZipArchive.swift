import Compression
import Foundation

/// The small part of ZIP that backups need. Foundation has no ZIP API, so this is written here and kept strict.
/// Writing: entries are stored or DEFLATE-compressed with sizes and CRC in the local header (no data descriptors, no ZIP64),
/// which `java.util.zip.ZipInputStream` on Android reads. Reading: driven by the central directory, so it also reads Android's
/// output (DEFLATE with data descriptors). Sizes are checked before anything is allocated.
enum ZipArchive {
    struct Entry {
        let name: String
        let method: UInt16
        let crc: UInt32
        let compressedSize: Int
        let size: Int
        let localHeaderOffset: Int
    }

    enum ZipError: Error { case malformed, unsupported, tooLarge, crcMismatch }

    // MARK: writing

    final class Writer {
        private let handle: FileHandle
        private var offset = 0
        private var central = Data()
        private var count = 0

        init(url: URL) throws {
            FileManager.default.createFile(atPath: url.path, contents: nil, attributes: [.protectionKey: FileProtectionType.complete])
            handle = try FileHandle(forWritingTo: url)
        }

        /// Adds one entry. `compress` tries DEFLATE and keeps it only when it is smaller (JPEGs are stored as they are).
        func add(_ name: String, _ data: Data, compress: Bool) throws {
            guard data.count < Int(UInt32.max), count < 0xFFFF else { throw ZipError.tooLarge }
            let crc = CRC32.checksum(data)
            var method: UInt16 = 0
            var payload = data
            if compress, let deflated = Deflate.compress(data), deflated.count < data.count {
                method = 8
                payload = deflated
            }
            let nameBytes = Data(name.utf8)
            var local = Data()
            local.le32(0x0403_4B50); local.le16(20); local.le16(0x0800); local.le16(method); local.le16(0); local.le16(0x21)
            local.le32(crc); local.le32(UInt32(payload.count)); local.le32(UInt32(data.count)); local.le16(UInt16(nameBytes.count)); local.le16(0)
            local.append(nameBytes)

            var c = Data()
            c.le32(0x0201_4B50); c.le16(20); c.le16(20); c.le16(0x0800); c.le16(method); c.le16(0); c.le16(0x21)
            c.le32(crc); c.le32(UInt32(payload.count)); c.le32(UInt32(data.count)); c.le16(UInt16(nameBytes.count))
            c.le16(0); c.le16(0); c.le16(0); c.le16(0); c.le32(0); c.le32(UInt32(offset))
            c.append(nameBytes)
            central.append(c)

            try handle.write(contentsOf: local)
            try handle.write(contentsOf: payload)
            offset += local.count + payload.count
            guard offset < Int(UInt32.max) else { throw ZipError.tooLarge }
            count += 1
        }

        func finish() throws {
            var end = Data()
            end.le32(0x0605_4B50); end.le16(0); end.le16(0); end.le16(UInt16(count)); end.le16(UInt16(count))
            end.le32(UInt32(central.count)); end.le32(UInt32(offset)); end.le16(0)
            try handle.write(contentsOf: central)
            try handle.write(contentsOf: end)
            try handle.close()
        }
    }

    // MARK: reading

    /// Lists entries in file order (by local header offset), from the central directory.
    static func entries(_ zip: Data) throws -> [Entry] {
        guard zip.count >= 22 else { throw ZipError.malformed }
        // End of central directory: last 22 bytes plus up to a 64 KiB comment.
        var eocd = -1
        var i = zip.count - 22
        let floor = max(0, zip.count - 22 - 0xFFFF)
        while i >= floor {
            if zip.u32(i) == 0x0605_4B50 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { throw ZipError.malformed }
        let total = Int(zip.u16(eocd + 10))
        let cdSize = Int(zip.u32(eocd + 12))
        let cdOffset = Int(zip.u32(eocd + 16))
        guard total != 0xFFFF, cdOffset != 0xFFFF_FFFF else { throw ZipError.unsupported } // ZIP64
        guard cdOffset + cdSize <= eocd else { throw ZipError.malformed }

        var result: [Entry] = []
        var p = cdOffset
        for _ in 0..<total {
            guard p + 46 <= zip.count, zip.u32(p) == 0x0201_4B50 else { throw ZipError.malformed }
            let flags = zip.u16(p + 8)
            let method = zip.u16(p + 10)
            let crc = zip.u32(p + 16)
            let csize = zip.u32(p + 20), size = zip.u32(p + 24)
            let nameLen = Int(zip.u16(p + 28)), extraLen = Int(zip.u16(p + 30)), commentLen = Int(zip.u16(p + 32))
            let local = zip.u32(p + 42)
            guard csize != 0xFFFF_FFFF, size != 0xFFFF_FFFF, local != 0xFFFF_FFFF else { throw ZipError.unsupported }
            guard flags & 0x1 == 0 else { throw ZipError.unsupported } // ZIP's own (weak) encryption is never used by VCardly
            guard p + 46 + nameLen <= zip.count else { throw ZipError.malformed }
            let name = String(decoding: zip.subdata(in: (p + 46)..<(p + 46 + nameLen)), as: UTF8.self)
            result.append(Entry(name: name, method: method, crc: crc, compressedSize: Int(csize), size: Int(size), localHeaderOffset: Int(local)))
            p += 46 + nameLen + extraLen + commentLen
        }
        return result.sorted { $0.localHeaderOffset < $1.localHeaderOffset }
    }

    /// The bytes of one entry, refusing anything larger than `maxSize` before inflating it, and checking the CRC.
    static func read(_ e: Entry, from zip: Data, maxSize: Int) throws -> Data {
        guard e.size <= maxSize else { throw ZipError.tooLarge }
        let h = e.localHeaderOffset
        guard h + 30 <= zip.count, zip.u32(h) == 0x0403_4B50 else { throw ZipError.malformed }
        let start = h + 30 + Int(zip.u16(h + 26)) + Int(zip.u16(h + 28))
        guard start + e.compressedSize <= zip.count else { throw ZipError.malformed }
        let raw = zip.subdata(in: start..<(start + e.compressedSize))
        let out: Data
        switch e.method {
        case 0:
            guard raw.count == e.size else { throw ZipError.malformed }
            out = raw
        case 8:
            guard let inflated = Deflate.decompress(raw, expectedSize: e.size) else { throw ZipError.malformed }
            out = inflated
        default:
            throw ZipError.unsupported
        }
        guard CRC32.checksum(out) == e.crc else { throw ZipError.crcMismatch }
        return out
    }
}

/// Raw DEFLATE (RFC 1951), which is what ZIP uses; Apple's COMPRESSION_ZLIB is raw DEFLATE.
enum Deflate {
    static func compress(_ data: Data) -> Data? {
        guard !data.isEmpty else { return nil }
        let capacity = data.count + data.count / 10 + 1024
        var out = Data(count: capacity)
        let n = out.withUnsafeMutableBytes { dst in
            data.withUnsafeBytes { src in
                compression_encode_buffer(dst.bindMemory(to: UInt8.self).baseAddress!, capacity,
                                          src.bindMemory(to: UInt8.self).baseAddress!, data.count, nil, COMPRESSION_ZLIB)
            }
        }
        guard n > 0 else { return nil }
        return out.prefix(n)
    }

    /// Returns nil unless the stream inflates to exactly `expectedSize` bytes (a larger stream is a zip bomb or damage).
    static func decompress(_ data: Data, expectedSize: Int) -> Data? {
        if expectedSize == 0 { return Data() }
        guard !data.isEmpty else { return nil }
        let capacity = expectedSize + 1
        var out = Data(count: capacity)
        let n = out.withUnsafeMutableBytes { dst in
            data.withUnsafeBytes { src in
                compression_decode_buffer(dst.bindMemory(to: UInt8.self).baseAddress!, capacity,
                                          src.bindMemory(to: UInt8.self).baseAddress!, data.count, nil, COMPRESSION_ZLIB)
            }
        }
        guard n == expectedSize else { return nil }
        return out.prefix(n)
    }
}

enum CRC32 {
    private static let table: [UInt32] = (0..<256).map { i -> UInt32 in
        var c = UInt32(i)
        for _ in 0..<8 { c = (c & 1) != 0 ? 0xEDB8_8320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        data.withUnsafeBytes { buf in
            for b in buf { crc = table[Int((crc ^ UInt32(b)) & 0xFF)] ^ (crc >> 8) }
        }
        return crc ^ 0xFFFF_FFFF
    }
}

extension Data {
    mutating func le16(_ v: UInt16) { Swift.withUnsafeBytes(of: v.littleEndian) { append(contentsOf: $0) } }
    mutating func le32(_ v: UInt32) { Swift.withUnsafeBytes(of: v.littleEndian) { append(contentsOf: $0) } }
    mutating func be32(_ v: UInt32) { Swift.withUnsafeBytes(of: v.bigEndian) { append(contentsOf: $0) } }

    func u16(_ at: Int) -> UInt16 {
        let i = startIndex + at
        return UInt16(self[i]) | UInt16(self[i + 1]) << 8
    }

    func u32(_ at: Int) -> UInt32 {
        let i = startIndex + at
        return UInt32(self[i]) | UInt32(self[i + 1]) << 8 | UInt32(self[i + 2]) << 16 | UInt32(self[i + 3]) << 24
    }
}
