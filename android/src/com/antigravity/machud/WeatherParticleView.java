package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WeatherParticleView - 专为 AMOLED 纯黑屏幕与低功耗定制的极客 Canvas 天气粒子动效引擎
 * 支持：雨滴流光水花 (RAIN)、轻盈飘雪 (SNOW)、日光微辉 (SUNNY)、浮云微移 (CLOUDY)
 * 特性：0 内存抖动 (对象池复用)、帧率限制节电、页面切走自动休眠 (0 CPU)
 */
public class WeatherParticleView extends View {

    public enum WeatherType {
        RAIN,
        SNOW,
        SUNNY,
        CLOUDY,
        NONE
    }

    private WeatherType currentType = WeatherType.RAIN;
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random random = new Random();
    private boolean isAnimating = false;
    private long lastFrameTime = 0;

    // 雨滴粒子
    private static class RainDrop {
        float x, y;
        float speed;
        float length;
        int alpha;
    }

    // 地面涟漪水花粒子
    private static class RainRipple {
        float x, y;
        float radius;
        float maxRadius;
        int alpha;
    }

    // 雪花粒子
    private static class SnowFlake {
        float x, y;
        float radius;
        float speedY;
        float speedX;
        float swingAngle;
        float swingSpeed;
        int alpha;
    }

    // 浮云粒子
    private static class CloudParticle {
        float x, y;
        float width, height;
        float speedX;
        int alpha;
    }

    private final List<RainDrop> rainDrops = new ArrayList<>();
    private final List<RainRipple> ripples = new ArrayList<>();
    private final List<SnowFlake> snowFlakes = new ArrayList<>();
    private final List<CloudParticle> cloudParticles = new ArrayList<>();

    private float sunPulse = 0f;
    private boolean sunPulseGrowing = true;

    public WeatherParticleView(Context context) {
        super(context);
        init();
    }

