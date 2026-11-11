package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GoogleImageResultsTest {

    @Test
    void retryAfterUsesTheProvidersDelayOrDateAndIgnoresInvalidValues() {
        long receivedAt = Instant.parse("2026-11-11T17:00:00Z").toEpochMilli();
        assertEquals(receivedAt + 5_000, GoogleImageResults.retryAt(" 5 ", receivedAt));
        assertEquals(receivedAt + 10_000,
                GoogleImageResults.retryAt("Wed, 11 Nov 2026 17:00:10 GMT", receivedAt));
        assertEquals(0, GoogleImageResults.retryAt("Wed, 11 Nov 2026 16:59:59 GMT", receivedAt));
        assertEquals(0, GoogleImageResults.retryAt("0", receivedAt));
        assertEquals(0, GoogleImageResults.retryAt("-5", receivedAt));
        assertEquals(0, GoogleImageResults.retryAt("9999999999999999999", receivedAt));
        assertEquals(0, GoogleImageResults.retryAt(null, receivedAt));
    }

    @Test
    void renderedImagePairsRetainTheOriginalAndRemoveDuplicates() {
        String item = """
                [1,[0,"F78",["https://encrypted-tbn0.gstatic.com/images?q\\u003dtbn:abc\\u0026s\\u003d10",447,447],\
                ["https://static.example.org/photo.png?v\\u003d1",1200,1200],null]]
                """;
        String unrelated = """
                ["https://example.org/thumb.jpg",2,2],["https://example.org/photo.jpg",8,8]
                """;
        assertEquals(List.of(new GoogleImageResults.Image(
                "https://static.example.org/photo.png?v=1",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:abc&s=10",
                "static.example.org", false)),
                GoogleImageResults.parseDocument(unrelated + item + item));
    }

    @Test
    void animationUsesTheResultMetadataAndRecognizesGifExtensions() {
        String items = """
                ["https://encrypted-tbn0.gstatic.com/images?q=tbn:abc",24,30],
                ["https://static.example.org/download/5500",48,60],null,4,"rgb(208,144,157)",null,1,{},
                ["https://encrypted-tbn0.gstatic.com/images?q=tbn:def",24,30],
                ["https://static.example.org/photo.GIF?v=1",48,60],null
                """;
        var images = GoogleImageResults.parseDocument(items);
        assertEquals(2, images.size());
        assertTrue(images.stream().allMatch(GoogleImageResults.Image::animated));
    }

    @Test
    void aProviderBatchRetainsMoreThanSeventyTwoResults() {
        var html = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            html.append("""
                    ["https://encrypted-tbn0.gstatic.com/images?q=tbn:sample",24,30],
                    ["https://static.example.org/photo%d.gif",48,60],null
                    """.formatted(index));
        }
        var images = GoogleImageResults.parseDocument(html.toString());
        assertEquals(100, images.size());
        assertEquals("https://static.example.org/photo99.gif", images.get(99).original());
    }

    @Test
    void additionalBatchesPreserveIndicesAndDiscardRepeatedOriginals() {
        var first = GoogleImageResults.image("https://example.org/first.gif",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:first", true);
        var duplicate = GoogleImageResults.image(first.original(),
                "https://encrypted-tbn1.gstatic.com/images?q=tbn:duplicate", true);
        var second = GoogleImageResults.image("https://example.org/second.gif",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:second", true);
        assertEquals(List.of(first, second),
                GoogleImageResults.appendNew(List.of(first), List.of(duplicate, second, second)));
    }

    @Test
    void aProviderRedirectCannotStandInForResultsOfAnotherSearch() {
        String url = GoogleImageResults.searchUrl("póker & GIF", false);
        assertTrue(url.contains("&safe=off&"));
        assertTrue(GoogleImageResults.isSearchFor(url, "póker & GIF"));
        assertFalse(GoogleImageResults.isSearchFor(url, "póker"));
        assertFalse(GoogleImageResults.isSearchFor("https://example.org/search?q=poker", "poker"));
        assertFalse(GoogleImageResults.isThumbnail("https://encrypted-tbn0.gstatic.com.example.org/images?q=tbn:abc"));
        assertTrue(GoogleImageResults.isGoogleInterruption("https://consent.google.com/d"));
    }

    @Test
    void continuationAcceptsOnlyTheRequestedOffset() {
        int next = GoogleImageResults.nextOffset(0);
        String first = GoogleImageResults.searchUrl("póker & GIF", false);
        String more = GoogleImageResults.searchUrl("póker & GIF", false, next);
        assertTrue(more.endsWith("&start=10"));
        assertTrue(GoogleImageResults.isSearchFor(more, "póker & GIF", next));
        assertTrue(GoogleImageResults.isSearchFor(first + "&start=00", "póker & GIF", 0));
        assertTrue(GoogleImageResults.isSearchFor(first + "&start=%31%30", "póker & GIF", next));
        assertFalse(GoogleImageResults.isSearchFor(first + "&start=unknown", "póker & GIF", 0));
        assertFalse(GoogleImageResults.isSearchFor(first, "póker & GIF", next));
        assertFalse(GoogleImageResults.isSearchFor(more, "póker & GIF", 0));
    }

    @Test
    void unusualTrafficIsRecognizedSeparatelyFromConsent() {
        assertTrue(GoogleImageResults.isGoogleRateLimited("https://www.google.com/sorry/index"));
        assertTrue(GoogleImageResults.isGoogleInterruption("https://www.google.com/sorry/index"));
        assertFalse(GoogleImageResults.isGoogleRateLimited("https://consent.google.com/d"));
        assertFalse(GoogleImageResults.isGoogleRateLimited("https://example.org/sorry/index"));
    }
}
