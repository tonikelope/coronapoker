/*
 * Copyright (C) 2026 tonikelope
 *
 * Zero-Trust Pure Java EC-SRA Cryptographic Engine.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import static com.tonikelope.coronapoker.WaitingRoomFrame.PING_INTERVAL_MS;
import static com.tonikelope.coronapoker.WaitingRoomFrame.POISON_PILL;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class Participant implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Participant.class.getName());

    public static final int ASYNC_COMMAND_QUEUE_WAIT = 1000;
    // Periodo de gracia tras el primer null read del socket de un peer.
    // Si llega resetSocket() en este margen el reader continua sin marcar
    // exit=true. Subido de 5s a 15s tras observar reconexiones reales que
    // tardaban 26s (TCP retransmit timeout largo + segundo intento): con
    // 5s el host marcaba exit=true y disparaba MISDEAL antes de que el
    // cliente pudiera volver, generando falsos positivos. 15s absorbe la
    // gran mayoria de hiccups de internet sin colgar la mesa mas alla de
    // lo tolerable cuando un peer SI esta muerto de verdad (abortToRecover
    // lleva a todo el mundo al lobby con recover dialog, asi que el coste
    // de un eventual false positive a 15s tampoco es grave).
    public static final int RECIBIDO_TIMEOUT = 15000;

    private final Object ping_pong_lock = new Object();
    private final Object participant_socket_lock = new Object();
    private final HashMap<String, Integer> last_received = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pre_game_socket_writer_queue = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> socket_reader_queue = new LinkedBlockingQueue<>();
    private final WaitingRoomFrame sala_espera;
    private final String nick;
    private final File avatar;

    private volatile Socket socket = null;
    private volatile Socket recon_socket = null;
    private volatile boolean exit = false;
    private volatile BufferedReader input_stream_reader = null;
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
    private volatile int latency;
    private volatile int latency2;
    private volatile int pong_timeout_counter = 0;
    // Numero de pongs consecutivos perdidos antes de marcar exit=true al
    // peer. Con PING_INTERVAL_MS=5s y PING_PONG_TIMEOUT=10s, 3 fallos = ~15s
    // sin respuesta del cliente, que es el mismo grace period que ya tiene
    // RECIBIDO_TIMEOUT para el reader. Sin este threshold, sockets que NO
    // detectan caida silenciosa (peer killed sin RST) dejaban al server sin
    // expulsar nunca al peer.
    public static final int PING_TIMEOUT_KICK_THRESHOLD = 3;
    private volatile int pong2_timeout_counter = 0;
    private volatile byte[] received_token = null;
    private volatile int new_hand_ready = 0;

    // --- SRA ZERO-TRUST VARIABLES ---
    private volatile byte[] sra_unlock = null; // Master key to remove player lock

    public byte[] getSra_unlock() {
        return sra_unlock;
    }

    public void setSra_unlock(byte[] sra_unlock) {
        this.sra_unlock = sra_unlock;
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
                ImageIO.write(Helpers.toBufferedImage(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage()), "png", new File(avatar.getAbsolutePath() + "_chat"));
                avatar_chat_src = new File(avatar.getAbsolutePath() + "_chat").toURI().toURL().toExternalForm();
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
        Helpers.threadRun(() -> {
            while (!exit && WaitingRoomFrame.getInstance() != null) {
                int ping = Helpers.CSPRNG_GENERATOR.nextInt();
                pong = null;
                pong2 = null;
                latency = -1;
                latency2 = -1;
                long pingStartNs = System.nanoTime();

                try {
                    writeCommandFromServer("PING#" + String.valueOf(ping));
                } catch (Exception ex) {
                    break;
                }

                long end = System.currentTimeMillis() + WaitingRoomFrame.PING_PONG_TIMEOUT;

                while (!exit && (pong == null || pong2 == null) && System.currentTimeMillis() < end) {
                    synchronized (ping_pong_lock) {
                        try {
                            ping_pong_lock.wait(end - System.currentTimeMillis());
                        } catch (InterruptedException ignored) {
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

                // Si el cliente lleva PING_TIMEOUT_KICK_THRESHOLD intentos
                // consecutivos sin responder al ping, lo damos por caido y
                // marcamos exit=true para que el reader rompa su bucle, los
                // waits del Crupier (requestRemoteUnlock, lectura de DECISION,
                // etc) salgan, y se dispare el flujo de peer caido normal
                // (autofold en ronda de apuestas o MISDEAL peer.* si la
                // cascade no puede completar). Sin este check, un socket que
                // no detecta caida silenciosa (peer killed sin RST) deja al
                // reader bloqueado indefinidamente y el server jamas expulsa
                // al peer.
                if (pong_timeout_counter >= PING_TIMEOUT_KICK_THRESHOLD && !exit && !isCpu()
                        && WaitingRoomFrame.getInstance() != null
                        && WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    LOGGER.log(Level.WARNING, "[PEER] Participant {0} missed {1} consecutive pongs — marking exit=true (peer kicked)", new Object[]{nick, pong_timeout_counter});
                    exit = true;
                    try {
                        if (this.socket != null) {
                            this.socket.close();
                        }
                    } catch (Exception ignored) {
                    }
                    synchronized (getParticipant_socket_lock()) {
                        getParticipant_socket_lock().notifyAll();
                    }
                    synchronized (ping_pong_lock) {
                        ping_pong_lock.notifyAll();
                    }
                    break;
                }

                if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().isPartida_empezada() && GameFrame.getInstance() != null) {
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
        });
    }

    private void runSocketReaderThread() {
        Helpers.threadRun(() -> {
            boolean timeout = false;
            while (!exit) {
                String mensaje_recibido = null;
                try {
                    mensaje_recibido = readCommandFromClient();
                } catch (Exception ex) {
                }

                if (mensaje_recibido != null) {
                    if (timeout) {
                        timeout = false;
                        GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(false);
                    }
                    String[] partes_comando = mensaje_recibido.split("#");
                    if (null == partes_comando[0]) {
                        try {
                            socket_reader_queue.put(mensaje_recibido);
                        } catch (Exception ex) {
                        }
                    } else {
                        switch (partes_comando[0]) {
                            case "PING":
                                writeCommandFromServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                try {
                                    socket_reader_queue.put(mensaje_recibido);
                                } catch (Exception ex) {
                                }
                                break;
                            case "PONG":
                                pong = Integer.valueOf(partes_comando[1]);
                                synchronized (ping_pong_lock) {
                                    ping_pong_lock.notifyAll();
                                }
                                break;
                            case "PONG2":
                                pong2 = Integer.valueOf(partes_comando[1]);
                                synchronized (ping_pong_lock) {
                                    ping_pong_lock.notifyAll();
                                }
                                break;
                            default:
                                try {
                                    socket_reader_queue.put(mensaje_recibido);
                                } catch (Exception ex) {
                                }
                                break;
                        }
                    }
                } else {
                    try {
                        if (!socket_reader_queue.contains(POISON_PILL)) {
                            socket_reader_queue.put(POISON_PILL);
                        }
                    } catch (Exception ex) {
                    }
                }

                if (mensaje_recibido == null && !reset_socket && !exit && !WaitingRoomFrame.getInstance().isExit()
                        && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null
                        || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                    if (!timeout) {
                        timeout = true;
                        GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(true);

                        long graceMs = (resetting_socket || force_reset_socket) ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT;
                        LOGGER.log(Level.INFO, "[PEER] Participant {0} entered TIMEOUT state — waiting {1}ms for reconnect", new Object[]{nick, graceMs});

                        if (!this.force_reset_socket) {
                            try {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (Exception ex) {
                            }
                        }

                        if (!reset_socket) {
                            synchronized (getParticipant_socket_lock()) {
                                try {
                                    getParticipant_socket_lock().wait(graceMs);
                                } catch (Exception ex) {
                                }
                            }
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "[PEER] Participant {0} TIMEOUT expired without reconnect — marking exit=true", nick);
                        exit = true;
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

                    // El id se mantiene a lo largo de los reintentos para que el cliente pueda deduplicar
                    // por (subcomando, id) si una retransmisión llega cuando ya procesó la primera copia.
                    int id = Helpers.CSPRNG_GENERATOR.nextInt();
                    String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                    do {
                        if (!writeCommandFromServer(Helpers.encryptCommand(full_command, getAes_key(), getHmac_key()))) {
                            waitPreGameCommandConfirmations(id, pendientes);
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

    public SecretKeySpec getHmac_key() {
        while (resetting_socket || force_reset_socket) {
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
        while (resetting_socket || force_reset_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        return aes_key;
    }

    public BufferedReader getInput_stream_reader() {
        while (resetting_socket || force_reset_socket) {
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

    public boolean writeGAMECommandFromServer(String command) {
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        return writeCommandFromServer("GAME#" + String.valueOf(id) + "#" + command);
    }

    public boolean writeCommandFromServer(String command) {
        while (resetting_socket || force_reset_socket) {
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
            // Socket cerrado / peer caido detectado en el write. Sin esto el
            // server seguia intentando escribir indefinidamente sin enterarse
            // de la caida: PINGs no llegan, DECISION_REQUEST/REQ_SRA_UNLOCK
            // no llegan, y los waits del Crupier quedaban colgados hasta
            // que el ping/pong threshold lo expulsara (15s) o nunca si el
            // socket no detectaba EOF.
            // Marcar exit + notify desbloquea inmediatamente los waiters:
            // ronda de apuestas (autofold del peer caido), requestRemoteUnlock
            // (sale con null -> MISDEAL peer.* -> abortAndRecover -> sala de
            // espera), y el propio reader thread sale del while.
            if (!exit && !resetting_socket && !force_reset_socket) {
                LOGGER.log(Level.WARNING, "[PEER] Participant {0} write failed (socket closed) — marking exit=true", nick);
                exit = true;
                synchronized (getParticipant_socket_lock()) {
                    getParticipant_socket_lock().notifyAll();
                }
                synchronized (ping_pong_lock) {
                    ping_pong_lock.notifyAll();
                }
            }
        }
        return true;
    }

    public String readCommandFromClient() {
        while (resetting_socket || force_reset_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (Exception ex) {
                }
            }
        }
        synchronized (getInput_stream_reader()) {
            try {
                return Helpers.decryptCommand(getInput_stream_reader().readLine(), getAes_key(), getHmac_key());
            } catch (Exception ex) {
            }
        }
        return null;
    }

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

    public void forceSocketReconnect() {
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

    private void setSocket(Socket socket) {
        synchronized (getParticipant_socket_lock()) {
            this.socket = socket;
            if (this.socket != null) {
                try {
                    this.input_stream_reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                } catch (Exception ex) {
                }
            }
        }
    }

    public boolean resetSocket(Socket sock, SecretKeySpec aes_k, SecretKeySpec hmac_k) {
        this.resetting_socket = true;
        forceSocketReconnect();
        this.recon_socket = sock;

        synchronized (getParticipant_socket_lock()) {
            try {
                this.socket = this.recon_socket;
                this.input_stream_reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                this.aes_key = aes_k;
                this.hmac_key = hmac_k;
                if (!isForce_reset_socket()) {
                    Audio.playWavResource("misc/yahoo.wav");
                }
                this.reset_socket = true;
                LOGGER.log(Level.INFO, "[PEER] Participant {0} resetSocket OK — reconnect succeeded within grace period (exit stays false)", nick);
            } catch (Exception ex) {
                this.reset_socket = false;
                LOGGER.log(Level.WARNING, "[PEER] Participant " + nick + " resetSocket FAILED — reader thread will continue to timeout", ex);
            } finally {
                this.recon_socket = null;
                this.force_reset_socket = false;
                this.resetting_socket = false;
            }
            getParticipant_socket_lock().notifyAll();
            return this.reset_socket;
        }
    }

    public boolean waitPreGameCommandConfirmations(int id, ArrayList<String> pending) {
        long start_time = System.currentTimeMillis();
        boolean timeout = false;
        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!exit && !pending.isEmpty() && !timeout) {
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
                        timeout = true;
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

    @Override
    public void run() {
        if (socket != null) {
            runPreGameSocketWriterQueueThread();
            runSocketReaderThread();
            runPingPongThread();

            String recibido;
            do {
                reset_socket = false;
                try {
                    recibido = socket_reader_queue.take();
                    if (!POISON_PILL.equals(recibido)) {
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
                                sala_espera.recibirMensajeChat(new String(Base64.getDecoder().decode(partes_comando[1]), "UTF-8"), mensaje);
                                break;
                            case "CONF":
                                WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{nick, Integer.valueOf(partes_comando[1])});
                                synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                                    WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                }
                                break;
                            case "GAME":
                                String subcomando = partes_comando[2];
                                int command_id = Integer.parseInt(partes_comando[1]);

                                // El paquete CONF tiene que ir cifrado: el cliente espera siempre comandos cifrados
                                // y un CONF en claro provoca fallo de descifrado y deadlock en su lectura.
                                try {
                                    String confMsg = "CONF#" + String.valueOf(command_id + 1) + "#OK";
                                    this.writeCommandFromServer(Helpers.encryptCommand(confMsg, this.aes_key, this.hmac_key));
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Failed to encrypt CONF message", e);
                                }

                                if (!last_received.containsKey(subcomando) || !last_received.get(subcomando).equals(command_id)) {
                                    last_received.put(subcomando, command_id);

                                    switch (subcomando) {

                                        case "PAUSE":
                                            final String[] partes_final_pause = partes_comando;
                                            Helpers.threadRun(() -> {
                                                synchronized (GameFrame.getInstance().getLock_pause()) {
                                                    if (("0".equals(partes_final_pause[3]) && GameFrame.getInstance().isTimba_pausada()) && nick.equals(GameFrame.getInstance().getNick_pause()) || ("1".equals(partes_final_pause[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                        GameFrame.getInstance().pauseTimba(nick);
                                                        if (GameFrame.getInstance().isTimba_pausada()) {
                                                            GameFrame.getInstance().getRegistro().print("PAUSE (" + nick + ")");
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
                                        case "RABBIT":
                                            GameFrame.getInstance().getCrupier().RABBIT_HANDLER(nick, Integer.parseInt(partes_comando[4]));
                                            break;
                                        case "REBUYNOW":
                                            GameFrame.getInstance().getCrupier().rebuyNow(nick, Integer.parseInt(partes_comando[3]));
                                            break;
                                        case "SHOWCARDS":
                                            Helpers.threadRun(() -> {
                                                try {
                                                    String shNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                    String sraKeyB64 = partes_comando[4];

                                                    // 1. El servidor descifra las cartas localmente con la SRA key recibida
                                                    GameFrame.getInstance().getCrupier().showPlayerCards(shNick, sraKeyB64);

                                                    // 2. Efecto Espejo: Si somos el Host, rebotamos la clave al resto de la red
                                                    if (GameFrame.getInstance().isPartida_local()) {
                                                        String rebroadcastCmd = "SHOWCARDS#" + partes_comando[3] + "#" + sraKeyB64;
                                                        // Le pasamos 'shNick' al final para excluir al jugador que originalmente envió el comando
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
                                                            // El testament es la clave SRA Unlock (32 bytes).
                                                            if (testament.length == 32) {
                                                                p.setSra_unlock(testament);
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
                                        default:
                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
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
                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, nick + " -> EXCEPCION AL PROCESAR ALGÚN COMANDO DE ESTE CLIENTE", ex);
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
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
        } else {
            this.exit = true;
        }
    }
}
