/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.Helpers.TapetePopupMenu.BARAJAS_MENU;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 *
 * @author tonikelope
 */
public final class Game extends javax.swing.JFrame implements ZoomableInterface {

    public static final boolean DEBUG_TO_FILE = true;
    public static final boolean TEST_MODE = false;
    public static final int TEST_MODE_PAUSE = 250;
    public static final int DEFAULT_ZOOM_LEVEL = -2;
    public static final float MIN_BIG_BLIND = 0.20f;
    public static final float ZOOM_STEP = 0.05f;
    public static volatile float CIEGA_PEQUEÑA = 0.10f;
    public static volatile float CIEGA_GRANDE = 0.20f;
    public static volatile int BUYIN = 10;
    public static volatile int CIEGAS_TIME = 60;
    public static volatile boolean REBUY = true;
    public static final int PAUSA_ENTRE_MANOS = 7; //Segundos
    public static final int PAUSA_ENTRE_MANOS_TEST = 1;
    public static final int PAUSA_ANTES_DE_SHOWDOWN = 1; //Segundos
    public static final int TIEMPO_PENSAR = 30; //Segundos
    public static final int WAIT_QUEUES = 1000;
    public static final int WAIT_PAUSE = 1000;
    public static final int CLIENT_RECEPTION_TIMEOUT = 10000;
    public static final int CONFIRMATION_TIMEOUT = 10000;
    public static final int CLIENT_RECON_TIMEOUT = 2 * TIEMPO_PENSAR * 1000; // Tiempo en milisegundos que esperaremos cliente que perdió la conexión antes (preguntar) si echarle de la timba
    public static final int CLIENT_RECON_ERROR_PAUSE = 5000;
    public static final int REBUY_TIMEOUT = 25000;
    public static final int MAX_TIMEOUT_CONFIRMATION_ERROR = 10;
    public static final String BARAJA_DEFAULT = "coronapoker";
    public static final String DEFAULT_LANGUAGE = "es";
    public static final int PEPILLO_COUNTER_MAX = 5;
    public static final int AUTO_ZOOM_TIMEOUT = 2000;
    public static final int GUI_ZOOM_WAIT = 250;

