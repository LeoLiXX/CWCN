package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.Collections;
import java.util.List;

/**
 * Shared tone-batch dispatch into timing/decode.
 *
 * <p>This runner sits one level above {@link RxTimingDecodeRunner}: callers
 * provide how each tone event becomes timing events, while the runner owns the
 * common batch loop and forwards timing batches to the shared decode runner.</p>
 */
public final class RxToneTimingRunner {
    public interface TimingEventProducer {
        @Nullable
        List<CwTimingEvent> produce(CwToneEvent toneEvent);
    }

    public interface TimingEventsObserver {
        void observe(CwToneEvent toneEvent, List<CwTimingEvent> timingEvents);
    }

    private final RxTimingDecodeRunner timingDecodeRunner;

    public RxToneTimingRunner(RxTimingDecodeRunner timingDecodeRunner) {
        this.timingDecodeRunner = timingDecodeRunner;
    }

    public void dispatchToneEvents(
            @Nullable List<CwToneEvent> toneEvents,
            @Nullable TimingEventProducer timingEventProducer,
            @Nullable TimingEventsObserver timingEventsObserver,
            @Nullable RxTimingDecodeRunner.TimingEventRelay timingEventRelay,
            @Nullable RxTimingDecodeRunner.DecodeEventConsumer decodeEventConsumer
    ) {
        if (timingDecodeRunner == null
                || toneEvents == null
                || toneEvents.isEmpty()
                || timingEventProducer == null) {
            return;
        }
        for (CwToneEvent toneEvent : toneEvents) {
            if (toneEvent == null) {
                continue;
            }
            List<CwTimingEvent> timingEvents = timingEventProducer.produce(toneEvent);
            List<CwTimingEvent> safeTimingEvents = timingEvents == null
                    ? Collections.emptyList()
                    : timingEvents;
            if (timingEventsObserver != null) {
                timingEventsObserver.observe(toneEvent, safeTimingEvents);
            }
            timingDecodeRunner.dispatchTimingEvents(
                    safeTimingEvents,
                    timingEventRelay,
                    decodeEventConsumer
            );
        }
    }
}
