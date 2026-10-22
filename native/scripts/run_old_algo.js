/**
 * 交叉验证入口（旧版 JS）：对固定测试集跑一遍旧版算法，输出 JSON。
 */
const old = require('./old_algo.js');

const CASES = require('./algo_cases.json');

function d(s) {
  const [y, m, day] = s.split('-').map(Number);
  return new Date(y, m - 1, day);
}

const out = [];

// 1. 日期解析
CASES.dateRanges.forEach((s) => {
  const r = old.parseDateRange(s);
  out.push(r ? `${s} => ${old.fmtDate(r.start)}~${old.fmtDate(r.end)}` : `${s} => null`);
});

// 2. 周范围 / 周四 / 月内周数 / 密码
CASES.dates.forEach((s) => {
  const date = d(s);
  const { sunday, saturday } = old.getWeekRange(date);
  const thu = old.getThursday(sunday);
  out.push(
    `${s} => week ${old.fmtDate(sunday)}-${old.fmtDate(saturday)}` +
    ` thu=${old.fmtDate8(thu)} weekOfMonth=${old.getWeekOfMonth(sunday)}`
  );
});

// 3. 文本格式化 + 敏感词
CASES.texts.forEach((t) => {
  out.push(`DONE<<<${old.formatDoneWorks(t)}>>>`);
  out.push(`PLAN<<<${old.formatPlanWorks(t)}>>>`);
  out.push(`SENS<<<${old.replaceSensitive(t)}>>>`);
});

// 4. 导出命名与密码
CASES.exports.forEach(([name, range]) => {
  const r = old.computeExportInfo(name, range);
  if (!r) { out.push(`EXPORT ${name}|${range} => null`); return; }
  out.push(`EXPORT ${name}|${range} => ${r.zipFileName} | ${r.xlsxFileName} | pwd=${r.password} | ${r.year}-${r.month} 第${r.weekNum}周`);
});

process.stdout.write(out.join('\n'));
