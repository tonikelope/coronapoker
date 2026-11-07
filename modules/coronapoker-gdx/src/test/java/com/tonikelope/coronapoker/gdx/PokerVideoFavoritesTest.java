/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PokerVideoFavoritesTest {
    @TempDir Path directory;
    private static final PokerVideoSearch.Video FIRST =
            new PokerVideoSearch.Video("ABCDEFGHIJK", "Póker: acción y ♠");
    private static final PokerVideoSearch.Video SECOND =
            new PokerVideoSearch.Video("LMNOPQRSTUV", "Second video");

    @Test void savesTitlesAndOrderAcrossSessionsAndPersistsRemoval() throws Exception {
        Path file = directory.resolve("favorites.properties");
        var store = new PokerVideoFavorites(file);
        assertFalse(Files.exists(file));
        store.toggle(FIRST, "shorts");
        store.toggle(SECOND, "shorts");
        var reopened = new PokerVideoFavorites(file);
        assertEquals(List.of(FIRST, SECOND), reopened.videos("shorts"));
        reopened.toggle(FIRST, "shorts");
        assertEquals(List.of(SECOND), new PokerVideoFavorites(file).videos("shorts"));
        try (var siblings = Files.list(directory)) { assertEquals(1, siblings.count()); }
    }

    @Test void startupOffKeepsOnlyTheIconWithoutLoadingUntilItIsClicked() throws Exception {
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                directory.resolve("favorites.properties")), ignored -> { }, false)) {
            overlay.update(true, true, true, PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);
            assertTrue(overlay.hostVisible());
            assertEquals(true, value(overlay, "minimized"));
            assertEquals(false, value(overlay, "initializationStarted"));
            assertNull(value(overlay, "runtime"));
            overlay.releaseForTable();
            overlay.restoreAfterTable();
            overlay.update(true, true, true, PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);
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
        assertThrows(java.io.IOException.class, () -> store.toggle(SECOND, "shorts"));
        assertEquals(corrupt, Files.readString(file));
    }

    @Test void malformedFavoriteEntriesDoNotDiscardValidOnes() throws Exception {
        Path file = directory.resolve("favorites.properties");
        Files.writeString(file, "order=shorts.invalid,unknown.ABCDEFGHIJK,normal.ABCDEFGHIJK\n"
                + "normal.ABCDEFGHIJK=Valid video\n");
        var store = new PokerVideoFavorites(file);
        assertTrue(store.videos("shorts").isEmpty());
        assertEquals(List.of(new PokerVideoSearch.Video("ABCDEFGHIJK", "Valid video")), store.videos("normal"));
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
                overlay.update(available, false, true, PokerVideoIcon.Placement.MENU,
                        "es", "shorts", 1920f, 1080f);
                assertFalse(overlay.hostVisible());
                assertEquals(false, value(overlay, "initializationStarted"));
                assertNull(value(overlay, "runtime"));
            }
        }
    }

    @Test void manuallyEnablingVideosLaterStillOpensEvenWhenStartupIsOff() throws Exception {
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                directory.resolve("favorites.properties")), ignored -> { }, false)) {
            overlay.update(true, false, true, PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);
            prepare(overlay, 260f);
            assertEquals(false, value(overlay, "minimized"));
        }
    }

    @Test void keepsFavoritesSeparateByFormatWithoutLosingTheOtherFormat() throws Exception {
        var store = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        store.toggle(FIRST, "shorts");
        store.toggle(FIRST, "normal");
        store.toggle(FIRST, "shorts");
        assertFalse(store.contains(FIRST, "shorts"));
        assertTrue(store.contains(FIRST, "normal"));
        assertEquals(List.of(FIRST), store.videos("normal"));
    }

    @Test void aFailedSaveDoesNotClaimThatTheVideoWasAdded() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var store = new PokerVideoFavorites(blocked.resolve("favorites.properties"));
        assertThrows(java.io.IOException.class, () -> store.toggle(FIRST, "normal"));
        assertFalse(store.contains(FIRST, "normal"));
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

    @Test void switchingFormatsInFavoritesLoadsTheMatchingSavedVideos() throws Exception {
        var store = new PokerVideoFavorites(directory.resolve("favorites.properties"));
        store.toggle(FIRST, "shorts");
        store.toggle(SECOND, "normal");
        var model = new PokerVideoCatalog(store);
        model.toggleMode();
        assertEquals(List.of(FIRST), model.videos());
        model.setType("normal");
        assertEquals(List.of(SECOND), model.videos());
        model.replaceNormal(List.of(FIRST), "normal");
        assertEquals(List.of(SECOND), model.videos(), "Search completion must not overwrite favorites mode");
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
            click(overlay, "favoritesModeBounds");
            assertFalse(model.favoritesMode());
        }
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
        set(overlay, "videoWidth", width);
        overlay.update(true, true, true, PokerVideoIcon.Placement.MENU, "es", type, 1920f, 1080f);
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
