/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxPlasticCardTest {
    @Test void theWholeCardStartsAndFinishesFlatAtItsOriginalFootprint() {
        Vector3 point = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (float progress : new float[]{0f, 1f}) {
                for (int row = 0; row <= 16; row++) {
                    for (int column = 0; column <= 28; column++) {
                        float u = column / 28f;
                        float v = row / 16f;
                        GdxPlasticCard.reveal(point, 100f, 140f, u, v, progress, direction);
                        assertEquals((u - 0.5f) * 100f * (progress == 0f ? 1 : -1), point.x, 0.0001f);
                        assertEquals(0f, point.y, 0.0001f);
                        assertEquals((v - 0.5f) * 140f, point.z, 0.0001f);
                    }
                }
            }
        }
    }

    @Test void flexPreservesThePlasticWidthAndNeverPassesThroughTheFelt() {
        Vector3 point = new Vector3();
        Vector3 previous = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (int frame = 1; frame < 100; frame++) {
                for (float v : new float[]{0f, 0.5f, 1f}) {
                    float length = 0f;
                    float minHeight = Float.POSITIVE_INFINITY;
                    for (int column = 0; column <= 100; column++) {
                        GdxPlasticCard.reveal(point, 100f, 140f, column / 100f,
                                v, frame / 100f, direction);
                        assertTrue(Float.isFinite(point.x) && Float.isFinite(point.y));
                        assertTrue(point.y >= -0.0001f, "Card penetrates the felt");
                        if (column > 0) length += point.dst(previous);
                        previous.set(point);
                        minHeight = Math.min(minHeight, point.y);
                    }
                    assertEquals(100f, length, 0.002f, "Plastic stretches during the turn");
                    assertEquals(0f, minHeight, 0.002f, "No part of the card remains supported");
                }
            }
        }
    }

    @Test void theTurningSurfaceActuallyBowsAndItsNearCornerLeads() {
        Vector3 left = new Vector3();
        Vector3 middle = new Vector3();
        Vector3 right = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            GdxPlasticCard.reveal(left, 100f, 140f, 0f, 0.5f, 0.35f, direction);
            GdxPlasticCard.reveal(right, 100f, 140f, 1f, 0.5f, 0.35f, direction);
            GdxPlasticCard.reveal(middle, 100f, 140f, 0.5f, 0.5f, 0.35f, direction);
            assertTrue(middle.dst(new Vector3(left).lerp(right, 0.5f)) > 6f);
            GdxPlasticCard.reveal(left, 100f, 140f, 0.5f, 0f, 0.25f, direction);
            GdxPlasticCard.reveal(right, 100f, 140f, 0.5f, 1f, 0.25f, direction);
            assertTrue(Math.abs(left.y - right.y) > 5f, "All corners move as one rigid panel");
        }
    }

    @Test void airborneFlexDoesNotStretchTheCardOrSnapOnLanding() {
        Vector3 current = new Vector3();
        Vector3 previous = new Vector3();
        for (float progress : new float[]{0f, 0.0001f, 0.25f, 0.5f, 0.75f, 0.9999f, 1f}) {
            float length = 0f;
            for (int step = 0; step <= 100; step++) {
                GdxPlasticCard.flight(current, 100f, 140f, 0.5f, step / 100f, progress);
                if (step > 0) length += current.dst(previous);
                previous.set(current);
            }
            assertEquals(140f, length, 0.002f);
        }
        GdxPlasticCard.flight(current, 100f, 140f, 1f, 1f, 0.9999f);
        GdxPlasticCard.flight(previous, 100f, 140f, 1f, 1f, 1f);
        assertTrue(current.dst(previous) < 0.01f);
        assertEquals(0.48f, GdxPlasticCard.thickness(100f), 0.0001f);
    }

    @Test void theFinalHandoffDoesNotPopOrBounce() {
        Vector3 previous = new Vector3();
        Vector3 settled = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (float u : new float[]{0f, 0.5f, 1f}) {
                for (float v : new float[]{0f, 1f}) {
                    GdxPlasticCard.reveal(previous, 100f, 140f, u, v, 0.9999f, direction);
                    GdxPlasticCard.reveal(settled, 100f, 140f, u, v, 1f, direction);
                    assertTrue(previous.dst(settled) < 0.02f);
                }
            }
        }
    }
    @Test void cornerLiftPreservesBothDimensionsAndKeepsASupportedRegion() {
        var shape = new GdxPlasticCard.PeekShape();
        Vector3 point = new Vector3();
        Vector3 previous = new Vector3();
        for (float amount : new float[]{0f, 0.001f, 0.1f, 0.3f, 0.6f, 1f}) {
            shape.set(amount);
            int supported = 0;
            for (int row = 0; row <= 10; row++) {
                float length = 0f;
                for (int column = 0; column <= 200; column++) {
                    shape.sample(point, 100f, 140f, column / 200f, row / 10f);
                    assertTrue(point.y >= -0.001f, "The sheet penetrates the table");
                    if (point.y < 0.001f) supported++;
                    if (column > 0) length += point.dst(previous);
                    previous.set(point);
                }
                assertEquals(100f, length, 0.05f, "The corner stretches across its width");
            }
            assertTrue(supported > 400, "The resting region has lifted off the table");
            for (int column = 0; column <= 10; column++) {
                float length = 0f;
                for (int row = 0; row <= 200; row++) {
                    shape.sample(point, 100f, 140f, column / 10f, row / 200f);
                    if (row > 0) length += point.dst(previous);
                    previous.set(point);
                }
                assertEquals(140f, length, 0.06f, "The corner stretches along its length");
            }
        }
    }

    @Test void thePeekUsesABroadBendRatherThanAPermanentCrease() {
        var shape = new GdxPlasticCard.PeekShape();
        shape.set(1f);
        var point = new Vector3(); var normal = new Vector3();
        var previous = new Vector3(); var previousNormal = new Vector3();
        float minRadius = Float.POSITIVE_INFINITY;
        for (int step=0; step<=1000; step++) {
            shape.sample(point, normal, 100f, 140f, .1f, step/1000f);
            if (step>0) {
                double angle = Math.atan2(new Vector3(normal).crs(previousNormal).len(), normal.dot(previousNormal));
                if (angle > .0005) minRadius = Math.min(minRadius, (float)(point.dst(previous)/angle));
            }
            previous.set(point); previousNormal.set(normal);
        }
        assertTrue(minRadius > 36f, "A tight crease has returned to the lifted sheet");
    }

    @Test void privateIndexesFaceTheViewerWithoutExposingTheWholeCard() {
        var shape = new GdxPlasticCard.PeekShape();
        shape.set(1f);
        Vector3 at = new Vector3(), du = new Vector3(), dv = new Vector3();
        Vector3 eye = new Vector3(0f, 0.602f, 0.799f);
        for (float v : new float[]{0.72f, 0.90f}) {
            shape.sample(at, 100f, 140f, 0.10f, v);
            shape.sample(du, 100f, 140f, 0.101f, v);
            shape.sample(dv, 100f, 140f, 0.10f, v + 0.001f);
            du.sub(at);
            dv.sub(at).crs(du).nor();
            assertTrue(dv.dot(eye) < -0.60f, "The rank or suit is edge-on to the viewer");
        }
        shape.sample(at, 100f, 140f, 0.5f, 0.1f);
        assertEquals(0f, at.y, 0.001f);
        shape.set(0f);
        shape.sample(at, 100f, 140f, 0.1f, 0.9f);
        assertEquals(new Vector3(-40f, 0f, 56f).x, at.x, 0.001f);
        assertEquals(0f, at.y, 0.001f);
        assertEquals(56f, at.z, 0.001f);
    }

    @Test void variedRevealsAreRepeatableAndStaySupportedUntilTheSameEndpoint() {
        Vector3 point = new Vector3(), repeat = new Vector3(), previous = new Vector3();
        assertNotEquals(GdxPlasticCard.motion(11), GdxPlasticCard.motion(12));
        for (long seed = 0; seed < 32; seed++) {
            var motion = GdxPlasticCard.motion(seed);
            assertEquals(motion, GdxPlasticCard.motion(seed));
            for (int frame = 0; frame <= 20; frame++) {
                float progress = frame / 20f;
                float minHeight = Float.POSITIVE_INFINITY;
                float length = 0f;
                for (int column = 0; column <= 100; column++) {
                    float u = column / 100f;
                    GdxPlasticCard.reveal(point, 100f, 140f, u, 0.5f, progress, 1f, motion);
                    GdxPlasticCard.reveal(repeat, 100f, 140f, u, 0.5f, progress, 1f, motion);
                    assertEquals(point, repeat);
                    assertTrue(point.y >= -0.001f);
                    minHeight = Math.min(minHeight, point.y);
                    if (column > 0) length += point.dst(previous);
                    previous.set(point);
                    if (frame == 0 || frame == 20) {
                        assertEquals(0f, point.y, 0.001f);
                        assertEquals((u - 0.5f) * 100f * (frame == 0 ? 1f : -1f), point.x, 0.001f);
                    }
                }
                assertEquals(0f, minHeight, 0.01f);
                assertEquals(100f, length, 0.005f);
            }
        }
    }
    @Test void aPublicUncoverContinuesThePrivateBendAndFinishesFlat() {
        var shape = new GdxPlasticCard.UncoverShape();
        var peek = new GdxPlasticCard.PeekShape();
        var point = new Vector3(); var normal = new Vector3();
        var reference = new Vector3(); var previous = new Vector3();
        for (float initial : new float[]{0f, .35f, 1f}) {
            peek.set(initial); shape.set(initial, 0f);
            for (int row=0; row<=40; row++) {
                float v=row/40f;
                shape.sample(point,normal,100f,140f,.2f,v);
                peek.sample(reference,100f,140f,.2f,v);
                assertTrue(point.dst(reference) < .001f, "The public gesture must start from the same sheet");
            }
        }
        for(int frame=0; frame<=100; frame++) {
            float turn=frame/100f;
            float extent=.5f*(com.badlogic.gdx.math.MathUtils.cosDeg(7f*turn)
                    + 100f/140f*Math.abs(com.badlogic.gdx.math.MathUtils.sinDeg(7f*turn)));
            shape.set(1f,turn,extent);
            float length=0f;
            for(int row=0; row<=400; row++) {
                shape.sample(point,normal,100f,140f,.2f,row/400f);
                assertTrue(point.y >= -.001f, "The sheet must not penetrate its support");
                if(row>0) length+=point.dst(previous);
                previous.set(point);
            }
            assertEquals(140f,length,.06f,"Uncovering must preserve the card's length");
        }
        shape.set(1f,1f);
        shape.sample(point,normal,100f,140f,.2f,.9f);
        assertEquals(-30f,point.x,.001f);
        assertEquals(-56f,point.z,.001f);
        assertEquals(0f,point.y,.001f);
        assertEquals(-1f,normal.y,.001f);
    }

    @Test void bothSheetsRemainSeparatedByTheirMaterialThicknessDuringUncovering() {
        var shape=new GdxPlasticCard.UncoverShape();
        var point=new Vector3(); var normal=new Vector3();
        var upper=new Vector3(); var tangentPoint=new Vector3();
        float thickness=GdxPlasticCard.thickness(100f)+.10f;
        for(int frame=0;frame<=80;frame++) {
            shape.set(1f,frame/80f,.55f);
            for(int row=0;row<=80;row++) {
                float v=row/80f;
                shape.sample(point,normal,100f,140f,.5f,v);
                upper.set(point).mulAdd(normal,thickness);
                assertEquals(thickness,point.dst(upper),.001f);
                // A neighbouring part of the opposite sheet may never cross
                // the thickness gap, even where the surface curves backwards.
                for(int other=Math.max(0,row-4);other<=Math.min(80,row+4);other++) {
                    shape.sample(tangentPoint,new Vector3(),100f,140f,.5f,other/80f);
                    assertTrue(tangentPoint.dst(upper) >= thickness*.98f);
                }
            }
        }
    }

    @Test void liftingKeepsTheSupportedPartAtItsActualMaterialCoordinates() {
        var shape = new GdxPlasticCard.PeekShape();
        var point = new Vector3(); var normal = new Vector3();
        for (int frame=0; frame<=80; frame++) {
            shape.set(frame/80f);
            for (float u : new float[]{0f,.25f,.5f,.75f,1f}) {
                for (float v : new float[]{0f,.05f,.1f,.199f}) {
                    shape.sample(point,normal,100f,140f,u,v);
                    assertEquals((u-.5f)*100f,point.x,.001f);
                    assertEquals((v-.5f)*140f,point.z,.001f);
                    assertEquals(0f,point.y,.001f);
                    assertEquals(1f,normal.y,.001f);
                }
            }
        }
    }

    @Test void aCompactFanKeepsBothCardsUnstretchedAcrossTheContactLine() {
        var shape = new GdxPlasticCard.PeekShape();
        var point = new Vector3(); var previous = new Vector3();
        float w=100f,h=140f;
        for (int frame=0;frame<=40;frame++) {
            shape.set(frame/40f);
            for (float degrees : new float[]{-3f,3f}) {
                double angle=Math.toRadians(degrees), c=Math.cos(angle), s=Math.sin(angle);
                float offset=degrees<0 ? 18f : -18f;
                for (boolean across : new boolean[]{true,false}) {
                    for (float fixed : new float[]{0f,.25f,.5f,.75f,1f}) {
                        float length=0f;
                        for(int i=0;i<=400;i++) {
                            float u=across?i/400f:fixed, v=across?fixed:i/400f;
                            double x=(u-.5)*w,z=(v-.5)*h;
                            shape.sample(point,w,h,(float)((offset+c*x-s*z)/w+.5),
                                    (float)((s*x+c*z)/h+.5));
                            assertTrue(point.y>=-.001f);
                            if(i>0)length+=point.dst(previous);
                            previous.set(point);
                        }
                        assertEquals(across?w:h,length,.02f,"The fan must not resize the plastic");
                    }
                }
            }
        }
    }

    @Test void pocketUncoverHasBoundedFrameTravelWithoutTheMidGestureStop() {
        var shape=new GdxPlasticCard.UncoverShape();
        var point=new Vector3(); var normal=new Vector3();
        float duration=GdxPlasticCard.uncoverSeconds(.620f);
        int frames=(int)Math.ceil(duration*60);
        for(float initial:new float[]{0f,.5f,1f}) {
            Vector3[] previous=new Vector3[101];
            float maxStep=0f;
            for(int frame=0;frame<=frames;frame++) {
                float p=frame/(float)frames;
                float lift=GdxPlasticCard.uncoverLift(initial,p);
                float turn=GdxPlasticCard.uncoverTurn(initial,p);
                shape.set(lift,turn,.55f);
                for(int row=0;row<=100;row++) {
                    shape.sample(point,normal,100f,140f,.5f,row/100f);
                    assertTrue(point.y>=-.001f && point.y<140f*.67f,
                            "The packet jumps up as a nearly vertical panel");
                    point.z+=140f*.06f*lift*(1-turn);
                    if(previous[row]!=null) maxStep=Math.max(maxStep,point.dst(previous[row]));
                    else previous[row]=new Vector3();
                    previous[row].set(point);
                }
            }
            // Bound path velocity independently of the selected animation duration.
            // Faster playback legitimately travels farther per frame, but must not
            // introduce a discontinuity into the lift/roll transition.
            assertTrue(maxStep*frames<140f*3.64f,
                    "Discontinuous uncover path at 60 Hz: "+maxStep*frames);
        }
        assertTrue(GdxPlasticCard.uncoverTurn(0f,.60f)>0f);
        assertTrue(GdxPlasticCard.uncoverLift(0f,.60f)<1f,
                "Lifting and rolling must overlap instead of stopping between phases");
    }

    @Test void showdownTintPreservesPrintedArtwork() {
        assertEquals(0f,GdxPerspectiveTable.showdownTintAmount(false));
        assertEquals(.31f,GdxPerspectiveTable.showdownTintAmount(true));
    }
}
