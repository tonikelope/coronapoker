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
    // Flag consumido por runPingPongThreadCliente al inicio de cada iteracion
    // para resetear su contador local consecutive_ping_failures. Se eleva en
    // reconectarCliente al completarse una reconexion: si el contador habia
    // acumulado fallos contra el socket viejo, el primer fail contra el nuevo
    // (potencialmente legitimo por jitter post-reconexion) no debe alcanzar
    // el threshold ni cerrar el socket recien instalado.
    private volatile boolean reset_ping_counters = false;
    // Cliente: marca si runPingPongThreadCliente está vivo. Si murió por el
    // threshold de PONGs perdidos (closeClientSocket+break), reconectarCliente lo
    // resucita tras un reconnect OK. Análogo al ping_pong_thread_alive del host.
    private volatile boolean ping_pong_thread_alive = false;
    // Telemetría: cuenta de reconexiones EXITOSAS del cliente al
    // server desde el arranque. Mirror del contador per-peer en Participant
    // (que cuenta en el servidor las reconexiones recibidas de cada peer).
    // El cliente puede comparar su propio valor con el broadcast TELEMETRY
    // del server para detectar divergencias.
    private volatile int reconnection_count = 0;

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

    // --- Socket y streams ---
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
     * Telemetría: nº de reconexiones EXITOSAS de este cliente al
     * server desde el arranque del NetClient.
     */
    public int getReconnectionCount() {
        return reconnection_count;
    }

    /**
     * Incrementa el contador. Debe llamarse desde reconectarCliente()
     * únicamente cuando la reconexión completa con éxito (ok_rec == true,
     * antes del return de la rama positiva).
     */
    public void incrementReconnectionCount() {
        this.reconnection_count++;
    }

    // --- Helpers de ciclo de vida ---
    public void closeClientSocket() {
        // Bajo local_client_socket_lock: el ping thread lo llamaba sin lock y podía
        // cerrar un socket recién instalado por una reconexión en curso. Re-entrante
        // para los callers que ya tienen el lock (writeCommand, reconectarCliente).
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
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Tomamos local_client_socket_lock para leer la volatile + escribir
        // atómicamente. Sin este lock, reconectarCliente (que tiene el mismo
        // lock) puede reasignar local_client_socket a un socket nuevo entre
        // nuestro read del volatile y el uso de getOutputStream(), provocando
        // que escribamos al socket viejo. El sync sobre s.getOutputStream()
        // anterior no protegía porque OutputStream del Socket viejo y el nuevo
        // son monitores distintos.
        //
        // Si reconectarCliente está activo cuando llegamos aquí, el lock está
        // tomado y bloquearemos hasta que termine. Eso es exactamente lo que
        // queremos — escribir DURANTE el reconnect no tiene sentido. La salida
        // por wait(1000) interrumpible de arriba es para el caso de espera
        // controlada por flag; este lock es para la consistencia atómica.
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
                // Paridad con Participant.writeCommandFromServer :
                // si el write falla, el socket esta muerto. Cerramos para forzar
                // readLine null en runSocketReaderClientThread -> reconectarCliente().
                // Sin esto el cliente solo detectaba la caida cuando el reader
                // devolvia null por su cuenta, que en Linux sin keepalive tarda
                // ~16 min de TCP retransmit.
                LOGGER.log(Level.WARNING, "Client write failed — socket dead, forcing reconnect", ex);
                closeClientSocket();
            }
        }
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
                    return Helpers.decryptCommand(frame.text(), local_client_aes_key, local_client_hmac_key);
                }
            } catch (java.security.KeyException ex) {
                LOGGER.log(Level.SEVERE, "Channel HMAC verification failed (wrong password or MITM)", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }

    /**
     * Decrypts and dispatches a binary frame relayed by the host. The host is trusted
     * to label the sender, so a voice note uses the frame's carried nick (parity with
     * the client side of the legacy VOICEMSG text relay). A malformed or HMAC-failing
     * frame is dropped without disturbing the command stream.
     */
    private void handleBinaryFromServer(byte[] frameBody) {
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
     * Binary sibling of {@link #writeCommand(String)}: writes a binary {@link WireFrame}
     * (a voice/avatar blob) to the server. Holds the same socket lock as the text writer,
     * so a binary frame and a text line never interleave on the channel.
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
