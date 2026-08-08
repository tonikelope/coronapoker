/*
 * Copyright (C) 2025 tonikelope
 */
package com.tonikelope.coronapoker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.Border;

/**
 * Line border with ROUNDED corners (Swing's {@code createLineBorder} only draws square ones).
 * Same color and thickness as a plain LineBorder, but strokes a rounded rectangle with
 * antialiasing. Used for the thin grouping boxes in config dialogs (blind cap/escalation,
 * rebuy).
 *
 * The panel corners outside the arc show the panel's own background; since that matches the
 * container's background in Nimbus dialogs, the rounding looks clean without making the panel
 * non-opaque.
 *
 * @author tonikelope
 */
public class RoundedLineBorder implements Border {

    private final Color color;
    private final int thickness;
    private final int arc;

    public RoundedLineBorder(Color color, int thickness, int arc) {
        this.color = color;
        this.thickness = Math.max(1, thickness);
        this.arc = arc;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        // Offset by half the stroke width so the line stays within the component bounds.
        float off = thickness / 2f;
        g2.draw(new RoundRectangle2D.Float(x + off, y + off, width - thickness, height - thickness, arc, arc));
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        int n = thickness + 2;
        return new Insets(n, n, n, n);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
