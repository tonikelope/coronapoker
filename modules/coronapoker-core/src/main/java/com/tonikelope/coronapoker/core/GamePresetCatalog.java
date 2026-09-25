package com.tonikelope.coronapoker.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Properties;

/**
 * Renderer-neutral persistence contract for named new-game presets.
 *
 * <p>The payload deliberately remains the classic {@code KEY=VALUE#...}
 * representation. Swing and GDX can therefore share the same entries without
 * either frontend depending on the other's widgets or configuration model.</p>
 */
public final class GamePresetCatalog {

    public static final String PROP_COUNT = "game_presets.count";
    public static final String PROP_PREFIX = "game_preset.";
    public static final int MAX_PRESETS = 100;
    public static final int MAX_NAME_LENGTH = 40;

    private GamePresetCatalog() {
    }

    public record Entry(String name, String settings) {
        public Entry {
            name = requireName(name);
            settings = Objects.requireNonNullElse(settings, "");
        }
    }

    public static String normalizeName(String value) {
        String name = requireName(value);
        return name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String requireName(String value) {
        String name = Objects.requireNonNullElse(value, "").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Preset name must not be blank");
        }
        return name;
    }

    /** Reads valid entries in their persisted order. */
    public static LinkedHashMap<String, Entry> readFrom(Properties properties) {
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        if (properties == null) return result;
        int count;
        try {
            count = Integer.parseInt(properties.getProperty(PROP_COUNT, "0").trim());
        } catch (NumberFormatException invalid) {
            return result;
        }
        count = Math.max(0, Math.min(count, MAX_PRESETS));
        for (int index = 0; index < count; index++) {
            String name = properties.getProperty(PROP_PREFIX + index + ".name");
            String settings = properties.getProperty(
                    PROP_PREFIX + index + ".settings");
            if (name == null || name.isBlank() || settings == null) continue;
            try {
                Entry entry = new Entry(name, settings);
                result.putIfAbsent(entry.name(), entry);
            } catch (IllegalArgumentException ignored) {
                // One hand-edited entry must never hide the remaining presets.
            }
        }
        return result;
    }

    /** Replaces the persisted catalogue without flushing the owner service. */
    public static void writeTo(Properties properties,
            Collection<Entry> entries) {
        if (properties == null) return;
        ArrayList<String> stale = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals(PROP_COUNT) || key.startsWith(PROP_PREFIX)) {
                stale.add(key);
            }
        }
        stale.forEach(properties::remove);
        int index = 0;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || index >= MAX_PRESETS) break;
                properties.setProperty(PROP_PREFIX + index + ".name",
                        entry.name());
                properties.setProperty(PROP_PREFIX + index + ".settings",
                        entry.settings());
                index++;
            }
        }
        properties.setProperty(PROP_COUNT, Integer.toString(index));
    }
}
