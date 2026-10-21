"""Minimal zipalign: rewrites an APK so every stored (uncompressed) entry starts on a 4-byte boundary."""
import struct
import sys
import zipfile

src, dst = sys.argv[1], sys.argv[2]
ALIGN = 4

with zipfile.ZipFile(src) as zin, open(dst, "wb") as out:
    central = []
    for info in zin.infolist():
        raw = zin.open(info)  # decompresses; we need the raw bytes instead
        raw.close()
        # read raw (possibly compressed) data straight from the source file
        with open(src, "rb") as f:
            f.seek(info.header_offset)
            hdr = f.read(30)
            (sig, ver, flag, meth, mtime, mdate, crc, csize, usize, nlen, xlen) = struct.unpack("<IHHHHHIIIHH", hdr)
            name = f.read(nlen)
            f.read(xlen)
            data = f.read(info.compress_size)
        offset = out.tell()
        extra = b""
        if info.compress_type == zipfile.ZIP_STORED:
            pos = offset + 30 + len(name)
            pad = (-pos) % ALIGN
            extra = b"\x00" * pad
        flag &= ~0x08  # sizes are written in the local header, no data descriptor
        out.write(struct.pack("<IHHHHHIIIHH", 0x04034B50, ver, flag, meth, mtime, mdate,
                              info.CRC, info.compress_size, info.file_size, len(name), len(extra)))
        out.write(name)
        out.write(extra)
        out.write(data)
        central.append((info, name, offset, flag, meth, mtime, mdate, ver))

    cd_start = out.tell()
    for info, name, offset, flag, meth, mtime, mdate, ver in central:
        out.write(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, info.create_version, ver, flag, meth,
                              mtime, mdate, info.CRC, info.compress_size, info.file_size, len(name),
                              0, 0, 0, 0, info.external_attr, offset))
        out.write(name)
    cd_size = out.tell() - cd_start
    n = len(central)
    out.write(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, n, n, cd_size, cd_start, 0))
print("aligned", n, "entries")
