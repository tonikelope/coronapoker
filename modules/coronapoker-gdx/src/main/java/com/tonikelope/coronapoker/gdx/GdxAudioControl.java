/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.PreferencesService;
import java.util.Objects;
import java.util.Properties;

/** Shared GDX view of CoronaPoker's persisted master sound switch. */
final class GdxAudioControl {

    private static final Object SESSION_LOCK = new Object();
    private static Boolean sessionEnabled;
    private final Properties properties;
    private final PreferencesService preferences;

    GdxAudioControl(Properties properties, PreferencesService preferences) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.preferences = preferences;
        synchronized (SESSION_LOCK) {
            if (sessionEnabled == null) {
                sessionEnabled = !Boolean.getBoolean("coronapoker.gdx.silent")
                        && Boolean.parseBoolean(
                                properties.getProperty("sonidos", "true"));
            }
        }
    }

    boolean enabled() {
        synchronized (SESSION_LOCK) {
            return sessionEnabled;
        }
    }

    boolean toggle() {
        return toggle(true);
    }

    boolean toggle(boolean persist) {
        return setEnabled(!enabled(), persist);
    }

    boolean setEnabled(boolean enabled) {
        return setEnabled(enabled, true);
    }

    boolean setEnabled(boolean enabled, boolean persist) {
        synchronized (SESSION_LOCK) {
            sessionEnabled = enabled;
        }
        properties.setProperty("sonidos", Boolean.toString(enabled));
        if (persist && preferences != null) {
            preferences.saveDeferred();
        }
        return enabled;
    }
}
