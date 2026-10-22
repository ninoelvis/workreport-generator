# 签名证书说明（keystore）

## ⚠️ 这里的东西不要提交到 GitHub

本目录下的 `*.keystore` 已被仓库根目录的 `.gitignore` 排除。

**为什么这么重要**：Android 用「包名 + 签名」来识别一个应用。签名私钥一旦泄露，
任何拿到它的人都能打包出和你 app **包名完全相同、签名完全一致**的安装包，
用户手机上会认为是你的应用更新并直接覆盖安装。

所以：**公开仓库（哪怕是 Private）也不要放 keystore。**

## 怎么用

### 方式一：放在本地（推荐）

把 `release.keystore` 放到本目录，`build.sh` 会自动找到：

```
keystore/release.keystore
```

### 方式二：环境变量

```bash
export KEYSTORE_PATH=/path/to/your.keystore
export KEYSTORE_PASS=你的密码
export KEY_ALIAS=别名
export KEY_PASS=你的密码
./build.sh
```

## 当前版本的证书信息

| 项目 | 值 |
|---|---|
| 别名 alias | `workreport` |
| 密码（store / key 相同） | `workreport2026` |
| 算法 | RSA 2048 / SHA256withRSA |
| 有效期 | 30 年 |
| 证书 SHA-256 | `08e8eb9dd3d8e5c09da8d5c63ddb73e2`（文件 md5，证书指纹见 `docs/签名信息.txt`） |

## 换证书会怎样

- 用**同一个** keystore 签名 → 用户可直接覆盖升级，本地数据保留 ✅
- 换了新 keystore → 签名不一致，Android 拒绝安装，用户必须先卸载旧版，
  **app 里存的周报草稿会一起清掉** ⚠️

所以请把这份 keystore 单独备份一份到网盘 / 邮箱，**不要只存在这一台设备上**。
