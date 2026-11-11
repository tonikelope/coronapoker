package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxImageSearchHistoryTest {
    @Test
    void multipleQueriesAndTheirPagesSurviveAReloadUntilExplicitlyCleared() throws Exception {
        var gif = GoogleImageResults.image("https://example.org/download/7",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:first", true);
        var photo = GoogleImageResults.image("https://example.org/photo.jpg",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:second", false);
        var first = new GdxImageSearchHistory.Snapshot("póker y GIF", List.of(gif), 10)
                .append(List.of(gif, photo), 20, true);
        var second = new GdxImageSearchHistory.Snapshot("gatos", List.of(photo), -1);
        var search = new GdxImageSearchHistory.History(second.term(), List.of(first, second));
        var properties = new Properties();
        GdxImageSearchHistory.write(properties, search);
        var file = new StringWriter();
        properties.store(file, null);
        var reloaded = new Properties();
        reloaded.load(new StringReader(file.toString()));
        assertEquals(search, GdxImageSearchHistory.read(reloaded));
        assertEquals(List.of(photo), GdxImageSearchHistory.read(reloaded).searches().get(0).pageImages());

        var empty = new GdxImageSearchHistory.History("", List.of());
        GdxImageSearchHistory.write(reloaded, empty);
        assertEquals(empty, GdxImageSearchHistory.read(reloaded));
    }

    @Test
    void historyRestoresAllFetchedBatchesAndTheirContinuation() {
        var images = new ArrayList<GoogleImageResults.Image>();
        for (int index = 0; index < 205; index++) {
            images.add(GoogleImageResults.image("https://example.org/photo" + index + ".gif",
                    "https://encrypted-tbn0.gstatic.com/images?q=tbn:test", true));
        }
        var properties = new Properties();
        var search = new GdxImageSearchHistory.Snapshot("GIF", List.copyOf(images), 30).withPage(4);
        var history = new GdxImageSearchHistory.History(search.term(), List.of(search));
        GdxImageSearchHistory.write(properties, history);
        assertEquals(history, GdxImageSearchHistory.read(properties));
        assertEquals(List.of(0, 50, 100, 150, 200), search.pageStarts());
        assertEquals(5, search.pageImages().size());
    }

    @Test
    void historyWithoutAContinuationResumesFromTheFirstProviderBatch() {
        var properties = new Properties();
        properties.setProperty("gdx_image_search_history", """
                {"term":"GIF","images":[{"original":"https://example.org/first.gif",
                "thumbnail":"https://encrypted-tbn0.gstatic.com/images?q=tbn:test","animated":true}]}
                """);
        var history = GdxImageSearchHistory.read(properties);
        assertEquals("GIF", history.selected());
        var search = history.searches().get(0);
        assertEquals(0, search.nextOffset());
        assertEquals(1, search.images().size());
    }

    @Test
    void aShortBatchStaysOnItsOwnPageWhenMoreResultsAreRequested() {
        var images = new ArrayList<GoogleImageResults.Image>();
        for (int index = 0; index < 141; index++) {
            images.add(GoogleImageResults.image("https://example.org/photo" + index + ".jpg",
                    "https://encrypted-tbn0.gstatic.com/images?q=tbn:test", false));
        }
        var first = new GdxImageSearchHistory.Snapshot("GIF", List.copyOf(images.subList(0, 37)), 10);
        var continued = first.append(List.copyOf(images), 20, true);
        assertEquals(List.of(0, 37, 87, 137), continued.pageStarts());
        assertEquals(1, continued.page());
        assertEquals(50, continued.pageImages().size());
        assertEquals(37, continued.withPage(0).pageImages().size());
        assertEquals(4, continued.withPage(3).pageImages().size());
    }

    @Test
    void savedSmallPagesResumeAroundTheSameImageAfterMigration() {
        var images = new ArrayList<GoogleImageResults.Image>();
        for (int index = 0; index < 140; index++) {
            images.add(GoogleImageResults.image("https://example.org/photo" + index + ".jpg",
                    "https://encrypted-tbn0.gstatic.com/images?q=tbn:test", false));
        }
        var previous = new GdxImageSearchHistory.Snapshot("GIF", List.copyOf(images), 10,
                List.of(0, 25, 50, 75, 100, 125), 3);
        var properties = new Properties();
        GdxImageSearchHistory.write(properties, new GdxImageSearchHistory.History("GIF", List.of(previous), 25));
        var migrated = GdxImageSearchHistory.read(properties).searches().get(0);
        assertEquals(List.of(0, 50, 100), migrated.pageStarts());
        assertEquals(1, migrated.page());
        assertTrue(migrated.pageImages().contains(images.get(previous.firstIndex())));
    }

    @Test
    void equivalentSearchesUseTheSameKey() {
        assertEquals(GdxImageSearchHistory.key("po\u0301ker gif"), GdxImageSearchHistory.key(" PÓKER\u00a0 GIF "));
    }
}
