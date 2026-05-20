package org.bi9clt.cwcn.core.rx;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxStableDecodeDeciderTest {
    @Test
    public void bootstrapAllowsContinuityCompensationWhenIsolationIsMicLimited() {
        assertTrue(RxStableDecodeDecider.passesBootstrapDecodeShape(
                true,
                0.44d,
                0.92d,
                0.12d,
                0.43d,
                0.35d
        ));
    }

    @Test
    public void bootstrapCanUseRecentLockContextEvenWhenCurrentHardLockDroppedAtCharacterBoundary() {
        assertTrue(RxStableDecodeDecider.passesBootstrapDecodeShape(
                false,
                0.44d,
                0.92d,
                0.12d,
                0.43d,
                0.35d
        ));
    }

    @Test
    public void bootstrapStillRejectsWeakContinuityWithLowIsolation() {
        assertFalse(RxStableDecodeDecider.passesBootstrapDecodeShape(
                true,
                0.10d,
                0.30d,
                0.50d,
                0.12d,
                0.10d
        ));
    }

    @Test
    public void steadyStateStillRequiresStrongerIsolation() {
        assertFalse(RxStableDecodeDecider.passesSteadyDecodeShape(
                true,
                0.70d,
                0.88d,
                0.10d,
                0.48d,
                0.35d
        ));
    }

    @Test
    public void steadyStateCanAdmitStrongNearTargetContinuityWhenLockCoverageIsJustBelowPrimaryThreshold() {
        assertTrue(RxStableDecodeDecider.passesSteadyDecodeShape(
                true,
                0.46d,
                0.95d,
                0.10d,
                0.88d,
                0.72d
        ));
    }

    @Test
    public void steadyStateCanCarryKnownCharacterAcrossQuietGapEdgeAfterTrust() {
        assertTrue(RxStableDecodeDecider.passesSteadyDecodeShape(
                false,
                0.58d,
                0.00d,
                0.00d,
                0.04d,
                0.04d
        ));
    }

    @Test
    public void steadyStateCanUseStrongCurrentLockWhenNearTargetHistoryFallsToZero() {
        assertTrue(RxStableDecodeDecider.passesSteadyDecodeShape(
                true,
                0.58d,
                0.00d,
                0.00d,
                0.87d,
                0.64d
        ));
    }

    @Test
    public void steadyStateCanUseModerateCurrentLockWhenToneShapeIsCleanAndStrong() {
        assertTrue(RxStableDecodeDecider.passesSteadyDecodeShape(
                true,
                0.46d,
                0.00d,
                0.00d,
                0.93d,
                0.71d
        ));
    }

    @Test
    public void steadyStateGapEdgeCarryStillRejectsWhenUnlockedNoisePersists() {
        assertFalse(RxStableDecodeDecider.passesSteadyDecodeShape(
                false,
                0.58d,
                0.00d,
                0.12d,
                0.04d,
                0.04d
        ));
    }

    @Test
    public void steadyStateStillRejectsSubThresholdLockCoverageWhenStrongNearTargetEvidenceIsMissing() {
        assertFalse(RxStableDecodeDecider.passesSteadyDecodeShape(
                true,
                0.46d,
                0.82d,
                0.10d,
                0.88d,
                0.72d
        ));
    }

    @Test
    public void boundaryBootstrapCanUseStrongContinuityEvenWhenGapPhaseTonePowerDrops() {
        assertTrue(RxStableDecodeDecider.passesBootstrapBoundaryShape(
                false,
                0.13d,
                1.00d,
                0.08d,
                0.10d,
                0.14d
        ));
    }

    @Test
    public void boundaryBootstrapStillRejectsCollapsedGapPhaseContinuity() {
        assertFalse(RxStableDecodeDecider.passesBootstrapBoundaryShape(
                false,
                0.04d,
                0.78d,
                0.28d,
                0.08d,
                0.07d
        ));
    }
}
