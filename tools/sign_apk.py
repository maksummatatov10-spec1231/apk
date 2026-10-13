#!/usr/bin/env python3
"""Подписывает APK схемами v1 (JAR) и v2 (APK Signature Scheme v2).

Использование: sign_apk.py <in.apk> <out.apk> <key.pem> <cert.pem>

  * v1 (JAR): META-INF/MANIFEST.MF, CERT.SF, CERT.RSA. Нужна для старых устройств.
    Подпись CERT.RSA создаётся через `openssl cms` (detached PKCS#7).
  * v2: блок APK Signing Block (ID 0x7109871a) перед Central Directory.
    Android 11+ (targetSdk >= 30) без v2 отказывается ставить APK и пишет
    «пакет не действителен / повреждён». Формат:
    https://source.android.com/docs/security/features/apksigning/v2
    Алгоритм: RSASSA-PKCS1-v1_5 с SHA-256 (ID 0x0103), через openssl.
    В CERT.SF добавляется `X-Android-APK-Signed: 2` (защита от понижения схемы).
"""
import base64
import hashlib
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def wrap(line: str) -> str:
    """Манифест JAR: строки не длиннее 72 байт, продолжение начинается с пробела."""
    data = line.encode("utf-8")
    out = []
    while len(data) > 72:
        out.append(data[:72].decode("utf-8", "ignore"))
        data = b" " + data[72:]
    out.append(data.decode("utf-8"))
    return "\r\n".join(out) + "\r\n"


def sha256_b64(data: bytes) -> str:
    return base64.b64encode(hashlib.sha256(data).digest()).decode()


# ---------------------------------------------------------------------------
# v1
# ---------------------------------------------------------------------------

def build_v1(src: str, dst: str, key: str, cert: str) -> None:
    with zipfile.ZipFile(src) as zin:
        entries = [i for i in zin.infolist()
                   if not i.is_dir() and not i.filename.startswith("META-INF/")]
        contents = {i.filename: zin.read(i.filename) for i in entries}
        infos = {i.filename: i for i in entries}

    # 1. MANIFEST.MF — digest каждого файла
    manifest_sections = {}
    manifest = wrap("Manifest-Version: 1.0") + wrap("Created-By: sign_apk.py") + "\r\n"
    for name in sorted(contents):
        section = wrap(f"Name: {name}") + wrap(f"SHA-256-Digest: {sha256_b64(contents[name])}") + "\r\n"
        manifest_sections[name] = section
        manifest += section
    manifest_bytes = manifest.encode("utf-8")

    # 2. CERT.SF — digest всего MANIFEST.MF и digest каждой секции манифеста
    sf = (wrap("Signature-Version: 1.0") + wrap("Created-By: sign_apk.py")
          + wrap("X-Android-APK-Signed: 2")
          + wrap(f"SHA-256-Digest-Manifest: {sha256_b64(manifest_bytes)}") + "\r\n")
    for name in sorted(manifest_sections):
        sf += (wrap(f"Name: {name}")
               + wrap(f"SHA-256-Digest: {sha256_b64(manifest_sections[name].encode('utf-8'))}")
               + "\r\n")
    sf_bytes = sf.encode("utf-8")

    # 3. CERT.RSA — detached PKCS#7 подпись CERT.SF
    with tempfile.TemporaryDirectory() as tmp:
        sf_path = Path(tmp) / "CERT.SF"
        rsa_path = Path(tmp) / "CERT.RSA"
        sf_path.write_bytes(sf_bytes)
        subprocess.run(
            ["openssl", "cms", "-sign", "-binary", "-in", str(sf_path),
             "-signer", cert, "-inkey", key, "-outform", "DER",
             "-md", "sha256", "-out", str(rsa_path)],
            check=True,
        )
        rsa_bytes = rsa_path.read_bytes()

    # 4. Пересборка APK: META-INF первыми, затем исходные записи.
    # Несжатые записи (resources.arsc, .so) выравниваются по 4 байта — требуется для targetSdk >= 30.
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        zout.writestr("META-INF/MANIFEST.MF", manifest_bytes)
        zout.writestr("META-INF/CERT.SF", sf_bytes)
        zout.writestr("META-INF/CERT.RSA", rsa_bytes)
        for name in sorted(contents):
            info = infos[name]
            if info.compress_type == zipfile.ZIP_STORED:
                zi = zipfile.ZipInfo(name, date_time=info.date_time)
                zi.compress_type = zipfile.ZIP_STORED
                zi.external_attr = 0o644 << 16
                header = zout.fp.tell() + 30 + len(name.encode("utf-8"))
                pad = (-header) % 4
                if pad in (1, 2, 3):
                    pad += 4
                if pad:
                    # extra-поле: id 0xD935 (выравнивание), длина pad-4
                    zi.extra = b"\xd9\x35" + (pad - 4).to_bytes(2, "little") + b"\x00" * (pad - 4)
                zout.writestr(zi, contents[name])
            else:
                zout.writestr(name, contents[name], compress_type=zipfile.ZIP_DEFLATED)


