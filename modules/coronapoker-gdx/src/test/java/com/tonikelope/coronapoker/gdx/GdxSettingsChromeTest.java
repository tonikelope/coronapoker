package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void saveUsesThePositiveMaterialAndRespectsDirtyState()
            throws Exception {
        String chrome = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxSettingsChrome.java"),
                StandardCharsets.UTF_8);

        assertTrue(chrome.contains("boolean saveEnabled, float alpha"));
        assertTrue(chrome.contains("GdxUiButtonStyle.Tone.POSITIVE, pointer, "
                + "saveEnabled, alpha"));
    }

    private static void assertSemanticIcons(
            List<GdxSettingsContract.Section> sections) {
        assertEquals(sections.size(), sections.stream()
                .map(GdxSettingsChrome::mainTabIconFor)
                .distinct()
                .count());
    }
}
