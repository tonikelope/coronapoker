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
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "v%C3%ADdeos+de+p%C3%B3ker+espa%C3%B1ol&hl=es"),
                PokerVideoSearch.searchUri("es", "normal"));
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "poker+videos+english&hl=en"),
                PokerVideoSearch.searchUri("en", "normal"));
        assertEquals("es", PokerVideoSearch.languageCode(null));
        assertEquals(PokerVideoSearch.TYPE_SHORTS,
                PokerVideoSearch.videoType("unexpected"));
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
    void extractsNormalVideosWithoutMixingShorts() {
        String html = "<script>var ytInitialData = {\"contents\":["
                + "{\"videoRenderer\":{\"videoId\":\"ABCDEFGHIJK\","
                + "\"title\":{\"runs\":[{\"text\":\"Normal poker video\"}]}}},"
                + "{\"shortsLockupViewModel\":{"
                + "\"onTap\":{\"innertubeCommand\":{\"reelWatchEndpoint\":{"
                + "\"videoId\":\"LMNOPQRSTUV\"}}}}}"
                + "]};</script>";

        List<PokerVideoSearch.Video> videos = PokerVideoSearch.parse(
                html, PokerVideoSearch.TYPE_NORMAL);

        assertEquals(List.of("ABCDEFGHIJK"),
                videos.stream().map(PokerVideoSearch.Video::id).toList());
        assertEquals("Normal poker video", videos.get(0).title());
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
                    "let VIDEO_IDS = ['ABCDEFGHIJK','LMNOPQRSTUV']"));
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
                    "function playVideo(target)"));
            assertTrue(response.body().contains("autoplay: 0"));
            assertFalse(response.body().contains("autoplay: 1"));
            assertFalse(response.body().contains("mute: 1"));
            assertTrue(response.body().contains(
                    "function applyDesiredAudio(target)"));
            assertTrue(response.body().contains(
                    "function monitorAudioState()"));
            assertTrue(response.body().contains(
                    "function captureStableAudioState()"));
            assertTrue(response.body().contains(
                    "!audioObservationArmed"));
            assertTrue(response.body().contains(
                    "function enforceDesiredAudio(target)"));
            assertTrue(response.body().contains(
                    "function synchronizeMutedVisualState(target)"));
            assertTrue(response.body().contains(
                    "window.coronaUserInteracted = function ()"));
            assertTrue(response.body().contains(
                    "captureStableAudioState();\n"
                    + "        audioObservationArmed = false;"));
            assertTrue(response.body().contains(
                    "applyDesiredAudio(target);\n"
                    + "      if (target.playVideo) target.playVideo();"));
            assertFalse(response.body().contains(
                    "RECOVER_AUDIO_PIPELINE"));
            assertFalse(response.body().contains("scheduleAudioSync("));
            assertFalse(response.body().contains("settleAudioAfterPlayback("));
            assertFalse(response.body().contains(
                    "scheduleMutedVisualConfirmation("));
            assertFalse(response.body().contains("audioStateTrusted"));
            assertFalse(response.body().contains("muteConfirmationPending"));
            assertTrue(response.body().contains(
                    "target.unMute();"));
            assertTrue(response.body().contains(
                    "if (target.setVolume) target.setVolume(desiredVolume);"));
            assertTrue(response.body().contains(
                    "videoId: VIDEO_IDS[currentIndex],\n"
                    + "        playerVars:"));
            assertTrue(response.body().contains("start: START_SECONDS"));
            assertFalse(response.body().contains(
                    "event.target.loadVideoById({"));
            assertTrue(response.body().contains(
                    "player.loadVideoById(VIDEO_IDS[currentIndex]);\n"
                    + "      playVideo(player);"));
            assertTrue(response.body().contains(
                    "window.coronaSelectRelative = selectRelative"));
            assertTrue(response.body().contains(
                    "window.coronaReplaceVideos = function (ids)"));
            assertTrue(response.body().contains(
                    "if (event.data === 0) selectRelative(1)"));
            int syncStart = response.body().indexOf(
                    "function synchronizeMutedVisualState(target)");
            int syncEnd = response.body().indexOf(
                    "function enforceDesiredAudio(target)", syncStart);
            String sync = response.body().substring(syncStart, syncEnd);
            int unmute = sync.indexOf("target.unMute();");
            int firstPaint = sync.indexOf("afterPaint(() =>", unmute);
            int mute = sync.indexOf("target.mute();", firstPaint);
            int secondPaint = sync.indexOf("afterPaint(() =>", mute);
            assertTrue(unmute >= 0 && firstPaint > unmute);
            assertTrue(mute > firstPaint && secondPaint > mute);
            assertEquals("strict-origin-when-cross-origin",
                    response.headers().firstValue("Referrer-Policy")
                            .orElseThrow());
        }
    }

    @Test
    void browserSessionsUseInjectedAudioAndMinimizePreservesIt()
            throws Exception {
        List<PokerVideoSearch.Video> videos = List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "First"));
        try (PokerVideoLocalServer server = PokerVideoLocalServer.start(videos,
                0, 0, true, 100, "es")) {
            String body = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.playerUrl())).GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()).body();

            assertTrue(body.contains("const INITIAL_MUTED = true"));
            assertTrue(body.contains("let desiredMuted = INITIAL_MUTED"));
            assertTrue(body.contains("let audioObservationArmed = false"));
            assertTrue(body.contains("let mutedVisualSyncPending = "
                    + "INITIAL_MUTED"));
            assertTrue(body.contains("autoplay: 0"));
            assertFalse(body.contains("mute: 1"));
            assertTrue(body.contains(
                    "if (target.mute) target.mute();"));
            assertTrue(body.contains(
                    "setInterval(monitorAudioState, 250)"));
            assertFalse(body.contains("RECOVER_AUDIO_PIPELINE"));
        }

        String runtimeSource = Files.readString(Path.of(
                "src/main/java/com/tonikelope/coronapoker/gdx/"
                + "PokerVideoRuntime.java"));
        assertTrue(runtimeSource.contains(
                "resumeSeconds, initialMuted, initialVolume, playerLanguage"));
        assertTrue(runtimeSource.contains("restoreAudioOnNextSession"));
        assertTrue(runtimeSource.contains(
                "boolean initialMuted = restoreAudio ? resumeMuted : true"));
        assertTrue(runtimeSource.contains(
                "restoreAudioOnNextSession = preserveAudioForResume "
                + "&& current != null"));
        assertTrue(runtimeSource.contains("request.startsWith(\"AUDIO|\")"));

        String overlaySource = Files.readString(Path.of(
                "src/main/java/com/tonikelope/coronapoker/gdx/"
                + "PokerVideoOverlay.java"));
        assertTrue(overlaySource.contains("releaseBrowserSession(false);"));
        assertTrue(overlaySource.contains(
                "releaseBrowserSession(restoreWindowAfterTable);"));
        assertTrue(overlaySource.contains(
                "hostVisible = visible && enabled && !releasedForTable;"));
        assertTrue(overlaySource.contains("case CLOSE ->"));
        assertTrue(overlaySource.contains("releaseBrowserSession(true);"));
        assertFalse(overlaySource.contains("featurePreferenceChanged"));
        assertTrue(overlaySource.contains(
                "current.notifyVideoInteraction();"));
        assertTrue(runtimeSource.contains(
                "void notifyVideoInteraction()"));
        assertTrue(runtimeSource.contains(
                "window.coronaUserInteracted();"));
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
        assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(
                GdxSettingsContract.POKER_VIDEO_TYPE_KEY));
    }

    @Test
    void overlayStartsAtItsCompactProportionalSize() {
        assertEquals(PokerVideoOverlay.MIN_WIDTH,
                PokerVideoOverlay.INITIAL_WIDTH);
        Rectangle menu = PokerVideoIcon.bounds(1920f,
                PokerVideoIcon.Placement.MENU, new Rectangle());
        Rectangle lobby = PokerVideoIcon.bounds(1920f,
                PokerVideoIcon.Placement.LOBBY, new Rectangle());
        assertEquals(1781f, menu.x);
        assertEquals(16f, menu.y);
        assertEquals(48f, menu.width);
        assertEquals(1478.5f, lobby.x);
        assertEquals(61f, lobby.y);
        assertEquals(60f, lobby.width);
    }

    @Test
    void everyVideoWindowEdgeAndCornerIsAResizeHandle() {
        Rectangle frame = new Rectangle(100f, 100f, 300f, 500f);

        assertEquals(PokerVideoOverlay.ResizeHandle.LEFT,
                PokerVideoOverlay.resizeHandleAt(frame, 105f, 300f));
        assertEquals(PokerVideoOverlay.ResizeHandle.RIGHT,
                PokerVideoOverlay.resizeHandleAt(frame, 395f, 300f));
        assertEquals(PokerVideoOverlay.ResizeHandle.BOTTOM,
                PokerVideoOverlay.resizeHandleAt(frame, 250f, 105f));
        assertEquals(PokerVideoOverlay.ResizeHandle.TOP,
                PokerVideoOverlay.resizeHandleAt(frame, 250f, 595f));
        assertEquals(PokerVideoOverlay.ResizeHandle.BOTTOM_LEFT,
                PokerVideoOverlay.resizeHandleAt(frame, 105f, 105f));
        assertEquals(PokerVideoOverlay.ResizeHandle.BOTTOM_RIGHT,
                PokerVideoOverlay.resizeHandleAt(frame, 395f, 105f));
        assertEquals(PokerVideoOverlay.ResizeHandle.TOP_LEFT,
                PokerVideoOverlay.resizeHandleAt(frame, 105f, 595f));
        assertEquals(PokerVideoOverlay.ResizeHandle.TOP_RIGHT,
                PokerVideoOverlay.resizeHandleAt(frame, 395f, 595f));
        assertEquals(PokerVideoOverlay.ResizeHandle.NONE,
                PokerVideoOverlay.resizeHandleAt(frame, 250f, 300f));
    }

    @Test
    void masterAnimationsGateEveryPokerVideoSurface() {
        assertTrue(PokerVideoIcon.systemEnabled(true, true));
        assertFalse(PokerVideoIcon.systemEnabled(true, false));
        assertFalse(PokerVideoIcon.systemEnabled(false, true));
    }

    @Test
    void disabledOverlayShowsNothingAndAcceptsNoInput() {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));
            assertFalse(overlay.touchUp(1805f, 40f, 0, 0));
        }
    }

    @Test
    void disabledStateSurvivesACompleteTableRoundTrip() {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);
            overlay.releaseForTable();
            overlay.restoreAfterTable();
            overlay.update(true, false, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));
            assertFalse(overlay.touchUp(1805f, 40f, 0, 0));
        }
    }

    @Test
    void unavailablePlatformShowsNothingAndAcceptsNoIconInput() {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            overlay.update(false, true, true,
                    PokerVideoIcon.Placement.MENU,
                    "es", "shorts", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));
        }
    }
}
