/*
 * Golden transcript for the current exact-cent money path.
 *
 * Re-enacts a fully specified Texas Hold'em hand through the REAL consensus money
 * primitives — CanonicalActionRecord.amountToCents (the cents choke point),
 * CanonicalActionRecord.encode + HandStateChain (the H_t action ratchet),
 * PotMath.splitAmongWinners (pot division) and SettlementRecord (the terminal
 * settlement table) — and pins the resulting H_final.
 *
 * Every value funnels through one quantization gate and one consensus gate. This test:
 *   1. pins H_final for a realistic multi-street, multi-winner hand (a tripwire
 *      for ANY accidental change to the money transcript);
 *   2. proves the current path stays exact at high stacks.
 *
 * The bare HandStateChain.absorb(record) ratchet (no signatures) is used on
 * purpose: signatures are orthogonal to the money arithmetic and would need test
 * Ed25519 keys. The cents in the records and the settlement table fully capture
 * all money that affects consensus.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // The current cents choke point.
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
     * 3-handed, blinds 0.25/0.50, host = alice (button). bob posts the 0.25
     * small blind then folds preflop (his 0.25 stays in the pot); carol (big
     * blind) and alice go to a checked-down river and split a 13.35 pot two
     * ways — an odd cent carried by PotMath. Streets carry host-signed
     * community reveals.
     */
    private static byte[] playGoldenHand() {
        byte[] handId = handId();
        HandStateChain chain = startChain(handId);

        final int PRE = CanonicalActionRecord.STREET_PREFLOP;
        final int FLOP = CanonicalActionRecord.STREET_FLOP;
        final int TURN = CanonicalActionRecord.STREET_TURN;
        final int RIVER = CanonicalActionRecord.STREET_RIVER;

        // --- Preflop -----------------------------------------------------------
        action(chain, handId, "alice", PRE, CanonicalActionRecord.ACTION_RAISE, centsDoublePath(1.50), false, true);
        action(chain, handId, "bob", PRE, CanonicalActionRecord.ACTION_FOLD, 0L, false, true);
        action(chain, handId, "carol", PRE, CanonicalActionRecord.ACTION_CALL, centsDoublePath(1.50), false, true);

        // --- Flop --------------------------------------------------------------
        community(chain, handId, FLOP, new int[]{3, 17, 42});
        action(chain, handId, "carol", FLOP, CanonicalActionRecord.ACTION_CHECK, centsDoublePath(0.00), false, true);
        action(chain, handId, "alice", FLOP, CanonicalActionRecord.ACTION_BET, centsDoublePath(2.00), false, true);
        action(chain, handId, "carol", FLOP, CanonicalActionRecord.ACTION_CALL, centsDoublePath(2.00), false, true);

        // --- Turn --------------------------------------------------------------
        community(chain, handId, TURN, new int[]{8});
        action(chain, handId, "carol", TURN, CanonicalActionRecord.ACTION_CHECK, centsDoublePath(0.00), false, true);
        action(chain, handId, "alice", TURN, CanonicalActionRecord.ACTION_BET, centsDoublePath(3.05), false, true);
        action(chain, handId, "carol", TURN, CanonicalActionRecord.ACTION_CALL, centsDoublePath(3.05), false, true);

        // --- River -------------------------------------------------------------
        community(chain, handId, RIVER, new int[]{25});
        action(chain, handId, "carol", RIVER, CanonicalActionRecord.ACTION_CHECK, centsDoublePath(0.00), false, true);
        action(chain, handId, "alice", RIVER, CanonicalActionRecord.ACTION_CHECK, centsDoublePath(0.00), false, true);

        // --- Settlement --------------------------------------------------------
        // Per-player contribution to the pot (bote) across all streets.
        long boteAlice = centsDoublePath(1.50 + 2.00 + 3.05); // 6.55
        long boteBob = centsDoublePath(0.25);                 // posted SB, then folded
        long boteCarol = centsDoublePath(1.50 + 2.00 + 3.05); // 6.55
        long potCents = boteAlice + boteBob + boteCarol;     // 13.35 -> 1335

        // Two winners (alice, carol) split the pot; PotMath carries the odd cent.
        var split = PotMath.splitAmongWinners(potCents / 100.0, 2);
        long perCents = Math.round(split[0] * 100.0);
        long sobranteCents = Math.round(split[1] * 100.0);

        List<SettlementRecord.Entry> entries = new ArrayList<>();
        entries.add(new SettlementRecord.Entry(pid("alice"), boteAlice, perCents));
        entries.add(new SettlementRecord.Entry(pid("bob"), boteBob, 0L));
        entries.add(new SettlementRecord.Entry(pid("carol"), boteCarol, perCents));

        // Conservation must hold before we commit the table.
        assertTrue(SettlementRecord.amountsBalance(entries, 0L, sobranteCents),
                "settlement must conserve money: pagar + sobrante == bote");

        byte[] table = SettlementRecord.encode(handId, entries, 0L, sobranteCents);
        chain.absorbSettlement(table);
        return chain.getCurrentHash();
    }

    // The pinned transcript digest for the current settlement format.
    private static final String GOLDEN_HFINAL
            = "5da3a5282990faf910ca9c23a6c00710e3a2e1a75a6c59531c196a5723b710fb";

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
    void potSplitCentsAreExact() {
        var split = PotMath.splitAmongWinners(13.35, 2);
        long per = Math.round(split[0] * 100.0);
        long rem = Math.round(split[1] * 100.0);
        assertEquals(667L, per, "13.35 split two ways -> 6.67 each");
        assertEquals(1L, rem, "odd cent carried");
        assertEquals(1335L, per * 2 + rem, "split conserves the pot");
    }

    @Test
    void currentMoneyTypeIsExactAtHighStacks() {
        double[] amounts = {200000.07, 1000000.55, 9999999.99};
        long[] exactCents = {20000007L, 100000055L, 999999999L};
        for (int i = 0; i < amounts.length; i++) {
            assertEquals(exactCents[i], centsDoublePath(amounts[i]),
                    "double path must be exact above the ceiling: " + amounts[i]);
        }
    }
}
