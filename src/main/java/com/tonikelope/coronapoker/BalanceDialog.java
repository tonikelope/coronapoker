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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Pantalla final de timba: overlay a pantalla completa SOBRE el tapete (que se
 * sigue viendo a brillo normal detrás, vía transparencia por píxel). De arriba a
 * abajo (BorderLayout): barra de botones, "LA TIMBA HA TERMINADO" + fecha, el
 * resultado del jugador local en GIGANTE (relleno verde/rojo/gris con borde negro,
 * al estilo del overlay de coste de igualar) con el importe debajo, y un carrusel
 * horizontal de cajas de jugador (avatar + nick + resultado + stack + buyin) con
 * flechas de desplazamiento lateral cuando hay más cajas de las que caben. Todo se
 * auto-ajusta a la resolución (responsive).
 *
 * @author tonikelope
 */
public class BalanceDialog extends JDialog {

    // Relleno del texto/resultado según el balance (borde siempre negro).
    private static final Color WIN = new Color(0, 200, 60);
    private static final Color LOSE = new Color(220, 30, 30);
    private static final Color NEUTRAL = new Color(140, 140, 140);
    // Naranja de marca: el color del contador mientras rueda (antes del +/- final).
    private static final Color ORANGE = new Color(255, 102, 0);

    // Cajas claras sobre el tapete (como el boceto de referencia).
    private static final Color CARD_BG = new Color(248, 248, 248);
    private static final Color CARD_TEXT = new Color(25, 25, 25);
    private static final Color CARD_TEXT_DIM = new Color(110, 110, 110);

    private volatile boolean recover = false;

    // Altavoz (mute) de la esquina superior derecha de la barra (fin de timba). Icono claro
    // (blanco) sobre el tapete oscuro. Sin rueda de ajustes a proposito: durante la pantalla
    // final no se tocan ajustes (la timba ya termino), solo el mute global.
    private static final int SOUND_ICON_SZ = 36;
    private JLabel sound_icon;

    // Snapshot del tapete (solo si el sistema NO soporta transparencia por
    // píxel): se pinta como fondo opaco para conservar el aspecto "tapete".
    private BufferedImage table_snapshot = null;

    // Dimensiones derivadas de la altura de la pantalla (escalan con resolución).
    private int screen_w;
    private int screen_h;
    private int card_w;
    private int card_h;
    private int card_gap;
    private int avatar_sz;

    private JScrollPane cards_scroll;
    private ArrowButton left_arrow;
    private ArrowButton right_arrow;
    private final java.util.List<CardPanel> card_panels = new java.util.ArrayList<>();

    // Los 4 botones de la barra superior, para uniformar su tamaño (misma
    // anchura y altura entre todos) tras el auto-fit responsive.
    private final java.util.List<JButton> nav_buttons = new java.util.ArrayList<>();

    // Animación del importe del jugador local: un contador que sube/baja desde el buyin
    // total hasta el stack final (estilo recuento de puntuación de videojuego) y luego se
    // revela como +/- el neto. Solo si hay ganancia/pérdida (no en empate).
    private OutlinedLabel amount_label;
    private double anim_buyin;
    private double anim_stack;
    private double anim_ganancia;

    public boolean isRecover() {
        return recover;
    }

