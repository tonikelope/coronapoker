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
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.html.HTML;

/**
 * JEditorPane that paints a rounded background under each chat bubble before
 * Swing's HTML renderer draws the bubble content on top. Bubbles are <table>
 * elements carrying class='bubble bubble-mine' or class='bubble bubble-other';
 * Swing's CSS1 renderer ignores 'border-radius', so we paint the rounded
 * rectangle ourselves. The bubble's <table> must NOT carry the bgcolor
 * attribute, otherwise the renderer would paint a square fill on top of the
 * rounded one.
 */
public class RoundedChatPane extends JEditorPane {

    private static final int CORNER_RADIUS = 12;
    private static final Color BUBBLE_MINE_BG = new Color(0xd9fdd3);
    private static final Color BUBBLE_OTHER_BG = Color.WHITE;

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBubbleBackgrounds(g2);
        } catch (Exception ex) {
            Logger.getLogger(RoundedChatPane.class.getName()).log(Level.FINE, "bubble paint failed", ex);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private void paintBubbleBackgrounds(Graphics2D g2) {
        View root = getUI().getRootView(this);
        if (root == null) {
            return;
        }

        Insets ins = getInsets();
        Rectangle alloc = new Rectangle(
                ins.left,
                ins.top,
                getWidth() - ins.left - ins.right,
                getHeight() - ins.top - ins.bottom);

        Rectangle clip = g2.getClipBounds();
        walk(g2, root, alloc, clip);
    }

    private static void walk(Graphics2D g2, View view, Rectangle alloc, Rectangle clip) {
        if (alloc.width <= 0 || alloc.height <= 0) {
            return;
        }
        if (clip != null && !alloc.intersects(clip)) {
            return;
        }

        Element elem = view.getElement();
        if (elem != null) {
            AttributeSet attrs = elem.getAttributes();
            Object tag = attrs.getAttribute(StyleConstants.NameAttribute);
            if (tag == HTML.Tag.TABLE) {
                Color bg = bubbleColor(attrs);
                if (bg != null) {
                    g2.setColor(bg);
                    g2.fillRoundRect(alloc.x, alloc.y, alloc.width, alloc.height, CORNER_RADIUS, CORNER_RADIUS);
                }
                // Bubbles do not nest: stop descending. If the table is not a bubble
                // (no matching class) we also stop because the chat HTML never wraps
                // anything sortable inside <table>.
                return;
            }
        }

        int n = view.getViewCount();
        for (int i = 0; i < n; i++) {
            View child = view.getView(i);
            Shape childAlloc = view.getChildAllocation(i, alloc);
            if (childAlloc == null) {
                continue;
            }
            Rectangle childRect = (childAlloc instanceof Rectangle)
                    ? (Rectangle) childAlloc
                    : childAlloc.getBounds();
            walk(g2, child, childRect, clip);
        }
    }

    private static Color bubbleColor(AttributeSet attrs) {
        Object classAttr = attrs.getAttribute(HTML.Attribute.CLASS);
        if (classAttr == null) {
            return null;
        }
        String cls = classAttr.toString();
        if (!cls.contains("bubble")) {
            return null;
        }
        if (cls.contains("bubble-mine")) {
            return BUBBLE_MINE_BG;
        }
        if (cls.contains("bubble-other")) {
            return BUBBLE_OTHER_BG;
        }
        return null;
    }
}