# ---------------------------------------------------------------------------
# v2
# ---------------------------------------------------------------------------

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
SIG_RSA_PKCS1_SHA256 = 0x0103
CHUNK = 1024 * 1024


def _u32(v: int) -> bytes:
    return v.to_bytes(4, "little")


def _lp(data: bytes) -> bytes:
    """Поле с 4-байтной длиной (little-endian) перед данными."""
    return _u32(len(data)) + data


def _openssl(*args: str, stdin: bytes | None = None) -> bytes:
    return subprocess.run(["openssl", *args], input=stdin, check=True, capture_output=True).stdout


def content_digest(sections: list) -> bytes:
    """Двухуровневый дайджест по чанкам 1 МБ (см. спецификацию v2)."""
    chunk_digests = []
    for sec in sections:
        for i in range(0, len(sec), CHUNK):
            chunk = sec[i:i + CHUNK]
            chunk_digests.append(hashlib.sha256(b"\xa5" + _u32(len(chunk)) + chunk).digest())
    top = b"\x5a" + _u32(len(chunk_digests)) + b"".join(chunk_digests)
    return hashlib.sha256(top).digest()


def add_v2_signature(apk_path: str, key: str, cert: str) -> None:
    data = Path(apk_path).read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0 or len(data) - eocd != 22:
        raise SystemExit("Неожиданный формат ZIP: EOCD с комментарием не поддерживается")
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]

    sec1 = data[:cd_offset]                    # записи ZIP
    sec3 = data[cd_offset:eocd]                # Central Directory
    sec4 = bytearray(data[eocd:])              # EOCD
    # В дайджесте EOCD должен указывать на исходное смещение CD (без блока подписи).
    struct.pack_into("<I", sec4, 16, cd_offset)

    digest = content_digest([sec1, sec3, bytes(sec4)])

    cert_der = _openssl("x509", "-in", cert, "-outform", "DER")
    pub_pem = _openssl("x509", "-in", cert, "-pubkey", "-noout")
    pub_der = _openssl("pkey", "-pubin", "-outform", "DER", stdin=pub_pem)

    # signed data: digests, certificates, additional attributes (пусто)
    digest_record = _u32(SIG_RSA_PKCS1_SHA256) + _lp(digest)
    digests_field = _lp(_lp(digest_record))
    certs_field = _lp(_lp(cert_der))
    attrs_field = _lp(b"")
    signed_data = digests_field + certs_field + attrs_field

    with tempfile.TemporaryDirectory() as tmp:
        sd_file = Path(tmp) / "signed_data.bin"
        sd_file.write_bytes(signed_data)
        sig = _openssl("dgst", "-sha256", "-sign", key, str(sd_file))

    sig_record = _u32(SIG_RSA_PKCS1_SHA256) + _lp(sig)
    signatures_field = _lp(_lp(sig_record))
    signer = _lp(signed_data) + signatures_field + _lp(pub_der)
    v2_value = _lp(_lp(signer))                # последовательность подписантов

    pair = _u32(V2_BLOCK_ID) + v2_value
    pairs = struct.pack("<Q", len(pair)) + pair
    block_size = len(pairs) + 8 + len(APK_SIG_BLOCK_MAGIC)
    signing_block = (struct.pack("<Q", block_size) + pairs
                     + struct.pack("<Q", block_size) + APK_SIG_BLOCK_MAGIC)

    new_cd_offset = cd_offset + len(signing_block)
    eocd_new = bytearray(sec4)
    struct.pack_into("<I", eocd_new, 16, new_cd_offset)
    Path(apk_path).write_bytes(sec1 + signing_block + sec3 + bytes(eocd_new))


def main() -> None:
    src, dst, key, cert = sys.argv[1:5]
    build_v1(src, dst, key, cert)
    add_v2_signature(dst, key, cert)
    print(f"Подписано (v1 + v2): {dst}")


if __name__ == "__main__":
    main()
