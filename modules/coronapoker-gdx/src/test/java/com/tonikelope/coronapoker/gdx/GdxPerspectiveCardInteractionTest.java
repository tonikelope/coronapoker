/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxPerspectiveCardInteractionTest {
    @Test void aDelayedFirstFrameStillPresentsTheCardAtItsDealerOrigin() {
        var clock = new GdxCardDealMotion.FrameClock();
        assertEquals(0f, clock.elapsedSeconds());
        clock.advance(9_000_000_000L);
        assertEquals(0f, clock.elapsedSeconds());
        clock.advance(9_016_000_000L);
        assertEquals(0.016f, clock.elapsedSeconds(), 0.00001f);
        clock.advance(9_010_000_000L);
        assertEquals(0.016f, clock.elapsedSeconds(), 0.00001f);
    }

    @Test void eitherDealerOriginReachesTheExactRestingHeightOfEachSlot() {
        var result = new Vector3();
        for (Vector3 source : new Vector3[]{new Vector3(-500, 16, -700), new Vector3(0, 16, 650)}) {
            for (int slot = 0; slot < 2; slot++) {
                var target = new Vector3(slot == 0 ? -65 : 65,
                        GdxPerspectiveTable.pocketLayerHeight(slot, 150), 410);
                GdxCardDealMotion.sample(result, source, target, 0, 150);
                assertTrue(source.epsilonEquals(result, 0.0001f));
                GdxCardDealMotion.sample(result, source, target, 1, 150);
                assertTrue(target.epsilonEquals(result, 0.0001f));
                for (int i = 0; i <= 100; i++) {
                    GdxCardDealMotion.sample(result, source, target, i / 100f, 150);
                    assertTrue(result.y >= target.y - 0.0001f);
                }
            }
        }
        assertTrue(GdxPerspectiveTable.pocketLayerHeight(1, 150) > GdxPlasticCard.thickness(150));
    }

    @Test void touchdownPrecedesTheLastSlideAndHasNoVerticalBounce() {
        var source = new Vector3(-200, 15, -500);
        var target = new Vector3(60, 0.82f, 400);
        var point = new Vector3();
        float lastDistance = Float.POSITIVE_INFINITY;
        for (int i = 68; i <= 100; i++) {
            GdxCardDealMotion.sample(point, source, target, i / 100f, 150);
            assertEquals(target.y, point.y, 0.0001f);
            assertTrue(point.dst(target) <= lastDistance);
            lastDistance = point.dst(target);
        }
        GdxCardDealMotion.sample(point, source, target, GdxCardDealMotion.TOUCHDOWN, 150);
        assertTrue(point.dst(target) > 10f, "The sheet teleports directly to its final slot on contact");
        var before = new Vector3();
        GdxCardDealMotion.sample(before, source, target, GdxCardDealMotion.TOUCHDOWN - 0.0001f, 150);
        assertTrue(before.dst(point) < 0.1f);
    }

    @Test void theLowThrowNeverPushesThePlasticThroughTheFelt() {
        var from = new Vector3(-300f,8f,-600f); var to = new Vector3(0f,0f,300f);
        var center = new Vector3(); var vertex = new Vector3();
        for (int frame=0;frame<=200;frame++) {
            float p=frame/200f;
            GdxCardDealMotion.sample(center,from,to,p,150f);
            assertTrue(center.y < 22f, "The card flies too high for a slide onto the table");
            for (int row=0;row<=20;row++) {
                GdxPlasticCard.flight(vertex,150f,210f,.5f,row/20f,GdxCardDealMotion.airborne(p));
                assertTrue(center.y + vertex.y >= -.0001f, "The flexible sheet crosses the contact plane");
            }
        }
    }

    @Test void pickingFindsTheFrontTriangleFromEitherSideAndRejectsEmptySpace() {
        var near = new GdxCardPickMesh();
        var far = new GdxCardPickMesh();
        near.append(triangle(3f));
        far.append(triangle(0f));
        Vector3 origin = new Vector3(0.2f, 5f, 0.2f), direction = new Vector3(0,-1,0);
        assertEquals(2f, near.distance(origin, direction), 0.0001f);
        assertTrue(near.distance(origin, direction) < far.distance(origin, direction));
        assertEquals(3f, near.distance(new Vector3(0.2f,0f,0.2f), new Vector3(0,1,0)), 0.0001f);
        assertFalse(Float.isFinite(near.distance(new Vector3(0.9f,5f,0.9f), direction)));
        assertFalse(Float.isFinite(near.distance(origin,new Vector3(1,0,0))));
    }

    private static FloatArray triangle(float y) {
        var vertices = new FloatArray();
        for (float[] p : new float[][]{{0,0},{1,0},{0,1}}) {
            vertices.add(p[0],y,p[1],0);
            vertices.add(0,1,0,1);
            vertices.add(0);
        }
        return vertices;
    }
}
