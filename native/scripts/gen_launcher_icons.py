#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成桌面启动图标（带 WorkReport 字样）+ 自适应图标前景。

⚠️ 关键约束：Android 会把启动图标裁成圆形/圆角方形，
   所以桌面图标必须是**竖版堆叠**（标志在上、文字在下），
   横排「左标志 + 右文字」会被圆形裁掉一半（参考图那种横版只适合宽版场景）。
"""
import os, sys, subprocess, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import BRAND_B

RES = "/workspace/native/app/src/main/res"
OUT = "/workspace/output"

DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

BRAND_STYLE_LIGHT = "--primary:#3370FF;--primary2:#4E8FD8;--primary3:#8FBDF0"
BRAND_STYLE_DARK = "--primary:#7FB0FF;--primary2:#A8CBFF;--primary3:#4E7FB8"


def launcher_svg(size=1024, dark=False, round_icon=False, text=True):
    """size×size 的桌面图标。"""
    if dark:
        bg1, bg2, ink = "#1D2635", "#0A0E15", "#E8ECF4"
        bstyle = BRAND_STYLE_DARK
    else:
        bg1, bg2, ink = "#FFFFFF", "#E7EEFB", "#1F2329"
        bstyle = BRAND_STYLE_LIGHT

    mark = size * 0.33
    tx = size * 0.5
    ty = size * 0.76
    fontsize = size * 0.125
    label = ""
    if text:
        label = (f'<text x="{tx:g}" y="{ty:g}" text-anchor="middle" '
                 f'font-family="Inter, Helvetica Neue, Arial, sans-serif" '
                 f'font-size="{fontsize:g}" font-weight="700" letter-spacing="{-size*0.003:.1f}" '
                 f'fill="{ink}">WorkReport</text>')
    # 方形版全出血（不自己画圆角，否则启动器再裁一次会让图标看着偏小）；
    # 圆形版画成整圆。
    bg_shape = ('<circle cx="%g" cy="%g" r="%g" fill="url(#g)"/>' % (size / 2, size / 2, size / 2)
                if round_icon else
                '<rect x="0" y="0" width="%g" height="%g" fill="url(#g)"/>' % (size, size))
    return f"""
<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="{bg1}"/><stop offset="1" stop-color="{bg2}"/>
  </linearGradient></defs>
  {bg_shape}
  <g transform="translate({size*0.5 - mark*0.5:.1f},{size*0.30:.1f}) scale({mark/48:.4f})"
     style="{bstyle}">{BRAND_B}</g>
  {label}
</svg>"""


def adaptive_foreground_svg(size=108, dark=False):
    """自适应图标前景：108 画布，内容必须在中心 66dp 安全区内。"""
    bstyle = BRAND_STYLE_DARK if dark else BRAND_STYLE_LIGHT
    mark = size * 0.33
    return f"""
<svg width="{size}" height="{size}" viewBox="0 0 {size} {size}" xmlns="http://www.w3.org/2000/svg">
  <g transform="translate({size*0.5 - mark*0.5:.1f},{size*0.29:.1f}) scale({mark/48:.4f})"
     style="{bstyle}">{BRAND_B}</g>
  <text x="{size*0.5:g}" y="{size*0.76:g}" text-anchor="middle"
        font-family="Inter, Helvetica Neue, Arial, sans-serif"
        font-size="{size*0.115:g}" font-weight="700" letter-spacing="{-size*0.003:.1f}"
        fill="{"#E8ECF4" if dark else "#1F2329"}">WorkReport</text>
</svg>"""


def render(svg, out_png, px, round_icon=False, scale=1.0):
    """用 Playwright 渲染 SVG 并截图为 PNG"""
    html = f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>
      html,body{{margin:0;padding:0;background:transparent}}
      .wrap{{width:{px}px;height:{px}px;overflow:hidden;
             border-radius:{"50%" if round_icon else "0"}}}
      svg{{display:block;width:{px}px;height:{px}px}}
    </style></head><body><div class="wrap">{svg}</div></body></html>"""
    tmp = "/tmp/_icon_render.html"
    open(tmp, "w", encoding="utf-8").write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=["--no-sandbox"])
        pg = b.new_page(viewport={"width": px, "height": px}, device_scale_factor=1)
        pg.goto("file://" + tmp)
        pg.wait_for_timeout(150)
        pg.locator(".wrap").screenshot(path=out_png, omit_background=True)
        b.close()


