package com.baibiaowang.stockjudge;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
                    private int tries = 0;
                    @Override public void run() {
                        WebView wv = (getBridge() == null) ? null : getBridge().getWebView();
                        if (wv != null) {
                            wv.evaluateJavascript(
                                "window.__SJ_INCOMING_PATH__=\"" + path + "\";", null);
                            return;
                        }
                        if (tries++ < 40) {
                            new Handler(Looper.getMainLooper()).postDelayed(this, 500);
                        }
                    }
                });
            }
        }).start();
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
            if (data.length < 32) return null;

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
