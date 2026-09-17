package com.antigravity.machud;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 🌟 ESP32-C3 AlphaPi 硬件控制器网络通信引擎
 * 支持 SSE 全双工实时遥测流、REST API 指令下发、动态鉴权与自动断线重连
 */
public class AlphaPiClient {

    private static final String TAG = "AlphaPiClient";

    public interface AlphaPiListener {
        void onTelemetry(float pitch, float roll, int ax, int ay, int az, int vol, int ir, int fps, boolean connected);
        void onWifiScanResult(List<WifiApItem> aps);
        void onSoundEvent(String soundFile);
        void onWifiStatus(boolean connected, String ssid, String ip, String gw);
        void onConnectionStatus(boolean online, String message);
    }

    public static class WifiApItem {
        public final String ssid;
        public final int rssi;
        public final int channel;

        public WifiApItem(String ssid, int rssi, int channel) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.channel = channel;
        }
    }

    private String host = "";
    private int port = 8765;
    private String secretPath = "/ctrl-ef691ada9ea6";
    private String authUser = "";
    private String authPass = "";

    private AlphaPiListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread workerThread = null;
    private volatile boolean isRunning = false;

    public AlphaPiClient() {}

    public void setListener(AlphaPiListener listener) {
        this.listener = listener;
    }

    public void setConfig(String host, int port, String secretPath, String authUser, String authPass) {
        if (host != null && !host.trim().isEmpty()) {
            this.host = host.trim();
        }
        if (port > 0) {
            this.port = port;
        }
        if (secretPath != null) {
            this.secretPath = secretPath.trim();
            if (!this.secretPath.isEmpty() && !this.secretPath.startsWith("/")) {
                this.secretPath = "/" + this.secretPath;
            }
        }
        this.authUser = authUser != null ? authUser.trim() : "";
        this.authPass = authPass != null ? authPass.trim() : "";

        // 若已经在运行，重启 worker 连接新地址
        if (isRunning) {
            stop();
            start();
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getSecretPath() { return secretPath; }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                streamWorkerLoop();
            }
        }, "AlphaPi-SSE-Worker");
        workerThread.start();
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private String buildBaseUrl() {
        String path = secretPath;
        if (path == null) path = "";
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "http://" + host + ":" + port + path;
    }

    private void streamWorkerLoop() {
        while (isRunning) {
            if (host == null || host.trim().isEmpty()) {
                postStatus(false, "⚠️ 未配置主机 IP，请先点击[通信配置]输入");
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                String eventUrl = buildBaseUrl() + "/events";
                postStatus(false, "正在连接 " + host + ":" + port + "...");

                URL url = new URL(eventUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "text/event-stream");

                if (!authUser.isEmpty() && !authPass.isEmpty()) {
                    String auth = authUser + ":" + authPass;
                    String encodedAuth = Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
                }

                int respCode = conn.getResponseCode();
                if (respCode == 200) {
                    postStatus(true, "● MCU ONLINE (已建联)");
                    InputStream is = conn.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while (isRunning && (line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("data:")) {
                            String jsonStr = line.substring(5).trim();
                            if (!jsonStr.isEmpty() && jsonStr.startsWith("{")) {
                                parseSseEvent(jsonStr);
                            }
                        }
                    }
                } else {
                    postStatus(false, "连接异常: HTTP " + respCode);
                }
            } catch (Exception e) {
                if (isRunning) {
                    postStatus(false, "通信断开，重连中...");
                    Log.w(TAG, "SSE error: " + e.getMessage());
                }
            } finally {
                try {
                    if (reader != null) reader.close();
                } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }

            if (isRunning) {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void parseSseEvent(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);

            // 检查是否有 wifi 数据
            if (obj.has("type") && "wifi".equals(obj.optString("type"))) {
                JSONArray apsArray = obj.optJSONArray("aps");
                if (apsArray != null) {
                    final List<WifiApItem> list = new ArrayList<>();
                    for (int i = 0; i < apsArray.length(); i++) {
                        JSONObject ap = apsArray.getJSONObject(i);
                        list.add(new WifiApItem(ap.optString("ssid", "Unknown"), ap.optInt("rssi", -100), ap.optInt("chan", 1)));
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onWifiScanResult(list);
                        }
                    });
                }
                return;
            }

            // 检查 Wi-Fi 连接状态事件
            if (obj.has("type") && "wifi_status".equals(obj.optString("type"))) {
                JSONObject st = obj.optJSONObject("status");
                if (st != null) {
                    final boolean conn = st.optBoolean("connected", false);
                    final String ssid = st.optString("ssid", "");
                    final String ip = st.optString("ip", "");
                    final String gw = st.optString("gw", "");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onWifiStatus(conn, ssid, ip, gw);
                        }
                    });
                }
                return;
            }

            // 检查声音事件
            if (obj.has("type") && "sound".equals(obj.optString("type"))) {
                final String soundFile = obj.optString("file", "");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) listener.onSoundEvent(soundFile);
                    }
                });
                return;
            }

            // 常规姿态与传感器遥测
            if (obj.has("acc") || obj.has("pitch") || obj.has("vol")) {
                final float pitch = (float) obj.optDouble("pitch", 0.0);
                final float roll = (float) obj.optDouble("roll", 0.0);
                final int vol = obj.optInt("vol", 0);
                final int ir = obj.optInt("ir", 1);
                final int fps = obj.optInt("fps", 0);
                final boolean connected = obj.optBoolean("connected", true);

                JSONArray accArr = obj.optJSONArray("acc");
                final int ax = (accArr != null && accArr.length() > 0) ? accArr.optInt(0, 0) : 0;
                final int ay = (accArr != null && accArr.length() > 1) ? accArr.optInt(1, 0) : 0;
                final int az = (accArr != null && accArr.length() > 2) ? accArr.optInt(2, -850) : -850;

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onTelemetry(pitch, roll, ax, ay, az, vol, ir, fps, connected);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing telemetry: " + e.getMessage());
        }
    }

    private void postStatus(final boolean online, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onConnectionStatus(online, msg);
            }
        });
    }

    // ============================================================
    // 异步控制指令发送 (LED / 音效 / Wi-Fi 扫描)
    // ============================================================

    public void sendLedRainbow() {
        postJson("/api/led", "{\"cmd\":\"rainbow\"}");
    }

    public void sendLedVolMode() {
        postJson("/api/led", "{\"cmd\":\"vol_mode\"}");
    }

    public void sendLedColor(int r, int g, int b, float brightness) {
        String json = String.format("{\"cmd\":\"color\",\"r\":%d,\"g\":%d,\"b\":%d,\"bri\":%.2f}", r, g, b, brightness);
        postJson("/api/led", json);
    }

    public void sendLedOff() {
        postJson("/api/led", "{\"cmd\":\"off\"}");
    }

    public void playSound(String fileName) {
        String json = String.format("{\"cmd\":\"sound\",\"file\":\"%s\"}", fileName);
        postJson("/api/sound", json);
    }

    public void stopSound() {
        postJson("/api/sound/stop", "{\"cmd\":\"stop_sound\"}");
    }

    public void scanWifi() {
        postJson("/api/wifi", "{\"cmd\":\"scan_wifi\"}");
    }

    public void sendWifiConnect(String ssid, String password, String targetHost) {
        try {
            JSONObject json = new JSONObject();
            json.put("ssid", ssid);
            json.put("password", password);
            json.put("target_host", targetHost);
            postJson("/api/wifi/connect", json.toString());
        } catch (Exception ignored) {}
    }

    public void sendWifiDisconnect() {
        postJson("/api/wifi/disconnect", "{}");
    }

    private void postJson(final String subPath, final String jsonBody) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String fullUrl = buildBaseUrl() + subPath;
                    URL url = new URL(fullUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                    if (!authUser.isEmpty() && !authPass.isEmpty()) {
                        String auth = authUser + ":" + authPass;
                        String encodedAuth = Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
                    }

                    conn.setDoOutput(true);
                    byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bytes);
                        os.flush();
                    }
                    int code = conn.getResponseCode();
                    Log.d(TAG, "POST " + subPath + " -> HTTP " + code);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to POST " + subPath + ": " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
