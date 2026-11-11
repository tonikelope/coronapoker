/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.ApplicationMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Shared bounded downloader for lobby and in-table chat images/GIFs.
 *
 * <p>The byte cache deliberately mirrors Swing's {@code ImageCacheManager}:
 * the URL fragment is ignored and chat files survive between sessions.
 * Search previews share the chat directory. The old search directory remains
 * readable and is included when clearing the cache.</p>
 */
final class GdxChatImageLoader {

    private static final int MAXIMUM_BYTES = 16 * 1024 * 1024;
    private static final long MAXIMUM_MEMORY_BYTES = 32L * 1024 * 1024;
    private static final String CHAT_CACHE = "ChatImagesCache";
    private static final String LEGACY_SEARCH_CACHE = "SearchImagesCache";
    // Guards disk cache access, the memory cache, and writes after a purge.
    private static final Object CACHE_LOCK = new Object();
    private static final LinkedHashMap<Path, byte[]> MEMORY_CACHE
            = new LinkedHashMap<>(64, 0.75f, true);
    private static long memoryBytes;
    private static long cacheGeneration;

    private enum Download { CHAT, PUBLIC_CHAT, SEARCH_PREVIEW }

    static byte[] download(String encodedUrl) {
        return download(encodedUrl, Download.CHAT);
    }

    static byte[] downloadPublic(String url) {
        return download(url, Download.PUBLIC_CHAT);
    }

    static byte[] downloadSearchPreview(String url) {
        return download(url, Download.SEARCH_PREVIEW);
    }

