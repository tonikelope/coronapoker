/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxCustomBotManagerContractTest {

    @Test
    void customBotManagerOwnsARealModalAndSubmitsTheOrderedCatalogue()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(source.contains(
                "surface == Surface.NEW_GAME\n"
                + "                && (customBotManagerOpen"));
        assertTrue(source.contains("drawCustomBotManagerDialog();"));
        assertTrue(source.contains("drawCustomBotManagerImages();"));
        assertTrue(source.contains("batch.setShader(avatarShader);"));
        assertTrue(source.contains(
                "table.customBots() ? List.copyOf(customBotProfiles)"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeTo(initialProperties"));
        assertTrue(source.contains(
                "customBotPersonalityDraft = CustomBotProfile.Personality.RANDOM"));
        assertTrue(source.contains(
                "float y = customBotManagerOpen ? 502f : 145f;"));
        assertTrue(source.contains(
                "surface == Surface.NEW_GAME && customBotManagerOpen\n"
                + "                && !customBotDeleteConfirmation && amountY != 0f"));
        assertTrue(source.contains("ScrollDrag.CUSTOM_BOTS"));
        assertTrue(source.contains("GdxSettingsStyle.drawRowScrollbar"));
        assertTrue(source.contains(
                "else if (!customBotManagerOpen\n"
                + "                    || customBotDeleteConfirmation)"));
        assertTrue(source.contains(
                "\"tooltip.cfg.custom_bots.order_up\""));
        assertTrue(source.contains(
                "\"tooltip.cfg.custom_bots.save\""));
        assertTrue(source.contains(
                "compactRepeatButton(listX + 245f"));
        assertTrue(source.contains("ensureCustomBotVisible(target);"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeEnabled(initialProperties"));
        assertTrue(source.contains(
                "draft.setCustomBots(CustomBotProfileCatalog.enabled"));
        assertFalse(source.contains("customBotPage"));
        assertFalse(source.contains(
                "gameText.translate(\"gdx.custom_bots.previous\")"));
    }
}
