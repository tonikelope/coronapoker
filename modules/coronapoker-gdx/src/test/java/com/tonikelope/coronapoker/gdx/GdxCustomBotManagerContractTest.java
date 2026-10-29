/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertTrue(source.contains(
                "customBotManagerMusic = music(\n"
                + "                \"sounds/misc/bots_manager.mp3\", 0.65f);"));
        assertTrue(source.contains(
                "customBotDialogMusic = surface == Surface.NEW_GAME"));
        assertTrue(source.contains(
                "syncTrack(customBotManagerMusic, playCustomBotManager);"));
        assertTrue(source.contains("customBotManagerMusic.dispose();"));
        assertTrue(source.contains("drawCustomBotManagerListLayer();\n"
                + "                drawCustomBotDeleteConfirmationTopLayer();"));
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
        assertTrue(source.contains("pixelScrollbarThumbHeight"));
        assertTrue(source.contains("customBotScrollTarget"));
        assertTrue(source.contains("customBotRowsActive = true;"));
        assertTrue(source.contains(
                "else if (!customBotManagerOpen\n"
                + "                    || customBotDeleteConfirmation)"));
        assertTrue(source.contains(
                "\"tooltip.cfg.custom_bots.order_up\""));
        assertTrue(source.contains(
                "\"tooltip.cfg.custom_bots.save\""));
        assertTrue(source.contains(
                "compactRepeatButton(listX + 479f"));
        assertTrue(source.contains(
                "compactRepeatButton(listX + 654f"));
        assertTrue(source.contains(
                "WIDTH / 2f, 912f, MUTED, true, 1320f);"));
        assertTrue(source.contains(
                "textFit(uiFont, gameText.translate(\"gdx.custom_bots.catalog\")"));
        assertTrue(source.contains(
                "textFit(uiFont, customBotSelected < 0"));
        assertTrue(source.contains(
                "rowY + 56f, Color.WHITE, false, 340f);"));
        assertTrue(source.contains(
                "listX + 600f, rowY + 72f, GOLD, true, 220f);"));
        assertTrue(source.contains(
                "listX + 600f, rowY + 44f, CYAN, true, 245f);"));
        assertFalse(source.contains(
                "textFit(smallFont, profile.nickname()"));
        assertFalse(source.contains(
                "gdx.custom_bots.avatar.default\")"));
        assertTrue(source.contains(
                "shapes.circle(editorAvatarCenterX, editorAvatarCenterY, 70f, 64);"));
        assertTrue(source.contains(
                "shapes.circle(editorAvatarCenterX, editorAvatarCenterY, 61f, 64);"));
        assertTrue(source.contains(
                "this::selectCustomBotAvatar);"));
        assertTrue(source.contains(
                "this::resetCustomBotAvatar);"));
        assertTrue(source.contains(
                "\"tooltip.change_avatar\""));
        assertFalse(source.contains(
                "gdx.custom_bots.avatar.choose"));
        assertFalse(source.contains(
                "gdx.custom_bots.avatar.default_button"));
        assertFalse(source.contains(
                "gameText.translate(\"gdx.custom_bots.marker_help\")"));
        assertTrue(source.contains(
                "field(editX + 42f, 590f, editW - 84f"));
        assertTrue(source.contains(
                "bidirectionalChoice(editX + 42f, 420f, editW - 84f"));
        assertTrue(source.contains(
                "bidirectionalChoice(editX + 42f, 285f, editW - 84f"));
        assertTrue(source.contains(
                "compactButton(editX + 42f, 158f, 140f, 54f"));
        assertTrue(source.contains("ensureCustomBotVisible(target);"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeEnabled(initialProperties"));
        assertTrue(source.contains(
                "draft.setCustomBots(CustomBotProfileCatalog.enabled"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeRandomSelection"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.profilesForSession"));
        assertFalse(source.contains("customBotPage"));
        assertFalse(source.contains("customBotScrollFirstRow"));
        assertFalse(source.contains(
                "gameText.translate(\"gdx.custom_bots.previous\")"));
    }

    @Test
    void customBotListUsesContinuousPixelScrolling() {
        assertEquals(0f, GdxFrontendScreen.customBotMaximumPixelScroll(5));
        assertEquals(105f,
                GdxFrontendScreen.customBotMaximumPixelScroll(6));
        assertEquals(48f, GdxFrontendScreen.customBotScrollAfterWheel(
                0f, 315f, 1f));
        assertEquals(315f, GdxFrontendScreen.customBotScrollAfterWheel(
                300f, 315f, 1f));
        assertEquals(105f, GdxFrontendScreen.customBotScrollForSelection(
                5, 0f, 105f));
    }
}
