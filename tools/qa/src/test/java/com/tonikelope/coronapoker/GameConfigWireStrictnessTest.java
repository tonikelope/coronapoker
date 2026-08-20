package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class GameConfigWireStrictnessTest {

    @Test
    public void exactRoundTripKeepsCanonicalBytes() {
        GameConfigWireV1 original = GameConfigWireV1.builder().build().value();
        GameConfigWireV1.Result decoded = GameConfigWireV1.decode(original.encode());
        assertTrue(decoded.isOk());
        assertArrayEquals(original.encode(), decoded.value().encode());
    }

    @Test
    public void trailingDataIsRejectedRatherThanIgnored() {
        byte[] exact = GameConfigWireV1.builder().build().value().encode();
        assertFalse(GameConfigWireV1.decode(Arrays.copyOf(exact, exact.length + 1)).isOk());
    }
}
