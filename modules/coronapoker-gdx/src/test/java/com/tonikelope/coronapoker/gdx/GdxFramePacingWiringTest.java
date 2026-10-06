package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxFramePacingWiringTest {

    @Test
    void launcherUsesTheTargetMonitorAndConfiguresBothPacingControls()
            throws Exception {
        String source = source("GdxLauncher.java");

        assertTrue(source.contains("getPrimaryMonitor()"));
        assertTrue(source.contains("getDisplayMode(\n                targetMonitor)"));
        assertTrue(source.contains(
                "config.useVsync(presentationSettings.vsyncEnabled())"));
        assertTrue(source.contains(
                "config.setForegroundFPS(presentationSettings.effectiveFrameRateLimit("));
        assertFalse(source.contains("fastestDisplayMode()"),
                "initial pacing must describe the monitor that owns the window");
    }

    @Test
    void shellAppliesLiveChangesOnlyWhenTheirEffectiveValueChanges()
            throws Exception {
        String source = source("GdxApplicationShell.java");
        int pacingStart = source.indexOf(
                "private void updateForegroundFrameRate(float delta)");
        int vsyncStart = source.indexOf(
                "private void applySelectedVsync(boolean force)", pacingStart);
        int nextMethod = source.indexOf("boolean audioOutputAvailable()",
                vsyncStart);
        assertTrue(pacingStart >= 0 && vsyncStart > pacingStart
                && nextMethod > vsyncStart);

        String pacing = source.substring(pacingStart, vsyncStart);
        assertTrue(pacing.contains("Gdx.graphics.getMonitor()"));
        assertTrue(pacing.contains("Gdx.graphics.getDisplayMode(monitor)"));
        assertTrue(pacing.contains("frameRateMonitorRefresh = mode.refreshRate"));
        assertTrue(pacing.contains("target != appliedForegroundFps"));
        assertTrue(pacing.contains("Gdx.graphics.setForegroundFPS(target)"));

        String vsync = source.substring(vsyncStart, nextMethod);
        assertTrue(vsync.contains("appliedVsync != selected"));
        assertTrue(vsync.contains("Gdx.graphics.setVSync(selected)"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/" + file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
