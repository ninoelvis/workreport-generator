// 用 pinyin-pro 生成「汉字 → 拼音首字母」紧凑表
// 依赖 pinyin-pro：npm i pinyin-pro（或从 CDN 取 dist/index.js 放到同目录）
const { pinyin } = require('./pinyin-pro.js');

const groups = [];
for (let g = 0; g < 26; g++) groups.push([]);

const START = 0x4e00, END = 0x9fff;
let count = 0, miss = 0;
const t0 = Date.now();

for (let code = START; code <= END; code++) {
  const ch = String.fromCharCode(code);
  let r;
  try {
    r = pinyin(ch, { pattern: 'first', toneType: 'none', type: 'array' });
  } catch (e) { r = null; }
  const s = r && r[0] ? String(r[0]).toLowerCase() : '';
  if (!s || s.length !== 1 || s < 'a' || s > 'z') { miss++; continue; }
  groups[s.charCodeAt(0) - 97].push(ch);
  count++;
}
console.log('耗时 %dms，覆盖 %d 字，跳过 %d', Date.now() - t0, count, miss);

const table = groups.map(g => g.join('')).join('|');
console.log('表长度: %d 字符 (UTF-16)', table.length);
console.log('字节数: %d', Buffer.byteLength(table, 'utf8'));

// 自检：现有 14 条敏感词的缩写
const DEFAULT = {
  '纪委': 'jw', '监委': 'jw', '案管': 'ag', '审理': 'sl', '党风': 'df',
  '一体化': 'yth', '查询': 'cx', '措施': 'cs', '线索': 'xs', '案件': 'aj',
  '信访': 'xf', '法规': 'fg', '保密': 'bm',
};
let bad = 0;
for (const [w, expect] of Object.entries(DEFAULT)) {
  const got = pinyin(w, { pattern: 'first', toneType: 'none', type: 'array' }).join('').toLowerCase();
  const ok = got === expect;
  if (!ok) { bad++; console.log('  ⚠️  %s → 算出 %s，期望 %s', w, got, expect); }
}
console.log(bad === 0 ? '✅ 现有 14 条敏感词缩写与逐字首字母规则完全一致' : '❌ %d 条不一致', bad);

require('fs').writeFileSync(__dirname + '/pinyin_table.js',
  '// 常用汉字 → 拼音首字母对照表（自动生成，勿手改）\n' +
  '// 26 组，用 | 分隔，依次对应 a~z\n' +
  'var PINYIN_TABLE = ' + JSON.stringify(table) + ';\n');

console.log('✅ 已写入 scripts/pinyin_table.js (%d 字节)', Buffer.byteLength(table, 'utf8'));
