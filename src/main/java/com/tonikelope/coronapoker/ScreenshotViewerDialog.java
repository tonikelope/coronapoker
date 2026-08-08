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
 * Screenshot gallery viewer (CTRL+P) for files saved under {@link Init#SCREENSHOTS_DIR}. Opens
 * on the most recent screenshot; navigate back/forward in time with the ◀ / ▶ buttons (or ← / →).
 * The image is scaled to fit the dialog, preserving aspect ratio and capped at 100% of its
 * native size (never enlarged). The title shows the file's OS creation date/time.
 *
 * @author tonikelope
 */
public class ScreenshotViewerDialog extends javax.swing.JDialog {

    // Single-instance window: reopening from the same owner reuses and refreshes the instance.
    private static volatile ScreenshotViewerDialog INSTANCE = null;

    private static final Color BACKDROP = new Color(24, 24, 24);

    // A screenshot plus its creation date (from the OS), resolved when the list is reloaded.
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
    // Copy item of the context menu: disabled while the clipboard dump is in progress (avoids
    // firing concurrent copies) and re-enabled when it finishes.
    private final JMenuItem copy_menu_item = new JMenuItem();
    // Fixed, EQUAL-width side columns: they always reserve their space, even when their arrow is
    // hidden at one end, so the central area stays symmetric and the image stays centered in the
    // dialog (if the arrow sat directly in WEST/EAST, hiding it would let CENTER take that side
    // and the image would go off-center).
    private final JPanel prev_slot = new JPanel(new java.awt.GridBagLayout());
    private final JPanel next_slot = new JPanel(new java.awt.GridBagLayout());

    private java.util.List<Shot> shots = new ArrayList<>();
    private int index = 0;

    // The currently painted image (freed on change) and a token that discards stale loads when
    // navigating fast (a decode in flight can finish after the user already moved on).
    private BufferedImage current_image = null;
    private volatile long load_token = 0;

    /**
     * Opens the viewer (or brings it to front and refreshes it if already open for the same
     * owner). Must be called on the EDT.
     *
     * @param owner window that owns the dialog
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

        super(owner); // JDialog(Window) => not modal: does not block the game.

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        Helpers.setTranslatedTitle(this, "menu.visor_capturas");

        try {
            setIconImage(new ImageIcon(getClass().getResource("/images/menu/camera.png")).getImage());
        } catch (Exception ex) {
            Logger.getLogger(ScreenshotViewerDialog.class.getName()).log(Level.WARNING, null, ex);
        }

        buildUI();

        // Initial size: comfortable but not excessive (90% of the owner's screen), with a sane
        // minimum. Resizable: the image rescales itself on repaint.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new Dimension(640, 480));
        setSize(Math.round(screen.width * 0.9f), Math.round(screen.height * 0.9f));

        Helpers.zoomFonts(this, Helpers.DIALOG_ZOOM, null);

        // Pin BOTH side columns to the same width (the arrow's, already scaled by zoomFonts) so
        // the central area is symmetric: the image stays centered whether or not each arrow shows.
        int col_w = Math.max(prev_button.getPreferredSize().width, next_button.getPreferredSize().width);
        Dimension col_dim = new Dimension(col_w, 1); // BorderLayout WEST/EAST uses the width; height is stretched
        prev_slot.setPreferredSize(col_dim);
        prev_slot.setMinimumSize(col_dim);
        next_slot.setPreferredSize(col_dim);
        next_slot.setMinimumSize(col_dim);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                // Release the whole chain that would otherwise keep the last screenshot alive:
                // load_token++ discards any decode in flight (which would else repopulate
                // current_image); setCurrentImage(null) flushes and nulls both current_image and
                // image_view.img (and stops the toast timer); INSTANCE=null lets the disposed
                // dialog be collected instead of being kept alive by the static field.
                load_token++;
                setCurrentImage(null);
                if (INSTANCE == ScreenshotViewerDialog.this) {
                    INSTANCE = null;
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
        // Game font (GUI_FONT), large; zoomFonts rescales it later by DIALOG_ZOOM.
        java.awt.Font base_font = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : title_label.getFont());
        title_label.setFont(base_font.deriveFont(java.awt.Font.BOLD, 26f));
        content.add(title_label, BorderLayout.NORTH);

        image_view.setBackground(BACKDROP);
        content.add(image_view, BorderLayout.CENTER);

        // Right-click context menu on the image: copy the screenshot to the clipboard.
        final JPopupMenu image_popup = new JPopupMenu();
        copy_menu_item.setText(Translator.translate("ui.copiar_imagen_portapapeles"));
        java.awt.Font item_font = (Helpers.GUI_FONT != null ? Helpers.GUI_FONT : copy_menu_item.getFont());
        copy_menu_item.setFont(item_font.deriveFont(java.awt.Font.PLAIN, 16f * Helpers.DIALOG_ZOOM));
        try {
            copy_menu_item.setIcon(new ImageIcon(getClass().getResource("/images/menu/copy.png")));
        } catch (Exception ex) {
            // No icon if the resource is unavailable: the text label is enough.
        }
        copy_menu_item.addActionListener(e -> copyCurrentImageToClipboard());
        image_popup.add(copy_menu_item);

        // Delete the visible screenshot (with confirmation). Icon = red X drawn at 24px, matching
        // the size of the copy item's copy.png so both menu icons look consistent.
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

            // isPopupTrigger is platform-dependent (Windows=released, X11/macOS=pressed): checked
            // on both. No image visible means nothing to copy, so the menu is not shown.
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
        prev_slot.add(prev_button); // GridBagLayout with no constraints centers the arrow in its column
        next_slot.add(next_button);
        content.add(prev_slot, BorderLayout.WEST);
        content.add(next_slot, BorderLayout.EAST);

        setContentPane(content);

        // Navigation keys (work whenever the window is focused, regardless of which component
        // has focus) plus ESC to close.
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

    // Rereads the screenshots directory (newest first) and shows target_index, clamped to the
    // available range; if none remain, falls back to the "no screenshots" state.
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
            load_token++; // invalidates any load in flight
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

    // File creation date as reported by the OS, falling back to last-modified when the
    // filesystem does not report a creation time.
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

        // Arrows DISAPPEAR at the ends (not just disabled): BorderLayout does not reserve space
        // for an invisible component, so the image takes over that side.
        prev_button.setVisible(index > 0);
        next_button.setVisible(index < shots.size() - 1);
        getContentPane().revalidate();
        getContentPane().repaint();

        // Decode OFF the EDT (a 4K screenshot is tens of MB); the token discards the result if
        // the user has already navigated elsewhere.
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
                        loaded.flush(); // stale load: discarded
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

    // Copies the currently visible screenshot to the system clipboard. Confirms with a toast
    // centered on the image, or shows an error dialog if the clipboard is unavailable.
    //
    // The toast paints instantly (on the EDT) and the copy happens OFF the EDT: dumping a large
    // image to the OS clipboard (DIB conversion) is blocking and would freeze the window. The
    // background thread gets a stable reference to the image, immune to navigating elsewhere.
    private void copyCurrentImageToClipboard() {
        if (current_image == null) {
            return;
        }
        final BufferedImage img = current_image;
        copy_menu_item.setEnabled(false); // stays consistent + avoids firing concurrent copies
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

    // Deletes the visible screenshot from disk (after confirmation) and REFRESHES the viewer,
    // staying at the same position: the next screenshot fills the gap (or the previous one if the
    // last was deleted). If none remain, falls back to the "no screenshots" state.
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

    // Paints an image scaled to fit its area, preserving aspect ratio, centered, with background-
    // colored bands, WITHOUT exceeding 100% of its native size. Rescales itself on every repaint
    // (resizing the dialog triggers paintComponent).
    private static final class ScaledImageView extends JComponent {

        // How long the confirmation toast stays visible (ms) before fading out.
        private static final int TOAST_MILLIS = 1500;

        private BufferedImage img = null;

        // Confirmation toast overlay (e.g. "Image copied"): current text (null = hidden) and a
        // one-shot timer that clears it. Everything is touched on the EDT (setImage/showToast/paint).
        private String toast_text = null;
        private javax.swing.Timer toast_timer = null;

        void setImage(BufferedImage image) {
            this.img = image;
            hideToast(); // the previous screenshot's toast must not carry over
            repaint();
        }

        // Shows a message centered on the image (black background, yellow text) that disappears
        // after TOAST_MILLIS. Successive calls reset the timer without overlapping.
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
                    // 100% cap: never enlarged past the native size.
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

        // Rounded label centered on the component: black box + yellow text in GUI_FONT, scaled
        // by the dialog zoom.
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

                // Semi-transparent black box: lets the screenshot show through underneath.
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
