package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class MicWaveView extends View {
    private static final int BAR_COUNT = 4;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private float currentAmplitude = 0.2f;
    private float phase = 0f;
    private boolean isAnimating = false;

    public MicWaveView(Context context) {
        super(context);
        init();
    }

    public MicWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(Color.parseColor("#00f59b"));
    }

    public void setRunning(boolean running) {
        this.isAnimating = running;
        if (!running) {
            currentAmplitude = 0.1f;
        }
        invalidate();
    }

    public void updateAmplitude(float amp) {
        // 平滑过渡
        this.currentAmplitude = this.currentAmplitude * 0.4f + amp * 0.6f;
        this.phase += 0.35f;
        if (isAnimating) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isAnimating) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float barWidth = w / (BAR_COUNT * 2f - 1f);
        float cornerRadius = barWidth / 2f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float x = i * barWidth * 2f;
            // 结合真实音量与正弦波呼吸
            double wave = Math.abs(Math.sin(phase + i * 0.9));
            float barHeightRatio = (float) (0.2 + 0.3 * wave + 0.5 * currentAmplitude);
            barHeightRatio = Math.max(0.15f, Math.min(1.0f, barHeightRatio));

            float barH = h * barHeightRatio;
            float y = (h - barH) / 2f;

            barRect.set(x, y, x + barWidth, y + barH);
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);
        }

        if (isAnimating) {
            postInvalidateDelayed(40);
        }
    }
}
