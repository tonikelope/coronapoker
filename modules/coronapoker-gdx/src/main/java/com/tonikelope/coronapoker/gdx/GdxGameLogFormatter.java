/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure presentation formatting for the renderer-native game log.
 *
 * <p>The dealer remains the sole source of log content.  This class only
 * interprets the role markers and colour hierarchy already emitted for the
 * classic log, keeping parsing rules out of the table renderer.</p>
 */
final class GdxGameLogFormatter {

    private static final Color DEFAULT = new Color(0xffffffff);
    private static final Color HEADER = new Color(0x78e1ebff);
    private static final Color BOARD = new Color(0x96c8ffff);
    private static final Color AMOUNT = new Color(0xffc85aff);
    private static final Color DIM = new Color(0xaaaaaaff);
    private static final Color RANK = new Color(0xcdcdcdff);
    private static final Color WIN = new Color(0x78e678ff);
    private static final Color LOSS = new Color(0xeb7878ff);
    private static final Color CARD_RED = new Color(0xff6767ff);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "\\(\\s*[+-]?\\d[\\d.,\\s]*[KkMm]?\\)");
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\[[^\\[\\]]*[♠♥♦♣]\\]");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\((?:---|\\*\\*\\*)\\)");
    private static final Pattern GRID_PATTERN = Pattern.compile(
            "[\\u2500-\\u257f]+");

    private GdxGameLogFormatter() {
    }

    static void wrapLine(List<String> target, String line,
            int maximumCharacters) {
        if (line.length() <= maximumCharacters) {
            target.add(line);
            return;
        }
        String remaining = line;
        boolean continuation = false;
        while (remaining.length() > maximumCharacters) {
            int split = remaining.lastIndexOf(' ', maximumCharacters);
            if (split < maximumCharacters / 2) split = maximumCharacters;
            target.add((continuation ? "  " : "")
                    + remaining.substring(0, split).stripTrailing());
            remaining = remaining.substring(split).stripLeading();
            continuation = true;
        }
        target.add((continuation ? "  " : "") + remaining);
    }

    static List<Run> runs(String value) {
        String visible = visibleText(value);
        if (visible.isEmpty()) return List.of();
        Color base = lineColor(value);
        Color[] colors = new Color[visible.length()];
        Arrays.fill(colors, base);
        if (marker(value) != Marker.NONE) {
            overlay(colors, visible, GRID_PATTERN, DIM);
        }
        if (base != HEADER && base != BOARD && base != LOSS) {
            int arrow = visible.indexOf(" -> ");
            if (arrow >= 0) {
                Arrays.fill(colors, arrow, colors.length, RANK);
            }
        }
        overlay(colors, visible, PLACEHOLDER_PATTERN, DIM);
        overlay(colors, visible, AMOUNT_PATTERN, AMOUNT);
        Matcher cards = CARD_PATTERN.matcher(visible);
        while (cards.find()) {
            String token = cards.group();
            Color card = token.indexOf('♥') >= 0 || token.indexOf('♦') >= 0
                    ? CARD_RED : Color.WHITE;
            Arrays.fill(colors, cards.start(), cards.end(), card);
        }
        ArrayList<Run> runs = new ArrayList<>();
        int start = 0;
        while (start < visible.length()) {
            int end = start + 1;
            while (end < visible.length() && colors[end] == colors[start]) end++;
            runs.add(new Run(visible.substring(start, end), colors[start]));
            start = end;
        }
        return List.copyOf(runs);
    }

    static Marker marker(String value) {
        if (value == null || value.length() < 4) return Marker.NONE;
        return switch (value.substring(0, 4)) {
            case "(D )" -> Marker.DEALER;
            case "(SB)" -> Marker.SMALL_BLIND;
            case "(BB)" -> Marker.BIG_BLIND;
            case "(  )" -> Marker.BLANK;
            case "(##)" -> Marker.GRID;
            case "($$)", "(A )" -> Marker.MONEY;
            case "(ST)" -> Marker.STRADDLE;
            case "(DS)" -> Marker.DEALER_STRADDLE;
            case "(MV)" -> Marker.MULTIVERSE;
            default -> Marker.NONE;
        };
    }

    static String visibleText(String value) {
        if (value == null) return "";
        return marker(value) == Marker.NONE ? value : value.substring(4);
    }

    private static Color lineColor(String value) {
        Marker marker = marker(value);
        String visible = visibleText(value);
        if (marker == Marker.GRID || marker == Marker.MULTIVERSE) return DIM;
        Color balanceResult = balanceResultColor(marker, visible);
        if (balanceResult != null) return balanceResult;
        String upper = visible.toUpperCase(Locale.ROOT);
        String stripped = upper.stripLeading();
        if (upper.contains("NI GANA NI PIERDE")
                || upper.contains("BREAK EVEN")) return DEFAULT;
        if (upper.contains("ERROR") || upper.contains("PIERDE")
                || upper.contains("LOSES POT") || upper.contains("FALLO")) {
            return LOSS;
        }
        if (upper.contains("GANA") || upper.contains("GANADOR")
                || upper.contains("WINS POT")) return WIN;
        if (visible.contains("***************")
                || stripped.startsWith("┌") || stripped.startsWith("│")
                || stripped.startsWith("└")) return HEADER;
        if (stripped.startsWith("FLOP -> ")
                || stripped.startsWith("TURN -> ")
                || stripped.startsWith("RIVER -> ")) return BOARD;
        if (stripped.startsWith("PAUSE (") || upper.contains("LOKI:")) {
            return DIM;
        }
        return DEFAULT;
    }

    /** Mirrors Swing's final-result table without matching words in nicknames. */
    private static Color balanceResultColor(Marker marker, String visible) {
        if (marker != Marker.BLANK) return null;
        int last = visible.lastIndexOf('│');
        if (last <= 0) return null;
        int previous = visible.lastIndexOf('│', last - 1);
        if (previous < 0) return null;
        String result = visible.substring(previous + 1, last)
                .strip().toUpperCase(Locale.ROOT);
        if (result.equals("NI GANA NI PIERDE")
                || result.equals("BREAK EVEN")) return DEFAULT;
        if (result.equals("GANA") || result.startsWith("GANA ")
                || result.equals("WINS") || result.startsWith("WINS ")) {
            return WIN;
        }
        if (result.equals("PIERDE") || result.startsWith("PIERDE ")
                || result.equals("LOSES") || result.startsWith("LOSES ")) {
            return LOSS;
        }
        return null;
    }

    private static void overlay(Color[] colors, String value,
            Pattern pattern, Color color) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            Arrays.fill(colors, matcher.start(), matcher.end(), color);
        }
    }

    record Run(String text, Color color) {
    }

    enum Marker {
        NONE, DEALER, SMALL_BLIND, BIG_BLIND, BLANK, GRID, MONEY,
        STRADDLE, DEALER_STRADDLE, MULTIVERSE
    }
}
