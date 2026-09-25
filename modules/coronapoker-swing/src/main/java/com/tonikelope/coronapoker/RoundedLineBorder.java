/*
 * Copyright (C) 2025 tonikelope
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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.Border;

/**
 * Line border with ROUNDED corners (Swing's {@code createLineBorder} only draws
 * square ones). Same color and thickness as a plain LineBorder, but strokes a
 * rounded rectangle with antialiasing. Used for the thin grouping boxes in
 * config dialogs (blind cap/escalation, rebuy).
 *
 * The panel corners outside the arc show the panel's own background; since that
 * matches the container's background in Nimbus dialogs, the rounding looks
 * clean without making the panel non-opaque.
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
