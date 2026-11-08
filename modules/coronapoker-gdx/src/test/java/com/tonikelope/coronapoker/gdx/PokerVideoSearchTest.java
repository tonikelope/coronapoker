/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PokerVideoSearchTest {

    @Test
    void boundedSearchResponseKeepsUtf8SizeLimitAndWholeRequestTimeout()
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch releaseStalledResponse = new CountDownLatch(1);
        byte[] text = "Póker en español".getBytes(StandardCharsets.UTF_8);
        server.createContext("/ok", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, text.length);
                exchange.getResponseBody().write(text);
            }
        });
        server.createContext("/large", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 4096);
                exchange.getResponseBody().write(new byte[4096]);
            }
        });
        server.createContext("/stall", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().flush();
                try {
                    releaseStalledResponse.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals("Póker en español", PokerVideoSearch.fetchBody(client,
                    HttpRequest.newBuilder(URI.create(base + "/ok")).GET().build(),
                    text.length));
            assertThrows(IOException.class, () -> PokerVideoSearch.fetchBody(client,
                    HttpRequest.newBuilder(URI.create(base + "/large")).GET().build(), 64));
            assertThrows(java.net.http.HttpTimeoutException.class,
                    () -> PokerVideoSearch.fetchBody(client,
                            HttpRequest.newBuilder(URI.create(base + "/stall"))
                                    .timeout(Duration.ofSeconds(2)).GET().build(), 64));
        } finally {
            releaseStalledResponse.countDown();
            server.stop(0);
        }
    }

    @Test
    void searchDefaultsToNormalVideosAndRespectsShorts() {
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "p%C3%B3ker+shorts+espa%C3%B1ol&hl=es"),
                PokerVideoSearch.searchUri("es", "shorts"));
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "poker+shorts+english&hl=en"),
                PokerVideoSearch.searchUri("en", "shorts"));
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "v%C3%ADdeos+de+p%C3%B3ker+espa%C3%B1ol&hl=es"),
                PokerVideoSearch.searchUri("es"));
        assertEquals(URI.create(
                "https://www.youtube.com/results?search_query="
                + "poker+videos+english&hl=en"),
                PokerVideoSearch.searchUri("en", "normal"));
        assertEquals("es", PokerVideoSearch.languageCode(null));
        assertEquals(PokerVideoSearch.TYPE_NORMAL,
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
    void idleSpeculativeConnectionDoesNotBlockThePlayerDocument() throws Exception {
        try (PokerVideoLocalServer server = PokerVideoLocalServer.start(List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "First")), 0, 0, true, 100, "es")) {
            URI origin = URI.create(server.origin());
            try (var idle = new java.net.Socket(origin.getHost(), origin.getPort())) {
                idle.getOutputStream().write("GET /player/ HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
                var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(server.playerUrl())).timeout(Duration.ofMillis(1500)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("ABCDEFGHIJK"));
            }
        }
    }

    @Test
    void localPlayerOnlyServesItsDocumentAndClosesPendingConnections()
            throws Exception {
        PokerVideoLocalServer server = PokerVideoLocalServer.start(List.of(
                new PokerVideoSearch.Video("ABCDEFGHIJK", "First")),
                0, 0, true, 100, "es");
        URI origin = URI.create(server.origin());
        try (server) {
            HttpClient client = HttpClient.newHttpClient();
            assertEquals(404, client.send(HttpRequest.newBuilder(origin.resolve("/other"))
                    .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(origin.resolve("/player/"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(204, client.send(HttpRequest.newBuilder(origin.resolve("/favicon.ico"))
                    .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            var head = client.send(HttpRequest.newBuilder(URI.create(server.playerUrl()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, head.statusCode());
            assertEquals("", head.body());
            try (var pending = new java.net.Socket(origin.getHost(), origin.getPort())) {
                pending.setSoTimeout(2000);
                pending.getOutputStream().write("GET /player/ HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
                server.close();
                assertEquals(-1, pending.getInputStream().read());
            }
        }
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

            String html = response.body().replace("\r\n", "\n");
            assertEquals(200, response.statusCode());
            assertTrue(html.contains(
                    "let VIDEO_IDS = ['ABCDEFGHIJK','LMNOPQRSTUV']"));
            assertTrue(html.contains("let currentIndex = 1"));
            assertTrue(html.contains("const START_SECONDS = 73"));
            assertTrue(html.contains(
                    "const INITIAL_MUTED = false"));
            assertTrue(html.contains(
                    "const INITIAL_VOLUME = 64"));
            assertTrue(html.contains(
                    "const PLAYER_LANGUAGE = 'en'"));
            assertTrue(html.contains(
                    "function skipUnavailable(errorCode)"));
            assertTrue(html.contains(
                    "[2, 5, 100, 101, 150]"));
            assertTrue(html.contains("controls: 1"));
            assertTrue(html.contains("disablekb: 1"));
            assertTrue(html.contains(
                    "function playVideo(target)"));
            assertTrue(html.contains("autoplay: 0"));
            assertFalse(html.contains("autoplay: 1"));
            assertFalse(html.contains("mute: 1"));
            assertTrue(html.contains(
                    "function applyDesiredAudio(target)"));
            assertTrue(html.contains(
                    "function monitorAudioState()"));
            assertTrue(html.contains(
                    "function captureStableAudioState()"));
            assertFalse(html.contains(
                    "RECOVER_AUDIO_PIPELINE"));
            assertFalse(html.contains("scheduleAudioSync("));
            assertFalse(html.contains("settleAudioAfterPlayback("));
            assertFalse(html.contains(
                    "scheduleMutedVisualConfirmation("));
            assertFalse(html.contains("audioStateTrusted"));
            assertFalse(html.contains("muteConfirmationPending"));
            assertTrue(html.contains(
                    "target.unMute();"));
            assertTrue(html.contains(
                    "if (target.setVolume) target.setVolume(desiredVolume);"));
            assertTrue(html.contains(
                    "videoId: VIDEO_IDS[currentIndex],\n"
                    + "        playerVars:"));
            assertTrue(html.contains("start: START_SECONDS"));
            assertFalse(html.contains(
                    "event.target.loadVideoById({"));
            assertTrue(html.contains(
                    "player.loadVideoById(VIDEO_IDS[currentIndex]);"));
            assertTrue(html.contains(
                    "window.coronaSelectRelative = selectRelative"));
            assertTrue(html.contains(
                    "window.coronaReplaceVideos = function (ids, ordered"));
            assertTrue(html.contains("let orderedPlaylist = false;"));
            assertTrue(html.contains("let playlistRevision = 0;"));
            assertTrue(html.contains(
                    "if (event.data === 0) selectRelative(1)"));
            assertEquals("strict-origin-when-cross-origin",
                    response.headers().firstValue("Referrer-Policy")
                            .orElseThrow());
        }
    }

    @Test
    void browserSessionsUseInjectedAudioAndNativeControls()
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
            assertTrue(body.contains("controls: 1"));
            assertTrue(body.contains("mute: INITIAL_MUTED ? 1 : 0"));
            assertFalse(body.contains("synchronizeMutedVisualState"));
            assertFalse(body.contains("enforceDesiredAudio"));
            assertFalse(body.contains("audioObservationArmed"));
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
                + "&& hadSession"));
        assertTrue(runtimeSource.contains("request.startsWith(\"AUDIO|\")"));

        String overlaySource = Files.readString(Path.of(
                "src/main/java/com/tonikelope/coronapoker/gdx/"
                + "PokerVideoOverlay.java"));
        assertTrue(overlaySource.contains("releaseBrowserSession(false);"));
        assertTrue(overlaySource.contains(
                "hostVisible = visible && enabled;"));
        assertTrue(overlaySource.contains("case CLOSE ->"));
        assertFalse(overlaySource.contains("featurePreferenceChanged"));
        assertFalse(runtimeSource.contains("notifyVideoInteraction"));
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
    void defaultOverlaySizesFitAboveTheFooterAndReachTheRightEdge() throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            var boundsField = PokerVideoOverlay.class.getDeclaredField("panelBounds");
            boundsField.setAccessible(true);
            Rectangle panel = (Rectangle) boundsField.get(overlay);

            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            assertEquals(514f, panel.width);
            assertEquals(100f, panel.y);
            assertEquals(1912f, panel.x + panel.width);
            assertEquals(16f / 9f, panel.width
                    / (panel.height - PokerVideoOverlay.CHROME_HEIGHT), 0.001f);

            overlay.update(true, true, false, "es", "shorts", 1920f, 1080f);
            assertEquals(330f, panel.width);
            assertEquals(108f, panel.y);
            assertEquals(1912f, panel.x + panel.width);
            assertEquals(9f / 16f, panel.width
                    / (panel.height - PokerVideoOverlay.CHROME_HEIGHT), 0.001f);

            overlay.update(true, true, false, "es", "normal", 1920f, 1080f);
            assertEquals(514f, panel.width);
            assertEquals(100f, panel.y);
        }
    }

    @Test
    void footerVideoIconKeepsItsSharedBounds() {
        Rectangle menu = PokerVideoIcon.bounds(1920f, new Rectangle());
        assertEquals(1781f, menu.x);
        assertEquals(16f, menu.y);
        assertEquals(48f, menu.width);
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
    void landscapeCanFillTheScreenAndStillFitsAfterSwitchingToShorts()
            throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            var started = PokerVideoOverlay.class.getDeclaredField(
                    "initializationStarted");
            started.setAccessible(true);
            started.setBoolean(overlay, true);
            var key = PokerVideoOverlay.class.getDeclaredField(
                    "requestedCatalogKey");
            key.setAccessible(true);
            key.set(overlay, "es:normal");
            overlay.update(true, true, true, "es", "normal", 1920f, 1080f);
            var boundsField = PokerVideoOverlay.class.getDeclaredField(
                    "panelBounds");
            boundsField.setAccessible(true);
            Rectangle panel = (Rectangle) boundsField.get(overlay);
            float left = panel.x + 2f;
            float middle = panel.y + panel.height / 2f;
            assertTrue(overlay.touchDown(left, middle, 0, 0));
            assertTrue(overlay.touchDragged(left - 10000f, middle, 0));
            assertTrue(overlay.touchUp(left - 10000f, middle, 0, 0));
            assertTrue(panel.width > 1700f);
            assertTrue(panel.x >= 8f && panel.x + panel.width <= 1912.01f);
            assertTrue(panel.y >= 8f && panel.y + panel.height <= 1072.01f);
            assertEquals(16f / 9f, panel.width
                    / (panel.height - PokerVideoOverlay.CHROME_HEIGHT), 0.001f);

            key.set(overlay, "es:shorts");
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertTrue(panel.x >= 8f && panel.x + panel.width <= 1912.01f);
            assertTrue(panel.y >= 8f && panel.y + panel.height <= 1072.01f);
            assertEquals(9f / 16f, panel.width
                    / (panel.height - PokerVideoOverlay.CHROME_HEIGHT), 0.001f);
        }
    }

    @Test
    void draggingPastTheBottomRightKeepsTheWindowInsideTheDisplay()
            throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            var started = PokerVideoOverlay.class.getDeclaredField("initializationStarted");
            started.setAccessible(true);
            started.setBoolean(overlay, true);
            var key = PokerVideoOverlay.class.getDeclaredField("requestedCatalogKey");
            key.setAccessible(true);
            key.set(overlay, "es:shorts");
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            var boundsField = PokerVideoOverlay.class.getDeclaredField("panelBounds");
            boundsField.setAccessible(true);
            Rectangle panel = (Rectangle) boundsField.get(overlay);
            float titleX = panel.x + panel.width / 2f;
            float titleY = panel.y + panel.height - PokerVideoOverlay.HEADER_HEIGHT / 2f;

            assertTrue(overlay.touchDown(titleX, titleY, 0, 0));
            assertTrue(overlay.touchDragged(2500f, -100f, 0));
            assertTrue(overlay.touchUp(2500f, -100f, 0, 0));
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertEquals(1912f, panel.x + panel.width);
            assertEquals(8f, panel.y);
        }
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
                    "es", "shorts", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));
            assertFalse(overlay.touchUp(1805f, 40f, 0, 0));
        }
    }

    @Test
    void minimizeSurvivesScreenAndTableTransitionsUntilIconIsClicked()
            throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            // Stand in for an already-fetched catalogue without starting a
            // network request or native browser in this input/lifecycle test.
            var started = PokerVideoOverlay.class.getDeclaredField(
                    "initializationStarted");
            started.setAccessible(true);
            started.setBoolean(overlay, true);
            var key = PokerVideoOverlay.class.getDeclaredField(
                    "requestedCatalogKey");
            key.setAccessible(true);
            key.set(overlay, "es:shorts");
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            var closeField = PokerVideoOverlay.class.getDeclaredField("closeBounds");
            closeField.setAccessible(true);
            Rectangle close = (Rectangle) closeField.get(overlay);
            float closeX = close.x + close.width / 2f;
            float closeY = close.y + close.height / 2f;
            assertTrue(overlay.touchDown(closeX, closeY, 0, 0));
            assertTrue(overlay.touchUp(closeX, closeY, 0, 0));
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertFalse(started.getBoolean(overlay));
            overlay.minimizeForTable();
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertFalse(started.getBoolean(overlay));
            assertTrue(overlay.hostVisible());
            assertTrue(overlay.touchDown(1805f, 40f, 0, 0));
            assertTrue(overlay.touchUp(1805f, 40f, 0, 0));
            // Restored window receives its close button, not another icon.
            assertTrue(overlay.touchDown(closeX, closeY, 0, 0));
            assertTrue(overlay.touchUp(closeX, closeY, 0, 0));
        }
    }

    @Test
    void disabledStateSurvivesACompleteTableRoundTrip() {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            overlay.update(true, false, true,
                    "es", "shorts", 1920f, 1080f);
            overlay.minimizeForTable();
            overlay.restoreAfterTable();
            overlay.update(true, false, true,
                    "es", "shorts", 1920f, 1080f);

            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));
            assertFalse(overlay.touchUp(1805f, 40f, 0, 0));
        }
    }

    @Test
    void enteringTableClosesOpenPlayerAndRestoresItOnReturnUnlessUsedAtTable()
            throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            overlay.update(true, true, false, "es", "shorts", 1920f, 1080f);
            var minimized = PokerVideoOverlay.class.getDeclaredField("minimized");
            minimized.setAccessible(true);
            assertFalse(minimized.getBoolean(overlay));

            overlay.minimizeForTable();
            assertTrue(minimized.getBoolean(overlay));
            overlay.restoreAfterTable();
            assertFalse(minimized.getBoolean(overlay));

            overlay.minimizeForTable();
            var started = PokerVideoOverlay.class.getDeclaredField("initializationStarted");
            started.setAccessible(true);
            started.setBoolean(overlay, true);
            var key = PokerVideoOverlay.class.getDeclaredField("requestedCatalogKey");
            key.setAccessible(true);
            key.set(overlay, "es:shorts");
            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertTrue(overlay.touchDown(1805f, 40f, 0, 0));
            assertTrue(overlay.touchUp(1805f, 40f, 0, 0));

            var close = PokerVideoOverlay.class.getDeclaredField("closeBounds");
            close.setAccessible(true);
            Rectangle button = (Rectangle) close.get(overlay);
            assertTrue(overlay.touchDown(button.x + 4f, button.y + 4f, 0, 0));
            assertTrue(overlay.touchUp(button.x + 4f, button.y + 4f, 0, 0));

            overlay.restoreAfterTable();
            assertTrue(minimized.getBoolean(overlay));
        }
    }

    @Test
    void tableModalTemporarilyHidesAnOpenPlayer() throws Exception {
        try (PokerVideoOverlay overlay = new PokerVideoOverlay()) {
            var started = PokerVideoOverlay.class.getDeclaredField("initializationStarted");
            started.setAccessible(true);
            started.setBoolean(overlay, true);
            var key = PokerVideoOverlay.class.getDeclaredField("requestedCatalogKey");
            key.setAccessible(true);
            key.set(overlay, "es:shorts");

            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            overlay.update(true, true, false, "es", "shorts", 1920f, 1080f);
            assertFalse(overlay.hostVisible());
            assertFalse(overlay.touchDown(1805f, 40f, 0, 0));

            overlay.update(true, true, true, "es", "shorts", 1920f, 1080f);
            assertTrue(overlay.hostVisible());
            var minimized = PokerVideoOverlay.class.getDeclaredField("minimized");
            minimized.setAccessible(true);
            assertFalse(minimized.getBoolean(overlay));
            assertTrue(started.getBoolean(overlay));
        }
    }

}
