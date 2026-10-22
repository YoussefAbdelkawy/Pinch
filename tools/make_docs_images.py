"""Builds the README images from emulator captures.

Inputs (made with the debug build's demo hook, see README "Build from source"):
  build/media/card_<theme>.png   the widget cropped from the home screen, one per theme
  build/media/a1.png, a2.png, a4.png   full app screenshots (signed in via the demo hook: --ez app true)

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


def clean_status_bar(screen):
    """Replaces the emulator's status bar with a tidy one: 9:41, signal, wifi, battery."""
    s = screen.convert("RGB").copy()
    h = 110 * s.width // 1080
    bg = s.getpixel((s.width // 2, h + 4))
    d = ImageDraw.Draw(s)
    d.rectangle((0, 0, s.width, h), fill=bg)
    k = s.width / 1080
    d.text((int(70 * k), int(32 * k)), "9:41", font=font("segoeuib.ttf", int(40 * k)), fill=TEXT)
    # Signal bars, wifi and battery, drawn as simple shapes.
    x0, base = int(860 * k), int(78 * k)
    for i in range(4):
        bh = int((10 + i * 8) * k)
        d.rectangle((x0 + i * int(11 * k), base - bh, x0 + i * int(11 * k) + int(7 * k), base), fill=TEXT)
    cx, cy = int(925 * k), int(80 * k)
    for r in (34, 22, 10):
        d.arc((cx - r * k, cy - r * k, cx + r * k, cy + r * k), 225, 315, fill=TEXT, width=max(2, int(6 * k)))
    bx = int(965 * k)
    d.rounded_rectangle((bx, int(46 * k), bx + int(62 * k), int(80 * k)), radius=int(8 * k), outline=TEXT, width=max(2, int(4 * k)))
    d.rectangle((bx + int(7 * k), int(53 * k), bx + int(46 * k), int(73 * k)), fill=TEXT)
    d.rectangle((bx + int(64 * k), int(56 * k), bx + int(69 * k), int(70 * k)), fill=TEXT)
    return s


def app():
    shots = [("a1.png", "Sign in once", "Connected, with a live widget preview"),
             ("a2.png", "Pick a look", "11 themes and 31 crab skins"),
             ("a4.png", "Set the refresh", "Every 5, 15, 30 or 60 minutes")]
    pw, gap, top = 400, 80, 170
    frames = [phone(clean_status_bar(Image.open(MEDIA / f)), pw) for f, _, _ in shots]
    W = len(frames) * frames[0].width + (len(frames) + 1) * gap
    H = top + frames[0].height + 170
    img = gradient(W, H).convert("RGBA")
    d = ImageDraw.Draw(img)
    title = "The Pinch app"
    tf = font("segoeuib.ttf", 52)
    d.text(((W - d.textlength(title, font=tf)) / 2, 58), title, font=tf, fill=TEXT)
    d.rounded_rectangle((W / 2 - 60, 132, W / 2 + 60, 137), radius=3, fill=ACCENT)
    for i, (f, (_, caption, sub)) in enumerate(zip(frames, shots)):
        x = gap + i * (f.width + gap)
        shadowed(img, f, (x, top), blur=20)
        cf, sf = font("segoeuib.ttf", 30), font("segoeui.ttf", 24)
        y = top + f.height + 38
        d.text((x + (f.width - d.textlength(caption, font=cf)) / 2, y), caption, font=cf, fill=TEXT)
        d.text((x + (f.width - d.textlength(sub, font=sf)) / 2, y + 44), sub, font=sf, fill=MUTED)
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
