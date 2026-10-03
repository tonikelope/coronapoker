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
    void exposesTheThreeCompleteDisplayModeNamesWithoutStatusSuffixes() {
        GdxGameText spanish = new GdxGameText("es");

        assertEquals("COMPLETA SIN BORDES",
                GdxWindowMode.BORDERLESS.label(spanish));
        assertEquals("COMPLETA EXCLUSIVA",
                GdxWindowMode.EXCLUSIVE.label(spanish));
        assertEquals("VENTANA", GdxWindowMode.WINDOWED.label(spanish));
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
    void everySuccessfulRuntimeTransitionSynchronizesTheSettingsValue() {
        Properties properties = new Properties();
        properties.setProperty(GdxWindowMode.PREFERENCE_KEY, "borderless");

        GdxDisplayModeController.synchronizePreference(properties,
                GdxWindowMode.WINDOWED);
        assertEquals(GdxWindowMode.WINDOWED,
                GdxWindowMode.configured(properties));

        GdxDisplayModeController.synchronizePreference(properties,
                GdxWindowMode.EXCLUSIVE);
        assertEquals(GdxWindowMode.EXCLUSIVE,
                GdxWindowMode.configured(properties));

        GdxDisplayModeController.synchronizePreference(properties,
                GdxWindowMode.BORDERLESS);
        assertEquals(GdxWindowMode.BORDERLESS,
                GdxWindowMode.configured(properties));
    }

    @Test
    void repeatedF11AndSettingsCyclesNeverDiverge() {
        Properties properties = new Properties();
        GdxDisplayModeController.synchronizePreference(properties,
                GdxWindowMode.EXCLUSIVE);
        GdxWindowMode active = GdxWindowMode.EXCLUSIVE;
        GdxWindowMode preferred = GdxWindowMode.EXCLUSIVE;

        for (int pass = 0; pass < 12; pass++) {
            active = GdxDisplayModeController.toggleTarget(active, preferred);
            GdxDisplayModeController.synchronizePreference(properties, active);
            assertEquals(active, GdxWindowMode.configured(properties));

            GdxWindowMode adjusted = GdxWindowMode.adjusted(properties, 1);
            GdxDisplayModeController.synchronizePreference(properties,
                    adjusted);
            active = adjusted;
            if (active != GdxWindowMode.WINDOWED) preferred = active;
            assertEquals(active, GdxWindowMode.configured(properties));
        }
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

    @Test
    void nativeWakeLockComplementsGlfwOnlyOutsideExclusiveFullscreen() {
        assertEquals(false, GdxDisplayModeController
                .requiresIndependentWakeLock(true));
        assertEquals(true, GdxDisplayModeController
                .requiresIndependentWakeLock(false));
    }
}
