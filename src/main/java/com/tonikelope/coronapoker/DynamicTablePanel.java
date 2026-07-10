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
 * Tablero ÚNICO que coloca a los N jugadores por geometría (elipse) en vez de
 * usar un .form fijo por cada número de jugadores (TablePanel2..TablePanel10).
 *
 * El jugador local va SIEMPRE abajo-centro y los remotos se reparten en orden
 * alrededor del óvalo con la misma fórmula angular que reproduce el orden de
 * asientos de los tableros fijos (contrasentido a las agujas del reloj: sube por
 * la izquierda, cruza por arriba y baja por la derecha), así la disposición se
 * siente igual que la de siempre.
 *
 * Toda la lógica (animaciones de reparto/fichas, overlays, zoom, autoZoom,
 * pintado del tapete) vive en la clase base TablePanel y es geometría-agnóstica
 * (lee posiciones reales en pantalla), así que este panel solo tiene que crear
 * los asientos y colocarlos: el resto funciona sin tocar nada.
 *
 * @author tonikelope
 */
public class DynamicTablePanel extends TablePanel {

    // Margen (px) entre el borde exterior de los asientos y el borde del tapete.
    // Pequeño: los asientos van PEGADOS a los bordes (como los tableros fijos).
    private static final int EDGE_MARGIN = 8;

    // Hueco (px) entre la columna lateral y el primer asiento de la fila superior,
    // para que no se solapen en las esquinas.
    private static final int SEAT_GAP = 14;

    // Reparto de los asientos REMOTOS por número de jugadores (índice = nº de
    // jugadores 2..10): {columna_izquierda, fila_superior, columna_derecha}. Extraído
    // de los 9 tableros fijos originales (TablePanel2..TablePanel10) para reproducir
    // su disposición exacta. Es simétrico (izquierda = derecha). La suma es siempre
    // nº de jugadores − 1 (todos los remotos).
    private static final int[][] SEAT_DISTRIBUTION = {
        null, null, // 0 y 1 no se usan
        {0, 1, 0}, // 2:  1 arriba
        {1, 0, 1}, // 3:  1 izq, 1 dcha
        {1, 1, 1}, // 4:  1 izq, 1 arriba, 1 dcha
        {1, 2, 1}, // 5:  1 izq, 2 arriba, 1 dcha
        {2, 1, 2}, // 6:  2 izq, 1 arriba, 2 dcha
        {2, 2, 2}, // 7:  2 izq, 2 arriba, 2 dcha
        {2, 3, 2}, // 8:  2 izq, 3 arriba, 2 dcha
        {3, 2, 3}, // 9:  3 izq, 2 arriba, 3 dcha
        {3, 3, 3}, // 10: 3 izq, 3 arriba, 3 dcha
    };

    private volatile CommunityCardsPanel communityCards;
    private volatile LocalPlayer localPlayer;

    // Asientos en orden de colocación: [0] = local, [1..n-1] = remotos. doLayout
    // los reparte sobre la elipse en este orden.
    private volatile Player[] seats;

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

