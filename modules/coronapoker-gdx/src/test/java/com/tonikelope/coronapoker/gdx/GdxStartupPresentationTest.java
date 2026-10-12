package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GdxStartupPresentationTest {

    @Test
    void copyrightSignatureUsesTheRuntimeYearAfterTheStartYear() {
        assertEquals("© 2020–2026 Tonikelope Solutions S. L.",
                CoronaPokerGdxTable.startupPresentationTitle(2026));
        assertEquals("© 2020–2020 Tonikelope Solutions S. L.",
                CoronaPokerGdxTable.startupPresentationTitle(2019));
    }

    @Test
    void startupCardCompositionIsStablePerRunButVariesBetweenRuns() {
        CoronaPokerGdxTable.IntroCardLayout first =
                CoronaPokerGdxTable.introCardLayout(42L);
        CoronaPokerGdxTable.IntroCardLayout repeated =
                CoronaPokerGdxTable.introCardLayout(42L);
        CoronaPokerGdxTable.IntroCardLayout another =
                CoronaPokerGdxTable.introCardLayout(43L);

        assertEquals(first, repeated);
        assertNotEquals(first, another);
        assertEquals(52, first.cards().size());
        Set<Integer> identities = new HashSet<>();
        for (CoronaPokerGdxTable.IntroCardPlacement card : first.cards()) {
            identities.add(card.card());
            assertTrue(card.xNormalised() >= -0.47f
                    && card.xNormalised() <= 0.47f);
            assertTrue(card.yNormalised() >= -0.50f
                    && card.yNormalised() <= 0.50f);
            assertTrue(card.exitAngle() >= 0f && card.exitAngle() < 1f);
            assertTrue(Math.abs(card.spin()) >= 430f);
        }
        assertEquals(52, identities.size(),
                "every physical card must occupy exactly one stack layer");
    }

    @Test
    void startupPilePreservesItsOriginalEnvelopeWithoutAnOpaqueGrid() {
        CoronaPokerGdxTable.IntroCardLayout layout =
                CoronaPokerGdxTable.introCardLayout(7L);
        float[] expectedX = {
            -0.46f, 0f, 0.46f, -0.46f, 0.46f, -0.46f, 0f, 0.46f
        };
        float[] expectedY = {
            -0.48f, -0.48f, -0.48f, 0f, 0f, 0.48f, 0.48f, 0.48f
        };

        for (int slot = 0; slot < expectedX.length; slot++) {
            CoronaPokerGdxTable.IntroCardPlacement card =
                    layout.cards().get(slot);
            assertTrue(Math.abs(card.xNormalised() - expectedX[slot])
                    <= 0.0101f);
            assertTrue(Math.abs(card.yNormalised() - expectedY[slot])
                    <= 0.0101f);
            assertTrue(Math.abs(card.rotation()) <= 36f);
        }
    }

    @Test
    void fullSizeLogoKeepsTheOriginalDockTimingAfterCardsClear() {
        float clearEnd = CoronaPokerGdxTable.introCardClearEndSeconds();
        float originalDockStart = CoronaPokerGdxTable
                .STARTUP_PRESENTATION_END_SECONDS + 1.42f;

        assertEquals(0f, CoronaPokerGdxTable.introLogoDockProgress(clearEnd));
        assertTrue(clearEnd < originalDockStart);
        assertEquals(0f,
                CoronaPokerGdxTable.introLogoDockProgress(originalDockStart));
        assertTrue(CoronaPokerGdxTable.introLogoDockProgress(
                originalDockStart + 0.10f) > 0f);
    }

    @Test
    void logoKeepsBeatingUntilMovementBeginsWithoutAScaleJump() {
        float pulseStart = 2.28f;
        float dockStart = CoronaPokerGdxTable
                .STARTUP_PRESENTATION_END_SECONDS + 1.42f;
        float pulseDuration = dockStart - pulseStart;
        float lastPeak = pulseStart + pulseDuration * (5.25f / 6f);

        assertEquals(0f, CoronaPokerGdxTable.introLogoPulse(pulseStart));
        assertTrue(CoronaPokerGdxTable.introLogoPulse(lastPeak) > 0.99f,
                "the uncovered full-size logo must still produce a final beat");
        assertEquals(0f, CoronaPokerGdxTable.introLogoDockProgress(lastPeak));
        assertEquals(0f, CoronaPokerGdxTable.introLogoPulse(dockStart),
                "movement starts at the neutral scale, without a size snap");
    }
}
