package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxSettingsNavigationContractTest {

    @Test
    void gamePagesHaveOneSharedContextAwareContract() {
        assertEquals(List.of("CIEGAS", "COMPRA", "RECOMPRA", "BOTS",
                "PARTIDA", "REGLAS"),
                GdxSettingsContract.WAITING_ROOM_GAME_PAGES);
        assertEquals(List.of("CONTROLES", "TIMBA", "CIEGAS", "COMPRA",
                "BOTS", "SESIÓN"),
                GdxSettingsContract.LIVE_TABLE_GAME_PAGES);
    }

    @Test
    void liveTableSettingsExposeARealSecondLevelSessionPage() {
        assertEquals(List.of("CONTROLES", "TIMBA", "CIEGAS", "COMPRA",
                "BOTS", "SESIÓN"),
                CoronaPokerGdxTable.settingsGamePageLabels());
    }

    @Test
    void sessionPageKeepsOnlyLiveTableOperationalActions() {
        assertEquals(List.of("Pantalla completa", "Visor de capturas",
                "Registro de la timba", "Reglas de Robert",
                "Marcar última mano", "Forzar reconexión de jugadores",
                "Detener timba", "Salir de la timba"),
                CoronaPokerGdxTable.settingsSessionActionLabels());
    }

    @Test
    void fastAccessBarRoutesEveryVisibleButtonToOneTypedAction() {
        assertEquals(List.of(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS,
                CoronaPokerGdxTable.FastAccessAction.CHAT,
                CoronaPokerGdxTable.FastAccessAction.VOICE,
                CoronaPokerGdxTable.FastAccessAction.IMAGE,
                CoronaPokerGdxTable.FastAccessAction.REBUY,
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 8)
                        .mapToObj(CoronaPokerGdxTable::fastAccessActionAt)
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(-1));
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(8));
    }

    @Test
    void hostFastAccessAddsStopImmediatelyBeforeExit() {
        assertEquals(List.of(
                CoronaPokerGdxTable.FastAccessAction.SETTINGS,
                CoronaPokerGdxTable.FastAccessAction.CHAT,
                CoronaPokerGdxTable.FastAccessAction.VOICE,
                CoronaPokerGdxTable.FastAccessAction.IMAGE,
                CoronaPokerGdxTable.FastAccessAction.REBUY,
                CoronaPokerGdxTable.FastAccessAction.GAME_LOG,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.STOP,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(index -> CoronaPokerGdxTable
                                .fastAccessActionAt(index, true))
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(9, true));
    }

    @Test
    void fastAccessSurfaceOwnsButtonsPaddingAndGaps() {
        // Closed bar owns its complete painted panel, not only the icon.
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                19f, CoronaPokerGdxTable.FAST_BAR_Y + 1f, false, true));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                83f, CoronaPokerGdxTable.FAST_BAR_Y + 60f, false, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                85f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, false, true));

        // Expanded host bar includes the gap before STOP and the complete EXIT
        // button, precisely where it overlaps the local HUD at 1920x1080.
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                434f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
        assertTrue(CoronaPokerGdxTable.fastAccessSurfaceContains(
                548f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                557f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
    }

    @Test
    void autoModeDoesNotBlockHoverDrivenTableUtilities() {
        assertFalse(CoronaPokerGdxTable.blocksTableUtilities(null));
        assertFalse(CoronaPokerGdxTable.blocksTableUtilities(
                GdxTableDialog.autoAction("VAS A PASAR")));
        assertTrue(CoronaPokerGdxTable.blocksTableUtilities(
                new GdxTableDialog(GdxTableDialog.Kind.CONFIRM,
                        "CONFIRMAR", GameDialogSink.Icon.NONE, 720, 0)));
    }
}
