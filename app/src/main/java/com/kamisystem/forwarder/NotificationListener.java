package com.kamisystem.forwarder;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "KaMiForwarder";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        
        // 只监听支付宝和微信
        if (!"com.eg.android.AlipayGphone".equals(packageName) 
            && !"com.tencent.mm".equals(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String content = getNotificationText(notification);
        if (content == null || content.isEmpty()) return;
        
        // 过滤：必须含付款/收款相关关键词
        if (!containsPaymentKeyword(content)) return;

        SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "");
        String token = prefs.getString("token", "");
        String agentCode = prefs.getString("agent_code", "");

        if (serverUrl.isEmpty()) {
            Log.i(TAG, "服务器地址未配置，跳过");
            return;
        }

        // 拼接完整URL
        StringBuilder urlBuilder = new StringBuilder(serverUrl);
        if (!serverUrl.contains("?")) {
            urlBuilder.append("?");
        } else {
            urlBuilder.append("&");
        }
        urlBuilder.append("token=").append(token);
        if (!agentCode.isEmpty()) {
            urlBuilder.append("&agent_code=").append(agentCode);
        }

        // 构建POST数据（模拟SmsForwarder格式）
        String from = packageName;
        String timestamp = String.valueOf(System.currentTimeMillis());
        String rawContent = "from=" + urlEncode(from) 
            + "&content=" + urlEncode(content)
            + "&timestamp=" + timestamp;

        String finalUrl = urlBuilder.toString();
        
        // 异步发送
        executor.execute(() -> {
            String result = sendPost(finalUrl, rawContent);
            Log.i(TAG, String.format("[%s] %s -> %s", 
                "com.tencent.mm".equals(packageName) ? "微信" : "支付宝", 
                getShortText(content, 40), result));
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 不需要处理
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "卡密转发服务已启动");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "卡密转发服务已断开，正在尝试重连...");
        requestRebind(new android.content.ComponentName(this, NotificationListener.class));
    }

    // ====== 辅助方法 ======

    private String getNotificationText(Notification notification) {
        Bundle extras = notification.extras;
        StringBuilder sb = new StringBuilder();

        // 1. inboxStyle 行数据（多条通知摘要，每行可能是一个独立收款）
        // android.textLines 在 API 16+ 可用
        CharSequence[] textLines = extras.getCharSequenceArray("android.textLines");
        if (textLines != null && textLines.length > 0) {
            for (CharSequence line : textLines) {
                if (line != null && line.length() > 0) {
                    sb.append(line).append("\n");
                }
            }
        }

        // 2. bigText（展开后的完整文本，微信用 BigTextStyle）
        CharSequence bigText = extras.getCharSequence("android.bigText");
        if (bigText != null && bigText.length() > 0) {
            sb.append(bigText).append("\n");
        }

        // 3. title + text + subText + summaryText
        String title = extras.getString("android.title", "");
        String text = extras.getCharSequence("android.text", "") != null
            ? extras.getCharSequence("android.text", "").toString() : "";
        String subText = extras.getString("android.subText", "");
        String summaryText = extras.getString("android.summaryText", "");

        if (!title.isEmpty()) sb.append(title).append("\n");
        if (!text.isEmpty()) sb.append(text).append("\n");
        if (!subText.isEmpty()) sb.append(subText).append("\n");
        if (!summaryText.isEmpty()) sb.append(summaryText).append("\n");

        // 4. tickerText 兜底
        if (sb.length() == 0 && notification.tickerText != null) {
            sb.append(notification.tickerText.toString());
        }

        // DEBUG: 完整 extras 数据
        sb.append("\n[DEBUG]").append(extras.toString());

        return sb.toString().trim();
    }

    private boolean containsPaymentKeyword(String text) {
        // 支付宝/微信收款关键词
        String[] keywords = {
            "收款", "到账", "付款", "转账", "支付", 
            "¥", "￥", "元", "入账", "到款"
        };
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String getShortText(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String sendPost(String urlStr, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "KaMiForwarder/1.0");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                return "OK(" + code + ")";
            } else {
                return "HTTP " + code;
            }
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
