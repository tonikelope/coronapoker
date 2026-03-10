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

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import static com.tonikelope.coronapoker.Init.DEV_MODE;
import static com.tonikelope.coronapoker.WaitingRoomFrame.PING_INTERVAL_MS;
import static com.tonikelope.coronapoker.WaitingRoomFrame.POISON_PILL;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

/**
 *
 * @author tonikelope
 */
public class Participant implements Runnable {

    public static final int ASYNC_COMMAND_QUEUE_WAIT = 1000;
    public static final int RECIBIDO_TIMEOUT = 5000;

    private final Object ping_pong_lock = new Object();
    private final Object participant_socket_lock = new Object();
    private final HashMap<String, Integer> last_received = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pre_game_socket_writer_queue = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> socket_reader_queue = new LinkedBlockingQueue<>();
    private final WaitingRoomFrame sala_espera;
    private final String nick;
    private final File avatar;
    private byte[] panoptes_public_key = null;
    private byte[] panoptes_private_key = null; // Bots only

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
    private volatile SecretKeySpec permutation_key = null;
    private volatile String permutation_key_hash = null;
    private volatile int new_hand_ready = 0;
    private volatile boolean unsecure_player = false;
    private volatile boolean reset_socket = false;
    private volatile String avatar_chat_src;
    private volatile boolean async_wait = false;
    private volatile boolean force_reset_socket = false;
    private volatile int latency;
    private volatile int latency2;
    private volatile int pong_timeout_counter = 0;
    private volatile int pong2_timeout_counter = 0;

    // =========================================================
    // TOKENS DE CONSENSO Y AUTORIZACIÓN
    // =========================================================
    private byte[] token_flop = null;
    private byte[] token_turn = null;
    private byte[] token_river = null;
    private volatile byte[] received_token = null;

    public byte[] getToken_flop() {
        return token_flop;
    }

    public void setToken_flop(byte[] token_flop) {
        this.token_flop = token_flop;
    }

    public byte[] getToken_turn() {
        return token_turn;
    }

    public void setToken_turn(byte[] token_turn) {
        this.token_turn = token_turn;
    }

    public byte[] getToken_river() {
        return token_river;
    }

    public void setToken_river(byte[] token_river) {
        this.token_river = token_river;
    }

    public byte[] getReceived_token() {
        return received_token;
    }

    public void setReceived_token(byte[] received_token) {
        this.received_token = received_token;
    }
    // =========================================================

    // Semilla de entropía aportada por el cliente para el mazo
    private byte[] panoptes_hand_seed = null;

    public byte[] getPanoptes_hand_seed() {
        return panoptes_hand_seed;
    }

    public void setPanoptes_hand_seed(byte[] panoptes_hand_seed) {
        this.panoptes_hand_seed = panoptes_hand_seed;
    }

    // Fragmento de la Master Key aportado por el cliente en el Showdown
    private byte[] mk_share = null;

    public byte[] getMk_share() {
        return mk_share;
    }

    public void setMk_share(byte[] mk_share) {
        this.mk_share = mk_share;
    }

    private byte[] ephemeral_pub_key = null; // Server's ephemeral public key KEM (OUTPUT)
    private byte[] encrypted_cards = null; // AEAD encrypted envelope for showdown

    public byte[] getEphemeral_pub_key() {
        return ephemeral_pub_key;
    }

    public void setEphemeral_pub_key(byte[] ephemeral_pub_key) {
        this.ephemeral_pub_key = ephemeral_pub_key;
    }

    public byte[] getEncrypted_cards() {
        return encrypted_cards;
    }

    public void setEncrypted_cards(byte[] encrypted_cards) {
        this.encrypted_cards = encrypted_cards;
    }

    public byte[] getPanoptes_public_key() {
        return panoptes_public_key;
    }

    public void setPanoptes_public_key(byte[] pk) {
        this.panoptes_public_key = pk;
    }

    public byte[] getPanoptes_private_key() {
        return panoptes_private_key;
    }

