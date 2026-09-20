/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonikelope.coronapoker.core.game.GameLogSink.ShowdownEntry;
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
}
