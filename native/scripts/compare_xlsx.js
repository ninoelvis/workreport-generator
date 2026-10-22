#!/usr/bin/env node
/**
 * xlsx 一致性对比：把「原生 Kotlin 生成的 xlsx」与「WebView 旧版生成的 xlsx」
 * 逐项比对（单元格文本、样式、合并、列宽、行高）。
 *
 * 用法：node compare_xlsx.js <原生xlsx> <旧版xlsx>
 */
const fs = require('fs');
const path = require('path');
const XLSX = require('/tmp/pp.js');

const [, , aPath, bPath] = process.argv;
if (!aPath || !bPath) {
  console.error('用法: node compare_xlsx.js <原生xlsx> <旧版xlsx>');
  process.exit(2);
}

function load(p) {
  const wb = XLSX.read(fs.readFileSync(p), { type: 'buffer', cellStyles: true });
  const name = wb.SheetNames[0];
  const ws = wb.Sheets[name];
  const cells = {};
  for (const addr of Object.keys(ws)) {
    if (addr[0] === '!') continue;
    const c = ws[addr];
    cells[addr] = { v: c.v, t: c.t, s: c.s || null };
  }
  return {
    sheetName: name,
    cells,
    merges: (ws['!merges'] || []).map(m => `${m.s.r},${m.s.c}-${m.e.r},${m.e.c}`).sort(),
    cols: (ws['!cols'] || []).map(c => c && c.wch),
    rows: (ws['!rows'] || []).map(r => r && r.hpx),
  };
}

const A = load(aPath); // 原生
const B = load(bPath); // 旧版

let fail = 0;
function check(label, ok, extra) {
  if (ok) { console.log('  ✅ ' + label); }
  else { fail++; console.log('  ❌ ' + label + (extra ? '  → ' + extra : '')); }
}

console.log('原生: ' + aPath);
console.log('旧版: ' + bPath);
console.log('');

console.log('【1】工作表名');
check('工作表名一致: ' + A.sheetName, A.sheetName === B.sheetName, A.sheetName + ' vs ' + B.sheetName);

console.log('\n【2】单元格文本');
const addrs = [...new Set([...Object.keys(A.cells), ...Object.keys(B.cells)])].sort();
let cellDiff = 0;
for (const addr of addrs) {
  const a = A.cells[addr], b = B.cells[addr];
  const av = a ? String(a.v ?? '') : null;
  const bv = b ? String(b.v ?? '') : null;
  if (av !== bv) {
    cellDiff++;
    console.log(`    ${addr}: 原生=${JSON.stringify(av)} 旧版=${JSON.stringify(bv)}`);
  }
}
check(`全部 ${addrs.length} 个单元格文本一致`, cellDiff === 0, cellDiff + ' 处不同');

console.log('\n【3】合并区域');
check('合并区域一致 (' + A.merges.length + ' 个)', JSON.stringify(A.merges) === JSON.stringify(B.merges),
  '\n      原生: ' + A.merges.join(' | ') + '\n      旧版: ' + B.merges.join(' | '));

console.log('\n【4】列宽 / 行高');
check('列宽一致', JSON.stringify(A.cols) === JSON.stringify(B.cols), JSON.stringify(A.cols) + ' vs ' + JSON.stringify(B.cols));
check('行高一致', JSON.stringify(A.rows) === JSON.stringify(B.rows), JSON.stringify(A.rows) + ' vs ' + JSON.stringify(B.rows));

console.log('\n【5】样式（字体/对齐/填充/边框）');
let styleDiff = 0;
for (const addr of addrs) {
  const a = A.cells[addr], b = B.cells[addr];
  if (!a || !b) continue;
  const norm = s => s ? {
    font: s.font ? [s.font.name, s.font.sz, !!s.font.bold, s.font.color && s.font.color.rgb] : null,
    align: s.alignment ? [s.alignment.horizontal, s.alignment.vertical, !!s.alignment.wrapText] : null,
    fill: s.fgColor ? s.fgColor.rgb : (s.fgColor && s.fgColor.rgb) || null,
    border: s.border ? ['left', 'right', 'top', 'bottom'].map(k => s.border[k] && s.border[k].style).join(',') : null,
  } : null;
  const na = JSON.stringify(norm(a.s));
  const nb = JSON.stringify(norm(b.s));
  if (na !== nb) {
    styleDiff++;
    console.log(`    ${addr}:\n      原生 ${na}\n      旧版 ${nb}`);
  }
}
check(`全部单元格样式一致`, styleDiff === 0, styleDiff + ' 处不同');

console.log('\n' + '─'.repeat(50));
console.log(fail === 0 ? '🎉 原生版与旧版 Excel 完全一致' : `⚠️ ${fail} 项不一致`);
process.exit(fail === 0 ? 0 : 1);
