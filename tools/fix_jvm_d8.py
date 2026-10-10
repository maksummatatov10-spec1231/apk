# Перевод jar, полученного из enjarify, в рабочий для JVM 25 (см. setup-toolchain.sh).
"""Fix enjarify-translated D8 jars so they run on a modern JVM.
 1. class major version 49 -> 52 (default methods)
 2. interface methods without Code get ACC_ABSTRACT
 3. Methodref -> InterfaceMethodref when the owner is an interface
Usage: fix_all.py out.jar in1.jar [in2.jar ...]
"""
import struct, sys, zipfile

def parse_cp(data):
    p = 8
    n = struct.unpack_from('>H', data, p)[0]; p += 2
    entries = {}  # idx -> (tag, offset, length)
    i = 1
    while i < n:
        tag = data[p]
        if tag == 1:
            ln = struct.unpack_from('>H', data, p+1)[0]
            entries[i] = (1, p, 3+ln); p += 3+ln
        elif tag in (3,4,9,10,11,12,17,18):
            entries[i] = (tag, p, 5); p += 5
        elif tag in (5,6):
            entries[i] = (tag, p, 9); p += 9; i += 1
        elif tag in (7,8,16,19,20):
            entries[i] = (tag, p, 3); p += 3
        elif tag == 15:
            entries[i] = (tag, p, 4); p += 4
        else:
            raise ValueError(f"bad tag {tag}")
        i += 1
    return entries, p  # p = offset after constant pool

def utf8(data, entries, idx):
    tag, off, _ = entries[idx]
    ln = struct.unpack_from('>H', data, off+1)[0]
    return data[off+3:off+3+ln].decode('utf-8', 'replace')

def class_info(data, entries):
    """Return (is_interface, this_name, {cp_idx: class_name})"""
    entries_cls = {}
    for idx,(tag,off,_) in entries.items():
        if tag == 7:
            entries_cls[idx] = utf8(data, entries, struct.unpack_from('>H', data, off+1)[0])
    p = entries_cls and None
    _, after = parse_cp(data)
    access = struct.unpack_from('>H', data, after)[0]
    this_idx = struct.unpack_from('>H', data, after+2)[0]
    return access, entries_cls[this_idx] if this_idx in entries_cls else None, entries_cls

def main():
    out, ins = sys.argv[1], sys.argv[2:]
    blobs = {}
    for src in ins:
        with zipfile.ZipFile(src) as z:
            for info in z.infolist():
                blobs[info.filename] = z.read(info.filename)
    # pass 1: interface names
    interfaces = set()
    for name, data in blobs.items():
        if name.endswith('.class') and data[:4] == b'\xca\xfe\xba\xbe':
            entries, after = parse_cp(data)
            access, this_name, _ = class_info(data, entries)
            if access & 0x200 and this_name:
                interfaces.add(this_name)
    import os
    if os.environ.get('JDK_IFACES'):
        with open(os.environ['JDK_IFACES']) as f:
            interfaces.update(l.strip() for l in f if l.strip())
    print('interfaces:', len(interfaces))
    stats = dict(version=0, abstract=0, ifacerefs=0)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for name, data in blobs.items():
            if name.endswith('.class') and data[:4] == b'\xca\xfe\xba\xbe':
                data = bytearray(data)
                major = struct.unpack_from('>H', data, 6)[0]
                if major < 52:
                    struct.pack_into('>H', data, 6, 52); stats['version'] += 1
                entries, after = parse_cp(bytes(data))
                # Methodref -> InterfaceMethodref
                for idx,(tag,off,_) in entries.items():
                    if tag == 10:
                        cls_idx = struct.unpack_from('>H', data, off+1)[0]
                        cls_name = utf8(bytes(data), entries, struct.unpack_from('>H', bytes(data), entries[cls_idx][1]+1)[0]) if cls_idx in entries else None
                        if cls_name in interfaces:
                            data[off] = 11; stats['ifacerefs'] += 1
                # methods without Code in interfaces -> ACC_ABSTRACT
                access, _, _ = class_info(bytes(data), entries)
                if access & 0x200:
                    p = after
                    p += 6
                    icount = struct.unpack_from('>H', data, p)[0]; p += 2 + 2*icount
                    fcount = struct.unpack_from('>H', data, p)[0]; p += 2
                    for _ in range(fcount):
                        p += 6
                        na = struct.unpack_from('>H', data, p)[0]; p += 2
                        for _ in range(na):
                            p += 6 + struct.unpack_from('>I', data, p+2)[0]
                    mcount = struct.unpack_from('>H', data, p)[0]; p += 2
                    for _ in range(mcount):
                        acc = struct.unpack_from('>H', data, p)[0]
                        na = struct.unpack_from('>H', data, p+6)[0]
                        q = p + 8; has_code = False
                        for _ in range(na):
                            ai = struct.unpack_from('>H', data, q)[0]
                            ln = struct.unpack_from('>I', data, q+2)[0]
                            if utf8(bytes(data), entries, ai) == 'Code': has_code = True
                            q += 6 + ln
                        if not has_code and not (acc & 0x0408) and not (acc & 0x0002):
                            struct.pack_into('>H', data, p, acc | 0x0400); stats['abstract'] += 1
                        p = q
                data = bytes(data)
            zout.writestr(name, data)
    print('stats:', stats, '->', out)

if __name__ == "__main__":
    main()
