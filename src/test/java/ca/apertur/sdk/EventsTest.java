package ca.apertur.sdk;

import ca.apertur.sdk.resource.Events;
import ca.apertur.sdk.resource.EventSubscription;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Offline smoke tests for the real-time {@link Events} resource.
 *
 * <p>These do not require a live Socket.IO server: they exercise the accessor
 * wiring, socket construction, listener registration and chaining, and clean
 * teardown. The socket is pointed at an unreachable local URL and closed
 * immediately, so any background connection attempt fails harmlessly.
 */
class EventsTest {

    private static Apertur client() {
        return new Apertur(new AperturConfig.Builder()
            .apiKey("aptr_test_smoke")
            .baseUrl("http://localhost:1")
            .build());
    }

    @Test
    void eventsAccessorIsWired() {
        assertNotNull(client().events());
    }

    @Test
    void subscribeReturnsChainableSubscription() {
        EventSubscription sub = client().events().subscribe("00000000-0000-0000-0000-000000000000");
        try {
            assertNotNull(sub);

            AtomicBoolean called = new AtomicBoolean(false);
            EventSubscription same = sub.on("image:ready", data -> called.set(true));

            // on(...) returns the same subscription for chaining.
            assertSame(sub, same);
        } finally {
            sub.close();
        }
    }
}
