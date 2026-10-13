#!/usr/bin/env bash
# Проверка физики и ИИ на JVM (без Android).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT"
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT" \
  "$HERE"/src/com/example/airhockey/Table.java "$HERE"/test/com/example/airhockey/TableCheck.java
"$T/jre/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" com.example.airhockey.TableCheck
