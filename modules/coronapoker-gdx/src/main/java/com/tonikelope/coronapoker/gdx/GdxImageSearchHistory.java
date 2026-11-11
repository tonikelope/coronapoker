/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/** Saved image searches, their page boundaries and the last selected search. */
final class GdxImageSearchHistory {
    static final int PAGE_SIZE = 50;
    private static final String KEY = "gdx_image_search_history";
    private static final History EMPTY = new History("", List.of());
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private GdxImageSearchHistory() { }

    static String key(String term) {
        return Normalizer.normalize(SPACES.matcher(term).replaceAll(" ").strip(), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }

    static History read(Properties properties) {
        String stored = properties.getProperty(KEY, "");
        if (stored.isEmpty()) return EMPTY;
        try {
            JsonValue data = new JsonReader().parse(stored);
            JsonValue saved = data.get("searches");
            if (saved == null) {
                Snapshot legacy = readSnapshot(data, 0);
                return legacy == null ? EMPTY : new History(legacy.term(), List.of(legacy));
            }
            var searches = new LinkedHashMap<String, Snapshot>();
            int savedPageSize = data.getInt("pageSize", 0);
            if (saved.isArray()) {
                for (JsonValue entry : saved) {
                    Snapshot snapshot = readSnapshot(entry, savedPageSize);
                    if (snapshot != null) searches.putIfAbsent(key(snapshot.term()), snapshot);
                }
            }
            if (searches.isEmpty()) return EMPTY;
            Snapshot selected = searches.get(key(data.getString("selected", "")));
            return new History(selected == null ? searches.values().iterator().next().term()
                    : selected.term(), List.copyOf(searches.values()));
        } catch (RuntimeException invalid) {
            return EMPTY;
        }
    }

    private static Snapshot readSnapshot(JsonValue data, int savedPageSize) {
        try {
            String term = data.getString("term", "").strip();
            if (key(term).isEmpty() || term.length() > 160) return null;
            List<GoogleImageResults.Image> images = new ArrayList<>();
            JsonValue list = data.get("images");
            if (list != null && list.isArray()) {
                for (JsonValue item : list) {
                    if (!item.isObject()) continue;
                    var image = GoogleImageResults.image(item.getString("original", ""),
                            item.getString("thumbnail", ""), item.getBoolean("animated", false));
                    if (image != null) images.add(image);
                }
            }
            List<Integer> starts = new ArrayList<>();
            JsonValue savedStarts = data.get("pageStarts");
            if (savedStarts != null && savedStarts.isArray() && list != null && list.size == images.size()) {
                for (JsonValue start : savedStarts) starts.add(start.asInt());
            }
            boolean currentLayout = savedPageSize == PAGE_SIZE;
            boolean valid = validPageStarts(starts, images.size(), currentLayout ? PAGE_SIZE : Integer.MAX_VALUE);
            int page = Math.max(0, Math.min(data.getInt("page", 0), starts.size() - 1));
            if (!currentLayout || !valid) {
                int anchor = valid && !starts.isEmpty() ? starts.get(page) : 0;
                starts = splitPages(images.size());
                page = anchor / PAGE_SIZE;
            }
            return new Snapshot(term, List.copyOf(images),
                    Math.max(GoogleImageResults.NO_MORE_RESULTS, data.getInt("nextOffset", 0)),
                    List.copyOf(starts), page);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean validPageStarts(List<Integer> starts, int size, int maxPageSize) {
        if (starts.isEmpty()) return size == 0;
        if (starts.get(0) != 0) return false;
        for (int page = 0; page < starts.size(); page++) {
            int end = page + 1 < starts.size() ? starts.get(page + 1) : size;
            int start = starts.get(page);
            if (end <= start || end > size || end - start > maxPageSize) return false;
        }
        return true;
    }

    private static List<Integer> splitPages(int size) {
        List<Integer> starts = new ArrayList<>();
        addPageStarts(starts, 0, size);
        return List.copyOf(starts);
    }

    private static void addPageStarts(List<Integer> starts, int start, int end) {
        for (int index = start; index < end; index += PAGE_SIZE) starts.add(index);
    }

    static void write(Properties properties, History history) {
        if (history.searches().isEmpty()) {
            properties.remove(KEY);
            return;
        }
        Json json = new Json(JsonWriter.OutputType.json);
        json.setTypeName(null);
        json.setUsePrototypes(false);
        properties.setProperty(KEY, json.toJson(history));
    }

    record History(String selected, List<Snapshot> searches, int pageSize) {
        History(String selected, List<Snapshot> searches) {
            this(selected, searches, PAGE_SIZE);
        }
    }

    record Snapshot(String term, List<GoogleImageResults.Image> images, int nextOffset,
            List<Integer> pageStarts, int page) {
        Snapshot(String term, List<GoogleImageResults.Image> images, int nextOffset) {
            this(term, images, nextOffset, splitPages(images.size()), 0);
        }

        int pageCount() {
            return pageStarts.size();
        }

        int firstIndex() {
            return pageStarts.isEmpty() ? 0 : pageStarts.get(page);
        }

        List<GoogleImageResults.Image> pageImages() {
            int end = page + 1 < pageStarts.size() ? pageStarts.get(page + 1) : images.size();
            return images.subList(firstIndex(), end);
        }

        Snapshot withPage(int selected) {
            return new Snapshot(term, images, nextOffset, pageStarts, selected);
        }

        Snapshot append(List<GoogleImageResults.Image> combined, int next, boolean advance) {
            List<Integer> starts = new ArrayList<>(pageStarts);
            addPageStarts(starts, images.size(), combined.size());
            int selected = advance && starts.size() > pageStarts.size() ? pageStarts.size() : page;
            return new Snapshot(term, combined, next, List.copyOf(starts), selected);
        }
    }
}
