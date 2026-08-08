/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JButton;

/**
 * JButton that paints translucent while disabled, instead of the default solid gray, so the
 * unavailable action dims and lets the felt show through behind it. When enabled it paints
 * exactly like a normal JButton (no extra cost).
 *
 * Used in LocalPlayer's action buttons (check/fold/bet/all-in), wired via the .form file like the
 * rest of the project's custom components. Deliberately non-opaque: on repaint, Swing first
 * refreshes the opaque ancestor (the felt), and the alpha composite is then applied over fresh
 * pixels.
 *
 * @author tonikelope
 */
public class TranslucentDisabledButton extends JButton {

    // Opacity while disabled (0..1), matched to the LocalPlayer idle border/background alpha
    // (Color(204,204,204,75), i.e. 75/255 ~= 29.4%) so they line up exactly. Shared with
    // TranslucentDisabledSpinner.
    public static final float DISABLED_OPACITY = 75f / 255f;

    public TranslucentDisabledButton() {
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        if (isEnabled()) {
            super.paint(g);
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_OPACITY));
                super.paint(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
