/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PokerVideoSearchTest {

    @Test
    void searchTargetsPokerShortsInTheGameLanguage() {
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "p%C3%B3ker+shorts+espa%C3%B1ol&hl=es"),
                PokerVideoSearch.searchUri("es"));
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "poker+shorts+english&hl=en"),
                PokerVideoSearch.searchUri("en"));
        assertEquals("es", PokerVideoSearch.languageCode(null));
    }

    @Test
    void extractsCurrentAndLegacyShortsWithoutDuplicates() {
        String html = "<script>var ytInitialData = {\"contents\":["
                + "{\"shortsLockupViewModel\":{"
                + "\"onTap\":{\"innertubeCommand\":{\"reelWatchEndpoint\":{"
                + "\"videoId\":\"ABCDEFGHIJK\"}}},"
                + "\"overlayMetadata\":{\"primaryText\":{"
                + "\"content\":\"Poker short one\"}}}},"
                + "{\"reelItemRenderer\":{\"videoId\":\"LMNOPQRSTUV\","
                + "\"title\":{\"simpleText\":\"Poker short two\"}}},"
                + "{\"reelItemRenderer\":{\"videoId\":\"ABCDEFGHIJK\","
                + "\"title\":{\"simpleText\":\"Duplicate\"}}}"
                + "]};</script>";

        List<PokerVideoSearch.Video> videos = PokerVideoSearch.parse(html);

        assertEquals(List.of("ABCDEFGHIJK", "LMNOPQRSTUV"),
                videos.stream().map(PokerVideoSearch.Video::id).toList());
        assertEquals(List.of("Poker short one", "Poker short two"),
                videos.stream().map(PokerVideoSearch.Video::title).toList());
    }

    @Test
    void fallbackAcceptsOnlyShortUrlsWhenInitialDataChangesShape() {
        List<PokerVideoSearch.Video> videos = PokerVideoSearch.parse(
                "x \"url\":\"/shorts/ABCDEFGHIJK\" y "
                + "\"videoId\":\"NOTASHORT01\" z "
                + "\"url\":\"/shorts/LMNOPQRSTUV\"");

        assertEquals(List.of("ABCDEFGHIJK", "LMNOPQRSTUV"),
                videos.stream().map(PokerVideoSearch.Video::id).toList());
    }

    @Test
    void shufflesOneSessionWithoutMutatingSearchOrder() {
        List<PokerVideoSearch.Video> ordered = List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "One"),
                new PokerVideoSearch.Video("LMNOPQRSTUV", "Two"),
                new PokerVideoSearch.Video("WXYZ0123456", "Three"),
                new PokerVideoSearch.Video("ZXCVBNMASDF", "Four"));

        List<PokerVideoSearch.Video> shuffled = PokerVideoSearch.shuffled(
                ordered, new Random(42L));

        assertNotEquals(ordered, shuffled);
        assertEquals(new HashSet<>(ordered), new HashSet<>(shuffled));
        assertEquals("ABCDEFGHIJK", ordered.get(0).id());
    }

    @Test
    void localPlayerUsesRequestedIndexPositionAndSecurityHeaders()
            throws Exception {
        List<PokerVideoSearch.Video> videos = List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "First"),
                new PokerVideoSearch.Video("LMNOPQRSTUV", "Second"));
        try (PokerVideoLocalServer server = PokerVideoLocalServer.start(videos,
                1, 73, false, 64, "en")) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.playerUrl())).GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(
                    "const VIDEO_IDS = ['ABCDEFGHIJK','LMNOPQRSTUV']"));
            assertTrue(response.body().contains("let currentIndex = 1"));
            assertTrue(response.body().contains("const START_SECONDS = 73"));
            assertTrue(response.body().contains(
                    "const INITIAL_MUTED = false"));
            assertTrue(response.body().contains(
                    "const INITIAL_VOLUME = 64"));
            assertTrue(response.body().contains(
                    "const PLAYER_LANGUAGE = 'en'"));
            assertTrue(response.body().contains(
                    "function skipUnavailable(errorCode)"));
            assertTrue(response.body().contains(
                    "[2, 5, 100, 101, 150]"));
            assertTrue(response.body().contains("controls: 0"));
            assertTrue(response.body().contains("disablekb: 1"));
            assertTrue(response.body().contains(
                    "function playWithDesiredAudio(target)"));
            assertTrue(response.body().contains(
                    "window.coronaSelectRelative = selectRelative"));
            assertTrue(response.body().contains(
                    "if (event.data === 0) selectRelative(1)"));
            assertEquals("strict-origin-when-cross-origin",
                    response.headers().firstValue("Referrer-Policy")
                            .orElseThrow());
        }
    }

    @Test
    void pokerVideoPreferenceTracksPlatformAvailabilityAndDefaultsOn() {
        var page = GdxSettingsContract.appearanceTogglePage(1);
        var option = page.options().stream()
                .filter(candidate -> GdxSettingsContract.POKER_VIDEOS_KEY
                        .equals(candidate.key()))
                .findFirst();

        assertEquals(PokerVideoPlatformSupport.isAvailable(),
                option.isPresent());
        option.ifPresent(candidate -> assertTrue(candidate.fallback()));
        assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(
                GdxSettingsContract.POKER_VIDEOS_KEY));
    }

    @Test
    void overlayStartsAtItsCompactProportionalSize() {
        assertEquals(PokerVideoOverlay.MIN_WIDTH,
                PokerVideoOverlay.INITIAL_WIDTH);
        Rectangle menu = PokerVideoIcon.bounds(1920f,
                PokerVideoIcon.Placement.MENU, new Rectangle());
        Rectangle lobby = PokerVideoIcon.bounds(1920f,
                PokerVideoIcon.Placement.LOBBY, new Rectangle());
        assertEquals(92f, menu.y);
        assertEquals(154f, lobby.y);
        assertTrue(lobby.y > menu.y + PokerVideoIcon.SIZE / 2f);
    }

    @Test
    void masterAnimationsGateEveryPokerVideoSurface() {
        assertTrue(PokerVideoIcon.systemEnabled(true, true));
        assertFalse(PokerVideoIcon.systemEnabled(true, false));
        assertFalse(PokerVideoIcon.systemEnabled(false, true));
        assertTrue(PokerVideoIcon.visibleOnTable(
                true, true, true, true, false));
        assertFalse(PokerVideoIcon.visibleOnTable(
                true, false, true, true, false));
        assertFalse(PokerVideoIcon.visibleOnTable(
                true, true, true, false, false));
        assertFalse(PokerVideoIcon.visibleOnTable(
                true, true, true, true, true));
    }

    @Test
    void disabledOverlayKeepsAnIconThatPersistsEnableAndCloseActions() {
        List<Boolean> preferenceChanges = new ArrayList<>();
        try (PokerVideoOverlay overlay =
                new PokerVideoOverlay(preferenceChanges::add)) {
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", 1920f, 1080f);

            assertTrue(overlay.hostVisible());
            assertTrue(overlay.touchDown(1867f, 121f, 0, 0));
            assertTrue(overlay.touchUp(1867f, 121f, 0, 0));
            assertEquals(List.of(true), preferenceChanges);

            assertTrue(overlay.touchDown(1868f, 592f, 0, 0));
            assertTrue(overlay.touchUp(1868f, 592f, 0, 0));
            assertEquals(List.of(true, false), preferenceChanges);
        }
    }

    @Test
    void minimizedStateSurvivesACompleteTableRoundTrip() {
        List<Boolean> preferenceChanges = new ArrayList<>();
        try (PokerVideoOverlay overlay =
                new PokerVideoOverlay(preferenceChanges::add)) {
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", 1920f, 1080f);
            overlay.releaseForTable();
            overlay.restoreAfterTable();
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", 1920f, 1080f);

            assertTrue(overlay.hostVisible());
            assertTrue(overlay.touchDown(1867f, 121f, 0, 0));
            assertTrue(overlay.touchUp(1867f, 121f, 0, 0));
            assertEquals(List.of(true), preferenceChanges);
        }
    }

    @Test
    void unavailablePlatformShowsNothingAndAcceptsNoIconInput() {
        List<Boolean> preferenceChanges = new ArrayList<>();
        try (PokerVideoOverlay overlay =
                new PokerVideoOverlay(preferenceChanges::add)) {
            overlay.update(false, true, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1867f, 121f, 0, 0));
            assertTrue(preferenceChanges.isEmpty());
        }
    }
}
