package com.example.decibeldetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class DecibelGaugeView extends View {
    private static final float START_ANGLE = 150f;
    private static final float SWEEP_ANGLE = 240f;
    private static final float MIN_DB = 0f;
    private static final float MAX_DB = 120f;
    private static final String RANGE_LABEL = "安全 0-50 | 注意 50-70 | 危险 70+";

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mediumTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float currentDb = 0f;

    public DecibelGaugeView(Context context) {
        super(context);
        init();
    }

    public DecibelGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DecibelGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setColor(Color.parseColor("#5E6B7B"));
        majorTickPaint.setStrokeWidth(dp(2f));

        mediumTickPaint.setStyle(Paint.Style.STROKE);
        mediumTickPaint.setColor(Color.parseColor("#7E8C9C"));
        mediumTickPaint.setStrokeWidth(dp(1.2f));

        minorTickPaint.setStyle(Paint.Style.STROKE);
        minorTickPaint.setColor(Color.parseColor("#A2AFBE"));
        minorTickPaint.setStrokeWidth(dp(0.7f));

        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeWidth(dp(5));
        pointerPaint.setStrokeCap(Paint.Cap.ROUND);
        pointerPaint.setColor(Color.parseColor("#1F2937"));

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.parseColor("#1F2937"));

        labelPaint.setColor(Color.parseColor("#5B6472"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(12));

        valuePaint.setColor(Color.parseColor("#111827"));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(24));
        valuePaint.setFakeBoldText(true);
    }

    public void setCurrentDb(double dbValue) {
        currentDb = Math.max(MIN_DB, Math.min(MAX_DB, (float) dbValue));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height * 0.66f;
        float outerPadding = dp(18);
        float strokeWidth = dp(16);
        float labelReserve = dp(34);
        float radius = Math.min(
                width / 2f - outerPadding - labelReserve,
                centerY - outerPadding - labelReserve / 2f
        );

        arcPaint.setStrokeWidth(strokeWidth);
        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        drawZone(canvas, 0f, 50f, "#2EBD85");
        drawZone(canvas, 50f, 70f, "#F2B134");
        drawZone(canvas, 70f, 120f, "#E15A5A");
        drawTicks(canvas, centerX, centerY, radius, strokeWidth);
        drawPointer(canvas, centerX, centerY, radius - strokeWidth / 3f);

        canvas.drawCircle(centerX, centerY, dp(8), centerPaint);
        canvas.drawText(Math.round(currentDb) + " dB", centerX, centerY + dp(55), valuePaint);
        canvas.drawText(RANGE_LABEL, centerX, centerY + radius * 0.7f, labelPaint);
    }

    private void drawZone(Canvas canvas, float startDb, float endDb, String color) {
        arcPaint.setColor(Color.parseColor(color));
        float startAngle = dbToAngle(startDb);
        float sweep = (endDb - startDb) / (MAX_DB - MIN_DB) * SWEEP_ANGLE;
        canvas.drawArc(arcRect, startAngle, sweep, false, arcPaint);
    }

    private void drawTicks(Canvas canvas, float centerX, float centerY, float radius, float strokeWidth) {
        for (int value = 0; value <= 120; value++) {
            boolean isMajor = value % 10 == 0;
            boolean isMedium = !isMajor && value % 5 == 0;
            Paint tickPaint = isMajor ? majorTickPaint : (isMedium ? mediumTickPaint : minorTickPaint);
            double angle = Math.toRadians(dbToAngle(value));

            float tickExtra = isMajor ? dp(12) : (isMedium ? dp(8) : dp(4));
            float endRadius = radius - strokeWidth / 2f - dp(1);
            float startRadius = endRadius - tickExtra;
            float startX = centerX + (float) (Math.cos(angle) * startRadius);
            float startY = centerY + (float) (Math.sin(angle) * startRadius);
            float endX = centerX + (float) (Math.cos(angle) * endRadius);
            float endY = centerY + (float) (Math.sin(angle) * endRadius);
            canvas.drawLine(startX, startY, endX, endY, tickPaint);

            if (isMajor) {
                float labelRadius = radius + strokeWidth / 2f + dp(14);
                float textX = centerX + (float) (Math.cos(angle) * labelRadius);
                float textY = centerY + (float) (Math.sin(angle) * labelRadius) + dp(4);
                canvas.drawText(String.valueOf(value), textX, textY, labelPaint);
            }
        }
    }

    private void drawPointer(Canvas canvas, float centerX, float centerY, float radius) {
        double angle = Math.toRadians(dbToAngle(currentDb));
        float endX = centerX + (float) (Math.cos(angle) * radius);
        float endY = centerY + (float) (Math.sin(angle) * radius);
        canvas.drawLine(centerX, centerY, endX, endY, pointerPaint);
    }

    private float dbToAngle(float dbValue) {
        return START_ANGLE + (dbValue - MIN_DB) / (MAX_DB - MIN_DB) * SWEEP_ANGLE;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
