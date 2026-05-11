package org.bi9clt.cwcn.ui.spectrum;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * Adapted from FT8CN ColumnarView (MIT License).
 */
public final class Ft8CnColumnarView extends View {
    private static final int ACCENT_COLOR = 0xff00ffff;
    private static final int AUTO_TRACK_COLOR = 0xffffcc33;
    private static final int TRACKING_WINDOW_COLOR = 0x2400ffff;
    private static final int SQL_REFERENCE_COLOR = 0xff7ef9ff;
    private static final int SQL_SHADE_COLOR = 0x2600ffff;
    private static final int SPACING_PX = 1;
    private static final int PEAK_TRAIL_HEIGHT_PX = 5;
    private static final int PEAK_TRAIL_FALL_SPEED_PX = 5;
    private static final int PEAK_TRAIL_GAP_PX = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackingWindowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sqlReferencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sqlShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Rect> bars = new ArrayList<>();
    private final List<Rect> peakTrails = new ArrayList<>();
    private Bitmap bitmap;
    private Canvas bitmapCanvas;
    private int touchFrequencyHz = -1;
    private int autoTrackedFrequencyHz = -1;
    private int trackingWindowCenterHz = -1;
    private int trackingWindowHalfWidthHz = 0;
    private int maxFrequencyHz = 3000;
    private int sqlReferenceLevel = -1;
    private int sqlPercent = -1;

    public Ft8CnColumnarView(Context context) {
        super(context);
    }

    public Ft8CnColumnarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Ft8CnColumnarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxFrequencyHz(int maxFrequencyHz) {
        this.maxFrequencyHz = Math.max(500, maxFrequencyHz);
    }

    public void setTouchFrequencyHz(int touchFrequencyHz) {
        this.touchFrequencyHz = touchFrequencyHz;
        postInvalidateOnAnimation();
    }

    public void setAutoTrackedFrequencyHz(int autoTrackedFrequencyHz) {
        this.autoTrackedFrequencyHz = autoTrackedFrequencyHz > 0 ? Math.min(maxFrequencyHz, autoTrackedFrequencyHz) : -1;
        postInvalidateOnAnimation();
    }

    public void setTrackingWindowHz(int centerFrequencyHz, int halfWidthHz) {
        trackingWindowCenterHz = centerFrequencyHz > 0 ? Math.min(maxFrequencyHz, centerFrequencyHz) : -1;
        trackingWindowHalfWidthHz = Math.max(0, halfWidthHz);
        postInvalidateOnAnimation();
    }

    public void setSqlReferenceLevel(int sqlReferenceLevel, int sqlPercent) {
        this.sqlReferenceLevel = sqlReferenceLevel >= 0 ? Math.min(255, sqlReferenceLevel) : -1;
        this.sqlPercent = sqlPercent >= 0 ? Math.min(100, sqlPercent) : -1;
        postInvalidateOnAnimation();
    }

