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

/** Frontend-owned playback of an in-game cinematic. */
public interface GameCinematicSink {

    enum Type {
        ALL_IN,
        IWTSTH_REQUEST,
        IWTSTH_DENIED
    }

    record Request(Type type, String assetName, long durationMillis) {

        public Request {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(assetName, "assetName");
            if (assetName.isBlank() || durationMillis < 0L) {
                throw new IllegalArgumentException("invalid cinematic request");
            }
        }
    }

    record Result(boolean shown, boolean skipped) {
    }

    CompletionStage<Result> play(Request request);

    static GameCinematicSink noop() {
        return request -> {
            Objects.requireNonNull(request, "request");
            return CompletableFuture.completedFuture(new Result(false, false));
        };
    }
}
