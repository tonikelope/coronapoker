package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxUiDialogStyleTest {

    @Test
    void sharedDialogMaterialRemainsTranslucent() {
        int panelAlpha = GdxUiDialogStyle.PANEL_RGBA & 0xff;
        int insetAlpha = GdxUiDialogStyle.INSET_RGBA & 0xff;

        assertTrue(panelAlpha < 0xff,
                "dialog panel must preserve the scene behind it");
        assertEquals(GdxFrontendScreen.SCREEN_PANEL_RGBA & 0xff,
                panelAlpha,
                "dialogs must match the frontend glass opacity");
        assertTrue(insetAlpha < panelAlpha,
                "nested reading surfaces must not make the shell opaque");
    }

    @Test
    void foregroundAutoOverlayOccludesCardsWithoutChangingOtherDialogs() {
        int foregroundPanelAlpha =
                GdxUiDialogStyle.FOREGROUND_PANEL_RGBA & 0xff;
        int foregroundInsetAlpha =
                GdxUiDialogStyle.FOREGROUND_INSET_RGBA & 0xff;

        assertTrue(foregroundPanelAlpha > (GdxUiDialogStyle.PANEL_RGBA & 0xff));
        assertTrue(foregroundInsetAlpha > (GdxUiDialogStyle.INSET_RGBA & 0xff));
        assertTrue(foregroundPanelAlpha >= 0xfa,
                "AUTO MODE must hide the cards and HUD underneath it");
    }
}
