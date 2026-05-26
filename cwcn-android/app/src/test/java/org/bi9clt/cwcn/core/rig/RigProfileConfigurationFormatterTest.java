package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class RigProfileConfigurationFormatterTest {
    @Test
    public void serialCatSummaryIncludesProtocolFamilyAndBaudRate() {
        RigProfile profile = RigProfileCatalog.findById("generic-cat-serial");
        RigProfileSettings settings = new RigProfileSettings(
                20,
                700,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.YAESU_STYLE,
                38400,
                "COM5",
                null,
                CatProtocolFamily.HAMLIB_RIGCTLD,
                null,
                4532,
                null
        );

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, settings);

        assertTrue(summary.contains("Yaesu 风格 CAT"));
        assertTrue(summary.contains("38400 baud"));
        assertTrue(summary.contains("COM5"));
    }

    @Test
    public void networkCatSummaryIncludesProtocolFamilyAndEndpoint() {
        RigProfile profile = RigProfileCatalog.findById("generic-network-cat");
        RigProfileSettings settings = new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.GENERIC,
                9600,
                null,
                null,
                CatProtocolFamily.HAMLIB_RIGCTLD,
                "192.168.1.8",
                4532,
                null
        );

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, settings);

        assertTrue(summary.contains("Hamlib rigctld"));
        assertTrue(summary.contains("192.168.1.8:4532"));
    }

    @Test
    public void concreteCatProfileUsesRecommendedDefaultsWhenNoOverridesExist() {
        RigProfile profile = RigProfileCatalog.findById("icom-civ-serial-generic");

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, profile.defaultSettings());

        assertTrue(summary.contains("Icom CI-V"));
        assertTrue(summary.contains("19200 baud"));
    }

    @Test
    public void yaesuRigctldSummaryIncludesYaesuBenchNote() {
        RigProfile profile = RigProfileCatalog.findById("yaesu-rigctld-network-family");
        RigProfileSettings settings = new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.YAESU_STYLE,
                38400,
                null,
                null,
                CatProtocolFamily.HAMLIB_RIGCTLD,
                "192.168.1.9",
                4532,
                null
        );

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, settings);

        assertTrue(summary.contains("Hamlib rigctld"));
        assertTrue(summary.contains("192.168.1.9:4532"));
        assertTrue(summary.contains("Yaesu 提示"));
    }

    @Test
    public void icomSerialSummaryIncludesCivAddress() {
        RigProfile profile = RigProfileCatalog.findById("icom-civ-serial-generic");
        RigProfileSettings settings = new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.ICOM_CIV,
                19200,
                null,
                "94",
                CatProtocolFamily.HAMLIB_RIGCTLD,
                "10.0.0.5",
                4532,
                null
        );

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, settings);

        assertTrue(summary.contains("Icom CI-V"));
        assertTrue(summary.contains("CI-V 地址：0x94"));
    }

    @Test
    public void xieguSerialSummaryIncludesXieguHint() {
        RigProfile profile = RigProfileCatalog.findById("xiegu-x6200-serial");

        String summary = RigProfileConfigurationFormatter.renderCompactSummary(profile, profile.defaultSettings());

        assertTrue(summary.contains("Xiegu"));
        assertTrue(summary.contains("19200 baud"));
        assertTrue(summary.contains("Xiegu 提示"));
    }
}
