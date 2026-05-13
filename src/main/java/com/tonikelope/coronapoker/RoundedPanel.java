/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tonikelope.coronapoker;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 *
 * @author tonikelope
 */
public class RoundedPanel extends JPanel {

    public static final int DEFAULT_ARC = 20;
    private final int arc;

    public RoundedPanel() {
        super();
        this.arc = DEFAULT_ARC;
    }

    public RoundedPanel(int arc) {
        super();
        this.arc = arc;
    }

    @Override
    protected void paintComponent(Graphics g) {

        if (isOpaque()) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                float effective_arc = arc * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), Math.round(effective_arc), Math.round(effective_arc));
            } finally {
                g2d.dispose();
            }
            // Skip super.paintComponent: the rounded fill above replaces the default
            // rectangular background; calling super would draw a square fill behind it.
        } else {
            super.paintComponent(g);
        }
    }
}
