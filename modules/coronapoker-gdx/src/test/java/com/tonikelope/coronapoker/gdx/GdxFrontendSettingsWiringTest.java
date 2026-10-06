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
        assertTrue(method.contains("drawTextItem(fittedTextItem("),
                "the final overlay must draw its text in the same frame");
        assertTrue(method.contains("fittedTextItem(fpsFont,"),
                "the FPS overlay must use a stable-width monospaced face");
        assertFalse(method.contains("textFit("),
                "queued text is cleared before the next frame");
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
