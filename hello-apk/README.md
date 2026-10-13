# hello-apk — Android APK, собранный в песочнице

Минимальное Android-приложение: одна Activity, которая показывает текст «Привет из APK!».
Собирается **без Android Studio, Gradle и Google SDK** — только Java-runtime, aapt2, apktool и android.jar.

## Структура

```
app/
  AndroidManifest.xml          манифест (minSdk 21, targetSdk 34)
  apktool.yml                  конфиг сборки apktool
  smali/.../MainActivity.smali исходный код Activity (smali — язык dex-байткода)
scripts/
  setup-toolchain.sh           установка тулчейна в ~/android-toolchain
  build-apk.sh                 сборка + подпись APK -> build/hello.apk
  sign_apk.py                  подпись схемами v1 (JAR) и v2 (APK Signature Scheme v2)
  verify_apk.py                (в ../tools) проверка подписи v1 и v2
```

## Быстрый старт

```bash
../tools/setup-toolchain.sh   # один раз: скачивает инструменты
./scripts/build-apk.sh         # собирает build/hello.apk
```

Установка на устройство: скопировать `build/hello.apk` на телефон и открыть
(потребуется разрешить установку из неизвестных источников).

## Ограничения окружения

- В песочнице нет `javac` (JDK без компилятора): Java-код не компилируется напрямую,
  поэтому логика написана в smali. Для Java/Kotlin нужен javac/kotlinc или ECJ.
- Недоступны `dl.google.com`, `maven.google.com`, `services.gradle.org`,
  поэтому Android SDK и Gradle не ставятся; используется `android.jar` из GitHub-репозитория
  `Sable/android-platforms`.
- Подпись — debug-ключ (создаётся в `~/android-toolchain/debug-key`, в репозиторий не попадает),
  схемы v1 и v2 (нужна Android 11+ при targetSdk ≥ 30). Для публикации в Google Play нужен свой ключ.
- Эмулятор и реальное устройство в песочнице недоступны, запуск не проверялся на Android-устройстве.
  Проверены: структура APK, манифест (`aapt2 dump badging`), хеши и CMS-подпись,
  декомпиляция dex обратно в smali.
