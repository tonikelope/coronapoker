package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RabbitShowdownHandSelectionTest {

    @Test
    void rabbitReplacesHighlightCardsWhenPlayerAlreadyShowedTheirHand() {
        List<Card> beforeRabbit = Collections.singletonList(null);
        List<Card> rabbitHand = Arrays.asList(null, null);

        List<Card> selected = LocalPlayer.showdownHandAfterRabbit(
                true, beforeRabbit, rabbitHand);

        assertEquals(rabbitHand, selected);
    }

    @Test
    void rabbitDoesNotExposeHighlightBeforePlayerShowsTheirCards() {
        List<Card> hidden = null;
        List<Card> rabbitHand = Arrays.asList(null, null);

        assertSame(hidden, LocalPlayer.showdownHandAfterRabbit(
                false, hidden, rabbitHand));
    }

    @Test
    void missingRabbitEvaluationPreservesExistingHighlight() {
        List<Card> beforeRabbit = Collections.singletonList(null);

        assertSame(beforeRabbit, LocalPlayer.showdownHandAfterRabbit(
                true, beforeRabbit, null));
    }
}
