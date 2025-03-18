/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tonikelope.coronapoker;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
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
        revalidate();
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dibujar el fondo redondeado si el componente tiene un color de fondo
        if (isOpaque()) {
            g2d.setColor(getBackground());
            g2d.fill(new RoundRectangle2D.Double(
                    0, 0,
                    getWidth(),
                    getHeight(),
                    arc * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP),
                    arc * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)
            ));
        }

        g2d.dispose();
    }
}
