package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
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
        assertEquals(0.68f, GdxUiDialogStyle.BACKDROP_ALPHA);
    }

    @Test
    void noticeMessagesPreferTheSemanticBreakAfterAColon() {
        assertEquals("Ya estás en el máximo de fichas de la mesa:\n"
                        + "no puedes recomprar.",
                GdxUiDialogStyle.formatNoticeMessage(
                        "Ya estás en el máximo de fichas de la mesa: "
                                + "no puedes recomprar."));
        assertEquals("Primera línea\nSegunda línea",
                GdxUiDialogStyle.formatNoticeMessage(
                        "Primera línea\nSegunda línea"));
        assertEquals("Mensaje sin detalle",
                GdxUiDialogStyle.formatNoticeMessage(
                        "Mensaje sin detalle"));
    }

    @Test
    void frontendConfirmationsShareOneCenteredGeometry() {
        GdxUiDialogStyle.ConfirmationLayout layout =
                GdxUiDialogStyle.confirmationLayout(1920f, 1080f);

        assertEquals(new Rectangle(560f, 375f, 800f, 330f),
                layout.panel());
        assertEquals(layout.negativeButton().y,
                layout.positiveButton().y);
        assertEquals(layout.negativeButton().width,
                layout.positiveButton().width);
        assertTrue(layout.message().y
                > layout.negativeButton().y
                        + layout.negativeButton().height);
        assertTrue(layout.title().y
                > layout.message().y + layout.message().height);
    }

    @Test
    void galleryHeaderKeepsReadableActionsAndAnEmphaticCloseControl() {
        GdxUiDialogStyle.GalleryHeaderLayout layout
                = GdxUiDialogStyle.galleryHeaderLayout(
                        390f, 180f, 1140f, 720f, true);

        assertEquals(220f, layout.searchButton().width);
        assertEquals(220f, layout.autoReceiveButton().width);
        assertEquals(142f, layout.clearButton().width);
        assertEquals(52f, layout.closeButton().width);
        assertEquals(52f, layout.closeButton().height);
        assertTrue(layout.title().x + layout.title().width
                < layout.searchButton().x);
        assertTrue(layout.searchButton().x + layout.searchButton().width
                < layout.autoReceiveButton().x);
        assertTrue(layout.autoReceiveButton().x
                + layout.autoReceiveButton().width
                < layout.clearButton().x);
        assertTrue(layout.clearButton().x + layout.clearButton().width
                < layout.closeButton().x);
        assertEquals("\u00d7", GdxUiDialogStyle.CLOSE_GLYPH);
    }
}
