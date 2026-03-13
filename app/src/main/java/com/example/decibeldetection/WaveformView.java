package com.example.decibeldetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class WaveformView extends View {
    private static final int BAR_COUNT = 28;
    private static final int BASELINE_BAR_COUNT = 44;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselineBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] normalizedBars = new float[BAR_COUNT];

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        baselineBarPaint.setStyle(Paint.Style.FILL);

        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setStrokeWidth(dp(1.4f));
        centerLinePaint.setColor(Color.parseColor("#EAF1FA"));
    }

    public void setWaveform(short[] buffer, int length) {
        if (buffer == null || length <= 0) {
            clear();
            return;
        }

        int step = Math.max(1, length / BAR_COUNT);
        for (int i = 0; i < BAR_COUNT; i++) {
            int start = i * step;
            if (start >= length) {
                normalizedBars[i] = 0f;
                continue;
            }

            int end = Math.min(length, start + step);
            int peak = 0;
            for (int j = start; j < end; j++) {
                peak = Math.max(peak, Math.abs(buffer[j]));
            }
            normalizedBars[i] = peak / 32768f;
        }
        invalidate();
    }

    public void clear() {
        for (int i = 0; i < normalizedBars.length; i++) {
            normalizedBars[i] = 0f;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Shader topShader = new LinearGradient(
                0, 0, 0, h,
                new int[]{Color.parseColor("#E8F0FA"), Color.parseColor("#6F8FB8")},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(topShader);
        baselineBarPaint.setColor(Color.parseColor("#97AECF"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float horizontalPadding = dp(6);
        float centerY = height * 0.60f;
        float topAreaTop = dp(10);
        float topAreaBottom = centerY - dp(8);
        float baselineTop = centerY + dp(18);
        float baselineBottom = baselineTop + dp(14);

        canvas.drawLine(horizontalPadding, centerY, width - horizontalPadding, centerY, centerLinePaint);

        drawTopBars(canvas, width, horizontalPadding, topAreaTop, topAreaBottom);
        drawBaselineBars(canvas, width, horizontalPadding, baselineTop, baselineBottom);
    }

    private void drawTopBars(Canvas canvas, float width, float horizontalPadding,
                             float topAreaTop, float topAreaBottom) {
        float availableWidth = width - horizontalPadding * 2f;
        float slotWidth = availableWidth / BAR_COUNT;
        float barWidth = Math.min(dp(8), slotWidth * 0.42f);
        float maxHeight = topAreaBottom - topAreaTop;

        for (int i = 0; i < BAR_COUNT; i++) {
            float centerX = horizontalPadding + slotWidth * i + slotWidth / 2f;
            float barHeight = dp(18) + normalizedBars[i] * (maxHeight - dp(18));
            float left = centerX - barWidth / 2f;
            float right = centerX + barWidth / 2f;
            float top = topAreaBottom - barHeight;
            canvas.drawRoundRect(left, top, right, topAreaBottom, dp(2), dp(2), barPaint);
        }
    }

    private void drawBaselineBars(Canvas canvas, float width, float horizontalPadding,
                                  float baselineTop, float baselineBottom) {
        float availableWidth = width - horizontalPadding * 2f;
        float slotWidth = availableWidth / BASELINE_BAR_COUNT;
        float barWidth = Math.min(dp(3), slotWidth * 0.34f);
        float centerIndex = (BASELINE_BAR_COUNT - 1) / 2f;

        for (int i = 0; i < BASELINE_BAR_COUNT; i++) {
            float centerX = horizontalPadding + slotWidth * i + slotWidth / 2f;
            float distanceFactor = 1f - Math.min(1f, Math.abs(i - centerIndex) / centerIndex);
            float barHeight = dp(8) + distanceFactor * dp(6);
            float left = centerX - barWidth / 2f;
            float right = centerX + barWidth / 2f;
            float top = baselineTop + (baselineBottom - baselineTop - barHeight) / 2f;
            float bottom = top + barHeight;
            canvas.drawRoundRect(left, top, right, bottom, dp(1.5f), dp(1.5f), baselineBarPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
