package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

final class GdxFrontendPasswordAndFooterTest {

    @Test
    void passwordMaskGlyphIsRasterizedAndTracksUnicodeCodePoints() {
        assertTrue(GdxFrontendScreen.FRONTEND_EXTRA_FONT_CHARACTERS.contains(
                GdxFrontendScreen.PASSWORD_MASK_GLYPH));
        assertEquals("\u2022\u2022\u2022",
                GdxFrontendScreen.maskedPassword("a\ud83d\ude00b"));
        assertEquals("\u2022\u2022\u2022",
                GdxFrontendScreen.passwordDisplay("abc", true, false));
        assertEquals("abc",
                GdxFrontendScreen.passwordDisplay("abc", true, true));
    }

    @Test
    void passwordEyeStaysInsideTheFieldWithoutCoveringItsTextArea() {
        Rectangle field = new Rectangle(610f, 475f, 450f, 70f);
        Rectangle eye = GdxFrontendScreen.passwordRevealBounds(
                field.x, field.y, field.width);
        assertTrue(field.contains(eye));
        assertEquals(12f, field.x + field.width - eye.x - eye.width);
        assertEquals(44f, eye.width);
    }

    @Test
    void lobbyPasswordActionDistinguishesSetFromChange() {
        assertEquals("auth.menu_poner_password",
                GdxFrontendScreen.lobbyPasswordActionKey(""));
        assertEquals("auth.menu_poner_password",
                GdxFrontendScreen.lobbyPasswordActionKey("   "));
        assertEquals("auth.menu_cambiar_password",
                GdxFrontendScreen.lobbyPasswordActionKey("secret"));
        assertFalse(GdxFrontendScreen.lobbyPasswordEnabled(""));
        assertFalse(GdxFrontendScreen.lobbyPasswordEnabled("   "));
        assertTrue(GdxFrontendScreen.lobbyPasswordEnabled("secret"));
    }

    @Test
    void mainMenuKeepsOnlyTheAudioControlInTheBottomRightCorner() {
        Rectangle bounds = GdxFrontendScreen.mainMenuSoundBounds();
        assertEquals(GdxFrontendScreen.MENU_SOUND_BOTTOM_MARGIN, bounds.y);
        assertEquals(GdxFrontendScreen.MENU_SOUND_SIZE, bounds.width);
        assertEquals(GdxFrontendScreen.MENU_SOUND_SIZE, bounds.height);
        assertEquals(GdxFrontendScreen.MENU_SOUND_RIGHT_MARGIN,
                1920f - bounds.x - bounds.width);
        assertTrue(bounds.y + bounds.height
                < GdxFrontendScreen.MENU_QUOTE_SINGLE_BASELINE,
                "the smaller audio control must stay below the quote line");
    }

    @Test
    void mainMenuQuotesUseTheFullWidthAtOneFixedSize() {
        assertEquals(1920f - 2f * GdxFrontendScreen.MENU_QUOTE_SIDE_MARGIN,
                GdxFrontendScreen.MENU_QUOTE_MAX_WIDTH);
        assertEquals(2, GdxFrontendScreen.MENU_QUOTE_MAX_LINES);
        assertEquals(GdxSettingsStyle.SMALL_FONT_SIZE, 18);
        assertTrue(GdxFrontendScreen.MENU_QUOTE_TWO_LINE_TOP_BASELINE
                - GdxFrontendScreen.MENU_QUOTE_LINE_HEIGHT
                > GdxFrontendScreen.MENU_SOUND_BOTTOM_MARGIN
                + GdxFrontendScreen.MENU_SOUND_SIZE);
    }
}
