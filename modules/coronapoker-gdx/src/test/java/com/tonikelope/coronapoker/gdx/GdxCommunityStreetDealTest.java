/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxCommunityStreetDealTest {

    @Test
    void theCoveredFlopFliesToSlotZeroAndBothMovingCardsLandTogether() {
        GdxCommunityStreetDeal spread = GdxCommunityStreetDeal.flop(
                1f, 0.25f, 0.62f, 100);
        float gap = 138f;
        assertFalse(spread.visible(0.74f));
        assertTrue(spread.inFlight(0.875f));
        assertEquals(0.5f, spread.flightProgress(0.875f), 0.001f);
        assertFalse(spread.inFlight(1f));
        assertEquals(0.5f, spread.flipProgress(1.31f), 0.001f);
        assertFalse(spread.flopFaceUp(1.31f));
        assertTrue(spread.flopFaceUp(1.63f));
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(0f, slot * gap + spread.flopOffsetX(slot, gap, 1.63f));
            assertEquals(slot * gap, slot * gap
                    + spread.flopOffsetX(slot, gap, spread.revealEnd()), 0.001f);
        }
        float middle = gap + spread.flopOffsetX(1, gap, 1.96f);
        float top = 2f * gap + spread.flopOffsetX(2, gap, 1.96f);
        assertEquals(gap / 2f, middle, 0.001f);
        assertEquals(middle * 2f, top, 0.001f);
        assertEquals(0f, spread.flopOffsetX(0, gap, 1.96f), 0.001f);
    }

    @Test
    void theSingleCardStaysCoveredThroughItsFlightThenFlipsAtItsSlot() {
        GdxCommunityStreetDeal deal = GdxCommunityStreetDeal.single(
                2f, 0.25f, 0.62f);
        assertFalse(deal.visible(1.74f));
        assertTrue(deal.inFlight(1.875f));
        assertEquals(0.5f, deal.flightProgress(1.875f), 0.001f);
        assertEquals(0f, deal.revealProgress(1.875f));
        assertFalse(deal.inFlight(2f));
        assertEquals(0f, deal.revealProgress(2f));
        assertEquals(0.5f, deal.revealProgress(2.31f), 0.001f);
        assertEquals(2.62f, deal.revealEnd(), 0.001f);
    }

    @Test
    void aShortLeadInStillWaitsForLandingAndTheSlowestSettingsFitTheBarrier() {
        GdxCommunityStreetDeal shortLead = GdxCommunityStreetDeal.single(
                0f, 0.3f, 0f);
        assertTrue(shortLead.inFlight(0.29f));
        assertEquals(0f, shortLead.revealProgress(0.29f));
        assertEquals(1f, shortLead.revealProgress(0.3f));
        assertTrue(GdxCommunityStreetDeal.single(2f, 0.75f, 1.5f)
                .revealEnd() < 3.9f);
        assertTrue(GdxCommunityStreetDeal.flop(2f, 0.75f, 1.5f, 150)
                .revealEnd() < 3.8f);
    }
}
