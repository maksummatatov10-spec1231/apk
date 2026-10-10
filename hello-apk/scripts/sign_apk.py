#!/usr/bin/env python3
"""Подписывает APK схемой v1 (JAR-подпись): META-INF/MANIFEST.MF, CERT.SF, CERT.RSA.

Использование: sign_apk.py <in.apk> <out.apk> <key.pem> <cert.pem>

Схема v1 принимается Android для minSdkVersion < 24 (здесь minSdk = 21).
Подпись CERT.RSA создаётся через `openssl cms` (detached PKCS#7).
"""
import base64
import hashlib
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


def main() -> None:
    src, dst, key, cert = sys.argv[1:5]

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

    # 4. Пересборка APK: META-INF первыми, затем исходные записи без изменений
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        zout.writestr("META-INF/MANIFEST.MF", manifest_bytes)
        zout.writestr("META-INF/CERT.SF", sf_bytes)
        zout.writestr("META-INF/CERT.RSA", rsa_bytes)
        for name in sorted(contents):
            info = infos[name]
            # resources.arsc и сжатые файлы сохраняем как есть (без выравнивания)
            ctype = zipfile.ZIP_STORED if info.compress_type == zipfile.ZIP_STORED else zipfile.ZIP_DEFLATED
            zout.writestr(name, contents[name], compress_type=ctype)

    print(f"Подписано (v1): {dst}")


if __name__ == "__main__":
    main()
