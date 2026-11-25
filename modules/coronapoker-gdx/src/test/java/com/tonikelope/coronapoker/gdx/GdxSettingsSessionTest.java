package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

final class GdxSettingsSessionTest {

    @Test
    void tableViewDoesNotChangeUntilTheDraftIsCommitted() {
        Properties preferences = new Properties();
        GdxSettingsSession session = new GdxSettingsSession();
        for (var context : GdxSettingsSession.Context.values()) {
            session.begin(context, preferences);
            assertFalse(session.propertiesChanged());
            GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, session.draftProperties(), 0);
            assertTrue(session.propertiesChanged());
            assertTrue(GdxAppearanceOptions.perspectiveTable(preferences));
            session.close();
            assertTrue(GdxAppearanceOptions.perspectiveTable(preferences));
        }
    }

    @Test
    void independentDiskFlushCannotWriteDraftAndSaveMergesOnlyEditedKeys(
            @TempDir Path directory) throws Exception {
        var file = directory.resolve("settings.properties");
        try (var prefs = new PreferencesService(file)) {
            prefs.start();
            var properties = prefs.properties();
            properties.setProperty(GdxShuffleAngle.KEY, "45");
            properties.setProperty("musica", "true");
            var session = new GdxSettingsSession();
            session.begin(GdxSettingsSession.Context.LIVE_TABLE, properties);
            var draft = session.draftProperties();
            GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, draft, 1);
            assertFalse(session.propertiesChanged());
            draft.setProperty(GdxShuffleAngle.KEY, "85");
            draft.setProperty("gdx_show_fps", "true");
            properties.setProperty("musica", "false");
            properties.setProperty("unrelated", "keep");
            prefs.save();
            try (var reloaded = new PreferencesService(file)) {
                assertEquals("45", reloaded.properties().getProperty(GdxShuffleAngle.KEY));
                assertFalse(reloaded.properties().containsKey("gdx_show_fps"));
            }
            session.commitProperties(properties);
            prefs.save();
        }
        try (var reloaded = new PreferencesService(file)) {
            var properties = reloaded.properties();
            assertEquals("85", properties.getProperty(GdxShuffleAngle.KEY));
            assertEquals("true", properties.getProperty("gdx_show_fps"));
            assertEquals("false", properties.getProperty("musica"));
            assertFalse(properties.containsKey(GdxSettingsContract.TABLE_VIEW_KEY));
            assertEquals("keep", properties.getProperty("unrelated"));
        }
    }

    @Test
    void frameRateDraftAppliesOnlyWhenSaved(@TempDir Path directory) throws Exception {
        var file = directory.resolve("frame-rate.properties");
        try (var prefs = new PreferencesService(file)) {
            prefs.start();
            var settings = prefs.properties();
            var presentation = new GdxGamePresentationSettings(prefs);
            var session = new GdxSettingsSession();

            for (var context : GdxSettingsSession.Context.values()) {
                GdxFrameRateLimit.reset(settings);
                GdxFrameRateLimit.CUSTOM.select(settings, 165);
                session.begin(context, settings);
                var draft = session.draftProperties();
                assertEquals(165, GdxFrameRateLimit.CUSTOM.value(draft));
                GdxFrameRateLimit.CUSTOM.select(draft, 197);
                GdxFrameRateLimit.selectMode(draft, 0);
                assertTrue(session.propertiesChanged());
                assertEquals(165, presentation.effectiveFrameRateLimit(240));
                prefs.save();
                try (var reloaded = new PreferencesService(file)) {
                    assertEquals(165, GdxFrameRateLimit.CUSTOM.value(reloaded.properties()));
                    assertFalse(GdxFrameRateLimit.matchRefresh(reloaded.properties()));
                }

                session.close();
                session.begin(context, settings);
                assertEquals(165, GdxFrameRateLimit.CUSTOM.value(draft));
                assertFalse(session.propertiesChanged());
                assertEquals(165, presentation.effectiveFrameRateLimit(240));

                int graphics = GdxSettingsContract.appearanceGraphicsPageIndex();
                GdxSettingsContract.restoreAppearancePageDefaults(draft, graphics, true, false);
                assertTrue(GdxSettingsContract.appearancePageAtDefaults(draft, graphics, true, false));
                assertEquals(165, presentation.effectiveFrameRateLimit(240));
                session.commitProperties(settings);
                assertEquals(120, presentation.effectiveFrameRateLimit(240));
                session.close();

                session.begin(context, settings);
                GdxFrameRateLimit.CUSTOM.select(draft, 197);
                GdxFrameRateLimit.selectMode(draft, 0);
                assertEquals(120, presentation.effectiveFrameRateLimit(240));
                session.commitProperties(settings);
                assertEquals(241, presentation.effectiveFrameRateLimit(240));
                assertEquals(197, GdxFrameRateLimit.CUSTOM.value(settings));
                session.close();
            }
        }
    }

    @Test
    void dialogDirectionAndDefaultsStayInTheDraftUntilSave() {
        var session = new GdxSettingsSession();
        var properties = new Properties();
        var choice = GdxAppearanceOptions.DIALOG_SLIDE_DIRECTION;
        for (var context : GdxSettingsSession.Context.values()) {
            properties.setProperty(choice.key(), "off");
            session.begin(context, properties);
            var draft = session.draftProperties();
            GdxAppearanceOptions.select(choice, draft, 1);
            GdxAppearanceOptions.select(choice, draft, 2);
            assertEquals("off", properties.getProperty(choice.key()));
            assertEquals(GdxDialogSlide.Direction.OFF, session.dialogSlideDirection(properties));
            assertTrue(session.propertiesChanged());
            session.close();
            session.begin(context, properties);
            assertFalse(session.propertiesChanged());
            assertEquals(4, GdxAppearanceOptions.selectedIndex(choice, draft));

            GdxSettingsContract.restoreAppearancePageDefaults(draft, 1, true, false);
            assertTrue(GdxSettingsContract.appearancePageAtDefaults(draft, 1, true, false));
            assertEquals("off", properties.getProperty(choice.key()));
            assertTrue(session.propertiesChanged());
            session.commitProperties(properties);
            session.close();
            assertEquals(GdxDialogSlide.Direction.UP, session.dialogSlideDirection(properties));
        }
    }

    @Test
    void menuAndLiveTableUseTheSameSessionWithContextualSections() {
        GdxSettingsSession session = new GdxSettingsSession();
        session.begin(GdxSettingsSession.Context.MENU, new Properties());
        assertEquals(GdxSettingsContract.MENU_SECTIONS, session.sections());
        assertEquals(GdxSettingsContract.WAITING_ROOM_GAME_PAGES,
                session.gamePages());
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
    void discardingTheDraftLeavesEverySharedPreferenceIntact() {
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

        session.draftProperties().setProperty("baraja", "goliat4");
        session.draftProperties().setProperty("shortcut.pause", "P");
        session.draftProperties().setProperty("auto_action_buttons", "true");
        session.draftProperties().setProperty("auto_action_persist", "false");
        session.draftProperties().setProperty("modo_auto_confirm", "false");
        session.draftProperties().setProperty("confirmar_todo", "true");
        session.draftProperties().setProperty(GdxSettingsContract.AUTO_REBUY_KEY, "true");
        session.draftProperties().setProperty("auto_call_enabled", "true");
        session.draftProperties().setProperty("auto_call_max", "4.5");
        assertTrue(session.propertiesChanged());
        session.close();
        assertFalse(session.propertiesChanged());
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
