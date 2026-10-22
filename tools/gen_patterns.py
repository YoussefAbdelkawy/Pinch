"""Generates the faint pixel wallpapers behind the widget, one tile per theme.

The halloween, space, ocean and cyberpunk tiles follow lib/theme.ps1 of
clawd-usage-winwidget (MIT); the rest are new. Output: res/drawable-nodpi/pat_*_tile.png
plus res/drawable/pat_*.xml that repeats the tile. Pure Python, no dependencies.

Run from the repo root:  python tools/gen_patterns.py
"""
import struct
import zlib
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DENSITY = 3  # pixels per dp; drawable-nodpi is not rescaled

PATTERNS = {
    "halloween": dict(tile=104, px=2, opacity=0.10,
        colors=dict(o="F97316", g="65A30D", b="A78BFA", w="F5F5F4", y="FDE047"),
        sprites=[
            (6, 6, ["....gg...", "...g.....", ".ooo.ooo.", "ooooooooo", "oo.ooo.oo", "ooooooooo", "oo.o.o.oo", ".ooooooo."]),
            (58, 20, ["b.........b", "bb..b.b..bb", "bbbbbbbbbbb", ".bbb.b.bbb.", "..b.....b.."]),
            (30, 56, ["..www..", ".wwwww.", "ww.w.ww", "wwwwwww", "wwwwwww", "wwwwwww", "w.w.w.w"]),
            (76, 70, [".y.", "yyy", ".y."]), (8, 84, ["y"]), (92, 4, ["y"]),
        ]),
    "space": dict(tile=96, px=1, opacity=0.45,
        colors=dict(w="FFFFFF", v="C4B5FD", p="F9A8D4", b="93C5FD"),
        sprites=[(10, 8, ["w"]), (41, 22, ["v"]), (77, 12, ["b"]), (24, 47, ["p"]), (63, 58, [".w.", "www", ".w."]),
                 (88, 40, ["v"]), (6, 79, ["b"]), (47, 86, ["w"]), (83, 81, ["p"])]),
    "ocean": dict(tile=104, px=2, opacity=0.07,
        colors=dict(w="E0F2FE", p="F9A8D4", y="FDE68A"),
        sprites=[(8, 10, [".ww.", "w..w", "w..w", ".ww."]), (22, 2, ["w"]),
                 (56, 28, [".p.p.", "ppppp", ".pyp.", "ppppp", ".p.p."]), (30, 70, [".w.", "w.w", ".w."]),
                 (84, 76, [".ww.", "w..w", "w..w", ".ww."]), (94, 62, ["w"])]),
    "cyberpunk": dict(tile=3, px=1, opacity=0.05, colors=dict(c="00E5FF"), sprites=[(0, 0, ["ccc"])]),
    "christmas": dict(tile=104, px=2, opacity=0.09,
        colors=dict(w="F8FAFC", g="16A34A", r="EF4444", y="FDE047"),
        sprites=[(8, 8, ["w.w.w", ".www.", "wwwww", ".www.", "w.w.w"]),
                 (54, 22, ["..y..", "..g..", ".ggg.", "ggggg", ".ggg.", "ggggg", "..r.."]),
                 (28, 62, [".w.", "www", ".w."]), (78, 72, ["w.w.w", ".www.", "wwwww", ".www.", "w.w.w"]),
                 (90, 6, ["w"]), (6, 90, ["w"])]),
    "valentine": dict(tile=96, px=2, opacity=0.09,
        colors=dict(h="F472B6", p="FBCFE8"),
        sprites=[(8, 8, [".hh.hh.", "hhhhhhh", "hhhhhhh", ".hhhhh.", "..hhh..", "...h..."]),
                 (58, 40, ["p.p", "ppp", ".p."]), (28, 66, [".hh.hh.", "hhhhhhh", ".hhhhh.", "..hhh..", "...h..."]),
                 (80, 10, ["p"]), (84, 80, ["p.p", "ppp", ".p."])]),
    "spring": dict(tile=104, px=2, opacity=0.08,
        colors=dict(p="F9A8D4", y="FDE047", g="4ADE80", w="F8FAFC"),
        sprites=[(8, 10, [".p.", "pyp", ".p.", ".g.", "gg."]), (56, 26, [".w.", "wyw", ".w."]),
                 (30, 64, ["p.p", ".y.", "p.p"]), (80, 74, [".p.", "pyp", ".p.", ".g.", ".gg"]), (92, 8, ["g"])]),
    "summer": dict(tile=104, px=2, opacity=0.08,
        colors=dict(y="FDE047", o="FB923C", w="E0F2FE"),
        sprites=[(8, 8, ["y.y.y", ".yyy.", "yyyyy", ".yyy.", "y.y.y"]), (58, 30, ["..o..", ".ooo.", "ooooo", "o.o.o"]),
                 (24, 70, ["w...w", ".w.w.", "..w.."]), (82, 76, [".ww.", "w..w", ".ww."])]),
}


def png(width, height, rgba_rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rgba_rows)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    (RES / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    for name, p in PATTERNS.items():
        size = p["tile"] * DENSITY
        buf = [[0] * (size * 4) for _ in range(size)]
        alpha = int(255 * p["opacity"])
        cell = p["px"] * DENSITY
        for sx, sy, art in p["sprites"]:
            for y, row in enumerate(art):
                for x, ch in enumerate(row):
                    if ch not in p["colors"]:
                        continue
                    r, g, b = (int(p["colors"][ch][i:i + 2], 16) for i in (0, 2, 4))
                    for yy in range((sy + y * p["px"]) * DENSITY, (sy + y * p["px"]) * DENSITY + cell):
                        for xx in range((sx + x * p["px"]) * DENSITY, (sx + x * p["px"]) * DENSITY + cell):
                            if yy < size and xx < size:
                                buf[yy][xx * 4:xx * 4 + 4] = [r, g, b, alpha]
        (RES / "drawable-nodpi" / f"pat_{name}_tile.png").write_bytes(png(size, size, buf))
        (RES / "drawable" / f"pat_{name}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<bitmap xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:src="@drawable/pat_{name}_tile"\n'
            '    android:tileMode="repeat" />\n', encoding="utf-8")


if __name__ == "__main__":
    main()
