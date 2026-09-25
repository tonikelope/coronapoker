/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Explicit one-shot state machine for the critical ACTIONDATA frame. */
public final class RecoveryActionReceiveState {

    public enum Status {
        WAITING, VALIDATED, FAILED
    }

    private Status status = Status.WAITING;
    private String actions;
    private String error;

    public synchronized void acceptFrame(String frame) {
        requireWaiting();
        String[] parts = frame == null ? new String[0] : frame.split("#", -1);
        if (parts.length != 4 || !"ACTIONDATA".equals(parts[2])) {
            fail("MALFORMED_FRAME");
            return;
        }
        if ("*".equals(parts[3])) {
            actions = "";
            status = Status.VALIDATED;
            return;
        }
        if (parts[3].isEmpty()) {
            fail("NON_CANONICAL_EMPTY");
            return;
        }
        try {
            byte[] wire = Base64.getDecoder().decode(parts[3]);
            actions = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(wire)).toString();
            if (actions.isEmpty()) {
                fail("NON_CANONICAL_EMPTY");
                return;
            }
            status = Status.VALIDATED;
        } catch (Exception ex) {
            fail("INVALID_PAYLOAD");
        }
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
        return status == Status.VALIDATED && actions != null;
    }

    public synchronized String actions() {
        return actions;
    }

    public synchronized String error() {
        return error;
    }

    private void requireWaiting() {
        if (status != Status.WAITING) {
            throw new IllegalStateException("ACTIONDATA already resolved: " + status);
        }
    }

    private void fail(String reason) {
        actions = null;
        error = reason;
        status = Status.FAILED;
    }
}