    public void setPanoptes_private_key(byte[] pk) {
        this.panoptes_private_key = pk;
    }

    public int getLatency2() {
        return latency2;
    }

    public int getLatency() {
        return latency;
    }

    public Participant(WaitingRoomFrame espera, String nick, File avatar, Socket socket, SecretKeySpec aes_k,
            SecretKeySpec hmac_k, boolean cpu) {

        this.nick = nick;
        this.setSocket(socket);
        this.sala_espera = espera;
        this.avatar = avatar;
        this.cpu = cpu;
        this.aes_key = aes_k;
        this.hmac_key = hmac_k;
        this.hmac_key_orig = hmac_k;

        if (this.aes_key != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(this.nick.getBytes("UTF-8"));
                md.update(this.sala_espera.getServer_nick().getBytes("UTF-8"));
                md.update(this.aes_key.getEncoded());
                md.update(this.hmac_key.getEncoded());
                this.permutation_key = new SecretKeySpec(md.digest(), "AES");
                md = MessageDigest.getInstance("MD5");
                this.permutation_key_hash = Base64.getEncoder()
                        .encodeToString(md.digest(this.permutation_key.getEncoded()));
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (avatar != null) {
            try {
                // Guardamos una versión de 32x32 del avatar para el chat
                ImageIO.write(
                        Helpers.toBufferedImage(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage()
                                .getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage()),
                        "png", new File(avatar.getAbsolutePath() + "_chat"));
                avatar_chat_src = new File(avatar.getAbsolutePath() + "_chat").toURI().toURL().toExternalForm();
            } catch (IOException ex) {
                avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            avatar_chat_src = cpu ? getClass().getResource("/images/avatar_bot_chat.png").toExternalForm()
                    : getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
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

        // --- INICIO BLOQUE 3: HILO GENERADOR DE LATIDOS PANOPTES (CORREGIDO) ---
        Helpers.threadRun(() -> {
            Helpers.pausar(15000);

            Panoptes panoptes = Panoptes.getInstance();

            while (!this.exit && !this.isCpu()) {
                try {
                    // 1. El ID de la sesión lo creamos con la IP del Cliente (para distinguirlo de
                    // otros jugadores)
                    String remoteIp = this.socket.getInetAddress().getHostAddress();
                    int remotePort = this.socket.getPort();
                    String sessionId = remoteIp + ":" + remotePort + "_heartbeat";

                    // 2. PERO el reto se genera con la IP y Puerto del SERVIDOR,
                    // para que el cliente verifique que realmente está conectado a ti.
                    String serverLocalIp = this.socket.getLocalAddress().getHostAddress();
                    int serverLocalPort = this.socket.getLocalPort();

                    // Generamos el reto con los datos del servidor
                    byte[] challenge = panoptes.generateChallenge(sessionId, serverLocalIp, serverLocalPort);
                    String challengeBase64 = Base64.getEncoder().encodeToString(challenge);

                    // Enviamos el reto de seguridad al cliente
                    writeCommandFromServer(
                            Helpers.encryptCommand("SECPING#" + challengeBase64, this.aes_key, this.hmac_key));

                } catch (Exception e) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, "Error enviando SECPING", e);
                }

                // Pausa de 15 segundos hasta el siguiente latido
                Helpers.pausar(15000);
            }
        });
        // --- FIN BLOQUE 3 ---

        // Cada X segundos mandamos un PING al cliente
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
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "Error enviando PING", ex);
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
                }

                if (latency2 == -1) {
                    pong2_timeout_counter++;
                }

