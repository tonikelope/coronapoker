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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

/**
 * Visor de galería de las capturas de pantalla (CTRL+P) guardadas en
 * {@link Init#SCREENSHOTS_DIR}. Arranca mostrando la más reciente y permite
 * navegar hacia atrás/adelante en el tiempo con las flechas ◀ / ▶ (o ← / →).
 * La imagen se reescala a caber en el diálogo conservando proporción y con tope
 * al 100% de su tamaño nativo (nunca se amplía). El título muestra la fecha y
 * hora de CREACIÓN del fichero según el sistema de ficheros.
 *
 * @author tonikelope
 */
public class ScreenshotViewerDialog extends javax.swing.JDialog {

    // Ventana única: reabrir desde el mismo owner reutiliza la instancia y la refresca.
    private static volatile ScreenshotViewerDialog INSTANCE = null;

    private static final Color BACKDROP = new Color(24, 24, 24);

    // Una captura + su fecha de creación (SO), resueltas al recargar la lista.
    private static final class Shot {

        final File file;
        final long created;

        Shot(File file, long created) {
            this.file = file;
            this.created = created;
        }
    }

    private final ScaledImageView image_view = new ScaledImageView();
    private final JLabel title_label = new JLabel("", SwingConstants.CENTER);
    private final JButton prev_button = arrowButton("◀");
    private final JButton next_button = arrowButton("▶");

    private java.util.List<Shot> shots = new ArrayList<>();
    private int index = 0;

    // La imagen actualmente pintada (para liberarla al cambiar) y un testigo que
    // descarta cargas obsoletas cuando se navega rápido (una decodificación en
    // curso puede terminar después de que el usuario ya haya cambiado de imagen).
    private BufferedImage current_image = null;
    private volatile long load_token = 0;

    /**
     * Abre el visor (o lo trae al frente y lo refresca si ya está abierto para
     * ese mismo owner). Debe llamarse en el EDT.
     */
    public static void open(Window owner) {

        if (INSTANCE != null && INSTANCE.isDisplayable() && INSTANCE.getOwner() == owner) {
            INSTANCE.reload();
            INSTANCE.setVisible(true);
            INSTANCE.toFront();
            INSTANCE.requestFocus();
            return;
        }

        if (INSTANCE != null) {
            INSTANCE.dispose();
        }

        INSTANCE = new ScreenshotViewerDialog(owner);
        INSTANCE.setLocationRelativeTo(owner);
        INSTANCE.setVisible(true);
        INSTANCE.requestFocus();
    }

