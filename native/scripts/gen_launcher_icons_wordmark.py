#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成桌面启动图标（v2.0.3 · 方案 A · 纯文字）。

方案 A：**横排原样**「WorkReport」字标
  · 「Work」灰蓝 #606078 +「Report」深藏蓝 #001848
  · 白底，纯文字，**没有任何图形**
  · 字形直接从用户给的设计稿原图抠出（不是字体重排）

⚠️ 关键约束（实测得出，见 tools/mask_preview.py）：
Android 的圆形遮罩只保留 108dp 画布的**中心 72dp**，
所以横排字标最多只能放到 **68dp 宽**（高 68/5.69 ≈ 12dp），否则两端被裁。

⚠️ 桌面图标**只有一份**，不跟随系统深浅色（用户要求）。
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

RES = "/workspace/native/app/src/main/res"
OUT = "/workspace/output/iconpreview"
SRC = "/workspace/attachments/b2337d200b08d2fa/1795269916272.png"

PX = 4
CANVAS_DP = 108.0
SAFE_DP = 72.0

BG_LUM = 238.0
WORK_LUM = 113.0
REPORT_LUM = 39.0

STRIP_BOX = (177, 908, 1866, 1205)
SPLIT_X = 940
WORK_ONLY_BOX = (177, 908, 930, 1205)
REPORT_ONLY_BOX = (936, 908, 1866, 1205)
W_W = SPLIT_X - 177
W_R = 1866 - SPLIT_X
TOTAL = W_W + W_R

DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]


def _alpha(arr, main_lum, split=None):
    lum = arr.sum(axis=2) / 3.0
    if split is None:
        a = np.clip((BG_LUM - lum) / (BG_LUM - main_lum), 0, 1)
    else:
        a = np.zeros(lum.shape)
        a[:, :split] = np.clip((BG_LUM - lum[:, :split]) / (BG_LUM - WORK_LUM), 0, 1)
        a[:, split:] = np.clip((BG_LUM - lum[:, split:]) / (BG_LUM - REPORT_LUM), 0, 1)
    a[lum > BG_LUM] = 0.0
    return a


def _mk(arr, alpha):
    return Image.fromarray(
        np.dstack([arr.astype(np.uint8), (alpha * 255).astype(np.uint8)]), "RGBA")


def load_wordmarks():
    """从设计稿原图抠出：整条横排（彩色）/ Work 单独 / Report 单独 / 整条单色"""
    src = Image.open(SRC).convert("RGB")

    arr = np.array(src.crop(STRIP_BOX)).astype(float)
    split = SPLIT_X - STRIP_BOX[0]
    strip = _mk(arr, _alpha(arr, 0, split=split))

    w_arr = np.array(src.crop(WORK_ONLY_BOX)).astype(float)
    r_arr = np.array(src.crop(REPORT_ONLY_BOX)).astype(float)
    work_only = _mk(w_arr, _alpha(w_arr, WORK_LUM))
    report_only = _mk(r_arr, _alpha(r_arr, REPORT_LUM))

    # 单色版（Android 13+ 主题图标用）：整条统一深藏蓝
    solid = Image.new("RGBA", strip.size)
    solid.paste((0, 24, 72), (0, 0), strip)
    return strip, work_only, report_only, solid


def place(items, canvas_px=432, px=PX):
    """按 dp 坐标摆图层到 108dp 画布"""
    S = int(CANVAS_DP * px)
    out = Image.new("RGBA", (S, S))
    for img, x_dp, y_dp, w_dp in items:
        w_px = max(1, int(round(w_dp * px)))
        h_px = max(1, int(round(img.height * w_px / img.width)))
        f = img.resize((w_px, h_px), Image.LANCZOS)
        out.alpha_composite(f, (int(round(x_dp * px)), int(round(y_dp * px))))
    return out


def wordmark_fg(strip, width_dp=68.0):
    """方案 A 的前景：横排字标，垂直居中在 108dp 画布上"""
    ww = width_dp * W_W / TOTAL
    rw = width_dp * W_R / TOTAL
    h = strip.height * (ww * PX / strip.width) / PX
    y = (CANVAS_DP - h) / 2
    x = (CANVAS_DP - width_dp) / 2
    return place([(strip.crop((0, 0, W_W, strip.height)), x, y, ww),
                  (strip.crop((W_W, 0, strip.width, strip.height)), x + ww, y, rw)])


