#!/usr/bin/env python3
"""Независимая проверка подписи APK: схемы v2 (блок подписи) и v1 (CERT.RSA/CERT.SF).

Использование: verify_apk.py <app.apk>

Проверяет по спецификации https://source.android.com/docs/security/features/apksigning/v2:
  1. APK Signing Block: магия «APK Sig Block 42», совпадение двух размеров, пара ID 0x7109871a;
  2. signed data: дайджест SHA-256 по чанкам 1 МБ совпадает с пересчитанным от файла;
  3. подпись RSASSA-PKCS1-v1_5/SHA-256 проверяется публичным ключом из блока (openssl);
  4. сертификат из блока совпадает с публичным ключом;
  5. v1: CERT.SF содержит X-Android-APK-Signed: 2, дайджесты манифеста и файлов совпадают,
     CERT.RSA подписывает CERT.SF.
Код выхода 0 — подпись корректна.
"""
import base64
import hashlib
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
SIG_RSA_SHA256 = 0x0103
CHUNK = 1024 * 1024


class VerifyError(Exception):
    pass


def openssl(*args, stdin=None):
    r = subprocess.run(["openssl", *args], input=stdin, capture_output=True)
    return r


def find_signing_block(data: bytes):
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise VerifyError("нет EOCD")
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_offset - 16:cd_offset] != MAGIC:
        raise VerifyError("перед Central Directory нет магии APK Signing Block")
    size1 = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    block_start = cd_offset - 8 - size1
    size2 = struct.unpack_from("<Q", data, block_start)[0]
    if size1 != size2:
        raise VerifyError("размеры APK Signing Block не совпадают")
    pairs = []
    p = block_start + 8
    end = cd_offset - 24
    while p < end:
        psize = struct.unpack_from("<Q", data, p)[0]
        pid = struct.unpack_from("<I", data, p + 8)[0]
        pairs.append((pid, data[p + 12:p + 8 + psize]))
        p += 8 + psize
    return block_start, cd_offset, eocd, pairs


def read_lp_seq(buf: bytes):
    """Разобрать последовательность length-prefixed элементов."""
    out, p = [], 0
    while p < len(buf):
        n = struct.unpack_from("<I", buf, p)[0]
        out.append(buf[p + 4:p + 4 + n])
        p += 4 + n
    return out


def verify_v2(data: bytes) -> dict:
    block_start, cd_offset, eocd, pairs = find_signing_block(data)
    v2 = [v for pid, v in pairs if pid == V2_ID]
    if len(v2) != 1:
        raise VerifyError(f"ожидалась одна пара v2, найдено: {len(v2)}")
    # значение пары: u32 размер последовательности подписантов, затем подписанты (каждый с u32 размером)
    signers = read_lp_seq(read_lp_seq(v2[0])[0])
    if len(signers) != 1:
        raise VerifyError("ожидался один подписант")
    fields = read_lp_seq(signers[0])
    signed_data, sigs_field, pubkey_der = fields[0], fields[1], fields[2]

    # Дайджесты (signed data)
    sd_fields = read_lp_seq(signed_data)
    digests = read_lp_seq(sd_fields[0])
    certs = read_lp_seq(sd_fields[1])
    if not digests or not certs:
        raise VerifyError("пустые digests или certificates")
    # digest record: u32 algorithm id + lp(digest)
    alg = struct.unpack_from("<I", digests[0], 0)[0]
    dlen = struct.unpack_from("<I", digests[0], 4)[0]
    digest_val = digests[0][8:8 + dlen]
    if alg != 0x0103:
        raise VerifyError(f"неожиданный алгоритм дайджеста 0x{alg:04x}")

    # Пересчёт дайджеста от содержимого файла (EOCD с исходным смещением CD)
    sec1 = data[:block_start]
    sec3 = data[cd_offset:eocd]
    sec4 = bytearray(data[eocd:])
    struct.pack_into("<I", sec4, 16, block_start)
    chunk_digests = []
    for sec in (sec1, sec3, bytes(sec4)):
        for i in range(0, len(sec), CHUNK):
            c = sec[i:i + CHUNK]
            chunk_digests.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    top = b"\x5a" + struct.pack("<I", len(chunk_digests)) + b"".join(chunk_digests)
    if hashlib.sha256(top).digest() != digest_val:
        raise VerifyError("дайджест содержимого v2 не совпадает")

    # Сертификат и публичный ключ
    cert_der = certs[0]
    pub_from_cert = openssl("x509", "-inform", "DER", "-pubkey", "-noout", stdin=cert_der)
    if pub_from_cert.returncode != 0:
        raise VerifyError("сертификат из блока не разбирается")
    pub_pem_block = openssl("pkey", "-pubin", "-inform", "DER", "-outform", "PEM", stdin=pubkey_der)
    if pub_pem_block.returncode != 0:
        raise VerifyError("публичный ключ из блока не разбирается")
    if pub_from_cert.stdout.strip() != pub_pem_block.stdout.strip():
        raise VerifyError("публичный ключ не совпадает с сертификатом")

    # Подпись
    sig_recs = read_lp_seq(sigs_field)
    if len(sig_recs) != 1:
        raise VerifyError("ожидалась одна подпись")
    sig_alg = struct.unpack_from("<I", sig_recs[0], 0)[0]
    sig_len = struct.unpack_from("<I", sig_recs[0], 4)[0]
    sig = sig_recs[0][8:8 + sig_len]
    if sig_alg != SIG_RSA_SHA256:
        raise VerifyError(f"неподдерживаемый алгоритм подписи 0x{sig_alg:04x}")
    with tempfile.TemporaryDirectory() as tmp:
        sd = Path(tmp) / "sd.bin"
        sf = Path(tmp) / "sig.bin"
        pk = Path(tmp) / "pub.pem"
        sd.write_bytes(signed_data)
        sf.write_bytes(sig)
        pk.write_bytes(pub_pem_block.stdout)
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(pk),
                            "-signature", str(sf), str(sd)], capture_output=True, text=True)
    if r.returncode != 0:
        raise VerifyError("подпись v2 не проверяется: " + r.stderr.strip())

    return {"certificates": len(certs), "block_size": cd_offset - block_start}


