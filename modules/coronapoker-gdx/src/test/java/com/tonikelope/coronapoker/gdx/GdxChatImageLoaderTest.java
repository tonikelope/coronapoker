package com.tonikelope.coronapoker.gdx;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void purgingSearchPreviewsClearsOnlyTheirFilesAndMemory(@TempDir Path home) throws Exception {
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            Path search = Files.createDirectories(home.resolve(".coronapoker/SearchImagesCache"));
            Path chat = Files.createDirectories(home.resolve(".coronapoker/ChatImagesCache"));
            String preview = "https://example.invalid/preview-only.gif";
            String usedInChat = "https://example.invalid/saved-to-chat.gif";
            Path previewFile = search.resolve(GdxChatImageLoader.cacheFileName(URI.create(preview).toURL()));
            String sharedName = GdxChatImageLoader.cacheFileName(URI.create(usedInChat).toURL());
            byte[] original = {1, 2, 3};
            assertEquals(0, GdxChatImageLoader.searchCacheSize());
            Files.write(previewFile, original);
            Files.write(search.resolve(sharedName), original);
            Files.createDirectory(search.resolve("ignored-directory"));
            assertEquals(6, GdxChatImageLoader.searchCacheSize());
            assertArrayEquals(original, GdxChatImageLoader.downloadSearchPreview(preview));
            assertArrayEquals(original, GdxChatImageLoader.downloadPublic(usedInChat));
            assertArrayEquals(original, Files.readAllBytes(chat.resolve(sharedName)));

            GdxChatImageLoader.purgeSearchCache();
            assertEquals(0, GdxChatImageLoader.searchCacheSize());
            assertFalse(Files.exists(previewFile));
            assertFalse(Files.exists(search.resolve(sharedName)));
            assertArrayEquals(original, Files.readAllBytes(chat.resolve(sharedName)));

            byte[] replacement = {4, 5, 6};
            Files.write(previewFile, replacement);
            assertArrayEquals(replacement, GdxChatImageLoader.downloadSearchPreview(preview));
            assertArrayEquals(original, GdxChatImageLoader.downloadSearchPreview(usedInChat));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }
}
