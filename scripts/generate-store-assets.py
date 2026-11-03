#!/usr/bin/env python3
"""
Generates Google Play Store & GitHub graphic assets and refines screenshot backdrops for TapInk (瞬墨):
- docs/play-store/icon-512.png (512x512 32-bit PNG, rendered with 4x supersampling)
- docs/play-store/feature-graphic-1024x500.png (1024x500 24-bit RGB PNG, 2x supersampling + 1:1 dithered E-Paper artwork)
- docs/play-store/social-preview-1280x640.png (1280x640 24-bit RGB PNG, GitHub Social Preview with safe zone)
- docs/screenshots/01~04.png (1080x2400 native resolution, adaptive sandwich modal scrim compositing, strictly idempotent)

Visual Spec (Targeted Incremental Refinement on Approved Brand Baseline):
- Brand: TapInk 瞬墨 (TapInk: NFC E-Paper & E-Ink)
- Palette:
  - Obsidian Slate Background: #1E2229 -> #141619 -> #101215
  - Warm E-Paper Ivory Card: #F6F4EE
  - Paper Fold Warm Gray: #DCD8CC
  - E-Ink Carbon Black: #1C1E21
  - E-Paper Crimson Red: #D93829
  - E-Paper Amber Yellow: #F2B705
"""

import base64
import math
import os
import zlib
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(PROJECT_DIR, "docs", "play-store")
SCREENSHOT_DIR = os.path.join(PROJECT_DIR, "docs", "screenshots")
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

# Brand Palette
COL_BG_TOP = (30, 34, 41)        # #1E2229
COL_BG_MID = (20, 22, 25)        # #141619
COL_BG_BOT = (16, 18, 21)        # #101215
COL_IVORY = (246, 244, 238)      # #F6F4EE
COL_FOLD = (220, 216, 204)       # #DCD8CC
COL_CARBON = (28, 30, 33)        # #1C1E21
COL_CRIMSON = (217, 56, 41)      # #D93829
COL_AMBER = (242, 183, 5)        # #F2B705
COL_SHEET = (25, 28, 32)         # #191C20 (TapInk Material 3 Bottom Sheet surface)


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


def render_master_icon_2048():
    size = 2048
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
    return img


def generate_icons():
    master_img = render_master_icon_2048()

    # Google Play Store 512x512 PNG
    icon_512 = master_img.resize((512, 512), Image.Resampling.LANCZOS)
    play_path = os.path.join(OUT_DIR, "icon-512.png")
    icon_512.save(play_path, "PNG")
    print(f"[OK] Generated {play_path} (512x512)")

    return icon_512


