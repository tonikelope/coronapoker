/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxStatsSyncSettingsTest {

    @Test
    void receivingAndSharingAreOptInByDefault() {
        Properties properties = new Properties();

        assertFalse(GdxStatsSyncSettings.receive(properties));
        assertFalse(GdxStatsSyncSettings.share(properties));
    }

    @Test
    void persistedChoicesStillOverrideTheDefaults() {
        Properties properties = new Properties();
        properties.setProperty(GdxStatsSyncSettings.RECEIVE_KEY, "true");
        properties.setProperty(GdxStatsSyncSettings.SHARE_KEY, "true");

        assertTrue(GdxStatsSyncSettings.receive(properties));
        assertTrue(GdxStatsSyncSettings.share(properties));
    }
}
