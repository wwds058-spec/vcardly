#!/usr/bin/env python3
"""Reference implementation of the `vcardly-backup-v1` file format, used only by tests.

It lets the Android (Kotlin) and iPhone (Swift) implementations be checked against one shared definition:

  * `fixtures <dir>`  writes sample backups (plain and password-protected) shaped exactly like Android's writer
                      (java.util.zip: DEFLATE entries with data descriptors), with fictional data;
  * `verify <file> [password]` strictly reads a backup made by either app and checks every rule the apps enforce.

Format (see docs/ARCHITECTURE.md, "Backup and restore"):
  ZIP: manifest.json first, data.json, images/<name>. The manifest lists a SHA-256 for every other entry.
  Optional encryption: "VCBK" | version 1 | kdf 1 | iterations (u32 BE) | salt 16 | nonce prefix 8 | chunks.
  Each chunk is AES-256-GCM over up to 64 KiB of the ZIP, key = PBKDF2-HMAC-SHA256(password, salt, iterations),
  nonce = prefix + chunk counter (u32 BE), associated data = header + (1 if last chunk else 0).

Needs: pip install cryptography
"""
import hashlib
import io
import json
import os
import re
import struct
import sys
import zipfile
import zlib

FORMAT_ID = "vcardly-backup-v1"
MAGIC = b"VCBK"
CHUNK = 64 * 1024
HEADER_LEN = 4 + 1 + 1 + 4 + 16 + 8
SAFE_IMAGE = re.compile(r"^images/[A-Za-z0-9._-]{1,100}$")


def sha256(b):
    return hashlib.sha256(b).hexdigest()


# ------------------------------------------------------------------ encryption

def _key(password, salt, iterations):
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations, 32)


def encrypt(plain, password, iterations=310_000, salt=None, prefix=None):
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    salt = salt or os.urandom(16)
    prefix = prefix or os.urandom(8)
    header = MAGIC + bytes([1, 1]) + struct.pack(">I", iterations) + salt + prefix
    aes = AESGCM(_key(password, salt, iterations))
    chunks = [plain[i:i + CHUNK] for i in range(0, len(plain), CHUNK)] or [b""]
    out = [header]
    for n, chunk in enumerate(chunks):
        last = n == len(chunks) - 1
        out.append(aes.encrypt(prefix + struct.pack(">I", n), chunk, header + bytes([1 if last else 0])))
    return b"".join(out)


def decrypt(blob, password):
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    header = blob[:HEADER_LEN]
    if len(header) < HEADER_LEN or header[:4] != MAGIC or header[4] != 1 or header[5] != 1:
        raise ValueError("bad encryption header")
    iterations = struct.unpack(">I", header[6:10])[0]
    if not 10_000 <= iterations <= 5_000_000:
        raise ValueError("unsupported iterations")
    salt, prefix = header[10:26], header[26:34]
    aes = AESGCM(_key(password, salt, iterations))
    body = blob[HEADER_LEN:]
    size = CHUNK + 16
    pieces = [body[i:i + size] for i in range(0, len(body), size)]
    if not pieces:
        raise ValueError("truncated")
    plain = []
    for n, piece in enumerate(pieces):
        last = n == len(pieces) - 1
        plain.append(aes.decrypt(prefix + struct.pack(">I", n), piece, header + bytes([1 if last else 0])))
    return b"".join(plain)


# ------------------------------------------------------------------ zip like java.util.zip.ZipOutputStream

def java_style_zip(entries):
    """DEFLATE entries with general-purpose flag bit 3 (sizes in a data descriptor) and bit 11 (UTF-8 names)."""
    out = io.BytesIO()
    central = []
    for name, data in entries:
        nb = name.encode("utf-8")
        offset = out.tell()
        comp = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -15)
        cdata = comp.compress(data) + comp.flush()
        crc = zlib.crc32(data) & 0xFFFFFFFF
        flags = 0x0808
        out.write(struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, flags, 8, 0, 0x21, 0, 0, 0, len(nb), 0) + nb)
        out.write(cdata)
        out.write(struct.pack("<IIII", 0x08074B50, crc, len(cdata), len(data)))
        central.append(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, flags, 8, 0, 0x21, crc, len(cdata), len(data),
                                   len(nb), 0, 0, 0, 0, 0, offset) + nb)
    cd_start = out.tell()
    for c in central:
        out.write(c)
    cd_size = out.tell() - cd_start
    out.write(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(central), len(central), cd_size, cd_start, 0))
    return out.getvalue()


