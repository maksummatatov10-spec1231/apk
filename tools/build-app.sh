#!/usr/bin/env bash
# Универсальная сборка APK без Gradle/Android Studio:
#   aapt2 (ресурсы, R.java) -> javac 8 -> D8 (dex) -> zip -> подпись v1+v2 -> проверка
# Использование: tools/build-app.sh <папка приложения> <имя_файла.apk>
# В папке приложения ожидаются: AndroidManifest.xml, res/, src/
# Результат: <папка>/build/<имя_файла.apk>
set -euo pipefail

APP="$(cd "${1:?укажите папку приложения}" && pwd)"
NAME="${2:?укажите имя выходного APK}"
TOOLS="$(cd "$(dirname "$0")" && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$APP/build"
ANDROID_JAR="$T/platforms/android-31/android.jar"
KEYDIR="$T/debug-key"
MIN_SDK="${MIN_SDK:-21}"
TARGET_SDK="${TARGET_SDK:-31}"

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

echo "==> 1/6 aapt2 compile"
aapt2 compile --dir "$APP/res" -o "$OUT/compiled/res.zip"

echo "==> 2/6 aapt2 link (targetSdk $TARGET_SDK, minSdk $MIN_SDK)"
aapt2 link -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code 1 --version-name 1.0 \
  -o "$OUT/base.apk" "$OUT/compiled/res.zip"

echo "==> 3/6 javac 8"
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
mapfile -t SOURCES < "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" "${SOURCES[@]}"

echo "==> 4/6 D8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
mapfile -t CLASSES < "$OUT/classes.txt"
d8 --min-api "$MIN_SDK" --lib "$ANDROID_JAR" --output "$OUT/dex" "${CLASSES[@]}"

echo "==> 5/6 упаковка и подпись (v1 + v2)"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes*.dex)
python3 "$TOOLS/sign_apk.py" "$OUT/unsigned.apk" "$OUT/$NAME" "$KEYDIR/key.pem" "$KEYDIR/cert.pem"

echo "==> 6/6 проверка"
aapt2 dump badging "$OUT/$NAME" | head -4
python3 "$TOOLS/verify_apk.py" "$OUT/$NAME"
echo "==> Готово: $OUT/$NAME ($(stat -c %s "$OUT/$NAME") байт)"
