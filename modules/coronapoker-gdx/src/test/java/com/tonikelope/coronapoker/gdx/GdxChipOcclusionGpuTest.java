package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

@Tag("certification")
class GdxChipOcclusionGpuTest {
    private static final int W = 640, H = 400;
    private static final float D = 39f, THICKNESS = GdxTableChipRack.thickness(D);

    @Test void stackedAndFlyingChipsOnTheProductionRenderer(@TempDir Path dir) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(W, H);
        var error = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
                // In create() the backend has not advanced its frame clock, so
                // the table cannot move its camera into the selected 3D view.
                if (Gdx.graphics.getDeltaTime() <= 0f) return;
                try {
                    long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
                    assertEquals(GLFW.GLFW_FALSE, GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    for (int samples : new int[]{0, 4}) verify(samples);
                    verifyAllIns(dir);
                } catch (Throwable t) { error.set(t); }
                finally { Gdx.app.exit(); }
            }
        }, config);
        if (error.get() != null) throw new AssertionError(error.get());
    }

    private static void verifyAllIns(Path dir) throws Exception {
        var prefs = new PreferencesService(dir.resolve("table.properties"));
        for (String key : List.of("musica", "sonido_efectos", "sonido_ascensor")) {
            prefs.properties().setProperty(key, "false");
        }
        prefs.properties().setProperty("gdx_table_view", "3d");
        var players = List.of(player("local", TableSnapshot.Position.DEALER),
                player("left", TableSnapshot.Position.SMALL_BLIND), player("right", TableSnapshot.Position.BIG_BLIND));
        var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP, 0d, "", false,
                players, List.of());
        var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> {}, () -> {}, new GdxGameLogSink(), prefs);
        try {
            table.create();
            table.resize(W, H);
            table.render();
            assertEquals(true, field(table, "perspectiveActive"));
            long seq = 1;
            for (var player : players) {
                var barrier = new CompletableFuture<Void>();
                table.acceptEvent(new TableVisualEvent.PlayerAction(++seq, player.nickname(),
                        TableVisualEvent.PlayerAction.ActionKind.ALL_IN, "ALL-IN", 2.37, 2.37, 0, 2.37, 2.37), barrier);
                assertTrue(barrier.isDone());
            }
            var owners = new IdentityHashMap<Object, String>();
            var actions = (List<?>) field(table, "liveActionChips");
            assertEquals(3, actions.size());
            for (Object action : actions) {
                String name = ((TableVisualEvent.PlayerAction) field(action, "event")).nickname();
                for (Object chip : (List<?>) field(action, "chips")) owners.put(field(chip, "track"), name);
            }
            var seen = new HashSet<String>();
            int peak = 0;
            long deadline = System.nanoTime() + 15_000_000_000L;
            do {
                table.render();
                int count = (int) field(table, "activeTableChipMoves");
                float scale = D / (float) field(table, "tableChipDiameter");
                var moves = (List<?>) field(table, "tableChipMoves");
                var solids = new ArrayList<GdxChipContactsTest.Solid>();
                for (int i = 0; i < count; i++) {
                    Object move = moves.get(i);
                    if (field(move, "from") == null) continue;
                    var pose = (GdxTableChipFlight.Pose) field(move, "pose");
                    var scaled = GdxChipContactsTest.flat(pose.x * scale, pose.y * scale, pose.height * scale);
                    scaled.tumbling = pose.tumbling;
                    scaled.tumbleAngle = pose.tumbleAngle;
                    scaled.axisX = pose.axisX;
                    scaled.axisZ = pose.axisZ;
                    scaled.pitch = pose.pitch;
                    scaled.roll = pose.roll;
                    var current = GdxChipContactsTest.solid(scaled);
                    for (var other : solids) assertTrue(GdxChipContactsTest.separated(current, other));
                    solids.add(current);
                    seen.add(owners.get(field(move, "track")));
                }
                peak = Math.max(peak, solids.size());
            } while (!actions.isEmpty() && System.nanoTime() < deadline);
            assertTrue(actions.isEmpty(), "All three flights must finish");
            assertEquals(java.util.Set.of("local", "left", "right"), seen);
            assertTrue(peak >= 3, "All-in should exercise concurrent airborne meshes");
            var inventory = (GdxPotChipInventory) field(table, "livePotChips");
            assertEquals(711L, CoronaPokerGdxTable.visualChipAmountMinor(inventory.snapshot()));
            System.out.println("Three all-ins settled, peak concurrent flying chips: " + peak);
        } finally { table.dispose(); }
    }

    private static TableSnapshot.PlayerSnapshot player(String name, TableSnapshot.Position position) {
        return new TableSnapshot.PlayerSnapshot(name, 2.37, 0d, 0d,
                true, false, false, false, -2, -2, 0, 0L, false, position, "", "", List.of());
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void verify(int samples) {
        var renderer = new GdxPerspectiveTable(samples);
        var batch = new SpriteBatch();
        Texture red = texture(Color.RED), green = texture(Color.GREEN);
        Texture[] faces = {green, red, red, red};
        try {
            renderer.layout(W, H, H * .5f);
            renderer.drawBackdrop(batch, red, false, 64f, W, H, 1f);
            verifyStackPreview(renderer, green);
            byte[] singles = flightGrid(renderer, faces, 1);
            assertArrayEquals(singles, flightGrid(renderer, faces, 63),
                    "Every transform must survive batching across multiple pages of the same denomination");
            for (boolean pot : new boolean[]{true, false}) {
                for (boolean front : new boolean[]{false, true}) {
                    byte[] rack = capture(renderer, faces, pot, front, true, false);
                    byte[] flight = capture(renderer, faces, pot, front, false, true);
                    byte[] combined = capture(renderer, faces, pot, front, true, true);
                    int overlap = 0, correct = 0;
                    for (int i = 0; i < rack.length; i += 4) {
                        if (!dominant(rack, i, 0) || !dominant(flight, i, 1)) continue;
                        overlap++;
                        if (dominant(combined, i, front ? 1 : 0)) correct++;
                    }
                    assertTrue(overlap > 120, "Missing overlapping silhouettes: " + overlap);
                    assertTrue(correct >= overlap * .95, "Wrong depth at " + (overlap - correct)
                            + " of " + overlap + " pixels, front=" + front + ", pot=" + pot + ", samples=" + samples);
                }
            }
        } finally {
            renderer.dispose();
            red.dispose();
            green.dispose();
            batch.dispose();
        }
    }

    private static void verifyStackPreview(GdxPerspectiveTable renderer, Texture green) {
        float x = W * .5f, y = H * .5f, diameter = 85f, thickness = 12f;
        Texture[] faces = {green, green, green, green};
        byte[] original = captureStackPreview(renderer, faces, x, y, diameter, thickness, 0, 1f);
        byte[] minimum = captureStackPreview(renderer, faces, x, y, diameter, thickness, 2, 0f);
        byte[] peak = captureStackPreview(renderer, faces, x, y, diameter, thickness, 2, 1f);
        assertArrayEquals(original, minimum, "The pulse returns to the original chip colour");

        var top = renderer.chipPointToScreen(x, y, 3 * thickness).cpy();
        var upperRim = renderer.chipPointToScreen(x, y - diameter / 2f, 2.5f * thickness).cpy();
        var middleRim = renderer.chipPointToScreen(x, y - diameter / 2f, 1.5f * thickness).cpy();
        var bottomRim = renderer.chipPointToScreen(x, y - diameter / 2f, .5f * thickness).cpy();
        for (var point : List.of(top, upperRim, middleRim)) {
            int index = (Math.round(point.y) * W + Math.round(point.x)) * 4;
            assertTrue((peak[index] & 255) > (original[index] & 255) + 50,
                    "The selected chip face and both adjoining rims should pulse: " + point);
        }
        int index = (Math.round(bottomRim.y) * W + Math.round(bottomRim.x)) * 4;
        assertEquals(original[index], peak[index], "The unselected bottom chip retains its colour");
        assertEquals(original[index + 1], peak[index + 1]);
        assertEquals(original[index + 2], peak[index + 2]);
        assertTrue((original[index + 1] & 255) > 50, "Sample must hit the bottom chip");
        assertArrayEquals(original, captureStackPreview(renderer, faces, x, y, diameter, thickness, 0, 1f),
                "Clearing hover immediately restores the entire rack");
    }

    private static byte[] captureStackPreview(GdxPerspectiveTable renderer, Texture[] faces,
            float x, float y, float diameter, float thickness, int previewCount, float pulse) {
        ScreenUtils.clear(Color.BLACK);
        renderer.beginChipFrame();
        renderer.beginStandingChipRack();
        renderer.addStandingChipPile(false, 0, 0, 0, 176, x, y, 3, diameter, thickness, 1f,
                null, previewCount);
        renderer.drawStandingChipRack(faces, 1f, Color.GOLD, pulse);
        return ScreenUtils.getFrameBufferPixels(0, 0, W, H, false);
    }

    private static byte[] capture(GdxPerspectiveTable renderer, Texture[] faces, boolean pot,
            boolean front, boolean rack, boolean flight) {
        ScreenUtils.clear(Color.BLACK);
        renderer.beginChipFrame();
        if (rack) {
            renderer.beginStandingChipRack();
            renderer.addStandingChipPile(pot, 0, 0, 0, 176, W * .5f, H * .5f, 12, D, THICKNESS, 1f, null, 0);
            renderer.drawStandingChipRack(faces, 1f, Color.GOLD, 0f);
        }
        if (flight) {
            // Over a diameter of horizontal clearance in both configurations.
            var pose = GdxChipContactsTest.flat(W * .5f, H * .5f + (front ? -1.05f : 1.05f) * D,
                    front ? D * 1.22f : 0f);
            renderer.beginChipRack();
            renderer.addFlyingTableChip(0, pose, D, THICKNESS);
            renderer.drawChipRack(faces, 1f, true);
        }
        return ScreenUtils.getFrameBufferPixels(0, 0, W, H, false);
    }

    private static byte[] flightGrid(GdxPerspectiveTable renderer, Texture[] faces, int groupSize) {
        ScreenUtils.clear(Color.BLACK);
        renderer.beginChipFrame();
        for (int i = 0; i < 63; i++) {
            if (i % groupSize == 0) renderer.beginChipRack();
            var point = renderer.boardAt((i % 9 + 1) * W / 10f, (i / 9 + 1) * H / 8f);
            var pose = GdxChipContactsTest.flat(point.x, point.y, 2f + i % 5);
            pose.pitch = .5f * (float) Math.sin(i);
            pose.roll = .45f * (float) Math.cos(i);
            renderer.addFlyingTableChip(0, pose, D * .5f, THICKNESS * .5f);
            if ((i + 1) % groupSize == 0) renderer.drawChipRack(faces, 1f, true);
        }
        byte[] image = ScreenUtils.getFrameBufferPixels(0, 0, W, H, false);
        int green = 0;
        for (int i = 0; i < image.length; i += 4) if (dominant(image, i, 1)) green++;
        assertTrue(green > 3000, "The grid must contain visible flying chips");
        return image;
    }

    private static boolean dominant(byte[] pixels, int i, int channel) {
        return (pixels[i + channel] & 255) > (pixels[i + 1 - channel] & 255) + 64;
    }

    private static Texture texture(Color color) {
        var pixmap = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        try { return new Texture(pixmap); }
        finally { pixmap.dispose(); }
    }
}
