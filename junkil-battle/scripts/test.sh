#!/usr/bin/env bash
# Тесты логики без Android: Морзе, бой, арена, фразы (JVM). Требует tools/setup-toolchain.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
PURE=(MorseCode BattleLogic Phrases ArenaSim)
SRC=()
for c in "${PURE[@]}"; do SRC+=("$HERE/src/com/example/junkil/$c.java"); done
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" "${SRC[@]}" \
  "$HERE/test/com/example/junkil/LogicCheck.java"
"$T/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.junkil.LogicCheck
