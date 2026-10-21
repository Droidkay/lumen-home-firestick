"""Draws Lumen's app icon and Fire TV banner: an original 'glass lens' mark."""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

OUT = sys.argv[1]
FONT = "/usr/share/fonts/opentype/inter/InterDisplay-Medium.otf"
SS = 4


def backdrop(w, h):
    """Deep navy with coloured light pools."""
    ys = np.linspace(0, 1, h)[:, None]
    xs = np.linspace(0, 1, w)[None, :]
    t = np.clip(ys * 0.8 + xs * 0.2, 0, 1)
    top = np.array([12, 16, 46.0])
    bot = np.array([6, 8, 22.0])
    img = top * (1 - t[..., None]) + bot * t[..., None]

    def pool(cx, cy, r, col, a):
        d2 = ((xs * w - cx) ** 2 + (ys * h - cy) ** 2) / (r * r)
        return np.exp(-d2)[..., None] * np.array(col)[None, None, :] * a

    m = min(w, h)
    img = img + pool(w * 0.22, h * 0.18, m * 0.55, (255, 70, 170), 0.85)
    img = img + pool(w * 0.85, h * 0.85, m * 0.60, (40, 190, 255), 0.85)
    img = img + pool(w * 0.70, h * 0.25, m * 0.40, (130, 90, 255), 0.75)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGBA")


def lens(base, cx, cy, r):
    """Thick glass disc: refracts (magnifies + blurs) what's behind, rim light, specular."""
    W, H = base.size
    r = int(r)
    box = (int(cx) - r, int(cy) - r, int(cx) - r + 2 * r, int(cy) - r + 2 * r)
    # refraction: sample a slightly smaller area and scale it up, then blur
    inset = int(r * 0.18)
    region = base.crop((box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset))
    region = region.resize((box[2] - box[0], box[3] - box[1]), Image.LANCZOS)
    region = region.filter(ImageFilter.GaussianBlur(r * 0.10))
    # brighten slightly, glass gathers light
    region = Image.blend(region, Image.new("RGBA", region.size, (255, 255, 255, 255)), 0.16)

    d = 2 * r
    mask = Image.new("L", (int(d), int(d)), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, d - 1, d - 1], fill=255)

    # inner shading: lighter top, darker bottom edge
    shade = Image.new("RGBA", (int(d), int(d)), (0, 0, 0, 0))
    arr = np.zeros((int(d), int(d), 4))
    yy, xx = np.mgrid[0:int(d), 0:int(d)]
    dist = np.sqrt((xx - r) ** 2 + (yy - r) ** 2) / r
    edge = np.clip((dist - 0.72) / 0.28, 0, 1) ** 1.6
    vert = (yy / d)
    # dark rim at bottom, light rim at top
    arr[..., 0:3] = np.where(vert[..., None] < 0.5, 255, 0)
    arr[..., 3] = edge * np.where(vert < 0.5, (0.5 - vert) * 2 * 150, (vert - 0.5) * 2 * 90)
    shade = Image.fromarray(arr.astype(np.uint8), "RGBA")
    region.alpha_composite(shade)

    # specular highlight: soft ellipse top-left
    spec = Image.new("RGBA", (int(d), int(d)), (0, 0, 0, 0))
    sd = ImageDraw.Draw(spec)
    sd.chord([d * 0.10, d * 0.04, d * 0.90, d * 0.62], 195, 345, fill=(255, 255, 255, 70))
    spec = spec.filter(ImageFilter.GaussianBlur(d * 0.05))
    region.alpha_composite(spec)

    # small glowing core: the 'light' in Lumen
    core = Image.new("RGBA", (int(d), int(d)), (0, 0, 0, 0))
    cd = ImageDraw.Draw(core)
    cr = d * 0.085
    cd.ellipse([r - cr * 3.2, r - cr * 3.2, r + cr * 3.2, r + cr * 3.2], fill=(255, 255, 255, 90))
    core = core.filter(ImageFilter.GaussianBlur(d * 0.07))
    hot = Image.new("RGBA", (int(d), int(d)), (0, 0, 0, 0))
    ImageDraw.Draw(hot).ellipse([r - cr * 1.3, r - cr * 1.3, r + cr * 1.3, r + cr * 1.3], fill=(255, 255, 255, 235))
    hot = hot.filter(ImageFilter.GaussianBlur(d * 0.025))
    core.alpha_composite(hot)
    region.alpha_composite(core)

    # drop shadow under the lens
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([box[0], box[1] + r * 0.10, box[2], box[3] + r * 0.10], fill=(0, 0, 0, 120))
    sh = sh.filter(ImageFilter.GaussianBlur(r * 0.12))
    base.alpha_composite(sh)

    base.paste(region, box[:2], mask)

    # crisp rim stroke with light gradient (bright top-left)
    rim = Image.new("RGBA", (int(d), int(d)), (0, 0, 0, 0))
    ImageDraw.Draw(rim).ellipse([1, 1, d - 2, d - 2], outline=(255, 255, 255, 255), width=max(2, int(d * 0.012)))
    ra = np.asarray(rim).astype(float)
    grad = np.clip(1.15 - (xx + yy) / (2 * d) * 1.6, 0.12, 1.0)
    grad = np.maximum(grad, np.clip((xx + yy) / (2 * d) - 0.75, 0, 1) * 1.6)
    ra[..., 3] *= grad
    rim = Image.fromarray(ra.astype(np.uint8), "RGBA")
    base.alpha_composite(rim, box[:2])
    return base


def rounded(img, rad):
    w, h = img.size
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], rad, fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(img, (0, 0), m)
    return out


def icon(px):
    S = px * SS
    b = backdrop(S, S)
    b = lens(b, S / 2, S / 2, S * 0.30)
    return rounded(b, int(S * 0.225)).resize((px, px), Image.LANCZOS)


def banner(w, h):
    W, H = w * SS, h * SS
    b = backdrop(W, H)
    r = H * 0.27
    cx = W * 0.20 + r
    b = lens(b, cx, H / 2, r)
    d = ImageDraw.Draw(b)
    f1 = ImageFont.truetype(FONT, int(H * 0.24))
    f2 = ImageFont.truetype(FONT, int(H * 0.095))
    tx = cx + r + W * 0.05
    d.text((tx, H * 0.30), "Lumen", font=f1, fill=(255, 255, 255, 255))
    d.text((tx + 4, H * 0.60), "H O M E", font=f2, fill=(255, 255, 255, 160))
    return b.resize((w, h), Image.LANCZOS)


icon(96).save(f"{OUT}/drawable-xhdpi/ic_launcher.png")
banner(320, 180).save(f"{OUT}/drawable-xhdpi/banner.png")
icon(512).save(f"{OUT}/../preview_icon.png")
banner(960, 540).save(f"{OUT}/../preview_banner.png")
print("art ok")
