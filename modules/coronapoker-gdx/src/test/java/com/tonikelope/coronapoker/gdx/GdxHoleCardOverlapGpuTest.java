/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tonikelope.coronapoker.gdx.GdxHoleCardOverlapGpuTest.Appearance.ACTIVE;
import static com.tonikelope.coronapoker.gdx.GdxHoleCardOverlapGpuTest.Appearance.DIMMED;
import static com.tonikelope.coronapoker.gdx.GdxHoleCardOverlapGpuTest.Appearance.HIDDEN;

@Tag("certification")
final class GdxHoleCardOverlapGpuTest {
    private static final int WIDTH = 960, HEIGHT = 540;
    private static final String LOCAL = "local", REMOTE = "remote";

    enum Appearance { ACTIVE, DIMMED, HIDDEN }

    @Test
    void opaqueCardsOccludeDimmedCardsRegardlessOfSideAcrossTableViews(@TempDir Path dir) throws Exception {
        Path mod = Files.createDirectories(dir.resolve("mod"));
        Files.writeString(mod.resolve("mod.xml"), "<mod><name>Test</name><version>1</version></mod>");
        Path faces = Files.createDirectories(mod.resolve("decks/overlap/hq"));
        writeFace(faces.resolve("K_D.jpg"), Color.MAGENTA);
        writeFace(faces.resolve("A_C.jpg"), Color.GREEN);
        var media = ModMediaCatalog.discover(dir);

        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(WIDTH, HEIGHT);
        var failure = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
                if (Gdx.graphics.getDeltaTime() <= 0f) return;
                try {
                    long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
                    assertEquals(GLFW.GLFW_FALSE, GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    verifyView(dir, media, false);
                    verifyView(dir, media, true);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyView(Path dir, ModMediaCatalog media, boolean perspective) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve(perspective ? "3d.properties" : "2d.properties"))) {
            prefs.properties().setProperty("gdx_table_view", perspective ? "3d" : "2d");
            prefs.properties().setProperty("baraja", "overlap");
            prefs.properties().setProperty("musica", "false");
            prefs.properties().setProperty("sonido_efectos", "false");
            var settings = new GdxGamePresentationSettings(prefs, media);
            var state = new GdxTableViewState(new TableSnapshot(1L, LOCAL, TableSnapshot.Street.RIVER,
                    0d, "", false, List.of(player(LOCAL), player(REMOTE)), List.of()));
            var table = new CoronaPokerGdxTable(60, state, command -> {}, () -> {},
                    new GdxGameLogSink(), prefs, null, settings);
            try {
                table.create();
                table.resize(WIDTH, HEIGHT);
                if (perspective) field("perspectiveTravel").setFloat(table, 1f);
                table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                        TableVisualEvent.PreparationStatus.Phase.READY), new CompletableFuture<>());
                table.render();
                assertEquals(perspective, field("perspectiveActive").getBoolean(table));
                var draw = CoronaPokerGdxTable.class.getDeclaredMethod("drawHoleCards", boolean.class);
                draw.setAccessible(true);

                int localFront = perspective ? 0 : 1;
                verifyFan(table, state, draw, LOCAL, localFront, perspective ? "local 3D" : "local 2D");
                if (!perspective) {
                    Field layer = field("uiLayer");
                    int previous = layer.getInt(table);
                    layer.setInt(table, field("UI_SETTINGS").getInt(null));
                    try {
                        verifyFan(table, state, draw, LOCAL, localFront, "local under settings");
                    } finally {
                        layer.setInt(table, previous);
                    }
                }
                present(state, LOCAL, HIDDEN, HIDDEN);
                verifyFan(table, state, draw, REMOTE, 1, perspective ? "remote in 3D" : "remote in 2D");
                present(state, REMOTE, HIDDEN, HIDDEN);
                verifyCommunityHighlight(table, state);
            } finally {
                table.dispose();
            }
        }
    }

    private static void verifyFan(CoronaPokerGdxTable table, GdxTableViewState state,
            Method draw, String name, int frontSlot, String view) throws Exception {
        present(state, name, ACTIVE, HIDDEN);
        byte[] first = capture(table, draw);
        present(state, name, HIDDEN, ACTIVE);
        byte[] second = capture(table, draw);
        byte[] front = frontSlot == 0 ? first : second;
        byte[] rear = frontSlot == 0 ? second : first;
        assertTrue(centerX(front, frontSlot) > centerX(rear, 1 - frontSlot), view + " front position");

        presentFan(state, name, frontSlot, DIMMED, HIDDEN);
        byte[] frontDimmed = capture(table, draw);
        presentFan(state, name, frontSlot, DIMMED, ACTIVE);
        byte[] frontDimmedPair = capture(table, draw);
        GdxPerspectiveTable pocket = LOCAL.equals(name) && field("perspectiveActive").getBoolean(table)
                ? (GdxPerspectiveTable) field("perspectiveTable").get(table) : null;
        float[][] mixedGeometry = pocket == null ? null : pocketGeometry(pocket);
        present(state, name, DIMMED, DIMMED);
        byte[] bothDimmed = capture(table, draw);
        presentFan(state, name, frontSlot, ACTIVE, DIMMED);
        byte[] rearDimmedPair = capture(table, draw);
        present(state, name, ACTIVE, ACTIVE);
        byte[] bothActive = capture(table, draw);
        if (pocket != null) assertPocketGeometry(mixedGeometry, pocket);

        boolean settings = field("uiLayer").getInt(table) == field("UI_SETTINGS").getInt(null);
        int overlap = 0, exposedDimmed = 0, incorrectFront = 0, incorrectRear = 0, changedNormalOrder = 0;
        for (int i = 0; i < front.length; i += 4) {
            if (isFace(front, i, frontSlot) && !isFace(rear, i, 1 - frontSlot)
                    && difference(frontDimmed, frontDimmedPair, i) <= 9
                    && difference(front, frontDimmedPair, i) > 80) exposedDimmed++;
            if (isFace(front, i, frontSlot) && isFace(rear, i, 1 - frontSlot)) {
                overlap++;
                if (difference(rear, frontDimmedPair, i) > 9) incorrectFront++;
                if (difference(front, rearDimmedPair, i) > 9) incorrectRear++;
                if (difference(front, bothActive, i) > 9
                        || (!settings && difference(frontDimmed, bothDimmed, i) > 9)) changedNormalOrder++;
            }
        }
        assertTrue(overlap > 40, view + " has only " + overlap + " overlapped interior pixels");
        assertTrue(exposedDimmed > 100, view + " must retain the exposed part of the dimmed card");
        assertTrue(incorrectFront < overlap / 8, view + " dimmed right card covers the left at " + incorrectFront + "/" + overlap);
        assertTrue(incorrectRear < overlap / 8, view + " dimmed left card covers the right at " + incorrectRear + "/" + overlap);
        assertTrue(changedNormalOrder < overlap / 8, view + " changes the order of equal-opacity cards");
        if (!settings) {
            verifyYellowOnTop(table, state, draw, name, 0, first, second, bothDimmed, view, pocket);
            verifyYellowOnTop(table, state, draw, name, 1, second, first, bothDimmed, view, pocket);
        }
        if (pocket != null) {
            verifyPocketHover(table, state, draw, pocket, frontSlot, front, rear,
                    frontDimmedPair, bothActive, mixedGeometry);
        }
    }

    private static void verifyYellowOnTop(CoronaPokerGdxTable table, GdxTableViewState state, Method draw,
            String name, int slot, byte[] selected, byte[] other, byte[] bothDimmed,
            String view, GdxPerspectiveTable pocket) throws Exception {
        float[][] normalGeometry = pocket == null ? null : pocketGeometry(pocket);
        var hover = field("liveShowdownHoverNickname");
        setHighlight(state, name, List.of(slot), List.of());
        hover.set(table, name);
        try {
            presentFan(state, name, slot, DIMMED, HIDDEN);
            byte[] yellowAlone = capture(table, draw);
            present(state, name, DIMMED, DIMMED);
            byte[] yellowPair = capture(table, draw);
            if (pocket != null) assertPocketGeometry(normalGeometry, pocket);
            int overlap = 0, incorrect = 0;
            for (int i = 0; i < selected.length; i += 4) {
                if (!isFace(selected, i, slot) || !isFace(other, i, 1 - slot)) continue;
                overlap++;
                if (!isOpaqueTint(yellowPair, i, slot) || difference(yellowAlone, yellowPair, i) > 9) incorrect++;
            }
            assertTrue(overlap > 40 && incorrect < overlap / 8,
                    view + " yellow slot " + slot + " is occluded or translucent at " + incorrect + "/" + overlap);
        } finally {
            hover.set(table, null);
            setHighlight(state, name, List.of(), List.of());
        }
        assertTrue(java.util.Arrays.equals(bothDimmed, capture(table, draw)),
                view + " should restore the original pair when highlight ends");
    }

    private static void verifyPocketHover(CoronaPokerGdxTable table, GdxTableViewState state, Method draw,
            GdxPerspectiveTable pocket, int frontSlot, byte[] front, byte[] rear,
            byte[] mixed, byte[] bothActive, float[][] geometry) throws Exception {
        var pointer = (Vector2) field("pointer").get(table);
        var previous = pointer.cpy();
        var viewport = (Viewport) field("viewport").get(table);
        presentFan(state, LOCAL, frontSlot, DIMMED, ACTIVE);
        capture(table, draw);
        try {
            pointer.set(pocketSample(viewport, front, rear, frontSlot, true));
            assertEquals(1 - frontSlot, pocket.pocketSlotAt(pointer.x, pointer.y),
                    "The visible opaque left card owns the overlap");
            assertArrayEquals(mixed, capture(table, draw));
            pointer.set(pocketSample(viewport, front, rear, frontSlot, false));
            assertEquals(frontSlot, pocket.pocketSlotAt(pointer.x, pointer.y),
                    "The exposed right card can still be inspected");
            assertArrayEquals(bothActive, capture(table, draw),
                    "Hovering the exposed right card restores its opacity and the usual order");
            assertPocketGeometry(geometry, pocket);
        } finally {
            pointer.set(previous);
        }
        assertArrayEquals(mixed, capture(table, draw), "Both cards return to the original presentation");
        assertPocketGeometry(geometry, pocket);
    }

    private static Vector2 pocketSample(Viewport viewport, byte[] front, byte[] rear,
            int frontSlot, boolean overlap) {
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int left = 4 * (y * width + x - 2), right = left + 16;
                if (isFace(front, left, frontSlot) && isFace(front, right, frontSlot)
                        && isFace(rear, left, 1 - frontSlot) == overlap
                        && isFace(rear, right, 1 - frontSlot) == overlap) {
                    return viewport.unproject(new Vector2((x + .5f) * Gdx.graphics.getWidth() / width,
                            (height - y - .5f) * Gdx.graphics.getHeight() / height));
                }
            }
        }
        throw new AssertionError("Expected a sample in the pocket " + (overlap ? "overlap" : "exposed right area"));
    }

    private static float[][] pocketGeometry(GdxPerspectiveTable pocket) throws ReflectiveOperationException {
        var field = GdxPerspectiveTable.class.getDeclaredField("pocketPicks");
        field.setAccessible(true);
        var picks = (GdxCardPickMesh[]) field.get(pocket);
        assertTrue(picks[0].triangles.size > 0 && picks[1].triangles.size > 0);
        return new float[][] {picks[0].triangles.toArray(), picks[1].triangles.toArray()};
    }

    private static void assertPocketGeometry(float[][] expected, GdxPerspectiveTable pocket)
            throws ReflectiveOperationException {
        float[][] actual = pocketGeometry(pocket);
        for (int slot = 0; slot < expected.length; slot++) {
            assertArrayEquals(expected[slot], actual[slot], "Opacity changed the mesh of slot " + slot);
        }
    }

    private static void verifyCommunityHighlight(CoronaPokerGdxTable table, GdxTableViewState state) throws Exception {
        var draw = CoronaPokerGdxTable.class.getDeclaredMethod("drawCardsAndPot", float.class, float.class, float.class);
        draw.setAccessible(true);
        var viewport = (Viewport) field("viewport").get(table);
        float width = viewport.getWorldWidth(), height = viewport.getWorldHeight();
        Object[] board = {width / 2f, height * .52f, Math.min(1510f, width * .78f)};
        setBoardFace(state, false);
        byte[] active = capture(table, draw, board);
        setBoardFace(state, true);
        byte[] dimmed = capture(table, draw, board);
        setHighlight(state, LOCAL, List.of(), List.of(0));
        var hover = field("liveShowdownHoverNickname");
        hover.set(table, LOCAL);
        byte[] yellow;
        try {
            yellow = capture(table, draw, board);
        } finally {
            hover.set(table, null);
            setHighlight(state, LOCAL, List.of(), List.of());
        }
        int face = 0, yellowPixels = 0;
        for (int i = 0; i < active.length; i += 4) {
            if (!isFace(active, i, 0) || difference(active, dimmed, i) < 80) continue;
            face++;
            if (isOpaqueTint(yellow, i, 0)) yellowPixels++;
        }
        assertTrue(face > 100 && yellowPixels > face * .9f,
                "The selected community card must render in opaque yellow at " + yellowPixels + "/" + face);
        assertTrue(java.util.Arrays.equals(dimmed, capture(table, draw, board)),
                "The community card should return to its dimmed state when highlight ends");
    }

    private static boolean isOpaqueTint(byte[] pixels, int i, int slot) {
        int red = pixels[i] & 255, green = pixels[i + 1] & 255, blue = pixels[i + 2] & 255;
        return slot == 0 ? red > 200 && blue > 135 && green > 50 && green < 100
                : green > 200 && red > 50 && red < 100 && blue < 30;
    }

    private static int centerX(byte[] pixels, int slot) {
        long sum = 0;
        int count = 0;
        int width = Gdx.graphics.getBackBufferWidth();
        for (int i = 0; i < pixels.length; i += 4) {
            if (isFace(pixels, i, slot)) {
                sum += i / 4 % width;
                count++;
            }
        }
        assertTrue(count > 100, "Expected a solid card in slot " + slot);
        return (int) (sum / count);
    }

    private static boolean isFace(byte[] pixels, int i, int slot) {
        int red = pixels[i] & 255, green = pixels[i + 1] & 255, blue = pixels[i + 2] & 255;
        return slot == 0 ? red > 175 && blue > 175 && green < 35
                : green > 175 && red < 35 && blue < 35;
    }

    private static int difference(byte[] a, byte[] b, int i) {
        return Math.max(Math.abs((a[i] & 255) - (b[i] & 255)),
                Math.max(Math.abs((a[i + 1] & 255) - (b[i + 1] & 255)),
                        Math.abs((a[i + 2] & 255) - (b[i + 2] & 255))));
    }

    private static byte[] capture(CoronaPokerGdxTable table, Method draw) throws Exception {
        return capture(table, draw, false);
    }

    private static byte[] capture(CoronaPokerGdxTable table, Method draw, Object... args) throws Exception {
        Gdx.gl.glClearColor(.15f, .15f, .15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        draw.invoke(table, args);
        return ScreenUtils.getFrameBufferPixels(false);
    }

    private static void setBoardFace(GdxTableViewState state, boolean dimmed) {
        state.apply(new TableVisualEvent.RevealCommunityCards(state.lastSequence() + 1, 0,
                List.of(new TableSnapshot.CardSnapshot("K_D", true, dimmed))));
    }

    private static void setHighlight(GdxTableViewState state, String name, List<Integer> holes, List<Integer> board) {
        state.apply(new TableVisualEvent.ShowdownHighlight(state.lastSequence() + 1, name,
                !holes.isEmpty() || !board.isEmpty(), holes, board));
    }

    private static void presentFan(GdxTableViewState state, String name,
            int frontSlot, Appearance front, Appearance rear) {
        present(state, name, frontSlot == 0 ? front : rear, frontSlot == 0 ? rear : front);
    }

    private static void present(GdxTableViewState state, String name, Appearance first, Appearance second) {
        state.apply(new TableVisualEvent.RevealHoleCards(state.lastSequence() + 1, name,
                new TableSnapshot.CardSnapshot("K_D", true, first == DIMMED, first != HIDDEN),
                new TableSnapshot.CardSnapshot("A_C", true, second == DIMMED, second != HIDDEN), ""));
    }

    private static TableSnapshot.PlayerSnapshot player(String name) {
        return new TableSnapshot.PlayerSnapshot(name, 20d, 0d, 20d,
                true, false, false, false, -2, -2, 0, 0L, false,
                TableSnapshot.Position.NONE, "", "", List.of());
    }

    private static Field field(String name) throws ReflectiveOperationException {
        var field = CoronaPokerGdxTable.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void writeFace(Path path, Color color) throws Exception {
        var image = new BufferedImage(180, 250, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        assertTrue(ImageIO.write(image, "jpg", path.toFile()));
    }
}
