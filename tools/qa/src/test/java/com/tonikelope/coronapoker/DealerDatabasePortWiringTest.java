package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DealerDatabasePortWiringTest {

    @Test
    void canonicalDealerUsesInjectedDatabaseAndClassicAdapterPreservesLock() throws Exception {
        Path root = root();
        String dealer = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/Crupier.java"));
        String dealerCode = dealer.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        String adapter = Files.readString(root.resolve(
                "src/main/java/com/tonikelope/coronapoker/SwingGameDatabase.java"));

        assertTrue(dealer.contains("GameDatabase gameDatabase"));
        assertTrue(dealer.contains("synchronized (game_database.lock())"));
        assertTrue(dealer.contains("game_database.connection()"));
        assertFalse(dealer.contains("GameFrame.SQL_LOCK"));
        assertFalse(dealer.contains("Helpers.getSQLITE()"));
        assertTrue(adapter.contains("return GameFrame.SQL_LOCK"));
        assertTrue(adapter.contains("return Helpers.getSQLITE()"));
        assertTrue(dealer.contains("host_configuration.create(this.getUGI())"));
        assertFalse(dealer.contains("GameConfigWireV1.fromGlobals()"));
        assertFalse(dealer.contains("GameFrame.RECOVER_ID"));
        assertFalse(dealer.contains("GameFrame.persistRecoverSettings"));
        assertFalse(dealerCode.contains("GameFrame."),
                "Crupier executable code must remain frontend-neutral");
        assertTrue(dealer.contains("table_display.showWinner("));
        assertTrue(dealer.contains("table_display.showLoser("));
        assertTrue(dealer.contains("table_display.showPlayerCards("));
        assertTrue(dealer.contains("table_display.resetPlayer("));
        assertTrue(dealer.contains("table_display.revealPlayerCards("));
        assertTrue(dealer.contains("table_display.showNeutralHand("));
        assertTrue(dealer.contains("table_display.showRabbitNotice("));
        assertTrue(dealer.contains("table_display.setRebuyWaiting("));
        assertTrue(dealer.contains("table_display.setStraddleThinking("));
        assertTrue(dealer.contains("table_display.setShowdownHighlight("));
        assertTrue(dealer.contains("new TableVisualEvent.ShowdownHighlight("));
        assertTrue(dealer.contains("new TableVisualEvent.ActionControls("));
        assertTrue(dealer.contains("setVoluntaryShowAction(true"));
        assertTrue(dealer.contains("setVoluntaryShowAction(false"));
        assertTrue(dealer.contains("game_decisions.replayRecoveredAction("));
        assertTrue(dealer.contains("table_display.suspendVoluntaryShowAction("));
        assertTrue(dealer.contains("table_display.activateLocalPreActions("));
        assertTrue(dealer.contains("table_display.deactivateLocalControls("));
        assertTrue(dealer.contains("table_display.deactivateLocalPreActions("));
        assertTrue(dealer.contains("table_display.showVoluntaryShowAction("));
        assertTrue(dealer.contains("table_display.hideVoluntaryShowAction("));
        assertTrue(dealer.contains("table_display.startIwtsthCandidateBlinking("));
        assertTrue(dealer.contains("table_display.setShowdownHighlight("));
        assertTrue(dealer.contains("new TableVisualEvent.ShowdownHighlight("));
        assertFalse(dealerCode.contains(".setWinner("));
        assertFalse(dealerCode.contains(".setLoser("));
        assertFalse(dealerCode.contains(".showCards("));
        assertFalse(dealerCode.contains(".resetGUI("));
        assertFalse(dealerCode.contains(".refreshPos("));
        assertFalse(dealerCode.contains(".refreshPositionChipIcons("));
        assertFalse(dealerCode.contains(".destaparCartas("));
        assertFalse(dealerCode.contains(".prepararDestapeAnimado("));
        assertFalse(dealerCode.contains(".showJugadaNeutral("));
        assertFalse(dealerCode.contains(".getChat_notify_label("));
        assertFalse(dealerCode.contains(".setNotifyRabbitLabel("));
        assertFalse(dealerCode.contains(".setRebuying("));
        assertFalse(dealerCode.contains("((RemotePlayer) jugador).showRebuyOutcome("));
        assertFalse(dealerCode.contains(".showStraddleThinking("));
        assertFalse(dealerCode.contains(".clearStraddleThinking("));
        assertFalse(dealerCode.contains(".setShowdownHand("));
        assertFalse(dealerCode.contains(".getPlayer_fold_button("));
        assertFalse(dealerCode.contains(".getPlayer_check_button("));
        assertFalse(dealerCode.contains(".getPlayer_bet_button("));
        assertFalse(dealerCode.contains(".getPlayer_allin_button("));
        assertFalse(dealerCode.contains(".activarPreBotones("));
        assertFalse(dealerCode.contains(".desactivarControles("));
        assertFalse(dealerCode.contains(".desActivarPreBotones("));
        assertFalse(dealerCode.contains(".activar_boton_mostrar("));
        assertFalse(dealerCode.contains(".desactivar_boton_mostrar("));
        assertFalse(dealerCode.contains(".getIwtsth_blink_timer("));
        assertFalse(dealerCode.contains(".setShowdownHand("));
    }

    private static Path root() {
        for (Path path = Path.of("").toAbsolutePath(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("tools/qa/pom.xml"))) return path;
        }
        throw new IllegalStateException("CoronaPoker root not found");
    }
}
