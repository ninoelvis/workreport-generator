# scripts/icons/

应用图标的矢量源与渲染脚本。

- `make_icon.py` —— 生成 4 个 SVG（`legacy` 圆角方形 / `adapt_bg` 自适应背景 /
  `adapt_fg` 自适应前景 / `round` 圆形），设计为「卷轴 + 笔 + 星光」主题。
- `render.js` —— 用 Playwright(Chromium) 把 SVG 渲染成各密度 PNG。
  ImageMagick 内置的 SVG 渲染器不支持滤镜与嵌套 transform，**不要用它**。

```bash
python3 scripts/icons/make_icon.py          # 出 SVG
node    scripts/icons/render.js              # 出 PNG
```

渲染产物需手动复制到 `app/src/main/res/mipmap-*/`：

| 文件 | 用途 |
|---|---|
| `ic_launcher.png` | Android 8 以下使用的传统图标（圆角方形） |
| `ic_launcher_round.png` | 圆形图标（部分桌面 / 旧版圆形启动器） |
| `ic_launcher_foreground.png` | 自适应图标前景层（108dp 各密度） |
| `ic_launcher_background` | 自适应图标背景层，用 `res/drawable/ic_launcher_background.xml` 代替位图 |
