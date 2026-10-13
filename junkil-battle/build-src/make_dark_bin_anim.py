#!/usr/bin/env python3
"""Покадровая анимация «Тёмного бака» для объяснения: сердце — точка, границы арены — _| .

Тайминги совпадают с ArenaSim: крышка закрывается 0–0.5 с, подсказки (пунктир) с 1.2 с,
каждый выстрел через 1.5 с после своей подсказки, два залпа по три выстрела, открытие крышки к 7.3 с.
Вывод: docs/anim/dark-bin.mp4 (ffmpeg из imageio-ffmpeg, кадры во временной папке).

Запуск:
    /home/user/.venv-render/bin/python build-src/make_dark_bin_anim.py
"""
import os
import shutil
import tempfile

import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "docs", "anim", "dark-bin.mp4")

W, H = 960, 540
FPS = 30
DURATION = 9.0

ARENA = (70, 80, 430, 440)          # квадрат арены (_| по краям)
CX, CY = (ARENA[0] + ARENA[2]) / 2, (ARENA[1] + ARENA[3]) / 2
HALF = (ARENA[2] - ARENA[0]) / 2  # noqa: E501 (не используется отдельно)

BG = (12, 12, 18)
WHITE = (240, 240, 240)
GREY = (120, 120, 130)
YELLOW = (255, 230, 0)
RED = (235, 40, 50)
LID = (22, 22, 30)
GLOW = [(30, 30, 42), (44, 44, 62), (58, 58, 80)]

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def lid_closed(t):
    """Крышка: сходится за 0.5 с, открывается с 6.9 до 7.3 с."""
    close = min(1.0, t / 0.5)
    left = 7.3 - t
    opening = min(1.0, left / 0.4) if t > 0 else 0.0
    return max(0.0, min(close, opening)) if 0.0 < t < 7.3 else 0.0


def glints(t):
    """Подсказки: (волна, индекс, время конца) — по три за залп, ведут к сердцу в центре."""
    out = []
    for wave_t in (1.2, 4.2):
        for i in range(3):
            tg = wave_t + i * 0.25
            if tg <= t < tg + 1.5:
                out.append((wave_t, i, tg + 1.5))
    return out


def shots(t):
    """Выстрелы: (индекс, доля пути 0..1) — летят от края к точке, где было сердце."""
    out = []
    for wave_t in (1.2, 4.2):
        for i in range(3):
            fire = wave_t + i * 0.25 + 1.5
            if fire <= t < fire + 0.6:
                out.append(((fire, i, wave_t), (t - fire) / 0.6))
    return out


def origin_for(wave_t, i):
    """Точки на краях арены для подсказок (фиксированные, как в симуляции случайно, но стабильно)."""
    seeds = [(0.05, 0.3), (0.95, 0.7), (0.4, 0.02), (0.1, 0.9), (0.75, 0.98), (0.6, 0.02)]
    k = (0 if wave_t < 2 else 3) + i
    fx, fy = seeds[k]
    return (ARENA[0] + fx * (ARENA[2] - ARENA[0]), ARENA[1] + fy * (ARENA[3] - ARENA[1]))


def dashed(d, p0, p1, color, width=2, dash=10, gap=7, on=True):
    if not on:
        return
    x0, y0 = p0
    x1, y1 = p1
    length = ((x1 - x0) ** 2 + (y1 - y0) ** 2) ** 0.5
    if length == 0:
        return
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    pos = 0.0
    while pos < length:
        end = min(length, pos + dash)
        d.line([(x0 + ux * pos, y0 + uy * pos), (x0 + ux * end, y0 + uy * end)], fill=color, width=width)
        pos += dash + gap


