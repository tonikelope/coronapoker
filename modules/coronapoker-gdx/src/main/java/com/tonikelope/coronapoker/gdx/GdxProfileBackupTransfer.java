/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;

final class GdxProfileBackupTransfer {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String RESULT = "CORONAPOKER_BACKUP_RESULT";
    private static final String DETAIL = "CORONAPOKER_BACKUP_DETAIL";
    private static final String CANCELLED = "gdx.backup.export_cancelled";

    record Request(boolean importing, Path archive,
            Set<GdxProfileBackup.Category> categories, String language) {

        Request {
            categories = Set.copyOf(categories);
        }

        Result run(Path profile, IntConsumer exportProgress) {
            return run(profile, new GdxProfileBackup.ExportControl(exportProgress));
        }

        Result run(Path profile, GdxProfileBackup.ExportControl export) {
            try (var lock = GdxProfileBackup.lock(profile, false)) {
                if (importing) {
                    Path previous = GdxProfileBackup.restoreProfile(profile, archive, categories);
                    return new Result("gdx.backup.import_done", previous.toString());
                }
                GdxProfileBackup.exportProfile(profile, archive, categories, false, export);
                return new Result("gdx.backup.export_done", archive.toString());
            } catch (CancellationException cancelled) {
                return new Result(CANCELLED, "");
            } catch (IOException | RuntimeException failure) {
                return new Result("gdx.backup.failed",
                        failure.getMessage() == null ? failure.toString() : failure.getMessage());
            }
        }
    }

    record Result(String messageKey, String detail) {

        boolean error() {
            return "gdx.backup.failed".equals(messageKey) || CANCELLED.equals(messageKey);
        }

        void writeTo(ProcessBuilder process) {
            process.environment().put(RESULT, messageKey);
            process.environment().put(DETAIL, detail.replace('\0', '\uFFFD'));
        }

        static Result fromEnvironment() {
            String key = System.getenv(RESULT);
            if (CANCELLED.equals(key)) return new Result(key, "");
            String detail = System.getenv(DETAIL);
            if (detail == null || !Set.of("gdx.backup.import_done",
                    "gdx.backup.export_done", "gdx.backup.failed").contains(
                            key == null ? "" : key)) return null;
            return new Result(key, detail);
        }
    }

    static Path exportPath(Path profile, LocalDateTime now) {
        return availableExportPath(profile.resolveSibling(
                "CoronaPoker-backup-" + FILE_TIME.format(now) + ".zip"));
    }

    static Path availableExportPath(Path archive) {
        String filename = archive.getFileName().toString();
        String base = filename.substring(0, filename.length() - ".zip".length());
        Path candidate = archive;
        for (int suffix = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); suffix++) {
            candidate = archive.resolveSibling(base + "-" + suffix + ".zip");
        }
        return candidate;
    }

    private GdxProfileBackupTransfer() {
    }
}
