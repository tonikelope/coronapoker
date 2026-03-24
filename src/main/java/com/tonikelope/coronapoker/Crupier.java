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
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
public class Crupier implements Runnable {

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
    public static final int PERMUTATION_ENCRYPTION_PLAYERS = 2;
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

    // VARIABLES CRIPTOGRÁFICAS DE ESTADO
    private volatile byte[] local_hand_seed = null;
    public volatile byte[] local_mega_packet = null;
    public volatile byte[] local_mk_share = null;
    public volatile byte[] valid_master_key = null;

    // --- FIX: TOKENS DEL HOST ---
    public volatile byte[] local_token_flop = null;
    public volatile byte[] local_token_turn = null;
    public volatile byte[] local_token_river = null;

    public volatile byte[] pure_local_cards = new byte[2];

    private final ConcurrentLinkedQueue<byte[]> crypto_replay_queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> received_commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones_locales_recuperadas = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Integer> rebuy_now = new ConcurrentHashMap<>();
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
    private final Object lock_permutation_key = new Object();
    private final Object lock_fin_mano = new Object();
    private final Object lock_hand_verification = new Object();
    private final ConcurrentHashMap<String, Player> nick2player = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Hand> perdedores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Player> flop_players = new ConcurrentLinkedQueue<>();

    private byte[] activeHandId;
    private volatile int conta_mano = 0;
    private volatile int conta_accion = 0;
    private volatile boolean verified_hand = false;
    private volatile float bote_total = 0f;
    private volatile float apuestas = 0f;
    private volatile float ciega_grande = GameFrame.CIEGA_GRANDE;
    private volatile float ciega_pequeña = GameFrame.CIEGA_PEQUEÑA;
    private volatile Integer[] permutacion_baraja = null;
    private volatile float apuesta_actual = 0f;
    private volatile float ultimo_raise = 0f;
    private volatile float partial_raise_cum = 0f;
    private volatile int conta_raise = 0;
    private volatile int conta_bet = 0;
    private volatile float bote_sobrante = 0f;
    private volatile String[] nicks_permutados;
    private volatile boolean fin_de_la_transmision = false;
    private volatile int street = PREFLOP;
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
    private volatile String permutation_key = null;
    private volatile GameOverDialog gameover_dialog = null;
    private volatile String dealer_nick = null;
    private volatile String big_blind_nick = null;
    private volatile String small_blind_nick = null;
    private volatile String utg_nick = null;
    private volatile boolean saltar_primera_mano = false;
    private volatile boolean update_game_seats = false;
    private volatile int tot_acciones_recuperadas = 0;
    private volatile Float beneficio_bote_principal = null;
    private volatile Integer[] permutacion_recuperada;
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
    private volatile boolean legitHand = false;

    private void enviarCartasJugadoresRemotos() {
        this.local_mk_share = null;
        for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
            if (p != null) {
                p.setMk_share(null);
            }
        }

        java.util.ArrayList<Player> ringCriptografico = getAnilloCriptografico();
        int numPlayers = ringCriptografico.size();

        byte[][] playerSeeds = new byte[numPlayers][32];
        byte[][] playerPubKeys = new byte[numPlayers][32];
        StringBuilder orderBuilder = new StringBuilder();

        String[] currentRing = new String[numPlayers];

        for (int i = 0; i < numPlayers; i++) {
            Player j = ringCriptografico.get(i);
            currentRing[i] = j.getNickname();

            byte[] pubKey = null;
            byte[] seed = null;
            try {
                orderBuilder.append(Base64.getEncoder().encodeToString(j.getNickname().getBytes("UTF-8"))).append(",");
            } catch (Exception e) {
            }

            if (j.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                pubKey = GameFrame.getInstance().getSala_espera().local_player_public_key;
                seed = this.local_hand_seed;
            } else {
                Participant p = GameFrame.getInstance().getParticipantes().get(j.getNickname());
                if (p != null) {
                    if (p.isCpu()) {
                        try {
                            byte[] botPriv = p.getPanoptes_private_key() != null ? p.getPanoptes_private_key() : java.security.MessageDigest.getInstance("SHA-256").digest(j.getNickname().getBytes("UTF-8"));
                            pubKey = Panoptes.getInstance().utilsGetPublicKey(botPriv);
                        } catch (Exception e) {
                        }
                    } else {
                        pubKey = p.getPanoptes_public_key();
                    }
                    seed = p.getPanoptes_hand_seed();
                }
            }
            playerPubKeys[i] = (pubKey != null) ? pubKey : new byte[32];
            playerSeeds[i] = (seed != null) ? seed : new byte[32];
        }

        this.active_crypto_ring = currentRing;

        // V71 FIX: Removed the call to stateInitializeHand() that wiped the entropy generated in readyForNextHand()
        this.local_mega_packet = Panoptes.getInstance().easyFlatDeal(playerSeeds, playerPubKeys);
        String megaPacketB64 = java.util.Base64.getEncoder().encodeToString(this.local_mega_packet);

        String orderB64 = "";
        try {
            orderB64 = java.util.Base64.getEncoder().encodeToString(orderBuilder.toString().getBytes("UTF-8"));
        } catch (Exception e) {
        }

        try {
            String panoptesFossilName = Init.DEV_MODE ? "/panoptes_hand_commit_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".bin" : "/panoptes_hand_commit.bin";
            String fullData = "ORDER@" + orderBuilder.toString() + "#FULLMEGAPACKET@" + megaPacketB64;
            Files.writeString(Paths.get(Init.CORONA_DIR + panoptesFossilName), fullData);
        } catch (Exception e) {
        }

        broadcastGAMECommandFromServer("MEGAPACKET#" + orderB64 + "#" + megaPacketB64, null, true);

