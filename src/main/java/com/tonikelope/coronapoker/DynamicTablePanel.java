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

    // Sesgo de reparto hacia los bordes LATERALES (izquierdo/derecho) frente al
    // superior. En una pantalla apaisada el borde superior es mucho más largo que
    // los laterales, así que un reparto por longitud pura amontonaría los asientos
    // arriba. Con este factor (>1) los laterales "pesan" más y atraen más asientos,
    // reproduciendo el reparto de los tableros fijos (p.ej. 6 jugadores → 2 izq / 1
    // arriba / 2 dcha; 10 → 3/3/3).
    private static final double SIDE_BIAS = 1.6;

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

        final int remotes = s.length - 1;
        if (remotes <= 0) {
            return;
        }

        // Tamaño nominal de un asiento remoto (son todos iguales) para trazar el
        // recorrido en "U" por el que se reparten. Se lee en cada layout, así que
        // respeta el zoom automáticamente.
        Dimension rd = ((JPanel) s[1]).getPreferredSize();
        final double half_w = rd.width / 2.0;
        final double half_h = rd.height / 2.0;

        // Coordenadas de los CENTROS de asiento sobre los tres bordes.
        final double x_left = EDGE_MARGIN + half_w;          // columna izquierda
        final double x_right = W - EDGE_MARGIN - half_w;     // columna derecha
        final double y_top = EDGE_MARGIN + half_h;           // fila superior
        final double y_bottom = H - EDGE_MARGIN - half_h;    // fondo de las columnas

        // Longitudes reales de cada tramo del recorrido (izq ↑, arriba →, dcha ↓).
        final double side_len = Math.max(1.0, y_bottom - y_top);
        final double top_len = Math.max(1.0, x_right - x_left);

        // Longitudes "efectivas" para el REPARTO: los laterales pesan más (SIDE_BIAS)
        // para que atraigan más asientos que el largo borde superior.
        final double eff_side = side_len * SIDE_BIAS;
        final double eff_top = top_len;
        final double eff_total = eff_side + eff_top + eff_side;

        for (int k = 0; k < remotes; k++) {

            // Posición del asiento a lo largo del recorrido efectivo (centrada en su
            // hueco → simétrico izquierda/derecha respecto al centro superior).
            double eff_dist = ((k + 0.5) / remotes) * eff_total;

            JPanel panel = (JPanel) s[k + 1];
            Dimension d = panel.getPreferredSize();
            double seat_cx;
            double seat_cy;

            if (eff_dist <= eff_side) {
                // Borde IZQUIERDO, de abajo hacia arriba. Pegado a la izquierda.
                double frac = eff_dist / eff_side;
                seat_cx = EDGE_MARGIN + d.width / 2.0;
                seat_cy = y_bottom - frac * side_len;
            } else if (eff_dist <= eff_side + eff_top) {
                // Borde SUPERIOR, de izquierda a derecha. Pegado arriba.
                double along = eff_dist - eff_side;
                seat_cx = x_left + along;
                seat_cy = EDGE_MARGIN + d.height / 2.0;
            } else {
                // Borde DERECHO, de arriba hacia abajo. Pegado a la derecha.
                double frac = (eff_dist - eff_side - eff_top) / eff_side;
                seat_cx = W - EDGE_MARGIN - d.width / 2.0;
                seat_cy = y_top + frac * side_len;
            }

            panel.setBounds((int) Math.round(seat_cx - d.width / 2.0),
                    (int) Math.round(seat_cy - d.height / 2.0), d.width, d.height);
        }
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
