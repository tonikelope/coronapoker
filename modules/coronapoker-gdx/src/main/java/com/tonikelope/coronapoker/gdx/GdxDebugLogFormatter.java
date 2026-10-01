/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mirrors the syntax hierarchy of Swing's {@code DebugSettingsPanel}. */
final class GdxDebugLogFormatter {

    private static final Color TS = color(0x6a6a6a);
    private static final Color CLASS = color(0x78e1eb);
    private static final Color METHOD = color(0xb9b9b9);
    private static final Color MSG = color(0xe6e6e6);
    private static final Color INFO = color(0x79e0a0);
    private static final Color WARN = color(0xffc85a);
    private static final Color WARN_MSG = color(0xf4e2bd);
    private static final Color SEV_MSG = color(0xff6a6a);
    private static final Color CONFIG = color(0x78e1eb);
    private static final Color FINE = color(0x8f8f8f);
    private static final Color SEP = color(0x5aa9d6);
    private static final Color NUMBER = color(0xffcf8a);
    private static final Color STACK = color(0x7a7a7a);
    private static final Color SEV_BG = color(0xaa1414);
    private static final int TS_WIDTH = 19;
    private static final Pattern HEADER = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .+");
    private static final Pattern LEVEL = Pattern.compile(
            "^(SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST): ?");
    private static final Pattern STACK_AT = Pattern.compile("^\\s+(at |\\.\\.\\. )");
    private static final Pattern EXCEPTION = Pattern.compile(
            "^(Caused by: |Suppressed: )?[\\w.$]+(Exception|Error|Throwable)([:\\s].*)?$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "(?<![\\w$/\\\\-])\\d+(?:[.,]\\d+)*(?![\\w$/\\\\-])");
    private static final Pattern WRAP_TOKEN = Pattern.compile("\\s+|\\S+");

    private GdxDebugLogFormatter() {
    }

    static List<Line> format(List<String> source) {
        ArrayList<Line> result = new ArrayList<>(source.size());
        Style continuation = Style.MESSAGE;
        for (String raw : source) {
            String line = raw == null ? "" : raw.replace('\t', ' ');
            if (line.stripLeading().startsWith("===")) {
                result.add(line(new Run(line, SEP, null, true)));
            } else if (HEADER.matcher(line).matches()) {
                result.add(header(line));
                continuation = Style.MESSAGE;
            } else {
                Matcher level = LEVEL.matcher(line);
                if (level.find()) {
                    LevelResult formatted = level(line, level.group(1));
                    result.add(formatted.line());
                    continuation = formatted.continuation();
                } else if (STACK_AT.matcher(line).find()) {
                    result.add(line(new Run(line, STACK, null, false)));
                } else if (EXCEPTION.matcher(line).matches()) {
                    result.add(line(new Run(line, SEV_MSG, null, true)));
                } else {
                    result.add(message(line, continuation));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Converts logical log entries into visual rows without discarding their
     * syntax colours. Words move together and exceptionally long hashes or
     * identifiers are split by code point, so no run can escape the console.
     */
    static List<Line> wrap(List<Line> source, float maximumWidth,
            ToDoubleFunction<Run> width) {
        float available = Math.max(1f, maximumWidth);
        ArrayList<Line> result = new ArrayList<>();
        for (Line logical : source) {
            ArrayList<Run> row = new ArrayList<>();
            float rowWidth = 0f;
            for (Run styled : logical.runs()) {
                Matcher tokens = WRAP_TOKEN.matcher(styled.text());
                while (tokens.find()) {
                    String pending = tokens.group();
                    boolean whitespace = pending.isBlank();
                    while (!pending.isEmpty()) {
                        if (whitespace && row.isEmpty()) break;
                        Run piece = styled.withText(pending);
                        float pieceWidth = (float) width.applyAsDouble(piece);
                        if (rowWidth + pieceWidth <= available) {
                            row.add(piece);
                            rowWidth += pieceWidth;
                            break;
                        }
                        if (!row.isEmpty()) {
                            result.add(new Line(List.copyOf(row)));
                            row.clear();
                            rowWidth = 0f;
                            if (whitespace) break;
                            continue;
                        }
                        int fit = fittingPrefix(styled, pending, available,
                                width);
                        String prefix = pending.substring(0, fit);
                        row.add(styled.withText(prefix));
                        rowWidth = (float) width.applyAsDouble(
                                styled.withText(prefix));
                        pending = pending.substring(fit);
                        if (!pending.isEmpty()) {
                            result.add(new Line(List.copyOf(row)));
                            row.clear();
                            rowWidth = 0f;
                        }
                    }
                }
            }
            if (!row.isEmpty() || logical.runs().isEmpty()) {
                result.add(new Line(List.copyOf(row)));
            }
        }
        return List.copyOf(result);
    }

    static String clipboardText(List<String> source) {
        return String.join(System.lineSeparator(), source);
    }

    private static int fittingPrefix(Run style, String value,
            float maximumWidth, ToDoubleFunction<Run> width) {
        int cursor = 0;
        int fitting = 0;
        while (cursor < value.length()) {
            int next = value.offsetByCodePoints(cursor, 1);
            if (width.applyAsDouble(style.withText(value.substring(0, next)))
                    > maximumWidth) break;
            fitting = next;
            cursor = next;
        }
        return fitting > 0 ? fitting : value.offsetByCodePoints(0, 1);
    }

    private static Line header(String value) {
        ArrayList<Run> runs = new ArrayList<>();
        String timestamp = value.substring(0, TS_WIDTH);
        String rest = value.substring(Math.min(value.length(), TS_WIDTH + 1));
        int lastSpace = rest.lastIndexOf(' ');
        String className = lastSpace >= 0 ? rest.substring(0, lastSpace) : rest;
        String method = lastSpace >= 0 ? rest.substring(lastSpace + 1) : "";
        int lastDot = className.lastIndexOf('.');
        String packageName = lastDot >= 0
                ? className.substring(0, lastDot + 1) : "";
        String simpleName = lastDot >= 0
                ? className.substring(lastDot + 1) : className;
        runs.add(new Run(timestamp + " " + packageName, TS, null, false));
        runs.add(new Run(simpleName, CLASS, null, true));
        if (!method.isEmpty()) {
            runs.add(new Run(" " + method, METHOD, null, false));
        }
        return new Line(List.copyOf(runs));
    }

    private static LevelResult level(String value, String level) {
        int tagEnd = level.length() + 1;
        Style messageStyle;
        Run tag;
        switch (level) {
            case "SEVERE" -> {
                tag = new Run(value.substring(0, tagEnd), Color.WHITE,
                        SEV_BG, true);
                messageStyle = Style.SEVERE;
            }
            case "WARNING" -> {
                tag = new Run(value.substring(0, tagEnd), WARN, null, true);
                messageStyle = Style.WARNING;
            }
            case "CONFIG" -> {
                tag = new Run(value.substring(0, tagEnd), CONFIG, null, true);
                messageStyle = Style.MESSAGE;
            }
            case "FINE", "FINER", "FINEST" -> {
                tag = new Run(value.substring(0, tagEnd), FINE, null, true);
                messageStyle = Style.FINE;
            }
            default -> {
                tag = new Run(value.substring(0, tagEnd), INFO, null, true);
                messageStyle = Style.MESSAGE;
            }
        }
        ArrayList<Run> runs = new ArrayList<>();
        runs.add(tag);
        runs.addAll(messageRuns(value.substring(tagEnd), messageStyle));
        return new LevelResult(new Line(List.copyOf(runs)), messageStyle);
    }

    private static Line message(String value, Style style) {
        return new Line(List.copyOf(messageRuns(value, style)));
    }

    private static List<Run> messageRuns(String value, Style style) {
        Color base = switch (style) {
            case WARNING -> WARN_MSG;
            case SEVERE -> SEV_MSG;
            case FINE -> FINE;
            default -> MSG;
        };
        boolean bold = style == Style.SEVERE;
        if (style == Style.SEVERE || style == Style.FINE || value.isEmpty()) {
            return List.of(new Run(value, base, null, bold));
        }
        Color[] colors = new Color[value.length()];
        Arrays.fill(colors, base);
        Matcher numbers = NUMBER_PATTERN.matcher(value);
        while (numbers.find()) {
            if (adjacentHexToken(value, numbers.start(), numbers.end())) continue;
            Arrays.fill(colors, numbers.start(), numbers.end(), NUMBER);
        }
        ArrayList<Run> runs = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = start + 1;
            while (end < value.length() && colors[end] == colors[start]) end++;
            runs.add(new Run(value.substring(start, end), colors[start],
                    null, false));
            start = end;
        }
        return runs;
    }

    private static boolean adjacentHexToken(String text, int start, int end) {
        int i = start - 1;
        while (i >= 0 && text.charAt(i) == ' ') i--;
        int previousEnd = i + 1;
        while (i >= 0 && text.charAt(i) != ' ') i--;
        if (previousEnd > i + 1
                && isHexToken(text.substring(i + 1, previousEnd))) return true;
        int j = end;
        while (j < text.length() && text.charAt(j) == ' ') j++;
        int nextStart = j;
        while (j < text.length() && text.charAt(j) != ' ') j++;
        return j > nextStart && isHexToken(text.substring(nextStart, j));
    }

    private static boolean isHexToken(String token) {
        if (token.length() < 4) return false;
        boolean letter = false;
        for (int i = 0; i < token.length(); i++) {
            char character = Character.toLowerCase(token.charAt(i));
            if (character >= 'a' && character <= 'f') letter = true;
            else if (!Character.isDigit(character)) return false;
        }
        return letter;
    }

    private static Line line(Run run) {
        return new Line(List.of(run));
    }

    private static Color color(int rgb) {
        return new Color((rgb << 8) | 0xff);
    }

    record Run(String text, Color foreground, Color background,
            boolean bold) {

        Run withText(String value) {
            return new Run(value, foreground, background, bold);
        }
    }

    record Line(List<Run> runs) {
    }

    private record LevelResult(Line line, Style continuation) {
    }

    private enum Style { MESSAGE, WARNING, SEVERE, FINE }
}
