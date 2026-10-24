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
        properties.setProperty("auto_action_buttons", "false");
        properties.setProperty("auto_action_persist", "true");
        properties.setProperty("modo_auto_confirm", "true");
        properties.setProperty("confirmar_todo", "false");
        properties.setProperty(GdxSettingsContract.AUTO_REBUY_KEY, "false");
        properties.setProperty("auto_call_enabled", "false");
        properties.setProperty("auto_call_max", "0");
        GdxSettingsSession session = new GdxSettingsSession();
        session.begin(GdxSettingsSession.Context.LIVE_TABLE, properties);

        properties.setProperty("baraja", "goliat4");
        properties.setProperty("shortcut.pause", "P");
        properties.setProperty("auto_action_buttons", "true");
        properties.setProperty("auto_action_persist", "false");
        properties.setProperty("modo_auto_confirm", "false");
        properties.setProperty("confirmar_todo", "true");
        properties.setProperty(GdxSettingsContract.AUTO_REBUY_KEY, "true");
        properties.setProperty("auto_call_enabled", "true");
        properties.setProperty("auto_call_max", "4.5");
        assertTrue(session.propertiesChanged(properties));
        session.restore(properties);
        assertFalse(session.propertiesChanged(properties));
        assertEquals("goliat", properties.getProperty("baraja"));
        assertEquals("ALT+P", properties.getProperty("shortcut.pause"));
        assertEquals("false", properties.getProperty("auto_action_buttons"));
        assertEquals("true", properties.getProperty("auto_action_persist"));
        assertEquals("true", properties.getProperty("modo_auto_confirm"));
        assertEquals("false", properties.getProperty("confirmar_todo"));
        assertEquals("false", properties.getProperty(
                GdxSettingsContract.AUTO_REBUY_KEY));
        assertEquals("false", properties.getProperty("auto_call_enabled"));
        assertEquals("0", properties.getProperty("auto_call_max"));

        session.close();
        assertFalse(session.open());
    }
}
