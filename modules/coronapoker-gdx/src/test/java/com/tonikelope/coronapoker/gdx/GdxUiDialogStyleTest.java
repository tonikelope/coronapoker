package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void legacyTopEdgeRailsStayRemovedFromSharedSurfaces() throws Exception {
        String base = "src/main/java/com/tonikelope/coronapoker/gdx/";
        String dialogs = Files.readString(Path.of(base
                + "GdxUiDialogStyle.java"), StandardCharsets.UTF_8);
        String settings = Files.readString(Path.of(base
                + "GdxSettingsChrome.java"), StandardCharsets.UTF_8);
        String frontend = Files.readString(Path.of(base
                + "GdxFrontendScreen.java"), StandardCharsets.UTF_8);
        String table = Files.readString(Path.of(base
                + "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);

        assertFalse(dialogs.contains("width - 56f, 3f"));
        assertFalse(settings.contains("panel.width - 44f, 3f"));
        assertFalse(frontend.contains("w - 36f, 2f"));
        assertFalse(frontend.contains("WIDTH - 80f, 3f"));
        assertFalse(table.contains("width - 20f, 2f"));
        assertFalse(table.contains("boxW - 20f, 2f"));
        assertFalse(table.contains("BASE_WIDTH - 80f, 3f"));
        assertFalse(table.contains("width - 10f, 6f, 3f"));
        assertFalse(table.contains("stackBounds.width - 24f, 2f"));
        assertFalse(table.contains("investedBounds.width - 24f, 2f"));
        assertFalse(table.contains("tipW - 16f, 2f"),
                "compact labels must not restore a detached top rail");
        assertFalse(table.contains("PLAYER_POD_WIDTH - 30f, 2f"),
                "seat action labels must use one uninterrupted surface");
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
        assertEquals(168f, GdxUiButtonStyle.labelContentWidth(
                layout.searchButton().width));
        assertEquals(107.92f, GdxUiButtonStyle.labelContentWidth(
                layout.clearButton().width), 0.001f);
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

    @Test
    void everyRendererUsesTheGeometricallyCenteredCloseIcon()
            throws Exception {
        String base = "src/main/java/com/tonikelope/coronapoker/gdx/";
        String frontend = Files.readString(Path.of(base
                + "GdxFrontendScreen.java"), StandardCharsets.UTF_8);
        String table = Files.readString(Path.of(base
                + "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);
        String icons = Files.readString(Path.of(base
                + "GdxUiIconRenderer.java"), StandardCharsets.UTF_8);

        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.CLOSE"));
        assertTrue(table.contains("drawCloseButtonIcon"));
        assertFalse(table.contains("GdxUiDialogStyle.CLOSE_GLYPH,"),
                "table dialogs must not fall back to font metrics for X");
        assertTrue(icons.contains("case CLOSE -> drawClose"));
    }
}
