/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serves the player from a real loopback origin so embeds receive a Referer. */
final class PokerVideoLocalServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final byte[] playerHtml;
    private final String origin;

    private PokerVideoLocalServer(HttpServer server, ExecutorService executor,
            byte[] playerHtml, String origin) {
        this.server = server;
        this.executor = executor;
        this.playerHtml = playerHtml;
        this.origin = origin;
    }

    static PokerVideoLocalServer start(List<PokerVideoSearch.Video> videos,
            int initialIndex, int startSeconds, boolean initialMuted,
            int initialVolume, String language) throws IOException {
        return start(videos, initialIndex, startSeconds, initialMuted,
                initialVolume, language, false);
    }

    static PokerVideoLocalServer start(List<PokerVideoSearch.Video> videos,
            int initialIndex, int startSeconds, boolean initialMuted,
            int initialVolume, String language, boolean recoverAudioPipeline)
            throws IOException {
        if (videos == null || videos.isEmpty()) {
            throw new IllegalArgumentException("At least one video is required");
        }
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "CoronaPoker-poker-video-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        String template = loadTemplate();
        String ids = videos.stream().map(PokerVideoSearch.Video::id)
                .map(PokerVideoLocalServer::javascriptString)
                .map(value -> "'" + value + "'")
                .collect(java.util.stream.Collectors.joining(","));
        int safeIndex = Math.max(0, Math.min(initialIndex,
                videos.size() - 1));
        int safeSeconds = Math.max(0, startSeconds);
        int safeVolume = Math.max(0, Math.min(100, initialVolume));
        byte[] html = template.replace("@@VIDEO_IDS@@", ids)
                .replace("@@INITIAL_INDEX@@", Integer.toString(safeIndex))
                .replace("@@START_SECONDS@@", Integer.toString(safeSeconds))
                .replace("@@INITIAL_MUTED@@",
                        Boolean.toString(initialMuted))
                .replace("@@INITIAL_VOLUME@@",
                        Integer.toString(safeVolume))
                .replace("@@RECOVER_AUDIO_PIPELINE@@",
                        Boolean.toString(recoverAudioPipeline))
                .replace("@@PLAYER_LANGUAGE@@", javascriptString(
                        PokerVideoSearch.languageCode(language)))
                .replace("@@ORIGIN@@", javascriptString(origin))
                .getBytes(StandardCharsets.UTF_8);
        PokerVideoLocalServer local = new PokerVideoLocalServer(server,
                executor, html, origin);
        server.createContext("/", local::handle);
        server.start();
        return local;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/favicon.ico")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!path.equals("/") && !path.equals("/player")
                    && !path.equals("/player/")) {
                byte[] notFound = "Not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, notFound.length);
                exchange.getResponseBody().write(notFound);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type",
                    "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Referrer-Policy",
                    "strict-origin-when-cross-origin");
            exchange.getResponseHeaders().set("X-Content-Type-Options",
                    "nosniff");
            exchange.getResponseHeaders().set("Permissions-Policy",
                    "autoplay=(self \"https://www.youtube.com\")");
            exchange.sendResponseHeaders(200, playerHtml.length);
            exchange.getResponseBody().write(playerHtml);
        }
    }

    private static String loadTemplate() throws IOException {
        try (InputStream input = PokerVideoLocalServer.class
                .getResourceAsStream("/poker-video-player.html")) {
            if (input == null) {
                throw new IOException("Missing poker-video-player.html");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String javascriptString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("<", "\\u003c").replace(">", "\\u003e");
    }

    String playerUrl() {
        return origin + "/player/";
    }

    String origin() {
        return origin;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
