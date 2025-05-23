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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import static com.tonikelope.coronapoker.InGameNotifyDialog.NOTIFICATION_TIMEOUT;

/**
 *
 * @author tonikelope
 */
public class Init extends JFrame {

    public static final boolean DEV_MODE = false;

    public static final boolean DEBUG_FILE = true;
    public static final String CORONA_DIR = System.getProperty("user.home") + "/.coronapoker";
    public static final String LOGS_DIR = CORONA_DIR + "/Logs";
    public static final String RADAR_DIR = CORONA_DIR + "/RADAR";
    public static final String DEBUG_DIR = CORONA_DIR + "/Debug";
    public static final String GIFSICLE_DIR = CORONA_DIR + "/gifsicle";
    public static final String SETDPI_DIR = CORONA_DIR + "/setdpi";
    public static final String CACHE_DIR = CORONA_DIR + "/Cache";
    public static final String SCREENSHOTS_DIR = CORONA_DIR + "/Screenshots";
    public static final int DEADLOCK_DETECT_WAIT = 5000;
    public static String SQL_FILE;
    public static final int ANTI_SCREENSAVER_DELAY = 60000; //Ms
    public static final ConcurrentLinkedDeque<JDialog> CURRENT_MODAL_DIALOG = new ConcurrentLinkedDeque<>();
    public static final Object LOCK_CINEMATICS = new Object();
    public static final int QUOTE_DELAY = 8000;
    public static final String CORONA_INIT_IMAGE = "/images/corona_init.png";
    public static volatile String WINDOW_TITLE = "CoronaPoker " + AboutDialog.VERSION;
    public static volatile ConcurrentHashMap<String, Object> MOD = null;
    public static volatile Connection SQLITE = null;
    public static volatile boolean INIT = false;
    public static volatile Boolean ANTI_SCREENSAVER_KEY_PRESSED = false;
    public static volatile Init VENTANA_INICIO = null;
    public static volatile Method CORONA_HMAC_J1 = null;
    public static volatile Method CORONA_HMAC_VM = null;
    public static volatile Method M1 = null;
    public static volatile Method M2 = null;
    public static volatile Image I1 = null;
    public static volatile URL CORONA_INIT_MOD_IMAGE = null;
    public static volatile boolean PEGI18_MOD = false;
    public static volatile boolean PLAYING_CINEMATIC = false;
    public static volatile VolumeControlDialog VOLUME_DIALOG = null;
    private static volatile boolean FORCE_CLOSE_DIALOG = false;
    private static volatile String NEW_VERSION = null;
    private volatile Timer quote_timer = null;
    private volatile int conta_quote = 0;
    private volatile JTextPane quote = null;

