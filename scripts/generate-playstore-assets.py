#!/usr/bin/env python3
"""
Generates Google Play Store mandatory graphic assets for EZ Sign NFC Updater:
- docs/play-store/icon-512.png (512x512 32-bit PNG)
- docs/play-store/feature-graphic-1024x500.png (1024x500 24-bit RGB PNG)
"""

import os
from PIL import Image, ImageDraw, ImageFont

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(PROJECT_DIR, "docs", "play-store")
os.makedirs(OUT_DIR, exist_ok=True)


def draw_epaper_device(draw, cx, cy, scale=1.0):
    # Outer NFC breathing rings
    for r_base, width, col in [
        (170, 6, (62, 71, 89, 180)),
        (210, 4, (168, 199, 250, 95)),
    ]:
        r = int(r_base * scale)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=max(2, int(width * scale)))

    # E-Paper Bezel Frame
    bw, bh = int(176 * scale), int(224 * scale)
    x0, y0 = cx - bw // 2, cy - bh // 2
    x1, y1 = cx + bw // 2, cy + bh // 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=int(24 * scale), fill=(242, 244, 248, 255))

    # Inner E-Ink Screen
    pad = int(18 * scale)
    sx0, sy0 = x0 + pad, y0 + pad
    sx1, sy1 = x1 - pad, y1 - int(38 * scale)
    draw.rounded_rectangle([sx0, sy0, sx1, sy1], radius=int(8 * scale), fill=(34, 37, 44, 255))

    # Sun (Yellow BWRY accent)
    sun_r = int(22 * scale)
    sun_cx = sx0 + int((sx1 - sx0) * 0.68)
    sun_cy = sy0 + int((sy1 - sy0) * 0.30)
    draw.ellipse([sun_cx - sun_r, sun_cy - sun_r, sun_cx + sun_r, sun_cy + sun_r], fill=(255, 214, 0, 255))

    # Mountains (Red BWRY accent)
    m_pts = [
        (sx0, sy1),
        (sx0 + int((sx1 - sx0) * 0.36), sy0 + int((sy1 - sy0) * 0.45)),
        (sx0 + int((sx1 - sx0) * 0.66), sy0 + int((sy1 - sy0) * 0.78)),
        (sx0 + int((sx1 - sx0) * 0.82), sy0 + int((sy1 - sy0) * 0.58)),
        (sx1, sy1),
    ]
    draw.polygon(m_pts, fill=(229, 57, 53, 255))

    # Bottom NFC Blue Indicator Bar
    bar_w, bar_h = int(68 * scale), int(14 * scale)
    by = y1 - int(22 * scale)
    draw.rounded_rectangle(
        [cx - bar_w // 2, by - bar_h // 2, cx + bar_w // 2, by + bar_h // 2],
        radius=int(7 * scale),
        fill=(168, 199, 250, 255),
    )


def generate_icon_512():
    img = Image.new("RGBA", (512, 512), (25, 28, 32, 255))
    draw = ImageDraw.Draw(img)
    draw_epaper_device(draw, 256, 256, scale=1.0)
    out_path = os.path.join(OUT_DIR, "icon-512.png")
    img.save(out_path, "PNG")
    print(f"[OK] Generated {out_path} (512x512)")


def generate_feature_graphic():
    img = Image.new("RGB", (1024, 500), (25, 28, 32))
    draw = ImageDraw.Draw(img)

    # Subtle radial background glow on left
    for r in range(360, 40, -20):
        c = 25 + int((360 - r) * 0.04)
        draw.ellipse([260 - r, 250 - r, 260 + r, 250 + r], fill=(c, c + 4, c + 12))

    draw_epaper_device(draw, 260, 250, scale=0.88)

    # Load font
    font_paths = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    font_title = None
    font_sub = None
    font_pill = None
    for fp in font_paths:
        if os.path.exists(fp):
            font_title = ImageFont.truetype(fp, 54)
            font_sub = ImageFont.truetype(fp, 26)
            font_pill = ImageFont.truetype(fp, 20)
            break
    if font_title is None:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_pill = ImageFont.load_default()

    draw.text((500, 130), "EZ Sign 電子紙", fill=(226, 226, 233), font=font_title)
    draw.text((500, 210), "NFC 免電池一鍵分享更新工具", fill=(168, 199, 250), font=font_sub)
    draw.text((500, 260), "自動偵測 2.9\" 黑白 / 4.2\" 四色規格", fill=(196, 198, 208), font=font_sub)

    # Feature pills
    pills = ["Material 3 介面", "Floyd-Steinberg 抖動", "零權限・純離線"]
    px = 500
    py = 330
    for text in pills:
        bbox = draw.textbbox((0, 0), text, font=font_pill)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        pw = tw + 32
        ph = th + 20
        draw.rounded_rectangle([px, py, px + pw, py + ph], radius=18, fill=(62, 71, 89), outline=(168, 199, 250), width=1)
        draw.text((px + 16, py + 8), text, fill=(216, 227, 248), font=font_pill)
        px += pw + 14

    out_path = os.path.join(OUT_DIR, "feature-graphic-1024x500.png")
    img.save(out_path, "PNG")
    print(f"[OK] Generated {out_path} (1024x500)")


if __name__ == "__main__":
    generate_icon_512()
    generate_feature_graphic()
