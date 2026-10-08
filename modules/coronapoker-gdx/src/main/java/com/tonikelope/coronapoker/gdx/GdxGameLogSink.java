/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameLogSink;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Thread-safe bridge between the dealer log and the native GDX overlay. */
final class GdxGameLogSink implements GameLogSink {

    private static final int MAX_LINES = 4_000;
    private final ArrayList<String> lines = new ArrayList<>();
    private List<ShowdownEntry> showdown = List.of();

    @Override
    public synchronized void print(String message) {
        String plain = GdxTableDialog.plainText(
                Objects.requireNonNull(message, "message"));
        for (String line : plain.split("\\R", -1)) {
            lines.add(line);
        }
        int overflow = lines.size() - MAX_LINES;
        if (overflow > 0) {
            lines.subList(0, overflow).clear();
        }
    }

    @Override
    public synchronized void updateShowdownCards(List<ShowdownEntry> entries) {
        showdown = List.copyOf(Objects.requireNonNull(entries, "entries"));
        for (int index = 0; index < lines.size(); index++) {
            lines.set(index, rewriteShowdownLine(lines.get(index), showdown));
        }
    }

    /** Mirrors Swing by replacing the placeholder in its original log row. */
    static String rewriteShowdownLine(String line,
            List<ShowdownEntry> entries) {
        String result = line == null ? "" : line;
        if (entries == null || entries.isEmpty()) return result;
        for (ShowdownEntry entry : entries) {
            String nickname = entry.nickname();
            int searchFrom = 0;
            while (searchFrom < result.length()) {
                int nicknameAt = result.indexOf(nickname, searchFrom);
                if (nicknameAt < 0) break;
                int nicknameEnd = nicknameAt + nickname.length();
                boolean leftBoundary = nicknameAt == 0
                        || Character.isWhitespace(result.charAt(nicknameAt - 1))
                        || result.charAt(nicknameAt - 1) == ')';
                int placeholderAt = nicknameEnd;
                while (placeholderAt < result.length()
                        && result.charAt(placeholderAt) == ' ') {
                    placeholderAt++;
                }
                if (!leftBoundary || placeholderAt == nicknameEnd
                        || !result.startsWith("(---)", placeholderAt)) {
                    searchFrom = nicknameEnd;
                    continue;
                }
                int afterPlaceholder = placeholderAt + 5;
                if (!entry.revealed()) {
                    result = result.substring(0, placeholderAt) + "(***)"
                            + result.substring(afterPlaceholder);
                    break;
                }
                int detailAt = afterPlaceholder;
                while (detailAt < result.length()
                        && result.charAt(detailAt) == ' ') detailAt++;
                String detail = result.substring(detailAt);
                if (detail.isBlank() || detail.indexOf(' ') <= 0) break;
                result = result.substring(0, placeholderAt)
                        + "(" + entry.holeCards() + ") " + detail
                        + " -> " + entry.hand();
                break;
            }
        }
        return result;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(lines), showdown);
    }

    synchronized void reset() {
        lines.clear();
        showdown = List.of();
    }

    @Override
    public synchronized void replaceHistory(List<String> messages) {
        reset();
        Objects.requireNonNull(messages, "messages").forEach(this::print);
    }

    synchronized void appendFinalSummary(TableSessionSummary summary,
            GdxGameText text) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(text, "text");
        if (!summary.hasBalances()
                || summary.reason()
                == TableSessionSummary.CloseReason.RECOVERABLE_STOP) {
            return;
        }
        print(text.translate("game.la_timba_ha_terminado_2") + " -> "
                + finalSummaryDate(summary.endedAtMillis()) + " ("
                + finalSummaryDuration(summary.durationSeconds()) + ")");
        print(finalResultTable(summary, text));
    }

    static String finalResultTable(TableSessionSummary summary,
            GdxGameText text) {
        String resultTitle = text.translate("ui.resultado");
        int nickWidth = "NICK".length();
        int resultWidth = resultTitle.length();
        ArrayList<String[]> rows = new ArrayList<>();
        for (TableSessionSummary.PlayerBalance balance : summary.balances()) {
            double result = balance.netResult();
            String resultText = result < 0d
                    ? text.translate("ui.pierde_2") + " "
                            + CoronaPokerGdxTable.formatAmount(-result)
                    : result > 0d
                            ? text.translate("ui.gana_4") + " "
                                    + CoronaPokerGdxTable.formatAmount(result)
                            : text.translate("ui.ni_gana_ni_pierde");
            nickWidth = Math.max(nickWidth, balance.nickname().length());
            resultWidth = Math.max(resultWidth, resultText.length());
            rows.add(new String[]{balance.nickname(), resultText});
        }
        int[] columns = {nickWidth, resultWidth};
        StringBuilder table = new StringBuilder("(##) ")
                .append(Crupier.gridBorderLine('\u250c', '\u252c', '\u2510', columns))
                .append("\n(##) ").append(Crupier.gridRowLine(
                        String.format("%-" + nickWidth + "s", "NICK"),
                        String.format("%-" + resultWidth + "s", resultTitle)))
                .append("\n(##) ")
                .append(Crupier.gridBorderLine('\u251c', '\u253c', '\u2524', columns));
        for (String[] row : rows) {
            table.append("\n(  ) ").append(Crupier.gridRowLine(
                    String.format("%-" + nickWidth + "s", row[0]),
                    String.format("%-" + resultWidth + "s", row[1])));
        }
        return table.append("\n(##) ")
                .append(Crupier.gridBorderLine('\u2514', '\u2534', '\u2518', columns))
                .toString();
    }

    private static String finalSummaryDate(long endedAtMillis) {
        return Instant.ofEpochMilli(endedAtMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    private static String finalSummaryDuration(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainder = seconds % 60L;
        return hours > 0L
                ? String.format("%d:%02d:%02d", hours, minutes, remainder)
                : String.format("%02d:%02d", minutes, remainder);
    }

    record Snapshot(List<String> lines, List<ShowdownEntry> showdown) {
        Snapshot {
            lines = List.copyOf(lines);
            showdown = List.copyOf(showdown);
        }
    }
}
