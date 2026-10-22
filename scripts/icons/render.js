// 用 Playwright(Chromium) 渲染 SVG → PNG，质量和浏览器里完全一致
const fs = require('fs');
const path = require('path');
const { chromium } = require('/usr/local/lib/node_modules/playwright');

const DIR = '/workspace/icons';

// 传统图标尺寸 + 自适应图标尺寸（前景/背景 108dp）
const DENSITIES = [
  ['mdpi', 48, 108],
  ['hdpi', 72, 162],
  ['xhdpi', 96, 216],
  ['xxhdpi', 144, 324],
  ['xxxhdpi', 192, 432],
];

async function shoot(page, svg, size, outFile, transparent) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(
    `<!doctype html><html><head><style>
      html,body{margin:0;padding:0;width:${size}px;height:${size}px;overflow:hidden;background:${transparent ? 'transparent' : '#fff'};}
      svg{display:block;width:${size}px;height:${size}px;}
    </style></head><body>${svg}</body></html>`,
    { waitUntil: 'load' }
  );
  await page.screenshot({ path: outFile, omitBackground: transparent });
}

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const page = await browser.newPage({ viewport: { width: 1024, height: 1024 }, deviceScaleFactor: 1 });
  const read = n => fs.readFileSync(path.join(DIR, n + '.svg'), 'utf8');

  const legacy = read('legacy');
  const adaptBg = read('adapt_bg');
  const adaptFg = read('adapt_fg');
  const round = read('round');

  // ---- 传统 mipmap ic_launcher.png ----
  for (const [d, legacySize] of DENSITIES) {
    const out = path.join(DIR, `mipmap-${d}`, 'ic_launcher.png');
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await shoot(page, legacy, legacySize, out, true);
    console.log('  ✅', path.relative(DIR, out), legacySize);
  }
  // Play Store / 站点用大图
  for (const size of [512, 1024]) {
    const out = path.join(DIR, `icon-${size}.png`);
    await shoot(page, legacy, size, out, false);
    console.log('  ✅', path.relative(DIR, out), size);
  }

  // ---- 自适应图标前景/背景 ----
  for (const [d, , adaptSize] of DENSITIES) {
    for (const [name, svg] of [['ic_launcher_foreground', adaptFg], ['ic_launcher_background', adaptBg]]) {
      const out = path.join(DIR, `mipmap-${d}`, name + '.png');
      fs.mkdirSync(path.dirname(out), { recursive: true });
      await shoot(page, svg, adaptSize, out, name === 'ic_launcher_foreground');
      console.log('  ✅', path.relative(DIR, out), adaptSize);
    }
  }

  // ---- 圆形图标 ----
  for (const [d, legacySize] of DENSITIES) {
    const out = path.join(DIR, `mipmap-${d}`, 'ic_launcher_round.png');
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await shoot(page, round, legacySize, out, true);
    console.log('  ✅', path.relative(DIR, out), legacySize);
  }

  // ---- 网页版 favicon ----
  for (const size of [32, 180]) {
    const out = path.join(DIR, `favicon-${size}.png`);
    await shoot(page, round, size, out, true);
    console.log('  ✅', path.relative(DIR, out), size);
  }
  await shoot(page, round, 512, path.join(DIR, 'icon-512.png'), false);
  console.log('  ✅ icon-512.png (round)');

  await browser.close();
  console.log('全部渲染完成');
})();
