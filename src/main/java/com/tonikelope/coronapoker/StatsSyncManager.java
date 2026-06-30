package com.tonikelope.coronapoker;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the peer-to-peer statistics database sync — the protocol logic on
 * top of the data layer ({@link StatsSync}), the message codec
 * ({@link StatsSyncProtocol}) and the waiting-room wire glue
 * ({@link WaitingRoomFrame#statsSyncRawSendToServer}/{@code ...ToClient}).
 *
 * <p>Topology is star (clients ↔ host). Flow, flag-free except for one
 * {@code wantsReceive} bit so a side never pushes to an unwilling receiver:
 * <ol>
 *   <li>The <b>client</b>, on connect, sends a MANIFEST (its shareable ugis +
 *       whether it wants to receive) — but only if it participates at all.</li>
 *   <li>The <b>host</b>, on a client's MANIFEST: if it shares and the client
 *       wants to receive, pushes the games the client lacks; and if it wants to
 *       receive, replies with its own MANIFEST so the client can push back.</li>
 *   <li>On a peer's MANIFEST, a side that shares pushes {@code difference(mine,
 *       theirs)} as GAMES batches; the receiver imports them idempotently.</li>
 *   <li>When the host imports new games from one client, it re-forwards them to
 *       the other connected clients that lack them (same-session convergence),
 *       tracking each client's known set so a game is normally not re-sent (a
 *       rare concurrent overlap is harmless — imports dedup by ugi).</li>
 * </ol>
 *
 * <p>All heavy work (DB read/write, socket writes) runs off the reader thread, so
 * a sync never blocks game traffic; if a session ends mid-transfer the next
 * connection resumes it (imports are idempotent by ugi).
 */
public final class StatsSyncManager {

    private static final Logger LOGGER = Logger.getLogger(StatsSyncManager.class.getName());

    // Games per GAMES message. Small so a single binary frame stays well under the
    // 16 MB cap even with large hands, and so game traffic is never blocked long.
    private static final int GAMES_PER_BATCH = 25;

    private final WaitingRoomFrame room;

    // HOST only: per connected client, the ugis it is known to already have (its
    // manifest plus everything we have since sent it) and whether it wants games.
    private final ConcurrentHashMap<String, PeerState> peers = new ConcurrentHashMap<>();

    StatsSyncManager(WaitingRoomFrame room) {
        this.room = room;
    }

