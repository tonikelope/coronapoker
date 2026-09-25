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
import javax.swing.JSpinner;

/**
 * JSpinner that paints translucent while disabled, at the same opacity as the
 * toolbar buttons ({@link TranslucentDisabledButton#DISABLED_OPACITY}), so the
 * unavailable control dims and lets the felt show through behind it. When
 * enabled it paints exactly like a normal JSpinner.
 *
 * Used as LocalPlayer's bet spinner, wired via the .form file like the rest of
 * the project's custom components. Deliberately non-opaque: on repaint, Swing
 * first refreshes the opaque ancestor (the felt), and the alpha composite is
 * then applied over fresh pixels.
 *
 * @author tonikelope
 */
public class TranslucentDisabledSpinner extends JSpinner {

    public TranslucentDisabledSpinner() {
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        if (isEnabled()) {
            super.paint(g);
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TranslucentDisabledButton.DISABLED_OPACITY));
                super.paint(g2);
            } finally {
                g2.dispose();
            }
        }
    }
}
