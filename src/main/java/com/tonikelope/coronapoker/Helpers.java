package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.Helpers.DECK_RANDOM_GENERATOR;
import static com.tonikelope.coronapoker.Helpers.SPRNG;
import static com.tonikelope.coronapoker.Init.CORONA_DIR;
import static com.tonikelope.coronapoker.Init.DEBUG_DIR;
import static com.tonikelope.coronapoker.Init.LOGS_DIR;
import static com.tonikelope.coronapoker.Init.REC_DIR;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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
import javazoom.jlgui.basicplayer.BasicController;
import javazoom.jlgui.basicplayer.BasicPlayer;
import javazoom.jlgui.basicplayer.BasicPlayerEvent;
import javazoom.jlgui.basicplayer.BasicPlayerException;
import javazoom.jlgui.basicplayer.BasicPlayerListener;
import org.apache.commons.codec.binary.Base64;
import org.random.api.RandomOrgClient;
import org.random.api.exception.RandomOrgBadHTTPResponseException;
import org.random.api.exception.RandomOrgInsufficientBitsError;
import org.random.api.exception.RandomOrgInsufficientRequestsError;
import org.random.api.exception.RandomOrgJSONRPCError;
import org.random.api.exception.RandomOrgKeyNotRunningError;
import org.random.api.exception.RandomOrgRANDOMORGError;
import org.random.api.exception.RandomOrgSendTimeoutException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author tonikelope
 */
public class Helpers {

    public static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0";
    public static final float MASTER_VOLUME = 0.8f;
    public static final Map.Entry<String, Float> ASCENSOR_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/background_music.mp3", 0.4f); //DEFAULT * CUSTOM
    public static final Map.Entry<String, Float> STATS_VOLUME = new ConcurrentHashMap.SimpleEntry<String, Float>("misc/stats_music.mp3", 0.7f);
    public static final Map<String, Float> CUSTOM_VOLUMES = Map.ofEntries(ASCENSOR_VOLUME, STATS_VOLUME);
    public static final int RANDOMORG_TIMEOUT = 10000;
    public static final int SPRNG = 2;
    public static final int TRNG = 1;
    public static final ConcurrentHashMap<Component, Integer> ORIGINAL_FONT_SIZE = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, BasicPlayer> MP3_LOOP = new ConcurrentHashMap<>();
    public static final ConcurrentLinkedQueue<String> MP3_LOOP_MUTED = new ConcurrentLinkedQueue<>();
    public static final ConcurrentHashMap<String, BasicPlayer> MP3_RESOURCES = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Clip>> WAVS_RESOURCES = new ConcurrentHashMap<>();
    public static final String PROPERTIES_FILE = Init.CORONA_DIR + "/coronapoker.properties";

    public volatile static ClipboardSpy CLIPBOARD_SPY = new ClipboardSpy();
    public volatile static int DECK_RANDOM_GENERATOR = SPRNG;
    public volatile static String RANDOM_ORG_APIKEY = "";
    public volatile static Random PRNG_GENERATOR = null;
    public volatile static SecureRandom SPRNG_GENERATOR = null;
    public volatile static Properties PROPERTIES = loadPropertiesFile();
    public volatile static Font GUI_FONT = null;
    public volatile static boolean MUTED_ALL = false;
    public volatile static boolean MUTED_MP3 = false;
    public volatile static boolean RANDOMORG_ERROR_MSG = false;

    //Thanks -> https://stackoverflow.com/a/3778768
    public static boolean isDebug() {

        return java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("jdwp");
    }

