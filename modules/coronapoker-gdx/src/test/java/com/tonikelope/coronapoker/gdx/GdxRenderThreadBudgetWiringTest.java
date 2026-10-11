package com.tonikelope.coronapoker.gdx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxRenderThreadBudgetWiringTest {

    @Test
    void tableHasNoDeadFrameStatisticsOrDuplicateMonitorPolling()
            throws Exception {
        String table = source("CoronaPokerGdxTable.java");
        assertFalse(table.contains("recordFrame("));
        assertFalse(table.contains("frameSamples"));
        assertFalse(table.contains("getDisplayMode(monitor)"));
        assertTrue(table.contains("networkBlockCache.update(delta"));
    }

    @Test
    void frontendAndTableDrawFromTheLowFrequencyNetworkCache()
            throws Exception {
        String frontend = source("GdxFrontendScreen.java");
        String table = source("CoronaPokerGdxTable.java");
        assertTrue(frontend.contains("networkBlockCache.update(frameDelta"));
        assertTrue(frontend.contains("networkBlockCache.current()"));
        assertTrue(table.contains("networkBlockCache.current()"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/" + file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
