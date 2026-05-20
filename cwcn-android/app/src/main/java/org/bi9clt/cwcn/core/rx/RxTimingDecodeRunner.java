package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.List;

/**
 * Shared timing-to-decode event pump for live and replay RX paths.
 *
 * <p>The caller still owns timing-event generation and any path-specific
 * admission/adaptation logic. This runner only centralizes the common event
 * dispatch shape:
 *
 * <ul>
 *   <li>timing event batch
 *   <li>optional relay / adaptation
 *   <li>decoder process
 *   <li>decode event fan-out
 * </ul>
 */
public final class RxTimingDecodeRunner {
    public interface TimingEventRelay {
        @Nullable
        CwTimingEvent relay(CwTimingEvent timingEvent);
    }

    public interface DecodeEventConsumer {
        void accept(CwDecodeEvent decodeEvent);
    }

    private final CwDecoder decoder;

    public RxTimingDecodeRunner(CwDecoder decoder) {
        this.decoder = decoder;
    }

    public void dispatchTimingEvents(
            @Nullable List<CwTimingEvent> timingEvents,
            @Nullable TimingEventRelay relay,
            @Nullable DecodeEventConsumer decodeEventConsumer
    ) {
        if (decoder == null || timingEvents == null || timingEvents.isEmpty()) {
            return;
        }
        for (CwTimingEvent timingEvent : timingEvents) {
            CwTimingEvent eventForDecode = relay == null
                    ? timingEvent
                    : relay.relay(timingEvent);
            if (eventForDecode == null) {
                continue;
            }
            List<CwDecodeEvent> decodeEvents = decoder.process(eventForDecode);
            dispatchDecodeEvents(decodeEvents, decodeEventConsumer);
        }
    }

    public void flushPendingCharacter(
            long timestampMs,
            @Nullable DecodeEventConsumer decodeEventConsumer
    ) {
        if (decoder == null) {
            return;
        }
        dispatchDecodeEvents(
                decoder.flushPendingCharacter(timestampMs),
                decodeEventConsumer
        );
    }

    public boolean hasPendingCharacter() {
        return decoder != null && decoder.hasPendingCharacter();
    }

    private void dispatchDecodeEvents(
            @Nullable List<CwDecodeEvent> decodeEvents,
            @Nullable DecodeEventConsumer decodeEventConsumer
    ) {
        if (decodeEventConsumer == null || decodeEvents == null || decodeEvents.isEmpty()) {
            return;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            decodeEventConsumer.accept(decodeEvent);
        }
    }
}
