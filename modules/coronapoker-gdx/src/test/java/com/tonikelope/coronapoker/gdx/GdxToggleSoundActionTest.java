package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GdxToggleSoundActionTest {

    @Test
    void disablingSoundsBeforeTheMutation() {
        List<String> events = new ArrayList<>();

        GdxToggleSoundAction.run(true,
                () -> events.add("action"),
                enabled -> events.add("sound:" + enabled));

        assertEquals(List.of("sound:false", "action"), events);
    }

    @Test
    void enablingSoundsAfterTheMutation() {
        List<String> events = new ArrayList<>();

        GdxToggleSoundAction.run(false,
                () -> events.add("action"),
                enabled -> events.add("sound:" + enabled));

        assertEquals(List.of("action", "sound:true"), events);
    }
}
