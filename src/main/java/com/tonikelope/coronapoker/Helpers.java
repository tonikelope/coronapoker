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

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.gif.GifControlDirectory;
import org.dosse.upnp.UPnP;
import static com.tonikelope.coronapoker.Init.CORONA_DIR;
import static com.tonikelope.coronapoker.Init.DEBUG_DIR;
import static com.tonikelope.coronapoker.Init.LOGS_DIR;
import static com.tonikelope.coronapoker.Init.SCREENSHOTS_DIR;
import static com.tonikelope.coronapoker.Init.SQLITE;
import static com.tonikelope.coronapoker.Init.SQL_FILE;
import static com.tonikelope.coronapoker.Init.VENTANA_INICIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.undo.UndoManager;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.sqlite.SQLiteConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.color.ColorSpace;
import java.awt.image.ColorConvertOp;
import java.awt.image.RescaleOp;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.TreeMap;
import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.plaf.synth.SynthFormattedTextFieldUI;
import javax.swing.text.JTextComponent;
import static com.tonikelope.coronapoker.Init.CHAT_IMAGE_CACHE;
import static java.beans.Beans.isDesignTime;
import java.util.Base64;

/**
 * Grab-bag of static helpers used across the app (GUI, crypto, I/O, config,
 * misc).
 *
 * @author tonikelope
 */
public class Helpers {

    private static final Logger LOGGER = Logger.getLogger(Helpers.class.getName());

    public static volatile ThreadPoolExecutor THREAD_POOL;
    // Single-thread FIFO queue for the log (GameLogDialog.print). THREAD_POOL is
    // multi-threaded, so two concurrent print() calls — or even sequential ones from
    // the same thread — could land in the log out of call order (e.g. the end-of-game
    // table slipping in between a hand's actions). A single consumer guarantees
    // arrival order. Same lifecycle as THREAD_POOL (created/destroyed per game in
    // CREATE/SHUTDOWN_THREAD_POOL) so it drains and recreates the same way without
    // leaking threads or carrying prints over between games.
    public static volatile ExecutorService LOG_POOL;
    public static final int THREAD_POOL_SHUTDOWN_TIMEOUT = 5;
    public static final int COOPERATIVE_SESSION_IO_TIMEOUT_MS = 3000;
    public static final String USER_AGENT_WEB_BROWSER = "Mozilla/5.0 (X11; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0";
    public static final String USER_AGENT_CORONAPOKER = "CoronaPoker " + AboutDialog.VERSION + " tonikelope@gmail.com";
    // Used by the version check (CoronaPoker and MOD) and the updater downloader's
    // connect. Generous on purpose: on a slow PC/network at startup, a short cap
    // aborted the check too early and left the user with only the manual button. The
    // check runs in the background and retries silently (Init.UPDATE_CHECK_RETRIES)
    // without blocking anything — the user can enter a game meanwhile — so 10 s costs
    // nothing.
    public static final int HTTP_TIMEOUT = 10000;
    // WEAK keys: a live component is always strongly referenced by its container, so
    // its entry survives while it's in use; once the dialog holding it is dispose()d
    // and dereferenced, the GC evicts the entry on its own. This closes the slow leak
    // the ConcurrentHashMap had (every PauseDialog/ShortcutsDialog reopen pinned its
    // whole component tree). Only accessed via put/get/containsKey (never iterated),
    // so synchronizedMap is enough for thread safety.
    public static final Map<Component, Integer> ORIGINAL_FONT_SIZE = Collections.synchronizedMap(new WeakHashMap<>());
    public static final String PROPERTIES_FILE = Init.CORONA_DIR + "/coronapoker.properties";
    // Path of the rescue copy when the preferences file came back unreadable, so
    // startup can warn once the log already exists. null = no incident.
    public static volatile String PROPERTIES_RESCUE_COPY = null;
    // Upper bound on a command line's size (post-Base64 + encryption + HMAC). Covers
    // with margin the largest message the legitimate protocol can produce (an SRA
    // MEGAPACKET at 52*32 = 1664 bytes + AES padding + IV + HMAC + Base64 runs 2-4 KB;
    // a serialized RECOVERDATA runs tens of KB). 16 MB is ~1000x any real command and
    // cuts off the infinite-line-in-readLine OOM path.
    public static final int MAX_COMMAND_LINE_CHARS = 16 * 1024 * 1024;
    public static final int DECK_ELEMENTS = 52;
    public static final int MIN_GIF_FRAME_DELAY = 3;
    public static final int DIALOG_ICON_SIZE = 70;
    public static final float MESSAGE_DIALOG_ZOOM = 1.3f;
    // Range of the GLOBAL dialog zoom (user preference). 1.0 = design size.
    public static final float DIALOG_ZOOM_MIN = 0.5f;
    public static final float DIALOG_ZOOM_MAX = 2.0f;
    public static ArrayList<String> POKER_QUOTES_ES = new ArrayList<>();
    public static ArrayList<String> POKER_QUOTES_EN = new ArrayList<>();
    public static volatile ImageIcon IMAGEN_BB = null;
    public static volatile ImageIcon IMAGEN_SB = null;
    public static volatile ImageIcon IMAGEN_DEALER = null;
    public static volatile ImageIcon IMAGEN_DEAD_DEALER = null;
    public static volatile ImageIcon IMAGEN_STRADDLE = null;
    public static volatile ImageIcon IMAGEN_DEALER_STRADDLE = null;
    public static volatile ImageIcon IMAGEN_POT_CHIP = null;

    public volatile static SecureRandom CSPRNG_GENERATOR = null;
    public volatile static Properties PROPERTIES = isDesignTime() ? new Properties() : loadPropertiesFile();
    // GLOBAL dialog zoom (font + window size), user preference under Settings ->
    // Appearance. 1.0 = design size (identical to before). INDEPENDENT of the TABLE
    // zoom (GameFrame.ZOOM_LEVEL), which this control does NOT touch.
    public volatile static float DIALOG_ZOOM = readDialogZoom();
    public volatile static Font GUI_FONT = null;

