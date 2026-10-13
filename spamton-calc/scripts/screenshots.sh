#!/usr/bin/env bash
# Снимки экранов в docs/screens/ (PNG) из того же кода экрана, что в APK.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/ui"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
SRC=$(find "$HERE/src/com/example/spamton" -name '*.java' ! -name 'MainActivity.java' ! -name 'AndroidSounds.java')
STUBS=$(find "$HERE/test/stubs" -name '*.java')
HARN="$HERE/test/harness/com/example/spamton"
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" \
  $SRC $STUBS "$HARN/Env.java" "$HARN/ScreenShots.java"
"$T/bin/java" -cp "$OUT/classes" com.example.spamton.ScreenShots "$HERE/docs/screens"
RENDER_PY="${RENDER_PY:-$HOME/.venv-render/bin/python}"
"$RENDER_PY" "$HERE/scripts/render_ops.py" "$HERE/docs/screens"
