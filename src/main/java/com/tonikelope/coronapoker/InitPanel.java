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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author tonikelope
 */
public class InitPanel extends javax.swing.JLayeredPane {

    protected volatile TexturePaint tp = null;
    protected volatile boolean invalidate = false;
    protected final Object paint_lock = new Object();

    /**
     * Creates new form InitPanel
     */
    public InitPanel() {
        BufferedImage tile = null;
        if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

            try {
                tile = Helpers.toBufferedImage(Init.I1);

            } catch (Exception ex) {
                Logger.getLogger(InitPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            try {

                tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg"));

            } catch (Exception ex) {

                try {
                    tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_verde.jpg"));
                } catch (IOException ex1) {
                    Logger.getLogger(InitPanel.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }

        Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
        tp = new TexturePaint(tile, tr);

        Helpers.GUIRunAndWait(this::initComponents);

        addComponentListener(new ComponentResizeEndListener() {
            @Override
            public void resizeTimedOut() {

                if (GameFrame.COLOR_TAPETE.endsWith("*")) {
                    invalidate = true;

                    revalidate();
                    repaint();

                }
            }
        });
    }

    public void refresh() {

        this.invalidate = true;

        Helpers.GUIRun(() -> {
            revalidate();
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {

        boolean ok = false;

        do {

            try {
                super.paintComponent(g);

                if (invalidate || tp == null) {

                    Helpers.threadRun(() -> {
                        synchronized (paint_lock) {
                            BufferedImage tile = null;
                            if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {
                                try {
                                    tile = Helpers.toBufferedImage(Init.I1.getScaledInstance(getWidth(), getHeight(), Image.SCALE_SMOOTH));
                                } catch (Exception ex) {
                                    Logger.getLogger(InitPanel.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                try {

                                    tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg"));

                                } catch (Exception ex) {

                                    try {
                                        tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_verde.jpg"));
                                    } catch (IOException ex1) {
                                        Logger.getLogger(InitPanel.class.getName()).log(Level.SEVERE, null, ex1);
                                    }
                                }
                            }
                            Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
                            tp = new TexturePaint(tile, tr);
                            invalidate = false;
                            Helpers.GUIRun(() -> {
                                revalidate();
                                repaint();
                            });
                        }
                    });

                    if (tp != null) {

                        Graphics2D g2d = (Graphics2D) g.create();

                        g2d.setPaint(tp);

                        g2d.fill(getBounds());

                        g2d.dispose();
                    }

                    ok = true;

                } else if (tp != null) {

                    Graphics2D g2d = (Graphics2D) g.create();

                    g2d.setPaint(tp);

                    g2d.fill(getBounds());

                    g2d.dispose();

                    ok = true;
                }

            } catch (Exception ex) {
                Logger.getLogger(InitPanel.class.getName()).log(Level.SEVERE, null, ex);
            }

        } while (!ok);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 700, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 500, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