    // Colocado geométrico: comunitarias al centro, el jugador local pegado al borde
    // inferior y los N-1 remotos repartidos sobre el perímetro en "U" (borde
    // izquierdo → superior → derecho, dejando la franja de abajo para el local),
    // cada uno PEGADO a su borde. Se llama en cada validación (resize del tapete,
    // revalidate tras zoom). NO toca los overlays de las capas superiores
    // (fastbuttons, central_label, etc.): esos los posiciona la clase base.
    @Override
    public void doLayout() {

        final Player[] s = seats;
        final CommunityCardsPanel community = communityCards;

        final int W = getWidth();
        final int H = getHeight();

        if (s == null || community == null || W <= 0 || H <= 0) {
            return;
        }

        final double cx = W / 2.0;
        final double cy = H / 2.0;

        // Comunitarias centradas en la mesa.
        Dimension cd = community.getPreferredSize();
        community.setBounds((int) Math.round(cx - cd.width / 2.0),
                (int) Math.round(cy - cd.height / 2.0), cd.width, cd.height);

        // Jugador local: pegado al borde inferior, centrado horizontalmente.
        JPanel local = (JPanel) s[0];
        Dimension ld = local.getPreferredSize();
        local.setBounds((int) Math.round(cx - ld.width / 2.0),
                H - EDGE_MARGIN - ld.height, ld.width, ld.height);

        final int n = s.length;
        final int remotes = n - 1;
        if (remotes <= 0) {
            return;
        }

        // Reparto de los remotos por bordes según el tablero original de N jugadores.
        int[] dist = (n < SEAT_DISTRIBUTION.length) ? SEAT_DISTRIBUTION[n] : null;
        if (dist == null) {
            return;
        }
        final int left = dist[0];
        final int top = dist[1];
        final int right = dist[2];

        // Tamaño nominal de un asiento remoto (son todos iguales); se lee en cada
        // layout, así que respeta el zoom automáticamente.
        Dimension rd = ((JPanel) s[1]).getPreferredSize();
        final double half_w = rd.width / 2.0;
        final double half_h = rd.height / 2.0;

        // Banda vertical de las columnas laterales (de arriba a abajo, pegadas) y
        // banda horizontal de la fila superior (pegada arriba, retranqueada tras las
        // columnas para no chocar en las esquinas).
        final double y1 = EDGE_MARGIN + half_h;
        final double y2 = H - EDGE_MARGIN - half_h;
        final double span_y = Math.max(1.0, y2 - y1);

        final double x_col_left = EDGE_MARGIN + half_w;
        final double x_col_right = W - EDGE_MARGIN - half_w;
        final double tx1 = (left > 0) ? x_col_left + 2 * half_w + SEAT_GAP : x_col_left;
        final double tx2 = (right > 0) ? x_col_right - 2 * half_w - SEAT_GAP : x_col_right;
        final double span_x = Math.max(1.0, tx2 - tx1);

        // Los remotos van en ORDEN del array (r1, r2, ...) recorriendo el anillo en
        // sentido antihorario desde el local: primero la columna IZQUIERDA de abajo
        // hacia arriba, luego la fila SUPERIOR de izquierda a derecha, y por último
        // la columna DERECHA de arriba hacia abajo. Reproduce el orden de los
        // tableros fijos.
        int idx = 1;

        // Columna IZQUIERDA (de abajo hacia arriba).
        for (int j = 0; j < left; j++) {
            double frac = (j + 0.5) / left; // 0 = abajo del todo
            double seat_cy = y2 - frac * span_y;
            placeSeat((JPanel) s[idx++], EDGE_MARGIN, seat_cy, true);
        }

        // Fila SUPERIOR (de izquierda a derecha).
        for (int j = 0; j < top; j++) {
            double frac = (top == 1) ? 0.5 : (j + 0.5) / top;
            double seat_cx = tx1 + frac * span_x;
            placeSeatTop((JPanel) s[idx++], seat_cx, EDGE_MARGIN);
        }

        // Columna DERECHA (de arriba hacia abajo).
        for (int j = 0; j < right; j++) {
            double frac = (j + 0.5) / right; // 0 = arriba del todo
            double seat_cy = y1 + frac * span_y;
            placeSeat((JPanel) s[idx++], EDGE_MARGIN, seat_cy, false);
        }
    }

    // Coloca un asiento de columna lateral pegado al borde izquierdo (left=true) o
    // derecho (left=false), centrado verticalmente en seat_cy.
    private void placeSeat(JPanel panel, int margin, double seat_cy, boolean left) {
        Dimension d = panel.getPreferredSize();
        int x = left ? margin : (getWidth() - margin - d.width);
        panel.setBounds(x, (int) Math.round(seat_cy - d.height / 2.0), d.width, d.height);
    }

    // Coloca un asiento de la fila superior pegado al borde de arriba, centrado
    // horizontalmente en seat_cx.
    private void placeSeatTop(JPanel panel, double seat_cx, int margin) {
        Dimension d = panel.getPreferredSize();
        panel.setBounds((int) Math.round(seat_cx - d.width / 2.0), margin, d.width, d.height);
    }

    // Tras el zoom, los asientos cambian de tamaño preferido: forzamos una
    // revalidación para recolocarlos sobre la elipse con el nuevo tamaño.
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
