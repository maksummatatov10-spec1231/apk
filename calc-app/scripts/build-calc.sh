#!/usr/bin/env bash
# Сборка APK калькулятора без Android Studio/Gradle:
#   aapt2 compile/link (ресурсы, R.java) -> javac 8 -> D8 (dex) -> zip -> подпись v1 -> проверка
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$(cd "$HERE/../tools" && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build"
ANDROID_JAR="$T/platforms/android-31/android.jar"
KEYDIR="$T/debug-key"

for f in "$T/bin/javac" "$T/bin/d8" "$T/bin/aapt2" "$ANDROID_JAR"; do
  if [ ! -e "$f" ]; then
    echo "Нет инструмента: $f. Запустите: $TOOLS/setup-toolchain.sh" >&2
    exit 1
  fi
done
export PATH="$T/bin:$PATH"

if [ ! -f "$KEYDIR/key.pem" ]; then
  mkdir -p "$KEYDIR"
  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 10000 \
    -subj "/CN=Android Debug/O=Android/C=US" \
    -keyout "$KEYDIR/key.pem" -out "$KEYDIR/cert.pem" 2>/dev/null
fi

rm -rf "$OUT" && mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> 1/6 aapt2 compile (ресурсы)"
aapt2 compile --dir "$HERE/res" -o "$OUT/compiled/res.zip"

echo "==> 2/6 aapt2 link (манифест, R.java, targetSdk 31, minSdk 21)"
aapt2 link -I "$ANDROID_JAR" \
  --manifest "$HERE/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 21 --target-sdk-version 31 \
  --version-code 1 --version-name 1.0 \
  -o "$OUT/base.apk" "$OUT/compiled/res.zip"

echo "==> 3/6 javac 8 (исходники Java 8, bootclasspath = android.jar API 31)"
find "$HERE/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
mapfile -t SOURCES < "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" "${SOURCES[@]}"

echo "==> 4/6 D8 (байткод -> dex)"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
mapfile -t CLASSES < "$OUT/classes.txt"
d8 --min-api 21 --lib "$ANDROID_JAR" --output "$OUT/dex" "${CLASSES[@]}"

echo "==> 5/6 упаковка и подпись (v1)"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)
python3 "$TOOLS/sign_apk.py" "$OUT/unsigned.apk" "$OUT/calc.apk" "$KEYDIR/key.pem" "$KEYDIR/cert.pem"

echo "==> 6/6 проверка"
aapt2 dump badging "$OUT/calc.apk" | head -6
TMPD="$(mktemp -d)"; trap 'rm -rf "$TMPD"' EXIT
unzip -q "$OUT/calc.apk" 'META-INF/*' -d "$TMPD"
openssl cms -verify -noverify -binary -inform DER \
  -in "$TMPD/META-INF/CERT.RSA" -content "$TMPD/META-INF/CERT.SF" >/dev/null 2>&1 && echo "Подпись CERT.RSA: OK"
echo "==> Готово: $OUT/calc.apk ($(stat -c %s "$OUT/calc.apk") байт)"
