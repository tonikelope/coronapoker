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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/**
 *
 * @author tonikelope
 */
public final class GameFrame extends javax.swing.JFrame implements ZoomableInterface {

    public static final int TEST_MODE_PAUSE = 250;
    public static final int DEFAULT_ZOOM_LEVEL = -2;
    public static final float MIN_BIG_BLIND = 0.20f;
    public static final float ZOOM_STEP = 0.05f;
    public static final int PAUSA_ENTRE_MANOS = 8; //Segundos
    public static final int PAUSA_ENTRE_MANOS_TEST = 1;
    public static final int PAUSA_ANTES_DE_SHOWDOWN = 1; //Segundos
    public static final int TIEMPO_PENSAR = 35; //Segundos
    public static final int WAIT_QUEUES = 1000;
    public static final int WAIT_PAUSE = 1000;
    public static final int CLIENT_RECEPTION_TIMEOUT = 10000;
    public static final int CONFIRMATION_TIMEOUT = 10000;
    public static final int CLIENT_RECON_TIMEOUT = 2 * TIEMPO_PENSAR * 1000; // Tiempo en milisegundos que esperaremos cliente que perdió la conexión antes (preguntar) si echarle de la timba
    public static final int CLIENT_RECON_ERROR_PAUSE = 5000;
    public static final int REBUY_TIMEOUT = 25000;
    public static final int MAX_TIMEOUT_CONFIRMATION_ERROR = 10;
    public static final String BARAJA_DEFAULT = "goliat";
    public static final String DEFAULT_LANGUAGE = "es";
    public static final int PEPILLO_COUNTER_MAX = 5;
    public static final int PAUSE_COUNTER_MAX = 3;
    public static final int AUTO_ZOOM_TIMEOUT = 3000;
    public static final int GUI_ZOOM_WAIT = 250;
    public static final boolean TEST_MODE = false;

