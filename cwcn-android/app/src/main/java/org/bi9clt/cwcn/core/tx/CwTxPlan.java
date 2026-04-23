package org.bi9clt.cwcn.core.tx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CwTxPlan {
    private final String sourceText;
    private final String normalizedText;
    private final String morsePreview;
    private final int wpm;
    private final int toneFrequencyHz;
    private final int dotDurationMs;
    private final List<CwTxElement> elements;
    private final int totalDurationMs;

    public CwTxPlan(
            String sourceText,
            String normalizedText,
            String morsePreview,
            int wpm,
            int toneFrequencyHz,
            int dotDurationMs,
            List<CwTxElement> elements
    ) {
        this.sourceText = sourceText == null ? "" : sourceText;
        this.normalizedText = normalizedText == null ? "" : normalizedText;
        this.morsePreview = morsePreview == null ? "" : morsePreview;
        this.wpm = wpm;
        this.toneFrequencyHz = toneFrequencyHz;
        this.dotDurationMs = dotDurationMs;
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));

        int duration = 0;
        for (CwTxElement element : elements) {
            duration += element.durationMs();
        }
        this.totalDurationMs = duration;
    }

    public String sourceText() {
        return sourceText;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public String morsePreview() {
        return morsePreview;
    }

    public int wpm() {
        return wpm;
    }

    public int toneFrequencyHz() {
        return toneFrequencyHz;
    }

    public int dotDurationMs() {
        return dotDurationMs;
    }

    public List<CwTxElement> elements() {
        return elements;
    }

    public int totalDurationMs() {
        return totalDurationMs;
    }
}
