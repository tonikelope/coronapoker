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
    private final String title;
    private volatile String message;
    private final GameDialogSink.Icon icon;
    private final int preferredWidth;
    private final int seconds;
    private final boolean timeoutAccepts;
    private final String negativeLabel;
    private final String positiveLabel;
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();
    private float openedAt = Float.NaN;

    GdxTableDialog(Kind kind, String message, GameDialogSink.Icon icon,
            int preferredWidth, int seconds) {
        this(kind, defaultTitle(kind), message, icon, preferredWidth, seconds,
                kind == Kind.TIMED_WARNING, "CANCELAR",
                kind == Kind.CONFIRM ? "ACEPTAR" : "CERRAR");
    }

    GdxTableDialog(Kind kind, String title, String message,
            GameDialogSink.Icon icon, int preferredWidth, int seconds,
            boolean timeoutAccepts, String negativeLabel,
            String positiveLabel) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = Objects.requireNonNull(title, "title");
        this.message = plainText(Objects.requireNonNull(message, "message"));
        this.icon = Objects.requireNonNull(icon, "icon");
        this.preferredWidth = Math.max(0, preferredWidth);
        this.seconds = Math.max(0, seconds);
        this.timeoutAccepts = timeoutAccepts;
        this.negativeLabel = Objects.requireNonNull(negativeLabel,
                "negativeLabel");
        this.positiveLabel = Objects.requireNonNull(positiveLabel,
                "positiveLabel");
    }

    Kind kind() { return kind; }
    String title() { return title; }
    String message() { return message; }
    GameDialogSink.Icon icon() { return icon; }
    int preferredWidth() { return preferredWidth; }
    int seconds() { return seconds; }
    String negativeLabel() { return negativeLabel; }
    String positiveLabel() { return positiveLabel; }
    CompletionStage<Boolean> result() { return result; }

    void message(String value) {
        message = plainText(Objects.requireNonNull(value, "value"));
    }

    void opened(float now) {
        if (Float.isNaN(openedAt)) openedAt = now;
    }

    boolean expired(float now) {
        return seconds > 0 && !Float.isNaN(openedAt)
                && now - openedAt >= seconds;
    }

    void timeout() { result.complete(timeoutAccepts); }
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

    private static String defaultTitle(Kind kind) {
        return switch (kind) {
            case ERROR -> "ERROR";
            case INFO -> "INFORMACIÓN";
            case CONFIRM -> "CONFIRMACIÓN";
            case TIMED_WARNING -> "AVISO";
        };
    }
}
