/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Routes blocking dealer dialogs to native, in-window GDX overlays. */
final class GdxGameDialogSink implements GameDialogSink {

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

    private static CompletionStage<Boolean> show(GdxTableDialog.Kind kind,
            String message, Icon icon, int preferredWidth, int seconds) {
        GdxTableDialog request = new GdxTableDialog(kind,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(icon, "icon"), preferredWidth, seconds);
        GdxApplicationShell shell = GdxApplicationShell.active();
        if (shell == null) {
            request.dismiss();
            return request.result();
        }
        shell.showDialog(request);
        return request.result();
    }

}
