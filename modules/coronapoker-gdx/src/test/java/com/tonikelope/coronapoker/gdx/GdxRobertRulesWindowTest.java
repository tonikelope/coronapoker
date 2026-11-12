/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxRobertRulesWindowTest {

    @Test
    void bothLocalizedDocumentsAndOfflineViewerShipInTheGame() throws Exception {
        var files = GdxRobertRulesAssets.load();
        for (String language : new String[]{"es", "en"}) {
            String document = GdxRobertRulesAssets.document(language);
            String url = GdxRobertRulesAssets.initialUrl(language);
            assertEquals(GdxRobertRulesAssets.VIEWER, GdxRobertRulesAssets.localPath(url));
            assertTrue(url.contains("file=" + document));
            assertEquals("%PDF-", new String(files.get(document), 0, 5, StandardCharsets.US_ASCII));
        }
        assertEquals("/robert_rules_es.pdf", GdxRobertRulesAssets.document("es"));
        assertEquals("/robert_rules.pdf", GdxRobertRulesAssets.document("en"));
        assertEquals("", GdxRobertRulesAssets.localPath("https://rules.coronapoker.invalid.example/web/viewer.html"));
        String html = new String(files.get(GdxRobertRulesAssets.VIEWER), StandardCharsets.UTF_8);
        assertTrue(html.contains("connect-src 'self'"));
        assertTrue(html.contains("/game-viewer.css"));
        assertTrue(files.containsKey("/build/pdf.worker.mjs"));
    }

    @Test
    void opacityUsesTheSameRangeAsTheVideoSlider() {
        Rectangle track = new Rectangle(30f, 0f, 200f, 5f);
        assertEquals(10, GdxRobertRulesWindow.opacityAt(track, 0f));
        assertEquals(55, GdxRobertRulesWindow.opacityAt(track, 130f));
        assertEquals(100, GdxRobertRulesWindow.opacityAt(track, 250f));
        Properties settings = new Properties();
        assertEquals(100, GdxRobertRulesWindow.readOpacity(settings));
        settings.setProperty(GdxRobertRulesWindow.OPACITY, "43");
        assertEquals(43, GdxRobertRulesWindow.readOpacity(settings));
        settings.setProperty(GdxRobertRulesWindow.OPACITY, "invalid");
        assertEquals(100, GdxRobertRulesWindow.readOpacity(settings));
    }

    @Test
    void resizingReservesSpaceForTitleAndOpacityControls() {
        Rectangle start = GdxFloatingGameLogLayout.initial(1920f, 1080f);
        Rectangle document = GdxRobertRulesWindow.documentBounds(start);
        Rectangle resized = GdxFloatingGameLogLayout.resizedFromLowerRight(
                start, 140f, -70f, 1920f, 1080f);
        Rectangle resizedDocument = GdxRobertRulesWindow.documentBounds(resized);
        assertTrue(resized.contains(resizedDocument));
        assertEquals(document.width + 140f, resizedDocument.width);
        assertEquals(document.height + 70f, resizedDocument.height);
        assertEquals(document.y + document.height, resizedDocument.y + resizedDocument.height);
    }

    @Test
    void bothOutsideEdgesResizeWithoutStealingThePdfScrollbar() {
        Rectangle panel = new Rectangle(80f, 100f, 800f, 600f);
        Rectangle document = GdxRobertRulesWindow.documentBounds(panel);
        assertEquals(panel.x + 2f, document.x);
        assertEquals(panel.width - 4f, document.width);
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.LEFT,
                GdxRobertRulesWindow.sideResizeHandle(panel, panel.x - 4f, 400f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.RIGHT,
                GdxRobertRulesWindow.sideResizeHandle(panel, panel.x + panel.width + 4f, 400f));
        assertEquals(GdxFloatingGameLogLayout.ResizeHandle.NONE,
                GdxRobertRulesWindow.sideResizeHandle(panel, document.x + document.width - 6f, 400f));
    }

    @Test
    void maximizedWindowFillsTheViewportToTheWindowMargin() {
        assertEquals(new Rectangle(16f, 16f, 1888f, 1048f),
                GdxRobertRulesWindow.maximizedBounds(1920f, 1080f));
    }

    @Test
    void sharedHeaderCentersTheTitleAndCloseButtonAboveThePdf() {
        Rectangle panel = GdxFloatingGameLogLayout.initial(1920f, 1080f);
        Rectangle title = GdxFloatingGameLogLayout.headerBounds(panel);
        Rectangle close = GdxFloatingGameLogLayout.closeBounds(panel);
        Rectangle document = GdxRobertRulesWindow.documentBounds(panel);
        assertEquals(panel.y + panel.height - GdxFloatingGameLogLayout.HEADER_HEIGHT / 2f,
                title.y + title.height / 2f);
        assertEquals(title.y + title.height / 2f, close.y + close.height / 2f);
        assertTrue(title.y >= document.y + document.height);
        assertEquals(title, GdxImageGalleryWindow.titleDragBounds(panel));
    }

    @Test
    void rasterKeepsThePhysicalResolutionWhenTheWindowGrows() {
        assertEquals(1884, GdxRobertRulesWindow.browserPixels(1884, 1d, 1d));
        int logical = GdxRobertRulesWindow.browserPixels(1884, 2d, 2.3d);
        assertEquals(3768d, logical * 2.3d, 1.15d);
    }

    @Test
    void aOnePixelDifferenceAtOpenDoesNotResampleThePdf() {
        double deviceScale = 23d / 15d;
        assertTrue(GdxRobertRulesWindow.nativeSampling(1542, 816, 1541, 816, deviceScale));
        assertTrue(GdxRobertRulesWindow.nativeSampling(1542, 816, 1543, 815, deviceScale));
        assertFalse(GdxRobertRulesWindow.nativeSampling(1699, 923, 1541, 816, deviceScale));
    }

    @Test
    void aSecondaryPressStaysCapturedAfterThePrimaryButtonIsReleased() {
        var pointer = new GdxFloatingWindowPointer();
        assertTrue(pointer.capture(0, Input.Buttons.LEFT));
        assertFalse(pointer.capture(0, Input.Buttons.RIGHT));
        assertTrue(pointer.releasePrimary(0, Input.Buttons.LEFT));
        assertTrue(pointer.active());
        assertTrue(pointer.releaseSecondary(0, Input.Buttons.RIGHT));
        assertFalse(pointer.active());
    }
}
