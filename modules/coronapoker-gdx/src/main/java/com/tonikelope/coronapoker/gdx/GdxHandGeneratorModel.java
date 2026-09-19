/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.List;

/**
 * Native-GDX presentation model for the classic hand generator.
 *
 * <p>The ordering and odds are deliberately the same as
 * {@code HandGeneratorDialog}.  The cards are canonical, unambiguous examples;
 * this model teaches hand ranks and never participates in game evaluation.
 * The number of visible cards also matches Swing: pair/trips/two-pair/quads
 * show only the cards that define that rank.</p>
 */
final class GdxHandGeneratorModel {

    static final String ROBERT_RULES_URL
            = "https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf";
    static final String POKER_ODDS_URL
            = "https://brilliant.org/wiki/math-of-poker/";

    private static final List<Example> EXAMPLES = List.of(
            new Example("hand.high_card", "4,74:1",
                    List.of("A_P", "J_C", "9_T", "6_D", "3_C")),
            new Example("hand.one_pair", "1,28:1",
                    List.of("8_P", "8_C")),
            new Example("hand.two_pair", "3,26:1",
                    List.of("K_P", "K_C", "5_T", "5_D")),
            new Example("hand.three_of_a_kind", "19,7:1",
                    List.of("7_P", "7_C", "7_T")),
            new Example("hand.straight", "20,65:1",
                    List.of("5_P", "6_C", "7_T", "8_D", "9_P")),
            new Example("hand.flush", "32,05:1",
                    List.of("A_C", "J_C", "8_C", "5_C", "2_C")),
            new Example("hand.full_house", "37,52:1",
                    List.of("Q_P", "Q_C", "Q_T", "4_D", "4_C")),
            new Example("hand.four_of_a_kind", "594:1",
                    List.of("10_P", "10_C", "10_T", "10_D")),
            new Example("hand.straight_flush", "3589,57:1",
                    List.of("5_T", "6_T", "7_T", "8_T", "9_T")),
            new Example("hand.royal_flush", "30939:1",
                    List.of("10_D", "J_D", "Q_D", "K_D", "A_D"))
    );

    private int index;

    GdxHandGeneratorModel() {
        this(EXAMPLES.size() - 1);
    }

    GdxHandGeneratorModel(int initialIndex) {
        index = Math.max(0, Math.min(initialIndex, EXAMPLES.size() - 1));
    }

    Example current() {
        return EXAMPLES.get(index);
    }

    int index() {
        return index;
    }

    int size() {
        return EXAMPLES.size();
    }

    boolean canPrevious() {
        return index > 0;
    }

    boolean canNext() {
        return index + 1 < EXAMPLES.size();
    }

    void previous() {
        if (canPrevious()) index--;
    }

    void next() {
        if (canNext()) index++;
    }

    record Example(String translationKey, String probability,
            List<String> cards) {

        Example {
            cards = List.copyOf(cards);
            if (cards.size() < 2 || cards.size() > 5
                    || cards.stream().distinct().count() != cards.size()) {
                throw new IllegalArgumentException(
                        "A hand example requires two to five unique cards");
            }
        }
    }
}
