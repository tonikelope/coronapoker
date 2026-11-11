package com.tonikelope.coronapoker.gdx;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxChatImageLoaderTest {

    @Test
    void decodesSwingChatImageTransportUrls() {
        assertEquals("http://example.invalid/a.png",
                GdxChatImageLoader.decodeChatImageUrl(
                        "img://example.invalid/a.png"));
        assertEquals("https://example.invalid/a.gif",
                GdxChatImageLoader.decodeChatImageUrl(
                        "imgs://example.invalid/a.gif"));
    }

    @Test
    void keepsRawUrlsButDoesNotInterpretAnInventedPreviewProtocol() {
        assertEquals("https://example.invalid/a.gif",
                GdxChatImageLoader.decodeChatImageUrl(
                        "https://example.invalid/a.gif"));
        assertThrows(IllegalArgumentException.class,
                () -> GdxChatImageLoader.decodeChatImageUrl(
                        "img://https://example.invalid/a.gif"));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void purgingBothCachesIncludesGifsReusedFromChatAndPreservesFavorites(@TempDir Path home) throws Exception {
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            Path search = home.resolve(".coronapoker/SearchImagesCache");
            Path chat = Files.createDirectories(home.resolve(".coronapoker/ChatImagesCache"));
            String reused = "https://example.invalid/reused.gif";
            String preview = "https://example.invalid/search-only.gif";
            String favorite = "https://example.invalid/favorite.gif";
            List<String> favorites = List.of(favorite + "#saved");
            byte[] gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAAKAAAALAAAAAABAAEAAAICRAEAIfkEAAoAAAAsAAAAAAEAAQAAAgJMAQA7");
            Path chatGif = chat.resolve(cacheFileName(reused));
            Path searchGif = search.resolve(cacheFileName(preview));
            Path chatFavorite = chat.resolve(cacheFileName(favorite));
            Path searchFavorite = search.resolve(cacheFileName(favorite));

            assertEquals(0, GdxChatImageLoader.imageCacheSize(favorites));
            Files.write(chatGif, gif);
            Files.write(chatFavorite, gif);
            assertArrayEquals(gif, GdxChatImageLoader.downloadSearchPreview(reused));
            assertFalse(Files.exists(search));
            assertEquals(gif.length, GdxChatImageLoader.imageCacheSize(favorites));

            Files.createDirectory(search);
            Files.createDirectory(search.resolve("ignored-directory"));
            Files.write(searchGif, gif);
            Files.write(searchFavorite, gif);
            assertArrayEquals(gif, GdxChatImageLoader.downloadSearchPreview(preview));
            assertEquals(2L * gif.length, GdxChatImageLoader.imageCacheSize(favorites));
            assertEquals(2L * gif.length, GdxChatImageLoader.purgeImageCache(favorites));
            assertEquals(0, GdxChatImageLoader.imageCacheSize(favorites));
            assertEquals(0, GdxChatImageLoader.purgeImageCache(favorites));
            assertFalse(Files.exists(chatGif));
            assertFalse(Files.exists(searchGif));
            assertTrue(Files.isDirectory(search.resolve("ignored-directory")));
            assertArrayEquals(gif, Files.readAllBytes(chatFavorite));
            assertArrayEquals(gif, Files.readAllBytes(searchFavorite));

            byte[] replacement = {4, 5, 6};
            Files.write(chatGif, replacement);
            Files.write(searchGif, replacement);
            assertArrayEquals(replacement, GdxChatImageLoader.downloadSearchPreview(reused));
            assertArrayEquals(replacement, GdxChatImageLoader.downloadSearchPreview(preview));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    private static String cacheFileName(String url) throws Exception {
        return GdxChatImageLoader.cacheFileName(URI.create(url).toURL());
    }
}