def render_dithered_epaper_landscape(width=222, height=258):
    """
    Renders a continuous-tone warm sun & layered mountain landscape at exact 1x logical resolution
    (222x258) and applies 4-color Floyd-Steinberg error-diffusion dithering using TapInk's
    brand E-Paper palette (Ivory #F6F4EE, Carbon #1C1E21, Crimson #D93829, Amber #F2B705).
    """
    palette = [
        COL_CARBON,   # 0: Carbon Black (#1C1E21)
        COL_IVORY,    # 1: Warm Ivory White (#F6F4EE)
        COL_AMBER,    # 2: Amber Yellow (#F2B705)
        COL_CRIMSON,  # 3: Crimson Red (#D93829)
    ]

    buf_r = [[0.0] * width for _ in range(height)]
    buf_g = [[0.0] * width for _ in range(height)]
    buf_b = [[0.0] * width for _ in range(height)]

    cx, cy = width * 0.62, height * 0.27
    sun_r = width * 0.185

    for y in range(height):
        ny = y / (height - 1.0)
        for x in range(width):
            nx = x / (width - 1.0)

            # Three layered mountain ridges matching the portrait landscape aesthetic
            ridge1 = 0.51 - 0.14 * math.sin(nx * math.pi * 1.35 + 0.18) - 0.04 * math.cos(nx * math.pi * 3.1)
            ridge2 = 0.65 - 0.11 * math.cos(nx * math.pi * 1.75 - 0.35) - 0.03 * math.sin(nx * math.pi * 3.8)
            ridge3 = 0.79 - 0.07 * math.sin(nx * math.pi * 2.3 + 0.9)

            dx = x - cx
            dy = y - cy
            dist = math.hypot(dx, dy)

            if ny > ridge3:
                # Foreground carbon silhouette ridge with subtle warm rim light near the crest
                t = min(1.0, (ny - ridge3) / max(0.01, 1.0 - ridge3))
                crest_glow = max(0.0, 1.0 - t * 3.5)
                r = 28 + 26 * crest_glow
                g = 30 + 10 * crest_glow
                b = 33 + 4 * crest_glow
            elif ny > ridge2:
                # Midground mountain: deep crimson-charcoal gradient (dithers to Carbon + Crimson + Amber)
                t = (ny - ridge2) / max(0.01, ridge3 - ridge2)
                r = 182 - 118 * t
                g = 62 - 34 * t
                b = 44 - 14 * t
            elif ny > ridge1:
                # Sunlit distant crimson-amber ridge (dithers to Crimson + Amber + Ivory)
                t = (ny - ridge1) / max(0.01, ridge2 - ridge1)
                r = 232 - 22 * t
                g = 122 - 64 * t
                b = 46 - 12 * t
            elif dist <= sun_r:
                # Radiant warm sun disc: Ivory core smoothly transitioning to pure Amber rim
                st = dist / sun_r
                if st < 0.42:
                    lt = st / 0.42
                    r = 246 * (1 - lt) + 244 * lt
                    g = 244 * (1 - lt) + 216 * lt
                    b = 238 * (1 - lt) + 115 * lt
                else:
                    lt = (st - 0.42) / 0.58
                    r = 244 * (1 - lt) + 242 * lt
                    g = 216 * (1 - lt) + 183 * lt
                    b = 115 * (1 - lt) + 5 * lt
            elif dist <= sun_r * 1.48:
                # Soft amber-ivory solar corona halo
                ht = (dist - sun_r) / (sun_r * 0.48)
                r = 244 + 2 * ht
                g = 204 + 40 * ht
                b = 82 + 156 * ht
            else:
                # Sky gradient: clean warm ivory paper at top transitioning to warm amber-crimson horizon glow
                sky_t = min(1.0, ny / 0.54)
                if sky_t < 0.40:
                    r, g, b = 246, 244, 238
                else:
                    lt = (sky_t - 0.40) / 0.60
                    r = 245
                    g = 244 - 52 * (lt ** 1.55)
                    b = 238 - 150 * (lt ** 1.55)

            buf_r[y][x] = max(0.0, min(255.0, float(r)))
            buf_g[y][x] = max(0.0, min(255.0, float(g)))
            buf_b[y][x] = max(0.0, min(255.0, float(b)))

    out_img = Image.new("RGB", (width, height), COL_IVORY)
    pixels = out_img.load()
    assert pixels is not None

    for y in range(height):
        xs = range(width) if (y % 2 == 0) else range(width - 1, -1, -1)
        step = 1 if (y % 2 == 0) else -1
        for x in xs:
            old_r = max(0.0, min(255.0, buf_r[y][x]))
            old_g = max(0.0, min(255.0, buf_g[y][x]))
            old_b = max(0.0, min(255.0, buf_b[y][x]))

            best_col = palette[0]
            best_dist = 1e18
            for col in palette:
                dr = old_r - col[0]
                dg = old_g - col[1]
                db = old_b - col[2]
                d = dr * dr + dg * dg + db * db
                if d < best_dist:
                    best_dist = d
                    best_col = col

            pixels[x, y] = best_col

            err_r = (old_r - best_col[0]) * 0.85
            err_g = (old_g - best_col[1]) * 0.85
            err_b = (old_b - best_col[2]) * 0.85

            if 0 <= x + step < width:
                buf_r[y][x + step] += err_r * (7.0 / 16.0)
                buf_g[y][x + step] += err_g * (7.0 / 16.0)
                buf_b[y][x + step] += err_b * (7.0 / 16.0)
            if y + 1 < height:
                if 0 <= x - step < width:
                    buf_r[y + 1][x - step] += err_r * (3.0 / 16.0)
                    buf_g[y + 1][x - step] += err_g * (3.0 / 16.0)
                    buf_b[y + 1][x - step] += err_b * (3.0 / 16.0)
                buf_r[y + 1][x] += err_r * (5.0 / 16.0)
                buf_g[y + 1][x] += err_g * (5.0 / 16.0)
                buf_b[y + 1][x] += err_b * (5.0 / 16.0)
                if 0 <= x + step < width:
                    buf_r[y + 1][x + step] += err_r * (1.0 / 16.0)
                    buf_g[y + 1][x + step] += err_g * (1.0 / 16.0)
                    buf_b[y + 1][x + step] += err_b * (1.0 / 16.0)

    return out_img