    public BalanceDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);

        screen_w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : 1280;
        screen_h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : 800;

        card_h = Math.max(170, Math.min(320, Math.round(screen_h * 0.30f)));
        card_w = Math.round(card_h * 0.80f);
        avatar_sz = Math.round(card_h * 0.42f);
        card_gap = 18;

        setupBackground(parent);

        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                if (table_snapshot != null) {
                    g.drawImage(table_snapshot, 0, 0, getWidth(), getHeight(), null);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        content.setOpaque(table_snapshot != null);
        setContentPane(content);

        // Arriba: barra de botones. Centro: "LA TIMBA HA TERMINADO" + fecha
        // centrados sobre el mensaje gigante (a su vez centrado en la franja).
        // Abajo: carrusel ENTERO (SOUTH siempre toma su alto preferido, nunca se
        // corta; el centro absorbe el espacio sobrante).
        content.add(buildNavBar(), BorderLayout.NORTH);
        content.add(buildCenter(), BorderLayout.CENTER);
        content.add(buildCardsRegion(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent evt) {
                startAmountAnimation();
            }

            @Override
            public void windowActivated(WindowEvent evt) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(BalanceDialog.this);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent evt) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }
        });

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        fitTaggedLabels(getContentPane());

        normalizeNavButtons();

        normalizeCardHeights();

        if (parent != null) {
            setBounds(parent.getBounds());
        }

        SwingUtilities.invokeLater(this::updateArrows);
    }

    // Transparencia por píxel para ver el tapete a brillo completo detrás. Si el
    // sistema no la soporta (o el frame está en fullscreen exclusivo), se captura
    // un snapshot del tapete y se pinta como fondo opaco (mismo aspecto, sin
    // translucidez real).
    private void setupBackground(java.awt.Frame parent) {
        try {
            GraphicsDevice dev = (parent != null ? parent.getGraphicsConfiguration() : getGraphicsConfiguration()).getDevice();
            if (dev.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                setBackground(new Color(0, 0, 0, 0));
                return;
            }
        } catch (Exception ex) {
            Logger.getLogger(BalanceDialog.class.getName()).log(Level.WARNING, "Per-pixel translucency unavailable, falling back to a table snapshot", ex);
        }

        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            try {
                BufferedImage img = new BufferedImage(parent.getWidth(), parent.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                parent.printAll(g);
                g.dispose();
                table_snapshot = img;
            } catch (Exception ex) {
                Logger.getLogger(BalanceDialog.class.getName()).log(Level.WARNING, "Could not snapshot the table for the balance overlay background", ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Barra de botones (arriba, repartidos a lo ancho).
    // -------------------------------------------------------------------------
    private JComponent buildNavBar() {
        JButton log_button = navButton(Translator.translate("log.registro_de_la_timba"), new Color(60, 63, 70));
        log_button.addActionListener((e) -> openLog());

        JButton stats_button = navButton(Translator.translate("ui.estadisticas"), new Color(255, 102, 0));
        stats_button.addActionListener((e) -> StatsDialog.showStats(this));

        JButton recover_button = navButton(GameFrame.getInstance().isPartida_local() ? Translator.translate("game.continuar_esta_timba") : Translator.translate("conn.reconectar_al_servidor"), new Color(0, 130, 0));
        recover_button.addActionListener((e) -> {
            recover = true;
            dispose();
        });

        JButton menu_button = navButton(Translator.translate("ui.menu_principal"), new Color(0, 153, 255));
        menu_button.addActionListener((e) -> dispose());

        JPanel row = new JPanel(new GridLayout(1, 4, 24, 0));
        row.setOpaque(false);
        for (JButton b : new JButton[]{menu_button, log_button, stats_button, recover_button}) {
            nav_buttons.add(b);
            JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            cell.setOpaque(false);
            cell.add(b);
            row.add(cell);
        }

        // Altavoz (mute) en la esquina superior derecha, a la derecha de los botones (misma
        // fila): discreto y donde se espera. Sin rueda: durante el fin de timba no se tocan
        // ajustes, solo el mute.
        JPanel line = new JPanel(new BorderLayout(20, 0));
        line.setOpaque(false);
        line.add(row, BorderLayout.CENTER);
        line.add(buildSoundCorner(), BorderLayout.EAST);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(34, 28, 0, 28));
        bar.add(line, BorderLayout.NORTH);
        return bar;
    }

    // Altavoz (mute rápido) en la esquina superior derecha, dentro de un CHIP con fondo
    // redondeado translúcido (NO transparente). Razón: la pantalla final es un overlay con
    // transparencia POR PÍXEL, y en esos overlays los píxeles TRANSPARENTES (el padding del
    // icono) DEJAN PASAR el ratón a la ventana de detrás -> el hover/click fallaba a ratos
    // (solo registraba sobre la forma sólida, pequeña, del altavoz). Un chip de píxeles
    // OPACOS hace que TODA su área capture el ratón de forma fiable, da un hit-area amplio y
    // una affordance de botón. El listener va en el icono Y en el chip (un clic en el icono
    // lo recibe el label, no el chip). sound.png/mute.png son blancos y resaltan sobre él.
    private JComponent buildSoundCorner() {
        sound_icon = new JLabel();
        refreshBalanceSoundIcon();

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                GameFrame.SONIDOS = !GameFrame.SONIDOS;
                Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");
                Helpers.savePropertiesFile();
                if (!GameFrame.SONIDOS) {
                    Audio.muteAll();
                } else {
                    Audio.unmuteAll();
                }
                refreshBalanceSoundIcon();
            }
        };
        sound_icon.addMouseListener(toggle);

        JPanel chip = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setBorder(BorderFactory.createEmptyBorder(7, 11, 7, 11));
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setToolTipText(Translator.translate("sound.click_para_activardesactivar_el_sonido"));
        chip.addMouseListener(toggle);
        chip.add(sound_icon);

        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.add(chip);
        return corner;
    }

    // Refleja el estado de SONIDOS en el icono del altavoz (sound/mute), como in-game.
    private void refreshBalanceSoundIcon() {
        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), SOUND_ICON_SZ, SOUND_ICON_SZ);
    }

    private JButton navButton(String text, Color bg) {
        FlatButton b = new FlatButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(15, 32, 15, 32));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Dialog", Font.BOLD, 22));
        // Auto-fit responsive: el texto se encoge para caber en su cuarto de la
        // barra (PCs antiguos / baja resolución no parten los botones).
        b.putClientProperty("fit.width", Math.max(40, screen_w / 4 - 110));
        return b;
    }

    private void openLog() {
        GameFrame.getInstance().getRegistro_dialog().setPreferredSize(new Dimension(Math.round(0.7f * GameFrame.getInstance().getWidth()), Math.round(0.7f * GameFrame.getInstance().getHeight())));
        GameFrame.getInstance().getRegistro_dialog().pack();
        GameFrame.getInstance().getRegistro_dialog().setLocationRelativeTo(this);
        GameFrame.getInstance().getRegistro_dialog().setModal(true);
        GameFrame.getInstance().getRegistro_dialog().setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Franja central (entre la barra de botones y el carrusel).
    // -------------------------------------------------------------------------
    // Franja central: el título de fin de timba + la fecha CENTRADOS verticalmente
    // en el hueco que queda sobre el mensaje gigante, y el mensaje gigante centrado
    // en la franja (entre la barra de botones y el carrusel). Filas con weighty
    // 1/0/1 -> el gigante queda centrado y el bloque de título, centrado encima.
    private JComponent buildCenter() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridy = 0;
        g.weighty = 1.0;
        g.anchor = GridBagConstraints.CENTER;
        center.add(buildTitleBlock(), g);

        g.gridy = 1;
        g.weighty = 0.0;
        center.add(buildHeroMessage(), g);

        // La cantidad, en la misma letra gigante, justo DEBAJO del mensaje (anclada
        // arriba del hueco para que quede cerca y no flotando en mitad del carrusel).
        g.gridy = 2;
        g.weighty = 1.0;
        g.anchor = GridBagConstraints.NORTH;
        center.add(buildAmount(), g);

        return center;
    }

    // "LA TIMBA HA TERMINADO" + fecha/hora/duración: fuente normal (sin contorno) y
    // color del fondo de tablero elegido (el de los contadores del tapete:
    // madera/negro* -> blanco, verde/azul/rojo -> su color). Apiladas a todo el
    // ancho con auto-fit, para cualquier resolución.
    private JComponent buildTitleBlock() {
        Color tapete_color = Color.WHITE;
        try {
            Color c = GameFrame.getInstance().getTapete().getCommunityCards().getColor_contadores();
            if (c != null) {
                tapete_color = c;
            }
        } catch (Exception ex) {
        }

        float subtitle_size = Math.max(22f, Math.min(90f, screen_h * 0.060f));
        float date_size = Math.max(16f, Math.min(48f, screen_h * 0.034f));
        int fit_w = Math.max(60, screen_w - 60);

        JLabel subtitle = new JLabel(Translator.translate("game.la_timba_ha_terminado"), SwingConstants.CENTER);
        subtitle.setForeground(tapete_color);
        subtitle.setFont(new Font("Dialog", Font.BOLD, Math.round(subtitle_size)));
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 24, 2, 24));
        subtitle.putClientProperty("fit.width", fit_w);

        JLabel date = new JLabel(Helpers.getFechaHoraActual() + "   (" + Helpers.seconds2FullTime(GameFrame.getInstance().getConta_tiempo_juego()) + ")", SwingConstants.CENTER);
        date.setForeground(tapete_color);
        date.setFont(new Font("Dialog", Font.PLAIN, Math.round(date_size)));
        date.setBorder(BorderFactory.createEmptyBorder(2, 16, 6, 16));
        date.putClientProperty("fit.width", fit_w);

        JPanel block = new JPanel(new GridBagLayout());
        block.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0;
        block.add(subtitle, g);
        g.gridy = 1;
        block.add(date, g);
        return block;
    }

    // Mensaje gigante del jugador local (sin importe): HAS GANADO /
    // HAS PERDIDO / NI GANAS NI PIERDES. Borde negro + relleno color,
    // auto-ajustado al ancho/alto reales (OutlinedLabel.paintComponent).
    private JComponent buildHeroMessage() {
        double ganancia = localGanancia();
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);

        String text;
        Color fill;
        if (cmp > 0) {
            text = Translator.translate("balance.has_ganado");
            fill = WIN;
        } else if (cmp < 0) {
            text = Translator.translate("balance.has_perdido");
            fill = LOSE;
        } else {
            text = Translator.translate("balance.empate");
            fill = NEUTRAL;
        }

        float hero_size = Math.max(40f, Math.min(300f, screen_h * 0.16f));

        OutlinedLabel hero = new OutlinedLabel(text, fill);
        hero.setFont(new Font("Dialog", Font.BOLD, Math.round(hero_size)));
        hero.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
        return hero;
    }

    // La cantidad del jugador local. Empieza mostrando el BUYIN total y se anima (en
    // startAmountAnimation) hasta el stack final; al aterrizar se revela como +/- el neto,
    // en la misma letra gigante y color (verde gana / rojo pierde). Vacía si es empate.
    private JComponent buildAmount() {
        double[] bs = localBuyinStack();
        double buyin = bs[0];
        double stack = bs[1];
        double ganancia = Helpers.doubleClean(stack - buyin);
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);

        float amount_size = Math.max(36f, Math.min(280f, screen_h * 0.15f));

        if (cmp == 0) {
            // Empate: no hay importe que mostrar (ni animación).
            OutlinedLabel empty = new OutlinedLabel("", NEUTRAL);
            empty.setFont(new Font("Dialog", Font.BOLD, Math.round(amount_size)));
            empty.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
            return empty;
        }

        // Arranca en naranja (el color del contador); el +/- final se revela en verde/rojo.
        OutlinedLabel amount = new OutlinedLabel(Helpers.money2String(buyin), ORANGE);
        amount.setFont(new Font("Dialog", Font.BOLD, Math.round(amount_size)));
        amount.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));

        amount_label = amount;
        anim_buyin = buyin;
        anim_stack = stack;
        anim_ganancia = ganancia;

        return amount;
    }

    // {buyin_total, stack_final} del jugador local (auditor: pasta[0]=stack, pasta[1]=buyin).
    private double[] localBuyinStack() {
        try {
            String nick = GameFrame.getInstance().getLocalPlayer().getNickname();
            Double[] pasta = GameFrame.getInstance().getCrupier().getAuditor().get(nick);
            if (pasta == null) {
                return new double[]{0, 0};
            }
            return new double[]{Helpers.doubleClean(pasta[1]), Helpers.doubleClean(pasta[0])};
        } catch (Exception ex) {
            return new double[]{0, 0};
        }
    }

    // Recuento animado del importe local: rueda desde el buyin hasta el stack con
    // desaceleración (ease-out cúbico), mantiene un instante el stack y revela el +/- neto.
    private void startAmountAnimation() {
        if (amount_label == null) {
            return;
        }

        final double from = anim_buyin;
        final double to = anim_stack;
        // 1.5s, en sincronia con la cortinilla de llenado de stacks (Crupier.STACK_FILL_MS).
        // La frenadita (ease-out cubico) y el parpadeo siguen intactos, son sello de esta pantalla.
        final long duration_ms = 1500;
        final long start_ms = System.currentTimeMillis();

        final String reveal_text = anim_ganancia > 0
                ? "+" + Helpers.money2String(anim_ganancia)
                : "-" + Helpers.money2String(anim_ganancia * -1);

        javax.swing.Timer roll = new javax.swing.Timer(16, null);
        roll.addActionListener((e) -> {
            double p = Math.min(1.0, (System.currentTimeMillis() - start_ms) / (double) duration_ms);

            if (p >= 1.0) {
                ((javax.swing.Timer) e.getSource()).stop();
                // Sin pausa: al llegar al stack se revela el neto +/- (verde/rojo) y parpadea.
                amount_label.setFill(anim_ganancia > 0 ? WIN : LOSE);
                amount_label.setText(reveal_text);
                blinkAmount();
                return;
            }

            double eased = 1.0 - Math.pow(1.0 - p, 3.0);
            double value = from + (to - from) * eased;
            amount_label.setText(Helpers.money2String(Helpers.doubleClean(value)));
        });
        roll.start();
    }

    // Parpadeo SOLO del importe al revelar el neto: alterna un flag de "no pintar" (que solo
    // repinta este label, sin remaquetar el resto del diálogo) y termina visible.
    private void blinkAmount() {
        if (amount_label == null) {
            return;
        }

        final int total = 6; // 3 ciclos apagar/encender
        final int[] count = {0};

        javax.swing.Timer blink = new javax.swing.Timer(130, null);
        blink.addActionListener((e) -> {
            count[0]++;
            amount_label.setBlank(count[0] % 2 == 1);
            if (count[0] >= total) {
                ((javax.swing.Timer) e.getSource()).stop();
                amount_label.setBlank(false);
            }
        });
        blink.start();
    }

    private double localGanancia() {
        try {
            String nick = GameFrame.getInstance().getLocalPlayer().getNickname();
            Double[] pasta = GameFrame.getInstance().getCrupier().getAuditor().get(nick);
            if (pasta == null) {
                return 0;
            }
            return Helpers.doubleClean(Helpers.doubleClean(pasta[0]) - Helpers.doubleClean(pasta[1]));
        } catch (Exception ex) {
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Carrusel de cajas (abajo) + flechas laterales.
    // -------------------------------------------------------------------------
    private JComponent buildCardsRegion() {
        CardsRow row = new CardsRow();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        final String local_nick = GameFrame.getInstance().getLocalPlayer().getNickname();

        // Orden de asientos (el mismo que recorre el auditor al cerrar cuentas).
        final ArrayList<String> seat_order = new ArrayList<>();
        for (Player p : GameFrame.getInstance().getJugadores()) {
            seat_order.add(p.getNickname());
        }

        Map<String, Double[]> auditor = GameFrame.getInstance().getCrupier().getAuditor();

        // El jugador local PRIMERO; el resto, en orden de asientos (quien ya no
        // esté sentado va al final, conservando su orden de iteración).
        ArrayList<String> nicks = new ArrayList<>(auditor.keySet());
        nicks.sort(Comparator.comparingInt((String n) -> {
            if (n.equals(local_nick)) {
                return -1;
            }
            int idx = seat_order.indexOf(n);
            return idx >= 0 ? idx : Integer.MAX_VALUE;
        }));

        row.add(Box.createHorizontalGlue());
        boolean first = true;
        for (String nick : nicks) {
            Double[] pasta = auditor.get(nick);
            if (pasta == null) {
                continue;
            }
            if (!first) {
                row.add(Box.createHorizontalStrut(card_gap));
            }
            first = false;
            double stack = Helpers.doubleClean(pasta[0]);
            double buyin = Helpers.doubleClean(pasta[1]);
            double ganancia = Helpers.doubleClean(stack - buyin);
            row.add(buildCard(nick, stack, buyin, ganancia));
        }
        row.add(Box.createHorizontalGlue());

        cards_scroll = new JScrollPane(row, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cards_scroll.setOpaque(false);
        cards_scroll.getViewport().setOpaque(false);
        cards_scroll.setBorder(BorderFactory.createEmptyBorder());
        cards_scroll.setViewportBorder(null);

        left_arrow = new ArrowButton(true);
        left_arrow.addActionListener((e) -> scrollCards(-(card_w + card_gap)));

        right_arrow = new ArrowButton(false);
        right_arrow.addActionListener((e) -> scrollCards(card_w + card_gap));

        JPanel region = new JPanel(new BorderLayout());
        region.setOpaque(false);
        // Mismo margen inferior que el superior de los botones (34px), para que el
        // carrusel quede simétrico respecto a la barra de arriba.
        region.setBorder(BorderFactory.createEmptyBorder(0, 0, 34, 0));
        region.add(left_arrow, BorderLayout.WEST);
        region.add(cards_scroll, BorderLayout.CENTER);
        region.add(right_arrow, BorderLayout.EAST);
        region.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateArrows();
            }
        });
        return region;
    }

    private JComponent buildCard(String nick, double stack, double buyin, double ganancia) {
        // Ancho fijo; el alto se UNIFORMA luego (normalizeCardHeights) al máximo de
        // todas, para que el carrusel tenga cajas idénticas sin cortar contenido.
        CardPanel card = new CardPanel(card_w);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setAlignmentY(Component.CENTER_ALIGNMENT);
        card_panels.add(card);

        int inner_w = card_w - 36;

        JLabel avatar = new JLabel();
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        setRoundedAvatar(avatar, nick, avatar_sz);
        card.add(avatar);
        card.add(Box.createVerticalStrut(10));

        JLabel nick_lbl = new JLabel(nick);
        nick_lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        nick_lbl.setForeground(CARD_TEXT);
        nick_lbl.setFont(new Font("Dialog", Font.BOLD, Math.max(14, Math.round(card_h * 0.085f))));
        nick_lbl.putClientProperty("fit.width", inner_w);
        card.add(nick_lbl);
        card.add(Box.createVerticalStrut(8));

        JLabel result_lbl = new JLabel(resultText(ganancia));
        result_lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        result_lbl.setForeground(resultColor(ganancia));
        result_lbl.setFont(new Font("Dialog", Font.BOLD, Math.max(15, Math.round(card_h * 0.10f))));
        result_lbl.putClientProperty("fit.width", inner_w);
        card.add(result_lbl);
        card.add(Box.createVerticalStrut(10));

        int stat_size = Math.max(12, Math.round(card_h * 0.06f));
        card.add(statRow(Translator.translate("balance.fichas"), Helpers.money2String(stack), stat_size));
        card.add(Box.createVerticalStrut(3));
        card.add(statRow(Translator.translate("stats.buyin"), Helpers.money2String(buyin), stat_size));

        return card;
    }

    private JComponent statRow(String label, String value, int size) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel l = new JLabel(label);
        l.setForeground(CARD_TEXT_DIM);
        l.setFont(new Font("Dialog", Font.PLAIN, size));

        JLabel v = new JLabel(value);
        v.setForeground(CARD_TEXT);
        v.setFont(new Font("Dialog", Font.BOLD, size + 2));

        p.add(l);
        p.add(v);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, v.getPreferredSize().height + 4));
        return p;
    }

    private String resultText(double ganancia) {
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);
        if (cmp > 0) {
            return Translator.translate("ui.gana_4") + " " + Helpers.money2String(ganancia);
        } else if (cmp < 0) {
            return Translator.translate("ui.pierde_2") + " " + Helpers.money2String(ganancia * -1);
        } else {
            return Translator.translate("ui.ni_gana_ni_pierde");
        }
    }

    private Color resultColor(double ganancia) {
        int cmp = Helpers.doubleSecureCompare(ganancia, 0f);
        return cmp > 0 ? WIN : cmp < 0 ? LOSE : NEUTRAL;
    }

    private void setRoundedAvatar(JLabel label, String nick, int size) {
        String avatar_path = GameFrame.getInstance().getNick2avatar().get(nick);

        Image img;
        if (avatar_path != null && !"".equals(avatar_path) && !"*".equals(avatar_path)) {
            img = new ImageIcon(avatar_path).getImage();
        } else if ("*".equals(avatar_path)) {
            img = new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage();
        } else {
            img = new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage();
        }

        ImageIcon icon = new ImageIcon(Helpers.makeImageRoundedCorner(highQualityScale(img, size), 20));
        label.setIcon(icon);
        label.setPreferredSize(new Dimension(size, size));
        label.setMaximumSize(new Dimension(size, size));
    }

    // Reescalado de avatar a la máxima calidad posible (interpolación bicúbica +
    // hints de calidad), pensado para AGRANDAR avatares pequeños sin el aliasing
    // de getScaledInstance(SCALE_SMOOTH). Garantiza la imagen origen ya cargada
    // (ImageIcon usa MediaTracker) antes de pintar en el lienzo destino.
    private static BufferedImage highQualityScale(Image src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2.drawImage(src, 0, 0, size, size, null);
        } finally {
            g2.dispose();
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Desplazamiento lateral por flechas.
    // -------------------------------------------------------------------------
    private void scrollCards(int dx) {
        if (cards_scroll == null) {
            return;
        }
        JViewport vp = cards_scroll.getViewport();
        if (vp.getView() == null) {
            return;
        }
        int max_x = Math.max(0, vp.getView().getWidth() - vp.getWidth());
        int nx = Math.max(0, Math.min(max_x, vp.getViewPosition().x + dx));
        vp.setViewPosition(new Point(nx, vp.getViewPosition().y));
        updateArrows();
    }

    private void updateArrows() {
        if (cards_scroll == null || left_arrow == null || right_arrow == null) {
            return;
        }
        JViewport vp = cards_scroll.getViewport();
        if (vp.getView() == null) {
            return;
        }
        int view_w = vp.getView().getWidth();
        int port_w = vp.getWidth();
        boolean overflow = view_w > port_w + 1;
        left_arrow.setVisible(overflow);
        right_arrow.setVisible(overflow);
        if (overflow) {
            int x = vp.getViewPosition().x;
            int max_x = view_w - port_w;
            left_arrow.setEnabled(x > 0);
            right_arrow.setEnabled(x < max_x);
        }
    }

    // Uniforma los 4 botones de la barra superior a un tamaño idéntico (misma
    // anchura Y altura entre todos), conservando el comportamiento responsive.
    // 1) Fuente común = la más pequeña de las cuatro tras el auto-fit por botón
    //    (fitTaggedLabels), de modo que el texto más largo siga cabiendo en su
    //    cuarto y todas compartan el mismo cuerpo de letra (de ahí, misma altura).
    // 2) Caja común = el máximo ancho/alto preferido de las cuatro, fijado en los
    //    tres tamaños (pref/min/max) para que el FlowLayout las pinte idénticas.
    // Todo deriva del tamaño de pantalla, así que en otra resolución cambian las
    // cuatro a la vez, pero siempre iguales entre sí.
    private void normalizeNavButtons() {
        if (nav_buttons.isEmpty()) {
            return;
        }
        float min_size = Float.MAX_VALUE;
        for (JButton b : nav_buttons) {
            min_size = Math.min(min_size, b.getFont().getSize2D());
        }
        for (JButton b : nav_buttons) {
            b.setFont(b.getFont().deriveFont(min_size));
        }
        int max_w = 0;
        int max_h = 0;
        for (JButton b : nav_buttons) {
            Dimension d = b.getPreferredSize();
            max_w = Math.max(max_w, d.width);
            max_h = Math.max(max_h, d.height);
        }
        Dimension uniform = new Dimension(max_w, max_h);
        for (JButton b : nav_buttons) {
            b.setPreferredSize(uniform);
            b.setMinimumSize(uniform);
            b.setMaximumSize(uniform);
        }
    }

    // Iguala el alto de TODAS las cajas al máximo (calculado con las fuentes ya
    // finalizadas tras updateFonts/fitTaggedLabels), para que el carrusel sea
    // perfectamente uniforme sin recortar el contenido de ninguna.
    private void normalizeCardHeights() {
        int max_h = 0;
        for (CardPanel c : card_panels) {
            max_h = Math.max(max_h, c.getPreferredSize().height);
        }
        for (CardPanel c : card_panels) {
            c.setUniformHeight(max_h);
        }
    }

    // -------------------------------------------------------------------------
    // Componentes de soporte.
    // -------------------------------------------------------------------------
    // Fila de cajas que rellena el viewport cuando todo cabe (las cajas quedan
    // centradas por los glue) y solo pasa a desplazarse cuando desbordan.
    private static final class CardsRow extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.width);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getParent().getWidth() >= getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    // Etiqueta con texto centrado pintado como contorno (borde negro) + relleno,
    // para que se lea sobre el tapete. Misma técnica que el overlay de coste de
    // igualar del tapete (TextLayout.getOutline: draw el halo, fill el relleno).
    private static final class OutlinedLabel extends JLabel {

        private static final float STROKE_RATIO = 0.06f;
        private final Color halo = new Color(0, 0, 0, 235);
        private Color fill;
        private boolean blank = false;

        OutlinedLabel(String text, Color fill) {
            super(text, SwingConstants.CENTER);
            this.fill = fill;
        }

        void setFill(Color c) {
            this.fill = c;
            repaint();
        }

        // Oculta/muestra el texto sin tocar el layout (solo repinta): para el parpadeo.
        void setBlank(boolean b) {
            this.blank = b;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            String text = getText();
            if (blank || text == null || text.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                java.awt.Insets ins = getInsets();
                double avail_w = (getWidth() - ins.left - ins.right) * 0.98;
                double avail_h = getHeight() - ins.top - ins.bottom;

                Font base = getFont();
                java.awt.font.FontRenderContext frc = g2.getFontRenderContext();

                // Auto-fit responsive: encoge la fuente para que el texto quepa
                // SIEMPRE en el ancho (y alto) reales, sea cual sea la resolución.
                TextLayout probe = new TextLayout(text, base, frc);
                double tw = probe.getAdvance();
                double th = probe.getAscent() + probe.getDescent();
                double scale = 1.0;
                if (tw > avail_w && tw > 0) {
                    scale = avail_w / tw;
                }
                if (th * scale > avail_h && th > 0) {
                    scale = Math.min(scale, avail_h / th);
                }
                Font font = scale < 1.0 ? base.deriveFont((float) Math.max(8.0, base.getSize2D() * scale)) : base;

                TextLayout tl = new TextLayout(text, font, frc);
                Rectangle2D b = tl.getBounds();
                double x = (getWidth() - b.getWidth()) / 2.0 - b.getX();
                double y = (getHeight() - b.getHeight()) / 2.0 - b.getY();
                java.awt.Shape outline = tl.getOutline(AffineTransform.getTranslateInstance(x, y));

                float stroke = Math.max(2f, font.getSize2D() * STROKE_RATIO);
                g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(halo);
                g2.draw(outline);
                g2.setColor(fill);
                g2.fill(outline);
            } finally {
                g2.dispose();
            }
        }
    }

    // Flecha de desplazamiento: triángulo blanco relleno pintado a mano (la
    // fuente de la UI no garantiza glifos de flecha) sobre un disco oscuro
    // semitransparente. Apunta a izquierda o derecha.
    private static final class ArrowButton extends JButton {

        private final boolean left;

        ArrowButton(boolean left) {
            super();
            this.left = left;
            setPreferredSize(new Dimension(84, 84));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int d = Math.min(w, h) - 14;
                int cx = w / 2;
                int cy = h / 2;
                boolean en = isEnabled();

                g2.setColor(new Color(0, 0, 0, en ? 140 : 50));
                g2.fillOval(cx - d / 2, cy - d / 2, d, d);

                int tw = d / 4;
                int th = d / 3;
                int[] xs;
                int[] ys;
                if (left) {
                    xs = new int[]{cx + tw / 2, cx + tw / 2, cx - tw};
                    ys = new int[]{cy - th, cy + th, cy};
                } else {
                    xs = new int[]{cx - tw / 2, cx - tw / 2, cx + tw};
                    ys = new int[]{cy - th, cy + th, cy};
                }
                g2.setColor(new Color(255, 255, 255, en ? 240 : 90));
                g2.fillPolygon(xs, ys, 3);
            } finally {
                g2.dispose();
            }
        }
    }

    // Botón plano de la barra superior: pinta a mano un rectángulo redondeado con
    // el color de fondo (sin el relleno opaco/borde raro del L&F). Fuera de las
    // esquinas queda transparente -> se ve el tapete.
    private static final class FlatButton extends JButton {

        FlatButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 18;

                Color bg = getBackground();
                if (getModel().isPressed()) {
                    bg = bg.darker();
                } else if (getModel().isRollover()) {
                    bg = bg.brighter();
                }
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(getForeground());
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                int tx = (w - fm.stringWidth(t)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(t, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    // Caja de jugador: relleno redondeado limpio (esquinas transparentes -> se ve
    // el tapete), todas idénticas (sin borde especial). Ancho fijo; el alto lo fija
    // normalizeCardHeights al máximo de todas para que el carrusel sea uniforme.
    private static final class CardPanel extends JPanel {

        private final int fixed_width;
        private int uniform_height = 0;

        CardPanel(int fixed_width) {
            this.fixed_width = fixed_width;
            setOpaque(false);
        }

        void setUniformHeight(int h) {
            this.uniform_height = h;
        }

        @Override
        public Dimension getPreferredSize() {
            int h = uniform_height > 0 ? uniform_height : super.getPreferredSize().height;
            return new Dimension(fixed_width, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int arc = 30;
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }
    }

    // Encoge la fuente de un JLabel/JButton etiquetado con "fit.width" para que su
    // texto quepa en ese ancho. Se llama tras updateFonts (familia final) para que
    // el ajuste sea exacto en cualquier resolución.
    private void fitTaggedLabels(Component c) {
        if (c instanceof JComponent) {
            Object w = ((JComponent) c).getClientProperty("fit.width");
            if (w instanceof Integer) {
                String text = (c instanceof JLabel) ? ((JLabel) c).getText()
                        : (c instanceof javax.swing.AbstractButton) ? ((javax.swing.AbstractButton) c).getText() : null;
                if (text != null) {
                    fitTextFont(c, text, (Integer) w);
                }
            }
        }
        if (c instanceof java.awt.Container) {
            for (Component ch : ((java.awt.Container) c).getComponents()) {
                fitTaggedLabels(ch);
            }
        }
    }

    private static void fitTextFont(Component c, String text, int max_width) {
        if (text.isEmpty() || max_width <= 0) {
            return;
        }
        Font f = c.getFont();
        int tw = c.getFontMetrics(f).stringWidth(text);
        if (tw > max_width) {
            c.setFont(f.deriveFont(Math.max(9f, f.getSize2D() * max_width / (float) tw)));
        }
    }
}
