package com.antigravity.machud;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

public class StatsClient {
    private static final String TAG = "StatsClient";

    public enum LinkType {
        USB,
        WIFI,
        CLOUD,
        DISCONNECTED
    }

    public interface Listener {
        void onStatsReceived(MacStats stats, LinkType linkType, String host);
        void onConnectionFailed(String error);
        void onMicStateChanged(boolean active);
        void onHostDiscovered(String ip);
    }

    public static class ProcessInfo {
        public String name;
        public double cpu;
        public String mem;

        public ProcessInfo(String name, double cpu, String mem) {
            this.name = name;
            this.cpu = cpu;
            this.mem = mem;
        }
    }

    public static class PowerInfo {
        public double watts = 0.0;
        public String wattsStr = "-- W";
        public String level = "NORMAL";
        public boolean isAC = true;
        public int adapterWatts = 65;
        public String adapterName = "PD 65W";
        public double voltage = 0.0;
        public double amperage = 0.0;
    }

    public static class MacStats {
        public double cpuPercent;
        public double[] cpuCores = new double[8];
        public double memUsedGb;
        public double memTotalGb;
        public double memPercent;
        public double memAppGb = 0.0;
        public double memWiredGb = 0.0;
        public double memCompGb = 0.0;
        public double swapUsedMb = 0.0;
        public double swapTotalMb = 0.0;
        public double diskUsedGb;
        public double diskTotalGb;
        public double diskPercent;
        public float netDownBytes = 0f;
        public float netUpBytes = 0f;
        public String netRx = "0 B/s";
        public String netTx = "0 B/s";
        public int macBatteryPercent = 0;
        public boolean macCharging = false;
        public String macBatteryTimeLeft = "";
        public PowerInfo power = new PowerInfo();
        public boolean micDemand = false;
        public String uptime = "--";
        public String macTime = "";
        public String macDate = "";
        public String cpuBrand = "i5 @ 2.30GHz";
        public String loadAvg = "0.00 · 0.00 · 0.00";
        public int procCount = 0;
        public String lanIP = "127.0.0.1";
        public String pingMs = "-- ms";
        public String totalIn = "0 B";
        public String totalOut = "0 B";
        public List<ProcessInfo> processes = new ArrayList<>();
    }

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning = false;
    private Thread pollerThread;
    private Thread beaconReceiverThread;

    private volatile String primaryHost = "127.0.0.1";
    private volatile String fallbackHost = "";
    private volatile int statsPort = 9527;
    private volatile int audioPort = 9528;
    private volatile LinkType currentLink = LinkType.USB;
    private volatile boolean isScanningSubnet = false;

    // 自定义/云端主机配置 (支持 Google 云服务器与自定义端口路径及 Basic 认证)
    private volatile String customHost = "";
    private volatile int customPort = 9527;
    private volatile String customPath = "/api/stats";
    private volatile String customUser = "";
    private volatile String customPass = "";

    public StatsClient(String fallbackIp, Listener listener) {
        if (fallbackIp != null && !fallbackIp.isEmpty()) {
            this.fallbackHost = fallbackIp;
        }
        this.listener = listener;
    }

    public void setCustomServer(String host, int port, String path) {
        setCustomServer(host, port, path, "", "");
    }

    public void setCustomServer(String host, int port, String path, String user, String pass) {
        this.customHost = (host != null) ? host.trim() : "";
        if (port > 0) this.customPort = port;
        if (path != null && !path.trim().isEmpty()) {
            this.customPath = path.trim();
        }
        this.customUser = (user != null) ? user.trim() : "";
        this.customPass = (pass != null) ? pass.trim() : "";
    }

    public String getCustomHost() {
        return customHost;
    }

    public int getCustomPort() {
        return customPort;
    }

    public String getCustomPath() {
        return customPath;
    }

    public String getCustomUser() {
        return customUser;
    }

    public String getCustomPass() {
        return customPass;
    }

    // 专用云端中继参数（当在外网、局域网与 USB 均不通时自动启用）
    private volatile String cloudHost = "";
    private volatile int cloudPort = 8000;
    private volatile String cloudSecret = "/ctrl-ef691ada9ea6";
    private volatile String cloudUser = "";
    private volatile String cloudPass = "";

    public void setCloudServer(String host, int port, String secret, String user, String pass) {
        this.cloudHost = (host != null) ? host.trim() : "";
        if (port > 0) this.cloudPort = port;
        if (secret != null && !secret.trim().isEmpty()) this.cloudSecret = secret.trim();
        this.cloudUser = (user != null) ? user.trim() : "";
        this.cloudPass = (pass != null) ? pass.trim() : "";
    }

