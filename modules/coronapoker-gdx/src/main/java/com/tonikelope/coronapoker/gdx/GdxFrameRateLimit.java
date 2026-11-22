/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.List;
import java.util.Properties;

enum GdxFrameRateLimit implements GdxSettingsSlider.Setting {
    CUSTOM;

    static final String KEY = "gdx_fps_limit";
    static final String MODE_KEY = "gdx_fps_match_refresh";
    static final int MIN = 30;
    static final int MAX = 400;
    static final int DEFAULT = GdxSettingsContract.DEFAULT_FRAME_RATE_LIMIT;
    private static final int MATCH_REFRESH_OPTION = 0;
    private static final int CUSTOM_OPTION = 1;

    static boolean matchRefresh(Properties settings) {
        String mode = settings.getProperty(MODE_KEY);
        // Older profiles used a limit of zero to follow the display.
        return mode == null ? configuredLimit(settings) == 0 : Boolean.parseBoolean(mode);
    }

    static int selectedMode(Properties settings) {
        return matchRefresh(settings) ? MATCH_REFRESH_OPTION : CUSTOM_OPTION;
    }

    static List<String> modeLabels(GdxGameText text) {
        return List.of(text.translate("gdx.settings.value.monitor_refresh"),
                text.translate("gdx.settings.value.custom_fps"));
    }

    static String selectedModeLabel(Properties settings, GdxGameText text) {
        if (matchRefresh(settings)) {
            return text.translate("gdx.settings.value.monitor_refresh");
        }
        return GdxSettingsContract.markDefault(text.translate("gdx.settings.value.custom_fps"),
                CUSTOM.value(settings) == DEFAULT);
    }

    static void selectMode(Properties settings, int option) {
        if (option == MATCH_REFRESH_OPTION || option == CUSTOM_OPTION) {
            settings.setProperty(MODE_KEY, Boolean.toString(option == MATCH_REFRESH_OPTION));
        }
    }

    static void reset(Properties settings) {
        CUSTOM.select(settings, DEFAULT);
        settings.remove(MODE_KEY);
    }

    static boolean isDefault(Properties settings) {
        return !matchRefresh(settings) && configuredLimit(settings) == DEFAULT;
    }

    private static int configuredLimit(Properties settings) {
        String raw = settings.getProperty(KEY);
        if (raw == null) return DEFAULT;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException invalid) {
            return DEFAULT;
        }
    }

    static boolean inRange(int value) {
        return value >= MIN && value <= MAX;
    }

    @Override public int min() { return MIN; }
    @Override public int max() { return MAX; }
    @Override public int step() { return 1; }

    @Override public int value(Properties settings) {
        int configured = configuredLimit(settings);
        return inRange(configured) ? configured : DEFAULT;
    }

    @Override public boolean enabled(Properties settings) {
        return !matchRefresh(settings);
    }

    @Override public void select(Properties settings, int value) {
        settings.setProperty(KEY, Integer.toString(value));
    }
}
