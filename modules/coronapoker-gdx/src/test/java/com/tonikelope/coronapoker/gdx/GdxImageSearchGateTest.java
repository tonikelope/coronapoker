package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxImageSearchGateTest {

    @Test
    void serializesProviderNavigationAndIgnoresStaleCompletion() {
        var gate = new GdxImageSearchGate();
        var first = new Object();
        var second = new Object();
        assertEquals(0, gate.begin(first, 1_000));
        assertEquals(GdxImageSearchGate.BUSY, gate.begin(second, 1_001));
        gate.finish(first, 1_002, 0);
        assertEquals(0, gate.begin(second, 1_003));
        gate.finish(first, 1_004, 90_000);
        assertEquals(GdxImageSearchGate.BUSY, gate.begin(first, 1_005));
        gate.finish(second, 1_006, 0);
        assertEquals(0, gate.begin(first, 1_007));
    }

    @Test
    void cooldownAppliesAcrossWindowsUntilTheFullWaitHasElapsed() {
        var gate = new GdxImageSearchGate();
        var first = new Object();
        var second = new Object();
        assertEquals(0, gate.begin(first, 1_000));
        assertEquals(6, gate.finish(first, 1_001, 7_001));
        assertEquals(1, gate.begin(second, 7_000));
        assertEquals(0, gate.begin(second, 7_001));
    }

    @Test
    void recognizesAnInlineChallengeByItsForm() {
        assertTrue(GoogleImageResults.isRateLimitedDocument(
                "<html><FORM method='post' id='captcha-form'></FORM></html>"));
        assertTrue(GoogleImageResults.isRateLimitedDocument(
                "<form action=\"/sorry/index?continue=search\"></form>"));
        assertFalse(GoogleImageResults.isRateLimitedDocument(
                "<article>unusual traffic and /sorry/index</article>"));
    }

    @Test
    void nativeGalleryHeaderReservesTheCloseButtonAndWebViewport() {
        var panel = new Rectangle(20f, 30f, 800f, 400f);
        var title = GdxImageGalleryWindow.titleDragBounds(panel);
        assertEquals(GdxFloatingGameLogLayout.headerBounds(panel), title);
        assertFalse(title.overlaps(GdxFloatingGameLogLayout.closeBounds(panel)));
        assertFalse(title.overlaps(GdxImageGalleryWindow.webBounds(panel)));
    }
}
