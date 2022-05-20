/*
 * Copyright (C) 2020 tonikelope
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

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 *
 * @author tonikelope
 */
public abstract class TablePanel extends javax.swing.JLayeredPane implements ZoomableInterface {

    protected volatile TexturePaint tp = null;

    protected volatile RemotePlayer[] remotePlayers;

    protected volatile Player[] players;

    protected volatile ZoomableInterface[] zoomables;

    protected final JLabel central_label = new JLabel();

    protected final TapeteFastButtons fastbuttons = new TapeteFastButtons();

    private volatile Long central_label_thread = null;

    protected volatile boolean invalidate = false;

    protected final Object paint_lock = new Object();

    public RemotePlayer[] getRemotePlayers() {
        return remotePlayers;
    }

    public Player[] getPlayers() {
        return players;
    }

    abstract public CommunityCardsPanel getCommunityCards();

    abstract public LocalPlayer getLocalPlayer();

    /**
     * Creates new form Tapete
     */
    public TablePanel() {

        BufferedImage tile = null;
        if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

            try {
                tile = Helpers.toBufferedImage(Init.I1);

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            try {

                tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg"));

            } catch (Exception ex) {

                try {
                    tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_verde.jpg"));
                } catch (IOException ex1) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }

        Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
        tp = new TexturePaint(tile, tr);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                add(fastbuttons, JLayeredPane.POPUP_LAYER);

                fastbuttons.setSize(fastbuttons.getPref_size());

                central_label.setDoubleBuffered(true);

                central_label.setFocusable(false);

                central_label.setCursor(new Cursor(Cursor.HAND_CURSOR));

                add(central_label, JLayeredPane.POPUP_LAYER);

                addComponentListener(new ComponentResizeEndListener() {

                    @Override
                    public void resizeTimedOut() {

                        if (GameFrame.AUTO_ZOOM) {
                            Helpers.threadRun(new Runnable() {
                                @Override
                                public void run() {
                                    autoZoom(false);
                                }
                            });
                        }

                        if (GameFrame.COLOR_TAPETE.endsWith("*")) {
                            invalidate = true;

                            revalidate();
                            repaint();

                        }

                        fastbuttons.setLocation(0, (int) (getHeight() - fastbuttons.getSize().getHeight()));

                    }
                });
            }
        });
    }

    public void showCentralImage(ImageIcon icon, long timeout) {

        int old_priority = Thread.currentThread().getPriority();

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        central_label_thread = Thread.currentThread().getId();

        synchronized (getCentral_label()) {
            getCentral_label().notify();
        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                ImageIcon final_icon = icon;

                if (GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL) {

                    int w = icon.getIconWidth();

                    int h = icon.getIconHeight();

                    final_icon = new ImageIcon(icon.getImage().getScaledInstance(Math.round(w * (1f + (GameFrame.ZOOM_LEVEL - GameFrame.DEFAULT_ZOOM_LEVEL) * GameFrame.ZOOM_STEP)), Math.round(h * (1f + (GameFrame.ZOOM_LEVEL - GameFrame.DEFAULT_ZOOM_LEVEL) * GameFrame.ZOOM_STEP)), Image.SCALE_DEFAULT));
                }

                final_icon.getImage().flush();
                getCentral_label().setIcon(final_icon);
                getCentral_label().setSize(getCentral_label().getIcon().getIconWidth(), getCentral_label().getIcon().getIconHeight());
                int pos_x = Math.round((getWidth() - getCentral_label().getIcon().getIconWidth()) / 2);
                int pos_y = Math.round((getHeight() - getCentral_label().getIcon().getIconHeight()) / 2);
                getCentral_label().setLocation(pos_x, pos_y);

                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    getCentral_label().setVisible(true);
                }

            }
        });

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().getId() == central_label_thread) {
            synchronized (getCentral_label()) {

                try {
                    getCentral_label().wait(timeout);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Helpers.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

            if (Thread.currentThread().getId() == central_label_thread) {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        getCentral_label().setVisible(false);

                    }
                });
            }
        }

        Thread.currentThread().setPriority(old_priority);

    }

    public JLabel getCentral_label() {
        return central_label;
    }

    public void hideALL() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                for (Player p : players) {
                    ((JPanel) p).setVisible(false);
                }

                getCommunityCards().setVisible(false);

                central_label.setVisible(false);

            }
        });

    }

    public void showALL() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                for (Player p : players) {
                    ((JPanel) p).setVisible(true);
                }

                getCommunityCards().setVisible(true);
            }
        });

    }

    public void refresh() {

        Audio.playWavResource("misc/mat.wav");

        this.invalidate = true;

        Helpers.GUIRun(new Runnable() {
            public void run() {

                revalidate();
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {

        boolean ok = false;

        do {

            try {
                super.paintComponent(g);

                if (invalidate || tp == null) {

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            synchronized (paint_lock) {

                                BufferedImage tile = null;

                                if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {
                                    try {
                                        tile = Helpers.toBufferedImage(Init.I1.getScaledInstance(getWidth(), getHeight(), Image.SCALE_SMOOTH));
                                    } catch (Exception ex) {
                                        Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                } else {
                                    try {

                                        tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg"));

                                    } catch (Exception ex) {

                                        try {
                                            tile = ImageIO.read(getClass().getResourceAsStream("/images/tapete_verde.jpg"));
                                        } catch (IOException ex1) {
                                            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                                        }
                                    }
                                }

                                Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());

                                tp = new TexturePaint(tile, tr);

                                invalidate = false;

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        revalidate();
                                        repaint();

                                    }
                                });

                            }

                        }
                    });

                    if (tp != null) {

                        Graphics2D g2 = (Graphics2D) g;

                        g2.setPaint(tp);

                        g2.fill(getBounds());
                    }

                    ok = true;

                } else if (tp != null) {

                    Graphics2D g2 = (Graphics2D) g;

                    g2.setPaint(tp);

                    g2.fill(getBounds());

                    ok = true;
                }

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
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

        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                formMouseEntered(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseEntered
        // TODO add your handling code here:
        if (fastbuttons.areButtonsVisible()) {
            fastbuttons.hideButtons();
        }
    }//GEN-LAST:event_formMouseEntered

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomeable : zoomables) {
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoomeable.zoom(factor, mynotifier);

                }
            });
        }

        while (mynotifier.size() < zoomables.length) {

            synchronized (mynotifier) {

                try {
                    mynotifier.wait(1000);

                } catch (InterruptedException ex) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }

    }

    public TapeteFastButtons getFastbuttons() {
        return fastbuttons;
    }

    public synchronized void autoZoom(boolean reset) {

        for (Player jugador : getPlayers()) {

            double tapeteBottom = getLocationOnScreen().getY() + getHeight();
            double tapeteRight = getLocationOnScreen().getX() + getWidth();
            double playerBottom = ((JPanel) jugador).getLocationOnScreen().getY() + ((JPanel) jugador).getHeight();
            double playerRight = ((JPanel) jugador).getLocationOnScreen().getX() + ((JPanel) jugador).getWidth();

            if (playerBottom > tapeteBottom || playerRight > tapeteRight) {

                if (reset && (GameFrame.ZOOM_LEVEL != GameFrame.DEFAULT_ZOOM_LEVEL)) {

                    //RESET ZOOM
                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {
                            GameFrame.getInstance().getZoom_menu_reset().doClick();
                        }
                    });

                    while (!GameFrame.getInstance().getZoom_menu().isEnabled()) {
                        synchronized (GameFrame.getInstance().getZoom_menu()) {

                            try {
                                GameFrame.getInstance().getZoom_menu().wait(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    tapeteBottom = getLocationOnScreen().getY() + getHeight();
                    tapeteRight = getLocationOnScreen().getX() + getWidth();
                    playerBottom = ((JPanel) jugador).getLocationOnScreen().getY() + ((JPanel) jugador).getHeight();
                    playerRight = ((JPanel) jugador).getLocationOnScreen().getX() + ((JPanel) jugador).getWidth();

                }

                while (playerBottom > tapeteBottom || playerRight > tapeteRight) {

                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {
                            GameFrame.getInstance().getZoom_menu_out().doClick();
                        }
                    });

                    while (!GameFrame.getInstance().getZoom_menu().isEnabled()) {
                        synchronized (GameFrame.getInstance().getZoom_menu()) {

                            try {
                                GameFrame.getInstance().getZoom_menu().wait(1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    tapeteBottom = getLocationOnScreen().getY() + getHeight();
                    tapeteRight = getLocationOnScreen().getX() + getWidth();
                    playerBottom = ((JPanel) jugador).getLocationOnScreen().getY() + ((JPanel) jugador).getHeight();
                    playerRight = ((JPanel) jugador).getLocationOnScreen().getX() + ((JPanel) jugador).getWidth();

                    Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);

                }
            }

        }

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
