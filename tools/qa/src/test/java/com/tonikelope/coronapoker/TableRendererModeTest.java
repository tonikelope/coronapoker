package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TableRendererModeTest {

    private String savedPreference;

    @BeforeEach
    void saveState() {
        savedPreference = Helpers.PROPERTIES.getProperty(TableRendererMode.PROPERTY_KEY);
        TableRendererMode.configureCommandLine(new String[0]);
    }

    @AfterEach
    void restoreState() {
        if (savedPreference == null) {
            Helpers.PROPERTIES.remove(TableRendererMode.PROPERTY_KEY);
        } else {
            Helpers.PROPERTIES.setProperty(TableRendererMode.PROPERTY_KEY, savedPreference);
        }
        TableRendererMode.configureCommandLine(new String[0]);
    }

    @Test
    void gdxIsTheDefaultForNewPreferences() {
        Helpers.PROPERTIES.remove(TableRendererMode.PROPERTY_KEY);
        assertEquals(TableRendererMode.GDX, TableRendererMode.preferred());
        assertEquals(TableRendererMode.GDX, TableRendererMode.effective());
    }

    @Test
    void savedSwingPreferenceIsHonoured() {
        Helpers.PROPERTIES.setProperty(TableRendererMode.PROPERTY_KEY, "swing");
        assertEquals(TableRendererMode.SWING, TableRendererMode.preferred());
        assertEquals(TableRendererMode.SWING, TableRendererMode.effective());
    }

    @Test
    void commandLineOverrideWinsWithoutChangingPreference() {
        Helpers.PROPERTIES.setProperty(TableRendererMode.PROPERTY_KEY, "swing");
        TableRendererMode.configureCommandLine(new String[]{"--gdx"});

        assertTrue(TableRendererMode.hasCommandLineOverride());
        assertEquals(TableRendererMode.GDX, TableRendererMode.effective());
        assertEquals(TableRendererMode.SWING, TableRendererMode.preferred());
    }

    @Test
    void emptyArgumentsClearTheProcessOverride() {
        TableRendererMode.configureCommandLine(new String[]{"--renderer=swing"});
        TableRendererMode.configureCommandLine(new String[0]);
        assertFalse(TableRendererMode.hasCommandLineOverride());
    }

    @Test
    void contradictoryOrUnknownRendererFlagsFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> TableRendererMode.configureCommandLine(new String[]{"--gdx", "--swing"}));
        assertThrows(IllegalArgumentException.class,
                () -> TableRendererMode.configureCommandLine(new String[]{"--table-renderer=software"}));
    }
}
