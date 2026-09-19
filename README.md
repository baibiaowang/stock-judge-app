# stock-judge-app

股票公告判断机 —— 离线安卓 App + 加密数据。

## 这是什么

后端把 A 股公告判断结果加密成**纯文本 txt**，推到本仓库 `data/stocks.txt`；
安卓 App 拉取（或导入）这个 txt，在本地解密后展示。

- 数据全程 AES-256-GCM 加密，仓库里只有密文
- 密钥不写在仓库里，构建时通过 GitHub Actions Secret 注入 apk
- App 内可改数据地址（支持多个地址按序 fallback），方便换源

## 目录结构

```
www/index.html                   App 本体（单文件，零外部依赖）
native/MainActivity.java         支持「用其他应用打开 txt」后直接解密
native/patch-manifest.mjs        构建时给 AndroidManifest 注入 intent-filter
capacitor.config.json            Capacitor 配置
package.json                     工程信息
data/stocks.txt                  加密后的判断结果（base64 纯文本）
.github/workflows/build-apk.yml  Actions：自动构建 apk
```

## 数据格式

```
stocks.txt (base64)
  └─ atob ─▶ [magic "SJ01" 4B][ver 1B][iv 12B][ciphertext][tag 16B]
前端解密：
  iv   = buf[5 .. 17)
  data = buf[17 .. )            // ciphertext || tag
  crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, data)
```

解密后是 JSON（schema 3）：`meta` / `layout` / `display` / `judge_tables` / `announcements` / `records`。

界面是**表驱动**的：表头、行数、列名全部读自 `judge_tables`，
新增公告分类只需在后端加一张表，App 不用改。

## 构建 apk

推送到 `main` 会自动触发构建，也可以手动触发：

> Actions → Build APK → Run workflow

构建完成后在 **Actions 运行页底部的 Artifacts** 里下载 `stock-judge-debug-apk`。

### 需要先配置的 Secrets

仓库 Settings → Secrets and variables → Actions：

| Secret | 必填 | 说明 |
|---|---|---|
| `AES_KEY` | **是** | 64 位十六进制密钥（32 字节）。不配构建会直接失败 |
| `DATA_URL` | 否 | 数据地址，多个用换行分隔。不配则用内置默认地址 |

## 本地预览

`www/index.html` 里的 `__AES_KEY__` / `__DATA_URL__` 是占位符，
用真实值替换后即可在浏览器里直接打开（浏览器需支持 Web Crypto）。