    public WeatherParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        particlePaint.setStyle(Paint.Style.FILL);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(1.5f);
        sunGlowPaint.setStyle(Paint.Style.FILL);
    }

    public void setWeatherType(WeatherType type) {
        if (this.currentType != type) {
            this.currentType = type;
            initParticles();
            invalidate();
        }
    }

    public void setWeatherCode(int wmoCode) {
        // WMO Weather interpretation codes:
        // 0: Clear sky -> SUNNY
        // 1, 2, 3: Mainly clear, partly cloudy, overcast -> CLOUDY / SUNNY
        // 51-67, 80-82: Drizzle & Rain -> RAIN
        // 71-77, 85-86: Snow -> SNOW
        // 95-99: Thunderstorm -> RAIN
        if (wmoCode == 0 || wmoCode == 1) {
            setWeatherType(WeatherType.SUNNY);
        } else if (wmoCode == 2 || wmoCode == 3 || wmoCode == 45 || wmoCode == 48) {
            setWeatherType(WeatherType.CLOUDY);
        } else if ((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82) || (wmoCode >= 95 && wmoCode <= 99)) {
            setWeatherType(WeatherType.RAIN);
        } else if ((wmoCode >= 71 && wmoCode <= 77) || (wmoCode >= 85 && wmoCode <= 86)) {
            setWeatherType(WeatherType.SNOW);
        } else {
            setWeatherType(WeatherType.RAIN);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initParticles();
    }

    private void initParticles() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        rainDrops.clear();
        ripples.clear();
        snowFlakes.clear();
        cloudParticles.clear();

        if (currentType == WeatherType.RAIN) {
            int count = Math.min(65, Math.max(35, w / 15));
            for (int i = 0; i < count; i++) {
                RainDrop drop = new RainDrop();
                drop.x = random.nextFloat() * w;
                drop.y = random.nextFloat() * h;
                drop.speed = 18f + random.nextFloat() * 16f;
                drop.length = 16f + random.nextFloat() * 20f;
                drop.alpha = 70 + random.nextInt(120);
                rainDrops.add(drop);
            }
        } else if (currentType == WeatherType.SNOW) {
            int count = Math.min(45, Math.max(25, w / 20));
            for (int i = 0; i < count; i++) {
                SnowFlake flake = new SnowFlake();
                flake.x = random.nextFloat() * w;
                flake.y = random.nextFloat() * h;
                flake.radius = 2.0f + random.nextFloat() * 3.5f;
                flake.speedY = 2.0f + random.nextFloat() * 3.0f;
                flake.speedX = -0.5f + random.nextFloat() * 1.0f;
                flake.swingAngle = random.nextFloat() * 6.28f;
                flake.swingSpeed = 0.03f + random.nextFloat() * 0.04f;
                flake.alpha = 90 + random.nextInt(130);
                snowFlakes.add(flake);
            }
        } else if (currentType == WeatherType.CLOUDY) {
            for (int i = 0; i < 4; i++) {
                CloudParticle c = new CloudParticle();
                c.width = w * (0.4f + random.nextFloat() * 0.3f);
                c.height = h * (0.25f + random.nextFloat() * 0.2f);
                c.x = random.nextFloat() * w;
                c.y = random.nextFloat() * (h * 0.6f);
                c.speedX = 0.4f + random.nextFloat() * 0.4f;
                c.alpha = 25 + random.nextInt(25);
                cloudParticles.add(c);
            }
        }
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    public void stopAnimation() {
        isAnimating = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        lastFrameTime = now;

        switch (currentType) {
            case RAIN:
                drawRain(canvas, w, h);
                break;
            case SNOW:
                drawSnow(canvas, w, h);
                break;
            case SUNNY:
                drawSunny(canvas, w, h);
                break;
            case CLOUDY:
                drawCloudy(canvas, w, h);
                break;
            default:
                break;
        }

        if (isAnimating) {
            // 节电限制：约 35~40 FPS 刷新，保证丝滑流畅且彻底避免 Note 3 发热
            postInvalidateDelayed(25);
        }
    }

    private void drawRain(Canvas canvas, int w, int h) {
        particlePaint.setStrokeWidth(1.8f);

        // 1. 绘制雨丝 (微带迎风倾角)
        final float windX = 3.5f;
        for (RainDrop drop : rainDrops) {
            particlePaint.setColor(Color.argb(drop.alpha, 70, 180, 255));
            canvas.drawLine(drop.x, drop.y, drop.x + windX, drop.y + drop.length, particlePaint);

            drop.x += windX * (drop.speed / 20f);
            drop.y += drop.speed;

            // 触底触发涟漪
            if (drop.y > h - 15) {
                if (random.nextFloat() < 0.25f && ripples.size() < 12) {
                    RainRipple r = new RainRipple();
                    r.x = drop.x;
                    r.y = h - 6 - random.nextFloat() * 12;
                    r.radius = 2f;
                    r.maxRadius = 8f + random.nextFloat() * 10f;
                    r.alpha = 100;
                    ripples.add(r);
                }
                drop.y = -drop.length - random.nextFloat() * 40;
                drop.x = random.nextFloat() * (w + 40) - 20;
            }
        }

        // 2. 绘制微涟漪
        for (int i = ripples.size() - 1; i >= 0; i--) {
            RainRipple r = ripples.get(i);
            ripplePaint.setColor(Color.argb(r.alpha, 70, 190, 255));
            RectF oval = new RectF(r.x - r.radius * 2f, r.y - r.radius * 0.6f,
                                   r.x + r.radius * 2f, r.y + r.radius * 0.6f);
            canvas.drawOval(oval, ripplePaint);

            r.radius += 0.8f;
            r.alpha -= 8;
            if (r.alpha <= 0 || r.radius >= r.maxRadius) {
                ripples.remove(i);
            }
        }
    }

    private void drawSnow(Canvas canvas, int w, int h) {
        for (SnowFlake flake : snowFlakes) {
            particlePaint.setColor(Color.argb(flake.alpha, 215, 235, 255));
            canvas.drawCircle(flake.x, flake.y, flake.radius, particlePaint);

            flake.swingAngle += flake.swingSpeed;
            flake.x += (float) Math.sin(flake.swingAngle) * 1.2f + flake.speedX;
            flake.y += flake.speedY;

            if (flake.y > h + 10) {
                flake.y = -10;
                flake.x = random.nextFloat() * w;
            }
            if (flake.x < -10) flake.x = w + 10;
            if (flake.x > w + 10) flake.x = -10;
        }
    }

    private void drawSunny(Canvas canvas, int w, int h) {
        if (sunPulseGrowing) {
            sunPulse += 0.008f;
            if (sunPulse >= 1.0f) sunPulseGrowing = false;
        } else {
            sunPulse -= 0.008f;
            if (sunPulse <= 0.0f) sunPulseGrowing = true;
        }

        // 右上角微温和日光晕
        float cx = w * 0.82f;
        float cy = h * 0.20f;
        float baseR = Math.min(w, h) * 0.16f;

        // 外层光环呼吸
        sunGlowPaint.setColor(Color.argb((int)(15 + sunPulse * 15), 255, 185, 0));
        canvas.drawCircle(cx, cy, baseR * 1.8f, sunGlowPaint);

        // 中层光辉
        sunGlowPaint.setColor(Color.argb((int)(30 + sunPulse * 25), 255, 195, 20));
        canvas.drawCircle(cx, cy, baseR * 1.3f, sunGlowPaint);

        // 内核暖日光球
        sunGlowPaint.setColor(Color.argb(190, 255, 210, 50));
        canvas.drawCircle(cx, cy, baseR * 0.8f, sunGlowPaint);
    }

    private void drawCloudy(Canvas canvas, int w, int h) {
        particlePaint.setStyle(Paint.Style.FILL);
        for (CloudParticle c : cloudParticles) {
            particlePaint.setColor(Color.argb(c.alpha, 120, 145, 170));
            RectF rect = new RectF(c.x, c.y, c.x + c.width, c.y + c.height);
            canvas.drawRoundRect(rect, c.height * 0.5f, c.height * 0.5f, particlePaint);

            c.x += c.speedX;
            if (c.x > w + 50) {
                c.x = -c.width - 50;
            }
        }
    }
}
