#!/usr/bin/env bash
# Снимки экранов в docs/screens/ (PNG) из того же кода экрана, что в APK.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/ui"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
mkdir -p docs/screens
SRC=(src/com/example/junkil/MorseCode.java src/com/example/junkil/BattleLogic.java src/com/example/junkil/Phrases.java src/com/example/junkil/ArenaSim.java src/com/example/junkil/BattleView.java src/com/example/junkil/Sounds.java)
STUBS=$(find "$HERE/test/stubs" -name '*.java')
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" \
  "${SRC[@]}" $STUBS test/harness/com/example/junkil/Env.java test/harness/com/example/junkil/ScreenShots.java test/harness/com/example/junkil/RecSounds.java
"$T/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.junkil.ScreenShots docs/screens
RENDER_PY="${RENDER_PY:-$HOME/.venv-render/bin/python}"
"$RENDER_PY" "$HERE/scripts/render_ops.py" docs/screens
