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
 * 🌟 ESP32-C3 AlphaPi 原生六轴姿态仪 (Artificial Horizon & Gimbal Indicator)
 * 纯原生 Canvas 硬件加速渲染，高帧率动态解算 Pitch(俯仰)、Roll(横滚) 与三轴重力加速度 (Ax, Ay, Az)
 */
public class ImuHorizonView extends View {

    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pitchLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path clipPath = new Path();
    private final RectF outerBounds = new RectF();

    // 姿态解算平滑滤波值
    private float pitch = 0f;       // 俯仰角 (-90° ~ +90°)
    private float roll = 0f;        // 横滚角 (-180° ~ +180°)
    private float targetPitch = 0f;
    private float targetRoll = 0f;

    // 三轴加速度原始值 (mg)
    private int rawAx = 0;
    private int rawAy = 0;
    private int rawAz = -850;

    public ImuHorizonView(Context context) {
        super(context);
        init();
    }

    public ImuHorizonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        skyPaint.setColor(Color.parseColor("#091d34"));      // 深蓝天际
        skyPaint.setStyle(Paint.Style.FILL);

        groundPaint.setColor(Color.parseColor("#1f1406"));   // 深褐大地
        groundPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(Color.parseColor("#1a365d"));     // 罗盘刻度外环
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        pitchLinePaint.setColor(Color.parseColor("#00f59b"));// 俯仰标尺绿线
        pitchLinePaint.setStyle(Paint.Style.STROKE);
        pitchLinePaint.setStrokeWidth(2.5f);

        reticlePaint.setColor(Color.parseColor("#f59e0b"));  // 准星十字琥珀金
        reticlePaint.setStyle(Paint.Style.STROKE);
        reticlePaint.setStrokeWidth(3f);

        textPaint.setColor(Color.parseColor("#00f59b"));
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        barPaint.setStyle(Paint.Style.FILL);
        barBgPaint.setColor(Color.parseColor("#121f2d"));
        barBgPaint.setStyle(Paint.Style.FILL);
    }

    public void updateAttitude(float newPitch, float newRoll, int ax, int ay, int az) {
        this.targetPitch = Math.max(-60f, Math.min(60f, newPitch));
        this.targetRoll = Math.max(-180f, Math.min(180f, newRoll));
        this.rawAx = ax;
        this.rawAy = ay;
        this.rawAz = az;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 平滑插值 (EMA 滤波)
        pitch = pitch * 0.7f + targetPitch * 0.3f;
        roll = roll * 0.7f + targetRoll * 0.3f;

        float cx = w * 0.40f;
        float cy = h * 0.45f;
        float radius = Math.min(cx, cy) - 14f;
        if (radius < 25) return;

        // 1. 绘制姿态球外轮廓裁剪区
        clipPath.reset();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        // 旋转天地线 (横滚 Roll)
        canvas.save();
        canvas.rotate(-roll, cx, cy);

        // 垂直平移 (俯仰 Pitch: 1度对应 1.8 像素)
        float pitchOffset = pitch * 1.8f;
        float bigR = radius * 2.5f;

        // 天际半球
        canvas.drawRect(cx - bigR, cy - bigR + pitchOffset, cx + bigR, cy + pitchOffset, skyPaint);
        // 大地半球
        canvas.drawRect(cx - bigR, cy + pitchOffset, cx + bigR, cy + bigR + pitchOffset, groundPaint);

        // 天地交界线 (白色基准地平线)
        pitchLinePaint.setColor(Color.WHITE);
        pitchLinePaint.setStrokeWidth(3f);
        canvas.drawLine(cx - bigR, cy + pitchOffset, cx + bigR, cy + pitchOffset, pitchLinePaint);

        // 绘制俯仰标尺梯线 (+20°, +10°, -10°, -20°)
        pitchLinePaint.setColor(Color.parseColor("#00f59b"));
        pitchLinePaint.setStrokeWidth(2f);
        int[] steps = {-30, -20, -10, 10, 20, 30};
        for (int s : steps) {
            float y = cy + pitchOffset - (s * 1.8f);
            float lineWidth = (Math.abs(s) % 20 == 0) ? radius * 0.65f : radius * 0.4f;
            canvas.drawLine(cx - lineWidth / 2, y, cx + lineWidth / 2, y, pitchLinePaint);
        }

        canvas.restore(); // 恢复天地线旋转

        canvas.restore(); // 恢复球形裁剪

        // 2. 绘制姿态仪固定机械十字准星 (飞机指示标)
        reticlePaint.setColor(Color.parseColor("#f59e0b"));
        canvas.drawCircle(cx, cy, 4f, reticlePaint);
        canvas.drawLine(cx - 28f, cy, cx - 8f, cy, reticlePaint);
        canvas.drawLine(cx + 8f, cy, cx + 28f, cy, reticlePaint);
        canvas.drawLine(cx, cy - 8f, cx, cy - 22f, reticlePaint);

        // 3. 姿态仪外环与极客刻度圈
        ringPaint.setColor(Color.parseColor("#00f59b"));
        ringPaint.setStrokeWidth(2.5f);
        canvas.drawCircle(cx, cy, radius, ringPaint);

        // 4. 底部显示实时度数数值
        textPaint.setTextSize(14f);
        textPaint.setColor(Color.parseColor("#e2e8f0"));
        String attitudeText = String.format(Locale.getDefault(), "俯仰 PITCH: %+.1f°  横滚 ROLL: %+.1f°", pitch, roll);
        canvas.drawText(attitudeText, cx, h - 8f, textPaint);

        // 5. 右侧显示三轴重力加速度 (Ax, Ay, Az 动态条形柱)
        float barLeft = w * 0.72f;
        float barWidth = w * 0.24f;
        float barTop = 14f;
        float barSpacing = (h - 32f) / 3f;

        drawAxisBar(canvas, "Ax", rawAx, barLeft, barTop, barWidth, 12f, Color.parseColor("#38bdf8"));
        drawAxisBar(canvas, "Ay", rawAy, barLeft, barTop + barSpacing, barWidth, 12f, Color.parseColor("#a78bfa"));
        drawAxisBar(canvas, "Az", rawAz, barLeft, barTop + barSpacing * 2, barWidth, 12f, Color.parseColor("#34d399"));
    }

    private void drawAxisBar(Canvas canvas, String label, int val, float x, float y, float width, float barH, int color) {
        // 背景槽
        outerBounds.set(x, y + 14f, x + width, y + 14f + barH);
        canvas.drawRoundRect(outerBounds, 4f, 4f, barBgPaint);

        // 动态填充条 (-1000 ~ +1000 归一化)
        float clamped = Math.max(-1000f, Math.min(1000f, (float) val));
        float ratio = (clamped + 1000f) / 2000f;
        float fillW = width * ratio;

        barPaint.setColor(color);
        outerBounds.set(x, y + 14f, x + fillW, y + 14f + barH);
        canvas.drawRoundRect(outerBounds, 4f, 4f, barPaint);

        // 文字标签
        textPaint.setTextSize(12f);
        textPaint.setColor(Color.parseColor("#94a3b8"));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label + ": " + val + "mg", x, y + 10f, textPaint);
    }
}
