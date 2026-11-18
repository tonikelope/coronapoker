package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GdxPerspectivePotGeometryTest {
    private static final float DIAMETER = GdxTableChipRack.diameter(140f);

    @Test void tableChipsShareCasinoDimensionsRelativeToTheCards() {
        assertEquals(39f / 63f, DIAMETER / 140f, .00001f);
        assertEquals(3.3f / 63f, GdxTableChipRack.thickness(DIAMETER) / 140f, .00001f);
    }

    @Test void potPlacesSurroundTheLabelAndClearTheCardsAndEachOther() {
        float jitter = DIAMETER * CoronaPokerGdxTable.CHIP_CATALOG_STACK_JITTER_RATIO;
        assertEquals(-1, GdxTableChipSlots.side(0));
        assertEquals(0, GdxTableChipSlots.side(1));
        assertEquals(1, GdxTableChipSlots.side(2));
        for (int slot = 0; slot < GdxTableChipSlots.POT_SLOT_COUNT; slot++) {
            float x = GdxTableChipSlots.offsetX(slot, DIAMETER, 165f, 24f);
            float y = GdxTableChipSlots.offsetY(slot, DIAMETER, 45f, 24f);
            assertTrue(y - DIAMETER / 2f > -45f, "The pot crosses the front edge of its label");
            assertTrue(Math.abs(x) - DIAMETER / 2f - jitter > 165f || y - DIAMETER / 2f > 45f);
            for (int other = slot + 1; other < GdxTableChipSlots.POT_SLOT_COUNT; other++) {
                float dx = Math.max(0f, Math.abs(x - GdxTableChipSlots.offsetX(other, DIAMETER, 165f, 24f))
                        - 2f * jitter);
                float dy = y - GdxTableChipSlots.offsetY(other, DIAMETER, 45f, 24f);
                assertTrue(Math.hypot(dx, dy) > DIAMETER, "Adjacent piles intersect");
            }
        }
    }

    @Test void potUsesFixedDenominationPositionsOnBothSides() {
        var rack = GdxTableChipSlots.pot();
        rack.arrange(new int[]{1, 0, 0, 0});
        int blue = rack.slot(0, 0);
        assertEquals(1, GdxTableChipSlots.side(blue));
        rack.arrange(new int[]{1, 1, 1, 1});
        assertEquals(blue, rack.slot(0, 0));
        float previousX = Float.NEGATIVE_INFINITY;
        for (int column = 3; column >= 0; column--) {
            int slot = rack.slot(column, 0);
            float x = GdxTableChipSlots.offsetX(slot, DIAMETER, 165f, 24f);
            assertEquals(column >= 2 ? -1 : 1, GdxTableChipSlots.side(slot));
            assertEquals(GdxTableChipSlots.offsetY(blue, DIAMETER, 45f, 24f),
                    GdxTableChipSlots.offsetY(slot, DIAMETER, 45f, 24f));
            assertTrue(x > previousX);
            previousX = x;
        }
    }

    @Test void potUsesTheAuxiliaryAreaOnlyAfterBothSidesFill() {
        var rack = GdxTableChipSlots.pot();
        rack.arrange(new int[]{30, 10, 10, 10});
        int blue = rack.slot(0, 0);
        assertEquals(6, potLateralPiles(rack));
        rack.arrange(new int[]{40, 10, 10, 10});
        assertEquals(6, potLateralPiles(rack));
        assertEquals(GdxTableChipSlots.POT_UPPER_CENTER, rack.slot(0, 3));

        rack.arrange(new int[]{40, 10, 0, 10});
        assertEquals(GdxTableChipSlots.POT_UPPER_CENTER, rack.slot(0, 3));
        rack.compactPotSides();
        assertEquals(6, potLateralPiles(rack));
        assertEquals(blue, rack.slot(0, 0));
        assertEquals(-1, GdxTableChipSlots.side(rack.slot(0, 3)));
        rack.arrange(new int[]{40, 10, 10, 10});
        assertEquals(0, rack.slot(2, 0), "Red reclaims its front position");
        assertEquals(6, potLateralPiles(rack));
        assertEquals(GdxTableChipSlots.POT_UPPER_CENTER, rack.slot(0, 3));
    }

    @Test void fullPotMakesRoomForANewDenominationAtItsOwnPosition() {
        var rack = GdxTableChipSlots.pot();
        rack.arrange(new int[]{90, 0, 0, 0});
        assertEquals(9, rack.piles(0));
        rack.arrange(new int[]{90, 0, 1, 0});
        assertEquals(0, rack.slot(2, 0));
        assertEquals(8, rack.piles(0));
        assertFalse(rack.ownsSlot(0, rack.slot(2, 0)));
        int chips = 0;
        for (int pile = 0; pile < rack.piles(0); pile++) chips += rack.height(0, pile, 90);
        assertEquals(90, chips);
        assertEquals(6, potLateralPiles(rack));
    }

    private static int potLateralPiles(GdxTableChipSlots rack) {
        int count = 0;
        for (int column = 0; column < 4; column++) {
            for (int pile = 0; pile < rack.piles(column); pile++) {
                if (GdxTableChipSlots.side(rack.slot(column, pile)) != 0) count++;
            }
        }
        return count;
    }

    @Test void useEveryPlaceBeforeIncreasingPileHeight() {
        var rack = GdxTableChipSlots.pot();
        rack.arrange(new int[]{1, 1, 1, 1});
        int blue = rack.slot(0, 0);
        rack.arrange(new int[]{60, 10, 10, 10});
        assertEquals(6, rack.piles(0));
        assertEquals(10, rack.height(0, 0, 60));
        assertEquals(blue, rack.slot(0, 0));
        assertEquals(0, rack.levelForChip(0, 10));
        assertEquals(1, rack.pileForChip(0, 10));
        rack.arrange(new int[]{61, 10, 10, 10});
        assertEquals(11, rack.height(0, 0, 61));
        assertEquals(10, rack.levelForChip(0, 60));
        assertEquals(0, rack.pileForChip(0, 60));
    }

    @Test void playerFillsTheFrontRowFromHighestToLowestAcrossBothSides() {
        var rack = GdxTableChipSlots.local();
        rack.arrange(new int[]{20, 20, 10, 10});
        rack.compactLocalRows();
        for (int column = 0; column < 4; column++) {
            int slot = rack.slot(column, 0);
            assertEquals(3 - column, slot);
            assertEquals(column >= 2, GdxTableChipRack.localLeft(slot));
            assertEquals(GdxTableChipRack.localOffsetY(0, DIAMETER, 1080f),
                    GdxTableChipRack.localOffsetY(slot, DIAMETER, 1080f));
            if (slot > 0) {
                assertTrue(GdxTableChipRack.localX(slot - 1, DIAMETER, 250f)
                        < GdxTableChipRack.localX(slot, DIAMETER, 250f));
            }
        }
        assertEquals(4, rack.slot(1, 1));
        assertEquals(5, rack.slot(0, 1));
        assertTrue(GdxTableChipRack.localOffsetY(4, DIAMETER, 1080f)
                > GdxTableChipRack.localOffsetY(3, DIAMETER, 1080f));
    }

    @Test void localPilesKeepTheirSlotsInFlightAndPackByValueWhenSettled() {
        var rack = GdxTableChipSlots.local();
        rack.arrange(new int[]{20, 0, 0, 20});
        assertEquals(0, rack.slot(3, 0));
        assertEquals(1, rack.slot(0, 0));
        assertEquals(2, rack.slot(3, 1));
        rack.compactLocalRows();
        assertEquals(0, rack.slot(3, 0));
        assertEquals(1, rack.slot(3, 1));
        assertEquals(2, rack.slot(0, 0));
        assertEquals(3, rack.slot(0, 1));

        rack.arrange(new int[]{20, 0, 10, 20});
        assertEquals(4, rack.slot(2, 0));
        assertEquals(3, rack.slot(0, 1));
        rack.compactLocalRows();
        assertEquals(0, rack.slot(3, 0));
        assertEquals(1, rack.slot(3, 1));
        assertEquals(2, rack.slot(2, 0));
        assertEquals(3, rack.slot(0, 0));
        assertEquals(4, rack.slot(0, 1));
    }

    @Test void bothLocalBanksKeepTheCommunityCardsVisibleFromTheGameCamera() {
        GdxNativesLoader.load();
        for (float height : new float[]{1080f, 1120f, 1200f, 1440f}) {
            float width = 1920f;
            float centerY = height * .52f;
            var camera = new GdxPerspectiveTable(4);
            float boardHeight = 140f * 1242f / 923f;
            camera.layout(width, height, centerY + boardHeight * .14f);
            float pocketY = height * CoronaPokerGdxTable.createSeatAnchors(9)[0][1] + 162f;
            float rackY = camera.layoutBoardAt(width / 2f, pocketY).y - 140f * .46f;
            float boardFront = camera.boardToScreen(width / 2f, centerY - boardHeight * .36f - 52f).y;
            float bank = CoronaPokerGdxTable.localChipBankOffset(140f);
            for (int slot = 0; slot < GdxTableChipSlots.LOCAL_SLOT_COUNT; slot++) {
                int chips = GdxTableChipRack.CHIPS_PER_PILE;
                float x = width / 2f + GdxTableChipRack.localX(slot, DIAMETER, bank);
                float y = rackY + GdxTableChipRack.localOffsetY(slot, DIAMETER, height);
                float reach = DIAMETER * (.50f + .048f * GdxTableChipRack.localDepthSway(height));
                float chipTop = camera.chipPointToScreen(x, y + reach,
                        chips * GdxTableChipRack.thickness(DIAMETER)).y;
                assertTrue(chipTop < boardFront - 3f, "height " + height + ", slot " + slot
                        + ", clearance " + (boardFront - chipTop));
                float bottom = camera.chipPointToScreen(x, y - DIAMETER * .52f, 0f).y;
                assertTrue(bottom > 141f, "The chip base must clear the HUD, height " + height + ", slot " + slot);
            }
        }
    }

    @Test void everyColourOnlyGrowsDuringTheStartingFillAndReachesFullInventory() {
        for (int count : new int[]{0, 1, 3, 9, 10, 11, 23, 51}) {
            int previous = 0;
            for (int step = 0; step <= 100; step++) {
                int visible = GdxTableChipRack.initialCount(count, 2, step / 100f, 213L);
                assertTrue(visible >= previous && visible <= count);
                previous = visible;
            }
            assertEquals(count, previous);
        }
    }

    @Test void fallingChipsAccelerateAndContactInStackOrder() {
        var to = new GdxTableChipFlight.Contact(500f, 270f, 32f, .3f);
        var pose = new GdxTableChipFlight.Pose();
        long seed = GdxTableChipFlight.tossSeed(120_519L, 8, 1);
        GdxTableChipFlight.sampleInitialDrop(pose, to, 0f, DIAMETER, .32f, seed);
        float height = pose.height;
        assertEquals(.5f * (9810f / 39f) * DIAMETER * .32f * .32f, height - to.height(), .001f);
        GdxTableChipFlight.sampleInitialDrop(pose, to, .5f, DIAMETER, .32f, seed);
        assertEquals((height - to.height()) * .25f, height - pose.height, .001f);
        GdxTableChipFlight.sampleInitialDrop(pose, to, 1f, DIAMETER, .32f, seed);
        assertEquals(to.height(), pose.height);
        assertEquals(to.x(), pose.x);
        assertEquals(to.y(), pose.y);
        assertEquals(to.rotation(), pose.rotation);
        assertEquals(0f, Math.abs(pose.pitch) + Math.abs(pose.roll));
        for (int index = 0; index < 35; index++) {
            float contact = GdxTableChipRack.initialLanding(35, 3, index, seed);
            assertEquals(index, GdxTableChipRack.initialCount(35, 3, Math.nextDown(contact), seed));
            assertEquals(index + 1, GdxTableChipRack.initialCount(35, 3, contact, seed));
        }
    }

    @Test void pileImpactsVaryAndAdditionalImpulsesPreservePosition() {
        var pile = new GdxTableChipRack.Impact();
        var other = new GdxTableChipRack.Impact();
        long start = 1_000_000_000L;
        pile.kick(start, GdxTableChipFlight.tossSeed(start, 0, 1));
        other.kick(start, GdxTableChipFlight.tossSeed(start, 1, 1));
        long nextFrame = start + 30_000_000;
        pile.advance(nextFrame);
        other.advance(nextFrame);
        float x = pile.offsetX(4, DIAMETER), y = pile.offsetY(4, DIAMETER);
        assertTrue(Math.hypot(x, y) > 1f);
        assertTrue(Math.hypot(x, y) / DIAMETER + CoronaPokerGdxTable.CHIP_CATALOG_STACK_JITTER_RATIO < .075f);
        assertNotEquals(pile.offsetX(4, DIAMETER), other.offsetX(4, DIAMETER));
        pile.kick(nextFrame, GdxTableChipFlight.tossSeed(nextFrame, 1, 1));
        assertEquals(x, pile.offsetX(4, DIAMETER));
        assertEquals(y, pile.offsetY(4, DIAMETER));
        for (int frame = 1; frame < 60; frame++) {
            pile.advance(nextFrame + frame * 16_666_667L);
        }
        assertEquals(0f, pile.offsetX(4, DIAMETER));
        assertEquals(0f, pile.offsetY(4, DIAMETER));
    }

    @Test void flightsMatchBothContactsAndKeepMovingThroughTheMiddle() {
        var from = new GdxTableChipFlight.Contact(100f, 200f, 38f, .3f);
        var to = new GdxTableChipFlight.Contact(330f, 780f, 14f, .1f);
        var pose = new GdxTableChipFlight.Pose();
        GdxTableChipFlight.sample(pose, from, to, 0f, DIAMETER, 74f, 1f, .65f);
        assertEquals(from.height(), pose.height);
        assertEquals(from.x(), pose.x);
        assertEquals(0f, pose.roll);
        GdxTableChipFlight.sample(pose, from, to, .5f, DIAMETER, 74f, 1f, .65f);
        assertTrue(pose.height > 74f && Math.abs(pose.roll) + Math.abs(pose.pitch) > .01f);
        float forward = GdxTableChipFlight.travel(.85f) - GdxTableChipFlight.travel(.15f);
        assertTrue(forward > .85f);
        GdxTableChipFlight.sample(pose, from, to, 1f, DIAMETER, 74f, 1f, .65f);
        assertEquals(to.y(), pose.y);
        assertEquals(to.height(), pose.height);
        assertEquals(to.rotation(), pose.rotation);
        assertEquals(0f, pose.pitch);
    }

    @Test void bothFeltZonesFitTheCommunityWidthAndLeaveThePocketSortLaneFree() {
        float cardWidth = 140f;
        float halfWidth = CoronaPokerGdxTable.communityBoardWidth(cardWidth) * .5f;
        float jitter = DIAMETER * CoronaPokerGdxTable.CHIP_CATALOG_STACK_JITTER_RATIO;
        float halfBank = GdxTableChipRack.halfFootprint(DIAMETER);
        float center = CoronaPokerGdxTable.localChipBankOffset(cardWidth);
        assertTrue(center + halfBank + jitter < halfWidth);
        assertTrue(center - halfBank - jitter > cardWidth * 1.32f);
        float outerPot = GdxTableChipSlots.offsetX(8, DIAMETER, 165f, 24f);
        assertTrue(outerPot + DIAMETER * .5f + jitter < halfWidth);
    }

    @Test void localBetAndPayoutTumbleOnAParabolaAndArriveFlat() {
        var low = new GdxTableChipFlight.Contact(100f, 260f, 14f, .3f);
        var high = new GdxTableChipFlight.Contact(450f, 810f, 38f, .1f);
        var pose = new GdxTableChipFlight.Pose();
        var obstacles = java.util.List.of(new GdxTableChipFlight.Pile(275f, 535f, 160f));
        long seed = GdxTableChipFlight.tossSeed(4_037_311L, 0, 2);
        for (int direction = 0; direction < 2; direction++) {
            var from = direction == 0 ? low : high;
            var to = direction == 0 ? high : low;
            GdxTableChipFlight.sampleParabola(pose, from, to, 0f, DIAMETER, obstacles, 0f, seed);
            assertEquals(from.height(), pose.height);
            assertEquals(from.x(), pose.x);
            assertEquals(0f, pose.tumbleAngle);

            GdxTableChipFlight.sampleParabola(pose, from, to, .25f, DIAMETER, obstacles, 0f, seed);
            assertEquals(from.x() + (to.x() - from.x()) * .25f, pose.x);
            assertEquals(from.y() + (to.y() - from.y()) * .25f, pose.y);
            float quarterLift = pose.height - (.75f * from.height() + .25f * to.height());
            assertTrue(Math.abs(pose.tumbleAngle) > .8f);
            GdxTableChipFlight.sampleParabola(pose, from, to, .5f, DIAMETER, obstacles, 0f, seed);
            float midpointLift = pose.height - (from.height() + to.height()) * .5f;
            assertEquals(.75f * midpointLift, quarterLift, .001f);
            assertEquals((float) Math.PI, Math.abs(pose.tumbleAngle), 1.1f);
            assertTrue(pose.height > 160f);
            GdxTableChipFlight.sampleParabola(pose, from, to, .999f, DIAMETER, obstacles, 0f, seed);
            assertEquals(to.x(), pose.x, .02f);
            assertEquals(to.y(), pose.y, .02f);
            GdxTableChipFlight.sampleParabola(pose, from, to, 1f, DIAMETER, obstacles, 0f, seed);
            assertEquals(to.y(), pose.y);
            assertEquals(to.height(), pose.height);
            assertEquals(to.rotation(), pose.rotation);
            assertEquals(0f, pose.tumbleAngle);
        }
    }

    @Test void localThrowsVaryBetweenActionsAndStayStableWithinAFlight() {
        var from = new GdxTableChipFlight.Contact(100f, 200f, 20f, 0f);
        var to = new GdxTableChipFlight.Contact(600f, 200f, 40f, .3f);
        var first = new GdxTableChipFlight.Pose();
        var repeated = new GdxTableChipFlight.Pose();
        var next = new GdxTableChipFlight.Pose();
        var piles = java.util.List.<GdxTableChipFlight.Pile>of();
        long firstSeed = GdxTableChipFlight.tossSeed(30_001L, 0, 2);
        long nextSeed = GdxTableChipFlight.tossSeed(50_107L, 0, 2);
        GdxTableChipFlight.sampleParabola(first, from, to, .5f, DIAMETER, piles, 0f, firstSeed);
        GdxTableChipFlight.sampleParabola(repeated, from, to, .5f, DIAMETER, piles, 0f, firstSeed);
        GdxTableChipFlight.sampleParabola(next, from, to, .5f, DIAMETER, piles, 0f, nextSeed);
        assertEquals(first.y, repeated.y);
        assertEquals(first.height, repeated.height);
        assertEquals(first.tumbleAngle, repeated.tumbleAngle);
        assertTrue(Math.abs(first.axisX - next.axisX) > .01f);
        assertTrue(Math.abs(first.height - next.height) > 1f);
        assertTrue(Math.abs(first.tumbleAngle - next.tumbleAngle) > .1f);
    }

    @Test void isolatedChipSoundsOnContactThenReboundsWithoutLeavingItsPile() {
        var from = new GdxTableChipFlight.Contact(100f, 200f, 20f, 0f);
        var to = new GdxTableChipFlight.Contact(600f, 400f, 40f, .3f);
        var pose = new GdxTableChipFlight.Pose();
        var piles = java.util.List.<GdxTableChipFlight.Pile>of();
        long seed = GdxTableChipFlight.tossSeed(30_001L, 0, 2);
        float touchdown = GdxTableChipFlight.TOUCHDOWN;
        assertFalse(CoronaPokerGdxTable.actionChipSoundDue(Math.nextDown(touchdown), true));
        assertTrue(CoronaPokerGdxTable.actionChipSoundDue(touchdown, true));
        assertFalse(CoronaPokerGdxTable.actionChipSoundDue(touchdown, false));

        for (float progress : new float[]{touchdown, (touchdown + 1f) * .5f, 1f}) {
            GdxTableChipFlight.sampleThrow(pose, from, to, progress, DIAMETER, piles, 0f, seed);
            assertEquals(to.x(), pose.x);
            assertEquals(to.y(), pose.y);
            assertEquals(to.rotation(), pose.rotation);
            assertFalse(pose.tumbling);
            if (progress == touchdown || progress == 1f) {
                assertEquals(to.height(), pose.height);
            } else {
                assertTrue(pose.height > to.height());
                assertTrue(pose.height < to.height() + GdxTableChipRack.thickness(DIAMETER));
            }
        }
    }

    @Test void feltMarkingsAreEnabledByDefaultAndOnlyEditableInThreeDimensions() {
        var option = new GdxSettingsContract.ToggleOption(GdxSettingsContract.TABLE_CHIP_MARKINGS_KEY,
                "Markings", true, GdxSettingsContract.Gate.NONE, false);
        var preferences = new Properties();
        assertTrue(option.fallback());
        assertTrue(GdxSettingsContract.enabled(option, preferences, true));
        preferences.setProperty(GdxSettingsContract.TABLE_VIEW_KEY, "2d");
        assertFalse(GdxSettingsContract.enabled(option, preferences, true));
    }
}
