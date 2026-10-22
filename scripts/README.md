# scripts/

## pinyin_table.js / gen_pinyin_table.js

「敏感词 → 小写拼音首字母」对照表及其生成脚本。

- `pinyin_table.js`：26 组汉字（按首字母 a~z 分组，用 `|` 分隔），共 20856 个常用汉字。
  该表已内联进 `app/src/main/assets/index.html`（`var PINYIN_TABLE = ...`），App 与网页版共用。
- `gen_pinyin_table.js`：重新生成该表的脚本，依赖 `pinyin-pro`：

  ```bash
  npm i pinyin-pro
  node scripts/gen_pinyin_table.js
  ```

生成后需把表内容同步回 `app/src/main/assets/index.html` 的 `PINYIN_TABLE` 常量。
