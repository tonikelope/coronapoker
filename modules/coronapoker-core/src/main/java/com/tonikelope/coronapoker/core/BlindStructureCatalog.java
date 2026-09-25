/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Renderer-neutral reader for the blind structures stored by the classic UI.
 * Invalid or duplicate entries are ignored so a damaged preference never
 * prevents either frontend from opening its settings.
 */
public final class BlindStructureCatalog {

    public static final int MAX_STRUCTURES = 64;
    public static final int MAX_NAME_LENGTH = 40;
    public static final String COUNT_KEY = "blind_structures.count";
    public static final String PREFIX = "blind_structure.";

    public record Entry(String name, List<BlindLevel> levels) {
        public Entry {
            name = name == null ? "" : name.trim();
            levels = List.copyOf(levels);
        }
    }

    public record BlindLevel(double smallBlind, double bigBlind) {
    }

    private BlindStructureCatalog() {
    }

    public static List<Entry> read(Properties properties) {
        if (properties == null) return List.of();
        int count;
        try {
            count = Integer.parseInt(properties.getProperty(COUNT_KEY, "0")
                    .trim());
        } catch (NumberFormatException invalid) {
            return List.of();
        }
        count = Math.max(0, Math.min(MAX_STRUCTURES, count));
        ArrayList<Entry> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < count; index++) {
            String name = properties.getProperty(PREFIX + index + ".name");
            String encoded = properties.getProperty(
                    PREFIX + index + ".levels");
            if (!validName(name) || encoded == null || !names.add(name.trim())) {
                continue;
            }
            try {
                double[][] parsed = parseLevels(encoded);
                if (BlindStructureRules.validateLevels(parsed) != null) continue;
                ArrayList<BlindLevel> levels = new ArrayList<>(parsed.length);
                for (double[] pair : parsed) {
                    levels.add(new BlindLevel(pair[0], pair[1]));
                }
                result.add(new Entry(name, levels));
            } catch (IllegalArgumentException invalid) {
                // Match the classic reader: a malformed entry is skipped.
            }
        }
        return List.copyOf(result);
    }

    /**
     * Replaces the persisted catalogue without flushing the owning preference
     * service. Every entry is validated before any property is changed, so a
     * rejected edit cannot leave a partially-written registry behind.
     */
    public static void writeTo(Properties properties,
            Collection<Entry> entries) {
        if (properties == null) return;

        ArrayList<Entry> validated = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null) {
                    throw new IllegalArgumentException(
                            "Blind structure must not be null");
                }
                if (validated.size() >= MAX_STRUCTURES) {
                    throw new IllegalArgumentException(
                            "Too many blind structures");
                }
                String name = entry.name().trim();
                if (!validName(name)) {
                    throw new IllegalArgumentException(
                            "Invalid blind structure name");
                }
                if (!names.add(name)) {
                    throw new IllegalArgumentException(
                            "Duplicate blind structure name: " + name);
                }
                double[][] levels = rawLevels(entry.levels());
                String validationError =
                        BlindStructureRules.validateLevels(levels);
                if (validationError != null) {
                    throw new IllegalArgumentException(validationError);
                }
                validated.add(new Entry(name, entry.levels()));
            }
        }

        ArrayList<String> stale = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals(COUNT_KEY) || key.startsWith(PREFIX)) {
                stale.add(key);
            }
        }
        stale.forEach(properties::remove);

        for (int index = 0; index < validated.size(); index++) {
            Entry entry = validated.get(index);
            properties.setProperty(PREFIX + index + ".name", entry.name());
            properties.setProperty(PREFIX + index + ".levels",
                    serializeLevels(entry.levels()));
        }
        properties.setProperty(COUNT_KEY,
                Integer.toString(validated.size()));
    }

    /** Returns whether a user-facing structure name is persistable. */
    public static boolean isValidName(String value) {
        return validName(value);
    }

    private static boolean validName(String value) {
        if (value == null) return false;
        String name = value.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return false;
        return name.codePoints().noneMatch(Character::isISOControl);
    }

    private static double[][] parseLevels(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("empty blind structure");
        }
        String[] tokens = encoded.trim().split(",");
        double[][] levels = new double[tokens.length][];
        for (int index = 0; index < tokens.length; index++) {
            String[] pair = tokens[index].trim().split("/");
            if (pair.length != 2) {
                throw new IllegalArgumentException("malformed blind level");
            }
            try {
                levels[index] = new double[]{
                    Double.parseDouble(pair[0].trim()),
                    Double.parseDouble(pair[1].trim())
                };
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("malformed blind amount",
                        invalid);
            }
        }
        return levels;
    }

    private static double[][] rawLevels(List<BlindLevel> levels) {
        if (levels == null) return null;
        double[][] raw = new double[levels.size()][];
        for (int index = 0; index < levels.size(); index++) {
            BlindLevel level = levels.get(index);
            if (level != null) {
                raw[index] = new double[]{level.smallBlind(),
                    level.bigBlind()};
            }
        }
        return raw;
    }

    private static String serializeLevels(List<BlindLevel> levels) {
        StringBuilder encoded = new StringBuilder();
        for (BlindLevel level : levels) {
            if (!encoded.isEmpty()) encoded.append(',');
            encoded.append(formatAmount(level.smallBlind())).append('/')
                    .append(formatAmount(level.bigBlind()));
        }
        return encoded.toString();
    }

    private static String formatAmount(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value) : Double.toString(value);
    }
}
