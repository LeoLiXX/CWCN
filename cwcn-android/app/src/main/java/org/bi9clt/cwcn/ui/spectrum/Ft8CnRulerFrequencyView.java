package org.bi9clt.cwcn.ui.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Adapted from FT8CN RulerFrequencyView (MIT License).
 */
public final class Ft8CnRulerFrequencyView extends View {
    private static final int ACCENT_COLOR = 0xff00ffff;
    private static final int AUTO_TRACK_COLOR = 0xffffcc33;
    private static final int MINOR_TICK_STEP_HZ = 100;
    private static final int MAJOR_TICK_STEP_HZ = 500;

    private int rulerWidth;
    private int maxFrequencyHz = 3000;
    private int centerFrequencyHz = 1000;
    private int autoTrackedFrequencyHz = -1;
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusLabelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean paintsInitialized;

    public Ft8CnRulerFrequencyView(Context context) {
        super(context);
    }

    public Ft8CnRulerFrequencyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public Ft8CnRulerFrequencyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCenterFrequencyHz(int frequencyHz) {
        centerFrequencyHz = Math.max(0, Math.min(maxFrequencyHz, frequencyHz));
        postInvalidateOnAnimation();
    }

    public void setMaxFrequencyHz(int maxFrequencyHz) {
        this.maxFrequencyHz = Math.max(500, maxFrequencyHz);
        postInvalidateOnAnimation();
    }

    public void setAutoTrackedFrequencyHz(int frequencyHz) {
        autoTrackedFrequencyHz = frequencyHz > 0 ? Math.min(maxFrequencyHz, frequencyHz) : -1;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rulerWidth = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        initPaintsIfNeeded();
        drawRuler(canvas);
        super.onDraw(canvas);
    }

    private int dpToPixel(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private void drawRuler(Canvas canvas) {
        int top = 1;
        int lineWidth = Math.max(1, (int) getResources().getDisplayMetrics().density);
        int minorTickHeight = (int) (2 * getResources().getDisplayMetrics().density);
        int majorTickHeight = minorTickHeight * 3;
        Rect rect = new Rect();
        for (int frequencyHz = 0; frequencyHz <= maxFrequencyHz; frequencyHz += MINOR_TICK_STEP_HZ) {
            float ratio = maxFrequencyHz <= 0 ? 0f : frequencyHz / (float) maxFrequencyHz;
            int left = Math.round(ratio * rulerWidth);
            rect.top = top;
            rect.left = left;
            rect.right = rect.left + lineWidth;
            boolean majorTick = frequencyHz % MAJOR_TICK_STEP_HZ == 0;
            if (majorTick) {
                rect.bottom = top + majorTickHeight;
                if (frequencyHz == 0) {
                    labelPaint.setTextAlign(Paint.Align.LEFT);
                } else if (frequencyHz == maxFrequencyHz) {
                    labelPaint.setTextAlign(Paint.Align.RIGHT);
                } else {
                    labelPaint.setTextAlign(Paint.Align.CENTER);
                }
                canvas.drawText(
                        String.format(Locale.US, "%dHz", frequencyHz),
                        rect.left,
                        rect.bottom + 8 * getResources().getDisplayMetrics().density,
                        labelPaint
                );
            } else {
                rect.bottom = top + minorTickHeight;
            }
            canvas.drawRect(rect, tickPaint);
        }

        rect.top = 1;
        rect.left = 0;
        rect.right = rulerWidth;
        rect.bottom = (int) (rect.top + 2 * getResources().getDisplayMetrics().density);
        canvas.drawRect(rect, tickPaint);

        if (centerFrequencyHz > 0 && maxFrequencyHz > 0) {
            float x = (centerFrequencyHz / (float) maxFrequencyHz) * rulerWidth;
            float halfBandWidth = rulerWidth * (50f / maxFrequencyHz);
            canvas.drawRect(
                    Math.max(0f, x - halfBandWidth),
                    0f,
                    Math.min(rulerWidth, x + halfBandWidth),
                    Math.max(2f, 3f * getResources().getDisplayMetrics().density),
                    focusBandPaint
            );
            canvas.drawLine(x, 0, x, getHeight(), focusPaint);
            drawFocusLabel(canvas, x);
        }

        if (autoTrackedFrequencyHz > 0 && maxFrequencyHz > 0) {
            float x = (autoTrackedFrequencyHz / (float) maxFrequencyHz) * rulerWidth;
            float shortHeight = Math.max(6f, getHeight() * 0.38f);
            canvas.drawLine(x, 0, x, shortHeight, autoTrackPaint);
        }
    }

    private void initPaintsIfNeeded() {
        if (paintsInitialized) {
            return;
        }
        paintsInitialized = true;
        tickPaint.setColor(ACCENT_COLOR);
        labelPaint.setTextSize(dpToPixel(8));
        labelPaint.setColor(ACCENT_COLOR);
        focusPaint.setColor(ACCENT_COLOR);
        focusPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        focusBandPaint.setColor(ACCENT_COLOR);
        focusLabelBackgroundPaint.setColor(0xcc021318);
        autoTrackPaint.setColor(AUTO_TRACK_COLOR);
        autoTrackPaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
    }

    private void drawFocusLabel(Canvas canvas, float x) {
        String label = String.format(Locale.US, "%dHz", centerFrequencyHz);
        float paddingHorizontal = dpToPixel(4);
        float paddingVertical = dpToPixel(2);
        float labelWidth = labelPaint.measureText(label);
        Paint.FontMetrics fontMetrics = labelPaint.getFontMetrics();
        float textHeight = fontMetrics.bottom - fontMetrics.top;
        float rectLeft = x - (labelWidth / 2f) - paddingHorizontal;
        float rectRight = x + (labelWidth / 2f) + paddingHorizontal;
        if (rectLeft < 0f) {
            rectRight -= rectLeft;
            rectLeft = 0f;
        }
        if (rectRight > rulerWidth) {
            float overflow = rectRight - rulerWidth;
            rectLeft = Math.max(0f, rectLeft - overflow);
            rectRight = rulerWidth;
        }
        float rectTop = Math.max(0f, getHeight() - textHeight - paddingVertical * 2f);
        float rectBottom = getHeight();
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, focusLabelBackgroundPaint);
        float textBaseline = rectTop + paddingVertical - fontMetrics.top;
        canvas.drawText(label, rectLeft + paddingHorizontal, textBaseline, labelPaint);
    }
}
