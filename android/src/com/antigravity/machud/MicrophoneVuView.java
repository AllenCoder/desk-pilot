package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * 🌟 ESP32-C3 AlphaPi 原生麦克风音频动态 VU 表 & 实时波形频谱
 * 纯原生 Canvas 硬件加速渲染，支持动态音量电平衰减与极客绿/黄/红三色阶梯指示
 */
public class MicrophoneVuView extends View {

    private static final int SEGMENT_COUNT = 16;
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segRect = new RectF();
    private final Path wavePath = new Path();

    private float currentVol = 0f;
    private float targetVol = 0f;
    private float peakVol = 0f;
    private long lastPeakTime = 0;

    // 波形历史队列
    private final float[] history = new float[40];
    private int historyIdx = 0;

    public MicrophoneVuView(Context context) {
        super(context);
        init();
    }

    public MicrophoneVuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        segmentPaint.setStyle(Paint.Style.FILL);
        bgSegmentPaint.setStyle(Paint.Style.FILL);
        bgSegmentPaint.setColor(Color.parseColor("#121f2d"));

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(2.5f);
        wavePaint.setColor(Color.parseColor("#00f59b"));

        textPaint.setColor(Color.parseColor("#94a3b8"));
        textPaint.setTextSize(13f);
        textPaint.setFakeBoldText(true);
    }

    public void updateVolume(int rawVol) {
        // 原始音量通常在 0 ~ 50 之间
        this.targetVol = Math.max(0, Math.min(100, rawVol * 2.2f));
        if (targetVol > peakVol) {
            peakVol = targetVol;
            lastPeakTime = System.currentTimeMillis();
        }

        // 记入波形历史
        history[historyIdx] = targetVol;
        historyIdx = (historyIdx + 1) % history.length;

        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 平滑插值与阻尼下落
        if (currentVol < targetVol) {
            currentVol = currentVol * 0.5f + targetVol * 0.5f;
        } else {
            currentVol = Math.max(0f, currentVol - 1.8f);
        }

        // 峰值慢速衰减 (Peak Hold)
        if (System.currentTimeMillis() - lastPeakTime > 600) {
            peakVol = Math.max(0f, peakVol - 1.2f);
        }

        // 1. 上半部分：实时滚动声波曲线
        float waveH = h * 0.45f;
        wavePath.reset();
        float stepX = (float) w / (history.length - 1);
        for (int i = 0; i < history.length; i++) {
            int idx = (historyIdx + i) % history.length;
            float amp = history[idx] / 100f; // 0 ~ 1
            float x = i * stepX;
            float y = (waveH / 2f) + (float) Math.sin(i * 0.5f + System.currentTimeMillis() * 0.005) * (amp * waveH * 0.45f);
            if (i == 0) {
                wavePath.moveTo(x, y);
            } else {
                wavePath.lineTo(x, y);
            }
        }
        canvas.drawPath(wavePath, wavePaint);

        // 基准中线
        wavePaint.setStrokeWidth(1f);
        wavePaint.setColor(Color.parseColor("#1a365d"));
        canvas.drawLine(0, waveH / 2f, w, waveH / 2f, wavePaint);
        wavePaint.setStrokeWidth(2.5f);
        wavePaint.setColor(Color.parseColor("#00f59b"));

        // 2. 下半部分：16 阶梯 VU Meter 柱状电平表
        float vuTop = waveH + 8f;
        float vuHeight = 16f;
        float gap = 3f;
        float segWidth = (w - (SEGMENT_COUNT - 1) * gap) / SEGMENT_COUNT;

        int litCount = (int) Math.round((currentVol / 100f) * SEGMENT_COUNT);
        int peakLitIdx = (int) Math.round((peakVol / 100f) * SEGMENT_COUNT) - 1;

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float x = i * (segWidth + gap);
            segRect.set(x, vuTop, x + segWidth, vuTop + vuHeight);

            // 背景底槽
            canvas.drawRoundRect(segRect, 3f, 3f, bgSegmentPaint);

            if (i < litCount) {
                // 三色渐变梯级：绿 -> 黄 -> 极客红
                if (i < 9) {
                    segmentPaint.setColor(Color.parseColor("#00f59b")); // 正常绿
                } else if (i < 13) {
                    segmentPaint.setColor(Color.parseColor("#f59e0b")); // 警告黄
                } else {
                    segmentPaint.setColor(Color.parseColor("#ef4444")); // 过载红
                }
                canvas.drawRoundRect(segRect, 3f, 3f, segmentPaint);
            }

            // 峰值驻留线 (Peak Hold Marker)
            if (i == peakLitIdx) {
                segmentPaint.setColor(Color.WHITE);
                canvas.drawRoundRect(segRect, 3f, 3f, segmentPaint);
            }
        }

        // 3. 底部文字数值 (实时音量 / 动态峰值)
        float textY = h - 6f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.parseColor("#94a3b8"));
        canvas.drawText("声级: " + (int) currentVol + " VU", 0, textY, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        String statusDesc = currentVol > 70 ? "🔊 强音捕捉" : (currentVol > 20 ? "🎙️ 环境拾音" : "🤫 安静环境");
        textPaint.setColor(currentVol > 70 ? Color.parseColor("#ef4444") : Color.parseColor("#00f59b"));
        canvas.drawText(statusDesc + " (峰值 " + (int) peakVol + "%)", w, textY, textPaint);
    }
}
