package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * MultiSegmentBarView
 * 专业级 macOS 活动监视器风格多段着色进度条：
 * - App 内存：极客蓝 (#00d2ff)
 * - Wired 联动核心：极客绿 (#00f59b)
 * - Compressed 压缩：能量橙 (#ffd166)
 * - Free 空闲：底槽深黑蓝 (#101722)
 */
public class MultiSegmentBarView extends View {
    private float appGb = 0f;
    private float wiredGb = 0f;
    private float compGb = 0f;
    private float totalGb = 16f;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint appPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wiredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint compPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public MultiSegmentBarView(Context context) {
        super(context);
        init();
    }

    public MultiSegmentBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#101722"));

        appPaint.setStyle(Paint.Style.FILL);
        appPaint.setColor(Color.parseColor("#00d2ff")); // 浅蓝 App

        wiredPaint.setStyle(Paint.Style.FILL);
        wiredPaint.setColor(Color.parseColor("#00f59b")); // 翡翠绿 Wired

        compPaint.setStyle(Paint.Style.FILL);
        compPaint.setColor(Color.parseColor("#ffd166")); // 琥珀黄 Compressed
    }

    public void setSegments(double app, double wired, double compressed, double total) {
        this.appGb = (float) Math.max(0, app);
        this.wiredGb = (float) Math.max(0, wired);
        this.compGb = (float) Math.max(0, compressed);
        this.totalGb = (float) Math.max(1.0, total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float radius = h / 2f;

        // 1. 绘制底槽 (深色带全圆角)
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);

        // 2. 依次按比例绘制各段
        float appW = Math.min(w, (appGb / totalGb) * w);
        float wiredW = Math.min(w - appW, (wiredGb / totalGb) * w);
        float compW = Math.min(w - appW - wiredW, (compGb / totalGb) * w);

        float currentLeft = 0f;

        if (appW > 1f) {
            rect.set(currentLeft, 0, currentLeft + appW, h);
            canvas.drawRoundRect(rect, radius, radius, appPaint);
            currentLeft += appW;
        }

        if (wiredW > 1f) {
            rect.set(currentLeft, 0, currentLeft + wiredW, h);
            canvas.drawRect(rect, wiredPaint);
            currentLeft += wiredW;
        }

        if (compW > 1f) {
            rect.set(currentLeft, 0, currentLeft + compW, h);
            canvas.drawRect(rect, compPaint);
            currentLeft += compW;
        }

        // 裁切修复整体右边缘圆角
        if (currentLeft > 0) {
            // 右端小圆角视觉自然过渡
        }
    }
}
