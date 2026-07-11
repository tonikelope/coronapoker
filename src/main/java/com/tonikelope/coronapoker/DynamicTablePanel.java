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

import java.awt.Container;
import java.awt.Dimension;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * Tablero ÚNICO que coloca a los N jugadores por geometría, sustituyendo a los 9
 * .form fijos (TablePanel2..TablePanel10).
 *
 * Las posiciones NO se inventan: están extraídas de los propios tableros
 * originales (instanciándolos y leyendo dónde los colocaba su GroupLayout) y
 * guardadas como FRACCIONES del ancho/alto del tapete (ver ANCHORS). Así se
 * reproduce su disposición exacta —incluidos los huecos iguales de los laterales,
 * las esquinas inferiores de 8 jugadores o el local descentrado de 10— y, al ser
 * fracciones, escala a cualquier resolución/tamaño de ventana.
 *
 * Toda la lógica (animaciones de reparto/fichas, overlays, zoom, autoZoom,
 * pintado del tapete) vive en la clase base TablePanel y es geometría-agnóstica
 * (lee posiciones reales en pantalla), así que este panel solo tiene que crear
 * los asientos y colocarlos: el resto funciona sin tocar nada.
 *
 * @author tonikelope
 */
public class DynamicTablePanel extends TablePanel {

    // Margen (px) entre el borde exterior del asiento y el borde del tapete. Los
    // asientos se PEGAN a su borde con este margen (no se anclan por el centro), así
    // aprovechan el espacio y siguen pegados aunque cambien de tamaño (vista compacta).
    // Pequeño a propósito: el propio asiento ya tiene un borde redondeado con padding,
    // que es el aire mínimo visible aunque el panel esté pegado al borde del tapete.
    private static final int EDGE_MARGIN = 5;

    // Anclas por número de jugadores (índice = nº de jugadores, 2..10). Cada fila es
    // un asiento en el ORDEN del array de jugadores: índice 0 = jugador local, 1..N-1
    // = remotos (remotePlayer1, remotePlayer2, ...). Los valores son {fx, fy}, el
    // CENTRO del asiento como fracción del ancho y alto del tapete. La última fila
    // (índice N) es el centro de las cartas COMUNITARIAS.
    //
    // Extraído de TablePanel2..TablePanel10 a 2560×1440 (16:9) con SeatLayoutExtractor.
    private static final double[][][] ANCHORS = new double[11][][];

