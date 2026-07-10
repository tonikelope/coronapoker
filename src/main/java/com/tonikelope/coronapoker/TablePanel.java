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

    // Tapete de IMAGEN ÚNICA (sufijo "*"): un JPG grande que se estira a todo el
    // tablero. Se guarda la imagen sin escalar y se pinta con drawImage(...,0,0,w,h)
    // en cada paint (ver paintComponent). NO se usa TexturePaint para esto: un tile
    // del tamaño del panel rinde distinto una franja recortada que el pintado
    // completo (costura/"deformación" por donde cruzan las fichas/cartas voladoras),
    // mientras que drawImage con rectángulo de destino mapea siempre la fuente
    // entera a (0,0)-(w,h) y el clip solo limita qué píxeles se escriben.
    protected volatile BufferedImage secret_bg = null;

    protected volatile RemotePlayer[] remotePlayers;

    protected volatile Player[] players;

    protected volatile ZoomableInterface[] zoomables;

    protected final CyclicBarrier central_label_barrier = new CyclicBarrier(2);

    protected final GifLabel central_label = new GifLabel();

    // Rótulo "BARAJANDO" que se muestra centrado donde iría el gif de barajado cuando
    // ESE gif NO se reproduce (la baraja no trae shuffle.gif, o las animaciones están
    // desactivadas): mismas letras que el mensaje gigante de la pantalla final (relleno
    // blanco, borde negro), con el ancho del panel de comunitarias. Vive en su propia
    // capa por encima de la mesa y solo está visible durante el barajado sin gif.
    protected final ShufflingTextLabel shuffling_label = new ShufflingTextLabel();

    // Overlay opcional sobre las comunitarias: muestra en grande el coste de igualar
    // del jugador local — cuánto tendrá que poner cuando le toque. Texto con relleno
    // negro semitransparente y halo blanco para leerse sobre cualquier fondo (cartas
    // claras, dorsos oscuros, tapete) sin tapar. Se actualiza en vivo según suben las
    // apuestas.
    protected final CallCostOverlayLabel call_cost_label = new CallCostOverlayLabel();

    // Overlays de coste por jugador para la ronda del RIVER: cuando ya no quedan
    // comunitarias por destapar, el coste de igualar pasa a mostrarse sobre las hole
    // cards TAPADAS de cada RemotePlayer que sigue en el bote (nunca sobre el local,
    // que ve sus cartas) — el coste de igualar es ahí el coste de "destapar" las manos
    // rivales en el showdown. Viven en DRAG_LAYER (por encima de todo el tablero) y se
    // gestionan solo en el EDT. La clave es el RemotePlayer (objeto estable por asiento).
    private final java.util.Map<RemotePlayer, CallCostOverlayLabel> player_call_cost_labels = new java.util.HashMap<>();
    private final java.util.Set<RemotePlayer> player_call_overlay_listeners = new java.util.HashSet<>();

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

        if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

            // Tapete de imagen única: se guarda sin escalar y se pinta estirado a
            // todo el panel con drawImage (ver paintComponent). NO se usa
            // TexturePaint para evitar las costuras al repintar franjas parciales
            // bajo las animaciones voladoras.
            try {
                secret_bg = Helpers.toBufferedImage(Init.I1);

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            BufferedImage tile = null;
            // try-with-resources: ImageIO.read(InputStream) NO cierra el stream
            // (contrato JDK), así que el handle del JAR resource quedaba colgado
            // hasta el GC. Mismo arreglo que ya se aplicó en el cambio de tapete
            // en vivo (paintComponent, más abajo en este fichero).
            try (java.io.InputStream is = getClass().getResourceAsStream("/images/tapete_" + GameFrame.COLOR_TAPETE + ".jpg")) {

                tile = ImageIO.read(is);

            } catch (Exception ex) {

                try (java.io.InputStream isf = getClass().getResourceAsStream("/images/tapete_verde.jpg")) {
                    tile = ImageIO.read(isf);
                } catch (IOException ex1) {
                    Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }

            Rectangle2D tr = new Rectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight());
            tp = new TexturePaint(tile, tr);
        }

        Helpers.GUIRunAndWait(() -> {
            initComponents();
            add(fastbuttons, JLayeredPane.POPUP_LAYER);
            fastbuttons.setSize(fastbuttons.getPref_size());
            central_label.setFocusable(false);
            central_label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            central_label.setBarrier(central_label_barrier);
            add(central_label, JLayeredPane.POPUP_LAYER);

            // Rótulo "BARAJANDO" (fallback sin gif de barajado): misma capa que el gif
            // central, oculto salvo durante el barajado sin animación.
            shuffling_label.setFocusable(false);
            shuffling_label.setVisible(false);
            add(shuffling_label, JLayeredPane.POPUP_LAYER);

            // Overlay de coste de igualar: por encima de las comunitarias (capa
            // PALETTE, debajo del shuffle/flying). Pinta su propio texto con halo
            // (centrado), así que no necesita alignment ni foreground.
            call_cost_label.setFocusable(false);
            call_cost_label.setOpaque(false);
            call_cost_label.setVisible(false);
            add(call_cost_label, JLayeredPane.PALETTE_LAYER);
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

                    // El overlay de coste de igualar está posicionado en absoluto:
                    // tras un resize/zoom hay que recolocarlo sobre las comunitarias
                    // (o sobre las hole cards rivales, en la ronda del river).
                    if (call_cost_label.isVisible()) {
                        layoutCallCostOverlay();
                        call_cost_label.repaint();
                    }
                    relayoutPlayerCallCostOverlaysIfVisible();

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
                        // Clip pre-abierto y reutilizado (uncover.wav se precarga
                        // al arranque): el sonido del giro arranca instantáneo y
                        // sincronizado con el primer frame del overlay, sin un open
                        // de línea por destape que llegue tarde. Off-EDT porque
                        // playPreloadedWav puede resolver una precarga perezosa.
                        Helpers.threadRun(() -> Audio.playPreloadedWav(audio));
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

    // Animación de reparto: una carta TAPADA viaja desde el asiento del DEALER
    // de la mano (carta-ancla origin) hasta el destino, ROTADA según el ángulo
    // origen→destino, y describiendo un arco suave con easeOut (arranque rápido,
    // frenada suave). Si origin es null parte del centro de la mesa (retrocompat).
    // Al aterrizar ejecuta onLand (que sienta la carta tapada en el asiento) y,
    // tras un breve dwell de relevo, retira la viajera SIN hueco: la viajera
    // muestra el MISMO dorso (Card.getBackImage) y aterriza recta y centrada
    // sobre el asiento, así el relevo viajera→carta es pixel-idéntico.
    //
    // Geometría-agnóstica: lee la posición real de target (y de origin) en
    // pantalla, así sirve para los 9 tableros, con zoom y HiDPI sin tocar nada.
    // Bloquea al llamante (hilo del crupier, NUNCA EDT) hasta el aterrizaje +
    // dwell. Si algo impide la animación (sin dorso, target fuera de pantalla,
    // fin de transmisión) ejecuta onLand en seco y vuelve.
    public void flyCardToSeat(final Card target, final Card origin, final int duration_ms, final String audio, final Runnable onLand) {

        // --- Afinado de la animación (tunables) ---
        // Offset (rad) sumado al ángulo origen→destino (0 = la carta se alinea
        // con la dirección de viaje; +PI/2 alinea el eje largo con el trayecto).
        final double ROT_OFFSET = 0.0;
        // Endereza a vertical al final del viaje para encajar con la carta del
        // asiento (que está recta) y que el relevo no tenga pop de rotación. Si
        // se desea que aterrice girada, poner a false.
        final boolean STRAIGHTEN_ON_LAND = true;
        // Velocidad constante: la duración se deriva de la distancia recorrida,
        // medida en ALTURAS DE CARTA (invariante al zoom), así todas las cartas
        // viajan a la misma velocidad visual sin importar el asiento (parece que
        // el crupier las tira todas con la misma fuerza). Con false se usa
        // duration_ms (duración fija por carta, comportamiento anterior).
        final boolean CONSTANT_SPEED = false;
        final double MS_PER_CARDHEIGHT = 120.0; // ms por cada altura-de-carta de distancia
        final int SPEED_MIN_MS = 120;
        final int SPEED_MAX_MS = 320;

        final ImageIcon back = Card.getBackImage();

        if (target == null || back == null || back.getIconWidth() <= 0
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            if (onLand != null) {
                Helpers.GUIRunAndWait(onLand);
            }
            return;
        }

        final int dw = back.getIconWidth();
        final int dh = back.getIconHeight();
        // Cuadrado que contiene la carta a CUALQUIER ángulo (su diagonal), para
        // no redimensionar la viajera mientras gira.
        final int box = (int) Math.ceil(Math.hypot(dw, dh));

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final FlyingCard[] travelerHolder = new FlyingCard[1];

        Helpers.GUIRunAndWait(() -> {
            try {
                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                // Centros en coordenadas locales del tapete.
                final double toCx = target.getLocationOnScreen().getX() + target.getWidth() / 2.0 - getLocationOnScreen().getX();
                final double toCy = target.getLocationOnScreen().getY() + target.getHeight() / 2.0 - getLocationOnScreen().getY();
                // Origen: asiento del dealer (carta-ancla); si no se da o no
                // está en pantalla (p.ej. dealer retirado), el centro de la
                // mesa (comportamiento anterior).
                final double fromCx, fromCy;
                if (origin != null && origin.isShowing()) {
                    fromCx = origin.getLocationOnScreen().getX() + origin.getWidth() / 2.0 - getLocationOnScreen().getX();
                    fromCy = origin.getLocationOnScreen().getY() + origin.getHeight() / 2.0 - getLocationOnScreen().getY();
                } else {
                    fromCx = getWidth() / 2.0;
                    fromCy = getHeight() / 2.0;
                }

                final double theta = Math.atan2(toCy - fromCy, toCx - fromCx) + ROT_OFFSET;

                // Punto de control del arco: medio del trayecto desplazado
                // perpendicular, con altura acotada.
                final double mx = (fromCx + toCx) / 2.0, my = (fromCy + toCy) / 2.0;
                final double vx = toCx - fromCx, vy = toCy - fromCy;
                final double len = Math.hypot(vx, vy);
                final double arc = Math.min(len * 0.16, dh);
                final double nx = (len > 1) ? -vy / len : 0.0;
                final double ny = (len > 1) ? vx / len : 0.0;
                final double ctrlX = mx + nx * arc;
                final double ctrlY = my + ny * arc;

                // Duración efectiva: con velocidad constante, proporcional a la
                // distancia en alturas-de-carta (acotada); si no, la pasada.
                final int eff_dur = CONSTANT_SPEED
                        ? (int) Math.round(Math.max(SPEED_MIN_MS,
                                Math.min(SPEED_MAX_MS, (len / dh) * MS_PER_CARDHEIGHT)))
                        : duration_ms;

                if (audio != null) {
                    Audio.playWavResource(audio, false);
                }

                final FlyingCard traveler = new FlyingCard(back.getImage(), dw, dh);
                traveler.setSize(box, box);
                traveler.setAngle(theta);
                traveler.setCenter(fromCx, fromCy);
                add(traveler, JLayeredPane.DRAG_LAYER);
                travelerHolder[0] = traveler;

                final long t0 = System.nanoTime();

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);
                holder[0] = player;

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, eff_dur));

                    // easeOut cuadrático para la posición.
                    double s = 1.0 - (1.0 - u) * (1.0 - u);
                    double is = 1.0 - s;
                    double x = is * is * fromCx + 2 * is * s * ctrlX + s * s * toCx;
                    double y = is * is * fromCy + 2 * is * s * ctrlY + s * s * toCy;

                    // Enderezado: mantiene theta el grueso del viaje y baja a 0
                    // al final (smoothstep 0.55→1) para aterrizar recta como el
                    // asiento (relevo sin pop de rotación).
                    double st = STRAIGHTEN_ON_LAND ? smoothstep(0.55, 1.0, u) : 0.0;
                    traveler.setAngle(theta * (1.0 - st));
                    traveler.setCenter(x, y);
                    traveler.repaint();

                    if (u >= 1.0 || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        if (onLand != null) {
                            // Sienta la carta (refresco async); la viajera sigue
                            // encima mostrando el mismo dorso → sin hueco.
                            onLand.run();
                        }
                        finished.countDown();
                    }
                });

                player.start();

            } catch (Exception ex) {
                // P.ej. IllegalComponentStateException si el target dejó de
                // estar en pantalla: limpiar y sentar en seco.
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                if (travelerHolder[0] != null) {
                    remove(travelerHolder[0]);
                }
                if (onLand != null) {
                    onLand.run();
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "deal flying card", ex);
        }

        // Dwell de relevo: la carta del asiento se pinta async bajo la viajera
        // (mismo dorso, misma posición) y al retirarla el relevo es idéntico.
        // No es un timeout defensivo: es el solape del relevo sin hueco.
        Helpers.parkThreadMillis(40);

        final FlyingCard traveler = travelerHolder[0];
        Helpers.GUIRunAndWait(() -> {
            if (traveler != null) {
                java.awt.Rectangle b = traveler.getBounds();
                remove(traveler);
                repaint(b);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    // Muestra/actualiza el overlay de coste de igualar centrado SOBRE las cartas
    // comunitarias (geometría-agnóstico: lee la posición real de cards_panel en
    // pantalla, así sirve para los 9 tableros con zoom y HiDPI). La fuente escala
    // con la altura de las comunitarias para verse grande sin tapar nada (gris 50%).
    public void updateCallCostOverlay(String text) {
        Helpers.GUIRun(() -> {
            if (hasFaceDownCommunityCards()) {
                // Preflop/flop/turn: aún quedan comunitarias por destapar → overlay
                // único sobre las tapadas.
                hidePlayerCallCostOverlays();
                call_cost_label.setText(text);
                if (layoutCallCostOverlay()) {
                    call_cost_label.setVisible(true);
                    call_cost_label.repaint();
                } else {
                    call_cost_label.setVisible(false);
                }
            } else {
                // River: ya no hay comunitarias que destapar → el coste se reparte
                // sobre las hole cards tapadas de cada RemotePlayer que sigue en el bote.
                call_cost_label.setVisible(false);
                updatePlayerCallCostOverlays(text);
            }
        });
    }

    public void hideCallCostOverlay() {
        Helpers.GUIRun(() -> {
            call_cost_label.setVisible(false);
            hidePlayerCallCostOverlays();
        });
    }

    // ¿Queda alguna comunitaria boca abajo? Determina el modo del overlay de coste:
    // si sí → overlay único sobre las comunitarias; si no (ronda del river) → overlays
    // por RemotePlayer.
    private boolean hasFaceDownCommunityCards() {
        CommunityCardsPanel cc = getCommunityCards();
        if (cc == null) {
            return false;
        }
        Card[] comunes = cc.getCartasComunes();
        if (comunes == null) {
            return false;
        }
        for (Card c : comunes) {
            if (c != null && c.isTapada()) {
                return true;
            }
        }
        return false;
    }

    private volatile boolean call_overlay_listener_attached = false;

    // Recoloca y reescala el overlay para cubrir las comunitarias (posición =
    // cards_panel) con una fuente proporcional a la altura REAL de una carta
    // comunitaria — así el texto SIGUE a las comunitarias: encoge con la vista
    // compacta y crece al ampliarse, y escala con el zoom. Devuelve false si las
    // comunitarias no están en pantalla (entonces el overlay se oculta). Debe
    // llamarse en el EDT.
    private boolean layoutCallCostOverlay() {
        try {
            javax.swing.JPanel cards = getCommunityCards().getCards_panel();
            Card[] comunes = getCommunityCards().getCartasComunes();
            if (cards == null || comunes == null || comunes.length == 0 || comunes[0] == null) {
                return false;
            }
            final Card ref = comunes[0];
            // Escucha cambios de geometría para reescalar/recolocar el overlay solo.
            attachCallOverlayResizeListener(ref);
            if (!cards.isShowing() || !isShowing()) {
                return false;
            }

            // El overlay solo cubre las comunitarias que SIGUEN TAPADAS: el coste de
            // igualar es a la vez el coste de "destapar" la(s) carta(s) que faltan
            // (preflop → las 5; tras el flop → turn + river; tras el turn → river), y
            // así no estorba la visión de las cartas ya descubiertas. Como se reparten
            // en fila (flop1·flop2·flop3·turn·river) las tapadas son siempre un sufijo.
            // Si no queda ninguna tapada (ronda del river) cae al conjunto entero para
            // no perder el dato del coste.
            java.awt.Rectangle box = unionCardBounds(comunes, true);
            if (box == null) {
                box = unionCardBounds(comunes, false);
            }
            if (box == null || box.width <= 0 || box.height <= 0) {
                return false;
            }

            java.awt.Point cp = cards.getLocationOnScreen();
            java.awt.Point origin = getLocationOnScreen();
            call_cost_label.setBounds(cp.x - origin.x + box.x, cp.y - origin.y + box.y, box.width, box.height);

            // Fuente proporcional a la altura REAL de una carta, pero ENCOGIDA si hace
            // falta para que el número quepa en el ancho del área cubierta: con una sola
            // carta (river) el texto se ajusta para no desbordar sobre las descubiertas.
            int card_h = ref.getHeight();
            float base = card_h > 0 ? card_h : box.height;
            float size = base * 0.9f;
            final String text = call_cost_label.getText();
            if (text != null && !text.isEmpty()) {
                java.awt.FontMetrics fm = call_cost_label.getFontMetrics(
                        call_cost_label.getFont().deriveFont(java.awt.Font.BOLD, size));
                int text_w = fm.stringWidth(text);
                float budget = box.width * 0.92f;
                if (text_w > budget && text_w > 0) {
                    size *= budget / text_w;
                }
            }
            size = Math.max(12f, size);
            call_cost_label.setFont(call_cost_label.getFont().deriveFont(java.awt.Font.BOLD, size));
            return true;
        } catch (Exception ex) {
            // P.ej. IllegalComponentStateException si cards_panel dejó de estar en
            // pantalla justo ahora.
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    // Bounding box (en coordenadas de cards_panel, el padre de las cartas) de las
    // comunitarias: si only_tapadas, solo las que siguen boca abajo; si no, todas.
    // Devuelve null si no hay ninguna que contar.
    private static java.awt.Rectangle unionCardBounds(Card[] comunes, boolean only_tapadas) {
        java.awt.Rectangle box = null;
        for (Card c : comunes) {
            if (c == null || (only_tapadas && !c.isTapada())) {
                continue;
            }
            box = (box == null) ? c.getBounds() : box.union(c.getBounds());
        }
        return box;
    }

    // Engancha (una sola vez) listeners que reescalan/recolocan el overlay cuando la
    // geometría cambia, mientras esté visible. Escucha DOS cosas:
    //   - la carta comunitaria: capta cambios de TAMAÑO (zoom, compacta que achica
    //     las cartas) para recalcular la fuente.
    //   - la CommunityCardsPanel entera: capta cambios de POSICIÓN/tamaño del conjunto
    //     (p.ej. compacta MEDIA: solo encogen los remotes y el panel SUBE sin que la
    //     carta cambie de tamaño ni de posición relativa → solo este lo detecta).
    private void attachCallOverlayResizeListener(final Card ref) {
        if (call_overlay_listener_attached) {
            return;
        }
        call_overlay_listener_attached = true;
        java.awt.event.ComponentAdapter relayout = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayoutCallCostOverlayIfVisible();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                relayoutCallCostOverlayIfVisible();
            }
        };
        ref.addComponentListener(relayout);
        getCommunityCards().addComponentListener(relayout);
    }

    private void relayoutCallCostOverlayIfVisible() {
        Helpers.GUIRun(() -> {
            if (call_cost_label.isVisible()) {
                layoutCallCostOverlay();
                call_cost_label.repaint();
            }
        });
    }

    // --- Overlays de coste por RemotePlayer (ronda del river) ----------------------

    // Pinta/actualiza un overlay de coste sobre las hole cards de cada RemotePlayer que
    // sigue en el bote con sus cartas tapadas. Reutiliza una etiqueta por jugador.
    // Debe llamarse en el EDT.
    private void updatePlayerCallCostOverlays(String text) {
        RemotePlayer[] rps = remotePlayers;
        if (rps == null) {
            hidePlayerCallCostOverlays();
            return;
        }
        // El overlay del river va SOLO sobre el ÚLTIMO AGRESOR (quien hizo la última subida o
        // resubida que el local tiene que igualar), no sobre todos los que igualan antes de mi
        // turno. current_bet se mantiene como guarda de robustez (el agresor fijó la apuesta
        // actual, así que su bet coincide) + para las comprobaciones de cartas en el bote.
        double current_bet = GameFrame.getInstance().getCrupier().getApuesta_actual();
        Player last_aggressor = GameFrame.getInstance().getCrupier().getLast_aggressor();
        for (RemotePlayer rp : rps) {
            if (rp == null) {
                continue;
            }
            CallCostOverlayLabel lbl = player_call_cost_labels.get(rp);
            if (rp == last_aggressor && isPotPlayerMatchingCurrentBet(rp, current_bet)) {
                if (lbl == null) {
                    lbl = new CallCostOverlayLabel();
                    lbl.setFocusable(false);
                    lbl.setOpaque(false);
                    lbl.setVisible(false);
                    // DRAG_LAYER: por encima de todo lo que vive en el asiento (cartas,
                    // ficha de posición, GIFs de chat/rebuy, franja de bote).
                    add(lbl, JLayeredPane.DRAG_LAYER);
                    player_call_cost_labels.put(rp, lbl);
                }
                attachPlayerCallOverlayResizeListener(rp);
                lbl.setText(text);
                if (layoutPlayerCallCostOverlay(rp, lbl)) {
                    lbl.setVisible(true);
                    lbl.repaint();
                } else {
                    lbl.setVisible(false);
                }
            } else if (lbl != null) {
                lbl.setVisible(false);
            }
        }
    }

    // Guarda de "sigue en el bote con cartas ocultas + ya igualó la apuesta": (a) el RemotePlayer
    // sigue en el bote con sus dos hole cards visibles en mesa (al foldear se ocultan con
    // setVisibleCard(false)) y boca abajo —eso excluye foldeados, all-in revelados y, por usar
    // remotePlayers, al jugador local— y (b) su bet coincide con apuesta_actual. El caller
    // (updatePlayerCallCostOverlays) ADEMÁS exige que sea el ÚLTIMO AGRESOR, así que el overlay
    // del river sale SOLO sobre el que subió/resubió, no sobre cada uno que iguala.
    private static boolean isPotPlayerMatchingCurrentBet(RemotePlayer rp, double current_bet) {
        Card c1 = rp.getHoleCard1();
        Card c2 = rp.getHoleCard2();
        if (c1 == null || c2 == null
                || !c1.isVisible_card() || !c2.isVisible_card()
                || !c1.isTapada() || !c2.isTapada()
                || c1.isSecure_hidden() || c2.isSecure_hidden()) {
            return false;
        }
        // apuesta_actual > 0 está garantizado (el overlay solo se pide cuando el local
        // tiene algo que igualar), pero lo comprobamos por robustez.
        return Helpers.doubleSecureCompare(current_bet, 0f) > 0
                && Helpers.doubleSecureCompare(current_bet, rp.getBet()) == 0;
    }

    // Recoloca/reescala el overlay de un RemotePlayer para cubrir, centrado, sus dos
    // hole cards (lee posiciones reales en pantalla → sirve para los 9 tableros, zoom,
    // HiDPI y la vista compacta que encoge los remotes). Fuente proporcional a la altura
    // de la carta y encogida para que el número quepa en el ancho de las dos cartas.
    // Devuelve false si no están en pantalla. Debe llamarse en el EDT.
    private boolean layoutPlayerCallCostOverlay(RemotePlayer rp, CallCostOverlayLabel lbl) {
        try {
            Card c1 = rp.getHoleCard1();
            Card c2 = rp.getHoleCard2();
            if (c1 == null || c2 == null || !isShowing() || !c1.isShowing() || !c2.isShowing()) {
                return false;
            }
            java.awt.Point origin = getLocationOnScreen();
            java.awt.Point p1 = c1.getLocationOnScreen();
            java.awt.Rectangle box = new java.awt.Rectangle(p1.x, p1.y, c1.getWidth(), c1.getHeight());
            java.awt.Point p2 = c2.getLocationOnScreen();
            box = box.union(new java.awt.Rectangle(p2.x, p2.y, c2.getWidth(), c2.getHeight()));
            if (box.width <= 0 || box.height <= 0) {
                return false;
            }
            lbl.setBounds(box.x - origin.x, box.y - origin.y, box.width, box.height);

            // La familia de fuente la tomamos de la etiqueta de las comunitarias
            // (que ya pasó por el font pass del tapete) para que el overlay por
            // jugador use EXACTAMENTE la misma fuente, no la de por defecto del JLabel.
            java.awt.Font base_font = call_cost_label.getFont();
            int card_h = c1.getHeight();
            float base = card_h > 0 ? card_h : box.height;
            float size = base * 0.9f;
            final String text = lbl.getText();
            if (text != null && !text.isEmpty()) {
                java.awt.FontMetrics fm = lbl.getFontMetrics(
                        base_font.deriveFont(java.awt.Font.BOLD, size));
                int text_w = fm.stringWidth(text);
                float budget = box.width * 0.92f;
                if (text_w > budget && text_w > 0) {
                    size *= budget / text_w;
                }
            }
            size = Math.max(12f, size);
            lbl.setFont(base_font.deriveFont(java.awt.Font.BOLD, size));
            return true;
        } catch (Exception ex) {
            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private void hidePlayerCallCostOverlays() {
        for (CallCostOverlayLabel lbl : player_call_cost_labels.values()) {
            lbl.setVisible(false);
        }
    }

    // Engancha (una vez por RemotePlayer) listeners que reescalan/recolocan sus overlays
    // cuando cambia su geometría mientras estén visibles. Igual que con las comunitarias,
    // escucha el asiento entero (POSICIÓN: la vista compacta lo sube) y panel_cartas +
    // hole cards (TAMAÑO: la compacta achica los remotes).
    private void attachPlayerCallOverlayResizeListener(final RemotePlayer rp) {
        if (!player_call_overlay_listeners.add(rp)) {
            return;
        }
        java.awt.event.ComponentAdapter relayout = new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayoutPlayerCallCostOverlaysIfVisible();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                relayoutPlayerCallCostOverlaysIfVisible();
            }
        };
        rp.addComponentListener(relayout);
        if (rp.getPanel_cartas() != null) {
            rp.getPanel_cartas().addComponentListener(relayout);
        }
        Card c1 = rp.getHoleCard1();
        Card c2 = rp.getHoleCard2();
        if (c1 != null) {
            c1.addComponentListener(relayout);
        }
        if (c2 != null) {
            c2.addComponentListener(relayout);
        }
    }

    private void relayoutPlayerCallCostOverlaysIfVisible() {
        Helpers.GUIRun(() -> {
            for (java.util.Map.Entry<RemotePlayer, CallCostOverlayLabel> e : player_call_cost_labels.entrySet()) {
                CallCostOverlayLabel lbl = e.getValue();
                if (lbl.isVisible()) {
                    if (layoutPlayerCallCostOverlay(e.getKey(), lbl)) {
                        lbl.repaint();
                    } else {
                        lbl.setVisible(false);
                    }
                }
            }
        });
    }

    // Etiqueta del overlay de coste de igualar: pinta el texto centrado con relleno
    // negro semitransparente y un contorno (halo) blanco, para que se lea sobre
    // CUALQUIER fondo (cartas claras, dorsos oscuros, tapete) sin tapar. Hereda de
    // JLabel para reutilizar setText/setFont/setBounds del posicionado; sobreescribe
    // el pintado para dibujar el contorno (el JLabel normal no lo soporta).
    private static final class CallCostOverlayLabel extends javax.swing.JLabel {

        // Tunables de contraste/visibilidad.
        private final java.awt.Color fill = new java.awt.Color(0, 0, 0, 204);
        private final java.awt.Color halo = new java.awt.Color(255, 255, 0, 204);
        private static final float STROKE_RATIO = 0.05f; 

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            final String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Font font = getFont();
                java.awt.font.TextLayout tl = new java.awt.font.TextLayout(text, font, g2.getFontRenderContext());
                java.awt.geom.Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // Rótulo "BARAJANDO": texto centrado pintado como contorno (borde negro) + relleno
    // blanco, igual que el mensaje gigante de la pantalla final (HAS GANADO). La fuente se
    // REESCALA en cada paint para LLENAR el ancho del componente (que se fija al ancho del
    // panel de comunitarias), limitada por el alto. Sustituto visual del gif de barajado
    // cuando ese gif no se reproduce.
    private static final class ShufflingTextLabel extends javax.swing.JLabel {

        // Relleno = color de los contadores del tapete (blanco en tapete oscuro/madera,
        // su color en verde/azul/rojo); borde negro fijo. setFill lo actualiza al mostrar.
        private java.awt.Color fill = java.awt.Color.WHITE;
        private final java.awt.Color halo = new java.awt.Color(0, 0, 0, 235);
        private static final float STROKE_RATIO = 0.06f;

        ShufflingTextLabel() {
            super("", javax.swing.SwingConstants.CENTER);
            setOpaque(false);
        }

        void setFill(java.awt.Color c) {
            this.fill = (c != null) ? c : java.awt.Color.WHITE;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            final String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Font base = getFont();
                java.awt.font.FontRenderContext frc = g2.getFontRenderContext();

                // Reescala la fuente para que el texto LLENE el ancho disponible (96%),
                // limitada por el alto: así "BARAJANDO" ocupa el ancho del panel de
                // comunitarias sea cual sea la resolución.
                java.awt.font.TextLayout probe = new java.awt.font.TextLayout(text, base, frc);
                double tw = probe.getAdvance();
                double th = probe.getAscent() + probe.getDescent();
                double avail_w = getWidth() * 0.96;
                double avail_h = getHeight() * 0.96;
                double scale = tw > 0 ? avail_w / tw : 1.0;
                if (th * scale > avail_h && th > 0) {
                    scale = Math.min(scale, avail_h / th);
                }
                java.awt.Font font = base.deriveFont((float) Math.max(8.0, base.getSize2D() * scale));

                java.awt.font.TextLayout tl = new java.awt.font.TextLayout(text, font, frc);
                java.awt.geom.Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // smoothstep clásico (Hermite): 0 en x≤a, 1 en x≥b, suave en medio.
    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    // Componente efímero de la carta viajera: pinta un dorso (ya rasterizado y
    // escalado) rotado un ángulo arbitrario sobre un lienzo cuadrado, centrado.
    // Transparente fuera de la carta. Sin estado de Swing pesado. Admite además
    // escala (1.0 = tamaño nominal) y opacidad (1.0 = opaco), por defecto neutras,
    // para el efecto de reducción-y-desvanecido del aterrizaje de fichas en el bote.
    private static final class FlyingCard extends javax.swing.JComponent {

        private final java.awt.Image img;
        private final int dw;
        private final int dh;
        private volatile double angle = 0.0;
        private volatile double scale = 1.0;
        private volatile float alpha = 1.0f;

        FlyingCard(java.awt.Image img, int dw, int dh) {
            this.img = img;
            this.dw = dw;
            this.dh = dh;
            setOpaque(false);
        }

        void setAngle(double a) {
            this.angle = a;
        }

        void setScale(double s) {
            this.scale = s;
        }

        void setAlpha(float a) {
            this.alpha = a;
        }

        void setCenter(double cx, double cy) {
            setLocation((int) Math.round(cx - getWidth() / 2.0), (int) Math.round(cy - getHeight() / 2.0));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                if (alpha < 1.0f) {
                    g2.setComposite(java.awt.AlphaComposite.getInstance(
                            java.awt.AlphaComposite.SRC_OVER, Math.max(0.0f, Math.min(1.0f, alpha))));
                }
                int w = getWidth();
                int h = getHeight();
                g2.rotate(angle, w / 2.0, h / 2.0);
                if (scale != 1.0) {
                    g2.translate(w / 2.0, h / 2.0);
                    g2.scale(scale, scale);
                    g2.translate(-w / 2.0, -h / 2.0);
                }
                g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            } finally {
                g2.dispose();
            }
        }
    }

    // Vuelo de una ficha de posición (dealer/ciega): su sprite (ya escalado),
    // el asiento de origen (portador anterior; null = centro de la mesa, p.ej.
    // primera mano) y el de destino (portador nuevo).
    public static final class ChipFlight {

        private final Player from;
        private final Player to;
        private final ImageIcon sprite;

        public ChipFlight(Player from, Player to, ImageIcon sprite) {
            this.from = from;
            this.to = to;
            this.sprite = sprite;
        }
    }

    // Desliza VARIAS fichas de posición a la vez (dealer + ciegas) de su asiento
    // anterior al nuevo, justo antes del barajado central. Reutiliza la cinemática
    // del vuelo de reparto (easeOut cuadrático + arco bezier perpendicular acotado,
    // tick de 10 ms con nanoTime, duración fija = misma velocidad que las cartas),
    // pero todas las fichas viajan en paralelo (una sola Timer) para no encadenar
    // pausas. Geometría-agnóstica: lee la posición real de cada asiento en pantalla.
    // Bloquea al llamante (hilo del crupier, NUNCA EDT) hasta el aterrizaje + dwell.
    // onLand (si no es null) se ejecuta en el EDT al tocar todas el destino, ANTES
    // del dwell, para reponer las fichas estáticas bajo las viajeras (relevo sin
    // hueco). Si la animación está desactivada o no hay vuelos, no hace nada.
    public void flyChipsToSeats(final java.util.List<ChipFlight> flights, final int duration_ms, final Runnable onLand) {

        if (flights == null || flights.isEmpty()
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            return;
        }

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final java.util.List<FlyingCard> travelers = new java.util.ArrayList<>();

        Helpers.GUIRunAndWait(() -> {
            try {
                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                final double tableCx = getWidth() / 2.0;
                final double tableCy = getHeight() / 2.0;
                final double originX = getLocationOnScreen().getX();
                final double originY = getLocationOnScreen().getY();

                // Trayectoria precomputada por ficha: {fromX, fromY, ctrlX, ctrlY, toX, toY}.
                final java.util.List<double[]> paths = new java.util.ArrayList<>();

                for (ChipFlight f : flights) {
                    if (f == null || f.to == null || f.sprite == null) {
                        continue;
                    }
                    final int w = f.sprite.getIconWidth();
                    final int h = f.sprite.getIconHeight();
                    if (w <= 0 || h <= 0) {
                        continue;
                    }

                    final java.awt.geom.Point2D toScr = f.to.getPositionChipScreenCenter(w, h);
                    if (toScr == null) {
                        continue;
                    }
                    final double toCx = toScr.getX() - originX;
                    final double toCy = toScr.getY() - originY;

                    final double fromCx, fromCy;
                    final java.awt.geom.Point2D fromScr = (f.from != null) ? f.from.getPositionChipScreenCenter(w, h) : null;
                    if (fromScr != null) {
                        fromCx = fromScr.getX() - originX;
                        fromCy = fromScr.getY() - originY;
                    } else {
                        fromCx = tableCx;
                        fromCy = tableCy;
                    }

                    // Punto de control del arco: medio del trayecto desplazado
                    // perpendicular, acotado (idéntico al vuelo de cartas).
                    final double mx = (fromCx + toCx) / 2.0, my = (fromCy + toCy) / 2.0;
                    final double vx = toCx - fromCx, vy = toCy - fromCy;
                    final double len = Math.hypot(vx, vy);
                    final double arc = Math.min(len * 0.16, h);
                    final double nx = (len > 1) ? -vy / len : 0.0;
                    final double ny = (len > 1) ? vx / len : 0.0;
                    final double ctrlX = mx + nx * arc;
                    final double ctrlY = my + ny * arc;

                    final int box = (int) Math.ceil(Math.hypot(w, h));
                    final FlyingCard traveler = new FlyingCard(f.sprite.getImage(), w, h);
                    traveler.setSize(box, box);
                    traveler.setAngle(0.0);
                    traveler.setCenter(fromCx, fromCy);
                    add(traveler, JLayeredPane.DRAG_LAYER);
                    travelers.add(traveler);
                    paths.add(new double[]{fromCx, fromCy, ctrlX, ctrlY, toCx, toCy});
                }

                if (travelers.isEmpty()) {
                    if (onLand != null) {
                        onLand.run();
                    }
                    finished.countDown();
                    return;
                }

                final long t0 = System.nanoTime();
                final boolean[] landed = {false};

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);
                holder[0] = player;

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, duration_ms));

                    // easeOut cuadrático para la posición (mismo que el reparto).
                    double s = 1.0 - (1.0 - u) * (1.0 - u);
                    double is = 1.0 - s;

                    for (int k = 0; k < travelers.size(); k++) {
                        double[] p = paths.get(k);
                        double x = is * is * p[0] + 2 * is * s * p[2] + s * s * p[4];
                        double y = is * is * p[1] + 2 * is * s * p[3] + s * s * p[5];
                        FlyingCard traveler = travelers.get(k);
                        traveler.setCenter(x, y);
                        traveler.repaint();
                    }

                    if (u >= 1.0 || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        player.stop();
                        if (!landed[0]) {
                            landed[0] = true;
                            // Repone las fichas estáticas bajo las viajeras (mismo
                            // sprite, misma posición) → relevo sin hueco al retirarlas.
                            if (onLand != null) {
                                onLand.run();
                            }
                        }
                        finished.countDown();
                    }
                });

                player.start();

            } catch (Exception ex) {
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                for (FlyingCard traveler : travelers) {
                    remove(traveler);
                }
                if (onLand != null) {
                    onLand.run();
                }
                repaint();
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Helpers.logCooperativeCancellation(Logger.getLogger(TablePanel.class.getName()),
                    "chip flight", ex);
        }

        // Dwell de relevo: la ficha estática (repuesta en onLand) se pinta async
        // bajo la viajera, así al retirarla el relevo es idéntico (sin parpadeo).
        Helpers.parkThreadMillis(40);

        Helpers.GUIRunAndWait(() -> {
            for (FlyingCard traveler : travelers) {
                java.awt.Rectangle b = traveler.getBounds();
                remove(traveler);
                repaint(b);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    // Vuelo de UNA ficha (sprite del bote) desde el asiento del jugador que acaba
    // de meter dinero hasta el icono del pot_label (el bote). Misma cinemática que el vuelo
    // de reparto/posición (easeOut cuadrático + arco bezier perpendicular acotado,
    // tick de 10 ms con nanoTime, velocidad constante por altura de sprite para que
    // todas las fichas viajen igual de rápido sin importar el asiento). A diferencia
    // del relevo de las fichas de posición, aquí NO hay ficha estática que reponer:
    // al aterrizar la viajera se ENCOGE y se desvanece (efecto de reducción), el
    // pot_label parpadea en amarillo (señal de que absorbió las fichas) y la viajera
    // se retira. Geometría-agnóstica: lee la posición real del asiento y del bote en
    // pantalla, así sirve para los 9 tableros con zoom y HiDPI.
    //
    // NO bloquea: arranca en el EDT y devuelve el control de inmediato (la limpieza,
    // el flash y onLand ocurren en el último tramo del propio timer). Pueden coexistir
    // varias fichas en vuelo.
    //
    // onLand (si no es null) se ejecuta EXACTAMENTE UNA VEZ: en el instante en que la
    // ficha toca el bote (a la vez que el parpadeo amarillo), para que el valor del
    // pot_label se actualice justo al aterrizar. Si la animación no puede correr (sin
    // sprite/origen visible o fin de transmisión) se ejecuta de inmediato para no dejar
    // el pot_label sin actualizar.
    public void flyChipToPot(final Player from, final ImageIcon sprite, final int shrink_ms, final Runnable onLand) {

        // Velocidad constante medida en ALTURAS DE SPRITE (invariante al zoom),
        // como el vuelo de reparto, para que todas las fichas viajen a la misma
        // velocidad visual sin importar el asiento.
        final double MS_PER_CHIPHEIGHT = 120.0;
        final int SPEED_MIN_MS = 120;
        final int SPEED_MAX_MS = 320;

        // Garantía de ejecución única de onLand pase lo que pase (aterrizaje real o
        // cualquier salida temprana), para no congelar el valor del pot_label.
        final Runnable[] land_holder = {onLand};

        if (sprite == null || from == null
                || GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
            runOnce(land_holder);
            return;
        }

        final int w = sprite.getIconWidth();
        final int h = sprite.getIconHeight();
        if (w <= 0 || h <= 0) {
            runOnce(land_holder);
            return;
        }

        Helpers.GUIRun(() -> {
            try {
                if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                    runOnce(land_holder);
                    return;
                }

                final double originX = getLocationOnScreen().getX();
                final double originY = getLocationOnScreen().getY();

                // Origen: asiento del jugador (mismo anclaje que su ficha de
                // posición). Si no está visible (asiento retirado, etc.) no animamos.
                final java.awt.geom.Point2D fromScr = from.getPositionChipScreenCenter(w, h);
                if (fromScr == null) {
                    runOnce(land_holder);
                    return;
                }
                final double fromCx = fromScr.getX() - originX;
                final double fromCy = fromScr.getY() - originY;

                // Destino: el ICONO (fichas) del pot_label. Si no está visible,
                // el centro de la mesa.
                final double toCx, toCy;
                final java.awt.geom.Point2D potIcon = getCommunityCards().getPotIconScreenCenter();
                if (potIcon != null) {
                    toCx = potIcon.getX() - originX;
                    toCy = potIcon.getY() - originY;
                } else {
                    toCx = getWidth() / 2.0;
                    toCy = getHeight() / 2.0;
                }

                // Punto de control del arco: medio del trayecto desplazado
                // perpendicular, acotado (idéntico al vuelo de cartas/fichas).
                final double mx = (fromCx + toCx) / 2.0, my = (fromCy + toCy) / 2.0;
                final double vx = toCx - fromCx, vy = toCy - fromCy;
                final double len = Math.hypot(vx, vy);
                final double arc = Math.min(len * 0.16, h);
                final double nx = (len > 1) ? -vy / len : 0.0;
                final double ny = (len > 1) ? vx / len : 0.0;
                final double ctrlX = mx + nx * arc;
                final double ctrlY = my + ny * arc;

                final int fly_dur = (int) Math.round(Math.max(SPEED_MIN_MS,
                        Math.min(SPEED_MAX_MS, (len / h) * MS_PER_CHIPHEIGHT)));

                final int box = (int) Math.ceil(Math.hypot(w, h));
                final FlyingCard traveler = new FlyingCard(sprite.getImage(), w, h);
                traveler.setSize(box, box);
                traveler.setAngle(0.0);
                traveler.setCenter(fromCx, fromCy);
                add(traveler, JLayeredPane.DRAG_LAYER);

                final long t0 = System.nanoTime();
                final boolean[] flashed = {false};

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);

                player.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;

                    boolean done = GameFrame.getInstance().getCrupier().isFin_de_la_transmision();

                    if (!done && elapsed < fly_dur) {
                        // Fase 1: vuelo al bote (easeOut cuadrático, como el reparto).
                        double u = (double) elapsed / Math.max(1, fly_dur);
                        double s = 1.0 - (1.0 - u) * (1.0 - u);
                        double is = 1.0 - s;
                        double x = is * is * fromCx + 2 * is * s * ctrlX + s * s * toCx;
                        double y = is * is * fromCy + 2 * is * s * ctrlY + s * s * toCy;
                        traveler.setCenter(x, y);
                        traveler.repaint();
                    } else if (!done) {
                        // Fase 2: aterrizaje en el bote → encoge y se desvanece
                        // (smoothstep para una reducción suave). Al tocar el bote
                        // (primer tick de esta fase) el valor del pot_label se
                        // actualiza Y parpadea en amarillo en el MISMO runnable del
                        // EDT (número y color cambian a la vez, el color no se
                        // adelanta al número).
                        if (!flashed[0]) {
                            flashed[0] = true;
                            final Runnable land = land_holder[0];
                            land_holder[0] = null; // consumido: el cleanup no lo re-ejecuta
                            getCommunityCards().flashPotLabelYellow(land);
                        }
                        long se = elapsed - fly_dur;
                        double su = Math.min(1.0, (double) se / Math.max(1, shrink_ms));
                        double k = su * su * (3.0 - 2.0 * su);
                        traveler.setCenter(toCx, toCy);
                        traveler.setScale(1.0 - k);
                        traveler.setAlpha((float) (1.0 - k));
                        traveler.repaint();
                        done = su >= 1.0;
                    }

                    if (done) {
                        player.stop();
                        java.awt.Rectangle b = traveler.getBounds();
                        remove(traveler);
                        repaint(b);
                        // Salida por fin de transmisión antes de tocar el bote: onLand
                        // no se ejecutó en la fase 2; ejecútalo ahora (no-op si ya corrió).
                        runOnce(land_holder);
                    }
                });

                player.start();

            } catch (Exception ex) {
                // P.ej. IllegalComponentStateException si el asiento dejó de estar
                // en pantalla: simplemente no animamos.
                Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                runOnce(land_holder);
            }
        });
    }

    // Ejecuta el Runnable del holder a lo sumo una vez (lo anula tras correr). Las
    // rutas que lo invocan son mutuamente excluyentes (salida temprana en el hilo
    // llamante, o ticks del timer en el EDT), así que no hay acceso concurrente.
    private static void runOnce(Runnable[] holder) {
        Runnable r = holder[0];
        if (r != null) {
            holder[0] = null;
            r.run();
        }
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

                // El audio del barajado va SINCRONIZADO a la vuelta del gif:
                // arranca con el primer frame y se corta al final de la vuelta;
                // si el gif da otra vuelta, vuelve a arrancar de cero. Clip
                // pre-abierto y reutilizado -> arrancar/parar es instantaneo, sin
                // un open por ciclo que pueda perder la carrera y quedarse mudo.
                if (audio != null) {
                    Audio.playPreloadedWav(audio);
                }

                final long[] t0 = {System.nanoTime()};
                final long total_ms = anim.getTotalMs();
                final int[] painted = {0};
                final boolean[] audio_on = {audio != null};

                final javax.swing.Timer player = new javax.swing.Timer(PRE_RENDERED_TICK_MS, null);

                player_holder[0] = player;

                player.addActionListener(e -> {

                    if (GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                        if (audio != null && audio_on[0]) {
                            audio_on[0] = false;
                            Audio.stopPreloadedWav(audio);
                        }
                        player.stop();
                        finished.countDown();
                        return;
                    }

                    long elapsed = (System.nanoTime() - t0[0]) / 1_000_000L;

                    int idx = anim.frameAt(elapsed);

                    // Corte temprano del audio en audio_stop_frame (antes del último
                    // frame): deja que el buffer de salida del dispositivo drene
                    // antes de que la vuelta acabe visualmente, para que el sonido no
                    // se oiga un pelín después de que la animación desaparezca.
                    if (audio_on[0] && idx + 1 >= audio_stop_frame) {
                        audio_on[0] = false;
                        Audio.stopPreloadedWav(audio);
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
                            // Nueva vuelta del gif: rebobinado exacto de tiempos y
                            // el audio vuelve a arrancar de cero (se cortará otra vez
                            // en audio_stop_frame de esta vuelta).
                            t0[0] = System.nanoTime();
                            painted[0] = 0;
                            getCentral_label().setFrameOverride(anim.getFrame(0));
                            if (audio != null) {
                                audio_on[0] = true;
                                Audio.playPreloadedWav(audio);
                            }
                        } else {
                            // Fin del barajado: el audio ya se cortó en
                            // audio_stop_frame; cierre defensivo por si no se alcanzó.
                            if (audio != null && audio_on[0]) {
                                audio_on[0] = false;
                                Audio.stopPreloadedWav(audio);
                            }
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

    // Muestra "BARAJANDO" centrado donde iría el gif de barajado, con el ANCHO del panel
    // de comunitarias (las letras se reescalan para llenar ese ancho). Es el sustituto
    // visual cuando el gif de barajado NO se reproduce (baraja sin shuffle.gif o
    // animaciones desactivadas). EDT-safe; la mantiene visible hasta hideShufflingText().
    public void showShufflingText() {
        Helpers.GUIRun(() -> {
            // El ancho se fija como fracción del TAPETE (no del panel de comunitarias):
            // es la única referencia INVARIANTE por mano. El ancho del panel de
            // comunitarias depende del estado de layout/zoom, que se asienta tras la
            // primera mano -> antes daba un rótulo grande la primera vez y más pequeño
            // las siguientes. El tapete no cambia con el zoom, así que el rótulo sale
            // SIEMPRE igual. 0.35 del ancho del tapete = tamaño elegido por el usuario.
            int w = Math.round(getWidth() * 0.35f);
            int h = Math.max(40, Math.round(w * 0.30f));
            // Relleno = color de los contadores del tapete (se adapta al fondo elegido);
            // blanco si aún no está definido. Borde negro fijo (lo pone el propio label).
            java.awt.Color tapete_color = null;
            try {
                tapete_color = getCommunityCards().getColor_contadores();
            } catch (Exception ex) {
            }
            shuffling_label.setFill(tapete_color);
            shuffling_label.setText(Translator.translate("game.barajando"));
            // Fuente base; el propio paint la reescala para llenar el ancho.
            shuffling_label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, Math.max(12, h)));
            shuffling_label.setSize(w, h);
            shuffling_label.setLocation(Math.round((getWidth() - w) / 2f), Math.round((getHeight() - h) / 2f));
            shuffling_label.setVisible(true);
            shuffling_label.repaint();
        });
    }

    public void hideShufflingText() {
        Helpers.GUIRun(() -> {
            shuffling_label.setVisible(false);
            shuffling_label.setText("");
        });
    }

    public void hideALL() {

        Helpers.GUIRun(() -> {
            for (Player p : players) {
                ((JPanel) p).setVisible(false);
            }

            getCommunityCards().setVisible(false);

            central_label.setVisible(false);

            // Rótulo "BARAJANDO": vive en su capa, fuera del flujo normal de la mesa; al
            // ocultar el tapete (salir, game over, balance) quedaría flotando en el centro.
            shuffling_label.setVisible(false);

            // El overlay de coste de igualar vive en una capa propia del tapete:
            // sin esto quedaría flotando en el centro al ocultarse la mesa (salir,
            // game over, balance). Lo mismo para los overlays por jugador del river.
            call_cost_label.setVisible(false);
            hidePlayerCallCostOverlays();

            // El diálogo del MODO AUTO vive en su propia ventana (no en la
            // jerarquía del tapete): al ocultar la mesa —salir de la timba, fin
            // de partida— quedaría flotando sobre el balance. Lo cerramos como
            // cancelado (la mano ya terminó, no ejecuta ninguna acción).
            LocalPlayer local_player = GameFrame.getInstance().getLocalPlayer();

            if (local_player != null && local_player.getAuto_action_dialog() != null) {
                local_player.getAuto_action_dialog().cancel();
            }
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

                if (GameFrame.COLOR_TAPETE.endsWith("*") && Init.I1 != null) {

                    // Tapete de imagen única: se estira al panel con drawImage y
                    // rectángulo de destino. A diferencia de un TexturePaint con un
                    // tile del tamaño del panel, drawImage mapea SIEMPRE la fuente
                    // completa a (0,0)-(w,h); el clip de un repintado parcial solo
                    // limita QUÉ píxeles se escriben, nunca el muestreo, así que la
                    // franja que repinta una ficha/carta voladora a su paso queda
                    // idéntica al pintado completo (sin costuras ni "deformación"
                    // del fondo por donde cruzan las animaciones).
                    if (secret_bg == null) {
                        try {
                            secret_bg = Helpers.toBufferedImage(Init.I1);
                        } catch (Exception ex) {
                            Logger.getLogger(TablePanel.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (secret_bg != null) {
                        Graphics2D g2d = (Graphics2D) g;
                        Object old_interp = g2d.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2d.drawImage(secret_bg, 0, 0, getWidth(), getHeight(), null);
                        if (old_interp != null) {
                            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, old_interp);
                        }
                    }

                    ok = true;

                } else if (invalidate || tp == null) {

                    Helpers.threadRun(() -> {
                        synchronized (paint_lock) {
                            // El tapete de imagen única (sufijo "*") se pinta arriba con
                            // drawImage y nunca llega aquí; este rebuild es solo para los
                            // tapetes de TEXTURA en mosaico (JPG pequeño que se repite).
                            BufferedImage tile = null;
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

        // La comprobación va DENTRO del synchronized: si quedara fuera, el último
        // zoomable podía hacer add()+notifyAll justo entre el size() y el wait, se
        // perdía la notificación y se dormía el 1000ms completo → atasco aleatorio
        // de ~1s en el arranque (zoom inicial) mientras los zoomables rescalan.
        synchronized (mynotifier) {
            while (mynotifier.size() < zoomables.length) {
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

                    tapeteBottom = getLocationOnScreen().getY() + getHeight();
                    tapeteRight = getLocationOnScreen().getX() + getWidth();
                    playerBottom = ((JPanel) jugador).getLocationOnScreen().getY() + ((JPanel) jugador).getHeight();
                    playerRight = ((JPanel) jugador).getLocationOnScreen().getX() + ((JPanel) jugador).getWidth();

                }

                while (playerBottom > tapeteBottom || playerRight > tapeteRight) {

                    Helpers.GUIRunAndWait(GameFrame.getInstance().getZoom_menu_out()::doClick);

                    tapeteBottom = getLocationOnScreen().getY() + getHeight();
                    tapeteRight = getLocationOnScreen().getX() + getWidth();
                    playerBottom = ((JPanel) jugador).getLocationOnScreen().getY() + ((JPanel) jugador).getHeight();
                    playerRight = ((JPanel) jugador).getLocationOnScreen().getX() + ((JPanel) jugador).getWidth();

                    Helpers.pausar(GameFrame.GUI_RENDER_WAIT);

                }
            }

        }

        // Con el tablero por anclas (DynamicTablePanel) los asientos van PEGADOS al
        // borde y NUNCA exceden el tapete, así que el recorte por bordes de arriba casi
        // no se dispara; pero a zoom ALTO en mesas llenas pueden SOLAPARSE entre sí o con
        // las comunitarias. Se hace zoom-out (sin bajar del zoom por defecto) hasta que
        // no haya solape. Guard para no colgarse. Solo actúa por encima del zoom por
        // defecto: a zoom normal la disposición no solapa (verificado), así que es no-op.
        int overlap_guard = 0;
        while (GameFrame.ZOOM_LEVEL > GameFrame.DEFAULT_ZOOM_LEVEL && hasLayoutOverlap() && overlap_guard++ < 60) {
            Helpers.GUIRunAndWait(GameFrame.getInstance().getZoom_menu_out()::doClick);
            Helpers.pausar(GameFrame.GUI_RENDER_WAIT);
        }

    }

    // ¿Se solapa el bounding box de algún asiento con el de otro, o con la FILA DE
    // CARTAS de las comunitarias (no el panel entero, que es más ancho por el bote y los
    // controles → daría falsos positivos)? Lo usa autoZoom para hacer zoom-out cuando el
    // zoom alto amontona los asientos. Devuelve false si algo no está en pantalla.
    private boolean hasLayoutOverlap() {
        Player[] ps = getPlayers();
        if (ps == null) {
            return false;
        }
        try {
            java.awt.Rectangle[] rects = new java.awt.Rectangle[ps.length];
            for (int i = 0; i < ps.length; i++) {
                JPanel p = (JPanel) ps[i];
                if (!p.isShowing()) {
                    return false;
                }
                java.awt.Point loc = p.getLocationOnScreen();
                rects[i] = new java.awt.Rectangle(loc.x, loc.y, p.getWidth(), p.getHeight());
            }
            for (int i = 0; i < rects.length; i++) {
                for (int j = i + 1; j < rects.length; j++) {
                    if (rects[i].intersects(rects[j])) {
                        return true;
                    }
                }
            }
            CommunityCardsPanel cc = getCommunityCards();
            JPanel cards = (cc != null) ? cc.getCards_panel() : null;
            if (cards != null && cards.isShowing()) {
                java.awt.Point cl = cards.getLocationOnScreen();
                java.awt.Rectangle cr = new java.awt.Rectangle(cl.x, cl.y, cards.getWidth(), cards.getHeight());
                for (java.awt.Rectangle r : rects) {
                    if (r.intersects(cr)) {
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            // getLocationOnScreen puede fallar si algo dejó de estar en pantalla justo ahora.
            return false;
        }
        return false;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
