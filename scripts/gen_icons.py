#!/usr/bin/env python3
"""生成 launcher 图标 PNG（API 24/25 fallback）：橙底圆角 + 白色胶囊药丸"""
from PIL import Image, ImageDraw

BASE = "/Users/songziwen/WorkBuddy/2026-08-26-09-53-31/MedReminder/app/src/main/res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BG = (255, 149, 0, 255)      # #FF9500
PILL = (255, 255, 255, 255)
SEAM = (230, 134, 0, 255)

def make(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.18), fill=BG)
    # 胶囊
    w, h = size * 0.60, size * 0.32
    x0, y0 = (size - w) / 2, (size - h) / 2
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=h / 2, fill=PILL)
    # 中间缝
    cx = size / 2
    d.line([cx, y0 + h * 0.30, cx, y0 + h * 0.70], fill=SEAM, width=max(1, int(size * 0.022)))
    return img

for name, s in SIZES.items():
    make(s).save(f"{BASE}/mipmap-{name}/ic_launcher.png")
    print(f"mipmap-{name}: {s}x{s}")
print("done")