    static {

        System.setProperty("sun.java2d.uiScale", "1");

        try {
            CORONA_HMAC_J1 = Class.forName("com.tonikelope.coronahmac.M").getMethod("J1", new Class<?>[]{byte[].class, byte[].class});
            CORONA_HMAC_VM = Class.forName("com.tonikelope.coronahmac.M").getMethod("VM", new Class<?>[]{});
        } catch (Exception ex) {

            if (Init.DEBUG_FILE) {
                try {
                    PrintStream fileOut = new PrintStream(new File(Init.DEBUG_DIR + "/CORONAPOKER_DEBUG_" + Helpers.getFechaHoraActual("dd_MM_yyyy__HH_mm_ss") + ".log"));
                    System.setOut(fileOut);
                    System.setErr(fileOut);
                } catch (FileNotFoundException ex1) {
                    Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

            Logger.getLogger(Init.class.getName()).log(Level.WARNING, "CoronaHMAC is not present!");
        }
        
        Logger.getLogger(Init.class.getName()).log(Level.INFO, System.getProperty("os.name"));
        
        if (Helpers.OSValidator.isUnix()) {
            System.setProperty("sun.java2d.opengl", "true");
            System.setProperty("sun.java2d.d3d", "false");
        }
        
        try {

            M1 = Class.forName("com.tonikelope.coronapoker.Huevos").getMethod("M1", new Class<?>[]{JDialog.class, String.class});

            M2 = Class.forName("com.tonikelope.coronapoker.Huevos").getMethod("M2", new Class<?>[]{String.class});

            try {

                I1 = ImageIO.read(new ByteArrayInputStream((byte[]) M2.invoke(null, "d")));

            } catch (Exception ex) {

                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (Exception ex) {
            Logger.getLogger(Init.class.getName()).log(Level.WARNING, "Huevos is not present!");
        }

    }

    public JLabel getBaraja_fondo() {
        return baraja_fondo;
    }

    public static boolean coronaHMACVM() {

        if (CORONA_HMAC_VM != null) {
            try {
                return (boolean) CORONA_HMAC_VM.invoke(null);
            } catch (Exception ex) {
                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return false;
    }

    public static String coronaHMACJ1(byte[] a, byte[] b) {

        if (CORONA_HMAC_J1 != null) {

            try {
                return (String) CORONA_HMAC_J1.invoke(null, a, b);
            } catch (Exception ex) {
                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return "MjEvMTIvMTk4NA==";
    }

    public JLabel getUpdate_label() {
        return update_label;
    }

    private void printQuote() {
        Helpers.threadRun(() -> {
            if (conta_quote % Helpers.POKER_QUOTES_ES.size() == 0) {
                conta_quote = 0;
                Collections.shuffle(Helpers.POKER_QUOTES_ES, Helpers.CSPRNG_GENERATOR);
                Collections.shuffle(Helpers.POKER_QUOTES_EN, Helpers.CSPRNG_GENERATOR);
            }
            String[] quote_parts = (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE) ? Helpers.POKER_QUOTES_ES : Helpers.POKER_QUOTES_EN).get(conta_quote++).trim().split("#");
            Helpers.GUIRun(() -> {
                try {
                    quote.setText("\"" + new String(quote_parts[0].getBytes(), "UTF-8") + "\" (" + new String(quote_parts[1].getBytes(), "UTF-8") + ")");
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        });

    }

    /**
     * Creates new form Inicio
     */
    public Init() {

        initComponents();

        setTitle(Init.WINDOW_TITLE);

        quote = new JTextPane();

        StyledDocument doc = quote.getStyledDocument();

        SimpleAttributeSet center = new SimpleAttributeSet();

        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);

        doc.setParagraphAttributes(0, doc.getLength(), center, false);

        quote.setEditable(false);

        quote.setOpaque(false);

        quote.setBackground(new Color(0, 0, 0, 0));

        quote.setForeground(Color.white);

        Font font = new Font("Dialog", Font.ITALIC, 18);

        quote.setFont(font);

        quote.setVisible(false);

        tapete.add(quote, JLayeredPane.POPUP_LAYER);

        addComponentListener(new ComponentResizeEndListener() {
            @Override
            public void resizeTimedOut() {

                if (Init.VENTANA_INICIO.isVisible()) {
                    if (Init.VENTANA_INICIO.getWidth() <= 1920 || Init.VENTANA_INICIO.getHeight() <= 1080 - 150) {

                        int new_w = Init.VENTANA_INICIO.getWidth();

                        int new_h = Math.round(1080 * new_w / 1920);

                        if (new_h > Init.VENTANA_INICIO.getHeight() - 150) {
                            new_h = Init.VENTANA_INICIO.getHeight() - 150;

                            new_w = Math.round(1920 * new_h / 1080);
                        }

                        Helpers.setScaledIconLabel(Init.VENTANA_INICIO.getBaraja_fondo(), CORONA_INIT_MOD_IMAGE != null ? CORONA_INIT_MOD_IMAGE : getClass().getResource(CORONA_INIT_IMAGE), Math.round(new_w * 0.9f), Math.round(new_h * 0.9f));
                    } else {
                        Helpers.setScaledIconLabel(Init.VENTANA_INICIO.getBaraja_fondo(), CORONA_INIT_MOD_IMAGE != null ? CORONA_INIT_MOD_IMAGE : getClass().getResource(CORONA_INIT_IMAGE), Math.round(1920 * 0.9f), Math.round(1080 * 0.9f));
                    }

                    quote.setSize((int) getWidth(), 150);
                    quote.setLocation(0, Init.VENTANA_INICIO.getHeight() - 125);
                    quote.setVisible(true);

                    quote.revalidate();
                    quote.repaint();
                }
            }
        });

        if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
            language_combobox.setSelectedIndex(0);
        } else {
            language_combobox.setSelectedIndex(1);
        }

        create_button.setBackground(Color.WHITE);

        join_button.setBackground(Color.WHITE);

        update_label.setVisible(false);

        update_button.setVisible(false);

        update_button.setIcon(new ImageIcon(getClass().getResource("/images/update.png")));

        update_label.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));

        HashMap<KeyStroke, Action> actionMap = new HashMap<>();

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("SCREENSHOT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance() != null) {

                    Audio.playWavResource("misc/screenshot.wav");

                    Helpers.threadRun(() -> {
                        Helpers.screenshot(new Rectangle(GameFrame.getInstance().getTapete().getLocationOnScreen(), GameFrame.getInstance().getTapete().getSize()), null);
                        Helpers.GUIRun(() -> {
                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, "CAPTURA OK", Color.WHITE, Color.BLACK, getClass().getResource("/images/screenshot.png"), NOTIFICATION_TIMEOUT);
                            dialog.setLocation(dialog.getParent().getLocation());
                            dialog.setVisible(true);
                        });
                    });
                }

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK), new AbstractAction("SOUND-SWITCH") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance() != null) {
                    GameFrame.getInstance().getSonidos_menu().doClick();
                } else if (VENTANA_INICIO.isVisible()) {
                    sound_iconMouseClicked(null);
                } else {
                    WaitingRoomFrame.getInstance().soundIconClick();
                }
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), new AbstractAction("VOLUME-DOWN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (Audio.MASTER_VOLUME > 0f) {
                    Audio.MASTER_VOLUME = Helpers.floatClean(Audio.MASTER_VOLUME - 0.01f, 2);

                    if (Audio.VOLUME_TIMER.isRunning()) {
                        Audio.VOLUME_TIMER.restart();
                    } else {
                        Audio.VOLUME_TIMER.start();
                    }

                    if (!GameFrame.SONIDOS) {
                        if (GameFrame.getInstance() != null) {
                            GameFrame.getInstance().getSonidos_menu().doClick();
                        } else if (VENTANA_INICIO.isVisible()) {
                            sound_iconMouseClicked(null);
                        } else {
                            WaitingRoomFrame.getInstance().soundIconClick();
                        }
                    }
                }

                if (VOLUME_DIALOG != null) {
                    VOLUME_DIALOG.refresh();
                } else {

                    if (!CURRENT_MODAL_DIALOG.isEmpty()) {
                        VOLUME_DIALOG = new VolumeControlDialog(CURRENT_MODAL_DIALOG.peekLast(), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getWidth() : VENTANA_INICIO.getWidth())));
                    } else {

                        VOLUME_DIALOG = new VolumeControlDialog(GameFrame.getInstance() != null ? GameFrame.getInstance() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getWidth() : VENTANA_INICIO.getWidth())));
                    }
                    VOLUME_DIALOG.setLocationRelativeTo(GameFrame.getInstance() != null ? GameFrame.getInstance() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()));
                    VOLUME_DIALOG.refresh();
                }
            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), new AbstractAction("VOLUME-UP") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!GameFrame.SONIDOS) {
                    if (GameFrame.getInstance() != null) {
                        GameFrame.getInstance().getSonidos_menu().doClick();
                    } else if (VENTANA_INICIO.isVisible()) {
                        sound_iconMouseClicked(null);
                    } else {
                        WaitingRoomFrame.getInstance().soundIconClick();
                    }
                }

                if (Audio.MASTER_VOLUME < 1.0f) {
                    Audio.MASTER_VOLUME = Helpers.floatClean(Audio.MASTER_VOLUME + 0.01f, 2);

                    if (Audio.VOLUME_TIMER.isRunning()) {
                        Audio.VOLUME_TIMER.restart();
                    } else {
                        Audio.VOLUME_TIMER.start();
                    }
                }

                if (VOLUME_DIALOG != null) {
                    VOLUME_DIALOG.refresh();
                } else {

                    if (!CURRENT_MODAL_DIALOG.isEmpty()) {
                        VOLUME_DIALOG = new VolumeControlDialog(CURRENT_MODAL_DIALOG.peekLast(), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getWidth() : VENTANA_INICIO.getWidth())));
                    } else {

                        VOLUME_DIALOG = new VolumeControlDialog(GameFrame.getInstance() != null ? GameFrame.getInstance() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getWidth() : VENTANA_INICIO.getWidth())));
                    }
                    VOLUME_DIALOG.setLocationRelativeTo(GameFrame.getInstance() != null ? GameFrame.getInstance() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()));
                    VOLUME_DIALOG.refresh();
                }

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK), new AbstractAction("FORCE_EXIT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!FORCE_CLOSE_DIALOG) {

                    FORCE_CLOSE_DIALOG = true;

                    if (Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, "¿FORZAR CIERRE?", new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {

                        System.exit(1);
                    }

                    FORCE_CLOSE_DIALOG = false;
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        kfm.addKeyEventDispatcher((KeyEvent e) -> {
            KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
            if (actionMap.containsKey(keyStroke)) {
                final Action a = actionMap.get(keyStroke);
                final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null);
                Helpers.GUIRun(() -> {
                    a.actionPerformed(ae);
                });
                return true;
            }
            return false;
        });

        quote_timer = new Timer(QUOTE_DELAY, (ActionEvent ae) -> {
            printQuote();
        });

        quote_timer.setInitialDelay(0);

        Helpers.setScaledIconLabel(baraja_fondo, CORONA_INIT_MOD_IMAGE != null ? CORONA_INIT_MOD_IMAGE : getClass().getResource(CORONA_INIT_IMAGE), Math.round(1920 * 0.9f), Math.round(1080 * 0.9f));

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        Helpers.setScaledIconButton(stats_button, getClass().getResource("/images/stats.png"), stats_button.getHeight(), stats_button.getHeight());

        revalidate();

        repaint();

    }

    public InitPanel getTapete() {
        return tapete;
    }

    public void translateGlobalLabels() {
        LocalPlayer.ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? LocalPlayer.ACTIONS_LABELS_ES : LocalPlayer.ACTIONS_LABELS_EN;
        LocalPlayer.POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? LocalPlayer.POSITIONS_LABELS_ES : LocalPlayer.POSITIONS_LABELS_EN;
        RemotePlayer.ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? RemotePlayer.ACTIONS_LABELS_ES : RemotePlayer.ACTIONS_LABELS_EN;
        RemotePlayer.POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? RemotePlayer.POSITIONS_LABELS_ES : RemotePlayer.POSITIONS_LABELS_EN;
        Hand.NOMBRES_JUGADAS = GameFrame.LANGUAGE.equals("es") ? Hand.NOMBRES_JUGADAS_ES : Hand.NOMBRES_JUGADAS_EN;

    }

    public void continueLastGame(boolean local) {

        NewGameDialog dialog = new NewGameDialog(this, true, local);

        if (GameFrame.PASSWORD_RECOVER != null) {
            dialog.setPass(GameFrame.PASSWORD_RECOVER);
        }

        dialog.setForce_recover(true);

        if (local) {
            dialog.getRecover_checkbox().doClick();
        }

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setEnabled(false);

        dialog.setVisible(true);

        setEnabled(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
            GameFrame.IWTSTH_RULE_RECOVER = null;
            GameFrame.RABBIT_HUNTING_RECOVER = null;
            GameFrame.PASSWORD_RECOVER = null;
        } else {
            setVisible(false);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tapete = new com.tonikelope.coronapoker.InitPanel();
        botones_panel = new javax.swing.JPanel();
        corona_init_panel = new javax.swing.JPanel();
        update_label = new javax.swing.JLabel();
        update_button = new javax.swing.JButton();
        action_buttons_panel = new javax.swing.JPanel();
        join_button = new javax.swing.JButton();
        stats_button = new javax.swing.JButton();
        create_button = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        sound_icon = new javax.swing.JLabel();
        exit_button = new javax.swing.JButton();
        language_combobox = new javax.swing.JComboBox<>();
        baraja_panel = new javax.swing.JPanel();
        baraja_fondo = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CoronaPoker");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                formComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        botones_panel.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.orange, 15));
        botones_panel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        botones_panel.setOpaque(false);

        corona_init_panel.setOpaque(false);

        update_label.setBackground(new java.awt.Color(0, 102, 255));
        update_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        update_label.setForeground(new java.awt.Color(255, 255, 255));
        update_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        update_label.setText("COMPROBANDO ACTUALIZACIÓN...");
        update_label.setOpaque(true);

        update_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        update_button.setText("ACTUALIZAR");
        update_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        update_button.setDoubleBuffered(true);
        update_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                update_buttonActionPerformed(evt);
            }
        });

        action_buttons_panel.setOpaque(false);

        join_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        join_button.setForeground(new java.awt.Color(102, 0, 204));
        join_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/unirme.png"))); // NOI18N
        join_button.setText("UNIRME A TIMBA");
        join_button.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 102, 0), 8, true));
        join_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        join_button.setDoubleBuffered(true);
        join_button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                join_buttonMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                join_buttonMouseExited(evt);
            }
        });
        join_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                join_buttonActionPerformed(evt);
            }
        });

        stats_button.setBackground(new java.awt.Color(255, 102, 0));
        stats_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        stats_button.setForeground(new java.awt.Color(255, 255, 255));
        stats_button.setText("ESTADÍSTICAS");
        stats_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        stats_button.setDoubleBuffered(true);
        stats_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stats_buttonActionPerformed(evt);
            }
        });

        create_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        create_button.setForeground(new java.awt.Color(102, 0, 204));
        create_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/crear.png"))); // NOI18N
        create_button.setText("CREAR TIMBA");
        create_button.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 102, 0), 8, true));
        create_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        create_button.setDoubleBuffered(true);
        create_button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                create_buttonMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                create_buttonMouseExited(evt);
            }
        });
        create_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                create_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout action_buttons_panelLayout = new javax.swing.GroupLayout(action_buttons_panel);
        action_buttons_panel.setLayout(action_buttons_panelLayout);
        action_buttons_panelLayout.setHorizontalGroup(
            action_buttons_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(action_buttons_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(action_buttons_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(action_buttons_panelLayout.createSequentialGroup()
                        .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, 350, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(join_button, javax.swing.GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE))
                    .addComponent(stats_button, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        action_buttons_panelLayout.setVerticalGroup(
            action_buttons_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(action_buttons_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(action_buttons_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(join_button, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(stats_button)
                .addGap(0, 0, 0))
        );

        jPanel1.setOpaque(false);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        exit_button.setBackground(new java.awt.Color(204, 0, 0));
        exit_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        exit_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/exit2.png"))); // NOI18N
        exit_button.setText("SALIR");
        exit_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        exit_button.setDoubleBuffered(true);
        exit_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exit_buttonActionPerformed(evt);
            }
        });

        language_combobox.setFont(new java.awt.Font("sansserif", 0, 20)); // NOI18N
        language_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Español", "English" }));
        language_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        language_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                language_comboboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(exit_button, javax.swing.GroupLayout.DEFAULT_SIZE, 475, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(language_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(language_combobox, javax.swing.GroupLayout.DEFAULT_SIZE, 58, Short.MAX_VALUE)
                    .addComponent(exit_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(sound_icon, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 58, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout corona_init_panelLayout = new javax.swing.GroupLayout(corona_init_panel);
        corona_init_panel.setLayout(corona_init_panelLayout);
        corona_init_panelLayout.setHorizontalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(update_button, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(update_label, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addComponent(action_buttons_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        corona_init_panelLayout.setVerticalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(corona_init_panelLayout.createSequentialGroup()
                .addComponent(action_buttons_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(update_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(update_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout botones_panelLayout = new javax.swing.GroupLayout(botones_panel);
        botones_panel.setLayout(botones_panelLayout);
        botones_panelLayout.setHorizontalGroup(
            botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(botones_panelLayout.createSequentialGroup()
                    .addGap(10, 10, 10)
                    .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(10, 10, 10)))
        );
        botones_panelLayout.setVerticalGroup(
            botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
            .addGroup(botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(botones_panelLayout.createSequentialGroup()
                    .addGap(10, 10, 10)
                    .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(10, 10, 10)))
        );

        baraja_panel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        baraja_panel.setOpaque(false);

        baraja_fondo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        baraja_fondo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        baraja_fondo.setDoubleBuffered(true);
        baraja_fondo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                baraja_fondoMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout baraja_panelLayout = new javax.swing.GroupLayout(baraja_panel);
        baraja_panel.setLayout(baraja_panelLayout);
        baraja_panelLayout.setHorizontalGroup(
            baraja_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, baraja_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(baraja_fondo, javax.swing.GroupLayout.DEFAULT_SIZE, 2878, Short.MAX_VALUE)
                .addContainerGap())
        );
        baraja_panelLayout.setVerticalGroup(
            baraja_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_fondo, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 2009, Short.MAX_VALUE)
        );

        tapete.setLayer(botones_panel, javax.swing.JLayeredPane.POPUP_LAYER);
        tapete.setLayer(baraja_panel, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout tapeteLayout = new javax.swing.GroupLayout(tapete);
        tapete.setLayout(tapeteLayout);
        tapeteLayout.setHorizontalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(tapeteLayout.createSequentialGroup()
                    .addContainerGap(996, Short.MAX_VALUE)
                    .addComponent(botones_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(996, Short.MAX_VALUE)))
        );
        tapeteLayout.setVerticalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(tapeteLayout.createSequentialGroup()
                    .addContainerGap(788, Short.MAX_VALUE)
                    .addComponent(botones_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(788, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tapete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tapete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.GUIRun(() -> {
            Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);
        });

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void language_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_language_comboboxActionPerformed
        // TODO add your handling code here:
        if (VENTANA_INICIO != null) {

            GameFrame.LANGUAGE = language_combobox.getSelectedIndex() == 0 ? "es" : "en";

            Helpers.PROPERTIES.setProperty("lenguaje", GameFrame.LANGUAGE);

            Helpers.savePropertiesFile();

            Helpers.translateComponents(this, true);

            translateGlobalLabels();

            Crupier.loadMODSounds();

            Helpers.setCoronaLocale();

            printQuote();
        }
    }//GEN-LAST:event_language_comboboxActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);
        if (quote_timer != null) {
            if (quote_timer.isRunning()) {
                quote_timer.restart();
            } else {
                quote_timer.start();
            }
        }

    }//GEN-LAST:event_formComponentShown

    private void update_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_update_buttonActionPerformed
        // TODO add your handling code here:
        UPDATE();
    }//GEN-LAST:event_update_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
        Helpers.savePropertiesFile();

    }//GEN-LAST:event_formWindowClosing

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentHidden
        // TODO add your handling code here:
        if (quote_timer.isRunning()) {
            quote_timer.stop();
        }
    }//GEN-LAST:event_formComponentHidden

    private void baraja_fondoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_baraja_fondoMouseClicked
        // TODO add your handling code here:

        if (!botones_panel.getBounds().contains(evt.getX(), evt.getY())) {

            AboutDialog dialog = new AboutDialog(this, true);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }
    }//GEN-LAST:event_baraja_fondoMouseClicked

    private void exit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exit_buttonActionPerformed
        // TODO add your handling code here:
        WindowEvent windowEvent = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
        processWindowEvent(windowEvent);
    }//GEN-LAST:event_exit_buttonActionPerformed

    private void create_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_create_buttonActionPerformed
        // TODO add your handling code here:

        NewGameDialog dialog = new NewGameDialog(this, true, true);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            setVisible(false);
        }
    }//GEN-LAST:event_create_buttonActionPerformed

    private void create_buttonMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_create_buttonMouseExited
        // TODO add your handling code here:
        create_button.setForeground(new Color(102, 0, 204));
        create_button.setBackground(Color.WHITE);
    }//GEN-LAST:event_create_buttonMouseExited

    private void create_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_create_buttonMouseEntered
        // TODO add your handling code here:
        create_button.setBackground(new Color(102, 0, 204));
        create_button.setForeground(Color.WHITE);
    }//GEN-LAST:event_create_buttonMouseEntered

    private void stats_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stats_buttonActionPerformed
        // TODO add your handling code here:
        StatsDialog dialog = new StatsDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_stats_buttonActionPerformed

    private void join_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_join_buttonActionPerformed
        // TODO add your handling code here:

        NewGameDialog dialog = new NewGameDialog(this, true, false);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            setVisible(false);
        }
    }//GEN-LAST:event_join_buttonActionPerformed

    private void join_buttonMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_join_buttonMouseExited
        // TODO add your handling code here:
        join_button.setForeground(new Color(102, 0, 204));
        join_button.setBackground(Color.WHITE);
    }//GEN-LAST:event_join_buttonMouseExited

    private void join_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_join_buttonMouseEntered
        // TODO add your handling code here:
        join_button.setBackground(new Color(102, 0, 204));
        join_button.setForeground(Color.WHITE);
    }//GEN-LAST:event_join_buttonMouseEntered

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        if (!INIT) {

            INIT = true;

            Helpers.threadRun(() -> {

                //Deadlock detection
                while (true) {
                    Helpers.detectAndHandleDeadlocks();
                    Helpers.pausar(DEADLOCK_DETECT_WAIT);
                }

            });

            if (GameFrame.TEST_MODE) {
                GameFrame.CINEMATICAS = false;
            }

            if (!Init.DEV_MODE) {
                SQL_FILE = CORONA_DIR + "/coronapoker.db";
            } else {
                if (Files.exists(Paths.get(CORONA_DIR + "/coronapoker.db"))) {

                    try {
                        File db = File.createTempFile("coronapoker_", ".db");
                        Files.copy(Paths.get(CORONA_DIR + "/coronapoker.db"), db.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        SQL_FILE = db.getAbsolutePath();
                    } catch (IOException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            /* Set the Nimbus look and feel */
            //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
            /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
             */
            try {
                for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        javax.swing.UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
                Logger.getLogger(Init.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            //</editor-fold>

            EmojiPanel.initClass();

            Helpers.setCoronaLocale();

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "Loading SQLITE DB...");

            Helpers.initSQLITE();

            try {
                Logger.getLogger(Init.class.getName()).log(Level.INFO, "Trying to load CSPRNG HASH DRBG SHA-512...");
                Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,reseed_only");
                Helpers.CSPRNG_GENERATOR = SecureRandom.getInstance("DRBG");
                Logger.getLogger(Init.class.getName()).log(Level.INFO, "CSPRNG OK");
            } catch (NoSuchAlgorithmException ex) {

                Helpers.CSPRNG_GENERATOR = new SecureRandom();
                Logger.getLogger(Init.class.getName()).log(Level.WARNING, "CSPRNG -> {0}", Helpers.CSPRNG_GENERATOR.getAlgorithm());
            }

            Helpers.GUI_FONT = Helpers.createAndRegisterFont(Helpers.class.getResourceAsStream("/fonts/McLaren-Regular.ttf"));

            Helpers.updateCoronaDialogsFont();

            Init.MOD = Helpers.loadMOD();

            if (Init.MOD != null) {

                WINDOW_TITLE += " @ " + MOD.get("name") + " " + MOD.get("version");

                PEGI18_MOD = (MOD.containsKey("adults") && (boolean) MOD.get("adults"));

                if ((boolean) MOD.get("init_background")) {
                    try {
                        CORONA_INIT_MOD_IMAGE = new File(Helpers.getCurrentJarParentPath() + "/mod/init.png").toURI().toURL();
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                //Cargamos las barajas del MOD
                for (Map.Entry<String, HashMap> entry : ((HashMap<String, HashMap>) Init.MOD.get("decks")).entrySet()) {

                    HashMap<String, Object> baraja = entry.getValue();

                    Card.BARAJAS.put((String) baraja.get("name"), new Object[]{baraja.get("aspect"), true, baraja.containsKey("sound") ? baraja.get("sound") : null});
                }

                if (Init.MOD.containsKey("fusion_sounds")) {
                    Crupier.FUSION_MOD_SOUNDS = (boolean) Init.MOD.get("fusion_sounds");
                }

                if (Init.MOD.containsKey("fusion_cinematics")) {
                    Crupier.FUSION_MOD_CINEMATICS = (boolean) Init.MOD.get("fusion_cinematics");
                }

                Crupier.loadMODSounds();

                Crupier.loadMODCinematicsAllin();

                //Actualizamos la fuente
                if (Init.MOD.containsKey("font") && Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")))) {

                    try {
                        Helpers.GUI_FONT = Helpers.createAndRegisterFont(new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")));
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }

            if (!Card.BARAJAS.containsKey(GameFrame.BARAJA)) {
                GameFrame.BARAJA = GameFrame.BARAJA_DEFAULT;
            }

            Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

            Audio.MASTER_VOLUME = Float.parseFloat(Helpers.PROPERTIES.getProperty("master_volume", "0.8"));

            if (!GameFrame.SONIDOS) {

                Audio.muteAll();

            } else {

                Audio.unmuteAll();

            }

            Audio.playWavResource("misc/init.wav");

            Audio.playLoopMp3Resource("misc/background_music.mp3");

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "Loading INIT WINDOW...");

            Helpers.GUIRun(() -> {
                VENTANA_INICIO = new Init();

                VENTANA_INICIO.setExtendedState(JFrame.MAXIMIZED_BOTH);

                VENTANA_INICIO.setVisible(true);
            });

            if (PEGI18_MOD && !Files.isReadable(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/.pegi18_warning"))) {

                if (Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, "EL MOD CARGADO CONTIENE MATERIAL CALIFICADO SÓLO PARA MAYORES DE 18 AÑOS. ¿Continuar?", new ImageIcon(Init.class.getResource("/images/pegi18.png"))) == 0) {
                    try {
                        Files.createFile(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/.pegi18_warning"));
                    } catch (IOException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {

                    System.exit(0);
                }
            }

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "CHECKING UPDATE...");

            UPDATE();

            if (!Helpers.OSValidator.isMac()) {
                antiScreensaver();
            }

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "LET'S GO");
        }
    }

    private static void UPDATE() {

        Helpers.threadRun(() -> {
            Helpers.GUIRun(() -> {
                VENTANA_INICIO.action_buttons_panel.setEnabled(false);
                VENTANA_INICIO.update_label.setVisible(true);
                VENTANA_INICIO.update_button.setVisible(false);
            });
            do {
                NEW_VERSION = Helpers.checkLatestCoronaPokerVersion(AboutDialog.UPDATE_URL);
                if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
                    if (VENTANA_INICIO.isVisible() && VENTANA_INICIO.isActive() && Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, "HAY UNA VERSIÓN NUEVA DE CORONAPOKER. ¿QUIERES ACTUALIZAR?", new ImageIcon(Init.class.getResource("/images/avatar_default.png"))) == 0) {
                        Helpers.GUIRun(() -> {
                            VENTANA_INICIO.update_label.setText(Translator.translate("PREPARANDO ACTUALIZACIÓN..."));
                        });
                        try {

                            String current_jar_path = Helpers.getCurrentJarPath();

                            String new_jar_path = current_jar_path.replaceAll(AboutDialog.VERSION + ".jar", NEW_VERSION + ".jar");

                            String updater_jar = Helpers.downloadUpdater();

                            if (updater_jar != null) {

                                Helpers.cleanCacheDIR();

                                if (GameFrame.LANGUAGE.equals("es")) {
                                    String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, NEW_VERSION, current_jar_path, new_jar_path, "¡Santiago y cierra, España!"};

                                    Runtime.getRuntime().exec(cmdArr);
                                } else {
                                    String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, NEW_VERSION, current_jar_path, new_jar_path};

                                    Runtime.getRuntime().exec(cmdArr);
                                }

                                System.exit(0);
                            } else {
                                Helpers.mostrarMensajeError(VENTANA_INICIO, "NO SE HA PODIDO ACTUALIZAR (ERROR AL DESCARGAR EL ACTUALIZADOR)");
                            }

                        } catch (Exception ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                            Helpers.mostrarMensajeError(VENTANA_INICIO, "NO SE HA PODIDO ACTUALIZAR (ERROR INESPERADO)");
                        }
                    }
                }
            } while (NEW_VERSION == null && Helpers.mostrarMensajeErrorSINO(VENTANA_INICIO, "NO SE HA PODIDO COMPROBAR SI HAY NUEVA VERSIÓN. ¿Volvemos a intentarlo?") == 0);
            if (Init.MOD != null) {
                Logger.getLogger(Init.class.getName()).log(Level.INFO, "CHECKING MOD UPDATE...");
                Helpers.checkMODVersion(VENTANA_INICIO);
            }
            Helpers.GUIRun(() -> {
                VENTANA_INICIO.update_label.setVisible(false);

                if (NEW_VERSION == null || !NEW_VERSION.isBlank()) {
                    VENTANA_INICIO.update_button.setVisible(true);
                }

                VENTANA_INICIO.action_buttons_panel.setEnabled(true);
            });
        });

    }

    private static void antiScreensaver() {

        java.util.Timer screensaver = new java.util.Timer();

        screensaver.schedule(new TimerTask() {
            @Override
            public void run() {
                if (GameFrame.getInstance() != null && GameFrame.getInstance().isFull_screen()) {

                    try {

                        Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
                        Robot rob = new Robot();
                        rob.mouseMove(mouseLoc.x, mouseLoc.y);

                    } catch (AWTException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

        }, ANTI_SCREENSAVER_DELAY, ANTI_SCREENSAVER_DELAY);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel action_buttons_panel;
    private javax.swing.JLabel baraja_fondo;
    private javax.swing.JPanel baraja_panel;
    private javax.swing.JPanel botones_panel;
    private javax.swing.JPanel corona_init_panel;
    private javax.swing.JButton create_button;
    private javax.swing.JButton exit_button;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton join_button;
    private javax.swing.JComboBox<String> language_combobox;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JButton stats_button;
    private com.tonikelope.coronapoker.InitPanel tapete;
    private javax.swing.JButton update_button;
    private javax.swing.JLabel update_label;
    // End of variables declaration//GEN-END:variables
}
