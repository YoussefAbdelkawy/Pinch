"""Builds the README images from emulator captures.

Inputs (made with the debug build's demo hook, see README "Build from source"):
  build/media/card_<theme>.png   the widget cropped from the home screen, one per theme
  build/media/app_top.png, app_mid.png, app_skins.png   full app screenshots

Run from the repo root:  python tools/make_docs_images.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
MEDIA = ROOT / "build" / "media"
DOCS = ROOT / "docs"
FONTS = Path("C:/Windows/Fonts")

BG_TOP = (20, 16, 30)
BG_BOTTOM = (34, 24, 48)
TEXT = (244, 244, 245)
MUTED = (161, 161, 170)
ACCENT = (215, 119, 87)

THEMES = [("halloween", "Halloween"), ("christmas", "Christmas"), ("valentine", "Valentine"),
          ("spring", "Spring"), ("summer", "Summer"), ("classic", "Classic"), ("oled", "OLED black"),
          ("midnight", "Midnight"), ("space", "Space"), ("ocean", "Underwater"), ("cyberpunk", "Cyberpunk")]


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        k = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * k) for a, b in zip(BG_TOP, BG_BOTTOM)))
    return img


def shadowed(canvas, img, xy, radius=28, offset=14, blur=26):
    """Pastes an RGBA image with a soft drop shadow."""
    x, y = xy
    shadow = Image.new("RGBA", (img.width + blur * 4, img.height + blur * 4), (0, 0, 0, 0))
    mask = img.split()[3] if img.mode == "RGBA" else Image.new("L", img.size, 255)
    shadow.paste((0, 0, 0, 150), (blur * 2, blur * 2), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(shadow, (x - blur * 2, y - blur * 2 + offset))
    canvas.alpha_composite(img.convert("RGBA"), (x, y))


def phone(screen, width):
    """A screenshot in a simple dark phone frame with rounded corners."""
    s = screen.convert("RGB").resize((width, int(screen.height * width / screen.width)), Image.LANCZOS)
    bezel, r = int(width * 0.035), int(width * 0.11)
    w, h = s.width + bezel * 2, s.height + bezel * 2
    frame = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=r, fill=(12, 12, 14, 255), outline=(60, 60, 68, 255), width=2)
    m = Image.new("L", s.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s.width - 1, s.height - 1), radius=r - bezel, fill=255)
    frame.paste(s, (bezel, bezel), m)
    return frame


def card(theme, width):
    c = Image.open(MEDIA / f"card_{theme}.png").convert("RGBA")
    return c.resize((width, int(c.height * width / c.width)), Image.LANCZOS)


def banner():
    W, H = 1600, 820
    img = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(img)
    d.text((90, 150), "Pinch", font=font("segoeuib.ttf", 112), fill=TEXT)
    d.text((96, 300), "Your Claude limits on your home screen.", font=font("segoeui.ttf", 38), fill=TEXT)
    lines = ["Session and weekly usage, what is left,", "and when it resets. A pixel crab lives on it,",
             "with a scene that changes with the seasons."]
    for i, line in enumerate(lines):
        d.text((96, 380 + i * 46), line, font=font("segoeui.ttf", 30), fill=MUTED)
    d.rounded_rectangle((96, 560, 96 + 240, 560 + 6), radius=3, fill=ACCENT)
    d.text((96, 600), "Android 8+   |   Free and open source   |   No tracking", font=font("segoeui.ttf", 26), fill=MUTED)
    shadowed(img, card("halloween", 700), (830, 120))
    shadowed(img, card("christmas", 420), (1110, 520))
    img.convert("RGB").save(DOCS / "banner.png", optimize=True)


def themes():
    cols, cw, gap, top = 3, 480, 40, 40
    rows = (len(THEMES) + cols - 1) // cols
    ch = card("halloween", cw).height
    W = cols * cw + (cols + 1) * gap
    H = top + rows * (ch + 70) + gap
    img = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(img)
    for i, (tid, name) in enumerate(THEMES):
        x = gap + (i % cols) * (cw + gap)
        y = top + (i // cols) * (ch + 70)
        shadowed(img, card(tid, cw), (x, y), blur=14, offset=8)
        d.text((x + 4, y + ch + 14), name, font=font("segoeuib.ttf", 26), fill=TEXT)
    img.convert("RGB").save(DOCS / "themes.png", optimize=True)


def app():
    shots = [("app_top.png", "Sign in and meet the crab"), ("app_mid.png", "Pick a theme"),
             ("app_skins.png", "Pick a skin")]
    pw, gap = 380, 70
    frames = [phone(Image.open(MEDIA / f), pw) for f, _ in shots]
    W = len(frames) * frames[0].width + (len(frames) + 1) * gap
    H = frames[0].height + 170
    img = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(img)
    for i, (f, caption) in enumerate(zip(frames, [c for _, c in shots])):
        x = gap + i * (f.width + gap)
        shadowed(img, f, (x, 60), blur=18)
        tw = d.textlength(caption, font=font("segoeuib.ttf", 28))
        d.text((x + (f.width - tw) / 2, 60 + f.height + 40), caption, font=font("segoeuib.ttf", 28), fill=TEXT)
    img.convert("RGB").save(DOCS / "app.png", optimize=True)


def widget():
    c = Image.open(MEDIA / "card_halloween.png").convert("RGBA")
    c.save(DOCS / "widget.png", optimize=True)


if __name__ == "__main__":
    DOCS.mkdir(exist_ok=True)
    banner()
    themes()
    app()
    widget()