    private static byte[] download(String encodedUrl, Download mode) {
        String value = decodeChatImageUrl(encodedUrl);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))) {
                throw new IOException("Unsupported chat image scheme");
            }
            URL url = uri.toURL();
            String cacheKey = cacheFileName(url);
            Path chatFile = cacheDirectory(CHAT_CACHE).resolve(cacheKey);
            Path searchFile = cacheDirectory(LEGACY_SEARCH_CACHE).resolve(cacheKey);
            long generation;
            synchronized (CACHE_LOCK) {
                byte[] cached = readCached(chatFile);
                if (cached != null) return cached;
                cached = readCached(searchFile);
                if (cached != null) return cached;
                generation = cacheGeneration;
            }

            byte[] received = mode == Download.CHAT ? receive(url) : receivePublic(url);
            if (mode == Download.SEARCH_PREVIEW && !isGif(received) && !isWebp(received)) {
                throw new IOException("Search animation is unavailable");
            }
            synchronized (CACHE_LOCK) {
                // An in-flight download may finish without repopulating a purged cache.
                if (generation == cacheGeneration) {
                    writeCached(chatFile, received);
                }
            }
            return received;
        } catch (IOException | IllegalArgumentException failure) {
            throw new CompletionException(failure);
        }
    }

    private static Path cacheDirectory(String name) {
        return Path.of(System.getProperty("user.home"), ".coronapoker", name).toAbsolutePath().normalize();
    }

    private static byte[] readCached(Path file) throws IOException {
        byte[] cached = MEMORY_CACHE.get(file);
        if (cached != null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return cached;
        byte[] data = readBounded(file);
        memoryPut(file, data);
        return data;
    }

    private static void writeCached(Path file, byte[] data) throws IOException {
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, file.getFileName() + ".", ".part");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        memoryPut(file, data);
    }

    static long imageCacheSize(List<String> favorites) throws IOException {
        Set<String> protectedNames = favoriteCacheNames(favorites);
        synchronized (CACHE_LOCK) {
            long bytes = 0;
            for (Path file : purgeableFiles(cacheDirectories(), protectedNames)) {
                bytes += Files.size(file);
            }
            return bytes;
        }
    }

    private static List<Path> cacheDirectories() {
        return List.of(cacheDirectory(CHAT_CACHE), cacheDirectory(LEGACY_SEARCH_CACHE));
    }

    private static Set<String> favoriteCacheNames(List<String> favorites) {
        Set<String> names = new HashSet<>();
        for (String url : favorites) {
            try {
                names.add(cacheFileName(URI.create(url).toURL()));
            } catch (IllegalArgumentException | IOException invalid) {
                // Malformed legacy favorites do not have a corresponding cache key.
            }
        }
        return names;
    }

    private static List<Path> purgeableFiles(List<Path> directories, Set<String> protectedNames)
            throws IOException {
        List<Path> cached = new ArrayList<>();
        for (Path directory : directories) {
            if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid image cache directory: " + directory.getFileName());
            }
            try (var files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    if (!protectedNames.contains(file.getFileName().toString())
                            && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        cached.add(file);
                    }
                }
            }
        }
        return cached;
    }

    static long purgeImageCache(List<String> favorites) throws IOException {
        Set<String> protectedNames = favoriteCacheNames(favorites);
        List<Path> directories = cacheDirectories();
        synchronized (CACHE_LOCK) {
            cacheGeneration++;
            long removed = 0;
            try {
                for (Path file : purgeableFiles(directories, protectedNames)) {
                    long bytes = Files.size(file);
                    if (Files.deleteIfExists(file)) removed += bytes;
                }
                return removed;
            } finally {
                var entries = MEMORY_CACHE.entrySet().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    Path file = entry.getKey();
                    if (directories.contains(file.getParent())
                            && !protectedNames.contains(file.getFileName().toString())) {
                        memoryBytes -= entry.getValue().length;
                        entries.remove();
                    }
                }
            }
        }
    }

    /** Decodes the same img(s) transport convention used by Swing. */
    static String decodeChatImageUrl(String encodedUrl) {
        if (encodedUrl.regionMatches(true, 0, "imgs://", 0, 7)) {
            return "https://" + encodedUrl.substring(7);
        }
        if (encodedUrl.regionMatches(true, 0, "img://", 0, 6)) {
            String payload = encodedUrl.substring(6);
            if (payload.regionMatches(true, 0, "http://", 0, 7)
                    || payload.regionMatches(true, 0, "https://", 0, 8)) {
                throw new IllegalArgumentException(
                        "Malformed CoronaPoker chat image transport URL");
            }
            return "http://" + payload;
        }
        return encodedUrl;
    }

    private static byte[] receive(URL url) throws IOException {
        return receive(open(url));
    }

    private static byte[] receivePublic(URL url) throws IOException {
        for (int redirects = 0; redirects <= 5; redirects++) {
            requirePublicAddress(url);
            HttpURLConnection connection = (HttpURLConnection) open(url);
            connection.setInstanceFollowRedirects(false);
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303
                        || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing image redirect");
                    url = URI.create(url.toExternalForm()).resolve(location).toURL();
                } else {
                    if (status != 200) throw new IOException("Image request failed: " + status);
                    return receive(connection);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many image redirects");
    }

    private static void requirePublicAddress(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!("http".equals(protocol) || "https".equals(protocol))
                || url.getUserInfo() != null || url.getHost().isBlank()
                || (url.getPort() != -1 && url.getPort() != url.getDefaultPort())) {
            throw new IOException("Unsupported image address");
        }
        for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
            byte[] bytes = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc
                    || bytes.length == 4 && (bytes[0] & 0xff) == 100
                    && (bytes[1] & 0xc0) == 0x40) {
                throw new IOException("Image address must be public");
            }
        }
    }

    private static URLConnection open(URL url) throws IOException {
        URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setUseCaches(true);
            connection.setRequestProperty("User-Agent", "CoronaPoker-GDX/"
                    + ApplicationMetadata.VERSION);
        return connection;
    }

    private static byte[] receive(URLConnection connection) throws IOException {
            long declared = connection.getContentLengthLong();
            if (declared > MAXIMUM_BYTES) {
                throw new IOException("Chat image is too large");
            }
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream(
                            declared > 0 ? (int) declared : 64 * 1024)) {
                byte[] buffer = new byte[16 * 1024];
                int total = 0;
                for (int count; (count = input.read(buffer)) >= 0;) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new java.io.InterruptedIOException(
                                "Chat image load cancelled");
                    }
                    total += count;
                    if (total > MAXIMUM_BYTES) {
                        throw new IOException("Chat image is too large");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
    }

    private static byte[] readBounded(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAXIMUM_BYTES) {
            throw new IOException("Cached chat image is too large");
        }
        byte[] result = Files.readAllBytes(file);
        if (result.length > MAXIMUM_BYTES) {
            throw new IOException("Cached chat image is too large");
        }
        return result;
    }

    static String cacheFileName(URL url) {
        String port = url.getPort() >= 0 ? ":" + url.getPort() : "";
        String query = url.getQuery();
        String identity = url.getProtocol() + "://" + url.getHost() + port
                + url.getPath() + (query == null ? "" : "?" + query);
        String path = url.getPath();
        String extension = path.contains(".")
                ? path.substring(path.lastIndexOf('.')) : ".tmp";
        if (!extension.matches("\\.[a-zA-Z0-9]{1,12}")) extension = ".tmp";
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(32 + extension.length());
            for (byte value : hash) name.append(String.format("%02x", value));
            return name + extension;
        } catch (NoSuchAlgorithmException impossible) {
            return "img_" + Math.abs(identity.hashCode()) + extension;
        }
    }

    private static void memoryPut(Path key, byte[] data) {
        if (data.length > MAXIMUM_MEMORY_BYTES) return;
        byte[] previous = MEMORY_CACHE.put(key, data);
        if (previous != null) memoryBytes -= previous.length;
        memoryBytes += data.length;
        Iterator<Map.Entry<Path, byte[]>> iterator
                = MEMORY_CACHE.entrySet().iterator();
        while (memoryBytes > MAXIMUM_MEMORY_BYTES && iterator.hasNext()) {
            Map.Entry<Path, byte[]> eldest = iterator.next();
            memoryBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I'
                && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    static boolean isWebp(byte[] data) {
        return data.length >= 12 && data[0] == 'R' && data[1] == 'I'
                && data[2] == 'F' && data[3] == 'F' && data[8] == 'W'
                && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private GdxChatImageLoader() {
    }
}
