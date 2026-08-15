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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/*
    "Gratitude is the mark of noble souls"

    SPECIAL THANKS TO:

    https://www.java.com/

    https://netbeans.apache.org/

    https://poker.cs.ualberta.ca/

    https://github.com/

    https://stackoverflow.com/

    https://www.3dgifmaker.com/

    https://www.picturetopeople.org/

    https://www.blender.org/ ( https://www.youtube.com/watch?v=0JnmWfWuMDw )

    Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones, Aidan N. Gomez, Lukasz Kaiser, Illia Polosukhin
    https://arxiv.org/abs/1706.03762


 */
/**
 * "About" dialog: credits, version/system info, an animated corona-logo (played back
 * pre-decoded to fixed frames to avoid AWT GIF flicker, see {@link #setupLogoAnimation()}),
 * live memory/thread stats, and optional MOD branding. Mutes whatever background music loop
 * was already playing for its own track and restores it on close.
 */
public class AboutDialog extends JDialog {

    public static final String VERSION = "23.47";
    public static final String UPDATE_URL = "https://github.com/tonikelope/coronapoker/releases/latest";

    public static final String TITLE = "about.titulo";
    public static final int MAX_MOD_LOGO_HEIGHT = 75;
    public static final int MEM_TIMER = 2000;
    private volatile String last_mp3_loop = null;
    private volatile int c = 0;
    private volatile Timer memory_timer = null;

    // Animated logo (corona_logo.gif) is played back pre-decoded to fixed frames instead of
    // via AWT's GIF player: this GIF's disposal method is restoreToBackgroundColor on every
    // frame, which flashes the background between frames on some Windows render pipelines.
    // Painting each frame as a static icon under Swing's double buffering avoids that. Same
    // engine (PreRenderedGif + time-based frameAt) as the shuffle GIF.
    private static volatile PreRenderedGif LOGO_ANIM_CACHE = null;
    private static volatile boolean LOGO_ANIM_TRIED = false;
    private PreRenderedGif logo_anim = null;
    private javax.swing.Timer logo_timer = null;
    private volatile int logo_frame_idx = 0;
    private volatile long logo_t0 = 0L;
    private volatile boolean logo_clock_started = false;

    /**
     * @param parent owner frame
     * @param modal whether the dialog blocks input to the owner
     */
    public AboutDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();
        Helpers.setTranslatedTitle(this, TITLE);

