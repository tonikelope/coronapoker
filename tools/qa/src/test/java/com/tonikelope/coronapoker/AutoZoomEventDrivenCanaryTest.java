/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** Regression guard for auto-fit zoom: it must not simulate asynchronous menu clicks or poll. */
public class AutoZoomEventDrivenCanaryTest {

    @Test
    public void autoZoomUsesSynchronousZoomAndNoFixedPolling() throws IOException {
        String source = Files.readString(locateSourceDir().resolve("TablePanel.java"));

        assertTrue(source.contains("applyZoomLevelSynchronouslyForAutoZoom"),
                "autoZoom must apply the zoom operation directly and wait for completion");
        assertFalse(source.contains("getZoom_menu_reset()::doClick"),
                "autoZoom must not simulate the asynchronous reset menu action");
        assertFalse(source.contains("getZoom_menu_out()::doClick"),
                "autoZoom must not simulate the asynchronous zoom-out menu action");
        assertFalse(source.contains("Helpers.pausar(GameFrame.GUI_RENDER_WAIT)"),
                "autoZoom must not poll layout with a fixed 125 ms sleep");
        assertFalse(source.contains("public synchronized void autoZoom"),
                "autoZoom must not hold a monitor while waiting on Swing");
    }

    private static Path locateSourceDir() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            Path candidate = p.resolve("src/main/java/com/tonikelope/coronapoker");
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("NewGameDialog.java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No encuentro el fuente de CoronaPoker desde " + start);
    }
}
