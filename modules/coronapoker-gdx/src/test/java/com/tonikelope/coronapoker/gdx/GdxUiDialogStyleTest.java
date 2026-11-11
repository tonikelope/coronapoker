package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxUiDialogStyleTest {

    @Test
    void sharedDialogMaterialMatchesTheSettingsSurface() {
        int panelAlpha = GdxUiDialogStyle.PANEL_RGBA & 0xff;
        int insetAlpha = GdxUiDialogStyle.INSET_RGBA & 0xff;

        assertTrue(panelAlpha < 0xff,
                "dialog panel must use the same glass opacity as settings");
        assertEquals(GdxFrontendScreen.SCREEN_PANEL_RGBA & 0xff,
                panelAlpha,
                "dialogs must match the frontend glass opacity");
        assertTrue(insetAlpha < panelAlpha,
                "nested reading surfaces must not make the shell opaque");
        assertEquals(0.68f, GdxUiDialogStyle.BACKDROP_ALPHA);
    }

    @Test
    void confirmationsNoticesAndTablePromptsUseTheSharedSettingsMaterial()
            throws Exception {
        String base = "src/main/java/com/tonikelope/coronapoker/gdx/";
        String frontend = Files.readString(Path.of(base
                + "GdxFrontendScreen.java"), StandardCharsets.UTF_8);
        String table = Files.readString(Path.of(base
                + "CoronaPokerGdxTable.java"), StandardCharsets.UTF_8);

        assertMethodSectionUses(frontend,
                "private GdxUiDialogStyle.ConfirmationLayout "
                        + "drawConfirmationChrome(",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(frontend,
                "private GdxUiDialogStyle.NoticeLayout drawNoticeChrome(",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table, "private void drawActiveDialog()",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table,
                "private void drawCanonicalConfirmationDialog(",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table,
                "private void drawCanonicalNoticeDialog(",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table,
                "private void drawSettingsDiscardDialog(",
                "GdxUiDialogStyle.drawPanel(");
        assertFalse(frontend.contains("drawOpaquePanel("));
        assertFalse(table.contains("drawOpaquePanel("));
    }

    @Test
    void createJoinAndStatisticsUseTheSameFrontendPanelAsSettings()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/"
                + "tonikelope/coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);

        assertMethodSectionUses(frontend,
                "private void drawNewGameDialogFrame()",
                "CYAN_DARK, PANEL);");
        assertMethodSectionUses(frontend,
                "private void drawStatsScreen()",
                "panel(20f, 20f, 1880f, 1040f, \"\");");
        assertMethodSectionUses(frontend,
                "private void panel(float x, float y, float w, float h, "
                        + "String title)",
                "outerBox(x, y, w, h, LINE, PANEL);");
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
    void noticeMessagesPreserveOnlyAuthoredLineBreaks() {
        assertEquals("Ya estás en el máximo de fichas de la mesa: "
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
        assertEquals(28f, layout.message().y
                - layout.negativeButton().y
                - layout.negativeButton().height, 0.001f,
                "confirmation text needs breathing room above its buttons");
        assertEquals(22f, layout.title().y
                - layout.message().y - layout.message().height, 0.001f,
                "moving the message must preserve the title separation");
    }

    @Test
    void frontendNoticesShareCenteredTitleMessageAndActionGeometry() {
        GdxUiDialogStyle.NoticeLayout layout =
                GdxUiDialogStyle.noticeLayout(1920f, 1080f);

        assertEquals(1920f / 2f,
                layout.panel().x + layout.panel().width / 2f);
        assertEquals(1920f / 2f,
                layout.title().x + layout.title().width / 2f);
        assertEquals(1920f / 2f,
                layout.message().x + layout.message().width / 2f);
        assertEquals(1920f / 2f,
                layout.button().x + layout.button().width / 2f);
        assertTrue(layout.title().y
                > layout.message().y + layout.message().height);
        assertTrue(layout.message().y
                > layout.button().y + layout.button().height);
    }

    @Test
    void simpleTablePromptsUseTheSameCanonicalDialogFamilies() {
        GdxTableDialog confirmation = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM, "¿CONTINUAR?",
                GameDialogSink.Icon.NONE, 760, 0);
        GdxTableDialog error = new GdxTableDialog(
                GdxTableDialog.Kind.ERROR, "NO SE PUDO COMPLETAR",
                GameDialogSink.Icon.NONE, 760, 0);
        GdxTableDialog info = new GdxTableDialog(
                GdxTableDialog.Kind.INFO, "OPERACIÓN COMPLETADA",
                GameDialogSink.Icon.NONE, 760, 0);
        GdxTableDialog timed = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM, "DECIDE AHORA",
                GameDialogSink.Icon.NONE, 760, 5);
        GdxTableDialog form = GdxTableDialog.autoCall(true, 1d);
        GdxTableDialog pendingRebuy = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM, "CONFIRMACIÓN",
                "Ya tienes una recompra programada.",
                GameDialogSink.Icon.NONE, 700, 0, false,
                "VOLVER", "ANULAR RECOMPRA");

        assertTrue(confirmation.usesCanonicalConfirmationLayout());
        assertTrue(pendingRebuy.usesCanonicalConfirmationLayout(),
                "the repeated-rebuy prompt must use the shared confirmation");
        assertTrue(error.usesCanonicalNoticeLayout());
        assertTrue(info.usesCanonicalNoticeLayout());
        assertFalse(timed.usesCanonicalConfirmationLayout(),
                "timed decisions retain their countdown layout");
        assertFalse(form.usesCanonicalConfirmationLayout(),
                "forms retain their purpose-built controls");
        assertFalse(error.showsNegative(),
                "notices expose one unambiguous close action");
        assertTrue(error.showsPositive());
    }

    @Test
    void remainingSimpleConfirmationUsesTheSharedChrome() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/"
                + "tonikelope/coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf(
                "private void drawBlindStructureDeleteConfirmation()");
        int end = frontend.indexOf("private void drawPurchasePage()", start);

        assertTrue(start >= 0 && end > start);
        assertTrue(frontend.substring(start, end).contains(
                "drawConfirmationChrome("));
        assertFalse(frontend.substring(start, end).contains(
                "GdxUiDialogStyle.drawPanel("));
    }

    @Test
    void standaloneFrontendNoticesUseTheSharedNoticeChrome() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/"
                + "tonikelope/coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);

        assertMethodUses(frontend, "drawFrontendNotice()",
                "drawNoticeChrome(");
        assertMethodUses(frontend, "drawIdentityChangeNotice()",
                "drawNoticeChrome(");
        assertMethodUses(frontend, "drawSettingsRestartNotice()",
                "drawNoticeChrome(");
    }

    private static void assertMethodUses(String source, String signature,
            String expected) {
        int start = source.indexOf("private void " + signature);
        int end = source.indexOf("private ", start + 1);
        assertTrue(start >= 0 && end > start,
                "method boundary not found for " + signature);
        assertTrue(source.substring(start, end).contains(expected),
                signature + " must use " + expected);
    }

    private static void assertMethodSectionUses(String source,
            String signature, String expected) {
        int start = source.indexOf(signature);
        int end = source.indexOf("\n    private ", start + signature.length());
        assertTrue(start >= 0 && end > start,
                "method boundary not found for " + signature);
        assertTrue(source.substring(start, end).contains(expected),
                signature + " must use " + expected);
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
