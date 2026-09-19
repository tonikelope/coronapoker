/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxWindowModeTest {

    @Test
    void usesBorderlessAsTheIndependentGdxDefault() {
        Properties defaults = new Properties();
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxWindowMode.configured(defaults));
        defaults.setProperty("auto_fullscreen", "false");
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxWindowMode.configured(defaults));
    }

    @Test
    void cyclesAllThreePersistedModes() {
        Properties properties = new Properties();
        assertEquals(GdxWindowMode.WINDOWED,
                GdxWindowMode.cycle(properties));
        assertEquals("windowed", properties.getProperty(
                GdxWindowMode.PREFERENCE_KEY));
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxWindowMode.cycle(properties));
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxWindowMode.cycle(properties));
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxWindowMode.adjust(properties, -1));
    }

    @Test
    void explicitLauncherArgumentOverridesPersistedMode() {
        Properties properties = new Properties();
        properties.setProperty(GdxWindowMode.PREFERENCE_KEY, "exclusive");
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxWindowMode.requested(new String[]{"--borderless"},
                        properties));
        assertEquals(GdxWindowMode.WINDOWED,
                GdxWindowMode.requested(new String[]{"--windowed"},
                        properties));
    }

    @Test
    void f11ReturnsToTheConfiguredFullscreenKind() {
        assertEquals(GdxWindowMode.WINDOWED,
                GdxDisplayModeController.toggleTarget(
                        GdxWindowMode.BORDERLESS,
                        GdxWindowMode.BORDERLESS));
        assertEquals(GdxWindowMode.WINDOWED,
                GdxDisplayModeController.toggleTarget(
                        GdxWindowMode.EXCLUSIVE,
                        GdxWindowMode.EXCLUSIVE));
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxDisplayModeController.toggleTarget(
                        GdxWindowMode.WINDOWED,
                        GdxWindowMode.BORDERLESS));
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxDisplayModeController.toggleTarget(
                        GdxWindowMode.WINDOWED,
                        GdxWindowMode.EXCLUSIVE));
    }

    @Test
    void configuredFullscreenKindWinsOverStalePreviewMemory() {
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxDisplayModeController.fullscreenPreference(
                        GdxWindowMode.BORDERLESS,
                        GdxWindowMode.EXCLUSIVE));
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxDisplayModeController.fullscreenPreference(
                        GdxWindowMode.EXCLUSIVE,
                        GdxWindowMode.BORDERLESS));
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxDisplayModeController.fullscreenPreference(
                        GdxWindowMode.WINDOWED,
                        GdxWindowMode.EXCLUSIVE));
    }
}
