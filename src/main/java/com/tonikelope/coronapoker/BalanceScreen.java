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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Pantalla final de timba: overlay a pantalla completa montado sobre el glassPane
 * del GameFrame, ENCIMA del tapete real (que se ve a través por transparencia de
 * COMPONENTE Swing, sin depender del compositor del sistema como el antiguo diálogo
 * con transparencia por píxel de ventana). De arriba a abajo (BorderLayout): barra
 * de botones, "LA TIMBA HA TERMINADO" + fecha, el resultado del jugador local en
 * GIGANTE (relleno verde/rojo/gris con borde negro, al estilo del overlay de coste
 * de igualar) con el importe debajo, y un carrusel horizontal de cajas de jugador
 * (avatar + nick + resultado + stack + buyin) con flechas de desplazamiento lateral
 * cuando hay más cajas de las que caben. Todo se auto-ajusta a la resolución
 * (responsive). GameFrame lo monta/desmonta y espera la elección del usuario
 * (continuar/menú) por un CountDownLatch, replicando la semántica del antiguo
 * diálogo modal sin usar una ventana.
 *
 * @author tonikelope
 */
public class BalanceScreen extends JPanel {

    // Relleno del texto/resultado según el balance (borde siempre negro).
    private static final Color WIN = new Color(0, 200, 60);
    private static final Color LOSE = new Color(220, 30, 30);
    private static final Color NEUTRAL = new Color(140, 140, 140);

    // Cajas claras sobre el tapete (como el boceto de referencia).
    private static final Color CARD_BG = new Color(248, 248, 248);
    private static final Color CARD_TEXT = new Color(25, 25, 25);
    private static final Color CARD_TEXT_DIM = new Color(110, 110, 110);

    private volatile boolean recover = false;

    // Altavoz (mute) a la derecha de la barra de botones (fin de timba). Icono claro
    // (blanco) sobre el tapete oscuro. Sin rueda de ajustes a proposito: durante la pantalla
    // final no se tocan ajustes (la timba ya termino), solo el mute global. El chip se
    // dimensiona CUADRADO con la MISMA altura que los botones (normalizeNavButtons), y el
    // tamaño del icono se deriva de esa altura.
    private static final int SOUND_ICON_SZ = 36;
    private JLabel sound_icon;
    private JComponent sound_chip;
    private int sound_icon_sz = SOUND_ICON_SZ;

    // Callback que GameFrame instala para despertar del CountDownLatch cuando el
    // jugador elige salir (continuar la timba / menú principal). Sustituye al cierre
    // del antiguo diálogo modal.
    private final Runnable on_close;

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

    // Timers Swing de la animación del importe (recuento + parpadeo del revelado). Se
    // guardan para poder CORTARLOS en seco al elegir salir (menú principal / continuar)
    // mientras el contador rueda: sus repintados por frame son caros (OutlinedLabel
    // recalcula el contorno del texto con TextLayout.getOutline en cada tick, a pantalla
    // completa) y, si siguen vivos tras el dispose, acaparan el EDT y retrasan el teardown
    // (RESET_GAME) hasta que la animación acaba sola. Detenerlos hace la salida instantánea.
    private javax.swing.Timer amount_roll_timer;
    private javax.swing.Timer amount_blink_timer;

    // Captura de la pantalla final (ajuste GameFrame.SCREENSHOT_FIN_TIMBA, por defecto activo). Se
    // toma automáticamente JUSTO al terminar el contador de dinero (fin del parpadeo del neto +/-);
    // pero si el jugador vuelve al menú principal ANTES de que termine, se toma en ese momento. Una
    // sola captura, gane quien gane la carrera (idempotencia vía screenshot_done). Todo en el EDT.
    // El timer one-shot solo interviene en el EMPATE (no hay contador que animar) y se cancela si el
    // jugador sale antes de que dispare.
    private javax.swing.Timer amount_screenshot_timer;
    private boolean screenshot_done = false;

    // Las tres piezas apiladas de la franja central, para fijar su mínimo/máximo
    // vertical una vez finalizadas las fuentes (finalizeCenterSizing): el bloque de
    // título es RÍGIDO (no se comprime nunca -> la fila de la fecha no se recorta en
    // resoluciones bajas) y el mensaje gigante + el importe son los que absorben el
    // déficit encogiendo (se auto-reescalan a su caja, OutlinedLabel).
    private JComponent title_block;
    private JComponent hero_label;
    private JComponent amount_component;

