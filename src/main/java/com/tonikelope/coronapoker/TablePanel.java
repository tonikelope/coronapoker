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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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

    // Tick del reproductor pre-decodificado (showCentralFrames). No marca el
    // ritmo de la animación (eso lo hace el indexado por nanoTime), solo la
    // frecuencia con la que se reevalúa qué frame toca.
    public static final int PRE_RENDERED_TICK_MS = 10;

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

                    if (GameFrame.getInstance() != null && GameFrame.getInstance().isFull_screen()) {
                        GameFrame.getInstance().setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }

                }
            });
        });
    }

    public void showCentralImage(ImageIcon icon, int frames, int delay_end, boolean center, String audio, int audio_frame_start, int audio_frame_end) {
        central_label_thread = Thread.currentThread().threadId();

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
        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().threadId() == central_label_thread) {
            try {
                central_label_barrier.await();
            } catch (InterruptedException | java.util.concurrent.BrokenBarrierException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label barrier", ex);
            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (delay_end > 0) {
                Helpers.parkThreadMillis(delay_end);
            }
            if (Thread.currentThread().threadId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setVisible(false);
                });
            }
        }
    }

    // Reproduce un GIF pre-decodificado sobre la central_label con indexado por
    // reloj (catch-up): el frame visible se elige por tiempo transcurrido con
    // nanoTime, así la duración total es siempre la nominal del GIF aunque los
    // ticks del timer lleguen tarde (granularidad del timer de Windows). Mismo
    // contrato que showCentralImage: bloquea al llamante hasta el fin de la
    // animación (con el mismo timeout) y respeta fin_de_la_transmision y el
    // takeover de central_label_thread.
    public void showCentralFrames(PreRenderedGif anim, int display_w, int display_h, int delay_end, String audio) {

        showCentralFrames(anim, display_w, display_h, delay_end, audio, null, null);
    }

    // on_show corre dentro del MISMO runnable del EDT que hace visible el
    // primer frame: el llamante puede ocultar ahí la carta tapada de debajo y
    // el relevo carta→GIF se pinta en un solo paint (con el hide en un evento
    // EDT separado el estado intermedio con el hueco vacío llegaba a pintarse
    // a veces — parpadeo sutil e intermitente). before_hide corre en el hilo
    // llamante tras el último frame y ANTES de la pausa delay_end, solo si
    // este hilo sigue siendo el dueño del label: el llamante puede destapar
    // ahí la carta estática debajo del GIF (que aún muestra su último frame
    // durante toda la pausa) y el relevo GIF→carta tampoco pinta nunca el
    // hueco vacío.
    public void showCentralFrames(PreRenderedGif anim, int display_w, int display_h, int delay_end, String audio, Runnable on_show, Runnable before_hide) {

        central_label_thread = Thread.currentThread().threadId();

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            getCentral_label().setSize(display_w, display_h);

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                getCentral_label().setIcon(null);
                getCentral_label().setFrameOverride(anim.getFrame(0));
                getCentral_label().setVisible(true);

                if (on_show != null) {
                    on_show.run();
                }

                if (audio != null) {
                    Audio.playWavResource(audio);
                }

                final long t0 = System.nanoTime();
                final int last_frame = anim.getFrameCount() - 1;
                final int[] painted = {0};

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);

                player_holder[0] = player;

                player.addActionListener(e -> {

                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;

                    int idx = anim.frameAt(elapsed);

                    if (idx != painted[0]) {
                        painted[0] = idx;
                        getCentral_label().setFrameOverride(anim.getFrame(idx));
                    }

                    if (idx == last_frame || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        finished.countDown();
                    }
                });

                player.start();

            } else {
                finished.countDown();
            }
        });

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().threadId() == central_label_thread) {

            try {
                finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label pre-rendered playback", ex);
            }

            // Antes de la pausa a propósito: lo que haga el llamante (destapar
            // la carta estática) ocurre tapado por el último frame durante
            // delay_end y no alarga la animación. Blindado para que un fallo
            // del hook jamás deje el label visible para siempre.
            if (before_hide != null && Thread.currentThread().threadId() == central_label_thread) {
                try {
                    before_hide.run();
                } catch (Exception ex) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            if (delay_end > 0) {
                Helpers.parkThreadMillis(delay_end);
            }

            if (Thread.currentThread().threadId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setFrameOverride(null);
                    getCentral_label().setVisible(false);
                });
            }
        }

        // Cinturón y tirantes: para ESTE player pase lo que pase con la espera
        // (timeout del latch, fin de transmisión, takeover). El Timer ya se
        // auto-termina al llegar al último frame; esto solo cierra los caminos
        // exóticos sin tocar el player de un posible nuevo dueño del label.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
    }

    // Reproduce los GIFs de giro de una o varias cartas A LA VEZ sobre
    // overlays efímeros en POPUP_LAYER (uno por carta, centrado sobre ella),
    // con el mismo motor catch-up y los mismos relevos sin hueco que
    // showCentralFrames: cada tapada se oculta en el MISMO evento EDT que
    // muestra su primer frame y cada carta se destapa síncronamente DEBAJO de
    // su último frame antes de retirar los overlays. Bloquea al llamante
    // (NUNCA llamar desde el EDT) hasta que todas las animaciones terminan
    // más delay_end — para destapes secuenciales (una carta aterrizada del
    // todo antes de que gire la siguiente) el llamante encadena llamadas de
    // una sola carta. No toca central_label ni su takeover: los overlays se
    // crean y se retiran aquí mismo.
    public void playCardFlipOverlays(Card[] cartas, PreRenderedGif[] anims, int[] dws, int[] dhs, int delay_end, String audio) {

        final GifLabel[] overlays = new GifLabel[cartas.length];

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            try {
                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                    for (int i = 0; i < cartas.length; i++) {

                        GifLabel overlay = new GifLabel();
                        overlay.setFocusable(false);
                        overlay.setSize(dws[i], dhs[i]);

                        int x = (int) ((int) ((cartas[i].getLocationOnScreen().getX() + Math.round(cartas[i].getWidth() / 2))
                                - Math.round(dws[i] / 2))
                                - getLocationOnScreen().getX());

                        int y = (int) ((int) ((cartas[i].getLocationOnScreen().getY() + Math.round(cartas[i].getHeight() / 2))
                                - Math.round(dhs[i] / 2))
                                - getLocationOnScreen().getY());

                        overlay.setLocation(x, y);
                        overlay.setFrameOverride(anims[i].getFrame(0));

                        add(overlay, JLayeredPane.POPUP_LAYER);

                        overlay.setVisible(true);

                        overlays[i] = overlay;

                        // Mismo evento EDT que muestra el primer frame: el relevo
                        // carta→GIF se pinta de una pieza.
                        cartas[i].setVisibleCard(false);
                    }

                    if (audio != null) {
                        Audio.playWavResource(audio);
                    }

                    final long t0 = System.nanoTime();
                    final int[] painted = new int[cartas.length];

                    final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);

                    player_holder[0] = player;

                    player.addActionListener(e -> {

                        long elapsed = (System.nanoTime() - t0) / 1_000_000L;

                        boolean all_done = true;

                        for (int i = 0; i < anims.length; i++) {

                            int idx = anims[i].frameAt(elapsed);

                            if (idx != painted[i]) {
                                painted[i] = idx;
                                overlays[i].setFrameOverride(anims[i].getFrame(idx));
                            }

                            all_done = all_done && (idx == anims[i].getFrameCount() - 1);
                        }

                        if (all_done || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                            player.stop();
                            finished.countDown();
                        }
                    });

                    player.start();

                } else {
                    finished.countDown();
                }
            } catch (Exception ex) {
                // P.ej. IllegalComponentStateException si una carta dejó de
                // estar en pantalla justo ahora: limpiar lo añadido y soltar
                // al llamante, que destapa en seco con el destaparSync de abajo.
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                for (GifLabel overlay : overlays) {
                    if (overlay != null) {
                        remove(overlay);
                    }
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "card flip overlays playback", ex);
        }

        // Igual que en showCentralFrames: el destape síncrono ocurre tapado por
        // el último frame durante delay_end, así el relevo GIF→carta nunca
        // pinta el hueco vacío.
        for (Card carta : cartas) {
            carta.destaparSync();
        }

        if (delay_end > 0) {
            Helpers.parkThreadMillis(delay_end);
        }

        Helpers.GUIRunAndWait(() -> {
            for (GifLabel overlay : overlays) {
                if (overlay != null) {
                    overlay.setVisible(false);
                    remove(overlay);
                }
            }
            revalidate();
            repaint();
        });

        // Cinturón y tirantes (mismo patrón que showCentralFrames): parar el
        // timer pase lo que pase con la espera.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
    }

    // Variante en bucle de showCentralFrames para el GIF de barajado: repite la
    // animación (centrada, con el audio re-disparado en cada ciclo y cortado en
    // audio_stop_frame, mismo contrato que addAudio(1, stop)) hasta que el
    // predicado caiga. El predicado solo se consulta al llegar al último frame,
    // así que siempre se reproduce al menos un ciclo completo, igual que el
    // do-while legacy sobre showCentralImage. Bloquea al llamante hasta el fin
    // del bucle y respeta fin_de_la_transmision y el takeover de
    // central_label_thread.
    public void showCentralFramesLoop(PreRenderedGif anim, int display_w, int display_h, String audio, int audio_stop_frame, java.util.function.BooleanSupplier keep_looping) {

        central_label_thread = Thread.currentThread().threadId();

        final CountDownLatch finished = new CountDownLatch(1);

        final javax.swing.Timer[] player_holder = new javax.swing.Timer[1];

        Helpers.GUIRunAndWait(() -> {

            getCentral_label().setSize(display_w, display_h);
            getCentral_label().setLocation(Math.round((getWidth() - display_w) / 2), Math.round((getHeight() - display_h) / 2));

            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {

                getCentral_label().setIcon(null);
                getCentral_label().setFrameOverride(anim.getFrame(0));
                getCentral_label().setVisible(true);

                if (audio != null) {
                    Audio.playWavResource(audio);
                }

                final long[] t0 = {System.nanoTime()};
                final long total_ms = anim.getTotalMs();
                final int[] painted = {0};
                final boolean[] audio_on = {audio != null};

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);

                player_holder[0] = player;

                player.addActionListener(e -> {

                    if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        if (audio_on[0]) {
                            audio_on[0] = false;
                            Audio.stopWavResource(audio);
                        }
                        player.stop();
                        finished.countDown();
                        return;
                    }

                    long elapsed = (System.nanoTime() - t0[0]) / 1_000_000L;

                    int idx = anim.frameAt(elapsed);

                    // Ventana de audio 1..audio_stop_frame (1-based) de cada ciclo
                    if (audio_on[0] && idx + 1 >= audio_stop_frame) {
                        audio_on[0] = false;
                        Audio.stopWavResource(audio);
                    }

                    if (idx != painted[0]) {
                        painted[0] = idx;
                        getCentral_label().setFrameOverride(anim.getFrame(idx));
                    }

                    // Fin de ciclo cuando el ÚLTIMO frame ha consumido también su
                    // delay (no al entrar en él), para que el ciclo dure siempre
                    // el total nominal del GIF.
                    if (elapsed >= total_ms) {

                        if (keep_looping.getAsBoolean()) {
                            // Rebobinado exacto: nuevo origen de tiempos y audio
                            // re-disparado, sin gap entre ciclos.
                            t0[0] = System.nanoTime();
                            painted[0] = 0;
                            getCentral_label().setFrameOverride(anim.getFrame(0));
                            if (audio != null) {
                                if (audio_on[0]) {
                                    Audio.stopWavResource(audio);
                                }
                                audio_on[0] = true;
                                Audio.playWavResource(audio);
                            }
                        } else {
                            player.stop();
                            finished.countDown();
                        }
                    }
                });

                player.start();

            } else {
                finished.countDown();
            }
        });

        if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && Thread.currentThread().threadId() == central_label_thread) {

            try {
                // El bucle dura lo que dure el predicado (la cascada SRA puede ir
                // para largo), así que la espera normal es indefinida: el único
                // exit normal es el countDown del player. El timeout por ronda es
                // solo defensivo: si el predicado ya cayó (o hay fin de
                // transmisión) y el player no ha contado el latch tras una ronda
                // ENTERA adicional, es que ya no hay EDT vivo que lo cuente
                // (shutdown) y seguir esperando bloquearía el hilo del crupier.
                boolean stopping_observed = false;

                while (!finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS)) {

                    boolean stopping = !keep_looping.getAsBoolean() || GameFrame.getInstance().getCrupier().isFin_de_la_transmision();

                    if (stopping && stopping_observed) {
                        break;
                    }

                    stopping_observed = stopping;
                }
            } catch (InterruptedException ex) {
                Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                        "central label pre-rendered loop playback", ex);
            }

            if (Thread.currentThread().threadId() == central_label_thread) {

                Helpers.GUIRunAndWait(() -> {
                    getCentral_label().setFrameOverride(null);
                    getCentral_label().setVisible(false);
                });
            }
        }

        // Mismo cinturón y tirantes que showCentralFrames para los caminos
        // exóticos (timeout defensivo, takeover): parar ESTE player sin tocar
        // el de un posible nuevo dueño del label.
        Helpers.GUIRun(() -> {
            if (player_holder[0] != null) {
                player_holder[0].stop();
            }
        });
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
                                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                // try-with-resources: ImageIO.read(InputStream) NO cierra el
                                // stream (contrato JDK). Cada cambio de tapete dejaba colgado
                                // el handle del JAR resource hasta GC.
                                try (java.io.InputStream is = getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg")) {

                                    tile = ImageIO.read(is);

                                } catch (Exception ex) {

                                    try (java.io.InputStream isf = getClass().getResourceAsStream("/images/tapete_verde.jpg")) {
                                        tile = ImageIO.read(isf);
                                    } catch (IOException ex1) {
                                        Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                                    }
                                }
                            }
                            // Sprint deferred 🟡-32: snapshot del tile anterior para
                            // flush DIFERIDO post-repaint. El EDT lee tp sin
                            // sincronización (no toma paint_lock), así que si
                            // hiciéramos flush() aquí mientras paintComponent del EDT
                            // pinta con el mismo tp, render inconsistente. invokeLater
                            // garantiza que el flush corra después de que la pintura
                            // con el tp viejo haya terminado.
                            final java.awt.Image oldImage = (tp != null) ? tp.getImage() : null;
                            Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
                            tp = new TexturePaint(tile, tr);
                            invalidate = false;
                            Helpers.GUIRun(() -> {

                                revalidate();
                                repaint();
                                if (oldImage != null) {
                                    javax.swing.SwingUtilities.invokeLater(oldImage::flush);
                                }
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

            notifier.add(Thread.currentThread().threadId());

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

                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);

                }
            }

        }

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
