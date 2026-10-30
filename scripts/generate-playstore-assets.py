#!/usr/bin/env python3
"""
Generates Google Play Store mandatory graphic assets for TapInk (瞬墨):
- docs/play-store/icon-512.png (512x512 32-bit PNG, rendered with 4x supersampling)
- docs/play-store/feature-graphic-1024x500.png (1024x500 24-bit RGB PNG, rendered with 2x supersampling)

Visual Spec (Blank-Slate Product Director Design):
- Brand: TapInk 瞬墨 (TapInk: NFC E-Paper & E-Ink)
- Palette:
  - Obsidian Slate Background: #1E2229 -> #141619 -> #101215
  - Warm E-Paper Ivory Card: #F6F4EE
  - Paper Fold Warm Gray: #DCD8CC
  - E-Ink Carbon Black: #1C1E21
  - E-Paper Crimson Red: #D93829
  - E-Paper Amber Yellow: #F2B705
"""

import math
import os
from PIL import Image, ImageDraw, ImageFont

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(PROJECT_DIR, "docs", "play-store")
os.makedirs(OUT_DIR, exist_ok=True)

# Brand Palette
COL_BG_TOP = (30, 34, 41)        # #1E2229
COL_BG_MID = (20, 22, 25)        # #141619
COL_BG_BOT = (16, 18, 21)        # #101215
COL_IVORY = (246, 244, 238)      # #F6F4EE
COL_FOLD = (220, 216, 204)       # #DCD8CC
COL_CARBON = (28, 30, 33)        # #1C1E21
COL_CRIMSON = (217, 56, 41)      # #D93829
COL_AMBER = (242, 183, 5)        # #F2B705


def load_cjk_font(size):
    font_paths = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    for fp in font_paths:
        if os.path.exists(fp):
            return ImageFont.truetype(fp, size)
    return ImageFont.load_default()


