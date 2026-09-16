package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 🌟 ESP32-C3 AlphaPi 14 颗板载 WS2812 RGB LED 原生灯带实时仿真与交互视窗
 * 支持彩虹流动灯效、拾音律动预览、单色高亮与光晕辉光渲染
 */
public class LedStripView extends View {

    private static final int LED_COUNT = 14;
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dotRect = new RectF();

    private String mode = "rainbow"; // "rainbow", "color", "vol_mode", "off"
    private int solidColor = Color.parseColor("#00f59b");
    private float brightness = 0.5f;
    private int currentVol = 0;
    private float rainbowStep = 0f;

    public LedStripView(Context context) {
        super(context);
        init();
    }

    public LedStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ledPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setColor(Color.parseColor("#1e293b"));
    }

    public void setLedState(String mode, int color, float brightness) {
        this.mode = mode;
        this.solidColor = color;
        this.brightness = Math.max(0.05f, Math.min(1.0f, brightness));
        postInvalidate();
    }

    public void updateVolPreview(int vol) {
        this.currentVol = vol;
        if ("vol_mode".equals(mode)) {
            postInvalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cy = h / 2f;
        float spacing = (float) w / (LED_COUNT + 1);
        float radius = Math.min(spacing * 0.42f, h * 0.38f);

        rainbowStep = (rainbowStep + 3f) % 256f;

        for (int i = 0; i < LED_COUNT; i++) {
            float cx = (i + 1) * spacing;
            int dotColor = getLedColor(i);

            // 1. 底层微弱插槽边框
            canvas.drawCircle(cx, cy, radius + 2f, borderPaint);

            // 2. 辉光光晕 (Glow)
            glowPaint.setColor(Color.argb((int) (50 * brightness), Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor)));
            canvas.drawCircle(cx, cy, radius * 1.6f, glowPaint);

            // 3. 核心发光 LED 珠
            ledPaint.setColor(dotColor);
            canvas.drawCircle(cx, cy, radius, ledPaint);

            // 4. 中心高光亮点
            ledPaint.setColor(Color.argb(120, 255, 255, 255));
            canvas.drawCircle(cx - radius * 0.25f, cy - radius * 0.25f, radius * 0.35f, ledPaint);
        }

        if ("rainbow".equals(mode)) {
            postInvalidateDelayed(35);
        }
    }

    private int getLedColor(int index) {
        if ("off".equals(mode)) {
            return Color.parseColor("#151d28");
        } else if ("color".equals(mode)) {
            int r = (int) (Color.red(solidColor) * brightness);
            int g = (int) (Color.green(solidColor) * brightness);
            int b = (int) (Color.blue(solidColor) * brightness);
            return Color.rgb(r, g, b);
        } else if ("vol_mode".equals(mode)) {
            int litThreshold = (int) Math.round(currentVol * 0.7);
            if (index < litThreshold) {
                if (index < 7) {
                    return Color.rgb(0, (int) (220 * brightness), 0);
                } else if (index < 11) {
                    return Color.rgb((int) (220 * brightness), (int) (180 * brightness), 0);
                } else {
                    return Color.rgb((int) (255 * brightness), 0, 0);
                }
            } else {
                return Color.parseColor("#151d28");
            }
        } else {
            // 彩虹流动轮询 (wheel算法)
            float pos = (index * 18 + rainbowStep) % 255f;
            return colorWheel(pos);
        }
    }

    private int colorWheel(float pos) {
        float r, g, b;
        if (pos < 85) {
            r = pos * 3;
            g = 255 - pos * 3;
            b = 0;
        } else if (pos < 170) {
            pos -= 85;
            r = 255 - pos * 3;
            g = 0;
            b = pos * 3;
        } else {
            pos -= 170;
            r = 0;
            g = pos * 3;
            b = 255 - pos * 3;
        }
        return Color.rgb((int) (r * brightness), (int) (g * brightness), (int) (b * brightness));
    }
}
