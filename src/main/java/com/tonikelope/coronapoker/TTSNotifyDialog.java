/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.Timer;

/**
 *
 * @author tonikelope
 */
public class TTSNotifyDialog extends javax.swing.JDialog {

    public static final int AVATAR_SIZE = 80;
    public static final int MAX_IMAGE_WIDTH = (int) Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.2f);

    private volatile String player = null;
    private volatile Timer timer = null;

    /**
     * Creates new form NickTTSDialog
     */
    public TTSNotifyDialog(java.awt.Frame parent, boolean modal, String nick, String msg) {
        super(parent, modal);

        this.player = nick;

        initComponents();

        int avatar_size = Math.round(AVATAR_SIZE * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP));

        Helpers.setResourceIconLabel(tts_panel.getSound_icon(), getClass().getResource((!GameFrame.SONIDOS || !GameFrame.SONIDOS_TTS || !GameFrame.TTS_SERVER || Audio.TTS_BLOCKED_USERS.contains(nick)) ? "/images/mute.png" : "/images/sound.png"), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

        if ((!GameFrame.SONIDOS || !GameFrame.SONIDOS_TTS || !GameFrame.TTS_SERVER || Audio.TTS_BLOCKED_USERS.contains(nick))) {

            tts_panel.getMessage().setText("[" + nick + (msg != null ? "]: " + msg : "]"));
        } else {
            tts_panel.getMessage().setText(nick + (msg != null ? " " + msg : ""));
        }

        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)) {

            if (GameFrame.getInstance().getSala_espera().getAvatar() != null) {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), GameFrame.getInstance().getSala_espera().getAvatar().getAbsolutePath(), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            } else {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), getClass().getResource("/images/avatar_default.png"), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            }
        } else {

            if (GameFrame.getInstance().getParticipantes().get(nick).getAvatar() != null) {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), GameFrame.getInstance().getParticipantes().get(nick).getAvatar().getAbsolutePath(), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

            } else {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), getClass().getResource("/images/avatar_default.png"), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            }

        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        pack();

    }

    public TTSNotifyDialog(java.awt.Frame parent, boolean modal, boolean tts) {
        super(parent, modal);

        initComponents();

        int sound_size = Math.round(AVATAR_SIZE * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP));

        Helpers.setResourceIconLabel(tts_panel.getSound_icon(), getClass().getResource(!tts ? "/images/mute.png" : "/images/sound.png"), Math.round(sound_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(sound_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

        tts_panel.getMessage().setText(tts ? "TTS ACTIVADO POR EL SERVIDOR" : "TTS DESACTIVADO POR EL SERVIDOR");

        tts_panel.setBackground(tts ? new Color(102, 102, 102) : Color.RED);

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        Helpers.translateComponents(this, false);

        pack();

        timer = new Timer(GameFrame.TTS_NO_SOUND_TIMEOUT, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                timer.stop();
                dispose();
            }
        });

        timer.setRepeats(false);
        timer.setCoalesce(false);

    }

    public TTSNotifyDialog(java.awt.Frame parent, boolean modal, String nick, ImageIcon image) {
        super(parent, modal);

        this.player = nick;

        initComponents();

        int avatar_size = Math.round(AVATAR_SIZE * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP));

        tts_panel.getSound_icon().setVisible(false);

        tts_panel.getMessage().setText(nick);

        if (GameFrame.getInstance().getLocalPlayer().getNickname().equals(nick)) {

            if (GameFrame.getInstance().getSala_espera().getAvatar() != null) {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), GameFrame.getInstance().getSala_espera().getAvatar().getAbsolutePath(), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            } else {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), getClass().getResource("/images/avatar_default.png"), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            }
        } else {

            if (GameFrame.getInstance().getParticipantes().get(nick).getAvatar() != null) {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), GameFrame.getInstance().getParticipantes().get(nick).getAvatar().getAbsolutePath(), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

            } else {

                Helpers.setResourceIconLabel(tts_panel.getMessage(), getClass().getResource("/images/avatar_default.png"), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)), Math.round(avatar_size * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            }

        }

        tts_panel.getImage_label().setIcon(image);

        Helpers.updateFonts(this, Helpers.GUI_FONT, 1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);

        pack();

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tts_panel = new com.tonikelope.coronapoker.TTSNotifyPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setAutoRequestFocus(false);
        setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        setFocusCycleRoot(false);
        setFocusable(false);
        setFocusableWindowState(false);
        setUndecorated(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tts_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tts_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked

        if (player != null && !Audio.TTS_BLOCKED_USERS.contains(player) && !GameFrame.getInstance().getLocalPlayer().getNickname().equals(player)) {

            if (Audio.TTS_PLAYER != null) {
                try {
                    // TODO add your handling code here:
                    Audio.TTS_PLAYER.stop();
                } catch (Exception ex) {
                    Logger.getLogger(TTSNotifyDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿IGNORAR LOS MENSAJES TTS DE ESTE USUARIO?") == 0) {

                Audio.TTS_BLOCKED_USERS.add(player);
            }

            if (timer != null) {
                timer.stop();
                dispose();
            }
        }
    }//GEN-LAST:event_formMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:
        if (timer != null) {

            timer.start();
        }
    }//GEN-LAST:event_formComponentShown

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private com.tonikelope.coronapoker.TTSNotifyPanel tts_panel;
    // End of variables declaration//GEN-END:variables
}
