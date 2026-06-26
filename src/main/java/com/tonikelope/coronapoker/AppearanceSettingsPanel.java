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

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Contenido de "Ajustes de apariencia" como JPanel (pestaña del diálogo unificado).
 * Todos los ajustes de apariencia son preferencias LOCALES de sesión que ya tienen
 * su propio item de menú con la lógica completa (persistir + efecto + reflejar en el
 * popup del tapete). Para no duplicar nada, cada control de este panel REFLEJA el
 * estado actual y DELEGA en el item de menú correspondiente vía {@code doClick()}
 * (igual que hacen los gemelos del popup): un clic en el control hace un clic en el
 * item de menú, que aplica todo. Como el control y el item arrancan sincronizados y
 * ambos conmutan un paso por clic, quedan siempre en el mismo estado.
 *
 * Solo tiene sentido en partida (necesita el menú de GameFrame), igual que el resto
 * del diálogo unificado.
 *
 * @author tonikelope
 */
public class AppearanceSettingsPanel extends JPanel {

    // Suprime las acciones de los combos mientras se construye (al fijar la
    // selección inicial no debe dispararse la delegación).
    private volatile boolean building = true;

    // Modo de pantalla elegido en el combo. NO se aplica en vivo (el toggle dispone y
    // recrea el frame, corrompiendo este diálogo); se RECUERDA aquí y lo aplica el
    // diálogo al cerrarse (applyPendingDisplayMode).
    private volatile boolean pending_fullscreen;

    // Snapshot del estado de apariencia al ABRIR: el diálogo es transaccional, así que
    // los cambios (que se aplican en vivo como previsualización) se REVIERTEN a estos
    // valores si se cancela (revert()); GUARDAR los conserva.
    private final int snap_zoom_level;
    private final int snap_vista_compacta;
    private final String snap_baraja;
    private final String snap_color_tapete;
    private final boolean snap_auto_zoom;
    private final boolean snap_show_clock;
    private final boolean snap_coste_igualar;
    private final boolean snap_cinematicas;
    private final boolean snap_anim_reparto;
    private final boolean snap_anim_ciegas_dealer;
    private final boolean snap_anim_apuestas;
    private final boolean snap_chat_images;
    private final boolean snap_fullscreen;

    public AppearanceSettingsPanel() {

        super(new java.awt.BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GameFrame gf = GameFrame.getInstance();

        snap_zoom_level = GameFrame.ZOOM_LEVEL;
        snap_vista_compacta = GameFrame.VISTA_COMPACTA;
        snap_baraja = GameFrame.BARAJA;
        snap_color_tapete = GameFrame.COLOR_TAPETE;
        snap_auto_zoom = GameFrame.AUTO_ZOOM;
        snap_show_clock = GameFrame.SHOW_CLOCK;
        snap_coste_igualar = GameFrame.MOSTRAR_COSTE_IGUALAR;
        snap_cinematicas = GameFrame.CINEMATICAS;
        snap_anim_reparto = GameFrame.ANIMACION_REPARTO;
        snap_anim_ciegas_dealer = GameFrame.ANIMACION_CIEGAS_DEALER;
        snap_anim_apuestas = GameFrame.ANIMACION_APUESTAS;
        snap_chat_images = GameFrame.CHAT_IMAGES_INGAME;
        snap_fullscreen = gf.isFull_screen();

        // ---------------- Pantalla y zoom ----------------
        JPanel pantalla = titledColumn("settings.apariencia_pantalla");

        // Modo de pantalla: ventana / pantalla completa. Refleja el estado actual del
        // tablero. NO se aplica en vivo (entrar/salir de pantalla completa dispone y
        // recrea el frame, lo que rompía este diálogo modal abierto: "solo funcionaba
        // una vez"). Se RECUERDA la elección y el diálogo la aplica al CERRARSE, con el
        // diálogo ya fuera; al reabrir, el combo vuelve a reflejar el estado real.
        pending_fullscreen = gf.isFull_screen();
        JComboBox<String> display_combo = new JComboBox<>(new String[]{
            Translator.translate("settings.modo_ventana"),
            Translator.translate("settings.modo_pantalla_completa")
        });
        display_combo.setSelectedIndex(pending_fullscreen ? 1 : 0);
        display_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            pending_fullscreen = display_combo.getSelectedIndex() == 1;
        });
        addLeft(pantalla, labeledRow("/images/menu/full_screen.png", "settings.modo_pantalla", display_combo));

