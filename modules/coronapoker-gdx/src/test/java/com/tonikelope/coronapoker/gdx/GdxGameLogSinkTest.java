/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.GameLogSink.ShowdownEntry;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxGameLogSinkTest {

    @Test
    void showdownRewritesOriginalRowsExactlyOnceLikeSwing() {
        GdxGameLogSink sink = new GdxGameLogSink();
        sink.print("(  )CoronaBot$1   (---)   PAREJA DE ASES");
        sink.print("(  )CoronaBot$2   (---)   CARTA ALTA");

        sink.updateShowdownCards(List.of(
                new ShowdownEntry("CoronaBot$1", true,
                        "A♥ A♣", "TRÍO DE ASES"),
                new ShowdownEntry("CoronaBot$2", false, "", "")));

        assertEquals(List.of(
                "(  )CoronaBot$1   (A♥ A♣) PAREJA DE ASES -> TRÍO DE ASES",
                "(  )CoronaBot$2   (***)   CARTA ALTA"),
                sink.snapshot().lines());
    }

    @Test
    void nicknameReplacementIsLiteralAndDoesNotTouchOtherPlayers() {
        String line = "(  )XCoronaBot$1 (---) PAREJA";
        assertEquals(line, GdxGameLogSink.rewriteShowdownLine(line,
                List.of(new ShowdownEntry("CoronaBot$1", false, "", ""))));
    }

    @Test
    void completedSessionAppendsTheFinalBalanceTableLikeSwing() {
        GdxGameLogSink sink = new GdxGameLogSink();
        TableSessionSummary summary = new TableSessionSummary("local", 4,
                125L, 0L, TableSessionSummary.CloseReason.COMPLETED,
                List.of(
                        new TableSessionSummary.PlayerBalance(
                                "local", 12.5d, 10d, 0),
                        new TableSessionSummary.PlayerBalance(
                                "rival", 7.5d, 10d, 0),
                        new TableSessionSummary.PlayerBalance(
                                "even", 10d, 10d, 0)));

        sink.appendFinalSummary(summary, new GdxGameText("es"));

        String log = String.join("\n", sink.snapshot().lines());
        assertTrue(log.contains("LA TIMBA HA TERMINADO"));
        assertTrue(log.contains("NICK"));
        assertTrue(log.contains("RESULTADO"));
        assertTrue(log.contains("local"));
        assertTrue(log.contains("GANA 2.5"));
        assertTrue(log.contains("PIERDE 2.5"));
        assertTrue(log.contains("NI GANA NI PIERDE"));
    }

    @Test
    void recoverableStopDoesNotForgeACompletedSessionLog() {
        GdxGameLogSink sink = new GdxGameLogSink();
        TableSessionSummary summary = new TableSessionSummary("local", 4,
                125L, 0L,
                TableSessionSummary.CloseReason.RECOVERABLE_STOP,
                List.of(new TableSessionSummary.PlayerBalance(
                        "local", 12.5d, 10d, 0)));

        sink.appendFinalSummary(summary, new GdxGameText("es"));

        assertFalse(sink.snapshot().lines().stream()
                .anyMatch(line -> line.contains("LA TIMBA HA TERMINADO")));
    }
}
