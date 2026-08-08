/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;

/**
 * Standalone UI widget showing a remote peer's connection telemetry (latency + reconnection
 * count) as a colored dot (green/yellow/orange/red/gray), driven by the host's TELEMETRY
 * broadcast.
 *
 * <p>{@link #setLatency} is thread-safe and may be called off the EDT; the repaint is always
 * deferred via {@code SwingUtilities.invokeLater}. If no update arrives within
 * {@link #STALE_THRESHOLD_MS}, the dot turns gray ("no data") rather than showing a stale value.
 */
// Extends JLabel (not JComponent) so NetBeans can bind it to a field declared as JLabel in the
// .form file without a type error. paintComponent() overrides the JLabel's own text/icon
// painting (without calling super.paintComponent), so the JLabel here just acts as a container.
public class LatencyDot extends JLabel {

    /** Displayed latency (ms). -1 = unknown / timeout / not yet measured. */
    private volatile int latency_ms = -1;
    /** Peer's cumulative reconnection count. */
    private volatile int reconnection_count = 0;
    /** Local timestamp (System.currentTimeMillis) of the last successful setLatency call. */
    private volatile long last_update_ms = 0L;

    /** Past this threshold with no update, the dot paints gray ("no data"). */
    public static final long STALE_THRESHOLD_MS = 15_000;

    /**
     * Base side (px) of the dot at zoom 1.0. {@link #applyZoom} multiplies it so the dot scales
     * with the rest of the player cell (avatar, icons, fonts) as the table zoom changes.
     */
    public static final int BASE_SIZE = 22;

    /** Latency thresholds (ms) used to classify latency into a color. */
    public static final int THRESHOLD_GREEN_MS = 100;
    public static final int THRESHOLD_YELLOW_MS = 250;
    public static final int THRESHOLD_ORANGE_MS = 400;

    public static final Color COLOR_GREEN = new Color(0x4C, 0xAF, 0x50);
    public static final Color COLOR_YELLOW = new Color(0xFF, 0xC1, 0x07);
    public static final Color COLOR_ORANGE = new Color(0xFF, 0x98, 0x00);
    public static final Color COLOR_RED = new Color(0xF4, 0x43, 0x36);
    public static final Color COLOR_STALE = new Color(0x9E, 0x9E, 0x9E);

    public LatencyDot() {
        // Base BASE_SIZE×BASE_SIZE size (room for the dot plus a small reconnection badge). If
        // the .form's GroupLayout requests another size, NetBeans applies it on top of these
        // defaults; otherwise this is what's shown. applyZoom(...) rescales it with the table.
        Dimension size = new Dimension(BASE_SIZE, BASE_SIZE);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setSize(size);
        setOpaque(false);
        setText(""); // override any text="" set on the underlying JLabel by the .form
        setToolTipText("---"); // placeholder until the first setLatency call
    }

    /**
     * Rescales the dot to {@code BASE_SIZE * factor} to track the table zoom (like the player's
     * avatar/icons/fonts). Must be called on the EDT; revalidates the container so GroupLayout
     * repositions the cell.
     *
     * @param factor absolute table scale (1f = neutral zoom)
     */
    public void applyZoom(float factor) {
        int side = Math.max(BASE_SIZE / 4, Math.round(BASE_SIZE * factor));
        Dimension size = new Dimension(side, side);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setSize(size);
        revalidate();
        repaint();
    }

    /**
     * Updates the peer's latency and reconnection count. Thread-safe; schedules a repaint on
     * the EDT.
     *
     * @param latencyMs latency in ms, or -1 for timeout/unknown
     * @param reconnectionCount peer's cumulative reconnection count
     */
    public void setLatency(int latencyMs, int reconnectionCount) {
        this.latency_ms = latencyMs;
        this.reconnection_count = reconnectionCount;
        this.last_update_ms = System.currentTimeMillis();
        javax.swing.SwingUtilities.invokeLater(() -> {
            updateTooltip();
            repaint();
        });
    }

    public int getLatencyMs() {
        return latency_ms;
    }

    public int getReconnectionCount() {
        return reconnection_count;
    }

    public long getLastUpdateMs() {
        return last_update_ms;
    }

    /**
     * Maps latency (and staleness) to the final dot color. Static so the mapping can be unit
     * tested without instantiating Swing.
     */
    public static Color colorFor(int latencyMs, long ageMs) {
        if (ageMs > STALE_THRESHOLD_MS) {
            return COLOR_STALE;
        }
        if (latencyMs < 0) {
            return COLOR_RED;
        }
        if (latencyMs <= THRESHOLD_GREEN_MS) {
            return COLOR_GREEN;
        }
        if (latencyMs <= THRESHOLD_YELLOW_MS) {
            return COLOR_YELLOW;
        }
        if (latencyMs <= THRESHOLD_ORANGE_MS) {
            return COLOR_ORANGE;
        }
        return COLOR_RED;
    }

    private void updateTooltip() {
        setToolTipText(latency_ms < 0 ? "? ms" : (latency_ms + " ms"));
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Do NOT call super.paintComponent — JLabel would paint its own text/icon, and we want
        // full control over rendering (dot + badge) here.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int diameter = Math.min(w, h) - 2;
            if (diameter < 4) {
                return;
            }
            int x = (w - diameter) / 2;
            int y = (h - diameter) / 2;

            long age = System.currentTimeMillis() - last_update_ms;
            Color dot = colorFor(latency_ms, last_update_ms == 0 ? Long.MAX_VALUE : age);

            // Dot
            g2.setColor(dot);
            g2.fillOval(x, y, diameter, diameter);

            // Subtle outline for contrast against both light and dark backgrounds
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawOval(x, y, diameter, diameter);

            // Reconnection-count badge, shown only when reconnection_count > 0
            if (reconnection_count > 0) {
                String txt = reconnection_count > 9 ? "9+" : String.valueOf(reconnection_count);
                int badge_diam = Math.max(8, diameter / 2);
                int bx = w - badge_diam;
                int by = h - badge_diam;
                g2.setColor(Color.WHITE);
                g2.fillOval(bx, by, badge_diam, badge_diam);
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawOval(bx, by, badge_diam, badge_diam);
                g2.setColor(Color.BLACK);
                g2.setFont(getFont() != null
                        ? getFont().deriveFont(Font.BOLD, badge_diam * 0.7f)
                        : new Font(Font.DIALOG, Font.BOLD, (int) (badge_diam * 0.7f)));
                FontMetrics fm = g2.getFontMetrics();
                int tx = bx + (badge_diam - fm.stringWidth(txt)) / 2;
                int ty = by + (badge_diam + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);
            }
        } finally {
            g2.dispose();
        }
    }
}