    static {
        if (!isDesignTime()) {

            Helpers.CREATE_THREAD_POOL();

            try {

                POKER_QUOTES_ES = (ArrayList<String>) getResourceTextFileAsList("quotes_ES.txt");
                POKER_QUOTES_EN = (ArrayList<String>) getResourceTextFileAsList("quotes_EN.txt");

                if (POKER_QUOTES_ES != null && POKER_QUOTES_ES.size() != POKER_QUOTES_EN.size()) {
                    LOGGER.log(Level.WARNING, "Quotes files length do not match — truncating");

                    final int size = Math.min(POKER_QUOTES_ES.size(), POKER_QUOTES_EN.size());
                    POKER_QUOTES_ES = (ArrayList<String>) POKER_QUOTES_ES.subList(0, size);
                    POKER_QUOTES_EN = (ArrayList<String>) POKER_QUOTES_EN.subList(0, size);
                }

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    public static class LeftClickMenuItem extends JMenuItem {

        private volatile int lastMouseButton = MouseEvent.BUTTON1;

        public LeftClickMenuItem(Action menu_item_action) {

            // Wrap the action so right-click is blocked
            setAction(new AbstractAction(
                    (String) menu_item_action.getValue(Action.NAME),
                    (Icon) menu_item_action.getValue(Action.SMALL_ICON)
            ) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (lastMouseButton == MouseEvent.BUTTON1) {
                        menu_item_action.actionPerformed(e);
                    }
                }
            });

            setToolTipText((String) menu_item_action.getValue(Action.SHORT_DESCRIPTION));

            setAccelerator((KeyStroke) menu_item_action.getValue(Action.ACCELERATOR_KEY));

            // Track which mouse button was pressed
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouseButton = e.getButton();
                }
            });
        }
    }

    public static class LeftClickCheckBoxMenuItem extends JCheckBoxMenuItem {

        private volatile int lastMouseButton = MouseEvent.BUTTON1;

        public LeftClickCheckBoxMenuItem(Action menu_item_action) {

            // Wrap the action so right-click is blocked
            setAction(new AbstractAction(
                    (String) menu_item_action.getValue(Action.NAME),
                    (Icon) menu_item_action.getValue(Action.SMALL_ICON)
            ) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (lastMouseButton == MouseEvent.BUTTON1) {
                        menu_item_action.actionPerformed(e);
                    } else {
                        setSelected(!isSelected());
                    }
                }
            });

            setToolTipText((String) menu_item_action.getValue(Action.SHORT_DESCRIPTION));

            setAccelerator((KeyStroke) menu_item_action.getValue(Action.ACCELERATOR_KEY));

            // Track which mouse button was pressed
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouseButton = e.getButton();
                }
            });
        }
    }

    public static class LeftClickRadioButtonMenuItem extends JRadioButtonMenuItem {

        private volatile int lastMouseButton = MouseEvent.BUTTON1;

        public LeftClickRadioButtonMenuItem(Action menu_item_action) {

            // Wrap the action so right-click is blocked
            setAction(new AbstractAction(
                    (String) menu_item_action.getValue(Action.NAME),
                    (Icon) menu_item_action.getValue(Action.SMALL_ICON)
            ) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (lastMouseButton == MouseEvent.BUTTON1) {
                        menu_item_action.actionPerformed(e);
                    } else {
                        setSelected(!isSelected());
                    }
                }
            });

            setToolTipText((String) menu_item_action.getValue(Action.SHORT_DESCRIPTION));

            setAccelerator((KeyStroke) menu_item_action.getValue(Action.ACCELERATOR_KEY));

            // Track which mouse button was pressed
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouseButton = e.getButton();
                }
            });
        }
    }

    public static void cleanHandCrupierTempFiles(int gameId) {
        deleteHandFossil(gameId);
    }

    public static void setSpinnerColors(JSpinner spinner, Color background, Color foreground) {

        final JComponent editor = spinner.getEditor();

        // The Nimbus DefaultEditor paints itself opaque from x=0, so its background
        // peeks out a few pixels to the left of the rect we fill in fillRect(3,3,...).
        // Making it non-opaque leaves that inset-3 rect as the only painted area — the
        // same inset (left=3, top=3) Nimbus uses for the button bar background, so the
        // spinner lines up pixel-for-pixel with it.
        editor.setOpaque(false);

        int c = editor.getComponentCount();

        for (int i = 0; i < c; i++) {
            final Component comp = editor.getComponent(i);

            if (comp instanceof JTextComponent) {

                ((JTextComponent) comp).setUI(new SynthFormattedTextFieldUI() {

                    @Override
                    protected void paint(javax.swing.plaf.synth.SynthContext context, java.awt.Graphics g) {

                        if (comp.isEnabled()) {
                            // Enable antialiasing for the text
                            Graphics2D g2d = (Graphics2D) g;
                            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                            // Custom background
                            g2d.setColor(background);
                            g2d.fillRect(3, 3, getComponent().getWidth() - 6, getComponent().getHeight() - 6);

                            // Custom text color
                            g2d.setColor(foreground);
                            g2d.setFont(getComponent().getFont());

                            // Draw the text manually
                            String text = ((JTextComponent) comp).getText();
                            FontMetrics fm = g2d.getFontMetrics();

                            int alignment = JTextField.LEFT;  // Default value

                            // Check whether the component is a JTextField
                            if (comp instanceof JTextField) {
                                alignment = ((JTextField) comp).getHorizontalAlignment();
                            }

                            // Compute the X position from the alignment
                            int x = 5;  // Default left margin

                            if (alignment == JTextField.RIGHT) {
                                x = getComponent().getWidth() - fm.stringWidth(text) - 5;  // Right-align
                            } else if (alignment == JTextField.CENTER) {
                                x = (getComponent().getWidth() - fm.stringWidth(text)) / 2;  // Center it
                            }

                            int y = (getComponent().getHeight() + fm.getAscent()) / 2 - 2; // Vertically centered

                            // Draw the text
                            g2d.drawString(text, x, y);

                        } else {
                            super.paint(context, g);
                        }
                    }
                });
            }
        }

        Helpers.GUIRun(() -> {

            spinner.revalidate();
            spinner.repaint();
        });
    }

    public static void detectAndHandleDeadlocks() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long[] threadIds = threadMXBean.findDeadlockedThreads();

        if (threadIds != null) {

            LOGGER.log(Level.SEVERE, "*************DEADLOCK DETECTED!*************");

            for (long threadId : threadIds) {
                LOGGER.log(Level.SEVERE, "Thread ID: {0} {1}", new Object[]{threadId, threadMXBean.getThreadInfo(threadId).getThreadName()});
                LOGGER.log(Level.SEVERE, "{0} {1}", new Object[]{threadMXBean.getThreadInfo(threadId).getLockName(), threadMXBean.getThreadInfo(threadId).getLockInfo().getClassName()});
            }

            Helpers.mostrarMensajeError(null, Translator.translate("error.fatal_deadlock"));
            System.exit(1);
        }
    }

    public static String[] runProcess(String[] command) {
        Process process = null;
        try {
            ProcessBuilder processbuilder = new ProcessBuilder(command);
            // NO redirectErrorStream(true) — that breaks callers that parse output[1]
            // expecting clean binary stdout. Instead, stderr is drained on a parallel
            // daemon thread to avoid the pipe-full hang without polluting stdout.
            process = processbuilder.start();

            long pid = process.pid();

            // Stderr drain thread (daemon) — avoids the block when the binary writes
            // more to stderr than the OS pipe buffer holds.
            final Process pRef = process;
            Thread stderrDrainer = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(
                        new InputStreamReader(pRef.getErrorStream()))) {
                    while (err.readLine() != null) {
                        // discard
                    }
                } catch (Exception ignored) {
                }
            }, "runProcess-stderr-drain");
            stderrDrainer.setDaemon(true);
            stderrDrainer.start();

            StringBuilder sb = new StringBuilder();

            // try-with-resources: the previous BufferedReader was never closed.
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;

                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            process.waitFor();

            return new String[]{String.valueOf(pid), sb.toString()};

        } catch (Exception ex) {
            // If the process started but failed afterwards (e.g. InterruptedException
            // in waitFor), destroy it to avoid a zombie.
            if (process != null && process.isAlive()) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

//Thanks -> https://stackoverflow.com/a/10245657
    public static class HandScrollListener extends MouseAdapter {

        private final Cursor defCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        private final Cursor hndCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        private final Point pp = new Point();
        private final JLabel image;
        private final Window parent;

        public HandScrollListener(JLabel image, Window parent) {
            this.image = image;
            this.parent = parent;
        }

        public void mouseDragged(final MouseEvent e) {
            JViewport vport = (JViewport) e.getSource();
            Point cp = e.getPoint();
            Point vp = vport.getViewPosition();
            vp.translate(pp.x - cp.x, pp.y - cp.y);
            image.scrollRectToVisible(new Rectangle(vp, vport.getSize()));
            pp.setLocation(cp);
        }

        public void mousePressed(MouseEvent e) {

            if (this.parent != null && SwingUtilities.isRightMouseButton(e)) {
                this.parent.setVisible(false);
            }

            image.setCursor(hndCursor);
            pp.setLocation(e.getPoint());
        }

        public void mouseReleased(MouseEvent e) {
            image.setCursor(defCursor);
            image.repaint();
        }
    }

    public static String downloadUpdater() throws IOException {

        HttpURLConnection con = null;

        String updater_path = null;

        try {

            URL url_api = new URL("https://github.com/tonikelope/coronapoker/raw/master/coronaupdater.jar");

            con = (HttpURLConnection) url_api.openConnection();

            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_WEB_BROWSER);

            con.setUseCaches(false);

            // Without timeouts, a stuck GitHub left this download thread blocked
            // forever (the user saw "preparing update" forever). Short connect;
            // generous read, since this is a real download (this doesn't abort a
            // slow-but-alive download, only a dead connection).
            con.setConnectTimeout(HTTP_TIMEOUT);
            con.setReadTimeout(60000);

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

    // Voice notes are persisted to disk so they replay by clicking their chat
    // line, but nothing ever purged VOICE_DIR: it grew without bound across
    // sessions. Drop notes older than the user-configured retention (audio
    // settings, default 90 days) at startup (best-effort); recent ones survive
    // so an in-game reopen can still replay the current session's notes. NOT
    // done on RESET_GAME for that exact reason.
    public static void purgeOldVoiceNotes() {

        int retention_days = AudioDeviceManager.getVoiceNoteRetentionDays();

        // "Keep forever": nothing to purge
        if (retention_days == AudioDeviceManager.VOICE_NOTE_RETENTION_KEEP_FOREVER) {
            return;
        }

        long cutoff = System.currentTimeMillis() - (long) retention_days * 24L * 60L * 60L * 1000L;

        // No FOLLOW_LINKS: never traverse a symlink out of VOICE_DIR and delete an
        // external .wav. The app never creates symlinks here, but this closes the hole.
        try (Stream<Path> notes = Files.walk(Paths.get(Init.VOICE_DIR))) {
            notes.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".wav"))
                    .filter(p -> p.toFile().lastModified() < cutoff)
                    .forEach(p -> {
                        // best-effort delete with exit-cleanup fallback (typically
                        // an AV still holding the handle), same as the gifsicle sweep.
                        try {
                            if (!p.toFile().delete()) {
                                p.toFile().deleteOnExit();
                            }
                        } catch (Exception ex) {
                            try {
                                p.toFile().deleteOnExit();
                            } catch (Exception ignored) {
                            }
                        }
                    });

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    // User-triggered wipe from the audio settings: drops EVERY stored note
    // regardless of retention. Returns the count actually removed (handles still
    // held by an AV are scheduled for exit-cleanup and not counted). Best-effort,
    // same delete/fallback policy as the retention purge above.
    public static int purgeAllVoiceNotes() {

        int[] deleted = {0};

        try (Stream<Path> notes = Files.walk(Paths.get(Init.VOICE_DIR))) {
            notes.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".wav"))
                    .forEach(p -> {
                        try {
                            if (p.toFile().delete()) {
                                deleted[0]++;
                            } else {
                                p.toFile().deleteOnExit();
                            }
                        } catch (Exception ex) {
                            try {
                                p.toFile().deleteOnExit();
                            } catch (Exception ignored) {
                            }
                        }
                    });

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return deleted[0];
    }

    public static void parkThreadMillis(long millis) {

        parkThreadNanos(millis * 1000000L);

    }

    public static void parkThreadMicros(long micros) {

        parkThreadNanos(micros * 1000L);

    }

    public static void parkThreadNanos(long nanos) {

        if (nanos > 0L) {
            long end = System.nanoTime() + nanos;

            while (System.nanoTime() < end && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(end - System.nanoTime());
            }
        }
    }

    public static void barraIndeterminada(JProgressBar barra) {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                cancelSmoothCountdownEDT(barra);
                barra.setMaximum(1);
                barra.setValue(1);
                barra.setIndeterminate(true);
            }
        });
    }

    public static void resetBarra(JProgressBar barra, int max) {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                cancelSmoothCountdownEDT(barra);
                barra.setIndeterminate(false);
                barra.setMinimum(0);
                barra.setMaximum(max);
                barra.setValue(max);
            }
        });
    }

    private static final String SMOOTH_TIMER_KEY = "coronapoker.smoothCountdown.timer";
    private static final int SMOOTH_TICK_MS = 50;

    /**
     * Smooth visual countdown for a JProgressBar: starts full and drains to 0
     * over {@code seconds}. Uses an ms scale (max = seconds * 1000) and a 50 ms
     * Timer based on a deadline-now diff (no cumulative drift). The Timer is
     * stashed on the bar via clientProperty, so a second call, or
     * resetBarra/barraIndeterminada, cancels it cleanly. seconds <= 0 leaves
     * the bar at 0 without starting a Timer.
     */
    public static void smoothCountdown(JProgressBar barra, int seconds) {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                cancelSmoothCountdownEDT(barra);
                barra.setIndeterminate(false);
                barra.setMinimum(0);
                if (seconds <= 0) {
                    barra.setMaximum(0);
                    barra.setValue(0);
                    return;
                }
                final int totalMs = seconds * 1000;
                barra.setMaximum(totalMs);
                barra.setValue(totalMs);
                // Issue#9: mutable deadline + lastTick to support pausing. While
                // GameFrame.timba_pausada is active, the logical counter
                // (response_counter in Local/RemotePlayer) stops decrementing, but this
                // visual bar kept draining because it runs off wall-clock time. On
                // unpause the bar was at 0 while response_counter still had tens of
                // seconds left -> visible desync. Push the deadline forward by the time
                // elapsed while paused, so the bar visually "freezes" and resumes
                // exactly where it left off.
                final long[] deadline = {System.currentTimeMillis() + totalMs};
                final long[] lastTick = {System.currentTimeMillis()};
                javax.swing.Timer t = new javax.swing.Timer(SMOOTH_TICK_MS, (java.awt.event.ActionEvent ae) -> {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastTick[0];
                    lastTick[0] = now;
                    GameFrame gf = GameFrame.getInstance();
                    if (gf != null && gf.isTimba_pausada()) {
                        deadline[0] += elapsed;
                        return;
                    }
                    long remaining = deadline[0] - now;
                    if (remaining <= 0) {
                        barra.setValue(0);
                        // Force a full repaint: the small-value->0 delta sometimes
                        // doesn't clear the last filled pixel and leaves a thin sliver.
                        barra.repaint();
                        javax.swing.Timer self = (javax.swing.Timer) barra.getClientProperty(SMOOTH_TIMER_KEY);
                        if (self != null) {
                            self.stop();
                        }
                        barra.putClientProperty(SMOOTH_TIMER_KEY, null);
                    } else {
                        barra.setValue((int) Math.min(remaining, totalMs));
                    }
                });
                t.setRepeats(true);
                t.setCoalesce(true);
                barra.putClientProperty(SMOOTH_TIMER_KEY, t);
                t.start();
            }
        });
    }

    private static void cancelSmoothCountdownEDT(JProgressBar barra) {
        Object prev = barra.getClientProperty(SMOOTH_TIMER_KEY);
        if (prev instanceof javax.swing.Timer) {
            ((javax.swing.Timer) prev).stop();
            barra.putClientProperty(SMOOTH_TIMER_KEY, null);
        }
    }

    public static String updateJarImgSrc(String html) {

        String msg = html;

        Pattern pattern = Pattern.compile("src='jar:file:[^!]+!([^']+)'");

        Matcher matcher = pattern.matcher(html);

        ArrayList<String> lista = new ArrayList<>();

        while (matcher.find()) {

            if (!lista.contains(matcher.group(0))) {

                msg = msg.replaceAll(Pattern.quote(matcher.group(0)), "src='" + Helpers.class
                        .getResource(matcher.group(1)).toExternalForm() + "'");

                lista.add(matcher.group(0));
            }
        }

        return msg;
    }

    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    public static long getUsedMemory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public static String getMemoryUsage() {
        return formatBytes(getUsedMemory()) + " / " + formatBytes(getMaxMemory());
    }

    public static String formatBytes(Long bytes) {

        String[] units = {"B", "KB", "MB", "GB", "TB"};

        bytes = Math.max(bytes, 0L);

        int pow = Math.min((int) ((bytes > 0L ? Math.log(bytes) : 0) / Math.log(1024)), units.length - 1);

        Double bytes_double = (double) bytes / (1L << (10 * pow));

        DecimalFormat df = new DecimalFormat("#.##");

        return df.format(bytes_double) + ' ' + units[pow];
    }

    public static int getGIFLength(URL url) throws IOException, ImageProcessingException {

        // try-with-resources over the URL's InputStream: ImageMetadataReader does NOT
        // close the stream it's given. Each call (one per chat GIF or allin animation)
        // was leaking the handle until GC.
        Metadata metadata;
        try (InputStream s = openCooperativeUrlStream(url)) {
            metadata = ImageMetadataReader.readMetadata(s);
        }
        List<GifControlDirectory> gifControlDirectories
                = (List<GifControlDirectory>) metadata.getDirectoriesOfType(GifControlDirectory.class
                );

        int timeLength = 0;
        if (gifControlDirectories.size() == 1) { // Do not read delay of static GIF files with single frame.
        } else if (gifControlDirectories.size() >= 1) {
            for (GifControlDirectory gifControlDirectory : gifControlDirectories) {
                try {
                    if (gifControlDirectory.hasTagName(GifControlDirectory.TAG_DELAY)) {
                        timeLength += Math.max(gifControlDirectory.getInt(GifControlDirectory.TAG_DELAY), MIN_GIF_FRAME_DELAY);
                    }
                } catch (MetadataException e) {
                    LOGGER.log(Level.SEVERE, null, e);
                }
            }
            // Unit of time is 10 milliseconds in GIF.
            timeLength *= 10;
        }
        return timeLength;

    }

    public static int getGIFFramesCount(URL url) throws IOException, ImageProcessingException {

        Metadata metadata;
        try (InputStream s = openCooperativeUrlStream(url)) {
            metadata = ImageMetadataReader.readMetadata(s);
        }

        List<GifControlDirectory> gifControlDirectories
                = (List<GifControlDirectory>) metadata.getDirectoriesOfType(GifControlDirectory.class
                );

        return gifControlDirectories.size();
    }

    public static boolean isImageGIF(URL url) {

        try (InputStream stream = openCooperativeUrlStream(url); ImageInputStream iis = ImageIO.createImageInputStream(stream)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            while (readers.hasNext()) {

                ImageReader read = readers.next();

                try {
                    if ("gif".equals(read.getFormatName().toLowerCase())) {
                        return true;
                    }
                } finally {
                    // ImageIO contract: every ImageReader obtained via getImageReaders
                    // MUST dispose() to free native buffers. This function is called for
                    // every image message in chat (hundreds per session).
                    read.dispose();
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return false;
    }

    private static InputStream openCooperativeUrlStream(URL url) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("URL read cancelled during table teardown");
        }

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(COOPERATIVE_SESSION_IO_TIMEOUT_MS);
        connection.setReadTimeout(COOPERATIVE_SESSION_IO_TIMEOUT_MS);
        return connection.getInputStream();
    }

    public static void updateCoronaDialogsFont() {
        // JOptionPane's font (message and buttons) follows the dialog zoom: base 14 x
        // factor. Re-applied when the zoom changes in Settings (AppearanceSettingsPanel)
        // so simple messages (info/error/confirm) shrink/grow with the rest of the dialogs.
        int sz = Math.round(14 * DIALOG_ZOOM);
        UIManager.put("OptionPane.messageFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), sz));
        UIManager.put("OptionPane.buttonFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), sz));
    }

    public static void setCoronaLocale() {

        Locale locale = new Locale(GameFrame.LANGUAGE, GameFrame.LANGUAGE.toUpperCase());
        Locale.setDefault(locale);
        JOptionPane.setDefaultLocale(locale);

        UIManager.put("OptionPane.cancelButtonText", Translator.translate("ui.option_pane.cancel"));
        UIManager.put("OptionPane.noButtonText", Translator.translate("ui.option_pane.no"));
        UIManager.put("OptionPane.okButtonText", Translator.translate("ui.option_pane.ok"));
        UIManager.put("OptionPane.yesButtonText", Translator.translate("ui.option_pane.yes"));

    }

    public static void windowAutoFitToRemoveHScrollBar(Window window, JScrollBar hbar, int max_width) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (hbar.isVisible()) {

                    // The bar's model already knows how much content is hidden
                    // horizontally: maximum (total width) - visibleAmount (visible
                    // width). That's exactly how much to widen the window for the bar to
                    // disappear, in a single resize (no stepwise repack growth).
                    int deficit = hbar.getMaximum() - hbar.getVisibleAmount();

                    if (deficit > 0) {

                        int new_width = Math.min(window.getWidth() + deficit, max_width);

                        if (new_width > window.getWidth()) {
                            window.setSize(new_width, window.getHeight());
                            window.setPreferredSize(window.getSize());
                            window.pack();
                        }
                    }
                }

                window.revalidate();
                window.repaint();

            }
        });

    }

    /**
     * Clamps a window's location so it lies entirely within the usable screen
     * bounds (the work area, which excludes the taskbar). Meant to be called
     * once the window already has its final size and location but before it is
     * shown: it guarantees the window never spills off-screen nor under the
     * taskbar on low resolutions, regardless of where the owner it was centered
     * on happens to be.
     */
    public static void clampWindowToUsableBounds(Window window) {

        GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                Rectangle usable = getUsableBoundsForWindow(window);

                int x = window.getX();
                int y = window.getY();

                if (x + window.getWidth() > usable.x + usable.width) {
                    x = usable.x + usable.width - window.getWidth();
                }

                if (y + window.getHeight() > usable.y + usable.height) {
                    y = usable.y + usable.height - window.getHeight();
                }

                x = Math.max(x, usable.x);
                y = Math.max(y, usable.y);

                if (x != window.getX() || y != window.getY()) {
                    window.setLocation(x, y);
                }
            }
        });
    }

    /**
     * Usable bounds (work area, taskbar excluded) of the monitor the window
     * currently sits on, chosen as the screen device whose bounds overlap the
     * window the most. GraphicsEnvironment.getMaximumWindowBounds() only ever
     * reports the PRIMARY display's work area, so a dialog centered over a
     * parent that lives on a secondary monitor would be dragged back onto the
     * primary screen by the clamp; resolving the device per-window keeps it on
     * the monitor it was centered on. Falls back to the primary work area when
     * the window overlaps no screen at all.
     */
    private static java.awt.GraphicsConfiguration graphicsConfigForWindow(Window window) {

        Rectangle window_bounds = window.getBounds();

        java.awt.GraphicsConfiguration best = null;
        long best_area = 0;

        for (java.awt.GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {

            java.awt.GraphicsConfiguration config = device.getDefaultConfiguration();

            Rectangle intersection = config.getBounds().intersection(window_bounds);

            long area = intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;

            if (area > best_area) {
                best_area = area;
                best = config;
            }
        }

        return best;
    }

    // Usable area (taskbar excluded) of the given monitor; the primary's work area if null.
    private static Rectangle usableBoundsOfConfig(java.awt.GraphicsConfiguration gc) {
        if (gc == null) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    private static Rectangle getUsableBoundsForWindow(Window window) {
        return usableBoundsOfConfig(graphicsConfigForWindow(window));
    }

    public static void setLocationContainerRelativeTo(Container reference, Container current) {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                int reference_center_x = (int) (reference.getLocation().getX() + Math.round(reference.getWidth() / 2));
                int reference_center_y = (int) (reference.getLocation().getY() + Math.round(reference.getHeight() / 2));

                current.setLocation(new Point(reference_center_x - Math.round(current.getWidth() / 2), reference_center_y - Math.round(current.getHeight() / 2)));
            }
        });
    }

    public static String escapeHTML(String str) {
        return str.codePoints().mapToObj(c -> c > 127 || "\"'<>&".indexOf(c) != -1
                ? "&#" + c + ";" : new String(Character.toChars(c)))
                .collect(Collectors.joining());
    }

    public static void setScaledIconLabel(JLabel label, String path, int width, int height) {
        // Image.getScaledInstance(0, 0, ...) throws IllegalArgumentException. When the
        // caller tries to scale before the container has a size (typical in zoomIcons
        // fired from a re-layout that hasn't been computed yet), the dimensions arrive
        // as 0; without this guard the exception bubbles up on the EDT and ends up as
        // SEVERE in JUL without the caller ever knowing.
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    label.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(new File(path).toURL()) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));
                    label.putClientProperty("cp_scaled_icon", Boolean.TRUE);

                } catch (MalformedURLException ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public static ImageIcon scaleIcon(String path, int width, int height) throws MalformedURLException {

        return new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(new File(path).toURL()) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

    }

    public static ImageIcon scaleIcon(URL path, int width, int height) throws MalformedURLException {

        return new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(path) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

    }

    // Translucent copy of an icon (global alpha 0..1), used to show the local player's
    // position chip at reduced opacity (intermediate toggle state).
    public static ImageIcon translucentIcon(ImageIcon src, float alpha) {
        if (src == null) {
            return null;
        }
        int w = src.getIconWidth(), h = src.getIconHeight();
        if (w <= 0 || h <= 0) {
            return src;
        }
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        g.drawImage(src.getImage(), 0, 0, null);
        g.dispose();
        return new ImageIcon(bi);
    }

    public static void setScaledIconLabel(JLabel label, URL path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                label.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(path) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));
                label.putClientProperty("cp_scaled_icon", Boolean.TRUE);
            }
        });
    }

    // Sets an icon the caller has ALREADY scaled (typically cached, to avoid repeating
    // decode + smooth scaling on every change), tagging it like the setScaled* helpers
    // so scaleIcons doesn't scale it again.
    public static void setPreScaledIconLabel(JLabel label, ImageIcon icon) {
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                label.setIcon(icon);
                label.putClientProperty("cp_scaled_icon", Boolean.TRUE);
            }
        });
    }

    // Scales an icon and recolors it to WHITE while preserving alpha (the drawing's
    // silhouette). For icons meant for a light background (e.g. the menu gear) that get
    // shown over the dark felt, like the speaker icon.
    public static void setScaledWhiteIconLabel(JLabel label, URL path, int width, int height) {
        setScaledTintedIconLabel(label, path, width, height, java.awt.Color.WHITE);
    }

    // Same as above but recolored to BLACK. Used for the settings gear on the start
    // screen and waiting room, where the speaker icon is black.
    public static void setScaledBlackIconLabel(JLabel label, URL path, int width, int height) {
        setScaledTintedIconLabel(label, path, width, height, java.awt.Color.BLACK);
    }

    // Scales the icon and recolors its silhouette (the opaque areas) to the given
    // color, preserving transparency via SRC_ATOP.
    private static void setScaledTintedIconLabel(JLabel label, URL path, int width, int height, java.awt.Color tint) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                Image src = new ImageIcon(path).getImage();
                BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, width, height, null);
                // SRC_ATOP paints the color ONLY where there was already opacity:
                // recolors the icon's silhouette without touching transparent areas.
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(tint);
                g.fillRect(0, 0, width, height);
                g.dispose();
                label.setIcon(new ImageIcon(bi));
                label.putClientProperty("cp_scaled_icon", Boolean.TRUE);
            }
        });
    }

    public static void setScaledRoundedIconLabel(JLabel label, String path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    label.setIcon(new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(new File(path).toURL()) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)).getImage(), 20)));
                    label.putClientProperty("cp_scaled_icon", Boolean.TRUE);

                } catch (MalformedURLException ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public static void setScaledRoundedIconLabel(JLabel label, URL path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                label.setIcon(new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(path) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)).getImage(), 20)));
                label.putClientProperty("cp_scaled_icon", Boolean.TRUE);
            }
        });
    }

    public static void setScaledIconButton(JButton button, String path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                try {
                    button.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(new File(path).toURL()) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));
                    button.putClientProperty("cp_scaled_icon", Boolean.TRUE);

                } catch (MalformedURLException ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public static void setScaledIconButton(JButton button, URL path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                button.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(path) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));
                button.putClientProperty("cp_scaled_icon", Boolean.TRUE);
            }
        });
    }

    public static String getLocalTimeString() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        return LocalDateTime.now().format(formatter);
    }

    //Thanks -> https://stackoverflow.com/a/46613809
    /**
     * Reads given resource file as a string.
     *
     * @param fileName path to the resource file
     * @return the file's contents
     * @throws IOException if read fails for any reason
     */
    public static List<String> getResourceTextFileAsList(String fileName) throws IOException {

        try (InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream(fileName)) {

            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {

                return reader.lines().collect(Collectors.toList());
            }
        }
    }

    public static void SHUTDOWN_THREAD_POOL() {

        THREAD_POOL.shutdown();

        THREAD_POOL.shutdownNow();
        boolean workersTerminated = awaitTermination(THREAD_POOL, "worker");
        if (!workersTerminated) {
            LOGGER.log(Level.SEVERE,
                    "Worker pool remains active; a guarded table session cannot be replaced yet.");
        }

        if (LOG_POOL != null) {
            LOG_POOL.shutdown();
            LOG_POOL.shutdownNow();
            awaitTermination(LOG_POOL, "log");
        }

        LOGGER.log(Level.INFO, "Thread pool shutdown — cooperative cancellation notices that follow are expected.");
    }

    private static boolean awaitTermination(ExecutorService executor, String name) {
        try {
            boolean terminated = executor.awaitTermination(THREAD_POOL_SHUTDOWN_TIMEOUT,
                    java.util.concurrent.TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.log(Level.SEVERE, "{0} executor still active after {1}s",
                        new Object[]{name, THREAD_POOL_SHUTDOWN_TIMEOUT});
            }
            return terminated;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, name + " executor termination wait interrupted", ex);
            return false;
        }
    }

    public static void CREATE_THREAD_POOL() {
        ThreadPoolExecutor previous = THREAD_POOL;
        if (previous != null && previous.isShutdown() && !previous.isTerminated()) {
            throw new IllegalStateException(
                    "Cannot create a new table executor while the previous one is still active");
        }
        THREAD_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        LOG_POOL = Executors.newSingleThreadExecutor();

        LOGGER.log(Level.INFO, "New thread pool created");
    }

    public static boolean UPnPClose(int port) {

        boolean ret = false;

        if (UPnP.isMappedTCP(port)) {

            if ((ret = UPnP.closePortTCP(port))) {

                Logger.getLogger(Helpers.class
                        .getName()).log(Level.INFO, "UPnP unmap OK for TCP port {0}", String.valueOf(port));

            } else {

                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, "UPnP unmap FAILED for TCP port {0}", String.valueOf(port));
            }
        }

        return ret;
    }

    public static boolean UPnPOpen(int port) {

        boolean upnp;

        if ((upnp = UPnP.isUPnPAvailable())) {

            if (!UPnP.isMappedTCP(port)) {
                if (UPnP.openPortTCP(port)) {

                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.INFO, "UPnP map OK for TCP port {0}", String.valueOf(port));

                } else {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, "UPnP map FAILED for TCP port {0}", String.valueOf(port));
                    upnp = false;

                }

            } else {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.WARNING, "UPnP port already mapped: TCP {0}", String.valueOf(port));

            }

        } else {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.WARNING, "UPnP not available");
        }

        return upnp;
    }

    public synchronized static Connection getSQLITE() throws SQLException {

        if (SQLITE != null && !SQLITE.isClosed()) {

            return SQLITE;

        } else {

            try {

                SQLiteConfig config = new SQLiteConfig();

                config.enforceForeignKeys(true);
                // Classic journal mode (rollback journal) — the original. WAL was tried
                // but introduced 2 files visible in the config dir (.db-wal + .db-shm)
                // that confused the author with no practical benefit (CoronaPoker is
                // single-user single-instance, no concurrent readers to benefit from WAL).
                //
                // NOTE: journal_mode is stored PERSISTENTLY in the DB header. If the DB
                // was already in WAL, simply omitting setJournalMode does NOT change it —
                // it stays in WAL. Hence the explicit DELETE: the next time the
                // connection opens, SQLite migrates the DB to the classic journal and
                // automatically removes the .db-wal and .db-shm files.
                config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.DELETE);
                // synchronous=FULL: fsync the rollback journal before touching the DB,
                // and the DB before deleting the journal. This is SQLite's default, but
                // set explicitly so it doesn't depend on the driver/version default: it
                // guarantees a power cut can't corrupt the file (at most the last
                // uncommitted transaction is lost, never the whole DB).
                config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.FULL);
                // 50 MB cache (negative = KB). The default is ~2MB, not enough for
                // StatsDialog's JOINs once there are thousands of hands.
                config.setCacheSize(-50_000);
                // Defender / antivirus takes a momentary share-lock on the .db during
                // COMMIT. Without busy_timeout, SQLITE_BUSY is returned instantly and the
                // INSERT/UPDATE is lost (Crupier's generic catch logs SEVERE but doesn't
                // retry). 5s covers transient share-locks without hanging the UI.
                config.setBusyTimeout(5000);

                SQLITE = DriverManager.getConnection("jdbc:sqlite:" + SQL_FILE, config.toProperties());

                return SQLITE;

            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, "Could not open the SQLite connection", ex);
            }

            return null;
        }
    }

    public synchronized static void closeSQLITE() {

        if (SQLITE != null) {
            try {
                if (!SQLITE.isClosed()) {
                    SQLITE.close();

                }

            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }

            SQLITE = null;
        }
    }

    public static void saveHandFossil(int gameId, String payload) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            try (java.sql.PreparedStatement st = getSQLITE().prepareStatement("INSERT OR REPLACE INTO hand_state(id_game, payload) VALUES (?, ?)")) {
                st.setQueryTimeout(30);
                st.setInt(1, gameId);
                st.setString(2, payload);
                st.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, "Failed to save hand fossil", ex);
            }
        }
    }

    public static String loadHandFossil(int gameId) {
        if (gameId <= 0) {
            return null;
        }
        synchronized (GameFrame.SQL_LOCK) {
            try (java.sql.PreparedStatement st = getSQLITE().prepareStatement("SELECT payload FROM hand_state WHERE id_game=?")) {
                st.setQueryTimeout(30);
                st.setInt(1, gameId);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("payload");
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, "Failed to load hand fossil", ex);
            }
            return null;
        }
    }

    public static void deleteHandFossil(int gameId) {
        if (gameId <= 0) {
            return;
        }
        synchronized (GameFrame.SQL_LOCK) {
            try (java.sql.PreparedStatement st = getSQLITE().prepareStatement("DELETE FROM hand_state WHERE id_game=?")) {
                st.setQueryTimeout(30);
                st.setInt(1, gameId);
                st.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, "Failed to delete hand fossil", ex);
            }
        }
    }

    /**
     * @return true if the database ends up open with an up-to-date schema.
     *
     * Returns false (never propagates) on any failure, including ones that
     * aren't an Exception: if the SQLite native library can't be loaded (locked
     * in the temp directory, or native access denied by the JVM) what comes out
     * of here is an Error, which used to escape the catch and kill the startup
     * thread, leaving the splash frozen with no explanation.
     */
    public static boolean initSQLITE() {
        try {
            Class.forName("org.sqlite.JDBC");

            // Integrity check + backup/restore before any game thread touches the
            // DB. If healthy, refreshes the .autobak snapshot; if corrupt, moves the
            // file aside and restores the last backup (or starts clean).
            verifyAndBackupDatabase();

            try (Statement statement = getSQLITE().createStatement()) {
                statement.setQueryTimeout(30);  // set timeout to 30 sec.
                statement.execute("CREATE TABLE IF NOT EXISTS game(id INTEGER PRIMARY KEY, start INTEGER, end INTEGER, play_time INTEGER, server TEXT, players TEXT, buyin INTEGER, sb REAL, blinds_time INTEGER, rebuy INTEGER, last_deck TEXT, blinds_time_type INTEGER, ugi TEXT, local INTEGER NOT NULL DEFAULT 0, recover_settings TEXT, private INTEGER NOT NULL DEFAULT 0, imported INTEGER NOT NULL DEFAULT 0, imported_from TEXT)");
                statement.execute("CREATE TABLE IF NOT EXISTS hand(id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT, start INTEGER, end INTEGER, com_cards TEXT, preflop_players TEXT, flop_players TEXT, turn_players TEXT, river_players TEXT, pot REAL, hand_id_b64 TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS action(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, counter INTEGER, round INTEGER, action INTEGER, bet REAL, conta_raise INTEGER, response_time INTEGER, record_b64 TEXT, sig_b64 TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS showdown(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, hole_cards TEXT, hand_cards TEXT, hand_val INTEGER, winner INTEGER, pay REAL, profit REAL, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS balance(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, rebuy_count INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS showcards(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, parguela INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS permutationkey(id INTEGER PRIMARY KEY, hash TEXT, key TEXT)");
                statement.execute("CREATE TABLE IF NOT EXISTS hand_state(id_game INTEGER PRIMARY KEY, payload TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS known_identities(nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, sessions_count INTEGER NOT NULL DEFAULT 0, verified_oob INTEGER NOT NULL DEFAULT 0)");
                // Secondary indexes on the FKs that StatsDialog uses in self-joins.
                // SQLite does NOT auto-index FKs. Without these, queries like
                // performance/raisesByRound/balance do a full table scan: O(rows_action *
                // rows_hand). With thousands of hands, seconds -> milliseconds.
                statement.execute("CREATE INDEX IF NOT EXISTS idx_hand_game ON hand(id_game)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_action_hand ON action(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_showdown_hand ON showdown(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_balance_hand ON balance(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_showcards_hand ON showcards(id_hand)");
                // GLOBAL stats (all games): performance/raisesByRound aggregate over
                // action/showdown filtering by round/action/winner and grouping by player
                // WITHOUT filtering by game, so the FK (id_hand) indexes above don't apply
                // and the query falls back to a full scan of action/showdown. With many
                // games (and old games with many hands) that blocked StatsDialog (a
                // single-thread executor getting stuck). These composite indexes cover
                // those WHERE/GROUP BY/COUNT(DISTINCT id_hand).
                statement.execute("CREATE INDEX IF NOT EXISTS idx_action_round_action_player ON action(round, action, player, id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_action_player_hand ON action(player, id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_showdown_player_winner ON showdown(player, winner, id_hand)");
                // Consensus: forensic log of hands whose end-of-hand consensus
                // did not check out unanimously. The hand is paid out regardless — this table is
                // informational only (spec §6.3 / §6.4). receipts BLOB holds the concatenation of
                // every receipt this peer collected for that hand (each receipt = HAND_ID ||
                // H_final || sig, 16+32+64 = 112 bytes); local_h is this peer's own H_final at
                // dispute time.
                statement.execute("CREATE TABLE IF NOT EXISTS disputed_hands(id INTEGER PRIMARY KEY, id_hand INTEGER NOT NULL, timestamp INTEGER NOT NULL, receipts BLOB NOT NULL, local_h BLOB NOT NULL, reason TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                // Recovery requires one unambiguous snapshot row per player and hand.
                HandCreateTransaction.ensureUniqueBalanceRows(getSQLITE());

            }

            return true;

        } catch (Throwable ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, "SQLite initialization failed", ex);
        }

        return false;
    }

    /**
     * Startup integrity gate. Runs single-threaded before any game thread
     * touches the DB, so it is free of the Connection-sharing / lock-ordering
     * hazards that a close-time backup would face.
     *
     * <ul>
     * <li>If the main DB passes {@code PRAGMA quick_check}, refresh the
     * {@code .autobak} snapshot — it then always holds a state already proven
     * healthy.</li>
     * <li>If it fails (corrupt / unreadable), set the corrupt file aside (never
     * deleted, for forensics) and restore the last {@code .autobak}. If there
     * is no usable backup, start fresh: the {@code CREATE TABLE IF NOT EXISTS}
     * block rebuilds an empty schema.</li>
     * </ul>
     *
     * Doing it at launch (not at close) also captures data after an unclean
     * previous shutdown and never overwrites a good backup with a corrupt
     * source.
     */
    private static void verifyAndBackupDatabase() {
        if (SQL_FILE == null || SQL_FILE.isBlank()) {
            // Defense in depth: never operate on a null/empty path (avoid generating
            // junk "null"/".autobak" files). Init guarantees this doesn't happen, but if
            // some future path left SQL_FILE unset, bail out without touching anything.
            LOGGER.log(Level.SEVERE, "SQL_FILE is not set; skipping integrity check and backup");
            return;
        }

        if (isSQLiteHealthy()) {
            backupSQLite();
            return;
        }

        LOGGER.log(Level.WARNING, "coronapoker.db failed its integrity check; attempting recovery from backup");

        java.nio.file.Path db = java.nio.file.Paths.get(SQL_FILE);
        java.nio.file.Path bak = java.nio.file.Paths.get(SQL_FILE + ".autobak");

        try {
            if (java.nio.file.Files.exists(db)) {
                java.nio.file.Path aside = java.nio.file.Paths.get(SQL_FILE + ".corrupt_" + System.currentTimeMillis());
                java.nio.file.Files.move(db, aside, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.log(Level.WARNING, "Corrupt DB set aside as {0}", aside);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not set the corrupt DB aside", ex);
        }

        if (java.nio.file.Files.exists(bak)) {
            try {
                java.nio.file.Files.copy(bak, db, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (isSQLiteHealthy()) {
                    LOGGER.log(Level.INFO, "Restored coronapoker.db from {0}", bak);
                    return;
                }
                LOGGER.log(Level.SEVERE, "Backup {0} also failed its integrity check; starting fresh", bak);
                if (java.nio.file.Files.exists(db)) {
                    java.nio.file.Files.move(db, java.nio.file.Paths.get(SQL_FILE + ".corrupt_bak_" + System.currentTimeMillis()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not restore the DB from backup", ex);
            }
        }

        // No usable backup: the CREATE TABLE IF NOT EXISTS block rebuilds a fresh schema.
        LOGGER.log(Level.WARNING, "Starting with a fresh stats database");
    }

    /**
     * @return true only if {@code PRAGMA quick_check} reports "ok". A
     * SQLException (e.g. "database disk image is malformed") or any other
     * result is treated as unhealthy. Opens the connection lazily through
     * {@link #getSQLITE()}.
     */
    private static boolean isSQLiteHealthy() {
        // Self-contained probe on its OWN short-lived connection, not the shared
        // getSQLITE one. An open/read failure here is EXPECTED when the file is
        // corrupt and is the signal that triggers recovery — routing it through
        // getSQLITE would log it as a SEVERE "could not open" stack trace and look
        // like a crash. The connection is closed (try-with-resources) before we
        // return, leaving the file unlocked for the move/restore that follows.
        try (java.sql.Connection conn = DriverManager.getConnection("jdbc:sqlite:" + SQL_FILE); Statement st = conn.createStatement()) {
            st.setQueryTimeout(60);
            try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                return rs.next() && "ok".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "SQLite integrity probe failed, treating DB as corrupt: {0}", ex.getMessage());
            return false;
        }
    }

    /**
     * Copies the main DB over the {@code .autobak} snapshot. Called only after
     * the DB has just passed its integrity check and the connection is closed,
     * so the backup is taken from a file at rest and is never poisoned by a
     * corrupt source.
     */
    private static void backupSQLite() {
        java.nio.file.Path tmp = java.nio.file.Paths.get(SQL_FILE + ".autobak.tmp");
        try {
            java.nio.file.Path db = java.nio.file.Paths.get(SQL_FILE);
            if (java.nio.file.Files.exists(db)) {
                // The copy goes to a temp file first and is then moved into place.
                // Copying directly over the backup ERASES it before writing starts: a
                // power cut mid-write would destroy the only copy there was — exactly
                // the copy you fall back to when the database breaks.
                java.nio.file.Path bak = java.nio.file.Paths.get(SQL_FILE + ".autobak");
                java.nio.file.Files.copy(db, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    java.nio.file.Files.move(tmp, bak,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    java.nio.file.Files.move(tmp, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not write the SQLite backup", ex);
            try {
                java.nio.file.Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    public static byte[] byteArrayConcat(byte[] a, byte[] b) {
        int lenA = a.length;
        int lenB = b.length;
        byte[] c = Arrays.copyOf(a, lenA + lenB);
        System.arraycopy(b, 0, c, lenA, lenB);
        return c;
    }

    public static void SQLITEVAC() {

        // The shared SQLite Connection is not thread-safe and a StatsSync import may hold an
        // open transaction on it from the network thread. Serialize under SQL_LOCK like every
        // other DB access (VACUUM in particular cannot run inside another statement's open
        // transaction on the same connection). Runs off the EDT — from finTransmision's teardown
        // thread AND from StatsDialog's "stats-db" executor (purge / delete-all / delete-imported) —
        // so taking SQL_LOCK here respects the "never request SQL_LOCK from the EDT" invariant.
        synchronized (GameFrame.SQL_LOCK) {
            try (Statement statement = Helpers.getSQLITE().createStatement()) {

                // VACUUM rewrites the ENTIRE database file, so its cost grows
                // linearly with the DB size (measured ~16 ms/MB: ~38 ms at 2 MB,
                // ~274 ms at 16 MB). The game history (game/hand/action/...) is only
                // ever purged manually from StatsDialog, so the file grows
                // monotonically across sessions. Running a full VACUUM on every game
                // end therefore made the return to the main screen progressively
                // slower while, in the common case (no rows deleted since the last
                // run), reclaiming nothing at all — pure wasted I/O on the exit path.
                //
                // Only compact when there is non-trivial free space to reclaim (i.e.
                // after a manual purge actually freed pages). Otherwise this is a
                // cheap no-op and the return to the lobby stays instant no matter how
                // large the history has grown.
                long free_pages = 0;
                long total_pages = 0;

                try (ResultSet rs = statement.executeQuery("PRAGMA freelist_count")) {
                    if (rs.next()) {
                        free_pages = rs.getLong(1);
                    }
                }

                try (ResultSet rs = statement.executeQuery("PRAGMA page_count")) {
                    if (rs.next()) {
                        total_pages = rs.getLong(1);
                    }
                }

                // Worth a full rewrite only when the free space is both absolutely
                // (> 256 free pages — a few hundred KB to ~1 MB depending on the
                // file's page_size) and relatively (>= 10% of the file) significant.
                // The relative gate is what matters; the page floor just avoids churn
                // on a small DB.
                boolean worth_compacting = free_pages > 256 && total_pages > 0
                        && (free_pages * 100L) / total_pages >= 10;

                if (!worth_compacting) {
                    LOGGER.log(Level.INFO, "SQLite VACUUM skipped ({0}/{1} free pages — nothing significant to reclaim).",
                            new Object[]{free_pages, total_pages});
                    return;
                }

                statement.execute("VACUUM");

                LOGGER.log(Level.INFO, "SQLite VACUUM done (reclaimed {0}/{1} pages).",
                        new Object[]{free_pages, total_pages});

            } catch (SQLException ex) {
                String msg = ex.getMessage();
                // VACUUM is opportunistic maintenance and benignly fails when
                // other SQL statements are still in progress (typical during a
                // busy game exit). That specific case is INFO. ANY other SQL
                // error (disk full, permission denied, corruption, etc.) is a
                // real problem and stays SEVERE.
                if (msg != null && msg.contains("SQL statements in progress")) {
                    LOGGER.log(Level.INFO, "SQLite VACUUM skipped (SQL statements in progress, will retry next session).");
                } else {
                    LOGGER.log(Level.SEVERE, "SQLite VACUUM failed", ex);
                }
            }
        } // synchronized (GameFrame.SQL_LOCK)

    }

    public static class maxLenghtFilter extends DocumentFilter {

        private int max_lenght;
        private JTextField textfield;

        public maxLenghtFilter(JTextField field, int max_lenght) {
            super();

            this.textfield = field;
            this.max_lenght = max_lenght;
        }

        @Override
        public void replace(DocumentFilter.FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException {

            if ((max_lenght == -1 || (textfield.getSelectedText() == null && (fb.getDocument().getLength() + str.length()) <= max_lenght) || (textfield.getSelectedText() != null && str.length() <= max_lenght))) {
                super.replace(fb, offs, length, str, a);
            }
        }
    }

    public static String processDetails(ProcessHandle process) {
        return String.format("%8d %8s %10s %26s %-40s\n", process.pid(), processText(process.parent().map(ProcessHandle::pid)), processText(process.info().user()), processText(process.info().startInstant()), processText(process.info().commandLine()));
    }

    public static String processText(Optional<?> optional) {
        return optional.map(Object::toString).orElse("-");

    }

    public static class numericFilter extends DocumentFilter {

        private int max_lenght;
        private Pattern regEx = Pattern.compile("[0-9]+");
        private JTextField textfield;

        public numericFilter(JTextField field, int max_lenght) {
            super();
            this.textfield = field;
            this.max_lenght = max_lenght;
        }

        @Override
        public void replace(DocumentFilter.FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException {

            Matcher matcher = regEx.matcher(str);

            if ((max_lenght == -1 || (textfield.getSelectedText() == null && (fb.getDocument().getLength() + str.length()) <= max_lenght) || (textfield.getSelectedText() != null && str.length() <= max_lenght)) && matcher.matches()) {
                super.replace(fb, offs, length, str, a);
            }
        }
    }

    // Input filter for numeric spinners: validates the RESULTING TEXT (not just what
    // was inserted), so it guarantees "digits only" (integer) or "digits + one comma or
    // dot" (decimal). Letters and a second comma are ignored WHILE TYPING (this is not
    // Swing's default "revert on commit"). Clamping to [min,max] and rounding are done
    // by the SpinnerNumberModel/NumberFormatter on commit.
    public static class numericInputFilter extends DocumentFilter {

        private final boolean allow_decimals;

        public numericInputFilter(boolean allow_decimals) {
            super();
            this.allow_decimals = allow_decimals;
        }

        private boolean valid(String text) {
            return text.isEmpty() || text.matches(allow_decimals ? "\\d*[.,]?\\d*" : "\\d*");
        }

        private String resulting(FilterBypass fb, int offset, int length, String str) throws BadLocationException {
            javax.swing.text.Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder(doc.getText(0, doc.getLength()));
            sb.replace(offset, offset + length, str == null ? "" : str);
            return sb.toString();
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String str, AttributeSet attr) throws BadLocationException {
            if (valid(resulting(fb, offset, 0, str))) {
                super.insertString(fb, offset, str, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String str, AttributeSet attr) throws BadLocationException {
            if (valid(resulting(fb, offset, length, str))) {
                super.replace(fb, offset, length, str, attr);
            }
        }
    }

    // Makes a numeric JSpinner keyboard-editable while ignoring letters: installs
    // numericInputFilter on the editor's textfield. ALWAYS call after setModel (which
    // recreates the editor) so the filter and the formatter's min/max limits stay fresh
    // against the current model. allow_decimals=true for money spinners with decimals
    // (accepts one comma).
    public static void makeNumericSpinnerEditable(javax.swing.JSpinner spinner, boolean allow_decimals) {
        if (spinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor) {
            JTextField tf = ((javax.swing.JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            tf.setEditable(true);
            ((javax.swing.text.AbstractDocument) tf.getDocument()).setDocumentFilter(new numericInputFilter(allow_decimals));
        }
    }

    public static String encryptString(String cadena, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        byte[] iv = new byte[16];

        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        return encryptString(cadena, aes_key, iv, hmac_key);

    }

    public static String encryptString(String cadena, SecretKeySpec aes_key, byte[] iv, SecretKeySpec hmac_key) {

        if (cadena != null) {
            try {
                Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

                cifrado.init(Cipher.ENCRYPT_MODE, aes_key, new IvParameterSpec(iv));

                byte[] cmsg = cifrado.doFinal(cadena.getBytes("UTF-8"));

                byte[] full_msg;

                byte[] iv_cmsg = new byte[iv.length + cmsg.length];

                // System.arraycopy -> native memcpy; replaces the previous code's 4
                // byte-by-byte for loops. Every Crupier GAME command (dozens per hand)
                // and every MEGAPACKET (52*32 = 1664 bytes) went through here.
                System.arraycopy(iv, 0, iv_cmsg, 0, iv.length);
                System.arraycopy(cmsg, 0, iv_cmsg, iv.length, cmsg.length);

                if (hmac_key != null) {

                    full_msg = new byte[32 + iv.length + cmsg.length];

                    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                    sha256_HMAC.init(hmac_key);

                    byte[] hmac = sha256_HMAC.doFinal(iv_cmsg);

                    System.arraycopy(hmac, 0, full_msg, 0, hmac.length);
                    System.arraycopy(iv_cmsg, 0, full_msg, hmac.length, iv_cmsg.length);
                } else {
                    full_msg = iv_cmsg;
                }

                return Base64.getEncoder().encodeToString(full_msg);

            } catch (UnsupportedEncodingException | IllegalStateException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public static String decryptString(String cadena, SecretKeySpec aes_key, SecretKeySpec hmac_key) throws KeyException {

        if (cadena != null) {
            try {

                Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

                byte[] full_msg;

                try {
                    full_msg = Base64.getDecoder().decode(cadena);
                } catch (IllegalArgumentException bad_base64) {
                    // Undecodable body: this frame doesn't make it through the channel, same
                    // as a bad HMAC. Throw KeyException so the reader DISCARDS it and keeps
                    // going, not the RuntimeException, which would escape up to a return null
                    // (end of read).
                    throw new KeyException("Undecodable frame body");
                }

                byte[] hmac = new byte[32];

                byte[] iv = new byte[cifrado.getBlockSize()];

                byte[] cmsg;

                if (hmac_key != null) {

                    if (full_msg.length < hmac.length + iv.length) {
                        // Frame shorter than HMAC+IV: same policy, KeyException (discard the
                        // frame) instead of the NegativeArraySizeException from new byte[negative].
                        throw new KeyException("Frame shorter than HMAC and IV");
                    }

                    cmsg = new byte[full_msg.length - hmac.length - iv.length];

                    // System.arraycopy -> native memcpy; replaces the previous code's 5
                    // byte-by-byte for loops.
                    System.arraycopy(full_msg, 0, hmac, 0, hmac.length);
                    System.arraycopy(full_msg, hmac.length, iv, 0, iv.length);
                    System.arraycopy(full_msg, hmac.length + iv.length, cmsg, 0, cmsg.length);

                    byte[] iv_cmsg = new byte[iv.length + cmsg.length];

                    System.arraycopy(iv, 0, iv_cmsg, 0, iv.length);
                    System.arraycopy(cmsg, 0, iv_cmsg, iv.length, cmsg.length);

                    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                    sha256_HMAC.init(hmac_key);

                    byte[] current_hmac = sha256_HMAC.doFinal(iv_cmsg);

                    if (!MessageDigest.isEqual(hmac, current_hmac)) {
                        throw new KeyException("BAD HMAC or BAD KEY");
                    }
                } else {

                    if (full_msg.length < iv.length) {
                        throw new KeyException("Frame shorter than IV");
                    }

                    cmsg = new byte[full_msg.length - iv.length];

                    System.arraycopy(full_msg, 0, iv, 0, iv.length);
                    System.arraycopy(full_msg, iv.length, cmsg, 0, cmsg.length);

                }

                cifrado.init(Cipher.DECRYPT_MODE, aes_key, new IvParameterSpec(iv));

                byte[] msg = cifrado.doFinal(cmsg);

                return new String(msg, "UTF-8");

                // NOTE: KeyException is deliberately NOT caught here. It's the one thrown a
                // few lines above when the HMAC doesn't match — i.e. someone tampered with
                // the frame — and it has to reach the socket reader so it discards THAT
                // frame and keeps going. Catching it here would swallow itself, return
                // empty, and the reader would read that as end of connection: a single
                // injected byte would be enough to kick a player off the table. The binary
                // decryption twin doesn't catch it either, for the same reason.
            } catch (UnsupportedEncodingException | IllegalStateException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }

        return null;

    }

    /**
     * Raw-bytes sibling of
     * {@link #encryptString(String, SecretKeySpec, byte[], SecretKeySpec)}:
     * encrypts the given payload bytes (no UTF-8 string round-trip, no Base64)
     * and returns {@code HMAC(32) || IV(16) || AES-CBC/PKCS5(payload)} as raw
     * bytes — the exact same wire structure encryptString produces, minus the
     * trailing Base64.
     *
     * Used as the body of a binary {@link WireFrame} so blobs (voice notes,
     * avatars) ride the channel without the double Base64 inflation of the text
     * command path. encryptString/decryptString are intentionally left
     * untouched.
     */
    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aes_key, byte[] iv, SecretKeySpec hmac_key) {

        if (payload != null) {
            try {
                Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

                cifrado.init(Cipher.ENCRYPT_MODE, aes_key, new IvParameterSpec(iv));

                byte[] cmsg = cifrado.doFinal(payload);

                byte[] iv_cmsg = new byte[iv.length + cmsg.length];

                System.arraycopy(iv, 0, iv_cmsg, 0, iv.length);
                System.arraycopy(cmsg, 0, iv_cmsg, iv.length, cmsg.length);

                if (hmac_key != null) {

                    byte[] full_msg = new byte[32 + iv.length + cmsg.length];

                    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                    sha256_HMAC.init(hmac_key);

                    byte[] hmac = sha256_HMAC.doFinal(iv_cmsg);

                    System.arraycopy(hmac, 0, full_msg, 0, hmac.length);
                    System.arraycopy(iv_cmsg, 0, full_msg, hmac.length, iv_cmsg.length);

                    return full_msg;
                }

                return iv_cmsg;

            } catch (IllegalStateException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        byte[] iv = new byte[16];

        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        return encryptBytes(payload, aes_key, iv, hmac_key);

    }

    /**
     * Raw-bytes sibling of
     * {@link #decryptString(String, SecretKeySpec, SecretKeySpec)}: takes the
     * raw {@code HMAC(32) || IV(16) || ciphertext} bytes (no Base64 decode),
     * verifies the HMAC in constant time, AES-decrypts and returns the
     * plaintext bytes (no UTF-8 string round-trip). Throws {@link KeyException}
     * on HMAC mismatch, the same contract decryptString uses.
     */
    public static byte[] decryptBytes(byte[] full_msg, SecretKeySpec aes_key, SecretKeySpec hmac_key) throws KeyException {

        if (full_msg != null) {
            try {

                Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

                byte[] hmac = new byte[32];

                byte[] iv = new byte[cifrado.getBlockSize()];

                byte[] cmsg;

                if (hmac_key != null) {

                    if (full_msg.length < hmac.length + iv.length) {
                        // Binary frame shorter than HMAC+IV: KeyException (discard) instead of
                        // the NegativeArraySizeException from new byte[negative]. The text twin
                        // does the same; see decryptString.
                        throw new KeyException("Binary frame shorter than HMAC and IV");
                    }

                    cmsg = new byte[full_msg.length - hmac.length - iv.length];

                    System.arraycopy(full_msg, 0, hmac, 0, hmac.length);
                    System.arraycopy(full_msg, hmac.length, iv, 0, iv.length);
                    System.arraycopy(full_msg, hmac.length + iv.length, cmsg, 0, cmsg.length);

                    byte[] iv_cmsg = new byte[iv.length + cmsg.length];

                    System.arraycopy(iv, 0, iv_cmsg, 0, iv.length);
                    System.arraycopy(cmsg, 0, iv_cmsg, iv.length, cmsg.length);

                    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                    sha256_HMAC.init(hmac_key);

                    byte[] current_hmac = sha256_HMAC.doFinal(iv_cmsg);

                    if (!MessageDigest.isEqual(hmac, current_hmac)) {
                        throw new KeyException("BAD HMAC or BAD KEY");
                    }
                } else {

                    if (full_msg.length < iv.length) {
                        throw new KeyException("Binary frame shorter than IV");
                    }

                    cmsg = new byte[full_msg.length - iv.length];

                    System.arraycopy(full_msg, 0, iv, 0, iv.length);
                    System.arraycopy(full_msg, iv.length, cmsg, 0, cmsg.length);

                }

                cifrado.init(Cipher.DECRYPT_MODE, aes_key, new IvParameterSpec(iv));

                return cifrado.doFinal(cmsg);

            } catch (IllegalStateException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }

        return null;

    }

    public static String encryptCommand(String command, SecretKeySpec aes_key, byte[] iv, SecretKeySpec hmac_key) {

        return ("*" + Helpers.encryptString(command, aes_key, iv, hmac_key));

    }

    public static String encryptCommand(String command, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        byte[] iv = new byte[16];

        Helpers.CSPRNG_GENERATOR.nextBytes(iv);

        return encryptCommand(command, aes_key, iv, hmac_key);

    }

    public static String decryptCommand(String command, SecretKeySpec aes_key, SecretKeySpec hmac_key) throws KeyException {

        // null = EOF / dropped socket: this is the contract readers expect, not a channel
        // failure (propagating it as KeyException would flood the log with false MITM
        // alerts on every clean disconnect).
        if (command == null) {
            return null;
        }

        // trim is applied BEFORE looking at the prefix: checking the prefix on the
        // untrimmed string and decrypting the trimmed one would send a legitimate
        // encrypted frame with a leading space down the plaintext branch.
        String frame = command.trim();

        if (!frame.isEmpty() && frame.charAt(0) == '*') {
            return Helpers.decryptString(frame.substring(1), aes_key, hmac_key);
        }

        // From here down the frame is NOT encrypted. Only the keepalive is admitted — it
        // travels in the clear by design — and anything else is rejected: returning the
        // text as-is would let an on-path attacker inject GAME commands into the
        // already-established socket and have them processed as valid, without passing
        // the HMAC or knowing the password. The reconnection ack already defended itself;
        // the rest of the message loop did not.
        if (isPlaintextControlFrame(frame)) {
            return frame;
        }

        throw new KeyException("Unauthenticated frame rejected on an encrypted channel");
    }

    /**
     * Verbs the keepalive writes WITHOUT encryption, by design: transport
     * writers dump the raw string and each caller is the one that applies
     * encryption — something the PING/PONG senders don't do, unlike the game
     * ones, which do go through encryptCommand. There are six: on the host,
     * {@code Participant}'s three (the heartbeat PING, and the PONG and PONG2
     * it replies with to the client's), and on the client,
     * {@code WaitingRoomFrame}'s (its own PING and the replies to the host's),
     * which go out via {@code writeCommandToServer}. WireFrame documents this
     * in its header.
     */
    private static final String[] PLAINTEXT_CONTROL_VERBS = {"PING", "PONG", "PONG2"};

    /**
     * {@code PING#<n>}, {@code PONG#<n>} or {@code PONG2#<n>} and nothing else:
     * an exact verb from the closed set plus an integer counter. Deliberately
     * strict, so the door the keepalive needs can't be used to smuggle anything
     * else through.
     */
    private static boolean isPlaintextControlFrame(String frame) {

        int sep = frame.indexOf('#');

        if (sep <= 0 || sep == frame.length() - 1) {
            return false;
        }

        String verb = frame.substring(0, sep);
        boolean known = false;

        for (String v : PLAINTEXT_CONTROL_VERBS) {
            if (v.equals(verb)) {
                known = true;
                break;
            }
        }

        if (!known) {
            return false;
        }

        String counter = frame.substring(sep + 1);

        // The counter is a signed int (nextInt can be negative): 11 characters at most,
        // counting the '-'.
        if (counter.length() > 11) {
            return false;
        }

        for (int i = 0; i < counter.length(); i++) {
            char c = counter.charAt(i);
            if ((c < '0' || c > '9') && !(i == 0 && c == '-')) {
                return false;
            }
        }

        return !"-".equals(counter);
    }

    /**
     * Atomically writes {@code data} to {@code target}: first to a tempfile
     * next to the target, then {@code Files.move} with ATOMIC_MOVE +
     * REPLACE_EXISTING.
     *
     * Solves the problem with Files.writeString's default open options (CREATE
     * + TRUNCATE_EXISTING + WRITE): it opens the file, truncates it to 0, then
     * writes. If the process dies between TRUNCATE and the first write (power
     * cut, BSOD, JVM kill, AV lock), the file is left EMPTY on disk — data
     * lost.
     *
     * With write-tmp + atomic-move, at any instant the target points to a
     * COMPLETE file (old or new, never partial). If the process dies during the
     * writeString to tmp, the tmp is left partial but the target is still
     * intact with its previous value.
     *
     * Non-atomic fallback on filesystems that don't support ATOMIC_MOVE (FAT32
     * across volumes, rare cases): Files.move without ATOMIC_MOVE. Still
     * preserves the "tmp fully written before the move" invariant; only the
     * window between delete-target and rename-tmp can leave the system without
     * a target (much shorter than the original's TRUNCATE-then-write window).
     *
     * If the move fails for any reason, the orphaned tmp is cleaned up.
     */
    public static void writeStringAtomic(java.nio.file.Path target, CharSequence data) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        java.nio.file.Path tmp = target.resolveSibling(
                target.getFileName().toString() + ".tmp-" + Long.toHexString(System.nanoTime()));
        try {
            java.nio.file.Files.writeString(tmp, data);
            try {
                java.nio.file.Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                // Non-atomic fallback (FAT32, etc). Still strictly better than a direct
                // writeString because the tmp is already fully written to disk.
                java.nio.file.Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException moveEx) {
            // If something fails, clean up the orphaned tmp before propagating.
            try {
                java.nio.file.Files.deleteIfExists(tmp);
            } catch (Exception cleanupEx) {
                // best-effort; the tmp is left for a later cleanup.
            }
            throw moveEx;
        }
    }

    /**
     * Telemetry: payload of a latency/reconnections snapshot that the host
     * emits periodically to all clients. Immutable.
     */
    public static final class TelemetryFrame {

        /**
         * Host's timestamp when emitted (System.currentTimeMillis).
         */
        public final long serverTimestampMs;
        /**
         * nick (canonical, NFC) -> [lat1_ms, lat2_ms, reconnection_count].
         */
        public final java.util.Map<String, int[]> perPeer;

        public TelemetryFrame(long serverTimestampMs, java.util.Map<String, int[]> perPeer) {
            this.serverTimestampMs = serverTimestampMs;
            this.perPeer = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(perPeer));
        }
    }

    /**
     * Encodes a TelemetryFrame to the wire format used by the TELEMETRY
     * broadcast. Format:
     *
     * <ts>#<b64nick>|<lat1>/<lat2>/<recon>@<b64nick>|<lat1>/<lat2>/<recon>@...
     *
     * - ts is the host's System.currentTimeMillis when emitted. - nick is
     * Base64-encoded UTF-8 to avoid clashing with the #/@/| separators (nicks
     * can contain any char). IMPORTANT: the nick/values separator is '|' (NOT
     * '='), because '=' is valid Base64 padding and mixing it in would confuse
     * the parser. - lat1, lat2 are ms. -1 = not measured / timeout. - recon is
     * that peer's cumulative reconnection count.
     *
     * The caller wraps the result in "GAME#<id>#TELEMETRY#<payload>" before the
     * usual encryptCommand.
     */
    public static String encodeTelemetry(Helpers.TelemetryFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }
        StringBuilder sb = new StringBuilder(64 + frame.perPeer.size() * 32);
        sb.append(frame.serverTimestampMs);
        sb.append('#');
        boolean first = true;
        for (java.util.Map.Entry<String, int[]> e : frame.perPeer.entrySet()) {
            int[] v = e.getValue();
            if (v == null || v.length < 3) {
                continue;
            }
            if (!first) {
                sb.append('@');
            }
            first = false;
            try {
                sb.append(java.util.Base64.getEncoder().encodeToString(e.getKey().getBytes("UTF-8")));
            } catch (java.io.UnsupportedEncodingException uee) {
                // UTF-8 is guaranteed by Java; this catch is defensive.
                sb.append(java.util.Base64.getEncoder().encodeToString(e.getKey().getBytes()));
            }
            sb.append('|');
            sb.append(v[0]).append('/').append(v[1]).append('/').append(v[2]);
        }
        return sb.toString();
    }

    /**
     * Decodes the TELEMETRY wire format. Tolerates malformed input (silently
     * skips entries with missing fields or that fail to parse as int) so a
     * hostile peer can't crash the client with a corrupt payload.
     *
     * Returns null if the payload doesn't even have the leading ts.
     */
    public static Helpers.TelemetryFrame decodeTelemetry(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        int firstHash = payload.indexOf('#');
        long ts;
        String entries;
        if (firstHash < 0) {
            // Just ts with no entries (empty broadcast).
            try {
                ts = Long.parseLong(payload);
            } catch (NumberFormatException ex) {
                return null;
            }
            return new TelemetryFrame(ts, new java.util.HashMap<>());
        }
        try {
            ts = Long.parseLong(payload.substring(0, firstHash));
        } catch (NumberFormatException ex) {
            return null;
        }
        entries = payload.substring(firstHash + 1);
        java.util.Map<String, int[]> map = new java.util.HashMap<>();
        if (!entries.isEmpty()) {
            String[] tuples = entries.split("@");
            for (String t : tuples) {
                // The nick/values separator is '|', NOT '='. Reason: '=' is Base64
                // padding and mixing it in would confuse the parser.
                int pipe = t.indexOf('|');
                if (pipe <= 0 || pipe >= t.length() - 1) {
                    continue;
                }
                String b64nick = t.substring(0, pipe);
                String numbers = t.substring(pipe + 1);
                String[] parts = numbers.split("/");
                if (parts.length < 3) {
                    continue;
                }
                String nick;
                try {
                    nick = new String(java.util.Base64.getDecoder().decode(b64nick), "UTF-8");
                } catch (Exception ex) {
                    continue;
                }
                if (nick.isEmpty()) {
                    continue;
                }
                int lat1;
                int lat2;
                int recon;
                try {
                    lat1 = Integer.parseInt(parts[0]);
                    lat2 = Integer.parseInt(parts[1]);
                    recon = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ex) {
                    continue;
                }
                map.put(nick, new int[]{lat1, lat2, recon});
            }
        }
        return new TelemetryFrame(ts, map);
    }

    /**
     * Runs {@code action} on the EDT as soon as {@code c} has height > 0
     * (layout applied). If already laid out, runs immediately. Otherwise
     * installs a one-shot ComponentListener that removes itself after the first
     * resize with height > 0.
     *
     * Replaces the anti-pattern {@code Helpers.threadRun(() -> { while (c.getHeight() == 0)
     * Helpers.pausar(125); Helpers.GUIRun(action); })}, which polled Swing's
     * event-driven state with sleeps — zero CPU while waiting, zero latency on
     * wake-up.
     *
     * Only suitable when {@code action} doesn't need to hold an external lock
     * during its execution (it runs directly on the EDT). If a lock +
     * GUIRunAndWait is needed, use
     * {@link #awaitFirstLayout(javax.swing.JComponent)} from an off-EDT thread.
     */
    public static void runWhenLaidOut(javax.swing.JComponent c, Runnable action) {
        if (c == null || action == null) {
            return;
        }
        GUIRun(() -> {
            if (c.getHeight() > 0) {
                action.run();
                return;
            }
            // Triple coverage so we never end up waiting on an event that never
            // arrives: ComponentListener (when the component resizes and gets height >
            // 0), HierarchyListener (when it becomes visible/displayable), and a 2s
            // safety Timer that runs the action anyway if none of the above fired.
            // Better late than never; without the timer, a component born at size 0x0
            // that never gets laid out would leave the action hanging forever.
            java.util.concurrent.atomic.AtomicBoolean done
                    = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.awt.event.ComponentListener[] cl = new java.awt.event.ComponentListener[1];
            java.awt.event.HierarchyListener[] hl = new java.awt.event.HierarchyListener[1];
            javax.swing.Timer[] timer = new javax.swing.Timer[1];

            Runnable fireOnce = () -> {
                if (done.compareAndSet(false, true)) {
                    if (cl[0] != null) {
                        c.removeComponentListener(cl[0]);
                    }
                    if (hl[0] != null) {
                        c.removeHierarchyListener(hl[0]);
                    }
                    if (timer[0] != null) {
                        timer[0].stop();
                    }
                    action.run();
                }
            };
            cl[0] = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (c.getHeight() > 0) {
                        fireOnce.run();
                    }
                }
            };
            hl[0] = (java.awt.event.HierarchyEvent e) -> {
                if (c.getHeight() > 0) {
                    fireOnce.run();
                }
            };
            timer[0] = new javax.swing.Timer(2000, ae -> fireOnce.run());
            timer[0].setRepeats(false);
            c.addComponentListener(cl[0]);
            c.addHierarchyListener(hl[0]);
            timer[0].start();
        });
    }

    /**
     * Blocks the current thread (which must NOT be the EDT) until {@code c} has
     * height > 0. Use when the caller needs to hold an external lock during the
     * subsequent GUIRunAndWait — the lock can't be taken from the EDT because
     * another non-EDT thread might be holding it while blocked waiting on the
     * EDT, which would deadlock.
     *
     * Returns without blocking if already laid out.
     */
    public static void awaitFirstLayout(javax.swing.JComponent c) throws InterruptedException {
        if (c == null || c.getHeight() > 0) {
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.awt.event.ComponentListener[] cl = new java.awt.event.ComponentListener[1];
        java.awt.event.HierarchyListener[] hl = new java.awt.event.HierarchyListener[1];
        Runnable release = () -> {
            if (latch.getCount() > 0) {
                latch.countDown();
                if (cl[0] != null) {
                    c.removeComponentListener(cl[0]);
                }
                if (hl[0] != null) {
                    c.removeHierarchyListener(hl[0]);
                }
            }
        };
        cl[0] = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (c.getHeight() > 0) {
                    release.run();
                }
            }
        };
        hl[0] = (java.awt.event.HierarchyEvent e) -> {
            if (c.getHeight() > 0) {
                release.run();
            }
        };
        GUIRun(() -> {
            c.addComponentListener(cl[0]);
            c.addHierarchyListener(hl[0]);
            // Re-check post-install to cover the race where layout was applied between
            // the initial check and addComponentListener.
            if (c.getHeight() > 0) {
                release.run();
            }
        });
        // Safety timeout: 2s. If still not laid out after this, bail out anyway rather
        // than blocking the caller indefinitely. Callers depending on the height will
        // have to tolerate it (same as runWhenLaidOut's initial-value case).
        if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            GUIRun(release);
        }
    }

    /**
     * Sanitizes a nick for safe use as a filename SEGMENT on disk. Defends
     * against path traversal when the nick comes from a remote peer (a hostile
     * host sending NEWUSER/USERSLIST with nick "../../../../foo") and against
     * Windows reserved names ("CON", "NUL", etc.) that would make
     * FileOutputStream fail silently.
     *
     * Rules: - Only keeps [A-Za-z0-9_-]. Any other char (including '.', '/',
     * '\', ':', control chars, Unicode) is replaced with '_'. - Truncates to 32
     * chars max (logs and avatars don't need more). - Windows reserved names
     * (CON/PRN/AUX/NUL/COM[1-9]/LPT[1-9], case-insensitive) are prefixed with
     * '_' to avoid AccessDeniedException. - null or an empty string after
     * sanitization returns "user".
     *
     * NOTE: the result is NOT a unique identifier (two different nicks can
     * collide after sanitization). Call sites that need uniqueness must add
     * their own suffix (random file_id, hash, etc.) — this helper only
     * guarantees the segment is filesystem-safe.
     */
    public static String safeNickForFilename(String nick) {
        if (nick == null || nick.isEmpty()) {
            return "user";
        }
        String safe = nick.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isEmpty()) {
            return "user";
        }
        if (safe.length() > 32) {
            safe = safe.substring(0, 32);
        }
        // Trim leading dashes (cosmetic — names like "-rf" look like flags)
        while (safe.startsWith("-")) {
            safe = safe.length() > 1 ? safe.substring(1) : "";
        }
        if (safe.isEmpty()) {
            return "user";
        }
        String upper = safe.toUpperCase();
        if (upper.equals("CON") || upper.equals("PRN") || upper.equals("AUX")
                || upper.equals("NUL") || upper.matches("COM[1-9]") || upper.matches("LPT[1-9]")) {
            return "_" + safe;
        }
        return safe;
    }

    /**
     * Bounded replacement for {@link java.io.BufferedReader#readLine()}. Same
     * contract (null if EOF before reading anything, CR-LF trimmed) but ABORTS
     * with an IOException if the line accumulates more than {@code maxChars}
     * characters before the line break. Defends against a peer that opens the
     * channel and sends bytes with no '\n' to force an OOM on the receiver (the
     * standard readLine grows its internal buffer without limit).
     *
     * The cap is measured in Reader characters (post UTF-8 decode). The
     * char~=byte approximation is valid for our wire format (Base64 + digits +
     * '#'), all ASCII.
     */
    public static String readBoundedLine(java.io.BufferedReader reader, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        int c;
        boolean readAnything = false;
        while ((c = reader.read()) != -1) {
            readAnything = true;
            if (c == '\n') {
                return sb.toString();
            }
            if (c == '\r') {
                continue;
            }
            sb.append((char) c);
            if (sb.length() > maxChars) {
                throw new IOException("Line exceeds " + maxChars + " char cap (DoS guard tripped)");
            }
        }
        return readAnything ? sb.toString() : null;
    }

    /**
     * Derives a 64-byte channel secret from the raw ECDH shared secret. If a
     * password is provided, the secret is bound to it via HMAC-SHA512, blocking
     * passive MITM attacks for password-protected games.
     */
    public static byte[] deriveChannelSecret(byte[] sharedSecret, String password) {
        try {
            if (password != null && !password.isEmpty()) {
                Mac mac = Mac.getInstance("HmacSHA512");
                mac.init(new SecretKeySpec(password.getBytes("UTF-8"), "HmacSHA512"));
                return mac.doFinal(sharedSecret);
            }
            return MessageDigest.getInstance("SHA-512").digest(sharedSecret);
        } catch (Exception ex) {
            throw new RuntimeException("Channel secret derivation failed", ex);
        }
    }

    /**
     * Estimates the entropy of a password in bits using the character-class
     * heuristic: alphabet size is the sum of the sizes of the character classes
     * present, and the entropy is length * log2(alphabet). Used by the password
     * strength warning at game creation. This is a floor estimate that does not
     * penalize dictionary words or common patterns.
     *
     * Returns 0 for null or empty input.
     */
    public static int estimatePasswordEntropyBits(String pwd) {
        if (pwd == null || pwd.isEmpty()) {
            return 0;
        }
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSymbol = false;
        for (int i = 0; i < pwd.length(); i++) {
            char c = pwd.charAt(i);
            if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        int alphabet = 0;
        if (hasLower) {
            alphabet += 26;
        }
        if (hasUpper) {
            alphabet += 26;
        }
        if (hasDigit) {
            alphabet += 10;
        }
        if (hasSymbol) {
            alphabet += 32;
        }
        if (alphabet == 0) {
            return 0;
        }
        double bits = pwd.length() * (Math.log(alphabet) / Math.log(2));
        return (int) Math.floor(bits);
    }

    // Renders a component (and its whole child hierarchy) to an image at the monitor's
    // NATIVE resolution (respects the OS's HiDPI scaling). Does NOT use Robot or the
    // system's screen capture: the component itself is redrawn with Java2D onto the
    // image (same engine that paints the felt every frame), so it works on any platform
    // (Windows, Mac, Linux X11/Wayland) without permissions and without coming out
    // black. MUST be called on the EDT (Swing isn't thread-safe when painting).
    public static BufferedImage renderComponentImage(Component comp) {

        if (comp == null) {
            return null;
        }

        int w = comp.getWidth();
        int h = comp.getHeight();

        if (w <= 0 || h <= 0) {
            return null;
        }

        double scale_x = 1.0;
        double scale_y = 1.0;

        java.awt.GraphicsConfiguration gc = comp.getGraphicsConfiguration();

        if (gc != null) {
            java.awt.geom.AffineTransform tx = gc.getDefaultTransform();
            scale_x = tx.getScaleX();
            scale_y = tx.getScaleY();
        }

        BufferedImage image = new BufferedImage((int) Math.round(w * scale_x), (int) Math.round(h * scale_y), BufferedImage.TYPE_INT_RGB);

        Graphics2D g = image.createGraphics();

        try {
            // HiDPI scaling: the component is painted at its logical size but onto a
            // canvas at physical resolution => native sharpness (text, chips and
            // borders are re-rasterized at high resolution instead of being copied).
            g.scale(scale_x, scale_y);
            comp.printAll(g);
        } finally {
            g.dispose();
        }

        return image;
    }

    // Saves a screenshot image to SCREENSHOTS_DIR as PNG. Does disk I/O: call OFF the
    // EDT. Releases the image when done. Returns true if it was written successfully.
    public static boolean saveScreenshot(BufferedImage image) {

        if (image == null) {
            return false;
        }

        try {
            ImageIO.write(image, "png", new File(SCREENSHOTS_DIR + "/coronapoker_screenshot_" + String.valueOf(System.currentTimeMillis()) + ".png"));

            return true;

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);

            return false;

        } finally {
            // A 4K capture is ~33 MB of native pixel data. Without flush, it waits on GC.
            image.flush();
        }
    }

    public static void createIfNoExistsCoronaDirs() {

        String[] dirs = new String[]{CORONA_DIR, LOGS_DIR, DEBUG_DIR, SCREENSHOTS_DIR, CHAT_IMAGE_CACHE, Init.VOICE_DIR}; //WATCH THE ORDER — CORONA_DIR MUST COME FIRST!

        for (String d : dirs) {
            if (!Files.isDirectory(Paths.get(d))) {
                try {
                    Files.createDirectories(Paths.get(d));

                } catch (IOException ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        // The chat image cache was never cleaned up: every image someone pastes stays
        // there forever. Pruned at startup (twice, actually: this class's initializer
        // and the app startup both call here, and the second call finds nothing to do)
        // with the directories already created, before anyone uses it.
        ImageCacheManager.purgeCache();

        // Avatars the game leaves in the system temp dir are NOT swept. Deleting ones
        // older than a day was tried and is dangerous: those files are re-read BY PATH
        // while the game is alive (on zoom change, when resending them to someone
        // joining or reconnecting, when setting up the next game), and a second instance
        // left open for more than a day would end up without them. They are still
        // deleted on game exit, as always. Accumulating a few kilobytes is far less bad
        // than losing avatars mid-game.
    }

    public static void copyTextToClipboard(String text) {

        StringSelection stringSelection = new StringSelection(text);
        Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
        clpbrd.setContents(stringSelection, null);

    }

    /**
     * Pushes the given BufferedImage to the system clipboard wrapped in a
     * Transferable that exposes DataFlavor.imageFlavor (consumed by image-aware
     * apps like Gimp, Photoshop, Telegram desktop, browsers, etc.). Returns
     * false if the image is null or the clipboard is unreachable.
     */
    public static boolean copyImageToClipboard(BufferedImage img) {

        if (img == null) {
            return false;
        }

        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new ImageTransferable(img), null);
            return true;
        } catch (Exception ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.WARNING, "Could not copy image to clipboard", ex);
            return false;
        }
    }

    private static final class ImageTransferable implements Transferable {

        private final Image image;

        ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    // Drawn play/stop icon (a matching pair that scales with DIALOG_ZOOM): green play
    // triangle or rounded red stop square. Shared by the Audio settings tab's playback
    // preview and by the voice notes viewer.
    public static javax.swing.Icon playStopGlyph(boolean stop) {
        final int size = Math.round(15 * Helpers.DIALOG_ZOOM);
        final java.awt.Color color = stop ? new java.awt.Color(0xC6, 0x28, 0x28) : new java.awt.Color(0x2E, 0x7D, 0x32);
        return new javax.swing.Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int m = Math.round(size * 0.12f);
                if (stop) {
                    int s = size - 2 * m;
                    int arc = Math.max(2, s / 4);
                    g2.fillRoundRect(x + m, y + m, s, s, arc, arc);
                } else {
                    int[] xs = {x + m, x + m, x + size - m};
                    int[] ys = {y + m, y + size - m, y + size / 2};
                    g2.fillPolygon(xs, ys, 3);
                }
                g2.dispose();
            }
        };
    }

    // Delete icon: a drawn red X (rounded stroke) at the given size, in the same red as
    // the stop glyph. Shared by the voice notes viewer and the screenshot viewer's menu.
    public static javax.swing.Icon deleteGlyph(int size) {
        final int s = size;
        final java.awt.Color color = new java.awt.Color(0xC6, 0x28, 0x28);
        return new javax.swing.Icon() {
            @Override
            public int getIconWidth() {
                return s;
            }

            @Override
            public int getIconHeight() {
                return s;
            }

            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new java.awt.BasicStroke(Math.max(2f, s * 0.16f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int m = Math.round(s * 0.2f);
                g2.drawLine(x + m, y + m, x + s - m, y + s - m);
                g2.drawLine(x + s - m, y + m, x + m, y + s - m);
                g2.dispose();
            }
        };
    }

    public static String genRandomString(int length) {

        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = length;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    /**
     * Attaches a DocumentListener to the JPasswordField that repaints its
     * background based on the password's estimated strength in entropy bits: -
     * empty -> defaultBg (no password = OK, public game). - 1..59 bits -> light
     * yellow (weak). - >=60 bits -> light green (strong). Same thresholds as
     * the "ui.password_debil_aviso" popup.
     */
    public static void attachPasswordStrengthHint(final javax.swing.JPasswordField field) {
        if (field == null) {
            return;
        }
        final java.awt.Color defaultBg = field.getBackground();
        final java.awt.Color weakBg = new java.awt.Color(0xFF, 0xF1, 0x76);
        final java.awt.Color strongBg = new java.awt.Color(0xC8, 0xE6, 0xC9);
        Runnable update = () -> {
            char[] chars = field.getPassword();
            if (chars == null || chars.length == 0) {
                field.setBackground(defaultBg);
            } else if (estimatePasswordEntropyBits(new String(chars)) >= 60) {
                field.setBackground(strongBg);
            } else {
                field.setBackground(weakBg);
            }
        };
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update.run();
            }
        });
        update.run();
    }

    /**
     * Adds an "eye" button anchored to the JPasswordField's right edge that
     * reveals the password in the clear WHILE held down (mouse) and hides it
     * again on release (even if released outside the button).
     *
     * Self-contained: only touches the field itself (its layout manager and its
     * right margin), so it works with any parent layout — GroupLayout,
     * BorderLayout — without restructuring it or touching the NetBeans .form.
     * The button is positioned by hand at the real right edge (ignoring the
     * inset), and the reserved right margin keeps the text/caret from running
     * under the eye. The icon is drawn vectorially (no assets). Idempotent.
     */
    public static void attachPasswordRevealButton(final javax.swing.JPasswordField field) {
        if (field == null) {
            return;
        }
        final char echo = field.getEchoChar();
        if (echo == 0) {
            return; // already shown in the clear: nothing to toggle
        }
        for (java.awt.Component existing : field.getComponents()) {
            if (existing instanceof javax.swing.JButton) {
                return; // already attached
            }
        }

        final int side = Math.max(14, field.getFont().getSize());
        final javax.swing.JButton eye = new javax.swing.JButton(new EyeIcon(side));
        eye.setFocusable(false);
        eye.setRolloverEnabled(false);
        eye.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        eye.setContentAreaFilled(false);
        eye.setOpaque(false);
        eye.setMargin(new java.awt.Insets(0, 0, 0, 0));
        eye.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        eye.setToolTipText(Translator.translate("auth.mostrar_password_pulsar"));

        // isPressed covers the mouse hold (goes back to false on release anywhere):
        // reveals while held down, hides on release.
        eye.getModel().addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                field.setEchoChar(eye.getModel().isPressed() ? (char) 0 : echo);
            }
        });

        // The field's UI paints its text up to the right inset; the button is placed by
        // hand at the real right edge (beyond the inset), so they don't overlap. The
        // reserved right margin = eye width + slack.
        final int reserve = side + 6;
        field.setLayout(new java.awt.LayoutManager() {
            @Override
            public void layoutContainer(java.awt.Container parent) {
                java.awt.Dimension bs = eye.getPreferredSize();
                int x = parent.getWidth() - bs.width - 3;
                int y = Math.max(0, (parent.getHeight() - bs.height) / 2);
                eye.setBounds(x, y, bs.width, bs.height);
            }

            @Override
            public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
                return parent.getSize();
            }

            @Override
            public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) {
                return new java.awt.Dimension(0, 0);
            }

            @Override
            public void addLayoutComponent(String name, java.awt.Component comp) {
            }

            @Override
            public void removeLayoutComponent(java.awt.Component comp) {
            }
        });
        field.add(eye);

        java.awt.Insets m = field.getMargin();
        if (m == null) {
            m = new java.awt.Insets(0, 0, 0, 0);
        }
        field.setMargin(new java.awt.Insets(m.top, m.left, m.bottom, m.right + reserve));
    }

    /**
     * Vector icon of an eye (almond outline + pupil) for the password reveal
     * button. Scales with the requested size; no image files. Antialiased.
     */
    private static final class EyeIcon implements javax.swing.Icon {

        private final int size;

        EyeIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                float w = size;
                float h = size;
                float cx = x + w / 2f;
                float cy = y + h / 2f;
                float eyeW = w * 0.92f;
                float eyeH = h * 0.55f;
                g2.setColor(new java.awt.Color(0x42, 0x42, 0x42));
                g2.setStroke(new java.awt.BasicStroke(Math.max(1f, size / 12f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                float left = cx - eyeW / 2f;
                float right = cx + eyeW / 2f;
                java.awt.geom.GeneralPath almond = new java.awt.geom.GeneralPath();
                almond.moveTo(left, cy);
                almond.quadTo(cx, cy - eyeH, right, cy);
                almond.quadTo(cx, cy + eyeH, left, cy);
                almond.closePath();
                g2.draw(almond);
                float pr = h * 0.17f;
                g2.fill(new java.awt.geom.Ellipse2D.Float(cx - pr, cy - pr, pr * 2f, pr * 2f));
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Generates a STRONG random password with a CSPRNG (not Random) using an
     * alphabet designed to be EASY TO DICTATE / TYPE by hand: lowercase letters
     * + digits only (36 chars). No uppercase (avoids upper/lower confusion when
     * dictating) and no symbols (avoids international keyboard issues,
     * dictation problems, and clashes with wire format characters).
     *
     * Entropy: log2(36^length). For length=14, ~72 bits — above the game's
     * 60-bit "weak password" warning threshold.
     *
     * Typical example: "k7m3p2n8qjz5xv".
     *
     * NOT to be confused with genRandomString (also a-z but uses pseudorandom
     * java.util.Random, NOT a CSPRNG — still fine for legacy non-sensitive
     * tokens: tempfile nicks, ephemeral ids).
     */
    public static String genStrongPassword(int length) {
        // a-z + 0-9 = 36 chars. Easy to dictate and type; ~2.8 bits/char.
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // SecureRandom.nextInt — uniform, unbiased.
            sb.append(alphabet.charAt(CSPRNG_GENERATOR.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Converts a given Image into a BufferedImage
     * https://stackoverflow.com/a/13605411
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    public static BufferedImage toBufferedImage(Image img) {
        // Check whether the image is null
        if (img == null) {
            throw new IllegalArgumentException("Image must not be null.");
        }

        // If the image is already a BufferedImage, return it directly
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a BufferedImage with transparency
        BufferedImage bimage = new BufferedImage(
                img.getWidth(null),
                img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB
        );

        // Draw the image onto the BufferedImage
        Graphics2D g2d = bimage.createGraphics();
        try {
            g2d.drawImage(img, 0, 0, null);
        } finally {
            g2d.dispose(); // Make sure to release resources
        }

        return bimage;
    }

    public static BufferedImage desaturate(BufferedImage source, float opacity) {
        ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        float[] scales = {1f, opacity};
        float[] offsets = new float[2];
        RescaleOp rop = new RescaleOp(scales, offsets, null);

        return rop.filter(colorConvert.filter(source, null), null);
    }

    //Thanks -> https://stackoverflow.com/a/7603815
    public static BufferedImage makeImageRoundedCorner(Image image, int cornerRadius) {
        // Get the original image's dimensions
        int width = image.getWidth(null);
        int height = image.getHeight(null);

        // Create a new image with transparency
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = output.createGraphics();

        try {
            // Enable antialiasing for smooth edges
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw a white rounded rectangle as a mask
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, cornerRadius, cornerRadius));

            // Set the composite mode to apply the mask
            g2d.setComposite(AlphaComposite.SrcIn);
            g2d.drawImage(image, 0, 0, null);
        } finally {
            // Release the Graphics2D's native resources. ALWAYS — without try/finally,
            // an OOM or IllegalArgumentException between createGraphics and dispose left
            // the native context leaked. Called on EVERY card load (~104 invocations per
            // zoom/deck change).
            g2d.dispose();
        }

        return output;
    }

    public static String extractStringFromClipboardContents(Transferable contents) {

        String ret = null;

        if (contents != null) {

            try {

                Object o = contents.getTransferData(DataFlavor.stringFlavor);

                if (o instanceof String) {

                    ret = (String) o;
                }

            } catch (Exception ex) {
            }
        }

        return ret;

    }

    public static void openBrowserURL(final String url) {
        Helpers.threadRun(new Runnable() {
            public void run() {
                try {
                    Desktop.getDesktop().browse(new URI(url));

                } catch (URISyntaxException | IOException ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex.getMessage());
                }
            }
        });
    }

    public static void openBrowserURLAndWait(final String url) {

        try {
            Desktop.getDesktop().browse(new URI(url));

        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex.getMessage());
        }

    }

    public static String toHexString(byte[] array) {
        return DatatypeConverter.printHexBinary(array);
    }

    public static byte[] toByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }

    public static void setTranslatedText(Component c, String key) {
        if (c == null || key == null) {
            return;
        }

        String translated = Translator.translate(key);
        if (c instanceof JLabel) {
            ((JLabel) c).setText(translated);
        } else if (c instanceof AbstractButton) {
            ((AbstractButton) c).setText(translated);
        } else if (c instanceof JTextField) {
            ((JTextField) c).setText(translated);
        } else if (c instanceof Frame) {
            ((Frame) c).setTitle(translated);
        } else if (c instanceof Dialog) {
            ((Dialog) c).setTitle(translated);
        }

        if (c instanceof JComponent) {
            ((JComponent) c).putClientProperty("i18n.key", key);
        }
    }

    // An overly long tooltip gets drawn as a single horizontal strip that runs off
    // screen. Above this threshold (in characters), it's wrapped in fixed-width HTML so
    // Swing splits it across multiple lines.
    private static final int TOOLTIP_WRAP_THRESHOLD = 60;
    private static final int TOOLTIP_WRAP_WIDTH_PX = 320;

    // Wraps a long tooltip in fixed-width (multiline) HTML. Returns the text as-is if
    // it's null, short, or already HTML (some tooltips bring their own custom <html>
    // and must not be re-wrapped).
    public static String wrapToolTip(String text) {
        if (text == null || text.length() <= TOOLTIP_WRAP_THRESHOLD || text.stripLeading().regionMatches(true, 0, "<html", 0, 5)) {
            return text;
        }

        return "<html><body style='width:" + TOOLTIP_WRAP_WIDTH_PX + "px'>" + text + "</body></html>";
    }

    public static void setTranslatedToolTip(Component c, String key) {
        if (c instanceof JComponent && key != null) {
            JComponent jc = (JComponent) c;
            jc.setToolTipText(wrapToolTip(Translator.translate(key)));
            jc.putClientProperty("i18n.tooltip_key", key);
        }
    }

    public static void translateComponents(final Component component, boolean force) {
        if (component != null) {
            if (component instanceof JComponent) {
                JComponent jc = (JComponent) component;
                String key = (String) jc.getClientProperty("i18n.key");
                String tooltipKey = (String) jc.getClientProperty("i18n.tooltip_key");

                if (key != null) {
                    if (jc instanceof JLabel) {
                        ((JLabel) jc).setText(Translator.translate(key, force));
                    } else if (jc instanceof AbstractButton) {
                        ((AbstractButton) jc).setText(Translator.translate(key, force));
                    } else if (jc instanceof JTextField) {
                        ((JTextField) jc).setText(Translator.translate(key, force));
                    }
                }

                if (tooltipKey != null) {
                    jc.setToolTipText(wrapToolTip(Translator.translate(tooltipKey, force)));
                }

                // Handle TitledBorder separately
                if (jc.getBorder() instanceof TitledBorder) {
                    TitledBorder border = (TitledBorder) jc.getBorder();
                    String borderKey = (String) jc.getClientProperty("i18n.border_key");
                    if (borderKey != null) {
                        border.setTitle(Translator.translate(borderKey, force));
                    }
                }
            }

            if (component instanceof JMenu) {
                JMenu menu = (JMenu) component;
                for (Component child : menu.getMenuComponents()) {
                    translateComponents(child, force);
                }
            } else if (component instanceof JComboBox) {
                // JComboBox items are tricky because they can be anything.
                // If they are translateable strings, they should probably be handled differently.
                // For now, let's keep it simple or skip if we don't have a reliable way.
            } else if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    translateComponents(child, force);
                }
            }
        }
    }

    public static void setTranslatedTitle(Component c, String t) {

        if (c instanceof JDialog) {

            ((JDialog) c).setTitle(Init.WINDOW_TITLE + " - " + Translator.translate(t));

        } else if (c instanceof JFrame) {
            ((JFrame) c).setTitle(Init.WINDOW_TITLE + " - " + Translator.translate(t));
        }
    }

    /**
     * Reliably restarts the CoronaPoker application by spawning a new JVM
     * process and terminating the current one.
     */
    public static void restartCoronaPoker() {
        try {
            // 0. Flush any pending deferred save BEFORE spawning the child JVM: the shutdown hook
            // would otherwise truncate and rewrite the properties file while the new instance is
            // already reading it, and the child could start with everything at its defaults.
            Helpers.savePropertiesFile();

            // 1. Get the Java executable and the current JAR paths
            String javaBin = Helpers.getJavaBinPath();
            String currentJar = Helpers.getCurrentJarPath();

            // 2. Build the launch command: java -jar CoronaPoker.jar
            ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", currentJar);

            // Set the working directory to the folder where the JAR is located
            builder.directory(new java.io.File(Helpers.getCurrentJarParentPath()));

            // 3. Start the new independent process
            builder.start();

            // 4. Safely close resources to prevent locks in the new instance
            Helpers.closeSQLITE();
            if (Helpers.THREAD_POOL != null) {
                Helpers.SHUTDOWN_THREAD_POOL();
            }

            // 5. Terminate the current JVM instance
            System.exit(0);

        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Helpers.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Critical error during restart", ex);

            // Fallback to manual restart if process creation fails
            Helpers.mostrarMensajeError(null, "RESTART ERROR");
        }
    }

    public static void updateFonts(final Component component, final Font font, final Float zoom_factor) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        updateFonts(child, font, zoom_factor);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        updateFonts(child, font, zoom_factor);
                    }
                }
            }

            Font old_font = component.getFont();

            Font new_font = font.deriveFont(old_font.getStyle(), zoom_factor != null ? Math.round(old_font.getSize() * zoom_factor) : old_font.getSize());

            boolean error;

            do {
                try {

                    if (component instanceof JTable) {
                        ((JTable) component).getTableHeader().setFont(new_font);
                    }

                    component.setFont(new_font);
                    error = false;
                } catch (Exception ex) {
                    error = true;
                }
            } while (error);

        }
    }

    // Reads a NUMERIC preference from PROPERTIES with a safety net: if the key is
    // missing, empty, or not a number, falls back to the default instead of taking down
    // startup. These parses live in static initializers, where a NumberFormatException
    // bubbles up as an ExceptionInInitializerError and leaves the window half-built.
    // This is what readDialogZoom (below) already did for dialog_zoom, generalized to
    // the other numeric keys.
    public static int propInt(String key, int def) {

        try {
            return Integer.parseInt(PROPERTIES.getProperty(key, String.valueOf(def)).trim());
        } catch (Exception ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.WARNING, "Invalid {0} property, falling back to default.", key);
            return def;
        }
    }

    // Variant that also clamps the value to the setting's valid range, for keys that have one.
    public static int propInt(String key, int def, int min, int max) {

        return Math.max(min, Math.min(propInt(key, def), max));
    }

    // The same safety net for decimal-valued keys.
    public static double propDouble(String key, double def) {

        try {
            return Double.parseDouble(PROPERTIES.getProperty(key, String.valueOf(def)).trim());
        } catch (Exception ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.WARNING, "Invalid {0} property, falling back to default.", key);
            return def;
        }
    }

    // Reads the dialog zoom preference (dialog_zoom) from PROPERTIES, clamped to the
    // valid range. 1.0 = design size. Called when initializing the static DIALOG_ZOOM field.
    private static float readDialogZoom() {
        try {
            float z = Float.parseFloat(PROPERTIES.getProperty("dialog_zoom", "1.0"));
            return Math.max(DIALOG_ZOOM_MIN, Math.min(DIALOG_ZOOM_MAX, z));
        } catch (Exception ex) {
            return 1f;
        }
    }

    public static boolean isDialogZoomActive() {
        return Math.abs(DIALOG_ZOOM - 1f) >= 0.01f;
    }

    // Applies the GLOBAL dialog zoom (DIALOG_ZOOM) to a window that's ALREADY PACKED to
    // its DESIGN size (the dialog must have done updateFonts(this,GUI_FONT,null) +
    // pack() before this, as usual). Scales the fonts and RESIZES THE WINDOW to (design
    // size x factor): that keeps the zoom UNIFORM with no leftover border. The key is to
    // NOT repack: pack() uses the .form's FIXED widths/gaps (e.g. a JLabel with a fixed
    // preferred width of 804px), which the font zoom doesn't shrink, leaving margins;
    // forcing the new size instead reflows the flexible components (the ones with MAX
    // width) to the real width. STATIC decorative icons are scaled separately with
    // scaleDialogIcon. No-op at 1.0. Does NOT touch the TABLE zoom (GameFrame.ZOOM_LEVEL).
    public static void zoomDialog(java.awt.Window window) {
        if (window == null || !isDialogZoomActive()) {
            return;
        }
        int design_w = window.getWidth();
        // Width that does NOT scale with the font (window chrome + scrollbar's vertical
        // bar): stays fixed, only the CONTENT is scaled, so text fits without clipping.
        java.awt.Insets ins = window.getInsets();
        int chrome_w = ins.left + ins.right + scrollBarAllowance(window);
        updateFonts(window, GUI_FONT, DIALOG_ZOOM);
        syncTitledBorderFonts(window);
        // Repacks with the already-scaled fonts and performs layout (baseline for measuring content).
        window.pack();
        // WIDTH: scales only the content (design - chrome) x factor and adds the fixed
        // chrome back on; that way it shrinks/grows without leftover border and without
        // clipping the longest line. Never below the real minimum (respects RIGID
        // fixed-width components, e.g. buttons). For dialogs with a JScrollPane, the
        // dialog must have already called trackViewportWidth(scroll) so the content
        // reflows to this width.
        int content_w = Math.round((design_w - chrome_w) * DIALOG_ZOOM) + chrome_w;
        int target_w = Math.max(content_w, window.getMinimumSize().width);
        // HEIGHT: defaults to the packed one. But if the content sits in a JScrollPane,
        // its design PREFERRED height is FIXED in the .form (doesn't follow the scaled
        // content, leaves a gap below), so instead the REAL height of the scroll's view
        // + the vertical chrome (insets + border/bar) is used: hugs the window to the
        // content, with no gap or spurious vertical scrollbar.
        int target_h = window.getHeight();
        javax.swing.JScrollPane sp = findScrollPane(window);
        if (sp != null && sp.getViewport() != null && sp.getViewport().getView() != null) {
            int view_h = sp.getViewport().getView().getPreferredSize().height;
            int chrome_h = ins.top + ins.bottom + (sp.getHeight() - sp.getViewport().getHeight());
            target_h = Math.max(view_h + chrome_h, window.getMinimumSize().height);
        }
        window.setSize(target_w, target_h);
    }

    // Makes a JScrollPane's content FOLLOW the viewport's width: as the window narrows,
    // the content reflows to that width (the flexible components shrink down to their
    // minimum, which does depend on the font) instead of showing a horizontal scrollbar
    // because of the .form's fixed PREFERRED width. JLabels don't wrap, so height
    // doesn't depend on width. For dialogs with scroll and zoom.
    public static void trackViewportWidth(javax.swing.JScrollPane scroll) {
        if (scroll == null) {
            return;
        }
        final javax.swing.JViewport vp = scroll.getViewport();
        java.awt.Component view = (vp != null) ? vp.getView() : null;
        if (!(view instanceof javax.swing.JComponent)) {
            return;
        }
        final javax.swing.JComponent v = (javax.swing.JComponent) view;
        vp.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int vw = vp.getExtentSize().width;
                if (vw > 0 && v.getPreferredSize().width != vw) {
                    v.setPreferredSize(new java.awt.Dimension(vw, v.getPreferredSize().height));
                    v.revalidate();
                }
            }
        });
    }

    // First JScrollPane inside the window (depth-first search), or null if none.
    private static javax.swing.JScrollPane findScrollPane(Container container) {
        for (Component ch : container.getComponents()) {
            if (ch instanceof javax.swing.JScrollPane) {
                return (javax.swing.JScrollPane) ch;
            }
            if (ch instanceof Container) {
                javax.swing.JScrollPane r = findScrollPane((Container) ch);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    // Width of the vertical scrollbar to reserve if the window contains a JScrollPane
    // (its bar has a fixed width that does NOT scale with the font). 0 if there's no scroll.
    private static int scrollBarAllowance(Container container) {
        javax.swing.JScrollPane sp = findScrollPane(container);
        if (sp == null) {
            return 0;
        }
        int w = sp.getVerticalScrollBar().getPreferredSize().width;
        return w > 0 ? w : 17;
    }

    // Re-syncs each TitledBorder's title font to its container's (already scaled) font.
    // Neither updateFonts nor zoomFonts reach the border's title font, so without this
    // the title would stay at design size while the content scales.
    private static void syncTitledBorderFonts(Container container) {
        if (container instanceof javax.swing.JComponent) {
            javax.swing.border.Border b = ((javax.swing.JComponent) container).getBorder();
            if (b instanceof javax.swing.border.TitledBorder) {
                ((javax.swing.border.TitledBorder) b).setTitleFont(container.getFont());
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                syncTitledBorderFonts((Container) child);
            }
        }
    }

    // Rescales a JLabel's DECORATIVE icon to (resource's natural size x DIALOG_ZOOM).
    // Reuses setScaledIconLabel, which uses Image.SCALE_DEFAULT for GIF (PRESERVES the
    // animation) and SCALE_SMOOTH for everything else: this is the project's standard
    // mechanism for scaling icons, including animated GIFs (e.g. the About dialog's
    // spinning logo). No-op at 1.0 or if there's no resource. For decoration only;
    // graphical content (cards, images) is scaled separately.
    public static void scaleDialogIcon(JLabel label, String resource) {
        if (label == null || resource == null || !isDialogZoomActive()) {
            return;
        }
        java.net.URL url = Helpers.class.getResource(resource);
        if (url == null) {
            return;
        }
        ImageIcon natural = new ImageIcon(url);
        int w = Math.round(natural.getIconWidth() * DIALOG_ZOOM);
        int h = Math.round(natural.getIconHeight() * DIALOG_ZOOM);
        setScaledIconLabel(label, url, w, h);
    }

    // Rescales by the factor the icon (ImageIcon) of every descendant JLabel and
    // AbstractButton (buttons, checkboxes, radios, menu items): chips next to
    // checkboxes, menu icons, button icons, avatars. For the dialog zoom, so icons
    // shrink/grow with the font and the window fits them on pack. Only the MAIN icon
    // (the selected/pressed/disabled states are derived by Nimbus). Not idempotent (one
    // pass per fresh instance, same as updateFonts). No-op at 1.0. WATCH OUT: this is
    // generic, so do NOT use it on dialogs whose CONTENT is an image in a JLabel (e.g.
    // ChatImageDialog): it would scale the image too.
    public static void scaleIcons(Container container, float factor) {
        if (container == null || Math.abs(factor - 1f) < 0.01f) {
            return;
        }
        scaleIconsRec(container, factor);
    }

    private static void scaleIconsRec(Component c, float factor) {
        // Icons set by the setScaled* helpers (avatars, speaker, gear, button icons,
        // etc.) are tagged with "cp_scaled_icon" and are SCALED AT THE SOURCE (their
        // size already comes out x zoom, or is tied to the component's height). Do NOT
        // re-scale them here: avoids double scaling and clipping when they're
        // re-applied (e.g. the speaker toggle). Only the .form's INLINE icons are
        // scaled here.
        boolean managed = (c instanceof javax.swing.JComponent)
                && Boolean.TRUE.equals(((javax.swing.JComponent) c).getClientProperty("cp_scaled_icon"));
        if (!managed) {
            if (c instanceof javax.swing.AbstractButton) {
                javax.swing.Icon scaled = scaleImageIcon(((javax.swing.AbstractButton) c).getIcon(), factor);
                if (scaled != null) {
                    ((javax.swing.AbstractButton) c).setIcon(scaled);
                }
            } else if (c instanceof JLabel) {
                javax.swing.Icon scaled = scaleImageIcon(((JLabel) c).getIcon(), factor);
                if (scaled != null) {
                    ((JLabel) c).setIcon(scaled);
                }
            }
        }
        if (c instanceof javax.swing.JMenu) {
            for (Component ch : ((javax.swing.JMenu) c).getMenuComponents()) {
                scaleIconsRec(ch, factor);
            }
        } else if (c instanceof Container) {
            for (Component ch : ((Container) c).getComponents()) {
                scaleIconsRec(ch, factor);
            }
        }
    }

    // Rescaled copy of the ImageIcon (natural x factor), or null if there's nothing to
    // touch (not an ImageIcon or has no image). SCALE_SMOOTH; animated GIFs are scaled
    // separately with scaleDialogIcon.
    private static javax.swing.Icon scaleImageIcon(javax.swing.Icon icon, float factor) {
        if (!(icon instanceof ImageIcon)) {
            return null;
        }
        ImageIcon ii = (ImageIcon) icon;
        java.awt.Image img = ii.getImage();
        if (img == null || ii.getIconWidth() <= 0 || ii.getIconHeight() <= 0) {
            return null;
        }
        int w = Math.max(1, Math.round(ii.getIconWidth() * factor));
        int h = Math.max(1, Math.round(ii.getIconHeight() * factor));
        return new ImageIcon(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH));
    }

    // Scales a dialog's fonts + decorative icons + TitledBorder titles by DIALOG_ZOOM,
    // WITHOUT resizing the window (each dialog does its own pack/clamp afterwards: when
    // scaling content down, its pack shrinks the window so it fits smaller
    // resolutions). At 100% this only applies the GUI_FONT family (identical to
    // updateFonts(w, GUI_FONT, null), which is what dialogs used to do, so 100% =
    // design). Does NOT touch the TABLE zoom. Do NOT use on dialogs whose content is an
    // image (it would scale content icons too); those go through plain updateFonts.
    // Accepts any Container (Window is a Container) so it can also scale the in-frame
    // overlays that act as lightweight "dialogs" (AUTO mode, straddle), which aren't
    // windows anymore.
    public static void applyDialogZoom(java.awt.Container container) {
        if (container == null) {
            return;
        }
        boolean active = isDialogZoomActive();
        updateFonts(container, GUI_FONT, active ? DIALOG_ZOOM : null);
        if (active) {
            scaleIcons(container, DIALOG_ZOOM);
            syncTitledBorderFonts(container);
        }
    }

    // Applies the base font to ALL descendant components at the SAME size (preserving
    // each one's bold/plain style). Unlike updateFonts (which scales each control's
    // existing size), here everything ends up at the SAME point size: used by the
    // settings dialogs (tabbed or not) for consistent typography. Doesn't reach
    // TitledBorder titles (those are handled separately).
    public static void setUniformFont(Container c, Font base, int size) {
        for (Component child : c.getComponents()) {
            Font f = child.getFont();
            int style = (f != null) ? f.getStyle() : Font.PLAIN;
            child.setFont(base.deriveFont(style, (float) size));
            if (child instanceof Container) {
                setUniformFont((Container) child, base, size);
            }
        }
    }

    /**
     * Returns the largest variant of {@code base_font} (never larger than its
     * own size) whose rendering of {@code text} fits within
     * {@code available_width} pixels, measured with {@code label}'s font
     * metrics. The best size is found by binary search in
     * {@code [min_size, base_size - 1]} (stringWidth is monotonic in size); if
     * the text still overflows at the floor, that floor variant is returned.
     * When the available width is unknown (component not laid out yet) the base
     * font is returned untouched.
     */
    public static Font fitFontToWidth(javax.swing.JComponent label, String text, Font base_font, int available_width, int min_size) {

        if (label == null || text == null || base_font == null || available_width <= 0) {
            return base_font;
        }

        if (label.getFontMetrics(base_font).stringWidth(text) <= available_width) {
            return base_font;
        }

        int lo = min_size;
        int hi = base_font.getSize() - 1;

        if (hi < lo) {
            return base_font;
        }

        // Binary search for the largest size that fits: text width grows monotonically
        // with size, so log2(range) measurements suffice instead of stepping down one
        // point at a time. If nothing fits, the floor size is returned.
        Font best = base_font.deriveFont(base_font.getStyle(), (float) min_size);

        while (lo <= hi) {

            int mid = (lo + hi) >>> 1;

            Font candidate = base_font.deriveFont(base_font.getStyle(), (float) mid);

            if (label.getFontMetrics(candidate).stringWidth(text) <= available_width) {
                best = candidate;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        return best;
    }

    public static Font createAndRegisterFont(InputStream stream) {

        Font font = null;

        // Takes ownership of the stream to guarantee it's closed even if
        // Font.createFont or registerFont throw. Both callers (Init.java:1072 with
        // getResourceAsStream and :1106 with FileInputStream) pass the stream and
        // discard their reference, so closing it here is semantically correct.
        try (InputStream s = stream) {

            font = Font.createFont(Font.TRUETYPE_FONT, s);

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

            ge.registerFont(font);

        } catch (FontFormatException | IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex.getMessage());
        }

        return font;
    }

    public static Properties loadPropertiesFile() {

        createIfNoExistsCoronaDirs();

        File properties = new File(PROPERTIES_FILE);

        if (!properties.exists() || !properties.canRead()) {
            try {
                new File(PROPERTIES_FILE).createNewFile();

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }

        Properties prop = new Properties();

        try (FileInputStream input = new FileInputStream(PROPERTIES_FILE)) {

            prop.load(input);

            return prop;

        } catch (Exception ex) {
            // ANY failure is caught here, not just read errors: a broken Unicode escape
            // in the file throws IllegalArgumentException, which isn't a read failure
            // and used to escape. And this runs in a static initializer, so whatever
            // escapes here isn't a warning, it's a startup that never happens. Returning
            // nothing wasn't an option either: nothing checks that preferences exist, so
            // the first access would blow up just the same.
            //
            // BEFORE CONTINUING, a copy of the unreadable file is saved. Without it,
            // starting up with whatever could be read dooms the rest: the first save
            // (just closing the start window persists the volume) rewrites the WHOLE
            // file and wipes out whatever couldn't be read, including the user's blind
            // structures, with no way back. With the copy, it can always be recovered by hand.
            //
            // What WAS parsed is returned (Properties.load keeps populating until it
            // fails), not an empty object: a broken line at the end still saves
            // everything before it.
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE,
                    "Could not read the preferences file — keeping a copy and starting with what could be read", ex);

            try {
                java.nio.file.Path origen = Paths.get(PROPERTIES_FILE);
                if (Files.exists(origen)) {
                    // With a fixed name the second copy would overwrite the first, and
                    // it's the first one that matters: by the time a second incident
                    // happens, the file already starts out mutilated by the save that
                    // followed the first one.
                    java.nio.file.Path copia = Paths.get(PROPERTIES_FILE + "_" + System.currentTimeMillis() + ".corrupto");
                    Files.copy(origen, copia);
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE,
                            "A copy of the unreadable preferences file was kept at {0}", copia);
                    // All of this happens in a static initializer that runs BEFORE the
                    // log file exists, so these warnings don't end up anywhere. The path
                    // is stashed so startup can surface it once there's somewhere to write it.
                    PROPERTIES_RESCUE_COPY = copia.toString();
                }
            } catch (Exception copyEx) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE,
                        "Could not keep a copy of the unreadable preferences file", copyEx);
            }

            return prop;
        }
    }

    public static String seconds2FullTime(long secs) {

        long uptime = secs;

        long days = TimeUnit.SECONDS.toDays(uptime);

        uptime -= TimeUnit.DAYS.toSeconds(days);

        long hours = TimeUnit.SECONDS.toHours(uptime);

        uptime -= TimeUnit.HOURS.toSeconds(hours);

        long minutes = TimeUnit.SECONDS.toMinutes(uptime);

        uptime -= TimeUnit.MINUTES.toSeconds(minutes);

        // Manual two-digit zero-pad into a StringBuilder instead of 4x String.format (this runs
        // once a second from the game clock). Byte-identical output to the old %02d formatting.
        StringBuilder time = new StringBuilder(days > 0 ? 12 : 8);

        if (days > 0) {
            pad2(time, days).append("D ");
        }

        pad2(time, hours).append(':');
        pad2(time, minutes).append(':');
        pad2(time, uptime);

        return time.toString();
    }

    // Appends v zero-padded to at least two digits (matching "%02d"); v is expected non-negative.
    private static StringBuilder pad2(StringBuilder sb, long v) {
        if (v >= 0 && v < 10) {
            sb.append('0');
        }
        return sb.append(v);
    }

    // Money formatting for the HUD/table (decimal separator per language, K abbreviation
    // for round thousands). The engine's money is double; below the float's precision
    // ceiling the result is the same as always (normal games unchanged).
    public static String money2String(double cantidad) {

        boolean es = GameFrame.LANGUAGE.toLowerCase().equals("es");
        char sep = es ? ',' : '.';

        if (Math.abs(cantidad) < 1000.0) {

            cantidad = Helpers.doubleClean(cantidad);

            return (es ? MONEY_STRIP_2_ES : MONEY_STRIP_2_EN).matcher(moneyFormat(false, sep).format(cantidad)).replaceAll("");

        } else {

            double cantidad_format_k = Helpers.doubleClean(cantidad / 1000.0, 3);

            DecimalFormat df = moneyFormat(true, sep);
            String base = df.format(cantidad_format_k);
            String f = (es ? MONEY_STRIP_3_ES : MONEY_STRIP_3_EN).matcher(base).replaceAll("$1K");

            return f.equals(base) ? (es ? MONEY_STRIP_3F_ES : MONEY_STRIP_3F_EN).matcher(df.format(cantidad)).replaceAll("") : f;
        }

    }

    // money2String is called on the animated counters' hot path (stack/pot/bet at ~60fps,
    // several at once): it used to create a DecimalFormatSymbols + DecimalFormat and
    // COMPILE a regex (String.replaceAll compiles a Pattern) on EVERY call -> a burst of
    // GC garbage on slow PCs. The regexes are now static Patterns (compiled once) and the
    // DecimalFormat is cached per thread (it is NOT thread-safe; called from the EDT and
    // also from logging), rebuilt only if the decimal separator (language) changes.
    // Output is byte-identical to the previous version.
    private static final java.util.regex.Pattern MONEY_STRIP_2_ES = java.util.regex.Pattern.compile(",00$");
    private static final java.util.regex.Pattern MONEY_STRIP_2_EN = java.util.regex.Pattern.compile("\\.00$");
    private static final java.util.regex.Pattern MONEY_STRIP_3_ES = java.util.regex.Pattern.compile("(?:(,[1-9])00$)|,000$");
    private static final java.util.regex.Pattern MONEY_STRIP_3_EN = java.util.regex.Pattern.compile("(?:(\\.[1-9])00$)|\\.000$");
    private static final java.util.regex.Pattern MONEY_STRIP_3F_ES = java.util.regex.Pattern.compile(",000$");
    private static final java.util.regex.Pattern MONEY_STRIP_3F_EN = java.util.regex.Pattern.compile("\\.000$");

    private static final ThreadLocal<DecimalFormat> MONEY_DF_2 = new ThreadLocal<>();
    private static final ThreadLocal<DecimalFormat> MONEY_DF_3 = new ThreadLocal<>();
    private static final ThreadLocal<Character> MONEY_DF_SEP = new ThreadLocal<>();

    // DecimalFormat cached per thread for the requested pattern ("0.00" or "0.000"),
    // rebuilt only if the decimal separator (language) changed since this thread's last use.
    private static DecimalFormat moneyFormat(boolean thousands, char sep) {
        Character cached = MONEY_DF_SEP.get();
        if (cached == null || cached.charValue() != sep) {
            DecimalFormatSymbols sym = new DecimalFormatSymbols();
            sym.setDecimalSeparator(sep);
            MONEY_DF_2.set(new DecimalFormat("0.00", sym));
            MONEY_DF_3.set(new DecimalFormat("0.000", sym));
            MONEY_DF_SEP.set(sep);
        }
        return thousands ? MONEY_DF_3.get() : MONEY_DF_2.get();
    }

    // COALESCED flush of the preferences, for CONTINUOUS controls: a JSpinner with the
    // arrow held down fires one change per repeat, and with savePropertiesFile() each
    // one would rewrite the whole file (I/O on the EDT). Here the flush is rescheduled
    // and only written once, PROPERTIES_FLUSH_DELAY ms after the last change. It's the
    // spinner equivalent of what sliders solve with getValueIsAdjusting(). DISCRETE
    // settings (checkboxes, dropdowns, menus) still save immediately with
    // savePropertiesFile(): one click, one write.
    private static final int PROPERTIES_FLUSH_DELAY = 500;
    // Preferences file's OWN lock. savePropertiesFile used to be "synchronized static",
    // i.e. it took Helpers.class's monitor, which is the de facto database lock
    // (getSQLITE, closeSQLITE and TOFUResolver's four blocks use it). Writing a
    // .properties file has nothing to do with SQLite, and from the shutdown hook that
    // dependency left the exit waiting on whatever transaction was in progress, with no
    // time limit.
    private static final Object PROPERTIES_LOCK = new Object();
    // There are values staged in PROPERTIES that aren't on disk yet. Checking
    // PROPERTIES_FLUSH_TIMER.isRunning() doesn't work: a non-repeating Timer
    // deregisters from the TimerQueue WHEN IT FIRES (TimerQueue.run calls post() and
    // immediately sets delayedTimer = null), and post() only queues the listener via
    // invokeLater, so between firing and the actual flush isRunning() already says
    // false and the hook would skip exactly the save it was meant to protect.
    private static volatile boolean PROPERTIES_DIRTY = false;
    private static final javax.swing.Timer PROPERTIES_FLUSH_TIMER = new javax.swing.Timer(PROPERTIES_FLUSH_DELAY, (java.awt.event.ActionEvent e) -> savePropertiesFile());

    static {
        PROPERTIES_FLUSH_TIMER.setRepeats(false);

        // Closes the coalesced-flush window: if the app closes within that
        // PROPERTIES_FLUSH_DELAY ms, the value would be in memory only and lost. The
        // hook covers a normal close and System.exit (not a hard kill, which carries
        // the same exposure as any partial write). Not registered at design time
        // (NetBeans): there PROPERTIES is an empty object and flushing it would clobber
        // the developer's real file.
        if (!isDesignTime()) {
            try {
                Thread flush_hook = new Thread(() -> {
                    try {
                        if (PROPERTIES_DIRTY) {
                            savePropertiesFile();
                        }
                    } catch (Throwable ignored) {
                        // During shutdown there's no one to notify: a failure here must not
                        // clutter the exit with a stack trace or kill the hook thread.
                    }
                }, "CoronaPoker-Properties-Flush-Hook");
                flush_hook.setDaemon(false);
                Runtime.getRuntime().addShutdownHook(flush_hook);
            } catch (Throwable ignored) {
                // Without the hook, behavior falls back to before: at most the last value
                // of a continuous control moved in the half-second before close is lost.
            }
        }
    }

    public static void savePropertiesFileDeferred() {

        PROPERTIES_DIRTY = true;
        PROPERTIES_FLUSH_TIMER.restart();
    }

    public static void savePropertiesFile() {

        synchronized (PROPERTIES_LOCK) {

            // An immediate flush leaves nothing for a pending one to do: it already
            // writes the WHOLE file, including whatever the continuous control left behind.
            PROPERTIES_FLUSH_TIMER.stop();

            // ATOMIC write: flushed to a temp file and moved into place. Writing in
            // place TRUNCATES first, so a cut mid-write (and this also runs from process
            // shutdown) left the file half-written or empty. It holds the blind
            // structures and saved settings, i.e. the game host's work. The atomic
            // helper already exists in this same file.
            try (java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                // Properties.store does NOT close the OutputStream it's given (JDK
                // contract). Without try-with-resources, every preference change
                // (volume, zoom, sounds, etc.) leaked an FD. In long games with many
                // cumulative changes it became visible in lsof.
                PROPERTIES.store(buffer, null);

                // store() writes ISO-8859-1 and escapes anything non-ASCII (JDK
                // contract), so the text flushed here is exactly what a direct store to
                // the file would write.
                writeStringAtomic(java.nio.file.Paths.get(PROPERTIES_FILE),
                        buffer.toString(java.nio.charset.StandardCharsets.ISO_8859_1));

                // Only once it's actually been written: if the flush fails, the pending
                // state stays pending and the shutdown hook will retry it.
                PROPERTIES_DIRTY = false;

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static String getFechaHoraActual() {

        String format = "dd-MM-yyyy HH:mm:ss";

        return getFechaHoraActual(format);
    }

    public static String getFechaHoraActual(String format) {

        Date currentDate = new Date(System.currentTimeMillis());

        DateFormat df = new SimpleDateFormat(format);

        return df.format(currentDate);
    }

    public static String getMyLocalIP() {
        try {
            String ip;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("google.com", 80));
                ip = socket.getLocalAddress().getHostAddress();
            }
            return ip;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static String getMyPublicIP() {

        String public_ip = null;
        HttpURLConnection con = null;

        try {

            URL url_api = new URL("http://whatismyip.akamai.com/");

            con = (HttpURLConnection) url_api.openConnection();

            con.setUseCaches(false);

            con.setConnectTimeout(COOPERATIVE_SESSION_IO_TIMEOUT_MS);
            con.setReadTimeout(COOPERATIVE_SESSION_IO_TIMEOUT_MS);

            try (InputStream is = con.getInputStream(); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = is.read(buffer)) != -1) {

                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }

                    byte_res.write(buffer, 0, reads);
                }

                public_ip = new String(byte_res.toByteArray(), "UTF-8");

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);

            }

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        return public_ip;
    }

    public static String findFirstRegex(String regex, String data, int group) {
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);

        Matcher matcher = pattern.matcher(data);

        return matcher.find() ? matcher.group(group) : null;
    }

    public static String checkLatestCoronaPokerVersion(String url) {

        String new_version_major = null, new_version_minor = null, current_version_major = null, current_version_minor = null;

        String ret = null;

        try {

            // HttpClient is used (not HttpURLConnection) to bound the TOTAL TIME of the
            // operation, including the connect/DNS resolution phase:
            // HttpURLConnection.setConnectTimeout does NOT cover the DNS lookup, so a
            // machine with broken DNS could leave the check hanging past the nominal
            // timeout. HttpRequest.timeout() imposes a global ceiling on the whole
            // exchange (DNS + connect + response).
            //
            // followRedirects(NEVER): /releases/latest responds with a 302 carrying the
            // version in the Location header, so that header is read without
            // downloading the (heavy) release page HTML. The client is local and
            // deliberately not closed: its threads are daemon and get released by GC; a
            // close() could block waiting on an operation stuck in DNS, exactly what
            // this timeout avoids.
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(HTTP_TIMEOUT))
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int response_code = response.statusCode();

            String latest_version = null;

            if (response_code >= 300 && response_code < 400) {

                String location = response.headers().firstValue("Location").orElse(null);

                if (location != null) {

                    latest_version = findFirstRegex("releases\\/tag\\/v?([0-9]+\\.[0-9]+)", location, 1);
                }

            } else {

                // Fallback: if GitHub stopped redirecting and served the page
                // directly, the body is parsed instead (also bounded by the
                // request's total timeout).
                latest_version = findFirstRegex("releases\\/tag\\/v?([0-9]+\\.[0-9]+)", response.body(), 1);
            }

            // latest_version == null => couldn't be determined (network down,
            // unexpected layout): null is returned so the caller retries and keeps the
            // manual check button visible. "" means "you're up to date".
            if (latest_version != null) {

                new_version_major = findFirstRegex("([0-9]+)\\.[0-9]+", latest_version, 1);

                new_version_minor = findFirstRegex("[0-9]+\\.([0-9]+)", latest_version, 1);

                current_version_major = findFirstRegex("([0-9]+)\\.[0-9]+$", AboutDialog.VERSION, 1);

                current_version_minor = findFirstRegex("[0-9]+\\.([0-9]+)$", AboutDialog.VERSION, 1);

                if (new_version_major != null && (Integer.parseInt(current_version_major) < Integer.parseInt(new_version_major) || (Integer.parseInt(current_version_major) == Integer.parseInt(new_version_major) && Integer.parseInt(current_version_minor) < Integer.parseInt(new_version_minor)))) {

                    ret = new_version_major + "." + new_version_minor;

                } else {

                    ret = "";

                }
            }

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, ex.getMessage());
        }

        return ret;
    }

    //Thanks -> https://stackoverflow.com/a/19746437 (screen 0 is the primary one)
    public static void centrarJFrame(JFrame window) {

        GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();

                int topLeftX, topLeftY, screenX, screenY, windowPosX, windowPosY;

                topLeftX = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().x;
                topLeftY = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().y;

                screenX = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().width;
                screenY = env.getDefaultScreenDevice().getDefaultConfiguration().getBounds().height;

                windowPosX = ((screenX - window.getWidth()) / 2) + topLeftX;
                windowPosY = ((screenY - window.getHeight()) / 2) + topLeftY;

                window.setLocation(windowPosX, windowPosY);
            }
        });
    }

    /**
     * Shows a (typically maximized) JFrame on the monitor described by the
     * given GraphicsConfiguration instead of wherever it was last realized. The
     * start window is created maximized on the PRIMARY monitor and merely
     * hidden between games, so returning to it would always pop it back on the
     * primary screen even when the game and its final screen were on a
     * secondary monitor. A null config (no reference window) just shows the
     * frame where it already was.
     *
     * When {@code maximized} is false and {@code restoreSize} is valid (the
     * size the window had right before it was hidden to launch the game), the
     * window is reopened in NORMAL state at that size, centered on the target
     * monitor {@code gc} (the screen the waiting room is on, which may differ
     * from the one it launched from). Otherwise it is maximized on the target
     * monitor as described below.
     *
     * Same proven technique as GameFrame.placeOnWaitingRoomMonitor: place the
     * window centered on the target monitor while in NORMAL state and maximize
     * BEFORE setVisible — on Windows setExtendedState(MAXIMIZED_BOTH) honors
     * the monitor where the window currently is. Because this frame came back
     * from a maximized+hidden state on the primary monitor (whose retained
     * native placement would otherwise snap it back), setMaximizedBounds pins
     * the maximized rectangle to the target monitor's work area explicitly.
     *
     * The restored (NORMAL) bounds are set to 80% of the target monitor,
     * centered, so that when the user un-maximizes the window it lands at a
     * sane size instead of the default oversized restored bounds that would
     * spill off-screen.
     *
     * setMaximizedBounds is used ONLY to force this initial maximization onto
     * the target monitor and then released (null) on the next EDT cycle:
     * because it persists, leaving it set would pin EVERY later maximization
     * (including the user maximizing by hand on another monitor) to this same
     * rectangle. Clearing it restores the native behavior of maximizing on
     * whatever monitor the window currently sits on.
     */
    public static void showFrameOnScreen(JFrame frame, java.awt.GraphicsConfiguration gc, java.awt.Dimension restoreSize, boolean maximized) {

        GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                if (gc == null) {
                    frame.setVisible(true);
                    return;
                }

                Rectangle screen = gc.getBounds();

                // It wasn't maximized: reopen at its same size, CENTERED on the target
                // screen (the waiting room's, which the user may have moved to another
                // monitor). Only the size is remembered, not the position.
                if (!maximized && restoreSize != null && restoreSize.width > 0 && restoreSize.height > 0) {
                    frame.setExtendedState(JFrame.NORMAL);
                    frame.setBounds(screen.x + (screen.width - restoreSize.width) / 2, screen.y + (screen.height - restoreSize.height) / 2, restoreSize.width, restoreSize.height);
                    frame.setVisible(true);
                    return;
                }

                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

                Rectangle usable = new Rectangle(
                        screen.x + insets.left,
                        screen.y + insets.top,
                        screen.width - insets.left - insets.right,
                        screen.height - insets.top - insets.bottom);

                Rectangle restored = defaultFrameBounds(screen);

                frame.setExtendedState(JFrame.NORMAL);
                frame.setBounds(restored);
                frame.setMaximizedBounds(usable);

                // On Windows (default Direct3D pipeline) setting MAXIMIZED_BOTH BEFORE
                // setVisible sometimes maps the maximized window with a blank
                // back-buffer: content doesn't paint until the first real WM_SIZE
                // (which is why it "fixes itself" on minimize + restore). It's
                // driver/resolution dependent, so it only surfaces on some machines.
                // Showing it in normal state first and maximizing right after, within
                // the same EDT cycle, triggers that real resize that validates the
                // content, with no perceptible flash (the window manager applies the
                // maximization before the first paint). The restored size (80%) is kept
                // because those are the normal bounds already set before maximizing.
                frame.setVisible(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

                // Release the fixed maximization rectangle so future manual
                // maximizations by the user aren't pinned to this monitor; the initial
                // maximization is already applied.
                SwingUtilities.invokeLater(() -> frame.setMaximizedBounds(null));
            }
        });
    }

    /**
     * Maximizes the frame on the given monitor with its restored size at 80%
     * (no prior normal-state snapshot). Shortcut used at startup.
     */
    public static void showFrameOnScreen(JFrame frame, java.awt.GraphicsConfiguration gc) {
        showFrameOnScreen(frame, gc, null, true);
    }

    /**
     * Normal-state bounds retained underneath an initially maximized frame.
     * Keeping this calculation shared prevents a frame restored by the user
     * from falling back to whatever tiny preferred size {@code pack()} found.
     */
    static Rectangle defaultFrameBounds(Rectangle screen) {
        int width = (int) Math.round(screen.width * 0.8);
        int height = (int) Math.round(screen.height * 0.8);
        return new Rectangle(
                screen.x + (screen.width - width) / 2,
                screen.y + (screen.height - height) / 2,
                width,
                height);
    }

    public static void mostrarMensajeInformativo(Container container, String msg, ImageIcon icon) {
        mostrarMensajeInformativo(container, msg, "center", null, icon);
    }

    public static void mostrarMensajeInformativo(Container container, String msg) {
        mostrarMensajeInformativo(container, msg, "center", null, null);
    }

    public static void mostrarMensajeInformativo(Container container, String msg, String align, Integer width, ImageIcon icon) {

        final String mensaje = Translator.translate(msg);

        if (Boolean.getBoolean("coronapoker.qa.suppressDialogs")) {
            LOGGER.log(Level.INFO, "QA dialog suppressed [Info]: {0}", mensaje);
            return;
        }

        if (GameFrame.avisoSonidoOn()) {
            Audio.playWavResource("misc/warning.wav");
        }

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");
        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM * DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(container, label, "Info", JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(Math.round(DIALOG_ICON_SIZE * DIALOG_ZOOM), Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) * DIALOG_ZOOM / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

        } else {
            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(container, label, "Info", JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(Math.round(DIALOG_ICON_SIZE * DIALOG_ZOOM), Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) * DIALOG_ZOOM / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

                }
            });
        }
    }

    public static int mostrarMensajeInformativoSINO(Container container, String msg) {
        return mostrarMensajeInformativoSINO(container, msg, "center", null, null);
    }

    public static int mostrarMensajeInformativoSINO(Container container, String msg, ImageIcon icon) {
        return mostrarMensajeInformativoSINO(container, msg, "center", null, icon);
    }

    // 0=yes, 1=no, 2=cancel
    public static int mostrarMensajeInformativoSINO(Container container, String msg, String align, Integer width, ImageIcon icon) {

        final String mensaje = Translator.translate(msg);

        if (Boolean.getBoolean("coronapoker.qa.suppressDialogs")) {
            LOGGER.log(Level.INFO, "QA dialog suppressed [Info/yes-no]: {0}", mensaje);
            return 1;
        }

        if (GameFrame.avisoSonidoOn()) {
            Audio.playWavResource("misc/warning.wav");
        }

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM * DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(container, label, "Info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(Math.round(DIALOG_ICON_SIZE * DIALOG_ZOOM), Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) * DIALOG_ZOOM / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(container, label, "Info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(Math.round(DIALOG_ICON_SIZE * DIALOG_ZOOM), Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) * DIALOG_ZOOM / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);
                }
            });

            return res[0];

        }

    }

    public static void deleteFile(String filename) {

        try {
            Files.deleteIfExists(Paths.get(filename));

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void mostrarMensajeError(Container container, String msg) {
        mostrarMensajeError(container, msg, "center", null);
    }

    public static void mostrarMensajeError(Container container, String msg, String align, Integer width) {

        final String mensaje = Translator.translate(msg);

        if (Boolean.getBoolean("coronapoker.qa.suppressDialogs")) {
            LOGGER.log(Level.SEVERE, "QA dialog suppressed [Error]: {0}", mensaje);
            return;
        }

        if (GameFrame.avisoSonidoOn()) {
            Audio.playWavResource("misc/warning.wav");
        }

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM * DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(container, label, "ERROR", JOptionPane.ERROR_MESSAGE);

        } else {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(container, label, "ERROR", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

    }

    public static int mostrarMensajeErrorSINO(Container container, String msg) {
        return mostrarMensajeErrorSINO(container, msg, "center", null);
    }

    // 0=yes, 1=no, 2=cancel
    public static int mostrarMensajeErrorSINO(Container container, String msg, String align, Integer width) {

        final String mensaje = Translator.translate(msg);

        if (Boolean.getBoolean("coronapoker.qa.suppressDialogs")) {
            LOGGER.log(Level.SEVERE, "QA dialog suppressed [Error/yes-no]: {0}", mensaje);
            return 1;
        }

        if (GameFrame.avisoSonidoOn()) {
            Audio.playWavResource("misc/warning.wav");
        }

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM * DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(container, label, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(container, label, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                }
            });

            return res[0];

        }
    }

    public static void checkMODVersion(Container container) {

        if (Init.MOD.containsKey("updateurl")) {

            String new_version_major = null, new_version_minor = null, current_version_major = null, current_version_minor = null;

            try {

                URL oracle = new URL((String) Init.MOD.get("updateurl"));

                // Explicit timeouts: openStream() doesn't impose any, so a dead or slow
                // MOD URL would hang the check indefinitely.
                URLConnection oracle_con = oracle.openConnection();
                oracle_con.setUseCaches(false);
                oracle_con.setConnectTimeout(HTTP_TIMEOUT);
                oracle_con.setReadTimeout(HTTP_TIMEOUT);

                ArrayList<String> update_info;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(oracle_con.getInputStream()))) {
                    update_info = new ArrayList<>();
                    String inputline;
                    while ((inputline = in.readLine()) != null) {
                        update_info.add(inputline);
                    }
                }

                String latest_version = findFirstRegex("([0-9]+\\.[0-9]+)", update_info.get(0), 1);

                new_version_major = findFirstRegex("([0-9]+)\\.[0-9]+", latest_version, 1);

                new_version_minor = findFirstRegex("[0-9]+\\.([0-9]+)", latest_version, 1);

                current_version_major = findFirstRegex("([0-9]+)\\.[0-9]+$", (String) Init.MOD.get("version"), 1);

                current_version_minor = findFirstRegex("[0-9]+\\.([0-9]+)$", (String) Init.MOD.get("version"), 1);

                if (new_version_major != null && (Integer.parseInt(current_version_major) < Integer.parseInt(new_version_major) || (Integer.parseInt(current_version_major) == Integer.parseInt(new_version_major) && Integer.parseInt(current_version_minor) < Integer.parseInt(new_version_minor)))) {

                    if (Helpers.mostrarMensajeInformativoSINO(container, Translator.translate("msg.mod_update_available"), new ImageIcon(Init.class.getResource("/images/avatar_default.png"))) == 0) {

                        if (container.equals(VENTANA_INICIO)) {
                            Helpers.GUIRun(new Runnable() {
                                @Override
                                public void run() {

                                    VENTANA_INICIO.getUpdate_label().setText(Translator.translate("update.preparando_actualizacion"));
                                }
                            });
                        }

                        try {
                            String current_jar_path = Helpers.getCurrentJarPath();

                            String updater_jar = Helpers.downloadUpdater();

                            if (updater_jar != null) {

                                String coronapoker_latest_version = Helpers.checkLatestCoronaPokerVersion(AboutDialog.UPDATE_URL);

                                if (coronapoker_latest_version == null || "".equals(coronapoker_latest_version)) {
                                    coronapoker_latest_version = AboutDialog.VERSION;
                                }

                                String[] cmdArr = {Helpers.getJavaBinPath(), "-jar", updater_jar, Helpers.getCurrentJarParentPath() + "/mod", update_info.get(0), current_jar_path, update_info.get(1).replaceAll("___CORONA_VERSION___", coronapoker_latest_version), Init.MOD.containsKey("updatepassword") ? (String) Init.MOD.get("updatepassword") : "", "¡Santiago y cierra, España!"};

                                Runtime.getRuntime().exec(cmdArr);

                                System.exit(0);
                            } else {
                                Helpers.mostrarMensajeError(VENTANA_INICIO, Translator.translate("update.no_se_ha_podido_actualizar_2"));

                            }

                        } catch (Exception ex) {
                            Logger.getLogger(Init.class
                                    .getName()).log(Level.SEVERE, null, ex);
                            Helpers.mostrarMensajeError(VENTANA_INICIO, Translator.translate("update.no_se_ha_podido_actualizar"));
                        }

                        Helpers.openBrowserURL(update_info.get(1));
                    }
                } else if (!container.equals(VENTANA_INICIO)) {
                    Helpers.mostrarMensajeInformativo(container, Translator.translate("msg.mod_already_latest"), new ImageIcon(Init.class.getResource("/images/avatar_default.png")));

                }
            } catch (Exception ex) {
                Logger.getLogger(AboutDialog.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static ConcurrentHashMap<String, Object> loadMOD() {

        if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod"))) {

            Logger.getLogger(Helpers.class
                    .getName()).log(Level.INFO, "Loading MOD...");

            ConcurrentHashMap<String, Object> mod = new ConcurrentHashMap<>();

            try {
                File file = new File(Helpers.getCurrentJarParentPath() + "/mod/mod.xml");
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = (Document) documentBuilder.parse(file);

                mod.put("name", document.getElementsByTagName("name").item(0).getTextContent());
                mod.put("version", document.getElementsByTagName("version").item(0).getTextContent().trim());

                if (document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("adults") != null) {
                    mod.put("adults", Boolean.parseBoolean(document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("adults").getTextContent().trim()));

                }

                if (document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_sounds") != null) {
                    mod.put("fusion_sounds", Boolean.parseBoolean(document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_sounds").getTextContent().trim()));

                }

                if (document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_cinematics") != null) {
                    mod.put("fusion_cinematics", Boolean.parseBoolean(document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_cinematics").getTextContent().trim()));

                }

                if (document.getElementsByTagName("font").item(0) != null) {

                    mod.put("font", document.getElementsByTagName("font").item(0).getTextContent().trim());
                }

                if (document.getElementsByTagName("updateurl").item(0) != null) {

                    mod.put("updateurl", document.getElementsByTagName("updateurl").item(0).getTextContent().trim());
                }

                if (document.getElementsByTagName("updatepassword").item(0) != null) {

                    mod.put("updatepassword", document.getElementsByTagName("updatepassword").item(0).getTextContent().trim());
                }

                //DECKS
                HashMap<String, Object> decks = new HashMap<>();

                // A MOD may bring only sounds, images, or a background and NOT touch the
                // decks: without this guard, a missing <decks> would crash the load (and
                // with it, startup).
                Node decks_node = document.getElementsByTagName("decks").item(0);

                NodeList nodeList = decks_node != null ? decks_node.getChildNodes() : null;

                for (int i = 0; nodeList != null && i < nodeList.getLength(); i++) {

                    if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element el = (Element) nodeList.item(i);
                        HashMap<String, Object> baraja = new HashMap<>();
                        baraja.put("name", el.getElementsByTagName("name").item(0).getTextContent().trim());
                        baraja.put("aspect", el.getElementsByTagName("aspect").item(0) != null ? Float.parseFloat(el.getElementsByTagName("aspect").item(0).getTextContent().trim()) : Helpers.getDeckMODAspectRatio(el.getElementsByTagName("name").item(0).getTextContent().trim()));

                        if (el.getElementsByTagName("sound").item(0) != null) {

                            baraja.put("sound", el.getElementsByTagName("sound").item(0).getTextContent());
                        }

                        decks.put((String) baraja.get("name"), baraja);
                    }
                }

                File decks_folder = new File(Helpers.getCurrentJarParentPath() + "/mod/decks");

                if (decks_folder.isDirectory() && decks_folder.canRead() && decks_folder.listFiles(File::isDirectory).length > 0) {

                    for (final File fileEntry : decks_folder.listFiles(File::isDirectory)) {

                        if (!decks.containsKey(fileEntry.getName())) {
                            HashMap<String, Object> baraja = new HashMap<>();
                            baraja.put("name", fileEntry.getName());
                            baraja.put("aspect", Helpers.getDeckMODAspectRatio(fileEntry.getName()));
                            decks.put((String) baraja.get("name"), baraja);
                        }
                    }
                }

                // NOTE: mod is a ConcurrentHashMap and does NOT allow null values, so a MOD
                // with no decks of its own can't store null here: the key is simply not
                // set, which for readers (get -> null) is exactly the same thing.
                if (!decks.isEmpty()) {
                    mod.put("decks", decks);
                }

                mod.put("init_background", Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/init.png")));

                Logger
                        .getLogger(Helpers.class
                                .getName()).log(Level.INFO, mod.get("name") + " " + mod.get("version") + " loaded {0}", mod);

                return mod;

            } catch (ParserConfigurationException | SAXException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (RuntimeException ex) {
                // A half-broken mod.xml (a missing tag, an aspect that isn't a number) should
                // just leave the MOD unloaded. This runs at startup, where an exception that
                // escapes leaves the window half-built and the splash hanging.
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, "Broken MOD, ignoring it.", ex);
            }
        }

        return null;
    }

    public static Float getDeckMODAspectRatio(String deck_name) {

        if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/decks/" + deck_name))) {

            ImageIcon image = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/decks/" + deck_name + "/A_C.jpg");

            return Helpers.floatClean((float) image.getIconHeight() / image.getIconWidth(), 2);
        }

        return null;
    }

    public static String
            getCurrentJarParentPath() {

        try {
            return new File(Init.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getAbsolutePath();

        } catch (URISyntaxException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static String
            getCurrentJarPath() {

        try {
            return new File(Init.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();

        } catch (URISyntaxException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static String getJavaBinPath() {
        StringBuilder java_bin = new StringBuilder();

        java_bin.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java");

        return java_bin.toString();
    }

    /**
     * Log a cooperative-cancellation event from a pool worker that was
     * waiting/sleeping when the pool was shut down. This is NEVER a real error
     * in CoronaPoker: the only source of {@code InterruptedException} is
     * {@code pool.shutdownNow()} during exit/teardown, and the only source of
     * {@code BrokenBarrierException} is another thread on the same barrier
     * being interrupted (cascade from the same shutdown). Re-raises the
     * interrupt flag for InterruptedException so callers up the stack can
     * observe the cancellation.
     */
    public static void logCooperativeCancellation(Logger logger, String operation, Throwable ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        logger.log(Level.INFO, "{0} cancelled — {1} (cooperative cancellation)",
                new Object[]{operation, ex.getClass().getSimpleName()});
    }

    /**
     * Control-flow throwable signalled by {@link #pausar(long)} when the
     * calling thread has been interrupted (typically by
     * {@code pool.shutdownNow()} during exit/teardown). Lets outer
     * {@code while}/{@code for} loops bail out of cooperative cancellation
     * naturally without each callsite having to check
     * {@code Thread.interrupted()} by hand.
     *
     * <p>
     * Extends {@link Error} ON PURPOSE so the dozens of existing
     * {@code catch (Exception)} blocks (some of which trigger destructive side
     * effects like {@code cancelarManoYDevolverApuestas} or the Crupier's
     * fail-closed recovery transition) do NOT swallow it: the
     * throwable must propagate to the top of the worker's {@code Runnable} and
     * be absorbed silently by the {@code Future}. The interrupt flag is
     * restored before the throw, so any catch site that explicitly wants to
     * react can still observe it. NOT a real error — never logged as SEVERE.
     */
    public static class CooperativeCancellationException extends Error {

        private static final long serialVersionUID = 1L;

        // writableStackTrace=false: stack trace intentionally suppressed.
        // Control-flow throwables don't need traces; suppressing them also
        // means any rare catch (Throwable) that prints the throwable produces
        // a single short line instead of a noisy multi-frame dump.
        public CooperativeCancellationException() {
            super("cooperative cancellation", null, false, false);
        }

        public CooperativeCancellationException(InterruptedException cause) {
            super("cooperative cancellation", cause, false, false);
        }
    }

    private static final ThreadLocal<Boolean> PAUSAR_CANCELLATION_LOGGED
            = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static void logPausarCancellationOnce(String msg) {
        // One-time log per thread. Without this guard, a worker that ignores
        // Thread.interrupted() in a tight while-pausar loop produces thousands
        // of identical INFO lines per second between pool.shutdownNow() and
        // the JVM finally tearing the thread down.
        if (!PAUSAR_CANCELLATION_LOGGED.get()) {
            PAUSAR_CANCELLATION_LOGGED.set(Boolean.TRUE);
            Logger.getLogger(Helpers.class.getName()).log(Level.INFO, msg);
        }
    }

    public static void pausar(long pause) {
        if (Thread.currentThread().isInterrupted()) {
            // Caller looped back into pausar() without observing the interrupt
            // flag. Throwing here breaks the spin and lets the outer try/catch
            // (or the Future submitted to the pool) absorb the cancellation.
            logPausarCancellationOnce("pausar() entered while interrupted — cooperative cancellation");
            throw new CooperativeCancellationException();
        }
        try {
            Thread.sleep(Math.max(pause, 0));

        } catch (InterruptedException ex) {
            // Restore the interrupt flag so callers up the stack can observe
            // cooperative cancellation if they need to.
            Thread.currentThread().interrupt();
            logPausarCancellationOnce("pausar() sleep interrupted — cooperative cancellation");
            // Cooperative cancellation (typically pool shutdown during game exit).
            // Propagated as an unchecked exception so any outer while/for loop
            // bails out automatically — NOT a real error.
            throw new CooperativeCancellationException(ex);
        }
    }

    /**
     * Defensive wrap: if the lambda throws an NPE because
     * GameFrame.getInstance() already returns null (resetInstance() drained the
     * window before the EDT got around to processing it — a normal race in
     * post-MISDEAL / end-of-game cleanup), the lambda is silently discarded. If
     * GameFrame is still alive instead, the NPE is a real bug and is rethrown
     * so the EDT logs it as usual.
     *
     * Without this wrap, every Helpers.GUIRun(() -> GameFrame.getInstance()...)
     * lambda would have to pre-validate the singleton — and there are literally
     * hundreds.
     */
    private static Runnable wrapGuiRunnable(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (NullPointerException ex) {
                if (GameFrame.getInstance() == null) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.INFO,
                            "GUIRun lambda dropped — GameFrame.getInstance() is null (cleanup race after resetInstance)");
                    return;
                }
                throw ex;
            }
        };
    }

    public static void GUIRun(Runnable r) {

        Runnable safe = wrapGuiRunnable(r);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(safe);
        } else {
            safe.run();
        }

    }

    public static void GUIRunAndWait(Runnable r) {

        Runnable safe = wrapGuiRunnable(r);
        try {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeAndWait(safe);
            } else {
                safe.run();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            // Expected during pool shutdown — the worker thread that was
            // waiting for the EDT got interrupted cooperatively.
            Logger.getLogger(Helpers.class.getName()).log(Level.INFO,
                    "GUIRunAndWait interrupted (cooperative cancellation)");
        } catch (InvocationTargetException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    // A "real" click only counts if released INSIDE the component where it was
    // pressed. Used from mouseReleased handlers (not mouseClicked): mouseClicked
    // doesn't fire if the mouse moves a few pixels between press and release, so the
    // click is intermittently lost. Listening on release and checking the bounds
    // replicates a reliable "click" and also lets the user cancel by dragging outside
    // the component before releasing. Does NOT filter by button: callers that need to
    // tell left/right apart check that separately (isLeftMouseButton/isRightMouseButton).
    public static boolean isReleaseInsideComponent(java.awt.event.MouseEvent evt) {
        java.awt.Component c = evt.getComponent();
        return c != null && evt.getX() >= 0 && evt.getY() >= 0
                && evt.getX() < c.getWidth() && evt.getY() < c.getHeight();
    }

    // Shortcut for the common case: a plain left-button click released inside the component.
    public static boolean isRealClick(java.awt.event.MouseEvent evt) {
        return javax.swing.SwingUtilities.isLeftMouseButton(evt) && isReleaseInsideComponent(evt);
    }

    public static Future threadRun(Runnable r) {

        try {
            return THREAD_POOL.submit(r);
        } catch (RejectedExecutionException ex) {
            // The pool is shutting down (game teardown). Mirror logRun's tolerance instead of letting
            // the rejection propagate uncaught out of a UI handler (which could strand a re-entrancy
            // guard or leave a button disabled). Returns null; callers that armed such a guard/button
            // before submitting reset it when this returns null.
            LOGGER.log(Level.FINE, "threadRun rejected — thread pool is shutting down");
            return null;
        }

    }

    /**
     * Starts work whose lifetime belongs to the application, not to one poker
     * table. The per-table executor is deliberately destroyed and recreated by
     * RESET_GAME; startup maintenance and updater network I/O must therefore not
     * participate in that handoff. Daemon status also prevents best-effort
     * maintenance from pinning process shutdown.
     */
    public static Thread applicationTask(Runnable task, String name) {
        if (task == null) {
            throw new IllegalArgumentException("application task is required");
        }
        Thread thread = new Thread(task,
                name == null || name.isBlank() ? "CoronaPoker-application-task" : name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // Enqueues a log task on the single-thread FIFO consumer (LOG_POOL): guarantees
    // print() calls are applied in call order. Defensive against the pool being shut
    // down between games (that game's log has already ended).
    public static void logRun(Runnable r) {

        ExecutorService pool = LOG_POOL;

        if (pool == null) {
            return;
        }

        try {
            pool.execute(r);
        } catch (RejectedExecutionException ignored) {
        }
    }

    // Waits for the log consumer (LOG_POOL) to apply all queued print() calls — drains
    // the FIFO queue. Used before exporting the log to a file (finTransmision) so the
    // .log includes up to the last line, in order, without losing what was just
    // enqueued (footer + end marker). No-op on the EDT: waiting there could deadlock
    // with actualizarCartasPerdedores (holds log_lock inside a GUIRunAndWait that needs
    // the EDT). Best-effort with a time cap.
    public static void logFlush() {

        ExecutorService pool = LOG_POOL;

        if (pool == null || SwingUtilities.isEventDispatchThread()) {
            return;
        }

        try {
            pool.submit(() -> {
            }).get(THREAD_POOL_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public static Future futureRun(Callable c) {

        return THREAD_POOL.submit(c);
    }

    public static void preserveOriginalFontSizes(final Component component) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        preserveOriginalFontSizes(child);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        preserveOriginalFontSizes(child);
                    }
                }
            }

            Helpers.ORIGINAL_FONT_SIZE.put(component, component.getFont().getSize());

        }
    }

    public static void zoomFonts(final Component component, final float zoom_factor, final int font_reference_size, final ConcurrentLinkedQueue<Long> notifier) {

        if (component != null) {

            final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

            int threads = 0;

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        threads++;

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {
                                zoomFonts(child, zoom_factor, font_reference_size, mynotifier);
                            }
                        });
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {

                    if (child instanceof Container) {

                        threads++;

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {
                                zoomFonts(child, zoom_factor, font_reference_size, mynotifier);
                            }
                        });
                    }
                }
            }

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    Font old_font = component.getFont();

                    if (old_font != null) {

                        Font new_font = old_font.deriveFont(old_font.getStyle(), Math.round(font_reference_size * zoom_factor));

                        component.setFont(new_font);

                        component.revalidate();
                        component.repaint();
                    }
                }
            });

            synchronized (mynotifier) {
                while (mynotifier.size() < threads) {
                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
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
    }

    /* public static void ensureRequiredJvmParameters(String[] args, Class<?> mainClass) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();

        // 1. Check standard JVM flags safely by iterating
        boolean hasNativeAccess = false;
        boolean hasDisableAttach = false;

        for (String arg : jvmArgs) {
            if (arg.contains("--enable-native-access=ALL-UNNAMED")) {
                hasNativeAccess = true;
            }
            if (arg.contains("-XX:+DisableAttachMechanism")) {
                hasDisableAttach = true;
            }
        }

        // 2. Check properties directly from the System
        String currentLibPath = System.getProperty("java.library.path");
        boolean hasLibraryPath = currentLibPath != null && currentLibPath.contains(DIR);

        // Check if IPv4 stack is explicitly preferred
        String preferIPv4 = System.getProperty("java.net.preferIPv4Stack");
        boolean hasIPv4Forced = "true".equals(preferIPv4);

        // 3. If all parameters are present, continue normal execution
        if (hasNativeAccess && hasLibraryPath && hasDisableAttach && hasIPv4Forced) {
            return;
        }

        LOGGER.log(Level.INFO, "Missing required JVM security, library, or network parameters. Restarting automatically...");

        try {
            // 4. Build the restart command
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");

            List<String> command = new ArrayList<>();
            command.add(javaBin);

            // Inject the required parameters
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-Djava.library.path=" + DIR);
            command.add("-Djava.net.preferIPv4Stack=true");
            command.add("-XX:+DisableAttachMechanism");

            // Add classpath and main class
            command.add("-cp");
            command.add(classpath);
            command.add(mainClass.getName());

            // Pass along the original application arguments
            if (args != null) {
                command.addAll(Arrays.asList(args));
            }

            // 5. Configure the new process
            ProcessBuilder builder = new ProcessBuilder(command);

            // 6. Sanitize the environment variables to prevent silent agent injection
            Map<String, String> env = builder.environment();
            env.remove("JAVA_TOOL_OPTIONS");
            env.remove("_JAVA_OPTIONS");
            env.remove("JDK_JAVA_OPTIONS");

            // 7. Launch the new process
            builder.inheritIO();
            builder.start();

            // 8. Terminate the current flawed instance
            System.exit(0);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while restarting JVM", e);
            System.exit(1);
        }
    }*/
    public static void zoomFonts(final Component component, final float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        if (component != null) {

            final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

            int threads = 0;

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        threads++;

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {
                                zoomFonts(child, zoom_factor, mynotifier);
                            }
                        });
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {

                    if (child instanceof Container) {

                        threads++;

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {
                                zoomFonts(child, zoom_factor, mynotifier);
                            }
                        });
                    }
                }
            }

            if (Helpers.ORIGINAL_FONT_SIZE.containsKey(component)) {

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        Font old_font = component.getFont();

                        if (old_font != null) {

                            Font new_font = old_font.deriveFont(old_font.getStyle(), Math.round(Helpers.ORIGINAL_FONT_SIZE.get(component) * zoom_factor));

                            component.setFont(new_font);

                            component.revalidate();

                            component.repaint();
                        }
                    }
                });

            }

            synchronized (mynotifier) {
                while (mynotifier.size() < threads) {
                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
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
    }

    public static int getTableColumnIndex(TableModel model, String column_name) {

        for (int i = 0; i < model.getColumnCount(); i++) {
            if (model.getColumnName(i).equals(column_name)) {
                return i;
            }
        }

        return -1;
    }

    public static void disableSortAllColumns(JTable table, TableRowSorter sorter) {

        for (int i = 0; i < table.getModel().getColumnCount(); i++) {
            sorter.setSortable(i, false);
        }

    }

//Thanks to -> https://stackoverflow.com/a/35658165
    public static void resultSetToTableModel(ResultSet rs, JTable table) throws SQLException {
        //Create new table model
        DefaultTableModel tableModel = new DefaultTableModel();

        //Retrieve meta data from ResultSet
        ResultSetMetaData metaData = rs.getMetaData();

        //Get number of columns from meta data
        int columnCount = metaData.getColumnCount();

        //Get all column names from meta data and add columns to table model
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {

            if (metaData.getColumnLabel(columnIndex).equals("TIEMPO")) {
                tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex).replace("_", " ")) + " " + Translator.translate("ui.segundos"));
            } else {
                tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex).replace("_", " ")));
            }
        }

        //Create array of Objects with size of column count from meta data
        Object[] row = new Object[columnCount];

        //Scroll through result set
        while (rs.next()) {
            //Get object from column with specific index of result set to array of objects
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            //Now add row to table model with that array of objects as an argument
            tableModel.addRow(row);
        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                //Now add that table model to your table and you are done :D
                table.setModel(tableModel);

            }
        });
    }

    public static String getSystemInfo() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + " / " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version");
    }

    // Frames a one-line title with box-drawing characters for the log (instead of
    // asterisk strips). Carries a leading "\n" (blank line before it). GameLogDialog
    // detects it by its leading box character: single frame (┌─┐) -> header style
    // (cyan); double frame (╔═╗) -> alert style (red).
    public static String framedTitle(String text) {
        String inner = "  " + text + "  ";
        String rule = "─".repeat(inner.length());
        return "\n┌" + rule + "┐\n│" + inner + "│\n└" + rule + "┘";
    }

    public static String framedTitleAlert(String text) {
        String inner = "  " + text + "  ";
        String rule = "═".repeat(inner.length());
        return "\n╔" + rule + "╗\n║" + inner + "║\n╚" + rule + "╝";
    }

    // Rounds a float to N decimals (HALF_UP), 2 by default. The engine's money is a
    // double, quantized to the cent via doubleClean; floatClean is for NON-money
    // magnitudes rounded to a few decimals (zoom, audio dB, stats percentages, the
    // bot's internal sizing).
    public static float floatClean(float val) {

        return floatClean(val, 2);
    }

    public static float floatClean(float val, int decs) {

        return new BigDecimal(val).setScale(decs, RoundingMode.HALF_UP).floatValue();
    }

    // Compares two floats at cent resolution (both are quantized via floatClean before
    // comparing). The engine's money compares with doubleSecureCompare; this is for
    // float values on the bot's side (sizing/ratios). The "1D" name is historical, from
    // when the chip was 0.1.
    public static int float1DSecureCompare(float val1, float val2) {

        return Float.compare(floatClean(val1), floatClean(val2));
    }

    // Current high-range money quantizer. Double raises the cent-precision ceiling
    // from ~131072 chips (float32) to ~9e13. Uses the precise new BigDecimal(double)
    // constructor, NOT BigDecimal.valueOf(double): on half-cents (2.675/0.145/1.005)
    // the precise one reproduces the float path's rounding (2.67/0.14/1.00) while
    // valueOf drifts (2.68/0.15/1.01).
    public static double doubleClean(double val) {

        return doubleClean(val, 2);
    }

    public static double doubleClean(double val, int decs) {
        if (!Double.isFinite(val)) {
            throw new IllegalArgumentException("money value must be finite");
        }
        return new BigDecimal(val).setScale(decs, RoundingMode.HALF_UP).doubleValue();
    }

    // Compares two money amounts (double) at cent resolution.
    public static int doubleSecureCompare(double val1, double val2) {

        return Double.compare(doubleClean(val1), doubleClean(val2));
    }

    private Helpers() {
    }

    public static class WrapLayoutFocusTraversalPolicyGPT extends FocusTraversalPolicy {

        private ArrayList<Component> components = new ArrayList<>();

        public void addComponent(Component component) {
            components.add(component);
        }

        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
            // Return null if there are no components to navigate
            if (components.isEmpty()) {
                return null;
            }
            int idx = (components.indexOf(aComponent) + 1) % components.size();
            return components.get(idx);
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
            // Return null if there are no components to navigate
            if (components.isEmpty()) {
                return null;
            }
            int idx = components.indexOf(aComponent) - 1;
            if (idx < 0) {
                idx = components.size() - 1;
            }
            return components.get(idx);
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
            // Prevent IndexOutOfBoundsException when the list is empty
            if (components.isEmpty()) {
                return null;
            }
            return components.get(0);
        }

        @Override
        public Component getLastComponent(Container aContainer) {
            // Prevent IndexOutOfBoundsException when the list is empty
            if (components.isEmpty()) {
                return null;
            }
            return components.get(components.size() - 1);
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
            return getFirstComponent(aContainer);
        }
    }

    /**
     * FlowLayout subclass that fully supports wrapping of components. Thanks ->
     * https://stackoverflow.com/a/15961424
     */
    public static class WrapLayout extends FlowLayout {

        /**
         * Constructs a new <code>WrapLayout</code> with a left alignment and a
         * default 5-unit horizontal and vertical gap.
         */
        public WrapLayout() {
            super();
        }

        /**
         * Constructs a new <code>FlowLayout</code> with the specified alignment
         * and a default 5-unit horizontal and vertical gap. The value of the
         * alignment argument must be one of <code>WrapLayout</code>,
         * <code>WrapLayout</code>, or <code>WrapLayout</code>.
         *
         * @param align the alignment value
         */
        public WrapLayout(int align) {
            super(align);
        }

        /**
         * Creates a new flow layout manager with the indicated alignment and
         * the indicated horizontal and vertical gaps.
         * <p>
         * The value of the alignment argument must be one of
         * <code>WrapLayout</code>, <code>WrapLayout</code>, or
         * <code>WrapLayout</code>.
         *
         * @param align the alignment value
         * @param hgap the horizontal gap between components
         * @param vgap the vertical gap between components
         */
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        /**
         * Returns the preferred dimensions for this layout given the
         * <i>visible</i> components in the specified target container.
         *
         * @param target the component which needs to be laid out
         * @return the preferred dimensions to lay out the subcomponents of the
         * specified container
         */
        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        /**
         * Returns the minimum dimensions needed to layout the <i>visible</i>
         * components contained in the specified target container.
         *
         * @param target the component which needs to be laid out
         * @return the minimum dimensions to lay out the subcomponents of the
         * specified container
         */
        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        /**
         * Returns the minimum or preferred dimension needed to layout the
         * target container.
         *
         * @param target target to get layout size for
         * @param preferred should preferred size be calculated
         * @return the dimension to layout the target container
         */
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                //  Each row must fit with the width allocated to the containter.
                //  When the container width = 0, the preferred width of the container
                //  has not yet been calculated so lets ask for the maximum.

                int targetWidth = target.getSize().width;

                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                //  Fit components into the allowed width
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);

                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                        //  Can't add the component to current row. Start a new row.
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        //  Add a horizontal gap for all components after the first
                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                //    When using a scroll pane or the DecoratedLookAndFeel we need to
                //  make sure the preferred size is less than the size of the
                //  target containter so shrinking the container size works
                //  correctly. Removing the horizontal gap is an easy way to do this.
                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);

                if (scrollPane != null && target.isValid()) {
                    dim.width -= (hgap + 1);
                }

                return dim;
            }
        }

        /*
         *  A new row has been completed. Use the dimensions of this row
         *  to update the preferred size for the container.
         *
         *  @param dim update the width and height when appropriate
         *  @param rowWidth the width of the row to add
         *  @param rowHeight the height of the row to add
         */
        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0) {
                dim.height += getVgap();
            }

            dim.height += rowHeight;
        }
    }

    public static class JTextFieldRegularPopupMenu {

        public static void addTo(JTextField txtField) {
            JPopupMenu popup = new JPopupMenu();

            UndoManager undoManager = new UndoManager();
            txtField.getDocument().addUndoableEditListener(undoManager);
            Action undoAction = new AbstractAction(Translator.translate("ui.deshacer")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (undoManager.canUndo() && txtField.isEditable()) {
                        undoManager.undo();
                    } else {
                    }
                }
            };
            Action copyAction = new AbstractAction(Translator.translate("ui.copiar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.copy();
                }
            };
            Action cutAction = new AbstractAction(Translator.translate("ui.cortar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.cut();
                }
            };
            Action pasteAction = new AbstractAction(Translator.translate("ui.pegar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.paste();
                }
            };
            Action selectAllAction = new AbstractAction(Translator.translate("ui.seleccionar_todo")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.selectAll();
                }
            };
            cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control X"));
            copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
            pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));
            selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control A"));

            JMenuItem undo = new LeftClickMenuItem(undoAction);
            undo.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/undo.png")));
            popup.add(undo);

            popup.addSeparator();

            JMenuItem cut = new LeftClickMenuItem(cutAction);
            cut.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/cut.png")));
            popup.add(cut);

            JMenuItem copy = new LeftClickMenuItem(copyAction);
            copy.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/copy.png")));
            popup.add(copy);

            JMenuItem paste = new LeftClickMenuItem(pasteAction);
            paste.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/paste.png")));
            popup.add(paste);

            popup.addSeparator();

            JMenuItem selectAll = new LeftClickMenuItem(selectAllAction);
            selectAll.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/select_all.png")));
            popup.add(selectAll);

            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(DIALOG_ZOOM));
            Helpers.scaleIcons(popup, DIALOG_ZOOM);
            txtField.setComponentPopupMenu(popup);
        }

        public static void addTo(JTextArea txtArea) {
            JPopupMenu popup = new JPopupMenu();
            UndoManager undoManager = new UndoManager();
            txtArea.getDocument().addUndoableEditListener(undoManager);
            Action undoAction = new AbstractAction(Translator.translate("ui.deshacer")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (undoManager.canUndo() && txtArea.isEditable()) {
                        undoManager.undo();
                    } else {
                    }
                }
            };
            Action copyAction = new AbstractAction(Translator.translate("ui.copiar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.copy();
                }
            };
            Action cutAction = new AbstractAction(Translator.translate("ui.cortar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.cut();
                }
            };
            Action pasteAction = new AbstractAction(Translator.translate("ui.pegar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.paste();
                }
            };
            Action selectAllAction = new AbstractAction(Translator.translate("ui.seleccionar_todo")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.selectAll();
                }
            };
            cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control X"));
            copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
            pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));
            selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control A"));
            JMenuItem undo = new LeftClickMenuItem(undoAction);
            undo.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/undo.png")));
            popup.add(undo);

            popup.addSeparator();

            JMenuItem cut = new LeftClickMenuItem(cutAction);
            cut.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/cut.png")));
            popup.add(cut);

            JMenuItem copy = new LeftClickMenuItem(copyAction);
            copy.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/copy.png")));
            popup.add(copy);

            JMenuItem paste = new LeftClickMenuItem(pasteAction);
            paste.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/paste.png")));
            popup.add(paste);

            popup.addSeparator();

            JMenuItem selectAll = new LeftClickMenuItem(selectAllAction);
            selectAll.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/select_all.png")));
            popup.add(selectAll);
            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(DIALOG_ZOOM));
            Helpers.scaleIcons(popup, DIALOG_ZOOM);
            txtArea.setComponentPopupMenu(popup);
        }

        public static void addTo(JEditorPane txtArea) {
            JPopupMenu popup = new JPopupMenu();
            UndoManager undoManager = new UndoManager();
            txtArea.getDocument().addUndoableEditListener(undoManager);
            Action undoAction = new AbstractAction(Translator.translate("ui.deshacer")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (undoManager.canUndo() && txtArea.isEditable()) {
                        undoManager.undo();
                    } else {
                    }
                }
            };
            Action copyAction = new AbstractAction(Translator.translate("ui.copiar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.copy();
                }
            };
            Action cutAction = new AbstractAction(Translator.translate("ui.cortar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.cut();
                }
            };
            Action pasteAction = new AbstractAction(Translator.translate("ui.pegar")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.paste();
                }
            };
            Action selectAllAction = new AbstractAction(Translator.translate("ui.seleccionar_todo")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.selectAll();
                }
            };
            cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control X"));
            copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
            pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));
            selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control A"));
            JMenuItem undo = new LeftClickMenuItem(undoAction);
            undo.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/undo.png")));
            popup.add(undo);

            popup.addSeparator();

            JMenuItem cut = new LeftClickMenuItem(cutAction);
            cut.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/cut.png")));
            popup.add(cut);

            JMenuItem copy = new LeftClickMenuItem(copyAction);
            copy.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/copy.png")));
            popup.add(copy);

            JMenuItem paste = new LeftClickMenuItem(pasteAction);
            paste.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/paste.png")));
            popup.add(paste);

            popup.addSeparator();

            JMenuItem selectAll = new LeftClickMenuItem(selectAllAction);
            selectAll.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/select_all.png")));
            popup.add(selectAll);
            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(DIALOG_ZOOM));
            Helpers.scaleIcons(popup, DIALOG_ZOOM);
            txtArea.setComponentPopupMenu(popup);
        }

        private JTextFieldRegularPopupMenu() {
        }
    }

    public static class TapetePopupMenu {

        public static JMenu BARAJAS_MENU = null;
        public static JMenu TAPETES_MENU = null;
        public static JMenu ZOOM_MENU = null;
        public static JMenu VISTA_MENU = null;
        public static JMenu AYUDA_MENU = null;
        public static JMenuItem MAX_HANDS_MENU;
        public static JMenuItem HALT_GAME_MENU;
        public static JCheckBoxMenuItem AUTO_FULLSCREEN_MENU;
        public static JCheckBoxMenuItem FULLSCREEN_MENU;
        public static JCheckBoxMenuItem RELOJ_MENU;
        public static JCheckBoxMenuItem REBUY_NOW_MENU;
        public static JCheckBoxMenuItem AUTO_REBUY_MENU;
        public static JMenuItem AJUSTES_PARTIDA_MENU;
        public static JCheckBoxMenuItem COMPACTA_MENU;
        public static JCheckBoxMenuItem CONFIRM_MENU;
        public static JCheckBoxMenuItem ANIM_REPARTO_MENU;
        public static JCheckBoxMenuItem ANIM_CIEGAS_DEALER_MENU;
        public static JCheckBoxMenuItem ANIM_APUESTAS_MENU;
        public static JCheckBoxMenuItem ANIM_CONTADORES_MENU;
        public static JCheckBoxMenuItem COSTE_IGUALAR_MENU;
        public static JCheckBoxMenuItem CHAT_IMAGE_MENU;
        public static JCheckBoxMenuItem CINEMATICAS_MENU;
        public static JCheckBoxMenuItem AUTO_ACTION_MENU;
        public static JCheckBoxMenuItem AUTO_ACTION_PERSIST_MENU;
        public static JMenuItem AUTO_CALL_MENU;
        public static JCheckBoxMenuItem MODO_AUTO_CONFIRM_MENU;
        public static JCheckBoxMenuItem LAST_HAND_MENU;
        public static JCheckBoxMenuItem AUTO_ZOOM_MENU;
        public static JRadioButtonMenuItem TAPETE_VERDE;
        public static JRadioButtonMenuItem TAPETE_AZUL;
        public static JRadioButtonMenuItem TAPETE_ROJO;
        public static JRadioButtonMenuItem TAPETE_NEGRO;
        public static JRadioButtonMenuItem TAPETE_MADERA;
        public static JPopupMenu popup = null;

        private static void generarBarajasMenu() {

            BARAJAS_MENU = new JMenu(Translator.translate("menu.barajas"));
            BARAJAS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/baraja.png")));

            HashMap hm = new HashMap<String, Object[]>();

            hm.putAll(Card.BARAJAS);

            TreeMap<String, Object[]> sorted_hm = new TreeMap<>();

            sorted_hm.putAll(hm);

            for (Map.Entry<String, Object[]> entry : sorted_hm.entrySet()) {

                Action barajaAction = new AbstractAction(entry.getKey()) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        for (Component menu : GameFrame.getInstance().getMenu_barajas().getMenuComponents()) {
                            if (((JRadioButtonMenuItem) menu).getText().equals(((JRadioButtonMenuItem) ae.getSource()).getText())) {
                                ((JRadioButtonMenuItem) menu).doClick();
                            }
                        }

                        for (Component menu : BARAJAS_MENU.getMenuComponents()) {
                            ((JRadioButtonMenuItem) menu).setSelected(false);
                        }

                        ((JRadioButtonMenuItem) ae.getSource()).setSelected(true);
                    }
                };

                LeftClickRadioButtonMenuItem menu_item = new LeftClickRadioButtonMenuItem(barajaAction);

                if (((JRadioButtonMenuItem) menu_item).getText().equals(GameFrame.BARAJA)) {
                    ((JRadioButtonMenuItem) menu_item).setSelected(true);
                } else {
                    ((JRadioButtonMenuItem) menu_item).setSelected(false);
                }

                BARAJAS_MENU.add(menu_item);
            }

        }

        private static void generarTapetesMenu() {

            Action tapeteVerdeAction = new AbstractAction(Translator.translate("menu.verde")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_tapete_verde().doClick();
                }
            };

            Action tapeteAzulAction = new AbstractAction(Translator.translate("menu.azul")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_tapete_azul().doClick();
                }
            };

            Action tapeteRojoAction = new AbstractAction(Translator.translate("menu.rojo")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_tapete_rojo().doClick();
                }
            };

            Action tapeteNegroAction = new AbstractAction(Translator.translate("menu.negro")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_tapete_negro().doClick();
                }
            };

            Action tapeteMaderaAction = new AbstractAction(Translator.translate("menu.sin_tapete")) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_tapete_madera().doClick();
                }
            };
            TAPETE_VERDE = new LeftClickRadioButtonMenuItem(tapeteVerdeAction);
            TAPETE_AZUL = new LeftClickRadioButtonMenuItem(tapeteAzulAction);
            TAPETE_ROJO = new LeftClickRadioButtonMenuItem(tapeteRojoAction);
            TAPETE_NEGRO = new LeftClickRadioButtonMenuItem(tapeteNegroAction);
            TAPETE_MADERA = new LeftClickRadioButtonMenuItem(tapeteMaderaAction);
            TAPETES_MENU = new JMenu(Translator.translate("menu.tapetes"));
            TAPETES_MENU.add(TAPETE_VERDE);
            TAPETES_MENU.add(TAPETE_AZUL);
            TAPETES_MENU.add(TAPETE_ROJO);
            TAPETES_MENU.add(TAPETE_NEGRO);
            TAPETES_MENU.add(TAPETE_MADERA);
            TAPETE_VERDE.setSelected(GameFrame.COLOR_TAPETE.startsWith("verde"));
            TAPETE_AZUL.setSelected(GameFrame.COLOR_TAPETE.startsWith("azul"));
            TAPETE_ROJO.setSelected(GameFrame.COLOR_TAPETE.startsWith("rojo"));
            TAPETE_NEGRO.setSelected(GameFrame.COLOR_TAPETE.startsWith("negro"));
            TAPETE_MADERA.setSelected(GameFrame.COLOR_TAPETE.startsWith("madera"));
            TAPETES_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/tapetes.png")));

        }

        public static void addTo(TablePanel tapete, boolean reset) {

            if (popup == null || reset) {

                popup = new JPopupMenu();

                generarBarajasMenu();

                generarTapetesMenu();

                Action shortcutsAction = new AbstractAction(Translator.translate("menu.ver_atajos")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getShortcuts_menu().doClick();
                    }
                };

                Action haltAction = new AbstractAction(Translator.translate("menu.detener_timba")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getHalt_game_menu().doClick();
                    }
                };

                Action exitAction = new AbstractAction(Translator.translate("menu.salir")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getExit_menu().doClick();
                    }
                };

                Action lastHandAction = new AbstractAction(Translator.translate("menu.ultima_mano")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getLast_hand_menu().doClick();
                    }
                };

                Action maxHandsAction = new AbstractAction(Translator.translate("menu.limite_manos")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getMax_hands_menu().doClick();
                    }
                };

                Action registroAction = new AbstractAction(Translator.translate("menu.ver_registro")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getRegistro_menu().doClick();
                    }
                };

                Action screenshotsAction = new AbstractAction(Translator.translate("menu.visor_capturas")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getScreenshots_menu().doClick();
                    }
                };

                Action rulesAction = new AbstractAction(Translator.translate("menu.reglas_robert")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getRobert_rules_menu().doClick();
                    }
                };

                Action jugadasAction = new AbstractAction(Translator.translate("menu.generador_jugadas")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getJugadas_menu().doClick();
                    }
                };

                Action autofullscreenAction = new AbstractAction(Translator.translate("menu.activar_pantalla_completa_al_empezar")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_fullscreen_menu().doClick();
                    }
                };

                Action fullscreenAction = new AbstractAction(Translator.translate("menu.pantalla_completa")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getFull_screen_menu().doClick();
                    }
                };

                Action zoominAction = new AbstractAction(Translator.translate("menu.aumentar_zoom")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getZoom_menu_in().doClick();
                    }
                };

                Action zoomoutAction = new AbstractAction(Translator.translate("menu.reducir_zoom")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getZoom_menu_out().doClick();
                    }
                };

                Action zoomresetAction = new AbstractAction(Translator.translate("menu.reset_zoom")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getZoom_menu_reset().doClick();
                    }
                };

                Action zoomautoAction = new AbstractAction(Translator.translate("menu.auto_ajustar_zoom")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_adjust_zoom_menu().doClick();
                    }
                };

                Action compactAction = new AbstractAction(Translator.translate("menu.vista_compacta")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getCompact_menu().doClick();
                    }
                };

                Action relojAction = new AbstractAction(Translator.translate("menu.mostrar_reloj")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getTime_menu().doClick();
                    }
                };

                Action rebuyNowAction = new AbstractAction(Translator.translate("menu.recomprar_siguiente_mano")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getRebuy_now_menu().doClick();
                    }
                };

                Action autoRebuyAction = new AbstractAction(Translator.translate("menu.recomprar_auto_arruinarse")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_rebuy_menu().doClick();
                    }
                };

                Action ajustesPartidaAction = new AbstractAction(Translator.translate("settings.ajustes")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAjustes_partida_menu().doClick();
                    }
                };

                Action confirmAction = new AbstractAction(Translator.translate("menu.confirmar_todas_las_acciones")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getConfirmar_menu().doClick();
                    }
                };

                Action cinematicasAction = new AbstractAction(Translator.translate("menu.cinematicas")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getMenu_cinematicas().doClick();
                    }
                };

                Action animRepartoAction = new AbstractAction(Translator.translate("menu.efectos_animacion_reparto")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAnim_reparto_menu().doClick();
                    }
                };

                Action animCiegasDealerAction = new AbstractAction(Translator.translate("menu.efectos_animacion_ciegas_dealer")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAnim_ciegas_dealer_menu().doClick();
                    }
                };

                Action animApuestasAction = new AbstractAction(Translator.translate("menu.efectos_animacion_apuestas")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAnim_apuestas_menu().doClick();
                    }
                };

                Action animContadoresAction = new AbstractAction(Translator.translate("menu.efectos_animacion_contadores")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAnim_contadores_menu().doClick();
                    }
                };

                Action chatimageAction = new AbstractAction(Translator.translate("menu.imagenes_chat_juego")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getChat_image_menu().doClick();
                    }
                };

                Action costeIgualarAction = new AbstractAction(Translator.translate("menu.coste_igualar")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getCoste_igualar_menu().doClick();
                    }
                };

                Action autoactAction = new AbstractAction(Translator.translate("menu.botones_auto")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_action_menu().doClick();
                    }
                };

                Action persistAutoAction = new AbstractAction(Translator.translate("menu.persistir_auto")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_action_persist_menu().doClick();
                    }
                };

                Action modoAutoConfirmAction = new AbstractAction(Translator.translate("menu.modo_auto_confirm")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getModo_auto_confirm_menu().doClick();
                    }
                };

                Action autoCallAction = new AbstractAction(Translator.translate("menu.auto_call")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_call_menu().doClick();
                    }
                };

                // === APPEARANCE submenu (display toggles + zoom + decks + mats) ===
                VISTA_MENU = new JMenu(Translator.translate("menu.apariencia"));
                VISTA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/gear.png")));

                FULLSCREEN_MENU = new LeftClickCheckBoxMenuItem(fullscreenAction);
                FULLSCREEN_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/full_screen.png")));
                FULLSCREEN_MENU.setSelected(GameFrame.getInstance().isFull_screen());
                FULLSCREEN_MENU.setEnabled(true);
                VISTA_MENU.add(FULLSCREEN_MENU);

                AUTO_FULLSCREEN_MENU = new LeftClickCheckBoxMenuItem(autofullscreenAction);
                AUTO_FULLSCREEN_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/full_screen_auto.png")));
                AUTO_FULLSCREEN_MENU.setSelected(GameFrame.AUTO_FULLSCREEN);
                AUTO_FULLSCREEN_MENU.setEnabled(true);
                VISTA_MENU.add(AUTO_FULLSCREEN_MENU);

                COMPACTA_MENU = new LeftClickCheckBoxMenuItem(compactAction);
                COMPACTA_MENU.setSelected(GameFrame.VISTA_COMPACTA > 0);
                COMPACTA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/tiny.png")));
                VISTA_MENU.add(COMPACTA_MENU);

                ZOOM_MENU = new JMenu("ZOOM");
                ZOOM_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/zoom.png")));
                JMenuItem zoom_in = new LeftClickMenuItem(zoominAction);
                zoom_in.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/zoom_in.png")));
                ZOOM_MENU.add(zoom_in);
                JMenuItem zoom_out = new LeftClickMenuItem(zoomoutAction);
                zoom_out.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/zoom_out.png")));
                ZOOM_MENU.add(zoom_out);
                JMenuItem zoom_reset = new LeftClickMenuItem(zoomresetAction);
                zoom_reset.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/zoom_reset.png")));
                ZOOM_MENU.add(zoom_reset);
                AUTO_ZOOM_MENU = new LeftClickCheckBoxMenuItem(zoomautoAction);
                AUTO_ZOOM_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/zoom_auto.png")));
                AUTO_ZOOM_MENU.setSelected(GameFrame.AUTO_ZOOM);
                ZOOM_MENU.add(AUTO_ZOOM_MENU);
                VISTA_MENU.add(ZOOM_MENU);

                RELOJ_MENU = new LeftClickCheckBoxMenuItem(relojAction);
                RELOJ_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/clock.png")));
                RELOJ_MENU.setSelected(GameFrame.SHOW_CLOCK);
                VISTA_MENU.add(RELOJ_MENU);

                CINEMATICAS_MENU = new LeftClickCheckBoxMenuItem(cinematicasAction);
                CINEMATICAS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/video.png")));
                CINEMATICAS_MENU.setSelected(GameFrame.CINEMATICAS_PREF);
                VISTA_MENU.add(CINEMATICAS_MENU);

                // "Animation effects" submenu with three combinable effects.
                JMenu efectos_anim_menu = new JMenu(Translator.translate("menu.animacion_de_cartas"));
                efectos_anim_menu.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/fx.png")));

                ANIM_REPARTO_MENU = new LeftClickCheckBoxMenuItem(animRepartoAction);
                ANIM_REPARTO_MENU.setSelected(GameFrame.ANIMACION_REPARTO_PREF);
                efectos_anim_menu.add(ANIM_REPARTO_MENU);

                ANIM_CIEGAS_DEALER_MENU = new LeftClickCheckBoxMenuItem(animCiegasDealerAction);
                ANIM_CIEGAS_DEALER_MENU.setSelected(GameFrame.ANIMACION_CIEGAS_DEALER_PREF);
                efectos_anim_menu.add(ANIM_CIEGAS_DEALER_MENU);

                ANIM_APUESTAS_MENU = new LeftClickCheckBoxMenuItem(animApuestasAction);
                ANIM_APUESTAS_MENU.setSelected(GameFrame.ANIMACION_APUESTAS_PREF);
                efectos_anim_menu.add(ANIM_APUESTAS_MENU);

                ANIM_CONTADORES_MENU = new LeftClickCheckBoxMenuItem(animContadoresAction);
                ANIM_CONTADORES_MENU.setSelected(GameFrame.ANIMACION_CONTADORES_PREF);
                efectos_anim_menu.add(ANIM_CONTADORES_MENU);

                VISTA_MENU.add(efectos_anim_menu);

                CHAT_IMAGE_MENU = new LeftClickCheckBoxMenuItem(chatimageAction);
                CHAT_IMAGE_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/chat_image.png")));
                CHAT_IMAGE_MENU.setSelected(GameFrame.CHAT_IMAGES_INGAME);
                VISTA_MENU.add(CHAT_IMAGE_MENU);

                COSTE_IGUALAR_MENU = new LeftClickCheckBoxMenuItem(costeIgualarAction);
                COSTE_IGUALAR_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/eyes.png")));
                COSTE_IGUALAR_MENU.setSelected(GameFrame.MOSTRAR_COSTE_IGUALAR);
                VISTA_MENU.add(COSTE_IGUALAR_MENU);

                // Decks and mats inside Appearance (previously in a separate
                // Customization submenu, now removed).
                VISTA_MENU.addSeparator();
                VISTA_MENU.add(BARAJAS_MENU);
                VISTA_MENU.add(TAPETES_MENU);

                // === HELP submenu (shortcuts, rules, hand evaluator) ===
                AYUDA_MENU = new JMenu(Translator.translate("menu.ayuda"));
                AYUDA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/info.png")));

                JMenuItem shortcuts = new LeftClickMenuItem(shortcutsAction);
                shortcuts.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/keyboard.png")));
                AYUDA_MENU.add(shortcuts);

                JMenuItem rules = new LeftClickMenuItem(rulesAction);
                rules.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/book.png")));
                AYUDA_MENU.add(rules);

                JMenuItem jugadas = new LeftClickMenuItem(jugadasAction);
                jugadas.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/games.png")));
                AYUDA_MENU.add(jugadas);

                // === ROOT popup ===
                // "Settings" (unified Appearance/Audio/Game dialog) as the FIRST item in
                // the popup, set apart with a separator.
                AJUSTES_PARTIDA_MENU = new LeftClickMenuItem(ajustesPartidaAction);
                AJUSTES_PARTIDA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/gear.png")));
                popup.add(AJUSTES_PARTIDA_MENU);

                popup.addSeparator();

                JMenuItem log = new LeftClickMenuItem(registroAction);
                log.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/log.png")));
                popup.add(log);

                JMenuItem screenshots = new LeftClickMenuItem(screenshotsAction);
                screenshots.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/camera.png")));
                popup.add(screenshots);

                // The "Appearance" submenu (VISTA_MENU) is no longer added to the popup:
                // all its settings live in the "Appearance" tab of the "Settings" dialog.
                // It's still built above because its twin items (FULLSCREEN_MENU,
                // COMPACTA_MENU, etc.) are sync targets from GameFrame.
                popup.addSeparator();

                AUTO_ACTION_MENU = new LeftClickCheckBoxMenuItem(autoactAction);
                AUTO_ACTION_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/auto.png")));
                AUTO_ACTION_MENU.setSelected(GameFrame.AUTO_ACTION_BUTTONS);
                popup.add(AUTO_ACTION_MENU);

                AUTO_CALL_MENU = new LeftClickMenuItem(autoCallAction);
                AUTO_CALL_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/auto.png")));
                AUTO_CALL_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
                popup.add(AUTO_CALL_MENU);

                // Grayed-out sibling: only operable while "AUTO Mode" is active.
                AUTO_ACTION_PERSIST_MENU = new LeftClickCheckBoxMenuItem(persistAutoAction);
                AUTO_ACTION_PERSIST_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/auto.png")));
                AUTO_ACTION_PERSIST_MENU.setSelected(GameFrame.AUTO_ACTION_PERSIST);
                AUTO_ACTION_PERSIST_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
                popup.add(AUTO_ACTION_PERSIST_MENU);

                MODO_AUTO_CONFIRM_MENU = new LeftClickCheckBoxMenuItem(modoAutoConfirmAction);
                MODO_AUTO_CONFIRM_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/auto.png")));
                MODO_AUTO_CONFIRM_MENU.setSelected(GameFrame.MODO_AUTO_CONFIRM);
                MODO_AUTO_CONFIRM_MENU.setEnabled(GameFrame.AUTO_ACTION_BUTTONS);
                popup.add(MODO_AUTO_CONFIRM_MENU);

                // Closes the "AUTO Buttons + children" group with a separator before
                // "Confirm" (the separator above already sits above AUTO Buttons).
                popup.addSeparator();

                // Confirm all actions, right below AUTO Buttons.
                CONFIRM_MENU = new LeftClickCheckBoxMenuItem(confirmAction);
                CONFIRM_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/confirmation.png")));
                CONFIRM_MENU.setSelected(GameFrame.CONFIRM_ACTIONS);
                popup.add(CONFIRM_MENU);

                REBUY_NOW_MENU = new LeftClickCheckBoxMenuItem(rebuyNowAction);
                REBUY_NOW_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/rebuy.png")));
                REBUY_NOW_MENU.setSelected(false);
                REBUY_NOW_MENU.setEnabled(GameFrame.REBUY);
                popup.add(REBUY_NOW_MENU);

                AUTO_REBUY_MENU = new LeftClickCheckBoxMenuItem(autoRebuyAction);
                AUTO_REBUY_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/rebuy.png")));
                AUTO_REBUY_MENU.setSelected(GameFrame.AUTO_REBUY_ON_BROKE);
                AUTO_REBUY_MENU.setEnabled(GameFrame.REBUY);
                popup.add(AUTO_REBUY_MENU);

                // "Last hand" and "Hand limit" are still built the same way (GameFrame
                // syncs their state), but they move: "Hand limit" now lives in the Game
                // tab of the Settings dialog and is no longer added to the popup;
                // "Last hand" moves to the Halt/Exit group.
                LAST_HAND_MENU = new LeftClickCheckBoxMenuItem(lastHandAction);
                LAST_HAND_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/last_hand.png")));
                LAST_HAND_MENU.setSelected(false);

                MAX_HANDS_MENU = new LeftClickMenuItem(maxHandsAction);
                MAX_HANDS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/meter.png")));

                // Help, set apart with its separators.
                popup.addSeparator();
                popup.add(AYUDA_MENU);
                popup.addSeparator();

                // Last hand, Halt game and Exit together (Last hand first).
                popup.add(LAST_HAND_MENU);

                HALT_GAME_MENU = new LeftClickMenuItem(haltAction);
                HALT_GAME_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/stop.png")));
                popup.add(HALT_GAME_MENU);

                JMenuItem exit_menu = new LeftClickMenuItem(exitAction);
                exit_menu.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/close.png")));
                popup.add(exit_menu);

                Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(DIALOG_ZOOM) * 1.10f);
                Helpers.scaleIcons(popup, DIALOG_ZOOM);
                Helpers.translateComponents(popup, false);

            }

            tapete.setComponentPopupMenu(popup);
        }

        private TapetePopupMenu() {
        }
    }

    public static class OSValidator {

        private static final String OS = System.getProperty("os.name").toLowerCase();

        public static boolean isWindows11() {

            return (isWindows() && OS.contains("11"));

        }

        public static boolean isWindows() {

            return (OS.contains("win"));

        }

        public static boolean isMac() {

            return (OS.contains("mac"));

        }

        public static boolean isUnix() {

            return (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);

        }

        public static boolean isSolaris() {

            return (OS.contains("sunos"));

        }

        private OSValidator() {
        }

    }
}