    public String getCloudHost() { return cloudHost; }
    public int getCloudPort() { return cloudPort; }
    public String getCloudSecret() { return cloudSecret; }

    public void setFallbackHost(String host) {
        if (host != null) {
            this.fallbackHost = host.trim();
        }
    }

    public String getFallbackHost() {
        return fallbackHost;
    }

    public String getCurrentActiveHost() {
        if (currentLink == LinkType.CLOUD) {
            if (cloudHost != null && !cloudHost.isEmpty()) {
                return cloudHost + ":" + cloudPort;
            }
            if (customHost != null && !customHost.isEmpty()) {
                return customHost + ":" + customPort;
            }
        }
        if (customHost != null && !customHost.isEmpty()) {
            return customHost + ":" + customPort;
        }
        return (currentLink == LinkType.USB) ? primaryHost : fallbackHost;
    }

    public LinkType getCurrentLink() {
        return currentLink;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        startBeaconReceiver();
        startPoller();
    }

    public void scanSubnetForMac() {
        if (isScanningSubnet) return;
        isScanningSubnet = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String localIp = getLocalWifiIp();
                    if (localIp == null || !localIp.contains(".")) {
                        isScanningSubnet = false;
                        return;
                    }
                    int lastDot = localIp.lastIndexOf('.');
                    String prefix = localIp.substring(0, lastDot + 1);

                    ExecutorService pool = Executors.newFixedThreadPool(25);
                    final AtomicBoolean found = new AtomicBoolean(false);

                    for (int i = 1; i <= 254; i++) {
                        if (found.get() || !isRunning) break;
                        final String testIp = prefix + i;
                        if (testIp.equals(localIp)) continue;

                        pool.submit(new Runnable() {
                            @Override
                            public void run() {
                                if (found.get() || !isRunning) return;
                                Socket s = null;
                                try {
                                    s = new Socket();
                                    s.connect(new InetSocketAddress(testIp, statsPort), 300);
                                    OutputStream os = s.getOutputStream();
                                    os.write("GET /api/stats HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes());
                                    os.flush();

                                    BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                                    String line = reader.readLine();
                                    if (line != null && line.contains("200 OK")) {
                                        if (found.compareAndSet(false, true)) {
                                            fallbackHost = testIp;
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (listener != null) {
                                                        listener.onHostDiscovered(testIp);
                                                    }
                                                }
                                            });
                                        }
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    if (s != null) {
                                        try { s.close(); } catch (Exception ignored) {}
                                    }
                                }
                            }
                        });
                    }

