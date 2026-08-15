package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemoteRosterAdmissionTest {

    @Test
    void admitsOnlyNewCanonicalNickBelowCapacity() {
        assertEquals(WaitingRoomFrame.RemoteRosterAdmission.ADMIT,
                WaitingRoomFrame.remoteRosterAdmission(
                        WaitingRoomFrame.MAX_PARTICIPANTES - 1, false, false));
        assertEquals(WaitingRoomFrame.RemoteRosterAdmission.DUPLICATE,
                WaitingRoomFrame.remoteRosterAdmission(
                        WaitingRoomFrame.MAX_PARTICIPANTES, true, true));
    }

    @Test
    void rejectsBeforeAllocationWhenRosterIsFullOrNfcCollides() {
        assertEquals(WaitingRoomFrame.RemoteRosterAdmission.REJECT,
                WaitingRoomFrame.remoteRosterAdmission(
                        WaitingRoomFrame.MAX_PARTICIPANTES, false, false));
        assertEquals(WaitingRoomFrame.RemoteRosterAdmission.REJECT,
                WaitingRoomFrame.remoteRosterAdmission(2, false, true));
    }
}
