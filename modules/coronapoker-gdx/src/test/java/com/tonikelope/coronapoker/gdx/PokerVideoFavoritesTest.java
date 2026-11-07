/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Rectangle;
import com.tonikelope.coronapoker.core.PreferencesService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PokerVideoFavoritesTest {
    @TempDir Path directory;
    private static final PokerVideoSearch.Video FIRST =
            new PokerVideoSearch.Video("ABCDEFGHIJK", "Póker: acción y ♠");
    private static final PokerVideoSearch.Video SECOND =
            new PokerVideoSearch.Video("LMNOPQRSTUV", "Second video");
    private static final PokerVideoSearch.Video FIRST_SHORT =
            new PokerVideoSearch.Video(FIRST.id(), FIRST.title(), "shorts");

    @Test void savesTitlesAndOrderAcrossSessionsAndPersistsRemoval() throws Exception {
        Path file = directory.resolve("favorites.properties");
        var store = new PokerVideoFavorites(file);
        assertFalse(Files.exists(file));
        store.toggle(FIRST_SHORT);
        store.toggle(SECOND);
        var reopened = new PokerVideoFavorites(file);
        assertEquals(List.of(FIRST_SHORT, SECOND), reopened.videos());
        reopened.toggle(FIRST_SHORT);
        assertEquals(List.of(SECOND), new PokerVideoFavorites(file).videos());
        try (var siblings = Files.list(directory)) { assertEquals(1, siblings.count()); }
    }

    @Test void startupOffKeepsOnlyTheIconWithoutLoadingUntilItIsClicked() throws Exception {
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                directory.resolve("favorites.properties")), ignored -> { }, false)) {
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertTrue(overlay.hostVisible());
            assertEquals(true, value(overlay, "minimized"));
            assertEquals(false, value(overlay, "initializationStarted"));
            assertNull(value(overlay, "runtime"));
            overlay.releaseForTable();
            overlay.restoreAfterTable();
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertEquals(true, value(overlay, "minimized"));
            assertEquals(false, value(overlay, "initializationStarted"));
            click(overlay, "iconBounds");
            assertEquals(false, value(overlay, "minimized"));
        }
    }

    @Test void corruptFavoritesCannotOverwriteTheExistingFile() throws Exception {
        Path file = directory.resolve("favorites.properties");
        String corrupt = "order=shorts.ABCDEFGHIJK\nshorts.ABCDEFGHIJK=\\uZZZZ\n";
        Files.writeString(file, corrupt);
        var store = new PokerVideoFavorites(file);
        assertThrows(java.io.IOException.class, () -> store.toggle(SECOND));
        assertEquals(corrupt, Files.readString(file));
    }

    @Test void malformedFavoriteEntriesDoNotDiscardValidOnes() throws Exception {
        Path file = directory.resolve("favorites.properties");
        Files.writeString(file, "order=shorts.invalid,unknown.ABCDEFGHIJK,normal.ABCDEFGHIJK\n"
                + "normal.ABCDEFGHIJK=Valid video\n");
        var store = new PokerVideoFavorites(file);
        assertEquals(List.of(new PokerVideoSearch.Video("ABCDEFGHIJK", "Valid video")), store.videos());
    }

    @Test void failedCatalogueOffersRetryWithoutLosingOtherControls() throws Exception {
        try (var overlay = overlay(260f)) {
            set(overlay, "loadingError", "Network unavailable");
            click(overlay, "retryBounds");
            assertNull(value(overlay, "loadingError"));
            assertEquals(false, value(overlay, "initializationStarted"));
            assertNull(value(overlay, "requestedCatalogKey"));
        }
    }

    @Test void closingDuringSearchCancelsItButDoesNotInterruptNativeInitialization() throws Exception {
        for (boolean nativePhase : new boolean[]{false, true}) {
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
            var task = new java.util.concurrent.FutureTask<Void>(() -> {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException stopped) { interrupted.set(true); }
                return null;
            });
            var worker = new Thread(task, "video-cancellation-regression");
            try (var overlay = overlay(260f)) {
                set(overlay, "initializationTask", task);
                set(overlay, "nativeStartupSince", nativePhase ? 1L : 0L);
                set(overlay, "catalogLoading", true);
                worker.start();
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                click(overlay, "closeBounds");
                assertTrue(task.isCancelled());
                release.countDown();
                worker.join(2000);
                assertFalse(worker.isAlive());
                assertEquals(!nativePhase, interrupted.get());
                assertEquals(false, value(overlay, "initializationStarted"));
            } finally { release.countDown(); worker.join(2000); }
        }
    }

    @Test void windowAnimationRunsBothWaysAndCanBeDisabled() throws Exception {
        try (var overlay = overlay(260f)) {
            set(overlay, "minimized", true);
            overlay.updateWindowAnimation(true, 0f);
            click(overlay, "iconBounds");
            assertEquals(0f, value(overlay, "transitionElapsed"));
            overlay.updateWindowAnimation(true, 1f);
            click(overlay, "closeBounds");
            assertEquals(0f, value(overlay, "transitionElapsed"));
            assertEquals(true, value(overlay, "minimized"));
            overlay.updateWindowAnimation(false, 0f);
            Object complete = value(overlay, "transitionElapsed");
            click(overlay, "iconBounds");
            assertEquals(false, value(overlay, "minimized"));
            assertEquals(complete, value(overlay, "transitionElapsed"));
            click(overlay, "closeBounds");
            assertEquals(true, value(overlay, "minimized"));
            assertEquals(complete, value(overlay, "transitionElapsed"));
        }
    }

    @Test void featureAndAnimationSwitchesDominateTheStartupOption() throws Exception {
        for (boolean available : new boolean[]{false, true}) {
            try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                    directory.resolve("favorites.properties")), ignored -> { }, true)) {
                overlay.update(available, false, true, "es", "shorts", 1920f, 1080f);
                assertFalse(overlay.hostVisible());
                assertEquals(false, value(overlay, "initializationStarted"));
                assertNull(value(overlay, "runtime"));
            }
        }
    }

    @Test void manuallyEnablingVideosLaterStillOpensEvenWhenStartupIsOff() throws Exception {
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                directory.resolve("favorites.properties")), ignored -> { }, false)) {
            overlay.update(true, false, true, "es", "shorts", 1920f, 1080f);
            prepare(overlay, 260f);
            assertEquals(false, value(overlay, "minimized"));
        }
    }

    @Test void keepsFavoritesSeparateByFormatWithoutLosingTheOtherFormat() throws Exception {
        var store = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        store.toggle(FIRST_SHORT);
        store.toggle(FIRST);
        assertEquals(List.of(FIRST_SHORT, FIRST), store.videos());
        store.toggle(FIRST_SHORT);
        assertFalse(store.contains(FIRST_SHORT));
        assertTrue(store.contains(FIRST));
        assertEquals(List.of(FIRST), store.videos());
    }

    @Test void aFailedSaveDoesNotClaimThatTheVideoWasAdded() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var store = new PokerVideoFavorites(blocked.resolve("favorites.properties"));
        assertThrows(java.io.IOException.class, () -> store.toggle(FIRST));
        assertFalse(store.contains(FIRST));
        assertEquals("not a directory", Files.readString(blocked));
    }

    @Test void favoriteCarouselCanBecomeEmptyAndReturnToTheSameNormalCatalogue() throws Exception {
        var model = new PokerVideoCatalog(new PokerVideoFavorites(directory.resolve("favorites.properties")));
        List<PokerVideoSearch.Video> normal = List.of(SECOND, FIRST);
        model.replaceNormal(normal, "shorts");
        long originalRevision = model.revision();
        model.toggleFavorite(FIRST);
        assertEquals(originalRevision, model.revision(), "Starring must not reload the current video");
        assertEquals(normal, model.videos());
        model.toggleMode();
        assertEquals(List.of(FIRST), model.videos());
        model.toggleFavorite(FIRST);
        assertTrue(model.videos().isEmpty());
        model.toggleMode();
        assertEquals(normal, model.videos());
    }

    @Test void favoritesKeepBothFormatsInOrderRegardlessOfTheSearchPreference() throws Exception {
        var store = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        store.toggle(FIRST_SHORT);
        store.toggle(SECOND);
        var model = new PokerVideoCatalog(store);
        model.toggleMode();
        long revision = model.revision();
        assertEquals(List.of(FIRST_SHORT, SECOND), model.videos());
        model.setType("shorts");
        assertEquals(revision, model.revision());
        assertEquals(List.of(FIRST_SHORT, SECOND), model.videos());
        model.setType("normal");
        model.replaceNormal(List.of(FIRST), "normal");
        assertEquals(List.of(FIRST_SHORT, SECOND), model.videos(), "Search must not overwrite favorites");
        model.toggleMode();
        assertEquals(List.of(FIRST), model.videos());
    }

    @Test void controlsStayBelowTheVideoAtCompactAndLandscapeSizes() throws Exception {
        for (float width : new float[]{260f, 900f}) {
            try (var overlay = overlay(width)) {
                Rectangle video = bounds(overlay, "videoBounds"), footer = bounds(overlay, "footerBounds");
                Rectangle header = bounds(overlay, "headerBounds");
                assertEquals(footer.y + footer.height, video.y, 0.01f);
                assertEquals(video.y + video.height, header.y, 0.01f);
                Rectangle previous = null;
                for (String name : List.of("favoriteBounds", "favoritesModeBounds", "videoTypeBounds")) {
                    Rectangle button = bounds(overlay, name);
                    assertTrue(footer.contains(button), name);
                    assertFalse(video.overlaps(button), name);
                    if (previous != null) assertFalse(previous.overlaps(button));
                    previous = button;
                }
                assertTrue(header.contains(bounds(overlay, "closeBounds")));
                Rectangle star = bounds(overlay, "favoriteBounds");
                Rectangle favorites = bounds(overlay, "favoritesModeBounds");
                Rectangle format = bounds(overlay, "videoTypeBounds");
                assertEquals(footer.x + 8f, star.x, 0.01f);
                assertEquals(star.x + star.width + 5f, favorites.x, 0.01f);
                assertEquals(footer.x + footer.width - 8f, format.x + format.width, 0.01f);
            }
        }
    }

    @Test void formatButtonUsesThePreferenceCallbackAndFavoritesButtonTogglesBack() throws Exception {
        var requested = new AtomicReference<String>();
        try (var overlay = new PokerVideoOverlay(
                new PokerVideoFavorites(directory.resolve("favorites.properties")), requested::set)) {
            prepare(overlay, 260f);
            click(overlay, "videoTypeBounds");
            assertEquals("normal", requested.get());
            prepare(overlay, 900f);
            click(overlay, "videoTypeBounds");
            assertEquals("shorts", requested.get());
            var field = PokerVideoOverlay.class.getDeclaredField("catalog");
            field.setAccessible(true);
            PokerVideoCatalog model = (PokerVideoCatalog) field.get(overlay);
            click(overlay, "favoritesModeBounds");
            assertTrue(model.favoritesMode());
            var tooltips = new java.util.ArrayList<String>();
            overlay.addTooltips((rectangle, label) -> tooltips.add(label));
            assertFalse(tooltips.contains("gdx.video.type.shorts"));
            click(overlay, "favoritesModeBounds");
            assertFalse(model.favoritesMode());
        }
    }

    @Test void changingFavoriteTypeRestoresTheCorrectLayoutWithoutChangingThePreference()
            throws Exception {
        var requested = new AtomicReference<String>();
        var store = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        store.toggle(FIRST_SHORT);
        store.toggle(SECOND);
        var constructor = PokerVideoRuntime.class.getDeclaredConstructor(
                List.class, PokerVideoFrameBuffer.class, String.class);
        constructor.setAccessible(true);
        try (var overlay = new PokerVideoOverlay(store, requested::set)) {
            var model = (PokerVideoCatalog) value(overlay, "catalog");
            model.replaceNormal(List.of(SECOND), "normal");
            model.toggleMode();
            var runtime = constructor.newInstance(model.videos(), new PokerVideoFrameBuffer(), "es");
            set(overlay, "runtime", runtime);
            set(overlay, "appliedCatalogRevision", model.revision());
            set(overlay, "appliedLanguage", "es");
            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            assertEquals(330f, bounds(overlay, "panelBounds").width);
            assertEquals(new PokerVideoRuntime.Selection(FIRST_SHORT, 0, 2),
                    value(overlay, "displayedSelection"));

            var message = PokerVideoRuntime.class.getDeclaredMethod("handlePlayerMessage", String.class);
            message.setAccessible(true);
            message.invoke(runtime, "INDEX|1:1:0");
            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            assertEquals(514f, bounds(overlay, "panelBounds").width);
            assertEquals(new PokerVideoRuntime.Selection(SECOND, 1, 2),
                    value(overlay, "displayedSelection"));
            model.toggleMode();
            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            message.invoke(runtime, "INDEX|1:1:0");
            assertEquals(new PokerVideoRuntime.Selection(SECOND, 0, 1), runtime.selection());
            assertNull(requested.get());
        }
    }

    @Test void separateWindowSizesAndPositionsSurviveARealPreferencesRestart() throws Exception {
        Path file = directory.resolve("coronapoker.properties");
        var favorites = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        Rectangle shorts, normal;
        try (var preferences = new PreferencesService(file)) {
            preferences.start();
            try (var overlay = new PokerVideoOverlay(favorites, ignored -> { }, true,
                    preferences.properties(), preferences::saveDeferred)) {
                set(overlay, "initializationStarted", true);
                set(overlay, "requestedCatalogKey", "es:shorts");
                overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
                moveAndResize(overlay, -160f);
                shorts = new Rectangle(bounds(overlay, "panelBounds"));
                set(overlay, "requestedCatalogKey", "es:normal");
                overlay.update(true, true, true, "es", "normal", 1920f, 1080f);
                moveAndResize(overlay, -80f);
                normal = new Rectangle(bounds(overlay, "panelBounds"));
            }
        }
        try (var preferences = new PreferencesService(file);
                var overlay = new PokerVideoOverlay(favorites, ignored -> { }, true,
                        preferences.properties(), preferences::saveDeferred)) {
            overlay.update(true, true, false, "es", "shorts", 1920f, 1080f);
            assertEquals(shorts, bounds(overlay, "panelBounds"));
            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            assertEquals(normal, bounds(overlay, "panelBounds"));
        }
    }

    private static void moveAndResize(PokerVideoOverlay overlay, float shift) throws Exception {
        Rectangle panel = bounds(overlay, "panelBounds");
        float titleX = panel.x + panel.width / 2f;
        float titleY = panel.y + panel.height - PokerVideoOverlay.HEADER_HEIGHT / 2f;
        assertTrue(overlay.touchDown(titleX, titleY, 0, 0));
        assertTrue(overlay.touchDragged(titleX + shift, titleY + 13f, 0));
        assertTrue(overlay.touchUp(titleX + shift, titleY + 13f, 0, 0));
        float edgeX = panel.x + 2f, edgeY = panel.y + panel.height / 2f;
        assertTrue(overlay.touchDown(edgeX, edgeY, 0, 0));
        assertTrue(overlay.touchDragged(edgeX - 20f, edgeY, 0));
        assertTrue(overlay.touchUp(edgeX - 20f, edgeY, 0, 0));
    }

    private PokerVideoOverlay overlay(float width) throws Exception {
        var overlay = new PokerVideoOverlay(new PokerVideoFavorites(directory.resolve("favorites.properties")), ignored -> { });
        prepare(overlay, width);
        return overlay;
    }

    private static void prepare(PokerVideoOverlay overlay, float width) throws Exception {
        String type = width > 260f ? "normal" : "shorts";
        set(overlay, "initializationStarted", true);
        set(overlay, "requestedCatalogKey", "es:" + type);
        Properties preferences = (Properties) value(overlay, "windowPreferences");
        preferences.setProperty("poker_video_window_" + type, "400,100," + width);
        set(overlay, "geometryInitialized", false);
        overlay.update(true, true, true, "es", type, 1920f, 1080f);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object value(PokerVideoOverlay overlay, String name) throws Exception {
        var field = PokerVideoOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(overlay);
    }

    private static Rectangle bounds(PokerVideoOverlay overlay, String name) throws Exception {
        var field = PokerVideoOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Rectangle) field.get(overlay);
    }

    private static void click(PokerVideoOverlay overlay, String name) throws Exception {
        Rectangle button = bounds(overlay, name);
        float x = button.x + button.width / 2f, y = button.y + button.height / 2f;
        assertTrue(overlay.touchDown(x, y, 0, 0));
        assertTrue(overlay.touchUp(x, y, 0, 0));
    }
}