    public static volatile float CIEGA_PEQUEÑA = 0.10f;
    public static volatile float CIEGA_GRANDE = 0.20f;
    public static volatile int BUYIN = 10;
    public static volatile int CIEGAS_DOUBLE = 60;
    public static volatile int CIEGAS_DOUBLE_TYPE = 1; //1 MINUTES, 2 HANDS
    public static volatile boolean REBUY = true;
    public static volatile int MANOS = -1;
    public static volatile boolean SONIDOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos", "true")) && !TEST_MODE;
    public static volatile boolean SONIDOS_CHORRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_chorra", "true"));
    public static volatile boolean SONIDOS_TTS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_tts", "true"));
    public static volatile boolean MUSICA_AMBIENTAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ascensor", "true"));
    public static volatile boolean SHOW_CLOCK = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("show_time", "false"));
    public static volatile boolean CONFIRM_ACTIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("confirmar_todo", "false")) && !TEST_MODE;
    public static volatile int ZOOM_LEVEL = Integer.parseInt(Helpers.PROPERTIES.getProperty("zoom_level", String.valueOf(GameFrame.DEFAULT_ZOOM_LEVEL)));
    public static volatile String BARAJA = Helpers.PROPERTIES.getProperty("baraja", BARAJA_DEFAULT);
    public static volatile boolean VISTA_COMPACTA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("vista_compacta", "false")) && !TEST_MODE;
    public static volatile boolean ANIMACION_REPARTIR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_reparto", "false"));
    public static volatile boolean AUTO_ACTION_BUTTONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_buttons", "false")) && !TEST_MODE;
    public static volatile String COLOR_TAPETE = Helpers.PROPERTIES.getProperty("color_tapete", "verde");
    public static volatile String LANGUAGE = Helpers.PROPERTIES.getProperty("lenguaje", "es").toLowerCase();
    public static volatile boolean CINEMATICAS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas", "true"));
    public static volatile boolean RECOVER = false;
    public static volatile Boolean MAC_NATIVE_FULLSCREEN = null;
    public static volatile boolean TTS_SERVER = true;
    public static volatile int RECOVER_ID = -1;
    public static volatile KeyEventDispatcher key_event_dispatcher = null;
    private static volatile GameFrame THIS = null;

    public static GameFrame getInstance() {
        return THIS;
    }

    private final Object registro_lock = new Object();
    private final Object full_screen_lock = new Object();
    private final Object lock_pause = new Object();
    private final Object lock_fin = new Object();
    private final ArrayList<Player> jugadores;
    private final ConcurrentHashMap<String, String> nick2avatar = new ConcurrentHashMap<>();
    private final Crupier crupier;
    private final boolean partida_local;
    private final String nick_local;

    private volatile ZoomableInterface[] zoomables;
    private volatile long conta_tiempo_juego = 0L;
    private volatile boolean full_screen = false;
    private volatile boolean timba_pausada = false;
    private volatile String nick_pause = null;
    private volatile PauseDialog pausa_dialog = null;
    private volatile boolean game_over_dialog = false;
    private volatile JFrame full_screen_frame = null;
    private volatile AboutDialog about_dialog = null;
    private volatile TTSNotifyDialog nick_dialog = null;
    private volatile HandGeneratorDialog jugadas_dialog = null;
    private volatile GameLogDialog registro_dialog = null;
    private volatile FastChatDialog fastchat_dialog = null;
    private volatile RebuyNowDialog rebuy_dialog = null;
    private volatile GifAnimationDialog gif_dialog = null;
    private volatile TablePanel tapete = null;
    private volatile Timer tiempo_juego;

    public JCheckBoxMenuItem getRebuy_now_menu() {
        return rebuy_now_menu;
    }

    public String getNick_pause() {
        return nick_pause;
    }

    public Object getLock_pause() {
        return lock_pause;
    }

    //--illegal-access=permit
    public void toggleMacNativeFullScreen(Window window) {

        if (Helpers.OSValidator.isMac()) {
            try {

                Method getApplication = Class.forName("com.apple.eawt.Application").getMethod("getApplication", (Class<?>[]) null);

                Object app = getApplication.invoke(null);

                Method requestToggleFullScreen = Class.forName("com.apple.eawt.Application").getMethod("requestToggleFullScreen", new Class<?>[]{Window.class});

                requestToggleFullScreen.invoke(Class.forName("com.apple.eawt.Application").cast(app), window);

            } catch (Exception ex) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    //--illegal-access=permit
    public void enableMacNativeFullScreen(Window window) {

        if (Helpers.OSValidator.isMac() && GameFrame.MAC_NATIVE_FULLSCREEN == null) {

            try {

                Method setWindowCanFullScreen = Class.forName("com.apple.eawt.FullScreenUtilities").getMethod("setWindowCanFullScreen", new Class<?>[]{Window.class, boolean.class});

                setWindowCanFullScreen.invoke(null, window, true);

                Method addFullScreenListenerTo = Class.forName("com.apple.eawt.FullScreenUtilities").getMethod("addFullScreenListenerTo", new Class<?>[]{Window.class, Class.forName("com.apple.eawt.FullScreenListener")});

                Object proxyFullScreenListener = Proxy.newProxyInstance(Class.forName("com.apple.eawt.FullScreenListener").getClassLoader(), new Class[]{Class.forName("com.apple.eawt.FullScreenListener")}, new InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        if (method.getName().equals("windowEnteredFullScreen")) {

                            Helpers.GUIRun(new Runnable() {
                                @Override
                                public void run() {
                                    menu_bar.setVisible(false);
                                    full_screen_menu.setEnabled(true);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                    full_screen_menu.setSelected(true);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(true);
                                    full_screen = true;

                                    synchronized (full_screen_lock) {
                                        full_screen_lock.notifyAll();
                                    }
                                }
                            });

                        } else if (method.getName().equals("windowExitedFullScreen")) {

                            Helpers.GUIRun(new Runnable() {
                                @Override
                                public void run() {
                                    menu_bar.setVisible(true);
                                    full_screen_menu.setEnabled(true);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);
                                    full_screen_menu.setSelected(false);
                                    Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(false);
                                    full_screen = false;

                                    synchronized (full_screen_lock) {
                                        full_screen_lock.notifyAll();
                                    }
                                }
                            });
                        }

                        return true;
                    }

                });

                addFullScreenListenerTo.invoke(null, window, Class.forName("com.apple.eawt.FullScreenListener").cast(proxyFullScreenListener));
                GameFrame.MAC_NATIVE_FULLSCREEN = true;

            } catch (Exception e) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.WARNING, null, e);
                GameFrame.MAC_NATIVE_FULLSCREEN = false;
            }
        }
    }

    public void autoZoomFullScreen() {

        if (Helpers.OSValidator.isMac()) {

            GameFrame.getInstance().enableMacNativeFullScreen(GameFrame.getInstance());

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    setVisible(true);
                    GameFrame.getInstance().setEnabled(false);
                }
            });

            Helpers.pausar(1000);
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                GameFrame.getInstance().setEnabled(true);
                full_screen_menu.doClick();
                GameFrame.getInstance().setEnabled(false);
            }
        });

        int t = 0;

        while (!full_screen && t < AUTO_ZOOM_TIMEOUT) {

            synchronized (full_screen_lock) {
                try {
                    full_screen_lock.wait(1000);
                    t += 1000;
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (GameFrame.getZoom_level() != 0) {
            GameFrame.getInstance().zoom(1f + GameFrame.getZoom_level() * GameFrame.ZOOM_STEP, null);
        }

        if (!tapete.autoZoom(false)) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, "AUTOZOOM TIMEOUT ERROR!");
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                GameFrame.getInstance().setEnabled(true);
                full_screen_menu.setEnabled(!GameFrame.isRECOVER());
                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(!GameFrame.isRECOVER());
            }
        });

    }

    public ConcurrentHashMap<String, String> getNick2avatar() {
        return nick2avatar;
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

    public JFrame getFrame() {
        return getFull_screen_frame() != null ? getFull_screen_frame() : this;
    }

    public void setConta_tiempo_juego(long tiempo_juego) {
        this.conta_tiempo_juego = tiempo_juego;
    }

    public JMenuItem getJugadas_menu() {
        return jugadas_menu;
    }

    public JMenuItem getExit_menu() {
        return exit_menu;
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

    public void toggleFullScreen() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (!full_screen) {

                    if (Helpers.OSValidator.isWindows()) {
                        setVisible(false);
                        getContentPane().remove(GameFrame.getInstance().getTapete());
                        full_screen_frame = new JFrame();
                        full_screen_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                        full_screen_frame.addWindowListener(new java.awt.event.WindowAdapter() {
                            @Override
                            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                                GameFrame.getInstance().closeWindow();
                            }
                        });
                        full_screen_frame.setTitle(GameFrame.getInstance().getTitle());
                        full_screen_frame.setUndecorated(true);
                        full_screen_frame.getContentPane().add(GameFrame.getInstance().getTapete());
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

                        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        GraphicsDevice device = env.getDefaultScreenDevice();
                        menu_bar.setVisible(false);
                        setVisible(false);
                        device.setFullScreenWindow(GameFrame.getInstance());
                    }

                } else {

                    if (Helpers.OSValidator.isWindows()) {

                        full_screen_frame.getContentPane().remove(GameFrame.getInstance().getTapete());
                        full_screen_frame.setVisible(false);
                        full_screen_frame.dispose();
                        full_screen_frame = null;

                        GameFrame.getInstance().getContentPane().add(GameFrame.getInstance().getTapete());
                        GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                        GameFrame.getInstance().setVisible(true);

                        if (timba_pausada) {

                            pausa_dialog.setVisible(false);
                            pausa_dialog.dispose();
                            pausa_dialog = new PauseDialog(GameFrame.getInstance(), false);
                            pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                            pausa_dialog.setVisible(true);
                        }

                    } else {

                        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        GraphicsDevice device = env.getDefaultScreenDevice();
                        device.setFullScreenWindow(null);
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

    public JMenuItem getAuto_zoom_menu() {
        return auto_zoom_menu;
    }

    public void cambiarBaraja() {

        Card.updateCachedImages(1f + GameFrame.getZoom_level() * GameFrame.getZOOM_STEP(), true);

        Helpers.playWavResource("misc/uncover.wav", false);

        Player[] players = tapete.getPlayers();

        for (Player jugador : players) {

            jugador.getPlayingCard1().refreshCard();
            jugador.getPlayingCard2().refreshCard();
        }

        for (Card carta : this.tapete.getCommunityCards().getCartasComunes()) {
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

        Helpers.playWavResource("misc/uncover.wav", false);

        RemotePlayer[] players = tapete.getRemotePlayers();

        for (RemotePlayer jugador : players) {

            jugador.getPlayingCard1().refreshCard();
            jugador.getPlayingCard2().refreshCard();
        }

        for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
            carta.refreshCard();
        }
    }

    public boolean isGame_over_dialog() {
        return game_over_dialog;
    }

    public boolean isTimba_pausada() {
        return timba_pausada;
    }

    public void pauseTimba(String user) {

        synchronized (lock_pause) {

            if (isPartida_local()) {

                getCrupier().broadcastGAMECommandFromServer("PAUSE#" + (this.timba_pausada ? "0" : "1"), user);

            } else if (getNick_local().equals(user)) {

                getCrupier().sendGAMECommandToServer("PAUSE#" + (this.timba_pausada ? "0" : "1"));

            }

            this.timba_pausada = !this.timba_pausada;

            if (this.timba_pausada) {
                this.nick_pause = user != null ? user : this.getNick_local();
                Helpers.playWavResource("misc/pause.wav");
            } else {
                this.nick_pause = null;
            }

            this.lock_pause.notifyAll();

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    if (pausa_dialog == null) {
                        pausa_dialog = new PauseDialog(getFrame(), false);
                    }

                    if (timba_pausada) {

                        if (isPartida_local() || getNick_local().equals(user)) {
                            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("CONTINUAR"));
                            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(true);

                        } else {
                            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled(false);
                        }

                        pausa_dialog.setLocationRelativeTo(pausa_dialog.getParent());
                        pausa_dialog.setVisible(true);

                    } else {

                        if (isPartida_local()) {
                            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("PAUSAR"));
                        } else {
                            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setText(Translator.translate("PAUSAR") + " (" + getLocalPlayer().getPause_counter() + ")");
                        }

                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setEnabled((isPartida_local() || getLocalPlayer().getPause_counter() > 0));

                        pausa_dialog.setVisible(false);
                        pausa_dialog.dispose();
                        pausa_dialog = null;

                    }

                }
            });

        }

    }

    public FastChatDialog getFastchat_dialog() {
        return fastchat_dialog;
    }

    public void setGame_over_dialog(boolean game_over_dialog) {
        this.game_over_dialog = game_over_dialog;
    }

    public boolean checkPause() {

        boolean paused = false;

        while (this.timba_pausada || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            paused = true;

            synchronized (this.lock_pause) {
                try {
                    this.lock_pause.wait(GameFrame.WAIT_PAUSE);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
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
        GameFrame.RECOVER = RECOVER;
    }

    public JMenuItem getShortcuts_menu() {
        return shortcuts_menu;
    }

    public JMenu getFile_menu() {
        return file_menu;
    }

    public JMenu getHelp_menu() {
        return help_menu;
    }

    public JMenu getOpciones_menu() {
        return opciones_menu;
    }

    public JMenu getZoom_menu() {
        return zoom_menu;
    }

    private void setupGlobalShortcuts() {

        HashMap<KeyStroke, Action> actionMap = new HashMap<>();

        KeyStroke key_pause = KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK);
        actionMap.put(key_pause, new AbstractAction("PAUSE") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
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

        KeyStroke key_zoom_auto = KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK);
        actionMap.put(key_zoom_auto, new AbstractAction("ZOOM-AUTO") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (auto_zoom_menu.isEnabled()) {
                    auto_zoom_menuActionPerformed(e);
                }
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

        KeyStroke key_fast_chat = KeyStroke.getKeyStroke('º');
        actionMap.put(key_fast_chat, new AbstractAction("FASTCHAT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!fastchat_dialog.isVisible()) {
                    fastchat_dialog.showDialog(getFrame());
                } else {
                    fastchat_dialog.setVisible(false);
                }

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

        KeyStroke key_fold = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        actionMap.put(key_fold, new AbstractAction("FOLD-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_fold().doClick();
                }
            }
        });

        KeyStroke key_check = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
        actionMap.put(key_check, new AbstractAction("CHECK-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                        getLocalPlayer().getPlayer_allin().doClick();

                    } else {
                        getLocalPlayer().getPlayer_check().doClick();
                    }
                }
            }
        });

        KeyStroke key_bet = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        actionMap.put(key_bet, new AbstractAction("BET-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_bet_button().doClick();
                }
            }
        });

        KeyStroke key_allin = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK);
        actionMap.put(key_allin, new AbstractAction("ALLIN-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano() && !GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        });

        KeyStroke key_bet_left = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
        actionMap.put(key_bet_left, new AbstractAction("BET-LEFT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {

                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();

                        if (model.getPreviousValue() != null) {

                            getLocalPlayer().getBet_spinner().setValue(model.getPreviousValue());
                        }
                    }
                }
            }
        });

        KeyStroke key_bet_down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
        actionMap.put(key_bet_down, new AbstractAction("BET-DOWN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getPreviousValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getPreviousValue());
                        }
                    }
                }
            }
        });

        KeyStroke key_bet_right = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0);
        actionMap.put(key_bet_right, new AbstractAction("BET-RIGHT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getNextValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getNextValue());
                        }
                    }
                }
            }
        });

        KeyStroke key_bet_up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
        actionMap.put(key_bet_up, new AbstractAction("BET-UP") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    if (getLocalPlayer().getBet_spinner().isEnabled()) {
                        SpinnerNumberModel model = (SpinnerNumberModel) getLocalPlayer().getBet_spinner().getModel();
                        if (model.getNextValue() != null) {
                            getLocalPlayer().getBet_spinner().setValue(model.getNextValue());
                        }
                    }
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        if (GameFrame.key_event_dispatcher != null) {
            kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
        }

        GameFrame.key_event_dispatcher = new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

                JFrame frame = GameFrame.getInstance().getFrame();

                if (actionMap.containsKey(keyStroke) && !file_menu.isSelected() && !zoom_menu.isSelected() && !opciones_menu.isSelected() && !help_menu.isSelected() && (frame.isActive() || (pausa_dialog != null && pausa_dialog.hasFocus()))) {
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
        };

        kfm.addKeyEventDispatcher(GameFrame.key_event_dispatcher);
    }

    private WaitingRoomFrame sala_espera;

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
        return this.sala_espera.getParticipantes();
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
        return tapete.getCommunityCards().getCartasComunes();
    }

    public static int getZoom_level() {
        return ZOOM_LEVEL;
    }

    public void setTapeteMano(int mano) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getHand_label().setText("#" + String.valueOf(mano) + (GameFrame.MANOS != -1 ? "/" + String.valueOf(GameFrame.MANOS) : ""));

                if (GameFrame.MANOS != -1 && crupier.getMano() > GameFrame.MANOS) {
                    tapete.getCommunityCards().getHand_label().setBackground(Color.red);
                    tapete.getCommunityCards().getHand_label().setForeground(Color.WHITE);
                    tapete.getCommunityCards().getHand_label().setOpaque(true);
                } else if (GameFrame.MANOS == -1 && tapete.getCommunityCards().getHand_label().getBackground() == Color.RED) {
                    tapete.getCommunityCards().getHand_label().setOpaque(false);
                    tapete.getCommunityCards().getHand_label().setForeground(tapete.getCommunityCards().getColor_contadores());
                }
            }
        });
    }

    public void zoom(float factor, final ConcurrentLinkedQueue<String> notifier) {

        final ConcurrentLinkedQueue<String> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomeable : zoomables) {
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoomeable.zoom(factor, mynotifier);

                }
            });
        }

        while (mynotifier.size() < zoomables.length) {

            synchronized (mynotifier) {

                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().getName());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }

    public void setTapeteBote(float bote, Float beneficio) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getPot_label().setText(Translator.translate("Bote: ") + Helpers.float2String(bote) + (beneficio != null ? " (" + Helpers.float2String(beneficio) + ")" : ""));
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

    public JCheckBoxMenuItem getTts_menu() {
        return tts_menu;
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

                tapete.getCommunityCards().getBet_label().setText(fase + (Helpers.float1DSecureCompare(0f, apuestas) < 0 ? Helpers.float2String(apuestas) : "---"));

                tapete.getCommunityCards().getBet_label().setVisible(true);
            }
        });

    }

    public void refreshTapete() {

        TablePanel nuevo_tapete = TablePanelFactory.downgradePanel(tapete);

        if (nuevo_tapete != null) {

            GameFrame.getInstance().getJugadores().clear();

            for (Player jugador : nuevo_tapete.getPlayers()) {
                GameFrame.getInstance().getJugadores().add(jugador);
            }

            JFrame frame = getFrame();

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {
                    frame.getContentPane().remove(tapete);

                    tapete = nuevo_tapete;

                    zoomables = new ZoomableInterface[]{tapete};

                    frame.getContentPane().add(tapete);

                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    updateSoundIcon();

                    switch (GameFrame.COLOR_TAPETE) {

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

                    Helpers.TapetePopupMenu.addTo(tapete);

                    setupGlobalShortcuts();

                    Helpers.loadOriginalFontSizes(frame);

                    Helpers.updateFonts(frame, Helpers.GUI_FONT, null);

                    Helpers.translateComponents(frame, false);

                    if (GameFrame.getZoom_level() != 0) {

                        GameFrame.getInstance().zoom(1f + GameFrame.getZoom_level() * GameFrame.ZOOM_STEP, null);

                    }

                    pack();

                }
            });
        }
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
                tapete.getCommunityCards().getBlinds_label().setText(Helpers.float2String(pequeña) + " / " + Helpers.float2String(grande) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") + (crupier.getCiegas_double() > 0 ? " (" + String.valueOf(crupier.getCiegas_double()) + ")" : "") : ""));
            }
        });

    }

    public WaitingRoomFrame getSala_espera() {
        return sala_espera;
    }

    public void updateSoundIcon() {

        if (tapete.getCommunityCards().getPot_label().getHeight() > 0) {

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(tapete.getCommunityCards().getPot_label().getHeight(), tapete.getCommunityCards().getPot_label().getHeight()));
                    tapete.getCommunityCards().getSound_icon().setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(tapete.getCommunityCards().getPot_label().getHeight(), tapete.getCommunityCards().getPot_label().getHeight(), Image.SCALE_SMOOTH)));
                }
            });
        } else {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH));
                    tapete.getCommunityCards().getSound_icon().setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH, Image.SCALE_SMOOTH)));
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

                    GameFrame.BARAJA = menu_item.getText();

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
    public GameFrame(WaitingRoomFrame salaespera, String nicklocal, boolean partidalocal) {

        THIS = this;

        sala_espera = salaespera; //Esto aquí arriba para que no pete getParticipantes()

        nick_local = nicklocal;

        partida_local = partidalocal;

        tapete = TablePanelFactory.getPanel(getParticipantes().size());

        Player[] players = tapete.getPlayers();

        Map<String, Object[][]> map = Init.MOD != null ? Map.ofEntries(Crupier.ALLIN_CINEMATICS_MOD) : Map.ofEntries(Crupier.ALLIN_CINEMATICS);

        zoomables = new ZoomableInterface[]{tapete};

        jugadores = new ArrayList<>();

        for (int j = 0; j < getParticipantes().size(); j++) {
            jugadores.add(players[j]);
        }

        for (Map.Entry<String, Participant> entry : getParticipantes().entrySet()) {

            Participant p = entry.getValue();

            if (p != null) {

                if (p.getAvatar() != null) {
                    nick2avatar.put(entry.getKey(), p.getAvatar().getAbsolutePath());
                } else if (partidalocal && p.isCpu()) {
                    nick2avatar.put(entry.getKey(), "*");
                } else {
                    nick2avatar.put(entry.getKey(), "");
                }

            } else {

                nick2avatar.put(entry.getKey(), sala_espera.getLocal_avatar() != null ? sala_espera.getLocal_avatar().getAbsolutePath() : "");
            }
        }

        crupier = new Crupier();

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate(" - Timba en curso (") + nicklocal + ")");

        getContentPane().add(tapete);

        rebuy_now_menu.setEnabled(GameFrame.REBUY);

        compact_menu.setSelected(GameFrame.VISTA_COMPACTA);

        if (!map.containsKey("allin/") || map.get("allin/").length == 0) {
            GameFrame.CINEMATICAS = false;
            menu_cinematicas.setSelected(false);
            menu_cinematicas.setEnabled(false);

        } else {
            menu_cinematicas.setSelected(GameFrame.CINEMATICAS);
        }

        last_hand_menu.setSelected(false);

        rebuy_now_menu.setSelected(false);

        animacion_menu.setSelected(GameFrame.ANIMACION_REPARTIR);

        confirmar_menu.setSelected(GameFrame.CONFIRM_ACTIONS);

        auto_action_menu.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        sonidos_menu.setSelected(GameFrame.SONIDOS);

        sonidos_chorra_menu.setSelected(GameFrame.SONIDOS_CHORRA);

        ascensor_menu.setSelected(GameFrame.MUSICA_AMBIENTAL);

        sonidos_chorra_menu.setEnabled(sonidos_menu.isSelected());

        ascensor_menu.setEnabled(sonidos_menu.isSelected());

        tts_menu.setSelected(GameFrame.SONIDOS_TTS);

        tts_menu.setEnabled(sonidos_menu.isSelected());

        generarBarajasMenu();

        for (Component menu : menu_barajas.getMenuComponents()) {

            if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
            }
        }

        menu_tapete_verde.setSelected(GameFrame.COLOR_TAPETE.equals("verde"));

        menu_tapete_azul.setSelected(GameFrame.COLOR_TAPETE.equals("azul"));

        menu_tapete_rojo.setSelected(GameFrame.COLOR_TAPETE.equals("rojo"));

        menu_tapete_madera.setSelected(GameFrame.COLOR_TAPETE.equals("madera"));

        switch (GameFrame.COLOR_TAPETE) {

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

        if (!isPartida_local()) {
            tapete.getCommunityCards().getPause_button().setText(Translator.translate("PAUSAR") + " (" + getLocalPlayer().getPause_counter() + ")");
        } else {
            tapete.getCommunityCards().getPause_button().setText(Translator.translate("PAUSAR"));
        }

        full_screen_menu.setEnabled(true);

        updateSoundIcon();

        tapete.getCommunityCards().getBarra_tiempo().setMinimum(0);

        tapete.getCommunityCards().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);

        server_separator_menu.setVisible(partida_local);

        tapete.getCommunityCards().getTiempo_partida().setVisible(GameFrame.SHOW_CLOCK);

        time_menu.setSelected(GameFrame.SHOW_CLOCK);

        tapete.getLocalPlayer().getPlayingCard1().setCompactable(false);
        tapete.getLocalPlayer().getPlayingCard2().setCompactable(false);

        //Metemos la pasta a todos (el BUY IN se podría parametrizar)
        for (Player jugador : jugadores) {
            jugador.setStack(GameFrame.BUYIN);
        }

        setupGlobalShortcuts();

        Helpers.TapetePopupMenu.addTo(tapete);

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(GameFrame.REBUY);

        Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(true);

        for (Component menu : BARAJAS_MENU.getMenuComponents()) {

            if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
            }
        }

        Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(GameFrame.COLOR_TAPETE.equals("verde"));

        Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(GameFrame.COLOR_TAPETE.equals("azul"));

        Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(GameFrame.COLOR_TAPETE.equals("rojo"));

        Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(GameFrame.COLOR_TAPETE.equals("madera"));

        Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(false);

        if (!partida_local) {
            last_hand_menu.setEnabled(false);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setEnabled(false);
            max_hands_menu.setEnabled(false);
            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setEnabled(false);
        }

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

    public JMenuItem getMax_hands_menu() {
        return max_hands_menu;
    }

    public long getConta_tiempo_juego() {
        return conta_tiempo_juego;
    }

    public void finTransmision(boolean partida_terminada) {

        synchronized (lock_fin) {

            getCrupier().setFin_de_la_transmision(true);

            if (Helpers.TTS_PLAYER != null) {
                try {
                    // TODO add your handling code here:
                    Helpers.TTS_PLAYER.stop();
                } catch (Exception ex) {
                    Logger.getLogger(TTSNotifyDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            Helpers.muteAllWav();

            GameFrame.getInstance().getTapete().hideALL();

            if (this.getLocalPlayer().getAuto_action() != null) {
                this.getLocalPlayer().getAuto_action().stop();
            }

            if (this.getLocalPlayer().getHurryup_timer() != null) {
                this.getLocalPlayer().getHurryup_timer().stop();
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    if (pausa_dialog != null) {
                        pausa_dialog.setVisible(false);
                    }

                    GameFrame.getInstance().getFastchat_dialog().setVisible(false);

                    exit_menu.setEnabled(false);

                    menu_bar.setVisible(false);
                }
            });

            if (partida_terminada) {

                getRegistro().print("\n*************** LA TIMBA HA TERMINADO ***************");

                getRegistro().print(Translator.translate("FIN DE LA TIMBA -> ") + Helpers.getFechaHoraActual() + " (" + Helpers.seconds2FullTime(conta_tiempo_juego) + ")");

                PreparedStatement statement;

                try {
                    statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET end=? WHERE id=?");
                    statement.setQueryTimeout(30);
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setLong(2, crupier.getSqlite_game_id());
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

            synchronized (crupier.getLock_contabilidad()) {

                crupier.auditorCuentas();

                for (Map.Entry<String, Float[]> entry : crupier.getAuditor().entrySet()) {

                    Float[] pasta = entry.getValue();

                    String ganancia_msg = "";

                    float ganancia = Helpers.floatClean1D(Helpers.floatClean1D(pasta[0]) - Helpers.floatClean1D(pasta[1]));

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

            String log_file = Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss") + ".log";

            try {
                Files.writeString(Paths.get(log_file), getRegistro().getText());
            } catch (IOException ex1) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
            }

            String chat_file = Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss") + ".log";

            try {
                Files.writeString(Paths.get(chat_file), this.getSala_espera().getChat().getText());
            } catch (IOException ex1) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
            }

            if (partida_terminada) {

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        BalanceDialog balance = new BalanceDialog(GameFrame.getInstance().getFrame(), true);

                        balance.setLocationRelativeTo(balance.getParent());

                        balance.setVisible(true);
                    }
                });
            }

            Helpers.SQLITEVAC();

            Helpers.forceCloseSQLITE();

            if (isPartida_local() && getSala_espera().isUpnp()) {
                Helpers.UPnPClose(getSala_espera().getServer_port());
            }

            if (partida_terminada && GameFrame.CINEMATICAS) {

                HashMap<KeyStroke, Action> actionMap = new HashMap<>();

                KeyStroke key_exit = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
                actionMap.put(key_exit, new AbstractAction("EXIT") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.exit(0);
                    }
                });

                KeyStroke key_exit2 = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
                actionMap.put(key_exit2, new AbstractAction("EXIT2") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.exit(0);
                    }
                });

                KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

                if (GameFrame.key_event_dispatcher != null) {
                    kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
                }

                GameFrame.key_event_dispatcher = new KeyEventDispatcher() {

                    @Override
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

                        if (actionMap.containsKey(keyStroke)) {
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
                };

                kfm.addKeyEventDispatcher(GameFrame.key_event_dispatcher);

                final ImageIcon icon;

                if (Init.MOD != null && Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/misc/end.gif"))) {
                    icon = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/cinematics/misc/end.gif");
                } else if (getClass().getResource("/cinematics/misc/end.gif") != null) {
                    icon = new ImageIcon(getClass().getResource("/cinematics/misc/end.gif"));
                } else {
                    icon = null;
                }

                if (icon != null) {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            gif_dialog = new GifAnimationDialog(GameFrame.getInstance().getFrame(), true, icon);
                            gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                            gif_dialog.setVisible(true);
                        }
                    });
                }

                Helpers.muteAllLoopMp3();

                Helpers.playWavResourceAndWait("misc/end.wav");
            }

            System.exit(0); //No hay otra
        }
    }

    public Timer getTiempo_juego() {
        return tiempo_juego;
    }

    public void AJUGAR() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                registro_dialog = new GameLogDialog(GameFrame.getInstance(), false);

                fastchat_dialog = new FastChatDialog(GameFrame.getInstance(), false);
            }
        });

        TTSWatchdog();

        Helpers.threadRun(crupier);

        tiempo_juego = new Timer(1000, new ActionListener() {

            public void actionPerformed(ActionEvent ae) {

                if (!crupier.isFin_de_la_transmision() && !crupier.isPlayerTimeout() && !crupier.isShow_time() && !crupier.isRebuy_time() && !isTimba_pausada() && !isRECOVER()) {
                    String tiempo_juego = Helpers.seconds2FullTime(++conta_tiempo_juego);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            tapete.getCommunityCards().getTiempo_partida().setText(tiempo_juego);
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
        });

        tiempo_juego.start();

        getRegistro().print(Translator.translate("COMIENZA LA TIMBA -> ") + Helpers.getFechaHoraActual());
    }

    private void TTSWatchdog() {

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                while (!crupier.isFin_de_la_transmision()) {

                    while (!Helpers.TTS_CHAT_QUEUE.isEmpty()) {

                        Object[] tts = Helpers.TTS_CHAT_QUEUE.poll();

                        Helpers.GUIRunAndWait(new Runnable() {
                            @Override
                            public void run() {
                                nick_dialog = new TTSNotifyDialog(GameFrame.getInstance().getFrame(), false, (String) tts[0]);
                                nick_dialog.setLocation(nick_dialog.getParent().getLocation());

                            }
                        });

                        if (GameFrame.SONIDOS && GameFrame.SONIDOS_TTS && GameFrame.TTS_SERVER && !Helpers.TTS_BLOCKED_USERS.contains((String) tts[0])) {

                            Helpers.TTS((String) tts[1], nick_dialog);

                        } else if (GameFrame.SONIDOS_TTS && GameFrame.TTS_SERVER && !Helpers.TTS_BLOCKED_USERS.contains((String) tts[0])) {

                            Helpers.GUIRunAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    nick_dialog.setVisible(true);
                                }
                            });

                            Helpers.pausar(1000);

                            Helpers.GUIRunAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    nick_dialog.setVisible(false);
                                }
                            });
                        }
                    }

                    synchronized (Helpers.TTS_CHAT_QUEUE) {

                        try {
                            Helpers.TTS_CHAT_QUEUE.wait(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }
            }
        });

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
        server_separator_menu = new javax.swing.JPopupMenu.Separator();
        full_screen_menu = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        last_hand_menu = new javax.swing.JCheckBoxMenuItem();
        max_hands_menu = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        exit_menu = new javax.swing.JMenuItem();
        zoom_menu = new javax.swing.JMenu();
        zoom_menu_in = new javax.swing.JMenuItem();
        zoom_menu_out = new javax.swing.JMenuItem();
        zoom_menu_reset = new javax.swing.JMenuItem();
        auto_zoom_menu = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        compact_menu = new javax.swing.JCheckBoxMenuItem();
        opciones_menu = new javax.swing.JMenu();
        sonidos_menu = new javax.swing.JCheckBoxMenuItem();
        sonidos_chorra_menu = new javax.swing.JCheckBoxMenuItem();
        ascensor_menu = new javax.swing.JCheckBoxMenuItem();
        tts_menu = new javax.swing.JCheckBoxMenuItem();
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
        rebuy_now_menu = new javax.swing.JCheckBoxMenuItem();
        help_menu = new javax.swing.JMenu();
        shortcuts_menu = new javax.swing.JMenuItem();
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

        last_hand_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        last_hand_menu.setSelected(true);
        last_hand_menu.setText("Última mano");
        last_hand_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                last_hand_menuActionPerformed(evt);
            }
        });
        file_menu.add(last_hand_menu);

        max_hands_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        max_hands_menu.setText("Límite de manos");
        max_hands_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                max_hands_menuActionPerformed(evt);
            }
        });
        file_menu.add(max_hands_menu);
        file_menu.add(jSeparator3);

        exit_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        exit_menu.setText("SALIR (ALT+F4)");
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

        auto_zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_zoom_menu.setText("AUTO-AJUSTE (CTRL+A)");
        auto_zoom_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_zoom_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(auto_zoom_menu);
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
        sonidos_chorra_menu.setText("Sonidos de coña");
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

        tts_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        tts_menu.setSelected(true);
        tts_menu.setText("TTS");
        tts_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tts_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(tts_menu);
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

        rebuy_now_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_now_menu.setSelected(true);
        rebuy_now_menu.setText("Recomprar (siguiente mano)");
        rebuy_now_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_now_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(rebuy_now_menu);

        menu_bar.add(opciones_menu);

        help_menu.setText("Ayuda");
        help_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        shortcuts_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        shortcuts_menu.setText("ATAJOS");
        shortcuts_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shortcuts_menuActionPerformed(evt);
            }
        });
        help_menu.add(shortcuts_menu);

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

        if (getLocalPlayer().isExit() && Helpers.mostrarMensajeInformativoSINO(THIS, "¿FORZAR CIERRE?") == 0) {
            System.exit(1);
        }

        if (this.isPartida_local()) {

            if (jugadores.size() > 1) {

                // 0=yes, 1=no, 2=cancel
                if (Helpers.mostrarMensajeInformativoSINO(this, "¡CUIDADO! ERES EL ANFITRIÓN Y SI SALES SE TERMINARÁ LA TIMBA. ¿ESTÁS SEGURO?") == 0) {

                    getLocalPlayer().setExit();

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

                        getLocalPlayer().setExit();

                        finTransmision(true);
                    }
                });
            }

        } else {
            // 0=yes, 1=no, 2=cancel
            if (Helpers.mostrarMensajeInformativoSINO(this, "¡CUIDADO! Si sales de la timba no podrás volver a entrar. ¿ESTÁS SEGURO?") == 0) {

                getLocalPlayer().setExit();

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

        Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                final ConcurrentLinkedQueue<String> mynotifier = new ConcurrentLinkedQueue<>();

                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, mynotifier);

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

                while (mynotifier.size() < 1) {

                    synchronized (mynotifier) {

                        try {
                            mynotifier.wait(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        zoom_menu.setEnabled(true);
                    }
                });

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
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    final ConcurrentLinkedQueue<String> mynotifier = new ConcurrentLinkedQueue<>();

                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP, mynotifier);

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

                    while (mynotifier.size() < 1) {

                        synchronized (mynotifier) {

                            try {
                                mynotifier.wait(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            zoom_menu.setEnabled(true);
                        }
                    });

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
            Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    final ConcurrentLinkedQueue<String> mynotifier = new ConcurrentLinkedQueue<>();

                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP, mynotifier);
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

                    while (mynotifier.size() < 1) {

                        synchronized (mynotifier) {

                            try {
                                mynotifier.wait(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            zoom_menu.setEnabled(true);
                        }
                    });

                }
            });

            Helpers.savePropertiesFile();
        }
    }//GEN-LAST:event_zoom_menu_resetActionPerformed

    private void registro_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_registro_menuActionPerformed
        // TODO add your handling code here:

        this.registro_dialog.setVisible(false);

        this.registro_dialog.setLocationRelativeTo(getFrame());

        this.registro_dialog.setVisible(true);

    }//GEN-LAST:event_registro_menuActionPerformed

    private void chat_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_menuActionPerformed
        // TODO add your handling code here:

        if (!this.sala_espera.isActive()) {
            this.sala_espera.setVisible(false);
        }

        this.sala_espera.setLocationRelativeTo(getFrame());
        this.sala_espera.setExtendedState(JFrame.NORMAL);
        this.sala_espera.setVisible(true);

    }//GEN-LAST:event_chat_menuActionPerformed

    private void sonidos_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sonidos_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.SONIDOS = this.sonidos_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonidos", String.valueOf(GameFrame.SONIDOS));

        Helpers.savePropertiesFile();

        updateSoundIcon();

        this.sonidos_chorra_menu.setEnabled(GameFrame.SONIDOS);

        this.ascensor_menu.setEnabled(GameFrame.SONIDOS);

        if (GameFrame.TTS_SERVER) {
            this.tts_menu.setEnabled(GameFrame.SONIDOS);
        }

        if (!GameFrame.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.unMuteAll();
        }

        Helpers.TapetePopupMenu.SONIDOS_MENU.setSelected(GameFrame.SONIDOS);

        Helpers.TapetePopupMenu.SONIDOS_COMENTARIOS_MENU.setEnabled(GameFrame.SONIDOS);

        Helpers.TapetePopupMenu.SONIDOS_MUSICA_MENU.setEnabled(GameFrame.SONIDOS);

        Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setEnabled(GameFrame.SONIDOS);
    }//GEN-LAST:event_sonidos_menuActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        this.exit_menu.doClick();
    }//GEN-LAST:event_formWindowClosing

    private void time_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_time_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.SHOW_CLOCK = time_menu.isSelected();

        tapete.getCommunityCards().getTiempo_partida().setVisible(time_menu.isSelected());

        Helpers.PROPERTIES.setProperty("show_time", String.valueOf(this.time_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.RELOJ_MENU.setSelected(GameFrame.SHOW_CLOCK);
    }//GEN-LAST:event_time_menuActionPerformed

    private void sonidos_chorra_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sonidos_chorra_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.SONIDOS_CHORRA = this.sonidos_chorra_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonidos_chorra", String.valueOf(this.sonidos_chorra_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.SONIDOS_COMENTARIOS_MENU.setSelected(GameFrame.SONIDOS_CHORRA);

    }//GEN-LAST:event_sonidos_chorra_menuActionPerformed

    private void ascensor_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ascensor_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.MUSICA_AMBIENTAL = this.ascensor_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonido_ascensor", String.valueOf(this.ascensor_menu.isSelected()));

        Helpers.savePropertiesFile();

        if (this.ascensor_menu.isSelected()) {
            Helpers.unmuteLoopMp3("misc/background_music.mp3");
        } else {
            Helpers.muteLoopMp3("misc/background_music.mp3");
        }

        Helpers.TapetePopupMenu.SONIDOS_MUSICA_MENU.setSelected(GameFrame.MUSICA_AMBIENTAL);
    }//GEN-LAST:event_ascensor_menuActionPerformed

    private void jugadas_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jugadas_menuActionPerformed
        // TODO add your handling code here:

        Helpers.threadRun(new Runnable() {
            public void run() {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {

                        if (jugadas_dialog == null) {
                            jugadas_dialog = new HandGeneratorDialog(GameFrame.getInstance(), false);

                            jugadas_dialog.pintarJugada();
                        } else {
                            jugadas_dialog.setVisible(false);
                        }

                    }
                });

                for (Card carta : jugadas_dialog.getCartas()) {
                    carta.refreshCard();
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        jugadas_dialog.pack();

                        jugadas_dialog.setLocationRelativeTo(getFrame());

                        jugadas_dialog.setVisible(true);
                    }
                });

            }
        });
    }//GEN-LAST:event_jugadas_menuActionPerformed

    private void full_screen_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_full_screen_menuActionPerformed
        // TODO add your handling code here:

        if (full_screen_menu.isEnabled() && !isGame_over_dialog()) {

            full_screen_menu.setEnabled(false);

            Helpers.TapetePopupMenu.FULLSCREEN_MENU.setEnabled(false);

            if (!Helpers.OSValidator.isMac() || !GameFrame.MAC_NATIVE_FULLSCREEN) {

                Helpers.TapetePopupMenu.FULLSCREEN_MENU.setSelected(!full_screen);

                toggleFullScreen();

            } else {

                toggleMacNativeFullScreen(GameFrame.getInstance());
            }
        }

    }//GEN-LAST:event_full_screen_menuActionPerformed

    private void compact_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compact_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.VISTA_COMPACTA = this.compact_menu.isSelected();

        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(GameFrame.VISTA_COMPACTA));

        Helpers.savePropertiesFile();

        Helpers.threadRun(new Runnable() {
            public void run() {
                vistaCompacta();
            }
        });

        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(GameFrame.VISTA_COMPACTA);
    }//GEN-LAST:event_compact_menuActionPerformed

    private void confirmar_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_confirmar_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.CONFIRM_ACTIONS = this.confirmar_menu.isSelected();

        Helpers.PROPERTIES.setProperty("confirmar_todo", String.valueOf(GameFrame.CONFIRM_ACTIONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CONFIRM_MENU.setSelected(GameFrame.CONFIRM_ACTIONS);

        if (!GameFrame.CONFIRM_ACTIONS) {
            this.getLocalPlayer().desarmarBotonesAccion();
        }

    }//GEN-LAST:event_confirmar_menuActionPerformed

    private void animacion_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_animacion_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.ANIMACION_REPARTIR = this.animacion_menu.isSelected();

        Helpers.PROPERTIES.setProperty("animacion_reparto", String.valueOf(GameFrame.ANIMACION_REPARTIR));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.ANIMACION_MENU.setSelected(GameFrame.ANIMACION_REPARTIR);
    }//GEN-LAST:event_animacion_menuActionPerformed

    private void auto_action_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_action_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.AUTO_ACTION_BUTTONS = this.auto_action_menu.isSelected();

        Helpers.PROPERTIES.setProperty("auto_action_buttons", String.valueOf(GameFrame.AUTO_ACTION_BUTTONS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTO_ACTION_MENU.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        if (GameFrame.AUTO_ACTION_BUTTONS) {
            this.getLocalPlayer().activarPreBotones();
        } else {
            this.getLocalPlayer().desActivarPreBotones();
        }
    }//GEN-LAST:event_auto_action_menuActionPerformed

    private void menu_tapete_verdeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_verdeActionPerformed
        // TODO add your handling code here:
        GameFrame.COLOR_TAPETE = "verde";

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

        GameFrame.COLOR_TAPETE = "azul";

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
        GameFrame.COLOR_TAPETE = "rojo";

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
        GameFrame.COLOR_TAPETE = "madera";

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

        GameFrame.CINEMATICAS = this.menu_cinematicas.isSelected();

        Helpers.PROPERTIES.setProperty("cinematicas", String.valueOf(GameFrame.CINEMATICAS));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.CINEMATICAS_MENU.setSelected(GameFrame.CINEMATICAS);
    }//GEN-LAST:event_menu_cinematicasActionPerformed

    private void shortcuts_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shortcuts_menuActionPerformed
        // TODO add your handling code here:
        Helpers.mostrarMensajeInformativo(getFrame(), Translator.translate("PASAR/IR -> [ESPACIO]\n\nAPOSTAR -> [ENTER] (FLECHA ARRIBA/ABAJO PARA SUBIR/BAJAR APUESTA)\n\nALL IN -> [MAYUS + ENTER]\n\nNO IR -> [ESC]\n\nMOSTRAR CARTAS -> [ESPACIO]\n\nMENSAJE CHAT RÁPIDO -> [º]"));

    }//GEN-LAST:event_shortcuts_menuActionPerformed

    private void tts_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tts_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.SONIDOS_TTS = this.tts_menu.isSelected();

        Helpers.PROPERTIES.setProperty("sonidos_tts", String.valueOf(this.tts_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setSelected(GameFrame.SONIDOS_TTS);

        if (GameFrame.SONIDOS_TTS) {

            Helpers.TTS_BLOCKED_USERS.clear();

            if (!GameFrame.TTS_SERVER && GameFrame.getInstance().isPartida_local()) {

                GameFrame.TTS_SERVER = true;

                tts_menu.setEnabled(false);

                TTSNotifyDialog dialog = new TTSNotifyDialog(GameFrame.getInstance().getFrame(), false, true);

                dialog.setLocation(dialog.getParent().getLocation());

                dialog.setVisible(true);

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        getCrupier().broadcastGAMECommandFromServer("TTS#1", null);

                        Helpers.GUIRun(new Runnable() {
                            public void run() {

                                tts_menu.setEnabled(true);

                                tts_menu.setOpaque(false);

                                tts_menu.setBackground(null);

                                Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setOpaque(false);

                                Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setBackground(null);

                            }
                        });

                    }
                });

            }

        } else if (GameFrame.getInstance().isPartida_local() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿DESACTIVAR EL CHAT DE VOZ PARA TODOS?") == 0) {

            GameFrame.TTS_SERVER = false;

            tts_menu.setEnabled(false);

            TTSNotifyDialog dialog = new TTSNotifyDialog(GameFrame.getInstance().getFrame(), false, false);

            dialog.setLocation(dialog.getParent().getLocation());

            dialog.setVisible(true);

            Helpers.threadRun(new Runnable() {
                public void run() {

                    getCrupier().broadcastGAMECommandFromServer("TTS#0", null);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            tts_menu.setEnabled(true);

                            tts_menu.setBackground(Color.RED);

                            tts_menu.setOpaque(true);

                            Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setBackground(Color.RED);

                            Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setOpaque(true);
                        }
                    });
                }
            });

        }
    }//GEN-LAST:event_tts_menuActionPerformed

    public RebuyNowDialog getRebuy_dialog() {
        return rebuy_dialog;
    }

    public JCheckBoxMenuItem getLast_hand_menu() {
        return last_hand_menu;
    }

    private void rebuy_now_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_now_menuActionPerformed
        // TODO add your handling code here:

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(this.rebuy_now_menu.isSelected());

        LocalPlayer player = GameFrame.getInstance().getLocalPlayer();

        this.rebuy_now_menu.setEnabled(false);

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(false);

        if (crupier.getRebuy_now().containsKey(player.getNickname())) {

            player.getPlayer_buyin().setBackground(Helpers.float1DSecureCompare((float) GameFrame.BUYIN, player.getBuyin()) == 0 ? new Color(204, 204, 204) : Color.cyan);
            player.getPlayer_buyin().setText(String.valueOf(player.getBuyin()));

            Helpers.threadRun(new Runnable() {
                public void run() {
                    crupier.rebuyNow(player.getNickname(), -1);
                    rebuy_now_menu.setEnabled(true);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                    Helpers.playWavResource("misc/button_off.wav");
                }
            });

        } else {

            if (Helpers.float1DSecureCompare(player.getStack() + (player.getDecision() != Player.FOLD ? player.getBote() : 0f) + player.getPagar(), (float) GameFrame.BUYIN) >= 0) {
                Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), Translator.translate("PARA RECOMPRAR DEBES TENER MENOS DE ") + GameFrame.BUYIN);
                rebuy_now_menu.setEnabled(true);
                rebuy_now_menu.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
            } else {

                rebuy_dialog = new RebuyNowDialog(GameFrame.getInstance().getFrame(), true, true, -1);

                rebuy_dialog.setLocationRelativeTo(rebuy_dialog.getParent());

                rebuy_dialog.setVisible(true);

                if (rebuy_dialog.isRebuy()) {
                    player.getPlayer_buyin().setBackground(Color.YELLOW);
                    player.getPlayer_buyin().setText(String.valueOf(player.getBuyin() + (int) rebuy_dialog.getRebuy_spinner().getValue()));

                    Helpers.threadRun(new Runnable() {
                        public void run() {
                            crupier.rebuyNow(player.getNickname(), (int) rebuy_dialog.getRebuy_spinner().getValue());
                            rebuy_now_menu.setEnabled(true);
                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                            Helpers.playWavResource("misc/button_on.wav");
                            rebuy_dialog = null;
                        }
                    });
                } else {
                    rebuy_now_menu.setEnabled(true);
                    rebuy_now_menu.setSelected(false);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                    Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                    rebuy_dialog = null;
                }
            }

        }

    }//GEN-LAST:event_rebuy_now_menuActionPerformed

    private void last_hand_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_last_hand_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.getInstance().getTapete().getCommunityCards().hand_label_left_click();
    }//GEN-LAST:event_last_hand_menuActionPerformed

    private void max_hands_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_max_hands_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.getInstance().getTapete().getCommunityCards().hand_label_right_click();
    }//GEN-LAST:event_max_hands_menuActionPerformed

    private void auto_zoom_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_zoom_menuActionPerformed
        // TODO add your handling code here:

        auto_zoom_menu.setEnabled(false);

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                if (!tapete.autoZoom(false)) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, "AUTOZOOM TIMEOUT ERROR!");
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        auto_zoom_menu.setEnabled(true);
                    }
                });

            }
        });
    }//GEN-LAST:event_auto_zoom_menuActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem acerca_menu;
    private javax.swing.JCheckBoxMenuItem animacion_menu;
    private javax.swing.JCheckBoxMenuItem ascensor_menu;
    private javax.swing.JCheckBoxMenuItem auto_action_menu;
    private javax.swing.JMenuItem auto_zoom_menu;
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
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JMenuItem jugadas_menu;
    private javax.swing.JCheckBoxMenuItem last_hand_menu;
    private javax.swing.JMenuItem max_hands_menu;
    private javax.swing.JMenuBar menu_bar;
    private javax.swing.JMenu menu_barajas;
    private javax.swing.JCheckBoxMenuItem menu_cinematicas;
    private javax.swing.JRadioButtonMenuItem menu_tapete_azul;
    private javax.swing.JRadioButtonMenuItem menu_tapete_madera;
    private javax.swing.JRadioButtonMenuItem menu_tapete_rojo;
    private javax.swing.JRadioButtonMenuItem menu_tapete_verde;
    private javax.swing.JMenu menu_tapetes;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JCheckBoxMenuItem rebuy_now_menu;
    private javax.swing.JMenuItem registro_menu;
    private javax.swing.JPopupMenu.Separator server_separator_menu;
    private javax.swing.JMenuItem shortcuts_menu;
    private javax.swing.JCheckBoxMenuItem sonidos_chorra_menu;
    private javax.swing.JCheckBoxMenuItem sonidos_menu;
    private javax.swing.JCheckBoxMenuItem time_menu;
    private javax.swing.JCheckBoxMenuItem tts_menu;
    private javax.swing.JMenu zoom_menu;
    private javax.swing.JMenuItem zoom_menu_in;
    private javax.swing.JMenuItem zoom_menu_out;
    private javax.swing.JMenuItem zoom_menu_reset;
    // End of variables declaration//GEN-END:variables
}
