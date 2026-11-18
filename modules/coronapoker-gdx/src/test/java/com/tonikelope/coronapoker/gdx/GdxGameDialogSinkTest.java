package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGameDialogSinkTest {

    @Test
    void genericDealerDialogsUseTheActiveLanguage() {
        GdxGameDialogSink sink = new GdxGameDialogSink(
                new GdxGameText("en"));

        GdxTableDialog confirmation = sink.request(
                GdxTableDialog.Kind.CONFIRM, "Continue?",
                GameDialogSink.Icon.NONE, 700, 0);
        assertEquals("CONFIRMATION", confirmation.title());
        assertEquals("CANCEL", confirmation.negativeLabel());
        assertEquals("OK", confirmation.positiveLabel());

        GdxTableDialog warning = sink.request(
                GdxTableDialog.Kind.TIMED_WARNING, "Warning",
                GameDialogSink.Icon.NONE, 700, 5);
        assertEquals("WARNING", warning.title());
        assertEquals("CLOSE", warning.positiveLabel());

        GdxTableDialog error = sink.request(
                GdxTableDialog.Kind.ERROR, "HAND VOIDED",
                GameDialogSink.Icon.NONE, 0, 0);
        assertFalse(error.showsNegative());
        assertEquals("CLOSE", error.positiveLabel());

        GdxTableDialog info = sink.request(
                GdxTableDialog.Kind.INFO, "Finished",
                GameDialogSink.Icon.EXIT, 0, 0);
        assertFalse(info.showsNegative());
        assertEquals("CLOSE", info.positiveLabel());

        GdxTableDialog zeroTrust = sink.request(
                GdxTableDialog.Kind.ZERO_TRUST, "Cryptographic anomaly",
                GameDialogSink.Icon.NONE, 700, 0);
        assertEquals("ZERO-TRUST", zeroTrust.title());
        assertFalse(zeroTrust.showsNegative());
        assertEquals("CLOSE", zeroTrust.positiveLabel());

        GdxTableDialog pendingRebuy = sink.request(
                GdxTableDialog.Kind.CONFIRM, "Pending rebuy",
                GameDialogSink.Icon.NONE, 0, 0,
                "BACK", "CANCEL REBUY");
        assertEquals("BACK", pendingRebuy.negativeLabel());
        assertEquals("CANCEL REBUY", pendingRebuy.positiveLabel());
    }

    @Test
    void tableErrorsStayModalAndAreWrittenToTheGameLogInsteadOfChat(
            @TempDir Path temporary) throws Exception {
        var text = new GdxGameText("es");
        var dialogs = new GdxGameDialogSink(text);
        var gameLog = new GdxGameLogSink(temporary.resolve("games.sqlite"));
        gameLog.reset();
        var table = tableWithLog(gameLog);
        var chat = tableChatFeed(table);

        List<String> messages = List.of(
                text.translate("rebuy.sin_margen"),
                text.translate("error.crupier_fatal"),
                text.translate("gdx.video.share.unavailable"));
        for (String message : messages) {
            var error = dialogs.request(GdxTableDialog.Kind.ERROR, message,
                    GameDialogSink.Icon.NONE, 760, 0);
            table.showDialog(error);
            assertTrue(table.hasActiveDialog());
            assertFalse(error.complete());
            assertTrue(chat.visible(0f).isEmpty());
            assertTrue(chat.history().isEmpty());

            assertTrue(table.resolveActiveDialogChoice(true));
            table.advanceDialogState();
            assertFalse(table.hasActiveDialog());
        }

        List<String> expected = messages.stream().map(message -> "ERROR: " + message).toList();
        assertEquals(expected, gameLog.snapshot().lines());
        assertEquals(expected, Files.readAllLines(gameLog.persistentLogPath()));
    }

    @Test
    void zeroTrustAlertsKeepTheDealersLogAndInformationalNoticesStillReachChat() throws Exception {
        var gameLog = new GdxGameLogSink();
        var table = tableWithLog(gameLog);
        var chat = tableChatFeed(table);
        var dialogs = new GdxGameDialogSink(new GdxGameText("es"));

        gameLog.print("Dealer incident");
        var security = dialogs.request(GdxTableDialog.Kind.ZERO_TRUST,
                "Security alert", GameDialogSink.Icon.NONE, 760, 0);
        table.showDialog(security);
        assertTrue(table.hasActiveDialog());
        assertFalse(security.complete());
        assertEquals(List.of("Dealer incident"), gameLog.snapshot().lines());
        assertTrue(chat.visible(0f).isEmpty());
        assertTrue(chat.history().isEmpty());
        assertTrue(table.resolveActiveDialogChoice(true));
        table.advanceDialogState();

        table.showDialog(dialogs.request(GdxTableDialog.Kind.INFO,
                "Game finished", GameDialogSink.Icon.EXIT, 760, 0));
        assertEquals(1, chat.history().size());
        assertEquals(GdxTableChatFeed.Kind.INFO, chat.history().get(0).kind());
        assertEquals("Game finished", chat.history().get(0).text());
        assertEquals(List.of("Dealer incident"), gameLog.snapshot().lines());
    }

    private static CoronaPokerGdxTable tableWithLog(GdxGameLogSink gameLog) {
        var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(), List.of());
        return new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> { }, () -> { }, gameLog, null);
    }

    private static GdxTableChatFeed tableChatFeed(CoronaPokerGdxTable table)
            throws ReflectiveOperationException {
        var field = CoronaPokerGdxTable.class.getDeclaredField("chatFeed");
        field.setAccessible(true);
        return (GdxTableChatFeed) field.get(table);
    }

    @Test
    void antiDosDecisionKeepsHostAuthorityAndPersistsWarningSuppression(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGameDialogSink sink = new GdxGameDialogSink(
                    new GdxGameText("es"), preferences);
            assertTrue(sink.antiDosWarningsEnabled());

            GdxTableDialog request = sink.antiDosRequest(
                    "Tráfico excesivo", "No mostrar estos avisos",
                    "NO", "SÍ");
            assertEquals(GdxTableDialog.Kind.CONFIRM, request.kind());
            assertEquals(GameDialogSink.Icon.STOP, request.icon());
            assertEquals("NO", request.negativeLabel());
            assertEquals("SÍ", request.positiveLabel());
            assertEquals("No mostrar estos avisos",
                    request.standaloneOptionLabel());
            assertFalse(request.optionEnabled());

            var decision = sink.antiDosDecision(request);
            request.toggleOptionEnabled();
            request.accept();

            assertEquals(new GameDialogSink.AntiDosDecision(true, true),
                    decision.toCompletableFuture().join());
            assertFalse(sink.antiDosWarningsEnabled());
        } finally {
            preferences.close();
        }
    }
}
