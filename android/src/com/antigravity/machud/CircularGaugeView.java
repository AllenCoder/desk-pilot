package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class CircularGaugeView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float progress = 0f;
    private int progressColor = Color.parseColor("#00f59b");
    private String subtitle = "NORMAL";

    public CircularGaugeView(Context context) {
        super(context);
        init();
    }

    public CircularGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(14f);
        trackPaint.setColor(Color.parseColor("#12171e"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(14f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(progressColor);

        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);

        subPaint.setColor(Color.parseColor("#8fa0b0"));
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setLetterSpacing(0.1f);
    }

    public void setProgress(float progress, int color, String subtitle) {
        this.progress = Math.max(0f, Math.min(100f, progress));
        this.progressColor = color;
        this.progressPaint.setColor(color);
        if (subtitle != null) {
            this.subtitle = subtitle;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.min(w, h);
        float stroke = size * 0.08f;
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);

        float pad = stroke;
        float cx = w / 2f;
        float cy = h / 2f;
        arcRect.set(cx - size / 2f + pad, cy - size / 2f + pad, cx + size / 2f - pad, cy + size / 2f - pad);

        // 背景圆环
        canvas.drawArc(arcRect, 0, 360, false, trackPaint);

        // 进度圆环 (从上方 -90度 开始)
        float sweep = (progress / 100f) * 360f;
        canvas.drawArc(arcRect, -90, sweep, false, progressPaint);

        // 数值
        valuePaint.setTextSize(size * 0.26f);
        String valStr = String.format(Locale.getDefault(), "%.0f%%", progress);
        float valY = cy + (size * 0.06f);
        canvas.drawText(valStr, cx, valY, valuePaint);

        // 副标题
        subPaint.setTextSize(size * 0.11f);
        canvas.drawText(subtitle, cx, valY + (size * 0.16f), subPaint);
    }
}
