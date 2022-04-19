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

import static com.tonikelope.coronapoker.Helpers.TapetePopupMenu.BARAJAS_MENU;
import static com.tonikelope.coronapoker.Init.M2;

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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

class WheelFrame extends JFrame implements MouseWheelListener {

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        e.consume();

        if (e.isControlDown()) {

            if (e.getWheelRotation() < 0) {
                GameFrame.getInstance().getZoom_menu_in().doClick();
            } else {
                GameFrame.getInstance().getZoom_menu_out().doClick();
            }

        } else if (getParent() != null) {
            getParent().dispatchEvent(e);
        }
    }

}

/**
 *
 * @author tonikelope
 */
public final class GameFrame extends javax.swing.JFrame implements ZoomableInterface, MouseWheelListener {

    public static final int TEST_MODE_PAUSE = 250;
    public static final int DEFAULT_ZOOM_LEVEL = -2;
    public static final float MIN_BIG_BLIND = 0.20f;
    public static final float ZOOM_STEP = 0.05f;
    public static final int PAUSA_ENTRE_MANOS = 10; //Segundos
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
    public static final String BARAJA_DEFAULT = "coronapoker";
    public static final String DEFAULT_LANGUAGE = "es";
    public static final int PEPILLO_COUNTER_MAX = 5;
    public static final int PAUSE_COUNTER_MAX = 3;
    public static final int AUTO_ZOOM_TIMEOUT = 3000;
    public static final int GUI_ZOOM_WAIT = 250;
    public static final boolean TEST_MODE = false;
    public static final int TTS_NO_SOUND_TIMEOUT = 3000;
    public static final ConcurrentLinkedQueue<Object[]> NOTIFY_CHAT_QUEUE = new ConcurrentLinkedQueue<>();

