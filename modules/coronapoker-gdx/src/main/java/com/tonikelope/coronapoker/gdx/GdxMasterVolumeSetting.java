/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** The master volume row uses the same percentage step as the volume shortcut. */
record GdxMasterVolumeSetting(BooleanSupplier soundEnabled, DoubleConsumer apply)
        implements GdxSettingsSlider.Setting {

    @Override public int min() { return 0; }
    @Override public int max() { return 100; }
    @Override public int step() {
        return Math.round(100f * GdxSettingsContract.MASTER_VOLUME_STEP);
    }

    @Override public int value(Properties settings) {
        return Math.round(100f * GdxSettingsContract.masterVolume(settings));
    }

    @Override public boolean enabled(Properties settings) {
        return soundEnabled.getAsBoolean();
    }

    @Override public void select(Properties settings, int value) {
        apply.accept(value / 100f);
    }
}
