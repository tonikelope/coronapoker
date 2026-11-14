/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Glyph repertoire for the active catalog, shared across the existing atlases. */
final class GdxLocaleFonts {

    private static final String INTER = "fonts/Inter-Medium.ttf";
    private final String fallbackPath;
    private final String catalogCharacters;
    private final boolean tamil;

    GdxLocaleFonts(GdxGameText text) {
        tamil = "ta-IN".equals(text.language());
        fallbackPath = switch (text.language()) {
            case "es", "en" -> null;
            case "ja-JP" -> "fonts/CoronaPokerSans-JP.otf";
            case "zh-CN" -> "fonts/CoronaPokerSans-SC.otf";
            case "ta-IN" -> "fonts/CoronaPokerSans-Tamil.ttf";
            default -> INTER;
        };
        catalogCharacters = fallbackPath == null ? "" : text.catalogCharacters();
    }

    FileHandle fallback() {
        return fallbackPath == null ? null : Gdx.files.internal(fallbackPath);
    }

    String characters(String base) {
        if (!tamil) return charactersFor(base, catalogCharacters);
        var extra = new StringBuilder(catalogCharacters);
        TamilClusters.REPLACEMENTS.values().forEach(extra::append);
        return charactersFor(base, extra.toString());
    }

    String charactersFor(String base, String text) {
        if (fallbackPath == null) return base;
        var characters = new LinkedHashSet<Integer>();
        base.chars().forEach(characters::add);
        String visible = tamil ? shapeTamil(text) : text;
        visible.chars().filter(c -> !Character.isISOControl(c)
                && !Character.isSurrogate((char) c)
                && Character.getType(c) != Character.FORMAT
                && c != 0xFE0F).forEach(characters::add);
        var result = new StringBuilder(characters.size());
        characters.forEach(result::appendCodePoint);
        return result.toString();
    }

    FreeTypeBitmapFontData fontData() {
        return tamil ? new TamilFontData() : new FreeTypeBitmapFontData();
    }

    private static final class TamilFontData extends FreeTypeBitmapFontData {

        @Override
        public void getGlyphs(GlyphRun run, CharSequence text, int start, int end,
                Glyph lastGlyph) {
            String shaped = TamilClusters.shape(text, start, end);
            if (shaped == null) {
                super.getGlyphs(run, text, start, end, lastGlyph);
            } else {
                super.getGlyphs(run, shaped, 0, shaped.length(), lastGlyph);
            }
        }
    }

    static String shapeTamil(String text) {
        String shaped = TamilClusters.shape(text, 0, text.length());
        return shaped == null ? text : shaped;
    }

    private static final class TamilClusters {
        private static final Map<String, Character> REPLACEMENTS = load();
        private static final int MAX_LENGTH = REPLACEMENTS.keySet().stream()
                .mapToInt(String::length).max().orElseThrow();

        private static Map<String, Character> load() {
            Map<String, Character> replacements = new LinkedHashMap<>();
            try (var input = GdxLocaleFonts.class.getResourceAsStream("/fonts/tamil-clusters.tsv");
                    var reader = new BufferedReader(new InputStreamReader(
                            input, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    int separator = line.indexOf('\t');
                    replacements.put(line.substring(0, separator), line.charAt(separator + 1));
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read Tamil font clusters", failure);
            }
            return Map.copyOf(replacements);
        }

        private static String shape(CharSequence text, int start, int end) {
            StringBuilder shaped = null;
            for (int index = start; index < end; index++) {
                char current = text.charAt(index);
                if (current >= 0x0B80 && current <= 0x0BFF) {
                    int length = Math.min(MAX_LENGTH, end - index);
                    for (; length > 0; length--) {
                        Character glyph = REPLACEMENTS.get(
                                text.subSequence(index, index + length).toString());
                        if (glyph != null) {
                            if (shaped == null) {
                                shaped = new StringBuilder(end - start);
                                shaped.append(text, start, index);
                            }
                            current = glyph;
                            index += length - 1;
                            break;
                        }
                    }
                }
                if (shaped != null) shaped.append(current);
            }
            return shaped == null ? null : shaped.toString();
        }
    }
}
