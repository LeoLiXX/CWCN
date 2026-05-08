package org.bi9clt.cwcn.ui.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;

public final class LiveSpectrumBarsView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint finalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private SpectrumSnapshotData snapshot;

    public LiveSpectrumBarsView(Context context) {
        super(context);
        init();
    }

    public LiveSpectrumBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveSpectrumBarsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#132029"));
        gridPaint.setStrokeWidth(dp(1f));

        trackedPaint.setColor(Color.parseColor("#6ED0FF"));
        trackedPaint.setStrokeWidth(dp(1f));

        finalPaint.setColor(Color.parseColor("#F4B04F"));
        finalPaint.setStrokeWidth(dp(1.3f));

        labelPaint.setColor(Color.parseColor("#6A818A"));
        labelPaint.setTextSize(dp(8f));

        peakPaint.setColor(Color.parseColor("#F7FBFC"));
        peakPaint.setStyle(Paint.Style.FILL);
    }

    public void setSpectrumSnapshot(@Nullable SpectrumSnapshotData snapshot) {
        this.snapshot = snapshot;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft() + dp(4f);
        float top = getPaddingTop() + dp(5f);
        float right = getWidth() - getPaddingRight() - dp(4f);
        float bottom = getHeight() - getPaddingBottom() - dp(12f);
        if (right <= left || bottom <= top) {
            return;
        }

        drawGrid(canvas, left, top, right, bottom);
        if (snapshot == null || snapshot.frequenciesHz().length == 0 || snapshot.magnitudes().length == 0) {
            canvas.drawText("NO SPECTRUM", left, top + dp(12f), labelPaint);
            return;
        }

        int[] frequencies = snapshot.frequenciesHz();
        float[] magnitudes = snapshot.magnitudes();
        float floor = snapshot.noiseFloorMagnitude();
        float ceiling = Math.max(snapshot.peakMagnitude(), floor + 1f);
        float peakX = left;
        float peakY = bottom;

        for (int index = 0; index < magnitudes.length; index++) {
            float startRatio = index / (float) magnitudes.length;
            float endRatio = (index + 1) / (float) magnitudes.length;
            float centerRatio = (startRatio + endRatio) * 0.5f;
            float x1 = left + ((right - left) * startRatio);
            float x2 = left + ((right - left) * endRatio);
            float xCenter = left + ((right - left) * centerRatio);
            float intensity = normalize(magnitudes[index], floor, ceiling);
            float y = bottom - ((bottom - top) * intensity);
            barPaint.setColor(colorForIntensity(intensity));
            canvas.drawRect(x1, y, Math.max(x1 + dp(0.6f), x2 - dp(0.2f)), bottom, barPaint);
            if (frequencies[index] == snapshot.peakFrequencyHz()) {
                peakX = xCenter;
                peakY = y;
            }
        }

        canvas.drawCircle(peakX, peakY, dp(1.8f), peakPaint);
        drawMarker(canvas, snapshot.trackedToneHz(), frequencies, left, top, right, bottom, trackedPaint);
        drawMarker(canvas, snapshot.finalAdoptedToneHz(), frequencies, left, top, right, bottom, finalPaint);
        drawScale(canvas, frequencies, left, right, top, bottom);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        float width = right - left;
        float height = bottom - top;
        for (int step = 0; step <= 2; step++) {
            float y = top + ((height / 2f) * step);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        for (int step = 0; step <= 4; step++) {
            float x = left + ((width / 4f) * step);
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }
    }

    private void drawMarker(
            Canvas canvas,
            int frequencyHz,
            int[] frequencies,
            float left,
            float top,
            float right,
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

    private void drawScale(Canvas canvas, int[] frequencies, float left, float right, float top, float bottom) {
        if (frequencies.length == 0) {
            return;
        }
        String topLeft = "LIVE";
        String topRight = "TRK " + positiveOrDash(snapshot.trackedToneHz()) + "  FIN " + positiveOrDash(snapshot.finalAdoptedToneHz());
        canvas.drawText(topLeft, left, top + dp(8f), labelPaint);
        float topRightWidth = labelPaint.measureText(topRight);
        canvas.drawText(topRight, right - topRightWidth, top + dp(8f), labelPaint);

        String leftLabel = frequencies[0] + "Hz";
        String rightLabel = frequencies[frequencies.length - 1] + "Hz";
        canvas.drawText(leftLabel, left, bottom + dp(10f), labelPaint);
        float textWidth = labelPaint.measureText(rightLabel);
        canvas.drawText(rightLabel, right - textWidth, bottom + dp(10f), labelPaint);
    }

    private int colorForIntensity(float intensity) {
        if (intensity < 0.5f) {
            return blend(Color.parseColor("#0D3342"), Color.parseColor("#2CA7D6"), intensity / 0.5f);
        }
        return blend(Color.parseColor("#2CA7D6"), Color.parseColor("#A8EFFF"), (intensity - 0.5f) / 0.5f);
    }

    private float normalize(float value, float floor, float ceiling) {
        return Math.max(0f, Math.min(1f, (value - floor) / Math.max(1f, ceiling - floor)));
    }

    private int blend(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int alpha = Math.round(Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * clamped));
        int red = Math.round(Color.red(from) + ((Color.red(to) - Color.red(from)) * clamped));
        int green = Math.round(Color.green(from) + ((Color.green(to) - Color.green(from)) * clamped));
        int blue = Math.round(Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * clamped));
        return Color.argb(alpha, red, green, blue);
    }

    private String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
