#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 App 内顶栏用的 Android VectorDrawable（卷轴+笔图标，纯原生矢量）。"""
import io, math

def f(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return s if s not in ('-0', '') else '0'

def rrect(x, y, w, h, r):
    return ('M%s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s H%s '
            'A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s Z') % (
        f(x + r), f(y), f(x + w - r), f(r), f(r), f(x + w), f(y + r),
        f(y + h - r), f(r), f(r), f(x + w - r), f(y + h),
        f(x + r), f(r), f(r), f(x), f(y + h - r),
        f(y + r), f(r), f(r), f(x + r), f(y))

def rot(x, y, cx, cy, deg):
    """把局部坐标 (x,y) 绕原点旋转 deg 度后，平移到 (cx,cy)。"""
    a = math.radians(deg)
    return (cx + x * math.cos(a) - y * math.sin(a),
            cy + x * math.sin(a) + y * math.cos(a))

# ---------- 卷轴 ----------
scroll_frame = rrect(265, 255, 430, 500, 46)
scroll_roll = rrect(265, 255, 88, 500, 44)

# 三小杠（紫色）
bars = ''
for bx, bw in ((400, 78), (493, 78), (586, 46)):
    bars += rrect(bx, 330, bw, 24, 12) + ' '

# 四条正文横线（深灰蓝）
lines = ''
for ly, lw in ((404, 232), (456, 172), (508, 122), (560, 88)):
    lines += rrect(400, ly, lw, 26, 13) + ' '

# ---------- 笔（先在局部坐标画，再整体旋转 45°） ----------
PCX, PCY, ANG = 556.0, 692.0, 45.0

def pen_path(local_path):
    pts = []
    cur = (0.0, 0.0)
    for cmd in local_path:
        if cmd[0] == 'M':
            cur = (cmd[1], cmd[2]); pts.append(('M',) + rot(cur[0], cur[1], PCX, PCY, ANG))
        elif cmd[0] == 'L':
            cur = (cmd[1], cmd[2]); pts.append(('L',) + rot(cur[0], cur[1], PCX, PCY, ANG))
        elif cmd[0] == 'Z':
            pts.append(('Z',))
    out = []
    for p in pts:
        if p[0] == 'Z':
            out.append('Z')
        else:
            out.append('%s%s,%s' % (p[0], f(p[1]), f(p[2])))
    return ' '.join(out)

# 笔身（局部）
pen_body = pen_path([
    ('M', -28, -268), ('L', 28, -268), ('L', 28, -12), ('L', -28, -12), ('Z',),
])
# 笔尖（局部三角）
pen_tip = pen_path([
    ('M', 0, 56), ('L', -28, -12), ('L', 28, -12), ('Z',),
])
# 笔帽两条分隔线
pen_cap1 = pen_path([('M', -28, -226), ('L', 28, -226)])
pen_cap2 = pen_path([('M', -28, -198), ('L', 28, -198)])

# ---------- 星星 ----------
SX, SY, SRX, SRY = 558.0, 545.0, 52.0, 46.0
sparkle = (
    'M%s,%s '
    'C%s,%s %s,%s %s,%s '
    'C%s,%s %s,%s %s,%s '
    'C%s,%s %s,%s %s,%s '
    'C%s,%s %s,%s %s,%s Z') % (
    f(SX), f(SY - SRY),
    f(SX + SRX * 0.14), f(SY - SRY * 0.34), f(SX + SRX * 0.34), f(SY - SRY * 0.14), f(SX + SRX), f(SY),
    f(SX + SRX * 0.34), f(SY + SRY * 0.14), f(SX + SRX * 0.14), f(SY + SRY * 0.34), f(SX), f(SY + SRY),
    f(SX - SRX * 0.14), f(SY + SRY * 0.34), f(SX - SRX * 0.34), f(SY + SRY * 0.14), f(SX - SRX), f(SY),
    f(SX - SRX * 0.34), f(SY - SRY * 0.14), f(SX - SRX * 0.14), f(SY - SRY * 0.34), f(SX), f(SY - SRY),
)

VD = '''<?xml version="1.0" encoding="utf-8"?>
<!-- App 内顶栏图标（卷轴+笔），与桌面图标同一套设计，纯原生矢量 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="42dp"
    android:height="42dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">

    <!-- 卷轴外框：紫蓝描边 -->
    <path
        android:pathData="%s"
        android:fillColor="#00000000"
        android:strokeColor="#FF7C3AED"
        android:strokeWidth="23" />

    <!-- 左侧卷轴轴心 -->
    <path
        android:pathData="%s"
        android:fillColor="#FFF3F1FE"
        android:strokeColor="#FF7C3AED"
        android:strokeWidth="23" />

    <!-- 顶部三小杠 -->
    <path
        android:pathData="%s"
        android:fillColor="#FF8B5CF6" />

    <!-- 正文横线 -->
    <path
        android:pathData="%s"
        android:fillColor="#FF4A4E7A" />

    <!-- 笔身 -->
    <path
        android:pathData="%s"
        android:fillColor="#FFDCE6FF"
        android:strokeColor="#FF4F46E5"
        android:strokeWidth="9" />

    <!-- 笔帽分隔线 -->
    <path
        android:pathData="%s %s"
        android:fillColor="#00000000"
        android:strokeColor="#FF4F46E5"
        android:strokeWidth="7"
        android:strokeLineCap="round" />

    <!-- 笔尖 -->
    <path
        android:pathData="%s"
        android:fillColor="#FF2563EB"
        android:strokeColor="#FF1D4ED8"
        android:strokeWidth="7"
        android:strokeLineJoin="miter" />

    <!-- 星光 -->
    <path
        android:pathData="%s"
        android:fillColor="#FF7C3AED" />
</vector>
''' % (scroll_frame, scroll_roll, bars.strip(), lines.strip(), pen_body,
       pen_cap1, pen_cap2, pen_tip, sparkle)

io.open('/workspace/native/app/src/main/res/drawable/ic_app_logo.xml', 'w', encoding='utf-8').write(VD)
print('✅ ic_app_logo.xml 已生成 (%d 字节)' % len(VD.encode('utf-8')))
