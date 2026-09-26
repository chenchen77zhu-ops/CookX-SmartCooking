"""Build the live-kitchen state backdrops from one pan photograph.

Usage: python frontend/scripts/generate-kitchen-backdrops.py SOURCE.png
Writes frontend/src/assets/backdrops/stage-{idle,preheat,heating,sear,overheat}.webp.

The source is the original high-resolution home hero photo (git history before
the WebP conversion). Each state is a colour grade plus light and haze layers;
steam and smoke are animated at runtime on top of these stills. Real photographs
of each state can replace the outputs with the same file names.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter

W, H = 900, 1950
OUT = Path(__file__).resolve().parents[1] / 'src' / 'assets' / 'backdrops'


def vertical_mask(width, height, fade_top, fade_bottom):
    mask = Image.new('L', (1, height), 255)
    for y in range(height):
        a = 255
        if y < fade_top:
            a = int(255 * (y / fade_top) ** 1.6)
        if y > height - fade_bottom:
            a = min(a, int(255 * ((height - y) / fade_bottom) ** 1.4))
        mask.putpixel((0, y), a)
    return mask.resize((width, height))


def bokeh_layer(seed):
    rnd = random.Random(seed)
    base = Image.new('RGB', (W, H), (14, 12, 10))
    top = Image.linear_gradient('L').resize((W, H))
    base = Image.composite(Image.new('RGB', (W, H), (9, 9, 8)), Image.new('RGB', (W, H), (30, 24, 18)), top)
    glow = Image.new('RGB', (W, H), (0, 0, 0))
    draw = ImageDraw.Draw(glow)
    palette = [(255, 176, 96), (255, 204, 140), (120, 150, 110), (255, 140, 70), (200, 190, 170)]
    for _ in range(26):
        x, y = rnd.randint(-60, W + 60), rnd.randint(40, 820)
        r = rnd.randint(28, 120)
        c = rnd.choice(palette)
        k = rnd.uniform(0.18, 0.55)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=tuple(int(v * k) for v in c))
    glow = glow.filter(ImageFilter.GaussianBlur(38))
    return ImageChops.add(base, glow)


def composite(source):
    photo = Image.open(source).convert('RGB')
    pan = photo.crop((760, 30, 1855, 848))
    scale = 1240 / pan.width
    pan = pan.resize((1240, int(pan.height * scale)), Image.LANCZOS)
    canvas = bokeh_layer(7)
    y0 = 560
    mask = vertical_mask(pan.width, pan.height, 220, 200)
    canvas.paste(pan, (-195, y0), mask)
    floor = Image.new('RGB', (W, H - (y0 + pan.height - 200)), (8, 9, 8))
    fade = Image.linear_gradient('L').resize(floor.size)
    canvas.paste(floor, (0, y0 + pan.height - 200), fade)
    return canvas


def radial(color, center, radius, strength):
    layer = Image.new('RGB', (W, H), (0, 0, 0))
    ImageDraw.Draw(layer).ellipse((center[0] - radius[0], center[1] - radius[1], center[0] + radius[0], center[1] + radius[1]), fill=tuple(int(v * strength) for v in color))
    return layer.filter(ImageFilter.GaussianBlur(min(radius) * 0.6))


def haze(color, top, bottom, strength):
    layer = Image.new('RGB', (W, H), (0, 0, 0))
    grad = Image.linear_gradient('L').resize((W, bottom - top))
    grad = Image.eval(grad, lambda v: int((255 - v) * strength))
    layer.paste(Image.new('RGB', (W, bottom - top), color), (0, top), grad)
    return layer.filter(ImageFilter.GaussianBlur(40))


def tint(image, r, g, b):
    rr, gg, bb = image.split()
    return Image.merge('RGB', (rr.point(lambda v: min(255, int(v * r))), gg.point(lambda v: min(255, int(v * g))), bb.point(lambda v: min(255, int(v * b)))))


def grade(base, state):
    img = base
    if state == 'idle':
        img = ImageEnhance.Color(img).enhance(0.45)
        img = ImageEnhance.Brightness(img).enhance(0.62)
        img = tint(img, 0.92, 0.98, 1.08)
    elif state == 'preheat':
        img = ImageEnhance.Color(img).enhance(0.8)
        img = ImageEnhance.Brightness(img).enhance(0.8)
        img = ImageChops.add(img, radial((255, 120, 40), (450, 1320), (420, 120), 0.25))
    elif state == 'heating':
        img = ImageEnhance.Brightness(img).enhance(0.95)
        img = ImageChops.add(img, radial((255, 120, 40), (450, 1330), (460, 140), 0.45))
        img = ImageChops.screen(img, haze((180, 170, 160), 380, 900, 0.18))
    elif state == 'sear':
        img = ImageEnhance.Color(img).enhance(1.15)
        img = ImageEnhance.Contrast(img).enhance(1.08)
        img = tint(img, 1.08, 1.0, 0.9)
        img = ImageChops.add(img, radial((255, 110, 30), (450, 1330), (500, 170), 0.7))
        img = ImageChops.screen(img, haze((210, 200, 190), 300, 950, 0.32))
    elif state == 'overheat':
        img = ImageEnhance.Contrast(img).enhance(1.1)
        img = tint(img, 1.18, 0.72, 0.66)
        img = ImageChops.add(img, radial((255, 50, 20), (450, 1320), (560, 220), 0.85))
        img = ImageChops.screen(img, haze((120, 110, 105), 120, 1000, 0.55))
    return img


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    base = composite(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for state in ('idle', 'preheat', 'heating', 'sear', 'overheat'):
        grade(base, state).save(OUT / f'stage-{state}.webp', quality=74, method=6)
        print('wrote', OUT / f'stage-{state}.webp')


if __name__ == '__main__':
    main()
