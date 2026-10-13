#!/usr/bin/env bash
# Проверки интерфейса через харнесс (касания, переходы, арена, джойстик).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/ui-test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
SRC=(src/com/example/junkil/MorseCode.java src/com/example/junkil/BattleLogic.java src/com/example/junkil/Phrases.java src/com/example/junkil/ArenaSim.java src/com/example/junkil/BattleView.java)
STUBS=$(find "$HERE/test/stubs" -name '*.java')
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" \
  "${SRC[@]}" $STUBS test/harness/com/example/junkil/Env.java test/harness/com/example/junkil/UiCheck.java
"$T/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.junkil.UiCheck
