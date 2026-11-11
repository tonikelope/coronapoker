package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.math.Rectangle;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PokerVideoChatLinkTest {

    @TempDir
    Path temp;

    @Test
    void linkPreservesTheVideoIdTypeAndTitle() {
        var video = new PokerVideoSearch.Video("abcdefghijk",
                "Póker: la mano final", PokerVideoSearch.TYPE_SHORTS);
        assertEquals(video, PokerVideoChatLink.parse(PokerVideoChatLink.format(video)));
    }

    @Test
    void onlyWellFormedPokerVideoTokensBecomeLinks() {
        assertNull(PokerVideoChatLink.parse("https://example.invalid/video"));
        assertNull(PokerVideoChatLink.parse(
                "coronapoker-video:1:normal:https://example.invalid/video"));
        assertNull(PokerVideoChatLink.parse(
                "coronapoker-video:1:external:abcdefghijk:Partida"));
    }

    @Test
    void sharedSelectionCanReplaceTheCurrentVideoAndReturnToTheCarousel() {
        var catalog = new PokerVideoCatalog(new PokerVideoFavorites(
                temp.resolve("favorites.json")));
        var regular = new PokerVideoSearch.Video("abcdefghijk", "Carrusel");
        var first = new PokerVideoSearch.Video("lmnopqrstuv", "Primer enlace");
        var second = new PokerVideoSearch.Video("0123456789_", "Segundo enlace",
                PokerVideoSearch.TYPE_SHORTS);
        catalog.replaceNormal(List.of(regular), PokerVideoSearch.TYPE_NORMAL);
        catalog.showShared(first);
        assertEquals(List.of(first), catalog.videos());
        catalog.showShared(second);
        assertEquals(List.of(second), catalog.videos());
        catalog.resumeCatalog();
        assertEquals(List.of(regular), catalog.videos());
    }

    @Test
    void linkReplacesTheRuntimeSelectionWithoutWaitingForDiscovery() throws Exception {
        var constructor = PokerVideoRuntime.class.getDeclaredConstructor(List.class,
                PokerVideoFrameBuffer.class, String.class);
        constructor.setAccessible(true);
        var playing = new PokerVideoSearch.Video("abcdefghijk", "En reproducción");
        var shared = new PokerVideoSearch.Video("lmnopqrstuv", "Compartido",
                PokerVideoSearch.TYPE_SHORTS);
        var runtime = (PokerVideoRuntime) constructor.newInstance(
                List.of(playing), new PokerVideoFrameBuffer(), "es");
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                temp.resolve("favorites.json")), ignored -> {}, false)) {
            overlay.update(true, true, false, "es", PokerVideoSearch.TYPE_NORMAL,
                    1920f, 1080f);
            var field = PokerVideoOverlay.class.getDeclaredField("runtime");
            field.setAccessible(true);
            field.set(overlay, runtime);
            overlay.openSharedVideo(shared);
            overlay.update(true, true, false, "es", PokerVideoSearch.TYPE_NORMAL,
                    1920f, 1080f);
            assertEquals(shared, runtime.selection().video());
        }
    }

    @Test
    void shareButtonFitsToTheRightOfFavoritesEvenAtMinimumWidth() throws Exception {
        try (var overlay = new PokerVideoOverlay(new PokerVideoFavorites(
                temp.resolve("favorites.json")), ignored -> {}, false)) {
            overlay.setShareAction(ignored -> {});
            var width = PokerVideoOverlay.class.getDeclaredField("videoWidth");
            width.setAccessible(true);
            width.setFloat(overlay, PokerVideoOverlay.MIN_WIDTH);
            var layout = PokerVideoOverlay.class.getDeclaredMethod("updateBounds");
            layout.setAccessible(true);
            layout.invoke(overlay);
            Rectangle favorites = bounds(overlay, "favoritesModeBounds");
            Rectangle share = bounds(overlay, "shareBounds");
            Rectangle opacity = bounds(overlay, "opacityBounds");
            assertEquals(32f, favorites.width);
            assertEquals(favorites.x + favorites.width + 5f, share.x);
            assertEquals(share.x + share.width + 5f, opacity.x);
        }
    }

    private static Rectangle bounds(PokerVideoOverlay overlay, String name)
            throws Exception {
        var field = PokerVideoOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Rectangle) field.get(overlay);
    }
}
