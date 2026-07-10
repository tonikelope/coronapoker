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

    // Margen (px) entre el borde exterior de los asientos extremos (arriba, abajo
    // y laterales) y el borde del tapete, para que no queden pegados ni recortados.
    private static final int EDGE_MARGIN = 12;

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

    // Colocado geométrico: comunitarias al centro de la mesa y los N asientos
    // repartidos sobre una elipse inscrita en el tapete. Se llama en cada
    // validación (resize del tapete, revalidate tras zoom). NO toca los overlays
    // de las capas superiores (fastbuttons, central_label, etc.): esos los
    // posiciona la clase base por su cuenta.
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

        // Radios de la elipse: se descuenta el semi-tamaño MAYOR de entre todos los
        // asientos (para que ninguno se recorte) más el margen, de modo que los
        // asientos de los extremos queden a EDGE_MARGIN del borde del tapete.
        int max_half_w = 0;
        int max_half_h = 0;
        for (Player p : s) {
            Dimension d = ((JPanel) p).getPreferredSize();
            max_half_w = Math.max(max_half_w, d.width / 2);
            max_half_h = Math.max(max_half_h, d.height / 2);
        }
        final double rx = Math.max(1.0, cx - max_half_w - EDGE_MARGIN);
        final double ry = Math.max(1.0, cy - max_half_h - EDGE_MARGIN);

        final int n = s.length;
        for (int i = 0; i < n; i++) {
            // θ = 90° (abajo) para el local, + i·(360°/n) en sentido antihorario.
            double theta = Math.PI / 2.0 + i * (2.0 * Math.PI / n);
            double seat_cx = cx + rx * Math.cos(theta);
            double seat_cy = cy + ry * Math.sin(theta);
            JPanel panel = (JPanel) s[i];
            Dimension d = panel.getPreferredSize();
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
