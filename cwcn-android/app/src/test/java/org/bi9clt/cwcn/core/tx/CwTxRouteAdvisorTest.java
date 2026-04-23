package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxRouteAdvisorTest {
    @Test
    public void audioVoxChecklistIncludesToneAndWpmWarningsForOutOfRangePlan() {
        CwTxBackend backend = new FakeBackend("rig-text:audio-vox-text");
        CwTxPlan plan = new CwTxEngine().buildPlan("CQ TEST", 35, 1000);

        String checklist = CwTxRouteAdvisor.buildChecklist(backend, plan);

        assertTrue(checklist.contains("Audio VOX checklist"));
        assertTrue(checklist.contains("Tone warning"));
        assertTrue(checklist.contains("WPM warning"));
    }

    @Test
    public void usbKeyerChecklistMentionsToneIrrelevance() {
        CwTxBackend backend = new FakeBackend("rig-text:usb-serial-keyer");
        CwTxPlan plan = new CwTxEngine().buildPlan("CQ TEST", 20, 650);

        String checklist = CwTxRouteAdvisor.buildChecklist(backend, plan);

        assertTrue(checklist.contains("USB RTS/DTR checklist"));
        assertTrue(checklist.contains("Tone note"));
    }

    private static final class FakeBackend implements CwTxBackend {
        private final String id;

        private FakeBackend(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return id;
        }

        @Override
        public String describeRoute() {
            return "";
        }

        @Override
        public String describeAvailability() {
            return "";
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean supportsLivePlanProfile() {
            return true;
        }

        @Override
        public boolean supportsProgressSnapshots() {
            return false;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public boolean start(CwTxPlan plan, CwTxRunner.Listener listener) {
            return false;
        }

        @Override
        public void stop() {
        }
    }
}
