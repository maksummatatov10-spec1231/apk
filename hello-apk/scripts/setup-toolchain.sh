#!/usr/bin/env bash
# Устанавливает набор инструментов для сборки APK в ~/android-toolchain.
# Источники: только то, что доступно из песочницы (PyPI, npm, GitHub git).
#   - Java runtime (JRE 25)   <- PyPI: jdk4py  (есть java, нет javac)
#   - aapt2 (Linux)           <- PyPI: aapt2
#   - apktool 2.4.1 + smali   <- npm: apktool-jar
#   - android.jar (API 34)    <- GitHub: Sable/android-platforms (sparse clone)
set -euo pipefail

T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$T/bin" "$T/platforms/android-34"

echo "==> JRE (jdk4py)"
( cd "$TMP" && pip3 download --no-deps -q -d . jdk4py )
unzip -q -o "$TMP"/jdk4py-*.whl -d "$TMP/jdk4py"
rm -rf "$T/jre"
cp -r "$TMP/jdk4py/jdk4py/java-runtime" "$T/jre"
chmod +x "$T/jre/bin/"*
ln -sf "$T/jre/bin/java" "$T/bin/java"

echo "==> aapt2 (PyPI: aapt2)"
( cd "$TMP" && pip3 download --no-deps -q -d . aapt2 )
unzip -q -o "$TMP"/aapt2-*.whl 'aapt2/bin/Linux/aapt2' -d "$TMP/aapt2"
cp "$TMP/aapt2/aapt2/bin/Linux/aapt2" "$T/bin/aapt2"
chmod +x "$T/bin/aapt2"

echo "==> apktool 2.4.1 (npm: apktool-jar)"
( cd "$TMP" && npm pack apktool-jar@2.4.1 -q >/dev/null && tar xzf apktool-jar-2.4.1.tgz )
cp "$TMP/package/bin/apktool_2.4.1.jar" "$T/apktool.jar"

echo "==> android.jar API 34 (GitHub: Sable/android-platforms)"
git clone -q --depth 1 --filter=blob:none --sparse \
  https://github.com/Sable/android-platforms.git "$TMP/ap"
git -C "$TMP/ap" sparse-checkout set android-34 >/dev/null
cp "$TMP/ap/android-34/android.jar" "$T/platforms/android-34/android.jar"

echo "==> Проверка"
"$T/bin/java" -version 2>&1 | head -1
"$T/bin/aapt2" version
"$T/bin/java" -jar "$T/apktool.jar" --version
echo "Готово: $T"
