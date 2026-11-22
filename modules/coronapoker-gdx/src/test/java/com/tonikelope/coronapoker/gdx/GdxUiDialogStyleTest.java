package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxUiDialogStyleTest {

    @Test
    void dialogProseJustifiesWrappedLinesAndPreservesParagraphEndings() {
        var layout = new GlyphLayout();
        GdxUiDialogStyle.layoutMessage(layout, messageFont(),
                "a b c\n\np:q", Color.WHITE, 36f, 80f, Align.center);

        assertEquals(3, layout.runs.size);
        assertEquals(36f, layout.runs.get(0).width);
        assertEquals(16f, layout.runs.get(0).xAdvances.get(2));
        assertEquals(10f, layout.runs.get(1).width);
        assertEquals(30f, layout.runs.get(2).width);
        assertEquals(-36f, layout.runs.get(2).y);
        layout.runs.forEach(line -> assertEquals(0f, line.x));
    }

    @Test
    void shortDialogTextStaysCenteredAndWideGapsStayNatural() {
        var layout = new GlyphLayout();
        BitmapFont font = messageFont();
        GdxUiDialogStyle.layoutMessage(layout, font,
                "a b", Color.WHITE, 50f, 80f, Align.center);
        assertEquals(10f, layout.runs.first().x);

        GdxUiDialogStyle.layoutMessage(layout, font,
                "a b cc", Color.WHITE, 48f, 80f, Align.center);
        assertEquals(30f, layout.runs.first().width);
        assertEquals(0f, layout.runs.first().x);
    }

    @Test
    void reflowMovesAWordToAThinLineBeforeJustifying() {
        var layout = new GlyphLayout();
        GdxUiDialogStyle.layoutTooltip(layout, messageFont(),
                "a a a a b ccccccc ppp\n\na b", Color.WHITE, 100f, 100f);

        assertEquals(4, layout.runs.size);
        assertEquals(100f, layout.runs.get(0).width);
        assertEquals(100f, layout.runs.get(1).width);
        assertEquals('b', layout.runs.get(1).glyphs.first().id);
        assertEquals(30f, layout.runs.get(2).width);
        assertEquals(30f, layout.runs.get(3).width);
        assertEquals(-48f, layout.runs.get(3).y);
    }

    @Test
    void aFloatingTooltipCanWidenToAvoidWidowsAndKeepItsSeparateFooter() {
        var layout = new GlyphLayout();
        GdxUiDialogStyle.layoutTooltip(layout, messageFont(),
                "aaaa bbbbb cccc ppppp\np:q",
                Color.WHITE, 90f, 104f, 100f);

        assertEquals(3, layout.runs.size);
        assertTrue(layout.width > 90f && layout.width <= 104f);
        assertEquals(1, GdxParagraphReflow.wordGaps(layout.runs.get(0)));
        assertEquals(1, GdxParagraphReflow.wordGaps(layout.runs.get(1)));
        assertEquals(30f, layout.runs.get(2).width);
        assertEquals(0f, layout.runs.get(2).x);
    }

    @Test
    void hardBreaksAlignLeftAndTextFitsTheAvailableHeight() {
        var layout = new GlyphLayout();
        BitmapFont font = messageFont();
        GdxUiDialogStyle.layoutMessage(layout, font, "ab\np:q",
                Color.WHITE, 50f, 80f, Align.center);
        assertEquals(2, layout.runs.size);
        assertEquals(20f, layout.runs.first().width);
        layout.runs.forEach(line -> assertEquals(0f, line.x));

        GdxUiDialogStyle.layoutMessage(layout, font, "a b c a b c a b c",
                Color.WHITE, 36f, 22f, Align.center);
        assertEquals(2, layout.runs.size);
        assertEquals(22f, layout.height);
        var lastLine = layout.runs.peek();
        assertEquals('…', lastLine.glyphs.peek().id);
    }

    @Test
    void explicitlyCenteredCreditsKeepEachLineCentered() {
        var layout = new GlyphLayout();
        GdxUiDialogStyle.layoutMessage(layout, messageFont(), "ab\np:q",
                Color.WHITE, 50f, 80f, Align.center, false);
        assertEquals(2, layout.runs.size);
        assertEquals(15f, layout.runs.get(0).x);
        assertEquals(10f, layout.runs.get(1).x);
    }

    private static BitmapFont messageFont() {
        var data = new BitmapFontData();
        data.capHeight = 10f;
        data.spaceXadvance = 10f;
        data.down = -12f;
        data.blankLineScale = 1f;
        var font = new BitmapFont(data, new TextureRegion(), false);
        for (char character : " abc:pq…".toCharArray()) {
            var glyph = new Glyph();
            glyph.id = character;
            glyph.width = 10;
            glyph.xadvance = 10;
            data.setGlyph(character, glyph);
        }
        return font;
    }

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
        assertEquals(1f, GdxUiDialogStyle.NOTICE_FILL.a);
        assertEquals(1f, GdxUiDialogStyle.TOOLTIP_FILL.a);
    }

    @Test
    void confirmationsUseTheSharedSettingsMaterialAndNoticesUseOpaquePanels()
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
                "GdxUiDialogStyle.drawNoticePanel(");
        assertMethodSectionUses(table, "private void drawActiveDialog()",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table,
                "private void drawCanonicalConfirmationDialog(",
                "GdxUiDialogStyle.drawPanel(");
        assertMethodSectionUses(table,
                "private void drawCanonicalNoticeDialog(",
                "GdxUiDialogStyle.drawNoticePanel(");
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
    void canonicalDialogsMakeRoomForOneLineWithoutMovingTheirButtons() {
        var glyph = new GlyphLayout();
        BitmapFont font = messageFont();
        String message = "a ".repeat(41) + "b";
        float textWidth = GdxUiDialogStyle.singleLineWidth(glyph, font, message);
        var notice = GdxUiDialogStyle.noticeLayout(1920f, 1080f, textWidth);
        var confirmation = GdxUiDialogStyle.confirmationLayout(1920f, 1080f, textWidth);
        var defaultNotice = GdxUiDialogStyle.noticeLayout(1920f, 1080f);
        var defaultConfirmation = GdxUiDialogStyle.confirmationLayout(1920f, 1080f);

        assertTrue(textWidth > defaultNotice.message().width);
        assertTrue(textWidth > defaultConfirmation.message().width);
        for (Rectangle messageBounds : List.of(notice.message(), confirmation.message())) {
            GdxUiDialogStyle.layoutMessage(glyph, font, message, Color.WHITE,
                    messageBounds.width, messageBounds.height, Align.center);
            assertEquals(1, glyph.runs.size);
            assertTrue(glyph.runs.first().x >= 0f);
        }
        assertEquals(defaultNotice.button(), notice.button());
        assertEquals(defaultConfirmation.negativeButton(), confirmation.negativeButton());
        assertEquals(defaultConfirmation.positiveButton(), confirmation.positiveButton());
        assertEquals(0f, GdxUiDialogStyle.singleLineWidth(glyph, font, "a\nb"));
    }

    @Test
    void longNoticeMakesRoomForTheSameBodyFont() {
        var glyph = new GlyphLayout();
        BitmapFont font = messageFont();
        String message = ("a b c\n").repeat(14) + "p";
        var layout = GdxUiDialogStyle.noticeLayout(1920f, 1080f, glyph, font, message);
        float requiredHeight = glyph.height;
        assertTrue(requiredHeight > GdxUiDialogStyle.noticeLayout(1920f, 1080f)
                .message().height);
        assertTrue(layout.message().height >= requiredHeight);
        assertTrue(layout.title().y > layout.message().y + layout.message().height);
        assertTrue(layout.button().y + layout.button().height < layout.message().y);

        GdxUiDialogStyle.layoutMessage(glyph, font, message, Color.WHITE,
                layout.message().width, layout.message().height, Align.center);
        assertEquals(15, glyph.runs.size);
        assertEquals(requiredHeight, glyph.height);
    }

    @Test
    void tooltipsUseDialogJustificationAndLeaveParagraphEndingsLeftAligned() {
        var glyph = new GlyphLayout();
        GdxUiDialogStyle.layoutTooltip(glyph, messageFont(), "a b c\r\n\r\np:q",
                Color.WHITE, 36f, 80f);

        assertEquals(3, glyph.runs.size);
        assertEquals(36f, glyph.runs.first().width);
        assertEquals(10f, glyph.runs.get(1).width);
        assertEquals(30f, glyph.runs.get(2).width);
        glyph.runs.forEach(line -> assertEquals(0f, line.x));
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

        assertTrue(frontend.contains("GdxUiDialogStyle.drawWindowClose"));
        assertTrue(table.contains("GdxUiDialogStyle.drawWindowClose"));
        for (String window : List.of("GdxRobertRulesWindow.java", "GdxImageGalleryWindow.java",
                "PokerVideoOverlay.java")) {
            assertTrue(Files.readString(Path.of(base + window), StandardCharsets.UTF_8)
                    .contains("GdxUiDialogStyle.drawWindowClose"), window);
        }
        assertFalse(table.contains("GdxUiDialogStyle.CLOSE_GLYPH,"),
                "table dialogs must not fall back to font metrics for X");
        assertTrue(icons.contains("case CLOSE -> drawClose"));
    }
}
