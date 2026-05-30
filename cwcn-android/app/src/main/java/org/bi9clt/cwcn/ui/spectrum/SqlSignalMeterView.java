package org.bi9clt.cwcn.ui.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.R;

public final class SqlSignalMeterView extends View {
    private static final float MIN_DISPLAY_CEILING = 20f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recommendedThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private float frameLevel;
    private float toneLevel;
    private float noiseLevel;
    private float thresholdLevel;
    private float releaseLevel;
    private float recommendedThresholdLevel;
    private float displayCeiling = MIN_DISPLAY_CEILING;

    public SqlSignalMeterView(Context context) {
        super(context);
        init();
    }

    public SqlSignalMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SqlSignalMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_track));
        strokePaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_stroke));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_grid));
        gridPaint.setStrokeWidth(dp(1f));
        noisePaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_noise_fill));
        framePaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_frame_fill));
        tonePaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_tone_fill));
        thresholdPaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_threshold));
        thresholdPaint.setStrokeWidth(dp(1.6f));
        recommendedThresholdPaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_recommended));
        recommendedThresholdPaint.setStrokeWidth(dp(1.2f));
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.cwcn_sql_meter_label_text));
        labelPaint.setTextSize(dp(8f));
        labelPaint.setFakeBoldText(true);
    }

    public void setLevels(
            float frameLevel,
            float toneLevel,
            float noiseLevel,
            float thresholdLevel,
            float releaseLevel
    ) {
        setLevels(frameLevel, toneLevel, noiseLevel, thresholdLevel, releaseLevel, 0f);
    }

    public void setLevels(
            float frameLevel,
            float toneLevel,
            float noiseLevel,
            float thresholdLevel,
            float releaseLevel,
            float recommendedThresholdLevel
    ) {
        this.frameLevel = Math.max(0f, frameLevel);
        this.toneLevel = Math.max(0f, toneLevel);
        this.noiseLevel = Math.max(0f, noiseLevel);
        this.thresholdLevel = Math.max(0f, thresholdLevel);
        this.releaseLevel = Math.max(0f, releaseLevel);
        this.recommendedThresholdLevel = Math.max(0f, recommendedThresholdLevel);
        updateDisplayCeiling();
        postInvalidateOnAnimation();
    }

    private void updateDisplayCeiling() {
        float observedMax = Math.max(
                Math.max(frameLevel, toneLevel),
                Math.max(Math.max(noiseLevel, thresholdLevel), Math.max(releaseLevel, recommendedThresholdLevel))
        );
        float targetCeiling = Math.max(MIN_DISPLAY_CEILING, observedMax * 1.14f);
        if (displayCeiling <= 0f) {
            displayCeiling = targetCeiling;
            return;
        }
        float smoothing = targetCeiling >= displayCeiling ? 0.35f : 0.08f;
        displayCeiling += (targetCeiling - displayCeiling) * smoothing;
        displayCeiling = Math.max(MIN_DISPLAY_CEILING, displayCeiling);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        if (right <= left || bottom <= top) {
            return;
        }

        float labelBandHeight = dp(8f);
        trackRect.set(left, top + labelBandHeight, right, bottom);
        float radius = dp(4f);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        drawGrid(canvas);

        int saveCount = canvas.save();
        canvas.clipRect(trackRect);
        drawNoiseFill(canvas);
        drawFrameFill(canvas);
        drawToneFill(canvas);
        canvas.restoreToCount(saveCount);

        canvas.drawRoundRect(trackRect, radius, radius, strokePaint);
        drawRecommendedThreshold(canvas);
        drawThreshold(canvas, labelBandHeight);
    }

    private void drawGrid(Canvas canvas) {
        float width = trackRect.width();
        for (int step = 1; step < 4; step++) {
            float x = trackRect.left + ((width * step) / 4f);
            canvas.drawLine(x, trackRect.top, x, trackRect.bottom, gridPaint);
        }
    }

    private void drawNoiseFill(Canvas canvas) {
        float x = levelToX(noiseLevel);
        if (x <= trackRect.left) {
            return;
        }
        canvas.drawRect(trackRect.left, trackRect.top, x, trackRect.bottom, noisePaint);
    }

    private void drawFrameFill(Canvas canvas) {
        float x = levelToX(frameLevel);
        if (x <= trackRect.left) {
            return;
        }
        float inset = dp(2f);
        canvas.drawRect(trackRect.left, trackRect.top + inset, x, trackRect.bottom - inset, framePaint);
    }

    private void drawToneFill(Canvas canvas) {
        float x = levelToX(toneLevel);
        if (x <= trackRect.left) {
            return;
        }
        float centerY = trackRect.centerY();
        float halfHeight = dp(1.2f);
        canvas.drawRect(trackRect.left, centerY - halfHeight, x, centerY + halfHeight, tonePaint);
    }

    private void drawThreshold(Canvas canvas, float labelBandHeight) {
        if (thresholdLevel <= 0f) {
            return;
        }
        float x = levelToX(thresholdLevel);
        canvas.drawLine(x, trackRect.top - dp(1f), x, trackRect.bottom, thresholdPaint);

        String label = "MAN";
        float textWidth = labelPaint.measureText(label);
        float textLeft = Math.max(
                trackRect.left,
                Math.min(x - (textWidth / 2f), trackRect.right - textWidth)
        );
        float baseline = getPaddingTop() + labelBandHeight - dp(1f);
        canvas.drawText(label, textLeft, baseline, labelPaint);
    }

    private void drawRecommendedThreshold(Canvas canvas) {
        if (recommendedThresholdLevel <= 0f) {
            return;
        }
        float x = levelToX(recommendedThresholdLevel);
        canvas.drawLine(x, trackRect.top - dp(1f), x, trackRect.bottom, recommendedThresholdPaint);
        String label = "REC";
        float textWidth = labelPaint.measureText(label);
        float textLeft = Math.max(
                trackRect.left,
                Math.min(x - (textWidth / 2f), trackRect.right - textWidth)
        );
        float baseline = getPaddingTop() + dp(7f);
        canvas.drawText(label, textLeft, baseline, labelPaint);
    }

    private float levelToX(float level) {
        float normalized = Math.max(0f, Math.min(1f, level / Math.max(MIN_DISPLAY_CEILING, displayCeiling)));
        return trackRect.left + (trackRect.width() * normalized);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
