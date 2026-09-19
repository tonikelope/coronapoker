/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Shared bounded downloader for lobby and in-table chat images/GIFs.
 *
 * <p>The byte cache deliberately mirrors Swing's {@code ImageCacheManager}:
 * the URL fragment is ignored, files survive between sessions in the same
 * {@code ChatImagesCache} directory and every caller still builds its own GDX
 * texture/GIF instance from the cached bytes.</p>
 */
final class GdxChatImageLoader {

    private static final int MAXIMUM_BYTES = 16 * 1024 * 1024;
    private static final long MAXIMUM_MEMORY_BYTES = 32L * 1024 * 1024;
    private static final Object MEMORY_LOCK = new Object();
    private static final LinkedHashMap<String, byte[]> MEMORY_CACHE
            = new LinkedHashMap<>(64, 0.75f, true);
    private static long memoryBytes;

    static byte[] download(String encodedUrl) {
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
            byte[] cached = memoryGet(cacheKey);
            if (cached != null) return cached;

            Path cacheDirectory = Path.of(System.getProperty("user.home"),
                    ".coronapoker", "ChatImagesCache");
            Path cacheFile = cacheDirectory.resolve(cacheKey);
            if (Files.isRegularFile(cacheFile)) {
                byte[] disk = readBounded(cacheFile);
                memoryPut(cacheKey, disk);
                return disk;
            }

            Files.createDirectories(cacheDirectory);
            byte[] received = receive(url);
            Path temporary = Files.createTempFile(cacheDirectory,
                    cacheKey + ".", ".part");
            try {
                Files.write(temporary, received);
                try {
                    Files.move(temporary, cacheFile,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, cacheFile,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            memoryPut(cacheKey, received);
            return received;
        } catch (IOException | IllegalArgumentException failure) {
            throw new CompletionException(failure);
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
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setUseCaches(true);
            connection.setRequestProperty("User-Agent", "CoronaPoker-GDX/24.11");
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

    private static byte[] memoryGet(String key) {
        synchronized (MEMORY_LOCK) {
            return MEMORY_CACHE.get(key);
        }
    }

    private static void memoryPut(String key, byte[] data) {
        if (data.length > MAXIMUM_MEMORY_BYTES) return;
        synchronized (MEMORY_LOCK) {
            byte[] previous = MEMORY_CACHE.put(key, data);
            if (previous != null) memoryBytes -= previous.length;
            memoryBytes += data.length;
            Iterator<Map.Entry<String, byte[]>> iterator
                    = MEMORY_CACHE.entrySet().iterator();
            while (memoryBytes > MAXIMUM_MEMORY_BYTES && iterator.hasNext()) {
                Map.Entry<String, byte[]> eldest = iterator.next();
                memoryBytes -= eldest.getValue().length;
                iterator.remove();
            }
        }
    }

    static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I'
                && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    private GdxChatImageLoader() {
    }
}
