package com.tonikelope.coronapoker.core;

import java.util.Objects;

/** Immutable handoff from either frontend to the shared session layer. */
public record NewGameRequest(NewGameConnectionDraft.Submission connection,
        NewGameTableDraft.Settings table) {

    public NewGameRequest {
        Objects.requireNonNull(connection, "connection");
        boolean joining = connection.mode() == NewGameConnectionDraft.Mode.JOIN;
        if (joining && table != null) {
            throw new IllegalArgumentException("Join requests cannot replace host table settings");
        }
        if (!joining && table == null) {
            throw new IllegalArgumentException("Host requests require table settings");
        }
        if (joining && connection.recover()) {
            throw new IllegalArgumentException("Join requests cannot recover a local game");
        }
    }

    /**
     * Locks the connection draft exactly once and snapshots every value that
     * the asynchronous session opener is allowed to observe.
     */
    public static NewGameRequest begin(NewGameConnectionDraft connection,
            NewGameTableDraft table) {
        Objects.requireNonNull(connection, "connection");
        NewGameConnectionDraft.Submission submission = connection.beginSubmission();
        try {
            NewGameTableDraft.Settings settings
                    = submission.mode() == NewGameConnectionDraft.Mode.JOIN
                            ? null : Objects.requireNonNull(table, "table").snapshot();
            return new NewGameRequest(submission, settings);
        } catch (RuntimeException | Error failure) {
            connection.submissionFailed();
            throw failure;
        }
    }

    public boolean joining() {
        return connection.mode() == NewGameConnectionDraft.Mode.JOIN;
    }
}
