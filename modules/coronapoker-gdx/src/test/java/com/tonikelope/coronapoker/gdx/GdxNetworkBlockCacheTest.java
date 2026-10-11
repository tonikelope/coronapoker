package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.NetworkBlock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxNetworkBlockCacheTest {

    @Test
    void renderingOnlySchedulesRefreshAndReusesItsImmutableSnapshot() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<Runnable> worker = new AtomicReference<>();
        NetworkBlock block = new NetworkBlock("127.0.0.2", 10_000L,
                NetworkBlock.Reason.CONNECTION_ABUSE);
        GdxNetworkBlockCache cache = new GdxNetworkBlockCache(() -> {
            reads.incrementAndGet();
            return List.of(block);
        }, worker::set);

        cache.update(0f, true);
        assertEquals(0, reads.get(),
                "render update must not acquire the network guard");
        assertNotNull(worker.get());
        worker.getAndSet(null).run();
        cache.update(0f, true);
        assertEquals(1, reads.get());
        assertEquals(List.of(block), cache.current());

        for (int frame = 0; frame < 119; frame++) {
            cache.update(1f / 120f, true);
        }
        assertEquals(1, reads.get(),
                "120-Hz rendering must not query and sort the guard per frame");

        cache.update(2f / 120f, true);
        assertEquals(1, reads.get());
        assertNotNull(worker.get());
        worker.getAndSet(null).run();
        cache.update(0f, true);
        assertEquals(2, reads.get());

        cache.removeLocally(block.address());
        assertTrue(cache.current().isEmpty(),
                "a confirmed unblock must disappear from the UI immediately");
        cache.refreshNow(true);
        assertNotNull(worker.get());
        worker.getAndSet(null).run();
        cache.update(0f, true);
        assertEquals(3, reads.get(),
                "an explicit unblock refreshes immediately");
        cache.update(0f, false);
        assertTrue(cache.current().isEmpty());
    }

    @Test
    void transientReadFailurePreservesTheLastKnownAdministrativeView() {
        AtomicReference<Runnable> worker = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        NetworkBlock block = new NetworkBlock("127.0.0.3", 20_000L,
                NetworkBlock.Reason.CONNECTION_ABUSE);
        GdxNetworkBlockCache cache = new GdxNetworkBlockCache(() -> {
            if (attempts.incrementAndGet() > 1) {
                throw new IllegalStateException("transient read failure");
            }
            return List.of(block);
        }, worker::set);

        cache.update(0f, true);
        worker.getAndSet(null).run();
        cache.update(0f, true);
        assertEquals(List.of(block), cache.current());

        cache.refreshNow(true);
        worker.getAndSet(null).run();
        cache.update(0f, true);
        assertEquals(List.of(block), cache.current(),
                "a failed refresh must not make a real block disappear from the UI");
    }
}
