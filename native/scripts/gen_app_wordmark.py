#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 App 内顶栏用的 WorkReport 字标资源（v2.0.3）。

为什么拆成 Work / Report 两份而不是一张整条：
  · 两份都是**单色形状**，App 里用 `ColorFilter.tint` 按主题染色，
    于是浅色/深色**自动跟随**，不用维护两套图。
  · 形状直接取自用户给的设计稿原图，字体跟设计稿一模一样。

输出：drawable-<density>/ic_wordmark_work.png、ic_wordmark_report.png
"""
import os
import numpy as np
from PIL import Image

RES = "/workspace/native/app/src/main/res"
SRC = "/workspace/attachments/b2337d200b08d2fa/1795269916272.png"

BG_LUM = 238.0
WORK_LUM = 113.0
REPORT_LUM = 39.0

# Work 段（含尾部 13px 连写间距，让两段拼回去跟设计稿一模一样）
WORK_BOX = (177, 908, 939, 1205)
REPORT_BOX = (940, 908, 1866, 1205)

# App 内顶栏的目标宽度（dp）
TARGET_DP = 96.0

DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]


def extract(src, box, main_lum):
    """抠出单色形状：白色填充 + 原形状的 alpha（运行时 tint 用）"""
    reg = src.crop(box)
    arr = np.array(reg).astype(float)
    lum = arr.sum(axis=2) / 3.0
    alpha = np.clip((BG_LUM - lum) / (BG_LUM - main_lum), 0, 1)
    alpha[lum > BG_LUM] = 0.0
    # 只保留 alpha，颜色统一成白色（tint 会覆盖）
    out = np.dstack([np.full(arr.shape, 255, dtype=np.uint8),
                     (alpha * 255).astype(np.uint8)])
    return Image.fromarray(out, "RGBA")


def main():
    src = Image.open(SRC).convert("RGB")
    work = extract(src, WORK_BOX, WORK_LUM)
    report = extract(src, REPORT_BOX, REPORT_LUM)

    # 整条宽度 = Work 段 + Report 段
    strip_w = work.width + report.width
    target_px_total = TARGET_DP

    made = 0
    for name, mult in DENSITIES:
        d = os.path.join(RES, "drawable-" + name)
        os.makedirs(d, exist_ok=True)
        scale = mult  # mdpi=1 → 1dp=1px
        w_total = int(round(target_px_total * scale))
        ratio = w_total / strip_w
        h_total = int(round((work.height) * ratio))

        work_o = work.resize((max(1, int(round(work.width * ratio))), h_total), Image.LANCZOS)
        report_o = report.resize((max(1, int(round(report.width * ratio))), h_total), Image.LANCZOS)
        work_o.save(os.path.join(d, "ic_wordmark_work.png"))
        report_o.save(os.path.join(d, "ic_wordmark_report.png"))
        made += 2
        print(f"  {name}: {work_o.width}x{h_total} + {report_o.width}x{h_total}")

    print("✅ App 内字标资源:", made, "个")

    # 预览（浅色 / 深色两套配色，看 tint 效果）
    from PIL import ImageDraw
    P = "/workspace/output/iconpreview"
    w96 = int(TARGET_DP * 4)
    ratio = w96 / strip_w
    h = int(round(work.height * ratio))
    wk = work.resize((int(round(work.width * ratio)), h), Image.LANCZOS)
    rp = report.resize((int(round(report.width * ratio)), h), Image.LANCZOS)

    def tinted(img, rgb):
        o = Image.new("RGBA", img.size)
        o.paste(rgb, (0, 0), img)
        return o

    for theme, (wc, rc) in {
        "light": ((96, 96, 120), (0, 24, 72)),
        "dark": ((154, 166, 188), (143, 184, 240)),
    }.items():
        strip = Image.new("RGBA", (w96, h))
        strip.alpha_composite(tinted(wk, wc), (0, 0))
        strip.alpha_composite(tinted(rp, rc), (wk.width, 0))
        bg = (255, 255, 255) if theme == "light" else (24, 26, 30)
        cv = Image.new("RGB", (w96 + 40, h + 40), bg)
        cv.paste(strip, (20, 20), strip)
        cv.save(f"{P}/wordmark_{theme}.png")
    print("✓ 预览:", P)


if __name__ == "__main__":
    main()
