package com.tonikelope.coronapoker.gdx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/** Shared-property adapter for Swing's chat image history. */
final class GdxChatImageHistory {

    static final String HISTORY_KEY = "chat_img_hist";
    static final String AUTO_RECEIVE_KEY = "chat_img_hist_auto_rec";
    static final int MAX_ENTRIES = 48;

    private GdxChatImageHistory() {
    }

    static List<String> read(Properties properties) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String encoded = properties.getProperty(HISTORY_KEY, "");
        if (!encoded.isBlank()) {
            for (String item : encoded.split("@")) {
                if (item.isBlank()) continue;
                try {
                    String url = new String(Base64.getDecoder().decode(item),
                            StandardCharsets.UTF_8).trim();
                    if (isHttpUrl(url)) result.add(url);
                } catch (IllegalArgumentException ignored) {
                    // Keep a damaged legacy entry from breaking the gallery.
                }
                if (result.size() >= MAX_ENTRIES) break;
            }
        }
        return List.copyOf(result);
    }

    static boolean autoReceive(Properties properties) {
        return Boolean.parseBoolean(properties.getProperty(
                AUTO_RECEIVE_KEY, "true"));
    }

    static void setAutoReceive(Properties properties, boolean enabled) {
        properties.setProperty(AUTO_RECEIVE_KEY, Boolean.toString(enabled));
    }

    static List<String> remember(Properties properties, String url,
            boolean newest) {
        if (!isHttpUrl(url)) return read(properties);
        ArrayList<String> updated = new ArrayList<>(read(properties));
        updated.remove(url.trim());
        if (newest) updated.add(0, url.trim());
        else updated.add(url.trim());
        while (updated.size() > MAX_ENTRIES) {
            updated.remove(updated.size() - 1);
        }
        write(properties, updated);
        return List.copyOf(updated);
    }

    static void clear(Properties properties) {
        properties.setProperty(HISTORY_KEY, "");
    }

    static boolean isHttpUrl(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://");
    }

    private static void write(Properties properties, List<String> history) {
        List<String> encoded = history.stream()
                .map(value -> Base64.getEncoder().encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)))
                .toList();
        properties.setProperty(HISTORY_KEY, String.join("@", encoded));
    }
}