def build(data, images, created_at=1_759_600_000_000, app_version="1.0.0", encrypted=False):
    data_bytes = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    files = {"data.json": sha256(data_bytes)}
    for name, b in images:
        files["images/" + name] = sha256(b)
    manifest = {
        "format": FORMAT_ID, "formatVersion": 1, "createdAt": created_at, "appVersion": app_version, "encrypted": encrypted,
        "counts": {"contacts": len(data["contacts"]), "categories": len(data["categories"]), "tags": len(data["tags"]),
                   "followUps": len(data["followUps"]), "images": len(images)},
        "files": files,
    }
    entries = [("manifest.json", json.dumps(manifest, separators=(",", ":")).encode("utf-8")), ("data.json", data_bytes)]
    entries += [("images/" + n, b) for n, b in images]
    return java_style_zip(entries)


# ------------------------------------------------------------------ strict reader

def verify(blob, password=None):
    """Returns (manifest, data, images) or raises ValueError naming the broken rule."""
    if blob[:4] == MAGIC:
        if password is None:
            raise ValueError("needs password")
        blob = decrypt(blob, password)
    if blob[:2] != b"PK":
        raise ValueError("not a zip")
    z = zipfile.ZipFile(io.BytesIO(blob))
    infos = sorted(z.infolist(), key=lambda i: i.header_offset)
    if not infos or infos[0].filename != "manifest.json":
        raise ValueError("manifest.json must be the first entry")
    if z.testzip() is not None:
        raise ValueError("crc mismatch")
    manifest = json.loads(z.read("manifest.json"))
    if manifest.get("format") != FORMAT_ID or manifest.get("formatVersion") != 1:
        raise ValueError("wrong format id/version")
    seen = {}
    images = {}
    for info in infos[1:]:
        if info.filename == "data.json" or SAFE_IMAGE.match(info.filename):
            b = z.read(info)
            seen[info.filename] = sha256(b)
            if info.filename != "data.json":
                images[info.filename[len("images/"):]] = b
    for name, digest in manifest["files"].items():
        if seen.get(name) != digest:
            raise ValueError("checksum mismatch for " + name)
    if "data.json" not in manifest["files"]:
        raise ValueError("data.json not covered by a checksum")
    data = json.loads(z.read("data.json"))
    counts = manifest["counts"]
    for key in ("contacts", "categories", "tags", "followUps"):
        if counts[key] != len(data[key]):
            raise ValueError("count mismatch for " + key)
    ids = {c["id"] for c in data["contacts"]}
    for f in data["followUps"]:
        if f["contactId"] not in ids:
            raise ValueError("follow-up points at a missing contact")
    for c in data["contacts"]:
        for side in ("frontImage", "backImage"):
            if c.get(side) and c[side] not in images:
                raise ValueError("contact references a missing image")
    return manifest, data, images


# ------------------------------------------------------------------ fixtures

