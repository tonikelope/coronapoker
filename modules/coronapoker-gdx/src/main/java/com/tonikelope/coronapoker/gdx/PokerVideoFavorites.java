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
import java.util.List;
import java.util.Properties;

/** A lazy, atomically saved catalogue independent of search results and CEF. */
final class PokerVideoFavorites {
    private final Path file;
    private LinkedHashMap<String, PokerVideoSearch.Video> entries = new LinkedHashMap<>();
    private List<PokerVideoSearch.Video> ordered = List.of();
    private boolean loaded;
    private IOException loadFailure;

    PokerVideoFavorites(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    static PokerVideoFavorites userStore() {
        return new PokerVideoFavorites(Path.of(System.getProperty("user.home"),
                ".coronapoker", "poker-video-favorites.properties"));
    }

    synchronized boolean contains(PokerVideoSearch.Video video) {
        load();
        return video != null && entries.containsKey(key(video));
    }

    synchronized List<PokerVideoSearch.Video> videos() {
        load();
        return ordered;
    }

    synchronized void toggle(PokerVideoSearch.Video video) throws IOException {
        load();
        if (loadFailure != null) throw new IOException("Cannot read video favorites", loadFailure);
        String key = key(video);
        var next = new LinkedHashMap<>(entries);
        if (next.remove(key) == null) next.put(key, video);
        save(next);
        entries = next;
        ordered = List.copyOf(entries.values());
    }

    private static String key(PokerVideoSearch.Video video) {
        return video.type() + "." + video.id();
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
                            saved.getProperty(key), type);
                    entries.putIfAbsent(key, video);
                } catch (IllegalArgumentException invalidEntry) {
                    // An invalid entry cannot become a URL or discard other favorites.
                }
            }
            ordered = List.copyOf(entries.values());
        } catch (IOException | IllegalArgumentException failure) {
            entries.clear();
            loadFailure = failure instanceof IOException io ? io : new IOException(failure);
        }
    }

    private void save(LinkedHashMap<String, PokerVideoSearch.Video> next) throws IOException {
        Properties saved = new Properties();
        saved.setProperty("order", String.join(",", next.keySet()));
        next.forEach((key, video) -> saved.setProperty(key, video.title()));
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
