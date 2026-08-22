package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SeededSecureRandomTest {

    @Test
    void equalSeedsReplayAndDifferentNodeSeedsDiverge() {
        byte[] first = primaryBytes(new SeededSecureRandom(23059L));
        byte[] replay = primaryBytes(new SeededSecureRandom(23059L));
        byte[] otherNode = primaryBytes(new SeededSecureRandom(23060L));

        assertArrayEquals(first, replay);
        assertFalse(Arrays.equals(first, otherNode));
    }

    @Test
    void repeatedReadsAdvanceTheSingleDeterministicStream() {
        SeededSecureRandom random = new SeededSecureRandom(23059L);
        byte[] first = primaryBytes(random);
        byte[] second = primaryBytes(random);

        assertFalse(Arrays.equals(first, second));
    }

    private static byte[] primaryBytes(SeededSecureRandom random) {
        byte[] bytes = new byte[256];
        random.nextBytes(bytes);
        return bytes;
    }
}
