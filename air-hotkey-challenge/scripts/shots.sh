#!/usr/bin/env bash
# Снимки экранов игры: Java-заглушки Android пишут команды, render_frames.py рисует PNG в build/screens.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
export PATH="$T/bin:$PATH"
find "$HERE/test/stubs" "$HERE/src/com/example/airhotkey/World.java" "$HERE/src/com/example/airhotkey/Fx.java" \
     "$HERE/test/com/example/airhotkey" -name "*.java" > "$OUT/sources.txt"
mapfile -t SRC < "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" "${SRC[@]}"
JAVA="$T/jre/bin/java"; [ -x "$JAVA" ] || JAVA=java
"$JAVA" -cp "$OUT/classes" com.example.airhotkey.WorldSim --shots "$HERE/build/screens"
# PNG: нужен Pillow (python3 с PIL или venv с ним)
PY="${RENDER_PYTHON:-python3}"
for t in "$HERE"/build/screens/*.txt; do "$PY" "$HERE/scripts/render_frames.py" "$t" "${t%.txt}.png" 720 1280; done
