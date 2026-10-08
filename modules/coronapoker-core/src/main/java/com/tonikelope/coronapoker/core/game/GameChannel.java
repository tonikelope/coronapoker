/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
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

    /**
     * Subscribes to definitive peer losses detected by the transport after its
     * reconnect grace expires.  This is deliberately separate from GAME wire
     * traffic: a dead peer cannot authenticate an EXIT command on its own
     * behalf, while the canonical dealer still has to fold it, wake its waits
     * and relay the resulting EXIT transition to the surviving peers.
     */
    default AutoCloseable subscribePeerLoss(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        return () -> { };
    }

    /** Client-to-host command; completes only after the authenticated ACK. */
    CompletionStage<Void> sendToHost(String command) throws IOException;

    /** Host-to-all command, optionally excluding one nickname; completes after every ACK. */
    CompletionStage<Void> broadcastFromHost(String command, String skipNickname)
            throws IOException;

    /** Host-to-one command; completes only after the authenticated ACK. */
    CompletionStage<Void> sendFromHost(String nickname, String command) throws IOException;

    /** True while the logical peer is alive but its socket generation is down. */
    default boolean isPeerReconnecting(String nickname) {
        return false;
    }

    /**
     * True when the nickname currently belongs to a live authenticated socket
     * generation. This distinguishes a delayed loss callback from an older
     * incarnation after the same persistent identity has already rejoined.
     */
    default boolean isPeerConnected(String nickname) {
        return false;
    }

    /** Successful authenticated socket replacements for this logical peer. */
    default int peerReconnectionCount(String nickname) {
        return 0;
    }

    /** Latest primary heartbeat latency, or the transport's unknown sentinel. */
    default int peerLatency(String nickname) {
        return Integer.MIN_VALUE;
    }

    /** Latest secondary heartbeat latency, or the transport's unknown sentinel. */
    default int peerSecondaryLatency(String nickname) {
        return Integer.MIN_VALUE;
    }

    /**
     * Host-only transport operation that replaces every currently connected
     * remote-human socket generation while preserving the authenticated
     * logical peers and their queued GAME traffic.  The returned count is the
     * number of physical connections whose reconnect cycle was started.
     */
    default int forceReconnectRemotePeers() {
        throw new UnsupportedOperationException(
                "This game channel cannot force peer reconnection");
    }

    /**
     * Host-side retirement of a peer whose authenticated voluntary EXIT has
     * already been applied and relayed by the dealer.
     */
    default void retirePeerAfterExit(String nickname) {
    }

    /**
     * Atomically retires a peer only when its current authenticated socket
     * generation is still disconnected. Used by definitive-loss callbacks so
     * a delayed callback from an older generation cannot remove a connection
     * that has already reauthenticated under the same persistent identity.
     *
     * @return true when the disconnected logical peer was retired
     */
    default boolean retirePeerAfterDefinitiveLoss(String nickname) {
        return false;
    }

    /**
     * Promotes a transport-level warming connection to ordinary game traffic.
     * Called only after it has applied recovery and confirmed its first hand.
     */
    default void activatePeer(String nickname) {
    }

    /**
     * Client-only terminal close. Unlike {@link #close()}, this also retires
     * the physical host socket so the reconnect watchdog cannot resurrect a
     * table that the user deliberately left.
     */
    default void closeLocalHostConnection() {
        close();
    }

    @Override
    void close();
}
