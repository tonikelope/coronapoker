/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/** Serves the bundled player inside CEF while keeping a distinct HTTP origin for the embed. */
final class PokerVideoPage extends CefResourceRequestHandlerAdapter implements AutoCloseable {
    static final Map<String, String> HEADERS = Map.of(
            "Content-Type", "text/html; charset=utf-8",
            "Cache-Control", "no-store",
            "Referrer-Policy", "strict-origin-when-cross-origin",
            "X-Content-Type-Options", "nosniff",
            "Permissions-Policy", "autoplay=(self \"https://www.youtube.com\")");

    private final Socket originReservation;
    private final String origin;
    private final String playerUrl;
    private final byte[] html;

    private PokerVideoPage(Socket originReservation, String origin, byte[] html) {
        this.originReservation = originReservation;
        this.origin = origin;
        this.playerUrl = origin + "/player/";
        this.html = html;
    }

    static PokerVideoPage create(List<PokerVideoSearch.Video> videos, int initialIndex,
            int startSeconds, boolean initialMuted, int initialVolume,
            String language, boolean ordered, long revision) throws IOException {
        if (videos == null || videos.isEmpty()) {
            throw new IllegalArgumentException("At least one video is required");
        }
        String template = loadTemplate();
        // Reserve the origin for this session without opening a listening socket.
        Socket reservation = new Socket();
        try {
            reservation.bind(new InetSocketAddress("127.0.0.1", 0));
            String origin = "http://127.0.0.1:" + reservation.getLocalPort();
            String ids = videos.stream().map(PokerVideoSearch.Video::id)
                    .map(PokerVideoPage::javascriptString)
                    .map(value -> "'" + value + "'")
                    .collect(Collectors.joining(","));
            int safeIndex = Math.max(0, Math.min(initialIndex, videos.size() - 1));
            int safeSeconds = Math.max(0, startSeconds);
            int safeVolume = Math.max(0, Math.min(100, initialVolume));
            byte[] html = template.replace("@@VIDEO_IDS@@", ids)
                    .replace("@@INITIAL_INDEX@@", Integer.toString(safeIndex))
                    .replace("@@ORDERED_PLAYLIST@@", Boolean.toString(ordered))
                    .replace("@@PLAYLIST_REVISION@@", Long.toString(revision))
                    .replace("@@START_SECONDS@@", Integer.toString(safeSeconds))
                    .replace("@@INITIAL_MUTED@@", Boolean.toString(initialMuted))
                    .replace("@@INITIAL_VOLUME@@", Integer.toString(safeVolume))
                    .replace("@@PLAYER_LANGUAGE@@", javascriptString(
                            PokerVideoSearch.languageCode(language)))
                    .replace("@@ORIGIN@@", javascriptString(origin))
                    .getBytes(StandardCharsets.UTF_8);
            return new PokerVideoPage(reservation, origin, html);
        } catch (IOException | RuntimeException failure) {
            try { reservation.close(); } catch (IOException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    private static String loadTemplate() throws IOException {
        try (InputStream input = PokerVideoPage.class.getResourceAsStream("/poker-video-player.html")) {
            if (input == null) throw new IOException("Missing poker-video-player.html");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String javascriptString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("<", "\\u003c").replace(">", "\\u003e");
    }

    String playerUrl() { return playerUrl; }
    String origin() { return origin; }
    boolean owns(String url) { return url != null && url.startsWith(origin + "/"); }

    @Override
    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
        return resource(request.getURL(), request.getMethod(), frame != null && frame.isMain());
    }

    CefResourceHandler resource(String url, String method, boolean mainFrame) {
        return mainFrame && playerUrl.equals(url) && "GET".equals(method)
                ? new DocumentHandler(html) : null;
    }

    @Override
    public void close() {
        try { originReservation.close(); } catch (IOException ignored) { }
    }

    private static final class DocumentHandler extends CefResourceHandlerAdapter {
        private final byte[] body;
        private int offset;

        DocumentHandler(byte[] body) { this.body = body; }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            return true;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirect) {
            response.setStatus(200);
            response.setStatusText("OK");
            response.setMimeType("text/html");
            response.setHeaderMap(HEADERS);
            length.set(body.length);
        }

        @Override
        public boolean read(byte[] output, int requested, IntRef count, CefResourceReadCallback callback) {
            return copy(output, requested, count);
        }

        @Override
        public boolean readResponse(byte[] output, int requested, IntRef count, CefCallback callback) {
            return copy(output, requested, count);
        }

        private boolean copy(byte[] output, int requested, IntRef count) {
            int size = Math.min(requested, body.length - offset);
            count.set(size);
            if (size <= 0) return false;
            System.arraycopy(body, offset, output, 0, size);
            offset += size;
            return true;
        }
    }
}
