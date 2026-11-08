/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** The table's conversation and its short-lived on-screen projection. */
final class GdxTableChatFeed {

    static final int VISIBLE_LIMIT = 6;
    static final float NOTICE_SECONDS = 9f;
    static final float FADE_SECONDS = 0.8f;

    enum Kind {
        TEXT("f5f7fa"), MEDIA("d9b8ff"), JOIN("a8eb84"), LEAVE("ffad93"),
        INFO("a9dfff"), SUCCESS("a8eb84"), WARNING("ffad93");

        final String color;

        Kind(String color) {
            this.color = color;
        }
    }

    record Line(Kind kind, String sender, String text, boolean local,
            long voiceSequence, CompletionStage<GdxVoiceNoteLibrary.Entry> voiceFile) {

        static Line notice(Kind kind, String sender, String text) {
            return new Line(kind, sender, text, false, -1, null);
        }

        boolean isVoice() {
            return voiceSequence >= 0;
        }

        String markup(String body) {
            String color = "[#" + kind.color + "]";
            if (sender.isEmpty()) return color + escape(body);
            String senderColor = switch (kind) {
                case TEXT, MEDIA -> local ? "[#ffdc83]" : "[#8bd7ff]";
                default -> color;
            };
            String separator = kind == Kind.TEXT || kind == Kind.MEDIA
                    ? ": " : " ";
            return senderColor + escape(sender) + separator + color + escape(body);
        }

        private static String escape(String value) {
            return value.replace("[", "[[").replace('\r', ' ')
                    .replace('\n', ' ').replace('\t', ' ');
        }
    }

    record Visible(Line line, float expiresAt) {
        float alpha(float now) {
            return Math.min(1f, Math.max(0f, (expiresAt - now) / FADE_SECONDS));
        }
    }

    private final ArrayList<Line> history = new ArrayList<>();
    private final List<Line> historyView = Collections.unmodifiableList(history);
    private final ArrayDeque<Visible> visible = new ArrayDeque<>(VISIBLE_LIMIT);

    List<Line> history() {
        return historyView;
    }

    void add(Line line, float now, boolean showOnTable) {
        history.add(line);
        if (!showOnTable) return;
        if (visible.size() == VISIBLE_LIMIT) visible.removeFirst();
        visible.addLast(new Visible(line, now + NOTICE_SECONDS));
    }

    List<Visible> visible(float now) {
        while (!visible.isEmpty() && visible.getFirst().expiresAt() <= now) {
            visible.removeFirst();
        }
        return List.copyOf(visible);
    }

    void clear() {
        history.clear();
        visible.clear();
    }
}
