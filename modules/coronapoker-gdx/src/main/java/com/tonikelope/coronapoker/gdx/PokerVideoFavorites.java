/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** A lazy, atomically saved catalogue independent of search results and CEF. */
final class PokerVideoFavorites {
    private record Entry(PokerVideoSearch.Video video, String type) { }
    private final Path file;
    private LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<PokerVideoSearch.Video>> views = new HashMap<>();
    private boolean loaded;
    private IOException loadFailure;

    PokerVideoFavorites(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    static PokerVideoFavorites userStore() {
        return new PokerVideoFavorites(Path.of(System.getProperty("user.home"),
                ".coronapoker", "poker-video-favorites.properties"));
    }

    synchronized boolean contains(PokerVideoSearch.Video video, String type) {
        load();
        return video != null && entries.containsKey(key(video.id(), type));
    }

    synchronized List<PokerVideoSearch.Video> videos(String type) {
        load();
        String normalized = PokerVideoSearch.videoType(type);
        return views.computeIfAbsent(normalized, ignored -> entries.values().stream()
                .filter(entry -> entry.type().equals(normalized)).map(Entry::video).toList());
    }

    synchronized void toggle(PokerVideoSearch.Video video, String type) throws IOException {
        load();
        if (loadFailure != null) throw new IOException("Cannot read video favorites", loadFailure);
        String normalized = PokerVideoSearch.videoType(type);
        String key = key(video.id(), normalized);
        var next = new LinkedHashMap<>(entries);
        if (next.remove(key) == null) next.put(key, new Entry(video, normalized));
        save(next);
        entries = next;
        views.clear();
    }

    private static String key(String id, String type) {
        return PokerVideoSearch.videoType(type) + "." + id;
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (Files.notExists(file)) return;
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Favorites file is too large");
            Properties saved = new Properties();
            saved.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            for (String key : saved.getProperty("order", "").split(",")) {
                int separator = key.indexOf('.');
                if (separator < 0) continue;
                String type = key.substring(0, separator);
                if (!List.of(PokerVideoSearch.TYPE_SHORTS, PokerVideoSearch.TYPE_NORMAL).contains(type)) continue;
                try {
                    var video = new PokerVideoSearch.Video(key.substring(separator + 1),
                            saved.getProperty(key));
                    entries.putIfAbsent(key, new Entry(video, type));
                } catch (IllegalArgumentException invalidEntry) {
                    // An invalid entry cannot become a URL or discard other favorites.
                }
            }
        } catch (IOException | IllegalArgumentException failure) {
            entries.clear();
            loadFailure = failure instanceof IOException io ? io : new IOException(failure);
        }
    }

    private void save(LinkedHashMap<String, Entry> next) throws IOException {
        Properties saved = new Properties();
        saved.setProperty("order", String.join(",", next.keySet()));
        next.forEach((key, entry) -> saved.setProperty(key, entry.video().title()));
        StringWriter text = new StringWriter();
        saved.store(text, "CoronaPoker poker video favorites");
        if (text.toString().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new IOException("Favorites file is too large");
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "poker-favorites-", ".tmp");
        try {
            Files.writeString(temporary, text.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
