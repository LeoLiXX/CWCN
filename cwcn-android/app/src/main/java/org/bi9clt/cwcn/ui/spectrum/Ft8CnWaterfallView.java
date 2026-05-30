package org.bi9clt.cwcn.ui.spectrum;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Adapted from FT8CN WaterfallView (MIT License), stripped to spectrum-only display.
 */
public final class Ft8CnWaterfallView extends View {
    private static final int ACCENT_COLOR = 0xff00ffff;
    private static final int AUTO_TRACK_COLOR = 0xffffcc33;
    private static final int TRACKING_WINDOW_COLOR = 0x2400ffff;
    private Bitmap bitmap;
    private Bitmap scrollBitmap;
    private Canvas bitmapCanvas;
    private Canvas scrollCanvas;
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackingWindowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();
    private final Rect cellRect = new Rect();
    private int blockHeight = 2;
    private int touchFrequencyHz = -1;
    private int autoTrackedFrequencyHz = -1;
    private int trackingWindowCenterHz = -1;
    private int trackingWindowHalfWidthHz = 0;
    private int maxFrequencyHz = 3000;
    public Ft8CnWaterfallView(Context context) {
        super(context);
    }

    public Ft8CnWaterfallView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Ft8CnWaterfallView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

    public void setWaveData(@Nullable int[] data, long sequence) {
        if (data == null || data.length == 0 || bitmap == null || bitmapCanvas == null || scrollBitmap == null || scrollCanvas == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int effectiveBlockHeight = Math.max(1, Math.min(blockHeight, height));
        int scrollHeight = Math.max(0, height - effectiveBlockHeight);

        scrollCanvas.drawColor(Color.BLACK);
        if (scrollHeight > 0) {
            srcRect.set(0, 0, width, scrollHeight);
            dstRect.set(0, effectiveBlockHeight, width, height);
            scrollCanvas.drawBitmap(bitmap, srcRect, dstRect, null);
        }
        drawSpectrumRow(scrollCanvas, data, effectiveBlockHeight);

        Bitmap swapBitmap = bitmap;
        bitmap = scrollBitmap;
        scrollBitmap = swapBitmap;
        Canvas swapCanvas = bitmapCanvas;
        bitmapCanvas = scrollCanvas;
        scrollCanvas = swapCanvas;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blockHeight = Math.max(1, h / 186);
        bitmap = Bitmap.createBitmap(w, h, ARGB_8888);
        scrollBitmap = Bitmap.createBitmap(w, h, ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        scrollCanvas = new Canvas(scrollBitmap);
        clearPaint.setColor(Color.BLACK);
        bitmapCanvas.drawRect(0, 0, w, h, clearPaint);
        scrollCanvas.drawRect(0, 0, w, h, clearPaint);

        markerPaint.setColor(ACCENT_COLOR);
        markerPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        labelPaint.setColor(ACCENT_COLOR);
        labelPaint.setTextSize(dpToPixel(10));
        labelPaint.setAntiAlias(true);
        labelPaint.setDither(true);
        labelBackgroundPaint.setColor(0xcc021318);
        autoTrackPaint.setColor(AUTO_TRACK_COLOR);
        autoTrackPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        trackingWindowPaint.setColor(TRACKING_WINDOW_COLOR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
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
            float shortTop = Math.max(0f, getHeight() * 0.04f);
            float shortBottom = Math.min(getHeight(), getHeight() * 0.18f);
            canvas.drawLine(autoX, shortTop, autoX, shortBottom, autoTrackPaint);
        }
        if (touchFrequencyHz > 0 && maxFrequencyHz > 0) {
            float x = getWidth() * (touchFrequencyHz / (float) maxFrequencyHz);
            canvas.drawLine(x, 0, x, getHeight(), markerPaint);
            drawFrequencyLabel(canvas, x);
        }
    }

    private void drawFrequencyLabel(Canvas canvas, float x) {
        String label = String.format(Locale.US, "%dHz", touchFrequencyHz);
        float paddingHorizontal = dpToPixel(4);
        float paddingVertical = dpToPixel(2);
        Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
        float textWidth = labelPaint.measureText(label);
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float rectLeft = x <= getWidth() / 2f ? x + dpToPixel(8) : x - textWidth - paddingHorizontal * 2f - dpToPixel(8);
        rectLeft = Math.max(0f, Math.min(rectLeft, getWidth() - textWidth - paddingHorizontal * 2f));
        float rectTop = Math.max(dpToPixel(10), getHeight() * 0.18f);
        float rectRight = rectLeft + textWidth + paddingHorizontal * 2f;
        float rectBottom = rectTop + textHeight + paddingVertical * 2f;
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, labelBackgroundPaint);
        float textBaseline = rectTop + paddingVertical - fontMetrics.top;
        canvas.drawText(label, rectLeft + paddingHorizontal, textBaseline, labelPaint);
    }

    private void drawSpectrumRow(Canvas canvas, int[] data, int rowHeight) {
        int width = getWidth();
        for (int index = 0; index < data.length; index++) {
            int left = index * width / data.length;
            int right = (index + 1) * width / data.length;
            if (right <= left) {
                right = Math.min(width, left + 1);
            }
            cellRect.set(left, 0, right, rowHeight);
            cellPaint.setColor(colorForValue(data[index]));
            canvas.drawRect(cellRect, cellPaint);
        }
    }

    private int colorForValue(int rawValue) {
        int value = Math.max(0, Math.min(255, rawValue));
        if (value < 128) {
            return 0xff000000 | (value << 1);
        }
        if (value < 192) {
            return 0xff0000ff | ((value - 127) << 10);
        }
        return 0xff00ffff | ((value - 127) << 18);
    }

    private int dpToPixel(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
