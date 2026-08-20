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
import java.awt.image.BufferedImage;
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
 * Application entry point and launcher window (main menu, splash/boot sequence,
 * update check).
 *
 * @author tonikelope
 */
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class Init extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(Init.class.getName());

    public static final boolean DEV_MODE = false;
    public static final String CORONA_DIR = System.getProperty("user.home") + "/.coronapoker";
    public static final String LOGS_DIR = CORONA_DIR + "/Logs";
    public static final String DEBUG_DIR = CORONA_DIR + "/Debug";
    public static final String CHAT_IMAGE_CACHE = CORONA_DIR + "/ChatImagesCache";
    public static final String SCREENSHOTS_DIR = CORONA_DIR + "/Screenshots";
    public static final String VOICE_DIR = CORONA_DIR + "/voice";
    public static final int DEADLOCK_DETECT_WAIT = 5000;
    // Splash step band: sits at the foot of the card (gif is 427x618), over its
    // clean white area, so it never covers the logo.
    private static final int SPLASH_STEP_BAND_HEIGHT = 26;
    private static final int SPLASH_STEP_BOTTOM_MARGIN = 40;
    private static final int SPLASH_STEP_PILL_PADDING = 14;
    private static final int SPLASH_STEP_FONT_SIZE = 15;
    // Pill color sampled from the logo's orange outline, with white text: a light
    // pill would blend into the card's white background.
    private static final Color SPLASH_STEP_TEXT_COLOR = Color.WHITE;
    private static final Color SPLASH_STEP_PILL_COLOR = new Color(255, 88, 0, 235);
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
    // Snapshot (size + state) of the launcher window taken right before launching
    // a game, so it reopens identically if cancelled from the waiting room.
    public static volatile java.awt.Dimension LAUNCH_FRAME_SIZE = null;
    public static volatile boolean LAUNCH_FRAME_MAXIMIZED = false;
    public static volatile Method M1 = null;
    public static volatile Method M2 = null;
    public static volatile Image I1 = null;
    public static volatile URL CORONA_INIT_MOD_IMAGE = null;
    public static volatile boolean PEGI18_MOD = false;
    public static volatile boolean PLAYING_CINEMATIC = false;
    public static volatile VolumeControlDialog VOLUME_DIALOG = null;
    // The volume confirmation beep fires on key RELEASE, not on a debounce
    // (which fired in the gap before OS key-repeat kicked in and doubled the
    // beep on a held key). This flag marks a pending real volume change;
    // consumed by the VK_UP/VK_DOWN release handler in the dispatcher.
    private static volatile boolean VOLUME_BEEP_PENDING = false;
    private static volatile boolean FORCE_CLOSE_DIALOG = false;
    private static volatile String NEW_VERSION = null;
    // Silent retries for the version check (startup and the UPDATE button):
    // with Helpers.HTTP_TIMEOUT bounding each attempt, a slow or down GitHub
    // never blocks or pops a dialog.
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

    /**
     * Redirects stdout/stderr and java.util.logging to a UTF-8 debug log file
     * (console output is preserved too), and wires up the in-memory
     * {@link DebugLog} used by the in-game log viewer.
     */
    public static void setupConsoleLogger() {
        try {
            // Ensure DEBUG_DIR exists BEFORE opening the FileOutputStream. This used
            // to hold by accident via Helpers' static init (loadPropertiesFile ->
            // createIfNoExistsCoronaDirs); if that chain ever changed (any import or
            // method running first that doesn't touch Helpers), DEBUG_DIR wouldn't
            // exist and the debug log would silently vanish.
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

            // Print a header to mark a new session in the log file. The bars are sized to the
            // middle line (whose length varies with the timestamp) so the banner is always a clean
            // rectangle with the text flanked symmetrically.
            String session_line = "=== NEW CORONAPOKER SESSION STARTED: " + java.time.LocalDateTime.now() + " ===";
            String session_bar = "=".repeat(session_line.length());
            LOGGER.log(Level.INFO, "\n" + session_bar + "\n" + session_line + "\n" + session_bar);

            // The unreadable-preferences-file rescue happens in a static initializer,
            // long before this logger exists, so its warning used to be lost entirely.
            // Repeated here now that there's somewhere to log it.
            if (Helpers.PROPERTIES_RESCUE_COPY != null) {
                LOGGER.log(Level.SEVERE,
                        "The preferences file could not be read at startup — a copy of it was kept at {0} and the game started with what could be read",
                        Helpers.PROPERTIES_RESCUE_COPY);
            }

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

        // Assign translation keys to each component. Kept outside initComponents
        // so NetBeans never wipes it on regeneration.
        update_label.putClientProperty("i18n.key", "ui.comprobando_actualizacion");
        update_button.putClientProperty("i18n.key", "update.actualizar");
        join_button.putClientProperty("i18n.key", "ui.unirme_a_timba");
        stats_button.putClientProperty("i18n.key", "ui.estadisticas");
        create_button.putClientProperty("i18n.key", "ui.crear_timba");
        exit_button.putClientProperty("i18n.key", "ui.salir");

        // Tooltips too
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

        // Purely decorative text: behave like a label (no text cursor, no
        // focus/caret/selection) instead of an editable text field.
        quote.setCursor(java.awt.Cursor.getDefaultCursor());
        quote.setFocusable(false);
        quote.setHighlighter(null);

        quote.setOpaque(false);

        quote.setBackground(new Color(0, 0, 0, 0));

        quote.setForeground(Color.white);

        Font font = new Font("Dialog", Font.ITALIC, 20);

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

                    // The button bar (font + size) scales with the window, just like the background.
                    applyInitScale(computeInitScale());

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

        // Global action bodies (launcher + waiting room + game), indexed by the shortcut
        // registry's STABLE id. The dispatcher resolves the id for the key combo pressed and
        // runs the body, so rebinding a key takes effect LIVE.
        HashMap<String, Action> initActions = new HashMap<>();

        initActions.put(KeyboardShortcuts.MUTE, new AbstractAction("SOUND-SWITCH") {
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

        initActions.put(KeyboardShortcuts.VOLUME_DOWN, new AbstractAction("VOLUME-DOWN") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (Audio.MASTER_VOLUME > 0f) {
                    Audio.MASTER_VOLUME = Helpers.floatClean(Audio.MASTER_VOLUME - 0.01f, 2);

                    // Immediate effect while the key is held; the confirmation
                    // beep is deferred to the release (VOLUME_BEEP_PENDING).
                    Audio.refreshALLVolumes(false);

                    VOLUME_BEEP_PENDING = true;

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

        initActions.put(KeyboardShortcuts.VOLUME_UP, new AbstractAction("VOLUME-UP") {
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

                    // Immediate effect while the key is held; the confirmation
                    // beep is deferred to the release (VOLUME_BEEP_PENDING).
                    Audio.refreshALLVolumes(false);

                    VOLUME_BEEP_PENDING = true;

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

        initActions.put(KeyboardShortcuts.FORCE_EXIT, new AbstractAction("FORCE_EXIT") {
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

        initActions.put(KeyboardShortcuts.SCREENSHOT, new AbstractAction("SCREENSHOT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (GameFrame.getInstance() != null) {

                    if (GameFrame.screenshotSonidoOn()) {
                        Audio.playWavResource("misc/screenshot.wav");
                    }

                    // Only two windows get photographed: the game log (registro) when it's the
                    // active window, otherwise the table (GameFrame). Any other focused dialog
                    // (exit, settings...) falls back to the table on purpose, so we never end up
                    // capturing a stray dialog. We're on the EDT (the dispatcher wraps the action
                    // in Helpers.GUIRun): render to an image here (printAll, no Robot/OS capture)
                    // and dump the PNG to disk in the background.
                    java.awt.Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

                    final javax.swing.JRootPane root = (active instanceof GameLogDialog && active.isShowing())
                            ? ((GameLogDialog) active).getRootPane()
                            : GameFrame.getInstance().getRootPane();

                    final BufferedImage image = Helpers.renderComponentImage(root);

                    Helpers.threadRun(() -> {

                        Helpers.saveScreenshot(image);

                        Helpers.GUIRun(() -> {
                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, Translator.translate("ui.captura_ok"), Color.WHITE, Color.BLACK, Init.class.getResource("/images/screenshot.png"), InGameNotifyDialog.SCREENSHOT_NOTIFICATION_TIMEOUT);
                            dialog.setLocation(dialog.getParent().getLocation());
                            dialog.setVisible(true);
                        });
                    });
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        kfm.addKeyEventDispatcher((KeyEvent e) -> {

            // While the Shortcuts tab is capturing a key, this dispatcher (and with it the
            // voice hook) steps aside so the pressed combo reaches the capturer.
            if (KeyboardShortcuts.isCapturing()) {
                return false;
            }

            // Configurable push-to-record key (voice messages, in game only)
            if (VoiceMessageManager.handleKeyEvent(e)) {
                return true;
            }

            // Volume confirmation beep on cursor-key RELEASE: a single sound once
            // the desired volume is reached, instead of the old debounce that could
            // sound twice (once in the pre-autorepeat gap, once at the end).
            // refreshALLVolumes(true) also forces the final authoritative refresh.
            // The event isn't consumed (others rely on the arrow keys).
            if (e.getID() == KeyEvent.KEY_RELEASED && (e.getKeyCode() == KeyboardShortcuts.keyCode(KeyboardShortcuts.VOLUME_UP) || e.getKeyCode() == KeyboardShortcuts.keyCode(KeyboardShortcuts.VOLUME_DOWN)) && VOLUME_BEEP_PENDING) {
                VOLUME_BEEP_PENDING = false;
                Audio.refreshALLVolumes(true);
            }

            KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
            // Resolve by the registry's id. Combos that belong to GameFrame resolve to an id
            // NOT present in initActions -> a = null -> this dispatcher lets them through.
            String id = KeyboardShortcuts.idFor(keyStroke);
            final Action a = id != null ? initActions.get(id) : null;
            if (a != null) {
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

        setupLanguageFlag();

        // Button bar that scales with the window: the action buttons' layout drops fixed
        // sizes so they follow the font/size (applyInitScale).
        rebuildActionButtonsLayout();

        setupHandCursors();

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
     * Captures the launcher window's screen, size, position and state
     * (maximized/normal) right before hiding it to launch a game, so it reopens
     * identically if the user cancels from the waiting room or returns to the
     * menu.
     */
    public static void captureLaunchFrameState() {
        if (VENTANA_INICIO != null) {
            LAUNCH_FRAME_MAXIMIZED = (VENTANA_INICIO.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            LAUNCH_FRAME_SIZE = VENTANA_INICIO.getSize();
        }
    }

    /**
     * Reopens the New Game dialog in recover mode to continue the last saved
     * game.
     *
     * @param local {@code true} to recover a local game, {@code false} for a
     * remote one
     */
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                settings_iconMouseClicked(evt);
            }
        });

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText(Helpers.wrapToolTip("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)"));
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
        language_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"Español", "English"}));
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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

    /**
     * Refreshes the launcher window's speaker icon to match SONIDOS. Called by
     * GameFrame.setSonidos so a change made from the audio settings dialog is
     * reflected here while no game is running yet.
     */
    public static void refreshSoundIcon() {

        Init ventana = VENTANA_INICIO;

        if (ventana != null) {
            Helpers.GUIRun(() -> {
                ventana.applySoundIconScaled();
            });
        }
    }

    private void settings_iconMouseClicked(java.awt.event.MouseEvent evt) {
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        // Opens the settings dialog in general mode (Appearance + Sound): there's no
        // GameFrame in the launcher, so the Game tab isn't mounted.
        SettingsDialog.open(this);
    }

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked

        // evt is null when the sound toggle is invoked programmatically (keyboard
        // shortcuts); in that case there's no real click to validate.
        if (evt != null && !Helpers.isRealClick(evt)) {
            return;
        }

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.GUIRun(() -> {
            applySoundIconScaled();
        });

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void language_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_language_comboboxActionPerformed
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

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        // First button-bar scale pass on show (sets the reference = the actual size already
        // realized, typically maximized -> scale 1.0, identical to the design).
        applyInitScale(computeInitScale());

        if (quote_timer != null) {
            if (quote_timer.isRunning()) {
                quote_timer.restart();
            } else {
                quote_timer.start();
            }
        }

    }//GEN-LAST:event_formComponentShown

    private void update_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_update_buttonActionPerformed
        // If the button already announces a new version, clicking it IS the
        // confirmation: launch the update directly, no re-check and no "want to
        // update?" popup again. In the "couldn't check" state (NEW_VERSION == null)
        // the button retries the check instead.
        if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
            final String target = NEW_VERSION;
            update_button.setVisible(false);
            update_label.setText(Translator.translate("update.preparando_actualizacion"));
            update_label.setVisible(true);
            Helpers.applicationTask(() -> {
                try {
                    performUpdate(target);
                } finally {
                    // performUpdate only returns if the update failed (on success it
                    // calls System.exit); restore the button so it can be retried.
                    Helpers.GUIRun(() -> {
                        update_label.setVisible(false);
                        update_button.setVisible(true);
                    });
                }
            }, "CoronaPoker-updater-download");
        } else {
            UPDATE();
        }
    }//GEN-LAST:event_update_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        Helpers.PROPERTIES.setProperty("master_volume", String.valueOf(Audio.MASTER_VOLUME));
        Helpers.savePropertiesFile();

    }//GEN-LAST:event_formWindowClosing

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentHidden
        if (quote_timer != null && quote_timer.isRunning()) {
            quote_timer.stop();
        }
    }//GEN-LAST:event_formComponentHidden

    private void baraja_fondoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_baraja_fondoMouseClicked
        // About opens ONLY on a click over the "corona poker" logo in the background
        // (top-left of corona_init.png), not anywhere on the image (cards/chips/felt).
        if (Helpers.isRealClick(evt) && isClickOnBackgroundLogo(evt.getX(), evt.getY())) {
            AboutDialog dialog = new AboutDialog(this, true);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }
    }//GEN-LAST:event_baraja_fondoMouseClicked

    // Logo area within corona_init.png, in image FRACTIONS (measured on the PNG:
    // "corona poker" + "by tonikelope" fit in the top-left corner).
    private static final float LOGO_FX0 = 0.00f, LOGO_FX1 = 0.31f, LOGO_FY0 = 0.00f, LOGO_FY1 = 0.27f;

    // Does the click (in baraja_fondo label coords) land on the logo? The image is scaled to
    // 90% of the screen and CENTERED in the label; map the click to image coordinates
    // (0..1 fraction) using the icon's LIVE size, so it's robust to scaling/resize with no
    // repositioning needed.
    private boolean isClickOnBackgroundLogo(int clickX, int clickY) {
        javax.swing.Icon ic = baraja_fondo.getIcon();
        if (ic == null) {
            return false;
        }
        int iconW = ic.getIconWidth();
        int iconH = ic.getIconHeight();
        if (iconW <= 0 || iconH <= 0) {
            return false;
        }
        int originX = (baraja_fondo.getWidth() - iconW) / 2;   // horizontal centering
        int originY = (baraja_fondo.getHeight() - iconH) / 2;  // vertical centering
        float fx = (clickX - originX) / (float) iconW;
        float fy = (clickY - originY) / (float) iconH;
        return fx >= LOGO_FX0 && fx <= LOGO_FX1 && fy >= LOGO_FY0 && fy <= LOGO_FY1;
    }

    // The hand cursor (HAND_CURSOR) should only show over clickable elements (logo, buttons,
    // flag, settings, sound). The background (cards/chips/felt) and its container panel use the
    // default cursor; over the background, the hand appears DYNAMICALLY only when the mouse is
    // over the logo (same hit-test that opens About). Buttons/flag/icons already have their own.
    private void setupHandCursors() {
        final java.awt.Cursor def = java.awt.Cursor.getDefaultCursor();
        final java.awt.Cursor hand = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR);
        baraja_fondo.setCursor(def);
        baraja_panel.setCursor(def);
        botones_panel.setCursor(def);
        baraja_fondo.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                baraja_fondo.setCursor(isClickOnBackgroundLogo(e.getX(), e.getY()) ? hand : def);
            }
        });
    }

    private void exit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exit_buttonActionPerformed
        WindowEvent windowEvent = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
        processWindowEvent(windowEvent);
    }//GEN-LAST:event_exit_buttonActionPerformed

    private void create_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_create_buttonActionPerformed

        // New game: always starts on default settings. Same as already done for bot difficulty,
        // reset here the settings that would otherwise leak from the previous game via session
        // statics (rabbit hunting, think time). The rest of the controls already default in the
        // dialog's constructor. To reuse a config, save it as a favorite preset. (On recover,
        // loadLastGame repopulates these controls from the recovered game's values.)
        Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
        GameFrame.RABBIT_HUNTING = 0;
        GameFrame.THINK_TIME = GameFrame.DEFAULT_THINK_TIME;
        GameFrame.THINK_TIME_ENABLED = true;
        GameFrame.SHOWDOWN_TIME = GameFrame.DEFAULT_SHOWDOWN_TIME;

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
        // Hover handled by GlassButtonUI (rollover); no more manual color inversion.
    }//GEN-LAST:event_create_buttonMouseExited

    private void create_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_create_buttonMouseEntered
        // Hover handled by GlassButtonUI (rollover); no more manual color inversion.
    }//GEN-LAST:event_create_buttonMouseEntered

    private void stats_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stats_buttonActionPerformed
        StatsDialog.showStats(this);
    }//GEN-LAST:event_stats_buttonActionPerformed

    private void join_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_join_buttonActionPerformed

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
        // Hover handled by GlassButtonUI (rollover); no more manual color inversion.
    }//GEN-LAST:event_join_buttonMouseExited

    private void join_buttonMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_join_buttonMouseEntered
        // Hover handled by GlassButtonUI (rollover); no more manual color inversion.
    }//GEN-LAST:event_join_buttonMouseEntered

    // Applies the "glass" (glassmorphism) style to the launcher's button bar over the existing
    // JButtons (setUI, non-invasive): rounded translucent black background that lets the felt
    // show through, gold accent for primary actions, soft hover painted by GlassButtonUI
    // (rollover). Removes the container's orange border. Touches neither layout, icons, actions
    // nor i18n.
    private void applyModernButtons() {
        final Color red = new Color(214, 78, 70);
        final Color green = new Color(70, 180, 110);

        // All with a neutral WHITE border (no gold); CREATE/JOIN a touch more opaque than
        // STATS to keep a subtle hierarchy by opacity rather than color.
        create_button.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        join_button.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        stats_button.setUI(new GlassButtonUI(null, false, false, 0.60f, 22));
        // Exit: neutral glass; red only appears on hover.
        exit_button.setUI(new GlassButtonUI(red, false, true, 0.66f, 22));
        // The EXIT icon (exit2.png) is a BLACK silhouette that's barely visible on the dark
        // glass; whiten it (keeping its alpha) to stand out, like the MENU icon in the final.
        javax.swing.ImageIcon white_exit = whitenIcon(exit_button.getIcon());
        if (white_exit != null) {
            exit_button.setIcon(white_exit);
        }
        // Update (only visible when a new version exists): green to stand out.
        update_button.setUI(new GlassButtonUI(green, true, false, 0.72f, 22));

        // Drop the button bar container's 5px orange border.
        botones_panel.setBorder(null);
    }

    // ---- Language selector as a FLAG (replaces the Spanish/English combo) ----
    // The flag shows the CURRENT language (Spain = es, Union Jack = en); clicking it
    // toggles both language and flag. Drawn in code (no extra image files).
    private javax.swing.JLabel language_flag;

    // Entry point to the screenshot viewer: camera icon to the right of the STATS button, at
    // its same height (square). Created and laid out in rebuildActionButtonsLayout; scaled in
    // applyInitScale.
    private javax.swing.JLabel screenshot_icon;

    // BASE height = same as the settings/sound icons (30); rectangular 3:2 width. The flag is
    // redrawn at its CURRENT size (flag_w/flag_h) because the button bar scales with the window.
    private static final int FLAG_H = 30;
    private static final int FLAG_W = 45;
    private int flag_w = FLAG_W;
    private int flag_h = FLAG_H;

    private void setupLanguageFlag() {
        language_flag = new javax.swing.JLabel();
        language_flag.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        updateLanguageFlag();
        language_flag.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (Helpers.isRealClick(e)) {
                    toggleLanguageByFlag();
                }
            }
        });

        // Rebuilds the bottom bar (jPanel1), replacing the combo with the flag:
        // [ EXIT (grows) ] [ flag ] [ settings ] [ sound ], vertically centered.
        jPanel1.remove(language_combobox);
        javax.swing.GroupLayout gl = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(gl);
        gl.setHorizontalGroup(
                gl.createSequentialGroup()
                        // EXIT fills the available width (MAX) but its PREFERRED size is its
                        // own content (not 605): this way jPanel1 doesn't impose a fixed width
                        // that, when shrinking, would dominate the action buttons and misalign them.
                        .addComponent(exit_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(16)
                        .addComponent(language_flag, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(14)
                        .addComponent(settings_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10)
                        .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        gl.setVerticalGroup(
                gl.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                        .addComponent(exit_button)
                        .addComponent(language_flag)
                        .addComponent(settings_icon)
                        .addComponent(sound_icon)
        );
        jPanel1.revalidate();
        jPanel1.repaint();
    }

    private void updateLanguageFlag() {
        boolean es = GameFrame.LANGUAGE.equals("es");
        language_flag.setIcon(es ? spainFlagIcon() : ukFlagIcon());
        language_flag.setToolTipText(es ? "Change language to English" : "Cambiar idioma a Español");
    }

    // ---- Launcher button bar that scales with the window (font + size) -----------------
    // Goal: the FONT adapts to the window size AND the BUTTON BAR resizes with it (buttons
    // follow the font). At full screen (larger sizes seen) everything is EXACTLY the 22.35
    // design (scale 1.0); shrinking the window shrinks the whole bar proportionally. Keys to
    // getting it right:
    //   1) the font is DERIVED from the real one already applied (keeps GUI_FONT, not a new Font);
    //   2) the whole felt panel is repainted (the glass buttons are non-opaque in the POPUP
    //      layer: repainting only the panel leaves trails when they reposition).
    // Minimum floor (legibility on tiny screens). NO upper cap: the bar grows proportionally on
    // ANY resolution above the canonical one (4K, 5K, 8K...) automatically. Safe, since it
    // always occupies the same screen fraction and never overflows.
    private static final float INIT_MIN_SCALE = 0.6f;
    private static final int CREATE_W = 453, JOIN_W = 463, ACTION_H = 80;
    // FIXED gap between CREATE and JOIN (same value used in the layout and when computing
    // STATS's width, so STATS spans EXACTLY the two twins). 12px is the real value of
    // addPreferredGap(UNRELATED) under Nimbus, so at 1440p (scale 1.0) it's PIXEL-PERFECT
    // identical to the 22.35 design (STATS = 453+12+463 = 928).
    private static final int TWIN_GAP = 12;
    // CANONICAL design resolution = 1440p (2560x1440), fixed (NOT the user's window): below
    // this — whether from monitor resolution or a shrunk window — the bar shrinks
    // proportionally, even maximized; at or above it, the bar shows at design size (cap 1.0).
    private static final int INIT_REF_W = 2560, INIT_REF_H = 1440;
    private int base_stats_h = 0;
    private boolean init_base_captured = false;
    // Last scale applied by applyInitScale: used by the mute toggle and refreshSoundIcon to
    // redraw the speaker icon at its CURRENT chip size. Otherwise toggling the icon would revert
    // it to its base size (30) and it would "stick out" while the bar is shrunk.
    private volatile float current_init_scale = 1f;
    private java.awt.Font base_create, base_join, base_stats, base_exit, base_update, base_update_label, base_quote;
    private javax.swing.Icon base_icon_create, base_icon_join, base_icon_exit, base_icon_stats;

    // Captures the already-initialized state ONCE (after Helpers.updateFonts, which applies
    // GUI_FONT): base fonts and icons at scale 1.0. Deriving from these bases guarantees
    // nothing changes at s=1.
    private void captureInitBaseIfNeeded() {
        if (init_base_captured) {
            return;
        }
        base_create = create_button.getFont();
        base_join = join_button.getFont();
        base_stats = stats_button.getFont();
        base_exit = exit_button.getFont();
        base_update = update_button.getFont();
        base_update_label = update_label.getFont();
        base_quote = quote.getFont();
        base_icon_create = create_button.getIcon();
        base_icon_join = join_button.getIcon();
        base_icon_exit = exit_button.getIcon();
        base_icon_stats = stats_button.getIcon();
        base_stats_h = stats_button.getPreferredSize().height;
        init_base_captured = true;
    }

    // Replaces the GENERATED layout of the action buttons (FIXED width/height literals
    // 453/463/80) with one that honors CREATE/JOIN's PREFERRED (scaled) size, with STATS
    // spanning both underneath. Called ONCE after the button bar is mounted.
    private void rebuildActionButtonsLayout() {
        // Screenshot viewer camera icon: next to STATS, at its same height. Uses
        // screenshot.png (256px) to scale up to the button's height without blurring.
        if (screenshot_icon == null) {
            screenshot_icon = new javax.swing.JLabel();
            screenshot_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            Helpers.setTranslatedToolTip(screenshot_icon, "ui.tooltip_visor_capturas");
            screenshot_icon.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (Helpers.isRealClick(e)) {
                        ScreenshotViewerDialog.open(Init.this);
                    }
                }
            });
        }
        javax.swing.GroupLayout gl = new javax.swing.GroupLayout(action_buttons_panel);
        action_buttons_panel.setLayout(gl);
        gl.setHorizontalGroup(
                gl.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(gl.createSequentialGroup()
                                .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(TWIN_GAP)
                                .addComponent(join_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(gl.createSequentialGroup()
                                .addComponent(stats_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(TWIN_GAP)
                                .addComponent(screenshot_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        gl.setVerticalGroup(
                gl.createSequentialGroup()
                        .addGroup(gl.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(join_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(gl.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                                .addComponent(stats_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(screenshot_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }

    // Button bar scale relative to the canonical DESIGN resolution (1440p): 1.0 at 1440p, grows
    // with NO cap above it (4K+), and below it applies a SOFT (square-root) curve so small
    // screens don't shrink too aggressively. Uses the smaller of the width/height ratios (also
    // reacts if only the height changes). Minimum floor for legibility; NO upper cap.
    private float computeInitScale() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return 1f;
        }
        // Linear ratio to the canonical (1440p): adapts to both the window size and the
        // monitor's resolution, even maximized. Uses the smaller of width/height.
        float raw = Math.min(w / (float) INIT_REF_W, h / (float) INIT_REF_H);
        // Band just below canonical (e.g. 1440p maximized loses ~3% height to the taskbar) ->
        // exact design size (PIXEL-PERFECT at 1440p). Doesn't affect s>=1.
        if (raw >= 0.92f && raw < 1f) {
            return 1f;
        }
        // At 1440p (1.0) or above: grows proportionally with NO upper cap (4K, 5K, 8K...).
        if (raw >= 1f) {
            return raw;
        }
        // BELOW canonical: SOFT curve (square root) so small resolutions don't shrink as
        // aggressively as the linear ratio (at 1366x768 -> ~0.73 instead of ~0.53). Still
        // monotonic and reaches 1.0 at canonical. Minimum floor for legibility.
        float s = (float) Math.sqrt(raw);
        return Math.max(INIT_MIN_SCALE, s);
    }

    // Applies the scale to the whole button bar. At s=1 the result is IDENTICAL to the 22.35 design.
    private void applyInitScale(float s) {
        captureInitBaseIfNeeded();
        current_init_scale = s;

        setScaledFont(create_button, base_create, s);
        setScaledFont(join_button, base_join, s);
        setScaledFont(stats_button, base_stats, s);
        setScaledFont(exit_button, base_exit, s);
        setScaledFont(update_button, base_update, s);
        setScaledFont(update_label, base_update_label, s);
        // The footer quote also scales its font with the window.
        setScaledFont(quote, base_quote, s);

        // Glass padding (base EmptyBorder(10,22) from GlassButtonUI) + icon/text gap (14).
        int pv = Math.round(10 * s), ph = Math.round(22 * s), gap = Math.round(14 * s);
        for (javax.swing.JButton b : new javax.swing.JButton[]{create_button, join_button, stats_button, exit_button, update_button}) {
            b.setBorder(javax.swing.BorderFactory.createEmptyBorder(pv, ph, pv, ph));
            b.setIconTextGap(gap);
        }

        // CREATE/JOIN at their scaled DESIGN size; STATS + gap + camera span EXACTLY the two
        // twins (CREATE + gap + JOIN). The camera is square, at STATS's height.
        int cw = Math.round(CREATE_W * s), jw = Math.round(JOIN_W * s);
        int stats_h = Math.round(base_stats_h * s);
        int cam = stats_h;
        create_button.setPreferredSize(new java.awt.Dimension(cw, Math.round(ACTION_H * s)));
        join_button.setPreferredSize(new java.awt.Dimension(jw, Math.round(ACTION_H * s)));
        stats_button.setPreferredSize(new java.awt.Dimension(cw + jw - cam, stats_h));
        screenshot_icon.setPreferredSize(new java.awt.Dimension(cam, cam));
        Helpers.setScaledIconLabel(screenshot_icon, getClass().getResource("/images/screenshot.png"), cam, cam);

        // Button icons: scaled from their BASE icon (identical at s=1).
        setScaledIconFromBase(create_button, base_icon_create, s);
        setScaledIconFromBase(join_button, base_icon_join, s);
        setScaledIconFromBase(exit_button, base_icon_exit, s);
        setScaledIconFromBase(stats_button, base_icon_stats, s);

        // Flag (redrawn) + gear + speaker (base 30).
        int chip = Math.round(30 * s);
        flag_w = Math.round(FLAG_W * s);
        flag_h = Math.round(FLAG_H * s);
        updateLanguageFlag();
        settings_icon.setPreferredSize(new java.awt.Dimension(chip, chip));
        Helpers.setScaledBlackIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), chip, chip);
        applySoundIconScaled();

        // Re-layout + FULL repaint of the felt panel (avoids trails from non-opaque buttons).
        action_buttons_panel.revalidate();
        jPanel1.revalidate();
        corona_init_panel.revalidate();
        botones_panel.revalidate();
        tapete.revalidate();
        tapete.repaint();
    }

    // Derives a scaled font from a base, keeping family and style (does NOT create a new Font).
    private void setScaledFont(javax.swing.JComponent c, java.awt.Font base, float s) {
        if (base != null) {
            c.setFont(base.deriveFont(Math.max(1f, base.getSize2D() * s)));
        }
    }

    // Scales the button's BASE (native) icon by the factor; identical to the original at s=1.
    private void setScaledIconFromBase(javax.swing.AbstractButton b, javax.swing.Icon base, float s) {
        if (base instanceof javax.swing.ImageIcon) {
            java.awt.Image img = ((javax.swing.ImageIcon) base).getImage();
            int bw = base.getIconWidth(), bh = base.getIconHeight();
            if (bw > 0 && bh > 0 && img != null) {
                b.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(Math.max(1, Math.round(bw * s)), Math.max(1, Math.round(bh * s)), java.awt.Image.SCALE_SMOOTH)));
            }
        }
    }

    // Draws the speaker icon (sound/mute per SONIDOS) at the button bar's CURRENT chip size
    // (remembered scale). Shared by applyInitScale, the mute toggle and refreshSoundIcon, so
    // toggling sound doesn't revert the icon to its base size and "stick out" when shrunk.
    private void applySoundIconScaled() {
        int chip = Math.max(1, Math.round(30 * current_init_scale));
        sound_icon.setPreferredSize(new java.awt.Dimension(chip, chip));
        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), chip, chip);
    }

    // Whitens an icon's silhouette (opaque areas -> white), preserving its alpha. For BLACK
    // line icons (e.g. exit2.png for EXIT) that would be invisible on the dark glass.
    private static javax.swing.ImageIcon whitenIcon(javax.swing.Icon icon) {
        if (!(icon instanceof javax.swing.ImageIcon)) {
            return null;
        }
        int w = icon.getIconWidth(), h = icon.getIconHeight();
        if (w <= 0 || h <= 0) {
            return (javax.swing.ImageIcon) icon;
        }
        java.awt.image.BufferedImage src = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = src.createGraphics();
        g.drawImage(((javax.swing.ImageIcon) icon).getImage(), 0, 0, null);
        g.dispose();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (src.getRGB(x, y) >>> 24);
                out.setRGB(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        return new javax.swing.ImageIcon(out);
    }

    // Toggles language + flag (same logic as the old language_comboboxActionPerformed).
    private void toggleLanguageByFlag() {
        GameFrame.LANGUAGE = GameFrame.LANGUAGE.equals("es") ? "en" : "es";
        Helpers.PROPERTIES.setProperty("lenguaje", GameFrame.LANGUAGE);
        Helpers.savePropertiesFile();
        Helpers.translateComponents(this, false);
        translateGlobalLabels();
        Crupier.loadMODSounds();
        Helpers.setCoronaLocale();
        printQuote();
        updateLanguageFlag();
    }

    // Redrawn at the CURRENT size (flag_w/flag_h): the bar scales with the window and drawing 2
    // small flags on every resize end is negligible. Drawn at 2x then downscaled (antialiasing).
    private javax.swing.ImageIcon spainFlagIcon() {
        return new javax.swing.ImageIcon(drawSpainFlag(flag_w * 2, flag_h * 2).getScaledInstance(flag_w, flag_h, java.awt.Image.SCALE_SMOOTH));
    }

    private javax.swing.ImageIcon ukFlagIcon() {
        return new javax.swing.ImageIcon(drawUKFlag(flag_w * 2, flag_h * 2).getScaledInstance(flag_w, flag_h, java.awt.Image.SCALE_SMOOTH));
    }

    // Spanish flag (rojigualda): red/gold/red stripes (1/4, 1/2, 1/4).
    private static java.awt.image.BufferedImage drawSpainFlag(int w, int h) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(198, 11, 30));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 196, 0));
        g.fillRect(0, Math.round(h * 0.25f), w, Math.round(h * 0.5f));
        g.setColor(new Color(0, 0, 0, 130));
        g.drawRect(0, 0, w - 1, h - 1);
        g.dispose();
        return img;
    }

    // United Kingdom flag (Union Jack), simplified (centered diagonal stripes).
    private static java.awt.image.BufferedImage drawUKFlag(int w, int h) {
        final Color BLUE = new Color(1, 33, 105);
        final Color RED = new Color(200, 16, 46);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(0, 0, w, h);
        g.setColor(BLUE);
        g.fillRect(0, 0, w, h);
        g.setStroke(new java.awt.BasicStroke(h * 0.30f));
        g.setColor(Color.WHITE);
        g.drawLine(0, 0, w, h);
        g.drawLine(0, h, w, 0);
        g.setStroke(new java.awt.BasicStroke(h * 0.12f));
        g.setColor(RED);
        g.drawLine(0, 0, w, h);
        g.drawLine(0, h, w, 0);
        int wc = Math.round(h * 0.34f);
        g.setColor(Color.WHITE);
        g.fillRect(0, h / 2 - wc / 2, w, wc);
        g.fillRect(w / 2 - wc / 2, 0, wc, h);
        int rc = Math.round(h * 0.20f);
        g.setColor(RED);
        g.fillRect(0, h / 2 - rc / 2, w, rc);
        g.fillRect(w / 2 - rc / 2, 0, rc, h);
        g.setColor(new Color(0, 0, 0, 130));
        g.drawRect(0, 0, w - 1, h - 1);
        g.dispose();
        return img;
    }

    /**
     * Submits the deadlock detection loop to the current thread pool. The loop
     * exits cleanly when interrupted (pausar() re-raises the flag after
     * catching the sleep interrupt, so the while-check breaks on the next
     * pass), avoiding an infinite SEVERE spam when the pool is shut down. Call
     * this once at app startup and again after every CREATE_THREAD_POOL to keep
     * the detector alive across game sessions.
     */
    public static void startDeadlockDetector() {
        Helpers.threadRun(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Helpers.detectAndHandleDeadlocks();
                Helpers.pausar(DEADLOCK_DETECT_WAIT);
            }
        });
    }

    /**
     * Writes the current startup step onto the splash screen.
     *
     * A slow startup (antivirus scanning the jar after a system update, or the
     * OS taking its time to seed the CSPRNG) used to be indistinguishable from
     * a hung one, because the splash said nothing. Each step replaces the last
     * on a rounded pill at the foot of the card.
     *
     * No-op if there's no splash (running from the IDE, or launched without
     * -splash) and never propagates: this is cosmetic and must never take
     * startup down with it.
     */
    private static void splashStep(String msg) {

        try {
            java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();

            if (splash == null || msg == null || !splash.isVisible()) {
                return;
            }

            java.awt.Graphics2D g = splash.createGraphics();

            if (g == null) {
                return;
            }

            try {
                java.awt.Dimension size = splash.getSize();
                int band_y = size.height - SPLASH_STEP_BOTTOM_MARGIN - SPLASH_STEP_BAND_HEIGHT;

                // Clear the previous band instead of repainting it white: the drawing
                // surface is composited OVER the splash gif, so making it transparent
                // is what restores the original background.
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fillRect(0, band_y, size.width, SPLASH_STEP_BAND_HEIGHT);
                g.setPaintMode();

                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // The game's font is registered at the very start of boot (before the
                // first step), so every step is rendered with it consistently.
                g.setFont(Helpers.GUI_FONT.deriveFont(Font.BOLD, (float) SPLASH_STEP_FONT_SIZE));

                java.awt.FontMetrics fm = g.getFontMetrics();
                int max_text_width = size.width - 2 * SPLASH_STEP_PILL_PADDING - 20;
                String text = msg;

                while (text.length() > 1 && fm.stringWidth(text) > max_text_width) {
                    text = text.substring(0, text.length() - 2) + "…";
                }

                int text_width = fm.stringWidth(text);

                g.setColor(SPLASH_STEP_PILL_COLOR);
                g.fillRoundRect((size.width - text_width) / 2 - SPLASH_STEP_PILL_PADDING, band_y, text_width + 2 * SPLASH_STEP_PILL_PADDING, SPLASH_STEP_BAND_HEIGHT, SPLASH_STEP_BAND_HEIGHT, SPLASH_STEP_BAND_HEIGHT);
                g.setColor(SPLASH_STEP_TEXT_COLOR);
                g.drawString(text, (size.width - text_width) / 2, band_y + (SPLASH_STEP_BAND_HEIGHT - fm.getHeight()) / 2 + fm.getAscent());

            } finally {
                g.dispose();
            }

            splash.update();

        } catch (Throwable ignored) {
            // The splash can close (when the first window becomes visible) between
            // the check and the update, which throws IllegalStateException.
        }
    }

    /**
     * Aborts startup with a user-facing message instead of leaving a zombie
     * process.
     *
     * Pool threads aren't daemons and the deadlock detector loops forever, so
     * if the main thread dies from an Error (e.g. the SQLite native library
     * failing to load), the JVM stays alive with no window behind it and the
     * splash frozen on screen forever.
     */
    private static void fatalStartupError(String msg, Throwable cause) {

        LOGGER.log(Level.SEVERE, "FATAL: startup aborted", cause);

        try {
            java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();

            if (splash != null && splash.isVisible()) {
                splash.close();
            }
        } catch (Throwable ignored) {
        }

        try {
            final String text = (cause != null) ? msg + "\n\n" + cause : msg;

            javax.swing.SwingUtilities.invokeAndWait(() -> {
                javax.swing.JOptionPane.showMessageDialog(null, text, WINDOW_TITLE, javax.swing.JOptionPane.ERROR_MESSAGE);
            });

        } catch (Throwable ignored) {
            // No usable GUI; the detail is already in the log, so exit anyway.
        }

        System.exit(1);
    }

    public static void main(String args[]) {

        try {
            boot(args);
        } catch (Throwable ex) {
            fatalStartupError(Translator.translate("error.arranque_fatal", DEBUG_DIR), ex);
        }
    }

    private static void boot(String args[]) {

        //ensureRequiredJvmParameters(args, Init.class);
        setupConsoleLogger();

        // Startup housekeeping: cap the unbounded growth of persisted voice notes.
        // Off the boot path on a background thread: it has zero dependency on the
        // rest of startup (notes only matter when a chat line is clicked later),
        // and it pulls in AudioDeviceManager whose init enumerates audio mixers
        // (tens-to-hundreds of ms on Windows). Not in loadPropertiesFile because
        // the configurable retention lives in AudioDeviceManager, whose static
        // init reads Helpers.PROPERTIES (still null during that early phase).
        Helpers.applicationTask(Helpers::purgeOldVoiceNotes,
                "CoronaPoker-voice-note-maintenance");

        startDeadlockDetector();

        if (GameFrame.TEST_MODE) {
            GameFrame.CINEMATICAS_PREF = false;
        }

        if (!Init.DEV_MODE) {
            SQL_FILE = CORONA_DIR + "/coronapoker.db";
        } else {
            // DEV_MODE: work on a disposable temp copy so we don't mutate the real DB.
            // Guard: SQL_FILE must NEVER stay null (otherwise it would open
            // "jdbc:sqlite:null" and create a "null" file). If the real DB doesn't
            // exist, use an empty temp one; if the copy fails, fall back to the real path.
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

        // The game font is registered before announcing the first step so every
        // splash step already renders with it (only reads a jar resource and
        // registers it: no dependency on the DB or the CSPRNG that come later).
        Helpers.GUI_FONT = Helpers.createAndRegisterFont(Helpers.class.getResourceAsStream("/fonts/McLaren-Regular.ttf"));

        splashStep(Translator.translate("splash.base_datos"));

        LOGGER.log(Level.INFO, "Loading SQLITE DB...");

        // No database means no stats, no game recovery, no TOFU identities: no
        // point continuing a half-booted startup.
        if (!Helpers.initSQLITE()) {
            fatalStartupError(Translator.translate("error.bd_fatal", DEBUG_DIR), null);
        }

        splashStep(Translator.translate("splash.aleatoriedad"));

        try {
            LOGGER.log(Level.INFO, "Trying to load CSPRNG HASH DRBG SHA-512...");
            Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,reseed_only");
            Helpers.CSPRNG_GENERATOR = SecureRandom.getInstance("DRBG");
            LOGGER.log(Level.INFO, "CSPRNG OK");
        } catch (NoSuchAlgorithmException ex) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
            LOGGER.log(Level.WARNING, "Fallback CSPRNG -> {0}", Helpers.CSPRNG_GENERATOR.getAlgorithm());
        }

        splashStep(Translator.translate("splash.recursos"));

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

            // A MOD may not ship its own decks (just sounds, images or background): then
            // there's no "decks" key and nothing to add to the catalog here.
            HashMap<String, HashMap> mod_decks = (HashMap<String, HashMap>) Init.MOD.get("decks");

            if (mod_decks != null) {
                for (Map.Entry<String, HashMap> entry : mod_decks.entrySet()) {
                    HashMap<String, Object> baraja = entry.getValue();
                    Card.BARAJAS.put((String) baraja.get("name"), new Object[]{baraja.get("aspect"), true, baraja.containsKey("sound") ? baraja.get("sound") : null});
                }
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

        // Pre-decodes the current deck's shuffle.gif in the background at startup,
        // so the first hand doesn't pay the decode cost.
        Crupier.warmShuffleAnimCache();

        // Same for the animated About logo (corona_logo.gif): pre-decode in the
        // background so the first About open doesn't pay the decode cost.
        AboutDialog.warmupLogoAnim();

        // Warms up the JIT for the heavy crypto (SRA cascade + shuffle proof/verification) in
        // the background, so the first hands don't run interpreted/C1 on slow machines
        // (multi-second cold vs ~0.1s once compiled). See CryptoWarmup.
        com.tonikelope.coronapoker.crypto.CryptoWarmup.warmup();

        splashStep(Translator.translate("splash.cartas"));

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

        splashStep(Translator.translate("splash.audio"));

        Audio.MASTER_VOLUME = master_volume;

        if (!GameFrame.SONIDOS) {
            Audio.muteAll();
        } else {
            Audio.unmuteAll();
        }

        // init.wav is the FIRST sound of the process and used to play clipped
        // sometimes: it played while the OS was still waking up the audio endpoint.
        // Warm the device with a silent line and ONLY THEN play init.wav, on a
        // separate thread so the window isn't delayed.
        Helpers.applicationTask(() -> {
            Audio.warmAudioDevice();
            Audio.playWavResourceAndWait("misc/init.wav", true, false, !GameFrame.arranqueSonidoOn());
            // uncover.wav (card reveal) is deck-independent (misc/) and plays on every
            // card flip: preloaded ONCE here, with the endpoint already warm, so every
            // reveal starts instantly (clip pre-opened and reused, no per-reveal line
            // open lagging behind the flip animation). Never invalidated.
            Audio.preloadWav("misc/uncover.wav");
        }, "CoronaPoker-audio-warmup");

        Audio.playLoopMp3Resource("misc/background_music.mp3");

        splashStep(Translator.translate("splash.ventana"));

        LOGGER.log(Level.INFO, "Loading GUI Window...");

        Helpers.GUIRun(() -> {
            VENTANA_INICIO = new Init();
            // Maximized on the primary monitor, but with the restored size (when
            // un-maximized) fixed to a centered 80% instead of the huge default
            // that spilled off-screen.
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

    // Downloads the updater and launches the update to the given version. On
    // success it calls System.exit(0) (the updater takes over) and does NOT
    // return; if the download fails or throws, it notifies the user and returns
    // so the caller can restore the UI. Must be invoked from a background
    // thread: downloadUpdater() blocks on the network.
    private static void performUpdate(String version) {
        Helpers.GUIRun(() -> {
            VENTANA_INICIO.update_label.setText(Translator.translate("update.preparando_actualizacion"));
        });
        try {
            String current_jar_path = Helpers.getCurrentJarPath();
            // replace (literal) instead of replaceAll (regex) — the '.' in
            // "20.66.jar" is a regex metacharacter that would match any char (e.g.
            // paths like "20X66Yjar" or "20<x>66<x>jar"). replace does a literal
            // substring match, which is what's needed here.
            String new_jar_path = current_jar_path.replace(AboutDialog.VERSION + ".jar", version + ".jar");
            String updater_jar = Helpers.downloadUpdater();

            if (updater_jar != null) {
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
        Helpers.applicationTask(() -> {
            // Only the "checking for update..." label: the action buttons stay free
            // during the check (with a slow GitHub, retries can take several seconds
            // and shouldn't block the user; the panel's setEnabled(false) that used
            // to be here was also a no-op — JPanel doesn't propagate disable to its
            // children). If the user already jumped into a game, the update offer
            // simply doesn't show that session (window visible + active guard).
            Helpers.GUIRun(() -> {
                VENTANA_INICIO.update_label.setVisible(true);
                VENTANA_INICIO.update_button.setVisible(false);
            });
            // Reset so a manual UPDATE click always re-checks (a prior "already
            // up to date" check leaves NEW_VERSION blank).
            NEW_VERSION = null;

            // try/finally: the check is best-effort and runs in the background, but
            // no matter what happens (unexpected network exception, Error, dialog
            // failure) the finally MUST restore the UI — otherwise the "CHECKING FOR
            // UPDATE..." label stays stuck forever.
            try {
                // Up to UPDATE_CHECK_RETRIES silent attempts: if GitHub doesn't
                // respond, just leave the UPDATE button visible for a manual check
                // (the "retry?" modal dialog that used to be here could ambush a
                // user already in a game, lacking the visible/active window guard
                // that the offer itself has).
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
                    // The button covers two very different states that must be
                    // distinguishable at a glance: either a new version was found
                    // (but the popup didn't complete — e.g. the window wasn't
                    // active), or the check couldn't reach anything (network/
                    // timeout). Labeled and colored per case so the user knows what
                    // clicking it does: update, or retry the check. NEW_VERSION == ""
                    // (already up to date) doesn't reach here: the button stays
                    // hidden. The i18n.key is also updated so a language change
                    // reapplies the right text (Helpers.translateComponents).
                    if (NEW_VERSION != null && !NEW_VERSION.isBlank()) {
                        VENTANA_INICIO.update_button.putClientProperty("i18n.key", "update.boton_hay_version_nueva");
                        VENTANA_INICIO.update_button.setText(Translator.translate("update.boton_hay_version_nueva"));
                        // Bright yellow (not the previous dark green): green barely contrasted on
                        // GlassButtonUI's dark glass. The font is already bold (Dialog BOLD 18,
                        // preserved by updateFonts/setScaledFont) like the rest of the bar.
                        VENTANA_INICIO.update_button.setForeground(new Color(255, 214, 0));
                        VENTANA_INICIO.update_button.setVisible(true);
                    } else if (NEW_VERSION == null) {
                        VENTANA_INICIO.update_button.putClientProperty("i18n.key", "update.boton_reintentar");
                        VENTANA_INICIO.update_button.setText(Translator.translate("update.boton_reintentar"));
                        VENTANA_INICIO.update_button.setForeground(new Color(204, 102, 0));
                        VENTANA_INICIO.update_button.setVisible(true);
                    }
                });
            }
        }, "CoronaPoker-update-check");
    }

    private static void antiScreensaver() {

        // Robot is only for the key-press FALLBACK (platforms without a native
        // wake-lock path). Windows/Linux don't need it via the native path; if it
        // can't be created (headless), we just continue without a fallback.
        Robot rob;
        try {
            rob = new Robot();
        } catch (AWTException ex) {
            LOGGER.log(Level.WARNING, "Robot unavailable — anti-screensaver key fallback disabled", ex);
            rob = null;
        }
        final Robot fallback_robot = rob;

        // Daemon timer: a single thread for the app's whole lifetime, daemon so it
        // never blocks JVM shutdown. SetThreadExecutionState is per-thread, so the
        // wake-lock is ALWAYS refreshed from this same thread.
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
