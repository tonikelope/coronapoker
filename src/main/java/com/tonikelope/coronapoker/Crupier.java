/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.Game.WAIT_QUEUES;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                {"training_day.gif", 2000L},
                {"wallstreet.gif", 1500L},
                {"casinoroyale.gif", 4500L}
            });

    public static volatile Map.Entry<String, Object[][]> ALLIN_CINEMATICS_MOD = null;

    public static final Map.Entry<String, String[]> ALLIN_SOUNDS = new HashMap.SimpleEntry<String, String[]>("allin/", new String[]{
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

    public static volatile Map.Entry<String, String[]> ALLIN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> FOLD_SOUNDS = new HashMap.SimpleEntry<String, String[]>("fold/", new String[]{
        "fary.wav",
        "mamar_pollas.wav",
        "maricon.wav",
        "marines.wav",
        "mcfly.wav",
        "mierda_alta.wav",
        "percibo_miedo.wav"});

    public static volatile Map.Entry<String, String[]> FOLD_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> SHOWDOWN_SOUNDS = new HashMap.SimpleEntry<String, String[]>("showdown/", new String[]{
        "berto.wav",
        "bond.wav",
        "kbill_show.wav"});

    public static volatile Map.Entry<String, String[]> SHOWDOWN_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> WINNER_SOUNDS = new HashMap.SimpleEntry<String, String[]>("winner/", new String[]{
        "ateam.wav",
        "divertido.wav",
        "dura.wav",
        "fisuras.wav",
        "lacasitos.wav",
        "nadie_te_aguanta.wav",
        "planesbien.wav",
        "reymundo.wav",
        "vivarey.wav"});

    public static volatile Map.Entry<String, String[]> WINNER_SOUNDS_MOD = null;

    public static final Map.Entry<String, String[]> LOSER_SOUNDS = new HashMap.SimpleEntry<String, String[]>("loser/", new String[]{
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

    public static volatile Map.Entry<String, String[]> LOSER_SOUNDS_MOD = null;

    public static final int CARTA_ALTA = 1;
    public static final int PAREJA = 2;
    public static final int DOBLE_PAREJA = 3;
    public static final int TRIO = 4;
    public static final int ESCALERA = 5;
    public static final int ESCALERA_REAL = 6;
    public static final int COLOR = 7;
    public static final int FULL = 8;
    public static final int POKER = 9;
    public static final int ESCALERA_COLOR = 10;
    public static final int ESCALERA_COLOR_REAL = 11;
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
    public static final String RECOVER_DECK_FILE = Init.REC_DIR + "/coronapoker_deck";
    public static final String RECOVER_SEATS_FILE = Init.REC_DIR + "/coronapoker_seats";
    public static final String RECOVER_BALANCE_FILE = Init.REC_DIR + "/coronapoker_balance";
    public static final String RECOVER_ACTIONS_FILE = Init.REC_DIR + "/coronapoker_actions";
    public static final float[][] CIEGAS = new float[][]{new float[]{0.1f, 0.2f}, new float[]{0.2f, 0.4f}, new float[]{0.5f, 1.0f}};
    public static volatile boolean FUSION_MOD_SOUNDS = true;
    public static volatile boolean FUSION_MOD_CINEMATICS = true;

    private final ConcurrentLinkedQueue<String> received_commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acciones_recuperadas = new ConcurrentLinkedQueue<>();
    private final HashMap<String, Float[]> auditor = new HashMap<>();
    private final Object lock_apuestas = new Object();
    private final Object lock_contabilidad = new Object();
    private final Object lock_cinematics = new Object();
    private final Object lock_mostrar = new Object();
    private final Object lock_nueva_mano = new Object();
    private final ConcurrentHashMap<String, Player> nick2player = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Player, Hand> perdedores = new ConcurrentHashMap<>();

    private volatile int mano = 0;
    private volatile float bote_total = 0f;
    private volatile float apuestas = 0f;
    private volatile float ciega_grande = Game.CIEGA_GRANDE;
    private volatile float ciega_pequeña = Game.CIEGA_PEQUEÑA;
    private volatile Integer[] permutacion_baraja;
    private volatile float apuesta_actual = 0f;
    private volatile float ultimo_raise = 0f;
    private volatile int conta_raise = 0;
    private volatile int conta_bet = 0;
    private volatile float bote_sobrante = 0f;
    private volatile String[] nicks_permutados;
    private volatile int dealer_pos = -1;
    private volatile int small_pos = -1;
    private volatile int big_pos = -1;
    private volatile int utg_pos = -1;
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

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/sounds/allin/"))) {
                File[] archivos = new File(Helpers.getCurrentJarPath() + "/mod/sounds/allin/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("allin/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.ALLIN_SOUNDS.getValue()));

                    sounds.addAll(filenames);

                    Crupier.ALLIN_SOUNDS_MOD = new HashMap.SimpleEntry<>("allin/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.ALLIN_SOUNDS_MOD = Crupier.ALLIN_SOUNDS;
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/sounds/fold/"))) {
                File[] archivos = new File(Helpers.getCurrentJarPath() + "/mod/sounds/fold/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("fold/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.FOLD_SOUNDS.getValue()));

                    sounds.addAll(filenames);

                    Crupier.FOLD_SOUNDS_MOD = new HashMap.SimpleEntry<>("fold/", sounds.toArray(new String[sounds.size()]));
                }

            } else {
                Crupier.FOLD_SOUNDS_MOD = Crupier.FOLD_SOUNDS;
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/sounds/showdown/"))) {
                File[] archivos = new File(Helpers.getCurrentJarPath() + "/mod/sounds/showdown/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("showdown/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.SHOWDOWN_SOUNDS.getValue()));

                    sounds.addAll(filenames);

                    Crupier.SHOWDOWN_SOUNDS_MOD = new HashMap.SimpleEntry<>("showdown/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.SHOWDOWN_SOUNDS_MOD = Crupier.SHOWDOWN_SOUNDS;
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/sounds/loser/"))) {
                File[] archivos = new File(Helpers.getCurrentJarPath() + "/mod/sounds/loser/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("loser/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.LOSER_SOUNDS.getValue()));

                    sounds.addAll(filenames);

                    Crupier.LOSER_SOUNDS_MOD = new HashMap.SimpleEntry<>("loser/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.LOSER_SOUNDS_MOD = Crupier.LOSER_SOUNDS;
            }

            if (Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/sounds/winner/"))) {
                File[] archivos = new File(Helpers.getCurrentJarPath() + "/mod/sounds/winner/").listFiles(File::isFile);

                ArrayList<String> filenames = new ArrayList<>();

                for (var f : archivos) {

                    filenames.add(f.getName());
                }

                if (!FUSION_MOD_SOUNDS) {
                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("winner/", filenames.toArray(new String[filenames.size()]));
                } else {

                    ArrayList<String> sounds = new ArrayList<>();

                    sounds.addAll(Arrays.asList(Crupier.WINNER_SOUNDS.getValue()));

                    sounds.addAll(filenames);

                    Crupier.WINNER_SOUNDS_MOD = new HashMap.SimpleEntry<>("winner/", sounds.toArray(new String[sounds.size()]));
                }

            } else {

                Crupier.WINNER_SOUNDS_MOD = Crupier.WINNER_SOUNDS;
            }

        }

    }

    public void remoteCinematicEnd(String nick) {

        if (Game.getInstance().isPartida_local()) {

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

    public void setFin_de_la_transmision(boolean fin_de_la_transmision) {
        this.fin_de_la_transmision = fin_de_la_transmision;
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

    public synchronized void decrementPausaBarra() {

        tiempo_pausa--;

    }

    public synchronized int getTiempo_pausa() {

        return tiempo_pausa;

    }

    public synchronized void setTiempo_pausa(int tiempo) {
        this.tiempo_pausa = tiempo;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setMaximum(tiempo);
                Game.getInstance().getBarra_tiempo().setValue(tiempo);
            }
        });
    }

    public long getTurno() {
        return turno;
    }

    public boolean localCinematicAllin() {

        Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD) : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

        if (!this.sincronizando_mano && Game.CINEMATICAS && map.containsKey("allin/") && map.get("allin/").length > 0) {

            Object[][] allin_cinematics = map.get("allin/");

            int r = Helpers.PRNG_GENERATOR.nextInt(allin_cinematics.length) + 1;

            String filename = (String) allin_cinematics[r - 1][0];

            long pausa = (long) allin_cinematics[r - 1][1];

            try {

                this.current_local_cinematic_b64 = Base64.encodeBase64String((Base64.encodeBase64String(filename.getBytes("UTF-8")) + "#" + String.valueOf(pausa)).getBytes("UTF-8"));

                if (pausa == 0L) {
                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            if (Helpers.playWavResourceAndWait("allin/" + filename.replaceAll("\\.gif$", ".wav"))) {

                                if (Game.getInstance().isPartida_local()) {

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

                            if (Helpers.playWavResourceAndWait("allin/" + filename.replaceAll("\\.gif$", ".wav"))) {

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
                    Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                    Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                    Game.getInstance().getBarra_tiempo().setIndeterminate(true);
                }
            });

            if (Game.CINEMATICAS) {

                ImageIcon icon = null;

                if (Init.MOD != null && Files.exists(Paths.get(Helpers.getCurrentJarPath() + "/mod/cinematics/allin/" + filename))) {

                    icon = new ImageIcon(Helpers.getCurrentJarPath() + "/mod/cinematics/allin/" + filename);

                } else if (getClass().getResource("/cinematics/allin/" + filename) != null) {
                    icon = new ImageIcon(getClass().getResource("/cinematics/allin/" + filename));
                }

                if (icon != null) {

                    GifAnimation gif = new GifAnimation(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), false, icon);

                    gif.setLocationRelativeTo(gif.getParent());

                    gif.setVisible(true);

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
                                    gif.dispose();

                                    Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                                    Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                                    Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                                }
                            });

                            synchronized (Game.getInstance().getCrupier().getLock_apuestas()) {
                                Game.getInstance().getCrupier().getLock_apuestas().notifyAll();
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
                                    Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                                    Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                                    Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                                }
                            });

                            synchronized (Game.getInstance().getCrupier().getLock_apuestas()) {
                                Game.getInstance().getCrupier().getLock_apuestas().notifyAll();
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
                                Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                                Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                                Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                            }
                        });

                        synchronized (Game.getInstance().getCrupier().getLock_apuestas()) {
                            Game.getInstance().getCrupier().getLock_apuestas().notifyAll();
                        }
                    }
                });

            }

        }

        return (pausa == 0L);

    }

    public void soundAllin() {

        if (!this.sincronizando_mano && Game.SONIDOS_CHORRA && !fold_sound_playing) {

            Helpers.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_SOUNDS_MOD) : Map.ofEntries(Crupier.ALLIN_SOUNDS));

        }

    }

    public void soundFold() {
        if (!this.sincronizando_mano && Game.SONIDOS_CHORRA && !fold_sound_playing) {
            this.fold_sound_playing = true;
            Helpers.threadRun(new Runnable() {
                public void run() {
                    Helpers.playRandomWavResourceAndWait(Init.MOD != null ? Map.ofEntries(Crupier.FOLD_SOUNDS_MOD) : Map.ofEntries(Crupier.FOLD_SOUNDS));
                    fold_sound_playing = false;
                }
            });
        }
    }

    public void soundShowdown() {
        if (!this.sincronizando_mano && Game.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Helpers.muteLoopMp3();
                        Helpers.playWavResourceAndWait("misc/badbeat.wav");
                        Helpers.unmuteLoopMp3();
                    }
                });
            } else if (jugada_ganadora >= Hand.POKER) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Helpers.muteLoopMp3();
                        Helpers.playWavResourceAndWait("misc/youarelucky.wav");
                        Helpers.unmuteLoopMp3();
                    }
                });

            } else {
                Helpers.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.SHOWDOWN_SOUNDS_MOD, Crupier.WINNER_SOUNDS_MOD, Crupier.LOSER_SOUNDS_MOD) : Map.ofEntries(Crupier.SHOWDOWN_SOUNDS, Crupier.WINNER_SOUNDS, Crupier.LOSER_SOUNDS));
            }
        }
    }

    public void soundWinner(int jugada, boolean ultima_carta) {
        if (!this.sincronizando_mano && Game.SONIDOS_CHORRA && !fold_sound_playing) {

            if (jugada >= Hand.POKER || badbeat) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Helpers.muteLoopMp3();
                        Helpers.playWavResourceAndWait("misc/youarelucky.wav");
                        Helpers.unmuteLoopMp3();
                    }
                });

            } else {

                Map<String, String[]> sonidos;

                if (ultima_carta) {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD, new HashMap.SimpleEntry<String, String[]>("misc/", new String[]{"lastcard.wav"})) : Map.ofEntries(Crupier.WINNER_SOUNDS, new HashMap.SimpleEntry<String, String[]>("misc/", new String[]{"lastcard.wav"}));

                } else {

                    sonidos = Init.MOD != null ? Map.ofEntries(Crupier.WINNER_SOUNDS_MOD) : Map.ofEntries(Crupier.WINNER_SOUNDS);
                }

                Helpers.playRandomWavResource(sonidos);
            }
        }
    }

    public void soundLoser(int jugada) {
        if (!this.sincronizando_mano && Game.SONIDOS_CHORRA && !fold_sound_playing) {

            if (badbeat) {
                Helpers.threadRun(new Runnable() {
                    public void run() {
                        Helpers.muteLoopMp3();
                        Helpers.playWavResourceAndWait("misc/badbeat.wav");
                        Helpers.unmuteLoopMp3();
                    }
                });
            } else if (jugada >= Hand.FULL) {

                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("loser/", new String[]{
                    "encargado.wav",
                    "matias.wav"});

                Helpers.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));

            } else {
                Helpers.playRandomWavResource(Init.MOD != null ? Map.ofEntries(Crupier.LOSER_SOUNDS_MOD) : Map.ofEntries(Crupier.LOSER_SOUNDS));
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

            for (Player jugador : Game.getInstance().getJugadores()) {
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

            Game.getInstance().getRegistro().print(status);

            Game.getInstance().getRegistro().print(Translator.translate("AUDITOR DE CUENTAS") + " -> STACKS: " + Helpers.float2String(stack_sum) + " / BUYIN: " + Helpers.float2String(buyin_sum) + " / INDIVISIBLE: " + Helpers.float2String(this.bote_sobrante));

            if (Helpers.float1DSecureCompare(Helpers.clean1DFloat(stack_sum) + Helpers.clean1DFloat(this.bote_sobrante), buyin_sum) != 0) {
                Game.getInstance().getRegistro().print("¡OJO A ESTO: NO SALEN LAS CUENTAS GLOBALES! -> (STACKS + INDIVISIBLE) != BUYIN");
            }
        }
    }

    private void recibirRebuys(ArrayList<String> pending) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setIndeterminate(true);
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

                        if (Game.getInstance().isPartida_local()) {

                            broadcastGAMECommandFromServer("REBUY#" + partes[3] + (partes.length > 4 ? "#" + partes[4] : ""), nick);
                        }

                        if (partes.length > 4) {

                            if (partes[4].equals("0")) {
                                jugador.setSpectator(null);
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

            if (!pending.isEmpty()) {
                Iterator<String> iterator = pending.iterator();

                while (iterator.hasNext()) {
                    String nick = iterator.next();

                    if (nick2player.get(nick).isExit()) {
                        iterator.remove();
                    }
                }

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > 2 * Game.REBUY_TIMEOUT) {

                    if (Game.getInstance().isPartida_local()) {

                        if (!pending.isEmpty()) {

                            for (String nick : pending) {
                                nick2player.get(nick).setTimeout(true);
                            }

                        }

                        int input = Helpers.mostrarMensajeErrorSINO(Game.getInstance(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

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
                Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
            }
        });

    }

    public synchronized void remotePlayerQuit(String nick) {

        Player jugador = nick2player.get(nick);

        if (jugador != null && !jugador.isExit()) {

            jugador.setExit();

            if (Game.getInstance().isPartida_local()) {

                Game.getInstance().getParticipantes().get(nick).setExit();

                try {

                    broadcastGAMECommandFromServer("EXIT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (Game.getInstance().getParticipantes().get(nick).isCpu()) {
                    Game.getInstance().getSala_espera().borrarParticipante(nick);
                }

            } else {

                Game.getInstance().getSala_espera().borrarParticipante(nick);
            }

            synchronized (this.getReceived_commands()) {
                this.getReceived_commands().notifyAll();
            }

            synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {
                WaitingRoom.getInstance().getReceived_confirmations().notifyAll();
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

    private void actualizarContadoresTapete() {

        Game.getInstance().setTapeteBote(this.bote_total);
        Game.getInstance().setTapeteApuestas(this.apuestas);
        Game.getInstance().setTapeteCiegas(this.ciega_pequeña, this.ciega_grande);
        Game.getInstance().setTapeteMano(this.mano);
    }

    private void resetBetPlayerDecisions(ArrayList<Player> jugadores, String nick) {

        if (nick == null) {
            this.last_aggressor = null;
        } else {
            this.last_aggressor = nick2player.get(nick);
        }

        for (Player jugador : jugadores) {

            if (jugador.isActivo() && jugador.getDecision() != Player.FOLD && jugador.getDecision() != Player.ALLIN && (nick == null || !jugador.getNickname().equals(nick))) {
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

                if (jugador != Game.getInstance().getLocalPlayer()) {

                    Helpers.playWavResource("misc/uncover.wav");

                    jugador.getPlayingCard1().destapar(false);
                    jugador.getPlayingCard2().destapar(false);

                    ArrayList<Card> cartas = new ArrayList<>();

                    cartas.add(jugador.getPlayingCard1());
                    cartas.add(jugador.getPlayingCard2());

                    String lascartas = Card.collection2String(cartas);

                    for (Card carta_comun : Game.getInstance().getCartas_comunes()) {

                        if (!carta_comun.isTapada()) {
                            cartas.add(carta_comun);
                        }
                    }

                    Hand jugada = new Hand(cartas);

                    jugador.showCards(jugada.getName());

                    if (Game.SONIDOS_CHORRA && jugador.getDecision() == Player.FOLD) {
                        Helpers.playWavResource("misc/showyourcards.wav");
                    }

                    if (!perdedores.containsKey(jugador)) {
                        Game.getInstance().getRegistro().print(nick + Translator.translate(" MUESTRA (") + lascartas + ") -> " + jugada);
                    }
                }

                setTiempo_pausa(Game.TEST_MODE ? Game.PAUSA_ENTRE_MANOS_TEST : Game.PAUSA_ENTRE_MANOS);

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

                String[] carta1_partes = carta1.split("_");
                String[] carta2_partes = carta2.split("_");

                jugador.getPlayingCard1().cargarCarta(carta1_partes[0], carta1_partes[1]);
                jugador.getPlayingCard2().cargarCarta(carta2_partes[0], carta2_partes[1]);

                Helpers.playWavResource("misc/uncover.wav");

                jugador.getPlayingCard1().destapar(false);
                jugador.getPlayingCard2().destapar(false);

                ArrayList<Card> cartas = new ArrayList<>();

                cartas.add(jugador.getPlayingCard1());
                cartas.add(jugador.getPlayingCard2());

                String lascartas = Card.collection2String(cartas);

                for (Card carta_comun : Game.getInstance().getCartas_comunes()) {

                    if (!carta_comun.isTapada()) {
                        cartas.add(carta_comun);
                    }
                }

                Hand jugada = new Hand(cartas);

                jugador.showCards(jugada.getName());

                if (Game.SONIDOS_CHORRA && jugador.getDecision() == Player.FOLD) {
                    Helpers.playWavResource("misc/showyourcards.wav");
                }

                if (!perdedores.containsKey(jugador)) {
                    Game.getInstance().getRegistro().print(nick + Translator.translate(" MUESTRA (") + lascartas + ") -> " + jugada);
                }

                setTiempo_pausa(Game.TEST_MODE ? Game.PAUSA_ENTRE_MANOS_TEST : Game.PAUSA_ENTRE_MANOS);
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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

    private void recibirDatosRecover() {

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

                        try {
                            ok = true;

                            Files.writeString(Paths.get(Crupier.RECOVER_BALANCE_FILE + "_" + Game.getInstance().getNick_local()), new String(Base64.decodeBase64(partes[3]), "UTF-8"));

                            if (partes.length > 4) {
                                Files.writeString(Paths.get(Crupier.RECOVER_ACTIONS_FILE + "_" + Game.getInstance().getNick_local()), new String(Base64.decodeBase64(partes[4]), "UTF-8"));
                            }
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

    public int getJugadoresActivos() {

        int t = 0;

        for (Player j : Game.getInstance().getJugadores()) {
            if (j.isActivo()) {
                t++;
            }
        }

        return t;
    }

    private boolean recuperarDatosClavePartida() {

        boolean saltar_primera_mano = false;

        // SERVER_NICK + BUYIN + REBUY + TIEMPO_JUEGO + CONTA_MANO + CIEGA_PEQUEÑA + CIEGA_GRANDE + TIEMPO_CIEGAS + CIEGAS_DOBLADAS + DEALER
        try {
            String datos = Files.readString(Paths.get(Crupier.RECOVER_BALANCE_FILE + (Game.getInstance().isPartida_local() ? "" : "_" + Game.getInstance().getNick_local())));

            String[] partes = datos.split("#");

            Game.BUYIN = Integer.parseInt(partes[1]);

            Game.REBUY = Boolean.parseBoolean(partes[2]);

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    Game.getInstance().getAuto_rebuy_menu().setEnabled(Game.REBUY);
                    Helpers.TapetePopupMenu.AUTOREBUY_MENU.setEnabled(Game.REBUY);

                }
            });

            Game.getInstance().setConta_tiempo_juego(Long.parseLong(partes[3]));

            this.mano = Integer.parseInt(partes[4]);

            this.ciega_pequeña = Float.parseFloat(partes[5]);

            this.ciega_grande = Float.parseFloat(partes[6]);

            Game.CIEGAS_TIME = Integer.parseInt(partes[7]);

            this.ciegas_double = Integer.parseInt(partes[8]);

            String dealer = new String(Base64.decodeBase64(partes[9]), "UTF-8");

            String[] auditor_partes = partes[10].split("@");

            ArrayList<String> nicks_recuperados = new ArrayList<>();

            for (String player_data : auditor_partes) {

                partes = player_data.split("\\|");

                try {
                    String name = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                    nicks_recuperados.add(name);

                    Player jugador = nick2player.get(name);

                    if (jugador != null) {

                        jugador.setStack(Float.parseFloat(partes[1]));

                        jugador.setBuyin(Integer.parseInt(partes[2]));

                        jugador.setBet(0f);

                        if (Helpers.float1DSecureCompare(0f, jugador.getStack()) == 0) {
                            jugador.setSpectator(null);
                        } else if (nick2player.get(dealer) == null) {
                            dealer = jugador.getNickname();
                        }
                        this.auditor.put(name, new Float[]{Float.parseFloat(partes[1]), Float.parseFloat(partes[2])});
                    } else {
                        this.auditor.put(name, new Float[]{Float.parseFloat(partes[1]), Float.parseFloat(partes[2])});

                        if (Helpers.float1DSecureCompare(0f, Float.parseFloat(partes[1])) < 0) {

                            String ganancia_msg = "";

                            float ganancia = Helpers.clean1DFloat(Helpers.clean1DFloat(Float.parseFloat(partes[1])) - Helpers.clean1DFloat(Float.parseFloat(partes[2])));

                            if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                                ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                            } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                                ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                            } else {
                                ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                            }

                            Game.getInstance().getRegistro().print(name + " " + Translator.translate("ABANDONA LA TIMBA") + " -> " + ganancia_msg);

                            saltar_primera_mano = true;
                        }
                    }

                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (!nicks_recuperados.contains(jugador.getNickname())) {

                    jugador.setSpectator(Translator.translate("CALENTANDO"));

                    this.auditor.put(jugador.getNickname(), new Float[]{jugador.getStack(), (float) jugador.getBuyin()});

                    Game.getInstance().getRegistro().print(jugador.getNickname() + Translator.translate(" se UNE a la TIMBA."));
                }
            }

            if (Game.getInstance().getJugadores().size() - this.getTotalSpectators() == 1) {

                for (Player jugador : Game.getInstance().getJugadores()) {

                    if (jugador.isSpectator() && Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0) {

                        jugador.unsetSpectator();
                    }
                }
            }

            this.dealer_pos = 0;

            while (this.dealer_pos < Game.getInstance().getJugadores().size()) {

                if (Game.getInstance().getJugadores().get(this.dealer_pos).getNickname().equals(dealer)) {
                    break;
                }

                this.dealer_pos++;
            }

            if (getJugadoresActivos() == 2) {

                this.small_pos = this.dealer_pos;

                this.utg_pos = this.dealer_pos;

                this.big_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();

                while (!Game.getInstance().getJugadores().get(this.big_pos).isActivo()) {

                    this.big_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();
                }

            } else {

                this.small_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();

                while (!Game.getInstance().getJugadores().get(this.small_pos).isActivo()) {

                    this.small_pos = (this.small_pos + 1) % Game.getInstance().getJugadores().size();
                }

                this.big_pos = (this.small_pos + 1) % Game.getInstance().getJugadores().size();

                while (!Game.getInstance().getJugadores().get(this.big_pos).isActivo()) {

                    this.big_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();
                }

                this.utg_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();

                while (!Game.getInstance().getJugadores().get(this.utg_pos).isActivo()) {
                    this.utg_pos = (this.utg_pos + 1) % Game.getInstance().getJugadores().size();
                }
            }

            for (Player jugador : Game.getInstance().getJugadores()) {
                jugador.refreshPos();
            }

            actualizarContadoresTapete();
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

        return saltar_primera_mano;

    }

    private String recibirDealer() {

        String dealer = null;

        boolean ok;

        long start_time = System.currentTimeMillis();

        do {

            ok = false;

            synchronized (this.getReceived_commands()) {

                ArrayList<String> rejected = new ArrayList<>();

                while (!ok && !this.getReceived_commands().isEmpty()) {

                    String comando = this.received_commands.poll();

                    String[] partes = comando.split("#");

                    if (partes[2].equals("DEALER")) {

                        ok = true;

                        try {
                            dealer = new String(Base64.decodeBase64(partes[3]), "UTF-8");
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        if (partes.length == 5) {
                            Game.getInstance().setConta_tiempo_juego(Long.parseLong(partes[4]));
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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

        return dealer;

    }

    public float getUltimo_raise() {
        return ultimo_raise;
    }

    private boolean checkDoblarCiegas() {

        return (Game.CIEGAS_TIME > 0 && (int) Math.floor((float) Game.getInstance().getConta_tiempo_juego() / (Game.CIEGAS_TIME * 60)) > this.ciegas_double);
    }

    private void doblarCiegas() {

        int i = 0;

        for (float[] f : CIEGAS) {

            if (f[0] == this.ciega_pequeña) {
                break;
            }

            i++;
        }

        this.ciegas_double++;

        i++;

        int exp = (int) (i / CIEGAS.length);

        double mul = Math.pow(10, exp);

        this.ciega_pequeña = Helpers.clean1DFloat(CIEGAS[i % CIEGAS.length][0] * (float) mul);

        this.ciega_grande = Helpers.clean1DFloat(CIEGAS[i % CIEGAS.length][1] * (float) mul);

        Helpers.playWavResource("misc/double_blinds.wav");

        Game.getInstance().getRegistro().print("SE DOBLAN LAS CIEGAS");

    }

    public float getBote_total() {
        return bote_total;
    }

    private boolean NUEVA_MANO() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                Game.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(false);
                Game.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Game.getInstance().getTapete().getCommunityCards().getBet_label().getForeground());

                Game.getInstance().getBarra_tiempo().setIndeterminate(true);

                if (!Game.getInstance().isPartida_local()) {
                    Game.getInstance().getExit_menu().setEnabled(false);
                    Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(false);
                }
            }
        });

        //SINCRONIZACIÓN DE LA MANO
        //Esperamos a recibir el comando de confirmación de que están listos para una nueva mano
        if (Game.getInstance().isPartida_local()) {

            boolean ready;

            do {

                ready = true;

                for (Map.Entry<String, Participant> entry : Game.getInstance().getParticipantes().entrySet()) {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && !p.isExit() && p.getNew_hand_ready() <= this.mano) {

                        ready = false;

                        break;

                    }

                }

                if (!ready) {

                    synchronized (lock_nueva_mano) {

                        try {
                            lock_nueva_mano.wait(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }

            } while (!ready);

        } else {

            this.sendGAMECommandToServer("NEWHANDREADY#" + String.valueOf(this.mano + 1));

        }

        for (Player jugador : Game.getInstance().getJugadores()) {

            if (jugador.isSpectator() && Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0) {
                jugador.unsetSpectator();
            }
        }

        for (Player jugador : Game.getInstance().getJugadores()) {

            if (jugador.isActivo()) {
                jugador.getPlayingCard1().descargarCarta();
                jugador.getPlayingCard2().descargarCarta();
            }

        }

        for (Card carta : Game.getInstance().getCartas_comunes()) {
            carta.descargarCarta();
        }

        this.mano++;

        Bot.BOT_COMMUNITY_CARDS.makeEmpty();

        Game.getInstance().getRegistro().print("\n*************** " + Translator.translate("MANO") + " (" + String.valueOf(this.mano) + ") ***************");

        //Colocamos al dealer, CP y CG
        this.setPositions();

        this.badbeat = false;

        this.jugada_ganadora = 0;

        this.perdedores.clear();

        this.fase = PREFLOP;

        this.cartas_resistencia = false;

        this.destapar_resistencia = false;

        this.apuesta_actual = this.ciega_grande;

        this.ultimo_raise = 0f;

        this.conta_raise = 0;

        this.conta_bet = 0;

        if (Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {

            if (Game.SONIDOS_CHORRA) {
                Helpers.playWavResource("misc/indivisible.wav");
            }

            Helpers.playWavResource("misc/cash_register.wav");

            Game.getInstance().getRegistro().print(Translator.translate("BOTE SOBRANTE NO DIVISIBLE") + " -> " + Helpers.float2String(bote_sobrante));

        }

        this.bote_total = 0f;

        this.bote = new Pot(0f);

        int i = 0;

        for (Player jugador : Game.getInstance().getJugadores()) {

            if (jugador.isActivo()) {
                jugador.nuevaMano(i);
            }

            i++;
        }

        Integer[] permutacion_recuperada = null;

        boolean saltar_mano_recover = false;

        //Actualizamos los datos en caso de estar en modo recover
        if (Game.isRECOVER()) {

            Game.getInstance().getRegistro().print("RECUPERANDO TIMBA...");

            if (Game.getInstance().isPartida_local()) {

                try {

                    String data = Base64.encodeBase64String(Files.readString(Paths.get(Crupier.RECOVER_BALANCE_FILE)).getBytes("UTF-8"));

                    if (Files.exists(Paths.get(Crupier.RECOVER_ACTIONS_FILE))) {
                        data += "#" + Base64.encodeBase64String(Files.readString(Paths.get(Crupier.RECOVER_ACTIONS_FILE)).getBytes("UTF-8"));
                    }

                    this.broadcastGAMECommandFromServer("RECOVERDATA#" + data, null);
                } catch (IOException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else {

                recibirDatosRecover();
            }

            recover_dialog = new RecoverDialog(Game.getInstance(), true);

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    recover_dialog.setLocationRelativeTo(recover_dialog.getParent());
                    recover_dialog.pack();
                    recover_dialog.setVisible(true);

                }
            });

            saltar_mano_recover = recuperarDatosClavePartida();

            if (getJugadoresActivos() > 1 && !saltar_mano_recover) {

                Game.getInstance().getRegistro().print("\n*************** " + Translator.translate("MANO RECUPERADA") + " (" + String.valueOf(this.mano) + ") ***************");

                if (Game.getInstance().isPartida_local()) {
                    permutacion_recuperada = this.recuperarPermutacion(Crupier.RECOVER_DECK_FILE);
                }

                recuperarAccionesLocales();

                if (!this.acciones_recuperadas.isEmpty()) {
                    this.sincronizando_mano = true;
                } else {
                    Game.getInstance().getRegistro().print("TIMBA RECUPERADA");
                    Helpers.playWavResource("misc/cash_register.wav");
                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            recover_dialog.setVisible(false);
                            recover_dialog.dispose();
                            recover_dialog = null;
                            Game.getInstance().getFull_screen_menu().setEnabled(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                        }
                    });
                }
            } else {
                Game.getInstance().getRegistro().print("TIMBA RECUPERADA");
                Helpers.playWavResource("misc/cash_register.wav");
                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        recover_dialog.setVisible(false);
                        recover_dialog.dispose();
                        recover_dialog = null;
                        Game.getInstance().getFull_screen_menu().setEnabled(true);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                    }
                });
            }

            Game.setRECOVER(false);

        } else {

            borrarAcciones();

            if (Game.getInstance().isPartida_local()) {
                Helpers.deleteFile(Crupier.RECOVER_DECK_FILE);
            }

            if (Game.getInstance().isPartida_local()) {
                preservarDatosClavePartida();
            }
        }

        if (getJugadoresActivos() > 1 && !saltar_mano_recover) {

            this.apuestas = Game.getInstance().getJugadores().get(this.big_pos).getBet() + Game.getInstance().getJugadores().get(this.small_pos).getBet();

            actualizarContadoresTapete();

            Object lock = new Object();

            barajando = true;

            Helpers.threadRun(new Runnable() {
                public void run() {
                    if (Game.CINEMATICAS) {
                        playing_cinematic = true;
                        ImageIcon icon = new ImageIcon(getClass().getResource("/cinematics/misc/shuffle.gif"));

                        if (icon != null) {
                            GifAnimation gif = new GifAnimation(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), false, icon);
                            Helpers.GUIRun(new Runnable() {
                                public void run() {

                                    gif.setLocationRelativeTo(gif.getParent());

                                    gif.setVisible(true);
                                }
                            });

                            Helpers.threadRun(new Runnable() {

                                public void run() {
                                    Helpers.pausar(2500);

                                    Helpers.GUIRun(new Runnable() {
                                        public void run() {

                                            gif.dispose();

                                            playing_cinematic = false;
                                        }
                                    });
                                }
                            });

                            Helpers.playWavResourceAndWait("misc/shuffle.wav");

                        } else {
                            playing_cinematic = false;

                            Helpers.playWavResourceAndWait("misc/shuffle.wav");
                        }

                    } else {
                        playing_cinematic = false;
                        Helpers.playWavResourceAndWait("misc/shuffle.wav");
                    }

                    barajando = false;

                    synchronized (lock) {

                        lock.notifyAll();
                    }
                }
            });

            if (Game.getInstance().isPartida_local()) {

                if (permutacion_recuperada != null) {
                    permutacion_baraja = permutacion_recuperada;
                } else {
                    permutacion_baraja = Helpers.getIntegerPermutation(Helpers.DECK_RANDOM_GENERATOR, 52);
                    preservarPermutacion(permutacion_baraja, RECOVER_DECK_FILE);
                }

                preCargarCartas();

                enviarCartasJugadoresRemotos();

            } else if (Game.getInstance().getLocalPlayer().isActivo()) {

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
                    Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                    Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                    Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                    Game.getInstance().getExit_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(true);
                }
            });

            return true;

        } else {

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (jugador.isActivo()) {
                    jugador.pagar(jugador.getBet());
                }

            }
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                Game.getInstance().getBarra_tiempo().setIndeterminate(false);
                Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                Game.getInstance().getExit_menu().setEnabled(true);
                Helpers.TapetePopupMenu.EXIT_MENU.setEnabled(true);
            }
        });

        return false;
    }

    private void repartir() {

        int pausa = Math.max(100, Math.round(REPARTIR_PAUSA * (2f / this.getJugadoresActivos())));

        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                Game.getInstance().getAnimacion_menu().setEnabled(false);
                Helpers.TapetePopupMenu.ANIMACION_MENU.setEnabled(false);

            }
        });

        if (!Game.ANIMACION_REPARTIR) {

            for (Card carta : Game.getInstance().getCartas_comunes()) {
                carta.cargarCarta();
            }

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (jugador.isActivo()) {

                    jugador.getPlayingCard1().cargarCarta();
                    jugador.getPlayingCard2().cargarCarta();
                }
            }
        }

        int j, pivote = (this.getDealer_pos() + 1) % Game.getInstance().getJugadores().size();

        j = pivote;

        do {

            Player jugador = Game.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && Game.ANIMACION_REPARTIR) {

                Helpers.playWavResource("misc/deal.wav");

                if (jugador == Game.getInstance().getLocalPlayer()) {

                    if (Game.getInstance().isPartida_local()) {

                        jugador.getPlayingCard1().cargarCarta();

                    } else {

                        String[] carta = cartas_locales_recibidas.get(0).split("_");

                        jugador.getPlayingCard1().cargarCarta(carta[0], carta[1]);
                    }

                    jugador.getPlayingCard1().destapar(false);

                } else {

                    jugador.getPlayingCard1().cargarCarta();

                }
            } else if (jugador.isActivo() && jugador == Game.getInstance().getLocalPlayer()) {

                Helpers.playWavResource("misc/deal.wav");

                if (Game.getInstance().isPartida_local()) {

                    jugador.getPlayingCard1().cargarCarta();

                } else {

                    String[] carta = cartas_locales_recibidas.get(0).split("_");

                    jugador.getPlayingCard1().cargarCarta(carta[0], carta[1]);
                }

                jugador.getPlayingCard1().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % Game.getInstance().getJugadores().size();

        } while (j != pivote);

        do {

            Player jugador = Game.getInstance().getJugadores().get(j);

            if (jugador.isActivo() && Game.ANIMACION_REPARTIR) {

                Helpers.playWavResource("misc/deal.wav");

                if (jugador == Game.getInstance().getLocalPlayer()) {

                    if (Game.getInstance().isPartida_local()) {

                        jugador.getPlayingCard2().cargarCarta();

                    } else {

                        String[] carta = cartas_locales_recibidas.get(1).split("_");

                        jugador.getPlayingCard2().cargarCarta(carta[0], carta[1]);

                    }

                    jugador.getPlayingCard2().destapar(false);

                } else {

                    jugador.getPlayingCard2().cargarCarta();
                }
            } else if (jugador.isActivo() && jugador == Game.getInstance().getLocalPlayer()) {

                Helpers.playWavResource("misc/deal.wav");

                if (Game.getInstance().isPartida_local()) {

                    jugador.getPlayingCard2().cargarCarta();

                } else {

                    String[] carta = cartas_locales_recibidas.get(1).split("_");

                    jugador.getPlayingCard2().cargarCarta(carta[0], carta[1]);
                }

                jugador.getPlayingCard2().destapar(false);

            }

            if (jugador.isActivo()) {
                Helpers.pausar(pausa);
            }

            j = (j + 1) % Game.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : Game.getInstance().getCartas_comunes()) {

            if (carta == Game.getInstance().getFlop1() || carta == Game.getInstance().getTurn() || carta == Game.getInstance().getRiver()) {

                if (Game.ANIMACION_REPARTIR) {
                    Helpers.playWavResource("misc/deal.wav");
                }

                Helpers.pausar(pausa);
            }

            if (Game.ANIMACION_REPARTIR) {
                Helpers.playWavResource("misc/deal.wav");
                carta.cargarCarta();
            }

            Helpers.pausar(pausa);
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                Game.getInstance().getAnimacion_menu().setEnabled(true);
                Helpers.TapetePopupMenu.ANIMACION_MENU.setEnabled(true);

            }
        });
    }

    private void preCargarCartas() {

        int p = 0, j, pivote = (this.getDealer_pos() + 1) % Game.getInstance().getJugadores().size();

        //Repartirmos la primera carta a todos los jugadores
        j = pivote;

        do {

            Player jugador = Game.getInstance().getJugadores().get(j);

            if (jugador.isActivo()) {

                jugador.getPlayingCard1().preCargarCarta(permutacion_baraja[p]);

                p++;
            }

            j = (j + 1) % Game.getInstance().getJugadores().size();

        } while (j != pivote);

        do {

            Player jugador = Game.getInstance().getJugadores().get(j);

            if (jugador.isActivo()) {

                jugador.getPlayingCard2().preCargarCarta(permutacion_baraja[p]);

                p++;
            }

            j = (j + 1) % Game.getInstance().getJugadores().size();

        } while (j != pivote);

        for (Card carta : Game.getInstance().getCartas_comunes()) {

            //Se quema una carta antes de cada calle
            if (carta == Game.getInstance().getFlop1() || carta == Game.getInstance().getTurn() || carta == Game.getInstance().getRiver()) {
                p++;
            }

            carta.preCargarCarta(permutacion_baraja[p]);

            p++;
        }

    }

    private void enviarCartasJugadoresRemotos() {

        long start = System.currentTimeMillis();

        ArrayList<String> pendientes = new ArrayList<>();

        for (Player jugador : Game.getInstance().getJugadores()) {

            if (jugador != Game.getInstance().getLocalPlayer() && jugador.isActivo() && !Game.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                pendientes.add(jugador.getNickname());
            }
        }

        int id = Helpers.SPRNG_GENERATOR.nextInt();

        boolean timeout = false;

        byte[] iv = new byte[16];

        Helpers.SPRNG_GENERATOR.nextBytes(iv);

        do {

            String command = "GAME#" + String.valueOf(id) + "#YOURCARDS";

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (pendientes.contains(jugador.getNickname())) {

                    Participant p = Game.getInstance().getParticipantes().get(jugador.getNickname());

                    if (p != null && !p.isCpu()) {

                        try {
                            String carta1 = jugador.getPlayingCard1().toShortString();

                            String carta2 = jugador.getPlayingCard2().toShortString();

                            p.writeCommandFromServer(Helpers.encryptCommand(command + "#" + carta1 + "@" + carta2, p.getAes_key(), iv, p.getHmac_key()));
                        } catch (IOException ex) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

            //Esperamos confirmaciones
            this.waitSyncConfirmations(id, pendientes);

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (jugador.isExit() && pendientes.contains(jugador.getNickname())) {

                    pendientes.remove(jugador.getNickname());
                }
            }

            if (System.currentTimeMillis() - start > Game.CLIENT_RECON_TIMEOUT) {
                int input = Helpers.mostrarMensajeErrorSINO(Game.getInstance(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

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

        pendientes.add(Game.getInstance().getSala_espera().getServer_nick());

        int id = Helpers.SPRNG_GENERATOR.nextInt();

        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

        do {

            try {

                Game.getInstance().getSala_espera().writeCommandToServer(Helpers.encryptCommand(full_command, Game.getInstance().getSala_espera().getLocal_client_aes_key(), Game.getInstance().getSala_espera().getLocal_client_hmac_key()));

                if (confirmation) {
                    this.waitSyncConfirmations(id, pendientes);
                }

            } catch (IOException ex) {

                if (confirmation) {

                    synchronized (Game.getInstance().getSala_espera().getLocalClientSocketLock()) {

                        try {
                            Game.getInstance().getSala_espera().getLocalClientSocketLock().wait(1000);
                        } catch (InterruptedException ex1) {
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex1);
                        }
                    }
                }
            }

            if (confirmation) {
                if (!pendientes.isEmpty()) {
                    Game.getInstance().getLocalPlayer().setTimeout(true);
                } else {
                    Game.getInstance().getLocalPlayer().setTimeout(false);
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

        while (!pending.isEmpty() && !timeout) {

            ArrayList<Object[]> rejected = new ArrayList<>();

            Object[] confirmation;

            while (!WaitingRoom.getInstance().getReceived_confirmations().isEmpty()) {

                confirmation = WaitingRoom.getInstance().getReceived_confirmations().poll();

                if ((int) confirmation[1] == id + 1) {

                    pending.remove(confirmation[0]);

                    if (nick2player.containsKey(confirmation[0])) {

                        nick2player.get(confirmation[0]).setTimeout(false);

                    }

                } else {
                    rejected.add(confirmation);
                }
            }

            if (!rejected.isEmpty()) {
                WaitingRoom.getInstance().getReceived_confirmations().addAll(rejected);
                rejected.clear();
            }

            if (System.currentTimeMillis() - start_time > Game.CONFIRMATION_TIMEOUT) {
                timeout = true;
            } else if (!pending.isEmpty()) {

                synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {
                    try {
                        WaitingRoom.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);
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

                    if (Game.getInstance().checkPause()) {
                        start = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start > Game.CLIENT_RECON_TIMEOUT) {

                        if (Game.getInstance().isPartida_local()) {

                            jugador.setTimeout(true);

                            int input = Helpers.mostrarMensajeErrorSINO(Game.getInstance(), jugador.getNickname() + Translator.translate(" parece que perdió la conexión y no ha vuelto a conectar (se le eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?"));

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

        switch (fase) {
            case FLOP:
                flop();
                break;
            case TURN:
                turn();
                break;
            case RIVER:
                river();
                break;
            default:
                break;
        }
    }

    public int getConta_raise() {
        return conta_raise;
    }

    private ArrayList<Player> rondaApuestas(int fase, ArrayList<Player> resisten) {

        Iterator<Player> iterator = resisten.iterator();

        while (iterator.hasNext()) {
            Player jugador = iterator.next();

            if (!jugador.isActivo()) {
                iterator.remove();
            }

            if (Game.getInstance().getLocalPlayer() != jugador && ((RemotePlayer) jugador).getBot() != null) {

                ((RemotePlayer) jugador).getBot().resetBot();
            }
        }

        this.fase = fase;

        if (fase > PREFLOP) {

            if (Game.getInstance().isPartida_local()) {

                //Enviamos las cartas comunitarias de esta fase a todos jugadores remotos
                String comando = null;

                switch (fase) {
                    case FLOP:
                        comando = "FLOPCARDS#" + Game.getInstance().getCartas_comunes()[0].toShortString() + "#" + Game.getInstance().getCartas_comunes()[1].toShortString() + "#" + Game.getInstance().getCartas_comunes()[2].toShortString();

                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(Game.getInstance().getFlop1().getValorNumerico() - 2, Bot.getCardSuit(Game.getInstance().getFlop1())));
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(Game.getInstance().getFlop2().getValorNumerico() - 2, Bot.getCardSuit(Game.getInstance().getFlop2())));
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(Game.getInstance().getFlop3().getValorNumerico() - 2, Bot.getCardSuit(Game.getInstance().getFlop3())));

                        break;
                    case TURN:
                        comando = "TURNCARD#" + Game.getInstance().getCartas_comunes()[3].toShortString();
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(Game.getInstance().getTurn().getValorNumerico() - 2, Bot.getCardSuit(Game.getInstance().getTurn())));

                        break;
                    case RIVER:
                        comando = "RIVERCARD#" + Game.getInstance().getCartas_comunes()[4].toShortString();
                        Bot.BOT_COMMUNITY_CARDS.addCard(new org.alberta.poker.Card(Game.getInstance().getRiver().getValorNumerico() - 2, Bot.getCardSuit(Game.getInstance().getRiver())));

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
                        cartas = recibirFlop();

                        for (int i = 0; i < 3; i++) {

                            partes = cartas[i].split("_");

                            Game.getInstance().getCartas_comunes()[i].cargarCarta(partes[0], partes[1]);
                        }

                        break;
                    case TURN:
                        carta = recibirTurn();

                        partes = carta.split("_");

                        Game.getInstance().getCartas_comunes()[3].cargarCarta(partes[0], partes[1]);

                        break;
                    case RIVER:
                        carta = recibirRiver();

                        partes = carta.split("_");

                        Game.getInstance().getCartas_comunes()[4].cargarCarta(partes[0], partes[1]);
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

            int conta_pos = (fase == PREFLOP ? this.utg_pos : this.small_pos);

            int end_pos = conta_pos;

            int decision;

            resetBetPlayerDecisions(Game.getInstance().getJugadores(), null);

            do {
                Game.getInstance().checkPause();

                turno++;

                Object[] accion_recuperada = null;

                Player current_player = Game.getInstance().getJugadores().get(conta_pos);

                if (current_player.isActivo() && current_player.getDecision() != Player.FOLD && current_player.getDecision() != Player.ALLIN) {

                    if (Game.AUTO_ACTION_BUTTONS && current_player != Game.getInstance().getLocalPlayer() && Game.getInstance().getLocalPlayer().getDecision() != Player.FOLD && Game.getInstance().getLocalPlayer().getDecision() != Player.ALLIN) {
                        Game.getInstance().getLocalPlayer().activarPreBotones();
                    }

                    float old_player_bet = current_player.getBet();

                    //Esperamos a que el jugador tome su decisión
                    if (current_player == Game.getInstance().getLocalPlayer()) {

                        current_player.esTuTurno();

                        //SOMOS NOSOTROS (jugador local)
                        if (!this.acciones_recuperadas.isEmpty() && (accion_recuperada = siguienteAccionRecuperada(current_player.getNickname())) != null) {

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

                            if (Game.getInstance().isPartida_local()) {

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

                        Object[] action = null;

                        if (!Game.getInstance().isPartida_local() || !Game.getInstance().getParticipantes().get(current_player.getNickname()).isCpu()) {

                            action = this.readActionFromRemotePlayer(current_player);

                        } else {

                            if (this.acciones_recuperadas.isEmpty() || (action = siguienteAccionRecuperada(current_player.getNickname())) == null) {

                                float call_required = getApuesta_actual() - current_player.getBet();

                                float min_raise = Helpers.float1DSecureCompare(0f, getUltimo_raise()) < 0 ? getUltimo_raise() : getCiega_grande();

                                int decision_loki = ((RemotePlayer) current_player).getBot().calculateBotDecision(resisten.size() - 1);

                                boolean slow_play = ((RemotePlayer) current_player).getBot().isSlow_play();

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

                                        float b;

                                        if (Helpers.float1DSecureCompare(this.getApuesta_actual(), 0f) == 0) {

                                            b = (slow_play && fase != Crupier.RIVER) ? this.getCiega_grande() : (Helpers.SPRNG_GENERATOR.nextInt(3) + 1) * this.getCiega_grande();

                                        } else {

                                            b = getApuesta_actual() + Math.max(min_raise, (Helpers.SPRNG_GENERATOR.nextInt(3) + 1) * this.getCiega_grande());
                                        }

                                        if (Helpers.float1DSecureCompare(current_player.getStack() / 2, b - current_player.getBet()) <= 0) {

                                            action = new Object[]{Player.ALLIN, ""};

                                        } else {

                                            action = new Object[]{Player.BET, b};

                                        }

                                        break;
                                }

                                Helpers.pausar((Helpers.SPRNG_GENERATOR.nextInt(2) + 1) * 1000);
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

                            if (Game.getInstance().isPartida_local()) {

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

                        Game.getInstance().getRegistro().print(current_player.getLastActionString());

                        if (current_player.getDecision() != Player.FOLD) {

                            this.apuestas += current_player.getBet() - old_player_bet;

                            if (decision == Player.BET || (decision == Player.ALLIN && Helpers.float1DSecureCompare(this.apuesta_actual, current_player.getBet()) <= 0)) {

                                this.conta_bet++;

                                //El jugador actual subió la apuesta, así que hay que reiniciar la ronda de apuestas
                                if (Helpers.float1DSecureCompare(this.apuesta_actual, current_player.getBet()) < 0) {

                                    this.ultimo_raise = current_player.getBet() - this.apuesta_actual;
                                    this.conta_raise++;
                                }

                                this.apuesta_actual = current_player.getBet();

                                resetBetPlayerDecisions(Game.getInstance().getJugadores(), current_player.getNickname());

                                end_pos = conta_pos;
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

                if (current_player.getDecision() != Player.NODEC && Game.getInstance().isPartida_local()) {
                    preservarAcciones();
                }

                actualizarContadoresTapete();

                conta_pos++;

                if (conta_pos >= Game.getInstance().getJugadores().size()) {
                    conta_pos %= Game.getInstance().getJugadores().size();
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

            this.bote_total += this.apuestas;

            this.apuestas = 0f;

            actualizarContadoresTapete();
        }

        if (resisten.size() > 1 && puedenApostar(resisten) <= 1) {

            Game.getInstance().hideTapeteApuestas();

            this.destapar_resistencia = true;

            if (resisten.contains(Game.getInstance().getLocalPlayer())) {

                Game.getInstance().getLocalPlayer().desactivarControles();
            }

            procesarCartasResistencia(resisten, true);
        }

        if (this.fase == Crupier.PREFLOP) {
            Game.getInstance().getJugadores().get(this.getUtg_pos()).disableUTG();
        }

        return (resisten.size() > 1 && fase < RIVER && getJugadoresActivos() > 1) ? rondaApuestas(fase + 1, resisten) : resisten;
    }

    public int getTotalCalentando() {

        int t = 0;

        for (Player jugador : Game.getInstance().getJugadores()) {
            if (jugador.isSpectator() && !jugador.isExit() && Helpers.float1DSecureCompare(0f, jugador.getStack()) < 0) {
                t++;
            }
        }

        return t;
    }

    private int getTotalSpectators() {

        int t = 0;

        for (Player jugador : Game.getInstance().getJugadores()) {
            if (jugador.isSpectator()) {
                t++;
            }
        }

        return t;
    }

    private int getTotalExit() {

        int t = 0;

        for (Player jugador : Game.getInstance().getJugadores()) {
            if (jugador.isExit()) {
                t++;
            }
        }

        return t;
    }

    private void sentarParticipantes() {

        String pivote = Game.getInstance().getNick_local();

        int i = 0;

        while (!this.nicks_permutados[i].equals(pivote)) {
            i++;
        }

        for (int j = 0; j < this.nicks_permutados.length; j++) {

            Game.getInstance().getJugadores().get(j).setNickname(this.nicks_permutados[(j + i) % this.nicks_permutados.length]);
        }

    }

    public void broadcastGAMECommandFromServer(String command, String skip_nick, boolean confirmation) {

        long start = System.currentTimeMillis();

        ArrayList<String> pendientes = new ArrayList<>();

        for (Map.Entry<String, Participant> entry : Game.getInstance().getParticipantes().entrySet()) {

            Participant p = entry.getValue();

            if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && !p.isExit()) {

                pendientes.add(p.getNick());

            }

        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.SPRNG_GENERATOR.nextInt();

            boolean timeout = false;

            byte[] iv = new byte[16];

            Helpers.SPRNG_GENERATOR.nextBytes(iv);

            do {

                String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                for (Map.Entry<String, Participant> entry : Game.getInstance().getParticipantes().entrySet()) {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && pendientes.contains(p.getNick())) {

                        try {
                            p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));
                        } catch (IOException ex) {
                        }

                    }
                }

                if (confirmation) {
                    //Esperamos confirmaciones y en caso de que alguna no llegue pasado un tiempo volvermos a enviar todos los que fallaron la confirmación la primera vez
                    this.waitSyncConfirmations(id, pendientes);

                    for (Map.Entry<String, Participant> entry : Game.getInstance().getParticipantes().entrySet()) {

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
                        }

                    }

                    if (System.currentTimeMillis() - start > Game.CLIENT_RECON_TIMEOUT) {
                        int input = Helpers.mostrarMensajeErrorSINO(Game.getInstance(), "Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?");

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
                                    Game.getInstance().getParticipantes().get(nick).setExit();
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

    private void calcularPosiciones() {

        if (Game.getInstance().isPartida_local()) {

            if (this.dealer_pos == -1) {
                this.dealer_pos = 0;

                for (int i = 0; i < Game.getInstance().getJugadores().size(); i++) {

                    if (Game.getInstance().getJugadores().get(i).getNickname().equals(this.nicks_permutados[0])) {
                        break;
                    } else {
                        this.dealer_pos++;
                    }
                }
            } else {

                this.dealer_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();

                while (!Game.getInstance().getJugadores().get(this.dealer_pos).isActivo()) {

                    this.dealer_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();
                }

            }
        }

        if (getJugadoresActivos() == 2) {

            this.small_pos = this.dealer_pos;

            this.utg_pos = this.dealer_pos;

            this.big_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();

            while (!Game.getInstance().getJugadores().get(this.big_pos).isActivo()) {

                this.big_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();
            }

        } else {

            this.small_pos = (this.dealer_pos + 1) % Game.getInstance().getJugadores().size();

            while (!Game.getInstance().getJugadores().get(this.small_pos).isActivo()) {

                this.small_pos = (this.small_pos + 1) % Game.getInstance().getJugadores().size();
            }

            this.big_pos = (this.small_pos + 1) % Game.getInstance().getJugadores().size();

            while (!Game.getInstance().getJugadores().get(this.big_pos).isActivo()) {

                this.big_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();
            }

            this.utg_pos = (this.big_pos + 1) % Game.getInstance().getJugadores().size();

            while (!Game.getInstance().getJugadores().get(this.utg_pos).isActivo()) {
                this.utg_pos = (this.utg_pos + 1) % Game.getInstance().getJugadores().size();
            }
        }
    }

    private void setPositions() {

        if (Game.getInstance().isPartida_local()) {

            this.calcularPosiciones();

            String comando = null;

            boolean doblar_ciegas = this.checkDoblarCiegas();

            try {
                comando = "DEALER#" + Base64.encodeBase64String(Game.getInstance().getJugadores().get(this.dealer_pos).getNickname().getBytes("UTF-8")) + (doblar_ciegas ? "#" + String.valueOf(Game.getInstance().getConta_tiempo_juego()) : "");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

            broadcastGAMECommandFromServer(comando, null);

            if (doblar_ciegas) {
                this.doblarCiegas();
            }

        } else {

            //Leemos el nick del dealer y calculamos posiciones nosotros en nuestro tablero
            String dealer = this.recibirDealer();

            this.dealer_pos = 0;

            while (this.dealer_pos < Game.getInstance().getJugadores().size()) {

                if (Game.getInstance().getJugadores().get(this.dealer_pos).getNickname().equals(dealer)) {
                    break;
                }

                this.dealer_pos++;
            }

            this.calcularPosiciones();

        }
    }

    public int getDealer_pos() {
        return dealer_pos;
    }

    public int getSmall_pos() {
        return small_pos;
    }

    public int getBig_pos() {
        return big_pos;
    }

    public int getUtg_pos() {
        return utg_pos;
    }

    private void colocarAvatares() {

        for (Player jugador : Game.getInstance().getJugadores()) {

            jugador.setAvatar();
        }
    }

    private void preservarAcciones() {

        String actions = "";

        for (String accion : this.acciones) {

            actions += accion + "@";
        }

        if (!"".equals(actions)) {

            try {
                Files.writeString(Paths.get(RECOVER_ACTIONS_FILE), actions.substring(0, actions.length() - 1));
            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void borrarAcciones() {

        acciones.clear();

        try {
            Files.deleteIfExists(Paths.get(RECOVER_ACTIONS_FILE + (Game.getInstance().isPartida_local() ? "" : "_" + Game.getInstance().getNick_local())));
        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void preservarDatosClavePartida() {

        try {

            //¡EL ORDEN ES IMPORTANTE!
            // SERVER_NICK + BUYIN + REBUY + TIEMPO_JUEGO + CONTA_MANO + CIEGA_PEQUEÑA + CIEGA_GRANDE + TIEMPO_CIEGAS + CIEGAS_DOBLADAS + DEALER
            String datos = Base64.encodeBase64String(WaitingRoom.getInstance().getServer_nick().getBytes("UTF-8")) + "#" + String.valueOf(Game.BUYIN) + "#" + String.valueOf(Game.REBUY) + "#" + String.valueOf(Game.getInstance().getConta_tiempo_juego()) + "#" + String.valueOf(this.mano) + "#" + Helpers.float2String(this.getCiega_pequeña()) + "#" + Helpers.float2String(this.getCiega_grande()) + "#" + String.valueOf(Game.CIEGAS_TIME) + "#" + String.valueOf(ciegas_double) + "#" + Base64.encodeBase64String(Game.getInstance().getJugadores().get(this.dealer_pos).getNickname().getBytes("UTF-8")) + "#";

            if (!auditor.isEmpty()) {
                for (Map.Entry<String, Float[]> entry : auditor.entrySet()) {

                    Player jugador = nick2player.get(entry.getKey());

                    if (jugador != null) {

                        datos += Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")) + "|" + Helpers.float2String(jugador.getStack() + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : jugador.getBote())) + "|" + String.valueOf(jugador.getBuyin()) + "@";

                    } else {

                        Float[] pasta = entry.getValue();

                        datos += Base64.encodeBase64String(entry.getKey().getBytes("UTF-8")) + "|" + Helpers.float2String(pasta[0]) + "|" + Helpers.float2String(pasta[1]) + "@";

                    }
                }
            } else {

                for (Player jugador : Game.getInstance().getJugadores()) {
                    datos += Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")) + "|" + Helpers.float2String(jugador.getStack() + (Helpers.float1DSecureCompare(0f, jugador.getPagar()) < 0 ? jugador.getPagar() : jugador.getBote())) + "|" + String.valueOf(jugador.getBuyin()) + "@";

                }

            }

            try {
                Files.writeString(Paths.get(RECOVER_BALANCE_FILE), datos.substring(0, datos.length() - 1));
            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void preservarSorteoSitios(String[] nicks) {

        try {
            String sitios = "";

            for (String nick : nicks) {

                sitios += Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#";

            }

            Files.writeString(Paths.get(Crupier.RECOVER_SEATS_FILE), sitios.substring(0, sitios.length() - 1));
        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private String[] recuperarSorteoSitios(String[] nicks_actuales) {

        if (Files.exists(Paths.get(Crupier.RECOVER_SEATS_FILE))) {

            try {

                ArrayList<String> actuales = new ArrayList<>();

                Collections.addAll(actuales, nicks_actuales);

                String[] sitiosb64 = Files.readString(Paths.get(Crupier.RECOVER_SEATS_FILE)).split("#");

                ArrayList<String> permutados = new ArrayList<>();

                for (String b64 : sitiosb64) {

                    String nick = new String(Base64.decodeBase64(b64), "UTF-8");

                    if (actuales.contains(nick)) {
                        permutados.add(nick);
                        actuales.remove(nick);
                    }
                }

                //Los nicks actuales nuevos los sentamos al final
                permutados.addAll(actuales);

                return permutados.toArray(new String[permutados.size()]);

            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        return null;
    }

    private void preservarPermutacion(Integer[] permutation, String filename) {

        try {
            String per = "";

            for (int p : permutation) {

                per += String.valueOf(p) + "|";
            }

            Files.writeString(Paths.get(filename), Base64.encodeBase64String(per.substring(0, per.length() - 1).getBytes("UTF-8")));
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Integer[] recuperarPermutacion(String filename) {

        if (Files.exists(Paths.get(filename))) {

            try {
                String datos = new String(Base64.decodeBase64(Files.readString(Paths.get(filename))), "UTF-8");

                String[] partes = datos.split("\\|");

                Integer[] permutacion = new Integer[partes.length];

                int i = 0;

                for (String p : partes) {
                    permutacion[i] = Integer.parseInt(p);
                    i++;
                }

                return permutacion;

            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        return null;
    }

    private Object[] siguienteAccionRecuperada(String nick) {

        while (!this.acciones_recuperadas.isEmpty()) {
            try {
                String accion = this.acciones_recuperadas.poll();

                String[] accion_partes = accion.split("#");

                String name = new String(Base64.decodeBase64(accion_partes[0]), "UTF-8");

                if (name.equals(nick)) {

                    Object[] res = new Object[2];

                    res[0] = Integer.parseInt(accion_partes[1]);

                    if ((int) res[0] == Player.BET) {
                        res[1] = Float.parseFloat(accion_partes[2]);
                    } else {
                        res[1] = 0f;
                    }

                    if (this.acciones_recuperadas.isEmpty()) {
                        if (recover_dialog != null) {

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    recover_dialog.setVisible(false);
                                    recover_dialog.dispose();
                                    recover_dialog = null;
                                    Game.getInstance().getFull_screen_menu().setEnabled(true);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                }
                            });
                        }

                        this.setSincronizando_mano(false);

                        Game.getInstance().getRegistro().print("TIMBA RECUPERADA");
                        Helpers.playWavResource("misc/cash_register.wav");
                    }

                    return res;
                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (recover_dialog != null) {

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    recover_dialog.setVisible(false);
                    recover_dialog.dispose();
                    recover_dialog = null;
                    Game.getInstance().getFull_screen_menu().setEnabled(true);
                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                }
            });
        }

        this.setSincronizando_mano(false);

        Game.getInstance().getRegistro().print("TIMBA RECUPERADA");
        Helpers.playWavResource("misc/cash_register.wav");

        return null;

    }

    private void recuperarAccionesLocales() {

        if (Files.exists(Paths.get(Crupier.RECOVER_ACTIONS_FILE + (Game.getInstance().isPartida_local() ? "" : "_" + Game.getInstance().getNick_local())))) {

            try {
                String datos = Files.readString(Paths.get(Crupier.RECOVER_ACTIONS_FILE + (Game.getInstance().isPartida_local() ? "" : "_" + Game.getInstance().getNick_local())));

                String[] rec = datos.split("@");

                for (String r : rec) {

                    String[] parts = r.split("#");

                    String nick = new String(Base64.decodeBase64(parts[0]), "UTF-8");

                    if (Game.getInstance().getLocalPlayer().getNickname().equals(nick) || (Game.getInstance().isPartida_local() && Game.getInstance().getParticipantes().get(nick).isCpu())) {
                        acciones_recuperadas.add(r);
                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    private String[] sortearSitios() {

        String[] permutados = null;

        Integer[] permutacion = null;

        if (Game.getInstance().isPartida_local()) {

            String[] nicks = new String[Game.getInstance().getParticipantes().size()];

            int i = 0;

            for (Map.Entry<String, Participant> entry : Game.getInstance().getParticipantes().entrySet()) {

                nicks[i++] = entry.getKey();
            }

            if (!Game.isRECOVER() || (permutados = this.recuperarSorteoSitios(nicks)) == null) {

                permutacion = Helpers.getIntegerPermutation(Helpers.SPRNG, Game.getInstance().getParticipantes().size());

                permutados = new String[nicks.length];

                i = 0;

                for (int p : permutacion) {

                    permutados[i++] = nicks[p - 1];
                }
            }

            preservarSorteoSitios(permutados);

            //Comunicamos a todos los participantes el sorteo
            String command = "SEATS#" + String.valueOf(permutados.length);

            for (String nick : permutados) {

                try {
                    command += "#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            this.broadcastGAMECommandFromServer(command, null);

            return permutados;

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

                    if (Game.getInstance().checkPause()) {
                        start_time = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

        Game.getInstance().getRegistro().print(sitios);

        return permutados;
    }

    public float getCiega_grande() {
        return ciega_grande;
    }

    public float getCiega_pequeña() {
        return ciega_pequeña;
    }

    public void flop() {

        Game.getInstance().getFlop1().destapar();

        Game.getInstance().getFlop1().checkSpecialCardSound();

        Helpers.pausar(1000);

        Game.getInstance().getFlop2().destapar();

        Game.getInstance().getFlop2().checkSpecialCardSound();

        Helpers.pausar(1000);

        Game.getInstance().getFlop3().destapar();

        Game.getInstance().getFlop3().checkSpecialCardSound();

        ArrayList<Card> flop = new ArrayList<>();

        flop.add(Game.getInstance().getFlop1());

        flop.add(Game.getInstance().getFlop2());

        flop.add(Game.getInstance().getFlop3());

        Game.getInstance().getRegistro().print("FLOP ->" + Card.collection2String(flop));
    }

    public void turn() {

        Game.getInstance().getTurn().destapar();

        Game.getInstance().getTurn().checkSpecialCardSound();

        Game.getInstance().getRegistro().print("TURN -> " + Game.getInstance().getTurn());
    }

    public void river() {

        Game.getInstance().getRiver().destapar();

        Game.getInstance().getRiver().checkSpecialCardSound();

        Game.getInstance().getRegistro().print("RIVER -> " + Game.getInstance().getRiver());
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

                            if (!jugador.getNickname().equals(Game.getInstance().getNick_local()) && !jugador.isExit()) {

                                String[] suscartas = cards.get(jugador.getNickname());

                                String[] carta1 = suscartas[0].split("_");

                                String[] carta2 = suscartas[1].split("_");

                                jugador.getPlayingCard1().cargarCarta(carta1[0], carta1[1]);

                                jugador.getPlayingCard2().cargarCarta(carta2[0], carta2[1]);

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

                if (Game.getInstance().checkPause()) {
                    start_time = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - start_time > Game.CLIENT_RECEPTION_TIMEOUT) {

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

            if (Game.getInstance().isPartida_local()) {

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
                    Helpers.playWavResource("misc/uncover.wav");

                    //Destapamos las cartas de los jugadores involucrados
                    for (Player jugador : resisten) {

                        if (jugador != Game.getInstance().getLocalPlayer() && !jugador.isExit()) {
                            jugador.getPlayingCard1().destapar(false);
                            jugador.getPlayingCard2().destapar(false);
                        }
                    }
                }

                this.cartas_resistencia = true;

            } else {

                //Recibimos las cartas de los jugadores involucrados en el bote_total (ignoramos las nuestras que ya las sabemos)
                recibirCartasResistencia(resisten);

                if (destapar) {
                    Helpers.playWavResource("misc/uncover.wav");

                    //Destapamos las cartas de los jugadores involucrados
                    for (Player jugador : resisten) {

                        if (jugador != Game.getInstance().getLocalPlayer() && !jugador.isExit()) {
                            jugador.getPlayingCard1().destapar(false);
                            jugador.getPlayingCard2().destapar(false);
                        }
                    }
                }

                this.cartas_resistencia = true;
            }
        }
    }

    private synchronized void updateSpectatorBots() {

        if (Game.getInstance().isPartida_local()) {

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (jugador != Game.getInstance().getLocalPlayer() && !jugador.isExit() && jugador.isSpectator() && Game.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                    this.remotePlayerQuit(jugador.getNickname());
                }
            }
        }
    }

    private synchronized void updateExitPlayers() {

        int exit = 0;

        for (Player jugador : Game.getInstance().getJugadores()) {
            if (jugador.isExit()) {
                this.auditor.put(jugador.getNickname(), new Float[]{jugador.getStack() + jugador.getPagar(), (float) jugador.getBuyin()});

                float ganancia = Helpers.clean1DFloat(Helpers.clean1DFloat(jugador.getStack()) + Helpers.clean1DFloat(jugador.getPagar())) - Helpers.clean1DFloat(jugador.getBuyin());

                String ganancia_msg = "";

                if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                    ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                    ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                } else {
                    ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                }

                Game.getInstance().getRegistro().print(jugador.getNickname() + " " + Translator.translate("ABANDONA LA TIMBA") + " -> " + ganancia_msg);

                exit++;
            }
        }

        if (exit > 0) {
            Game.getInstance().refreshTapete();

            nick2player.clear();

            for (Player jugador : Game.getInstance().getJugadores()) {
                nick2player.put(jugador.getNickname(), jugador);
            }

        }

    }

    public boolean ganaPorUltimaCarta(Player jugador, Hand jugada, int MIN) {

        if (!Game.getInstance().getRiver().isTapada() && jugada.getWinners().contains(Game.getInstance().getRiver()) && jugada.getVal() >= MIN && (jugada.getWinners().contains(jugador.getPlayingCard1()) || jugada.getWinners().contains(jugador.getPlayingCard2()))) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(Game.getInstance().getCartas_comunes()));
            cartas.add(jugador.getPlayingCard1());
            cartas.add(jugador.getPlayingCard2());
            cartas.remove(Game.getInstance().getRiver());

            Hand nueva_jugada = new Hand(cartas);

            return (nueva_jugada.getVal() != jugada.getVal());
        }

        return false;
    }

    public boolean badbeat(Player perdedor, Player ganador) {

        if (ganador != null) {

            ArrayList<Card> cartas = new ArrayList<>(Arrays.asList(Game.getInstance().getCartas_comunes()));

            cartas.add(perdedor.getPlayingCard1());

            cartas.add(perdedor.getPlayingCard2());

            cartas.remove(Game.getInstance().getRiver());

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

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setMaximum(tiempo);
                Game.getInstance().getBarra_tiempo().setValue(tiempo);
            }
        });

        this.setTiempo_pausa(tiempo);

        while (this.getTiempo_pausa() > 0) {

            Helpers.pausar(1000);

            if (!Game.getInstance().isTimba_pausada() && !isFin_de_la_transmision()) {

                this.decrementPausaBarra();

                final int val = this.getTiempo_pausa();

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        Game.getInstance().getBarra_tiempo().setValue(val);
                    }
                });
            }

        }

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setValue(tiempo);
            }
        });
    }

    public void showdown(HashMap<Player, Hand> perdedores, HashMap<Player, Hand> ganadores) {

        int pivote;

        if (this.last_aggressor != null) {

            pivote = 0;

            for (Player jugador : Game.getInstance().getJugadores()) {

                if (jugador == this.last_aggressor) {
                    break;
                }

                pivote++;
            }

        } else {

            pivote = (this.getDealer_pos() + 1) % Game.getInstance().getJugadores().size();
        }

        boolean hay_ganador = false;

        int pos = pivote;

        do {

            Player jugador_actual = Game.getInstance().getJugadores().get(pos);

            if (perdedores.containsKey(jugador_actual) || ganadores.containsKey(jugador_actual)) {

                if (hay_ganador) {

                    if (ganadores.containsKey(jugador_actual)) {

                        jugador_actual.getPlayingCard1().destapar(false);

                        jugador_actual.getPlayingCard2().destapar(false);

                        Hand jugada = ganadores.get(jugador_actual);

                        jugador_actual.setWinner(jugada.getName());

                        if (Game.SONIDOS_CHORRA && jugador_actual == Game.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Helpers.playWavResource("winner/orgasmo.wav");
                            } else {
                                this.soundWinner(jugada.getVal(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                            }
                        }

                    } else {

                        Hand jugada = perdedores.get(jugador_actual);

                        if (jugador_actual == Game.getInstance().getLocalPlayer()) {

                            jugador_actual.setLoser(jugada.getName());

                            if (!this.destapar_resistencia) {
                                Game.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                            } else {
                                Game.getInstance().getLocalPlayer().setMuestra(true);
                            }

                        } else {

                            if (jugador_actual.getPlayingCard1().isTapada()) {

                                jugador_actual.setLoser(Translator.translate("PIERDE"));

                            } else {

                                jugador_actual.setLoser(jugada.getName());
                            }
                        }

                        if (Game.SONIDOS_CHORRA && jugador_actual == Game.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("loser/", new String[]{
                                    "encargado.wav",
                                    "matias.wav"});

                                Helpers.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));
                            } else {

                                this.soundLoser(jugada.getVal());
                            }
                        }
                    }

                } else {

                    if (ganadores.containsKey(jugador_actual)) {

                        jugador_actual.getPlayingCard1().destapar(false);

                        jugador_actual.getPlayingCard2().destapar(false);

                        Hand jugada = ganadores.get(jugador_actual);

                        jugador_actual.setWinner(jugada.getName());

                        if (Game.SONIDOS_CHORRA && jugador_actual == Game.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Helpers.playWavResource("winner/orgasmo.wav");
                            } else {
                                this.soundWinner(jugada.getVal(), ganaPorUltimaCarta(jugador_actual, jugada, Crupier.MIN_ULTIMA_CARTA_JUGADA));
                            }
                        }

                        hay_ganador = true;

                    } else {

                        ArrayList<Card> cartas = new ArrayList<>();

                        cartas.add(jugador_actual.getPlayingCard1());

                        cartas.add(jugador_actual.getPlayingCard2());

                        jugador_actual.getPlayingCard1().destapar(false);

                        jugador_actual.getPlayingCard2().destapar(false);

                        Hand jugada = perdedores.get(jugador_actual);

                        jugador_actual.setLoser(jugada.getName());

                        if (jugador_actual == Game.getInstance().getLocalPlayer()) {
                            Game.getInstance().getLocalPlayer().setMuestra(true);
                        }

                        if (Game.SONIDOS_CHORRA && jugador_actual == Game.getInstance().getLocalPlayer()) {

                            if (jugador_actual.getDecision() == Player.ALLIN) {
                                Map.Entry<String, String[]> WTF_SOUNDS = new HashMap.SimpleEntry<String, String[]>("loser/", new String[]{
                                    "encargado.wav",
                                    "matias.wav"});

                                Helpers.playRandomWavResource(Map.ofEntries(WTF_SOUNDS));
                            } else {

                                this.soundLoser(jugada.getVal());
                            }
                        }

                    }
                }

            }

            pos = (pos + 1) % Game.getInstance().getJugadores().size();

        } while (pos != pivote);

    }

    @Override
    public void run() {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);
                Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
            }
        });

        if (Game.getInstance().isPartida_local()) {

            broadcastGAMECommandFromServer("INIT#" + String.valueOf(Game.BUYIN) + "#" + String.valueOf(Game.CIEGA_PEQUEÑA) + "#" + String.valueOf(Game.CIEGA_GRANDE) + "#" + String.valueOf(Game.CIEGAS_TIME) + "#" + String.valueOf(Game.isRECOVER()) + "#" + String.valueOf(Game.REBUY), null);

        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                Game.getInstance().getSala_espera().getStatus().setText(Translator.translate("Sorteando sitios..."));
            }
        });

        this.nicks_permutados = sortearSitios();

        sentarParticipantes();

        //ESTE MAPA HAY QUE CARGARLO UNA VEZ TENEMOS A LOS JUGADORES EN SUS SITIOS
        for (Player jugador : Game.getInstance().getJugadores()) {
            nick2player.put(jugador.getNickname(), jugador);
        }

        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                Game.getInstance().getSala_espera().getStatus().setText(Translator.translate("Preparando mesa..."));
            }
        });

        if (!Game.TEST_MODE || Game.getInstance().isPartida_local()) {

            if (Game.getZoom_level() != 0) {
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {

                        Game.getInstance().getSala_espera().getStatus().setText(Translator.translate("Restaurando zoom..."));
                    }
                });
                Game.getInstance().zoom(1f + Game.getZoom_level() * Game.ZOOM_STEP);

            }

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    Game.getInstance().getSala_espera().getStatus().setText(Translator.translate("Timba en curso"));
                }
            });

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    Game.getInstance().getSala_espera().setVisible(false);
                }
            });

            Game.getInstance().autoZoomFullScreen();
        }

        while (!fin_de_la_transmision) {

            if (getJugadoresActivos() > 1 && !Game.getInstance().getLocalPlayer().isExit()) {

                if (this.NUEVA_MANO()) {

                    auditorCuentas();

                    Game.getInstance().getRegistro().print(Game.getInstance().getJugadores().get(this.big_pos).getNickname() + Translator.translate(" es la CIEGA GRANDE (") + Helpers.float2String(this.ciega_grande) + ") / " + Game.getInstance().getJugadores().get(this.small_pos).getNickname() + Translator.translate(" es la CIEGA PEQUEÑA (") + Helpers.float2String(this.ciega_pequeña) + ") / " + Game.getInstance().getJugadores().get(this.dealer_pos).getNickname() + Translator.translate(" es el DEALER"));

                    ArrayList<Player> resisten = this.rondaApuestas(PREFLOP, new ArrayList<>(Game.getInstance().getJugadores()));

                    Game.getInstance().hideTapeteApuestas();

                    Game.getInstance().getLocalPlayer().desactivarControles();

                    if (Game.AUTO_ACTION_BUTTONS) {
                        Game.getInstance().getLocalPlayer().desActivarPreBotones();
                    }

                    this.show_time = true;

                    HashMap<Player, Hand> jugadas = null;

                    HashMap<Player, Hand> ganadores = null;

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

                        if (resisten.size() == 1) {

                            //Todos se han tirado menos uno GANA SIN MOSTRAR
                            resisten.get(0).setWinner(resisten.contains(Game.getInstance().getLocalPlayer()) ? Translator.translate("GANAS SIN MOSTRAR") : Translator.translate("GANA SIN MOSTRAR"));

                            resisten.get(0).pagar(this.bote.getTotal() + this.bote_sobrante);

                            Game.getInstance().getRegistro().print(resisten.get(0).getNickname() + Translator.translate(" GANA BOTE (") + Helpers.float2String(this.bote.getTotal()) + Translator.translate(") SIN TENER QUE MOSTRAR"));

                            Game.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);

                            Game.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.GREEN);

                            Game.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);

                            this.bote_total = 0f;

                            this.bote_sobrante = 0f;

                            if (resisten.get(0) == Game.getInstance().getLocalPlayer()) {
                                Game.getInstance().getLocalPlayer().activar_boton_mostrar(false);
                            }

                            if (resisten.get(0) == Game.getInstance().getLocalPlayer()) {

                                this.soundWinner(0, false);
                            }

                        } else {

                            procesarCartasResistencia(resisten, false);

                            Helpers.pausar(Game.PAUSA_ANTES_DE_SHOWDOWN * 1000);

                            if (this.bote.getSidePot() == null) {

                                //NO HAY BOTES DERIVADOS
                                jugadas = this.calcularJugadas(resisten);

                                ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

                                float[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal(), ganadores.size());

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

                                    if (ganadores.size() == 1) {
                                        ganador.pagar(cantidad_pagar_ganador[0] + this.bote_sobrante);
                                        this.bote_sobrante = 0f;
                                    } else {
                                        ganador.pagar(cantidad_pagar_ganador[0]);
                                    }

                                    this.bote_total -= cantidad_pagar_ganador[0];

                                    ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                    Game.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                    unganador = ganador;

                                    jugada_ganadora = jugada.getVal();
                                }

                                for (Card carta : Game.getInstance().getCartas_comunes()) {
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

                                    Game.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");

                                }

                                this.showdown(jugadas, ganadores);

                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);
                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.GREEN);
                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.BLACK);

                            } else {

                                //Vamos a ver los ganadores de cada bote_total
                                String bote_tapete = Helpers.float2String(this.bote.getTotal());

                                jugadas = this.calcularJugadas(resisten);

                                ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

                                float[] cantidad_pagar_ganador = this.calcularBoteParaGanador(this.bote.getTotal(), ganadores.size());

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

                                    if (ganadores.size() == 1) {
                                        ganador.pagar(cantidad_pagar_ganador[0] + this.bote_sobrante);
                                        this.bote_sobrante = 0f;
                                    } else {
                                        ganador.pagar(cantidad_pagar_ganador[0]);
                                    }

                                    this.bote_total -= cantidad_pagar_ganador[0];

                                    ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                    cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                    Game.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE PRINCIPAL (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                    unganador = ganador;

                                    jugada_ganadora = jugada.getVal();
                                }

                                for (Card carta : Game.getInstance().getCartas_comunes()) {
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

                                    Game.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE PRINCIPAL (") + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                }

                                this.showdown(jugadas, ganadores);

                                Pot current = this.bote.getSidePot();

                                int conta_bote_secundario = 1;

                                while (current != null) {

                                    float pagar = current.getTotal();

                                    bote_tapete = bote_tapete + " + " + String.valueOf(conta_bote_secundario) + "@" + Helpers.float2String(current.getTotal());

                                    if (current.getPlayers().size() == 1) {

                                        current.getPlayers().get(0).pagar(pagar);

                                        this.bote_total -= pagar;

                                        current.getPlayers().get(0).setBoteSecundario("(+" + String.valueOf(conta_bote_secundario) + ")");

                                        Game.getInstance().getRegistro().print(current.getPlayers().get(0).getNickname() + Translator.translate(" RECUPERA BOTE (SOBRANTE) SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(pagar) + ")");

                                    } else {

                                        jugadas = this.calcularJugadas(current.getPlayers());

                                        ganadores = this.calcularGanadores(new HashMap<Player, Hand>(jugadas));

                                        cantidad_pagar_ganador = this.calcularBoteParaGanador(pagar, ganadores.size());

                                        for (Map.Entry<Player, Hand> entry : ganadores.entrySet()) {

                                            Player ganador = entry.getKey();

                                            jugadas.remove(entry.getKey());

                                            ganador.pagar(cantidad_pagar_ganador[0]);

                                            this.bote_total -= cantidad_pagar_ganador[0];

                                            Hand jugada = entry.getValue();

                                            ganador.setBoteSecundario("(+" + String.valueOf(conta_bote_secundario) + ")");

                                            ArrayList<Card> cartas_repartidas_jugador = new ArrayList<>();

                                            cartas_repartidas_jugador.add(ganador.getPlayingCard1());

                                            cartas_repartidas_jugador.add(ganador.getPlayingCard2());

                                            Game.getInstance().getRegistro().print(ganador.getNickname() + " (" + Card.collection2String(cartas_repartidas_jugador) + Translator.translate(") GANA BOTE SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ") -> " + jugada);

                                        }

                                        for (Map.Entry<Player, Hand> entry : jugadas.entrySet()) {

                                            Player perdedor = entry.getKey();

                                            perdedor.getPlayingCard1().desenfocar();

                                            perdedor.getPlayingCard2().desenfocar();

                                            perdedores.put(perdedor, entry.getValue());

                                            Game.getInstance().getRegistro().print(perdedor.getNickname() + Translator.translate(" (---) PIERDE BOTE SECUNDARIO #") + String.valueOf(conta_bote_secundario) + " (" + Helpers.float2String(cantidad_pagar_ganador[0]) + ")");
                                        }

                                    }

                                    current = current.getSidePot();

                                    conta_bote_secundario++;

                                }

                                Game.getInstance().getTapete().getCommunityCards().getBet_label().setVisible(false);
                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setOpaque(true);
                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setBackground(Color.BLACK);
                                Game.getInstance().getTapete().getCommunityCards().getPot_label().setForeground(Color.WHITE);
                                Game.getInstance().setTapeteBote(bote_tapete);
                            }

                        }

                        if (!Game.TEST_MODE && !resisten.contains(Game.getInstance().getLocalPlayer())) {

                            if (Game.getInstance().getLocalPlayer().isActivo() && Game.getInstance().getLocalPlayer().getParguela_counter() > 0) {

                                Game.getInstance().getLocalPlayer().activar_boton_mostrar(true);
                            }

                            this.soundShowdown();

                        }

                        if (Helpers.float1DSecureCompare(0f, this.bote_total) < 0) {
                            this.bote_sobrante += this.bote_total;
                            this.bote_total = 0f;
                        }

                        for (Player jugador : Game.getInstance().getJugadores()) {
                            jugador.resetBote();
                        }

                    }

                    if (!Game.TEST_MODE) {

                        if (getJugadoresActivos() > 1 && !Game.getInstance().getLocalPlayer().isExit()) {

                            this.pausaConBarra(Game.PAUSA_ENTRE_MANOS);
                        }

                        synchronized (lock_mostrar) {
                            this.show_time = false;
                        }

                        Game.getInstance().getLocalPlayer().desactivar_boton_mostrar();

                        Game.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);

                        ArrayList<String> rebuy_players = new ArrayList<>();

                        for (Player jugador : Game.getInstance().getJugadores()) {

                            if (jugador != Game.getInstance().getLocalPlayer() && jugador.isActivo() && Helpers.float1DSecureCompare(0f, Helpers.clean1DFloat(jugador.getStack()) + Helpers.clean1DFloat(jugador.getPagar())) == 0) {

                                if (Game.REBUY) {
                                    rebuy_players.add(jugador.getNickname());
                                } else {
                                    jugador.setSpectator(null);
                                }

                            }
                        }

                        this.rebuy_time = !rebuy_players.isEmpty();

                        if (Game.getInstance().getLocalPlayer().isActivo() && Helpers.float1DSecureCompare(Helpers.clean1DFloat(Game.getInstance().getLocalPlayer().getStack()) + Helpers.clean1DFloat(Game.getInstance().getLocalPlayer().getPagar()), 0f) == 0) {

                            this.rebuy_time = true;

                            if (Game.REBUY) {

                                if (!Game.AUTO_REBUY) {

                                    GameOverDialog dialog = new GameOverDialog(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), true);

                                    Game.getInstance().setGame_over_dialog(true);

                                    Helpers.GUIRunAndWait(new Runnable() {
                                        public void run() {
                                            dialog.setLocationRelativeTo(dialog.getParent());

                                            dialog.setVisible(true);
                                        }
                                    });

                                    Game.getInstance().setGame_over_dialog(false);

                                    if (dialog.isContinua()) {

                                        try {

                                            rebuy_players.remove(Game.getInstance().getLocalPlayer().getNickname());

                                            String comando = "REBUY#" + Base64.encodeBase64String(Game.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"));

                                            if (Game.getInstance().isPartida_local()) {
                                                this.broadcastGAMECommandFromServer(comando, null);
                                            } else {
                                                this.sendGAMECommandToServer(comando);
                                            }
                                        } catch (UnsupportedEncodingException ex) {
                                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    } else {
                                        try {

                                            rebuy_players.remove(Game.getInstance().getLocalPlayer().getNickname());

                                            String comando = "REBUY#" + Base64.encodeBase64String(Game.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8")) + "#0";

                                            if (Game.getInstance().isPartida_local()) {
                                                this.broadcastGAMECommandFromServer(comando, null);
                                            } else {
                                                this.sendGAMECommandToServer(comando);
                                            }
                                        } catch (UnsupportedEncodingException ex) {
                                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                        }

                                        Game.getInstance().getLocalPlayer().setSpectator(null);

                                        Game.getInstance().getRegistro().print(Game.getInstance().getLocalPlayer().getNickname() + Translator.translate(" -> TE QUEDAS DE ESPECTADOR"));
                                    }

                                } else {

                                    try {

                                        rebuy_players.remove(Game.getInstance().getLocalPlayer().getNickname());

                                        String comando = "REBUY#" + Base64.encodeBase64String(Game.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8"));

                                        if (Game.getInstance().isPartida_local()) {
                                            this.broadcastGAMECommandFromServer(comando, null);
                                        } else {
                                            this.sendGAMECommandToServer(comando);
                                        }
                                    } catch (UnsupportedEncodingException ex) {
                                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }

                            } else {

                                GameOverDialog dialog = new GameOverDialog(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), true, true);

                                Game.getInstance().setGame_over_dialog(true);

                                Helpers.GUIRunAndWait(new Runnable() {
                                    public void run() {
                                        dialog.setLocationRelativeTo(dialog.getParent());
                                        dialog.setVisible(true);
                                    }
                                });

                                Game.getInstance().setGame_over_dialog(false);

                                Game.getInstance().getLocalPlayer().setSpectator(null);

                                Game.getInstance().getRegistro().print(Game.getInstance().getLocalPlayer().getNickname() + Translator.translate(" -> TE QUEDAS DE ESPECTADOR"));
                            }

                        }

                        if (!rebuy_players.isEmpty()) {

                            //Enviamos los REBUYS de los bots
                            if (Game.getInstance().isPartida_local()) {

                                for (Player jugador : Game.getInstance().getJugadores()) {

                                    if (rebuy_players.contains(jugador.getNickname()) && Game.getInstance().getParticipantes().get(jugador.getNickname()).isCpu()) {

                                        int res = Helpers.mostrarMensajeInformativoSINO(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), Translator.translate("¿RECOMPRA? -> ") + jugador.getNickname());

                                        rebuy_players.remove(jugador.getNickname());

                                        try {
                                            String comando = "REBUY#" + Base64.encodeBase64String(jugador.getNickname().getBytes("UTF-8")) + ((!Game.REBUY || res != 0) ? "#0" : "");

                                            this.broadcastGAMECommandFromServer(comando, null);

                                        } catch (UnsupportedEncodingException ex) {
                                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                                        }

                                        if (res != 0) {
                                            jugador.setSpectator(null);
                                        }

                                    }
                                }
                            }

                            this.recibirRebuys(rebuy_players);
                        }

                        this.rebuy_time = false;

                        updateSpectatorBots();

                        updateExitPlayers();

                    } else {

                        this.pausaConBarra(Game.PAUSA_ENTRE_MANOS_TEST);

                        synchronized (lock_mostrar) {
                            this.show_time = false;
                        }

                        Game.getInstance().getLocalPlayer().desactivar_boton_mostrar();

                        Game.getInstance().getRegistro().actualizarCartasPerdedores(perdedores);
                    }
                }

            } else {

                if (!Game.getInstance().getLocalPlayer().isSpectator() && Helpers.float1DSecureCompare(0f, this.bote_sobrante) < 0) {

                    Game.getInstance().getLocalPlayer().pagar(this.bote_sobrante);

                    this.bote_sobrante = 0f;
                }

                for (Card carta : Game.getInstance().getCartas_comunes()) {
                    carta.cargarCarta();
                }

                Game.getInstance().getTiempo_juego().stop();

                Game.getInstance().getRegistro().print("LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)");

                Helpers.mostrarMensajeInformativo(Game.getInstance(), "LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)");

                fin_de_la_transmision = true;
            }

        }

        if (!fin_de_la_transmision && !Game.getInstance().isPartida_local()) {
            sendGAMECommandToServer("EXIT", false);
        }

        Game.getInstance().finTransmision(fin_de_la_transmision);

    }

    public HashMap<Player, Hand> calcularJugadas(ArrayList<Player> jugadores) {

        HashMap<Player, Hand> jugadas = new HashMap<>();

        for (Player jugador : jugadores) {

            ArrayList<Card> cartas_utilizables = new ArrayList<>(Arrays.asList(Game.getInstance().getCartas_comunes()));

            cartas_utilizables.add(jugador.getPlayingCard1());

            cartas_utilizables.add(jugador.getPlayingCard2());

            jugadas.put(jugador, new Hand(cartas_utilizables));
        }

        return jugadas;
    }

    public HashMap<Player, Hand> calcularGanadores(HashMap<Player, Hand> candidatos) {

        int jugada_max = CARTA_ALTA;

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
                case ESCALERA_COLOR:
                case ESCALERA:
                    return desempatarEscalera(candidatos);
                case POKER:
                    return desempatarRepetidas(candidatos, CARTAS_POKER);
                case FULL:
                    return desempatarFull(candidatos);
                case COLOR:
                    return desempatarCartaAlta(candidatos, 0);
                case TRIO:
                    return desempatarRepetidas(candidatos, CARTAS_TRIO);
                case DOBLE_PAREJA:
                    return desempatarDoblePareja(candidatos);
                case PAREJA:
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

            //Nos cargamos todos los que tengan un trío con carta más pequeña
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
