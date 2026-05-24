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
 * Sprint 7 telemetría — componente UI standalone para mostrar latencia + nº
 * reconexiones de un peer remoto. Pinta una bolita coloreada (verde/amarillo
 * /naranja/rojo/gris) cuyo color depende del último valor de latencia recibido
 * del broadcast TELEMETRY del host.
 *
 * Diseño:
 *   - JComponent puro (no JLabel) — pinta a bajo nivel para tener control
 *     total del antialiasing y la composición sin pelear con Look&Feel.
 *   - PreferredSize 16×16 por defecto. Se reescala con el zoom de la mesa.
 *   - Estado interno mínimo: latency_ms + reconnection_count + last_update_ms.
 *   - setLatency(...) puede llamarse desde cualquier thread (volatile), el
 *     repaint() siempre se posterga al EDT vía SwingUtilities.invokeLater.
 *   - Si tras STALE_THRESHOLD_MS no se ha refrescado, color pasa a gris
 *     "no data" — defensa contra mostrar valor obsoleto si el host cae.
 *
 * INTEGRACIÓN PENDIENTE (.form via NetBeans):
 *   1. Abrir RemotePlayer.form en NetBeans.
 *   2. Arrastrar un JComponent al lado del avatar.
 *   3. Cambiar su clase a com.tonikelope.coronapoker.LatencyDot.
 *   4. Asignarle nombre 'latency_dot'.
 *   5. En RemotePlayer.java añadir getter + un método de update que llame
 *      latency_dot.setLatency(...) leyendo del WaitingRoomFrame
 *      .getLatest_telemetry() para el nick correspondiente.
 *   6. Hookear el update desde el TELEMETRY case del cliente() switch
 *      (o desde Participant.runPingPongThread, donde ya se llama
 *      jugador.updateLatency para el F7 legacy label).
 *
 * La clase aquí está completamente funcional y testeable de forma aislada;
 * solo le falta el wiring visual y el dispatch desde el handler de
 * TELEMETRY entrante.
 */
// extends JLabel (no JComponent) para que NetBeans pueda asignarlo a un
// campo declarado como JLabel en el .form sin error de tipos. Las pintadas
// de texto/icon del JLabel base se sobrescriben en paintComponent (sin
// llamar super.paintComponent), así que el JLabel actúa como contenedor.
public class LatencyDot extends JLabel {

    /** Latencia mostrada (ms). -1 = unknown / timeout / sin medir. */
    private volatile int latency_ms = -1;
    /** Contador de reconexiones acumulado del peer. */
    private volatile int reconnection_count = 0;
    /** Timestamp local del último setLatency exitoso (System.currentTimeMillis). */
    private volatile long last_update_ms = 0L;

    /** Tras este umbral sin update, la bolita se pinta gris "sin datos". */
    public static final long STALE_THRESHOLD_MS = 15_000;

    /** Umbrales (ms) para clasificar latencia → color. */
    public static final int THRESHOLD_GREEN_MS = 100;
    public static final int THRESHOLD_YELLOW_MS = 250;
    public static final int THRESHOLD_ORANGE_MS = 400;

    public static final Color COLOR_GREEN = new Color(0x4C, 0xAF, 0x50);
    public static final Color COLOR_YELLOW = new Color(0xFF, 0xC1, 0x07);
    public static final Color COLOR_ORANGE = new Color(0xFF, 0x98, 0x00);
    public static final Color COLOR_RED = new Color(0xF4, 0x43, 0x36);
    public static final Color COLOR_STALE = new Color(0x9E, 0x9E, 0x9E);

    public LatencyDot() {
        // Tamaño base 22×22 (suficiente para ver la bola + un badge pequeño
        // si hay reconexiones). Si el GroupLayout del .form pide otro
        // tamaño, NetBeans lo aplica encima de estos defaults. Si no, este
        // es el tamaño que se ve.
        Dimension size = new Dimension(22, 22);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setSize(size);
        setOpaque(false);
        setText(""); // sobrescribe cualquier text="" del JLabel base.
        setToolTipText("---"); // placeholder hasta primer setLatency
    }

    /**
     * Actualiza latencia + contador reconexiones del peer. Thread-safe.
     * Programa repaint en EDT automáticamente.
     *
     * @param latencyMs latencia en ms. -1 = timeout/unknown.
     * @param reconnectionCount contador acumulado de reconexiones del peer.
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
     * Clasifica la latencia (y antigüedad) al color final. Estático para
     * tests AAA del mapping sin instanciar Swing.
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
        // NO llamamos super.paintComponent — JLabel pintaría su texto/icon
        // y aquí queremos control total del rendering (bola + badge).
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

            // Bolita
            g2.setColor(dot);
            g2.fillOval(x, y, diameter, diameter);

            // Borde sutil para contraste sobre fondos claros y oscuros
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawOval(x, y, diameter, diameter);

            // Badge numérico con reconnection_count si > 0
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
