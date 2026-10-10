#!/usr/bin/env bash
# Сборка и подпись APK из smali-исходников.
#   1. apktool b  — smali -> classes.dex, ресурсы/манифест -> aapt2
#   2. sign_apk.py — подпись схемами v1 + v2 (debug-ключ)
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build"
KEYDIR="$T/debug-key"

if [ ! -x "$T/bin/java" ] || [ ! -f "$T/apktool.jar" ]; then
  echo "Тулчейн не найден. Запустите: scripts/setup-toolchain.sh" >&2
  exit 1
fi
export PATH="$T/bin:$PATH"

# Debug-ключ (создаётся один раз, вне репозитория)
if [ ! -f "$KEYDIR/key.pem" ]; then
  mkdir -p "$KEYDIR"
  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 10000 \
    -subj "/CN=Android Debug/O=Android/C=US" \
    -keyout "$KEYDIR/key.pem" -out "$KEYDIR/cert.pem" 2>/dev/null
fi

rm -rf "$OUT" && mkdir -p "$OUT"

echo "==> apktool b (smali + aapt2)"
java -jar "$T/apktool.jar" b "$HERE/app" --use-aapt2 -p "$OUT/framework" \
  -o "$OUT/unsigned.apk"

echo "==> Подпись (v1 + v2)"
python3 "$HERE/../tools/sign_apk.py" "$OUT/unsigned.apk" "$OUT/hello.apk" \
  "$KEYDIR/key.pem" "$KEYDIR/cert.pem"

echo "==> Проверка подписи"
python3 "$HERE/../tools/verify_apk.py" "$OUT/hello.apk"

echo "==> Готово: $OUT/hello.apk"
ls -la "$OUT/hello.apk"
