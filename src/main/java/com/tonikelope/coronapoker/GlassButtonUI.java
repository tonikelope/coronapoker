/*
 * Copyright (C) 2026 tonikelope
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
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * Glassmorphism ButtonUI for the start-screen button bar: paints a rounded, translucent black
 * background (the felt shows through), a subtle top gloss highlight and, optionally, an accent
 * tint/halo. Hover/pressed state is read straight from the ButtonModel (rollover), so no external
 * MouseListener is needed.
 *
 * <p>Non-invasive: applied via {@code button.setUI(new GlassButtonUI(...))} on existing JButtons
 * (no changes to generated code or the .form); {@code installUI} makes the button non-opaque with
 * no border/fill of its own so only this UI's painting shows.
 *
 * @author tonikelope
 */
public class GlassButtonUI extends BasicButtonUI {

    // Fill-tint and hover-halo color; null means a neutral, untinted glass button (no halo).
    private final Color accent;
    // If true, fill with an accent-tinted gradient even at rest (highlighted primary action).
    private final boolean filled_accent;
    // Base glass opacity at rest (secondary buttons use a lower value for more transparency).
    private final float base_alpha;
    private final int radius;

    /**
     * @param accent accent color for the fill tint and hover halo; {@code null} for a neutral,
     * untinted button
     * @param filled_accent paint the accent-tinted fill even at rest, not just on hover
     * @param hover_only_accent currently unused, kept for constructor-signature compatibility
     * @param base_alpha base glass opacity at rest
     * @param radius corner radius in pixels
     */
    public GlassButtonUI(Color accent, boolean filled_accent, boolean hover_only_accent, float base_alpha, int radius) {
        this.accent = accent;
        this.filled_accent = filled_accent;
        this.base_alpha = base_alpha;
        this.radius = radius;
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        AbstractButton b = (AbstractButton) c;
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setForeground(Color.WHITE);
        // Inner padding so the icon/text doesn't touch the rounded edge.
        b.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        b.setIconTextGap(14);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel m = b.getModel();
        int w = c.getWidth();
        int h = c.getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean enabled = b.isEnabled();
        boolean pressed = m.isArmed() && m.isPressed();
        boolean hover = enabled && m.isRollover();

        // Hover/pressed raise opacity relative to the base value, so the highlight stays
        // proportional instead of saturating as base_alpha increases.
        float fill_alpha;
        if (!enabled) {
            fill_alpha = 0.30f;
        } else if (pressed) {
            fill_alpha = Math.min(0.92f, base_alpha + 0.22f);
        } else if (hover) {
            fill_alpha = Math.min(0.85f, base_alpha + 0.12f);
        } else {
            fill_alpha = base_alpha;
        }

        RoundRectangle2D rr = new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h - 3f, radius, radius);

        // 1. Glass: translucent black.
        g2.setColor(new Color(0f, 0f, 0f, fill_alpha));
        g2.fill(rr);

        // 2. Accent tint (filled primary buttons only).
        if (filled_accent && accent != null && enabled) {
            int top = Math.round((hover ? 0.34f : 0.22f) * 255);
            int bot = Math.round((hover ? 0.14f : 0.08f) * 255);
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), top),
                    0, h, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), bot));
            g2.setPaint(gp);
            g2.fill(rr);
        }

        // 3. Top gloss highlight, covering the upper half.
        GradientPaint gloss = new GradientPaint(
                0, 1.5f, new Color(255, 255, 255, hover ? 60 : 42),
                0, h * 0.55f, new Color(255, 255, 255, 0));
        g2.setPaint(gloss);
        g2.fill(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3f, h * 0.55f, radius, radius));

        // 4. Accent halo on hover (soft glow; e.g. tints the exit button red).
        if (hover && accent != null) {
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
            g2.setStroke(new BasicStroke(3.5f));
            g2.draw(new RoundRectangle2D.Float(0f, 0f, w - 0.5f, h - 0.5f, radius + 4f, radius + 4f));
        }

        g2.dispose();

        // Label (icon + text) painted on top via BasicButtonUI.
        super.paint(g, c);
    }

    // Subtle text shadow for legibility over the glass + felt background.
    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fg = b.getForeground();
        Rectangle shadow = new Rectangle(textRect.x + 1, textRect.y + 1, textRect.width, textRect.height);
        b.setForeground(new Color(0, 0, 0, 150));
        super.paintText(g2, b, shadow, text);
        b.setForeground(fg);
        super.paintText(g2, b, textRect, text);
        g2.dispose();
    }
}
