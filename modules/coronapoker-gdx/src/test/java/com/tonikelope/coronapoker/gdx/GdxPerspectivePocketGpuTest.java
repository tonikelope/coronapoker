/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.game.ActionControlState;
import com.tonikelope.coronapoker.table.TableCommand;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

@Tag("certification")
final class GdxPerspectivePocketGpuTest {
    private static final int WIDTH = 960, HEIGHT = 540;

    @Test void perspectiveTablePreservesCoveredCardsAndAutoCallInput(@TempDir Path dir) {
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
                    verifyLiveClickOwnership(dir);
                    verifyAutoCallPreview(dir);
                    verifyPocket(dir);
                    verifyCrossDeckBack(dir);
                    verifyFlopCallCost(dir);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyCrossDeckBack(Path dir) throws Exception {
        var root = Files.createDirectories(dir.resolve("artwork/mod"));
        Files.writeString(root.resolve("mod.xml"), "<mod><name>Test</name><version>1</version></mod>");
        // Partial decks are valid: the reference face (2_P) need not be present.
        var faces = Files.createDirectories(root.resolve("decks/tall/hq"));
        var backs = Files.createDirectories(root.resolve("decks/wide/hq"));
        ImageIO.write(new BufferedImage(120, 240, BufferedImage.TYPE_INT_RGB), "jpg",
                faces.resolve("A_P.jpg").toFile());
        var magenta = new BufferedImage(320, 128, BufferedImage.TYPE_INT_RGB);
        var graphics = magenta.createGraphics();
        try {
            graphics.setColor(java.awt.Color.MAGENTA);
            graphics.fillRect(0, 0, magenta.getWidth(), magenta.getHeight());
        } finally {
            graphics.dispose();
        }
        ImageIO.write(magenta, "jpg", backs.resolve("trasera.jpg").toFile());
        try (var prefs = new PreferencesService(dir.resolve("cross-deck.properties"))) {
            prefs.properties().setProperty("baraja", "tall");
            prefs.properties().setProperty("trasera", "wide");
            prefs.properties().setProperty("sonido_visor", "false");
            var settings = new GdxGamePresentationSettings(prefs, ModMediaCatalog.discover(root.getParent()));
            var hidden = new TableSnapshot.CardSnapshot("", false, false);
            var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.FLOP,
                    0d, "", false, List.of(), List.of(hidden));
            var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                    command -> {}, () -> {}, new GdxGameLogSink(), prefs, null, settings);
            try {
                table.create();
                table.resize(WIDTH, HEIGHT);
                var viewed = CoronaPokerGdxTable.class.getDeclaredMethod("viewedCard",
                        TableSnapshot.CardSnapshot.class, String.class, int.class);
                viewed.setAccessible(true);
                Object card = viewed.invoke(table, hidden, "local", 0);
                var open = CoronaPokerGdxTable.class.getDeclaredMethod("openCardViewer", card.getClass());
                open.setAccessible(true);
                open.invoke(table, card);
                var opened = CoronaPokerGdxTable.class.getDeclaredField("uiOpenedAt");
                opened.setAccessible(true);
                opened.setFloat(table, -10f);
                table.render();
                byte[] pixels = ScreenUtils.getFrameBufferPixels(false);
                int centerX = WIDTH / 2, centerY = HEIGHT / 2;
                int across = 0, down = 0;
                for (int x = 0; x < WIDTH; x++) if (magenta(pixels, x, centerY)) across++;
                for (int y = 0; y < HEIGHT; y++) if (magenta(pixels, centerX, y)) down++;
                var viewport = (Viewport) field(table, "viewport");
                assertEquals(120f * viewport.getScreenWidth() / viewport.getWorldWidth(), across, 2f);
                assertEquals(240f * viewport.getScreenHeight() / viewport.getWorldHeight(), down, 2f);
            } finally {
                table.dispose();
            }
        }
    }

    private static boolean magenta(byte[] pixels, int x, int y) {
        int i = 4 * (y * WIDTH + x);
        return (pixels[i] & 255) > 200 && (pixels[i + 1] & 255) < 40 && (pixels[i + 2] & 255) > 200;
    }

    private static void verifyFlopCallCost(Path dir) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve("call-cost.properties"))) {
            prefs.properties().setProperty("gdx_table_view", "3d");
            prefs.properties().setProperty("mostrar_coste_igualar", "true");
            var local = new TableSnapshot.PlayerSnapshot("local", 20d, 0d, 2d,
                    true, false, false, false, -2, -2, 0, 0L, false,
                    TableSnapshot.Position.NONE, "", "", List.of());
            var covered = new TableSnapshot.CardSnapshot("", false, false);
            var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.FLOP,
                    8d, "local", false, List.of(local), List.of(
                            new TableSnapshot.CardSnapshot("3_P", true, false),
                            new TableSnapshot.CardSnapshot("7_P", true, false),
                            new TableSnapshot.CardSnapshot("K_P", true, false),
                            covered, covered));
            var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                    command -> {}, () -> {}, new GdxGameLogSink(), prefs);
            try {
                table.create();
                table.resize(WIDTH, HEIGHT);
                var travel = CoronaPokerGdxTable.class.getDeclaredField("perspectiveTravel");
                travel.setAccessible(true);
                travel.setFloat(table, 1f);
                table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                        TableVisualEvent.PreparationStatus.Phase.READY), new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.CallCost(3L, "+2", "rival"),
                        new CompletableFuture<>());
                table.render();

                Viewport viewport = (Viewport) field(table, "viewport");
                float width = viewport.getWorldWidth();
                float height = viewport.getWorldHeight();
                float tableWidth = Math.min(1510f, width * .78f);
                var draw = CoronaPokerGdxTable.class.getDeclaredMethod("drawCallCostOverlay",
                        float.class, float.class, float.class);
                draw.setAccessible(true);
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                draw.invoke(table, width / 2f, height * .52f, tableWidth);
                var glyph = (GlyphLayout) field(table, "glyph");
                float projectedSourceHeight = glyph.height;
                byte[] pixels = ScreenUtils.getFrameBufferPixels(false);
                int bufferWidth = Gdx.graphics.getBackBufferWidth();
                int firstColumn = bufferWidth;
                for (int offset = 0; offset < pixels.length; offset += 4) {
                    if (Byte.toUnsignedInt(pixels[offset]) > 32) {
                        firstColumn = Math.min(firstColumn, (offset / 4) % bufferWidth);
                    }
                }
                assertTrue(firstColumn > bufferWidth * .55f && firstColumn < bufferWidth,
                        "The flop amount must be drawn in the turn and river slots");

                var perspective = CoronaPokerGdxTable.class.getDeclaredField("perspectiveActive");
                perspective.setAccessible(true);
                perspective.setBoolean(table, false);
                draw.invoke(table, width / 2f, height * .52f, tableWidth);
                assertEquals(glyph.height, projectedSourceHeight, .01f,
                        "The source glyph has the same height before the 3D projection");
            } finally {
                table.dispose();
            }
        }
    }

    private static void verifyLiveClickOwnership(Path dir) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve("click-ownership.properties"))) {
            prefs.properties().setProperty("auto_action_buttons", "true");
            prefs.properties().setProperty("auto_call_enabled", "true");
            prefs.properties().setProperty("confirmar_todo", "false");
            prefs.properties().setProperty("musica", "false");
            prefs.properties().setProperty("sonido_efectos", "false");
            var player = new TableSnapshot.PlayerSnapshot("local", 20d, 0d, 20d,
                    true, false, false, false, -2, -2, 0, 0L, false,
                    TableSnapshot.Position.DEALER, "", "", List.of());
            var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                    0d, "local", false, List.of(player), List.of());
            var submitted = new ArrayList<TableCommand>();
            var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                    submitted::add, () -> {}, new GdxGameLogSink(), prefs);
            Input original = Gdx.input;
            var mouse = new Mouse();
            try {
                Gdx.input = mouse.input(original);
                table.create();
                table.resize(WIDTH, HEIGHT);
                table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                        TableVisualEvent.PreparationStatus.Phase.READY), new CompletableFuture<>());
                var controls = ActionControlState.forTurn(
                        2d, 2d, 2d, 1d, 0d, 20d, 2, true, 0);
                table.acceptEvent(new TableVisualEvent.ActionControls(3L, controls),
                        new CompletableFuture<>());
                table.render();
                var viewport = (Viewport) field(table, "viewport");
                var layout = CoronaPokerGdxTable.localActionLayout(viewport.getWorldWidth());
                Point call = screenCenter(viewport, layout.call());
                Point fold = screenCenter(viewport, layout.fold());
                var input = table.inputProcessor();
                mouse.move(call);
                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                // The timeout can reach the renderer after touchDown but before
                // the next frame even samples the pressed button.
                table.acceptEvent(new TableVisualEvent.TurnTimer(4L, "local",
                        1000L, 0L, TableVisualEvent.TurnTimer.Phase.STOP),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.PreActionControls(5L, true, true),
                        new CompletableFuture<>());
                table.render();
                mouse.down = mouse.justPressed = false;
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(0, field(table, "queuedPreAction"),
                        "A held manual call must not become AUTO IR after the timeout");
                assertTrue(submitted.isEmpty());

                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                input.touchUp(call.x, call.y, 0, Input.Buttons.RIGHT);
                table.render();
                assertEquals(0, field(table, "queuedPreAction"));
                mouse.down = mouse.justPressed = false;
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(2, field(table, "queuedPreAction"),
                        "A fresh click on AUTO IR must still work");
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(2, field(table, "queuedPreAction"),
                        "A repeated release must not toggle the selection");

                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                mouse.move(fold);
                mouse.down = mouse.justPressed = false;
                input.touchUp(fold.x, fold.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(2, field(table, "queuedPreAction"),
                        "Dragging from AUTO IR to AUTO TIRAR cannot select either");
                assertTrue(submitted.isEmpty());

                mouse.move(call);
                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                table.acceptEvent(new TableVisualEvent.PreActionControls(6L, false, true),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.TurnTimer(7L, "local",
                        1000L, 1000L, TableVisualEvent.TurnTimer.Phase.START),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.ActionControls(8L, controls),
                        new CompletableFuture<>());
                table.render();
                mouse.down = mouse.justPressed = false;
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertTrue(submitted.isEmpty(),
                        "A held AUTO IR must not submit a manual call on a new turn");

                table.acceptEvent(new TableVisualEvent.TurnTimer(9L, "local",
                        1000L, 0L, TableVisualEvent.TurnTimer.Phase.STOP),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.PreActionControls(10L, true, true),
                        new CompletableFuture<>());
                // The timeout is already applied, but the last frame still
                // shows IR. AUTO IR must wait until it has been presented.
                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                mouse.down = mouse.justPressed = false;
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(0, field(table, "queuedPreAction"),
                        "An AUTO target cannot be armed before it has been drawn");

                table.acceptEvent(new TableVisualEvent.PreActionControls(11L, false, true),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.TurnTimer(12L, "local",
                        1000L, 1000L, TableVisualEvent.TurnTimer.Phase.START),
                        new CompletableFuture<>());
                table.acceptEvent(new TableVisualEvent.ActionControls(13L, controls),
                        new CompletableFuture<>());
                table.render();
                Point plus = screenCenter(viewport, layout.plus());
                mouse.move(plus);
                double before = ((Number) field(table, "liveBetAmount")).doubleValue();
                mouse.down = mouse.justPressed = true;
                input.touchDown(plus.x, plus.y, 0, Input.Buttons.LEFT);
                table.render();
                double firstStep = ((Number) field(table, "liveBetAmount")).doubleValue();
                assertTrue(firstStep > before, "A press steps the bet amount immediately");
                mouse.justPressed = false;
                var repeat = table.getClass().getDeclaredMethod("updatePointerRepeat", float.class);
                repeat.setAccessible(true);
                repeat.invoke(table, 0.8f);
                double held = ((Number) field(table, "liveBetAmount")).doubleValue();
                assertTrue(held > firstStep, "A held plus button repeats");
                mouse.down = false;
                input.touchUp(plus.x, plus.y, 0, Input.Buttons.LEFT);
                mouse.down = mouse.justPressed = true;
                input.touchDown(plus.x, plus.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(held + firstStep - before,
                        ((Number) field(table, "liveBetAmount")).doubleValue(), .001d,
                        "A second click starts a new repeat even without a frame between gestures");
                mouse.down = mouse.justPressed = false;
                input.touchUp(plus.x, plus.y, 0, Input.Buttons.LEFT);
                table.render();

                mouse.move(call);
                mouse.down = mouse.justPressed = true;
                input.touchDown(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                mouse.down = mouse.justPressed = false;
                input.touchUp(call.x, call.y, 0, Input.Buttons.LEFT);
                table.render();
                assertEquals(List.of(new TableCommand.CheckOrCall()), submitted,
                        "A fresh manual call still submits exactly once");
            } finally {
                Gdx.input = original;
                table.dispose();
            }
        }
    }

    private static void verifyAutoCallPreview(Path dir) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve("auto.properties"))) {
            prefs.properties().setProperty("gdx_table_view", "3d");
            prefs.properties().setProperty("modo_auto_confirm", "true");
            prefs.properties().setProperty("auto_call_enabled", "true");
            for (String key : List.of("musica", "sonido_efectos", "sonido_ascensor")) {
                prefs.properties().setProperty(key, "false");
            }
            var player = new TableSnapshot.PlayerSnapshot("local", 20d, 0d, 20d,
                    true, false, false, false, -2, -2, 0, 0L, false,
                    TableSnapshot.Position.DEALER, "", "", List.of());
            var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                    0d, "local", false, List.of(player), List.of());
            var submitted = new ArrayList<TableCommand>();
            var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                    submitted::add, () -> {}, new GdxGameLogSink(), prefs);
            Input original = Gdx.input;
            var mouse = new Mouse();
            try {
                Gdx.input = mouse.input(original);
                table.create();
                table.resize(WIDTH, HEIGHT);
                var travel = CoronaPokerGdxTable.class.getDeclaredField("perspectiveTravel");
                travel.setAccessible(true);
                travel.setFloat(table, 1f);
                table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                        TableVisualEvent.PreparationStatus.Phase.READY), new CompletableFuture<>());
                var controls = ActionControlState.forTurn(2d, 2d, 2d, 1d,
                        0d, 20d, 2, true, 0);
                table.acceptEvent(new TableVisualEvent.ActionControls(3L, controls),
                        new CompletableFuture<>());
                var viewport = (Viewport) field(table, "viewport");
                float width = viewport.getWorldWidth();
                Rectangle call = CoronaPokerGdxTable.localActionLayout(width).call();
                mouse.move(screenCenter(viewport, call));
                table.render();
                var manual = (int[]) field(table, "activeStackBetPreview");
                assertNotNull(manual, "Hovering IR must select physical chips");
                manual = manual.clone();

                mouse.move(new Point(WIDTH / 2, 0));
                var toggle = CoronaPokerGdxTable.class.getDeclaredMethod("toggleQueuedPreAction", int.class);
                toggle.setAccessible(true);
                toggle.invoke(table, 2);
                var eligible = CoronaPokerGdxTable.class.getDeclaredField("autoActionEligibleAt");
                eligible.setAccessible(true);
                eligible.setFloat(table, 0f);
                table.render();

                var auto = (GdxTableDialog) field(table, "autoActionDialog");
                assertNotNull(auto, "The queued call must open the Auto HUD");
                assertArrayEquals(manual, (int[]) field(table, "activeStackBetPreview"));

                Rectangle cancel = CoronaPokerGdxTable.autoActionHudLayout(width).cancel();
                Point click = screenCenter(viewport, cancel);
                mouse.move(click);
                mouse.down = mouse.justPressed = true;
                var processor = table.inputProcessor();
                processor.touchDown(click.x, click.y, 0, Input.Buttons.LEFT);
                table.render();
                assertNull(field(table, "autoActionDialog"), "CANCELAR must immediately restore the HUD");
                assertFalse(auto.result().toCompletableFuture().join());
                assertTrue(submitted.isEmpty(), "The cancellation press must not submit a poker action");

                mouse.down = mouse.justPressed = false;
                processor.touchUp(click.x, click.y, 0, Input.Buttons.LEFT);
                mouse.move(new Point(WIDTH / 2, 0));
                table.render();
                assertNull(field(table, "activeStackBetPreview"), "Auto chips must stop pulsing after cancellation");
                mouse.move(screenCenter(viewport, call));
                table.render();
                assertArrayEquals(manual, (int[]) field(table, "activeStackBetPreview"));
                assertTrue(submitted.isEmpty(), "Releasing CANCELAR must not affect the hand");

                table.acceptEvent(new TableVisualEvent.ActionControls(4L, controls),
                        new CompletableFuture<>());
                toggle.invoke(table, 2);
                eligible.setFloat(table, 0f);
                table.render();
                var nextAuto = (GdxTableDialog) field(table, "autoActionDialog");
                assertNotNull(nextAuto);
                mouse.spaceJustPressed = true;
                table.render();
                mouse.spaceJustPressed = false;
                assertTrue(nextAuto.complete(), "SPACE must finish the countdown");
                assertTrue(nextAuto.result().toCompletableFuture().join());
                assertEquals(List.of(new TableCommand.CheckOrCall()), submitted,
                        "SPACE must still accept the Auto call exactly once");
            } finally {
                Gdx.input = original;
                table.dispose();
            }
        }
    }

    private static Point screenCenter(Viewport viewport, Rectangle bounds) {
        Vector2 center = viewport.project(bounds.getCenter(new Vector2()));
        return new Point(Math.round(center.x), HEIGHT - 1 - Math.round(center.y));
    }

    private static void verifyPocket(Path dir) throws Exception {
        var prefs = new PreferencesService(dir.resolve("table.properties"));
        prefs.properties().setProperty("gdx_table_view", "3d");
        for (String key : List.of("musica", "sonido_efectos", "sonido_ascensor")) {
            prefs.properties().setProperty(key, "false");
        }
        var left = new TableSnapshot.CardSnapshot("A_C", true, false);
        var right = new TableSnapshot.CardSnapshot("K_D", true, false);
        var player = new TableSnapshot.PlayerSnapshot("local", 20d, 0d, 20d,
                true, false, false, false, -2, -2, 0, 0L, false,
                TableSnapshot.Position.DEALER, "", "", List.of(left, right));
        var snapshot = new TableSnapshot(1L, "local", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(player), List.of());
        var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> {}, () -> {}, new GdxGameLogSink(), prefs);
        Input original = Gdx.input;
        var mouse = new Mouse();
        try {
            Gdx.input = mouse.input(original);
            table.create();
            table.resize(WIDTH, HEIGHT);
            var cameraTravel = CoronaPokerGdxTable.class.getDeclaredField("perspectiveTravel");
            cameraTravel.setAccessible(true);
            cameraTravel.setFloat(table, 1f);
            var ready = new CompletableFuture<Void>();
            table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                    TableVisualEvent.PreparationStatus.Phase.READY), ready);
            assertTrue(ready.isDone());
            ready.join();
            table.render();

            var renderer = (GdxPerspectiveTable) field(table, "perspectiveTable");
            var viewport = (Viewport) field(table, "viewport");
            verifyFixedDealerDuringSort(table, viewport);
            var button = (Rectangle) field(table, "pocketPeekButton");
            var hitsButton = CoronaPokerGdxTable.class.getDeclaredMethod(
                    "pocketPeekSurfaceContains", float.class, float.class);
            hitsButton.setAccessible(true);
            assertTrue(button.width > 0f, "The button must be shown when the setting is absent");
            float buttonX = button.x + button.width / 2f, buttonY = button.y + button.height / 2f;
            assertTrue((boolean) hitsButton.invoke(table, buttonX, buttonY));
            prefs.properties().setProperty(GdxSettingsContract.TABLE_PEEK_BUTTON_KEY, "false");
            assertFalse((boolean) hitsButton.invoke(table, buttonX, buttonY));
            table.render();
            assertEquals(0f, button.width, "The disabled button must not be drawn");

            mouse.peekKeyDown = true;
            for (int frame = 0; frame < 20 && renderer.peekAmount() == 0f; frame++) table.render();
            assertTrue(renderer.peekAmount() > 0f, "Tab still peeks with the button hidden");
            mouse.peekKeyDown = false;
            renderer.coverCards();
            var shortcuts = (GdxShortcutBindings) field(table, "shortcutBindings");
            shortcuts.beginEdit();
            assertEquals(GdxShortcutBindings.Assignment.ASSIGNED,
                    shortcuts.assign(GdxShortcutBindings.POCKET_PEEK, Input.Keys.K,
                            false, true, false));
            shortcuts.commitEdit();
            mouse.peekKeyDown = true;
            table.render();
            assertEquals(0f, renderer.peekAmount(), "The previous key no longer peeks");
            mouse.peekKey = Input.Keys.K;
            table.render();
            assertEquals(0f, renderer.peekAmount(), "The new shortcut requires its modifier");
            mouse.peekControlDown = true;
            for (int frame = 0; frame < 20 && renderer.peekAmount() == 0f; frame++) table.render();
            assertTrue(renderer.peekAmount() > 0f, "The saved Control + K shortcut peeks");
            mouse.peekKeyDown = false;
            mouse.peekControlDown = false;
            mouse.peekKey = Input.Keys.TAB;
            prefs.properties().remove("shortcut." + GdxShortcutBindings.POCKET_PEEK);
            shortcuts.reload();
            renderer.coverCards();
            table.render();
            var grip = CoronaPokerGdxTable.class.getDeclaredMethod("pocketPeekDragContains", float.class, float.class);
            grip.setAccessible(true);
            var cardAt = CoronaPokerGdxTable.class.getDeclaredMethod("liveCardAt", float.class, float.class, boolean.class);
            cardAt.setAccessible(true);

            var occludes = CoronaPokerGdxTable.class.getDeclaredMethod(
                    "tableChromeOccludesCards", float.class, float.class, float.class);
            occludes.setAccessible(true);
            var visibleCards = List.of(new ArrayList<Point>(), new ArrayList<Point>());
            var probe = new Vector2();
            for (int y = 1; y < HEIGHT; y += 3) {
                for (int x = WIDTH / 2 - 100; x < WIDTH / 2 + 100; x += 4) {
                    viewport.unproject(probe.set(x, y));
                    int slot = renderer.pocketSlotAt(probe.x, probe.y);
                    if (slot < 0 || (boolean) occludes.invoke(table, probe.x, probe.y, viewport.getWorldWidth())) {
                        continue;
                    }
                    assertTrue((boolean) grip.invoke(table, probe.x, probe.y),
                            "Every visible point on either covered card must accept a drag");
                    visibleCards.get(slot).add(new Point(x, y));
                }
            }
            for (var card : visibleCards) {
                assertTrue(card.size() > 20, "Both covered cards must be visible");
                for (int sixth : new int[]{1, 3, 5}) {
                    mouse.move(card.get(card.size() * sixth / 6));
                    table.render();
                    assertEquals(Cursor.SystemCursor.Hand, field(table, "appliedSystemCursor"));
                }
            }
            var firstCard = visibleCards.get(0);
            var secondCard = visibleCards.get(1);
            Point center = firstCard.get(firstCard.size() / 2);
            Point dragOrigin = secondCard.get(secondCard.size() / 6);
            Vector2 covered = viewport.unproject(new Vector2(center.x, center.y));
            assertNotNull(cardAt.invoke(table, covered.x, covered.y, false));
            assertNull(cardAt.invoke(table, covered.x, covered.y, true));

            var processor = table.inputProcessor();
            mouse.move(new Point(WIDTH / 2, 0));
            table.render();
            assertEquals(Cursor.SystemCursor.Arrow, field(table, "appliedSystemCursor"));
            mouse.move(center);
            mouse.down = mouse.justPressed = true;
            assertTrue(processor.touchDown(center.x, center.y, 0, Input.Buttons.LEFT));
            table.render();
            mouse.down = mouse.justPressed = false;
            processor.touchUp(center.x, center.y, 0, Input.Buttons.LEFT);
            table.render();
            assertEquals(CoronaPokerGdxTable.UI_NONE, field(table, "uiLayer"));
            mouse.move(dragOrigin);
            table.render();
            assertEquals(Cursor.SystemCursor.Hand, field(table, "appliedSystemCursor"));

            mouse.down = true;
            assertTrue(processor.touchDown(dragOrigin.x, dragOrigin.y, 0, Input.Buttons.LEFT));
            table.render();
            assertEquals(0f, renderer.peekAmount());
            assertEquals(true, field(table, "pocketDragCursorApplied"));
            assertTrue(processor.touchDragged(dragOrigin.x, dragOrigin.y - 12, 0));
            table.render();
            assertEquals(.25f, renderer.peekAmount(), .002f);
            assertTrue(processor.touchDragged(dragOrigin.x, dragOrigin.y - 35, 0));
            table.render();
            assertEquals(35f / 48f, renderer.peekAmount(), .002f);
            assertTrue(processor.touchDragged(dragOrigin.x, dragOrigin.y - 120, 0));
            table.render();
            assertEquals(1f, renderer.peekAmount());
            assertTrue(processor.touchDragged(dragOrigin.x, dragOrigin.y - 24, 0));
            table.render();
            assertEquals(.5f, renderer.peekAmount(), .002f);
            assertTrue(processor.touchDragged(dragOrigin.x, dragOrigin.y - 120, 0));
            table.render();

            mouse.down = false;
            assertTrue(processor.touchUp(dragOrigin.x, dragOrigin.y - 120, 0, Input.Buttons.LEFT));
            table.render();
            assertEquals(false, field(table, "pocketDragCursorApplied"));
            float dt = Math.min(Gdx.graphics.getDeltaTime(), .05f);
            float firstClosingAmount = (1f + 17f * dt) * (float) Math.exp(-17f * dt);
            assertEquals(firstClosingAmount, renderer.peekAmount(), .0001f,
                    "Drag release must use the peek button's closing spring");
            assertEquals(CoronaPokerGdxTable.UI_NONE, field(table, "uiLayer"));

            mouse.move(new Point(WIDTH / 2, 0));
            renderer.coverCards();
            verifyRabbitPocket(table, visibleCards, mouse, renderer, viewport);
            mouse.move(new Point(WIDTH / 2, 0));
            renderer.coverCards();
            table.render();

            var barrier = new CompletableFuture<Void>();
            table.acceptEvent(new TableVisualEvent.RevealHoleCards(8L, "local", left, right, ""), barrier);
            assertFalse(barrier.isDone(), "The public uncover must animate");
            assertNull(cardAt.invoke(table, covered.x, covered.y, true),
                    "The viewer is disabled during the public uncover");
            long deadline = System.nanoTime() + 8_000_000_000L;
            while (!barrier.isDone() && System.nanoTime() < deadline) table.render();
            assertTrue(barrier.isDone(), "The public uncover animation timed out");
            barrier.join();
            table.render();

            Point exposed = null;
            for (int y = HEIGHT - 1; y > 0 && exposed == null; y--) {
                Vector2 point = viewport.unproject(new Vector2(WIDTH / 2f, y));
                if (cardAt.invoke(table, point.x, point.y, true) != null
                        && !(boolean) occludes.invoke(table, point.x, point.y, viewport.getWorldWidth())) {
                    exposed = new Point(WIDTH / 2, y);
                }
            }
            assertNotNull(exposed, "A fully exposed local card is available again in the viewer");
            mouse.move(exposed);
            table.render();
            assertEquals(Cursor.SystemCursor.Hand, field(table, "appliedSystemCursor"));
            mouse.down = mouse.justPressed = true;
            processor.touchDown(exposed.x, exposed.y, 0, Input.Buttons.LEFT);
            table.render();
            mouse.down = mouse.justPressed = false;
            processor.touchUp(exposed.x, exposed.y, 0, Input.Buttons.LEFT);
            table.render();
            assertEquals(CoronaPokerGdxTable.UI_CARD_VIEWER, field(table, "uiLayer"));
            assertEquals(true, field(table, "cardViewerFaceUp"));
        } finally {
            Gdx.input = original;
            table.dispose();
        }
    }

    private static void verifyFixedDealerDuringSort(CoronaPokerGdxTable table, Viewport viewport) throws Exception {
        Object seat = ((Object[]) field(table, "seats"))[0];
        var anchor = CoronaPokerGdxTable.class.getDeclaredMethod("positionMarkerAnchor", seat.getClass());
        anchor.setAccessible(true);
        var fixed = new Vector2((Vector2) anchor.invoke(table, seat));
        Vector2 center = viewport.project(new Vector2(fixed));
        // The exchanging card passes under the left side of the dealer disc.
        int x = Math.round(center.x) - WIDTH / 50;
        int y = Math.round(center.y) - 2;
        byte[] artwork = ScreenUtils.getFrameBufferPixels(x, y, 5, 5, false);
        var order = (GdxPocketOrder) field(table, "pocketOrder");
        order.deal(0, "A_C");
        order.deal(1, "K_D");
        assertTrue(order.busy());
        for (int frame = 0; frame < 60 && order.busy(); frame++) {
            order.update(true, true, 1f / 60f);
            table.render();
            assertEquals(fixed, anchor.invoke(table, seat));
            assertArrayEquals(artwork, ScreenUtils.getFrameBufferPixels(x, y, 5, 5, false));
        }
        assertFalse(order.busy());
    }

    private static void verifyRabbitPocket(CoronaPokerGdxTable table,
            List<? extends List<Point>> visibleCards, Mouse mouse,
            GdxPerspectiveTable renderer, Viewport viewport) throws Exception {
        table.acceptEvent(new TableVisualEvent.HandResult(3L, "local", "PAREJA",
                true, TableSnapshot.Street.SHOWDOWN, true), new CompletableFuture<>());
        table.acceptEvent(new TableVisualEvent.ShowdownHighlight(4L, "local",
                true, List.of(0, 1), List.of(0, 1, 2)), new CompletableFuture<>());
        table.render();
        byte[] before = ScreenUtils.getFrameBufferPixels(true);

        var rabbitCard = new TableVisualEvent.RabbitCard(4,
                new TableSnapshot.CardSnapshot("Q_C", false, false));
        table.acceptEvent(new TableVisualEvent.RabbitCards(5L,
                List.of(rabbitCard), true), new CompletableFuture<>());
        var reveal = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.RabbitCards(6L,
                List.of(new TableVisualEvent.RabbitCard(4,
                        new TableSnapshot.CardSnapshot("Q_C", true, false))),
                false), reveal);
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!reveal.isDone() && System.nanoTime() < deadline) table.render();
        assertTrue(reveal.isDone(), "The Rabbit peel must finish");
        reveal.join();
        table.acceptEvent(new TableVisualEvent.RabbitHand(7L, "local", "COLOR",
                List.of(1), List.of(1, 2, 3, 4)), new CompletableFuture<>());
        table.render();
        assertCoveredCardsMatch(before, visibleCards);

        Object local = ((Object[]) field(table, "seats"))[0];
        var badgeBounds = CoronaPokerGdxTable.class.getDeclaredMethod(
                "localActionBadgeBounds", local.getClass(), float.class);
        badgeBounds.setAccessible(true);
        var badge = (Rectangle) badgeBounds.invoke(null, local, viewport.getWorldWidth());
        var center = viewport.project(badge.getCenter(new Vector2()));
        mouse.move(new Point(Math.round(center.x), HEIGHT - 1 - Math.round(center.y)));
        table.render();
        assertEquals("local", field(table, "liveShowdownHoverNickname"));
        assertCoveredCardsMatch(before, visibleCards);

        mouse.peekKeyDown = true;
        for (int frame = 0; frame < 20 && renderer.peekAmount() == 0f; frame++) table.render();
        assertTrue(renderer.peekAmount() > 0f, "V must still peek during Rabbit Hunting");
        assertFalse(((GdxTableViewState) field(table, "liveState")).hasPublicHoleCards("local"));
        mouse.peekKeyDown = false;
    }

    private static void assertCoveredCardsMatch(byte[] before,
            List<? extends List<Point>> visibleCards) {
        byte[] after = ScreenUtils.getFrameBufferPixels(true);
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        for (int slot = 0; slot < visibleCards.size(); slot++) {
            List<Point> points = visibleCards.get(slot);
            int changed = 0;
            for (Point point : points) {
                int pixel = 4 * (point.y * height / HEIGHT * width + point.x * width / WIDTH);
                if (Math.abs(Byte.toUnsignedInt(before[pixel]) - Byte.toUnsignedInt(after[pixel])) > 8
                        || Math.abs(Byte.toUnsignedInt(before[pixel + 1]) - Byte.toUnsignedInt(after[pixel + 1])) > 8
                        || Math.abs(Byte.toUnsignedInt(before[pixel + 2]) - Byte.toUnsignedInt(after[pixel + 2])) > 8) {
                    changed++;
                }
            }
            assertTrue(changed < points.size() / 10,
                    "Rabbit Hunting changed the covered card in slot " + slot
                    + " at " + changed + " of " + points.size() + " pixels");
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class Mouse {
        private int x, y;
        boolean down, justPressed, peekKeyDown, peekControlDown, spaceJustPressed;
        int peekKey = Input.Keys.TAB;

        void move(Point point) {
            x = point.x;
            y = point.y;
        }

        Input input(Input fallback) {
            return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> x;
                        case "getY" -> y;
                        case "isKeyPressed" -> (int) args[0] == peekKey
                                ? peekKeyDown : (int) args[0] == Input.Keys.CONTROL_LEFT
                                        ? peekControlDown : fallback.isKeyPressed((int) args[0]);
                        case "isKeyJustPressed" -> (int) args[0] == Input.Keys.SPACE
                                ? spaceJustPressed : fallback.isKeyJustPressed((int) args[0]);
                        case "isButtonPressed" -> (int) args[0] == Input.Buttons.LEFT && down;
                        case "isButtonJustPressed" -> (int) args[0] == Input.Buttons.LEFT && justPressed;
                        default -> method.invoke(fallback, args);
                    });
        }
    }
}
