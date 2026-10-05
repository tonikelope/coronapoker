package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.badlogic.gdx.graphics.Color;
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
        assertEquals(0xcc, GdxSettingsStyle.PANEL_RGBA & 0xff,
                "all settings entry points must use 80% panel opacity");
        assertEquals(0, GdxSettingsStyle.CONTENT_RGBA & 0xff,
                "the content well must not compound the glass opacity");
        assertEquals(0x111a2add, GdxSettingsStyle.PANEL_LIGHT_RGBA);
        assertEquals(0x36d9ffff, GdxSettingsStyle.CYAN_RGBA);
        assertEquals(0xffe07aff, GdxSettingsStyle.GOLD_RGBA);
        assertEquals(66f, GdxSettingsStyle.TOGGLE_WIDTH);
        assertEquals(38f, GdxSettingsStyle.TOGGLE_HEIGHT);
        assertEquals(28f, GdxSettingsStyle.TOGGLE_KNOB_TRAVEL);
    }

    @Test
    void hotPathPaletteObjectsAreReusedInsteadOfAllocatedPerFrame() {
        assertSame(GdxSettingsStyle.rowBorder(true, false),
                GdxSettingsStyle.rowBorder(true, false));
        assertSame(GdxSettingsStyle.rowFill(true, false),
                GdxSettingsStyle.rowFill(true, false));
        Color track = GdxSettingsStyle.toggleTrack(0f);
        assertEquals(new Color(GdxSettingsStyle.TOGGLE_OFF_RGBA), track);
        assertSame(track, GdxSettingsStyle.toggleTrack(1f));
        assertEquals(new Color(GdxSettingsStyle.TOGGLE_ON_RGBA), track);
    }
}
