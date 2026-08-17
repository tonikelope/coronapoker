package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * TDD coverage for rejecting a signed action replay before money is applied.
 */
class ActionReplayBindingTest {

    @Test
    void acceptsOnlyTheRecordWhosePrevHashIsTheCurrentChainHash() {
        byte[] current = filled(32, (byte) 0x11);
        byte[] record = filled(CanonicalActionRecord.RECORD_BYTES, (byte) 0x00);
        System.arraycopy(current, 0, record, CanonicalActionRecord.OFFSET_PREV_H, current.length);

        assertTrue(Crupier.recordStartsAtHash(record, current));
    }

    @Test
    void rejectsAValidlySizedSignedRecordFromAnEarlierChainPosition() {
        byte[] current = filled(32, (byte) 0x11);
        byte[] oldHash = filled(32, (byte) 0x22);
        byte[] record = filled(CanonicalActionRecord.RECORD_BYTES, (byte) 0x00);
        System.arraycopy(oldHash, 0, record, CanonicalActionRecord.OFFSET_PREV_H, oldHash.length);

        assertFalse(Crupier.recordStartsAtHash(record, current));
    }

    @Test
    void malformedRecordOrMissingExpectedHashIsNotAcceptedAsAReplay() {
        byte[] current = filled(32, (byte) 0x11);
        assertFalse(Crupier.recordStartsAtHash(new byte[4], current));
        assertTrue(Crupier.recordStartsAtHash(
                filled(CanonicalActionRecord.RECORD_BYTES, (byte) 0x00), null),
                "legacy/no-chain callers intentionally have no hash to enforce");
    }

    @Test
    void recoveryUsesBothTheMoneyGateAndTheCurrentChainHash() {
        byte[] current = filled(32, (byte) 0x11);
        byte[] old = filled(32, (byte) 0x22);
        byte[] record = filled(CanonicalActionRecord.RECORD_BYTES, (byte) 0x00);
        System.arraycopy(current, 0, record, CanonicalActionRecord.OFFSET_PREV_H, current.length);

        assertTrue(Crupier.recoveredActionIsSafe(
                record, Player.BET, 40d, 10d, 100d, 20d, 20d, 10d, current));
        assertFalse(Crupier.recoveredActionIsSafe(
                record, Player.BET, 111d, 10d, 100d, 20d, 20d, 10d, current));

        System.arraycopy(old, 0, record, CanonicalActionRecord.OFFSET_PREV_H, old.length);
        assertFalse(Crupier.recoveredActionIsSafe(
                record, Player.BET, 40d, 10d, 100d, 20d, 20d, 10d, current));
    }

    @Test
    void recoveryBindsCheckAndAllInAmountsToThePreActionState() {
        byte[] previous = filled(32, (byte) 0x11);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] player = CanonicalActionRecord.playerIdFromNick("alice");

        byte[] check = CanonicalActionRecord.encode(previous, hand, player,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK, 2000L, false, true);
        assertTrue(Crupier.recoveredActionBindsToRecordWithState(
                check, Player.CHECK, 0d, "alice", hand, 10d, 100d, 20d));

        byte[] wrongCheckAmount = CanonicalActionRecord.encode(previous, hand, player,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK, 2100L, false, true);
        assertFalse(Crupier.recoveredActionBindsToRecordWithState(
                wrongCheckAmount, Player.CHECK, 0d, "alice", hand, 10d, 100d, 20d));

        byte[] allIn = CanonicalActionRecord.encode(previous, hand, player,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_ALLIN, 11000L, true, true);
        assertTrue(Crupier.recoveredActionBindsToRecordWithState(
                allIn, Player.ALLIN, "cinematic", "alice", hand, 10d, 100d, 20d));

        byte[] wrongAllInAmount = CanonicalActionRecord.encode(previous, hand, player,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_ALLIN, 9999L, true, true);
        assertFalse(Crupier.recoveredActionBindsToRecordWithState(
                wrongAllInAmount, Player.ALLIN, "cinematic", "alice", hand, 10d, 100d, 20d));
    }

    private static byte[] filled(int length, byte value) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }
}
