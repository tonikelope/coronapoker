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
import static com.tonikelope.coronapoker.Helpers.DECK_RANDOM_GENERATOR;
import static com.tonikelope.coronapoker.Init.CACHE_DIR;
import static com.tonikelope.coronapoker.Init.CORONA_DIR;
import static com.tonikelope.coronapoker.Init.DEBUG_DIR;
import static com.tonikelope.coronapoker.Init.GIFSICLE_DIR;
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
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.attribute.PosixFilePermission;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

import org.random.api.RandomOrgClient;
import org.random.api.exception.RandomOrgBadHTTPResponseException;
import org.random.api.exception.RandomOrgInsufficientBitsError;
import org.random.api.exception.RandomOrgInsufficientRequestsError;
import org.random.api.exception.RandomOrgJSONRPCError;
import org.random.api.exception.RandomOrgKeyNotRunningError;
import org.random.api.exception.RandomOrgRANDOMORGError;
import org.random.api.exception.RandomOrgSendTimeoutException;
import org.sqlite.SQLiteConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import static com.tonikelope.coronapoker.Init.SETDPI_DIR;
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
 *
 * @author tonikelope
 *
 * Too much stuff here...
 *
 */
public class Helpers {

    private static final Logger LOGGER = Logger.getLogger(Helpers.class.getName());

    public static volatile ThreadPoolExecutor THREAD_POOL;
    public static final int THREAD_POOL_SHUTDOWN_TIMEOUT = 5;
    public static final String USER_AGENT_WEB_BROWSER = "Mozilla/5.0 (X11; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0";
    public static final String USER_AGENT_CORONAPOKER = "CoronaPoker " + AboutDialog.VERSION + " tonikelope@gmail.com";
    public static final int RANDOMORG_TIMEOUT = 15000;
    public static final int HTTP_TIMEOUT = 15000;
    public static final int CSPRNG = 3;
    public static final int TRNG = 2;
    public static final int TRNG_CSPRNG = 1;
    public static final boolean INFINITE_DECK_SHUFFLE = false;
    public static final ConcurrentHashMap<Component, Integer> ORIGINAL_FONT_SIZE = new ConcurrentHashMap<>();
    public static final String PROPERTIES_FILE = Init.CORONA_DIR + "/coronapoker.properties";
    // Tope superior de tamaño de una línea de comando (post-Base64 + cifrado + HMAC).
    // Cubre con margen el mensaje más grande que el protocolo legítimo puede generar
    // (MEGAPACKET SRA con 52*32 = 1664 bytes + AES padding + IV + HMAC + Base64 ronda
    // los 2-4 KB; RECOVERDATA serializado ronda decenas de KB). 16 MB es ~1000× más
    // que cualquier comando real y corta la vía OOM por línea infinita en readLine.
    public static final int MAX_COMMAND_LINE_CHARS = 16 * 1024 * 1024;
    public static final int DECK_ELEMENTS = 52;
    public static final int MIN_GIF_FRAME_DELAY = 3;
    public static final int DIALOG_ICON_SIZE = 70;
    public static final float MESSAGE_DIALOG_ZOOM = 1.3f;
    public static final ArrayList<String> GIFSICLE_FAST_TEMP_FILES = new ArrayList<>();
    public static ArrayList<String> POKER_QUOTES_ES = new ArrayList<>();
    public static ArrayList<String> POKER_QUOTES_EN = new ArrayList<>();
    public static volatile ImageIcon IMAGEN_BB = null;
    public static volatile ImageIcon IMAGEN_SB = null;
    public static volatile ImageIcon IMAGEN_DEALER = null;
    public static volatile ImageIcon IMAGEN_DEAD_DEALER = null;

    public volatile static int DECK_RANDOM_GENERATOR = Helpers.CSPRNG;
    public volatile static String RANDOM_ORG_APIKEY = "";
    public volatile static SecureRandom CSPRNG_GENERATOR = null;
    public volatile static Properties PROPERTIES = isDesignTime() ? new Properties() : loadPropertiesFile();
    public volatile static Font GUI_FONT = null;
    public volatile static boolean RANDOMORG_ERROR_MSG = false;
    public volatile static boolean GENERATING_GIFSICLE_CACHE = false;
    public volatile static String GIFSICLE_CACHE_ZOOM = "";
    public volatile static long GIFSICLE_CACHE_THREAD;
    public volatile static String WINDOWS_ORIG_DPI = null;