def draw_frame(t):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    lid = lid_closed(t)
    dark = lid > 0

    # арена: тёмная заливка и границы _|
    d.rectangle(ARENA, fill=(0, 0, 0))
    if dark:
        # свет вокруг сердца: три ступени, чуть пульсируют
        r = 60 * lid * (1 + 0.04 * (0.5 - (t * 3 % 1)))
        for k, col in enumerate(GLOW):
            rr = r * (1.0 - 0.28 * k)
            d.ellipse([CX - rr, CY - rr, CX + rr, CY + rr], fill=col)

    # крышка: полосы по верхнему и нижнему краю, интерьер арены остаётся тёмным
    if lid > 0:
        band = 56 * lid
        d.rectangle([ARENA[0], ARENA[1], ARENA[2], ARENA[1] + band], fill=LID)
        d.rectangle([ARENA[0], ARENA[3] - band, ARENA[2], ARENA[3]], fill=LID)
        if lid < 0.999:
            d.rectangle([ARENA[0], ARENA[1] + band - 3, ARENA[2], ARENA[1] + band], fill=YELLOW)
            d.rectangle([ARENA[0], ARENA[3] - band, ARENA[2], ARENA[3] - band + 3], fill=YELLOW)
        d.rectangle([CX - 18, ARENA[1] + band / 2 - 4, CX + 18, ARENA[1] + band / 2 + 0], fill=GREY)
        d.rectangle([CX - 18, ARENA[3] - band / 2, CX + 18, ARENA[3] - band / 2 + 4], fill=GREY)

    # подсказки-пунктиры: от края к сердцу, мигают
    for (wave_t, i, end) in glints(t):
        ox, oy = origin_for(wave_t, i)
        blink = int((end - t) * 10) % 2 == 0
        dashed(d, (ox, oy), (CX, CY), YELLOW, width=3, on=blink)

    # выстрелы: точки летят от края к месту, где было сердце
    for (fire, i, wave_t), u in shots(t):
        ox, oy = origin_for(wave_t, i)
        x = ox + (CX - ox) * u
        y = oy + (CY - oy) * u
        d.ellipse([x - 6, y - 6, x + 6, y + 6], fill=WHITE)

    # границы арены _| поверх всего
    lw = 6
    d.line([(ARENA[0], ARENA[3]), (ARENA[2], ARENA[3])], fill=WHITE, width=lw)     # _ низ
    d.line([(ARENA[0], ARENA[1]), (ARENA[0], ARENA[3])], fill=WHITE, width=lw)     # | лево
    d.line([(ARENA[2], ARENA[1]), (ARENA[2], ARENA[3])], fill=WHITE, width=lw)     # | право
    d.line([(ARENA[0], ARENA[1]), (ARENA[2], ARENA[1])], fill=GREY, width=2)       # верх (тонкая)

    # сердце — точка
    d.ellipse([CX - 8, CY - 8, CX + 8, CY + 8], fill=RED)

    # подписи шагов
    if t < 0.5:
        step = "1. Крышка закрывается: арена темнеет"
    elif t < 1.2:
        step = "1. Темнота: видно только круг вокруг точки"
    elif t < 2.7:
        step = "2. Пунктир: сюда полетит выстрел"
    elif t < 4.2:
        step = "3. Уйди с линии выстрела!"
    elif t < 5.7:
        step = "2. Второй залп: новые пунктиры"
    elif t < 7.3:
        step = "3. Выстрелы второго залпа"
    elif t < 8.0:
        step = "4. Крышка открывается"
    else:
        step = ""

    d.text((ARENA[0], 40), "ТЁМНЫЙ БАК", font=font(30, True), fill=YELLOW)
    if step:
        d.text((ARENA[0], ARENA[3] + 22), step, font=font(20), fill=WHITE)

    # легенда справа
    lx = 540
    d.text((lx, 120), "Легенда", font=font(24, True), fill=WHITE)
    rows = [
        ("•", RED, "Сердце: это ты"),
        ("_|", WHITE, "Граница арены"),
        ("- - -", YELLOW, "Подсказка: линия выстрела"),
        ("•", WHITE, "Выстрел из темноты"),
        ("▮▮", GREY, "Крышка бака"),
    ]
    for k, (sym, col, text) in enumerate(rows):
        y = 170 + k * 52
        d.text((lx, y), sym, font=font(24, True), fill=col)
        d.text((lx + 110, y + 2), text, font=font(19), fill=WHITE)

    d.text((lx, 440), "Что делать:", font=font(22, True), fill=WHITE)
    d.text((lx, 470), "Не стой на линии пунктира.", font=font(20), fill=WHITE)
    d.text((lx, 498), "Двигайся туда, где нет линии.", font=font(20), fill=WHITE)

    if t >= 8.0:
        d.rectangle([0, 0, W, H], fill=BG)
        d.text((W // 2 - 260, H // 2 - 40), "ТЁМНЫЙ БАК", font=font(44, True), fill=YELLOW)
        d.text((W // 2 - 300, H // 2 + 20), "Финал: перед геноцидом и перед милостью", font=font(24), fill=WHITE)

    return img


def main():
    tmp = tempfile.mkdtemp(prefix="darkbin_")
    try:
        n = int(DURATION * FPS)
        for i in range(n):
            draw_frame(i / FPS).save(os.path.join(tmp, "f%05d.png" % i))
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        ff = imageio_ffmpeg.get_ffmpeg_exe()
        import subprocess
        subprocess.run([
            ff, "-y", "-loglevel", "error",
            "-framerate", str(FPS), "-i", os.path.join(tmp, "f%05d.png"),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "26", "-movflags", "+faststart",
            OUT,
        ], check=True)
        print("wrote", OUT, os.path.getsize(OUT), "bytes,", n, "frames")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