def _render_banner_composition(canvas_w, canvas_h, ox, oy, icon_img, dithered_art):
    """
    Renders the TapInk banner composition onto a canvas of size (canvas_w, canvas_h)
    with logical content offset (ox, oy).
    - Left typography & 3 feature pills at logical X: 56..550 (100% preserved baseline)
    - Right hero illustration at logical X: 560..968, Y: 40..460 with multi-layer shadows,
      specular bevels, and 1:1 pixel-exact 4-color Floyd-Steinberg dithered E-Paper artwork.
    """
    ss = 2
    w, h = canvas_w * ss, canvas_h * ss
    img = Image.new("RGBA", (w, h), (20, 23, 28, 255))
    draw = ImageDraw.Draw(img)

    # Diagonal background gradient (#14171C -> #1F242D)
    for y in range(h):
        t = y / (h - 1)
        r = int(20 * (1 - t) + 31 * t)
        g = int(23 * (1 - t) + 36 * t)
        b = int(28 * (1 - t) + 45 * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Subtle Crimson & Amber ambient glow behind right hero visual (logical X ~ 770, Y ~ 250)
    glow_cx, glow_cy = (ox + 770) * ss, (oy + 250) * ss
    for radius in range(230 * ss, 20 * ss, -8 * ss):
        ratio = 1.0 - (radius / (230.0 * ss))
        r = min(255, int(26 + 38 * ratio))
        g = min(255, int(30 + 22 * ratio))
        b = min(255, int(38 + 10 * ratio))
        draw.ellipse(
            [glow_cx - radius, glow_cy - radius, glow_cx + radius, glow_cy + radius],
            fill=(r, g, b, 255),
        )

    # =========================================================================
    # RIGHT HERO ILLUSTRATION (logical X: 560..968, Y: 40..460):
    # Physical 4-Color E-Paper Desk Sign + Phone Bottom Sheet + NFC Energy Arcs
    # =========================================================================
    card_x0, card_y0 = (ox + 685) * ss, (oy + 78) * ss
    card_x1, card_y1 = (ox + 955) * ss, (oy + 412) * ss

    # 1. Soft multi-layer ambient drop shadow behind E-Paper Desk Sign
    shadow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sh_draw = ImageDraw.Draw(shadow_layer)
    for step, (dx_s, dy_s, expand_s, alpha_s) in enumerate([
        (14, 20, 10, 26),
        (11, 16, 6, 42),
        (8, 12, 2, 68),
        (5, 7, 0, 95),
    ]):
        sh_draw.rounded_rectangle(
            [
                card_x0 + (dx_s - expand_s) * ss,
                card_y0 + (dy_s - expand_s) * ss,
                card_x1 + (dx_s + expand_s) * ss,
                card_y1 + (dy_s + expand_s) * ss,
            ],
            radius=(22 + expand_s) * ss,
            fill=(6, 8, 12, alpha_s),
        )
    img = Image.alpha_composite(img, shadow_layer)
    draw = ImageDraw.Draw(img)

    # Matte Ceramic White Bezel with dual-layer Specular Bevel Outline
    draw.rounded_rectangle(
        [card_x0, card_y0, card_x1, card_y1],
        radius=20 * ss,
        fill=(242, 244, 248, 255),
        outline=(58, 63, 72, 255),
        width=2 * ss,
    )
    # Inner bright specular rim bevel along top-left & subtle shadow bevel along bottom-right
    draw.rounded_rectangle(
        [card_x0 + 2 * ss, card_y0 + 2 * ss, card_x1 - 2 * ss, card_y1 - 2 * ss],
        radius=18 * ss,
        outline=(255, 255, 255, 220),
        width=1 * ss,
    )

    # Inner Warm Ivory E-Paper Screen Frame (logical 234 x 270 -> X: 703..937, Y: 96..366)
    scr_x0, scr_y0 = card_x0 + 18 * ss, card_y0 + 18 * ss
    scr_x1, scr_y1 = card_x1 - 18 * ss, card_y1 - 44 * ss
    # Subtle recessed inner bezel shadow around E-Paper glass panel
    draw.rounded_rectangle(
        [scr_x0 - 2 * ss, scr_y0 - 2 * ss, scr_x1 + 2 * ss, scr_y1 + 2 * ss],
        radius=9 * ss,
        fill=(216, 214, 206, 255),
        outline=(188, 184, 174, 255),
        width=1 * ss,
    )
    draw.rounded_rectangle(
        [scr_x0, scr_y0, scr_x1, scr_y1],
        radius=8 * ss,
        fill=COL_IVORY + (255,),
        outline=(205, 201, 190, 255),
        width=1 * ss,
    )

    # Bottom bezel brand pill & subtle status LED recess
    draw.rounded_rectangle(
        [card_x0 + 95 * ss, card_y1 - 26 * ss, card_x1 - 95 * ss, card_y1 - 14 * ss],
        radius=6 * ss,
        fill=COL_CARBON + (255,),
    )

    # 2. Occlusion Shadow cast by Front Phone Sheet onto the Desk Sign
    ph_x0, ph_y0 = (ox + 590) * ss, (oy + 195) * ss
    ph_x1, ph_y1 = (ox + 755) * ss, (oy + 435) * ss

    # 3. NFC Energy Waves Bridging Phone & E-Paper (logical Center 665, 155)
    wave_cx, wave_cy = (ox + 665) * ss, (oy + 155) * ss
    for r_dp, col in [(22, COL_AMBER), (36, COL_CRIMSON), (50, COL_AMBER)]:
        r_px = r_dp * ss
        draw.arc(
            [wave_cx - r_px, wave_cy - r_px, wave_cx + r_px, wave_cy + r_px],
            start=-45,
            end=35,
            fill=col + (255,),
            width=4 * ss,
        )

    # =========================================================================
    # LEFT TYPOGRAPHY & VALUE PROPOSITION (logical X: 56..550, 100% Frozen Baseline)
    # =========================================================================
    font_brand = load_cjk_font(24 * ss)
    font_tag = load_cjk_font(13 * ss)
    font_h1 = load_cjk_font(42 * ss)
    font_sub = load_cjk_font(18 * ss)

    # Top Brand Badge Row (logical Y: 66)
    mini_icon = icon_img.resize((46 * ss, 46 * ss), Image.Resampling.LANCZOS)
    mask = Image.new("L", (46 * ss, 46 * ss), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, 46 * ss, 46 * ss], radius=11 * ss, fill=255)
    img.paste(mini_icon.convert("RGBA"), ((ox + 56) * ss, (oy + 66) * ss), mask)

    draw.text(((ox + 114) * ss, (oy + 66) * ss), "TapInk 瞬墨", fill=COL_IVORY + (255,), font=font_brand)
    draw.text(((ox + 114) * ss, (oy + 98) * ss), "NFC E-PAPER STUDIO", fill=COL_AMBER + (255,), font=font_tag)

    # Headline (logical Y: 146..250) with Amber highlight on "一觸秒刷"
    draw.text(((ox + 56) * ss, (oy + 146) * ss), "相簿分享，", fill=(255, 255, 255, 255), font=font_h1)
    draw.text(((ox + 56) * ss, (oy + 204) * ss), "一觸秒刷", fill=COL_AMBER + (255,), font=font_h1)
    bbox_hl = draw.textbbox(((ox + 56) * ss, (oy + 204) * ss), "一觸秒刷", font=font_h1)
    draw.text((bbox_hl[2] + 4 * ss, (oy + 204) * ss), "電子紙", fill=(255, 255, 255, 255), font=font_h1)

    # Subheadline (logical Y: 278..335)
    draw.text(
        ((ox + 56) * ss, (oy + 278) * ss),
        "免開 App、自動識別雙色/三色/四色規格",
        fill=(184, 191, 204, 255),
        font=font_sub,
    )
    draw.text(
        ((ox + 56) * ss, (oy + 308) * ss),
        "專為無電池 NFC 電子紙桌牌與名片打造",
        fill=(184, 191, 204, 255),
        font=font_sub,
    )

    # Bottom 3 Feature Pills (logical Y: 372..412)
    font_pill = load_cjk_font(13 * ss)
    pills = [
        ("系統「分享」直達", COL_AMBER),
        ("四色專業抖動", COL_CRIMSON),
        ("100% 純離線零廣告", COL_IVORY),
    ]
    px = (ox + 56) * ss
    py = (oy + 372) * ss
    for text, dot_col in pills:
        bbox = draw.textbbox((0, 0), text, font=font_pill)
        tw = bbox[2] - bbox[0]
        pw = tw + 34 * ss
        ph = 40 * ss
        draw.rounded_rectangle(
            [px, py, px + pw, py + ph],
            radius=20 * ss,
            fill=(35, 41, 52, 255),
            outline=(56, 65, 82, 255),
            width=2 * ss,
        )
        dot_r = 4 * ss
        dot_cx = px + 14 * ss
        dot_cy = py + ph // 2
        draw.ellipse([dot_cx - dot_r, dot_cy - dot_r, dot_cx + dot_r, dot_cy + dot_r], fill=dot_col + (255,))
        draw.text((px + 23 * ss, py + 9 * ss), text, fill=COL_IVORY + (255,), font=font_pill)
        px += pw + 8 * ss

    # Downsample 2x supersampled base image to 1x target canvas
    final_img = img.convert("RGB").resize((canvas_w, canvas_h), Image.Resampling.LANCZOS)

    # =========================================================================
    # 1:1 PIXEL-EXACT DITHERED ARTWORK INJECTION & FOREGROUND PHONE OVERLAY
    # =========================================================================
    # Paste the 222x258 1x Floyd-Steinberg dithered artwork directly at 1:1 pixel ratio
    # into the inner E-Paper screen safe zone (logical X: 709..931, Y: 102..360 -> 222x258)
    # so every single E-Ink pigment dot is 100% crisp with zero resampling Moiré!
    art_x0, art_y0 = ox + 709, oy + 102
    art_mask = Image.new("L", (222, 258), 0)
    art_mask_draw = ImageDraw.Draw(art_mask)
    art_mask_draw.rounded_rectangle([0, 0, 221, 257], radius=5, fill=255)
    final_img.paste(dithered_art, (art_x0, art_y0), art_mask)

    # Now render the foreground Phone Bottom Sheet at 2x supersampling on an overlay
    # (including its multi-layer occlusion shadow over the E-Paper desk sign) and composite cleanly
    fg_overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(fg_overlay)

    # Multi-layer occlusion shadow cast by Phone Sheet onto Desk Sign & background
    for dx_s, dy_s, expand_s, alpha_s in [
        (12, 16, 8, 45),
        (9, 12, 4, 75),
        (6, 8, 1, 115),
        (3, 4, 0, 145),
    ]:
        fg_draw.rounded_rectangle(
            [
                ph_x0 + (dx_s - expand_s) * ss,
                ph_y0 + (dy_s - expand_s) * ss,
                ph_x1 + (dx_s + expand_s) * ss,
                ph_y1 + (dy_s + expand_s) * ss,
            ],
            radius=(24 + expand_s) * ss,
            fill=(6, 8, 12, alpha_s),
        )

    # Phone Sheet Card Body with specular bevel rim
    fg_draw.rounded_rectangle(
        [ph_x0, ph_y0, ph_x1, ph_y1],
        radius=24 * ss,
        fill=(25, 28, 32, 255),
        outline=(78, 90, 110, 255),
        width=2 * ss,
    )
    fg_draw.rounded_rectangle(
        [ph_x0 + 2 * ss, ph_y0 + 2 * ss, ph_x1 - 2 * ss, ph_y1 - 2 * ss],
        radius=22 * ss,
        outline=(120, 135, 160, 55),
        width=1 * ss,
    )

    # Drag handle
    fg_draw.rounded_rectangle(
        [ph_x0 + 68 * ss, ph_y0 + 12 * ss, ph_x1 - 68 * ss, ph_y0 + 16 * ss],
        radius=2 * ss,
        fill=(148, 154, 166, 255),
    )

    # Amber Success Check Circle inside Phone Sheet
    chk_cx, chk_cy, chk_r = (ph_x0 + ph_x1) // 2, ph_y0 + 106 * ss, 40 * ss
    # Subtle warm ambient halo ring around check badge
    fg_draw.ellipse(
        [chk_cx - chk_r - 12 * ss, chk_cy - chk_r - 12 * ss, chk_cx + chk_r + 12 * ss, chk_cy + chk_r + 12 * ss],
        fill=(38, 35, 26, 255),
        outline=COL_AMBER + (255,),
        width=2 * ss,
    )
    fg_draw.ellipse([chk_cx - chk_r, chk_cy - chk_r, chk_cx + chk_r, chk_cy + chk_r], fill=COL_AMBER + (255,))
    # Checkmark lines
    fg_draw.line(
        [
            (chk_cx - 17 * ss, chk_cy + 2 * ss),
            (chk_cx - 4 * ss, chk_cy + 15 * ss),
            (chk_cx + 19 * ss, chk_cy - 13 * ss),
        ],
        fill=COL_CARBON + (255,),
        width=6 * ss,
        joint="curve",
    )

    # Spec pill inside phone sheet
    font_phone_pill = load_cjk_font(13 * ss)
    fg_draw.rounded_rectangle(
        [chk_cx - 58 * ss, ph_y0 + 175 * ss, chk_cx + 58 * ss, ph_y0 + 205 * ss],
        radius=12 * ss,
        fill=(35, 42, 54, 255),
        outline=COL_AMBER + (255,),
        width=1 * ss,
    )
    fg_draw.text(
        (chk_cx - 44 * ss, ph_y0 + 181 * ss),
        "4.2\" 四色 BWRY",
        fill=COL_IVORY + (255,),
        font=font_phone_pill,
    )

    fg_overlay_1x = fg_overlay.resize((canvas_w, canvas_h), Image.Resampling.LANCZOS)
    final_rgba = Image.alpha_composite(final_img.convert("RGBA"), fg_overlay_1x)
    return final_rgba.convert("RGB")


