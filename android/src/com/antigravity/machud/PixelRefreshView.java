package com.antigravity.machud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * PixelRefreshView
 * 专为 AMOLED 屏幕打造的持续抗衰洗屏保养模式。
 * 运行时在全屏柔和缓慢推移全光谱渐变流动条，活化并均衡所有 RGB 子像素的电致发光特性，
 * 消除潜在隐性微残影。支持持续常态运行，单击屏幕任意处随时退出。
 */
public class PixelRefreshView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float offset = 0f;
    private boolean isRefreshing = false;

    public interface OnRefreshCompleteListener {
        void onComplete();
    }
    private OnRefreshCompleteListener completeListener;

    public PixelRefreshView(Context context) {
        super(context);
        init();
    }

    public PixelRefreshView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
    }

    public void startRefresh(OnRefreshCompleteListener listener) {
        this.completeListener = listener;
        this.isRefreshing = true;
        this.setVisibility(VISIBLE);
        invalidate();
    }

    public void stopRefresh() {
        this.isRefreshing = false;
        this.setVisibility(GONE);
        if (completeListener != null) {
            completeListener.onComplete();
        }
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isRefreshing) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        offset += 6f; // 平滑平移速度
        if (offset > w * 2) {
            offset = 0f;
        }

        // 全光谱温和均匀色彩分布（均衡激发红、绿、蓝所有有机子像素）
        int[] colors = {
                Color.parseColor("#55ff3333"), // 纯红
                Color.parseColor("#55ff9922"), // 暖橙
                Color.parseColor("#55ffee33"), // 亮黄
                Color.parseColor("#5522dd66"), // 翡翠绿
                Color.parseColor("#5511ccee"), // 湖水青
                Color.parseColor("#553388ff"), // 纯蓝
                Color.parseColor("#55aa44ee"), // 紫罗兰
                Color.parseColor("#55ff3333")  // 闭环红
        };
        float[] positions = {0f, 0.14f, 0.28f, 0.42f, 0.57f, 0.71f, 0.85f, 1f};

        LinearGradient shader = new LinearGradient(
                offset - w, 0, offset + w, h,
                colors, positions, Shader.TileMode.REPEAT
        );
        paint.setShader(shader);
        canvas.drawRect(0, 0, w, h, paint);

        postInvalidateOnAnimation();
    }
}