def draw_tapink_card_glyph(draw, ox, oy, unit_scale):
    """
    Draws the exact 108x108dp TapInk icon foreground geometry scaled by `unit_scale`
    with top-left origin at (ox, oy).
    """
    def pt(x, y):
        return (ox + x * unit_scale, oy + y * unit_scale)

    # Subtle outer NFC magnetic field ring (center 54,54, r=44)
    c_ring = pt(54, 54)
    r_ring = 44 * unit_scale
    draw.ellipse(
        [c_ring[0] - r_ring, c_ring[1] - r_ring, c_ring[0] + r_ring, c_ring[1] + r_ring],
        outline=(35, 40, 48, 255),
        width=max(1, int(0.8 * unit_scale)),
    )

    # Element A: Warm E-Paper Ivory Card with Bottom-Right Dog-Ear Fold (X: 26..82, Y: 29..79)
    # Build smooth polygon with rounded corners (radius 6dp) on TL, TR, BL and 45-deg cut on BR
    poly_pts = []
    # Top-Left rounded corner: center (32, 35), r=6, angle 180 -> 270
    for a in range(180, 275, 10):
        rad = math.radians(a)
        poly_pts.append(pt(32 + 6 * math.cos(rad), 35 + 6 * math.sin(rad)))
    # Top-Right rounded corner: center (76, 35), r=6, angle 270 -> 360
    for a in range(270, 365, 10):
        rad = math.radians(a)
        poly_pts.append(pt(76 + 6 * math.cos(rad), 35 + 6 * math.sin(rad)))
    # Dog-ear fold points: (82, 67) -> (70, 79)
    poly_pts.append(pt(82, 67))
    poly_pts.append(pt(70, 79))
    # Bottom-Left rounded corner: center (32, 73), r=6, angle 90 -> 180
    for a in range(90, 185, 10):
        rad = math.radians(a)
        poly_pts.append(pt(32 + 6 * math.cos(rad), 73 + 6 * math.sin(rad)))

    draw.polygon(poly_pts, fill=COL_IVORY + (255,))

    # Element B: Bottom-Right Paper Fold Flap (#DCD8CC)
    flap_pts = [pt(82, 67), pt(70, 79)]
    # Inner rounded vertex: center (74, 71), r=4, angle 180 -> 270
    for a in range(180, 275, 15):
        rad = math.radians(a)
        flap_pts.append(pt(74 + 4 * math.cos(rad), 71 + 4 * math.sin(rad)))
    draw.polygon(flap_pts, fill=COL_FOLD + (255,))

    # Element C: Left NFC Signal Source & Dual Wave Arcs (Center 36, 54)
    src_c = pt(36, 54)
    src_r = 2.8 * unit_scale
    draw.ellipse(
        [src_c[0] - src_r, src_c[1] - src_r, src_c[0] + src_r, src_c[1] + src_r],
        fill=COL_CARBON + (255,),
    )

    stroke_w = max(2, int(3.2 * unit_scale))
    for arc_r_dp in (8.5, 15.0):
        r_px = arc_r_dp * unit_scale
        bbox = [src_c[0] - r_px, src_c[1] - r_px, src_c[0] + r_px, src_c[1] + r_px]
        draw.arc(bbox, start=-42, end=42, fill=COL_CARBON + (255,), width=stroke_w)
        # Round line caps at exact -42 and +42 degrees
        cap_r = stroke_w * 0.48
        for deg in (-42, 42):
            rad = math.radians(deg)
            ex = src_c[0] + (r_px - stroke_w * 0.5) * math.cos(rad)
            ey = src_c[1] + (r_px - stroke_w * 0.5) * math.sin(rad)
            draw.ellipse(
                [ex - cap_r, ey - cap_r, ex + cap_r, ey + cap_r],
                fill=COL_CARBON + (255,),
            )

    # Element D: Right 4-Color E-Paper Halftone Dither Matrix
    dither_dots = [
        # Column 1 (X = 59)
        (59.0, 41.0, 2.8, COL_CRIMSON),
        (59.0, 48.0, 2.8, COL_CARBON),
        (59.0, 55.0, 2.8, COL_AMBER),
        (59.0, 62.0, 2.4, COL_CRIMSON),
        # Column 2 (X = 65.5)
        (65.5, 41.0, 2.4, COL_AMBER),
        (65.5, 48.0, 2.8, COL_CRIMSON),
        (65.5, 55.0, 2.4, COL_CARBON),
        (65.5, 62.0, 2.0, COL_AMBER),
        # Column 3 (X = 72.0)
        (72.0, 41.0, 1.8, COL_CARBON),
        (72.0, 48.0, 2.2, COL_AMBER),
        (72.0, 55.0, 2.0, COL_CRIMSON),
    ]
    for dx, dy, dr, col in dither_dots:
        dc = pt(dx, dy)
        r_px = dr * unit_scale
        draw.ellipse(
            [dc[0] - r_px, dc[1] - r_px, dc[0] + r_px, dc[1] + r_px],
            fill=col + (255,),
        )


def generate_icon_512():
    # Render at 2048x2048 (4x supersampling) and downsample with LANCZOS for crisp anti-aliased vectors
    ss = 4
    size = 512 * ss
    img = Image.new("RGBA", (size, size), COL_BG_MID + (255,))
    draw = ImageDraw.Draw(img)

    # 135-deg Diagonal Gradient Background (#1E2229 -> #101215)
    for y in range(size):
        t = y / (size - 1)
        r = int(COL_BG_TOP[0] * (1 - t) + COL_BG_BOT[0] * t)
        g = int(COL_BG_TOP[1] * (1 - t) + COL_BG_BOT[1] * t)
        b = int(COL_BG_TOP[2] * (1 - t) + COL_BG_BOT[2] * t)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))

    draw_tapink_card_glyph(draw, 0, 0, size / 108.0)

    final_img = img.resize((512, 512), Image.Resampling.LANCZOS)
    out_path = os.path.join(OUT_DIR, "icon-512.png")
    final_img.save(out_path, "PNG")
    print(f"[OK] Generated {out_path} (512x512)")
    return final_img


