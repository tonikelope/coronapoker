/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Copies a closed CoronaPoker profile without touching a live database. */
final class GdxProfileBackup {

    static final String PROFILE = ".coronapoker";
    private static final String ROOT = PROFILE + "/";
    private static final Pattern INVALID_COMPONENT = Pattern.compile(
            "[\\x00-\\x1f\\\\:<>\"|?*]|[ .]$");
    private static final Pattern DEVICE_NAME = Pattern.compile(
            "^(?:CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³])(?:\\.|$)",
            Pattern.CASE_INSENSITIVE);

    enum Category {
        SETTINGS, DATABASE, IDENTITY, LOGS, OTHER;

        String textKey() {
            return "gdx.backup.category." + name().toLowerCase(Locale.ROOT);
        }

        static Category forName(String relativeName) {
            String name = relativeName.split("/", 2)[0].toLowerCase(Locale.ROOT);
            if (name.startsWith("coronapoker.properties")
                    || name.startsWith("poker-video-favorites.properties")) return SETTINGS;
            if (name.equals("coronapoker.db") || name.startsWith("coronapoker.db-")) {
                return DATABASE;
            }
            if (name.startsWith("identity_")) return IDENTITY;
            if (name.equals("debug") || name.equals("logs")) return LOGS;
            return OTHER;
        }
    }

    private record Entry(ZipEntry zip, String relative, Category category) {
    }

    static Path profile() {
        return Path.of(System.getProperty("user.home"), PROFILE)
                .toAbsolutePath().normalize();
    }

    static FileChannel lock(Path profile, boolean shared) throws IOException {
        Path home = profile.toAbsolutePath().normalize().getParent();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path runtime;
        if (os.startsWith("windows")) {
            runtime = home.resolve("AppData/Local/CoronaPoker/runtime");
        } else if (os.startsWith("mac") || os.startsWith("darwin")) {
            runtime = home.resolve("Library/Caches/CoronaPoker/runtime");
        } else {
            runtime = home.resolve(".cache/coronapoker/runtime");
        }
        // Stay outside the profile so import can replace it while the lock is held.
        Files.createDirectories(runtime);
        Path lockFile = runtime.resolve("profile.lock");
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            if (channel.tryLock(0, Long.MAX_VALUE, shared) != null) return channel;
        } catch (OverlappingFileLockException busy) {
            // Another owner in this process already holds the profile lock.
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
        channel.close();
        throw new IOException("CoronaPoker profile is in use");
    }

