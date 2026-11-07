/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        int restore = source.indexOf(
                "restoreDefaultFrontendRenderState();", blur);
        int shade = source.indexOf("drawUnifiedModalBackdrop();", restore);
        int modal = source.indexOf("if (hasVisibleFrontendModal())", shade);
        int toast = source.indexOf("drawToastTopLayer();", modal);
        int signature = source.indexOf(
                "drawFrontendVersionLabelTopLayer();", modal);

        assertTrue(blur >= 0 && restore > blur);
        assertTrue(shade > restore,
                "frontend blur must restore viewport and renderer state");
        assertTrue(modal > shade,
                "the common shade must be ready before modal chrome");
        assertTrue(toast > modal,
                "transient feedback must remain visible above every modal");
        assertTrue(signature > toast,
                "the product signature must be a sharp top layer");
        assertEquals(1, occurrences(source, "drawToastTopLayer();"),
                "frontend feedback needs one common top-layer pass");
        assertEquals(1, occurrences(source,
                "GdxUiDialogStyle.drawBackdrop("));
        assertEquals(2, occurrences(source,
                "restoreDefaultFrontendRenderState();"),
                "root and nested blur passes need the same restoration");
    }

    @Test
    void everyExitConfirmationUsesTheSharedExitIcon() throws IOException {
        String frontend = source("GdxFrontendScreen.java");
        String table = source("CoronaPokerGdxTable.java");
        int lobbyStart = frontend.indexOf(
                "private void drawLobbyConfirmation()");
        int lobbyEnd = frontend.indexOf(
                "void showIdentityChangeNotice", lobbyStart);
        String lobby = frontend.substring(lobbyStart, lobbyEnd);

        assertTrue(lobby.contains(
                "lobbyConfirmation == LobbyConfirmation.LEAVE"));
        assertTrue(lobby.contains("exitButton(accept.x"),
                "lobby exit must center its label like the main menu");
        assertTrue(lobby.contains(
                "uppercase(gameText.translate(\"ui.salir\")),"));
        assertTrue(table.contains("drawDialogExitIcon(positive.x"),
                "every canonical exit confirmation must use the exit icon");
        assertFalse(table.contains("drawDialogChoiceLabel("),
                "table exit labels must remain centered on the full button");
        assertTrue(table.contains(
                "GdxUiIconRenderer.Icon.EXIT"));
        assertEquals(3, occurrences(table, ").exitChoice();"),
                "all three table exit flows share the canonical renderer");
    }

    @Test
    void everyFrontendExitButtonCentersItsLabelOnTheFullButton()
            throws IOException {
        String source = source("GdxFrontendScreen.java");
        int helperStart = source.indexOf("private void exitButton(");
        int helperEnd = source.indexOf(
                "/** Language is a menu action", helperStart);
        String helper = source.substring(helperStart, helperEnd);

        assertEquals(3, occurrences(source, "exitButton("),
                "menu and lobby confirmation share the centered exit helper");
        assertTrue(source.contains("exitButton(975f, 205f, 350f, 82f,"),
                "main-menu exit must use the shared composition");
        assertTrue(source.contains("iconButton(35f, 55f, 220f, 70f,"),
                "waiting-room exit needs its own label inset");
        assertTrue(helper.contains(
                "themedButton(x, y, w, h, label, ButtonTone.DANGER"),
                "the label must be laid out against the full button bounds");
        assertTrue(helper.contains(
                "drawButtonIconColumn(x, y, w, h, 5, ButtonTone.DANGER"),
                "the exit icon remains in its independent leading column");
        assertFalse(helper.contains("labelX"),
                "exit labels must never use the icon-reserved content area");
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

    @Test
    void nestedCustomBotConfirmationShieldsCoveredHoverAndInput()
            throws IOException {
        String source = source("GdxFrontendScreen.java");
        int managerBranch = source.indexOf(
                "} else if (customBotManagerOpen) {");
        int pointerShield = source.indexOf(
                "if (customBotDeleteConfirmation) {", managerBranch);
        int managerDraw = source.indexOf("drawCustomBotManagerDialog();",
                pointerShield);
        int pointerRestore = source.indexOf(
                "pointer.set(managerPointerX, managerPointerY);", managerDraw);
        int confirmationDraw = source.indexOf(
                "drawCustomBotDeleteConfirmationTopLayer();", pointerRestore);

        assertTrue(managerBranch >= 0 && pointerShield > managerBranch);
        assertTrue(managerDraw > pointerShield,
                "the covered manager must render with its pointer shielded");
        assertTrue(pointerRestore > managerDraw,
                "the child dialog must recover the live pointer");
        assertTrue(confirmationDraw > pointerRestore,
                "only the foreground confirmation may react to hover");
        assertTrue(source.contains(
                "if (customBotDeleteConfirmation) return false;"),
                "the child dialog must also block manager scrollbar capture");
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
