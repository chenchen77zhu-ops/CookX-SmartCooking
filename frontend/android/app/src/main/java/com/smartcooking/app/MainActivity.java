package com.smartcooking.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient; // 必须导入这个

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TemperatureBluetoothPlugin.class);
        super.onCreate(savedInstanceState);

        // ✅ 核心修复：使用 Capacitor 专属的 BridgeWebViewClient
        // 这样既能加载本地 index.html，又能拦截跳转
        this.getBridge().getWebView().setWebViewClient(new BridgeWebViewClient(this.getBridge()) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // 如果是正常的网页或者本地 localhost 资源，交给 Capacitor 处理
                if (url.startsWith("http") || url.contains("localhost")) {
                    return super.shouldOverrideUrlLoading(view, request);
                } else {
                    // 如果是 meituan:// 或 androidamap:// 等协议
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        view.getContext().startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return super.shouldOverrideUrlLoading(view, request);
                    }
                }
            }
        });
    }
}