def generate_feature_and_social_graphics(icon_img):
    dithered_art = render_dithered_epaper_landscape(width=222, height=258)

    # 1. Google Play Feature Graphic (1024x500, 24-bit RGB)
    fg_img = _render_banner_composition(
        canvas_w=1024,
        canvas_h=500,
        ox=0,
        oy=0,
        icon_img=icon_img,
        dithered_art=dithered_art,
    )
    fg_path = os.path.join(OUT_DIR, "feature-graphic-1024x500.png")
    fg_img.save(fg_path, "PNG")
    print(f"[OK] Generated {fg_path} (1024x500)")

    # 2. GitHub Social Preview Graphic (1280x640, 24-bit RGB, centered with 128px horizontal & 70px vertical Safe Zone)
    social_img = _render_banner_composition(
        canvas_w=1280,
        canvas_h=640,
        ox=128,
        oy=70,
        icon_img=icon_img,
        dithered_art=dithered_art,
    )
    social_path = os.path.join(OUT_DIR, "social-preview-1280x640.png")
    social_img.save(social_path, "PNG")
    print(f"[OK] Generated {social_path} (1280x640)")


_STATUS_BAR_ALPHA_B85 = (
    "c-rmVd1w@89LDk0nntBjqNEZdqC~U}*hHk2LKS0cW30p~HXe;tN>y6hY82|7AV@{gQb>v7(RwRdv1l7f8;Y%pC^Yd_6l)D?)F>X&"
    "B<7mVZ)SG0$LwTg-LX`^pFi}S_nm!rvNJsFyzk7c#{&QW0000000000000000000000000z>|2fYw9~=r!V+o!q8r=quTM$rMe@0"
    "#=x^AHgm=CCdH?=Y|ET?W1`i0xwlt7XW*F;le4cz301lBHQQahe(-VuxdA*kUYc8>gmWRklfBrutSGllTT-&JvThl@Jyy$a@PPyW"
    "d+`hJC|0VLw6_-<ielEXH>AVAT>3-0jsI%NC#Eg0&e>K}ylZJjKxM~|*Y7RfvM8A&0C1|+{A#5o*T>t74YS;6(-_)cdl&wWq-E!1"
    "bf(CpDS6Lh<BiXZYp$JJsn^wS>da99IMS{}vEy)OJF#Jw8|_Sb>lFRJW2TV;Cb==>R`WYIt?<@vY_2?=z;OULF-o!LOFOag-{r<S"
    "Z`Yx@(ML~|ntS>RpX;?tw1sND@>h-nz=`*4=EfvDv0;@P=kyCW>Ric<LhUUsZph6U5Yo0=nvQ11czPDf>B?2DGEGj-DyPMGMx2-C"
    "k2nwjCyH&w#<3Ue#D-OFbk)xpT=~8+Nk%qqO)+ij{77v(q+f`#O5S{gls3q~hBi#vUAF!>#{u9(hYL1h<9dRv*s#crYJKUL8@Um8"
    "P3`kqnt9_$c|&73w3n0@OG)OP_ZXog!ya#OAOKGEyx(GMRHxaC4U613<$qQb<=mJjUmk`B=f(CNqvZeeZK@td<pXt1xj3z#Y{g`w"
    "Qie^JavT7Tq&Kt>8%<d@W5Xghe$p1sxsn^l)h>4h)n(KxN<(m#cpe$wH^$0V^ffB~P|Lq?7y!;pZy`4p*o_T~+*mH<iLT72q{tN_"
    ">7H&=^75v1_g1c!_gNdX<`}7zQH`lmNHS`FRLi9t1%N|qEn{P+?bxu$4eg}hiYvJ>OYN~dbzi;oTvyabul!n;mIbbubXG=qr8Hhz"
    "y;ConWGaS(0C4DqLsnBMv9@EwA~*W`f3JydZfsV2yj1D!`(}TwvdalYIhE*JXYkuYvzN>8>BAw~hRd3>{Bon5g8*<U{_jYpQbJ-Q"
    "d~P(hQHy)z0*>ma=f<Cb+i*^wbB!4^$nPlt4~@o(k@~X4%ptuPcS$W@;TQlM>s{r1DkU^F!so_S@8YngsON@u<(6`JVP?j1ZE9$t"
    "$vQ@ETpMUCZ<nP{rPRJiyX%)j0B|m&(L6V@oWw@>+&E|~b+{S*^Azpb9>sA|94g;t&WFzp<+1UIkbyE9Qnzm8u=y9Ydd5Y8NX#;g"
    "jYW=PBYbY$DJ?xLOS@ed-?*Xn_)kZ@yvBdP)6Zu!a`SiGmd15@#rJ_5zMlgCh{R?`Qz@pgVR>I$iMo)jxRe_|807)d{p>I+CjKrX"
    "E$Ldh?Qh=gI&)P0XBGP&h(t{2q)J(=v)HiAjaeSg%lF*K4cU~9-US)qSQ&23#Jg(iR%0Pgn-#uO5HI5+Q`!GOMB>YXr&1Cf#)f5X"
    "boF=+xH1oW(*JT%nQJW!yQ++Vp7kxgnKLaj#?!k|QR=&~`+>-$RN0(L2^Sld_tCp{{f2~r@Ann%1>bCvker<CyNXn9gRKa9d4cz0"
    "FaVLrXtFhx5;iuha-+c0<B>bLA&=mBV2q1u7jy-jbjV2QP4B|)&`U#8?^}$B0BG4<zYNHC9vfD<F~n2q((}IhH#Bdpq(no&NylP&"
    "!@<T7j+}`d2zYVwDVdFlW7h+b+u8C|ibZT#<i`D0^IUsQpbVQ-WlAZl#`n}e?|t;z3`<!Tt*$UQ591fecu3=i?0Db_#2#yDD#a=`"
    "EOKLS?;6)~V^FO;KXlE6ob}bd$9Og0N3UImUcPkH@LX-esKR^Q{KTzA$7>an+ur~<lyKd8D#bE3EOKMoNf)0>oaa9-^Nm*3d>?(k"
    "8~$5Z<NHa6)kaMt&+Gx7l=j5l+O&vb!{WZS0#~1Bo?qvC#?W_qjo*Fr{ZIH!EsymC<;L03>~r9WjjmJbv%@tODUs#I-FkO_2Vqc&"
    "cFIwI<b9(e;6D1<W%}JW<=-ZG{BxuF^!73Aa3C69nUNB<&HC$x+3&-)eLBCp<Vnn)F>{>XOiJK=^xY=p&Ys*i-~~NX6WQ0m(@=2R"
    "A~wu_k3RG#s~@-Kg9Kn@*#6Q$B_Hsi00000000000000000000000000001<L;nIJ?()O"
)


