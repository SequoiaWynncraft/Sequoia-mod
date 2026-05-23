package org.sequoia.seq.accessors;


import com.collarmc.pounce.CancelableCallback;
import com.collarmc.pounce.EventBus;
import org.sequoia.seq.client.SeqClient;

public interface EventBusAccessor {
    default EventBus seqevents() {
        return SeqClient.getEventBus();
    }

    default void seqdispatch(Object o) {
        seqevents().dispatch(o);
    }

    default void seqdispatch(Object event, CancelableCallback callback) {
        seqevents().dispatch(event, callback);
    }

    default void seqsubscribe(Object listener) {
        seqevents().subscribeStrongly(listener);
    }

    default void sequnsubscribe(Object listener) {
        seqevents().unsubscribe(listener);
    }
}
