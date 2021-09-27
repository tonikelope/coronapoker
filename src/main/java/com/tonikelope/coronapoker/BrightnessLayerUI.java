/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.plaf.LayerUI;

public class BrightnessLayerUI extends LayerUI<JComponent> {

    private float brightness = 0f;

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getBrightness() {
        return brightness;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);

        if (getBrightness() > 0f) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(0, 0, 0, getBrightness()));
            g2d.fillRect(0, 0, c.getWidth(), c.getHeight());
            g2d.dispose();
        }
    }

}
