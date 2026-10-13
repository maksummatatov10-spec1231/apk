#!/usr/bin/env python3
"""Рисует PNG из журнала команд харнесса (*.ops), которые пишет заглушка Canvas.

Команды: R l t r b ARGB (прямоугольник), T x y size bold ARGB текст (x, базовая линия),
C l t r b (обрезка), S/E (save/restore). Шрифт — DejaVu Sans Mono (как моноширинный шрифт Android).

Использование: python3 scripts/render_ops.py docs/screens
Нужен Pillow: по умолчанию берётся ~/.venv-render/bin/python (см. README).
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

W, H = 720, 1440
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
_cache = {}


def font(size, bold):
    key = (round(size), bold)
    if key not in _cache:
        _cache[key] = ImageFont.truetype(FONT_BOLD if bold else FONT, max(1, round(size)))
    return _cache[key]


def rgb(argb):
    v = int(argb, 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def render(ops_path, png_path):
    img = Image.new("RGB", (W, H), (0, 0, 0))
    draw = ImageDraw.Draw(img)
    clip = None
    stack = []
    with open(ops_path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            kind, _, rest = line.partition(" ")
            if kind == "R":
                l, t, r, b, col = rest.split()
                box = [float(l), float(t), float(r) - 1, float(b) - 1]
                color = rgb(col)
                if clip is None:
                    draw.rectangle(box, fill=color)
                else:
                    layer = Image.new("RGB", (W, H), color)
                    mask = Image.new("L", (W, H), 0)
                    ImageDraw.Draw(mask).rectangle(box, fill=255)
                    mask = Image.composite(mask, Image.new("L", (W, H), 0), _clip_mask(clip))
                    img.paste(layer, (0, 0), mask)
            elif kind == "T":
                parts = rest.split(" ", 4)
                x, y, size, bold = float(parts[0]), float(parts[1]), float(parts[2]), parts[3] == "1"
                col, text = parts[4].split(" ", 1) if " " in parts[4] else (parts[4], "")
                color = rgb(col)
                fnt = font(size, bold)
                if clip is None:
                    draw.text((x, y), text, font=fnt, fill=color, anchor="ls")
                else:
                    layer = Image.new("RGB", (W, H), (0, 0, 0))
                    mask = Image.new("L", (W, H), 0)
                    ImageDraw.Draw(layer).text((x, y), text, font=fnt, fill=color, anchor="ls")
                    ImageDraw.Draw(mask).text((x, y), text, font=fnt, fill=255, anchor="ls")
                    mask = Image.composite(mask, Image.new("L", (W, H), 0), _clip_mask(clip))
                    img.paste(layer, (0, 0), mask)
            elif kind == "I":
                path, x, y, w, h = rest.split()
                src_path = os.path.join(BASE, path)
                pic = Image.open(src_path).convert("RGBA")
                pic = pic.resize((max(1, round(float(w))), max(1, round(float(h)))), Image.NEAREST)
                img.paste(pic, (round(float(x)), round(float(y))), pic)
            elif kind == "J":
                # J путь sl st sr sb dl dt dr db: кусок картинки (пиксели) в прямоугольник назначения
                path, sl, st, sr, sb, dl, dt, dr, db = rest.split()
                pic = Image.open(os.path.join(BASE, path)).convert("RGBA")
                pic = pic.crop((int(sl), int(st), int(sr), int(sb)))
                dw, dh = max(1, round(float(dr) - float(dl))), max(1, round(float(db) - float(dt)))
                pic = pic.resize((dw, dh), Image.LANCZOS)
                img.paste(pic, (round(float(dl)), round(float(dt))), pic)
            elif kind == "C":
                clip = tuple(float(v) for v in rest.split())
            elif kind == "S":
                stack.append(clip)
            elif kind == "E":
                clip = stack.pop() if stack else None
    img.save(png_path)
    return png_path


def _clip_mask(clip):
    l, t, r, b = clip
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).rectangle([l, t, r - 1, b - 1], fill=255)
    return m


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "docs/screens"
    for ops in sorted(glob.glob(os.path.join(folder, "*.ops"))):
        png = ops[:-4] + ".png"
        render(ops, png)
        os.remove(ops)
        print("rendered", png)


if __name__ == "__main__":
    main()
