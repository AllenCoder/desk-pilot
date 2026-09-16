package com.antigravity.machud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * WeatherService - DeskPilot 极客全景气象与穿衣生活决策服务
 * 特性：自动 IP 精准定位 (如杭州市)、全球高精度天气预报、双日全景对比、智能穿衣算法
 */
public class WeatherService {
    private static final String TAG = "WeatherService";
    private static final String PREF_NAME = "DeskPilot_Weather";
    private static final String KEY_CACHED_JSON = "cached_weather_json";
    private static final String KEY_LAST_FETCH = "last_fetch_timestamp";

    public static class WeatherData {
        public String city = "杭州市";
        public double currentTemp = 28.7;
        public double feelsLike = 28.5;
        public int currentWeatherCode = 3;
        public String currentWeatherDesc = "局部多云";
        public double windSpeed = 8.5; // km/h
        public int windLevel = 2;
        public int rainProbability = 0; // %
        public double humidity = 72.0;

        // 今日
        public double todayMin = 21.6;
        public double todayMax = 30.3;

        // 明日全景
        public double tomorrowTemp = 30.5;
        public double tomorrowMin = 20.4;
        public double tomorrowMax = 30.5;
        public int tomorrowWeatherCode = 3;
        public String tomorrowWeatherDesc = "大部晴朗";
        public int tomorrowRainProb = 0;
        public double tomorrowTempDiff = 10.1;
        public String tomorrowDiffDesc = "气温平稳 · 比今天微升 0.5°C";
        public String tomorrowAdvice = "短袖出行 · 紫外线较强注意防晒";

        // 今日穿衣决策
        public String clothingMain = "透气短袖 · 纯棉T恤搭配薄长裤";
        public String clothingSub = "日照充裕，午间注意防晒；室内空调房备轻薄开衫。";
        public String tempDiffWarning = "温差适中";
        public boolean isTempDiffHigh = false;

        // 生活指数
        public String umbrellaIndex = "无需带伞 (0%)";
        public String sportIndex = "极佳适宜 (傍晚慢跑舒适)";
        public String carWashIndex = "适宜洗车 (近期持续晴好)";
        public String coldIndex = "少发 (气温平稳)";
    }

    public interface OnWeatherUpdatedListener {
        void onWeatherUpdated(WeatherData data);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnWeatherUpdatedListener listener;
    private boolean isFetching = false;

    // 默认杭州市经纬度 (可通过 IP 定位自动更新，或由用户手动指定输入)
    private double latitude = 30.2936;
    private double longitude = 120.1614;
    private String cityName = "杭州市";
    private boolean isManualCity = false;

    public WeatherService(Context context) {
        this.context = context.getApplicationContext();
        loadLocationPreference();
    }

    private void loadLocationPreference() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.isManualCity = sp.getBoolean("is_manual_city", false);
        this.cityName = sp.getString("city_name", "杭州市");
        this.latitude = Double.parseDouble(sp.getString("latitude", "30.2936"));
        this.longitude = Double.parseDouble(sp.getString("longitude", "120.1614"));
    }

    public void setListener(OnWeatherUpdatedListener listener) {
        this.listener = listener;
    }

    public void loadCachedOrFetch() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cached = sp.getString(KEY_CACHED_JSON, null);
        if (cached != null) {
            try {
                WeatherData data = parseWeatherData(cached);
                if (listener != null) {
                    listener.onWeatherUpdated(data);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse cached weather: " + e.getMessage());
            }
        }

        long lastFetch = sp.getLong(KEY_LAST_FETCH, 0);
        long now = System.currentTimeMillis();
        // 超过 15 分钟则刷新
        if (now - lastFetch > 15 * 60 * 1000L || cached == null) {
            fetchWeatherAsync(false);
        }
    }

    public void fetchWeatherAsync(final boolean force) {
        if (isFetching) return;
        isFetching = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 自动执行 IP 真实定位 (如果尚未锁定城市或强制刷新)
                    detectRealLocation();

                    // 2. 请求 Open-Meteo 获取高精度当前与未来天气
                    String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                            + "&longitude=" + longitude
                            + "&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                            + "&timezone=auto";

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            sb.append(line);
                        }
                        in.close();

                        final String response = sb.toString();
                        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        sp.edit().putString(KEY_CACHED_JSON, response)
                                 .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                                 .putString("city_name", cityName)
                                 .putString("latitude", String.valueOf(latitude))
                                 .putString("longitude", String.valueOf(longitude))
                                 .apply();

