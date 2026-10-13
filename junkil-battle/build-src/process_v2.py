"""Обработка v2: ключ зелёного фона у спрайта; бесшовные текстуры (смещение + кроссфейд по швам)."""
import glob, os
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

def key_green(path, out_h=440):
    im = Image.open(path).convert("RGBA")
    a = np.array(im).astype(np.int32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    key = g - np.maximum(r, b)           # зелёный доминирует
    alpha = np.clip(255 - key * 4, 0, 255)
    alpha = np.where(key > 40, 0, alpha)
    a[..., 3] = np.minimum(a[..., 3], alpha.astype(np.int32))
    im = Image.fromarray(a.astype(np.uint8), "RGBA")
    bbox = im.getbbox()
    im = im.crop(bbox)
    w = round(im.width * out_h / im.height)
    return im.resize((w, out_h), Image.LANCZOS)

def make_seamless(src, size=512, blend=96):
    im = Image.open(src).convert("RGB").resize((size, size), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32)
    h, w = a.shape[:2]
    # сдвиг на половину: швы оказываются в центре, там кроссфейд
    s = np.roll(np.roll(a, h // 2, axis=0), w // 2, axis=1)
    mask = np.ones((h, w), np.float32)
    ramp = np.linspace(0, 1, blend)
    cy, cx = h // 2, w // 2
    mask[cy - blend // 2: cy + blend // 2, :] *= 0  # placeholder, перезаписано ниже
    m = np.ones((h, w), np.float32)
    # по вертикальному шву (x=cx) и горизонтальному (y=cy)
    dx = np.abs(np.arange(w) - cx)
    dy = np.abs(np.arange(h) - cy)
    wx = np.clip(1 - dx / (blend / 2), 0, 1)
    wy = np.clip(1 - dy / (blend / 2), 0, 1)
    wgt = np.maximum(wx[None, :], wy[:, None])
    out = a * (1 - wgt[..., None]) + s * wgt[..., None]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")

if __name__ == "__main__":
    sp = key_green(os.path.join(HERE, "junkil_v2_raw.png"))
    sp.save(os.path.join(ROOT, "res/drawable-nodpi/junkil.png"))
    print("sprite", sp.size, sp.mode)
    os.makedirs(os.path.join(ROOT, "res/drawable-nodpi"), exist_ok=True)
    for p in sorted(glob.glob(os.path.join(HERE, "tex", "*.png"))):
        name = os.path.splitext(os.path.basename(p))[0]
        out = os.path.join(ROOT, "res/drawable-nodpi", "atk_" + name + ".png")
        make_seamless(p).save(out)
        print("tex", out)
