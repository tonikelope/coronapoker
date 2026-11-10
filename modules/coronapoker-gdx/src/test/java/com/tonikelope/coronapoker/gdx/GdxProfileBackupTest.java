/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.gdx.GdxProfileBackup.Category;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

final class GdxProfileBackupTest {

    @TempDir Path home;

    @Test
    void fullZipRestoresAllCategoriesAndEmptyDirectories() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "lenguaje=en");
        write(profile, "poker-video-favorites.properties", "favorites");
        write(profile, "coronapoker.db", "database");
        write(profile, "coronapoker.db-wal", "wal");
        write(profile, "identity_alex.ed25519", "identity");
        write(profile, "Debug/old.log", "debug");
        write(profile, "Logs/game.log", "game");
        write(profile, "voice/note.wav", "voice");
        write(profile, "nested/ñ/other.txt", "other");
        Files.createDirectory(profile.resolve("empty"));
        Path zip = home.resolve("full.zip");
        var all = EnumSet.allOf(Category.class);
        GdxProfileBackup.exportProfile(profile, zip, all, false);
        assertEquals(all, GdxProfileBackup.categories(zip));

        write(profile, "coronapoker.properties", "lenguaje=es");
        write(profile, "Debug/current.log", "current");
        Path previous = GdxProfileBackup.restoreProfile(profile, zip, all);
        assertEquals("lenguaje=en", Files.readString(profile.resolve("coronapoker.properties")));
        assertEquals("favorites", Files.readString(profile.resolve("poker-video-favorites.properties")));
        assertEquals("database", Files.readString(profile.resolve("coronapoker.db")));
        assertEquals("wal", Files.readString(profile.resolve("coronapoker.db-wal")));
        assertEquals("identity", Files.readString(profile.resolve("identity_alex.ed25519")));
        assertEquals("debug", Files.readString(profile.resolve("Debug/old.log")));
        assertEquals("game", Files.readString(profile.resolve("Logs/game.log")));
        assertEquals("voice", Files.readString(profile.resolve("voice/note.wav")));
        assertEquals("other", Files.readString(profile.resolve("nested/ñ/other.txt")));
        assertTrue(Files.isDirectory(profile.resolve("empty")));
        assertFalse(Files.exists(profile.resolve("Debug/current.log")));
        assertEquals("lenguaje=es", Files.readString(previous.resolve("coronapoker.properties")));
        assertEquals("current", Files.readString(previous.resolve("Debug/current.log")));
    }

    @Test
    void exportReportsProgressWithinOneLargeSelectedFile() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        Files.write(profile.resolve("coronapoker.db"), new byte[256 * 1024]);
        Files.write(profile.resolve("unselected.bin"), new byte[256 * 1024]);
        Path zip = home.resolve("progress.zip");
        List<Integer> progress = new ArrayList<>();

        GdxProfileBackup.exportProfile(profile, zip, Set.of(Category.DATABASE),
                false, progress::add);

        assertEquals(List.of(0, 25, 50, 75, 99, 100), progress);
        assertEquals(Set.of(Category.DATABASE), GdxProfileBackup.categories(zip));
    }

    @Test
    void selectiveRestorePreservesUnselectedCategories() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "exported");
        write(profile, "coronapoker.db", "exported database");
        write(profile, "voice/note.wav", "keep");
        Path zip = home.resolve("selected.zip");
        GdxProfileBackup.exportProfile(profile, zip, Set.of(Category.SETTINGS, Category.DATABASE), false);
        assertEquals(Set.of(Category.SETTINGS, Category.DATABASE), GdxProfileBackup.categories(zip));

        write(profile, "coronapoker.properties", "current");
        write(profile, "coronapoker.db", "current database");
        GdxProfileBackup.restoreProfile(profile, zip, Set.of(Category.SETTINGS));
        assertEquals("exported", Files.readString(profile.resolve("coronapoker.properties")));
        assertEquals("current database", Files.readString(profile.resolve("coronapoker.db")));
        assertEquals("keep", Files.readString(profile.resolve("voice/note.wav")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void cancellingBeforeSaveRemovesTemporaryZipAndReturnsError(int cancelAt)
            throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        Files.write(profile.resolve("coronapoker.db"), new byte[256 * 1024]);
        Path archive = home.resolve("cancel.zip");
        var writing = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var export = new GdxProfileBackup.ExportControl(percent -> {
            if (percent < cancelAt) return;
            writing.countDown();
            try {
                assertTrue(resume.await(10, TimeUnit.SECONDS), "Export did not resume");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        });
        var request = new GdxProfileBackupTransfer.Request(false, archive,
                Set.of(Category.DATABASE), "en");
        var result = CompletableFuture.supplyAsync(() -> request.run(profile, export));
        try {
            assertTrue(writing.await(10, TimeUnit.SECONDS), "Export did not start writing");
            try (var paths = Files.list(home)) {
                assertTrue(paths.anyMatch(path -> path.getFileName().toString()
                        .startsWith(".coronapoker-export-")));
            }
            assertTrue(export.cancel());
        } finally {
            resume.countDown();
        }

        var cancelled = result.get(10, TimeUnit.SECONDS);
        assertTrue(cancelled.error());
        assertEquals("gdx.backup.export_cancelled", cancelled.messageKey());
        assertFalse(Files.exists(archive));
        assertFalse(Files.exists(home.resolve(".coronapoker.lock")));
        try (var paths = Files.list(home)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".coronapoker-export-")));
        }
    }

    @Test
    void rejectsUnsafeZipPathsBeforeChangingProfile() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "original");
        for (String bad : new String[]{
            ".coronapoker/../escape", ".coronapoker/.. /escape",
            ".coronapoker/path\\escape", ".coronapoker/NUL.txt", ".coronapoker/x:stream",
            ".coronapoker//absolute", "outside"
        }) {
            Path zip = home.resolve("unsafe.zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
                output.putNextEntry(new ZipEntry(".coronapoker/"));
                output.closeEntry();
                output.putNextEntry(new ZipEntry(bad));
                output.write(42);
                output.closeEntry();
            }
            assertThrows(IOException.class, () -> GdxProfileBackup.categories(zip), bad);
            assertThrows(IOException.class,
                    () -> GdxProfileBackup.restoreProfile(profile, zip, Set.of(Category.OTHER)), bad);
        }
        assertEquals("original", Files.readString(profile.resolve("coronapoker.properties")));
        assertFalse(Files.exists(home.resolve("escape")));
    }

    @Test
    void corruptedZipEntryLeavesProfileInPlace() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "original");
        Path zip = home.resolve("damaged.zip");
        byte[] payload = "zip-payload-that-will-be-damaged".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(".coronapoker/"));
            output.closeEntry();
            ZipEntry entry = new ZipEntry(".coronapoker/coronapoker.properties");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            CRC32 crc = new CRC32();
            crc.update(payload);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(payload);
            output.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(zip);
        int offset = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                .indexOf("zip-payload-that-will-be-damaged");
        assertTrue(offset >= 0);
        bytes[offset] ^= 1;
        Files.write(zip, bytes);

        assertThrows(IOException.class,
                () -> GdxProfileBackup.restoreProfile(profile, zip, Set.of(Category.SETTINGS)));
        assertEquals("original", Files.readString(profile.resolve("coronapoker.properties")));
        try (var paths = Files.list(home)) {
            assertEquals(2, paths.count());
        }
    }

    @Test
    void preventsSelfInclusionAndRequiresApprovalToReplaceZip() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "original");
        Path zip = home.resolve("existing.zip");
        Files.writeString(zip, "existing");
        var categories = Set.of(Category.SETTINGS);
        assertThrows(IOException.class,
                () -> GdxProfileBackup.exportProfile(profile, profile.resolve("inside.zip"), categories, false));
        assertThrows(IOException.class,
                () -> GdxProfileBackup.exportProfile(profile, zip, categories, false));
        assertEquals("existing", Files.readString(zip));
        GdxProfileBackup.exportProfile(profile, zip, categories, true);
        assertEquals(categories, GdxProfileBackup.categories(zip));
    }

    @Test
    void exportUsesAnAvailableNameInTheUserHome() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "settings");
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 15, 30);
        Path original = GdxProfileBackupTransfer.exportPath(profile, now);
        assertEquals(home.resolve("CoronaPoker-backup-20261109_153000_000.zip"), original);
        Files.writeString(original, "existing");

        Path archive = GdxProfileBackupTransfer.exportPath(profile, now);
        assertEquals(home.resolve("CoronaPoker-backup-20261109_153000_000-2.zip"), archive);
        var categories = Set.of(Category.SETTINGS);
        GdxProfileBackup.exportProfile(profile, archive, categories, false);
        assertEquals("existing", Files.readString(original));
        assertEquals(categories, GdxProfileBackup.categories(archive));
    }

    @Test
    void alternateDestinationKeepsExistingZipAndRejectsProfileDirectory() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        Path folder = Files.createDirectory(home.resolve("Alternate backups"));
        Path occupied = folder.resolve("CoronaPoker-backup-example.zip");
        Files.writeString(occupied, "existing");
        write(profile, "coronapoker.properties", "settings");

        Path directory = GdxProfileBackup.exportDirectory(profile, folder);
        Path archive = GdxProfileBackupTransfer.availableExportPath(directory.resolve(occupied.getFileName()));
        assertEquals(folder.toRealPath().resolve("CoronaPoker-backup-example-2.zip"), archive);
        GdxProfileBackup.exportProfile(profile, archive, Set.of(Category.SETTINGS), false);
        assertEquals("existing", Files.readString(occupied));
        assertEquals(Set.of(Category.SETTINGS), GdxProfileBackup.categories(archive));
        assertThrows(IOException.class, () -> GdxProfileBackup.exportDirectory(profile, profile));
    }

    @Test
    void profileMustBeClosedBeforeBackup() throws Exception {
        Path profile = home.resolve(".coronapoker");
        try (var openProfile = GdxProfileBackup.lock(profile, true)) {
            assertThrows(IOException.class, () -> GdxProfileBackup.lock(profile, false));
        }
        try (var exclusive = GdxProfileBackup.lock(profile, false)) {
            assertTrue(exclusive.isOpen());
        }
        assertFalse(Files.exists(home.resolve(".coronapoker.lock")));
    }

    @Test
    void profileReplacementKeepsTheSameLock() throws Exception {
        Path profile = Files.createDirectory(home.resolve(".coronapoker"));
        write(profile, "coronapoker.properties", "settings");
        Path archive = home.resolve("restore.zip");
        GdxProfileBackup.exportProfile(profile, archive, Set.of(Category.SETTINGS), false);

        try (var exclusive = GdxProfileBackup.lock(profile, false)) {
            GdxProfileBackup.restoreProfile(profile, archive, Set.of(Category.SETTINGS));
            assertThrows(IOException.class, () -> GdxProfileBackup.lock(profile, true));
            assertFalse(Files.exists(home.resolve(".coronapoker.lock")));
        }
        try (var openProfile = GdxProfileBackup.lock(profile, true)) {
            assertTrue(openProfile.isOpen());
        }
    }

    private static void write(Path profile, String relative, String contents) throws IOException {
        Path file = profile.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