    private double anim_buyin;
    private double anim_stack;
    private double anim_ganancia;

    public boolean isRecover() {
        return recover;
    }

    public BalanceScreen(java.awt.Frame parent, Runnable on_close) {
        super();

        this.on_close = on_close;

        // El SFX del contador (balance_count.wav) va en lockstep con la animacion
        // del importe (startAmountAnimation). Se precarga aqui, FUERA del camino
        // sync-critico y off-EDT, para que la reproduccion arranque instantanea sobre
        // una linea ya abierta. Si se abre una linea nueva en el momento de animar,
        // ese open() se atasca cuando el dispositivo esta ocupado (p.ej. justo tras el
        // teardown de audio de la salida, mientras las lineas del tablero aun se
        // liberan), dejando la animacion muda y el sonido cayendo tarde. La
        // construccion del overlay (avatares, layout) da tiempo de sobra a que la
        // linea abra antes de startAnimations. Mismo patron que shuffle.wav.
        Helpers.threadRun(() -> Audio.preloadWav("misc/balance_count.wav"));

        // Transparente: el fondo es el tapete real que se ve a través del glassPane.
        setOpaque(false);
        setLayout(new BorderLayout());

        screen_w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : 1280;
        screen_h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : 800;

        card_h = Math.max(170, Math.min(320, Math.round(screen_h * 0.30f)));
        card_w = Math.round(card_h * 0.80f);
        avatar_sz = Math.round(card_h * 0.42f);
        card_gap = 18;

        // Arriba: barra de botones. Centro: "LA TIMBA HA TERMINADO" + fecha
        // centrados sobre el mensaje gigante (a su vez centrado en la franja).
        // Abajo: carrusel ENTERO (SOUTH siempre toma su alto preferido, nunca se
        // corta; el centro absorbe el espacio sobrante).
        add(buildNavBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildCardsRegion(), BorderLayout.SOUTH);

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        fitTaggedLabels(this);

        // Tras finalizar las fuentes (updateFonts/fitTaggedLabels cambian los tamaños
        // preferidos): fija el reparto vertical de la franja central para que la fila de
        // la fecha no se recorte en resoluciones bajas (ver finalizeCenterSizing).
        finalizeCenterSizing();

        normalizeNavButtons();

        normalizeCardHeights();

        SwingUtilities.invokeLater(this::updateArrows);
    }

    // Arranca la animación del importe (antes en windowOpened). La invoca GameFrame
    // tras montar el overlay en el glassPane y hacerlo visible.
    public void startAnimations() {
        startAmountAnimation();
        // Empate: no hay contador de dinero (amount_label == null, sin animación). La captura
        // automática se programa con un breve margen para que el overlay esté ya maquetado/pintado.
        // En ganancia/pérdida la dispara el fin del contador (blinkAmount), no un timer.
        if (amount_label == null) {
            scheduleAutoScreenshot(700);
        }
    }

    // Suelta los recursos del overlay (antes en windowClosed). La invoca GameFrame
    // una vez resuelta la elección del usuario, antes de desmontar el glassPane:
    // corta los timers de la animación (sus repintados por frame acaparan el EDT y
    // retrasarían el teardown), suelta la linea precargada del SFX del contador y
    // cierra el diálogo de estadísticas si quedó abierto (es ownerless, no se cierra
    // con nosotros; true = restaura el loop de música que silenció, ya que el control
    // vuelve a la pantalla en curso).
    public void cleanup() {
        stopAmountAnimation();
        Audio.closePreloadedWav("misc/balance_count.wav");
        StatsDialog.disposeIfOpen(true);
    }

