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
 *
 * @author tonikelope
 */
public class InGameNotifyDialog extends JDialog {

    public static final int NOTIFICATION_TIMEOUT = 5000;
    // Aviso de captura de pantalla (CTRL+P): es solo una confirmación rápida, así que dura
    // bastante menos que el aviso genérico para no tapar la mesa más de lo necesario.
    public static final int SCREENSHOT_NOTIFICATION_TIMEOUT = 2000;
    // El nivel de zoom se anuncia de pasada, y encima se toca a ráfagas: dura aún menos.
    public static final int ZOOM_NOTIFICATION_TIMEOUT = 1500;
    private static final int COUNTDOWN_TICK_MS = 50;
    // Aire alrededor del texto y hueco entre el icono y el texto, en proporción al
    // cuerpo de la letra (que ya viene escalada por el zoom de la mesa). A los lados
    // hace falta más que arriba y abajo: ahí es donde muerde la curva de la esquina.
    private static final float PAD_X_RATIO = 0.7f;
    private static final float PAD_Y_RATIO = 0.25f;
    private static final float ICON_GAP_RATIO = 0.35f;
    // Aire extra bajo el texto cuando la notificación lleva cuenta atrás: la franja
    // se pinta dentro de la caja y no debe rozar las letras.
    private static final float COUNTDOWN_PAD_RATIO = 0.5f;
    public static volatile InGameNotifyDialog LATEST_NOTIFICATION = null;
    public static final Object LATEST_LOCK = new Object();
    private volatile Timer timer = null;

    /**
     * Creates new form ChatNotifyDialog
     */
    public InGameNotifyDialog(java.awt.Frame parent, boolean modal, String message, Color bg, Color fg, URL icon_path, Integer timeout) {
        this(parent, modal, message, bg, fg, icon_path, timeout, false);
    }

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
            // Countdown visual: franja que arranca al 100% y baja hasta 0 sincronizada
            // con el timeout. La pinta el propio panel dentro de su caja redondeada
            // (una JProgressBar colgada debajo rompía la silueta).
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
     * Anuncia el nivel de zoom de la mesa por el mismo canal que el resto de
     * avisos del juego, en la esquina superior izquierda de la ventana. Como
     * toda notificación, releva a la que hubiera puesta. Seguro desde cualquier
     * hilo; fuera de partida no hace nada.
     */
    public static void notifyZoom() {

        Helpers.GUIRun(() -> {

            GameFrame gf = GameFrame.getInstance();

            if (gf == null || !gf.isShowing()) {
                return;
            }

            // El porcentaje se lee ya dentro del EDT: los cambios de zoom se aplican
            // cada uno en su hilo, así el aviso canta el nivel vigente y no el que
            // había cuando arrancó el que lo pidió.
            InGameNotifyDialog dialog = new InGameNotifyDialog(gf, false,
                    "ZOOM: " + Math.round((1f + ZOOM_LEVEL * ZOOM_STEP) * 100f) + "%",
                    Color.BLACK, Color.WHITE, InGameNotifyDialog.class.getResource("/images/zoom_notify.png"),
                    ZOOM_NOTIFICATION_TIMEOUT);

            dialog.setLocation(gf.getLocation());

            dialog.setVisible(true);
        });
    }

    // Estilo común a todas las notificaciones: caja redondeada (que necesita la
    // ventana transparente por píxel, si el sistema la da), aire alrededor del
    // texto y hueco entre el icono y el mensaje, todo a la escala de la letra ya
    // zoomeada. El color lo pone cada aviso; aquí solo se le da forma.
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

    // Hace transparente el fondo de la ventana para que asomen las esquinas
    // redondeadas del panel. Devuelve false donde el sistema no soporta
    // translucidez por píxel: allí la notificación se queda rectangular, como
    // siempre, en vez de enseñar cuatro esquinas del color del escritorio.
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
     * Limpia la static LATEST_NOTIFICATION si apunta a this antes de disponer.
     * Sin esto, el slot global retiene el dialog (y todo su grafo: panel,
     * iconos, parent GameFrame) incluso después de dispose; las siguientes
     * partidas heredan referencias del juego anterior. Leak severo en
     * sesiones largas con TTS reportado en el informe v2 (🟠-22).
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
