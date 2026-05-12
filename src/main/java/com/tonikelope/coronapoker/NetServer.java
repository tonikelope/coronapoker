/*
 * Copyright (C) 2020 tonikelope
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lado servidor (host) de la sala de espera. Gestiona el ServerSocket, el accept
 * loop de conexiones entrantes, el alta/baja de Participants y los broadcasts
 * pre-game a todos los clientes conectados.
 *
 * Esta clase se instancia desde WaitingRoomFrame cuando server == true.
 *
 * REFACTOR EN CURSO (Fase 2): contiene ya el estado server-side. Los métodos
 * (servidor(), serverSocketHandler(), nuevoParticipante(), broadcasts, etc.)
 * migrarán aquí en fases sucesivas.
 */
public class NetServer {

    private static final Logger LOGGER = Logger.getLogger(NetServer.class.getName());

    private final WaitingRoomFrame waiting_room;

    // ESTADO SERVER-SIDE (Fase 2 — migrado desde WaitingRoomFrame)
    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> client_threads = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> late_clients_warning = new ConcurrentLinkedQueue<>();
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
    public void writeCommandFromServer(String command, Socket socket) {
        try {
            synchronized (socket.getOutputStream()) {
                socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                socket.getOutputStream().flush();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public String readCommandFromClient(Socket socket, SecretKeySpec key, SecretKeySpec hmac_key) {
        try {
            synchronized (socket.getInputStream()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                return Helpers.decryptCommand(in.readLine(), key, hmac_key);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
