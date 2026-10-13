#!/usr/bin/env bash
# Тесты логики без Android: калькулятор, Спамтон, бой, пули (JVM).
# Требует тулчейн из tools/setup-toolchain.sh.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/test"
rm -rf "$OUT" && mkdir -p "$OUT/classes"
PURE=(Calc Spamton Ads BulletField Battle)
SRC=()
for c in "${PURE[@]}"; do SRC+=("$HERE/src/com/example/spamton/$c.java"); done
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT/classes" "${SRC[@]}" \
  "$HERE/test/com/example/spamton/LogicCheck.java"
"$T/bin/java" -cp "$OUT/classes" com.example.spamton.LogicCheck
