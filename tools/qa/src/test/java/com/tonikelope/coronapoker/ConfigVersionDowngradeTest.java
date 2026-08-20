package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ConfigVersionDowngradeTest {
    @Test
    public void onlyCurrentV1IsAcceptedAndCanonicalHashMatches() {
        GameConfigWireV1 first = GameConfigWireV1.builder().build().value();
        GameConfigWireV1 second = GameConfigWireV1.builder().build().value();
        assertArrayEquals(first.canonicalHash(), second.canonicalHash());
        byte[] encoded = first.encode();
        assertTrue(GameConfigWireV1.decode(encoded).isOk());
        encoded[7] = 0; // version int: V1 -> V0; there is deliberately no legacy parser.
        assertFalse(GameConfigWireV1.decode(encoded).isOk());
    }
}
