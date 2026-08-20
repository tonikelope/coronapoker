package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * TDD coverage for the host COMM_REVEAL context/replay gate.
 */
class CommunityRevealBindingTest {

    @Test
    void acceptsTheCurrentHostRevealForThisHandAndStreet() {
        byte[] previous = filled(32, (byte) 0x11);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] host = CanonicalActionRecord.playerIdFromNick("host");
        byte[] record = CanonicalActionRecord.encode(previous, hand, host,
                CanonicalActionRecord.STREET_FLOP,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{0, 17, 51}),
                false, false);

        assertTrue(Crupier.communityRevealRecordIsSafe(record,
                CanonicalActionRecord.STREET_FLOP, 3, previous, hand, host));
    }

    @Test
    void rejectsAValidlySignedShapeFromAnotherChainPositionOrHand() {
        byte[] previous = filled(32, (byte) 0x11);
        byte[] old = filled(32, (byte) 0x33);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] host = CanonicalActionRecord.playerIdFromNick("host");
        byte[] record = CanonicalActionRecord.encode(old, hand, host,
                CanonicalActionRecord.STREET_TURN,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{12}),
                false, false);

        assertFalse(Crupier.communityRevealRecordIsSafe(record,
                CanonicalActionRecord.STREET_TURN, 1, previous, hand, host));

        byte[] otherHand = filled(16, (byte) 0x44);
        byte[] currentRecord = CanonicalActionRecord.encode(previous, otherHand, host,
                CanonicalActionRecord.STREET_TURN,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{12}),
                false, false);
        assertFalse(Crupier.communityRevealRecordIsSafe(currentRecord,
                CanonicalActionRecord.STREET_TURN, 1, previous, hand, host));
    }

    @Test
    void rejectsWrongSignerIdentityHiddenPackedBytesAndFlags() {
        byte[] previous = filled(32, (byte) 0x11);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] host = CanonicalActionRecord.playerIdFromNick("host");
        byte[] other = CanonicalActionRecord.playerIdFromNick("other");

        byte[] wrongHost = CanonicalActionRecord.encode(previous, hand, other,
                CanonicalActionRecord.STREET_RIVER,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{51}),
                false, false);
        assertFalse(Crupier.communityRevealRecordIsSafe(wrongHost,
                CanonicalActionRecord.STREET_RIVER, 1, previous, hand, host));

        byte[] hiddenByte = CanonicalActionRecord.encode(previous, hand, host,
                CanonicalActionRecord.STREET_FLOP,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{1, 2, 3}) | (1L << 24),
                false, false);
        assertFalse(Crupier.communityRevealRecordIsSafe(hiddenByte,
                CanonicalActionRecord.STREET_FLOP, 3, previous, hand, host));

        byte[] flagged = CanonicalActionRecord.encode(previous, hand, host,
                CanonicalActionRecord.STREET_FLOP,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{1, 2, 3}),
                true, false);
        assertFalse(Crupier.communityRevealRecordIsSafe(flagged,
                CanonicalActionRecord.STREET_FLOP, 3, previous, hand, host));
    }

    @Test
    void rejectsMalformedOrOutOfRangeCommunityRecords() {
        byte[] current = filled(32, (byte) 0x11);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] host = CanonicalActionRecord.playerIdFromNick("host");
        assertFalse(Crupier.communityRevealRecordIsSafe(new byte[4],
                CanonicalActionRecord.STREET_FLOP, 3, current, hand, host));

        byte[] badCard = CanonicalActionRecord.encode(current, hand, host,
                CanonicalActionRecord.STREET_TURN,
                CanonicalActionRecord.ACTION_COMMUNITY,
                52L, false, false);
        assertFalse(Crupier.communityRevealRecordIsSafe(badCard,
                CanonicalActionRecord.STREET_TURN, 1, current, hand, host));
    }

    @Test
    void rejectsDuplicateCardsInsideACommunityReveal() {
        byte[] current = filled(32, (byte) 0x11);
        byte[] hand = filled(16, (byte) 0x22);
        byte[] host = CanonicalActionRecord.playerIdFromNick("host");
        byte[] duplicateFlop = CanonicalActionRecord.encode(current, hand, host,
                CanonicalActionRecord.STREET_FLOP,
                CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(new int[]{7, 7, 19}),
                false, false);

        assertFalse(Crupier.communityRevealRecordIsSafe(duplicateFlop,
                CanonicalActionRecord.STREET_FLOP, 3, current, hand, host));
        assertFalse(Crupier.communityCardsAreUnique(new int[]{19}, java.util.List.of(2, 19, 31)),
                "turn/river cannot replay a card from an earlier board street");
        assertTrue(Crupier.communityCardsAreUnique(new int[]{20}, java.util.List.of(2, 19, 31)));
    }

    private static byte[] filled(int length, byte value) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }
}
