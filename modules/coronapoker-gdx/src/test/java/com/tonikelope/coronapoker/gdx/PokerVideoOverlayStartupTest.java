/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PokerVideoOverlayStartupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void closingSettingsAfterReenablingVideosOnlyShowsTheIcon() {
        try (var overlay = overlay(true)) {
            update(overlay, true, false);
            update(overlay, false, false);
            update(overlay, true, false);
            update(overlay, true, true);

            Rectangle icon = PokerVideoIcon.bounds(1920f, new Rectangle());
            assertTrue(overlay.ownsHover(icon.x + icon.width / 2f,
                    icon.y + icon.height / 2f));
        }
    }

    @Test
    void videosEnabledAfterStartingWithThemOffOnlyShowTheIcon() {
        try (var overlay = overlay(true)) {
            update(overlay, false, true);
            update(overlay, true, true);

            Rectangle icon = PokerVideoIcon.bounds(1920f, new Rectangle());
            assertTrue(overlay.ownsHover(icon.x + icon.width / 2f,
                    icon.y + icon.height / 2f));
        }
    }

    @Test
    void startupStillRespectsTheAutoOpenPreference() throws Exception {
        try (var open = overlay(true); var minimized = overlay(false)) {
            update(open, true, false);
            update(minimized, true, false);
            var minimizedField = PokerVideoOverlay.class.getDeclaredField("minimized");
            minimizedField.setAccessible(true);
            assertFalse(minimizedField.getBoolean(open));
            assertTrue(minimizedField.getBoolean(minimized));
        }
    }

    private PokerVideoOverlay overlay(boolean openOnStartup) {
        return new PokerVideoOverlay(new PokerVideoFavorites(
                temporaryDirectory.resolve("favorites.json")), ignored -> { },
                openOnStartup);
    }

    private static void update(PokerVideoOverlay overlay, boolean enabled,
            boolean visible) {
        overlay.update(true, enabled, visible, "en", PokerVideoSearch.TYPE_NORMAL,
                1920f, 1080f);
    }
}
