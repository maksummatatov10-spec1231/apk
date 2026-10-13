#!/usr/bin/env bash
# Проверки экрана без устройства: раскладка, касания, реклама, захват, бой, союзник.
# Android-заглушки из test/stubs (Canvas/View на AWT), код экрана — тот же, что в APK.
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
  $SRC $STUBS "$HARN/Env.java" "$HARN/FlowCheck.java" "$HARN/ScreenShots.java"
"$T/bin/java" -Dstdout.encoding=UTF-8 -cp "$OUT/classes" com.example.spamton.FlowCheck
