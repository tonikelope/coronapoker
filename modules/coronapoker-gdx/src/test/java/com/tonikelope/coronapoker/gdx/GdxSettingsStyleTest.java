package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxSettingsStyleTest {

    @Test
    void canonicalTypographyMatchesTheMainSettingsScreen() {
        assertEquals(58, GdxSettingsStyle.TITLE_FONT_SIZE);
        assertEquals(30, GdxSettingsStyle.HEADING_FONT_SIZE);
        assertEquals(26, GdxSettingsStyle.ACTION_FONT_SIZE);
        assertEquals(24, GdxSettingsStyle.BODY_FONT_SIZE);
        assertEquals(18, GdxSettingsStyle.SMALL_FONT_SIZE);
        assertEquals(15, GdxSettingsStyle.TINY_FONT_SIZE);
    }

    @Test
    void canonicalControlsRetainTheMainScreenPaletteAndGeometry() {
        assertEquals(0x111a2add, GdxSettingsStyle.PANEL_LIGHT_RGBA);
        assertEquals(0x36d9ffff, GdxSettingsStyle.CYAN_RGBA);
        assertEquals(0xffe07aff, GdxSettingsStyle.GOLD_RGBA);
        assertEquals(66f, GdxSettingsStyle.TOGGLE_WIDTH);
        assertEquals(38f, GdxSettingsStyle.TOGGLE_HEIGHT);
        assertEquals(28f, GdxSettingsStyle.TOGGLE_KNOB_TRAVEL);
    }
}
