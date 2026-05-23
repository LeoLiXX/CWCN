package org.bi9clt.cwcn.core.app;

import static org.junit.Assert.assertEquals;

import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigSupportLevel;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;

public final class RxInputRouteResolverTest {
    @Test
    public void autoWithoutRigFallsBackToPhoneWhenEnabled() {
        assertEquals(
                RxInputRouteResolver.Source.PHONE_MICROPHONE,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.AUTO,
                        true,
                        false,
                        null
                )
        );
    }

    @Test
    public void autoWithRigAndUsbAudioPrefersUsb() {
        assertEquals(
                RxInputRouteResolver.Source.USB_EXTERNAL_AUDIO,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.AUTO,
                        true,
                        true,
                        fakeProfile("yaesu-cat")
                )
        );
    }

    @Test
    public void autoWithRigFallsBackToPhoneWhenUsbAudioMissing() {
        assertEquals(
                RxInputRouteResolver.Source.PHONE_MICROPHONE,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.AUTO,
                        true,
                        false,
                        fakeProfile("yaesu-cat")
                )
        );
    }

    @Test
    public void autoWithRigAndNoFallbackLeavesRxUnboundWhenUsbAudioMissing() {
        assertEquals(
                RxInputRouteResolver.Source.NONE,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.AUTO,
                        false,
                        false,
                        fakeProfile("yaesu-cat")
                )
        );
    }

    @Test
    public void explicitUsbModeDoesNotAutoFallback() {
        assertEquals(
                RxInputRouteResolver.Source.USB_EXTERNAL_AUDIO,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.USB_EXTERNAL_AUDIO,
                        true,
                        false,
                        fakeProfile("yaesu-cat")
                )
        );
    }

    @Test
    public void explicitPhoneModeAlwaysUsesPhone() {
        assertEquals(
                RxInputRouteResolver.Source.PHONE_MICROPHONE,
                RxInputRouteResolver.resolve(
                        RxInputSettingsStore.RxInputMode.PHONE_MICROPHONE,
                        false,
                        true,
                        fakeProfile("yaesu-cat")
                )
        );
    }

    private static RigProfile fakeProfile(String id) {
        return new RigProfile(
                id,
                id,
                "Test",
                "Test Model",
                RigTransport.TransportKind.USB_SERIAL,
                "generic-cat",
                RigSupportLevel.BENCH_READY,
                EnumSet.noneOf(RigCapability.class),
                "summary",
                "notes",
                Collections.emptyList()
        );
    }
}
