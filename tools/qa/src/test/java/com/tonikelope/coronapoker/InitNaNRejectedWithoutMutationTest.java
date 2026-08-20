package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class InitNaNRejectedWithoutMutationTest {
    @Test
    public void nanConfigCannotReplacePublishedSnapshot() {
        GameConfigWireV1 valid = GameConfigWireV1.builder().build().value();
        AtomicReference<GameConfigWireV1> published = new AtomicReference<>(valid);
        GameConfigWireV1.Result invalid = GameConfigWireV1.builder()
                .smallBlind(Double.NaN).build();
        assertFalse(GameConfigWireV1.publish(invalid, published));
        assertSame(valid, published.get());
    }
}
