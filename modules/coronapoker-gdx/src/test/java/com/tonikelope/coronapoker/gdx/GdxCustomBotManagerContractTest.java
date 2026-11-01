/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.CustomBotProfile;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
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
                "} else if (customBotManagerOpen) {"));
        assertTrue(source.contains(
                "if (customBotDeleteConfirmation) {\n"
                + "                    pointer.set(-10_000f, -10_000f);\n"
                + "                }\n"
                + "                drawCustomBotManagerDialog();"));
        assertTrue(source.contains("drawCustomBotManagerDialog();"));
        assertTrue(source.contains("drawCustomBotManagerImages();"));
        assertTrue(source.contains(
                "customBotManagerMusic = music(\n"
                + "                \"sounds/misc/bots_manager.mp3\", 0.65f);"));
        assertTrue(source.contains(
                "customBotDialogMusic = customBotManagerOpen"));
        assertTrue(source.contains(
                "syncTrack(customBotManagerMusic, playCustomBotManager);"));
        assertTrue(source.contains(
                "musicMasterEnabled() && customBotDialogMusic\n"
                + "                && preferenceBoolean(\"musica_gestor_bots\", true)"));
        assertFalse(source.contains(
                "musicMasterEnabled() && customBotDialogMusic\n"
                + "                && preferenceBoolean(\"sonido_ascensor\", true)"));
        assertTrue(source.contains("customBotManagerMusic.dispose();"));
        assertTrue(source.contains("drawCustomBotManagerListLayer();\n"
                + "                drawCustomBotDeleteConfirmationTopLayer();"));
        assertTrue(source.contains(".displayName()));"));
        assertFalse(source.contains(".displayName()), true);"));
        assertTrue(source.contains("batch.setShader(avatarShader);"));
        assertTrue(source.contains(
                "table.customBots() ? List.copyOf(customBotProfiles)"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeTo(initialProperties"));
        assertTrue(source.contains(
                "customBotAutomaticRebuyDraft = true"));
        assertTrue(source.contains(
                "float y = customBotManagerOpen ? 502f : 145f;"));
        assertTrue(source.contains(
                "if (customBotManagerOpen\n"
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
                "customBotAddButton(newButtonX, 820f, 220f, 54f"));
        assertTrue(source.contains(
                "() -> newCustomBot(emptyRow));\n"
                + "                    tooltip(rowX, hitBottom"));
        assertTrue(source.contains(
                "customBotSelectionPulse(elapsed)"));
        assertTrue(source.contains(
                "index == customBotNewSlotSelected"));
        assertTrue(source.contains(
                "customBotSelectionBorder.set(GOLD).lerp(CYAN, pulse);"));
        assertFalse(source.contains(
                "2.4f + 1.6f * pulse"));
        assertTrue(source.contains(
                "WIDTH / 2f, 912f, MUTED, true, 1320f);"));
        assertTrue(source.contains(
                "gameText.translate(\"gdx.custom_bots.subtitle\")"));
        assertFalse(source.contains("gdx.custom_bots.subtitle.random"));
        assertFalse(source.contains("gdx.custom_bots.order_help"));
        assertTrue(source.contains(
                "textFit(uiFont, gameText.translate(\"gdx.custom_bots.catalog\")"));
        assertTrue(source.contains(
                "textFit(uiFont, customBotSelected < 0"));
        assertTrue(source.contains("float rowX = listX + 32f;"));
        assertTrue(source.contains("float rowW = listW - 100f;"));
        assertFalse(source.contains(
                "textFit(smallFont, profile.nickname()"));
        assertFalse(source.contains(
                "gdx.custom_bots.avatar.default\")"));
        assertTrue(source.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes,\n"
                + "                editorAvatarCenterX, editorAvatarCenterY, 70f);"));
        assertTrue(source.contains(
                "GdxAntialiasedRoundedRect.fillCircle(shapes,\n"
                + "                editorAvatarCenterX, editorAvatarCenterY, 61f);"));
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
                "field(editX + 42f, 610f, editW - 84f"));
        assertTrue(source.contains(
                "bidirectionalChoice(editX + 42f, 465f, editW - 84f"));
        assertTrue(source.contains(
                "bidirectionalChoice(editX + 42f, 340f, editW - 84f"));
        assertTrue(source.contains(
                "gameText.translate(\"gdx.custom_bots.automatic_rebuy\")"));
        assertTrue(source.contains(
                "globalBotRebuy && customBotAutomaticRebuyDraft"));
        assertTrue(source.contains("ButtonTone.POSITIVE, this::saveCustomBot"));
        assertTrue(source.contains("ButtonTone.DANGER, this::openCustomBotDeleteConfirmation"));
        assertTrue(source.contains("compactVerticalArrowButton(moveUpX"));
        assertTrue(source.contains("GdxUiIconRenderer.Icon.ADD"));
        assertTrue(source.contains("ensureCustomBotVisible(target);"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeEnabled(initialProperties"));
        assertTrue(source.contains(
                "draft.setCustomBots(CustomBotProfileCatalog.enabled"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.writeRandomSelection"));
        assertTrue(source.contains(
                "CustomBotProfileCatalog.profilesForSession"));
        String toggleCustomBots = source.substring(
                source.indexOf("private void toggleCustomBots()"),
                source.indexOf("private void toggleCustomBotsOnly()"));
        String toggleCustomBotsOnly = source.substring(
                source.indexOf("private void toggleCustomBotsOnly()"),
                source.indexOf("private void selectBotDifficulty"));
        assertFalse(toggleCustomBots.contains("initialProperties"));
        assertFalse(toggleCustomBotsOnly.contains("initialProperties"));
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

    @Test
    void selectedCustomBotBorderUsesASubtleHeartbeat() {
        assertEquals(0f,
                GdxFrontendScreen.customBotSelectionPulse(0f), 0.0001f);
        assertEquals(0.5f,
                GdxFrontendScreen.customBotSelectionPulse(0.45f), 0.0001f);
        assertEquals(1f,
                GdxFrontendScreen.customBotSelectionPulse(0.90f), 0.0001f);
        assertEquals(0.5f,
                GdxFrontendScreen.customBotSelectionPulse(1.35f), 0.0001f);
        assertEquals(0f,
                GdxFrontendScreen.customBotSelectionPulse(1.80f), 0.0001f);
    }

    @Test
    void saveChangesOnlyEnablesForARealDraftDifference() {
        Path avatar = Path.of("avatar.png").toAbsolutePath().normalize();
        CustomBotProfile selected = CustomBotProfile.create("T-800", avatar,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.NIT);

        assertFalse(GdxFrontendScreen.customBotDraftHasChanges(selected,
                "  T-800  ", avatar,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.NIT, true));
        assertTrue(GdxFrontendScreen.customBotDraftHasChanges(selected,
                "T-1000", avatar,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.NIT, true));
        assertTrue(GdxFrontendScreen.customBotDraftHasChanges(selected,
                "T-800", null,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.NIT, true));
        assertTrue(GdxFrontendScreen.customBotDraftHasChanges(selected,
                "T-800", avatar,
                NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.NIT, false));
        assertFalse(GdxFrontendScreen.customBotDraftHasChanges(null, "",
                null, NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.RANDOM, true));
        assertTrue(GdxFrontendScreen.customBotDraftHasChanges(null, "New bot",
                null, NewGameTableDraft.BotDifficulty.MEDIUM,
                CustomBotProfile.Personality.RANDOM, true));
    }
}
