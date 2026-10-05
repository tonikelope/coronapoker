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
        assertEquals(List.of("TIMBA", "CIEGAS", "COMPRA", "BOTS"),
                GdxSettingsContract.LIVE_TABLE_GAME_PAGES);
    }

    @Test
    void liveTableSettingsExcludeFastAccessSessionOperations() {
        assertEquals(List.of("TIMBA", "CIEGAS", "COMPRA", "BOTS"),
                CoronaPokerGdxTable.settingsGamePageLabels());
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
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(CoronaPokerGdxTable::fastAccessActionAt)
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(-1));
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(9));
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
                CoronaPokerGdxTable.FastAccessAction.SCREENSHOTS,
                CoronaPokerGdxTable.FastAccessAction.FULLSCREEN,
                CoronaPokerGdxTable.FastAccessAction.STOP,
                CoronaPokerGdxTable.FastAccessAction.EXIT),
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(index -> CoronaPokerGdxTable
                                .fastAccessActionAt(index, true))
                        .toList());
        assertEquals(CoronaPokerGdxTable.FastAccessAction.NONE,
                CoronaPokerGdxTable.fastAccessActionAt(10, true));
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
                614f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
        assertFalse(CoronaPokerGdxTable.fastAccessSurfaceContains(
                616f, CoronaPokerGdxTable.FAST_BAR_Y + 30f, true, true));
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
