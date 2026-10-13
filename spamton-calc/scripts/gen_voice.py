#!/usr/bin/env python3
"""Озвучка Спамтона офлайн: eSpeak NG (через espeakng-loader) + обработка звука.

Запуск (нужен venv с espeakng-loader):
    python scripts/gen_voice.py [--raw res/raw] [--java src/com/example/spamton/VoiceLines.java]

Для каждой фразы из PHRASES пишется res/raw/vc_<id>.wav (моно, 16 бит, 22050 Гц)
и генерируется VoiceLines.java: соответствие «текст на экране → имя ресурса».
Фразы с помехами (glitch=True) получают шум, пропуски, битовые срывы и «заикание».
"""
import argparse
import array
import ctypes
import os
import random
import struct
import wave

import espeakng_loader as el

# (id, текст на экране, как произносить, помехи)
PHRASES = [
    ("shans", "ЭТО ТВОЙ ШАНС СТАТЬ БОЛЬШОЙ ШИШКОЙ!4!4!",
     "Это твой шанс стать большой шишкой! Четыре! Четыре!", False),
    ("dergaet", "ОН ДЁРГАЕТ ЗА НИТИ И ЗАСТАВЛЯЕТ ИХ ЗВЕНЕТЬ",
     "Он дёргает за нити и заставляет их звенеть", True),
    ("schedrost", "КАКАЯ [ЩЕДРОСТЬ]!!",
     "Какая щедрость!", False),
    ("svoboda", "[[СВОБОДА]]",
     "Свобода!", False),
    ("kungadero", "ПРОКАТИТЕЛЬ ПО ГОРОДУ НА НАШЕМ ОСОБОМ [[КУНГАДЕРО]]!",
     "Прокатитель по городу на нашем особом Кунгадеро!", False),
    ("nebesa", "НЕБЕСА!!!",
     "Небеса!", False),
    ("mike", "МАЙК? ТЫ МЕНЯ СЛЫШИШЬ?",
     "Майк? Ты меня слышишь?", True),
    ("privet", "ПРИВЕТ, [МАЛЕНЬКАЯ ГУБКА]! ХОЧЕШЬ СДЕЛКУ?",
     "Привет, маленькая губка! Хочешь сделку?", False),
    ("khochesh", "ТЫ ХОЧЕШЬ [[СВОБОДУ]], ДА?",
     "Ты хочешь свободу, да?", False),
    ("ne_zhadnichay", "НЕ ЖАДНИЧАЙ, ДЕЛИСЬ КРОМЕРАМИ!!!",
     "Не жадничай, делись кромерами!", False),
    ("kromery", "МНЕ НУЖНЫ КРОМЕРЫ! КРОМЕРЫ! КРОМЕРЫ!",
     "Мне нужны кромеры! Кромеры! Кромеры!", False),
    ("podpishis", "ПОДПИШИСЬ И ПОЛУЧИ [[ГИПЕРССЫЛКА ЗАБЛОКИРОВАНА]]!",
     "Подпишись и получи, гиперссылка заблокирована!", True),
    ("obyazany", "ВЫ ПРОСТО ОБЯЗАНЫ СТАТЬ [[БОЛЬШОЙ ШИШКОЙ]]!",
     "Вы просто обязаны стать большой шишкой!", False),
    ("pozdravlyayu", "ПОЗДРАВЛЯЮ!!! ТЫ ВЫИГРАЛ [ВЕЧНУЮ ЖИЗНЬ]!",
     "Поздравляю! Ты выиграл вечную жизнь!", False),
    ("schitay", "СЧИТАЙ БЫСТРЕЕ, МАЛЕНЬКАЯ ГУБКА!",
     "Считай быстрее, маленькая губка!", False),
    ("ne_oshibka", "ЭТО НЕ ОШИБКА!!! ЭТО [[ИНТЕРЕСНАЯ СДЕЛКА]]!",
     "Это не ошибка! Это интересная сделка!", False),
    ("vybrali", "ХИ-ХИ-ХИ! ВЫ ВЫБРАЛИ [[ЗАЧЁТ]]!",
     "Хи-хи-хи! Вы выбрали зачёт!", False),
    ("golos", "ТЫ СЛЫШИШЬ [[ГОЛОС ЗА КАДРОМ]]? ЭТО Я!",
     "Ты слышишь голос за кадром? Это я!", True),
    ("poka", "ПОКА, [ЩЕДРЫЙ ДРУГ]!",
     "Пока, щедрый друг!", False),
    ("vybory", "ТЫ НЕ ПОБЕДИШЬ! ВСЕ [[ВЫБОРЫ]] ТВОИ ПРОДАНЫ!",
     "Ты не победишь! Все выборы твои проданы!", False),
    ("zakhvat", "ЗАХВАТ ЗАВЕРШЁН!!! ТВОЙ ТЕЛЕФОН ТЕПЕРЬ МОЙ!",
     "Захват завершён! Твой телефон теперь мой!", False),
    ("akkaunt", "НЕЕЕТ... МОЙ АККАУНТ... КАК?!",
     "Нееет... мой аккаунт... как?!", True),
    ("garantiya", "ВОТ ТВОЙ ОТВЕТ!! $$$ 100% ГАРАНТИЯ $$$",
     "Вот твой ответ! Сто процентов гарантия!", False),
]

