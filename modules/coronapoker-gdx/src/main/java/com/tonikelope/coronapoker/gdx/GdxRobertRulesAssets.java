/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Resources served only to the in-game PDF viewer. */
final class GdxRobertRulesAssets {

    static final String ORIGIN = "https://rules.coronapoker.invalid";
    static final String VIEWER = "/web/viewer.html";
    static final String ORIGINAL = "/robert_rules.pdf";
    static final String SPANISH = "/robert_rules_es.pdf";
    private static final String RESOURCES = "/robert-rules/";
    private static Map<String, byte[]> packaged;

    private GdxRobertRulesAssets() {}

    static String document(String language) {
        return "es".equalsIgnoreCase(language) ? SPANISH : ORIGINAL;
    }

    static String initialUrl(String language) {
        String locale = SPANISH.equals(document(language)) ? "es" : "en-US";
        return ORIGIN + VIEWER + "?file=" + document(language) + "&gameLocale=" + locale + "#zoom=page-width";
    }

    static String localPath(String url) {
        if (url == null || !url.startsWith(ORIGIN + "/")) return "";
        int end = url.length();
        int query = url.indexOf('?', ORIGIN.length());
        int fragment = url.indexOf('#', ORIGIN.length());
        if (query >= 0) end = query;
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(ORIGIN.length(), end);
    }

    static synchronized Map<String, byte[]> load() throws IOException {
        if (packaged != null) return packaged;
        Map<String, byte[]> files = new HashMap<>();
        try (var archive = new ZipInputStream(open(RESOURCES + "pdfjs-6.4.299-dist.zip"))) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                if (!entry.isDirectory() && !entry.getName().endsWith(".map")) {
                    files.put("/" + entry.getName(), archive.readAllBytes());
                }
            }
        }
        byte[] html = files.get(VIEWER);
        if (html == null) throw new IOException("Missing PDF.js viewer");
        String themed = new String(html, StandardCharsets.UTF_8)
                .replace("connect-src * blob: data:", "connect-src 'self' blob: data:")
                .replace("</head>", """
                      <link rel="stylesheet" href="/game-viewer.css">
                      <script src="/game-viewer.js"></script>
                    </head>""");
        files.put(VIEWER, themed.getBytes(StandardCharsets.UTF_8));
        files.put("/game-viewer.css", read(RESOURCES + "viewer.css"));
        files.put("/game-viewer.js", read(RESOURCES + "viewer.js"));
        files.put(ORIGINAL, read(ORIGINAL));
        files.put(SPANISH, read(SPANISH));
        packaged = Map.copyOf(files);
        return packaged;
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream source = open(path)) {
            return source.readAllBytes();
        }
    }

    private static InputStream open(String path) throws IOException {
        InputStream source = GdxRobertRulesAssets.class.getResourceAsStream(path);
        if (source == null) throw new IOException("Missing " + path);
        return source;
    }

    static String mimeType(String path) {
        int dot = path.lastIndexOf('.');
        return switch (dot < 0 ? "" : path.substring(dot + 1)) {
            case "html" -> "text/html";
            case "mjs", "js" -> "text/javascript";
            case "css" -> "text/css";
            case "json" -> "application/json";
            case "ftl" -> "text/plain";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "wasm" -> "application/wasm";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
