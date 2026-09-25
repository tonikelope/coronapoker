package com.tonikelope.coronapoker.core;

import java.util.concurrent.CompletionStage;

/** Executes typed lobby actions without exposing sockets to either frontend. */
@FunctionalInterface
public interface LobbyCommandSink {

    CompletionStage<Void> submit(LobbyCommand command);
}