def main():
    made = []
    for name, mult in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        base = int(48 * mult)
        # 方形
        render(launcher_svg(base, dark=False), os.path.join(d, "ic_launcher.png"), base)
        made.append(d + "/ic_launcher.png")
        # 圆形
        render(launcher_svg(base, dark=False), os.path.join(d, "ic_launcher_round.png"), base, round_icon=True)
        made.append(d + "/ic_launcher_round.png")
        # 深色版
        dn = os.path.join(RES, "mipmap-night-" + name)
        os.makedirs(dn, exist_ok=True)
        render(launcher_svg(base, dark=True), os.path.join(dn, "ic_launcher.png"), base)
        render(launcher_svg(base, dark=True), os.path.join(dn, "ic_launcher_round.png"), base, round_icon=True)
        made.append(dn + "/ic_launcher.png")
        made.append(dn + "/ic_launcher_round.png")
    print("✅ 传统 PNG 图标 %d 个" % len(made))

    # ---- 自适应图标前景（108dp 画布，透明底）----
    from playwright.sync_api import sync_playwright
    fg = []
    for name, mult in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        dn = os.path.join(RES, "mipmap-night-" + name)
        px = int(108 * mult)
        for dark, target in ((False, d), (True, dn)):
            svg = adaptive_foreground_svg(px, dark)
            html = ('<!DOCTYPE html><html><head><meta charset="utf-8"><style>'
                    'html,body{margin:0;padding:0;background:transparent}'
                    'svg{display:block;width:%dpx;height:%dpx}</style></head>'
                    '<body>%s</body></html>' % (px, px, svg))
            open("/tmp/_fg.html", "w", encoding="utf-8").write(html)
            with sync_playwright() as pw:
                b = pw.chromium.launch(args=["--no-sandbox"])
                pg = b.new_page(viewport={"width": px, "height": px}, device_scale_factor=1)
                pg.goto("file:///tmp/_fg.html")
                pg.wait_for_timeout(120)
                pg.locator("svg").screenshot(path=os.path.join(target, "ic_launcher_foreground.png"),
                                             omit_background=True)
                b.close()
            fg.append(target + "/ic_launcher_foreground.png")
    print("✅ 自适应前景 %d 个" % len(fg))

    # ---- anydpi 配置 ----
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    adaptive_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- 自适应图标：前景 = 品牌标志 + WorkReport，背景 = 浅蓝渐变。\n'
        '     深色模式由 drawable-night / mipmap-night 的同名资源自动接管。 -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n')
    open(os.path.join(anydpi, "ic_launcher.xml"), "w", encoding="utf-8").write(adaptive_xml)
    open(os.path.join(anydpi, "ic_launcher_round.xml"), "w", encoding="utf-8").write(adaptive_xml)
    print("✅ anydpi 配置完成")

    # ---- 背景渐变（浅色 / 深色）----
    def bg_xml(c1, c2, angle):
        return ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- 自适应图标背景层 -->\n'
                '<shape xmlns:android="http://schemas.android.com/apk/res/android" '
                'android:shape="rectangle">\n'
                '    <gradient\n'
                '        android:type="linear"\n'
                '        android:angle="%d"\n'
                '        android:startColor="%s"\n'
                '        android:endColor="%s" />\n'
                '</shape>\n' % (angle, c1, c2))
    os.makedirs(os.path.join(RES, "drawable-night"), exist_ok=True)
    open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w", encoding="utf-8").write(
        bg_xml("#FFFFFFFF", "#FFE7EEFB", 315))
    open(os.path.join(RES, "drawable-night", "ic_launcher_background.xml"), "w", encoding="utf-8").write(
        bg_xml("#FF1D2635", "#FF0A0E15", 315))
    print("✅ 背景渐变（浅/深）完成")


if __name__ == "__main__":
    main()
