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
import javax.swing.JComponent;
import javax.swing.plaf.LayerUI;

public class BrightnessLayerUI extends LayerUI<JComponent> {

    public static final float LIGHTS_OFF_BRIGHTNESS = 0.40f;
    private float brightness = 0f;

    public void lightsOFF() {

        setBrightness(BrightnessLayerUI.LIGHTS_OFF_BRIGHTNESS);
    }

    public void lightsON() {

        setBrightness(0f);
    }

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
