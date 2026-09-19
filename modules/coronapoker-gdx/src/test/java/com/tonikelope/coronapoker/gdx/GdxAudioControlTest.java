package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxAudioControlTest {

    @Test
    void liveToggleUpdatesTheSharedRuntimeStateAndPersistedValueTogether() {
        Properties properties = new Properties();
        GdxAudioControl control = new GdxAudioControl(properties, null);

        control.setEnabled(false);
        assertFalse(control.enabled());
        assertEquals("false", properties.getProperty("sonidos"));

        assertTrue(control.toggle());
        assertTrue(control.enabled());
        assertEquals("true", properties.getProperty("sonidos"));
    }
}