    public static volatile float CIEGA_PEQUEÑA = 0.10f;
    public static volatile float CIEGA_GRANDE = 0.20f;
    public static volatile int BUYIN = 10;
    public static volatile int CIEGAS_DOUBLE = 60;
    public static volatile int CIEGAS_DOUBLE_TYPE = 1; //1 MINUTES, 2 HANDS
    public static volatile boolean REBUY = true;
    public static volatile int MANOS = -1;
    public static volatile boolean SONIDOS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos", "true")) && !TEST_MODE;
    public static volatile boolean SONIDOS_CHORRA = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonidos_chorra", "false"));
    public static volatile boolean SONIDOS_TTS = true;
    public static volatile boolean MUSICA_AMBIENTAL = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("sonido_ascensor", "true"));
    public static volatile boolean SHOW_CLOCK = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("show_time", "false"));
    public static volatile boolean CONFIRM_ACTIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("confirmar_todo", "false")) && !TEST_MODE;
    public static volatile int ZOOM_LEVEL = Integer.parseInt(Helpers.PROPERTIES.getProperty("zoom_level", String.valueOf(GameFrame.DEFAULT_ZOOM_LEVEL)));
    public static volatile String BARAJA = Helpers.PROPERTIES.getProperty("baraja", BARAJA_DEFAULT);
    public static volatile int VISTA_COMPACTA = Integer.parseInt(Helpers.isNumeric(Helpers.PROPERTIES.getProperty("vista_compacta", "0")) ? Helpers.PROPERTIES.getProperty("vista_compacta", "0") : "0") % 3;
    public static volatile boolean ANIMACION_REPARTIR = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("animacion_reparto", "true"));
    public static volatile boolean AUTO_ACTION_BUTTONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_action_buttons", "false")) && !TEST_MODE;
    public static volatile String COLOR_TAPETE = Helpers.PROPERTIES.getProperty("color_tapete", "verde");
    public static volatile String LANGUAGE = Helpers.PROPERTIES.getProperty("lenguaje", "es").toLowerCase();
    public static volatile boolean CINEMATICAS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("cinematicas", "true"));
    public static volatile boolean AUTO_ZOOM = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("auto_zoom", "false"));
    public static volatile boolean LOCAL_POSITION_CHIP = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("local_pos_chip", "true"));
    public static volatile String SERVER_HISTORY = Helpers.PROPERTIES.getProperty("server_history", "");
    public static volatile boolean RECOVER = false;
    public static volatile Boolean MAC_NATIVE_FULLSCREEN = null;
    public static volatile boolean TTS_SERVER = true;
    public static volatile int RECOVER_ID = -1;
    public static volatile String UGI = null;
    public final static int UGI_LENGTH = 50;
    public static volatile long GAME_START_TIMESTAMP;
    public static volatile KeyEventDispatcher key_event_dispatcher = null;
    private static final Object ZOOM_LOCK = new Object();

    private static volatile GameFrame THIS = null;

    public static GameFrame getInstance() {
        return THIS;
    }

    private final Object registro_lock = new Object();
    private final Object full_screen_lock = new Object();
    private final Object lock_pause = new Object();
    private final Object exit_now_lock = new Object();
    private final ArrayList<Player> jugadores;
    private final ConcurrentHashMap<String, String> nick2avatar = new ConcurrentHashMap<>();
    private final Crupier crupier;
    private final boolean partida_local;
    private final String nick_local;
    private final BrightnessLayerUI capa_brillo = new BrightnessLayerUI();

    private volatile ZoomableInterface[] zoomables;
    private volatile long conta_tiempo_juego = 0L;
    private volatile boolean full_screen = false;
    private volatile boolean timba_pausada = false;
    private volatile String nick_pause = null;
    private volatile PauseDialog pausa_dialog = null;
    private volatile boolean game_over_dialog = false;
    private volatile WheelFrame full_screen_frame = null;
    private volatile AboutDialog about_dialog = null;
    private volatile HandGeneratorDialog jugadas_dialog = null;
    private volatile GameLogDialog registro_dialog = null;
    private volatile ShortcutsDialog shortcuts_dialog = null;
    private volatile FastChatDialog fastchat_dialog = null;
    private volatile RebuyDialog rebuy_dialog = null;
    private volatile GifAnimationDialog gif_dialog = null;
    public volatile VolumeControlDialog volume_dialog = null;
    private volatile TablePanel tapete = null;
    private volatile Timer tiempo_juego;
    private volatile int tapete_counter = 0;
    private volatile int i60_c = 0;
    private volatile JLayer<JComponent> frame_layer = null;
    private volatile boolean retry = false;
    private volatile boolean fin = false;
    private volatile InGameNotifyDialog notify_dialog = null;

    public InGameNotifyDialog getNotify_dialog() {
        return notify_dialog;
    }

    public static void resetInstance() {

        if (THIS != THIS.getFrame()) {
            THIS.getFrame().setVisible(false);
            THIS.getFrame().dispose();
        }

        THIS.dispose();

        THIS = null;
    }

    public BrightnessLayerUI getCapa_brillo() {
        return capa_brillo;
    }

    public JMenuItem getRobert_rules_menu() {
        return robert_rules_menu;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        e.consume();

        if (e.isControlDown()) {

            if (e.getWheelRotation() < 0) {
                zoom_menu_in.doClick();
            } else {
                zoom_menu_out.doClick();
            }

        } else if (getParent() != null) {
            getParent().dispatchEvent(e);
        }
    }

    public JCheckBoxMenuItem getAuto_adjust_zoom_menu() {
        return auto_fit_zoom_menu;
    }

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

                                    GameFrame.getInstance().getFrame().requestFocus();
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

                                    GameFrame.getInstance().getFrame().requestFocus();
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

        if (GameFrame.ZOOM_LEVEL != 0) {
            GameFrame.getInstance().zoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);
        }

        if (GameFrame.AUTO_ZOOM) {
            tapete.autoZoom(false);
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

    public JRadioButtonMenuItem getMenu_tapete_negro() {
        return menu_tapete_negro;
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

    private void incrementZoom() {

        synchronized (ZOOM_LOCK) {
            ZOOM_LEVEL++;
        }
    }

    private void decrementZoom() {
        synchronized (ZOOM_LOCK) {
            ZOOM_LEVEL--;
        }
    }

    public void toggleFullScreen() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                full_screen = !full_screen;

                if (full_screen) {

                    if (Helpers.OSValidator.isWindows()) {
                        setVisible(false);
                        getContentPane().remove(frame_layer);
                        full_screen_frame = new WheelFrame();
                        full_screen_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                        full_screen_frame.addMouseWheelListener(full_screen_frame);
                        full_screen_frame.addWindowListener(new java.awt.event.WindowAdapter() {
                            @Override
                            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                                GameFrame.getInstance().closeWindow();
                            }
                        });
                        full_screen_frame.setTitle(GameFrame.getInstance().getTitle());
                        full_screen_frame.setUndecorated(true);
                        frame_layer = new JLayer<>(GameFrame.getInstance().getTapete(), capa_brillo);
                        full_screen_frame.getContentPane().add(frame_layer);
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

                        frame_layer = new JLayer<>(GameFrame.getInstance().getTapete(), capa_brillo);
                        GameFrame.getInstance().getContentPane().add(frame_layer);
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

                synchronized (full_screen_lock) {
                    full_screen_lock.notifyAll();
                }

                GameFrame.getInstance().getFrame().requestFocus();
            }
        });

    }

    public void cambiarBaraja() {

        Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

        Audio.playWavResource("misc/uncover.wav", false);

        Player[] players = tapete.getPlayers();

        for (Player jugador : players) {

            jugador.getPlayingCard1().invalidateImagePrecache();
            jugador.getPlayingCard1().refreshCard();

            jugador.getPlayingCard2().invalidateImagePrecache();
            jugador.getPlayingCard2().refreshCard();
        }

        for (Card carta : this.tapete.getCommunityCards().getCartasComunes()) {
            carta.invalidateImagePrecache();
            carta.refreshCard();
        }

        if (this.jugadas_dialog != null && this.jugadas_dialog.isVisible()) {
            for (Card carta : this.jugadas_dialog.getCartas()) {
                carta.invalidateImagePrecache();
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

        RemotePlayer[] players = tapete.getRemotePlayers();

        final ConcurrentLinkedQueue<Long> notifier = new ConcurrentLinkedQueue<>();

        for (RemotePlayer jugador : players) {

            jugador.getPlayingCard1().refreshCard(true, notifier);
            jugador.getPlayingCard2().refreshCard(true, notifier);
        }

        for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
            carta.refreshCard();
        }

        while (notifier.size() < players.length * 2) {
            synchronized (notifier) {
                try {
                    notifier.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        for (RemotePlayer jugador : players) {

            jugador.refreshSecPotLabel();
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

                if (!GameFrame.getInstance().getCrupier().isIwtsthing()) {
                    Audio.playWavResource("misc/pause.wav");
                }
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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("QUIT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameFrame.getInstance().getExit_menu().doClick();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), new AbstractAction("BUYIN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameFrame.getInstance().getLocalPlayer().player_stack_click();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK), new AbstractAction("PAUSE") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_DOWN_MASK), new AbstractAction("LIGHTS") {
            @Override
            public void actionPerformed(ActionEvent e) {
                GameFrame.getInstance().getTapete().getCommunityCards().lightsButtonClick();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK), new AbstractAction("FULL-SCREEN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                full_screen_menuActionPerformed(e);
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.ALT_DOWN_MASK), new AbstractAction("COMPACT-CARDS") {
            @Override
            public void actionPerformed(ActionEvent e) {
                compact_menu.doClick();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-IN") {
            @Override
            public void actionPerformed(ActionEvent e) {

                zoom_menu_inActionPerformed(e);

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-OUT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                zoom_menu_outActionPerformed(e);

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("ZOOM-RESET") {
            @Override
            public void actionPerformed(ActionEvent e) {

                zoom_menu_resetActionPerformed(e);

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_DOWN_MASK), new AbstractAction("CHAT") {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat_menuActionPerformed(e);
            }
        });

        actionMap.put(KeyStroke.getKeyStroke('º'), new AbstractAction("FASTCHAT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (fastchat_dialog != null) {

                    FastChatDialog old_dialog = fastchat_dialog;

                    fastchat_dialog = new FastChatDialog(getFrame(), false, fastchat_dialog.getChat_box());

                    old_dialog.dispose();

                } else {
                    fastchat_dialog = new FastChatDialog(getFrame(), false, null);
                }

                fastchat_dialog.setLocation(getFrame().getX(), getFrame().getY() + getFrame().getHeight() - fastchat_dialog.getHeight());

                fastchat_dialog.setVisible(true);

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), new AbstractAction("FASTCHAT-IMAGE") {
            @Override
            public void actionPerformed(ActionEvent e) {

                ChatImageDialog chat_image_dialog = new ChatImageDialog(getFrame(), true, getFrame().getHeight());
                chat_image_dialog.setLocation((int) (getFrame().getLocation().getX() + getFrame().getWidth()) - chat_image_dialog.getWidth(), (int) getFrame().getLocation().getY());
                chat_image_dialog.setVisible(true);

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK), new AbstractAction("REGISTRO") {
            @Override
            public void actionPerformed(ActionEvent e) {
                registro_menuActionPerformed(e);
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), new AbstractAction("RELOJ") {
            @Override
            public void actionPerformed(ActionEvent e) {
                time_menu.doClick();
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new AbstractAction("FOLD-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_fold().doClick();
                }
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), new AbstractAction("CHECK-BUTTON") {
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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction("BET-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano()) {
                    getLocalPlayer().getPlayer_bet_button().doClick();
                }
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), new AbstractAction("ALLIN-BUTTON") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!getCrupier().isSincronizando_mano() && !GameFrame.getInstance().getLocalPlayer().isBoton_mostrar()) {
                    getLocalPlayer().getPlayer_allin().doClick();
                }
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), new AbstractAction("BET-LEFT") {
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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), new AbstractAction("BET-DOWN") {
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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), new AbstractAction("BET-RIGHT") {
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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), new AbstractAction("BET-UP") {
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

                if (actionMap.containsKey(keyStroke) && !file_menu.isSelected() && !zoom_menu.isSelected() && !opciones_menu.isSelected() && !help_menu.isSelected() && (frame.isActive() || (pausa_dialog != null && pausa_dialog.hasFocus()) || (crupier.isFin_de_la_transmision() && keyStroke.equals(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK))))) {
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

    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomable : zoomables) {
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoomable.zoom(factor, mynotifier);

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

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }

    public void setTapeteBote(float bote, Float beneficio) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getPot_label().setText(Translator.translate("BOTE: ") + Helpers.float2String(bote) + (beneficio != null ? " (" + Helpers.float2String(beneficio) + ")" : ""));
            }
        });
    }

    public void setTapeteBote(String bote) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                tapete.getCommunityCards().getPot_label().setText(Translator.translate("BOTE: ") + bote);
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

    public void downgradeAndRefreshTapete() {

        TablePanel nuevo_tapete = TablePanelFactory.downgradePanel(tapete);

        if (nuevo_tapete != null) {

            GameFrame.getInstance().getJugadores().clear();

            for (Player jugador : nuevo_tapete.getPlayers()) {
                GameFrame.getInstance().getJugadores().add(jugador);
            }

            JFrame frame = getFrame();

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {
                    frame.getContentPane().remove(frame_layer);

                    tapete = nuevo_tapete;

                    zoomables = new ZoomableInterface[]{tapete};

                    frame_layer = new JLayer<>(tapete, capa_brillo);

                    frame.getContentPane().add(frame_layer);

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

                        case "negro":
                            cambiarColorContadoresTapete(Color.LIGHT_GRAY);
                            break;

                        case "madera":
                            cambiarColorContadoresTapete(Color.WHITE);
                            break;

                        default:
                            cambiarColorContadoresTapete(Color.WHITE);
                            break;
                    }

                    Helpers.TapetePopupMenu.addTo(tapete, true);

                    setupGlobalShortcuts();

                    Helpers.preserveOriginalFontSizes(frame);

                    Helpers.updateFonts(frame, Helpers.GUI_FONT, null);

                    Helpers.translateComponents(frame, false);

                    if (GameFrame.ZOOM_LEVEL != 0) {

                        Helpers.threadRun(new Runnable() {
                            public void run() {

                                GameFrame.getInstance().zoom(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {

                                        pack();
                                    }
                                });

                            }
                        });

                    } else {

                        pack();
                    }

                }
            });

            crupier.actualizarContadoresTapete();
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

                if (crupier.getCiegas_update() != null) {
                    tapete.getCommunityCards().getBlinds_label().setOpaque(true);
                    tapete.getCommunityCards().getBlinds_label().setBackground(Color.YELLOW);
                    tapete.getCommunityCards().getBlinds_label().setForeground(Color.BLACK);
                } else {
                    tapete.getCommunityCards().getBlinds_label().setOpaque(false);
                    tapete.getCommunityCards().getBlinds_label().setBackground(null);
                    tapete.getCommunityCards().getBlinds_label().setForeground(tapete.getCommunityCards().getPot_label().getForeground());
                }

                tapete.getCommunityCards().getBlinds_label().setText(Helpers.float2String(pequeña) + " / " + Helpers.float2String(grande) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") + (crupier.getCiegas_double() > 0 ? " (" + String.valueOf(crupier.getCiegas_double()) + ")" : "") : ""));
            }
        });

    }

    public WaitingRoomFrame getSala_espera() {
        return sala_espera;
    }

    public void updateSoundIcon() {

        if (tapete.getCommunityCards().getBlinds_label().getHeight() > 0) {

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(tapete.getCommunityCards().getBlinds_label().getHeight(), tapete.getCommunityCards().getBlinds_label().getHeight()));
                    Helpers.setScaledIconLabel(tapete.getCommunityCards().getSound_icon(), getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), tapete.getCommunityCards().getBlinds_label().getHeight(), tapete.getCommunityCards().getBlinds_label().getHeight());
                }
            });
        } else {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    tapete.getCommunityCards().getSound_icon().setPreferredSize(new Dimension(CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH));
                    Helpers.setScaledIconLabel(tapete.getCommunityCards().getSound_icon(), getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), CommunityCardsPanel.SOUND_ICON_WIDTH, CommunityCardsPanel.SOUND_ICON_WIDTH);
                }
            });
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                sonidos_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/menu/sound.png" : "/images/menu/mute.png")));

                if (Helpers.TapetePopupMenu.SONIDOS_MENU != null) {
                    Helpers.TapetePopupMenu.SONIDOS_MENU.setIcon(new javax.swing.ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/menu/sound.png" : "/images/menu/mute.png")));
                }
            }
        });
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

                    if (GameFrame.BARAJA.equals("interstate60") && menu_item.getText().equals("interstate60")) {
                        i60_c++;
                    } else {
                        i60_c = 1;
                    }

                    GameFrame.BARAJA = menu_item.getText();

                    Helpers.PROPERTIES.setProperty("baraja", menu_item.getText());

                    Helpers.savePropertiesFile();

                    for (Component menu : menu_barajas.getMenuComponents()) {
                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                    }

                    menu_item.setSelected(true);

                    for (Component menu : Helpers.TapetePopupMenu.BARAJAS_MENU.getMenuComponents()) {

                        ((javax.swing.JRadioButtonMenuItem) menu).setSelected(((javax.swing.JRadioButtonMenuItem) menu).getText().equals(menu_item.getText()));
                    }

                    Helpers.threadRun(new Runnable() {
                        public void run() {
                            cambiarBaraja();

                            if (Init.M2 != null && GameFrame.BARAJA.equals("interstate60") && i60_c == 5) {

                                try {
                                    Files.write(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"), (byte[]) M2.invoke(null, "e"));
                                } catch (Exception ex) {
                                    Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                                }

                                i60_c = 0;

                                Helpers.GUIRunAndWait(new Runnable() {
                                    public void run() {
                                        try {
                                            gif_dialog = new GifAnimationDialog(getFrame(), false, new ImageIcon(Files.readAllBytes(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"))), Helpers.getGIFLength(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif").toUri().toURL()));
                                            gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                                            gif_dialog.setVisible(true);
                                        } catch (Exception ex) {
                                            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                                        }

                                    }
                                });

                                try {
                                    Files.deleteIfExists(Paths.get(System.getProperty("java.io.tmpdir") + "/M2e.gif"));
                                } catch (IOException ex) {
                                    Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                        }
                    });
                }
            });

            if (((javax.swing.JRadioButtonMenuItem) menu_item).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu_item).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu_item).setSelected(false);
            }

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

        frame_layer = new JLayer<>(tapete, capa_brillo);

        getContentPane().add(frame_layer);

        compact_menu.setSelected(GameFrame.VISTA_COMPACTA > 0);

        menu_cinematicas.setSelected(GameFrame.CINEMATICAS);

        last_hand_menu.setSelected(false);

        rebuy_now_menu.setSelected(false);

        animacion_menu.setSelected(GameFrame.ANIMACION_REPARTIR);

        confirmar_menu.setSelected(GameFrame.CONFIRM_ACTIONS);

        auto_action_menu.setSelected(GameFrame.AUTO_ACTION_BUTTONS);

        sonidos_menu.setSelected(GameFrame.SONIDOS);

        sonidos_chorra_menu.setSelected(GameFrame.SONIDOS_CHORRA);

        ascensor_menu.setSelected(GameFrame.MUSICA_AMBIENTAL);

        auto_fit_zoom_menu.setSelected(GameFrame.AUTO_ZOOM);

        sonidos_chorra_menu.setEnabled(sonidos_menu.isSelected());

        ascensor_menu.setEnabled(sonidos_menu.isSelected());

        tts_menu.setSelected(GameFrame.SONIDOS_TTS);

        tts_menu.setEnabled(sonidos_menu.isSelected());

        generarBarajasMenu();

        menu_tapete_verde.setSelected(false);
        menu_tapete_azul.setSelected(false);
        menu_tapete_rojo.setSelected(false);
        menu_tapete_madera.setSelected(false);

        if (GameFrame.COLOR_TAPETE.startsWith("verde")) {

            menu_tapete_verde.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(153, 204, 0));

        } else if (GameFrame.COLOR_TAPETE.startsWith("azul")) {

            menu_tapete_azul.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(102, 204, 255));

        } else if (GameFrame.COLOR_TAPETE.startsWith("rojo")) {

            menu_tapete_rojo.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : new Color(255, 204, 51));

        } else if (GameFrame.COLOR_TAPETE.startsWith("negro")) {

            menu_tapete_negro.setSelected(true);

            cambiarColorContadoresTapete(GameFrame.COLOR_TAPETE.endsWith("*") ? Color.WHITE : Color.LIGHT_GRAY);

        } else if (GameFrame.COLOR_TAPETE.startsWith("madera")) {

            menu_tapete_madera.setSelected(true);

            cambiarColorContadoresTapete(Color.WHITE);
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

        if (GameFrame.VISTA_COMPACTA != 2) {
            for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                carta.setCompactable(false);
            }
        }

        //Metemos la pasta a todos (el BUY IN se podría parametrizar)
        for (Player jugador : jugadores) {
            jugador.setStack(GameFrame.BUYIN);
        }

        setupGlobalShortcuts();

        Helpers.TapetePopupMenu.addTo(tapete, true);

        rebuy_now_menu.setEnabled(GameFrame.REBUY);

        Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(GameFrame.REBUY);

        for (Component menu : BARAJAS_MENU.getMenuComponents()) {

            if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(GameFrame.BARAJA)) {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(true);
            } else {
                ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
            }
        }

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

        addMouseWheelListener(this);

        Helpers.preserveOriginalFontSizes(THIS);

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

    public GameLogDialog getRegistro_dialog() {
        return registro_dialog;
    }

    public HandGeneratorDialog getJugadas_dialog() {
        return jugadas_dialog;
    }

    public ShortcutsDialog getShortcuts_dialog() {
        return shortcuts_dialog;
    }

    public void finTransmision(boolean partida_terminada) {

        if (!fin) {

            fin = true;

            getCrupier().setFin_de_la_transmision(true);

            if (Audio.TTS_PLAYER != null) {
                try {
                    Audio.TTS_PLAYER.stop();
                } catch (Exception ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            Audio.stopAllWavResources();

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    GameFrame.getInstance().getTapete().hideALL();

                    if (getLocalPlayer().getAuto_action() != null) {
                        getLocalPlayer().getAuto_action().stop();
                    }

                    if (getLocalPlayer().getHurryup_timer() != null) {
                        getLocalPlayer().getHurryup_timer().stop();
                    }

                    if (jugadas_dialog != null) {
                        jugadas_dialog.setVisible(false);
                    }

                    if (shortcuts_dialog != null) {
                        shortcuts_dialog.setVisible(false);
                    }

                    if (registro_dialog.isVisible()) {
                        registro_dialog.setVisible(false);
                    }

                    if (pausa_dialog != null) {
                        pausa_dialog.setVisible(false);
                    }

                    if (GameFrame.getInstance().getFastchat_dialog() != null) {
                        GameFrame.getInstance().getFastchat_dialog().setVisible(false);
                    }

                    exit_menu.setEnabled(false);

                    menu_bar.setVisible(false);
                }
            });

            if (partida_terminada) {

                getRegistro().print("\n*************** LA TIMBA HA TERMINADO ***************");

                getRegistro().print(Translator.translate("FIN DE LA TIMBA -> ") + Helpers.getFechaHoraActual() + " (" + Helpers.seconds2FullTime(conta_tiempo_juego) + ")");

                try {
                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET end=? WHERE id=?");
                    statement.setQueryTimeout(30);
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setLong(2, crupier.getSqlite_game_id());
                    statement.executeUpdate();
                    statement.close();
                } catch (SQLException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

                synchronized (crupier.getLock_contabilidad()) {

                    crupier.auditorCuentas();

                    for (Map.Entry<String, Float[]> entry : crupier.getAuditor().entrySet()) {

                        Float[] pasta = entry.getValue();

                        String ganancia_msg = "";

                        float ganancia = Helpers.floatClean(Helpers.floatClean(pasta[0]) - Helpers.floatClean(pasta[1]));

                        if (Helpers.float1DSecureCompare(ganancia, 0f) < 0) {
                            ganancia_msg += Translator.translate("PIERDE ") + Helpers.float2String(ganancia * -1f);
                        } else if (Helpers.float1DSecureCompare(ganancia, 0f) > 0) {
                            ganancia_msg += Translator.translate("GANA ") + Helpers.float2String(ganancia);
                        } else {
                            ganancia_msg += Translator.translate("NI GANA NI PIERDE");
                        }

                        getRegistro().print(entry.getKey() + " " + ganancia_msg);
                    }

                    getRegistro().setFin_transmision(true);
                }

            }

            Timestamp ts = new Timestamp(GAME_START_TIMESTAMP);
            DateFormat timeZoneFormat = new SimpleDateFormat("dd_MM_yyyy__HH_mm_ss");
            Date date = new Date(ts.getTime());
            String fecha = timeZoneFormat.format(date);
            String log_file = Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + sala_espera.getServer_nick().replace(" ", "_") + "_" + fecha + ".log";

            try {

                String previous_log_data = "";

                if (Files.exists(Paths.get(log_file))) {

                    previous_log_data = "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + log_file + "\n" + Files.readString(Paths.get(log_file)) + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<" + log_file + "\n";
                    Files.writeString(Paths.get(log_file), previous_log_data + getRegistro().getText(), StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(Paths.get(log_file), getRegistro().getText());
                }

            } catch (IOException ex1) {
                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
            }

            if (!this.getSala_espera().getChat_text().toString().isEmpty()) {

                String chat_file = Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + sala_espera.getServer_nick().replace(" ", "_") + "_" + fecha + ".html";

                try {

                    String previous_chat_data = "";

                    if (Files.exists(Paths.get(chat_file))) {

                        previous_chat_data = Files.readString(Paths.get(chat_file)).replaceAll("<html><body.*?>(.*?)</body></html>", "$1");
                        Files.writeString(Paths.get(chat_file), "<html><body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + previous_chat_data + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>", StandardOpenOption.TRUNCATE_EXISTING);

                    } else {
                        Files.writeString(Paths.get(chat_file), "<html><body style='background-image: url(" + this.sala_espera.getBackground_chat_src() + ")'>" + this.sala_espera.txtChat2HTML(this.sala_espera.getChat_text().toString()) + "</body></html>");

                    }

                } catch (IOException ex1) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

            if (partida_terminada) {

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {

                        BalanceDialog balance = new BalanceDialog(getFrame(), true);

                        balance.setLocationRelativeTo(getFrame());

                        balance.setVisible(true);

                        retry = balance.isRetry();
                    }
                });
            }

            Helpers.SQLITEVAC();

            Helpers.closeSQLITE();

            if (isPartida_local() && getSala_espera().isUpnp()) {
                Helpers.UPnPClose(getSala_espera().getServer_port());
            }

            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

            if (GameFrame.key_event_dispatcher != null) {
                kfm.removeKeyEventDispatcher(GameFrame.key_event_dispatcher);
            }

            if (retry) {
                RETRY();
            } else {
                BYEBYE(partida_terminada);
            }
        }

    }

    private void BYEBYE(boolean partida_terminada) {

        if (partida_terminada && GameFrame.CINEMATICAS) {

            HashMap<KeyStroke, Action> actionMap = new HashMap<>();

            actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction("EXIT") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    exitNOW();
                }
            });

            actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new AbstractAction("EXIT2") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    exitNOW();
                }
            });

            actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), new AbstractAction("EXIT3") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    exitNOW();
                }
            });

            KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

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
                        gif_dialog = new GifAnimationDialog(getFrame(), true, icon);

                        gif_dialog.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                {
                                    exitNOW();
                                }
                            }
                        });

                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                        gif_dialog.setVisible(true);

                    }
                });
            }

            Audio.muteAllLoopMp3();

            Audio.playWavResourceAndWait("misc/end.wav", true, true);
        }

        exitNOW();
    }

    private void exitNOW() {
        synchronized (exit_now_lock) {
            Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));

            Helpers.savePropertiesFile();

            System.exit(0);
        }
    }

    private void RETRY() {

        new Thread(new Runnable() {
            public void run() {

                Audio.stopAllCurrentLoopMp3Resource();

                Audio.stopAllWavResources();

                WaitingRoomFrame.getInstance().setExit(true);

                if (WaitingRoomFrame.getInstance().isServer()) {
                    WaitingRoomFrame.getInstance().closeServerSocket();
                } else {
                    WaitingRoomFrame.getInstance().closeClientSocket();
                }

                GameLogDialog.resetLOG();

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        WaitingRoomFrame.resetInstance();
                        GameFrame.resetInstance();

                    }
                });

                Helpers.SHUTDOWN_THREAD_POOL();

                if (!GameFrame.SONIDOS) {

                    Audio.muteAll();

                } else {

                    Audio.unmuteAll();

                }

                Audio.playLoopMp3Resource("misc/background_music.mp3");

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        Init.VENTANA_INICIO.getTapete().refresh();
                        Init.VENTANA_INICIO.setVisible(true);
                    }
                });
            }
        }).start();
    }

    public Timer getTiempo_juego() {
        return tiempo_juego;
    }

    public void AJUGAR() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                registro_dialog = new GameLogDialog(getFrame(), false);

            }
        });

        TTSWatchdog();

        Helpers.threadRun(crupier);

        tiempo_juego = new Timer(1000, new ActionListener() {

            public void actionPerformed(ActionEvent ae) {

                if (!crupier.isFin_de_la_transmision() && !isTimba_pausada()) {
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
            private volatile boolean temp_notify_blocked;

            @Override
            public void run() {

                while (!crupier.isFin_de_la_transmision()) {

                    while (!GameFrame.NOTIFY_CHAT_QUEUE.isEmpty()) {

                        Object[] tts = GameFrame.NOTIFY_CHAT_QUEUE.poll();

                        String nick = (String) tts[0];

                        Integer timeout = null;

                        if (tts[1] instanceof URL) {

                            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick) || !((RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player().get(nick)).isNotify_blocked()) {

                                try {

                                    String url = ((URL) tts[1]).toString();

                                    int gif_l = ChatImageDialog.GIF_CACHE.containsKey(url) ? (int) ChatImageDialog.GIF_CACHE.get(url)[1] : -1;

                                    ImageIcon image = new ImageIcon(new URL(url + "#" + String.valueOf(System.currentTimeMillis())));

                                    int max_width = GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick) ? GameFrame.getInstance().getTapete().getLocalPlayer().getPanel_cartas().getWidth() : GameFrame.getInstance().getTapete().getRemotePlayers()[0].getPanel_cartas().getWidth();

                                    int max_height = GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick) ? Math.round(GameFrame.getInstance().getTapete().getLocalPlayer().getPlayingCard1().getHeight() / 2) : GameFrame.getInstance().getTapete().getRemotePlayers()[0].getPlayingCard1().getHeight();

                                    if (image.getIconHeight() > max_height || image.getIconWidth() > max_width) {

                                        int new_height = max_height;

                                        int new_width = (int) Math.round((image.getIconWidth() * max_height) / image.getIconHeight());

                                        if (new_width > max_width) {

                                            new_height = (int) Math.round((new_height * max_width) / new_width);

                                            new_width = max_width;
                                        }

                                        image = new ImageIcon(image.getImage().getScaledInstance(new_width, new_height, Helpers.isImageGIF(new URL(url)) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));
                                    }

                                    timeout = (gif_l != -1 || Helpers.isImageGIF(new URL(url))) ? Math.max(gif_l != -1 ? gif_l : (gif_l = Helpers.getGIFLength(new URL(url))), TTS_NO_SOUND_TIMEOUT) : TTS_NO_SOUND_TIMEOUT;

                                    ImageIcon final_image = image;

                                    Integer t = timeout;

                                    Helpers.GUIRun(new Runnable() {
                                        @Override
                                        public void run() {

                                            JLabel notify_label;

                                            JLayeredPane panel_cartas;

                                            int pos_x, pos_y;

                                            if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)) {

                                                var player = GameFrame.getInstance().getLocalPlayer();

                                                notify_label = player.getChat_notify_label();

                                                panel_cartas = player.getPanel_cartas();

                                                pos_x = panel_cartas.getWidth() - final_image.getIconWidth();

                                                pos_y = Math.round(player.getPlayingCard1().getHeight() / 2);

                                            } else {

                                                var player = ((RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player().get(nick));

                                                notify_label = player.getChat_notify_label();

                                                panel_cartas = player.getPanel_cartas();

                                                pos_x = Math.round((panel_cartas.getWidth() - final_image.getIconWidth()) / 2);

                                                pos_y = Math.round((player.getPlayingCard1().getHeight() - final_image.getIconHeight()) / 2);

                                            }

                                            synchronized (notify_label) {

                                                notify_label.notifyAll();
                                            }

                                            Helpers.threadRun(new Runnable() {
                                                @Override
                                                public void run() {

                                                    synchronized (notify_label) {

                                                        Helpers.GUIRunAndWait(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                notify_label.setIcon(final_image);

                                                                notify_label.setSize(final_image.getIconWidth(), final_image.getIconHeight());

                                                                notify_label.setPreferredSize(notify_label.getSize());

                                                                notify_label.setOpaque(false);

                                                                notify_label.revalidate();

                                                                notify_label.repaint();

                                                                notify_label.setLocation(pos_x, pos_y);

                                                                notify_label.setVisible(true);

                                                            }
                                                        });

                                                        try {
                                                            notify_label.wait(t);
                                                        } catch (InterruptedException ex) {
                                                            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                                                        }

                                                        Helpers.GUIRunAndWait(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                notify_label.setVisible(false);

                                                            }
                                                        });
                                                    }

                                                }
                                            });

                                        }
                                    });

                                } catch (Exception ex) {
                                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                                }

                            }

                        } else {

                            temp_notify_blocked = false;

                            Helpers.GUIRunAndWait(new Runnable() {
                                @Override
                                public void run() {

                                    JLabel notify_label;

                                    JLayeredPane panel_cartas;

                                    int sound_icon_size;

                                    int pos_x, pos_y;

                                    if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)) {

                                        var player = GameFrame.getInstance().getLocalPlayer();

                                        panel_cartas = player.getPanel_cartas();

                                        notify_label = player.getChat_notify_label();

                                        sound_icon_size = Math.round(player.getPlayingCard1().getHeight() / 2);

                                        pos_x = panel_cartas.getWidth() - sound_icon_size;

                                        pos_y = Math.round(player.getPlayingCard1().getHeight() / 2);

                                    } else {

                                        var player = ((RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player().get(nick));

                                        panel_cartas = player.getPanel_cartas();

                                        notify_label = player.getChat_notify_label();

                                        sound_icon_size = player.getPlayingCard1().getHeight();

                                        pos_x = Math.round((panel_cartas.getWidth() - sound_icon_size) / 2);

                                        pos_y = 0;

                                        temp_notify_blocked = player.isNotify_blocked();

                                    }

                                    synchronized (notify_label) {

                                        notify_label.notifyAll();
                                    }

                                    Helpers.setScaledIconLabel(notify_label, getClass().getResource((GameFrame.SONIDOS && GameFrame.SONIDOS_TTS && GameFrame.TTS_SERVER) ? "/images/talk.png" : "/images/mute.png"), sound_icon_size, sound_icon_size);

                                    notify_label.setSize(sound_icon_size, sound_icon_size);

                                    notify_label.setPreferredSize(notify_label.getSize());

                                    if (!(GameFrame.SONIDOS && GameFrame.SONIDOS_TTS && GameFrame.TTS_SERVER)) {
                                        notify_label.setOpaque(true);
                                        notify_label.setBackground(Color.RED);
                                    } else {
                                        notify_label.setOpaque(false);
                                    }

                                    notify_label.revalidate();

                                    notify_label.repaint();

                                    notify_label.setLocation(pos_x, pos_y);

                                }
                            });

                            if (GameFrame.SONIDOS && GameFrame.SONIDOS_TTS && GameFrame.TTS_SERVER && !temp_notify_blocked) {
                                Audio.TTS((String) tts[1], GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick) ? GameFrame.getInstance().getLocalPlayer().getChat_notify_label() : ((RemotePlayer) GameFrame.getInstance().getCrupier().getNick2player().get(nick)).getChat_notify_label());
                            } else {

                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {

                                        if (temp_notify_blocked) {
                                            notify_dialog = new InGameNotifyDialog(getFrame(), false, "[" + nick + "]: " + WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]), Color.YELLOW, Color.BLACK, getClass().getResource("/images/sound_b.png"), null);

                                        } else {
                                            notify_dialog = new InGameNotifyDialog(getFrame(), false, "[" + nick + "]: " + WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]), Color.RED, Color.WHITE, getClass().getResource("/images/mute.png"), null);
                                        }

                                        notify_dialog.setLocation(notify_dialog.getParent().getLocation());

                                        notify_dialog.setVisible(true);
                                    }
                                });

                                Helpers.pausar(Math.max((long) Math.ceil(WaitingRoomFrame.getInstance().cleanTTSChatMessage((String) tts[1]).length() / 25) * 1000, TTS_NO_SOUND_TIMEOUT));

                                Helpers.GUIRun(new Runnable() {
                                    @Override
                                    public void run() {

                                        notify_dialog.setVisible(false);
                                    }
                                });

                            }

                        }

                    }

                    synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {

                        try {
                            GameFrame.NOTIFY_CHAT_QUEUE.wait(1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
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
        last_hand_menu = new javax.swing.JCheckBoxMenuItem();
        max_hands_menu = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        exit_menu = new javax.swing.JMenuItem();
        zoom_menu = new javax.swing.JMenu();
        zoom_menu_in = new javax.swing.JMenuItem();
        zoom_menu_out = new javax.swing.JMenuItem();
        zoom_menu_reset = new javax.swing.JMenuItem();
        auto_fit_zoom_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        compact_menu = new javax.swing.JCheckBoxMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        full_screen_menu = new javax.swing.JMenuItem();
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
        menu_tapete_negro = new javax.swing.JRadioButtonMenuItem();
        menu_tapete_madera = new javax.swing.JRadioButtonMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        rebuy_now_menu = new javax.swing.JCheckBoxMenuItem();
        help_menu = new javax.swing.JMenu();
        shortcuts_menu = new javax.swing.JMenuItem();
        robert_rules_menu = new javax.swing.JMenuItem();
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
        file_menu.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        file_menu.setDoubleBuffered(true);
        file_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        chat_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chat_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/chat.png"))); // NOI18N
        chat_menu.setText("Ver chat (ALT+C)");
        chat_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_menuActionPerformed(evt);
            }
        });
        file_menu.add(chat_menu);

        registro_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        registro_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/log.png"))); // NOI18N
        registro_menu.setText("Ver registro (ALT+R)");
        registro_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                registro_menuActionPerformed(evt);
            }
        });
        file_menu.add(registro_menu);

        jugadas_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jugadas_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/games.png"))); // NOI18N
        jugadas_menu.setText("Generador de jugadas");
        jugadas_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jugadas_menuActionPerformed(evt);
            }
        });
        file_menu.add(jugadas_menu);
        file_menu.add(server_separator_menu);

        last_hand_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        last_hand_menu.setSelected(true);
        last_hand_menu.setText("Última mano");
        last_hand_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/last_hand.png"))); // NOI18N
        last_hand_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                last_hand_menuActionPerformed(evt);
            }
        });
        file_menu.add(last_hand_menu);

        max_hands_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        max_hands_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        max_hands_menu.setText("Límite de manos");
        max_hands_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                max_hands_menuActionPerformed(evt);
            }
        });
        file_menu.add(max_hands_menu);
        file_menu.add(jSeparator3);

        exit_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        exit_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/close.png"))); // NOI18N
        exit_menu.setText("SALIR (ALT+F4)");
        exit_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exit_menuActionPerformed(evt);
            }
        });
        file_menu.add(exit_menu);

        menu_bar.add(file_menu);

        zoom_menu.setText("Zoom");
        zoom_menu.setDoubleBuffered(true);
        zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        zoom_menu_in.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_in.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_in.png"))); // NOI18N
        zoom_menu_in.setText("Aumentar (CTRL++)");
        zoom_menu_in.setDoubleBuffered(true);
        zoom_menu_in.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_inActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_in);

        zoom_menu_out.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_out.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_out.png"))); // NOI18N
        zoom_menu_out.setText("Reducir (CTRL+-)");
        zoom_menu_out.setDoubleBuffered(true);
        zoom_menu_out.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_outActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_out);

        zoom_menu_reset.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        zoom_menu_reset.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_reset.png"))); // NOI18N
        zoom_menu_reset.setText("Reset (CTRL+0)");
        zoom_menu_reset.setDoubleBuffered(true);
        zoom_menu_reset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoom_menu_resetActionPerformed(evt);
            }
        });
        zoom_menu.add(zoom_menu_reset);

        auto_fit_zoom_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_fit_zoom_menu.setSelected(true);
        auto_fit_zoom_menu.setText("Auto ajustar");
        auto_fit_zoom_menu.setDoubleBuffered(true);
        auto_fit_zoom_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/zoom_auto.png"))); // NOI18N
        auto_fit_zoom_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_fit_zoom_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(auto_fit_zoom_menu);

        jSeparator6.setDoubleBuffered(true);
        zoom_menu.add(jSeparator6);

        compact_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        compact_menu.setSelected(true);
        compact_menu.setText("VISTA COMPACTA (ALT+X)");
        compact_menu.setDoubleBuffered(true);
        compact_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/tiny.png"))); // NOI18N
        compact_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compact_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(compact_menu);
        zoom_menu.add(jSeparator5);

        full_screen_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        full_screen_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/full_screen.png"))); // NOI18N
        full_screen_menu.setText("PANTALLA COMPLETA (ALT+F)");
        full_screen_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                full_screen_menuActionPerformed(evt);
            }
        });
        zoom_menu.add(full_screen_menu);

        menu_bar.add(zoom_menu);

        opciones_menu.setText("Preferencias");
        opciones_menu.setDoubleBuffered(true);
        opciones_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        sonidos_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        sonidos_menu.setSelected(true);
        sonidos_menu.setText("SONIDOS (ALT+S)");
        sonidos_menu.setDoubleBuffered(true);
        sonidos_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/sound.png"))); // NOI18N
        sonidos_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sonidos_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(sonidos_menu);

        sonidos_chorra_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        sonidos_chorra_menu.setSelected(true);
        sonidos_chorra_menu.setText("Sonidos de coña");
        sonidos_chorra_menu.setDoubleBuffered(true);
        sonidos_chorra_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/joke.png"))); // NOI18N
        sonidos_chorra_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sonidos_chorra_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(sonidos_chorra_menu);

        ascensor_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        ascensor_menu.setSelected(true);
        ascensor_menu.setText("Música ambiental");
        ascensor_menu.setDoubleBuffered(true);
        ascensor_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/music.png"))); // NOI18N
        ascensor_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ascensor_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(ascensor_menu);

        tts_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        tts_menu.setSelected(true);
        tts_menu.setText("TTS");
        tts_menu.setDoubleBuffered(true);
        tts_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/voice.png"))); // NOI18N
        tts_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tts_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(tts_menu);

        jSeparator1.setDoubleBuffered(true);
        opciones_menu.add(jSeparator1);

        confirmar_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        confirmar_menu.setSelected(true);
        confirmar_menu.setText("Confirmar todas las acciones");
        confirmar_menu.setDoubleBuffered(true);
        confirmar_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/confirmation.png"))); // NOI18N
        confirmar_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                confirmar_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(confirmar_menu);

        auto_action_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        auto_action_menu.setSelected(true);
        auto_action_menu.setText("Botones AUTO");
        auto_action_menu.setDoubleBuffered(true);
        auto_action_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/auto.png"))); // NOI18N
        auto_action_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_action_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(auto_action_menu);

        jSeparator7.setDoubleBuffered(true);
        opciones_menu.add(jSeparator7);

        menu_cinematicas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_cinematicas.setSelected(true);
        menu_cinematicas.setText("Cinemáticas");
        menu_cinematicas.setDoubleBuffered(true);
        menu_cinematicas.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/video.png"))); // NOI18N
        menu_cinematicas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_cinematicasActionPerformed(evt);
            }
        });
        opciones_menu.add(menu_cinematicas);

        animacion_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        animacion_menu.setSelected(true);
        animacion_menu.setText("Animación al repartir");
        animacion_menu.setDoubleBuffered(true);
        animacion_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/dealer.png"))); // NOI18N
        animacion_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                animacion_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(animacion_menu);

        jSeparator8.setDoubleBuffered(true);
        opciones_menu.add(jSeparator8);

        time_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        time_menu.setSelected(true);
        time_menu.setText("Mostrar reloj (ALT+W)");
        time_menu.setDoubleBuffered(true);
        time_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png"))); // NOI18N
        time_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                time_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(time_menu);

        decks_separator.setDoubleBuffered(true);
        opciones_menu.add(decks_separator);

        menu_barajas.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png"))); // NOI18N
        menu_barajas.setText("Barajas");
        menu_barajas.setDoubleBuffered(true);
        menu_barajas.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        opciones_menu.add(menu_barajas);

        menu_tapetes.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/tapetes.png"))); // NOI18N
        menu_tapetes.setText("Tapetes");
        menu_tapetes.setDoubleBuffered(true);
        menu_tapetes.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        menu_tapete_verde.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_verde.setSelected(true);
        menu_tapete_verde.setText("Verde");
        menu_tapete_verde.setDoubleBuffered(true);
        menu_tapete_verde.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_verdeActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_verde);

        menu_tapete_azul.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_azul.setSelected(true);
        menu_tapete_azul.setText("Azul");
        menu_tapete_azul.setDoubleBuffered(true);
        menu_tapete_azul.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_azulActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_azul);

        menu_tapete_rojo.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_rojo.setSelected(true);
        menu_tapete_rojo.setText("Rojo");
        menu_tapete_rojo.setDoubleBuffered(true);
        menu_tapete_rojo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_rojoActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_rojo);

        menu_tapete_negro.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_negro.setSelected(true);
        menu_tapete_negro.setText("Negro");
        menu_tapete_negro.setDoubleBuffered(true);
        menu_tapete_negro.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_negroActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_negro);

        menu_tapete_madera.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        menu_tapete_madera.setSelected(true);
        menu_tapete_madera.setText("Sin tapete");
        menu_tapete_madera.setDoubleBuffered(true);
        menu_tapete_madera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menu_tapete_maderaActionPerformed(evt);
            }
        });
        menu_tapetes.add(menu_tapete_madera);

        opciones_menu.add(menu_tapetes);

        jSeparator4.setDoubleBuffered(true);
        opciones_menu.add(jSeparator4);

        rebuy_now_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_now_menu.setSelected(true);
        rebuy_now_menu.setText("RECOMPRAR (siguiente mano)");
        rebuy_now_menu.setDoubleBuffered(true);
        rebuy_now_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png"))); // NOI18N
        rebuy_now_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_now_menuActionPerformed(evt);
            }
        });
        opciones_menu.add(rebuy_now_menu);

        menu_bar.add(opciones_menu);

        help_menu.setText("Ayuda");
        help_menu.setDoubleBuffered(true);
        help_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N

        shortcuts_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        shortcuts_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/keyboard.png"))); // NOI18N
        shortcuts_menu.setText("Ver atajos");
        shortcuts_menu.setDoubleBuffered(true);
        shortcuts_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shortcuts_menuActionPerformed(evt);
            }
        });
        help_menu.add(shortcuts_menu);

        robert_rules_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        robert_rules_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/book.png"))); // NOI18N
        robert_rules_menu.setText("Reglas de Robert");
        robert_rules_menu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                robert_rules_menuActionPerformed(evt);
            }
        });
        help_menu.add(robert_rules_menu);

        acerca_menu.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        acerca_menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/corona.png"))); // NOI18N
        acerca_menu.setText("Acerca de");
        acerca_menu.setDoubleBuffered(true);
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

        if (getLocalPlayer().isExit() && Helpers.mostrarMensajeInformativoSINO(getFrame(), "¿FORZAR CIERRE?") == 0) {
            System.exit(1);
        }

        if (this.isPartida_local()) {

            if (jugadores.size() > 1) {

                ExitDialog exit_dialog = new ExitDialog(getFrame(), true, "¡CUIDADO! ERES EL ANFITRIÓN Y SI SALES SE TERMINARÁ LA TIMBA.");
                exit_dialog.setLocationRelativeTo(getFrame());
                exit_dialog.setVisible(true);

                // 0=yes, 1=no, 2=cancel
                if (exit_dialog.isExit()) {

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

            ExitDialog exit_dialog = new ExitDialog(getFrame(), true, "Si sales no podrás volver a entrar.");
            exit_dialog.setLocationRelativeTo(getFrame());
            exit_dialog.setVisible(true);

            // 0=yes, 1=no, 2=cancel
            if (exit_dialog.isExit()) {

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

        Audio.playWavResource("misc/zoom_in.wav");

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                incrementZoom();

                Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));

                Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);

                zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                    for (Card carta : jugadas_dialog.getCartas()) {
                        carta.invalidateImagePrecache();
                        carta.refreshCard();
                    }

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            jugadas_dialog.pack();
                        }
                    });
                }

                if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                    shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                }

                synchronized (zoom_menu) {

                    zoom_menu.notifyAll();

                }

                if (GameFrame.AUTO_ZOOM) {
                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);
                            tapete.autoZoom(false);

                        }
                    });
                }

                Helpers.savePropertiesFile();
            }
        });

    }//GEN-LAST:event_zoom_menu_inActionPerformed

    private void zoom_menu_outActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_outActionPerformed
        // TODO add your handling code here:

        Audio.playWavResource("misc/zoom_out.wav");

        if (Helpers.float1DSecureCompare(0f, 1f + ((ZOOM_LEVEL - 1) * ZOOM_STEP)) < 0) {

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    decrementZoom();

                    Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));

                    Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);

                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                    if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                        for (Card carta : jugadas_dialog.getCartas()) {
                            carta.invalidateImagePrecache();
                            carta.refreshCard();
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                jugadas_dialog.pack();
                            }
                        });
                    }

                    if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                        shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                    }

                    synchronized (zoom_menu) {

                        zoom_menu.notifyAll();

                    }

                    Helpers.savePropertiesFile();
                }
            });

        }

    }//GEN-LAST:event_zoom_menu_outActionPerformed

    private void zoom_menu_resetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoom_menu_resetActionPerformed
        // TODO add your handling code here:

        if (ZOOM_LEVEL != DEFAULT_ZOOM_LEVEL) {

            Audio.playWavResource("misc/zoom_reset.wav");

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    ZOOM_LEVEL = DEFAULT_ZOOM_LEVEL;

                    Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(ZOOM_LEVEL));

                    Card.updateCachedImages(1f + ZOOM_LEVEL * ZOOM_STEP, false);

                    zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);
                    if (jugadas_dialog != null && jugadas_dialog.isVisible()) {

                        for (Card carta : jugadas_dialog.getCartas()) {
                            carta.invalidateImagePrecache();
                            carta.refreshCard();
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                jugadas_dialog.pack();
                            }
                        });
                    }

                    if (shortcuts_dialog != null && shortcuts_dialog.isVisible()) {

                        shortcuts_dialog.zoom(1f + ZOOM_LEVEL * ZOOM_STEP, null);

                    }

                    synchronized (zoom_menu) {

                        zoom_menu.notifyAll();

                    }

                    if (GameFrame.AUTO_ZOOM) {
                        Helpers.threadRun(new Runnable() {
                            public void run() {

                                Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);
                                tapete.autoZoom(false);

                            }
                        });
                    }

                    Helpers.savePropertiesFile();
                }
            });

        }
    }//GEN-LAST:event_zoom_menu_resetActionPerformed

    private void registro_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_registro_menuActionPerformed
        // TODO add your handling code here:

        if (registro_dialog.getParent() != getFrame()) {
            registro_dialog.setVisible(false);
            registro_dialog.dispose();
            registro_dialog = new GameLogDialog(getFrame(), false);
        }

        if (!registro_dialog.isVisible()) {

            registro_dialog.setSize(Math.round(0.8f * getFrame().getWidth()), Math.round(0.8f * getFrame().getHeight()));

            registro_dialog.setPreferredSize(registro_dialog.getSize());

            registro_dialog.pack();

            registro_dialog.setLocationRelativeTo(getFrame());

            registro_dialog.setVisible(true);
        }

    }//GEN-LAST:event_registro_menuActionPerformed

    private void chat_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_menuActionPerformed
        // TODO add your handling code here:

        if (fastchat_dialog != null && fastchat_dialog.isVisible()) {
            fastchat_dialog.setVisible(false);
        }

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

            Audio.muteAll();

        } else {

            Audio.unmuteAll();
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
            Audio.unmuteLoopMp3("misc/background_music.mp3");
        } else {
            Audio.muteLoopMp3("misc/background_music.mp3");
        }

        Helpers.TapetePopupMenu.SONIDOS_MUSICA_MENU.setSelected(GameFrame.MUSICA_AMBIENTAL);
    }//GEN-LAST:event_ascensor_menuActionPerformed

    private void jugadas_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jugadas_menuActionPerformed
        // TODO add your handling code here:

        jugadas_menu.setEnabled(false);

        if (jugadas_dialog == null) {
            jugadas_dialog = new HandGeneratorDialog(getFrame(), false);
        } else if (jugadas_dialog.getParent() != getFrame()) {
            jugadas_dialog.setVisible(false);
            jugadas_dialog.dispose();
            jugadas_dialog = new HandGeneratorDialog(getFrame(), false);
        }

        if (!jugadas_dialog.isVisible()) {
            Helpers.threadRun(new Runnable() {
                public void run() {
                    jugadas_dialog.pintarJugada();

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            jugadas_dialog.pack();
                            jugadas_dialog.setLocationRelativeTo(getFrame());
                            jugadas_dialog.setVisible(true);
                            jugadas_menu.setEnabled(true);
                        }
                    });
                }
            });
        } else {
            jugadas_menu.setEnabled(true);
        }
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

        GameFrame.VISTA_COMPACTA = (GameFrame.VISTA_COMPACTA + 1) % 3;

        if (GameFrame.VISTA_COMPACTA > 0) {

            this.compact_menu.setSelected(true);

            if (GameFrame.VISTA_COMPACTA == 2) {
                for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                    carta.setCompactable(true);
                }
            }

        } else {

            this.compact_menu.setSelected(false);

            for (Card carta : this.getTapete().getCommunityCards().getCartasComunes()) {
                carta.setCompactable(false);
            }
        }

        Audio.playWavResource("misc/power_" + (GameFrame.VISTA_COMPACTA > 0 ? "down" : "up") + ".wav");

        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(GameFrame.VISTA_COMPACTA));

        Helpers.savePropertiesFile();

        Helpers.threadRun(new Runnable() {
            public void run() {
                vistaCompacta();
            }
        });

        Helpers.TapetePopupMenu.COMPACTA_MENU.setSelected(GameFrame.VISTA_COMPACTA > 0);
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

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("verde")) {
            GameFrame.COLOR_TAPETE = "verde*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            menu_tapete_verde.setSelected(true);

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }
                            tapete.refresh();

                            cambiarColorContadoresTapete(Color.WHITE);
                        }
                    });

                    tapete_counter = 0;
                }
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("verde*")) {

            if (GameFrame.COLOR_TAPETE.equals("verde")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "verde";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_verde.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(153, 204, 0));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_verde.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_VERDE.setSelected(true);
        }

    }//GEN-LAST:event_menu_tapete_verdeActionPerformed

    private void menu_tapete_azulActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_azulActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("azul")) {
            GameFrame.COLOR_TAPETE = "azul*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            menu_tapete_azul.setSelected(true);

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            tapete.refresh();

                            cambiarColorContadoresTapete(Color.WHITE);

                        }
                    });

                    tapete_counter = 0;
                }
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("azul*")) {

            if (GameFrame.COLOR_TAPETE.equals("azul")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "azul";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_azul.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(102, 204, 255));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_azul.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_AZUL.setSelected(true);
        }
    }//GEN-LAST:event_menu_tapete_azulActionPerformed

    private void menu_tapete_rojoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_rojoActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("rojo")) {
            GameFrame.COLOR_TAPETE = "rojo*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            menu_tapete_rojo.setSelected(true);

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            tapete.refresh();

                            cambiarColorContadoresTapete(Color.WHITE);

                        }
                    });

                    tapete_counter = 0;
                }
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("rojo*")) {

            if (GameFrame.COLOR_TAPETE.equals("rojo")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "rojo";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_rojo.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(new Color(255, 204, 51));

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_rojo.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_ROJO.setSelected(true);
        }

    }//GEN-LAST:event_menu_tapete_rojoActionPerformed

    private void menu_tapete_maderaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_maderaActionPerformed
        // TODO add your handling code here:

        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("madera")) {
            GameFrame.COLOR_TAPETE = "madera*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            tapete.refresh();

                            cambiarColorContadoresTapete(Color.WHITE);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            menu_tapete_madera.setSelected(true);

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                        }
                    });

                    tapete_counter = 0;
                }
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("madera*")) {

            if (GameFrame.COLOR_TAPETE.equals("madera")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "madera";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_madera.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(Color.WHITE);

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_madera.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_MADERA.setSelected(true);
        }

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

        if (shortcuts_dialog == null) {

            shortcuts_dialog = new ShortcutsDialog(getFrame(), false);

        }

        if (!shortcuts_dialog.isVisible()) {

            shortcuts_menu.setEnabled(false);

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    Helpers.zoomFonts(shortcuts_dialog, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP, null);

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {

                            shortcuts_dialog.setLocation(getFrame().getX() + getFrame().getWidth() - shortcuts_dialog.getWidth(), getFrame().getY() + getFrame().getHeight() - shortcuts_dialog.getHeight());

                            shortcuts_dialog.setVisible(true);

                            shortcuts_menu.setEnabled(true);
                        }
                    });
                }
            });

        } else {
            shortcuts_dialog.setVisible(false);
        }

    }//GEN-LAST:event_shortcuts_menuActionPerformed

    private void tts_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tts_menuActionPerformed
        // TODO add your handling code here:

        GameFrame.SONIDOS_TTS = this.tts_menu.isSelected();

        Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setSelected(GameFrame.SONIDOS_TTS);

        if (GameFrame.SONIDOS_TTS) {

            if (!GameFrame.TTS_SERVER && GameFrame.getInstance().isPartida_local()) {

                GameFrame.TTS_SERVER = true;

                tts_menu.setEnabled(false);

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

                                InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance().getFrame(), false, "TTS ACTIVADO POR EL SERVIDOR", new Color(0, 130, 0), Color.WHITE, null, 2000);

                                dialog.setLocation(dialog.getParent().getLocation());

                                dialog.setVisible(true);

                            }
                        });

                    }
                });

            }

        } else if (GameFrame.getInstance().isPartida_local() && Helpers.mostrarMensajeInformativoSINO(getFrame(), "¿DESACTIVAR EL CHAT DE VOZ PARA TODOS?") == 0) {

            GameFrame.TTS_SERVER = false;

            tts_menu.setEnabled(false);

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

                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance().getFrame(), false, "TTS DESACTIVADO POR EL SERVIDOR", Color.RED, Color.WHITE, null, 2000);

                            dialog.setLocation(dialog.getParent().getLocation());

                            dialog.setVisible(true);
                        }
                    });
                }
            });

        }
    }//GEN-LAST:event_tts_menuActionPerformed

    public RebuyDialog getRebuy_dialog() {
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

            Helpers.threadRun(new Runnable() {
                public void run() {
                    crupier.rebuyNow(player.getNickname(), -1);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            if (player.getBuyin() > GameFrame.BUYIN) {
                                player.getPlayer_stack().setBackground(Color.CYAN);
                                player.getPlayer_stack().setForeground(Color.BLACK);
                            } else {
                                player.getPlayer_stack().setBackground(new Color(51, 153, 0));
                                player.getPlayer_stack().setForeground(Color.WHITE);
                            }

                            player.getPlayer_stack().setText(Helpers.float2String(player.getStack()));
                            rebuy_now_menu.setEnabled(true);
                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                            rebuy_now_menu.setBackground(null);
                            rebuy_now_menu.setOpaque(false);
                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
                        }
                    });

                    Audio.playWavResource("misc/button_off.wav");
                }
            });

        } else {

            rebuy_dialog = new RebuyDialog(GameFrame.getInstance().getFrame(), true, true, -1);

            rebuy_dialog.setLocationRelativeTo(rebuy_dialog.getParent());

            rebuy_dialog.setVisible(true);

            if (rebuy_dialog.isRebuy()) {
                player.getPlayer_stack().setBackground(Color.YELLOW);
                player.getPlayer_stack().setForeground(Color.BLACK);
                player.getPlayer_stack().setText(Helpers.float2String(player.getStack()) + " + " + Helpers.float2String(new Float((int) rebuy_dialog.getRebuy_spinner().getValue())));
                this.rebuy_now_menu.setBackground(Color.YELLOW);
                this.rebuy_now_menu.setOpaque(true);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(Color.YELLOW);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(true);

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        crupier.rebuyNow(player.getNickname(), (int) rebuy_dialog.getRebuy_spinner().getValue());
                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                rebuy_now_menu.setEnabled(true);
                                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                                rebuy_dialog = null;
                            }
                        });
                        Audio.playWavResource("misc/button_on.wav");

                    }
                });
            } else {
                rebuy_now_menu.setEnabled(true);
                rebuy_now_menu.setSelected(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                this.rebuy_now_menu.setBackground(null);
                this.rebuy_now_menu.setOpaque(false);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setBackground(null);
                Helpers.TapetePopupMenu.REBUY_NOW_MENU.setOpaque(false);
                rebuy_dialog = null;
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

    private void auto_fit_zoom_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_fit_zoom_menuActionPerformed
        // TODO add your handling code here:
        GameFrame.AUTO_ZOOM = auto_fit_zoom_menu.isSelected();

        if (auto_fit_zoom_menu.isSelected()) {

            auto_fit_zoom_menu.setEnabled(false);

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {

                    tapete.autoZoom(false);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            auto_fit_zoom_menu.setEnabled(true);
                        }
                    });

                }
            });
        }

        Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(auto_fit_zoom_menu.isSelected()));

        Helpers.savePropertiesFile();

        Helpers.TapetePopupMenu.AUTO_ZOOM_MENU.setSelected(GameFrame.AUTO_ZOOM);
    }//GEN-LAST:event_auto_fit_zoom_menuActionPerformed

    private void robert_rules_menuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_robert_rules_menuActionPerformed
        // TODO add your handling code here:
        Helpers.openBrowserURL("https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf");
    }//GEN-LAST:event_robert_rules_menuActionPerformed

    private void menu_tapete_negroActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_tapete_negroActionPerformed
        // TODO add your handling code here:
        if (Init.M2 != null && tapete_counter == 4 && GameFrame.COLOR_TAPETE.equals("negro")) {
            GameFrame.COLOR_TAPETE = "negro*";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setEnabled(false);
            }

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            menu_tapete_negro.setSelected(true);

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setSelected(false);
                            }

                            Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);

                            for (Component c : menu_tapetes.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                                ((JRadioButtonMenuItem) c).setEnabled(true);
                            }

                            tapete.refresh();

                            cambiarColorContadoresTapete(Color.WHITE);

                        }
                    });

                    tapete_counter = 0;
                }
            });

        } else if (!GameFrame.COLOR_TAPETE.equals("negro*")) {

            if (GameFrame.COLOR_TAPETE.equals("negro")) {
                tapete_counter++;
            } else {
                tapete_counter = 1;
            }

            GameFrame.COLOR_TAPETE = "negro";

            Helpers.PROPERTIES.setProperty("color_tapete", GameFrame.COLOR_TAPETE);

            Helpers.savePropertiesFile();

            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_negro.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);

            tapete.refresh();

            cambiarColorContadoresTapete(Color.LIGHT_GRAY);

        } else {
            for (Component c : this.menu_tapetes.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            this.menu_tapete_negro.setSelected(true);

            for (Component c : Helpers.TapetePopupMenu.TAPETES_MENU.getMenuComponents()) {
                ((JRadioButtonMenuItem) c).setSelected(false);
            }

            Helpers.TapetePopupMenu.TAPETE_NEGRO.setSelected(true);
        }
    }//GEN-LAST:event_menu_tapete_negroActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem acerca_menu;
    private javax.swing.JCheckBoxMenuItem animacion_menu;
    private javax.swing.JCheckBoxMenuItem ascensor_menu;
    private javax.swing.JCheckBoxMenuItem auto_action_menu;
    private javax.swing.JCheckBoxMenuItem auto_fit_zoom_menu;
    private javax.swing.JMenuItem chat_menu;
    private javax.swing.JCheckBoxMenuItem compact_menu;
    private javax.swing.JCheckBoxMenuItem confirmar_menu;
    private javax.swing.JPopupMenu.Separator decks_separator;
    private javax.swing.JMenuItem exit_menu;
    private javax.swing.JMenu file_menu;
    private javax.swing.JMenuItem full_screen_menu;
    private javax.swing.JMenu help_menu;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
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
    private javax.swing.JRadioButtonMenuItem menu_tapete_negro;
    private javax.swing.JRadioButtonMenuItem menu_tapete_rojo;
    private javax.swing.JRadioButtonMenuItem menu_tapete_verde;
    private javax.swing.JMenu menu_tapetes;
    private javax.swing.JMenu opciones_menu;
    private javax.swing.JCheckBoxMenuItem rebuy_now_menu;
    private javax.swing.JMenuItem registro_menu;
    private javax.swing.JMenuItem robert_rules_menu;
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
