#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 SVG 图标转成 Android VectorDrawable（原生矢量，无需 WebView / SVG 库）。

支持：rect(含圆角)、path(M/L/H/V/C/A/Z 绝对坐标)、line、fill/stroke。
渐变：填充渐变用 <aapt:attr> 保留；描边渐变退化为纯色（Android VD 的 stroke 不支持渐变）。
"""
import io, re, sys, math

def parse_color(c, default='?attr/colorControlNormal'):
    if not c or c == 'none':
        return None
    c = c.strip()
    if c.startswith('#'):
        h = c[1:]
        if len(h) == 3:
            h = ''.join(ch*2 for ch in h)
        if len(h) == 6:
            h = 'FF' + h
        return '#' + h.upper()
    return default

def fmt(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return s if s not in ('-0', '') else '0'

def rect_to_path(x, y, w, h, rx):
    if not rx:
        return 'M%s,%s h%s v%s h%s Z' % (fmt(x), fmt(y), fmt(w), fmt(h), fmt(-w))
    rx = min(rx, w / 2, h / 2)
    return ('M%s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s Z' % (
        fmt(x+rx), fmt(y), fmt(x+w-rx), fmt(rx), fmt(rx), fmt(x+w), fmt(y+rx),
        fmt(y+h-rx), fmt(rx), fmt(rx), fmt(x+w-rx), fmt(y+h),
        fmt(x+rx), fmt(rx), fmt(rx), fmt(x), fmt(y+h-rx),
        fmt(y+rx), fmt(rx), fmt(rx), fmt(x+rx), fmt(y)))

def transform_svg_path(d, ox, oy, sx=1.0, sy=1.0):
    """把 SVG path 的绝对坐标平移缩放，输出 VectorDrawable 兼容的 pathData。"""
    out = []
    i = 0
    tokens = re.findall(r'([MmLlHhVvCcSsQqTtAaZz])|(-?\d*\.?\d+(?:[eE][-+]?\d+)?)', d)
    cmd = None
    nums = []

    def flush():
        nonlocal cmd, nums
        if cmd is None or not nums:
            return
        c = cmd
        v = nums[:]
        n = len(v)
        if c in 'Mm':
            for i in range(0, n - 1, 2):
                x, y = v[i] * sx - ox * sx, v[i+1] * sy - oy * sy
                out.append('%s%s,%s' % ('M' if (c == 'M' and i == 0) or (c == 'm' and i == 0) else 'L', fmt(x), fmt(y)))
        elif c in 'Ll':
            for i in range(0, n - 1, 2):
                out.append('L%s,%s' % (fmt(v[i]*sx - ox*sx), fmt(v[i+1]*sy - oy*sy)))
        elif c in 'Hh':
            for x in v:
                out.append('H%s' % fmt(x * sx - ox * sx))
        elif c in 'Vv':
            for y in v:
                out.append('V%s' % fmt(y * sy - oy * sy))
        elif c in 'Cc':
            for i in range(0, n - 5, 6):
                out.append('C%s,%s %s,%s %s,%s' % (
                    fmt(v[i]*sx - ox*sx), fmt(v[i+1]*sy - oy*sy),
                    fmt(v[i+2]*sx - ox*sx), fmt(v[i+3]*sy - oy*sy),
                    fmt(v[i+4]*sx - ox*sx), fmt(v[i+5]*sy - oy*sy)))
        elif c in 'Ss':
            for i in range(0, n - 3, 4):
                out.append('C%s,%s %s,%s %s,%s' % (
                    fmt(v[i]*sx - ox*sx), fmt(v[i+1]*sy - oy*sy),
                    fmt(v[i+2]*sx - ox*sx), fmt(v[i+3]*sy - oy*sy),
                    fmt(v[i+2]*sx - ox*sx), fmt(v[i+3]*sy - oy*sy)))
        elif c in 'Zz':
            out.append('Z')
        cmd = None
        nums = []

    for kind, num in tokens:
        if kind:
            flush()
            cmd = kind
        else:
            nums.append(float(num))
    flush()
    return ' '.join(out)

def gradient_xml(grad_id, stops, vb_w, vb_h):
    """生成 <aapt:attr name="android:fillColor"><gradient>…</gradient></aapt:attr>"""
    items = []
    for offset, color in stops:
        items.append(
            '            <item android:offset="%s" android:color="%s"/>' % (fmt(offset), color)
        )
    return (
        '      <aapt:attr name="android:fillColor">\n'
        '        <gradient\n'
        '            android:type="linear"\n'
        '            android:startX="0" android:startY="0"\n'
        '            android:endX="%s" android:endY="%s">\n' % (fmt(vb_w), fmt(vb_h)) +
        '\n'.join(items) + '\n'
        '        </gradient>\n'
        '      </aapt:attr>'
    )

def convert(svg_path, out_path, name):
    svg = io.open(svg_path, encoding='utf-8').read()
    # 去掉 clipPath / defs（渐变另行解析），里面的形状不参与渲染
    svg = re.sub(r'<clipPath\b.*?</clipPath>', '', svg, flags=re.S)

    # viewBox
    vb = re.search(r'viewBox="([\d.\- ]+)"', svg)
    vx, vy, vw, vh = [float(v) for v in vb.group(1).split()]

    # 渐变定义
    grads = {}
    for gm in re.finditer(r'<(linear|radial)Gradient([^>]*)>(.*?)</\1Gradient>', svg, re.S):
        gid = re.search(r'id="([^"]+)"', gm.group(2)).group(1)
        stops = []
        for sm in re.finditer(r'<stop([^>]*)/>', gm.group(3)):
            attrs = sm.group(1)
            off = re.search(r'offset="([^"]+)"', attrs)
            col = re.search(r'stop-color="([^"]+)"', attrs)
            pct = float(off.group(1).rstrip('%')) / 100 if off else 0
            stops.append((pct, parse_color(col.group(1) if col else None, '#FF000000')))
        grads[gid] = stops

    body = []

    def style_of(tag):
        d = {}
        st = re.search(r'style="([^"]+)"', tag)
        if st:
            for kv in st.group(1).split(';'):
                if ':' in kv:
                    k, v = kv.split(':', 1)
                    d[k.strip()] = v.strip()
        # 属性写法优先（style 里没有才用）
        for key in ('fill', 'stroke', 'stroke-width', 'stroke-linejoin'):
            if key not in d:
                am = re.search(key + r'="([^"]+)"', tag)
                if am:
                    d[key] = am.group(1)
        return d

    # <rect>
    for m in re.finditer(r'<rect\b([^>]*?)/?>', svg, re.S):
        attrs = m.group(1)
        if 'clip-path' in attrs:
            continue
        d = style_of(m.group(0))
        def num(name, default=0.0):
            mm = re.search(name + r'="([^"]+)"', attrs)
            return float(mm.group(1)) if mm else default
        x, y, w, h, rx = num('x'), num('y'), num('width'), num('height'), num('rx')
        if w <= 0 or h <= 0:
            continue
        fill = d.get('fill')
        stroke = d.get('stroke')
        sw = float(d.get('stroke-width', '1').replace('px', ''))
        path_data = rect_to_path(x, y, w, h, rx)
        body.append(emit_path(path_data, fill, stroke, sw, grads, vx, vy, vw, vh, d))

    # <path>
    for m in re.finditer(r'<path\b([^>]*?)d="([^"]+)"([^>]*?)/?>', svg, re.S):
        d = style_of(m.group(0))
        path_data = transform_svg_path(m.group(2), vx, vy)
        fill = d.get('fill')
        stroke = d.get('stroke')
        sw = float(d.get('stroke-width', '1').replace('px', ''))
        body.append(emit_path(path_data, fill, stroke, sw, grads, vx, vy, vw, vh, d))

    # <line>
    for m in re.finditer(r'<line\b([^>]*?)/?>', svg, re.S):
        attrs = m.group(1)
        d = style_of(m.group(0))
        def num2(name):
            mm = re.search(name + r'="([^"]+)"', attrs)
            return float(mm.group(1)) if mm else 0.0
        x1, y1, x2, y2 = num2('x1'), num2('y1'), num2('x2'), num2('y2')
        path_data = 'M%s,%s L%s,%s' % (fmt(x1 - vx), fmt(y1 - vy), fmt(x2 - vx), fmt(y2 - vy))
        body.append(emit_path(path_data, None, d.get('stroke'), float(d.get('stroke-width', '1')), grads, vx, vy, vw, vh, d))

    out = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="%sdp"\n'
        '    android:height="%sdp"\n'
        '    android:viewportWidth="%s"\n'
        '    android:viewportHeight="%s">\n' % (fmt(vw), fmt(vh), fmt(vw), fmt(vh)) +
        '\n'.join(body) + '\n</vector>\n'
    )
    io.open(out_path, 'w', encoding='utf-8').write(out)
    print('✅ %s  (%d 个 path, %d 字节)' % (out_path, len(body), len(out.encode('utf-8'))))

def emit_path(path_data, fill, stroke, sw, grads, vx, vy, vw, vh, d):
    fill_url = re.match(r'url\(#([^)]+)\)', fill or '')
    stroke_url = re.match(r'url\(#([^)]+)\)', stroke or '')
    stroke_color = None
    if stroke and not stroke_url:
        stroke_color = parse_color(stroke, '#FF000000')
    if stroke_url:
        # 描边渐变 → 取中间色近似
        st = grads.get(stroke_url.group(1), [])
        stroke_color = st[len(st)//2][1] if st else '#FF7C3AED'
    fill_color = parse_color(fill, None) if (fill and not fill_url) else None

    parts = ['    <path']
    parts.append('        android:pathData="%s"' % path_data)
    if fill_url:
        st = grads.get(fill_url.group(1), [])
        if st:
            parts.append(gradient_xml(fill_url.group(1), st, vw, vh))
        else:
            parts.append('        android:fillColor="#FFF2F5FC"')
    elif fill_color:
        parts.append('        android:fillColor="%s"' % fill_color)
    else:
        parts.append('        android:fillColor="#00000000"')
    if stroke_color:
        parts.append('        android:strokeColor="%s"' % stroke_color)
        parts.append('        android:strokeWidth="%s"' % fmt(sw))
        if d.get('stroke-linejoin'):
            parts.append('        android:strokeLineJoin="miter"')
    parts[-1] += ' />'
    return '\n'.join(parts)

if __name__ == '__main__':
    convert(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else 'icon')