    // -------------------------------------------------------------------------
    // Barra de botones (arriba, repartidos a lo ancho).
    // -------------------------------------------------------------------------
    private JComponent buildNavBar() {
        JButton log_button = navButton(Translator.translate("log.registro_de_la_timba"), scaledIcon("/images/menu/log2.png", 28));
        log_button.addActionListener((e) -> openLog());

        JButton stats_button = navButton(Translator.translate("ui.estadisticas"), scaledIcon("/images/stats.png", 28));
        stats_button.addActionListener((e) -> StatsDialog.showStats(this));

        JButton recover_button = navButton(GameFrame.getInstance().isPartida_local() ? Translator.translate("game.continuar_esta_timba") : Translator.translate("conn.reconectar_al_servidor"), scaledIcon("/images/continue.png", 28));
        recover_button.addActionListener((e) -> {
            stopAmountAnimation();
            recover = true;
            if (on_close != null) {
                on_close.run();
            }
        });

        JButton menu_button = navButton(Translator.translate("ui.menu_principal"), whiteScaledIcon("/images/exit2.png", 28));
        menu_button.addActionListener((e) -> {
            // Respaldo de la captura: si el jugador vuelve al menú principal ANTES de que termine el
            // contador de dinero (aún no se tomó la captura automática), se toma AQUÍ para que la
            // timba quede igualmente registrada. Si el contador ya terminó, es no-op (idempotente).
            // Render en el EDT con el overlay aún montado, ANTES de disparar el teardown vía on_close.
            // NO se captura al "continuar la timba" (recover_button): la timba no termina.
            takeBalanceScreenshot();
            stopAmountAnimation();
            recover = false;
            if (on_close != null) {
                on_close.run();
            }
        });

        JPanel row = new JPanel(new GridLayout(1, 4, 24, 0));
        row.setOpaque(false);
        for (JButton b : new JButton[]{menu_button, log_button, stats_button, recover_button}) {
            nav_buttons.add(b);
            JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            cell.setOpaque(false);
            cell.add(b);
            row.add(cell);
        }

        // Altavoz (mute) a la derecha de los botones, en su MISMA fila y alineado con ellos
        // (mismo alto, cuadrado): discreto y donde se espera. Sin rueda: durante el fin de
        // timba no se tocan ajustes, solo el mute.
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
    // redondeado translúcido (NO transparente): da un hit-area amplio y una affordance de
    // botón, y el icono blanco (sound.png/mute.png) resalta sobre él. El listener va en el
    // icono Y en el chip (un clic sobre el icono lo recibe el label, no el chip).
    private JComponent buildSoundCorner() {
        sound_icon = new JLabel();
        refreshBalanceSoundIcon();

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (!Helpers.isRealClick(e)) {
                    return;
                }
                // setSonidos hace el flip + persiste + mute/unmute (y refresca los iconos de
                // altavoz que existan); aquí solo refrescamos el NUESTRO (que no conoce).
                GameFrame.setSonidos(!GameFrame.SONIDOS);
                refreshBalanceSoundIcon();
            }
        };
        sound_icon.addMouseListener(toggle);

        // GridBagLayout: centra el icono dentro del chip cuadrado (H y V). El tamaño
        // cuadrado (= alto de los botones) lo fija normalizeNavButtons; aquí solo se
        // construye con la misma arc (24) que los botones para que parezca uno más.
        JPanel chip = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setToolTipText(Translator.translate("sound.click_para_activardesactivar_el_sonido"));
        chip.addMouseListener(toggle);
        chip.add(sound_icon);
        sound_chip = chip;

