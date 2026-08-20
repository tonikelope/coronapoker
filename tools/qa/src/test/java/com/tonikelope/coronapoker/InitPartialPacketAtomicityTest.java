package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class InitPartialPacketAtomicityTest {
    @Test
    public void everyTruncationLeavesPreviousSnapshotUntouched() {
        GameConfigWireV1 valid = GameConfigWireV1.builder().build().value();
        byte[] encoded = valid.encode();
        for (int length = 0; length < encoded.length; length++) {
            AtomicReference<GameConfigWireV1> published = new AtomicReference<>(valid);
            assertFalse(GameConfigWireV1.decodeAndPublish(
                    Arrays.copyOf(encoded, length), published));
            assertSame(valid, published.get());
        }
    }
}
