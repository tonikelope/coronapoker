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

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.ImageIcon;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class Crupier implements Runnable {

    public static final Map.Entry<String, Object[][]> ALLIN_CINEMATICS = new HashMap.SimpleEntry<String, Object[][]>("allin/",
            new Object[][]{
                {"rounders.gif", 4500L},
                {"hulk.gif", 1100L},
                {"nicolas_cage.gif", 1000L},
                {"nicolas_cage2.gif", 2050L},
                {"training_day.gif", 2000L},
                {"wallstreet.gif", 1500L},
                {"casinoroyale.gif", 4550L},
                {"joker.gif", 3200L},
                {"cards.gif", 2700L}
            });

    public static volatile Map.Entry<String, Object[][]> ALLIN_CINEMATICS_MOD = null;

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_ES = new HashMap.SimpleEntry<String, String[]>("joke/es/allin/", new String[]{
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

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS_EN = new HashMap.SimpleEntry<String, String[]>("joke/en/allin/", new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> ALLIN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> ALLIN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_ES = new HashMap.SimpleEntry<String, String[]>("joke/es/fold/", new String[]{
        "fary.wav",
        "mamar_pollas.wav",
        "maricon.wav",
        "marines.wav",
        "mcfly.wav",
        "mierda_alta.wav",
        "percibo_miedo.wav"});

    public static final Map.Entry<String, String[]> FOLD_SOUNDS_EN = new HashMap.SimpleEntry<String, String[]>("joke/en/fold/", new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> FOLD_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> FOLD_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_ES = new HashMap.SimpleEntry<String, String[]>("joke/es/showdown/", new String[]{
        "berto.wav",
        "bond.wav",
        "kbill_show.wav"});

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS_EN = new HashMap.SimpleEntry<String, String[]>("joke/en/showdown/", new String[]{
        "berto.wav",
        "bond.wav",
        "kbill_show.wav"});

    public static final Map<String, Map.Entry<String, String[]>> SHOWDOWN_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> SHOWDOWN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_ES = new HashMap.SimpleEntry<String, String[]>("joke/es/winner/", new String[]{
        "ateam.wav",
        "divertido.wav",
        "dura.wav",
        "fisuras.wav",
        "lacasitos.wav",
        "nadie_te_aguanta.wav",
        "planesbien.wav",
        "reymundo.wav",
        "vivarey.wav"});

    public static final Map.Entry<String, String[]> WINNER_SOUNDS_EN = new HashMap.SimpleEntry<String, String[]>("joke/en/winner/", new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> WINNER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> WINNER_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_ES = new HashMap.SimpleEntry<String, String[]>("joke/es/loser/", new String[]{
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

    public static final Map.Entry<String, String[]> LOSER_SOUNDS_EN = new HashMap.SimpleEntry<String, String[]>("joke/en/loser/", new String[]{});

    public static final Map<String, Map.Entry<String, String[]>> LOSER_SOUNDS = new HashMap<>();

    public static volatile Map.Entry<String, String[]> LOSER_SOUNDS_MOD = null;

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
    public static final int REPARTIR_PAUSA = 250; //2 players
    public static final int MIN_ULTIMA_CARTA_JUGADA = Hand.TRIO;
    public static final int PERMUTATION_ENCRYPTION_PLAYERS = 2;
    public static final float[][] CIEGAS = new float[][]{new float[]{0.1f, 0.2f}, new float[]{0.2f, 0.4f}, new float[]{0.3f, 0.6f}, new float[]{0.5f, 1.0f}};
    public static volatile boolean FUSION_MOD_SOUNDS = true;
    public static volatile boolean FUSION_MOD_CINEMATICS = true;
    public static final int NEW_HAND_READY_WAIT = 1000;
    public static final int NEW_HAND_READY_WAIT_TIMEOUT = 15000;
    public static final int IWTSTH_ANTI_FLOOD_TIME = 30 * 60 * 1000; // 30 minutes BAN

    private final ConcurrentLinkedQueue<String> received_commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones_locales_recuperadas = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Integer> rebuy_now = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> iwtsth_requests = new ConcurrentHashMap<>();
    private final HashMap<String, Float[]> auditor = new HashMap<>();
    private final Object lock_apuestas = new Object();
    private final Object lock_contabilidad = new Object();
    private final Object lock_cinematics = new Object();
    private final Object lock_mostrar = new Object();
    private final Object iwtsth_lock = new Object();
    private final Object lock_last_hand = new Object();
    private final Object lock_nueva_mano = new Object();
    private final Object lock_rebuynow = new Object();
    private final Object lock_tiempo_pausa_barra = new Object();
    private final Object permutation_key_lock = new Object();
    private final ConcurrentHashMap<String, Player> nick2player = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Hand> perdedores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Player> flop_players = new ConcurrentLinkedQueue<>();

    private volatile int conta_mano = 0;
    private volatile int conta_accion = 0;
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
    private volatile int fase = PREFLOP;
    private volatile Pot bote = null;
    private volatile boolean cartas_resistencia = false;
    private volatile int ciegas_double = 0;
    private volatile long turno = 0;
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
    private volatile boolean playing_cinematic = false;
    private volatile String current_local_cinematic_b64 = null;
    private volatile String current_remote_cinematic_b64 = null;
    private volatile boolean rebuy_time = false;
    private volatile boolean last_hand = false;
    private volatile int sqlite_id_game = -1;
    private volatile int sqlite_id_hand = -1;
    private volatile String permutation_key = null;
    private volatile GifAnimationDialog gif_dialog = null;
    private volatile GameOverDialog gameover_dialog = null;
    private volatile String dealer_nick = null;
    private volatile String bb_nick = null;
    private volatile String sb_nick = null;
    private volatile String utg_nick = null;
    private volatile boolean saltar_primera_mano = false;
    private volatile boolean update_game_seats = false;
    private volatile int tot_acciones_recuperadas = 0;
    private volatile Float beneficio_bote_principal = null;
    private volatile Integer[] permutacion_recuperada = null;
    private volatile boolean iwtsth = false;
    private volatile boolean iwtsthing = false;
    private volatile boolean iwtsthing_request = false;
    private volatile Long last_iwtsth_rejected = null;
    private volatile int limpers;
    private volatile int game_recovered = 0;

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

    public String getDealer_nick() {
        return dealer_nick;
    }

    public String getBb_nick() {
        return bb_nick;
    }

    public String getSb_nick() {
        return sb_nick;
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
        return permutation_key_lock;
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

    public void setPlaying_cinematic(boolean playing_cinematic) {
        this.playing_cinematic = playing_cinematic;
    }

    public boolean isPlaying_cinematic() {
        return playing_cinematic;
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
                    this.broadcastGAMECommandFromServer("REBUYNOW#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + String.valueOf(buyin), nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname()) ? null : nick);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if (nick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                this.sendGAMECommandToServer("REBUYNOW#" + String.valueOf(buyin));
            }
        }
    }

    public static void loadMODCinematics() {

        if (Init.MOD != null) {

            HashMap<String, HashMap<String, Object>> cinematics_mod = (HashMap<String, HashMap<String, Object>>) Init.MOD.get("cinematics");

            //ALLIN CINEMATICS
            if (!cinematics_mod.isEmpty()) {

                ArrayList<Object[]> cinematics = new ArrayList<>();

                for (Map.Entry<String, HashMap<String, Object>> entry : cinematics_mod.entrySet()) {

                    HashMap<String, Object> mapa = entry.getValue();

                    if (mapa.get("event").equals("allin")) {
                        cinematics.add(new Object[]{mapa.get("name"), mapa.get("time")});
                    }
                }

                if (FUSION_MOD_CINEMATICS) {

                    cinematics.addAll(Arrays.asList(Crupier.ALLIN_CINEMATICS.getValue()));
                }
                Crupier.ALLIN_CINEMATICS_MOD = new HashMap.SimpleEntry<>("allin/", cinematics.toArray(new Object[cinematics.size()][]));

            } else {

                Crupier.ALLIN_CINEMATICS_MOD = Crupier.ALLIN_CINEMATICS;
            }

        }
    }

    public static void loadMODSounds() {

        if (Init.MOD != null) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/"))) {
                File[] archivos = new File(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/allin/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/allin/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.ALLIN_SOUNDS_MOD = Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/"))) {
                File[] archivos = new File(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/fold/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/fold/", sounds.toArray(new String[sounds.size()]));
                }

            } else {
                Crupier.FOLD_SOUNDS_MOD = Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/"))) {
                File[] archivos = new File(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/showdown/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/showdown/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.SHOWDOWN_SOUNDS_MOD = Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/"))) {
                File[] archivos = new File(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/loser/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/loser/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.LOSER_SOUNDS_MOD = Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE);
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/"))) {
                File[] archivos = new File(Helpers.getCurrentJarParentPath() + "/mod/sounds/joke/" + GameFrame.LANGUAGE + "/winner/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE).getValue()));

                    sounds.addAll(filenames);

                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("joke/" + GameFrame.LANGUAGE + "/winner/", sounds.toArray(new String[sounds.size()]));
                }

            } else {
                Crupier.WINNER_SOUNDS_MOD = Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE);
            }

        }

    }

    public void remoteCinematicEnd(String nick) {

        if (GameFrame.getInstance().isPartida_local()) {

            broadcastGAMECommandFromServer("CINEMATICEND", nick);
        }

        playing_cinematic = false;

        synchronized (lock_cinematics) {
            lock_cinematics.notifyAll();
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

        synchronized (lock_tiempo_pausa_barra) {
            this.tiempo_pausa = tiempo;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    GameFrame.getInstance().getBarra_tiempo().setMaximum(tiempo);
                    GameFrame.getInstance().getBarra_tiempo().setValue(tiempo);
                }
            });
        }

    }

    public int getTiempoPausa() {

        synchronized (lock_tiempo_pausa_barra) {

            return tiempo_pausa;
        }
    }

    public long getTurno() {
        return turno;
    }

    public boolean localCinematicAllin() {

        Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD) : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

        if (!this.sincronizando_mano && GameFrame.CINEMATICAS && map.containsKey("allin/") && map.get("allin/").length > 0) {

            Object[][] allin_cinematics = map.get("allin/");

            int r = Helpers.CSPRNG_GENERATOR.nextInt(allin_cinematics.length) + 1;

            String filename = (String) allin_cinematics[r - 1][0];

            long pausa = (long) allin_cinematics[r - 1][1];

            try {

                this.current_local_cinematic_b64 = Base64.encodeBase64String((Base64.encodeBase64String(filename.getBytes("UTF-8")) + "#" + String.valueOf(pausa)).getBytes("UTF-8"));

                if (pausa == 0L) {
                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            if (Audio.playWavResourceAndWait("allin/" + filename.replaceAll("\\.gif$", ".wav"))) {

                                if (GameFrame.getInstance().isPartida_local()) {

                                    broadcastGAMECommandFromServer("CINEMATICEND", null);

                                } else {

                                    sendGAMECommandToServer("CINEMATICEND");
                                }

                                playing_cinematic = false;

                                synchronized (lock_cinematics) {
                                    lock_cinematics.notifyAll();
                                }
                            }
                        }
                    });
                } else if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav"))) || getClass().getResource("/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null) {

                    Audio.playWavResource("allin/" + filename.replaceAll("\\.gif$", ".wav"));

                }

                return _cinematicAllin(filename, pausa);

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);

                playing_cinematic = false;
                this.current_local_cinematic_b64 = null;
            }
        } else {
            playing_cinematic = false;
            this.current_local_cinematic_b64 = null;
        }

        return false;

    }

    public boolean remoteCinematicAllin() {

        if (getCurrent_remote_cinematic_b64() != null) {

            try {

                String animationb64 = new String(Base64.decodeBase64(getCurrent_remote_cinematic_b64()), "UTF-8");

                String[] partes = animationb64.split("#");

                String filename = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                long pausa = Long.parseLong(partes[1]);

                if (pausa == 0L) {
                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            if (Audio.playWavResourceAndWait("allin/" + filename.replaceAll("\\.gif$", ".wav"))) {

                                playing_cinematic = false;
                                synchronized (lock_cinematics) {
                                    lock_cinematics.notifyAll();
                                }
                            }
                        }
                    });
                }

                return _cinematicAllin(filename, pausa);

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                setPlaying_cinematic(false);
                setCurrent_remote_cinematic_b64(null);
            }

        } else {
            setPlaying_cinematic(false);
        }

        return false;

    }

    private boolean _cinematicAllin(String filename, long pausa) {

        if (!this.sincronizando_mano) {

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
                }
            });

            if (GameFrame.CINEMATICAS) {

                final ImageIcon icon;

                if (Init.MOD != null && Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename))) {
                    icon = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename);
                } else if (getClass().getResource("/cinematics/allin/" + filename) != null) {
                    icon = new ImageIcon(getClass().getResource("/cinematics/allin/" + filename));
                } else {
                    icon = null;
                }

                if (icon != null) {

                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            Helpers.GUIRun(new Runnable() {
                                public void run() {

                                    gif_dialog = new GifAnimationDialog(GameFrame.getInstance().getFrame(), false, icon);
                                    gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                    gif_dialog.setVisible(true);
                                }
                            });

                            if (pausa != 0L) {
                                Helpers.pausar(pausa);
                                playing_cinematic = false;
                            } else {

                                while (playing_cinematic) {

                                    synchronized (lock_cinematics) {

                                        try {
                                            lock_cinematics.wait(1000);
                                        } catch (InterruptedException ex) {
                                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }
                            }

                            current_remote_cinematic_b64 = null;

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    gif_dialog.dispose();

                                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                                }
                            });

                            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                            }
                        }
                    });

                } else {

                    Helpers.threadRun(new Runnable() {

                        public void run() {
                            if (current_remote_cinematic_b64 != null) {

                                if (pausa != 0L) {
                                    Helpers.pausar(pausa);
                                    playing_cinematic = false;
                                } else {

                                    while (playing_cinematic) {

                                        synchronized (lock_cinematics) {

                                            try {
                                                lock_cinematics.wait(1000);
                                            } catch (InterruptedException ex) {
                                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                            }
                                        }
                                    }
                                }

                            }

                            current_remote_cinematic_b64 = null;

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                                }
                            });

                            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                            }
                        }
                    });
                }

            } else {

                Helpers.threadRun(new Runnable() {

                    public void run() {
                        if (pausa != 0L) {
                            Helpers.pausar(pausa);
                            playing_cinematic = false;
                        } else {

                            while (playing_cinematic) {

                                synchronized (lock_cinematics) {

                                    try {
                                        lock_cinematics.wait(1000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                        }

                        current_remote_cinematic_b64 = null;

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                                GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                            }
                        });

                        synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                            GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                        }
                    }
                });

            }

        }

        return (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav"))) || getClass().getResource("/cinematics/allin/" + filename.replaceAll("\\.gif$", ".wav")) != null);

    }

    public void soundAllin() {

        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_SOUNDS_MOD) : Map.ofEntries(Crupier.ALLIN_SOUNDS.get(GameFrame.LANGUAGE)));

        }

    }

    public void soundFold() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {
            this.fold_sound_playing = true;
            Helpers.threadRun(new Runnable() {
                public void run() {
                    Audio.playRandomWavResourceAndWait(Init.MOD != null ? Map.ofEntries(Crupier.FOLD_SOUNDS_MOD) : Map.ofEntries(Crupier.FOLD_SOUNDS.get(GameFrame.LANGUAGE)));
                    fold_sound_playing = false;
                }
            });
        }
    }

    public void soundShowdown() {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Audio.muteAllLoopMp3();
                        Audio.playWavResourceAndWait("misc/badbeat.wav");
                        Audio.unmuteAllLoopMp3();
                    }
                });
            } else if (jugada_ganadora >= Hand.POKER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Audio.muteAllLoopMp3();
                        Audio.playWavResourceAndWait("misc/youarelucky.wav");
                        Audio.unmuteAllLoopMp3();
                    }
                });

            } else {
                Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.SHOWDOWN_SOUNDS_MOD, Crupier.WINNER_SOUNDS_MOD, Crupier.LOSER_SOUNDS_MOD) : Map.ofEntries(Crupier.SHOWDOWN_SOUNDS.get(GameFrame.LANGUAGE), Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE), Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public void soundWinner(int jugada, boolean ultima_carta) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if ((jugada >= Hand.POKER || badbeat) && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Audio.muteAllLoopMp3();
                        Audio.playWavResourceAndWait("misc/youarelucky.wav");
                        Audio.unmuteAllLoopMp3();
                    }
                });

            } else {

                Map<String, String[]> sonidos;

                if (ultima_carta && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD, new HashMap.SimpleEntry<String, String[]>("misc/", new String[]{"lastcard.wav"})) : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE), new HashMap.SimpleEntry<String, String[]>("misc/", new String[]{"lastcard.wav"}));

                } else {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD) : Map.ofEntries(Crupier.WINNER_SOUNDS.get(GameFrame.LANGUAGE));
                }

                Audio.playRandomWavResource(sonidos);
            }
        }
    }

    public void soundLoser(int jugada) {
        if (!this.sincronizando_mano && GameFrame.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Audio.muteAllLoopMp3();
                        Audio.playWavResourceAndWait("misc/badbeat.wav");
                        Audio.unmuteAllLoopMp3();
                    }
                });
            } else if (jugada >= Hand.FULL && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("joke/es/loser/", new String[]{
                    "encargado.wav",
                    "matias.wav"});

                Audio.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));

            } else {
                Audio.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.LOSER_SOUNDS_MOD) : Map.ofEntries(Crupier.LOSER_SOUNDS.get(GameFrame.LANGUAGE)));
            }
        }
    }

    public int getFase() {
        return fase;
    }

    public Pot getBote() {
        return bote;
    }

    public HashMap<String, Float[]> getAuditor() {
        return auditor;
    }

    public void auditorCuentas() {

        synchronized (this.getLock_contabilidad()) {

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                this.auditor.put(jugador.getNickname(), new Float[]{jugador.getStack() + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : jugador.getBote()), (float) jugador.getBuyin()});
            }

            float stack_sum = 0f;

            float buyin_sum = 0f;

            String status = "[NICK / STACK / BUYIN] -> ";

            for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {

                Float[] pasta = entry.getValue();

                stack_sum += pasta[0];

                buyin_sum += pasta[1];

                status += " [" + entry.getKey() + " / " + Helpers.float2String(pasta[0]) + " / " + Helpers.float2String(pasta[1]) + "] ";

            }

            GameFrame.getInstance().getRegistro().print(status);

            if (Helpers.float1DSecureCompare(Helpers.floatClean1D(stack_sum) + Helpers.floatClean1D(this.bote_sobrante), buyin_sum) != 0) {

                if (this.game_recovered == 1 && Helpers.float1DSecureCompare(0f, this.bote_sobrante) <= 0) {

                    this.game_recovered = 2;

                    //CORREGIMOS EL BOTE SOBRANTE DESAPARECIDO AL RECUPERAR LA PARTIDA
                    this.bote_sobrante = Helpers.floatClean1D(Helpers.floatClean1D(buyin_sum) - Helpers.floatClean1D(stack_sum));
                    this.bote_total = this.bote_sobrante;

                    GameFrame.getInstance().getRegistro().print(Translator.translate("AUDITOR DE CUENTAS") + " -> STACKS: " + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum) + " / INDIVISIBLE: " + Helpers.float2String(this.bote_sobrante));
                } else {
                    GameFrame.getInstance().getRegistro().print(Translator.translate("AUDITOR DE CUENTAS") + " -> STACKS: " + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum) + " / INDIVISIBLE: " + Helpers.float2String(this.bote_sobrante));
                    GameFrame.getInstance().getRegistro().print("¡OJO A ESTO: NO SALEN LAS CUENTAS GLOBALES! -> (STACKS + INDIVISIBLE) != BUYIN");
                }
            } else {
                GameFrame.getInstance().getRegistro().print(Translator.translate("AUDITOR DE CUENTAS") + " -> STACKS: " + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum) + " / INDIVISIBLE: " + Helpers.float2String(this.bote_sobrante));
            }
        }
    }

    private void recibirRebuys(ArrayList<String> pending) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);
            }
        });

        //Esperamos confirmación
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
                            nick = new String(Base64.decodeBase64(partes[3]), "UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        pending.remove(nick);

                        Player jugador = nick2player.get(nick);

                        jugador.setTimeout(false);

                        if (GameFrame.getInstance().isPartida_local()) {

                            broadcastGAMECommandFromServer("REBUY#" + partes[3] + (partes.length > 4 ? "#" + partes[4] : ""), nick);
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
                                try {
                                    this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                                } catch (UnsupportedEncodingException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                        }

                        int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

                        // 0=yes, 1=no, 2=cancel
                        if (input == 1) {

                            timeout = true;

                            for (String nick : pending) {
                                this.remotePlayerQuit(nick);
                            }

                        } else {
                            start_time = System.currentTimeMillis();
                        }

                    } else {

                        //Comprobamos si la conexión con el servidor está funcionando
                        this.sendGAMECommandToServer("PING");

                        start_time = System.currentTimeMillis();
                    }
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.getReceived_commands().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }

            }

        }

        Helpers.GUIRun(new Runnable() {
            public void run() {
                GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
            }
        });

    }

    public synchronized void remotePlayerQuit(String nick) {

        Player jugador = nick2player.get(nick);

        if (jugador != null && !jugador.isExit()) {

            jugador.setExit();

            if (GameFrame.getInstance().isPartida_local()) {

                GameFrame.getInstance().getParticipantes().get(nick).exitAndCloseSocket();

                try {

                    broadcastGAMECommandFromServer("EXIT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (GameFrame.getInstance().getParticipantes().get(nick).isCpu()) {
                    GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
                }

            } else {

                GameFrame.getInstance().getSala_espera().borrarParticipante(nick);
            }

            synchronized (this.getReceived_commands()) {
                this.getReceived_commands().notifyAll();
            }

            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
            }
        }

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

            if (jugador.isActivo() && jugador.getDecision() != Player.FOLD && jugador.getDecision() != Player.ALLIN && (nick == null || partial_raise || !jugador.getNickname().equals(nick))) {
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

                try {
                    String comando = "SHOWCARDS#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + jugador.getPlayingCard1().toShortString() + "#" + jugador.getPlayingCard2().toShortString();

                    broadcastGAMECommandFromServer(comando, nick);

                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.getPlayingCard1().isTapada()) {

                    jugador.destaparCartas(true);

                    ArrayList<Card> cartas = new ArrayList<>();

                    cartas.add(jugador.getPlayingCard1());
                    cartas.add(jugador.getPlayingCard2());

                    String lascartas = Card.collection2String(cartas);

                    for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                        if (!carta_comun.isTapada()) {
                            cartas.add(carta_comun);
                        }
                    }

                    Hand jugada = new Hand(cartas);

                    jugador.showCards(jugada.getName());

                    if (GameFrame.SONIDOS_CHORRA && jugador.getDecision() == Player.FOLD) {
                        Audio.playWavResource("misc/showyourcards.wav");
                    }

                    if (!perdedores.containsKey(jugador)) {
                        GameFrame.getInstance().getRegistro().print(nick + Translator.translate(" MUESTRA (") + lascartas + ")" + (jugada != null ? " -> " + jugada : ""));
                    }

                    sqlNewShowcards(jugador.getNickname(), jugador.getDecision() == Player.FOLD);

                    sqlUpdateShowdownHand(jugador, jugada);

                    setTiempo_pausa(GameFrame.TEST_MODE ? GameFrame.PAUSA_ENTRE_MANOS_TEST : GameFrame.PAUSA_ENTRE_MANOS);
                } else if (jugador == GameFrame.getInstance().getLocalPlayer()) {
                    setTiempo_pausa(GameFrame.TEST_MODE ? GameFrame.PAUSA_ENTRE_MANOS_TEST : GameFrame.PAUSA_ENTRE_MANOS);
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

                if (jugador.getPlayingCard1().isTapada()) {

                    String[] carta1_partes = carta1.split("_");
                    String[] carta2_partes = carta2.split("_");

                    jugador.getPlayingCard1().actualizarValorPalo(carta1_partes[0], carta1_partes[1]);
                    jugador.getPlayingCard2().actualizarValorPalo(carta2_partes[0], carta2_partes[1]);

                    jugador.destaparCartas(true);

                    ArrayList<Card> cartas = new ArrayList<>();

                    cartas.add(jugador.getPlayingCard1());
                    cartas.add(jugador.getPlayingCard2());

                    String lascartas = Card.collection2String(cartas);

                    for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                        if (!carta_comun.isTapada()) {
                            cartas.add(carta_comun);
                        }
                    }

                    Hand jugada = new Hand(cartas);

                    jugador.showCards(jugada.getName());

                    if (GameFrame.SONIDOS_CHORRA && jugador.getDecision() == Player.FOLD) {
                        Audio.playWavResource("misc/showyourcards.wav");
                    }

                    if (!perdedores.containsKey(jugador)) {
                        GameFrame.getInstance().getRegistro().print(nick + Translator.translate(" MUESTRA (") + lascartas + ")" + (jugada != null ? " -> " + jugada : ""));
                    }

                    sqlNewShowcards(jugador.getNickname(), jugador.getDecision() == Player.FOLD);

                    sqlUpdateShowdownHand(jugador, jugada);

                    setTiempo_pausa(GameFrame.TEST_MODE ? GameFrame.PAUSA_ENTRE_MANOS_TEST : GameFrame.PAUSA_ENTRE_MANOS);

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

                    if (partes[2].equals("YOURCARDS")) {

                        ok = true;

                        cartas = partes[3].split("@");

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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

        return new ArrayList<>(Arrays.asList(cartas));

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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

    private void recuperarDatosClavePartida() {

        HashMap<String, Object> map;

        saltar_primera_mano = false;

        if (GameFrame.getInstance().isPartida_local()) {

            map = sqlRecoverGameKeyData();

            GameFrame.GAME_START_TIMESTAMP = (long) map.get("start");

            ArrayList<String> pendientes = new ArrayList<>();

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador != GameFrame.getInstance().getLocalPlayer() && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                    pendientes.add(jugador.getNickname());
                }
            }

            permutacion_recuperada = this.recuperarPermutacion();

            if (permutacion_recuperada == null) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), "ERROR: NO SE HA PODIDO RECUPERAR LA CLAVE DE PERMUTACIÓN DE ESTA MANO");
                    }
                });

                map.put("permutation_key", false);

                saltar_primera_mano = true;

            } else {

                map.put("permutation_key", true);
            }

            enviarDatosClaveRecuperados(pendientes, map);

        } else {

            map = recibirDatosClaveRecuperados();

            if (!((boolean) map.get("permutation_key"))) {

                saltar_primera_mano = true;
            }
        }

        if ((Long) map.get("hand_end") != 0L) {
            saltar_primera_mano = true;
        }

        this.sqlite_id_hand = (int) map.get("hand_id");

        GameFrame.BUYIN = (int) map.get("buyin");

        GameFrame.REBUY = (boolean) map.get("rebuy");

        GameFrame.getInstance().setConta_tiempo_juego((long) map.get("play_time"));

        this.conta_mano = (int) map.get("conta_mano");

        this.ciega_pequeña = (float) map.get("sbval");

        this.ciega_grande = (float) map.get("bbval");

        GameFrame.CIEGAS_DOUBLE = (int) map.get("blinds_time");

        GameFrame.CIEGAS_DOUBLE_TYPE = (int) map.get("blinds_time_type");

        this.ciegas_double = (int) map.get("blinds_double");

        String[] auditor_partes = ((String) map.get("balance")).split("@");

        ArrayList<String> nicks_recuperados = new ArrayList<>();

        for (String player_data : auditor_partes) {

            String[] partes = player_data.split("\\|");

            try {
                String name = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                nicks_recuperados.add(name);

                Player jugador = nick2player.get(name);

                if (jugador != null) {

                    //Es un jugador conocido en esta timba
                    jugador.setBuyin(Integer.parseInt(partes[2]));

                    jugador.setStack(Float.parseFloat(partes[1]));

                    jugador.setBet(0f);

                    this.auditor.put(name, new Float[]{Float.parseFloat(partes[1]), Float.parseFloat(partes[2])});

                    if (Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0) {

                        jugador.setSpectator(null);
                    }

                } else {

                    //Un jugador que estaba en la timba anterior pero no está ahora
                    this.auditor.put(name, new Float[]{Float.parseFloat(partes[1]), Float.parseFloat(partes[2])});

                    if (((String) map.get("preflop_players")).contains(Base64.encodeBase64String(name.getBytes("UTF-8"))) && Helpers.float1DSecureCompare(0f, Float.parseFloat(partes[1])) < 0) {

                        //Este jugador estaba en la mano que se interrumpió -> NO SE PUEDE RECUPERAR LA MANO INTERRUMPIDA
                        String ganancia_msg = "";

                        float ganancia = Helpers.floatClean1D(Helpers.floatClean1D(Float.parseFloat(partes[1])) - Helpers.floatClean1D(Float.parseFloat(partes[2])));

                        if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                            ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                        } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                            ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                        } else {
                            ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                        }

                        GameFrame.getInstance().getRegistro().print(name + " " + Translator.translate("ABANDONA LA TIMBA") + " -> " + ganancia_msg);

                        saltar_primera_mano = true;
                    }
                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        //COMPROBAMOS LOS JUGADORES NUEVOS Y LOS PONEMOS A CALENTAR EN LA PRIMERA MANO
        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (!nicks_recuperados.contains(jugador.getNickname())) {

                jugador.setSpectator(Translator.translate("CALENTANDO"));

                this.auditor.put(jugador.getNickname(), new Float[]{jugador.getStack(), (float) jugador.getBuyin()});

                GameFrame.getInstance().getRegistro().print(jugador.getNickname() + Translator.translate(" se UNE a la TIMBA."));

            }
        }

        if (this.getJugadoresActivos() < 2) {

            saltar_primera_mano = true;

            if (this.getJugadoresActivos() + this.getJugadoresCalentando() < 2 && GameFrame.getInstance().getJugadores().size() >= 2) {

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (jugador.isSpectator() && !jugador.isCalentando()) {

                        jugador.setBuyin(jugador.getBuyin() + GameFrame.BUYIN);

                        jugador.setStack(GameFrame.BUYIN);

                        jugador.setSpectator(Translator.translate("CALENTANDO"));

                        Audio.playWavResource("misc/cash_register.wav");
                    }
                }

            }

        }

        //RECUPERAMOS LAS POSICIONES DE LA MESA
        this.dealer_nick = (String) map.get("dealer");

        this.sb_nick = (String) map.get("sb");

        this.bb_nick = (String) map.get("bb");

        if (!saltar_primera_mano) {

            int bb_pos = permutadoNick2Pos(this.bb_nick);

            if (bb_pos != -1) {

                //SI LA CIEGA GRANDE NO ESTÁ YA, LA MANO ACTUAL NO SE PUEDE RECUPERAR
                if (getJugadoresActivos() == 2) {

                    this.utg_nick = this.dealer_nick;

                } else {

                    //UTG
                    int utg_pos = bb_pos + 1;

                    String new_utg = permutadoPos2Nick(utg_pos);

                    while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {

                        new_utg = permutadoPos2Nick(++utg_pos);

                    }

                    this.utg_nick = new_utg;
                }

                //Si la mano que se interrumpió se puede recuperar actualizamos las posiciones de dealer, ciegas y utg
                for (Player jugador : GameFrame.getInstance().getJugadores()) {
                    jugador.refreshPos();
                }

            }
        }

        actualizarContadoresTapete();

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
                            this.utg_nick = new String(Base64.decodeBase64(partes[3]), "UTF-8");

                            this.bb_nick = new String(Base64.decodeBase64(partes[4]), "UTF-8");

                            this.sb_nick = new String(Base64.decodeBase64(partes[5]), "UTF-8");

                            this.dealer_nick = new String(Base64.decodeBase64(partes[6]), "UTF-8");

                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }

        } while (!ok);

    }

    public float getUltimo_raise() {
        return ultimo_raise;
    }

    private boolean checkDoblarCiegas() {

        if (GameFrame.CIEGAS_DOUBLE_TYPE <= 1) {
            return (GameFrame.CIEGAS_DOUBLE > 0 && (int) Math.floor((float) GameFrame.getInstance().getConta_tiempo_juego() / (GameFrame.CIEGAS_DOUBLE * 60)) > this.ciegas_double);
        } else {
            return (GameFrame.CIEGAS_DOUBLE > 0 && this.conta_mano > 1 && ((int) Math.floor((float) (this.conta_mano - 1)) / GameFrame.CIEGAS_DOUBLE) > this.ciegas_double);
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

        GameFrame.getInstance().getRegistro().print("SE DOBLAN LAS CIEGAS");

    }

    public float getBote_total() {
        return bote_total;
    }

    private void readyForNextHand() {

        //SINCRONIZACIÓN DE LA MANO
        if (GameFrame.getInstance().isPartida_local()) {

            //Esperamos a recibir el comando de confirmación de que están listos para una nueva mano
            boolean ready;

            int timeout = 0;

            do {

                ready = true;

                for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && !p.isExit() && p.getNew_hand_ready() <= this.conta_mano) {

                        ready = false;

                        if (timeout == NEW_HAND_READY_WAIT_TIMEOUT) {

                            Logger.getLogger(Crupier.class.getName()).log(Level.WARNING, p.getNick() + " -> NEW HAND (" + String.valueOf(this.conta_mano + 1) + ") CONFIRMATION NOT RECEIVED!");

                            nick2player.get(p.getNick()).setTimeout(true);

                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(p.getNick().getBytes("UTF-8")), p.getNick(), false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } else {
                        Helpers.threadRun(new Runnable() {

                            public void run() {
                                Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), "HAY JUGADORES QUE NO HAN CONFIRMADO LA NUEVA MANO");
                            }
                        });
                    }
                }

            } while (!ready && timeout <= NEW_HAND_READY_WAIT_TIMEOUT);

        } else {

            this.sendGAMECommandToServer("NEWHANDREADY#" + String.valueOf(this.conta_mano + 1));

        }

    }

    public void IWTSTH_HANDLER(String iwtsther) {

        iwtsthing = true;

        Helpers.threadRun(new Runnable() {

            public void run() {

                synchronized (iwtsth_lock) {

                    if (!iwtsth) {

                        iwtsth = true;

                        if (iwtsth_requests.containsKey(iwtsther)) {
                            iwtsth_requests.put(iwtsther, (int) iwtsth_requests.get(iwtsther) + 1);
                        } else {
                            iwtsth_requests.put(iwtsther, 1);
                        }

                        int conta_iwtsth = (int) iwtsth_requests.get(iwtsther);

                        GameFrame.getInstance().getRegistro().print(iwtsther + Translator.translate(" SOLICITA IWTSTH (") + String.valueOf(conta_iwtsth) + ")");

                        Helpers.GUIRun(new Runnable() {
                            public void run() {

                                if (GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado()) {
                                    GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(false);
                                }

                                GameFrame.getInstance().getTapete().getCommunityCards().getBarra_tiempo().setIndeterminate(true);

                            }
                        });

                        if (GameFrame.getInstance().isPartida_local()) {

                            try {
                                broadcastGAMECommandFromServer("IWTSTH#" + Base64.encodeBase64String(iwtsther.getBytes("UTF-8")), null);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        }

                        if (GameFrame.CINEMATICAS) {

                            Helpers.GUIRunAndWait(new Runnable() {
                                public void run() {
                                    GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance().getFrame(), false, new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth.gif")), 2500);

                                    gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                    gif_dialog.setVisible(true);
                                }
                            });

                            Helpers.pausar(500);

                            Audio.playWavResourceAndWait("misc/iwtsth.wav");

                        } else {
                            Audio.playWavResourceAndWait("misc/iwtsth.wav");
                        }

                        if (GameFrame.getInstance().isPartida_local()) {

                            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther) || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), iwtsther + Translator.translate(" SOLICITA IWTSTH (") + String.valueOf(conta_iwtsth) + Translator.translate(") ¿AUTORIZAMOS?")) == 0) {
                                IWTSTH_SHOW(iwtsther, true);
                            } else {
                                IWTSTH_SHOW(iwtsther, false);
                            }

                        }
                    }

                }
            }
        });

    }

    public void IWTSTH_SHOW(String iwtsther, boolean authorized) {

        if (GameFrame.getInstance().isPartida_local()) {

            try {
                broadcastGAMECommandFromServer("IWTSTHSHOW#" + Base64.encodeBase64String(iwtsther.getBytes("UTF-8")) + "#" + String.valueOf(authorized), null);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        if (authorized) {

            synchronized (lock_mostrar) {

                for (Player j : GameFrame.getInstance().getJugadores()) {

                    if (GameFrame.getInstance().getLocalPlayer() != j) {

                        RemotePlayer rp = (RemotePlayer) j;

                        if (rp.isIwtsthCandidate()) {

                            rp.destaparCartas(true);

                            ArrayList<Card> cartas = new ArrayList<>();

                            cartas.add(rp.getPlayingCard1());
                            cartas.add(rp.getPlayingCard2());

                            String lascartas = Card.collection2String(cartas);

                            for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                                if (!carta_comun.isTapada()) {
                                    cartas.add(carta_comun);
                                }
                            }

                            Hand jugada = new Hand(cartas);

                            rp.showCards(jugada.getName());

                            GameFrame.getInstance().getRegistro().print("IWTSTH (" + iwtsther + ") -> " + rp.getNickname() + Translator.translate(" MUESTRA (") + lascartas + ")" + (jugada != null ? " -> " + jugada : ""));

                            sqlNewShowcards(rp.getNickname(), rp.getDecision() == Player.FOLD);

                            sqlUpdateShowdownHand(rp, jugada);

                        }

                    }
                }

                setTiempo_pausa(GameFrame.TEST_MODE ? GameFrame.PAUSA_ENTRE_MANOS_TEST : GameFrame.PAUSA_ENTRE_MANOS);
            }

            if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {

                if (GameFrame.getInstance().getLocalPlayer().isLoser()) {

                    ArrayList<Card> cartas = new ArrayList<>();

                    cartas.add(GameFrame.getInstance().getLocalPlayer().getPlayingCard1());
                    cartas.add(GameFrame.getInstance().getLocalPlayer().getPlayingCard2());

                    String lascartas = Card.collection2String(cartas);

                    for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                        if (!carta_comun.isTapada()) {
                            cartas.add(carta_comun);
                        }
                    }

                    Hand jugada = new Hand(cartas);

                    Helpers.GUIRunAndWait(new Runnable() {
                        public void run() {

                            GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();

                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setForeground(Color.WHITE);

                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setBackground(new Color(51, 153, 255));

                            GameFrame.getInstance().getLocalPlayer().getPlayer_action().setText(Translator.translate(" MUESTRAS (") + jugada.getName() + ")");

                        }
                    });

                    GameFrame.getInstance().getLocalPlayer().setMuestra(true);

                    GameFrame.getInstance().getRegistro().print("IWTSTH (" + iwtsther + ") -> " + GameFrame.getInstance().getLocalPlayer().getNickname() + Translator.translate(" MUESTRA (") + lascartas + ")" + (jugada != null ? " -> " + jugada : ""));

                    sqlNewShowcards(GameFrame.getInstance().getLocalPlayer().getNickname(), GameFrame.getInstance().getLocalPlayer().getDecision() == Player.FOLD);

                    sqlUpdateShowdownHand(GameFrame.getInstance().getLocalPlayer(), jugada);

                }

            }

        } else {

            GameFrame.getInstance().getRegistro().print(Translator.translate("EL SERVIDOR HA DENEGADO LA SOLICITUD IWTSTH DE ") + iwtsther);

            if (GameFrame.CINEMATICAS) {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        GifAnimationDialog gif_dialog = new GifAnimationDialog(GameFrame.getInstance().getFrame(), false, new ImageIcon(getClass().getResource("/cinematics/misc/iwtsth_no.gif")), 3000);

                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                        gif_dialog.setVisible(true);
                    }
                });

            }

            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(iwtsther)) {

                this.last_iwtsth_rejected = System.currentTimeMillis();
            }
        }

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar() && !GameFrame.getInstance().getLocalPlayer().isBotonMostrarActivado() && !GameFrame.getInstance().getLocalPlayer().isMuestra()) {
                    GameFrame.getInstance().getLocalPlayer().getPlayer_allin_button().setEnabled(true);
                }

                GameFrame.getInstance().getTapete().getCommunityCards().getBarra_tiempo().setIndeterminate(false);

            }
        });

        iwtsth = true;

        iwtsthing = false;

        iwtsthing_request = false;
    }

    public boolean isIwtsthing_request() {
        return iwtsthing_request;
    }

    public void IWTSTH_REQUEST(String iwtsther) {

        if (this.last_iwtsth_rejected == null || System.currentTimeMillis() - this.last_iwtsth_rejected > IWTSTH_ANTI_FLOOD_TIME) {

            iwtsthing_request = true;

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    GameFrame.getInstance().getTapete().getCommunityCards().getBarra_tiempo().setIndeterminate(true);

                }
            });

            if (!GameFrame.getInstance().isPartida_local()) {

                this.sendGAMECommandToServer("IWTSTH");

            } else {
                IWTSTH_HANDLER(iwtsther);
            }

        } else {
            Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), Translator.translate("TIENES QUE ESPERAR ") + Helpers.seconds2FullTime(Math.round(((float) (IWTSTH_ANTI_FLOOD_TIME - (System.currentTimeMillis() - this.last_iwtsth_rejected))) / 1000)) + Translator.translate(" PARA VOLVER A SOLICITAR IWTSTH"));
        }
    }

    public boolean isIWTSTH4LocalPlayerAuthorized() {

        return flop_players.contains(GameFrame.getInstance().getLocalPlayer());
    }

    private boolean NUEVA_MANO() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(false);
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().getForeground());
                GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setText("---"); //Para que se mantenga la altura de la fila del bote
                GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(false);
                GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(false);

                GameFrame.getInstance().getBarra_tiempo().setIndeterminate(true);

                if (!GameFrame.getInstance().isPartida_local()) {
                    GameFrame.getInstance().getExit_menu().setEnabled(false);
                    Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(false);
                }
            }
        });

        readyForNextHand();

        this.iwtsth = false;

        this.iwtsthing = false;

        this.iwtsthing_request = false;

        this.sqlite_id_hand = -1;

        this.conta_accion = 0;

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (!jugador.isExit() && jugador.isSpectator() && (Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0 || rebuy_now.containsKey(jugador.getNickname()))) {

                jugador.unsetSpectator();

                if (rebuy_now.containsKey(jugador.getNickname())) {
                    jugador.setSpectatorBB(true);
                }
            }
        }

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador.isActivo()) {
                jugador.getPlayingCard1().resetearCarta(false);
                jugador.getPlayingCard2().resetearCarta(false);
            }

        }

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
            carta.resetearCarta(false);
        }

        this.conta_mano++;

        if (GameFrame.MANOS == conta_mano && GameFrame.getInstance().isPartida_local()) {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    GameFrame.getInstance().getTapete().getCommunityCards().hand_label_left_click();
                }
            });
        }

        Bot.BOT_COMMUNITY_CARDS.makeEmpty();

        GameFrame.getInstance().getRegistro().print("\n*************** " + Translator.translate("MANO") + " (" + String.valueOf(this.conta_mano) + ") ***************");

        //Colocamos al dealer, CP y CG
        if (!GameFrame.RECOVER) {
            this.setPositions();
        }

        this.badbeat = false;

        this.jugada_ganadora = 0;

        this.perdedores.clear();

        this.fase = PREFLOP;

        this.cartas_resistencia = false;

        this.destapar_resistencia = false;

        this.apuesta_actual = this.ciega_grande;

        this.ultimo_raise = 0f;

        this.partial_raise_cum = 0f;

        this.conta_raise = 0;

        this.conta_bet = 0;

        if (Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {

            if (GameFrame.SONIDOS_CHORRA && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                Audio.playWavResource("misc/indivisible.wav");
            }

            Audio.playWavResource("misc/cash_register.wav");

            GameFrame.getInstance().getRegistro().print(Translator.translate("BOTE SOBRANTE NO DIVISIBLE") + " -> " + Helpers.float2String(bote_sobrante));

        }

        this.bote_total = this.bote_sobrante;

        this.bote = new Pot(0f);

        this.beneficio_bote_principal = null;

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador.isActivo()) {
                jugador.nuevaMano();
            }

        }

        this.rebuy_now.clear();

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                GameFrame.getInstance().getRebuy_now_menu().setSelected(false);
                GameFrame.getInstance().getRebuy_now_menu().setBackground(null);
                GameFrame.getInstance().getRebuy_now_menu().setOpaque(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
            }
        });

        saltar_primera_mano = false;

        //Actualizamos los datos en caso de estar en modo recover
        if (GameFrame.isRECOVER()) {

            GameFrame.getInstance().getRegistro().print("RECUPERANDO TIMBA...");

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    recover_dialog = new RecoverDialog(GameFrame.getInstance(), true);
                    recover_dialog.setLocationRelativeTo(recover_dialog.getParent());
                    recover_dialog.setVisible(true);

                }
            });

            recuperarDatosClavePartida();

            if (!GameFrame.getInstance().isPartida_local()) {
                sqlNewGame();
            }

            if (getJugadoresActivos() > 1 && !saltar_primera_mano) {

                if (GameFrame.getInstance().isPartida_local() || GameFrame.getInstance().getLocalPlayer().isActivo()) {
                    recuperarAccionesLocales();
                }

                if (!this.acciones_locales_recuperadas.isEmpty()) {
                    this.sincronizando_mano = true;
                } else {
                    GameFrame.getInstance().getRegistro().print("TIMBA RECUPERADA");
                    if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                        Audio.playWavResource("misc/startplay.wav");
                    }

                    if (GameFrame.MUSICA_AMBIENTAL) {
                        Audio.stopLoopMp3("misc/recovering.mp3");
                        Audio.playLoopMp3Resource("misc/background_music.mp3");
                    }
                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            recover_dialog.setVisible(false);
                            recover_dialog.dispose();
                            recover_dialog = null;
                            GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                        }
                    });
                }

            } else {
                GameFrame.getInstance().getRegistro().print("TIMBA RECUPERADA");
                if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                    Audio.playWavResource("misc/startplay.wav");
                }

                if (GameFrame.MUSICA_AMBIENTAL) {
                    Audio.stopLoopMp3("misc/recovering.mp3");
                    Audio.playLoopMp3Resource("misc/background_music.mp3");
                }
                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        recover_dialog.setVisible(false);
                        recover_dialog.dispose();
                        recover_dialog = null;
                        GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                    }
                });
            }

            this.update_game_seats = true;

            GameFrame.setRECOVER(false);

            this.game_recovered = 1;
        }

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

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    GameFrame.getInstance().getTapete().getCommunityCards().getHand_label().setVisible(true);
                    GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(true);

                }
            });

            actualizarContadoresTapete();

            Object lock = new Object();

            barajando = true;

            Helpers.threadRun(new Runnable() {
                public void run() {
                    if (GameFrame.CINEMATICAS) {
                        playing_cinematic = true;
                        ImageIcon icon = new ImageIcon(getClass().getResource("/cinematics/misc/shuffle.gif"));

                        if (icon != null) {

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    gif_dialog = new GifAnimationDialog(GameFrame.getInstance().getFrame(), false, icon);

                                    gif_dialog.setLocationRelativeTo(gif_dialog.getParent());

                                    gif_dialog.setVisible(true);
                                }
                            });

                            Helpers.threadRun(new Runnable() {

                                public void run() {
                                    Helpers.pausar(2500);

                                    Helpers.GUIRun(new Runnable() {
                                        public void run() {

                                            gif_dialog.dispose();

                                            playing_cinematic = false;
                                        }
                                    });
                                }
                            });

                            Audio.playWavResourceAndWait("misc/shuffle.wav");

                        } else {
                            playing_cinematic = false;

                            Audio.playWavResourceAndWait("misc/shuffle.wav");
                        }

                    } else {
                        playing_cinematic = false;
                        Audio.playWavResourceAndWait("misc/shuffle.wav");
                    }

                    barajando = false;

                    synchronized (lock) {

                        lock.notifyAll();
                    }
                }
            });

            if (GameFrame.getInstance().isPartida_local()) {

                if (permutacion_recuperada != null) {
                    permutacion_baraja = permutacion_recuperada;
                    permutacion_recuperada = null;
                } else {
                    permutacion_baraja = Helpers.getRandomIntegerSequence(Helpers.DECK_RANDOM_GENERATOR, 1, 52, Helpers.CSPRNG_RESHUFFLE ? permutacion_baraja : null);
                    preservarPermutacion(permutacion_baraja);
                }

                preCargarCartas();

                enviarCartasJugadoresRemotos();

                for (Player j : GameFrame.getInstance().getJugadores()) {
                    if (j != GameFrame.getInstance().getLocalPlayer()) {
                        j.ordenarCartas();
                    }
                }

            } else if (GameFrame.getInstance().getLocalPlayer().isActivo()) {

                //Leemos las cartas que nos han tocado del servidor
                cartas_locales_recibidas = recibirMisCartas();
            }

            if (barajando) {
                do {
                    synchronized (lock) {

                        try {
                            lock.wait(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } while (barajando);
            }

            repartir();

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                    GameFrame.getInstance().getExit_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(true);
                }
            });

            disableAllPlayersTimeout();

            return true;

        } else {

            permutacion_recuperada = null;

            //Si la mano no se ha podido recuperar le devolvemos la pasta a las ciegas
            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador.isActivo() && Helpers.float1DSecureCompare(0f, jugador.getBet()) < 0) {
                    jugador.pagar(jugador.getBet());
                }

            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    GameFrame.getInstance().getBarra_tiempo().setIndeterminate(false);
                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                    GameFrame.getInstance().getExit_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(true);
                }
            });

            disableAllPlayersTimeout();

            return false;

        }
    }

    private void sqlNewAction(Player current_player) {

        try {

            String sql = "INSERT INTO action(id_hand, player, counter, round, action, bet, conta_raise, response_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_hand);

            statement.setString(2, current_player.getNickname());

            statement.setInt(3, this.conta_accion);

            statement.setInt(4, this.fase);

            statement.setInt(5, current_player.getDecision());

            statement.setFloat(6, Helpers.floatClean1D(current_player.getBet()));

            statement.setInt(7, this.getConta_raise());

            statement.setInt(8, current_player.getResponseTime());

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private boolean sqlCheckGenuineRecoverAction(Player current_player) {

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
                //Existe la acción de ese jugador en esa mano, ahora vamos a ver si coincide lo que tenemos guardado con lo que ha enviado el servidor/jugador

                sql = "SELECT player FROM action WHERE id_hand=? and player=? and counter=? and action=?" + (current_player.getDecision() >= Player.BET ? " and bet=?" : "");

                statement = Helpers.getSQLITE().prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, this.sqlite_id_hand);

                statement.setString(2, current_player.getNickname());

                statement.setInt(3, this.conta_accion);

                statement.setInt(4, current_player.getDecision());

                if (current_player.getDecision() >= Player.BET) {
                    statement.setFloat(5, Helpers.floatClean1D(current_player.getBet()));
                }

                rs = statement.executeQuery();

                ret = rs.next();

            } else {
                //No existe esa acción para ese jugador, por lo que no podemos comparar y por tanto nos fiamos de lo que envía el servidor/jugador
                ret = true;
            }

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;
    }

    private void sqlNewShowcards(String jugador, boolean parguela) {

        String sql = "INSERT INTO showcards(id_hand, player, parguela) VALUES(?,?,?)";

        try {
            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setInt(1, this.sqlite_id_hand);

            statement.setString(2, jugador);

            statement.setBoolean(3, parguela);

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void sqlUpdateShowdownHand(Player jugador, Hand jugada) {

        String sql = "UPDATE showdown SET hole_cards=?, hand_cards=?, hand_val=? WHERE id_hand=? AND player=?";

        try {
            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setString(1, jugador.getPlayingCard1().isTapada() ? null : jugador.getPlayingCard1().toShortString() + "#" + jugador.getPlayingCard2().toShortString());

            statement.setString(2, (jugador.getPlayingCard1().isTapada() || jugada == null) ? null : Card.collection2ShortString(jugada.getMano()));

            statement.setInt(3, (jugador.getPlayingCard1().isTapada() || jugada == null) ? -1 : jugada.getVal());

            statement.setInt(4, this.sqlite_id_hand);

            statement.setString(5, jugador.getNickname());

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void sqlUpdateShowdownPay(Player jugador) {

        String sql = "UPDATE showdown SET pay=?, profit=? WHERE id_hand=? AND player=?";

        try {
            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setFloat(1, Helpers.floatClean1D(jugador.getPagar()));

            statement.setFloat(2, Helpers.floatClean1D(jugador.getPagar() - jugador.getBote()));

            statement.setInt(3, this.sqlite_id_hand);

            statement.setString(4, jugador.getNickname());

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void sqlNewShowdown(Player jugador, Hand jugada, boolean win) {

        String sql = "INSERT INTO showdown(id_hand, player, hole_cards, hand_cards, hand_val, winner, pay, profit) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setInt(1, this.sqlite_id_hand);

            statement.setString(2, jugador != null ? jugador.getNickname() : "-----");

            statement.setString(3, (jugador == null || jugador.getPlayingCard1().isTapada()) ? null : jugador.getPlayingCard1().toShortString() + "#" + jugador.getPlayingCard2().toShortString());

            statement.setString(4, (jugador == null || jugador.getPlayingCard1().isTapada() || jugada == null) ? null : Card.collection2ShortString(jugada.getMano()));

            statement.setInt(5, (jugador == null || jugador.getPlayingCard1().isTapada() || jugada == null) ? -1 : jugada.getVal());

            statement.setBoolean(6, win);

            statement.setFloat(7, Helpers.floatClean1D(jugador != null ? jugador.getPagar() : 0f));

            statement.setFloat(8, Helpers.floatClean1D(jugador != null ? jugador.getPagar() - jugador.getBote() : 0f));

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void sqlUpdateHandEnd(float bote_tot) {

        PreparedStatement statement;
        try {
            statement = Helpers.getSQLITE().prepareStatement("UPDATE hand SET end=?, pot=? WHERE id=?");
            statement.setQueryTimeout(30);
            statement.setLong(1, System.currentTimeMillis());
            statement.setFloat(2, Helpers.floatClean1D(bote_tot));
            statement.setInt(3, this.sqlite_id_hand);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {

            Player jugador = nick2player.get(entry.getKey());

            if (jugador != null) {

                this.sqlUpdateHandBalance(jugador.getNickname(), jugador.getStack() + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : 0f), jugador.getBuyin());

            } else {

                Float[] pasta = entry.getValue();
                this.sqlUpdateHandBalance(entry.getKey(), pasta[0], Math.round(pasta[1]));

            }
        }

        try {
            statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET play_time=? WHERE id=?");
            statement.setQueryTimeout(30);
            statement.setLong(1, GameFrame.getInstance().getConta_tiempo_juego());
            statement.setInt(2, this.sqlite_id_game);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sqlUpdateHandPlayers(ArrayList<Player> resistencia) {

        String jugadores = "";

        for (Player jugador : resistencia) {

            try {
                jugadores += "#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        String cards = null;

        String sql = "";

        if (this.fase == PREFLOP) {
            sql = "UPDATE hand SET preflop_players=?, com_cards=? WHERE id=?";
        } else if (this.fase == FLOP) {
            sql = "UPDATE hand SET flop_players=?, com_cards=? WHERE id=?";
            cards = Card.collection2ShortString(new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 3));
        } else if (this.fase == TURN) {
            sql = "UPDATE hand SET turn_players=?, com_cards=? WHERE id=?";
            cards = Card.collection2ShortString(new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())).subList(0, 4));
        } else if (this.fase == RIVER) {
            sql = "UPDATE hand SET river_players=?, com_cards=? WHERE id=?";
            cards = Card.collection2ShortString(new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes())));
        }

        PreparedStatement statement;
        try {
            statement = Helpers.getSQLITE().prepareStatement(sql);
            statement.setQueryTimeout(30);
            statement.setString(1, jugadores.substring(1));
            statement.setString(2, cards);
            statement.setInt(3, this.sqlite_id_hand);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public int getSqlite_game_id() {
        return sqlite_id_game;
    }

    public int getSqlite_hand_id() {
        return sqlite_id_hand;
    }

    private void sqlNewHandBalance(String nick, float stack, int buyin) {

        PreparedStatement statement;
        try {
            statement = Helpers.getSQLITE().prepareStatement("INSERT INTO balance(id_hand, player, stack, buyin) VALUES (?,?,?,?)");
            statement.setQueryTimeout(30);
            statement.setInt(1, this.sqlite_id_hand);
            statement.setString(2, nick);
            statement.setFloat(3, Helpers.floatClean1D(stack));
            statement.setInt(4, buyin);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sqlUpdateHandBalance(String nick, float stack, int buyin) {

        PreparedStatement statement;
        try {
            statement = Helpers.getSQLITE().prepareStatement("UPDATE balance SET stack=?, buyin=? WHERE id_hand=? and player=?");
            statement.setQueryTimeout(30);
            statement.setFloat(1, Helpers.floatClean1D(stack));
            statement.setInt(2, buyin);
            statement.setInt(3, this.sqlite_id_hand);
            statement.setString(4, nick);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void sqlNewGame() {

        try {

            String sql = "INSERT INTO game(start, players, buyin, sb, blinds_time, rebuy, server, blinds_time_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            GameFrame.GAME_START_TIMESTAMP = System.currentTimeMillis();

            statement.setLong(1, GameFrame.GAME_START_TIMESTAMP);

            String players = "";

            for (String nick : nicks_permutados) {

                if (nick2player.get(nick).isActivo()) {
                    players += "#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                }
            }

            statement.setString(2, players.substring(1));

            statement.setInt(3, GameFrame.BUYIN);

            statement.setFloat(4, Helpers.floatClean1D(GameFrame.CIEGA_PEQUEÑA));

            statement.setInt(5, GameFrame.CIEGAS_DOUBLE);

            statement.setBoolean(6, GameFrame.REBUY);

            statement.setString(7, GameFrame.getInstance().getSala_espera().getServer_nick());

            statement.setInt(8, GameFrame.CIEGAS_DOUBLE_TYPE);

            statement.executeUpdate();

            sqlite_id_game = statement.getGeneratedKeys().getInt(1);

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void sqlNewHand() {

        String jugadores = "";

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador.isActivo()) {

                try {
                    jugadores += "#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        try {

            String sql = "INSERT INTO hand(id_game, counter, sbval, blinds_double, dealer, sb, bb, start, preflop_players) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_game);

            statement.setInt(2, this.conta_mano);

            statement.setFloat(3, Helpers.floatClean1D(this.ciega_pequeña));

            statement.setInt(4, this.ciegas_double);

            statement.setString(5, this.dealer_nick);

            statement.setString(6, this.sb_nick);

            statement.setString(7, this.bb_nick);

            statement.setLong(8, System.currentTimeMillis());

            statement.setString(9, jugadores);

            statement.executeUpdate();

            sqlite_id_hand = statement.getGeneratedKeys().getInt(1);

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
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

    private void repartir() {

        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / this.getJugadoresActivos())));

        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().getAnimacion_menu().setEnabled(false);
                Helpers.TapetePopupMenu.ANIMACION_MENU.setEnabled(false);

            }
        });

        if (!GameFrame.ANIMACION_REPARTIR) {

            for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                carta.iniciarCarta();
            }

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (jugador.isActivo()) {

                    jugador.getPlayingCard1().iniciarCarta();
                    jugador.getPlayingCard2().iniciarCarta();
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

            if (jugador.isActivo() && GameFrame.ANIMACION_REPARTIR) {

                Audio.playWavResource("misc/deal.wav", false);

                if (jugador == GameFrame.getInstance().getLocalPlayer()) {

                    if (GameFrame.getInstance().isPartida_local()) {

                        jugador.getPlayingCard1().iniciarCarta();

                    } else {

                        String[] carta = cartas_locales_recibidas.get(0).split("_");

                        jugador.getPlayingCard1().iniciarConValorPalo(carta[0], carta[1]);
                    }

                    jugador.getPlayingCard1().destapar(false);

                } else {

                    jugador.getPlayingCard1().iniciarCarta();

                }
            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                if (GameFrame.getInstance().isPartida_local()) {

                    jugador.getPlayingCard1().iniciarCarta();

                } else {

                    String[] carta = cartas_locales_recibidas.get(0).split("_");

                    jugador.getPlayingCard1().iniciarConValorPalo(carta[0], carta[1]);
                }

                jugador.getPlayingCard1().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        do {
            GameFrame.getInstance().checkPause();

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && GameFrame.ANIMACION_REPARTIR) {

                Audio.playWavResource("misc/deal.wav", false);

                if (jugador == GameFrame.getInstance().getLocalPlayer()) {

                    if (GameFrame.getInstance().isPartida_local()) {

                        jugador.getPlayingCard2().iniciarCarta();

                    } else {

                        String[] carta = cartas_locales_recibidas.get(1).split("_");

                        jugador.getPlayingCard2().iniciarConValorPalo(carta[0], carta[1]);

                    }

                    jugador.getPlayingCard2().destapar(false);

                } else {

                    jugador.getPlayingCard2().iniciarCarta();
                }
            } else if (jugador.isActivo() && jugador == GameFrame.getInstance().getLocalPlayer()) {

                Audio.playWavResource("misc/deal.wav", false);

                if (GameFrame.getInstance().isPartida_local()) {

                    jugador.getPlayingCard2().iniciarCarta();

                } else {

                    String[] carta = cartas_locales_recibidas.get(1).split("_");

                    jugador.getPlayingCard2().iniciarConValorPalo(carta[0], carta[1]);
                }

                jugador.getPlayingCard2().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {

            GameFrame.getInstance().checkPause();

            if (carta == GameFrame.getInstance().getFlop1() || carta == GameFrame.getInstance().getTurn() || carta == GameFrame.getInstance().getRiver()) {

                if (GameFrame.ANIMACION_REPARTIR) {
                    Audio.playWavResource("misc/deal.wav", false);
                }

                Helpers.pausar(pausa);
            }

            if (GameFrame.ANIMACION_REPARTIR) {
                Audio.playWavResource("misc/deal.wav", false);
                carta.iniciarCarta();
            }

            Helpers.pausar(pausa);
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().getAnimacion_menu().setEnabled(true);
                Helpers.TapetePopupMenu.ANIMACION_MENU.setEnabled(true);

            }
        });

        GameFrame.getInstance().getLocalPlayer().ordenarCartas();
    }

    private void preCargarCartas() {

        int i = 0;

        while (!GameFrame.getInstance().getJugadores().get(i++).getNickname().equals(this.dealer_nick));

        int p = 0, j, pivote = (i + 1) % GameFrame.getInstance().getJugadores().size();

        //Repartirmos la primera carta a todos los jugadores
        j = pivote;

        do {

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo()) {

                jugador.getPlayingCard1().preIniciarConValorNumerico(permutacion_baraja[p++]);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        //Repartirmos la segunda carta a todos los jugadores
        do {

            Player jugador = GameFrame.getInstance().getJugadores().get(j);

            if (jugador.isActivo()) {

                jugador.getPlayingCard2().preIniciarConValorNumerico(permutacion_baraja[p++]);
            }

            j = (j + 1) % GameFrame.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {

            //Se quema una carta antes de cada calle
            if (carta == GameFrame.getInstance().getFlop1() || carta == GameFrame.getInstance().getTurn() || carta == GameFrame.getInstance().getRiver()) {
                p = p + 1;
            }

            carta.preIniciarConValorNumerico(permutacion_baraja[p++]);
        }

    }

    private void enviarCartasJugadoresRemotos() {

        long start = System.currentTimeMillis();

        ArrayList<String> pendientes = new ArrayList<>();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo() && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                pendientes.add(jugador.getNickname());
            }
        }

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        boolean timeout = false;

        byte[] iv = new byte[16];

        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        do {

            String command = "GAME#" + String.valueOf(id) + "#YOURCARDS";

            for (Player jugador : GameFrame.getInstance().getJugadores()) {

                if (pendientes.contains(jugador.getNickname())) {

                    Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());

                    if (p != null && !p.isCpu()) {

                        String carta1 = jugador.getPlayingCard1().toShortString();
                        String carta2 = jugador.getPlayingCard2().toShortString();
                        p.writeCommandFromServer(Helpers.encryptCommand(command + "#" + carta1 + "@" + carta2, p.getAes_key(), iv, p.getHmac_key()));

                    }
                }
            }

            //Esperamos confirmaciones
            this.waitSyncConfirmations(id, pendientes);

            if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {
                int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

                // 0=yes, 1=no, 2=cancel
                if (input == 1) {

                    timeout = true;

                } else {
                    start = System.currentTimeMillis();
                }
            }

            if (!pendientes.isEmpty()) {

                for (String nick : pendientes) {
                    nick2player.get(nick).setTimeout(true);
                    try {
                        this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }
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
                            ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.decodeBase64(partes[3]));
                            in = new ObjectInputStream(byteIn);
                            map = (HashMap<String, Object>) in.readObject();
                            map.put("hand_id", -1);
                        } catch (IOException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ClassNotFoundException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        } finally {
                            try {
                                in.close();
                            } catch (IOException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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
                            actions = !"*".equals(partes[3]) ? new String(Base64.decodeBase64(partes[3]), "UTF-8") : "";
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {
                    synchronized (this.getReceived_commands()) {
                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                String command = "GAME#" + String.valueOf(id) + "#RECOVERDATA#" + Base64.encodeBase64String(byteOut.toByteArray());

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (pendientes.contains(jugador.getNickname())) {

                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());

                        if (p != null && !p.isCpu()) {

                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));

                        }
                    }
                }

                //Esperamos confirmaciones
                this.waitSyncConfirmations(id, pendientes);

                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {
                    int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

                    // 0=yes, 1=no, 2=cancel
                    if (input == 1) {

                        timeout = true;

                    } else {
                        start = System.currentTimeMillis();
                    }
                }
                if (!pendientes.isEmpty()) {

                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        try {
                            this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }
            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    out.close();
                } catch (IOException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                String command = "GAME#" + String.valueOf(id) + "#ACTIONDATA#" + ((datos == null || datos.isEmpty()) ? "*" : Base64.encodeBase64String(datos.getBytes("UTF-8")));

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (pendientes.contains(jugador.getNickname())) {

                        Participant p = GameFrame.getInstance().getParticipantes().get(jugador.getNickname());

                        if (p != null && !p.isCpu()) {

                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));

                        }
                    }
                }

                //Esperamos confirmaciones
                this.waitSyncConfirmations(id, pendientes);

                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {
                    int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

                    // 0=yes, 1=no, 2=cancel
                    if (input == 1) {

                        timeout = true;

                    } else {
                        start = System.currentTimeMillis();
                    }
                }
                if (!pendientes.isEmpty()) {

                    for (String nick : pendientes) {
                        nick2player.get(nick).setTimeout(true);
                        try {
                            this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }
            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

    public void sendGAMECommandToServer(String command, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();

        pendientes.add(GameFrame.getInstance().getSala_espera().getServer_nick());

        int id = Helpers.CSPRNG_GENERATOR.nextInt();

        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

        do {

            try {

                GameFrame.getInstance().getSala_espera().writeCommandToServer(Helpers.encryptCommand(full_command, GameFrame.getInstance().getSala_espera().getLocal_client_aes_key(), GameFrame.getInstance().getSala_espera().getLocal_client_hmac_key()));

                if (confirmation) {
                    this.waitSyncConfirmations(id, pendientes);
                }

            } catch (IOException ex) {

                if (confirmation) {

                    synchronized (GameFrame.getInstance().getSala_espera().getLocalClientSocketLock()) {

                        try {
                            GameFrame.getInstance().getSala_espera().getLocalClientSocketLock().wait(1000);
                        } catch (InterruptedException ex1) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex1);
                        }
                    }
                }
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

        //Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!pending.isEmpty() && !timeout) {

            Object[] confirmation;

            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                while (!WaitingRoomFrame.getInstance().getReceived_confirmations().isEmpty()) {

                    confirmation = WaitingRoomFrame.getInstance().getReceived_confirmations().poll();

                    if (confirmation != null && confirmation[0] != null && confirmation[1] != null && (int) confirmation[1] == id + 1) {

                        pending.remove((String) confirmation[0]);

                        if (nick2player.containsKey(confirmation[0])) {

                            nick2player.get(confirmation[0]).setTimeout(false);

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
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }

        }

        return !pending.isEmpty();
    }

    public Object[] readActionFromRemotePlayer(Player jugador) {

        long start = System.currentTimeMillis();

        boolean ok, timeout;

        Object[] action = new Object[2];

        do {

            ok = false;

            timeout = false;

            if (!jugador.isExit()) {

                synchronized (this.getReceived_commands()) {

                    ArrayList<String> rejected = new ArrayList<>();

                    while (!ok && !this.getReceived_commands().isEmpty()) {

                        String comando = this.received_commands.poll();

                        String[] partes = comando.split("#");

                        if (!jugador.isExit()) {

                            try {
                                if (partes[2].equals("ACTION") && new String(Base64.decodeBase64(partes[3]), "UTF-8").equals(jugador.getNickname())) {
                                    ok = true;
                                    action[0] = Integer.valueOf(partes[4]);

                                    if (((Integer) action[0]) == Player.BET) {
                                        action[1] = Float.valueOf(partes[5]);
                                    } else if (((Integer) action[0]) == Player.ALLIN) {

                                        action[1] = partes.length > 5 ? partes[5] : "";

                                    } else {
                                        action[1] = 0f;
                                    }

                                } else {
                                    rejected.add(comando);
                                }
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                    }

                    if (!rejected.isEmpty()) {
                        this.getReceived_commands().addAll(rejected);
                        rejected.clear();
                    }

                }

                if (!ok) {

                    if (GameFrame.getInstance().checkPause()) {
                        start = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {

                        if (GameFrame.getInstance().isPartida_local()) {

                            jugador.setTimeout(true);

                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")), jugador.getNickname(), false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), jugador.getNickname() + Translator.translate(" parece que perdió la conexión y no ha vuelto a conectar (se le eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?"));

                            // 0=yes, 1=no, 2=cancel
                            if (input == 1) {

                                timeout = true;

                                this.remotePlayerQuit(jugador.getNickname());

                            } else {
                                start = System.currentTimeMillis();
                            }

                        } else {

                            //Comprobamos si la conexión con el servidor está funcionando
                            this.sendGAMECommandToServer("PING");

                            start = System.currentTimeMillis();
                        }

                    } else {

                        synchronized (this.getReceived_commands()) {

                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                }

            }
        } while (!ok && !jugador.isExit() && !timeout);

        if (jugador.isExit()) {

            action[0] = -1;
            action[1] = 0f;

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

    private void destaparCartaComunitaria(int fase) {

        Helpers.pausar(1000);

        GameFrame.getInstance().checkPause();

        switch (fase) {
            case FLOP:
                destaparFlop();
                break;
            case TURN:
                destaparTurn();
                break;
            case RIVER:
                destaparRiver();
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

    private ArrayList<Player> rondaApuestas(int fase, ArrayList<Player> resisten) {

        disableAllPlayersTimeout();

        Iterator<Player> iterator = resisten.iterator();

        while (iterator.hasNext()) {
            Player jugador = iterator.next();

            if (!jugador.isActivo()) {
                iterator.remove();
            }

            if (GameFrame.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).getBot() != null) {

                ((RemotePlayer) jugador).getBot().resetBot();
            }
        }

        this.fase = fase;

        sqlUpdateHandPlayers(resisten);

        if (fase > PREFLOP) {

            if (GameFrame.getInstance().isPartida_local()) {

                //Enviamos las cartas comunitarias de esta fase a todos jugadores remotos
                String comando = null;

                switch (fase) {
                    case FLOP:

                        flop_players.clear();

                        flop_players.addAll(resisten);
                        comando = "FLOPCARDS#" + GameFrame.getInstance().getCartas_comunes()[0].toShortString() + "#" + GameFrame.getInstance().getCartas_comunes()[1].toShortString() + "#" + GameFrame.getInstance().getCartas_comunes()[2].toShortString();

                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(GameFrame.getInstance().getFlop1().getValorNumerico() - 2, Bot.getCardSuit(GameFrame.getInstance().getFlop1())));
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(GameFrame.getInstance().getFlop2().getValorNumerico() - 2, Bot.getCardSuit(GameFrame.getInstance().getFlop2())));
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(GameFrame.getInstance().getFlop3().getValorNumerico() - 2, Bot.getCardSuit(GameFrame.getInstance().getFlop3())));

                        break;
                    case TURN:
                        comando = "TURNCARD#" + GameFrame.getInstance().getCartas_comunes()[3].toShortString();
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(GameFrame.getInstance().getTurn().getValorNumerico() - 2, Bot.getCardSuit(GameFrame.getInstance().getTurn())));

                        break;
                    case RIVER:
                        comando = "RIVERCARD#" + GameFrame.getInstance().getCartas_comunes()[4].toShortString();
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(GameFrame.getInstance().getRiver().getValorNumerico() - 2, Bot.getCardSuit(GameFrame.getInstance().getRiver())));

                        break;
                    default:
                        break;
                }

                broadcastGAMECommandFromServer(comando, null);

            } else {

                //Recibimos las cartas comunitarias de esta fase del servidor
                String carta;
                String[] cartas, partes;

                switch (fase) {
                    case FLOP:

                        flop_players.clear();

                        flop_players.addAll(resisten);

                        cartas = recibirFlop();

                        for (int i = 0; i < 3; i++) {

                            partes = cartas[i].split("_");

                            GameFrame.getInstance().getCartas_comunes()[i].actualizarValorPalo(partes[0], partes[1]);
                        }

                        break;
                    case TURN:
                        carta = recibirTurn();

                        partes = carta.split("_");

                        GameFrame.getInstance().getCartas_comunes()[3].actualizarValorPalo(partes[0], partes[1]);

                        break;
                    case RIVER:
                        carta = recibirRiver();

                        partes = carta.split("_");

                        GameFrame.getInstance().getCartas_comunes()[4].actualizarValorPalo(partes[0], partes[1]);
                        break;
                    default:
                        break;
                }

            }

            //Destapamos una carta
            destaparCartaComunitaria(fase);

        }

        if (puedenApostar(resisten) > 0 && !this.cartas_resistencia) {

            if (fase > PREFLOP) {
                this.apuesta_actual = 0f;

                this.ultimo_raise = 0f;

                this.conta_raise = 0;

                this.conta_bet = 0;

                for (Player jugador : resisten) {

                    jugador.setBet(0f);
                }
            }

            int conta_pos = 0;

            if (fase == PREFLOP) {

                limpers = 0;

                while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.utg_nick)) {
                    conta_pos++;
                }

            } else {

                if (nick2player.containsKey(this.sb_nick) && nick2player.get(this.sb_nick).isActivo()) {

                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.sb_nick)) {
                        conta_pos++;
                    }

                } else {

                    while (!GameFrame.getInstance().getJugadores().get(conta_pos).getNickname().equals(this.bb_nick)) {
                        conta_pos++;
                    }
                }
            }

            int end_pos = conta_pos;

            int decision;

            resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), null, false);

            actualizarContadoresTapete();

            do {
                GameFrame.getInstance().checkPause();

                turno++;

                Object[] accion_recuperada = null;

                Object[] action = null;

                Player current_player = GameFrame.getInstance().getJugadores().get(conta_pos);

                if (current_player.isActivo() && current_player.getDecision() != Player.FOLD && current_player.getDecision() != Player.ALLIN) {

                    if (GameFrame.AUTO_ACTION_BUTTONS && current_player != GameFrame.getInstance().getLocalPlayer() && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.FOLD && GameFrame.getInstance().getLocalPlayer().getDecision() != Player.ALLIN) {
                        GameFrame.getInstance().getLocalPlayer().activarPreBotones();
                    }

                    float old_player_bet = current_player.getBet();

                    //Esperamos a que el jugador tome su decisión
                    if (current_player == GameFrame.getInstance().getLocalPlayer()) {

                        current_player.esTuTurno();

                        //SOMOS NOSOTROS (jugador local)
                        if (this.isSincronizando_mano() && (accion_recuperada = siguienteAccionLocalRecuperada(current_player.getNickname())) != null) {

                            LocalPlayer localplayer = (LocalPlayer) current_player;

                            localplayer.setClick_recuperacion(true);

                            if ((int) accion_recuperada[0] == Player.FOLD) {

                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {
                                        localplayer.getPlayer_fold_button().doClick();
                                        localplayer.setClick_recuperacion(false);
                                    }
                                });
                            } else if ((int) accion_recuperada[0] == Player.CHECK) {
                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {
                                        localplayer.getPlayer_check_button().doClick();
                                        localplayer.setClick_recuperacion(false);
                                    }
                                });
                            } else if ((int) accion_recuperada[0] == Player.ALLIN) {

                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {
                                        localplayer.getPlayer_allin_button().doClick();
                                        localplayer.setClick_recuperacion(false);
                                    }
                                });
                            } else if ((int) accion_recuperada[0] == Player.BET) {
                                localplayer.setApuesta_recuperada((float) accion_recuperada[1]);
                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {

                                        localplayer.getPlayer_bet_button().doClick();
                                        localplayer.setClick_recuperacion(false);
                                    }
                                });

                            }
                        }

                        do {
                            synchronized (getLock_apuestas()) {
                                try {
                                    getLock_apuestas().wait(WAIT_QUEUES);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                        } while (current_player.isTurno());

                        decision = current_player.getDecision();

                        if (!current_player.isExit()) {

                            String comando = null;
                            try {
                                comando = "ACTION#" + Base64.encodeBase64String(current_player.getNickname().getBytes("UTF-8")) + "#" + String.valueOf(decision) + (decision == Player.BET ? "#" + Helpers.float2String(current_player.getBet()) : "");
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            if (decision == Player.ALLIN && this.current_local_cinematic_b64 != null) {

                                comando += "#" + this.current_local_cinematic_b64;
                            }

                            if (GameFrame.getInstance().isPartida_local()) {

                                //Mandamos nuestra decisión a todos los jugadores
                                broadcastGAMECommandFromServer(comando, null);

                            } else {

                                //Mandamos nuestra decisión al servidor
                                this.sendGAMECommandToServer(comando);
                            }
                        }

                    } else {

                        //ES OTRO JUGADOR
                        current_player.esTuTurno();

                        if (!GameFrame.getInstance().isPartida_local() || !GameFrame.getInstance().getParticipantes().get(current_player.getNickname()).isCpu()) {

                            action = this.readActionFromRemotePlayer(current_player);

                        } else {

                            if (!this.isSincronizando_mano() || (action = siguienteAccionLocalRecuperada(current_player.getNickname())) == null) {

                                long start = System.currentTimeMillis();

                                float call_required = getApuesta_actual() - current_player.getBet();

                                int decision_loki = ((RemotePlayer) current_player).getBot().calculateBotDecision(resisten.size() - 1);

                                action = new Object[]{decision_loki, 0f};

                                switch (decision_loki) {

                                    case Player.FOLD:

                                        if (Helpers.float1DSecureCompare(0f, this.getApuesta_actual()) == 0 || Helpers.float1DSecureCompare(current_player.getBet(), this.getApuesta_actual()) == 0) {
                                            action = new Object[]{Player.CHECK, 0f};
                                        }

                                        break;

                                    case Player.CHECK:

                                        if (Helpers.float1DSecureCompare(current_player.getStack(), call_required) <= 0) {

                                            action = new Object[]{Player.ALLIN, ""};
                                        }

                                        break;

                                    case Player.BET:

                                        if (Helpers.float1DSecureCompare(current_player.getStack(), call_required) <= 0) {

                                            action = new Object[]{Player.ALLIN, ""};
                                        } else {

                                            float b = ((RemotePlayer) current_player).getBot().getBetSize();

                                            if (Helpers.float1DSecureCompare(current_player.getStack() * 0.75f, b - current_player.getBet()) <= 0) {

                                                action = new Object[]{Player.ALLIN, ""};

                                            } else if (puedenApostar(GameFrame.getInstance().getJugadores()) <= 1) {

                                                action = new Object[]{Player.CHECK, 0f};

                                            } else {

                                                action = new Object[]{Player.BET, b};

                                            }

                                        }

                                        break;
                                }

                                long bot_elapsed_time = System.currentTimeMillis() - start;

                                if (Bot.BOT_THINK_TIME - bot_elapsed_time > 0L) {
                                    Helpers.pausar(Bot.BOT_THINK_TIME - bot_elapsed_time);
                                }
                            }
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

                        if (!current_player.isExit()) {

                            ((RemotePlayer) current_player).setDecisionFromRemotePlayer(decision, (float) action[1]);

                            do {
                                synchronized (getLock_apuestas()) {
                                    try {
                                        getLock_apuestas().wait(WAIT_QUEUES);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }

                            } while (current_player.isTurno());

                            if (GameFrame.getInstance().isPartida_local()) {

                                String comando = null;
                                try {
                                    comando = "ACTION#" + Base64.encodeBase64String(current_player.getNickname().getBytes("UTF-8")) + "#" + String.valueOf(decision) + (decision == Player.BET ? "#" + Helpers.float2String((float) action[1]) : "");
                                } catch (UnsupportedEncodingException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                }

                                if (decision == Player.ALLIN && this.current_remote_cinematic_b64 != null) {

                                    comando += "#" + this.current_remote_cinematic_b64;
                                }

                                //Le mandamos la decisión del jugador remoto al resto de jugadores
                                broadcastGAMECommandFromServer(comando, current_player.getNickname());

                            }

                        }
                    }

                    if (!current_player.isExit()) {

                        GameFrame.getInstance().getRegistro().print(current_player.getLastActionString());

                        if (current_player.getDecision() != Player.FOLD) {

                            this.apuestas += current_player.getBet() - old_player_bet;

                            this.bote_total += current_player.getBet() - old_player_bet;

                            if (decision == Player.BET || (decision == Player.ALLIN && Helpers.float1DSecureCompare(this.apuesta_actual, current_player.getBet()) < 0)) {

                                //El jugador actual subió la apuesta actual
                                boolean partial_raise = false;

                                float min_raise = Helpers.float1DSecureCompare(0f, getUltimo_raise()) < 0 ? getUltimo_raise() : Helpers.floatClean1D(getCiega_grande());

                                float current_raise = current_player.getBet() - this.apuesta_actual + this.partial_raise_cum;

                                if (Helpers.float1DSecureCompare(min_raise, current_raise) <= 0) {

                                    this.ultimo_raise = current_raise;
                                    this.partial_raise_cum = 0f;
                                    this.conta_raise++;

                                } else if (decision == Player.ALLIN) {

                                    //El jugador va ALL-IN y ha resubido pero NO lo suficiente para considerarlo una resubida
                                    partial_raise = true;
                                    this.partial_raise_cum += current_player.getBet() - this.apuesta_actual;
                                }

                                this.conta_bet++;

                                this.apuesta_actual = current_player.getBet();

                                resetBetPlayerDecisions(GameFrame.getInstance().getJugadores(), partial_raise ? (this.last_aggressor != null ? this.last_aggressor.getNickname() : null) : current_player.getNickname(), partial_raise);

                                if (fase == PREFLOP) {
                                    limpers = 0;
                                }

                                end_pos = conta_pos;

                            } else if (fase == PREFLOP && Helpers.float1DSecureCompare(this.apuesta_actual, this.getCiega_grande()) == 0 && !current_player.getNickname().equals(this.getBb_nick()) && !current_player.getNickname().equals(this.getSb_nick())) {
                                limpers++;
                            }

                        } else {
                            resisten.remove(current_player);
                        }
                    } else {
                        resisten.remove(current_player);
                    }

                    try {
                        this.acciones.add(Base64.encodeBase64String(current_player.getNickname().getBytes("UTF-8")) + "#" + String.valueOf(current_player.getDecision()) + (current_player.getDecision() == Player.BET ? "#" + Helpers.float2String(current_player.getBet()) : ""));
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else if (current_player.getDecision() != Player.ALLIN) {
                    resisten.remove(current_player);

                    try {
                        this.acciones.add(Base64.encodeBase64String(current_player.getNickname().getBytes("UTF-8")) + "#" + String.valueOf(current_player.getDecision()) + (current_player.getDecision() == Player.BET ? "#" + Helpers.float2String(current_player.getBet()) : ""));
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                actualizarContadoresTapete();

                conta_pos++;

                if (conta_pos >= GameFrame.getInstance().getJugadores().size()) {
                    conta_pos %= GameFrame.getInstance().getJugadores().size();
                }

                if (current_player.isActivo()) {
                    this.conta_accion++;

                    if (!this.isSincronizando_mano() || this.conta_accion > this.tot_acciones_recuperadas) {
                        this.sqlNewAction(current_player);
                    } else if (GameFrame.getInstance().isPartida_local()) {

                        String recover_action = current_player.getNickname() + " " + String.valueOf(current_player.getDecision()) + " " + Helpers.float2String(current_player.getBet()) + " COUNTER: " + String.valueOf(this.conta_accion) + " HAND ID: " + String.valueOf(this.sqlite_id_hand);

                        if (this.sqlCheckGenuineRecoverAction(current_player)) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.INFO, "RECOVER ACTION OK -> " + recover_action);
                        } else {
                            Logger.getLogger(Crupier.class.getName()).log(Level.WARNING, "BAD RECOVER ACTION! -> " + recover_action);
                        }
                    }
                }

                while (isPlaying_cinematic()) {

                    synchronized (getLock_apuestas()) {
                        try {
                            getLock_apuestas().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

            } while (conta_pos != end_pos && resisten.size() > 1);

            this.apuestas = 0f;

            actualizarContadoresTapete();

            GameFrame.getInstance().hideTapeteApuestas();
        }

        if (resisten.size() > 1 && puedenApostar(resisten) <= 1) {

            this.destapar_resistencia = true;

            if (resisten.contains(GameFrame.getInstance().getLocalPlayer())) {

                GameFrame.getInstance().getLocalPlayer().desactivarControles();
            }

            procesarCartasResistencia(resisten, true);
        }

        if (this.fase == Crupier.PREFLOP) {
            nick2player.get(this.utg_nick).disableUTG();
        }

        return (resisten.size() > 1 && fase < RIVER && getJugadoresActivos() > 1) ? rondaApuestas(fase + 1, resisten) : resisten;
    }

    private void sentarParticipantes() {

        String pivote = GameFrame.getInstance().getNick_local();

        int i = 0;

        while (!this.nicks_permutados[i].equals(pivote)) {
            i++;
        }

        for (int j = 0; j < this.nicks_permutados.length; j++) {

            GameFrame.getInstance().getJugadores().get(j).setNickname(this.nicks_permutados[(j + i) % this.nicks_permutados.length]);
        }

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

                        p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));

                    }
                }

                if (confirmation) {
                    //Esperamos confirmaciones y en caso de que alguna no llegue pasado un tiempo volvermos a enviar todos los que fallaron la confirmación la primera vez
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
                            try {
                                this.broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                    }

                    if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT) {
                        int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

                        // 0=yes, 1=no, 2=cancel
                        if (input == 1) {

                            timeout = true;

                            if (!nick2player.isEmpty()) {
                                for (String nick : pendientes) {
                                    if (!nick2player.get(nick).isExit()) {
                                        this.remotePlayerQuit(nick);
                                    }
                                }
                            } else {
                                for (String nick : pendientes) {
                                    GameFrame.getInstance().getParticipantes().get(nick).exitAndCloseSocket();
                                }
                            }

                        } else {
                            start = System.currentTimeMillis();
                        }
                    }
                }

            } while (confirmation && !pendientes.isEmpty() && !timeout);
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

    private void calcularPosiciones() {

        //DEAD BUTTON STRATEGY
        String old_bb = this.bb_nick;

        int bb_pos = 0;

        if (this.bb_nick == null) {

            this.bb_nick = this.nicks_permutados[0];

            bb_pos = 0;

        } else {

            int old_bb_pos = permutadoNick2Pos(this.bb_nick);

            String new_bb = null;

            if (old_bb_pos == -1) {

                try {
                    //LA CIEGA GRANDE SE HA IDO

                    String[] asientos = this.sqlRecoverGameSeats().split("#");

                    String grande_b64 = Base64.encodeBase64String(this.bb_nick.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i && this.permutadoNick2Pos(new String(Base64.decodeBase64(asientos[i]), "UTF-8")) == -1) {

                        i = (i + 1) % asientos.length;
                    }

                    new_bb = new String(Base64.decodeBase64(asientos[i]), "UTF-8");

                    bb_pos = permutadoNick2Pos(new_bb);

                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else {

                bb_pos = old_bb_pos + 1;

                new_bb = permutadoPos2Nick(bb_pos);
            }

            while (!this.nick2player.containsKey(new_bb) || !this.nick2player.get(new_bb).isActivo()) {

                new_bb = permutadoPos2Nick(++bb_pos);

            }

            this.bb_nick = new_bb;
        }

        if (getJugadoresActivos() == 2) {

            for (Player j : GameFrame.getInstance().getJugadores()) {

                if (j.isActivo() && !j.getNickname().equals(this.bb_nick)) {
                    this.sb_nick = j.getNickname();
                    break;
                }

            }

            this.dealer_nick = this.sb_nick;

            this.utg_nick = this.dealer_nick;

        } else {

            //UTG
            int utg_pos = bb_pos + 1;

            String new_utg = permutadoPos2Nick(utg_pos);

            while (!this.nick2player.containsKey(new_utg) || !this.nick2player.get(new_utg).isActivo()) {

                new_utg = permutadoPos2Nick(++utg_pos);

            }

            this.utg_nick = new_utg;

            //DEALER
            int de_pos;

            String new_dealer;

            if (this.sb_nick != null) {

                new_dealer = this.sb_nick;

                de_pos = permutadoNick2Pos(new_dealer);

                if (de_pos == -1) {

                    try {

                        String[] asientos = this.sqlRecoverGameSeats().split("#");

                        String dealer_b64 = Base64.encodeBase64String(new_dealer.getBytes("UTF-8"));

                        int i = 0;

                        while (i < asientos.length && !asientos[i].equals(dealer_b64)) {

                            i++;
                        }

                        int j = i;

                        i--;

                        if (i < 0) {
                            i += asientos.length;
                        }

                        while (j != i && this.permutadoNick2Pos(new String(Base64.decodeBase64(asientos[i]), "UTF-8")) == -1) {

                            i--;

                            if (i < 0) {
                                i += asientos.length;
                            }
                        }

                        new_dealer = new String(Base64.decodeBase64(asientos[i]), "UTF-8");

                        de_pos = permutadoNick2Pos(new_dealer);

                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }

            } else {

                de_pos = bb_pos - 2;
                new_dealer = permutadoPos2Nick(de_pos);

            }

            while (!this.nick2player.containsKey(new_dealer) || !this.nick2player.get(new_dealer).isActivo()) {

                new_dealer = permutadoPos2Nick(--de_pos);

            }

            this.dealer_nick = new_dealer;

            //SB
            if (old_bb != null) {

                this.sb_nick = old_bb;

            } else {

                this.sb_nick = permutadoPos2Nick(bb_pos - 1);
            }

        }
    }

    private void setPositions() {

        if (GameFrame.getInstance().isPartida_local()) {

            this.calcularPosiciones();

            String comando = null;

            boolean doblar_ciegas = this.checkDoblarCiegas();

            try {

                comando = "POSITIONS#" + Base64.encodeBase64String(this.utg_nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(this.bb_nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(this.sb_nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(this.dealer_nick != null ? this.dealer_nick.getBytes("UTF-8") : "".getBytes("UTF-8")) + "#" + String.valueOf(GameFrame.getInstance().getConta_tiempo_juego()) + (doblar_ciegas ? "#1" : "#0");

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

            broadcastGAMECommandFromServer(comando, null);

            if (doblar_ciegas) {
                this.doblarCiegas();
            }

        } else {

            //Recibimos las posiciones utg, bb, sb, dealer calculados por el servidor
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

            String preflop_players = (String) this.sqlRecoverGameKeyData().get("preflop_players");

            ArrayList<String> permutados = new ArrayList<>();

            for (String b64 : sitiosb64) {

                String nick = new String(Base64.decodeBase64(b64), "UTF-8");

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

                    String grande_b64 = Base64.encodeBase64String(grande.getBytes("UTF-8"));

                    int i = 0;

                    while (i < asientos.length && !asientos[i].equals(grande_b64)) {

                        i++;
                    }

                    int j = i;

                    i = (i + 1) % asientos.length;

                    while (j != i && !permutados.contains(new String(Base64.decodeBase64(asientos[i]), "UTF-8"))) {

                        i = (i + 1) % asientos.length;
                    }

                    grande = new String(Base64.decodeBase64(asientos[i]), "UTF-8");
                }

                for (String nick : permutados) {

                    permutados_aux.add(nick);

                    if (nick.equals(grande)) {

                        //Los jugadores nuevos los colocamos después de la CIEGA GRANDE ACTUAL
                        Collections.shuffle(actuales, Helpers.CSPRNG_GENERATOR);
                        permutados_aux.addAll(actuales);
                        actuales.clear();
                    }
                }

                permutados = permutados_aux;
            }

            return permutados.isEmpty() ? actuales : permutados;

        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private void sqlUpdateGameLastDeck(String deck) {

        try {
            String sql = "UPDATE game SET last_deck=? WHERE id=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setString(1, deck);
            statement.setInt(2, this.sqlite_id_game);
            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private String sqlRecoverGameLastDeck() {

        String ret = null;

        try {
            String sql = "SELECT last_deck from game WHERE id=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_game);

            ResultSet rs = statement.executeQuery();

            rs.next();

            ret = rs.getString("last_deck");

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;
    }

    private String sqlRecoverGameSeats() {

        String ret = null;

        try {
            String sql = "SELECT players from game WHERE id=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_game);

            ResultSet rs = statement.executeQuery();

            rs.next();

            ret = rs.getString("players");

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;
    }

    private void sqlUpdateGameSeats(String players) {

        try {
            String sql = "UPDATE game SET players=? WHERE id=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setString(1, players);

            statement.setInt(2, this.sqlite_id_game);

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String sqlRecoverHandActions() {

        String ret = null;

        try {
            String sql = "select player, action, round(bet,2) as bet from action where action.id_hand=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_hand);

            ResultSet rs = statement.executeQuery();

            String actions = "";

            while (rs.next()) {

                actions += Base64.encodeBase64String(rs.getString("player").getBytes("UTF-8")) + "#" + String.valueOf(rs.getInt("action")) + "#" + String.valueOf(rs.getFloat("bet")) + "@";
            }
            ret = actions;

            statement.close();

            Logger.getLogger(Crupier.class.getName()).log(Level.INFO, actions);

        } catch (SQLException | UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;
    }

    public HashMap<String, Object> sqlRecoverGameKeyData() {

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

            sql = "select balance.player as PLAYER, round(balance.stack,2) as STACK, balance.buyin as BUYIN from balance,hand,game where balance.id_hand=hand.id and game.id=? and hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?)";

            statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_game);

            statement.setInt(2, this.sqlite_id_game);

            rs = statement.executeQuery();

            ArrayList<String> balance = new ArrayList<>();

            while (rs.next()) {

                balance.add(Base64.encodeBase64String(rs.getString("PLAYER").getBytes("UTF-8")) + "|" + rs.getFloat("STACK") + "|" + rs.getInt("BUYIN"));
            }

            map.put("balance", String.join("@", balance));

            statement.close();

        } catch (SQLException | UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return map;
    }

    private HashMap<String, Object> sqlRecoverGamePositions() {

        HashMap<String, Object> map = null;

        try {

            String sql = "select hand.dealer as dealer, hand.sb as sb, hand.bb as bb from game,hand where hand.id=(SELECT max(hand.id) from hand,game where hand.id_game=game.id and hand.id_game=?) and game.id=hand.id_game and hand.id_game=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, this.sqlite_id_game);

            statement.setInt(2, this.sqlite_id_game);

            ResultSet rs = statement.executeQuery();

            rs.next();

            map = new HashMap<>();

            map.put("dealer", rs.getString("dealer"));
            map.put("sb", rs.getString("sb"));
            map.put("bb", rs.getString("bb"));

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return map;
    }

    private void preservarPermutacion(Integer[] permutation) {

        try {
            String per = "";

            for (int p : permutation) {

                per += String.valueOf(p) + "|";
            }

            ArrayList<Participant> clientes_humanos = getClientHumanActiveParticipants();

            if (!clientes_humanos.isEmpty()) {

                Collections.shuffle(clientes_humanos, Helpers.CSPRNG_GENERATOR);

                String enc_per = "";

                for (Participant p : clientes_humanos.subList(0, Math.min(PERMUTATION_ENCRYPTION_PLAYERS, clientes_humanos.size()))) {
                    enc_per += Base64.encodeBase64String(p.getNick().getBytes("UTF-8")) + "@" + p.getPermutation_key_hash() + "@" + Helpers.encryptString(per.substring(0, per.length() - 1), p.getPermutation_key(), null) + "#";
                }

                sqlUpdateGameLastDeck(enc_per.substring(0, enc_per.length() - 1));

            } else {

                sqlUpdateGameLastDeck(Base64.encodeBase64String(per.substring(0, per.length() - 1).getBytes("UTF-8")));

            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

    private Integer[] recuperarPermutacion() {

        try {

            String datos;

            String last_deck = this.sqlRecoverGameLastDeck();

            if (last_deck.contains("@")) {

                String[] perm_players = last_deck.split("#");

                String per = null;

                this.permutation_key = null;

                for (String player : perm_players) {

                    String[] perm_parts = player.split("@");

                    ArrayList<String> pendientes = new ArrayList<>();

                    pendientes.add(new String(Base64.decodeBase64(perm_parts[0]), "UTF-8"));

                    int id = Helpers.CSPRNG_GENERATOR.nextInt();

                    Participant p = GameFrame.getInstance().getParticipantes().get(new String(Base64.decodeBase64(perm_parts[0]), "UTF-8"));

                    if (p != null) {

                        String full_command = "GAME#" + String.valueOf(id) + "#PERMUTATIONKEY#" + perm_parts[1];

                        p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), p.getHmac_key()));

                        this.waitSyncConfirmations(id, pendientes);

                        while (this.permutation_key == null) {
                            synchronized (this.permutation_key_lock) {

                                try {
                                    permutation_key_lock.wait(1000);

                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }

                        if (!"*".equals(this.permutation_key)) {

                            per = perm_parts[2];

                            break;

                        } else {

                            this.permutation_key = null;
                        }

                    }
                }

                if (per != null) {
                    datos = Helpers.decryptString(per, new SecretKeySpec(Base64.decodeBase64(permutation_key), "AES"), null);
                } else {
                    return null;
                }
            } else {
                datos = new String(Base64.decodeBase64(last_deck), "UTF-8");
            }

            String[] partes = datos.split("\\|");

            Integer[] permutacion = new Integer[partes.length];

            int i = 0;

            for (String p : partes) {
                permutacion[i] = Integer.parseInt(p);
                i++;
            }

            permutation_key = null;

            return permutacion;

        } catch (IOException | KeyException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private Object[] siguienteAccionLocalRecuperada(String nick) {

        Object[] res = null;

        while (!this.acciones_locales_recuperadas.isEmpty()) {
            try {
                String accion = this.acciones_locales_recuperadas.poll();

                String[] accion_partes = accion.split("#");

                String name = new String(Base64.decodeBase64(accion_partes[0]), "UTF-8");

                if (name.equals(nick)) {

                    res = new Object[2];

                    res[0] = Integer.parseInt(accion_partes[1]);

                    if ((int) res[0] == Player.BET) {
                        res[1] = Helpers.floatClean1D(Float.parseFloat(accion_partes[2]));
                    } else {
                        res[1] = 0f;
                    }

                    break;
                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (this.acciones_locales_recuperadas.isEmpty()) {

            if (recover_dialog != null) {

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        recover_dialog.setVisible(false);
                        recover_dialog.dispose();
                        recover_dialog = null;
                        GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                    }
                });
            }

            this.setSincronizando_mano(false);

            GameFrame.getInstance().getRegistro().print("TIMBA RECUPERADA");

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

                    if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo() && !GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                        pendientes.add(jugador.getNickname());
                    }
                }

                enviarAccionesRecuperadas(pendientes, datos);

            } else {

                datos = this.recibirAccionesRecuperadas();
            }

            if (datos != null) {

                String[] rec = datos.split("@");

                this.tot_acciones_recuperadas = rec.length;

                for (String r : rec) {

                    if (!"".equals(r)) {

                        String[] parts = r.split("#");

                        String nick = new String(Base64.decodeBase64(parts[0]), "UTF-8");

                        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick) || (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().containsKey(nick) && GameFrame.getInstance().getParticipantes().get(nick).isCpu())) {
                            acciones_locales_recuperadas.add(r);
                        }
                    }
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private String[] sortearSitios() {

        ArrayList<String> nicks = null;

        String[] permutados = null;

        if (GameFrame.getInstance().isPartida_local()) {

            if (!GameFrame.isRECOVER() || (nicks = this.recuperarSorteoSitios()) == null) {

                nicks = new ArrayList<>();

                for (Map.Entry<String, Participant> entry : GameFrame.getInstance().getParticipantes().entrySet()) {

                    nicks.add(entry.getKey());
                }

                Collections.shuffle(nicks, Helpers.CSPRNG_GENERATOR);
            }

            //Comunicamos a todos los participantes el sorteo
            String command = "SEATS#" + String.valueOf(nicks.size());

            for (String nick : nicks) {

                try {
                    command += "#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            this.broadcastGAMECommandFromServer(command, null);

            permutados = nicks.toArray(new String[nicks.size()]);

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
                                    permutados[i] = new String(Base64.decodeBase64(partes[i + 4]), "UTF-8");
                                } catch (UnsupportedEncodingException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                        this.sendGAMECommandToServer("PING");

                        start_time = System.currentTimeMillis();
                    } else {
                        synchronized (this.getReceived_commands()) {

                            try {
                                this.received_commands.wait(WAIT_QUEUES);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }

            } while (!ok);

        }

        String sitios = "Sorteo de sitios:";

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

    public void destaparFlop() {

        GameFrame.getInstance().getFlop1().destapar();

        GameFrame.getInstance().getFlop1().checkSpecialCardSound();

        Helpers.pausar(1000);

        GameFrame.getInstance().checkPause();

        GameFrame.getInstance().getFlop2().destapar();

        GameFrame.getInstance().getFlop2().checkSpecialCardSound();

        Helpers.pausar(1000);

        GameFrame.getInstance().checkPause();

        GameFrame.getInstance().getFlop3().destapar();

        GameFrame.getInstance().getFlop3().checkSpecialCardSound();

        ArrayList<Card> flop = new ArrayList<>();

        flop.add(GameFrame.getInstance().getFlop1());

        flop.add(GameFrame.getInstance().getFlop2());

        flop.add(GameFrame.getInstance().getFlop3());

        GameFrame.getInstance().getRegistro().print("FLOP -> " + Card.collection2String(flop));
    }

    public void destaparTurn() {

        GameFrame.getInstance().getTurn().destapar();

        GameFrame.getInstance().getTurn().checkSpecialCardSound();

        GameFrame.getInstance().getRegistro().print("TURN -> " + GameFrame.getInstance().getTurn());
    }

    public void destaparRiver() {

        GameFrame.getInstance().getRiver().destapar();

        GameFrame.getInstance().getRiver().checkSpecialCardSound();

        GameFrame.getInstance().getRegistro().print("RIVER -> " + GameFrame.getInstance().getRiver());
    }

    private void recibirCartasResistencia(ArrayList<Player> resistencia) {

        HashMap<String, String[]> cards = new HashMap<>();

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("POTCARDS")) {

                        int total = (int) ((float) (partes.length - 3) / 3);

                        ok = true;

                        for (int i = 0; i < total; i++) {

                            try {
                                String nick = new String(Base64.decodeBase64(partes[3 + 3 * i]), "UTF-8");

                                String carta1 = partes[4 + 3 * i];

                                String carta2 = partes[5 + 3 * i];

                                cards.put(nick, new String[]{carta1, carta2});
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                        for (Player jugador : resistencia) {

                            if (!jugador.getNickname().equals(GameFrame.getInstance().getNick_local()) && !jugador.isExit()) {

                                String[] suscartas = cards.get(jugador.getNickname());

                                String[] carta1 = suscartas[0].split("_");

                                String[] carta2 = suscartas[1].split("_");

                                jugador.getPlayingCard1().actualizarValorPalo(carta1[0], carta1[1]);

                                jugador.getPlayingCard2().actualizarValorPalo(carta2[0], carta2[1]);
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

                    this.sendGAMECommandToServer("PING");

                    start_time = System.currentTimeMillis();
                } else {

                    synchronized (this.getReceived_commands()) {

                        try {
                            this.received_commands.wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

        } while (!ok);

    }

    public void procesarCartasResistencia(ArrayList<Player> resisten, boolean destapar) {

        if (!this.cartas_resistencia) {

            if (GameFrame.getInstance().isPartida_local()) {

                //Enviamos a cada jugador las cartas de los jugadores que han llegado al final de todas las rondas de apuestas
                String comando = "POTCARDS";

                for (Player jugador : resisten) {

                    if (!jugador.isExit()) {
                        try {
                            comando += "#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")) + "#" + jugador.getPlayingCard1().toShortString() + "#" + jugador.getPlayingCard2().toShortString();
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                broadcastGAMECommandFromServer(comando, null);

                if (destapar) {
                    Audio.playWavResource("misc/uncover.wav", false);

                    //Destapamos las cartas de los jugadores involucrados
                    for (Player jugador : resisten) {

                        if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit()) {

                            jugador.destaparCartas(false);
                        }
                    }
                }

                this.cartas_resistencia = true;

            } else {

                //Recibimos las cartas de los jugadores involucrados en el bote_total (ignoramos las nuestras que ya las sabemos)
                recibirCartasResistencia(resisten);

                if (destapar) {
                    Audio.playWavResource("misc/uncover.wav", false);

                    //Destapamos las cartas de los jugadores involucrados
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

                if (jugador != GameFrame.getInstance().getLocalPlayer() && !jugador.isExit() && jugador.isSpectator() && Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0 && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

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

                float ganancia = Helpers.floatClean1D(Helpers.floatClean1D(jugador.getStack()) + Helpers.floatClean1D(jugador.getPagar())) - Helpers.floatClean1D(jugador.getBuyin());

                String ganancia_msg = "";

                if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                    ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                    ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                } else {
                    ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                }

                GameFrame.getInstance().getRegistro().print(jugador.getNickname() + " " + Translator.translate("ABANDONA LA TIMBA") + " -> " + ganancia_msg);

                exit++;
            }
        }

        if (exit > 0) {
            GameFrame.getInstance().refreshTapete();

            nick2player.clear();

            for (Player jugador : GameFrame.getInstance().getJugadores()) {
                nick2player.put(jugador.getNickname(), jugador);
            }

        }

    }

    public boolean ganaPorUltimaCarta(Player jugador, Hand jugada, int MIN) {

        if (!GameFrame.getInstance().getRiver().isTapada() && jugada.getWinners().contains(GameFrame.getInstance().getRiver()) && jugada.getVal() >= MIN && (jugada.getWinners().contains(jugador.getPlayingCard1()) || jugada.getWinners().contains(jugador.getPlayingCard2()))) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));
            cartas.add(jugador.getPlayingCard1());
            cartas.add(jugador.getPlayingCard2());
            cartas.remove(GameFrame.getInstance().getRiver());

            Hand nueva_jugada = new Hand(cartas);

            return (nueva_jugada.getVal() != jugada.getVal());
        }

        return false;
    }

    public boolean badbeat(Player perdedor, Player ganador) {

        if (ganador != null) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));

            cartas.add(perdedor.getPlayingCard1());

            cartas.add(perdedor.getPlayingCard2());

            cartas.remove(GameFrame.getInstance().getRiver());

            Hand jugada_perdedor_turn = new Hand(cartas);

            cartas.remove(perdedor.getPlayingCard1());

            cartas.remove(perdedor.getPlayingCard2());

            cartas.add(ganador.getPlayingCard1());

            cartas.add(ganador.getPlayingCard2());

            Hand jugada_ganador_turn = new Hand(cartas);

            return (jugada_perdedor_turn.getVal() >= Hand.TRIO && (jugada_perdedor_turn.getVal() > jugada_ganador_turn.getVal()));
        } else {
            return false;
        }

    }

    public void pausaConBarra(int tiempo) {

        this.setTiempo_pausa(tiempo);

        while (getTiempoPausa() > 0) {

            synchronized (lock_tiempo_pausa_barra) {
                try {
                    lock_tiempo_pausa_barra.wait(1000);

                    if (!GameFrame.getInstance().isTimba_pausada() && !isFin_de_la_transmision() && !isIwtsthing()) {

                        this.tiempo_pausa--;

                        int val = this.tiempo_pausa;

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                GameFrame.getInstance().getBarra_tiempo().setValue(val);
                            }
                        });
                    }

                } catch (InterruptedException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        Helpers.GUIRun(new Runnable() {
            public void run() {
                GameFrame.getInstance().getBarra_tiempo().setValue(tiempo);
            }
        });
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

        boolean hay_ganador = false;

        int pos = pivote;

        do {

            Player jugador_actual = GameFrame.getInstance().getJugadores().get(pos);

            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                if (hay_ganador) {

                    if (ganadores.containsKey(jugador_actual)) {

                        jugador_actual.destaparCartas(false);

                        Hand jugada = ganadores.get(jugador_actual);

                        jugador_actual.setWinner(jugada.getName());

                        this.sqlNewShowdown(jugador_actual, jugada, true);

                        if (GameFrame.SONIDOS_CHORRA && jugador_actual == GameFrame.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Audio.playWavResource("joke/" + GameFrame.LANGUAGE + "/winner/applause.wav");
                            } else {
                                this.soundWinner(jugada.getVal(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                            }
                        }

                    } else {

                        Hand jugada = perdedores.get(jugador_actual);

                        if (jugador_actual == GameFrame.getInstance().getLocalPlayer()) {

                            jugador_actual.setLoser(jugada.getName());

                            if (!this.destapar_resistencia) {
                                GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                            } else {
                                GameFrame.getInstance().getLocalPlayer().setMuestra(true);
                            }

                        } else {

                            if (jugador_actual.getPlayingCard1().isTapada()) {

                                jugador_actual.setLoser(Translator.translate("PIERDE"));

                            } else {

                                jugador_actual.setLoser(jugada.getName());
                            }
                        }

                        this.sqlNewShowdown(jugador_actual, jugada, false);

                        if (GameFrame.SONIDOS_CHORRA && jugador_actual == GameFrame.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("joke/es/loser/", new String[]{
                                    "encargado.wav",
                                    "matias.wav"});

                                Audio.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));
                            } else {

                                this.soundLoser(jugada.getVal());
                            }
                        }
                    }

                } else {

                    if (ganadores.containsKey(jugador_actual)) {

                        jugador_actual.destaparCartas(false);

                        Hand jugada = ganadores.get(jugador_actual);

                        jugador_actual.setWinner(jugada.getName());

                        this.sqlNewShowdown(jugador_actual, jugada, true);

                        if (GameFrame.SONIDOS_CHORRA && jugador_actual == GameFrame.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Audio.playWavResource("joke/" + GameFrame.LANGUAGE + "/winner/applause.wav");
                            } else {
                                this.soundWinner(jugada.getVal(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                            }
                        }

                        hay_ganador = true;

                    } else {

                        ArrayList<Card> cartas = new ArrayList<>();

                        cartas.add(jugador_actual.getPlayingCard1());

                        cartas.add(jugador_actual.getPlayingCard2());

                        jugador_actual.destaparCartas(false);

                        Hand jugada = perdedores.get(jugador_actual);

                        jugador_actual.setLoser(jugada.getName());

                        if (jugador_actual == GameFrame.getInstance().getLocalPlayer()) {
                            GameFrame.getInstance().getLocalPlayer().setMuestra(true);
                        }

                        this.sqlNewShowdown(jugador_actual, jugada, false);

                        if (GameFrame.SONIDOS_CHORRA && jugador_actual == GameFrame.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
                                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("joke/es/loser/", new String[]{
                                    "encargado.wav",
                                    "matias.wav"});

                                Audio.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));
                            } else {

                                this.soundLoser(jugada.getVal());
                            }
                        }

                    }
                }

            }

            pos = (pos + 1) % GameFrame.getInstance().getJugadores().size();

        } while (pos != pivote);

    }

    @Override
    public void run() {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);
                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
            }
        });

        if (GameFrame.getInstance().isPartida_local()) {

            broadcastGAMECommandFromServer("INIT#" + String.valueOf(GameFrame.BUYIN) + "#" + String.valueOf(GameFrame.CIEGA_PEQUEÑA) + "#" + String.valueOf(GameFrame.CIEGA_GRANDE) + "#" + String.valueOf(GameFrame.CIEGAS_DOUBLE) + "@" + String.valueOf(GameFrame.CIEGAS_DOUBLE_TYPE) + "#" + String.valueOf(GameFrame.isRECOVER()) + "#" + String.valueOf(GameFrame.REBUY) + "#" + String.valueOf(GameFrame.MANOS), null);

        }

        if (GameFrame.RECOVER && GameFrame.getInstance().isPartida_local()) {

            this.sqlite_id_game = GameFrame.RECOVER_ID;

            if (this.sqlite_id_game == -1) {
                Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), "ERROR FATAL: NO SE HA PODIDO RECUPERAR LA TIMBA");

                if (GameFrame.getInstance().getJugadores().size() > 1) {

                    //Hay que avisar a los clientes de que la timba ha terminado
                    broadcastGAMECommandFromServer("SERVEREXIT", null, false);

                    GameFrame.getInstance().getLocalPlayer().setExit();

                    GameFrame.getInstance().finTransmision(true);

                } else {

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            GameFrame.getInstance().getLocalPlayer().setExit();

                            GameFrame.getInstance().finTransmision(true);
                        }
                    });
                }
            }
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("Sorteando sitios..."));
            }
        });

        this.nicks_permutados = sortearSitios();

        sentarParticipantes();

        //ESTE MAPA HAY QUE CARGARLO UNA VEZ TENEMOS A LOS JUGADORES EN SUS SITIOS
        for (Player jugador : GameFrame.getInstance().getJugadores()) {
            nick2player.put(jugador.getNickname(), jugador);
        }

        if (!GameFrame.RECOVER) {
            sqlNewGame();
        }

        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().getSala_espera().setVisible(false);
            }
        });

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                GameFrame.getInstance().getSala_espera().getStatus().setText(Translator.translate("Timba en curso"));
                GameFrame.getInstance().getSala_espera().getTts_warning().setVisible(true);
                GameFrame.getInstance().getSala_espera().getChat_notifications().setVisible(true);
                GameFrame.getInstance().getSala_espera().getBarra().setVisible(false);
                GameFrame.getInstance().getSala_espera().pack();
            }
        });

        Audio.stopLoopMp3("misc/waiting_room.mp3");

        if (!GameFrame.RECOVER && GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

            Audio.playWavResource("misc/startplay.wav");
        }

        if (GameFrame.MUSICA_AMBIENTAL) {

            if (!GameFrame.RECOVER) {
                Audio.unmuteLoopMp3("misc/background_music.mp3");
            } else {
                Audio.stopLoopMp3("misc/background_music.mp3");
                Audio.playLoopMp3Resource("misc/recovering.mp3");
            }
        }

        GameFrame.getInstance().autoZoomFullScreen();

        while (!fin_de_la_transmision) {

            if ((getJugadoresActivos() + getJugadoresCalentando()) > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {

                if (this.NUEVA_MANO()) {

                    auditorCuentas();

                    GameFrame.getInstance().getRegistro().print(this.bb_nick + Translator.translate(" es la CIEGA GRANDE (") + Helpers.float2String(this.ciega_grande) + ") / " + this.sb_nick + Translator.translate(" es la CIEGA PEQUEÑA (") + Helpers.float2String(this.ciega_pequeña) + ") / " + this.dealer_nick + Translator.translate(" es el DEALER"));

                    ArrayList<Player> resisten = this.rondaApuestas(PREFLOP, new ArrayList<>(GameFrame.getInstance().getJugadores()));

                    GameFrame.getInstance().hideTapeteApuestas();

                    GameFrame.getInstance().getLocalPlayer().desactivarControles();

                    if (GameFrame.AUTO_ACTION_BUTTONS) {
                        GameFrame.getInstance().getLocalPlayer().desActivarPreBotones();
                    }

                    if (!this.acciones_locales_recuperadas.isEmpty()) {

                        this.acciones_locales_recuperadas.clear();

                        if (recover_dialog != null) {

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    recover_dialog.setVisible(false);
                                    recover_dialog.dispose();
                                    recover_dialog = null;
                                    GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                }
                            });
                        }

                        this.setSincronizando_mano(false);

                        GameFrame.getInstance().getRegistro().print("TIMBA RECUPERADA");

                        if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {

                            Audio.playWavResource("misc/startplay.wav");
                        }

                        if (GameFrame.MUSICA_AMBIENTAL) {
                            Audio.stopLoopMp3("misc/recovering.mp3");
                            Audio.playLoopMp3Resource("misc/background_music.mp3");
                        }
                    }

                    this.show_time = true;

                    HashMap<Player, Hand> jugadas;

                    HashMap<Player, Hand> ganadores;

                    synchronized (this.getLock_contabilidad()) {

                        Iterator<Player> iterator = resisten.iterator();

                        while (iterator.hasNext()) {
                            Player jugador = iterator.next();

                            if (jugador.isExit()) {
                                iterator.remove();
                            }

                        }

                        this.bote.genSidePots();

                        badbeat = false;

                        float sql_bote_total = this.bote_total;

                        if (resisten.size() == 0) {

                            GameFrame.getInstance().getRegistro().print("-----" + Translator.translate(" GANA BOTE (") + Helpers.float2String(this.bote.getTotal() + this.bote_sobrante) + Translator.translate(") SIN TENER QUE MOSTRAR"));

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);

                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.RED);

                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                }
                            });

                            GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, 0f);

                            if (Helpers.float1DSecureCompare(0f, this.bote_total) < 0) {
                                this.bote_sobrante += this.bote_total;
                            }

                            ganadores = new HashMap<>();

                        } else if (resisten.size() == 1) {

                            //Todos se han tirado menos uno GANA SIN MOSTRAR
                            resisten.get(0).setWinner(resisten.contains(GameFrame.getInstance().getLocalPlayer()) ? Translator.translate("GANAS") : Translator.translate("GANA"));

                            resisten.get(0).pagar(this.bote.getTotal() + this.bote_sobrante);

                            this.beneficio_bote_principal = this.bote.getTotal() + this.bote_sobrante - this.bote.getBet();

                            GameFrame.getInstance().getRegistro().print(resisten.get(0).getNickname() + Translator.translate(" GANA BOTE (") + Helpers.float2String(this.bote.getTotal() + this.bote_sobrante) + Translator.translate(") SIN TENER QUE MOSTRAR"));

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);

                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.GREEN);

                                    GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                }
                            });

                            GameFrame.getInstance().setTapeteBote(this.bote.getTotal() + this.bote_sobrante, this.beneficio_bote_principal);

                            this.bote_total = 0f;

                            this.bote_sobrante = 0f;

                            if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {
                                GameFrame.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                            }

                            if (resisten.get(0) == GameFrame.getInstance().getLocalPlayer()) {

                                this.soundWinner(0, false);
                            }

                            ganadores = new HashMap<>();

                            ganadores.put(resisten.get(0), null);

                            this.sqlNewShowdown(resisten.get(0), null, true);

                        } else {

                            procesarCartasResistencia(resisten, false);

                            if (!this.destapar_resistencia) {
                                Helpers.pausar(GameFrame.PAUSA_ANTES_DE_SHOWDOWN * 1000);
                            }

                            if (this.bote.getSidePot() == null) {

                                //NO HAY BOTES DERIVADOS
                                jugadas = this.calcularJugadas(resisten);

                                ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

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

                                    if (!cartas.contains(ganador.getPlayingCard1())) {
                                        ganador.getPlayingCard1().desenfocar();
                                    }

                                    if (!cartas.contains(ganador.getPlayingCard2())) {
                                        ganador.getPlayingCard2().desenfocar();
                                    }

                                    jugadas.remove(ganador);

                                    ganador.pagar(cantidad_pagar_ganador[0]);

                                    this.bote_total -= cantidad_pagar_ganador[0];

                                    ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                    GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                    unganador = ganador;

                                    jugada_ganadora = jugada.getVal();
                                }

                                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                    if (!cartas_usadas_jugadas.contains(carta)) {
                                        carta.desenfocar();
                                    }
                                }

                                for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {

                                    Player perdedor = entry.getKey();

                                    perdedor.getPlayingCard1().desenfocar();

                                    perdedor.getPlayingCard2().desenfocar();

                                    badbeat = badbeat(perdedor, unganador);

                                    perdedores.put(perdedor, entry.getValue());

                                    GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");

                                }

                                this.showdown(jugadas, ganadores);
                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.GREEN);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);
                                    }
                                });

                                GameFrame.getInstance().setTapeteBote(cantidad_pagar_ganador[0], this.beneficio_bote_principal);

                            } else {

                                //Vamos a ver los ganadores de cada bote_total
                                jugadas = this.calcularJugadas(resisten);

                                ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

                                float[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal() + this.bote_sobrante, ganadores.size());

                                this.beneficio_bote_principal = cantidad_pagar_ganador[0] - this.bote.getBet();

                                String bote_tapete = Helpers.float2String(this.bote.getTotal()) + " (" + Helpers.float2String(this.beneficio_bote_principal) + ")";

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

                                    if (!cartas.contains(ganador.getPlayingCard1())) {
                                        ganador.getPlayingCard1().desenfocar();
                                    }

                                    if (!cartas.contains(ganador.getPlayingCard2())) {
                                        ganador.getPlayingCard2().desenfocar();
                                    }

                                    jugadas.remove(ganador);

                                    ganador.pagar(cantidad_pagar_ganador[0]);

                                    this.bote_total -= cantidad_pagar_ganador[0];

                                    ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                    GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE PRINCIPAL (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                    unganador = ganador;

                                    jugada_ganadora = jugada.getVal();
                                }

                                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                                    if (!cartas_usadas_jugadas.contains(carta)) {
                                        carta.desenfocar();
                                    }
                                }

                                for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {

                                    Player perdedor = entry.getKey();

                                    perdedor.getPlayingCard1().desenfocar();

                                    perdedor.getPlayingCard2().desenfocar();

                                    badbeat = badbeat(perdedor, unganador);

                                    perdedores.put(perdedor, entry.getValue());

                                    GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE PRINCIPAL (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                }

                                this.showdown(jugadas, ganadores);

                                Pot current_pot = this.bote.getSidePot();

                                int conta_bote_secundario = 2;

                                while (current_pot != null) {

                                    if (current_pot.getPlayers().size() == 1) {

                                        bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.float2String(current_pot.getTotal()) + "}";

                                        current_pot.getPlayers().get(0).pagar(current_pot.getTotal());

                                        this.bote_total -= current_pot.getTotal();

                                        current_pot.getPlayers().get(0).setBoteSecundario("#" + String.valueOf(conta_bote_secundario));

                                        GameFrame.getInstance().getRegistro().print(current_pot.getPlayers().get(0).getNickname() + Translator.translate(" RECUPERA BOTE (SOBRANTE) SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(current_pot.getTotal()) + ")");

                                        this.sqlUpdateShowdownPay(current_pot.getPlayers().get(0));

                                    } else {

                                        jugadas = this.calcularJugadas(current_pot.getPlayers());

                                        ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

                                        cantidad_pagar_ganador = this.calcularBoteParaGanador(current_pot.getTotal(), ganadores.size());

                                        float beneficio_bote = cantidad_pagar_ganador[0] - current_pot.getBet();

                                        bote_tapete = bote_tapete + " + #" + String.valueOf(conta_bote_secundario) + "{" + Helpers.float2String(current_pot.getTotal()) + " (" + Helpers.float2String(beneficio_bote) + ")}";

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {

                                            Player ganador = entry.getKey();

                                            jugadas.remove(entry.getKey());

                                            ganador.pagar(cantidad_pagar_ganador[0]);

                                            this.bote_total -= cantidad_pagar_ganador[0];

                                            Hand jugada = entry.getValue();

                                            ganador.setBoteSecundario("#" + String.valueOf(conta_bote_secundario));

                                            ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                            cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                            cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                            GameFrame.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                            this.sqlUpdateShowdownPay(ganador);
                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {

                                            Player perdedor = entry.getKey();

                                            perdedor.getPlayingCard1().desenfocar();

                                            perdedor.getPlayingCard2().desenfocar();

                                            perdedores.put(perdedor, entry.getValue());

                                            GameFrame.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                    }

                                    current_pot = current_pot.getSidePot();

                                    conta_bote_secundario++;

                                }
                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        GameFrame.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(false);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.BLACK);
                                        GameFrame.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                    }
                                });

                                GameFrame.getInstance().setTapeteBote(bote_tapete);
                            }

                            this.bote_sobrante = this.bote_total;
                        }

                        for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                            if (carta.isTapada()) {
                                carta.desenfocar();
                            }
                        }

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
                                    players += "#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                                } catch (UnsupportedEncodingException ex) {
                                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

                    if (!GameFrame.TEST_MODE) {

                        if (getJugadoresActivos() > 1 && !GameFrame.getInstance().getLocalPlayer().isExit()) {

                            this.pausaConBarra(GameFrame.PAUSA_ENTRE_MANOS);

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

                        } else {

                            if (!GameFrame.getInstance().isPartida_local()) {
                                GameFrame.getInstance().getSala_espera().sqlRemovePermutationkey();
                            }

                            fin_de_la_transmision = true;
                        }

                    } else {

                        this.pausaConBarra(GameFrame.PAUSA_ENTRE_MANOS_TEST);

                        synchronized (lock_mostrar) {
                            this.show_time = false;
                        }

                        GameFrame.getInstance().getLocalPlayer().desactivar_boton_mostrar();

                        GameFrame.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);

                        fin_de_la_transmision = this.isLast_hand();

                    }

                }

            } else {

                if (!GameFrame.getInstance().getLocalPlayer().isSpectator() && Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {

                    GameFrame.getInstance().getLocalPlayer().pagar(this.bote_sobrante);

                    this.bote_sobrante = 0f;
                }

                for (Card carta : GameFrame.getInstance().getCartas_comunes()) {
                    carta.iniciarCarta();
                }

                GameFrame.getInstance().getTiempo_juego().stop();

                GameFrame.getInstance().getRegistro().print("LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)");

                Helpers.mostrarMensajeInformativo(GameFrame.getInstance(), "LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)");

                fin_de_la_transmision = true;
            }

        }

        if (!GameFrame.getInstance().isPartida_local() && !fin_de_la_transmision) {
            sendGAMECommandToServer("EXIT", false);
        }

        GameFrame.getInstance().finTransmision(fin_de_la_transmision);

    }

    public void checkRebuyTime() {

        ArrayList<String> rebuy_players = new ArrayList<>();

        for (Player jugador : GameFrame.getInstance().getJugadores()) {

            if (jugador != GameFrame.getInstance().getLocalPlayer() && jugador.isActivo() && Helpers.float1DSecureCompare(0f, Helpers.floatClean1D(jugador.getStack()) + Helpers.floatClean1D(jugador.getPagar())) == 0) {

                if (GameFrame.REBUY) {
                    rebuy_players.add(jugador.getNickname());
                } else {
                    jugador.setSpectator(null);
                }

            }
        }

        this.rebuy_time = !rebuy_players.isEmpty();

        if (GameFrame.getInstance().getLocalPlayer().isActivo() && Helpers.float1DSecureCompare(Helpers.floatClean1D(GameFrame.getInstance().getLocalPlayer().getStack()) + Helpers.floatClean1D(GameFrame.getInstance().getLocalPlayer().getPagar()), 0f) == 0) {

            this.rebuy_time = true;

            if (GameFrame.REBUY) {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        gameover_dialog = new GameOverDialog(GameFrame.getInstance().getFrame(), true);

                        GameFrame.getInstance().setGame_over_dialog(true);

                        gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());

                        gameover_dialog.setVisible(true);
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                if (gameover_dialog.isContinua()) {

                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        rebuy_now.put(GameFrame.getInstance().getLocalPlayer().getNickname(), (int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        String comando = "REBUY#" + Base64.encodeBase64String(GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8")) + "#" + String.valueOf((int) gameover_dialog.getBuyin_dialog().getRebuy_spinner().getValue());

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try {

                        rebuy_players.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                        String comando = "REBUY#" + Base64.encodeBase64String(GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8")) + "#0";

                        if (GameFrame.getInstance().isPartida_local()) {
                            this.broadcastGAMECommandFromServer(comando, null);
                        } else {
                            this.sendGAMECommandToServer(comando);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    if (rebuy_now.containsKey(GameFrame.getInstance().getLocalPlayer().getNickname())) {

                        rebuy_now.remove(GameFrame.getInstance().getLocalPlayer().getNickname());

                    }

                    GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                    GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + Translator.translate(" -> TE QUEDAS DE ESPECTADOR"));
                }

            } else {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        gameover_dialog = new GameOverDialog(GameFrame.getInstance().getFrame(), true, true);

                        GameFrame.getInstance().setGame_over_dialog(true);
                        gameover_dialog.setLocationRelativeTo(gameover_dialog.getParent());
                        gameover_dialog.setVisible(true);
                    }
                });

                GameFrame.getInstance().setGame_over_dialog(false);

                GameFrame.getInstance().getLocalPlayer().setSpectator(null);

                GameFrame.getInstance().getRegistro().print(GameFrame.getInstance().getLocalPlayer().getNickname() + Translator.translate(" -> TE QUEDAS DE ESPECTADOR"));
            }

        }

        if (!rebuy_players.isEmpty()) {

            //Enviamos los REBUYS de los bots
            if (GameFrame.getInstance().isPartida_local()) {

                for (Player jugador : GameFrame.getInstance().getJugadores()) {

                    if (rebuy_players.contains(jugador.getNickname()) && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()) != null && GameFrame.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                        int res = Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), Translator.translate("¿RECOMPRA? -> ") + jugador.getNickname());

                        rebuy_players.remove(jugador.getNickname());

                        rebuy_now.put(jugador.getNickname(), GameFrame.BUYIN);

                        try {
                            String comando = "REBUY#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")) + ((!GameFrame.REBUY || res != 0) ? "#0" : "#" + String.valueOf(GameFrame.BUYIN));

                            this.broadcastGAMECommandFromServer(comando, null);

                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
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

            ArrayList<Card> cartas_utilizables = new ArrayList<>(Arrays.asList(GameFrame.getInstance().getCartas_comunes()));

            cartas_utilizables.add(jugador.getPlayingCard1());

            cartas_utilizables.add(jugador.getPlayingCard2());

            jugadas.put(jugador, new Hand(cartas_utilizables));
        }

        return jugadas;
    }

    public HashMap<Player, Hand> calcularGanadores(HashMap<Player, Hand> candidatos) {

        int jugada_max = Hand.CARTA_ALTA;

        //Averiguamos la jugada máxima entre todos los jugadores
        for (Map.Entry<Player, Hand> entry : candidatos.entrySet()) {

            if (entry.getValue().getVal() > jugada_max) {
                jugada_max = entry.getValue().getVal();
            }
        }

        //Eliminamos a los jugadores con jugadas por debajo de la jugada máxima
        for (Iterator<Map.Entry<Player, Hand>> it = candidatos.entrySet().iterator(); it.hasNext();) {

            Map.Entry<Player, Hand> entry = it.next();

            if (entry.getValue().getVal() < jugada_max) {
                it.remove();
            }
        }

        if (candidatos.size() == 1) {

            return candidatos;

        } else {

            //Si hay varios con la jugada máxima intentamos desempatar
            switch (jugada_max) {
                case Hand.ESCALERA_COLOR:
                case Hand.ESCALERA:
                    return desempatarEscalera(candidatos);
                case Hand.POKER:
                    return desempatarRepetidas(candidatos, CARTAS_POKER);
                case Hand.FULL:
                    return desempatarFull(candidatos);
                case Hand.COLOR:
                    return desempatarCartaAlta(candidatos, 0);
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

        //Averiguamos la carta más alta de la primera pareja (la pareja grande)
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        //Nos cargamos todos los que tengan una pareja grande menor
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

            //Averiguamos la carta más alta de la segunda pareja
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(2).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(2).getValorNumerico();
                }
            }

            //Nos cargamos todos los que tengan una pareja secundaria menor
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

        //Averiguamos la carta más alta del trío del FULL
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        //Nos cargamos todos los que tengan un trío con carta más pequeña
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

            //Averiguamos la carta más alta de la pareja del FULL
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(3).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(3).getValorNumerico();
                }
            }

            //Nos cargamos todos los que tengan una pareja con carta más pequeña
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

        //Averiguamos la carta más alta del POKER/TRIO/PAREJA
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico() > carta_alta) {
                carta_alta = jugada.getMano().get(0).getValorNumerico();
            }
        }

        //Nos cargamos todos los que tengan un POKER/TRIO/PAREJA con carta más pequeña
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
        int carta_alta = -1;
        boolean escalera_as = false;

        //Miramos si hay alguna escalera al AS
        for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
            Hand jugada = entry.getValue();
            if (Hand.isEscaleraAs(jugada)) {
                escalera_as = true;
                carta_alta = jugada.getMano().get(0).getValorNumerico();
                break;
            }
        }

        //Si es una escalera "normal" averiguamos la carta más alta
        if (!escalera_as) {

            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(0).getValorNumerico(!escalera_as) > carta_alta) {
                    carta_alta = jugada.getMano().get(0).getValorNumerico(!escalera_as);
                }
            }
        }

        //Nos cargamos todos los que tengan una escalera con carta alta menor que la máxima
        for (Iterator<Map.Entry<Player, Hand>> it = jugadores.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Player, Hand> entry = it.next();
            Hand jugada = entry.getValue();
            if (jugada.getMano().get(0).getValorNumerico(!escalera_as) < carta_alta) {
                it.remove();
            }
        }

        return jugadores;
    }

    private HashMap<Player, Hand> desempatarCartaAlta(HashMap<Player, Hand> jugadores, int start_card) {

        for (int i = start_card; i < CARTAS_MAX; i++) {

            int carta_alta = 1;

            //Averiguamos la carta más alta
            for (Map.Entry<Player, Hand> entry : jugadores.entrySet()) {
                Hand jugada = entry.getValue();
                if (jugada.getMano().get(i).getValorNumerico() > carta_alta) {
                    carta_alta = jugada.getMano().get(i).getValorNumerico();
                }
            }

            //Nos cargamos todos los que tengan una carta menor
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
}
