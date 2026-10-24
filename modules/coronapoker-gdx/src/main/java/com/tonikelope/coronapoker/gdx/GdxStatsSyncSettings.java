/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Properties;

/** Canonical keys and defaults for optional statistics synchronization. */
final class GdxStatsSyncSettings {

    static final String RECEIVE_KEY = "sync_stats_receive";
    static final String SHARE_KEY = "sync_stats_share";
    static final boolean RECEIVE_DEFAULT = false;
    static final boolean SHARE_DEFAULT = false;

    private GdxStatsSyncSettings() {
    }

    static boolean receive(Properties properties) {
        return booleanValue(properties, RECEIVE_KEY, RECEIVE_DEFAULT);
    }

    static boolean share(Properties properties) {
        return booleanValue(properties, SHARE_KEY, SHARE_DEFAULT);
    }

    private static boolean booleanValue(Properties properties, String key,
            boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key,
                Boolean.toString(fallback)));
    }
}
