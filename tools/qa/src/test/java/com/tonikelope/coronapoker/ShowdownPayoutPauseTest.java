package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ShowdownPayoutPauseTest {

    @Test
    public void payoutKeepsOnlyABriefPauseAfterTheChipAnimation() {
        assertEquals(125, Crupier.SHOWDOWN_PAYOUT_POST_ANIMATION_PAUSE_MS);
    }
}
