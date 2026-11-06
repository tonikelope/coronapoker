/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the anonymous, ordered video results from the configured poker search. */
final class PokerVideoSearch {

    static final URI SEARCH_URI = URI.create(
            "https://www.youtube.com/results?search_query=poker+shorts");
    static final int MAX_RESULTS = 40;
    private static final int MAX_RESPONSE_CHARS = 12 * 1024 * 1024;
    private static final Pattern FALLBACK_SHORT_ID = Pattern.compile(
            "\\\"url\\\"\\s*:\\s*\\\"/shorts/([A-Za-z0-9_-]{11})");

    record Video(String id, String title) {

        Video {
            if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
                throw new IllegalArgumentException("Invalid YouTube video id");
            }
            title = title == null || title.isBlank()
                    ? "Short de póker" : title.strip();
        }
    }

    private PokerVideoSearch() {
    }

    static List<Video> fetch(String language) throws IOException,
            InterruptedException {
        String acceptedLanguage = language == null || language.isBlank()
                ? "es" : language.toLowerCase(Locale.ROOT);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(SEARCH_URI)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/152.0.0.0 Safari/537.36")
                .header("Accept-Language", acceptedLanguage + ",en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YouTube search returned HTTP "
                    + response.statusCode());
        }
        if (response.body().length() > MAX_RESPONSE_CHARS) {
            throw new IOException("YouTube search response is unexpectedly large");
        }
        List<Video> videos = parse(response.body());
        if (videos.isEmpty()) {
            throw new IOException("YouTube search did not expose playable videos");
        }
        return videos;
    }

    static List<Video> parse(String html) {
        if (html == null || html.isBlank()) return List.of();
        LinkedHashMap<String, Video> ordered = new LinkedHashMap<>();
        String initialData = extractInitialData(html);
        if (initialData != null) {
            try {
                collectShorts(new JsonReader().parse(initialData), ordered);
            } catch (RuntimeException ignored) {
                // YouTube changes this non-public document periodically. The
                // conservative ID-only fallback still preserves document order.
            }
        }
        if (ordered.isEmpty()) {
            Matcher matcher = FALLBACK_SHORT_ID.matcher(html);
            while (matcher.find() && ordered.size() < MAX_RESULTS) {
                String id = matcher.group(1);
                ordered.putIfAbsent(id, new Video(id, "Short de póker"));
            }
        }
        return List.copyOf(ordered.values());
    }

    private static void collectShorts(JsonValue value,
            Map<String, Video> ordered) {
        if (value == null || ordered.size() >= MAX_RESULTS) return;
        if (value.isObject()) {
            JsonValue lockup = value.get("shortsLockupViewModel");
            if (lockup != null) addLockup(lockup, ordered);
            JsonValue legacy = value.get("reelItemRenderer");
            if (legacy != null) addLegacyRenderer(legacy, ordered);
        }
        for (JsonValue child = value.child; child != null
                && ordered.size() < MAX_RESULTS; child = child.next) {
            collectShorts(child, ordered);
        }
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
        ordered.put(id, new Video(id, title));
    }

    private static void addLegacyRenderer(JsonValue renderer,
            Map<String, Video> ordered) {
        String id = renderer.getString("videoId", "");
        if (!id.matches("[A-Za-z0-9_-]{11}") || ordered.containsKey(id)) {
            return;
        }
        ordered.put(id, new Video(id, legacyRendererTitle(renderer)));
    }

    private static String legacyRendererTitle(JsonValue renderer) {
        JsonValue title = renderer.get("title");
        if (title == null) return "Short de póker";
        String simple = title.getString("simpleText", "");
        if (!simple.isBlank()) return simple;
        JsonValue runs = title.get("runs");
        if (runs == null) return "Short de póker";
        ArrayList<String> fragments = new ArrayList<>();
        for (JsonValue run = runs.child; run != null; run = run.next) {
            String text = run.getString("text", "");
            if (!text.isBlank()) fragments.add(text);
        }
        String joined = String.join("", fragments).strip();
        return joined.isBlank() ? "Short de póker" : joined;
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
                    || json.contains("reelItemRenderer"))) return json;
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
