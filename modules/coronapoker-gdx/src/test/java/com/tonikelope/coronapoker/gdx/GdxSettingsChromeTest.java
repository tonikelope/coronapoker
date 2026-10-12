package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxSettingsChromeTest {

    @Test
    void everySettingsSectionOwnsOneSemanticIcon() {
        assertEquals(GdxSettingsChrome.MainTabIcon.APPEARANCE,
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.APPEARANCE));
        assertEquals(GdxSettingsChrome.MainTabIcon.AUDIO,
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.AUDIO));
        assertEquals(GdxSettingsChrome.MainTabIcon.CONTROLS,
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.SHORTCUTS));
        assertEquals(GdxSettingsChrome.MainTabIcon.GAME,
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.GAME));
        assertEquals(GdxSettingsChrome.MainTabIcon.DEBUG,
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.DEBUG));

        assertEquals(GdxSettingsContract.Section.values().length,
                EnumSet.allOf(GdxSettingsChrome.MainTabIcon.class).size());
    }

    @Test
    void menuAndTableOrdersCannotChangeAnIconsMeaning() {
        assertSemanticIcons(GdxSettingsContract.MENU_SECTIONS);
        assertSemanticIcons(GdxSettingsContract.TABLE_SECTIONS);

        assertNotEquals(GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.SHORTCUTS),
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.GAME));
        assertNotEquals(GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.GAME),
                GdxSettingsChrome.mainTabIconFor(
                        GdxSettingsContract.Section.DEBUG));
    }

    private static void assertSemanticIcons(
            List<GdxSettingsContract.Section> sections) {
        assertEquals(sections.size(), sections.stream()
                .map(GdxSettingsChrome::mainTabIconFor)
                .distinct()
                .count());
    }
}
