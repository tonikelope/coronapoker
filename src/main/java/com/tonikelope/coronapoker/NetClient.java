/*
 * Copyright (C) 2020 tonikelope
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;

/**
 * Client side of the waiting room. Manages the connection to the server (host),
 * the ECDH/AES+HMAC handshake, the incoming command loop (CHAT, USERSLIST,
 * NEWUSER, DELUSER, GAME, CONF, etc.), ping/pong and automatic reconnection.
 *
 * <p>
 * Instantiated from {@code WaitingRoomFrame} when {@code server == false}.
 */
public class NetClient {

    private static final Logger LOGGER = Logger.getLogger(NetClient.class.getName());

    private final WaitingRoomFrame waiting_room;

    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> late_clients_warning = new ConcurrentLinkedQueue<>();
    // Client-side twin of Participant.SOCKET_READER_QUEUE_CAPACITY: without it, a hostile host
    // flooding commands faster than the client can process them would OOM it. Once full, the
    // reader stops draining the socket and TCP backpressure throttles the sender. Generously
    // sized for the game, where legitimate bursts are in the tens.
    public static final int SOCKET_READER_QUEUE_CAPACITY = 10000;

    private final LinkedBlockingQueue<String> local_client_socket_reader_queue = new LinkedBlockingQueue<>(SOCKET_READER_QUEUE_CAPACITY);
    // Concurrent: written/read by the consumer thread (containsKey/get/put for GAME
    // command dedup) and clear()-ed by the reader thread on a null-read. A plain
    // HashMap raced across those two threads could corrupt the table during a resize.
    private final Map<String, Integer> cliente_last_received = new ConcurrentHashMap<>();
    private final Object local_client_socket_lock = new Object();
    private final Object lock_reconnect = new Object();
    private final Object lock_client_reconnect = new Object();

    private volatile Socket local_client_socket = null;
    private volatile BufferedInputStream local_client_buffer_read_is = null;
    private volatile SecretKeySpec local_client_aes_key = null;
    private volatile SecretKeySpec local_client_hmac_key = null;
    private volatile SecretKeySpec local_client_hmac_key_orig = null;
    private volatile Reconnect2ServerDialog reconnect_dialog = null;
    private volatile boolean reconnecting = false;
    private volatile boolean unsecure_server = false;
    private volatile Integer remote_server_pong;
    private volatile Integer remote_server_pong2;
    private volatile int remote_server_latency;
    private volatile int remote_server_latency2;
    // Consumed by runPingPongThreadCliente at the start of each iteration to reset its local
    // consecutive_ping_failures counter. Raised by reconectarCliente once a reconnection
    // completes: if the counter had accumulated failures against the old socket, the first
    // failure against the new one (possibly legitimate post-reconnect jitter) must not reach
    // the threshold and close the freshly installed socket.
    private volatile boolean reset_ping_counters = false;
    // Client-side: whether runPingPongThreadCliente is alive. If it died from the missed-PONG
    // threshold (closeClientSocket+break), reconectarCliente revives it after a successful
    // reconnect. Mirrors the host's ping_pong_thread_alive.
    private volatile boolean ping_pong_thread_alive = false;
    // Telemetry: count of SUCCESSFUL client reconnections to the server since startup. Mirrors
    // the per-peer counter in Participant (which counts, server-side, the reconnections
    // received from each peer). The client can compare its own value against the server's
    // TELEMETRY broadcast to detect divergence.
    private volatile int reconnection_count = 0;

    public NetClient(WaitingRoomFrame waiting_room) {
        this.waiting_room = waiting_room;
    }

    public WaitingRoomFrame getWaiting_room() {
        return waiting_room;
    }

