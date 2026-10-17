package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UpnpPortReservationTest {

    @Test
    void successfulOpeningStaysMappedUntilTheFormCancels() {
        FakeGateway gateway = new FakeGateway();
        UpnpPortReservation.Attempt attempt
                = UpnpPortReservation.openTcp(7234, gateway);

        assertTrue(attempt.opened());
        assertTrue(attempt.reservation().active());
        assertEquals(1, gateway.opens);
        assertEquals(0, gateway.closes);
        attempt.reservation().close();
        assertFalse(attempt.reservation().active());
        assertEquals(1, gateway.closes);
    }

    @Test
    void lobbyTransferKeepsTheSameMappingAndChangesItsOwner() {
        FakeGateway gateway = new FakeGateway();
        UpnpPortReservation reservation
                = UpnpPortReservation.openTcp(21_339, gateway).reservation();

        UpnpPortMapping lobbyMapping = reservation.transferToLobby(21_339);
        reservation.close();

        assertEquals(1, gateway.opens);
        assertEquals(0, gateway.closes,
                "closing the old form owner must not close the lobby mapping");
        lobbyMapping.close();
        assertEquals(1, gateway.closes);
    }

    @Test
    void existingForeignMappingIsNeverClaimedOrClosed() {
        FakeGateway gateway = new FakeGateway();
        gateway.mapped = true;
        UpnpPortReservation.Attempt attempt
                = UpnpPortReservation.openTcp(7234, gateway);

        assertEquals(UpnpPortReservation.Status.ALREADY_MAPPED,
                attempt.status());
        assertFalse(attempt.opened());
        assertEquals(0, gateway.opens);
        assertEquals(0, gateway.closes);
    }

    @Test
    void unavailableAndFailedGatewaysRemainDistinct() {
        FakeGateway unavailable = new FakeGateway();
        unavailable.available = false;
        assertEquals(UpnpPortReservation.Status.UNAVAILABLE,
                UpnpPortReservation.openTcp(7234, unavailable).status());

        FakeGateway failed = new FakeGateway();
        failed.opensSuccessfully = false;
        assertEquals(UpnpPortReservation.Status.FAILED,
                UpnpPortReservation.openTcp(7234, failed).status());
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
