/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxNestedModalOwnershipContractTest {

    @Test
    void frontendNestedPopupOwnsASecondBlurAndClearsParentHitMaps()
            throws Exception {
        String source = Files.readString(source("GdxFrontendScreen.java"));

        assertTrue(source.contains(
                "private GdxModalBackdropBlur nestedModalBackdropBlur;"));
        assertTrue(source.contains("private void renderNestedFrontendModal()"));
        assertTrue(source.contains("modalBackdropBlur.drawCached("));
        assertTrue(source.contains("nestedModalBackdropBlur.endCaptureAndDraw("));
        assertTrue(source.contains("private void clearModalCompositionState()"));
        assertTrue(source.contains("hits.clear();")
                && source.contains("secondaryHits.clear();")
                && source.contains("textFieldHits.clear();")
                && source.contains("tooltipHits.clear();"),
                "the child popup must be the only remaining pointer target");
    }

    @Test
    void frontendPointerPathsConsumeEveryVisibleModal() throws Exception {
        String source = Files.readString(source("GdxFrontendScreen.java"));
        int mouseMoved = source.indexOf(
                "@Override public boolean mouseMoved(int x, int y)");
        int scrolled = source.indexOf("public boolean scrolled(", mouseMoved);
        String mouseMovedMethod = source.substring(mouseMoved, scrolled);

        assertTrue(source.contains("return hasBlockingFrontendModal();"));
        assertTrue(mouseMovedMethod.contains(
                "return hasBlockingFrontendModal();"),
                "pointer movement must not propagate through a modal");
        assertTrue(source.contains("if (hasNestedFrontendModal()) return true;"),
                "nested modal drags must never reach their parent");
    }

    @Test
    void tablePopupBlursEveryOpenForegroundToolAndOwnsPointerInput()
            throws Exception {
        String source = Files.readString(source("CoronaPokerGdxTable.java"));

        int nested = source.indexOf("private boolean hasNestedTableModal()");
        int nextMethod = source.indexOf("private boolean", nested + 1);
        String method = source.substring(nested, nextMethod);
        assertTrue(method.contains("uiLayer != UI_NONE"),
                "a popup must blur chat and the floating game log too");
        assertTrue(source.contains("private void renderNestedTableModal()"));
        assertTrue(source.contains("nestedModalBackdropBlur.endCaptureAndDraw("));
        assertTrue(source.contains("private boolean blockingTablePointerSurface()"));
        int mouseMoved = source.indexOf(
                "public boolean mouseMoved(int screenX, int screenY)");
        int inputEnd = source.indexOf("};", mouseMoved);
        String mouseMovedMethod = source.substring(mouseMoved, inputEnd);
        assertTrue(mouseMovedMethod.contains(
                "return blockingTablePointerSurface();"),
                "the final table pointer path must consume modal input");
    }

    private static Path source(String fileName) {
        return Path.of("src", "main", "java", "com", "tonikelope",
                "coronapoker", "gdx", fileName);
    }
}
