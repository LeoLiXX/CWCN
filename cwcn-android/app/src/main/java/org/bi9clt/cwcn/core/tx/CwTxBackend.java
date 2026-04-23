package org.bi9clt.cwcn.core.tx;

public interface CwTxBackend {
    String id();

    String displayName();

    String describeRoute();

    String describeAvailability();

    boolean isReady();

    boolean supportsLivePlanProfile();

    boolean supportsProgressSnapshots();

    boolean isRunning();

    boolean start(CwTxPlan plan, CwTxRunner.Listener listener);

    void stop();
}
