package org.bi9clt.cwcn.ui.debug;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public final class AudioSpectrumView extends View {
    private static final int[][] MARKER_ROWS = new int[][]{
            {0, 1},
            {2, 3},
            {4, 5}
    };

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint preferredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hypothesisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hypothesisGuardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint preferredWinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wideWinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint acquisitionWinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint finalAdoptedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint decodeRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint decodeRefBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerLabelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();

    private AudioSpectrumSnapshot snapshot;

    public AudioSpectrumView(Context context) {
        super(context);
        init();
    }

    public AudioSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioSpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(Color.parseColor("#2E3B4E"));
        gridPaint.setStrokeWidth(dp(1));

        curvePaint.setColor(Color.parseColor("#63D1FF"));
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(dp(2));

        preferredPaint.setColor(Color.parseColor("#B8C0CC"));
        preferredPaint.setStrokeWidth(dp(1));

        trackedPaint.setColor(Color.parseColor("#F4B04F"));
        trackedPaint.setStrokeWidth(dp(2));

        hypothesisPaint.setColor(Color.parseColor("#D96CFF"));
        hypothesisPaint.setStrokeWidth(dp(1.5f));

        hypothesisGuardPaint.setColor(Color.parseColor("#1DE9B6"));
        hypothesisGuardPaint.setStrokeWidth(dp(2.5f));

        preferredWinnerPaint.setColor(Color.parseColor("#4FC3F7"));
        preferredWinnerPaint.setStrokeWidth(dp(1.5f));

        wideWinnerPaint.setColor(Color.parseColor("#EF5350"));
        wideWinnerPaint.setStrokeWidth(dp(1.5f));

        acquisitionWinnerPaint.setColor(Color.parseColor("#81C784"));
        acquisitionWinnerPaint.setStrokeWidth(dp(2));

        finalAdoptedPaint.setColor(Color.parseColor("#FFD54F"));
        finalAdoptedPaint.setStrokeWidth(dp(2.5f));

        peakPaint.setColor(Color.parseColor("#EAF6FF"));
        peakPaint.setStyle(Paint.Style.FILL);

        noisePaint.setColor(Color.parseColor("#5FBF7F"));
        noisePaint.setStrokeWidth(dp(1.5f));

        textPaint.setColor(Color.parseColor("#C9D4E3"));
        textPaint.setTextSize(dp(11));

        decodeRefPaint.setColor(Color.parseColor("#F4B04F"));
        decodeRefPaint.setTextSize(dp(10));
        decodeRefPaint.setFakeBoldText(true);

        decodeRefBackgroundPaint.setStyle(Paint.Style.FILL);
        decodeRefBackgroundPaint.setColor(Color.parseColor("#33F4B04F"));

        markerLabelPaint.setColor(Color.parseColor("#F4F7FB"));
        markerLabelPaint.setTextSize(dp(9));
        markerLabelPaint.setFakeBoldText(true);

        markerLabelBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setSpectrumSnapshot(@Nullable AudioSpectrumSnapshot snapshot) {
        this.snapshot = snapshot;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft() + dp(8);
        float top = getPaddingTop() + dp(10);
        float right = getWidth() - getPaddingRight() - dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(18);

        if (right <= left || bottom <= top) {
            return;
        }

        drawGrid(canvas, left, top, right, bottom);
        if (snapshot == null || snapshot.frequenciesHz().length == 0 || snapshot.magnitudes().length == 0) {
            canvas.drawText("Waiting for audio spectrum...", left, top + dp(16), textPaint);
            return;
        }

        float maxMagnitude = Math.max(snapshot.peakMagnitude(), snapshot.noiseFloorMagnitude() * 1.8f);
        if (maxMagnitude <= 0.0f) {
            maxMagnitude = 1.0f;
        }

        drawNoiseLine(canvas, left, top, right, bottom, maxMagnitude);
        drawCurve(canvas, left, top, right, bottom, maxMagnitude);
        drawMarkers(canvas, left, top, right, bottom);
        drawDecodeReferenceBadge(canvas, left, top);
        drawLegend(canvas, left, top, right, bottom);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        float width = right - left;
        float height = bottom - top;
        for (int step = 0; step <= 4; step++) {
            float y = top + ((height / 4.0f) * step);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        for (int step = 0; step <= 6; step++) {
            float x = left + ((width / 6.0f) * step);
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }
    }

    private void drawNoiseLine(Canvas canvas, float left, float top, float right, float bottom, float maxMagnitude) {
        float ratio = Math.max(0.0f, Math.min(1.0f, snapshot.noiseFloorMagnitude() / maxMagnitude));
        float y = bottom - ((bottom - top) * ratio);
        canvas.drawLine(left, y, right, y, noisePaint);
    }

    private void drawMarkers(Canvas canvas, float left, float top, float right, float bottom) {
        int[] frequenciesHz = snapshot.frequenciesHz();
        int minHz = frequenciesHz[0];
        int maxHz = frequenciesHz[frequenciesHz.length - 1];
        drawMarker(canvas, snapshot.preferredToneHz(), "PREF", minHz, maxHz, left, top, right, bottom, preferredPaint, 0);
        drawMarker(canvas, snapshot.preferredWindowWinnerToneHz(), "PW", minHz, maxHz, left, top, right, bottom, preferredWinnerPaint, 1);
        drawMarker(canvas, snapshot.wideScanWinnerToneHz(), "WS", minHz, maxHz, left, top, right, bottom, wideWinnerPaint, 2);
        drawMarker(canvas, snapshot.acquisitionWinnerToneHz(), "AQ", minHz, maxHz, left, top, right, bottom, acquisitionWinnerPaint, 3);
        drawMarker(canvas, snapshot.finalAdoptedToneHz(), "AD", minHz, maxHz, left, top, right, bottom, finalAdoptedPaint, 4);
        drawMarker(canvas, snapshot.hypothesisToneHz(), "HYP", minHz, maxHz, left, top, right, bottom, hypothesisPaint, 5);
        if (snapshot.hypothesisGuardEnabled() && snapshot.hypothesisGuardAppliedToneHz() > 0) {
            drawMarker(
                    canvas,
                    snapshot.hypothesisGuardAppliedToneHz(),
                    snapshot.hypothesisGuardApplied() ? "HG!" : "HG",
                    minHz,
                    maxHz,
                    left,
                    top,
                    right,
                    bottom,
                    hypothesisGuardPaint,
                    6
            );
        }
        drawMarker(canvas, snapshot.trackedToneHz(), "TRK", minHz, maxHz, left, top, right, bottom, trackedPaint, 7);
    }

    private void drawMarker(
            Canvas canvas,
            int frequencyHz,
            String shortLabel,
            int minHz,
            int maxHz,
            float left,
            float top,
            float right,
            float bottom,
            Paint paint,
            int markerIndex
    ) {
        if (frequencyHz <= 0 || maxHz <= minHz) {
            return;
        }
        float ratio = (frequencyHz - minHz) / (float) (maxHz - minHz);
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        float x = left + ((right - left) * ratio);
        canvas.drawLine(x, top, x, bottom, paint);
        drawMarkerLabel(canvas, x, top, bottom, shortLabel + " " + frequencyHz, paint, markerIndex);
    }

    private void drawMarkerLabel(
            Canvas canvas,
            float x,
            float top,
            float bottom,
            String label,
            Paint linePaint,
            int markerIndex
    ) {
        if (label == null || label.isEmpty()) {
            return;
        }
        int rowGroup = markerIndex % MARKER_ROWS.length;
        int rowOffset = markerIndex / MARKER_ROWS.length;
        float labelHeight = dp(12);
        float labelSpacing = dp(2);
        float labelTop = top + dp(4) + ((rowGroup + (rowOffset * MARKER_ROWS.length)) * (labelHeight + labelSpacing));
        float textWidth = markerLabelPaint.measureText(label);
        float labelLeft = Math.max(dp(2), Math.min(x - (textWidth / 2.0f) - dp(4), getWidth() - textWidth - dp(10)));
        float labelRight = labelLeft + textWidth + dp(8);
        float labelBottom = Math.min(bottom - dp(2), labelTop + labelHeight);
        markerLabelBackgroundPaint.setColor(adjustAlpha(linePaint.getColor(), 0.28f));
        canvas.drawRoundRect(labelLeft, labelTop, labelRight, labelBottom, dp(4), dp(4), markerLabelBackgroundPaint);
        canvas.drawText(label, labelLeft + dp(4), labelBottom - dp(3), markerLabelPaint);
    }

    private void drawCurve(Canvas canvas, float left, float top, float right, float bottom, float maxMagnitude) {
        curvePath.reset();
        int[] frequenciesHz = snapshot.frequenciesHz();
        float[] magnitudes = snapshot.magnitudes();
        int minHz = frequenciesHz[0];
        int maxHz = frequenciesHz[frequenciesHz.length - 1];
        float peakX = left;
        float peakY = bottom;

        for (int index = 0; index < frequenciesHz.length; index++) {
            float xRatio = (frequenciesHz[index] - minHz) / (float) (maxHz - minHz);
            float yRatio = Math.max(0.0f, Math.min(1.0f, magnitudes[index] / maxMagnitude));
            float x = left + ((right - left) * xRatio);
            float y = bottom - ((bottom - top) * yRatio);
            if (index == 0) {
                curvePath.moveTo(x, y);
            } else {
                curvePath.lineTo(x, y);
            }
            if (frequenciesHz[index] == snapshot.peakFrequencyHz()) {
                peakX = x;
                peakY = y;
            }
        }

        canvas.drawPath(curvePath, curvePaint);
        canvas.drawCircle(peakX, peakY, dp(3), peakPaint);
    }

    private void drawDecodeReferenceBadge(Canvas canvas, float left, float top) {
        String label = "Decode Ref: TRK";
        float textWidth = decodeRefPaint.measureText(label);
        float badgeLeft = left + dp(6);
        float badgeTop = top + dp(6);
        float badgeRight = badgeLeft + textWidth + dp(10);
        float badgeBottom = badgeTop + dp(16);
        canvas.drawRoundRect(
                badgeLeft,
                badgeTop,
                badgeRight,
                badgeBottom,
                dp(5),
                dp(5),
                decodeRefBackgroundPaint
        );
        canvas.drawText(label, badgeLeft + dp(5), badgeBottom - dp(4), decodeRefPaint);
    }

    private void drawLegend(Canvas canvas, float left, float top, float right, float bottom) {
        String leftLabel = snapshot.frequenciesHz()[0] + " Hz";
        String rightLabel = snapshot.frequenciesHz()[snapshot.frequenciesHz().length - 1] + " Hz";
        String peakLabel = "Peak " + snapshot.peakFrequencyHz() + " Hz";
        String noiseLabel = "Noise " + Math.round(snapshot.noiseFloorMagnitude())
                + " | " + snapshot.acquisitionWinnerSource()
                + " -> " + snapshot.finalAdoptedSource();
        if (snapshot.hypothesisGuardEnabled()) {
            noiseLabel += " | HG " + snapshot.hypothesisGuardAppliedToneHz() + " Hz";
        }
        canvas.drawText(leftLabel, left, bottom + dp(14), textPaint);
        float rightLabelWidth = textPaint.measureText(rightLabel);
        canvas.drawText(rightLabel, right - rightLabelWidth, bottom + dp(14), textPaint);
        canvas.drawText(peakLabel, left, top - dp(2), textPaint);
        float noiseLabelWidth = textPaint.measureText(noiseLabel);
        canvas.drawText(noiseLabel, right - noiseLabelWidth, top - dp(2), textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.max(0, Math.min(255, Math.round(Color.alpha(color) * factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
