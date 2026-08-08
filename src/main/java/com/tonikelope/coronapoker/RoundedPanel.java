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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * JPanel with rounded corners, used for the seat/action panels.
 *
 * @author tonikelope
 */
public class RoundedPanel extends JPanel {

    public static final int DEFAULT_ARC = 20;
    private final int arc;
    // This panel is NEVER truly opaque (same pattern as LocalPlayer/RemotePlayer.setOpaque). If
    // it were, Swing's RepaintManager would treat it as covering that region and stop repainting
    // the ancestor behind it, so an isolated setBackground (outside a full repaint) would leave
    // the corners outside the arc black. setOpaque only records the fill INTENT here; the actual
    // fill is drawn (rounded) in paintComponent, while Swing still repaints the real background
    // behind the non-opaque panel, keeping the corners clean.
    private volatile boolean rounded_fill = false;

    public RoundedPanel() {
        this(DEFAULT_ARC);
    }

    public RoundedPanel(int arc) {
        super();
        this.arc = arc;
        setOpaque(true);
    }

    @Override
    public void setOpaque(boolean isOpaque) {
        this.rounded_fill = isOpaque;
        super.setOpaque(false);
    }

    // The fill intent (what was passed to setOpaque). Don't use isOpaque() for this: the panel is
    // ALWAYS non-opaque as far as Swing is concerned (so it keeps repainting the background
    // behind it), so isOpaque() returns false even while the panel is painting its fill.
    public boolean isRoundedFill() {
        return rounded_fill;
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (rounded_fill) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                float effective_arc = arc * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), Math.round(effective_arc), Math.round(effective_arc));
            } finally {
                g2d.dispose();
            }
            // super.paintComponent is deliberately not called: Swing already repainted the
            // background behind this (non-opaque) panel, and the rounded fill goes on top of
            // that; calling super would paint a square background over it.
        } else {
            super.paintComponent(g);
        }
    }
}
