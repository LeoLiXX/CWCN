package org.bi9clt.cwcn.core.rig;

import java.util.List;

public interface MockUsbSerialBenchFactory extends UsbSerialRouteFactory {
    List<MockUsbSerialBenchScenario> availableBenchScenarios();

    MockUsbSerialBenchScenario selectedBenchScenario();

    boolean selectBenchScenario(MockUsbSerialBenchScenario scenario);
}