def verify_v1(path: str) -> dict:
    try:
        return _verify_v1(path)
    except zipfile.BadZipFile as e:
        raise VerifyError(f"повреждён ZIP: {e}")


def _verify_v1(path: str) -> dict:
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        for need in ("META-INF/MANIFEST.MF", "META-INF/CERT.SF", "META-INF/CERT.RSA"):
            if need not in names:
                raise VerifyError(f"нет {need}")
        manifest = z.read("META-INF/MANIFEST.MF")
        sf = z.read("META-INF/CERT.SF")
        rsa = z.read("META-INF/CERT.RSA")
        if b"X-Android-APK-Signed: 2" not in sf:
            raise VerifyError("в CERT.SF нет X-Android-APK-Signed: 2")
        # дайджест манифеста
        expected = sf.split(b"SHA-256-Digest-Manifest: ", 1)[1].split(b"\r\n", 1)[0].strip()
        if base64.b64encode(hashlib.sha256(manifest).digest()) != expected:
            raise VerifyError("дайджест MANIFEST.MF в CERT.SF не совпадает")
        # дайджесты файлов
        for name in names:
            if name.startswith("META-INF/") or name.endswith("/"):
                continue
            key = f"Name: {name}\r\n".encode()
            idx = manifest.find(key)
            if idx < 0:
                raise VerifyError(f"нет записи манифеста для {name}")
            line = manifest[idx:].split(b"SHA-256-Digest: ", 1)[1].split(b"\r\n", 1)[0]
            if base64.b64encode(hashlib.sha256(z.read(name)).digest()) != line.strip():
                raise VerifyError(f"дайджест файла не совпадает: {name}")
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "CERT.RSA").write_bytes(rsa)
            (Path(tmp) / "CERT.SF").write_bytes(sf)
            r = subprocess.run(["openssl", "cms", "-verify", "-noverify", "-binary", "-inform", "DER",
                                "-in", str(Path(tmp) / "CERT.RSA"), "-content", str(Path(tmp) / "CERT.SF")],
                               capture_output=True)
        if r.returncode != 0:
            raise VerifyError("CERT.RSA не подписывает CERT.SF")
    return {"entries": len(names)}


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    path = sys.argv[1]
    data = Path(path).read_bytes()
    try:
        v1 = verify_v1(path)
        print(f"v1 (JAR): OK, записей {v1['entries']}")
    except VerifyError as e:
        print(f"v1 (JAR): ОШИБКА — {e}")
        sys.exit(1)
    try:
        v2 = verify_v2(data)
        print(f"v2: OK, блок подписи {v2['block_size']} байт, сертификатов {v2['certificates']}")
    except VerifyError as e:
        print(f"v2: ОШИБКА — {e}")
        sys.exit(1)
    print("Итог: APK подписан схемами v1 и v2, подпись корректна")


if __name__ == "__main__":
    main()