        // Zoom: spinner en % (cada paso = 5% = un nivel de zoom interno). Aplica al
        // vuelo al nivel elegido.
        int zoom_pct = Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * 100f);
        // Los límites SIEMPRE contienen el valor actual (no hay tope superior de zoom
        // en el motor) para que SpinnerNumberModel no lance si el zoom guardado se sale.
        JSpinner zoom_spinner = new JSpinner(new SpinnerNumberModel(zoom_pct, Math.min(5, zoom_pct), Math.max(300, zoom_pct), 5));
        zoom_spinner.addChangeListener(e -> {
            if (building) {
                return;
            }
            int pct = (Integer) zoom_spinner.getValue();
            gf.setZoomLevel(Math.round((pct - 100) / (GameFrame.ZOOM_STEP * 100f)));
        });
        addLeft(pantalla, labeledRow("/images/menu/zoom.png", "settings.zoom_pct", zoom_spinner));

        // Vista compacta: desplegable tri-estado (0=off, 1=compacta, 2=compacta+cartas),
        // aplica al vuelo.
        JComboBox<String> compact_combo = new JComboBox<>(new String[]{
            Translator.translate("settings.compacta_off"),
            Translator.translate("settings.compacta_on"),
            Translator.translate("settings.compacta_full")
        });
        compact_combo.setSelectedIndex(Math.min(Math.max(GameFrame.VISTA_COMPACTA, 0), 2));
        compact_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            gf.setCompactView(compact_combo.getSelectedIndex());
        });
        addLeft(pantalla, labeledRow("/images/menu/tiny.png", "view.vista_compacta", compact_combo));

        addLeft(pantalla, delegatingCheckbox("/images/menu/zoom_auto.png", "menu.auto_ajustar", GameFrame.AUTO_ZOOM, gf.getAuto_fit_zoom_menu()));

        // "Pantalla y zoom" es más corta que la columna derecha y se estira para igualarla;
        // el glue empuja sus filas arriba y deja el hueco abajo (como "Varios" en Partida).
        closeColumn(pantalla);

        // ---------------- Mesa ----------------
        JPanel mesa = titledColumn("settings.apariencia_mesa");

        // Baraja: combo con las barajas disponibles (incluye las de MODs), delega en
        // el item de radio del submenú de barajas con ese nombre.
        List<String> decks = new ArrayList<>(Card.BARAJAS.keySet());
        Collections.sort(decks);
        JComboBox<String> baraja_combo = new JComboBox<>(decks.toArray(new String[0]));
        baraja_combo.setSelectedItem(GameFrame.BARAJA);
        baraja_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            String sel = (String) baraja_combo.getSelectedItem();
            if (sel != null && !sel.equals(GameFrame.BARAJA)) {
                for (Component c : gf.getMenu_barajas().getMenuComponents()) {
                    if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals(sel)) {
                        ((JMenuItem) c).doClick();
                        break;
                    }
                }
            }
        });
        addLeft(mesa, labeledRow("/images/menu/baraja.png", "menu.barajas", baraja_combo));

        // Tapete: combo con los 5 colores; delega en el radio correspondiente.
        JComboBox<String> tapete_combo = new JComboBox<>(new String[]{
            Translator.translate("menu.verde"),
            Translator.translate("menu.azul"),
            Translator.translate("menu.rojo"),
            Translator.translate("menu.negro"),
            Translator.translate("menu.sin_tapete")
        });
        tapete_combo.setSelectedIndex(currentTapeteIndex());
        tapete_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            switch (tapete_combo.getSelectedIndex()) {
                case 0:
                    gf.getMenu_tapete_verde().doClick();
                    break;
                case 1:
                    gf.getMenu_tapete_azul().doClick();
                    break;
                case 2:
                    gf.getMenu_tapete_rojo().doClick();
                    break;
                case 3:
                    gf.getMenu_tapete_negro().doClick();
                    break;
                case 4:
                    gf.getMenu_tapete_madera().doClick();
                    break;
                default:
                    break;
            }
        });
        addLeft(mesa, labeledRow("/images/menu/tapetes.png", "menu.tapetes", tapete_combo));

        addLeft(mesa, delegatingCheckbox("/images/menu/clock.png", "action.mostrar_reloj", GameFrame.SHOW_CLOCK, gf.getTime_menu()));
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "menu.coste_igualar", GameFrame.MOSTRAR_COSTE_IGUALAR, gf.getCoste_igualar_menu()));

        // ---------------- Animaciones y chat ----------------
        JPanel anim = titledColumn("settings.apariencia_animaciones");

        addLeft(anim, delegatingCheckbox("/images/menu/video.png", "menu.cinematicas", GameFrame.CINEMATICAS, gf.getMenu_cinematicas()));
        addLeft(anim, delegatingCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_reparto", GameFrame.ANIMACION_REPARTO, gf.getAnim_reparto_menu()));
        addLeft(anim, delegatingCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_ciegas_dealer", GameFrame.ANIMACION_CIEGAS_DEALER, gf.getAnim_ciegas_dealer_menu()));
        addLeft(anim, delegatingCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_apuestas", GameFrame.ANIMACION_APUESTAS, gf.getAnim_apuestas_menu()));
        addLeft(anim, delegatingCheckbox("/images/menu/chat_image.png", "menu.imagenes_del_chat_en_el_juego", GameFrame.CHAT_IMAGES_INGAME, gf.getChat_image_menu()));

        // Fila Pantalla | (Mesa sobre Animaciones) a su ALTO NATURAL en el NORTE,
        // alineadas arriba a la izquierda; el hueco sobrante cae limpio a la derecha y
        // abajo (mismo patrón que la pestaña Partida) sin estirar ni recortar los
        // subpaneles.
        pantalla.setAlignmentY(JComponent.TOP_ALIGNMENT);
        mesa.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        anim.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel right_inner = new JPanel();
        right_inner.setLayout(new BoxLayout(right_inner, BoxLayout.Y_AXIS));
        right_inner.setAlignmentY(JComponent.TOP_ALIGNMENT);
        right_inner.add(mesa);
        right_inner.add(Box.createVerticalStrut(10));
        right_inner.add(anim);

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(pantalla);
        row.add(Box.createHorizontalStrut(12));
        row.add(right_inner);

        add(row, java.awt.BorderLayout.NORTH);

        building = false;
    }

    // Aplica el modo de pantalla elegido en el combo. Lo invoca el diálogo al GUARDAR
    // (no en vivo: el toggle dispone y recrea el frame y corrompería el diálogo abierto).
    // Solo actúa si el usuario CAMBIÓ el combo respecto al estado de apertura; si no, no
    // toca AUTO_FULLSCREEN, para que guardar un ajuste no relacionado no reescriba la
    // preferencia de arranque (p.ej. tras un ALT+F transitorio que no la cambia).
    public void applyPendingDisplayMode() {
        if (pending_fullscreen == snap_fullscreen) {
            return;
        }
        GameFrame gf = GameFrame.getInstance();
        if (gf != null) {
            gf.setDisplayModeFullScreen(pending_fullscreen);
        }
    }

    // ¿Hay cambios de apariencia respecto al estado de apertura? (incluye el modo de
    // pantalla pendiente, que aún no se ha aplicado). Lo usa el diálogo para preguntar
    // antes de descartar al cancelar.
    public boolean isDirty() {
        return GameFrame.ZOOM_LEVEL != snap_zoom_level
                || GameFrame.VISTA_COMPACTA != snap_vista_compacta
                || !snap_baraja.equals(GameFrame.BARAJA)
                || !snap_color_tapete.equals(GameFrame.COLOR_TAPETE)
                || GameFrame.AUTO_ZOOM != snap_auto_zoom
                || GameFrame.SHOW_CLOCK != snap_show_clock
                || GameFrame.MOSTRAR_COSTE_IGUALAR != snap_coste_igualar
                || GameFrame.CINEMATICAS != snap_cinematicas
                || GameFrame.ANIMACION_REPARTO != snap_anim_reparto
                || GameFrame.ANIMACION_CIEGAS_DEALER != snap_anim_ciegas_dealer
                || GameFrame.ANIMACION_APUESTAS != snap_anim_apuestas
                || GameFrame.CHAT_IMAGES_INGAME != snap_chat_images
                || pending_fullscreen != snap_fullscreen;
    }

    // Revierte (al CANCELAR el diálogo transaccional) los ajustes de apariencia al
    // estado capturado al abrir: re-aplica cada uno por su camino normal (toggles por
    // doClick si difieren; zoom/compacta por su setter; baraja/tapete re-seleccionando
    // el radio). El modo de pantalla pendiente NO se aplica si se cancela.
    public void revert() {
        GameFrame gf = GameFrame.getInstance();
        if (gf == null) {
            return;
        }
        if (GameFrame.ZOOM_LEVEL != snap_zoom_level) {
            gf.setZoomLevel(snap_zoom_level);
        }
        if (GameFrame.VISTA_COMPACTA != snap_vista_compacta) {
            gf.setCompactView(snap_vista_compacta);
        }
        if (!snap_baraja.equals(GameFrame.BARAJA)) {
            selectBaraja(gf, snap_baraja);
        }
        if (!snap_color_tapete.equals(GameFrame.COLOR_TAPETE)) {
            selectTapete(gf, snap_color_tapete);
        }
        if (GameFrame.AUTO_ZOOM != snap_auto_zoom) {
            if (gf.getAuto_fit_zoom_menu().isEnabled()) {
                gf.getAuto_fit_zoom_menu().doClick();
            } else {
                // El menú de auto-ajustar se deshabilita mientras corre el autoZoom async
                // (al activarlo); un doClick aquí sería NO-OP y AUTO_ZOOM fugaría al
                // cancelar. Revertir el flag directamente (este caso solo apaga).
                GameFrame.AUTO_ZOOM = snap_auto_zoom;
                gf.getAuto_fit_zoom_menu().setSelected(snap_auto_zoom);
                Helpers.TapetePopupMenu.AUTO_ZOOM_MENU.setSelected(snap_auto_zoom);
                Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(snap_auto_zoom));
                Helpers.savePropertiesFile();
            }
        }
        if (GameFrame.SHOW_CLOCK != snap_show_clock) {
            gf.getTime_menu().doClick();
        }
        if (GameFrame.MOSTRAR_COSTE_IGUALAR != snap_coste_igualar) {
            gf.getCoste_igualar_menu().doClick();
        }
        if (GameFrame.CINEMATICAS != snap_cinematicas) {
            gf.getMenu_cinematicas().doClick();
        }
        if (GameFrame.ANIMACION_REPARTO != snap_anim_reparto) {
            gf.getAnim_reparto_menu().doClick();
        }
        if (GameFrame.ANIMACION_CIEGAS_DEALER != snap_anim_ciegas_dealer) {
            gf.getAnim_ciegas_dealer_menu().doClick();
        }
        if (GameFrame.ANIMACION_APUESTAS != snap_anim_apuestas) {
            gf.getAnim_apuestas_menu().doClick();
        }
        if (GameFrame.CHAT_IMAGES_INGAME != snap_chat_images) {
            gf.getChat_image_menu().doClick();
        }
    }

    private static void selectBaraja(GameFrame gf, String baraja) {
        for (Component c : gf.getMenu_barajas().getMenuComponents()) {
            if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals(baraja)) {
                ((JMenuItem) c).doClick();
                break;
            }
        }
    }

    private static void selectTapete(GameFrame gf, String color) {
        if (color.startsWith("azul")) {
            gf.getMenu_tapete_azul().doClick();
        } else if (color.startsWith("rojo")) {
            gf.getMenu_tapete_rojo().doClick();
        } else if (color.startsWith("negro")) {
            gf.getMenu_tapete_negro().doClick();
        } else if (color.startsWith("madera")) {
            gf.getMenu_tapete_madera().doClick();
        } else {
            gf.getMenu_tapete_verde().doClick();
        }
    }

    private JPanel titledColumn(String titleKey) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(Translator.translate(titleKey)));
        return p;
    }

    // Añade una fila alineada a la izquierda + un hueco vertical constante (12px, la
    // misma separación que las filas de "Varios" en la pestaña Partida). Las filas son
    // naturalRow() (alto máximo = preferido), así que NO se estiran a rellenar la
    // columna; el sobrante lo absorbe el glue que cierra cada columna.
    private void addLeft(JPanel column, JComponent comp) {
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        column.add(comp);
        column.add(Box.createVerticalStrut(12));
    }

    // Cierra una columna con un glue que empuja las filas hacia arriba y deja el hueco
    // sobrante abajo (igual que el addContainerGap final de la pestaña Partida), en vez
    // de repartirlo entre las filas. Solo importa en la columna más corta ("Pantalla y
    // zoom"), que se estira para igualar a la derecha.
    private static void closeColumn(JPanel column) {
        column.add(Box.createVerticalGlue());
    }

    // Fila (FlowLayout) cuyo alto MÁXIMO es su alto preferido: en el BoxLayout Y de la
    // columna no se estira para rellenar el hueco, así las filas quedan a separación
    // constante (la del strut de addLeft) en vez de desperdigadas.
    private static JPanel naturalRow() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    private JComponent delegatingCheckbox(String iconPath, String i18nKey, boolean selected, JMenuItem menu) {
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), selected);
        cb.setEnabled(menu.isEnabled());
        // Un clic en el checkbox => un clic en el item de menú (aplica + persiste +
        // refleja en el popup). Ambos conmutan un paso, así que quedan sincronizados.
        cb.addActionListener(e -> menu.doClick());
        // Icono a la izquierda (el mismo del antiguo ítem de menú) para dar paridad con
        // la pestaña Partida y con los menús que este diálogo sustituye.
        JPanel row = naturalRow();
        row.add(new JLabel(icon(iconPath)));
        row.add(cb);
        return row;
    }

    private JPanel labeledRow(String iconPath, String labelKey, JComponent control) {
        JPanel row = naturalRow();
        JLabel label = new JLabel(Translator.translate(labelKey) + ":");
        label.setIcon(icon(iconPath));
        row.add(label);
        row.add(control);
        return row;
    }

    private static javax.swing.ImageIcon icon(String path) {
        return new javax.swing.ImageIcon(AppearanceSettingsPanel.class.getResource(path));
    }

    private int currentTapeteIndex() {
        String ct = GameFrame.COLOR_TAPETE;
        if (ct.startsWith("azul")) {
            return 1;
        }
        if (ct.startsWith("rojo")) {
            return 2;
        }
        if (ct.startsWith("negro")) {
            return 3;
        }
        if (ct.startsWith("madera")) {
            return 4;
        }
        return 0;
    }

}