def generate_feature_graphic(icon_img):
    # Render at 2048x1000 (2x supersampling) then LANCZOS resize to 1024x500
    ss = 2
    w, h = 1024 * ss, 500 * ss
    img = Image.new("RGB", (w, h), (20, 23, 28))
    draw = ImageDraw.Draw(img)

    # Diagonal background gradient (#14171C -> #1F242D)
    for y in range(h):
        t = y / (h - 1)
        r = int(20 * (1 - t) + 31 * t)
        g = int(23 * (1 - t) + 36 * t)
        b = int(28 * (1 - t) + 45 * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Subtle Crimson & Amber ambient glow behind right hero visual (X ~ 770*ss, Y ~ 250*ss)
    glow_cx, glow_cy = 770 * ss, 250 * ss
    for radius in range(230 * ss, 20 * ss, -8 * ss):
        ratio = 1.0 - (radius / (230.0 * ss))
        r = min(255, int(26 + 38 * ratio))
        g = min(255, int(30 + 22 * ratio))
        b = min(255, int(38 + 10 * ratio))
        draw.ellipse(
            [glow_cx - radius, glow_cy - radius, glow_cx + radius, glow_cy + radius],
            fill=(r, g, b),
        )

    # =========================================================================
    # RIGHT HERO ILLUSTRATION (X: 560..968, Y: 40..460):
    # Physical 4-Color E-Paper Desk Sign + Phone Bottom Sheet + NFC Energy Arcs
    # =========================================================================
    # 1. E-Paper 4.2" Desk Sign on Right (X: 690..955, Y: 80..410)
    card_x0, card_y0, card_x1, card_y1 = 685 * ss, 78 * ss, 955 * ss, 412 * ss
    # Drop shadow
    draw.rounded_rectangle(
        [card_x0 + 8 * ss, card_y0 + 12 * ss, card_x1 + 8 * ss, card_y1 + 12 * ss],
        radius=22 * ss,
        fill=(10, 12, 16),
    )
    # Matte White Bezel
    draw.rounded_rectangle(
        [card_x0, card_y0, card_x1, card_y1],
        radius=20 * ss,
        fill=(242, 244, 248),
        outline=(68, 71, 78),
        width=2 * ss,
    )
    # Inner Warm Ivory E-Paper Screen
    scr_x0, scr_y0 = card_x0 + 18 * ss, card_y0 + 18 * ss
    scr_x1, scr_y1 = card_x1 - 18 * ss, card_y1 - 44 * ss
    draw.rounded_rectangle(
        [scr_x0, scr_y0, scr_x1, scr_y1],
        radius=8 * ss,
        fill=COL_IVORY,
        outline=(210, 206, 196),
        width=1 * ss,
    )
    # 4-Color Artwork on E-Paper Screen (Sun + Crimson Mountains + Carbon Badge Typography)
    sun_cx, sun_cy, sun_r = scr_x0 + 165 * ss, scr_y0 + 68 * ss, 34 * ss
    draw.ellipse([sun_cx - sun_r, sun_cy - sun_r, sun_cx + sun_r, sun_cy + sun_r], fill=COL_AMBER)
    # Halftone dither dots around sun
    for deg in range(0, 360, 24):
        rad = math.radians(deg)
        dx = sun_cx + int(42 * ss * math.cos(rad))
        dy = sun_cy + int(42 * ss * math.sin(rad))
        draw.ellipse([dx - 3 * ss, dy - 3 * ss, dx + 3 * ss, dy + 3 * ss], fill=COL_AMBER)

    # Mountains in Crimson Red & Carbon Black
    m1 = [
        (scr_x0, scr_y1),
        (scr_x0 + 78 * ss, scr_y0 + 115 * ss),
        (scr_x0 + 150 * ss, scr_y0 + 195 * ss),
        (scr_x0 + 192 * ss, scr_y0 + 145 * ss),
        (scr_x1, scr_y1),
    ]
    draw.polygon(m1, fill=COL_CRIMSON)
    m2 = [
        (scr_x0, scr_y1),
        (scr_x0 + 52 * ss, scr_y0 + 178 * ss),
        (scr_x0 + 118 * ss, scr_y1),
    ]
    draw.polygon(m2, fill=COL_CARBON)

    # Bottom bezel brand pill
    draw.rounded_rectangle(
        [card_x0 + 95 * ss, card_y1 - 26 * ss, card_x1 - 95 * ss, card_y1 - 14 * ss],
        radius=6 * ss,
        fill=COL_CARBON,
    )

    # 2. Phone Bottom Sheet Overlay on Left-Center of Hero Zone (X: 590..755, Y: 195..435)
    ph_x0, ph_y0, ph_x1, ph_y1 = 590 * ss, 195 * ss, 755 * ss, 435 * ss
    draw.rounded_rectangle(
        [ph_x0 + 6 * ss, ph_y0 + 8 * ss, ph_x1 + 6 * ss, ph_y1 + 8 * ss],
        radius=24 * ss,
        fill=(8, 10, 14),
    )
    draw.rounded_rectangle(
        [ph_x0, ph_y0, ph_x1, ph_y1],
        radius=24 * ss,
        fill=(25, 28, 32),
        outline=(72, 82, 100),
        width=2 * ss,
    )
    # Drag handle
    draw.rounded_rectangle(
        [ph_x0 + 72 * ss, ph_y0 + 12 * ss, ph_x1 - 72 * ss, ph_y0 + 16 * ss],
        radius=2 * ss,
        fill=(140, 145, 155),
    )
    # Green/Amber Success Check Circle inside Phone Sheet
    chk_cx, chk_cy, chk_r = (ph_x0 + ph_x1) // 2, ph_y0 + 108 * ss, 42 * ss
    draw.ellipse(
        [chk_cx - chk_r - 12 * ss, chk_cy - chk_r - 12 * ss, chk_cx + chk_r + 12 * ss, chk_cy + chk_r + 12 * ss],
        outline=COL_AMBER,
        width=3 * ss,
    )
    draw.ellipse([chk_cx - chk_r, chk_cy - chk_r, chk_cx + chk_r, chk_cy + chk_r], fill=COL_AMBER)
    # Checkmark lines
    draw.line(
        [
            (chk_cx - 18 * ss, chk_cy + 2 * ss),
            (chk_cx - 4 * ss, chk_cy + 16 * ss),
            (chk_cx + 20 * ss, chk_cy - 14 * ss),
        ],
        fill=COL_CARBON,
        width=6 * ss,
        joint="curve",
    )
    # Spec pill inside phone sheet
    font_phone_pill = load_cjk_font(13 * ss)
    draw.rounded_rectangle(
        [chk_cx - 58 * ss, ph_y0 + 175 * ss, chk_cx + 58 * ss, ph_y0 + 205 * ss],
        radius=12 * ss,
        fill=(35, 42, 54),
        outline=COL_AMBER,
        width=1 * ss,
    )
    draw.text((chk_cx - 44 * ss, ph_y0 + 181 * ss), "4.2\" 四色 BWRY", fill=COL_IVORY, font=font_phone_pill)

    # 3. NFC Energy Waves Bridging Phone & E-Paper (Center 665, 155)
    wave_cx, wave_cy = 665 * ss, 155 * ss
    for r_dp, col in [(22, COL_AMBER), (36, COL_CRIMSON), (50, COL_AMBER)]:
        r_px = r_dp * ss
        draw.arc(
            [wave_cx - r_px, wave_cy - r_px, wave_cx + r_px, wave_cy + r_px],
            start=-45,
            end=35,
            fill=col,
            width=4 * ss,
        )

    # =========================================================================
    # LEFT TYPOGRAPHY & VALUE PROPOSITION (X: 56..550)
    # =========================================================================
    font_brand = load_cjk_font(24 * ss)
    font_tag = load_cjk_font(13 * ss)
    font_h1 = load_cjk_font(42 * ss)
    font_sub = load_cjk_font(18 * ss)
    font_pill = load_cjk_font(15 * ss)

    # Top Brand Badge Row (Y: 68)
    mini_icon = icon_img.resize((46 * ss, 46 * ss), Image.Resampling.LANCZOS)
    # Rounded mask for mini icon
    mask = Image.new("L", (46 * ss, 46 * ss), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, 46 * ss, 46 * ss], radius=11 * ss, fill=255)
    img.paste(mini_icon.convert("RGB"), (56 * ss, 66 * ss), mask)

    draw.text((114 * ss, 66 * ss), "TapInk 瞬墨", fill=COL_IVORY, font=font_brand)
    draw.text((114 * ss, 98 * ss), "NFC E-PAPER STUDIO", fill=COL_AMBER, font=font_tag)

    # Headline (Y: 148..250) with Amber highlight on "一觸秒刷"
    draw.text((56 * ss, 146 * ss), "相簿分享，", fill=(255, 255, 255), font=font_h1)
    draw.text((56 * ss, 204 * ss), "一觸秒刷", fill=COL_AMBER, font=font_h1)
    bbox_hl = draw.textbbox((56 * ss, 204 * ss), "一觸秒刷", font=font_h1)
    draw.text((bbox_hl[2] + 4 * ss, 204 * ss), "電子紙", fill=(255, 255, 255), font=font_h1)

    # Subheadline (Y: 278..335)
    draw.text(
        (56 * ss, 278 * ss),
        "免開 App、自動識別雙色/三色/四色規格",
        fill=(184, 191, 204),
        font=font_sub,
    )
    draw.text(
        (56 * ss, 308 * ss),
        "專為無電池 NFC 電子紙桌牌與名片打造",
        fill=(184, 191, 204),
        font=font_sub,
    )

    # Bottom 3 Feature Pills (Y: 372..414)
    font_pill = load_cjk_font(13 * ss)
    pills = [
        ("系統「分享」直達", COL_AMBER),
        ("四色專業抖動", COL_CRIMSON),
        ("100% 純離線零廣告", COL_IVORY),
    ]
    px = 56 * ss
    py = 372 * ss
    for text, dot_col in pills:
        bbox = draw.textbbox((0, 0), text, font=font_pill)
        tw = bbox[2] - bbox[0]
        pw = tw + 34 * ss
        ph = 40 * ss
        draw.rounded_rectangle(
            [px, py, px + pw, py + ph],
            radius=20 * ss,
            fill=(35, 41, 52),
            outline=(56, 65, 82),
            width=2 * ss,
        )
        # Colored indicator dot inside pill
        dot_r = 4 * ss
        dot_cx = px + 14 * ss
        dot_cy = py + ph // 2
        draw.ellipse([dot_cx - dot_r, dot_cy - dot_r, dot_cx + dot_r, dot_cy + dot_r], fill=dot_col)
        draw.text((px + 23 * ss, py + 9 * ss), text, fill=COL_IVORY, font=font_pill)
        px += pw + 8 * ss

    final_fg = img.resize((1024, 500), Image.Resampling.LANCZOS)
    out_path = os.path.join(OUT_DIR, "feature-graphic-1024x500.png")
    final_fg.save(out_path, "PNG")
    print(f"[OK] Generated {out_path} (1024x500)")


if __name__ == "__main__":
    icon = generate_icon_512()
    generate_feature_graphic(icon)
