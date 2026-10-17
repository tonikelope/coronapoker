/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.io.IOException;
import java.util.Objects;

/**
 * Structured lobby refusal preserved across the renderer-neutral network
 * boundary so each frontend can explain the failure in its active language.
 */
public final class LobbyConnectionRefusedException extends IOException {

    public enum Reason {
        BAD_VERSION,
        TABLE_STARTED,
        HOT_JOIN_DISABLED,
        TABLE_FULL,
        NICK_TAKEN,
        NICK_UNAUTHORIZED,
        IDENTITY_MISMATCH,
        HOT_JOIN_THROTTLED,
        CONNECTION_ABUSE_BLOCKED,
        SERVER_REJECTED
    }

    private final Reason reason;
    private final long retryAfterSeconds;

    LobbyConnectionRefusedException(Reason reason, long retryAfterSeconds,
            String diagnosticMessage) {
        super(diagnosticMessage);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.retryAfterSeconds = Math.max(0L, retryAfterSeconds);
    }

    public Reason reason() {
        return reason;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