def _load_wallpaper_1080():
    wp_path = os.path.join(SCREENSHOT_DIR, "wallpaper-backdrop.jpg")
    wp_orig = Image.open(wp_path).convert("RGB")
    scale = max(1080.0 / wp_orig.width, 2400.0 / wp_orig.height)
    new_w = int(round(wp_orig.width * scale))
    new_h = int(round(wp_orig.height * scale))
    wp_resized = wp_orig.resize((new_w, new_h), Image.Resampling.LANCZOS)
    left = (new_w - 1080) // 2
    top = (new_h - 2400) // 2
    return wp_resized.crop((left, top, left + 1080, top + 2400))


def _compute_backdrop_pixel(wp_pix, x, y, y_top):
    wr, wg, wb = wp_pix[x, y]
    if x < 70:
        sheet_edge_y = (y_top + 70.0) - math.sqrt(max(0.0, 70.0 ** 2 - (x - 70.0) ** 2))
    elif x > 1009:
        sheet_edge_y = (y_top + 70.0) - math.sqrt(max(0.0, 70.0 ** 2 - (x - 1009.0) ** 2))
    else:
        sheet_edge_y = float(y_top)
    dist = sheet_edge_y - float(y)
    if 0.0 <= dist <= 56.0:
        s_t = (1.0 - dist / 56.0) ** 2.2
        shadow_mul = 1.0 - 0.18 * s_t
        wr *= shadow_mul
        wg *= shadow_mul
        wb *= shadow_mul
    return (
        int(round(max(0.0, min(255.0, wr)))),
        int(round(max(0.0, min(255.0, wg)))),
        int(round(max(0.0, min(255.0, wb)))),
    )


