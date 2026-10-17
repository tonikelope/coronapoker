package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.core.network.UpnpPortReservation;
import java.util.Objects;

/** Immutable handoff from either frontend to the shared session layer. */
public record NewGameRequest(NewGameConnectionDraft.Submission connection,
        NewGameTableDraft.Settings table,
        UpnpPortReservation upnpReservation) {

    public NewGameRequest(NewGameConnectionDraft.Submission connection,
            NewGameTableDraft.Settings table) {
        this(connection, table, null);
    }

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
        if (upnpReservation != null) {
            if (joining || !connection.upnp()) {
                throw new IllegalArgumentException(
                        "Only a UPnP host request may carry a port reservation");
            }
            if (upnpReservation.port()
                    != Integer.parseInt(connection.port())) {
                throw new IllegalArgumentException(
                        "UPnP reservation does not match the requested port");
            }
        }
    }

    /**
     * Locks the connection draft exactly once and snapshots every value that
     * the asynchronous session opener is allowed to observe.
     */
    public static NewGameRequest begin(NewGameConnectionDraft connection,
            NewGameTableDraft table) {
        return begin(connection, table, null);
    }

    public static NewGameRequest begin(NewGameConnectionDraft connection,
            NewGameTableDraft table,
            UpnpPortReservation upnpReservation) {
        Objects.requireNonNull(connection, "connection");
        NewGameConnectionDraft.Submission submission = connection.beginSubmission();
        try {
            NewGameTableDraft.Settings settings
                    = submission.mode() == NewGameConnectionDraft.Mode.JOIN
                            ? null : Objects.requireNonNull(table, "table").snapshot();
            return new NewGameRequest(submission, settings, upnpReservation);
        } catch (RuntimeException | Error failure) {
            connection.submissionFailed();
            throw failure;
        }
    }

    public boolean joining() {
        return connection.mode() == NewGameConnectionDraft.Mode.JOIN;
    }

    /** Releases a form-owned mapping when the lobby never adopted it. */
    public void closeUnclaimedResources() {
        if (upnpReservation != null) upnpReservation.close();
    }
}