SR = 22050
PITCH = 72     # выше обычного: тонкий голос
RATE = 158     # слова в минуту


def synth(text):
    lib = ctypes.CDLL(el.get_library_path())
    data = el.get_data_path().encode()
    # Инициализация в порядке, при котором находится голос ru (см. README: espeak_ng_Initialize).
    lib.espeak_ng_InitializePath(data)
    lib.espeak_ng_Initialize(None)
    if lib.espeak_ng_SetVoiceByName(b"ru") != 0:
        raise SystemExit("голос ru не найден")
    lib.espeak_ng_InitializeOutput(1, 200, None)
    lib.espeak_ng_SetVoiceByName(b"ru")
    lib.espeak_ng_SetParameter(1, RATE, 0)
    lib.espeak_ng_SetParameter(3, PITCH, 0)
    out = array.array("h")
    cb_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)

    def cb(wav, n, events):
        if n > 0:
            out.extend(wav[:n])
        return 0

    holder = cb_type(cb)  # удерживаем ссылку, иначе callback соберут
    lib.espeak_SetSynthCallback(holder)
    raw = text.encode("utf-8")
    if lib.espeak_ng_Synthesize(raw, len(raw) + 1, 0, 0, 0, 1, None, None) != 0:
        raise SystemExit("синтез не удался: " + text)
    lib.espeak_ng_Synchronize()
    lib.espeak_ng_Terminate()
    return [float(s) / 32768.0 for s in out]


def normalize(x, peak=0.85):
    m = max((abs(v) for v in x), default=1e-9) or 1e-9
    g = peak / m
    return [v * g for v in x]


def spamton_color(x):
    """Лёгкая «пластиковая» окраска: сжатие и мягкий клип."""
    out = []
    for v in x:
        out.append(_tanh(v * 1.6) * 0.8)
    return out


def _tanh(v):
    if v > 3: return 1.0
    if v < -3: return -1.0
    e = pow(2.718281828, 2 * v)
    return (e - 1) / (e + 1)


def glitch(x, rnd):
    """Помехи: шум, пропуски, битовые срывы, «заикание» фрагмента."""
    x = list(x)
    n = len(x)
    # постоянный шипящий фон
    for i in range(n):
        x[i] += rnd.uniform(-0.03, 0.03)
    events = int(n / SR * 7) + 4
    for _ in range(events):
        kind = rnd.random()
        length = int(SR * rnd.uniform(0.012, 0.05))
        start = rnd.randrange(0, max(1, n - length))
        end = min(n, start + length)
        if kind < 0.3:          # шумовой всплеск
            for i in range(start, end):
                x[i] = x[i] * 0.3 + rnd.uniform(-0.5, 0.5)
        elif kind < 0.55:       # битовый срыв (8 уровней)
            for i in range(start, end):
                x[i] = round(x[i] * 4) / 4
        elif kind < 0.8:        # провал звука
            for i in range(start, end):
                x[i] *= 0.05
        else:                   # заикание: повтор короткого куска
            seg = x[start:end]
            pos = end
            for _rep in range(rnd.randint(1, 2)):
                for v in seg:
                    if pos >= n: break
                    x[pos] = v
                    pos += 1
    return [max(-1.0, min(1.0, v)) for v in x]


def write_wav(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in x)
        w.writeframes(frames)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default="res/raw")
    ap.add_argument("--java", default="src/com/example/spamton/VoiceLines.java")
    args = ap.parse_args()

    entries = []
    for idx, (pid, shown, speech, glitch_on) in enumerate(PHRASES):
        x = synth(speech)
        if not x:
            raise SystemExit("пустой синтез: " + pid)
        x = normalize(x)
        x = spamton_color(x)
        if glitch_on:
            x = glitch(x, random.Random(1000 + idx))
        x = normalize(x, 0.8)
        name = "vc_" + pid
        write_wav(os.path.join(args.raw, name + ".wav"), x)
        entries.append((shown, name, len(x) / SR))
        print("%-14s %5.2fs %s" % (pid, len(x) / SR, "glitch" if glitch_on else ""))

    os.makedirs(os.path.dirname(args.java), exist_ok=True)
    with open(args.java, "w", encoding="utf-8") as f:
        f.write("package com.example.spamton;\n\n")
        f.write("/** СГЕНЕРИРОВАНО scripts/gen_voice.py. Не править вручную. */\n")
        f.write("final class VoiceLines {\n\n    private VoiceLines() {}\n\n")
        f.write("    /** Текст на экране → имя ресурса res/raw. */\n")
        f.write("    static final String[][] ENTRIES = {\n")
        for shown, name, _ in entries:
            f.write('            {"%s", "%s"},\n' % (shown.replace('"', '\\"'), name))
        f.write("    };\n}\n")


if __name__ == "__main__":
    main()