    public void setWaveData(@Nullable int[] data) {
        if (data == null || data.length == 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        bars.clear();
        float rateHeight = 0.95f * getHeight() / 256f;
        for (int index = 0; index < data.length; index++) {
            Rect rect = new Rect();
            rect.left = index * getWidth() / data.length;
            rect.top = getHeight() - Math.round(Math.max(0, Math.min(255, data[index])) * rateHeight);
            int nextLeft = (index + 1) * getWidth() / data.length;
            rect.right = Math.max(rect.left + 1, nextLeft - SPACING_PX);
            rect.bottom = getHeight();
            bars.add(rect);
        }
        updatePeakTrails();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bitmap = Bitmap.createBitmap(w, h, ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        LinearGradient linearGradient = new LinearGradient(
                0f,
                0f,
                0f,
                getHeight(),
                new int[]{ACCENT_COLOR, ACCENT_COLOR, Color.BLUE},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP
        );
        paint.setShader(linearGradient);
        markerPaint.setColor(ACCENT_COLOR);
        markerPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        peakTrailPaint.setShader(linearGradient);
        labelPaint.setColor(ACCENT_COLOR);
        labelPaint.setTextSize(dpToPixel(10));
        labelBackgroundPaint.setColor(0xcc021318);
        autoTrackPaint.setColor(AUTO_TRACK_COLOR);
        autoTrackPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        trackingWindowPaint.setColor(TRACKING_WINDOW_COLOR);
        sqlReferencePaint.setColor(SQL_REFERENCE_COLOR);
        sqlReferencePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        sqlShadePaint.setColor(SQL_SHADE_COLOR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmapCanvas == null || bitmap == null) {
            return;
        }
        bitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (Rect rect : bars) {
            bitmapCanvas.drawRect(rect, paint);
        }
        for (Rect rect : peakTrails) {
            bitmapCanvas.drawRect(rect, peakTrailPaint);
        }
        canvas.drawBitmap(bitmap, 0, 0, null);
        drawSqlReference(canvas);
        if (trackingWindowCenterHz > 0 && trackingWindowHalfWidthHz > 0 && maxFrequencyHz > 0) {
            float left = getWidth() * ((trackingWindowCenterHz - trackingWindowHalfWidthHz) / (float) maxFrequencyHz);
            float right = getWidth() * ((trackingWindowCenterHz + trackingWindowHalfWidthHz) / (float) maxFrequencyHz);
            canvas.drawRect(
                    Math.max(0f, left),
                    0f,
                    Math.min(getWidth(), right),
                    getHeight(),
                    trackingWindowPaint
            );
        }
        if (autoTrackedFrequencyHz > 0 && maxFrequencyHz > 0) {
            float autoX = getWidth() * (autoTrackedFrequencyHz / (float) maxFrequencyHz);
            float shortTop = Math.max(0f, getHeight() * 0.08f);
            float shortBottom = Math.min(getHeight(), getHeight() * 0.32f);
            canvas.drawLine(autoX, shortTop, autoX, shortBottom, autoTrackPaint);
        }
        if (touchFrequencyHz > 0 && maxFrequencyHz > 0) {
            float x = getWidth() * (touchFrequencyHz / (float) maxFrequencyHz);
            canvas.drawLine(x, 0, x, getHeight(), markerPaint);
            drawFrequencyLabel(canvas, x);
        }
    }

    private void drawSqlReference(Canvas canvas) {
        if (sqlReferenceLevel < 0 || getHeight() <= 0) {
            return;
        }
        float y = sqlReferenceLevelToY(sqlReferenceLevel);
        canvas.drawRect(0f, y, getWidth(), getHeight(), sqlShadePaint);
        canvas.drawLine(0f, y, getWidth(), y, sqlReferencePaint);
        drawSqlLabel(canvas, y);
    }

    private float sqlReferenceLevelToY(int referenceLevel) {
        float rateHeight = 0.95f * getHeight() / 256f;
        return getHeight() - (Math.max(0, Math.min(255, referenceLevel)) * rateHeight);
    }

    private void drawSqlLabel(Canvas canvas, float y) {
        String label = "SQL";
        float paddingHorizontal = dpToPixel(4);
        float paddingVertical = dpToPixel(2);
        Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
        float textWidth = labelPaint.measureText(label);
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float rectLeft = dpToPixel(6);
        float desiredTop = y - textHeight - paddingVertical * 2f - dpToPixel(4);
        float rectTop = Math.max(0f, Math.min(desiredTop, getHeight() - textHeight - paddingVertical * 2f));
        float rectRight = rectLeft + textWidth + paddingHorizontal * 2f;
        float rectBottom = rectTop + textHeight + paddingVertical * 2f;
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, labelBackgroundPaint);
        float textBaseline = rectTop + paddingVertical - fontMetrics.top;
        canvas.drawText(label, rectLeft + paddingHorizontal, textBaseline, labelPaint);
    }

    private void updatePeakTrails() {
        if (peakTrails.size() != bars.size()) {
            peakTrails.clear();
            for (int index = 0; index < bars.size(); index++) {
                Rect bar = bars.get(index);
                Rect peakTrail = new Rect();
                peakTrail.left = bar.left;
                peakTrail.right = bar.right;
                peakTrail.top = Math.max(0, getHeight() - PEAK_TRAIL_HEIGHT_PX);
                peakTrail.bottom = Math.min(getHeight(), peakTrail.top + PEAK_TRAIL_HEIGHT_PX);
                peakTrails.add(peakTrail);
            }
        }
        for (int index = 0; index < peakTrails.size(); index++) {
            Rect peakTrail = peakTrails.get(index);
            Rect bar = bars.get(index);
            peakTrail.left = bar.left;
            peakTrail.right = bar.right;
            int candidateTop = Math.max(0, bar.top - PEAK_TRAIL_HEIGHT_PX - PEAK_TRAIL_GAP_PX);
            if (candidateTop < peakTrail.top) {
                peakTrail.top = candidateTop;
            } else {
                peakTrail.top = Math.min(getHeight() - PEAK_TRAIL_HEIGHT_PX, peakTrail.top + PEAK_TRAIL_FALL_SPEED_PX);
            }
            peakTrail.bottom = Math.min(getHeight(), peakTrail.top + PEAK_TRAIL_HEIGHT_PX);
        }
    }

    private void drawFrequencyLabel(Canvas canvas, float x) {
        String label = String.format(Locale.US, "%dHz", touchFrequencyHz);
        float paddingHorizontal = dpToPixel(4);
        float paddingVertical = dpToPixel(2);
        Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
        float textWidth = labelPaint.measureText(label);
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float rectLeft = x <= getWidth() / 2f ? x + dpToPixel(6) : x - textWidth - paddingHorizontal * 2f - dpToPixel(6);
        rectLeft = Math.max(0f, Math.min(rectLeft, getWidth() - textWidth - paddingHorizontal * 2f));
        float rectTop = dpToPixel(8);
        float rectRight = rectLeft + textWidth + paddingHorizontal * 2f;
        float rectBottom = rectTop + textHeight + paddingVertical * 2f;
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, labelBackgroundPaint);
        float textBaseline = rectTop + paddingVertical - fontMetrics.top;
        canvas.drawText(label, rectLeft + paddingHorizontal, textBaseline, labelPaint);
    }

    private int dpToPixel(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
