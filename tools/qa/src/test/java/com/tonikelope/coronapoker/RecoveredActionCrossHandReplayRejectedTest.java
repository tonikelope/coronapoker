package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RecoveredActionCrossHandReplayRejectedTest {
    @Test
    public void validRecordFromAnotherHandDoesNotBind() {
        byte[] previous = new byte[32];
        byte[] handA = new byte[16];
        byte[] handB = new byte[16];
        handB[0] = 1;
        byte[] record = CanonicalActionRecord.encode(previous, handA,
                CanonicalActionRecord.playerIdFromNick("alice"),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_FOLD, 0L, false, true);
        assertFalse(Crupier.recoveredActionBindsToRecordWithState(
                record, Player.FOLD, 0d, "alice", handB,
                0d, 100d, 0d));
    }
}