    public static volatile boolean SONIDOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos", "true")) && !TEST_MODE;
    public static volatile boolean SONIDOS_CHORRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_chorra", "true"));
    public static volatile boolean MUSICA_AMBIENTAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ascensor", "true"));
    public static volatile boolean AUTO_REBUY = false;
    public static volatile boolean SHOW_CLOCK = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("show_time", "false"));
    public static volatile boolean CONFIRM_ACTIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("confirmar_todo", "false")) && !TEST_MODE;
    public static volatile int ZOOM_LEVEL = Integer.parseInt(Helpers.PROPERTIES.getProperty("zoom_level", String.valueOf(Game.DEFAULT_ZOOM_LEVEL)));
    public static volatile String BARAJA = Helpers.PROPERTIES.getProperty("baraja", BARAJA_DEFAULT);
    public static volatile boolean VISTA_COMPACTA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("vista_compacta", "false")) && !TEST_MODE;
    public static volatile boolean ANIMACION_REPARTIR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_reparto", "true"));
    public static volatile boolean AUTO_ACTION_BUTTONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_buttons", "false")) && !TEST_MODE;
    public static volatile String COLOR_TAPETE = Helpers.PROPERTIES.getProperty("color_tapete", "verde");
    public static volatile String LANGUAGE = Helpers.PROPERTIES.getProperty("lenguaje", "es");
    public static volatile boolean CINEMATICAS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas", "true"));
    public static volatile boolean RECOVER = false;
    private static volatile Game THIS = null;

    public static Game getInstance() {
        return THIS;
    }

    private ZoomableInterface[] zoomeables;
    private Card[] cartas_comunes;
    private ArrayList<Player> jugadores;
    private Map<String, Participant> participantes;
    private volatile GameLogDialog registro_dialog = null;
    private final int contador_manos = 0;
    private final float acumulador_bote = 0f;
    private final float acumulador_apuestas = 0f;
    private Crupier crupier;
    private boolean partida_local;
    private String nick_local;
    private volatile Timer tiempo_juego;
    private volatile long conta_tiempo_juego = 0L;
    private volatile boolean full_screen = false;
    private final HashMap<KeyStroke, Action> actionMap = new HashMap<>();
    private volatile boolean timba_pausada = false;
    private volatile PauseDialog pausa_dialog = null;
    private final Object lock_pause = new Object();
    private volatile boolean game_over_dialog = false;
    private volatile JFrame full_screen_frame = null;
    private volatile Window full_screen_window = null;
    private volatile AboutDialog about_dialog = null;
    private volatile HandGeneratorDialog jugadas_dialog = null;
    private final Object registro_lock = new Object();
    private final Object full_screen_lock = new Object();

    private TablePanel tapete = null;

    public void autoZoomFullScreen() {

        Helpers.threadRun(new Runnable() {

            public void run() {

                int t;

                if (!Helpers.OSValidator.isMac()) {

                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {
                            zoom_menu_in.setEnabled(false);
                            zoom_menu_out.setEnabled(false);
                            zoom_menu_reset.setEnabled(false);
                            full_screen_menu.doClick();
                        }
                    });

                    t = 0;

                    while (t < AUTO_ZOOM_TIMEOUT && !full_screen) {

                        synchronized (full_screen_lock) {
                            try {
                                full_screen_lock.wait(1000);
                                t += 1000;
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                } else {

                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {

                            setExtendedState(JFrame.MAXIMIZED_BOTH);
                            setVisible(true);

                        }
                    });
                }

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        full_screen_menu.setEnabled(false);
                        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(false);
                    }
                });

                if (full_screen) {

                    double frameHeight = tapete.getHeight();

                    double frameWidth = tapete.getWidth();

                    t = 0;

                    while (t < AUTO_ZOOM_TIMEOUT && frameWidth == tapete.getWidth() && frameHeight == tapete.getHeight()) {
                        Helpers.pausar(GUI_ZOOM_WAIT);
                        t += GUI_ZOOM_WAIT;
                    }

                }

                double playerBottom = getLocalPlayer().getLocationOnScreen().getY() + getLocalPlayer().getHeight();

                double tapeteBottom = tapete.getLocationOnScreen().getY() + tapete.getHeight();

                t = 0;

                while (t < AUTO_ZOOM_TIMEOUT && playerBottom > tapeteBottom) {

                    double playerHeight = getLocalPlayer().getHeight();

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {
                            zoom_menu_out.setEnabled(true);
                            zoom_menu_out.doClick();
                            zoom_menu_out.setEnabled(false);
                        }
                    });

                    t = 0;

                    while (t < AUTO_ZOOM_TIMEOUT && playerHeight == getLocalPlayer().getHeight()) {

                        Helpers.pausar(GUI_ZOOM_WAIT);
                        t += GUI_ZOOM_WAIT;
                    }

                    if (playerHeight != getLocalPlayer().getHeight()) {
                        playerBottom = getLocalPlayer().getLocationOnScreen().getY() + getLocalPlayer().getHeight();
                        tapeteBottom = tapete.getLocationOnScreen().getY() + tapete.getHeight();
                    }

                }

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {

                        if (!Game.isRECOVER()) {
                            full_screen_menu.setEnabled(true);
                            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                        }

                        zoom_menu_in.setEnabled(true);
                        zoom_menu_out.setEnabled(true);
                        zoom_menu_reset.setEnabled(true);

                    }
                });
            }
        });
    }

    public JCheckBoxMenuItem getMenu_cinematicas() {
        return menu_cinematicas;
    }

    public void cambiarColorContadoresTapete(Color color) {

        tapete.getCommunityCards().cambiarColorContadores(color);

    }

    public JRadioButtonMenuItem getMenu_tapete_madera() {
        return menu_tapete_madera;
    }

    public JRadioButtonMenuItem getMenu_tapete_rojo() {
        return menu_tapete_rojo;
    }

    public JRadioButtonMenuItem getMenu_tapete_azul() {
        return menu_tapete_azul;
    }

    public JRadioButtonMenuItem getMenu_tapete_verde() {
        return menu_tapete_verde;
    }

    public JCheckBoxMenuItem getAuto_action_menu() {
        return auto_action_menu;
    }

    public JCheckBoxMenuItem getAnimacion_menu() {
        return animacion_menu;
    }

    public JCheckBoxMenuItem getAscensor_menu() {
        return ascensor_menu;
    }

    public JCheckBoxMenuItem getAuto_rebuy_menu() {
        return auto_rebuy_menu;
    }

    public JMenuItem getChat_menu() {
        return chat_menu;
    }

    public JMenuItem getRegistro_menu() {
        return registro_menu;
    }

    public JCheckBoxMenuItem getSonidos_chorra_menu() {
        return sonidos_chorra_menu;
    }

    public JCheckBoxMenuItem getSonidos_menu() {
        return sonidos_menu;
    }

    public JCheckBoxMenuItem getTime_menu() {
        return time_menu;
    }

    public JMenuItem getZoom_menu_reset() {
        return zoom_menu_reset;
    }

    public void setConta_tiempo_juego(long conta_tiempo_juego) {
        this.conta_tiempo_juego = conta_tiempo_juego;
    }

    public JMenuItem getJugadas_menu() {
        return jugadas_menu;
    }

    public JMenuItem getExit_menu() {
        return exit_menu;
    }

    public void setFull_screen_window(Window full_screen_window) {
        this.full_screen_window = full_screen_window;
    }

    public JFrame getFull_screen_frame() {
        return full_screen_frame;
    }

    public void closeWindow() {

        formWindowClosing(null);
    }

    public boolean isFull_screen() {
        return full_screen;
    }

    public JCheckBoxMenuItem getConfirmar_menu() {
        return confirmar_menu;
    }

    public void fullScreen() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice device = env.getDefaultScreenDevice();

                if (!full_screen) {

                    if (Helpers.OSValidator.isWindows()) {
                        setVisible(false);
                        remove(Game.getInstance().getTapete());
                        full_screen_frame = new JFrame();
                        full_screen_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                        full_screen_frame.addWindowListener(new java.awt.event.WindowAdapter() {
                            @Override
                            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                                Game.getInstance().closeWindow();
                            }
                        });
                        full_screen_frame.setTitle(Game.getInstance().getTitle());
                        full_screen_frame.setUndecorated(true);
                        full_screen_frame.add(Game.getInstance().getTapete());
                        full_screen_frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                        full_screen_frame.setVisible(true);

                        if (timba_pausada) {

                            pausa_dialog.setVisible(false);
                            pausa_dialog.dispose();
                            pausa_dialog = new PauseDialog(full_screen_frame, false);
                            pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                            pausa_dialog.setVisible(true);
                        }

                    } else {
                        menu_bar.setVisible(false);
                        setVisible(false);
                        device.setFullScreenWindow(Game.getInstance());
                        setFull_screen_window(device.getFullScreenWindow());
                    }

                } else {

                    if (Helpers.OSValidator.isWindows()) {

                        full_screen_frame.remove(Game.getInstance().getTapete());
                        full_screen_frame.setVisible(false);
                        full_screen_frame.dispose();
                        full_screen_frame = null;

                        Game.getInstance().add(Game.getInstance().getTapete());
                        Game.getInstance().pack();
                        Game.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                        Game.getInstance().setVisible(true);

                        if (timba_pausada) {

                            pausa_dialog.setVisible(false);
                            pausa_dialog.dispose();
                            pausa_dialog = new PauseDialog(Game.getInstance(), false);
                            pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                            pausa_dialog.setVisible(true);
                        }

                    } else {

                        device.setFullScreenWindow(null);
                        setFull_screen_window(null);
                        setExtendedState(JFrame.MAXIMIZED_BOTH);
                        setVisible(true);
                        menu_bar.setVisible(true);
                    }
                }

                full_screen_menu.setEnabled(true);
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                full_screen = !full_screen;

                synchronized (full_screen_lock) {
                    full_screen_lock.notifyAll();
                }
            }
        });

    }

    public void cambiarBaraja() {

        Card.actualizarImagenesPrecargadas(1f + Game.getZoom_level() * Game.getZOOM_STEP());

        Helpers.playWavResource("misc/uncover.wav");

        Player[] players = tapete.getPlayers();

        for (Player jugador : players) {

            jugador.getPlayingCard1().refreshCard();
            jugador.getPlayingCard2().refreshCard();
        }

        for (Card carta : this.cartas_comunes) {
            carta.refreshCard();
        }

        if (this.jugadas_dialog != null && this.jugadas_dialog.isVisible()) {
            for (Card carta : this.jugadas_dialog.getCartas()) {
                carta.refreshCard();
            }

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    jugadas_dialog.pack();
                }
            });
        }
    }

    public void vistaCompacta() {

        Helpers.playWavResource("misc/uncover.wav");

        if (Game.SHOW_CLOCK) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    getTime_menu().doClick();
                }
            });
        }

        RemotePlayer[] players = tapete.getRemotePlayers();

        for (RemotePlayer jugador : players) {

            jugador.getPlayingCard1().refreshCard();
            jugador.getPlayingCard2().refreshCard();
        }

        for (Card carta : this.cartas_comunes) {
            carta.refreshCard();
        }
    }

    public boolean isGame_over_dialog() {
        return game_over_dialog;
    }

    public boolean isTimba_pausada() {
        return timba_pausada;
    }

    public void pauseTimba() {

        if (this.isPartida_local()) {

            getCrupier().broadcastGAMECommandFromServer("PAUSE", null);
        }

        this.timba_pausada = !this.timba_pausada;

        if (this.timba_pausada) {
            Helpers.playWavResource("misc/pause.wav");
        }

        synchronized (lock_pause) {
            this.lock_pause.notifyAll();
        }

        if (this.pausa_dialog == null) {
            this.pausa_dialog = new PauseDialog(this.getFull_screen_frame() != null ? this.getFull_screen_frame() : this, false);
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                if (timba_pausada) {

                    pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());

                    pausa_dialog.setVisible(true);

                } else {

                    pausa_dialog.setVisible(false);
                    pausa_dialog.dispose();
                    pausa_dialog = null;
                }

                if (isPartida_local()) {
                    Helpers.TapetePopupMenu.PAUSA_MENU.setText(timba_pausada ? "Reanudar timba" : "Pausar timba");

                    Helpers.TapetePopupMenu.PAUSA_MENU.setEnabled(true);

                    Helpers.translateComponents(Helpers.TapetePopupMenu.popup, false);

                    pausa_menu.setText(timba_pausada ? "Reanudar timba (ALT+P)" : "Pausar timba (ALT+P)");

                    Helpers.translateComponents(pausa_menu, false);

                    pausa_menu.setEnabled(true);
                }
            }
        });

    }

    public void setGame_over_dialog(boolean game_over_dialog) {
        this.game_over_dialog = game_over_dialog;
    }

    public boolean checkPause() {

        boolean paused = false;

        while (this.timba_pausada) {

            paused = true;

            if (this.timba_pausada) {
                synchronized (this.lock_pause) {
                    try {
                        this.lock_pause.wait(Game.WAIT_PAUSE);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }
        }

        return paused;

    }

    public JMenuItem getFull_screen_menu() {
        return full_screen_menu;
    }

    public static boolean isRECOVER() {
        return RECOVER;
    }

    public static void setRECOVER(boolean RECOVER) {
        Game.RECOVER = RECOVER;
    }

    private void setupGlobalShortcuts() {

        KeyStroke key_pause = KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_pause, new AbstractAction("PAUSE") {
            @Override
            public void actionPerformed(ActionEvent e) {
                pausa_menuActionPerformed(e);
            }
        });

        KeyStroke key_full_screen = KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_full_screen, new AbstractAction("FULL-SCREEN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                full_screen_menuActionPerformed(e);
            }
        });

        KeyStroke key_visor_jugadas = KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_visor_jugadas, new AbstractAction("VISOR-JUGADAS") {
            @Override
            public void actionPerformed(ActionEvent e) {
                jugadas_menu.doClick();
            }
        });

        KeyStroke compactCards = KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(compactCards, new AbstractAction("COMPACT-CARDS") {
            @Override
            public void actionPerformed(ActionEvent e) {
                compact_menu.doClick();
            }
        });

        KeyStroke key_zoom_in = KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK);
        actionMap.put(key_zoom_in, new AbstractAction("ZOOM-IN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoom_menu_inActionPerformed(e);
            }
        });

        KeyStroke key_zoom_out = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK);
        actionMap.put(key_zoom_out, new AbstractAction("ZOOM-OUT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoom_menu_outActionPerformed(e);
            }
        });

        KeyStroke key_zoom_reset = KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK);
        actionMap.put(key_zoom_reset, new AbstractAction("ZOOM-RESET") {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoom_menu_resetActionPerformed(e);
            }
        });

        KeyStroke key_sound = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_sound, new AbstractAction("SOUND") {
            @Override
            public void actionPerformed(ActionEvent e) {
                sonidos_menu.doClick();
            }
        });

        KeyStroke key_chat = KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_chat, new AbstractAction("CHAT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat_menuActionPerformed(e);
            }
        });

        KeyStroke key_registro = KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_registro, new AbstractAction("REGISTRO") {
            @Override
            public void actionPerformed(ActionEvent e) {
                registro_menuActionPerformed(e);
            }
        });

        KeyStroke key_time = KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_time, new AbstractAction("RELOJ") {
            @Override
            public void actionPerformed(ActionEvent e) {
                time_menu.doClick();
            }
        });

        KeyStroke key_fold = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        actionMap.put(key_fold, new AbstractAction("FOLD-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_fold().doClick();
                }
            }
        });

        KeyStroke key_check = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
        actionMap.put(key_check, new AbstractAction("CHECK-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_check().doClick();
                }
            }
        });

        KeyStroke key_bet = KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0);
        actionMap.put(key_bet, new AbstractAction("BET-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_bet_button().doClick();
                }
            }
        });

        KeyStroke key_allin = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0);
        actionMap.put(key_allin, new AbstractAction("ALLIN-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano() && !Game.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        });

        KeyStroke key_show = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        actionMap.put(key_show, new AbstractAction("SHOW-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano() && Game.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        });

        KeyStroke key_bet_left = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
        actionMap.put(key_bet_left, new AbstractAction("BET-LEFT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_slider().isEnabled()) {
                        getLocalPlayer().getBet_slider().setValue(getLocalPlayer().getBet_slider().getValue() - 1);
                    }
                }
            }
        });

        KeyStroke key_bet_down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
        actionMap.put(key_bet_down, new AbstractAction("BET-DOWN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_slider().isEnabled()) {
                        getLocalPlayer().getBet_slider().setValue(getLocalPlayer().getBet_slider().getValue() - 1);
                    }
                }
            }
        });

        KeyStroke key_bet_right = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0);
        actionMap.put(key_bet_right, new AbstractAction("BET-RIGHT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_slider().isEnabled()) {
                        getLocalPlayer().getBet_slider().setValue(getLocalPlayer().getBet_slider().getValue() + 1);
                    }
                }
            }
        });

        KeyStroke key_bet_up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
        actionMap.put(key_bet_up, new AbstractAction("BET-UP") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_slider().isEnabled()) {
                        getLocalPlayer().getBet_slider().setValue(getLocalPlayer().getBet_slider().getValue() + 1);
                    }
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

                JFrame frame = Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance();

                if (actionMap.containsKey(keyStroke) && !file_menu.isSelected() && !zoom_menu.isSelected() && !opciones_menu.isSelected() && !help_menu.isSelected() && frame.isActive()) {
                    final Action a = actionMap.get(keyStroke);
                    final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null);

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {
                            a.actionPerformed(ae);
                        }
                    });

                    return true;
                }

                return false;
            }
        });
    }

    public JMenuItem getPausa_menu() {
        return pausa_menu;
    }

    private WaitingRoom sala_espera;

    public Crupier getCrupier() {
        return crupier;
    }

    public boolean isPartida_local() {
        return partida_local;
    }

    public String getNick_local() {
        return nick_local;
    }

    public Map<String, Participant> getParticipantes() {
        return participantes;
    }

    public static float getZOOM_STEP() {
        return ZOOM_STEP;
    }

    public ArrayList<Player> getJugadores() {
        return jugadores;
    }

    public GameLogDialog getRegistro() {

        synchronized (registro_lock) {
            return registro_dialog;
        }
    }

    public Card getFlop1() {
        return tapete.getCommunityCards().getFlop1();
    }

    public Card getFlop2() {
        return tapete.getCommunityCards().getFlop2();
    }

    public JProgressBar getBarra_tiempo() {
        return tapete.getCommunityCards().getBarra_tiempo();
    }

    public Card getFlop3() {
        return tapete.getCommunityCards().getFlop3();
    }

    public LocalPlayer getLocalPlayer() {
        return tapete.getLocalPlayer();
    }

    public Card getRiver() {
        return tapete.getCommunityCards().getRiver();
    }

    public Card getTurn() {
        return tapete.getCommunityCards().getTurn();
    }

    public JMenuItem getZoom_menu_in() {
        return zoom_menu_in;
    }

    public JMenuItem getZoom_menu_out() {
        return zoom_menu_out;
    }

    public TablePanel getTapete() {
        return tapete;
    }

    public Card[] getCartas_comunes() {
        return cartas_comunes;
    }

    public static int getZoom_level() {
        return ZOOM_LEVEL;
    }

    public void setTapeteMano(int mano) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getHand_label().setText(Translator.translate("Mano: ") + String.valueOf(mano));
            }
        });
    }

    public void zoom(float factor) {

        Card.actualizarImagenesPrecargadas(factor);

        for (ZoomableInterface zoomeable : zoomeables) {
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoomeable.zoom(factor);
                }
            });
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                zoom_menu.setEnabled(true);
            }
        });
    }

    public void setTapeteBote(float bote) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getPot_label().setText(Translator.translate("Bote: ") + Helpers.float2String(bote));
            }
        });
    }

    public void setTapeteBote(String bote) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getPot_label().setText(Translator.translate("Bote: ") + bote);
            }
        });
    }

    public void setTapeteApuestas(float apuestas) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                String fase = null;

                switch (getCrupier().getFase()) {
                    case Crupier.PREFLOP:
                        fase = "Preflop: ";
                        break;

                    case Crupier.FLOP:
                        fase = "Flop: ";
                        break;

                    case Crupier.TURN:
                        fase = "Turn: ";
                        break;

                    case Crupier.RIVER:
                        fase = "River: ";
                        break;
                }

                tapete.getCommunityCards().getBet_label().setText(fase + Helpers.float2String(apuestas));
                tapete.getCommunityCards().getBet_label().setVisible(true);
            }
        });

    }

    public void hideTapeteApuestas() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                tapete.getCommunityCards().getBet_label().setVisible(false);
            }
        });

    }

    public void setTapeteCiegas(float pequeña, float grande) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getBlinds_label().setText(Translator.translate("Ciegas: ") + Helpers.float2String(pequeña) + " / " + Helpers.float2String(grande) + (Game.CIEGAS_TIME > 0 ? " @ " + String.valueOf(Game.CIEGAS_TIME) + "'" + (crupier.getCiegas_double() > 0 ? " (" + String.valueOf(crupier.getCiegas_double()) + ")" : "") : ""));
            }
        });

    }

    public WaitingRoom getSala_espera() {
        return sala_espera;
    }

    public void updateSoundIcon() {

        if (tapete.getCommunityCards().getPot_label().getHeight() > 0) {

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(tapete.getCommunityCards().getPot_label().getHeight(), tapete.getCommunityCards().getPot_label().getHeight()));
                    tapete.getCommunityCards().getSound_icon().setIcon(new ImageIcon(new ImageIcon(getClass().getResource(Game.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(tapete.getCommunityCards().getPot_label().getHeight(), tapete.getCommunityCards().getPot_label().getHeight(), Image.SCALE_SMOOTH)));
                }
            });
        } else {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH));
                    tapete.getCommunityCards().getSound_icon().setIcon(new ImageIcon(new ImageIcon(getClass().getResource(Game.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH, Image.SCALE_SMOOTH)));
                }
            });
        }
    }

    public JCheckBoxMenuItem getCompact_menu() {
        return compact_menu;
    }

    public JMenu getMenu_barajas() {
        return menu_barajas;
    }

    private void generarBarajasMenu() {

        for (Map.Entry<String, Object[]> entry : Card.BARAJAS.entrySet()) {

            javax.swing.JRadioButtonMenuItem menu_item = new javax.swing.JRadioButtonMenuItem(entry.getKey());

            menu_item.setFont(new java.awt.Font("Dialog", 0, 14));

            menu_item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    Game.BARAJA = menu_item.getText();

                    Helpers.PROPERTIES.setProperty("baraja", menu_item.getText());

                    Helpers.savePropertiesFile();

                    for (Component menu : menu_barajas.getMenuComponents()) {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                    }

                    menu_item.setSelected(true);

                    for (Component menu : BARAJAS_MENU.getMenuComponents()) {

                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(((javax.swing.JRadioButtonMenuItem) menu).getText().equals(menu_item.getText()));
                    }

                    Helpers.threadRun(new Runnable() {
                        public void run() {
                            cambiarBaraja();
                        }
                    });
                }
            });

            menu_barajas.add(menu_item);

        }
    }

    /**
     * Creates new form CoronaMainView
     */
    public Game(Map<String, Participant> parts, WaitingRoom salaespera, String nicklocal, boolean partidalocal) {

        THIS = this;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                setTitle(Init.WINDOW_TITLE + Translator.translate(" - Timba en curso (") + nicklocal + ")");

                participantes = parts;

                tapete = TablePanelFactory.getPanel(participantes.size());

                getContentPane().add(tapete);

                Player[] players = tapete.getPlayers();

                sala_espera = salaespera;

                nick_local = nicklocal;

                partida_local = partidalocal;

                auto_rebuy_menu.setSelected(Game.AUTO_REBUY);

                auto_rebuy_menu.setEnabled(Game.REBUY);

                compact_menu.setSelected(Game.VISTA_COMPACTA);

                Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD) : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

                if (!map.containsKey("allin/") || map.get("allin/").length == 0) {
                    Game.CINEMATICAS = false;
                    menu_cinematicas.setSelected(false);
                    menu_cinematicas.setEnabled(false);

                } else {
                    menu_cinematicas.setSelected(Game.CINEMATICAS);
                }

                animacion_menu.setSelected(Game.ANIMACION_REPARTIR);

                confirmar_menu.setSelected(Game.CONFIRM_ACTIONS);

                auto_action_menu.setSelected(Game.AUTO_ACTION_BUTTONS);

                sonidos_menu.setSelected(Game.SONIDOS);

                sonidos_chorra_menu.setSelected(Game.SONIDOS_CHORRA);

                ascensor_menu.setSelected(Game.MUSICA_AMBIENTAL);

                sonidos_chorra_menu.setEnabled(sonidos_menu.isSelected());

                ascensor_menu.setEnabled(sonidos_menu.isSelected());

                generarBarajasMenu();

                for (Component menu : menu_barajas.getMenuComponents()) {

                    if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(Game.BARAJA)) {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
                    } else {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                    }
                }

                menu_tapete_verde.setSelected(Game.COLOR_TAPETE.equals("verde"));

                menu_tapete_azul.setSelected(Game.COLOR_TAPETE.equals("azul"));

                menu_tapete_rojo.setSelected(Game.COLOR_TAPETE.equals("rojo"));

                menu_tapete_madera.setSelected(Game.COLOR_TAPETE.equals("madera"));

                switch (Game.COLOR_TAPETE) {

                    case "verde":
                        cambiarColorContadoresTapete(new Color(153, 204, 0));
                        break;

                    case "azul":
                        cambiarColorContadoresTapete(new Color(102, 204, 255));
                        break;

                    case "rojo":
                        cambiarColorContadoresTapete(new Color(255, 204, 51));
                        break;

                    case "madera":
                        cambiarColorContadoresTapete(Color.WHITE);
                        break;
                }

                full_screen_menu.setEnabled(true);

                updateSoundIcon();

                tapete.getCommunityCards().getBarra_tiempo().setMinimum(0);

                tapete.getCommunityCards().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);

                pausa_menu.setVisible(partida_local);

                server_separator_menu.setVisible(partida_local);

                tapete.getCommunityCards().getTiempo_partida().setVisible(Game.SHOW_CLOCK);

                time_menu.setSelected(Game.SHOW_CLOCK);

                zoomeables = new ZoomableInterface[]{tapete};

                cartas_comunes = tapete.getCommunityCards().getCartasComunes();

                tapete.getLocalPlayer().getPlayingCard1().setCompactable(false);
                tapete.getLocalPlayer().getPlayingCard2().setCompactable(false);

                jugadores = new ArrayList<>();

                for (int j = 0; j < participantes.size(); j++) {
                    jugadores.add(players[j]);
                }

                //Desactivamos los sitios no usados
                for (Player j : players) {
                    if (!jugadores.contains(j)) {
                        j.disablePlayer(false);
                        getContentPane().remove((Component) j);
                    }
                }

                getContentPane().revalidate();

                //Metemos la pasta a todos (el BUY IN se podría parametrizar)
                for (Player jugador : jugadores) {
                    jugador.setStack(Game.BUYIN);
                }

                setupGlobalShortcuts();

                // pausa_dialog = new PauseDialog(this, false);
                crupier = new Crupier();

                Helpers.TapetePopupMenu.addTo(tapete);

                Helpers.TapetePopupMenu.AUTOREBUY_MENU.setEnabled(Game.REBUY);

                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

                for (Component menu : BARAJAS_MENU.getMenuComponents()) {

                    if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(Game.BARAJA)) {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
                    } else {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                    }
                }

                Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(Game.COLOR_TAPETE.equals("verde"));

                Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(Game.COLOR_TAPETE.equals("azul"));

                Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(Game.COLOR_TAPETE.equals("rojo"));

                Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(Game.COLOR_TAPETE.equals("madera"));

                if (!menu_cinematicas.isEnabled()) {
                    Helpers.TapetePopupMenu.CINEMATICAS_MENU.setEnabled(false);
                    Helpers.TapetePopupMenu.CINEMATICAS_MENU.setSelected(false);
                }

                Helpers.loadOriginalFontSizes(THIS);

                Helpers.updateFonts(THIS, Helpers.GUI_FONT, null);

                Helpers.translateComponents(THIS, false);

                Helpers.translateComponents(Helpers.TapetePopupMenu.popup, false);

                pack();
            }
        });
    }

    public long getConta_tiempo_juego() {
        return conta_tiempo_juego;
    }

    public synchronized void finTransmision(boolean partida_terminada) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                exit_menu.setEnabled(false);
                menu_bar.setVisible(false);
            }
        });

        if (partida_terminada) {

            getRegistro().print("\n*************** LA TIMBA HA TERMINADO ***************");

            getRegistro().print(Translator.translate("FIN DE LA TIMBA -> ") + Helpers.getFechaHoraActual() + " (" + Helpers.seconds2FullTime(conta_tiempo_juego) + ")");

        }

        synchronized (crupier.getLock_contabilidad()) {

            crupier.auditorCuentas();

            for (Map.Entry<String, Float[]> entry : crupier.getAuditor().entrySet()) {

                Float[] pasta = entry.getValue();

                String ganancia_msg = "";

                float ganancia = Helpers.clean1DFloat(Helpers.clean1DFloat(pasta[0]) - Helpers.clean1DFloat(pasta[1]));

                if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                    ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                    ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                } else {
                    ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                }

                getRegistro().print(entry.getKey() + " " + ganancia_msg);
            }
        }

        getLocalPlayer().setExit();

        getCrupier().setFin_de_la_transmision(true); //AQUí, y NO ANTES porque si el hilo del crupier termina antes la liamos

        String log_file = Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss") + ".log";

        try {
            Files.writeString(Paths.get(log_file), getRegistro().getText());
        } catch (IOException ex1) {
            Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex1);
        }

        String chat_file = Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss") + ".log";

        try {
            Files.writeString(Paths.get(chat_file), this.getSala_espera().getChat().getText());
        } catch (IOException ex1) {
            Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex1);
        }

        if (Game.SONIDOS && Game.SONIDOS_CHORRA) {
            Helpers.muteLoopMp3();
            Helpers.playWavResourceAndWait("misc/end.wav");
        }

        System.exit(0); //No hay otra
    }

    public Timer getTiempo_juego() {
        return tiempo_juego;
    }

    public void AJUGAR() {

        ActionListener listener = new ActionListener() {

            public void actionPerformed(ActionEvent ae) {

                if (!crupier.isFin_de_la_transmision() && crupier.isJugadores_suficientes() && !isTimba_pausada() && !WaitingRoom.isExit() && !isRECOVER()) {
                    conta_tiempo_juego++;

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            tapete.getCommunityCards().getTiempo_partida().setText(Helpers.seconds2FullTime(conta_tiempo_juego));
                        }
                    });
                } else {
                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            tapete.getCommunityCards().getTiempo_partida().setText("--:--:--");
                        }
                    });
                }

            }
        };

        tiempo_juego = new Timer(1000, listener);

        tiempo_juego.start();

        Helpers.playWavResource("misc/startplay.wav");

        if (!Game.MUSICA_AMBIENTAL) {
            Helpers.pauseLoopMp3Resource("misc/background_music.mp3");
        }

        registro_dialog = new GameLogDialog(this, false);

        getRegistro().print(Translator.translate("COMIENZA LA TIMBA -> ") + Helpers.getFechaHoraActual());

        Helpers.threadRun(crupier);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        menu_bar = new javax.swing.JMenuBar();
        file_menu = new javax.swing.JMenu();
        chat_menu = new javax.swing.JMenuItem();
        registro_menu = new javax.swing.JMenuItem();
        jugadas_menu = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        pausa_menu = new javax.swing.JMenuItem();
        server_separator_menu = new javax.swing.JPopupMenu.Separator();
        full_screen_menu = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        exit_menu = new javax.swing.JMenuItem();
        zoom_menu = new javax.swing.JMenu();
        zoom_menu_in = new javax.swing.JMenuItem();
        zoom_menu_out = new javax.swing.JMenuItem();
        zoom_menu_reset = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        compact_menu = new javax.swing.JCheckBoxMenuItem();
        opciones_menu = new javax.swing.JMenu();
        sonidos_menu = new javax.swing.JCheckBoxMenuItem();
        sonidos_chorra_menu = new javax.swing.JCheckBoxMenuItem();
        ascensor_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        confirmar_menu = new javax.swing.JCheckBoxMenuItem();
        auto_action_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator7 = new javax.swing.JPopupMenu.Separator();
        menu_cinematicas = new javax.swing.JCheckBoxMenuItem();
        animacion_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        time_menu = new javax.swing.JCheckBoxMenuItem();
        decks_separator = new javax.swing.JPopupMenu.Separator();
        menu_barajas = new javax.swing.JMenu();
        menu_tapetes = new javax.swing.JMenu();
        menu_tapete_verde = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_azul = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_rojo = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_madera = new javax.swing.JRadioButtonMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        auto_rebuy_menu = new javax.swing.JCheckBoxMenuItem();
        help_menu = new javax.swing.JMenu();
        acerca_menu = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("CoronaPoker");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        menu_bar.setDoubleBuffered(true);
        menu_bar.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N

        file_menu.setMnemonic('i');
        file_menu.setText("Archivo");
        file_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        chat_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chat_menu.setText("Ver chat (ALT+C)");
        chat_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_menuActionPerformed(evt);
            }
        });
        file_menu.add(chat_menu);

        registro_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        registro_menu.setText("Ver registro (ALT+R)");
        registro_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                registro_menuActionPerformed(evt);
            }
        });
        file_menu.add(registro_menu);

        jugadas_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jugadas_menu.setText("Generador de jugadas (ALT+J)");
        jugadas_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jugadas_menuActionPerformed(evt);
            }
        });
        file_menu.add(jugadas_menu);
        file_menu.add(jSeparator5);

        pausa_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        pausa_menu.setText("Pausar timba (ALT+P)");
        pausa_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pausa_menuActionPerformed(evt);
            }
        });
        file_menu.add(pausa_menu);
        file_menu.add(server_separator_menu);

        full_screen_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        full_screen_menu.setText("PANTALLA COMPLETA (ALT+F)");
        full_screen_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                full_screen_menuActionPerformed(evt);
            }
        });
        file_menu.add(full_screen_menu);
        file_menu.add(jSeparator2);

        exit_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        exit_menu.setText("Salir (ALT+F4)");
        exit_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exit_menuActionPerformed(evt);
            }
        });
        file_menu.add(exit_menu);

        menu_bar.add(file_menu);

        zoom_menu.setText("Zoom");
        zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        zoom_menu_in.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_in.setText("Aumentar (CTRL++)");
        zoom_menu_in.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_inActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_in);

        zoom_menu_out.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_out.setText("Reducir (CTRL+-)");
        zoom_menu_out.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_outActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_out);

        zoom_menu_reset.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_reset.setText("Reset (CTRL+0)");
        zoom_menu_reset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_resetActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_reset);
        zoom_menu.add(jSeparator6);

        compact_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        compact_menu.setSelected(true);
        compact_menu.setText("Vista compacta (ALT+X)");
        compact_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compact_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(compact_menu);

        menu_bar.add(zoom_menu);

        opciones_menu.setText("Preferencias");
        opciones_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        sonidos_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        sonidos_menu.setSelected(true);
        sonidos_menu.setText("SONIDOS (ALT+S)");
        sonidos_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sonidos_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(sonidos_menu);

        sonidos_chorra_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        sonidos_chorra_menu.setSelected(true);
        sonidos_chorra_menu.setText("Comentarios profesionales");
        sonidos_chorra_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sonidos_chorra_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(sonidos_chorra_menu);

        ascensor_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        ascensor_menu.setSelected(true);
        ascensor_menu.setText("Música ambiental");
        ascensor_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ascensor_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(ascensor_menu);
        opciones_menu.add(jSeparator1);

        confirmar_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        confirmar_menu.setSelected(true);
        confirmar_menu.setText("Confirmar todas las acciones");
        confirmar_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                confirmar_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(confirmar_menu);

        auto_action_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_action_menu.setSelected(true);
        auto_action_menu.setText("Botones AUTO");
        auto_action_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_action_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_action_menu);
        opciones_menu.add(jSeparator7);

        menu_cinematicas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_cinematicas.setSelected(true);
        menu_cinematicas.setText("Cinemáticas");
        menu_cinematicas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_cinematicasActionPerformed(evt);
            }
        });
        opciones_menu.add(menu_cinematicas);

        animacion_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        animacion_menu.setSelected(true);
        animacion_menu.setText("Animación al repartir");
        animacion_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                animacion_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(animacion_menu);
        opciones_menu.add(jSeparator8);

        time_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        time_menu.setSelected(true);
        time_menu.setText("Mostrar reloj (ALT+W)");
        time_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                time_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(time_menu);
        opciones_menu.add(decks_separator);

        menu_barajas.setText("Barajas");
        menu_barajas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        opciones_menu.add(menu_barajas);

        menu_tapetes.setText("Tapetes");
        menu_tapetes.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        menu_tapete_verde.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_verde.setSelected(true);
        menu_tapete_verde.setText("Verde");
        menu_tapete_verde.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_verdeActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_verde);

        menu_tapete_azul.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_azul.setSelected(true);
        menu_tapete_azul.setText("Azul");
        menu_tapete_azul.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_azulActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_azul);

        menu_tapete_rojo.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_rojo.setSelected(true);
        menu_tapete_rojo.setText("Rojo");
        menu_tapete_rojo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_rojoActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_rojo);

        menu_tapete_madera.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_madera.setSelected(true);
        menu_tapete_madera.setText("Sin tapete");
        menu_tapete_madera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_maderaActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_madera);

        opciones_menu.add(menu_tapetes);
        opciones_menu.add(jSeparator4);

        auto_rebuy_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_rebuy_menu.setSelected(true);
        auto_rebuy_menu.setText("Recompra automática");
        auto_rebuy_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_rebuy_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_rebuy_menu);

        menu_bar.add(opciones_menu);

        help_menu.setText("Ayuda");
        help_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        acerca_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        acerca_menu.setText("Acerca de");
        acerca_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acerca_menuActionPerformed(evt);
            }
        });
        help_menu.add(acerca_menu);

        menu_bar.add(help_menu);

        setJMenuBar(menu_bar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exit_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exit_menuActionPerformed
        // TODO add your handling code here:

        if (this.isPartida_local()) {

            if (jugadores.size() > 1) {

                // 0=yes, 1=no, 2=cancel
                if (Helpers.mostrarMensajeInformativoSINO(this, "¡CUIDADO! ERES EL ANFITRIÓN Y SI SALES SE TERMINARÁ LA TIMBA. ¿ESTÁS SEGURO?") == 0) {

                    Helpers.threadRun(new Runnable() {
                        public void run() {
                            //Hay que avisar a los clientes de que la timba ha terminado
                            crupier.broadcastGAMECommandFromServer("SERVEREXIT", null, false);
                            finTransmision(true);
                        }
                    });

                }

            } else {

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        finTransmision(true);
                    }
                });
            }

        } else {
            // 0=yes, 1=no, 2=cancel
            if (Helpers.mostrarMensajeInformativoSINO(this, "¡CUIDADO! Si sales de la timba no podrás volver a entrar. ¿ESTÁS SEGURO?") == 0) {

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        if (!getSala_espera().isReconnecting()) {
                            crupier.sendGAMECommandToServer("EXIT", false);
                        }

                        finTransmision(false);
                    }
                });

            }
        }

    }//GEN-LAST:event_exit_menuActionPerformed

    private void acerca_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acerca_menuActionPerformed
        // TODO add your handling code here:
        this.about_dialog = new AboutDialog(this, true);

        this.about_dialog.setLocationRelativeTo(about_dialog.getParent());

        this.about_dialog.setVisible(true);
    }//GEN-LAST:event_acerca_menuActionPerformed

    private void zoom_menu_inActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_inActionPerformed
        // TODO add your handling code here:

        zoom_menu.setEnabled(false);

        ZOOM_LEVEL++;

        Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {
                zoom(1f + ZOOM_LEVEL * ZOOM_STEP);

                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.refreshCard();
                    }

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            jugadas_dialog.pack();
                        }
                    });
                }

            }
        });

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_zoom_menu_inActionPerformed

    private void zoom_menu_outActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_outActionPerformed
        // TODO add your handling code here:

        zoom_menu.setEnabled(false);

        if (Helpers.float1DSecureCompare(0f, 1f + ((ZOOM_LEVEL - 1) * ZOOM_STEP)) < 0) {
            ZOOM_LEVEL--;
            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP);

                    if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                        for (Card carta : jugadas_dialog.getCartas()) {
                            carta.refreshCard();
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                jugadas_dialog.pack();
                            }
                        });
                    }
                }
            });

            Helpers.savePropertiesFile();
        }

    }//GEN-LAST:event_zoom_menu_outActionPerformed

    private void zoom_menu_resetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_resetActionPerformed
        // TODO add your handling code here:

        if (ZOOM_LEVEL != DEFAULT_ZOOM_LEVEL) {
            zoom_menu.setEnabled(false);

            ZOOM_LEVEL = DEFAULT_ZOOM_LEVEL;

            Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP);
                    if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                        for (Card carta : jugadas_dialog.getCartas()) {
                            carta.refreshCard();
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                jugadas_dialog.pack();
                            }
                        });
                    }
                }
            });

            Helpers.savePropertiesFile();
        }
    }//GEN-LAST:event_zoom_menu_resetActionPerformed

    private void registro_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_registro_menuActionPerformed
        // TODO add your handling code here:

        this.registro_dialog.setVisible(false);

        this.registro_dialog.setLocationRelativeTo(getFull_screen_frame() != null ? getFull_screen_frame() : this);

        this.registro_dialog.setVisible(true);

    }//GEN-LAST:event_registro_menuActionPerformed

    private void chat_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_menuActionPerformed
        // TODO add your handling code here:

        if (!this.sala_espera.isActive()) {
            this.sala_espera.setVisible(false);
        }

        this.sala_espera.setLocationRelativeTo(this.getFull_screen_frame() != null ? this.getFull_screen_frame() : this);
        this.sala_espera.setExtendedState(JFrame.NORMAL);
        this.sala_espera.setVisible(true);

    }//GEN-LAST:event_chat_menuActionPerformed

    private void sonidos_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sonidos_menuActionPerformed
        // TODO add your handling code here:

        Game.SONIDOS = this.sonidos_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonidos", Game.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        updateSoundIcon();

        this.sonidos_chorra_menu.setEnabled(Game.SONIDOS);

        this.ascensor_menu.setEnabled(Game.SONIDOS);

        if (!Game.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.unMuteAll();
        }

        Helpers.TapetePopupMenu.SONIDOS_MENU.setSelected(Game.SONIDOS);

        Helpers.TapetePopupMenu.SONIDOS_COMENTARIOS_MENU.setEnabled(Game.SONIDOS);

        Helpers.TapetePopupMenu.SONIDOS_MUSICA_MENU.setEnabled(Game.SONIDOS);
    }//GEN-LAST:event_sonidos_menuActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        this.exit_menu.doClick();
    }//GEN-LAST:event_formWindowClosing

    private void time_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_time_menuActionPerformed
        // TODO add your handling code here:

        Game.SHOW_CLOCK = time_menu.isSelected();

        tapete.getCommunityCards().getTiempo_partida().setVisible(time_menu.isSelected());

        Helpers.PROPERTIES.setProperty("show_time", this.time_menu.isSelected() ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.RELOJ_MENU.setSelected(Game.SHOW_CLOCK);
    }//GEN-LAST:event_time_menuActionPerformed

    private void sonidos_chorra_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sonidos_chorra_menuActionPerformed
        // TODO add your handling code here:
        Game.SONIDOS_CHORRA = this.sonidos_chorra_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonidos_chorra", this.sonidos_chorra_menu.isSelected() ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.SONIDOS_COMENTARIOS_MENU.setSelected(Game.SONIDOS_CHORRA);

    }//GEN-LAST:event_sonidos_chorra_menuActionPerformed

    private void ascensor_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ascensor_menuActionPerformed
        // TODO add your handling code here:
        Game.MUSICA_AMBIENTAL = this.ascensor_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonido_ascensor", this.ascensor_menu.isSelected() ? "true" : "false");

        Helpers.savePropertiesFile();

        if (this.ascensor_menu.isSelected()) {
            Helpers.resumeLoopMp3Resource("misc/background_music.mp3");
        } else {
            Helpers.pauseLoopMp3Resource("misc/background_music.mp3");
        }

        Helpers.TapetePopupMenu.SONIDOS_MUSICA_MENU.setSelected(Game.MUSICA_AMBIENTAL);
    }//GEN-LAST:event_ascensor_menuActionPerformed

    private void jugadas_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jugadas_menuActionPerformed
        // TODO add your handling code here:

        Helpers.threadRun(new Runnable() {
            public void run() {
                if (jugadas_dialog == null) {

                    jugadas_dialog = new HandGeneratorDialog(Game.getInstance(), false);

                    jugadas_dialog.pintarJugada();

                } else {
                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            jugadas_dialog.setVisible(false);
                        }
                    });
                }

                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.refreshCard();
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        jugadas_dialog.pack();

                        jugadas_dialog.setLocationRelativeTo(getFull_screen_frame() != null ? getFull_screen_frame() : Game.getInstance());

                        jugadas_dialog.setVisible(true);
                    }
                });

            }
        });
    }//GEN-LAST:event_jugadas_menuActionPerformed

    private void full_screen_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_full_screen_menuActionPerformed
        // TODO add your handling code here:

        if (this.full_screen_menu.isEnabled() && !this.isGame_over_dialog()) {

            this.full_screen_menu.setEnabled(false);

            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(!this.full_screen);

            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(false);

            fullScreen();
        }

    }//GEN-LAST:event_full_screen_menuActionPerformed

    private void auto_rebuy_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_rebuy_menuActionPerformed
        // TODO add your handling code here:

        Game.AUTO_REBUY = this.auto_rebuy_menu.isSelected();

        Helpers.PROPERTIES.setProperty("auto_rebuy", this.auto_rebuy_menu.isSelected() ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTOREBUY_MENU.setSelected(Game.AUTO_REBUY);

        Helpers.playWavResource("misc/cash_register.wav");

    }//GEN-LAST:event_auto_rebuy_menuActionPerformed

    private void pausa_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pausa_menuActionPerformed
        // TODO add your handling code here:

        if (this.isPartida_local() && !isRECOVER() && !getCrupier().isSincronizando_mano() && !this.isGame_over_dialog()) {

            this.pausa_menu.setText("Pausando timba...");

            this.pausa_menu.setEnabled(false);

            Helpers.TapetePopupMenu.PAUSA_MENU.setText("Pausando timba...");

            Helpers.TapetePopupMenu.PAUSA_MENU.setEnabled(false);

            if (this.timba_pausada) {
                this.pausa_dialog.resuming();
            }

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    pauseTimba();
                }
            });

        }

    }//GEN-LAST:event_pausa_menuActionPerformed

    private void compact_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compact_menuActionPerformed
        // TODO add your handling code here:
        Game.VISTA_COMPACTA = this.compact_menu.isSelected();

        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(Game.VISTA_COMPACTA));

        Helpers.savePropertiesFile();

        Helpers.threadRun(new Runnable() {
            public void run() {
                vistaCompacta();
            }
        });

        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(Game.VISTA_COMPACTA);
    }//GEN-LAST:event_compact_menuActionPerformed

    private void confirmar_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_confirmar_menuActionPerformed
        // TODO add your handling code here:
        Game.CONFIRM_ACTIONS = this.confirmar_menu.isSelected();

        Helpers.PROPERTIES.setProperty("confirmar_todo", String.valueOf(Game.CONFIRM_ACTIONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CONFIRM_MENU.setSelected(Game.CONFIRM_ACTIONS);

        if (!Game.CONFIRM_ACTIONS) {
            this.getLocalPlayer().desarmarBotonesAccion();
        }

    }//GEN-LAST:event_confirmar_menuActionPerformed

    private void animacion_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_animacion_menuActionPerformed
        // TODO add your handling code here:

        Game.ANIMACION_REPARTIR = this.animacion_menu.isSelected();

        Helpers.PROPERTIES.setProperty("animacion_reparto", String.valueOf(Game.ANIMACION_REPARTIR));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.ANIMACION_MENU.setSelected(Game.ANIMACION_REPARTIR);
    }//GEN-LAST:event_animacion_menuActionPerformed

    private void auto_action_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_action_menuActionPerformed
        // TODO add your handling code here:
        Game.AUTO_ACTION_BUTTONS = this.auto_action_menu.isSelected();

        Helpers.PROPERTIES.setProperty("auto_action_buttons", String.valueOf(Game.AUTO_ACTION_BUTTONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTO_ACTION_MENU.setSelected(Game.AUTO_ACTION_BUTTONS);

        if (Game.AUTO_ACTION_BUTTONS) {
            this.getLocalPlayer().activarPreBotones();
        } else {
            this.getLocalPlayer().desActivarPreBotones();
        }
    }//GEN-LAST:event_auto_action_menuActionPerformed

    private void menu_tapete_verdeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_verdeActionPerformed
        // TODO add your handling code here:
        Game.COLOR_TAPETE = "verde";

        Helpers.PROPERTIES.setProperty("color_tapete", "verde");

        Helpers.savePropertiesFile();

        for (Component c : this.menu_tapetes.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        this.menu_tapete_verde.setSelected(true);

        tapete.refresh();

        cambiarColorContadoresTapete(new Color(153, 204, 0));

        for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);
    }//GEN-LAST:event_menu_tapete_verdeActionPerformed

    private void menu_tapete_azulActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_azulActionPerformed
        // TODO add your handling code here:

        Game.COLOR_TAPETE = "azul";

        Helpers.PROPERTIES.setProperty("color_tapete", "azul");

        Helpers.savePropertiesFile();

        for (Component c : this.menu_tapetes.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        this.menu_tapete_azul.setSelected(true);

        tapete.refresh();

        cambiarColorContadoresTapete(new Color(102, 204, 255));

        for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);
    }//GEN-LAST:event_menu_tapete_azulActionPerformed

    private void menu_tapete_rojoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_rojoActionPerformed
        // TODO add your handling code here:
        Game.COLOR_TAPETE = "rojo";

        Helpers.PROPERTIES.setProperty("color_tapete", "rojo");

        Helpers.savePropertiesFile();

        for (Component c : this.menu_tapetes.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        this.menu_tapete_rojo.setSelected(true);

        tapete.refresh();

        cambiarColorContadoresTapete(new Color(255, 204, 51));

        for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);
    }//GEN-LAST:event_menu_tapete_rojoActionPerformed

    private void menu_tapete_maderaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_maderaActionPerformed
        // TODO add your handling code here:
        Game.COLOR_TAPETE = "madera";

        Helpers.PROPERTIES.setProperty("color_tapete", "madera");

        Helpers.savePropertiesFile();

        for (Component c : this.menu_tapetes.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        this.menu_tapete_madera.setSelected(true);

        tapete.refresh();

        cambiarColorContadoresTapete(Color.WHITE);

        for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
            ((JRadioButtonMenuItem) c).setSelected(false);
        }

        Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);
    }//GEN-LAST:event_menu_tapete_maderaActionPerformed

    private void menu_cinematicasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_cinematicasActionPerformed
        // TODO add your handling code here:

        Game.CINEMATICAS = this.menu_cinematicas.isSelected();

        Helpers.PROPERTIES.setProperty("cinematicas", String.valueOf(Game.CINEMATICAS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CINEMATICAS_MENU.setSelected(Game.CINEMATICAS);
    }//GEN-LAST:event_menu_cinematicasActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem acerca_menu;
    private javax.swing.JCheckBoxMenuItem animacion_menu;
    private javax.swing.JCheckBoxMenuItem ascensor_menu;
    private javax.swing.JCheckBoxMenuItem auto_action_menu;
    private javax.swing.JCheckBoxMenuItem auto_rebuy_menu;
    private javax.swing.JMenuItem chat_menu;
    private javax.swing.JCheckBoxMenuItem compact_menu;
    private javax.swing.JCheckBoxMenuItem confirmar_menu;
    private javax.swing.JPopupMenu.Separator decks_separator;
    private javax.swing.JMenuItem exit_menu;
    private javax.swing.JMenu file_menu;
    private javax.swing.JMenuItem full_screen_menu;
    private javax.swing.JMenu help_menu;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JMenuItem jugadas_menu;
    private javax.swing.JMenuBar menu_bar;
    private javax.swing.JMenu menu_barajas;
    private javax.swing.JCheckBoxMenuItem menu_cinematicas;
    private javax.swing.JRadioButtonMenuItem menu_tapete_azul;
    private javax.swing.JRadioButtonMenuItem menu_tapete_madera;
    private javax.swing.JRadioButtonMenuItem menu_tapete_rojo;
    private javax.swing.JRadioButtonMenuItem menu_tapete_verde;
    private javax.swing.JMenu menu_tapetes;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JMenuItem pausa_menu;
    private javax.swing.JMenuItem registro_menu;
    private javax.swing.JPopupMenu.Separator server_separator_menu;
    private javax.swing.JCheckBoxMenuItem sonidos_chorra_menu;
    private javax.swing.JCheckBoxMenuItem sonidos_menu;
    private javax.swing.JCheckBoxMenuItem time_menu;
    private javax.swing.JMenu zoom_menu;
    private javax.swing.JMenuItem zoom_menu_in;
    private javax.swing.JMenuItem zoom_menu_out;
    private javax.swing.JMenuItem zoom_menu_reset;
    // End of variables declaration//GEN-END:variables
}
