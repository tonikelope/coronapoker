package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxRemoteSeatPanelContractTest {

    @Test
    void remoteSeatPanelUsesSurfacesWithoutInternalSeparatorBars()
            throws Exception {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);
        String drawSeats = source.substring(
                source.indexOf("    private void drawSeats()"),
                source.indexOf("    /** Drawn inside an active SpriteBatch. */"));

        assertTrue(drawSeats.contains("roundedRect(seat.podX, seat.podY"),
                "The remote pod must retain its rounded continuous surface");
        assertTrue(drawSeats.contains("roundedRect(seat.podX + 7f"),
                "The action badge must remain an independent rounded surface");
        assertFalse(drawSeats.contains("shapes.rect("),
                "Remote seats must not draw internal straight separator bars");
    }
}
