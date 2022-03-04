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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author tonikelope
 */
public class Init extends javax.swing.JFrame {

    public static final boolean DEV_MODE = false;
    public static final String CORONA_DIR = System.getProperty("user.home") + "/.coronapoker";
    public static final String LOGS_DIR = CORONA_DIR + "/Logs";
    public static final String DEBUG_DIR = CORONA_DIR + "/Debug";
    public static final String SCREENSHOTS_DIR = CORONA_DIR + "/Screenshots";
    public static final String SQL_FILE = CORONA_DIR + "/coronapoker.db";
    public static final int ANTI_SCREENSAVER_DELAY = 60000; //Ms
    public static final ConcurrentLinkedDeque<JDialog> CURRENT_MODAL_DIALOG = new ConcurrentLinkedDeque<>();
    public static final Object LOCK_CINEMATICS = new Object();
    public static final int QUOTE_DELAY = 10000;
    public static volatile String WINDOW_TITLE = "CoronaPoker " + AboutDialog.VERSION;
    public static volatile ConcurrentHashMap<String, Object> MOD = null;
    public static volatile Connection SQLITE = null;
    public static volatile boolean INIT = false;
    public static volatile Boolean ANTI_SCREENSAVER_KEY_PRESSED = false;
    public static volatile Init VENTANA_INICIO = null;
    public static volatile Method CORONA_HMAC_J1 = null;
    public static volatile Method M1 = null;
    public static volatile Method M2 = null;
    public static volatile Image I1 = null;
    public static volatile boolean PLAYING_CINEMATIC = false;
    public static volatile VolumeControlDialog VOLUME_DIALOG = null;
    private static volatile boolean FORCE_CLOSE_DIALOG = false;
    private static volatile String NEW_VERSION = null;
    private volatile int k = 0;
    private volatile GifAnimationDialog gif_dialog = null;
    private volatile Timer quote_timer = null;
    private volatile int conta_quote = 0;

    static {

        try {
            CORONA_HMAC_J1 = Class.forName("com.tonikelope.coronahmac.M").getMethod("J1", new Class<?>[]{byte[].class, byte[].class});
        } catch (Exception ex) {

            if (!Init.DEV_MODE) {
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

    private void printQuote() {
        Helpers.threadRun(new Runnable() {
            public void run() {
                if (conta_quote % Helpers.POKER_QUOTES_ES.size() == 0) {
                    conta_quote = 0;
                    Collections.shuffle(Helpers.POKER_QUOTES_ES, Helpers.CSPRNG_GENERATOR);
                    Collections.shuffle(Helpers.POKER_QUOTES_EN, Helpers.CSPRNG_GENERATOR);
                }

                String[] quote_parts = (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE) ? Helpers.POKER_QUOTES_ES : Helpers.POKER_QUOTES_EN).get(conta_quote++).trim().split("#");

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            quote.setText("\"" + new String(quote_parts[0].getBytes(), "UTF-8") + "\" (" + new String(quote_parts[1].getBytes(), "UTF-8") + ")");
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                });
            }
        });

    }

    /**
     * Creates new form Inicio
     */
    public Init() {

        initComponents();

        setTitle(Init.WINDOW_TITLE);

        if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
            language_combobox.setSelectedIndex(0);
        } else {
            language_combobox.setSelectedIndex(1);
        }

        VENTANA_INICIO = this;

        StyledDocument doc = quote.getStyledDocument();

