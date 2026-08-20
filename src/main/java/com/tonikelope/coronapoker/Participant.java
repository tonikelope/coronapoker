/*
 * Copyright (C) 2026 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import static com.tonikelope.coronapoker.WaitingRoomFrame.PING_INTERVAL_MS;
import static com.tonikelope.coronapoker.WaitingRoomFrame.POISON_PILL;
import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Host-side handle for a single peer's connection: owns its socket, the reader
 * / writer / ping-pong threads, the reconnect grace-period state machine,
 * per-peer anti-DoS rate limiting, and dispatches incoming GAME subcommands
 * into the Crupier's command queue.
 */
public class Participant implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Participant.class.getName());
    private static final AtomicLong NEXT_REBUY_SOURCE_ID = new AtomicLong();

    public static final int ASYNC_COMMAND_QUEUE_WAIT = 1000;

    // Base grace period after the reader's first null-read on a peer socket, before any
    // authenticated reconnect attempt has arrived. 45s is anchored to the worst-case "mute
    // socket" detection window: MAX_CONSECUTIVE_PING_FAILURES * (PING_INTERVAL_MS +
    // PING_PONG_TIMEOUT) = 3 * (5s + 10s) = 45s, so the host never gives up before the peer
    // itself could even notice it dropped and start reconnecting (it used to be 40s, just
    // under that 45s detection window). signalReconnectIntent() extends the deadline to
    // GameFrame.CLIENT_RECON_TIMEOUT (80s, independent of think-time) once an HMAC-
    // authenticated reconnect attempt lands, giving a legit peer time to finish resetSocket
    // even over a slow handshake.
    public static final int RECIBIDO_TIMEOUT = 45000;

    // ZERO-TRUST: response subcommands whose parts[3] is the SENDER's nick. Before queueing
    // them into received_commands (where the Crupier matches them by that nick), the reader
    // requires parts[3] == the nick owning THIS authenticated connection — otherwise a peer
    // could inject a response on behalf of another (force-fold a rival, frame the deck
    // cascade, hide their showdown, forge a REBUY/BUYIN, hijack an RIT vote, poison the deal).
    // HANDVERIFY and STRADDLE_RESP already had this guard inline; it's generalized here.
    private static final java.util.Set<String> NICK_BOUND_SUBCOMMANDS = java.util.Set.of(
            "ACTION", "REBUY", "BUYIN", "RESP_SHOWDOWN_KEY",
            "DECK_CASCADE_RESP", "DECK_ROTATION_RESP", "RESP_SRA_UNLOCK_CHAIN", "RIT_VOTE_RESP",
            "SEAT_COMMIT", "SEAT_REVEAL");

    // F2 ANTI-DoS: per-peer size cap + frequency token-bucket for non-critical text commands. Thresholds
    // are huge relative to legit traffic (a real game command is < 10 KB, a few per second),
    // so they never trigger in normal play — only under flood/OOM. Over the limit -> SILENT-
    // REFUSE (drop) + strike; accumulated strikes -> AUTO-EXPEL (only this peer is kicked, the
    // table continues). One reader thread per peer touches these fields -> no lock needed.
    private static final int MAX_INBOUND_COMMAND_CHARS = 512 * 1024;  // caps the ~12 MB/entry OOM (legit << 10 KB)
    private static final double INBOUND_BURST = 2000.0;               // tolerated burst
    private static final double INBOUND_REFILL_PER_SEC = 500.0;       // tolerated sustained rate (legit: a few/s)
    private static final int MAX_ABUSE_STRIKES = 50;                  // strikes before expulsion
    private double inbound_tokens = INBOUND_BURST;
    private long inbound_last_refill_ns = 0L;
    private int abuse_strikes = 0;

    // F2 ANTI-DoS (BINARY channel: voice + stats sync). Binary frames are handled INLINE on
    // the reader thread (handleBinaryFromClient), not in run()'s loop, so they skip
    // inboundAbuse/strikes. Each one triggers heavy async work (StatsSync = a thread+DB write
    // per frame; a voice note = disk write + relay to N peers). Per-frame work is already
    // bounded (WireFrame size cap, gzip-bomb closed), so capping the arrival RATE bounds
    // thread/CPU/disk/relay growth to a constant. The budget is generous but well above legit
    // use (human voice ~1/s; the stats burst on connect is << the burst allowance). Over the
    // limit -> silent DROP, no strike/expulsion: a legit stats burst (large backlog) is
    // indistinguishable from abuse, and dropping is harmless (import is idempotent, it resyncs
    // next session). Flood expulsion still lives on the text path (F2 above). Single thread
    // (the peer's reader) -> no lock needed.
    private static final double BINARY_INBOUND_BURST = 128.0;            // tolerated binary burst
    private static final double BINARY_INBOUND_REFILL_PER_SEC = 8.0;     // tolerated binary sustained rate (legit: << 1/s)
    private double binary_inbound_tokens = BINARY_INBOUND_BURST;
    private long binary_inbound_last_refill_ns = 0L;

    private final Object ping_pong_lock = new Object();
    private final Object participant_socket_lock = new Object();
    private final GameCommandGate game_command_gate
            = new GameCommandGate(GameCommandType.Direction.CLIENT_TO_HOST);
    private final ConcurrentLinkedQueue<String> pre_game_socket_writer_queue = new ConcurrentLinkedQueue<>();
    // BOUNDED queue. Unbounded, it would grow until memory ran out, and the abuse guard that
    // should throttle a flood runs at CONSUME time — i.e. behind it: a peer spamming commands
    // faster than they're processed would OOM the process before anything stops it. Once full,
    // that peer's reader blocks waiting for room, stops draining its socket, and TCP
    // backpressure does the rest. The cap is generous for normal play, where legit bursts are
    // dozens of messages.
    public static final int SOCKET_READER_QUEUE_CAPACITY = 10000;

    private final LinkedBlockingQueue<String> socket_reader_queue = new LinkedBlockingQueue<>(SOCKET_READER_QUEUE_CAPACITY);
    private final WaitingRoomFrame sala_espera;
    private final String nick;
    // The reader is the sole owner of this counter. It is copied into the async task before
    // Helpers.THREAD_POOL can reorder execution, and the source id distinguishes reconnects
    // that happen to use the same nickname.
    private final long rebuy_source_id = NEXT_REBUY_SOURCE_ID.incrementAndGet();
    private long rebuy_inbound_sequence = 0L;
    private final Object pause_sequence_lock = new Object();
    private long pause_inbound_sequence = 0L;
    private long pause_applied_sequence = 0L;
    private final File avatar;

    private volatile Socket socket = null;
    private volatile Socket recon_socket = null;
    private volatile boolean exit = false;
    private volatile BufferedInputStream input_stream_reader = null;
    private volatile Integer pong;
    private volatile Integer pong2;
    private volatile boolean cpu = false;
    private volatile Boolean resetting_socket = false;
    private volatile SecretKeySpec aes_key = null;
    private volatile SecretKeySpec hmac_key = null;
    private volatile SecretKeySpec hmac_key_orig = null;
    private volatile boolean unsecure_player = false;
    private volatile boolean reset_socket = false;
    private volatile String avatar_chat_src;
    private volatile boolean async_wait = false;
    private volatile boolean force_reset_socket = false;

    // Is this peer in its GRACE PERIOD? Set by the reader on detecting a drop, cleared when
    // the peer speaks again. Used to be a local variable inside the reader, so writers
    // couldn't see a grace was in progress and would kick the peer on the first failed write.
    private volatile boolean timeout = false;
    // "Force reconnect" generation: each forceSocketReconnectWithWatchdog bumps it (under
    // lock). The watchdog captures its own at start and only acts if still current, so a
    // stale watchdog can't expel a peer over a NEWER force call (e.g. a double-click on the
    // "Force reconnect" menu within the grace window).
    private volatile int force_reset_generation = 0;
    private volatile int latency;
    private volatile int latency2;
    private volatile int pong_timeout_counter = 0;
    // Consecutive times the heartbeat write hasn't returned in time. More than one is
    // required for the same reason as the PONGs below: a single stall can just be a long
    // binary send in flight, holding the socket's write turn while everything's fine.
    private volatile int ping_write_stall_counter = 0;

    // Wait before retrying a pre-game command whose write failed. See the loop in
    // runPreGameSocketWriterQueueThread.
    private static final int PRE_GAME_WRITE_RETRY_MS = 1000;

    // Timestamp when WE closed the socket ourselves over a stalled write. That close is not
    // grounds to kick anyone: a peer that isn't reading doesn't block the others, and one that
    // does is already caught by the progress deadlines (hand start, deal), which kick whoever
    // is actually holding up the table. Closing only wakes the reader, which is what opens the
    // grace period. Without this marker, the stalled write wakes up with an error on close and
    // its catch block would give up on the peer before the reader had a chance to open any
    // grace window, so the reconnect window would never exist.
    //
    // It's a timestamp rather than a boolean ON PURPOSE: it expires on its own. The reader
    // clears it as soon as it takes over, but if that thread ever died, a boolean would stay
    // set forever and NOBODY could ever give up on this peer again — it'd be a zombie for the
    // rest of the game. Expiring, the worst case is just falling back to normal behavior a few
    // seconds later.
    private static final long NO_STALL_CLOSE = Long.MIN_VALUE;
    private volatile long stall_close_ns = NO_STALL_CLOSE;
    // Time given to the reader to take over after that close. Plenty: it only needs to wake
    // from its read and open the grace period, a matter of milliseconds. Measured with a
    // MONOTONIC clock, like the latencies below: with wall-clock time, a backward adjustment
    // (NTP, waking from suspend) could make the subtraction negative and leave the marker
    // stuck set — exactly what the expiry is meant to avoid.
    private static final long STALL_CLOSE_GRACE_NS = 10_000_000_000L;
    // Last room-password change actually written to THIS peer, plus its lock. See
    // writeRoomPassword.
    private final Object password_write_lock = new Object();
    private volatile long last_password_version = 0;
    private volatile int pong2_timeout_counter = 0;
    // Telemetry: count of this peer's successful reconnects to the server. Incremented in
    // resetSocket() after reset_socket is set true. Covers both natural reconnects (peer drops
    // + returns) and ones forced via the "Force reconnect" menu — both signal observable link
    // instability.
    private volatile int reconnection_count = 0;
    private volatile byte[] received_token = null;
    private volatile int new_hand_ready = 0;

    // Deadline floor for the grace wait in runSocketReaderThread. signalReconnectIntent()
    // raises it to now()+CLIENT_RECON_TIMEOUT when an incoming reconnect attempt passes HMAC
    // verification against hmac_key_orig: the reader, on rearming its wait, uses
    // max(current_deadline, grace_deadline_floor), extending the grace as needed for a legit
    // client to finish the handshake over a slow network. Monotonic — only grows.
    private volatile long grace_deadline_floor = 0L;

    // Whether runPingPongThread is currently running. The thread deliberately dies via break
    // after socketClose() once the peer misses MAX_CONSECUTIVE_PING_FAILURES PONGs; if the
    // peer later reconnects and resetSocket restores the channel, the counters reset but the
    // thread stays dead -> the peer loses active PING supervision until its socket next fails
    // on write (or never, if nobody writes). resetSocket checks this flag and relaunches the
    // thread when needed.
    private volatile boolean ping_pong_thread_alive = false;

    // --- SRA ZERO-TRUST VARIABLES ---
    // sra_unlock: scalar for POCKET pieces. Used to be the peer's only key; after the dual-
    // lock refactor it's still valid for pockets but must NEVER be handed over via the
    // testament — exposing it would let the host decrypt the leaving peer's pocket cards.
    private volatile byte[] sra_unlock = null;
    // sra_unlock_community: scalar for community pieces after the rotation phase. The only
    // half included in the testament on EXIT, so the game can keep revealing community cards
    // without exposing pockets.
    private volatile byte[] sra_unlock_community = null;

    public byte[] getSra_unlock() {
        return sra_unlock;
    }

    public void setSra_unlock(byte[] sra_unlock) {
        this.sra_unlock = sra_unlock;
    }

    public byte[] getSra_unlock_community() {
        return sra_unlock_community;
    }

    public void setSra_unlock_community(byte[] sra_unlock_community) {
        this.sra_unlock_community = sra_unlock_community;
    }

    // --- Identity ---
    // Cached at JOIN time. The host keeps these so it can later relay the verbatim
    // self_sig to any peer that connects after this one (atomic identity transport
    // via intro/USERSLIST/NEWUSER).
    private volatile byte[] identity_pubkey = null;
    private volatile byte[] identity_self_sig = null;

    public byte[] getIdentity_pubkey() {
        return identity_pubkey;
    }

    public void setIdentity_pubkey(byte[] pubkey) {
        this.identity_pubkey = pubkey;
    }

    public byte[] getIdentity_self_sig() {
        return identity_self_sig;
    }

    public void setIdentity_self_sig(byte[] self_sig) {
        this.identity_self_sig = self_sig;
    }

    public int getNew_hand_ready() {
        return new_hand_ready;
    }

    public void setNew_hand_ready(int new_hand_ready) {
        this.new_hand_ready = new_hand_ready;
    }

    public byte[] getReceived_token() {
        return received_token;
    }

    public void setReceived_token(byte[] received_token) {
        this.received_token = received_token;
    }

    public int getLatency2() {
        return latency2;
    }

    public int getLatency() {
        return latency;
    }

    /**
     * Telemetry: number of successful reconnects of this peer to the server
     * since this Participant was created (game start or joining the room). Only
     * counts reconnects that reached reset_socket=true.
     *
     * @return the reconnect count
     */
    public int getReconnectionCount() {
        return reconnection_count;
    }

    /**
     * @param espera the waiting room this peer belongs to
     * @param nick the peer's authenticated nick
     * @param avatar the peer's avatar image file, or {@code null} for the
     * default avatar
     * @param socket the peer's already-connected socket
     * @param aes_k session AES key for this peer's channel
     * @param hmac_k session HMAC key for this peer's channel
     * @param cpu whether this Participant represents a bot seat rather than a
     * real peer
     */
    public Participant(WaitingRoomFrame espera, String nick, File avatar, Socket socket, SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu) {
        this.nick = nick;
        this.setSocket(socket);
        this.sala_espera = espera;
        this.avatar = avatar;
        this.cpu = cpu;
        this.aes_key = aes_k;
        this.hmac_key = hmac_k;
        this.hmac_key_orig = hmac_k;

        if (avatar != null) {
            try {
                // The peer's avatar lives in tmpdir; its _chat thumbnail is a
                // sibling temp file. Clean it up on exit (the base avatar is
                // owned and explicitly cleaned by the waiting-room AvatarIO store).
                java.awt.image.BufferedImage thumbnail = Helpers.toBufferedImage(
                        new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage()
                                .getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage());
                File avatar_chat;
                if (espera.isOwnedRemoteAvatar(avatar)) {
                    avatar_chat = espera.writeRemoteAvatarThumbnail(avatar, thumbnail);
                } else {
                    avatar_chat = new File(avatar.getAbsolutePath() + "_chat");
                    avatar_chat.deleteOnExit();
                    ImageIO.write(thumbnail, "png", avatar_chat);
                }
                avatar_chat_src = avatar_chat.toURI().toURL().toExternalForm();
            } catch (IOException ex) {
                avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
            }
        } else {
            avatar_chat_src = cpu ? getClass().getResource("/images/avatar_bot_chat.png").toExternalForm() : getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
        }
    }

    public void setForce_reset_socket(boolean force) {
        this.force_reset_socket = force;
    }

    public boolean isForce_reset_socket() {
        return force_reset_socket;
    }

    /**
     * Anti-DoS: true if this peer's socket is DOWN or mid-reconnect (reset in
     * progress, host- forced reconnect, or a null/closed socket). A peer that's
     * STALLING (alive, answering PING but not sending what's expected) returns
     * false — its socket is still open. Broadcast/recovery progress deadlines
     * use this to NOT expel a peer that's legitimately reconnecting (give it
     * its grace) but DO expel a live one that's stalling. Player.timeout isn't
     * used here because those loops set it themselves on pending players (to
     * show "waiting"), which would contaminate the signal.
     *
     * @return true if the socket is down or reconnecting
     */
    public boolean isSocketDownOrReconnecting() {
        if (resetting_socket || force_reset_socket) {
            return true;
        }
        Socket s = this.socket;
        return s == null || s.isClosed();
    }

    public boolean isAsync_wait() {
        return async_wait;
    }

    public void setAsync_wait(boolean async_w) {
        if (this.async_wait != async_w) {
            this.async_wait = async_w;
            Helpers.GUIRun(() -> {
                WaitingRoomFrame.getInstance().getConectados().revalidate();
                WaitingRoomFrame.getInstance().getConectados().repaint();
            });
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public String getAvatar_chat_src() {
        return avatar_chat_src;
    }

    private void runPingPongThread() {
        ping_pong_thread_alive = true;
        Helpers.threadRun(() -> {
            try {
                while (!exit && WaitingRoomFrame.getInstance() != null) {
                    final int ping = Helpers.CSPRNG_GENERATOR.nextInt();
                    pong = null;
                    pong2 = null;
                    latency = -1;
                    latency2 = -1;
                    long pingStartNs = System.nanoTime();

                    // The PING write is capped. Writing to a socket whose peer isn't reading
                    // eventually fills the OS buffer and the write just hangs, with no deadline,
                    // holding the socket's write monitor: the very watchdog meant to catch that
                    // peer would get stuck inside the write it was supposed to be watching, and
                    // block anyone else trying to write to it too.
                    //
                    // Mind the cap: that monitor is shared with BINARY sends (a voice note, an
                    // avatar, recovery data, a stats batch), which with several peers and thin
                    // upload can legitimately take a while even when everyone's healthy; and while
                    // a reconnect is in progress, the write is SUPPOSED to wait. So the timeout is
                    // generous, doesn't count while the peer is reconnecting, and needs SEVERAL in
                    // a row — same threshold as the lost-PONGs close below. The counter resets on
                    // reconnect so stalls on the old socket aren't inherited by the new one. And
                    // hitting the limit doesn't kick anyone: it just closes the socket so the
                    // reader can open the grace period.
                    java.util.concurrent.Future<?> ping_write;

                    try {
                        ping_write = Helpers.THREAD_POOL.submit(
                                () -> writeCommandFromServer("PING#" + String.valueOf(ping)));
                    } catch (Exception ex) {
                        break;
                    }

                    try {
                        ping_write.get(WaitingRoomFrame.PING_WRITE_STALL_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS);
                        ping_write_stall_counter = 0;
                    } catch (java.util.concurrent.TimeoutException ex) {
                        if (!exit && !resetting_socket && !force_reset_socket
                                && ++ping_write_stall_counter >= WaitingRoomFrame.MAX_CONSECUTIVE_PING_FAILURES) {
                            LOGGER.log(Level.SEVERE,
                                    "PING write to {0} stalled {1} times in a row ({2} ms each) — peer is not reading; closing its socket",
                                    new Object[]{nick, ping_write_stall_counter, WaitingRoomFrame.PING_WRITE_STALL_TIMEOUT});
                            // The peer is NOT given up on here: this only closes the socket, same
                            // as the lost-heartbeat twin below. Closing wakes the reader, which is
                            // what opens the grace period and gives the peer its window to return
                            // — a laptop that suspends for a while shouldn't be kicked for not
                            // reading. And if it were actually holding up the table, that's
                            // already handled by the progress deadlines, which expel whoever's
                            // stalling.
                            //
                            // The marker is essential: the write that just timed out is still
                            // stuck on this socket, and closing it wakes it with an IOException
                            // whose catch block would give up on the peer before the reader got a
                            // chance to open the grace period.
                            ping_pong_thread_alive = false;
                            stall_close_ns = System.nanoTime();
                            try {
                                socketClose();
                            } catch (Exception ignored) {
                            }
                            break;
                        }

                        // The write is left to run its course (cancelling it wouldn't unblock a
                        // stuck write, and the socket may be mid-reinstall). The PONG counter is
                        // left alone: without a PING there can be no PONG, and counting it as lost
                        // would kick the peer through the other path. Wait the normal interval and
                        // retry.
                        Helpers.pausar(WaitingRoomFrame.PING_INTERVAL_MS);
                        continue;
                    } catch (Exception ex) {
                        break;
                    }

                    long end = System.currentTimeMillis() + WaitingRoomFrame.PING_PONG_TIMEOUT;

                    while (!exit && (pong == null || pong2 == null) && System.currentTimeMillis() < end) {
                        synchronized (ping_pong_lock) {
                            // Re-check inside the monitor before sleeping: a PONG arriving between
                            // the while condition and acquiring the lock would lose its notify and
                            // we'd sleep the full PING_PONG_TIMEOUT (which can trigger a spurious
                            // socket close). The remaining>0 guard also avoids wait(0)=indefinite
                            // wait and wait(<0)=IAE in the race window of computing the remaining
                            // time.
                            long remaining = end - System.currentTimeMillis();
                            if ((pong == null || pong2 == null) && remaining > 0) {
                                try {
                                    ping_pong_lock.wait(remaining);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                        if (latency == -1 && pong != null && pong == ping + 1) {
                            latency = Math.round((System.nanoTime() - pingStartNs) / 1_000_000);
                        }
                        if (latency2 == -1 && pong2 != null && pong2 == ping + 2) {
                            latency2 = Math.round((System.nanoTime() - pingStartNs) / 1_000_000);
                        }
                    }

                    if (latency == -1) {
                        pong_timeout_counter++;
                    } else {
                        pong_timeout_counter = 0;
                    }
                    if (latency2 == -1) {
                        pong2_timeout_counter++;
                    } else {
                        pong2_timeout_counter = 0;
                    }

                    // Safety net for "mute" sockets (peer killed without RST, one-way partition,
                    // infinite GC stall). The primary path is still the IOException in
                    // writeCommandFromServer, but if the peer only ever receives without anyone
                    // writing anything besides PING, that PING write DOES throw IOException...
                    // unless the OS keeps buffering the send unacked with no error. In that edge
                    // case, this threshold (N=3 consecutive lost PONGs) closes the socket on our
                    // own initiative and lets runSocketReaderThread take the normal grace path.
                    //
                    // Anti-race guard: mid-resetSocket/forceSocketReconnect the counters may still
                    // be accumulated against the old socket. Closing now would wrongly close the
                    // newly installed socket instead.
                    if (!exit && !resetting_socket && !force_reset_socket
                            && (pong_timeout_counter >= WaitingRoomFrame.MAX_CONSECUTIVE_PING_FAILURES
                            || pong2_timeout_counter >= WaitingRoomFrame.MAX_CONSECUTIVE_PING_FAILURES)) {
                        LOGGER.log(Level.WARNING,
                                "PEER: Participant {0} lost {1}/{2} consecutive PONGs — closing socket",
                                new Object[]{nick, pong_timeout_counter, pong2_timeout_counter});
                        // alive=false BEFORE closing: so resetSocket's resurrection check sees the
                        // thread dead and relaunches it. Without this, in the break->finally
                        // window the check would see alive=true and skip the resurrection. The
                        // finally block sets it false again (idempotent).
                        // This close is deliberately NOT marked as our own stall close, unlike the
                        // stalled-write one above, even though that looks inconsistent: marking it
                        // was tried and reverted, because the window it opens interacts badly with
                        // how the Crupier's progress deadlines freeze and resume. Don't change
                        // this without understanding why.
                        ping_pong_thread_alive = false;
                        socketClose();
                        break;
                    }

                    if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().isPartida_empezada() && GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                        RemotePlayer jugador = (RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player().get(nick);
                        if (jugador != null) {
                            if (latency != -1 && latency2 != -1) {
                                jugador.updateLatency(Translator.translate("conn.latencia_format", String.valueOf(latency), String.valueOf(latency2)), false);
                            } else {
                                jugador.updateLatency(Translator.translate("conn.latencia_format", (latency != -1 ? String.valueOf(latency) : "-"), (latency2 != -1 ? String.valueOf(latency2) : "-")), true);
                            }
                        }
                    }

                    if (WaitingRoomFrame.getInstance() != null && !isCpu() && (!WaitingRoomFrame.getInstance().isPartida_empezada() || WaitingRoomFrame.getInstance().isVisible())) {
                        WaitingRoomFrame.getInstance().updateParticipantLatency(nick, latency, latency2);
                    }

                    if (!exit && WaitingRoomFrame.getInstance() != null) {
                        Helpers.pausar(PING_INTERVAL_MS);
                    }
                }
            } finally {
                ping_pong_thread_alive = false;
            }
        });
    }

    private void runSocketReaderThread() {
        Helpers.threadRun(() -> {
            while (!exit) {
                String mensaje_recibido = null;
                try {
                    mensaje_recibido = readCommandFromClient();
                } catch (Exception ex) {
                }

                if (mensaje_recibido != null) {
                    if (timeout) {
                        timeout = false;
                        setPlayerTimeoutSafe(false);
                    }
                    String[] partes_comando = mensaje_recibido.split("#");
                    if (null == partes_comando[0]) {
                        try {
                            encolarLeido(mensaje_recibido);
                        } catch (Exception ex) {
                        }
                    } else {
                        switch (partes_comando[0]) {
                            // Malformed control frames (PING/PONG/PONG2 without the numeric
                            // counter) must NOT bring down this reader thread: an
                            // Integer.parseInt on a corrupt frame used to throw
                            // NumberFormatException/AIOOBE that killed the reader and left the
                            // peer ZOMBIE — without the markExitAndNotify or the TIMEOUT
                            // broadcast of the normal disconnect path, so the rest of the
                            // table kept waiting on it. An honest peer (same version) always
                            // sends the counter; ignoring the corrupt frame is strictly safer
                            // than tearing down the connection half-way.
                            case "PING":
                                if (partes_comando.length >= 2) {
                                    try {
                                        writeCommandFromServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                    } catch (NumberFormatException nfe) {
                                    }
                                }
                                try {
                                    encolarLeido(mensaje_recibido);
                                } catch (Exception ex) {
                                }
                                break;
                            case "PONG":
                                if (partes_comando.length >= 2) {
                                    try {
                                        pong = Integer.valueOf(partes_comando[1]);
                                    } catch (NumberFormatException nfe) {
                                    }
                                    synchronized (ping_pong_lock) {
                                        ping_pong_lock.notifyAll();
                                    }
                                }
                                break;
                            case "PONG2":
                                if (partes_comando.length >= 2) {
                                    try {
                                        pong2 = Integer.valueOf(partes_comando[1]);
                                    } catch (NumberFormatException nfe) {
                                    }
                                    synchronized (ping_pong_lock) {
                                        ping_pong_lock.notifyAll();
                                    }
                                }
                                break;
                            default:
                                try {
                                    encolarLeido(mensaje_recibido);
                                } catch (Exception ex) {
                                }
                                break;
                        }
                    }
                } else {
                    try {
                        if (!socket_reader_queue.contains(POISON_PILL)) {
                            encolarSenalCierre();
                        }
                    } catch (Exception ex) {
                    }
                }

                // One-shot reset_socket: if this null is the OLD socket closing because of a
                // reconnect (resetSocket set reset_socket=true under lock), consume it HERE —
                // in the reader, which is what reacts to the signal — and skip the drop
                // handling. This used to be cleared by run() (a different, unlocked thread):
                // if it cleared between resetSocket's set and this check, the signal was lost
                // and an ALREADY reconnected peer got expelled after waiting out the full
                // grace. The grace wait below (under lock) remains the safety net if the
                // reconnect lands DURING the wait.
                if (mensaje_recibido == null && reset_socket) {
                    reset_socket = false;
                } else if (mensaje_recibido == null && !exit && !WaitingRoomFrame.getInstance().isExit()
                        && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null
                        || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                    // This close was done by US over a stalled write, so it's not that this
                    // peer's window ran out — it's that we're opening it right now. Without
                    // this it would be kicked on the spot: the "window open" marker survives a
                    // reconnect (only reading a frame clears it, and a peer that isn't reading
                    // isn't reading), so a peer that already had it set would fall straight
                    // into the else branch below and get kicked by the very close meant to
                    // give it a chance.
                    if (isStallClose()) {
                        timeout = false;
                    }

                    if (!timeout) {
                        // Marking the drop happens under the SAME lock that resetSocket uses
                        // to install the new socket (it clears reset_socket and timeout and
                        // calls setPlayerTimeoutSafe(false), all under this lock). Without
                        // this, the reader could read reset_socket=false above, have a full
                        // resetSocket run in between, and then mark the drop (magenta border +
                        // error sound) of a peer that had already returned. Checking and
                        // marking together under the lock makes it atomic against the
                        // reconnect: if it already landed, nothing gets marked.
                        boolean reconecto;
                        synchronized (getParticipant_socket_lock()) {
                            reconecto = reset_socket;
                            if (reconecto) {
                                reset_socket = false;
                            } else {
                                timeout = true;
                                setPlayerTimeoutSafe(true);
                                // The reader has taken over now: from here on it's `timeout`
                                // protecting the grace window, so the stall-close marker is no
                                // longer needed and shouldn't stay set, masking a legitimate
                                // drop later on.
                                stall_close_ns = NO_STALL_CLOSE;
                            }
                        }

                        if (!reconecto) {
                            long graceMs = (resetting_socket || force_reset_socket) ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT;
                            LOGGER.log(Level.INFO, "PEER: Participant {0} entered TIMEOUT state — waiting {1}ms for reconnect", new Object[]{nick, graceMs});

                            // The broadcast happens OUTSIDE the lock: broadcasting takes other
                            // peers' socket locks, and doing it inside would deadlock two
                            // simultaneous drops (each reader waiting on the other's lock).
                            // Re-checking reset_socket narrows the remaining window: if the
                            // reconnect lands right here, we don't broadcast a drop that's
                            // already resolved (and the heartbeat would heal it anyway).
                            if (!this.force_reset_socket && !this.reset_socket) {
                                try {
                                    GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                                } catch (Exception ex) {
                                }
                            }

                            // Wait with a rearmable deadline: signalReconnectIntent() can raise
                            // grace_deadline_floor during this wait and the loop picks up the
                            // extension on its next iteration. So a peer on a slow network
                            // that takes longer than the base grace to finish the handshake
                            // isn't expelled as long as it keeps cryptographically proving its
                            // identity.
                            if (!reset_socket) {
                                long deadline = System.currentTimeMillis() + graceMs;
                                synchronized (getParticipant_socket_lock()) {
                                    while (!reset_socket && !exit
                                            && !WaitingRoomFrame.getInstance().isExit()
                                            && System.currentTimeMillis() < deadline) {
                                        if (grace_deadline_floor > deadline) {
                                            LOGGER.log(Level.INFO,
                                                    "PEER: Participant {0} grace extended by authenticated reconnect intent (+{1}ms)",
                                                    new Object[]{nick, grace_deadline_floor - deadline});
                                            deadline = grace_deadline_floor;
                                        }
                                        long remaining = deadline - System.currentTimeMillis();
                                        if (remaining <= 0) {
                                            break;
                                        }
                                        try {
                                            getParticipant_socket_lock().wait(remaining);
                                        } catch (Exception ex) {
                                        }
                                    }
                                    // If we exit the grace because the reconnect ARRIVED
                                    // (reset_socket=true), consume the signal here: it already
                                    // did its job (getting us out of the wait). Left
                                    // unconsumed, it would persist and the one-shot check above
                                    // would eat the first null of the NEXT real drop as if it
                                    // were the old socket closing — delaying detection of that
                                    // drop by one reader iteration. resetSocket no longer
                                    // depends on reset_socket (it uses its own local 'ok'), so
                                    // clearing it here doesn't affect its result.
                                    if (reset_socket) {
                                        reset_socket = false;
                                    }
                                }
                            }
                        }
                    } else {
                        markExitAndNotify("TIMEOUT expired without reconnect");
                    }
                }
            } // END WHILE
        });
    }

    private void runPreGameSocketWriterQueueThread() {
        Helpers.threadRun(() -> {
            while (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                while (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada() && !getPre_game_socket_writer_queue().isEmpty()) {
                    String command = getPre_game_socket_writer_queue().peek();
                    ArrayList<String> pendientes = new ArrayList<>();
                    pendientes.add(getNick());

                    // The id is kept across retries so the client can dedupe by (subcommand, id) if a
                    // retransmission arrives after it already processed the first copy.
                    int id = Helpers.CSPRNG_GENERATOR.nextInt();
                    String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                    do {
                        if (!writeCommandFromServer(Helpers.encryptCommand(full_command, getAes_key(), getHmac_key()))) {
                            waitPreGameCommandConfirmations(id, pendientes);
                        } else {
                            // The write failed, typically because the socket is closed while
                            // the peer is getting its window to return. The loop's only wait
                            // is for confirmations, and that's skipped exactly when the write
                            // fails, so without this pause it would busy-retry, re-encrypting
                            // every lap, for the whole 45-second window — a core pegged at
                            // 100% making no progress. With it, retries happen at a sane rate:
                            // when the peer returns, the command is still pending and gets
                            // delivered.
                            Helpers.pausar(PRE_GAME_WRITE_RETRY_MS);
                        }
                    } while (!pendientes.isEmpty() && !exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada());

                    getPre_game_socket_writer_queue().poll();
                }

                synchronized (WaitingRoomFrame.getInstance().getLock_client_pre_game_commands_wait()) {
                    WaitingRoomFrame.getInstance().getLock_client_pre_game_commands_wait().notifyAll();
                }

                if (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    synchronized (getPre_game_socket_writer_queue()) {
                        try {
                            getPre_game_socket_writer_queue().wait(ASYNC_COMMAND_QUEUE_WAIT);
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        });
    }

    public boolean isUnsecure_player() {
        return unsecure_player;
    }

    /**
     * Marks this peer as running a modified/unsecure client build. Setting it
     * true for the first time pops a warning in the room and flags the player
     * as a cheater.
     *
     * @param val whether the peer's game binary is untrusted
     */
    public void setUnsecure_player(boolean val) {
        if (!this.unsecure_player && val) {
            Helpers.threadRun(() -> {
                Helpers.mostrarMensajeInformativo(WaitingRoomFrame.getInstance(), "[" + nick + "] " + Translator.translate("radar.cuidado_el_ejecutable_del_juego"), new ImageIcon(Init.class.getResource("/images/shield.png")));
            });

            if (WaitingRoomFrame.getInstance() != null) {
                WaitingRoomFrame.getInstance().markPlayerAsCheater(nick);
            }
        }
        this.unsecure_player = val;
    }

    public Object getParticipant_socket_lock() {
        return participant_socket_lock;
    }

    public ConcurrentLinkedQueue<String> getPre_game_socket_writer_queue() {
        return pre_game_socket_writer_queue;
    }

    public SecretKeySpec getHmac_key_orig() {
        return hmac_key_orig;
    }

    // The wait((resetting_socket||force_reset_socket)&&!exit) loop below (repeated by every
    // socket-state accessor in this class) blocks callers while a socket swap is in flight
    // (resetSocket / forceSocketReconnect), so nobody ever observes a mid-swap socket, stream
    // or key. resetSocket's finally always calls notifyAll(), so this normally wakes promptly;
    // the 1s timeout is just a defensive fallback.
    public SecretKeySpec getHmac_key() {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        return hmac_key;
    }

    public SecretKeySpec getAes_key() {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        return aes_key;
    }

    public BufferedInputStream getInput_stream_reader() {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        return input_stream_reader;
    }

    public boolean isCpu() {
        return cpu;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    /**
     * Marks this Participant exited and, if connected, tells the peer (pre-game
     * only, via a plain EXIT command) and closes its socket.
     */
    public void exitAndCloseSocket() {
        this.exit = true;
        if (this.socket != null) {
            if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {
                this.writeCommandFromServer(Helpers.encryptCommand("EXIT", this.getAes_key(), this.getHmac_key()));
            }
            this.socketClose();
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
        }
    }

    public boolean isExit() {
        return exit;
    }

    public File getAvatar() {
        return avatar;
    }

    public String getNick() {
        return nick;
    }

    long getRebuySourceId() {
        return rebuy_source_id;
    }

    /**
     * Called only by this Participant's socket-reader/consumer thread.
     */
    long nextRebuyInboundSequence() {
        return ++rebuy_inbound_sequence;
    }

    /**
     * Called only by this Participant's socket-reader/consumer thread.
     */
    long nextPauseInboundSequence() {
        return ++pause_inbound_sequence;
    }

    /**
     * Queues what was read from the socket, respecting the queue cap.
     *
     * <p>
     * Drops nothing while the peer is still in the game: retries every second
     * while the queue is full, letting TCP backpressure do the rest (we stop
     * reading the socket, its window closes, the sender backs off). A plain
     * {@code put} would do the same but silently and without an exit: once the
     * peer is given up on, it would wait for room forever in a queue nobody
     * will drain. The only message actually lost is one from a peer already
     * given up on.
     *
     * <p>
     * Not suitable for the close signal — use {@link #encolarSenalCierre()} for
     * that.
     */
    private void encolarLeido(String mensaje) {
        try {
            while (!exit) {
                if (socket_reader_queue.offer(mensaje, 1, java.util.concurrent.TimeUnit.SECONDS)) {
                    return;
                }
                LOGGER.log(Level.WARNING,
                        "Socket reader queue for {0} is full ({1}) — waiting for the consumer",
                        new Object[]{nick, SOCKET_READER_QUEUE_CAPACITY});
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Delivers the room's new password to THIS peer, discarding late arrivals.
     *
     * <p>
     * The lock is per-peer, not room-wide. Writing to a peer can stall for a
     * while (while it's reconnecting, or stuck behind a voice note holding the
     * socket's write turn), so a global lock would leave EVERYONE ELSE without
     * the new password for that whole stretch, and anyone who dropped in that
     * window couldn't rejoin. The caller also spawns one thread per peer, which
     * is what actually stops a stuck peer from blocking the others — the lock
     * alone wasn't enough, since the old loop went one peer at a time.
     *
     * <p>
     * Ordering is guaranteed where it matters — per socket: the version number
     * is recorded INSIDE the lock, so it doesn't matter which caller gets there
     * first. If the new change enters first, the old one finds the number
     * already bumped and doesn't write; if the old one enters first, the new
     * one writes right after and leaves the correct password in place. Checking
     * the version outside the write wasn't safe: the long wait is INSIDE the
     * lock, so two threads could both pass the check, both stall there, and
     * exit in either order.
     *
     * @param version change number, to discard late arrivals
     * @param payload the base64 password, or the sentinel meaning "no password
     * anymore"
     */
    public void writeRoomPassword(long version, String payload) {
        synchronized (password_write_lock) {
            if (version <= last_password_version) {
                return;
            }

            // The version is recorded ALWAYS, whether the write succeeded or not, and that's
            // correct even though it looks backwards. Recording it only on success was tried,
            // to avoid marking a password "delivered" that never arrived, but that reopened
            // the exact hole this method exists to close: if the new write fails, the number
            // doesn't advance, and an older delivery arriving later finds the door open and
            // overwrites it with the OLD password. Nothing was gained either way, since nobody
            // retries a failed send: the next change will carry a higher number and write
            // regardless.
            last_password_version = version;

            try {
                if (writeCommandFromServer(Helpers.encryptCommand(
                        "NEWPASS#" + payload, getAes_key(), getHmac_key()))) {
                    LOGGER.log(Level.WARNING,
                            "The new room password did not reach {0}: it still has the old one", nick);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not send the new room password to " + nick, ex);
            }
        }
    }

    /**
     * Whether we just closed the socket ourselves over a stalled write, so the
     * error that causes in any writes that were stuck doesn't count as the peer
     * having left. See {@link #stall_close_ns}.
     *
     * @return true if within the stall-close grace window
     */
    private boolean isStallClose() {
        long cerrado = stall_close_ns;

        return cerrado != NO_STALL_CLOSE && System.nanoTime() - cerrado < STALL_CLOSE_GRACE_NS;
    }

    /**
     * Queues the close signal no matter what.
     *
     * <p>
     * It's the only thing that gets the consumer out of its {@code take()}, and
     * from there flow the descriptor close and the death of its own thread.
     * That's why {@code exit} isn't checked here: when another thread orders
     * the exit (an expulsion, an expired deadline, a failed write),
     * {@code exit} is already true by the time this runs, and waiting for it to
     * go false would leave the signal unqueued and the consumer asleep forever,
     * with its thread and socket hanging for the rest of the game.
     *
     * <p>
     * That teardown is also where the rest of the table learns this player
     * left, which is the most important part today: without the signal, the
     * consumer never wakes, {@code remotePlayerQuit} is never called, and
     * nobody broadcasts anything, leaving the table waiting on the turn of
     * someone who's already gone.
     *
     * <p>
     * If the queue were full, room is made by dropping the oldest entry: those
     * are commands from a peer that's already gone, and none of them matter
     * more than the signal itself.
     */
    private void encolarSenalCierre() {
        for (int intentos = 0; intentos < SOCKET_READER_QUEUE_CAPACITY && !socket_reader_queue.offer(POISON_PILL); intentos++) {
            socket_reader_queue.poll();
        }
    }

    /**
     * Writes a text command to this peer's socket.
     *
     * @param command the plaintext (or already-encrypted) command line to send
     * @return {@code true} if the write failed (peer likely dropped);
     * {@code false} on success — note the inverted sense, callers read it as
     * "did this fail?"
     */
    public boolean writeCommandFromServer(String command) {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        try {
            synchronized (this.socket.getOutputStream()) {
                this.socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                this.socket.getOutputStream().flush();
                return false;
            }
        } catch (IOException ex) {
            // Closed socket / dropped peer detected on write. markExitAndNotify marks
            // Participant.exit AND Player.exit, and wakes the Crupier's waits on
            // received_commands. Without propagating to Player.exit, the do-while waiting on
            // a decision in Crupier hangs forever, since it checks jugador.isExit() on Player,
            // not Participant.
            //
            // A peer in its GRACE PERIOD (timeout) isn't given up on here: the reader is
            // already handling its drop and waiting for it to return, and it's the reader's
            // call when the deadline expires. Without this check, any write to the already-
            // closed socket would cut the grace short and the peer would lose its reconnect
            // window.
            //
            // Same story with stall_close, an instant EARLIER: we just closed this socket
            // ourselves so the reader can open that grace period, and this exception is a
            // consequence of that close, not of the peer actually leaving. The reader hasn't
            // had a chance to raise `timeout` yet, so without this check we'd win the race and
            // give up on the peer right when we meant to grant its window.
            // Order matters: stall_close is checked BEFORE timeout because the reader writes
            // them in the opposite order (raises timeout, then clears the marker). Checking in
            // this order, either we see the marker still set, or if it's already clear the
            // reader has already been through there and timeout is already true. Checking the
            // other way round could catch both in the single moment when neither protects.
            if (!exit && !isStallClose() && !timeout && !resetting_socket && !force_reset_socket) {
                markExitAndNotify("write failed (socket closed)");
            }
        }
        return true;
    }

    /**
     * Binary sibling of {@link #writeCommandFromServer(String)}: writes a
     * binary {@link WireFrame} (a voice/avatar blob) to this peer. Synchronizes
     * on the same OutputStream monitor as the text writers, so a binary frame
     * and a text line can never interleave on the socket.
     *
     * @param frameBody the raw binary payload to send
     * @return {@code true} if the write failed; {@code false} on success (same
     * inverted convention as {@link #writeCommandFromServer(String)})
     */
    public boolean writeBinaryFromServer(byte[] frameBody) {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        try {
            synchronized (this.socket.getOutputStream()) {
                WireFrame.writeBinary(this.socket.getOutputStream(), frameBody);
                return false;
            }
        } catch (IOException ex) {
            // Same criteria as the text twin, stall_close included, and it matters even more
            // here: this is the one that actually tends to stall. A voice note is hundreds of
            // kilobytes and holds the socket's write turn while the peer doesn't read, which
            // is exactly what exhausts the heartbeat deadline. If this catch block didn't
            // check our own close marker, the very close meant to open the peer's reconnect
            // window would end up kicking it through this door instead.
            if (!exit && !isStallClose() && !timeout && !resetting_socket && !force_reset_socket) {
                markExitAndNotify("binary write failed (socket closed)");
            }
        }
        return true;
    }

    // Sets/clears the visual TIMEOUT indicator for the player with this nick, tolerating that
    // GameFrame/Crupier/Player may not exist yet (a PRE-game drop, or a nick already removed
    // from the map). The reader used to call
    // GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(...) directly:
    // a non-clean drop in the waiting room (GameFrame==null) or an already-removed nick threw
    // an NPE that KILLED the reader thread, leaving the peer a zombie (no grace, no TIMEOUT
    // broadcast, the rest of the table left waiting on it).
    private void setPlayerTimeoutSafe(boolean value) {
        try {
            GameFrame gf = GameFrame.getInstance();
            if (gf != null && gf.getCrupier() != null && gf.getCrupier().getNick2player() != null) {
                var p = gf.getCrupier().getNick2player().get(nick);
                if (p != null) {
                    p.setTimeout(value);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "setPlayerTimeoutSafe failed for {0}", nick);
        }
    }

    /**
     * Marks this Participant exit=true AND propagates it to the associated
     * Player (RemotePlayer.setExit), notifies every relevant wait, and wakes
     * the Crupier's command queue so any wait on
     * DECISION/ACTION/RESP_SRA_UNLOCK from the dropped peer returns
     * immediately.
     *
     * <p>
     * Without this, marking only Participant.exit left the Crupier's loop
     * (which checks Player.isExit, not Participant.isExit) hanging
     * indefinitely, with the wait on received_commands never notified.
     *
     * @param reason short description of why this peer is being marked exited,
     * for logs
     */
    public void markExitAndNotify(String reason) {
        if (exit) {
            return;
        }
        exit = true;
        LOGGER.log(Level.WARNING, "PEER: Participant {0} marked exit — {1}", new Object[]{nick, reason});
        try {
            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                Crupier c = GameFrame.getInstance().getCrupier();
                Player p = c.getNick2player() != null ? c.getNick2player().get(nick) : null;
                if (p != null && !p.isExit()) {
                    p.setExit();
                }
                synchronized (c.getReceived_commands()) {
                    c.getReceived_commands().notifyAll();
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "markExitAndNotify failed to propagate to Player/Crupier", ex);
        }
        try {
            synchronized (getParticipant_socket_lock()) {
                getParticipant_socket_lock().notifyAll();
            }
        } catch (Exception ignored) {
        }
        try {
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
        } catch (Exception ignored) {
        }
        // And the consumer is woken up, since it's the only thing none of the notifications
        // above reach: it waits on its queue and only the close signal gets it out. Without
        // this it would sleep forever, and with it goes the descriptor close and the death of
        // its own thread.
        //
        // The case that makes this essential is an expulsion that does NOT close the socket
        // (the zero-trust cascade violation): there the reader is still parked in its read,
        // the next incoming message returns it to the loop, finds itself already marked, and
        // dies without queueing anything, leaving the consumer waiting. On paths that do close
        // the socket, the reader wakes up with a null and queues the signal itself.
        try {
            encolarSenalCierre();
        } catch (Exception ignored) {
        }
    }

    /**
     * Blocks reading the next decrypted text command from this peer's socket,
     * handling binary frames inline and dropping frames that fail
     * authentication.
     *
     * @return the decrypted command line, or {@code null} on EOF/read failure
     */
    public String readCommandFromClient() {
        while ((resetting_socket || force_reset_socket) && !exit) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        synchronized (getInput_stream_reader()) {
            try {
                while (true) {
                    WireFrame.Result frame = WireFrame.read(getInput_stream_reader(), Helpers.MAX_COMMAND_LINE_CHARS);
                    if (frame == null) {
                        return null;
                    }
                    if (frame.isBinary()) {
                        // Binary voice/avatar frame: handle inline and read the next one.
                        // Voice is an order-independent side channel, so processing it on
                        // the reader thread (rather than via the text command queue) is
                        // safe, and recibirNotaVoz offloads its heavy work asynchronously.
                        handleBinaryFromClient(frame.binary());
                        continue;
                    }
                    // Text frame body is the exact line readBoundedLine returned, so
                    // decryptCommand is unchanged.
                    try {
                        return Helpers.decryptCommand(frame.text(), getAes_key(), getHmac_key());
                    } catch (java.security.KeyException ke) {
                        // A frame that fails the channel check (bad/tampered HMAC, or
                        // unencrypted without being a keepalive) is DISCARDED and reading
                        // continues, as documented in SECURITY.md ("the receiver drops the
                        // frame"). Letting it fall through to the return null below would turn
                        // it into an EOF and tear down the whole connection, so a single
                        // injected byte would be enough to kick a player from the table.
                        LOGGER.log(Level.WARNING, "PEER: dropping unauthenticated frame from {0} ({1})",
                                new Object[]{nick, ke.getMessage()});
                        continue;
                    }
                }
            } catch (Exception ex) {
            }
        }
        return null;
    }

    /**
     * Decrypts and dispatches a binary frame received from this peer. A voice
     * note is attributed to the connection's AUTHENTICATED nick (never the
     * frame's claimed nick), preserving the anti-spoof guarantee of the legacy
     * VOICEMSG text path. A malformed or HMAC-failing frame is dropped without
     * tearing down the reader.
     */
    private void handleBinaryFromClient(byte[] frameBody) {
        // F2 ANTI-DoS: rate-limit the binary channel BEFORE decrypting/processing. Cuts off a
        // flood of binary frames (voice/stats) that bypasses run()'s text bucket, each of
        // which triggers heavy async work. Over the limit -> silent DROP (no strike/expulsion;
        // see the BINARY_INBOUND_* fields).
        if (binaryInboundAbuse()) {
            // Uniform §8 visibility: RED log entry + popup (deduped per game in
            // warnMaliciousPeer, so a sustained flood only produces ONE, not one per frame).
            // Only when a game is in progress.
            try {
                if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                    GameFrame.getInstance().getCrupier().warnMaliciousPeer(this.nick, "zero_trust.peer_binary_flood");
                }
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            byte[] payload = Helpers.decryptBytes(frameBody, getAes_key(), getHmac_key());
            if (payload == null) {
                return;
            }
            BinaryWire.Decoded decoded = BinaryWire.decode(payload);
            if (decoded.type == BinaryWire.TYPE_VOICE) {
                sala_espera.recibirNotaVoz(nick, decoded.payload);
            } else if (decoded.type == BinaryWire.TYPE_DB) {
                // Stats DB sync from this client. Attribute to the connection's
                // AUTHENTICATED nick (never the frame's claimed nick), same
                // anti-spoof rule as a voice note.
                sala_espera.statsSyncOnMessage(nick, decoded.payload, true);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Dropped malformed binary frame from peer {0}", nick);
        }
    }

    /**
     * Closes this peer's socket, if open. Idempotent.
     */
    public void socketClose() {
        synchronized (getParticipant_socket_lock()) {
            if (this.socket != null && !this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    /**
     * Signals that a cryptographically authenticated reconnect attempt has
     * arrived (the nick's HMAC verified against hmac_key_orig). Raises
     * grace_deadline_floor to now()+CLIENT_RECON_TIMEOUT (monotonic) and wakes
     * the reader's wait so it rearms its deadline to the new floor.
     *
     * <p>
     * Must only be called from the server socket handler after identity is
     * verified: never on an invalid HMAC, never on IP match alone. This lets a
     * dropped peer that still holds its original session key extend the grace
     * as many times as it needs while retrying the handshake, without an
     * outside attacker being able to do the same.
     */
    public void signalReconnectIntent() {
        long candidate = System.currentTimeMillis() + GameFrame.CLIENT_RECON_TIMEOUT;
        synchronized (getParticipant_socket_lock()) {
            if (candidate > grace_deadline_floor) {
                grace_deadline_floor = candidate;
            }
            getParticipant_socket_lock().notifyAll();
        }
    }

    /**
     * Synchronized on participant_socket_lock: closes the current socket (and a
     * pending recon_socket, if any) and sets force_reset_socket. Used to mutate
     * socket/recon_socket/force_reset_socket WITHOUT a lock, racing with
     * resetSocket and with the "Force reconnect" menu (which calls it in
     * parallel across all peers). Re-entrant: resetSocket invokes it while
     * already holding the lock.
     */
    public void forceSocketReconnect() {
        synchronized (getParticipant_socket_lock()) {
            if (this.recon_socket != null) {
                try {
                    this.recon_socket.close();
                } catch (Exception ex) {
                }
            }
            if (this.socket != null) {
                try {
                    this.socket.close();
                } catch (Exception ex) {
                }
            }
            force_reset_socket = true;
        }
    }

    /**
     * Called by the host's "Force reconnect" menu (GameFrame). Closes the
     * socket to force the peer to reconnect AND starts a watchdog: if the peer
     * does NOT return within the grace (CLIENT_RECON_TIMEOUT), it clears
     * force_reset_socket and gives up on the peer. Without this, a forced peer
     * that never reconnects left readCommandFromClient (the reader thread
     * itself), writers, and getters BLOCKED FOREVER in
     * while((resetting_socket||force_reset_socket)&&!exit): force_reset_socket
     * is only cleared by a successful resetSocket (which never happens here),
     * and the reader, blocked in that wait, never reaches markExit -> exit was
     * never set. The watchdog is a daemon thread: it doesn't hold up JVM
     * shutdown even while sleeping through the grace.
     */
    public void forceSocketReconnectWithWatchdog() {
        final int myGen;
        synchronized (getParticipant_socket_lock()) {
            forceSocketReconnect();
            myGen = ++force_reset_generation;
        }
        Thread wd = new Thread(() -> {
            boolean giveUp = false;
            // Rearmable wait that honors grace_deadline_floor EXACTLY like the reader's grace
            // wait (runSocketReaderThread): sleeping a FIXED period used to expel a peer that,
            // reconnecting with a valid session identity, extended its grace via
            // signalReconnectIntent right at the edge of the deadline — the reader gave it
            // more time but this watchdog gave up on it anyway. Now both share the same
            // authenticated floor. grace_deadline_floor is monotonic, so a floor from an
            // earlier cycle never extends this one (the base now+CLIENT_RECON_TIMEOUT already
            // exceeds it): only a NEW reconnect attempt from this cycle can raise it.
            //
            // Decision made UNDER the lock: if a reconnect is in progress, resetSocket holds
            // the lock and we wait for it to finish; if it succeeded, force_reset_socket is
            // already false -> we don't give up on the peer (closes the boundary race with
            // resetSocket). force_reset_generation==myGen stops this watchdog from acting on a
            // NEWER force call (a double-click on the menu within the grace, already
            // reconnecting).
            synchronized (getParticipant_socket_lock()) {
                long deadline = System.currentTimeMillis() + GameFrame.CLIENT_RECON_TIMEOUT;
                while (force_reset_socket && !reset_socket && !exit && force_reset_generation == myGen
                        && System.currentTimeMillis() < deadline) {
                    if (grace_deadline_floor > deadline) {
                        deadline = grace_deadline_floor;
                    }
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        getParticipant_socket_lock().wait(remaining);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // The peer is given up on ONLY if the deadline (with its authenticated
                // extensions) expired and it's still forced, not reconnected, not exited, and
                // in the same force generation. Same condition as always, now honoring the
                // grace floor.
                if (force_reset_socket && !reset_socket && !exit && force_reset_generation == myGen) {
                    giveUp = true;
                }
            }
            // markExitAndNotify OUTSIDE the lock (it acquires other monitors; avoid nesting),
            // only if the decision made under the lock said giveUp.
            if (giveUp) {
                LOGGER.log(Level.WARNING, "PEER: Participant {0} forced-reconnect watchdog: peer did not return within grace, giving up", nick);
                // markExit FIRST (sets exit=true and notifies) BEFORE clearing the flags. If
                // we cleared force_reset_socket/resetting_socket while exit was still false,
                // the reader — woken by that notifyAll — would see the flags false and exit
                // false and enter the TIMEOUT branch, emitting a spurious TIMEOUT broadcast
                // for a peer we've ALREADY given up on. With exit=true first, its else-if
                // (which requires !exit) doesn't trigger. The transport loops
                // while((resetting||force)&&!exit) exit just the same via exit thanks to
                // markExit's notifyAll; the clear below is just state hygiene (don't leave the
                // flags true on a dead peer) and needs no notifyAll of its own.
                markExitAndNotify("forced reconnect watchdog: no return within grace");
                synchronized (getParticipant_socket_lock()) {
                    force_reset_socket = false;
                    resetting_socket = false;
                }
            }
        });
        wd.setDaemon(true);
        wd.start();
    }

    private void setSocket(Socket socket) {
        synchronized (getParticipant_socket_lock()) {
            this.socket = socket;
            if (this.socket != null) {
                try {
                    this.input_stream_reader = new BufferedInputStream(this.socket.getInputStream());
                } catch (Exception ex) {
                }
            }
        }
    }

    /**
     * Installs a new socket/keys for this peer after a successful reconnect.
     * The ENTIRE swap happens under participant_socket_lock — the prologue used
     * to run OUTSIDE the lock (resetting_socket, forceSocketReconnect,
     * recon_socket): two concurrent reconnects for the SAME nick, or the "Force
     * reconnect" menu running in parallel, would interleave and leave
     * socket/stream/keys inconsistent (NPE on recon_socket==null, crossed
     * keys). Now it's atomic: the latest reconnect cleanly wins, without
     * corrupting the Participant's state.
     *
     * @param sock the peer's newly accepted socket
     * @param aes_k new session AES key
     * @param hmac_k new session HMAC key
     * @return true if the reconnect was installed successfully
     */
    public boolean resetSocket(Socket sock, SecretKeySpec aes_k, SecretKeySpec hmac_k) {
        synchronized (getParticipant_socket_lock()) {
            // TOCTOU: if the peer is already leaving (grace expired / markExitAndNotify
            // already called, but not yet removed from the participant map) we do NOT accept
            // the reconnect — the handler denies it and run() finishes removing it. Without
            // this, the new socket would get installed + RECONNECT_OK sent, and right after
            // run() would remove the player: the client believed it was in while the host had
            // already kicked it out (plus a leak).
            if (exit) {
                LOGGER.log(Level.WARNING, "PEER: Participant {0} resetSocket refused — peer is exiting", nick);
                return false;
            }
            this.resetting_socket = true;
            forceSocketReconnect();
            this.recon_socket = sock;
            // ok: LOCAL result of the reset, IMMUNE to the reader clearing this.reset_socket
            // (its one-shot read, WITHOUT a lock) in the window between us setting it true and
            // the return/resurrection here. Without this, that concurrent clear made
            // resetSocket return false -> the handler sent RESET_FAIL and closed the NEW
            // socket of a reconnect that actually DID succeed (and skipped the resurrection).
            boolean ok = false;
            try {
                // Transactional swap: build the new stream BEFORE committing socket/keys. If
                // getInputStream() throws, this.socket/stream/keys stay as they were (the old
                // ones), not half-changed (new socket + old stream).
                BufferedInputStream nuevo_stream = new BufferedInputStream(this.recon_socket.getInputStream());
                this.socket = this.recon_socket;
                this.input_stream_reader = nuevo_stream;
                this.aes_key = aes_k;
                this.hmac_key = hmac_k;
                if (!isForce_reset_socket() && GameFrame.conexionSonidoOn()) {
                    Audio.playWavResource("misc/yahoo.wav");
                }
                this.reset_socket = true;
                ok = true;
                // Telemetry: per-peer count of successful reconnects. Only incremented once
                // we get here (reset_socket=true already guarantees the new socket is
                // installed and the streams are ready). The host's TELEMETRY broadcast
                // exposes this value to all clients to show link instability.
                this.reconnection_count++;
                // Defensive ping counter reset: if failures had accumulated against the old
                // socket, the first failure against the new one (which can be legit jitter
                // right after reconnecting) shouldn't reach the threshold or close the
                // freshly installed socket.
                this.pong_timeout_counter = 0;
                this.pong2_timeout_counter = 0;
                this.ping_write_stall_counter = 0;
                this.stall_close_ns = NO_STALL_CLOSE;
                // And the "window open" marker, which belongs to the old socket just like the
                // counters above. It used to stay set because only reading a frame clears it,
                // and the first one can take until the next heartbeat: if the peer's network
                // dropped again in that gap, the reader would find it already marked,
                // consider its window exhausted, and kick it on the spot, granting nothing.
                // Hitting exactly the peers with unstable links, which are the ones
                // reconnecting the most.
                this.timeout = false;
                setPlayerTimeoutSafe(false);
                LOGGER.log(Level.INFO, "PEER: Participant {0} resetSocket OK — reconnect succeeded within grace period (exit stays false)", nick);
            } catch (Exception ex) {
                this.reset_socket = false;
                LOGGER.log(Level.WARNING, "PEER: Participant " + nick + " resetSocket FAILED — reader thread will continue to timeout", ex);
            } finally {
                this.recon_socket = null;
                this.force_reset_socket = false;
                this.resetting_socket = false;
            }
            getParticipant_socket_lock().notifyAll();
            // If the ping watchdog died via socketClose+break (threshold exceeded against the
            // old socket), resurrect it after a successful reset: without this, the
            // reconnected peer loses active PING supervision (if the new socket goes mute,
            // nobody detects it until a write fails, which with an active grace might not
            // mark exit). reset_socket=true: don't relaunch if the reset failed; !exit: don't
            // resurrect already-expelled peers.
            if (ok && !this.exit && !this.ping_pong_thread_alive) {
                LOGGER.log(Level.INFO, "PEER: Participant {0} runPingPongThread was dead after reset — resurrecting", nick);
                runPingPongThread();
            }
            return ok;
        }
    }

    /**
     * Waits for this peer to confirm the pre-game command with the given id,
     * removing confirmed nicks from {@code pending} as they arrive.
     *
     * @param id command id (confirmations carry id+1, see the "CONF" case in
     * {@link #run()})
     * @param pending nicks still awaiting confirmation; mutated in place
     * @return {@code true} if the confirmation deadline expired with peers
     * still pending
     */
    public boolean waitPreGameCommandConfirmations(int id, ArrayList<String> pending) {
        long start_time = System.currentTimeMillis();
        boolean plazo_vencido = false;
        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!exit && !pending.isEmpty() && !plazo_vencido) {
            Object[] confirmation;
            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                while (!exit && !WaitingRoomFrame.getInstance().getReceived_confirmations().isEmpty()) {
                    confirmation = WaitingRoomFrame.getInstance().getReceived_confirmations().poll();
                    if (confirmation != null && confirmation[0] != null && confirmation[1] != null) {
                        if ((int) confirmation[1] == id + 1) {
                            pending.remove((String) confirmation[0]);
                        } else {
                            rejected.add(confirmation);
                        }
                    }
                }

                if (!exit) {
                    if (!rejected.isEmpty()) {
                        WaitingRoomFrame.getInstance().getReceived_confirmations().addAll(rejected);
                        rejected.clear();
                    }

                    if (System.currentTimeMillis() - start_time > GameFrame.CONFIRMATION_TIMEOUT) {
                        plazo_vencido = true;
                    } else if (!pending.isEmpty()) {
                        try {
                            WaitingRoomFrame.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        }
        return !pending.isEmpty();
    }

    // F2 ANTI-DoS: true if this frame exceeds the size cap or the tolerated frequency (token-bucket).
    // Thresholds are huge: in normal play this never returns true. Single thread (the peer's reader) -> no lock.
    private boolean inboundAbuse(String frame) {
        if (frame.length() > MAX_INBOUND_COMMAND_CHARS) {
            return true;
        }
        long now = System.nanoTime();
        if (inbound_last_refill_ns == 0L) {
            inbound_last_refill_ns = now;
        }
        double elapsed = (now - inbound_last_refill_ns) / 1_000_000_000.0;
        inbound_last_refill_ns = now;
        inbound_tokens = Math.min(INBOUND_BURST, inbound_tokens + elapsed * INBOUND_REFILL_PER_SEC);
        if (inbound_tokens >= 1.0) {
            inbound_tokens -= 1.0;
            return false;
        }
        return true; // no tokens left -> over the rate limit
    }

    // F2 ANTI-DoS (binary): true if this binary frame exceeds the tolerated rate (its own
    // token-bucket, see the BINARY_INBOUND_* fields). Only called from handleBinaryFromClient
    // on the reader thread -> no lock.
    private boolean binaryInboundAbuse() {
        long now = System.nanoTime();
        if (binary_inbound_last_refill_ns == 0L) {
            binary_inbound_last_refill_ns = now;
        }
        double elapsed = (now - binary_inbound_last_refill_ns) / 1_000_000_000.0;
        binary_inbound_last_refill_ns = now;
        binary_inbound_tokens = Math.min(BINARY_INBOUND_BURST,
                binary_inbound_tokens + elapsed * BINARY_INBOUND_REFILL_PER_SEC);
        if (binary_inbound_tokens >= 1.0) {
            binary_inbound_tokens -= 1.0;
            return false;
        }
        return true; // no tokens left -> over the rate limit
    }

    // F2 ANTI-DoS: adds one strike to THIS peer; true once it reaches the expulsion threshold.
    private boolean registerAbuseStrike(String reason) {
        abuse_strikes++;
        if (abuse_strikes == 1 || abuse_strikes % 25 == 0) {
            LOGGER.log(Level.SEVERE, "ZERO-TRUST DoS: peer {0} abuse strike {1} ({2})",
                    new Object[]{this.nick, abuse_strikes, reason});
        }
        return abuse_strikes >= MAX_ABUSE_STRIKES;
    }

    // F2 ANTI-DoS: expels THIS peer without touching the others — marks exit + propagates to
    // Player.exit + wakes the Crupier's waits (markExitAndNotify) and CUTS the socket (to stop
    // the flood). The table continues: the rest see the EXIT through normal teardown
    // (remotePlayerQuit when the reader loop exits).
    private void autoExpel(String reason) {
        LOGGER.log(Level.SEVERE, "ZERO-TRUST DoS: AUTO-EXPEL peer {0} — {1}", new Object[]{this.nick, reason});
        markExitAndNotify("auto-expel: " + reason);
        try {
            socketClose();
        } catch (Exception ignored) {
        }
        // Uniform §8 visibility: RED log entry + popup naming the expelled player (deduped per
        // game in warnMaliciousPeer). Only when a game is in progress (the waiting room has no
        // Crupier/log).
        try {
            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                GameFrame.getInstance().getCrupier().warnMaliciousPeer(this.nick, "zero_trust.peer_expelled_flood");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run() {
        if (socket != null) {
            runPreGameSocketWriterQueueThread();
            runSocketReaderThread();
            runPingPongThread();

            String recibido;
            do {
                // reset_socket is no longer cleared here: the reader (which owns the signal)
                // consumes it on its null-read. Clearing it from THIS thread, without a lock,
                // used to lose the reconnect signal and expel already-reconnected peers. See
                // runSocketReaderThread (one-shot reset_socket).
                try {
                    recibido = socket_reader_queue.take();
                    if (!POISON_PILL.equals(recibido)) {
                        // F2 ANTI-DoS: size cap + per-peer rate limit BEFORE processing
                        // anything. Over the limit -> discard the frame (SILENT-REFUSE) +
                        // strike; accumulated strikes -> AUTO-EXPEL (kick THIS peer, the table
                        // continues). Thresholds are huge -> never affects an honest client.
                        if (inboundAbuse(recibido)) {
                            if (recibido.startsWith("GAME#")) {
                                LOGGER.log(Level.SEVERE,
                                        "Rate-limited critical GAME command from {0}; closing connection", nick);
                                game_command_gate.rejectForRateLimit(null);
                                exitAndCloseSocket();
                                break;
                            }
                            if (registerAbuseStrike("inbound flood/oversize")) {
                                autoExpel("inbound flood/oversize");
                                break;
                            }
                            continue;
                        }
                        String[] partes_comando = recibido.split("#");

                        switch (partes_comando[0]) {
                            case "PING":
                                writeCommandFromServer("PONG2#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 2));
                                break;
                            case "EXIT":
                                exit = true;
                                if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nick);
                                } else if (sala_espera != null) {
                                    sala_espera.borrarParticipante(this.nick);
                                }
                                break;
                            case "CHAT":
                                String mensaje;
                                if (partes_comando.length == 3) {
                                    mensaje = new String(Base64.getDecoder().decode(partes_comando[2]), "UTF-8");
                                } else {
                                    mensaje = "";
                                }
                                // Attribute to this peer's AUTHENTICATED nick, not the
                                // one carried in the frame: a rogue client must not be
                                // able to post chat as another player.
                                sala_espera.recibirMensajeChat(nick, mensaje);
                                break;
                            case "CONF":
                                WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{nick, Integer.valueOf(partes_comando[1])});
                                synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                                    WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                }
                                break;
                            case "GAME":
                                if (partes_comando.length < 3) {
                                    LOGGER.log(Level.SEVERE, "Malformed GAME frame from {0}; closing connection", nick);
                                    exitAndCloseSocket();
                                    break;
                                }
                                String subcomando = partes_comando[2];
                                final int command_id;
                                try {
                                    command_id = Integer.parseInt(partes_comando[1]);
                                } catch (NumberFormatException ex) {
                                    LOGGER.log(Level.SEVERE, "Invalid GAME id from " + nick + "; closing connection", ex);
                                    exitAndCloseSocket();
                                    break;
                                }
                                GameCommandGate.Decision gateDecision
                                        = game_command_gate.accept(subcomando, command_id);
                                if (gateDecision.closeConnection()) {
                                    LOGGER.log(Level.SEVERE,
                                            "Unknown GAME subcommand {0} from {1}; closing connection",
                                            new Object[]{subcomando, nick});
                                    exitAndCloseSocket();
                                    break;
                                }

                                // The CONF packet must be encrypted: the client always expects encrypted
                                // commands, and a plaintext CONF causes a decrypt failure and deadlocks its
                                // read loop.
                                try {
                                    String confMsg = "CONF#" + String.valueOf(command_id + 1) + "#OK";
                                    this.writeCommandFromServer(Helpers.encryptCommand(confMsg, this.aes_key, this.hmac_key));
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Failed to encrypt CONF message", e);
                                }

                                if (gateDecision.enqueue()) {
                                    switch (subcomando) {

                                        case "PAUSE":
                                            if (partes_comando.length < 4
                                                    || (!"0".equals(partes_comando[3]) && !"1".equals(partes_comando[3]))) {
                                                LOGGER.log(Level.WARNING, "Dropping malformed PAUSE from {0}", nick);
                                                break;
                                            }
                                            final String pause_value = partes_comando[3];
                                            final long pause_sequence = nextPauseInboundSequence();
                                            Helpers.threadRun(() -> {
                                                // The reader must stay free to consume CONF, but the
                                                // cached pool can execute pause/resume tasks backwards.
                                                // Serialize the sequence check with the state transition so
                                                // a late task cannot undo the newer toggle.
                                                synchronized (pause_sequence_lock) {
                                                    if (!Crupier.shouldApplyAsyncSequence(pause_sequence, pause_applied_sequence)) {
                                                        return;
                                                    }
                                                    synchronized (GameFrame.getInstance().getLock_pause()) {
                                                        pause_applied_sequence = pause_sequence;
                                                        if (("0".equals(pause_value) && GameFrame.getInstance().isTimba_pausada())
                                                                && nick.equals(GameFrame.getInstance().getNick_pause())
                                                                || ("1".equals(pause_value) && !GameFrame.getInstance().isTimba_pausada())) {
                                                            GameFrame.getInstance().pauseTimba(nick);
                                                            if (GameFrame.getInstance().isTimba_pausada()) {
                                                                GameFrame.getInstance().getRegistro().print("PAUSE (" + nick + ")");
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                            break;
                                        case "IWTSTH":
                                            if (GameFrame.getInstance().getCrupier().isShow_time() && !GameFrame.getInstance().getCrupier().isIwtsthing()) {
                                                GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(nick);
                                            }
                                            break;
                                        case "RABBIT_REQ": {
                                            try {
                                                if (partes_comando.length != 4) {
                                                    throw new IllegalArgumentException("wrong Rabbit request arity");
                                                }
                                                RabbitFeeLedger.Result<RabbitFeeLedger.Request> decoded
                                                        = RabbitFeeLedger.Request.decode(Base64.getDecoder().decode(partes_comando[3]));
                                                if (!decoded.isOk() || !nick.equals(decoded.value().playerId())) {
                                                    throw new IllegalArgumentException("invalid Rabbit request identity or wire");
                                                }
                                                GameFrame.getInstance().getCrupier().RABBIT_REQUEST_HANDLER(decoded.value());
                                            } catch (Exception ex) {
                                                LOGGER.log(Level.SEVERE, "Invalid critical Rabbit request from " + nick + "; closing connection", ex);
                                                exitAndCloseSocket();
                                            }
                                            break;
                                        }
                                        case "REBUYNOW":
                                            // On a separate thread, like PAUSE/SHOWCARDS: rebuyNow (host branch)
                                            // does a broadcastGAMECommandFromServer WITH confirmation, which
                                            // BLOCKS THIS reader thread waiting on clients' CONFs — and those
                                            // CONFs are read by this SAME thread (the "CONF" case above).
                                            // rebuyNow also holds lock_rebuynow for the whole broadcast. Inline,
                                            // two near-simultaneous rebuys would deadlock each other: X's reader,
                                            // inside X's broadcast holding the lock, waits on Y's CONF; Y's reader
                                            // is blocked on lock_rebuynow and never reads that CONF -> a deadlock
                                            // that hangs the table (same class of bug as the pause one). Taking it
                                            // off the reader thread closes this.
                                            if (partes_comando.length < 4) {
                                                LOGGER.log(Level.WARNING, "Dropping malformed REBUYNOW from {0}", nick);
                                                break;
                                            }
                                            try {
                                                final int rebuy_buyin = Integer.parseInt(partes_comando[3]);
                                                final long rebuy_sequence = nextRebuyInboundSequence();
                                                final long rebuy_source = getRebuySourceId();
                                                Helpers.threadRun(() -> GameFrame.getInstance().getCrupier()
                                                        .rebuyNowFromClient(nick, rebuy_buyin, rebuy_source, rebuy_sequence));
                                            } catch (NumberFormatException ex) {
                                                LOGGER.log(Level.WARNING, "Dropping malformed REBUYNOW amount from {0}", nick);
                                            }
                                            break;
                                        case "SHOWCARDS":
                                            Helpers.threadRun(() -> {
                                                try {
                                                    String shNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                    // ZERO-TRUST: a SHOWCARDS can only reveal the cards of the nick
                                                    // owning THIS authenticated connection (parity with
                                                    // HANDVERIFY/STRADDLE_RESP). Otherwise a peer could name another
                                                    // player and, via showPlayerCards, trigger the host's LOCKDOWN
                                                    // with a missing/invalid signature -> kill the table with a
                                                    // single message. Discarded.
                                                    if (!shNick.equals(this.nick)) {
                                                        LOGGER.log(Level.SEVERE,
                                                                "ZERO-TRUST: dropping SHOWCARDS with nick mismatch on connection {0} (claimed {1})",
                                                                new Object[]{this.nick, shNick});
                                                        return;
                                                    }
                                                    String sraKeyB64 = partes_comando[4];
                                                    // PHASE A.1: the Ed25519 signature travels alongside the SRA
                                                    // key. The host CANNOT tamper with it — it's the proof that it
                                                    // came from that nick's private key.
                                                    String sigB64 = (partes_comando.length >= 6) ? partes_comando[5] : null;

                                                    // 1. The server verifies the signature + decrypts locally. On
                                                    // the HOST, a SHOWCARDS from a peer with a missing/invalid
                                                    // signature does NOT trigger lockdown (SILENT-REFUSE): it
                                                    // returns false and is NOT relayed.
                                                    boolean revealed = GameFrame.getInstance().getCrupier().showPlayerCards(shNick, sraKeyB64, sigB64);

                                                    // 2. Mirror effect: only a VERIFIED SHOWCARDS is relayed to the
                                                    // rest, signature intact (receivers re-verify it). An
                                                    // unverified one is never relayed: clients would read that as a
                                                    // hostile host and lock down (an amplified kill).
                                                    if (revealed && GameFrame.getInstance().isPartida_local()) {
                                                        String rebroadcastCmd = "SHOWCARDS#" + partes_comando[3] + "#" + sraKeyB64 + "#" + sigB64;
                                                        GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer(rebroadcastCmd, shNick);
                                                    }
                                                } catch (Exception e) {
                                                    LOGGER.log(Level.SEVERE, "Error processing/forwarding SHOWCARDS on server", e);
                                                }
                                            });
                                            break;
                                        case "HAND_READY": // SRA LIGHTWEIGHT START COMMAND
                                            try {
                                                this.new_hand_ready = Integer.parseInt(partes_comando[3]);
                                            } catch (Exception e) {
                                            }

                                            synchronized (GameFrame.getInstance().getCrupier().getLock_nueva_mano()) {
                                                GameFrame.getInstance().getCrupier().getLock_nueva_mano().notifyAll();
                                            }
                                            break;
                                        case "EXIT":
                                            String exitingNick = this.nick;

                                            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                                int offset = 3;

                                                if (!GameFrame.getInstance().isPartida_local() && partes_comando.length >= 4) {
                                                    try {
                                                        exitingNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                    } catch (Exception e) {
                                                    }
                                                    offset = 4;
                                                }

                                                if (partes_comando.length > offset) {
                                                    Participant p = GameFrame.getInstance().getParticipantes().get(exitingNick);
                                                    if (p != null && !partes_comando[offset].equals("*")) {
                                                        try {
                                                            byte[] testament = Base64.getDecoder().decode(partes_comando[offset]);
                                                            // Dual-lock: the testament hands over ONLY the community
                                                            // half. The leaving peer's pocket half stays secret. A
                                                            // USABLE scalar is required, not just one of the right
                                                            // size: 32 zero bytes passed the size check and inverting
                                                            // them crashed the Crupier's thread, taking the whole
                                                            // process down with it.
                                                            if (RistrettoSRA.isValidScalar(testament)) {
                                                                p.setSra_unlock_community(testament);
                                                            }
                                                        } catch (Exception e) {
                                                        }
                                                    }
                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(exitingNick, partes_comando[offset]);
                                                } else {
                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(exitingNick);
                                                }
                                            }
                                            if (this.nick.equals(exitingNick)) {
                                                exit = true;
                                            }
                                            break;
                                        case "HANDVERIFY":
                                            // ZERO-TRUST: a closing receipt must belong to the nick that
                                            // owns THIS authenticated connection. Otherwise a peer could
                                            // submit (and have the host relay) a receipt on behalf of
                                            // another player, forging a false DIVERGENT / framing them.
                                            try {
                                                if (partes_comando.length >= 5
                                                        && new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8").equals(this.nick)) {
                                                    synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                        GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                () -> Helpers.threadRun(this::exitAndCloseSocket));
                                                        GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                    }
                                                } else {
                                                    LOGGER.log(Level.SEVERE,
                                                            "ZERO-TRUST: dropping HANDVERIFY receipt with nick mismatch on connection {0}", this.nick);
                                                }
                                            } catch (Exception ex) {
                                                LOGGER.log(Level.SEVERE, "Dropping malformed HANDVERIFY receipt", ex);
                                            }
                                            break;
                                        case "STRADDLE_RESP":
                                            // ZERO-TRUST: a voluntary straddle response must belong to the nick
                                            // owning THIS authenticated connection. Otherwise a peer could forge
                                            // the UTG's RESP and force them to post a straddle (2x the big blind,
                                            // out of THEIR stack) they never accepted. Same guard as HANDVERIFY.
                                            try {
                                                if (partes_comando.length >= 5
                                                        && new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8").equals(this.nick)) {
                                                    synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                        GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                () -> Helpers.threadRun(this::exitAndCloseSocket));
                                                        GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                    }
                                                } else {
                                                    LOGGER.log(Level.SEVERE,
                                                            "ZERO-TRUST: dropping STRADDLE_RESP with nick mismatch on connection {0}", this.nick);
                                                }
                                            } catch (Exception ex) {
                                                LOGGER.log(Level.SEVERE, "Dropping malformed STRADDLE_RESP", ex);
                                            }
                                            break;
                                        default:
                                            // ZERO-TRUST: if this is a response subcommand carrying the
                                            // sender's nick, tie it to THIS connection (parts[3] == this.nick).
                                            // If it doesn't match (or is malformed), it's discarded: a peer must
                                            // NOT be able to speak for another.
                                            if (NICK_BOUND_SUBCOMMANDS.contains(partes_comando[2])) {
                                                boolean nickOk;
                                                try {
                                                    nickOk = partes_comando.length >= 4
                                                            && new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8").equals(this.nick);
                                                } catch (Exception ex) {
                                                    nickOk = false;
                                                }
                                                if (!nickOk) {
                                                    LOGGER.log(Level.SEVERE,
                                                            "ZERO-TRUST: dropping {0} with nick mismatch on connection {1}",
                                                            new Object[]{partes_comando[2], this.nick});
                                                    // Speaking for someone else is deliberate abuse -> strike (and expulsion on repeat).
                                                    if (registerAbuseStrike("nick spoof " + partes_comando[2])) {
                                                        autoExpel("repeated nick spoofing");
                                                    }
                                                    break;
                                                }
                                            }
                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                        () -> Helpers.threadRun(this::exitAndCloseSocket));
                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                            }
                                            break;
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    }
                } catch (Exception ex) {
                    if (!exit && WaitingRoomFrame.getInstance() != null && !WaitingRoomFrame.getInstance().isExit()) {
                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, nick + " -> exception while processing a command from this client", ex);
                    }
                }
            } while (!exit && !WaitingRoomFrame.getInstance().isExit() && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()));

            if (!WaitingRoomFrame.getInstance().isExit() && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {
                if (WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    GameFrame.getInstance().getCrupier().remotePlayerQuit(nick);
                } else {
                    sala_espera.borrarParticipante(nick);
                }
            }
            exit = true;
            // Closes the socket's FD during teardown: run()'s terminal path used to skip this,
            // and on a clean EXIT (the peer sent EXIT and our side never closed) the descriptor
            // stayed half-open until GC/RST -> an FD leak in long sessions with lots of
            // joins/leaves. socketClose() is idempotent.
            socketClose();
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
        } else {
            this.exit = true;
        }
    }
}