    private ScreenshotViewerDialog(Window owner) {

        super(owner); // JDialog(Window) => NO modal: no bloquea la partida.

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        Helpers.setTranslatedTitle(this, "menu.visor_capturas");

        try {
            setIconImage(new ImageIcon(getClass().getResource("/images/menu/camera.png")).getImage());
        } catch (Exception ex) {
            Logger.getLogger(ScreenshotViewerDialog.class.getName()).log(Level.WARNING, null, ex);
        }

        buildUI();

        // Tamaño inicial: cómodo pero sin pasarse (85% de la pantalla del owner),
        // con un mínimo razonable. Redimensionable: la imagen se reajusta sola.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new Dimension(640, 480));
        setSize(Math.round(screen.width * 0.9f), Math.round(screen.height * 0.9f));

        Helpers.zoomFonts(this, Helpers.DIALOG_ZOOM, null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (current_image != null) {
                    current_image.flush();
                    current_image = null;
                }
            }
        });

        reload();
    }

    private void buildUI() {

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKDROP);

        title_label.setForeground(Color.WHITE);
        title_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16));
        // Fuente del juego (GUI_FONT) y grande; zoomFonts la reescala luego por DIALOG_ZOOM.
        java.awt.Font base_font = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : title_label.getFont());
        title_label.setFont(base_font.deriveFont(java.awt.Font.BOLD, 26f));
        content.add(title_label, BorderLayout.NORTH);

        image_view.setBackground(BACKDROP);
        content.add(image_view, BorderLayout.CENTER);

        prev_button.addActionListener(e -> showRelative(-1));
        next_button.addActionListener(e -> showRelative(1));
        content.add(prev_button, BorderLayout.WEST);
        content.add(next_button, BorderLayout.EAST);

        setContentPane(content);

        // Teclas de navegación (funcionan con la ventana enfocada,
        // independientemente del componente que tenga el foco) + ESC para cerrar.
        javax.swing.JRootPane root = getRootPane();
        javax.swing.InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = root.getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "prev");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "next");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        am.put("prev", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showRelative(-1);
            }
        });
        am.put("next", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showRelative(1);
            }
        });
        am.put("close", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
    }

    // Relee el directorio de capturas y ordena de la más nueva a la más antigua.
    private void reload() {

        shots = new ArrayList<>();

        File dir = new File(Init.SCREENSHOTS_DIR);
        File[] arr = dir.listFiles((File d, String name) -> {
            String lower = name.toLowerCase();
            return lower.startsWith("coronapoker_screenshot_") && lower.endsWith(".png");
        });

        if (arr != null) {
            for (File f : arr) {
                shots.add(new Shot(f, creationMillis(f)));
            }
        }

        shots.sort((Shot a, Shot b) -> Long.compare(b.created, a.created));

        index = 0;

        if (shots.isEmpty()) {
            load_token++; // invalida cualquier carga en vuelo
            title_label.setText(Translator.translate("ui.no_capturas"));
            setCurrentImage(null);
            prev_button.setVisible(false);
            next_button.setVisible(false);
            getContentPane().revalidate();
            getContentPane().repaint();
        } else {
            showIndex(0);
        }
    }

    // Fecha de creación del fichero según el SO. Con caída a la de última
    // modificación si el sistema de ficheros no soporta creationTime.
    private static long creationMillis(File f) {
        try {
            BasicFileAttributes attr = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            long created = attr.creationTime().toMillis();
            return created > 0 ? created : attr.lastModifiedTime().toMillis();
        } catch (Exception ex) {
            return f.lastModified();
        }
    }

    private void showRelative(int delta) {
        showIndex(index + delta);
    }

    private void showIndex(int i) {

        if (shots.isEmpty() || i < 0 || i >= shots.size()) {
            return;
        }

        index = i;

        Shot shot = shots.get(index);

        Locale locale = new Locale(GameFrame.LANGUAGE);
        String when = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM, locale).format(new Date(shot.created));
        title_label.setText(when + "     ( " + (index + 1) + " / " + shots.size() + " )");

        // Las flechas DESAPARECEN en los extremos (no solo se deshabilitan): BorderLayout no
        // reserva espacio para un componente invisible, así la imagen ocupa ese lado.
        prev_button.setVisible(index > 0);
        next_button.setVisible(index < shots.size() - 1);
        getContentPane().revalidate();
        getContentPane().repaint();

        // Decodificación FUERA del EDT (una captura 4K son decenas de MB); el
        // testigo descarta el resultado si el usuario ya ha navegado a otra.
        final long token = ++load_token;
        final File file = shot.file;

        Helpers.threadRun(() -> {

            BufferedImage img = null;

            try {
                img = ImageIO.read(file);
            } catch (Exception ex) {
                Logger.getLogger(ScreenshotViewerDialog.class.getName()).log(Level.WARNING, "Cannot read screenshot " + file, ex);
            }

            final BufferedImage loaded = img;

            Helpers.GUIRun(() -> {
                if (token != load_token) {
                    if (loaded != null) {
                        loaded.flush(); // carga obsoleta: se descarta
                    }
                    return;
                }
                setCurrentImage(loaded);
            });
        });
    }

    private void setCurrentImage(BufferedImage img) {
        if (current_image != null && current_image != img) {
            current_image.flush();
        }
        current_image = img;
        image_view.setImage(img);
    }

    private static JButton arrowButton(String glyph) {
        JButton b = new JButton(glyph);
        b.setFont(b.getFont().deriveFont(java.awt.Font.BOLD, 28f));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        b.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 18, 0, 18));
        return b;
    }

    /**
     * Componente que pinta una imagen escalada para caber en su área conservando
     * proporción, centrada y con bandas del color de fondo, SIN superar el 100%
     * de su tamaño nativo. Se reajusta solo en cada repintado (redimensionar el
     * diálogo dispara paintComponent).
     */
    private static final class ScaledImageView extends JComponent {

        private BufferedImage img = null;

        void setImage(BufferedImage image) {
            this.img = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int cw = getWidth();
            int ch = getHeight();

            g.setColor(getBackground());
            g.fillRect(0, 0, cw, ch);

            if (img == null) {
                return;
            }

            int iw = img.getWidth();
            int ih = img.getHeight();

            if (iw <= 0 || ih <= 0) {
                return;
            }

            // Tope 100%: nunca se amplía por encima del tamaño nativo.
            double scale = Math.min(Math.min(cw / (double) iw, ch / (double) ih), 1.0);

            int dw = (int) Math.round(iw * scale);
            int dh = (int) Math.round(ih * scale);
            int x = (cw - dw) / 2;
            int y = (ch - dh) / 2;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(img, x, y, dw, dh, null);
            } finally {
                g2.dispose();
            }
        }
    }
}
