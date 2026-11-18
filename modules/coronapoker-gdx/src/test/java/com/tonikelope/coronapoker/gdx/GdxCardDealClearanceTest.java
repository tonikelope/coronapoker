/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxCardDealClearanceTest {
    private static final float WIDTH = 1920f;
    private static final float HEIGHT = 1080f;
    private static final float BOARD_Y = HEIGHT * .52f;
    private static final float ASPECT = 88f / 63f;
    private static final float BOARD_CARD = 140f;
    private static final float CAMERA_CENTER = BOARD_Y + BOARD_CARD * ASPECT * .14f;
    private static final float POCKET_CARD = 184f;
    private static final float REST = .72f;
    private static final float DIAMETER = GdxTableChipRack.diameter(BOARD_CARD);
    private static final int[][] RACK_HEIGHTS = {
        {1, 5, 0, 0, 4, 0, 0, 0, 0},
        {10, 3, 8, 10, 4, 6, 10, 0, 0},
        {10, 10, 10, 10, 10, 10, 10, 10, 10},
        {16, 26, 14, 21, 35, 14, 21, 26, 16}
    };

    @Test
    void clearsBothRacksFromEveryDealerSeatAtEveryTableSize() {
        GdxNativesLoader.load();
        GdxPerspectiveTable table = new GdxPerspectiveTable(4);
        table.layout(WIDTH, HEIGHT, CAMERA_CENTER);
        for (int players = 2; players <= 10; players++) {
            float[][] anchors = CoronaPokerGdxTable.createSeatAnchors(players);
            float handScreenY = anchors[0][1] * HEIGHT + 102f + 60f;
            float handZ = CAMERA_CENTER - table.layoutBoardAt(WIDTH * .5f, handScreenY).y;
            for (int dealer = 0; dealer < players; dealer++) {
                float screenX = anchors[dealer][0] * WIDTH;
                float screenY = anchors[dealer][1] * HEIGHT;
                if (dealer != 0) {
                    screenX = Math.max(151f, Math.min(WIDTH - 151f, screenX)) - 91f;
                    screenY = CoronaPokerGdxTable.adjustedRivalSeatY(screenY, HEIGHT, ASPECT);
                }
                for (int layout = 0; layout < RACK_HEIGHTS.length; layout++) {
                    List<GdxCardDealMotion.Pile> piles = racks(handZ, layout);
                    for (int slot = 0; slot < 5; slot++) {
                        Vector3 target = new Vector3((slot - 2f) * (BOARD_CARD + 18f),
                                0f, 0f);
                        verify(table, screenX, screenY, target, BOARD_CARD, slot, 0f, piles,
                                players + "/" + dealer + " board " + slot + " layout " + layout);
                    }
                    for (int slot = 0; slot < 2; slot++) {
                        Vector3 target = new Vector3((slot == 0 ? .18f : -.18f) * POCKET_CARD,
                                GdxPerspectiveTable.pocketLayerHeight(slot, POCKET_CARD), handZ);
                        verify(table, screenX, screenY, target, POCKET_CARD, slot,
                                slot == 0 ? -3f : 3f, piles,
                                players + "/" + dealer + " pocket " + slot + " layout " + layout);
                    }
                }
            }
        }
    }

    @Test
    void avoidsTallAndLateObstaclesAndKeepsTheDirectMotionIfClear() {
        Vector3 from = new Vector3(-500f, 7.7f, 0f);
        Vector3 to = new Vector3(500f, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(-850f, 850f, -500f, 500f);
        var clear = List.of(new GdxCardDealMotion.Pile(0f, -350f, 45f, 500f));
        var unobstructed = GdxCardDealMotion.plan(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, clear, bounds);
        assertEquals(0f, unobstructed.lift());
        assertEquals(0f, unobstructed.sideways());
        assertEquals(GdxCardDealMotion.TOUCHDOWN, unobstructed.touchdown());

        var tall = new GdxCardDealMotion.Pile(0f, 0f, 45f, 275f);
        var late = new GdxCardDealMotion.Pile(378f, 75f, 44f, 32f);
        var piles = List.of(tall, late);
        var route = GdxCardDealMotion.plan(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, piles, bounds);
        assertNotEquals(0f, route.sideways(), "A tall column offers an open lane around it");
        checkSweep(from, to, BOARD_CARD, -18f, 0f, route, false, piles, "tall and late columns");
        var aligning = new Vector3();
        GdxCardDealMotion.sample(aligning, from, to, GdxCardDealMotion.TOUCHDOWN - .001f, BOARD_CARD, route);
        assertEquals(0f, aligning.z, .01f, "The detour should merge with a straight tangent");
        for (int step = 0; step <= 16; step++) {
            float p = GdxCardDealMotion.TOUCHDOWN + (1f - GdxCardDealMotion.TOUCHDOWN) * step / 16f;
            GdxCardDealMotion.sample(aligning, from, to, p, BOARD_CARD, route);
            assertEquals(0f, aligning.z, .001f, "The card should slide straight after contact");
        }

        var narrow = new GdxCardDealMotion.Bounds(-850f, 850f, -20f, 20f);
        var arc = GdxCardDealMotion.plan(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, piles, narrow);
        assertEquals(0f, arc.sideways());
        assertTrue(arc.lift() > 0f);
        checkSweep(from, to, BOARD_CARD, -18f, 0f, arc, false, piles, "late landing over tall columns");
        var nearTouchdown = new Vector3();
        GdxCardDealMotion.sample(nearTouchdown, from, to, arc.touchdown() - .001f, BOARD_CARD, arc);
        assertEquals(to.y, nearTouchdown.y, .01f, "Touchdown eases down to the felt");
    }

    @Test
    void communityReachesTheFeltBeforeItsSlotWithATangentAndAFlatSlide() {
        Vector3 from = new Vector3(-540f, 8f, -120f);
        Vector3 to = new Vector3(390f, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(-900f, 900f, -500f, 500f);
        var route = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, List.of(), List.of(), bounds);
        assertEquals(.82f, route.touchdown(), .001f);
        assertTrue(route.directCommunity());
        var before = new Vector3();
        var contact = new Vector3();
        var after = new Vector3();
        GdxCardDealMotion.sample(before, from, to, route.touchdown() - .001f, BOARD_CARD, route);
        GdxCardDealMotion.sample(contact, from, to, route.touchdown(), BOARD_CARD, route);
        GdxCardDealMotion.sample(after, from, to, route.touchdown() + .001f, BOARD_CARD, route);
        assertTrue(contact.dst(to) >= BOARD_CARD * .28f && contact.dst(to) < BOARD_CARD * .5f);
        assertEquals(0f, contact.y, .001f);
        assertEquals(0f, after.y, .001f);
        assertEquals(0f, GdxCardDealMotion.angle(-18f, 0f, route.touchdown(), route), .001f);
        assertTrue(contact.cpy().sub(before).epsilonEquals(after.cpy().sub(contact), .01f));
        float lastHeight = from.y;
        for (int step = 0; step <= 100; step++) {
            GdxCardDealMotion.sample(after, from, to, step / 100f, BOARD_CARD, route);
            float t = (after.x - from.x) / (to.x - from.x);
            assertEquals(from.z + (to.z - from.z) * t, after.z, .001f);
            assertTrue(after.y <= lastHeight, "An unobstructed card should descend throughout the throw");
            lastHeight = after.y;
        }
        assertTrue(after.epsilonEquals(to, .001f));
    }

    @Test
    void communityKeepsItsHeadingWhenThePreviousCardOnlyRequiresALaterTouchdown() {
        Vector3 from = new Vector3(-500f, 8f, 0f);
        Vector3 to = new Vector3(500f, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(-900f, 900f, -500f, 500f);
        var previous = new GdxCardDealMotion.BoardCard(342f, 0f, BOARD_CARD, BOARD_CARD * ASPECT);
        var route = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, List.of(), List.of(previous), bounds);
        var pose = new Vector3();
        assertTrue(route.directCommunity());
        assertTrue(route.touchdown() > .82f);
        for (int step = 0; step <= 1000; step++) {
            float progress = step / 1000f;
            GdxCardDealMotion.sample(pose, from, to, progress, BOARD_CARD, route);
            assertEquals(0f, pose.z, .001f);
            if (progress >= route.touchdown()) {
                assertTrue(pose.x - previous.x() >= BOARD_CARD);
                assertEquals(0f, pose.y, .001f);
            }
        }
    }

    @Test
    void communityDescendsWhileAdvancingImmediatelyAfterClearingAnOrdinaryPile() {
        Vector3 from = new Vector3(-500f, 8f, 0f);
        Vector3 to = new Vector3(500f, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(-900f, 900f, -500f, 500f);
        var piles = List.of(new GdxCardDealMotion.Pile(0f, 0f, 35f, 36f));
        var route = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, piles, List.of(), bounds);
        assertTrue(route.directCommunity());
        assertTrue(route.lift() > 0f && route.descendFrom() < .66f);
        var before = new Vector3();
        var after = new Vector3();
        GdxCardDealMotion.sample(before, from, to, route.descendFrom(), BOARD_CARD, route);
        GdxCardDealMotion.sample(after, from, to, (route.descendFrom() + route.touchdown()) / 2f,
                BOARD_CARD, route);
        assertTrue(after.y < before.y && after.x > before.x);
        checkSweep(from, to, BOARD_CARD, -18f, 0f, route, true, piles, "ordinary pile in the direct path");
    }

    @Test
    void communityLandingReservesSeveralRenderedFramesForTheSameCheckedTrajectory() {
        var route = new GdxCardDealMotion.Route(0f, 72f, .35f, .88f, .975f, true);
        for (float duration : new float[]{.26f, .52f}) {
            float start = 0f, contact = 0f, previous = 0f;
            for (int frame = 1; frame <= 1000; frame++) {
                float time = frame / 1000f;
                float progress = GdxCardDealMotion.communityProgress(time, duration, BOARD_CARD, route);
                assertTrue(progress >= previous && progress <= 1f);
                if (previous < route.descendFrom() && progress >= route.descendFrom()) start = time;
                if (previous < route.touchdown() && progress >= route.touchdown()) contact = time;
                previous = progress;
            }
            assertEquals(1f, previous);
            assertTrue((contact - start) * duration >= .085f,
                    "The descent should get at least five frames at 60 FPS");
        }
    }

    @Test
    void lowCommunityDetourSettlesDuringTheApproachFromARemoteDealer() {
        // A throw from the left side crosses the pot and the four cards already dealt.
        float gap = BOARD_CARD + 18f;
        var from = new Vector3(-907f, 7.7f, -233f);
        var to = new Vector3(2f * gap, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(from.x, 890f, -500f, 500f);
        var cards = List.of(boardCard(-2f * gap), boardCard(-gap), boardCard(0f), boardCard(gap));
        var piles = List.of(new GdxCardDealMotion.Pile(-45f, -163f, 29f, 67f),
                new GdxCardDealMotion.Pile(25f, -167f, 29f, 47f));
        var route = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, piles, cards, bounds);
        assertFalse(route.directCommunity());
        assertTrue(route.lift() > 0f && route.lift() < BOARD_CARD * .06f);
        checkSweep(from, to, BOARD_CARD, -18f, 0f, route, true, piles, "low remote approach");

        for (float seconds : new float[]{.26f, .52f}) {
            var pose = new Vector3();
            float nearEnd = GdxCardDealMotion.communityProgress(1f - .025f / seconds, seconds, BOARD_CARD, route);
            GdxCardDealMotion.sample(pose, from, to, nearEnd, BOARD_CARD, route, true);
            assertTrue(pose.y < route.lift() * .10f, "The last frames should already be near the felt");
            assertTrue(pose.x < to.x, "The card should still be advancing as it flattens");
            GdxCardDealMotion.sample(pose, from, to, 1f, BOARD_CARD, route, true);
            assertTrue(pose.epsilonEquals(to, .001f));
        }
    }

    private static GdxCardDealMotion.BoardCard boardCard(float x) {
        return new GdxCardDealMotion.BoardCard(x, 0f, BOARD_CARD, BOARD_CARD * ASPECT);
    }

    @Test
    void communityUsesAerialClearanceWhenTheDirectDescentIsBlockedByTallPiles() {
        Vector3 from = new Vector3(-500f, 8f, 0f);
        Vector3 to = new Vector3(500f, 0f, 0f);
        var bounds = new GdxCardDealMotion.Bounds(-900f, 900f, -500f, 500f);
        var piles = List.of(new GdxCardDealMotion.Pile(385f, 0f, 25f, 145f),
                new GdxCardDealMotion.Pile(500f, 120f, 16f, 145f),
                new GdxCardDealMotion.Pile(500f, -120f, 16f, 145f));
        var route = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, piles, List.of(), bounds);
        assertFalse(route.directCommunity());
        assertTrue(route.lift() > BOARD_CARD * .7f);
        checkSweep(from, to, BOARD_CARD, -18f, 0f, route, true, piles, "tall piles across the direct route");

        var tallColumns = List.of(new GdxCardDealMotion.Pile(0f, 0f, 45f, 275f),
                new GdxCardDealMotion.Pile(378f, 75f, 44f, 32f));
        var detour = GdxCardDealMotion.planCommunity(from, to, BOARD_CARD, BOARD_CARD * ASPECT,
                -18f, 0f, REST, tallColumns, List.of(), bounds);
        assertFalse(detour.directCommunity());
        assertNotEquals(0f, detour.sideways(), "A tall column with space beside it should permit an air detour");
        checkSweep(from, to, BOARD_CARD, -18f, 0f, detour, true, tallColumns, "aerial detour around tall columns");
    }

    private static void verify(GdxPerspectiveTable table, float x, float y, Vector3 target,
            float width, int slot, float restAngle, List<GdxCardDealMotion.Pile> piles, String caseName) {
        Vector3 from = new Vector3();
        table.dealOrigin(from, x, y, width);
        float startAngle = (slot & 1) == 0 ? -18f : 18f;
        float rear = CAMERA_CENTER - table.boardAt(WIDTH * .5f, HEIGHT * .89f).y + width * ASPECT * .5f;
        float front = CAMERA_CENTER - table.boardAt(WIDTH * .5f, HEIGHT * .10f).y - width * ASPECT * .5f;
        var bounds = new GdxCardDealMotion.Bounds(
                Math.min(from.x, -WIDTH * .5f + width * .5f),
                Math.max(from.x, WIDTH * .5f - width * .5f),
                Math.min(rear, from.z), Math.max(front, from.z));
        var route = width == BOARD_CARD
                ? GdxCardDealMotion.planCommunity(from, target, width, width * ASPECT,
                        startAngle, restAngle, REST, piles, List.of(), bounds)
                : GdxCardDealMotion.plan(from, target, width, width * ASPECT,
                        startAngle, restAngle, REST, piles, bounds);
        checkSweep(from, target, width, startAngle, restAngle, route, width == BOARD_CARD, piles, caseName);
    }

    private static void checkSweep(Vector3 from, Vector3 to, float width, float startAngle,
            float endAngle, GdxCardDealMotion.Route route, boolean community, List<GdxCardDealMotion.Pile> piles,
            String caseName) {
        Vector3 position = new Vector3();
        for (int frame = 0; frame <= 1_000; frame++) {
            float progress = frame / 1000f;
            GdxCardDealMotion.sample(position, from, to, progress, width, route, community);
            assertTrue(Float.isFinite(position.y), caseName);
            double radians = Math.toRadians(GdxCardDealMotion.angle(startAngle, endAngle, progress, route));
            double c = Math.cos(radians), s = Math.sin(radians);
            for (var pile : piles) {
                if (position.y + REST >= pile.top() - .01f) continue;
                float x = pile.x() - position.x, z = pile.z() - position.z;
                double u = Math.max(0d, Math.abs(x * c + z * s) - width * .5f);
                double v = Math.max(0d, Math.abs(z * c - x * s) - width * ASPECT * .5f);
                if (Math.hypot(u, v) < pile.radius()) {
                    fail(caseName + " intersects " + pile + " at " + progress + " " + position + " " + route);
                }
            }
        }
        assertTrue(position.epsilonEquals(to, .001f), caseName);
    }

    private static List<GdxCardDealMotion.Pile> racks(float handZ, int layout) {
        var piles = new ArrayList<GdxCardDealMotion.Pile>();
        float potZ = -BOARD_CARD * ASPECT * .50f - 69f;
        int[] counts = RACK_HEIGHTS[layout];
        for (int slot = 0; slot < counts.length; slot++) {
            addPile(piles, GdxTableChipSlots.offsetX(slot, DIAMETER, 165f, 24f),
                    potZ - GdxTableChipSlots.offsetY(slot, DIAMETER, 45f, 24f), counts[slot]);
        }
        float bank = CoronaPokerGdxTable.localChipBankOffset(BOARD_CARD);
        for (int slot = 0; slot < GdxTableChipSlots.LOCAL_SLOT_COUNT; slot++) {
            float x = GdxTableChipRack.localX(slot, DIAMETER, bank);
            float z = handZ + BOARD_CARD * .46f - GdxTableChipRack.localOffsetY(slot, DIAMETER, HEIGHT);
            int count = GdxTableChipRack.localLeft(slot) || layout >= 2 ? counts[(slot + 1) % counts.length] : 0;
            addPile(piles, x, z, count);
        }
        return piles;
    }

    private static void addPile(List<GdxCardDealMotion.Pile> piles, float x, float z, int count) {
        if (count > 0) {
            piles.add(new GdxCardDealMotion.Pile(x, z, .59f * DIAMETER,
                    count * GdxTableChipRack.thickness(DIAMETER) + .1f));
        }
    }
}
