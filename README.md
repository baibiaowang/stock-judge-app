# stock-judge-app

股票公告判断机 —— 安卓 App + 加密数据。

## 这是什么

后端把 A 股公告判断结果加密成**纯文本 txt**，推到本仓库 `data/`；
安卓 App 拉取（或导入）这个 txt，在本地解密后展示。

- 数据全程 AES-256-GCM 加密，仓库里只有密文
- 密钥不写在仓库里，构建时通过 GitHub Actions Secret 注入 apk
- App 内可改数据地址（支持多个地址按序 fallback），方便换源

> ⚠️ **它不是「完全离线」的 App**：`data/` 不在 `www/` 下，**不会打进 APK**。
> 首次启动必须联网拉数据（或在 App 里手动导入 txt）。
> 「离线」指的是**解密和查看在本地完成**，不是数据本地自带。

## 目录结构

```
www/index.html                   App 本体（单文件，零外部依赖）—— 源文件，含占位符
native/inject.mjs                构建时注入密钥/数据源，产物写到 dist/www/（不动源文件）
native/MainActivity.java         支持「用其他应用打开 txt」后直接解密
native/patch-manifest.mjs        构建时给 AndroidManifest 注入 intent-filter
capacitor.config.json            Capacitor 配置（webDir 指向 dist/www）
package.json                     工程信息 + Capacitor 依赖（锁 ^8.0.0，engines.node >= 22）
data/stocks.txt                  全量判断结果（2823 条，base64 密文）
data/000586.txt                  单票判断结果示例（21 条公告）
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

本地构建（需要 Node ≥ 22 与 JDK 21，Capacitor 8 的硬要求）：

```bash
npm install
AES_KEY=<64位hex> npm run inject     # 生成 dist/www/，www/ 源文件不动
npm run cap:add
npm run patch
npm run cap:sync
cd android && ./gradlew assembleDebug
```

### 需要先配置的 Secrets

仓库 Settings → Secrets and variables → Actions：

| Secret | 必填 | 说明 |
|---|---|---|
| `AES_KEY` | **是** | 64 位十六进制密钥（32 字节）。不配构建会直接失败 |
| `DATA_URL` | 否 | 数据地址，多个用换行分隔。不配则用内置默认地址 |

内置默认地址（按顺序尝试）：

1. `https://cdn.jsdelivr.net/gh/baibiaowang/stock-judge-app@main/data/stocks.txt`
2. `https://raw.githubusercontent.com/baibiaowang/stock-judge-app/main/data/stocks.txt`

## 本地预览

`www/index.html` 里的 `__AES_KEY__` / `__DATA_URL__` 是占位符。

> ⚠️ **不能直接双击用 `file://` 打开** —— 浏览器只在安全上下文里暴露 `crypto.subtle`，
> `file://` 下它是 `undefined`，页面会直接报「环境不支持 Web Crypto」。
> 必须先注入、再起一个 http 服务。

```bash
AES_KEY=<64位hex> npm run inject
npx serve dist/www          # 或：python -m http.server -d dist/www 8000
# 然后访问 http://localhost:3000
```

注入后的产物在 `dist/www/`，已被 `.gitignore` 排除 —— **密钥不会被提交上去**。
`inject.mjs` 还会在写盘前断言「源文件里没有真实密钥」，防止误提交后照常构建。
