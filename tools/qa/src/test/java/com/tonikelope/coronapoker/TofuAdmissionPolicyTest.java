package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TofuAdmissionPolicyTest {

    @Test
    void onlyDurableNewAndMatchingPinsAreAdmitted() {
        assertTrue(WaitingRoomFrame.isTofuAdmissionAllowed(
                new TOFUResolver.Resolution(TOFUResolver.Outcome.NEW, false, 1)));
        assertTrue(WaitingRoomFrame.isTofuAdmissionAllowed(
                new TOFUResolver.Resolution(TOFUResolver.Outcome.MATCH, true, 2)));
        assertFalse(WaitingRoomFrame.isTofuAdmissionAllowed(
                new TOFUResolver.Resolution(TOFUResolver.Outcome.CHANGED, true, 1)));
        assertFalse(WaitingRoomFrame.isTofuAdmissionAllowed(
                new TOFUResolver.Resolution(TOFUResolver.Outcome.ERROR, false, 0)));
        assertFalse(WaitingRoomFrame.isTofuAdmissionAllowed(null));
    }

    @Test
    void remoteClientsCannotClaimBotNamespace() {
        assertTrue(WaitingRoomFrame.isReservedRemoteNick("CoronaBot$1"));
        assertTrue(WaitingRoomFrame.isReservedRemoteNick("CoronaBot$evil"));
        assertFalse(WaitingRoomFrame.isReservedRemoteNick("CoronaBot"));
        assertFalse(WaitingRoomFrame.isReservedRemoteNick("alice"));
    }
}