def flat_bg(canvas_px=432):
    """白底（设计稿就是白底，纯文字图标要干净的底）"""
    return Image.new("RGBA", (canvas_px, canvas_px), (255, 255, 255, 255))


def round_mask(canvas_px=432):
    m = Image.new("L", (canvas_px, canvas_px), 0)
    ImageDraw.Draw(m).ellipse([0, 0, canvas_px - 1, canvas_px - 1], fill=255)
    out = Image.new("RGBA", (canvas_px, canvas_px))
    out.paste((255, 255, 255, 255), (0, 0, canvas_px, canvas_px), m)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    strip, work_only, report_only, solid = load_wordmarks()
    strip.save(os.path.join(OUT, "wm_strip.png"))

    # 前景（自适应图标用）：透明底，68dp 宽，垂直居中
    fg = wordmark_fg(strip, 68.0)
    # 单色版（主题图标）
    mono = wordmark_fg(solid, 68.0)
    # 启动图：放大一点，桌面图标小字看不清
    splash = wordmark_fg(strip, 76.0)

    made = []
    for name, mult in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        px = int(108 * mult)
        scale = px / 432.0

        fg.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"))
        mono.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_monochrome.png"))
        splash.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_splash.png"))

        # legacy 全出血（Android 8+ 用不到，保留给旧 ROM）
        base = int(48 * mult)
        white = flat_bg(base)
        white.alpha_composite(fg.resize((base, base), Image.LANCZOS))
        white.convert("RGB").save(os.path.join(d, "ic_launcher.png"))
        rnd = round_mask(base)
        rnd.alpha_composite(fg.resize((base, base), Image.LANCZOS))
        rnd.convert("RGB").save(os.path.join(d, "ic_launcher_round.png"))

        made.append(d)
    print("✅ 图标已生成:", len(made), "个密度目录")

    # 背景层：纯白
    open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w", encoding="utf-8").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- 自适应图标背景层（v2.0.3 · 方案 A 纯文字）——纯白底，\n'
        '     与设计稿一致；桌面图标只有一份，不跟随系统深浅色。 -->\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
        '    <solid android:color="#FFFFFFFF" />\n'
        '</shape>\n'
    )
    print("✅ 背景 drawable 已写入（纯白）")

    # anydpi 配置
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for fn in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(anydpi, fn), "w", encoding="utf-8").write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- 自适应图标：背景 = 纯白，前景 = WorkReport 字标（方案 A 纯文字）。\n'
            '     v2.0.3 起**只有一份**，桌面图标不再跟随系统深浅色切换；\n'
            '     App 内的深浅色主题切换是 Compose 管的，与此无关。 -->\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n'
        )
    print("✅ anydpi 配置已写入")

    # 预览
    mask_shape = None
    from PIL import ImageDraw as _ID
    def _mask(img, shape):
        """前景（透明底字标）叠到白底，再套系统遮罩。

        ⚠️ 遮罩的 alpha 要与前景 alpha **相乘**，不能直接 putalpha 覆盖 ——
           否则会把字标的透明度抹掉，预览出来变成黑底白字。
        """
        from PIL import ImageChops
        S = img.width
        base = flat_bg(S)
        base.alpha_composite(img)
        m = Image.new("L", (S, S), 0)
        dd = _ID.Draw(m)
        r = SAFE_DP / 2 * S / CANVAS_DP
        if shape == "circle":
            dd.ellipse([S/2-r, S/2-r, S/2+r, S/2+r], fill=255)
        elif shape == "squircle":
            dd.rounded_rectangle([S/2-r, S/2-r, S/2+r, S/2+r], radius=r*0.42, fill=255)
        else:
            dd.rectangle([0, 0, S, S], fill=255)
        o = base.copy()
        o.putalpha(ImageChops.multiply(base.split()[3], m))
        return o

    grid = Image.new("RGB", (3 * 216 + 4 * 20, 216 + 40), (221, 227, 236))
    for j, shape in enumerate(("circle", "squircle", "square")):
        tile = _mask(fg, shape).resize((216, 216), Image.LANCZOS)
        grid.paste(tile, (20 + j * 236, 20), tile)
    grid.save(os.path.join(OUT, "desktop_v203_A.png"))
    print("✓ 预览:", os.path.join(OUT, "desktop_v203_A.png"))


if __name__ == "__main__":
    main()
