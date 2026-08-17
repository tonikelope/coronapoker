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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;

/**
 * Body of an in-game notification: a rounded-corner box in the color the alert
 * requests (background = fill, foreground = text) with an optional countdown
 * bar painted inside the box itself.
 *
 * @author tonikelope
 */
public class InGameNotifyPanel extends javax.swing.JPanel {

    // Corner radius, relative to box height, so the silhouette scales with the
    // text at any zoom level. No border: the background color already separates
    // the box from the felt, and a foreground-colored outline used to draw a
    // white halo around light-text notifications.
    private static final float ARC_RATIO = 0.6f;

    // Countdown bar: height and side inset, both relative to box height.
    private static final float COUNTDOWN_RATIO = 0.06f;
    private static final int COUNTDOWN_TRACK_ALPHA = 70;

    // Cached overlay color rebuilt only when the brightness changes.
    private Color cached_overlay = null;
    private float cached_brightness = -1f;

    // Cached diluted countdown-track color, rebuilt only when the text (foreground) color changes,
    // instead of allocating a new Color on every 20 Hz countdown paint.
    private Color cached_track = null;
    private Color cached_track_src = null;

    // The rounded silhouette needs a per-pixel-transparent window; where the
    // platform doesn't support that, the box falls back to plain rectangular
    // painting.
    private boolean rounded = false;

    // Remaining countdown fraction (1 = just opened, 0 = expired), or negative
    // if this notification has no countdown.
    private float countdown = -1f;

    public void setRounded(boolean rounded) {
        this.rounded = rounded;
        setOpaque(!rounded);
    }

    public void setCountdown(float fraction) {
        this.countdown = fraction;
        // Full repaint (not a dirty-region strip): paint() reapplies the brightness overlay across
        // the WHOLE box, and this panel lives in a standalone dialog outside the table's repaint
        // tree, so a bottom-band-only repaint could leave the overlay stale above the bar if the
        // brightness changed mid-countdown. The toast is tiny and short-lived, so full repaint at
        // 20 Hz is cheap; the per-paint saving here isn't worth that seam. (The diluted track color
        // is still cached in paintCountdown — that win has no such caveat.)
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

            paintCountdown(g2, arc);

        } finally {
            g2.dispose();
        }
    }

    // Countdown drawn as a bar at the bottom of the box, INSIDE its silhouette:
    // a bar hanging outside would break the rounded corners.
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
            Color fg = msg.getForeground();
            if (cached_track == null || !fg.equals(cached_track_src)) {
                cached_track = diluted(fg, COUNTDOWN_TRACK_ALPHA);
                cached_track_src = fg;
            }
            g2.setColor(cached_track);
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
                    // The brightness overlay stays within the silhouette: outside it the
                    // window is transparent, and a dark corner there would give it away.
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
     * Creates new form InGameNotifyPanel
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
