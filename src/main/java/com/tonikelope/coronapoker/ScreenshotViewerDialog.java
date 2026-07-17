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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
    // Ítem del menú contextual para copiar: se deshabilita mientras el volcado al portapapeles está
    // en curso (evita disparar copias concurrentes) y se rehabilita al terminar.
    private final JMenuItem copy_menu_item = new JMenuItem();
    // Columnas laterales de ancho FIJO e IGUAL: reservan su sitio SIEMPRE, aunque su flecha se
    // oculte en un extremo, de modo que el área central sea simétrica y la imagen quede SIEMPRE
    // centrada en el diálogo (si la flecha estuviera directa en WEST/EAST, al ocultarse el CENTER
    // ocuparía ese lado y la imagen se descentraría).
    private final JPanel prev_slot = new JPanel(new java.awt.GridBagLayout());
    private final JPanel next_slot = new JPanel(new java.awt.GridBagLayout());

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

        // Fija AMBAS columnas laterales al mismo ancho (el de la flecha ya escalada por zoomFonts)
        // para que el área central sea simétrica: la imagen queda centrada aparezca o no cada flecha.
        int col_w = Math.max(prev_button.getPreferredSize().width, next_button.getPreferredSize().width);
        Dimension col_dim = new Dimension(col_w, 1); // BorderLayout WEST/EAST usa el ancho; estira el alto
        prev_slot.setPreferredSize(col_dim);
        prev_slot.setMinimumSize(col_dim);
        next_slot.setPreferredSize(col_dim);
        next_slot.setMinimumSize(col_dim);

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

        // Menú contextual (clic derecho) sobre la imagen: copiar la captura al portapapeles.
        final JPopupMenu image_popup = new JPopupMenu();
        copy_menu_item.setText(Translator.translate("ui.copiar_imagen_portapapeles"));
        java.awt.Font item_font = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : copy_menu_item.getFont());
        copy_menu_item.setFont(item_font.deriveFont(java.awt.Font.PLAIN, 16f * Helpers.DIALOG_ZOOM));
        try {
            copy_menu_item.setIcon(new ImageIcon(getClass().getResource("/images/menu/copy.png")));
        } catch (Exception ex) {
            // sin icono si el recurso no está disponible: el texto basta
        }
        copy_menu_item.addActionListener(e -> copyCurrentImageToClipboard());
        image_popup.add(copy_menu_item);

        // Borrar la captura visible (con confirmación). Icono = X roja dibujada a 24px, igual que
        // copy.png del ítem de copiar, para que ambos iconos del menú tengan el mismo tamaño.
        JMenuItem delete_menu_item = new JMenuItem(Translator.translate("ui.borrar_captura"));
        delete_menu_item.setFont(item_font.deriveFont(java.awt.Font.PLAIN, 16f * Helpers.DIALOG_ZOOM));
        delete_menu_item.setIcon(Helpers.deleteGlyph(24));
        delete_menu_item.addActionListener(e -> deleteCurrent());
        image_popup.add(delete_menu_item);

        image_view.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }

            // isPopupTrigger es dependiente de plataforma (Windows=released, X11/macOS=pressed):
            // se comprueba en ambos. Sin imagen visible no hay nada que copiar => no se muestra.
            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() && current_image != null) {
                    image_popup.show(image_view, e.getX(), e.getY());
                }
            }
        });

        prev_button.addActionListener(e -> showRelative(-1));
        next_button.addActionListener(e -> showRelative(1));
        prev_slot.setOpaque(false);
        next_slot.setOpaque(false);
        prev_slot.add(prev_button); // GridBagLayout sin constraints => centra la flecha en su columna
        next_slot.add(next_button);
        content.add(prev_slot, BorderLayout.WEST);
        content.add(next_slot, BorderLayout.EAST);

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

    private void reload() {
        reload(0);
    }

    // Relee el directorio de capturas (más nueva primero) y muestra la de target_index, acotado al
    // rango disponible; si no queda ninguna, pasa al estado "sin capturas".
    private void reload(int target_index) {

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

        if (shots.isEmpty()) {
            index = 0;
            load_token++; // invalida cualquier carga en vuelo
            title_label.setText(Translator.translate("ui.no_capturas"));
            setCurrentImage(null);
            prev_button.setVisible(false);
            next_button.setVisible(false);
            getContentPane().revalidate();
            getContentPane().repaint();
        } else {
            showIndex(Math.max(0, Math.min(target_index, shots.size() - 1)));
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

    // Copia la captura actualmente visible al portapapeles del sistema. Confirmación con un toast
    // centrado sobre la imagen, o diálogo de error si el portapapeles no está disponible.
    //
    // El toast se pinta al instante (en el EDT) y la copia se hace FUERA del EDT: volcar una imagen
    // grande al portapapeles del SO (conversión a DIB) es bloqueante y congelaría la ventana. Al
    // hilo de fondo se le pasa una referencia estable a la imagen, inmune a que se navegue a otra.
    private void copyCurrentImageToClipboard() {
        if (current_image == null) {
            return;
        }
        final BufferedImage img = current_image;
        copy_menu_item.setEnabled(false); // congruencia + evita disparar copias concurrentes
        image_view.showToast(Translator.translate("ui.imagen_copiada"));
        Helpers.threadRun(() -> {
            final boolean ok = Helpers.copyImageToClipboard(img);
            Helpers.GUIRun(() -> {
                copy_menu_item.setEnabled(true);
                if (!ok) {
                    image_view.hideToast();
                    Helpers.mostrarMensajeError(this, Translator.translate("ui.copiar_imagen_error"));
                }
            });
        });
    }

    // Borra del disco la captura visible (tras confirmar) y REFRESCA el visor manteniéndose en la
    // misma posición: la siguiente captura ocupa el hueco (o la anterior si se borró la última). Si
    // no queda ninguna, pasa al estado "sin capturas".
    private void deleteCurrent() {
        if (shots.isEmpty() || index < 0 || index >= shots.size()) {
            return;
        }
        File file = shots.get(index).file;
        if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("ui.borrar_captura_confirm")) != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }
        if (!file.delete() && file.exists()) {
            Helpers.mostrarMensajeError(this, Translator.translate("ui.borrar_captura_error"));
            return;
        }
        reload(index);
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

        // Duración visible del toast de confirmación (ms) antes de desvanecerse.
        private static final int TOAST_MILLIS = 1500;

        private BufferedImage img = null;

        // Toast de confirmación superpuesto (p. ej. "Imagen copiada"): texto actual (null = oculto)
        // y timer de un disparo que lo borra. Todo se toca en el EDT (setImage/showToast/paint).
        private String toast_text = null;
        private javax.swing.Timer toast_timer = null;

        void setImage(BufferedImage image) {
            this.img = image;
            hideToast(); // al cambiar de captura el toast de la anterior no debe arrastrarse
            repaint();
        }

        // Muestra un mensaje centrado sobre la imagen (fondo negro, texto amarillo) que desaparece
        // solo tras TOAST_MILLIS. Llamadas sucesivas reinician el reloj sin solaparse.
        void showToast(String text) {
            toast_text = text;
            if (toast_timer != null) {
                toast_timer.stop();
            }
            toast_timer = new javax.swing.Timer(TOAST_MILLIS, e -> {
                toast_text = null;
                repaint();
            });
            toast_timer.setRepeats(false);
            toast_timer.start();
            repaint();
        }

        void hideToast() {
            if (toast_timer != null) {
                toast_timer.stop();
                toast_timer = null;
            }
            toast_text = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int cw = getWidth();
            int ch = getHeight();

            g.setColor(getBackground());
            g.fillRect(0, 0, cw, ch);

            if (img != null) {
                int iw = img.getWidth();
                int ih = img.getHeight();

                if (iw > 0 && ih > 0) {
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

            if (toast_text != null) {
                paintToast((Graphics2D) g, cw, ch);
            }
        }

        // Rótulo redondeado centrado en el componente: caja negra + texto amarillo en GUI_FONT
        // escalada por el zoom de diálogos.
        private void paintToast(Graphics2D g, int cw, int ch) {

            java.awt.Font base = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : getFont());
            java.awt.Font font = base.deriveFont(java.awt.Font.BOLD, 30f * Helpers.DIALOG_ZOOM);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(font);

                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(toast_text);
                int th = fm.getAscent() + fm.getDescent();

                int pad_x = Math.round(30f * Helpers.DIALOG_ZOOM);
                int pad_y = Math.round(18f * Helpers.DIALOG_ZOOM);
                int box_w = tw + pad_x * 2;
                int box_h = th + pad_y * 2;
                int bx = (cw - box_w) / 2;
                int by = (ch - box_h) / 2;
                int arc = Math.round(20f * Helpers.DIALOG_ZOOM);

                // Caja negra semitransparente: deja entrever la captura por debajo.
                g2.setColor(new Color(0, 0, 0, 185));
                g2.fillRoundRect(bx, by, box_w, box_h, arc, arc);

                g2.setColor(Color.YELLOW);
                g2.drawString(toast_text, bx + pad_x, by + pad_y + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
