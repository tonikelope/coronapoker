package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void sessionPageKeepsTheOperationalActionsRemovedFromRightClick() {
        assertEquals(List.of("Pantalla completa", "Visor de capturas",
                "Registro de la timba", "Reglas de Robert",
                "Generador de jugadas", "Marcar última mano",
                "Forzar reconexión de jugadores", "Detener timba",
                "Salir de la timba"),
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
}