    public static void SQLITEVAC() {

        try {
            Statement statement = Init.SQLITE.createStatement();
            statement.execute("VACUUM");
        } catch (SQLException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
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

    public static String currentJarHMAC(byte[] hmac_key) {

        try {

            if ((Helpers.isDebug() || !new File(Helpers.class.getProtectionDomain().getCodeSource().getLocation().toURI()).isFile()) && !Game.DEV_MODE) {
                System.exit(1);
            }

            if (new File(Helpers.class.getProtectionDomain().getCodeSource().getLocation().toURI()).isFile()) {

                Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                sha256_HMAC.init(new SecretKeySpec(hmac_key, "HmacSHA256"));

                JarFile jarFile = new JarFile(new File(Helpers.class.getProtectionDomain().getCodeSource().getLocation().toURI()));

                Enumeration allEntries = jarFile.entries();

                while (allEntries.hasMoreElements()) {

                    JarEntry entry = (JarEntry) allEntries.nextElement();

                    String name = entry.getName();

                    if (name.startsWith("com/tonikelope/coronapoker/") && name.endsWith(".class")) {
                        try (InputStream is = Helpers.class.getResourceAsStream("/" + name)) {
                            sha256_HMAC.update(is.readAllBytes());
                        }
                    }
                }

                return Base64.encodeBase64String(sha256_HMAC.doFinal());
            }

        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | URISyntaxException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

        return "*";

    }

    public static String encryptString(String cadena, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        byte[] iv = new byte[16];

        Helpers.SPRNG_GENERATOR.nextBytes(iv);

        return encryptString(cadena, aes_key, iv, hmac_key);

    }

    public static String encryptString(String cadena, SecretKeySpec aes_key, byte[] iv, SecretKeySpec hmac_key) {

        try {
            Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

            cifrado.init(Cipher.ENCRYPT_MODE, aes_key, new IvParameterSpec(iv));

            byte[] cmsg = cifrado.doFinal(cadena.getBytes("UTF-8"));

            byte[] full_msg;

            byte[] iv_cmsg = new byte[iv.length + cmsg.length];

            int i;

            for (i = 0; i < iv.length; i++) {
                iv_cmsg[i] = iv[i];
            }

            for (i = 0; i < cmsg.length; i++) {
                iv_cmsg[i + iv.length] = cmsg[i];
            }

            if (hmac_key != null) {

                full_msg = new byte[32 + iv.length + cmsg.length];

                Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                sha256_HMAC.init(hmac_key);

                byte[] hmac = sha256_HMAC.doFinal(iv_cmsg);

                for (i = 0; i < hmac.length; i++) {
                    full_msg[i] = hmac[i];
                }

                for (i = 0; i < iv_cmsg.length; i++) {
                    full_msg[i + hmac.length] = iv_cmsg[i];
                }
            } else {
                full_msg = iv_cmsg;
            }

            return Base64.encodeBase64String(full_msg);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static String decryptString(String cadena, SecretKeySpec aes_key, SecretKeySpec hmac_key) throws KeyException {

        try {

            Cipher cifrado = Cipher.getInstance("AES/CBC/PKCS5Padding");

            byte[] full_msg = Base64.decodeBase64(cadena);

            byte[] hmac = new byte[32];

            byte[] iv = new byte[cifrado.getBlockSize()];

            byte[] cmsg;

            int i;

            if (hmac_key != null) {

                cmsg = new byte[full_msg.length - hmac.length - iv.length];

                for (i = 0; i < hmac.length; i++) {
                    hmac[i] = full_msg[i];
                }

                for (i = 0; i < iv.length; i++) {
                    iv[i] = full_msg[i + hmac.length];
                }

                for (i = 0; i < cmsg.length; i++) {
                    cmsg[i] = full_msg[i + hmac.length + iv.length];
                }

                byte[] iv_cmsg = new byte[iv.length + cmsg.length];

                for (i = 0; i < iv.length; i++) {
                    iv_cmsg[i] = iv[i];
                }

                for (i = 0; i < cmsg.length; i++) {
                    iv_cmsg[i + iv.length] = cmsg[i];
                }

                Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                sha256_HMAC.init(hmac_key);

                byte[] current_hmac = sha256_HMAC.doFinal(iv_cmsg);

                if (!MessageDigest.isEqual(hmac, current_hmac)) {
                    throw new KeyException("BAD HMAC or BAD KEY");
                }
            } else {

                cmsg = new byte[full_msg.length - iv.length];

                for (i = 0; i < iv.length; i++) {
                    iv[i] = full_msg[i];
                }

                for (i = 0; i < cmsg.length; i++) {
                    cmsg[i] = full_msg[i + iv.length];
                }

            }

            cifrado.init(Cipher.DECRYPT_MODE, aes_key, new IvParameterSpec(iv));

            byte[] msg = cifrado.doFinal(cmsg);

            return new String(msg, "UTF-8");

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;

    }

    public static String encryptCommand(String command, SecretKeySpec aes_key, byte[] iv, SecretKeySpec hmac_key) {

        return ("*" + Helpers.encryptString(command, aes_key, iv, hmac_key));

    }

    public static String encryptCommand(String command, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        byte[] iv = new byte[16];

        Helpers.SPRNG_GENERATOR.nextBytes(iv);

        return encryptCommand(command, aes_key, iv, hmac_key);

    }

    public static String decryptCommand(String command, SecretKeySpec aes_key, SecretKeySpec hmac_key) throws KeyException {

        return command.charAt(0) == '*' ? Helpers.decryptString(command.trim().substring(1), aes_key, hmac_key) : command;
    }

    public static void createIfNoExistsCoronaDirs() {

        File f = new File(CORONA_DIR);

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File(LOGS_DIR);

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File(DEBUG_DIR);

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File(REC_DIR);

        if (!f.exists()) {
            f.mkdir();
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

    //Thanks -> https://stackoverflow.com/a/7603815
    public static BufferedImage makeImageRoundedCorner(Image image, int cornerRadius) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);

        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();

        // This is what we want, but it only does hard-clipping, i.e. aliasing
        // g2.setClip(new RoundRectangle2D ...)
        // so instead fake soft-clipping by first drawing the desired clip shape
        // in fully opaque white with antialiasing enabled...
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadius, cornerRadius));

        // ... then compositing the image on top,
        // using the white shape from above as alpha source
        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);
        g2.dispose();

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
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex.getMessage());
                }
            }
        });
    }

    public static String toHexString(byte[] array) {
        return DatatypeConverter.printHexBinary(array);
    }

    public static byte[] toByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }

    public static void translateComponents(final Component component, boolean force) {

        if (component != null) {

            if (component instanceof JLabel) {

                ((JLabel) component).setText(Translator.translate(((JLabel) component).getText(), force));
                ((JLabel) component).setToolTipText(Translator.translate(((JLabel) component).getToolTipText(), force));

            } else if (component instanceof JTextField) {

                ((JTextField) component).setText(Translator.translate(((JTextField) component).getText(), force));
                ((JTextField) component).setToolTipText(Translator.translate(((JTextField) component).getToolTipText(), force));

            } else if (component instanceof JButton) {

                ((JButton) component).setText(Translator.translate(((JButton) component).getText(), force));
                ((JButton) component).setToolTipText(Translator.translate(((JButton) component).getToolTipText(), force));

            } else if (component instanceof JCheckBox) {

                ((JCheckBox) component).setText(Translator.translate(((JCheckBox) component).getText(), force));
                ((JCheckBox) component).setToolTipText(Translator.translate(((JCheckBox) component).getToolTipText(), force));

            } else if ((component instanceof JMenuItem) && !(component instanceof JMenu)) {

                ((JMenuItem) component).setText(Translator.translate(((JMenuItem) component).getText(), force));
                ((JMenuItem) component).setToolTipText(Translator.translate(((JMenuItem) component).getToolTipText(), force));

            } else if (component instanceof JMenu) {

                for (Component child : ((JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {
                        translateComponents(child, force);
                    }
                }

                ((JMenu) component).setText(Translator.translate(((JMenu) component).getText(), force));

            } else if (component instanceof JComboBox) {

                int selected = ((JComboBox) component).getSelectedIndex();

                DefaultComboBoxModel model = (DefaultComboBoxModel) ((JComboBox) component).getModel();

                String[] elements = new String[((JComboBox) component).getItemCount()];

                int size = ((JComboBox) component).getItemCount();

                for (int i = 0; i < size; i++) {
                    elements[i] = Translator.translate((String) model.getElementAt(i), force);

                }

                ((JComboBox) component).setModel(new DefaultComboBoxModel(elements));

                ((JComboBox) component).setSelectedIndex(selected);

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        translateComponents(child, force);
                    }
                }

                if ((component instanceof JPanel) && (((JComponent) component).getBorder() instanceof TitledBorder)) {
                    ((TitledBorder) ((JComponent) component).getBorder()).setTitle(Translator.translate(((TitledBorder) ((JComponent) component).getBorder()).getTitle(), force));
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

    public static void updateFonts(final Component component, final Font font, final int size_dif) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        updateFonts(child, font, size_dif);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        updateFonts(child, font, size_dif);
                    }
                }
            }

            Font old_font = component.getFont();

            Font new_font = font.deriveFont(old_font.getStyle(), size_dif != 0 ? (old_font.getSize() + size_dif) : old_font.getSize());

            boolean error;

            do {
                try {
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

        try {

            font = Font.createFont(Font.TRUETYPE_FONT, stream);

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

            ge.registerFont(font);

        } catch (FontFormatException | IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex.getMessage());
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
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
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

        cantidad = Helpers.floatClean1D(cantidad);

        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();
        otherSymbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.00", otherSymbols);

        return df.format(cantidad).replaceAll("\\.00$", "");

    }

    public static void savePropertiesFile() {

        try {
            PROPERTIES.store(new FileOutputStream(PROPERTIES_FILE), null);
        } catch (IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
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

    public static String getMyPublicIP() {

        String public_ip = null;
        HttpURLConnection con = null;

        try {

            URL url_api = new URL("http://whatismyip.akamai.com/");

            con = (HttpURLConnection) url_api.openConnection();

            con.setUseCaches(false);

            try (InputStream is = con.getInputStream(); ByteArrayOutputStream byte_res = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[16];

                int reads;

                while ((reads = is.read(buffer)) != -1) {

                    byte_res.write(buffer, 0, reads);
                }

                public_ip = new String(byte_res.toByteArray(), "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        return public_ip;
    }

    public static Integer[] getIntegerPermutation(int method, int count) {

        ArrayList<Integer> permutacion = new ArrayList<>();

        switch (method) {
            case Helpers.TRNG:

                FutureTask future = null;

                try {

                    future = Helpers.futureRun(new Callable() {
                        @Override
                        public Object call() {

                            try {
                                RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(RANDOM_ORG_APIKEY, 24 * 60 * 60 * 1000, 2 * Helpers.RANDOMORG_TIMEOUT, true);

                                return Arrays.stream(roc.generateIntegers(count, 1, count, false)).boxed().toArray(Integer[]::new);

                            } catch (RandomOrgSendTimeoutException | RandomOrgKeyNotRunningError | RandomOrgInsufficientRequestsError | RandomOrgInsufficientBitsError | RandomOrgBadHTTPResponseException | RandomOrgRANDOMORGError | RandomOrgJSONRPCError | IOException ex) {
                                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            return null;
                        }
                    });

                    Integer[] per = (Integer[]) future.get(Helpers.RANDOMORG_TIMEOUT, TimeUnit.MILLISECONDS);

                    if (per != null) {
                        return per;
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

                            int res = Helpers.mostrarMensajeErrorSINO(Game.getInstance() != null ? Game.getInstance() : null, "Parece que hubo algún problema con RANDOM.ORG (se usará el SPRNG en su lugar)\n¿Quieres desactivar RANDOM.ORG para el resto de la partida?");

                            if (res == 0) {

                                DECK_RANDOM_GENERATOR = SPRNG;
                            }

                            Helpers.RANDOMORG_ERROR_MSG = false;
                        }
                    });
                }

                //Fallback to SPRNG
                return getIntegerPermutation(Helpers.SPRNG, count);

            case Helpers.SPRNG:

                if (Helpers.SPRNG_GENERATOR != null) {
                    for (int i = 1; i <= count; i++) {
                        permutacion.add(i);
                    }
                    Collections.shuffle(permutacion, Helpers.SPRNG_GENERATOR);
                    return permutacion.toArray(new Integer[permutacion.size()]);
                }

            default:
                return getIntegerPermutation(Helpers.SPRNG, count);
        }

    }

    //Thanks -> https://stackoverflow.com/a/19746437 (Pantalla 0 es la principal)
    public static void centrarJFrame(JFrame window, int screen) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] allDevices = env.getScreenDevices();
        int topLeftX, topLeftY, screenX, screenY, windowPosX, windowPosY;

        if (screen < allDevices.length && screen > -1) {
            topLeftX = allDevices[screen].getDefaultConfiguration().getBounds().x;
            topLeftY = allDevices[screen].getDefaultConfiguration().getBounds().y;

            screenX = allDevices[screen].getDefaultConfiguration().getBounds().width;
            screenY = allDevices[screen].getDefaultConfiguration().getBounds().height;
        } else {
            topLeftX = allDevices[0].getDefaultConfiguration().getBounds().x;
            topLeftY = allDevices[0].getDefaultConfiguration().getBounds().y;

            screenX = allDevices[0].getDefaultConfiguration().getBounds().width;
            screenY = allDevices[0].getDefaultConfiguration().getBounds().height;
        }

        windowPosX = ((screenX - window.getWidth()) / 2) + topLeftX;
        windowPosY = ((screenY - window.getHeight()) / 2) + topLeftY;

        window.setLocation(windowPosX, windowPosY);
    }

    public static void mostrarMensajeInformativo(JFrame frame, String msg) {

        final String mensaje = Translator.translate(msg);

        Helpers.playWavResource("misc/warning.wav");

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(frame, mensaje);

        } else {
            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(frame, mensaje);
                }
            });
        }
    }

    // 0=yes, 1=no, 2=cancel
    public static int mostrarMensajeInformativoSINO(JFrame frame, String msg) {

        final String mensaje = Translator.translate(msg);

        Helpers.playWavResource("misc/warning.wav");

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(frame, mensaje, "Info", JOptionPane.YES_NO_OPTION);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(frame, mensaje, "Info", JOptionPane.YES_NO_OPTION);
                }
            });

            return res[0];

        }

    }

    public static void deleteFile(String filename) {

        try {
            Files.deleteIfExists(Paths.get(filename));
        } catch (IOException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void mostrarMensajeError(JFrame frame, String msg) {

        final String mensaje = Translator.translate(msg);

        Helpers.playWavResource("misc/warning.wav");

        if (SwingUtilities.isEventDispatchThread()) {

            JOptionPane.showMessageDialog(frame, mensaje, "ERROR", JOptionPane.ERROR_MESSAGE);

        } else {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(frame, mensaje, "ERROR", JOptionPane.ERROR_MESSAGE);
                }
            });
        }

    }

    // 0=yes, 1=no, 2=cancel
    public static int mostrarMensajeErrorSINO(JFrame frame, String msg) {

        final String mensaje = Translator.translate(msg);

        Helpers.playWavResource("misc/warning.wav");

        if (SwingUtilities.isEventDispatchThread()) {

            return JOptionPane.showConfirmDialog(frame, mensaje, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

        } else {

            final int[] res = new int[1];

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {

                    res[0] = JOptionPane.showConfirmDialog(frame, mensaje, "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                }
            });

            return res[0];

        }
    }

    public static void playRandomWavResource(Map<String, String[]> sonidos) {

        ArrayList<String> sounds = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : sonidos.entrySet()) {

            String folder = entry.getKey();

            String[] ficheros = entry.getValue();

            for (String fichero : ficheros) {
                sounds.add(folder + fichero);
            }
        }

        int elegido = Helpers.PRNG_GENERATOR.nextInt(sounds.size());

        Helpers.playWavResource(sounds.get(elegido));
    }

    public static void playRandomWavResourceAndWait(Map<String, String[]> sonidos) {

        ArrayList<String> sounds = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : sonidos.entrySet()) {

            String folder = entry.getKey();

            String[] ficheros = entry.getValue();

            for (String fichero : ficheros) {
                sounds.add(folder + fichero);
            }
        }

        int elegido = Helpers.PRNG_GENERATOR.nextInt(sounds.size());

        Helpers.playWavResourceAndWait(sounds.get(elegido));
    }

    public static float getSoundVolume(String sound) {

        return CUSTOM_VOLUMES.containsKey(sound) ? MASTER_VOLUME * CUSTOM_VOLUMES.get(sound) : MASTER_VOLUME;
    }

    public static ConcurrentHashMap<String, Object> loadMOD() {

        if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod"))) {
            ConcurrentHashMap<String, Object> mod = new ConcurrentHashMap<>();

            try {
                File file = new File(Helpers.getCurrentJarParentPath() + "/mod/mod.xml");
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = (Document) documentBuilder.parse(file);

                mod.put("name", document.getElementsByTagName("name").item(0).getTextContent());
                mod.put("version", document.getElementsByTagName("version").item(0).getTextContent().trim());

                if (document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_sounds") != null) {
                    mod.put("fusion_sounds", Boolean.parseBoolean(document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_sounds").getTextContent().trim()));

                }

                if (document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_cinematics") != null) {
                    mod.put("fusion_cinematics", Boolean.parseBoolean(document.getElementsByTagName("mod").item(0).getAttributes().getNamedItem("fusion_cinematics").getTextContent().trim()));

                }

                if (document.getElementsByTagName("font").item(0) != null) {

                    mod.put("font", document.getElementsByTagName("font").item(0).getTextContent().trim());
                }

                //DECKS
                HashMap<String, Object> decks = new HashMap<>();

                NodeList nodeList = document.getElementsByTagName("decks").item(0).getChildNodes();

                for (int i = 0; i < nodeList.getLength(); i++) {

                    if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element el = (Element) nodeList.item(i);
                        HashMap<String, Object> baraja = new HashMap<>();
                        baraja.put("name", el.getElementsByTagName("name").item(0).getTextContent().trim());
                        baraja.put("aspect", Float.parseFloat(el.getElementsByTagName("aspect").item(0).getTextContent().trim()));

                        if (el.getElementsByTagName("sound").item(0) != null) {

                            baraja.put("sound", el.getElementsByTagName("sound").item(0).getTextContent());
                        }

                        decks.put((String) baraja.get("name"), baraja);
                    }
                }

                mod.put("decks", decks.isEmpty() ? null : decks);

                //CINEMATICS
                HashMap<String, Object> cinematics = new HashMap<>();

                nodeList = document.getElementsByTagName("cinematics").item(0).getChildNodes();

                for (int i = 0; i < nodeList.getLength(); i++) {

                    if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element el = (Element) nodeList.item(i);
                        HashMap<String, Object> animation = new HashMap<>();
                        animation.put("name", el.getElementsByTagName("name").item(0).getTextContent().trim());
                        animation.put("time", Long.parseLong(el.getElementsByTagName("time").item(0).getTextContent().trim()));

                        if (el.getElementsByTagName("event") != null) {
                            animation.put("event", el.getElementsByTagName("event").item(0).getTextContent().trim());
                        } else {
                            animation.put("event", "misc");
                        }

                        cinematics.put((String) animation.get("name"), animation);
                    }
                }

                mod.put("cinematics", cinematics.isEmpty() ? null : cinematics);

                Logger.getLogger(Helpers.class.getName()).log(Level.INFO, mod.get("name") + " " + mod.get("version") + " cargado {0}", mod);

                return mod;
            } catch (ParserConfigurationException | SAXException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }

    public static String getCurrentJarParentPath() {
        try {
            CodeSource codeSource = Init.class.getProtectionDomain().getCodeSource();

            File jarFile = new File(codeSource.getLocation().toURI().getPath());

            return jarFile.getParentFile().getAbsolutePath();

        } catch (URISyntaxException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static String getCurrentJarPath() {
        try {

            return new File(Init.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();

        } catch (URISyntaxException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    private static InputStream getSoundInputStream(String sound) {

        if (Init.MOD != null) {

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/sounds/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound))) {

                try {
                    return new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/cinematics/" + sound);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        InputStream is;

        if ((is = Helpers.class.getResourceAsStream("/sounds/" + sound)) != null || (is = Helpers.class.getResourceAsStream("/cinematics/" + sound)) != null) {
            return is;
        }

        Logger.getLogger(Helpers.class.getName()).log(Level.INFO, "NO se encuentra el SONIDO {0}", sound);

        return null;
    }

    public static boolean playWavResourceAndWait(String sound) {

        return playWavResourceAndWait(sound, true);

    }

    public static boolean playWavResourceAndWait(String sound, boolean force_close) {
        if (!Game.TEST_MODE) {
            InputStream sound_stream;
            if ((sound_stream = getSoundInputStream(sound)) != null) {
                try (final BufferedInputStream bis = new BufferedInputStream(sound_stream); final Clip clip = AudioSystem.getClip()) {
                    if (WAVS_RESOURCES.containsKey(sound)) {

                        if (force_close) {

                            for (Clip c : WAVS_RESOURCES.get(sound)) {
                                c.stop();
                            }

                            WAVS_RESOURCES.get(sound).clear();

                            WAVS_RESOURCES.get(sound).add(clip);

                        } else {
                            WAVS_RESOURCES.get(sound).add(clip);
                        }

                    } else {
                        ConcurrentLinkedQueue<Clip> list = new ConcurrentLinkedQueue<>();
                        WAVS_RESOURCES.put(sound, list);
                        list.add(clip);
                    }
                    clip.open(AudioSystem.getAudioInputStream(bis));
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    if (!Game.SONIDOS) {
                        gainControl.setValue(gainControl.getMinimum());
                    } else {
                        float dB = (float) Math.log10(getSoundVolume(sound)) * 20.0f;
                        gainControl.setValue(dB);
                    }
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                    Helpers.pausar(clip.getMicrosecondLength() / 1000);

                    if (WAVS_RESOURCES.containsKey(sound) && WAVS_RESOURCES.get(sound).contains(clip)) {
                        clip.stop();
                        WAVS_RESOURCES.get(sound).remove(clip);
                    }
                    return true;
                } catch (Exception ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, "ERROR -> {0}", sound);
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return false;
    }

    public static synchronized int getTotalLoopMp3Playing() {

        int tot = 0;

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().getStatus() == BasicPlayer.PLAYING) {
                tot++;
            }
        }

        return tot;
    }

    public static boolean isLoopMp3Playing() {

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().getStatus() == BasicPlayer.PLAYING) {

                return true;

            }
        }

        return false;

    }

    public static void playLoopMp3Resource(String sound) {

        if (!Game.TEST_MODE) {

            Helpers.threadRun(new Runnable() {

                @Override
                public void run() {

                    final Object player_wait = new Object();

                    final BasicPlayer player = new BasicPlayer();

                    do {

                        try (BufferedInputStream bis = new BufferedInputStream(getSoundInputStream(sound))) {

                            player.addBasicPlayerListener(new BasicPlayerListener() {

                                @Override
                                public void stateUpdated(BasicPlayerEvent bpe) {
                                    synchronized (player_wait) {
                                        player_wait.notifyAll();
                                    }
                                }

                                @Override
                                public void opened(Object o, Map map) {
                                }

                                @Override
                                public void progress(int i, long l, byte[] bytes, Map map) {
                                }

                                @Override
                                public void setController(BasicController bc) {
                                }

                            });

                            player.open(bis);

                            MP3_LOOP.put(sound, player);

                            if (player.getStatus() != BasicPlayer.PLAYING) {
                                player.play();
                            }

                            if (!Game.SONIDOS || MP3_LOOP_MUTED.contains(sound)) {
                                player.setGain(0f);
                            } else {
                                player.setGain(getSoundVolume(sound));
                            }

                            do {
                                synchronized (player_wait) {

                                    try {
                                        player_wait.wait(1000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            } while (player.getStatus() == BasicPlayer.PLAYING || player.getStatus() == BasicPlayer.PAUSED);

                        } catch (Exception ex) {
                            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, "ERROR -> {0}", sound);
                            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    } while (MP3_LOOP.containsKey(sound));

                }
            });

        }
    }

    public static void playWavResource(String sound) {

        playWavResource(sound, true);

    }

    public static void playWavResource(String sound, boolean force_close) {
        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {
                Helpers.playWavResourceAndWait(sound, force_close);
            }
        });
    }

    public static void stopWavResource(String sound) {

        if (WAVS_RESOURCES.containsKey(sound)) {
            ConcurrentLinkedQueue<Clip> list = WAVS_RESOURCES.remove(sound);

            for (Clip c : list) {
                c.stop();
            }
        }
    }

    public static void stopLoopMp3(String sound) {

        BasicPlayer player = MP3_LOOP.remove(sound);

        if (player != null) {
            try {

                player.stop();

                MP3_LOOP_MUTED.remove(sound);

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void pauseLoopMp3(String sound) {

        BasicPlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {
                player.pause();

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void muteLoopMp3(String sound) {

        BasicPlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {
                MP3_LOOP_MUTED.add(sound);
                player.setGain(0f);

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void unmuteLoopMp3(String sound) {

        BasicPlayer player = MP3_LOOP.get(sound);

        if (player != null) {
            try {
                MP3_LOOP_MUTED.remove(sound);

                if (!MUTED_ALL) {
                    player.setGain(getSoundVolume(sound));
                }

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void resumeLoopMp3Resource(String sound) {

        BasicPlayer player = MP3_LOOP.get(sound);

        if (player != null) {

            try {
                player.resume();

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            playLoopMp3Resource(sound);
        }

    }

    public static void pauseCurrentLoopMp3Resource() {

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().getStatus() == BasicPlayer.PLAYING) {

                try {
                    entry.getValue().pause();
                } catch (BasicPlayerException ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    }

    public static void stopAllCurrentLoopMp3Resource() {

        Iterator<Map.Entry<String, BasicPlayer>> iterator = MP3_LOOP.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<String, BasicPlayer> entry = iterator.next();

            if (entry.getValue().getStatus() == BasicPlayer.PLAYING && !MP3_LOOP_MUTED.contains(entry.getKey())) {

                iterator.remove();

                try {

                    entry.getValue().stop();

                    MP3_LOOP_MUTED.remove(entry.getKey());

                } catch (BasicPlayerException ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }

    }

    public static void muteAll() {

        MUTED_ALL = true;

        muteAllLoopMp3();

        muteAllWav();

    }

    public static void muteAllWav() {
        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : Helpers.WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                FloatControl gainControl = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(gainControl.getMinimum());
            }
        }
    }

    public static void muteAllLoopMp3() {

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            try {
                entry.getValue().setGain(0f);
            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public static void unmuteAllLoopMp3() {

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            try {

                if (!MP3_LOOP_MUTED.contains(entry.getKey()) && !MUTED_ALL) {
                    entry.getValue().setGain(getSoundVolume(entry.getKey()));
                }

            } catch (BasicPlayerException ex) {
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void unmuteAllWav() {
        for (Map.Entry<String, ConcurrentLinkedQueue<Clip>> entry : Helpers.WAVS_RESOURCES.entrySet()) {

            ConcurrentLinkedQueue<Clip> list = entry.getValue();

            for (Clip c : list) {

                FloatControl gainControl = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = (float) Math.log10(getSoundVolume(entry.getKey())) * 20.0f;
                gainControl.setValue(dB);
            }
        }
    }

    public static void unMuteAll() {

        MUTED_ALL = false;

        unmuteAllLoopMp3();

        unmuteAllWav();

    }

    public static String getCurrentLoopMp3Playing() {

        for (Map.Entry<String, BasicPlayer> entry : MP3_LOOP.entrySet()) {

            if (entry.getValue().getStatus() == BasicPlayer.PLAYING) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static void stopAllWavResources() {

        Iterator<Map.Entry<String, ConcurrentLinkedQueue<Clip>>> iterator = WAVS_RESOURCES.entrySet().iterator();

        while (iterator.hasNext()) {

            ConcurrentLinkedQueue<Clip> list = iterator.next().getValue();

            for (Clip c : list) {
                c.stop();
            }

            iterator.remove();

        }
    }

    public static void pausar(long pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException ex) {
            Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static void GUIRun(Runnable r) {

        boolean ok;

        do {
            ok = true;

            try {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(r);
                } else {
                    r.run();
                }
            } catch (Exception ex) {
                ok = false;
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                Helpers.pausar(250);
            }

        } while (!ok);
    }

    public static void GUIRunAndWait(Runnable r) {

        boolean ok;

        do {
            ok = true;
            try {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeAndWait(r);
                } else {
                    r.run();
                }
            } catch (Exception ex) {
                ok = false;
                Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                Helpers.pausar(250);
            }
        } while (!ok);
    }

    public static void threadRun(Runnable r) {

        Thread hilo = new Thread(r);

        hilo.start();

    }

    public static FutureTask futureRun(Callable c) {

        FutureTask f = new FutureTask(c);

        Thread hilo = new Thread(f);

        hilo.start();

        return f;
    }

    public static void loadOriginalFontSizes(final Component component) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        loadOriginalFontSizes(child);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        loadOriginalFontSizes(child);
                    }
                }
            }

            Helpers.ORIGINAL_FONT_SIZE.put(component, component.getFont().getSize());

        }
    }

    public static void zoomFonts(final Component component, final float zoom_factor, final int font_reference_size) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        zoomFonts(child, zoom_factor, font_reference_size);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        zoomFonts(child, zoom_factor, font_reference_size);
                    }
                }
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    Font old_font = component.getFont();

                    Font new_font = old_font.deriveFont(old_font.getStyle(), Math.round(font_reference_size * zoom_factor));

                    component.setFont(new_font);
                }
            });
        }
    }

    public static void zoomFonts(final Component component, final float zoom_factor) {

        if (component != null) {

            if (component instanceof javax.swing.JMenu) {

                for (Component child : ((javax.swing.JMenu) component).getMenuComponents()) {
                    if (child instanceof JMenuItem) {

                        zoomFonts(child, zoom_factor);
                    }
                }

            } else if (component instanceof Container) {

                for (Component child : ((Container) component).getComponents()) {
                    if (child instanceof Container) {

                        zoomFonts(child, zoom_factor);
                    }
                }
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    Font old_font = component.getFont();

                    Font new_font = old_font.deriveFont(old_font.getStyle(), Math.round(Helpers.ORIGINAL_FONT_SIZE.get(component) * zoom_factor));

                    component.setFont(new_font);
                }
            });
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
            tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex)));
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

    public static float floatClean1D(float val) {
        return Math.round(val * 10f) / 10f;
    }

    public static int float1DSecureCompare(float val1, float val2) {

        return Float.compare(floatClean1D(val1), floatClean1D(val2));
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

    public static class JTextFieldRegularPopupMenu {

        public static void addTo(JTextField txtField) {
            JPopupMenu popup = new JPopupMenu();

            UndoManager undoManager = new UndoManager();
            txtField.getDocument().addUndoableEditListener(undoManager);
            Action undoAction = new AbstractAction("Deshacer") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (undoManager.canUndo()) {
                        undoManager.undo();
                    } else {
                    }
                }
            };
            Action copyAction = new AbstractAction("Copiar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.copy();
                }
            };
            Action cutAction = new AbstractAction("Cortar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.cut();
                }
            };
            Action pasteAction = new AbstractAction("Pegar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.paste();
                }
            };
            Action selectAllAction = new AbstractAction("Seleccionar todo") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtField.selectAll();
                }
            };
            cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control X"));
            copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
            pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));
            selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control A"));
            popup.add(undoAction);
            popup.addSeparator();
            popup.add(cutAction);
            popup.add(copyAction);
            popup.add(pasteAction);
            popup.addSeparator();
            popup.add(selectAllAction);
            Helpers.updateFonts(popup, Helpers.GUI_FONT, null);
            txtField.setComponentPopupMenu(popup);
        }

        public static void addTo(JTextArea txtArea) {
            JPopupMenu popup = new JPopupMenu();
            UndoManager undoManager = new UndoManager();
            txtArea.getDocument().addUndoableEditListener(undoManager);
            Action undoAction = new AbstractAction("Deshacer") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    if (undoManager.canUndo()) {
                        undoManager.undo();
                    } else {
                    }
                }
            };
            Action copyAction = new AbstractAction("Copiar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.copy();
                }
            };
            Action cutAction = new AbstractAction("Cortar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.cut();
                }
            };
            Action pasteAction = new AbstractAction("Pegar") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.paste();
                }
            };
            Action selectAllAction = new AbstractAction("Seleccionar todo") {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    txtArea.selectAll();
                }
            };
            cutAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control X"));
            copyAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control C"));
            pasteAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control V"));
            selectAllAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control A"));
            popup.add(undoAction);
            popup.addSeparator();
            popup.add(cutAction);
            popup.add(copyAction);
            popup.add(pasteAction);
            popup.addSeparator();
            popup.add(selectAllAction);
            Helpers.updateFonts(popup, Helpers.GUI_FONT, null);
            txtArea.setComponentPopupMenu(popup);
        }

        private JTextFieldRegularPopupMenu() {
        }
    }

    public static class TapetePopupMenu {

        public static JMenu BARAJAS_MENU = new JMenu("Barajas");
        public static JMenu TAPETES_MENU = new JMenu("Tapetes");
        public static JMenuItem EXIT_MENU;
        public static JCheckBoxMenuItem FULLSCREEN_MENU;
        public static JCheckBoxMenuItem SONIDOS_MENU;
        public static JCheckBoxMenuItem SONIDOS_COMENTARIOS_MENU;
        public static JCheckBoxMenuItem SONIDOS_MUSICA_MENU;
        public static JCheckBoxMenuItem RELOJ_MENU;
        public static JCheckBoxMenuItem AUTOREBUY_MENU;
        public static JCheckBoxMenuItem COMPACTA_MENU;
        public static JCheckBoxMenuItem CONFIRM_MENU;
        public static JCheckBoxMenuItem ANIMACION_MENU;
        public static JCheckBoxMenuItem CINEMATICAS_MENU;
        public static JCheckBoxMenuItem AUTO_ACTION_MENU;
        public static JRadioButtonMenuItem TAPETE_VERDE;
        public static JRadioButtonMenuItem TAPETE_AZUL;
        public static JRadioButtonMenuItem TAPETE_ROJO;
        public static JRadioButtonMenuItem TAPETE_MADERA;
        public static JPopupMenu popup = null;

        private static void generarBarajasMenu() {

            for (Map.Entry<String, Object[]> entry : Card.BARAJAS.entrySet()) {

                javax.swing.JRadioButtonMenuItem menu_item = new javax.swing.JRadioButtonMenuItem(entry.getKey());

                menu_item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {

                        for (Component menu : Game.getInstance().getMenu_barajas().getMenuComponents()) {
                            if (((javax.swing.JRadioButtonMenuItem) menu).getText().equals(menu_item.getText())) {
                                ((javax.swing.JRadioButtonMenuItem) menu).doClick();
                            }
                        }

                        for (Component menu : BARAJAS_MENU.getMenuComponents()) {
                            ((javax.swing.JRadioButtonMenuItem) menu).setSelected(false);
                        }

                        menu_item.setSelected(true);
                    }
                });

                BARAJAS_MENU.add(menu_item);
            }
        }

        public static void addTo(TablePanel tapete) {

            if (popup == null) {

                popup = new JPopupMenu();

                Action shortcutsAction = new AbstractAction("ATAJOS") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getShortcuts_menu().doClick();
                    }
                };

                Action exitAction = new AbstractAction("Salir") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getExit_menu().doClick();
                    }
                };

                Action soundAction = new AbstractAction("SONIDOS") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getSonidos_menu().doClick();
                    }
                };

                Action comentariosAction = new AbstractAction("Comentarios profesionales") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getSonidos_chorra_menu().doClick();
                    }
                };

                Action musicaAction = new AbstractAction("Música ambiental") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getAscensor_menu().doClick();
                    }
                };

                Action chatAction = new AbstractAction("Ver chat") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getChat_menu().doClick();
                    }
                };

                Action registroAction = new AbstractAction("Ver registro") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getRegistro_menu().doClick();
                    }
                };

                Action jugadasAction = new AbstractAction("Generador de jugadas") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getJugadas_menu().doClick();
                    }
                };

                Action fullscreenAction = new AbstractAction("PANTALLA COMPLETA") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getFull_screen_menu().doClick();
                    }
                };

                Action zoominAction = new AbstractAction("Aumentar zoom") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getZoom_menu_in().doClick();
                    }
                };

                Action zoomoutAction = new AbstractAction("Reducir zoom") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getZoom_menu_out().doClick();
                    }
                };

                Action zoomresetAction = new AbstractAction("Reset zoom") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getZoom_menu_reset().doClick();
                    }
                };

                Action compactAction = new AbstractAction("Vista compacta") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getCompact_menu().doClick();
                    }
                };

                Action relojAction = new AbstractAction("Mostrar reloj") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getTime_menu().doClick();
                    }
                };

                Action rebuyAction = new AbstractAction("Recompra automática") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getAuto_rebuy_menu().doClick();
                    }
                };

                Action confirmAction = new AbstractAction("Confirmar todas las acciones") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getConfirmar_menu().doClick();
                    }
                };

                Action cinematicasAction = new AbstractAction("Cinemáticas") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getMenu_cinematicas().doClick();
                    }
                };

                Action animacionAction = new AbstractAction("Animación al repartir") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getAnimacion_menu().doClick();
                    }
                };

                Action autoactAction = new AbstractAction("Botones AUTO") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getAuto_action_menu().doClick();
                    }
                };

                Action tapeteVerdeAction = new AbstractAction("Verde") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getMenu_tapete_verde().doClick();
                    }
                };

                Action tapeteAzulAction = new AbstractAction("Azul") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getMenu_tapete_azul().doClick();
                    }
                };

                Action tapeteRojoAction = new AbstractAction("Rojo") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getMenu_tapete_rojo().doClick();
                    }
                };

                Action tapeteMaderaAction = new AbstractAction("Sin tapete") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Game.getInstance().getMenu_tapete_madera().doClick();
                    }
                };

                popup.add(chatAction);
                popup.add(registroAction);
                popup.add(jugadasAction);
                popup.addSeparator();
                FULLSCREEN_MENU = new JCheckBoxMenuItem(fullscreenAction);
                FULLSCREEN_MENU.setSelected(Game.getInstance().isFull_screen());
                popup.add(FULLSCREEN_MENU);
                popup.addSeparator();
                popup.add(zoominAction);
                popup.add(zoomoutAction);
                popup.add(zoomresetAction);
                popup.addSeparator();
                COMPACTA_MENU = new JCheckBoxMenuItem(compactAction);
                COMPACTA_MENU.setSelected(Game.VISTA_COMPACTA);
                popup.add(COMPACTA_MENU);
                popup.addSeparator();
                SONIDOS_MENU = new JCheckBoxMenuItem(soundAction);
                SONIDOS_MENU.setSelected(Game.SONIDOS);
                popup.add(SONIDOS_MENU);
                SONIDOS_COMENTARIOS_MENU = new JCheckBoxMenuItem(comentariosAction);
                SONIDOS_COMENTARIOS_MENU.setSelected(Game.SONIDOS_CHORRA);
                SONIDOS_COMENTARIOS_MENU.setEnabled(Game.SONIDOS);
                popup.add(SONIDOS_COMENTARIOS_MENU);
                SONIDOS_MUSICA_MENU = new JCheckBoxMenuItem(musicaAction);
                SONIDOS_MUSICA_MENU.setSelected(Game.MUSICA_AMBIENTAL);
                SONIDOS_MUSICA_MENU.setEnabled(Game.SONIDOS);
                popup.add(SONIDOS_MUSICA_MENU);
                popup.addSeparator();
                popup.add(shortcutsAction);
                CONFIRM_MENU = new JCheckBoxMenuItem(confirmAction);
                CONFIRM_MENU.setSelected(Game.CONFIRM_ACTIONS);
                popup.add(CONFIRM_MENU);
                AUTO_ACTION_MENU = new JCheckBoxMenuItem(autoactAction);
                AUTO_ACTION_MENU.setSelected(Game.AUTO_ACTION_BUTTONS);
                popup.add(AUTO_ACTION_MENU);
                popup.addSeparator();
                CINEMATICAS_MENU = new JCheckBoxMenuItem(cinematicasAction);
                CINEMATICAS_MENU.setSelected(Game.CINEMATICAS);
                popup.add(CINEMATICAS_MENU);
                ANIMACION_MENU = new JCheckBoxMenuItem(animacionAction);
                ANIMACION_MENU.setSelected(Game.ANIMACION_REPARTIR);
                popup.add(ANIMACION_MENU);
                popup.addSeparator();
                RELOJ_MENU = new JCheckBoxMenuItem(relojAction);
                RELOJ_MENU.setSelected(Game.SHOW_CLOCK);
                popup.add(RELOJ_MENU);
                popup.addSeparator();
                generarBarajasMenu();
                popup.add(BARAJAS_MENU);
                TAPETE_VERDE = new JRadioButtonMenuItem(tapeteVerdeAction);
                TAPETE_AZUL = new JRadioButtonMenuItem(tapeteAzulAction);
                TAPETE_ROJO = new JRadioButtonMenuItem(tapeteRojoAction);
                TAPETE_MADERA = new JRadioButtonMenuItem(tapeteMaderaAction);
                TAPETES_MENU.add(TAPETE_VERDE);
                TAPETES_MENU.add(TAPETE_AZUL);
                TAPETES_MENU.add(TAPETE_ROJO);
                TAPETES_MENU.add(TAPETE_MADERA);
                popup.add(TAPETES_MENU);
                popup.addSeparator();
                AUTOREBUY_MENU = new JCheckBoxMenuItem(rebuyAction);
                AUTOREBUY_MENU.setSelected(Game.AUTO_REBUY);
                popup.add(AUTOREBUY_MENU);
                popup.addSeparator();
                EXIT_MENU = new JMenuItem(exitAction);
                popup.add(EXIT_MENU);

                Helpers.updateFonts(popup, Helpers.GUI_FONT, 2);
            }

            tapete.setComponentPopupMenu(popup);
        }

        private TapetePopupMenu() {
        }
    }

    public static class OSValidator {

        private static final String OS = System.getProperty("os.name").toLowerCase();

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
