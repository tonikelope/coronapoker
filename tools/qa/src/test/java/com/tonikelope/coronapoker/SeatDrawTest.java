/*
 * Deterministic tests for SeatDraw, the pure crypto core of the verifiable (commit-reveal) seat
 * draw. These pin the properties the anti-bias guarantee rests on, with no network or RNG:
 *   1) commit/verifyCommit round-trips, and rejects any tamper of reveal / nick / nonce / commit;
 *   2) the derived order is a genuine permutation of the roster (everyone seated exactly once);
 *   3) derivation is deterministic and independent of the INPUT ORDER of both the roster and the
 *      reveal set — so every peer, fed the same reveals, seats the table identically;
 *   4) every contributor's reveal (and the nonce) affects the seed — no single input is ignorable,
 *      which is what stops any one participant (host included) from fixing the outcome.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SeatDrawTest {

    private static byte[] fixed(int fill) {
        byte[] b = new byte[SeatDraw.REVEAL_BYTES];
        Arrays.fill(b, (byte) fill);
        return b;
    }

    private static byte[] nonce(int fill) {
        byte[] b = new byte[SeatDraw.NONCE_BYTES];
        Arrays.fill(b, (byte) fill);
        return b;
    }

    private static Map<String, byte[]> reveals(String[] nicks, int[] fills) {
        LinkedHashMap<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < nicks.length; i++) {
            m.put(nicks[i], fixed(fills[i]));
        }
        return m;
    }

    @Test
    public void commitVerifyRoundTrip() {
        byte[] n = nonce(1);
        byte[] r = fixed(7);
        byte[] c = SeatDraw.commit(n, "alice", r);

        assertEquals(SeatDraw.COMMIT_BYTES, c.length);
        assertTrue(SeatDraw.verifyCommit(n, "alice", r, c));

        // Any tamper is rejected.
        assertFalse(SeatDraw.verifyCommit(n, "alice", fixed(8), c), "different reveal");
        assertFalse(SeatDraw.verifyCommit(n, "bob", r, c), "different nick");
        assertFalse(SeatDraw.verifyCommit(nonce(2), "alice", r, c), "different nonce");
        byte[] badCommit = c.clone();
        badCommit[0] ^= 0x01;
        assertFalse(SeatDraw.verifyCommit(n, "alice", r, badCommit), "flipped commit bit");
    }

    @Test
    public void orderIsAPermutationOfTheRoster() {
        byte[] n = nonce(5);
        String[] nicks = {"alice", "bob", "carol", "dave"};
        Map<String, byte[]> rv = reveals(nicks, new int[]{1, 2, 3, 4});
        List<String> roster = Arrays.asList("alice", "bob", "carol", "dave", "elf-bot", "orc-bot");

        byte[] seed = SeatDraw.deriveSeed(n, rv);
        String[] seated = SeatDraw.deriveOrder(roster, seed);

        assertEquals(roster.size(), seated.length);
        List<String> asList = Arrays.asList(seated);
        for (String nick : roster) {
            assertTrue(asList.contains(nick), "roster member missing from seating: " + nick);
        }
        // No duplicates.
        assertEquals(roster.size(), new java.util.HashSet<>(asList).size());
    }

    @Test
    public void derivationIsDeterministic() {
        byte[] n = nonce(9);
        String[] nicks = {"alice", "bob", "carol"};
        List<String> roster = Arrays.asList("alice", "bob", "carol");

        byte[] s1 = SeatDraw.deriveSeed(n, reveals(nicks, new int[]{10, 20, 30}));
        byte[] s2 = SeatDraw.deriveSeed(n, reveals(nicks, new int[]{10, 20, 30}));
        assertArrayEquals(s1, s2);

        assertArrayEquals(SeatDraw.deriveOrder(roster, s1), SeatDraw.deriveOrder(roster, s2));
    }

    @Test
    public void independentOfRosterAndRevealInputOrder() {
        // Two peers holding the same facts in different local orders must seat identically. This is
        // the cross-peer agreement property: the host's announcement order must not matter.
        byte[] n = nonce(3);
        String[] nicksA = {"alice", "bob", "carol"};
        String[] nicksB = {"carol", "alice", "bob"};

        byte[] seedA = SeatDraw.deriveSeed(n, reveals(nicksA, new int[]{4, 5, 6}));
        byte[] seedB = SeatDraw.deriveSeed(n, reveals(nicksB, new int[]{6, 4, 5})); // same nick->reveal, other insertion order
        assertArrayEquals(seedA, seedB, "seed must not depend on reveal insertion order");

        List<String> rosterA = new ArrayList<>(Arrays.asList("alice", "bob", "carol", "z-bot"));
        List<String> rosterB = new ArrayList<>(Arrays.asList("z-bot", "carol", "bob", "alice"));
        assertArrayEquals(SeatDraw.deriveOrder(rosterA, seedA), SeatDraw.deriveOrder(rosterB, seedB),
                "seating must not depend on roster input order");
    }

    @Test
    public void everyContributorAndTheNonceAffectTheSeed() {
        byte[] n = nonce(1);
        String[] nicks = {"alice", "bob", "carol"};
        byte[] base = SeatDraw.deriveSeed(n, reveals(nicks, new int[]{1, 2, 3}));

        // Flip ONE contributor's reveal at a time — the seed must change every time.
        assertFalse(Arrays.equals(base, SeatDraw.deriveSeed(n, reveals(nicks, new int[]{99, 2, 3}))), "alice ignored");
        assertFalse(Arrays.equals(base, SeatDraw.deriveSeed(n, reveals(nicks, new int[]{1, 99, 3}))), "bob ignored");
        assertFalse(Arrays.equals(base, SeatDraw.deriveSeed(n, reveals(nicks, new int[]{1, 2, 99}))), "carol ignored");

        // Changing only the nonce must change the seed too.
        assertFalse(Arrays.equals(base, SeatDraw.deriveSeed(nonce(2), reveals(nicks, new int[]{1, 2, 3}))), "nonce ignored");
    }

    @Test
    public void singleContributorWorks() {
        byte[] n = nonce(42);
        byte[] seed = SeatDraw.deriveSeed(n, reveals(new String[]{"solo"}, new int[]{1}));
        assertEquals(SeatDraw.SEED_BYTES, seed.length);
        String[] seated = SeatDraw.deriveOrder(Arrays.asList("solo", "bot1", "bot2"), seed);
        assertEquals(3, seated.length);
    }

    @Test
    public void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> SeatDraw.deriveSeed(nonce(1), new LinkedHashMap<>()), "empty reveal set");
        assertThrows(IllegalArgumentException.class,
                () -> SeatDraw.commit(new byte[8], "alice", fixed(1)), "short nonce");
        assertThrows(IllegalArgumentException.class,
                () -> SeatDraw.commit(nonce(1), "alice", new byte[8]), "short reveal");
        assertThrows(IllegalArgumentException.class,
                () -> SeatDraw.commit(nonce(1), "", fixed(1)), "empty nick");
        assertThrows(IllegalArgumentException.class,
                () -> SeatDraw.deriveOrder(Collections.emptyList(), new byte[SeatDraw.SEED_BYTES]), "empty roster");
    }

    @Test
    public void recoverSeatingAcceptsRotationJoinsAndLeaves() {
        List<String> ring = Arrays.asList("alice", "bob", "carol", "dave");

        // Identical order -> consistent.
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("alice", "bob", "carol", "dave")));
        // Cyclic rotation (each peer stores the ring rotated to its own pivot) -> consistent.
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("carol", "dave", "alice", "bob")));
        // A player left (bob) -> tolerated, the rest keep their cyclic order.
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("carol", "dave", "alice")));
        // A new player joined (eve) appended -> tolerated (their legitimacy is a join concern).
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("alice", "bob", "carol", "dave", "eve")));
        // Joins AND leaves at once, shared players keep cyclic order -> consistent.
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("carol", "dave", "eve", "alice")));
    }

    @Test
    public void recoverSeatingCatchesReordering() {
        List<String> ring = Arrays.asList("alice", "bob", "carol", "dave");

        // bob and carol swapped -> the shared players' cyclic order is broken -> caught.
        assertFalse(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("alice", "carol", "bob", "dave")));
        // dave moved next to alice out of cyclic order -> caught.
        assertFalse(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("alice", "dave", "bob", "carol")));
        // Reordering that survives a join must still be caught (eve joined, but bob/carol swapped).
        assertFalse(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("alice", "carol", "bob", "dave", "eve")));
    }

    @Test
    public void recoverSeatingNoOpsWithoutEnoughOverlap() {
        List<String> ring = Arrays.asList("alice", "bob", "carol");
        // Not enough local history / empty inputs -> treated as consistent (nothing to judge).
        assertTrue(SeatDraw.recoveredSeatingConsistent(null, Arrays.asList("alice", "bob")));
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, java.util.Collections.emptyList()));
        assertTrue(SeatDraw.recoveredSeatingConsistent(Arrays.asList("solo"), Arrays.asList("solo", "x")));
        // Only one shared player -> no relative order to violate -> consistent.
        assertTrue(SeatDraw.recoveredSeatingConsistent(ring, Arrays.asList("carol", "x", "y")));
    }

    @Test
    public void nickIsBoundInTheCommit() {
        // The length-prefixed framing binds the nick: "ab" and "a" can never share a commit, so a
        // contributor can't repurpose another's commitment by claiming a differently-split nick.
        byte[] n = nonce(1);
        byte[] r = fixed(1);
        byte[] cAB = SeatDraw.commit(n, "ab", r);
        byte[] cA = SeatDraw.commit(n, "a", r);
        assertFalse(Arrays.equals(cAB, cA));
        assertFalse(SeatDraw.verifyCommit(n, "a", r, cAB));
        assertTrue(SeatDraw.verifyCommit(n, "ab", r, cAB));
    }
}
