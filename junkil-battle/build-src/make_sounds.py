"""Звуки Джанклима (res/raw/*.wav): мягкие тона без квадратных волн и резкого шума.

Тембр — синус с лёгкими гармониками, атака 8–15 мс, экспоненциальное затухание,
короткий фейд на конце (без щелчков), общий тихий уровень и ограничение пиков.
Без внешних ассетов, только numpy.
"""
import os
import wave
import numpy as np

RATE = 22050
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "res", "raw")
MORSE = {"A": ".-", "B": "-...", "C": "-.-.", "D": "-..", "E": ".", "F": "..-.", "G": "--.", "H": "....",
         "I": "..", "J": ".---", "K": "-.-", "L": ".-..", "M": "--", "N": "-.", "O": "---", "P": ".--.",
         "Q": "--.-", "R": ".-.", "S": "...", "T": "-", "U": "..-", "V": "...-", "W": ".--", "X": "-..-",
         "Y": "-.--", "Z": "--.."}


def t(sec):
    return np.arange(int(RATE * sec)) / RATE


def tone(freq, sec, decay=6.0, partials=((1, 1.0), (2, 0.18), (3, 0.05)), attack=0.012):
    """Мягкий тон: сумма синусов с убывающими гармониками и экспоненциальным затуханием."""
    x = t(sec)
    y = np.zeros_like(x)
    for mult, amp in partials:
        if freq * mult < RATE / 2:
            y += amp * np.sin(2 * np.pi * freq * mult * x)
    env = np.exp(-decay * x)
    a = min(len(x), int(RATE * attack))
    env[:a] *= np.linspace(0, 1, a)
    return y * env * fade_out(len(x))


def glide(f0, f1, sec, decay=3.0, attack=0.01):
    """Плавная смена высоты (мягкий «боп»), синус без резких краёв."""
    x = t(sec)
    f = np.linspace(f0, f1, len(x))
    ph = 2 * np.pi * np.cumsum(f) / RATE
    y = np.sin(ph) + 0.15 * np.sin(2 * ph)
    env = np.exp(-decay * x)
    a = min(len(x), int(RATE * attack))
    env[:a] *= np.linspace(0, 1, a)
    return y * env * fade_out(len(x))


def fade_out(n, ms=12):
    f = np.ones(n)
    r = min(n, int(RATE * ms / 1000))
    f[n - r:] = np.linspace(1, 0, r)
    return f


def soft_noise(sec, cutoff=900.0, seed=7):
    """Шум, сглаженный однополюсным фильтром: звучит как ветер, а не как помехи."""
    rng = np.random.default_rng(seed)
    n = int(RATE * sec)
    x = rng.standard_normal(n)
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.zeros(n)
    acc = 0.0
    for i in range(n):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    y /= np.max(np.abs(y)) + 1e-9
    return y * np.sin(np.pi * np.arange(n) / n) ** 2


def write(name, y, peak=0.32):
    """Нормировка пика (тихо и без клипа), запись 16-бит моно."""
    m = np.max(np.abs(y)) + 1e-9
    y = y / m * peak
    data = (np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print("wrote", path, round(len(y) / RATE, 3), "s, peak", round(float(np.max(np.abs(y))), 3))


def gap(sec):
    return np.zeros(int(RATE * sec))


def morse_tone(word):
    """Точки и тире как короткие мягкие ноты (одна высота, тире длиннее)."""
    parts = []
    for li, ch in enumerate(word):
        for si, sym in enumerate(MORSE[ch]):
            parts.append(tone(640, 0.08 if sym == "." else 0.22, decay=10.0, partials=((1, 1.0), (2, 0.1))))
            if si < len(MORSE[ch]) - 1:
                parts.append(gap(0.06))
        if li < len(word) - 1:
            parts.append(gap(0.18))
    return np.concatenate(parts)


def main():
    os.makedirs(OUT, exist_ok=True)
    # выбор и подтверждение: короткие светлые ноты
    write("select", tone(784, 0.07, decay=18))
    write("confirm", np.concatenate([tone(659, 0.09, decay=12), gap(0.02), tone(988, 0.14, decay=10)]))
    write("back", glide(500, 380, 0.12, decay=10))
    # удар/урон: мягкий низкий «тунк» с падением высоты, без шума
    write("hit", glide(220, 130, 0.2, decay=9))
    write("hurt", glide(330, 180, 0.24, decay=8))
    # лечение: восходящая арпеджио-ми
    write("heal", np.concatenate([tone(523, 0.1, decay=9), tone(659, 0.1, decay=9), tone(784, 0.2, decay=7)]))
    # свист атаки: тихий сглаженный шум
    write("whoosh", soft_noise(0.4, cutoff=1400), peak=0.22)
    # милость: тёплое трезвучие
    write("mercy", np.concatenate([tone(523, 0.16, decay=5), tone(659, 0.16, decay=5),
                                   tone(784, 0.16, decay=5), tone(1047, 0.45, decay=3.5)]))
    # победа и поражение
    write("win", np.concatenate([tone(523, 0.12, decay=8), tone(659, 0.12, decay=8),
                                 tone(784, 0.12, decay=8), tone(1047, 0.4, decay=4)]))
    write("lose", np.concatenate([glide(392, 300, 0.3, decay=4), glide(294, 196, 0.5, decay=3)]))
    for word in ["SOS", "JUNK", "FREE", "BUY", "SALE", "HELP", "YES", "NO"]:
        write("morse_" + word.lower(), morse_tone(word), peak=0.3)


if __name__ == "__main__":
    main()