def refine_screenshot_backdrops():
    """
    Adaptive Sandwich Wallpaper Compositing for docs/screenshots/01~04.png:
    1. Dynamically detects along X = 540:
       - Bottom sheet top edge y_top (1205 for 01/02, 1352 for 03/04)
    2. Composites the edge-to-edge bright frosted glass wallpaper (docs/screenshots/wallpaper-backdrop.jpg)
       across Y in [0, y_top + 70) with dark Android 15 status bar icons in Y: 0..62 and a soft Material 3
       elevation shadow above the bottom sheet.
    3. Strictly idempotent (running 1 or N times produces bit-identical PNGs).
    """
    screenshot_files = [
        "01-waiting-nfc.png",
        "02-error-retry.png",
        "03-transferring-290bw.png",
        "04-transferring-420bwry.png",
    ]

    wp_1080 = _load_wallpaper_1080()
    wp_pix = wp_1080.load()
    assert wp_pix is not None
    status_alpha = zlib.decompress(base64.b85decode(_STATUS_BAR_ALPHA_B85))
    col_status_dark = (28, 30, 33)

    r_corner = 70.0
    x_left_c = 70.0
    x_right_c = 1009.0
    y_status = 63

    for fname in screenshot_files:
        fpath = os.path.join(SCREENSHOT_DIR, fname)
        if not os.path.exists(fpath):
            continue

        img = Image.open(fpath).convert("RGB")
        w, h = img.size
        assert (w, h) == (1080, 2400), f"Unexpected screenshot dimensions for {fname}: {w}x{h}"
        pixels = img.load()
        assert pixels is not None

        # 1. Dynamic boundary detection along center axis X = 540
        y_top = None
        for y in range(500, h):
            if pixels[540, y] == COL_SHEET:
                y_top = y
                break
        if y_top is None:
            raise RuntimeError(f"Could not detect bottom sheet top edge COL_SHEET={COL_SHEET} in {fname}")

        y_full = y_top + 70
        y_center = float(y_full)

        # 2. Edge-to-edge status bar (Y: 0..62) with dark icons over bright wallpaper
        for y in range(y_status):
            row_off = y * 1080
            for x in range(w):
                icon_a = status_alpha[row_off + x] / 255.0
                br, bg, bb = _compute_backdrop_pixel(wp_pix, x, y, y_top)
                pixels[x, y] = (
                    int(round(icon_a * col_status_dark[0] + (1.0 - icon_a) * br)),
                    int(round(icon_a * col_status_dark[1] + (1.0 - icon_a) * bg)),
                    int(round(icon_a * col_status_dark[2] + (1.0 - icon_a) * bb)),
                )

        # 3. Upper backdrop replacement: Y in [y_status, y_top)
        for y in range(y_status, y_top):
            for x in range(w):
                pixels[x, y] = _compute_backdrop_pixel(wp_pix, x, y, y_top)

        # 4. Rounded corner transition zone: Y in [y_top, y_full)
        for y in range(y_top, y_full):
            for x in range(w):
                if x < 70:
                    d = math.hypot(x - x_left_c, y - y_center)
                elif x > 1009:
                    d = math.hypot(x - x_right_c, y - y_center)
                else:
                    continue

                if d <= r_corner - 1.0:
                    continue

                alpha = max(0.0, min(1.0, r_corner - d))
                c_scrim = _compute_backdrop_pixel(wp_pix, x, y, y_top)
                out_r = int(round(alpha * COL_SHEET[0] + (1.0 - alpha) * c_scrim[0]))
                out_g = int(round(alpha * COL_SHEET[1] + (1.0 - alpha) * c_scrim[1]))
                out_b = int(round(alpha * COL_SHEET[2] + (1.0 - alpha) * c_scrim[2]))
                pixels[x, y] = (out_r, out_g, out_b)

        img.save(fpath, "PNG")
        print(f"[OK] Refined screenshot backdrop: {fpath} (y_status={y_status}, y_top={y_top})")


if __name__ == "__main__":
    icon = generate_icons()
    generate_feature_and_social_graphics(icon)
    refine_screenshot_backdrops()
