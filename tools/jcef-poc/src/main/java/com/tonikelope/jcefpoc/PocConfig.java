package com.tonikelope.jcefpoc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime configuration for the standalone experiment. */
public final class PocConfig {
    public static final String DEFAULT_VIDEO_ID = "M7lc1UVf-VE";
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final String videoId;
    private final String pageUrl;
    private final Path dataDirectory;
    private final int browserWidth;
    private final int browserHeight;

    private PocConfig(String videoId, String pageUrl, Path dataDirectory, int browserWidth,
            int browserHeight) {
        this.videoId = videoId;
        this.pageUrl = pageUrl;
        this.dataDirectory = dataDirectory;
        this.browserWidth = browserWidth;
        this.browserHeight = browserHeight;
    }

    public static PocConfig load(String[] args) throws IOException {
        Map<String, String> commandLine = parseArguments(args);
        Path propertiesPath = Paths.get(commandLine.getOrDefault("config", "jcef-poc.properties"))
                .toAbsolutePath().normalize();
        Properties properties = new Properties();
        if (Files.isRegularFile(propertiesPath)) {
            try (InputStream input = Files.newInputStream(propertiesPath)) {
                properties.load(input);
            }
        }

        String video = firstNonBlank(commandLine.get("video"), properties.getProperty("video"),
                DEFAULT_VIDEO_ID);
        String pageUrl = firstNonBlank(commandLine.get("page-url"),
                properties.getProperty("pageUrl"), "");
        String dataDir = firstNonBlank(commandLine.get("jcef-dir"),
                properties.getProperty("jcefDir"), null);
        int browserWidth = parseDimension(firstNonBlank(commandLine.get("browser-width"),
                properties.getProperty("browserWidth"), "640"), "browserWidth");
        int browserHeight = parseDimension(firstNonBlank(commandLine.get("browser-height"),
                properties.getProperty("browserHeight"), "360"), "browserHeight");

        String videoId = extractVideoId(video);
        if (videoId == null) {
            throw new IllegalArgumentException("No se reconoce el vídeo de YouTube: " + video);
        }
        if (!pageUrl.isBlank()) {
            URI hostedUri = URI.create(pageUrl);
            if (!"https".equalsIgnoreCase(hostedUri.getScheme())) {
                throw new IllegalArgumentException("pageUrl debe utilizar HTTPS");
            }
        }

        Path dataDirectory = dataDir == null
                ? defaultDataDirectory()
                : Paths.get(dataDir).toAbsolutePath().normalize();
        return new PocConfig(videoId, pageUrl, dataDirectory, browserWidth, browserHeight);
    }

    static Map<String, String> parseArguments(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                if (!values.containsKey("video")) {
                    values.put("video", argument);
                    continue;
                }
                throw new IllegalArgumentException("Argumento inesperado: " + argument);
            }
            String option = argument.substring(2);
            int equals = option.indexOf('=');
            if (equals >= 0) {
                values.put(option.substring(0, equals), option.substring(equals + 1));
            } else {
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Falta el valor de --" + option);
                }
                values.put(option, args[++index]);
            }
        }
        return values;
    }

    public static String extractVideoId(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (VIDEO_ID.matcher(trimmed).matches()) return trimmed;

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                return firstPathSegment(uri.getPath());
            }
            if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.equals("/watch")) return queryParameter(uri.getRawQuery(), "v");
                Matcher embedded = Pattern.compile("/(?:embed|shorts|live)/([A-Za-z0-9_-]{11})")
                        .matcher(path);
                if (embedded.find()) return embedded.group(1);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private static String firstPathSegment(String path) {
        if (path == null) return null;
        Matcher matcher = Pattern.compile("^/([A-Za-z0-9_-]{11})(?:/|$)").matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                String value = equals >= 0 ? pair.substring(equals + 1) : "";
                String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                return VIDEO_ID.matcher(decoded).matches() ? decoded : null;
            }
        }
        return null;
    }

    private static int parseDimension(String value, String label) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 200 || parsed > 4096) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " debe estar entre 200 y 4096");
        }
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private static Path defaultDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, "CoronaPoker", "jcef-poc");
            }
        }
        if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Caches", "CoronaPoker",
                    "jcef-poc");
        }
        String xdgCache = System.getenv("XDG_CACHE_HOME");
        if (xdgCache != null && !xdgCache.isBlank()) {
            return Paths.get(xdgCache, "coronapoker", "jcef-poc");
        }
        return Paths.get(System.getProperty("user.home"), ".cache", "coronapoker", "jcef-poc");
    }

    public String videoId() {
        return videoId;
    }

    public String pageUrl() {
        return pageUrl;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public int browserWidth() {
        return browserWidth;
    }

    public int browserHeight() {
        return browserHeight;
    }
}
