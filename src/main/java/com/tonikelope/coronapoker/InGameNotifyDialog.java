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

import static com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL;
import static com.tonikelope.coronapoker.GameFrame.ZOOM_STEP;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.Timer;

/**
 * Borderless, translucent toast used for short-lived in-game notifications
 * (zoom level, screenshots, chat alerts, etc.), optionally with a countdown bar
 * synced to the timeout. Showing a new instance replaces whichever one is
 * currently visible.
 *
 * @author tonikelope
 */
public class InGameNotifyDialog extends JDialog {

    public static final int NOTIFICATION_TIMEOUT = 5000;
    // Screenshot confirmation (Ctrl+P) is just a quick heads-up, so it stays up much shorter
    // than the generic notification to avoid covering the table longer than needed.
    public static final int SCREENSHOT_NOTIFICATION_TIMEOUT = 2000;
    // Zoom changes fire in quick bursts, so this one is even shorter-lived.
    public static final int ZOOM_NOTIFICATION_TIMEOUT = 1500;
    private static final int COUNTDOWN_TICK_MS = 50;
    // Padding around the text and icon-to-text gap, sized relative to the font (already
    // scaled by the table zoom). Sides need more room than top/bottom, where the rounded
    // corner cuts in.
    private static final float PAD_X_RATIO = 0.7f;
    private static final float PAD_Y_RATIO = 0.25f;
    private static final float ICON_GAP_RATIO = 0.35f;
    // Extra bottom padding when the notification has a countdown bar, so the bar (painted
    // inside the box) doesn't touch the text.
    private static final float COUNTDOWN_PAD_RATIO = 0.5f;
    public static volatile InGameNotifyDialog LATEST_NOTIFICATION = null;
    public static final Object LATEST_LOCK = new Object();
    private volatile Timer timer = null;

    /**
     * Same as
     * {@link #InGameNotifyDialog(java.awt.Frame, boolean, String, Color, Color, URL, Integer, boolean)}
     * without a countdown bar.
     */
    public InGameNotifyDialog(java.awt.Frame parent, boolean modal, String message, Color bg, Color fg, URL icon_path, Integer timeout) {
        this(parent, modal, message, bg, fg, icon_path, timeout, false);
    }

