package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UpnpPortProbeTest {

    @Test
    void successfulProbeReleasesOnlyItsOwnTemporaryMapping() {
        FakeGateway gateway = new FakeGateway();

        assertEquals(UpnpPortProbe.Status.AVAILABLE,
                UpnpPortProbe.probeTcp(7234, gateway));
        assertEquals(1, gateway.opens);
        assertEquals(1, gateway.closes);
    }

    @Test
    void existingMappingIsReportedAndNeverRemoved() {
        FakeGateway gateway = new FakeGateway();
        gateway.mapped = true;

        assertEquals(UpnpPortProbe.Status.ALREADY_MAPPED,
                UpnpPortProbe.probeTcp(7234, gateway));
        assertEquals(0, gateway.opens);
        assertEquals(0, gateway.closes);
    }

    @Test
    void unavailableAndFailedGatewaysRemainDistinct() {
        FakeGateway unavailable = new FakeGateway();
        unavailable.available = false;
        assertEquals(UpnpPortProbe.Status.UNAVAILABLE,
                UpnpPortProbe.probeTcp(7234, unavailable));

        FakeGateway failed = new FakeGateway();
        failed.opensSuccessfully = false;
        assertEquals(UpnpPortProbe.Status.FAILED,
                UpnpPortProbe.probeTcp(7234, failed));
    }

    private static final class FakeGateway
            implements UpnpPortMapping.Gateway {
        private boolean available = true;
        private boolean mapped;
        private boolean opensSuccessfully = true;
        private int opens;
        private int closes;

        @Override public boolean available() { return available; }
        @Override public boolean mappedTcp(int port) { return mapped; }

        @Override
        public boolean openTcp(int port) {
            opens++;
            return opensSuccessfully;
        }

        @Override
        public boolean closeTcp(int port) {
            closes++;
            return true;
        }
    }
}
