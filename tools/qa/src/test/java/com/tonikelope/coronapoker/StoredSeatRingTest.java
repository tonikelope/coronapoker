package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoredSeatRingTest {

    private static String ring(String... nicks) {
        return java.util.Arrays.stream(nicks)
                .map(n -> Base64.getEncoder().encodeToString(n.getBytes(StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("#"));
    }

    @Test
    void findsCandidateInEitherDirection() {
        String stored = ring("ana", "bea", "carla", "dora");
        assertEquals("dora", Crupier.storedRingNeighbor(stored, "bea", Set.of("dora"), true));
        assertEquals("dora", Crupier.storedRingNeighbor(stored, "bea", Set.of("dora"), false));
    }

    @Test
    void missingPivotOrCandidateTerminatesWithoutInventingASeat() {
        String stored = ring("ana", "bea", "carla");
        assertNull(Crupier.storedRingNeighbor(stored, "missing", Set.of("ana"), true));
        assertNull(Crupier.storedRingNeighbor(stored, "ana", Set.of("missing"), true));
    }

    @Test
    void rejectsUnavailableOrMalformedRings() {
        assertNull(Crupier.storedRingNeighbor(null, "ana", Set.of("bea"), true));
        assertNull(Crupier.storedRingNeighbor("", "ana", Set.of("bea"), true));
        assertNull(Crupier.storedRingNeighbor("%%%", "ana", Set.of("bea"), true));
        assertNull(Crupier.storedRingNeighbor(ring("ana") + "#", "ana", Set.of("bea"), true));
    }
}
