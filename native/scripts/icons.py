#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「工作报告生成器」图标体系 —— 设计稿生成器。

参照用户给的参考图：浅色渐变底 + 蓝色文档图形 + WorkReport 字样。
产出：
  - brand.svg        品牌标志（文档 + 三条横线 + 对勾 + 柱状图）
  - 24 个功能区线性图标（替换 App 里现有的 emoji）
  - 3 个底部导航图标
浅色/深色两套配色，靠 CSS 变量切换。
"""

# ---------------------------------------------------------------- 配色
LIGHT = {
    "ink":       "#1F2329",   # 主文字
    "primary":   "#2C6FD1",   # 主蓝（图标主色）
    "primary2":  "#4E8FD8",   # 次蓝
    "primary3":  "#8FBDF0",   # 浅蓝
    "muted":     "#8A93A3",
    "chip":      "#E8F0FF",
    "bg":        "#F5F7FB",
    "card":      "#FFFFFF",
    "hairline":  "#E3E8F0",
}
DARK = {
    "ink":       "#E8ECF4",
    "primary":   "#7FB0FF",
    "primary2":  "#A8CBFF",
    "primary3":  "#4E7FB8",
    "muted":     "#6E7788",
    "chip":      "#1E3A5F",
    "bg":        "#0E1117",
    "card":      "#171B23",
    "hairline":  "#2A3038",
}

# ---------------------------------------------------------------- 品牌标志
# 48x48 网格。文档圆角矩形（右上角断开） + 三条横线（深→浅） + 对勾 + 四根递增柱子
BRAND = """
<g fill="none" stroke-linecap="round" stroke-linejoin="round">
  <!-- 文档主体轮廓：左下闭合，右上留缺口 -->
  <path d="M33.5,9 H15 A6,6 0 0 0 9,15 V35 A6,6 0 0 0 15,41 H26"
        stroke="var(--primary)" stroke-width="2.6"/>
  <!-- 右上角那一小段 -->
  <path d="M37,17 V15 A6,6 0 0 0 31,9"
        stroke="var(--primary)" stroke-width="2.6"/>
  <!-- 三条横线：由深到浅 -->
  <path d="M15,16.5 H29.5" stroke="var(--primary)"  stroke-width="2.6"/>
  <path d="M15,22.5 H25"   stroke="var(--primary2)" stroke-width="2.6"/>
  <path d="M15,28 H23.5"   stroke="var(--primary3)" stroke-width="2.6"/>
  <!-- 对勾 -->
  <path d="M14.6,33.4 L18.2,37 L24.4,29.4"
        stroke="var(--primary)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
</g>
<!-- 右下角四根递增的柱子 -->
<g stroke-linecap="round">
  <path d="M27.2,41 V37.4" stroke="var(--primary)"  stroke-width="2.8"/>
  <path d="M31.2,41 V34.2" stroke="var(--primary)"  stroke-width="2.8"/>
  <path d="M35.2,41 V31.4" stroke="var(--primary2)" stroke-width="2.8"/>
  <path d="M39.2,41 V28.2" stroke="var(--primary)"  stroke-width="2.8"/>