    static {
        ANCHORS[2] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.5000, 0.1590}, // r1
            {0.4242, 0.5000}, // comunitarias
        };
        ANCHORS[3] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.0805, 0.1674}, // r1
            {0.9195, 0.1674}, // r2
            {0.5000, 0.5021}, // comunitarias
        };
        ANCHORS[4] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.5000}, // r1
            {0.5000, 0.1590}, // r2
            {0.9242, 0.5000}, // r3
            {0.3668, 0.5063}, // comunitarias
        };
        ANCHORS[5] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.5000}, // r1
            {0.3570, 0.1674}, // r2
            {0.6430, 0.1674}, // r3
            {0.9242, 0.5000}, // r4
            {0.3668, 0.5167}, // comunitarias
        };
        ANCHORS[6] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.7132}, // r1
            {0.0758, 0.2868}, // r2
            {0.5000, 0.1590}, // r3
            {0.9242, 0.2868}, // r4
            {0.9242, 0.7132}, // r5
            {0.3668, 0.5063}, // comunitarias
        };
        ANCHORS[7] = new double[][]{
            {0.5000, 0.8451}, // local
            {0.0758, 0.7132}, // r1
            {0.0758, 0.2868}, // r2
            {0.3570, 0.1674}, // r3
            {0.6430, 0.1674}, // r4
            {0.9242, 0.2854}, // r5
            {0.9242, 0.7139}, // r6
            {0.3668, 0.5146}, // comunitarias
        };
        ANCHORS[8] = new double[][]{
            {0.4996, 0.8451}, // local
            {0.1879, 0.8410}, // r1
            {0.0805, 0.5000}, // r2
            {0.2172, 0.1590}, // r3
            {0.5008, 0.1590}, // r4
            {0.7832, 0.1590}, // r5
            {0.9195, 0.5000}, // r6
            {0.8113, 0.8410}, // r7
            {0.5012, 0.3410}, // comunitarias
        };
        ANCHORS[9] = new double[][]{
            {0.5000, 0.8368}, // local
            {0.0805, 0.8326}, // r1
            {0.0805, 0.5000}, // r2
            {0.0805, 0.1674}, // r3
            {0.3602, 0.1674}, // r4
            {0.6398, 0.1674}, // r5
            {0.9195, 0.1674}, // r6
            {0.9195, 0.5000}, // r7
            {0.9195, 0.8326}, // r8
            {0.5012, 0.5063}, // comunitarias
        };
        ANCHORS[10] = new double[][]{
            {0.3793, 0.8368}, // local
            {0.0805, 0.8326}, // r1
            {0.0805, 0.5000}, // r2
            {0.0805, 0.1674}, // r3
            {0.3602, 0.1674}, // r4
            {0.6398, 0.1674}, // r5
            {0.9195, 0.1674}, // r6
            {0.9195, 0.5000}, // r7
            {0.9195, 0.8326}, // r8
            {0.6781, 0.8326}, // r9
            {0.5012, 0.5063}, // comunitarias
        };
    }

    private volatile CommunityCardsPanel communityCards;
    private volatile LocalPlayer localPlayer;
    private volatile Player[] seats;

    // Mientras dura la animación de downgrade, doLayout() no re-ancla los asientos
    // (el tween los mueve a mano con setBounds y competirían).
    private volatile boolean layout_frozen = false;

    public DynamicTablePanel(int num_players) {

        // La clase base (super()) ya ha montado su layout vacío y añadido en sus
        // capas los overlays (fastbuttons, central_label, shuffling_label,
        // call_cost_label). Aquí creamos los asientos y pasamos a colocado manual.

        Helpers.GUIRunAndWait(() -> {

            // Sin layout manager: el posicionado lo hace doLayout() por geometría.
            setLayout(null);

            CommunityCardsPanel community = new CommunityCardsPanel();
            LocalPlayer local = new LocalPlayer();

            RemotePlayer[] remotes = new RemotePlayer[num_players - 1];
            for (int i = 0; i < remotes.length; i++) {
                remotes[i] = new RemotePlayer();
            }

            Player[] all = new Player[num_players];
            all[0] = local;
            System.arraycopy(remotes, 0, all, 1, remotes.length);

            // Asientos y comunitarias en la capa por defecto (por debajo de los
            // overlays que la base añade en POPUP/PALETTE/DRAG).
            add(community, JLayeredPane.DEFAULT_LAYER);
            for (Player p : all) {
                add((JPanel) p, JLayeredPane.DEFAULT_LAYER);
            }

            // Campos propios ANTES de revalidate(): doLayout() podría dispararse en
            // la revalidación y los necesita (guarda además contra null por si acaso).
            this.communityCards = community;
            this.localPlayer = local;
            this.seats = all;

            // Arrays que consume la clase base.
            players = all;
            remotePlayers = remotes;

            ZoomableInterface[] z = new ZoomableInterface[num_players + 2];
            for (int i = 0; i < num_players; i++) {
                z[i] = (ZoomableInterface) all[i];
            }
            z[num_players] = community;
            z[num_players + 1] = fastbuttons;
            zoomables = z;

            revalidate();
            repaint();
        });
    }

    @Override
    public CommunityCardsPanel getCommunityCards() {
        return communityCards;
    }

    @Override
    public LocalPlayer getLocalPlayer() {
        return localPlayer;
    }

    // Colocado por anclas: cada asiento (y las comunitarias) se centra en su
    // posición {fx, fy} del tablero original de N jugadores, escalada al tamaño
    // ACTUAL del tapete. Se llama en cada validación (resize del tapete, revalidate
    // tras zoom). NO toca los overlays de las capas superiores (fastbuttons,
    // central_label, etc.): esos los posiciona la clase base.
    @Override
    public void doLayout() {

        // Congelado durante la animación de downgrade: el tween mueve los asientos
        // con setBounds y un doLayout los devolvería a su ancla (competirían).
        if (layout_frozen) {
            return;
        }

        final Player[] s = seats;
        final CommunityCardsPanel community = communityCards;

        final int W = getWidth();
        final int H = getHeight();

        if (s == null || community == null || W <= 0 || H <= 0) {
            return;
        }

        final int n = s.length;
        final double[][] anchors = (n < ANCHORS.length) ? ANCHORS[n] : null;
        if (anchors == null) {
            return;
        }

        // Asientos (0 = local, 1..n-1 = remotos): cada uno se PEGA a su borde más
        // cercano (según su ancla) con EDGE_MARGIN, y usa la coordenada perpendicular
        // del ancla para conservar el reparto exacto del original. Al anclar por el
        // borde y no por el centro, el asiento sigue pegado aunque encoja (compacta).
        for (int i = 0; i < n; i++) {
            JPanel panel = (JPanel) s[i];
            java.awt.Rectangle r = seatBoundsFor(n, i, panel.getPreferredSize());
            if (r != null) {
                panel.setBounds(r);
            }
        }

        // Comunitarias: SIEMPRE centradas en la mesa por su FILA DE CARTAS, no por los
        // bounds del panel. El CommunityCardsPanel es más ancho que las cartas (lleva el
        // bote y los controles); las cartas van CENTRADAS dentro de su cards_panel (gaps
        // elásticos a ambos lados) y el cards_panel ocupa todo el ancho del panel. Colocamos
        // el panel de forma que el CENTRO de su cards_panel caiga en (W/2, H/2).
        //
        // CLAVE (por qué NO hay bucle NI transitorio, pese a que off_x = cards.width/2 SÍ
        // depende del ancho del panel): el offset se LEE del layout que Swing ya calculó, y
        // ese layout es un PUNTO FIJO. Razón: doLayout fija el community a su PROPIO preferred
        // (cd), y el preferred del community NO depende del tamaño que se le asigna —ningún
        // hijo reflowa por ancho (todo GroupLayout, labels de una línea, sin HTML ni wrap)—,
        // así que su layout interno (y con él la X de la fila de cartas) es estable entre
        // pasadas. Converge en 2 pasadas y la 2.ª solo cambia POSICIÓN, que no invalida.
        // NUNCA se fuerza community.doLayout() aquí: hacerlo re-colocaba los hijos a media
        // pasada, el preferred size oscilaba y colgaba el EDT al pausar (bucle infinito →
        // cuelgue total en pantalla completa). Ese, y NO el que off_x dependa del ancho, era
        // el mecanismo del cuelgue de 22.58.
        //
        // doLayout es IDEMPOTENTE por diseño: no toca el preferred de nadie, y el setBounds
        // de abajo solo dispara cuando algo cambió de verdad; un cambio de POSICIÓN no
        // invalida (Component.reshape solo invalida al redimensionar), y el TAMAÑO solo
        // cambia cuando cambia el preferred (evento puntual: zoom, pausa), que se estabiliza
        // en una pasada. Por tanto no puede realimentarse a sí mismo → no hay bucle posible.
        // HORIZONTAL: por la fila de cartas (punto fijo, converge en 2 pasadas). Es el arreglo
        // del descentrado original y NO se mueve al pausar/última mano (el banner solo cambia
        // el ALTO del panel, no la X de la fila de cartas).
        //
        // VERTICAL: por los BOUNDS del panel (cd.height/2), NO por la fila de cartas. En
        // juego normal las cartas YA están en el centro vertical del panel (medido: dif
        // ~1px), así que no se mueven. Pero centrarlo por la fila de cartas hacía que, al
        // aparecer/desaparecer el banner de "ÚLTIMA MANO" (que empuja las cartas hacia
        // abajo), el panel ENTERO se reposicionara para volver a centrarlas → parpadeo de
        // todo el community. Por bounds, el panel solo CRECE/ENCOGE simétrico en su sitio
        // (sin salto) y en UNA pasada (off_y no depende de que las cartas ya estén
        // colocadas, así que no hay lectura obsoleta ni doble reposicionamiento).
        Dimension cd = community.getPreferredSize();
        double off_x = cd.width / 2.0;
        double off_y = cd.height / 2.0;
        JPanel cards = community.getCards_panel();
        if (cards != null && cards.getWidth() > 0) {
            java.awt.Point p = javax.swing.SwingUtilities.convertPoint(
                    cards, cards.getWidth() / 2, cards.getHeight() / 2, community);
            off_x = p.x;
        }
        int comm_x = (int) Math.round(W / 2.0 - off_x);
        int comm_y = (int) Math.round(H / 2.0 - off_y);
        if (community.getX() != comm_x || community.getY() != comm_y
                || community.getWidth() != cd.width || community.getHeight() != cd.height) {
            community.setBounds(comm_x, comm_y, cd.width, cd.height);
        }
    }

    // Bounds (con el modelo de pegado al borde) del asiento 'index' en una mesa de
    // 'total' jugadores, para un asiento de tamaño 'd', al tamaño actual del tapete.
    // Reutilizado por doLayout() y por la animación de downgrade (que necesita saber
    // dónde caerá cada superviviente en la mesa de M jugadores). Devuelve null si no
    // hay anclas para ese total/índice.
    private java.awt.Rectangle seatBoundsFor(int total, int index, Dimension d) {
        final int W = getWidth();
        final int H = getHeight();
        final double[][] anchors = (total >= 0 && total < ANCHORS.length) ? ANCHORS[total] : null;
        if (anchors == null || index < 0 || index >= anchors.length) {
            return null;
        }
        double fx = anchors[index][0];
        double fy = anchors[index][1];

        double d_left = fx;
        double d_right = 1.0 - fx;
        double d_top = fy;
        double d_bottom = 1.0 - fy;
        double d_min = Math.min(Math.min(d_left, d_right), Math.min(d_top, d_bottom));

        double seat_cx;
        double seat_cy;
        if (d_min == d_left) {
            seat_cx = EDGE_MARGIN + d.width / 2.0;
            seat_cy = fy * H;
        } else if (d_min == d_right) {
            seat_cx = W - EDGE_MARGIN - d.width / 2.0;
            seat_cy = fy * H;
        } else if (d_min == d_top) {
            seat_cx = fx * W;
            seat_cy = EDGE_MARGIN + d.height / 2.0;
        } else {
            seat_cx = fx * W;
            seat_cy = H - EDGE_MARGIN - d.height / 2.0;
        }

        return new java.awt.Rectangle((int) Math.round(seat_cx - d.width / 2.0),
                (int) Math.round(seat_cy - d.height / 2.0), d.width, d.height);
    }

    // Anima la transición de N a M jugadores cuando alguno abandona: los que se van
    // se DESVANECEN (fantasma-snapshot con alfa 1→0) y los supervivientes se DESLIZAN
    // de su posición actual (mesa de N) a su hueco en la mesa de M, manteniendo el
    // orden del anillo. Funciona con 1 o varios abandonos a la vez. Bloquea al
    // llamante (hilo del crupier, NUNCA EDT) hasta terminar. Pensado para llamarse
    // JUSTO ANTES del swap del tablero (downgradeAndRefreshTapete): al acabar, los
    // supervivientes quedan en las posiciones de la mesa de M, que es donde el
    // tablero nuevo colocará sus copias → el swap es imperceptible. No toca la lógica
    // de juego (arrays de jugadores): es puramente visual.
    public void animateDowngrade(int duration_ms) {

        final Player[] all = players;
        if (all == null) {
            return;
        }

        // TOCTOU CONOCIDO (dejado a propósito; cosmético y AUTO-CORREGIDO): esta lectura de
        // isExit() (T1) y la que hace TablePanelFactory.downgradePanel al reconstruir el
        // tablero (T2, ~500ms después) son DOS lecturas distintas. isExit() puede pasar a
        // true entre ambas: se fija con RemotePlayer.setExit() (flag volatile, NO bajo el
        // monitor del crupier) vía Participant.markExitAndNotify(), que corre en hilos
        // watchdog/escritor (timeout, socket cerrado, auto-expulsión). Como exit es monótono,
        // leaving(T2) ⊇ leaving(T1). CONSECUENCIA ACOTADA A LO VISUAL: si un 2.º jugador cae
        // durante la animación, su asiento se desliza como superviviente pero el tablero nuevo
        // (T2) ya no lo incluye → un salto de UN fotograma al hacer el swap, tras el cual el
        // tablero queda correcto (downgradePanel es internamente consistente: tamaño y copia
        // usan la MISMA lectura T2). Si quedaran <2 jugadores no hay swap y el tablero no se
        // re-ancla, pero eso es fin de partida y la pantalla de balance lo tapa en segundos.
        // NUNCA afecta a dinero/nick/asiento (esta animación es puramente visual) y se
        // auto-corrige. NO se blinda con snapshot único porque tocaría el camino sensible de
        // expulsiones a cambio de un caso extremo (2 caídas en ~500ms) e inocuo. (Auditoría
        // adversaria 8 lentes, jul-2026.)
        final java.util.List<JPanel> survivors = new java.util.ArrayList<>();
        final java.util.List<JPanel> leaving = new java.util.ArrayList<>();
        for (Player p : all) {
            if ((p instanceof RemotePlayer) && ((RemotePlayer) p).isExit()) {
                leaving.add((JPanel) p);
            } else {
                survivors.add((JPanel) p);
            }
        }

        final int m = survivors.size();
        final int n = all.length;
        if (m < 2 || m >= n || m >= ANCHORS.length || ANCHORS[m] == null
                || getWidth() <= 0 || getHeight() <= 0 || !isShowing()) {
            return; // nada que animar (o fuera del rango de tableros)
        }

        final java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        final javax.swing.Timer[] holder = new javax.swing.Timer[1];
        final java.util.List<FadeGhost> ghosts = new java.util.ArrayList<>();

        Helpers.GUIRunAndWait(() -> {
            try {
                // Congela el re-anclaje: a partir de aquí las posiciones las manda el tween.
                layout_frozen = true;

                final java.awt.Rectangle[] from = new java.awt.Rectangle[m];
                final java.awt.Rectangle[] to = new java.awt.Rectangle[m];
                for (int j = 0; j < m; j++) {
                    JPanel sv = survivors.get(j);
                    from[j] = sv.getBounds();
                    java.awt.Rectangle t = seatBoundsFor(m, j, sv.getPreferredSize());
                    to[j] = (t != null) ? t : from[j];
                }

                // Fantasma (snapshot) por cada saliente para desvanecerlo, y se oculta
                // el asiento real debajo.
                for (JPanel lv : leaving) {
                    if (lv.getWidth() <= 0 || lv.getHeight() <= 0) {
                        continue;
                    }
                    java.awt.image.BufferedImage snap = new java.awt.image.BufferedImage(
                            lv.getWidth(), lv.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = snap.createGraphics();
                    lv.paint(g);
                    g.dispose();
                    FadeGhost ghost = new FadeGhost(snap);
                    ghost.setBounds(lv.getBounds());
                    add(ghost, JLayeredPane.DRAG_LAYER);
                    ghosts.add(ghost);
                    lv.setVisible(false);
                }

                final long t0 = System.nanoTime();
                final javax.swing.Timer timer = new javax.swing.Timer(15, null);
                holder[0] = timer;
                timer.addActionListener(e -> {
                    long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                    double u = Math.min(1.0, (double) elapsed / Math.max(1, duration_ms));
                    double s = u * u * (3.0 - 2.0 * u); // smoothstep (arranque/frenada suaves)

                    for (int j = 0; j < m; j++) {
                        java.awt.Rectangle a = from[j];
                        java.awt.Rectangle b = to[j];
                        int x = (int) Math.round(a.x + (b.x - a.x) * s);
                        int y = (int) Math.round(a.y + (b.y - a.y) * s);
                        survivors.get(j).setBounds(x, y, a.width, a.height);
                    }
                    for (FadeGhost ghost : ghosts) {
                        ghost.setAlpha((float) (1.0 - u));
                        ghost.repaint();
                    }

                    if (u >= 1.0) {
                        timer.stop();
                        finished.countDown();
                    }
                });
                timer.start();

            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(DynamicTablePanel.class.getName())
                        .log(java.util.logging.Level.SEVERE, null, ex);
                finished.countDown();
            }
        });

        try {
            finished.await(GifLabel.GIF_BARRIER_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        // Limpieza: se quitan los fantasmas. Los supervivientes quedan en la posición
        // destino (mesa de M). NO se descongela el layout: este panel se descarta en
        // el swap posterior, y descongelar podría dispararse un doLayout que los
        // devolviera a la mesa de N (parpadeo) justo antes del swap.
        Helpers.GUIRunAndWait(() -> {
            for (FadeGhost ghost : ghosts) {
                remove(ghost);
            }
        });

        Helpers.GUIRun(() -> {
            if (holder[0] != null) {
                holder[0].stop();
            }
        });
    }

    // Componente efímero que pinta un snapshot (imagen) con opacidad variable, para
    // desvanecer un asiento que abandona la mesa.
    private static final class FadeGhost extends javax.swing.JComponent {

        private final java.awt.Image img;
        private volatile float alpha = 1.0f;

        FadeGhost(java.awt.Image img) {
            this.img = img;
            setOpaque(false);
        }

        void setAlpha(float a) {
            this.alpha = a;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
                g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            } finally {
                g2.dispose();
            }
        }
    }

    // Tras el zoom, los asientos cambian de tamaño preferido: forzamos una
    // revalidación para recolocarlos con el nuevo tamaño.
    @Override
    public void zoom(float factor, ConcurrentLinkedQueue<Long> notifier) {
        super.zoom(factor, notifier);
        Helpers.GUIRun(() -> {
            revalidate();
            repaint();
        });
    }

    // Sin layout manager, el tamaño preferido por defecto sería (0,0). Como el
    // tapete va al CENTER del content pane (lo estira el frame), devolvemos el
    // tamaño del contenedor si está disponible, con un valor de diseño de reserva.
    @Override
    public Dimension getPreferredSize() {
        Container parent = getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            return new Dimension(parent.getWidth(), parent.getHeight());
        }
        return new Dimension(1200, 750);
    }
}
