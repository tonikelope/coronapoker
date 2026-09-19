/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import com.tonikelope.coronapoker.core.game.GameText;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Routes blocking dealer dialogs to native, in-window GDX overlays. */
final class GdxGameDialogSink implements GameDialogSink {

    private final GameText text;

    GdxGameDialogSink() {
        this(GameText.keys());
    }

    GdxGameDialogSink(GameText text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public CompletionStage<Void> showError(String message, int preferredWidth) {
        return show(GdxTableDialog.Kind.ERROR, message, Icon.NONE,
                preferredWidth, 0).thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<Void> showInfo(String message, Icon icon,
            int preferredWidth) {
        return show(GdxTableDialog.Kind.INFO, message, icon,
                preferredWidth, 0).thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<Boolean> confirm(String message, Icon icon) {
        return show(GdxTableDialog.Kind.CONFIRM, message, icon, 0, 0);
    }

    @Override
    public CompletionStage<Void> showTimedWarning(String message, int seconds) {
        return show(GdxTableDialog.Kind.TIMED_WARNING, message, Icon.NONE,
                0, seconds).thenApply(ignored -> null);
    }

    private CompletionStage<Boolean> show(GdxTableDialog.Kind kind,
            String message, Icon icon, int preferredWidth, int seconds) {
        GdxTableDialog request = request(kind, message, icon, preferredWidth,
                seconds);
        GdxApplicationShell shell = GdxApplicationShell.active();
        if (shell == null) {
            request.dismiss();
            return request.result();
        }
        shell.showDialog(request);
        return request.result();
    }

    GdxTableDialog request(GdxTableDialog.Kind kind, String message,
            Icon icon, int preferredWidth, int seconds) {
        String title = switch (kind) {
            case ERROR -> tr("gdx.dialog.error", "ERROR");
            case INFO -> tr("gdx.dialog.information", "INFORMACIÓN");
            case CONFIRM -> tr("gdx.dialog.confirmation", "CONFIRMACIÓN");
            case TIMED_WARNING -> tr("gdx.dialog.warning", "AVISO");
            default -> throw new IllegalArgumentException(
                    "Unsupported generic dialog kind: " + kind);
        };
        return new GdxTableDialog(kind, title,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(icon, "icon"), preferredWidth,
                seconds, kind == GdxTableDialog.Kind.TIMED_WARNING,
                tr("ui.cancelar", "CANCELAR"),
                kind == GdxTableDialog.Kind.CONFIRM
                        ? tr("ui.aceptar", "ACEPTAR")
                        : tr("ui.cerrar", "CERRAR"));
    }

    private String tr(String key, String fallback) {
        String translated = text.translate(key);
        return key.equals(translated) ? fallback : translated;
    }

}