        SimpleAttributeSet center = new SimpleAttributeSet();

        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);

        doc.setParagraphAttributes(0, doc.getLength(), center, false);

        quote.setOpaque(false);

        quote.setBackground(new Color(0, 0, 0, 0));

        panel_textarea.getViewport().setOpaque(false);

        panel_textarea.setViewportBorder(null);

        panel_textarea.setOpaque(false);

        update_button.setVisible(false);

        update_button.setIcon(new ImageIcon(getClass().getResource("/images/update.png")));

        update_label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/gears.gif")).getImage().getScaledInstance(64, 64, Image.SCALE_DEFAULT)));

        HashMap<KeyStroke, Action> actionMap = new HashMap<>();

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), new AbstractAction("SCREENSHOT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance() != null) {

                    Audio.playWavResource("misc/screenshot.wav");

                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            Helpers.screenshot(new Rectangle(GameFrame.getInstance().getTapete().getLocationOnScreen(), GameFrame.getInstance().getTapete().getSize()), null);

                            Helpers.GUIRun(new Runnable() {
                                public void run() {

                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance().getFrame(), false, "CAPTURA OK", Color.WHITE, Color.BLACK, getClass().getResource("/images/screenshot.png"), 2000);
                                    dialog.setVisible(true);

                                }
                            });
                        }
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
                        VOLUME_DIALOG = new VolumeControlDialog(CURRENT_MODAL_DIALOG.peekLast(), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame().getWidth() : VENTANA_INICIO.getWidth())));
                    } else {

                        VOLUME_DIALOG = new VolumeControlDialog(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame().getWidth() : VENTANA_INICIO.getWidth())));
                    }
                    VOLUME_DIALOG.setLocationRelativeTo(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()));
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
                        VOLUME_DIALOG = new VolumeControlDialog(CURRENT_MODAL_DIALOG.peekLast(), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame().getWidth() : VENTANA_INICIO.getWidth())));
                    } else {

                        VOLUME_DIALOG = new VolumeControlDialog(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()), false, Math.round(0.5f * (GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame().getWidth() : VENTANA_INICIO.getWidth())));
                    }
                    VOLUME_DIALOG.setLocationRelativeTo(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : (VENTANA_INICIO.isVisible() ? VENTANA_INICIO : WaitingRoomFrame.getInstance()));
                    VOLUME_DIALOG.refresh();
                }

            }
        });

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK), new AbstractAction("FORCE_EXIT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!FORCE_CLOSE_DIALOG) {

                    FORCE_CLOSE_DIALOG = true;

                    if (Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, "¿FORZAR CIERRE?") == 0) {
                        System.exit(1);
                    }

                    FORCE_CLOSE_DIALOG = false;
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        kfm.addKeyEventDispatcher(
                new KeyEventDispatcher() {

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
        }
        );

        quote_timer = new Timer(QUOTE_DELAY, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {

                printQuote();

            }
        });

        quote_timer.setInitialDelay(0);

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), 30, 30);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        Helpers.setScaledIconButton(stats_button, getClass().getResource("/images/stats.png"), stats_button.getHeight(), stats_button.getHeight());

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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tapete = new com.tonikelope.coronapoker.InitPanel();
        panel = new javax.swing.JPanel();
        corona_init_panel = new javax.swing.JPanel();
        sound_icon = new javax.swing.JLabel();
        krusty = new javax.swing.JLabel();
        create_button = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        join_button = new javax.swing.JButton();
        pegi_panel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        language_combobox = new javax.swing.JComboBox<>();
        stats_button = new javax.swing.JButton();
        update_label = new javax.swing.JLabel();
        update_button = new javax.swing.JButton();
        panel_textarea = new javax.swing.JScrollPane();
        quote = new javax.swing.JTextPane();

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

        panel.setOpaque(false);

        corona_init_panel.setOpaque(false);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        krusty.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/krusty.png"))); // NOI18N
        krusty.setToolTipText("Krusty sabe lo que se hace");
        krusty.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        krusty.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                krustyMouseClicked(evt);
            }
        });

        create_button.setBackground(new java.awt.Color(255, 255, 255));
        create_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        create_button.setForeground(new java.awt.Color(102, 0, 204));
        create_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/crear.png"))); // NOI18N
        create_button.setText("CREAR TIMBA");
        create_button.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 102, 0), 8, true));
        create_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        create_button.setDoubleBuffered(true);
        create_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                create_buttonActionPerformed(evt);
            }
        });

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        jLabel1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel1.setDoubleBuffered(true);
        jLabel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel1MouseClicked(evt);
            }
        });

        join_button.setBackground(new java.awt.Color(255, 255, 255));
        join_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        join_button.setForeground(new java.awt.Color(102, 0, 204));
        join_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/unirme.png"))); // NOI18N
        join_button.setText("UNIRME A TIMBA");
        join_button.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 102, 0), 8, true));
        join_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        join_button.setDoubleBuffered(true);
        join_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                join_buttonActionPerformed(evt);
            }
        });

        pegi_panel.setOpaque(false);

        jLabel3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_badlanguage.png"))); // NOI18N
        jLabel3.setToolTipText("Puede contener lenguaje soez");
        jLabel3.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_gambling.png"))); // NOI18N
        jLabel4.setToolTipText("Contiene apuestas con dinero ficticio");
        jLabel4.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi16.png"))); // NOI18N
        jLabel2.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_online.png"))); // NOI18N
        jLabel6.setToolTipText("Permite jugar online");
        jLabel6.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        javax.swing.GroupLayout pegi_panelLayout = new javax.swing.GroupLayout(pegi_panel);
        pegi_panel.setLayout(pegi_panelLayout);
        pegi_panelLayout.setHorizontalGroup(
            pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pegi_panelLayout.createSequentialGroup()
                .addComponent(jLabel2)
                .addGap(18, 18, 18)
                .addComponent(jLabel3)
                .addGap(18, 18, 18)
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(jLabel6))
        );
        pegi_panelLayout.setVerticalGroup(
            pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pegi_panelLayout.createSequentialGroup()
                .addGroup(pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel6)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addGap(0, 0, 0))
        );

        language_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Español", "English" }));
        language_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        language_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                language_comboboxActionPerformed(evt);
            }
        });

        stats_button.setBackground(new java.awt.Color(255, 153, 51));
        stats_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        stats_button.setForeground(new java.awt.Color(255, 255, 255));
        stats_button.setText("ESTADÍSTICAS");
        stats_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        stats_button.setDoubleBuffered(true);
        stats_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stats_buttonActionPerformed(evt);
            }
        });

        update_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        update_label.setForeground(new java.awt.Color(255, 255, 255));
        update_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        update_label.setText("COMPROBANDO ACTUALIZACIÓN...");

        update_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        update_button.setText("ACTUALIZAR");
        update_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        update_button.setDoubleBuffered(true);
        update_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                update_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout corona_init_panelLayout = new javax.swing.GroupLayout(corona_init_panel);
        corona_init_panel.setLayout(corona_init_panelLayout);
        corona_init_panelLayout.setHorizontalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(corona_init_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(update_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createSequentialGroup()
                        .addComponent(krusty)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pegi_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(corona_init_panelLayout.createSequentialGroup()
                                .addComponent(language_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(stats_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(join_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, 315, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(update_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        corona_init_panelLayout.setVerticalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(corona_init_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(corona_init_panelLayout.createSequentialGroup()
                        .addComponent(create_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(join_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jLabel1))
                .addGap(18, 18, Short.MAX_VALUE)
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(krusty, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(pegi_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(corona_init_panelLayout.createSequentialGroup()
                            .addComponent(stats_button)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(sound_icon, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(language_combobox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(update_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(update_label))
        );

        panel_textarea.setBorder(null);
        panel_textarea.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel_textarea.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        panel_textarea.setDoubleBuffered(true);

        quote.setEditable(false);
        quote.setBorder(null);
        quote.setFont(new java.awt.Font("Dialog", 2, 24)); // NOI18N
        quote.setForeground(new java.awt.Color(255, 255, 255));
        quote.setText("\"The strong point in poker is never to lose your temper, either with those you are playing or, more particularly with the cards. There is no sympathy in poker. Always keep cool. If you lose your head you will lose all your chips.\" (William J. Florence)\n");
        quote.setAutoscrolls(false);
        quote.setDoubleBuffered(true);
        panel_textarea.setViewportView(quote);

        javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
        panel.setLayout(panelLayout);
        panelLayout.setHorizontalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap(58, Short.MAX_VALUE)
                .addComponent(panel_textarea, javax.swing.GroupLayout.PREFERRED_SIZE, 880, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(57, Short.MAX_VALUE))
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panelLayout.setVerticalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(50, 50, 50)
                .addComponent(panel_textarea, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout tapeteLayout = new javax.swing.GroupLayout(tapete);
        tapete.setLayout(tapeteLayout);
        tapeteLayout.setHorizontalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        tapeteLayout.setVerticalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tapeteLayout.createSequentialGroup()
                .addContainerGap(80, Short.MAX_VALUE)
                .addComponent(panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(20, Short.MAX_VALUE))
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

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.GUIRun(new Runnable() {
            public void run() {

                Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), 30, 30);

            }
        });

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void jLabel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel1MouseClicked
        // TODO add your handling code here:
        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_jLabel1MouseClicked

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

    private void stats_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stats_buttonActionPerformed
        // TODO add your handling code here:
        StatsDialog dialog = new StatsDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_stats_buttonActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), 30, 30);
        if (quote_timer != null) {
            if (quote_timer.isRunning()) {
                quote_timer.restart();
            } else {
                quote_timer.start();
            }
        }

    }//GEN-LAST:event_formComponentShown

    private void krustyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_krustyMouseClicked
        // TODO add your handling code here:

        Audio.playWavResource("misc/krusty.wav");

        if (M2 != null && ++k == 5) {

            var tthis = this;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    try {
                        gif_dialog = new GifAnimationDialog(tthis, true, new ImageIcon((byte[]) M2.invoke(null, "f")), 5500);
                        gif_dialog.setLocationRelativeTo(gif_dialog.getParent());
                        gif_dialog.setVisible(true);
                    } catch (Exception ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });

            k = 0;
        }

    }//GEN-LAST:event_krustyMouseClicked

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

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        if (!INIT) {

            INIT = true;

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
                Logger.getLogger(Init.class.getName()).log(Level.INFO, "Loading CSPRNG...");
                Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,none");
                Helpers.CSPRNG_GENERATOR = SecureRandom.getInstance("DRBG");

            } catch (NoSuchAlgorithmException ex) {

                Helpers.CSPRNG_GENERATOR = new SecureRandom();
                Logger.getLogger(Init.class.getName()).log(Level.WARNING, "CSPRNG -> {0}", Helpers.CSPRNG_GENERATOR.getAlgorithm());
            }

            Helpers.GUI_FONT = Helpers.createAndRegisterFont(Helpers.class.getResourceAsStream("/fonts/McLaren-Regular.ttf"));

            Helpers.updateCoronaDialogsFont();

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "Loading MOD...");

            Init.MOD = Helpers.loadMOD();

            if (Init.MOD != null) {

                WINDOW_TITLE += " @ " + MOD.get("name") + " " + MOD.get("version");

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

            VENTANA_INICIO = new Init();

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    VENTANA_INICIO.setExtendedState(VENTANA_INICIO.getExtendedState() | JFrame.MAXIMIZED_BOTH);

                    VENTANA_INICIO.setVisible(true);
                }
            });

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "CHECKING UPDATE...");

            UPDATE();

            if (!Helpers.OSValidator.isMac()) {
                antiScreensaver();
            }

            Logger.getLogger(Init.class.getName()).log(Level.INFO, "LET'S GO");

        }
    }

    private static String downloadUpdater() throws IOException {

        HttpURLConnection con = null;

        String updater_path = null;

        try {

            URL url_api = new URL("https://github.com/tonikelope/coronapoker/raw/master/coronaupdater.jar");

            con = (HttpURLConnection) url_api.openConnection();

            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_WEB_BROWSER);

            con.setUseCaches(false);

            updater_path = System.getProperty("java.io.tmpdir") + "/coronaupdater.jar";

            try (BufferedInputStream bis = new BufferedInputStream(con.getInputStream()); BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(updater_path))) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = bis.read(buffer)) != -1) {

                    bfos.write(buffer, 0, reads);

                }
            }

        } finally {

            if (con != null) {
                con.disconnect();
            }
        }

        return updater_path;

    }

    private static void UPDATE() {

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {

                        VENTANA_INICIO.update_label.setVisible(true);

                        VENTANA_INICIO.update_button.setVisible(false);
                    }
                });

                NEW_VERSION = Helpers.checkNewVersion(AboutDialog.UPDATE_URL);

                if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {

                    if (VENTANA_INICIO.isVisible() && VENTANA_INICIO.isActive() && Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, "HAY UNA VERSIÓN NUEVA DE CORONAPOKER. ¿QUIERES ACTUALIZAR?") == 0) {

                        Helpers.GUIRun(new Runnable() {
                            @Override
                            public void run() {

                                VENTANA_INICIO.update_label.setText(Translator.translate("PREPARANDO ACTUALIZACIÓN..."));
                            }
                        });

                        try {

                            String current_jar_path = Helpers.getCurrentJarPath();

                            String new_jar_path = current_jar_path.replaceAll(AboutDialog.VERSION + ".jar", NEW_VERSION + ".jar");

                            String updater_jar = downloadUpdater();

                            if (updater_jar != null) {

                                String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, NEW_VERSION, current_jar_path, new_jar_path, Translator.translate("ACTUALIZANDO >>> ") + NEW_VERSION};

                                Runtime.getRuntime().exec(cmdArr);

                                System.exit(0);
                            } else {
                                Helpers.mostrarMensajeError(VENTANA_INICIO, "NO SE HA PODIDO ACTUALIZAR (ERROR AL DESCARGAR EL ACTUALIZADOR)");
                            }

                        } catch (Exception ex) {
                            Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                            Helpers.mostrarMensajeError(VENTANA_INICIO, "NO SE HA PODIDO ACTUALIZAR (ERROR INESPERADO)");
                        }
                    }

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {

                            VENTANA_INICIO.update_button.setVisible(true);
                        }
                    });

                } else if (NEW_VERSION == null) {
                    Helpers.mostrarMensajeError(VENTANA_INICIO, "NO SE HA PODIDO COMPROBAR SI HAY NUEVA VERSIÓN. ¿TIENES CONEXIÓN A INTERNET?");
                }

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {

                        VENTANA_INICIO.update_label.setVisible(false);
                    }
                });

            }
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
    private javax.swing.JPanel corona_init_panel;
    private javax.swing.JButton create_button;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JButton join_button;
    private javax.swing.JLabel krusty;
    private javax.swing.JComboBox<String> language_combobox;
    private javax.swing.JPanel panel;
    private javax.swing.JScrollPane panel_textarea;
    private javax.swing.JPanel pegi_panel;
    private javax.swing.JTextPane quote;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JButton stats_button;
    private com.tonikelope.coronapoker.InitPanel tapete;
    private javax.swing.JButton update_button;
    private javax.swing.JLabel update_label;
    // End of variables declaration//GEN-END:variables
}
