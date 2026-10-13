# Release

APK-файлы приложений репозитория. Все подписаны схемами v1 и v2 (нужно для Android 11+ / targetSdk > 30).

| Файл | Приложение | Версия | Android |
|---|---|---|---|
| `CalcSolver-v1.1.apk` | Калькулятор: обычный и научный режимы, решение уравнений и систем (любое число уравнений, несовместные и с бесконечным множеством решений), график с перетаскиванием и масштабом, кнопка переноса строки «↵» | 1.1 | 12 (API 31)+ |
| `AirHotkeyChallenge-v1.0.apk` | Игра «Air Hotkey Challenge»: лови подсказанные клавиши по порядку | 1.0 | 5.0 (API 21)+ |
| `AirHockey-v1.0.apk` | Air Hockey Challenge: стол, шайба, матч до 7 голов. Режимы: против ИИ, 2 игрока, бот против бота (у каждого бота свой уровень). Четыре уровня ИИ, магазин цветов, пауза, статистика и достижения, новые звуки | 1.0 | 5.0 (API 21)+ |
| `Spamton-v1.0.apk` | Спамтон: калькулятор с рекламой. Спамтон врёт, пока его не победишь в бою на арифметических вопросах; потом считает точно. 14 эмоций (портрет меняется по событиям), весь текст на русском, звуки, захват телефона и бой | 1.0 | 5.0 (API 21)+ |
| `Hello-v1.0.apk` | Учебное приложение «Hello» | 1.0 | — |

## SHA-256

```
1f5b9a1b22edd28b7c3e074f70ba29bf7665eb6413d3c8f771384f3259551bc8  AirHockey-v1.0.apk
1f5b9a1b22edd28b7c3e074f70ba29bf7665eb6413d3c8f771384f3259551bc8  AirHotkeyChallenge-v1.0.apk
1f5b9a1b22edd28b7c3e074f70ba29bf7665eb6413d3c8f771384f3259551bc8  CalcSolver-v1.1.apk
1f5b9a1b22edd28b7c3e074f70ba29bf7665eb6413d3c8f771384f3259551bc8  Hello-v1.0.apk
1f5b9a1b22edd28b7c3e074f70ba29bf7665eb6413d3c8f771384f3259551bc8  Spamton-v1.0.apk
```

Проверка: `sha256sum -c` в этой папке (или `sha256sum *.apk`, сравнить с таблицей).

## Установка

Разрешите установку из неизвестных источников для браузера или файлового менеджера, затем откройте APK.

## Как собрано

- Исходники: `calc-app/`, `air-hotkey-challenge/`, `hello-apk/`, `spamton-calc/` (сборка: `tools/build-app.sh spamton-calc Spamton-v1.0.apk`; проверки: `spamton-calc/scripts/test.sh`, `spamton-calc/scripts/ui-test.sh`). Сборщики: `tools/build-app.sh`, `calc-app/scripts/build-calc.sh`, `hello-apk/scripts/build-apk.sh`.
- Проверки: `calc-app/scripts/test-calc.sh`, `calc-app/scripts/test-math.sh`, `air-hotkey-challenge/scripts/test.sh` (симуляция игры без устройства).
