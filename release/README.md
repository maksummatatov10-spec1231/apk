# Release

APK-файлы приложений репозитория. Все подписаны схемами v1 и v2 (нужно для Android 11+ / targetSdk > 30).

| Файл | Приложение | Версия | Android |
|---|---|---|---|
| `CalcSolver-v1.1.apk` | Калькулятор: обычный и научный режимы, решение уравнений и систем (любое число уравнений, несовместные и с бесконечным множеством решений), график с перетаскиванием и масштабом, кнопка переноса строки «↵» | 1.1 | 12 (API 31)+ |
| `AirHotkeyChallenge-v1.0.apk` | Игра «Air Hotkey Challenge»: лови подсказанные клавиши по порядку | 1.0 | 5.0 (API 21)+ |
| `Hello-v1.0.apk` | Учебное приложение «Hello» | 1.0 | — |

## SHA-256

```
66a9342dfee7fc25311be50cce97d92f36d101ba61203b96b7ac67712484b59a  AirHotkeyChallenge-v1.0.apk
9b6b0a3df04ef33157e2815d8dc5ebef5ff1168cf2aebe600f460ec95825693b  CalcSolver-v1.1.apk
337a533a4ef98d4126a08b2a9488098923c54c48351150c76b0dbc0e8a92207d  Hello-v1.0.apk
```

Проверка: `sha256sum -c` в этой папке (или `sha256sum *.apk`, сравнить с таблицей).

## Установка

Разрешите установку из неизвестных источников для браузера или файлового менеджера, затем откройте APK.

## Как собрано

- Исходники: `calc-app/`, `air-hotkey-challenge/`, `hello-apk/`. Сборщики: `tools/build-app.sh`, `calc-app/scripts/build-calc.sh`, `hello-apk/scripts/build-apk.sh`.
- Проверки: `calc-app/scripts/test-calc.sh`, `calc-app/scripts/test-math.sh`, `air-hotkey-challenge/scripts/test.sh` (симуляция игры без устройства).
