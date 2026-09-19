package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxSettingsSessionTest {

    @Test
    void menuAndLiveTableUseTheSameSessionWithContextualSections() {
        GdxSettingsSession session = new GdxSettingsSession();
        session.begin(GdxSettingsSession.Context.MENU, new Properties());
        assertEquals(GdxSettingsContract.MENU_SECTIONS, session.sections());
        assertTrue(session.gamePages().isEmpty());
        session.selectTab(99);
        assertEquals(GdxSettingsContract.Section.DEBUG, session.section());

        session.begin(GdxSettingsSession.Context.LIVE_TABLE,
                new Properties());
        assertEquals(GdxSettingsContract.TABLE_SECTIONS, session.sections());
        assertEquals(GdxSettingsContract.LIVE_TABLE_GAME_PAGES,
                session.gamePages());
        assertEquals(GdxSettingsContract.Section.APPEARANCE,
                session.section());
    }

    @Test
    void waitingRoomIsExplicitAndUsesTheSharedGameSection() {
        GdxSettingsSession session = new GdxSettingsSession();

        session.begin(GdxSettingsSession.Context.WAITING_ROOM,
                new Properties());

        assertEquals(GdxSettingsSession.Context.WAITING_ROOM,
                session.context());
        assertEquals(GdxSettingsContract.TABLE_SECTIONS,
                session.sections());
        assertEquals(GdxSettingsContract.WAITING_ROOM_GAME_PAGES,
                session.gamePages());
    }

    @Test
    void cancelRestoresTheExactSharedPreferenceSnapshot() {
        Properties properties = new Properties();
        properties.setProperty("baraja", "goliat");
        properties.setProperty("shortcut.pause", "ALT+P");
        GdxSettingsSession session = new GdxSettingsSession();
        session.begin(GdxSettingsSession.Context.LIVE_TABLE, properties);

        properties.setProperty("baraja", "goliat4");
        properties.setProperty("shortcut.pause", "P");
        assertTrue(session.propertiesChanged(properties));
        session.restore(properties);
        assertFalse(session.propertiesChanged(properties));
        assertEquals("goliat", properties.getProperty("baraja"));
        assertEquals("ALT+P", properties.getProperty("shortcut.pause"));

        session.close();
        assertFalse(session.open());
    }
}
