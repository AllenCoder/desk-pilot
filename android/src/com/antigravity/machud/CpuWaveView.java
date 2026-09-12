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

/**
 * CpuWaveView
 * 实时绘制最近 60 秒 CPU 总体负载波动微折线（Sparkline），
 * 搭配半透明流光渐变填充和基准刻度，填补 CPU 卡片空白并提供历史追溯。
 */
public class CpuWaveView extends View {
    private static final int MAX_POINTS = 45;
    private final List<Float> cpuHistory = new ArrayList<>();

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public CpuWaveView(Context context) {
        super(context);
        init();
    }

    public CpuWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.2f);
        linePaint.setColor(Color.parseColor("#00f59b"));

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.parseColor("#151d26"));

        for (int i = 0; i < MAX_POINTS; i++) {
            cpuHistory.add(0f);
        }
    }

    public void addSample(float cpuPercent) {
        if (cpuHistory.size() >= MAX_POINTS) {
            cpuHistory.remove(0);
        }
        cpuHistory.add(Math.max(0f, Math.min(100f, cpuPercent)));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || cpuHistory.isEmpty()) return;

        // 绘制两道轻微水平基准参考线 (50% 和 25%)
        float y50 = h * 0.5f;
        float y25 = h * 0.75f;
        canvas.drawLine(0, y50, w, y50, gridPaint);
        canvas.drawLine(0, y25, w, y25, gridPaint);

        linePath.reset();
        fillPath.reset();

        float stepX = (float) w / (MAX_POINTS - 1);
        float baseH = h - 2f;

        // 动态根据最新数值切换折线颜色 (若超 70% 报警红，超 35% 警示橙，其余极客绿)
        float latest = cpuHistory.get(cpuHistory.size() - 1);
        int waveColor = (latest > 70f) ? Color.parseColor("#ff4757") :
                (latest > 35f) ? Color.parseColor("#ffd166") : Color.parseColor("#00f59b");
        linePaint.setColor(waveColor);

        // 渐变填充
        int fillColorTop = Color.argb(45, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor));
        int fillColorBottom = Color.argb(0, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor));
        fillPaint.setShader(new LinearGradient(0, 0, 0, h, fillColorTop, fillColorBottom, Shader.TileMode.CLAMP));

        fillPath.moveTo(0, h);

        for (int i = 0; i < cpuHistory.size(); i++) {
            float val = cpuHistory.get(i);
            float y = baseH - (val / 100f) * (baseH - 4f);
            float x = i * stepX;

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        fillPath.lineTo(w, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}
