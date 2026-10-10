#!/usr/bin/env bash
# Устанавливает тулчейн для сборки Android APK в песочнице без Google SDK/Gradle.
# Всё берётся из PyPI, npm и GitHub (git clone), т.к. dl.google.com и Maven недоступны.
#
#   java      — JRE 25 (PyPI: jdk4py)
#   javac     — javac 8 из tools.jar OpenJDK 8 (npm: dataslope-tools-jar), работает на JRE 25
#   d8        — D8 из d8-termux (npm), переведён enjarify (GitHub) и исправлен под JVM
#   aapt2     — Linux-бинарник (PyPI: aapt2)
#   apktool   — apktool 2.4.1 со smali (npm: apktool-jar)
#   android.jar — API 31 и 34 (GitHub: Sable/android-platforms)
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
T="${ANDROID_TOOLCHAIN:-$HOME/android-toolchain}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$T/bin" "$T/platforms/android-31" "$T/platforms/android-34" "$T/d8" "$T/javac"

echo "==> JRE (jdk4py)"
( cd "$TMP" && pip3 download --no-deps -q -d . jdk4py )
unzip -q -o "$TMP"/jdk4py-*.whl -d "$TMP/jdk4py"
rm -rf "$T/jre" && cp -r "$TMP/jdk4py/jdk4py/java-runtime" "$T/jre" && chmod +x "$T/jre/bin/"*
ln -sf "$T/jre/bin/java" "$T/bin/java"

echo "==> android.jar API 31/34 (GitHub: Sable/android-platforms)"
git clone -q --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms.git "$TMP/ap"
git -C "$TMP/ap" sparse-checkout set android-31 android-34 >/dev/null
cp "$TMP/ap/android-31/android.jar" "$T/platforms/android-31/android.jar"
cp "$TMP/ap/android-34/android.jar" "$T/platforms/android-34/android.jar"

echo "==> javac 8 (npm: dataslope-tools-jar)"
( cd "$TMP" && npm pack dataslope-tools-jar@1.0.0 -q >/dev/null && tar xzf dataslope-tools-jar-1.0.0.tgz )
cp "$TMP/package/tools.jar" "$T/javac/tools.jar"
cat > "$T/bin/javac" <<EOS
#!/usr/bin/env bash
exec "$T/bin/java" -cp "$T/javac/tools.jar" com.sun.tools.javac.Main "\$@"
EOS
chmod +x "$T/bin/javac"

echo "==> D8 (npm: d8-termux + enjarify + исправления)"
( cd "$TMP" && npm pack d8-termux@1.0.0 -q >/dev/null && tar xzf d8-termux-1.0.0.tgz )
mkdir -p "$TMP/d8dex" && unzip -q -o "$TMP/package/d8.jar" -d "$TMP/d8dex"
git clone -q --depth 1 https://github.com/Storyyeller/enjarify.git "$TMP/enjarify"
for dex in "$TMP"/d8dex/classes*.dex; do
  name="$(basename "$dex" .dex)"
  PYTHONPATH="$TMP/enjarify" python3 -m enjarify.main -f -o "$TMP/$name.jar" "$dex" >/dev/null
done
mkdir -p "$TMP/lister"
"$T/bin/javac" -source 8 -target 8 -bootclasspath "$T/platforms/android-34/android.jar" \
  -d "$TMP/lister" "$TOOLS_DIR/ListIfaces.java"
echo "    (список интерфейсов JDK)"
"$T/bin/java" -cp "$TMP/lister" ListIfaces > "$TMP/jdk_ifaces.txt"
JDK_IFACES="$TMP/jdk_ifaces.txt" python3 "$TOOLS_DIR/fix_jvm_d8.py" "$T/d8/d8-jvm.jar" \
  "$TMP/classes.jar" "$TMP/classes2.jar" "$TMP/classes3.jar"
cat > "$T/bin/d8" <<EOS
#!/usr/bin/env bash
exec "$T/bin/java" -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -Xmx2g \\
  -cp "$T/d8/d8-jvm.jar" com.android.tools.r8.D8 "\$@"
EOS
chmod +x "$T/bin/d8"

echo "==> aapt2 (PyPI: aapt2)"
( cd "$TMP" && pip3 download --no-deps -q -d . aapt2 )
unzip -q -o "$TMP"/aapt2-*.whl 'aapt2/bin/Linux/aapt2' -d "$TMP/aapt2"
cp "$TMP/aapt2/aapt2/bin/Linux/aapt2" "$T/bin/aapt2" && chmod +x "$T/bin/aapt2"

echo "==> apktool 2.4.1 (npm: apktool-jar)"
( cd "$TMP" && npm pack apktool-jar@2.4.1 -q >/dev/null && tar xzf apktool-jar-2.4.1.tgz )
cp "$TMP/package/bin/apktool_2.4.1.jar" "$T/apktool.jar"

echo "==> Проверка"
"$T/bin/java" -version 2>&1 | head -1
"$T/bin/aapt2" version
"$T/bin/java" -jar "$T/apktool.jar" --version
"$T/bin/javac" -version
"$T/bin/d8" --version
echo "Готово: $T"
