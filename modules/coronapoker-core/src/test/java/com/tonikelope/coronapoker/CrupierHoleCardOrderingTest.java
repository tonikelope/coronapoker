/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.CardCode;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CrupierHoleCardOrderingTest {

    @Test
    void deferredStraddleOrderingComparesRanksAcrossSuits() {
        int fiveDiamonds = CardCode.of("5", "D").index() + 1;
        int tenClubs = CardCode.of("10", "T").index() + 1;

        assertTrue(Crupier.holeCardsNeedDisplaySwap(
                fiveDiamonds, tenClubs));
        assertFalse(Crupier.holeCardsNeedDisplaySwap(
                tenClubs, fiveDiamonds));
    }

    @Test
    void equalRanksKeepTheirDealtOrder() {
        int queenSpades = CardCode.of("Q", "P").index() + 1;
        int queenHearts = CardCode.of("Q", "C").index() + 1;

        assertFalse(Crupier.holeCardsNeedDisplaySwap(
                queenSpades, queenHearts));
    }
}
