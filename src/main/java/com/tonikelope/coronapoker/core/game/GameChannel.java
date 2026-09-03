/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Ordered, authenticated GAME-command channel handed from the lobby to the
 * canonical controller. Commands never include the GAME/id envelope.
 */
public interface GameChannel extends AutoCloseable {

    record Inbound(String peerNickname, String command) {
        public Inbound {
            peerNickname = Objects.requireNonNull(peerNickname, "peerNickname");
            command = Objects.requireNonNull(command, "command");
        }
    }

    /** Installs the sole consumer and drains commands received during handoff. */
    AutoCloseable subscribe(Consumer<Inbound> listener);

    /** Client-to-host command. */
    void sendToHost(String command) throws IOException;

    /** Host-to-all command, optionally excluding one nickname. */
    void broadcastFromHost(String command, String skipNickname) throws IOException;

    /** Host-to-one command. */
    void sendFromHost(String nickname, String command) throws IOException;

    @Override
    void close();
}
