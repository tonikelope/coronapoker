/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.List;

/** Atomic decoder for a complete current-protocol recovery action history. */
final class RecoveredActionBatch {

    enum Error {
        MISSING,
        INVALID_ACTION
    }

    static final class Action {

        private final String encoded;
        private final RecoveredActionCodec.Wire wire;

        private Action(String encoded, RecoveredActionCodec.Wire wire) {
            this.encoded = encoded;
            this.wire = wire;
        }

        String encoded() {
            return encoded;
        }

        RecoveredActionCodec.Wire wire() {
            return wire;
        }
    }

    static final class Result {

        private final List<Action> actions;
        private final Error error;
        private final RecoveredActionCodec.Error actionError;

        private Result(List<Action> actions, Error error,
                RecoveredActionCodec.Error actionError) {
            this.actions = actions;
            this.error = error;
            this.actionError = actionError;
        }

        boolean isOk() {
            return error == null;
        }

        List<Action> actions() {
            return actions;
        }

        Error error() {
            return error;
        }

        RecoveredActionCodec.Error actionError() {
            return actionError;
        }
    }

    private RecoveredActionBatch() {
    }

    static Result decode(String batch) {
        if (batch == null) {
            return failure(Error.MISSING, null);
        }
        if (batch.isEmpty() || "*".equals(batch)) {
            return success(List.of());
        }
        List<Action> decodedActions = new ArrayList<>();
        for (String token : batch.split("@", -1)) {
            // SQL emits a trailing '@'. Preserve the live decoder's treatment
            // of empty separators while still validating every actual token.
            if (token.isEmpty()) {
                continue;
            }
            RecoveredActionCodec.Result decoded = RecoveredActionCodec.decode(token);
            if (!decoded.isOk()) {
                return failure(Error.INVALID_ACTION, decoded.error());
            }
            decodedActions.add(new Action(token, decoded.value()));
        }
        return success(List.copyOf(decodedActions));
    }

    private static Result success(List<Action> actions) {
        return new Result(actions, null, null);
    }

    private static Result failure(Error error,
            RecoveredActionCodec.Error actionError) {
        return new Result(List.of(), error, actionError);
    }
}
