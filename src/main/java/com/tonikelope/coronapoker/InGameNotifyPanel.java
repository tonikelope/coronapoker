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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;

/**
 * Cuerpo de las notificaciones del juego: caja de esquinas redondeadas con el
 * color que pide cada aviso (el fondo es su background y la letra su
 * foreground), un filo del color de la letra muy diluido para despegarla del
 * tapete, y una cuenta atrás opcional pintada dentro de la propia caja.
 *
 * @author tonikelope
 */
public class InGameNotifyPanel extends javax.swing.JPanel {

    // Radio de las esquinas y grosor del filo, relativos al alto de la caja: la
    // silueta acompaña al tamaño de la letra sea cual sea el zoom.
    private static final float ARC_RATIO = 0.6f;
    private static final float BORDER_RATIO = 0.02f;
    private static final int BORDER_ALPHA = 90;

    // Franja de la cuenta atrás: alto y separación de los lados, también relativos
    // al alto de la caja.
    private static final float COUNTDOWN_RATIO = 0.06f;
    private static final int COUNTDOWN_TRACK_ALPHA = 70;

    // Cached overlay color rebuilt only when the brightness changes.
    private Color cached_overlay = null;
    private float cached_brightness = -1f;

    // La silueta redondeada exige que la ventana sea transparente por píxel; donde
    // el sistema no lo permita, la caja se pinta rectangular como siempre.
    private boolean rounded = false;

    // Fracción pendiente de la cuenta atrás (1 = recién abierta, 0 = agotada), o
    // negativo si esta notificación no lleva cuenta atrás.
    private float countdown = -1f;

    public void setRounded(boolean rounded) {
        this.rounded = rounded;
        setOpaque(!rounded);
    }

    public void setCountdown(float fraction) {
        this.countdown = fraction;
        repaint();
    }

    private int arc() {
        return rounded ? Math.round(Math.min(getWidth(), getHeight()) * ARC_RATIO) : 0;
    }

    private Color diluted(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (!rounded) {
            super.paintComponent(g);
            paintCountdown((Graphics2D) g, 0);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            final int arc = arc();

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

            g2.setColor(diluted(msg.getForeground(), BORDER_ALPHA));
            g2.setStroke(new BasicStroke(Math.max(1f, getHeight() * BORDER_RATIO)));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

            paintCountdown(g2, arc);

        } finally {
            g2.dispose();
        }
    }

    // Cuenta atrás como una franja en la base de la caja, DENTRO de su silueta: una
    // barra colgada por fuera rompería las esquinas redondeadas.
    private void paintCountdown(Graphics2D g, int arc) {

        if (countdown < 0f) {
            return;
        }

        final int thickness = Math.max(2, Math.round(getHeight() * COUNTDOWN_RATIO));
        final int inset = Math.max(2, arc / 4);
        final int y = getHeight() - thickness - inset;
        final int track = getWidth() - 2 * inset;

        if (track <= 0 || y <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(diluted(msg.getForeground(), COUNTDOWN_TRACK_ALPHA));
            g2.fillRoundRect(inset, y, track, thickness, thickness, thickness);
            g2.setColor(msg.getForeground());
            g2.fillRoundRect(inset, y, Math.round(track * Math.min(1f, countdown)), thickness, thickness, thickness);
        } finally {
            g2.dispose();
        }
    }

    // paint() is intentional here (not paintComponent): the overlay must be
    // drawn after paintChildren so it sits on top of the JLabel.
    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (GameFrame.getInstance() != null) {
            float b = GameFrame.getInstance().getCapa_brillo().getBrightness();
            if (b > 0f) {
                if (cached_overlay == null || cached_brightness != b) {
                    cached_overlay = new Color(0f, 0f, 0f, b);
                    cached_brightness = b;
                }
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(cached_overlay);
                    // El velo de la capa de brillo se queda dentro de la silueta: fuera de
                    // ella la ventana es transparente y una esquina oscura la delataría.
                    final int arc = arc();
                    g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                } finally {
                    g2d.dispose();
                }
            }
        }
    }

    public JLabel getMsg() {
        return msg;
    }

    /**
     * Creates new form ChatNotifyPanel
     */
    public InGameNotifyPanel() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        msg = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 0, 0));
        setFocusable(false);

        msg.setFont(new java.awt.Font("Dialog", 1, 28)); // NOI18N
        msg.setForeground(new java.awt.Color(255, 255, 255));
        msg.setText("NICK: bla bla bla");
        msg.setFocusable(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(msg)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(msg)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel msg;
    // End of variables declaration//GEN-END:variables
}