                if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().isPartida_empezada()
                        && GameFrame.getInstance() != null) {
                    RemotePlayer jugador = (RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player()
                            .get(nick);

                    if (jugador != null) {

                        if (latency != -1 && latency2 != -1) {

                            jugador.updateLatency(Translator.translate("Latencia:") + " " + String.valueOf(latency)
                                    + " ms (" + String.valueOf(pong_timeout_counter) + ") | " + String.valueOf(latency2)
                                    + " ms (" + String.valueOf(pong2_timeout_counter) + ")", false);

                        } else {

                            jugador.updateLatency(Translator.translate("Latencia:") + " "
                                    + (latency != -1 ? String.valueOf(latency) : "-") + " ms ("
                                    + String.valueOf(pong_timeout_counter) + ") | "
                                    + (latency2 != -1 ? String.valueOf(latency2) : "-") + " ms ("
                                    + String.valueOf(pong2_timeout_counter) + ")", true);
                        }
                    }
                }

                if (WaitingRoomFrame.getInstance() != null && !isCpu()
                        && (!WaitingRoomFrame.getInstance().isPartida_empezada()
                        || WaitingRoomFrame.getInstance().isVisible())) {

                    WaitingRoomFrame.getInstance().updateParticipantLatency(nick, latency, latency2);
                }

                if (!exit && WaitingRoomFrame.getInstance() != null) {

                    if (pong == null) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING,
                                "{0} NO RESPONDIÓ EL PING", nick);

                    } else if (pong != ping + 1) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "PONG DE {0} INCORRECTO",
                                nick);

                    } else if (pong2 == null) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING,
                                "{0} NO RESPONDIÓ EL PING2", nick);

                    } else if (pong2 != ping + 2) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "PONG2 DE {0} INCORRECTO",
                                nick);

                    } else if (DEV_MODE) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO,
                                "PONGS DE {0} RECIBIDOS CORRECTAMENTE. (Latencia: {1} ms / {2} ms)",
                                new Object[]{nick, latency, latency2});
                    }

                    Helpers.pausar(PING_INTERVAL_MS);
                }
            }
        });

    }

    private void runPreGameSocketWriterQueueThread() {
        // Creamos un hilo por cada participante para enviar comandos de juego con
        // confirmación y no bloquear el servidor por si se conectan nuevos usuarios
        Helpers.threadRun(() -> {
            while (!exit && !WaitingRoomFrame.getInstance().isExit()
                    && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                while (!exit && !WaitingRoomFrame.getInstance().isExit()
                        && !WaitingRoomFrame.getInstance().isPartida_empezada()
                        && !getPre_game_socket_writer_queue().isEmpty()) {

                    String command = getPre_game_socket_writer_queue().peek();

                    ArrayList<String> pendientes = new ArrayList<>();

                    pendientes.add(getNick());

                    do {
                        int id = Helpers.CSPRNG_GENERATOR.nextInt();

                        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                        if (!writeCommandFromServer(
                                Helpers.encryptCommand(full_command, getAes_key(), getHmac_key()))) {

                            waitPreGameCommandConfirmations(id, pendientes);

                            if (!pendientes.isEmpty()) {
                                Logger.getLogger(Participant.class.getName()).log(Level.WARNING,
                                        "{0} COMANDO ASYNC CONFIRMATION ERROR!", getNick());
                            }

                        } else {
                            Logger.getLogger(Participant.class.getName()).log(Level.WARNING,
                                    "{0} COMANDO ASYNC SOCKET ERROR!", getNick());
                        }

                    } while (!pendientes.isEmpty() && !exit && !WaitingRoomFrame.getInstance().isExit()
                            && !WaitingRoomFrame.getInstance().isPartida_empezada());

                    getPre_game_socket_writer_queue().poll();

                }

                synchronized (WaitingRoomFrame.getInstance().getLock_client_pre_game_commands_wait()) {
                    WaitingRoomFrame.getInstance().getLock_client_pre_game_commands_wait().notifyAll();
                }

                if (!exit && !WaitingRoomFrame.getInstance().isExit()
                        && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    synchronized (getPre_game_socket_writer_queue()) {

                        try {
                            getPre_game_socket_writer_queue().wait(ASYNC_COMMAND_QUEUE_WAIT);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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
                Helpers.mostrarMensajeInformativo(WaitingRoomFrame.getInstance(),
                        "[" + nick + "] " + Translator.translate(WaitingRoomFrame.getInstance().isServer()
                                ? "CUIDADO: el ejecutable del juego de este usuario es diferente\nEs posible que intente hacer trampas con una versión hackeada del juego (¿o eres tú el trampos@?)"
                                : "CUIDADO: el ejecutable del juego de este usuario es diferente\n(Es posible que intente hacer trampas con una versión hackeada del juego)"),
                        new ImageIcon(Init.class.getResource("/images/shield.png")));
            });

        }

        this.unsecure_player = val;

    }

    public String getPermutation_key_hash() {
        return permutation_key_hash;
    }

    public SecretKeySpec getPermutation_key() {
        return permutation_key;
    }

    public int getNew_hand_ready() {
        return new_hand_ready;
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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
            System.getLogger(Participant.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        return true;

    }

    public String readCommandFromClient() {

        while (resetting_socket || force_reset_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        synchronized (getInput_stream_reader()) {

            try {
                return Helpers.decryptCommand(getInput_stream_reader().readLine(), getAes_key(), getHmac_key());
            } catch (Exception ex) {
                System.getLogger(Participant.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
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
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
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

            } catch (Exception ex) {

                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);

                this.reset_socket = false;

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

        // Esperamos confirmación
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
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

        }

        return !pending.isEmpty();
    }

    private void runSocketReaderThread() {
        Helpers.threadRun(() -> {
            boolean timeout = false;
            while (!exit) {
                String mensaje_recibido = null;
                try {
                    mensaje_recibido = readCommandFromClient();
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, (String) null, ex);
                }

                if (mensaje_recibido != null) {
                    if (timeout) {
                        timeout = false;
                        GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(false);
                    }
                    String[] partes_comando = mensaje_recibido.split("#");
                    if ("PING".equals(partes_comando[0])) {
                        writeCommandFromServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                        try {
                            // Usamos la cola correcta del Participant
                            socket_reader_queue.put(mensaje_recibido);
                        } catch (InterruptedException ex) {
                            System.getLogger(Participant.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                        }
                    } else if ("SECPING".equals(partes_comando[0])) {
                        try {
                            byte[] challengeBytes = Base64.getDecoder().decode(partes_comando[1]);
                            byte[] signatureBytes = Panoptes.getInstance().signChallenge(challengeBytes);
                            String signatureBase64 = signatureBytes != null ? Base64.getEncoder().encodeToString(signatureBytes) : "";
                            writeCommandFromServer(Helpers.encryptCommand("SECPONG#" + signatureBase64, this.aes_key, this.hmac_key));
                        } catch (Exception e) {
                            Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, "Fallo al firmar el latido del cliente", e);
                        }
                    } else {
                        try {
                            socket_reader_queue.put(mensaje_recibido);
                        } catch (InterruptedException ex) {
                            System.getLogger(Participant.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                        }
                    }
                } else {
                    try {
                        if (!socket_reader_queue.contains(POISON_PILL)) {
                            socket_reader_queue.put(POISON_PILL);
                        }
                    } catch (InterruptedException ex) {
                        System.getLogger(Participant.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                }

                if (mensaje_recibido == null && !reset_socket && !exit && !WaitingRoomFrame.getInstance().isExit()
                        && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null
                        || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                    if (!timeout) {
                        timeout = true;
                        GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(true);

                        if (!this.force_reset_socket) {
                            try {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer(
                                        "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                        if (!reset_socket) {
                            synchronized (getParticipant_socket_lock()) {
                                try {
                                    getParticipant_socket_lock().wait((resetting_socket || force_reset_socket) ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    } else {
                        // ANTI-FLOOD: Si el socket está muerto, pausamos medio segundo para evitar el 100% de CPU
                        Helpers.pausar(500);
                    }
                }
            }
        });
    }

    @Override
    public void run() {
        if (socket != null) {
            runPreGameSocketWriterQueueThread();
            runPingPongThread();
            runSocketReaderThread();

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
                                if (partes_comando.length >= 5) {
                                    try {
                                        if (!partes_comando[1].equals("*")) {
                                            this.setMk_share(Base64.getDecoder().decode(partes_comando[1]));
                                        }
                                        if (!partes_comando[2].equals("*")) {
                                            this.setToken_flop(Base64.getDecoder().decode(partes_comando[2]));
                                        }
                                        if (!partes_comando[3].equals("*")) {
                                            this.setToken_turn(Base64.getDecoder().decode(partes_comando[3]));
                                        }
                                        if (!partes_comando[4].equals("*")) {
                                            this.setToken_river(Base64.getDecoder().decode(partes_comando[4]));
                                        }
                                    } catch (Exception e) {
                                    }
                                } else if (partes_comando.length > 1 && partes_comando[1].length() > 10) {
                                    try {
                                        this.setMk_share(Base64.getDecoder().decode(partes_comando[1]));
                                    } catch (Exception e) {
                                    }
                                }
                                exit = true;
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
                                this.writeCommandFromServer(("CONF#" + String.valueOf(command_id + 1) + "#OK"));
                                if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != command_id) {
                                    last_received.put(subcomando, command_id);

                                    switch (subcomando) {
                                        case "RADAR":
                                            Helpers.threadRun(() -> {
                                                try {
                                                    if (partes_comando.length == 5) {
                                                        String suspicious = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                        byte[] requesterPubKey = Base64.getDecoder().decode(partes_comando[4]);
                                                        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(suspicious)) {
                                                            GameFrame.getInstance().getLocalPlayer().RADAR(nick, requesterPubKey);
                                                        } else if (!GameFrame.getInstance().getParticipantes().get(suspicious).isCpu()) {
                                                            GameFrame.getInstance().getParticipantes().get(suspicious).writeGAMECommandFromServer("RADAR#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#" + partes_comando[4]);
                                                        }
                                                    } else if (partes_comando.length == 7) {
                                                        String requester = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(requester)) {
                                                            byte[] imageBytes = partes_comando[4].equals("*") ? null : Base64.getDecoder().decode(partes_comando[4]);
                                                            byte[] encryptedRadarData = partes_comando[5].equals("*") ? null : Base64.getDecoder().decode(partes_comando[5]);
                                                            long timestamp = Long.parseLong(partes_comando[6]);
                                                            StringBuilder sb = new StringBuilder();
                                                            sb.append("  ____                            ____       _                ____      _    ____    _    ____  \n"
                                                                    + " / ___|___  _ __ ___  _ __   __ _|  _ \\ ___ | | _____ _ __   |  _ \\    / \\  |  _ \\  / \\  |  _ \\ \n"
                                                                    + "| |   / _ \\| '__/ _ \\| '_ \\ / _` | |_) / _ \\| |/ / _ \\ '__| | |_) |  / _ \\ | | | |/ _ \\ | |_) |\n"
                                                                    + "| |__| (_) | | | (_) | | | | (_| |  __/ (_) |   <  __/ |    |  _ <  / ___ \\| |_| / ___ \\|  _ < \n"
                                                                    + " \\____\\___/|_|  \\___/|_| |_|\\__,_|_|   \\___/|_|\\_\\___|_|    |_| \\_\\/_/   \\_\\____/_/   \\_\\_| \\_\\\n"
                                                                    + "                                                                                               \n\n");
                                                            sb.append("CoronaPoker Radar -> [").append(nick).append("] ").append(Helpers.getFechaHoraActual()).append("\n\n");
                                                            if (encryptedRadarData != null) {
                                                                byte[] myPrivateKey = GameFrame.getInstance().getSala_espera().getLocal_player_private_key();
                                                                String rawIntel = Panoptes.getInstance().parseRadarReport(myPrivateKey, encryptedRadarData);
                                                                if (rawIntel != null) {
                                                                    sb.append(rawIntel);
                                                                } else {
                                                                    sb.append("************************************************************************\n");
                                                                    sb.append("[!] CRITICAL SECURITY ERROR: INTEGRITY CHECK FAILED (MAC POLY1305)\n");
                                                                    sb.append("Packet was altered in transit or Chaos/KEM signature is invalid.\n");
                                                                    sb.append("************************************************************************\n");
                                                                }
                                                            } else {
                                                                sb.append("[!] No encrypted process data received.\n");
                                                            }
                                                            GameFrame.getInstance().getCrupier().saveRADARLog(nick, imageBytes, sb.toString(), timestamp);
                                                        } else {
                                                            GameFrame.getInstance().getParticipantes().get(requester).writeGAMECommandFromServer("RADAR#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#" + partes_comando[4] + "#" + partes_comando[5] + "#" + partes_comando[6]);
                                                        }
                                                    }
                                                } catch (Exception ex) {
                                                    ex.printStackTrace();
                                                }
                                            });
                                            break;
                                        case "PERMUTATIONKEY":
                                            Helpers.threadRun(() -> {
                                                synchronized (GameFrame.getInstance().getCrupier().getPermutation_key_lock()) {
                                                    GameFrame.getInstance().getCrupier().setPermutation_key(partes_comando[3]);
                                                    GameFrame.getInstance().getCrupier().getPermutation_key_lock().notifyAll();
                                                }
                                            });
                                            break;
                                        case "PAUSE":
                                            Helpers.threadRun(() -> {
                                                synchronized (GameFrame.getInstance().getLock_pause()) {
                                                    if (("0".equals(partes_comando[3]) && GameFrame.getInstance().isTimba_pausada()) && nick.equals(GameFrame.getInstance().getNick_pause()) || ("1".equals(partes_comando[3]) && !GameFrame.getInstance().isTimba_pausada())) {
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
                                        case "SHOWMYCARDS":
                                            Helpers.threadRun(() -> {
                                                GameFrame.getInstance().getCrupier().showAndBroadcastPlayerCards(nick);
                                            });
                                            break;
                                        case "NEWHANDREADY":
                                            try {
                                                this.new_hand_ready = Integer.parseInt(partes_comando[3]);
                                                if (partes_comando.length > 4) {
                                                    byte[] clientSeed = Base64.getDecoder().decode(partes_comando[4]);
                                                    this.setPanoptes_hand_seed(clientSeed);
                                                }
                                            } catch (Exception e) {
                                            }
                                            synchronized (GameFrame.getInstance().getCrupier().getLock_nueva_mano()) {
                                                GameFrame.getInstance().getCrupier().getLock_nueva_mano().notifyAll();
                                            }
                                            break;
                                        case "EXIT":
                                            // Detect if we are in the Waiting Room or in the Game
                                            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                                if (partes_comando.length >= 7) {
                                                    try {
                                                        if (!partes_comando[3].equals("*")) {
                                                            this.setMk_share(Base64.getDecoder().decode(partes_comando[3]));
                                                        }
                                                        if (!partes_comando[4].equals("*")) {
                                                            this.setToken_flop(Base64.getDecoder().decode(partes_comando[4]));
                                                        }
                                                        if (!partes_comando[5].equals("*")) {
                                                            this.setToken_turn(Base64.getDecoder().decode(partes_comando[5]));
                                                        }
                                                        if (!partes_comando[6].equals("*")) {
                                                            this.setToken_river(Base64.getDecoder().decode(partes_comando[6]));
                                                        }
                                                    } catch (Exception e) {
                                                    }
                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nick);
                                                } else if (partes_comando.length == 4) {
                                                    try {
                                                        String exiting_nick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                        GameFrame.getInstance().getCrupier().remotePlayerQuit(exiting_nick);
                                                    } catch (Exception e) {
                                                    }
                                                    // Broadcast received, we don't kill our own thread
                                                    break;
                                                } else {
                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nick);
                                                }
                                            } else {
                                                // The game hasn't started, safely remove from WaitingRoomFrame
                                                if (sala_espera != null) {
                                                    sala_espera.borrarParticipante(this.nick);
                                                }
                                            }

                                            // Only the exiting client itself triggers its thread exit
                                            if (partes_comando.length != 4) {
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
                    } else {
                        // If socket is dead, we break the loop immediately to avoid zombies
                        exit = true;
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