        mod_bar.setVisible(false);
        main_scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        main_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);
        memory_usage.setText(Helpers.getMemoryUsage());
        threads.setText(String.valueOf(Helpers.THREAD_POOL.getActiveCount() + 2) + "/" + String.valueOf(Helpers.THREAD_POOL.getPoolSize() + 2) + " " + Translator.translate("ui.hilos"));

        if (Init.MOD != null) {
            mod_label.setText(Init.MOD.get("name") + " " + Init.MOD.get("version"));

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/mod.png"))) {
                Image logo = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/mod.png").getImage();

                if (logo.getHeight(null) > MAX_MOD_LOGO_HEIGHT || logo.getWidth(null) > MAX_MOD_LOGO_HEIGHT) {

                    int new_height = MAX_MOD_LOGO_HEIGHT;

                    int new_width = Math.round(((float) logo.getWidth(null) * MAX_MOD_LOGO_HEIGHT) / logo.getHeight(null));

                    mod_label.setIcon(new ImageIcon(logo.getScaledInstance(new_width, new_height, Image.SCALE_SMOOTH)));

                } else {
                    mod_label.setIcon(new ImageIcon(logo));

                }
            }
        } else {
            mod_label.setVisible(false);
        }

        // GUI_FONT family at DESIGN size (as always) + pack to MEASURE the design size.
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();

        // Global dialog zoom: scale decorations BEFORE zoomDialog repacks, so their scaled size
        // is accounted for. The animated logo (corona_logo.gif) doesn't use AWT's GIF player (it
        // flickers on some machines, see the field comments above): it's played back pre-decoded
        // to fixed frames scaled to design size × zoom (setupLogoAnimation). The rest are PNGs
        // scaled with scaleDialogIcon. zoomDialog then scales fonts and the window.
        setupLogoAnimation();
        Helpers.scaleDialogIcon(dedicado, "/images/luto.png");
        Helpers.scaleDialogIcon(jLabel12, "/images/open-book.png");
        Helpers.scaleDialogIcon(jLabel9, "/images/cruz.png");
        // The content sits in a JScrollPane with one line PREFIXED to a fixed width in the .form
        // (jLabel5=804); without this it wouldn't shrink below that width when the window shrinks
        // (horizontal scrollbar). This makes the content track the viewport width and re-wrap.
        // Install BEFORE zoomDialog (which resizes the window).
        Helpers.trackViewportWidth(main_scroll_panel);
        Helpers.zoomDialog(this);

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(new Dimension(w, h));

            setPreferredSize(getSize());

            pack();
        }

        setResizable(false);

        memory_timer = new Timer(MEM_TIMER, (ActionEvent ae) -> {
            memory_usage.setText(Helpers.getMemoryUsage());
            threads.setText(String.valueOf(Helpers.THREAD_POOL.getActiveCount() + 2) + "/" + String.valueOf(Helpers.THREAD_POOL.getPoolSize() + 2) + " " + Translator.translate("ui.hilos"));
        });

        memory_timer.setRepeats(true);
        memory_timer.setCoalesce(false);

    }

    // Logo GIF decoded ONCE into full frames (cached; the frames are immutable and shared
    // across dialog openings). Null if decoding fails, in which case the caller falls back
    // to the native GIF player.
    private static PreRenderedGif logoAnim() {
        if (!LOGO_ANIM_TRIED) {
            synchronized (AboutDialog.class) {
                if (!LOGO_ANIM_TRIED) {
                    try {
                        LOGO_ANIM_CACHE = PreRenderedGif.decode(AboutDialog.class.getResource("/images/corona_logo.gif"));
                    } catch (Throwable ex) {
                        LOGO_ANIM_CACHE = null;
                        Logger.getLogger(AboutDialog.class.getName()).log(Level.WARNING, "corona_logo.gif pre-render failed, falling back to native GIF", ex);
                    }
                    LOGO_ANIM_TRIED = true;
                }
            }
        }
        return LOGO_ANIM_CACHE;
    }

    /**
     * Pre-decodes corona_logo.gif in the background at startup (alongside the other warmups in
     * {@code Init}), so the first About dialog opened doesn't pay the ~120 ms decode cost on the
     * constructor's EDT. {@link #logoAnim()} is idempotent and thread-safe, so opening the dialog
     * later reuses the already-decoded result (and decodes on the spot if the warmup hasn't
     * finished yet).
     */
    public static void warmupLogoAnim() {
        Helpers.threadRun(() -> logoAnim());
    }

    // Plays the animated logo WITHOUT AWT's GIF player: an Icon that paints the current frame
    // (scaled to the bounds by the GPU) and a Timer that advances the frame by elapsed time, in
    // a loop. Avoids the GIF flicker (see the field comments). The Timer is started in
    // formWindowOpened and stopped in formWindowClosed. Falls back to the native path
    // (scaleDialogIcon) if pre-rendering isn't available.
    private void setupLogoAnimation() {
        PreRenderedGif anim = logoAnim();
        if (anim == null) {
            Helpers.scaleDialogIcon(corona_icon_label, "/images/corona_logo.gif");
            return;
        }
        logo_anim = anim;
        final int dw = Math.round(anim.getWidth() * Helpers.DIALOG_ZOOM);
        final int dh = Math.round(anim.getHeight() * Helpers.DIALOG_ZOOM);
        corona_icon_label.setIcon(new javax.swing.Icon() {
            @Override
            public int getIconWidth() {
                return dw;
            }

            @Override
            public int getIconHeight() {
                return dh;
            }

            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.image.BufferedImage f = logo_anim.getFrame(logo_frame_idx);
                if (f == null) {
                    return;
                }
                if (dw != logo_anim.getWidth() || dh != logo_anim.getHeight()) {
                    ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                g.drawImage(f, x, y, dw, dh, c);
            }
        });
        final long total_ms = Math.max(1L, anim.getTotalMs());
        // Same fine-grained tick as the table animations: sampling the frame by elapsed time
        // every ~2 ms keeps it smooth; coarser ticks caused stutter.
        logo_timer = new javax.swing.Timer(GameFrame.getTickMs(), (ActionEvent ae) -> {
            long now = System.nanoTime();
            if (!logo_clock_started) {
                // Anchor the clock on the FIRST REAL tick, not in formWindowOpened. At that
                // point the EDT is still busy (first dialog layout/paint, native peer/D3D
                // surface creation, audio startup): anchoring earlier let the catch-up model
                // (elapsed-time frameAt) compute a large elapsed value on the first tick and
                // skip several frames at once, causing a stutter at startup. Anchoring here
                // always starts the spin smoothly from frame 0.
                logo_clock_started = true;
                logo_t0 = now;
            }
            int idx = logo_anim.frameAt(((now - logo_t0) / 1_000_000L) % total_ms);
            if (idx != logo_frame_idx) {
                logo_frame_idx = idx;
                corona_icon_label.repaint();
            }
        });
        logo_timer.setCoalesce(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        main_scroll_panel = new javax.swing.JScrollPane();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        dedicado = new javax.swing.JLabel();
        jvm = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        merecemos = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        mod_label = new javax.swing.JLabel();
        corona_icon_label = new javax.swing.JLabel();
        mod_bar = new javax.swing.JProgressBar();
        jPanel4 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        memory_usage = new javax.swing.JLabel();
        threads = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("¿De dónde ha salido esto?");
        setBackground(new java.awt.Color(255, 255, 255));
        setModal(true);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        main_scroll_panel.setBorder(null);
        main_scroll_panel.setDoubleBuffered(true);

        jPanel2.setBackground(new java.awt.Color(255, 255, 255));

        jLabel2.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("Gracias a todos los amigos que han colaborado en esta aventura, en especial a Pepsi por sus barajas y el \"hilo fino\",");
        jLabel2.setDoubleBuffered(true);
        jLabel2.putClientProperty("i18n.key", "about.gracias_1");

        dedicado.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        dedicado.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        dedicado.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/luto.png"))); // NOI18N
        dedicado.setText("En memoria de todas las víctimas de la COVID-19");
        dedicado.setDoubleBuffered(true);
        dedicado.putClientProperty("i18n.key", "about.dedicado");

        jvm.setText(Helpers.getSystemInfo());
        jvm.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jvm.setDoubleBuffered(true);
        jvm.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jvmMouseClicked(evt);
            }
        });

        jLabel3.setText("Jn 8:32");
        jLabel3.setDoubleBuffered(true);

        jLabel4.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("(Todos los céntimos desaparecidos en las betas fueron para una buena causa).");
        jLabel4.setDoubleBuffered(true);
        jLabel4.putClientProperty("i18n.key", "about.centimos");

        merecemos.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        merecemos.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        merecemos.setText("El videojuego de Texas hold 'em NL que nos merecemos, no el que necesitamos ¿o era al revés?");
        merecemos.setDoubleBuffered(true);
        merecemos.putClientProperty("i18n.key", "about.merecemos");

        jLabel6.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel6.setText("Nota: si posees el copyright de esta música (o cualquier otro elemento) y no permites su utilización, escríbeme a -> tonikelope@gmail.com");
        jLabel6.setDoubleBuffered(true);
        jLabel6.putClientProperty("i18n.key", "about.copyright");

        jLabel5.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel5.setText("a Pepillo por ese talento para cazar los bugs más raros, a Lato por las pruebas en su Mac y a mi madre... por todo lo demás.");
        jLabel5.setDoubleBuffered(true);
        jLabel5.putClientProperty("i18n.key", "about.gracias_2");

        jLabel7.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel7.setText("El hilo musical que suena durante el juego fue compuesto por David Luong.");
        jLabel7.setDoubleBuffered(true);
        jLabel7.putClientProperty("i18n.key", "about.musica_juego");

        jLabel8.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel8.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel8.setText("La canción que suena en el visor de estadísticas es el tema principal de la mítica película EL GOLPE.");
        jLabel8.setDoubleBuffered(true);
        jLabel8.putClientProperty("i18n.key", "about.musica_stats");

        jLabel10.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("La canción que suena aquí es \"La Sala del Trono\" compuesta por John Williams para Star Wars.");
        jLabel10.setDoubleBuffered(true);
        jLabel10.putClientProperty("i18n.key", "about.musica_about");

        jLabel11.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("La canción que suena en la sala de espera es \"The Dream\" compuesta por Jerry Goldsmith para la película Total Recall.");
        jLabel11.setDoubleBuffered(true);
        jLabel11.putClientProperty("i18n.key", "about.musica_espera");

        jPanel1.setOpaque(false);

        jPanel3.setOpaque(false);

        mod_label.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        mod_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        mod_label.setText("MOD");
        mod_label.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        mod_label.setDoubleBuffered(true);
        mod_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                mod_labelMouseClicked(evt);
            }
        });

        corona_icon_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        corona_icon_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_logo.gif"))); // NOI18N
        corona_icon_label.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        corona_icon_label.setDoubleBuffered(true);
        corona_icon_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                corona_icon_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(mod_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(mod_bar, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE))
                .addContainerGap())
            .addComponent(corona_icon_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(corona_icon_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 42, Short.MAX_VALUE)
                .addComponent(mod_bar, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(mod_label)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel4.setOpaque(false);

        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/open-book.png"))); // NOI18N
        jLabel12.setToolTipText("Reglas de Robert");
        jLabel12.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jLabel12.setDoubleBuffered(true);
        jLabel12.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jLabel12MouseClicked(evt);
            }
        });
        jLabel12.putClientProperty("i18n.tooltip_key", "tooltip.robert_rules");

        jLabel9.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/cruz.png"))); // NOI18N
        jLabel9.setText("Hecho a mano en España y con amor por tonikelope (c) 2020");
        jLabel9.setToolTipText("PLVS VLTRA");
        jLabel9.putClientProperty("i18n.key", "about.hecho_a_mano");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addGap(0, 0, 0))
        );

        memory_usage.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        memory_usage.setDoubleBuffered(true);

        threads.setDoubleBuffered(true);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(dedicado, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(threads)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(memory_usage)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jvm))
            .addComponent(merecemos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel11, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 804, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(merecemos)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(dedicado)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6)
                .addGap(18, 18, 18)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jvm)
                        .addComponent(memory_usage)
                        .addComponent(threads))
                    .addComponent(jLabel3)))
        );

        main_scroll_panel.setViewportView(jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(main_scroll_panel)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(main_scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 682, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jLabel12MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel12MouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        Helpers.openBrowserURL("https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf");
    }//GEN-LAST:event_jLabel12MouseClicked

    private void jvmMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jvmMouseClicked
        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }
        if (Init.M1 != null && ++c == 5) {

            try {
                Init.M1.invoke(null, this, SwingUtilities.isLeftMouseButton(evt) ? "c" : "g");
            } catch (Exception ex) {
                Logger.getLogger(AboutDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            c = 0;
        }
    }//GEN-LAST:event_jvmMouseClicked

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        memory_timer.start();

        // Starts the logo playback from frame 0. The clock is NOT anchored here: it's anchored
        // on the timer's first real tick (see setupLogoAnimation), because at this point the EDT
        // is still busy opening the dialog and anchoring now made the spin stutter at startup.
        if (logo_timer != null) {
            logo_frame_idx = 0;
            logo_clock_started = false;
            corona_icon_label.repaint();
            logo_timer.start();
        }

        last_mp3_loop = Audio.getCurrentLoopMp3Playing();

        if (GameFrame.SONIDOS && last_mp3_loop != null && !Audio.MP3_LOOP_MUTED.contains(last_mp3_loop)) {
            Audio.muteLoopMp3(last_mp3_loop);
        } else {
            last_mp3_loop = null;
        }

        Audio.playLoopMp3Resource("misc/about_music.mp3");

    }//GEN-LAST:event_formWindowOpened

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        Audio.stopLoopMp3("misc/about_music.mp3");

        if (last_mp3_loop != null) {
            Audio.unmuteLoopMp3(last_mp3_loop);
        }

        memory_timer.stop();

        if (logo_timer != null) {
            logo_timer.stop();
        }
    }//GEN-LAST:event_formWindowClosed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (!mod_bar.isVisible()) {
            dispose();
        }
    }//GEN-LAST:event_formWindowClosing

    private void corona_icon_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_corona_icon_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        Helpers.openBrowserURL("https://github.com/tonikelope/coronapoker");
    }//GEN-LAST:event_corona_icon_labelMouseClicked

    private void mod_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mod_labelMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        if (!mod_bar.isVisible()) {
            mod_bar.setIndeterminate(true);
            mod_bar.setVisible(true);
            pack();

            Helpers.threadRun(() -> {
                Helpers.checkMODVersion(getContentPane());
                Helpers.GUIRun(() -> {
                    mod_bar.setVisible(false);
                    pack();
                });
            });
        }
    }//GEN-LAST:event_mod_labelMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel corona_icon_label;
    private javax.swing.JLabel dedicado;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JLabel jvm;
    private javax.swing.JScrollPane main_scroll_panel;
    private javax.swing.JLabel memory_usage;
    private javax.swing.JLabel merecemos;
    private javax.swing.JProgressBar mod_bar;
    private javax.swing.JLabel mod_label;
    private javax.swing.JLabel threads;
    // End of variables declaration//GEN-END:variables
}

/*

"If you are out to describe the truth, leave elegance to the tailor".

Albert Einstein

 */
