/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Exposes parts of the game catalog to the embedded Chromium pages. */
final class GdxWebLocalization {
    static final String COPY_PATH = "/game-copy.js";
    static final String FONT_CSS_PATH = "/game-fonts.css";
    private static final String INTER_PATH = "/fonts/Inter-Medium.ttf";
    private static final Pattern WORD_BOUNDARY = Pattern.compile("_([a-z])");

    private GdxWebLocalization() { }

    static String json(String language, String prefix) {
        GdxGameText text = new GdxGameText(language);
        StringWriter output = new StringWriter();
        try (JsonWriter writer = new JsonWriter(output)) {
            writer.setOutputType(JsonWriter.OutputType.json);
            writer.object().set("locale", text.language()).object("labels");
            for (var label : text.messagesWithPrefix(prefix).entrySet()) {
                String key = WORD_BOUNDARY.matcher(label.getKey()).replaceAll(match ->
                        match.group(1).toUpperCase(Locale.ROOT));
                writer.set(key, label.getValue());
            }
            writer.pop().pop();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        return output.toString().replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    static byte[] script(String language, String prefix) {
        return utf8("window.coronaCopy = " + json(language, prefix) + ";");
    }

    static Map<String, byte[]> fonts(String language) throws IOException {
        Map<String, byte[]> assets = new HashMap<>();
        String css = fontFace("Corona Inter", INTER_PATH);
        assets.put(INTER_PATH, resource(INTER_PATH));
        String fallback = switch (language) {
            case "ja-JP" -> "/fonts/CoronaPokerSans-JP.otf";
            case "zh-CN" -> "/fonts/CoronaPokerSans-SC.otf";
            case "ta-IN" -> "/fonts/CoronaPokerSans-Tamil.ttf";
            default -> null;
        };
        if (fallback != null) {
            css += fontFace("Corona Locale", fallback);
            assets.put(fallback, resource(fallback));
        }
        assets.put(FONT_CSS_PATH, utf8(css));
        return assets;
    }

    static String fontMimeType(String path) {
        if (path.endsWith(".otf")) return "font/otf";
        if (path.endsWith(".ttf")) return "font/ttf";
        return "text/css";
    }

    private static String fontFace(String name, String path) {
        return "@font-face{font-family:'" + name + "';src:url('" + path + "');font-display:swap}\n";
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = GdxWebLocalization.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing " + path);
            return input.readAllBytes();
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
