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
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lado cliente de la sala de espera. Gestiona la conexión al servidor (host),
 * el handshake ECDH/AES+HMAC, el bucle de comandos entrantes (CHAT, USERSLIST,
 * NEWUSER, DELUSER, GAME, CONF, etc.), el ping/pong y la reconexión automática.
 *
 * Se instancia desde WaitingRoomFrame cuando server == false.
 */
public class NetClient {

    private static final Logger LOGGER = Logger.getLogger(NetClient.class.getName());

    private final WaitingRoomFrame waiting_room;

    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> late_clients_warning = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> local_client_socket_reader_queue = new LinkedBlockingQueue<>();
    private final HashMap<String, Integer> cliente_last_received = new HashMap<>();
    private final Object local_client_socket_lock = new Object();
    private final Object lock_reconnect = new Object();
    private final Object lock_client_reconnect = new Object();

    private volatile Socket local_client_socket = null;
    private volatile BufferedReader local_client_buffer_read_is = null;
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

    public NetClient(WaitingRoomFrame waiting_room) {
        this.waiting_room = waiting_room;
    }

    public WaitingRoomFrame getWaiting_room() {
        return waiting_room;
    }

    // --- Colas y mapas ---
    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return received_confirmations;
    }

    public ConcurrentLinkedQueue<String> getLate_clients_warning() {
        return late_clients_warning;
    }

    public LinkedBlockingQueue<String> getLocal_client_socket_reader_queue() {
        return local_client_socket_reader_queue;
    }

    public HashMap<String, Integer> getCliente_last_received() {
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

    // --- Socket y streams ---
    public Socket getLocal_client_socket() {
        return local_client_socket;
    }

    public void setLocal_client_socket(Socket s) {
        this.local_client_socket = s;
    }

    public BufferedReader getLocal_client_buffer_read_is() {
        return local_client_buffer_read_is;
    }

    public void setLocal_client_buffer_read_is(BufferedReader r) {
        this.local_client_buffer_read_is = r;
    }

    // --- Llaves cripto ---
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

    // --- Datos del servidor remoto ---
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

    // --- Helpers de ciclo de vida ---
    public void closeClientSocket() {
        if (local_client_socket != null) {
            try {
                local_client_socket.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    // --- Transporte: lectura/escritura cifrada al servidor ---
    // La clase representa el lado cliente, así que el destino/origen es siempre el servidor.
    public void writeCommand(String command) {
        // Si estamos reconectando, esperamos a que termine antes de escribir.
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

        try {
            Socket s = local_client_socket;
            synchronized (s.getOutputStream()) {
                s.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                s.getOutputStream().flush();
            }
        } catch (IOException ex) {
            // Paridad con Participant.writeCommandFromServer (commit 27fe6906):
            // si el write falla, el socket esta muerto. Cerramos para forzar
            // readLine null en runSocketReaderClientThread -> reconectarCliente().
            // Sin esto el cliente solo detectaba la caida cuando el reader
            // devolvia null por su cuenta, que en Linux sin keepalive tarda
            // ~16 min de TCP retransmit.
            LOGGER.log(Level.WARNING, "Client write failed - socket dead, forcing reconnect", ex);
            closeClientSocket();
        }
    }

    private volatile boolean last_read_hmac_failure = false;

    public boolean wasLastReadHmacFailure() {
        return last_read_hmac_failure;
    }

    public String readCommand() {
        // Si estamos reconectando, esperamos.
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
                last_read_hmac_failure = false;
                return Helpers.decryptCommand(local_client_buffer_read_is.readLine(),
                        local_client_aes_key, local_client_hmac_key);
            } catch (java.security.KeyException ex) {
                last_read_hmac_failure = true;
                LOGGER.log(Level.SEVERE, "Channel HMAC verification failed (wrong password or MITM)", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }
}
