package com.tonikelope.coronapoker.core;

import java.util.concurrent.CompletableFuture;

/** Asynchronous boundary that opens the real host or client session. */
@FunctionalInterface
public interface NewGameSessionGateway {

    /** Completes only when ownership has passed to a usable lobby session. */
    CompletableFuture<LobbySession> open(NewGameRequest request);
}
