#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""渲染 App 内**实际观感**：VectorDrawable 单色 tint + 透明度层次。

Compose 的 Icon(painterResource(id), tint = ...) 会把矢量里所有颜色统一替换成 tint，
但 fillAlpha / strokeAlpha 会保留 —— 所以设计稿里的「深→浅」层次在 App 里
表现为「同色 + 透明度」。这个脚本就是把真实渲染效果画出来检查。
"""
import os, re, glob, html

RES = "/workspace/native/app/src/main/res/drawable"
OUT = "/workspace/output"

PATH_RE = re.compile(r"<path\s(.*?)/>", re.S)
ATTR_RE = re.compile(r'([\w:]+)="([^"]*)"')

TINT_LIGHT = "#2F6FE4"   # 浅色模式 primary
TINT_DARK = "#8FB6FF"    # 深色模式 primary


def vd_to_svg(xml: str, tint: str) -> str:
    m = re.search(r'android:viewportWidth="([\d.]+)"', xml)
    vw = float(m.group(1)) if m else 24.0
    body = []
    for pm in PATH_RE.finditer(xml):
        a = dict(ATTR_RE.findall(pm.group(1)))
        d = a.get("android:pathData", "")
        if not d:
            continue
        fill_raw = a.get("android:fillColor", "@android:color/transparent")
        stroke_raw = a.get("android:strokeColor", "")
        fill = "none" if fill_raw in ("none", "@android:color/transparent") else tint
        if "fillAlpha" in a:
            fill += ' fill-opacity="%s"' % a["fillAlpha"]
        attrs = ['d="%s"' % d, 'fill="%s"' % fill]
        if stroke_raw and stroke_raw != "none":
            s = 'stroke="%s"' % tint
            if "strokeAlpha" in a:
                s += ' stroke-opacity="%s"' % a["strokeAlpha"]
            s += ' stroke-width="%s"' % a.get("android:strokeWidth", "1.9")
            s += ' stroke-linecap="%s"' % a.get("android:strokeLineCap", "round")
            s += ' stroke-linejoin="%s"' % a.get("android:strokeLineJoin", "round")
            attrs.append(s)
        body.append("<path %s/>" % " ".join(attrs))
    return ('<svg width="48" height="48" viewBox="0 0 %g %g" '
            'xmlns="http://www.w3.org/2000/svg">%s</svg>' % (vw, vw, "".join(body)))


def grid(files, tint, bg, cols, cell, title):
    items = []
    for f in files:
        name = os.path.basename(f)[:-4]
        svg = vd_to_svg(open(f, encoding="utf-8").read(), tint)
        items.append(
            '<div class="cell"><div class="ic">%s</div><div class="nm">%s</div></div>'
            % (svg, html.escape(name)))
    return ('<div class="wrap" style="background:%s"><h2>%s</h2>'
            '<div class="grid" style="grid-template-columns:repeat(%d,%dpx)">%s</div></div>'
            % (bg, title, cols, cell, "".join(items)))


def main():
    files = sorted(glob.glob(RES + "/ic_*.xml"))
    nav = [f for f in files if os.path.basename(f).startswith("ic_nav_")]
    brand = [f for f in files if os.path.basename(f) == "ic_brand.xml"]
    rest = [f for f in files if f not in nav and f not in brand]

    html_doc = """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
      body{margin:0;font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif}
      .wrap{padding:22px 26px}
      h2{font-size:16px;color:#222;margin:0 0 14px}
      .grid{display:grid;gap:6px}
      .cell{display:flex;flex-direction:column;align-items:center;justify-content:center;
            background:#fff;border-radius:10px;padding:8px 4px 6px;border:1px solid rgba(0,0,0,.06)}
      .ic{width:30px;height:30px;display:flex;align-items:center;justify-content:center}
      .ic svg{width:26px;height:26px}
      .nm{font-size:9px;color:#666;margin-top:5px;text-align:center;word-break:break-all;line-height:1.25}
    </style></head><body>
    """ + grid(nav + brand, TINT_LIGHT, "#FFFFFF", 4, 96, "导航 / 品牌（浅色）") \
        + grid(rest, TINT_LIGHT, "#FFFFFF", 9, 96, "功能区 + 操作（浅色 · 单色 tint）") \
        + grid(nav + brand, TINT_DARK, "#14181F", 4, 96, "导航 / 品牌（深色）") \
        + grid(rest, TINT_DARK, "#14181F", 9, 96, "功能区 + 操作（深色 · 单色 tint）") \
        + "</body></html>"

    path = "/tmp/_icons_preview.html"
    open(path, "w", encoding="utf-8").write(html_doc)
    print(path)


if __name__ == "__main__":
    main()
