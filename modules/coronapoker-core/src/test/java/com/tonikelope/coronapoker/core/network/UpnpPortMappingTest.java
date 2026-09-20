package com.tonikelope.coronapoker.core.network;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UpnpPortMappingTest {

    @Test
    void successfulMappingIsOwnedAndClosedExactlyOnce() {
        FakeGateway gateway = new FakeGateway();

        UpnpPortMapping.Attempt attempt = UpnpPortMapping.openTcp(7234, gateway);

        assertTrue(attempt.opened());
        assertNotNull(attempt.lease());
        assertEquals(1, gateway.opens.get());
        attempt.lease().close();
        attempt.lease().close();
        assertEquals(1, gateway.closes.get());
    }

    @Test
    void existingForeignMappingIsNeverClaimedOrClosed() {
        FakeGateway gateway = new FakeGateway();
        gateway.mapped = true;

        UpnpPortMapping.Attempt attempt = UpnpPortMapping.openTcp(7234, gateway);

        assertFalse(attempt.opened());
        assertEquals(UpnpPortMapping.Status.ALREADY_MAPPED,
                attempt.status());
        assertNull(attempt.lease());
        assertEquals(0, gateway.opens.get());
        assertEquals(0, gateway.closes.get());
    }

    @Test
    void unavailableOrFailingRoutersDoNotAbortTheHost() {
        FakeGateway unavailable = new FakeGateway();
        unavailable.available = false;
        assertEquals(UpnpPortMapping.Status.UNAVAILABLE,
                UpnpPortMapping.openTcp(7234, unavailable).status());

        FakeGateway failing = new FakeGateway();
        failing.opensSuccessfully = false;
        assertEquals(UpnpPortMapping.Status.FAILED,
                UpnpPortMapping.openTcp(7234, failing).status());
    }

    private static final class FakeGateway
            implements UpnpPortMapping.Gateway {
        private boolean available = true;
        private boolean mapped;
        private boolean opensSuccessfully = true;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        @Override public boolean available() { return available; }
        @Override public boolean mappedTcp(int port) { return mapped; }
        @Override public boolean openTcp(int port) {
            opens.incrementAndGet();
            return opensSuccessfully;
        }
        @Override public boolean closeTcp(int port) {
            closes.incrementAndGet();
            return true;
        }
    }
}
