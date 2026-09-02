package com.tonikelope.coronapoker.core;

import java.util.concurrent.CompletableFuture;

/** Asynchronous boundary that opens the real host or client session. */
@FunctionalInterface
public interface NewGameSessionGateway {

    /** Completes only when ownership has passed to the lobby/session layer. */
    CompletableFuture<Void> open(NewGameRequest request);
}
