/*
 * Golden transcript = identity oracle for the float -> double money migration.
 *
 * Re-enacts a fully specified Texas Hold'em hand through the REAL consensus money
 * primitives — CanonicalActionRecord.amountToCents (the cents choke point),
 * CanonicalActionRecord.encode + HandStateChain (the H_t action ratchet),
 * PotMath.splitAmongWinners (pot division) and SettlementRecord (the terminal
 * settlement table) — and pins the resulting H_final.
 *
 * Why this is the safety net for the migration: the engine's working money type
 * changes from float to double, but every value still funnels through ONE
 * quantization gate (Helpers.floatClean/doubleClean) and ONE consensus gate
 * (amountToCents = round(x*100) -> integer cents). Below the float exactness
 * ceiling (~131072 chips) the float and double paths produce byte-identical
 * cents, so the whole transcript — and therefore H_final — must not move. This
 * test:
 *   1. pins H_final for a realistic multi-street, multi-winner hand (a tripwire
 *      for ANY accidental change to the money transcript);
 *   2. proves the float and double working types agree on every cent below the
 *      ceiling (the migration is consensus-invariant);
 *   3. proves the double path stays exact above the ceiling where float drifts
 *      (the reason the migration exists).
 *
 * The bare HandStateChain.absorb(record) ratchet (no signatures) is used on
 * purpose: signatures are orthogonal to the money arithmetic and would need test
 * Ed25519 keys. The cents in the records and the settlement table fully capture
 * what the float -> double change can affect.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoneyTranscriptGoldenTest {

    // Fixed, host-independent inputs so H_0 (and therefore H_final) is a stable
    // cross-platform constant. The exact bytes are arbitrary but frozen.
    private static final String HOST = "alice";
    private static final String[] NICKS = {"alice", "bob", "carol"};

    private static byte[] handId() {
        byte[] h = new byte[16];
        for (int i = 0; i < h.length; i++) {
            h[i] = (byte) (0x10 + i);
        }
        return h;
    }

    private static byte[] fixed32(int seed) {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (seed * 31 + i);
        }
        return b;
    }

    private static byte[] pid(String nick) {
        return CanonicalActionRecord.playerIdFromNick(nick);
    }

    // The engine's cents choke point, float working type (today's production path,
    // see Crupier.predictPostActionBetCents): clean to the cent grid, then round.
    private static long centsFloatPath(double betTo) {
        return CanonicalActionRecord.amountToCents(Helpers.floatClean((float) betTo));
    }

    // The same choke point after the migration, double working type: clean via the
    // double cleaner, then round to integer cents. Below the ceiling this must
    // agree with centsFloatPath cent-for-cent.
    private static long centsDoublePath(double betTo) {
        return Math.round(Helpers.doubleClean(betTo) * 100.0);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    private static HandStateChain startChain(byte[] handId) {
        List<byte[]> ids = new ArrayList<>();
        List<byte[]> kp = new ArrayList<>();
        List<byte[]> kc = new ArrayList<>();
        for (int i = 0; i < NICKS.length; i++) {
            ids.add(pid(NICKS[i]));
            kp.add(fixed32(100 + i));
            kc.add(fixed32(200 + i));
        }
        byte[] deck = fixed32(7);
        return HandStateChain.start(handId, ids, kp, kc, deck);
    }

    private static void action(HandStateChain chain, byte[] handId, String nick,
            int street, int action, long cents, boolean allin, boolean voluntary) {
        byte[] rec = CanonicalActionRecord.encode(chain.getCurrentHash(), handId, pid(nick),
                street, action, cents, allin, voluntary);
        chain.absorb(rec);
    }

    private static void community(HandStateChain chain, byte[] handId, int street, int[] cards) {
        long packed = CanonicalActionRecord.packCommunityCards(cards);
        byte[] rec = CanonicalActionRecord.encode(chain.getCurrentHash(), handId, pid(HOST),
                street, CanonicalActionRecord.ACTION_COMMUNITY, packed, false, false);
        chain.absorb(rec);
    }

    /**
     * Plays the fixed scenario through the chain and returns H_final.
     *
     * 3-handed, blinds 0.25/0.50, host = alice (button). bob posts the 0.25 small
     * blind then folds preflop (his 0.25 stays in the pot); carol (big blind) and
     * alice go to a checked-down river and split a 13.35 pot two ways — an odd cent
     * carried by PotMath. Streets carry host-signed community reveals.
     */
    private static byte[] playGoldenHand() {
        byte[] handId = handId();
        HandStateChain chain = startChain(handId);

        final int PRE = CanonicalActionRecord.STREET_PREFLOP;
        final int FLOP = CanonicalActionRecord.STREET_FLOP;
        final int TURN = CanonicalActionRecord.STREET_TURN;
        final int RIVER = CanonicalActionRecord.STREET_RIVER;

        // --- Preflop -----------------------------------------------------------
        action(chain, handId, "alice", PRE, CanonicalActionRecord.ACTION_RAISE, centsFloatPath(1.50), false, true);
        action(chain, handId, "bob", PRE, CanonicalActionRecord.ACTION_FOLD, 0L, false, true);
        action(chain, handId, "carol", PRE, CanonicalActionRecord.ACTION_CALL, centsFloatPath(1.50), false, true);

        // --- Flop --------------------------------------------------------------
        community(chain, handId, FLOP, new int[]{3, 17, 42});
        action(chain, handId, "carol", FLOP, CanonicalActionRecord.ACTION_CHECK, centsFloatPath(0.00), false, true);
        action(chain, handId, "alice", FLOP, CanonicalActionRecord.ACTION_BET, centsFloatPath(2.00), false, true);
        action(chain, handId, "carol", FLOP, CanonicalActionRecord.ACTION_CALL, centsFloatPath(2.00), false, true);

        // --- Turn --------------------------------------------------------------
        community(chain, handId, TURN, new int[]{8});
        action(chain, handId, "carol", TURN, CanonicalActionRecord.ACTION_CHECK, centsFloatPath(0.00), false, true);
        action(chain, handId, "alice", TURN, CanonicalActionRecord.ACTION_BET, centsFloatPath(3.05), false, true);
        action(chain, handId, "carol", TURN, CanonicalActionRecord.ACTION_CALL, centsFloatPath(3.05), false, true);

        // --- River -------------------------------------------------------------
        community(chain, handId, RIVER, new int[]{25});
        action(chain, handId, "carol", RIVER, CanonicalActionRecord.ACTION_CHECK, centsFloatPath(0.00), false, true);
        action(chain, handId, "alice", RIVER, CanonicalActionRecord.ACTION_CHECK, centsFloatPath(0.00), false, true);

        // --- Settlement --------------------------------------------------------
        // Per-player contribution to the pot (bote) across all streets.
        long boteAlice = centsFloatPath(1.50 + 2.00 + 3.05); // 6.55
        long boteBob = centsFloatPath(0.25);                 // posted SB, then folded
        long boteCarol = centsFloatPath(1.50 + 2.00 + 3.05); // 6.55
        long potCents = boteAlice + boteBob + boteCarol;     // 13.35 -> 1335

        // Two winners (alice, carol) split the pot; PotMath carries the odd cent.
        var split = PotMath.splitAmongWinners((float) (potCents / 100.0), 2);
        long perCents = Math.round((double) split[0] * 100.0);
        long sobranteCents = Math.round((double) split[1] * 100.0);

        List<SettlementRecord.Entry> entries = new ArrayList<>();
        entries.add(new SettlementRecord.Entry(pid("alice"), boteAlice, perCents));
        entries.add(new SettlementRecord.Entry(pid("bob"), boteBob, 0L));
        entries.add(new SettlementRecord.Entry(pid("carol"), boteCarol, perCents));

        // Conservation must hold before we commit the table.
        assertTrue(SettlementRecord.amountsBalance(entries, sobranteCents),
                "settlement must conserve money: pagar + sobrante == bote");

        byte[] table = SettlementRecord.encode(handId, entries, sobranteCents);
        chain.absorbSettlement(table);
        return chain.getCurrentHash();
    }

    // The pinned transcript digest. Frozen against the current (float) money path;
    // the migration to double must leave it byte-identical (that is the test).
    private static final String GOLDEN_HFINAL =
            "1b12232c746d0fa418ea77351c1e6cf33558e34734a884da3c4223158ec4a436";

    @Test
    void fullHandTranscriptMatchesGolden() {
        byte[] hFinal = playGoldenHand();
        assertEquals(GOLDEN_HFINAL, hex(hFinal),
                "H_final of the golden hand changed — the money transcript moved");
    }

    @Test
    void goldenHandIsDeterministic() {
        assertEquals(hex(playGoldenHand()), hex(playGoldenHand()),
                "same inputs must produce the same H_final");
    }

    @Test
    void floatAndDoubleWorkingTypesAgreeBelowCeiling() {
        // Every money amount the golden hand touches, plus the odd-cent split
        // boundary, must convert to identical cents in both working types.
        double[] amounts = {0.00, 0.25, 0.50, 1.50, 2.00, 3.05, 6.55, 13.35,
            0.05, 0.35, 12.34, 999.90, 5000.00, 131072.00};
        for (double v : amounts) {
            assertEquals(centsFloatPath(v), centsDoublePath(v),
                    "float and double cents must agree below the ceiling: " + v);
        }
    }

    @Test
    void potSplitCentsAreWorkingTypeIndependent() {
        // The pot division feeds the settlement payout; its cents must be the same
        // whatever the working type PotMath returns.
        var split = PotMath.splitAmongWinners(13.35f, 2);
        long per = Math.round((double) split[0] * 100.0);
        long rem = Math.round((double) split[1] * 100.0);
        assertEquals(667L, per, "13.35 split two ways -> 6.67 each");
        assertEquals(1L, rem, "odd cent carried");
        assertEquals(1335L, per * 2 + rem, "split conserves the pot");
    }

    @Test
    void doubleWorkingTypeIsExactAboveCeilingWhereFloatDrifts() {
        // The migration payoff: above ~131072 chips float32 can no longer carry
        // every cent, so the float path drifts off the true cents while the double
        // path stays exact. A high-stakes hand only settles correctly in double.
        double[] amounts = {200000.07, 1000000.55, 9999999.99};
        long[] exactCents = {20000007L, 100000055L, 999999999L};
        for (int i = 0; i < amounts.length; i++) {
            assertEquals(exactCents[i], centsDoublePath(amounts[i]),
                    "double path must be exact above the ceiling: " + amounts[i]);
            assertNotEquals(exactCents[i], centsFloatPath(amounts[i]),
                    "float path is expected to drift above the ceiling: " + amounts[i]);
        }
    }
}
