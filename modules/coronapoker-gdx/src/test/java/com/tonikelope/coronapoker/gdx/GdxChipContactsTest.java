package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GdxChipContactsTest {
    private static final float D = 39f, H = GdxTableChipRack.thickness(D);

    @Test void isolatedThrowKeepsEveryPoseAndBothContacts() {
        var scene = new GdxChipContacts();
        var track = new GdxChipContacts.Track();
        var from = new GdxTableChipFlight.Contact(-100, 130, H, .5f);
        var to = new GdxTableChipFlight.Contact(210, -90, H, 1f);
        var pose = new GdxTableChipFlight.Pose();
        for (int frame = 0; frame <= 240; frame++) {
            float t = frame / 240f;
            scene.begin(D, frame * 4_166_667L);
            scene.standing(0, from.x(), from.y(), 0);
            scene.standing(1, to.x(), to.y(), 0);
            GdxTableChipFlight.sampleThrow(pose, from, to, t, D, List.of(), 0, 814L);
            float x = pose.x, y = pose.y, z = pose.height, flip = pose.tumbleAngle, yaw = pose.rotation;
            scene.place(pose, track, t, 0);
            assertEquals(x, pose.x);
            assertEquals(y, pose.y);
            assertEquals(z, pose.height, .00001f);
            assertEquals(flip, pose.tumbleAngle);
            assertEquals(yaw, pose.rotation);
        }
    }

    @Test void skippedFrameCannotSwapTwoFlatChipsOrCrossAStandingChip() {
        for (boolean standing : new boolean[]{false, true}) {
            var scene = new GdxChipContacts();
            var first = new GdxChipContacts.Track();
            var second = new GdxChipContacts.Track();
            for (int frame = 0; frame < 2; frame++) {
                scene.begin(D, 1_000_000_000L + frame * 66_666_667L);
                var a = flat(standing ? 0 : frame == 0 ? -45 : 45, 0, 0);
                var b = flat(frame == 0 ? 65 : -65, 0, 0);
                if (standing) scene.standing(0, a.x, a.y, 0);
                else scene.place(a, first, .2f + .1f * frame, -1);
                scene.place(b, second, .2f + .1f * frame, -1);
                assertTrue(b.x - a.x >= D - .001f, "The incoming face was crossed between frames");
            }
        }
    }

    @Test void convergingLocalAndRemoteBurstsKeepTheirSolidVolumesSeparate() {
        for (int fps : new int[]{24, 60, 240}) {
            for (int seed = 0; seed < 4; seed++) verifyBurst(fps, seed);
        }
    }

    private static void verifyBurst(int fps, int seed) {
        int n = 24;
        var scene = new GdxChipContacts();
        var tracks = new GdxChipContacts.Track[n];
        var poses = new GdxTableChipFlight.Pose[n];
        for (int i = 0; i < n; i++) {
            tracks[i] = new GdxChipContacts.Track();
            poses[i] = new GdxTableChipFlight.Pose();
        }
        var pile = new GdxTableChipFlight.Pile(0, 0, H * 10);
        for (int f = 1; f < fps; f++) {
            float t = f / (float) fps;
            scene.begin(D, 1_000_000_000L + f * (1_000_000_000L / fps));
            List<Solid> solids = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                var rest = flat(j % 2 * .3f, 0, j * H);
                scene.standing(j, rest.x, rest.y, rest.height);
                solids.add(solid(rest));
            }
            for (int i = 0; i < n; i++) {
                int seat = i % 3;
                var from = new GdxTableChipFlight.Contact((seat - 1) * 128,
                        seat == 1 ? -240 : 150, seat == 1 ? (i / 3) * H : 0, 0);
                var to = new GdxTableChipFlight.Contact((i % 2) * D * 1.15f, 0,
                        ((i % 2 == 0 ? 10 : 0) + i / 2) * H, 0);
                var pose = poses[i];
                GdxTableChipFlight.sampleParabola(pose, from, to, t, D, List.of(pile),
                        i % 5 - 2, GdxTableChipFlight.tossSeed(seed, i, i % 4));
                scene.place(pose, tracks[i], t, i % 2);
                Solid moving = solid(pose);
                assertTrue(moving.center.y + .0003f >= extent(moving.normal.y));
                for (Solid other : solids) {
                    if (!separated(moving, other)) fail("Overlap at " + fps + " Hz, seed " + seed + ", frame " + f
                            + ", chip " + i + ", moving=" + moving + ", other=" + other);
                }
                solids.add(moving);
            }
        }
    }

    static GdxTableChipFlight.Pose flat(float x, float y, float bottom) {
        var p = new GdxTableChipFlight.Pose();
        p.x = x;
        p.y = y;
        p.height = bottom;
        return p;
    }

    record Solid(Vector3 center, Vector3 normal, Quaternion rotation) {}

    // Independent geometric witness: a separating plane must exist for the
    // rendered pair. The collision response and its cached state are never queried.
    static Solid solid(GdxTableChipFlight.Pose p) {
        var q = p.tumbling ? new Quaternion().setFromAxisRad(p.axisX, 0, p.axisZ, p.tumbleAngle)
                : new Quaternion().setEulerAnglesRad(0, p.pitch, p.roll);
        Vector3 n = q.transform(Vector3.Y.cpy()).nor();
        float center = p.height + (p.tumbling ? H * .5f : (float) extent(n.y));
        return new Solid(new Vector3(p.x, center, -p.y), n, q);
    }

    private static double extent(double cosine) {
        double c = Math.min(1d, Math.abs(cosine));
        return D * .5d * Math.sqrt(1d - c * c) + H * .5d * c;
    }

    static boolean separated(Solid a, Solid b) {
        Vector3 delta = a.center.cpy().sub(b.center);
        if (delta.len2() >= D * D + H * H) return true;
        for (Vector3 axis : new Vector3[]{Vector3.Y, a.normal, b.normal, delta,
                new Vector3(delta.x, 0, delta.z), a.normal.cpy().crs(b.normal)}) {
            double norm = Math.sqrt((double) axis.x * axis.x + (double) axis.y * axis.y + (double) axis.z * axis.z);
            if (norm < .00001) continue;
            if (Math.abs(dot(delta, axis) / norm) + .0004d >= projected(a.normal, axis, norm)
                    + projected(b.normal, axis, norm)) return true;
        }
        // Test the face normals and edge cross-products of the two 48-sided
        // prisms, including the full rim at both ends of the bevel.
        Vector3[] ra = rim(a), rb = rim(b);
        Vector3[] ea = edges(ra, a.normal), eb = edges(rb, b.normal);
        Vector3 axis = new Vector3();
        for (Vector3 edge : ea) {
            if (meshGap(delta, axis.set(edge).crs(a.normal), a, ra, b, rb)) return true;
            for (Vector3 other : eb) {
                if (meshGap(delta, axis.set(edge).crs(other), a, ra, b, rb)) return true;
            }
        }
        for (Vector3 edge : eb) {
            if (meshGap(delta, axis.set(edge).crs(b.normal), a, ra, b, rb)) return true;
        }
        return false;
    }

    private static Vector3[] rim(Solid solid) {
        Vector3[] points = new Vector3[48];
        for (int i = 0; i < points.length; i++) {
            float angle = i * com.badlogic.gdx.math.MathUtils.PI2 / points.length;
            points[i] = solid.rotation.transform(new Vector3(com.badlogic.gdx.math.MathUtils.cos(angle),
                    0f, com.badlogic.gdx.math.MathUtils.sin(angle))).scl(D * .5f);
        }
        return points;
    }

    private static Vector3[] edges(Vector3[] rim, Vector3 normal) {
        Vector3[] edges = new Vector3[rim.length + 1];
        for (int i = 0; i < rim.length; i++) edges[i] = rim[i].cpy().sub(rim[(i + 1) % rim.length]);
        edges[rim.length] = normal;
        return edges;
    }

    private static boolean meshGap(Vector3 delta, Vector3 axis, Solid a, Vector3[] ra, Solid b, Vector3[] rb) {
        if (axis.len2() < .0000001f) return false;
        axis.nor();
        return Math.abs(dot(delta, axis)) + .0004d >= meshExtent(a, ra, axis) + meshExtent(b, rb, axis);
    }

    private static double meshExtent(Solid disc, Vector3[] rim, Vector3 axis) {
        double extent = 0d;
        for (Vector3 point : rim) extent = Math.max(extent, Math.abs(dot(point, axis)));
        return extent + H * .5d * Math.abs(dot(disc.normal, axis));
    }
    private static double projected(Vector3 normal, Vector3 axis, double norm) {
        return extent(dot(normal, axis) / (norm * Math.sqrt(dot(normal, normal))));
    }

    private static double dot(Vector3 a, Vector3 b) {
        return (double) a.x * b.x + (double) a.y * b.y + (double) a.z * b.z;
    }
}
