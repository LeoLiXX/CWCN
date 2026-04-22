package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwFixtureScenarioTest {
    @Test
    public void timingProfileSummaryIncludesPerPartOverrides() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ / TU",
                Arrays.asList("CQ", "TU"),
                1800,
                18,
                650,
                18000,
                500,
                0.10d,
                2000,
                0.12d,
                0.08d,
                Arrays.asList(
                        new CwFixtureScenario.PartTimingProfile(0.92d, 1.08d, 0.30d, 0.20d, 0.06d, 1.20d, 1.40d, 1.80d, 4, 2.0d),
                        new CwFixtureScenario.PartTimingProfile(1.08d, 1.12d, 0.06d, 0.02d, 0.00d, 0.90d, 0.95d, 1.10d, 0, 0.0d)
                ),
                250,
                450,
                "CQ TU",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        String summary = scenario.timingProfileSummary();

        assertTrue(summary.contains("global jitter 12%"));
        assertTrue(summary.contains("global dot swing 8%"));
        assertTrue(summary.contains("P1"));
        assertTrue(summary.contains("P2"));
        assertTrue(summary.contains("drift to 108%"));
        assertTrue(summary.contains("handoff gap x1.8"));
        assertTrue(summary.contains("pause +2 dot / 4 char"));
    }

    @Test
    public void missingPartProfileFallsBackToDefault() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ / K",
                Arrays.asList("CQ", "K"),
                1800,
                18,
                650,
                18000,
                500,
                0.0d,
                0,
                0.0d,
                0.0d,
                Collections.singletonList(
                        new CwFixtureScenario.PartTimingProfile(0.95d, 0.20d, null, 0.05d, 1.10d, 1.30d, 3, 1.5d)
                ),
                250,
                450,
                "CQ K",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        assertEquals(0.95d, scenario.timingProfileForPart(0).wpmScale(), 0.0001d);
        assertTrue(scenario.timingProfileForPart(1).isDefault());
    }

    @Test
    public void interfererToneConfigurationIsExposed() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                830,
                22000,
                500,
                0.0d,
                0,
                0.0d,
                0.0d,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        assertEquals(830, scenario.interfererToneFrequencyHz());
        assertEquals(22000, scenario.interfererToneAmplitude());
    }

    @Test
    public void edgeRampConfigurationIsExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                500,
                0.08d,
                2000,
                0.10d,
                0.04d,
                5,
                7,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        assertEquals(5, scenario.riseRampMs());
        assertEquals(7, scenario.fallRampMs());
        assertTrue(scenario.timingProfileSummary().contains("edge ramp 5/7ms"));
    }

    @Test
    public void toneDriftConfigurationIsExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                500,
                0.08d,
                2000,
                14.0d,
                0.10d,
                0.04d,
                5,
                7,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        assertEquals(14.0d, scenario.toneDriftHz(), 0.0001d);
        assertTrue(scenario.timingProfileSummary().contains("tone drift 14Hz"));
    }

    @Test
    public void interfererToneDriftConfigurationIsExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                910,
                9000,
                500,
                0.08d,
                2000,
                0.0d,
                -120.0d,
                0.10d,
                0.04d,
                5,
                7,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        );

        assertEquals(-120.0d, scenario.interfererToneDriftHz(), 0.0001d);
        assertTrue(scenario.timingProfileSummary().contains("interferer drift -120Hz"));
    }

    @Test
    public void additionalInterferersAreExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                910,
                9000,
                500,
                0.08d,
                2000,
                0.0d,
                -120.0d,
                0.10d,
                0.04d,
                5,
                7,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        ).withAdditionalInterferers(Arrays.asList(
                new CwFixtureScenario.ContinuousInterfererProfile(560, 2200),
                new CwFixtureScenario.ContinuousInterfererProfile(810, 2600, -35.0d)
        ));

        assertEquals(2, scenario.additionalInterferers().size());
        assertEquals(560, scenario.additionalInterferers().get(0).toneFrequencyHz());
        assertEquals(2600, scenario.additionalInterferers().get(1).toneAmplitude());
        assertEquals(-35.0d, scenario.additionalInterferers().get(1).toneDriftHz(), 0.0001d);
        assertTrue(scenario.timingProfileSummary().contains("extra interferer 560Hz @ 2200"));
        assertTrue(scenario.timingProfileSummary().contains("extra interferer 810Hz @ 2600 drift -35Hz"));
    }

    @Test
    public void burstyAdditionalInterfererIsExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                500,
                0.0d,
                0,
                0.0d,
                0.0d,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        ).withAdditionalInterferers(Collections.singletonList(
                new CwFixtureScenario.ContinuousInterfererProfile(780, 1800, -12.0d, 70, 130, 25)
        ));

        CwFixtureScenario.ContinuousInterfererProfile interferer = scenario.additionalInterferers().get(0);
        assertEquals(780, interferer.toneFrequencyHz());
        assertEquals(1800, interferer.toneAmplitude());
        assertEquals(-12.0d, interferer.toneDriftHz(), 0.0001d);
        assertEquals(70, interferer.burstOnMs());
        assertEquals(130, interferer.burstOffMs());
        assertEquals(25, interferer.burstOffsetMs());
        assertTrue(interferer.isBursting());
        assertTrue(scenario.timingProfileSummary().contains("burst 70/130ms"));
        assertTrue(scenario.timingProfileSummary().contains("offset 25ms"));
    }

    @Test
    public void burstWobbleConfigurationIsExposedAndIncludedInSummary() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                500,
                0.0d,
                0,
                0.0d,
                0.0d,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "test"
        ).withAdditionalInterferers(Collections.singletonList(
                new CwFixtureScenario.ContinuousInterfererProfile(780, 1800, -12.0d, 70, 130, 25, 0.18d, 320)
        ));

        CwFixtureScenario.ContinuousInterfererProfile interferer = scenario.additionalInterferers().get(0);
        assertTrue(interferer.isBursting());
        assertTrue(interferer.hasBurstWobble());
        assertEquals(0.18d, interferer.burstWobbleDepth(), 0.0001d);
        assertEquals(320, interferer.burstWobbleCycleMs());
        assertTrue(scenario.timingProfileSummary().contains("wobble 18%/320ms"));
    }

    @Test
    public void expectedFrontEndQualityCodeIsNormalizedAndExposed() {
        CwFixtureScenario scenario = new CwFixtureScenario(
                "test",
                "Test",
                "CQ CQ",
                Arrays.asList("CQ CQ"),
                1800,
                18,
                650,
                18000,
                0,
                0,
                500,
                0.08d,
                2000,
                0.0d,
                0.0d,
                0.10d,
                0.04d,
                0,
                0,
                Collections.singletonList(CwFixtureScenario.PartTimingProfile.defaultProfile()),
                250,
                450,
                "CQ CQ",
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                QsoPhase.CALLING_CQ,
                null,
                null,
                " drop ",
                "test"
        );

        assertEquals("DROP", scenario.expectedFrontEndQualityCode());
    }
}
