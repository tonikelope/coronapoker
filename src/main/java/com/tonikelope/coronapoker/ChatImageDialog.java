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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Dialog for sending a chat image by URL (or opening a Google Images search when the
 * input isn't a URL), with a persisted history panel of past sends/receives and an
 * anti-flood cooldown between sends.
 *
 * @author tonikelope
 */
public class ChatImageDialog extends JDialog {

    public static final int MAX_IMAGE_WIDTH = (int) Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.20f);
    public static final ConcurrentHashMap<String, ImageIcon> STATIC_IMAGE_CACHE = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Object[]> GIF_CACHE = new ConcurrentHashMap<>();

    // Bound the two decoded-image caches so a chat-heavy session can't grow them without limit.
    // CACHE_ORDER tracks insertion order (guarded by the class monitor); eviction drops the OLDEST
    // url from both maps, minimizing the chance of racing a lock-free reader of a recently-shown
    // image. Callers put via putStaticImageCache/putGifCache, never into the maps directly.
    private static final int MAX_CHAT_IMAGE_CACHE = 300;
    private static final ArrayDeque<String> CACHE_ORDER = new ArrayDeque<>();

    public static void putStaticImageCache(String url, ImageIcon icon) {
        if (STATIC_IMAGE_CACHE.putIfAbsent(url, icon) == null) {
            trackAndEvictCache(url);
        }
    }

    public static void putGifCache(String url, Object[] value) {
        if (GIF_CACHE.putIfAbsent(url, value) == null) {
            trackAndEvictCache(url);
        }
    }

    private static synchronized void trackAndEvictCache(String url) {
        CACHE_ORDER.remove(url);
        CACHE_ORDER.addLast(url);
        while (CACHE_ORDER.size() > MAX_CHAT_IMAGE_CACHE) {
            String oldest = CACHE_ORDER.pollFirst();
            if (oldest != null) {
                STATIC_IMAGE_CACHE.remove(oldest);
                GIF_CACHE.remove(oldest);
            }
        }
    }

    public static final int ANTI_FLOOD_IMAGE = 2;
    private static final ThreadPoolExecutor IMAGE_THREAD_POOL = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
    private static final ArrayDeque<String> HISTORIAL = cargarHistorial();
    private static final Object LOAD_IMAGES_LOCK = new Object();
    private volatile static boolean AUTO_REC;

    // Thread-safe atomic counter for flood prevention
    private static final java.util.concurrent.atomic.AtomicInteger ANTI_FLOOD_WAIT = new java.util.concurrent.atomic.AtomicInteger(0);

    // Timer to safely update the progress bar UI independently of the countdown thread
    private javax.swing.Timer ui_update_timer;

    private volatile JLabel last_focused = null;
    private volatile boolean exit = false;
    private volatile boolean exiting = false;

    /**
     * @param parent owner frame, used for sizing/centering
     * @param modal whether the dialog blocks its owner
     * @param h dialog height in pixels, applied after the initial pack()
     */
    public ChatImageDialog(java.awt.Frame parent, boolean modal, int h) {

        super(parent, modal);

        initComponents();

        historial_panel.setLayout(new Helpers.WrapLayout());

        historial_panel.setFocusTraversalPolicy(new Helpers.WrapLayoutFocusTraversalPolicyGPT());

        historial_panel.setFocusTraversalPolicyProvider(true);

        Helpers.setTranslatedTitle(this, "ui.enviar_imagen");

        Helpers.JTextFieldRegularPopupMenu.addTo(image_url);

        send_button.setEnabled(false);

        clear_button.setEnabled(false);

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);

        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        auto_recibir_checkbox.setSelected(AUTO_REC);

        // Safe read of the atomic integer
        if (ANTI_FLOOD_WAIT.get() > 0) {
            Helpers.resetBarra(barra, ANTI_FLOOD_IMAGE);
            barra.setValue(ANTI_FLOOD_WAIT.get());
            barra.setVisible(true);
        } else {
            Helpers.barraIndeterminada(barra);
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
        Helpers.translateComponents(this, false);
        pack();
        setSize(getWidth(), h);
        setPreferredSize(getSize());
        pack();
        Helpers.windowAutoFitToRemoveHScrollBar(this, scroll_panel.getHorizontalScrollBar(), parent.getWidth());

        // Initialize and start the UI update timer to poll the atomic integer
        ui_update_timer = new javax.swing.Timer(500, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int waitTime = ANTI_FLOOD_WAIT.get();
                if (waitTime > 0) {
                    if (!barra.isVisible() || barra.isIndeterminate()) {
                        Helpers.resetBarra(barra, ANTI_FLOOD_IMAGE);
                        barra.setVisible(true);
                    }
                    barra.setValue(waitTime);
                } else {
                    if (barra.isVisible() && !barra.isIndeterminate()) {
                        barra.setVisible(false);
                        Helpers.barraIndeterminada(barra);
                    }
                }
            }
        });
        ui_update_timer.start();

        cargarHistorialPanel();

    }

    /**
     * Stops the progress-bar update timer (it would otherwise keep firing and hold a
     * reference to this dialog) before delegating to the superclass.
     */
    @Override
    public void dispose() {
        if (ui_update_timer != null && ui_update_timer.isRunning()) {
            ui_update_timer.stop();
        }
        super.dispose();
    }

    private void cargarHistorialPanel() {

        synchronized (LOAD_IMAGES_LOCK) {
            // GUIRun (not GUIRunAndWait): avoid blocking on the EDT while holding LOAD_IMAGES_LOCK
            Helpers.GUIRun(ChatImageDialog.this.historial_panel::removeAll);
        }

        // Create a snapshot of the Deque to prevent ConcurrentModificationException during iteration
        final ArrayList<String> historySnapshot;
        synchronized (ChatImageDialog.class) {
            historySnapshot = new ArrayList<>(HISTORIAL);
        }

        Helpers.threadRun(() -> {
            if (!exit) {
                // Iterate over the thread-safe snapshot
                for (String h : historySnapshot) {

                    // Prevent deadlock: run UI creation asynchronously
                    Helpers.GUIRun(new Runnable() {
                        private volatile JLabel label;

                        @Override
                        public void run() {
                            label = new JLabel();
                            label.setFocusable(true);
                            label.setAlignmentX(0.5f);
                            label.setBorder(new EmptyBorder(10, 0, 10, 0));
                            label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                            label.setIcon(new ImageIcon(getClass().getResource("/images/loading.gif")));

                            label.revalidate();
                            label.repaint();
                            label.addFocusListener(new FocusListener() {
                                @Override
                                public void focusGained(FocusEvent fe) {
                                    label.setBorder(new LineBorder(Color.YELLOW, 5));
                                    ChatImageDialog.this.historial_panel.scrollRectToVisible(label.getBounds());
                                }

                                @Override
                                public void focusLost(FocusEvent fe) {
                                    if (label.getBorder() instanceof LineBorder && ((LineBorder) label.getBorder()).getLineColor() == Color.YELLOW) {
                                        label.setBorder(new EmptyBorder(10, 0, 10, 0));
                                        ChatImageDialog.this.last_focused = label;
                                    }
                                }
                            });
                            label.addKeyListener(new KeyListener() {
                                @Override
                                public void keyTyped(KeyEvent ke) {
                                }

                                @Override
                                public void keyPressed(KeyEvent ke) {
                                    switch (ke.getKeyCode()) {
                                        case KeyEvent.VK_ESCAPE:
                                            formWindowClosing(null);
                                            break;
                                        case KeyEvent.VK_S:
                                            ChatImageDialog.this.image_url.setText(h);
                                            ChatImageDialog.this.image_url.setEnabled(false);
                                            ChatImageDialog.this.send_buttonActionPerformed(null);
                                            break;
                                        case KeyEvent.VK_1:
                                            if (!ChatImageDialog.this.exiting) {
                                                ChatImageDialog.this.exiting = true;
                                                Helpers.threadRun(() -> {
                                                    synchronized (LOAD_IMAGES_LOCK) {
                                                        ChatImageDialog.this.exit = true;
                                                    }
                                                    Helpers.GUIRun(() -> {
                                                        ChatImageDialog.this.dispose();
                                                        if (WaitingRoomFrame.getInstance().isVisible()) {
                                                            WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                                                        }
                                                    });
                                                });
                                            }
                                            break;
                                        case KeyEvent.VK_BACK_SPACE:
                                            label.setBorder(new LineBorder(Color.RED, 5));
                                            if (Helpers.mostrarMensajeInformativoSINO(ChatImageDialog.this, Translator.translate("chat.eliminar_esta_imagen_del_historial"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {
                                                Helpers.threadRun(() -> {
                                                    synchronized (LOAD_IMAGES_LOCK) {
                                                        if (!ChatImageDialog.this.exit) {
                                                            Helpers.GUIRun(() -> {
                                                                ChatImageDialog.this.historial_panel.remove(label);
                                                                ChatImageDialog.this.historial_panel.revalidate();
                                                                ChatImageDialog.this.historial_panel.repaint();

                                                                if (ChatImageDialog.this.last_focused != null) {
                                                                    ChatImageDialog.this.last_focused.requestFocus();
                                                                }
                                                            });
                                                            ChatImageDialog.removeFromHistory(h);
                                                        }
                                                    }
                                                });
                                            } else {
                                                label.requestFocus();
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }

                                @Override
                                public void keyReleased(KeyEvent ke) {
                                }
                            });
                            label.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseReleased(MouseEvent e) {
                                    if (!Helpers.isReleaseInsideComponent(e)) {
                                        return;
                                    }
                                    if (SwingUtilities.isLeftMouseButton(e)) {
                                        ChatImageDialog.this.image_url.setText(h);
                                        ChatImageDialog.this.image_url.setEnabled(false);
                                        ChatImageDialog.this.send_buttonActionPerformed(null);
                                    } else if (SwingUtilities.isRightMouseButton(e)) {
                                        label.setBorder(new LineBorder(Color.RED, 5));
                                        if (Helpers.mostrarMensajeInformativoSINO(ChatImageDialog.this, Translator.translate("chat.eliminar_esta_imagen_del_historial"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {
                                            Helpers.threadRun(() -> {
                                                synchronized (LOAD_IMAGES_LOCK) {
                                                    if (!exit) {
                                                        Helpers.GUIRun(() -> {
                                                            ChatImageDialog.this.historial_panel.remove(label);
                                                            ChatImageDialog.this.historial_panel.revalidate();
                                                            ChatImageDialog.this.historial_panel.repaint();

                                                            if (last_focused != null) {
                                                                ChatImageDialog.this.last_focused.requestFocus();
                                                            }
                                                        });
                                                        ChatImageDialog.removeFromHistory(h);
                                                    }
                                                }
                                            });
                                        } else {
                                            label.setBorder(new EmptyBorder(10, 0, 10, 0));
                                            ChatImageDialog.this.image_url.requestFocus();
                                        }
                                    }
                                }
                            });
                            synchronized (LOAD_IMAGES_LOCK) {
                                ((Helpers.WrapLayoutFocusTraversalPolicyGPT) ChatImageDialog.this.historial_panel.getFocusTraversalPolicy()).addComponent(label);
                                ChatImageDialog.this.historial_panel.add(label);
                                ChatImageDialog.this.historial_panel.revalidate();
                                ChatImageDialog.this.historial_panel.repaint();
                                loadImage(label, h);
                            }
                        }
                    });
                }
            }
            Helpers.GUIRun(() -> {
                if (barra.isIndeterminate()) {
                    ChatImageDialog.this.barra.setVisible(false);
                }
                ChatImageDialog.this.send_button.setEnabled(true);
                // Check against the actual size of the synchronized history
                ChatImageDialog.this.clear_button.setEnabled(!historySnapshot.isEmpty());
                ChatImageDialog.this.revalidate();
                ChatImageDialog.this.repaint();
            });
        });
    }

    private void loadImage(JLabel label, String url) {

        IMAGE_THREAD_POOL.submit(new Runnable() {
            private volatile ImageIcon image;
            private volatile boolean isgif;

            @Override
            public void run() {
                if (!exit) {
                    try {

                        // getImageFromCache snapshots the caches and returns null if the entry was
                        // evicted, so fall back to a fresh load rather than NPE on a null icon.
                        ImageIcon cached = getImageFromCache(url);
                        image = (cached != null) ? cached : ImageCacheManager.getIcon(new URL(url));
                        MediaTracker tracker = new MediaTracker(label);
                        tracker.addImage(image.getImage(), 0);
                        tracker.waitForAll();

                        if (image.getImageLoadStatus() != MediaTracker.ERRORED) {

                            isgif = GIF_CACHE.containsKey(url);

                            // Cache the width-capped version, independent of zoom: the cache is canonical
                            // and must not stay tied to whichever zoom was active on first load.
                            if (image.getIconWidth() > ChatImageDialog.MAX_IMAGE_WIDTH) {
                                isgif = (isgif || Helpers.isImageGIF(new URL(url)));
                                image = new ImageIcon(image.getImage().getScaledInstance(ChatImageDialog.MAX_IMAGE_WIDTH, (int) Math.round((image.getIconHeight() * ChatImageDialog.MAX_IMAGE_WIDTH) / image.getIconWidth()), isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));
                            }

                            if (isgif = isgif || Helpers.isImageGIF(new URL(url))) {
                                putGifCache(url, new Object[]{image, Helpers.getGIFLength(new URL(url))});
                            } else if (!GIF_CACHE.containsKey(url)) {
                                putStaticImageCache(url, image);
                            }

                            // Display copy scaled by the dialog zoom (not cached). Scale ALL images, not
                            // just the ones over the cap, so small ones also shrink/grow with the zoom.
                            ImageIcon display_image = image;
                            int display_w = Math.round(image.getIconWidth() * Helpers.DIALOG_ZOOM);
                            if (display_w > 0 && display_w != image.getIconWidth()) {
                                display_image = new ImageIcon(image.getImage().getScaledInstance(display_w, (int) Math.round((image.getIconHeight() * display_w) / image.getIconWidth()), isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));
                            }
                            final ImageIcon final_display = display_image;

                            Helpers.GUIRun(() -> {
                                label.setIcon(final_display);
                                label.revalidate();
                                label.repaint();

                                ChatImageDialog.this.historial_panel.revalidate();
                                ChatImageDialog.this.historial_panel.repaint();
                            });

                        } else {
                            Helpers.threadRun(() -> {
                                synchronized (LOAD_IMAGES_LOCK) {
                                    if (!exit) {
                                        // GUIRun: don't block on the EDT while holding LOAD_IMAGES_LOCK
                                        Helpers.GUIRun(() -> {
                                            ChatImageDialog.this.historial_panel.remove(label);
                                            ChatImageDialog.this.historial_panel.revalidate();
                                            ChatImageDialog.this.historial_panel.repaint();
                                        });
                                        ChatImageDialog.removeFromHistory(url);
                                    }
                                }
                            });
                            Logger.getLogger(ChatImageDialog.class.getName()).log(Level.WARNING, "ERROR LOADING IMAGE -> {0}", url);
                        }

                    } catch (Exception ex) {
                        Helpers.threadRun(() -> {
                            synchronized (LOAD_IMAGES_LOCK) {
                                if (!exit) {
                                    // GUIRun: don't block on the EDT while holding LOAD_IMAGES_LOCK
                                    Helpers.GUIRun(() -> {
                                        ChatImageDialog.this.historial_panel.remove(label);
                                        ChatImageDialog.this.historial_panel.revalidate();
                                        ChatImageDialog.this.historial_panel.repaint();
                                    });
                                    ChatImageDialog.removeFromHistory(url);
                                }
                            }
                        });
                        Logger.getLogger(ChatImageDialog.class.getName()).log(Level.WARNING, "ERROR LOADING IMAGE -> {0}", url);
                    }
                }
            }
        });
    }

    private ImageIcon getImageFromCache(String url) {
        // Snapshot the lock-free reads into locals so a concurrent eviction between a containsKey
        // and a get can't NPE (and can't trip loadImage's catch into deleting a still-valid image
        // from the persisted history). Returns null when not cached; callers fall back to a load.
        ImageIcon cached_static = STATIC_IMAGE_CACHE.get(url);
        if (cached_static != null) {
            return cached_static;
        }
        Object[] cached_gif = GIF_CACHE.get(url);
        if (cached_gif != null) {
            return (ImageIcon) cached_gif[0];
        }
        return null;
    }

    /**
     * Drops {@code url} from both image caches and from the persisted history.
     *
     * @param url image URL to forget
     */
    public synchronized static void removeFromHistory(String url) {

        STATIC_IMAGE_CACHE.remove(url);

        GIF_CACHE.remove(url);

        CACHE_ORDER.remove(url);

        HISTORIAL.remove(url);

        guardarHistorial();
    }

    private synchronized static void updateHistorialEnviados(String url) {

        if (HISTORIAL.contains(url)) {
            HISTORIAL.remove(url);
        }

        HISTORIAL.addFirst(url);

        guardarHistorial();

    }

    /**
     * Appends a received image URL to the history, if auto-receive is enabled.
     *
     * @param url received image URL
     */
    public synchronized static void updateHistorialRecibidos(String url) {

        if (AUTO_REC && !HISTORIAL.contains(url)) {

            HISTORIAL.addLast(url);

            guardarHistorial();

        }
    }

    /**
     * Appends received image URLs to the history, if auto-receive is enabled.
     *
     * @param urls received image URLs
     */
    public synchronized static void updateHistorialRecibidos(ArrayList<String> urls) {

        if (AUTO_REC) {

            for (String s : urls) {
                if (!HISTORIAL.contains(s)) {
                    HISTORIAL.addLast(s);
                }
            }

            guardarHistorial();
        }
    }

    /**
     * Persists the current history (Base64-encoded, {@code @}-joined) and the
     * auto-receive flag to the properties file.
     */
    public synchronized static void guardarHistorial() {

        String[] historial = HISTORIAL.toArray(new String[0]);

        for (int i = 0; i < historial.length; i++) {

            try {
                historial[i] = Base64.getEncoder().encodeToString(historial[i].getBytes("UTF-8"));

            } catch (Exception ex) {
                Logger.getLogger(ChatImageDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        Helpers.PROPERTIES.setProperty("chat_img_hist", String.join("@", historial));

        Helpers.PROPERTIES.setProperty("chat_img_hist_auto_rec", String.valueOf(AUTO_REC));

        Helpers.savePropertiesFile();

    }

    private synchronized static ArrayDeque<String> cargarHistorial() {

        ArrayDeque<String> historial = new ArrayDeque<>();

        String hist_b64 = Helpers.PROPERTIES.getProperty("chat_img_hist", "");

        if (!hist_b64.isBlank()) {

            String[] hist = hist_b64.split("@");

            for (String h : hist) {
                try {
                    historial.addLast(new String(Base64.getDecoder().decode(h), "UTF-8"));
                } catch (Exception ex) {
                    Logger.getLogger(ChatImageDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        AUTO_REC = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("chat_img_hist_auto_rec", "true"));

        return historial;

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        image_url = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        send_button = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        auto_recibir_checkbox = new javax.swing.JCheckBox();
        clear_button = new javax.swing.JButton();
        barra = new javax.swing.JProgressBar();
        scroll_panel = new javax.swing.JScrollPane();
        historial_panel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Enviar imagen");
        setUndecorated(true);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 102, 0), 10));

        image_url.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        image_url.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                image_urlActionPerformed(evt);
            }
        });
        image_url.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                image_urlKeyPressed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        jLabel1.setText("URL:");
        jLabel1.setFocusable(false);

        send_button.setBackground(new java.awt.Color(0, 130, 0));
        send_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        send_button.setForeground(new java.awt.Color(255, 255, 255));
        send_button.setText("Enviar");
        send_button.putClientProperty("i18n.key", "ui.enviar");
        send_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        send_button.setFocusable(false);
        send_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                send_buttonActionPerformed(evt);
            }
        });

        jLabel2.setFont(new java.awt.Font("Dialog", 2, 12)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("Nota: también puedes buscar desde aquí imágenes en Google introduciendo palabras clave.");
        jLabel2.putClientProperty("i18n.key", "ui.nota_tambien_puedes_buscar_desde");
        jLabel2.setFocusable(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(image_url)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(send_button))
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(image_url)
                    .addComponent(jLabel1)
                    .addComponent(send_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addContainerGap())
        );

        auto_recibir_checkbox.setText("Añadir imágenes recibidas al historial");
        auto_recibir_checkbox.putClientProperty("i18n.key", "chat.anadir_imagenes_recibidas_al_historial");
        auto_recibir_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        auto_recibir_checkbox.setFocusable(false);
        auto_recibir_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auto_recibir_checkboxActionPerformed(evt);
            }
        });

        clear_button.setBackground(new java.awt.Color(255, 0, 0));
        clear_button.setForeground(new java.awt.Color(255, 255, 255));
        clear_button.setText("Borrar historial");
        clear_button.putClientProperty("i18n.key", "chat.borrar_historial");
        clear_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        clear_button.setFocusable(false);
        clear_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clear_buttonActionPerformed(evt);
            }
        });

        barra.setFocusable(false);

        scroll_panel.setBorder(null);
        scroll_panel.setFocusCycleRoot(true);

        javax.swing.GroupLayout historial_panelLayout = new javax.swing.GroupLayout(historial_panel);
        historial_panel.setLayout(historial_panelLayout);
        historial_panelLayout.setHorizontalGroup(
            historial_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 516, Short.MAX_VALUE)
        );
        historial_panelLayout.setVerticalGroup(
            historial_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 555, Short.MAX_VALUE)
        );

        scroll_panel.setViewportView(historial_panel);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scroll_panel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(barra, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(auto_recibir_checkbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(clear_button))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 523, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(auto_recibir_checkbox)
                    .addComponent(clear_button))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void send_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_send_buttonActionPerformed
        // TODO add your handling code here:

        String url = ChatImageDialog.this.image_url.getText().trim();

        if (url.startsWith("http")) {

            // Safe atomic read
            if (ANTI_FLOOD_WAIT.get() > 0) {
                Helpers.mostrarMensajeError(ChatImageDialog.this, Translator.translate("ui.espera_un_poco"));

                if (!ChatImageDialog.this.image_url.isEnabled()) {
                    ChatImageDialog.this.image_url.setText("");
                    ChatImageDialog.this.image_url.setEnabled(true);
                }

            } else {

                ChatImageDialog.this.send_button.setEnabled(false);
                ChatImageDialog.this.image_url.setEnabled(false);
                ChatImageDialog.this.barra.setVisible(true);

                Helpers.threadRun(() -> {
                    // HISTORIAL is also mutated from other threads, so read it under the class lock
                    boolean existsInHistory;
                    synchronized (ChatImageDialog.class) {
                        existsInHistory = HISTORIAL.contains(url);
                    }

                    if (existsInHistory) {
                        synchronized (LOAD_IMAGES_LOCK) {
                            ChatImageDialog.this.exit = true;
                        }
                        Helpers.GUIRun(() -> {
                            ChatImageDialog.this.dispose();

                            if (WaitingRoomFrame.getInstance().isVisible()) {
                                if (WaitingRoomFrame.getInstance().getEmoji_scroll_panel().isVisible()) {
                                    WaitingRoomFrame.getInstance().getEmoji_button().doClick();
                                }
                                WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                            } else {
                                try {
                                    GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{GameFrame.getInstance().getLocalPlayer().getNickname(), new URL(url)});
                                } catch (MalformedURLException ex) {
                                    Logger.getLogger(ChatImageDialog.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {
                                    GameFrame.NOTIFY_CHAT_QUEUE.notifyAll();
                                }
                            }
                            WaitingRoomFrame.getInstance().chatHTMLAppend(WaitingRoomFrame.getInstance().getLocal_nick() + ":(" + Helpers.getLocalTimeString() + ") " + url.replaceAll("^http", "img") + "\n");
                        });
                        WaitingRoomFrame.getInstance().enviarMensajeChat(WaitingRoomFrame.getInstance().getLocal_nick(), url.replaceAll("^http", "img"));
                        updateHistorialEnviados(url);

                        // Safe atomic write
                        ANTI_FLOOD_WAIT.set(ANTI_FLOOD_IMAGE);

                        // Background thread ONLY handles the logical countdown, NOT the UI.
                        // The UI is now safely updated by the ui_update_timer in the constructor.
                        Helpers.threadRun(() -> {
                            while (ANTI_FLOOD_WAIT.get() > 0) {
                                Helpers.pausar(1000);
                                ANTI_FLOOD_WAIT.decrementAndGet();
                            }
                        });
                    } else {
                        try {
                            ImageIcon image = ImageCacheManager.getIcon(new URL(url));

                            if (image.getImageLoadStatus() != MediaTracker.ERRORED) {
                                updateHistorialEnviados(url);
                                ChatImageDialog.this.cargarHistorialPanel();
                                Helpers.GUIRun(() -> {
                                    ChatImageDialog.this.image_url.setText("");
                                });
                            } else {
                                Helpers.mostrarMensajeError(ChatImageDialog.this, Translator.translate("error.error_la_imagen_no_es"));
                            }
                        } catch (MalformedURLException ex) {
                            Logger.getLogger(ChatImageDialog.class.getName()).log(Level.SEVERE, null, ex);
                            Helpers.mostrarMensajeError(ChatImageDialog.this, Translator.translate("error.error_la_imagen_no_es"));
                        }
                        Helpers.GUIRun(() -> {
                            ChatImageDialog.this.barra.setVisible(false);
                            ChatImageDialog.this.image_url.setEnabled(true);
                            ChatImageDialog.this.send_button.setEnabled(true);
                            ChatImageDialog.this.image_url.requestFocus();
                        });
                    }
                });
            }

        } else if (!url.isBlank()) {

            ChatImageDialog.this.send_button.setEnabled(false);
            // Make the bar indeterminate since we don't know how long the OS takes to open the browser
            Helpers.barraIndeterminada(ChatImageDialog.this.barra);
            ChatImageDialog.this.barra.setVisible(true);

            // Offload browser opening to a background thread so the UI can update
            Helpers.threadRun(() -> {
                try {
                    Helpers.openBrowserURL("https://www.google.com/search?q=" + URLEncoder.encode(url, "UTF-8") + "&tbm=isch");
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(ChatImageDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                // Restore UI on the Event Dispatch Thread once the operation is dispatched
                Helpers.GUIRun(() -> {
                    ChatImageDialog.this.barra.setVisible(false);
                    ChatImageDialog.this.send_button.setEnabled(true);
                    ChatImageDialog.this.image_url.requestFocus();
                });
            });

        } else {
            ChatImageDialog.this.image_url.requestFocus();
        }

    }//GEN-LAST:event_send_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:

        if (!exiting) {
            exiting = true;

            Helpers.threadRun(() -> {
                synchronized (LOAD_IMAGES_LOCK) {
                    exit = true;
                }
                Helpers.GUIRun(() -> {
                    dispose();

                    if (WaitingRoomFrame.getInstance().isVisible()) {
                        WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                    }
                });
            });

        }

    }//GEN-LAST:event_formWindowClosing

    private void image_urlActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_image_urlActionPerformed
        // TODO add your handling code here:
        send_button.doClick();
    }//GEN-LAST:event_image_urlActionPerformed

    private void clear_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clear_buttonActionPerformed
        // TODO add your handling code here:
        if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("chat.borrar_todas_las_imagenes_del"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {

            // Clear all four under the class monitor (as the puts/eviction do) so a concurrent
            // cache put can't leave an entry tracked in one structure but not the others.
            synchronized (ChatImageDialog.class) {
                STATIC_IMAGE_CACHE.clear();
                GIF_CACHE.clear();
                HISTORIAL.clear();
                CACHE_ORDER.clear();
            }

            historial_panel.removeAll();
            clear_button.setEnabled(false);
            historial_panel.revalidate();
            historial_panel.repaint();

            Helpers.threadRun(ChatImageDialog::guardarHistorial);
        }

        image_url.requestFocus();
    }//GEN-LAST:event_clear_buttonActionPerformed

    private void auto_recibir_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auto_recibir_checkboxActionPerformed
        // TODO add your handling code here:

        AUTO_REC = auto_recibir_checkbox.isSelected();

        Helpers.PROPERTIES.setProperty("chat_img_hist_auto_rec", String.valueOf(AUTO_REC));

        Helpers.savePropertiesFile();

        image_url.requestFocus();

    }//GEN-LAST:event_auto_recibir_checkboxActionPerformed

    private void image_urlKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_image_urlKeyPressed
        // TODO add your handling code here:
        if (evt.getKeyCode() == KeyEvent.VK_ESCAPE || (evt.getKeyCode() == KeyEvent.VK_1 && evt.isControlDown())) {

            if (!exiting) {
                exiting = true;

                Helpers.threadRun(() -> {
                    synchronized (LOAD_IMAGES_LOCK) {
                        exit = true;
                    }
                    Helpers.GUIRun(() -> {
                        dispose();

                        if (WaitingRoomFrame.getInstance().isVisible()) {
                            WaitingRoomFrame.getInstance().getChat_box().requestFocus();
                        }
                    });
                });

            }

        }
    }//GEN-LAST:event_image_urlKeyPressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox auto_recibir_checkbox;
    private javax.swing.JProgressBar barra;
    private javax.swing.JButton clear_button;
    private javax.swing.JPanel historial_panel;
    private javax.swing.JTextField image_url;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane scroll_panel;
    private javax.swing.JButton send_button;
    // End of variables declaration//GEN-END:variables
}