    /** CLIENT: the connection to the host is fully established. */
    void onConnectedToServer() {
        if (!GameFrame.SYNC_STATS_RECEIVE_PREF && !GameFrame.SYNC_STATS_SHARE_PREF) {
            LOGGER.log(Level.INFO, "StatsSync [CLIENT]: connected to host, but stats sync is fully OFF "
                    + "(both RECEIVE and SHARE disabled) — not syncing anything.");
            return; // fully opted out
        }
        Helpers.threadRun(() -> {
            try {
                List<String> mine = StatsSync.listShareableUgis();
                byte[] manifest = StatsSyncProtocol.manifestMessage(mine, GameFrame.SYNC_STATS_RECEIVE_PREF);
                room.statsSyncRawSendToServer(manifest);
                LOGGER.log(Level.INFO, "StatsSync [CLIENT]: connected — sent my manifest to the host: I hold {0} "
                        + "shareable game(s); wantsReceive={1}, willShare={2}. Now waiting for the host to reply.",
                        new Object[]{mine.size(), GameFrame.SYNC_STATS_RECEIVE_PREF, GameFrame.SYNC_STATS_SHARE_PREF});
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "StatsSync [CLIENT]: failed to send my manifest to the host", ex);
            }
        });
    }

    /** HOST: a client disconnected — drop its tracking state. */
    void onPeerGone(String nick) {
        if (peers.remove(nick) != null) {
            LOGGER.log(Level.INFO, "StatsSync [HOST]: client {0} left — dropped its sync tracking state.", nick);
        }
    }

    /**
     * A TYPE_DB message arrived from {@code peerNick}. {@code iAmHost} is true when
     * this peer is the host receiving from a client. Called on the reader thread;
     * the actual work is offloaded so the reader is never blocked.
     */
    void onMessage(String peerNick, byte[] message, boolean iAmHost) {
        if (!GameFrame.SYNC_STATS_RECEIVE_PREF && !GameFrame.SYNC_STATS_SHARE_PREF) {
            // Fully opted out: do not even decode a peer's (possibly hostile) frame.
            return;
        }
        byte subtype = StatsSyncProtocol.subtype(message);
        if (subtype == StatsSyncProtocol.MANIFEST) {
            Helpers.threadRun(() -> handleManifest(peerNick, message, iAmHost));
        } else if (subtype == StatsSyncProtocol.GAMES) {
            Helpers.threadRun(() -> handleGames(peerNick, message, iAmHost));
        }
    }

    private void handleManifest(String peerNick, byte[] message, boolean iAmHost) {
        String side = iAmHost ? "HOST" : "CLIENT";
        try {
            StatsSyncProtocol.Manifest manifest = StatsSyncProtocol.readManifest(message);
            List<String> mine = StatsSync.listShareableUgis();

            if (iAmHost) {
                PeerState state = new PeerState(manifest.wantsReceive);
                state.knownUgis.addAll(manifest.ugis);
                peers.put(peerNick, state);
            }

            LOGGER.log(Level.INFO, "StatsSync [{0}]: received manifest from {1} — it holds {2} game(s) and "
                    + "wantsReceive={3}; I hold {4} shareable game(s).",
                    new Object[]{side, peerNick, manifest.ugis.size(), manifest.wantsReceive, mine.size()});

            // Decide whether to push the games the peer is missing.
            if (!GameFrame.SYNC_STATS_SHARE_PREF) {
                LOGGER.log(Level.INFO, "StatsSync [{0}]: my SHARE is OFF — not sending any of my games to {1}.",
                        new Object[]{side, peerNick});
            } else if (!manifest.wantsReceive) {
                LOGGER.log(Level.INFO, "StatsSync [{0}]: {1} does not want to receive — not sending it any games.",
                        new Object[]{side, peerNick});
            } else {
                List<String> missing = StatsSync.difference(mine, manifest.ugis);
                if (missing.isEmpty()) {
                    LOGGER.log(Level.INFO, "StatsSync [{0}]: {1} already has all {2} of my game(s) — nothing to send.",
                            new Object[]{side, peerNick, mine.size()});
                } else {
                    LOGGER.log(Level.INFO, "StatsSync [{0}]: {1} is missing {2} of my {3} game(s) — sending them now.",
                            new Object[]{side, peerNick, missing.size(), mine.size()});
                    pushGames(peerNick, missing, iAmHost);
                }
            }

            // The host replies with its own manifest so the client can push back —
            // only if the host itself wants to receive. The client never replies
            // (it already sent its manifest on connect), so there is no loop.
            if (iAmHost) {
                if (GameFrame.SYNC_STATS_RECEIVE_PREF) {
                    LOGGER.log(Level.INFO, "StatsSync [HOST]: asking {0} for whatever I am missing — replying with "
                            + "my manifest ({1} game(s)).", new Object[]{peerNick, mine.size()});
                    room.statsSyncRawSendToClient(peerNick,
                            StatsSyncProtocol.manifestMessage(mine, true));
                } else {
                    LOGGER.log(Level.INFO, "StatsSync [HOST]: my RECEIVE is OFF — not requesting any games from {0}.",
                            peerNick);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "StatsSync [" + side + "]: could not process the manifest from " + peerNick, ex);
        }
    }

    private void handleGames(String peerNick, byte[] message, boolean iAmHost) {
        String side = iAmHost ? "HOST" : "CLIENT";
        if (!GameFrame.SYNC_STATS_RECEIVE_PREF) {
            LOGGER.log(Level.INFO, "StatsSync [{0}]: my RECEIVE is OFF — ignoring a games batch from {1}.",
                    new Object[]{side, peerNick});
            return; // we did not ask for games
        }
        try {
            byte[] blob = StatsSyncProtocol.gamesBlob(message);
            LOGGER.log(Level.INFO, "StatsSync [{0}]: receiving a games batch from {1} ({2} bytes) — importing...",
                    new Object[]{side, peerNick, blob.length});
            int imported = StatsSync.importGames(blob);
            LOGGER.log(Level.INFO, "StatsSync [{0}]: imported {1} NEW game(s) from {2} (the rest were already known).",
                    new Object[]{side, imported, peerNick});
            if (iAmHost && imported > 0) {
                // Same-session convergence: spread freshly-acquired games to the
                // other connected clients that still lack them.
                LOGGER.log(Level.INFO, "StatsSync [HOST]: spreading {0} freshly-received game(s) to the other clients.",
                        imported);
                forwardToOtherClients(peerNick);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "StatsSync [" + side + "]: could not process a games batch from " + peerNick, ex);
        }
    }

    /** HOST: push games each other client is still missing (idempotent, deduped per client). */
    private void forwardToOtherClients(String exceptNick) {
        if (!GameFrame.SYNC_STATS_SHARE_PREF) {
            LOGGER.log(Level.INFO, "StatsSync [HOST]: my SHARE is OFF — not forwarding to the other clients.");
            return;
        }
        List<String> mine = StatsSync.listShareableUgis();
        for (String nick : room.statsSyncClientNicks()) {
            if (nick.equals(exceptNick)) {
                continue;
            }
            PeerState state = peers.get(nick);
            if (state == null || !state.wantsReceive) {
                LOGGER.log(Level.FINE, "StatsSync [HOST]: skipping {0} when forwarding (unknown or not receiving).", nick);
                continue;
            }
            List<String> missing = StatsSync.difference(mine, state.knownUgis);
            if (missing.isEmpty()) {
                LOGGER.log(Level.FINE, "StatsSync [HOST]: {0} already has everything — nothing to forward.", nick);
            } else {
                LOGGER.log(Level.INFO, "StatsSync [HOST]: forwarding {0} game(s) to {1}.",
                        new Object[]{missing.size(), nick});
                pushGames(nick, missing, true);
            }
        }
    }

    /**
     * Exports {@code ugis} in batches and sends them as GAMES messages — to the
     * given client (host) or to the host (client). On the host the per-peer known
     * set is updated so a game is never sent to the same client twice.
     */
    private void pushGames(String peerNick, List<String> ugis, boolean iAmHost) {
        if (ugis == null || ugis.isEmpty()) {
            return;
        }
        String side = iAmHost ? "HOST" : "CLIENT";
        PeerState state = iAmHost ? peers.get(peerNick) : null;
        int totalBatches = (ugis.size() + GAMES_PER_BATCH - 1) / GAMES_PER_BATCH;
        LOGGER.log(Level.INFO, "StatsSync [{0}]: sending {1} game(s) to {2} in {3} batch(es) of up to {4}...",
                new Object[]{side, ugis.size(), peerNick, totalBatches, GAMES_PER_BATCH});
        int sent = 0;
        for (int i = 0; i < ugis.size(); i += GAMES_PER_BATCH) {
            List<String> batch = ugis.subList(i, Math.min(i + GAMES_PER_BATCH, ugis.size()));
            byte[] blob = StatsSync.exportGames(batch);
            if (blob == null) {
                LOGGER.log(Level.WARNING, "StatsSync [{0}]: could not serialize a batch of {1} game(s) for {2} — "
                        + "skipping that batch.", new Object[]{side, batch.size(), peerNick});
                continue; // export failed (logged in StatsSync); skip this batch
            }
            byte[] gamesMessage = StatsSyncProtocol.gamesMessage(blob);

            boolean delivered;
            if (iAmHost) {
                // A client can leave the lobby at any time; stop pushing the moment
                // its socket is gone instead of churning the remaining batches.
                delivered = room.statsSyncRawSendToClient(peerNick, gamesMessage);
            } else {
                room.statsSyncRawSendToServer(gamesMessage);
                delivered = true; // client→host is best-effort; NetClient handles a dead socket
            }
            if (!delivered) {
                LOGGER.log(Level.INFO, "StatsSync [{0}]: {1} disconnected mid-send — stopped after {2} of {3} game(s); "
                        + "the rest will go out on the next sync.", new Object[]{side, peerNick, sent, ugis.size()});
                break;
            }
            // Only mark a game as known once it actually went out, so a reconnect
            // re-pushes anything that did not make it.
            if (state != null) {
                state.knownUgis.addAll(batch);
            }
            sent += batch.size();
            LOGGER.log(Level.FINE, "StatsSync [{0}]: sent batch {1}/{2} ({3} game(s)) to {4}.",
                    new Object[]{side, (i / GAMES_PER_BATCH) + 1, totalBatches, batch.size(), peerNick});
        }
        if (sent > 0) {
            LOGGER.log(Level.INFO, "StatsSync [{0}]: finished sending {1} game(s) to {2}.",
                    new Object[]{side, sent, peerNick});
        }
    }

    private static final class PeerState {

        final boolean wantsReceive;
        final Set<String> knownUgis = ConcurrentHashMap.newKeySet();

        PeerState(boolean wantsReceive) {
            this.wantsReceive = wantsReceive;
        }
    }
}
