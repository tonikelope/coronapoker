/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.tonikelope.coronapoker.core.DatabaseService;
import com.tonikelope.coronapoker.core.IdentityTrustStore;
import com.tonikelope.coronapoker.core.LobbyParticipant;
import com.tonikelope.coronapoker.core.LobbySession;
import com.tonikelope.coronapoker.core.LobbySnapshot;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.RecoverableGameRepository;
import com.tonikelope.coronapoker.core.StatsRepository;
import com.tonikelope.coronapoker.core.UpdateService;
import com.tonikelope.coronapoker.core.UpdaterService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

@Tag("certification")
final class GdxFrontendSettingsGpuTest {
    private static final int WIDTH = 960, HEIGHT = 540;

    @Test
    void menuAndLobbySettingsChangeOnPressAndTheImageViewerKeepsModalOwnership(
            @TempDir Path dir) {
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
                    assertEquals(GLFW.GLFW_FALSE,
                            GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    verify(dir);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verify(Path dir) throws Exception {
        try (var prefs = new PreferencesService(dir.resolve("settings.properties"));
                var database = new DatabaseService(dir.resolve("test.sqlite").toString());
                var updates = new UpdateService("27.81", 1, () -> "27.81");
                var updater = new UpdaterService(() -> dir.resolve("unused.jar"), command -> {})) {
            prefs.start();
            database.start();
            updates.start();
            updater.start();
            prefs.properties().setProperty("mostrar_videos_poker", "false");
            var presentation = new GdxGamePresentationSettings(prefs, ModMediaCatalog.discover(dir));
            var screen = new GdxFrontendScreen(prefs,
                    request -> { throw new AssertionError("Unexpected lobby creation"); },
                    new RecoverableGameRepository(database), IdentityTrustStore.unavailable(),
                    new SecureRandom(), session -> {}, () -> {},
                    presentation, new GdxGameText("es"), language -> {},
                    updates, updater, new StatsRepository(database));
            try {
                screen.holdStartupAudio();
                screen.create();
                screen.resize(WIDTH, HEIGHT);
                invoke(screen, "cancelUpdateCheck", new Class<?>[0]);
                screen.completeStartupReveal();
                verifySettings(screen, presentation, GdxSettingsSession.Context.MENU);
                verifyImageViewer(screen);
                try (var lobby = lobby()) {
                    screen.openLobby(lobby);
                    verifySettings(screen, presentation, GdxSettingsSession.Context.WAITING_ROOM);
                }
                verifyLobbyNoticeHandoff(screen);
            } finally {
                screen.dispose();
            }
            verifyLiveTable(prefs, presentation);
        }
    }

    private static void verifyLobbyNoticeHandoff(GdxFrontendScreen screen)
            throws Exception {
        try (var lobby = lobby(LobbySnapshot.Phase.ERROR, "Server connection lost")) {
            screen.openLobby(lobby);
            assertEquals("Server connection lost", field(screen, "frontendNoticeMessage"));
            var table = GdxFinalSummaryNoticeTest.table();
            screen.transferLobbyNoticeTo(table);
            assertEquals("", field(screen, "frontendNoticeMessage"));
            GdxFinalSummaryNoticeTest.close(table,
                    com.tonikelope.coronapoker.table.TableSessionSummary.CloseReason.FAILURE, false);
            assertEquals("Server connection lost",
                    GdxFinalSummaryNoticeTest.activeDialog(table).message());
            assertFalse(table.handleFinalSummaryNavigationAction(0));
            assertTrue(table.resolveActiveDialogChoice(true));
            table.advanceDialogState();
            assertTrue(table.handleFinalSummaryNavigationAction(0));
            screen.returnFromTable(lobby);
            assertEquals("", field(screen, "frontendNoticeMessage"));
        }
    }

    private static void verifySettings(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation, GdxSettingsSession.Context context)
            throws Exception {
        invoke(screen, "openSettings", new Class<?>[]{GdxSettingsContract.Section.class},
                GdxSettingsContract.Section.APPEARANCE);
        var session = (GdxSettingsSession) field(screen, "settingsSession");
        assertEquals(context, session.context());
        for (var section : session.sections()) {
            session.selectTab(session.sections().indexOf(section));
            List<?> pages = (List<?>) invoke(screen, "settingsSubpageLabels", new Class<?>[0]);
            for (int page = 0; page < Math.max(1, pages.size()); page++) {
                invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, page);
                screen.render();
                assertPressHits(screen, context + "/" + section + "/" + page);
            }
        }
        session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.APPEARANCE));
        verifyZoomTracks(screen, screen, screen::render, (java.util.Properties) field(screen, "initialProperties"));
        verifyAngleTrack(screen, screen, screen::render, (java.util.Properties) field(screen, "initialProperties"));
        invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, GdxSettingsContract.appearanceGraphicsPageIndex());
        var modes = presentation.cardRenderingModes();
        assertEquals(List.of(GdxCardSmaaRenderer.RENDERING_BALANCED, GdxCardSmaaRenderer.RENDERING_SMOOTH,
                GdxCardSmaaRenderer.RENDERING_SMOOTH_AF), modes);
        for (int mode : List.of(GdxCardSmaaRenderer.RENDERING_SMOOTH, GdxCardSmaaRenderer.RENDERING_SMOOTH_AF,
                GdxCardSmaaRenderer.RENDERING_BALANCED)) {
            var point = pressOption(screen, "CARD_RENDERING", modes.indexOf(mode));
            assertEquals(mode, presentation.requestedCardRenderingMode());
            screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        }
        invoke(screen, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
        for (String felt : List.of("azul", "verde", "azul", "verde", "azul")) {
            chooseFelt(screen, presentation, felt);
        }
        assertTrue(presentation.secretFelt());
        assertEquals(true, field(screen, "secretFeltTexture"));
        chooseFelt(screen, presentation, "azul");
        assertFalse(presentation.secretFelt());
        assertEquals(false, field(screen, "secretFeltTexture"));
        verifyDeckPreview(screen, presentation);
        invoke(screen, "closeSettings", new Class<?>[]{boolean.class}, false);
    }

    private static void assertPressHits(GdxFrontendScreen screen, String label)
            throws Exception {
        List<?> hits = (List<?>) field(screen, "hits");
        assertFalse(hits.isEmpty(), label);
        for (Object hit : hits) {
            assertNotEquals("RELEASE", field(hit, "activation").toString(), label);
        }
    }

    private static void chooseFelt(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation, String felt) throws Exception {
        var point = pressOption(screen, "FELT", GdxGamePresentationSettings.FELTS.indexOf(felt));
        assertEquals(felt, presentation.felt());
        assertEquals("NONE", field(screen, "dropdown").toString());
        Object texture = field(screen, "feltTexture");
        screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        assertSame(texture, field(screen, "feltTexture"));
    }

    private static void verifyZoomTracks(Object owner, com.badlogic.gdx.InputProcessor input, Runnable render,
            java.util.Properties properties) throws Exception {
        var slider = field(owner, "cardZoomSlider");
        var viewport = (Viewport) field(owner, "viewport");
        var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
        scroll.setAccessible(true);
        var saved = (java.util.Properties) properties.clone();
        try {
            for (var zoom : GdxCardZoom.values()) {
                boolean perspective = zoom == GdxCardZoom.SHUFFLE_3D;
                properties.setProperty(GdxSettingsContract.TABLE_VIEW_KEY, perspective ? "3d" : "2d");
                invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 1);
                var other = GdxCardZoom.shuffle(!perspective);
                int initial = zoom.percent(properties), otherValue = other.percent(properties);
                Rectangle plus = (Rectangle) field(slider, "plus"), minus = (Rectangle) field(slider, "minus");
                for (int offset = 0; offset < 1100; offset += 48) {
                    scroll.setFloat(owner, offset);
                    render.run();
                    if (field(slider, "visible") == zoom && ((Rectangle) field(slider, "hit"))
                            .contains(plus.x + plus.width / 2, plus.y + plus.height / 2)) break;
                }
                assertSame(zoom, field(slider, "visible"));
                for (Rectangle button : List.of(plus, minus)) {
                    var p = viewport.project(button.getCenter(new Vector2()));
                    int x = Math.round(p.x), y = HEIGHT - 1 - Math.round(p.y);
                    assertTrue(input.touchDown(x, y, 0, Input.Buttons.LEFT));
                    assertTrue(input.touchUp(x, y, 0, Input.Buttons.LEFT));
                    assertEquals(button == plus ? initial + 5 : initial, zoom.percent(properties));
                }
                var track = (Rectangle) field(slider, "track");
                var start = viewport.project(new Vector2(track.x, track.y + track.height / 2));
                var end = viewport.project(new Vector2(track.x + track.width, track.y + track.height / 2));
                int y = HEIGHT - 1 - Math.round(start.y);
                assertTrue(input.touchDown(Math.round(start.x), y, 0, Input.Buttons.LEFT));
                assertEquals(GdxCardZoom.MIN, zoom.percent(properties));
                assertTrue(input.touchDragged(Math.round(end.x), y, 0));
                assertTrue(input.touchUp(Math.round(end.x), y, 0, Input.Buttons.LEFT));
                assertEquals(zoom.max, zoom.percent(properties));
                assertEquals(otherValue, other.percent(properties));
            }
        } finally {
            for (var zoom : GdxCardZoom.values()) restoreKey(properties, saved, zoom.key);
            restoreKey(properties, saved, GdxSettingsContract.TABLE_VIEW_KEY);
            scroll.setFloat(owner, 0f);
        }
    }

    private static void restoreKey(java.util.Properties properties, java.util.Properties saved, String key) {
        if (saved.containsKey(key)) properties.setProperty(key, saved.getProperty(key));
        else properties.remove(key);
    }

    private static void verifyAngleTrack(Object owner, com.badlogic.gdx.InputProcessor input, Runnable render,
            java.util.Properties properties) throws Exception {
        invoke(owner, "selectSettingsSubpage", new Class<?>[]{int.class}, 1);
        var slider = field(owner, "shuffleAngleSlider");
        var track = (Rectangle) field(slider, "track");
        var hit = (Rectangle) field(slider, "hit");
        var scroll = owner.getClass().getDeclaredField("settingsAppearanceScroll");
        scroll.setAccessible(true);
        for (int offset = 0; offset < 1000; offset += 64) {
            scroll.setFloat(owner, offset);
            render.run();
            if (hit.height > 32f) break;
        }
        assertTrue(hit.height > 32f, "The angle track must be visible and enabled");
        var viewport = (Viewport) field(owner, "viewport");
        Vector2 start = viewport.project(new Vector2(track.x, hit.y + hit.height * .5f));
        Vector2 end = viewport.project(new Vector2(track.x + track.width + 10f, hit.y + hit.height * .5f));
        int touchY = HEIGHT - 1 - Math.round(start.y);
        assertTrue(input.touchDown((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertEquals(-180, GdxShuffleAngle.value(properties));
        assertTrue(input.touchCancelled((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertTrue(input.touchDown((int) start.x, touchY, 0, Input.Buttons.LEFT));
        assertTrue(input.touchDragged(Math.round(end.x), touchY, 0));
        assertTrue(input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT));
        assertEquals(180, GdxShuffleAngle.value(properties));
        assertTrue(((GdxSettingsSession) field(owner, "settingsSession")).propertiesChanged(properties));
        GdxSettingsContract.restoreAppearancePageDefaults(properties, 1, true);
        assertEquals(45, GdxShuffleAngle.value(properties));
    }

    private static void verifyDeckPreview(GdxFrontendScreen screen,
            GdxGamePresentationSettings presentation) throws Exception {
        var preview = (GdxImagePreview) field(screen, "imagePreview");
        int deck = presentation.availableDecks().indexOf("interstate60");
        for (int selection = 1; selection <= 5; selection++) {
            var point = pressOption(screen, "DECK", deck);
            assertEquals("interstate60", presentation.deck());
            assertEquals(selection == 5, preview.isOpen());
            screen.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        }
        assertEnlargedFrame(preview);
        screen.render();
        assertEquals(1, ((List<?>) field(screen, "hits")).size());
        assertTrue(screen.keyDown(Input.Keys.ESCAPE));
        assertFalse(preview.isOpen());
        assertEquals("SETTINGS", field(screen, "surface").toString());
    }

    private static void verifyMixedDeckProportions(CoronaPokerGdxTable table,
            GdxGamePresentationSettings settings) throws Exception {
        String faceDeck = settings.deck(), cardBack = settings.cardBack();
        try {
            settings.selectDeck("goliat", false);
            settings.selectCardBack("coronapoker", false);
            invoke(table, "synchronizeSelectedDeck", new Class<?>[0]);
            table.render();
            Texture back = (Texture) invoke(table, "activeCardBack", new Class<?>[0]);
            float faceRatio = 1242f / 923f;
            assertNotEquals(faceRatio, back.getHeight() / (float) back.getWidth(), .000001f);
            var seats = (Object[]) field(table, "seats");
            for (int index : new int[]{0, 1}) {
                Object card = invoke(table, "liveHolePlacement", new Class<?>[]{seats[index].getClass(), int.class},
                        seats[index], 0);
                assertEquals(faceRatio, (float) field(card, "height") / (float) field(card, "width"), .000001f);
            }
            assertEquals(faceRatio, (float) invoke(table, "deckCardHeight", new Class<?>[]{float.class}, 1f), .000001f);
        } finally {
            settings.selectDeck(faceDeck, false);
            settings.selectCardBack(cardBack, false);
            invoke(table, "synchronizeSelectedDeck", new Class<?>[0]);
        }
    }

    private static void verifyLiveTable(PreferencesService preferences,
            GdxGamePresentationSettings presentation) throws Exception {
        var player = new TableSnapshot.PlayerSnapshot("human", 20d, 0d, 20d,
                true, false, false, false, -2, -2, 0, 0L, false,
                TableSnapshot.Position.DEALER, "", "", List.of());
        var snapshot = new TableSnapshot(1L, "human", TableSnapshot.Street.PREFLOP,
                0d, "", false, List.of(player), List.of());
        var table = new CoronaPokerGdxTable(60, new GdxTableViewState(snapshot),
                command -> {}, () -> {}, new GdxGameLogSink(), preferences, null, presentation);
        try {
            table.create();
            table.resize(WIDTH, HEIGHT);
            var ready = new CompletableFuture<Void>();
            table.acceptEvent(new TableVisualEvent.PreparationStatus(2L,
                    TableVisualEvent.PreparationStatus.Phase.READY), ready);
            ready.join();
            verifyMixedDeckProportions(table, presentation);
            invoke(table, "openSettings", new Class<?>[0]);
            var session = (GdxSettingsSession) field(table, "settingsSession");
            session.selectTab(session.sections().indexOf(GdxSettingsContract.Section.APPEARANCE));
            verifyZoomTracks(table, table.inputProcessor(), table::render, preferences.properties());
            verifyAngleTrack(table, table.inputProcessor(), table::render, preferences.properties());
            verifyShuffleAngleLock(table, preferences.properties());
            invoke(table, "selectSettingsSubpage", new Class<?>[]{int.class}, 0);
            for (String felt : List.of("azul", "verde", "azul", "verde", "azul")) {
                invoke(table, "selectTableFeltOption", new Class<?>[]{int.class},
                        GdxGamePresentationSettings.FELTS.indexOf(felt));
            }
            assertTrue(presentation.secretFelt());
            assertEquals(true, field(table, "secretFeltTexture"));
            invoke(table, "selectTableFeltOption", new Class<?>[]{int.class},
                    GdxGamePresentationSettings.FELTS.indexOf("azul"));
            assertEquals(false, field(table, "secretFeltTexture"));

            var preview = (GdxImagePreview) field(table, "imagePreview");
            int deck = presentation.availableDecks().indexOf("interstate60");
            for (int selection = 1; selection <= 5; selection++) {
                invoke(table, "selectTableDeckOption", new Class<?>[]{int.class}, deck);
                assertEquals(selection == 5, preview.isOpen());
            }
            assertEnlargedFrame(preview);
            table.render();
            assertTrue((boolean) invoke(table, "hasNestedTableModal", new Class<?>[0]));
            assertEquals(CoronaPokerGdxTable.UI_SETTINGS, field(table, "uiLayer"));
        } finally {
            table.dispose();
        }
    }

    private static void verifyShuffleAngleLock(CoronaPokerGdxTable table, java.util.Properties properties) throws Exception {
        var slider = field(table, "shuffleAngleSlider");
        var track = (Rectangle) field(slider, "track");
        var hit = (Rectangle) field(slider, "hit");
        var viewport = (Viewport) field(table, "viewport");
        var input = table.inputProcessor();
        Vector2 start = viewport.project(new Vector2(track.x + track.width * .7f, hit.y + hit.height * .5f));
        Vector2 end = viewport.project(new Vector2(track.x, hit.y + hit.height * .5f));
        int touchY = HEIGHT - 1 - Math.round(start.y);
        assertTrue(input.touchDown(Math.round(start.x), touchY, 0, Input.Buttons.LEFT));
        int initialAngle = GdxShuffleAngle.value(properties);
        assertTrue(initialAngle > 60 && initialAngle < 80);
        properties.setProperty(GdxCardZoom.SHUFFLE_2D.key, "115");
        properties.setProperty(GdxCardZoom.SHUFFLE_3D.key, "175");
        var view = GdxAppearanceOptions.SHUFFLE_VIEW;
        properties.setProperty(view.key(), "3d");
        var started = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(3, "goliat", TableVisualEvent.Shuffle.Phase.START), started);
        assertTrue(started.isDone());
        var shuffle = field(table, "liveShuffle");
        assertEquals(initialAngle, field(shuffle, "angle"));
        assertEquals(true, field(shuffle, "tiltedOnFlatTable"));
        invoke(table, "selectTableAppearanceChoice",
                new Class<?>[]{GdxAppearanceOptions.Choice.class, int.class}, view, 0);
        assertEquals("3d", properties.getProperty(view.key()));
        assertEquals(115, field(shuffle, "zoom2D"));
        assertEquals(175, field(shuffle, "zoom3D"));
        properties.setProperty(GdxCardZoom.SHUFFLE_3D.key, "250");
        assertEquals(175, invoke(table, "shuffleZoom", new Class<?>[]{boolean.class}, true));
        input.touchDragged(Math.round(end.x), touchY, 0);
        input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        table.render();
        assertNull(field(field(table, "cardZoomSlider"), "visible"));
        assertEquals(0f, hit.width);
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        invoke(table, "restoreTableAppearanceDefaults", new Class<?>[0]);
        assertEquals(HEIGHT, Gdx.graphics.getHeight(), "Resetting animations must preserve the window size");
        assertEquals("3d", properties.getProperty(view.key()));
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        // Cancelling uncommitted settings may restore the next hand's preference.
        properties.setProperty(GdxShuffleAngle.KEY, "15");
        assertEquals(initialAngle, invoke(table, "shuffleAngle", new Class<?>[0]));
        properties.setProperty(GdxShuffleAngle.KEY, Integer.toString(initialAngle));
        // Advance the animation clock across the first pass, still awaiting FINISH.
        float cycle = (float) field(shuffle, "cycleSeconds");
        var epoch = shuffle.getClass().getDeclaredField("startedAtNanos");
        epoch.setAccessible(true);
        epoch.setLong(shuffle, System.nanoTime() - (long) ((cycle + .25) * 1_000_000_000L));
        table.render();
        table.render();
        assertTrue((boolean) field(shuffle, "firstEndPresented"));
        assertSame(shuffle, field(table, "liveShuffle"));
        assertEquals(0f, hit.width);
        input.touchDown(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        input.touchUp(Math.round(end.x), touchY, 0, Input.Buttons.LEFT);
        assertEquals(initialAngle, GdxShuffleAngle.value(properties));
        var finished = new CompletableFuture<Void>();
        table.acceptEvent(new TableVisualEvent.Shuffle(4, "goliat", TableVisualEvent.Shuffle.Phase.FINISH), finished);
        table.render();
        assertTrue(finished.isDone());
        assertNull(field(table, "liveShuffle"));
        assertTrue(hit.width > 0f);
        assertTrue(input.touchDown((int) end.x, touchY, 0, Input.Buttons.LEFT));
        input.touchUp((int) end.x, touchY, 0, Input.Buttons.LEFT);
        assertEquals(-180, GdxShuffleAngle.value(properties));
        GdxSettingsContract.restoreAppearancePageDefaults(properties, 1, true);
    }

    private static void assertEnlargedFrame(GdxImagePreview preview) throws InterruptedException {
        Texture frame = null;
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (frame == null && System.nanoTime() < deadline) {
            frame = preview.frame();
            if (frame == null) Thread.sleep(5);
        }
        assertNotNull(frame, "The enlarged animation must reach the renderer");
        assertEquals(632, frame.getWidth());
        assertEquals(502, frame.getHeight());
    }

    private static Vector2 pressOption(GdxFrontendScreen screen, String dropdown, int row)
            throws Exception {
        assertTrue(row >= 0 && row < GdxSettingsLayout.POPUP_MAX_VISIBLE_ROWS);
        Class<?> dropdownType = field(screen, "dropdown").getClass();
        Object target = List.of(dropdownType.getEnumConstants()).stream()
                .filter(value -> value.toString().equals(dropdown)).findFirst().orElseThrow();
        invoke(screen, "openDropdown", new Class<?>[]{
                dropdownType, float.class, float.class, float.class, float.class},
                target, 950f, 600f, 400f, 72f);
        screen.render();
        Rectangle menu = (Rectangle) field(screen, "dropdownClip");
        var point = screenPoint(screen, menu.x + 40f, menu.y + menu.height
                - (row + .5f) * GdxSettingsLayout.POPUP_ROW_HEIGHT);
        assertTrue(screen.touchDown((int) point.x, (int) point.y, 0, Input.Buttons.LEFT));
        return point;
    }

    private static void verifyImageViewer(GdxFrontendScreen screen) throws Exception {
        invoke(screen, "openAboutDialog", new Class<?>[0]);
        for (boolean alternate : new boolean[]{false, true}) {
            for (int count = 0; count < 5; count++) {
                invoke(screen, "activateAboutEasterEgg",
                        new Class<?>[]{boolean.class}, alternate);
            }
            Texture visible = (Texture) field(screen, "aboutEasterEggTexture");
            assertNotNull(visible);
            screen.render();
            assertTrue((boolean) invoke(screen, "hasNestedFrontendModal", new Class<?>[0]));
            assertEquals(1, ((List<?>) field(screen, "hits")).size());
            assertTrue(screen.keyDown(Input.Keys.ESCAPE));
            assertNull(field(screen, "aboutEasterEggTexture"));
            assertEquals(true, field(screen, "aboutOpen"));
        }
        invoke(screen, "closeAboutDialog", new Class<?>[0]);
    }

    private static Vector2 screenPoint(GdxFrontendScreen screen, float x, float y)
            throws Exception {
        var viewport = (Viewport) field(screen, "viewport");
        Vector2 point = viewport.project(new Vector2(x, y));
        return point.set(point.x, HEIGHT - 1f - point.y);
    }

    private static LobbySession lobby() {
        return lobby(LobbySnapshot.Phase.WAITING_FOR_PLAYERS, "");
    }

    private static LobbySession lobby(LobbySnapshot.Phase phase, String detail) {
        return new LobbySession(new LobbySnapshot("human", "human", "127.0.0.1:2345",
                true, phase, detail,
                List.of(new LobbyParticipant("human", null, true, true,
                        false, true, false, true, 0, 0)),
                List.of(), null, false), command -> CompletableFuture.completedFuture(null));
    }

    private static Object field(Object receiver, String name) throws Exception {
        var field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static Object invoke(Object receiver, String name, Class<?>[] parameters,
            Object... arguments) throws Exception {
        var method = receiver.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(receiver, arguments);
    }
}