        for (int i = 0; i < numPlayers; i++) {
            Player j = ringCriptografico.get(i);
            Participant p = GameFrame.getInstance().getParticipantes().get(j.getNickname());

            if (j.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                if (Panoptes.getInstance().stateIngestMegapacket(this.local_mega_packet, i)) {
                    byte[] visual = Panoptes.getInstance().stateGetLocalPocketCards();
                    if (visual != null && visual.length == 2) {
                        this.local_original_cards[0] = visual[0];
                        this.local_original_cards[1] = visual[1];
                    }
                }
            } else if (p != null && p.isCpu()) {
                byte[] epub = new byte[32];
                System.arraycopy(this.local_mega_packet, 49 + (i * 210) + 64, epub, 0, 32);
                byte[] enc = new byte[114];
                System.arraycopy(this.local_mega_packet, 49 + (i * 210) + 96, enc, 0, 114);
                try {
                    byte[] botPriv = java.security.MessageDigest.getInstance("SHA-256").digest(j.getNickname().getBytes("UTF-8"));
                    byte[] clear = Panoptes.getInstance().utilsDecryptBotEnvelope(botPriv, epub, enc);

                    if (clear != null && clear.length >= 2) {
                        if (j.isActivo()) {
                            j.getHoleCard1().iniciarConValorNumerico(((int) clear[0] & 0xFF) + 1);
                            j.getHoleCard2().iniciarConValorNumerico(((int) clear[1] & 0xFF) + 1);
                        }

                        if (clear.length >= 98) {
                            p.setMk_share(java.util.Arrays.copyOfRange(clear, 18, 50));
                            p.setToken_flop(java.util.Arrays.copyOfRange(clear, 50, 66));
                            p.setToken_turn(java.util.Arrays.copyOfRange(clear, 66, 82));
                            p.setToken_river(java.util.Arrays.copyOfRange(clear, 82, 98));
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private ArrayList<String> recibirMisCartas() {
        String[] cartas = new String[2];
        long start_time = System.currentTimeMillis();
        boolean ok;

        do {
            ok = false;
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    if (partes[2].equals("MEGAPACKET")) {
                        ok = true;

                        // Parse the explicitly provided ring order
                        String orderB64 = partes[3];
                        this.local_mega_packet = java.util.Base64.getDecoder().decode(partes[4]);

                        String orderStr = "";
                        try {
                            orderStr = new String(java.util.Base64.getDecoder().decode(orderB64), "UTF-8");
                            String[] orderTokens = orderStr.split(",");
                            java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                            for (String token : orderTokens) {
                                if (!token.isEmpty()) {
                                    ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                }
                            }
                            this.active_crypto_ring = ringList.toArray(new String[0]);
                        } catch (Exception e) {
                            java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, "Failed to decode exact ring order", e);
                        }

                        // Store local panoptesFossil for client recovery using Host compatible text format
                        try {
                            String panoptesFossilName = "/panoptes_hand_commit.bin";
                            if (Init.DEV_MODE) {
                                String safeNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                                panoptesFossilName = "/panoptes_hand_commit_" + safeNick + ".bin";
                            }
                            String fullData = "ORDER@" + orderStr + "#FULLMEGAPACKET@" + partes[4];
                            java.nio.file.Files.writeString(java.nio.file.Paths.get(Init.CORONA_DIR + panoptesFossilName), fullData);
                        } catch (Exception e) {
                        }

                        // MyPos calculation is now guaranteed mathematically stable
                        int myPos = calcularPosicionEnPaquete(GameFrame.getInstance().getNick_local());

                        if (myPos != -1) {
                            if (Panoptes.getInstance().stateIngestMegapacket(this.local_mega_packet, myPos)) {
                                byte[] visual = Panoptes.getInstance().stateGetLocalPocketCards();

                                if (visual != null && visual.length == 2) {
                                    this.local_original_cards[0] = visual[0];
                                    this.local_original_cards[1] = visual[1];

                                    int id1 = ((int) visual[0] & 0xFF) + 1;
                                    int id2 = ((int) visual[1] & 0xFF) + 1;

                                    cartas[0] = Card.VALORES[(id1 - 1) % 13] + "_" + Card.PALOS[(id1 - 1) / 13];
                                    cartas[1] = Card.VALORES[(id2 - 1) % 13] + "_" + Card.PALOS[(id2 - 1) / 13];
                                }
                            } else {
                                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, "[PANOPTES] FATAL ERROR: Native Ingest failed for Client.");
                            }
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
                Helpers.pausar(100);
            }
        } while (!ok);

        return new ArrayList<>(java.util.Arrays.asList(cartas));
    }

    private void preShowdownDecryption(ArrayList<Player> inShowdown) {
        if (!GameFrame.getInstance().isPartida_local() || this.local_mega_packet == null) {
            return;
        }
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
            boolean isHost = jugador.getNickname().equals(GameFrame.getInstance().getNick_local());
            boolean isBot = (p != null && p.isCpu());
            boolean isZombie = jugador.isExit();

            if (isHost || isBot || isZombie) {
                if (isHost) {
                    // ELIMINADO: No llamamos a actualizarConValorNumerico para el Host.
                    // Ya tenemos las cartas y están en su orden correcto.
                } else {
                    try {
                        int pos = calcularPosicionEnPaquete(jugador.getNickname());
                        if (pos != -1) {
                            byte[] epub = new byte[32];
                            System.arraycopy(this.local_mega_packet, 49 + (pos * 210) + 64, epub, 0, 32);
                            byte[] encCards = new byte[114];
                            System.arraycopy(this.local_mega_packet, 49 + (pos * 210) + 96, encCards, 0, 114);

                            byte[] botPriv = java.security.MessageDigest.getInstance("SHA-256").digest(jugador.getNickname().getBytes("UTF-8"));
                            byte[] clear = Panoptes.getInstance().utilsDecryptBotEnvelope(botPriv, epub, encCards);

                            if (clear != null && clear.length >= 98) {
                                if (p != null) {
                                    p.setMk_share(Arrays.copyOfRange(clear, 18, 50));
                                }
                                if (!isZombie && inShowdown.contains(jugador)) {
                                    jugador.getHoleCard1().actualizarConValorNumerico(((int) clear[0] & 0xFF) + 1);
                                    jugador.getHoleCard2().actualizarConValorNumerico(((int) clear[1] & 0xFF) + 1);
                                    jugador.ordenarCartas();
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    public void verificarManoLocal(byte[] mk, String[] partesHandVerify) {

        try {

            synchronized (this.lock_hand_verification) {

                if (this.local_mega_packet == null) {
                    this.verified_hand = true;
                    this.lock_hand_verification.notifyAll();
                    return;
                }

                int myPos = calcularPosicionEnPaquete(GameFrame.getInstance().getNick_local());

                if (myPos == -1) {
                    this.verified_hand = true;
                    this.lock_hand_verification.notifyAll();
                    return;
                }

                byte[][] receiptsArray = null;

                if (partesHandVerify != null) {
                    int offset = -1;
                    for (int i = 0; i < partesHandVerify.length; i++) {
                        if ("HANDVERIFY".equals(partesHandVerify[i])) {
                            offset = i;
                            break;
                        }
                    }

                    if (offset != -1 && partesHandVerify.length > offset + 2) {
                        int numPlayers = this.local_mega_packet[16] & 0xFF;
                        receiptsArray = new byte[numPlayers][];

                        // Extract Host/Server receipt
                        int serverPos = calcularPosicionEnPaquete(GameFrame.getInstance().getSala_espera().getServer_nick());
                        if (serverPos != -1 && !partesHandVerify[offset + 2].equals("*")) {
                            try {
                                receiptsArray[serverPos] = java.util.Base64.getDecoder().decode(partesHandVerify[offset + 2]);
                            } catch (Exception e) {
                            }
                        }

                        // Extract Peers and Bots receipts
                        for (int i = offset + 3; i < partesHandVerify.length; i++) {
                            String[] peerData = partesHandVerify[i].split(":");
                            if (peerData.length == 2) {
                                try {
                                    String peerNick = new String(java.util.Base64.getDecoder().decode(peerData[0]), "UTF-8");
                                    int pPos = calcularPosicionEnPaquete(peerNick);
                                    if (pPos != -1) {
                                        receiptsArray[pPos] = java.util.Base64.getDecoder().decode(peerData[1]);
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }

                try {
                    // V71 FIX: We only send mega_packet, master_key, myPos and receipts. C-Engine extracts cards from vault.
                    this.legitHand = Panoptes.getInstance().utilsVerifyHandHistory(this.local_mega_packet, mk, myPos, receiptsArray);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error verifying hand history", ex);
                }

            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in manual hand verification", e);

        } finally {
            this.verified_hand = true;

            synchronized (this.lock_hand_verification) {
                this.lock_hand_verification.notifyAll();
            }
        }

    }

    public String getTestamentoCriptografico() {
        try {
            // V61: Extracts the 96-byte testament and triggers native vault lobotomy
            byte[] testament = Panoptes.getInstance().sessionGenerateExitTestament();
            if (testament != null && testament.length == 96) {
                return Base64.getEncoder().encodeToString(testament);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating exit testament", e);
        }
        return "*";
    }

    public boolean extractHandWithMasterKey(Player target) {
        if (this.local_mega_packet == null || this.valid_master_key == null || this.valid_master_key.length != 32) {
            LOGGER.log(Level.WARNING, "⚠️ [ZERO-TRUST] Cannot reveal cards for " + target.getNickname() + ". Master Key is unavailable.");
            return false;
        }
        try {
            byte[] final_seed = new byte[32];
            System.arraycopy(this.local_mega_packet, 17, final_seed, 0, 32);
            int numPlayers = this.local_mega_packet[16] & 0xFF;

            for (int p = 0; p < numPlayers; p++) {
                for (int i = 0; i < 32; i++) {
                    final_seed[i] ^= this.local_mega_packet[49 + (p * 210) + i];
                }
            }

            for (int i = 0; i < 32; i++) {
                final_seed[i] ^= this.valid_master_key[i];
            }

            byte[] deck = Panoptes.getInstance().utilsShuffleDeck(final_seed);

            int pos = calcularPosicionEnPaquete(target.getNickname());
            if (pos != -1) {
                int c1 = deck[pos] & 0xFF;
                int c2 = deck[numPlayers + pos] & 0xFF;
                target.getHoleCard1().actualizarConValorNumerico(c1 + 1);
                target.getHoleCard2().actualizarConValorNumerico(c2 + 1);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
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

    public void setPermutation_key(String permutation_key) {
        this.permutation_key = permutation_key;
    }

    public Object getPermutation_key_lock() {
        return lock_permutation_key;
    }

    public ConcurrentHashMap<String, Integer> getRebuy_now() {
        return rebuy_now;
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
        ArrayList<String> pendientes = new ArrayList<>();
        for (Player jugador : resisten) {
            if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {
                Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                if (p != null && !p.isCpu()) {
                    pendientes.add(jugador.getNickname());
                }
            }
        }

        if (pendientes.isEmpty()) {
            return null; // OK, no hay pendientes
        }

        int id = Helpers.CSPRNG_GENERATOR.nextInt();
        byte[] iv = new byte[16];
        Helpers.CSPRNG_GENERATOR.nextBytes(iv);
        String command = "GAME#" + id + "#REQ_TOKEN#" + targetStreet;

        for (String nick : pendientes) {
            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
            if (p != null) {
                p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
            }
        }

        long start_time = System.currentTimeMillis();
        boolean timeout = false;
        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!this.getReceived_commands().isEmpty()) {
                    String cmd = this.received_commands.poll();
                    String[] partes = cmd.split("#");

                    if (partes.length >= 5 && partes[2].equals("STREET_TOKEN") && Integer.parseInt(partes[4]) == targetStreet) {
                        try {
                            String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            pendientes.remove(nick);
                        } catch (Exception e) {
                        }
                    } else {
                        rejected.add(cmd);
                    }
                }
                if (!rejected.isEmpty()) {
                    this.getReceived_commands().addAll(rejected);
                    rejected.clear();
                }
            }

            if (!pendientes.isEmpty()) {
                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                    timeout = true;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!pendientes.isEmpty() && !timeout);

        if (timeout) {
            return String.join(", ", pendientes); // Devolvemos los nicks de los culpables
        }
        return null;
    }

    private Object[] recopilarTokens(int targetStreet, java.util.ArrayList<Player> resisten) {
        java.util.ArrayList<Player> ringCriptografico = getAnilloCriptografico();

        for (Player j : GameFrame.getInstance().getJugadores()) {
            Participant p = GameFrame.getInstance().getParticipantes().get(j.getNickname());
            if (p != null) {
                p.setReceived_token(null);
            }
        }

        java.util.ArrayList<String> pendientesHumanos = new java.util.ArrayList<>();
        for (Player jugador : ringCriptografico) {
            if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                if (p != null && !p.isCpu() && !jugador.isExit()) {
                    pendientesHumanos.add(jugador.getNickname());
                }
            }
        }

        if (!pendientesHumanos.isEmpty() && GameFrame.getInstance().isPartida_local()) {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);
            String command = "GAME#" + String.valueOf(id) + "#REQ_TOKEN#" + targetStreet;
            for (String nick : pendientesHumanos) {
                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                if (p != null) {
                    p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                }
            }

            long start_time = System.currentTimeMillis();
            boolean timeout = false;
            do {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!this.getReceived_commands().isEmpty()) {
                        String cmd = this.received_commands.poll();
                        String[] partes = cmd.split("#");
                        if (partes[2].equals("STREET_TOKEN")) {
                            try {
                                String nick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                                if (pendientesHumanos.contains(nick) && Integer.parseInt(partes[4]) == targetStreet) {
                                    Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                                    if (p != null) {
                                        p.setReceived_token(java.util.Base64.getDecoder().decode(partes[5]));
                                    }
                                    pendientesHumanos.remove(nick);
                                }
                            } catch (Exception e) {
                            }
                        } else {
                            rejected.add(cmd);
                        }
                    }
                    this.getReceived_commands().addAll(rejected);
                }
                if (!pendientesHumanos.isEmpty()) {
                    if (GameFrame.getInstance().checkPause()) {
                        start_time = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                        timeout = true;
                    } else {
                        synchronized (this.getReceived_commands()) {
                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                            }
                        }
                    }
                }
            } while (!pendientesHumanos.isEmpty() && !timeout);
        }

        byte[] aggregatedTokens = new byte[ringCriptografico.size() * 16];
        java.util.ArrayList<String> missingNicks = new java.util.ArrayList<>();

        for (int i = 0; i < ringCriptografico.size(); i++) {
            Player jug = ringCriptografico.get(i);
            Participant part = GameFrame.getInstance().getParticipantes().get(jug.getNickname());
            byte[] token = null;

            if (jug.getNickname().equals(GameFrame.getInstance().getNick_local())) {
                if (targetStreet == Crupier.FLOP) {
                    token = Panoptes.getInstance().stateGetFlopToken();
                } else if (targetStreet == Crupier.TURN) {
                    token = Panoptes.getInstance().stateGetTurnToken();
                } else if (targetStreet == Crupier.RIVER) {
                    token = Panoptes.getInstance().stateGetRiverToken();
                }
            } else if (part != null && (part.isCpu() || jug.isExit())) {
                if (targetStreet == Crupier.FLOP) {
                    token = part.getToken_flop();
                } else if (targetStreet == Crupier.TURN) {
                    token = part.getToken_turn();
                } else if (targetStreet == Crupier.RIVER) {
                    token = part.getToken_river();
                }
            } else if (part != null) {
                token = part.getReceived_token();
            }

            if (token != null && token.length == 16) {
                System.arraycopy(token, 0, aggregatedTokens, i * 16, 16);
            } else {
                missingNicks.add(jug.getNickname());
            }
        }

        if (!missingNicks.isEmpty()) {
            return new Object[]{null, String.join(", ", missingNicks)};
        }
        return new Object[]{aggregatedTokens, null};
    }

    public void rebuyNow(String nick, int buyin) {

        synchronized (lock_rebuynow) {
            if (!rebuy_now.containsKey(nick)) {
                this.rebuy_now.put(nick, buyin);
            } else {
                this.rebuy_now.remove(nick);
            }

            if (GameFrame.getInstance().isPartida_local()) {

                try {
                    this.broadcastGAMECommandFromServer(
                            "REBUYNOW#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                            + String.valueOf(buyin),
                            nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname()) ? null : nick);
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
                                            LOGGER.log(Level.SEVERE, null, ex);
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
                                "¡OJO A ESTO: NO SALEN LAS CUENTAS GLOBALES! -> (STACKS + SOBRANTE) != BUYIN");
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

        // Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        while (!pending.isEmpty() && !timeout) {

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("REBUY")) {

                        String nick = null;

                        try {
                            nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                        pending.remove(nick);

                        Player jugador = nick2player.get(nick);

                        jugador.setTimeout(false);

                        if (GameFrame.getInstance().isPartida_local()) {

                            broadcastGAMECommandFromServer(
                                    "REBUY#" + partes[3] + (partes.length > 4 ? "#" + partes[4] : ""), nick);
                        }

                        if (partes.length > 4) {

                            if (partes[4].equals("0")) {
                                jugador.setSpectator(null);
                            } else {
                                rebuy_now.put(nick, Integer.parseInt(partes[4]));
                            }
                        } else {
                            rebuy_now.put(nick, GameFrame.BUYIN);
                        }

                    } else {
                        rejected.add(comando);
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

                    if (nick2player.get(nick).isExit()) {
                        iterator.remove();
                    }
                }

                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > 2 * GameFrame.REBUY_TIMEOUT) {

                    if (GameFrame.getInstance().isPartida_local()) {

                        if (!pending.isEmpty()) {

                            for (String nick : pending) {
                                nick2player.get(nick).setTimeout(true);

                                if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                                    try {
                                        this.broadcastGAMECommandFromServer(
                                                "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")),
                                                nick,
                                                false);
                                    } catch (UnsupportedEncodingException ex) {
                                        LOGGER.log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                        }

                        // 0=yes, 1=no, 2=cancel
                        if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                Translator.translate("conn.forzamos_reset_del_socket_de"),
                                new ImageIcon(getClass().getResource("/images/action/timeout.png"))) == 0) {
                            for (String nick : pending) {
                                GameFrame.getInstance().getParticipantes().get(nick).forceSocketReconnect();
                            }

                        }

                        start_time = System.currentTimeMillis();

                    } else {

                        start_time = System.currentTimeMillis();
                    }
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
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
                GameFrame.getInstance().getParticipantes().get(nick).exitAndCloseSocket();
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
                    if (GameFrame.getInstance().getParticipantes().get(nick).isCpu()) {
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
            if (this.show_time) {
                Player jugador = nick2player.get(nick);
                if (jugador == null) {
                    return;
                }
                boolean isLocal = jugador.equals(GameFrame.getInstance().getLocalPlayer());

                // Solo desciframos si es remoto y faltan los valores
                if (!isLocal && (jugador.getHoleCard1().getValor() == null || jugador.getHoleCard1().getValor().isEmpty())) {
                    if (this.valid_master_key != null && this.valid_master_key.length == 32) {
                        extractHandWithMasterKey(jugador);
                        jugador.ordenarCartas();
                    }
                }

                try {
                    String comando = "SHOWCARDS#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#" + jugador.getHoleCard1().toShortString() + "#" + jugador.getHoleCard2().toShortString();
                    if (GameFrame.getInstance().isPartida_local()) {
                        broadcastGAMECommandFromServer(comando, nick);
                    } else if (isLocal) {
                        sendGAMECommandToServer(comando, false);
                    }
                } catch (Exception ex) {
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
                    }

                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                } else if (isLocal) {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                }
            }
        }
    }

    public ConcurrentHashMap<Player, Hand> getPerdedores() {
        return perdedores;
    }

    public boolean isShow_time() {
        return show_time;
    }

    public void showPlayerCards(String nick, String carta1, String carta2) {
        synchronized (lock_mostrar) {
            if (this.show_time) {
                Player jugador = nick2player.get(nick);
                if (jugador == null) {
                    return;
                }

                // BLINDAJE V61: Si el server nos hace un echo de nuestro propio paquete, LO IGNORAMOS.
                if (jugador.equals(GameFrame.getInstance().getLocalPlayer())) {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                    return;
                }

                if (jugador.getHoleCard1().isTapada()) {
                    String[] p1 = carta1.split("_");
                    String[] p2 = carta2.split("_");

                    jugador.getHoleCard1().actualizarValorPalo(p1[0], p1[1]);
                    jugador.getHoleCard2().actualizarValorPalo(p2[0], p2[1]);
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
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                } else {
                    setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);
                }
            }
        }
    }

    private String[] recibirFlop() {

        String[] cartas = new String[3];

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("FLOPCARDS")) {

                        ok = true;

                        cartas[0] = partes[3];

                        cartas[1] = partes[4];

                        cartas[2] = partes[5];

                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return cartas;

    }

    private String recibirTurn() {

        String carta = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("TURNCARD")) {

                        ok = true;

                        carta = partes[3];
                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return carta;

    }

    private String recibirRiver() {

        String carta = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("RIVERCARD")) {

                        ok = true;

                        carta = partes[3];
                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return carta;

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

    private void recuperarDatosClavePartida() {
        LOGGER.log(Level.INFO, "[ZERO-TRUST DEBUG] Starting recuperarDatosClavePartida...");

        killAllPlayerTimers();
        turno = System.currentTimeMillis();

        java.util.HashMap<String, Object> map;
        saltar_primera_mano = false;

        if (GameFrame.getInstance().isPartida_local()) {
            map = sqlRecoverServerLocalGameKeyData(true);
            if (map == null) {
                return;
            }

            map.put("permutation_key", false);
            if (map.get("start") != null) {
                GameFrame.GAME_START_TIMESTAMP = (long) map.get("start");
            }

            java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (!jugador.equals(GameFrame.getInstance().getLocalPlayer())
                        && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                    pendientes.add(jugador.getNickname());
                }
            }

            // Strict preflop player validation
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
                            LOGGER.log(Level.WARNING, "⚠️ [ZERO-TRUST] Cannot recover hand. Player missing or inactive: {0}", n);
                            break;
                        }
                    } catch (Exception e) {
                    }
                }
            }

            if (!saltar_primera_mano && map.get("hand_end") != null && (Long) map.get("hand_end") == 0L) {
                try {
                    String fosil = null;
                    String panoptesFossilName = "/panoptes_hand_commit.bin";
                    if (Init.DEV_MODE) {
                        String safeNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                        panoptesFossilName = "/panoptes_hand_commit_" + safeNick + ".bin";
                    }
                    java.io.File fFile = new java.io.File(Init.CORONA_DIR + panoptesFossilName);
                    if (fFile.exists()) {
                        fosil = java.nio.file.Files.readString(fFile.toPath());
                    }

                    if (fosil != null && fosil.contains("#")) {
                        String orderMap = null;
                        String[] panoptesFossilParts = fosil.split("#");
                        byte[] megaPacket = null;

                        for (String part : panoptesFossilParts) {
                            if (part.startsWith("ORDER@")) {
                                orderMap = part.substring("ORDER@".length());
                            } else if (part.startsWith("FULLMEGAPACKET@")) {
                                megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                            }
                        }

                        if (orderMap != null && megaPacket != null) {
                            this.local_mega_packet = megaPacket;

                            // Parse the explicitly provided ring order
                            String[] orderTokens = orderMap.split(",");
                            java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                            for (String token : orderTokens) {
                                if (!token.isEmpty()) {
                                    ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                }
                            }
                            this.active_crypto_ring = ringList.toArray(new String[0]);

                            // 1. Inicializa la mano (esto limpia la memoria de la bóveda C)
                            this.activeHandId = Panoptes.getInstance().stateInitializeHand();

                            // 2. INYECTAR ENTROPÍA AQUÍ (Para que sobreviva a la inicialización)
                            String entropyFileName = Init.DEV_MODE ? "/panoptes_entropy_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".bin" : "/panoptes_entropy.bin";
                            java.io.File entropyFile = new java.io.File(Init.CORONA_DIR + entropyFileName);
                            if (entropyFile.exists()) {
                                try {
                                    byte[] entropyBlob = java.nio.file.Files.readAllBytes(entropyFile.toPath());
                                    if (Panoptes.getInstance().stateImportLocalEntropy(entropyBlob)) {
                                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Local entropy restored successfully before ingest.");
                                    } else {
                                        LOGGER.log(Level.SEVERE, "[ZERO-TRUST] FATAL: Entropy file tampered or MAC invalid!");
                                    }
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Failed to restore entropy", e);
                                }
                            }

                            map.put("permutation_key", true);
                            java.util.HashMap<String, String> netPayloads = new java.util.HashMap<>();
                            String[] order = orderMap.split(",");

                            for (int i = 0; i < order.length; i++) {
                                if (order[i].isEmpty()) {
                                    continue;
                                }
                                String nick = new String(Base64.getDecoder().decode(order[i]), "UTF-8");
                                Player j = nick2player.get(nick);
                                Participant p = GameFrame.getInstance().getParticipantes().get(nick);

                                if (nick.startsWith("CoronaBot$") && p == null) {
                                    p = new Participant(GameFrame.getInstance().getSala_espera(), nick, null, null, null, null, true);
                                    GameFrame.getInstance().getParticipantes().put(nick, p);
                                }
                                boolean isBot = (p != null && p.isCpu()) || nick.startsWith("CoronaBot$");

                                // --- FIX: Declarar epub y encCards fuera del bloque if para que el else pueda usarlos ---
                                byte[] epub = new byte[32];
                                System.arraycopy(megaPacket, 49 + (i * 210) + 64, epub, 0, 32);

                                byte[] encCards = new byte[114];
                                System.arraycopy(megaPacket, 49 + (i * 210) + 96, encCards, 0, 114);
                                // ------------------------------------------------------------------------------------------

                                if (j != null) {
                                    if (p != null) {
                                        p.setEphemeral_pub_key(epub);
                                        p.setEncrypted_cards(encCards);
                                    }

                                    if (nick.equals(GameFrame.getInstance().getNick_local()) || isBot) {
                                        byte[] clear = null;

                                        if (nick.equals(GameFrame.getInstance().getNick_local())) {
                                            if (Panoptes.getInstance().stateIngestMegapacket(this.local_mega_packet, i)) {
                                                byte[] visual = Panoptes.getInstance().stateGetLocalPocketCards();
                                                if (visual != null && visual.length == 2) {
                                                    clear = new byte[98];
                                                    clear[0] = visual[0];
                                                    clear[1] = visual[1];
                                                }
                                            }
                                        } else {
                                            try {
                                                byte[] botPriv = java.security.MessageDigest.getInstance("SHA-256").digest(nick.getBytes("UTF-8"));
                                                clear = Panoptes.getInstance().utilsDecryptBotEnvelope(botPriv, epub, encCards);
                                            } catch (Exception ex) {
                                            }
                                        }

                                        if (clear != null && clear.length >= 2) {
                                            int c1 = ((int) clear[0] & 0xFF);
                                            int c2 = ((int) clear[1] & 0xFF);

                                            if (c1 < 52 && c2 < 52) {
                                                j.getHoleCard1().actualizarConValorNumerico(c1 + 1);
                                                j.getHoleCard2().actualizarConValorNumerico(c2 + 1);

                                                if (nick.equals(GameFrame.getInstance().getNick_local())) {
                                                    this.local_original_cards[0] = (byte) c1;
                                                    this.local_original_cards[1] = (byte) c2;
                                                    j.getHoleCard1().destapar(false);
                                                    j.getHoleCard2().destapar(false);
                                                } else if (isBot && p != null && clear.length >= 98) {
                                                    p.setMk_share(java.util.Arrays.copyOfRange(clear, 18, 50));
                                                    p.setToken_flop(java.util.Arrays.copyOfRange(clear, 50, 66));
                                                    p.setToken_turn(java.util.Arrays.copyOfRange(clear, 66, 82));
                                                    p.setToken_river(java.util.Arrays.copyOfRange(clear, 82, 98));
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    byte[] netChunk = new byte[146];
                                    System.arraycopy(epub, 0, netChunk, 0, 32);
                                    System.arraycopy(encCards, 0, netChunk, 32, 114);
                                    netPayloads.put(nick, Base64.getEncoder().encodeToString(netChunk));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    saltar_primera_mano = true;
                }
            } else {
                saltar_primera_mano = true;
            }
            enviarDatosClaveRecuperados(pendientes, map);
        } else {
            map = recibirDatosClaveRecuperados();

            if (map != null && map.get("hand_id") != null) {
                this.sqlite_id_hand = (int) map.get("hand_id");
            }

            try {
                String panoptesFossilName = "/panoptes_hand_commit.bin";
                if (Init.DEV_MODE) {
                    String safeNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                    panoptesFossilName = "/panoptes_hand_commit_" + safeNick + ".bin";
                }
                java.io.File fFile = new java.io.File(Init.CORONA_DIR + panoptesFossilName);
                if (fFile.exists()) {

                    // Read file as string to parse the custom format
                    String fosil = java.nio.file.Files.readString(fFile.toPath());

                    if (fosil != null && fosil.contains("#")) {
                        String orderMap = null;
                        String[] panoptesFossilParts = fosil.split("#");
                        byte[] megaPacket = null;

                        // Extract order and megapacket data
                        for (String part : panoptesFossilParts) {
                            if (part.startsWith("ORDER@")) {
                                orderMap = part.substring("ORDER@".length());
                            } else if (part.startsWith("FULLMEGAPACKET@")) {
                                megaPacket = Base64.getDecoder().decode(part.substring("FULLMEGAPACKET@".length()));
                            }
                        }

                        if (orderMap != null && megaPacket != null) {
                            this.local_mega_packet = megaPacket;

                            // Parse the explicitly provided ring order for the client
                            String[] orderTokens = orderMap.split(",");
                            java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                            for (String token : orderTokens) {
                                if (!token.isEmpty()) {
                                    ringList.add(new String(java.util.Base64.getDecoder().decode(token), "UTF-8"));
                                }
                            }
                            this.active_crypto_ring = ringList.toArray(new String[0]);

                            // 1. Inicializa la mano (esto limpia la memoria de la bóveda C)
                            this.activeHandId = Panoptes.getInstance().stateInitializeHand();

                            // 2. INYECTAR ENTROPÍA AQUÍ (Para que sobreviva a la inicialización)
                            String entropyFileName = Init.DEV_MODE ? "/panoptes_entropy_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".bin" : "/panoptes_entropy.bin";
                            java.io.File entropyFile = new java.io.File(Init.CORONA_DIR + entropyFileName);
                            if (entropyFile.exists()) {
                                try {
                                    byte[] entropyBlob = java.nio.file.Files.readAllBytes(entropyFile.toPath());
                                    if (Panoptes.getInstance().stateImportLocalEntropy(entropyBlob)) {
                                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Local entropy restored successfully before ingest.");
                                    } else {
                                        LOGGER.log(Level.SEVERE, "[ZERO-TRUST] FATAL: Entropy file tampered or MAC invalid!");
                                    }
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Failed to restore entropy", e);
                                }
                            }

                            int myPos = calcularPosicionEnPaquete(GameFrame.getInstance().getNick_local());

                            if (myPos != -1) {
                                if (Panoptes.getInstance().stateIngestMegapacket(this.local_mega_packet, myPos)) {
                                    byte[] visual = Panoptes.getInstance().stateGetLocalPocketCards();
                                    if (visual != null && visual.length == 2) {
                                        this.local_original_cards[0] = visual[0];
                                        this.local_original_cards[1] = visual[1];

                                        Player myPlayer = GameFrame.getInstance().getLocalPlayer();
                                        myPlayer.getHoleCard1().iniciarConValorNumerico((visual[0] & 0xFF) + 1);
                                        myPlayer.getHoleCard2().iniciarConValorNumerico((visual[1] & 0xFF) + 1);
                                        myPlayer.getHoleCard1().destapar(false);
                                        myPlayer.getHoleCard2().destapar(false);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to recover client panoptesFossil data", e);
            }

            if (map == null || !Boolean.TRUE.equals(map.get("permutation_key")) || (map.get("hand_end") != null && (Long) map.get("hand_end") != 0L)) {
                saltar_primera_mano = true;
            } else {
                this.game_recovered = 1;
            }
        }

        if (map != null) {
            this.sqlite_id_hand = map.get("hand_id") != null ? (int) map.get("hand_id") : -1;
            GameFrame.BUYIN = map.get("buyin") != null ? (int) map.get("buyin") : 100;
            GameFrame.REBUY = map.get("rebuy") != null ? (boolean) map.get("rebuy") : true;
            this.conta_mano = map.get("conta_mano") != null ? (int) map.get("conta_mano") : 1;
            this.ciega_pequeña = map.get("sbval") != null ? (float) map.get("sbval") : 0.1f;
            this.ciega_grande = map.get("bbval") != null ? (float) map.get("bbval") : 0.2f;
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
                    } catch (Exception e) {
                    }
                }
                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (!nicksRec.contains(j.getNickname())) {
                        j.setSpectator(Translator.translate("game.calentando"));
                        this.auditor.put(j.getNickname(), new Float[]{j.getStack(), (float) j.getBuyin()});
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

            if (this.tot_acciones_recuperadas > 0) {
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
        LOGGER.log(Level.WARNING, "[ZERO-TRUST] MISDEAL ACTIVADO: {0}", motivo);
        GameFrame.getInstance().getRegistro().print(Translator.translate("ame.mano_anulada") + motivo);
        GameFrame.getInstance().getRegistro().print(Translator.translate("game.mano_anulada_footer"));

        synchronized (getLock_contabilidad()) {
            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                if (jugador.getBet() > 0) {
                    jugador.setStack(jugador.getStack() + jugador.getBet());
                    jugador.setBet(0f);
                }
            }
            this.apuestas = 0f;
            this.bote_total = 0f;
            this.bote = new HandPot(0f); // Reiniciamos el bote
        }

        Audio.playWavResource("misc/error.wav");

        Helpers.GUIRun(() -> {
            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("game.mano_anulada") + motivo + Translator.translate("game.mano_anulada_footer"));
        });
    }

    private void recibirPosiciones() {

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("POSITIONS")) {

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

                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

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

    private boolean checkDoblarCiegas() {

        synchronized (lock_ciegas) {
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
        received_commands.clear();

        this.activeHandId = Panoptes.getInstance().stateInitializeHand();

        byte[] external_entropy = null;

        if (GameFrame.getInstance().isPartida_local()) {

            if (Helpers.DECK_RANDOM_GENERATOR == Helpers.TRNG_CSPRNG || Helpers.DECK_RANDOM_GENERATOR == Helpers.TRNG) {
                external_entropy = Helpers.getRandomOrgBytes(32);
            } else if (Helpers.CSPRNG_GENERATOR != null) {
                external_entropy = new byte[32];
                Helpers.CSPRNG_GENERATOR.nextBytes(external_entropy);
            }

        } else {
            if (Helpers.CSPRNG_GENERATOR != null) {
                external_entropy = new byte[32];
                Helpers.CSPRNG_GENERATOR.nextBytes(external_entropy);
            }
        }

        this.local_hand_seed = Panoptes.getInstance().stateGenerateLocalSeed(external_entropy);

        // --- V73: EXPORT LOCAL ENTROPY TO DISK ---
        // CRITICAL FIX: Do not overwrite the old entropy file if we are in the middle of a recovery!
        if (!GameFrame.isRECOVER()) {
            try {
                byte[] entropyBlob = Panoptes.getInstance().stateExportLocalEntropy();
                if (entropyBlob != null && entropyBlob.length == 48) {
                    String entropyFileName = Init.DEV_MODE ? "/panoptes_entropy_" + GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_") + ".bin" : "/panoptes_entropy.bin";
                    java.nio.file.Files.write(java.nio.file.Paths.get(Init.CORONA_DIR + entropyFileName), entropyBlob);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to save entropy state", e);
            }
        }
        // -----------------------------------------

        if (GameFrame.getInstance().isPartida_local()) {

            for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {
                Participant p = entry.getValue();
                if (p != null && p.isCpu()) {
                    byte[] botSeed = new byte[32];
                    if (Helpers.CSPRNG_GENERATOR != null) {
                        Helpers.CSPRNG_GENERATOR.nextBytes(botSeed);
                    }
                    p.setPanoptes_hand_seed(botSeed);
                }
            }

            boolean ready;
            int timeout = 0;
            boolean[] timeout_msg = new boolean[1];
            timeout_msg[0] = false;

            do {
                ready = true;
                for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {
                    Participant p = entry.getValue();
                    if (p != null && !p.isCpu() && !p.isExit() && p.getNew_hand_ready() <= this.conta_mano) {
                        ready = false;
                        if (timeout == NEW_HAND_READY_WAIT_TIMEOUT) {
                            LOGGER.log(Level.WARNING,
                                    "{0} -> NEW HAND ({1}) CONFIRMATION TIMEOUT!",
                                    new Object[]{p.getNick(), String.valueOf(this.conta_mano + 1)});
                            nick2player.get(p.getNick()).setTimeout(true);
                            if (!p.isForce_reset_socket()) {
                                try {
                                    this.broadcastGAMECommandFromServer(
                                            "TIMEOUT#"
                                            + Base64.getEncoder().encodeToString(p.getNick().getBytes("UTF-8")),
                                            p.getNick(), false);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }

                if (!ready) {
                    timeout += NEW_HAND_READY_WAIT;
                    if (timeout <= NEW_HAND_READY_WAIT_TIMEOUT) {
                        synchronized (lock_nueva_mano) {
                            try {
                                lock_nueva_mano.wait(NEW_HAND_READY_WAIT);
                            } catch (InterruptedException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }
                    } else {
                        Helpers.threadRun(() -> {
                            if (!timeout_msg[0]) {
                                timeout_msg[0] = true;
                                Helpers.mostrarMensajeError(GameFrame.getInstance(),
                                        "HAY JUGADORES QUE NO HAN CONFIRMADO LA NUEVA MANO (SEGUIMOS ESPERANDO...)");
                                timeout_msg[0] = false;
                            }
                        });
                        timeout = 0;
                    }
                }
            } while (!ready);

        } else {
            String seedB64 = Base64.getEncoder().encodeToString(this.local_hand_seed);
            this.sendGAMECommandToServer("NEWHANDREADY#" + String.valueOf(this.conta_mano + 1) + "#" + seedB64);
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

                    if (GameFrame.RABBIT_HUNTING == 2 && conta_rabbit > 1) {
                        coste_rabbit = ciega_pequeña;
                        bote_sobrante += coste_rabbit;
                        jugador.setStack(stack - coste_rabbit);
                    } else if (GameFrame.RABBIT_HUNTING == 3) {
                        if (conta_rabbit == 2) {
                            coste_rabbit = ciega_pequeña;
                            bote_sobrante += coste_rabbit;
                            jugador.setStack(stack - coste_rabbit);
                        } else if (conta_rabbit > 2) {
                            coste_rabbit = ciega_grande;
                            bote_sobrante += coste_rabbit;
                            jugador.setStack(stack - coste_rabbit);
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

        iwtsthing = true;

        Helpers.threadRun(() -> {

            if (!iwtsth) {

                iwtsth = true;

                synchronized (lock_iwtsth) {

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

            }

        });

    }

    public void IWTSTH_SHOW(String iwtsther, boolean authorized) {
        if (iwtsthing) {
            Helpers.threadRun(() -> {
                synchronized (lock_iwtsth) {
                    if (this.iwtsthing) {
                        if (GameFrame.getInstance().isPartida_local()) {
                            try {
                                broadcastGAMECommandFromServer("IWTSTHSHOW#" + Base64.getEncoder().encodeToString(iwtsther.getBytes("UTF-8")) + "#" + String.valueOf(authorized), null, true);
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                        if (authorized) {
                            synchronized (lock_mostrar) {
                                for (RemotePlayer rp : GameFrame.getInstance().getTapete().getRemotePlayers()) {
                                    if (rp.isIwtsthCandidate()) {
                                        if (rp.getHoleCard1().getValor() == null || rp.getHoleCard1().getValor().isEmpty() || rp.getHoleCard1().getValor().equals("null")) {
                                            if (this.valid_master_key == null || this.valid_master_key.length == 0 || !extractHandWithMasterKey(rp)) {
                                                continue;
                                            }
                                        }
                                        rp.destaparCartas(true);
                                        ArrayList<Card> cartas_jugada = new ArrayList<>(rp.getHoleCards());
                                        String hole_cards_string = Card.collection2String(rp.getHoleCards());

                                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!carta_comun.isTapada()) {
                                                cartas_jugada.add(carta_comun);
                                            }
                                        }

                                        Hand jugada = null;
                                        try {
                                            jugada = new Hand(cartas_jugada);
                                            rp.showCards(jugada.getName());
                                        } catch (Exception e) {
                                        }

                                        GameFrame.getInstance().getRegistro().print("IWTSTH (" + iwtsther + ") -> " + rp.getNickname() + Translator.translate("ui.muestra_2") + hole_cards_string + ")" + (jugada != null ? " -> " + jugada : ""));
                                        sqlNewShowcards(rp.getNickname(), rp.getDecision() == Player.FOLD);
                                        sqlUpdateShowdownHand(rp, jugada);
                                    }
                                }
                                setTiempo_pausa(GameFrame.TEST_MODE ? PAUSA_ENTRE_MANOS_TEST : PAUSA_ENTRE_MANOS);

                                if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {
                                    if (GameFrame.getInstance().getLocalPlayer().isLoser()) {
                                        ArrayList<Card> cartas_jugada = new ArrayList<>(GameFrame.getInstance().getLocalPlayer().getHoleCards());
                                        String hole_cards_string = Card.collection2String(GameFrame.getInstance().getLocalPlayer().getHoleCards());

                                        for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {
                                            if (!carta_comun.isTapada()) {
                                                cartas_jugada.add(carta_comun);
                                            }
                                        }

                                        Hand jugada = null;
                                        try {
                                            jugada = new Hand(cartas_jugada);
                                        } catch (Exception e) {
                                        }

                                        // FIX COMPILACIÓN: Creamos una referencia final para la lambda
                                        final Hand final_jugada = jugada;

                                        Helpers.GUIRunAndWait(() -> {
                                            GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setForeground(Color.WHITE);
                                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setBackground(new Color(51, 153, 255));
                                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setText(Translator.translate("ui.muestras") + (final_jugada != null ? final_jugada.getName() : "") + ")");
                                        });

                                        GameFrame.getInstance().getLocalPlayer().setMuestra(true);
                                        GameFrame.getInstance().getRegistro().print("IWTSTH (" + iwtsther + ") -> " + GameFrame.getInstance().getLocalPlayer().getNickname() + Translator.translate("ui.muestra_2") + hole_cards_string + ")" + (jugada != null ? " -> " + jugada : ""));
                                        sqlNewShowcards(GameFrame.getInstance().getLocalPlayer().getNickname(), GameFrame.getInstance().getLocalPlayer().getDecision() == Player.FOLD);
                                        sqlUpdateShowdownHand(GameFrame.getInstance().getLocalPlayer(), jugada);
                                    }
                                }
                            }
                        } else {
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
                Helpers.GUIRunAndWait(() -> {
                    if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {
                        GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(true);
                    }
                    GameFrame.getInstance().getTapete().getCommunityCards().getBarra_tiempo().setIndeterminate(false);
                });
                iwtsth = true;
                iwtsthing = false;
                iwtsthing_request = false;
                synchronized (lock_iwtsth) {
                    lock_iwtsth.notifyAll();
                }
            });
        }
    }

    public boolean isIwtsthing_request() {
        return iwtsthing_request;
    }

    public void IWTSTH_REQUEST(String iwtsther) {

        if (this.show_time && this.last_iwtsth_rejected == null
                || System.currentTimeMillis() - this.last_iwtsth_rejected > IWTSTH_ANTI_FLOOD_TIME) {

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
                }
                // Swing se encarga del repaint de forma fluida.
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

        this.active_crypto_ring = null;
        this.game_recovered = 0;
        this.legitHand = false;
        this.verified_hand = false;

        Helpers.GUIRun(() -> {
            GameFrame.getInstance().getTapete().getCommunityCards().restoreBetLabelicon();
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_panel().setOpaque(false);
            GameFrame.getInstance().getTapete().getCommunityCards().getPot_label()
                    .setHorizontalAlignment(JLabel.LEADING);
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

        // ZERO-TRUST: Delete previous hand ID and Binary Log to start clean
        try {
            String fileName = "/hand.id";
            if (Init.DEV_MODE) {
                String safeNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                fileName = "/hand_" + safeNick + ".id";
            }
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(Init.CORONA_DIR + fileName));
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(Init.CORONA_DIR + "/hand_" + this.sqlite_id_hand + ".bin"));
        } catch (Exception e) {
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
                            "LA CONFIGURACIÓN DE LAS CIEGAS SE HA ACTUALIZADO",
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

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (!jugador.isExit() && jugador.isSpectator() && (Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0
                    || rebuy_now.containsKey(jugador.getNickname()))) {
                jugador.unsetSpectator();
                if (rebuy_now.containsKey(jugador.getNickname())) {
                    jugador.setSpectatorBB(true);
                }
            }
        }

        // [CRITICAL FIX]: Ensure nicks_permutados accurately reflects the newly integrated players.
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

        this.conta_mano++;

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
        this.street = PREFLOP;
        this.cartas_resistencia = false;
        this.destapar_resistencia = false;
        this.ultimo_raise = 0f;
        this.partial_raise_cum = 0f;
        this.conta_raise = 0;
        this.conta_bet = 0;

        if (Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {
            if (GameFrame.SONIDOS_CHORRA && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                Audio.playWavResource("misc/indivisible.wav");
            }
            Audio.playWavResource("misc/cash_register.wav");
            GameFrame.getInstance().getRegistro()
                    .print(Translator.translate("game.bote_sobrante") + " -> " + Helpers.float2String(bote_sobrante));
        }

        this.bote_total = Math.max(0f, this.bote_sobrante);
        this.bote = new HandPot(0f);
        this.beneficio_bote_principal = null;

        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            if (jugador.isActivo()) {
                jugador.nuevaMano();
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
            recuperarDatosClavePartida();
        }

        this.apuesta_actual = this.ciega_grande;

        // --- TRACK NEW HAND FOR ALL PLAYERS ---
        for (Player p : GameFrame.getInstance().getJugadores()) {
            if (p.isActivo()) {
                Bot.TRACKER_MEMORY.putIfAbsent(p.getNickname(), new Bot.OpponentTracker());
                Bot.TRACKER_MEMORY.get(p.getNickname()).recordHandPlayed();
            }
        }
        // --------------------------------------

        if (getJugadoresActivos() > 1 && !saltar_primera_mano) {
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
            barajando = true;

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
                    GameFrame.getInstance().getTapete().showCentralImage(icon, 0, SHUFFLE_ANIMATION_DELAY, true,
                            "misc/shuffle.wav", 1, 53);
                    if (!isFin_de_la_transmision()) {
                        Helpers.GUIRunAndWait(() -> {
                            GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                        });
                    }
                } else if (!isFin_de_la_transmision()) {
                    Helpers.GUIRunAndWait(() -> {
                        GameFrame.getInstance().getTapete().getCommunityCards().setVisible(true);
                    });
                    Audio.playWavResource("misc/shuffle.wav");
                    Helpers.pausar(GIF_SHUFFLE_ANIMATION_TIMEOUT);
                    Audio.stopWavResource("misc/shuffle.wav");
                }
                barajando = false;
                synchronized (shuffle_lock) {
                    shuffle_lock.notifyAll();
                }
            });

            if (GameFrame.getInstance().isPartida_local() && this.game_recovered == 0) {
                enviarCartasJugadoresRemotos();
                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (j != GameFrame.getInstance().getLocalPlayer()) {
                        j.ordenarCartas();
                    }
                }
            } else if (!GameFrame.getInstance().isPartida_local()
                    && !GameFrame.getInstance().getLocalPlayer().isCalentando() && this.game_recovered == 0) {
                cartas_locales_recibidas = recibirMisCartas();
            }

            if (barajando) {
                do {
                    synchronized (shuffle_lock) {
                        try {
                            shuffle_lock.wait(1000);
                        } catch (InterruptedException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                } while (barajando);
            }

            repartir();
            Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);
            Helpers.GUIRun(() -> {
                GameFrame.getInstance().getExit_menu().setEnabled(true);
            });
            disableAllPlayersTimeout();
            return true;

        } else {
            permutacion_recuperada = null;
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

            if (this.conta_mano == 1) {
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador.isActivo() || Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0) {
                        this.sqlNewHandBalance(jugador.getNickname(), jugador.getStack() + jugador.getBet(), jugador.getBuyin());
                    }
                }
            } else {
                for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {
                    Player jugador = nick2player.get(entry.getKey());
                    if (jugador != null) {
                        if (jugador.isActivo() || Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0) {
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
            // [CRITICAL FIX]: Ensure active player exists before writing their action
            ensurePlayerExists(current_player.getNickname());

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
                                + "|" + String.valueOf(jugador.getBuyin());
                    } else {

                        Float[] pasta = entry.getValue();
                        sqlUpdateHandBalance(entry.getKey(), pasta[0], Math.round(pasta[1]));
                        balance_float[i] = Base64.getEncoder().encodeToString(entry.getKey().getBytes("UTF-8")) + "|"
                                + String.valueOf(pasta[0]) + "|" + String.valueOf(Math.round(pasta[1]));
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
                        .prepareStatement("INSERT INTO balance(id_hand, player, stack, buyin) VALUES (?,?,?,?)");
                statement.setQueryTimeout(30);
                statement.setInt(1, this.sqlite_id_hand);
                statement.setString(2, nick);
                statement.setFloat(3, Helpers.floatClean(stack));
                statement.setInt(4, buyin);
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
                        .prepareStatement("UPDATE balance SET stack=?, buyin=? WHERE id_hand=? and player=?");
                statement.setQueryTimeout(30);
                statement.setFloat(1, Helpers.floatClean(stack));
                statement.setInt(2, buyin);
                statement.setInt(3, this.sqlite_id_hand);
                statement.setString(4, nick);
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

        while (!GameFrame.getInstance().getJugadores().get(i).getNickname().equals(this.dealer_nick)) {
            i++;
        }

        int j, pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();

        j = pivote;

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && animacion) {

                Audio.playWavResource("misc/deal.wav", false);

                if (jugador == GameFrame.getInstance().getLocalPlayer()) {

                    // V54 FIX: Las cartas ya se extrajeron de la bóveda C, las seteamos aquí
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

    private boolean recibirShares(ArrayList<String> pendientes, ArrayList<Player> inShowdown) {
        boolean timeout = false;
        long start_time = System.currentTimeMillis();
        do {
            synchronized (this.getReceived_commands()) {
                ArrayList<String> rejected = new ArrayList<>();
                while (!pendientes.isEmpty() && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    // PURE ZERO-TRUST: GAME#ID#SHOWDOWN_REVEAL#NICK#SHARE (No more plain text cards here)
                    if (partes.length >= 5 && partes[2].equals("SHOWDOWN_REVEAL")) {
                        try {
                            String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                            if (pendientes.contains(nick)) {
                                byte[] share = Base64.getDecoder().decode(partes[4]);
                                Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                                if (p != null) {
                                    p.setMk_share(share);
                                }
                                pendientes.remove(nick);
                            } else {
                                rejected.add(comando);
                            }
                        } catch (Exception ex) {
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
                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                    timeout = true;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!pendientes.isEmpty() && !timeout);

        return timeout;
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
                            case "SHOWDOWN_REQ":

                                try {
                                    this.local_mk_share = Panoptes.getInstance().stateGetShuffleKeyShare();
                                    if (this.local_mk_share != null) {
                                        String shareBase64 = Base64.getEncoder().encodeToString(this.local_mk_share);
                                        // PURE ZERO-TRUST: NO plaintext cards appended
                                        String responseCmd = "SHOWDOWN_REVEAL#"
                                                + Base64.getEncoder().encodeToString(GameFrame.getInstance().getNick_local().getBytes("UTF-8"))
                                                + "#" + shareBase64;

                                        sendGAMECommandToServer(responseCmd, false);
                                    }
                                } catch (Exception e) {
                                }
                                break;

                            case "SHOWDOWN_REVEAL":
                                if (partes.length >= 5) {
                                    try {
                                        String nick = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                        Player jugadorRemoto = nick2player.get(nick);

                                        if (jugadorRemoto != null && !jugadorRemoto.equals(GameFrame.getInstance().getLocalPlayer())) {
                                            Participant p = GameFrame.getInstance().getParticipantes().get(nick);
                                            if (p != null) {
                                                p.setMk_share(Base64.getDecoder().decode(partes[4]));
                                            }
                                        }
                                    } catch (Exception ex) {
                                    }
                                }
                                break;

                            case "HANDVERIFY":
                                if (partes[3].equals("SKIPPED")) {
                                    this.valid_master_key = new byte[0];

                                    this.verified_hand = true;

                                    synchronized (this.lock_hand_verification) {
                                        this.lock_hand_verification.notifyAll();
                                    }
                                } else {
                                    this.valid_master_key = Base64.getDecoder().decode(partes[3]);

                                    // ====================================================================
                                    // PURE ZERO-TRUST EXTRACTION FOR CLIENTS
                                    // Forcefully extract real cards for humans before evaluating
                                    // ====================================================================
                                    if (!GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getLocalPlayer().isCalentando()) {
                                        for (Player jugador : inShowdown) {
                                            if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {
                                                extractHandWithMasterKey(jugador);
                                                jugador.ordenarCartas();
                                            }
                                        }
                                    }
                                    // ====================================================================

                                    Helpers.threadRun(() -> {
                                        Panoptes.getInstance().chainCloseStateAndGetReceipt();
                                        GameFrame.getInstance().getCrupier().verificarManoLocal(this.valid_master_key, partes);
                                    });
                                }
                                consensus_ok = true;
                                break;

                            case "MISDEAL":
                                String motivo = "";
                                try {
                                    motivo = new String(Base64.getDecoder().decode(partes[3]), "UTF-8");
                                } catch (Exception e) {
                                }
                                cancelarManoYDevolverApuestas(motivo);
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
                if (GameFrame.getInstance().checkPause()) {

                    start_time = System.currentTimeMillis();

                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {

                    GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.fast_consensus_timeout"));

                    synchronized (this.lock_hand_verification) {
                        this.lock_hand_verification.notifyAll();
                    }

                    consensus_ok = true;

                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!consensus_ok && !isFin_de_la_transmision());
    }

    private void requestShowdownKeys(ArrayList<Player> inShowdown) {
        if (!GameFrame.getInstance().isPartida_local()) {
            if (this.local_mega_packet != null) {
                Helpers.threadRun(() -> {
                    recibirConsensoFinal(inShowdown);
                });
            }
            return;
        }

        java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
        java.util.ArrayList<Player> ringCriptografico = getAnilloCriptografico();

        for (Player jugador : ringCriptografico) {
            if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {
                Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                if (p != null && !p.isCpu() && p.getMk_share() == null) {
                    pendientes.add(jugador.getNickname());
                }
            }
        }

        if (!pendientes.isEmpty() && GameFrame.getInstance().isPartida_local()) {
            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            do {
                String command = "GAME#" + String.valueOf(id) + "#SHOWDOWN_REQ";
                for (Player jugador : ringCriptografico) {
                    if (pendientes.contains(jugador.getNickname())) {
                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                        if (p != null && !p.isCpu()) {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        }
                    }
                }

                java.util.ArrayList<String> pendientes_conf = new java.util.ArrayList<>(pendientes);
                this.waitSyncConfirmations(id, pendientes_conf);
                boolean timeout = recibirShares(pendientes, inShowdown);

                if (timeout && !pendientes.isEmpty()) {
                    for (String nick : pendientes) {
                        if (!nick2player.get(nick).isExit()) {
                            this.remotePlayerQuit(nick);
                        }
                    }
                    pendientes.clear();
                }
            } while (!pendientes.isEmpty());
        }

        int numPlayersInPacket = this.local_mega_packet[16] & 0xFF;
        for (Player jugador : ringCriptografico) {
            Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
            boolean isHost = jugador.getNickname().equals(GameFrame.getInstance().getNick_local());
            boolean isBot = (p != null && p.isCpu());
            boolean isZombie = jugador.isExit();

            if (isHost || isBot || isZombie) {
                if (isHost) {
                    // BLINDAJE: NO TOCAMOS AL HOST. 
                } else {
                    try {
                        int pos = calcularPosicionEnPaquete(jugador.getNickname());
                        if (pos != -1) {
                            byte[] epub = new byte[32];
                            System.arraycopy(this.local_mega_packet, 49 + (pos * 210) + 64, epub, 0, 32);

                            byte[] encCards = new byte[114];
                            System.arraycopy(this.local_mega_packet, 49 + (pos * 210) + 96, encCards, 0, 114);

                            byte[] botPriv = java.security.MessageDigest.getInstance("SHA-256").digest(jugador.getNickname().getBytes("UTF-8"));
                            byte[] clear = Panoptes.getInstance().utilsDecryptBotEnvelope(botPriv, epub, encCards);

                            if (clear != null && clear.length >= 98) {
                                byte[] mkS = new byte[32];
                                System.arraycopy(clear, 18, mkS, 0, 32);
                                if (p != null) {
                                    p.setMk_share(mkS);
                                }

                                if (!isZombie && inShowdown.contains(jugador)) {
                                    jugador.getHoleCard1().actualizarConValorNumerico(((int) clear[0] & 0xFF) + 1);
                                    jugador.getHoleCard2().actualizarConValorNumerico(((int) clear[1] & 0xFF) + 1);
                                    jugador.ordenarCartas();
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }

        this.local_mk_share = Panoptes.getInstance().stateGetShuffleKeyShare();

        byte[] finalMasterKey = new byte[32];
        int sharesXORed = 0;

        if (this.local_mk_share != null) {
            for (int i = 0; i < 32; i++) {
                finalMasterKey[i] ^= this.local_mk_share[i];
            }
            sharesXORed++;
        }
        for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
            if (p != null && p.getMk_share() != null && !p.getNick().equals(GameFrame.getInstance().getNick_local())) {
                for (int k = 0; k < 32; k++) {
                    finalMasterKey[k] ^= p.getMk_share()[k];
                }
                sharesXORed++;
            }
        }

        if (sharesXORed == numPlayersInPacket) {
            this.valid_master_key = finalMasterKey;

            // ====================================================================
            // PURE ZERO-TRUST EXTRACTION FOR HOST
            // Forcefully extract real cards for humans before sending POTCARDS
            // ====================================================================
            for (Player jugador : inShowdown) {
                if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {
                    Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());
                    if (p != null && !p.isCpu()) {
                        extractHandWithMasterKey(jugador);
                        jugador.ordenarCartas();
                    }
                }
            }
            // ====================================================================

            String mkBase64 = java.util.Base64.getEncoder().encodeToString(finalMasterKey);

            Helpers.threadRun(() -> {
                byte[] myLocalReceipt = Panoptes.getInstance().chainCloseStateAndGetReceipt();
                String myReceiptStr = myLocalReceipt != null ? java.util.Base64.getEncoder().encodeToString(myLocalReceipt) : "*";
                StringBuilder allReceipts = new StringBuilder();
                allReceipts.append(myReceiptStr);

                for (Participant p : GameFrame.getInstance().getParticipantes().values()) {
                    if (p != null && p.getChain_receipt() != null && !p.getNick().equals(GameFrame.getInstance().getNick_local())) {
                        try {
                            String rB64 = java.util.Base64.getEncoder().encodeToString(p.getChain_receipt());
                            String nB64 = java.util.Base64.getEncoder().encodeToString(p.getNick().getBytes("UTF-8"));
                            allReceipts.append("#").append(nB64).append(":").append(rB64);
                        } catch (Exception e) {
                        }
                    }
                }

                String verifyCommand = "HANDVERIFY#" + mkBase64 + "#" + allReceipts.toString();
                broadcastGAMECommandFromServer(verifyCommand, null, false);
                this.verificarManoLocal(finalMasterKey, verifyCommand.split("#"));
            });

        } else {
            LOGGER.log(Level.WARNING, "❌ [ZERO-TRUST WARN] Disconnected player without Testament. Missing shares.");
            this.valid_master_key = new byte[0];
            broadcastGAMECommandFromServer("HANDVERIFY#SKIPPED", null, false);
        }
    }

    private HashMap<String, Object> recibirDatosClaveRecuperados() {

        HashMap<String, Object> map = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("RECOVERDATA")) {

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
                            try {
                                in.close();
                            } catch (IOException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }

                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return map;
    }

    private String recibirAccionesRecuperadas() {

        String actions = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("ACTIONDATA")) {

                        ok = true;

                        try {
                            actions = !"*".equals(partes[3])
                                    ? new String(Base64.getDecoder().decode(partes[3]), "UTF-8")
                                    : "";
                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    } else {
                        rejected.add(comando);
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
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return actions;
    }

    public void enviarDatosClaveRecuperados(ArrayList<String> pendientes, HashMap<String, Object> datos) {

        long start = System.currentTimeMillis();

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        boolean timeout = false;

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

                            p.writeCommandFromServer(
                                    Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));

                        }
                    }
                }

                // Esperamos confirmaciones
                this.waitSyncConfirmations(id, pendientes);

                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {

                    // 0=yes, 1=no, 2=cancel
                    if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                            Translator.translate("game.forzamos_reset_socket"),
                            new ImageIcon(getClass().getResource("/images/action/timeout.png"))) == 0) {
                        for (String nick : pendientes) {
                            GameFrame.getInstance().getParticipantes().get(nick).forceSocketReconnect();
                        }

                    }
                    start = System.currentTimeMillis();

                }
                if (!pendientes.isEmpty()) {

                    for (String nick : pendientes) {

                        nick2player.get(nick).setTimeout(true);

                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer(
                                        "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick,
                                        false);
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    out.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

        } while (!pendientes.isEmpty() && !timeout);

        if (timeout) {

            for (String nick : pendientes) {
                if (!nick2player.get(nick).isExit()) {
                    this.remotePlayerQuit(nick);
                }
            }
        }

    }

    public void enviarAccionesRecuperadas(ArrayList<String> pendientes, String datos) {

        long start = System.currentTimeMillis();

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        boolean timeout = false;

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

                            p.writeCommandFromServer(
                                    Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));

                        }
                    }
                }

                // Esperamos confirmaciones
                this.waitSyncConfirmations(id, pendientes);

                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {

                    // 0=yes, 1=no, 2=cancel
                    if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                            Translator.translate("game.forzamos_reset_socket"),
                            new ImageIcon(getClass().getResource("/images/action/timeout.png"))) == 0) {
                        for (String nick : pendientes) {
                            GameFrame.getInstance().getParticipantes().get(nick).forceSocketReconnect();
                        }

                    }
                    start = System.currentTimeMillis();

                }
                if (!pendientes.isEmpty()) {

                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);

                        if (!GameFrame.getInstance().getParticipantes().get(nick).isForce_reset_socket()) {
                            try {
                                this.broadcastGAMECommandFromServer(
                                        "TIMEOUT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")), nick,
                                        false);
                            } catch (UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }

                        }
                    }

                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } while (!pendientes.isEmpty() && !timeout);

        if (timeout) {

            for (String nick : pendientes) {
                if (!nick2player.get(nick).isExit()) {
                    this.remotePlayerQuit(nick);
                }
            }
        }

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

    public void saveRADARLog(String suspicious, byte[] image, String process_list, long timestamp) {

        Helpers.threadRun(() -> {
            RemotePlayer jugador = (RemotePlayer) nick2player.get(suspicious);
            int[] a = new int[]{0};
            Helpers.threadRun(() -> {
                Helpers.mostrarMensajeInformativo(GameFrame.getInstance(),
                        Translator.translate("radar.se_ha_recibido_un_informe") + suspicious
                        + Translator.translate(
                                "]\n\n(Por seguridad no podrás verlo hasta que termine la mano en curso)."),
                        new ImageIcon(Init.class.getResource("/images/shield.png")));

                a[0] = 1;

                if (jugador.getRadar_dialog() != null
                        && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                Translator.translate("radar.informe_anticheat_de_2") + suspicious
                                + Translator.translate("ui.disponible_quieres_verlo_2"),
                                new ImageIcon(Init.class.getResource("/images/shield.png"))) == 0
                        && !isFin_de_la_transmision()) {

                    jugador.getRadar_dialog().setLocationRelativeTo(GameFrame.getInstance());
                    jugador.getRadar_dialog().setVisible(true);
                }
            });
            while (!isShow_time()) {
                Helpers.pausar(1000);
            }
            try {
                String fecha = Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss");
                String path = Init.RADAR_DIR + "/" + suspicious + "_" + fecha;

                if (image != null && Helpers.OSValidator.isWindows()) {
                    //Sólo se permite ver el screenshot sí tú también tienes Windows para que sea justo
                    Files.write(Paths.get(path + ".jpg"), image);
                }

                Files.write(Paths.get(path + ".log"), process_list.getBytes("UTF-8"));
                Helpers.GUIRun(() -> {
                    jugador.setRadar_dialog(new RadarLogDialog(GameFrame.getInstance(), false, path, timestamp));

                    if (a[0] == 1 && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                            Translator.translate("radar.informe_anticheat_de_2") + suspicious
                            + Translator.translate("ui.disponible_quieres_verlo_2"),
                            new ImageIcon(Init.class.getResource("/images/shield.png"))) == 0) {

                        jugador.getRadar_dialog().setLocationRelativeTo(GameFrame.getInstance());
                        jugador.getRadar_dialog().setVisible(true);
                    }
                });
                jugador.setRadar_checking(false);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

    }

    public boolean isRadarChecking() {

        for (RemotePlayer j : GameFrame.getInstance().getTapete().getRemotePlayers()) {
            if (j.isRadar_checking()) {
                return true;
            }
        }

        return false;
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
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                }
            }

        }

        return !pending.isEmpty();
    }

    public Object[] readActionFromRemotePlayer(Player jugador) {
        long start = System.currentTimeMillis();
        boolean ok = false, timeout = false;
        Object[] action = new Object[3]; // [0] decision, [1] bet (o cinemática en ALLIN), [2] packetB64

        do {
            ok = false;
            timeout = false;

            if (!jugador.isExit()) {
                synchronized (this.getReceived_commands()) {
                    java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                    while (!ok && !this.getReceived_commands().isEmpty()) {
                        String comando = this.received_commands.poll();
                        String[] partes = comando.split("#");

                        if (!jugador.isExit()) {
                            try {
                                // FORMATO UNIFICADO: GAME#ID#ACTION#NICK_B64#DECISION#[BET]#PACKET#[CINEMATICA]
                                if (partes.length >= 5 && partes[2].equals("ACTION")) {
                                    String senderNick = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");

                                    if (senderNick.equals(jugador.getNickname())) {
                                        ok = true;
                                        action[0] = Integer.valueOf(partes[4]); // Decision

                                        if ((Integer) action[0] == Player.BET) {
                                            action[1] = Float.valueOf(partes[5]); // Cantidad
                                            action[2] = (partes.length > 6 && !partes[6].isEmpty()) ? partes[6] : null; // Packet
                                        } else {
                                            action[1] = 0f;
                                            action[2] = (partes.length > 5 && !partes[5].isEmpty()) ? partes[5] : null; // Packet

                                            // Cinemática en ALLIN
                                            if ((Integer) action[0] == Player.ALLIN && partes.length > 6 && !partes[6].isEmpty()) {
                                                action[1] = partes[6];
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, "Error parseando acción remota", ex);
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
                    if (GameFrame.getInstance().checkPause()) {
                        start = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {
                        timeout = true;
                    } else {
                        synchronized (this.getReceived_commands()) {
                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                            }
                        }
                    }
                }
            }
        } while (!ok && !jugador.isExit() && !timeout);

        if (jugador.isExit()) {
            action[0] = -1;
            action[1] = 0f;
            action[2] = null;
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
        java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.INFO, "[PANOPTES] Initiating street unlock: {0}", street);

        Object[] tokenResult = recopilarTokens(street, resisten);
        byte[] aggregatedTokens = (byte[]) tokenResult[0];

        if (aggregatedTokens == null) {
            String missing = (String) tokenResult[1];
            String motivo = "CONSENSUS FAILED: Missing keys from [" + missing + "]";
            try {
                broadcastGAMECommandFromServer("MISDEAL#" + java.util.Base64.getEncoder().encodeToString(motivo.getBytes("UTF-8")), null, false);
            } catch (Exception e) {
            }
            cancelarManoYDevolverApuestas(motivo);
            return false;
        }

        int numTokens = aggregatedTokens.length / 16;
        byte[] consensusKey = new byte[16];
        for (int p = 0; p < numTokens; p++) {
            for (int i = 0; i < 16; i++) {
                consensusKey[i] ^= aggregatedTokens[p * 16 + i];
            }
        }

        byte[] ramCards = null;
        String comando = null;

        int c_street = street - 1;
        ramCards = Panoptes.getInstance().stateEvolveStreet(c_street, consensusKey);

        // Security check
        if (ramCards != null) {
            for (byte b : ramCards) {
                if ((b & 0xFF) >= 52) {
                    ramCards = null;
                    break;
                }
            }
        }

        if (ramCards != null) {
            String keyB64 = java.util.Base64.getEncoder().encodeToString(consensusKey);

            if (street == Crupier.FLOP && ramCards.length >= 3) {
                GameFrame.getInstance().getFlop1().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);
                GameFrame.getInstance().getFlop2().actualizarConValorNumerico((ramCards[1] & 0xFF) + 1);
                GameFrame.getInstance().getFlop3().actualizarConValorNumerico((ramCards[2] & 0xFF) + 1);

                // Se envía el texto plano para los espectadores nuevos
                comando = "FLOPCARDS#" + keyB64 + "#" + (ramCards[0] & 0xFF) + "#" + (ramCards[1] & 0xFF) + "#" + (ramCards[2] & 0xFF);
            } else if (street == Crupier.TURN && ramCards.length >= 1) {
                GameFrame.getInstance().getTurn().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);

                comando = "TURNCARD#" + keyB64 + "#" + (ramCards[0] & 0xFF);
            } else if (street == Crupier.RIVER && ramCards.length >= 1) {
                GameFrame.getInstance().getRiver().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);

                comando = "RIVERCARD#" + keyB64 + "#" + (ramCards[0] & 0xFF);
            }
        }

        if (ramCards == null) {
            String motivo = "CRYPTOGRAPHIC ERROR: Native Vault rejected the consensus tokens.";
            cancelarManoYDevolverApuestas(motivo);
            return false;
        }

        broadcastGAMECommandFromServer(comando, null);
        return true;
    }

    private boolean recibirCartasComunitarias() {
        long start_time = System.currentTimeMillis();
        boolean ok = false, timeout = false;
        int myPos = calcularPosicionEnPaquete(GameFrame.getInstance().getNick_local());

        do {
            synchronized (this.getReceived_commands()) {
                java.util.ArrayList<String> rejected = new java.util.ArrayList<>();
                while (!ok && !this.getReceived_commands().isEmpty()) {
                    String comando = this.received_commands.poll();
                    String[] partes = comando.split("#");

                    try {
                        if (partes.length >= 4 && partes[2].equals("REQ_TOKEN")) {
                            if (myPos != -1) {
                                int reqStreet = Integer.parseInt(partes[3]);
                                byte[] token = null;

                                if (reqStreet == Crupier.FLOP) {
                                    token = Panoptes.getInstance().stateGetFlopToken();
                                } else if (reqStreet == Crupier.TURN) {
                                    token = Panoptes.getInstance().stateGetTurnToken();
                                } else if (reqStreet == Crupier.RIVER) {
                                    token = Panoptes.getInstance().stateGetRiverToken();
                                }

                                if (token != null) {
                                    String tokenB64 = java.util.Base64.getEncoder().encodeToString(token);
                                    String nickB64 = java.util.Base64.getEncoder().encodeToString(GameFrame.getInstance().getNick_local().getBytes("UTF-8"));
                                    String response = "STREET_TOKEN#" + nickB64 + "#" + reqStreet + "#" + tokenB64;
                                    this.sendGAMECommandToServer(response);
                                }
                            }
                        } else if (street == Crupier.FLOP && partes.length >= 4 && partes[2].equals("FLOPCARDS")) {
                            if (myPos != -1) {
                                byte[] consensusKey = java.util.Base64.getDecoder().decode(partes[3]);
                                byte[] ramCards = Panoptes.getInstance().stateEvolveStreet(street - 1, consensusKey);

                                if (ramCards != null && ramCards.length >= 3) {
                                    GameFrame.getInstance().getFlop1().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);
                                    GameFrame.getInstance().getFlop2().actualizarConValorNumerico((ramCards[1] & 0xFF) + 1);
                                    GameFrame.getInstance().getFlop3().actualizarConValorNumerico((ramCards[2] & 0xFF) + 1);

                                    ok = true;
                                } else {
                                    rejected.add(comando);
                                }
                            } else if (partes.length >= 7) { // Espectador "Calentando" (Texto Plano)
                                GameFrame.getInstance().getFlop1().actualizarConValorNumerico(Integer.parseInt(partes[4]) + 1);
                                GameFrame.getInstance().getFlop2().actualizarConValorNumerico(Integer.parseInt(partes[5]) + 1);
                                GameFrame.getInstance().getFlop3().actualizarConValorNumerico(Integer.parseInt(partes[6]) + 1);
                                ok = true;
                            }
                        } else if (street == Crupier.TURN && partes.length >= 4 && partes[2].equals("TURNCARD")) {
                            if (myPos != -1) {
                                byte[] consensusKey = java.util.Base64.getDecoder().decode(partes[3]);
                                byte[] ramCards = Panoptes.getInstance().stateEvolveStreet(street - 1, consensusKey);

                                if (ramCards != null && ramCards.length >= 1) {
                                    GameFrame.getInstance().getTurn().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);

                                    ok = true;
                                } else {
                                    rejected.add(comando);
                                }
                            } else if (partes.length >= 5) {
                                GameFrame.getInstance().getTurn().actualizarConValorNumerico(Integer.parseInt(partes[4]) + 1);
                                ok = true;
                            }
                        } else if (street == Crupier.RIVER && partes.length >= 4 && partes[2].equals("RIVERCARD")) {
                            if (myPos != -1) {
                                byte[] consensusKey = java.util.Base64.getDecoder().decode(partes[3]);
                                byte[] ramCards = Panoptes.getInstance().stateEvolveStreet(street - 1, consensusKey);

                                if (ramCards != null && ramCards.length >= 1) {
                                    GameFrame.getInstance().getRiver().actualizarConValorNumerico((ramCards[0] & 0xFF) + 1);

                                    ok = true;
                                } else {
                                    rejected.add(comando);
                                }
                            } else if (partes.length >= 5) {
                                GameFrame.getInstance().getRiver().actualizarConValorNumerico(Integer.parseInt(partes[4]) + 1);
                                ok = true;
                            }
                        } else if (partes.length >= 4 && partes[2].equals("MISDEAL")) {
                            String motivo = new String(java.util.Base64.getDecoder().decode(partes[3]), "UTF-8");
                            cancelarManoYDevolverApuestas(motivo);
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
            if (!ok) {
                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                    timeout = true;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!ok && !timeout);

        if (timeout) {
            cancelarManoYDevolverApuestas("TIMEOUT WAITING FOR COMMUNITY CARDS");
            return false;
        }
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
            }
            if (GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).getBot() != null) {
                ((RemotePlayer) jugador).getBot().resetBot();
            }
        }

        this.street = street;

        if (street > PREFLOP) {

            // Create a scheduler to delay the UI loading state
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            // =================================================================
            // [LATENCY MASKING] - Visual feedback for network wait (Delayed 1s)
            // =================================================================
            ScheduledFuture<?> loadingTask = scheduler.schedule(() -> {
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.ORANGE);
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setText(Translator.translate("zero_trust.decrypting_street"));
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
                });
            }, 500, TimeUnit.MILLISECONDS);
            // =================================================================

            boolean success = false;

            try {
                // Perform network operations
                if (GameFrame.getInstance().isPartida_local()) {
                    success = enviarCartasComunitarias(resisten);
                } else {
                    success = recibirCartasComunitarias();
                }
            } finally {
                // Cancel the delayed UI update immediately. 
                // If 1 second hasn't passed, the loading message will never appear.
                loadingTask.cancel(false);
                scheduler.shutdown();

                // Always restore original UI state, regardless of success or failure.
                // This is safe to run even if the loading state was never triggered.
                Helpers.GUIRunAndWait(() -> {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(
                            GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground()
                    );
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                });
            }

            // Handle MISDEAL logic after UI has been safely restored
            if (!success) {
                resisten.clear();
                return resisten; // MISDEAL TRIGGERED
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

                boolean isCryptoReplay = this.conta_accion < this.tot_acciones_recuperadas;
                boolean eraSincronizacion = this.isSincronizando_mano();

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

                if (current_player.isActivo() && current_player.getDecision() != Player.FOLD && current_player.getDecision() != Player.ALLIN) {

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
                                default:
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
                    }

                    if (action == null || action.length < 2) {
                        action = new Object[]{Player.FOLD, 0f, null};
                    } else if (action.length < 3) {
                        action = new Object[]{action[0], action[1], null};
                    }

                    decision = (int) action[0];
                    String actionPacketB64 = action[2] != null ? (String) action[2] : null;

                    if (decision == Player.ALLIN) {
                        if ((action[1] instanceof String) && !"".equals((String) action[1])) {
                            this.current_remote_cinematic_b64 = (String) action[1];
                        }
                        action[1] = 0f;
                    } else {
                        this.current_remote_cinematic_b64 = null;
                    }

                    if (!current_player.isExit()) {

                        byte[] cryptoPacket = null;
                        Participant p = GameFrame.getInstance().getParticipantes().get(current_player.getNickname());
                        boolean isBotLocal = GameFrame.getInstance().isPartida_local() && ((p != null && p.isCpu()) || current_player.getNickname().startsWith("CoronaBot$"));

                        if (isCryptoReplay) {
                            byte[] pastPacket = crypto_replay_queue.poll();
                            if (pastPacket != null) {
                                Panoptes.getInstance().chainVerifyRemoteAction(pastPacket);
                                actionPacketB64 = Base64.getEncoder().encodeToString(pastPacket);
                            }
                        } else {
                            if (current_player == GameFrame.getInstance().getLocalPlayer()) {
                                cryptoPacket = Panoptes.getInstance().chainCommitLocalAction(decision, (float) action[1]);
                                // V71 FIX: If vault locks up and returns null, we gracefully handle it to avoid NPE on Base64
                                if (cryptoPacket == null) {
                                    LOGGER.log(Level.SEVERE, "❌ [ZERO-TRUST] NATIVE VAULT REJECTED LOCAL ACTION COMMIT! VAULT POISONED.");
                                    actionPacketB64 = "*";
                                } else {
                                    actionPacketB64 = Base64.getEncoder().encodeToString(cryptoPacket);
                                }
                            } else if (isBotLocal) {
                                byte[] botPriv = null;
                                try {
                                    botPriv = p != null && p.getPanoptes_private_key() != null
                                            ? p.getPanoptes_private_key()
                                            : java.security.MessageDigest.getInstance("SHA-256").digest(current_player.getNickname().getBytes("UTF-8"));
                                } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                                cryptoPacket = Panoptes.getInstance().chainCommitBotAction(decision, (float) action[1], botPriv);
                                actionPacketB64 = Base64.getEncoder().encodeToString(cryptoPacket);
                            } else {
                                if (actionPacketB64 != null) {
                                    cryptoPacket = Base64.getDecoder().decode(actionPacketB64);
                                    boolean validSignature = Panoptes.getInstance().chainVerifyRemoteAction(cryptoPacket);
                                    if (!validSignature) {
                                        LOGGER.log(Level.SEVERE, "❌ [ZERO-TRUST] ALERT: Mathematical signature rejected for {0}", current_player.getNickname());
                                        GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.action_rejected_invalid_signature", current_player.getNickname()));
                                    }
                                }
                            }
                        }

                        if (cryptoPacket != null) {
                            saveCryptoActionToBin(cryptoPacket);
                        }

                        String comando = null;

                        try {
                            comando = "ACTION#"
                                    + java.util.Base64.getEncoder().encodeToString(current_player.getNickname().getBytes("UTF-8"))
                                    + "#" + decision
                                    + (decision == Player.BET ? "#" + String.valueOf((float) action[1]) : "")
                                    + (actionPacketB64 != null ? "#" + actionPacketB64 : "");
                        } catch (Exception ex) {
                        }

                        if (current_player == GameFrame.getInstance().getLocalPlayer()) {

                            if (GameFrame.getInstance().isPartida_local()) {

                                //Soy el server y tengo que reenviar mi decisión de otro jugador al resto
                                if (decision == Player.ALLIN && this.current_remote_cinematic_b64 != null) {
                                    comando += "#" + this.current_remote_cinematic_b64;
                                }

                                broadcastGAMECommandFromServer(comando, current_player.getNickname());

                            } else {

                                //Soy un jugador normal y le mando mi decisión al server
                                if (decision == Player.ALLIN && this.current_local_cinematic_b64 != null) {
                                    comando += "#" + this.current_local_cinematic_b64;
                                }

                                this.sendGAMECommandToServer(comando);
                            }

                        } else {

                            ((RemotePlayer) current_player).setDecisionFromRemotePlayer(decision, (float) action[1]);

                            if (GameFrame.getInstance().isPartida_local()) {

                                //Soy el server y tengo que reenviar la decisión de otro jugador al resto
                                if (decision == Player.ALLIN && this.current_remote_cinematic_b64 != null) {
                                    comando += "#" + this.current_remote_cinematic_b64;
                                }

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

                    } else {
                        current_player.stopActionTimer();
                    }

                }

                if (!current_player.isExit()) {

                    // --- TRACK EVERY PLAYER ACTION (BUG FIXED + AF TRACKING) ---
                    Bot.TRACKER_MEMORY.putIfAbsent(current_player.getNickname(), new Bot.OpponentTracker());
                    Bot.OpponentTracker stats = Bot.TRACKER_MEMORY.get(current_player.getNickname());

                    if (this.street == Crupier.PREFLOP) {
                        // Pass hand ID to prevent stat inflation from multiple actions per round
                        if (current_player.getDecision() == Player.CHECK
                                || current_player.getDecision() == Player.BET
                                || current_player.getDecision() == Player.ALLIN) {
                            stats.recordVPIP(this.conta_mano);
                        }
                        if (current_player.getDecision() == Player.BET
                                || current_player.getDecision() == Player.ALLIN) {
                            stats.recordPFR(this.conta_mano);
                        }
                    } else {
                        // Post-Flop Aggression Factor (AF) Tracking
                        if (current_player.getDecision() == Player.BET || current_player.getDecision() == Player.ALLIN) {
                            stats.recordPostFlopBetOrRaise();
                        } else if (current_player.getDecision() == Player.CHECK && this.apuesta_actual > old_player_bet) {
                            // In this engine's architecture, CHECK acts as a CALL if there is a pending bet
                            stats.recordPostFlopCall();
                        }
                    }
                    // -----------------------------------------------------------

                    GameFrame.getInstance().getRegistro().print(current_player.getLastActionString());

                    if (current_player.getDecision() != Player.FOLD) {

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
                } else {
                    resisten.remove(current_player);
                }

                try {
                    this.acciones.add(java.util.Base64.getEncoder().encodeToString(current_player.getNickname().getBytes("UTF-8"))
                            + "#" + String.valueOf(current_player.getDecision())
                            + (current_player.getDecision() == Player.BET ? "#" + String.valueOf(current_player.getBet()) : ""));
                } catch (Exception ex) {
                }

                actualizarContadoresTapete();
                conta_pos++;

                if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                    conta_pos %= GameFrame.getInstance().getJugadores().size();
                }

                if (current_player.isActivo()) {
                    this.conta_accion++;

                    if (!isCryptoReplay) {
                        this.sqlNewAction(current_player);
                    } else if (GameFrame.getInstance().isPartida_local()) {
                        if (this.sqlCheckGenuineRecoverAction(current_player)) {
                            LOGGER.log(Level.INFO, "RECOVER ACTION OK");
                        }
                    }
                }

                while (Init.PLAYING_CINEMATIC) {
                    synchronized (getLock_apuestas()) {
                        try {
                            getLock_apuestas().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
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

        } // Cierra el if(puedenApostar > 0)

        if (isFin_de_la_transmision()) {
            synchronized (getLock_apuestas()) {
                try {
                    getLock_apuestas().wait();
                } catch (InterruptedException ex) {
                }
            }
        }

        return (resisten.size() > 1 && street < RIVER && getJugadoresActivos() > 1)
                ? rondaApuestas(street + 1, resisten)
                : resisten;
    }

    private void procesarCartasComunesRestantes() {

        Helpers.barraIndeterminada(GameFrame.getInstance().getBarra_tiempo());

        // V61 FAST RABBIT: If we have the Master Key, decrypt the deck in memory
        // bypassing the network and respecting the burn cards of the native engine.
        if (this.valid_master_key != null && this.valid_master_key.length == 32 && this.local_mega_packet != null) {
            try {
                byte[] final_seed = new byte[32];
                System.arraycopy(this.local_mega_packet, 17, final_seed, 0, 32);
                int numPlayers = this.local_mega_packet[16] & 0xFF;

                for (int p = 0; p < numPlayers; p++) {
                    for (int i = 0; i < 32; i++) {
                        final_seed[i] ^= this.local_mega_packet[49 + (p * 210) + i];
                    }
                }
                for (int i = 0; i < 32; i++) {
                    final_seed[i] ^= this.valid_master_key[i];
                }

                byte[] deck = Panoptes.getInstance().utilsShuffleDeck(final_seed);

                // Replicate native logic from state_extract_game (ESCROW_C)
                int offset = numPlayers * 2; // Skip all pocket cards

                if (street < Crupier.FLOP) {
                    offset++; // Burn 1 card before Flop
                    GameFrame.getInstance().getFlop1().actualizarConValorNumerico((deck[offset++] & 0xFF) + 1);
                    GameFrame.getInstance().getFlop2().actualizarConValorNumerico((deck[offset++] & 0xFF) + 1);
                    GameFrame.getInstance().getFlop3().actualizarConValorNumerico((deck[offset++] & 0xFF) + 1);
                } else {
                    offset += 4; // If Flop is already dealt, skip offset (1 burn + 3 flop cards)
                }

                if (street < Crupier.TURN) {
                    offset++; // Burn 1 card before Turn
                    GameFrame.getInstance().getTurn().actualizarConValorNumerico((deck[offset++] & 0xFF) + 1);
                } else {
                    offset += 2; // If Turn is already dealt, skip offset (1 burn + 1 turn card)
                }

                if (street < Crupier.RIVER) {
                    offset++; // Burn 1 card before River
                    GameFrame.getInstance().getRiver().actualizarConValorNumerico((deck[offset] & 0xFF) + 1);
                }

            } catch (Exception e) {
                java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, "[ZERO-TRUST] Rabbit Hunting Master Key decryption failed", e);
            }
        }

        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), 0);

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
                    if (partes.length >= 5 && partes[2].equals("VISUAL_CARDS_RESP")) {
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
                if (GameFrame.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > GameFrame.CLIENT_RECEPTION_TIMEOUT) {
                    timeout = true;
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        } while (!pendientes.isEmpty() && !timeout);
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

            GameFrame.getInstance().refreshPlayersAndCommunity();

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

            GameFrame.getInstance().refreshPlayersAndCommunity();

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
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }

        } while (pending);
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

        long start = System.currentTimeMillis();

        ArrayList<String> pendientes = new ArrayList<>();

        for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

            Participant p = entry.getValue();

            if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && !p.isExit()) {

                pendientes.add(p.getNick());

            }

        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();

            boolean timeout = false;

            byte[] iv = new byte[16];

            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            do {

                String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && pendientes.contains(p.getNick())) {

                        p.writeCommandFromServer(
                                Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));

                    }
                }

                if (confirmation) {
                    // Esperamos confirmaciones y en caso de que alguna no llegue pasado un tiempo
                    // volvermos a enviar todos los que fallaron la confirmación la primera vez
                    this.waitSyncConfirmations(id, pendientes);

                    for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                        Participant p = entry.getValue();

                        if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && p.isExit()) {

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
                                            nick,
                                            false);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        }

                    }

                    if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {

                        // 0=yes, 1=no, 2=cancel
                        if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                Translator.translate("game.forzamos_reset_socket"),
                                new ImageIcon(getClass().getResource("/images/action/timeout.png"))) == 0) {

                            if (!nick2player.isEmpty()) {
                                for (String nick : pendientes) {
                                    if (!nick2player.get(nick).isExit()) {
                                        GameFrame.getInstance().getParticipantes().get(nick).forceSocketReconnect();
                                    }
                                }
                            }

                        }
                        start = System.currentTimeMillis();

                    }
                }

            } while (confirmation && !pendientes.isEmpty() && !timeout);
        }
    }

    public void sqlRemovePermutationkey() {

        synchronized (GameFrame.SQL_LOCK) {

            String sql = "DELETE FROM permutationkey WHERE hash=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, GameFrame.getInstance().getSala_espera().getLocal_client_permutation_key_hash());

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

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

                            map.put("forced_balance", true);

                            LOGGER.log(Level.WARNING, "Balance recuperado forzado");

                            LOGGER.log(Level.WARNING, balance);

                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                    } else {

                        sql = "select balance.player as PLAYER, round(balance.stack,2) as STACK, balance.buyin as BUYIN from balance,hand,game where balance.id_hand=hand.id and game.id=? and hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?)";

                        statement = Helpers.getSQLITE().prepareStatement(sql);

                        statement.setQueryTimeout(30);

                        statement.setInt(1, this.sqlite_id_game);

                        statement.setInt(2, this.sqlite_id_game);

                        rs = statement.executeQuery();

                        ArrayList<String> balance = new ArrayList<>();

                        while (rs.next()) {

                            balance.add(
                                    Base64.getEncoder().encodeToString(rs.getString("PLAYER").getBytes("UTF-8")) + "|"
                                    + rs.getFloat("STACK") + "|" + rs.getInt("BUYIN"));
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
            String cryptoLogB64 = "*"; // Marcador de log vacío

            if (GameFrame.getInstance().isPartida_local()) {
                datos = sqlRecoverHandActions();

                // [V58] EL SERVIDOR LEE EL LOG BINARIO (LA VERDAD ABSOLUTA)
                File logFile = new File(Init.CORONA_DIR + "/hand_" + this.sqlite_id_hand + ".bin");
                if (logFile.exists()) {
                    byte[] logBytes = java.nio.file.Files.readAllBytes(logFile.toPath());
                    cryptoLogB64 = Base64.getEncoder().encodeToString(logBytes);
                }

                ArrayList<String> pendientes = new ArrayList<>();
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                            && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {
                        pendientes.add(jugador.getNickname());
                    }
                }

                // Enviamos SQL + BINARIO unidos por |||
                enviarAccionesRecuperadas(pendientes, datos + "|||" + cryptoLogB64);

            } else {
                String fullPacket = this.recibirAccionesRecuperadas();
                if (fullPacket != null && fullPacket.contains("|||")) {
                    String[] parts = fullPacket.split("\\|\\|\\|");
                    datos = parts[0];
                    if (parts.length > 1) {
                        cryptoLogB64 = parts[1];
                    }
                } else {
                    datos = fullPacket;
                }
            }

            // [V58] SINCRONIZAR LA COLA DE REPLAY EN CLIENTES Y SERVER
            if (!"*".equals(cryptoLogB64)) {
                byte[] remoteLog = Base64.getDecoder().decode(cryptoLogB64);
                crypto_replay_queue.clear();
                for (int i = 0; i < remoteLog.length; i += 48) {
                    byte[] signature = Arrays.copyOfRange(remoteLog, i, i + 48);
                    crypto_replay_queue.add(signature);
                }
                GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.replay_queue_synced", remoteLog.length / 48));
            } else if (GameFrame.getInstance().isPartida_local()) {
                loadCryptoActionsToQueue();
            }

            this.tot_acciones_recuperadas = 0;
            if (datos != null) {
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

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
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

                        String[] partes = comando.split("#");

                        if (partes[2].equals("SEATS")) {

                            ok = true;

                            int tot = Integer.valueOf(partes[3]);

                            permutados = new String[tot];

                            for (int i = 0; i < tot; i++) {

                                try {
                                    permutados[i] = new String(Base64.getDecoder().decode(partes[i + 4]), "UTF-8");
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        } else {
                            rejected.add(comando);
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
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }

            } while (!ok);

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

        ArrayList<Card> flop = new ArrayList<>();

        flop.add(GameFrame.getInstance().getFlop1());

        flop.add(GameFrame.getInstance().getFlop2());

        flop.add(GameFrame.getInstance().getFlop3());

        GameFrame.getInstance().getRegistro().print("FLOP -> " + Card.collection2String(flop));

        checkJugadasParciales(resisten);
    }

    public void destaparTurn(ArrayList<Player> resisten) {

        mostrarAnimacionDestaparCartaComunitaria(GameFrame.getInstance().getTurn());

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
                || GameFrame.getInstance().getLocalPlayer().isSpectator()
                || this.valid_master_key == null || this.valid_master_key.length == 0;

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
                                cancelarManoYDevolverApuestas(motivo);
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

        GameFrame.getInstance().refreshPlayersAndCommunity();

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
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        GameFrame.getInstance().refreshPlayersAndCommunity();

        synchronized (lock_iwtsth) {
            lock_iwtsth.notifyAll();
        }
    }

    public void showdown(HashMap<Player, Hand> perdedores, HashMap<Player, Hand> ganadores) {
        int pivote;
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
        do {
            Player jugador_actual = GameFrame.getInstance().getJugadores().get(pos);
            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                boolean isLocal = jugador_actual.equals(GameFrame.getInstance().getLocalPlayer());
                jugador_actual.destaparCartas(false);

                if (ganadores.containsKey(jugador_actual)) {
                    Hand jugada = ganadores.get(jugador_actual);
                    jugador_actual.setWinner(jugada.getName());
                    this.sqlNewShowdown(jugador_actual, jugada, true, false);
                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        if (jugador_actual.getDecision() == Player.ALLIN) {
                            Audio.playWavResource("joke/" + GameFrame.LANGUAGE + "/winner/applause.wav");
                        } else {
                            this.soundWinner(jugada.getValue(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                        }
                    }
                } else {
                    Hand jugada = perdedores.get(jugador_actual);
                    jugador_actual.setLoser(jugada.getName());
                    if (isLocal) {
                        GameFrame.getInstance().getLocalPlayer().setMuestra(true);
                    }
                    this.sqlNewShowdown(jugador_actual, jugada, false, false);
                    if (GameFrame.SONIDOS_CHORRA && isLocal) {
                        this.soundLoser(jugada.getValue());
                    }
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

    public void checkHandVerification() {

        int cmano = this.conta_mano;

        synchronized (this.lock_hand_verification) {
            while (!this.verified_hand && cmano == this.conta_mano) {
                try {
                    this.lock_hand_verification.wait(1000);
                } catch (InterruptedException ex) {
                    System.getLogger(Crupier.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            }
        }

        if (cmano != this.conta_mano) {

            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.timeout"));

        } else if (!this.legitHand) {

            if (!GameFrame.getInstance().isPartida_local()) {
                GameFrame.getInstance().getSala_espera().setUnsecure_server(true);
            }

            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("error.zero_trust_alert"));
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.critical"));

        } else {

            GameFrame.getInstance().getTapete().getCommunityCards().showVerifiedHandIcon();
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.legit"));

        }

    }

    @Override
    public void run() {
        Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), Crupier.TIEMPO_PENSAR);

        try {
            String sessionFileName = "/panoptes_session.key";
            if (Init.DEV_MODE) {
                String safeNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                sessionFileName = "/panoptes_session_" + safeNick + ".key";
            }

            File sessionFile = new File(Init.CORONA_DIR + sessionFileName);
            boolean sessionLoaded = false;

            // Attempt to load existing session if recovering
            if (GameFrame.RECOVER && sessionFile.exists()) {
                byte[] sessionBlob = java.nio.file.Files.readAllBytes(sessionFile.toPath());
                if (Panoptes.getInstance().sessionLoad(sessionBlob)) {
                    sessionLoaded = true;
                    LOGGER.log(Level.INFO, Translator.translate("zero_trust.session_restored", true));
                } else {
                    LOGGER.log(Level.WARNING, "Session file rejected (invalid MAC or HWID). Generating fresh session key...");
                }
            }

            // CRITICAL FIX: If the session could not be loaded (because the player is NEW 
            // and joining a recovered game, or the file was corrupted), DO NOT crash. 
            // Generate a fresh session for them instead.
            if (!sessionLoaded) {
                java.nio.file.Files.deleteIfExists(sessionFile.toPath());

                byte[] sessionBlob = Panoptes.getInstance().sessionInitialize();
                if (sessionBlob != null && sessionBlob.length == 48) {
                    java.nio.file.Files.write(sessionFile.toPath(), sessionBlob);
                    LOGGER.log(Level.INFO, Translator.translate("zero_trust.session_generated", true));
                } else {
                    throw new Exception("Failed to initialize native session.");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, Translator.translate("zero_trust.critical_session_error", true), e);
            Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("error.critical_vault_failure"));
            System.exit(1);
        }
        // =========================================================

        if (GameFrame.getInstance().isPartida_local()) {
            GameFrame.UGI = this.getUGI();
            broadcastGAMECommandFromServer("INIT#" + String.valueOf(GameFrame.BUYIN) + "#" + String.valueOf(GameFrame.CIEGA_PEQUEÑA) + "#" + String.valueOf(GameFrame.CIEGA_GRANDE) + "#" + String.valueOf(GameFrame.CIEGAS_DOUBLE) + "@" + String.valueOf(GameFrame.CIEGAS_DOUBLE_TYPE) + "#" + String.valueOf(GameFrame.isRECOVER()) + "@" + GameFrame.UGI + "#" + String.valueOf(GameFrame.REBUY) + "#" + String.valueOf(GameFrame.MANOS), null);
        }

        if (GameFrame.RECOVER) {
            if (GameFrame.getInstance().isPartida_local()) {
                this.sqlite_id_game = GameFrame.RECOVER_ID;
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

                        if (!this.acciones_locales_recuperadas.isEmpty()) {
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

                        this.show_time = true;

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
                                    preShowdownDecryption(new ArrayList<Player>());
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
                                    preShowdownDecryption(new ArrayList<Player>());
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
                                    preShowdownDecryption(resisten);
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

                        Helpers.threadRun(() -> {
                            checkHandVerification();
                        });

                        disableAllPlayersTimeout();

                        GameFrame.getInstance().refreshPlayersAndCommunity();

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
                                    this.show_time = false;
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
                                    if (!GameFrame.getInstance().isPartida_local()) {
                                        sqlRemovePermutationkey();
                                    }
                                    fin_de_la_transmision = true;
                                }
                            } else {
                                this.pausaConBarra(Crupier.PAUSA_ENTRE_MANOS_TEST);
                                synchronized (lock_mostrar) {
                                    this.show_time = false;
                                }
                                GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();
                                GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);
                                fin_de_la_transmision = this.isLast_hand();
                            }

                            Helpers.GUIRun(() -> {
                                if (GameFrame.getInstance().isPartida_local()) {
                                    GameFrame.getInstance().getMenu_rabbit_off().setEnabled(true);
                                    GameFrame.getInstance().getMenu_rabbit_free().setEnabled(true);
                                    GameFrame.getInstance().getMenu_rabbit_sb().setEnabled(true);
                                    GameFrame.getInstance().getMenu_rabbit_bb().setEnabled(true);
                                    GameFrame.getInstance().getIwtsth_rule_menu().setEnabled(true);
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
                        GameFrame.getInstance().getRegistro().print(Translator.translate("player.la_timba_ha_terminado_no"));
                        Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), Translator.translate("player.la_timba_ha_terminado_no"), new ImageIcon(Init.class.getResource("/images/exit.png")));
                        fin_de_la_transmision = true;
                    }
                } else {
                    Helpers.pausar(1000);
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

        cleanTempCrupierFiles();

        GameFrame.getInstance().finTransmision(fin_de_la_transmision);
    }

    // --- NEW HELPER TO PREVENT FOREIGN KEY CASCADE ---
    private void ensurePlayerExists(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            String sql = "INSERT OR IGNORE INTO player(nick) VALUES (?)";
            try (PreparedStatement stmt = Helpers.getSQLITE().prepareStatement(sql)) {
                stmt.setString(1, nick);
                stmt.executeUpdate();
            } catch (Exception e) {
                // Ignore silent constraints
            }
        }
    }

    public void cleanTempCrupierFiles() {

        String handFileName = "/hand_" + this.sqlite_id_hand + ".bin";

        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(Init.CORONA_DIR + handFileName));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete temporary file: " + handFileName, e);
        }

        if (!isForce_recover()) {
            // Determine the file suffix once based on DEV_MODE
            String suffix = "";
            if (Init.DEV_MODE) {
                String sanitizedNick = GameFrame.getInstance().getNick_local().replaceAll("[^a-zA-Z0-9.-]", "_");
                suffix = "_" + sanitizedNick;
            }

            // Array of file templates using %s as a placeholder for the suffix
            String[] fileTemplates = {
                "/balance_backup%s.txt",
                "/panoptes_session%s.key",
                "/panoptes_entropy%s.bin",
                "/panoptes_hand_commit%s.bin"
            };

            // Iterate and delete all temporary files
            for (String template : fileTemplates) {
                String fileName = String.format(template, suffix);
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(Init.CORONA_DIR + fileName));
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to delete temporary file: " + fileName, e);
                }
            }
        }
    }

    public void checkRebuyTime() {

        ArrayList<String> rebuy_players = new ArrayList<>();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo()
                    && Helpers.float1DSecureCompare(0f,
                            Helpers.floatClean(jugador.getStack()) + Helpers.floatClean(jugador.getPagar())) == 0) {

                if (GameFrame.REBUY) {
                    rebuy_players.add(jugador.getNickname());
                } else {
                    jugador.setSpectator(null);
                }

            }
        }

        this.rebuy_time = !rebuy_players.isEmpty();

        if (GameFrame.getInstance().getLocalPlayer().isActivo()
                && Helpers.float1DSecureCompare(Helpers.floatClean(GameFrame.getInstance().getLocalPlayer().getStack())
                        + Helpers.floatClean(GameFrame.getInstance().getLocalPlayer().getPagar()), 0f) == 0) {

            this.rebuy_time = true;

            final float old_brightness = GameFrame.getInstance().getCapa_brillo().getBrightness();

            if (GameFrame.REBUY) {

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

                        int res = Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance(),
                                Translator.translate("rebuy.recompra") + jugador.getNickname(),
                                new ImageIcon(getClass().getResource("/images/pot.png")));

                        rebuy_players.remove(jugador.getNickname());

                        rebuy_now.put(jugador.getNickname(), GameFrame.BUYIN);

                        try {
                            String comando = "REBUY#"
                                    + Base64.getEncoder().encodeToString(jugador.getNickname().getBytes("UTF-8"))
                                    + ((!GameFrame.REBUY || res != 0) ? "#0" : "#" + String.valueOf(GameFrame.BUYIN));

                            this.broadcastGAMECommandFromServer(comando, null);

                        } catch (UnsupportedEncodingException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }

                        if (res != 0) {
                            rebuy_now.remove(jugador.getNickname());
                            jugador.setSpectator(null);
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

            // Si el jugador llega al cálculo de ganadores con las cartas nulas,
            // significa que se desconectó a lo bestia y la Master Key saltó a SKIPPED.
            // Como es matemáticamente IMPOSIBLE saber sus cartas, su mano se declara MUERTA (Muck).
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

    private void saveCryptoActionToBin(byte[] packet) {
        if (packet == null || packet.length != 48) {
            return;
        }
        try {
            java.io.File logFile = new java.io.File(Init.CORONA_DIR + "/hand_" + this.sqlite_id_hand + ".bin");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, true)) {
                fos.write(packet);
                fos.flush();
                fos.getFD().sync();
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, Translator.translate("zero_trust.write_binary_log_failure"), e);
        }
    }

    private void loadCryptoActionsToQueue() {
        crypto_replay_queue.clear();
        java.io.File logFile = new java.io.File(Init.CORONA_DIR + "/hand_" + this.sqlite_id_hand + ".bin");
        if (!logFile.exists()) {
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.no_binary_log"));
            return;
        }
        try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.FileInputStream(logFile))) {
            int count = 0;
            while (true) {
                try {
                    // V57: Leemos bloques exactos de 48 bytes a la cola en RAM
                    byte[] buffer = new byte[48];
                    dis.readFully(buffer);
                    crypto_replay_queue.add(buffer);
                    count++;
                } catch (java.io.EOFException eof) {
                    break; // Fin del archivo alcanzado de forma segura
                }
            }
            GameFrame.getInstance().getRegistro().print(Translator.translate("zero_trust.binary_log_loaded", count));
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(Crupier.class.getName()).log(java.util.logging.Level.SEVERE, Translator.translate("zero_trust.read_binary_log_failure"), e);
        }
    }

    public java.util.ArrayList<Player> getAnilloCriptografico() {
        java.util.ArrayList<Player> ring = new java.util.ArrayList<>();
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            // Include everyone (active, normal spectators, server, zombies)
            // STRICTLY EXCLUDE new players who are "warming up" (calentando) during a recovery.
            if (!jugador.isCalentando()) {
                ring.add(jugador);
            }
        }
        // Sort alphabetically to guarantee identical mathematical positions across all clients
        java.util.Collections.sort(ring, (p1, p2) -> p1.getNickname().compareTo(p2.getNickname()));
        return ring;
    }
}
