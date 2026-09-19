package com.tonikelope.coronapoker.gdx;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GdxGameLogSelectionTest {

    @Test
    void copiesInclusiveSelectionInEitherDirection() {
        List<String> lines = List.of("uno", "dos", "tres", "cuatro");
        String expected = String.join(System.lineSeparator(),
                "dos", "tres", "cuatro");

        assertEquals(expected,
                CoronaPokerGdxTable.selectedGameLogText(lines, 1, 3));
        assertEquals(expected,
                CoronaPokerGdxTable.selectedGameLogText(lines, 3, 1));
    }

    @Test
    void invalidOrEmptySelectionCopiesNothing() {
        assertEquals("", CoronaPokerGdxTable.selectedGameLogText(
                List.of("uno"), -1, 0));
        assertEquals("", CoronaPokerGdxTable.selectedGameLogText(
                List.of(), 0, 0));
    }

    @Test
    void copiedSelectionOmitsInternalSwingRoleMarkers() {
        assertEquals(String.join(System.lineSeparator(),
                        " server 10 10", " 70 70"),
                CoronaPokerGdxTable.selectedGameLogText(
                        List.of("(D ) server 10 10", "($$) 70 70"),
                        0, 1));
    }
}
