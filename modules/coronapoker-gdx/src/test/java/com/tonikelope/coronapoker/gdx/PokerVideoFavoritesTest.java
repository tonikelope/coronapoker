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
