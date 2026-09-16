package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/**
 * 🌟 ESP32-C3 AlphaPi 原生红外避障 / 接近检测雷达 (Infrared Proximity Radar)
 * 具备 360° 旋转雷达光束扫描与近距触发红光呼吸告警特效
 */
public class IrRadarView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint alertPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int irState = 1; // 1 = 通畅 (无遮挡), 0 = 近距阻挡 (有物体贴近)
    private float sweepAngle = 0f;
    private float pulseAlpha = 0f;
    private boolean isPulseIncreasing = true;

    public IrRadarView(Context context) {
        super(context);
        init();
    }

    public IrRadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.parseColor("#1a365d"));
        ringPaint.setStrokeWidth(1.5f);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(Color.parseColor("#122438"));
        linePaint.setStrokeWidth(1.2f);

        alertPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(13f);
        textPaint.setFakeBoldText(true);
    }

    public void updateIrState(int state) {
        this.irState = state;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h * 0.45f;
        float maxR = Math.min(cx, cy) - 10f;
        if (maxR < 20) return;

        // 雷达自旋动画
        sweepAngle = (sweepAngle + 4f) % 360f;

        // 1. 同心雷达环
        for (int i = 1; i <= 3; i++) {
            float r = maxR * (i / 3f);
            ringPaint.setColor(irState == 0 ? Color.parseColor("#5c1d1d") : Color.parseColor("#1a365d"));
            canvas.drawCircle(cx, cy, r, ringPaint);
        }

        // 十字经纬标线
        canvas.drawLine(cx - maxR, cy, cx + maxR, cy, linePaint);
        canvas.drawLine(cx, cy - maxR, cx, cy + maxR, linePaint);

        // 2. 旋转扫描光束 (Sweep beam)
        canvas.save();
        canvas.rotate(sweepAngle, cx, cy);
        int beamColor = (irState == 0) ? Color.parseColor("#ef4444") : Color.parseColor("#00f59b");
        int beamColorTransparent = Color.TRANSPARENT;
        SweepGradient gradient = new SweepGradient(cx, cy,
                new int[]{beamColorTransparent, beamColorTransparent, Color.argb(80, Color.red(beamColor), Color.green(beamColor), Color.blue(beamColor)), beamColor},
                new float[]{0f, 0.7f, 0.95f, 1.0f}
        );
        sweepPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, maxR, sweepPaint);
        sweepPaint.setShader(null);
        // 扫描前锋亮线
        linePaint.setColor(beamColor);
        linePaint.setStrokeWidth(2f);
        canvas.drawLine(cx, cy, cx + maxR, cy, linePaint);
        canvas.restore();

        // 3. 中心红外避障状态告警灯
        if (irState == 0) {
            // 障碍物靠近：红光强烈脉冲呼吸
            if (isPulseIncreasing) {
                pulseAlpha += 8f;
                if (pulseAlpha >= 220f) isPulseIncreasing = false;
            } else {
                pulseAlpha -= 8f;
                if (pulseAlpha <= 60f) isPulseIncreasing = true;
            }
            alertPaint.setColor(Color.argb((int) pulseAlpha, 239, 68, 68));
            canvas.drawCircle(cx, cy, 18f, alertPaint);

            alertPaint.setColor(Color.parseColor("#ef4444"));
            canvas.drawCircle(cx, cy, 9f, alertPaint);

            textPaint.setColor(Color.parseColor("#ef4444"));
            canvas.drawText("⚠️ 障碍物贴近 / 遮挡触动", cx, h - 8f, textPaint);
        } else {
            // 前方通畅：绿色微光
            alertPaint.setColor(Color.argb(70, 0, 245, 155));
            canvas.drawCircle(cx, cy, 14f, alertPaint);

            alertPaint.setColor(Color.parseColor("#00f59b"));
            canvas.drawCircle(cx, cy, 7f, alertPaint);

            textPaint.setColor(Color.parseColor("#00f59b"));
            canvas.drawText("✅ 红外视距通畅 (无遮挡)", cx, h - 8f, textPaint);
        }

        // 持续微帧刷新维持雷达旋转动画
        postInvalidateDelayed(35);
    }
}
