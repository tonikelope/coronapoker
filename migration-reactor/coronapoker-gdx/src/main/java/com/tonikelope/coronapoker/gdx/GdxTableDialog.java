/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One renderer-owned modal request; poker state never lives here. */
final class GdxTableDialog {

    enum Kind {
        ERROR,
        INFO,
        CONFIRM,
        TIMED_WARNING
    }

    private final Kind kind;
    private final String message;
    private final GameDialogSink.Icon icon;
    private final int preferredWidth;
    private final int seconds;
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();
    private float openedAt = Float.NaN;

    GdxTableDialog(Kind kind, String message, GameDialogSink.Icon icon,
            int preferredWidth, int seconds) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = plainText(Objects.requireNonNull(message, "message"));
        this.icon = Objects.requireNonNull(icon, "icon");
        this.preferredWidth = Math.max(0, preferredWidth);
        this.seconds = Math.max(0, seconds);
    }

    Kind kind() { return kind; }
    String message() { return message; }
    GameDialogSink.Icon icon() { return icon; }
    int preferredWidth() { return preferredWidth; }
    int seconds() { return seconds; }
    CompletionStage<Boolean> result() { return result; }

    void opened(float now) {
        if (Float.isNaN(openedAt)) openedAt = now;
    }

    boolean expired(float now) {
        return kind == Kind.TIMED_WARNING && !Float.isNaN(openedAt)
                && now - openedAt >= seconds;
    }

    void accept() { result.complete(true); }
    void dismiss() { result.complete(false); }
    boolean complete() { return result.isDone(); }

    static String plainText(String value) {
        return value.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .strip();
    }
}
