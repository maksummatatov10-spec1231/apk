#!/usr/bin/env python3
"""Синтез звуков аэрохоккея (WAV 44.1 кГц, 16 бит, моно) в res/raw.

Звуки собраны из синусоид со скольжением частоты, шумовых вспышек и огибающих.
Громкость: сигнал прижимается мягким ограничением (tanh) и нормируется к 0.97 — звуки «плотнее» и громче.
Запуск: python3 scripts/gen_sounds.py
"""
import math, os, random, struct, wave

RATE = 44100
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "res", "raw")
random.seed(7)


def env_exp(t, k):
    return math.exp(-k * t)


def sweep(f0, f1, dur, wave_fn=math.sin, decay=6.0, amp=1.0):
    n = int(RATE * dur)
    out, ph = [], 0.0
    for i in range(n):
        t = i / RATE
        f = f0 + (f1 - f0) * (t / dur)
        ph += 2 * math.pi * f / RATE
        out.append(amp * wave_fn(ph) * env_exp(t, decay))
    return out


def square(ph):
    return 1.0 if math.sin(ph) >= 0 else -1.0


def noise_burst(dur, decay, amp=1.0):
    n = int(RATE * dur)
    return [amp * (random.random() * 2 - 1) * env_exp(i / RATE, decay) for i in range(n)]


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def at(offset, track):
    return [0.0] * int(RATE * offset) + track


def chord(freqs, dur, decay, fn=math.sin, amp=0.4):
    n = int(RATE * dur)
    out = [0.0] * n
    for f in freqs:
        for i in range(n):
            t = i / RATE
            out[i] += amp * fn(2 * math.pi * f * t) * env_exp(t, decay)
    return out


def finish(samples, peak=0.97):
    drive = 2.2
    shaped = [math.tanh(drive * s) for s in samples]
    m = max(1e-9, max(abs(s) for s in shaped))
    return [s / m * peak for s in shaped]


def save(name, samples):
    samples = finish(samples)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples)
        w.writeframes(frames)
    print(f"{name}.wav  {len(samples) / RATE:.2f} с")


def main():
    os.makedirs(OUT, exist_ok=True)
    # удар ракетки: глухой низкий удар + щелчок
    save("snd_hit", mix(sweep(260, 70, 0.22, decay=9.0, amp=1.0),
                        noise_burst(0.02, 180, amp=0.7),
                        sweep(900, 500, 0.05, square, decay=60, amp=0.25)))
    # отскок от борта: короткий резкий щелчок
    save("snd_wall", mix(sweep(1100, 420, 0.12, square, decay=28, amp=0.45),
                         noise_burst(0.03, 120, amp=0.5)))
    # гол: восходящий аккорд и низкий удар
    save("snd_goal", mix(chord([261.6, 329.6, 392.0, 523.3], 0.9, 3.2, math.sin, 0.35),
                         sweep(140, 35, 0.8, decay=3.0, amp=1.0),
                         noise_burst(0.12, 30, amp=0.4)))
    # подача: шуршание со свистом вверх
    save("snd_serve", mix(sweep(380, 1400, 0.35, decay=4.0, amp=0.35),
                          [v * 0.6 for v in noise_burst(0.35, 5.0, amp=0.5)]))
    # клик интерфейса
    save("snd_click", sweep(1300, 1000, 0.06, decay=50, amp=0.8))
    # победа: арпеджио и аккорд
    arp = mix(at(0.00, chord([523.3], 0.25, 9, math.sin, 0.6)),
              at(0.12, chord([659.3], 0.25, 9, math.sin, 0.6)),
              at(0.24, chord([784.0], 0.25, 9, math.sin, 0.6)),
              at(0.36, chord([1046.5, 1318.5, 1568.0], 0.9, 3.5, math.sin, 0.35)))
    save("snd_win", arp)
    # поражение: спадающий тон
    save("snd_lose", mix(sweep(330, 110, 0.9, square, decay=2.8, amp=0.45),
                         sweep(330 * 0.5, 80, 0.9, math.sin, decay=2.5, amp=0.7)))


if __name__ == "__main__":
    main()