                    pool.shutdown();
                    pool.awaitTermination(4, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                } finally {
                    isScanningSubnet = false;
                }
            }
        }, "SubnetScanner-Thread").start();
    }

    public static String getLocalWifiIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void startBeaconReceiver() {
        beaconReceiverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket(9529);
                    socket.setBroadcast(true);
                    byte[] buf = new byte[1024];

                    while (isRunning) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        try {
                            JSONObject json = new JSONObject(msg);

                            // 1. 处理 Mac 端的麦克风事件广播 (毫秒级即时触发)
                            if ("mic_state".equals(json.optString("event"))) {
                                final boolean active = json.optBoolean("active", false);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (listener != null) {
                                            listener.onMicStateChanged(active);
                                        }
                                    }
                                });
                            }

                            // 2. 处理服务发现广播
                            if ("machud".equals(json.optString("service"))) {
                                final String ip = json.optString("ip", "");
                                if (!ip.isEmpty() && !"localhost".equals(ip) && !"127.0.0.1".equals(ip)) {
                                    boolean changed = !ip.equals(fallbackHost);
                                    fallbackHost = ip;
                                    statsPort = json.optInt("statsPort", 9527);
                                    audioPort = json.optInt("audioPort", 9528);
                                    if (changed) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (listener != null) {
                                                    listener.onHostDiscovered(ip);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Beacon receiver stopped: " + e.getMessage());
                } finally {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                }
            }
        }, "StatsBeacon-Thread");
        beaconReceiverThread.start();
    }

    private void startPoller() {
        pollerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int consecutiveUsbFails = 0;
                int consecutiveAllFails = 0;

                while (isRunning) {
                    MacStats stats = null;
                    LinkType activeLink = LinkType.DISCONNECTED;
                    String activeHost = primaryHost;

                    // 0. 若配置了自定义主机，优先使用自定义配置
                    if (customHost != null && !customHost.isEmpty()) {
                        try {
                            String urlStr;
                            if (customHost.startsWith("http://") || customHost.startsWith("https://")) {
                                urlStr = customHost;
                            } else {
                                String path = customPath.startsWith("/") ? customPath : ("/" + customPath);
                                urlStr = "http://" + customHost + ":" + customPort + path;
                            }
                            String authHeader = null;
                            if (customUser != null && !customUser.isEmpty() && customPass != null && !customPass.isEmpty()) {
                                String auth = customUser + ":" + customPass;
                                authHeader = "Basic " + Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                            }
                            stats = fetchFromUrl(urlStr, authHeader, 2500);
                            boolean isCloud = !customHost.equals("127.0.0.1") && !customHost.startsWith("192.168.") && !customHost.startsWith("10.") && !customHost.startsWith("172.");
                            activeLink = isCloud ? LinkType.CLOUD : LinkType.WIFI;
                            activeHost = customHost;
                        } catch (Exception ignored) {}
                    }

                    // 1. 尝试 USB 直连 (127.0.0.1)
                    if (stats == null && (customHost == null || customHost.isEmpty() || customHost.equals("127.0.0.1"))) {
                        if (consecutiveUsbFails < 2) {
                            try {
                                stats = fetchFromHost(primaryHost, statsPort, 1000);
                                activeLink = LinkType.USB;
                                activeHost = primaryHost;
                                consecutiveUsbFails = 0;
                            } catch (Exception e) {
                                consecutiveUsbFails++;
                            }
                        }
                    }

                    // 2. 若 USB 失败或连续失败，尝试局域网 Wi-Fi IP
                    if (stats == null && fallbackHost != null && !fallbackHost.isEmpty()) {
                        try {
                            stats = fetchFromHost(fallbackHost, statsPort, 1200);
                            activeLink = LinkType.WIFI;
                            activeHost = fallbackHost;
                        } catch (Exception ignored) {}
                    }

                    // 3. 🌟 关键：云端中继兜底通道 (当 USB 与局域网离线/用户在室外时，自动无缝切入云端)
                    if (stats == null && cloudHost != null && !cloudHost.isEmpty()) {
                        try {
                            String sec = (cloudSecret != null) ? cloudSecret.trim() : "";
                            while (sec.endsWith("/")) sec = sec.substring(0, sec.length() - 1);
                            String authHeader = null;
                            if (cloudUser != null && !cloudUser.isEmpty() && cloudPass != null && !cloudPass.isEmpty()) {
                                String auth = cloudUser + ":" + cloudPass;
                                authHeader = "Basic " + Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                            }

                            // 策略 A：直接请求 /api/stats (经 server.py 校验放行)
                            String urlA = "http://" + cloudHost + ":" + cloudPort + "/api/stats";
                            stats = fetchFromUrl(urlA, authHeader, 2500);

                            // 策略 B：若策略 A 未果，尝试带安全前缀
                            if (stats == null && !sec.isEmpty()) {
                                String urlB = "http://" + cloudHost + ":" + cloudPort + sec + "/api/stats";
                                stats = fetchFromUrl(urlB, authHeader, 2500);
                            }

                            if (stats != null) {
                                activeLink = LinkType.CLOUD;
                                activeHost = cloudHost + ":" + cloudPort;
                            }
                        } catch (Exception ignored) {}
                    }

                    // 4. 如果通过 Wi-Fi 成功，但累计 USB 失败，则每 5 次轮询静默探活一次 USB 是否恢复
                    if (stats != null && activeLink == LinkType.WIFI && (customHost == null || customHost.isEmpty())) {
                        consecutiveUsbFails++;
                        if (consecutiveUsbFails >= 5) {
                            consecutiveUsbFails = 0;
                        }
                    }

                    // 4. 如果两路均无法连接，自动触发网段智能扫描
                    if (stats == null) {
                        consecutiveAllFails++;
                        if (consecutiveAllFails >= 3 && !isScanningSubnet) {
                            scanSubnetForMac();
                            consecutiveAllFails = 0;
                        }
                    } else {
                        consecutiveAllFails = 0;
                    }

                    currentLink = activeLink;

                    if (stats != null) {
                        final MacStats finalStats = stats;
                        final LinkType finalLink = activeLink;
                        final String finalHost = activeHost;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onStatsReceived(finalStats, finalLink, finalHost);
                                    // 兜底同步麦克风状态
                                    listener.onMicStateChanged(finalStats.micDemand);
                                }
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onConnectionFailed("Dual-link timeout");
                                }
                            }
                        });
                    }

                    try {
                        long sleepMs = (stats != null) ? 1000 : 2000;
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "StatsPoller-Thread");
        pollerThread.start();
    }

    private MacStats fetchFromHost(String host, int port, int timeoutMs) throws Exception {
        return fetchFromUrl("http://" + host + ":" + port + "/api/stats", null, timeoutMs);
    }

    private MacStats fetchFromUrl(String urlStr, String authHeader, int timeoutMs) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        if (authHeader != null && !authHeader.isEmpty()) {
            conn.setRequestProperty("Authorization", authHeader);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        JSONObject json = new JSONObject(sb.toString());
        if (json.has("mac") && json.optJSONObject("mac") != null) {
            json = json.getJSONObject("mac");
        }
        return parseStatsJson(json);
    }

    private MacStats parseStatsJson(JSONObject json) {
        MacStats stats = new MacStats();

        stats.macTime = json.optString("time", "");
        stats.macDate = json.optString("date", "");
        stats.uptime = json.optString("uptime", "--");

        JSONObject cpuObj = json.optJSONObject("cpu");
        if (cpuObj != null) {
            stats.cpuPercent = cpuObj.optDouble("total", 0.0);
            stats.cpuBrand = cpuObj.optString("brand", "i5 @ 2.30GHz");
            stats.loadAvg = cpuObj.optString("loadAvg", "0.00 · 0.00 · 0.00");
            stats.procCount = cpuObj.optInt("procs", 0);
            JSONArray coresArr = cpuObj.optJSONArray("cores");
            if (coresArr != null) {
                for (int i = 0; i < Math.min(8, coresArr.length()); i++) {
                    stats.cpuCores[i] = coresArr.optDouble(i, 0.0);
                }
            }
        }

        JSONObject memObj = json.optJSONObject("memory");
        if (memObj != null) {
            stats.memUsedGb = memObj.optDouble("used", 0.0);
            stats.memTotalGb = memObj.optDouble("total", 16.0);
            stats.memPercent = memObj.optDouble("percent", 0.0);
            stats.memAppGb = memObj.optDouble("app", 0.0);
            stats.memWiredGb = memObj.optDouble("wired", 0.0);
            stats.memCompGb = memObj.optDouble("compressed", 0.0);
            stats.swapUsedMb = memObj.optDouble("swapUsed", 0.0);
            stats.swapTotalMb = memObj.optDouble("swapTotal", 1024.0);
        }

        JSONObject diskObj = json.optJSONObject("disk");
        if (diskObj != null) {
            stats.diskUsedGb = diskObj.optDouble("used", 0.0);
            stats.diskTotalGb = diskObj.optDouble("total", 250.0);
            stats.diskPercent = diskObj.optDouble("percent", 0.0);
        }

        JSONObject netObj = json.optJSONObject("network");
        if (netObj != null) {
            stats.netRx = netObj.optString("downStr", "0 B/s");
            stats.netTx = netObj.optString("upStr", "0 B/s");
            stats.netDownBytes = (float) netObj.optDouble("down", 0.0);
            stats.netUpBytes = (float) netObj.optDouble("up", 0.0);
            stats.lanIP = netObj.optString("lanIP", "127.0.0.1");
            stats.pingMs = netObj.optString("pingMs", "-- ms");
            stats.totalIn = netObj.optString("totalIn", "0 B");
            stats.totalOut = netObj.optString("totalOut", "0 B");
        }

        JSONObject battObj = json.optJSONObject("battery");
        if (battObj != null) {
            stats.macBatteryPercent = battObj.optInt("percent", 0);
            stats.macCharging = battObj.optBoolean("charging", false);
            stats.macBatteryTimeLeft = battObj.optString("timeLeft", "");
        }

        JSONObject pwrObj = json.optJSONObject("power");
        if (pwrObj != null) {
            stats.power.watts = pwrObj.optDouble("watts", 0.0);
            stats.power.wattsStr = pwrObj.optString("wattsStr", "-- W");
            stats.power.level = pwrObj.optString("level", "NORMAL");
            stats.power.isAC = pwrObj.optBoolean("isAC", true);
            stats.power.adapterWatts = pwrObj.optInt("adapterWatts", 65);
            stats.power.adapterName = pwrObj.optString("adapterName", "PD 65W");
            stats.power.voltage = pwrObj.optDouble("voltage", 0.0);
            stats.power.amperage = pwrObj.optDouble("amperage", 0.0);
        }

        JSONObject micObj = json.optJSONObject("mic");
        if (micObj != null) {
            stats.micDemand = micObj.optBoolean("active", false);
        }

        JSONArray procArr = json.optJSONArray("processes");
        if (procArr != null) {
            for (int i = 0; i < Math.min(4, procArr.length()); i++) {
                JSONObject p = procArr.optJSONObject(i);
                if (p != null) {
                    stats.processes.add(new ProcessInfo(
                            p.optString("name", "Unknown"),
                            p.optDouble("cpu", 0.0),
                            p.optString("mem", "")
                    ));
                }
            }
        }
        return stats;
    }

    public synchronized void stop() {
        isRunning = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }
        if (beaconReceiverThread != null) {
            beaconReceiverThread.interrupt();
            beaconReceiverThread = null;
        }
    }
}
