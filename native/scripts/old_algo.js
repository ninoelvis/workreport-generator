/**
 * 旧版 WebView（index.html）算法实现 —— 用于与原生 Kotlin 版交叉验证。
 * 函数体逐字摘自 /workspace/repo/app/src/main/assets/index.html
 */
function fmtDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return y + '.' + m + '.' + day;
}
function fmtDate8(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return y + m + day;
}
function getWeekRange(date) {
  const d = new Date(date);
  const dayOfWeek = d.getDay();
  const sunday = new Date(d);
  sunday.setDate(d.getDate() - dayOfWeek);
  const saturday = new Date(sunday);
  saturday.setDate(sunday.getDate() + 6);
  return { sunday: sunday, saturday: saturday };
}
function getThursday(sunday) {
  const thu = new Date(sunday);
  thu.setDate(sunday.getDate() + 4);
  return thu;
}
function getWeekOfMonth(sunday) {
  const d = new Date(sunday);
  const month = d.getMonth();
  const year = d.getFullYear();
  const firstDay = new Date(year, month, 1);
  let firstSunday = new Date(firstDay);
  while (firstSunday.getDay() !== 0) {
    firstSunday.setDate(firstSunday.getDate() + 1);
  }
  let count = 0;
  const cursor = new Date(firstSunday);
  while (cursor <= d) {
    if (cursor.getDay() === 0) count++;
    cursor.setDate(cursor.getDate() + 7);
  }
  return count;
}
function parseDateRange(str) {
  var m = str.match(/^(\d{4})\.(\d{2})\.(\d{2})-(\d{4})\.(\d{2})\.(\d{2})$/);
  if (!m) return null;
  return {
    start: new Date(+m[1], +m[2] - 1, +m[3]),
    end: new Date(+m[4], +m[5] - 1, +m[6])
  };
}

var DEFAULT_SENSITIVE = {
  '纪委': 'jw', '监委': 'jw', '案管': 'ag', '审理': 'sl', '党风': 'df',
  '一体化': 'yth', '查询': 'cx', '措施': 'cs', '线索': 'xs', '案件': 'aj',
  '信访': 'xf', '法规': 'fg', '保密': 'bm'
};
var SENSITIVE_MAP = Object.assign({}, DEFAULT_SENSITIVE);

function replaceSensitive(text) {
  for (var key in SENSITIVE_MAP) {
    if (SENSITIVE_MAP.hasOwnProperty(key)) {
      text = text.split(key).join(SENSITIVE_MAP[key]);
    }
  }
  return text;
}
function formatDoneWorks(text) {
  var lines = text.split('\n').filter(function (l) { return l.trim() !== ''; });
  var n = lines.length;
  if (n === 0) return '';
  var result = '完成共' + n + '项工作内容：\n';
  for (var i = 0; i < n; i++) {
    var endChar = (i === n - 1) ? '。' : '；';
    result += (i + 1) + '、' + replaceSensitive(lines[i].trim()) + endChar + '\n';
  }
  return result.trim();
}
function formatPlanWorks(text) {
  var lines = text.split('\n').filter(function (l) { return l.trim() !== ''; });
  var n = lines.length;
  if (n === 0) return '';
  var result = '下周预计共' + n + '项工作任务：\n';
  for (var i = 0; i < n; i++) {
    var endChar = (i === n - 1) ? '。' : '；';
    result += (i + 1) + '、' + replaceSensitive(lines[i].trim()) + endChar + '\n';
  }
  return result.trim();
}

// 导出命名与密码（逐字复刻 index.html 中导出逻辑）
function computeExportInfo(userName, timeRangeText) {
  var parsed = parseDateRange(timeRangeText);
  if (!parsed) return null;
  var sunday = parsed.start;
  var thu = new Date(sunday);
  thu.setDate(sunday.getDate() + 4);
  var password = fmtDate8(thu);
  var weekNum = getWeekOfMonth(sunday);
  var month = sunday.getMonth() + 1;
  var year = sunday.getFullYear();
  var name = (userName || '').trim() || '\u59d3\u540d';
  var baseName = name + '-' + year + '\u5e74' + month + '\u6708\u7b2c' + weekNum + '\u5468\u5de5\u4f5c\u62a5\u544a';
  return {
    baseName: baseName,
    zipFileName: baseName + '.zip',
    xlsxFileName: baseName + '.xlsx',
    password: password,
    year: year, month: month, weekNum: weekNum
  };
}

module.exports = {
  computeExportInfo,
  fmtDate, fmtDate8, getWeekRange, getThursday, getWeekOfMonth,
  parseDateRange, replaceSensitive, formatDoneWorks, formatPlanWorks,
  DEFAULT_SENSITIVE
};
