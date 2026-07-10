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

        // Asientos (0 = local, 1..n-1 = remotos).
        for (int i = 0; i < n; i++) {
            centerAt((JPanel) s[i], anchors[i][0] * W, anchors[i][1] * H);
        }

        // Comunitarias: SIEMPRE centradas en la mesa, pero por su FILA DE CARTAS, no
        // por los bounds del panel. El CommunityCardsPanel es más ancho que las
        // cartas (lleva el bote y los controles) y las cartas van alineadas a la
        // izquierda dentro de él, así que centrar los bounds del panel dejaba las
        // cartas desplazadas. Colocamos el panel de forma que el CENTRO de su
        // cards_panel caiga en (W/2, H/2).
        Dimension cd = community.getPreferredSize();
        community.setBounds(0, 0, cd.width, cd.height); // provisional, para que se coloquen los hijos
        community.doLayout();
        double off_x = cd.width / 2.0;
        double off_y = cd.height / 2.0;
        JPanel cards = community.getCards_panel();
        if (cards != null) {
            java.awt.Point p = javax.swing.SwingUtilities.convertPoint(
                    cards, cards.getWidth() / 2, cards.getHeight() / 2, community);
            off_x = p.x;
            off_y = p.y;
        }
        community.setBounds((int) Math.round(W / 2.0 - off_x),
                (int) Math.round(H / 2.0 - off_y), cd.width, cd.height);
    }

    // Coloca un componente con su tamaño preferido, centrado en (cx, cy).
    private static void centerAt(JPanel panel, double cx, double cy) {
        Dimension d = panel.getPreferredSize();
        panel.setBounds((int) Math.round(cx - d.width / 2.0),
                (int) Math.round(cy - d.height / 2.0), d.width, d.height);
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
