/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameLogSink;
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
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(lines), showdown);
    }

    synchronized void reset() {
        lines.clear();
        showdown = List.of();
    }

    record Snapshot(List<String> lines, List<ShowdownEntry> showdown) {
        Snapshot {
            lines = List.copyOf(lines);
            showdown = List.copyOf(showdown);
        }
    }
}
