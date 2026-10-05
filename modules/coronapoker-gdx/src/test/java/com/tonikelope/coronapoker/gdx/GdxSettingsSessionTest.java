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

        session.close();
        session.begin(GdxSettingsSession.Context.MENU, new Properties());
        assertEquals(GdxSettingsContract.Section.DEBUG, session.section());
    }

    @Test
    void remembersTheLastTabOnlyForTheCurrentRuntimeContext() {
        GdxSettingsSession session = new GdxSettingsSession();

        session.begin(GdxSettingsSession.Context.WAITING_ROOM,
                new Properties());
        assertEquals(GdxSettingsContract.Section.APPEARANCE,
                session.section());
        session.selectTab(3);
        assertEquals(GdxSettingsContract.Section.GAME, session.section());
        session.close();

        session.begin(GdxSettingsSession.Context.WAITING_ROOM,
                new Properties());
        assertEquals(GdxSettingsContract.Section.GAME, session.section());

        session.close();
        session.begin(GdxSettingsSession.Context.LIVE_TABLE,
                new Properties());
        assertEquals(GdxSettingsContract.Section.APPEARANCE,
                session.section());
    }

    @Test
    void firstOpenStartsAtFirstTabAndFirstSubpageInEveryContext() {
        GdxSettingsSession session = new GdxSettingsSession();

        for (GdxSettingsSession.Context context
                : GdxSettingsSession.Context.values()) {
            session.begin(context, new Properties());
            assertEquals(0, session.tabIndex());
            assertEquals(GdxSettingsContract.Section.APPEARANCE,
                    session.section());
            assertEquals(0, session.subpageIndex());
            session.close();
        }
    }

    @Test
    void remembersSubpagesPerSectionAndRuntimeContext() {
        GdxSettingsSession session = new GdxSettingsSession();

        session.begin(GdxSettingsSession.Context.WAITING_ROOM,
                new Properties());
        session.selectSubpage(2);
        session.selectTab(3);
        session.selectSubpage(4);
        session.close();

        session.begin(GdxSettingsSession.Context.WAITING_ROOM,
                new Properties());
        assertEquals(GdxSettingsContract.Section.GAME, session.section());
        assertEquals(4, session.subpageIndex());
        session.selectTab(0);
        assertEquals(2, session.subpageIndex());
        session.close();

        session.begin(GdxSettingsSession.Context.LIVE_TABLE,
                new Properties());
        assertEquals(GdxSettingsContract.Section.APPEARANCE,
                session.section());
        assertEquals(0, session.subpageIndex());
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