                        final WeatherData data = parseWeatherData(response);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onWeatherUpdated(data);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Fetch weather failed: " + e.getMessage());
                } finally {
                    isFetching = false;
                }
            }
        }, "DeskPilot-WeatherFetch").start();
    }

    /**
     * 自动通过真实 IP 获取所在省市并解析地理坐标 (若用户已指定城市则跳过)
     */
    private void detectRealLocation() {
        if (isManualCity) {
            return;
        }
        try {
            URL ipUrl = new URL("https://whois.pconline.com.cn/ipJson.jsp?json=true");
            HttpURLConnection ipConn = (HttpURLConnection) ipUrl.openConnection();
            ipConn.setConnectTimeout(4000);
            ipConn.setReadTimeout(4000);
            ipConn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (ipConn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(ipConn.getInputStream(), "GBK"));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close();

                JSONObject ipJson = new JSONObject(sb.toString().trim());
                String detectedCity = ipJson.optString("city", "");
                String detectedPro = ipJson.optString("pro", "");

                if (!detectedCity.isEmpty()) {
                    this.cityName = detectedCity;
                    // 对常见重点城市直接映射秒出，兼顾地理编码 API
                    if (detectedCity.contains("杭州")) {
                        this.latitude = 30.2936;
                        this.longitude = 120.1614;
                    } else if (detectedCity.contains("北京")) {
                        this.latitude = 39.9042;
                        this.longitude = 116.4074;
                    } else if (detectedCity.contains("上海")) {
                        this.latitude = 31.2304;
                        this.longitude = 121.4737;
                    } else if (detectedCity.contains("深圳")) {
                        this.latitude = 22.5431;
                        this.longitude = 114.0579;
                    } else if (detectedCity.contains("广州")) {
                        this.latitude = 23.1291;
                        this.longitude = 113.2644;
                    } else {
                        // 动态查询 Open-Meteo Geocoding
                        String queryName = detectedCity.replace("市", "");
                        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                                + URLEncoder.encode(queryName, "UTF-8") + "&count=1&language=zh";
                        URL gUrl = new URL(geoUrl);
                        HttpURLConnection gConn = (HttpURLConnection) gUrl.openConnection();
                        gConn.setConnectTimeout(3000);
                        gConn.setReadTimeout(3000);
                        if (gConn.getResponseCode() == 200) {
                            BufferedReader gr = new BufferedReader(new InputStreamReader(gConn.getInputStream()));
                            StringBuilder gsb = new StringBuilder();
                            while ((l = gr.readLine()) != null) gsb.append(l);
                            gr.close();
                            JSONObject gRoot = new JSONObject(gsb.toString());
                            JSONArray results = gRoot.optJSONArray("results");
                            if (results != null && results.length() > 0) {
                                JSONObject first = results.getJSONObject(0);
                                this.latitude = first.optDouble("latitude", this.latitude);
                                this.longitude = first.optDouble("longitude", this.longitude);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Detect real location failed: " + e.getMessage());
        }
    }

    private WeatherData parseWeatherData(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        WeatherData data = new WeatherData();
        data.city = this.cityName;

        if (root.has("current_weather")) {
            JSONObject cw = root.getJSONObject("current_weather");
            data.currentTemp = cw.optDouble("temperature", 28.5);
            data.currentWeatherCode = cw.optInt("weathercode", 3);
            data.currentWeatherDesc = wmoCodeToDesc(data.currentWeatherCode);
            data.windSpeed = cw.optDouble("windspeed", 8.5);
            data.windLevel = kmhToWindScale(data.windSpeed);
        }

        if (root.has("daily")) {
            JSONObject daily = root.getJSONObject("daily");
            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
            JSONArray codeArr = daily.optJSONArray("weathercode");
            JSONArray rainArr = daily.optJSONArray("precipitation_probability_max");

            if (maxArr != null && maxArr.length() > 0) {
                data.todayMax = maxArr.optDouble(0, data.currentTemp + 2);
            }
            if (minArr != null && minArr.length() > 0) {
                data.todayMin = minArr.optDouble(0, data.currentTemp - 6);
            }
            if (rainArr != null && rainArr.length() > 0) {
                data.rainProbability = rainArr.optInt(0, 0);
            }

            // 明天数据
            if (maxArr != null && maxArr.length() > 1) {
                data.tomorrowMax = maxArr.optDouble(1, data.todayMax);
                data.tomorrowTemp = data.tomorrowMax;
            }
            if (minArr != null && minArr.length() > 1) {
                data.tomorrowMin = minArr.optDouble(1, data.todayMin);
            }
            if (codeArr != null && codeArr.length() > 1) {
                data.tomorrowWeatherCode = codeArr.optInt(1, 0);
                data.tomorrowWeatherDesc = wmoCodeToDesc(data.tomorrowWeatherCode);
            }
            if (rainArr != null && rainArr.length() > 1) {
                data.tomorrowRainProb = rainArr.optInt(1, 0);
            }
        }

        // 体感温度计算
        data.feelsLike = Math.round((data.currentTemp - (data.windSpeed * 0.12)) * 10.0) / 10.0;

        // 明日温差与对比计算
        data.tomorrowTempDiff = Math.round((data.tomorrowMax - data.tomorrowMin) * 10.0) / 10.0;
        double diff = data.tomorrowMax - data.todayMax;
        if (diff <= -3.0) {
            data.tomorrowDiffDesc = "比今天骤降 " + String.format("%.0f", Math.abs(diff)) + "°C · 早晚需添衣";
        } else if (diff >= 3.0) {
            data.tomorrowDiffDesc = "比今天升温 " + String.format("%.0f", diff) + "°C · 气温偏热";
        } else {
            data.tomorrowDiffDesc = "气温基本平稳 (升降幅约 " + String.format("%.1f", Math.abs(diff)) + "°C)";
        }

        if (data.tomorrowRainProb >= 50) {
            data.tomorrowAdvice = "有明显降水 · 明日出门备雨具";
        } else if (data.tomorrowMax >= 30.0) {
            data.tomorrowAdvice = "日照充裕紫外线较强 · 建议短袖出行防晒";
        } else {
            data.tomorrowAdvice = "适宜短袖与薄外套 · 气候宜人";
        }

        // 计算今日穿衣建议与生活指数
        calculateClothingAdvice(data);
        calculateLifestyleIndices(data);

        return data;
    }

    private void calculateClothingAdvice(WeatherData data) {
        double maxT = data.todayMax;
        double minT = data.todayMin;
        double diff = maxT - minT;

        if (diff >= 8.0) {
            data.isTempDiffHigh = true;
            data.tempDiffWarning = "⚠️ 早晚温差 " + String.format("%.0f", diff) + "°C";
        } else {
            data.isTempDiffHigh = false;
            data.tempDiffWarning = "温差适中 (" + String.format("%.0f", diff) + "°C)";
        }

        if (maxT >= 28.0) {
            data.clothingMain = "清凉短袖 · 纯棉T恤搭配透气休闲裤";
            data.clothingSub = "气温温热，日照充裕注意防晒；室内空调房备轻薄开衫。";
        } else if (maxT >= 23.0) {
            data.clothingMain = "透气短袖 · 薄款休闲长裤";
            data.clothingSub = "气候舒适宜人，早晚微风，适宜轻便单层着装。";
        } else if (maxT >= 18.0) {
            data.clothingMain = "长袖衬衫 · 连帽卫衣搭配牛仔裤";
            data.clothingSub = "早晚微凉，建议备一件薄外套，适合双层随心叠穿。";
        } else if (maxT >= 13.0) {
            data.clothingMain = "针织毛衣 · 风衣外套搭配休闲长裤";
            data.clothingSub = "早晚体感微凉，注意防风保暖。";
        } else {
            data.clothingMain = "厚大衣 · 加厚卫衣内搭保暖内衣";
            data.clothingSub = "寒意显著，注意添衣防寒。";
        }
    }

    private void calculateLifestyleIndices(WeatherData data) {
        // 1. 带伞指数
        if (data.rainProbability >= 50 || data.currentWeatherDesc.contains("雨") || data.currentWeatherDesc.contains("雪")) {
            data.umbrellaIndex = "⚠️ 必带雨具 (" + data.rainProbability + "%)";
        } else if (data.rainProbability >= 20) {
            data.umbrellaIndex = "建议备伞 (" + data.rainProbability + "%)";
        } else {
            data.umbrellaIndex = "无需带伞 (" + data.rainProbability + "%)";
        }

        // 2. 运动指数
        if (data.rainProbability >= 40 || data.windLevel >= 5) {
            data.sportIndex = "室内健身 (避开风雨)";
        } else if (data.currentTemp >= 31.0) {
            data.sportIndex = "傍晚适宜 (避开午间暴晒)";
        } else {
            data.sportIndex = "极佳适宜 (慢跑/骑行极舒适)";
        }

        // 3. 洗车指数
        if (data.rainProbability >= 30 || data.tomorrowRainProb >= 30) {
            data.carWashIndex = "不宜洗车 (近期有降水)";
        } else {
            data.carWashIndex = "适宜洗车 (近期天气晴好)";
        }

        // 4. 感冒指数
        double diff = data.tomorrowMax - data.todayMax;
        if (diff <= -4.0 || data.isTempDiffHigh) {
            data.coldIndex = "较易发 (温差较大)";
        } else {
            data.coldIndex = "少发 (气温平稳)";
        }
    }

    public static String wmoCodeToDesc(int code) {
        switch (code) {
            case 0: return "晴朗少云";
            case 1: return "大部晴朗";
            case 2: return "局部多云";
            case 3: return "阴天多云";
            case 45: case 48: return "有雾";
            case 51: return "毛毛细雨";
            case 53: return "中度细雨";
            case 55: return "密集细雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "冰雹霰粒";
            case 80: return "阵雨";
            case 81: return "中阵雨";
            case 82: return "强阵雨";
            case 85: case 86: return "阵雪";
            case 95: return "雷暴雨";
            case 96: case 99: return "强雷暴伴冰雹";
            default: return "局部多云";
        }
    }

    public static String wmoCodeToIcon(int code) {
        switch (code) {
            case 0: return "☀️";
            case 1: return "🌤️";
            case 2: return "⛅";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55: case 61: return "🌧️";
            case 63: case 65: case 80: case 81: case 82: return "🌧️";
            case 66: case 67: return "🌨️";
            case 71: case 73: case 75: case 77: case 85: case 86: return "❄️";
            case 95: case 96: case 99: return "⛈️";
            default: return "🌤️";
        }
    }

    private static int kmhToWindScale(double kmh) {
        double ms = kmh / 3.6;
        if (ms < 0.3) return 0;
        if (ms < 1.6) return 1;
        if (ms < 3.4) return 2;
        if (ms < 5.5) return 3;
        if (ms < 8.0) return 4;
        if (ms < 10.8) return 5;
        if (ms < 13.9) return 6;
        return 7;
    }

    public String getCityName() {
        return cityName;
    }

    public boolean isManualCity() {
        return isManualCity;
    }

    public void setCustomCity(final String name) {
        if (name == null || name.trim().isEmpty()) return;
        final String cleanName = name.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                isManualCity = true;
                cityName = cleanName;
                double lat = 30.2936, lon = 120.1614;
                if (cleanName.contains("北京")) {
                    lat = 39.9042; lon = 116.4074;
                } else if (cleanName.contains("杭州")) {
                    lat = 30.2936; lon = 120.1614;
                } else if (cleanName.contains("上海")) {
                    lat = 31.2304; lon = 121.4737;
                } else if (cleanName.contains("深圳")) {
                    lat = 22.5431; lon = 114.0579;
                } else if (cleanName.contains("广州")) {
                    lat = 23.1291; lon = 113.2644;
                } else if (cleanName.contains("成都")) {
                    lat = 30.5728; lon = 104.0668;
                } else if (cleanName.contains("武汉")) {
                    lat = 30.5928; lon = 114.3055;
                } else if (cleanName.contains("南京")) {
                    lat = 32.0603; lon = 118.7969;
                } else if (cleanName.contains("西安")) {
                    lat = 34.3416; lon = 108.9398;
                } else if (cleanName.contains("重庆")) {
                    lat = 29.5630; lon = 106.5516;
                } else {
                    try {
                        String enc = URLEncoder.encode(cleanName, "UTF-8");
                        URL geoUrl = new URL("https://geocoding-api.open-meteo.com/v1/search?name=" + enc + "&count=1&language=zh&format=json");
                        HttpURLConnection geoConn = (HttpURLConnection) geoUrl.openConnection();
                        geoConn.setConnectTimeout(4000);
                        geoConn.setReadTimeout(4000);
                        if (geoConn.getResponseCode() == 200) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(geoConn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            br.close();
                            JSONObject geoJson = new JSONObject(sb.toString());
                            JSONArray results = geoJson.optJSONArray("results");
                            if (results != null && results.length() > 0) {
                                JSONObject first = results.getJSONObject(0);
                                lat = first.optDouble("latitude", lat);
                                lon = first.optDouble("longitude", lon);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                latitude = lat;
                longitude = lon;

                SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                sp.edit()
                    .putBoolean("is_manual_city", true)
                    .putString("city_name", cityName)
                    .putString("latitude", String.valueOf(latitude))
                    .putString("longitude", String.valueOf(longitude))
                    .apply();

                fetchWeatherAsync(true);
            }
        }, "DeskPilot-SetCustomCity").start();
    }

    public void setAutoDetectCity() {
        isManualCity = false;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean("is_manual_city", false).apply();
        fetchWeatherAsync(true);
    }
}
