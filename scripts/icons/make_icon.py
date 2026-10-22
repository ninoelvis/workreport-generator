#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重绘「工作报告生成器」App 图标（矢量，天然无水印）
设计参照用户提供的豆包 AI 生成稿：紫蓝渐变描边的卷轴 + 笔 + 星星
"""
import os, subprocess, sys

OUT = '/workspace/icons'
os.makedirs(OUT, exist_ok=True)

# ---------- 组件 SVG 片段 ----------

def scroll_group(inset=0.0):
    """卷轴文档：圆角外框 + 左侧卷轴轴心 + 三小杠 + 四条正文横线"""
    # inset: 内容整体缩放比例（1.0 = 满幅），用于 adaptive 前景
    return f'''
  <g transform="translate(512 512) scale({inset}) translate(-512 -512)">
    <!-- 外框（紫→蓝 渐变描边） -->
    <rect x="265" y="255" width="430" height="500" rx="46" ry="46"
          fill="none" stroke="url(#gradStroke)" stroke-width="23"/>
    <!-- 左侧卷轴轴心（胶囊） -->
    <rect x="265" y="255" width="88" height="500" rx="44" ry="44"
          fill="url(#gradScrollFill)" stroke="url(#gradStroke)" stroke-width="23"/>
    <!-- 顶部三小杠（紫） -->
    <g fill="#8B5CF6">
      <rect x="400" y="330" width="78"  height="24" rx="12" ry="12"/>
      <rect x="493" y="330" width="78"  height="24" rx="12" ry="12"/>
      <rect x="586" y="330" width="46"  height="24" rx="12" ry="12"/>
    </g>
    <!-- 正文横线（深灰蓝） -->
    <g fill="#4A4E7A">
      <rect x="400" y="404" width="232" height="26" rx="13" ry="13"/>
      <rect x="400" y="456" width="172" height="26" rx="13" ry="13"/>
      <rect x="400" y="508" width="122" height="26" rx="13" ry="13"/>
      <rect x="400" y="560" width="88"  height="26" rx="13" ry="13"/>
    </g>
  </g>'''

def pen_group(inset=0.0):
    """斜插的笔：浅紫蓝笔身 + 蓝色笔尖 + 笔帽分隔线"""
    return f'''
  <g transform="translate(512 512) scale({inset}) translate(-512 -512)">
    <g transform="translate(556 692) rotate(45)">
      <!-- 笔尖 -->
      <path d="M 0 56 L -28 -12 L 28 -12 Z" fill="#2563EB" stroke="#1D4ED8"
            stroke-width="7" stroke-linejoin="round"/>
      <!-- 笔身 -->
      <rect x="-28" y="-268" width="56" height="256" rx="18" ry="18"
            fill="url(#gradPenFill)" stroke="#4F46E5" stroke-width="9"/>
      <!-- 笔帽分隔线 -->
      <g stroke="#4F46E5" stroke-width="7" stroke-linecap="round">
        <line x1="-28" y1="-226" x2="28" y2="-226"/>
        <line x1="-28" y1="-198" x2="28" y2="-198"/>
      </g>
    </g>
  </g>'''

def sparkle_group(inset=0.0, cx=558, cy=545, rx=52, ry=46, color='#7C3AED'):
    """四角星光（豆包稿里的那个小闪光）"""
    return f'''
  <g transform="translate(512 512) scale({inset}) translate(-512 -512)">
    <path d="M {cx} {cy-ry}
             C {cx+rx*0.14} {cy-ry*0.34} {cx+rx*0.34} {cy-ry*0.14} {cx+rx} {cy}
             C {cx+rx*0.34} {cy+ry*0.14} {cx+rx*0.14} {cy+ry*0.34} {cx} {cy+ry}
             C {cx-rx*0.14} {cy+ry*0.34} {cx-rx*0.34} {cy+ry*0.14} {cx-rx} {cy}
             C {cx-rx*0.34} {cy-ry*0.14} {cx-rx*0.14} {cy-ry*0.34} {cx} {cy-ry} Z"
          fill="{color}"/>
  </g>'''

def defs():
    return '''  <defs>
    <linearGradient id="gradStroke" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%"   stop-color="#A855F7"/>
      <stop offset="45%"  stop-color="#7C3AED"/>
      <stop offset="100%" stop-color="#2563EB"/>
    </linearGradient>
    <linearGradient id="gradScrollFill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="#F5F3FF"/>
      <stop offset="55%"  stop-color="#EEF2FF"/>
      <stop offset="100%" stop-color="#E0F2FE"/>
    </linearGradient>
    <linearGradient id="gradPenFill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="#E0E7FF"/>
      <stop offset="60%"  stop-color="#C7D2FE"/>
      <stop offset="100%" stop-color="#BAE6FD"/>
    </linearGradient>
    <linearGradient id="gradBg" x1="0" y1="0" x2="0.6" y2="1">
      <stop offset="0%"   stop-color="#FFFFFF"/>
      <stop offset="55%"  stop-color="#F7F9FF"/>
      <stop offset="100%" stop-color="#E8EEFC"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0%"   stop-color="#8B5CF6" stop-opacity="0.30"/>
      <stop offset="60%"  stop-color="#8B5CF6" stop-opacity="0.08"/>
      <stop offset="100%" stop-color="#8B5CF6" stop-opacity="0"/>
    </radialGradient>
    <filter id="soft" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="26"/>
    </filter>
  </defs>'''

# 1) 传统启动图标：渐变背景铺满 + 内容
SVG_LEGACY = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
{defs()}
  <clipPath id="roundRect"><rect x="0" y="0" width="1024" height="1024" rx="205" ry="205"/></clipPath>
  <g clip-path="url(#roundRect)">
    <rect width="1024" height="1024" fill="url(#gradBg)"/>
    <ellipse cx="512" cy="560" rx="330" ry="300" fill="url(#glow)" filter="url(#soft)"/>
    <ellipse cx="600" cy="330" rx="210" ry="150" fill="#67E8F9" opacity="0.16" filter="url(#soft)"/>
  </g>
{scroll_group(1.12)}
{pen_group(1.12)}
{sparkle_group(1.12)}
</svg>'''

# 2) 自适应图标 · 背景层
SVG_ADAPT_BG = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
{defs()}
  <rect width="1024" height="1024" fill="url(#gradBg)"/>
  <ellipse cx="512" cy="560" rx="330" ry="300" fill="url(#glow)" filter="url(#soft)"/>
</svg>'''

# 3) 自适应图标 · 前景层（内容缩到安全区 ~62%）
SVG_ADAPT_FG = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
{defs()}
{scroll_group(0.68)}
{pen_group(0.68)}
{sparkle_group(0.68)}
</svg>'''

# 4) 圆形图标（部分桌面 / 网页 favicon）
SVG_ROUND = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
{defs()}
  <circle cx="512" cy="512" r="512" fill="url(#gradBg)"/>
  <ellipse cx="512" cy="560" rx="330" ry="300" fill="url(#glow)" filter="url(#soft)"/>
{scroll_group(0.92)}
{pen_group(0.92)}
{sparkle_group(0.92)}
</svg>'''

def write_svg(name, svg_text):
    path = os.path.join(OUT, name + '.svg')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(svg_text)
    print('  ✅ %-10s %s (%d 字节)' % (name, path, len(svg_text.encode('utf-8'))))

if __name__ == '__main__':
    for name, svg in [('legacy', SVG_LEGACY), ('adapt_bg', SVG_ADAPT_BG),
                      ('adapt_fg', SVG_ADAPT_FG), ('round', SVG_ROUND)]:
        write_svg(name, svg)
    print('SVG 全部生成，PNG 请用 node render.js 渲染')
