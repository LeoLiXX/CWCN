package org.bi9clt.cwcn.core.callsign;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class CallsignMetadataDatasetTest {
    @Test
    public void resolve_prefersLongestPrefixMatch() {
        CallsignMetadataDataset dataset = CallsignMetadataDataset.fromText(
                "Monaco:14:27:EU:43.73:-7.40:-1.0:3A:\n    3A;\n"
                        + "Mauritius:39:53:AF:-20.35:-57.50:-4.0:3B8:\n    3B8;",
                "Monaco:摩纳哥\nMauritius:毛里求斯\n"
        );

        CallsignMetadata metadata = dataset.resolve("3B8AA");
        assertNotNull(metadata);
        assertEquals("Mauritius", metadata.countryNameEn());
        assertEquals("毛里求斯", metadata.countryNameZhCn());
        assertEquals("AF / CQ 39 / ITU 53", metadata.regionSummary());
        assertEquals("3B8", metadata.dxccPrefix());
    }

    @Test
    public void resolve_honorsExactOverridesBeforeGenericPrefix() {
        CallsignMetadataDataset dataset = CallsignMetadataDataset.fromText(
                "Spratly Islands China:26:50:AS:9.88:-114.23:-8.0:9M0:\n"
                        + "    9M0,=DX0JP;\n"
                        + "Philippines:27:50:OC:12.88:-121.77:-8.0:DU:\n"
                        + "    DU,DX0;",
                "Spratly Islands China:中国南沙群岛\nPhilippines:菲律宾\n"
        );

        CallsignMetadata metadata = dataset.resolve("DX0JP");
        assertNotNull(metadata);
        assertEquals("Spratly Islands China", metadata.countryNameEn());
        assertEquals("中国南沙群岛", metadata.countryNameZhCn());
        assertEquals("=DX0JP", metadata.matchedRule());
    }
}
