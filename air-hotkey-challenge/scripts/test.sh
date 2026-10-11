#!/usr/bin/env bash
# Симуляция игры без устройства: логика World + отрисовка через заглушки Android-классов.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
export PATH="$T/bin:$PATH"
find "$HERE/test/stubs" "$HERE/src/com/example/airhotkey/World.java" "$HERE/src/com/example/airhotkey/Fx.java" \
     "$HERE/test/com/example/airhotkey" -name "*.java" > "$OUT/sources.txt"
mapfile -t SRC < "$OUT/sources.txt"
# Компиляция против android.jar: API проверяется по настоящим сигнатурам Android 12.
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" "${SRC[@]}"
# Запуск: заглушки из classpath (в JVM нет пакетов android.*).
JAVA="$T/jre/bin/java"; [ -x "$JAVA" ] || JAVA=java
"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.airhotkey.WorldSim
