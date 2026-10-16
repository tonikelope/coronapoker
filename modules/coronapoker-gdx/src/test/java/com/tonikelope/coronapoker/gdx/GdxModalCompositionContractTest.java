/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxModalCompositionContractTest {

    @Test
    void frontendUsesOneImmediateBackdropAndKeepsTheSignatureSharp()
            throws IOException {
        String source = source("GdxFrontendScreen.java");
        int blur = source.indexOf("modalBackdropBlur.endCaptureAndDraw(");
        int shade = source.indexOf("drawUnifiedModalBackdrop();", blur);
        int modal = source.indexOf("if (hasVisibleFrontendModal())", shade);
        int signature = source.indexOf(
                "drawFrontendVersionLabelTopLayer();", modal);

        assertTrue(blur >= 0 && shade > blur);
        assertTrue(modal > shade,
                "the common shade must be ready before modal chrome");
        assertTrue(signature > modal,
                "the product signature must be a sharp top layer");
        assertEquals(1, occurrences(source,
                "GdxUiDialogStyle.drawBackdrop("));
    }

    @Test
    void tableUsesTheSameCompositionOrderAndCoversNestedConfirmation()
            throws IOException {
        String source = source("CoronaPokerGdxTable.java");
        int blur = source.indexOf("modalBackdropBlur.endCaptureAndDraw(");
        int restore = source.indexOf("restoreDefaultTableRenderState();",
                blur);
        int shade = source.indexOf("drawUnifiedModalBackdrop();", restore);
        int modal = source.indexOf("drawUiLayer();", shade);
        int signature = source.indexOf("drawProductVersionBrand(1f);", modal);

        assertTrue(blur >= 0 && restore > blur,
                "modal composition must restore the default render target");
        assertTrue(shade > restore,
                "the common shade must use the restored table projection");
        assertTrue(modal > shade);
        assertTrue(signature > modal,
                "the table signature must remain visible above every modal");
        assertTrue(source.contains("|| tableImageClearConfirmation"),
                "a nested chat confirmation is a real blurred modal");
        assertEquals(1, occurrences(source,
                "GdxUiDialogStyle.drawBackdrop("));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String source(String filename) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com",
                "tonikelope", "coronapoker", "gdx", filename));
    }
}
