/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Base64;

/** Explicit one-shot state machine for a critical RECOVERDATA frame. */
public final class RecoveryReceiveState {

    public enum Status {
        WAITING, RECEIVED, DECODED, VALIDATED, FAILED
    }

    private final String expectedSession;
    private Status status = Status.WAITING;
    private RecoverySnapshotV1 snapshot;
    private String error;

    public RecoveryReceiveState(String expectedSession) {
        this.expectedSession = expectedSession;
    }

    public synchronized void acceptBase64(String encoded) {
        requireWaiting();
        status = Status.RECEIVED;
        final byte[] wire;
        try {
            wire = Base64.getDecoder().decode(encoded);
        } catch (RuntimeException ex) {
            fail("INVALID_BASE64");
            return;
        }
        status = Status.DECODED;
        final RecoverySnapshotV1.Result decoded;
        try {
            decoded = RecoverySnapshotV1.decode(wire, expectedSession);
        } catch (RuntimeException ex) {
            fail("DECODE_FAILURE");
            return;
        }
        if (!decoded.isOk()) {
            fail("SNAPSHOT_" + decoded.error());
            return;
        }
        snapshot = decoded.value();
        status = Status.VALIDATED;
    }

    public synchronized void rejectMalformedFrame() {
        requireWaiting();
        status = Status.RECEIVED;
        fail("MALFORMED_FRAME");
    }

    public synchronized void rejectTimeout() {
        requireWaiting();
        fail("TIMEOUT");
    }

    public synchronized void rejectInterrupted() {
        requireWaiting();
        fail("INTERRUPTED");
    }

    public synchronized void rejectTransportClosed() {
        requireWaiting();
        fail("TRANSPORT_CLOSED");
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized boolean isTerminal() {
        return status == Status.VALIDATED || status == Status.FAILED;
    }

    public synchronized boolean isSuccess() {
        return status == Status.VALIDATED && snapshot != null;
    }

    public synchronized boolean shouldRetry() {
        return status == Status.WAITING;
    }

    public synchronized RecoverySnapshotV1 snapshot() {
        return snapshot;
    }

    public synchronized String error() {
        return error;
    }

    private void requireWaiting() {
        if (status != Status.WAITING) {
            throw new IllegalStateException("RECOVERDATA already resolved: " + status);
        }
    }

    private void fail(String reason) {
        snapshot = null;
        error = reason;
        status = Status.FAILED;
    }
}