def sample():
    """Fictional data covering every field the apps read, including Unicode and statuses added later."""
    data = {
        "categories": [
            {"id": 1, "name": "", "colorArgb": 0xFF2E7D32, "systemKey": "customer", "sortOrder": 0, "createdAt": 1_700_000_000_000},
            {"id": 2, "name": "", "colorArgb": 0xFFA0522D, "systemKey": "supplier", "sortOrder": 1, "createdAt": 1_700_000_000_000},
            {"id": 9, "name": "Investors", "colorArgb": 0xFF000000, "systemKey": None, "sortOrder": 9, "createdAt": 1_700_000_000_000},
        ],
        "tags": [{"id": 1, "name": "VIP", "colorArgb": None, "createdAt": 1_700_000_000_000},
                 {"id": 2, "name": "expo", "colorArgb": None, "createdAt": 1_700_000_000_000}],
        "contacts": [
            {"id": 11, "fullName": "Asha Rao", "jobTitle": "Senior Software Engineer", "company": "Acme Technologies",
             "phone": "+91 98765 43210", "phoneAlt": "", "email": "asha.rao@acme.example", "emailAlt": "", "website": "acme.example",
             "address": "Plot 12, Banjara Hills, Hyderabad", "notes": "హరి — met at the expo\nsecond line", "categoryId": 1,
             "isFavorite": True, "frontImage": "front-11.jpg", "backImage": None, "source": "SCAN",
             "createdAt": 1_750_000_000_000, "updatedAt": 1_751_000_000_000, "tagIds": [1, 2]},
            {"id": 12, "fullName": "Ben Ito", "jobTitle": "", "company": "Globex", "phone": "+1 555 123 4567", "phoneAlt": "",
             "email": "", "emailAlt": "", "website": "", "address": "", "notes": "", "categoryId": 9, "isFavorite": False,
             "frontImage": None, "backImage": None, "source": "MANUAL", "createdAt": 1_752_000_000_000,
             "updatedAt": 1_752_000_000_000, "tagIds": []},
        ],
        "followUps": [
            {"id": 21, "contactId": 11, "title": "Send revised quotation", "type": "QUOTATION", "status": "RESCHEDULED", "notes": "",
             "dueAt": 4_102_444_800_000, "reminderEnabled": True, "reminderOffsetMinutes": 15, "completedAt": None,
             "notifiedAt": None, "createdAt": 1_750_000_000_000, "updatedAt": 1_751_000_000_000},
            {"id": 22, "contactId": 12, "title": "Call about delivery", "type": "CALL", "status": "COMPLETED", "notes": "Done",
             "dueAt": 1_751_000_000_000, "reminderEnabled": False, "reminderOffsetMinutes": 0, "completedAt": 1_751_000_100_000,
             "notifiedAt": None, "createdAt": 1_750_000_000_000, "updatedAt": 1_751_000_100_000},
        ],
        "myCard": {"fullName": "Sam Lee", "jobTitle": "Founder", "company": "Northwind", "phone": "+44 20 7946 0958",
                   "phoneAlt": "", "email": "sam@northwind.example", "emailAlt": "", "website": "", "address": ""},
    }
    # A tiny but real JPEG (1x1, grey), so the apps can also decode it.
    jpeg = bytes.fromhex(
        "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c"
        "20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc4001f0000010501010101010100"
        "000000000000000102030405060708090a0bffc400b5100002010303020403050504040000017d01020300041105122131410613516107227114"
        "328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a63646566"
        "6768696a737475767778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3"
        "d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00fbd3ffd9")
    return data, [("front-11.jpg", jpeg)]


def write_fixtures(directory):
    os.makedirs(directory, exist_ok=True)
    data, images = sample()
    plain = build(data, images)
    with open(os.path.join(directory, "sample-plain.vcbackup"), "wb") as f:
        f.write(plain)
    # Fixed salt and prefix so the fixture is reproducible; low iteration count keeps tests fast (the apps accept >= 10,000).
    locked = encrypt(build(data, images, encrypted=True), "correct horse", iterations=10_000, salt=b"s" * 16, prefix=b"p" * 8)
    with open(os.path.join(directory, "sample-encrypted.vcbackup"), "wb") as f:
        f.write(locked)
    # Larger than one 64 KiB encryption chunk: contact 12 gets a 150 KB back image (deterministic bytes, not a real JPEG).
    big = b"".join(hashlib.sha256(i.to_bytes(4, "big")).digest() for i in range(4_700))[:150_000]  # incompressible
    data["contacts"][1]["backImage"] = "back-12.jpg"
    multi = encrypt(build(data, images + [("back-12.jpg", big)], encrypted=True), "correct horse", iterations=10_000,
                    salt=b"t" * 16, prefix=b"q" * 8)
    with open(os.path.join(directory, "sample-multichunk.vcbackup"), "wb") as f:
        f.write(multi)
    for name in ("sample-plain.vcbackup", "sample-encrypted.vcbackup", "sample-multichunk.vcbackup"):
        with open(os.path.join(directory, name), "rb") as f:
            verify(f.read(), "correct horse")
    print("fixtures written to", directory)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "fixtures":
        write_fixtures(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == "verify":
        with open(sys.argv[2], "rb") as fh:
            m, d, imgs = verify(fh.read(), sys.argv[3] if len(sys.argv) > 3 else None)
        print("OK", sys.argv[2], m["counts"], "images:", len(imgs))
    else:
        print(__doc__)
        sys.exit(2)
