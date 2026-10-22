#!/usr/bin/env bash
# 打包原生工程（不含私钥、构建产物、缓存）
set -euo pipefail

SRC="/workspace/native"
OUT="/workspace/output"
NAME="工作报告生成器_原生版_工程源码_v1.3.0-beta1.zip"

mkdir -p "$OUT"
rm -f "$OUT/$NAME"

cd "$SRC"
zip -r -q "$OUT/$NAME" \
  -x "build/*" \
  -x "app/build/*" \
  -x ".gradle/*" \
  -x "keystore/*" \
  -x "*.keystore" \
  -x "*.jks" \
  -x "local.properties" \
  -x "*.log" \
  -x ".idea/*" \
  -x "build/kcheck/*"

echo "已生成: $OUT/$NAME"
unzip -l "$OUT/$NAME" | tail -3
echo "--- 私钥检查（应无输出）---"
unzip -l "$OUT/$NAME" | grep -Ei "keystore|\.jks" || echo "✅ 无任何私钥文件"