    static {
        if (!isDesignTime()) {

            Helpers.CREATE_THREAD_POOL();

            try {

                POKER_QUOTES_ES = (ArrayList<String>) getResourceTextFileAsList("quotes_ES.txt");
                POKER_QUOTES_EN = (ArrayList<String>) getResourceTextFileAsList("quotes_EN.txt");

                if (POKER_QUOTES_ES != null && POKER_QUOTES_ES.size() != POKER_QUOTES_EN.size()) {
                    LOGGER.log(Level.WARNING, "QUOTES FILES LENGTH DO NOT MATCH. TRUNCATING...");

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

            // Asigna acción con lógica para bloquear clic derecho
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

            // Captura el botón del mouse
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

            // Asigna acción con lógica para bloquear clic derecho
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

            // Captura el botón del mouse
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

            // Asigna acción con lógica para bloquear clic derecho
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

            // Captura el botón del mouse
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

        int c = editor.getComponentCount();

        for (int i = 0; i < c; i++) {
            final Component comp = editor.getComponent(i);

            if (comp instanceof JTextComponent) {

                ((JTextComponent) comp).setUI(new SynthFormattedTextFieldUI() {

                    @Override
                    protected void paint(javax.swing.plaf.synth.SynthContext context, java.awt.Graphics g) {

                        if (comp.isEnabled()) {
                            // Habilitar antialiasing para el texto
                            Graphics2D g2d = (Graphics2D) g;
                            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                            // Fondo personalizado
                            g2d.setColor(background);
                            g2d.fillRect(3, 3, getComponent().getWidth() - 6, getComponent().getHeight() - 6);

                            // Cambiar color del texto
                            g2d.setColor(foreground);
                            g2d.setFont(getComponent().getFont());

                            // Dibujar el texto manualmente
                            String text = ((JTextComponent) comp).getText();
                            FontMetrics fm = g2d.getFontMetrics();

                            int alignment = JTextField.LEFT;  // Valor por defecto

                            // Verificar si el componente es un JTextField
                            if (comp instanceof JTextField) {
                                alignment = ((JTextField) comp).getHorizontalAlignment();
                            }

                            // Calcular la posición X en función de la alineación
                            int x = 5;  // Margen izquierdo por defecto

                            if (alignment == JTextField.RIGHT) {
                                x = getComponent().getWidth() - fm.stringWidth(text) - 5;  // Alinear a la derecha
                            } else if (alignment == JTextField.CENTER) {
                                x = (getComponent().getWidth() - fm.stringWidth(text)) / 2;  // Centrar el texto
                            }

                            int y = (getComponent().getHeight() + fm.getAscent()) / 2 - 2; // Centrado verticalmente

                            // Dibujar el texto
                            g2d.drawString(text, x, y);

                        } else {
                            super.paint(context, g);
                        }
                    }
                ;
            }

        
        );
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

    public static boolean bufferedImagesEqual(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() == img2.getWidth() && img1.getHeight() == img2.getHeight()) {
            for (int x = 0; x < img1.getWidth(); x++) {
                for (int y = 0; y < img1.getHeight(); y++) {
                    if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
        return true;
    }

    public static void forceRepaintComponentNow(JComponent c) {
        Helpers.GUIRun(() -> {

            c.revalidate();
            c.repaint();
        });
    }

    public static String[] runProcess(String[] command) {
        Process process = null;
        try {
            ProcessBuilder processbuilder = new ProcessBuilder(command);
            // Sprint deferred 🟡-25: redirectErrorStream(true) une stderr en
            // stdout. Sin esto, si el binario escribe a stderr y llena el
            // buffer del OS pipe (~64KB típico), el process se bloquea
            // esperando lectura → waitFor() también se cuelga indefinido.
            processbuilder.redirectErrorStream(true);

            process = processbuilder.start();

            long pid = process.pid();

            StringBuilder sb = new StringBuilder();

            // try-with-resources: el BufferedReader anterior nunca se cerraba.
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
            // Si el process arrancó pero falló después (e.g. InterruptedException
            // en waitFor), destruir para evitar zombi.
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

    public static BufferedImage convertToGrayScale(BufferedImage image) {
        ColorConvertOp colorConvert = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        return colorConvert.filter(image, null);
    }

    public static String downloadUpdater() throws IOException {

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

    public static String getSetdpiBinaryPath() {

        String path = null;

        if (Helpers.OSValidator.isWindows()) {

            path = SETDPI_DIR + "/setdpi.exe";

            if (!Files.isReadable(Paths.get(path))) {

                try {

                    Files.createDirectories(Paths.get(SETDPI_DIR));

                    Files.copy(Helpers.class.getResourceAsStream("/setdpi/setdpi.exe"), Paths.get(path), REPLACE_EXISTING);

                } catch (Exception ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                    path = null;
                }
            }

        }

        return path;

    }

    public static String getGifsicleBinaryPath() {

        String path = null;

        if (!Files.isDirectory(Paths.get(CACHE_DIR))) {
            try {
                Files.createDirectories(Paths.get(CACHE_DIR));

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
                return path;
            }
        }

        if (Helpers.OSValidator.isUnix()) {

            path = GIFSICLE_DIR + "/gifsicle";

            if (!Files.isReadable(Paths.get(path))) {
                try {

                    Files.createDirectories(Paths.get(GIFSICLE_DIR));

                    Files
                            .copy(Helpers.class
                                    .getResourceAsStream("/gifsicle/linux/gifsicle"), Paths.get(path), REPLACE_EXISTING);

                    Set<PosixFilePermission> perms = new HashSet<>();

                    perms.add(PosixFilePermission.OWNER_READ);

                    perms.add(PosixFilePermission.OWNER_WRITE);

                    perms.add(PosixFilePermission.OWNER_EXECUTE);

                    Files.setPosixFilePermissions(Paths.get(path), perms);

                } catch (Exception ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                    path = null;
                }

            }

        } else if (Helpers.OSValidator.isWindows()) {

            path = GIFSICLE_DIR + "/gifsicle.exe";

            if (!Files.isReadable(Paths.get(path))) {

                try {

                    Files.createDirectories(Paths.get(GIFSICLE_DIR));

                    if (System.getenv("ProgramFiles(x86)") != null) {
                        Files.copy(Helpers.class
                                .getResourceAsStream("/gifsicle/win/gifsicle.exe"), Paths.get(path), REPLACE_EXISTING);

                    } else {
                        Files.copy(Helpers.class
                                .getResourceAsStream("/gifsicle/win/gifsicle32.exe"), Paths.get(path), REPLACE_EXISTING);

                    }

                } catch (Exception ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.SEVERE, null, ex);
                    path = null;
                }
            }

        } else if (Helpers.OSValidator.isMac()) {

            path = GIFSICLE_DIR + "/gifsicle";

            if (!Files.isReadable(Paths.get(path))) {

                try {
                    //(Extract gifsicle from jar to cache dir)
                    Files.createDirectories(Paths.get(GIFSICLE_DIR));

                    Files
                            .copy(Helpers.class
                                    .getResourceAsStream("/gifsicle/mac/gifsicle_mac"), Paths.get(path), REPLACE_EXISTING);

                    Set<PosixFilePermission> perms = new HashSet<>();

                    perms.add(PosixFilePermission.OWNER_READ);

                    perms.add(PosixFilePermission.OWNER_WRITE);

                    perms.add(PosixFilePermission.OWNER_EXECUTE);

                    Files.setPosixFilePermissions(Paths.get(path), perms);

                } catch (Exception ex) {
                    Logger.getLogger(Helpers.class
                            .getName()).log(Level.WARNING, "To enjoy high quality card animations you need to manually install HOMEBREW + GIFSICLE.\n\n$ /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"\n\n$ brew install gifsicle");
                    path = null;

                }
            }

        } else {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.WARNING, "NO GIFSICLE BINARY AVAILABLE FOR YOUR PLATFORM");
        }

        return path;

    }

    public static void cleanGifsicleFiles() {

        for (String f : GIFSICLE_FAST_TEMP_FILES) {

            try {
                Files.deleteIfExists(Paths.get(f));
            } catch (Exception ex) {
                // Sprint 6 deferred 🟡-35: Defender / AV puede tener bloqueado
                // el .gif justo cuando intentamos borrarlo. En lugar de
                // silenciar (acumulación monotónica en %TEMP%), marcar el
                // fichero para borrado al exit del JVM — la mayoría de los
                // antivirus liberan el handle antes que la JVM termine.
                try {
                    Paths.get(f).toFile().deleteOnExit();
                } catch (Exception markEx) {
                    // best-effort, ya nada más que hacer.
                }
            }

        }

        try {
            Files.walk(Paths.get(CACHE_DIR), FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .filter(a -> (a.getFileName().toString().startsWith("gifsicle_") && !a.getFileName().toString().startsWith("gifsicle_" + String.valueOf(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) + "_")))
                    .forEach(p -> {
                        // forEach delete con fallback a deleteOnExit. File.delete()
                        // devuelve boolean — el original ignoraba. Aquí también lo
                        // tratamos como best-effort pero marcamos exit-cleanup en
                        // caso de fallo (típicamente AV holding).
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

    public static void cleanCacheDIR() {
        try {
            Files.walk(Paths.get(CACHE_DIR), FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void resetWindowsGlobalZoom() {

        String[] output = Helpers.runProcess(new String[]{Helpers.getSetdpiBinaryPath(), "value"});

        try {
            Helpers.WINDOWS_ORIG_DPI = output[1].trim();
            Helpers.runProcess(new String[]{Helpers.getSetdpiBinaryPath(), "100"});

        } catch (Exception ex) {
        }
    }

    public static void restoreWindowsGlobalZoom() {

        if (Helpers.WINDOWS_ORIG_DPI != null) {

            Helpers.runProcess(new String[]{Helpers.getSetdpiBinaryPath(), Helpers.WINDOWS_ORIG_DPI});

        }

    }

    //card_id es baraja_valor_palo, por ejemplo "coronapoker_7_P"
    public static ImageIcon genGifsicleCardAnimation(URL url, float zoom, String card_id) {

        if (!Files.isReadable(Paths.get(CACHE_DIR + "/gifsicle_" + String.valueOf(Helpers.floatClean(zoom, 2)) + "_" + card_id + ".gif")) && Helpers.getGifsicleBinaryPath() != null) {

            genGifsicleCardAnimationsHQCache(url, zoom);

            String filename_orig = System.getProperty("java.io.tmpdir") + "/gifsicle_fast_orig_" + String.valueOf(Helpers.floatClean(zoom, 2)) + "_" + card_id + ".gif";

            String filename_new = System.getProperty("java.io.tmpdir") + "/gifsicle_fast_" + String.valueOf(Helpers.floatClean(zoom, 2)) + "_" + card_id + ".gif";

            Process proc = null;
            try {
                // Files.copy(InputStream,...) NO cierra el InputStream
                // (contrato JDK). Sin try-with-resources, el handle del URL
                // quedaba colgado tras la copia.
                try (InputStream src = url.openStream()) {
                    Files.copy(src, Paths.get(filename_orig), REPLACE_EXISTING);
                }

                String[] command = {Helpers.getGifsicleBinaryPath(), filename_orig, "--scale", String.valueOf(Helpers.floatClean(zoom, 2)), "--colors", "256", "--careful", "--no-loopcount", "-o", filename_new};

                // Sprint deferred 🟡-26: ProcessBuilder con redirectErrorStream
                // + redirectOutput DISCARD para evitar OS pipe buffer fill →
                // waitFor cuelga. Sin esto, gifsicle podía bloquearse en stderr
                // si --careful generaba warnings.
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                proc = pb.start();

                proc.waitFor();

                Files.deleteIfExists(Paths.get(filename_orig));

                GIFSICLE_FAST_TEMP_FILES.add(filename_new);

                return Files.isReadable(Paths.get(filename_new)) ? new ImageIcon(filename_new) : null;

            } catch (Exception ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
                // Si excepción salta entre exec y waitFor (e.g. InterruptedException),
                // destruir el process para evitar zombi.
                if (proc != null && proc.isAlive()) {
                    try {
                        proc.destroy();
                    } catch (Exception ignored) {
                    }
                }
            }

            return null;

        } else if (Files.isReadable(Paths.get(CACHE_DIR + "/gifsicle_" + String.valueOf(Helpers.floatClean(zoom, 2)) + "_" + card_id + ".gif"))) {
            return new ImageIcon(CACHE_DIR + "/gifsicle_" + String.valueOf(Helpers.floatClean(zoom, 2)) + "_" + card_id + ".gif");
        }

        return null;
    }

    //LANCZOS3 GIF ANIMATIONS
    public static void genGifsicleCardAnimationsHQCache(URL url, float zoom) {

        final String zoom_str = String.valueOf(Helpers.floatClean(zoom, 2));

        if (!GENERATING_GIFSICLE_CACHE || !GIFSICLE_CACHE_ZOOM.equals(zoom_str)) {

            GIFSICLE_CACHE_ZOOM = zoom_str;

            GENERATING_GIFSICLE_CACHE = true;

            GIFSICLE_CACHE_THREAD = -1;

            Helpers.threadRun(new Runnable() {
                public void run() {

                    GIFSICLE_CACHE_THREAD = Thread.currentThread().threadId();

                    String base_url = url.toExternalForm().replaceAll("[AKQJ0-9]+_[CDPT]\\.gif$", "");

                    String baraja = url.toExternalForm().replaceAll("^.*?([^/+]+)/gif/[AKQJ0-9]+_[CDPT]\\.gif$", "$1");

                    String[] palos = new String[]{"C", "D", "P", "T"};

                    String[] valores = new String[]{"A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};

                    String gifsicle_bin_path = Helpers.getGifsicleBinaryPath();

                    for (String p : palos) {

                        for (String v : valores) {

                            if (GIFSICLE_CACHE_THREAD != Thread.currentThread().threadId()) {
                                break;
                            }

                            String card_zoom_id = zoom_str + "_" + baraja + "_" + v + "_" + p;

                            String filename_orig = System.getProperty("java.io.tmpdir") + "/gifsicle_" + String.valueOf(Thread.currentThread().threadId()) + "_" + card_zoom_id + ".gif";

                            String filename_new = CACHE_DIR + "/gifsicle_" + card_zoom_id + ".gif";

                            if (!Files.isReadable(Paths.get(filename_new))) {

                                Process proc = null;
                                try {
                                    try (InputStream src = new URL(base_url + v + "_" + p + ".gif").openStream()) {
                                        Files.copy(src, Paths.get(filename_orig), REPLACE_EXISTING);
                                    }

                                    String[] command = {gifsicle_bin_path, filename_orig, "--scale", zoom_str, "--resize-method=lanczos3", "--colors", "256", "--careful", "--no-loopcount", "-o", filename_new};

                                    // Sprint deferred 🟡-26: ProcessBuilder con redirectErrorStream
                                    // + redirectOutput DISCARD. Sin esto, gifsicle podía colgarse
                                    // en stderr (--careful genera warnings que llenan el pipe).
                                    ProcessBuilder pb = new ProcessBuilder(command);
                                    pb.redirectErrorStream(true);
                                    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                                    proc = pb.start();

                                    proc.waitFor();

                                    Files.deleteIfExists(Paths.get(filename_orig));

                                } catch (Exception ex) {
                                    Logger.getLogger(Helpers.class
                                            .getName()).log(Level.SEVERE, null, ex);
                                    // Defensa: destruir process si quedó vivo tras excepción.
                                    if (proc != null && proc.isAlive()) {
                                        try {
                                            proc.destroy();
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    break;
                                }
                            }

                        }
                    }

                    if (GIFSICLE_CACHE_THREAD == Thread.currentThread().threadId()) {
                        GENERATING_GIFSICLE_CACHE = false;
                    }
                }
            });
        }
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

            while (System.nanoTime() < end) {
                LockSupport.parkNanos(end - System.nanoTime());
            }
        }
    }

    public static void barraIndeterminada(JProgressBar barra) {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                barra.setMaximum(1);
                barra.setValue(1);
                barra.setIndeterminate(true);
            }
        });
    }

    public static void resetBarra(JProgressBar barra, int max) {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                barra.setIndeterminate(false);
                barra.setMinimum(0);
                barra.setMaximum(max);
                barra.setValue(max);
            }
        });
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

        // try-with-resources sobre el InputStream del URL: ImageMetadataReader
        // NO cierra el stream que recibe. Cada call (uno por GIF de chat o
        // animación allin) filtraba el handle hasta GC.
        Metadata metadata;
        try (InputStream s = url.openStream()) {
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
                    e.printStackTrace();
                }
            }
            // Unit of time is 10 milliseconds in GIF.
            timeLength *= 10;
        }
        return timeLength;

    }

    public static int getGIFFramesCount(URL url) throws IOException, ImageProcessingException {

        Metadata metadata;
        try (InputStream s = url.openStream()) {
            metadata = ImageMetadataReader.readMetadata(s);
        }

        List<GifControlDirectory> gifControlDirectories
                = (List<GifControlDirectory>) metadata.getDirectoriesOfType(GifControlDirectory.class
                );

        return gifControlDirectories.size();
    }

    public static boolean isImageGIF(URL url) {

        try (InputStream stream = url.openStream();
                ImageInputStream iis = ImageIO.createImageInputStream(stream)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            while (readers.hasNext()) {

                ImageReader read = readers.next();

                try {
                    if ("gif".equals(read.getFormatName().toLowerCase())) {
                        return true;
                    }
                } finally {
                    // Contrato ImageIO: todo ImageReader obtenido vía
                    // getImageReaders DEBE dispose() para liberar buffers
                    // nativos. Función llamada por cada mensaje con imagen
                    // en el chat (centenares por sesión).
                    read.dispose();
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

        return false;
    }

    public static void updateCoronaDialogsFont() {
        UIManager.put("OptionPane.messageFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), 14));
        UIManager.put("OptionPane.buttonFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), 14));
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

    public static void windowAutoFitToRemoveHScrollBar(Window window, JScrollBar hbar, int max_width, float increment) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (hbar.isVisible()) {
                    int i = 1;
                    int new_width;

                    do {

                        new_width = Math.round(window.getWidth() * (1.0f + increment * i));

                        if (new_width < max_width) {
                            window.setSize(new_width, window.getHeight());
                            window.setPreferredSize(window.getSize());
                            window.pack();
                        }

                        i++;

                    } while (hbar.isVisible() && new_width < max_width);
                }

                window.revalidate();
                window.repaint();

            }
        });

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
        // Image.getScaledInstance(0, 0, ...) lanza IllegalArgumentException. Cuando
        // el caller intenta escalar antes de que el contenedor tenga tamaño
        // (típico en zoomIcons disparado desde un re-layout que aún no se ha
        // computado) las dimensiones llegan a 0; sin este guard la excepción
        // sube a EDT y queda como SEVERE en JUL sin que el caller se entere.
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    label.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(new File(path).toURL()) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));

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

    public static void setScaledIconLabel(JLabel label, URL path, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {
                label.setIcon(new ImageIcon(new ImageIcon(path).getImage().getScaledInstance(width, height, Helpers.isImageGIF(path) ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH)));
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

        LOGGER.log(Level.INFO, "Thread pool shutdown — cooperative cancellation notices that follow are expected.");
    }

    public static void CREATE_THREAD_POOL() {
        THREAD_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        LOGGER.log(Level.INFO, "********** NEW THREAD-POOL CREATED. LET'S GO! **********");
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
                // WAL: writers no bloquean a readers (StatsDialog puede leer
                // mientras Crupier escribe acciones de la mano en curso).
                config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
                // NORMAL es seguro con WAL: solo se hace fsync en commits y al
                // checkpoint, no por cada page write. Reduce 3-5× el coste de
                // sqlNewAction/sqlNewHand/sqlNewHandBalance que el Crupier hace
                // varias veces por mano.
                config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);
                // 50 MB de cache (negativo = KB). Default es ~2MB, insuficiente
                // para los JOINs de StatsDialog cuando hay miles de manos.
                config.setCacheSize(-50_000);
                // Defender / antivirus toma share-lock momentáneo en .db-wal
                // durante COMMIT. Sin busy_timeout, SQLITE_BUSY se devuelve
                // instantáneamente y el INSERT/UPDATE se pierde (catch genérico
                // del Crupier lo loguea SEVERE pero no reintenta). 5s cubre
                // share-locks transitorios sin colgar la UI.
                config.setBusyTimeout(5000);

                SQLITE = DriverManager.getConnection("jdbc:sqlite:" + SQL_FILE, config.toProperties());

                return SQLITE;

            } catch (SQLException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
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

    public static void initSQLITE() {
        try {
            Class.forName("org.sqlite.JDBC");

            try (Statement statement = getSQLITE().createStatement()) {
                statement.setQueryTimeout(30);  // set timeout to 30 sec.
                statement.execute("CREATE TABLE IF NOT EXISTS game(id INTEGER PRIMARY KEY, start INTEGER, end INTEGER, play_time INTEGER, server TEXT, players TEXT, buyin INTEGER, sb REAL, blinds_time INTEGER, rebuy INTEGER, last_deck TEXT, blinds_time_type INTEGER)");
                statement.execute("CREATE TABLE IF NOT EXISTS hand(id INTEGER PRIMARY KEY, id_game INTEGER, counter INTEGER, sbval REAL, blinds_double INTEGER, dealer TEXT, sb TEXT, bb TEXT, start INTEGER, end INTEGER, com_cards TEXT, preflop_players TEXT, flop_players TEXT, turn_players TEXT, river_players TEXT, pot REAL, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS action(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, counter INTEGER, round INTEGER, action INTEGER, bet REAL, conta_raise INTEGER, response_time INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS showdown(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, hole_cards TEXT, hand_cards TEXT, hand_val INTEGER, winner INTEGER, pay REAL, profit REAL, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS balance(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, stack REAL, buyin INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS showcards(id INTEGER PRIMARY KEY, id_hand INTEGER, player TEXT, parguela INTEGER, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS permutationkey(id INTEGER PRIMARY KEY, hash TEXT, key TEXT)");
                statement.execute("CREATE TABLE IF NOT EXISTS hand_state(id_game INTEGER PRIMARY KEY, payload TEXT, FOREIGN KEY(id_game) REFERENCES game(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS known_identities(nick TEXT PRIMARY KEY, pubkey BLOB NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, sessions_count INTEGER NOT NULL DEFAULT 0, verified_oob INTEGER NOT NULL DEFAULT 0)");
                // Índices secundarios sobre las FKs que StatsDialog usa en self-joins.
                // SQLite NO auto-indexa FKs. Sin estos, queries como rendimiento /
                // subidasRonda / balance hacen full table scan: O(rows_action *
                // rows_hand). Con miles de manos, segundos → milisegundos.
                statement.execute("CREATE INDEX IF NOT EXISTS idx_hand_game ON hand(id_game)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_action_hand ON action(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_showdown_hand ON showdown(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_balance_hand ON balance(id_hand)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_showcards_hand ON showcards(id_hand)");
                // EC-Identity v1 (commit 6): forensic log of hands whose end-of-hand consensus
                // did not check out unanimously. The hand is paid out regardless — this table is
                // signalético only (spec §6.3 / §6.4). receipts BLOB holds the concatenation of
                // every receipt this peer collected for that hand (each receipt = HAND_ID ||
                // H_final || sig, 16+32+64 = 112 bytes); local_h is this peer's own H_final at
                // dispute time.
                statement.execute("CREATE TABLE IF NOT EXISTS disputed_hands(id INTEGER PRIMARY KEY, id_hand INTEGER NOT NULL, timestamp INTEGER NOT NULL, receipts BLOB NOT NULL, local_h BLOB NOT NULL, reason TEXT, FOREIGN KEY(id_hand) REFERENCES hand(id) ON DELETE CASCADE)");
                //ACTUALIZACIÓN
                try {
                    statement.execute("ALTER TABLE game ADD ugi TEXT");
                } catch (Exception ex) {
                }
                try {
                    statement.execute("ALTER TABLE game ADD local INTEGER DEFAULT 0");
                } catch (Exception ex) {
                } // set timeout to 30 sec.
                try {
                    statement.execute("ALTER TABLE game ADD recover_settings TEXT");
                } catch (Exception ex) {
                }
                try {
                    statement.execute("ALTER TABLE balance ADD rebuy_count INTEGER DEFAULT 0");
                } catch (Exception ex) {
                }
                // EC-Identity v1 (recovery): per-hand cryptographic HAND_ID (16 bytes,
                // base64). Needed to rebuild HandStateChain on recovery — the SQL
                // hand.id is an auto-increment PK and does NOT match the bytes that
                // initHandStateChain feeds into SHA-256(domain || HAND_ID || ...).
                try {
                    statement.execute("ALTER TABLE hand ADD hand_id_b64 TEXT");
                } catch (Exception ex) {
                }
                // EC-Identity v1 (recovery): per-action canonical 92-byte record + Ed25519
                // signature, both base64. Stored so recovery can replay every action
                // through HandStateChain.absorb with the exact bytes that were absorbed
                // pre-crash. Other peers' signatures cannot be re-derived locally
                // (different privkeys), so persisting them is the only way to converge
                // the chain after a recovery.
                try {
                    statement.execute("ALTER TABLE action ADD record_b64 TEXT");
                } catch (Exception ex) {
                }
                try {
                    statement.execute("ALTER TABLE action ADD sig_b64 TEXT");
                } catch (Exception ex) {
                }

            }
        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
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

        try (Statement statement = Helpers.getSQLITE().createStatement()) {
            statement.execute("VACUUM");
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

                // System.arraycopy → memcpy nativo; sustituye 4 bucles for byte-a-byte
                // del código anterior. Cada comando GAME del Crupier (decenas por mano)
                // y cada MEGAPACKET (52*32 = 1664 bytes) pasaba por aquí.
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

                byte[] full_msg = Base64.getDecoder().decode(cadena);

                byte[] hmac = new byte[32];

                byte[] iv = new byte[cifrado.getBlockSize()];

                byte[] cmsg;

                if (hmac_key != null) {

                    cmsg = new byte[full_msg.length - hmac.length - iv.length];

                    // System.arraycopy → memcpy nativo; sustituye 5 bucles
                    // for byte-a-byte del código anterior.
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

                    cmsg = new byte[full_msg.length - iv.length];

                    System.arraycopy(full_msg, 0, iv, 0, iv.length);
                    System.arraycopy(full_msg, iv.length, cmsg, 0, cmsg.length);

                }

                cifrado.init(Cipher.DECRYPT_MODE, aes_key, new IvParameterSpec(iv));

                byte[] msg = cifrado.doFinal(cmsg);

                return new String(msg, "UTF-8");

            } catch (UnsupportedEncodingException | IllegalStateException | InvalidAlgorithmParameterException | KeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
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

        return (command != null && command.charAt(0) == '*') ? Helpers.decryptString(command.trim().substring(1), aes_key, hmac_key) : command;
    }

    /**
     * Escribe {@code data} atómicamente en {@code target}: primero a un tempfile
     * vecino del target, luego {@code Files.move} con ATOMIC_MOVE + REPLACE_EXISTING.
     *
     * Resuelve el problema de Files.writeString por defecto (CREATE +
     * TRUNCATE_EXISTING + WRITE): abre el fichero, lo trunca a 0, y luego
     * escribe. Si el proceso muere entre TRUNCATE y la primera write (corte
     * de luz, BSOD, JVM kill, OS lock por AV), el fichero queda VACÍO en
     * disco — datos perdidos.
     *
     * Con write-tmp + atomic-move, en cualquier instante el target apunta
     * a un fichero COMPLETO (viejo o nuevo, nunca parcial). Si el proceso
     * muere durante el writeString al tmp, el tmp queda parcial pero el
     * target sigue intacto con su valor anterior.
     *
     * Fallback no-atómico en FS que no soportan ATOMIC_MOVE (FAT32 entre
     * volúmenes, casos raros): Files.move sin ATOMIC_MOVE. Aún preserva
     * el invariante "tmp escrito completo antes del move", solo la ventana
     * entre delete-target y rename-tmp puede dejar sistema sin target
     * (mucho más corta que la ventana TRUNCATE-then-write del original).
     *
     * Si el move falla por cualquier motivo, limpia el tmp huérfano.
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
                // Fallback no-atómico (FAT32, etc). Aún strictly mejor que
                // writeString directo porque el tmp ya está completo en disco.
                java.nio.file.Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException moveEx) {
            // Si algo falla, limpia el tmp huérfano antes de propagar.
            try {
                java.nio.file.Files.deleteIfExists(tmp);
            } catch (Exception cleanupEx) {
                // best-effort; el tmp queda para una limpieza posterior.
            }
            throw moveEx;
        }
    }

    /**
     * Sprint 7 telemetría: pinta overlay de bola de latencia + badge de
     * reconexiones en la esquina superior derecha del componente. Se invoca
     * desde paintChildren() de RemotePlayer y LocalPlayer — sin widget Swing
     * adicional, sin layouts. Robusto al zoom (tamaño relativo a la anchura).
     *
     * @param g Graphics del paintChildren del Player.
     * @param panelW ancho del Player JPanel.
     * @param panelH alto del Player JPanel.
     * @param latencyMs última latencia (-1 = unknown).
     * @param recon contador acumulado de reconexiones del peer.
     * @param lastUpdateMs timestamp del último update (0 = nunca).
     */
    public static void paintLatencyDotOverlay(java.awt.Graphics g, int panelW, int panelH,
            int latencyMs, int recon, long lastUpdateMs) {
        if (panelW <= 0 || panelH <= 0) {
            return;
        }
        // Diámetro proporcional al ancho del panel; con un mínimo legible.
        int diameter = Math.max(14, Math.round(panelW * 0.08f));
        int margin = Math.max(4, diameter / 4);
        int x = panelW - diameter - margin;
        int y = margin;

        long age = lastUpdateMs == 0 ? Long.MAX_VALUE : (System.currentTimeMillis() - lastUpdateMs);
        java.awt.Color dotColor = LatencyDot.colorFor(latencyMs, age);

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            // Sombra suave para contraste sobre cualquier fondo
            g2.setColor(new java.awt.Color(0, 0, 0, 100));
            g2.fillOval(x + 1, y + 1, diameter, diameter);
            // Bolita
            g2.setColor(dotColor);
            g2.fillOval(x, y, diameter, diameter);
            // Borde
            g2.setColor(new java.awt.Color(0, 0, 0, 140));
            g2.setStroke(new java.awt.BasicStroke(1.2f));
            g2.drawOval(x, y, diameter, diameter);

            // Badge numérico con contador de reconexiones si > 0
            if (recon > 0) {
                String txt = recon > 9 ? "9+" : String.valueOf(recon);
                int badge = Math.max(10, diameter / 2 + 2);
                int bx = panelW - badge - 1;
                int by = y + diameter - badge / 2;
                g2.setColor(java.awt.Color.WHITE);
                g2.fillOval(bx, by, badge, badge);
                g2.setColor(new java.awt.Color(0, 0, 0, 200));
                g2.drawOval(bx, by, badge, badge);
                g2.setColor(java.awt.Color.BLACK);
                g2.setFont(new java.awt.Font(java.awt.Font.DIALOG,
                        java.awt.Font.BOLD, Math.max(9, badge - 4)));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tx = bx + (badge - fm.stringWidth(txt)) / 2;
                int ty = by + (badge + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Sprint 7 telemetría: payload de una snapshot de latencia/reconexiones
     * que el host emite periódicamente a todos los clientes. Inmutable.
     */
    public static final class TelemetryFrame {

        /** Timestamp del host al emitir (System.currentTimeMillis). */
        public final long serverTimestampMs;
        /** nick (canonical, NFC) → [lat1_ms, lat2_ms, reconnection_count]. */
        public final java.util.Map<String, int[]> perPeer;

        public TelemetryFrame(long serverTimestampMs, java.util.Map<String, int[]> perPeer) {
            this.serverTimestampMs = serverTimestampMs;
            this.perPeer = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(perPeer));
        }
    }

    /**
     * Codifica un TelemetryFrame al wire format usado por el broadcast
     * TELEMETRY del Sprint 7. Formato:
     *
     *   <ts>#<b64nick>|<lat1>/<lat2>/<recon>@<b64nick>|<lat1>/<lat2>/<recon>@...
     *
     * - ts es System.currentTimeMillis del host al emitir.
     * - nick va Base64-encoded en UTF-8 para evitar conflictos con los
     *   separadores #/@/| (los nicks pueden contener cualquier char).
     *   IMPORTANTE: el separador nick/valores es '|' (NO '='), porque '='
     *   es padding válido de Base64 y mezclarlo confundiría al parser.
     * - lat1, lat2 son ms. -1 = no medido / timeout.
     * - recon es el contador acumulado de reconexiones de ese peer.
     *
     * El caller envolverá el resultado en "GAME#<id>#TELEMETRY#<payload>"
     * antes del encryptCommand habitual.
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
                // UTF-8 está garantizado por Java; este catch es defensivo.
                sb.append(java.util.Base64.getEncoder().encodeToString(e.getKey().getBytes()));
            }
            sb.append('|');
            sb.append(v[0]).append('/').append(v[1]).append('/').append(v[2]);
        }
        return sb.toString();
    }

    /**
     * Decodifica el wire format del Sprint 7 TELEMETRY. Tolera entradas
     * mal formadas (skip silencioso de entries con campos faltantes o
     * sin parsear como int) para que un peer hostil no pueda romper el
     * cliente con un payload corrupto.
     *
     * Devuelve null si el payload no tiene al menos el ts inicial.
     */
    public static Helpers.TelemetryFrame decodeTelemetry(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        int firstHash = payload.indexOf('#');
        long ts;
        String entries;
        if (firstHash < 0) {
            // Solo ts sin entries (broadcast vacío).
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
                // Separador nick/valores es '|', NO '='. Razón: '=' es padding
                // de Base64 y mezclarlo confundiría al parser.
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
     * Ejecuta {@code action} en EDT en cuanto {@code c} tenga altura > 0
     * (layout aplicado). Si ya está laid out, ejecuta inmediatamente. Si no,
     * instala un ComponentListener one-shot que se auto-remueve tras el primer
     * resize con altura > 0.
     *
     * Reemplaza el anti-patrón {@code Helpers.threadRun(() -> { while (c.getHeight() == 0)
     * Helpers.pausar(125); Helpers.GUIRun(action); })} que polleaba con sleep
     * el estado event-driven de Swing — cero CPU mientras se espera, cero
     * latencia al despertar.
     *
     * Apto solo cuando {@code action} no requiere mantener un lock externo
     * durante su ejecución (corre directamente en EDT). Si se necesita lock
     * + GUIRunAndWait, usar {@link #awaitFirstLayout(javax.swing.JComponent)}
     * desde un thread off-EDT.
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
            java.awt.event.ComponentListener[] holder = new java.awt.event.ComponentListener[1];
            holder[0] = new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (c.getHeight() > 0) {
                        c.removeComponentListener(holder[0]);
                        action.run();
                    }
                }
            };
            c.addComponentListener(holder[0]);
        });
    }

    /**
     * Bloquea el thread actual (que NO debe ser EDT) hasta que {@code c} tenga
     * altura > 0. Usar cuando el caller necesita mantener un lock externo
     * durante el subsiguiente GUIRunAndWait — el lock no puede tomarse desde
     * EDT porque otro thread non-EDT puede estar reteniéndolo y bloqueado
     * esperando a EDT, lo que produciría deadlock.
     *
     * Si ya está laid out, retorna sin bloquear.
     */
    public static void awaitFirstLayout(javax.swing.JComponent c) throws InterruptedException {
        if (c == null || c.getHeight() > 0) {
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.awt.event.ComponentListener[] holder = new java.awt.event.ComponentListener[1];
        holder[0] = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (c.getHeight() > 0 && latch.getCount() > 0) {
                    latch.countDown();
                    c.removeComponentListener(holder[0]);
                }
            }
        };
        GUIRun(() -> {
            c.addComponentListener(holder[0]);
            // Re-check post-install para cubrir la race en la que el layout
            // se aplicó entre el check inicial y addComponentListener (otro
            // evento EDT pendiente que dispara setBounds).
            if (c.getHeight() > 0 && latch.getCount() > 0) {
                latch.countDown();
                c.removeComponentListener(holder[0]);
            }
        });
        latch.await();
    }

    /**
     * Sanea un nick para uso seguro como SEGMENTO de filename en disco.
     * Defensa contra path traversal cuando el nick proviene de un peer remoto
     * (host hostil enviando NEWUSER/USERSLIST con nick "../../../../foo") y
     * contra nombres reservados de Windows ("CON", "NUL", etc.) que harían
     * fallar FileOutputStream silenciosamente.
     *
     * Reglas:
     *   - Solo conserva [A-Za-z0-9_-]. Cualquier otro char (incluido '.', '/',
     *     '\', ':', control chars, Unicode) se sustituye por '_'.
     *   - Trunca a 32 chars máximo (los logs y avatares no necesitan más).
     *   - Nombres reservados Windows (CON/PRN/AUX/NUL/COM[1-9]/LPT[1-9],
     *     case-insensitive) se prefijan con '_' para evitar AccessDeniedException.
     *   - null o cadena vacía tras sanitización devuelven "user".
     *
     * NOTA: el resultado NO es un identificador único (dos nicks distintos
     * pueden colisionar tras la sanitización). Los call sites que necesitan
     * unicidad deben añadir su propio sufijo (file_id aleatorio, hash, etc.)
     * — el helper solo garantiza que el segmento sea filesystem-safe.
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
        // Trim leading dashes (cosmético — los nombres tipo "-rf" parecen flags)
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
     * Reemplazo acotado de {@link java.io.BufferedReader#readLine()}. Mismo
     * contrato (null si EOF antes de leer nada, trim de CR-LF) pero ABORTA con
     * IOException si la línea acumula más de {@code maxChars} caracteres antes
     * del salto de línea. Defensa contra un peer que abre canal y envía bytes
     * sin '\n' hasta forzar OOM en el receptor (readLine estándar crece el
     * buffer interno sin límite).
     *
     * El cap se mide en caracteres del Reader (post-decode UTF-8). La aproximación
     * char≈byte es válida para nuestro wire format (Base64 + dígitos + '#'),
     * todo ASCII.
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
     * Derives a 64-byte channel secret from the raw ECDH shared secret. If a password is
     * provided, the secret is bound to it via HMAC-SHA512, blocking passive MITM attacks
     * for password-protected games.
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
     * Estimates the entropy of a password in bits using the character-class heuristic:
     * alphabet size is the sum of the sizes of the character classes present, and the
     * entropy is length * log2(alphabet). Used by the password strength warning at
     * game creation. This is a floor estimate that does not penalize dictionary words
     * or common patterns.
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

    public static void screenshot(Rectangle rectangle, Integer delay) {
        try {
            Robot robot = new Robot();

            if (delay != null) {
                Helpers.pausar(delay);
            }

            BufferedImage image = robot.createScreenCapture(rectangle);
            try {
                ImageIO.write(image, "png", new File(SCREENSHOTS_DIR + "/coronapoker_screenshot_" + String.valueOf(System.currentTimeMillis()) + ".png"));
            } finally {
                // Captura 4K = ~33 MB de pixel data nativa. Sin flush, espera al GC.
                image.flush();
            }

        } catch (Exception ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void createIfNoExistsCoronaDirs() {

        String[] dirs = new String[]{CORONA_DIR, LOGS_DIR, DEBUG_DIR, SCREENSHOTS_DIR, CACHE_DIR, CHAT_IMAGE_CACHE}; //OJO AL ORDEN POR EL CORONA_DIR!

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
    }

    public static void copyTextToClipboard(String text) {

        StringSelection stringSelection = new StringSelection(text);
        Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
        clpbrd.setContents(stringSelection, null);

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
     * Converts a given Image into a BufferedImage
     * https://stackoverflow.com/a/13605411
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    public static BufferedImage toBufferedImage(Image img) {
        // Verificar si la imagen es nula
        if (img == null) {
            throw new IllegalArgumentException("La imagen no puede ser nula.");
        }

        // Si la imagen ya es un BufferedImage, devolverla directamente
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Crear un BufferedImage con transparencia
        BufferedImage bimage = new BufferedImage(
                img.getWidth(null),
                img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB
        );

        // Dibujar la imagen en el BufferedImage
        Graphics2D g2d = bimage.createGraphics();
        try {
            g2d.drawImage(img, 0, 0, null);
        } finally {
            g2d.dispose(); // Asegurarse de liberar recursos
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

    public static BufferedImage setColorImageOpacity(BufferedImage source, float opacity) {

        float[] scales = {1f, 1f, 1f, opacity};
        float[] offsets = new float[4];
        RescaleOp rop = new RescaleOp(scales, offsets, null);

        return rop.filter(source, null);
    }

    //Thanks -> https://stackoverflow.com/a/7603815
    public static BufferedImage makeImageRoundedCorner(Image image, int cornerRadius) {
        // Obtener las dimensiones de la imagen original
        int width = image.getWidth(null);
        int height = image.getHeight(null);

        // Crear una nueva imagen con transparencia
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = output.createGraphics();

        try {
            // Habilitar antialiasing para bordes suaves
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dibujar un rectángulo redondeado blanco como máscara
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, cornerRadius, cornerRadius));

            // Configurar el modo de composición para aplicar la máscara
            g2d.setComposite(AlphaComposite.SrcIn);
            g2d.drawImage(image, 0, 0, null);
        } finally {
            // Liberar recursos nativos del Graphics2D. SIEMPRE — sin try/finally,
            // un OOM o IllegalArgumentException entre createGraphics y dispose
            // dejaba colgado el contexto nativo. Llamada en TODA carga de carta
            // (~104 invocaciones por cambio de zoom/baraja).
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

    public static void setTranslatedToolTip(Component c, String key) {
        if (c instanceof JComponent && key != null) {
            JComponent jc = (JComponent) c;
            jc.setToolTipText(Translator.translate(key));
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
                    jc.setToolTipText(Translator.translate(tooltipKey, force));
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

    public static Font createAndRegisterFont(InputStream stream) {

        Font font = null;

        // Toma ownership del stream para garantizar close incluso si
        // Font.createFont o registerFont lanzan. Los dos callers
        // (Init.java:1072 con getResourceAsStream y :1106 con
        // FileInputStream) pasan el stream y descartan la referencia,
        // así que cerrarlo aquí es semánticamente correcto.
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

        try (FileInputStream input = new FileInputStream(PROPERTIES_FILE)) {

            Properties prop = new Properties();

            if (input != null) {
                prop.load(input);
            }

            return prop;

        } catch (IOException ex) {
            return null;
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

        String time = "";

        if (days > 0) {
            time += String.format("%02d", days) + "D ";
        }

        time += String.format("%02d", hours) + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", uptime);

        return time;
    }

    public static String float2String(float cantidad) {

        if (Math.abs(cantidad) < 1000f) {

            cantidad = Helpers.floatClean(cantidad);

            DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();

            DecimalFormat df;

            if (GameFrame.LANGUAGE.toLowerCase().equals("es")) {

                otherSymbols.setDecimalSeparator(',');

                df = new DecimalFormat("0.00", otherSymbols);

                return df.format(cantidad).replaceAll("\\,00$", "");

            } else {

                otherSymbols.setDecimalSeparator('.');

                df = new DecimalFormat("0.00", otherSymbols);

                return df.format(cantidad).replaceAll("\\.00$", "");
            }

        } else {

            float cantidad_format_k = Helpers.floatClean(cantidad / 1000f, 3);

            DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();

            DecimalFormat df;

            if (GameFrame.LANGUAGE.toLowerCase().equals("es")) {

                otherSymbols.setDecimalSeparator(',');

                df = new DecimalFormat("0.000", otherSymbols);

                String f = df.format(cantidad_format_k).replaceAll("(?:(\\,[1-9])00$)|\\,000$", "$1K");

                return f.equals(df.format(cantidad_format_k)) ? df.format(cantidad).replaceAll("\\,000$", "") : f;

            } else {

                otherSymbols.setDecimalSeparator('.');

                df = new DecimalFormat("0.000", otherSymbols);

                String f = df.format(cantidad_format_k).replaceAll("(?:(\\.[1-9])00$)|\\.000$", "$1K");

                return f.equals(df.format(cantidad_format_k)) ? df.format(cantidad).replaceAll("\\.000$", "") : f;
            }

        }

    }

    public synchronized static void savePropertiesFile() {

        try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
            // Properties.store NO cierra el OutputStream que recibe (contrato JDK).
            // Sin try-with-resources, cada cambio de preferencia (volumen, zoom,
            // sonidos, etc.) filtraba un FD. En partidas largas con muchos cambios
            // acumulativos llegaba a ser visible en lsof.
            PROPERTIES.store(fos, null);

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class
                    .getName()).log(Level.SEVERE, null, ex);
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

            try (InputStream is = con.getInputStream(); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = is.read(buffer)) != -1) {

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

        URL mb_url;

        HttpURLConnection con = null;

        try {

            mb_url = new URL(url);

            con = (HttpURLConnection) mb_url.openConnection();

            con.setUseCaches(false);

            con.setConnectTimeout(HTTP_TIMEOUT);

            con.setReadTimeout(HTTP_TIMEOUT);

            try (BufferedInputStream bis = new BufferedInputStream(con.getInputStream()); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = bis.read(buffer)) != -1) {

                    byte_res.write(buffer, 0, reads);
                }

                String latest_version_res = new String(byte_res.toByteArray(), "UTF-8");

                String latest_version = findFirstRegex("releases\\/tag\\/v?([0-9]+\\.[0-9]+)", latest_version_res, 1);

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
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        return ret;
    }

    public static byte[] getRandomOrgBytes(int nbytes) {

        LOGGER.log(Level.INFO, () -> "Getting TRUE RANDOM [" + String.valueOf(nbytes) + "] BYTES SEED from Random.org...");

        byte[] seed = new byte[nbytes];

        HttpURLConnection con = null;

        try {
            // Build the URL for raw bytes as requested
            URL url_api = new URL("https://www.random.org/cgi-bin/randbyte?nbytes=" + nbytes + "&format=f");

            con = (HttpURLConnection) url_api.openConnection();

            // Use your original User-Agent and cache settings
            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_CORONAPOKER);
            con.setUseCaches(false);

            // Read the binary stream directly
            try (BufferedInputStream bis = new BufferedInputStream(con.getInputStream()); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int reads;
                while ((reads = bis.read(buffer)) != -1) {
                    byte_res.write(buffer, 0, reads);
                }

                // Return the downloaded bytes if successful
                return byte_res.toByteArray();
            }

        } catch (Exception ex) {
            // Log failure and proceed to local fallback
            LOGGER.log(Level.SEVERE, "Network random source failed, using fallback", ex);

            // Fallback using your existing CSPRNG_GENERATOR logic
            if (Helpers.CSPRNG_GENERATOR != null) {
                Helpers.CSPRNG_GENERATOR.nextBytes(seed);
            } else {
                // Safety net if the generator is null
                new java.security.SecureRandom().nextBytes(seed);
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        return seed;
    }

    public static Integer[] getRandomIntegerSequence(int method, int min, int max) throws Exception {

        return getRandomIntegerSequence(method, min, max, null);

    }

    public static Integer[] getRandomIntegerSequence(int method, Integer[] init) throws Exception {

        return getRandomIntegerSequence(method, null, null, init);

    }

    private static Integer[] getRandomIntegerSequence(int method, Integer min, Integer max, Integer[] init) throws Exception {

        if ((method == Helpers.TRNG || method == Helpers.TRNG_CSPRNG || init == null) && (min == null || max == null || min < 0 || min > max)) {
            throw new Exception("BAD INTEGER SEQUENCE PARAMETERS!");
        }

        switch (method) {

            case Helpers.TRNG_CSPRNG:

            case Helpers.TRNG:

                Future future = null;

                try {

                    future = Helpers.futureRun(new Callable() {
                        @Override
                        public Object call() {

                            if (!Helpers.RANDOM_ORG_APIKEY.isBlank()) {

                                try {
                                    RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(RANDOM_ORG_APIKEY, 24 * 60 * 60 * 1000, 2 * Helpers.RANDOMORG_TIMEOUT, true);

                                    return Arrays.stream(roc.generateIntegers(max - min + 1, min, max, false)).boxed().toArray(Integer[]::new);

                                } catch (RandomOrgSendTimeoutException | RandomOrgKeyNotRunningError | RandomOrgInsufficientRequestsError | RandomOrgInsufficientBitsError | RandomOrgBadHTTPResponseException | RandomOrgRANDOMORGError | RandomOrgJSONRPCError | IOException ex) {
                                    Logger.getLogger(Helpers.class
                                            .getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {

                                HttpURLConnection con = null;

                                try {

                                    URL url_api = new URL("https://www.random.org/sequences/?min=" + String.valueOf(min) + "&max=" + String.valueOf(max) + "&col=1&format=plain&rnd=new");

                                    con = (HttpURLConnection) url_api.openConnection();

                                    con.addRequestProperty("User-Agent", USER_AGENT_CORONAPOKER);

                                    con.setUseCaches(false);

                                    String output = null;

                                    try (BufferedInputStream bis = new BufferedInputStream(con.getInputStream()); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                                        byte[] buffer = new byte[1024];

                                        int reads;

                                        while ((reads = bis.read(buffer)) != -1) {

                                            byte_res.write(buffer, 0, reads);
                                        }

                                        output = new String(byte_res.toByteArray(), "UTF-8").trim();

                                    }

                                    String[] per_str_array = output.split("\n");

                                    if (per_str_array.length == max - min + 1) {

                                        Integer[] permutacion = new Integer[per_str_array.length];

                                        for (int i = 0; i < per_str_array.length; i++) {

                                            permutacion[i] = Integer.valueOf(per_str_array[i].trim());
                                        }

                                        return permutacion;

                                    }

                                } catch (Exception ex) {

                                    Logger.getLogger(Helpers.class
                                            .getName()).log(Level.SEVERE, null, ex);

                                } finally {

                                    if (con != null) {
                                        con.disconnect();
                                    }
                                }

                            }

                            return null;
                        }
                    });

                    Integer[] randomorg_shuffle = (Integer[]) future.get(Helpers.RANDOMORG_TIMEOUT, TimeUnit.MILLISECONDS);

                    if (randomorg_shuffle != null) {

                        if (method == Helpers.TRNG_CSPRNG) {

                            //Second shuffle with CSPRNG (PARANOID MODE)
                            return getRandomIntegerSequence(Helpers.CSPRNG, randomorg_shuffle);

                        }

                        return randomorg_shuffle;
                    }

                } catch (InterruptedException | ExecutionException | TimeoutException ex) {

                    if (future != null) {
                        future.cancel(true);
                    }
                }

                if (!Helpers.RANDOMORG_ERROR_MSG) {
                    Helpers.RANDOMORG_ERROR_MSG = true;

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            int res = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance() != null ? GameFrame.getInstance() : null, Translator.translate("msg.randomorg_problem"));

                            if (res == 0) {

                                DECK_RANDOM_GENERATOR = Helpers.CSPRNG;

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {

                                        GameFrame.getInstance().getTapete().getCommunityCards().getRandom_button().setVisible(true);
                                    }
                                });
                            }

                            Helpers.RANDOMORG_ERROR_MSG = false;
                        }
                    });
                }

                //Fallback to CSPRNG
                return getRandomIntegerSequence(Helpers.CSPRNG, min, max, init);

            case Helpers.CSPRNG:

                /*
                    EYE ON FISHER-YATES WHEN SHUFFLING A POKER DECK:

                    The Fisher-Yates shuffling algorithm guarantees that all permutations are equally likely, but in
                    order to randomly generate ANY of the mathematically possible permutations, a suitable random number
                    generator is required.

                    A 52 cards poker deck can be sorted in [52! = 80,658,175,170,943,878,571,660,636,856,403,766,975,289,505,440,883,277,824,000,000,000,000]
                    different ways. In order to generate ANY of the 52! permutations, a minimum period of 2^226
                    may be required, although it would be advisable to exceed several orders of magnitude this amount.
                    CSPRNG HASH-DRBG SHA-512 has an average period of 2^512 (2^1024 internal state length) which
                    is M-A-N-Y orders of magnitude greater than required.

                    Note: reshuffling the same deck continuously might mitigate a short period PRNG. See constant INFINITE_DECK_SHUFFLE.

                 */
                if (Helpers.CSPRNG_GENERATOR != null) {

                    ArrayList<Integer> permutacion = new ArrayList<>();

                    if (init == null) {

                        for (int i = min; i <= max; i++) {
                            permutacion.add(i);
                        }

                    } else {

                        permutacion.addAll(Arrays.asList(init));

                    }

                    Collections.shuffle(permutacion, Helpers.CSPRNG_GENERATOR); //Fisher-Yates

                    return permutacion.toArray(new Integer[0]);
                } else {
                    throw new Exception("NO RNG AVAILABLE!");
                }

            default:
                return getRandomIntegerSequence(Helpers.CSPRNG, min, max, init);

        }

    }

    //Thanks -> https://stackoverflow.com/a/19746437 (Pantalla 0 es la principal)
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

    public static void mostrarMensajeInformativo(Container container, String msg, ImageIcon icon) {
        mostrarMensajeInformativo(container, msg, "center", null, icon);
    }

    public static void mostrarMensajeInformativo(Container container, String msg) {
        mostrarMensajeInformativo(container, msg, "center", null, null);
    }

    public static void mostrarMensajeInformativo(Container container, String msg, String align, Integer width, ImageIcon icon) {

        final String mensaje = Translator.translate(msg);

        Audio.playWavResource("misc/warning.wav");

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");
        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(container, label, "Info", JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(DIALOG_ICON_SIZE, Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

        } else {
            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(container, label, "Info", JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(DIALOG_ICON_SIZE, Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

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

        Audio.playWavResource("misc/warning.wav");

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM);

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(container, label, "Info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(DIALOG_ICON_SIZE, Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(container, label, "Info", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, icon != null ? new ImageIcon(icon.getImage().getScaledInstance(DIALOG_ICON_SIZE, Math.round((float) (icon.getIconHeight() * DIALOG_ICON_SIZE) / icon.getIconWidth()), Image.SCALE_SMOOTH)) : icon);
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

        Audio.playWavResource("misc/warning.wav");

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM);

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

        Audio.playWavResource("misc/warning.wav");

        JLabel label = new JLabel("<html><div align='" + align + "'" + (width != null ? " style='width:" + String.valueOf(width) + "px'" : "") + ">" + mensaje.replaceAll("\n", "<br>") + "</div></html>");

        Helpers.updateFonts(label, GUI_FONT, MESSAGE_DIALOG_ZOOM);

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

                ArrayList<String> update_info;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(oracle.openStream()))) {
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

                NodeList nodeList = document.getElementsByTagName("decks").item(0).getChildNodes();

                for (int i = 0; i < nodeList.getLength(); i++) {

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

                mod.put("decks", decks.isEmpty() ? null : decks);

                mod.put("init_background", Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/init.png")));

                Logger
                        .getLogger(Helpers.class
                                .getName()).log(Level.INFO, mod.get("name") + " " + mod.get("version") + " cargado {0}", mod);

                return mod;

            } catch (ParserConfigurationException | SAXException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);

            } catch (IOException ex) {
                Logger.getLogger(Helpers.class
                        .getName()).log(Level.SEVERE, null, ex);
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
     * waiting/sleeping when the pool was shut down. This is NEVER a real
     * error in CoronaPoker: the only source of {@code InterruptedException}
     * is {@code pool.shutdownNow()} during exit/teardown, and the only source
     * of {@code BrokenBarrierException} is another thread on the same barrier
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
     * Control-flow throwable signalled by {@link #pausar(long)} when the calling
     * thread has been interrupted (typically by {@code pool.shutdownNow()} during
     * exit/teardown). Lets outer {@code while}/{@code for} loops bail out of
     * cooperative cancellation naturally without each callsite having to check
     * {@code Thread.interrupted()} by hand.
     *
     * <p>Extends {@link Error} ON PURPOSE so the dozens of existing
     * {@code catch (Exception)} blocks (some of which trigger destructive side
     * effects like {@code cancelarManoYDevolverApuestas} or
     * {@code System.exit(1)} on CRUPIER FATAL ERROR) do NOT swallow it: the
     * throwable must propagate to the top of the worker's {@code Runnable} and
     * be absorbed silently by the {@code Future}. The interrupt flag is restored
     * before the throw, so any catch site that explicitly wants to react can
     * still observe it. NOT a real error — never logged as SEVERE.
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

    private static final ThreadLocal<Boolean> PAUSAR_CANCELLATION_LOGGED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

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
     * Wrap defensivo: si el lambda lanza NPE porque GameFrame.getInstance()
     * ya devuelve null (resetInstance() drenó la ventana antes de que el EDT
     * llegase a procesarlo — race normal del cleanup post-MISDEAL /
     * fin-de-partida), descartamos el lambda silenciosamente. Si en cambio
     * GameFrame sigue vivo, el NPE es bug real y se relanza para que el EDT
     * lo loguee como siempre.
     *
     * Sin este wrap, cada lambda Helpers.GUIRun(() -> GameFrame.getInstance()...)
     * tendría que pre-validar el singleton — y hay literalmente cientos.
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

    public static Future threadRun(Runnable r) {

        return THREAD_POOL.submit(r);

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

            while (mynotifier.size() < threads) {

                synchronized (mynotifier) {

                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(Helpers.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if (notifier != null) {

                notifier.add(Thread.currentThread().threadId());

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

            while (mynotifier.size() < threads) {

                synchronized (mynotifier) {

                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(Helpers.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if (notifier != null) {

                notifier.add(Thread.currentThread().threadId());

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

    public static float floatClean(float val) {

        return floatClean(val, 1);
    }

    public static float floatClean(float val, int decs) {

        return new BigDecimal(val).setScale(decs, RoundingMode.HALF_UP).floatValue();
    }

    public static int float1DSecureCompare(float val1, float val2) {

        return Float.compare(floatClean(val1), floatClean(val2));
    }

    public static HashMap<Object, Object> reverseHashMap(HashMap<Object, Object> map) {

        HashMap<Object, Object> reverse = new HashMap<>();

        for (Map.Entry<Object, Object> entry : map.entrySet()) {

            Object key = entry.getKey();

            Object value = entry.getValue();

            reverse.put(value, key);

        }

        return reverse;
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;

        }
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

            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(Helpers.PROPERTIES.getProperty("zoom_menu", "1")));
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
            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(Helpers.PROPERTIES.getProperty("zoom_menu", "1")));
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
            Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(Helpers.PROPERTIES.getProperty("zoom_menu", "1")));
            txtArea.setComponentPopupMenu(popup);
        }

        private JTextFieldRegularPopupMenu() {
        }
    }

    public static class TapetePopupMenu {

        public static JMenu BARAJAS_MENU = null;
        public static JMenu TAPETES_MENU = null;
        public static JMenu RABBIT_MENU = null;
        public static JMenu ZOOM_MENU = null;
        public static JMenu VISTA_MENU = null;
        public static JMenu SONIDO_MENU = null;
        public static JMenu PERSONALIZACION_MENU = null;
        public static JMenu AYUDA_MENU = null;
        public static JMenuItem MAX_HANDS_MENU;
        public static JMenuItem HALT_GAME_MENU;
        public static JCheckBoxMenuItem AUTO_FULLSCREEN_MENU;
        public static JCheckBoxMenuItem FULLSCREEN_MENU;
        public static JCheckBoxMenuItem SONIDOS_MENU;
        public static JCheckBoxMenuItem SONIDOS_COMENTARIOS_MENU;
        public static JCheckBoxMenuItem SONIDOS_MUSICA_MENU;
        public static JCheckBoxMenuItem SONIDOS_TTS_MENU;
        public static JCheckBoxMenuItem RELOJ_MENU;
        public static JCheckBoxMenuItem REBUY_NOW_MENU;
        public static JCheckBoxMenuItem IWTSTH_RULE_MENU;
        public static JCheckBoxMenuItem COMPACTA_MENU;
        public static JCheckBoxMenuItem CONFIRM_MENU;
        public static JCheckBoxMenuItem ANIMACION_MENU;
        public static JCheckBoxMenuItem CHAT_IMAGE_MENU;
        public static JCheckBoxMenuItem CINEMATICAS_MENU;
        public static JCheckBoxMenuItem AUTO_ACTION_MENU;
        public static JCheckBoxMenuItem LAST_HAND_MENU;
        public static JCheckBoxMenuItem AUTO_ZOOM_MENU;
        public static JRadioButtonMenuItem TAPETE_VERDE;
        public static JRadioButtonMenuItem TAPETE_AZUL;
        public static JRadioButtonMenuItem TAPETE_ROJO;
        public static JRadioButtonMenuItem TAPETE_NEGRO;
        public static JRadioButtonMenuItem TAPETE_MADERA;
        public static JRadioButtonMenuItem RABBIT_OFF;
        public static JRadioButtonMenuItem RABBIT_FREE;
        public static JRadioButtonMenuItem RABBIT_SB;
        public static JRadioButtonMenuItem RABBIT_BB;
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

        private static void generarRabbitMenu() {

            Action rabbitOffAction = new AbstractAction("OFF") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_rabbit_off().doClick();
                }
            };

            Action rabbitFreeAction = new AbstractAction("FREE") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_rabbit_free().doClick();
                }
            };

            Action rabbitSbAction = new AbstractAction("FREE + SB") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_rabbit_sb().doClick();
                }
            };

            Action rabbitBbAction = new AbstractAction("FREE + SB + BB") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    GameFrame.getInstance().getMenu_rabbit_bb().doClick();
                }
            };

            RABBIT_OFF = new LeftClickRadioButtonMenuItem(rabbitOffAction);
            RABBIT_FREE = new LeftClickRadioButtonMenuItem(rabbitFreeAction);
            RABBIT_SB = new LeftClickRadioButtonMenuItem(rabbitSbAction);
            RABBIT_BB = new LeftClickRadioButtonMenuItem(rabbitBbAction);

            RABBIT_MENU = new JMenu(Translator.translate("menu.rabbit_hunting"));
            RABBIT_MENU.add(RABBIT_OFF);
            RABBIT_MENU.add(RABBIT_FREE);
            RABBIT_MENU.add(RABBIT_SB);
            RABBIT_MENU.add(RABBIT_BB);

            RABBIT_OFF.setSelected(GameFrame.RABBIT_HUNTING == 0);
            RABBIT_FREE.setSelected(GameFrame.RABBIT_HUNTING == 1);
            RABBIT_SB.setSelected(GameFrame.RABBIT_HUNTING == 2);
            RABBIT_BB.setSelected(GameFrame.RABBIT_HUNTING == 3);

            RABBIT_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/rabbit.png")));

        }

        public static void addTo(TablePanel tapete, boolean reset) {

            if (popup == null || reset) {

                popup = new JPopupMenu();

                generarBarajasMenu();

                generarTapetesMenu();

                generarRabbitMenu();

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

                Action soundAction = new AbstractAction(Translator.translate("menu.sonidos")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getSonidos_menu().doClick();
                    }
                };

                Action comentariosAction = new AbstractAction(Translator.translate("menu.sonidos_cona")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getSonidos_chorra_menu().doClick();
                    }
                };

                Action musicaAction = new AbstractAction(Translator.translate("menu.musica_ambiental")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAscensor_menu().doClick();
                    }
                };

                Action TTSAction = new AbstractAction(Translator.translate("menu.tts")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getTts_menu().doClick();
                    }
                };

                Action chatAction = new AbstractAction(Translator.translate("menu.ver_chat")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getChat_menu().doClick();
                    }
                };

                Action registroAction = new AbstractAction(Translator.translate("menu.ver_registro")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getRegistro_menu().doClick();
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

                Action iwtsthRuleAction = new AbstractAction(Translator.translate("menu.regla_iwtsth")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getIwtsth_rule_menu().doClick();
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

                Action animacionAction = new AbstractAction(Translator.translate("menu.animacion_cartas")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAnimacion_menu().doClick();
                    }
                };

                Action chatimageAction = new AbstractAction(Translator.translate("menu.imagenes_chat_juego")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getChat_image_menu().doClick();
                    }
                };

                Action autoactAction = new AbstractAction(Translator.translate("menu.botones_auto")) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        GameFrame.getInstance().getAuto_action_menu().doClick();
                    }
                };

                // === VIEW submenu (display toggles + zoom + UI behavior preferences) ===
                VISTA_MENU = new JMenu(Translator.translate("menu.vista"));
                VISTA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/tiny.png")));

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
                CINEMATICAS_MENU.setSelected(GameFrame.CINEMATICAS);
                VISTA_MENU.add(CINEMATICAS_MENU);

                ANIMACION_MENU = new LeftClickCheckBoxMenuItem(animacionAction);
                ANIMACION_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/dealer.png")));
                ANIMACION_MENU.setSelected(GameFrame.ANIMACION_CARTAS);
                VISTA_MENU.add(ANIMACION_MENU);

                CHAT_IMAGE_MENU = new LeftClickCheckBoxMenuItem(chatimageAction);
                CHAT_IMAGE_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/chat_image.png")));
                CHAT_IMAGE_MENU.setSelected(GameFrame.CHAT_IMAGES_INGAME);
                VISTA_MENU.add(CHAT_IMAGE_MENU);

                VISTA_MENU.addSeparator();

                CONFIRM_MENU = new LeftClickCheckBoxMenuItem(confirmAction);
                CONFIRM_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/confirmation.png")));
                CONFIRM_MENU.setSelected(GameFrame.CONFIRM_ACTIONS);
                VISTA_MENU.add(CONFIRM_MENU);

                // === SOUND submenu ===
                SONIDO_MENU = new JMenu(Translator.translate("menu.audio"));
                SONIDO_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/sound.png")));

                SONIDOS_MENU = new LeftClickCheckBoxMenuItem(soundAction);
                SONIDOS_MENU.setSelected(GameFrame.SONIDOS);
                SONIDOS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource(GameFrame.SONIDOS ? "/images/menu/sound.png" : "/images/menu/mute.png")));
                SONIDO_MENU.add(SONIDOS_MENU);

                SONIDOS_COMENTARIOS_MENU = new LeftClickCheckBoxMenuItem(comentariosAction);
                SONIDOS_COMENTARIOS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/joke.png")));
                SONIDOS_COMENTARIOS_MENU.setSelected(GameFrame.SONIDOS_CHORRA);
                SONIDOS_COMENTARIOS_MENU.setEnabled(GameFrame.SONIDOS);
                SONIDO_MENU.add(SONIDOS_COMENTARIOS_MENU);

                SONIDOS_MUSICA_MENU = new LeftClickCheckBoxMenuItem(musicaAction);
                SONIDOS_MUSICA_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/music.png")));
                SONIDOS_MUSICA_MENU.setSelected(GameFrame.MUSICA_AMBIENTAL);
                SONIDOS_MUSICA_MENU.setEnabled(GameFrame.SONIDOS);
                SONIDO_MENU.add(SONIDOS_MUSICA_MENU);

                SONIDOS_TTS_MENU = new LeftClickCheckBoxMenuItem(TTSAction);
                SONIDOS_TTS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/voice.png")));
                SONIDOS_TTS_MENU.setSelected(GameFrame.SONIDOS_TTS);
                SONIDOS_TTS_MENU.setEnabled(GameFrame.SONIDOS);
                SONIDO_MENU.add(SONIDOS_TTS_MENU);

                // === CUSTOMIZATION submenu (decks + mats) ===
                PERSONALIZACION_MENU = new JMenu(Translator.translate("menu.personalizacion"));
                PERSONALIZACION_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/baraja.png")));
                PERSONALIZACION_MENU.add(BARAJAS_MENU);
                PERSONALIZACION_MENU.add(TAPETES_MENU);

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
                JMenuItem chat = new LeftClickMenuItem(chatAction);
                chat.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/chat.png")));
                popup.add(chat);

                JMenuItem log = new LeftClickMenuItem(registroAction);
                log.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/log.png")));
                popup.add(log);

                popup.addSeparator();

                popup.add(VISTA_MENU);
                popup.add(SONIDO_MENU);
                popup.add(PERSONALIZACION_MENU);
                popup.add(AYUDA_MENU);

                popup.addSeparator();

                AUTO_ACTION_MENU = new LeftClickCheckBoxMenuItem(autoactAction);
                AUTO_ACTION_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/auto.png")));
                AUTO_ACTION_MENU.setSelected(GameFrame.AUTO_ACTION_BUTTONS);
                popup.add(AUTO_ACTION_MENU);

                REBUY_NOW_MENU = new LeftClickCheckBoxMenuItem(rebuyNowAction);
                REBUY_NOW_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/rebuy.png")));
                REBUY_NOW_MENU.setSelected(false);
                REBUY_NOW_MENU.setEnabled(GameFrame.REBUY);
                popup.add(REBUY_NOW_MENU);

                IWTSTH_RULE_MENU = new LeftClickCheckBoxMenuItem(iwtsthRuleAction);
                IWTSTH_RULE_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/eyes.png")));
                IWTSTH_RULE_MENU.setSelected(GameFrame.IWTSTH_RULE);
                popup.add(IWTSTH_RULE_MENU);

                popup.add(RABBIT_MENU);

                popup.addSeparator();

                LAST_HAND_MENU = new LeftClickCheckBoxMenuItem(lastHandAction);
                LAST_HAND_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/last_hand.png")));
                LAST_HAND_MENU.setSelected(false);
                popup.add(LAST_HAND_MENU);

                MAX_HANDS_MENU = new LeftClickMenuItem(maxHandsAction);
                MAX_HANDS_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/meter.png")));
                popup.add(MAX_HANDS_MENU);

                popup.addSeparator();

                HALT_GAME_MENU = new LeftClickMenuItem(haltAction);
                HALT_GAME_MENU.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/stop.png")));
                popup.add(HALT_GAME_MENU);

                popup.addSeparator();

                JMenuItem exit_menu = new LeftClickMenuItem(exitAction);
                exit_menu.setIcon(new javax.swing.ImageIcon(Helpers.class.getResource("/images/menu/close.png")));
                popup.add(exit_menu);

                Helpers.updateFonts(popup, Helpers.GUI_FONT, Float.valueOf(Helpers.PROPERTIES.getProperty("zoom_menu", "1")) * 1.10f);
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