    static Set<Category> categories(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return categories(entries(zip));
        }
    }

    static Path exportDirectory(Path profile, Path directory) throws IOException {
        Path parent = directory.toRealPath();
        if (parent.startsWith(profile.toRealPath())) {
            throw new IOException("Save the ZIP outside " + PROFILE);
        }
        if (!Files.isDirectory(parent)) {
            throw new IOException("Backup destination is not a directory: " + parent);
        }
        return parent;
    }

    static void exportProfile(Path profile, Path archive, Set<Category> selected,
            boolean replace) throws IOException {
        exportProfile(profile, archive, selected, replace, percent -> {});
    }

    /** Reports 0-99 while compressing and 100 while finalizing the ZIP. */
    static void exportProfile(Path profile, Path archive, Set<Category> selected,
            boolean replace, IntConsumer progress) throws IOException {
        exportProfile(profile, archive, selected, replace, new ExportControl(progress));
    }

    static void exportProfile(Path profile, Path archive, Set<Category> selected,
            boolean replace, ExportControl control) throws IOException {
        control.checkCancelled();
        if (selected.isEmpty()) throw new IOException("No backup categories selected");
        requireDirectory(profile);
        Path destination = archive.toAbsolutePath().normalize();
        Path parent = exportDirectory(profile, destination.getParent());
        destination = parent.resolve(destination.getFileName());
        List<Path> roots = children(profile).stream()
                .filter(top -> selected.contains(Category.forName(top.getFileName().toString())))
                .toList();
        control.start(exportBytes(roots, control));
        control.checkCancelled();
        Path temporary = Files.createTempFile(parent, ".coronapoker-export-", ".tmp");
        try {
            PlayerIdentity.restrictToOwner(temporary);
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                output.putNextEntry(new ZipEntry(ROOT));
                output.closeEntry();
                byte[] buffer = new byte[64 * 1024];
                for (Path top : roots) {
                    writeTree(profile, top, output, buffer, control);
                }
                control.finishWriting();
            }
            control.beginSave();
            if (replace) {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Path restoreProfile(Path profile, Path archive, Set<Category> selected)
            throws IOException {
        requireDirectory(profile);
        Path staging = Files.createTempDirectory(profile.getParent(), PROFILE + "-restore-");
        try {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                List<Entry> entries = entries(zip);
                if (selected.isEmpty() || !categories(entries).containsAll(selected)) {
                    throw new IOException("Selected categories are missing from the ZIP");
                }
                for (Path top : children(profile)) {
                    if (!selected.contains(Category.forName(top.getFileName().toString()))) {
                        copyTree(profile, top, staging);
                    }
                }
                for (Entry entry : entries) {
                    if (selected.contains(entry.category())) extract(zip, entry, staging);
                }
            }
            Path previous = profile.resolveSibling(PROFILE + "-before-import-" + UUID.randomUUID());
            Files.move(profile, previous);
            try {
                Files.move(staging, profile);
            } catch (IOException failure) {
                try {
                    Files.move(previous, profile);
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                    throw new IOException("Previous profile: " + previous, failure);
                }
                throw failure;
            }
            return previous;
        } finally {
            deleteTree(staging);
        }
    }

    private static Set<Category> categories(List<Entry> entries) {
        Set<Category> categories = EnumSet.noneOf(Category.class);
        for (Entry entry : entries) categories.add(entry.category());
        return categories;
    }

    private static List<Entry> entries(ZipFile zip) throws IOException {
        List<Entry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        var elements = zip.entries();
        boolean rootSeen = false;
        while (elements.hasMoreElements()) {
            ZipEntry entry = elements.nextElement();
            String name = entry.getName();
            if (name.equals(ROOT)) {
                if (rootSeen) throw new IOException("Duplicate backup root");
                rootSeen = true;
                continue;
            }
            if (!name.startsWith(ROOT)) throw new IOException("Invalid backup root");
            String relative = name.substring(ROOT.length(), name.length()
                    - (entry.isDirectory() ? 1 : 0));
            validateName(relative);
            if (!names.add(relative.toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate backup entry: " + relative);
            }
            if (entry.getSize() < 0 || entry.getCrc() < 0) {
                throw new IOException("Invalid ZIP metadata: " + relative);
            }
            entries.add(new Entry(entry, relative, Category.forName(relative)));
        }
        if (!rootSeen) throw new IOException("Missing CoronaPoker backup root");
        return entries;
    }

    private static void validateName(String relative) throws IOException {
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || INVALID_COMPONENT.matcher(part).find()
                    || DEVICE_NAME.matcher(part).find()) {
                throw new IOException("Invalid backup path: " + relative);
            }
        }
    }

    private static void extract(ZipFile zip, Entry entry, Path staging) throws IOException {
        Path destination = staging.resolve(entry.relative()).normalize();
        if (!destination.startsWith(staging)) throw new IOException("Invalid backup path");
        if (entry.zip().isDirectory()) {
            Files.createDirectories(destination);
        } else {
            Files.createDirectories(destination.getParent());
            createProfileFile(entry.relative(), destination);
            CRC32 crc = new CRC32();
            try (InputStream input = new CheckedInputStream(zip.getInputStream(entry.zip()), crc);
                    OutputStream output = Files.newOutputStream(destination, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > entry.zip().getSize()) {
                        throw new IOException("Invalid size: " + entry.relative());
                    }
                    output.write(buffer, 0, count);
                }
                if (size != entry.zip().getSize() || crc.getValue() != entry.zip().getCrc()) {
                    throw new IOException("Damaged ZIP entry: " + entry.relative());
                }
            }
        }
        if (entry.zip().getLastModifiedTime() != null) {
            Files.setLastModifiedTime(destination, entry.zip().getLastModifiedTime());
        }
    }

    private static long exportBytes(List<Path> roots, ExportControl control) throws IOException {
        class FileSizes extends SimpleFileVisitor<Path> {
            long total;

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                control.checkCancelled();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                control.checkCancelled();
                if (attrs.isRegularFile()) total += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        }
        var sizes = new FileSizes();
        for (Path top : roots) Files.walkFileTree(top, sizes);
        return sizes.total;
    }

    /** One export with a single decision between cancellation and saving. */
    static final class ExportControl {
        private enum State { RUNNING, CANCELLED, SAVING }

        private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
        private final IntConsumer callback;
        private long total;
        private long bytes;
        private int percent;

        ExportControl(IntConsumer callback) {
            this.callback = callback;
        }

        boolean cancel() {
            return state.compareAndSet(State.RUNNING, State.CANCELLED);
        }

        boolean cancellable() {
            return state.get() == State.RUNNING;
        }

        boolean cancelled() {
            return state.get() == State.CANCELLED;
        }

        private void checkCancelled() {
            if (cancelled()) throw new CancellationException();
        }

        private void beginSave() {
            if (!state.compareAndSet(State.RUNNING, State.SAVING)) {
                throw new CancellationException();
            }
        }

        private void start(long total) {
            this.total = Math.max(1, total);
            callback.accept(0);
        }

        private void advance(int count) {
            bytes += count;
            int next = (int) Math.min(99, bytes * 100.0 / total);
            if (next > percent) {
                percent = next;
                callback.accept(percent);
            }
        }

        private void finishWriting() {
            checkCancelled();
            callback.accept(100);
        }
    }

    private static void writeTree(Path profile, Path top, ZipOutputStream output,
            byte[] buffer, ExportControl control) throws IOException {
        Path realProfile = profile.toRealPath();
        try (Stream<Path> tree = Files.walk(top)) {
            Iterator<Path> paths = tree.iterator();
            while (paths.hasNext()) {
                control.checkCancelled();
                Path path = paths.next();
                requireWithinProfile(profile, realProfile, path);
                boolean directory = isDirectoryOrFile(path);
                String relative = profile.relativize(path).toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                validateName(relative);
                ZipEntry entry = new ZipEntry(ROOT + relative + (directory ? "/" : ""));
                entry.setLastModifiedTime(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS));
                output.putNextEntry(entry);
                if (!directory) {
                    try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            control.checkCancelled();
                            output.write(buffer, 0, count);
                            control.advance(count);
                        }
                    }
                }
                output.closeEntry();
            }
        }
    }

    private static void copyTree(Path profile, Path top, Path staging) throws IOException {
        Path realProfile = profile.toRealPath();
        try (Stream<Path> tree = Files.walk(top)) {
            Iterator<Path> paths = tree.iterator();
            while (paths.hasNext()) {
                Path path = paths.next();
                requireWithinProfile(profile, realProfile, path);
                Path relative = profile.relativize(path);
                Path destination = staging.resolve(relative);
                if (!isDirectoryOrFile(path) && privateIdentity(relative.toString())) {
                    createProfileFile(relative.toString(), destination);
                    try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                            OutputStream output = Files.newOutputStream(destination, StandardOpenOption.WRITE)) {
                        input.transferTo(output);
                    }
                    Files.setLastModifiedTime(destination, Files.getLastModifiedTime(path));
                } else {
                    Files.copy(path, destination,
                            LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void createProfileFile(String relative, Path destination) throws IOException {
        Files.createFile(destination);
        if (privateIdentity(relative)) PlayerIdentity.restrictToOwner(destination);
    }

    private static boolean privateIdentity(String relative) {
        return Category.forName(relative) == Category.IDENTITY
                && relative.toLowerCase(Locale.ROOT).endsWith(".ed25519");
    }

    private static void requireWithinProfile(Path profile, Path realProfile, Path path)
            throws IOException {
        if (!path.toRealPath().equals(realProfile.resolve(profile.relativize(path)))) {
            throw new IOException("Unsupported linked profile entry: " + path);
        }
    }

    private static List<Path> children(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.toList();
        }
    }

    private static boolean isDirectoryOrFile(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return true;
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        throw new IOException("Unsupported profile entry: " + path);
    }

    private static void requireDirectory(Path profile) throws IOException {
        if (!Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid CoronaPoker profile: " + profile);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> paths;
        try (Stream<Path> tree = Files.walk(root)) {
            paths = tree.toList();
        }
        for (int index = paths.size() - 1; index >= 0; index--) {
            Files.delete(paths.get(index));
        }
    }

    private GdxProfileBackup() {
    }
}
