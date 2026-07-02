/*
 * Copyright (C) 2026 tonikelope
 *
 * Zero-Trust Pure Java SRA Cryptographic Engine.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import static com.tonikelope.coronapoker.WaitingRoomFrame.PING_INTERVAL_MS;
import static com.tonikelope.coronapoker.WaitingRoomFrame.POISON_PILL;
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
    // exit=true.
    //
    // Grace base del host tras detectar la caida de un peer (null-read), cuando
    // todavia NO ha llegado ningun intento de reconexion autenticado.
    //   RECIBIDO_TIMEOUT      = 45s -> ANCLADO a la ventana de deteccion peor-caso
    //       del socket mudo: MAX_CONSECUTIVE_PING_FAILURES * (PING_INTERVAL_MS +
    //       PING_PONG_TIMEOUT) = 3 * (5s + 10s) = 45s. Hacer el grace >= esa ventana
    //       garantiza que el host no se rinda ANTES de que el propio peer pueda
    //       siquiera enterarse de que se cayo y empezar a reconectar (antes eran
    //       40s, justo por debajo de los 45s de deteccion).
    //   CLIENT_RECON_TIMEOUT  = 80s (constante propia, INDEPENDIENTE del tiempo de pensar) ->
    //       grace EXTENDIDO tras un intent autenticado / umbral del dialogo manual en el cliente.
    //
    // Si durante el grace base el cliente consigue abrir socket + handshake + HMAC
    // contra hmac_key_orig (intent autenticado criptograficamente),
    // signalReconnectIntent() refresca el deadline a CLIENT_RECON_TIMEOUT desde ese
    // momento, dando al peer legitimo tiempo a completar resetSocket aunque el
    // handshake tarde en redes lentas.
    public static final int RECIBIDO_TIMEOUT = 45000;

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
    // Generación del "force reconnect": cada forceSocketReconnectWithWatchdog la
    // incrementa (bajo lock). El watchdog captura la suya al arrancar y solo actúa si
    // sigue vigente -> un watchdog viejo no expulsa un peer por un force MÁS NUEVO
    // (p.ej. doble clic del menú "Forzar reconexión" dentro del grace).
    private volatile int force_reset_generation = 0;
    private volatile int latency;
    private volatile int latency2;
    private volatile int pong_timeout_counter = 0;
    private volatile int pong2_timeout_counter = 0;
    // Telemetría: cuenta de reconexiones exitosas del peer al server.
    // Incrementado en resetSocket() tras setear reset_socket=true. Cubre tanto
    // reconexiones naturales (peer cae + vuelve) como las forzadas vía menú
    // "FORZAR RECONEXIÓN" — ambas indican inestabilidad de enlace observable.
    private volatile int reconnection_count = 0;
    private volatile byte[] received_token = null;
    private volatile int new_hand_ready = 0;

    // Suelo de deadline para el wait de grace en runSocketReaderThread.
    // signalReconnectIntent() lo eleva a now()+CLIENT_RECON_TIMEOUT cuando
    // un intento de reconexion entrante valida HMAC contra hmac_key_orig:
    // el reader, al rearmarse el wait, usara max(deadline_actual, grace_deadline_floor)
    // y asi el grace se prolonga el tiempo necesario para que el cliente
    // legitimo complete el handshake aunque la red sea lenta. Solo crece,
    // nunca decrece (monotonico).
    private volatile long grace_deadline_floor = 0L;

    // Marca si runPingPongThread está corriendo. El thread se mata
    // intencionalmente con break tras socketClose() cuando el peer pierde
    // MAX_CONSECUTIVE_PING_FAILURES PONGs; si después el peer reconecta y
    // resetSocket repone el canal, los contadores se resetean pero el thread
    // sigue muerto -> el peer queda sin supervisión activa via PING hasta que su
    // socket vuelva a fallar via write (o nunca, si nadie escribe). resetSocket
    // comprueba este flag y relanza el thread cuando hace falta. (Enfoque del
    // commit local 5e5a9734 del autor, integrado sobre el resetSocket refactor.)
    private volatile boolean ping_pong_thread_alive = false;

    // --- SRA ZERO-TRUST VARIABLES ---
    // sra_unlock: scalar para POCKET pieces. Antes era la única clave del peer;
    // tras el refactor dual-lock (Opción G) sigue siendo válido para pockets
    // pero NUNCA debe entregarse vía testamento — su exposición permitiría al
    // host descifrar las pocket cards del peer que sale.
    private volatile byte[] sra_unlock = null;
    // sra_unlock_community: scalar para community pieces tras la fase de
    // rotación. Es la única mitad que se incluye en el testamento al hacer
    // EXIT, así el juego puede continuar revelando comunitarias sin exponer
    // pockets.
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
     * Telemetría: nº de reconexiones exitosas de este peer al server
     * desde que se creó el Participant (inicio de partida o entrada a la sala).
     * Sólo cuenta reconexiones que llegaron hasta reset_socket=true.
     */
    public int getReconnectionCount() {
        return reconnection_count;
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
                // The peer's avatar lives in tmpdir; its _chat thumbnail is a
                // sibling temp file. Clean it up on exit (the base avatar is
                // already deleteOnExit-registered at its creation site).
                File avatar_chat = new File(avatar.getAbsolutePath() + "_chat");
                avatar_chat.deleteOnExit();
                ImageIO.write(Helpers.toBufferedImage(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage()), "png", avatar_chat);
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
                        // Re-check dentro del monitor antes de dormir: un PONG que llega
                        // entre la condición del while y la toma del lock perdería su
                        // notify y dormiríamos hasta PING_PONG_TIMEOUT completo (lo que
                        // puede disparar un cierre de socket espurio). El guard remaining>0
                        // evita además wait(0)=espera indefinida y wait(<0)=IAE en la
                        // ventana de carrera del cálculo del tiempo restante.
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

                // Red de seguridad para sockets "mudos" (peer killed sin RST, particion
                // unidireccional, GC stall infinito). La via primaria sigue siendo la
                // IOException en writeCommandFromServer , pero si el peer
                // SOLO recibe sin que nadie le escriba nada distinto al PING, ese write
                // del PING SI dispara IOException... a menos que el SO mantenga el envio
                // bufferizado sin ack y no genere error. En ese borde, este threshold
                // (N=3 PONGs perdidos consecutivos) cierra el socket por nuestra cuenta
                // y deja que runSocketReaderThread entre por la via de grace normal.
                //
                // Guarda anti-race: si estamos en mitad de un resetSocket/forceSocketReconnect
                // los contadores pueden estar acumulados contra el socket viejo. Cerrar
                // ahora cerraria el socket nuevo recien instalado por error.
                if (!exit && !resetting_socket && !force_reset_socket
                        && (pong_timeout_counter >= WaitingRoomFrame.MAX_CONSECUTIVE_PING_FAILURES
                        || pong2_timeout_counter >= WaitingRoomFrame.MAX_CONSECUTIVE_PING_FAILURES)) {
                    LOGGER.log(Level.WARNING,
                            "PEER: Participant {0} lost {1}/{2} consecutive PONGs — closing socket",
                            new Object[]{nick, pong_timeout_counter, pong2_timeout_counter});
                    // alive=false ANTES de cerrar: así la resurrección de resetSocket ve el
                    // thread muerto y lo relanza. Sin esto, en la ventana break->finally el
                    // chequeo veía alive=true y se saltaba la resurrección. El finally lo
                    // vuelve a poner false (idempotente).
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
                        setPlayerTimeoutSafe(false);
                    }
                    String[] partes_comando = mensaje_recibido.split("#");
                    if (null == partes_comando[0]) {
                        try {
                            socket_reader_queue.put(mensaje_recibido);
                        } catch (Exception ex) {
                        }
                    } else {
                        switch (partes_comando[0]) {
                            // Control frames mal formados (PING/PONG/PONG2 sin el
                            // contador numérico) NO deben tumbar este hilo lector:
                            // un Integer.parseInt sobre un frame corrupto lanzaba
                            // NumberFormatException/AIOOBE que rompía el reader y
                            // dejaba al peer ZOMBIE — sin el markExitAndNotify ni el
                            // broadcast TIMEOUT del camino de desconexión normal, así
                            // que el resto de la mesa seguía esperándolo. Un peer
                            // honesto (misma versión) siempre manda el contador;
                            // ignorar el frame corrupto es estrictamente más seguro
                            // que matar la conexión a medias.
                            case "PING":
                                if (partes_comando.length >= 2) {
                                    try {
                                        writeCommandFromServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                    } catch (NumberFormatException nfe) {
                                    }
                                }
                                try {
                                    socket_reader_queue.put(mensaje_recibido);
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

                // One-shot reset_socket: si este null es el socket VIEJO cerrándose por la
                // reconexión (resetSocket puso reset_socket=true bajo lock), lo consumimos
                // AQUÍ —en el reader, que es quien reacciona a la señal— y saltamos el
                // manejo de caída. Antes lo limpiaba run() (otro hilo, sin lock): si lo
                // borraba entre el set de resetSocket y este chequeo, la señal se perdía y
                // un peer YA reconectado era expulsado tras esperar el grace completo. El
                // grace wait de abajo (bajo lock) sigue siendo la red de seguridad si la
                // reconexión llega DURANTE la espera.
                if (mensaje_recibido == null && reset_socket) {
                    reset_socket = false;
                } else if (mensaje_recibido == null && !exit && !WaitingRoomFrame.getInstance().isExit()
                        && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null
                        || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                    if (!timeout) {
                        timeout = true;
                        setPlayerTimeoutSafe(true);

                        long graceMs = (resetting_socket || force_reset_socket) ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT;
                        LOGGER.log(Level.INFO, "PEER: Participant {0} entered TIMEOUT state — waiting {1}ms for reconnect", new Object[]{nick, graceMs});

                        if (!this.force_reset_socket) {
                            try {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (Exception ex) {
                            }
                        }

                        // Wait con deadline rearmable: signalReconnectIntent() puede
                        // elevar grace_deadline_floor durante este wait y el bucle
                        // recogera la extension en la siguiente iteracion. Asi un
                        // peer con red lenta que tarda mas que el grace base en
                        // completar el handshake no es expulsado mientras siga
                        // demostrando criptograficamente su identidad.
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
                                // Si salimos del grace porque LLEGÓ el reconnect
                                // (reset_socket=true), consumimos la señal aquí: ya
                                // cumplió su función (sacarnos de la espera). Si no se
                                // consume, persiste y el one-shot de arriba se comería
                                // el primer null del SIGUIENTE drop real como si fuera
                                // el cierre del socket viejo —retrasando la detección
                                // de esa caída una iteración del reader—. resetSocket ya
                                // no depende de reset_socket (usa su 'ok' local), así
                                // que limpiarlo aquí no afecta a su resultado.
                                if (reset_socket) {
                                    reset_socket = false;
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
            // Socket cerrado / peer caido detectado en el write. markExitAndNotify
            // marca Participant.exit Y Player.exit Y despierta los waits del
            // Crupier sobre received_commands. Sin propagar a Player.exit, el
            // do-while que espera DECISION (Crupier.java ~5476) queda colgado
            // porque chequea jugador.isExit() = Player, no Participant.
            if (!exit && !resetting_socket && !force_reset_socket) {
                markExitAndNotify("write failed (socket closed)");
            }
        }
        return true;
    }

    /**
     * Binary sibling of {@link #writeCommandFromServer(String)}: writes a binary
     * {@link WireFrame} (a voice/avatar blob) to this peer. Synchronizes on the same
     * OutputStream monitor as the text writers, so a binary frame and a text line can
     * never interleave on the socket.
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
            if (!exit && !resetting_socket && !force_reset_socket) {
                markExitAndNotify("binary write failed (socket closed)");
            }
        }
        return true;
    }

    /**
     * Marca este Participant como exit=true Y propaga al Player asociado
     * (RemotePlayer.setExit), notifica todos los waits posibles, y despierta
     * la queue de comandos del Crupier para que cualquier wait que espere
     * DECISION/ACTION/RESP_SRA_UNLOCK del peer caido salga inmediatamente.
     *
     * Sin esto, marcar solo Participant.exit dejaba el bucle del Crupier
     * (que checa Player.isExit, no Participant.isExit) colgado indefinido,
     * y el wait sobre received_commands sin notificar.
     */
    // Marca/desmarca el indicador visual de TIMEOUT del jugador de este nick,
    // tolerando que TODAVIA no exista GameFrame/Crupier/Player (caida PRE-partida,
    // o jugador ya retirado del mapa). Antes el reader hacia
    // GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(...)
    // a pelo: una caida no-limpia en la sala de espera (GameFrame==null) o un nick
    // ya borrado lanzaba NPE que MATABA el hilo lector -> el peer quedaba zombie
    // (sin grace, sin broadcast TIMEOUT, y el resto de la mesa esperandolo).
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
    }

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
                    return Helpers.decryptCommand(frame.text(), getAes_key(), getHmac_key());
                }
            } catch (Exception ex) {
            }
        }
        return null;
    }

    /**
     * Decrypts and dispatches a binary frame received from this peer. A voice note is
     * attributed to the connection's AUTHENTICATED nick (never the frame's claimed
     * nick), preserving the anti-spoof guarantee of the legacy VOICEMSG text path.
     * A malformed or HMAC-failing frame is dropped without tearing down the reader.
     */
    private void handleBinaryFromClient(byte[] frameBody) {
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
     * Senaliza que ha llegado un intento de reconexion autenticado
     * criptograficamente (HMAC del nick contra hmac_key_orig verificado).
     * Eleva grace_deadline_floor a now()+CLIENT_RECON_TIMEOUT (monotonico)
     * y despierta el wait del reader para que rearme su deadline al
     * nuevo suelo.
     *
     * Solo debe llamarse desde serverSocketHandler una vez verificada
     * la identidad: jamas con HMAC invalido, jamas por simple coincidencia
     * de IP. Asi un peer caido pero con su clave de sesion original puede
     * extender el grace todas las veces que necesite mientras reintenta
     * el handshake, sin que un atacante externo pueda hacerlo.
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

    // Sincronizado en participant_socket_lock: cierra el socket actual (y un
    // recon_socket pendiente si lo hubiera) y marca force_reset_socket. Antes
    // mutaba socket/recon_socket/force_reset_socket SIN lock, lo que carreaba con
    // resetSocket y con el menú "Forzar reconexión" (que lo llama en paralelo sobre
    // todos los peers). Re-entrante: resetSocket lo invoca teniendo ya el lock.
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

    // Llamado por el menú "Forzar reconexión" del host (GameFrame). Cierra el socket
    // para forzar al peer a reconectar Y arranca un watchdog: si el peer NO vuelve
    // dentro del grace (CLIENT_RECON_TIMEOUT), libera force_reset_socket y da el peer
    // por perdido. Sin esto, un peer forzado que no reconecta dejaba
    // readCommandFromClient (el propio hilo lector), writers y getters BLOQUEADOS PARA
    // SIEMPRE en while((resetting_socket||force_reset_socket)&&!exit): force_reset_socket
    // solo lo limpia un resetSocket exitoso (que aquí no ocurre) y el lector, bloqueado
    // en esa espera, jamás llega a markExit -> exit nunca se ponía. El watchdog es
    // daemon: no retiene la salida de la JVM aunque esté durmiendo el grace.
    public void forceSocketReconnectWithWatchdog() {
        final int myGen;
        synchronized (getParticipant_socket_lock()) {
            forceSocketReconnect();
            myGen = ++force_reset_generation;
        }
        Thread wd = new Thread(() -> {
            Helpers.pausar(GameFrame.CLIENT_RECON_TIMEOUT);
            boolean giveUp = false;
            // Decisión BAJO el lock: si hay una reconexión en curso, resetSocket tiene el
            // lock y esperamos a que acabe; si tuvo éxito, force_reset_socket ya es false
            // -> no damos el peer por perdido (cierra el boundary race con resetSocket).
            // force_reset_generation==myGen evita que este watchdog actúe sobre un force
            // MÁS NUEVO (doble clic del menú dentro del grace, reconectando ya).
            synchronized (getParticipant_socket_lock()) {
                if (force_reset_socket && !reset_socket && !exit && force_reset_generation == myGen) {
                    giveUp = true;
                }
            }
            // markExitAndNotify FUERA del lock (adquiere otros monitores; evitamos anidar),
            // solo si la decisión bajo lock dijo giveUp.
            if (giveUp) {
                LOGGER.log(Level.WARNING, "PEER: Participant {0} forced-reconnect watchdog: peer did not return within grace, giving up", nick);
                // markExit PRIMERO (pone exit=true y notifica) ANTES de limpiar los flags.
                // Si limpiáramos force_reset_socket/resetting_socket con exit aún en false,
                // el reader —despertado por ese notifyAll— vería los flags a false y exit a
                // false y entraría en el ramo de TIMEOUT, emitiendo un broadcast TIMEOUT
                // espurio de un peer que YA damos por perdido. Con exit=true primero, su
                // else-if (que exige !exit) no entra. Los loops de transporte
                // while((resetting||force)&&!exit) salen igual por exit con el notifyAll de
                // markExit; el clear de abajo es solo higiene de estado (no dejar los flags
                // a true en un peer muerto) y por eso no necesita su propio notifyAll.
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

    public boolean resetSocket(Socket sock, SecretKeySpec aes_k, SecretKeySpec hmac_k) {
        // TODO el swap bajo participant_socket_lock. Antes el prólogo (resetting_socket,
        // forceSocketReconnect, recon_socket) corría FUERA del lock: dos reconexiones
        // concurrentes del MISMO nick, o el menú "Forzar reconexión" en paralelo,
        // interleaveaban y dejaban socket/stream/keys inconsistentes (NPE sobre
        // recon_socket==null, claves cruzadas). Ahora es atómico: la última reconexión
        // gana limpiamente, sin corromper el estado del Participant.
        synchronized (getParticipant_socket_lock()) {
            // TOCTOU: si el peer ya está saliendo (grace expirado / markExitAndNotify
            // llamado, pero aún no borrado del mapa participantes) NO aceptamos el
            // reconnect — el handler lo deniega y run() termina de retirarlo. Sin esto,
            // se instalaba el socket nuevo + RECONNECT_OK y acto seguido run() borraba
            // al jugador: el cliente se creía dentro y el host lo había echado (+leak).
            if (exit) {
                LOGGER.log(Level.WARNING, "PEER: Participant {0} resetSocket refused — peer is exiting", nick);
                return false;
            }
            this.resetting_socket = true;
            forceSocketReconnect();
            this.recon_socket = sock;
            // ok: resultado LOCAL del reset, INMUNE a que el reader limpie this.reset_socket
            // (su one-shot, SIN lock) en la ventana entre que lo ponemos a true y el
            // return/resurrección de aquí. Sin esto, ese clear concurrente hacía que
            // resetSocket devolviera false -> el handler mandaba RESET_FAIL y cerraba el
            // socket NUEVO de un reconnect que SÍ tuvo éxito (y se saltaba la resurrección).
            boolean ok = false;
            try {
                // Swap transaccional: construimos el stream nuevo ANTES de comprometer
                // socket/keys. Si getInputStream() lanza, this.socket/stream/keys quedan
                // como estaban (viejos), no a medio cambiar (socket nuevo + stream viejo).
                BufferedInputStream nuevo_stream = new BufferedInputStream(this.recon_socket.getInputStream());
                this.socket = this.recon_socket;
                this.input_stream_reader = nuevo_stream;
                this.aes_key = aes_k;
                this.hmac_key = hmac_k;
                if (!isForce_reset_socket()) {
                    Audio.playWavResource("misc/yahoo.wav");
                }
                this.reset_socket = true;
                ok = true;
                // Telemetría: contador por peer de reconexiones exitosas.
                // Sólo se incrementa cuando llegamos aquí (reset_socket=true ya
                // garantiza que el socket nuevo está instalado y los streams
                // listos). El TELEMETRY broadcast del host expone este valor
                // a todos los clientes para mostrar inestabilidad de enlace.
                this.reconnection_count++;
                // Reseteo de contadores ping defensivo: si llevaban fallos acumulados
                // contra el socket viejo, el primer fail contra el nuevo (que puede
                // ser legitimo por jitter post-reconexion) no debe alcanzar el
                // threshold ni cerrar el socket recien instalado.
                this.pong_timeout_counter = 0;
                this.pong2_timeout_counter = 0;
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
            // Si el ping defensivo murió por socketClose+break (threshold superado
            // contra el socket viejo) lo resucitamos tras un reset OK: sin esto el peer
            // reconectado queda sin supervisión activa via PING (si el socket nuevo se
            // queda mudo, nadie lo detecta hasta un write fail, que con grace activo
            // puede no markar exit). reset_socket=true: no relanzar si el reset falló;
            // !exit: no resucitar peers ya expulsados.
            if (ok && !this.exit && !this.ping_pong_thread_alive) {
                LOGGER.log(Level.INFO, "PEER: Participant {0} runPingPongThread was dead after reset — resurrecting", nick);
                runPingPongThread();
            }
            return ok;
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
                // reset_socket ya NO se limpia aquí: lo consume el reader (dueño de la
                // señal) en su null-read. Limpiarlo desde ESTE hilo, sin lock, perdía la
                // señal de reconexión y expulsaba peers ya reconectados. Ver
                // runSocketReaderThread (one-shot reset_socket).
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
                                                    // PHASE A.1: la sig Ed25519 acompaña a la SRA key. El host NO puede
                                                    // modificarla — es la prueba de que viene de la privkey del nick.
                                                    String sigB64 = (partes_comando.length >= 6) ? partes_comando[5] : null;

                                                    // 1. El servidor verifica firma + descifra localmente
                                                    GameFrame.getInstance().getCrupier().showPlayerCards(shNick, sraKeyB64, sigB64);

                                                    // 2. Efecto Espejo: rebotamos el SHOWCARDS al resto incluyendo la sig
                                                    // intacta. El host NO la altera — los receptores re-verifican.
                                                    if (GameFrame.getInstance().isPartida_local()) {
                                                        String sigPart = (sigB64 != null) ? sigB64 : "*";
                                                        String rebroadcastCmd = "SHOWCARDS#" + partes_comando[3] + "#" + sraKeyB64 + "#" + sigPart;
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
                                                            // Dual-lock: el testamento entrega SOLO la mitad community.
                                                            // La mitad pocket del peer que sale permanece secreta.
                                                            if (testament.length == 32) {
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
                                                        GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
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
                                            // ZERO-TRUST: la respuesta de straddle voluntario debe pertenecer
                                            // al nick que posee ESTA conexión autenticada. Si no, un peer podría
                                            // falsificar el RESP del UTG y forzarle a postear un straddle (2x la
                                            // ciega grande de SU stack) que nunca aceptó. Mismo guard que HANDVERIFY.
                                            try {
                                                if (partes_comando.length >= 5
                                                        && new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8").equals(this.nick)) {
                                                    synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                        GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
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
            // Cierra el FD del socket en el teardown: antes el camino terminal de run()
            // no lo cerraba y, en un EXIT limpio (el peer mandó EXIT y nuestro lado nunca
            // cerró), el descriptor quedaba half-open hasta GC/RST -> fuga de FDs en
            // sesiones largas con muchas altas/bajas. socketClose() es idempotente.
            socketClose();
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
        } else {
            this.exit = true;
        }
    }
}
