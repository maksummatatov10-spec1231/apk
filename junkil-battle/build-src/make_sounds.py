"""Генерация звуков Джанклима (res/raw/*.wav): блипы, удары, морзе-тоны для слов. Без внешних ассетов."""
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
    return np.linspace(0, sec, int(RATE * sec), endpoint=False)


def env(n, attack=0.005, release=0.03):
    e = np.ones(n)
    a = min(n, int(RATE * attack))
    r = min(n, int(RATE * release))
    e[:a] = np.linspace(0, 1, a)
    e[n - r:] *= np.linspace(1, 0, r)
    return e


def square(freq, sec, duty=0.5):
    x = t(sec)
    return np.where((freq * x) % 1.0 < duty, 1.0, -1.0) * env(len(x))


def sweep(f0, f1, sec, wave="square"):
    x = t(sec)
    f = np.linspace(f0, f1, len(x))
    ph = 2 * np.pi * np.cumsum(f) / RATE
    y = np.sign(np.sin(ph)) if wave == "square" else np.sin(ph)
    return y * env(len(x))


def noise(sec, seed=1):
    rng = np.random.default_rng(seed)
    n = int(RATE * sec)
    y = rng.uniform(-1, 1, n)
    return y * np.linspace(1, 0, n) ** 2


def write(name, y, gain=0.45):
    y = np.clip(y * gain, -1, 1)
    data = (y * 32767).astype("<i2").tobytes()
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    print("wrote", path, round(len(y) / RATE, 3), "s")


def morse_tone(code_word):
    parts = []
    gap = np.zeros(int(RATE * 0.07))
    letter_gap = np.zeros(int(RATE * 0.22))
    for li, ch in enumerate(code_word):
        for si, sym in enumerate(MORSE[ch]):
            parts.append(square(660, 0.07 if sym == "." else 0.22))
            if si < len(MORSE[ch]) - 1:
                parts.append(gap)
        if li < len(code_word) - 1:
            parts.append(letter_gap)
    return np.concatenate(parts)


def main():
    os.makedirs(OUT, exist_ok=True)
    write("select", square(880, 0.035), 0.3)
    write("confirm", sweep(660, 990, 0.12), 0.35)
    write("back", sweep(520, 300, 0.09), 0.3)
    write("hurt", np.concatenate([sweep(440, 110, 0.18), noise(0.12, 3) * 0.6]), 0.5)
    write("hit", sweep(260, 90, 0.2), 0.5)
    write("heal", np.concatenate([square(523, 0.07), square(659, 0.07), square(784, 0.1)]), 0.35)
    write("whoosh", noise(0.45, 7) * np.linspace(0.2, 1, int(RATE * 0.45)), 0.4)
    write("mercy", np.concatenate([square(523, 0.12), square(659, 0.12), square(523, 0.12), square(784, 0.3)]), 0.35)
    write("win", np.concatenate([square(523, 0.12), square(659, 0.12), square(784, 0.12), square(1047, 0.35)]), 0.35)
    write("lose", np.concatenate([sweep(440, 220, 0.3), sweep(220, 110, 0.5)]), 0.4)
    for word in ["SOS", "JUNK", "FREE", "BUY", "SALE", "HELP", "YES", "NO"]:
        write("morse_" + word.lower(), morse_tone(word), 0.4)


if __name__ == "__main__":
    main()
