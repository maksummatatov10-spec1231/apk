#!/usr/bin/env bash
# Самопроверка математического ядра на JVM (без Android). Использует javac 8 и JRE из тулчейна.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT"
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT" \
  "$HERE"/src/com/example/calcpro/math/*.java "$HERE"/test/com/example/calcpro/math/SolverCheck.java
"$T/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" com.example.calcpro.math.SolverCheck
