#!/usr/bin/env bash
# Сборка консольной обёртки над решателем (без Android): build/cli/classes
# Запуск: java -cp build/cli/classes com.example.calcpro.SolverCli < запросы.txt
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
OUT="$HERE/build/cli/classes"
rm -rf "$OUT" && mkdir -p "$OUT"
"$T/bin/javac" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$T/platforms/android-31/android.jar" -d "$OUT" \
  "$HERE"/src/com/example/calcpro/math/*.java \
  "$HERE"/src/com/example/calcpro/Catalog.java \
  "$HERE"/test/com/example/calcpro/SolverCli.java
echo "CLI собран: $OUT"
