#!/usr/bin/env bash
# ============================================================
#  工作报告生成器 · 一键构建脚本
#
#  用法：
#     ./build.sh                # 构建并签名，产物在 dist/
#     ./build.sh --unsigned     # 只构建不签名（调试用）
#
#  依赖：JDK 17（或更高）、bash、python3、curl
#  工具链（aapt2 / d8 / zipalign / apksigner / android.jar）会自动下载到 tools/
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

# ---------- 可通过环境变量覆盖 ----------
KEYSTORE_PATH="${KEYSTORE_PATH:-keystore/release.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-workreport2026}"
KEY_ALIAS="${KEY_ALIAS:-workreport}"
KEY_PASS="${KEY_PASS:-$KEYSTORE_PASS}"

TOOLS_DIR="tools"
DIST_DIR="dist"
BUILD_DIR="build"
ANDROID_JAR="$TOOLS_DIR/platform/android-35/android.jar"
BUILD_TOOLS="$TOOLS_DIR/android-14"

UNSIGNED=0
[ "${1:-}" = "--unsigned" ] && UNSIGNED=1

# ---------- 工具函数 ----------
say()  { printf "\033[36m▸\033[0m %s\n" "$*"; }
ok()   { printf "\033[32m✓\033[0m %s\n" "$*"; }
die()  { printf "\033[31m✗ %s\033[0m\n" "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"; }

# ---------- 检查环境 ----------
need java; need curl; need unzip; need python3

if [ -z "${JAVA_HOME:-}" ]; then
  JAVAC_BIN="$(command -v javac)"
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVAC_BIN")")")"
  export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"
say "JDK: $(java -version 2>&1 | head -1)"

mkdir -p "$TOOLS_DIR" "$DIST_DIR" "$BUILD_DIR"

# ---------- 下载工具链 ----------
fetch() { # url dest
  local url="$1" dest="$2"
  [ -f "$dest" ] && return 0
  say "下载 $(basename "$dest") ..."
  curl -fsSL --retry 3 -o "$dest.part" "$url" || die "下载失败：$url"
  mv "$dest.part" "$dest"
}

# build-tools
if [ ! -x "$BUILD_TOOLS/aapt2" ]; then
  fetch "https://dl.google.com/android/repository/build-tools_r34-linux.zip" "$TOOLS_DIR/build-tools.zip"
  say "解压 build-tools ..."
  rm -rf "$TOOLS_DIR/bt_tmp" && mkdir -p "$TOOLS_DIR/bt_tmp"
  unzip -q "$TOOLS_DIR/build-tools.zip" -d "$TOOLS_DIR/bt_tmp"
  rm -rf "$BUILD_TOOLS"
  mv "$TOOLS_DIR/bt_tmp"/android-* "$BUILD_TOOLS"
  rm -rf "$TOOLS_DIR/bt_tmp"
  chmod +x "$BUILD_TOOLS"/{aapt2,d8,zipalign,apksigner} 2>/dev/null || true
fi
ok "build-tools 就绪"

# android.jar (API 35)
if [ ! -f "$ANDROID_JAR" ]; then
  fetch "https://dl.google.com/android/repository/platform-35_r02.zip" "$TOOLS_DIR/platform.zip"
  say "解压 android platform ..."
  rm -rf "$TOOLS_DIR/pl_tmp" && mkdir -p "$TOOLS_DIR/pl_tmp"
  unzip -q "$TOOLS_DIR/platform.zip" -d "$TOOLS_DIR/pl_tmp"
  mkdir -p "$TOOLS_DIR/platform"
  mv "$TOOLS_DIR/pl_tmp"/* "$TOOLS_DIR/platform" 2>/dev/null || true
  rm -rf "$TOOLS_DIR/pl_tmp"
fi
[ -f "$ANDROID_JAR" ] || die "未找到 $ANDROID_JAR"
ok "android.jar 就绪"

# ---------- 1) 编译资源 ----------
say "aapt2 compile（资源）"
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"/{gen,classes,dex}
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o "$BUILD_DIR/res.zip"

# ---------- 2) 链接资源 + 清单 ----------
say "aapt2 link（资源 + AndroidManifest）"
"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD_DIR/base.apk" \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_JAR" \
  --java "$BUILD_DIR/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  "$BUILD_DIR/res.zip"

# ---------- 3) 编译 Java ----------
say "javac（Java 源码）"
find app/src/main/java -name "*.java" > "$BUILD_DIR/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"

# ---------- 4) 转 dex ----------
say "d8（dex）"
"$BUILD_TOOLS/d8" --min-api 21 --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" $(find "$BUILD_DIR/classes" -name "*.class")

# ---------- 5) 合并 assets + dex ----------
say "打包（assets + classes.dex）"
python3 - "$BUILD_DIR" <<'PY'
import sys, zipfile
bd = sys.argv[1]
base = zipfile.ZipFile(f"{bd}/base.apk", "r")
out  = zipfile.ZipFile(f"{bd}/merged.apk", "w")
for item in base.infolist():
    zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    zi.compress_type = item.compress_type
    zi.external_attr = item.external_attr
    out.writestr(zi, base.read(item.filename))
base.close()
out.write("app/src/main/assets/index.html", "assets/index.html", zipfile.ZIP_DEFLATED)
zi = zipfile.ZipInfo("classes.dex", date_time=(2026, 1, 1, 0, 0, 0))
zi.compress_type = zipfile.ZIP_STORED
out.writestr(zi, open(f"{bd}/dex/classes.dex", "rb").read())
out.close()
PY

# ---------- 6) 对齐 ----------
say "zipalign"
"$BUILD_TOOLS/zipalign" -p -f 4 "$BUILD_DIR/merged.apk" "$BUILD_DIR/aligned.apk"

# ---------- 7) 签名 ----------
APK_NAME="工作报告生成器_v$(grep -o 'versionName="[^"]*"' app/src/main/AndroidManifest.xml | head -1 | cut -d'"' -f2).apk"
OUT_APK="$DIST_DIR/$APK_NAME"

if [ "$UNSIGNED" = "1" ]; then
  say "跳过签名（--unsigned）"
  cp "$BUILD_DIR/aligned.apk" "$DIST_DIR/${APK_NAME%.apk}_unsigned.apk"
  ok "产物：$DIST_DIR/${APK_NAME%.apk}_unsigned.apk"
  exit 0
fi

if [ ! -f "$KEYSTORE_PATH" ]; then
  die "找不到签名证书：$KEYSTORE_PATH
   请把 keystore/release.keystore 放到该位置，或用环境变量 KEYSTORE_PATH 指定。
   只想先出包看看？加 --unsigned 参数。"
fi

say "apksigner（签名）"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE_PATH" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT_APK" "$BUILD_DIR/aligned.apk"

# ---------- 8) 校验 ----------
"$BUILD_TOOLS/apksigner" verify "$OUT_APK" >/dev/null
"$BUILD_TOOLS/zipalign" -c 4 "$OUT_APK" >/dev/null

ok "构建完成 → $OUT_APK"
ls -lh "$OUT_APK"
say "校验通过：v1+v2+v3 签名 / 4 字节对齐"