        // GridBagLayout centra el chip VERTICALMENTE dentro del EAST (que toma todo el alto
        // de la fila), de modo que queda alineado con los botones aunque la fila creciera.
        JPanel corner = new JPanel(new GridBagLayout());
        corner.setOpaque(false);
        corner.add(chip);
        return corner;
    }

    // Refleja el estado de SONIDOS en el icono del altavoz (sound/mute), como in-game. El
    // tamaño (sound_icon_sz) lo deriva normalizeNavButtons del alto de los botones.
    private void refreshBalanceSoundIcon() {
        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), sound_icon_sz, sound_icon_sz);
    }

    private JButton navButton(String text, javax.swing.Icon icon) {
        // Estilo "cristal" (glassmorphism) IDÉNTICO a los botones neutrales de la pantalla de
        // inicio (crear/unirse): cristal negro translúcido redondeado que deja ver el tapete, y
        // al pasar el ratón solo sube la opacidad + brillo, SIN halo de color (accent = null).
        // Antes cada botón pasaba su propio color como accent y salía un borde/halo de color en
        // hover distinto por botón -> el autor lo quería idéntico a inicio. setUI antes de
        // setBorder para que gane nuestro padding (installUI pone uno).
        JButton b = new JButton(text);
        b.setUI(new GlassButtonUI(null, false, false, 0.70f, 24));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(15, 26, 15, 26));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setFont(new Font("Dialog", Font.BOLD, 22));
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(9);
        }
        // Auto-fit responsive: el texto se encoge para caber en su cuarto de la
        // barra (PCs antiguos / baja resolución no parten los botones).
        b.putClientProperty("fit.width", Math.max(40, screen_w / 4 - 118));
        return b;
    }

    // Icono escalado desde recursos (para los botones cristal de la barra superior).
    private static javax.swing.ImageIcon scaledIcon(String resource, int size) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(BalanceScreen.class.getResource(resource));
            return new javax.swing.ImageIcon(src.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    // Igual, pero tiñe de BLANCO la silueta (conserva alfa): para iconos de línea oscura que
    // sobre el cristal oscuro quedarían invisibles (p. ej. la puerta de salida de MENÚ PRINCIPAL).
    private static javax.swing.ImageIcon whiteScaledIcon(String resource, int size) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(BalanceScreen.class.getResource(resource));
            java.awt.image.BufferedImage w = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int a = (src.getRGB(x, y) >>> 24) & 0xFF;
                    w.setRGB(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            return new javax.swing.ImageIcon(w.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
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
    // Franja central: el título de fin de timba + la fecha sobre el mensaje gigante,
    // con la cantidad justo debajo (entre la barra de botones y el carrusel).
    //
    // BoxLayout vertical con pegamentos (glue): el sobrante de la franja lo absorben
    // los glue (centran el bloque de título arriba y dejan hueco bajo el importe), y
    // el DÉFICIT (resoluciones bajas: la franja no da para todo) lo absorben SOLO el
    // mensaje gigante y el importe encogiendo —se auto-reescalan a su caja—, mientras
    // el bloque de título permanece RÍGIDO (su mínimo = su preferido, fijado en
    // finalizeCenterSizing). Antes, con GridBagLayout y weighty 1/0/1, el déficit caía
    // sobre la fila del título y RECORTABA la fecha (un JLabel plano no se reescala).
    private JComponent buildCenter() {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        title_block = buildTitleBlock();
        title_block.setAlignmentX(Component.CENTER_ALIGNMENT);

        hero_label = buildHeroMessage();
        hero_label.setAlignmentX(Component.CENTER_ALIGNMENT);

        // La cantidad, en la misma letra gigante, justo DEBAJO del mensaje.
        amount_component = buildAmount();
        amount_component.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Reparto del sobrante igual que el GridBagLayout anterior (weighty 1/0/1):
        // S/4 sobre el título, S/4 entre título y mensaje, S/2 bajo el importe (dos
        // glue al pie) -> el bloque queda en la MISMA posición que antes en alta
        // resolución; el resto del comportamiento (déficit, fecha sin recortar) cambia.
        center.add(Box.createVerticalGlue());
        center.add(title_block);
        center.add(Box.createVerticalGlue());
        center.add(hero_label);
        center.add(amount_component);
        center.add(Box.createVerticalGlue());
        center.add(Box.createVerticalGlue());

        return center;
    }

    // Fija el reparto vertical de la franja central UNA VEZ finalizadas las fuentes
    // (tras updateFonts/fitTaggedLabels, que cambian los tamaños preferidos). El bloque
    // de título queda rígido (mín = pref) para que su fila de la fecha no se recorte; el
    // mensaje gigante y el importe pueden encoger (mín pequeño) y absorben el déficit en
    // resoluciones bajas reescalándose. Todos con ancho máximo libre (ocupan el ancho).
    private void finalizeCenterSizing() {
        if (title_block != null) {
            int h = title_block.getPreferredSize().height;
            title_block.setMinimumSize(new Dimension(0, h));
            title_block.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        }
        makeVerticallyShrinkable(hero_label);
        makeVerticallyShrinkable(amount_component);
    }

    // Deja crecer NADA en vertical (su sobrante va a los glue) pero permite ENCOGER
    // hasta un mínimo holgado: el OutlinedLabel se auto-reescala a la altura que le den.
    private static void makeVerticallyShrinkable(JComponent c) {
        if (c == null) {
            return;
        }
        int h = c.getPreferredSize().height;
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        c.setMinimumSize(new Dimension(0, Math.min(h, Math.max(24, h / 4))));
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

        // Total de manos jugadas en la timba (acumulativo, se restaura al recuperar), tras la
        // duración y entre corchetes: getMano() es el número de la mano en curso, que en el fin de
        // timba equivale al total jugado. Singular/plural para no mostrar "1 manos". Formato:
        // "fecha   (duración)   [N manos]".
        int manos = 0;
        try {
            manos = GameFrame.getInstance().getCrupier().getMano();
        } catch (Exception ex) {
        }
        String manos_txt = manos + " " + Translator.translate(manos == 1 ? "balance.mano" : "balance.manos");

        JLabel date = new JLabel(Helpers.getFechaHoraActual() + "   (" + Helpers.seconds2FullTime(GameFrame.getInstance().getConta_tiempo_juego()) + ")   [" + manos_txt + "]", SwingConstants.CENTER);
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

        // Rueda ya en el color del resultado (verde gana / rojo pierde) en vez de en naranja;
        // al aterrizar solo cambia el numero al +/- neto (mismo color) y parpadea.
        OutlinedLabel amount = new OutlinedLabel(Helpers.money2String(buyin), cmp > 0 ? WIN : LOSE);
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

        final javax.swing.Timer roll = new javax.swing.Timer(16, null);
        amount_roll_timer = roll;
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

        // Retro point-counting SFX, synced to the roll: its blips decelerate with
        // the same ease-out curve and the closing accent lands at ~1.5s, on the
        // +/- reveal. Se reproduce sobre el clip PRECARGADO en el constructor (linea
        // ya abierta) para arrancar instantaneo y en lockstep con el roll, sin un
        // open() al vuelo que se atasque con el dispositivo ocupado y desincronice el
        // sonido. Off-EDT: si la precarga aun no termino, playPreloadedWav la resuelve
        // en este hilo (nunca en el EDT). playPreloadedWav rebobina y reaplica el
        // volumen/mute (setClipVolume), asi que reanimar la pantalla lo reinicia limpio.
        if (GameFrame.conteoSonidoOn()) {
            Helpers.threadRun(() -> Audio.playPreloadedWav("misc/balance_count.wav"));
        }

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

        final javax.swing.Timer blink = new javax.swing.Timer(130, null);
        amount_blink_timer = blink;
        blink.addActionListener((e) -> {
            count[0]++;
            amount_label.setBlank(count[0] % 2 == 1);
            if (count[0] >= total) {
                ((javax.swing.Timer) e.getSource()).stop();
                amount_label.setBlank(false);
                // El contador de dinero ha terminado (neto +/- revelado y ya estable): captura
                // automática de la pantalla final. Síncrona (printAll lee el estado ACTUAL del label,
                // con blank=false recién fijado), así no hay riesgo de rasterizar un frame en blanco
                // del parpadeo. Idempotente: si el jugador ya volvió al menú, es no-op.
                takeBalanceScreenshot();
            }
        });
        blink.start();
    }

    // Programa la captura automática diferida de la pantalla final (solo EMPATE, que no tiene contador
    // que animar) tras delay_ms, dejando asentado el pintado del overlay. One-shot; se cancela en
    // stopAmountAnimation si el jugador sale antes de que dispare. EDT-only.
    private void scheduleAutoScreenshot(int delay_ms) {
        if (screenshot_done || !GameFrame.SCREENSHOT_FIN_TIMBA) {
            return;
        }
        final javax.swing.Timer t = new javax.swing.Timer(delay_ms, null);
        amount_screenshot_timer = t;
        t.setRepeats(false);
        t.addActionListener((e) -> {
            t.stop();
            takeBalanceScreenshot();
        });
        t.start();
    }

    // Captura la VENTANA COMPLETA (rootPane) con el overlay de la pantalla final montado sobre el
    // glassPane, con el MISMO mecanismo que CTRL+P (Helpers.renderComponentImage: printAll de Java2D,
    // sin Robot ni captura del SO, funciona en cualquier plataforma sin permisos): render en el EDT y
    // volcado del PNG a disco en un hilo aparte. Idempotente (screenshot_done). DEBE llamarse en el EDT.
    private void takeBalanceScreenshot() {
        if (screenshot_done || !GameFrame.SCREENSHOT_FIN_TIMBA) {
            return;
        }
        screenshot_done = true;
        GameFrame gf = GameFrame.getInstance();
        if (gf == null) {
            return;
        }
        // Rasteriza YA, en el EDT y con el overlay aún montado (el resultado es un snapshot
        // independiente del árbol de componentes, seguro aunque el frame se desmonte a continuación).
        final BufferedImage image = Helpers.renderComponentImage(gf.getRootPane());
        if (image == null) {
            return;
        }
        // BLINDAJE del race con el teardown: cuando la captura se dispara al VOLVER AL MENÚ, justo
        // después arranca finTransmision -> RESET_GAME, que llama a Helpers.SHUTDOWN_THREAD_POOL()
        // (shutdownNow del THREAD_POOL). Si el volcado fuera por Helpers.threadRun (ese mismo pool),
        // el shutdownNow DESCARTARÍA la tarea aún encolada o INTERRUMPIRÍA el ImageIO.write a medias
        // -> captura perdida o PNG corrupto. Por eso el I/O va en un hilo DEDICADO, ajeno al pool
        // (mismo criterio que los Swing Timers del frame, que "viven fuera de THREAD_POOL y por eso
        // sobreviven a SHUTDOWN_THREAD_POOL"). No-demonio para que un cierre normal de la JVM espere
        // a que termine de escribir el fichero. Solo toca datos propios (la imagen ya rasterizada y
        // SCREENSHOTS_DIR estático): nada que el teardown pueda invalidar.
        Thread saver = new Thread(() -> Helpers.saveScreenshot(image), "balance-screenshot-saver");
        saver.setDaemon(false);
        saver.start();
    }

    // Corta en seco la animación del importe (recuento + parpadeo) y su SFX. La llaman los
    // botones de salida (menú principal / continuar) ANTES de dispose(): mientras el contador
    // rueda, sus repintados por frame (TextLayout.getOutline recalcula el contorno del texto
    // a pantalla completa, ~16 ms) acaparan el EDT; si se dejan vivos, el teardown de la timba
    // (RESET_GAME, que descarta el tablero y abre el menú principal vía invokeAndWait) queda
    // famélico detrás de ellos y el menú no aparece hasta que la animación termina sola. Al
    // pararlos, la salida es instantánea igual que si se pulsa con el recuento ya terminado.
    // EDT-only (handlers de botón / windowClosed): stop() de un Timer no arrancado es no-op.
    private void stopAmountAnimation() {
        if (amount_roll_timer != null) {
            amount_roll_timer.stop();
        }
        if (amount_blink_timer != null) {
            amount_blink_timer.stop();
        }
        // Cancela el timer diferido de la captura del EMPATE si aún no ha disparado: al salir
        // (continuar / menú / cleanup) no debe dispararse tarde sobre una pantalla en teardown. Parar
        // el roll/blink ya impide la captura automática de ganancia/pérdida (su disparo vive en el fin
        // del parpadeo). En la ruta de menú, takeBalanceScreenshot ya se llamó ANTES que esto.
        if (amount_screenshot_timer != null) {
            amount_screenshot_timer.stop();
        }
        Audio.stopPreloadedWav("misc/balance_count.wav");
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

        // El altavoz queda CUADRADO con el mismo alto que los botones (su ancho pasa a ser
        // ese alto) y el icono se escala a ~la mitad de ese lado. Asi se alinea con la fila
        // y parece un boton mas, redondo-cuadrado, en cualquier resolucion.
        if (sound_chip != null) {
            Dimension square = new Dimension(max_h, max_h);
            sound_chip.setPreferredSize(square);
            sound_chip.setMinimumSize(square);
            sound_chip.setMaximumSize(square);
            sound_icon_sz = Math.max(16, Math.round(max_h * 0.5f));
            refreshBalanceSoundIcon();
            sound_chip.revalidate();
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
