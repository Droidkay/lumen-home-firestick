"""Draws Lumen's built-in wallpaper scenes (1280x720 JPEG)."""
import math
import sys

import numpy as np
from PIL import Image, ImageFilter

OUT = sys.argv[1]
W, H = 1280, 720
rng = np.random.default_rng(7)


def lerp(a, b, t):
    return a + (b - a) * t


def vgrad(stops):
    """stops: list of (pos 0..1, (r,g,b)). Returns HxWx3 float."""
    ys = np.linspace(0, 1, H)
    out = np.zeros((H, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, [s[0] for s in stops], [s[1][c] for s in stops])
    return np.repeat(out[:, None, :], W, axis=1)


def stars(img, n, ymax=1.0, bright=1.0):
    for _ in range(n):
        x = rng.integers(0, W)
        y = int(rng.random() ** 1.6 * H * ymax)
        b = rng.random() ** 3 * 255 * bright
        img[y, x] = np.maximum(img[y, x], b)
        if b > 170 and 0 < x < W - 1 and 0 < y < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[y + dy, x + dx] = np.maximum(img[y + dy, x + dx], b * 0.35)
    return img


def glow(img, cx, cy, r, color, strength):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    g = np.exp(-d ** 2) * strength
    return img + g[..., None] * (np.array(color) / 255.0)[None, None, :]


def ridge(base, amp, octaves, seed, rough=0.5):
    r = np.random.default_rng(seed)
    xs = np.linspace(0, 1, W)
    y = np.full(W, base, dtype=float)
    a = amp
    f = 2
    for _ in range(octaves):
        ph = r.random() * 10
        pts = r.random(f + 2)
        y += np.interp(xs * (f + 1), np.arange(f + 2), pts - 0.5) * a
        a *= rough
        f *= 2
    return y


def fill_below(img, yline, color, fog_color=None, fog=0.0):
    yy = np.arange(H)[:, None]
    mask = yy >= yline[None, :]
    col = np.array(color, dtype=float)
    if fog_color is not None:
        # slight vertical fade inside the layer for depth
        depth = np.clip((yy - yline[None, :]) / 140.0, 0, 1)
        layer = col[None, None, :] * (1 - fog * (1 - depth[..., None])) + np.array(fog_color)[None, None, :] * fog * (1 - depth[..., None])
    else:
        layer = np.broadcast_to(col, (H, W, 3))
    img[mask] = layer[mask] if layer.ndim == 3 else col
    return img


def save(img, name, blur=0):
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    # gentle grain to avoid banding
    arr = np.asarray(im).astype(float) + rng.normal(0, 1.6, (H, W, 1))
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).save(f"{OUT}/{name}.jpg", quality=86, optimize=True, progressive=False)


# 1. Dusk Peaks
img = vgrad([(0, (14, 12, 40)), (0.35, (52, 30, 92)), (0.58, (196, 92, 120)), (0.70, (250, 160, 110)), (1, (40, 20, 40))])
img = stars(img, 900, ymax=0.45)
img = glow(img, W * 0.62, H * 0.66, 220, (255, 170, 110), 70)
layers = [
    (0.60, 120, (122, 70, 120), 3),
    (0.66, 140, (82, 44, 92), 11),
    (0.74, 150, (48, 26, 64), 21),
    (0.84, 120, (22, 12, 32), 31),
]
for base, amp, col, seed in layers:
    y = ridge(base * H, amp, 6, seed, 0.48)
    img = fill_below(img, y, col, fog_color=(230, 140, 150), fog=0.35)
save(img, "scene_peaks", blur=0.6)

# 2. Aurora
img = vgrad([(0, (3, 8, 18)), (0.5, (6, 24, 34)), (0.78, (10, 36, 44)), (1, (2, 6, 10))])
img = stars(img, 1400, ymax=0.8)
aur = np.zeros((H, W))
xs = np.arange(W)
for k, (amp, y0, phase, width) in enumerate([(70, 0.30, 0.0, 130), (55, 0.38, 2.0, 100), (40, 0.24, 4.1, 80)]):
    curve = y0 * H + amp * np.sin(xs / W * 2 * math.pi * 1.3 + phase) + 25 * np.sin(xs / W * 2 * math.pi * 4.2 + phase * 2)
    streak = 0.55 + 0.45 * np.sin(xs * 0.09 + phase) * np.sin(xs * 0.023)
    yy = np.arange(H)[:, None]
    dist = (curve[None, :] - yy)
    curtain = np.where(dist > 0, np.exp(-dist / width), np.exp(-(dist / 10) ** 2))
    aur += curtain * streak[None, :] * (1.0 - 0.25 * k)
aur = np.clip(aur, 0, 1.4)
img += aur[..., None] * np.array([40, 210, 150])[None, None, :] * 0.85
img += (aur ** 3)[..., None] * np.array([120, 60, 200])[None, None, :] * 0.25
y = ridge(0.80 * H, 70, 7, 5, 0.55)
img = fill_below(img, y, (4, 12, 16))
save(img, "scene_aurora", blur=1.2)

# 3. Nebula
img = vgrad([(0, (6, 4, 18)), (1, (2, 2, 8))])
neb = np.zeros((H, W, 3))
for _ in range(70):
    cx, cy = rng.normal(W * 0.6, W * 0.28), rng.normal(H * 0.45, H * 0.28)
    r = rng.uniform(40, 220)
    col = [(140, 60, 220), (60, 120, 255), (255, 70, 150), (40, 200, 230)][rng.integers(0, 4)]
    neb = glow(neb, cx, cy, r * 0.8, col, rng.uniform(8, 30))
img += neb
img = stars(img, 2200, ymax=1.0, bright=1.0)
save(img, "scene_nebula", blur=0.8)

# 4. Ocean Night
img = vgrad([(0, (4, 10, 30)), (0.55, (20, 46, 92)), (0.62, (36, 70, 120)), (0.63, (10, 26, 52)), (1, (2, 8, 18))])
img = stars(img, 800, ymax=0.5)
img = glow(img, W * 0.72, H * 0.24, 60, (255, 250, 230), 140)
img = glow(img, W * 0.72, H * 0.24, 220, (120, 160, 230), 30)
yy, xx = np.mgrid[0:H, 0:W]
moon = ((xx - W * 0.72) ** 2 + (yy - H * 0.24) ** 2) < 26 ** 2
img[moon] = [250, 248, 236]
sea = yy > H * 0.625
ripple = (np.sin(yy * 0.9 + np.sin(xx * 0.05 + yy * 0.02) * 3) * 0.5 + 0.5) * (0.6 + 0.4 * np.sin(xx * 0.31 + yy * 0.7))
path = np.exp(-((xx - W * 0.72) / (40 + (yy - H * 0.625) * 0.9)) ** 2)
refl = path * ripple * np.clip((yy - H * 0.625) / 60, 0, 1) * 150
img[sea] += (refl[..., None] * np.array([0.9, 0.95, 1.0])[None, None, :])[sea]
save(img, "scene_ocean", blur=0.7)
print("scenes ok")
