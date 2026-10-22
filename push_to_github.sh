#!/usr/bin/env bash
# ============================================================
#  一键推送到 GitHub 并发布 Release
#
#  用法：
#      TOKEN=ghp_xxx ./push_to_github.sh <用户名> <仓库名> [public|private]
#
#  做了四件事：
#      1. 仓库不存在 → 自动创建
#      2. git push 推送 main 分支
#      3. 打 tag v1.0.0 并推送
#      4. 创建 Release 并把 APK 挂上去
#
#  Token 只存在于本进程环境变量，不会写进 .git/config 或磁盘
# ============================================================
set -euo pipefail

USER_NAME="${1:?用法: TOKEN=xxx ./push_to_github.sh <用户名> <仓库名> [public|private]}"
REPO_NAME="${2:?缺少仓库名}"
VISIBILITY="${3:-private}"
TAG="v1.0.0"

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_DIR"

[ -n "${TOKEN:-}" ] || { echo "✗ 请先设置 TOKEN 环境变量"; exit 1; }

API="https://api.github.com"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

say() { printf "\033[36m▸\033[0m %s\n" "$*"; }
ok()  { printf "\033[32m✓\033[0m %s\n" "$*"; }
die() { printf "\033[31m✗ %s\033[0m\n" "$*" >&2; exit 1; }

# ---------- 0) 校验 Token ----------
say "校验 Token ..."
LOGIN="$(curl -fsS "${AUTH[@]}" "$API/user" | python3 -c 'import sys,json; print(json.load(sys.stdin)["login"])' 2>/dev/null)" \
  || die "Token 无效或已过期"
ok "Token 有效，账号：$LOGIN"

# ---------- 1) 仓库存在吗 ----------
say "检查仓库 $USER_NAME/$REPO_NAME ..."
EXIST=0
curl -fsS "${AUTH[@]}" "$API/repos/$USER_NAME/$REPO_NAME" >/dev/null 2>&1 && EXIST=1

if [ "$EXIST" = "0" ]; then
  say "仓库不存在，创建一个 $VISIBILITY 仓库 ..."
  PAYLOAD=$(python3 -c "
import json,sys
print(json.dumps({
  'name': sys.argv[1],
  'private': sys.argv[2] == 'private',
  'description': '工作报告生成器 · 加密周报 ZIP 一键生成 / 自动复制文件名 / 自动发邮件',
  'has_issues': True,
  'has_wiki': False,
}))" "$REPO_NAME" "$VISIBILITY")
  curl -fsS -X POST "${AUTH[@]}" -H "Content-Type: application/json" \
       -d "$PAYLOAD" "$API/user/repos" >/dev/null \
    || die "创建仓库失败"
  ok "仓库已创建（$VISIBILITY）"
else
  ok "仓库已存在，直接使用"
fi

# ---------- 2) 推送代码 ----------
say "推送 main 分支 ..."
# 用 extraheader 传 token，避免把凭据写进 .git/config
BASIC=$(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)
git -c "http.extraheader=Authorization: Basic $BASIC" \
    remote add origin "https://github.com/$USER_NAME/$REPO_NAME.git" 2>/dev/null \
  || git -c "http.extraheader=Authorization: Basic $BASIC" \
       remote set-url origin "https://github.com/$USER_NAME/$REPO_NAME.git"
git -c "http.extraheader=Authorization: Basic $BASIC" push -u origin main
ok "代码已推送"

# ---------- 3) 打 tag ----------
say "推送 tag $TAG ..."
git tag -f "$TAG" >/dev/null 2>&1 || true
git -c "http.extraheader=Authorization: Basic $BASIC" push origin "$TAG" --force
ok "tag 已推送"

# ---------- 4) 发 Release ----------
APK="$(ls -1 dist/*.apk 2>/dev/null | head -1 || true)"
if [ -z "$APK" ]; then
  say "dist/ 下没找到 APK，跳过 Release 创建（可稍后手动发）"
else
  say "创建 Release ..."
  REL_JSON=$(curl -fsS -X POST "${AUTH[@]}" -H "Content-Type: application/json" -d @- "$API/repos/$USER_NAME/$REPO_NAME/releases" <<'JSON'
{
  "tag_name": "v1.0.0",
  "name": "工作报告生成器 v1.0.0",
  "body": "首个版本。\n\n- 加密 ZIP 周报生成，文件名自动复制到剪贴板\n- 自动拉起自选邮箱 App，把压缩包作为附件带入\n- 纯离线运行，不申请 INTERNET 权限\n\n**安装**：下载 APK 后直接安装。首次安装如提示「未知来源」，到 设置 → 应用 → 特殊应用权限 → 安装未知应用 放行一次。\n\n**网页版**：见仓库 `web/workreport-web.htm`，浏览器打开即可，离线可用。",
  "draft": false,
  "prerelease": false
}
JSON
)
  REL_ID=$(printf '%s' "$REL_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
  UPLOAD_URL=$(printf '%s' "$REL_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["upload_url"])')
  ASSET_NAME="$(basename "$APK")"

  say "上传附件 $ASSET_NAME ..."
  curl -fsS -X POST "${AUTH[@]}" \
       -H "Content-Type: application/vnd.android.package-archive" \
       --data-binary "@$APK" \
       "$UPLOAD_URL?name=$ASSET_NAME" >/dev/null
  ok "Release 已发布"
  echo
  printf "\033[32m🎉 完成！永久下载地址：\033[0m\n"
  printf "   APK  : https://github.com/%s/%s/releases/download/%s/%s\n" "$USER_NAME" "$REPO_NAME" "$TAG" "$ASSET_NAME"
  printf "   仓库 : https://github.com/%s/%s\n" "$USER_NAME" "$REPO_NAME"
  echo
  printf "\033[33m⚠️  现在去 Settings → Developer settings → Personal access tokens 撤销这个 Token\033[0m\n"
fi
