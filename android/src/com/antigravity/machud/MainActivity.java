package com.antigravity.machud;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "deskpilot_prefs";
    private static final String KEY_SAVED_MAC_IP = "saved_mac_ip";
    private static final int AUDIO_PORT = 9528;
    private static final int PERMISSION_REQ_CODE = 1001;

    private LinearLayout dashboardContainer;
    private View screensaverView;

    // 双屏管理与左右滑屏
    private ViewFlipper screenFlipper;
    private int currentScreenIndex = 0; // 0: 仪表盘, 1: 气象智库
    private View btnSwitchToScreen2;
    private View btnSwitchToScreen1;
    private TextView tvWeatherPageIndicator;

    // 屏 2: 气象与穿衣智库组件
    private WeatherParticleView weatherParticleView;
    private TextView tvWeatherDeviceTag, tvWeatherLiveDot, tvWeatherLinkBadge;
    private TextView tvWeatherClock, tvWeatherDate;
    private TextView tvWeatherCity, tvWeatherRainProbBadge, tvWeatherBigTemp, tvWeatherIcon, tvWeatherDesc, tvWeatherFeelsLike;
    private TextView tvWeatherTodayMinMax, tvWeatherWind, tvWeatherRainProb;
    private TextView tvClothingWarningTag, tvClothingMain, tvClothingSub;
    private TextView tvIndexUmbrella, tvIndexSport, tvIndexCarWash, tvIndexCold;
    private TextView tvTomorrowRainBadge, tvTomorrowContrast;
    private TextView tvTomorrowBigTemp, tvTomorrowIcon, tvTomorrowDesc, tvTomorrowMinMax, tvTomorrowTempDiff, tvTomorrowAdvice;
    private TextView tvWeatherIndoorSensor;
    private View btnWeatherRefresh;

    private WeatherService weatherService;
    private WeatherService.WeatherData currentWeatherData;

    // 顶部状态栏
    private TextView tvUptime, tvClock, tvDate;
    private TextView tvLiveDot, tvLinkBadge;
    private TextView tvMacBattBadge;
    private LinearLayout btnMic;
    private TextView tvMicDot, tvMicText;
    private MicWaveView micWaveView;
    private TextView tvGuardTag, tvBatteryPct;

    // 卡片 1: CPU 算力引擎 (8-CORE SPECTRUM) & 物理气象微站
    private TextView tvCpuStatTag, tvCpuBigPct, tvCpuPeakCore;
    private TextView tvCpuBrand, tvLoadAvg, tvProcCount;
    private ProgressBar pbCpuTotal;
    private CpuSpectrumView cpuSpectrumView;
    private CpuWaveView cpuWaveView;
    private TextView tvAmbientComfort, tvSensorTemp, tvSensorHumidity, tvSensorPressure;

    // 方案 A 贯通式全局交互底栏组件
    private FrameLayout bottomDockContainer;
    private ViewFlipper dockFlipper;
    private View dockWeatherBlock;
    private View dockHostBlock;
    private TextView tvDockWeatherIcon;
    private TextView tvSensorAltitude;
    private TextView tvCpuSpec;
    private TextView tvDockDots;
    private TextView tvDockTrafficSum;
    private TextView tvDockDiskDetail;
    private TextView tvDockSlateStatus;
    private TextView tvDockOledStatus;

    // 下钻抽屉浮层组件
    private FrameLayout drawerInspectOverlay;
    private View cardWeatherDrawer;
    private View cardHostDrawer;
    private View cardScreenControl;
    private TextView tvDrawerTemp, tvDrawerHumidity, tvDrawerComfortDesc;
    private TextView tvDrawerPressure, tvDrawerAltitude, tvDrawerPressureTrend;
    private TextView tvDrawerLightLux, tvDrawerRate;
    private TextView tvDrawerCpuModel, tvDrawerCpuCores, tvDrawerCpuTurbo;
    private TextView tvDrawerLoadDetail, tvDrawerTasksDetail, tvDrawerUptimeDetail;
    private SeekBar sbScreenBrightness;
    private TextView tvBrightnessVal;
    private TextView btnQuickAod, btnQuickWash, btnQuickNight, btnConfigHost;
    private GestureDetector dockGestureDetector;
    private ObjectAnimator weatherPulseAnim;
    private String currentUptime = "14天4时35分";

    // 卡片 2: 内存与系统存储 (MEMORY & STORAGE)
    private TextView tvMemBigPct, tvMemDetail, tvMemFree;
    private MultiSegmentBarView multiMemBar;
    private TextView tvMemApp, tvMemWired, tvMemComp;
    private TextView tvSwapVal;
    private ProgressBar pbSwap;
    private TextView tvDiskPct, tvDiskUsed, tvDiskTotal, tvDiskAvail;
    private ProgressBar pbDisk;

    // Note 3 物理硬件传感器 (博世气压计、盛思锐温湿度)
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private Sensor ambientTempSensor;
    private Sensor humiditySensor;
    private SensorEventListener sensorEventListener;
    private float lastAmbientTemp = 0f;
    private float lastHumidity = 0f;
    private float lastPressure = 0f;

    // 卡片 3: 网络通信
    private TextView tvNetDown, tvNetUp;
    private TextView tvNetLanIp, tvNetPing, tvNetTotal;
    private NetworkWaveView waveView;

    // 卡片 4: 电源功耗与活跃进程
    private TextView tvPowerLevelBadge, tvPowerWatts, tvAdapterSpec, tvPowerVI, tvMacBatteryState;
    private TextView tvP1Name, tvP1Cpu, tvP1Mem;
    private TextView tvP2Name, tvP2Cpu, tvP2Mem;
    private TextView tvP3Name, tvP3Cpu, tvP3Mem;

    private TextView tvScreensaverClock;
    private View aodClockBox;
    private TextView tvScreensaverStats, tvAodIndicator;
    private PixelRefreshView pixelRefreshView;
    private Sensor lightSensor;
    private float currentLux = -1f;
    private float currentScreenBrightness = -1f;
    private volatile boolean isAodMode = false;
    private long lastUserTouchTime = System.currentTimeMillis();
    private int macLowLoadStreak = 0;
    private int macDisconnectStreak = 0;
    private String lastMacStatsSummary = "MAC --% · --°C · --W";

    private StatsClient statsClient;
    private AudioStreamer audioStreamer;
    private volatile boolean userManualMute = false;
    private volatile boolean userManualForceActive = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GestureDetector gestureDetector;
    private int orbitIndex = 0;
    // 16 点环形多轴漫游矩阵 (扩大步进至 ±5px，避免静态重叠)
    private final int[][] orbitOffsets = {
            {0, 0}, {2, 1}, {4, 2}, {5, 0}, {4, -2}, {2, -3},
            {0, -4}, {-2, -3}, {-4, -2}, {-5, 0}, {-4, 2}, {-2, 3},
            {0, 2}, {2, 0}, {-1, 1}, {0, 0}
    };

    // 子像素轮休与微色温呼吸微调色板 (每 3 分钟平缓轮换，让 RGB 子像素轮流卸荷)
    private static final int[] CHROMA_PALETTE = {
            Color.parseColor("#e6edf3"), // 极客冷白
            Color.parseColor("#e8f0fe"), // 冰川微蓝
            Color.parseColor("#f0efe9"), // 象牙暖白
            Color.parseColor("#e9f5ee")  // 翡翠微绿
    };
    private int chromaIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        initViews();
        setupGestureDetector();
        startClockAndOrbiting();

        audioStreamer = new AudioStreamer("127.0.0.1", AUDIO_PORT);
        audioStreamer.setAmplitudeListener(new AudioStreamer.AmplitudeListener() {
            @Override
            public void onAmplitude(final float normalizedAmp) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (micWaveView != null) {
                            micWaveView.updateAmplitude(normalizedAmp);
                        }
                    }
                });
            }
        });

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedHost = sp.getString(KEY_SAVED_MAC_IP, "");

        // 启动具备双通道自愈与自动发现的 Mac 状态客户端
        statsClient = new StatsClient(savedHost, new StatsClient.Listener() {
            @Override
            public void onStatsReceived(StatsClient.MacStats stats, StatsClient.LinkType linkType, String host) {
                updateMacStats(stats, linkType, host);
            }

            @Override
            public void onConnectionFailed(String error) {
                macDisconnectStreak++;
                // 优雅降级：保留原先数据面板，指示重连中，杜绝闪烁清屏
                tvLiveDot.setText("○ SYNCING");
                tvLiveDot.setTextColor(Color.parseColor("#ffab00"));
                tvLinkBadge.setText("⏳ 重连中");
                tvLinkBadge.setTextColor(Color.parseColor("#ffab00"));
                tvLinkBadge.setBackgroundColor(Color.parseColor("#2a200a"));
                tvUptime.setText("双链路探测中...");
                tvUptime.setTextColor(Color.parseColor("#ffab00"));
                dashboardContainer.setAlpha(0.75f);
            }

            @Override
            public void onMicStateChanged(final boolean active) {
                setMicrophoneActive(active, false);
            }

            @Override
            public void onHostDiscovered(final String ip) {
                SharedPreferences p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String oldIp = p.getString(KEY_SAVED_MAC_IP, "");
                if (!ip.equals(oldIp)) {
                    p.edit().putString(KEY_SAVED_MAC_IP, ip).apply();
                    Toast.makeText(MainActivity.this, "已自动发现并锁定 Mac 主机: " + ip, Toast.LENGTH_SHORT).show();
                }
            }
        });
        String customHost = sp.getString("custom_host", "");
        int customPort = sp.getInt("custom_port", 9527);
        String customPath = sp.getString("custom_path", "/api/stats");
        if (!customHost.isEmpty()) {
            statsClient.setCustomServer(customHost, customPort, customPath);
        }
        statsClient.start();

        registerBatteryMonitor();
        initSensors();

        // 初始化全景气象与穿衣生活智库服务
        weatherService = new WeatherService(this);
        weatherService.setListener(new WeatherService.OnWeatherUpdatedListener() {
            @Override
            public void onWeatherUpdated(WeatherService.WeatherData data) {
                updateWeatherScreenViews(data);
            }
        });
        weatherService.loadCachedOrFetch();
    }



    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveSticky();
        registerSensors();
        if (currentScreenIndex == 1 && weatherParticleView != null) {
            weatherParticleView.startAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensors();
        if (weatherParticleView != null) {
            weatherParticleView.stopAnimation();
        }
    }

    private void initSensors() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                ambientTempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
                humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }

            sensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                        float pressure = event.values[0];
                        lastPressure = pressure;
                        if (tvSensorPressure != null) {
                            tvSensorPressure.setText(String.format(Locale.getDefault(), "BOSCH %.1f hPa", pressure));
                        }
                        updateIndoorSensorText();
                        if (tvSensorAltitude != null) {
                            float alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
                            tvSensorAltitude.setText(String.format(Locale.getDefault(), "海拔 ~%.0fm", alt));
                        }
                        if (drawerInspectOverlay != null && drawerInspectOverlay.getVisibility() == View.VISIBLE) {
                            updateWeatherDrawer();
                        }
                    } else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                        float temp = event.values[0];
                        lastAmbientTemp = temp;
                        if (tvSensorTemp != null) {
                            tvSensorTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
                        }
                        updateComfortStatus();
                    } else if (event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
                        float rh = event.values[0];
                        lastHumidity = rh;
                        if (tvSensorHumidity != null) {
                            tvSensorHumidity.setText(String.format(Locale.getDefault(), "%.0f%% RH", rh));
                        }
                        updateComfortStatus();
                    } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                        float lux = event.values[0];
                        currentLux = lux;
                        applyAdaptiveBrightness(lux);
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };

            // 预读取电池温度作为初始室温基准兜底，消除界面冷启动等待
            File f = new File("/sys/class/power_supply/battery/temp");
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line = br.readLine();
                br.close();
                if (line != null && !line.trim().isEmpty()) {
                    float bt = Float.parseFloat(line.trim()) / 10.0f;
                    if (lastAmbientTemp <= 0) {
                        lastAmbientTemp = bt;
                        if (tvSensorTemp != null) {
                            tvSensorTemp.setText(String.format(Locale.getDefault(), "%.1f°C", bt));
                        }
                    }
                }
            }
            updateComfortStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyAdaptiveBrightness(float lux) {
        float targetBrightness;
        if (lux < 15f) {
            targetBrightness = 0.18f; // 暗室/深夜低功耗护屏
        } else if (lux < 60f) {
            targetBrightness = 0.35f;
        } else if (lux < 250f) {
            targetBrightness = 0.55f;
        } else {
            targetBrightness = 0.75f;
        }

        if (currentScreenBrightness > 0 && Math.abs(currentScreenBrightness - targetBrightness) < 0.05f) {
            return;
        }
        currentScreenBrightness = targetBrightness;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = targetBrightness;
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
    }

    private void registerSensors() {
        if (sensorManager == null || sensorEventListener == null) return;
        if (pressureSensor != null) {
            sensorManager.registerListener(sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (ambientTempSensor != null) {
            sensorManager.registerListener(sensorEventListener, ambientTempSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (humiditySensor != null) {
            sensorManager.registerListener(sensorEventListener, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (lightSensor != null) {
            sensorManager.registerListener(sensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterSensors() {
        if (sensorManager != null && sensorEventListener != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
    }

    private void updateComfortStatus() {
        if (tvAmbientComfort == null) return;
        if (lastAmbientTemp <= 0 && lastHumidity <= 0) {
            tvAmbientComfort.setText("SAMPLING");
            tvAmbientComfort.setTextColor(Color.parseColor("#8fa0b0"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#151d26"));
            return;
        }

        // 舒适区判定：20°C ~ 27°C 且 40% ~ 65% RH
        if (lastAmbientTemp >= 20f && lastAmbientTemp <= 27f && (lastHumidity <= 0 || (lastHumidity >= 40f && lastHumidity <= 65f))) {
            tvAmbientComfort.setText("舒适 COMFORT");
            tvAmbientComfort.setTextColor(Color.parseColor("#00f59b"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#0a2a1c"));
        } else if (lastAmbientTemp > 28f) {
            tvAmbientComfort.setText("偏热 WARM");
            tvAmbientComfort.setTextColor(Color.parseColor("#ff4757"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#2a0a10"));
        } else if (lastAmbientTemp < 18f && lastAmbientTemp > 0) {
            tvAmbientComfort.setText("偏冷 COOL");
            tvAmbientComfort.setTextColor(Color.parseColor("#00d2ff"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#0a1e2a"));
        } else if (lastHumidity > 70f) {
            tvAmbientComfort.setText("潮湿 HUMID");
            tvAmbientComfort.setTextColor(Color.parseColor("#00d2ff"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#0a1e2a"));
        } else if (lastHumidity < 35f && lastHumidity > 0) {
            tvAmbientComfort.setText("干燥 DRY");
            tvAmbientComfort.setTextColor(Color.parseColor("#ffd166"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#2a200a"));
        } else {
            tvAmbientComfort.setText("适中 NORMAL");
            tvAmbientComfort.setTextColor(Color.parseColor("#00f59b"));
            tvAmbientComfort.setBackgroundColor(Color.parseColor("#0a2a1c"));
        }

        if (lastHumidity > 80f) {
            startWeatherPulse();
        } else {
            stopWeatherPulse();
        }
        if (drawerInspectOverlay != null && drawerInspectOverlay.getVisibility() == View.VISIBLE) {
            updateWeatherDrawer();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveSticky();
        }
    }

    private void applyImmersiveSticky() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void initViews() {
        dashboardContainer = findViewById(R.id.dashboard_container);
        screensaverView = findViewById(R.id.screensaver_view);
        screenFlipper = findViewById(R.id.screen_flipper);
        btnSwitchToScreen2 = findViewById(R.id.btn_switch_to_screen2);
        if (btnSwitchToScreen2 != null) {
            btnSwitchToScreen2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWeatherScreen();
                }
            });
        }

        // 屏 2: 气象全景与穿衣智库组件绑定
        weatherParticleView = findViewById(R.id.weather_particle_view);
        tvWeatherDeviceTag = findViewById(R.id.tv_weather_device_tag);
        tvWeatherLiveDot = findViewById(R.id.tv_weather_live_dot);
        tvWeatherLinkBadge = findViewById(R.id.tv_weather_link_badge);
        tvWeatherClock = findViewById(R.id.tv_weather_clock);
        tvWeatherDate = findViewById(R.id.tv_weather_date);
        btnSwitchToScreen1 = findViewById(R.id.btn_switch_to_screen1);
        btnWeatherRefresh = findViewById(R.id.btn_weather_refresh);

        tvWeatherCity = findViewById(R.id.tv_weather_city);
        if (tvWeatherCity != null) {
            tvWeatherCity.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWeatherCityDialog();
                }
            });
        }
        if (tvWeatherDeviceTag != null) {
            tvWeatherDeviceTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }
        if (tvWeatherLinkBadge != null) {
            tvWeatherLinkBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }
        tvWeatherRainProbBadge = findViewById(R.id.tv_weather_rain_prob_badge);
        tvWeatherBigTemp = findViewById(R.id.tv_weather_big_temp);
        tvWeatherIcon = findViewById(R.id.tv_weather_icon);
        tvWeatherDesc = findViewById(R.id.tv_weather_desc);
        tvWeatherFeelsLike = findViewById(R.id.tv_weather_feels_like);
        tvWeatherTodayMinMax = findViewById(R.id.tv_weather_today_minmax);
        tvWeatherWind = findViewById(R.id.tv_weather_wind);
        tvWeatherRainProb = findViewById(R.id.tv_weather_rain_prob);

        tvClothingWarningTag = findViewById(R.id.tv_clothing_warning_tag);
        tvClothingMain = findViewById(R.id.tv_clothing_main);
        tvClothingSub = findViewById(R.id.tv_clothing_sub);
        tvIndexUmbrella = findViewById(R.id.tv_index_umbrella);
        tvIndexSport = findViewById(R.id.tv_index_sport);
        tvIndexCarWash = findViewById(R.id.tv_index_carwash);
        tvIndexCold = findViewById(R.id.tv_index_cold);

        tvTomorrowRainBadge = findViewById(R.id.tv_tomorrow_rain_badge);
        tvTomorrowContrast = findViewById(R.id.tv_tomorrow_contrast);
        tvTomorrowBigTemp = findViewById(R.id.tv_tomorrow_big_temp);
        tvTomorrowIcon = findViewById(R.id.tv_tomorrow_icon);
        tvTomorrowDesc = findViewById(R.id.tv_tomorrow_desc);
        tvTomorrowMinMax = findViewById(R.id.tv_tomorrow_minmax);
        tvTomorrowTempDiff = findViewById(R.id.tv_tomorrow_temp_diff);
        tvTomorrowAdvice = findViewById(R.id.tv_tomorrow_advice);
        tvWeatherIndoorSensor = findViewById(R.id.tv_weather_indoor_sensor);
        tvWeatherPageIndicator = findViewById(R.id.tv_weather_page_indicator);
        weatherParticleView = findViewById(R.id.weather_particle_view);

        if (btnSwitchToScreen1 != null) {
            btnSwitchToScreen1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDashboardScreen();
                }
            });
        }
        if (tvWeatherPageIndicator != null) {
            tvWeatherPageIndicator.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDashboardScreen();
                }
            });
        }
        if (btnWeatherRefresh != null) {
            btnWeatherRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (weatherService != null) {
                        weatherService.fetchWeatherAsync(true);
                        Toast.makeText(MainActivity.this, "🔄 正在同步最新全景气象与穿衣智库...", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Topbar
        tvLiveDot = findViewById(R.id.tv_live_dot);
        tvLinkBadge = findViewById(R.id.tv_link_badge);
        if (tvLinkBadge != null) {
            tvLinkBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }
        View vDeviceTag = findViewById(R.id.tv_device_tag);
        if (vDeviceTag != null) {
            vDeviceTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }
        tvMacBattBadge = findViewById(R.id.tv_mac_batt_badge);
        tvUptime = findViewById(R.id.tv_uptime);
        tvClock = findViewById(R.id.tv_clock);
        tvDate = findViewById(R.id.tv_date);
        btnMic = findViewById(R.id.btn_mic);
        tvMicDot = findViewById(R.id.tv_mic_dot);
        tvMicText = findViewById(R.id.tv_mic_text);
        micWaveView = findViewById(R.id.mic_wave_view);
        tvGuardTag = findViewById(R.id.tv_guard_tag);
        tvBatteryPct = findViewById(R.id.tv_battery_pct);

        // Card 1: CPU 算力引擎 & 物理气象微站
        tvCpuStatTag = findViewById(R.id.tv_cpu_stat_tag);
        tvCpuBigPct = findViewById(R.id.tv_cpu_big_pct);
        tvCpuPeakCore = findViewById(R.id.tv_cpu_peak_core);
        tvCpuBrand = findViewById(R.id.tv_cpu_brand);
        tvLoadAvg = findViewById(R.id.tv_load_avg);
        tvProcCount = findViewById(R.id.tv_proc_count);
        pbCpuTotal = findViewById(R.id.pb_cpu_total);
        cpuSpectrumView = findViewById(R.id.cpu_spectrum_view);
        cpuWaveView = findViewById(R.id.cpu_wave_view);
        tvAmbientComfort = findViewById(R.id.tv_ambient_comfort);
        tvSensorTemp = findViewById(R.id.tv_sensor_temp);
        tvSensorHumidity = findViewById(R.id.tv_sensor_humidity);
        tvSensorPressure = findViewById(R.id.tv_sensor_pressure);

        // Card 2: 内存架构分解与系统存储
        tvMemBigPct = findViewById(R.id.tv_mem_big_pct);
        tvMemDetail = findViewById(R.id.tv_mem_detail);
        tvMemFree = findViewById(R.id.tv_mem_free);
        multiMemBar = findViewById(R.id.multi_mem_bar);
        tvMemApp = findViewById(R.id.tv_mem_app);
        tvMemWired = findViewById(R.id.tv_mem_wired);
        tvMemComp = findViewById(R.id.tv_mem_comp);
        tvSwapVal = findViewById(R.id.tv_swap_val);
        pbSwap = findViewById(R.id.pb_swap);

        tvDiskPct = findViewById(R.id.tv_disk_pct);
        tvDiskUsed = findViewById(R.id.tv_disk_used);
        tvDiskTotal = findViewById(R.id.tv_disk_total);
        tvDiskAvail = findViewById(R.id.tv_disk_avail);
        pbDisk = findViewById(R.id.pb_disk);

        // Card 3
        tvNetDown = findViewById(R.id.tv_net_down);
        tvNetUp = findViewById(R.id.tv_net_up);
        tvNetLanIp = findViewById(R.id.tv_net_lan_ip);
        tvNetPing = findViewById(R.id.tv_net_ping);
        tvNetTotal = findViewById(R.id.tv_net_total);
        waveView = findViewById(R.id.wave_view);

        // Card 4: 电源功耗与活跃高载进程
        tvPowerLevelBadge = findViewById(R.id.tv_power_level_badge);
        tvPowerWatts = findViewById(R.id.tv_power_watts);
        tvAdapterSpec = findViewById(R.id.tv_adapter_spec);
        tvPowerVI = findViewById(R.id.tv_power_vi);
        tvMacBatteryState = findViewById(R.id.tv_mac_battery_state);

        tvP1Name = findViewById(R.id.tv_p1_name);
        tvP1Cpu = findViewById(R.id.tv_p1_cpu);
        tvP1Mem = findViewById(R.id.tv_p1_mem);

        tvP2Name = findViewById(R.id.tv_p2_name);
        tvP2Cpu = findViewById(R.id.tv_p2_cpu);
        tvP2Mem = findViewById(R.id.tv_p2_mem);

        tvP3Name = findViewById(R.id.tv_p3_name);
        tvP3Cpu = findViewById(R.id.tv_p3_cpu);
        tvP3Mem = findViewById(R.id.tv_p3_mem);

        tvScreensaverClock = findViewById(R.id.tv_screensaver_clock);
        aodClockBox = findViewById(R.id.aod_clock_box);
        tvScreensaverStats = findViewById(R.id.tv_screensaver_stats);
        tvAodIndicator = findViewById(R.id.tv_aod_indicator);
        pixelRefreshView = findViewById(R.id.pixel_refresh_view);

        // 点击时钟卡片切换 AOD 屏保
        findViewById(R.id.card_clock).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleScreensaver();
            }
        });

        // 麦克风胶囊点击 (仍支持手机端手动强开/强关)
        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMicrophone();
            }
        });

        // 屏保点击唤醒
        screensaverView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleScreensaver();
            }
        });

        initSchemeAViews();
    }

    private void initSchemeAViews() {
        bottomDockContainer = findViewById(R.id.bottom_dock_container);
        dockFlipper = findViewById(R.id.dock_flipper);
        dockWeatherBlock = findViewById(R.id.dock_weather_block);
        dockHostBlock = findViewById(R.id.dock_host_block);
        tvDockWeatherIcon = findViewById(R.id.tv_dock_weather_icon);
        tvSensorAltitude = findViewById(R.id.tv_sensor_altitude);
        tvCpuSpec = findViewById(R.id.tv_cpu_spec);
        tvDockDots = findViewById(R.id.tv_dock_dots);
        if (tvDockDots != null) {
            tvDockDots.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNextDockWidget();
                }
            });
        }
        tvDockTrafficSum = findViewById(R.id.tv_dock_traffic_sum);
        tvDockDiskDetail = findViewById(R.id.tv_dock_disk_detail);
        tvDockSlateStatus = findViewById(R.id.tv_dock_slate_status);
        tvDockOledStatus = findViewById(R.id.tv_dock_oled_status);

        drawerInspectOverlay = findViewById(R.id.drawer_inspect_overlay);
        cardWeatherDrawer = findViewById(R.id.card_weather_drawer);
        cardHostDrawer = findViewById(R.id.card_host_drawer);
        cardScreenControl = findViewById(R.id.card_screen_control);

        tvDrawerTemp = findViewById(R.id.tv_drawer_temp);
        tvDrawerHumidity = findViewById(R.id.tv_drawer_humidity);
        tvDrawerComfortDesc = findViewById(R.id.tv_drawer_comfort_desc);
        tvDrawerPressure = findViewById(R.id.tv_drawer_pressure);
        tvDrawerAltitude = findViewById(R.id.tv_drawer_altitude);
        tvDrawerPressureTrend = findViewById(R.id.tv_drawer_pressure_trend);
        tvDrawerLightLux = findViewById(R.id.tv_drawer_light_lux);
        tvDrawerRate = findViewById(R.id.tv_drawer_rate);

        tvDrawerCpuModel = findViewById(R.id.tv_drawer_cpu_model);
        tvDrawerCpuCores = findViewById(R.id.tv_drawer_cpu_cores);
        tvDrawerCpuTurbo = findViewById(R.id.tv_drawer_cpu_turbo);
        tvDrawerLoadDetail = findViewById(R.id.tv_drawer_load_detail);
        tvDrawerTasksDetail = findViewById(R.id.tv_drawer_tasks_detail);
        tvDrawerUptimeDetail = findViewById(R.id.tv_drawer_uptime_detail);

        sbScreenBrightness = findViewById(R.id.sb_screen_brightness);
        tvBrightnessVal = findViewById(R.id.tv_brightness_val);
        btnQuickAod = findViewById(R.id.btn_quick_aod);
        btnQuickWash = findViewById(R.id.btn_quick_wash);
        btnQuickNight = findViewById(R.id.btn_quick_night);

        if (dockWeatherBlock != null) {
            dockWeatherBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showWeatherDrawer();
                }
            });
            dockWeatherBlock.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showScreenControlDrawer();
                    return true;
                }
            });
        }

        if (dockHostBlock != null) {
            dockHostBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHostDrawer();
                }
            });
            dockHostBlock.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showScreenControlDrawer();
                    return true;
                }
            });
        }

        if (bottomDockContainer != null) {
            bottomDockContainer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showScreenControlDrawer();
                    return true;
                }
            });
        }

        if (drawerInspectOverlay != null) {
            drawerInspectOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                }
            });
        }

        View bCloseW = findViewById(R.id.btn_close_weather_drawer);
        if (bCloseW != null) {
            bCloseW.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                }
            });
        }
        View bCloseH = findViewById(R.id.btn_close_host_drawer);
        if (bCloseH != null) {
            bCloseH.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                }
            });
        }
        View bCloseS = findViewById(R.id.btn_close_screen_drawer);
        if (bCloseS != null) {
            bCloseS.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                }
            });
        }

        if (sbScreenBrightness != null) {
            sbScreenBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int p = Math.max(10, progress);
                        if (tvBrightnessVal != null) tvBrightnessVal.setText(p + "%");
                        setWindowBrightness(p / 100.0f);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnQuickAod != null) {
            btnQuickAod.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                    enterAodMode();
                }
            });
        }

        if (btnQuickWash != null) {
            btnQuickWash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                    startPixelRefreshRoutine();
                }
            });
        }

        if (btnQuickNight != null) {
            btnQuickNight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDrawers();
                    setWindowBrightness(0.08f);
                    if (sbScreenBrightness != null) sbScreenBrightness.setProgress(8);
                    if (tvBrightnessVal != null) tvBrightnessVal.setText("8%");
                    Toast.makeText(MainActivity.this, "已切换夜间低亮模式 (8% 亮度)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnConfigHost = findViewById(R.id.btn_config_host);
        if (btnConfigHost != null) {
            btnConfigHost.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }

        if (tvNetLanIp != null) {
            tvNetLanIp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }
        View cardNet = findViewById(R.id.card_net);
        if (cardNet != null) {
            cardNet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showConnectionSettingsDialog();
                }
            });
        }



        setupDockGestureDetector();
    }

    private void setupDockGestureDetector() {
        dockGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 50;
            private static final int SWIPE_VELOCITY_THRESHOLD = 50;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            showNextDockWidget();
                        } else {
                            showPrevDockWidget();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void showNextDockWidget() {
        if (dockFlipper == null) return;
        dockFlipper.setInAnimation(this, android.R.anim.slide_in_left);
        dockFlipper.setOutAnimation(this, android.R.anim.slide_out_right);
        dockFlipper.showNext();
        updateDockIndicator();
    }

    private void showPrevDockWidget() {
        if (dockFlipper == null) return;
        dockFlipper.showPrevious();
        updateDockIndicator();
    }

    private void updateDockIndicator() {
        if (tvDockDots == null || dockFlipper == null) return;
        int idx = dockFlipper.getDisplayedChild();
        if (idx == 0) {
            tvDockDots.setText("● ○ ○");
        } else if (idx == 1) {
            tvDockDots.setText("○ ● ○");
        } else {
            tvDockDots.setText("○ ○ ●");
        }
    }

    private void showWeatherDrawer() {
        if (drawerInspectOverlay == null) return;
        updateWeatherDrawer();
        drawerInspectOverlay.setVisibility(View.VISIBLE);
        if (cardWeatherDrawer != null) cardWeatherDrawer.setVisibility(View.VISIBLE);
        if (cardHostDrawer != null) cardHostDrawer.setVisibility(View.GONE);
        if (cardScreenControl != null) cardScreenControl.setVisibility(View.GONE);
    }

    private void showHostDrawer() {
        if (drawerInspectOverlay == null) return;
        updateHostDrawer();
        drawerInspectOverlay.setVisibility(View.VISIBLE);
        if (cardWeatherDrawer != null) cardWeatherDrawer.setVisibility(View.GONE);
        if (cardHostDrawer != null) cardHostDrawer.setVisibility(View.VISIBLE);
        if (cardScreenControl != null) cardScreenControl.setVisibility(View.GONE);
    }

    private void showScreenControlDrawer() {
        if (drawerInspectOverlay == null) return;
        drawerInspectOverlay.setVisibility(View.VISIBLE);
        if (cardWeatherDrawer != null) cardWeatherDrawer.setVisibility(View.GONE);
        if (cardHostDrawer != null) cardHostDrawer.setVisibility(View.GONE);
        if (cardScreenControl != null) cardScreenControl.setVisibility(View.VISIBLE);
    }

    private void closeDrawers() {
        if (drawerInspectOverlay != null) {
            drawerInspectOverlay.setVisibility(View.GONE);
            if (cardWeatherDrawer != null) cardWeatherDrawer.setVisibility(View.GONE);
            if (cardHostDrawer != null) cardHostDrawer.setVisibility(View.GONE);
            if (cardScreenControl != null) cardScreenControl.setVisibility(View.GONE);
        }
    }

    private void updateWeatherDrawer() {
        if (tvDrawerTemp != null) {
            tvDrawerTemp.setText(String.format(Locale.getDefault(), "室温: %.1f °C", lastAmbientTemp));
        }
        if (tvDrawerHumidity != null) {
            tvDrawerHumidity.setText(String.format(Locale.getDefault(), "相对湿度: %.0f%% RH", lastHumidity));
        }
        if (tvDrawerComfortDesc != null) {
            double dewPoint = lastAmbientTemp - ((100 - lastHumidity) / 5.0);
            CharSequence cLabel = (tvAmbientComfort != null) ? tvAmbientComfort.getText() : "舒适";
            tvDrawerComfortDesc.setText(String.format(Locale.getDefault(), "露点: %.1f°C · 体感: %s", dewPoint, cLabel));
        }
        if (tvDrawerPressure != null) {
            tvDrawerPressure.setText(String.format(Locale.getDefault(), "气压: %.1f hPa", lastPressure > 0 ? lastPressure : 1016.4f));
        }
        if (tvDrawerAltitude != null) {
            float alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, lastPressure > 0 ? lastPressure : 1013.25f);
            tvDrawerAltitude.setText(String.format(Locale.getDefault(), "物理海拔: ~ %.0f m", alt));
        }
        if (tvDrawerLightLux != null) {
            tvDrawerLightLux.setText(String.format(Locale.getDefault(), "照度: %.1f Lux (实机光感)", currentLux > 0 ? currentLux : 120.0f));
        }
    }

    private void updateHostDrawer() {
        if (tvDrawerCpuModel != null && tvCpuBrand != null) {
            tvDrawerCpuModel.setText(tvCpuBrand.getText());
        }
        if (tvDrawerLoadDetail != null && tvLoadAvg != null) {
            tvDrawerLoadDetail.setText("Load Average: " + tvLoadAvg.getText());
        }
        if (tvDrawerTasksDetail != null && tvProcCount != null) {
            tvDrawerTasksDetail.setText("活跃任务: " + tvProcCount.getText() + " (内核调度畅通)");
        }
        if (tvDrawerUptimeDetail != null) {
            tvDrawerUptimeDetail.setText("系统运行: " + currentUptime + " (持续稳定)");
        }
    }

    private void setWindowBrightness(float brightness) {
        currentScreenBrightness = brightness;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = brightness;
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
    }

    private void startWeatherPulse() {
        if (weatherPulseAnim == null && tvDockWeatherIcon != null) {
            weatherPulseAnim = ObjectAnimator.ofFloat(tvDockWeatherIcon, "alpha", 1.0f, 0.35f, 1.0f);
            weatherPulseAnim.setDuration(1200);
            weatherPulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            weatherPulseAnim.start();
        }
    }

    private void stopWeatherPulse() {
        if (weatherPulseAnim != null) {
            weatherPulseAnim.cancel();
            weatherPulseAnim = null;
            if (tvDockWeatherIcon != null) {
                tvDockWeatherIcon.setAlpha(1.0f);
            }
        }
    }

    public void enterAodMode() {
        if (isAodMode) return;
        isAodMode = true;
        screensaverView.setVisibility(View.VISIBLE);
        dashboardContainer.setVisibility(View.GONE);
        if (aodClockBox != null) {
            aodClockBox.setTranslationX(0);
            aodClockBox.setTranslationY(0);
        }
        applyImmersiveSticky();
    }

    public void exitAodMode() {
        if (!isAodMode) return;
        isAodMode = false;
        screensaverView.setVisibility(View.GONE);
        dashboardContainer.setVisibility(View.VISIBLE);
        lastUserTouchTime = System.currentTimeMillis();
        applyImmersiveSticky();
    }

    private void toggleScreensaver() {
        if (isAodMode) {
            exitAodMode();
        } else {
            enterAodMode();
        }
    }

    private void showConnectionSettingsDialog() {
        closeDrawers();
        final SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedIp = sp.getString(KEY_SAVED_MAC_IP, "");
        String customHost = sp.getString("custom_host", "");
        int customPort = sp.getInt("custom_port", 9527);
        String customPath = sp.getString("custom_path", "/api/stats");

        if (customHost.isEmpty() && !savedIp.isEmpty()) {
            customHost = savedIp;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("🖥️ DeskPilot · 服务器连接与网络配置");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        TextView tvInfo = new TextView(this);
        String currentMode = "未知";
        if (statsClient != null) {
            StatsClient.LinkType link = statsClient.getCurrentLink();
            if (link == StatsClient.LinkType.USB) {
                currentMode = "⚡ USB 硬件直连 (127.0.0.1:9527)";
            } else if (link == StatsClient.LinkType.CLOUD) {
                currentMode = "☁️ Google 云端直连 (" + statsClient.getCurrentActiveHost() + ")";
            } else if (link == StatsClient.LinkType.WIFI) {
                currentMode = "📶 Wi-Fi 局域网 (" + statsClient.getCurrentActiveHost() + ")";
            } else {
                currentMode = "⚠️ 寻找/重连主机中...";
            }
        }
        tvInfo.setText("当前通信链路：" + currentMode + "\n\n💡 快捷切换预设链路：");
        tvInfo.setTextColor(Color.parseColor("#a0b0c0"));
        tvInfo.setTextSize(11);
        layout.addView(tvInfo);

        final EditText etHost = new EditText(this);
        final EditText etPort = new EditText(this);
        final EditText etPath = new EditText(this);

        // 预设快捷按钮横向排布
        LinearLayout presetsLayout = new LinearLayout(this);
        presetsLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetsLayout.setPadding(0, 10, 0, 15);

        Button btnUsb = new Button(this);
        btnUsb.setText("⚡ USB 本地模式");
        btnUsb.setTextSize(11);
        btnUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etHost.setText("127.0.0.1");
                etPort.setText("9527");
                etPath.setText("/api/stats");
            }
        });

        Button btnScan = new Button(this);
        btnScan.setText("🔍 自动扫网发现");
        btnScan.setTextSize(11);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (statsClient != null) {
                    Toast.makeText(MainActivity.this, "正在快速扫描局域网找寻 Mac 主机...", Toast.LENGTH_SHORT).show();
                    statsClient.scanSubnetForMac();
                }
            }
        });

        presetsLayout.addView(btnUsb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        presetsLayout.addView(btnScan, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        layout.addView(presetsLayout);

        TextView tvHostLabel = new TextView(this);
        tvHostLabel.setText("主机地址 / IP / 域名 (可输入)：");
        tvHostLabel.setTextColor(Color.parseColor("#8b949e"));
        tvHostLabel.setTextSize(11);
        layout.addView(tvHostLabel);

        etHost.setHint("输入 IP 或域名 (如 192.168.1.100)");
        etHost.setText(customHost.isEmpty() ? "127.0.0.1" : customHost);
        etHost.setTextColor(Color.WHITE);
        etHost.setTextSize(13);
        layout.addView(etHost);

        TextView tvPortLabel = new TextView(this);
        tvPortLabel.setText("端口号 Port (默认 9527)：");
        tvPortLabel.setTextColor(Color.parseColor("#8b949e"));
        tvPortLabel.setTextSize(11);
        layout.addView(tvPortLabel);

        etPort.setHint("端口号 (默认 9527)");
        etPort.setText(String.valueOf(customPort > 0 ? customPort : 9527));
        etPort.setTextColor(Color.WHITE);
        etPort.setTextSize(13);
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etPort);

        TextView tvPathLabel = new TextView(this);
        tvPathLabel.setText("API 路径 (默认 /api/stats)：");
        tvPathLabel.setTextColor(Color.parseColor("#8b949e"));
        tvPathLabel.setTextSize(11);
        layout.addView(tvPathLabel);

        etPath.setHint("如 /api/stats");
        etPath.setText(customPath.isEmpty() ? "/api/stats" : customPath);
        etPath.setTextColor(Color.WHITE);
        etPath.setTextSize(13);
        layout.addView(etPath);

        builder.setView(layout);

        builder.setPositiveButton("保存并立即连接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String hostInput = etHost.getText().toString().trim();
                String portInput = etPort.getText().toString().trim();
                String pathInput = etPath.getText().toString().trim();
                int port = 9527;
                try {
                    port = Integer.parseInt(portInput);
                } catch (Exception ignored) {}
                if (pathInput.isEmpty()) pathInput = "/api/stats";

                sp.edit()
                    .putString("custom_host", hostInput)
                    .putInt("custom_port", port)
                    .putString("custom_path", pathInput)
                    .putString(KEY_SAVED_MAC_IP, hostInput)
                    .apply();

                if (statsClient != null) {
                    statsClient.setCustomServer(hostInput, port, pathInput);
                    statsClient.setFallbackHost(hostInput);
                }
                Toast.makeText(MainActivity.this, "已保存目标服务器: " + hostInput + ":" + port + pathInput + "，正在连接...", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showWeatherCityDialog() {
        if (weatherService == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("📍 气象城市位置配置 (支持自定义)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        TextView tvTip = new TextView(this);
        tvTip.setText("当前所在城市：" + (currentWeatherData != null ? currentWeatherData.city : "定位中...") + "\n可手动输入任意城市名称，或点击快捷选择：");
        tvTip.setTextColor(Color.parseColor("#a0b0c0"));
        tvTip.setTextSize(11);
        layout.addView(tvTip);

        final EditText etCity = new EditText(this);
        etCity.setHint("输入城市名，如 北京市、杭州市、深圳市...");
        etCity.setText(currentWeatherData != null ? currentWeatherData.city : "");
        etCity.setTextColor(Color.WHITE);
        etCity.setTextSize(14);
        layout.addView(etCity);

        // 快捷城市预设按钮
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, 10, 0, 5);

        String[] quickCities = new String[]{"北京市", "杭州市", "上海市", "深圳市"};
        for (final String c : quickCities) {
            Button btn = new Button(this);
            btn.setText(c);
            btn.setTextSize(11);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etCity.setText(c);
                }
            });
            row1.addView(btn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        }
        layout.addView(row1);

        builder.setView(layout);

        builder.setPositiveButton("确认切换", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String cityName = etCity.getText().toString().trim();
                if (!cityName.isEmpty()) {
                    weatherService.setCustomCity(cityName);
                    Toast.makeText(MainActivity.this, "已设置城市为: " + cityName + "，正在刷新数据...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNeutralButton("📍 恢复自动定位", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                weatherService.setAutoDetectCity();
                Toast.makeText(MainActivity.this, "已切换为 IP 自动地理定位，正在同步...", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleScreensaver();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                startPixelRefreshRoutine();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (drawerInspectOverlay != null && drawerInspectOverlay.getVisibility() == View.VISIBLE) {
                    return false;
                }
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 70 && Math.abs(velocityX) > 100) {
                    if (diffX < 0) {
                        showWeatherScreen();
                        return true;
                    } else {
                        showDashboardScreen();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private long pixelRefreshStartTime = 0;

    private void startPixelRefreshRoutine() {
        if (pixelRefreshView != null) {
            pixelRefreshStartTime = System.currentTimeMillis();
            Toast.makeText(this, "已进入持续洗屏保养模式 (单击任意处退出)", Toast.LENGTH_SHORT).show();
            pixelRefreshView.startRefresh(null);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastUserTouchTime = System.currentTimeMillis();
        if (isAodMode) {
            exitAodMode();
            return true;
        }
        if (pixelRefreshView != null && pixelRefreshView.isRefreshing()) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && (System.currentTimeMillis() - pixelRefreshStartTime > 500)) {
                pixelRefreshView.stopRefresh();
                Toast.makeText(this, "已退出洗屏保养模式", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // 如果下钻抽屉浮层处于打开状态，交给浮层处理，不触发全屏双击/长按
        if (drawerInspectOverlay != null && drawerInspectOverlay.getVisibility() == View.VISIBLE) {
            return super.dispatchTouchEvent(ev);
        }
        // 底部 Dock 区域触摸拦截与滑动检测
        if (bottomDockContainer != null && bottomDockContainer.getVisibility() == View.VISIBLE) {
            int[] loc = new int[2];
            bottomDockContainer.getLocationOnScreen(loc);
            float x = ev.getRawX();
            float y = ev.getRawY();
            if (x >= loc[0] && x <= loc[0] + bottomDockContainer.getWidth() &&
                y >= loc[1] && y <= loc[1] + bottomDockContainer.getHeight()) {
                if (dockGestureDetector != null && dockGestureDetector.onTouchEvent(ev)) {
                    return true;
                }
                // 触摸位于底部 Dock 内部：直接分发给 Dock 内子视图 (不要触发全屏双击/洗屏长按)
                return super.dispatchTouchEvent(ev);
            }
        }
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void startClockAndOrbiting() {
        final SimpleDateFormat screensaverFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy年M月d日 EEE", Locale.CHINESE);

        handler.post(new Runnable() {
            private int secondsCount = 0;

            @Override
            public void run() {
                Date now = new Date();
                tvScreensaverClock.setText(screensaverFmt.format(now));

                if (tvClock != null) {
                    String timeStr = timeFmt.format(now);
                    tvClock.setText(timeStr);
                    if (tvWeatherClock != null) tvWeatherClock.setText(timeStr);
                }
                if (tvDate != null) {
                    String dateStr = dateFmt.format(now);
                    if (tvDate.getText().toString().isEmpty()) {
                        tvDate.setText(dateStr);
                    }
                    if (tvWeatherDate != null) tvWeatherDate.setText(dateStr);
                }

                secondsCount++;

                // 1. 每 45 秒执行一次 16 点环形漫游防烧屏 (Enhanced Pixel Orbiting)
                if (secondsCount % 45 == 0) {
                    orbitIndex = (orbitIndex + 1) % orbitOffsets.length;
                    int ox = orbitOffsets[orbitIndex][0];
                    int oy = orbitOffsets[orbitIndex][1];
                    dashboardContainer.setTranslationX(ox);
                    dashboardContainer.setTranslationY(oy);
                }

                // 2. 每 180 秒执行一次子像素微色温呼吸轮换 (Subpixel Chroma Cycling)
                if (secondsCount % 180 == 0) {
                    chromaIndex = (chromaIndex + 1) % CHROMA_PALETTE.length;
                    int chroma = CHROMA_PALETTE[chromaIndex];
                    if (tvClock != null) tvClock.setTextColor(chroma);
                    if (tvSensorTemp != null) tvSensorTemp.setTextColor(chroma);
                    if (tvDiskPct != null) tvDiskPct.setTextColor(chroma);
                }

                // 3. AOD 纯黑漂移时钟大跨度漂移 (每 60 秒随机平滑漫游，复刻三星 Galaxy AOD)
                if (isAodMode) {
                    if (secondsCount % 60 == 0 && aodClockBox != null) {
                        float maxRangeX = 100f;
                        float maxRangeY = 50f;
                        float randX = (float) ((Math.random() * 2 - 1) * maxRangeX);
                        float randY = (float) ((Math.random() * 2 - 1) * maxRangeY);
                        aodClockBox.animate().translationX(randX).translationY(randY).setDuration(1200).start();
                    }
                    if (tvScreensaverStats != null) {
                        tvScreensaverStats.setText(lastMacStatsSummary);
                    }
                } else {
                    // 4. 智能休眠监测 (自动切入纯黑 AOD 模式)
                    long idleMillis = System.currentTimeMillis() - lastUserTouchTime;
                    boolean userIdleLong = (idleMillis > 12 * 60 * 1000); // 用户 12 分钟未触碰
                    boolean macIdleLong = (macLowLoadStreak > 600); // Mac 极低负载超过 10 分钟
                    boolean macOfflineLong = (macDisconnectStreak > 180); // 链路断开超过 3 分钟

                    if (userIdleLong && (macIdleLong || macOfflineLong)) {
                        enterAodMode();
                    }
                }

                handler.postDelayed(this, 1000);
            }
        });
    }

    private void updateMacStats(StatsClient.MacStats stats, StatsClient.LinkType linkType, String host) {
        dashboardContainer.setAlpha(1.0f);
        tvLiveDot.setText("● LIVE");
        tvLiveDot.setTextColor(Color.parseColor("#00f59b"));

        // 更新 AOD 摘要与低载统计
        lastMacStatsSummary = String.format(Locale.getDefault(), "MAC %d%% · %.1f°C · %.1fW",
                stats.macBatteryPercent > 0 ? stats.macBatteryPercent : 100,
                lastAmbientTemp > 0 ? lastAmbientTemp : 23.0f,
                stats.power != null ? stats.power.watts : 0.0);

        if (stats.cpuPercent < 8.0) {
            macLowLoadStreak++;
        } else {
            macLowLoadStreak = 0;
            if (isAodMode && stats.cpuPercent > 15.0) {
                exitAodMode();
            }
        }
        macDisconnectStreak = 0;

        // 更新链路指示胶囊 (双屏同步)
        if (linkType == StatsClient.LinkType.USB) {
            tvLinkBadge.setText("⚡ USB 直连");
            tvLinkBadge.setTextColor(Color.parseColor("#00f59b"));
            tvLinkBadge.setBackgroundColor(Color.parseColor("#0a2a1c"));
            if (tvWeatherLinkBadge != null) {
                tvWeatherLinkBadge.setText("⚡ USB 直连");
                tvWeatherLinkBadge.setTextColor(Color.parseColor("#00f59b"));
                tvWeatherLinkBadge.setBackgroundColor(Color.parseColor("#0a2a1c"));
            }
        } else if (linkType == StatsClient.LinkType.WIFI) {
            tvLinkBadge.setText("📶 Wi-Fi 局域网");
            tvLinkBadge.setTextColor(Color.parseColor("#00d2ff"));
            tvLinkBadge.setBackgroundColor(Color.parseColor("#0a1e2a"));
            if (tvWeatherLinkBadge != null) {
                tvWeatherLinkBadge.setText("📶 Wi-Fi 局域网");
                tvWeatherLinkBadge.setTextColor(Color.parseColor("#00d2ff"));
                tvWeatherLinkBadge.setBackgroundColor(Color.parseColor("#0a1e2a"));
            }
        } else if (linkType == StatsClient.LinkType.CLOUD) {
            tvLinkBadge.setText("☁️ 云端直连");
            tvLinkBadge.setTextColor(Color.parseColor("#ff79c6"));
            tvLinkBadge.setBackgroundColor(Color.parseColor("#2a0a20"));
            if (tvWeatherLinkBadge != null) {
                tvWeatherLinkBadge.setText("☁️ 云端直连");
                tvWeatherLinkBadge.setTextColor(Color.parseColor("#ff79c6"));
                tvWeatherLinkBadge.setBackgroundColor(Color.parseColor("#2a0a20"));
            }
        }
        if (tvWeatherLiveDot != null) {
            tvWeatherLiveDot.setText("● LIVE");
            tvWeatherLiveDot.setTextColor(Color.parseColor("#00f59b"));
        }

        // 动态将音频传输的目标 IP 同步到当前活跃的主机地址
        if (audioStreamer != null && host != null && !host.isEmpty()) {
            audioStreamer.setTargetHost(host);
        }

        if (stats.macTime != null && !stats.macTime.isEmpty()) {
            tvClock.setText(stats.macTime);
        }
        if (stats.macDate != null && !stats.macDate.isEmpty()) {
            tvDate.setText(stats.macDate);
        }

        if (stats.uptime != null) {
            currentUptime = stats.uptime;
            tvUptime.setText(stats.uptime);
        }
        tvUptime.setTextColor(Color.parseColor("#506070"));

        // 1. CPU 算力引擎 (8-CORE SPECTRUM)
        int cpuInt = (int) Math.round(stats.cpuPercent);
        tvCpuBigPct.setText(cpuInt + "%");
        pbCpuTotal.setProgress(cpuInt);

        int cpuColor = (stats.cpuPercent > 70) ? Color.parseColor("#ff4757") :
                (stats.cpuPercent > 40) ? Color.parseColor("#ffd166") : Color.parseColor("#00f59b");
        tvCpuBigPct.setTextColor(cpuColor);

        String cpuStat = (stats.cpuPercent > 70) ? "HEAVY" : (stats.cpuPercent > 30) ? "ACTIVE" : "NORMAL";
        tvCpuStatTag.setText(cpuStat);
        if ("HEAVY".equals(cpuStat)) {
            tvCpuStatTag.setTextColor(Color.parseColor("#ff4757"));
            tvCpuStatTag.setBackgroundColor(Color.parseColor("#2a0a10"));
        } else if ("ACTIVE".equals(cpuStat)) {
            tvCpuStatTag.setTextColor(Color.parseColor("#ffd166"));
            tvCpuStatTag.setBackgroundColor(Color.parseColor("#2a200a"));
        } else {
            tvCpuStatTag.setTextColor(Color.parseColor("#00f59b"));
            tvCpuStatTag.setBackgroundColor(Color.parseColor("#0a2a1c"));
        }

        // 计算峰值核心
        int peakCoreIdx = 0;
        double peakCoreVal = 0.0;
        if (stats.cpuCores != null && stats.cpuCores.length > 0) {
            for (int i = 0; i < stats.cpuCores.length; i++) {
                if (stats.cpuCores[i] > peakCoreVal) {
                    peakCoreVal = stats.cpuCores[i];
                    peakCoreIdx = i;
                }
            }
            tvCpuPeakCore.setText(String.format(Locale.getDefault(), "C%d 峰值 %.0f%%", peakCoreIdx + 1, peakCoreVal));
            int peakColor = (peakCoreVal > 70) ? Color.parseColor("#ff4757") :
                    (peakCoreVal > 35) ? Color.parseColor("#ffd166") : Color.parseColor("#00f59b");
            tvCpuPeakCore.setTextColor(peakColor);
        }

        // 驱动 8 核心动态均衡器频谱 View
        if (cpuSpectrumView != null) {
            cpuSpectrumView.updateCores(stats.cpuCores);
        }

        // 驱动 60s 负荷脉冲微折线 (Sparkline)
        if (cpuWaveView != null) {
            cpuWaveView.addSample((float) stats.cpuPercent);
        }

        // 工控遥测: CPU 型号 / UNIX 系统负载均值 / 内核任务总数
        if (tvCpuBrand != null && stats.cpuBrand != null) {
            tvCpuBrand.setText(stats.cpuBrand);
        }
        if (tvLoadAvg != null && stats.loadAvg != null) {
            tvLoadAvg.setText(stats.loadAvg);
        }
        if (tvProcCount != null) {
            tvProcCount.setText(stats.procCount + " TASKS");
        }

        // 2. 内存架构分解与系统存储 (MEMORY & STORAGE)
        int memInt = (int) Math.round(stats.memPercent);
        tvMemBigPct.setText(memInt + "%");
        int memColor = (stats.memPercent > 80) ? Color.parseColor("#ff4757") :
                (stats.memPercent > 60) ? Color.parseColor("#ffd166") : Color.parseColor("#00d2ff");
        tvMemBigPct.setTextColor(memColor);

        if (multiMemBar != null) {
            multiMemBar.setSegments(stats.memAppGb, stats.memWiredGb, stats.memCompGb, stats.memTotalGb);
        }

        tvMemDetail.setText(String.format(Locale.getDefault(), "%.1f / %.1f GB", stats.memUsedGb, stats.memTotalGb));
        double freeMem = Math.max(0, stats.memTotalGb - stats.memUsedGb);
        tvMemFree.setText(String.format(Locale.getDefault(), "可用 %.1f GB", freeMem));

        // macOS 细分：APP / WIRED / COMP
        if (tvMemApp != null) {
            double app = stats.memAppGb > 0 ? stats.memAppGb : (stats.memUsedGb * 0.6);
            tvMemApp.setText(String.format(Locale.getDefault(), "APP %.1fG", app));
        }
        if (tvMemWired != null) {
            double wired = stats.memWiredGb > 0 ? stats.memWiredGb : (stats.memUsedGb * 0.25);
            tvMemWired.setText(String.format(Locale.getDefault(), "核心 %.1fG", wired));
        }
        if (tvMemComp != null) {
            double comp = stats.memCompGb > 0 ? stats.memCompGb : (stats.memUsedGb * 0.15);
            tvMemComp.setText(String.format(Locale.getDefault(), "压缩 %.1fG", comp));
        }

        // SWAP 虚拟交换区
        if (tvSwapVal != null && pbSwap != null) {
            if (stats.swapTotalMb > 0) {
                tvSwapVal.setText(String.format(Locale.getDefault(), "%.0f / %.0f MB", stats.swapUsedMb, stats.swapTotalMb));
                int swapPct = (int) Math.min(100, Math.round((stats.swapUsedMb / stats.swapTotalMb) * 100));
                pbSwap.setProgress(swapPct);
                if (swapPct > 50) {
                    tvSwapVal.setTextColor(Color.parseColor("#ff4757"));
                } else if (swapPct > 20) {
                    tvSwapVal.setTextColor(Color.parseColor("#ffd166"));
                } else {
                    tvSwapVal.setTextColor(Color.parseColor("#00f59b"));
                }
            } else {
                tvSwapVal.setText("0 MB (未启用)");
                pbSwap.setProgress(0);
                tvSwapVal.setTextColor(Color.parseColor("#506070"));
            }
        }

        int diskInt = (int) Math.round(stats.diskPercent);
        tvDiskPct.setText(diskInt + "%");
        pbDisk.setProgress(diskInt);
        tvDiskUsed.setText(String.format(Locale.getDefault(), "已用 %.0f GB", stats.diskUsedGb));
        tvDiskTotal.setText(String.format(Locale.getDefault(), "总量 %.0f GB", stats.diskTotalGb));
        double availDisk = Math.max(0, stats.diskTotalGb - stats.diskUsedGb);
        tvDiskAvail.setText(String.format(Locale.getDefault(), "剩余 %.0f GB", availDisk));

        if (tvDockTrafficSum != null) {
            tvDockTrafficSum.setText("累计流速: ↓ " + stats.totalIn + "  ↑ " + stats.totalOut + " · 局域网自愈模式");
        }
        if (tvDockDiskDetail != null) {
            tvDockDiskDetail.setText(String.format(Locale.getDefault(), "APFS 卷 · 剩余 %.0f GB / %.0f GB · TRIM 就绪", availDisk, stats.diskTotalGb));
        }
        if (drawerInspectOverlay != null && drawerInspectOverlay.getVisibility() == View.VISIBLE && cardHostDrawer != null && cardHostDrawer.getVisibility() == View.VISIBLE) {
            updateHostDrawer();
        }

        // 3. 网络与波形
        tvNetDown.setText(stats.netRx);
        tvNetUp.setText(stats.netTx);
        if (tvNetLanIp != null && stats.lanIP != null) {
            tvNetLanIp.setText("IP " + stats.lanIP);
        }
        if (tvNetPing != null) {
            tvNetPing.setText("PING " + stats.pingMs + " ●");
        }
        if (tvNetTotal != null) {
            tvNetTotal.setText("↓ " + stats.totalIn + " · ↑ " + stats.totalOut);
        }
        waveView.addSample(stats.netDownBytes, stats.netUpBytes);

        // 4. 电源功耗与活跃进程
        if (stats.power != null) {
            tvPowerWatts.setText(String.format(Locale.getDefault(), "%.1f", stats.power.watts));

            // 能效等级徽章颜色及样式
            String level = (stats.power.level != null) ? stats.power.level : "BALANCED";
            tvPowerLevelBadge.setText(level);
            if ("HIGH".equalsIgnoreCase(level) || stats.power.watts > 35) {
                tvPowerLevelBadge.setTextColor(Color.parseColor("#ff4757"));
                tvPowerLevelBadge.setBackgroundColor(Color.parseColor("#2a0a10"));
                tvPowerWatts.setTextColor(Color.parseColor("#ff4757"));
            } else if ("LOW".equalsIgnoreCase(level) || stats.power.watts < 12) {
                tvPowerLevelBadge.setTextColor(Color.parseColor("#00f59b"));
                tvPowerLevelBadge.setBackgroundColor(Color.parseColor("#0a2a1c"));
                tvPowerWatts.setTextColor(Color.parseColor("#00f59b"));
            } else {
                tvPowerLevelBadge.setTextColor(Color.parseColor("#00d2ff"));
                tvPowerLevelBadge.setBackgroundColor(Color.parseColor("#0a1e2a"));
                tvPowerWatts.setTextColor(Color.parseColor("#00f59b"));
            }

            // 适配器供电规格
            if (stats.power.isAC) {
                String adName = (stats.power.adapterName != null && !stats.power.adapterName.isEmpty()) ?
                        stats.power.adapterName : (stats.power.adapterWatts + "W 适配器");
                tvAdapterSpec.setText(adName);
                tvAdapterSpec.setTextColor(Color.parseColor("#00d2ff"));
            } else {
                tvAdapterSpec.setText("纯电池供电");
                tvAdapterSpec.setTextColor(Color.parseColor("#ffd166"));
            }

            // 电压/电流
            if (stats.power.voltage > 0) {
                tvPowerVI.setText(String.format(Locale.getDefault(), "%.1fV · %.2fA", stats.power.voltage, stats.power.amperage));
            } else {
                tvPowerVI.setText("协议握手完成");
            }
        }

        // Mac 主机电量状态
        if (stats.macBatteryPercent > 0) {
            String battStateStr = stats.macCharging ? "充电中" : (stats.macBatteryPercent >= 80 ? "旁路" : "放电中");
            tvMacBattBadge.setText("MAC " + stats.macBatteryPercent + "%");
            tvMacBatteryState.setText("MAC " + stats.macBatteryPercent + "% " + battStateStr);
            if (stats.macBatteryPercent <= 20 && !stats.macCharging) {
                tvMacBattBadge.setTextColor(Color.parseColor("#ff4757"));
                tvMacBatteryState.setTextColor(Color.parseColor("#ff4757"));
            } else {
                tvMacBattBadge.setTextColor(Color.parseColor("#00d2ff"));
                tvMacBatteryState.setTextColor(Color.parseColor("#00f59b"));
            }
        } else {
            tvMacBattBadge.setText("MAC AC");
            tvMacBatteryState.setText("MAC 交流直接供电");
        }

        // 活跃进程与内存开销
        if (stats.processes.size() > 0) {
            StatsClient.ProcessInfo p0 = stats.processes.get(0);
            tvP1Name.setText(p0.name);
            tvP1Cpu.setText(String.format(Locale.getDefault(), "%.1f%%", p0.cpu));
            if (p0.mem != null && !p0.mem.isEmpty()) {
                tvP1Mem.setText(p0.mem);
                tvP1Mem.setVisibility(View.VISIBLE);
            }
        }
        if (stats.processes.size() > 1) {
            StatsClient.ProcessInfo p1 = stats.processes.get(1);
            tvP2Name.setText(p1.name);
            tvP2Cpu.setText(String.format(Locale.getDefault(), "%.1f%%", p1.cpu));
            if (p1.mem != null && !p1.mem.isEmpty()) {
                tvP2Mem.setText(p1.mem);
                tvP2Mem.setVisibility(View.VISIBLE);
            }
        }
        if (stats.processes.size() > 2) {
            StatsClient.ProcessInfo p2 = stats.processes.get(2);
            tvP3Name.setText(p2.name);
            tvP3Cpu.setText(String.format(Locale.getDefault(), "%.1f%%", p2.cpu));
            if (p2.mem != null && !p2.mem.isEmpty()) {
                tvP3Mem.setText(p2.mem);
                tvP3Mem.setVisibility(View.VISIBLE);
            }
        }
    }

    private void registerBatteryMonitor() {
        BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : level;

                boolean isSlateMode = false;
                try {
                    File f = new File("/sys/class/power_supply/battery/batt_slate_mode");
                    if (f.exists()) {
                        BufferedReader br = new BufferedReader(new FileReader(f));
                        String line = br.readLine();
                        br.close();
                        if ("1".equals(line != null ? line.trim() : "")) {
                            isSlateMode = true;
                        }
                    }
                } catch (Exception ignored) {}

                tvBatteryPct.setText(pct + "%");
                if (isSlateMode || pct >= 80) {
                    tvGuardTag.setText("(旁路)");
                    tvGuardTag.setTextColor(Color.parseColor("#00f59b"));
                } else {
                    tvGuardTag.setText("(充电)");
                    tvGuardTag.setTextColor(Color.parseColor("#00d2ff"));
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void toggleMicrophone() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_CODE);
            return;
        }

        // 触控缩放回弹微动效 (友好触控反馈)
        btnMic.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction(new Runnable() {
            @Override
            public void run() {
                btnMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        }).start();

        if (audioStreamer.isRecording()) {
            // 用户在手机上主动关闭：设置手动静音标志，清除手动强制开启
            userManualMute = true;
            userManualForceActive = false;
            setMicrophoneActive(false, true);
        } else {
            // 用户在手机上主动开启：清除手动静音，设置手动强制开启
            userManualMute = false;
            userManualForceActive = true;
            setMicrophoneActive(true, true);
        }
    }

    public synchronized void setMicrophoneActive(boolean active, boolean userInitiated) {
        if (!userInitiated) {
            if (userManualForceActive) {
                // 用户在手机上主动开启了，保持开启，不受 Mac 空闲轮询影响
                return;
            }
            if (userManualMute && active) {
                // 用户主动手动静音闭麦中，抑制后台被动开启请求
                return;
            }
            if (!active) {
                // Mac 端无录音需求，复位手动静音标记，以便下次 Mac 唤醒能正常触发
                userManualMute = false;
            }
        }

        if (active == audioStreamer.isRecording()) return;

        if (active) {
            boolean ok = audioStreamer.start();
            if (ok) {
                btnMic.setBackgroundResource(R.drawable.bg_mic_capsule_active);
                tvMicDot.setText("🔴");
                tvMicText.setText("MIC LIVE");
                tvMicText.setTextColor(Color.parseColor("#00f59b"));

                AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.25f);
                pulse.setDuration(550);
                pulse.setRepeatMode(Animation.REVERSE);
                pulse.setRepeatCount(Animation.INFINITE);
                tvMicDot.startAnimation(pulse);

                if (micWaveView != null) {
                    micWaveView.setVisibility(View.VISIBLE);
                    micWaveView.setRunning(true);
                }
                if (userInitiated) {
                    Toast.makeText(this, "麦克风已连接 Mac 系统输入", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            audioStreamer.stop();
            tvMicDot.clearAnimation();
            btnMic.setBackgroundResource(R.drawable.bg_mic_capsule_idle);
            tvMicDot.setText("🎙️");
            tvMicText.setText("MIC OFF");
            tvMicText.setTextColor(Color.parseColor("#506070"));
            if (micWaveView != null) {
                micWaveView.setRunning(false);
                micWaveView.setVisibility(View.GONE);
            }
            if (userInitiated) {
                Toast.makeText(this, "麦克风已关闭", Toast.LENGTH_SHORT).show();
            }
        }
        applyImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statsClient != null) {
            statsClient.stop();
        }
        if (audioStreamer != null) {
            audioStreamer.stop();
        }
    }

    // ============================================================
    // 🌟 双屏切换与全景气象穿衣智库核心控制
    // ============================================================

    private void showWeatherScreen() {
        if (currentScreenIndex == 1 || screenFlipper == null) return;
        currentScreenIndex = 1;
        screenFlipper.setInAnimation(this, R.anim.slide_in_right);
        screenFlipper.setOutAnimation(this, R.anim.slide_out_left);
        screenFlipper.setDisplayedChild(1);
        if (weatherParticleView != null) {
            weatherParticleView.startAnimation();
        }
        updateIndoorSensorText();
        writeTelemetryFile();
    }

    private void showDashboardScreen() {
        if (currentScreenIndex == 0 || screenFlipper == null) return;
        currentScreenIndex = 0;
        screenFlipper.setInAnimation(this, R.anim.slide_in_left);
        screenFlipper.setOutAnimation(this, R.anim.slide_out_right);
        screenFlipper.setDisplayedChild(0);
        if (weatherParticleView != null) {
            weatherParticleView.stopAnimation(); // 切换回硬件屏时自动停止粒子重绘，杜绝发热
        }
        writeTelemetryFile();
    }

    private void updateIndoorSensorText() {
        if (tvWeatherIndoorSensor != null) {
            String tempStr = (lastAmbientTemp > 0) ? String.format(Locale.getDefault(), "%.1f°C", lastAmbientTemp) : "25.4°C";
            String humStr = (lastHumidity > 0) ? String.format(Locale.getDefault(), "%.0f%% RH", lastHumidity) : "80% RH";
            String pressStr = (lastPressure > 0) ? String.format(Locale.getDefault(), "%.1f hPa", lastPressure) : "1014.4 hPa";
            tvWeatherIndoorSensor.setText(String.format("🌡️ 工位室内环境: %s · %s · %s (Note 3 原生传感器)", tempStr, humStr, pressStr));
        }
        writeTelemetryFile();
    }

    private void writeTelemetryFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    org.json.JSONObject jo = new org.json.JSONObject();
                    jo.put("screenIndex", currentScreenIndex);
                    jo.put("ambientTemp", lastAmbientTemp > 0 ? lastAmbientTemp : 25.1f);
                    jo.put("humidity", lastHumidity > 0 ? lastHumidity : 76.0f);
                    jo.put("pressure", lastPressure > 0 ? lastPressure : 1014.6f);
                    jo.put("lux", currentLux);
                    jo.put("timestamp", System.currentTimeMillis());
                    if (currentWeatherData != null) {
                        jo.put("city", currentWeatherData.city);
                        jo.put("currentTemp", currentWeatherData.currentTemp);
                        jo.put("weatherDesc", currentWeatherData.currentWeatherDesc);
                        jo.put("rainProb", currentWeatherData.rainProbability);
                    }
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir != null) {
                        java.io.File f = new java.io.File(dir, "deskpilot_telemetry.json");
                        java.io.FileWriter fw = new java.io.FileWriter(f);
                        fw.write(jo.toString());
                        fw.close();
                    }
                } catch (Exception e) {
                    android.util.Log.e("DeskPilot", "writeTelemetryFile error", e);
                }
            }
        }).start();
    }

    private void updateWeatherScreenViews(final WeatherService.WeatherData data) {
        if (data == null) return;
        currentWeatherData = data;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvWeatherCity != null) tvWeatherCity.setText("📍 " + data.city);
                if (tvWeatherRainProbBadge != null) tvWeatherRainProbBadge.setText("🌧️ 降水 " + data.rainProbability + "%");
                if (tvWeatherBigTemp != null) tvWeatherBigTemp.setText(String.format(Locale.getDefault(), "%.0f°", data.currentTemp));
                if (tvWeatherIcon != null) tvWeatherIcon.setText(WeatherService.wmoCodeToIcon(data.currentWeatherCode));
                if (tvWeatherDesc != null) tvWeatherDesc.setText(data.currentWeatherDesc);
                if (tvWeatherFeelsLike != null) {
                    tvWeatherFeelsLike.setText(String.format(Locale.getDefault(), "体感温度 %.1f°C · %s", data.feelsLike, data.currentTemp > 24 ? "温和微风" : "清爽舒适"));
                }
                if (tvWeatherTodayMinMax != null) {
                    tvWeatherTodayMinMax.setText(String.format(Locale.getDefault(), "%.0f°C ~ %.0f°C", data.todayMin, data.todayMax));
                }
                if (tvWeatherWind != null) {
                    tvWeatherWind.setText(String.format(Locale.getDefault(), "%d级 · %.0fkm/h", data.windLevel, data.windSpeed));
                }
                if (tvWeatherRainProb != null) tvWeatherRainProb.setText(data.rainProbability + "%");
                if (weatherParticleView != null) {
                    weatherParticleView.setWeatherCode(data.currentWeatherCode);
                }

                // 穿衣决策
                if (tvClothingWarningTag != null) {
                    tvClothingWarningTag.setText(data.tempDiffWarning);
                    if (data.isTempDiffHigh) {
                        tvClothingWarningTag.setTextColor(Color.parseColor("#ffaa00"));
                        tvClothingWarningTag.setBackgroundColor(Color.parseColor("#2a1f0a"));
                    } else {
                        tvClothingWarningTag.setTextColor(Color.parseColor("#00f59b"));
                        tvClothingWarningTag.setBackgroundColor(Color.parseColor("#0a2a1c"));
                    }
                }
                if (tvClothingMain != null) tvClothingMain.setText(data.clothingMain);
                if (tvClothingSub != null) tvClothingSub.setText(data.clothingSub);

                // 4大生活指数
                if (tvIndexUmbrella != null) tvIndexUmbrella.setText(data.umbrellaIndex);
                if (tvIndexSport != null) tvIndexSport.setText(data.sportIndex);
                if (tvIndexCarWash != null) tvIndexCarWash.setText(data.carWashIndex);
                if (tvIndexCold != null) tvIndexCold.setText(data.coldIndex);

                // 明日天气预报 (深度呈现，解决用户反馈)
                if (tvTomorrowBigTemp != null) {
                    tvTomorrowBigTemp.setText(String.format(Locale.getDefault(), "%.0f°", data.tomorrowMax));
                }
                if (tvTomorrowIcon != null) {
                    tvTomorrowIcon.setText(WeatherService.wmoCodeToIcon(data.tomorrowWeatherCode));
                }
                if (tvTomorrowDesc != null) {
                    tvTomorrowDesc.setText(data.tomorrowWeatherDesc);
                }
                if (tvTomorrowRainBadge != null) {
                    tvTomorrowRainBadge.setText("☀️ 降水 " + data.tomorrowRainProb + "%");
                }
                if (tvTomorrowContrast != null) {
                    tvTomorrowContrast.setText(data.tomorrowDiffDesc);
                }
                if (tvTomorrowMinMax != null) {
                    tvTomorrowMinMax.setText(String.format(Locale.getDefault(), "%.0f°C ~ %.0f°C", data.tomorrowMin, data.tomorrowMax));
                }
                if (tvTomorrowTempDiff != null) {
                    tvTomorrowTempDiff.setText(String.format(Locale.getDefault(), "%.0f°C (%s)", data.tomorrowTempDiff, data.tomorrowTempDiff >= 8 ? "温差大" : "温差适中"));
                }
                if (tvTomorrowAdvice != null) {
                    tvTomorrowAdvice.setText(data.tomorrowAdvice);
                }
                updateIndoorSensorText();
                writeTelemetryFile(); // 动态粒子特效控制
                if (weatherParticleView != null) {
                    weatherParticleView.setWeatherCode(data.currentWeatherCode);
                }

                updateIndoorSensorText();
            }
        });
    }
}
