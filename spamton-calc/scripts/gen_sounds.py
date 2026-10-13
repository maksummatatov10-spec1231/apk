#!/usr/bin/env python3
"""Генерирует звуки Спамтона в res/raw/*.wav (моно, 16 бит, 22050 Гц). Только стандартная библиотека.

Перегенерация: python3 scripts/gen_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "res", "raw")


def tone(freq_fn, dur, wave_kind="square", vol=0.5, decay=0.0, noise=0.0, seed=1):
    """freq_fn(t) -> Гц. Возвращает список float в диапазоне [-1, 1]."""
    rnd = random.Random(seed)
    n = int(RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = freq_fn(t)
        phase += 2 * math.pi * f / RATE
        if wave_kind == "square":
            s = 1.0 if math.sin(phase) >= 0 else -1.0
        elif wave_kind == "saw":
            s = 2 * ((phase / (2 * math.pi)) % 1.0) - 1
        else:
            s = math.sin(phase)
        if noise:
            s = s * (1 - noise) + noise * (rnd.random() * 2 - 1)
        env = math.exp(-decay * t) if decay else 1.0
        # короткое нарастание и спад, чтобы не было щелчков
        edge = min(1.0, i / (RATE * 0.004), (n - i) / (RATE * 0.01))
        out.append(s * vol * env * max(0.0, edge))
    return out


def mix(*parts):
    n = max(len(p) for p in parts)
    res = [0.0] * n
    for p in parts:
        for i, v in enumerate(p):
            res[i] += v
    return res


def silence(dur):
    return [0.0] * int(RATE * dur)


def concat(*parts):
    res = []
    for p in parts:
        res.extend(p)
    return res


def soft_clip(x):
    return math.tanh(x * 1.4) / math.tanh(1.4)


def write(name, samples):
    peak = max(1e-9, max(abs(v) for v in samples))
    scale = 0.85 / peak if peak > 0.85 else 1.0
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = b"".join(struct.pack("<h", int(soft_clip(v * scale) * 32767)) for v in samples)
        w.writeframes(frames)
    print("wrote", path, round(len(samples) / RATE, 3), "s")


def main():
    os.makedirs(OUT, exist_ok=True)

    # blip — писк текста: короткий, тихий
    write("blip", tone(lambda t: 880, 0.03, "square", 0.25, decay=40))

    # click — кнопка калькулятора
    write("click", tone(lambda t: 1400 - 900 * t / 0.02 if t < 0.02 else 500, 0.05, "square", 0.45, decay=60))

    # right — верный ответ (случайный): восходящее арпеджио
    write("right", concat(
        tone(lambda t: 660, 0.07, "square", 0.4, decay=10),
        tone(lambda t: 880, 0.07, "square", 0.4, decay=10),
        tone(lambda t: 1320, 0.22, "square", 0.45, decay=8)))

    # wrong — неверный ответ: нисходящий «бдыыы»
    write("wrong", tone(lambda t: 420 - 260 * t / 0.45, 0.45, "saw", 0.5, decay=3))

    # error — ошибка ввода: короткое жужжание
    write("error", tone(lambda t: 150, 0.22, "square", 0.5, noise=0.35, decay=6, seed=3))

    # ding — реклама: звонкий «дзынь» с двумя тонами
    write("ding", mix(
        tone(lambda t: 1568, 0.5, "sine", 0.55, decay=6),
        tone(lambda t: 2093, 0.5, "sine", 0.35, decay=8)))

    # hit — удар по Спамтону: шум и низкий удар
    write("hit", mix(
        tone(lambda t: 90 - 50 * t / 0.2 if t < 0.2 else 40, 0.25, "sine", 0.9, decay=12),
        tone(lambda t: 1000, 0.18, "square", 0.25, noise=0.9, decay=18, seed=7)))

    # heal — «спам» лечит его: восходящий глиссандо
    write("heal", tone(lambda t: 400 + 700 * min(1.0, t / 0.35), 0.4, "square", 0.4, decay=3))

    # takeover — захват телефона: сирена
    write("takeover", tone(
        lambda t: 620 if int(t * 6) % 2 == 0 else 930, 1.2, "square", 0.42, decay=0.6))

    # lose — проигрыш раунда: печальный спуск
    write("lose", concat(
        tone(lambda t: 500, 0.25, "square", 0.45, decay=2),
        tone(lambda t: 420, 0.25, "square", 0.45, decay=2),
        tone(lambda t: 350 - 230 * t / 0.5, 0.5, "saw", 0.45, decay=2)))

    # win — победа: аккорд и фанфара
    chord = mix(
        tone(lambda t: 523, 0.9, "sine", 0.3, decay=2.5),
        tone(lambda t: 659, 0.9, "sine", 0.3, decay=2.5),
        tone(lambda t: 784, 0.9, "sine", 0.3, decay=2.5))
    write("win", concat(
        tone(lambda t: 523, 0.12, "square", 0.4, decay=4),
        tone(lambda t: 659, 0.12, "square", 0.4, decay=4),
        tone(lambda t: 784, 0.12, "square", 0.4, decay=4),
        chord))


if __name__ == "__main__":
    main()
