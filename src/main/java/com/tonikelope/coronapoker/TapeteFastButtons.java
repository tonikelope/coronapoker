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
        botones = new Object[][]{{chat, "chat.png", "Chat rápido"}, {image, "image.png", "Enviar imagen"}, {compact, "compact.png", "Vista compacta"}, {zoom_in, "zoom_in.png", "Aumentar zoom"}, {zoom_reset, "zoom_reset.png", "Reset zoom"}, {zoom_out, "zoom_out.png", "Reducir zoom"}, {fullscreen, "fullscreen.png", "Pantalla completa"}, {log, "log.png", "Registro"}, {rebuy, "rebuy.png", "Recomprar"}};

        for (Object[] b : botones) {
            Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));
            ((JLabel) b[0]).setToolTipText(Translator.translate((String) b[2]));
        }

        Helpers.setScaledIconLabel(menu, getClass().getResource("/images/fast_panel/menu.png"), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H), Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * H));

        pref_size = getPreferredSize();
        hideButtons();
        setComListeners();
    }

    private void zoomIcons(float factor) {

        Helpers.GUIRunAndWait(() -> {
            if (!chat.isVisible()) {
                for (Object[] b : botones) {
                    Helpers.setScaledIconLabel(((JLabel) b[0]), getClass().getResource("/images/fast_panel/" + ((String) b[1])), Math.round(factor * H), Math.round(factor * H));

                }

                zoom_factor = factor;
            }
        });
    }

    private void setComListeners() {
        for (Object[] b : botones) {
            ((Component) b[0]).addMouseListener(new MouseAdapter() {

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && (GameFrame.getInstance().getFastchat_dialog() == null || !GameFrame.getInstance().getFastchat_dialog().isVisible())) {
                        showButtons();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        Rectangle r = new Rectangle(getLocationOnScreen(), getSize());
                        if (!r.contains(e.getPoint())) {

                            hideButtons();
                        }
                    }
                }
            });
        }
    }

    public void hideButtons() {
        Helpers.GUIRun(() -> {
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
        image = new javax.swing.JLabel();
        rebuy = new javax.swing.JLabel();
        log = new javax.swing.JLabel();
        compact = new javax.swing.JLabel();
        zoom_out = new javax.swing.JLabel();
        zoom_reset = new javax.swing.JLabel();
        zoom_in = new javax.swing.JLabel();
        fullscreen = new javax.swing.JLabel();

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
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 5));

        menu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/menu.png"))); // NOI18N
        menu.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        menu.setDoubleBuffered(true);
        add(menu);

        chat.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/chat.png"))); // NOI18N
        chat.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat.setDoubleBuffered(true);
        chat.setFocusable(false);
        chat.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                chatMouseClicked(evt);
            }
        });
        add(chat);

        image.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/image.png"))); // NOI18N
        image.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        image.setDoubleBuffered(true);
        image.setFocusable(false);
        image.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                imageMouseClicked(evt);
            }
        });
        add(image);

        rebuy.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/rebuy.png"))); // NOI18N
        rebuy.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy.setDoubleBuffered(true);
        rebuy.setFocusable(false);
        rebuy.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                rebuyMouseClicked(evt);
            }
        });
        add(rebuy);

        log.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/log.png"))); // NOI18N
        log.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        log.setDoubleBuffered(true);
        log.setFocusable(false);
        log.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                logMouseClicked(evt);
            }
        });
        add(log);

        compact.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/compact.png"))); // NOI18N
        compact.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        compact.setDoubleBuffered(true);
        compact.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                compactMouseClicked(evt);
            }
        });
        add(compact);

        zoom_out.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_out.png"))); // NOI18N
        zoom_out.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_out.setDoubleBuffered(true);
        zoom_out.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                zoom_outMouseClicked(evt);
            }
        });
        add(zoom_out);

        zoom_reset.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_reset.png"))); // NOI18N
        zoom_reset.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_reset.setDoubleBuffered(true);
        zoom_reset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                zoom_resetMouseClicked(evt);
            }
        });
        add(zoom_reset);

        zoom_in.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/zoom_in.png"))); // NOI18N
        zoom_in.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        zoom_in.setDoubleBuffered(true);
        zoom_in.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                zoom_inMouseClicked(evt);
            }
        });
        add(zoom_in);

        fullscreen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/fast_panel/fullscreen.png"))); // NOI18N
        fullscreen.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fullscreen.setDoubleBuffered(true);
        fullscreen.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                fullscreenMouseClicked(evt);
            }
        });
        add(fullscreen);
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && (GameFrame.getInstance().getFastchat_dialog() == null || !GameFrame.getInstance().getFastchat_dialog().isVisible())) {
            showButtons();
        }
    }//GEN-LAST:event_formMouseEntered

    private void formMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseExited
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            Rectangle r = new Rectangle(this.getLocationOnScreen(), this.getSize());
            if (!r.contains(evt.getPoint())) {

                hideButtons();
            }
        }

    }//GEN-LAST:event_formMouseExited

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_chatMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatDialog();
        }
    }//GEN-LAST:event_chatMouseClicked

    private void imageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_imageMouseClicked

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().showFastChatImage();
        }
    }//GEN-LAST:event_imageMouseClicked

    private void compactMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_compactMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getCompact_menu().doClick();
        }

    }//GEN-LAST:event_compactMouseClicked

    private void zoom_outMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_outMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_out().doClick();
        }

    }//GEN-LAST:event_zoom_outMouseClicked

    private void zoom_resetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_resetMouseClicked
        // TODO add your handling code here:

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_reset().doClick();
        }
    }//GEN-LAST:event_zoom_resetMouseClicked

    private void zoom_inMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_zoom_inMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getZoom_menu_in().doClick();
        }

    }//GEN-LAST:event_zoom_inMouseClicked

    private void fullscreenMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fullscreenMouseClicked
        // TODO add your handling code here:

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

            GameFrame.getInstance().getFull_screen_menu().doClick();
        }
    }//GEN-LAST:event_fullscreenMouseClicked

    private void rebuyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_rebuyMouseClicked
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
    private javax.swing.JLabel rebuy;
    private javax.swing.JLabel zoom_in;
    private javax.swing.JLabel zoom_out;
    private javax.swing.JLabel zoom_reset;
    // End of variables declaration//GEN-END:variables

    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {

        zoomIcons(factor);

        if (notifier != null) {

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }
}
