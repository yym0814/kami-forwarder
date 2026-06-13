package com.kamisystem.forwarder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    // ===== 硬编码配置（构建APK前修改这里）=====
    private static final String SERVER_URL = "https://fk.nswlkj.cn/api/notify.php";
    private static final String TOKEN = "kamisystem_2024";
    // ==========================================

    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnTest;
    private Button btnLogout;
    private Button btnOpenSettings;
    private TextView tvStatus;
    private TextView tvSubtitle;
    private LinearLayout cardLoggedIn;
    private TextView tvLoggedUser;
    private TextView tvLoggedCode;
    private TextView tvVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnTest = findViewById(R.id.btn_test);
        btnLogout = findViewById(R.id.btn_logout);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        tvStatus = findViewById(R.id.tv_status);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        cardLoggedIn = findViewById(R.id.card_logged_in);
        tvLoggedUser = findViewById(R.id.tv_logged_user);
        tvLoggedCode = findViewById(R.id.tv_logged_code);
        tvVersion = findViewById(R.id.tv_version);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("v" + pi.versionName + " (" + pi.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("v?.?");
        }

        // 保存硬编码配置
        SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
        prefs.edit()
            .putString("server_url", SERVER_URL)
            .putString("token", TOKEN)
            .apply();

        loadConfig();
        updateUI();

        btnLogin.setOnClickListener(v -> doLogin());
        btnTest.setOnClickListener(v -> testConnection());
        btnLogout.setOnClickListener(v -> doLogout());
        btnOpenSettings.setOnClickListener(v -> openNotificationSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
        etUsername.setText(prefs.getString("username", ""));
    }

    private void updateUI() {
        SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
        String agentCode = prefs.getString("agent_code", "");
        String username = prefs.getString("username", "");

        if (!agentCode.isEmpty()) {
            // 已登录
            tvSubtitle.setText("服务运行中");
            btnLogin.setVisibility(View.GONE);
            etPassword.setVisibility(View.GONE);
            btnTest.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.VISIBLE);
            cardLoggedIn.setVisibility(View.VISIBLE);
            tvLoggedUser.setText("代理账号: " + username);
            tvLoggedCode.setText("推广码: " + agentCode);
            tvStatus.setVisibility(View.GONE);

            // 检查通知权限状态
            ComponentName cn = new ComponentName(this, NotificationListener.class);
            String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
            boolean enabled = flat != null && flat.contains(cn.flattenToString());
            if (enabled) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("通知监听已开启，收款通知会自动转发");
                tvStatus.setBackgroundColor(0xFFE8F5E9);
                tvStatus.setTextColor(0xFF4CAF50);
            }
        } else {
            // 未登录
            tvSubtitle.setText("请先登录代理账号");
            btnLogin.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.VISIBLE);
            btnTest.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
            cardLoggedIn.setVisibility(View.GONE);
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            showStatus("请输入用户名", 0xFFFF5722);
            return;
        }
        if (password.isEmpty()) {
            showStatus("请输入密码", 0xFFFF5722);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在验证身份...");
        tvStatus.setBackgroundColor(0xFFFFF3E0);
        tvStatus.setTextColor(0xFFFF9800);

        new Thread(() -> {
            try {
                String apiUrl = SERVER_URL.replace("/api/notify.php", "") + "/api/agent_login.php";

                String body = "username=" + URLEncoder.encode(username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password, "UTF-8")
                    + "&token=" + URLEncoder.encode(TOKEN, "UTF-8");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                StringBuilder response = new StringBuilder();
                try (InputStream is = conn.getInputStream();
                     InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {
                    char[] buf = new char[256];
                    int n;
                    while ((n = isr.read(buf)) > 0) {
                        response.append(buf, 0, n);
                    }
                } catch (Exception e) {
                    InputStream err = conn.getErrorStream();
                    if (err != null) {
                        try (InputStreamReader isr = new InputStreamReader(err, "UTF-8")) {
                            char[] buf = new char[256];
                            int n;
                            while ((n = isr.read(buf)) > 0) {
                                response.append(buf, 0, n);
                            }
                        } catch (Exception ex) {}
                    }
                }

                conn.disconnect();

                final String respStr = response.toString();

                runOnUiThread(() -> {
                    try {
                        boolean success = respStr.contains("\"success\":true");
                        if (success) {
                            String agentCode = extractJsonString(respStr, "agent_code");
                            String userName = extractJsonString(respStr, "username");

                            SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
                            prefs.edit()
                                .putString("agent_code", agentCode)
                                .putString("username", userName)
                                .apply();

                            showStatus("登录成功！推广码: " + agentCode, 0xFF4CAF50);
                            updateUI();
                        } else {
                            String msg = extractJsonString(respStr, "message");
                            showStatus("登录失败: " + (msg.isEmpty() ? "用户名或密码错误" : msg), 0xFFFF5722);
                        }
                    } catch (Exception e) {
                        showStatus("解析失败: " + e.getMessage(), 0xFFFF5722);
                    }
                });

            } catch (final Exception e) {
                runOnUiThread(() -> {
                    showStatus("网络错误: " + e.getMessage(), 0xFFFF5722);
                });
            } finally {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("登录");
                });
            }
        }).start();
    }

    private void doLogout() {
        SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
        prefs.edit()
            .remove("agent_code")
            .apply();

        Toast.makeText(this, "已注销", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void testConnection() {
        btnTest.setEnabled(false);
        btnTest.setText("测试中...");

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("kamisystem", Context.MODE_PRIVATE);
                String agentCode = prefs.getString("agent_code", "");

                StringBuilder urlBuilder = new StringBuilder(SERVER_URL);
                if (!SERVER_URL.contains("?")) {
                    urlBuilder.append("?");
                } else {
                    urlBuilder.append("&");
                }
                urlBuilder.append("token=").append(TOKEN);
                if (!agentCode.isEmpty()) {
                    urlBuilder.append("&agent_code=").append(agentCode);
                }

                String body = "from=19999999999&content=19999999999%0A%E6%81%AD%E5%96%9C%E6%82%A8%EF%BC%8C%E5%8F%91%E9%80%81%E6%B5%8B%E8%AF%95%E6%88%90%E5%8A%9F&timestamp=" + System.currentTimeMillis();

                URL u = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }

                final int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 200) {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接成功！(HTTP 200)");
                        tvStatus.setBackgroundColor(0xFFE8F5E9);
                        tvStatus.setTextColor(0xFF4CAF50);
                    } else {
                        tvStatus.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接失败 HTTP " + code);
                        tvStatus.setBackgroundColor(0xFFFFEBEE);
                        tvStatus.setTextColor(0xFFF44336);
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("连接失败: " + e.getMessage());
                    tvStatus.setBackgroundColor(0xFFFFEBEE);
                    tvStatus.setTextColor(0xFFF44336);
                });
            } finally {
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText("测试连接");
                });
            }
        }).start();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "在列表中找到「卡密转发」并开启权限", Toast.LENGTH_LONG).show();
    }

    private void showStatus(String text, int color) {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(text);
        tvStatus.setTextColor(0xFFFFFFFF);
        tvStatus.setBackgroundColor(color);
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            search = "\"" + key + "\": \"";
            start = json.indexOf(search);
        }
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end);
    }
}
