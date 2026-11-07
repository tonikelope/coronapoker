/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serves the player from a real loopback origin so embeds receive a Referer. */
final class PokerVideoLocalServer implements AutoCloseable {

    private final ServerSocket server;
    private final ExecutorService executor;
    private final ExecutorService requests = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "CoronaPoker-poker-video-request");
                thread.setDaemon(true);
                return thread;
            });
    private final byte[] playerHtml;
    private final String origin;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();

    private PokerVideoLocalServer(ServerSocket server, ExecutorService executor,
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
                initialVolume, language, false, 0L);
    }

    static PokerVideoLocalServer start(List<PokerVideoSearch.Video> videos,
            int initialIndex, int startSeconds, boolean initialMuted, int initialVolume,
            String language, boolean ordered, long revision) throws IOException {
        if (videos == null || videos.isEmpty()) {
            throw new IllegalArgumentException("At least one video is required");
        }
        String template = loadTemplate();
        ServerSocket server = new ServerSocket(0, 8,
                InetAddress.getByName("127.0.0.1"));
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "CoronaPoker-poker-video-http");
            thread.setDaemon(true);
            return thread;
        });
        String origin = "http://127.0.0.1:" + server.getLocalPort();
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
                .replace("@@ORDERED_PLAYLIST@@", Boolean.toString(ordered))
                .replace("@@PLAYLIST_REVISION@@", Long.toString(revision))
                .replace("@@START_SECONDS@@", Integer.toString(safeSeconds))
                .replace("@@INITIAL_MUTED@@",
                        Boolean.toString(initialMuted))
                .replace("@@INITIAL_VOLUME@@",
                        Integer.toString(safeVolume))
                .replace("@@PLAYER_LANGUAGE@@", javascriptString(
                        PokerVideoSearch.languageCode(language)))
                .replace("@@ORIGIN@@", javascriptString(origin))
                .getBytes(StandardCharsets.UTF_8);
        PokerVideoLocalServer local = new PokerVideoLocalServer(server,
                executor, html, origin);
        executor.submit(local::acceptRequests);
        return local;
    }

    private void acceptRequests() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                connections.add(client);
                if (server.isClosed()) {
                    closeConnection(client);
                    return;
                }
                try {
                    requests.execute(() -> {
                        try {
                            client.setSoTimeout(2000);
                            handle(client);
                        } catch (IOException ignored) {
                            // Idle speculative connections and browser cancellation are expected.
                        } finally {
                            closeConnection(client);
                        }
                    });
                } catch (RejectedExecutionException busy) {
                    closeConnection(client);
                }
            } catch (IOException ignored) {
                // Closing the listener unblocks accept without retaining any sockets.
            }
        }
    }

    private void closeConnection(Socket client) {
        try { client.close(); } catch (IOException ignored) { }
        connections.remove(client);
    }

    /** Only serves a fixed document; no filesystem, proxying or request bodies. */
    private void handle(Socket client) throws IOException {
        InputStream input = new BufferedInputStream(client.getInputStream());
        StringBuilder headers = new StringBuilder();
        long deadline = System.nanoTime() + 2_000_000_000L;
        int next;
        while (headers.length() < 16384 && System.nanoTime() < deadline
                && (next = input.read()) != -1) {
            headers.append((char) next);
            if (headers.length() >= 4 && headers.substring(headers.length() - 4)
                    .equals("\r\n\r\n")) break;
        }
        if (!headers.toString().endsWith("\r\n\r\n")) {
            respond(client, 400, "Bad Request", new byte[0], false);
            return;
        }
        String[] request = headers.substring(0, headers.indexOf("\r\n")).split(" ", 3);
        if (request.length != 3 || !request[2].startsWith("HTTP/1.")) {
            respond(client, 400, "Bad Request", new byte[0], false);
            return;
        }
        boolean head = "HEAD".equals(request[0]);
        if (!head && !"GET".equals(request[0])) {
            respond(client, 405, "Method Not Allowed", new byte[0], false);
            return;
        }
        String path;
        try {
            URI target = URI.create(request[1]);
            if (target.isAbsolute() || target.getRawAuthority() != null) {
                throw new IllegalArgumentException("Not an origin-form target");
            }
            path = target.getPath();
        } catch (IllegalArgumentException invalid) {
            respond(client, 400, "Bad Request", new byte[0], head);
            return;
        }
        if ("/favicon.ico".equals(path)) {
            respond(client, 204, "No Content", new byte[0], head);
        } else if ("/".equals(path) || "/player".equals(path) || "/player/".equals(path)) {
            respond(client, 200, "OK", playerHtml, head);
        } else {
            respond(client, 404, "Not Found", new byte[0], head);
        }
    }

    private static void respond(Socket client, int status, String reason,
            byte[] body, boolean head) throws IOException {
        OutputStream output = client.getOutputStream();
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Referrer-Policy: strict-origin-when-cross-origin\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Permissions-Policy: autoplay=(self \"https://www.youtube.com\")\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        if (!head) output.write(body);
        output.flush();
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
        try { server.close(); } catch (IOException ignored) { }
        connections.forEach(this::closeConnection);
        requests.shutdownNow();
        executor.shutdownNow();
    }
}
