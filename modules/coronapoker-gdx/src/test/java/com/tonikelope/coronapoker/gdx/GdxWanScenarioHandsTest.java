/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxWanScenarioHandsTest {
    private static final List<String> KEYS = List.of(GdxWanScenarioHands.SUSTAINED,
            GdxWanScenarioHands.EXTREME, GdxWanScenarioHands.LEGACY_SOAK);

    @Test void explicitWanDepthWinsWithoutChangingLegacyStandaloneInvocations() {
        withProperties(Map.of(GdxWanScenarioHands.LEGACY_SOAK, "20"), () -> {
            assertEquals(20, GdxWanScenarioHands.sustained());
            assertEquals(3, GdxWanScenarioHands.extreme());
        });
        withProperties(Map.of(GdxWanScenarioHands.LEGACY_SOAK, "20",
                GdxWanScenarioHands.SUSTAINED, "5", GdxWanScenarioHands.EXTREME, "2"), () -> {
            assertEquals(5, GdxWanScenarioHands.sustained());
            assertEquals(2, GdxWanScenarioHands.extreme());
        });
    }

    @Test void standaloneDefaultsAndLongOverridesKeepMultipleHands() {
        withProperties(Map.of(), () -> {
            assertEquals(3, GdxWanScenarioHands.sustained());
            assertEquals(3, GdxWanScenarioHands.extreme());
        });
        withProperties(Map.of(GdxWanScenarioHands.SUSTAINED, "50",
                GdxWanScenarioHands.EXTREME, "3"), () -> {
            assertEquals(50, GdxWanScenarioHands.sustained());
            assertEquals(3, GdxWanScenarioHands.extreme());
        });
    }

    @Test void invalidDepthCannotSilentlyRemoveTheTransitionOrUseADifferentBudget() {
        for (String value : List.of("0", "1", "-1", "1001", "broken")) {
            withProperties(Map.of(GdxWanScenarioHands.SUSTAINED, value), () ->
                    assertThrows(IllegalArgumentException.class, GdxWanScenarioHands::sustained));
            withProperties(Map.of(GdxWanScenarioHands.EXTREME, value), () ->
                    assertThrows(IllegalArgumentException.class, GdxWanScenarioHands::extreme));
        }
    }

    private static void withProperties(Map<String, String> values, Runnable assertions) {
        Map<String, String> previous = new HashMap<>();
        KEYS.forEach(key -> previous.put(key, System.getProperty(key)));
        try {
            KEYS.forEach(System::clearProperty);
            values.forEach(System::setProperty);
            assertions.run();
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }
}