</g>
"""

# ---------------------------------------------------------------- 导航图标（24x24）
NAV = {
    # 首页：文档 + 勾（品牌标志的线条简化版）
    "home": """
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6.5,3.5 H14 A3.5,3.5 0 0 1 17.5,7 V20 A3.5,3.5 0 0 1 14,23.5 H6.5 A3.5,3.5 0 0 1 3,20 V7 A3.5,3.5 0 0 1 6.5,3.5 Z"
              stroke="currentColor" stroke-width="1.9"/>
        <path d="M6.5,9 H14" stroke="currentColor" stroke-width="1.9"/>
        <path d="M6.5,13.2 H12" stroke="currentColor" stroke-width="1.9"/>
        <path d="M6.4,19.6 L9,22.2 L14,16.4" stroke="currentColor" stroke-width="2.2"/>
      </g>""",
    # 历史报告：时钟 + 回转箭头
    "history": """
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.4,12 A8.6,8.6 0 1 0 6.2,6" stroke="currentColor" stroke-width="1.9"/>
        <path d="M2.6,4.6 L3.4,9.2 L8,8.2" stroke="currentColor" stroke-width="1.9"/>
        <path d="M12,7.4 V12.2 L15.4,14.2" stroke="currentColor" stroke-width="1.9"/>
      </g>""",
    # 我的：人像
    "profile": """
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="7.8" r="4" stroke="currentColor" stroke-width="1.9"/>
        <path d="M4.6,20.6 C4.6,16.2 7.9,13.6 12,13.6 C16.1,13.6 19.4,16.2 19.4,20.6"
              stroke="currentColor" stroke-width="1.9"/>
      </g>""",
}

# ---------------------------------------------------------------- 功能区图标（24x24，替换 emoji）
FEATURE = {
    "shield": ("""<path d="M12 2.6 L20 5.6 V11.6 C20,16.2 16.6,19.9 12,21.4 C7.4,19.9 4,16.2 4,11.6 V5.6 Z"/>""",
               """<path d="M12 2.6 L20 5.6 V11.6 C20,16.2 16.6,19.9 12,21.4 C7.4,19.9 4,16.2 4,11.6 V5.6 Z"
                    fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>"""),
    "info": ("""<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M12,10.8 V16.6" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"/>
               <circle cx="12" cy="7.4" r="1.15" fill="currentColor"/>""",
              """<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M12,10.8 V16.6" stroke="currentColor" stroke-width="2.1" stroke-linecap="round"/>
               <circle cx="12" cy="7.4" r="1.15" fill="currentColor"/>"""),
    "broom": ("""<path d="M4.6,6.4 H19.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M9.4,6.4 V4.8 A1.6,1.6 0 0 1 11,3.2 H13 A1.6,1.6 0 0 1 14.6,4.8 V6.4"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M6.2,6.4 L7,19.6 A2,2 0 0 0 9,21.4 H15 A2,2 0 0 0 17,19.6 L17.8,6.4"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M10.4,10.2 V17.4 M13.6,10.2 V17.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>""",
               """<path d="M4.6,6.4 H19.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M9.4,6.4 V4.8 A1.6,1.6 0 0 1 11,3.2 H13 A1.6,1.6 0 0 1 14.6,4.8 V6.4"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M6.2,6.4 L7,19.6 A2,2 0 0 0 9,21.4 H15 A2,2 0 0 0 17,19.6 L17.8,6.4"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M10.4,10.2 V17.4 M13.6,10.2 V17.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>"""),
    "mail": ("""<rect x="2.6" y="4.8" width="18.8" height="14.4" rx="3" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M3.4,7.6 L12,13.4 L20.6,7.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>""",
               """<rect x="2.6" y="4.8" width="18.8" height="14.4" rx="3" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M3.4,7.6 L12,13.4 L20.6,7.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>"""),
    "folder": ("""<path d="M2.8,6.8 A2.4,2.4 0 0 1 5.2,4.4 H9.4 L12,7.6 H18.8 A2.4,2.4 0 0 1 21.2,10 V17.6 A2.4,2.4 0 0 1 18.8,20 H5.2 A2.4,2.4 0 0 1 2.8,17.6 Z"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>""",
                """<path d="M2.8,6.8 A2.4,2.4 0 0 1 5.2,4.4 H9.4 L12,7.6 H18.8 A2.4,2.4 0 0 1 21.2,10 V17.6 A2.4,2.4 0 0 1 18.8,20 H5.2 A2.4,2.4 0 0 1 2.8,17.6 Z"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>"""),
    "save": ("""<path d="M7.2,18.4 A4.6,4.6 0 0 1 6.6,9.2 A5.8,5.8 0 0 1 17.4,7.8 A4.4,4.4 0 0 1 17.8,16.6"
                   fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M12,20.6 V12.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
               <path d="M8.8,15.6 L12,12.4 L15.2,15.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>""",
              """<path d="M7.2,18.4 A4.6,4.6 0 0 1 6.6,9.2 A5.8,5.8 0 0 1 17.4,7.8 A4.4,4.4 0 0 1 17.8,16.6"
                   fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M12,20.6 V12.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
               <path d="M8.8,15.6 L12,12.4 L15.2,15.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>"""),
    "user": ("""<circle cx="12" cy="7.6" r="4" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M4.4,20.8 C4.4,16.2 7.8,13.4 12,13.4 C16.2,13.4 19.6,16.2 19.6,20.8"
                     fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
              """<circle cx="12" cy="7.6" r="4" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M4.4,20.8 C4.4,16.2 7.8,13.4 12,13.4 C16.2,13.4 19.6,16.2 19.6,20.8"
                     fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "theme": ("""<circle cx="12" cy="12" r="8.8" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M12,3.2 A8.8,8.8 0 0 1 12,20.8 Z" fill="currentColor"/>""",
               """<circle cx="12" cy="12" r="8.8" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M12,3.2 A8.8,8.8 0 0 1 12,20.8 Z" fill="currentColor"/>"""),
    "bolt": ("""<path d="M13.4,2.4 L5.2,13.2 H11 L10.2,21.6 L18.8,10.6 H12.8 Z"
                fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>""",
              """<path d="M13.4,2.4 L5.2,13.2 H11 L10.2,21.6 L18.8,10.6 H12.8 Z"
                fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>"""),
    "clock": ("""<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M12,6.8 V12.2 L15.6,14.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>""",
               """<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.9"/>
                <path d="M12,6.8 V12.2 L15.6,14.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>"""),
    "note": ("""<path d="M6,3.4 H15.4 L19.6,7.6 V20.6 H6 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M15.2,3.6 V8 H19.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M8.8,12 H16.4 M8.8,16 H14.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
               """<path d="M6,3.4 H15.4 L19.6,7.6 V20.6 H6 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M15.2,3.6 V8 H19.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M8.8,12 H16.4 M8.8,16 H14.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "folderOpen": ("""<path d="M2.8,7.4 A2.4,2.4 0 0 1 5.2,5 H9.6 L12.2,8.2 H18.4 A2.4,2.4 0 0 1 20.8,10.6"
                       fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                     <path d="M2.8,7.4 V18 A2.4,2.4 0 0 0 5.2,20.4 H17.4 A2.4,2.4 0 0 0 19.8,18 L21.6,12 H6.4 A2.4,2.4 0 0 0 4,14.4 Z"
                       fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>""",
                    """<path d="M2.8,7.4 A2.4,2.4 0 0 1 5.2,5 H9.6 L12.2,8.2 H18.4 A2.4,2.4 0 0 1 20.8,10.6"
                       fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                     <path d="M2.8,7.4 V18 A2.4,2.4 0 0 0 5.2,20.4 H17.4 A2.4,2.4 0 0 0 19.8,18 L21.6,12 H6.4 A2.4,2.4 0 0 0 4,14.4 Z"
                       fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>"""),
    "bellOff": ("""<path d="M8.6,17.4 A5.4,5.4 0 0 1 7.4,7.8" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M9.8,4.8 A4.8,4.8 0 0 1 18,9.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M17,11.4 C17,15 18.4,16.6 19,17.6 H9.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                   <path d="M10.4,20 A1.8,1.8 0 0 0 13.6,20" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M3.4,3.4 L20.6,20.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                  """<path d="M8.6,17.4 A5.4,5.4 0 0 1 7.4,7.8" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M9.8,4.8 A4.8,4.8 0 0 1 18,9.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M17,11.4 C17,15 18.4,16.6 19,17.6 H9.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                   <path d="M10.4,20 A1.8,1.8 0 0 0 13.6,20" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M3.4,3.4 L20.6,20.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "bell": ("""<path d="M7.4,17.4 C7.4,15.6 6.2,14.6 6.2,12 A5.8,5.8 0 0 1 17.8,12 C17.8,14.6 16.6,15.6 16.6,17.4 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M10.2,20 A1.9,1.9 0 0 0 13.8,20" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
              """<path d="M7.4,17.4 C7.4,15.6 6.2,14.6 6.2,12 A5.8,5.8 0 0 1 17.8,12 C17.8,14.6 16.6,15.6 16.6,17.4 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M10.2,20 A1.9,1.9 0 0 0 13.8,20" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "download": ("""<path d="M12,3.4 V15" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                    <path d="M7.4,10.6 L12,15.2 L16.6,10.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M4,17.4 V18.6 A2.4,2.4 0 0 0 6.4,21 H17.6 A2.4,2.4 0 0 0 20,18.6 V17.4"
                          fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                   """<path d="M12,3.4 V15" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                    <path d="M7.4,10.6 L12,15.2 L16.6,10.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M4,17.4 V18.6 A2.4,2.4 0 0 0 6.4,21 H17.6 A2.4,2.4 0 0 0 20,18.6 V17.4"
                          fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "upload": ("""<path d="M12,15.2 V3.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                  <path d="M7.4,8.2 L12,3.6 L16.6,8.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M4,17.4 V18.6 A2.4,2.4 0 0 0 6.4,21 H17.6 A2.4,2.4 0 0 0 20,18.6 V17.4"
                        fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                 """<path d="M12,15.2 V3.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                  <path d="M7.4,8.2 L12,3.6 L16.6,8.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M4,17.4 V18.6 A2.4,2.4 0 0 0 6.4,21 H17.6 A2.4,2.4 0 0 0 20,18.6 V17.4"
                        fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "edit": ("""<path d="M16.4,3.6 L20.4,7.6 L8.8,19.2 L3.6,20.4 L4.8,15.2 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M14.2,5.8 L18.2,9.8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
              """<path d="M16.4,3.6 L20.4,7.6 L8.8,19.2 L3.6,20.4 L4.8,15.2 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M14.2,5.8 L18.2,9.8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "clipboard": ("""<path d="M9,4.2 H7 A2,2 0 0 0 5,6.2 V19.4 A2,2 0 0 0 7,21.4 H17 A2,2 0 0 0 19,19.4 V6.2 A2,2 0 0 0 17,4.2 H15"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                    <rect x="9" y="2.4" width="6" height="3.8" rx="1.4" fill="none" stroke="currentColor" stroke-width="1.9"/>
                    <path d="M8.6,11 H15.4 M8.6,14.6 H15.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                   """<path d="M9,4.2 H7 A2,2 0 0 0 5,6.2 V19.4 A2,2 0 0 0 7,21.4 H17 A2,2 0 0 0 19,19.4 V6.2 A2,2 0 0 0 17,4.2 H15"
                      fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                    <rect x="9" y="2.4" width="6" height="3.8" rx="1.4" fill="none" stroke="currentColor" stroke-width="1.9"/>
                    <path d="M8.6,11 H15.4 M8.6,14.6 H15.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "calendar": ("""<rect x="3.4" y="5.4" width="17.2" height="15.6" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.9"/>
                   <path d="M3.4,10.2 H20.6" stroke="currentColor" stroke-width="1.9"/>
                   <path d="M8,3.2 V6.6 M16,3.2 V6.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M7.6,14 H9 M11,14 H12.4 M14.4,14 H15.8 M7.6,17.4 H9 M11,17.4 H12.4"
                         stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>""",
                  """<rect x="3.4" y="5.4" width="17.2" height="15.6" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.9"/>
                   <path d="M3.4,10.2 H20.6" stroke="currentColor" stroke-width="1.9"/>
                   <path d="M8,3.2 V6.6 M16,3.2 V6.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                   <path d="M7.6,14 H9 M11,14 H12.4 M14.4,14 H15.8 M7.6,17.4 H9 M11,17.4 H12.4"
                         stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>"""),
    "document": ("""<path d="M6.4,3.4 H14.6 L18.8,7.6 V20.6 H6.4 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                    <path d="M14.4,3.6 V8 H18.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                    <path d="M9.4,12 H15.6 M9.4,15.6 H15.6 M9.4,19.2 H13.4"
                          stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                   """<path d="M6.4,3.4 H14.6 L18.8,7.6 V20.6 H6.4 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                    <path d="M14.4,3.6 V8 H18.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                    <path d="M9.4,12 H15.6 M9.4,15.6 H15.6 M9.4,19.2 H13.4"
                          stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "bulb": ("""<path d="M8.4,14.6 A5.6,5.6 0 1 1 15.6,14.6 V17.2 H8.4 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M9.8,20.4 H14.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
               <path d="M10.4,17.2 V20.4 M13.6,17.2 V20.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
              """<path d="M8.4,14.6 A5.6,5.6 0 1 1 15.6,14.6 V17.2 H8.4 Z"
                 fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
               <path d="M9.8,20.4 H14.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
               <path d="M10.4,17.2 V20.4 M13.6,17.2 V20.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "eye": ("""<path d="M2.4,12 C4.6,7.8 8,5.6 12,5.6 C16,5.6 19.4,7.8 21.6,12 C19.4,16.2 16,18.4 12,18.4 C8,18.4 4.6,16.2 2.4,12 Z"
                fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
              <circle cx="12" cy="12" r="3.1" fill="none" stroke="currentColor" stroke-width="1.9"/>""",
             """<path d="M2.4,12 C4.6,7.8 8,5.6 12,5.6 C16,5.6 19.4,7.8 21.6,12 C19.4,16.2 16,18.4 12,18.4 C8,18.4 4.6,16.2 2.4,12 Z"
                fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
              <circle cx="12" cy="12" r="3.1" fill="none" stroke="currentColor" stroke-width="1.9"/>"""),
    "scale": ("""<path d="M12,3.6 V20.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M6.4,6.2 H17.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M6.4,6.2 L2.8,12.6 H10 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M17.6,6.2 L14,12.6 H21.2 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M8.4,20.4 H15.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
               """<path d="M12,3.6 V20.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M6.4,6.2 H17.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                <path d="M6.4,6.2 L2.8,12.6 H10 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M17.6,6.2 L14,12.6 H21.2 Z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
                <path d="M8.4,20.4 H15.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "arrowBack": ("""<path d="M19,12 H5.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                     <path d="M11,5.6 L4.6,12 L11,18.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>""",
                    """<path d="M19,12 H5.4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>
                     <path d="M11,5.6 L4.6,12 L11,18.4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>"""),
    "search": ("""<circle cx="10.6" cy="10.6" r="6.8" fill="none" stroke="currentColor" stroke-width="1.9"/>
                 <path d="M15.6,15.6 L20.8,20.8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
                """<circle cx="10.6" cy="10.6" r="6.8" fill="none" stroke="currentColor" stroke-width="1.9"/>
                 <path d="M15.6,15.6 L20.8,20.8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
    "share": ("""<circle cx="17.6" cy="5.8" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <circle cx="6.4" cy="12" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <circle cx="17.6" cy="18.2" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <path d="M8.8,10.6 L15.2,7.2 M8.8,13.4 L15.2,16.8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>""",
               """<circle cx="17.6" cy="5.8" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <circle cx="6.4" cy="12" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <circle cx="17.6" cy="18.2" r="2.8" fill="none" stroke="currentColor" stroke-width="1.8"/>
                <path d="M8.8,10.6 L15.2,7.2 M8.8,13.4 L15.2,16.8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>"""),
    "lock": ("""<rect x="4.6" y="10.4" width="14.8" height="10" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M8,10.4 V7.6 A4,4 0 0 1 16,7.6 V10.4" fill="none" stroke="currentColor" stroke-width="1.9"/>""",
              """<rect x="4.6" y="10.4" width="14.8" height="10" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M8,10.4 V7.6 A4,4 0 0 1 16,7.6 V10.4" fill="none" stroke="currentColor" stroke-width="1.9"/>"""),
    "copy": ("""<rect x="8.4" y="3.6" width="12" height="12" rx="2.4" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M15.6,18.4 V19.2 A2.2,2.2 0 0 1 13.4,21.4 H6.6 A2.2,2.2 0 0 1 4.4,19.2 V12.4 A2.2,2.2 0 0 1 6.6,10.2 H7.6"
                     fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>""",
              """<rect x="8.4" y="3.6" width="12" height="12" rx="2.4" fill="none" stroke="currentColor" stroke-width="1.9"/>
               <path d="M15.6,18.4 V19.2 A2.2,2.2 0 0 1 13.4,21.4 H6.6 A2.2,2.2 0 0 1 4.4,19.2 V12.4 A2.2,2.2 0 0 1 6.6,10.2 H7.6"
                     fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>"""),
}

FEATURE_LABEL = {
    "shield": "脱敏词库", "info": "说明", "broom": "清除数据", "mail": "邮件发送",
    "folder": "存放目录", "save": "配置备份", "user": "个人信息", "theme": "深浅主题",
    "bolt": "快捷记录", "clock": "周报提醒", "note": "工作记录", "folderOpen": "目录内容",
    "bellOff": "免打扰", "bell": "开启提醒", "download": "再次下载", "upload": "导出分享",
    "edit": "载入编辑", "clipboard": "模板复制", "calendar": "起始日期", "document": "报告文件",
    "bulb": "小提示", "eye": "预览区", "scale": "脱敏放行", "arrowBack": "返回",
    "search": "搜索", "share": "系统分享", "lock": "密码", "copy": "复制",
}

NAV_LABEL = {"home": "首页", "history": "历史报告", "profile": "我的"}


# ---------------------------------------------------------------- 操作按钮图标（重绘 Material Icons，统一风格）
ACTION = {
    "tag": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.6,11.6 L11.6,3.6 H20.4 V12.4 L12.4,20.4 Z" stroke-width="1.9"/>
        <circle cx="16.4" cy="7.6" r="1.5" stroke-width="1.7"/>
      </g>""",
    "merge": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12,3.4 V11.6" stroke-width="1.9"/>
        <path d="M8.4,8 L12,11.6 L15.6,8" stroke-width="1.9"/>
        <path d="M4.6,15.4 H19.4" stroke-width="1.9"/>
        <path d="M6.4,15.4 V19.8 A1.4,1.4 0 0 0 7.8,21.2 H16.2 A1.4,1.4 0 0 0 17.6,19.8 V15.4" stroke-width="1.9"/>
      </g>""",
    "delete": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4.6,6.6 H19.4" stroke-width="1.9"/>
        <path d="M9.4,6.6 V4.9 A1.5,1.5 0 0 1 10.9,3.4 H13.1 A1.5,1.5 0 0 1 14.6,4.9 V6.6" stroke-width="1.9"/>
        <path d="M6.4,6.6 L7.2,19.8 A2,2 0 0 0 9.2,21.6 H14.8 A2,2 0 0 0 16.8,19.8 L17.6,6.6" stroke-width="1.9"/>
        <path d="M10.4,10.4 V17.6 M13.6,10.4 V17.6" stroke-width="1.8"/>
      </g>""",
    "add": """<g fill="none" stroke="currentColor" stroke-linecap="round">
        <path d="M12,4.6 V19.4 M4.6,12 H19.4" stroke-width="2"/>
      </g>""",
    "fullscreen": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.6,9 V4.8 A1.2,1.2 0 0 1 4.8,3.6 H9" stroke-width="1.9"/>
        <path d="M15,3.6 H19.2 A1.2,1.2 0 0 1 20.4,4.8 V9" stroke-width="1.9"/>
        <path d="M20.4,15 V19.2 A1.2,1.2 0 0 1 19.2,20.4 H15" stroke-width="1.9"/>
        <path d="M9,20.4 H4.8 A1.2,1.2 0 0 1 3.6,19.2 V15" stroke-width="1.9"/>
      </g>""",
    "close": """<g fill="none" stroke="currentColor" stroke-linecap="round">
        <path d="M5.8,5.8 L18.2,18.2 M18.2,5.8 L5.8,18.2" stroke-width="2"/>
      </g>""",
    "visibilityOff": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9.6,5.4 A8.2,8.2 0 0 1 12,5.2 C16,5.2 19.4,7.4 21.6,12 C20.8,13.4 19.8,14.6 18.6,15.6" stroke-width="1.9"/>
        <path d="M6.2,7.6 C4,9.2 2.6,11 2.4,12 C4.6,16.6 8,18.8 12,18.8 C14,18.8 15.8,18.3 17.4,17.4" stroke-width="1.9"/>
        <path d="M10,10.1 A2.9,2.9 0 0 0 14,14.1" stroke-width="1.9"/>
        <path d="M3.6,3.6 L20.4,20.4" stroke-width="1.9"/>
      </g>""",
    "arrowUp": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12,19.4 V5" stroke-width="1.9"/>
        <path d="M5.8,11.2 L12,5 L18.2,11.2" stroke-width="1.9"/>
      </g>""",
    "arrowDown": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12,4.6 V19" stroke-width="1.9"/>
        <path d="M5.8,12.8 L12,19 L18.2,12.8" stroke-width="1.9"/>
      </g>""",
    "expandMore": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5.4,9 L12,15.4 L18.6,9" stroke-width="1.9"/>
      </g>""",
    "chevronRight": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9.4,5.6 L15.4,12 L9.4,18.4" stroke-width="1.9"/>
      </g>""",
    "star": """<path d="M12,3.4 L14.7,9 L20.8,9.8 L16.4,14 L17.6,20 L12,17.2 L6.4,20 L7.6,14 L3.2,9.8 L9.3,9 Z"
             fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>""",
    "restore": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4.2,11.4 A8,8 0 1 1 6.6,17" stroke-width="1.9"/>
        <path d="M3.4,6.4 V11.8 H8.8" stroke-width="1.9"/>
      </g>""",
    "refresh": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20,12 A8,8 0 1 1 17.2,6" stroke-width="1.9"/>
        <path d="M20.4,3.4 V7.8 H16" stroke-width="1.9"/>
      </g>""",
    "lockOpen": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <rect x="4.6" y="10.4" width="14.8" height="10" rx="2.6" stroke-width="1.9"/>
        <path d="M8,10.4 V7.6 A4,4 0 0 1 15.4,5.4" stroke-width="1.9"/>
      </g>""",
    "contentPaste": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M15,4.4 H18.4 A2,2 0 0 1 20.4,6.4 V19 A2,2 0 0 1 18.4,21 H6 A2,2 0 0 1 4,19 V6.4 A2,2 0 0 1 6,4.4 H9"
              stroke-width="1.9"/>
        <rect x="8.6" y="2.4" width="6.8" height="4" rx="1.4" stroke-width="1.9"/>
        <path d="M8.4,12.4 H15.6 M8.4,16 H13.2" stroke-width="1.8"/>
      </g>""",
    "contentCopy": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <rect x="8.4" y="3.6" width="12" height="12" rx="2.4" stroke-width="1.9"/>
        <path d="M15.6,18.4 V19.2 A2.2,2.2 0 0 1 13.4,21.4 H6.6 A2.2,2.2 0 0 1 4.4,19.2 V12.4 A2.2,2.2 0 0 1 6.6,10.2 H7.6"
              stroke-width="1.9"/>
      </g>""",
    "code": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M8.6,7.6 L3.6,12 L8.6,16.4" stroke-width="1.9"/>
        <path d="M15.4,7.6 L20.4,12 L15.4,16.4" stroke-width="1.9"/>
        <path d="M13.4,4.6 L10.6,19.4" stroke-width="1.9"/>
      </g>""",
    "check": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4.8,12.6 L9.6,17.4 L19.2,7.2" stroke-width="2.2"/>
      </g>""",
    "arrowBackIos": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14.4,5.6 L7.6,12 L14.4,18.4" stroke-width="1.9"/>
      </g>""",
    "send": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21,3.4 L10.6,13.8" stroke-width="1.9"/>
        <path d="M21,3.4 L14.2,21 L10.6,13.8 L3.4,10.2 Z" stroke-width="1.9"/>
      </g>""",
    "playlistAdd": """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.4,6.4 H12.6 M3.4,11.4 H12.6 M3.4,16.4 H8.6" stroke-width="1.9"/>
        <path d="M17.4,12.4 V20.4 M13.4,16.4 H21.4" stroke-width="1.9"/>
      </g>""",
}

ACTION_LABEL = {
    "delete": "删除", "add": "添加", "fullscreen": "放大", "close": "关闭",
    "tag": "标题", "merge": "汇总",
    "visibilityOff": "隐藏", "arrowUp": "收起", "arrowDown": "展开",
    "expandMore": "更多", "chevronRight": "进入", "star": "置顶",
    "restore": "恢复", "refresh": "刷新", "lockOpen": "解锁",
    "contentPaste": "粘贴", "contentCopy": "复制", "code": "源码",
    "check": "确定", "arrowBackIos": "返回", "send": "发送",
    "playlistAdd": "追加",
}

# 品牌标志 —— 方案 B（加粗版，最终采用）
BRAND_B = """
<g fill="none" stroke-linecap="round" stroke-linejoin="round">
  <path d="M33.5,9 H15 A6,6 0 0 0 9,15 V35 A6,6 0 0 0 15,41 H26"
        stroke="var(--primary)" stroke-width="3.4"/>
  <path d="M37,17 V15 A6,6 0 0 0 31,9" stroke="var(--primary)" stroke-width="3.4"/>
  <path d="M15,16.5 H29.5" stroke="var(--primary)"  stroke-width="3.4"/>
  <path d="M15,22.5 H25"   stroke="var(--primary2)" stroke-width="3.4"/>
  <path d="M15,28 H23.5"   stroke="var(--primary3)" stroke-width="3.4"/>
  <path d="M14.6,33.4 L18.2,37 L24.4,29.4"
        stroke="var(--primary)" stroke-width="3.8" stroke-linecap="round" stroke-linejoin="round"/>
</g>
<g stroke-linecap="round">
  <path d="M27.2,41 V37.4" stroke="var(--primary)"  stroke-width="3.2"/>
  <path d="M31.2,41 V34.2" stroke="var(--primary)"  stroke-width="3.2"/>
  <path d="M35.2,41 V31.4" stroke="var(--primary2)" stroke-width="3.2"/>
  <path d="M39.2,41 V28.2" stroke="var(--primary)"  stroke-width="3.2"/>
</g>
"""


# ============================================================
# 图标改版 v1：去重与语义区分（在初版定义之后覆盖）
#   · home      → 房子+对勾，避免与品牌标志、文档图标撞脸
#   · broom     → 扫帚，避免与垃圾桶（delete）撞脸
#   · expandMore→ V 形双箭头，避免与单箭头（arrowDown）撞脸
#   · restore   → 时钟+回滚，避免与 refresh 撞脸
#   · copy      → 与 contentCopy 重复且未被引用，直接移除
# ============================================================
NAV["home"] = """
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.4,10.6 L12,3.4 L20.6,10.6" stroke="currentColor" stroke-width="1.9"/>
        <path d="M5.8,12.4 V19.2 A1.8,1.8 0 0 0 7.6,21 H16.4 A1.8,1.8 0 0 0 18.2,19.2 V12.4"
              stroke="currentColor" stroke-width="1.9"/>
        <path d="M9.2,16.4 L11.4,18.6 L15,15" stroke="currentColor" stroke-width="1.9"/>
      </g>"""

FEATURE["broom"] = (
    FEATURE["broom"][0],
    """<g fill="none" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14.4,3.4 L20.6,9.6" stroke="currentColor" stroke-width="1.9"/>
                <path d="M12.4,5.4 L18.6,11.6 L11.6,18.6 H8.4 L7.2,15.6 Z"
                      stroke="currentColor" stroke-width="1.9"/>
                <path d="M8.8,16.4 L5.6,19.6 M11.6,15.2 L8.8,18.4"
                      stroke="currentColor" stroke-width="1.6"/>
              </g>""")

ACTION["expandMore"] = """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5.6,9.4 L12,15.6 L18.4,9.4" stroke-width="1.9"/>
      </g>"""

ACTION["restore"] = """<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12.4" cy="13" r="7.2" stroke-width="1.9"/>
        <path d="M12.4,9.2 V13 L15,14.6" stroke-width="1.8"/>
        <path d="M5.6,7.6 A7.2,7.2 0 0 1 8.4,5.2" stroke-width="1.8"/>
        <path d="M5,4.6 V8.2 H8.6" stroke-width="1.8"/>
      </g>"""

FEATURE.pop("copy", None)
