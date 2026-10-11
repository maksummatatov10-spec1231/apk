#!/usr/bin/env python3
"""Превращает список команд рисования (NAME.txt из Canvas-заглушек) в PNG.

Нужен Pillow:  pip install pillow.  Использование: render_frames.py файл.txt [файл.png] [ширина высота]
Рисует с суперсэмплингом x2, поддерживает альфу, градиенты (linear/radial), свечение (shadowLayer), текст.
Это проверочный рендер для визуального осмотра, не точная копия Android-рендера.
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

S = 2
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
_fonts = {}


def font(size, bold):
    key = (size, bold)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(FONT_B if bold else FONT, max(1, int(size)))
    return _fonts[key]


def argb(hexs):
    v = int(hexs, 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def gradient_layer(w, h, sh, bx, by):
    """Слой RGBA w×h с градиентом шейдера; bx, by — начало блока в пикселях устройства."""
    kind, _, vals = sh.partition(":")
    parts = vals.split(",")
    if kind == "lin":
        x0, y0, x1, y1 = (float(v) * S for v in parts[:4])
        c0, c1 = argb(parts[4]), argb(parts[5])
        if abs(x0 - x1) < 1e-3 or abs(y1 - y0) >= abs(x1 - x0):
            t = Image.linear_gradient("L").resize((w, h))
            if y1 < y0:
                t = t.transpose(Image.FLIP_TOP_BOTTOM)
        else:
            t = Image.linear_gradient("L").rotate(90, expand=True).resize((w, h))
            if x1 < x0:
                t = t.transpose(Image.FLIP_LEFT_RIGHT)
    else:
        cx, cy, r = (float(v) * S for v in parts[:3])
        c0, c1 = argb(parts[3]), argb(parts[4])
        t = Image.new("L", (w, h), 255)
        d = ImageDraw.Draw(t)
        steps = max(8, min(120, int(r / 4)))
        for k in range(steps, 0, -1):
            rr = r * k / steps
            v = int(255 * k / steps)
            d.ellipse([cx - bx - rr, cy - by - rr, cx - bx + rr, cy - by + rr], fill=v)
    a = Image.new("RGBA", (w, h), c0)
    b = Image.new("RGBA", (w, h), c1)
    return Image.composite(b, a, t)


class Frame:
    def __init__(self, W, H):
        self.W, self.H = W * S, H * S
        self.img = Image.new("RGBA", (self.W, self.H), (6, 4, 18, 255))
        self.ox = 0.0
        self.oy = 0.0
        self.stack = []

    def composite(self, mask, box, spec):
        """mask: L-изображение размера box (в пикселях устройства). spec: параметры кисти."""
        color, style, sw, shader, shadow_r, shadow_c = spec
        x0, y0, x1, y1 = box
        w, h = x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            return
        # обрезка по холсту
        cx0, cy0 = max(0, x0), max(0, y0)
        cx1, cy1 = min(self.W, x1), min(self.H, y1)
        if cx1 <= cx0 or cy1 <= cy0:
            return
        lx0, ly0 = cx0 - x0, cy0 - y0
        mask = mask.crop((lx0, ly0, lx0 + (cx1 - cx0), ly0 + (cy1 - cy0)))
        bw, bh = mask.size
        if shadow_r > 0:
            glow = mask.filter(ImageFilter.GaussianBlur(shadow_r * S * 0.5))
            glow = glow.point(lambda v: min(255, int(v * 1.4)))
            gl = Image.new("RGBA", (bw, bh), argb(shadow_c)[:3] + (255,))
            gl.putalpha(glow.point(lambda v: v * argb(shadow_c)[3] // 255))
            self.img.alpha_composite(gl, (cx0, cy0))
        if shader is not None and shader != "-":
            layer = gradient_layer(w, h, shader, x0, y0)
            layer = layer.crop((lx0, ly0, lx0 + bw, ly0 + bh))
        else:
            r, g, b, a = argb(color)
            layer = Image.new("RGBA", (bw, bh), (r, g, b, 255))
            mask = mask.point(lambda v: v * a // 255)
        layer.putalpha(mask if shader is None or shader == "-" else
                       _mul(layer.getchannel("A"), mask))
        self.img.alpha_composite(layer, (cx0, cy0))



def _mul(a, b):
    return Image.composite(a, Image.new("L", a.size, 0), b)


def spec_of(parts):
    color, style, sw, shader, ts, bold, align, shr, shc = parts
    return (color, style, float(sw), shader, float(shr), shc)


def main():
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src.replace(".txt", ".png")
    W = int(sys.argv[3]) if len(sys.argv) > 4 else 720
    H = int(sys.argv[4]) if len(sys.argv) > 4 else 1280
    fr = Frame(W, H)
    lines = open(src, encoding="utf-8").read().splitlines()
    for line in lines:
        if line == "S":
            fr.stack.append((fr.ox, fr.oy))
            continue
        if line == "R":
            if fr.stack:
                fr.ox, fr.oy = fr.stack.pop()
            continue
        if line.startswith("T "):
            _, dx, dy = line.split()
            fr.ox += float(dx)
            fr.oy += float(dy)
            continue
        head, _, rest = line.partition(" | ")
        parts = rest.split()
        spec_parts = parts[:9]
        ts, bold, align = float(spec_parts[4]), spec_parts[5] == "1", spec_parts[6]
        color, style, sw, shader, shr, shc = spec_of(spec_parts)
        spec = (color, style, sw, shader, shr, shc)
        kind, *args = head.split()
        ox, oy = fr.ox, fr.oy
        f = lambda v: float(v)
        pad = int(shr * 3 + sw + 2) * S
        if kind == "circle":
            x, y, r = (f(a) + o for a, o in zip(args, (ox, oy, 0)))
            x, y, r = x * S, y * S, f(args[2]) * S
            box = (int(x - r - pad), int(y - r - pad), int(x + r + pad), int(y + r + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            d = ImageDraw.Draw(m)
            c = (x - box[0], y - box[1])
            if style == "S":
                d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=255, width=int(sw * S))
            else:
                d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=255)
        elif kind in ("rect", "rrect"):
            l, t, rr, b = (f(v) for v in args[:4])
            l, t, rr, b = (l + ox) * S, (t + oy) * S, (rr + ox) * S, (b + oy) * S
            rad = f(args[4]) * S if kind == "rrect" else 0
            box = (int(l - pad), int(t - pad), int(rr + pad), int(b + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            d = ImageDraw.Draw(m)
            loc = [l - box[0], t - box[1], rr - box[0], b - box[1]]
            kwargs = {"outline": 255, "width": int(sw * S)} if style == "S" else {"fill": 255}
            if rad > 0:
                d.rounded_rectangle(loc, radius=rad, **kwargs)
            else:
                d.rectangle(loc, **kwargs)
        elif kind == "line":
            x0, y0, x1, y1 = (f(v) for v in args[:4])
            x0, y0, x1, y1 = (x0 + ox) * S, (y0 + oy) * S, (x1 + ox) * S, (y1 + oy) * S
            box = (int(min(x0, x1) - pad), int(min(y0, y1) - pad), int(max(x0, x1) + pad), int(max(y0, y1) + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            ImageDraw.Draw(m).line([x0 - box[0], y0 - box[1], x1 - box[0], y1 - box[1]], fill=255, width=max(1, int(sw * S)))
        elif kind == "arc":
            l, t, rr, b, s0, sw_ = (f(v) for v in args[:6])
            l, t, rr, b = (l + ox) * S, (t + oy) * S, (rr + ox) * S, (b + oy) * S
            box = (int(l - pad), int(t - pad), int(rr + pad), int(b + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            loc = [l - box[0], t - box[1], rr - box[0], b - box[1]]
            ImageDraw.Draw(m).arc(loc, s0, s0 + sw_, fill=255, width=max(1, int(sw * S)))
        elif kind == "path":
            toks = args[0].split("\u0002")
            pts, polys, i = [], [], 0
            cur = None
            while i < len(toks):
                op = toks[i]
                if op == "M":
                    if pts:
                        polys.append(pts)
                    pts = [(f(toks[i + 1]) + ox, f(toks[i + 2]) + oy)]
                    i += 3
                elif op == "L":
                    pts.append((f(toks[i + 1]) + ox, f(toks[i + 2]) + oy))
                    i += 3
                elif op == "C":
                    p0 = pts[-1]
                    c1 = (f(toks[i + 1]) + ox, f(toks[i + 2]) + oy)
                    c2 = (f(toks[i + 3]) + ox, f(toks[i + 4]) + oy)
                    p3 = (f(toks[i + 5]) + ox, f(toks[i + 6]) + oy)
                    for k in range(1, 17):
                        t_ = k / 16
                        mt = 1 - t_
                        pts.append((mt**3 * p0[0] + 3 * mt * mt * t_ * c1[0] + 3 * mt * t_ * t_ * c2[0] + t_**3 * p3[0],
                                    mt**3 * p0[1] + 3 * mt * mt * t_ * c1[1] + 3 * mt * t_ * t_ * c2[1] + t_**3 * p3[1]))
                    i += 7
                else:
                    i += 1
            if pts:
                polys.append(pts)
            allp = [(x * S, y * S) for poly in polys for (x, y) in poly]
            if not allp:
                continue
            xs = [p[0] for p in allp]
            ys = [p[1] for p in allp]
            box = (int(min(xs) - pad), int(min(ys) - pad), int(max(xs) + pad), int(max(ys) + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            d = ImageDraw.Draw(m)
            for poly in polys:
                q = [(x * S - box[0], y * S - box[1]) for (x, y) in poly]
                if style == "S":
                    d.line(q, fill=255, width=max(1, int(sw * S)), joint="curve")
                elif len(q) > 2:
                    d.polygon(q, fill=255)
        elif kind == "text":
            x, y = f(args[0]) + ox, f(args[1]) + oy
            x, y = x * S, y * S
            text = args[2].replace("\u0001", " ")
            fnt = font(ts * S, bold)
            anchor = {"L": "ls", "C": "ms", "R": "rs"}[align]
            bb = fnt.getbbox(text, anchor=anchor)
            box = (int(x + bb[0] - pad), int(y + bb[1] - pad), int(x + bb[2] + pad), int(y + bb[3] + pad))
            m = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
            ImageDraw.Draw(m).text((x - box[0], y - box[1]), text, font=fnt, fill=255, anchor=anchor)
        else:
            continue
        fr.composite(m, box, spec)
    out = fr.img.resize((W, H), Image.LANCZOS).convert("RGB")
    out.save(dst)
    print("  ", dst)


if __name__ == "__main__":
    main()
