package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.game.CardCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GdxHandGeneratorModelTest {

    @Test
    void startsAtRoyalFlushAndStopsAtBothSwingBoundaries() {
        GdxHandGeneratorModel model = new GdxHandGeneratorModel();

        assertEquals(9, model.index());
        assertEquals("hand.royal_flush", model.current().translationKey());
        assertFalse(model.canNext());
        model.next();
        assertEquals(9, model.index());

        for (int index = 0; index < 20; index++) model.previous();
        assertEquals(0, model.index());
        assertEquals("hand.high_card", model.current().translationKey());
        assertFalse(model.canPrevious());
    }

    @Test
    void preservesSwingOrderOddsAndCanonicalVisibleCardCounts() {
        List<String> keys = List.of("hand.high_card", "hand.one_pair",
                "hand.two_pair", "hand.three_of_a_kind", "hand.straight",
                "hand.flush", "hand.full_house", "hand.four_of_a_kind",
                "hand.straight_flush", "hand.royal_flush");
        List<String> odds = List.of("4,74:1", "1,28:1", "3,26:1",
                "19,7:1", "20,65:1", "32,05:1", "37,52:1", "594:1",
                "3589,57:1", "30939:1");
        List<Integer> visibleCardCounts = List.of(5, 2, 4, 3, 5, 5, 5, 4,
                5, 5);
        GdxHandGeneratorModel model = new GdxHandGeneratorModel(0);

        assertEquals(keys.size(), model.size());
        for (int index = 0; index < model.size(); index++) {
            GdxHandGeneratorModel.Example example = model.current();
            assertEquals(keys.get(index), example.translationKey());
            assertEquals(odds.get(index), example.probability());
            assertEquals(visibleCardCounts.get(index), example.cards().size());
            assertEquals(example.cards().size(),
                    new HashSet<>(example.cards()).size());
            Set<CardCode> parsed = new HashSet<>();
            for (String card : example.cards()) {
                parsed.add(CardCode.parseShortCode(card));
            }
            assertEquals(example.cards().size(), parsed.size());
            if (index + 1 < model.size()) {
                assertTrue(model.canNext());
                model.next();
            }
        }
    }
}
