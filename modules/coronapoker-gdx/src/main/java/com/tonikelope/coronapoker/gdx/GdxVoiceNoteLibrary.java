/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.audio.VoiceWavContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Native, UI-free access to the voice-note library shared with Swing. */
final class GdxVoiceNoteLibrary {

    record Entry(Path path, long timestampMillis, String nickname,
            long durationMillis) {
    }

    private final Path root;

    GdxVoiceNoteLibrary() {
        this(Path.of(System.getProperty("user.home"), ".coronapoker",
                "voice"));
    }

    GdxVoiceNoteLibrary(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    List<Entry> list() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (var children = Files.list(root)) {
            for (Path child : children.toList()) {
                Entry entry = readEntry(child);
                if (entry != null) entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingLong(Entry::timestampMillis)
                .reversed());
        return List.copyOf(entries);
    }

    byte[] read(Entry entry) throws IOException {
        Path path = requireSafeDirectFile(entry);
        byte[] wav = Files.readAllBytes(path);
        if (!VoiceWavContract.isValid(wav)) {
            throw new IOException("Invalid CoronaPoker voice note");
        }
        return wav;
    }

    Entry store(String nickname, byte[] wav) throws IOException {
        if (!VoiceWavContract.isValid(wav)) {
            throw new IOException("Invalid CoronaPoker voice note");
        }
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            throw new IOException("Voice note library is unavailable");
        }
        long timestamp = System.currentTimeMillis();
        String safeNickname = (nickname == null ? "player" : nickname)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeNickname.isBlank()) safeNickname = "player";
        String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8);
        Path destination = root.resolve(timestamp + "_" + safeNickname
                + "_" + suffix + ".wav");
        Files.write(destination, wav);
        return new Entry(destination.toAbsolutePath().normalize(), timestamp,
                safeNickname, VoiceWavContract.durationMillis(wav));
    }

    int purgeExpired(int retentionDays, long nowMillis) throws IOException {
        if (retentionDays <= 0
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            return 0;
        }
        long cutoff = nowMillis - retentionDays * 24L * 60L * 60L * 1000L;
        int deleted = 0;
        try (var children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (isSafeDirectWav(child)
                        && Files.getLastModifiedTime(child).toMillis() < cutoff
                        && Files.deleteIfExists(child)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    boolean delete(Entry entry) throws IOException {
        return Files.deleteIfExists(requireSafeDirectFile(entry));
    }

    int purge() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            return 0;
        }
        int deleted = 0;
        try (var children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (isSafeDirectWav(child) && Files.deleteIfExists(child)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private Entry readEntry(Path candidate) {
        try {
            if (!isSafeDirectWav(candidate)) return null;
            byte[] wav = Files.readAllBytes(candidate);
            long duration = VoiceWavContract.durationMillis(wav);
            if (duration < 0L) return null;
            String filename = candidate.getFileName().toString();
            String base = filename.substring(0, filename.length() - 4);
            int first = base.indexOf('_');
            int last = base.lastIndexOf('_');
            long timestamp = Files.getLastModifiedTime(candidate).toMillis();
            String nickname = base;
            if (first > 0 && last > first) {
                try {
                    timestamp = Long.parseLong(base.substring(0, first));
                } catch (NumberFormatException ignored) {
                    // Preserve the filesystem timestamp fallback used by Swing.
                }
                nickname = base.substring(first + 1, last);
            }
            return new Entry(candidate.toAbsolutePath().normalize(),
                    timestamp, nickname, duration);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Path requireSafeDirectFile(Entry entry) throws IOException {
        if (entry == null || !isSafeDirectWav(entry.path())) {
            throw new IOException("Voice note is outside the library");
        }
        return entry.path().toAbsolutePath().normalize();
    }

    private boolean isSafeDirectWav(Path candidate) {
        if (candidate == null) return false;
        Path normalized = candidate.toAbsolutePath().normalize();
        return root.equals(normalized.getParent())
                && normalized.getFileName().toString().toLowerCase(
                        Locale.ROOT).endsWith(".wav")
                && !Files.isSymbolicLink(normalized)
                && Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS);
    }
}
