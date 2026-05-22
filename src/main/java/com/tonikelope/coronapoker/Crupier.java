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

import com.drew.imaging.ImageProcessingException;
import static com.tonikelope.coronapoker.Card.BARAJAS;
import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import java.awt.Color;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope This croupier does too many things, but at least it does
 * them well.
 */
public class Crupier implements Runnable, com.tonikelope.coronapoker.bot.context.DealerView {

    private static final Logger LOGGER = Logger.getLogger(Crupier.class.getName());

    public static final boolean ALLIN_BOT_TEST = false; // TRUE FOR TESTING (Init.DEV_MODE MUST BE TRUE)

    public static final String[] STREETS = new String[]{"Preflop", "Flop", "Turn", "River"};

    public static final Map.Entry<String, Object[][]> ALLIN_CINEMATICS = new HashMap.SimpleEntry<>("allin/",
            new Object[][]{
                {"rounders.gif"},
                {"hulk.gif"},
                {"nicolas_cage.gif"},
                {"nicolas_cage2.gif"},
                {"training_day.gif"},
                {"wallstreet.gif"},
                {"casinoroyale.gif"},
                {"joker.gif"},
                {"terminator2.gif"}
            });

    public static volatile Map.Entry<String, Object[][]> ALLIN_CINEMATICS_MOD = null;

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/allin/",
            new String[]{
                "dolor.wav",
                "final_flash.wav",
                "follon.wav",
                "kbill_allin.wav",
                "maximo.wav",
                "mclane.wav",
                "montoya.wav",
                "pecho.wav",
                "puteado.wav",
                "sonrie.wav",
                "vanidoso.wav",
                "vietnam.wav"});

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/allin/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> ALLIN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> ALLIN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/fold/",
            new String[]{
                "fary.wav",
                "mamar_pollas.wav",
                "maricon.wav",
                "marines.wav",
                "mcfly.wav",
                "mierda_alta.wav",
                "percibo_miedo.wav"});

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/fold/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> FOLD_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> FOLD_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/showdown/",
            new String[]{
                "berto.wav",
                "bond.wav",
                "kbill_show.wav"});

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/showdown/",
            new String[]{
                "berto.wav",
                "bond.wav",
                "kbill_show.wav"});

    public static final Map<String, Map.Entry<String, String[]>> SHOWDOWN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> SHOWDOWN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/winner/",
            new String[]{
                "ateam.wav",
                "divertido.wav",
                "dura.wav",
                "fisuras.wav",
                "lacasitos.wav",
                "nadie_te_aguanta.wav",
                "planesbien.wav",
                "reymundo.wav",
                "vivarey.wav"});

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/winner/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> WINNER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> WINNER_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_ES = new HashMap.SimpleEntry<>("joke/es/loser/",
            new String[]{
                "afregar.wav",
                "bambi.wav",
                "elgolpe.wav",
                "encargado.wav",
                "hammond.wav",
                "matias.wav",
                "mierda.wav",
                "nomejodas.wav",
                "pasta.wav",
                "presion.wav",
                "primo.wav",
                "quecabron.wav",
                "vamos_no_me_jodas.wav"});

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_EN = new HashMap.SimpleEntry<>("joke/en/loser/",
            new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> LOSER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> LOSER_SOUNDS_MOD = null;

    public static final int GIF_SHUFFLE_ANIMATION_TIMEOUT = 1500;

    static {

        ALLIN_SOUNDS.put("es", ALLIN_SOUNDS_ES);
        FOLD_SOUNDS.put("es", FOLD_SOUNDS_ES);
        WINNER_SOUNDS.put("es", WINNER_SOUNDS_ES);
        LOSER_SOUNDS.put("es", LOSER_SOUNDS_ES);
        SHOWDOWN_SOUNDS.put("es", SHOWDOWN_SOUNDS_ES);

        ALLIN_SOUNDS.put("en", ALLIN_SOUNDS_EN);
        FOLD_SOUNDS.put("en", FOLD_SOUNDS_EN);
        WINNER_SOUNDS.put("en", WINNER_SOUNDS_EN);
        LOSER_SOUNDS.put("en", LOSER_SOUNDS_EN);
        SHOWDOWN_SOUNDS.put("en", SHOWDOWN_SOUNDS_EN);
    }

    public static final int CARTAS_MAX = 5;
    public static final int CARTAS_ESCALERA = 5;
    public static final int CARTAS_COLOR = 5;
    public static final int CARTAS_POKER = 4;
    public static final int CARTAS_TRIO = 3;
    public static final int CARTAS_PAREJA = 2;
    public static final int PREFLOP = 1;
    public static final int FLOP = 2;
    public static final int TURN = 3;
    public static final int RIVER = 4;
    public static final int SHOWDOWN = 5;
    public static final int REPARTIR_PAUSA = 250; // 2 players
    public static final int CARD_ANIMATION_DELAY = 100;
    public static final int SHUFFLE_ANIMATION_DELAY = 250;
    public static final int MIN_ULTIMA_CARTA_JUGADA = Hand.TRIO;
    public static final float[][] CIEGAS = new float[][]{new float[]{0.1f, 0.2f}, new float[]{0.2f, 0.4f},
    new float[]{0.3f, 0.6f}, new float[]{0.5f, 1.0f}};
    public static volatile boolean FUSION_MOD_SOUNDS = true;
    public static volatile boolean FUSION_MOD_CINEMATICS = true;
    public static final int NEW_HAND_READY_WAIT = 1000;
    public static final int PAUSA_DESTAPAR_CARTA = 1000;
    public static final int PAUSA_DESTAPAR_CARTA_ALLIN = 2000;
    public static final int TIEMPO_PENSAR = 40; // Segundos
    public static final int PAUSA_ENTRE_MANOS = 10; // Segundos
    public static final int PAUSA_ENTRE_MANOS_TEST = 1;
    public static final int PAUSA_ANTES_DE_SHOWDOWN = 1; // Segundos
    public static final int NEW_HAND_READY_WAIT_TIMEOUT = 30000;
    public static final int IWTSTH_ANTI_FLOOD_TIME = 15 * 60 * 1000; // 15 minutes BAN
    public static final boolean IWTSTH_BLINKING = true;
    public static final int IWTSTH_TIMEOUT = 15000;
    public static final int MONTECARLO_ITERATIONS = 1000;// Suficiente para tener un compromiso entre
    // velocidad/precisión
    public static final int RABBIT_LABEL_TIMEOUT = 3000;

    public static volatile boolean SECURITY_LOCKDOWN = false;

    public void triggerSecurityLockdown(String reason) {
        if (!Crupier.SECURITY_LOCKDOWN) {
            Crupier.SECURITY_LOCKDOWN = true;
            // Despierta a cualquier handler bloqueado en
            // awaitStreetForUnlockPhase para que vea el lockdown y aborte
            // sin esperar al timeout.
            synchronized (protocol_state_lock) {
                protocol_state_lock.notifyAll();
            }
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.security_alert") + " " + reason);
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.lockdown_activated"));

            // Si somos cliente, cerramos el socket con el host inmediatamente.
            // En lockdown el cliente refusa cualquier REQ_SRA_UNLOCK_BATCH siguiente
            // y la cascade SRA no tiene timeout artificial, así que sin cierre
            // el host queda esperando respuesta indefinidamente. Cerrar el
            // socket fuerza al host a detectar peer caído por SocketException
            // → exit=true → cascade falla limpio → MISDEAL → abortToRecover →
            // SERVEREXITRECOVER al resto del ring.
            if (!GameFrame.getInstance().isPartida_local()) {
                WaitingRoomFrame wrf = WaitingRoomFrame.getInstance();
                if (wrf != null) {
                    try {
                        wrf.closeClientSocket();
                    } catch (Exception ignored) {
                    }
                }
            }

            Helpers.threadRun(() -> {
                Helpers.mostrarMensajeError(GameFrame.getInstance(),
                        Translator.translate("zero_trust.critical_alert_header")
                        + reason + "\n\n"
                        + Translator.translate("zero_trust.critical_alert_body"));
                // Tras el popup del lockdown la timba se da por acabada para
                // este peer. El HOST se entera por socket caido + cascade
                // fail -> abortAndExit broadcast SERVEREXIT al resto. Pero
                // ESTE peer (con socket ya cerrado) no recibira SERVEREXIT,
                // asi que dispara local su propio finTransmision sin
                // force_recover -> BalanceDialog final. Sin esto, el peer
                // que detecta el ataque quedaria con GameFrame abierto
                // esperando algo que no llega.
                try {
                    GameFrame inst = GameFrame.getInstance();
                    if (inst != null) {
                        Crupier c = inst.getCrupier();
                        if (c != null) {
                            c.setForce_recover(false);
                            c.setFin_de_la_transmision(true);
                        }
                        inst.finTransmision(true);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to dispatch local finTransmision after zero-trust lockdown", ex);
                }
            });
        }
    }

    // VARIABLES CRIPTOGRÁFICAS DE ESTADO
    private volatile byte[] local_hand_seed = null;

    // Añadir esto debajo de local_hand_seed
    public byte[] getLocal_hand_seed() {
        return local_hand_seed;
    }

    // --- LLAVES EC-SRA DEL JUGADOR LOCAL ---
    public volatile byte[] local_sra_lock = null;
    public volatile byte[] local_sra_unlock = null;
    public volatile byte[] local_mega_packet = null;

    // --- TOKENS DEL HOST ---
    public volatile byte[] local_token_flop = null;
    public volatile byte[] local_token_turn = null;
    public volatile byte[] local_token_river = null;

    public volatile byte[] pure_local_cards = new byte[2];

    public final ConcurrentHashMap<String, byte[]> single_locked_pocket_cards = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<String> received_commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones_locales_recuperadas = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Integer> rebuy_now = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> rebuy_counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> iwtsth_requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> rabbit_players = new ConcurrentHashMap<>();
    private final HashMap<String, Float[]> auditor = new HashMap<>();
    private final Object lock_ciegas = new Object();
    private final Object lock_apuestas = new Object();
    private final Object lock_contabilidad = new Object();
    private final Object lock_mostrar = new Object();
    private final Object lock_iwtsth = new Object();
    private final Object lock_last_hand = new Object();
    private final Object lock_nueva_mano = new Object();
    private final Object lock_rabbit = new Object();
    private final Object lock_rebuynow = new Object();
    private final Object lock_pausa_barra = new Object();
    private final Object lock_fin_mano = new Object();
    // Publica las transiciones de calle (street) y de showdown (show_time)
    // hacia los hilos que deben esperar a esos estados antes de servir una
    // REQ_SRA_UNLOCK_BATCH. Toda escritura de street/show_time pasa por
    // setStreetLocal/setShowTime y dispara notifyAll bajo este lock, así
    // ningún waiter pierde una transición.
    private final Object protocol_state_lock = new Object();
    private final ConcurrentHashMap<String, Player> nick2player = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Hand> perdedores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Player> flop_players = new ConcurrentLinkedQueue<>();

    private byte[] activeHandId;
    private volatile int conta_mano = 0;
    private volatile int conta_accion = 0;
    private volatile float bote_total = 0f;
    private volatile float apuestas = 0f;
    private volatile float ciega_grande = GameFrame.CIEGA_GRANDE;
    private volatile float ciega_pequeña = GameFrame.CIEGA_PEQUEÑA;
    private volatile float apuesta_actual = 0f;
    private volatile float ultimo_raise = 0f;
    private volatile float partial_raise_cum = 0f;
    private volatile int conta_raise = 0;
    private volatile int conta_bet = 0;
    private volatile float bote_sobrante = 0f;
    private volatile String[] nicks_permutados;
    private volatile boolean fin_de_la_transmision = false;
    private volatile int street = PREFLOP;
    // Zero-trust state machine: el cliente sólo responde REQ_SRA_UNLOCK_BATCH
    // si la longitud de cada item encaja con la calle/fase en que está. Para
    // POCKET el guard verdadero es el anti-genesis cripto-check del cliente —
    // no hay un flag de "ya terminó Phase 2 para mí" porque cada peer sigue
    // siendo unlocker para los pockets de los OTROS targets aún cuando ya
    // recibió los suyos.
    private volatile boolean flop_revealed = false;
    private volatile boolean turn_revealed = false;
    private volatile boolean river_revealed = false;
    private volatile HandPot bote = null;
    private volatile boolean cartas_resistencia = false;
    private volatile int ciegas_double = 0;
    private volatile long turno = System.currentTimeMillis();
    private volatile boolean fold_sound_playing = false;
    private volatile int tiempo_pausa = 0;
    private volatile boolean barajando = false;
    private volatile ArrayList<String> cartas_locales_recibidas = null;
    private volatile Player last_aggressor = null;
    private volatile boolean destapar_resistencia = false;
    private volatile boolean show_time = false;
    private volatile boolean badbeat = false;
    private volatile int jugada_ganadora = 0;
    private volatile boolean sincronizando_mano = false;
    private volatile RecoverDialog recover_dialog = null;
    private volatile String current_local_cinematic_b64 = null;
    private volatile String current_remote_cinematic_b64 = null;
    private volatile boolean rebuy_time = false;
    private volatile boolean last_hand = false;
    private volatile int sqlite_id_game = -1;
    private volatile int sqlite_id_hand = -1;
    private volatile GameOverDialog gameover_dialog = null;
    private volatile String dealer_nick = null;
    private volatile String big_blind_nick = null;
    private volatile String small_blind_nick = null;
    private volatile String utg_nick = null;
    private volatile boolean saltar_primera_mano = false;
    private volatile boolean update_game_seats = false;
    private volatile int tot_acciones_recuperadas = 0;
    private volatile Float beneficio_bote_principal = null;
    private volatile boolean iwtsth = false;
    private volatile boolean iwtsthing = false;
    private volatile boolean iwtsthing_request = false;
    private volatile Long last_iwtsth_rejected = null;
    private volatile int limpers;
    private volatile int game_recovered = 0;
    private volatile Object[] ciegas_update = null;
    private volatile boolean dead_dealer = false;
    private volatile boolean force_recover = false;
    public volatile String[] active_crypto_ring = null;

    // EC-Identity v1: hand-state chain (H_t ratchet) for the current hand. Initialized
    // after the MEGAPACKET is processed on both host and clients; absorbs every canonical
    // action record produced during the hand. Cleared to null between hands by
    // readyForNextHand.
    public volatile byte[] current_hand_id = null;
    public volatile HandStateChain hand_state_chain = null;

    private byte[] requestRemoteCascade(String nick, byte[] currentDeck, Participant p) {
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        String deckB64 = Base64.getEncoder().encodeToString(currentDeck);
        try {
            p.writeCommandFromServer(Helpers.encryptCommand("GAME#" + id + "#DECK_CASCADE_REQ#" + deckB64, p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            return null;
        }

        // Sin timeout artificial: la cascada SRA con N peers se alarga linealmente
        // con N (y con la latencia de los clientes más lentos). Un timeout fijo
        // aborta cascadas legítimas en mesas grandes o con clientes lentos. La
        // única señal real de "este peer no va a responder" es que su propio thread
        // de Participant lo marca exit por inactividad de PING/PONG; usamos eso.
        boolean ok = false;
        byte[] newDeck = null;
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("DECK_CASCADE_RESP")) {
                        try {
                            String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (senderNick.equals(nick)) {
                                newDeck = Base64.getDecoder().decode(partes[4]);
                                if (newDeck.length == 1664) {
                                    ok = true;
                                }
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            rejected.add(cmd);
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (!ok) {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision() && !p.isExit());
        if (!ok) {
            return null;
        }
        return newDeck;
    }

    // v3: ronda total — pide a UN peer remoto que aplique su unlock a una LISTA
    // de payloads en un solo RTT. Cada payload identifica al destinatario al que
    // pertenece (peer_idx en el ring) y comparte la misma phase del batch. El
    // peer valida cada item por separado (state machine, anti-reuse, GATE 6 cripto)
    // y devuelve los unlocked en el mismo orden. Mantiene la propiedad de la versión
    // single-item: si el peer hace EXIT durante la espera, devolvemos null y el
    // caller decide aplicar testamento local o cancelar la mano.
    private ArrayList<byte[]> requestRemoteUnlockBatch(String nick, Participant p, int phase, ArrayList<Integer> peerIdxs, ArrayList<byte[]> payloads) {
        if (peerIdxs.size() != payloads.size()) {
            return null;
        }
        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("GAME#").append(id).append("#REQ_SRA_UNLOCK_BATCH#");
            sb.append(phase).append('#').append(this.conta_mano).append('#').append(peerIdxs.size());
            for (int i = 0; i < peerIdxs.size(); i++) {
                sb.append('#').append(peerIdxs.get(i));
                sb.append('#').append(Base64.getEncoder().encodeToString(payloads.get(i)));
            }
            p.writeCommandFromServer(Helpers.encryptCommand(sb.toString(),
                    p.getAes_key(), iv, p.getHmac_key()));
        } catch (Exception e) {
            return null;
        }

        boolean ok = false;
        ArrayList<byte[]> unlocked = null;
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");
                    if (partes.length >= 5 && partes[2].equals("RESP_SRA_UNLOCK_BATCH")) {
                        try {
                            String senderNick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (senderNick.equals(nick)) {
                                int count = Integer.parseInt(partes[4]);
                                if (count == peerIdxs.size() && partes.length == 5 + 2 * count) {
                                    ArrayList<byte[]> outList = new ArrayList<>(count);
                                    boolean wellFormed = true;
                                    for (int k = 0; k < count; k++) {
                                        int respPeerIdx = Integer.parseInt(partes[5 + 2 * k]);
                                        byte[] respPayload = Base64.getDecoder().decode(partes[6 + 2 * k]);
                                        if (respPeerIdx != peerIdxs.get(k)
                                                || respPayload.length != payloads.get(k).length) {
                                            wellFormed = false;
                                            break;
                                        }
                                        outList.add(respPayload);
                                    }
                                    if (wellFormed) {
                                        unlocked = outList;
                                        ok = true;
                                    } else {
                                        rejected.add(cmd);
                                    }
                                } else {
                                    rejected.add(cmd);
                                }
                            } else {
                                rejected.add(cmd);
                            }
                        } catch (Exception e) {
                            rejected.add(cmd);
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (!ok) {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.getReceived_commands().wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision() && !p.isExit());
        if (!ok) {
            return null;
        }
        return unlocked;
    }

    private boolean enviarCartasJugadoresRemotos() {
        for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
            if (p != null) {
                p.setReceived_token(null); // Usado para guardar la llave de los Bots
            }
        }

        // EC-Identity v1: fresh per-hand 16-byte HAND_ID that the host broadcasts to
        // every peer inside the MEGAPACKET. Every peer seeds its HandStateChain with
        // this id + the sorted player ids of the crypto-ring + the cascaded deck, so
        // H_0 is byte-identical across the table.
        this.current_hand_id = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        if (Helpers.CSPRNG_GENERATOR != null) {
            Helpers.CSPRNG_GENERATOR.nextBytes(this.current_hand_id);
        }

        // FASE 1: CASCADA DE CIFRADO Y BARAJADO
        //
        // Si un peer humano cae DURANTE su pase de cascade (entre el DECK_CASCADE_REQ
        // y nuestra recepción de su RESP), aún no se han enviado las pocket cards,
        // así que no es un misdeal: rehacemos la cascada desde el genesis con un
        // ring nuevo SIN el peer caído. Sólo damos por perdida la mano si se ha
        // ido tanta gente que ya no quedan ≥2 activos para jugar.
        StringBuilder orderBuilder;
        String[] currentRing;
        byte[] workingDeck;

        while (true) {
            // Resetea cualquier estado parcial de un intento anterior abortado.
            for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
                if (p != null) {
                    p.setReceived_token(null);
                }
            }

            java.util.ArrayList<Player> ringCriptografico = getAnilloCriptografico();
            int numPlayers = ringCriptografico.size();
            if (numPlayers < 2) {
                // Sin jugadores suficientes la mano no puede jugarse; sí es misdeal.
                cancelarManoYDevolverApuestas("peer.not_enough_players");
                return false;
            }

            orderBuilder = new StringBuilder();
            currentRing = new String[numPlayers];

            for (int i = 0; i < numPlayers; i++) {
                Player j = ringCriptografico.get(i);
                currentRing[i] = j.getNickname();
                try {
                    orderBuilder.append(Base64.getEncoder().encodeToString(j.getNickname().getBytes("UTF-8"))).append(",");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error encoding nick in orderBuilder", e);
                }
            }
            this.active_crypto_ring = currentRing;

            // Candado fresco del Host por intento.
            this.local_sra_lock = CryptoSRA.generateLockScalar();
            this.local_sra_unlock = CryptoSRA.getUnlockScalar(this.local_sra_lock);

            workingDeck = CryptoSRA.applyCommutativeLock(CryptoSRA.getGenesisDeck(), this.local_sra_lock);
            workingDeck = CryptoSRA.shuffleDeck(workingDeck, this.local_hand_seed);

            boolean restart = false;
            for (int i = 0; i < numPlayers && !restart; i++) {
                String currNick = currentRing[i];
                if (!currNick.equals(GameFrame.getInstance().getNick_local())) {
                    Participant p = GameFrame.getInstance().getParticipantes().get(currNick);
                    if (p != null && p.isCpu()) {
                        byte[] botLock = CryptoSRA.generateLockScalar();
                        byte[] botUnlock = CryptoSRA.getUnlockScalar(botLock);
                        byte[] botSeed = new byte[48];
                        if (Helpers.CSPRNG_GENERATOR != null) {
                            Helpers.CSPRNG_GENERATOR.nextBytes(botSeed);
                        }
                        p.setReceived_token(botUnlock);
                        workingDeck = CryptoSRA.applyCommutativeLock(workingDeck, botLock);
                        workingDeck = CryptoSRA.shuffleDeck(workingDeck, botSeed);
                    } else if (p != null && !p.isExit()) {
                        byte[] cascaded = requestRemoteCascade(currNick, workingDeck, p);
                        if (cascaded != null) {
                            workingDeck = cascaded;
                        } else {
                            // El peer cayó durante el cascade (su Participant lo marcó
                            // exit por socket muerto). Aún no hemos repartido nada, así
                            // que volvemos a empezar la cascada SIN él.
                            LOGGER.log(Level.WARNING,
                                    "Peer {0} dropped during cascade — restarting shuffle without them",
                                    currNick);
                            restart = true;
                        }
                    }
                }
            }
            if (!restart) {
                break;
            }
        }

        this.local_mega_packet = workingDeck;
        String megaPacketB64 = Base64.getEncoder().encodeToString(this.local_mega_packet);
        String orderB64 = "";
        try {
            orderB64 = Base64.getEncoder().encodeToString(orderBuilder.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error encoding orderB64 for MEGAPACKET", e);
        }

        // Enviamos el MEGAPACKET final a todos. EC-Identity v1: append HAND_ID as
        // a fourth field. Old clients (pre-v1) just stop parsing at the third field;
        // new clients pick it up to seed their HandStateChain.
        String handIdB64 = Base64.getEncoder().encodeToString(this.current_hand_id);
        broadcastGAMECommandFromServer("MEGAPACKET#" + orderB64 + "#" + megaPacketB64 + "#" + handIdB64, null, true);

        // EC-Identity v1: now that MEGAPACKET is finalised and every peer (in theory)
        // sees the same active_crypto_ring + cascadedDeck + handId, seed our own
        // HandStateChain. Subsequent actions in rondaApuestas ratchet H_t through this
        // chain on every peer in parallel.
        initHandStateChain();

        // FASE 2 (v3): cascade POCKET en un único batch por helper humano.
        //
        // Por cada slot i del ring se construye pockets[i] = mega_packet[i*64:(i+1)*64].
        // El host quita su lock localmente salvo si el target del slot ES el
        // host (su lock se queda hasta el resolveCardIndex final). Igual con
        // bots: si target del slot es un bot, su lock se queda. Tras eso,
        // por cada humano remoto H pedimos en UN solo REQ_SRA_UNLOCK_BATCH
        // que H quite su lock de todos los slots i cuyo target no sea H
        // (target=H mantiene el lock del propio H para que su client lo abra).
        // Si un humano H ha hecho EXIT con testamento, aplicamos su unlock
        // localmente para esos mismos slots; sin testamento abortamos la mano.
        String hostNick = GameFrame.getInstance().getNick_local();
        byte[][] pockets = new byte[currentRing.length][];
        for (int i = 0; i < currentRing.length; i++) {
            String targetNick = currentRing[i];
            byte[] pocketCards = new byte[64];
            System.arraycopy(local_mega_packet, i * 64, pocketCards, 0, 64);

            if (!targetNick.equals(hostNick)) {
                pocketCards = CryptoSRA.applyCommutativeLock(pocketCards, this.local_sra_unlock);
            }

            for (String bNick : currentRing) {
                Participant pb = GameFrame.getInstance().getParticipantes().get(bNick);
                if (pb != null && pb.isCpu() && !bNick.equals(targetNick)) {
                    pocketCards = CryptoSRA.applyCommutativeLock(pocketCards, pb.getReceived_token());
                }
            }
            pockets[i] = pocketCards;
        }

        // Recorrido de helpers humanos remotos en orden del ring (determinista).
        for (int h = 0; h < currentRing.length; h++) {
            String hNick = currentRing[h];
            if (hNick.equals(hostNick)) {
                continue;
            }
            Participant ph = GameFrame.getInstance().getParticipantes().get(hNick);
            if (ph == null || ph.isCpu()) {
                continue;
            }

            ArrayList<Integer> peerIdxs = new ArrayList<>();
            ArrayList<byte[]> payloads = new ArrayList<>();
            ArrayList<Integer> slotsForH = new ArrayList<>();
            for (int i = 0; i < currentRing.length; i++) {
                if (i != h) {
                    peerIdxs.add(i);
                    payloads.add(pockets[i]);
                    slotsForH.add(i);
                }
            }

            if (!ph.isExit()) {
                ArrayList<byte[]> response = requestRemoteUnlockBatch(hNick, ph, UNLOCK_PHASE_POCKET, peerIdxs, payloads);
                if (response != null) {
                    for (int k = 0; k < slotsForH.size(); k++) {
                        pockets[slotsForH.get(k)] = response.get(k);
                    }
                } else if (ph.getSra_unlock() != null) {
                    for (int slot : slotsForH) {
                        pockets[slot] = CryptoSRA.applyCommutativeLock(pockets[slot], ph.getSra_unlock());
                    }
                } else {
                    cancelarManoYDevolverApuestas("peer.unlock_no_testament");
                    return false;
                }
            } else if (ph.getSra_unlock() != null) {
                for (int slot : slotsForH) {
                    pockets[slot] = CryptoSRA.applyCommutativeLock(pockets[slot], ph.getSra_unlock());
                }
            } else {
                cancelarManoYDevolverApuestas("peer.unlock_no_testament");
                return false;
            }
        }

        // Broadcast y resolución local por target.
        for (int i = 0; i < currentRing.length; i++) {
            String targetNick = currentRing[i];
            byte[] pocketCards = pockets[i];
            this.single_locked_pocket_cards.put(targetNick, pocketCards);

            try {
                String pcB64 = Base64.getEncoder().encodeToString(pocketCards);
                String nickB64 = Base64.getEncoder().encodeToString(targetNick.getBytes("UTF-8"));
                broadcastGAMECommandFromServer("POCKET_CARDS#" + nickB64 + "#" + pcB64, null, true);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error broadcasting POCKET_CARDS for " + targetNick, e);
                cancelarManoYDevolverApuestas("peer.broadcast_failed");
                return false;
            }

            if (targetNick.equals(hostNick)) {
                byte[] myPocket = CryptoSRA.applyCommutativeLock(pocketCards, this.local_sra_unlock);
                byte[] c1 = Arrays.copyOfRange(myPocket, 0, 32);
                byte[] c2 = Arrays.copyOfRange(myPocket, 32, 64);
                this.local_original_cards[0] = (byte) CryptoSRA.resolveCardIndex(c1);
                this.local_original_cards[1] = (byte) CryptoSRA.resolveCardIndex(c2);
            } else {
                Participant pTarget = GameFrame.getInstance().getParticipantes().get(targetNick);
                if (pTarget != null && pTarget.isCpu()) {
                    byte[] botPocket = CryptoSRA.applyCommutativeLock(pocketCards, pTarget.getReceived_token());
                    byte[] c1 = Arrays.copyOfRange(botPocket, 0, 32);
                    byte[] c2 = Arrays.copyOfRange(botPocket, 32, 64);
                    int id1 = CryptoSRA.resolveCardIndex(c1);
                    int id2 = CryptoSRA.resolveCardIndex(c2);
                    if (id1 >= 0 && id2 >= 0) {
                        Player botPlayer = nick2player.get(targetNick);
                        if (botPlayer != null) {
                            botPlayer.getHoleCard1().iniciarConValorNumerico(id1 + 1);
                            botPlayer.getHoleCard2().iniciarConValorNumerico(id2 + 1);
                        }
                    }
                }
            }
        }

        // GUARDAMOS EL FÓSIL DESPUÉS DE REPARTIR (Obligatorio en SRA)
        this.guardarFosilSRA();

        return true;
    }

    private ArrayList<String> recibirMisCartas() {
        long start_time = System.currentTimeMillis();
        boolean ok = false;
        String[] cartas = new String[2];

        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");
                        if (partes.length < 3) {
                            LOGGER.log(Level.WARNING, "Malformed command dropped (receiveMyCards): {0}", comando);
                            continue;
                        }

                        if (partes[2].equals("MEGAPACKET") && partes.length >= 5) {
                            String orderB64 = partes[3];
                            this.local_mega_packet = java.util.Base64.getDecoder().decode(partes[4]);
                            try {
                                String orderStr = new String(java.util.Base64.getDecoder().decode(orderB64), "UTF-8");
                                String[] orderTokens = orderStr.split(",");
                                java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                                for (String token : orderTokens) {
                                    if (!token.isEmpty()) {
                                        ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                    }
                                }
                                this.active_crypto_ring = ringList.toArray(new String[0]);
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error parsing ORDER of MEGAPACKET", e);
                            }
                            // EC-Identity v1: the host appends a 16-byte HAND_ID as a fourth
                            // payload field. If present and well formed, seed our HandStateChain
                            // so subsequent actions ratchet on every peer in parallel.
                            if (partes.length >= 6) {
                                try {
                                    byte[] hid = java.util.Base64.getDecoder().decode(partes[5]);
                                    if (hid.length == CanonicalActionRecord.HAND_ID_BYTES) {
                                        this.current_hand_id = hid;
                                    } else {
                                        LOGGER.log(Level.WARNING, "MEGAPACKET HAND_ID has wrong length: {0}", hid.length);
                                        this.current_hand_id = null;
                                    }
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, "Error parsing HAND_ID of MEGAPACKET", e);
                                    this.current_hand_id = null;
                                }
                            }
                            initHandStateChain();
                        } else if (partes[2].equals("POCKET_CARDS") && partes.length >= 5) {
                            try {
                                String targetNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                                byte[] unlockedByOthers = java.util.Base64.getDecoder().decode(partes[4]);
                                if (unlockedByOthers != null) {
                                    this.single_locked_pocket_cards.put(targetNick, unlockedByOthers);
                                }

                                if (targetNick.equals(GameFrame.getInstance().getNick_local())) {

                                    // El MEGAPACKET tiene que haberse procesado antes.
                                    if (this.local_mega_packet != null) {
                                        this.local_sra_unlock = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local()).getSra_unlock();

                                        // Quitamos nuestro propio candado (El último de la capa de cifrado)
                                        byte[] myPocket = CryptoSRA.applyCommutativeLock(unlockedByOthers, this.local_sra_unlock);
                                        byte[] c1 = java.util.Arrays.copyOfRange(myPocket, 0, 32);
                                        byte[] c2 = java.util.Arrays.copyOfRange(myPocket, 32, 64);

                                        int id1 = CryptoSRA.resolveCardIndex(c1);
                                        int id2 = CryptoSRA.resolveCardIndex(c2);

                                        if (id1 >= 0 && id2 >= 0) {
                                            this.local_original_cards[0] = (byte) id1;
                                            this.local_original_cards[1] = (byte) id2;
                                            cartas[0] = Card.VALORES[id1 % 13] + "_" + Card.PALOS[id1 / 13];
                                            cartas[1] = Card.VALORES[id2 % 13] + "_" + Card.PALOS[id2 / 13];
                                            ok = true;
                                        }
                                    } else {
                                        // Si ha llegado antes que la baraja, lo devolvemos a la cola y esperamos
                                        rejected.add(comando);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error processing POCKET_CARDS", e);
                            }
                        } else if (partes[2].equals("MISDEAL") && partes.length >= 4) {
                            // El host aborta la mano: salimos del consumer sin las cartas.
                            // El cancelar ya lo hace el case top-level en WaitingRoomFrame.
                            return null;
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receiveMyCards: " + comando, ex);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }
            if (!ok) {
                if (isFin_de_la_transmision()) {
                    break;
                }
                Helpers.pausar(100);
            }
        } while (!ok && !isFin_de_la_transmision());

        if (!ok) {
            return null;
        }

        // ¡ESENCIAL! El cliente guarda sus cartas en su Fósil local
        this.guardarFosilSRA();

        return new ArrayList<>(java.util.Arrays.asList(cartas));
    }


    public String getTestamentoCriptografico() {
        return getTestamentoCriptografico(GameFrame.getInstance().getNick_local());
    }

    public String getTestamentoCriptografico(String nick) {
        // ZERO-TRUST: si el cliente entró en lockdown nunca compartimos
        // nuestra propia sra_unlock con el servidor — eso permitiría al
        // host descifrar nuestras pocket cards de la mano congelada. El
        // testamento de OTROS peers sí se devuelve normal (uso local del
        // host honesto para destapar a un peer que se marchó).
        if (Crupier.SECURITY_LOCKDOWN && nick.equals(GameFrame.getInstance().getNick_local())) {
            return "*";
        }
        try {
            byte[] testament = null;
            if (nick.equals(GameFrame.getInstance().getNick_local())) {
                testament = this.local_sra_unlock;
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null) {
                    if (p.isCpu()) {
                        testament = p.getReceived_token(); // Llave del Bot (Host side)
                    } else {
                        testament = p.getSra_unlock(); // Llave que nos envi el humano (o la que guardamos nosotros)
                    }
                }
            }

            // Fallback para cliente remoto que no ha seteado local_sra_unlock pero la tiene en Participant
            if (testament == null && nick.equals(GameFrame.getInstance().getNick_local())) {
                Participant p = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                if (p != null) {
                    testament = p.getSra_unlock();
                }
            }

            if (testament != null && testament.length == 32) {
                return Base64.getEncoder().encodeToString(testament);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating the SRA exit testament for " + nick, e);
        }
        return "*";
    }

    public boolean unlockPlayerCardsWithSRAKey(Player target) {
        Participant p = GameFrame.getInstance().getParticipantes().get(target.getNickname());
        if (p != null && p.getSra_unlock() != null) {
            byte[] pocketCards = this.single_locked_pocket_cards.get(target.getNickname());
            if (pocketCards != null && pocketCards.length == 64) {
                try {
                    byte[] unlocked = CryptoSRA.applyCommutativeLock(pocketCards, p.getSra_unlock());
                    byte[] c1 = Arrays.copyOfRange(unlocked, 0, 32);
                    byte[] c2 = Arrays.copyOfRange(unlocked, 32, 64);

                    int id1 = CryptoSRA.resolveCardIndex(c1);
                    int id2 = CryptoSRA.resolveCardIndex(c2);
                    if (id1 >= 0 && id2 >= 0) {
                        target.getHoleCard1().actualizarConValorNumerico(id1 + 1);
                        target.getHoleCard2().actualizarConValorNumerico(id2 + 1);
                        return true;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return false;
    }

    public int calcularPosicionEnPaquete(String nick) {

        // 1. Strict explicit packet layout (Prevents ZERO-TRUST index shifting)
        if (this.active_crypto_ring != null) {
            for (int i = 0; i < this.active_crypto_ring.length; i++) {
                if (this.active_crypto_ring[i].equals(nick)) {
                    return i;
                }
            }
            return -1;
        }

        // 2. Fallback to dynamic UI ring (Only used in edge cases/recoveries before MEGAPACKET)
        java.util.ArrayList<Player> ring = getAnilloCriptografico();
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i).getNickname().equals(nick)) {
                return i;
            }
        }
        return -1;
    }

    // Variables puras para auditoría (Inmunes a la GUI)
    public volatile byte[] local_envelope = null;
    public volatile byte[] local_original_cards = new byte[2];

    public byte[] getLocal_original_cards() {
        return local_original_cards;
    }

    public byte[] getActiveHandId() {
        return activeHandId;
    }

    public boolean isForce_recover() {
        return force_recover;
    }

    public void setForce_recover(boolean force_recover) {
        this.force_recover = force_recover;
    }

    public Object getLock_rabbit() {
        return lock_rabbit;
    }

    public ConcurrentHashMap<String, Boolean> getRabbit_players() {
        return rabbit_players;
    }

    public boolean isDead_dealer() {
        return dead_dealer;
    }

    public Object[] getCiegas_update() {
        return ciegas_update;
    }

    public Object getLock_ciegas() {
        return lock_ciegas;
    }

    public void setCurrent_local_cinematic_b64(String current_local_cinematic_b64) {
        this.current_local_cinematic_b64 = current_local_cinematic_b64;
    }

    public int getLimpersCount() {
        return limpers;
    }

    public boolean isIwtsthing() {
        return iwtsthing;
    }

    public boolean isIwtsth() {
        return iwtsth;
    }

    public boolean isSomePlayerTimeout() {

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isTimeout()) {

                return true;
            }
        }

        return false;
    }

    public Object getLock_fin_mano() {
        return lock_fin_mano;
    }

    public String getDealer_nick() {
        return dealer_nick;
    }

    public String getBb_nick() {
        return big_blind_nick;
    }

    public String getSb_nick() {
        return small_blind_nick;
    }

    public String getUtg_nick() {
        return utg_nick;
    }

    public ConcurrentHashMap<String, Player> getNick2player() {
        return nick2player;
    }

    public ConcurrentHashMap<String, Integer> getRebuy_now() {
        return rebuy_now;
    }

    public ConcurrentHashMap<String, Integer> getRebuy_counts() {
        return rebuy_counts;
    }

    public int getRebuyCount(String nick) {
        return rebuy_counts.getOrDefault(nick, 0);
    }

    public boolean atRebuyLimit(String nick) {
        return GameFrame.REBUY_LIMIT > 0 && getRebuyCount(nick) >= GameFrame.REBUY_LIMIT;
    }

    public boolean isLast_hand() {

        synchronized (lock_last_hand) {
            return last_hand;
        }
    }

    public void setLast_hand(boolean last_hand) {
        synchronized (lock_last_hand) {
            this.last_hand = last_hand;
        }
    }

    public boolean isRebuy_time() {
        return rebuy_time;
    }

    public Object getLock_nueva_mano() {
        return lock_nueva_mano;
    }

    public float getApuestas() {
        return apuestas;
    }

    public int getConta_bet() {
        return conta_bet;
    }

    public String getCurrent_remote_cinematic_b64() {
        return current_remote_cinematic_b64;
    }

    public void setCurrent_remote_cinematic_b64(String current_remote_cinematic_b64) {
        this.current_remote_cinematic_b64 = current_remote_cinematic_b64;
    }

    private String pedirPermisoAClientes(int targetStreet, ArrayList<Player> resisten) {
        return null; // OBSOLETE IN EC-SRA
    }

    private Object[] recopilarTokens(int targetStreet, java.util.ArrayList<Player> resisten) {
        return new Object[]{null, null}; // OBSOLETE IN EC-SRA
    }

    public void rebuyNow(String nick, int buyin) {

        synchronized (lock_rebuynow) {
            boolean denied_by_limit = false;
            if (!rebuy_now.containsKey(nick)) {
                if (atRebuyLimit(nick)) {
                    denied_by_limit = true;
                } else {
                    this.rebuy_now.put(nick, buyin);
                }
            } else {
                this.rebuy_now.remove(nick);
            }

            if (GameFrame.getInstance().isPartida_local()) {

                try {
                    if (denied_by_limit) {
                        this.broadcastGAMECommandFromServer(
                                "REBUYDENIED#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(GameFrame.REBUY_LIMIT),
                                null, false);
                    } else {
                        this.broadcastGAMECommandFromServer(
                                "REBUYNOW#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(buyin),
                                nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname()) ? null : nick);
                    }
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            } else if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                this.sendGAMECommandToServer("REBUYNOW#" + String.valueOf(buyin));
            }
        }
    }

    public static void loadMODCinematicsAllin() {

        if (Init.MOD != null) {

            ArrayList<Object[]> cinematics = new ArrayList<>();

            File cinematics_folder = new File(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin");

            // ALLIN CINEMATICS
            if (cinematics_folder.isDirectory() && cinematics_folder.canRead()
                    && cinematics_folder.listFiles(File::isFile).length > 0) {

                for (final File fileEntry : cinematics_folder.listFiles(File::isFile)) {
                    if (fileEntry.getName().toLowerCase().endsWith(".gif")) {
                        cinematics.add(new Object[]{fileEntry.getName()});
                    }
                }

                if (FUSION_MOD_CINEMATICS) {

                    cinematics.addAll(Arrays.asList(Crupier.ALLIN_CINEMATICS.getValue()));
                }
                Crupier.ALLIN_CINEMATICS_MOD = new HashMap.SimpleEntry<>("allin/", cinematics.toArray(new Object[0][]));

            } else {

                Crupier.ALLIN_CINEMATICS_MOD = Crupier.ALLIN_CINEMATICS;
            }

        }
    }

    public static void loadMODSounds() {

        if (Init.MOD != null) {

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.ALLIN_SOUNDS_MOD = Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/",
                            sounds.toArray(new String[0]));
                }

            } else {
                Crupier.FOLD_SOUNDS_MOD = Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(
                    Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.SHOWDOWN_SOUNDS_MOD = Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/",
                            sounds.toArray(new String[0]));
                }

            } else {

                Crupier.LOSER_SOUNDS_MOD = Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths
                    .get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/"))) {
                File[] archivos = new File(
                        Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/")
                        .listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/",
                            filenames.toArray(new String[0]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/",
                            sounds.toArray(new String[0]));
                }

            } else {
                Crupier.WINNER_SOUNDS_MOD = Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE);
            }

        }

    }

    public boolean isFin_de_la_transmision() {
        return fin_de_la_transmision;
    }

    public void setFin_de_la_transmision(boolean fin) {
        fin_de_la_transmision = fin;
    }

    public boolean isSincronizando_mano() {
        return sincronizando_mano;
    }

    public void setSincronizando_mano(boolean sincronizando_mano) {
        this.sincronizando_mano = sincronizando_mano;
    }

    public Object getLock_contabilidad() {
        return lock_contabilidad;
    }

    public void setTiempo_pausa(int tiempo) {

        synchronized (lock_pausa_barra) {
            this.tiempo_pausa = tiempo;

            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), tiempo);
        }

    }

    public int getTiempoPausa() {

        synchronized (lock_pausa_barra) {

            return tiempo_pausa;
        }
    }

    public long getTurno() {
        return turno;
    }

    public boolean localCinematicAllin() {

        Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD)
                : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

        if (!this.sincronizando_mano && GameFrame.CINEMATICAS && map.containsKey("allin/")
                && map.get("allin/").length > 0) {

            Object[][] allin_cinematics = map.get("allin/");

            int r = Helpers.CSPRNG_GENERATOR.nextInt(allin_cinematics.length);

            String filename = (String) allin_cinematics[r][0];

            long pausa = 0L;

            if (allin_cinematics[r].length > 1) {

                pausa = (long) allin_cinematics[r][1];

            } else if (Files
                    .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename))) {

                try {
                    pausa = Helpers.getGIFLength(
                            Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename).toUri()
                                    .toURL());

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            } else if (getClass().getResource("/cinematics/allin/" + filename) != null) {
                try {
                    pausa = Helpers
                            .getGIFLength(getClass().getResource("/cinematics/allin/" + filename).toURI().toURL());

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            if (pausa != 0L) {

                try {

                    this.current_local_cinematic_b64 = Base64.getEncoder().encodeToString(
                            (Base64.getEncoder().encodeToString(filename.getBytes("UTF-8")) + "#"
                                    + String.valueOf(pausa))
                                    .getBytes("UTF-8"));

                    synchronized (getLock_apuestas()) {
                        getLock_apuestas().notifyAll();
                    }

                    if (Files
                            .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/"
                                    + filename.replaceAll("\\.gif$", ".wav")))
                            || getClass().getResource(
                                    "/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null) {

                        Audio.playWavResource("allin/" + filename.replaceAll("\\.gif$", ".wav"));
                    }

                    return _cinematicAllin(filename, pausa);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    Init.PLAYING_CINEMATIC = false;
                    this.current_local_cinematic_b64 = null;
                }
            } else {
                Init.PLAYING_CINEMATIC = false;
                this.current_local_cinematic_b64 = null;
            }
        } else {
            Init.PLAYING_CINEMATIC = false;
            this.current_local_cinematic_b64 = null;
        }

        return false;

    }

    public boolean remoteCinematicAllin() {

        if (getCurrent_remote_cinematic_b64() != null) {

            try {

                String animationb64 = new String(Base64.getDecoder().decode(getCurrent_remote_cinematic_b64()),
                        "UTF-8");

                String[] partes = animationb64.split("#");

                return _cinematicAllin(new String(Base64.getDecoder().decode(partes[0]), "UTF-8"),
                        Long.parseLong(partes[1]));

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                Init.PLAYING_CINEMATIC = false;
                setCurrent_remote_cinematic_b64(null);
            }

        } else {
            Init.PLAYING_CINEMATIC = false;
        }

        return false;

    }

    private boolean _cinematicAllin(String filename, long pausa) {

        if (!this.sincronizando_mano) {

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (GameFrame.CINEMATICAS) {

                final ImageIcon icon;
                URL url_icon = null;

                if (Init.MOD != null && Files
                        .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename))) {
                    try {
                        url_icon = Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename)
                                .toUri().toURL();
                    } catch (MalformedURLException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                } else if (getClass().getResource("/cinematics/allin/" + filename) != null) {
                    url_icon = getClass().getResource("/cinematics/allin/" + filename);
                } else {
                    url_icon = null;
                }

                if (url_icon != null) {

                    icon = new ImageIcon(url_icon);

                    final URL f_url_icon = url_icon;

                    Helpers.threadRun(new Runnable() {
                        private volatile GifAnimationDialog gif_dialog;

                        public void run() {

                            if (pausa != 0L) {

                                long now = System.currentTimeMillis();

                                Helpers.GUIRun(() -> {
                                    try {
                                        gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true, icon,
                                                Helpers.getGIFFramesCount(f_url_icon));
                                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                        gif_dialog.setVisible(true);
                                    } catch (IOException | ImageProcessingException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                });

                                while (Init.PLAYING_CINEMATIC && !gif_dialog.isForce_exit()) {

                                    synchronized (Init.LOCK_CINEMATICS) {

                                        try {
                                            Init.LOCK_CINEMATICS.wait(1000);

                                        } catch (InterruptedException ex) {
                                            Helpers.logCooperativeCancellation(LOGGER, "cinematic playback wait", ex);
                                            break;
                                        }
                                    }
                                }

                                if (gif_dialog.isForce_exit()) {

                                    long pause = now + pausa - System.currentTimeMillis();

                                    if (pause > 0) {
                                        Helpers.pausar(pause);
                                    }

                                    Init.PLAYING_CINEMATIC = false;

                                    synchronized (Init.LOCK_CINEMATICS) {

                                        Init.LOCK_CINEMATICS.notifyAll();

                                    }
                                }
                            }

                            current_remote_cinematic_b64 = null;

                            Helpers.GUIRun(() -> {
                                if (gif_dialog.isVisible()) {
                                    gif_dialog.dispose();
                                }

                                Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);
                            });

                            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                            }
                        }
                    });

                } else {

                    Helpers.threadRun(() -> {
                        if (current_remote_cinematic_b64 != null && pausa != 0L) {

                            Helpers.pausar(pausa);
                            Init.PLAYING_CINEMATIC = false;

                            synchronized (Init.LOCK_CINEMATICS) {

                                Init.LOCK_CINEMATICS.notifyAll();

                            }
                        }

                        current_remote_cinematic_b64 = null;

                        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                        synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                            GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                        }
                    });
                }

            } else {

                Helpers.threadRun(() -> {
                    if (pausa != 0L) {
                        Helpers.pausar(pausa);
                        Init.PLAYING_CINEMATIC = false;

                        synchronized (Init.LOCK_CINEMATICS) {

                            Init.LOCK_CINEMATICS.notifyAll();

                        }
                    }

                    current_remote_cinematic_b64 = null;

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

                    synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                        GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                    }
                });

            }

        }

        return (Files
                .exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/"
                        + filename.replaceAll("\\.gif$", ".wav")))
                || getClass().getResource("/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null);

    }

    public void soundAllin() {

        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_SOUNDS_MOD)
                    : Map.ofEntries(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE)));

        }

    }

    public void soundFold() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {
            this.fold_sound_playing = true;
            Helpers.threadRun(() -> {
                Audio.playRandomWavResourceAndWait(Init.MOD != null ? Map.ofEntries(Crupier.FOLD_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE)));
                fold_sound_playing = false;
            });
        }
    }

    public void soundShowdown() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/badbeat.wav");
                    Audio.unmuteAllLoopMp3();
                });
            } else if (jugada_ganadora >= Hand.POKER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/youarelucky.wav");
                    Audio.unmuteAllLoopMp3();
                });

            } else {
                Audio.playRandomWavResource(Init.MOD != null
                        ? Map.ofEntries(Crupier.SHOWDOWN_SOUNDS_MOD, Crupier.WINNER_SOUNDS_MOD,
                                Crupier.LOSER_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE),
                                Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE),
                                Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public void soundWinner(int jugada, boolean ultima_carta) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if ((jugada >= Hand.POKER || badbeat) && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/youarelucky.wav");
                    Audio.unmuteAllLoopMp3();
                });

            } else {

                Map<String, String[]> sonidos;

                if (ultima_carta && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                    sonidos = Init.MOD != null
                            ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD,
                                    new HashMap.SimpleEntry<>("misc/", new String[]{"lastcard.wav"}))
                            : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE),
                                    new HashMap.SimpleEntry<>("misc/", new String[]{"lastcard.wav"}));

                } else {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD)
                            : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE));
                }

                Audio.playRandomWavResource(sonidos);
            }
        }
    }

    public void soundLoser(int jugada) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(() -> {
                    Audio.muteAllLoopMp3();
                    Audio.playWavResourceAndWait("misc/badbeat.wav");
                    Audio.unmuteAllLoopMp3();
                });
            } else if (jugada >= Hand.FULL && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<>("joke/es/loser/", new String[]{
                    "encargado.wav",
                    "matias.wav"});

                Audio.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));

            } else {
                Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.LOSER_SOUNDS_MOD)
                        : Map.ofEntries(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public int getStreet() {
        return street;
    }

    public HandPot getBote() {
        return bote;
    }

    public HashMap<String, Float[]> getAuditor() {
        return auditor;
    }

    public void auditorCuentas() {

        synchronized (this.getLock_contabilidad()) {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                this.auditor.put(jugador.getNickname(),
                        new Float[]{jugador.getStack()
                            + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar()
                            : jugador.getBote()),
                            (float) jugador.getBuyin()});
            }

            float stack_sum = 0f;

            float buyin_sum = 0f;

            String status = "[NICK / STACK / BUYIN] -> ";

            for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {

                Float[] pasta = entry.getValue();

                stack_sum += pasta[0];

                buyin_sum += pasta[1];

                status += " [" + entry.getKey() + " / " + Helpers.float2String(pasta[0]) + " / "
                        + Helpers.float2String(pasta[1]) + "] ";

            }

            GameFrame.getInstance().getRegistro().print(status);

            if (Helpers.float1DSecureCompare(Helpers.floatClean(stack_sum) + Helpers.floatClean(this.bote_sobrante),
                    buyin_sum) != 0) {

                if (this.game_recovered == 1 && Helpers.float1DSecureCompare(0f, this.bote_sobrante) <= 0) {

                    this.game_recovered = 2;

                    // CORREGIMOS EL BOTE SOBRANTE DESAPARECIDO AL RECUPERAR LA PARTIDA
                    this.bote_sobrante = Helpers
                            .floatClean(Helpers.floatClean(buyin_sum) - Helpers.floatClean(stack_sum));

                    if (Helpers.float1DSecureCompare(0f, this.bote_sobrante) <= 0) {

                        this.bote_total = this.bote_sobrante;

                    } else {
                        // No debería llegar aqui nunca (bote sobrante negativo) si no ha habido algún
                        // error jodido (Si ocurriese, ponemos el sobrante a cero aunque el auditor dará
                        // aviso en el registro)
                        this.bote_sobrante = 0f;
                    }

                    GameFrame.getInstance().getRegistro()
                            .print(Translator.translate("ui.auditor_de_cuentas") + " -> STACKS: "
                                    + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum)
                                    + Translator.translate("ui.sobrante") + Helpers.float2String(this.bote_sobrante));

                    if (Helpers.float1DSecureCompare(
                            Helpers.floatClean(stack_sum) + Helpers.floatClean(this.bote_sobrante), buyin_sum) != 0) {
                        Helpers.mostrarMensajeError(GameFrame.getInstance(),
                                Translator.translate("ui.ojo_a_esto_no_salen"));
                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("ui.ojo_a_esto_no_salen"));
                    }

                } else {
                    GameFrame.getInstance().getRegistro()
                            .print(Translator.translate("ui.auditor_de_cuentas") + " -> STACKS: "
                                    + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum)
                                    + Translator.translate("ui.sobrante") + Helpers.float2String(this.bote_sobrante));
                    GameFrame.getInstance().getRegistro()
                            .print(Translator.translate("ui.ojo_a_esto_no_salen"));
                }
            } else {
                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("ui.auditor_de_cuentas") + " -> STACKS: "
                                + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum)
                                + Translator.translate("ui.sobrante") + Helpers.float2String(this.bote_sobrante));
            }
        }
    }

    private void recibirRebuys(ArrayList<String> pending) {

        Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

        long start_time = System.currentTimeMillis();
        boolean timeout = false;

        while (!pending.isEmpty() && !timeout) {

            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");
                        if (partes.length < 3) {
                            LOGGER.log(Level.WARNING, "Malformed command dropped (REBUY wait): {0}", comando);
                            continue;
                        }

                        if (partes[2].equals("REBUY") && partes.length >= 4) {
                            String nick = null;
                            try {
                                nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.WARNING, "Badly-encoded nick in REBUY", ex);
                                continue;
                            }
                            pending.remove(nick);
                            Player jugador = nick2player.get(nick);
                            if (jugador == null) {
                                LOGGER.log(Level.WARNING, "REBUY from unknown nick: {0}", nick);
                                continue;
                            }
                            jugador.setTimeout(false);

                            if (GameFrame.getInstance().isPartida_local()) {
                                broadcastGAMECommandFromServer("REBUY#" + partes[3] + (partes.length > 4 ? "#" + partes[4] : ""), nick);
                            }

                            if (partes.length > 4) {
                                if (partes[4].equals("0") || atRebuyLimit(nick)) {
                                    jugador.setSpectator(null);
                                } else {
                                    rebuy_now.put(nick, Integer.parseInt(partes[4]));
                                }
                            } else if (atRebuyLimit(nick)) {
                                jugador.setSpectator(null);
                            } else {
                                rebuy_now.put(nick, GameFrame.BUYIN);
                            }
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in REBUY wait: " + comando, ex);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }
            }

            if (!pending.isEmpty()) {
                Iterator<String> iterator = pending.iterator();
                while (iterator.hasNext()) {
                    String nick = iterator.next();
                    Player jp = nick2player.get(nick);
                    if (jp != null && jp.isExit()) {
                        iterator.remove();
                    }
                }

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > 2 * GameFrame.REBUY_TIMEOUT) {
                    if (GameFrame.getInstance().isPartida_local()) {
                        // Jugador no respondió al rebuy en el tiempo esperado:
                        // se asume "no rebuy" (= spectator), MISMO comportamiento
                        // que si hubiera contestado "0" expresamente. Antes este
                        // path llamaba remotePlayerQuit y los expulsaba de la
                        // mesa, lo cual es un kick injustificado: el jugador
                        // sigue en la partida como spectator y volverá a jugar
                        // si recarga manualmente en la siguiente mano.
                        LOGGER.log(Level.INFO, "REBUY timeout — pending players default to spectator (no kick)");
                        for (String nick : pending) {
                            Player jpk = nick2player.get(nick);
                            if (jpk != null && !jpk.isExit()) {
                                jpk.setSpectator(null);
                                jpk.setTimeout(false);
                            }
                        }
                        timeout = true;
                    } else {
                        start_time = System.currentTimeMillis();
                    }
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }

        }

        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);
    }

    public synchronized void remotePlayerQuit(String nick, String testamento) {
        Player jugador = nick2player.get(nick);
        if (jugador != null && !jugador.isExit()) {
            jugador.setExit();
            if (GameFrame.getInstance().isPartida_local()) {
                Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
                if (participante != null) {
                    participante.exitAndCloseSocket();
                }
                try {
                    String cmd = "EXIT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                    // V60: Propagamos el testamento en bloque (si existe)
                    if (testamento != null && !testamento.isEmpty() && !testamento.equals("*")) {
                        cmd += "#" + testamento;
                    }
                    broadcastGAMECommandFromServer(cmd, nick);
                } catch (UnsupportedEncodingException ex) {
                }

                if (this.isFin_de_la_transmision() || !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    if (participante != null && participante.isCpu()) {
                        GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
                    }
                }
            } else {
                if (this.isFin_de_la_transmision() || !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
                }
            }
            synchronized (this.getReceived_commands()) {
                this.getReceived_commands().notifyAll();
            }
            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
            }
        }
    }

    // Sobrecarga de compatibilidad
    public synchronized void remotePlayerQuit(String nick) {
        remotePlayerQuit(nick, null);
    }

    public Object getLock_apuestas() {
        return lock_apuestas;
    }

    public ConcurrentLinkedQueue<String> getReceived_commands() {
        return received_commands;
    }

    public float getApuesta_actual() {
        return apuesta_actual;
    }

    public int getCiegas_double() {
        return ciegas_double;
    }

    public int getMano() {
        return conta_mano;
    }

    public void actualizarContadoresTapete() {

        GameFrame.getInstance().setTapeteBote(this.bote_total, this.beneficio_bote_principal);
        GameFrame.getInstance().setTapeteApuestas(this.apuestas);
        GameFrame.getInstance().setTapeteCiegas(this.ciega_pequeña, this.ciega_grande);
        GameFrame.getInstance().setTapeteMano(this.conta_mano);
    }

    private void resetBetPlayerDecisions(ArrayList<Player> jugadores, String nick, boolean partial_raise) {

        if (nick == null) {
            this.last_aggressor = null;
        } else {
            this.last_aggressor = nick2player.get(nick);
        }

        for (Player jugador : jugadores) {

            if (jugador.isActivo() && jugador.getDecision() != Player.FOLD && jugador.getDecision() != Player.ALLIN
                    && (nick == null || partial_raise || !jugador.getNickname().equals(nick))) {
                jugador.resetBetDecision();
            }
        }
    }

    public Object getLock_mostrar() {
        return lock_mostrar;
    }

    public void showAndBroadcastPlayerCards(String nick) {
        synchronized (lock_mostrar) {
            // Nota: ya no gateamos con show_time. Si el comando proviene del flujo IWTSTH, la temporización
            // del wait/pausaConBarra puede haber cerrado show_time antes de que llegue el SHOWCARDS de un
            // candidato remoto. La función es idempotente (chequea isTapada() abajo) y los callers
            // ya verifican show_time donde corresponde para el flujo voluntario (LocalPlayer.player_allin_buttonActionPerformed).
            Player jugador = nick2player.get(nick);
            if (jugador == null) {
                return;
            }
            boolean isLocal = jugador.equals(GameFrame.getInstance().getLocalPlayer());

            // Solo desciframos si es remoto y faltan los valores
            if (!isLocal && (jugador.getHoleCard1().getValor() == null || jugador.getHoleCard1().getValor().isEmpty())) {
                // ZERO-TRUST: Si el jugador ha enviado su testamento (sra_unlock), desciframos su mano
                Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                if (p != null && p.getSra_unlock() != null && p.getSra_unlock().length == 32) {
                    unlockPlayerCardsWithSRAKey(jugador);
                    jugador.ordenarCartas();
                }
            }

            // ZERO-TRUST SRA: Enviamos la clave de desbloqueo en vez de las cartas en texto plano.
            // Cada receptor descifra localmente desde su propia copia del mega_packet.
            try {
                String sraKeyB64 = getTestamentoCriptografico(nick);

                String comando = "SHOWCARDS#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#" + sraKeyB64;
                if (GameFrame.getInstance().isPartida_local()) {
                    broadcastGAMECommandFromServer(comando, nick);
                } else if (isLocal) {
                    // ZERO-TRUST: SHOWCARDS lleva nuestra sra_unlock; si el
                    // cliente ya entró en lockdown por una incidencia previa,
                    // la promesa "no se envía ninguna clave criptográfica más
                    // al servidor en esta sesión" debe cubrir también la
                    // muestra voluntaria. Suprimimos el envío.
                    if (Crupier.SECURITY_LOCKDOWN) {
                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: SHOWCARDS suppressed — security lockdown active");
                    } else {
                        // CRÍTICO: el host necesita la clave SRA para descifrar y mostrar las cartas. Esperamos ACK.
                        sendGAMECommandToServer(comando, true);
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error sending SHOWCARDS for " + nick, ex);
            }

            if (jugador.getHoleCard1().isTapada()) {
                jugador.destaparCartas(true);

                // CLONACIÓN DEFENSIVA: Pasamos una copia a la clase Hand para que no desordene la UI
                ArrayList<Card> evalList = new ArrayList<>();
                evalList.addAll(jugador.getHoleCards());
                for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                    if (!c.isTapada()) {
                        evalList.add(c);
                    }
                }

                try {
                    Hand jugada = new Hand(evalList);
                    jugador.showCards(jugada.getName());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error evaluating Hand while showing cards of " + nick, e);
                }

                setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
            } else if (isLocal) {
                setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
            }
        }
    }

    public ConcurrentHashMap<Player, Hand> getPerdedores() {
        return perdedores;
    }

    public boolean isShow_time() {
        return show_time;
    }

    public boolean isFlop_revealed() {
        return flop_revealed;
    }

    public boolean isTurn_revealed() {
        return turn_revealed;
    }

    public boolean isRiver_revealed() {
        return river_revealed;
    }

    public void setFlop_revealed(boolean v) {
        this.flop_revealed = v;
    }

    public void setTurn_revealed(boolean v) {
        this.turn_revealed = v;
    }

    public void setRiver_revealed(boolean v) {
        this.river_revealed = v;
    }

    // Phase enum para REQ_SRA_UNLOCK_BATCH. Cada item del batch lleva un
    // (phase, peer_idx); el cliente valida que (phase, peer_idx) encaja con
    // su estado local y aún no se ha servido en esta mano (anti-reuse, ver
    // isSraUnlockRequestLegitimate).
    public static final int UNLOCK_PHASE_POCKET = 0;
    public static final int UNLOCK_PHASE_FLOP = 1;
    public static final int UNLOCK_PHASE_TURN = 2;
    public static final int UNLOCK_PHASE_RIVER = 3;
    public static final int UNLOCK_PHASE_RABBIT_FLOP = 4;
    public static final int UNLOCK_PHASE_RABBIT_TURN = 5;
    public static final int UNLOCK_PHASE_RABBIT_RIVER = 6;

    // Tags ya servidas esta mano (clave compuesta phase:peer_idx para todas
    // las phases en v3 — comunitaria también es per-recipient). Bloquea que
    // el host pida el mismo (phase, peer_idx) dos veces engañando con bytes
    // distintos.
    private final Set<String> sra_unlock_tags_served = ConcurrentHashMap.newKeySet();

    public Set<String> getSra_unlock_tags_served() {
        return sra_unlock_tags_served;
    }

    public static String sraUnlockTagKey(int phase, int peer_idx) {
        return phase + ":" + peer_idx;
    }

    public static int phaseForStreet(int street, boolean isRabbit) {
        if (street == Crupier.FLOP) {
            return isRabbit ? UNLOCK_PHASE_RABBIT_FLOP : UNLOCK_PHASE_FLOP;
        }
        if (street == Crupier.TURN) {
            return isRabbit ? UNLOCK_PHASE_RABBIT_TURN : UNLOCK_PHASE_TURN;
        }
        return isRabbit ? UNLOCK_PHASE_RABBIT_RIVER : UNLOCK_PHASE_RIVER;
    }

    public boolean hasMegaPacket() {
        return local_mega_packet != null;
    }

    // Tiempo máximo que el handler de REQ_SRA_UNLOCK_BATCH espera a que el
    // Crupier local avance hasta la calle exigida por la phase del batch
    // antes de tratarlo como maniobra del host (early-cascade attack).
    // Generoso: cubre clientes lentos, redes con jitter alto y manos con
    // muchos jugadores remotos. Un host honesto nunca rebasa este margen
    // porque su propia cascade se dispara justo después de su rondaApuestas
    // local cerrar, y eso espera al último ACTION que el cliente también
    // recibió por el mismo socket.
    public static final long UNLOCK_WAIT_TIMEOUT_MS = 60000L;

    // Único punto desde el que se modifica street; publica la transición
    // bajo protocol_state_lock para que awaitStreetForUnlockPhase no se
    // pierda el cambio. Llamar siempre por aquí en lugar de tocar el
    // campo directamente.
    private void setStreetLocal(int s) {
        synchronized (protocol_state_lock) {
            this.street = s;
            protocol_state_lock.notifyAll();
        }
    }

    // Idem para show_time (cubre las phases RABBIT_*).
    public void setShowTime(boolean v) {
        synchronized (protocol_state_lock) {
            this.show_time = v;
            protocol_state_lock.notifyAll();
        }
    }

    // Idem para conta_mano. Necesario para que el wait con hand_id pueda
    // distinguir un comando de mano antigua que llega retrasado (drop
    // silencioso) de un timeout genuino (ataque, lockdown). Todas las
    // mutaciones de conta_mano pasan por aquí.
    private void setContaManoLocal(int value) {
        synchronized (protocol_state_lock) {
            this.conta_mano = value;
            protocol_state_lock.notifyAll();
        }
    }

    /**
     * Espera bloqueante hasta que el Crupier local haya progresado lo
     * suficiente para que sea seguro servir un REQ_SRA_UNLOCK_BATCH de la phase
     * pedida, o hasta agotar el timeout.
     *
     * Gateo zero-trust contra el "early-cascade attack": un host malicioso
     * (con el código modificado, y dado que CoronaPoker es 100% open source
     * cualquiera puede compilar una versión hostil) podría adelantar la
     * cascade de FLOP/TURN/RIVER antes de jugar la calle previa, leer las
     * cartas comunitarias y jugar el pre-flop con conocimiento del board.
     * El state machine por sí solo no lo detecta (la cascade está bien
     * formada, sólo va prematura). Esta espera fuerza que el cliente sólo
     * sirva la clave cuando su propia ronda local ha cerrado la calle
     * anterior — el cliente es la fuente de verdad de "hemos jugado el
     * pre-flop".
     *
     * Devuelve un código discreto para que el caller distinga las tres
     * salidas posibles (READY, STALE_HAND, TIMEOUT) y aplique distinta
     * política de seguridad — un comando residual de una mano ya pasada
     * NO es trampa (drop silencioso), pero un timeout legítimo sí lo es.
     */
    public UnlockWaitResult awaitStreetForUnlockPhase(int phase, int hand_id, long timeoutMs) {
        synchronized (protocol_state_lock) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                if (Crupier.SECURITY_LOCKDOWN) {
                    return UnlockWaitResult.LOCKDOWN;
                }
                if (isFin_de_la_transmision()) {
                    return UnlockWaitResult.STALE_HAND;
                }
                if (hand_id != this.conta_mano) {
                    // Mano de la request no coincide con la actual: o
                    // viene de una mano ya cerrada (drop silencioso, no
                    // es ataque) o el host nos está adelantando (raro y
                    // tampoco sirve para nada; lo tratamos igual).
                    return UnlockWaitResult.STALE_HAND;
                }
                if (isUnlockPhaseStateSafe(phase)) {
                    return UnlockWaitResult.READY;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return UnlockWaitResult.TIMEOUT;
                }
                try {
                    protocol_state_lock.wait(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return UnlockWaitResult.TIMEOUT;
                }
            }
        }
    }

    public enum UnlockWaitResult {
        READY,        // listo para servir, las gates posteriores deciden
        STALE_HAND,   // hand_id no coincide con la mano actual o se canceló: drop silencioso
        TIMEOUT,      // expiró el deadline esperando a la calle: ataque
        LOCKDOWN      // lockdown ya activo, no servir nada más
    }

    // Predicado interno: ¿el estado local del Crupier admite ya servir
    // esta phase? POCKET es siempre seguro (es el primer paso de la mano);
    // FLOP/TURN/RIVER exigen que el propio rondaApuestas haya avanzado la
    // calle correspondiente; los RABBIT_* exigen show_time. Llamado bajo
    // protocol_state_lock.
    private boolean isUnlockPhaseStateSafe(int phase) {
        switch (phase) {
            case UNLOCK_PHASE_POCKET:
                return true;
            case UNLOCK_PHASE_FLOP:
                return this.street >= FLOP;
            case UNLOCK_PHASE_TURN:
                return this.street >= TURN;
            case UNLOCK_PHASE_RIVER:
                return this.street >= RIVER;
            case UNLOCK_PHASE_RABBIT_FLOP:
            case UNLOCK_PHASE_RABBIT_TURN:
            case UNLOCK_PHASE_RABBIT_RIVER:
                return this.show_time;
            default:
                return false;
        }
    }

    /**
     * Zero-trust gate for REQ_SRA_UNLOCK_BATCH items (validated one by one).
     *
     * The host declares which slot it is asking the client to unlock via
     * (phase, peer_idx, hand_id). En v3 cada recipient (pocket o comunitaria)
     * tiene su propia copia per-destinatario, por lo que peer_idx siempre
     * identifica al destinatario en el ring para CUALQUIER phase. El cliente
     * valida:
     *   1) MEGAPACKET ya recibido (sin él no hay nada que unlockear).
     *   2) hand_id == conta_mano (anti replay cross-hand).
     *   3) (phase, peer_idx) no ha sido servida ya esta mano (anti-reuse).
     *   4) payload length coherente con phase.
     *   5) state machine: la phase es legal AHORA según flags locales.
     *   6) peer_idx ∈ [0, ring_size) y ring[peer_idx] != mi_nick. Con per-recipient
     *      copies, pedirme que abra mi propia copia equivaldría a extraer mis
     *      cartas privadas o mi propio piece comunitario.
     *
     * El caller registra la tag-key en sra_unlock_tags_served DESPUÉS de responder.
     */
    public boolean isSraUnlockRequestLegitimate(int phase, int peer_idx, int hand_id, int length) {
        if (this.local_mega_packet == null) {
            return false;
        }
        if (hand_id != this.conta_mano) {
            return false;
        }
        if (this.sra_unlock_tags_served.contains(sraUnlockTagKey(phase, peer_idx))) {
            return false;
        }
        if (this.active_crypto_ring == null
                || peer_idx < 0 || peer_idx >= this.active_crypto_ring.length) {
            return false;
        }
        String targetNick = this.active_crypto_ring[peer_idx];
        String myNick = GameFrame.getInstance().getNick_local();
        if (targetNick == null || targetNick.equals(myNick)) {
            return false;
        }
        switch (phase) {
            case UNLOCK_PHASE_POCKET:
                return length == 64;
            // Para TURN/RIVER usamos sra_unlock_tags_served (lo que YO he servido)
            // en lugar de los flags flop_revealed/turn_revealed (que se setean al
            // recibir el broadcast del host). Si dependiéramos del broadcast, un
            // host malicioso podría enviar bytes sin haber hecho la cascada
            // de unlock y abrir la phase TURN sin más. Con esta versión, sólo
            // TÚ puedes avanzar el state machine sirviendo el tag legítimo.
            //
            // El anti-reuse usa la tag compuesta (phase:peer_idx); el predicate
            // "sirví alguna tag de la phase anterior" exige containsAny sobre
            // todas las peer_idx posibles de esa phase.
            case UNLOCK_PHASE_FLOP:
                return length == 96 && !this.flop_revealed;
            case UNLOCK_PHASE_TURN:
                return length == 32
                        && servedAnyForPhase(UNLOCK_PHASE_FLOP)
                        && !this.turn_revealed;
            case UNLOCK_PHASE_RIVER:
                return length == 32
                        && servedAnyForPhase(UNLOCK_PHASE_TURN)
                        && !this.river_revealed;
            case UNLOCK_PHASE_RABBIT_FLOP:
                return length == 96 && this.show_time && !this.flop_revealed;
            case UNLOCK_PHASE_RABBIT_TURN:
                return length == 32 && this.show_time && !this.turn_revealed;
            case UNLOCK_PHASE_RABBIT_RIVER:
                return length == 32 && this.show_time && !this.river_revealed;
            default:
                return false;
        }
    }

    // ¿He servido AL MENOS UNA tag (phase:*) de esta phase esta mano? Indica
    // que el cascade previo de la calle anterior ya pasó por mí al menos para
    // un recipient — equivalente al containsAny implícito que antes hacíamos
    // con tags no compuestas.
    private boolean servedAnyForPhase(int phase) {
        String prefix = phase + ":";
        for (String tag : this.sra_unlock_tags_served) {
            if (tag.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public void showPlayerCards(String nick, String sraKeyB64) {
        synchronized (lock_mostrar) {
            // Nota: ya no gateamos con show_time. El SHOWCARDS puede llegar en la cola de drenaje
            // del IWTSTH cuando show_time ya está cerrado por la temporización del showdown.
            // La función es idempotente (chequea isTapada()) y no causa efectos negativos si llega tarde.
            {
                Player jugador = nick2player.get(nick);
                if (jugador == null) {
                    return;
                }

                // BLINDAJE V61: Si el server nos hace un echo de nuestro propio paquete en un cliente remoto, LO IGNORAMOS.
                if (!GameFrame.getInstance().isPartida_local() && jugador.equals(GameFrame.getInstance().getLocalPlayer())) {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                    return;
                }

                if (jugador.getHoleCard1().isTapada()) {
                    // ZERO-TRUST SRA: Usamos la clave recibida para descifrar localmente
                    boolean decrypted = false;
                    if (sraKeyB64 != null && !sraKeyB64.equals("*")) {
                        try {
                            byte[] sraKey = Base64.getDecoder().decode(sraKeyB64);
                            if (sraKey.length == 32) {
                                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                                if (p != null) {
                                    p.setSra_unlock(sraKey);
                                }
                                decrypted = unlockPlayerCardsWithSRAKey(jugador);
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error decrypting SRA SHOWCARDS for " + nick, e);
                        }
                    }

                    if (decrypted) {
                        jugador.ordenarCartas();
                        jugador.destaparCartas(true);

                        ArrayList<Card> evaluationList = new ArrayList<>();
                        evaluationList.addAll(jugador.getHoleCards());
                        for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                            if (!c.isTapada()) {
                                evaluationList.add(c);
                            }
                        }

                        Hand jugada = null;
                        try {
                            jugada = new Hand(evaluationList);
                            jugador.showCards(jugada.getName());
                        } catch (Exception e) {
                        }

                        if (GameFrame.SONIDOS_CHORRA && jugador.getDecision() == Player.FOLD) {
                            Audio.playWavResource("misc/showyourcards.wav");
                        }

                        if (!perdedores.containsKey(jugador)) {
                            GameFrame.getInstance().getRegistro().print(nick + " " + Translator.translate("ui.muestra_2") + Card.collection2String(jugador.getHoleCards()) + ")" + (jugada != null ? " -> " + jugada : ""));
                        }

                        sqlNewShowcards(jugador.getNickname(), jugador.getDecision() == Player.FOLD);
                        sqlUpdateShowdownHand(jugador, jugada);
                    }
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                } else {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                }
            }
        }
    }

    public int getJugadoresCalentando() {

        int t = 0;

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isCalentando()) {
                t++;
            }
        }

        return t;
    }

    public int getJugadoresActivos() {

        int t = 0;

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.isActivo()) {
                t++;
            }
        }

        return t;
    }

    private void killAllPlayerTimers() {
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p != null) {
                p.stopActionTimer();
            }
        }
    }

    private void sqlSyncRecoveryShells(java.util.HashMap<String, Object> map) {
        if (map == null) {
            return;
        }

        synchronized (GameFrame.SQL_LOCK) {
            try {
                // 1. Ensure the Game record exists locally
                String sqlGame = "INSERT OR IGNORE INTO game(id, start, players, buyin, sb, blinds_time, rebuy, server, blinds_time_type, ugi, local) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement stG = Helpers.getSQLITE().prepareStatement(sqlGame)) {
                    stG.setInt(1, this.sqlite_id_game);
                    stG.setLong(2, map.get("start") != null ? (long) map.get("start") : System.currentTimeMillis());
                    stG.setString(3, (String) map.get("preflop_players"));
                    stG.setInt(4, map.get("buyin") != null ? (int) map.get("buyin") : 100);
                    stG.setFloat(5, map.get("sbval") != null ? (float) map.get("sbval") : 0.1f);
                    stG.setInt(6, map.get("blinds_time") != null ? (int) map.get("blinds_time") : 0);
                    stG.setBoolean(7, map.get("rebuy") != null ? (boolean) map.get("rebuy") : true);
                    stG.setString(8, (String) map.get("server"));
                    stG.setInt(9, map.get("blinds_time_type") != null ? (int) map.get("blinds_time_type") : 0);
                    stG.setString(10, GameFrame.UGI);
                    stG.setInt(11, 0);
                    stG.executeUpdate();
                }

                // 2. Ensure the Hand record exists locally
                String sqlHand = "INSERT OR IGNORE INTO hand(id, id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement stH = Helpers.getSQLITE().prepareStatement(sqlHand)) {
                    stH.setInt(1, this.sqlite_id_hand);
                    stH.setInt(2, this.sqlite_id_game);
                    stH.setInt(3, map.get("conta_mano") != null ? (int) map.get("conta_mano") : 1);
                    stH.setFloat(4, map.get("sbval") != null ? (float) map.get("sbval") : 0.1f);
                    stH.setInt(5, map.get("blinds_double") != null ? (int) map.get("blinds_double") : 0);
                    stH.setString(6, (String) map.get("dealer"));
                    stH.setString(7, (String) map.get("sb"));
                    stH.setString(8, (String) map.get("bb"));
                    stH.setLong(9, System.currentTimeMillis());
                    stH.setString(10, (String) map.get("preflop_players"));
                    stH.executeUpdate();
                }

                LOGGER.log(Level.INFO, "Local database shells synchronized with server IDs.");
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to sync local SQL shell records", ex);
            }
        }
    }

    private void recuperarDatosClavePartida() {
        LOGGER.log(Level.INFO, "[ZERO-TRUST DEBUG] Starting recuperarDatosClavePartida...");

        for (Player j : GameFrame.getInstance().getJugadores()) {
            if (j.getNickname().startsWith("CoronaBot$") && !GameFrame.getInstance().getParticipantes().containsKey(j.getNickname())) {
                Participant dummy = new Participant(GameFrame.getInstance().getSala_espera(), j.getNickname(), null, null, null, null, true);
                GameFrame.getInstance().getParticipantes().put(j.getNickname(), dummy);
            }
        }

        for (Player p : GameFrame.getInstance().getJugadores()) {
            nick2player.putIfAbsent(p.getNickname(), p);
        }

        killAllPlayerTimers();
        turno = System.currentTimeMillis();

        java.util.HashMap<String, Object> map;
        saltar_primera_mano = false;

        if (GameFrame.getInstance().isPartida_local()) {
            map = sqlRecoverServerLocalGameKeyData(true);
            if (map == null) {
                return;
            }

            if (map.get("start") != null) {
                GameFrame.GAME_START_TIMESTAMP = (long) map.get("start");
            }

            java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (!jugador.equals(GameFrame.getInstance().getLocalPlayer()) && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                    pendientes.add(jugador.getNickname());
                }
            }

            if (map.get("preflop_players") != null) {
                String[] preflop = ((String) map.get("preflop_players")).split("#");
                for (String b64 : preflop) {
                    if (b64.isEmpty()) {
                        continue;
                    }
                    try {
                        String n = new String(Base64.getDecoder().decode(b64), "UTF-8");
                        Player p = nick2player.get(n);
                        if (p == null || !p.isActivo()) {
                            saltar_primera_mano = true;
                            break;
                        }
                    } catch (Exception e) {
                    }
                }
            }

            if (!saltar_primera_mano && map.get("hand_end") != null && (Long) map.get("hand_end") == 0L) {
                try {
                    String fosil = Helpers.loadHandFossil(this.sqlite_id_game);

                    if (fosil != null && fosil.contains("#")) {
                        String orderMap = null;
                        String[] sraFossilParts = fosil.split("#");
                        byte[] megaPacket = null;

                        for (String part : sraFossilParts) {
                            if (part.startsWith("ORDER@")) {
                                orderMap = part.substring("ORDER@".length());
                            } else if (part.startsWith("FULLMEGAPACKET@")) {
                                megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                            } else if (part.startsWith("SRAKEYS@")) {
                                this.local_sra_unlock = Base64.getDecoder().decode(part.substring("SRAKEYS@".length()));
                                Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                                if (myP != null) {
                                    myP.setSra_unlock(this.local_sra_unlock);
                                }
                            } else if (part.startsWith("BOTKEYS@")) {
                                String[] bKeys = part.substring("BOTKEYS@".length()).split(",");
                                for (String bk : bKeys) {
                                    if (bk.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = bk.split(":");
                                    try {
                                        String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        byte[] bUnlock = Base64.getDecoder().decode(pair[1]);
                                        Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                        if (pBot != null) {
                                            pBot.setReceived_token(bUnlock);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("BOTVISUAL@")) {
                                String[] bVisuals = part.substring("BOTVISUAL@".length()).split("@");
                                for (String bv : bVisuals) {
                                    if (bv.isEmpty()) {
                                        continue;
                                    }
                                    String[] pair = bv.split(":");
                                    try {
                                        String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                        String[] cards = pair[1].split(",");
                                        Player botPlayer = nick2player.get(bNick);
                                        if (botPlayer != null) {
                                            // BOTVISUAL almacena getCartaComoEntero() (rango 1..52) — no sumar +1 al restaurar.
                                            botPlayer.getHoleCard1().iniciarConValorNumerico(Integer.parseInt(cards[0]));
                                            botPlayer.getHoleCard2().iniciarConValorNumerico(Integer.parseInt(cards[1]));

                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } else if (part.startsWith("VISUAL@")) {
                                String[] vis = part.substring("VISUAL@".length()).split(",");
                                try {
                                    byte v0 = Byte.parseByte(vis[0]);
                                    byte v1 = Byte.parseByte(vis[1]);
                                    if (v0 >= 0 && v0 < 52 && v1 >= 0 && v1 < 52) {
                                        this.local_original_cards[0] = v0;
                                        this.local_original_cards[1] = v1;
                                    } else {
                                        LOGGER.log(Level.WARNING, "VISUAL@ skipped — out-of-range values ({0},{1}) from poisoned fossil (likely MISDEAL before card decryption)", new Object[]{v0, v1});
                                    }
                                } catch (NumberFormatException nfe) {
                                    LOGGER.log(Level.WARNING, "VISUAL@ unparseable: {0}", part);
                                }
                            }
                        }

                        if (orderMap != null && megaPacket != null) {
                            this.local_mega_packet = megaPacket;
                            String[] orderTokens = orderMap.split(",");
                            java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                            for (String token : orderTokens) {
                                if (!token.isEmpty()) {
                                    ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                }
                            }
                            this.active_crypto_ring = ringList.toArray(new String[0]);

                            byte[] visual = this.local_original_cards;
                            if (visual != null && visual.length == 2) {
                                Player myPlayer = GameFrame.getInstance().getLocalPlayer();
                                myPlayer.getHoleCard1().iniciarConValorNumerico((visual[0] & 0xFF) + 1);
                                myPlayer.getHoleCard2().iniciarConValorNumerico((visual[1] & 0xFF) + 1);
                                myPlayer.getHoleCard1().destapar(false);
                                myPlayer.getHoleCard2().destapar(false);
                            }
                        }
                    }
                } catch (Exception e) {
                    saltar_primera_mano = true;
                }
            } else {
                saltar_primera_mano = true;
            }
            // Si el fosil existia pero no llego a entregar mega_packet+ring
            // utilizables (corrupto o vacio por MISDEAL+rollback previo), no
            // hay material para replay. Forzar saltar=true asegura simetria
            // con el branch cliente (recibira map sin megapacket y tambien
            // saltara).
            if (!saltar_primera_mano && (this.local_mega_packet == null || this.active_crypto_ring == null)) {
                saltar_primera_mano = true;
            }
            enviarDatosClaveRecuperados(pendientes, map);
        } else {
            map = recibirDatosClaveRecuperados();
            if (map != null && map.get("hand_id") != null) {
                this.sqlite_id_hand = (int) map.get("hand_id");
            }
            sqlSyncRecoveryShells(map);
            // Solo cargar fosil si la mano realmente estaba en-curso
            // (hand_end == 0L). Si la mano esta cerrada (post-MISDEAL la
            // marca con end!=0 via rollbackAbortedHand) NO se carga
            // local_mega_packet/ring desde el fosil viejo — sino el
            // DECK_CASCADE_REQ de la mano FRESH del host disparaba
            // hasMegaPacket()=true en cliente y triggerSecurityLockdown
            // como falso positivo "MEGAPACKET already locked".
            // El HOST ya tiene este mismo check arriba (linea ~2671);
            // sin el equivalente aqui en CLIENTE, race condition garantizada.
            boolean handInProgress = map != null
                    && map.get("hand_end") != null
                    && (Long) map.get("hand_end") == 0L;
            try {
                String fosil = handInProgress ? Helpers.loadHandFossil(this.sqlite_id_game) : null;
                if (fosil != null && fosil.contains("#")) {
                    String orderMap = null;
                    String[] sraFossilParts = fosil.split("#");
                    byte[] megaPacket = null;

                    for (String part : sraFossilParts) {
                        if (part.startsWith("ORDER@")) {
                            orderMap = part.substring("ORDER@".length());
                        } else if (part.startsWith("FULLMEGAPACKET@")) {
                            megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                        } else if (part.startsWith("SRAKEYS@")) {
                            this.local_sra_unlock = Base64.getDecoder().decode(part.substring("SRAKEYS@".length()));
                            Participant myP = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                            if (myP != null) {
                                myP.setSra_unlock(this.local_sra_unlock);
                            }
                        } else if (part.startsWith("BOTKEYS@")) {
                            String[] bKeys = part.substring("BOTKEYS@".length()).split(",");
                            for (String bk : bKeys) {
                                if (bk.isEmpty()) {
                                    continue;
                                }
                                String[] pair = bk.split(":");
                                try {
                                    String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    byte[] bUnlock = Base64.getDecoder().decode(pair[1]);
                                    Participant pBot = GameFrame.getInstance().getParticipantes().get(bNick);
                                    if (pBot != null) {
                                        pBot.setReceived_token(bUnlock);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("BOTVISUAL@")) {
                            String[] bVisuals = part.substring("BOTVISUAL@".length()).split("@");
                            for (String bv : bVisuals) {
                                if (bv.isEmpty()) {
                                    continue;
                                }
                                String[] pair = bv.split(":");
                                try {
                                    String bNick = new String(Base64.getDecoder().decode(pair[0]), "UTF-8");
                                    String[] cards = pair[1].split(",");
                                    Player botPlayer = nick2player.get(bNick);
                                    if (botPlayer != null) {
                                        // BOTVISUAL almacena getCartaComoEntero() (rango 1..52) — no sumar +1 al restaurar.
                                        botPlayer.getHoleCard1().iniciarConValorNumerico(Integer.parseInt(cards[0]));
                                        botPlayer.getHoleCard2().iniciarConValorNumerico(Integer.parseInt(cards[1]));

                                    }
                                } catch (Exception e) {
                                }
                            }
                        } else if (part.startsWith("VISUAL@")) {
                            String[] vis = part.substring("VISUAL@".length()).split(",");
                            try {
                                byte v0 = Byte.parseByte(vis[0]);
                                byte v1 = Byte.parseByte(vis[1]);
                                if (v0 >= 0 && v0 < 52 && v1 >= 0 && v1 < 52) {
                                    this.local_original_cards[0] = v0;
                                    this.local_original_cards[1] = v1;
                                } else {
                                    LOGGER.log(Level.WARNING, "VISUAL@ skipped — out-of-range values ({0},{1}) from poisoned fossil (likely MISDEAL before card decryption)", new Object[]{v0, v1});
                                }
                            } catch (NumberFormatException nfe) {
                                LOGGER.log(Level.WARNING, "VISUAL@ unparseable: {0}", part);
                            }
                        }
                    }

                    if (orderMap != null && megaPacket != null) {
                        this.local_mega_packet = megaPacket;
                        String[] orderTokens = orderMap.split(",");
                        java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                        for (String token : orderTokens) {
                            if (!token.isEmpty()) {
                                ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                            }
                        }
                        this.active_crypto_ring = ringList.toArray(new String[0]);

                        byte[] visual = this.local_original_cards;
                        if (visual != null && visual.length == 2) {
                            Player myPlayer = GameFrame.getInstance().getLocalPlayer();
                            myPlayer.getHoleCard1().iniciarConValorNumerico((visual[0] & 0xFF) + 1);
                            myPlayer.getHoleCard2().iniciarConValorNumerico((visual[1] & 0xFF) + 1);
                            myPlayer.getHoleCard1().destapar(false);
                            myPlayer.getHoleCard2().destapar(false);
                        }
                    }
                }
            } catch (Exception e) {
            }

            // Simetrico con la decision del host (rama partida_local de arriba,
            // checks de map.get("hand_end") y del fosil): solo se intenta replay
            // de la mano interrumpida cuando hand_end existe explicitamente y
            // vale 0 (mano-en-curso real) Y el fosil entrega un megapacket
            // utilizable. Si la mano fue rolled-back tras MISDEAL, hand_end
            // viene null o sin row, y el fosil queda vacio: ahi NO hay nada
            // que replay, hay que arrancar mano fresh. Sin este branch el
            // cliente quedaba esperando datos de una mano inexistente mientras
            // el host ya habia llamado sqlNewHand() -> cuelgue indefinido y
            // stack 0 en GUI (de ahi el "ESPECTADOR no calentando").
            if (map == null
                    || map.get("hand_end") == null
                    || (Long) map.get("hand_end") != 0L
                    || this.local_mega_packet == null
                    || this.active_crypto_ring == null) {
                saltar_primera_mano = true;
            } else {
                this.game_recovered = 1;
            }
        }

        if (map != null) {
            // rs.getInt/getLong devuelven 0 cuando la columna SQL es NULL (no
            // null) y map.put usa primitivos autoboxed por lo que !=null
            // siempre es true. Sin estos guards extra, un recovery sobre SQL
            // sin row (post-MISDEAL con la mano ya cerrada por
            // rollbackAbortedHand) sobreescribia GameFrame.BUYIN/CIEGAS a 0
            // -> players sin dinero ni blinds correctos.
            int recoveredHandId = map.get("hand_id") != null ? (int) map.get("hand_id") : -1;
            this.sqlite_id_hand = recoveredHandId > 0 ? recoveredHandId : -1;
            int recoveredBuyin = map.get("buyin") != null ? (int) map.get("buyin") : 0;
            if (recoveredBuyin > 0) {
                GameFrame.BUYIN = recoveredBuyin;
            }
            if (map.get("rebuy") != null) {
                GameFrame.REBUY = (boolean) map.get("rebuy");
            }
            int recoveredContaMano = map.get("conta_mano") != null ? (int) map.get("conta_mano") : 0;
            if (recoveredContaMano > 0) {
                setContaManoLocal(recoveredContaMano);
            }
            float recoveredSb = map.get("sbval") != null ? (float) map.get("sbval") : 0f;
            if (recoveredSb > 0f) {
                this.ciega_pequeña = recoveredSb;
            }
            float recoveredBb = map.get("bbval") != null ? (float) map.get("bbval") : 0f;
            if (recoveredBb > 0f) {
                this.ciega_grande = recoveredBb;
            }
            this.ciegas_double = map.get("blinds_double") != null ? (int) map.get("blinds_double") : 0;
            if (map.get("play_time") != null) {
                GameFrame.getInstance().setConta_tiempo_juego((long) map.get("play_time"));
            }

            if (map.get("balance") != null) {
                String[] bal = ((String) map.get("balance")).split("@");
                java.util.ArrayList<String> nicksRec = new java.util.ArrayList<>();
                for (String d : bal) {
                    if (d.isEmpty()) {
                        continue;
                    }
                    String[] p = d.split("\\|");
                    try {
                        String name = new String(Base64.getDecoder().decode(p[0]), "UTF-8");
                        nicksRec.add(name);
                        Player jug = nick2player.get(name);
                        if (jug != null) {
                            jug.setStack(Float.parseFloat(p[1]));
                            jug.setBuyin(Integer.parseInt(p[2]));
                            jug.setBet(0f);
                            this.auditor.put(name, new Float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                            if (Helpers.float1DSecureCompare(0f, jug.getStack()) == 0) {
                                jug.setSpectator(null);
                            }
                        } else {
                            this.auditor.put(name, new Float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                        }
                        if (p.length > 3) {
                            int rc = Integer.parseInt(p[3]);
                            if (rc > 0) {
                                rebuy_counts.put(name, rc);
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                java.util.List<String> cryptoRingList = this.active_crypto_ring != null ? java.util.Arrays.asList(this.active_crypto_ring) : null;

                for (Player j : GameFrame.getInstance().getJugadores()) {
                    boolean inBalance = nicksRec.contains(j.getNickname());
                    boolean inRing = cryptoRingList != null && cryptoRingList.contains(j.getNickname());

                    if (Helpers.float1DSecureCompare(0f, j.getStack()) == 0) {
                        j.setSpectator(null);
                    }

                    if (cryptoRingList != null) {
                        if (inRing) {
                            if (!inBalance) {
                                j.setStack(0f);
                                j.setBet(0f);
                                j.setSpectator(null);
                                this.auditor.put(j.getNickname(), new Float[]{0f, (float) j.getBuyin()});
                            } else if (j.isCalentando() && Helpers.float1DSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                        } else {
                            if (Helpers.float1DSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                            this.auditor.put(j.getNickname(), new Float[]{j.getStack(), (float) j.getBuyin()});
                        }
                    } else {
                        if (!inBalance) {
                            if (Helpers.float1DSecureCompare(0f, j.getStack()) < 0) {
                                j.setSpectator(Translator.translate("game.calentando"));
                            }
                            this.auditor.put(j.getNickname(), new Float[]{j.getStack(), (float) j.getBuyin()});
                        }
                    }
                }
            }

            if (this.getJugadoresActivos() < 2) {
                saltar_primera_mano = true;
            }
            this.dealer_nick = (String) map.get("dealer");
            this.small_blind_nick = (String) map.get("sb");
            this.big_blind_nick = (String) map.get("bb");

            if (!saltar_primera_mano) {
                int bb_pos = permutadoNick2Pos(this.big_blind_nick);
                if (bb_pos != -1) {
                    if (getJugadoresActivos() == 2) {
                        this.utg_nick = this.dealer_nick;
                    } else {
                        int utg_pos = bb_pos + 1;
                        String new_utg = permutadoPos2Nick(utg_pos);
                        while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {
                            new_utg = permutadoPos2Nick(++utg_pos);
                        }
                        this.utg_nick = new_utg;
                    }
                    for (Player jugador : GameFrame.getInstance().getJugadores()) {
                        jugador.refreshPos();
                    }
                }
            } else {
                setPositions();
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    jugador.refreshPos();
                }
            }
            actualizarContadoresTapete();
        }

        if (getJugadoresActivos() > 1 && !saltar_primera_mano) {
            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.stopLoopMp3("misc/background_music.mp3");
                Audio.playLoopMp3Resource("misc/recovering.mp3");
            }

            final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

            Helpers.GUIRun(() -> {
                if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                    GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);
                    GameFrame.getInstance().getTapete().repaint();
                }

                recover_dialog = new RecoverDialog(GameFrame.getInstance(), true);
                recover_dialog.setLocationRelativeTo(recover_dialog.getParent());
                recover_dialog.setVisible(true);

                if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                    GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);
                    GameFrame.getInstance().getTapete().repaint();
                }

                GameFrame.getInstance().getTapete().getCommunityCards().refreshLightsIcon();
            });

            if (GameFrame.getInstance().isPartida_local() || GameFrame.getInstance().getLocalPlayer().isActivo()) {
                recuperarAccionesLocales();
            }

            // sincronizando_mano debe activarse solo si TENGO acciones MIAS
            // (o de bots locales) que replicar visualmente via
            // siguienteAccionLocalRecuperada — esa es la unica ruta que
            // cierra el dragon dialog cuando la queue se vacia. Antes el
            // check era sobre tot_acciones_recuperadas (TODAS las acciones
            // de TODOS los peers de la mano): si la mano tenia acciones de
            // otros pero ninguna mia (caso clasico: me desconecte antes de
            // actuar), sincronizando_mano quedaba latched y el dragon
            // nunca se cerraba. Quien NO tiene acciones que replicar es un
            // observador del replay y no necesita el dialog.
            if (!this.acciones_locales_recuperadas.isEmpty()) {
                this.sincronizando_mano = true;
            } else {
                GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

                if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                    Audio.playWavResource("misc/startplay.wav");
                }

                if (GameFrame.MUSICA_AMBIENTAL) {
                    Audio.stopLoopMp3("misc/recovering.mp3");
                    Audio.playLoopMp3Resource("misc/background_music.mp3");
                }
                Helpers.GUIRun(() -> {
                    if (recover_dialog != null) {
                        recover_dialog.setVisible(false);
                        recover_dialog.dispose();
                        recover_dialog = null;
                    }
                    GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                    GameFrame.getInstance().refresh();
                });
            }
        } else {
            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));
            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                Audio.playWavResource("misc/startplay.wav");
            }
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
            });
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            jugador.setContaWin(this.sqlGetPlayerContaWins(jugador.getNickname(), this.sqlite_id_game));
        }

        this.update_game_seats = true;
        GameFrame.setRECOVER(false);

        if (getJugadoresActivos() > 1 && !saltar_primera_mano) {
            this.game_recovered = 1;
            GameFrame.getInstance().refresh();
        } else {
            this.game_recovered = 0;
        }
    }

    private void cancelarManoYDevolverApuestas(String motivo) {
        cancelarManoYDevolverApuestas(motivo, true);
    }

    public void cancelarManoYDevolverApuestas(String motivo, boolean broadcast) {
        // Idempotency: MISDEAL is intentionally double-fed on the
        // client side. WaitingRoomFrame.java:2015 invokes us directly from
        // the reader thread for an immediate refund + popup, then queues
        // the same MISDEAL command on received_commands so the Crupier
        // run() consumers (Crupier.java:4405/5091/7163) can break out of
        // their consensus/wait loops. Those consumers call us again with
        // the same motivo. Without an early return here, the second call
        // would re-log, re-print the registro, re-play the error sound and
        // re-queue another popup on the EDT (it would appear after the user
        // dismisses the first one). The first invocation already performed
        // the visible side effects; the second one is solely a signaling
        // breakout so just return.
        if (isFin_de_la_transmision()) {
            return;
        }
        LOGGER.log(Level.WARNING, "MISDEAL triggered: {0}", motivo);
        // Defense in depth: if a recovery dragon was left open (e.g. a
        // ZERO_TRUST cascade failure aborted the hand mid-replay) close it now so it
        // does not stay on screen and does not keep sincronizando_mano latched.
        cerrarRecoverDialogYSync();
        GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_anulada") + Translator.translate(motivo));
        GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_anulada_footer"));

        if (broadcast && GameFrame.getInstance().isPartida_local()) {
            try {
                String motivoB64 = Base64.getEncoder().encodeToString(motivo.getBytes("UTF-8"));
                broadcastGAMECommandFromServer("MISDEAL#" + motivoB64, null, true);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast MISDEAL to clients", ex);
            }
        }

        synchronized (getLock_contabilidad()) {
            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                // getBote() es el total invertido por el jugador en la mano actual,
                // sumando todas las streets.
                float refund = Helpers.floatClean(jugador.getBote());

                if (Helpers.float1DSecureCompare(refund, 0f) > 0) {
                    jugador.setStack(Helpers.floatClean(jugador.getStack()) + refund);
                    jugador.setBet(0f);
                    jugador.resetBote(); // Purge the financial memory for this aborted hand
                }
            }

            this.apuestas = 0f;
            this.bote_total = 0f;

            // Note: bote_sobrante is kept intact by design (it belongs to the global game, not the aborted hand)
            this.bote = new HandPot(0f);
        }

        Audio.playWavResource("misc/error.wav");

        // Coherent MISDEAL halt across host AND client.
        //
        // Both peers MUST:
        //   - Show the MISDEAL popup synchronously so the calling thread
        //     (Crupier thread on host, network reader thread on client)
        //     blocks until the user acknowledges. While the modal is up,
        //     nothing in that thread's callstack can advance towards a
        //     fresh NUEVA_MANO / cascade.
        //   - rollbackAbortedHand(): undo the conta_mano++ (NUEVA_MANO:3460)
        //     and the sqlNewHand insert (NUEVA_MANO:3547). Both ran on
        //     EVERY peer before the cascade failed. If we skipped this on
        //     the client, the client's local SQLite would still hold a
        //     hand row with end=0, and a future "Recover" from its main
        //     menu would try to replay a hand that was never really dealt.
        //   - setFin_de_la_transmision(true): halt the local run() loop
        //     before any new hand can start. ANY isFin_de_la_transmision()
        //     check downstream (NUEVA_MANO, repartir, rondaApuestas, SRA
        //     helpers, etc) bails out cleanly.
        //
        // Only the HOST then fires abortToRecover() which broadcasts
        // SERVEREXITRECOVER and triggers finTransmision -> RESET_GAME ->
        // Init.VENTANA_INICIO with the recover dialog auto-opened.
        //
        //
        // Clients do NOT broadcast — they are driven by receiving the
        // host's SERVEREXITRECOVER right after the reader thread resumes
        // from the popup. TCP guarantees the command arrives in order;
        // the reader thread blocking during the modal just delays delivery
        // by however long the user takes to click OK, which is harmless.
        //
        Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("game.mano_anulada") + " " + Translator.translate(motivo) + "<b>" + Translator.translate("game.mano_anulada_footer") + "</b>");
        rollbackAbortedHand();
        setFin_de_la_transmision(true);

        if (broadcast && GameFrame.getInstance().isPartida_local()) {
            // Violacion zero-trust = ataque o protocolo roto: la timba acaba,
            // BalanceDialog final, NO se vuelve a sala de espera. Cualquier otro
            // motivo (peer caido normal, etc.) sigue por el flujo de recover.
            if (motivo != null && motivo.startsWith("zero_trust.")) {
                abortAndExit();
            } else {
                abortAndRecover();
            }
        }
    }

    /**
     * Undo the conta_mano increment and the sqlNewHand insert that NUEVA_MANO
     * performed before the cascade/deal failed. After this method, the in-
     * memory counter and the SQLite state reflect the last successfully
     * completed hand, which is what recuperarDatosClavePartida should see on
     * the recover path. balance/action/showdown/showcards rows for this hand
     * are wiped automatically via ON DELETE CASCADE on the hand foreign key.
     */
    private void rollbackAbortedHand() {
        // La mano abortada se MARCA como terminada (end != 0, pot=0) +
        // balance row con stacks post-refund, en lugar de borrarse. Asi el
        // recovery encuentra una "ultima mano cerrada limpiamente" igual
        // que tras un exit con wait-for-hand-end. La siguiente NUEVA_MANO
        // arranca fresh con calcularPosiciones limpio. conta_mano y
        // sqlite_id_hand se preservan (la mano cuenta como completada).
        // Sin este UPDATE+INSERT (DELETE original), recovery encontraba
        // SQL vacio -> dealer_nick=null -> repartir() petaba +
        // balance vacio -> players quedaban spectator sin cartas.
        if (sqlite_id_hand > 0) {
            synchronized (GameFrame.SQL_LOCK) {
                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement("UPDATE hand SET end=?, pot=0 WHERE id=?")) {
                    statement.setQueryTimeout(30);
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setInt(2, sqlite_id_hand);
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to mark aborted hand as ended", ex);
                }
            }
            try {
                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (j != null && !j.isExit()) {
                        sqlNewHandBalance(j.getNickname(), Helpers.floatClean(j.getStack()), j.getBuyin());
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to persist post-MISDEAL balance", ex);
            }
        }
        // Refs in-memory: el Crupier sera destruido por RESET_GAME tras
        // abortAndRecover/abortAndExit. Limpieza por higiene.
        this.local_mega_packet = null;
        this.active_crypto_ring = null;
        this.local_sra_unlock = null;
        this.local_original_cards = new byte[2];
    }

    /**
     * Auto-trigger the exit-with-recover flow from the MISDEAL handler. Mirrors
     * what GameFrame.exit_menuActionPerformed does when the user clicks "wait
     * for hand end and exit" — except the hand is already aborted by MISDEAL,
     * so there is nothing to wait for. Runs in its own thread because
     * finTransmision -> RESET_GAME shuts down the very thread pool we are on.
     */
    private void abortAndRecover() {
        // Aborto controlado: host fuerza recover -> todos a sala de espera
        // con dialog recover auto-abierto. Para MISDEAL no-zero-trust (peer
        // caido, etc.) la timba puede continuar fresh.
        LOGGER.log(Level.WARNING, "[RECOVERY] abortAndRecover engaged — broadcasting SERVEREXITRECOVER and routing everyone to main menu with recover dialog");
        setForce_recover(true);
        Helpers.threadRun(() -> {
            try {
                String passSuffix = "";
                if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().getPassword() != null) {
                    passSuffix = "#" + Base64.getEncoder().encodeToString(
                            WaitingRoomFrame.getInstance().getPassword().getBytes("UTF-8"));
                }
                broadcastGAMECommandFromServer("SERVEREXITRECOVER" + passSuffix, null, false);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast SERVEREXITRECOVER from MISDEAL", ex);
            }
            GameFrame.getInstance().finTransmision(true);
        });
    }

    private void abortAndExit() {
        // Violacion zero-trust detectada: la timba acaba. Broadcast SERVEREXIT
        // (no RECOVER) y finTransmision sin force_recover -> BalanceDialog
        // final con el balance al momento del ultimo refund. No reconexion,
        // no sala de espera. Fin punto.
        LOGGER.log(Level.WARNING, "[ZERO-TRUST] abortAndExit engaged — broadcasting SERVEREXIT and routing everyone to BalanceDialog (game over)");
        // NO setForce_recover(true) — queremos que finTransmision detecte
        // force_recover=false y vaya a la rama BalanceDialog.
        Helpers.threadRun(() -> {
            try {
                broadcastGAMECommandFromServer("SERVEREXIT", null, false);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to broadcast SERVEREXIT from zero-trust MISDEAL", ex);
            }
            GameFrame.getInstance().finTransmision(true);
        });
    }

    private void recibirPosiciones() {

        // Guard de salida: si la transmisión termina (host caído, cliente
        // saliendo por SERVEREXITRECOVER, etc.) salimos sin haber recibido
        // POSITIONS. El caller (Crupier.run) ya tiene checks posteriores
        // que abortan limpiamente al ver isFin_de_la_transmision. Sin este
        // guard el cliente queda colgado para siempre esperando un
        // comando que nunca va a llegar.
        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 9 && partes[2].equals("POSITIONS")) {

                            ok = true;

                            try {
                                this.utg_nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");

                                this.big_blind_nick = new String(Base64.getDecoder().decode(partes[4]), "UTF-8");

                                this.small_blind_nick = new String(Base64.getDecoder().decode(partes[5]), "UTF-8");

                                this.dealer_nick = new String(Base64.getDecoder().decode(partes[6]), "UTF-8");

                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }

                            GameFrame.getInstance().setConta_tiempo_juego(Long.parseLong(partes[7]));

                            if (partes[8].equals("1")) {

                                this.doblarCiegas();
                            }

                        } else if (partes.length >= 3 && partes[2].equals("POSITIONS")) {
                            LOGGER.log(Level.WARNING, "POSITIONS malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receivePositions: " + comando, ex);
                    }

                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    start_time = System.currentTimeMillis();
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

    }

    public float getUltimo_raise() {
        return ultimo_raise;
    }

    public void actualizarCiegasManualmente(float sb, float bb, int double_val, int double_type) {
        synchronized (lock_ciegas) {

            if (this.ciega_pequeña != sb || this.ciega_grande != bb || GameFrame.CIEGAS_DOUBLE != double_val
                    || GameFrame.CIEGAS_DOUBLE_TYPE != double_type) {

                this.ciegas_update = new Object[]{sb, bb, double_val, double_type};

            } else {

                this.ciegas_update = null;
            }
        }
    }

    private float[] simulateNextBlinds() {
        int i = 0, j = 0;
        while (Helpers.float1DSecureCompare((float) this.ciega_pequeña / (float) Math.pow(10, j), CIEGAS[i][0]) != 0) {
            i = (i + 1) % CIEGAS.length;
            if (i == 0) {
                j++;
            }
        }
        i = (i + 1) % CIEGAS.length;
        if (i == 0) {
            j++;
        }
        return new float[]{(float) (CIEGAS[i][0] * Math.pow(10, j)), (float) (CIEGAS[i][1] * Math.pow(10, j))};
    }

    private boolean checkDoblarCiegas() {

        synchronized (lock_ciegas) {
            if (GameFrame.BLIND_CAP > 0f && simulateNextBlinds()[1] > GameFrame.BLIND_CAP) {
                return false;
            }
            if (GameFrame.CIEGAS_DOUBLE_TYPE <= 1) {
                return (GameFrame.CIEGAS_DOUBLE > 0
                        && (int) Math.floor((float) GameFrame.getInstance().getConta_tiempo_juego()
                                / (GameFrame.CIEGAS_DOUBLE * 60)) > this.ciegas_double);
            } else {
                return (GameFrame.CIEGAS_DOUBLE > 0 && this.conta_mano > 1
                        && ((int) Math.floor((float) (this.conta_mano - 1))
                        / GameFrame.CIEGAS_DOUBLE) > this.ciegas_double);
            }
        }
    }

    private void doblarCiegas() {

        int i, j;

        i = 0;

        j = 0;

        while (Helpers.float1DSecureCompare((float) ciega_pequeña / (float) (Math.pow(10, j)), CIEGAS[i][0]) != 0) {

            i = (i + 1) % CIEGAS.length;

            if (i == 0) {
                j++;
            }
        }

        i = (i + 1) % CIEGAS.length;

        if (i == 0) {
            j++;
        }

        this.ciegas_double++;

        this.ciega_pequeña = (float) (CIEGAS[i][0] * Math.pow(10, j));

        this.ciega_grande = (float) (CIEGAS[i][1] * Math.pow(10, j));

        Audio.playWavResource("misc/double_blinds.wav");

        GameFrame.getInstance().getRegistro().print(Translator.translate("blinds.se_doblan_las_ciegas"));

    }

    public float getBote_total() {
        return bote_total;
    }

    private void readyForNextHand() {
        // Limpieza entre manos del cache de pocket cards y de la cola de comandos
        // pendientes. La cola tiene que limpiarse bajo su propio monitor porque
        // el thread del Participant puede estar polleando entradas al mismo
        // tiempo (sync sobre received_commands en cada acceso). Sin synchronized
        // aquí podía aparecer ConcurrentModificationException o mensajes legítimos
        // de la mano siguiente quedarse en estado inconsistente.
        single_locked_pocket_cards.clear();

        synchronized (received_commands) {
            received_commands.clear();
        }

        // EC-Identity v1: the per-hand chain belongs to the hand that just ended. The
        // new hand seeds a fresh chain after its MEGAPACKET arrives.
        this.current_hand_id = null;
        this.hand_state_chain = null;

        // Local entropy for our SRA shuffle (never leaves this process). 48 bytes:
        // first 32 feed the AES-256 key, last 16 feed the CTR IV.
        byte[] jvm_entropy = new byte[48];

        if (Helpers.CSPRNG_GENERATOR != null) {
            Helpers.CSPRNG_GENERATOR.nextBytes(jvm_entropy);
        }

        this.local_hand_seed = jvm_entropy;

        if (GameFrame.getInstance().isPartida_local()) {
            // Espera a que todos los humanos conectados envíen HAND_READY. Sin
            // timeout artificial — clientes lentos en la red o en CPU NO se kickean;
            // la única salida del bucle es que estén ready o que su socket muera
            // (isExit() lo refleja, gestionado por su propio Participant.run()).
            boolean ready;
            do {
                ready = true;
                for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {
                    Participant p = entry.getValue();
                    if (p != null && !p.getNick().equals(GameFrame.getInstance().getNick_local())
                            && !p.isCpu() && !p.isExit() && p.getNew_hand_ready() <= this.conta_mano) {
                        ready = false;
                        break;
                    }
                }
                if (!ready) {
                    synchronized (lock_nueva_mano) {
                        try {
                            lock_nueva_mano.wait(NEW_HAND_READY_WAIT);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            } while (!ready && !isFin_de_la_transmision());

            // Host ordena a todos que pueden empezar a procesar la cascada SRA.
            broadcastGAMECommandFromServer("START_SRA_CASCADE", null, true);

        } else {
            // Cliente avisa de que está listo para la mano actual y espera la
            // señal del host SIN timeout. Si el host se cae, isFin_de_la_transmision
            // o el socket reader lo detectarán por su cuenta.
            this.sendGAMECommandToServer("HAND_READY#" + String.valueOf(this.conta_mano + 1));

            boolean serverCommitted = false;
            do {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!serverCommitted && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");
                        if (partes.length >= 3 && partes[2].equals("START_SRA_CASCADE")) {
                            serverCommitted = true;
                        } else {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }
                if (!serverCommitted && !isFin_de_la_transmision()) {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            } while (!serverCommitted && !isFin_de_la_transmision());
        }
    }

    public void RABBIT_HANDLER(String nick, int conta_rabbit) {

        Helpers.threadRun(() -> {

            synchronized (lock_rabbit) {

                rabbit_players.put(nick, true); // Lo ponemos en PENDING (para que el server pueda llevar la cuenta de
                // todos los que fueron procesados).

                if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                    destaparRabbitCards();
                }

                Player jugador = nick2player.get(nick);

                if (jugador != null) {

                    if (jugador instanceof RemotePlayer) {
                        RemotePlayer rp = (RemotePlayer) jugador;

                        Helpers.threadRun(() -> {

                            Helpers.GUIRunAndWait(() -> {

                                rp.setNotifyRabbitLabel();
                                rp.getChat_notify_label().setVisible(true);

                            });

                            synchronized (rp.getChat_notify_label()) {
                                Helpers.pausar(RABBIT_LABEL_TIMEOUT);

                                Helpers.GUIRun(() -> {

                                    rp.getChat_notify_label().setVisible(false);

                                });

                                rp.getChat_notify_label().notifyAll();
                            }

                        });

                    }

                    float stack = jugador.getStack();
                    float coste_rabbit = 0f;

                    synchronized (getLock_contabilidad()) {
                        if (GameFrame.RABBIT_HUNTING == 2 && conta_rabbit > 1) {
                            coste_rabbit = ciega_pequeña;
                            if (Helpers.float1DSecureCompare(stack, coste_rabbit) >= 0) {
                                bote_sobrante += coste_rabbit;
                                jugador.setStack(stack - coste_rabbit);
                            } else {
                                coste_rabbit = 0f;
                            }
                        } else if (GameFrame.RABBIT_HUNTING == 3) {
                            if (conta_rabbit == 2) {
                                coste_rabbit = ciega_pequeña;
                                if (Helpers.float1DSecureCompare(stack, coste_rabbit) >= 0) {
                                    bote_sobrante += coste_rabbit;
                                    jugador.setStack(stack - coste_rabbit);
                                } else {
                                    coste_rabbit = 0f;
                                }
                            } else if (conta_rabbit > 2) {
                                coste_rabbit = ciega_grande;
                                if (Helpers.float1DSecureCompare(stack, coste_rabbit) >= 0) {
                                    bote_sobrante += coste_rabbit;
                                    jugador.setStack(stack - coste_rabbit);
                                } else {
                                    coste_rabbit = 0f;
                                }
                            }
                        }
                    }

                    GameFrame.getInstance().getRegistro().print(nick + Translator.translate("rabbit.solicito_rabbit_hunting")
                            + "(" + Helpers.float2String(coste_rabbit) + ")");

                    if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                        // Si es una petición local calculamos mejor mano hipotética
                        ArrayList<Card> cartas = new ArrayList<>();

                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                            if (!carta_comun.isTapada()) {
                                cartas.add(carta_comun);
                            }
                        }

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_cartas_comunitarias")
                                        + Card.collection2String(cartas));

                        cartas = GameFrame.getInstance().getLocalPlayer().getHoleCards();

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_tu_mano_repartida")
                                        + Card.collection2String(cartas));

                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                            if (!carta_comun.isTapada()) {
                                cartas.add(carta_comun);
                            }
                        }

                        Hand jugada = new Hand(cartas);

                        GameFrame.getInstance().getLocalPlayer().setRabbitJugada(jugada.getName());

                        GameFrame.getInstance().getRegistro()
                                .print(Translator.translate("rabbit.rabbit_hunting_mejor_hipotetica_jugada")
                                        + Card.collection2String(jugada.getWinners()) + " (" + jugada.getName() + ")");

                    }

                    // Avisamos al server o al resto de jugadores si procede
                    String comando;
                    try {
                        comando = "RABBIT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + String.valueOf(conta_rabbit);

                        if (GameFrame.getInstance().isPartida_local()) {
                            // Si somos el sevidor re-enviamos el comando a todo el mundo.
                            broadcastGAMECommandFromServer(comando, nick);

                        } else if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                            // Si somos cliente enviamos comando al server en caso de que fuéramos nosotros
                            // los que pedimos las RABIT.
                            sendGAMECommandToServer(comando);
                        }

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

                rabbit_players.put(nick, false); // Petición Rabbit procesada

                lock_rabbit.notifyAll();

            }

        });

    }

    public Object getIwtsth_lock() {
        return lock_iwtsth;
    }

    public void IWTSTH_HANDLER(String iwtsther) {
        Helpers.threadRun(() -> {
            synchronized (lock_iwtsth) {
                if (iwtsthing || iwtsth) {
                    return;
                }
                iwtsthing = true;
                iwtsth = true;

                if (iwtsth_requests.containsKey(iwtsther)) {
                    iwtsth_requests.put(iwtsther, (int) iwtsth_requests.get(iwtsther) + 1);
                } else {
                    iwtsth_requests.put(iwtsther, 1);
                }

                int conta_iwtsth = (int) iwtsth_requests.get(iwtsther);

                GameFrame.getInstance().getRegistro().print(
                        iwtsther + Translator.translate("iwtsth.solicita_iwtsth") + String.valueOf(conta_iwtsth) + ")");

                Helpers.GUIRunAndWait(() -> {
                    if (GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado()) {
                        GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(false);
                    }
                    Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
                });

                if (GameFrame.getInstance().isPartida_local()) {
                    try {
                        broadcastGAMECommandFromServer(
                                "IWTSTH#" + Base64.getEncoder().encodeToString(iwtsther.getBytes("UTF-8")), null);
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

                if (GameFrame.CINEMATICAS) {
                    Helpers.GUIRun(() -> {
                        try {
                            GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true,
                                    new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth.gif")),
                                    Helpers.getGIFFramesCount(
                                            getClass().getResource("/cinematics/misc/iwtsth.gif").toURI().toURL()));
                            gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                            gif_dialog.setVisible(true);
                        } catch (URISyntaxException | IOException | ImageProcessingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    });
                    Helpers.pausar(500);
                    Audio.playWavResourceAndWait("misc/iwtsth.wav");
                } else {
                    Audio.playWavResourceAndWait("misc/iwtsth.wav");
                }

                if (GameFrame.getInstance().isPartida_local()) {
                    if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther)
                            || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                    iwtsther + Translator.translate("iwtsth.solicita_iwtsth")
                                    + String.valueOf(conta_iwtsth)
                                    + Translator.translate("ui.autorizamos"),
                                    new ImageIcon(Init.class.getResource("/images/action/robot.png"))) == 0) {
                        IWTSTH_SHOW(iwtsther, true);
                    } else {
                        IWTSTH_SHOW(iwtsther, false);
                    }
                }
            }
        });
    }

    public void IWTSTH_SHOW(String iwtsther, boolean authorized) {
        // NOTA: ya no gateamos con iwtsthing. El guard original silenciaba el flujo si iwtsthing era false
        // (p.ej. cuando IWTSTH_HANDLER no llegó a correr en un candidato remoto por race con show_time/pausaConBarra).
        // El caller (IWTSTH_HANDLER del host tras autorizar, o WaitingRoomFrame.case "IWTSTHSHOW" en clientes) ya
        // ha decidido que esto debe ejecutarse — no debe depender de un flag que pudo no setearse por timing.
        Helpers.threadRun(() -> {
            synchronized (lock_iwtsth) {
                {

                        // 1. If we are the Server Host, broadcast the verdict to all clients
                        if (GameFrame.getInstance().isPartida_local()) {
                            try {
                                broadcastGAMECommandFromServer("IWTSTHSHOW#" + Base64.getEncoder().encodeToString(iwtsther.getBytes("UTF-8")) + "#" + String.valueOf(authorized), null, true);
                            } catch (Exception ex) {
                            }
                        }

                        if (authorized) {
                            // El servidor no puede descifrar las cartas de los remotos (no tiene sus llaves SRA),
                            // así que solo autoriza la petición y cada cliente confiesa sus propias cartas.

                            // A) Local Player: si soy candidato (perdedor que no ha mostrado en el showdown) confieso.
                            // NOTAS:
                            //   - NO chequeamos isBoton_mostrar / isBotonMostrarActivado: esos flags reflejan el
                            //     estado del botón "Mostrar" voluntario y dependen de que IWTSTH_HANDLER haya
                            //     deshabilitado el botón (race con timing).
                            //   - NO chequeamos getHoleCard1().isTapada(): el LocalPlayer SIEMPRE ve sus propias
                            //     cartas (destapar() se llama en el reparto, línea 4320, y Card.tapar() nunca se
                            //     invoca en el código), así que isTapada() es false en el propio jugador desde el
                            //     reparto. Ese check es la perspectiva externa (otros viendo al candidato), no la
                            //     del propio jugador, y si lo dejamos aquí el SHOWCARDS NUNCA se envía.
                            //   - isMuestra() distingue "auto-show en showdown" (true, no hay que confesar nada)
                            //     vs "auto-muck/IWTSTH candidate" (false, hay que confesar).
                            LocalPlayer local = GameFrame.getInstance().getLocalPlayer();
                            if (local.isLoser() && !local.isMuestra()) {
                                showAndBroadcastPlayerCards(local.getNickname());
                                // Marcamos al jugador como "ya mostrado" tras la confesión forzada del IWTSTH.
                                // Sin esto, el cleanup de abajo vería isBoton_mostrar()=true && !isBotonMostrarActivado()
                                // && !isMuestra() y re-habilitaría el botón "Mostrar" voluntario, lo cual no tiene
                                // sentido — las cartas YA se mostraron forzosamente.
                                local.setMuestra(true);
                                local.desactivar_boton_mostrar();
                            }

                            // B) Bots: Since they live in the Host's memory, the Server Host forces them to show
                            if (GameFrame.getInstance().isPartida_local()) {
                                for (RemotePlayer rp : GameFrame.getInstance().getTapete().getRemotePlayers()) {
                                    Participant p = GameFrame.getInstance().getParticipantes().get(rp.getNickname());
                                    if (p != null && p.isCpu() && rp.isIwtsthCandidate() && rp.getHoleCard1().isTapada()) {
                                        showAndBroadcastPlayerCards(rp.getNickname());
                                    }
                                }
                            }
                        } else {
                            // If denied, inform the UI and register rejection timestamp for anti-flood
                            GameFrame.getInstance().getRegistro().print(Translator.translate("iwtsth.el_servidor_ha_denegado_la") + iwtsther);
                            if (GameFrame.CINEMATICAS) {
                                Helpers.GUIRun(() -> {
                                    try {
                                        GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance(), true, new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth_no.gif")), Helpers.getGIFFramesCount(getClass().getResource("/cinematics/misc/iwtsth_no.gif").toURI().toURL()));
                                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                        gif_dialog.setVisible(true);
                                    } catch (URISyntaxException | IOException | ImageProcessingException ex) {
                                    }
                                });
                            }
                            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther)) {
                                this.last_iwtsth_rejected = System.currentTimeMillis();
                            }
                        }
                    }
                }

                // Cleanup UI:
                // - Restauramos el botón de mostrar si procede
                // - Restauramos la barra al estado correcto de pausaConBarra (no solo setIndeterminate(false),
                //   que dejaría la barra con max=1/val=1 visualmente "al máximo, sin moverse").
                Helpers.GUIRunAndWait(() -> {
                    if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {
                        GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(true);
                    }
                });
                // Restaurar la barra al valor restante de pausaConBarra para que el bucle pueda
                // seguir decrementando correctamente sin verse "atascada al máximo".
                setTiempo_pausa(getTiempoPausa());

                // Release the lock to let the game proceed
                synchronized (lock_iwtsth) {
                    iwtsth = true;
                    iwtsthing = false;
                    iwtsthing_request = false;
                    lock_iwtsth.notifyAll();
                }
                // Despertamos también al loop de pausaConBarra que está dormido en lock_pausa_barra.wait(1000)
                // para que reanude su decremento sin tener que esperar el timeout.
                synchronized (lock_pausa_barra) {
                    lock_pausa_barra.notifyAll();
                }
            });
    }

    public boolean isIwtsthing_request() {
        return iwtsthing_request;
    }

    public void IWTSTH_REQUEST(String iwtsther) {

        if (this.show_time && (this.last_iwtsth_rejected == null
                || System.currentTimeMillis() - this.last_iwtsth_rejected > IWTSTH_ANTI_FLOOD_TIME)) {

            iwtsthing_request = true;

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (!GameFrame.getInstance().isPartida_local()) {
                this.sendGAMECommandToServer("IWTSTH");
            } else {
                IWTSTH_HANDLER(iwtsther);
            }

        } else {
            Helpers.mostrarMensajeError(GameFrame.getInstance(),
                    Translator.translate("ui.tienes_que_esperar")
                    + Helpers.seconds2FullTime(Math.round(((float) (IWTSTH_ANTI_FLOOD_TIME
                            - (System.currentTimeMillis() - this.last_iwtsth_rejected))) / 1000))
                    + Translator.translate("iwtsth.para_volver_a_solicitar_iwtsth"));
        }
    }

    public boolean isIWTSTH4LocalPlayerAuthorized() {

        return flop_players.contains(GameFrame.getInstance().getLocalPlayer());
    }

    public void destaparRabbitCards() {
        synchronized (lock_rabbit) {
            boolean hay_rabbits_tapadas = false;

            for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                if (carta.isRabbitTapada()) {
                    hay_rabbits_tapadas = true;
                    break;
                }
            }

            if (hay_rabbits_tapadas) {
                Audio.playWavResource("misc/uncover.wav", false);
                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                    carta.destaparRabbit();
                    // Hay que destapar la carta subyacente cuando el rabbit desaparece.
                    if (carta.getCartaComoEntero() >= 0) {
                        carta.destapar(false);
                    }
                }
            }
        }
    }

    private void setPotBackground(Color color) {
        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setOpaque(true);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setBackground(color);
        });
    }

    private boolean NUEVA_MANO() {

        this.local_sra_lock = null;
        this.local_sra_unlock = null;
        this.local_mega_packet = null;

        this.active_crypto_ring = null;
        this.game_recovered = 0;

        Helpers.GUIRun(() -> {

            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setOpaque(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label()
                    .setHorizontalAlignment(JLabel.LEADING);
            GameFrame.getInstance().getTapete().getCommunityCards().restoreBetLabelicon();
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                    GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground());
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setText("---");
            GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(false);

            Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

            if (!GameFrame.getInstance().isPartida_local()) {
                GameFrame.getInstance().getExit_menu().setEnabled(false);
            }
        });

        if (!GameFrame.RECOVER) {
            Helpers.cleanHandCrupierTempFiles(this.sqlite_id_game);
        }

        readyForNextHand();

        if (this.ciegas_update != null) {
            synchronized (lock_ciegas) {
                GameFrame.CIEGAS_DOUBLE = (int) ciegas_update[2];
                GameFrame.CIEGAS_DOUBLE_TYPE = (int) ciegas_update[3];
                this.ciega_pequeña = (float) ciegas_update[0];
                this.ciega_grande = (float) ciegas_update[1];
                this.ciegas_update = null;
                sqlUpdateGameDoubleBlinds();
                GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("blinds.la_configuracion_de_las_ciegas"));
                Helpers.threadRun(() -> {
                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance(),
                            Translator.translate("blinds.la_configuracion_de_las_ciegas"),
                            new ImageIcon(Init.class.getResource("/images/ciegas_big.png")));
                });
            }
        }

        rabbit_players.clear();
        this.iwtsth = false;
        this.iwtsthing = false;
        this.iwtsthing_request = false;
        this.sqlite_id_hand = -1;
        this.conta_accion = 0;
        this.tot_acciones_recuperadas = 0;
        this.acciones_locales_recuperadas.clear();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (!jugador.isExit() && jugador.isSpectator() && (Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0
                    || rebuy_now.containsKey(jugador.getNickname()))) {
                jugador.unsetSpectator();
                if (rebuy_now.containsKey(jugador.getNickname())) {
                    jugador.setSpectatorBB(true);
                }
            }
        }

        // nicks_permutados tiene que reflejar a los jugadores recién integrados.
        ArrayList<String> nicksList = new ArrayList<>(Arrays.asList(this.nicks_permutados));
        boolean nicksChanged = false;
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (!jugador.isCalentando() && !nicksList.contains(jugador.getNickname())) {
                nicksList.add(jugador.getNickname());
                nicksChanged = true;
            }
        }
        if (nicksChanged) {
            this.nicks_permutados = nicksList.toArray(new String[0]);
            this.update_game_seats = true;
            LOGGER.log(Level.INFO, "Updated seating permutations to inject warm-up players into the crypto ring.");
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isActivo()) {
                jugador.getHoleCard1().resetearCarta(false);
                jugador.getHoleCard2().resetearCarta(false);
                jugador.resetGUI();
            }
        }

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
            carta.resetearCarta(false);
        }

        setContaManoLocal(this.conta_mano + 1);

        if (GameFrame.MANOS == conta_mano && GameFrame.getInstance().isPartida_local()) {
            Helpers.GUIRun(GameFrame.getInstance().getTapete().getCommunityCards()::hand_label_left_click);
        }

        Bot.BOT_COMMUNITY_CARDS.makeEmpty();
        GameFrame.getInstance().getRegistro().print("\n*************** " + Translator.translate("game.mano_2") + " ("
                + String.valueOf(this.conta_mano) + ") ***************");

        if (!GameFrame.RECOVER) {
            this.setPositions();
        }

        if (GameFrame.isRECOVER() && GameFrame.getInstance().isPartida_local()) {
            resyncRECOVERGLOBALS();
        }

        this.badbeat = false;
        this.jugada_ganadora = 0;
        this.perdedores.clear();
        setStreetLocal(PREFLOP);
        this.flop_revealed = false;
        this.turn_revealed = false;
        this.river_revealed = false;
        this.sra_unlock_tags_served.clear();
        this.cartas_resistencia = false;
        this.destapar_resistencia = false;
        this.ultimo_raise = 0f;
        this.partial_raise_cum = 0f;
        this.conta_raise = 0;
        this.conta_bet = 0;

        synchronized (getLock_contabilidad()) {
            if (Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {
                if (GameFrame.SONIDOS_CHORRA && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                    Audio.playWavResource("misc/indivisible.wav");
                }
                Audio.playWavResource("misc/cash_register.wav");
                GameFrame.getInstance().getRegistro()
                        .print(Translator.translate("game.bote_sobrante") + " -> " + Helpers.float2String(bote_sobrante));
            }

            this.bote_total = Math.max(0f, this.bote_sobrante);
        }
        this.bote = new HandPot(0f);
        this.beneficio_bote_principal = null;

        HashSet<String> rebuys_about_to_apply = new HashSet<>();
        for (Map.Entry<String, Integer> e : rebuy_now.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                rebuys_about_to_apply.add(e.getKey());
            }
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isActivo()) {
                jugador.nuevaMano();
            }
        }

        for (String nick : rebuys_about_to_apply) {
            if (!rebuy_now.containsKey(nick)) {
                rebuy_counts.merge(nick, 1, Integer::sum);
            }
        }

        this.rebuy_now.clear();

        Helpers.GUIRun(() -> {
            if (GameFrame.getInstance().getRebuy_now_menu().isEnabled()) {
                GameFrame.getInstance().getRebuy_now_menu().setSelected(false);
                GameFrame.getInstance().getRebuy_now_menu().setBackground(null);
                GameFrame.getInstance().getRebuy_now_menu().setOpaque(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
            }
        });

        saltar_primera_mano = false;

        if (GameFrame.isRECOVER()) {
            GameFrame.getInstance().getRegistro().print(Translator.translate("game.recuperando_timba"));
            try {
                recuperarDatosClavePartida();
            } finally {
                // Garantizar reset incluso si recuperarDatosClavePartida lanza
                // o sale temprano por algún return interno. De lo contrario
                // GameFrame.RECOVER queda stale y la siguiente partida fresh
                // entra de nuevo en modo recovery — el host sin datos válidos
                // por SQL y el cliente esperando RECOVERDATA del host (que no
                // los va a enviar porque para él NO es recovery). Cuelgue
                // garantizado. setRECOVER es idempotente.
                if (GameFrame.RECOVER) {
                    GameFrame.setRECOVER(false);
                }
                // Tras recovery con saltar=true (mano fresh, sin replay), el
                // setPositions de NUEVA_MANO mas arriba se skipeo (estaba bajo
                // !GameFrame.RECOVER y RECOVER aun era true en ese punto).
                // Sin esta llamada explicita aqui, dealer/sb/bb quedan null
                // y repartir() peta. En HOST calcularPosiciones asigna nicks
                // desde nicks_permutados y broadcast POSITIONS; en CLIENTE
                // recibirPosiciones lee POSITIONS de la queue del Crupier.
                if (saltar_primera_mano) {
                    // Limpiar refs cripto que recuperarDatosClavePartida pudo
                    // haber repoblado del fosil viejo. Sin esto, la cascade
                    // NUEVA del host disparaba DECK_CASCADE_REQ en cliente y
                    // el handler veia hasMegaPacket()=true (de la mano vieja)
                    // -> lockdown falso positivo "MEGAPACKET already locked".
                    this.local_mega_packet = null;
                    this.active_crypto_ring = null;
                    this.local_sra_unlock = null;
                    this.local_sra_lock = null;
                    this.local_original_cards = new byte[2];
                    this.setPositions();
                }
                // Rescate de spectator: tras saltar=true y balance vacio del
                // recovery, los players quedan marcados spectator desde el
                // INIT (warming-up). Sin este unsetSpectator, isActivo()=false
                // -> getJugadoresActivos()=0 -> NUEVA_MANO no arranca, timba
                // muere. Solo rescatamos players con stack > 0.
                if (saltar_primera_mano) {
                    try {
                        for (Player j : GameFrame.getInstance().getJugadores()) {
                            if (j != null && !j.isExit() && j.isSpectator()
                                    && Helpers.float1DSecureCompare(0f, j.getStack()) < 0) {
                                j.unsetSpectator();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        this.apuesta_actual = this.ciega_grande;

        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p.isActivo()) {
                Bot.TRACKER_MEMORY.computeIfAbsent(p.getNickname(), k -> new Bot.OpponentTracker()).recordHandPlayed();
            }
        }

        if (getJugadoresActivos() > 1) {
            if (saltar_primera_mano) {
                // Recovery decided not to replay the previous hand (clean exit
                // with hand_end!=0, fossil corrupt, or a preflop player missing).
                // Force a brand-new hand record so actions don't get written
                // into the previous (already-closed) hand.
                this.sqlite_id_hand = -1;
            }
            if (this.sqlite_id_hand == -1) {
                sqlNewHand();
            }

            this.apuestas = 0f;

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (Helpers.float1DSecureCompare(0f, jugador.getBet()) < 0) {
                    this.apuestas += jugador.getBet();
                }
            }

            this.bote_total += this.apuestas;

            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(true);
                GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(true);
            });

            actualizarContadoresTapete();

            Object shuffle_lock = new Object();
            // barajando here means "SRA cascade still running, keep looping the shuffle
            // animation". The thread polls it after each complete GIF cycle (and after
            // each audio-only cycle in the fallback path).
            barajando = true;

            final boolean[] gif_thread_done = {false};

            Helpers.threadRun(() -> {
                String baraja = GameFrame.BARAJA;
                boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[1];
                URL url_icon = null;
                if (baraja_mod && Files.exists(
                        Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/shuffle.gif"))) {
                    try {
                        url_icon = Paths
                                .get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/shuffle.gif")
                                .toUri().toURL();
                    } catch (MalformedURLException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                } else if (getClass().getResource("/images/decks/" + baraja + "/gif/shuffle.gif") != null) {
                    url_icon = getClass().getResource("/images/decks/" + baraja + "/gif/shuffle.gif");

                }
                if (url_icon != null && GameFrame.ANIMACION_CARTAS) {
                    ImageIcon icon = new ImageIcon(url_icon);
                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().getCommunityCards().setVisible(false);
                    });
                    // Loop the shuffle GIF (with audio re-triggered each cycle) until the
                    // SRA cascade finishes. Minimum 1 full cycle thanks to do-while.
                    // delay_end=0 keeps the gap between cycles to the bare EDT round-trip.
                    do {
                        GameFrame.getInstance().getTapete().showCentralImage(icon, 0, 0, true,
                                "misc/shuffle.wav", 1, 53);
                    } while (barajando && !isFin_de_la_transmision());
                    if (!isFin_de_la_transmision()) {
                        Helpers.GUIRunAndWait(() -> {
                            GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                        });
                    }
                } else if (!isFin_de_la_transmision()) {
                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                    });
                    // Audio-only fallback when there is no shuffle.gif for this deck (or
                    // when animations are disabled). playWavResourceAndWait blocks for the
                    // natural duration of the clip, so the do-while replays it back-to-back
                    // with no silence in between. Minimum 1 play guaranteed.
                    do {
                        Audio.playWavResourceAndWait("misc/shuffle.wav");
                    } while (barajando && !isFin_de_la_transmision());
                }
                synchronized (shuffle_lock) {
                    gif_thread_done[0] = true;
                    shuffle_lock.notifyAll();
                }
            });

            if (GameFrame.getInstance().isPartida_local() && this.game_recovered == 0) {

                // Si la cascada falla (alguien no responde), abortamos la inicialización
                if (!enviarCartasJugadoresRemotos()) {
                    barajando = false;
                    synchronized (shuffle_lock) {
                        while (!gif_thread_done[0]) {
                            try {
                                shuffle_lock.wait(1000);
                            } catch (InterruptedException ex) {
                                Helpers.logCooperativeCancellation(LOGGER, "shuffle wait (abort path)", ex);
                                break;
                            }
                        }
                    }
                    return false;
                }

                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (j != GameFrame.getInstance().getLocalPlayer()) {
                        j.ordenarCartas();
                    }
                }
            } else if (!GameFrame.getInstance().isPartida_local()
                    && !GameFrame.getInstance().getLocalPlayer().isCalentando() && this.game_recovered == 0) {
                cartas_locales_recibidas = recibirMisCartas();
            }

            // Cascade done: signal the loop to exit at the next cycle boundary.
            barajando = false;

            synchronized (shuffle_lock) {
                while (!gif_thread_done[0]) {
                    try {
                        shuffle_lock.wait(1000);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "shuffle wait", ex);
                        break;
                    }
                }
            }

            repartir();
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getExit_menu().setEnabled(true);
            });
            disableAllPlayersTimeout();
            return true;

        } else {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador.isActivo() && Helpers.float1DSecureCompare(0f, jugador.getBet()) < 0) {
                    jugador.pagar(jugador.getBet(), null);
                }
            }
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getExit_menu().setEnabled(true);
            });
            disableAllPlayersTimeout();
            return false;
        }
    }

    private void sqlNewHand() {
        synchronized (GameFrame.SQL_LOCK) {

            ArrayList<String> jugadores = new ArrayList<>();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador.isActivo()) {
                    try {
                        jugadores.add(Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8")));
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }

            String sql = "INSERT INTO hand(id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_game);
                statement.setInt(2, this.conta_mano);
                statement.setFloat(3, Helpers.floatClean(this.ciega_pequeña));
                statement.setInt(4, this.ciegas_double);
                statement.setString(5, this.dealer_nick);
                statement.setString(6, this.small_blind_nick);
                statement.setString(7, this.big_blind_nick);
                statement.setLong(8, System.currentTimeMillis());
                statement.setString(9, String.join("#", jugadores.toArray(new String[0])));
                statement.executeUpdate();

                sqlite_id_hand = statement.getGeneratedKeys().getInt(1);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            // Auditor invariant must hold across "exited cleanly + come back
            // via recovery". A human player who sends EXIT mid-game
            // stays in jugadores with isExit()=true and their stack+buyin
            // preserved in memory. Before this fix the condition below skipped
            // them (isActivo() is false for exited players and their stack is
            // typically >0), so no balance row got written for them in any
            // subsequent hand. The recovery loader (sqlRecoverServerLocalGameKeyData
            // around line 6575) only reads balances from MAX(hand.id), so the
            // exited player ended up with no row to restore from and came back
            // with default stack/buyin — instant auditor mismatch.
            //
            // Including isExit() here writes one balance row per (exited
            // player, hand) with their unchanged stack and buyin. The row is
            // idempotent for sqlUpdateHandEnd (their pagar is 0 because they
            // aren't betting) and is exactly what recovery needs to find their
            // state on the LATEST hand of the game. Warming-up players
            // (spectator && stack>0 && !exit) still get no row — they should
            // not contribute to balance until they enter a hand for real.
            if (this.conta_mano == 1) {
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador.isActivo() || Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0 || jugador.isExit()) {
                        this.sqlNewHandBalance(jugador.getNickname(), jugador.getStack() + jugador.getBet(), jugador.getBuyin());
                    }
                }
            } else {
                for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {
                    Player jugador = nick2player.get(entry.getKey());
                    if (jugador != null) {
                        if (jugador.isActivo() || Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0 || jugador.isExit()) {
                            this.sqlNewHandBalance(jugador.getNickname(), jugador.getStack() + jugador.getBet(), jugador.getBuyin());
                        }
                    } else {
                        Float[] pasta = entry.getValue();
                        this.sqlNewHandBalance(entry.getKey(), pasta[0], Math.round(pasta[1]));
                    }
                }
            }
        }
    }

    private void sqlNewAction(Player current_player) {
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "INSERT INTO action(id_hand, player, counter, round, action, bet, conta_raise, response_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (java.sql.PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                statement.setString(2, current_player.getNickname());
                statement.setInt(3, this.conta_accion);
                statement.setInt(4, this.street);
                statement.setInt(5, current_player.getDecision());
                statement.setFloat(6, Helpers.floatClean(current_player.getBet()));
                statement.setInt(7, this.getConta_raise());
                statement.setInt(8, current_player.getResponseTime());
                statement.executeUpdate();
            } catch (java.sql.SQLException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
        }
    }

    private boolean sqlCheckGenuineRecoverAction(Player current_player) {
        synchronized (GameFrame.SQL_LOCK) {
            boolean ret = false;

            try {

                String sql = "SELECT player FROM action WHERE id_hand=? and player=? and counter=?";

                PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, current_player.getNickname());

                statement.setInt(3, this.conta_accion);

                ResultSet rs = statement.executeQuery();

                if (rs.next()) {
                    // Existe la acción de ese jugador en esa mano, ahora vamos a ver si coincide lo
                    // que tenemos guardado con lo que ha enviado el servidor/jugador

                    sql = "SELECT player FROM action WHERE id_hand=? and player=? and counter=? and action=?"
                            + (current_player.getDecision() >= Player.BET ? " and bet=?" : "");

                    statement = Helpers.getSQLITE().prepareStatement(sql);

                    statement.setQueryTimeout(30);

                    statement.setInt(1, this.sqlite_id_hand);

                    statement.setString(2, current_player.getNickname());

                    statement.setInt(3, this.conta_accion);

                    statement.setInt(4, current_player.getDecision());

                    if (current_player.getDecision() >= Player.BET) {
                        statement.setFloat(5, Helpers.floatClean(current_player.getBet()));
                    }

                    rs = statement.executeQuery();

                    ret = rs.next();

                } else {
                    // No existe esa acción para ese jugador, por lo que no podemos comparar y por
                    // tanto nos fiamos de lo que envía el servidor/jugador
                    ret = true;
                }

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;
        }
    }

    private void sqlNewShowcards(String jugador, boolean parguela) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO showcards(id_hand, player, parguela) VALUES(?,?,?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, jugador);

                statement.setBoolean(3, parguela);

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private void sqlUpdateShowdownHand(Player jugador, Hand jugada) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE showdown SET hole_cards=?, hand_cards=?, hand_val=? WHERE id_hand=? AND player=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setString(1, jugador.getHoleCard1().isTapada() ? null
                        : jugador.getHoleCard1().toShortString() + "#" + jugador.getHoleCard2().toShortString());

                statement.setString(2, (jugador.getHoleCard1().isTapada() || jugada == null) ? null
                        : Card.collection2ShortString(jugada.getMano()));

                statement.setInt(3, (jugador.getHoleCard1().isTapada() || jugada == null) ? -1 : jugada.getValue());

                statement.setInt(4, this.sqlite_id_hand);

                statement.setString(5, jugador.getNickname());

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateShowdownPay(Player jugador) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE showdown SET pay=?, profit=? WHERE id_hand=? AND player=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setFloat(1, Helpers.floatClean(jugador.getPagar()));

                statement.setFloat(2, Helpers.floatClean(jugador.getPagar() - jugador.getBote()));

                statement.setInt(3, this.sqlite_id_hand);

                statement.setString(4, jugador.getNickname());

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private int sqlGetPlayerContaWins(String nick, int game_id) {
        synchronized (GameFrame.SQL_LOCK) {

            int tot = 0;

            String sql = "SELECT COUNT(*) as total FROM showdown,hand WHERE showdown.player=? AND showdown.winner=? AND showdown.id_hand=hand.id AND hand.id_game=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setString(1, nick);

                statement.setBoolean(2, true);

                statement.setInt(3, game_id);

                ResultSet rs = statement.executeQuery();

                if (rs.next()) {
                    tot = rs.getInt("total");
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return tot;
        }
    }

    private void sqlNewShowdown(Player jugador, Hand jugada, boolean win, boolean tapadas) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO showdown(id_hand, player, hole_cards, hand_cards, hand_val, winner, pay, profit) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, jugador != null ? jugador.getNickname() : "-----");

                statement.setString(3, (jugador == null || tapadas) ? null
                        : jugador.getHoleCard1().toShortString() + "#" + jugador.getHoleCard2().toShortString());

                statement.setString(4, (jugador == null || tapadas || jugada == null) ? null
                        : Card.collection2ShortString(jugada.getMano()));

                statement.setInt(5, (jugador == null || tapadas || jugada == null) ? -1 : jugada.getValue());

                statement.setBoolean(6, win);

                statement.setFloat(7, Helpers.floatClean(jugador != null ? jugador.getPagar() : 0f));

                statement.setFloat(8,
                        Helpers.floatClean(jugador != null ? jugador.getPagar() - jugador.getBote() : 0f));

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandEnd(float bote_tot) {
        synchronized (GameFrame.SQL_LOCK) {

            PreparedStatement statement;
            try {
                statement = Helpers.getSQLITE().prepareStatement("UPDATE hand SET end=?, pot=? WHERE id=?");
                statement.setQueryTimeout(30);
                statement.setLong(1, System.currentTimeMillis());
                statement.setFloat(2, Helpers.floatClean(bote_tot));
                statement.setInt(3, this.sqlite_id_hand);
                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            String[] balance_float = new String[auditor.size()];

            int i = 0;

            for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {

                Player jugador = nick2player.get(entry.getKey());

                try {
                    if (jugador != null) {

                        sqlUpdateHandBalance(jugador.getNickname(), jugador.getStack()
                                + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : 0f),
                                jugador.getBuyin());
                        balance_float[i] = Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                + "|"
                                + String.valueOf(jugador.getStack()
                                        + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar()
                                        : 0f))
                                + "|" + String.valueOf(jugador.getBuyin())
                                + "|" + String.valueOf(getRebuyCount(jugador.getNickname()));
                    } else {

                        Float[] pasta = entry.getValue();
                        sqlUpdateHandBalance(entry.getKey(), pasta[0], Math.round(pasta[1]));
                        balance_float[i] = Base64.getEncoder().encodeToString(entry.getKey().getBytes("UTF-8")) + "|"
                                + String.valueOf(pasta[0]) + "|" + String.valueOf(Math.round(pasta[1]))
                                + "|" + String.valueOf(getRebuyCount(entry.getKey()));
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

                i++;
            }

            LOGGER.log(Level.INFO, () -> "BALANCE AFTER HAND(" + String.valueOf(conta_mano) + ") -> " + String.join("@", balance_float));

            String balanceFileName = Init.DEV_MODE ? "/balance_backup_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt" : "/balance_backup.txt";

            try {
                Files.writeString(Paths.get(Init.CORONA_DIR + balanceFileName), String.join("@", balance_float));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            try {
                statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET play_time=? WHERE id=?");
                statement.setQueryTimeout(30);
                statement.setLong(1, GameFrame.getInstance().getConta_tiempo_juego());
                statement.setInt(2, this.sqlite_id_game);
                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandPlayers(ArrayList<Player> resistencia) {

        synchronized (GameFrame.SQL_LOCK) {

            ArrayList<String> jugadores = new ArrayList<>();

            for (Player jugador : resistencia) {

                try {
                    jugadores.add(Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8")));
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            }

            String cards = null;

            String sql = "";

            switch (this.street) {

                case PREFLOP:
                    sql = "UPDATE hand SET preflop_players=?, com_cards=? WHERE id=?";
                    break;

                case FLOP:
                    sql = "UPDATE hand SET flop_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 3));
                    break;

                case TURN:
                    sql = "UPDATE hand SET turn_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 4));
                    break;

                case RIVER:
                    sql = "UPDATE hand SET river_players=?, com_cards=? WHERE id=?";
                    cards = Card.collection2ShortString(
                            new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())));
                    break;
            }

            PreparedStatement statement;
            try {
                statement = Helpers.getSQLITE().prepareStatement(sql);
                statement.setQueryTimeout(30);
                statement.setString(1, String.join("#", jugadores.toArray(new String[0])));
                statement.setString(2, cards);
                statement.setInt(3, this.sqlite_id_hand);
                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    public int getSqlite_game_id() {
        return sqlite_id_game;
    }

    public int getSqlite_hand_id() {
        return sqlite_id_hand;
    }

    private void sqlNewHandBalance(String nick, float stack, int buyin) {

        synchronized (GameFrame.SQL_LOCK) {

            PreparedStatement statement;
            try {
                statement = Helpers.getSQLITE()
                        .prepareStatement("INSERT INTO balance(id_hand, player, stack, buyin, rebuy_count) VALUES (?,?,?,?,?)");
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                statement.setString(2, nick);
                statement.setFloat(3, Helpers.floatClean(stack));
                statement.setInt(4, buyin);
                statement.setInt(5, getRebuyCount(nick));
                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private void sqlUpdateHandBalance(String nick, float stack, int buyin) {
        synchronized (GameFrame.SQL_LOCK) {
            PreparedStatement statement;
            try {
                statement = Helpers.getSQLITE()
                        .prepareStatement("UPDATE balance SET stack=?, buyin=?, rebuy_count=? WHERE id_hand=? and player=?");
                statement.setQueryTimeout(30);
                statement.setFloat(1, Helpers.floatClean(stack));
                statement.setInt(2, buyin);
                statement.setInt(3, getRebuyCount(nick));
                statement.setInt(4, this.sqlite_id_hand);
                statement.setString(5, nick);
                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    private void sqlNewGame() {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO game(start, players, buyin, sb, blinds_time, rebuy, server, blinds_time_type, ugi, local) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                GameFrame.GAME_START_TIMESTAMP = System.currentTimeMillis();

                statement.setLong(1, GameFrame.GAME_START_TIMESTAMP);

                ArrayList<String> players = new ArrayList<>();

                for (String nick : nicks_permutados) {

                    if (nick2player.get(nick).isActivo()) {
                        players.add(Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")));
                    }
                }

                statement.setString(2, String.join("#", players.toArray(new String[0])));

                statement.setInt(3, GameFrame.BUYIN);

                statement.setFloat(4, Helpers.floatClean(GameFrame.CIEGA_PEQUEÑA));

                statement.setInt(5, GameFrame.CIEGAS_DOUBLE);

                statement.setBoolean(6, GameFrame.REBUY);

                statement.setString(7, GameFrame.getInstance().getSala_espera().getServer_nick());

                statement.setInt(8, GameFrame.CIEGAS_DOUBLE_TYPE);

                statement.setString(9, GameFrame.UGI);

                statement.setInt(10, GameFrame.getInstance().isPartida_local() ? 1 : 0);

                statement.executeUpdate();

                sqlite_id_game = statement.getGeneratedKeys().getInt(1);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

        GameFrame.persistRecoverSettings(sqlite_id_game);
    }

    private void repartir() {

        boolean animacion = GameFrame.ANIMACION_CARTAS;

        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / this.getJugadoresActivos())));

        if (!animacion) {

            for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                carta.iniciarCarta();
            }

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador.isActivo()) {

                    jugador.getHoleCard1().iniciarCarta();
                    jugador.getHoleCard2().iniciarCarta();
                }
            }
        }

        int i = 0;

        // Guard: si dealer_nick es null o no esta en la lista de jugadores,
        // el while desborda con IndexOutOfBoundsException matando el thread
        // del Crupier silenciosamente. Abortamos la mano controladamente.
        if (this.dealer_nick == null) {
            LOGGER.log(Level.SEVERE, "repartir() dealer_nick is null — aborting hand");
            cancelarManoYDevolverApuestas("peer.state_inconsistent", true);
            return;
        }
        int jugSize = GameFrame.getInstance().getJugadores().size();
        boolean found = false;
        while (i < jugSize) {
            if (this.dealer_nick.equals(GameFrame.getInstance().getJugadores().get(i).getNickname())) {
                found = true;
                break;
            }
            i++;
        }
        if (!found) {
            LOGGER.log(Level.SEVERE, "repartir() dealer_nick={0} not found in jugadores — aborting hand", this.dealer_nick);
            cancelarManoYDevolverApuestas("peer.state_inconsistent", true);
            return;
        }

        int j, pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();

        j = pivote;

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && animacion) {

                Audio.playWavResource("misc/deal.wav", false);

                if (jugador == GameFrame.getInstance().getLocalPlayer()) {

                    // Las cartas ya se extrajeron de la bóveda C, las seteamos aquí.
                    jugador.getHoleCard1().iniciarConValorNumerico((this.local_original_cards[0] & 0xFF) + 1);
                    jugador.getHoleCard1().destapar(false);

                } else {

                    jugador.getHoleCard1().iniciarCarta();

                }
            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                jugador.getHoleCard1().iniciarConValorNumerico((this.local_original_cards[0] & 0xFF) + 1);
                jugador.getHoleCard1().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && animacion) {

                Audio.playWavResource("misc/deal.wav", false);

                if (jugador == GameFrame.getInstance().getLocalPlayer()) {

                    jugador.getHoleCard2().iniciarConValorNumerico((this.local_original_cards[1] & 0xFF) + 1);
                    jugador.getHoleCard2().destapar(false);

                } else {

                    jugador.getHoleCard2().iniciarCarta();
                }
            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                jugador.getHoleCard2().iniciarConValorNumerico((this.local_original_cards[1] & 0xFF) + 1);
                jugador.getHoleCard2().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {

            GameFrame.getInstance().checkPause();

            if (animacion) {
                Audio.playWavResource("misc/deal.wav", false);
                carta.iniciarCarta();
            }

            Helpers.pausar(pausa);
        }

        GameFrame.getInstance().getLocalPlayer().ordenarCartas();
    }

    private void recibirConsensoFinal(ArrayList<Player> inShowdown) {
        boolean consensus_ok = false;
        long start_time = System.currentTimeMillis();

        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!consensus_ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#", -1);

                    if (partes.length >= 3) {
                        switch (partes[2]) {
                            case "HANDVERIFY":
                                consensus_ok = true;
                                break;
                            case "MISDEAL":
                                String motivo = "";
                                try {
                                    motivo = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                                } catch (Exception e) {
                                }
                                cancelarManoYDevolverApuestas(motivo, false);
                                return;
                            default:
                                rejected.add(comando);
                                break;
                        }
                    } else {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (!consensus_ok) {
                GameFrame.getInstance().checkPause();
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while (!consensus_ok && !isFin_de_la_transmision());
    }

    private void requestShowdownKeys(ArrayList<Player> inShowdown) {
        if (!GameFrame.getInstance().isPartida_local()) {
            Helpers.threadRun(() -> {
                recibirConsensoFinal(inShowdown);
            });
            return;
        }
        // Synchronization signal: all clients ACK before the host closes the showdown.
        broadcastGAMECommandFromServer("HANDVERIFY", null, true);
    }

    private HashMap<String, Object> recibirDatosClaveRecuperados() {

        // Guard de salida (ver nota en recibirPosiciones). Retorno null
        // si la transmisión muere antes de recibir RECOVERDATA.
        HashMap<String, Object> map = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 4 && partes[2].equals("RECOVERDATA")) {

                            ObjectInputStream in = null;
                            try {
                                ok = true;
                                ByteArrayInputStream byteIn = new ByteArrayInputStream(
                                        Base64.getDecoder().decode(partes[3]));
                                in = new ObjectInputStream(byteIn);
                                map = (HashMap<String, Object>) in.readObject();

                                Integer hand_id = this.getHandIdFromUGI(GameFrame.UGI);

                                map.put("hand_id", hand_id != null ? hand_id : -1);

                            } catch (IOException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            } catch (ClassNotFoundException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            } finally {
                                if (in != null) {
                                    try {
                                        in.close();
                                    } catch (IOException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                        } else if (partes.length >= 3 && partes[2].equals("RECOVERDATA")) {
                            LOGGER.log(Level.WARNING, "RECOVERDATA malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in receiveRecoveryKeyData: " + comando, ex);
                    }

                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

        return map;
    }

    private String recibirAccionesRecuperadas() {

        // Guard de salida (ver nota en recibirPosiciones). Sin él, si la
        // transmisión muere durante el wait el cliente queda colgado para
        // siempre esperando ACTIONDATA. El retorno null en ese caso es
        // OK: el caller ya está en flujo de terminación.
        String actions = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();
                    try {
                        String[] partes = comando.split("#");

                        if (partes.length >= 4 && partes[2].equals("ACTIONDATA")) {

                            ok = true;

                            try {
                                actions = !"*".equals(partes[3])
                                        ? new String(Base64.getDecoder().decode(partes[3]), "UTF-8")
                                        : "";
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }

                        } else if (partes.length >= 3 && partes[2].equals("ACTIONDATA")) {
                            LOGGER.log(Level.WARNING, "ACTIONDATA malformed dropped: {0}", comando);
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Exception while processing command in ACTIONDATA wait: " + comando, ex);
                    }
                }

                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }

            }

            if (!ok) {

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                            break;
                        }
                    }
                }
            }

        } while (!ok && !isFin_de_la_transmision());

        return actions;
    }

    public void enviarDatosClaveRecuperados(ArrayList<String> pendientes, HashMap<String, Object> datos) {

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        do {
            ObjectOutputStream out = null;
            try {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                out = new ObjectOutputStream(byteOut);
                out.writeObject(datos);
                String command = "GAME#" + String.valueOf(id) + "#RECOVERDATA#"
                        + Base64.getEncoder().encodeToString(byteOut.toByteArray());

                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (pendientes.contains(jugador.getNickname())) {
                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                        if (p != null && !p.isCpu()) {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        }
                    }
                }

                this.waitSyncConfirmations(id, pendientes);

                // Sin timeout artificial: si un cliente tarda en confirmar la
                // recuperación de datos (red lenta, payload grande), esperamos.
                // La única salida es que el cliente se marque exit por su socket
                // muerto, en cuyo caso waitSyncConfirmations sale por su cuenta
                // y la siguiente vuelta del do-while reevalúa pendientes.
                if (!pendientes.isEmpty()) {
                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ex) {
                }
            }

        } while (!pendientes.isEmpty());
    }

    public void enviarAccionesRecuperadas(ArrayList<String> pendientes, String datos) {

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        do {
            try {
                String command = "GAME#" + String.valueOf(id) + "#ACTIONDATA#"
                        + ((datos == null || datos.isEmpty()) ? "*"
                        : Base64.getEncoder().encodeToString(datos.getBytes("UTF-8")));

                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (pendientes.contains(jugador.getNickname())) {
                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                        if (p != null && !p.isCpu()) {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        }
                    }
                }

                this.waitSyncConfirmations(id, pendientes);

                // Sin timeout artificial: ver enviarDatosClaveRecuperados.
                if (!pendientes.isEmpty()) {
                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } while (!pendientes.isEmpty());
    }

    private float[] calcularBoteParaGanador(float cantidad, int tot_ganadores) {

        if (tot_ganadores > 1) {

            float bote_div = cantidad / tot_ganadores;

            float bote_div_limpio = Math.round(bote_div * 100f) / 100f;

            float bote_individual = (float) Math.floor(bote_div_limpio * 10f) / 10f;

            float sobrante = Math.round((cantidad - tot_ganadores * bote_individual) * 10f) / 10f;

            return new float[]{bote_individual, sobrante};
        } else {
            return new float[]{cantidad, 0f};
        }

    }

    public void sendGAMECommandToServer(String command, boolean confirmation) {

        // Si el menú intenta mandar un EXIT desnudo por otra vía, le pegamos las llaves
        if (command != null && command.equals("EXIT")) {
            command = "EXIT#" + getTestamentoCriptografico();
        }

        ArrayList<String> pendientes = new ArrayList<>();

        pendientes.add(GameFrame.getInstance().getSala_espera().getServer_nick());

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

        do {

            GameFrame.getInstance().getSala_espera()
                    .writeCommandToServer(Helpers.encryptCommand(full_command,
                            GameFrame.getInstance().getSala_espera().getLocal_client_aes_key(),
                            GameFrame.getInstance().getSala_espera().getLocal_client_hmac_key()));

            if (confirmation) {
                this.waitSyncConfirmations(id, pendientes);
            }

            if (confirmation) {
                if (!pendientes.isEmpty()) {
                    GameFrame.getInstance().getLocalPlayer().setTimeout(true);
                } else {
                    GameFrame.getInstance().getLocalPlayer().setTimeout(false);
                }
            }

        } while (!pendientes.isEmpty() && confirmation);
    }

    public void sendGAMECommandToServer(String command) {

        this.sendGAMECommandToServer(command, true);

    }

    private boolean waitSyncConfirmations(int id, ArrayList<String> pending) {

        // Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!pending.isEmpty() && !timeout) {

            Object[] confirmation;

            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                while (!WaitingRoomFrame.getInstance().getReceived_confirmations().isEmpty()) {

                    confirmation = WaitingRoomFrame.getInstance().getReceived_confirmations().poll();

                    if (confirmation != null && confirmation[0] != null && confirmation[1] != null
                            && (int) confirmation[1] == id + 1) {

                        pending.remove((String) confirmation[0]);

                        if (nick2player.containsKey((String) confirmation[0])) {

                            nick2player.get((String) confirmation[0]).setTimeout(false);

                        }

                    } else if (confirmation != null && confirmation[0] != null && confirmation[1] != null) {
                        rejected.add(confirmation);
                    }
                }

                if (!rejected.isEmpty()) {
                    WaitingRoomFrame.getInstance().getReceived_confirmations().addAll(rejected);
                    rejected.clear();
                }

                if (System.currentTimeMillis() - start_time > GameFrame.CONFIRMATION_TIMEOUT) {
                    timeout = true;
                } else if (!pending.isEmpty()) {

                    try {
                        WaitingRoomFrame.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);

                        for (Player jugador : GameFrame.getInstance().getJugadores()) {

                            if (jugador.isExit() && pending.contains(jugador.getNickname())) {

                                pending.remove(jugador.getNickname());
                            }
                        }

                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "received confirmations wait", ex);
                        break;
                    }

                }
            }

        }

        return !pending.isEmpty();
    }

    public Object[] readActionFromRemotePlayer(Player jugador) {
        boolean ok = false;
        // EC-Identity v1 (commit 5): action[] grows to 6 slots so the wire's
        // record + sig and the voluntary flag survive the trip through Crupier:
        //   [0] decision        (Integer)
        //   [1] bet             (Float; on ALLIN overloaded to cinematic String —
        //                        kept for backward-compat with rondaApuestas)
        //   [2] cinematic       (String|null — separate slot; [1] is overloaded)
        //   [3] record          (byte[92]|null — canonical record from the wire)
        //   [4] sig             (byte[64]|null — Ed25519 signature from the wire)
        //   [5] isVoluntary     (Boolean — false only for host-issued auto-fold §4.5)
        Object[] action = new Object[6];

        // Sin timeout artificial en el host: cada cliente tiene su propio
        // contador de tiempo de pensar (LocalPlayer.response_counter) que al
        // llegar a cero hace auto-click en CHECK o FOLD y envía la ACTION.
        // Si el reloj del cliente está desfasado respecto al del host (clientes
        // lentos, GC stalls, latencia), forzar FOLD desde el host expulsaría
        // la decisión real del jugador. Confiamos en la ACTION del cliente y
        // sólo salimos del bucle si su socket muere de verdad (isExit).
        do {
            ok = false;

            if (!jugador.isExit()) {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!ok && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");

                        if (!jugador.isExit()) {
                            try {
                                /* EC-Identity v1 wire (commit 5):
                                 *   GAME#ID#ACTION#NICK_B64#DECISION#BET#CINEMATIC_OR_*#RECORD_B64#SIG_B64
                                 * Legacy wire (pre-commit-5) is shorter; record/sig stay null and the
                                 * receiver verification is skipped (the chain absorbs only if both are
                                 * present, so the legacy path remains correct).
                                 */
                                if (partes.length >= 6 && partes[2].equals("ACTION")) {
                                    String senderNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");

                                    if (senderNick.equals(jugador.getNickname())) {
                                        ok = true;
                                        action[0] = Integer.valueOf(partes[4]);
                                        action[1] = Float.valueOf(partes[5]);
                                        action[2] = null;

                                        /* Cinematic extraction on ALLIN */
                                        if ((Integer) action[0] == Player.ALLIN && partes.length > 6 && !partes[6].isEmpty() && !partes[6].equals("*")) {
                                            action[1] = partes[6];
                                            action[2] = partes[6];
                                        }

                                        // EC-Identity v1: record + sig from the wire (commit 5).
                                        // partes[7] / partes[8] arrive as base64; absent (or "*") means
                                        // the sender is on a pre-v1 build and verification is skipped.
                                        action[3] = null;
                                        action[4] = null;
                                        action[5] = Boolean.TRUE; // genuine player decision
                                        if (partes.length >= 9
                                                && !"*".equals(partes[7]) && !"*".equals(partes[8])) {
                                            try {
                                                byte[] wireRecord = java.util.Base64.getDecoder().decode(partes[7]);
                                                byte[] wireSig = java.util.Base64.getDecoder().decode(partes[8]);
                                                action[3] = wireRecord;
                                                action[4] = wireSig;

                                                // EC-Identity v1: decode the FLAGS.is_voluntary bit from the
                                                // record so action[5] reflects what the sender claimed (§4.5
                                                // host auto-folds use voluntary=0). The §10 receiver rule
                                                // picks the signer pubkey from this bit + Participant.isCpu().
                                                if (wireRecord != null
                                                        && wireRecord.length == CanonicalActionRecord.RECORD_BYTES) {
                                                    int flags = ((wireRecord[CanonicalActionRecord.OFFSET_FLAGS] & 0xff) << 8)
                                                            | (wireRecord[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xff);
                                                    boolean wireVoluntary = ((flags >> CanonicalActionRecord.FLAG_BIT_VOLUNTARY) & 1) != 0;
                                                    action[5] = wireVoluntary;

                                                    byte[] signerPubkey = resolveActionSignerPubkey(jugador.getNickname(), wireVoluntary);
                                                    if (signerPubkey == null) {
                                                        LOGGER.log(Level.WARNING,
                                                                "Cannot resolve signer pubkey for action by {0} (voluntary={1}) — verification skipped",
                                                                new Object[]{jugador.getNickname(), wireVoluntary});
                                                    } else if (!IdentityManager.verifyAction(signerPubkey, wireRecord, wireSig)) {
                                                        LOGGER.log(Level.SEVERE,
                                                                "ZERO-TRUST: invalid Ed25519 signature on action by {0} (voluntary={1}) — absorbed anyway, divergence will surface at hand close",
                                                                new Object[]{jugador.getNickname(), wireVoluntary});
                                                    }
                                                }
                                            } catch (Exception parseEx) {
                                                LOGGER.log(Level.WARNING, "Failed to decode/verify record/sig from ACTION wire", parseEx);
                                                action[3] = null;
                                                action[4] = null;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.log(Level.SEVERE, "Error parsing remote action", ex);
                            }
                        }
                        if (!ok) {
                            rejected.add(comando);
                        }
                    }
                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                    }
                }

                if (!ok) {
                    GameFrame.getInstance().checkPause();
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!ok && !jugador.isExit());

        if (jugador.isExit()) {
            // EC-Identity v1: host-issued auto-fold per §4.5. The host fabricates the
            // canonical record (voluntary=0, PLAYER_ID names the timed-out player so
            // the chain reflects who folded) and signs with its own privkey. The
            // wire will carry these bytes verbatim through the broadcast below so
            // every receiver verifies against the host's pubkey.
            action[0] = Player.FOLD;
            action[1] = 0f;
            action[2] = null;
            Object[] recsig = buildLocalActionRecordAndSig(
                    jugador.getNickname(), Player.FOLD, 0f, jugador, false);
            if (recsig != null) {
                action[3] = recsig[0];
                action[4] = recsig[1];
            }
            action[5] = Boolean.FALSE; // host-issued auto-fold (§4.5)
        } else {
            jugador.setTimeout(false);
        }
        return action;
    }

    public int puedenApostar(ArrayList<Player> jugadores) {

        int tot = 0;

        for (Player jugador : jugadores) {

            if (jugador.isActivo() && jugador.getDecision() != Player.ALLIN && jugador.getDecision() != Player.FOLD) {
                tot++;
            }
        }

        return tot;
    }

    private void destaparCartaComunitaria(int street, ArrayList<Player> resisten) {

        GameFrame.getInstance().checkPause();

        switch (street) {
            case FLOP:
                destaparFlop(resisten);
                break;
            case TURN:
                destaparTurn(resisten);
                break;
            case RIVER:
                destaparRiver(resisten);
                break;
            default:
                break;
        }
    }

    public int getConta_raise() {
        return conta_raise;
    }

    public Player getLast_aggressor() {
        return last_aggressor;
    }

    private boolean enviarCartasComunitarias(java.util.ArrayList<Player> resisten) {
        java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, "Initiating EC-SRA street unlock: {0}", street);

        if (this.local_sra_unlock == null || this.active_crypto_ring == null) {
            cancelarManoYDevolverApuestas("peer.state_inconsistent");
            return false;
        }

        int numPlayers = this.active_crypto_ring.length;
        int numCards = (street == Crupier.FLOP) ? 3 : 1;

        int offset = numPlayers * 2;
        if (street == Crupier.FLOP) {
            offset += 1;
        } else if (street == Crupier.TURN) {
            offset += 1 + 3 + 1;
        } else if (street == Crupier.RIVER) {
            offset += 1 + 3 + 1 + 1 + 1;
        }

        int unlockPhase = phaseForStreet(street, false);
        String pieceCommand = (street == Crupier.FLOP) ? "FLOP_PIECE"
                : (street == Crupier.TURN) ? "TURN_PIECE" : "RIVER_PIECE";

        int[] hostIndices = cascadeAndDealCommunityPieces(offset, numCards, unlockPhase, pieceCommand, true);
        if (hostIndices == null) {
            return false;
        }

        if (street == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(hostIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(hostIndices[2] + 1);
        } else if (street == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(hostIndices[0] + 1);
        } else if (street == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(hostIndices[0] + 1);
        }
        return true;
    }

    // POCKET-like cascade comunitario (v3): construimos una copia per-recipient
    // (host + cada humano remoto), cada una con todos los locks salvo el del
    // destinatario, y la broadcastamos como *_PIECE. El destinatario aplica
    // su propio unlock localmente y verifica via resolveCardIndex (-1 ⇒
    // lockdown). El host es destinatario también, pero su copia NO le aplica
    // localmente su propio unlock antes de la cascade: así garantizamos que
    // ningún helper vea bytes en genesis durante el unlock por red (siempre
    // queda al menos el host_lock encima). Tras la cascade, el host aplica
    // su unlock local sobre SU copia para resolver los chunks y devolverlos
    // al caller, que actualiza su UI.
    //
    // Si abortOnFail==true, los fallos disparan cancelarManoYDevolverApuestas
    // (path vivo). Si false, simplemente devolvemos null (path rabbit: una
    // pieza que no llega no aborta la mano, sólo deja la carta tapada).
    private int[] cascadeAndDealCommunityPieces(int offset, int numCards, int unlockPhase, String pieceCommand, boolean abortOnFail) {
        String hostNick = GameFrame.getInstance().getNick_local();
        HashMap<String, Integer> nick2idx = new HashMap<>();
        for (int i = 0; i < this.active_crypto_ring.length; i++) {
            nick2idx.put(this.active_crypto_ring[i], i);
        }

        ArrayList<String> remoteHumans = new ArrayList<>();
        for (String nick : this.active_crypto_ring) {
            if (nick.equals(hostNick)) {
                continue;
            }
            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
            if (p != null && !p.isCpu()) {
                remoteHumans.add(nick);
            }
        }

        HashMap<String, byte[]> copies = new HashMap<>();

        // Copia del host: NO le quitamos su propio lock todavía.
        byte[] copyHost = new byte[numCards * 32];
        System.arraycopy(this.local_mega_packet, offset * 32, copyHost, 0, numCards * 32);
        for (String nick : this.active_crypto_ring) {
            Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
            if (pp != null && pp.isCpu()) {
                if (pp.getReceived_token() == null) {
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("peer.bot_no_token");
                    }
                    return null;
                }
                copyHost = CryptoSRA.applyCommutativeLock(copyHost, pp.getReceived_token());
            }
        }
        copies.put(hostNick, copyHost);

        // Copia de cada humano remoto R: aplicamos local host_unlock + bot_unlocks.
        for (String r : remoteHumans) {
            byte[] copyR = new byte[numCards * 32];
            System.arraycopy(this.local_mega_packet, offset * 32, copyR, 0, numCards * 32);
            copyR = CryptoSRA.applyCommutativeLock(copyR, this.local_sra_unlock);
            for (String nick : this.active_crypto_ring) {
                Participant pp = GameFrame.getInstance().getParticipantes().get(nick);
                if (pp != null && pp.isCpu() && pp.getReceived_token() != null) {
                    copyR = CryptoSRA.applyCommutativeLock(copyR, pp.getReceived_token());
                }
            }
            copies.put(r, copyR);
        }

        // Cascade total: por cada helper humano remoto H, un único batch con
        // los items de TODOS los recipients X != H.
        for (String h : remoteHumans) {
            Participant ph = GameFrame.getInstance().getParticipantes().get(h);
            if (ph == null) {
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.community_unlock_no_testament");
                }
                return null;
            }

            ArrayList<Integer> peerIdxs = new ArrayList<>();
            ArrayList<byte[]> payloads = new ArrayList<>();
            ArrayList<String> recipientsForH = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : copies.entrySet()) {
                if (!e.getKey().equals(h)) {
                    peerIdxs.add(nick2idx.get(e.getKey()));
                    payloads.add(e.getValue());
                    recipientsForH.add(e.getKey());
                }
            }

            if (!ph.isExit()) {
                ArrayList<byte[]> response = requestRemoteUnlockBatch(h, ph, unlockPhase, peerIdxs, payloads);
                if (response != null) {
                    for (int i = 0; i < recipientsForH.size(); i++) {
                        copies.put(recipientsForH.get(i), response.get(i));
                    }
                } else if (ph.getSra_unlock() != null) {
                    for (String r : recipientsForH) {
                        copies.put(r, CryptoSRA.applyCommutativeLock(copies.get(r), ph.getSra_unlock()));
                    }
                } else {
                    if (abortOnFail) {
                        cancelarManoYDevolverApuestas("peer.community_unlock_no_testament");
                    }
                    return null;
                }
            } else if (ph.getSra_unlock() != null) {
                for (String r : recipientsForH) {
                    copies.put(r, CryptoSRA.applyCommutativeLock(copies.get(r), ph.getSra_unlock()));
                }
            } else {
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.community_unlock_no_testament");
                }
                return null;
            }
        }

        // Resolver la copia del host (queda sólo su propio lock).
        byte[] hostFinal = CryptoSRA.applyCommutativeLock(copies.get(hostNick), this.local_sra_unlock);
        int[] hostIndices = new int[numCards];
        for (int i = 0; i < numCards; i++) {
            byte[] chunk = Arrays.copyOfRange(hostFinal, i * 32, (i + 1) * 32);
            int idx = CryptoSRA.resolveCardIndex(chunk);
            if (idx < 0) {
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("zero_trust.card_resolve_failed");
                }
                return null;
            }
            hostIndices[i] = idx;
        }

        // Difundir las piezas a cada humano remoto. broadcastGAMECommandFromServer
        // manda a todos los clientes; sólo el destinatario podrá descifrar su pieza.
        for (String r : remoteHumans) {
            try {
                String nickB64 = Base64.getEncoder().encodeToString(r.getBytes("UTF-8"));
                String payloadB64 = Base64.getEncoder().encodeToString(copies.get(r));
                broadcastGAMECommandFromServer(pieceCommand + "#" + nickB64 + "#" + payloadB64, null);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error broadcasting community piece for " + r, e);
                if (abortOnFail) {
                    cancelarManoYDevolverApuestas("peer.broadcast_failed");
                }
                return null;
            }
        }

        return hostIndices;
    }

    private boolean recibirCartasComunitarias() {
        boolean ok = false;

        // En v3 cada cliente humano recibe SU PROPIA pieza cifrada (FLOP_PIECE
        // /TURN_PIECE/RIVER_PIECE) con los locks de los demás ya quitados; el
        // cliente aplica su propio sra_unlock y resuelve mediante
        // resolveCardIndex. Un -1 significa que el host envió bytes que no son
        // genesis tras mi unlock — basura o smuggling cross-slot — y disparamos
        // lockdown.
        //
        // Sin timeout: la cascade del host puede tardar minutos con un ring grande
        // y clientes lentos. El cliente espera indefinidamente — TCP nos garantiza
        // que la pieza llega o el socket muere. MISDEAL del host es la única señal
        // de que la mano se cancela legítimamente.
        String localNick = GameFrame.getInstance().getNick_local();
        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    try {
                        String expectedCmd = (street == Crupier.FLOP) ? "FLOP_PIECE"
                                : (street == Crupier.TURN) ? "TURN_PIECE"
                                        : (street == Crupier.RIVER) ? "RIVER_PIECE" : null;
                        if (expectedCmd != null && partes.length >= 5 && partes[2].equals(expectedCmd)) {
                            String targetNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (!targetNick.equals(localNick)) {
                                // Pieza de otro destinatario; no la puedo
                                // descifrar (los demás siguen con su lock).
                                // Drop silencioso.
                                continue;
                            }
                            byte[] piece = java.util.Base64.getDecoder().decode(partes[4]);
                            int expectedLen = (street == Crupier.FLOP) ? 96 : 32;
                            if (piece == null || piece.length != expectedLen) {
                                LOGGER.log(Level.SEVERE,
                                        "ZERO-TRUST: community piece for street {0} has bad length {1} — host sent garbage, lockdown",
                                        new Object[]{street, (piece == null ? -1 : piece.length)});
                                triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                                return false;
                            }
                            byte[] unlocked = CryptoSRA.applyCommutativeLock(piece, this.local_sra_unlock);
                            int numCards = (street == Crupier.FLOP) ? 3 : 1;
                            int[] indices = new int[numCards];
                            for (int k = 0; k < numCards; k++) {
                                byte[] chunk = Arrays.copyOfRange(unlocked, k * 32, (k + 1) * 32);
                                int idx = CryptoSRA.resolveCardIndex(chunk);
                                if (idx < 0) {
                                    LOGGER.log(Level.SEVERE,
                                            "ZERO-TRUST: community piece for street {0} chunk {1} does NOT resolve to genesis — host sent wrong-slot bytes, lockdown",
                                            new Object[]{street, k});
                                    triggerSecurityLockdown(Translator.translate("zero_trust.host_community_garbage"));
                                    return false;
                                }
                                indices[k] = idx;
                            }
                            if (street == Crupier.FLOP) {
                                GameFrame.getInstance().getFlop1().actualizarConValorNumerico(indices[0] + 1);
                                GameFrame.getInstance().getFlop2().actualizarConValorNumerico(indices[1] + 1);
                                GameFrame.getInstance().getFlop3().actualizarConValorNumerico(indices[2] + 1);
                                this.flop_revealed = true;
                            } else if (street == Crupier.TURN) {
                                GameFrame.getInstance().getTurn().actualizarConValorNumerico(indices[0] + 1);
                                this.turn_revealed = true;
                            } else if (street == Crupier.RIVER) {
                                GameFrame.getInstance().getRiver().actualizarConValorNumerico(indices[0] + 1);
                                this.river_revealed = true;
                            }
                            ok = true;
                        } else if (partes.length >= 4 && partes[2].equals("MISDEAL")) {
                            String motivo = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                            cancelarManoYDevolverApuestas(motivo, false);
                            return false;
                        } else {
                            rejected.add(comando);
                        }
                    } catch (Exception ex) {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (!ok && !isFin_de_la_transmision()) {
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision());

        return true;
    }

    private ArrayList<Player> rondaApuestas(int street, ArrayList<Player> resisten) {

        LOGGER.log(Level.INFO, "[HAND {0}] ({1})",
                new Object[]{String.valueOf(getMano()), STREETS[street - 1]});

        disableAllPlayersTimeout();

        java.util.Iterator<Player> iterator = resisten.iterator();

        while (iterator.hasNext()) {
            Player jugador = iterator.next();
            if (!jugador.isActivo()) {
                iterator.remove();
                continue;
            }
            if (street == PREFLOP && GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).getBot() != null) {
                ((RemotePlayer) jugador).getBot().resetBot();
            }
        }

        setStreetLocal(street);

        if (this.street == Crupier.FLOP) {
            this.flop_players.clear();
            this.flop_players.addAll(resisten);
        }

        if (street > PREFLOP) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> loadingTask = scheduler.schedule(() -> {
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.ORANGE);
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setText(Translator.translate("zero_trust.decrypting_street"));
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
                });
            }, 500, TimeUnit.MILLISECONDS);

            boolean success = false;
            try {
                if (GameFrame.getInstance().isPartida_local()) {
                    success = enviarCartasComunitarias(resisten);
                } else {
                    success = recibirCartasComunitarias();
                }
            } finally {
                loadingTask.cancel(false);
                scheduler.shutdown();
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground()
                    );
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                });
            }

            if (!success) {
                resisten.clear();
                return resisten;
            }

            actualizarContadoresTapete();
            LOGGER.log(Level.INFO, "UNCOVER COM CARDS");
            destaparCartaComunitaria(street, resisten);
        }

        sqlUpdateHandPlayers(resisten);

        if (puedenApostar(resisten) > 0 && !this.cartas_resistencia) {

            if (street > PREFLOP) {
                this.apuesta_actual = 0f;
                this.ultimo_raise = 0f;
                this.conta_raise = 0;
                this.conta_bet = 0;
                for (Player jugador : resisten) {
                    jugador.setBet(0f);
                }
            }

            int conta_pos = 0;
            if (street == PREFLOP) {
                limpers = 0;
                while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.utg_nick)) {
                    conta_pos++;
                }
            } else {
                if (nick2player.containsKey(this.small_blind_nick) && nick2player.get(this.small_blind_nick).isActivo()) {
                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.small_blind_nick)) {
                        conta_pos++;
                    }
                } else {
                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.big_blind_nick)) {
                        conta_pos++;
                    }
                }
            }

            int end_pos = conta_pos;
            int decision = -1;

            resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), null, false);
            actualizarContadoresTapete();

            do {
                GameFrame.getInstance().checkPause();
                turno = System.currentTimeMillis();
                Object[] accion_recuperada = null;
                Object[] action = null;

                Player current_player = GameFrame.getInstance().getJugadores().get(conta_pos);

                if (!resisten.contains(current_player)
                        || current_player.getDecision() == Player.ALLIN
                        || current_player.getDecision() == Player.FOLD) {
                    conta_pos++;
                    if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                        conta_pos %= GameFrame.getInstance().getJugadores().size();
                    }
                    continue;
                }

                boolean isCryptoReplay = this.conta_accion < this.tot_acciones_recuperadas;
                boolean eraSincronizacion = this.isSincronizando_mano();

                // Dead branch: the two reads above are consecutive and never disagree
                // in single-threaded execution, so (!era && now) cannot fire. Kept here
                // for archaeology only; the real dragon-close after the replay ends
                // happens inside siguienteAccionLocalRecuperada (path #3) and as a
                // safety net after rondaApuestas(PREFLOP) (path #4).
                if (!eraSincronizacion && this.isSincronizando_mano()) {
                    this.setSincronizando_mano(false);
                    Helpers.GUIRun(() -> {
                        if (recover_dialog != null) {
                            recover_dialog.setVisible(false);
                            recover_dialog.dispose();
                            recover_dialog = null;
                        }
                        GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                        GameFrame.getInstance().refresh();
                    });
                    GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_recuperada"));
                    if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                        Audio.playWavResource("misc/startplay.wav");
                    }
                    if (GameFrame.MUSICA_AMBIENTAL) {
                        Audio.stopLoopMp3("misc/recovering.mp3");
                        Audio.playLoopMp3Resource("misc/background_music.mp3");
                    }
                }

                float old_player_bet = current_player.getBet();
                LOGGER.log(Level.INFO, "Read DECISION from -> {0}", current_player.getNickname());

                if (GameFrame.AUTO_ACTION_BUTTONS && current_player != GameFrame.getInstance().getLocalPlayer()
                        && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.FOLD
                        && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.ALLIN) {
                    GameFrame.getInstance().getLocalPlayer().activarPreBotones();
                }

                if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                    current_player.esTuTurno();
                    if (eraSincronizacion && (accion_recuperada = siguienteAccionLocalRecuperada(current_player.getNickname())) != null) {
                        LocalPlayer localplayer = (LocalPlayer) current_player;
                        localplayer.setClick_recuperacion(true);
                        switch ((int) accion_recuperada[0]) {
                            case Player.FOLD:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_fold_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.CHECK:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_check_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.ALLIN:
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_allin_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                            case Player.BET:
                                localplayer.setApuesta_recuperada((float) accion_recuperada[1]);
                                Helpers.GUIRun(() -> {
                                    localplayer.getPlayer_bet_button().doClick();
                                    localplayer.setClick_recuperacion(false);
                                });
                                break;
                        }
                    }

                    do {
                        synchronized (getLock_apuestas()) {
                            try {
                                getLock_apuestas().wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                            }
                        }
                    } while (current_player.isTurno());

                    decision = current_player.getDecision();
                    action = new Object[]{decision, current_player.getBet(), null};

                } else {
                    current_player.esTuTurno();
                    if (!current_player.isExit()) {
                        if (!GameFrame.getInstance().isPartida_local() || !GameFrame.getInstance().getParticipantes().get(current_player.getNickname()).isCpu()) {
                            action = this.readActionFromRemotePlayer(current_player);
                        } else {
                            if (!eraSincronizacion || (accion_recuperada = siguienteAccionLocalRecuperada(current_player.getNickname())) == null) {
                                long start = System.currentTimeMillis();
                                float call_required = getApuesta_actual() - current_player.getBet();
                                int decision_loki = ((RemotePlayer) current_player).getBot().calculateBotDecision(resisten.size() - 1);
                                action = new Object[]{decision_loki, 0f, null};

                                switch (decision_loki) {
                                    case Player.FOLD:
                                        if (Helpers.float1DSecureCompare(0f, this.getApuesta_actual()) == 0 || Helpers.float1DSecureCompare(current_player.getBet(), this.getApuesta_actual()) == 0) {
                                            action = new Object[]{Player.CHECK, 0f, null};
                                        }
                                        break;
                                    case Player.CHECK:
                                        if (Helpers.float1DSecureCompare(current_player.getStack(), call_required) <= 0) {
                                            action = new Object[]{Player.ALLIN, "", null};
                                        }
                                        break;
                                    case Player.BET:
                                        if (Helpers.float1DSecureCompare(current_player.getStack(), call_required) <= 0) {
                                            action = new Object[]{Player.ALLIN, "", null};
                                        } else {
                                            float b = ((RemotePlayer) current_player).getBot().getBetSize();
                                            if (Helpers.float1DSecureCompare(current_player.getStack() * 0.75f, b - current_player.getBet()) <= 0) {
                                                action = new Object[]{Player.ALLIN, "", null};
                                            } else if (puedenApostar(GameFrame.getInstance().getJugadores()) <= 1) {
                                                action = new Object[]{Player.CHECK, 0f, null};
                                            } else {
                                                action = new Object[]{Player.BET, b, null};
                                            }
                                        }
                                        break;
                                }

                                if (Init.DEV_MODE && ALLIN_BOT_TEST) {
                                    action = new Object[]{Player.ALLIN, "", null};
                                }

                                long bot_elapsed_time = System.currentTimeMillis() - start;
                                if (Bot.BOT_THINK_TIME - bot_elapsed_time > 0L) {
                                    Helpers.pausar(Bot.BOT_THINK_TIME - bot_elapsed_time);
                                }
                            } else {
                                action = accion_recuperada;
                            }
                        }
                    } else {
                        current_player.stopActionTimer();
                    }
                }

                if (action == null || action.length < 2) {
                    action = new Object[]{Player.FOLD, 0f, null};
                } else if (action.length < 3) {
                    action = new Object[]{action[0], action[1], null};
                }

                decision = (int) action[0];

                if (decision == Player.ALLIN) {
                    if ((action[1] instanceof String) && !"".equals((String) action[1])) {
                        this.current_remote_cinematic_b64 = (String) action[1];
                    }
                    action[1] = 0f;
                } else {
                    this.current_remote_cinematic_b64 = null;
                }

                // EC-Identity v1 (commit 5): resolve record + sig for this action.
                // Four sources cover all cases without ambiguity:
                //   1) RemotePlayer human, action came from the wire via
                //      readActionFromRemotePlayer → action[3]/[4] already populated.
                //   2) RemotePlayer that EXIT'd mid-turn → readActionFromRemotePlayer
                //      synthesised host-signed autofold with voluntary=false (§4.5).
                //   3) RemotePlayer that is a bot → host has to sign with its privkey
                //      because bots have no persistent identity (§10). action[] from
                //      the bot path is 3-slot, so fall through to local build below.
                //   4) LocalPlayer (host's or client's own UI) → action[] from the
                //      UI is 3-slot too; build with own privkey.
                byte[] localRecord = null;
                byte[] localSig = null;
                boolean isVoluntary = true;
                if (action.length >= 6) {
                    if (action[3] instanceof byte[]) {
                        localRecord = (byte[]) action[3];
                    }
                    if (action[4] instanceof byte[]) {
                        localSig = (byte[]) action[4];
                    }
                    if (action[5] instanceof Boolean) {
                        isVoluntary = (Boolean) action[5];
                    }
                }
                if (localRecord == null || localSig == null) {
                    Object[] recsig = buildLocalActionRecordAndSig(
                            current_player.getNickname(), decision, action[1], current_player, isVoluntary);
                    if (recsig != null) {
                        localRecord = (byte[]) recsig[0];
                        localSig = (byte[]) recsig[1];
                    }
                }

                // Cinematic_or_* field is now always present in the wire (fixed slot).
                String cinematicField = "*";
                if (decision == Player.ALLIN) {
                    if (current_player == GameFrame.getInstance().getLocalPlayer()
                            && !GameFrame.getInstance().isPartida_local()) {
                        if (this.current_local_cinematic_b64 != null) {
                            cinematicField = this.current_local_cinematic_b64;
                        }
                    } else if (this.current_remote_cinematic_b64 != null) {
                        cinematicField = this.current_remote_cinematic_b64;
                    }
                }

                String comando = null;
                try {
                    // EC-Identity v1 wire (commit 5):
                    //   ACTION#nickB64#decision#bet#cinematic_or_*#record_or_*#sig_or_*
                    comando = "ACTION#"
                            + java.util.Base64.getEncoder().encodeToString(current_player.getNickname().getBytes("UTF-8"))
                            + "#" + decision
                            + "#" + (decision == Player.BET ? String.valueOf((float) action[1]) : "0")
                            + "#" + cinematicField
                            + "#" + (localRecord != null ? java.util.Base64.getEncoder().encodeToString(localRecord) : "*")
                            + "#" + (localSig != null ? java.util.Base64.getEncoder().encodeToString(localSig) : "*");
                } catch (Exception ex) {
                }

                if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(comando, current_player.getNickname());
                    } else {
                        this.sendGAMECommandToServer(comando);
                    }
                } else {
                    ((RemotePlayer) current_player).setDecisionFromRemotePlayer(decision, (float) action[1]);
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(comando, current_player.getNickname());
                    }
                }

                do {
                    synchronized (getLock_apuestas()) {
                        try {
                            getLock_apuestas().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                } while (current_player.isTurno());

                // EC-Identity v1 (commit 5): absorb the (record || sig) bytes into H_t.
                // Same call on host and every client, so the chain stays byte-identical
                // across the table. Failed-verify sigs are absorbed too so divergence
                // remains detectable at hand close (§4.5).
                absorbActionIntoChain(current_player.getNickname(), localRecord, localSig);

                Bot.OpponentTracker stats = Bot.TRACKER_MEMORY.computeIfAbsent(current_player.getNickname(), k -> new Bot.OpponentTracker());

                if (this.street == Crupier.PREFLOP) {
                    boolean isBBCheck = current_player.getNickname().equals(this.big_blind_nick)
                            && decision == Player.CHECK
                            && Helpers.float1DSecureCompare(this.apuesta_actual, this.getCiega_grande()) == 0;
                    if (!isBBCheck && (decision == Player.CHECK || decision == Player.BET || decision == Player.ALLIN)) {
                        stats.recordVPIP(this.conta_mano);
                    }
                    if (decision == Player.BET || decision == Player.ALLIN) {
                        stats.recordPFR(this.conta_mano);
                    }
                } else {
                    if (decision == Player.BET || decision == Player.ALLIN) {
                        stats.recordPostFlopBetOrRaise();
                    } else if (decision == Player.CHECK && this.apuesta_actual > old_player_bet) {
                        stats.recordPostFlopCall();
                    }
                }

                GameFrame.getInstance().getRegistro().print(current_player.getLastActionString());

                if (decision != Player.FOLD) {
                    this.apuestas += current_player.getBet() - old_player_bet;
                    this.bote_total += current_player.getBet() - old_player_bet;

                    if (decision == Player.BET || (decision == Player.ALLIN && Helpers.float1DSecureCompare(this.apuesta_actual, current_player.getBet()) < 0)) {
                        boolean partial_raise = false;
                        float min_raise = Helpers.float1DSecureCompare(0f, getUltimo_raise()) < 0 ? getUltimo_raise() : Helpers.floatClean(getCiega_grande());
                        float current_raise = current_player.getBet() - this.apuesta_actual + this.partial_raise_cum;

                        if (Helpers.float1DSecureCompare(min_raise, current_raise) <= 0) {
                            this.ultimo_raise = current_raise;
                            this.partial_raise_cum = 0f;
                            this.conta_raise++;
                        } else if (decision == Player.ALLIN) {
                            partial_raise = true;
                            this.partial_raise_cum += current_player.getBet() - this.apuesta_actual;
                        }

                        this.conta_bet++;
                        this.apuesta_actual = current_player.getBet();
                        resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), partial_raise ? (this.last_aggressor != null ? this.last_aggressor.getNickname() : null) : current_player.getNickname(), partial_raise);

                        if (street == PREFLOP) {
                            limpers = 0;
                        }
                        end_pos = conta_pos;

                    } else if (street == PREFLOP && Helpers.float1DSecureCompare(this.apuesta_actual, this.getCiega_grande()) == 0 && !current_player.getNickname().equals(this.getBb_nick()) && !current_player.getNickname().equals(this.getSb_nick())) {
                        limpers++;
                    }
                } else {
                    resisten.remove(current_player);
                }

                try {
                    this.acciones.add(java.util.Base64.getEncoder().encodeToString(current_player.getNickname().getBytes("UTF-8"))
                            + "#" + String.valueOf(decision)
                            + (decision == Player.BET ? "#" + String.valueOf((float) action[1]) : ""));
                } catch (Exception ex) {
                }

                this.conta_accion++;

                if (!isCryptoReplay) {
                    this.sqlNewAction(current_player);
                } else if (GameFrame.getInstance().isPartida_local()) {
                    if (this.sqlCheckGenuineRecoverAction(current_player)) {
                        LOGGER.log(Level.INFO, "RECOVER ACTION OK");
                    }
                }

                actualizarContadoresTapete();
                conta_pos++;
                if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                    conta_pos %= GameFrame.getInstance().getJugadores().size();
                }

            } while (conta_pos != end_pos && resisten.size() > 1 && !isFin_de_la_transmision());

            this.apuestas = 0f;
            actualizarContadoresTapete();
            GameFrame.getInstance().hideTapeteApuestas();

            if (resisten.size() > 1 && puedenApostar(resisten) <= 1) {
                this.destapar_resistencia = true;
                if (resisten.contains(GameFrame.getInstance().getLocalPlayer())) {
                    GameFrame.getInstance().getLocalPlayer().desactivarControles();
                }
                procesarCartasResistencia(resisten, true);
                checkJugadasParciales(resisten);
            }

            if (this.street == Crupier.PREFLOP) {
                nick2player.get(this.utg_nick).disableUTG();
            }

        }

        if (isFin_de_la_transmision()) {
            // Game cancelled mid-round: park the crupier thread here so the
            // recursive rondaApuestas(street + 1, ...) below is never reached
            // once fin_de_la_transmision is set. Loop with a timeout to be
            // resilient to spurious wakeups and to unrelated notifyAll() calls
            // on lock_apuestas (incoming bets, queue drains). Exit only on
            // thread interruption.
            synchronized (getLock_apuestas()) {
                while (isFin_de_la_transmision()) {
                    try {
                        getLock_apuestas().wait(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return (resisten.size() > 1 && street < RIVER && getJugadoresActivos() > 1)
                ? rondaApuestas(street + 1, resisten)
                : resisten;
    }

    public void guardarFosilSRA() {
        if (this.local_mega_packet == null || this.active_crypto_ring == null) {
            return;
        }
        try {
            StringBuilder fosil = new StringBuilder();
            fosil.append("ORDER@");
            for (String nickRing : this.active_crypto_ring) {
                fosil.append(java.util.Base64.getEncoder().encodeToString(nickRing.getBytes("UTF-8"))).append(",");
            }
            fosil.append("#FULLMEGAPACKET@").append(java.util.Base64.getEncoder().encodeToString(this.local_mega_packet));

            byte[] unlockToSave = this.local_sra_unlock;
            if (unlockToSave == null) {
                Participant p = GameFrame.getInstance().getParticipantes().get(GameFrame.getInstance().getNick_local());
                if (p != null) {
                    unlockToSave = p.getSra_unlock();
                }
            }

            if (unlockToSave != null) {
                fosil.append("#SRAKEYS@").append(java.util.Base64.getEncoder().encodeToString(unlockToSave));
            }

            StringBuilder botKeys = new StringBuilder();
            StringBuilder botVisuals = new StringBuilder();
            for (String nick : this.active_crypto_ring) {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                Player botPlayer = nick2player.get(nick);

                if (p != null && p.isCpu()) {
                    if (p.getReceived_token() != null) {
                        botKeys.append(java.util.Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                                .append(":")
                                .append(java.util.Base64.getEncoder().encodeToString(p.getReceived_token()))
                                .append(",");
                    }
                    if (botPlayer != null && botPlayer.getHoleCard1().getCartaComoEntero() >= 0) {
                        botVisuals.append(java.util.Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")))
                                .append(":")
                                .append(botPlayer.getHoleCard1().getCartaComoEntero()).append(",")
                                .append(botPlayer.getHoleCard2().getCartaComoEntero()).append("@");
                    }
                }
            }
            if (botKeys.length() > 0) {
                fosil.append("#BOTKEYS@").append(botKeys.toString());
            }
            if (botVisuals.length() > 0) {
                fosil.append("#BOTVISUAL@").append(botVisuals.toString());
            }

            // VISUAL@ guarda los índices 0..51 que resuelven a una carta
            // del genesis deck. Si la mano abortó por MISDEAL antes de
            // descifrar correctamente las hole cards, resolveCardIndex
            // devolvió -1 y guardar ese valor envenena el fósil: en
            // recovery se lee como byte=-1 → (byte&0xFF)+1 = 256 →
            // PALOS[19] OOB → CRUPIER FATAL ERROR. Sólo persistimos si
            // ambos índices son válidos.
            if (this.local_original_cards != null
                    && this.local_original_cards[0] >= 0 && this.local_original_cards[0] < 52
                    && this.local_original_cards[1] >= 0 && this.local_original_cards[1] < 52) {
                fosil.append("#VISUAL@").append(this.local_original_cards[0]).append(",").append(this.local_original_cards[1]);
            }

            Helpers.saveHandFossil(this.sqlite_id_game, fosil.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving SRA fossil to disk", e);
        }
    }

    private void procesarCartasComunesRestantes() {
        Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());
        try {
            if (street <= PREFLOP && GameFrame.getInstance().getFlop1().isTapada()) {
                enviarRabbitComunitarias(FLOP);
            }
            if (street <= FLOP && GameFrame.getInstance().getTurn().isTapada()) {
                enviarRabbitComunitarias(TURN);
            }
            if (street <= TURN && GameFrame.getInstance().getRiver().isTapada()) {
                enviarRabbitComunitarias(RIVER);
            }
        } catch (Exception e) {
        }
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), 0);
    }

    private boolean enviarRabbitComunitarias(int targetStreet) {
        if (this.local_sra_unlock == null || this.active_crypto_ring == null) {
            return false;
        }

        int numPlayers = this.active_crypto_ring.length;
        int numCards = (targetStreet == Crupier.FLOP) ? 3 : 1;
        int offset = numPlayers * 2;
        if (targetStreet == Crupier.FLOP) {
            offset += 1;
        } else if (targetStreet == Crupier.TURN) {
            offset += 1 + 3 + 1;
        } else if (targetStreet == Crupier.RIVER) {
            offset += 1 + 3 + 1 + 1 + 1;
        }

        int rabbitPhase = phaseForStreet(targetStreet, true);
        String pieceCommand = (targetStreet == Crupier.FLOP) ? "RABBIT_FLOP_PIECE"
                : (targetStreet == Crupier.TURN) ? "RABBIT_TURN_PIECE" : "RABBIT_RIVER_PIECE";

        int[] hostIndices = cascadeAndDealCommunityPieces(offset, numCards, rabbitPhase, pieceCommand, false);
        if (hostIndices == null) {
            return false;
        }

        if (targetStreet == Crupier.FLOP) {
            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(hostIndices[1] + 1);
            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(hostIndices[2] + 1);
            GameFrame.getInstance().getFlop1().taparRabbit();
            GameFrame.getInstance().getFlop2().taparRabbit();
            GameFrame.getInstance().getFlop3().taparRabbit();
        } else if (targetStreet == Crupier.TURN) {
            GameFrame.getInstance().getTurn().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getTurn().taparRabbit();
        } else if (targetStreet == Crupier.RIVER) {
            GameFrame.getInstance().getRiver().actualizarConValorNumerico(hostIndices[0] + 1);
            GameFrame.getInstance().getRiver().taparRabbit();
        }
        return true;
    }

    private void solicitarYRecibirCartasVisuales(ArrayList<Player> resisten) {
        if (!GameFrame.getInstance().isPartida_local()) {
            return;
        }

        ArrayList<String> pendientes = new ArrayList<>();
        for (Player p : resisten) {
            if (!p.getNickname().equals(GameFrame.getInstance().getNick_local()) && !p.isExit()) {
                Participant part = GameFrame.getInstance().getParticipantes().get(p.getNickname());
                // Do not ask bots, the server already knows their deterministic cards
                if (part != null && !part.isCpu()) {
                    pendientes.add(p.getNickname());
                }
            }
        }

        if (pendientes.isEmpty()) {
            return;
        }

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        // Request remote clients to confess their cards for the UI
        String reqCmd = "GAME#" + id + "#REQ_VISUAL_CARDS";
        for (String nick : pendientes) {
            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
            if (p != null) {
                p.writeCommandFromServer(Helpers.encryptCommand(reqCmd, p.getAes_key(), iv, p.getHmac_key()));
            }
        }

        long start_time = System.currentTimeMillis();
        boolean timeout = false;

        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!pendientes.isEmpty() && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    // Wait for the visual confession
                    if (partes.length >= 6 && partes[2].equals("VISUAL_CARDS_RESP")) {
                        try {
                            String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (pendientes.contains(nick)) {
                                Player remoteP = nick2player.get(nick);
                                if (remoteP != null) {
                                    String c1_str = partes[4];
                                    String c2_str = partes[5];
                                    if (c1_str != null && c1_str.contains("_") && c2_str != null && c2_str.contains("_")) {
                                        String[] c1 = c1_str.split("_");
                                        String[] c2 = c2_str.split("_");

                                        // Assign in memory ONLY for the visual Montecarlo
                                        remoteP.getHoleCard1().actualizarValorPalo(c1[0], c1[1]);
                                        remoteP.getHoleCard2().actualizarValorPalo(c2[0], c2[1]);

                                        // BROADCAST: Send these visual cards to the rest of the clients
                                        broadcastGAMECommandFromServer("VISUAL_UPDATE#" + partes[3] + "#" + c1_str + "#" + c2_str, nick, false);
                                    }
                                }
                                pendientes.remove(nick);
                            } else {
                                rejected.add(comando);
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error processing VISUAL_CARDS_RESP", e);
                        }
                    } else if (partes.length >= 6 && partes[2].equals("VISUAL_UPDATE")) {
                        // Receive visual cards from another client and update UI instantly
                        try {
                            String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            Player p_ui = nick2player.get(nick);
                            if (p_ui != null && !p_ui.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                                // Solo actualizamos si la carta sigue boca abajo (evita race con showdown).
                                if (p_ui.getHoleCard1().isTapada()) {
                                    String[] c1 = partes[4].split("_");
                                    String[] c2 = partes[5].split("_");
                                    p_ui.getHoleCard1().actualizarValorPalo(c1[0], c1[1]);
                                    p_ui.getHoleCard2().actualizarValorPalo(c2[0], c2[1]);
                                }
                            }
                        } catch (Exception e) {
                        }
                    } else {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (!pendientes.isEmpty()) {
                GameFrame.getInstance().checkPause();
                synchronized (this.getReceived_commands()) {
                    try {
                        this.received_commands.wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while (!pendientes.isEmpty() && !isFin_de_la_transmision());
    }

    private void checkJugadasParciales(ArrayList<Player> resisten) {

        if (this.destapar_resistencia && this.street != Crupier.RIVER) {

            HashMap<Player, Hand> jugadas = calcularJugadas(resisten);
            HashMap<Player, Hand> ganadores = calcularGanadores(new HashMap<>(jugadas));

            // Blindaje contra nulls
            for (Player p : resisten) {
                if (jugadas.containsKey(p)) {
                    p.setJugadaParcial(jugadas.get(p), ganadores.containsKey(p), -1);
                }
            }

            HashMap<Player, Integer[]> multiverse = monteCarlo(resisten, MONTECARLO_ITERATIONS);

            for (Player p : resisten) {
                // Solo simulamos los que confesaron sus cartas
                if (!jugadas.containsKey(p)) {
                    continue;
                }

                org.alberta.poker.Card card1 = Bot.coronaCard2LokiCard(p.getHoleCard1());
                org.alberta.poker.Card card2 = Bot.coronaCard2LokiCard(p.getHoleCard2());

                if (card1 == null || card2 == null) {
                    continue;
                }

                double strength = Bot.HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS,
                        resisten.size() - 1);
                double ppot = Bot.HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, false);
                double npot = Bot.HANDPOTENTIAL.getLastNPot();
                double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

                jugadas.get(p).setFuerza(effectiveStrength * 100);
            }

            ArrayList<Object[]> stats_ordenadas = new ArrayList<>();

            for (Player p : resisten) {
                if (multiverse.containsKey(p) && jugadas.containsKey(p)) {
                    stats_ordenadas.add(new Object[]{p, multiverse.get(p)});
                }
            }

            stats_ordenadas.sort((a, b) -> ((Integer[]) b[1])[1] - ((Integer[]) a[1])[1]);

            ArrayList<String> stats_registro = new ArrayList<>();

            for (Object[] s : stats_ordenadas) {
                Player p = (Player) s[0];
                Integer[] stats = (Integer[]) s[1];
                Hand manoParcial = jugadas.get(p);

                p.setJugadaParcial(manoParcial, ganadores.containsKey(p),
                        Helpers.floatClean(((float) (stats[1] + stats[3]) / stats[0]) * 100));

                stats_registro.add(p.getNickname() + " (" + Card.collection2String(p.getHoleCards())
                        + Translator.translate("ui.multiverso") + stats[0] + Translator.translate("ui.gana")
                        + Helpers.floatClean(((float) stats[1] / stats[0]) * 100, 2)
                        + Translator.translate("ui.pierde")
                        + Helpers.floatClean(((float) stats[2] / stats[0]) * 100, 2)
                        + Translator.translate("ui.empata")
                        + Helpers.floatClean(((float) stats[3] / stats[0]) * 100, 2) + "%   (LOKI: "
                        + Helpers.floatClean((float) manoParcial.getFuerza(), 2) + "%)");
            }

            if (!stats_registro.isEmpty()) {
                GameFrame.getInstance().getRegistro().print(String.join("\n\n", stats_registro));
            }
        }
    }

    private void waitRabbitProcessing() {

        boolean pending;

        do {
            pending = false;

            for (Map.Entry<String, Boolean> entry : rabbit_players.entrySet()) {

                pending = entry.getValue();

                if (pending) {
                    break;
                }

            }

            if (pending) {
                synchronized (lock_rabbit) {
                    try {
                        lock_rabbit.wait(1000);
                    } catch (InterruptedException ex) {
                        Helpers.logCooperativeCancellation(LOGGER, "rabbit hunting wait", ex);
                        break;
                    }
                }
            }

        } while (pending);
    }

    /**
     * EC-Identity v1: seed the per-hand H_t chain from the three pieces every peer
     * already has after MEGAPACKET — the HAND_ID from the host, the active_crypto_ring
     * nicks and the cascaded deck. Idempotent: if any of them is missing the chain is
     * set to null so downstream absorb calls become no-ops (failing soft until commit 5
     * makes the chain a hard requirement).
     */
    private void initHandStateChain() {
        if (this.current_hand_id == null
                || this.local_mega_packet == null
                || this.active_crypto_ring == null
                || this.active_crypto_ring.length == 0) {
            this.hand_state_chain = null;
            return;
        }
        java.util.List<byte[]> playerIds = new java.util.ArrayList<>();
        for (String nick : this.active_crypto_ring) {
            playerIds.add(CanonicalActionRecord.playerIdFromNick(nick));
        }
        try {
            this.hand_state_chain = HandStateChain.start(
                    this.current_hand_id, playerIds, this.local_mega_packet);
            LOGGER.log(Level.INFO, "Hand state chain initialized: H_0={0}",
                    Base64.getEncoder().encodeToString(this.hand_state_chain.getCurrentHash()));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "HandStateChain.start failed: " + ex.getMessage(), ex);
            this.hand_state_chain = null;
        }
    }

    /**
     * Translate the in-memory Java action enum into the wire enum the spec defines
     * (§4.3). Java collapses CALL into CHECK and RAISE into BET (the bet amount
     * differentiates them); we keep that collapse on the wire side too. What matters
     * is that every peer maps the same Java decision to the same wire value, so H_t
     * converges across the table.
     */
    private static int mapJavaActionToWire(int javaDecision) {
        switch (javaDecision) {
            case Player.FOLD:  return CanonicalActionRecord.ACTION_FOLD;
            case Player.CHECK: return CanonicalActionRecord.ACTION_CHECK;
            case Player.BET:   return CanonicalActionRecord.ACTION_BET;
            case Player.ALLIN: return CanonicalActionRecord.ACTION_ALLIN;
            default:
                throw new IllegalArgumentException("Unmappable Java decision: " + javaDecision);
        }
    }

    /** Java streets are 1-based (PREFLOP=1..SHOWDOWN=5), wire is 0-based. */
    private static int mapJavaStreetToWire(int javaStreet) {
        return javaStreet - 1;
    }

    /**
     * Returns {@code true} iff {@code nick} sits in the current hand's crypto-ring.
     * Players who joined or warmed up after the deal (and therefore are not in
     * {@code active_crypto_ring}) are excluded so their stray actions don't pollute
     * the chain.
     */
    private boolean isInActiveCryptoRing(String nick) {
        String[] ring = this.active_crypto_ring;
        if (ring == null) {
            return false;
        }
        for (String n : ring) {
            if (n.equals(nick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * EC-Identity v1: absorbs the wire (record, sig) bytes directly into the
     * peer's H_t chain. Used by the action loop once the canonical record +
     * signature are in hand. Invalid signatures are absorbed too (the receipt
     * protocol catches divergence at hand close, §4.5). No-op when the chain
     * is uninitialised (older host without HAND_ID, recovery paths, etc).
     */
    private void absorbActionIntoChain(String playerNick, byte[] record, byte[] sig) {
        HandStateChain chain = this.hand_state_chain;
        if (chain == null) {
            return;
        }
        if (record == null || sig == null) {
            return;
        }
        if (!isInActiveCryptoRing(playerNick)) {
            return;
        }
        try {
            byte[] newHash = chain.absorb(record, sig);
            if (HandStateChain.DEBUG_HANDCHAIN) {
                try {
                    String hCheckCmd = "H_CHECK#"
                            + Base64.getEncoder().encodeToString(playerNick.getBytes("UTF-8"))
                            + "#" + Base64.getEncoder().encodeToString(newHash);
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(hCheckCmd, null, false);
                    }
                    LOGGER.log(Level.INFO, "H_CHECK after {0}'s signed action: {1}",
                            new Object[]{playerNick, Base64.getEncoder().encodeToString(newHash)});
                } catch (Exception broadcastEx) {
                    LOGGER.log(Level.WARNING, "H_CHECK broadcast failed: " + broadcastEx.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to absorb signed action into hand state chain (nick=" + playerNick + ")", ex);
        }
    }

    /**
     * EC-Identity v1 (commit 5): predicts the post-action total bet (in cents) for the
     * player about to act, WITHOUT waiting for async setDecision side-effects.
     *
     * <ul>
     *   <li>FOLD → 0 (no money moves)</li>
     *   <li>CHECK → {@code apuesta_actual} (covers true check and call)</li>
     *   <li>BET → absolute target supplied by the local UI / bot logic</li>
     *   <li>ALLIN → {@code player.bet + player.stack} (allin moves stack into bet,
     *       so the sum is invariant pre/post-action)</li>
     * </ul>
     */
    private long predictPostActionBetCents(int javaDecision, Object originalActionBet, Player player) {
        switch (javaDecision) {
            case Player.FOLD:
                return 0L;
            case Player.CHECK:
                return CanonicalActionRecord.amountToCents(Helpers.floatClean(this.apuesta_actual));
            case Player.BET: {
                float betFloat = 0f;
                if (originalActionBet instanceof Float) {
                    betFloat = (Float) originalActionBet;
                } else if (originalActionBet instanceof Number) {
                    betFloat = ((Number) originalActionBet).floatValue();
                }
                return CanonicalActionRecord.amountToCents(Helpers.floatClean(betFloat));
            }
            case Player.ALLIN:
                return CanonicalActionRecord.amountToCents(
                        Helpers.floatClean(player.getBet() + player.getStack()));
            default:
                return 0L;
        }
    }

    /**
     * EC-Identity v1 (commit 5): resolves the Ed25519 raw pubkey to verify an action's
     * signature against, applying the §10 consolidated receiver rule:
     *
     * <ul>
     *   <li>voluntary=0 (host auto-fold per §4.5) → host's pubkey</li>
     *   <li>actor is a bot (Participant.isCpu()) → host's pubkey (§10)</li>
     *   <li>otherwise (voluntary human action) → actor's pubkey</li>
     * </ul>
     *
     * Host's pubkey resolution depends on which side we are on:
     *   - partida_local=true (this process IS the host): IdentityManager.getInstance().getPublicKey().
     *   - partida_local=false (this process is a client): participantes.get(server_nick).getIdentity_pubkey().
     *
     * Returns null when the requested pubkey is not yet available (TOFU race during
     * a fresh JOIN). Caller treats null as "verification skipped" and logs.
     */
    private byte[] resolveActionSignerPubkey(String actorNick, boolean isVoluntary) {
        boolean useHostKey = !isVoluntary;
        if (!useHostKey) {
            Participant actorPar = GameFrame.getInstance().getParticipantes().get(actorNick);
            if (actorPar != null && actorPar.isCpu()) {
                useHostKey = true;
            } else if (actorPar != null) {
                return actorPar.getIdentity_pubkey();
            } else {
                return null;
            }
        }
        if (GameFrame.getInstance().isPartida_local()) {
            IdentityManager im = IdentityManager.getInstance();
            return im.isReady() ? im.getPublicKey() : null;
        }
        String hostNick = GameFrame.getInstance().getSala_espera().getServer_nick();
        if (hostNick == null) {
            return null;
        }
        Participant hostPar = GameFrame.getInstance().getParticipantes().get(hostNick);
        return hostPar != null ? hostPar.getIdentity_pubkey() : null;
    }

    /**
     * EC-Identity v1 (commit 5): builds and signs a canonical action record for an
     * action originating LOCALLY in this process — host's own UI, a bot (host drives
     * it), an auto-fold (host fabricates on behalf of a timed-out / EXITed peer), or
     * a client's own UI when partida_local is false. Returns null when the chain is
     * uninitialised or IdentityManager isn't ready (the wire then carries "*"
     * placeholders so the receiver skips verification gracefully).
     *
     * <p>{@code isVoluntary} is true for genuine decisions, false for host-fabricated
     * auto-folds (§4.5). Bot actions are voluntary=true but signed by the host's
     * privkey — receivers identify them by Participant.isCpu() (§10).
     */
    private Object[] buildLocalActionRecordAndSig(String playerNick, int javaDecision,
            Object originalActionBet, Player player, boolean isVoluntary) {
        HandStateChain chain = this.hand_state_chain;
        if (chain == null) {
            return null;
        }
        IdentityManager im = IdentityManager.getInstance();
        if (!im.isReady()) {
            return null;
        }
        try {
            int wireAction = mapJavaActionToWire(javaDecision);
            int wireStreet = mapJavaStreetToWire(this.street);
            long cents = predictPostActionBetCents(javaDecision, originalActionBet, player);
            byte[] pid = CanonicalActionRecord.playerIdFromNick(playerNick);
            byte[] record = CanonicalActionRecord.encode(
                    chain.getCurrentHash(),
                    chain.getHandId(),
                    pid,
                    wireStreet,
                    wireAction,
                    cents,
                    javaDecision == Player.ALLIN,
                    isVoluntary);
            byte[] sig = im.signAction(record);
            return new Object[]{record, sig};
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Failed to build local action record/sig (nick=" + playerNick
                    + ", decision=" + javaDecision + ")", ex);
            return null;
        }
    }

    private void sentarParticipantes() {

        String pivote = GameFrame.getInstance().getNick_local();

        int i = 0;

        while (!this.nicks_permutados[i].equals(pivote)) {
            i++;
        }

        String sentados_msg = "\n*********************\n";

        for (int j = 0; j < this.nicks_permutados.length; j++) {

            GameFrame.getInstance().getJugadores().get(j)
                    .setNickname(this.nicks_permutados[(j + i) % this.nicks_permutados.length]);
            try {
                sentados_msg += Base64.getEncoder().encodeToString(
                        GameFrame.getInstance().getJugadores().get(j).getNickname().getBytes("UTF-8")) + "|"
                        + GameFrame.getInstance().getJugadores().get(j).getNickname() + "\n";
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        Logger
                .getLogger(Crupier.class
                        .getName())
                .log(Level.INFO, "{0}*********************\n", new Object[]{sentados_msg
        });

    }

    public void disableAllPlayersTimeout() {

        for (Player j : GameFrame.getInstance().getJugadores()) {

            j.setTimeout(false);
        }

    }

    public void broadcastGAMECommandFromServer(String command, String skip_nick, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();
        ArrayList<Participant> targets = new ArrayList<>();

        Map<String, Participant> participantes_map = GameFrame.getInstance().getParticipantes();
        synchronized (participantes_map) {
            for (Map.Entry<String, Participant> entry : participantes_map.entrySet()) {
                Participant p = entry.getValue();
                if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && !p.isExit()) {
                    pendientes.add(p.getNick());
                    targets.add(p);
                }
            }
        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            // Sin timeout artificial: si un cliente tarda mucho en confirmar (red lenta,
            // CPU saturada, baraja SRA grande), seguimos esperando indefinidamente.
            // TCP nos garantiza que el mensaje llegará o el socket morirá; nunca se
            // pierde "en el medio". La única salida del bucle es que pendientes se
            // vacíe (todos confirmaron o todos se marcaron exit porque su socket murió
            // de verdad). Antes este loop forzaba remotePlayerQuit() tras
            // CLIENT_RECON_TIMEOUT, lo que provocaba kicks injustos a clientes
            // simplemente lentos durante cascadas SRA.
            do {
                String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                for (Participant p : targets) {
                    if (pendientes.contains(p.getNick())) {
                        p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));
                    }
                }

                if (confirmation) {
                    this.waitSyncConfirmations(id, pendientes);

                    for (Participant p : targets) {
                        if (!p.getNick().equals(skip_nick) && p.isExit()) {
                            pendientes.remove(p.getNick());
                            if (nick2player.containsKey(p.getNick())) {
                                nick2player.get(p.getNick()).setTimeout(false);
                            }
                        }
                    }

                    if (!pendientes.isEmpty() && !nick2player.isEmpty()) {
                        for (String nick : pendientes) {
                            nick2player.get(nick).setTimeout(true);
                            if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                                try {
                                    this.broadcastGAMECommandFromServer(
                                            "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")),
                                            nick, false);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    }
                }

            } while (confirmation && !pendientes.isEmpty());
        }
    }

    public void broadcastGAMECommandFromServer(String command, String skip_nick) {

        broadcastGAMECommandFromServer(command, skip_nick, true);
    }

    private int permutadoNick2Pos(String nick) {

        int i = 0;

        while (i < this.nicks_permutados.length && !this.nicks_permutados[i].equals(nick)) {
            i++;
        }

        if (i == this.nicks_permutados.length) {
            return -1;
        }

        return i;
    }

    private String permutadoPos2Nick(int i) {

        if (i < 0) {

            while (i < 0) {
                i += nicks_permutados.length;
            }

        } else if (i >= nicks_permutados.length) {
            i = i % nicks_permutados.length;
        }

        return nicks_permutados[i];

    }

    // DEAD BUTTON STRATEGY
    private void calcularPosiciones() {

        String old_dealer_nick = this.dealer_nick;

        String old_big_blind = this.big_blind_nick;

        int big_blind_pos = 0;

        if (this.big_blind_nick == null) {

            // FIRST GAME HAND
            this.big_blind_nick = this.nicks_permutados[0];

            big_blind_pos = 0;

        } else {

            int old_bb_pos = permutadoNick2Pos(this.big_blind_nick);

            String new_big_blind = null;

            if (old_bb_pos == -1) {

                try {
                    // BIG BLIND LEFT THE GAME

                    String[] asientos = this.sqlRecoverGameSeats().split("#");

                    String grande_b64 = Base64.getEncoder().encodeToString(this.big_blind_nick.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i
                            && this.permutadoNick2Pos(
                                    new String(Base64.getDecoder().decode(asientos[i]), "UTF-8")) == -1) {

                        i = (i + 1) % asientos.length;
                    }

                    new_big_blind = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");

                    big_blind_pos = permutadoNick2Pos(new_big_blind);

                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

            } else {

                big_blind_pos = old_bb_pos + 1;

                new_big_blind = permutadoPos2Nick(big_blind_pos);
            }

            while (!this.nick2player.containsKey(new_big_blind) || !this.nick2player.get(new_big_blind).isActivo()) {

                new_big_blind = permutadoPos2Nick(++big_blind_pos);

            }

            this.big_blind_nick = new_big_blind;
        }

        if (getJugadoresActivos() == 2) {

            for (Player j : GameFrame.getInstance().getJugadores()) {

                if (j.isActivo() && !j.getNickname().equals(this.big_blind_nick)) {
                    this.small_blind_nick = j.getNickname();
                    break;
                }

            }

            this.dealer_nick = this.small_blind_nick;

            this.utg_nick = this.dealer_nick;

        } else {

            // UTG
            int utg_pos = big_blind_pos + 1;

            String new_utg = permutadoPos2Nick(utg_pos);

            while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {

                new_utg = permutadoPos2Nick(++utg_pos);

            }

            this.utg_nick = new_utg;

            // DEALER
            int dealer_pos;

            String new_dealer;

            String old_small_blind = this.small_blind_nick;

            if (old_small_blind != null) {

                new_dealer = old_small_blind;

                dealer_pos = permutadoNick2Pos(new_dealer);

                if (dealer_pos == -1) {

                    try {

                        String[] asientos = this.sqlRecoverGameSeats().split("#");

                        String dealer_b64 = Base64.getEncoder().encodeToString(new_dealer.getBytes("UTF-8"));

                        int i = 0;

                        while (i < asientos.length && !asientos[i].equals(dealer_b64)) {

                            i++;
                        }

                        int j = i;

                        i--;

                        if (i < 0) {
                            i += asientos.length;
                        }

                        while (j != i && this
                                .permutadoNick2Pos(
                                        new String(Base64.getDecoder().decode(asientos[i]), "UTF-8")) == -1) {

                            i--;

                            if (i < 0) {
                                i += asientos.length;
                            }
                        }

                        new_dealer = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");

                        dealer_pos = permutadoNick2Pos(new_dealer);

                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                }

            } else {

                dealer_pos = big_blind_pos - 2;
                new_dealer = permutadoPos2Nick(dealer_pos);

            }

            while (!this.nick2player.containsKey(new_dealer) || !this.nick2player.get(new_dealer).isActivo()) {

                new_dealer = permutadoPos2Nick(--dealer_pos);

            }

            this.dealer_nick = new_dealer;

            // SMALL BLIND
            if (old_big_blind != null) {

                this.small_blind_nick = old_big_blind;

            } else {

                this.small_blind_nick = permutadoPos2Nick(big_blind_pos - 1);
            }

        }

        this.dead_dealer = (this.dealer_nick.equals(old_dealer_nick));
    }

    private void setPositions() {

        if (GameFrame.getInstance().isPartida_local()) {

            this.calcularPosiciones();

            String comando = null;

            boolean doblar_ciegas = this.checkDoblarCiegas();

            try {

                comando = "POSITIONS#" + Base64.getEncoder().encodeToString(this.utg_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(this.big_blind_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(this.small_blind_nick.getBytes("UTF-8")) + "#"
                        + Base64.getEncoder().encodeToString(
                                this.dealer_nick != null ? this.dealer_nick.getBytes("UTF-8") : "".getBytes("UTF-8"))
                        + "#" + String.valueOf(GameFrame.getInstance().getConta_tiempo_juego())
                        + (doblar_ciegas ? "#1" : "#0");

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            broadcastGAMECommandFromServer(comando, null);

            if (doblar_ciegas) {
                this.doblarCiegas();
            }

        } else {

            // Recibimos las posiciones utg, bb, sb, dealer calculados por el servidor
            this.recibirPosiciones();

        }
    }

    private ArrayList<String> recuperarSorteoSitios() {

        try {

            ArrayList<String> actuales = new ArrayList<>();

            for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                actuales.add(entry.getKey());
            }

            String[] sitiosb64 = this.sqlRecoverGameSeats().split("#");

            String preflop_players = (String) this.sqlRecoverServerLocalGameKeyData(false).get("preflop_players");

            // Tras un MISDEAL que aborta antes de que la mano tenga el row
            // preflop_players guardado en SQL, esta lectura devuelve null y el
            // .contains(b64) de abajo lanzaba NullPointerException. El catch
            // sólo coge IOException, así que el NPE escapaba y mataba el
            // thread del Crupier silenciosamente — el cliente quedaba
            // esperando un SEATS que nunca llegaba ("sorteando sitios"
            // colgado). Devolviendo null aquí, el caller (sortearSitios) cae
            // en su rama `else` y hace un shuffle fresh, comportamiento de
            // "no hay nada que recuperar".
            if (preflop_players == null) {
                LOGGER.log(Level.WARNING, "recuperarSorteoSitios: no preflop_players row in SQL — falling back to fresh shuffle");
                return null;
            }

            ArrayList<String> permutados = new ArrayList<>();

            for (String b64 : sitiosb64) {

                String nick = new String(Base64.getDecoder().decode(b64), "UTF-8");

                if (actuales.contains(nick) && preflop_players.contains(b64)) {
                    permutados.add(nick);
                    actuales.remove(nick);
                }
            }

            if (!actuales.isEmpty() && !permutados.isEmpty()) {

                HashMap<String, Object> map = this.sqlRecoverGamePositions();

                String grande = (String) map.get("bb");

                ArrayList<String> permutados_aux = new ArrayList<>();

                if (!permutados.contains(grande)) {

                    String[] asientos = this.sqlRecoverGameSeats().split("#");

                    String grande_b64 = Base64.getEncoder().encodeToString(grande.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i
                            && !permutados.contains(new String(Base64.getDecoder().decode(asientos[i]), "UTF-8"))) {

                        i = (i + 1) % asientos.length;
                    }

                    grande = new String(Base64.getDecoder().decode(asientos[i]), "UTF-8");
                }

                for (String nick : permutados) {

                    permutados_aux.add(nick);

                    if (nick.equals(grande)) {

                        // Los jugadores nuevos los colocamos después de la CIEGA GRANDE ACTUAL
                        Collections.shuffle(actuales, Helpers.CSPRNG_GENERATOR);
                        permutados_aux.addAll(actuales);
                        actuales.clear();
                    }
                }

                permutados = permutados_aux;
            }

            return permutados.isEmpty() ? actuales : permutados;

        } catch (IOException ex) {
            Logger.getLogger(Crupier.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private void sqlUpdateGameDoubleBlinds() {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE game SET blinds_time_type=?, blinds_time=? WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, GameFrame.CIEGAS_DOUBLE_TYPE);
                statement.setInt(2, GameFrame.CIEGAS_DOUBLE);
                statement.setInt(3, this.sqlite_id_game);
                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    private void sqlUpdateGameLastDeck(String deck) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE game SET last_deck=? WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, deck);
                statement.setInt(2, this.sqlite_id_game);
                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private String sqlRecoverGameSeats() {
        synchronized (GameFrame.SQL_LOCK) {

            String ret = null;

            String sql = "SELECT players from game WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_game);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getString("players");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }
    }

    private void sqlUpdateGameSeats(String players) {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "UPDATE game SET players=? WHERE id=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, players);

                statement.setInt(2, this.sqlite_id_game);

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }
    }

    private String sqlRecoverHandActions() {
        synchronized (GameFrame.SQL_LOCK) {
            String ret = null;
            String sql = "SELECT player, action, round(bet,2) as bet FROM action WHERE action.id_hand=?";
            String actions = null;
            try (java.sql.PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                java.sql.ResultSet rs = statement.executeQuery();
                actions = "";
                while (rs.next()) {
                    actions += java.util.Base64.getEncoder().encodeToString(rs.getString("player").getBytes("UTF-8")) + "#"
                            + String.valueOf(rs.getInt("action")) + "#"
                            + String.valueOf(rs.getFloat("bet")) + "@";
                }
                ret = actions;
            } catch (java.sql.SQLException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            } catch (java.io.UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, actions);
            return ret;
        }
    }

    public HashMap<String, Object> sqlRecoverServerLocalGameKeyData(boolean include_balance) {

        synchronized (GameFrame.SQL_LOCK) {

            HashMap<String, Object> map = null;

            try {

                String sql = "select hand.id as hand_id, hand.end as hand_end, hand.preflop_players as preflop_players, server, game.start, buyin, rebuy, play_time, (SELECT count(hand.id) from hand where hand.id_game=?) as conta_mano, round(hand.sbval,2) as sbval, round((hand.sbval*2),2) as bbval, blinds_time, blinds_time_type, hand.blinds_double as blinds_double, hand.dealer as dealer, hand.sb as sb, hand.bb as bb from game,hand where hand.id=(SELECT max(hand.id) from hand,game where hand.id_game=game.id and hand.id_game=?) and game.id=hand.id_game and hand.id_game=?";

                PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_game);

                statement.setInt(2, this.sqlite_id_game);

                statement.setInt(3, this.sqlite_id_game);

                ResultSet rs = statement.executeQuery();

                rs.next();

                map = new HashMap<>();
                map.put("start", rs.getLong("start"));
                map.put("hand_id", rs.getInt("hand_id"));
                map.put("hand_end", rs.getLong("hand_end"));
                map.put("server", rs.getString("server"));
                map.put("preflop_players", rs.getString("preflop_players"));
                map.put("buyin", rs.getInt("buyin"));
                map.put("rebuy", rs.getBoolean("rebuy"));
                map.put("play_time", rs.getLong("play_time"));
                map.put("conta_mano", rs.getInt("conta_mano"));
                map.put("sbval", rs.getFloat("sbval"));
                map.put("bbval", rs.getFloat("bbval"));
                map.put("blinds_time", rs.getInt("blinds_time"));
                map.put("blinds_time_type", rs.getInt("blinds_time_type"));
                map.put("blinds_double", rs.getInt("blinds_double"));
                map.put("dealer", rs.getString("dealer"));
                map.put("sb", rs.getString("sb"));
                map.put("bb", rs.getString("bb"));

                if (include_balance) {

                    // Recuperamos el balance
                    if (Files.exists(Paths.get(Init.CORONA_DIR + "/balance")) && Helpers.mostrarMensajeInformativoSINO(
                            GameFrame.getInstance(),
                            Translator.translate("ui.se_ha_encontrado_un_fichero"),
                            new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {

                        try {
                            String balance = Files.readString(Paths.get(Init.CORONA_DIR + "/balance"));

                            Files.move(Paths.get(Init.CORONA_DIR + "/balance"),
                                    Paths.get(Init.CORONA_DIR + "/balance_used"));

                            map.put("balance", balance.trim());

                            LOGGER.log(Level.WARNING, "Balance recuperado forzado");

                            LOGGER.log(Level.WARNING, balance);

                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    } else {

                        sql = "select balance.player as PLAYER, round(balance.stack,2) as STACK, balance.buyin as BUYIN, balance.rebuy_count as REBUY_COUNT from balance,hand,game where balance.id_hand=hand.id and game.id=? and hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?)";

                        statement = Helpers.getSQLITE().prepareStatement(sql);

                        statement.setQueryTimeout(30);

                        statement.setInt(1, this.sqlite_id_game);

                        statement.setInt(2, this.sqlite_id_game);

                        rs = statement.executeQuery();

                        ArrayList<String> balance = new ArrayList<>();

                        while (rs.next()) {

                            balance.add(
                                    Base64.getEncoder().encodeToString(rs.getString("PLAYER").getBytes("UTF-8")) + "|"
                                    + rs.getFloat("STACK") + "|" + rs.getInt("BUYIN") + "|" + rs.getInt("REBUY_COUNT"));
                        }

                        map.put("balance", String.join("@", balance));

                        statement.close();
                    }
                }

            } catch (SQLException | UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return map;

        }
    }

    private HashMap<String, Object> sqlRecoverGamePositions() {

        synchronized (GameFrame.SQL_LOCK) {

            HashMap<String, Object> map = null;

            String sql = "select hand.dealer as dealer, hand.sb as sb, hand.bb as bb from game,hand where hand.id=(SELECT max(hand.id) from hand,game where hand.id_game=game.id and hand.id_game=?) and game.id=hand.id_game and hand.id_game=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_game);

                statement.setInt(2, this.sqlite_id_game);

                ResultSet rs = statement.executeQuery();

                rs.next();

                map = new HashMap<>();

                map.put("dealer", rs.getString("dealer"));
                map.put("sb", rs.getString("sb"));
                map.put("bb", rs.getString("bb"));
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return map;

        }
    }

    private ArrayList<Participant> getClientHumanActiveParticipants() {

        ArrayList<Participant> humanos = new ArrayList<>();

        for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

            if (entry.getValue() != null && !entry.getValue().isCpu() && nick2player.get(entry.getKey()).isActivo()) {
                humanos.add(entry.getValue());
            }
        }

        return humanos;
    }

    private Object[] siguienteAccionLocalRecuperada(String nick) {

        Object[] res = null;

        while (!this.acciones_locales_recuperadas.isEmpty()) {
            try {
                String accion = this.acciones_locales_recuperadas.poll();

                String[] accion_partes = accion.split("#");

                String name = new String(Base64.getDecoder().decode(accion_partes[0]), "UTF-8");

                if (name.equals(nick)) {

                    res = new Object[2];

                    res[0] = Integer.parseInt(accion_partes[1]);

                    if ((int) res[0] == Player.BET) {
                        res[1] = Helpers.floatClean(Float.parseFloat(accion_partes[2]));
                    } else {
                        res[1] = 0f;
                    }

                    break;
                }

            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        if (this.acciones_locales_recuperadas.isEmpty()) {

            Helpers.GUIRun(() -> {
                if (recover_dialog != null) {
                    recover_dialog.setVisible(false);
                    recover_dialog.dispose();
                    recover_dialog = null;
                }

                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                GameFrame.getInstance().refresh();
            });

            this.setSincronizando_mano(false);

            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Audio.playWavResource("misc/startplay.wav");
            }

            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.stopLoopMp3("misc/recovering.mp3");
                Audio.playLoopMp3Resource("misc/background_music.mp3");

            }

        }

        return res;

    }

    private void recuperarAccionesLocales() {
        try {
            String datos;

            if (GameFrame.getInstance().isPartida_local()) {
                datos = sqlRecoverHandActions();
                ArrayList<String> pendientes = new ArrayList<>();
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                            && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                        pendientes.add(jugador.getNickname());
                    }
                }
                enviarAccionesRecuperadas(pendientes, datos);
            } else {
                datos = this.recibirAccionesRecuperadas();
            }

            this.tot_acciones_recuperadas = 0;
            if (datos != null && !datos.isEmpty() && !datos.equals("*")) {
                String[] rec = datos.split("@");
                for (String r : rec) {
                    if (!"".equals(r)) {
                        this.tot_acciones_recuperadas++;
                        String[] parts = r.split("#");
                        String nick = new String(Base64.getDecoder().decode(parts[0]), "UTF-8");

                        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)
                                || (GameFrame.getInstance().isPartida_local()
                                && GameFrame.getInstance().getParticipantes().containsKey(nick)
                                && GameFrame.getInstance().getParticipantes().get(nick).isCpu())) {
                            acciones_locales_recuperadas.add(r);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Defensive cleanup: dispose the recovery dragon dialog (if open) and clear the
     * sincronizando_mano flag. Idempotent — calling it when nothing is pending is a
     * no-op. Used by early-exit paths (cancelarManoYDevolverApuestas, GAME OVER) so a
     * dragon left behind by an aborted recovery does not stay on screen forever and
     * does not keep suppressing cinematics/sounds via the sincronizando_mano gate.
     */
    private void cerrarRecoverDialogYSync() {
        boolean wasSyncing = this.isSincronizando_mano();
        boolean hadDialog = (this.recover_dialog != null);

        if (hadDialog) {
            Helpers.GUIRun(() -> {
                if (recover_dialog != null) {
                    recover_dialog.setVisible(false);
                    recover_dialog.dispose();
                    recover_dialog = null;
                }
                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                GameFrame.getInstance().refresh();
            });
        }

        if (wasSyncing) {
            this.setSincronizando_mano(false);
            this.acciones_locales_recuperadas.clear();
            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.stopLoopMp3("misc/recovering.mp3");
                Audio.playLoopMp3Resource("misc/background_music.mp3");
            }
        }
    }

    private String[] sortearSitios() {

        ArrayList<String> nicks = null;

        String[] permutados = null;

        if (GameFrame.getInstance().isPartida_local()) {

            if (!GameFrame.isRECOVER() || (nicks = this.recuperarSorteoSitios()) == null) {

                nicks = new ArrayList<>();

                // Safe iteration over map keys to avoid CME during player drop
                synchronized (GameFrame.getInstance().getParticipantes()) {
                    for (String key : GameFrame.getInstance().getParticipantes().keySet()) {
                        nicks.add(key);
                    }
                }

                Collections.shuffle(nicks, Helpers.CSPRNG_GENERATOR);
            }

            // Comunicamos a todos los participantes el sorteo
            String command = "SEATS#" + String.valueOf(nicks.size());

            for (String nick : nicks) {

                try {
                    command += "#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            this.broadcastGAMECommandFromServer(command, null);

            permutados = nicks.toArray(new String[0]);

        } else {

            boolean ok;

            long start_time = System.currentTimeMillis();

            do {

                ok = false;

                synchronized (this.getReceived_commands()) {

                    ArrayList<String> rejected = new ArrayList<>();

                    while (!ok && !this.getReceived_commands().isEmpty()) {

                        String comando = this.received_commands.poll();
                        try {
                            String[] partes = comando.split("#");

                            if (partes.length >= 4 && partes[2].equals("SEATS")) {

                                int tot = Integer.valueOf(partes[3]);

                                if (partes.length < 4 + tot) {
                                    LOGGER.log(Level.WARNING, "SEATS malformed (tot={0} but len={1}): {2}",
                                            new Object[]{tot, partes.length, comando});
                                    continue;
                                }

                                ok = true;
                                permutados = new String[tot];

                                for (int i = 0; i < tot; i++) {

                                    try {
                                        permutados[i] = new String(Base64.getDecoder().decode(partes[i + 4]), "UTF-8");
                                    } catch (UnsupportedEncodingException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else if (partes.length >= 3 && partes[2].equals("SEATS")) {
                                LOGGER.log(Level.WARNING, "SEATS malformed dropped: {0}", comando);
                            } else {
                                rejected.add(comando);
                            }
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Exception while processing command in receiveSEATS: " + comando, ex);
                        }

                    }

                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                        rejected.clear();
                    }

                }

                if (!ok) {

                    if (GameFrame.getInstance().checkPause()) {
                        start_time = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                        start_time = System.currentTimeMillis();
                    } else {
                        synchronized (this.getReceived_commands()) {

                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                                Helpers.logCooperativeCancellation(LOGGER, "received commands wait", ex);
                                break;
                            }
                        }
                    }
                }

            // Guard de salida (ver nota en recibirPosiciones). Si la
            // transmisión muere el cliente abandona el wait de SEATS
            // y permutados queda null — el caller debe ser robusto a eso
            // o el flujo terminar por isFin_de_la_transmision al mirar
            // el retorno.
            } while (!ok && !isFin_de_la_transmision());

        }

        String sitios = Translator.translate("ui.sorteo_de_sitios");

        for (String nick : permutados) {

            sitios += " [" + nick + "] ";
        }

        GameFrame.getInstance().getRegistro().print(sitios);

        return permutados;
    }

    public float getCiega_grande() {
        return ciega_grande;
    }

    public float getCiega_pequeña() {
        return ciega_pequeña;
    }

    public void mostrarAnimacionDestaparCartaComunitaria(Card carta) {

        String baraja = GameFrame.BARAJA;
        boolean baraja_mod = (boolean) ((Object[]) BARAJAS.get(GameFrame.BARAJA))[1];

        if (GameFrame.ANIMACION_CARTAS && ((baraja_mod && Files.exists(Paths.get(Helpers.getCurrentJarParentPath()
                + "/mod/decks/" + baraja + "/gif/" + carta.getValor() + "_" + carta.getPalo() + ".gif")))
                || getClass().getResource("/images/decks/" + baraja + "/gif/" + carta.getValor() + "_" + carta.getPalo()
                        + ".gif") != null)) {

            long start = System.currentTimeMillis();

            try {
                URL url_icon = null;

                if (baraja_mod) {
                    url_icon = Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + baraja + "/gif/"
                            + carta.getValor() + "_" + carta.getPalo() + ".gif").toUri().toURL();
                } else {
                    url_icon = getClass().getResource(
                            "/images/decks/" + baraja + "/gif/" + carta.getValor() + "_" + carta.getPalo() + ".gif");
                }

                ImageIcon icon = new ImageIcon(url_icon);

                if (GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL) {

                    ImageIcon icon_gifsicle = Helpers.genGifsicleCardAnimation(url_icon,
                            (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP),
                            baraja + "_" + carta.getValor() + "_" + carta.getPalo());

                    if (icon_gifsicle != null) {
                        icon = icon_gifsicle;
                    } else {
                        int w = icon.getIconWidth();
                        int h = icon.getIconHeight();
                        icon = new ImageIcon(icon.getImage().getScaledInstance(
                                Math.round(w * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)),
                                Math.round(h * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)),
                                Image.SCALE_DEFAULT));
                    }
                }

                long lapsed = System.currentTimeMillis() - start;

                Helpers.pausar(
                        (carta == GameFrame.getInstance().getFlop2() || carta == GameFrame.getInstance().getFlop3()) ? 0
                        : (this.destapar_resistencia ? PAUSA_DESTAPAR_CARTA_ALLIN - lapsed
                                : PAUSA_DESTAPAR_CARTA - lapsed));

                final ImageIcon ficon = icon;

                Helpers.GUIRunAndWait(() -> {
                    int x = (int) ((int) ((carta.getLocationOnScreen().getX() + Math.round(carta.getWidth() / 2))
                            - Math.round(ficon.getIconWidth() / 2))
                            - GameFrame.getInstance().getTapete().getLocationOnScreen().getX());

                    int y = (int) ((int) ((carta.getLocationOnScreen().getY() + Math.round(carta.getHeight() / 2))
                            - Math.round(ficon.getIconHeight() / 2))
                            - GameFrame.getInstance().getTapete().getLocationOnScreen().getY());

                    GameFrame.getInstance().getTapete().getCentral_label().setLocation(x, y);

                    carta.setVisibleCard(false);
                });

                GameFrame.getInstance().getTapete().showCentralImage(ficon, 0, CARD_ANIMATION_DELAY, false,
                        "misc/uncover.wav", 1, -1);

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                // Even if the animation crashes, we MUST logically flip the card 
                // otherwise Hand evaluation crashes later.
                carta.destapar(false);
            }

        } else {
            Helpers.pausar(this.destapar_resistencia ? PAUSA_DESTAPAR_CARTA_ALLIN : PAUSA_DESTAPAR_CARTA);
            carta.destapar();
        }

        carta.checkSpecialCardSound();
        GameFrame.getInstance().checkPause();
    }

    public void destaparFlop(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop1());
        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop2());
        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getFlop3());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop1().getCartaComoEntero()));
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop2().getCartaComoEntero()));
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getFlop3().getCartaComoEntero()));
        }

        ArrayList<Card> flop = new ArrayList<>();
        flop.add(GameFrame.getInstance().getFlop1());
        flop.add(GameFrame.getInstance().getFlop2());
        flop.add(GameFrame.getInstance().getFlop3());

        GameFrame.getInstance().getRegistro().print("FLOP -> " + Card.collection2String(flop));

        checkJugadasParciales(resisten);
    }

    public void destaparTurn(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getTurn());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getTurn().getCartaComoEntero()));
        }

        ArrayList<Card> com = new ArrayList<>();
        com.add(GameFrame.getInstance().getFlop1());
        com.add(GameFrame.getInstance().getFlop2());
        com.add(GameFrame.getInstance().getFlop3());
        com.add(GameFrame.getInstance().getTurn());

        GameFrame.getInstance().getRegistro().print("TURN -> " + Card.collection2String(com));

        checkJugadasParciales(resisten);
    }

    public void destaparRiver(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getRiver());

        if (GameFrame.getInstance().isPartida_local()) {
            Bot.BOT_COMMUNITY_CARDS.addCard(Bot.coronaIntegerCard2LokiCard(GameFrame.getInstance().getRiver().getCartaComoEntero()));
        }

        ArrayList<Card> com = new ArrayList<>();
        com.add(GameFrame.getInstance().getFlop1());
        com.add(GameFrame.getInstance().getFlop2());
        com.add(GameFrame.getInstance().getFlop3());
        com.add(GameFrame.getInstance().getTurn());
        com.add(GameFrame.getInstance().getRiver());

        GameFrame.getInstance().getRegistro().print("RIVER -> " + Card.collection2String(com));
    }

    private void recibirCartasResistencia(ArrayList<Player> resistencia) {
        HashMap<String, String[]> cards = new HashMap<>();
        boolean ok = false;
        long start_time = System.currentTimeMillis();

        // Determine if this client is a spectator/warming up without a valid crypto ring
        boolean iAmCalentando = GameFrame.getInstance().getLocalPlayer().isCalentando()
                || GameFrame.getInstance().getLocalPlayer().isSpectator();

        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#", -1);

                    if (partes.length >= 3) {
                        switch (partes[2]) {
                            case "POTCARDS":
                                int total = (int) ((float) (partes.length - 3) / 3);
                                ok = true;
                                for (int i = 0; i < total; i++) {
                                    try {
                                        String nick = new String(Base64.getDecoder().decode(partes[3 + 3 * i]), "UTF-8");
                                        cards.put(nick, new String[]{partes[4 + 3 * i], partes[5 + 3 * i]});
                                    } catch (UnsupportedEncodingException ex) {
                                    }
                                }

                                for (Player jugador : resistencia) {
                                    if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {
                                        String[] suscartas = cards.get(jugador.getNickname());
                                        if (suscartas != null) {
                                            // BLINDAJE: Solo aplicamos el texto plano de POTCARDS si somos espectadores 
                                            // o si la carta sigue inexplicablemente tapada (por fallo de desencriptación previa)
                                            if (iAmCalentando || jugador.getHoleCard1().isTapada()) {
                                                String c1_str = suscartas[0];
                                                String c2_str = suscartas[1];

                                                if (c1_str != null && c1_str.length() >= 3 && c1_str.contains("_")
                                                        && c2_str != null && c2_str.length() >= 3 && c2_str.contains("_")) {
                                                    String[] carta1 = c1_str.split("_");
                                                    String[] carta2 = c2_str.split("_");
                                                    jugador.getHoleCard1().actualizarValorPalo(carta1[0], carta1[1]);
                                                    jugador.getHoleCard2().actualizarValorPalo(carta2[0], carta2[1]);
                                                }
                                            }
                                        }
                                    }
                                }
                                break;

                            case "REQ_VISUAL_CARDS":
                                // Respond immediately with our cards for the TV broadcast (non-blocking)
                                Helpers.threadRun(() -> {
                                    try {
                                        String myNickB64 = java.util.Base64.getEncoder().encodeToString(GameFrame.getInstance().getNick_local().getBytes("UTF-8"));
                                        String c1 = GameFrame.getInstance().getLocalPlayer().getHoleCard1().toShortString();
                                        String c2 = GameFrame.getInstance().getLocalPlayer().getHoleCard2().toShortString();
                                        sendGAMECommandToServer("VISUAL_CARDS_RESP#" + myNickB64 + "#" + c1 + "#" + c2, false);
                                    } catch (Exception e) {
                                    }
                                });
                                break;

                            case "VISUAL_UPDATE":
                                // Receive visual cards from another client and update UI instantly
                                if (partes.length >= 6) {
                                    try {
                                        String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                        Player p = nick2player.get(nick);
                                        if (p != null && !p.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                                            String[] c1 = partes[4].split("_");
                                            String[] c2 = partes[5].split("_");
                                            p.getHoleCard1().actualizarValorPalo(c1[0], c1[1]);
                                            p.getHoleCard2().actualizarValorPalo(c2[0], c2[1]);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                                break;

                            case "MISDEAL":
                                String motivo = "";
                                try {
                                    motivo = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                } catch (Exception e) {
                                }
                                cancelarManoYDevolverApuestas(motivo, false);
                                return;

                            default:
                                rejected.add(comando);
                                break;
                        }
                    } else {
                        rejected.add(comando);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                }
            }

            if (!ok) {
                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!ok && !isFin_de_la_transmision());
    }

    public void procesarCartasResistencia(ArrayList<Player> resisten, boolean destapar) {

        if (!this.cartas_resistencia) {

            if (GameFrame.getInstance().isPartida_local()) {

                // --- NUEVO: OPTIMISTIC UI - Pedimos las cartas a los clientes remotos ---
                solicitarYRecibirCartasVisuales(resisten);
                // ------------------------------------------------------------------------

                // Enviamos a cada jugador las cartas de los jugadores que han llegado al final
                // de todas las rondas de apuestas
                String comando = "POTCARDS";

                for (Player jugador : resisten) {
                    if (!jugador.isExit()) {
                        try {
                            comando += "#" + Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                    + "#"
                                    + jugador.getHoleCard1().toShortString() + "#"
                                    + jugador.getHoleCard2().toShortString();
                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }

                broadcastGAMECommandFromServer(comando, null);

                if (destapar) {
                    Audio.playWavResource("misc/uncover.wav", false);

                    // Destapamos las cartas de los jugadores involucrados
                    for (Player jugador : resisten) {

                        if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit()) {

                            jugador.destaparCartas(false);
                        }
                    }
                }

                this.cartas_resistencia = true;

            } else {

                // Recibimos las cartas de los jugadores involucrados en el bote_total
                // (ignoramos las nuestras que ya las sabemos)
                recibirCartasResistencia(resisten);

                if (destapar) {
                    Audio.playWavResource("misc/uncover.wav", false);

                    // Destapamos las cartas de los jugadores involucrados
                    for (Player jugador : resisten) {

                        if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit()) {

                            jugador.destaparCartas(false);
                        }
                    }
                }

                this.cartas_resistencia = true;
            }
        }
    }

    private synchronized void exitSpectatorBots() {

        if (GameFrame.getInstance().isPartida_local()) {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit() && jugador.isSpectator()
                        && Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0
                        && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                    this.remotePlayerQuit(jugador.getNickname());

                }
            }
        }
    }

    private synchronized void updateExitPlayers() {
        int exit = 0;
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isExit()) {
                this.auditor.put(jugador.getNickname(), new Float[]{jugador.getStack() + jugador.getPagar(), (float) jugador.getBuyin()});
                float ganancia = Helpers.floatClean(Helpers.floatClean(jugador.getStack()) + Helpers.floatClean(jugador.getPagar())) - Helpers.floatClean(jugador.getBuyin());
                String ganancia_msg = "";
                if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                    ganancia_msg += Translator.translate("ui.pierde_2") + Helpers.float2String(ganancia * -1f);
                } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                    ganancia_msg += Translator.translate("ui.gana_4") + Helpers.float2String(ganancia);
                } else {
                    ganancia_msg += Translator.translate("ui.ni_gana_ni_pierde");
                }
                GameFrame.getInstance().getRegistro().print(jugador.getNickname() + " " + Translator.translate("game.abandona_la_timba_2") + " -> " + ganancia_msg);
                exit++;
            }
        }

        if (exit > 0) {
            GameFrame.getInstance().downgradeAndRefreshTapete();
            nick2player.clear();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                nick2player.put(jugador.getNickname(), jugador);
                //
                if (jugador.isExit() && GameFrame.getInstance().isPartida_local()) {
                    GameFrame.getInstance().getSala_espera().borrarParticipante(jugador.getNickname());
                }
            }
        }
    }

    public boolean ganaPorUltimaCarta(Player jugador, Hand jugada, int MIN) {

        if (!GameFrame.getInstance().getRiver().isTapada()
                && jugada.getWinners().contains(GameFrame.getInstance().getRiver()) && jugada.getValue() >= MIN
                && (jugada.getWinners().contains(jugador.getHoleCard1())
                || jugada.getWinners().contains(jugador.getHoleCard2()))) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));
            cartas.add(jugador.getHoleCard1());
            cartas.add(jugador.getHoleCard2());
            cartas.remove(GameFrame.getInstance().getRiver());

            Hand nueva_jugada = new Hand(cartas);

            return (nueva_jugada.getValue() != jugada.getValue());
        }

        return false;
    }

    public boolean badbeat(Player perdedor, Player ganador) {

        if (ganador != null) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));

            cartas.add(perdedor.getHoleCard1());

            cartas.add(perdedor.getHoleCard2());

            cartas.remove(GameFrame.getInstance().getRiver());

            Hand jugada_perdedor_turn = new Hand(cartas);

            cartas.remove(perdedor.getHoleCard1());

            cartas.remove(perdedor.getHoleCard2());

            cartas.add(ganador.getHoleCard1());

            cartas.add(ganador.getHoleCard2());

            Hand jugada_ganador_turn = new Hand(cartas);

            return (jugada_perdedor_turn.getValue() >= Hand.TRIO
                    && (jugada_perdedor_turn.getValue() > jugada_ganador_turn.getValue()));
        } else {
            return false;
        }

    }

    public void pausaConBarra(int tiempo) {

        this.setTiempo_pausa(tiempo);

        while (getTiempoPausa() > 0) {

            synchronized (lock_pausa_barra) {
                try {
                    lock_pausa_barra.wait(1000);

                    if (!GameFrame.getInstance().isTimba_pausada() && !isFin_de_la_transmision() && !isIwtsthing()) {

                        tiempo_pausa--;

                        int val = tiempo_pausa;

                        Helpers.GUIRun(() -> {
                            GameFrame.getInstance().getBarra_tiempo().setValue(val);
                        });
                    }

                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "pause progress bar loop", ex);
                    break;
                }
            }
        }

        synchronized (lock_iwtsth) {
            lock_iwtsth.notifyAll();
        }
    }

    public void showdown(HashMap<Player, Hand> perdedores, HashMap<Player, Hand> ganadores) {
        int pivote;

        // 1. Determinar quién es el primero en enseñar (último agresor o el siguiente al dealer)
        if (this.last_aggressor != null) {
            pivote = 0;
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador == this.last_aggressor) {
                    break;
                }
                pivote++;
            }
        } else {
            int i = 0;
            while (!GameFrame.getInstance().getJugadores().get(i).getNickname().equals(this.dealer_nick)) {
                i++;
            }
            pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();
        }

        int pos = pivote;
        boolean first_to_show = true; // Control para obligar a mostrar al primero en hablar

        do {
            Player jugador_actual = GameFrame.getInstance().getJugadores().get(pos);

            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                boolean isLocal = jugador_actual.equals(GameFrame.getInstance().getLocalPlayer());
                boolean isWinner = ganadores.containsKey(jugador_actual);
                Hand jugada = isWinner ? ganadores.get(jugador_actual) : perdedores.get(jugador_actual);

                // LÓGICA AUTO-MUCK (IWTSTH):
                // Se destapa la carta SÓLO si:
                // - IWTSTH está desactivado (todos muestran)
                // - Es un All-in (destapar_resistencia = true)
                // - El jugador es uno de los ganadores del bote
                // - Es el primer jugador en actuar en el showdown (pivote)
                boolean mustShow = !GameFrame.IWTSTH_RULE || 
                                   this.destapar_resistencia || 
                                   isWinner || 
                                   first_to_show;

                if (mustShow) {
                    jugador_actual.destaparCartas(false);
                }

                // A partir de ahora, los siguientes perdedores podrán ocultar sus cartas
                first_to_show = false;

                if (isWinner) {
                    jugador_actual.setWinner(jugada.getName());
                    this.sqlNewShowdown(jugador_actual, jugada, true, !mustShow);

                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        if (jugador_actual.getDecision() == Player.ALLIN) {
                            Audio.playWavResource("joke/" + GameFrame.LANGUAGE + "/winner/applause.wav");
                        } else {
                            this.soundWinner(jugada.getValue(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                        }
                    }

                } else {
                    // Actualización de UI para perdedores
                    if (isLocal) {
                        jugador_actual.setLoser(jugada.getName());
                        GameFrame.getInstance().getLocalPlayer().setMuestra(mustShow);
                        
                        // Si el jugador local hizo "muck" (cartas tapadas), habilitamos el botón voluntario
                        if (!mustShow) {
                            GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(true);
                        }
                    } else {
                        // destaparCartas() above is async (Helpers.GUIRun), so we cannot
                        // read getHoleCard1().isTapada() here to decide the label. Use the
                        // mustShow flag that drove the uncover decision: a remote player
                        // that did NOT show keeps the generic "PIERDE" label; otherwise
                        // we expose the hand name.
                        if (!mustShow) {
                            jugador_actual.setLoser(Translator.translate("ui.pierde_3"));
                        } else {
                            jugador_actual.setLoser(jugada.getName());
                        }
                    }

                    this.sqlNewShowdown(jugador_actual, jugada, false, !mustShow);

                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        if (jugador_actual.getDecision() == Player.ALLIN && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                            java.util.Map.Entry<String, String[]> WTF_SOUNDS = new java.util.HashMap.SimpleEntry<>("joke/es/loser/", new String[]{
                                "encargado.wav",
                                "matias.wav"});
                            Audio.playRandomWavResource(java.util.Map.ofEntries(WTF_SOUNDS));
                        } else {
                            this.soundLoser(jugada.getValue());
                        }
                    }
                }
            }

            // Registro de historial para simular "Tilt" en los bots
            if (GameFrame.getInstance().isPartida_local()
                    && jugador_actual != GameFrame.getInstance().getLocalPlayer()
                    && jugador_actual instanceof RemotePlayer) {
                Bot bot = ((RemotePlayer) jugador_actual).getBot();
                if (bot != null) {
                    bot.recordHandResult(ganadores.containsKey(jugador_actual));
                }
            }

            pos = (pos + 1) % GameFrame.getInstance().getJugadores().size();

        } while (pos != pivote);
    }

    public void startIWTSTHPlayersBlinking() {

        if (GameFrame.IWTSTH_RULE && IWTSTH_BLINKING && isIWTSTH4LocalPlayerAuthorized()) {

            Helpers.GUIRun(() -> {
                for (RemotePlayer rp : GameFrame.getInstance().getTapete().getRemotePlayers()) {
                    if (rp.isActivo() && rp.isLoser() && rp.getHoleCard1().isTapada()) {
                        rp.getIwtsth_blink_timer().start();
                    }
                }
            });

        }
    }

    public Integer sqlUGI2GID(String ugi) {
        synchronized (GameFrame.SQL_LOCK) {

            Integer ret = null;

            String sql = "SELECT id from game WHERE ugi=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, ugi);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getInt("id");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }

    }

    public Integer getHandIdFromUGI(String ugi) {

        synchronized (GameFrame.SQL_LOCK) {

            Integer ret = null;

            String sql = "SELECT max(hand.id) as hand_id from game,hand WHERE game.ugi=? AND hand.id_game=game.id";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, ugi);

                ResultSet rs = statement.executeQuery();

                rs.next();

                ret = rs.getInt("hand_id");
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            return ret;

        }

    }

    public String getUGI() {
        synchronized (GameFrame.SQL_LOCK) {
            if (GameFrame.isRECOVER()) {
                String ret = null;

                String sql = "SELECT ugi from game WHERE id=?";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);

                    statement.setInt(1, GameFrame.RECOVER_ID);

                    ResultSet rs = statement.executeQuery();

                    rs.next();

                    ret = rs.getString("ugi");
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

                return ret;
            } else {
                return Helpers.genRandomString(GameFrame.UGI_LENGTH);
            }
        }
    }

    private void resyncRECOVERGLOBALS() {
        if (Boolean.TRUE.equals(GameFrame.IWTSTH_RULE_RECOVER)) {
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getIwtsth_rule_menu().setSelected(false);
                GameFrame.getInstance().getIwtsth_rule_menu().doClick();
                GameFrame.IWTSTH_RULE_RECOVER = null;
            });

        }

        if (GameFrame.RABBIT_HUNTING_RECOVER != null && GameFrame.RABBIT_HUNTING_RECOVER != 0) {
            Helpers.GUIRun(() -> {
                switch (GameFrame.RABBIT_HUNTING_RECOVER) {
                    case 1:
                        GameFrame.getInstance().getMenu_rabbit_free().setSelected(false);
                        GameFrame.getInstance().getMenu_rabbit_free().doClick();
                        break;
                    case 2:
                        GameFrame.getInstance().getMenu_rabbit_sb().setSelected(false);
                        GameFrame.getInstance().getMenu_rabbit_sb().doClick();
                        break;
                    case 3:
                        GameFrame.getInstance().getMenu_rabbit_bb().setSelected(false);
                        GameFrame.getInstance().getMenu_rabbit_bb().doClick();
                        break;

                }
                GameFrame.RABBIT_HUNTING_RECOVER = null;
            });

        }
    }

    @Override
    public void run() {
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

        if (GameFrame.getInstance().isPartida_local()) {
            GameFrame.UGI = this.getUGI();
            broadcastGAMECommandFromServer("INIT#" + String.valueOf(GameFrame.BUYIN) + "#" + String.valueOf(GameFrame.CIEGA_PEQUEÑA) + "#" + String.valueOf(GameFrame.CIEGA_GRANDE) + "#" + String.valueOf(GameFrame.CIEGAS_DOUBLE) + "@" + String.valueOf(GameFrame.CIEGAS_DOUBLE_TYPE) + "#" + String.valueOf(GameFrame.isRECOVER()) + "@" + GameFrame.UGI + "#" + String.valueOf(GameFrame.REBUY) + "#" + String.valueOf(GameFrame.MANOS) + "#" + String.valueOf(GameFrame.BLIND_CAP) + "#" + String.valueOf(GameFrame.REBUY_LIMIT) + "#" + String.valueOf(GameFrame.BOT_REBUY), null);
        }

        if (GameFrame.RECOVER) {
            if (GameFrame.getInstance().isPartida_local()) {
                this.sqlite_id_game = GameFrame.RECOVER_ID;
                GameFrame.persistRecoverSettings(this.sqlite_id_game);
            } else {
                Integer gid = sqlUGI2GID(GameFrame.UGI);
                if (gid == null) {
                    this.sqlNewGame();
                } else {
                    this.sqlite_id_game = gid;
                }
            }
        }

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("ui.sorteando_sitios"));
        });

        this.nicks_permutados = sortearSitios();
        sentarParticipantes();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            nick2player.put(jugador.getNickname(), jugador);
        }

        if (!GameFrame.RECOVER) {
            sqlNewGame();
        }

        Helpers.GUIRunAndWait(() -> {
            GameFrame.getInstance().getSala_espera().setVisible(false);
        });

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("game.timba_en_curso"));
            GameFrame.getInstance().getSala_espera().getTts_warning().setVisible(true);
            GameFrame.getInstance().getSala_espera().getChat_notifications().setVisible(true);
            GameFrame.getInstance().getSala_espera().getBarra().setVisible(false);
            GameFrame.getInstance().getSala_espera().getStatus().setIcon(null);
            GameFrame.getInstance().getSala_espera().pack();
        });

        Audio.stopLoopMp3("misc/waiting_room.mp3");

        if (!GameFrame.RECOVER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
            Audio.playWavResource("misc/startplay.wav");
        }

        if (GameFrame.MUSICA_AMBIENTAL) {
            Audio.unmuteLoopMp3("misc/background_music.mp3");
        }

        GameFrame.getInstance().autoZoomFullScreen(GameFrame.AUTO_FULLSCREEN);

        while (!fin_de_la_transmision) {
            try {
                if ((getJugadoresActivos() + getJugadoresCalentando()) > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {
                    if (this.NUEVA_MANO()) {
                        auditorCuentas();
                        GameFrame.getInstance().getRegistro().print(this.big_blind_nick + " " + Translator.translate("blinds.es_la_ciega_grande") + Helpers.float2String(this.ciega_grande) + ") / " + this.small_blind_nick + " " + Translator.translate("blinds.es_la_ciega_pequena") + Helpers.float2String(this.ciega_pequeña) + ") / " + this.dealer_nick + " " + Translator.translate("ui.es_el_dealer"));

                        ArrayList<Player> resisten = this.rondaApuestas(PREFLOP, new ArrayList<>(GameFrame.getInstance().getJugadores()));

                        GameFrame.getInstance().hideTapeteApuestas();
                        GameFrame.getInstance().getLocalPlayer().desactivarControles();

                        if (GameFrame.AUTO_ACTION_BUTTONS) {
                            GameFrame.getInstance().getLocalPlayer().desActivarPreBotones();
                        }

                        // The dragon must close after the preflop replay even
                        // when the local queue was empty from the start. That happens to
                        // a CLIENT who reconnects during a recovery where they were not
                        // part of the interrupted hand's crypto-ring (CALENTANDO): the
                        // queue filter at line 6611 leaves it empty, current_player ==
                        // localPlayer never fires for them in this round, so the path #3
                        // close inside siguienteAccionLocalRecuperada is never invoked.
                        // Guard on the sync flag instead of on the queue contents so the
                        // dragon closes here as a final safety net.
                        if (this.isSincronizando_mano()) {
                            this.acciones_locales_recuperadas.clear();
                            Helpers.GUIRun(() -> {
                                if (recover_dialog != null) {
                                    recover_dialog.setVisible(false);
                                    recover_dialog.dispose();
                                    recover_dialog = null;
                                }
                                GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                GameFrame.getInstance().refresh();
                            });
                            this.setSincronizando_mano(false);
                            GameFrame.getInstance().getRegistro().print(Translator.translate("game.timba_recuperada"));

                            if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                                Audio.playWavResource("misc/startplay.wav");
                            }

                            if (GameFrame.MUSICA_AMBIENTAL) {
                                Audio.stopLoopMp3("misc/recovering.mp3");
                                Audio.playLoopMp3Resource("misc/background_music.mp3");
                            }
                        }

                        setShowTime(true);

                        synchronized (lock_mostrar) {
                            lock_mostrar.notifyAll();
                        }

                        HashMap<Player, Hand> jugadas;
                        HashMap<Player, Hand> ganadores;

                        synchronized (getLock_contabilidad()) {
                            java.util.Iterator<Player> iterator = resisten.iterator();
                            while (iterator.hasNext()) {
                                Player jugador = iterator.next();
                                if (jugador.isExit()) {
                                    iterator.remove();
                                }
                            }

                            this.bote.genSidePots();
                            badbeat = false;
                            float sql_bote_total = this.bote_total;

                            switch (resisten.size()) {
                                case 0:
                                    // Math yes, GUI no.
                                    requestShowdownKeys(new ArrayList<Player>());
                                    procesarCartasResistencia(new ArrayList<Player>(), false);

                                    GameFrame.getInstance().getRegistro().print("-----" + Translator.translate("game.gana_bote") + " " + Helpers.float2String(this.bote.getTotal() + this.bote_sobrante) + " " + Translator.translate("action.sin_tener_que_mostrar"));
                                    Helpers.GUIRun(() -> {
                                        setPotBackground(Color.RED);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                    });
                                    GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, 0f);
                                    if (Helpers.float1DSecureCompare(0f, this.bote_total) < 0) {
                                        this.bote_sobrante += this.bote_total;
                                    }
                                    ganadores = new HashMap<>();
                                    for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                        carta.desenfocar();
                                    }
                                    break;
                                case 1:
                                    requestShowdownKeys(new ArrayList<Player>());
                                    procesarCartasResistencia(new ArrayList<Player>(), false);

                                    resisten.get(0).setWinner(resisten.contains(GameFrame.getInstance().getLocalPlayer()) ? Translator.translate("ui.ganas_3") : Translator.translate("ui.gana_3"));
                                    if (resisten.get(0) != GameFrame.getInstance().getLocalPlayer()) {
                                        resisten.get(0).getHoleCard1().desenfocar();
                                        resisten.get(0).getHoleCard2().desenfocar();
                                    }
                                    resisten.get(0).pagar(this.bote.getTotal() + this.bote_sobrante, null);
                                    this.beneficio_bote_principal = this.bote.getTotal() + this.bote_sobrante - this.bote.getBet();
                                    GameFrame.getInstance().getRegistro().print(resisten.get(0).getNickname() + " " + Translator.translate("game.gana_bote") + Helpers.float2String(this.bote.getTotal() + this.bote_sobrante) + Translator.translate("action.sin_tener_que_mostrar"));
                                    Helpers.GUIRun(() -> {
                                        setPotBackground(Color.GREEN);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                    });
                                    GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, this.beneficio_bote_principal);
                                    this.bote_total = 0f;
                                    this.bote_sobrante = 0f;
                                    for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                        carta.desenfocar();
                                    }
                                    if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {
                                        GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                                    }
                                    if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {
                                        this.soundWinner(0, false);
                                    }
                                    ganadores = new HashMap<>();
                                    ganadores.put(resisten.get(0), null);
                                    this.sqlNewShowdown(resisten.get(0), null, true, true);
                                    break;
                                default:
                                    // Everyone shows their cards and GUI updates
                                    requestShowdownKeys(resisten);
                                    procesarCartasResistencia(resisten, false);
                                    if (!this.destapar_resistencia) {
                                        Helpers.pausar(Crupier.PAUSA_ANTES_DE_SHOWDOWN * 1000);
                                    }

                                    if (this.bote.getSidePot() == null) {
                                        jugadas = this.calcularJugadas(resisten);
                                        ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                        float[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal() + this.bote_sobrante, ganadores.size());
                                        this.beneficio_bote_principal = cantidad_pagar_ganador[0] - this.bote.getBet();
                                        ArrayList<Card> cartas_usadas_jugadas = new ArrayList<>();
                                        Player unganador = null;

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                            Player ganador = entry.getKey();
                                            Hand jugada = entry.getValue();
                                            ArrayList<Card> cartas = ganadores.size() == 1 ? jugada.getWinners() : jugada.getMano();
                                            for (Card carta : cartas) {
                                                if (!cartas_usadas_jugadas.contains(carta)) {
                                                    cartas_usadas_jugadas.add(carta);
                                                }
                                            }
                                            if (!cartas.contains(ganador.getHoleCard1())) {
                                                ganador.getHoleCard1().desenfocar();
                                            }
                                            if (!cartas.contains(ganador.getHoleCard2())) {
                                                ganador.getHoleCard2().desenfocar();
                                            }
                                            jugadas.remove(ganador);
                                            ganador.pagar(cantidad_pagar_ganador[0], null);
                                            this.bote_total -= cantidad_pagar_ganador[0];
                                            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_2") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                            unganador = ganador;
                                            jugada_ganadora = jugada.getValue();
                                        }

                                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!cartas_usadas_jugadas.contains(carta)) {
                                                carta.desenfocar();
                                            }
                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                            Player perdedor = entry.getKey();
                                            badbeat = badbeat(perdedor, unganador);
                                            perdedores.put(perdedor, entry.getValue());
                                            GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                        this.showdown(jugadas, ganadores);
                                        Helpers.GUIRun(() -> {
                                            setPotBackground(Color.GREEN);
                                            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                        });
                                        GameFrame.getInstance().setTapeteBote(cantidad_pagar_ganador[0], this.beneficio_bote_principal);

                                    } else {
                                        jugadas = this.calcularJugadas(resisten);
                                        ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                        float[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal() + this.bote_sobrante, ganadores.size());
                                        this.beneficio_bote_principal = cantidad_pagar_ganador[0] - this.bote.getBet();
                                        String bote_tapete = "#1{" + Helpers.float2String(this.bote.getTotal()) + "}";
                                        ArrayList<Card> cartas_usadas_jugadas = new ArrayList<>();
                                        Player unganador = null;

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                            Player ganador = entry.getKey();
                                            Hand jugada = entry.getValue();
                                            ArrayList<Card> cartas = ganadores.size() == 1 ? jugada.getWinners() : jugada.getMano();
                                            for (Card carta : cartas) {
                                                if (!cartas_usadas_jugadas.contains(carta)) {
                                                    cartas_usadas_jugadas.add(carta);
                                                }
                                            }
                                            if (!cartas.contains(ganador.getHoleCard1())) {
                                                ganador.getHoleCard1().desenfocar();
                                            }
                                            if (!cartas.contains(ganador.getHoleCard2())) {
                                                ganador.getHoleCard2().desenfocar();
                                            }
                                            jugadas.remove(entry.getKey());
                                            ganador.pagar(cantidad_pagar_ganador[0], 1);
                                            this.bote_total -= cantidad_pagar_ganador[0];
                                            jugada = entry.getValue();
                                            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + Translator.translate("game.gana_bote_principal") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                            unganador = ganador;
                                            jugada_ganadora = jugada.getValue();
                                        }

                                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!cartas_usadas_jugadas.contains(carta)) {
                                                carta.desenfocar();
                                            }
                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                            Player perdedor = entry.getKey();
                                            badbeat = badbeat(perdedor, unganador);
                                            perdedores.put(perdedor, entry.getValue());
                                            GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote_principal") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                        this.showdown(jugadas, ganadores);

                                        HandPot current_pot = this.bote.getSidePot();
                                        int conta_bote_secundario = 2;

                                        while (current_pot != null) {
                                            if (current_pot.getPlayers().size() == 1) {
                                                bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.float2String(current_pot.getTotal()) + "}";
                                                current_pot.getPlayers().get(0).pagar(current_pot.getTotal(), conta_bote_secundario);
                                                this.bote_total -= current_pot.getTotal();
                                                GameFrame.getInstance().getRegistro().print(current_pot.getPlayers().get(0).getNickname() + " " + Translator.translate("game.recupera_bote_sobrante_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(current_pot.getTotal()) + ")");
                                                this.sqlUpdateShowdownPay(current_pot.getPlayers().get(0));
                                            } else {
                                                jugadas = this.calcularJugadas(current_pot.getPlayers());
                                                ganadores = this.calcularGanadores(new HashMap<>(jugadas));
                                                cantidad_pagar_ganador = this.calcularBoteParaGanador(current_pot.getTotal(), ganadores.size());
                                                bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.float2String(current_pot.getTotal()) + "}";
                                                for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {
                                                    Player ganador = entry.getKey();
                                                    jugadas.remove(entry.getKey());
                                                    ganador.pagar(cantidad_pagar_ganador[0], conta_bote_secundario);
                                                    this.bote_total -= cantidad_pagar_ganador[0];
                                                    Hand jugada = entry.getValue();
                                                    GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(ganador.getHoleCards()) + " " + Translator.translate("game.gana_bote_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);
                                                    this.sqlUpdateShowdownPay(ganador);
                                                }
                                                for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {
                                                    Player perdedor = entry.getKey();
                                                    perdedores.put(perdedor, entry.getValue());
                                                    GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + " " + Translator.translate("game.pierde_bote_secundario") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                                }
                                            }
                                            current_pot = current_pot.getSidePot();
                                            conta_bote_secundario++;
                                        }
                                        Helpers.GUIRun(() -> {
                                            setPotBackground(Color.BLACK);
                                            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                        });
                                        GameFrame.getInstance().setTapeteBote(bote_tapete);
                                    }
                                    this.bote_sobrante = this.bote_total;
                                    break;
                            }

                            Helpers.GUIRun(() -> {
                                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setHorizontalAlignment(JLabel.CENTER);
                            });

                            this.bote_total = 0f;

                            if (!GameFrame.TEST_MODE && !resisten.contains(GameFrame.getInstance().getLocalPlayer())) {
                                if (GameFrame.getInstance().getLocalPlayer().isActivo() && GameFrame.getInstance().getLocalPlayer().getParguela_counter() > 0) {
                                    GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(true);
                                }
                                this.soundShowdown();
                            }

                            sqlUpdateHandEnd(sql_bote_total);

                            if (this.update_game_seats) {
                                String players = "";
                                for (String nick : this.nicks_permutados) {
                                    try {
                                        players += "#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                                    } catch (UnsupportedEncodingException ex) {
                                    }
                                }
                                sqlUpdateGameSeats(players.substring(1));
                                this.update_game_seats = false;
                            }

                            this.bote_total = 0f;

                            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                                jugador.resetBote();
                                jugador.checkGameOver();
                            }
                        }

                        disableAllPlayersTimeout();

                        synchronized (lock_fin_mano) {

                            Helpers.GUIRun(() -> {
                                GameFrame.getInstance().getMenu_rabbit_off().setEnabled(false);
                                GameFrame.getInstance().getMenu_rabbit_free().setEnabled(false);
                                GameFrame.getInstance().getMenu_rabbit_sb().setEnabled(false);
                                GameFrame.getInstance().getMenu_rabbit_bb().setEnabled(false);
                                GameFrame.getInstance().getIwtsth_rule_menu().setEnabled(false);
                                Helpers.TapetePopupMenu.IWTSTH_RULE_MENU.setEnabled(false);
                                Helpers.TapetePopupMenu.RABBIT_OFF.setEnabled(false);
                                Helpers.TapetePopupMenu.RABBIT_FREE.setEnabled(false);
                                Helpers.TapetePopupMenu.RABBIT_SB.setEnabled(false);
                                Helpers.TapetePopupMenu.RABBIT_BB.setEnabled(false);
                                GameFrame.getInstance().getTapete().getCommunityCards().repaint();
                            });

                            synchronized (lock_rabbit) {

                                if (GameFrame.RABBIT_HUNTING != 0 && !GameFrame.getInstance().getLocalPlayer().isCalentando() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {
                                    procesarCartasComunesRestantes();
                                }

                                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                    if (carta.isTapada()) {
                                        if (GameFrame.RABBIT_HUNTING != 0 && !GameFrame.getInstance().getLocalPlayer().isCalentando() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {
                                            carta.taparRabbit();
                                        } else {
                                            carta.desenfocar();
                                        }
                                    }
                                }

                            }

                            startIWTSTHPlayersBlinking();

                            if (!GameFrame.TEST_MODE) {
                                if (getJugadoresActivos() > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {
                                    this.pausaConBarra(this.bote.getSide_pot_count() == 0 ? ((resisten.size() > 1 || GameFrame.RABBIT_HUNTING != 0) ? PAUSA_ENTRE_MANOS : Math.round(0.5f * PAUSA_ENTRE_MANOS)) : Math.round(1.5f * PAUSA_ENTRE_MANOS));
                                }

                                if (this.iwtsthing) {
                                    synchronized (lock_iwtsth) {
                                        if (this.iwtsthing) {
                                            try {
                                                lock_iwtsth.wait(IWTSTH_TIMEOUT);
                                            } catch (InterruptedException ex) {
                                            }
                                            this.iwtsthing = false;
                                        }
                                    }
                                }

                                synchronized (lock_mostrar) {
                                    setShowTime(false);
                                }
                                GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);

                                if (!this.isLast_hand()) {
                                    checkRebuyTime();
                                    exitSpectatorBots();
                                    updateExitPlayers();
                                    if (GameFrame.RABBIT_HUNTING != 0) {
                                        waitRabbitProcessing();
                                    }
                                } else {

                                    fin_de_la_transmision = true;
                                }
                            } else {
                                this.pausaConBarra(Crupier.PAUSA_ENTRE_MANOS_TEST);
                                synchronized (lock_mostrar) {
                                    setShowTime(false);
                                }
                                GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);
                                fin_de_la_transmision = this.isLast_hand();
                            }

                            Helpers.GUIRun(() -> {
                                // El lambda se programa al EDT y puede ejecutarse
                                // después de que finTransmision/abortToRecover hayan
                                // disposed GameFrame (caso MISDEAL → abort). Sin
                                // null-check, el NPE rompe el EDT y deja la JVM
                                // medio-muerta para la siguiente partida.
                                GameFrame gf = GameFrame.getInstance();
                                if (gf != null && gf.isPartida_local()) {
                                    gf.getMenu_rabbit_off().setEnabled(true);
                                    gf.getMenu_rabbit_free().setEnabled(true);
                                    gf.getMenu_rabbit_sb().setEnabled(true);
                                    gf.getMenu_rabbit_bb().setEnabled(true);
                                    gf.getIwtsth_rule_menu().setEnabled(true);
                                    Helpers.TapetePopupMenu.IWTSTH_RULE_MENU.setEnabled(true);
                                    Helpers.TapetePopupMenu.RABBIT_OFF.setEnabled(true);
                                    Helpers.TapetePopupMenu.RABBIT_FREE.setEnabled(true);
                                    Helpers.TapetePopupMenu.RABBIT_SB.setEnabled(true);
                                    Helpers.TapetePopupMenu.RABBIT_BB.setEnabled(true);
                                }
                            });
                        }
                    } else {

                        if (!GameFrame.getInstance().getLocalPlayer().isSpectator() && Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {
                            GameFrame.getInstance().getLocalPlayer().pagar(this.bote_sobrante, null);
                            this.bote_sobrante = 0f;
                        }

                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                            carta.iniciarCarta();
                        }

                        GameFrame.getInstance().getTiempo_juego().stop();

                        // Defense in depth: if we reach GAME OVER while a
                        // recovery dragon was still open (NUEVA_MANO returned false
                        // before the replay loop got a chance to close it), tear it
                        // down so the user is not stuck looking at the GIF.
                        cerrarRecoverDialogYSync();

                        GameFrame.getInstance().getRegistro().print(Translator.translate("player.la_timba_ha_terminado_no"));

                        Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("player.la_timba_ha_terminado_no"), new ImageIcon(Init.class.getResource("/images/exit.png")));

                        fin_de_la_transmision = true;

                    }
                } else {

                    GameFrame.getInstance().getTiempo_juego().stop();

                    // Defense in depth: see comment above.
                    cerrarRecoverDialogYSync();

                    GameFrame.getInstance().getRegistro().print(Translator.translate("player.la_timba_ha_terminado_no"));

                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("player.la_timba_ha_terminado_no"), new ImageIcon(Init.class.getResource("/images/exit.png")));

                    fin_de_la_transmision = true;
                }
            } catch (Exception ex) {
                if (!fin_de_la_transmision) {
                    LOGGER.log(Level.SEVERE, "CRUPIER FATAL ERROR", ex);
                    Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("error.crupier_fatal"));
                    System.exit(1);
                }
            }
        }

        if (!GameFrame.getInstance().isPartida_local()) {
            String exitCmd = "EXIT#" + getTestamentoCriptografico();
            sendGAMECommandToServer(exitCmd, false);
        }

        GameFrame.getInstance().finTransmision(fin_de_la_transmision);
    }

    public void checkRebuyTime() {

        ArrayList<String> rebuy_players = new ArrayList<>();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                    && Helpers.float1DSecureCompare(0f,
                            Helpers.floatClean(jugador.getStack()) + Helpers.floatClean(jugador.getPagar())) == 0) {

                String nick = jugador.getNickname();
                Participant participante = GameFrame.getInstance().getParticipantes().get(nick);
                boolean isBot = participante != null && participante.isCpu();

                if (!GameFrame.REBUY || (isBot && !GameFrame.BOT_REBUY) || atRebuyLimit(nick)) {
                    jugador.setSpectator(null);
                } else {
                    rebuy_players.add(nick);
                }

            }
        }

        this.rebuy_time = !rebuy_players.isEmpty();

        if (GameFrame.getInstance().getLocalPlayer().isActivo()
                && Helpers.float1DSecureCompare(Helpers.floatClean(GameFrame.getInstance().getLocalPlayer().getStack())
                        + Helpers.floatClean(GameFrame.getInstance().getLocalPlayer().getPagar()), 0f) == 0) {

            this.rebuy_time = true;

            final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

            if (GameFrame.REBUY && !atRebuyLimit(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                Helpers.GUIRunAndWait(() -> {
                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);

                        GameFrame.getInstance().getTapete().repaint();
                    }

                    gameover_dialog = new GameOverDialog(GameFrame.getInstance(), true);

                    GameFrame.getInstance().setGame_over_dialog(true);

                    gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());

                    gameover_dialog.setVisible(true);

                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);

                        GameFrame.getInstance().getTapete().repaint();
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                if (gameover_dialog.isContinua()) {

                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        rebuy_now.put(GameFrame.getInstance().getLocalPlayer().getNickname(),
                                (int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#"
                                + String.valueOf((int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                } else {
                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        String comando = "REBUY#"
                                + Base64.getEncoder().encodeToString(
                                        GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"))
                                + "#0";

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                    if (rebuy_now.containsKey(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                        rebuy_now.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                    }

                    GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                    GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname()
                            + Translator.translate("player.te_quedas_de_espectador"));
                }

            } else {

                Helpers.GUIRunAndWait(() -> {
                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {
                        GameFrame.getInstance().getCapa_brillo().setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);

                        GameFrame.getInstance().getTapete().repaint();
                    }

                    gameover_dialog = new GameOverDialog(GameFrame.getInstance(), true, true);

                    GameFrame.getInstance().setGame_over_dialog(true);

                    gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());

                    gameover_dialog.setVisible(true);

                    if (old_brightness != BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS) {

                        GameFrame.getInstance().getCapa_brillo().setBrightness(old_brightness);

                        GameFrame.getInstance().getTapete().repaint();
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + " "
                        + Translator.translate("player.te_quedas_de_espectador"));
            }

        }

        if (!rebuy_players.isEmpty()) {

            // Enviamos los REBUYS de los bots
            if (GameFrame.getInstance().isPartida_local()) {

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (rebuy_players.contains(jugador.getNickname())
                            && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()) != null
                            && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                        rebuy_players.remove(jugador.getNickname());

                        rebuy_now.put(jugador.getNickname(), GameFrame.BUYIN);

                        try {
                            String comando = "REBUY#"
                                    + Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                    + "#" + String.valueOf(GameFrame.BUYIN);

                            this.broadcastGAMECommandFromServer(comando, null);

                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

            this.recibirRebuys(rebuy_players);
        }

        this.rebuy_time = false;

    }

    public HashMap<Player, Hand> calcularJugadas(ArrayList<Player> jugadores) {
        HashMap<Player, Hand> jugadas = new HashMap<>();

        for (Player jugador : jugadores) {

            // If the player reaches winner calculation with null cards, it means
            // they disconnected before reaching showdown. Their hand is mucked.
            if (jugador.getHoleCard1().getValor() == null || jugador.getHoleCard1().getValor().isEmpty() || jugador.getHoleCard1().getValor().equals("null")) {
                LOGGER.log(Level.WARNING, "⚠️ [MUCK] {0} could not reveal cards due to disconnection. Pot lost.", jugador.getNickname());
                continue; // Al no meterlo en el mapa 'jugadas', el motor lo ignora para el premio.
            }

            ArrayList<Card> cartas_utilizables = new ArrayList<>();
            for (Card c : GameFrame.getInstance().getCartas_comunes()) {
                if (c != null && !c.isTapada() && c.getValor() != null && !c.getValor().isEmpty()) {
                    cartas_utilizables.add(c);
                }
            }

            cartas_utilizables.add(jugador.getHoleCard1());
            cartas_utilizables.add(jugador.getHoleCard2());
            try {
                jugadas.put(jugador, new Hand(cartas_utilizables));
            } catch (Exception e) {
            }
        }

        return jugadas;
    }

    public HashMap<Player, Hand> calcularGanadores(HashMap<Player, Hand> candidatos) {

        int jugada_max = Hand.CARTA_ALTA;

        // Averiguamos la jugada máxima entre todos los jugadores
        for (Map.Entry<Player, Hand> entry : candidatos.entrySet()) {

            if (entry.getValue().getValue() > jugada_max) {
                jugada_max = entry.getValue().getValue();
            }
        }

        // Eliminamos a los jugadores con jugadas por debajo de la jugada máxima
        for (Iterator<Map.Entry<Player, Hand>> it = candidatos.entrySet().iterator(); it.hasNext();) {

            Map.Entry<Player, Hand> entry = it.next();

            if (entry.getValue().getValue() < jugada_max) {
                it.remove();
            }
        }

        if (candidatos.size() == 1 || jugada_max == Hand.ESCALERA_COLOR_REAL) {

            return candidatos;

        } else {

            // Si hay varios con la jugada máxima intentamos desempatar
            switch (jugada_max) {
                case Hand.ESCALERA_COLOR:
                    return desempatarEscalera(candidatos);
                case Hand.POKER:
                    return desempatarRepetidas(candidatos, CARTAS_POKER);
                case Hand.FULL:
                    return desempatarFull(candidatos);
                case Hand.COLOR:
                    return desempatarCartaAlta(candidatos, 0);
                case Hand.ESCALERA:
                    return desempatarEscalera(candidatos);
                case Hand.TRIO:
                    return desempatarRepetidas(candidatos, CARTAS_TRIO);
                case Hand.DOBLE_PAREJA:
                    return desempatarDoblePareja(candidatos);
                case Hand.PAREJA:
                    return desempatarRepetidas(candidatos, CARTAS_PAREJA);
                default:
                    return desempatarCartaAlta(candidatos, 0);
            }
        }
    }

    private HashMap<Player, Hand> desempatarDoblePareja(HashMap<Player, Hand> jugadores) {

        int carta_alta = 1;

        // Averiguamos la carta más alta de la primera pareja (la pareja grande)
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan una pareja grande menor
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            carta_alta = 1;

            // Averiguamos la carta más alta de la segunda pareja
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(2).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(2).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una pareja secundaria menor
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(2).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            if (jugadores.size() == 1) {

                return jugadores;
            } else {

                return desempatarCartaAlta(jugadores, CARTAS_PAREJA * 2);
            }

        }

    }

    private HashMap<Player, Hand> desempatarFull(HashMap<Player, Hand> jugadores) {

        int carta_alta = 1;

        // Averiguamos la carta más alta del trío del FULL
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan un trío con carta más pequeña
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            carta_alta = 1;

            // Averiguamos la carta más alta de la pareja del FULL
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(3).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(3).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una pareja con carta más pequeña
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(3).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            return jugadores;

        }

    }

    private HashMap<Player, Hand> desempatarRepetidas(HashMap<Player, Hand> jugadores, int repetidas) {

        int carta_alta = 1;

        // Averiguamos la carta más alta del POKER/TRIO/PAREJA
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan un POKER/TRIO/PAREJA con carta más pequeña
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        if (jugadores.size() == 1) {

            return jugadores;

        } else {

            return desempatarCartaAlta(jugadores, repetidas);
        }

    }

    private HashMap<Player, Hand> desempatarEscalera(HashMap<Player, Hand> jugadores) {

        int carta_alta = 0;

        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {

            Hand jugada = entry.getValue();

            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        // Nos cargamos todos los que tengan una escalera menor que la máxima
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {

            Map.Entry<Player, Hand> entry = it.next();

            Hand jugada = entry.getValue();

            if (jugada.getMano().get(0).getValorNumerico() < carta_alta) {
                it.remove();
            }
        }

        return jugadores;
    }

    private HashMap<Player, Hand> desempatarCartaAlta(HashMap<Player, Hand> jugadores, int start_card) {

        int cartas_max = CARTAS_MAX;

        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {

            Hand jugada = entry.getValue();

            cartas_max = Math.min(cartas_max, jugada.getMano().size());
        }

        for (int i = start_card; i < cartas_max; i++) {

            int carta_alta = 1;

            // Averiguamos la carta más alta
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(i).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(i).getValorNumerico();
                }
            }

            // Nos cargamos todos los que tengan una carta menor
            for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Player, Hand> entry = it.next();
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(i).getValorNumerico() < carta_alta) {
                    it.remove();
                }
            }

            if (jugadores.size() == 1) {

                return jugadores;
            }
        }

        return jugadores;

    }

    private HashMap<Player, Integer[]> monteCarlo(ArrayList<Player> resisten, int iterations) {

        HashMap<Player, Integer[]> stats = new HashMap<>();

        // Defensa: jugadores que llegan a monteCarlo sin hole cards
        // reveladas (disconnection mid-hand antes del showdown) construyen
        // una org.alberta.poker.Hand con suit/rank invalido, y
        // HandEvaluator.Find_Flush devuelve -1 al iterar el array de
        // palos -> OOBE -> CRUPIER FATAL. calcularJugadas ya filtra el
        // mismo caso (logea [MUCK] y los excluye del calculo de
        // ganadores); aqui replicamos el filtro para que monteCarlo no
        // pete cuando se evalua la ronda siguiente a un peer recien caido.
        ArrayList<Player> resistenSafe = new ArrayList<>(resisten.size());
        for (Player p : resisten) {
            if (p == null) {
                continue;
            }
            Card hc1 = p.getHoleCard1();
            Card hc2 = p.getHoleCard2();
            if (hc1 == null || hc2 == null) {
                continue;
            }
            String v1 = hc1.getValor();
            String v2 = hc2.getValor();
            if (v1 == null || v1.isEmpty() || v1.equals("null")
                    || v2 == null || v2.isEmpty() || v2.equals("null")) {
                continue;
            }
            resistenSafe.add(p);
        }
        resisten = resistenSafe;

        if (resisten.size() <= 1) {
            return stats;
        }

        ArrayList<Integer> deck = new ArrayList<>();

        for (int i = 1; i <= 52; i++) {
            deck.add(i);
        }

        org.alberta.poker.Hand board = new org.alberta.poker.Hand();

        if (this.street == Crupier.FLOP) {
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero()));

            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero());
        } else if (this.street == Crupier.TURN) {
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero()));
            board.addCard(Bot.coronaIntegerCard2LokiCard(
                    GameFrame.getInstance().getTapete().getCommunityCards().getTurn().getCartaComoEntero()));

            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop1().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop2().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getFlop3().getCartaComoEntero());
            deck.remove(
                    (Integer) GameFrame.getInstance().getTapete().getCommunityCards().getTurn().getCartaComoEntero());
        }

        HashMap<Player, org.alberta.poker.Hand> hole_cards = new HashMap<>();

        for (Player p : resisten) {

            org.alberta.poker.Hand hole = new org.alberta.poker.Hand();
            hole.addCard(Bot.coronaIntegerCard2LokiCard(p.getHoleCard1().getCartaComoEntero()));
            hole.addCard(Bot.coronaIntegerCard2LokiCard(p.getHoleCard2().getCartaComoEntero()));
            hole_cards.put(p, hole);

            deck.remove((Integer) p.getHoleCard1().getCartaComoEntero());
            deck.remove((Integer) p.getHoleCard2().getCartaComoEntero());

            stats.put(p, new Integer[]{iterations, 0, 0, 0});
        }

        for (int m = 0; m < iterations; m++) {

            ArrayList<Integer> deck_iteration = new ArrayList<>(deck);

            Collections.shuffle(deck_iteration, Helpers.CSPRNG_GENERATOR);

            org.alberta.poker.Hand board_iteration = new org.alberta.poker.Hand(board);

            switch (board_iteration.size()) {
                case 0:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(1)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(2)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(3)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(4)));
                    break;
                case 3:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(1)));
                    break;
                case 4:
                    board_iteration.addCard(Bot.coronaIntegerCard2LokiCard(deck_iteration.get(0)));
                    break;
                default:
                    break;
            }

            HashMap<Player, org.alberta.poker.Hand> cards7_iteration = new HashMap<>();

            for (Player p : resisten) {
                org.alberta.poker.Hand player_c7_iteration = new org.alberta.poker.Hand(board_iteration);

                player_c7_iteration.addCard(hole_cards.get(p).getCard(1));
                player_c7_iteration.addCard(hole_cards.get(p).getCard(2));

                cards7_iteration.put(p, player_c7_iteration);
            }

            org.alberta.poker.Hand best = cards7_iteration.get(resisten.get(0));

            boolean tie = false;

            for (Player p : resisten) {

                if (cards7_iteration.get(p) != best) {
                    int compare = Bot.HANDEVALUATOR.compareHands(cards7_iteration.get(p), best);

                    if (compare == 1) {
                        best = cards7_iteration.get(p);
                    } else if (compare == 0) {
                        tie = true;
                    }
                }
            }

            for (Player p : resisten) {

                Integer[] stats_jugador = stats.get(p);

                if (Bot.HANDEVALUATOR.compareHands(cards7_iteration.get(p), best) == 0) {
                    if (tie) {
                        stats_jugador[3]++;
                        stats.put(p, stats_jugador);
                    } else {
                        stats_jugador[1]++;
                        stats.put(p, stats_jugador);
                    }
                } else {
                    stats_jugador[2]++;
                    stats.put(p, stats_jugador);
                }
            }

        }

        return stats;

    }

    public java.util.ArrayList<Player> getAnilloCriptografico() {
        java.util.ArrayList<Player> ring = new java.util.ArrayList<>();
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            // Incluimos a todos (activos, espectadores, server, desconectados).
            // Excluimos solo a los que entran a la mesa en caliente (calentando) durante un recover.
            if (!jugador.isCalentando()) {
                ring.add(jugador);
            }
        }
        // Sort alphabetically to guarantee identical mathematical positions across all clients
        java.util.Collections.sort(ring, (p1, p2) -> p1.getNickname().compareTo(p2.getNickname()));
        return ring;
    }

    // --- DealerView contract additions (read-only views over GameFrame and BOT_COMMUNITY_CARDS) ---

    @Override
    public java.util.List<? extends Player> getPlayersInSeatingOrder() {
        return GameFrame.getInstance().getJugadores();
    }

    @Override
    public int getBoardSize() {
        return Bot.BOT_COMMUNITY_CARDS.size();
    }

    @Override
    public int getBoardCardIndex(int i) {
        if (i < 0 || i >= Bot.BOT_COMMUNITY_CARDS.size()) {
            return -1;
        }
        return Bot.BOT_COMMUNITY_CARDS.getCard(i + 1).getIndex();
    }

}
