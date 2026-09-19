/**
 * 构建时把 www/index.html 里的占位符替换成真实值。
 *
 * 用法（在仓库根目录执行）：
 *   AES_KEY=<64位hex> [DATA_URL=<地址,多个用换行分隔>] node native/inject.mjs
 *
 * - AES_KEY  必填，64 位十六进制；不合法直接失败（避免构建出解不开的 apk）
 * - DATA_URL 可选，留空则用内置默认地址列表（raw + GitHub Pages）
 *
 * ★ 两个占位符都位于 JS 单引号字符串字面量内（见 www/index.html 顶部
 *   `const DEF_KEY = '__AES_KEY__'` / `const DEF_URLS = '__DATA_URL__'`），
 *   所以替换前必须做 JS 字符串转义。
 *
 * ★ 历史事故（2026-09-20）：多行地址未转义就替换，字面量里出现裸换行 ->
 *   整个 <script> 报 SyntaxError 解析失败 -> App 打开后页面只剩静态骨架、
 *   ⋯ 按钮和所有交互全无反应；而旧版脚本只查「占位符还在不在」，占位符确实
 *   没了，于是照样输出 ok、退出码 0、APK 正常产出 —— 静默失败。
 *   现在改成：注入后把每个 <script> 块真正解析一遍，解析不过就 exit 1，
 *   并且**校验通过才写盘**（失败时原文件保持不动）。
 */
import fs from 'node:fs';

const P = 'www/index.html';

const DEFAULT_URLS = [
  'https://raw.githubusercontent.com/baibiaowang/stock-judge-app/main/data/stocks.txt',
  'https://baibiaowang.github.io/stock-judge-app/data/stocks.txt',
].join('\n');

const key = (process.env.AES_KEY || '').trim();
if (!/^[0-9a-fA-F]{64}$/.test(key)) {
  console.error('::error::AES_KEY missing or malformed (need 64 hex chars)');
  process.exit(1);
}

const urls = (process.env.DATA_URL || '').trim() || DEFAULT_URLS;

if (!fs.existsSync(P)) {
  console.error('::error::not found: ' + P);
  process.exit(1);
}

/** 转义成可安全放进「单引号 JS 字符串字面量」的内容 */
function jsSingleQuote(s) {
  return String(s)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

let h = fs.readFileSync(P, 'utf8');
h = h.split('__AES_KEY__').join(jsSingleQuote(key));
h = h.split('__DATA_URL__').join(jsSingleQuote(urls));

if (h.indexOf('__AES_KEY__') >= 0 || h.indexOf('__DATA_URL__') >= 0) {
  console.error('::error::placeholder still present after injection');
  process.exit(1);
}

/* ---- 真校验：把注入后的每个 <script> 块解析一遍（只解析、不执行） ---- */
const blocks = [...h.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map((m) => m[1]);
if (!blocks.length) {
  console.error('::error::no <script> block found in ' + P);
  process.exit(1);
}
for (let i = 0; i < blocks.length; i++) {
  try {
    new Function(blocks[i]);
  } catch (e) {
    console.error(`::error::injected JS failed to parse (script #${i + 1}): ${e.message}`);
    process.exit(1);
  }
}

/* ---- 校验通过才落盘 ---- */
fs.writeFileSync(P, h);

console.log(
  '[inject] ok  key_len=' + key.length +
  '  urls=' + urls.split('\n').length +
  '  scripts=' + blocks.length + ' parsed'
);
