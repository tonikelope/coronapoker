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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope
 */
public final class TapeteFastButtons extends javax.swing.JPanel implements ZoomableInterface {

    public final static int H = 50;
    private final Object[][] botones;
    private volatile Dimension pref_size;
    private volatile float zoom_factor;

    // Barra plegable con desvanecimiento. Se despliega SOLO al pasar el ratón por el icono "menu"
    // (esquina inferior izquierda, el único siempre visible al plegarse). Al salir el ratón NO se
    // pliega al instante: espera HIDE_DELAY_MS y luego se desvanece durante FADE_MS. Volver a entrar
    // en la barra durante la espera o el desvanecimiento lo cancela y restaura la opacidad plena.
    private static final int HIDE_DELAY_MS = 1000;
    private static final int FADE_MS = 400;
    private static final int FADE_INTERVAL_MS = 16;
    private volatile float bar_opacity = 1f;
    private javax.swing.Timer hide_delay_timer;
    private javax.swing.Timer fade_timer;

    public Dimension getPref_size() {
        return pref_size;
    }

    public boolean areButtonsVisible() {
        return chat.isVisible();
    }

    public JLabel getMenu() {
        return menu;
    }

    /**
     * Creates new form ChatImageTapetePanel
     */
    public TapeteFastButtons() {

        initComponents();
        botones = new Object[][]{{chat, "chat.png", "chat.chat_rapido"}, {mic, "mic.png", "audio.boton_nota_voz"}, {image, "image.png", "chat.enviar_imagen"}, {compact, "compact.png", "view.vista_compacta"}, {zoom_in, "zoom_in.png", "view.aumentar_zoom"}, {zoom_reset, "zoom_reset.png", "view.reset_zoom"}, {zoom_out, "zoom_out.png", "view.reducir_zoom"}, {fullscreen, "fullscreen.png", "view.pantalla_completa"}, {log, "log.png", "log.registro"}, {rebuy, "rebuy.png", "rebuy.recomprar"}};

        for (Object[] b : botones) {
            Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));
            ((JLabel) b[0]).setToolTipText(Translator.translate((String) b[2]));
        }

        Helpers.setScaledIconLabel(menu, getClass().getResource("/images/fast_panel/menu.png"), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));

        pref_size = getPreferredSize();
        // El panel abarca el ancho de la barra desplegada aunque esté plegado; su cursor es normal
        // para que el hueco vacío (a la derecha del icono) NO muestre la manita. El icono "menu" y
        // los botones conservan su propio cursor de mano.
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        hideButtons();
        setComListeners();
    }

    private void zoomIcons(float factor) {

        Helpers.GUIRunAndWait(() -> {
            for (Object[] b : botones) {
                Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round(factor * H), Math.round(factor * H));

            }

            zoom_factor = factor;

            // Si la barra está desplegada (p. ej. al pulsar sus propios botones de zoom),
            // reajustamos su tamaño y posición al vuelo para que el nuevo zoom se vea al
            // instante, no solo tras plegarla y volver a mostrarla.
            if (chat.isVisible()) {
                revalidate();
                pref_size = getPreferredSize();
                setSize(pref_size);
                setLocation(0, (int) (GameFrame.getInstance().getTapete().getHeight() - getSize().getHeight()));
                repaint();
            }
        });
    }

    private void setComListeners() {
        initHoverTimers();

        // El icono "menu" (esquina, siempre visible al plegarse) es el ÚNICO disparador para
        // DESPLEGAR la barra.
        menu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (canShow()) {
                    showBar();
                }
            }
        });

        // Los botones (visibles solo con la barra desplegada) la mantienen viva mientras el ratón
        // esté encima; al salir programan el plegado con retardo (1 s + desvanecimiento).
        for (Object[] b : botones) {
            ((Component) b[0]).addMouseListener(new MouseAdapter() {

                @Override
                public void mouseEntered(MouseEvent e) {
                    cancelHide();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    scheduleHide();
                }
            });
        }
    }

    // Crea los timers de plegado (una sola vez). El de RETARDO, al cumplirse el segundo, arranca el
    // desvanecimiento SOLO si el ratón sigue fuera de la barra (recomprobado con MouseInfo, por si
    // volvió durante la espera sin que llegara un mouseEntered). El de FADE baja la opacidad por
    // frames y, al llegar a 0, pliega la barra (icono "menu") y restaura la opacidad para la próxima.
    private void initHoverTimers() {
        hide_delay_timer = new javax.swing.Timer(HIDE_DELAY_MS, e -> {
            hide_delay_timer.stop();
            if (pointerOutsideBar()) {
                startFade();
            }
        });
        hide_delay_timer.setRepeats(false);

        fade_timer = new javax.swing.Timer(FADE_INTERVAL_MS, e -> {
            bar_opacity -= FADE_INTERVAL_MS / (float) FADE_MS;
            if (bar_opacity <= 0f) {
                fade_timer.stop();
                // hideButtons restaura la opacidad a 1 junto con el plegado (mismo bloque EDT), así
                // el icono "menu" reaparece a opacidad plena sin destello.
                hideButtons();
            } else {
                repaint();
            }
        });
    }

    // Despliega la barra desde el icono "menu": cancela cualquier plegado en curso, restaura la
    // opacidad y muestra los botones.
    private void showBar() {
        cancelHide();
        showButtons();
    }

    // Cancela el plegado en curso (retardo o desvanecimiento) y restaura la opacidad plena.
    private void cancelHide() {
        if (hide_delay_timer != null) {
            hide_delay_timer.stop();
        }
        if (fade_timer != null) {
            fade_timer.stop();
        }
        if (bar_opacity != 1f) {
            bar_opacity = 1f;
            repaint();
        }
    }

    // Arranca (o reinicia) el retardo de 1 s tras el que la barra empezará a desvanecerse. No hace
    // nada si ya está plegada o ya se está desvaneciendo. Público: el tapete (TablePanel) lo llama
    // al recibir el ratón (señal fiable de "he salido de la barra") en vez de plegar al instante.
    public void scheduleHide() {
        if (!chat.isVisible() || (fade_timer != null && fade_timer.isRunning())) {
            return;
        }
        // Arranca SOLO si no está ya corriendo: el tapete emite mouseEntered cada vez que el ratón
        // cruza sus subcomponentes, y con restart() el segundo nunca se cumpliría al mover el ratón.
        // Así cuenta 1 s desde que se sale de la barra; volver a entrar lo cancela (cancelHide).
        if (hide_delay_timer != null && !hide_delay_timer.isRunning()) {
            hide_delay_timer.start();
        }
    }

    private void startFade() {
        bar_opacity = 1f;
        if (fade_timer != null) {
            fade_timer.start();
        }
    }

    // El puntero está fuera de la barra (o la barra no está en pantalla). Con MouseInfo (coordenadas
    // de pantalla) para no depender del sistema de coordenadas del evento.
    private boolean pointerOutsideBar() {
        if (!isShowing()) {
            return true;
        }
        java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
        if (pi == null) {
            return true;
        }
        return !new Rectangle(getLocationOnScreen(), getSize()).contains(pi.getLocation());
    }

    // La barra puede desplegarse: la partida sigue viva y no hay un chat rápido abierto encima.
    private boolean canShow() {
        GameFrame gf = GameFrame.getInstance();
        return !gf.getCrupier().isFin_de_la_transmision() && (gf.getFastchat_dialog() == null || !gf.getFastchat_dialog().isVisible());
    }

    // Pinta la barra con la opacidad actual (para el desvanecimiento). A opacidad plena delega
    // directamente; si no, compone todo el árbol (iconos incluidos) con un AlphaComposite.
    @Override
    public void paint(java.awt.Graphics g) {
        if (bar_opacity >= 1f) {
            super.paint(g);
            return;
        }
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, bar_opacity)));
            super.paint(g2);
        } finally {
            g2.dispose();
        }
    }

    public void hideButtons() {
        Helpers.GUIRun(() -> {
            if (hide_delay_timer != null) {
                hide_delay_timer.stop();
            }
            if (fade_timer != null) {
                fade_timer.stop();
            }
            bar_opacity = 1f;
            for (Object[] b : botones) {
                ((Component) b[0]).setVisible(false);
            }
            if (isEnabled()) {
                menu.setVisible(true);
            }
        });
    }

    private void showButtons() {

        if (zoom_factor != (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)) {
            zoomIcons(1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
        }
        Helpers.GUIRun(() -> {
            for (Object[] b : botones) {

                if (((Component) b[0]) == image) {
                    ((Component) b[0]).setVisible(GameFrame.CHAT_IMAGES_INGAME);
                } else if (((Component) b[0]) == mic) {
                    ((Component) b[0]).setVisible(GameFrame.VOICE_MESSAGES);
                } else {
                    ((Component) b[0]).setVisible(true);
                }
            }

            if (getPref_size() != getPreferredSize()) {
                pref_size = getPreferredSize();
                setSize(pref_size);
                setLocation(0, (int) (GameFrame.getInstance().getTapete().getHeight() - getSize().getHeight()));
            }

            menu.setVisible(false);
        });

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        menu = new javax.swing.JLabel();
        chat = new javax.swing.JLabel();
        mic = new javax.swing.JLabel();
        image = new javax.swing.JLabel();
        rebuy = new javax.swing.JLabel();
        log = new javax.swing.JLabel();
        compact = new javax.swing.JLabel();
        zoom_out = new javax.swing.JLabel();
        zoom_reset = new javax.swing.JLabel();
        zoom_in = new javax.swing.JLabel();
        fullscreen = new javax.swing.JLabel();

        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setFocusable(false);
        setOpaque(false);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                formMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                formMouseExited(evt);
            }
        });
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/menu.png"))); // NOI18N
        menu.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        add(menu);

        chat.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/chat.png"))); // NOI18N
        chat.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat.setFocusable(false);
        chat.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                chatMouseClicked(evt);
            }
        });
        add(chat);

        mic.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/mic.png"))); // NOI18N
        mic.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        mic.setFocusable(false);
        mic.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                micMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                micMouseReleased(evt);
            }
        });
        add(mic);

        image.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/image.png"))); // NOI18N
        image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        image.setFocusable(false);
        image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                imageMouseClicked(evt);
            }
        });
        add(image);

        rebuy.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/rebuy.png"))); // NOI18N
        rebuy.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy.setFocusable(false);
        rebuy.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                rebuyMouseClicked(evt);
            }
        });
        add(rebuy);

        log.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/log.png"))); // NOI18N
        log.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        log.setFocusable(false);
        log.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                logMouseClicked(evt);
            }
        });
        add(log);

        compact.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/compact.png"))); // NOI18N
        compact.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        compact.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                compactMouseClicked(evt);
            }
        });
        add(compact);

        zoom_out.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_out.png"))); // NOI18N
        zoom_out.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_out.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_outMouseClicked(evt);
            }
        });
        add(zoom_out);

        zoom_reset.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_reset.png"))); // NOI18N
        zoom_reset.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_reset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_resetMouseClicked(evt);
            }
        });
        add(zoom_reset);

        zoom_in.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_in.png"))); // NOI18N
        zoom_in.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_in.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                zoom_inMouseClicked(evt);
            }
        });
        add(zoom_in);

        fullscreen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/fullscreen.png"))); // NOI18N
        fullscreen.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fullscreen.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                fullscreenMouseClicked(evt);
            }
        });
        add(fullscreen);
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
        // Entrar en el panel (no en el icono "menu") NO despliega la barra: solo mantiene viva la
        // que ya esté desplegada, cancelando un plegado pendiente.
        cancelHide();
    }//GEN-LAST:event_formMouseEntered

    private void formMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseExited
        scheduleHide();
    }//GEN-LAST:event_formMouseExited

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_chatMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatDialog();
        }
    }//GEN-LAST:event_chatMouseClicked

    private void micMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_micMousePressed
        // Hold left button to record a voice note (same as holding the voice key)
        if (javax.swing.SwingUtilities.isLeftMouseButton(evt) && !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            VoiceMessageManager.buttonPressed();
        }
    }//GEN-LAST:event_micMousePressed

    private void micMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_micMouseReleased
        // Release sends (or cancels if the talk-now dialog had not appeared yet)
        if (javax.swing.SwingUtilities.isLeftMouseButton(evt)) {

            VoiceMessageManager.buttonReleased();
        }
    }//GEN-LAST:event_micMouseReleased

    private void imageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_imageMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatImage();
        }
    }//GEN-LAST:event_imageMouseClicked

    private void compactMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_compactMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getCompact_menu().doClick();
        }

    }//GEN-LAST:event_compactMouseClicked

    private void zoom_outMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_outMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_out().doClick();
        }

    }//GEN-LAST:event_zoom_outMouseClicked

    private void zoom_resetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_resetMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_reset().doClick();
        }
    }//GEN-LAST:event_zoom_resetMouseClicked

    private void zoom_inMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_inMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_in().doClick();
        }

    }//GEN-LAST:event_zoom_inMouseClicked

    private void fullscreenMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fullscreenMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getFull_screen_menu().doClick();
        }
    }//GEN-LAST:event_fullscreenMouseClicked

    private void rebuyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_rebuyMouseClicked
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // TODO add your handling code here:

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getRebuy_now_menu().doClick();
        }

    }//GEN-LAST:event_rebuyMouseClicked

    private void logMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getRegistro_menu().doClick();
        }

    }//GEN-LAST:event_logMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel chat;
    private javax.swing.JLabel compact;
    private javax.swing.JLabel fullscreen;
    private javax.swing.JLabel image;
    private javax.swing.JLabel log;
    private javax.swing.JLabel menu;
    private javax.swing.JLabel mic;
    private javax.swing.JLabel rebuy;
    private javax.swing.JLabel zoom_in;
    private javax.swing.JLabel zoom_out;
    private javax.swing.JLabel zoom_reset;
    // End of variables declaration//GEN-END:variables

    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {

        zoomIcons(factor);

        if (notifier != null) {

            notifier.add(Thread.currentThread().threadId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }
}
