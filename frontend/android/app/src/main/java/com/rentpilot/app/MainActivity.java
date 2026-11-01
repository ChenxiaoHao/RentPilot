package com.rentpilot.app;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return;
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String path = uri.getEncodedPath();
                if (!"GET".equals(request.getMethod()) || !"https".equals(uri.getScheme()) ||
                    !"localhost".equals(uri.getHost()) || uri.getPort() != -1 || uri.getUserInfo() != null ||
                    !"/_AMapService/v3/log/init".equals(path)) {
                    return super.shouldInterceptRequest(view, request);
                }
                HttpURLConnection connection = null;
                try {
                    String query = uri.getEncodedQuery();
                    if (query != null && query.length() > 8192) return failure();
                    URL target = new URL("http://10.0.2.2:8080/_AMapService/v3/log/init" + (query == null ? "" : "?" + query));
                    connection = (HttpURLConnection) target.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);
                    if (connection.getResponseCode() != 200) return failure();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = connection.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > 2 * 1024 * 1024) return failure();
                            bytes.write(buffer, 0, count);
                        }
                    }
                    String contentType = connection.getContentType();
                    String mime = contentType == null ? "application/json" : contentType.split(";", 2)[0].trim();
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", Collections.emptyMap(), new ByteArrayInputStream(bytes.toByteArray()));
                } catch (Exception ignored) {
                    return failure();
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static WebResourceResponse failure() {
        return new WebResourceResponse("text/plain", "UTF-8", 502, "Bad Gateway", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
}
