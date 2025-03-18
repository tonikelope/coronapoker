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

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
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

    protected final CyclicBarrier central_label_barrier = new CyclicBarrier(2);

    protected final GifLabel central_label = new GifLabel();

    protected final TapeteFastButtons fastbuttons = new TapeteFastButtons();

    protected volatile Long central_label_thread = null;

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

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            add(fastbuttons, JLayeredPane.POPUP_LAYER);
            fastbuttons.setSize(fastbuttons.getPref_size());
            central_label.setDoubleBuffered(true);
            central_label.setFocusable(false);
            central_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            central_label.setBarrier(central_label_barrier);
            add(central_label, JLayeredPane.POPUP_LAYER);
            addComponentListener(new ComponentResizeEndListener() {
                @Override
                public void resizeTimedOut() {
                    if (GameFrame.AUTO_ZOOM) {
                        Helpers.threadRun(() -> {
                            autoZoom(false);
                        });
                    }
                    if (GameFrame.COLOR_TAPETE.endsWith("*")) {
                        invalidate = true;

                        revalidate();
                        repaint();

                    }

                    fastbuttons.setLocation(0, (int) (getHeight() - fastbuttons.getSize().getHeight()));

                    if (Helpers.OSValidator.isWindows() && GameFrame.getInstance().isFull_screen() && GameFrame.getInstance() != null && GameFrame.getInstance().getExtendedState() != JFrame.MAXIMIZED_BOTH) {

                        GameFrame.getInstance().getFull_screen_menu().setEnabled(false);

                        GameFrame.getInstance().setVisible(false);

                        GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);

                        GameFrame.getInstance().setVisible(true);

                        GameFrame.getInstance().getFull_screen_menu().setEnabled(true);
                    }

                }
            });
        });
    }

    public void showCentralImage(ImageIcon icon, int frames, int delay_end) {

        showCentralImage(icon, frames, delay_end, true, null, 0, 0);

    }

    public void showCentralImage(ImageIcon icon, int frames, int delay_end, boolean center, String audio, int audio_frame_start, int audio_frame_end) {
        central_label_thread = Thread.currentThread().getId();

        try {
            central_label_barrier.reset();
        } catch (Exception ex) {
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
        }

        Helpers.GUIRunAndWait(() -> {
            getCentral_label().setSize(icon.getIconWidth(), icon.getIconHeight());

            if (center) {
                int pos_x = Math.round((getWidth() - icon.getIconWidth()) / 2);
                int pos_y = Math.round((getHeight() - icon.getIconHeight()) / 2);
                getCentral_label().setLocation(pos_x, pos_y);
            }

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                icon.getImage().flush();
                getCentral_label().setIcon(icon, frames);
                getCentral_label().addAudio(audio, audio_frame_start, audio_frame_end);
                getCentral_label().setVisible(true);
                getCentral_label().revalidate();
                getCentral_label().repaint();
            }
        });
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().getId() == central_label_thread) {
            try {
                central_label_barrier.await();
            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (delay_end > 0) {
                Helpers.parkThreadMillis(delay_end);
            }
            if (Thread.currentThread().getId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setVisible(false);
                });
            }
        }
    }

    public GifLabel getCentral_label() {
        return central_label;
    }

    public void hideALL() {

        Helpers.GUIRun(() -> {
            for (Player p : players) {
                ((JPanel) p).setVisible(false);
            }

            getCommunityCards().setVisible(false);

            central_label.setVisible(false);
        });

    }

    public void showALL() {

        Helpers.GUIRun(() -> {
            for (Player p : players) {
                ((JPanel) p).setVisible(true);
            }

            getCommunityCards().setVisible(true);
        });

    }

    public void refresh() {

        Audio.playWavResource("misc/mat.wav");

        this.invalidate = true;

        Helpers.GUIRunAndWait(() -> {
            GameFrame.getInstance().revalidate();
            GameFrame.getInstance().repaint();
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
                            Helpers.GUIRun(() -> {
                                revalidate();
                                repaint();
                            });
                        }
                    });

                    if (tp != null) {

                        Graphics2D g2d = (Graphics2D) g;

                        g2d.setPaint(tp);

                        g2d.fill(getBounds());
                    }

                    ok = true;

                } else if (tp != null) {

                    Graphics2D g2d = (Graphics2D) g;

                    g2d.setPaint(tp);

                    g2d.fill(getBounds());

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
            Helpers.threadRun(() -> {
                zoomeable.zoom(factor, mynotifier);
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
                    Helpers.GUIRunAndWait(GameFrame.getInstance().getZoom_menu_reset()::doClick);

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

                    Helpers.GUIRunAndWait(GameFrame.getInstance().getZoom_menu_out()::doClick);

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
