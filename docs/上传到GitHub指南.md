# 上传到 GitHub · 完整操作指南

本文档有两种走法：

- **路线 A（推荐给我代劳）**：把一个 Token 贴给我，我帮你建仓库、推代码、发 Release。
- **路线 B（你自己做，Token 不经过任何人）**：在你自己的电脑上执行，5 分钟搞定。

---

## 怎么查看 / 获取 Token

GitHub 现在有两种 Token，新手用 **Fine-grained（细粒度）** 更安全——它可以只授权某一个仓库、设短期有效期。

### 方式一：Fine-grained Token（推荐）

1. 登录 GitHub，点右上角**头像** → **Settings**（设置）
2. 左侧栏滑到最底部 → **Developer settings**（开发者设置）
3. 左侧 **Personal access tokens** → **Fine-grained tokens**（细粒度令牌）
4. 点 **Generate new token**
5. 填写：
   - **Token name**：随便起，比如 `workreport-push`
   - **Expiration**：选 **90 days**（或 30 days，别选永久）
   - **Repository access**：选 **Only select repositories** → 勾选你要放这个项目的仓库
     （如果还没建仓库，先用方式二建好空仓库再回来）
   - **Repository permissions** → 找到 **Contents**，改成 **Read and write** ✅
     - 如果还要建新仓库，把上面的 **Account permissions → Administration** 勾上（可选）
6. 拉到底点 **Generate token**
7. ⚠️ **页面只显示这一次**，立刻复制（形如 `github_pat_xxxxx...`）

### 方式二：Classic Token（老牌，步骤更少）

1. **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
2. 点 **Generate new token (classic)**
3. **Note** 随便填 → **Expiration** 选 90 days
4. 勾选 **`repo`** 这个权限（勾了它才有读写仓库、建 Release 的权限）
5. 点底部 **Generate token**，**立刻复制**（形如 `ghp_xxxxx...`）

### Token 长什么样？

```
Fine-grained:  github_pat_11ABCDEFG0aBcDeFgHiJkL_mNoPqRsTuVwXyZ0123456789
Classic:      ghp_16C7e42F292c6912E7710c838347Ae178B4a
```

**两者都能用**，随便哪个。

---

## 路线 A：给我 Token，我帮你推

把 Token 贴进对话，告诉我：

- 你的 **GitHub 用户名**（个人主页地址栏里那个）
- 仓库名（想叫什么都行，比如 `workreport-generator`）
- 仓库要 **Public** 还是 **Private**（我建议 **Private**——这是个内部工具）

我会依次完成：

1. `git remote add origin https://github.com/<你>/<仓库名>.git`
2. `git push -u origin main`
3. 帮你建 GitHub Release，把 APK 挂上去
4. 给你一个**永久稳定的下载地址**：
   ```
   https://github.com/<你>/<仓库名>/releases/download/v1.0.0/工作报告生成器_v1.0.apk
   ```

> 🔒 **安全提醒**：Token 等于你 GitHub 账号的钥匙，贴给我后**请立刻去
> Settings → Developer settings → Personal access tokens 把这个 Token 删掉（Revoke）**。
> 仓库推完之后它就没用了。我用完也会提醒你。

---

## 路线 B：你自己推（Token 全程不经过别人）

### B-1 装 Git（如果没装）

- Windows：到 <https://git-scm.com/download/win> 下载安装，一路下一步
- macOS：自带 Terminal 就行；没装就 `brew install git`
- Linux：`sudo apt install git`

### B-2 推代码

解压仓库压缩包，进入目录，依次执行：

```bash
# 1. 放签名私钥（本地，git 会自动忽略，不会被推上去）
cp /你的路径/release.keystore keystore/

# 2. 初始化并提交
git init -b main
git add .
git commit -m "feat: 工作报告生成器 v1.0.0"

# 3. 关联远程仓库并推送
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

推送时 GitHub 会提示：

```
Username for 'https://github.com': <你的用户名>
Password for 'https://github.com': <在这里粘贴你的 Token>
```

> ⚠️ **Password 位置填 Token**，不是你的 GitHub 登录密码。
> 粘贴时终端不显示任何字符，是正常的，粘完直接回车。
> （如果它要密码而不是 Token，说明本地 git 没开 credential helper，
> 改用 `git config --global credential.helper store` 再试，
> 或改用下面的 B-3 方式。）

### B-3 更省事的推送方式（用 GitHub 官方 CLI，免手输 Token）

```bash
# 装 GitHub CLI
# macOS:  brew install gh
# Windows: winget install --id GitHub.cli
# Linux:  参见 https://github.com/cli/cli/blob/trunk/docs/install_linux.md

gh auth login          # 选 GitHub.com → HTTPS → 浏览器授权
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

`gh auth login` 会给你一个**一次性验证码**，在浏览器里输入即可授权，**Token 永远不经过聊天窗口**。

---

## 发 Release（做出长期下载链接）

**不要把 APK 提交进 git**（会让仓库历史永久膨胀，而且每次改版都存一份完整副本）。
用 GitHub Release 分发：

```bash
# 1. 打标签
git tag v1.0.0
git push origin v1.0.0
```

2. 打开仓库页面 → 右侧 **Releases** → 点 **Draft a new release**
3. **Choose a tag** 选 `v1.0.0`
4. 标题填 `工作报告生成器 v1.0.0`
5. 把 APK 文件**拖进页面**（或用 `gh release create v1.0.0 dist/*.apk`）
6. 点 **Publish release**

下载地址（永久稳定）：

```
https://github.com/<你的用户名>/<仓库名>/releases/download/v1.0.0/工作报告生成器_v1.0.apk
```

---

## 常见问题

**Q: 推送时提示 `remote: Permission denied`？**
A: Token 权限不够。Classic Token 要勾 `repo`；Fine-grained 要把仓库的 **Contents** 权限改成 Read and write，并确认 **Repository access** 里选了这个仓库。

**Q: 提示 `Updates were rejected because the remote contains work that you do not have locally`？**
A: 远端仓库里有东西（多半是你在网页上建仓库时勾了 README）。二选一：
```bash
git pull origin main --allow-unrelated-histories
git push -u origin main
```
或者新建一个空仓库重来（推荐，省事）。

**Q: 私钥会不会被推上去？**
A: 不会。仓库根目录的 `.gitignore` 里有 `*.keystore`，`git add .` 会自动跳过它。
你可以用这条命令自查：
```bash
git ls-files | grep keystore
```
只会看到 `keystore/README.md` 和 `keystore/.gitkeep`，看不到 `.keystore` 文件。

**Q: 以后怎么出新版本？**
A: 改完代码后：
```bash
./build.sh                      # 产出 dist/xxx.apk
git add . && git commit -m "feat: xxx"
git tag v1.1.0 && git push origin main v1.1.0
```
然后去 Releases 页面发新版本。**记得用同一个 keystore 签名**，
否则用户手机上的 app 无法覆盖升级，本地草稿会丢。
