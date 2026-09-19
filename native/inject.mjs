/**
 * 构建时把 www/index.html 里的占位符替换成真实值。
 *
 * 用法（在仓库根目录执行）：
 *   AES_KEY=<64位hex> [DATA_URL=<地址,多个用换行分隔>] node native/inject.mjs
 *
 * - AES_KEY  必填，64 位十六进制；不合法直接失败（避免构建出解不开的 apk）
 * - DATA_URL 可选，留空则用内置默认地址列表（raw + GitHub Pages）
 *
 * 用 node 而不是 sed，是为了支持多行地址（sed 处理换行很脆）。
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

let h = fs.readFileSync(P, 'utf8');
h = h.split('__AES_KEY__').join(key);
h = h.split('__DATA_URL__').join(urls);
fs.writeFileSync(P, h);

if (h.indexOf('__AES_KEY__') >= 0 || h.indexOf('__DATA_URL__') >= 0) {
  console.error('::error::placeholder still present after injection');
  process.exit(1);
}

console.log('[inject] ok  key_len=' + key.length + '  urls=' + urls.split('\n').length);
