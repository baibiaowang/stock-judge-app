package com.baibiaowang.stockjudge;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 支持「用其他应用打开」一个 txt 文件后直接解密。
 *
 * 做法：收到 ACTION_VIEW 的 text/plain 后，把文件内容落到 app 私有目录
 *       (files/incoming.txt)，再把路径注入到 WebView 的
 *       window.__SJ_INCOMING_PATH__；前端轮询到该变量后，用
 *       Capacitor.convertFileSrc() 读取并解密。
 *
 * 注意：注入的是「路径」而不是文件内容，避免大字符串走 evaluateJavascript。
 */
public class MainActivity extends BridgeActivity {

    private static final String INCOMING_FILE = "incoming.txt";
    /** 前端轮询上限是 60 * 500ms = 30s，这里保持同一量级 */
    private static final int MAX_ATTEMPTS = 60;
    private static final long RETRY_MS = 500L;
    /** 与前端 decryptBytes() 的 `u8.length < 33` 保持一致 */
    private static final int MIN_BYTES = 33;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        final Uri uri = intent.getData();
        if (uri == null) return;

        new Thread(new Runnable() {
            @Override public void run() {
                final String path = copyToPrivate(uri);
                if (path == null) return;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { injectWhenReady(path, 0); }
                });
            }
        }).start();
    }

    /**
     * ★ 2026-09-20 修：原实现只要 getWebView() 非 null 就注入。
     *   冷启动时 WebView 对象已经存在、但页面还没加载完，注入的变量会被
     *   随后的页面加载冲掉 —— 前端轮询 30 秒也拿不到，表现为
     *   「用其他应用打开 txt，什么都没发生」。
     *   热启动（App 已在前台，走 onNewIntent）时页面已就绪，所以只有冷启动坏，
     *   排查时极易误判成「intent-filter 没生效」。
     *   现在先问 document.readyState，只有 complete 才注入，否则每 500ms 重试。
     */
    private void injectWhenReady(final String path, final int attempt) {
        if (attempt >= MAX_ATTEMPTS) return;
        final WebView wv = (getBridge() == null) ? null : getBridge().getWebView();
        if (wv == null) { retry(path, attempt); return; }
        try {
            wv.evaluateJavascript("document.readyState", new ValueCallback<String>() {
                @Override public void onReceiveValue(String v) {
                    if (v != null && v.indexOf("complete") >= 0) {
                        try {
                            wv.evaluateJavascript(
                                "window.__SJ_INCOMING_PATH__=" + jsStr(path) + ";", null);
                        } catch (Exception ignored) { }
                    } else {
                        retry(path, attempt);
                    }
                }
            });
        } catch (Exception e) {
            retry(path, attempt);
        }
    }

    private void retry(final String path, final int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { injectWhenReady(path, attempt + 1); }
        }, RETRY_MS);
    }

    /**
     * ★ 2026-09-20 新增：转义成 JS 双引号字符串字面量。
     *   原实现直接把路径拼进 `"...\" + path + "\"..."` ——
     *   路径里只要出现引号或反斜杠就会把注入的 JS 写成语法错误。
     *   （同一个「注入字符串不转义」的家族问题在 inject.mjs 里已经犯过一次，
     *   这次把原生侧也补上。）
     */
    private static String jsStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append("\\u").append(pad4(Integer.toHexString(c)));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append("\"").toString();
    }

    private static String pad4(String hex) {
        StringBuilder sb = new StringBuilder(hex);
        while (sb.length() < 4) sb.insert(0, '0');
        return sb.toString();
    }

    /** 把外部 URI 的内容复制到 app 私有目录，返回绝对路径；失败返回 null */
    private String copyToPrivate(Uri uri) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            byte[] data = bos.toByteArray();
            if (data.length < MIN_BYTES) return null;

            File out = new File(getFilesDir(), INCOMING_FILE);
            fos = new FileOutputStream(out);
            fos.write(data);
            fos.flush();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (fos != null) fos.close(); } catch (Exception ignored) { }
        }
    }
}
