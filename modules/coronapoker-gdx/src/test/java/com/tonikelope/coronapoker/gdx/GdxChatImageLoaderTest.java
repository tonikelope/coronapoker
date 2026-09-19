package com.tonikelope.coronapoker.gdx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
