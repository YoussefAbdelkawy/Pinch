"""Generates repetitive Android resources: the level bars and their layouts.

Run from the repo root:  python tools/gen_res.py
"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

BAR = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape>
            <corners android:radius="3dp" />
            <solid android:color="#26FFFFFF" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape>
                <corners android:radius="3dp" />
                <solid android:color="#FF{color}" />
            </shape>
        </clip>
    </item>
</layer-list>
"""

BAR_LAYOUT_HEAD = """<?xml version="1.0" encoding="utf-8"?>
<!-- Level bars stacked; code shows the one matching the level, which works on every Android version. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/{name}_bars"
    android:layout_width="match_parent"
    android:layout_height="6dp"
    android:layout_marginTop="4dp">
"""

BAR_LAYOUT_ITEM = """
    <ProgressBar
        android:id="@+id/{name}_bar_{level}"
        style="@android:style/Widget.ProgressBar.Horizontal"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:max="100"
        android:progressDrawable="@drawable/bar_{level}"{gone} />
"""

LEVELS = {"ok": "4ADE80", "warn": "FBBF24", "crit": "F87171", "dead": "6B7280"}


def main():
    for level, color in LEVELS.items():
        (RES / "drawable" / f"bar_{level}.xml").write_text(BAR.format(color=color), encoding="utf-8")
    for name in ("session", "week"):
        out = BAR_LAYOUT_HEAD.format(name=name)
        for i, level in enumerate(LEVELS):
            gone = "" if i == 0 else '\n        android:visibility="gone"'
            out += BAR_LAYOUT_ITEM.format(name=name, level=level, gone=gone)
        out += "</FrameLayout>\n"
        (RES / "layout" / f"widget_bar_{name}.xml").write_text(out, encoding="utf-8")


if __name__ == "__main__":
    main()