    /**
     * @param parent parent frame
     * @param modal whether the dialog blocks input
     * @param message notification text
     * @param bg background color
     * @param fg text color
     * @param icon_path optional icon resource, or {@code null} for none
     * @param timeout auto-dismiss delay in milliseconds, or {@code null} to
     * stay open indefinitely
     * @param withCountdownBar whether to paint a shrinking bar synced to
     * {@code timeout}
     */
    public InGameNotifyDialog(java.awt.Frame parent, boolean modal, String message, Color bg, Color fg, URL icon_path, Integer timeout, boolean withCountdownBar) {
        super(parent, modal);

        initComponents();

        setOpacity(0.8f);

        panel.getMsg().setText(message);

        panel.getMsg().setForeground(fg);

        panel.setBackground(bg);

        Helpers.updateFonts(this, Helpers.GUI_FONT, (1f + ZOOM_LEVEL * ZOOM_STEP));

        Helpers.translateComponents(this, false);

        applyStyle(withCountdownBar);

        pack();

        if (icon_path != null) {
            Helpers.setScaledIconLabel(panel.getMsg(), icon_path, panel.getMsg().getHeight(), panel.getMsg().getHeight());
            pack();
        }

        if (timeout != null && withCountdownBar) {
            // Countdown bar starts at 100% and ticks down to 0 in sync with the timeout.
            // The panel paints it itself inside its rounded box (a JProgressBar hung
            // below it broke the silhouette).
            panel.setCountdown(1f);

            final long deadline = System.currentTimeMillis() + timeout;
            final int totalMs = timeout;
            timer = new Timer(COUNTDOWN_TICK_MS, (ActionEvent ae) -> {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    panel.setCountdown(0f);
                    timer.stop();
                    dispose();
                } else {
                    panel.setCountdown(Math.min(remaining, totalMs) / (float) totalMs);
                }
            });
            timer.setRepeats(true);
            timer.setCoalesce(true);
        } else if (timeout != null) {
            timer = new Timer(timeout, (ActionEvent ae) -> {
                timer.stop();

                dispose();
            });

            timer.setRepeats(false);
            timer.setCoalesce(false);
        }

    }

    /**
     * Announces the current table zoom level through the same notification
     * channel as other in-game toasts, replacing whichever one is currently
     * showing. Thread-safe; a no-op outside a game.
     */
    public static void notifyZoom() {

        Helpers.GUIRun(() -> {

            GameFrame gf = GameFrame.getInstance();

            if (gf == null || !gf.isShowing()) {
                return;
            }

            // Read inside the EDT: zoom changes are applied on their own thread, so this
            // reports the level in effect now rather than whatever it was when the
            // triggering action started.
            InGameNotifyDialog dialog = new InGameNotifyDialog(gf, false,
                    "ZOOM: " + Math.round((1f + ZOOM_LEVEL * ZOOM_STEP) * 100f) + "%",
                    Color.BLACK, Color.WHITE, InGameNotifyDialog.class.getResource("/images/zoom_notify.png"),
                    ZOOM_NOTIFICATION_TIMEOUT);

            dialog.setLocation(gf.getLocation());

            dialog.setVisible(true);
        });
    }

    // Style shared by all notifications: rounded box (needs per-pixel window translucency,
    // when the platform provides it), padding around the text, and icon-to-text gap, all
    // scaled to the already-zoomed font. Color is set by each caller; this only shapes it.
    private void applyStyle(boolean withCountdownBar) {

        final boolean rounded = applyTranslucentWindow();

        panel.setRounded(rounded);

        final float font_size = panel.getMsg().getFont().getSize2D();
        final int pad_x = Math.round(font_size * PAD_X_RATIO);
        final int pad_y = Math.round(font_size * PAD_Y_RATIO);
        final int bottom = withCountdownBar ? pad_y + Math.round(font_size * COUNTDOWN_PAD_RATIO) : pad_y;

        panel.setBorder(BorderFactory.createEmptyBorder(pad_y, pad_x, bottom, pad_x));
        panel.getMsg().setIconTextGap(Math.round(font_size * ICON_GAP_RATIO));
    }

    // Makes the window background transparent so the panel's rounded corners show through.
    // Returns false where the platform doesn't support per-pixel translucency: the
    // notification then stays rectangular instead of showing four desktop-colored corners.
    private boolean applyTranslucentWindow() {

        try {
            if (!GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                return false;
            }

            setBackground(new Color(0, 0, 0, 0));

            if (getContentPane() instanceof JComponent) {
                ((JComponent) getContentPane()).setOpaque(false);
            }

            return true;

        } catch (Exception ex) {
            Logger.getLogger(InGameNotifyDialog.class.getName()).log(Level.WARNING, "Per-pixel translucency not available for notifications", ex);
            return false;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel = new com.tonikelope.coronapoker.InGameNotifyPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setAutoRequestFocus(false);
        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setFocusCycleRoot(false);
        setFocusable(false);
        setFocusableWindowState(false);
        setUndecorated(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        panel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:
        if (timer != null) {
            timer.start();
        }
    }//GEN-LAST:event_formWindowOpened

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        if (timer != null) {
            timer.stop();
        }
    }//GEN-LAST:event_formWindowClosing

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        // TODO add your handling code here:
        dispose();
    }//GEN-LAST:event_formMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        synchronized (LATEST_LOCK) {

            if (LATEST_NOTIFICATION != null) {
                LATEST_NOTIFICATION.setVisible(false);
            }

            LATEST_NOTIFICATION = this;
        }
    }//GEN-LAST:event_formComponentShown

    /**
     * Clears the static {@link #LATEST_NOTIFICATION} slot if it points to this
     * dialog before disposing. Without this the global slot keeps the whole
     * object graph alive (panel, icons, parent GameFrame) past dispose, and the
     * next game inherits references from the previous one — a leak reported in
     * long TTS sessions (v2 report, 🟠-22).
     */
    @Override
    public void dispose() {
        synchronized (LATEST_LOCK) {
            if (LATEST_NOTIFICATION == this) {
                LATEST_NOTIFICATION = null;
            }
        }
        if (timer != null) {
            timer.stop();
        }
        super.dispose();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.tonikelope.coronapoker.InGameNotifyPanel panel;
    // End of variables declaration//GEN-END:variables
}
