/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the anonymous, ordered video results from the configured poker search. */
final class PokerVideoSearch {

    private static final String SEARCH_BASE =
            "https://www.youtube.com/results?search_query=";
    static final String TYPE_SHORTS = "shorts";
    static final String TYPE_NORMAL = "normal";
    static final int MAX_RESULTS = 40;
    private static final int MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
    private static final Pattern FALLBACK_SHORT_ID = Pattern.compile(
            "\\\"url\\\"\\s*:\\s*\\\"/shorts/([A-Za-z0-9_-]{11})");
    private static final Pattern FALLBACK_NORMAL_ID = Pattern.compile(
            "\\\"url\\\"\\s*:\\s*\\\"/watch\\?v=([A-Za-z0-9_-]{11})");

    record Video(String id, String title, String type) {

        Video(String id, String title) {
            this(id, title, TYPE_NORMAL);
        }

        Video {
            if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
                throw new IllegalArgumentException("Invalid YouTube video id");
            }
            title = title == null || title.isBlank()
                    ? "Vídeo de póker" : title.strip();
            type = videoType(type);
        }
    }

    private PokerVideoSearch() {
    }

    static List<Video> fetch(String language) throws IOException,
            InterruptedException {
        return fetch(language, TYPE_NORMAL);
    }

    static List<Video> fetch(String language, String videoType)
            throws IOException, InterruptedException {
        String acceptedLanguage = languageCode(language);
        String fallbackLanguage = "es".equals(acceptedLanguage)
                ? "en" : "es";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String normalizedType = videoType(videoType);
        HttpRequest request = HttpRequest.newBuilder(searchUri(language,
                normalizedType))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/152.0.0.0 Safari/537.36")
                .header("Accept-Language", acceptedLanguage + ","
                        + fallbackLanguage + ";q=0.8")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        List<Video> videos = parse(fetchBody(client, request, MAX_RESPONSE_BYTES),
                normalizedType);
        if (videos.isEmpty()) {
            throw new IOException("YouTube search did not expose playable videos");
        }
        return videos;
    }

    static String fetchBody(HttpClient client, HttpRequest request, int maxBytes)
            throws IOException, InterruptedException {
        var pending = client.sendAsync(request, boundedBodyHandler(maxBytes));
        HttpResponse<String> response;
        try {
            response = pending.get(request.timeout().orElse(Duration.ofSeconds(15))
                    .toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new HttpTimeoutException("YouTube search response timed out");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            throw interrupted;
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("YouTube search response failed", failure.getCause());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YouTube search returned HTTP "
                    + response.statusCode());
        }
        return response.body();
    }

    /** Bounds allocation before accumulating each body chunk. */
    static HttpResponse.BodyHandler<String> boundedBodyHandler(int maxBytes) {
        return response -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofByteArrayConsumer(chunk -> {
                        if (chunk.isEmpty()) return;
                        byte[] data = chunk.get();
                        if (data.length > maxBytes - bytes.size()) {
                            throw new UncheckedIOException(new IOException(
                                    "YouTube search response is unexpectedly large"));
                        }
                        bytes.writeBytes(data);
                    }), ignored -> bytes.toString(StandardCharsets.UTF_8));
        };
    }

    static URI searchUri(String language) {
        return searchUri(language, TYPE_NORMAL);
    }

    static URI searchUri(String language, String videoType) {
        String code = languageCode(language);
        boolean shorts = TYPE_SHORTS.equals(videoType(videoType));
        String query = "es".equals(code)
                ? shorts ? "póker shorts español" : "vídeos de póker español"
                : shorts ? "poker shorts english" : "poker videos english";
        return URI.create(SEARCH_BASE + URLEncoder.encode(query,
                StandardCharsets.UTF_8) + "&hl=" + code);
    }

    static String videoType(String value) {
        return TYPE_SHORTS.equalsIgnoreCase(value == null ? "" : value.strip())
                ? TYPE_SHORTS : TYPE_NORMAL;
    }

    static String languageCode(String language) {
        String normalized = language == null ? ""
                : language.strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("en") ? "en" : "es";
    }

    static List<Video> parse(String html) {
        return parse(html, TYPE_SHORTS);
    }

    static List<Video> parse(String html, String videoType) {
        if (html == null || html.isBlank()) return List.of();
        String normalizedType = videoType(videoType);
        LinkedHashMap<String, Video> ordered = new LinkedHashMap<>();
        String initialData = extractInitialData(html);
        if (initialData != null) {
            try {
                collectVideos(new JsonReader().parse(initialData), ordered,
                        normalizedType);
            } catch (RuntimeException ignored) {
                // YouTube changes this non-public document periodically. The
                // conservative ID-only fallback still preserves document order.
            }
        }
        if (ordered.isEmpty()) {
            Matcher matcher = (TYPE_SHORTS.equals(normalizedType)
                    ? FALLBACK_SHORT_ID : FALLBACK_NORMAL_ID).matcher(html);
            while (matcher.find() && ordered.size() < MAX_RESULTS) {
                String id = matcher.group(1);
                ordered.putIfAbsent(id, new Video(id, "Vídeo de póker", normalizedType));
            }
        }
        return List.copyOf(ordered.values());
    }

    /** Randomizes one session without mutating the ordered search results. */
    static List<Video> shuffled(List<Video> videos, Random random) {
        Objects.requireNonNull(videos, "videos");
        Objects.requireNonNull(random, "random");
        ArrayList<Video> shuffled = new ArrayList<>(videos);
        Collections.shuffle(shuffled, random);
        return List.copyOf(shuffled);
    }

    private static void collectVideos(JsonValue value,
            Map<String, Video> ordered, String videoType) {
        if (value == null || ordered.size() >= MAX_RESULTS) return;
        if (value.isObject()) {
            if (TYPE_SHORTS.equals(videoType)) {
                JsonValue lockup = value.get("shortsLockupViewModel");
                if (lockup != null) addLockup(lockup, ordered);
                JsonValue legacy = value.get("reelItemRenderer");
                if (legacy != null) addLegacyRenderer(legacy, ordered);
            } else {
                JsonValue renderer = value.get("videoRenderer");
                if (renderer != null) addNormalRenderer(renderer, ordered);
            }
        }
        for (JsonValue child = value.child; child != null
                && ordered.size() < MAX_RESULTS; child = child.next) {
            collectVideos(child, ordered, videoType);
        }
    }

    private static void addNormalRenderer(JsonValue renderer,
            Map<String, Video> ordered) {
        String id = renderer.getString("videoId", "");
        if (!id.matches("[A-Za-z0-9_-]{11}") || ordered.containsKey(id)) {
            return;
        }
        ordered.put(id, new Video(id, rendererTitle(renderer)));
    }

    private static void addLockup(JsonValue lockup,
            Map<String, Video> ordered) {
        JsonValue endpoint = nested(lockup, "onTap", "innertubeCommand",
                "reelWatchEndpoint");
        String id = endpoint == null ? ""
                : endpoint.getString("videoId", "");
        if (!id.matches("[A-Za-z0-9_-]{11}") || ordered.containsKey(id)) {
            return;
        }
        JsonValue primaryText = nested(lockup, "overlayMetadata",
                "primaryText");
        String title = primaryText == null ? ""
                : primaryText.getString("content", "");
        ordered.put(id, new Video(id, title, TYPE_SHORTS));
    }

    private static void addLegacyRenderer(JsonValue renderer,
            Map<String, Video> ordered) {
        String id = renderer.getString("videoId", "");
        if (!id.matches("[A-Za-z0-9_-]{11}") || ordered.containsKey(id)) {
            return;
        }
        ordered.put(id, new Video(id, rendererTitle(renderer), TYPE_SHORTS));
    }

    private static String rendererTitle(JsonValue renderer) {
        JsonValue title = renderer.get("title");
        if (title == null) return "Vídeo de póker";
        String simple = title.getString("simpleText", "");
        if (!simple.isBlank()) return simple;
        JsonValue runs = title.get("runs");
        if (runs == null) return "Vídeo de póker";
        ArrayList<String> fragments = new ArrayList<>();
        for (JsonValue run = runs.child; run != null; run = run.next) {
            String text = run.getString("text", "");
            if (!text.isBlank()) fragments.add(text);
        }
        String joined = String.join("", fragments).strip();
        return joined.isBlank() ? "Vídeo de póker" : joined;
    }

    private static JsonValue nested(JsonValue root, String... names) {
        JsonValue value = root;
        for (String name : names) {
            if (value == null) return null;
            value = value.get(name);
        }
        return value;
    }

    private static String extractInitialData(String html) {
        int marker = html.indexOf("ytInitialData");
        while (marker >= 0) {
            int brace = html.indexOf('{', marker + "ytInitialData".length());
            if (brace < 0) return null;
            String json = balancedObject(html, brace);
            if (json != null && (json.contains("shortsLockupViewModel")
                    || json.contains("reelItemRenderer")
                    || json.contains("videoRenderer"))) return json;
            marker = html.indexOf("ytInitialData", brace + 1);
        }
        return null;
    }

    private static String balancedObject(String source, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) {
                return source.substring(start, index + 1);
            }
        }
        return null;
    }
}
