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
 * Lado servidor (host) de la sala de espera. Gestiona el ServerSocket, el accept
 * loop de conexiones entrantes, el alta/baja de Participants y los broadcasts
 * pre-game a todos los clientes conectados.
 *
 * Se instancia desde WaitingRoomFrame cuando server == true.
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

    // --- Transporte: lectura/escritura cifrada por socket de cliente ---
    // La clase representa el lado servidor; el destino/origen es siempre un cliente
    // identificado por el Socket que recibe la llamada.
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

    // --- Broadcasts pre-game a los Participants conectados ---
    // Envía un comando GAME a todos los Participants excepto `except`. Si confirmation=true,
    // el comando se encola en el writer queue del Participant (se procesa con ACK); si false,
    // se escribe directamente al socket (fire-and-forget).
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

    // Envío puntual a un Participant. Misma semántica de confirmation.
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

    // --- Gestión de Participants ---

    /**
     * Le envía a un Participant recién conectado el USERSLIST con todos los demás
     * Participants ya presentes (excluyendo al propio destinatario). El host NO
     * va aquí: su identidad ya viaja en el intro síncrono del handshake.
     *
     * Wire format per entry: {@code nickB64|unsecureFlag|avatarB64_or_*|pubkeyB64_or_*|selfSigB64_or_*}
     * Entries are joined with {@code @}. Bots have no identity → {@code *|*}.
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
                                    InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png");
                                    if (is != null) {
                                        avatar_b = is.readAllBytes();
                                        is.close();
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
     * Alta de un nuevo Participant: lo añade al mapa, arranca su thread de socket
     * (si no es CPU) y delega en WaitingRoomFrame la parte de actualización de UI.
     */
    public synchronized void addParticipant(String nick, java.io.File avatar, Socket socket,
            SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(waiting_room, nick, avatar, socket, aes_k, hmac_k, cpu);

        waiting_room.getParticipantes().put(nick, participante);
        participante.setUnsecure_player(unsecure);

        if (socket != null) {
            Helpers.threadRun(participante);
        }

        // Callback a la UI
        waiting_room.onParticipantAdded(nick, avatar, cpu);
    }

    /**
     * Baja de un Participant: lo quita del mapa, broadcast DELUSER al resto y
     * delega en WaitingRoomFrame la parte de actualización de UI.
     */
    public synchronized void removeParticipant(String nick) {
        Map<String, Participant> participantes = waiting_room.getParticipantes();
        if (!participantes.containsKey(nick)) {
            return;
        }

        Audio.playWavResource("misc/toilet.wav");

        // Guardamos la referencia ANTES de retirarlo
        Participant pToDel = participantes.get(nick);
        String avatar_src = pToDel.getAvatar_chat_src();

        participantes.remove(nick);

        // Callback a la UI (también desabilita botones, etc.)
        waiting_room.onParticipantRemoved(nick, avatar_src);

        if (!waiting_room.isPartida_empezada() && !waiting_room.isExit()) {
            try {
                String comando = "DELUSER#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                // Pasamos pToDel para excluirlo del broadcast aunque ya no esté en el map
                broadcastASYNCGAMECommand(comando, pToDel);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }
}
