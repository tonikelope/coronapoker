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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;

/**
 * Host side of the waiting room's networking. Owns the {@code ServerSocket}, the accept
 * loop for incoming connections, Participant add/remove, and pre-game broadcasts to
 * connected clients.
 *
 * <p>Instantiated by {@code WaitingRoomFrame} when {@code server == true}.
 */
public class NetServer {

    private static final Logger LOGGER = Logger.getLogger(NetServer.class.getName());

    private final WaitingRoomFrame waiting_room;

    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> client_threads = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> late_clients_warning = new ConcurrentLinkedQueue<>();
    private final Object lock_client_pre_game_commands_wait = new Object();
    private volatile ServerSocket server_socket = null;

    public NetServer(WaitingRoomFrame waiting_room) {
        this.waiting_room = waiting_room;
    }

    public WaitingRoomFrame getWaiting_room() {
        return waiting_room;
    }

    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return received_confirmations;
    }

    public ConcurrentLinkedQueue<Long> getClient_threads() {
        return client_threads;
    }

    public ConcurrentLinkedQueue<String> getLate_clients_warning() {
        return late_clients_warning;
    }

    public Object getLock_client_pre_game_commands_wait() {
        return lock_client_pre_game_commands_wait;
    }

    public ServerSocket getServer_socket() {
        return server_socket;
    }

    public void setServer_socket(ServerSocket server_socket) {
        this.server_socket = server_socket;
    }

    public void closeServerSocket() {
        if (server_socket != null) {
            try {
                server_socket.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    // --- Transport: encrypted read/write on a client socket ---
    // This class is the server side, so the destination is always a specific client,
    // identified by the Socket passed in.
    /**
     * Encrypts and writes a text command to a specific client socket.
     *
     * @param command the plaintext command to send
     * @param socket the destination client's socket
     */
    public void writeCommand(String command, Socket socket) {
        try {
            synchronized (socket.getOutputStream()) {
                socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                socket.getOutputStream().flush();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Reads and decrypts the next text command from a specific client socket.
     *
     * @param socket the client's socket to read from
     * @param key session AES key
     * @param hmac_key session HMAC key
     * @return the decrypted command, or {@code null} on end of stream / I/O failure
     */
    public String readCommand(Socket socket, SecretKeySpec key, SecretKeySpec hmac_key) {
        try {
            synchronized (socket.getInputStream()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                return Helpers.decryptCommand(
                        Helpers.readBoundedLine(in, Helpers.MAX_COMMAND_LINE_CHARS),
                        key, hmac_key);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    // --- Pre-game broadcasts to connected Participants ---
    /**
     * Sends a GAME command to every connected Participant except {@code except}.
     *
     * @param command the game command to send (without the GAME# envelope)
     * @param except a Participant to skip (e.g. the sender), or {@code null}
     * @param confirmation if {@code true}, queues the command on each Participant's
     * pre-game writer queue for ACK'd processing; if {@code false}, writes directly to
     * the socket (fire-and-forget)
     */
    public void broadcastASYNCGAMECommand(String command, Participant except, boolean confirmation) {
        ArrayList<Participant> targets = new ArrayList<>();
        Map<String, Participant> participantes = waiting_room.getParticipantes();
        // Safely lock the map to extract valid targets without CME
        synchronized (participantes) {
            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                Participant p = entry.getValue();
                if (p != null && !p.isCpu() && p != except && !p.isExit()) {
                    targets.add(p);
                }
            }
        }

        if (!targets.isEmpty()) {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            for (Participant p : targets) {
                if (!confirmation) {
                    String full_command = "GAME#" + String.valueOf(id) + "#" + command;
                    p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));
                } else {
                    synchronized (p.getPre_game_socket_writer_queue()) {
                        p.getPre_game_socket_writer_queue().add(command);
                        p.getPre_game_socket_writer_queue().notifyAll();
                    }
                }
            }
        }
    }

    public void broadcastASYNCGAMECommand(String command, Participant except) {
        broadcastASYNCGAMECommand(command, except, true);
    }

    /**
     * Sends a GAME command to a single Participant. Same confirmation semantics as
     * {@link #broadcastASYNCGAMECommand(String, Participant, boolean)}.
     *
     * @param command the game command to send
     * @param p the recipient Participant
     * @param confirmation queue for ACK'd processing ({@code true}) or write directly,
     * fire-and-forget ({@code false})
     */
    public void sendASYNCGAMECommand(String command, Participant p, boolean confirmation) {
        if (!confirmation) {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            String full_command = "GAME#" + String.valueOf(id) + "#" + command;
            p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), p.getHmac_key()));
        } else {
            synchronized (p.getPre_game_socket_writer_queue()) {
                p.getPre_game_socket_writer_queue().add(command);
                p.getPre_game_socket_writer_queue().notifyAll();
            }
        }
    }

    public void sendASYNCGAMECommand(String command, Participant p) {
        sendASYNCGAMECommand(command, p, true);
    }

    // --- Participant lifecycle ---

    /**
     * Sends a newly connected Participant the USERSLIST of every other Participant
     * already present (excluding the recipient itself). The host is NOT included here:
     * its identity already travels in the handshake's synchronous intro.
     *
     * <p>Wire format per entry: {@code nickB64|unsecureFlag|avatarB64_or_*|pubkeyB64_or_*|selfSigB64_or_*}
     * Entries are joined with {@code @}. Bots have no identity ({@code *|*}).
     *
     * @param par the newly connected Participant to send the list to
     */
    public void enviarListaUsuariosToNewUser(Participant par) {
        StringBuilder commandBuilder = new StringBuilder("USERSLIST#");
        Map<String, Participant> participantes = waiting_room.getParticipantes();
        synchronized (participantes) {
            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                Participant p = entry.getValue();
                try {
                    if (p != null && p != par) {
                        commandBuilder.append(Base64.getEncoder().encodeToString(p.getNick().getBytes("UTF-8")))
                                .append("|")
                                .append(p.isUnsecure_player() ? "1" : "0")
                                .append("|");

                        byte[] avatar_b = null;
                        if (p.getAvatar() != null || p.isCpu()) {
                            try {
                                if (!p.isCpu() && p.getAvatar() != null) {
                                    try (InputStream is = new FileInputStream(p.getAvatar())) {
                                        avatar_b = is.readAllBytes();
                                    }
                                } else if (p.isCpu()) {
                                    try (InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                                        if (is != null) {
                                            avatar_b = is.readAllBytes();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error reading avatar for USERSLIST", e);
                            }
                        }
                        commandBuilder.append(avatar_b != null ? Base64.getEncoder().encodeToString(avatar_b) : "*");

                        // Identity: pubkey + self_sig per entry, atomic with
                        // the rest of the peer's data. Bots have no identity ("*|*").
                        byte[] pubkey = p.isCpu() ? null : p.getIdentity_pubkey();
                        byte[] selfSig = p.isCpu() ? null : p.getIdentity_self_sig();
                        commandBuilder.append("|")
                                .append(pubkey != null ? Base64.getEncoder().encodeToString(pubkey) : "*")
                                .append("|")
                                .append(selfSig != null ? Base64.getEncoder().encodeToString(selfSig) : "*");
                        commandBuilder.append("@");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error enqueuing entry in USERSLIST", ex);
                }
            }
        }
        sendASYNCGAMECommand(commandBuilder.toString(), par);
    }

    /**
     * Adds a new Participant: puts it in the map, starts its socket thread (unless it's
     * a CPU/bot), and delegates the UI-update side to WaitingRoomFrame.
     *
     * <p>Currently dead code: nothing calls this. {@code WaitingRoomFrame.nuevoParticipante()}
     * is the add path actually in use, for both host and client.
     *
     * @param nick nickname/key under which the Participant is registered
     * @param avatar avatar image file, or {@code null}
     * @param socket the client socket, or {@code null} for a CPU/bot participant
     * @param aes_k session AES key
     * @param hmac_k session HMAC key
     * @param cpu whether this Participant is a bot (no socket thread is started)
     * @param unsecure whether the peer connected without identity verification
     */
    public synchronized void addParticipant(String nick, java.io.File avatar, Socket socket,
            SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(waiting_room, nick, avatar, socket, aes_k, hmac_k, cpu);

        waiting_room.getParticipantes().put(nick, participante);
        participante.setUnsecure_player(unsecure);

        if (socket != null) {
            Helpers.threadRun(participante);
        }

        // UI callback
        waiting_room.onParticipantAdded(nick, avatar, cpu);
    }

    /**
     * Removes a Participant: takes it out of the map, broadcasts DELUSER to the rest,
     * and delegates the UI-update side to WaitingRoomFrame.
     *
     * <p>Currently dead code: nothing calls this. The removal path actually in use is
     * {@code WaitingRoomFrame.borrarParticipante()}, for both host and client.
     *
     * @param nick nickname/key of the Participant to remove
     */
    public synchronized void removeParticipant(String nick) {
        Map<String, Participant> participantes = waiting_room.getParticipantes();
        // map.remove()'s return value is used instead of contains+get+remove, which would be
        // a check-then-act race on a merely-synchronized map — moot in practice since this
        // method is dead code (see above), but worth keeping if it's ever revived.
        Participant pToDel;

        synchronized (participantes) {
            pToDel = participantes.remove(nick);
        }

        if (pToDel == null) {
            return;
        }

        if (GameFrame.saleSonidoOn()) {
            Audio.playWavResource("misc/toilet.wav");
        }

        String avatar_src = pToDel.getAvatar_chat_src();

        // UI callback (also disables buttons, etc.)
        waiting_room.onParticipantRemoved(nick, avatar_src);

        if (!waiting_room.isPartida_empezada() && !waiting_room.isExit()) {
            try {
                String comando = "DELUSER#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                // Pass pToDel so it's excluded from the broadcast even though it's already gone from the map
                broadcastASYNCGAMECommand(comando, pToDel);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }
}
