"""Generates the launcher icon and the widget picker preview: Clawd as vector pixels.

Run from the repo root:  python tools/gen_icon.py
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

# Clawd's default pose, 18 x 10 pixels: B body, k eyes (same map the app draws).
CLAWD = [
    "...BBBBBBBBBBBBB..",
    "...BBBBBBBBBBBBB..",
    "...BBkBBBBBBBkBB..",
    "...BBkBBBBBBBkBB..",
    ".BBBBBBBBBBBBBBBBB",
    ".BBBBBBBBBBBBBBBBB",
    "...BBBBBBBBBBBBB..",
    "...BBBBBBBBBBBBB..",
    "...B.B.......B.B..",
    "...B.B.......B.B..",
]
CROWN = [
    ".....g...g...g....",
    ".....gg.ggg.gg....",
    ".....ggggggggg....",
]


def rects(art, ch, unit, ox, oy):
    out = []
    for y, row in enumerate(art):
        x = 0
        while x < len(row):
            if row[x] != ch:
                x += 1
                continue
            x0 = x
            while x < len(row) and row[x] == ch:
                x += 1
            out.append(f"M{ox + x0 * unit:g},{oy + y * unit:g}h{(x - x0) * unit:g}v{unit:g}h{-(x - x0) * unit:g}z")
    return "".join(out)


def vector(size, unit, oy_extra=0.0):
    w = 18 * unit
    ox = (size - w) / 2
    oy = (size - 13 * unit) / 2 + 3 * unit + oy_extra
    paths = [
        ("#FFFBBF24", rects(CROWN, "g", unit, ox, oy - 3 * unit)),
        ("#FFD77757", rects(CLAWD, "B", unit, ox, oy)),
        ("#FF141416", rects(CLAWD, "k", unit, ox, oy)),
    ]
    body = "".join(f'    <path android:fillColor="{c}" android:pathData="{d}" />\n' for c, d in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{size}" android:viewportHeight="{size}">\n'
            f'{body}</vector>\n')


def main():
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(vector(108, 3.2), encoding="utf-8")
    (RES / "drawable" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <solid android:color="#FF1C1326" />\n'
        '</shape>\n', encoding="utf-8")
    # The widget picker preview is a real render: res/drawable-nodpi/widget_preview.png.
    (RES / "mipmap-anydpi-v26" / "ic_launcher.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n', encoding="utf-8")


if __name__ == "__main__":
    main()
