#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 icons.py 里的 SVG 图标转成 Android VectorDrawable（res/drawable/ic_*.xml）。

颜色规则（App 内深浅色自动切换靠 Compose 侧 tint）：
  设计稿的「深→浅」三档色阶在这里改成**同色 + 透明度层级**，
  这样 App 内用 Icon(tint = MaterialTheme.colorScheme.primary) 渲染时：
    - 浅色模式 → 深蓝主体 + 淡蓝次要笔画
    - 深色模式 → 浅蓝主体 + 半透明次要笔画
  层次感来自透明度，而不是写死的颜色值。
  currentColor / var(--primary)  → @color/icon_primary            (alpha 1.0)
  var(--primary2)                → @color/icon_primary + alpha .66
  var(--primary3)                → @color/icon_primary + alpha .40
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import FEATURE, NAV, ACTION, BRAND_B

RES = "/workspace/native/app/src/main/res/drawable"
COLOR_MAP = {
    "currentColor": "@color/icon_primary",
    "var(--primary)": "@color/icon_primary",
    "var(--primary2)": "@color/icon_primary",
    "var(--primary3)": "@color/icon_primary",
}
# 同色不同透明度 → 保留设计稿的深→浅层次，同时可被 Compose tint 统一接管
ALPHA_MAP = {
    "var(--primary)": None,
    "var(--primary2)": "0.66",
    "var(--primary3)": "0.40",
}

TAG_RE = re.compile(r"<(g|path|circle|rect|line|polyline|polygon)\b([^>]*?)(/?)>", re.S)
ATTR_RE = re.compile(r'([\w:-]+)\s*=\s*"([^"]*)"')


def color(v):
    v = v.strip()
    for k, rep in COLOR_MAP.items():
        if v == k:
            return rep
    return v


def alpha_of(v):
    """原 SVG 色阶 → 透明度；非色阶返回 None"""
    return ALPHA_MAP.get(v.strip())


def parse_attrs(s):
    out = {}
    for k, v in ATTR_RE.findall(s):
        out[k] = color(v)
        a = alpha_of(v)
        if a is not None:
            out["__alpha__"] = a
    return out


