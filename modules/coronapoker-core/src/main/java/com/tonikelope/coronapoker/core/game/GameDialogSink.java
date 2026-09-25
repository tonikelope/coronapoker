/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Modal and transient decisions requested by the game without owning widgets. */
public interface GameDialogSink {

    enum Icon {
        NONE,
        ROBOT,
        BLINDS,
        STOP,
        MAINTENANCE,
        EXIT
    }

    CompletionStage<Void> showError(String message, int preferredWidth);

    CompletionStage<Void> showInfo(String message, Icon icon, int preferredWidth);

    CompletionStage<Boolean> confirm(String message, Icon icon);

    CompletionStage<Void> showTimedWarning(String message, int seconds);

    default CompletionStage<Void> showError(String message) {
        return showError(message, 0);
    }

    default CompletionStage<Void> showInfo(String message, Icon icon) {
        return showInfo(message, icon, 0);
    }

    static GameDialogSink noop() {
        return new GameDialogSink() {
            @Override
            public CompletionStage<Void> showError(String message, int preferredWidth) {
                Objects.requireNonNull(message, "message");
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> showInfo(String message, Icon icon, int preferredWidth) {
                Objects.requireNonNull(message, "message");
                Objects.requireNonNull(icon, "icon");
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Boolean> confirm(String message, Icon icon) {
                Objects.requireNonNull(message, "message");
                Objects.requireNonNull(icon, "icon");
                return CompletableFuture.completedFuture(false);
            }

            @Override
            public CompletionStage<Void> showTimedWarning(String message, int seconds) {
                Objects.requireNonNull(message, "message");
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
