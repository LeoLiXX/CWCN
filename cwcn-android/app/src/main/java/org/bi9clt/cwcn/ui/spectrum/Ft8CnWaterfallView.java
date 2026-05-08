package org.bi9clt.cwcn.ui.spectrum;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
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
    private Bitmap bitmap;
    private Canvas bitmapCanvas;
    private final Paint linearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int blockHeight = 2;
    private int touchFrequencyHz = -1;
    private int autoTrackedFrequencyHz = -1;
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

    public void setWaveData(@Nullable int[] data, long sequence) {
        if (data == null || data.length == 0 || bitmap == null || bitmapCanvas == null) {
            return;
        }

        int[] colors = new int[data.length];
        for (int index = 0; index < data.length; index++) {
            int value = Math.max(0, Math.min(255, data[index]));
            if (value < 128) {
                colors[index] = 0xff000000 | (value << 1);
            } else if (value < 192) {
                colors[index] = 0xff0000ff | ((value - 127) << 10);
            } else {
                colors[index] = 0xff00ffff | ((value - 127) << 18);
            }
        }

        LinearGradient linearGradient = new LinearGradient(
                0,
                0,
                getWidth() * 2f,
                0,
                colors,
                null,
                Shader.TileMode.CLAMP
        );
        linearPaint.setShader(linearGradient);
        Bitmap scrolled = Bitmap.createBitmap(bitmap, 0, 0, getWidth(), Math.max(1, getHeight() - blockHeight));
        bitmapCanvas.drawBitmap(scrolled, 0, blockHeight, linearPaint);
        scrolled.recycle();
        bitmapCanvas.drawRect(0, 0, getWidth(), blockHeight, linearPaint);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blockHeight = Math.max(1, h / 186);
        bitmap = Bitmap.createBitmap(w, h, ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(0xFF000000);
        bitmapCanvas.drawRect(0, 0, w, h, blackPaint);

        markerPaint.setColor(ACCENT_COLOR);
        markerPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        labelPaint.setColor(ACCENT_COLOR);
        labelPaint.setTextSize(dpToPixel(10));
        labelPaint.setAntiAlias(true);
        labelPaint.setDither(true);
        labelBackgroundPaint.setColor(0xcc021318);
        autoTrackPaint.setColor(AUTO_TRACK_COLOR);
        autoTrackPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0, 0, null);
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

    private int dpToPixel(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
