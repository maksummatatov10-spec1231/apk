#!/usr/bin/env bash
# Самопроверка логики калькулятора: сценарии нажатий (CalcEngine) через реальный решатель.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test-calc"
mkdir -p "$OUT/classes"
AJ="$T/platforms/android-31/android.jar"
export PATH="$T/bin:$PATH"
find "$HERE/src/com/example/calcpro/math" -name '*.java' > "$OUT/sources.txt"
echo "$HERE/src/com/example/calcpro/CalcEngine.java" >> "$OUT/sources.txt"
echo "$HERE/test/com/example/calcpro/CalcEngineCheck.java" >> "$OUT/sources.txt"
mapfile -t SRC < "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn -bootclasspath "$AJ" -d "$OUT/classes" "${SRC[@]}"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.calcpro.CalcEngineCheck