def circle_to_path(cx, cy, r):
    return (f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z")


def rect_to_path(x, y, w, h, rx=0.0):
    if rx <= 0:
        return f"M{x},{y} H{x + w} V{y + h} H{x} Z"
    rx = min(rx, w / 2, h / 2)
    return (f"M{x + rx},{y} H{x + w - rx} A{rx},{rx} 0 0 1 {x + w},{y + rx} "
            f"V{y + h - rx} A{rx},{rx} 0 0 1 {x + w - rx},{y + h} "
            f"H{x + rx} A{rx},{rx} 0 0 1 {x},{y + h - rx} "
            f"V{y + rx} A{rx},{rx} 0 0 1 {x + rx},{y} Z")


def android_attrs(a):
    """SVG 属性 → Android vector path 属性"""
    out = {}
    alpha = a.get("__alpha__")
    if "d" in a:
        out["android:pathData"] = a["d"]
    fill = a.get("fill")
    stroke = a.get("stroke")
    # AAPT2 不接受 fillColor="none"，用透明色等价表达「不填充」
    out["android:fillColor"] = (fill if (fill and fill != "none")
                                else "@android:color/transparent")
    if alpha is not None and fill and fill != "none":
        out["android:fillAlpha"] = alpha
    if stroke and stroke != "none":
        out["android:strokeColor"] = stroke
        if alpha is not None:
            out["android:strokeAlpha"] = alpha
        out["android:strokeWidth"] = a.get("stroke-width", "1.9")
        cap = a.get("stroke-linecap", "round")
        join = a.get("stroke-linejoin", "round")
        if cap != "butt":
            out["android:strokeLineCap"] = cap
        if join != "miter":
            out["android:strokeLineJoin"] = join
    return out


def convert(svg: str, name: str, size: float = 24.0):
    """svg 片段 → vector XML 文本"""
    paths = []
    # 逐个标签处理，维护 <g> 的属性栈
    stack = []
    for m in TAG_RE.finditer(svg):
        tag, attr_s, selfclose = m.group(1), m.group(2), m.group(3)
        a = parse_attrs(attr_s)
        if tag == "g":
            if selfclose:
                continue
            merged = dict(stack[-1]) if stack else {}
            for k in ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin"):
                if k in a:
                    merged[k] = a[k]
            if "__alpha__" in a:
                merged["__alpha__"] = a["__alpha__"]
            stack.append(merged)
            continue
        # 叶子节点：合并继承
        eff = dict(stack[-1]) if stack else {}
        eff.update(a)
        if tag == "circle":
            cx, cy, r = float(eff.get("cx", 0)), float(eff.get("cy", 0)), float(eff.get("r", 0))
            eff["d"] = circle_to_path(cx, cy, r)
            eff.setdefault("fill", "none")
        elif tag == "rect":
            x, y = float(eff.get("x", 0)), float(eff.get("y", 0))
            w, h = float(eff.get("width", 0)), float(eff.get("height", 0))
            rx = float(eff.get("rx", 0) or 0)
            eff["d"] = rect_to_path(x, y, w, h, rx)
            eff.setdefault("fill", "none")
        elif tag in ("line", "polyline", "polygon"):
            continue
        attrs = android_attrs(eff)
        if "android:pathData" not in attrs:
            continue
        parts = " ".join('%s="%s"' % (k, v) for k, v in attrs.items())
        paths.append("    <path\n        %s />" % parts.replace(" ", "\n        ", 0))
    # 清理格式
    body = "\n".join(p.replace('" ', '"\n            ') for p in paths)
    body = body.replace('"\n            ', '"\n            ')
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {name} —— 工作报告生成器图标体系（纯矢量，无位图） -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{size:g}"
    android:viewportHeight="{size:g}">
{body}
</vector>
"""
    return xml


def fix_body(xml: str):
    """把属性拆到多行，保持文件可读"""
    out = []
    for line in xml.split("\n"):
        if line.strip().startswith('android:') or line.strip().startswith('<?xml') or \
           line.strip().startswith('<!--') or line.strip().startswith('android:'):
            out.append(line)
        else:
            out.append(line)
    return "\n".join(out)


def snake(name: str) -> str:
    """Android 资源名只允许小写字母/数字/下划线"""
    import re as _re
    return _re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def main():
    os.makedirs(RES, exist_ok=True)
    n = 0
    for key in NAV:
        p = os.path.join(RES, "ic_nav_%s.xml" % snake(key))
        open(p, "w", encoding="utf-8").write(convert(NAV[key], "ic_nav_%s" % key))
        n += 1
    for key in FEATURE:
        p = os.path.join(RES, "ic_%s.xml" % snake(key))
        open(p, "w", encoding="utf-8").write(convert(FEATURE[key][1], "ic_%s" % key))
        n += 1
    for key in ACTION:
        p = os.path.join(RES, "ic_%s.xml" % snake(key))
        open(p, "w", encoding="utf-8").write(convert(ACTION[key], "ic_%s" % key))
        n += 1
    # 品牌标志（App 内顶栏，48 网格；保留色阶 → 转成透明度层次）
    brand = BRAND_B
    # 48 网格直接使用：内容范围约 x/y = 9..41，占画布 67%，
    # 渲染时 android:width=24dp → 顶栏 30.dp 图标实际图形约 20dp，比例合适。
    xml = convert(brand, "ic_brand（品牌标志·加粗版）", size=48.0)
    open(os.path.join(RES, "ic_brand.xml"), "w", encoding="utf-8").write(xml)
    n += 1
    print("✅ 生成 %d 个 VectorDrawable → %s" % (n, RES))


if __name__ == "__main__":
    main()
