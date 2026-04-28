package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.qso.QsoPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CwFixtureScenario {
    private static final int DEFAULT_INTER_MESSAGE_GAP_MS = 1800;

    private final String id;
    private final String displayName;
    private final String message;
    private final List<String> messageParts;
    private final int interMessageGapMs;
    private final int wpm;
    private final int toneFrequencyHz;
    private final int toneAmplitude;
    private final int interfererToneFrequencyHz;
    private final int interfererToneAmplitude;
    private final List<ContinuousInterfererProfile> additionalInterferers;
    private final List<KeyedInterfererProfile> keyedInterferers;
    private final int noiseAmplitude;
    private final double qsbDepth;
    private final int qsbCycleMs;
    private final double toneDriftHz;
    private final double interfererToneDriftHz;
    private final double timingJitterDepth;
    private final double dotSwingDepth;
    private final int riseRampMs;
    private final int fallRampMs;
    private final List<PartTimingProfile> partTimingProfiles;
    private final int leadInMs;
    private final int tailMs;
    private final String expectedRawText;
    private final String expectedNormalizedText;
    private final List<String> expectedCallsigns;
    private final List<String> expectedHints;
    private final QsoPhase expectedPhase;
    private final String expectedRstSent;
    private final String expectedRstRcvd;
    private final String expectedFrontEndQualityCode;
    private final String notes;

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                null,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                0,
                0,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                0,
                0,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                0,
                0,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                Collections.emptyList(),
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    private CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            List<ContinuousInterfererProfile> additionalInterferers,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                additionalInterferers,
                Collections.emptyList(),
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    private CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            List<ContinuousInterfererProfile> additionalInterferers,
            List<KeyedInterfererProfile> keyedInterferers,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                additionalInterferers,
                keyedInterferers,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    private CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            List<ContinuousInterfererProfile> additionalInterferers,
            List<KeyedInterfererProfile> keyedInterferers,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double interfererToneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedRawText,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this.id = id;
        this.displayName = displayName;
        this.message = message;
        this.messageParts = new ArrayList<>(messageParts);
        this.interMessageGapMs = interMessageGapMs;
        this.wpm = wpm;
        this.toneFrequencyHz = toneFrequencyHz;
        this.toneAmplitude = toneAmplitude;
        this.interfererToneFrequencyHz = interfererToneFrequencyHz;
        this.interfererToneAmplitude = interfererToneAmplitude;
        this.additionalInterferers = sanitizeAdditionalInterferers(additionalInterferers);
        this.keyedInterferers = sanitizeKeyedInterferers(keyedInterferers);
        this.noiseAmplitude = noiseAmplitude;
        this.qsbDepth = qsbDepth;
        this.qsbCycleMs = qsbCycleMs;
        this.toneDriftHz = toneDriftHz;
        this.interfererToneDriftHz = interfererToneDriftHz;
        this.timingJitterDepth = timingJitterDepth;
        this.dotSwingDepth = dotSwingDepth;
        this.riseRampMs = Math.max(0, riseRampMs);
        this.fallRampMs = Math.max(0, fallRampMs);
        this.partTimingProfiles = sanitizePartTimingProfiles(partTimingProfiles);
        this.leadInMs = leadInMs;
        this.tailMs = tailMs;
        this.expectedRawText = expectedRawText == null ? expectedNormalizedText : expectedRawText;
        this.expectedNormalizedText = expectedNormalizedText;
        this.expectedCallsigns = new ArrayList<>(expectedCallsigns);
        this.expectedHints = new ArrayList<>(expectedHints);
        this.expectedPhase = expectedPhase;
        this.expectedRstSent = expectedRstSent;
        this.expectedRstRcvd = expectedRstRcvd;
        this.expectedFrontEndQualityCode = normalizeFrontEndQualityCode(expectedFrontEndQualityCode);
        this.notes = notes;
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int interfererToneFrequencyHz,
            int interfererToneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                0,
                0,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                0,
                0,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                0,
                0,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                timingJitterDepth,
                dotSwingDepth,
                0,
                0,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double toneDriftHz,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                0,
                0,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            int riseRampMs,
            int fallRampMs,
            List<PartTimingProfile> partTimingProfiles,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                0,
                0,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            double timingJitterDepth,
            double dotSwingDepth,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                timingJitterDepth,
                dotSwingDepth,
                0,
                0,
                new ArrayList<>(),
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                0,
                0,
                new ArrayList<>(),
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            List<String> messageParts,
            int interMessageGapMs,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                0,
                0,
                new ArrayList<>(),
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                buildSinglePartMessageList(message),
                DEFAULT_INTER_MESSAGE_GAP_MS,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                0,
                0,
                new ArrayList<>(),
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                notes
        );
    }

    public CwFixtureScenario(
            String id,
            String displayName,
            String message,
            int wpm,
            int toneFrequencyHz,
            int toneAmplitude,
            int noiseAmplitude,
            double qsbDepth,
            int qsbCycleMs,
            int leadInMs,
            int tailMs,
            String expectedNormalizedText,
            List<String> expectedCallsigns,
            List<String> expectedHints,
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String expectedFrontEndQualityCode,
            String notes
    ) {
        this(
                id,
                displayName,
                message,
                buildSinglePartMessageList(message),
                DEFAULT_INTER_MESSAGE_GAP_MS,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                0.0d,
                0.0d,
                0,
                0,
                new ArrayList<>(),
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String message() {
        return message;
    }

    public List<String> messageParts() {
        return new ArrayList<>(messageParts);
    }

    public int interMessageGapMs() {
        return interMessageGapMs;
    }

    public int wpm() {
        return wpm;
    }

    public int toneFrequencyHz() {
        return toneFrequencyHz;
    }

    public int toneAmplitude() {
        return toneAmplitude;
    }

    public int interfererToneFrequencyHz() {
        return interfererToneFrequencyHz;
    }

    public int interfererToneAmplitude() {
        return interfererToneAmplitude;
    }

    public List<ContinuousInterfererProfile> additionalInterferers() {
        return new ArrayList<>(additionalInterferers);
    }

    public List<KeyedInterfererProfile> keyedInterferers() {
        return new ArrayList<>(keyedInterferers);
    }

    public int noiseAmplitude() {
        return noiseAmplitude;
    }

    public double qsbDepth() {
        return qsbDepth;
    }

    public int qsbCycleMs() {
        return qsbCycleMs;
    }

    public double toneDriftHz() {
        return toneDriftHz;
    }

    public double interfererToneDriftHz() {
        return interfererToneDriftHz;
    }

    public CwFixtureScenario withAdditionalInterferers(List<ContinuousInterfererProfile> interferers) {
        return new CwFixtureScenario(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                interferers,
                keyedInterferers,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public CwFixtureScenario withKeyedInterferers(List<KeyedInterfererProfile> interferers) {
        return new CwFixtureScenario(
                id,
                displayName,
                message,
                messageParts,
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                additionalInterferers,
                interferers,
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                partTimingProfiles,
                leadInMs,
                tailMs,
                expectedNormalizedText,
                expectedCallsigns,
                expectedHints,
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public double timingJitterDepth() {
        return timingJitterDepth;
    }

    public double dotSwingDepth() {
        return dotSwingDepth;
    }

    public int riseRampMs() {
        return riseRampMs;
    }

    public int fallRampMs() {
        return fallRampMs;
    }

    public List<PartTimingProfile> partTimingProfiles() {
        return new ArrayList<>(partTimingProfiles);
    }

    public PartTimingProfile timingProfileForPart(int partIndex) {
        if (partIndex >= 0 && partIndex < partTimingProfiles.size()) {
            return partTimingProfiles.get(partIndex);
        }
        return PartTimingProfile.defaultProfile();
    }

    public int leadInMs() {
        return leadInMs;
    }

    public int tailMs() {
        return tailMs;
    }

    public String expectedNormalizedText() {
        return expectedNormalizedText;
    }

    public String expectedRawText() {
        return expectedRawText;
    }

    public List<String> expectedCallsigns() {
        return new ArrayList<>(expectedCallsigns);
    }

    public List<String> expectedHints() {
        return new ArrayList<>(expectedHints);
    }

    public QsoPhase expectedPhase() {
        return expectedPhase;
    }

    public String expectedRstSent() {
        return expectedRstSent;
    }

    public String expectedRstRcvd() {
        return expectedRstRcvd;
    }

    public String expectedFrontEndQualityCode() {
        return expectedFrontEndQualityCode;
    }

    public String notes() {
        return notes;
    }

    public CwFixtureScenario withExpectedRawText(String expectedRawText) {
        return new CwFixtureScenario(
                id,
                displayName,
                message,
                new ArrayList<>(messageParts),
                interMessageGapMs,
                wpm,
                toneFrequencyHz,
                toneAmplitude,
                interfererToneFrequencyHz,
                interfererToneAmplitude,
                new ArrayList<>(additionalInterferers),
                new ArrayList<>(keyedInterferers),
                noiseAmplitude,
                qsbDepth,
                qsbCycleMs,
                toneDriftHz,
                interfererToneDriftHz,
                timingJitterDepth,
                dotSwingDepth,
                riseRampMs,
                fallRampMs,
                new ArrayList<>(partTimingProfiles),
                leadInMs,
                tailMs,
                expectedRawText,
                expectedNormalizedText,
                new ArrayList<>(expectedCallsigns),
                new ArrayList<>(expectedHints),
                expectedPhase,
                expectedRstSent,
                expectedRstRcvd,
                expectedFrontEndQualityCode,
                notes
        );
    }

    public String timingProfileSummary() {
        ArrayList<String> parts = new ArrayList<>();
        if (timingJitterDepth > 0.0d) {
            parts.add("global jitter " + Math.round(timingJitterDepth * 100.0d) + "%");
        }
        if (dotSwingDepth > 0.0d) {
            parts.add("global dot swing " + Math.round(dotSwingDepth * 100.0d) + "%");
        }
        if (Math.abs(toneDriftHz) > 0.0d) {
            parts.add("tone drift " + trimDouble(toneDriftHz) + "Hz");
        }
        if (Math.abs(interfererToneDriftHz) > 0.0d) {
            parts.add("interferer drift " + trimDouble(interfererToneDriftHz) + "Hz");
        }
        for (ContinuousInterfererProfile interferer : additionalInterferers) {
            parts.add("extra interferer " + interferer.summaryLabel());
        }
        for (KeyedInterfererProfile interferer : keyedInterferers) {
            parts.add("keyed interferer " + interferer.summaryLabel());
        }
        if (riseRampMs > 0 || fallRampMs > 0) {
            parts.add("edge ramp " + riseRampMs + "/" + fallRampMs + "ms");
        }
        for (int index = 0; index < partTimingProfiles.size(); index++) {
            PartTimingProfile profile = partTimingProfiles.get(index);
            if (!profile.isDefault()) {
                parts.add("P" + (index + 1) + " " + profile.summaryLabel());
            }
        }
        if (parts.isEmpty()) {
            return "machine-stable timing";
        }
        return String.join(", ", parts);
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static List<String> buildSinglePartMessageList(String message) {
        ArrayList<String> parts = new ArrayList<>();
        if (message != null && !message.trim().isEmpty()) {
            parts.add(message);
        }
        return parts;
    }

    private static List<PartTimingProfile> sanitizePartTimingProfiles(List<PartTimingProfile> profiles) {
        ArrayList<PartTimingProfile> sanitized = new ArrayList<>();
        if (profiles == null) {
            return sanitized;
        }
        for (PartTimingProfile profile : profiles) {
            sanitized.add(profile == null ? PartTimingProfile.defaultProfile() : profile);
        }
        return sanitized;
    }

    private static List<ContinuousInterfererProfile> sanitizeAdditionalInterferers(
            List<ContinuousInterfererProfile> interferers
    ) {
        ArrayList<ContinuousInterfererProfile> sanitized = new ArrayList<>();
        if (interferers == null) {
            return sanitized;
        }
        for (ContinuousInterfererProfile interferer : interferers) {
            if (interferer == null) {
                continue;
            }
            if (interferer.toneFrequencyHz() <= 0 || interferer.toneAmplitude() <= 0) {
                continue;
            }
            sanitized.add(interferer);
        }
        return sanitized;
    }

    private static List<KeyedInterfererProfile> sanitizeKeyedInterferers(
            List<KeyedInterfererProfile> interferers
    ) {
        ArrayList<KeyedInterfererProfile> sanitized = new ArrayList<>();
        if (interferers == null) {
            return sanitized;
        }
        for (KeyedInterfererProfile interferer : interferers) {
            if (interferer == null) {
                continue;
            }
            if (interferer.toneFrequencyHz() <= 0
                    || interferer.toneAmplitude() <= 0
                    || interferer.wpm() <= 0
                    || !interferer.hasDecodableMessage()) {
                continue;
            }
            sanitized.add(interferer);
        }
        return sanitized;
    }

    private static String trimDouble(double value) {
        return String.format(Locale.US, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static String normalizeFrontEndQualityCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        return normalized.isEmpty() ? null : normalized;
    }

    public static final class ContinuousInterfererProfile {
        private final int toneFrequencyHz;
        private final int toneAmplitude;
        private final double toneDriftHz;
        private final int burstOnMs;
        private final int burstOffMs;
        private final int burstOffsetMs;
        private final double burstWobbleDepth;
        private final int burstWobbleCycleMs;

        public ContinuousInterfererProfile(int toneFrequencyHz, int toneAmplitude) {
            this(toneFrequencyHz, toneAmplitude, 0.0d, 0, 0, 0, 0.0d, 0);
        }

        public ContinuousInterfererProfile(int toneFrequencyHz, int toneAmplitude, double toneDriftHz) {
            this(toneFrequencyHz, toneAmplitude, toneDriftHz, 0, 0, 0, 0.0d, 0);
        }

        public ContinuousInterfererProfile(
                int toneFrequencyHz,
                int toneAmplitude,
                double toneDriftHz,
                int burstOnMs,
                int burstOffMs,
                int burstOffsetMs
        ) {
            this(
                    toneFrequencyHz,
                    toneAmplitude,
                    toneDriftHz,
                    burstOnMs,
                    burstOffMs,
                    burstOffsetMs,
                    0.0d,
                    0
            );
        }

        public ContinuousInterfererProfile(
                int toneFrequencyHz,
                int toneAmplitude,
                double toneDriftHz,
                int burstOnMs,
                int burstOffMs,
                int burstOffsetMs,
                double burstWobbleDepth,
                int burstWobbleCycleMs
        ) {
            this.toneFrequencyHz = toneFrequencyHz;
            this.toneAmplitude = toneAmplitude;
            this.toneDriftHz = toneDriftHz;
            this.burstOnMs = Math.max(0, burstOnMs);
            this.burstOffMs = Math.max(0, burstOffMs);
            this.burstOffsetMs = Math.max(0, burstOffsetMs);
            this.burstWobbleDepth = Math.max(0.0d, Math.min(0.45d, burstWobbleDepth));
            this.burstWobbleCycleMs = Math.max(0, burstWobbleCycleMs);
        }

        public int toneFrequencyHz() {
            return toneFrequencyHz;
        }

        public int toneAmplitude() {
            return toneAmplitude;
        }

        public double toneDriftHz() {
            return toneDriftHz;
        }

        public int burstOnMs() {
            return burstOnMs;
        }

        public int burstOffMs() {
            return burstOffMs;
        }

        public int burstOffsetMs() {
            return burstOffsetMs;
        }

        public double burstWobbleDepth() {
            return burstWobbleDepth;
        }

        public int burstWobbleCycleMs() {
            return burstWobbleCycleMs;
        }

        public boolean isBursting() {
            return burstOnMs > 0 && burstOffMs > 0;
        }

        public boolean hasBurstWobble() {
            return isBursting() && burstWobbleDepth > 0.0d && burstWobbleCycleMs > 0;
        }

        public String summaryLabel() {
            StringBuilder builder = new StringBuilder()
                    .append(toneFrequencyHz)
                    .append("Hz @ ")
                    .append(toneAmplitude);
            if (Math.abs(toneDriftHz) > 0.0d) {
                builder.append(" drift ")
                        .append(trimDouble(toneDriftHz))
                        .append("Hz");
            }
            if (isBursting()) {
                builder.append(" burst ")
                        .append(burstOnMs)
                        .append("/")
                        .append(burstOffMs)
                        .append("ms");
                if (burstOffsetMs > 0) {
                    builder.append(" offset ")
                            .append(burstOffsetMs)
                            .append("ms");
                }
                if (hasBurstWobble()) {
                    builder.append(" wobble ")
                            .append(Math.round(burstWobbleDepth * 100.0d))
                            .append("%/")
                            .append(burstWobbleCycleMs)
                            .append("ms");
                }
            }
            return builder.toString();
        }
    }

    public static final class KeyedInterfererProfile {
        private final String message;
        private final List<String> messageParts;
        private final int interMessageGapMs;
        private final int wpm;
        private final int toneFrequencyHz;
        private final int toneAmplitude;
        private final int startOffsetMs;
        private final double toneDriftHz;
        private final int riseRampMs;
        private final int fallRampMs;

        public KeyedInterfererProfile(
                String message,
                int wpm,
                int toneFrequencyHz,
                int toneAmplitude,
                int startOffsetMs
        ) {
            this(
                    message,
                    Collections.singletonList(message),
                    DEFAULT_INTER_MESSAGE_GAP_MS,
                    wpm,
                    toneFrequencyHz,
                    toneAmplitude,
                    startOffsetMs,
                    0.0d,
                    3,
                    3
            );
        }

        public KeyedInterfererProfile(
                String message,
                List<String> messageParts,
                int interMessageGapMs,
                int wpm,
                int toneFrequencyHz,
                int toneAmplitude,
                int startOffsetMs,
                double toneDriftHz,
                int riseRampMs,
                int fallRampMs
        ) {
            this.message = message == null ? "" : message;
            this.messageParts = sanitizeMessageParts(messageParts, this.message);
            this.interMessageGapMs = Math.max(0, interMessageGapMs);
            this.wpm = Math.max(1, wpm);
            this.toneFrequencyHz = toneFrequencyHz;
            this.toneAmplitude = toneAmplitude;
            this.startOffsetMs = Math.max(0, startOffsetMs);
            this.toneDriftHz = toneDriftHz;
            this.riseRampMs = Math.max(0, riseRampMs);
            this.fallRampMs = Math.max(0, fallRampMs);
        }

        public String message() {
            return message;
        }

        public List<String> messageParts() {
            return new ArrayList<>(messageParts);
        }

        public int interMessageGapMs() {
            return interMessageGapMs;
        }

        public int wpm() {
            return wpm;
        }

        public int toneFrequencyHz() {
            return toneFrequencyHz;
        }

        public int toneAmplitude() {
            return toneAmplitude;
        }

        public int startOffsetMs() {
            return startOffsetMs;
        }

        public double toneDriftHz() {
            return toneDriftHz;
        }

        public int riseRampMs() {
            return riseRampMs;
        }

        public int fallRampMs() {
            return fallRampMs;
        }

        public boolean hasDecodableMessage() {
            for (String part : messageParts) {
                if (part != null && !part.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        public String summaryLabel() {
            StringBuilder builder = new StringBuilder()
                    .append(toneFrequencyHz)
                    .append("Hz keyed @ ")
                    .append(toneAmplitude)
                    .append(" ")
                    .append(wpm)
                    .append("WPM");
            if (startOffsetMs > 0) {
                builder.append(" offset ")
                        .append(startOffsetMs)
                        .append("ms");
            }
            if (Math.abs(toneDriftHz) > 0.0d) {
                builder.append(" drift ")
                        .append(trimDouble(toneDriftHz))
                        .append("Hz");
            }
            if (!message.isEmpty()) {
                builder.append(" \"")
                        .append(message.length() > 18 ? message.substring(0, 18) + "…" : message)
                        .append("\"");
            }
            return builder.toString();
        }

        private static List<String> sanitizeMessageParts(List<String> parts, String fallbackMessage) {
            ArrayList<String> sanitized = new ArrayList<>();
            if (parts != null) {
                for (String part : parts) {
                    if (part != null && !part.trim().isEmpty()) {
                        sanitized.add(part);
                    }
                }
            }
            if (sanitized.isEmpty() && fallbackMessage != null && !fallbackMessage.trim().isEmpty()) {
                sanitized.add(fallbackMessage);
            }
            return sanitized;
        }
    }

    public static final class PartTimingProfile {
        private final double wpmScale;
        private final double endWpmScale;
        private final Double timingJitterDepthOverride;
        private final Double dotSwingDepthOverride;
        private final double dotJitterBoost;
        private final double letterGapScale;
        private final double wordGapScale;
        private final double handoffGapScale;
        private final int extraPauseEveryCharacters;
        private final double extraPauseDotUnits;
        private final List<Integer> extraPauseCharacterOffsets;
        private final Integer toneFrequencyOverrideHz;
        private final Integer endToneFrequencyOverrideHz;

        public PartTimingProfile(
                double wpmScale,
                Double timingJitterDepthOverride,
                Double dotSwingDepthOverride,
                double dotJitterBoost,
                double letterGapScale,
                double wordGapScale,
                int extraPauseEveryCharacters,
                double extraPauseDotUnits
        ) {
            this(
                    wpmScale,
                    wpmScale,
                    timingJitterDepthOverride,
                    dotSwingDepthOverride,
                    dotJitterBoost,
                    letterGapScale,
                    wordGapScale,
                    1.0d,
                    extraPauseEveryCharacters,
                    extraPauseDotUnits,
                    Collections.emptyList(),
                    null,
                    null
            );
        }

        public PartTimingProfile(
                double wpmScale,
                double endWpmScale,
                Double timingJitterDepthOverride,
                Double dotSwingDepthOverride,
                double dotJitterBoost,
                double letterGapScale,
                double wordGapScale,
                double handoffGapScale,
                int extraPauseEveryCharacters,
                double extraPauseDotUnits
        ) {
            this(
                    wpmScale,
                    endWpmScale,
                    timingJitterDepthOverride,
                    dotSwingDepthOverride,
                    dotJitterBoost,
                    letterGapScale,
                    wordGapScale,
                    handoffGapScale,
                    extraPauseEveryCharacters,
                    extraPauseDotUnits,
                    Collections.emptyList(),
                    null,
                    null
            );
        }

        public PartTimingProfile(
                double wpmScale,
                double endWpmScale,
                Double timingJitterDepthOverride,
                Double dotSwingDepthOverride,
                double dotJitterBoost,
                double letterGapScale,
                double wordGapScale,
                double handoffGapScale,
                int extraPauseEveryCharacters,
                double extraPauseDotUnits,
                List<Integer> extraPauseCharacterOffsets
        ) {
            this(
                    wpmScale,
                    endWpmScale,
                    timingJitterDepthOverride,
                    dotSwingDepthOverride,
                    dotJitterBoost,
                    letterGapScale,
                    wordGapScale,
                    handoffGapScale,
                    extraPauseEveryCharacters,
                    extraPauseDotUnits,
                    extraPauseCharacterOffsets,
                    null,
                    null
            );
        }

        public PartTimingProfile(
                double wpmScale,
                double endWpmScale,
                Double timingJitterDepthOverride,
                Double dotSwingDepthOverride,
                double dotJitterBoost,
                double letterGapScale,
                double wordGapScale,
                double handoffGapScale,
                int extraPauseEveryCharacters,
                double extraPauseDotUnits,
                List<Integer> extraPauseCharacterOffsets,
                Integer toneFrequencyOverrideHz,
                Integer endToneFrequencyOverrideHz
        ) {
            this.wpmScale = wpmScale;
            this.endWpmScale = endWpmScale;
            this.timingJitterDepthOverride = timingJitterDepthOverride;
            this.dotSwingDepthOverride = dotSwingDepthOverride;
            this.dotJitterBoost = dotJitterBoost;
            this.letterGapScale = letterGapScale;
            this.wordGapScale = wordGapScale;
            this.handoffGapScale = handoffGapScale;
            this.extraPauseEveryCharacters = extraPauseEveryCharacters;
            this.extraPauseDotUnits = extraPauseDotUnits;
            this.extraPauseCharacterOffsets = sanitizePauseOffsets(extraPauseCharacterOffsets);
            this.toneFrequencyOverrideHz = sanitizeToneFrequencyOverride(toneFrequencyOverrideHz);
            this.endToneFrequencyOverrideHz = sanitizeToneFrequencyOverride(
                    endToneFrequencyOverrideHz == null ? toneFrequencyOverrideHz : endToneFrequencyOverrideHz
            );
        }

        public static PartTimingProfile defaultProfile() {
            return new PartTimingProfile(
                    1.0d,
                    1.0d,
                    null,
                    null,
                    0.0d,
                    1.0d,
                    1.0d,
                    1.0d,
                    0,
                    0.0d,
                    Collections.emptyList(),
                    null,
                    null
            );
        }

        public PartTimingProfile withToneFrequencyHz(int toneFrequencyHz) {
            return withToneSweepHz(toneFrequencyHz, toneFrequencyHz);
        }

        public PartTimingProfile withToneSweepHz(int toneFrequencyHz, int endToneFrequencyHz) {
            return new PartTimingProfile(
                    wpmScale,
                    endWpmScale,
                    timingJitterDepthOverride,
                    dotSwingDepthOverride,
                    dotJitterBoost,
                    letterGapScale,
                    wordGapScale,
                    handoffGapScale,
                    extraPauseEveryCharacters,
                    extraPauseDotUnits,
                    extraPauseCharacterOffsets,
                    toneFrequencyHz,
                    endToneFrequencyHz
            );
        }

        public double wpmScale() {
            return wpmScale;
        }

        public double endWpmScale() {
            return endWpmScale;
        }

        public double effectiveTimingJitterDepth(double scenarioDefault) {
            return timingJitterDepthOverride == null ? scenarioDefault : timingJitterDepthOverride;
        }

        public double effectiveDotSwingDepth(double scenarioDefault) {
            return dotSwingDepthOverride == null ? scenarioDefault : dotSwingDepthOverride;
        }

        public double dotJitterBoost() {
            return dotJitterBoost;
        }

        public double letterGapScale() {
            return letterGapScale;
        }

        public double wordGapScale() {
            return wordGapScale;
        }

        public double handoffGapScale() {
            return handoffGapScale;
        }

        public int extraPauseEveryCharacters() {
            return extraPauseEveryCharacters;
        }

        public double extraPauseDotUnits() {
            return extraPauseDotUnits;
        }

        public List<Integer> extraPauseCharacterOffsets() {
            return new ArrayList<>(extraPauseCharacterOffsets);
        }

        public Integer toneFrequencyOverrideHz() {
            return toneFrequencyOverrideHz;
        }

        public Integer endToneFrequencyOverrideHz() {
            return endToneFrequencyOverrideHz;
        }

        public boolean hasToneOverride() {
            return toneFrequencyOverrideHz != null;
        }

        public boolean isDefault() {
            return approximatelyEquals(wpmScale, 1.0d)
                    && approximatelyEquals(endWpmScale, 1.0d)
                    && timingJitterDepthOverride == null
                    && dotSwingDepthOverride == null
                    && approximatelyEquals(dotJitterBoost, 0.0d)
                    && approximatelyEquals(letterGapScale, 1.0d)
                    && approximatelyEquals(wordGapScale, 1.0d)
                    && approximatelyEquals(handoffGapScale, 1.0d)
                    && extraPauseEveryCharacters <= 0
                    && approximatelyEquals(extraPauseDotUnits, 0.0d)
                    && extraPauseCharacterOffsets.isEmpty()
                    && toneFrequencyOverrideHz == null
                    && endToneFrequencyOverrideHz == null;
        }

        public String summaryLabel() {
            ArrayList<String> parts = new ArrayList<>();
            if (!approximatelyEquals(wpmScale, 1.0d)) {
                parts.add(Math.round(wpmScale * 100.0d) + "% WPM");
            }
            if (!approximatelyEquals(endWpmScale, wpmScale)) {
                parts.add("drift to " + Math.round(endWpmScale * 100.0d) + "%");
            }
            if (timingJitterDepthOverride != null) {
                parts.add("jitter " + Math.round(timingJitterDepthOverride * 100.0d) + "%");
            }
            if (dotSwingDepthOverride != null) {
                parts.add("dot swing " + Math.round(dotSwingDepthOverride * 100.0d) + "%");
            }
            if (!approximatelyEquals(dotJitterBoost, 0.0d)) {
                parts.add("dot boost " + Math.round(dotJitterBoost * 100.0d) + "%");
            }
            if (!approximatelyEquals(letterGapScale, 1.0d)) {
                parts.add("letter gap x" + trimDouble(letterGapScale));
            }
            if (!approximatelyEquals(wordGapScale, 1.0d)) {
                parts.add("word gap x" + trimDouble(wordGapScale));
            }
            if (!approximatelyEquals(handoffGapScale, 1.0d)) {
                parts.add("handoff gap x" + trimDouble(handoffGapScale));
            }
            if (extraPauseEveryCharacters > 0 && extraPauseDotUnits > 0.0d) {
                parts.add("pause +" + trimDouble(extraPauseDotUnits) + " dot / " + extraPauseEveryCharacters + " char");
            }
            if (!extraPauseCharacterOffsets.isEmpty() && extraPauseDotUnits > 0.0d) {
                parts.add("pause @" + joinPauseOffsets(extraPauseCharacterOffsets) + " +" + trimDouble(extraPauseDotUnits) + " dot");
            }
            if (toneFrequencyOverrideHz != null) {
                if (endToneFrequencyOverrideHz != null && !toneFrequencyOverrideHz.equals(endToneFrequencyOverrideHz)) {
                    parts.add("tone " + toneFrequencyOverrideHz + "->" + endToneFrequencyOverrideHz + "Hz");
                } else {
                    parts.add("tone " + toneFrequencyOverrideHz + "Hz");
                }
            }
            return parts.isEmpty() ? "default" : String.join(", ", parts);
        }

        private List<Integer> sanitizePauseOffsets(List<Integer> offsets) {
            if (offsets == null || offsets.isEmpty()) {
                return Collections.emptyList();
            }
            ArrayList<Integer> cleaned = new ArrayList<>();
            for (Integer offset : offsets) {
                if (offset == null || offset <= 0 || cleaned.contains(offset)) {
                    continue;
                }
                cleaned.add(offset);
            }
            return cleaned.isEmpty() ? Collections.emptyList() : cleaned;
        }

        private String joinPauseOffsets(List<Integer> offsets) {
            ArrayList<String> labels = new ArrayList<>();
            for (Integer offset : offsets) {
                labels.add(String.valueOf(offset));
            }
            return String.join(",", labels);
        }

        private Integer sanitizeToneFrequencyOverride(Integer toneFrequencyHz) {
            if (toneFrequencyHz == null || toneFrequencyHz <= 0) {
                return null;
            }
            return toneFrequencyHz;
        }

        private static boolean approximatelyEquals(double left, double right) {
            return Math.abs(left - right) < 0.0001d;
        }

        private static String trimDouble(double value) {
            return CwFixtureScenario.trimDouble(value);
        }
    }
}
