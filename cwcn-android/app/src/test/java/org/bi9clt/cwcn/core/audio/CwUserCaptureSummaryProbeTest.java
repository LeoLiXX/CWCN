package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class CwUserCaptureSummaryProbeTest {
    @Test
    public void printRepresentativeUserCaptureSummaries() throws Exception {
        printSummary("user_speed_shift_jv3vv_700hz");
        printSummary("user_long_qso_drift_bg1xxx_ja1abc");
        printSummary("user_multi_round_continuous_qso_bi9clt_ja1abc");
        printSummary("user_short_tail_qrz_bi3tuk_kn");
        printSummary("user_similar_callsign_collision_bi9cms_bi9clt");
    }

    private void printSummary(String scenarioId) throws Exception {
        CwUserCaptureCoverageTest coverageTest = new CwUserCaptureCoverageTest();
        Method evaluateOfflineBundle = CwUserCaptureCoverageTest.class.getDeclaredMethod(
                "evaluateOfflineBundle",
                String.class
        );
        evaluateOfflineBundle.setAccessible(true);
        Object bundle = evaluateOfflineBundle.invoke(coverageTest, scenarioId);

        Field resultField = bundle.getClass().getDeclaredField("result");
        resultField.setAccessible(true);
        Object result = resultField.get(bundle);
        Method renderDebugSummary = CwUserCaptureCoverageTest.class.getDeclaredMethod(
                "renderDebugSummary",
                result.getClass(),
                bundle.getClass()
        );
        renderDebugSummary.setAccessible(true);
        String summary = (String) renderDebugSummary.invoke(coverageTest, result, bundle);

        System.out.println("==== " + scenarioId + " ====");
        System.out.println(summary);
    }
}
