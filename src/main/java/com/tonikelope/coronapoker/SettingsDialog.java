/*
 * Copyright (C) 2026 tonikelope
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

/**
 * Diálogo unificado de ajustes. En partida muestra 3 pestañas: Apariencia, Audio y
 * Partida. Fuera de partida (lanzador / sala de espera, sin GameFrame) se abre en modo
 * "general" con solo Apariencia y Audio; la pestaña Partida (ciegas + reglas) solo se
 * monta en partida.
 *
 * Apariencia y Audio se aplican EN VIVO en partida (preferencia local) y solo PERSISTEN
 * la preferencia fuera de ella (no hay mesa contra la que previsualizar). Partida (ciegas
 * + reglas) solo al pulsar GUARDAR. Para clientes la pestaña Partida es de solo-lectura
 * (las reglas las manda el host); Apariencia y Audio siguen siendo editables (locales).
 *
 * @author tonikelope
 */
public class SettingsDialog extends JDialog {

    private final AppearanceSettingsPanel appearance_panel;
    private final AudioSettingsPanel audio_panel;
    private final GameSettingsPanel game_panel;
    // Pestaña "Partida" de la SALA DE ESPERA (config de timba antes de empezar). Excluyente
    // con game_panel: uno u otro según el contexto (in-game vs sala), nunca los dos.
    private final WaitingGameSettingsPanel waiting_panel;
    // Diálogo transaccional: true solo si se pulsó GUARDAR (entonces NO se revierte).
    private boolean committed = false;

    // Instancia abierta (único modal a la vez). La usa el cierre automático al arrancar la
    // partida (closeIfOpen) y el refresco del espejo de la pestaña Partida de sala.
    private static volatile SettingsDialog INSTANCE;