    // --- Queues and maps ---
    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return received_confirmations;
    }

    public ConcurrentLinkedQueue<String> getLate_clients_warning() {
        return late_clients_warning;
    }

    public LinkedBlockingQueue<String> getLocal_client_socket_reader_queue() {
        return local_client_socket_reader_queue;
    }

    /**
     * Queues what was read from the socket, honoring the queue cap. Client-side
     * twin of {@code Participant.encolarLeido}.
     *
     * <p>
     * Never drops anything while the room is alive: retries every second while
     * the queue is full, and TCP backpressure does the rest (the reader stops
     * draining the socket, its window closes and the host throttles). A plain
     * {@code put} would do the same but silently, with no way out. Do NOT use
     * this for the close signal — use {@link #encolarSenalCierre()}.
     *
     * @param mensaje the raw line read from the socket
     */
    public void encolarLeido(String mensaje) {
        try {
            while (!waiting_room.isExit()) {
                if (local_client_socket_reader_queue.offer(mensaje, 1, java.util.concurrent.TimeUnit.SECONDS)) {
                    return;
                }
                LOGGER.log(Level.WARNING,
                        "Client socket reader queue is full ({0}) — waiting for the consumer",
                        SOCKET_READER_QUEUE_CAPACITY);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Queues the close signal no matter what. Client-side twin of
     * {@code Participant.encolarSenalCierre}.
     *
     * <p>
     * It's the only thing that pulls the consumer out of its {@code take()},
     * which is where the socket close and reconnect originate — that's why room
     * exit isn't checked here. If the queue is full, room is made by dropping
     * the oldest entry: those are commands from a connection that's already
     * going down, and none of them matter more than the signal itself.
     */
    public void encolarSenalCierre() {
        for (int intentos = 0; intentos < SOCKET_READER_QUEUE_CAPACITY
                && !local_client_socket_reader_queue.offer(WaitingRoomFrame.POISON_PILL); intentos++) {
            local_client_socket_reader_queue.poll();
        }
    }

    public Map<String, Integer> getCliente_last_received() {
        return cliente_last_received;
    }

    // --- Locks ---
    public Object getLocal_client_socket_lock() {
        return local_client_socket_lock;
    }

    public Object getLock_reconnect() {
        return lock_reconnect;
    }

    public Object getLock_client_reconnect() {
        return lock_client_reconnect;
    }

    // --- Socket and streams ---
    public Socket getLocal_client_socket() {
        return local_client_socket;
    }

    public void setLocal_client_socket(Socket s) {
        this.local_client_socket = s;
    }

    public BufferedInputStream getLocal_client_buffer_read_is() {
        return local_client_buffer_read_is;
    }

    public void setLocal_client_buffer_read_is(BufferedInputStream r) {
        this.local_client_buffer_read_is = r;
    }

    // --- Crypto keys ---
    public SecretKeySpec getLocal_client_aes_key() {
        return local_client_aes_key;
    }

    public void setLocal_client_aes_key(SecretKeySpec k) {
        this.local_client_aes_key = k;
    }

    public SecretKeySpec getLocal_client_hmac_key() {
        return local_client_hmac_key;
    }

    public void setLocal_client_hmac_key(SecretKeySpec k) {
        this.local_client_hmac_key = k;
    }

    public SecretKeySpec getLocal_client_hmac_key_orig() {
        return local_client_hmac_key_orig;
    }

    public void setLocal_client_hmac_key_orig(SecretKeySpec k) {
        this.local_client_hmac_key_orig = k;
    }

    // --- Remote server data ---
    public Reconnect2ServerDialog getReconnect_dialog() {
        return reconnect_dialog;
    }

    public void setReconnect_dialog(Reconnect2ServerDialog d) {
        this.reconnect_dialog = d;
    }

    public boolean isReconnecting() {
        return reconnecting;
    }

    public void setReconnecting(boolean b) {
        this.reconnecting = b;
    }

    public boolean isUnsecure_server() {
        return unsecure_server;
    }

    public void setUnsecure_server(boolean b) {
        this.unsecure_server = b;
    }

    public Integer getRemote_server_pong() {
        return remote_server_pong;
    }

    public void setRemote_server_pong(Integer p) {
        this.remote_server_pong = p;
    }

    public Integer getRemote_server_pong2() {
        return remote_server_pong2;
    }

    public void setRemote_server_pong2(Integer p) {
        this.remote_server_pong2 = p;
    }

    public int getRemote_server_latency() {
        return remote_server_latency;
    }

    public void setRemote_server_latency(int l) {
        this.remote_server_latency = l;
    }

    public int getRemote_server_latency2() {
        return remote_server_latency2;
    }

    public void setRemote_server_latency2(int l) {
        this.remote_server_latency2 = l;
    }

    public boolean isReset_ping_counters() {
        return reset_ping_counters;
    }

    public void setReset_ping_counters(boolean v) {
        this.reset_ping_counters = v;
    }

    public boolean isPingPongThreadAlive() {
        return ping_pong_thread_alive;
    }

    public void setPingPongThreadAlive(boolean v) {
        this.ping_pong_thread_alive = v;
    }

    /**
     * @return the number of SUCCESSFUL reconnections of this client to the
     * server since the {@code NetClient} started (telemetry)
     */
    public int getReconnectionCount() {
        return reconnection_count;
    }

    /**
     * Increments the counter. Must be called from {@code reconectarCliente()}
     * only when the reconnection completes successfully
     * ({@code ok_rec == true}, before the positive branch's return).
     */
    public void incrementReconnectionCount() {
        this.reconnection_count++;
    }

    // --- Lifecycle helpers ---
    /**
     * Closes the client socket. Re-entrant for callers that already hold
     * {@code local_client_socket_lock} (writeCommand, reconectarCliente).
     */
    public void closeClientSocket() {
        // Under local_client_socket_lock: the ping thread used to call this without the lock
        // and could close a socket just installed by an in-progress reconnection.
        synchronized (local_client_socket_lock) {
            if (local_client_socket != null) {
                try {
                    local_client_socket.close();
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * Closes a SPECIFIC socket whose write is stuck because the server stopped
     * reading. Deliberately does NOT take {@code local_client_socket_lock}:
     * that lock is exactly what the blocked write holds
     * ({@link #writeCommand(String)} writes under it), so
     * {@link #closeClientSocket()}, which needs it, couldn't unstick it.
     * {@code close()} is thread-safe and wakes the stalled write with an
     * {@code IOException}, whose catch forces a reconnect. Closes the received
     * reference, not {@code local_client_socket}, so as not to tear down a new
     * socket a reconnection may have installed meanwhile.
     *
     * @param s the specific socket to close
     */
    public void closeStalledSocket(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "closeStalledSocket failed", ex);
            }
        }
    }

    // --- Transport: encrypted read/write to the server ---
    // This class is the client side, so the destination/origin is always the server.
    /**
     * Encrypts and writes a text command to the server, blocking while a
     * reconnect is in progress and forcing one if the write fails.
     *
     * @param command the plaintext command to send
     */
    public void writeCommand(String command) {
        // While reconnecting, wait for it to finish before writing.
        while (reconnecting) {
            synchronized (local_client_socket_lock) {
                try {
                    local_client_socket_lock.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "reconnect wait", ex);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Takes local_client_socket_lock to read the volatile and write atomically. Without
        // this lock, reconectarCliente (which holds the same lock) could reassign
        // local_client_socket to a new socket between our read of the volatile and the
        // getOutputStream() call, causing us to write to the stale socket. Synchronizing on
        // s.getOutputStream() alone didn't protect against this, since the old and new
        // Socket's OutputStream are different monitors.
        //
        // If reconectarCliente is active when we get here, the lock is held and we'll block
        // until it finishes — exactly what we want, since writing DURING a reconnect makes no
        // sense. The interruptible wait(1000) above handles the flag-controlled wait; this
        // lock handles atomic consistency.
        synchronized (local_client_socket_lock) {
            Socket s = local_client_socket;
            if (s == null) {
                LOGGER.log(Level.WARNING, "Client write skipped — socket not yet available");
                return;
            }
            try {
                java.io.OutputStream os = s.getOutputStream();
                os.write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ex) {
                // Parity with Participant.writeCommandFromServer: if the write fails, the
                // socket is dead. Close it to force a null readLine in
                // runSocketReaderClientThread -> reconectarCliente(). Without this, the client
                // only detected the drop when the reader returned null on its own, which on
                // Linux without keepalive takes ~16 min of TCP retransmits.
                LOGGER.log(Level.WARNING, "Client write failed — socket dead, forcing reconnect", ex);
                closeClientSocket();
            }
        }
    }

    /**
     * Reads and decrypts the next text command from the server, transparently
     * handling relayed binary frames and dropping (not disconnecting on) frames
     * that fail channel auth.
     *
     * @return the decrypted command, or {@code null} on end of stream / I/O
     * failure
     */
    public String readCommand() {
        // While reconnecting, wait.
        while (reconnecting) {
            synchronized (local_client_socket_lock) {
                try {
                    local_client_socket_lock.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "reconnect wait", ex);
                    break;
                }
            }
        }

        synchronized (local_client_buffer_read_is) {
            try {
                while (true) {
                    WireFrame.Result frame = WireFrame.read(local_client_buffer_read_is, Helpers.MAX_COMMAND_LINE_CHARS);
                    if (frame == null) {
                        return null;
                    }
                    if (frame.isBinary()) {
                        // Binary voice/avatar frame relayed by the host: handle inline and
                        // read the next frame. Order-independent side channel (see Participant).
                        handleBinaryFromServer(frame.binary());
                        continue;
                    }
                    try {
                        return Helpers.decryptCommand(frame.text(), local_client_aes_key, local_client_hmac_key);
                    } catch (java.security.KeyException ke) {
                        // The frame is DROPPED and reading continues, as SECURITY.md documents
                        // ("the receiver drops the frame"). This used to break out of the loop
                        // and fall through to the return null below, i.e. EOF: a frame that
                        // failed channel auth triggered a full reconnect instead of being
                        // ignored.
                        LOGGER.log(Level.WARNING,
                                "Dropping unauthenticated frame from the host ({0}) — wrong password, MITM or corruption",
                                ke.getMessage());
                        continue;
                    }
                }
            } catch (Exception ex) {
                // Channel failures no longer reach here: they're dropped per-frame inside the
                // loop. What's left is real I/O, and that does mean end of read.
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }

    // F2 ANTI-DoS (client-side BINARY channel): symmetric to Participant.binaryInboundAbuse. A hostile
    // HOST could flood the client with binary frames (voice/stats) and exhaust threads/disk/CPU on ITS
    // OWN machine (statsSyncOnMessage = 1 thread+SQLite per frame; a voice note = disk write + playback).
    // Same as the host side: its own token bucket, excess -> silent DROP BEFORE decrypting/processing. No
    // warning: a large but LEGITIMATE stats push from the host (a client with a big backlog on connect)
    // is indistinguishable from abuse, and dropping is harmless (import is idempotent, resynced next
    // time); warning would falsely accuse an honest host. Single thread (the reader, serialized on
    // local_client_buffer_read_is) -> no lock needed.
    private static final double BINARY_INBOUND_BURST = 128.0;
    private static final double BINARY_INBOUND_REFILL_PER_SEC = 8.0;
    private double binary_inbound_tokens = BINARY_INBOUND_BURST;
    private long binary_inbound_last_refill_ns = 0L;

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
        return true; // no tokens left -> rate exceeded
    }

    /**
     * Decrypts and dispatches a binary frame relayed by the host. The host is
     * trusted to label the sender, so a voice note uses the frame's carried
     * nick (parity with the client side of the legacy VOICEMSG text relay). A
     * malformed or HMAC-failing frame is dropped without disturbing the command
     * stream.
     */
    private void handleBinaryFromServer(byte[] frameBody) {
        // F2 ANTI-DoS: rate-limit the binary channel BEFORE decrypting/processing. Excess -> silent DROP.
        if (binaryInboundAbuse()) {
            return;
        }
        try {
            byte[] payload = Helpers.decryptBytes(frameBody, local_client_aes_key, local_client_hmac_key);
            if (payload == null) {
                return;
            }
            BinaryWire.Decoded decoded = BinaryWire.decode(payload);
            if (decoded.type == BinaryWire.TYPE_VOICE) {
                waiting_room.recibirNotaVoz(decoded.nick, decoded.payload);
            } else if (decoded.type == BinaryWire.TYPE_DB) {
                // Stats DB sync relayed by the host (the only peer for a client).
                waiting_room.statsSyncOnMessage(decoded.nick, decoded.payload, false);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Dropped malformed binary frame from server", ex);
        }
    }

    /**
     * Binary sibling of {@link #writeCommand(String)}: writes a binary
     * {@link WireFrame} (a voice/avatar blob) to the server. Holds the same
     * socket lock as the text writer, so a binary frame and a text line never
     * interleave on the channel.
     *
     * @param frameBody the raw binary frame payload to send
     */
    public void writeBinary(byte[] frameBody) {
        while (reconnecting) {
            synchronized (local_client_socket_lock) {
                try {
                    local_client_socket_lock.wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "reconnect wait", ex);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        synchronized (local_client_socket_lock) {
            Socket s = local_client_socket;
            if (s == null) {
                LOGGER.log(Level.WARNING, "Client binary write skipped — socket not yet available");
                return;
            }
            try {
                WireFrame.writeBinary(s.getOutputStream(), frameBody);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Client binary write failed — socket dead, forcing reconnect", ex);
                closeClientSocket();
            }
        }
    }
}
