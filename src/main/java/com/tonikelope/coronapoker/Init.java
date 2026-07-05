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
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import static java.beans.Beans.isDesignTime;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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

/**
 *
 * @author tonikelope
 */
public class Init extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(Init.class.getName());

    public static final boolean DEV_MODE = false;
    public static final String CORONA_DIR = System.getProperty("user.home") + "/.coronapoker";
    public static final String LOGS_DIR = CORONA_DIR + "/Logs";
    public static final String DEBUG_DIR = CORONA_DIR + "/Debug";
    public static final String GIFSICLE_DIR = CORONA_DIR + "/gifsicle";
    public static final String CACHE_DIR = CORONA_DIR + "/Cache";
    public static final String CHAT_IMAGE_CACHE = CORONA_DIR + "/ChatImagesCache";
    public static final String SCREENSHOTS_DIR = CORONA_DIR + "/Screenshots";
    public static final String VOICE_DIR = CORONA_DIR + "/voice";
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
    public static volatile Init VENTANA_INICIO = null;
    // Snapshot (tamaño + estado) de la ventana de inicio en el momento de lanzar
    // la timba, para reabrirla igual al cancelar desde la sala de espera.
    public static volatile java.awt.Dimension LAUNCH_FRAME_SIZE = null;
    public static volatile boolean LAUNCH_FRAME_MAXIMIZED = false;
    public static volatile Method M1 = null;
    public static volatile Method M2 = null;
    public static volatile Image I1 = null;
    public static volatile URL CORONA_INIT_MOD_IMAGE = null;
    public static volatile boolean PEGI18_MOD = false;
    public static volatile boolean PLAYING_CINEMATIC = false;
    public static volatile VolumeControlDialog VOLUME_DIALOG = null;
    private static volatile boolean FORCE_CLOSE_DIALOG = false;
    private static volatile String NEW_VERSION = null;
    // Reintentos SILENCIOSOS del check de versión (arranque y botón
    // ACTUALIZAR): con el timeout acotado de Helpers.HTTP_TIMEOUT, GitHub lento
    // o caído no bloquea nada ni saca diálogos.
    private static final int UPDATE_CHECK_RETRIES = 3;
    private volatile Timer quote_timer = null;
    private volatile int conta_quote = 0;
    private volatile JTextPane quote = null;

    static {
        if (!isDesignTime()) {
            LOGGER.log(Level.INFO, "OS: {0}", System.getProperty("os.name"));

            // Force JVM HiDPI scaling to 1.0 so the OS display scale (Windows zoom,
            // macOS Retina factor, etc.) never re-scales the UI. The in-game Ctrl+/Ctrl-
            // zoom is the only mechanism that changes rendering size.
            System.setProperty("sun.java2d.uiScale", "1");

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

                    LOGGER.log(Level.SEVERE, null, ex);
                }

            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Huevos is not present!");
            }
        }
    }

    public JLabel getBaraja_fondo() {
        return baraja_fondo;
    }

    public JLabel getUpdate_label() {
        return update_label;
    }

    public static void setupConsoleLogger() {
        try {
            // Garantizar que DEBUG_DIR existe ANTES de abrir el FileOutputStream.
            // Antes, el orden se sostenía por accidente vía static init de Helpers
            // (loadPropertiesFile → createIfNoExistsCoronaDirs). Si esa cadena se
            // alterase (cualquier import o método antes que no toque Helpers),
            // DEBUG_DIR no existiría y el debug-log se perdería silenciosamente.
            Helpers.createIfNoExistsCoronaDirs();

            // Define the path for the debug log file (append mode = true)
            java.io.File logFile = new java.io.File(DEBUG_DIR + "/coronapoker_debug_" + Helpers.genRandomString(10) + ".log");
            java.io.FileOutputStream fileOut = new java.io.FileOutputStream(logFile, true);

            // Force UTF-8 encoding for the file output
            java.io.PrintStream filePrintStream = new java.io.PrintStream(fileOut, true, "UTF-8");

            // Force UTF-8 encoding for standard output pipeline
            TeeOutputStream teeOut = new TeeOutputStream(System.out, filePrintStream);
            java.io.PrintStream outPrintStream = new java.io.PrintStream(teeOut, true, "UTF-8");

            // Force UTF-8 encoding for standard error pipeline
            TeeOutputStream teeErr = new TeeOutputStream(System.err, filePrintStream);
            java.io.PrintStream errPrintStream = new java.io.PrintStream(teeErr, true, "UTF-8");

            // Inject custom pipes into the JVM
            System.setOut(outPrintStream);
            System.setErr(errPrintStream);

            // Intercept native Java loggers
            java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");

            // Find and destroy the default console handler
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                if (handler instanceof java.util.logging.ConsoleHandler) {
                    rootLogger.removeHandler(handler);
                }
            }

            // Inject a new handler with explicit UTF-8 encoding
            java.util.logging.ConsoleHandler newConsoleHandler = new java.util.logging.ConsoleHandler();
            try {
                newConsoleHandler.setEncoding("UTF-8");
            } catch (Exception encodingEx) {
                // Ignore failure; fallback to default encoding
            }
            rootLogger.addHandler(newConsoleHandler);

            // In-memory handler so the GameLogDialog can show the live debug log
            DebugLog.install();

            // Print a header to mark a new session in the log file
            LOGGER.log(Level.INFO, "{0}=== NEW CORONAPOKER SESSION STARTED: {1} ==={2}", new Object[]{"\n============================================================================\n", java.time.LocalDateTime.now(), "\n============================================================================\n"});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not initialize file logger!", e);
        }
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
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            });
        });
    }

    private void initTranslations() {

        // Asignamos las "etiquetas" (keys) de traducción a cada componente.
        // Como esto está fuera de initComponents, NetBeans NUNCA lo borrará.
        update_label.putClientProperty("i18n.key", "ui.comprobando_actualizacion");
        update_button.putClientProperty("i18n.key", "update.actualizar");
        join_button.putClientProperty("i18n.key", "ui.unirme_a_timba");
        stats_button.putClientProperty("i18n.key", "ui.estadisticas");
        create_button.putClientProperty("i18n.key", "ui.crear_timba");
        exit_button.putClientProperty("i18n.key", "ui.salir");

        // También los Tooltips
        sound_icon.putClientProperty("i18n.tooltip_key", "ui.click_para_activar_desactivar_sonido");
        settings_icon.putClientProperty("i18n.tooltip_key", "settings.ajustes");
        Helpers.setScaledBlackIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), 30, 30);

    }

    /**
     * Creates new form Inicio
     */
    public Init() {

        initComponents();

        initTranslations();

        translateGlobalLabels();

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

        actionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_DOWN_MASK), new AbstractAction("SOUND-SWITCH") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance() != null) {
                    GameFrame.setSonidos(!GameFrame.SONIDOS);
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

                    // Immediate effect; the debounced timer only adds the beep
                    Audio.refreshALLVolumes(false);

                    if (Audio.VOLUME_TIMER.isRunning()) {
                        Audio.VOLUME_TIMER.restart();
                    } else {
                        Audio.VOLUME_TIMER.start();
                    }

                    AudioSettingsPanel.refreshVolume();

                    if (!GameFrame.SONIDOS) {
                        if (GameFrame.getInstance() != null) {
                            GameFrame.setSonidos(!GameFrame.SONIDOS);
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
                        GameFrame.setSonidos(!GameFrame.SONIDOS);
                    } else if (VENTANA_INICIO.isVisible()) {
                        sound_iconMouseClicked(null);
                    } else {
                        WaitingRoomFrame.getInstance().soundIconClick();
                    }
                }

                if (Audio.MASTER_VOLUME < 1.0f) {
                    Audio.MASTER_VOLUME = Helpers.floatClean(Audio.MASTER_VOLUME + 0.01f, 2);

                    // Immediate effect; the debounced timer only adds the beep
                    Audio.refreshALLVolumes(false);

                    if (Audio.VOLUME_TIMER.isRunning()) {
                        Audio.VOLUME_TIMER.restart();
                    } else {
                        Audio.VOLUME_TIMER.start();
                    }

                    AudioSettingsPanel.refreshVolume();
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

                    if (Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {

                        System.exit(1);
                    }

                    FORCE_CLOSE_DIALOG = false;
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        kfm.addKeyEventDispatcher((KeyEvent e) -> {

            // Configurable push-to-record key (voice messages, in game only)
            if (VoiceMessageManager.handleKeyEvent(e)) {
                return true;
            }

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

        applyModernButtons();

        revalidate();

        repaint();

    }

    public InitPanel getTapete() {
        return tapete;
    }

    public void translateGlobalLabels() {
        LocalPlayer.ACTIONS_LABELS = LocalPlayer.getActionsLabels();
        RemotePlayer.ACTIONS_LABELS = RemotePlayer.getActionsLabels();
        Hand.NOMBRES_JUGADAS = Hand.getNombreJugadas();

    }

    /**
     * Guarda pantalla, tamaño, posición y estado (maximizado o normal) de la
     * ventana de inicio justo antes de ocultarla para lanzar una timba, de modo
     * que al cancelar desde la sala o volver al menú se reabra exactamente igual.
     */
    public static void captureLaunchFrameState() {
        if (VENTANA_INICIO != null) {
            LAUNCH_FRAME_MAXIMIZED = (VENTANA_INICIO.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            LAUNCH_FRAME_SIZE = VENTANA_INICIO.getSize();
        }
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
            GameFrame.RUN_IT_TWICE_RECOVER = null;
            GameFrame.PASSWORD_RECOVER = null;
        } else {
            captureLaunchFrameState();
            setVisible(false);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
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
        settings_icon = new javax.swing.JLabel();
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

        botones_panel.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.orange, 5));
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
                        .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, 453, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(join_button, javax.swing.GroupLayout.PREFERRED_SIZE, 463, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(stats_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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

        settings_icon.setToolTipText("Ajustes");
        settings_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        settings_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        settings_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                settings_iconMouseClicked(evt);
            }
        });

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
                .addComponent(exit_button, javax.swing.GroupLayout.DEFAULT_SIZE, 605, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(language_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(settings_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(language_combobox, javax.swing.GroupLayout.DEFAULT_SIZE, 58, Short.MAX_VALUE)
                    .addComponent(exit_button)
                    .addComponent(settings_icon, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(sound_icon, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout corona_init_panelLayout = new javax.swing.GroupLayout(corona_init_panel);
        corona_init_panel.setLayout(corona_init_panelLayout);
        corona_init_panelLayout.setHorizontalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(update_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(update_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout botones_panelLayout = new javax.swing.GroupLayout(botones_panel);
        botones_panel.setLayout(botones_panelLayout);
        botones_panelLayout.setHorizontalGroup(
            botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(botones_panelLayout.createSequentialGroup()
                .addContainerGap(8, Short.MAX_VALUE)
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(8, Short.MAX_VALUE))
        );
        botones_panelLayout.setVerticalGroup(
            botones_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(botones_panelLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10))
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
                .addComponent(baraja_fondo, javax.swing.GroupLayout.DEFAULT_SIZE, 2986, Short.MAX_VALUE)
                .addContainerGap())
        );
        baraja_panelLayout.setVerticalGroup(
            baraja_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_fondo, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 2064, Short.MAX_VALUE)
        );

        tapete.setLayer(botones_panel, javax.swing.JLayeredPane.POPUP_LAYER);
        tapete.setLayer(baraja_panel, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout tapeteLayout = new javax.swing.GroupLayout(tapete);
        tapete.setLayout(tapeteLayout);
        tapeteLayout.setHorizontalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tapeteLayout.createSequentialGroup()
                    .addContainerGap(1012, Short.MAX_VALUE)
                    .addComponent(botones_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap(1012, Short.MAX_VALUE)))
        );
        tapeteLayout.setVerticalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(baraja_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tapeteLayout.createSequentialGroup()
                    .addContainerGap(830, Short.MAX_VALUE)
                    .addComponent(botones_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap(831, Short.MAX_VALUE)))
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

    // Refresca el icono de altavoz de la ventana de inicio según SONIDOS. Lo
    // usa GameFrame.setSonidos para que el cambio hecho desde el diálogo de
    // ajustes de audio se vea aquí cuando aún no hay partida.
    public static void refreshSoundIcon() {

        Init ventana = VENTANA_INICIO;

        if (ventana != null) {
            Helpers.GUIRun(() -> {
                Helpers.setScaledIconLabel(ventana.sound_icon, Init.class.getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);
            });
        }
    }

    private void settings_iconMouseClicked(java.awt.event.MouseEvent evt) {
        // Abre el diálogo de ajustes en modo general (Apariencia + Sonido): no hay
        // GameFrame en el lanzador, así que la pestaña Partida no se monta.
        SettingsDialog.open(this);
    }

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

            Helpers.translateComponents(this, false);

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
        // Si el botón ya anuncia versión nueva, pulsarlo ES la confirmación: se
        // lanza la actualización directa, sin re-chequear ni volver a sacar el
        // popup "¿quieres actualizar?". En el estado "no se pudo comprobar"
        // (NEW_VERSION == null) el botón reintenta el check.
        if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
            final String target = NEW_VERSION;
            update_button.setVisible(false);
            update_label.setText(Translator.translate("update.preparando_actualizacion"));
            update_label.setVisible(true);
            Helpers.threadRun(() -> {
                try {
                    performUpdate(target);
                } finally {
                    // performUpdate solo retorna si la actualización falló (en
                    // éxito hace System.exit); restaurar el botón para reintentar.
                    Helpers.GUIRun(() -> {
                        update_label.setVisible(false);
                        update_button.setVisible(true);
                    });
                }
            });
        } else {
            UPDATE();
        }
    }//GEN-LAST:event_update_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
        Helpers.savePropertiesFile();

    }//GEN-LAST:event_formWindowClosing

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentHidden
        // TODO add your handling code here:
        if (quote_timer != null && quote_timer.isRunning()) {
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

        // Nueva timba: arranca SIEMPRE en la configuracion por defecto. Igual que ya se hace
        // con la dificultad de los bots, se resetean aqui los ajustes que de otro modo se
        // arrastrarian de la timba anterior via estaticos de sesion (rabbit y tiempo de pensar).
        // El resto de controles ya arrancan en su default en el constructor del dialogo. Para
        // reutilizar una config hay que guardarla como preset favorito. (En recover, loadLastGame
        // repuebla estos controles con los valores de la timba recuperada.)
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
        GameFrame.RABBIT_HUNTING = 0;
        GameFrame.THINK_TIME = GameFrame.DEFAULT_THINK_TIME;
        GameFrame.THINK_TIME_ENABLED = true;

        NewGameDialog dialog = new NewGameDialog(this, true, true);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            captureLaunchFrameState();
            setVisible(false);
        }
    }//GEN-LAST:event_create_buttonActionPerformed

    private void create_buttonMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_create_buttonMouseExited
        // Hover gestionado por GlassButtonUI (rollover); ya no invertimos colores a mano.
    }//GEN-LAST:event_create_buttonMouseExited

    private void create_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_create_buttonMouseEntered
        // Hover gestionado por GlassButtonUI (rollover); ya no invertimos colores a mano.
    }//GEN-LAST:event_create_buttonMouseEntered

    private void stats_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stats_buttonActionPerformed
        // TODO add your handling code here:
        StatsDialog.showStats(this);
    }//GEN-LAST:event_stats_buttonActionPerformed

    private void join_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_join_buttonActionPerformed
        // TODO add your handling code here:

        NewGameDialog dialog = new NewGameDialog(this, true, false);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            captureLaunchFrameState();
            setVisible(false);
        }
    }//GEN-LAST:event_join_buttonActionPerformed

    private void join_buttonMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_join_buttonMouseExited
        // Hover gestionado por GlassButtonUI (rollover); ya no invertimos colores a mano.
    }//GEN-LAST:event_join_buttonMouseExited

    private void join_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_join_buttonMouseEntered
        // Hover gestionado por GlassButtonUI (rollover); ya no invertimos colores a mano.
    }//GEN-LAST:event_join_buttonMouseEntered

    // Aplica el estilo "cristal" (glassmorphism) a la botonera de inicio sobre los JButton
    // existentes (setUI, no invasivo): fondo negro translúcido redondeado que deja ver el tapete,
    // acento dorado para las acciones primarias, hover suave pintado por GlassButtonUI (rollover).
    // Quita el marco naranja del contenedor. NO toca disposición, iconos, acciones ni i18n.
    private void applyModernButtons() {
        final Color red = new Color(214, 78, 70);
        final Color green = new Color(70, 180, 110);

        // Todos con borde BLANCO neutro (sin dorado); CREAR/UNIRME un pelín más opacas que
        // ESTADÍSTICAS para conservar una jerarquía sutil por opacidad, no por color.
        create_button.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        join_button.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        stats_button.setUI(new GlassButtonUI(null, false, false, 0.60f, 22));
        // Salir: cristal neutro; el rojo solo aparece al pasar el ratón.
        exit_button.setUI(new GlassButtonUI(red, false, true, 0.66f, 22));
        // Actualizar (solo visible cuando hay versión nueva): verde para destacar.
        update_button.setUI(new GlassButtonUI(green, true, false, 0.72f, 22));

        // Fuera el marco naranja de 5px del contenedor de la botonera.
        botones_panel.setBorder(null);
    }

    /**
     * Submits the deadlock detection loop to the current thread pool. The loop
     * exits cleanly when interrupted (pausar() re-raises the flag after
     * catching the sleep interrupt, so the while-check breaks on the next
     * pass), avoiding an infinite SEVERE spam when the pool is shut down.
     * Call this once at app startup and again after every CREATE_THREAD_POOL
     * to keep the detector alive across game sessions.
     */
    public static void startDeadlockDetector() {
        Helpers.threadRun(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Helpers.detectAndHandleDeadlocks();
                Helpers.pausar(DEADLOCK_DETECT_WAIT);
            }
        });
    }

    public static void main(String args[]) {

        //ensureRequiredJvmParameters(args, Init.class);
        setupConsoleLogger();

        // Startup housekeeping: cap the unbounded growth of persisted voice notes.
        // Off the boot path on a background thread: it has zero dependency on the
        // rest of startup (notes only matter when a chat line is clicked later),
        // and it pulls in AudioDeviceManager whose init enumerates audio mixers
        // (tens-to-hundreds of ms on Windows). Not in loadPropertiesFile because
        // the configurable retention lives in AudioDeviceManager, whose static
        // init reads Helpers.PROPERTIES (still null during that early phase).
        Helpers.threadRun(Helpers::purgeOldVoiceNotes);

        startDeadlockDetector();

        if (GameFrame.TEST_MODE) {
            GameFrame.CINEMATICAS_PREF = false;
        }

        if (!Init.DEV_MODE) {
            SQL_FILE = CORONA_DIR + "/coronapoker.db";
        } else {
            // DEV_MODE: trabajamos sobre una copia temporal desechable para no
            // mutar la BD real. Blindaje: SQL_FILE NUNCA debe quedar en null (si
            // no, se abriria "jdbc:sqlite:null" y se crearia un fichero "null").
            // Si la BD real no existe, usamos un temporal vacio; si la copia falla,
            // caemos a la ruta real.
            try {
                File db = File.createTempFile("coronapoker_" + Helpers.genRandomString(WIDTH), ".db");
                if (Files.exists(Paths.get(CORONA_DIR + "/coronapoker.db"))) {
                    Files.copy(Paths.get(CORONA_DIR + "/coronapoker.db"), db.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                SQL_FILE = db.getAbsolutePath();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "DEV_MODE temp DB copy failed; falling back to the real DB", ex);
                SQL_FILE = CORONA_DIR + "/coronapoker.db";
            }
        }

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

        EmojiPanel.initClass();
        Helpers.setCoronaLocale();

        LOGGER.log(Level.INFO, "Loading SQLITE DB...");
        Helpers.initSQLITE();

        try {
            LOGGER.log(Level.INFO, "Trying to load CSPRNG HASH DRBG SHA-512...");
            Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,reseed_only");
            Helpers.CSPRNG_GENERATOR = SecureRandom.getInstance("DRBG");
            LOGGER.log(Level.INFO, "CSPRNG OK");
        } catch (NoSuchAlgorithmException ex) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
            LOGGER.log(Level.WARNING, "Fallback CSPRNG -> {0}", Helpers.CSPRNG_GENERATOR.getAlgorithm());
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
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

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

            if (Init.MOD.containsKey("font") && Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")))) {
                try {
                    Helpers.GUI_FONT = Helpers.createAndRegisterFont(new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")));
                } catch (FileNotFoundException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        if (!Card.BARAJAS.containsKey(GameFrame.BARAJA)) {
            GameFrame.BARAJA = GameFrame.BARAJA_DEFAULT;
        }

        // Pre-decodifica el shuffle.gif de la baraja actual en background desde
        // el arranque, para que la primera mano no pague el decode
        Crupier.warmShuffleAnimCache();

        // Calienta el JIT de la cripto pesada (cascada SRA + prueba/verificación de barajado)
        // en background, para que las primeras manos no corran interpretadas / en C1 en PCs
        // lentos (multi-segundo en frío vs ~0,1 s ya compilado). Ver CryptoWarmup.
        com.tonikelope.coronapoker.crypto.CryptoWarmup.warmup();

        Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);

        // A corrupt master_volume used to cascade: >1.0 overflows the gain control
        // (misdiagnosed as a missing audio device) and NaN poisons floatClean.
        float master_volume;

        try {
            master_volume = Float.parseFloat(Helpers.PROPERTIES.getProperty("master_volume", "0.8"));
        } catch (NumberFormatException ex) {
            master_volume = Float.NaN;
        }

        if (Float.isNaN(master_volume) || master_volume < 0f || master_volume > 1f) {
            LOGGER.log(Level.WARNING, "Invalid master_volume property, falling back to default.");
            master_volume = 0.8f;
        }

        Audio.MASTER_VOLUME = master_volume;

        if (!GameFrame.SONIDOS) {
            Audio.muteAll();
        } else {
            Audio.unmuteAll();
        }

        // El init.wav es el PRIMER sonido del proceso y salía cortado a veces:
        // se reproducía mientras el SO aún despertaba el endpoint de audio.
        // Caldeamos el dispositivo con una línea de silencio y SOLO después
        // soltamos el init.wav, en un hilo aparte para no retrasar la ventana.
        Helpers.threadRun(() -> {
            Audio.warmAudioDevice();
            Audio.playWavResourceAndWait("misc/init.wav");
        });

        Audio.playLoopMp3Resource("misc/background_music.mp3");

        LOGGER.log(Level.INFO, "Loading GUI Window...");

        Helpers.GUIRun(() -> {
            VENTANA_INICIO = new Init();
            // Maximizada en el monitor primario, pero con el tamaño restaurado
            // (al desmaximizar) fijado al 80% centrado en vez del enorme por
            // defecto que se salia de la pantalla.
            Helpers.showFrameOnScreen(VENTANA_INICIO, java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
        });

        if (PEGI18_MOD && !Files.isReadable(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/.pegi18_warning"))) {
            if (Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, Translator.translate("mod.el_mod_cargado_contiene_material"), new ImageIcon(Init.class.getResource("/images/pegi18.png"))) == 0) {
                try {
                    Files.createFile(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/.pegi18_warning"));
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            } else {
                System.exit(0);
            }
        }

        LOGGER.log(Level.INFO, "Checking for updates...");

        UPDATE();

        if (!Helpers.OSValidator.isMac()) {
            antiScreensaver();
        }

        LOGGER.log(Level.INFO, "Initialization complete. Ready.");
    }

    // Descarga el updater y arranca la actualización a la versión dada. En éxito
    // hace System.exit(0) (el updater toma el relevo) y NO retorna; si la
    // descarga falla o salta una excepción, avisa al usuario y retorna para que
    // el caller restaure la UI. Debe invocarse desde un hilo de fondo:
    // downloadUpdater() bloquea en red.
    private static void performUpdate(String version) {
        Helpers.GUIRun(() -> {
            VENTANA_INICIO.update_label.setText(Translator.translate("update.preparando_actualizacion"));
        });
        try {
            String current_jar_path = Helpers.getCurrentJarPath();
            // replace (literal) en vez de replaceAll (regex) — el '.' en
            // "20.66.jar" es metacaracter regex y matchearía cualquier char
            // (e.g. paths como "20X66Yjar" o "20<algo>66<algo>jar"). replace
            // hace substring literal, que es lo correcto aquí.
            String new_jar_path = current_jar_path.replace(AboutDialog.VERSION + ".jar", version + ".jar");
            String updater_jar = Helpers.downloadUpdater();

            if (updater_jar != null) {
                Helpers.cleanCacheDIR();
                if (GameFrame.LANGUAGE.equals("es")) {
                    String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, version, current_jar_path, new_jar_path, "¡Santiago y cierra, España!"};
                    Runtime.getRuntime().exec(cmdArr);
                } else {
                    String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, version, current_jar_path, new_jar_path};
                    Runtime.getRuntime().exec(cmdArr);
                }
                System.exit(0);
            } else {
                Helpers.mostrarMensajeError(VENTANA_INICIO, Translator.translate("update.no_se_ha_podido_actualizar_2"));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            Helpers.mostrarMensajeError(VENTANA_INICIO, Translator.translate("update.no_se_ha_podido_actualizar"));
        }
    }

    private static void UPDATE() {
        Helpers.threadRun(() -> {
            // Solo el label de "comprobando actualización": los botones de
            // acción quedan libres durante el check (con GitHub lento los
            // reintentos pueden tardar varios segundos y no deben retener al
            // usuario; el setEnabled(false) del panel que había aquí era
            // además un no-op: JPanel no propaga el disable a sus hijos). Si
            // el usuario ya se metió en una partida, la oferta de update
            // simplemente no se muestra esa sesión (guard de ventana visible
            // y activa).
            Helpers.GUIRun(() -> {
                VENTANA_INICIO.update_label.setVisible(true);
                VENTANA_INICIO.update_button.setVisible(false);
            });
            // Reset para que el botón ACTUALIZAR manual re-compruebe siempre
            // (un check previo "estás al día" deja NEW_VERSION en blanco).
            NEW_VERSION = null;

            // try/finally: el check es best-effort y corre en background, pero
            // pase lo que pase (excepción de red no prevista, Error, fallo de un
            // diálogo) el finally DEBE restaurar la UI — si no, la etiqueta
            // "COMPROBANDO ACTUALIZACIÓN..." se queda colgada para siempre.
            try {
                // Hasta UPDATE_CHECK_RETRIES intentos en silencio: si GitHub no
                // responde, se deja visible el botón ACTUALIZAR para el check
                // manual y punto (el diálogo modal de "¿reintentar?" que había
                // aquí podía asaltar al usuario ya metido en partida, sin el
                // guard de ventana visible/activa que sí tiene la oferta).
                for (int intento = 0; intento < UPDATE_CHECK_RETRIES && NEW_VERSION == null; intento++) {
                    NEW_VERSION = Helpers.checkLatestCoronaPokerVersion(AboutDialog.UPDATE_URL);
                }

                if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
                    if (VENTANA_INICIO.isVisible() && VENTANA_INICIO.isActive() && Helpers.mostrarMensajeInformativoSINO(VENTANA_INICIO, Translator.translate("update.hay_una_version_nueva_de"), new ImageIcon(Init.class.getResource("/images/avatar_default.png"))) == 0) {
                        performUpdate(NEW_VERSION);
                    }
                }

                if (Init.MOD != null) {
                    LOGGER.log(Level.INFO, "Checking MOD updates...");
                    Helpers.checkMODVersion(VENTANA_INICIO);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Update check failed unexpectedly", t);
            } finally {
                Helpers.GUIRun(() -> {
                    VENTANA_INICIO.update_label.setVisible(false);
                    // El botón cubre dos estados muy distintos y hay que poder
                    // distinguirlos de un vistazo: o se encontró versión nueva
                    // (pero el popup no llegó a completarse — p.ej. la ventana no
                    // estaba activa), o el check no pudo comprobar nada (red /
                    // timeout). Se etiqueta y colorea según el caso para que el
                    // usuario sepa qué le ofrece al pulsarlo: actualizar, o
                    // reintentar la comprobación. NEW_VERSION == "" (al día) no
                    // entra: el botón queda oculto. Se actualiza también la
                    // i18n.key para que un cambio de idioma reaplique el texto
                    // correcto (Helpers.translateComponents).
                    if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
                        VENTANA_INICIO.update_button.putClientProperty("i18n.key", "update.boton_hay_version_nueva");
                        VENTANA_INICIO.update_button.setText(Translator.translate("update.boton_hay_version_nueva"));
                        VENTANA_INICIO.update_button.setForeground(new Color(0, 153, 0));
                        VENTANA_INICIO.update_button.setVisible(true);
                    } else if (NEW_VERSION == null) {
                        VENTANA_INICIO.update_button.putClientProperty("i18n.key", "update.boton_reintentar");
                        VENTANA_INICIO.update_button.setText(Translator.translate("update.boton_reintentar"));
                        VENTANA_INICIO.update_button.setForeground(new Color(204, 102, 0));
                        VENTANA_INICIO.update_button.setVisible(true);
                    }
                });
            }
        });
    }

    private static void antiScreensaver() {

        // Robot solo para el FALLBACK de tecla (plataformas sin vía nativa de
        // wake-lock). En Windows/Linux la vía nativa no lo necesita; si no se
        // puede crear (headless), seguimos sin fallback.
        Robot rob;
        try {
            rob = new Robot();
        } catch (AWTException ex) {
            LOGGER.log(Level.WARNING, "Robot unavailable — anti-screensaver key fallback disabled", ex);
            rob = null;
        }
        final Robot fallback_robot = rob;

        // Timer daemon: un único hilo para toda la vida de la app, daemon para
        // que jamás impida el cierre de la JVM. SetThreadExecutionState es
        // por-hilo, así que el wake-lock se refresca SIEMPRE desde este hilo.
        java.util.Timer screensaver = new java.util.Timer("anti-screensaver", true);

        screensaver.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean fullscreen = GameFrame.getInstance() != null && GameFrame.getInstance().isFull_screen();
                ScreenWakeLock.refresh(fullscreen, fallback_robot);
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
    private javax.swing.JLabel settings_icon;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JButton stats_button;
    private com.tonikelope.coronapoker.InitPanel tapete;
    private javax.swing.JButton update_button;
    private javax.swing.JLabel update_label;
    // End of variables declaration//GEN-END:variables
}
