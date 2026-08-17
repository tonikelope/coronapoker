package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the community panel's pre-layout zoom wait.
 */
class CommunityZoomEventDrivenCanaryTest {

    @Test
    void zoomWaitsForLayoutSignalWithSafetyTimeout() throws IOException {
        String source = Files.readString(locateSource()).replace("\r\n", "\n");

        assertTrue(source.contains("CountDownLatch ready_latch = new CountDownLatch(1)"));
        assertTrue(source.contains("ready_latch.await(2, TimeUnit.SECONDS)"));
        assertTrue(source.contains("ready_latch.countDown()"));
        assertTrue(source.contains("notifyZoomCompletion(notifier)"));
        assertFalse(source.contains("while (!ready)"));
        assertFalse(source.contains("Helpers.pausar(GameFrame.GUI_RENDER_WAIT)"));
    }

    private static Path locateSource() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            Path candidate = path.resolve("src/main/java/com/tonikelope/coronapoker/CommunityCardsPanel.java");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("CommunityCardsPanel.java not found from " + start);
    }
}
