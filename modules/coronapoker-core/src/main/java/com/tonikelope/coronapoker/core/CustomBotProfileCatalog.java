/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.random.RandomGenerator;
import java.util.Set;

/** Persistent, ordered catalogue used by the custom-bot manager. */
public final class CustomBotProfileCatalog {

    public static final String PROP_ENABLED = "custom_bots.enabled";
    public static final String PROP_RANDOM_SELECTION =
            "custom_bots.random_selection";
    public static final String PROP_COUNT = "custom_bots.count";
    public static final String PROP_PREFIX = "custom_bot.";
    public static final int MAX_PROFILES = 50;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");

    private CustomBotProfileCatalog() {
    }

    /** Returns the host preference for using the ordered custom catalogue. */
    public static boolean enabled(Properties properties) {
        return properties != null && Boolean.parseBoolean(
                properties.getProperty(PROP_ENABLED, "false"));
    }

    /** Persists only the catalogue switch, leaving every profile untouched. */
    public static void writeEnabled(Properties properties, boolean enabled) {
        if (properties != null) {
            properties.setProperty(PROP_ENABLED, Boolean.toString(enabled));
        }
    }

    /** Returns whether a new table shuffles the catalogue before admission. */
    public static boolean randomSelection(Properties properties) {
        return properties != null && Boolean.parseBoolean(
                properties.getProperty(PROP_RANDOM_SELECTION, "false"));
    }

    /** Persists the host's admission policy without rewriting the catalogue. */
    public static void writeRandomSelection(Properties properties,
            boolean enabled) {
        if (properties != null) {
            properties.setProperty(PROP_RANDOM_SELECTION,
                    Boolean.toString(enabled));
        }
    }

    /** Creates the stable, duplicate-free admission order for one new lobby. */
    public static List<CustomBotProfile> profilesForSession(
            Collection<CustomBotProfile> profiles, boolean randomSelection,
            RandomGenerator random) {
        ArrayList<CustomBotProfile> result = new ArrayList<>();
        if (profiles != null) {
            for (CustomBotProfile profile : profiles) {
                if (profile != null) result.add(profile);
            }
        }
        if (randomSelection && result.size() > 1) {
            Objects.requireNonNull(random, "random");
            for (int index = result.size() - 1; index > 0; index--) {
                int target = random.nextInt(index + 1);
                CustomBotProfile swap = result.get(index);
                result.set(index, result.get(target));
                result.set(target, swap);
            }
        }
        return List.copyOf(result);
    }

    public static List<CustomBotProfile> readFrom(Properties properties) {
        if (properties == null) return List.of();
        int count;
        try {
            count = Integer.parseInt(properties.getProperty(
                    PROP_COUNT, "0").trim());
        } catch (NumberFormatException invalid) {
            return List.of();
        }
        count = Math.max(0, Math.min(count, MAX_PROFILES));
        ArrayList<CustomBotProfile> result = new ArrayList<>();
        HashSet<String> nicknames = new HashSet<>();
        for (int index = 0; index < count; index++) {
            String prefix = PROP_PREFIX + index + '.';
            String nickname = properties.getProperty(prefix + "nickname", "");
            String difficulty = properties.getProperty(prefix + "difficulty",
                    NewGameTableDraft.BotDifficulty.MEDIUM.name());
            String personality = properties.getProperty(prefix + "personality",
                    CustomBotProfile.Personality.RANDOM.name());
            String storedAvatar = properties.getProperty(prefix + "avatar", "");
            try {
                String canonical = Normalizer.normalize(nickname.trim(),
                        Normalizer.Form.NFC);
                if (!nicknames.add(canonical)) continue;
                Path avatar = validStoredAvatar(storedAvatar);
                result.add(new CustomBotProfile(canonical, avatar,
                        NewGameTableDraft.BotDifficulty.valueOf(difficulty),
                        CustomBotProfile.Personality.valueOf(personality)));
            } catch (IllegalArgumentException ignored) {
                // A hand-edited entry must not hide the remaining profiles.
            }
        }
        return List.copyOf(result);
    }

    public static void writeTo(Properties properties,
            Collection<CustomBotProfile> profiles) {
        if (properties == null) return;
        ArrayList<String> stale = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals(PROP_COUNT) || key.startsWith(PROP_PREFIX)) {
                stale.add(key);
            }
        }
        stale.forEach(properties::remove);
        int index = 0;
        if (profiles != null) {
            for (CustomBotProfile profile : profiles) {
                if (profile == null || index >= MAX_PROFILES) break;
                String prefix = PROP_PREFIX + index + '.';
                properties.setProperty(prefix + "nickname", profile.nickname());
                properties.setProperty(prefix + "difficulty",
                        profile.difficulty().name());
                properties.setProperty(prefix + "personality",
                        profile.personality().name());
                properties.setProperty(prefix + "avatar",
                        profile.avatar() == null ? ""
                                : profile.avatar().toString());
                index++;
            }
        }
        properties.setProperty(PROP_COUNT, Integer.toString(index));
    }

    /** Imports a validated avatar into CoronaPoker-owned durable storage. */
    public static Path importAvatar(Path source, Path coronaDirectory)
            throws IOException {
        Path candidate = Objects.requireNonNull(source, "source")
                .toAbsolutePath().normalize();
        AvatarImageValidator.validate(candidate);
        byte[] bytes = Files.readAllBytes(candidate);
        String extension = imageExtension(candidate);
        Path directory = Objects.requireNonNull(coronaDirectory,
                "coronaDirectory").toAbsolutePath().normalize()
                .resolve("custom-bot-avatars");
        Files.createDirectories(directory);
        Path target = directory.resolve(hex(sha256(bytes)) + extension);
        if (Files.isRegularFile(target)) {
            AvatarImageValidator.validate(target);
            return target;
        }
        Path temporary = Files.createTempFile(directory, ".avatar-", ".tmp");
        boolean committed = false;
        try {
            Files.write(temporary, bytes);
            AvatarImageValidator.validate(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            committed = true;
            return target;
        } finally {
            if (!committed) Files.deleteIfExists(temporary);
        }
    }

    private static Path validStoredAvatar(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            AvatarImageValidator.validate(path);
            return path;
        } catch (IOException | RuntimeException invalid) {
            return null;
        }
    }

    private static String imageExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : IMAGE_EXTENSIONS) {
            if (name.endsWith(extension)) return extension;
        }
        return ".img";
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }
}
