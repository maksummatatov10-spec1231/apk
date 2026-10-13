#!/usr/bin/env python3
"""Пересчитывает PORTRAIT_CROP (SpamtonView.java): общая непрозрачная область всех портретов.
Запуск: .venv с Pillow; python scripts/portrait_crop.py
"""
import glob
import os
import re
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
l = t = 10 ** 9
r = b = -1
for f in glob.glob(os.path.join(ROOT, "res/drawable-nodpi/spamton*.png")):
    alpha = Image.open(f).convert("RGBA").split()[3]
    bb = alpha.point(lambda v: 255 if v > 8 else 0).getbbox()
    if bb:
        l, t, r, b = min(l, bb[0]), min(t, bb[1]), max(r, bb[2]), max(b, bb[3])
path = os.path.join(ROOT, "src/com/example/spamton/SpamtonView.java")
src = open(path, encoding="utf-8").read()
src = re.sub(r"PORTRAIT_CROP = new android\.graphics\.Rect\([^)]*\);",
             "PORTRAIT_CROP = new android.graphics.Rect(%d, %d, %d, %d);" % (l, t, r, b), src)
open(path, "w", encoding="utf-8").write(src)
print("PORTRAIT_CROP", l, t, r, b)
