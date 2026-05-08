package org.bi9clt.cwcn.ui.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;

import java.util.Collections;
import java.util.List;

public final class WaterfallSpectrumView extends View {
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackedMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint finalMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<SpectrumSnapshotData> history = Collections.emptyList();

    public WaterfallSpectrumView(Context context) {
        super(context);
        init();
    }

    public WaterfallSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaterfallSpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        labelPaint.setColor(Color.parseColor("#627983"));
        labelPaint.setTextSize(dp(8f));

        trackedMarkerPaint.setColor(Color.parseColor("#5CC4F3"));
        trackedMarkerPaint.setStrokeWidth(dp(0.8f));

        finalMarkerPaint.setColor(Color.parseColor("#F4B04F"));
        finalMarkerPaint.setStrokeWidth(dp(1f));
    }

    public void setSpectrumHistory(@Nullable List<SpectrumSnapshotData> history) {
        this.history = history == null ? Collections.emptyList() : history;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft() + dp(3f);
        float top = getPaddingTop() + dp(3f);
        float right = getWidth() - getPaddingRight() - dp(3f);
        float bottom = getHeight() - getPaddingBottom() - dp(10f);
        if (right <= left || bottom <= top) {
            return;
        }
        if (history == null || history.isEmpty()) {
            canvas.drawText("NO HISTORY", left, top + dp(10f), labelPaint);
            return;
        }

        int rowCount = history.size();
        float rowHeight = Math.max(dp(2f), (bottom - top) / Math.max(1, rowCount));
        for (int row = 0; row < rowCount; row++) {
            SpectrumSnapshotData snapshot = history.get(row);
            if (snapshot == null || snapshot.frequenciesHz().length == 0 || snapshot.magnitudes().length == 0) {
                continue;
            }
            float rowTop = bottom - ((row + 1) * rowHeight);
            float rowBottom = rowTop + rowHeight;
            drawHistoryRow(canvas, snapshot, left, rowTop, right, rowBottom);
        }

        SpectrumSnapshotData latest = history.get(history.size() - 1);
        if (latest != null && latest.frequenciesHz().length > 0) {
            drawMarker(canvas, latest.trackedToneHz(), latest.frequenciesHz(), left, right, top, bottom, trackedMarkerPaint);
            drawMarker(canvas, latest.finalAdoptedToneHz(), latest.frequenciesHz(), left, right, top, bottom, finalMarkerPaint);
            drawScale(canvas, latest, left, right, top, bottom);
        }
    }

    private void drawHistoryRow(
            Canvas canvas,
            SpectrumSnapshotData snapshot,
            float left,
            float rowTop,
            float right,
            float rowBottom
    ) {
        int[] frequencies = snapshot.frequenciesHz();
        float[] magnitudes = snapshot.magnitudes();
        if (frequencies.length == 0 || magnitudes.length == 0) {
            return;
        }
        float peakMagnitude = Math.max(snapshot.peakMagnitude(), snapshot.noiseFloorMagnitude() + 1f);
        for (int index = 0; index < magnitudes.length; index++) {
            float startRatio = index / (float) magnitudes.length;
            float endRatio = (index + 1) / (float) magnitudes.length;
            float x1 = left + ((right - left) * startRatio);
            float x2 = left + ((right - left) * endRatio);
            float intensity = (magnitudes[index] - snapshot.noiseFloorMagnitude()) / Math.max(1f, peakMagnitude - snapshot.noiseFloorMagnitude());
            intensity = Math.max(0f, Math.min(1f, intensity));
            cellPaint.setColor(colorForIntensity(intensity));
            canvas.drawRect(x1, rowTop, Math.max(x1 + dp(0.6f), x2), rowBottom, cellPaint);
        }
    }

    private void drawMarker(
            Canvas canvas,
            int frequencyHz,
            int[] frequencies,
            float left,
            float right,
            float top,
            float bottom,
            Paint paint
    ) {
        if (frequencyHz <= 0 || frequencies.length == 0) {
            return;
        }
        int minHz = frequencies[0];
        int maxHz = frequencies[frequencies.length - 1];
        if (maxHz <= minHz) {
            return;
        }
        float ratio = (frequencyHz - minHz) / (float) (maxHz - minHz);
        ratio = Math.max(0f, Math.min(1f, ratio));
        float x = left + ((right - left) * ratio);
        canvas.drawLine(x, top, x, bottom, paint);
    }

    private void drawScale(Canvas canvas, SpectrumSnapshotData latest, float left, float right, float top, float bottom) {
        String topLeft = "WF " + history.size();
        String topRight = latest.syntheticFallback() ? "SYNTH" : "LIVE";
        canvas.drawText(topLeft, left, top + dp(8f), labelPaint);
        float topRightWidth = labelPaint.measureText(topRight);
        canvas.drawText(topRight, right - topRightWidth, top + dp(8f), labelPaint);

        String leftLabel = latest.frequenciesHz()[0] + "Hz";
        String rightLabel = latest.frequenciesHz()[latest.frequenciesHz().length - 1] + "Hz";
        canvas.drawText(leftLabel, left, bottom + dp(8f), labelPaint);
        float rightWidth = labelPaint.measureText(rightLabel);
        canvas.drawText(rightLabel, right - rightWidth, bottom + dp(8f), labelPaint);
    }

    private int colorForIntensity(float intensity) {
        if (intensity < 0.25f) {
            return blend(Color.parseColor("#05090C"), Color.parseColor("#0D3B55"), intensity / 0.25f);
        }
        if (intensity < 0.5f) {
            return blend(Color.parseColor("#0D3B55"), Color.parseColor("#1778A8"), (intensity - 0.25f) / 0.25f);
        }
        if (intensity < 0.75f) {
            return blend(Color.parseColor("#1778A8"), Color.parseColor("#49C7ED"), (intensity - 0.5f) / 0.25f);
        }
        return blend(Color.parseColor("#49C7ED"), Color.parseColor("#F0E178"), (intensity - 0.75f) / 0.25f);
    }

    private int blend(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int alpha = Math.round(Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * clamped));
        int red = Math.round(Color.red(from) + ((Color.red(to) - Color.red(from)) * clamped));
        int green = Math.round(Color.green(from) + ((Color.green(to) - Color.green(from)) * clamped));
        int blue = Math.round(Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * clamped));
        return Color.argb(alpha, red, green, blue);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
