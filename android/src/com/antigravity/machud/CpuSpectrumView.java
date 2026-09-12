package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * CpuSpectrumView
 * 专为 8 核 CPU 设计的立体音频均衡器 (Audio-Equalizer) 风格频谱柱状图。
 * 具备柔和动画平滑插值、动态三色梯度预警以及核心序号标注。
 */
public class CpuSpectrumView extends View {
    private static final int CORES_COUNT = 8;
    private final float[] targetLoads = new float[CORES_COUNT];
    private final float[] currentLoads = new float[CORES_COUNT];

    private final Paint barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF barRect = new RectF();

    public CpuSpectrumView(Context context) {
        super(context);
        init();
    }

    public CpuSpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barBgPaint.setStyle(Paint.Style.FILL);
        barBgPaint.setColor(Color.parseColor("#0d131a"));

        baseGlowPaint.setStyle(Paint.Style.FILL);
        baseGlowPaint.setColor(Color.parseColor("#0d2e20"));

        segmentPaint.setStyle(Paint.Style.STROKE);
        segmentPaint.setColor(Color.parseColor("#000000"));

        barPaint.setStyle(Paint.Style.FILL);

        peakPaint.setStyle(Paint.Style.FILL);
        peakPaint.setColor(Color.WHITE);

        textPaint.setColor(Color.parseColor("#506070"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void updateCores(double[] cores) {
        if (cores == null) return;
        int len = Math.min(cores.length, CORES_COUNT);
        for (int i = 0; i < len; i++) {
            targetLoads[i] = (float) Math.max(0, Math.min(100, cores[i]));
            // 平滑滤波快速逼近目标
            currentLoads[i] = currentLoads[i] * 0.3f + targetLoads[i] * 0.7f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 垂直分配: 顶部留一点空间，底部留 14dp 给 C1~C8 标注
        float density = getResources().getDisplayMetrics().density;
        float bottomLabelH = 14f * density;
        float barTop = 2f * density;
        float barBottom = h - bottomLabelH;
        float maxBarH = barBottom - barTop;

        float totalSpacing = 4f * density * (CORES_COUNT - 1);
        float barWidth = (w - totalSpacing) / CORES_COUNT;

        textPaint.setTextSize(7.5f * density);

        for (int i = 0; i < CORES_COUNT; i++) {
            float left = i * (barWidth + 4f * density);
            float right = left + barWidth;

            // 1. 绘制柱体底槽 (暗黑带微圆角)
            barRect.set(left, barTop, right, barBottom);
            canvas.drawRoundRect(barRect, 3f * density, 3f * density, barBgPaint);

            // 1.1 绘制待机底盘微绿基座 (即使 0% 也不空旷，维持机架设备开机待命质感)
            barRect.set(left, barBottom - 2.5f * density, right, barBottom);
            canvas.drawRoundRect(barRect, 1.5f * density, 1.5f * density, baseGlowPaint);

            // 2. 计算动态填充高度 (最低保底 2.5dp)
            float val = currentLoads[i];
            float fillH = Math.max(2.5f * density, (val / 100f) * maxBarH);
            float fillTop = barBottom - fillH;

            // 动态梯度着色: >75% 烈焰红, >35% 能量橙黄, <=35% 极客绿
            int color;
            if (val > 75f) {
                color = Color.parseColor("#ff4757");
            } else if (val > 35f) {
                color = Color.parseColor("#ffd166");
            } else {
                color = Color.parseColor("#00f59b");
            }
            barPaint.setColor(color);

            barRect.set(left, fillTop, right, barBottom);
            canvas.drawRoundRect(barRect, 3f * density, 3f * density, barPaint);

            // 柱顶高亮小白点峰值指示 (Cyber 均衡器质感，负载极低时不显示)
            if (val > 4f) {
                canvas.drawRect(left, fillTop, right, Math.min(barBottom, fillTop + 2f * density), peakPaint);
            }

            // 2.1 绘制硬件 LED 刻度切分线 (每 20% 刻度一条黑色细缝，塑造专业机柜工控表质感)
            segmentPaint.setStrokeWidth(1.2f * density);
            for (float pct = 0.2f; pct < 0.9f; pct += 0.2f) {
                float notchY = barBottom - pct * maxBarH;
                canvas.drawLine(left, notchY, right, notchY, segmentPaint);
            }

            // 3. 底部绘制核心标号 (C1 ~ C8)
            float cx = left + barWidth / 2f;
            canvas.drawText("C" + (i + 1), cx, h - 3f * density, textPaint);
        }
    }
}