    public static void open(java.awt.Frame parent) {
        Helpers.GUIRun(() -> {
            SettingsDialog dialog = new SettingsDialog(parent, true);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    public SettingsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        // Tres contextos según dónde se abra la rueda:
        //  - EN PARTIDA (hay GameFrame): pestaña Partida EN VIVO (GameSettingsPanel).
        //  - EN LA SALA DE ESPERA (parent = WaitingRoomFrame, sin partida empezada): pestaña
        //    Partida de SALA (WaitingGameSettingsPanel, config completa pre-timba).
        //  - LANZADOR (ni juego ni sala): solo Apariencia y Sonido (sin pestaña Partida).
        boolean in_game = GameFrame.getInstance() != null;
        boolean in_waiting = !in_game && (parent instanceof WaitingRoomFrame)
                && !((WaitingRoomFrame) parent).isPartida_empezada();
        boolean read_only_game = !in_game || !GameFrame.getInstance().isPartida_local();
        // En la sala: editable solo para el HOST. Cliente / no-servidor -> solo lectura TOTAL.
        // Al RECUPERAR una timba (parada para admitir jugadores), el HOST puede editar los
        // ajustes de "Partida" (reglas + tiempo de pensar) y la dificultad de bots, pero la
        // economía (compra, recompra, ciegas, estructura, ante, straddle) sigue BLOQUEADA con
        // los valores de la timba recuperada -> modo recover parcial (no read-only total).
        boolean read_only_wait = !in_waiting || !((WaitingRoomFrame) parent).isServer();
        boolean recover_wait = in_waiting && !read_only_wait && GameFrame.isRECOVER();

        setTitle(Translator.translate("settings.ajustes"));
        // DO_NOTHING: la X la gestiona windowClosing (pregunta antes de descartar, igual
        // que el botón Cancelar).
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        appearance_panel = new AppearanceSettingsPanel();
        audio_panel = new AudioSettingsPanel();
        game_panel = in_game ? new GameSettingsPanel(read_only_game) : null;
        waiting_panel = in_waiting ? new WaitingGameSettingsPanel(read_only_wait, recover_wait) : null;

        // Cada pestaña va dentro de un JScrollPane (ScrollableTabPanel): sigue el ancho
        // del viewport (sin barra horizontal espuria) y rellena el alto cuando cabe, pero
        // muestra barra vertical cuando el contenido no entra. Así el diálogo se encoge y
        // scrollea en resoluciones bajas en vez de salirse de la pantalla.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Translator.translate("settings.tab_apariencia"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")), scrollableTab(appearance_panel));
        tabs.addTab(Translator.translate("settings.tab_audio"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/sound.png")), scrollableTab(audio_panel));
        if (in_game) {
            tabs.addTab(Translator.translate("settings.tab_partida"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")), scrollableTab(game_panel));
        } else if (in_waiting) {
            tabs.addTab(Translator.translate("settings.tab_partida"), new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")), scrollableTab(waiting_panel));
        }

        // Diálogo TRANSACCIONAL: Apariencia y Audio se aplican en vivo como
        // previsualización, pero GUARDAR es lo que los CONFIRMA y además aplica el modo
        // de pantalla pendiente y la pestaña Partida (ciegas + reglas, solo si eres host:
        // applyToGame no-opea para clientes). Cancelar / cerrar revierte TODO al estado
        // de apertura (ver windowClosed). GUARDAR está siempre activo: para un cliente
        // confirma sus ajustes LOCALES de apariencia y audio.
        JButton save_button = new JButton(Translator.translate("ui.guardar"));
        save_button.setBackground(new java.awt.Color(0, 130, 0));
        save_button.setForeground(new java.awt.Color(255, 255, 255));
        save_button.addActionListener(e -> {
            committed = true;
            if (game_panel != null) {
                game_panel.applyToGame();
            }
            if (waiting_panel != null) {
                waiting_panel.applyToGame();
            }
            appearance_panel.applyPendingDisplayMode();
            dispose();
        });

        JButton cancel_button = new JButton(Translator.translate("ui.cancelar_2"));
        cancel_button.addActionListener(e -> cancelWithConfirm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(save_button);
        buttons.add(cancel_button);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(tabs, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelWithConfirm();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                if (isModal()) {
                    Init.CURRENT_MODAL_DIALOG.add(SettingsDialog.this);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                if (isModal()) {
                    try {
                        Init.CURRENT_MODAL_DIALOG.removeLast();
                    } catch (Exception ex) {
                    }
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                // Si NO se guardó (Cancelar / cerrar), revierte los cambios EN VIVO de
                // Apariencia y Audio al estado de apertura. El modo de pantalla y la
                // pestaña Partida solo se aplican al GUARDAR (no aquí). Esto corre también
                // en el cierre automático (dispose) al arrancar la partida: descarta sin
                // preguntar (windowClosing -y su confirmación- no se dispara con dispose()).
                if (!committed) {
                    appearance_panel.revert();
                    audio_panel.revert();
                }
                // Cierra la captura de tecla del panel de audio + persiste el volumen.
                audio_panel.cleanup();
                if (INSTANCE == SettingsDialog.this) {
                    INSTANCE = null;
                }
            }
        });

        // Fuentes UNIFICADAS al tamaño del diálogo de nueva timba (16, conservando el
        // estilo bold/plain de cada control). Las pestañas Apariencia y Audio usaban
        // la fuente por defecto (más pequeña) y quedaban descompensadas respecto a
        // Partida; con esto todo el diálogo va al mismo tamaño.
        Helpers.setUniformFont(content, Helpers.GUI_FONT, 16);

        // setUniformFont no alcanza los títulos de los TitledBorder.
        fixTitledBorderFonts(content, save_button.getFont());

        // Arreglos de tamaño del panel de audio (máximos de fila/panel), ya con la
        // fuente unificada aplicada.
        audio_panel.applyFontsAndSizing();

        // Botones de acción un pelín más grandes que el resto del diálogo.
        java.awt.Font buttons_font = Helpers.GUI_FONT.deriveFont(Font.BOLD, 18f);
        save_button.setFont(buttons_font);
        cancel_button.setFont(buttons_font);

        pack();

        // Ensancha el diálogo ~15% sobre su tamaño empaquetado, a la anchura del diálogo
        // de nueva timba: así la columna de Ciegas (combo a la derecha de su etiqueta) y el
        // resto de paneles, que se estiran para rellenar, respiran igual que al crear timba.
        // capToScreen lo recorta si no cabe en pantalla (solo encoge).
        setSize(Math.round(getWidth() * 1.15f), getHeight());

        // Tope al ÁREA ÚTIL de la pantalla (mismo patrón de baja resolución que
        // NewGameDialog: getMaximumWindowBounds excluye la barra de tareas). El diálogo
        // queda lo más pequeño posible que entre todo; si aún se sale (resolución muy
        // baja / escalado alto), se recorta y cada pestaña pasa a scrollear. Los botones
        // GUARDAR/Cancelar viven en el SOUTH, fuera del scroll, así que siempre se ven.
        capToScreen();

        // Único modal a la vez: registrarse como la instancia abierta (la limpia
        // windowClosed). Lo usan closeIfOpen (auto-cierre al arrancar) y refreshWaitingMirror.
        INSTANCE = this;
    }

    // Recorta el tamaño empaquetado al área útil de la pantalla (95%). Solo encoge.
    private void capToScreen() {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int max_w = Math.round(usable.width * 0.95f);
        int max_h = Math.round(usable.height * 0.95f);
        int w = getWidth();
        int h = getHeight();
        // Si hay que recortar el ALTO, aparecerá barra vertical; reserva su ancho (~17px)
        // para que NO se dispare además una barra horizontal espuria por el ancho que
        // roba. Si la pantalla no da para ese extra, la horizontal aparece y se scrollea.
        if (h > max_h) {
            w += new javax.swing.JScrollBar(javax.swing.JScrollBar.VERTICAL).getPreferredSize().width + 2;
        }
        w = Math.min(w, max_w);
        h = Math.min(h, max_h);
        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);
        }
    }

    // ¿Hay cambios sin confirmar en cualquiera de las pestañas? (Apariencia/Audio se
    // aplican en vivo; Partida es apply-on-save.) Se usa para preguntar antes de
    // descartar al cancelar.
    private boolean isDirty() {
        return appearance_panel.isDirty() || audio_panel.isDirty()
                || (game_panel != null && game_panel.isDirty())
                || (waiting_panel != null && waiting_panel.isDirty());
    }

    // Cierra el diálogo abierto (si lo hay) SIN preguntar por cambios sin guardar. Lo usa
    // el arranque de partida en el cliente: una vez empezada la timba los ajustes de la
    // pestaña Partida de sala ya no aplican. dispose() directo NO dispara windowClosing
    // (donde vive la confirmación de descarte), así que cierra como un Alt+F4 pero sin el
    // diálogo de "¿descartar cambios?"; los cambios sin guardar se descartan. Idempotente.
    public static void closeIfOpen() {
        Helpers.GUIRun(() -> {
            SettingsDialog d = INSTANCE;
            if (d != null && d.isDisplayable()) {
                d.dispose();
            }
        });
    }

    // Refresca (en vivo) la pestaña Partida de SALA en SOLO-LECTURA cuando llega un nuevo
    // espejo de config del host (GAMECONFIG). Llamar en el EDT.
    public static void refreshWaitingMirror() {
        SettingsDialog d = INSTANCE;
        if (d != null && d.waiting_panel != null) {
            d.waiting_panel.refreshFromMirror();
        }
    }

    // Cierra descartando los cambios; si hay cambios sin confirmar, pregunta primero.
    // Lo usan el botón Cancelar y la X de la ventana.
    private void cancelWithConfirm() {
        if (!isDirty() || Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("settings.descartar_cambios")) == javax.swing.JOptionPane.YES_OPTION) {
            dispose();
        }
    }

    private static void fixTitledBorderFonts(Container c, Font font) {
        if (c instanceof javax.swing.JComponent) {
            javax.swing.border.Border b = ((javax.swing.JComponent) c).getBorder();
            if (b instanceof TitledBorder) {
                ((TitledBorder) b).setTitleFont(font);
            }
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                fixTitledBorderFonts((Container) child, font);
            }
        }
    }

    // Envuelve el contenido de una pestaña en un JScrollPane sin borde, con barras
    // vertical y horizontal bajo demanda y rueda de ratón fluida. El contenido (ver
    // ScrollableTabPanel) RELLENA el viewport mientras cabe y solo scrollea cuando no.
    private static JScrollPane scrollableTab(Component panel) {
        JScrollPane sp = new JScrollPane(new ScrollableTabPanel(panel),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // Contenedor que, dentro de un JScrollPane, RELLENA el viewport mientras el contenido
    // cabe (sigue su ancho/alto: ni barras espurias ni franja de fondo en pantallas
    // amplias) y deja scrollear en el eje que NO cabe cuando el diálogo se encoge.
    // Patrón estándar "ScrollablePanel".
    private static final class ScrollableTabPanel extends JPanel implements Scrollable {

        ScrollableTabPanel(Component view) {
            super(new BorderLayout());
            add(view, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        // Sigue el ancho del viewport solo si el contenido CABE; si no, deja que aparezca
        // la barra horizontal (cuando el usuario estrecha mucho el diálogo).
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getPreferredSize().width <= getParent().getWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getPreferredSize().height < getParent().getHeight();
        }
    }

}
