/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.awt.Point;
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

    @Test void dragStartsAnywhereOnEitherCoveredPocketCard(@TempDir Path dir) {
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
                    verifyPocket(dir);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
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
            assertTrue(renderer.peekAmount() > 0f, "V still peeks with the button hidden");
            mouse.peekKeyDown = false;
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

            var barrier = new CompletableFuture<Void>();
            table.acceptEvent(new TableVisualEvent.RevealHoleCards(3L, "local", left, right, ""), barrier);
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

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class Mouse {
        private int x, y;
        boolean down, justPressed, peekKeyDown;

        void move(Point point) {
            x = point.x;
            y = point.y;
        }

        Input input(Input fallback) {
            return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> x;
                        case "getY" -> y;
                        case "isKeyPressed" -> (int) args[0] == Input.Keys.V
                                ? peekKeyDown : fallback.isKeyPressed((int) args[0]);
                        case "isButtonPressed" -> (int) args[0] == Input.Buttons.LEFT && down;
                        case "isButtonJustPressed" -> (int) args[0] == Input.Buttons.LEFT && justPressed;
                        default -> method.invoke(fallback, args);
                    });
        }
    }
}
