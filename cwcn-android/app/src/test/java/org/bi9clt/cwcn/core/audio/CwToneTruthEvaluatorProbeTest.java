package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

public final class CwToneTruthEvaluatorProbeTest {
    @Test
    public void printToneTruthComparisonsForRepresentativeFixtures() {
        print("user_light_qsb_cq_18wpm_700hz", 450);
        print("user_light_qsb_cq_18wpm_600hz", 450);
        print("user_light_qsb_cq_18wpm_800hz", 450);
        print("user_qsb_cq_18wpm_700hz", 450);
        print("user_qsb_cq_18wpm_600hz", 450);
        print("user_qsb_cq_18wpm_800hz", 450);
        print("user_tone_sweep_vvv_18wpm", 450);
        print("user_speed_shift_jv3vv_700hz", 450);
    }

    private static void print(String scenarioId, int preferredToneHz) {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture(scenarioId, preferredToneHz);
        System.out.println("==== " + scenarioId + " ====");
        System.out.println(comparison.renderSummary());
    }
}
