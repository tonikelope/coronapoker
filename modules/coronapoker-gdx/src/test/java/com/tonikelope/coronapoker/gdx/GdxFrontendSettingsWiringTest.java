package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.LobbyCommand;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.NewGameTableDraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxFrontendSettingsWiringTest {

    @Test
    void hostPublishesChangedTableAndChatNotificationSettings() {
        NewGameTableDraft table = new NewGameTableDraft();
        NewGameTableDraft.Settings original = table.snapshot();
        table.setThinkSeconds(original.thinkSeconds() + 1);
        NewGameTableDraft.Settings changed = table.snapshot();

        List<LobbyCommand> commands = GdxFrontendScreen
                .lobbySettingsCommands(snapshot(true, original, true),
                        changed, false);

        assertEquals(2, commands.size());
        assertEquals(changed, assertInstanceOf(
                LobbyCommand.UpdateTableSettings.class, commands.get(0))
                .settings());
        assertEquals(false, assertInstanceOf(
                LobbyCommand.SetChatNotifications.class, commands.get(1))
                .enabled());
    }

    @Test
    void clientPublishesOnlyItsLocalChatNotificationPreference() {
        NewGameTableDraft.Settings settings =
                new NewGameTableDraft().snapshot();

        List<LobbyCommand> commands = GdxFrontendScreen
                .lobbySettingsCommands(snapshot(false, settings, true),
                        settings, false);

        assertEquals(1, commands.size());
        assertInstanceOf(LobbyCommand.SetChatNotifications.class,
                commands.get(0));
    }

    @Test
    void unchangedLobbySettingsDoNotProduceNetworkWork() {
        NewGameTableDraft.Settings settings =
                new NewGameTableDraft().snapshot();

        assertEquals(List.of(), GdxFrontendScreen.lobbySettingsCommands(
                snapshot(true, settings, true), settings, true));
    }

    @Test
    void globalFpsOverlayDrawsItsGlyphsInTheImmediateTopLayerPass()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private void drawFrontendFpsCounter()");
        int end = source.indexOf("private void drawFittedCenteredInBox(",
                start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("drawFittedCenteredInBox(fpsFont,"),
                "the final overlay must draw centered text in the same frame");
        assertTrue(method.contains("panelWidth - 16f, panelHeight,"),
                "the FPS overlay must use a stable-width monospaced face");
        assertFalse(method.contains("y + 29f"),
                "vertical centering must not rely on a fixed baseline offset");
        assertFalse(method.contains("shapes.rect("),
                "the FPS overlay must not draw the old cyan underline");
    }

    @Test
    void graphicsVsyncUsesTheSharedValueAndPerformanceContract()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains("GdxSettingsContract.vsyncStatusLabel("));
        assertTrue(frontend.contains("GdxSettingsLayout.ROW_HEIGHT), \"gdx_vsync\")"));
        assertTrue(table.contains("GdxSettingsContract.vsyncStatusLabel("));
        assertTrue(table.contains("\"gdx_vsync\", viewportBounds, properties"));
        assertTrue(table.contains("drawFittedCenteredInBox(gameLogFont,"));
    }

    @Test
    void rootSettingsDrawTheirTooltipLayerAboveTheModal() throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains(
                "if (rootSurfaceModal && dropdown == Dropdown.NONE)"));
        int tooltipGuardStart = frontend.indexOf(
                "if (rootSurfaceModal && dropdown == Dropdown.NONE)");
        int tooltipCall = frontend.indexOf("drawTooltipTopLayer();", tooltipGuardStart);
        assertTrue(tooltipCall > tooltipGuardStart);
        assertFalse(frontend.substring(tooltipGuardStart, tooltipCall)
                .contains("hasBlockingFrontendModal"));
        assertTrue(frontend.contains("case SETTINGS -> drawSettingsScreen();"),
                "menu and lobby settings must share the same sharp modal");
        assertTrue(frontend.contains(
                "settingsReturnSurface == Surface.LOBBY && lobby != null"),
                "only the blurred backdrop may differ between menu and lobby");
    }

    @Test
    void rootSettingsOwnTheirWheelAndScrollbarDespiteProtectingTheBackdrop()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int wheel = frontend.indexOf(
                "public boolean scrolled(float amountX, float amountY)");
        int settingsWheel = frontend.indexOf(
                "if (surface == Surface.SETTINGS && amountY != 0f", wheel);
        int modalShield = frontend.indexOf(
                "if (hasBlockingFrontendModal() && dropdown == Dropdown.NONE)",
                wheel);

        assertTrue(wheel >= 0 && settingsWheel > wheel);
        assertTrue(settingsWheel < modalShield,
                "settings must route their wheel before the root-modal shield");
        assertTrue(frontend.contains(
                "boolean settingsOwnsPointer = surface == Surface.SETTINGS"));
        assertTrue(frontend.contains(
                "&& !settingsScrollBlockedByChildDialog();"));
        assertTrue(frontend.contains("currentSettingsPixelRows()"),
                "wheel limits must come from the current subsection rows");
    }

    @Test
    void menuLobbyAndLiveSettingsConsumeTheSameVisualContracts()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(frontend.contains(
                "GdxSettingsLayout.contentClip(content, settingsRowsClip)"));
        assertTrue(table.contains(
                "GdxSettingsLayout.contentClip(content)"));
        assertTrue(frontend.contains(
                "GdxSettingsLayout.AUDIO_MASTER_ROW_OFFSET"));
        assertTrue(table.contains(
                "GdxSettingsLayout.AUDIO_MASTER_ROW_OFFSET"));
        assertTrue(frontend.contains("GdxSettingsLayout.splitActionRow("));
        assertTrue(table.contains("GdxSettingsLayout.splitActionRow("));
        assertTrue(frontend.contains("GdxSettingsStyle.drawShortcutRow("));
        assertTrue(table.contains("GdxSettingsStyle.drawShortcutRow("));
        assertTrue(frontend.contains("GdxSettingsStyle.drawRowScrollbar("));
        assertTrue(table.contains("GdxSettingsStyle.drawRowScrollbar("));
        assertTrue(frontend.contains("textFit(smallFont, key,"));
        assertTrue(table.contains(
                "drawFittedCentered(settingsSmallFont, key,"),
                "live shortcut keys must not silently grow beyond the menu");
        assertTrue(frontend.contains(
                "GdxSettingsStyle.shortcutKeyText(capturing)"));
        assertTrue(table.contains(
                "GdxSettingsStyle.shortcutKeyText(capturing)"));
        assertTrue(table.contains(
                "drawFittedLeft(settingsTitleFont,"),
                "the live settings title must retain the master font path");
        assertFalse(table.contains(
                "capturing ? settingsSmallFont\n                : settingsActionFont"));
    }

    @Test
    void menuLobbyAndLiveControlsShareTheSameSixActionRows()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertEquals(6, GdxSettingsContract.CONTROL_ROWS);
        assertTrue(frontend.contains(
                "GdxSettingsContract.CONTROL_ROWS + entries.size()"));
        assertTrue(table.contains(
                "GdxSettingsContract.CONTROL_ROWS + entries.size()"));
        for (String key : List.of("auto_action_buttons",
                "auto_call_enabled", "auto_call_max",
                "auto_action_persist", "modo_auto_confirm",
                "confirmar_todo")) {
            assertTrue(frontend.contains("\"" + key + "\""), key);
            assertTrue(table.contains("\"" + key + "\""), key);
            assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(key),
                    key + " must participate in Cancel/Save transactions");
        }
        assertTrue(frontend.contains(
                "presentationSettings.autoRebuyOnBroke()"));
        assertTrue(table.contains("presentationSettings.autoRebuyOnBroke()"));
    }

    @Test
    void iconButtonsHaveNoDecorativeSeparatorAndUseVectorKeyIcons()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int start = frontend.indexOf("private void drawButtonIconColumn(");
        int end = frontend.indexOf("private void drawMainMenuIcon(", start);
        assertTrue(start >= 0 && end > start);
        String iconColumn = frontend.substring(start, end);
        assertFalse(iconColumn.contains("shapes.rect("),
                "the old vertical separator must not return");
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.SETTINGS"));
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.EXIT"));
        assertTrue(frontend.contains("GdxUiIconRenderer.Icon.CAMERA"));
        for (String icon : List.of("ADD", "PLAYERS", "STATISTICS",
                "SETTINGS", "INFO", "EXIT", "CAMERA")) {
            assertTrue(frontend.contains("GdxUiIconRenderer.Icon." + icon),
                    icon + " must use the shared resolution-independent set");
        }
    }

    @Test
    void mainMenuLanguageUsesTheSameGlassMaterialAsItsPeers()
            throws Exception {
        String frontend = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/GdxFrontendScreen.java"),
                StandardCharsets.UTF_8);
        int menuStart = frontend.indexOf("private void drawMainMenu()");
        int menuEnd = frontend.indexOf("private void drawMainMenuQuote(",
                menuStart);
        int choiceStart = frontend.indexOf("private void mainMenuChoice(");
        int choiceEnd = frontend.indexOf("private void iconButton(",
                choiceStart);
        assertTrue(menuStart >= 0 && menuEnd > menuStart);
        assertTrue(choiceStart >= 0 && choiceEnd > choiceStart);
        String menu = frontend.substring(menuStart, menuEnd);
        String languageChoice = frontend.substring(choiceStart, choiceEnd);

        assertTrue(menu.contains("mainMenuChoice(975f, 310f, 350f, 82f"));
        assertFalse(menu.contains("choice(975f, 310f, 350f, 82f"));
        assertTrue(languageChoice.contains("themedButton("));
        assertTrue(languageChoice.contains("ButtonTone.NEUTRAL"));
    }

    @Test
    void fittedTextUsesProportionalScalingBeforeItsFinalEllipsisGuard() {
        assertEquals(1f, GdxFrontendScreen.fittedTextScale(
                100f, 120f, 0.72f));
        assertEquals(0.8f, GdxFrontendScreen.fittedTextScale(
                150f, 120f, 0.72f));
        assertEquals(0.72f, GdxFrontendScreen.fittedTextScale(
                300f, 120f, 0.72f));
    }

    @Test
    void finalSummaryUsesStackInBothLanguages() {
        assertEquals("STACK", new GdxGameText("es")
                .translate("balance.fichas"));
        assertEquals("STACK", new GdxGameText("en")
                .translate("balance.fichas"));
    }

    @Test
    void compactSummaryAndQuickAccessIconsRemainResolutionIndependent()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        int summaryStart = table.indexOf("private void drawFinalSummary()");
        int summaryEnd = table.indexOf("private void drawFinalExitPending(",
                summaryStart);
        assertTrue(summaryStart >= 0 && summaryEnd > summaryStart);
        String summary = table.substring(summaryStart, summaryEnd);

        assertTrue(summary.contains("GdxUiIconRenderer.draw(shapes,"));
        assertTrue(summary.contains("FINAL_NAV_ICONS[index]"));
        assertTrue(summary.contains("GdxUiIconRenderer.Icon.CAMERA"));
        assertTrue(summary.contains("drawFinalNavigationLabel("),
                "final navigation must not apply button padding twice");
        assertTrue(table.contains("action == FastAccessAction.SETTINGS"));
        assertFalse(table.contains("private Texture finalLogIcon;"),
                "a 24 px log PNG must never be enlarged in the final header");
        assertFalse(table.contains("private Texture finalScreenshotIcon;"));
        assertTrue(table.contains("GdxUiIconRenderer.Icon.SETTINGS"),
                "the 24 px quick-settings PNG also needs vector geometry");

        String iconRenderer = Files.readString(Path.of("src/main/java/com/"
                + "tonikelope/coronapoker/gdx/GdxUiIconRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(iconRenderer.contains("drawGearTooth("),
                "settings must use a real vector cog silhouette");
        assertTrue(iconRenderer.contains(
                "shapes.triangle(previousOuterX, previousOuterY"),
                "rings must be continuous filled meshes without cap seams");
        int ringStart = iconRenderer.indexOf("private static void ring(");
        int ringEnd = iconRenderer.indexOf("private static void roundedRect(",
                ringStart);
        assertTrue(ringStart >= 0 && ringEnd > ringStart);
        assertFalse(iconRenderer.substring(ringStart, ringEnd)
                .contains("rectLine("),
                "annuli must not expose overlapping line caps");
        assertFalse(iconRenderer.contains("MathUtils.random"),
                "toolbar artwork must stay deterministic between frames");
    }

    @Test
    void tableTimeoutWarningUsesHighResolutionArtwork() throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);

        assertTrue(table.contains(
                "timeoutIcon = texture(\"images/action/timeout.png\")"));
        assertFalse(table.contains(
                "timeoutIcon = texture(\"images/menu/timeout.png\")"));
    }

    @Test
    void communityHudFitsBothDynamicStatusLabelsInsideTheirBoxes()
            throws Exception {
        String table = Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8);
        int start = table.indexOf("private void drawCommunityHud(");
        int end = table.indexOf("static String communityBlindsText(", start);
        assertTrue(start >= 0 && end > start);
        String method = table.substring(start, end);
        assertTrue(method.contains(
                "drawFittedCenteredInBox(actionFont, blinds,"));
        assertTrue(method.contains(
                "drawFittedCenteredInBox(actionFont, hand,"));
        assertFalse(method.contains("float iconX = communityPauseX"),
                "The compact pause control must remain text-only");
        assertTrue(method.contains(
                "communityPauseX + 8f, communityPauseY + 5f"),
                "The pause label must use the space freed by its old icon");
    }

    private static LobbySnapshot snapshot(boolean host,
            NewGameTableDraft.Settings settings, boolean notifications) {
        LobbyParticipant local = new LobbyParticipant(
                host ? "server" : "client", null, true, host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        LobbyParticipant remote = new LobbyParticipant(
                host ? "client" : "server", null, false, !host, false, true,
                false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
        return new LobbySnapshot(local.nickname(), "server",
                "localhost:2345", host,
                LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "",
                List.of(local, remote), List.of(), settings, false,
                notifications);
    }
}
