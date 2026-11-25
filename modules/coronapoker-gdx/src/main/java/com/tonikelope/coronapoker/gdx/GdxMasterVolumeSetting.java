/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Properties;

/** The master volume row uses the same percentage step as the volume shortcut. */
final class GdxMasterVolumeSetting implements GdxSettingsSlider.Setting {

    @Override public int min() { return 0; }
    @Override public int max() { return 100; }
    @Override public int step() {
        return Math.round(100f * GdxSettingsContract.MASTER_VOLUME_STEP);
    }

    @Override public int value(Properties settings) {
        return Math.round(100f * GdxSettingsContract.masterVolume(settings));
    }

    @Override public boolean enabled(Properties settings) {
        return Boolean.parseBoolean(settings.getProperty("sonidos", "true"));
    }

    @Override public void select(Properties settings, int value) {
        settings.setProperty("master_volume", Float.toString(value / 100f));
    }
}
