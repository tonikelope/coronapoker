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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTML;

/**
 * BlockView that paints a rounded rectangle background under chat bubble divs.
 * Mirrors what {@link RoundedPanel} does at the JComponent level, but at the
 * Swing text-View level so nested HTML content (text, tonimg, emojis) keeps
 * being rendered by the standard HTMLEditorKit machinery.
 *
 * The view is selected in {@link CoronaHTMLEditorKit} for any &lt;div&gt;
 * whose class attribute contains "bubble-mine" or "bubble-other". The fill
 * color is derived from that class; "border-radius" CSS is ignored by Swing,
 * so we paint the rounded rect ourselves before delegating to BlockView.
 */
public class RoundedBubbleView extends BlockView {

    private static final int CORNER_RADIUS = 12;
    private static final Color BUBBLE_MINE_BG = new Color(0xd9fdd3);
    private static final Color BUBBLE_OTHER_BG = Color.WHITE;

    public RoundedBubbleView(Element elem) {
        super(elem, View.Y_AXIS);
    }

    @Override
    public void paint(Graphics g, Shape allocation) {
        Color bg = pickColor(getElement().getAttributes());
        if (bg != null) {
            Rectangle r = (allocation instanceof Rectangle)
                    ? (Rectangle) allocation
                    : allocation.getBounds();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(r.x, r.y, r.width, r.height, CORNER_RADIUS * 2, CORNER_RADIUS * 2);
            } finally {
                g2.dispose();
            }
        }
        super.paint(g, allocation);
    }

    private static Color pickColor(AttributeSet attrs) {
        Object classAttr = attrs.getAttribute(HTML.Attribute.CLASS);
        if (classAttr == null) {
            return null;
        }
        String cls = classAttr.toString();
        if (cls.contains("bubble-mine")) {
            return BUBBLE_MINE_BG;
        }
        if (cls.contains("bubble-other")) {
            return BUBBLE_OTHER_BG;
        }
        return null;
    }
}
