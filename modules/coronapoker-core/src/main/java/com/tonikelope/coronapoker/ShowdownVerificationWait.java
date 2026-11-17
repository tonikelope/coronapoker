/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameDialogSink;
import java.util.function.Supplier;

/** Shows a slow-wait notice only while the operation is still pending. */
final class ShowdownVerificationWait implements AutoCloseable {
    private final Supplier<GameDialogSink.WaitDialog> showDialog;
    private GameDialogSink.WaitDialog dialog;
    private boolean finished;

    ShowdownVerificationWait(Supplier<GameDialogSink.WaitDialog> showDialog) {
        this.showDialog = showDialog;
    }

    synchronized void countdownExpired() {
        if (!finished && dialog == null) {
            dialog = showDialog.get();
        }
    }

    @Override
    public synchronized void close() {
        finished = true;
        if (dialog != null) {
            dialog.close();
            dialog = null;
        }
    }
}
