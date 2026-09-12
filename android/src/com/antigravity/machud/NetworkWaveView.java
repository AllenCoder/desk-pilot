package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class NetworkWaveView extends View {
    private static final int MAX_POINTS = 40;
    private final List<Float> downHistory = new ArrayList<>();
    private final List<Float> upHistory = new ArrayList<>();

    private final Paint downLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path downPath = new Path();
    private final Path downFillPath = new Path();
    private final Path upPath = new Path();

    private float maxVal = 1000f; // 初始基准 1KB

    public NetworkWaveView(Context context) {
        super(context);
        init();
    }

    public NetworkWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        downLinePaint.setStyle(Paint.Style.STROKE);
        downLinePaint.setStrokeWidth(2.5f);
        downLinePaint.setColor(Color.parseColor("#00f59b"));

        downFillPaint.setStyle(Paint.Style.FILL);

        upLinePaint.setStyle(Paint.Style.STROKE);
        upLinePaint.setStrokeWidth(2f);
        upLinePaint.setColor(Color.parseColor("#00d2ff"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.parseColor("#151b24"));

        for (int i = 0; i < MAX_POINTS; i++) {
            downHistory.add(0f);
            upHistory.add(0f);
        }
    }

    public void addSample(float downBytes, float upBytes) {
        downHistory.remove(0);
        downHistory.add(downBytes);

        upHistory.remove(0);
        upHistory.add(upBytes);

        float curMax = 500f;
        for (float v : downHistory) if (v > curMax) curMax = v;
        for (float v : upHistory) if (v > curMax) curMax = v;
        maxVal = curMax * 1.15f;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 绘制轻微网格线 (工控雷达示波器坐标网格)
        canvas.drawLine(0, h * 0.25f, w, h * 0.25f, gridPaint);
        canvas.drawLine(0, h * 0.50f, w, h * 0.50f, gridPaint);
        canvas.drawLine(0, h * 0.75f, w, h * 0.75f, gridPaint);
        canvas.drawLine(w * 0.25f, 0, w * 0.25f, h, gridPaint);
        canvas.drawLine(w * 0.50f, 0, w * 0.50f, h, gridPaint);
        canvas.drawLine(w * 0.75f, 0, w * 0.75f, h, gridPaint);

        downPath.reset();
        downFillPath.reset();
        upPath.reset();

        float stepX = (float) w / (MAX_POINTS - 1);

        downFillPath.moveTo(0, h);
        for (int i = 0; i < MAX_POINTS; i++) {
            float x = i * stepX;
            float dy = h - (downHistory.get(i) / maxVal) * h * 0.9f;
            float uy = h - (upHistory.get(i) / maxVal) * h * 0.9f;

            if (i == 0) {
                downPath.moveTo(x, dy);
                downFillPath.lineTo(x, dy);
                upPath.moveTo(x, uy);
            } else {
                downPath.lineTo(x, dy);
                downFillPath.lineTo(x, dy);
                upPath.lineTo(x, uy);
            }
        }
        downFillPath.lineTo(w, h);
        downFillPath.close();

        // 渐变填充绿色
        downFillPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                Color.parseColor("#4400f59b"), Color.parseColor("#0500f59b"),
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(downFillPath, downFillPaint);
        canvas.drawPath(downPath, downLinePaint);
        canvas.drawPath(upPath, upLinePaint);
    }
}
