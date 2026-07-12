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
import java.util.function.Consumer;
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
 *
 * Tiene DOS modos según haya o no partida en curso ({@code GameFrame.getInstance()}):
 *
 * - EN PARTIDA (gf != null): cada control REFLEJA el estado actual y DELEGA en el item
 *   de menú correspondiente del GameFrame vía {@code doClick()} (o en su setter), que
 *   aplica EN VIVO el efecto en la mesa + lo persiste + lo refleja en el popup del tapete.
 *   Como el control y el item arrancan sincronizados y ambos conmutan un paso por clic,
 *   quedan siempre en el mismo estado.
 *
 * - FUERA DE PARTIDA (gf == null: lanzador / sala de espera): no hay mesa contra la que
 *   previsualizar, así que los controles SOLO PERSISTEN la preferencia (flag estático +
 *   {@code Helpers.PROPERTIES} + {@code savePropertiesFile()}); surte efecto cuando se
 *   crea la timba (el GameFrame lee esas preferencias al construirse). Sin efecto en vivo,
 *   SALVO el tapete: la pantalla de inicio pinta su fondo con ese color ({@code InitPanel}),
 *   así que cambiarlo refresca el lanzador al vuelo como previsualización (y se revierte al
 *   cancelar, igual que en partida).
 *
 * El diálogo es TRANSACCIONAL en ambos modos: los cambios se revierten al estado de
 * apertura si se cancela (revert()); GUARDAR los conserva.
 *
 * NOTA: la preferencia de cada toggle de animación vive a la vez en el isSelected del
 * item de menú y en su clave de PROPERTIES, y ambos se mantienen sincronizados (el item
 * persiste la clave en cada cambio y se inicializa desde ella). Por eso aquí se lee
 * SIEMPRE desde PROPERTIES: es equivalente a leer el item y no depende de que haya
 * GameFrame.
 *
 * @author tonikelope
 */
public class AppearanceSettingsPanel extends JPanel {

    // GameFrame en curso, o null fuera de partida (lanzador / sala de espera). En modo
    // null los controles solo persisten la preferencia, sin efecto en vivo.
    private final GameFrame gf;

    // Suprime las acciones de los combos mientras se construye (al fijar la
    // selección inicial no debe dispararse la delegación).
    private volatile boolean building = true;

    // Modo de pantalla elegido en el combo. NO se aplica en vivo (el toggle dispone y
    // recrea el frame, corrompiendo este diálogo); se RECUERDA aquí y lo aplica el
    // diálogo al cerrarse (applyPendingDisplayMode).
    private volatile boolean pending_fullscreen;

    // Los 5 checkboxes individuales de animacion y sus items de menu (null fuera de
    // partida), para que el maestro los DESHABILITE (sin desmarcar) al desmarcarse.
    private final java.util.List<JCheckBox> anim_sub_cb = new ArrayList<>();
    private final java.util.List<JMenuItem> anim_sub_menu = new ArrayList<>();

    // Snapshot del estado de apariencia al ABRIR: el diálogo es transaccional, así que
    // los cambios (que se aplican en vivo como previsualización) se REVIERTEN a estos
    // valores si se cancela (revert()); GUARDAR los conserva.
    private final int snap_zoom_level;
    private final int snap_vista_compacta;
    private final String snap_baraja;
    private final String snap_trasera;
    private final String snap_color_tapete;
    private final boolean snap_auto_zoom;
    private final boolean snap_show_clock;
    private final boolean snap_coste_igualar;
    private final boolean snap_cinematicas;
    private final boolean snap_anim_barajado;
    private final boolean snap_anim_reparto;
    private final boolean snap_anim_destape;
    private final boolean snap_anim_ciegas_dealer;
    private final boolean snap_anim_apuestas;
    private final boolean snap_anim_contadores;
    private final boolean snap_anim_cascada_overlay;
    private final boolean snap_resaltar_jugada_perdedor;
    private final boolean snap_animaciones;
    private final boolean snap_chat_images;
    private final boolean snap_fullscreen;
    private final int snap_card_flip_duration;
    private final int snap_card_flip_zoom;
    private final int snap_reparto_velocidad;
    private final boolean snap_anim_downgrade;
    private final int snap_downgrade_velocidad;

    public AppearanceSettingsPanel() {

        super(new java.awt.BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        gf = GameFrame.getInstance();

        snap_zoom_level = GameFrame.ZOOM_LEVEL;
        snap_vista_compacta = GameFrame.VISTA_COMPACTA;
        snap_baraja = GameFrame.BARAJA;
        snap_trasera = GameFrame.TRASERA;
        snap_color_tapete = GameFrame.COLOR_TAPETE;
        snap_auto_zoom = GameFrame.AUTO_ZOOM;
        snap_show_clock = GameFrame.SHOW_CLOCK;
        snap_coste_igualar = GameFrame.MOSTRAR_COSTE_IGUALAR;
        // Snapshot de las PREFERENCIAS de animación leídas de PROPERTIES (equivalente al
        // isSelected del item, ver nota de clase): NO del flag EFECTIVO, que con el maestro
        // off es false para todos y no permitiría distinguir un cambio de preferencia al
        // revertir.
        snap_cinematicas = prefBool("cinematicas");
        // Barajado y destape no tienen item de menú: su preferencia es el flag de GameFrame
        // (ya migrado del histórico "animacion_reparto" si aún no se habían guardado), no PROPERTIES
        // en crudo, que podría no tener aún la clave.
        snap_anim_barajado = GameFrame.ANIMACION_BARAJADO_PREF;
        snap_anim_reparto = prefBool("animacion_reparto");
        snap_anim_destape = GameFrame.ANIMACION_DESTAPE_PREF;
        snap_anim_ciegas_dealer = prefBool("animacion_ciegas_dealer");
        snap_anim_apuestas = prefBool("animacion_apuestas");
        snap_anim_contadores = prefBool("animacion_contadores");
        snap_anim_cascada_overlay = prefBool("animacion_cascada_overlay", false);
        snap_resaltar_jugada_perdedor = prefBool("resaltar_jugada_perdedor", true);
        snap_animaciones = GameFrame.ANIMACIONES;
        snap_chat_images = GameFrame.CHAT_IMAGES_INGAME;
        snap_fullscreen = (gf != null) ? gf.isFull_screen() : GameFrame.AUTO_FULLSCREEN;
        snap_card_flip_duration = GameFrame.CARD_FLIP_DURATION;
        snap_card_flip_zoom = GameFrame.CARD_FLIP_ZOOM;
        snap_reparto_velocidad = GameFrame.REPARTO_VELOCIDAD;
        snap_anim_downgrade = GameFrame.ANIMACION_DOWNGRADE_PREF;
        snap_downgrade_velocidad = GameFrame.DOWNGRADE_VELOCIDAD;

        // ---------------- Pantalla y zoom ----------------
        JPanel pantalla = titledColumn("settings.apariencia_pantalla");

        // Modo de pantalla: ventana / pantalla completa. Refleja el estado actual del
        // tablero (o la preferencia AUTO_FULLSCREEN fuera de partida). NO se aplica en
        // vivo (entrar/salir de pantalla completa dispone y recrea el frame, lo que
        // rompía este diálogo modal abierto: "solo funcionaba una vez"). Se RECUERDA la
        // elección y el diálogo la aplica al CERRARSE (en partida cambia el modo; fuera
        // de partida solo persiste la preferencia de arranque).
        pending_fullscreen = snap_fullscreen;
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

        // Zoom: spinner en % (cada paso = 5% = un nivel de zoom interno). En partida aplica
        // al vuelo al nivel elegido; fuera de partida solo persiste la preferencia.
        int zoom_pct = Math.round((1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP) * 100f);
        // Los límites SIEMPRE contienen el valor actual (no hay tope superior de zoom
        // en el motor) para que SpinnerNumberModel no lance si el zoom guardado se sale.
        JSpinner zoom_spinner = new JSpinner(new SpinnerNumberModel(zoom_pct, Math.min(5, zoom_pct), Math.max(300, zoom_pct), 5));
        zoom_spinner.addChangeListener(e -> {
            if (building) {
                return;
            }
            int pct = (Integer) zoom_spinner.getValue();
            int level = Math.round((pct - 100) / (GameFrame.ZOOM_STEP * 100f));
            if (gf != null) {
                gf.setZoomLevel(level);
            } else {
                GameFrame.ZOOM_LEVEL = level;
                persist("zoom_level", String.valueOf(level));
            }
        });
        addLeft(pantalla, labeledRow("/images/menu/zoom.png", "settings.zoom_pct", zoom_spinner));

        // Vista compacta: desplegable tri-estado (0=off, 1=compacta, 2=compacta+cartas),
        // aplica al vuelo en partida / solo persiste fuera de partida.
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
            int idx = compact_combo.getSelectedIndex();
            if (gf != null) {
                gf.setCompactView(idx);
            } else {
                GameFrame.VISTA_COMPACTA = idx;
                persist("vista_compacta", String.valueOf(idx));
            }
        });
        addLeft(pantalla, labeledRow("/images/menu/tiny.png", "view.vista_compacta", compact_combo));

        addLeft(pantalla, delegatingCheckbox("/images/menu/zoom_auto.png", "menu.auto_ajustar", GameFrame.AUTO_ZOOM,
                gf != null ? gf.getAuto_fit_zoom_menu() : null,
                () -> {
                    GameFrame.AUTO_ZOOM = !GameFrame.AUTO_ZOOM;
                    persist("auto_zoom", String.valueOf(GameFrame.AUTO_ZOOM));
                }));

        // "Pantalla y zoom" es más corta que la columna derecha y se estira para igualarla;
        // el glue empuja sus filas arriba y deja el hueco abajo (como "Varios" en Partida).
        closeColumn(pantalla);

        // ---------------- Mesa ----------------
        JPanel mesa = titledColumn("settings.apariencia_mesa");

        List<String> decks = new ArrayList<>(Card.BARAJAS.keySet());
        Collections.sort(decks);

        // Baraja: combo con las barajas disponibles (incluye las de MODs). En partida delega
        // en el item de radio del submenú de barajas (recarga las imágenes); fuera de partida
        // persiste y reconstruye las imágenes estáticas (así la trasera "default" queda bien).
        JComboBox<String> baraja_combo = new JComboBox<>(decks.toArray(new String[0]));
        baraja_combo.setSelectedItem(GameFrame.BARAJA);
        baraja_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            String sel = (String) baraja_combo.getSelectedItem();
            if (sel != null && !sel.equals(GameFrame.BARAJA)) {
                if (gf != null) {
                    for (Component c : gf.getMenu_barajas().getMenuComponents()) {
                        if (c instanceof JMenuItem && ((JMenuItem) c).getText().equals(sel)) {
                            ((JMenuItem) c).doClick();
                            break;
                        }
                    }
                } else {
                    GameFrame.BARAJA = sel;
                    persist("baraja", sel);
                    Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);
                }
            }
        });
        // Baraja + Trasera van juntas en un recuadro con borde (groupBox), igual
        // que las opciones agrupadas de la columna Animaciones: la baraja es la
        // opción principal y la trasera su subajuste (cuelga con sangría).
        JPanel baraja_group = groupBox();
        addToGroup(baraja_group, labeledRow("/images/menu/baraja.png", "settings.baraja", baraja_combo));

        // Trasera: "default" (sigue a la baraja actual) + una opción por cada baraja (juego o
        // mod) para usar su dorso con otras caras. Va con SANGRÍA bajo "Baraja". En partida
        // aplica en vivo (refresca el dorso); fuera persiste y reconstruye el dorso estático.
        List<String> traseras = new ArrayList<>();
        traseras.add("default");
        traseras.addAll(decks);
        JComboBox<String> trasera_combo = new JComboBox<>(traseras.toArray(new String[0]));
        // El VALOR interno sigue siendo "default" (persistencia), pero se muestra traducido.
        trasera_combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if ("default".equals(value)) {
                    setText(Translator.translate("settings.trasera_default"));
                }
                return c;
            }
        });
        trasera_combo.setSelectedItem(Card.BARAJAS.containsKey(GameFrame.TRASERA) ? GameFrame.TRASERA : "default");
        trasera_combo.addActionListener(e -> {
            if (building) {
                return;
            }
            String sel = (String) trasera_combo.getSelectedItem();
            if (sel != null && !sel.equals(GameFrame.TRASERA)) {
                if (gf != null) {
                    gf.setTrasera(sel);
                } else {
                    GameFrame.TRASERA = sel;
                    persist("trasera", sel);
                    Card.updateCachedImages(1f + GameFrame.ZOOM_LEVEL * GameFrame.getZOOM_STEP(), true);
                }
            }
        });
        JPanel trasera_row = naturalRow();
        trasera_row.add(Box.createHorizontalStrut(24)); // sangría: cuelga de "Baraja"
        JLabel trasera_label = new JLabel(Translator.translate("settings.trasera") + ":");
        trasera_label.setIcon(icon("/images/menu/baraja.png"));
        trasera_row.add(trasera_label);
        trasera_row.add(trasera_combo);
        addToGroup(baraja_group, trasera_row);
        addLeft(mesa, baraja_group);

        // Tapete: combo con los 5 colores; en partida delega en el radio correspondiente
        // (refresca la mesa); fuera de partida persiste el color base y refresca al vuelo el
        // fondo de la pantalla de inicio (InitPanel), que es lo único que previsualiza el
        // tapete fuera de la mesa.
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
            int idx = tapete_combo.getSelectedIndex();
            if (gf != null) {
                switch (idx) {
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
            } else {
                String color = tapeteColorForIndex(idx);
                GameFrame.COLOR_TAPETE = color;
                persist("color_tapete", color);
                refreshLauncherTapete();
            }
        });
        addLeft(mesa, labeledRow("/images/menu/tapetes.png", "settings.tapete", tapete_combo));

        addLeft(mesa, delegatingCheckbox("/images/menu/clock.png", "action.mostrar_reloj", GameFrame.SHOW_CLOCK,
                gf != null ? gf.getTime_menu() : null,
                () -> {
                    GameFrame.SHOW_CLOCK = !GameFrame.SHOW_CLOCK;
                    persist("show_time", String.valueOf(GameFrame.SHOW_CLOCK));
                }));
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "menu.coste_igualar", GameFrame.MOSTRAR_COSTE_IGUALAR,
                gf != null ? gf.getCoste_igualar_menu() : null,
                () -> {
                    GameFrame.MOSTRAR_COSTE_IGUALAR = !GameFrame.MOSTRAR_COSTE_IGUALAR;
                    persist("mostrar_coste_igualar", String.valueOf(GameFrame.MOSTRAR_COSTE_IGUALAR));
                }));
        addLeft(mesa, delegatingCheckbox("/images/menu/chat_image.png", "menu.imagenes_del_chat_en_el_juego", GameFrame.CHAT_IMAGES_INGAME,
                gf != null ? gf.getChat_image_menu() : null,
                () -> {
                    GameFrame.CHAT_IMAGES_INGAME = !GameFrame.CHAT_IMAGES_INGAME;
                    persist("chat_images_ingame", String.valueOf(GameFrame.CHAT_IMAGES_INGAME));
                }));
        // Resaltado del showdown: sin item de menú ni efecto en vivo (se lee al vuelo al pasar
        // el ratón por la etiqueta de jugada). Persist-only, como la cascada.
        addLeft(mesa, delegatingCheckbox("/images/menu/eyes.png", "settings.resaltar_jugada_perdedor", GameFrame.RESALTAR_JUGADA_PERDEDOR,
                null,
                () -> {
                    GameFrame.RESALTAR_JUGADA_PERDEDOR = !GameFrame.RESALTAR_JUGADA_PERDEDOR;
                    persist("resaltar_jugada_perdedor", String.valueOf(GameFrame.RESALTAR_JUGADA_PERDEDOR));
                }, "tooltip.cfg.resaltar_jugada_perdedor"));

        // ---------------- Animaciones ----------------
        JPanel anim = titledColumn("settings.apariencia_animaciones");

        // Maestro: activa/desactiva TODAS las animaciones de un plumazo. Al desmarcarlo,
        // DESHABILITA (no desmarca) los 5 checkboxes de abajo, que conservan su valor.
        JCheckBox anim_master = new JCheckBox(Translator.translate("menu.efectos_animacion_general"), GameFrame.ANIMACIONES);
        anim_master.addActionListener(e -> {
            boolean on = anim_master.isSelected();
            if (gf != null) {
                gf.setAnimacionesMaster(on);
            } else {
                // Fuera de partida: el maestro es solo un GATE. Persiste ANIMACIONES y NO
                // toca las preferencias individuales (los *_PREF son la preferencia cruda;
                // el gate lo aplican los helpers *On() al leerlas).
                GameFrame.ANIMACIONES = on;
                persist("animaciones", String.valueOf(on));
            }
            for (int i = 0; i < anim_sub_cb.size(); i++) {
                JMenuItem m = anim_sub_menu.get(i);
                anim_sub_cb.get(i).setEnabled(on && (m == null || m.isEnabled()));
            }
        });
        JPanel master_row = naturalRow();
        master_row.add(new JLabel(icon("/images/menu/fx.png")));
        master_row.add(anim_master);
        addLeft(anim, master_row);

        addLeft(anim, animCheckbox("/images/menu/video.png", "menu.cinematicas",
                gf != null ? gf.getMenu_cinematicas() : null, "cinematicas", v -> GameFrame.CINEMATICAS_PREF = v));
        // --- Barajado (solo Ajustes, sin item de menú) + su subajuste Cascada SRA ---
        // Al activarlo re-calienta la caché del shuffle.gif (el warm-up de arranque pudo saltárselo).
        // Padre + subcontroles anidados dentro de un recuadro fino que los agrupa.
        JPanel barajado_group = groupBox();
        addToGroup(barajado_group, animCheckbox("/images/menu/baraja.png", "menu.efectos_animacion_barajado",
                null, "animacion_barajado",
                v -> { GameFrame.ANIMACION_BARAJADO_PREF = v; if (v) { Crupier.warmShuffleAnimCache(); } },
                GameFrame.ANIMACION_BARAJADO_PREF));
        final JCheckBox barajado_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Cascada SRA: overlay de barajado por jugador. Cuelga (más sangrado) de "Barajado": se
        // deshabilita si se desmarca "Barajado" o el maestro. Persist-only (sin item de menú); se
        // construye a mano (no vía animCheckbox) para gatear su habilitación por "Barajado", no solo
        // por el maestro.
        {
            final JCheckBox cascada_cb = new JCheckBox(Translator.translate("menu.efectos_animacion_cascada_overlay"),
                    prefBool("animacion_cascada_overlay", false));
            cascada_cb.addActionListener(e -> {
                boolean now = cascada_cb.isSelected();
                persist("animacion_cascada_overlay", String.valueOf(now));
                GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = now;
            });
            Runnable updateCascadaEnabled = () -> cascada_cb.setEnabled(anim_master.isSelected() && barajado_cb.isSelected());
            anim_master.addActionListener(e -> updateCascadaEnabled.run());
            barajado_cb.addActionListener(e -> updateCascadaEnabled.run());
            updateCascadaEnabled.run();
            JPanel cascada_row = naturalRow();
            cascada_row.add(Box.createHorizontalStrut(36)); // sub de "Barajado"
            cascada_row.add(new JLabel(icon("/images/menu/baraja.png")));
            cascada_row.add(cascada_cb);
            addToGroup(barajado_group, cascada_row);
        }
        addLeft(anim, barajado_group);

        // --- Reparto (era "Cartas", conserva su item de menú y la clave "animacion_reparto") ---
        JPanel reparto_group = groupBox();
        addToGroup(reparto_group, animCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_reparto",
                gf != null ? gf.getAnim_reparto_menu() : null, "animacion_reparto", v -> GameFrame.ANIMACION_REPARTO_PREF = v));
        final JCheckBox reparto_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Velocidad del reparto: 3 opciones (lento/normal/rápido). "Normal" = velocidad histórica
        // EXACTA (REPARTO_VELOCIDAD 100 -> factor 1.0). Cuelga de "Reparto": se deshabilita si se
        // desmarca "Reparto" o el maestro. Guarda el % de la pausa base (GameFrame.REPARTO_VELOCIDAD).
        {
            final int[] speed_pct = {150, GameFrame.DEFAULT_REPARTO_VELOCIDAD, 60}; // lento, normal, rápido
            final String[] speed_keys = {"settings.reparto_lento", "settings.reparto_normal", "settings.reparto_rapido"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel deal_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> deal_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selecciona la opción cuyo % guardado sea el más cercano (por defecto Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_pct.length; i++) {
                int d = Math.abs(speed_pct[i] - GameFrame.REPARTO_VELOCIDAD);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            deal_combo.setSelectedIndex(sel);
            deal_combo.setMaximumSize(deal_combo.getPreferredSize());
            deal_combo.addActionListener(e -> {
                int pct = speed_pct[deal_combo.getSelectedIndex()];
                GameFrame.REPARTO_VELOCIDAD = pct;
                persist("reparto_velocidad", String.valueOf(pct));
            });
            Helpers.setTranslatedToolTip(deal_combo, "tooltip.cfg.reparto_velocidad");

            Runnable updateDealEnabled = () -> {
                boolean on = anim_master.isSelected() && reparto_cb.isSelected();
                deal_combo.setEnabled(on);
                deal_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateDealEnabled.run());
            reparto_cb.addActionListener(e -> updateDealEnabled.run());
            updateDealEnabled.run();

            JPanel deal_row = naturalRow();
            deal_row.add(Box.createHorizontalStrut(36)); // sub de "Reparto"
            deal_row.add(new JLabel(icon("/images/menu/clock.png")));
            deal_row.add(deal_text);
            deal_row.add(deal_combo);
            addToGroup(reparto_group, deal_row);
        }
        addLeft(anim, reparto_group);

        // --- Destapar (era la parte de giro del antiguo "Cartas", ahora propio, solo Ajustes) ---
        // De él cuelgan la velocidad del destape y el efecto acercar.
        JPanel destapar_group = groupBox();
        addToGroup(destapar_group, animCheckbox("/images/menu/pica_roja.png", "menu.efectos_animacion_destape",
                null, "animacion_destape", v -> GameFrame.ANIMACION_DESTAPE_PREF = v, GameFrame.ANIMACION_DESTAPE_PREF));
        final JCheckBox destapar_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Velocidad del destape: 5 opciones (muy lenta ... muy rápida). "Normal" es el valor
        // por defecto exacto. Cuelga (más sangrado) del ajuste "Destapar": se deshabilita si se
        // desmarca "Destapar" o el maestro. Guarda la duración en ms (GameFrame.CARD_FLIP_DURATION).
        {
            final int[] speed_ms = {1100, 850, GameFrame.DEFAULT_CARD_FLIP_DURATION, 480, 350}; // muy lenta -> muy rápida
            final String[] speed_keys = {"settings.destape_muy_lenta", "settings.destape_lenta",
                "settings.destape_normal", "settings.destape_rapida", "settings.destape_muy_rapida"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel flip_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> speed_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selecciona la opción cuyo ms guardado sea el más cercano (por defecto Normal).
            int sel = 2, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.CARD_FLIP_DURATION);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            speed_combo.setSelectedIndex(sel);
            speed_combo.setMaximumSize(speed_combo.getPreferredSize());
            speed_combo.addActionListener(e -> {
                int ms = speed_ms[speed_combo.getSelectedIndex()];
                GameFrame.CARD_FLIP_DURATION = ms;
                persist("card_flip_duration", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(speed_combo, "tooltip.cfg.card_flip_duration");

            // Habilitado solo si el maestro de animaciones Y el checkbox "Destapar" están activos.
            Runnable updateFlipEnabled = () -> {
                boolean on = anim_master.isSelected() && destapar_cb.isSelected();
                speed_combo.setEnabled(on);
                flip_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateFlipEnabled.run());
            destapar_cb.addActionListener(e -> updateFlipEnabled.run());
            updateFlipEnabled.run();

            JPanel flip_row = naturalRow();
            flip_row.add(Box.createHorizontalStrut(36)); // más sangrado: cuelga de "Destapar"
            flip_row.add(new JLabel(icon("/images/menu/clock.png")));
            flip_row.add(flip_text);
            flip_row.add(speed_combo);
            addToGroup(destapar_group, flip_row);
        }
        // Efecto "acercar": 4 opciones (desactivado ... fuerte). Cuelga de "Destapar" igual que la
        // velocidad. Guarda el porcentaje de agrandado (GameFrame.CARD_FLIP_ZOOM): 100 = desactivado.
        {
            final int[] acercar_pct = {100, 115, 130, 145}; // desactivado, suave, normal, fuerte
            final String[] zoom_keys = {"settings.acercar_desactivado", "settings.acercar_suave",
                "settings.acercar_normal", "settings.acercar_fuerte"};
            final String[] zoom_labels = new String[zoom_keys.length];
            for (int i = 0; i < zoom_keys.length; i++) {
                zoom_labels[i] = Translator.translate(zoom_keys[i]);
            }

            final JLabel zoom_text = new JLabel(Translator.translate("settings.efecto_acercar") + ":");
            final javax.swing.JComboBox<String> zoom_combo = new javax.swing.JComboBox<>(zoom_labels);

            // Selecciona la opción cuyo porcentaje guardado sea el más cercano (por defecto Desactivado).
            int sel = 0, best = Integer.MAX_VALUE;
            for (int i = 0; i < acercar_pct.length; i++) {
                int d = Math.abs(acercar_pct[i] - GameFrame.CARD_FLIP_ZOOM);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            zoom_combo.setSelectedIndex(sel);
            zoom_combo.setMaximumSize(zoom_combo.getPreferredSize());
            zoom_combo.addActionListener(e -> {
                int pct = acercar_pct[zoom_combo.getSelectedIndex()];
                GameFrame.CARD_FLIP_ZOOM = pct;
                persist("card_flip_zoom", String.valueOf(pct));
            });
            Helpers.setTranslatedToolTip(zoom_combo, "tooltip.cfg.card_flip_zoom");

            // Habilitado solo si el maestro de animaciones Y el checkbox "Destapar" están activos.
            Runnable updateZoomEnabled = () -> {
                boolean on = anim_master.isSelected() && destapar_cb.isSelected();
                zoom_combo.setEnabled(on);
                zoom_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateZoomEnabled.run());
            destapar_cb.addActionListener(e -> updateZoomEnabled.run());
            updateZoomEnabled.run();

            JPanel zoom_row = naturalRow();
            zoom_row.add(Box.createHorizontalStrut(36)); // mismo sangrado que la velocidad
            zoom_row.add(new JLabel(icon("/images/menu/zoom_in.png")));
            zoom_row.add(zoom_text);
            zoom_row.add(zoom_combo);
            addToGroup(destapar_group, zoom_row);
        }
        addLeft(anim, destapar_group);

        // --- Ordenar la mano (cruce animado de tus dos hole cards al ordenarlas, solo Ajustes) ---
        // De él cuelga la velocidad del cruce.
        JPanel swap_group = groupBox();
        addToGroup(swap_group, animCheckbox("/images/menu/swap.png", "menu.efectos_animacion_swap",
                null, "animacion_swap", v -> GameFrame.ANIMACION_SWAP_PREF = v, GameFrame.ANIMACION_SWAP_PREF));
        final JCheckBox swap_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Velocidad del cruce: 3 opciones (lento/normal/rápido). "Normal" = valor por defecto
        // (320 ms). Cuelga del ajuste: se deshabilita si se desmarca o el maestro está off.
        // Guarda la duración en ms (GameFrame.SWAP_ANIM_DURATION).
        {
            final int[] speed_ms = {520, GameFrame.DEFAULT_SWAP_ANIM_DURATION, 200}; // lento, normal, rápido
            final String[] speed_keys = {"settings.reparto_lento", "settings.reparto_normal", "settings.reparto_rapido"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel swap_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> swap_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selecciona la opción cuyo ms guardado sea el más cercano (por defecto Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.SWAP_ANIM_DURATION);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            swap_combo.setSelectedIndex(sel);
            swap_combo.setMaximumSize(swap_combo.getPreferredSize());
            swap_combo.addActionListener(e -> {
                int ms = speed_ms[swap_combo.getSelectedIndex()];
                GameFrame.SWAP_ANIM_DURATION = ms;
                persist("swap_velocidad", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(swap_combo, "tooltip.cfg.swap_velocidad");

            Runnable updateSwapEnabled = () -> {
                boolean on = anim_master.isSelected() && swap_cb.isSelected();
                swap_combo.setEnabled(on);
                swap_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateSwapEnabled.run());
            swap_cb.addActionListener(e -> updateSwapEnabled.run());
            updateSwapEnabled.run();

            JPanel swap_row = naturalRow();
            swap_row.add(Box.createHorizontalStrut(36)); // sub de "Ordenar la mano"
            swap_row.add(new JLabel(icon("/images/menu/clock.png")));
            swap_row.add(swap_text);
            swap_row.add(swap_combo);
            addToGroup(swap_group, swap_row);
        }
        // Estilo del cruce: 2 opciones (Arco "saltito" / Horizontal). Cuelga del ajuste
        // "Ordenar la mano" igual que la velocidad. Guarda un booleano (GameFrame.SWAP_ANIM_ARC).
        {
            final String[] style_keys = {"settings.swap_arco", "settings.swap_horizontal"};
            final String[] style_labels = new String[style_keys.length];
            for (int i = 0; i < style_keys.length; i++) {
                style_labels[i] = Translator.translate(style_keys[i]);
            }

            final JLabel style_text = new JLabel(Translator.translate("settings.swap_estilo") + ":");
            final javax.swing.JComboBox<String> style_combo = new javax.swing.JComboBox<>(style_labels);
            style_combo.setSelectedIndex(GameFrame.SWAP_ANIM_ARC ? 0 : 1);
            style_combo.setMaximumSize(style_combo.getPreferredSize());
            style_combo.addActionListener(e -> {
                boolean arc = style_combo.getSelectedIndex() == 0;
                GameFrame.SWAP_ANIM_ARC = arc;
                persist("swap_arco", String.valueOf(arc));
            });
            Helpers.setTranslatedToolTip(style_combo, "tooltip.cfg.swap_estilo");

            Runnable updateStyleEnabled = () -> {
                boolean on = anim_master.isSelected() && swap_cb.isSelected();
                style_combo.setEnabled(on);
                style_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateStyleEnabled.run());
            swap_cb.addActionListener(e -> updateStyleEnabled.run());
            updateStyleEnabled.run();

            JPanel style_row = naturalRow();
            style_row.add(Box.createHorizontalStrut(36)); // mismo sangrado que la velocidad
            style_row.add(new JLabel(icon("/images/menu/swap.png")));
            style_row.add(style_text);
            style_row.add(style_combo);
            addToGroup(swap_group, style_row);
        }
        addLeft(anim, swap_group);

        // --- Recolocación de la mesa al salir jugadores (DynamicTablePanel, solo Ajustes) ---
        // De él cuelga la velocidad de la animación de deslizamiento.
        JPanel downgrade_group = groupBox();
        addToGroup(downgrade_group, animCheckbox("/images/menu/tiny.png", "menu.efectos_animacion_downgrade",
                null, "animacion_downgrade", v -> GameFrame.ANIMACION_DOWNGRADE_PREF = v, GameFrame.ANIMACION_DOWNGRADE_PREF));
        final JCheckBox downgrade_cb = anim_sub_cb.get(anim_sub_cb.size() - 1);
        // Velocidad de la recolocación: 3 opciones (lenta/normal/rápida). "Normal" = valor por
        // defecto (500 ms). Cuelga del ajuste: se deshabilita si se desmarca o el maestro está off.
        // Guarda la duración en ms (GameFrame.DOWNGRADE_VELOCIDAD).
        {
            final int[] speed_ms = {800, GameFrame.DEFAULT_DOWNGRADE_VELOCIDAD, 300}; // lenta, normal, rápida
            final String[] speed_keys = {"settings.downgrade_lento", "settings.downgrade_normal", "settings.downgrade_rapido"};
            final String[] speed_labels = new String[speed_keys.length];
            for (int i = 0; i < speed_keys.length; i++) {
                speed_labels[i] = Translator.translate(speed_keys[i]);
            }

            final JLabel dg_text = new JLabel(Translator.translate("settings.velocidad") + ":");
            final javax.swing.JComboBox<String> dg_combo = new javax.swing.JComboBox<>(speed_labels);

            // Selecciona la opción cuyo ms guardado sea el más cercano (por defecto Normal).
            int sel = 1, best = Integer.MAX_VALUE;
            for (int i = 0; i < speed_ms.length; i++) {
                int d = Math.abs(speed_ms[i] - GameFrame.DOWNGRADE_VELOCIDAD);
                if (d < best) {
                    best = d;
                    sel = i;
                }
            }
            dg_combo.setSelectedIndex(sel);
            dg_combo.setMaximumSize(dg_combo.getPreferredSize());
            dg_combo.addActionListener(e -> {
                int ms = speed_ms[dg_combo.getSelectedIndex()];
                GameFrame.DOWNGRADE_VELOCIDAD = ms;
                persist("downgrade_velocidad", String.valueOf(ms));
            });
            Helpers.setTranslatedToolTip(dg_combo, "tooltip.cfg.downgrade_velocidad");

            Runnable updateDgEnabled = () -> {
                boolean on = anim_master.isSelected() && downgrade_cb.isSelected();
                dg_combo.setEnabled(on);
                dg_text.setEnabled(on);
            };
            anim_master.addActionListener(e -> updateDgEnabled.run());
            downgrade_cb.addActionListener(e -> updateDgEnabled.run());
            updateDgEnabled.run();

            JPanel dg_row = naturalRow();
            dg_row.add(Box.createHorizontalStrut(36)); // sub del ajuste
            dg_row.add(new JLabel(icon("/images/menu/clock.png")));
            dg_row.add(dg_text);
            dg_row.add(dg_combo);
            addToGroup(downgrade_group, dg_row);
        }
        addLeft(anim, downgrade_group);

        addLeft(anim, animCheckbox("/images/menu/dealer.png", "menu.efectos_animacion_ciegas_dealer",
                gf != null ? gf.getAnim_ciegas_dealer_menu() : null, "animacion_ciegas_dealer", v -> GameFrame.ANIMACION_CIEGAS_DEALER_PREF = v));
        addLeft(anim, animCheckbox("/images/menu/rebuy.png", "menu.efectos_animacion_apuestas",
                gf != null ? gf.getAnim_apuestas_menu() : null, "animacion_apuestas", v -> GameFrame.ANIMACION_APUESTAS_PREF = v));
        addLeft(anim, animCheckbox("/images/menu/meter.png", "menu.efectos_animacion_contadores",
                gf != null ? gf.getAnim_contadores_menu() : null, "animacion_contadores", v -> GameFrame.ANIMACION_CONTADORES_PREF = v));

        // Fila Animaciones | (Mesa sobre Pantalla) a su ALTO NATURAL en el NORTE, alineadas arriba
        // a la izquierda. Animaciones (la columna más alta desde que agrupa Barajado/Reparto/Destapar)
        // va SOLA a la izquierda y las dos más bajas (Mesa y Pantalla) se apilan a la derecha, para
        // equilibrar alturas y que el diálogo quede menos alto. El hueco sobrante cae a la derecha y
        // abajo (mismo patrón que la pestaña Partida) sin estirar ni recortar los subpaneles.
        anim.setAlignmentY(JComponent.TOP_ALIGNMENT);
        mesa.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        pantalla.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        // Glue al fondo de Animaciones: si esta columna resultara la MÁS CORTA, al estirarla para
        // igualar alturas el hueco se recoge limpio abajo (dentro de su recuadro).
        closeColumn(anim);

        JPanel right_inner = new JPanel();
        right_inner.setLayout(new BoxLayout(right_inner, BoxLayout.Y_AXIS));
        right_inner.setAlignmentY(JComponent.TOP_ALIGNMENT);
        right_inner.add(mesa);
        right_inner.add(Box.createVerticalStrut(10));
        // Glue entre Mesa y Pantalla: al estirar la columna derecha para igualar la altura de
        // Animaciones, el hueco se mete AQUÍ y Pantalla baja hasta que su borde inferior queda
        // ALINEADO con el de Animaciones (Mesa se queda arriba).
        right_inner.add(Box.createVerticalGlue());
        right_inner.add(pantalla);

        // Ambas columnas se estiran en vertical hasta la altura de la más alta (BoxLayout X con
        // el máximo sin tope) para que sus bordes inferiores queden alineados.
        anim.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        right_inner.setMaximumSize(new java.awt.Dimension(Short.MAX_VALUE, Short.MAX_VALUE));

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(anim);
        row.add(Box.createHorizontalStrut(12));
        row.add(right_inner);

        add(row, java.awt.BorderLayout.NORTH);

        building = false;
    }

    // Aplica el modo de pantalla elegido en el combo. Lo invoca el diálogo al GUARDAR
    // (no en vivo: el toggle dispone y recrea el frame y corrompería el diálogo abierto).
    // Solo actúa si el usuario CAMBIÓ el combo respecto al estado de apertura; si no, no
    // toca AUTO_FULLSCREEN, para que guardar un ajuste no relacionado no reescriba la
    // preferencia de arranque (p.ej. tras un ALT+F transitorio que no la cambia). En
    // partida cambia el modo del frame; fuera de partida solo persiste la preferencia.
    public void applyPendingDisplayMode() {
        if (pending_fullscreen == snap_fullscreen) {
            return;
        }
        if (gf != null) {
            gf.setDisplayModeFullScreen(pending_fullscreen);
        } else {
            GameFrame.AUTO_FULLSCREEN = pending_fullscreen;
            persist("auto_fullscreen", String.valueOf(pending_fullscreen));
        }
    }

    // ¿Hay cambios de apariencia respecto al estado de apertura? (incluye el modo de
    // pantalla pendiente, que aún no se ha aplicado). Lo usa el diálogo para preguntar
    // antes de descartar al cancelar. Las preferencias de animación se leen de PROPERTIES
    // (equivalente al item de menú, ver nota de clase), así que no depende de gf.
    public boolean isDirty() {
        return GameFrame.ZOOM_LEVEL != snap_zoom_level
                || GameFrame.VISTA_COMPACTA != snap_vista_compacta
                || !snap_baraja.equals(GameFrame.BARAJA)
                || !snap_trasera.equals(GameFrame.TRASERA)
                || !snap_color_tapete.equals(GameFrame.COLOR_TAPETE)
                || GameFrame.AUTO_ZOOM != snap_auto_zoom
                || GameFrame.SHOW_CLOCK != snap_show_clock
                || GameFrame.MOSTRAR_COSTE_IGUALAR != snap_coste_igualar
                || prefBool("cinematicas") != snap_cinematicas
                || GameFrame.ANIMACION_BARAJADO_PREF != snap_anim_barajado
                || prefBool("animacion_reparto") != snap_anim_reparto
                || GameFrame.ANIMACION_DESTAPE_PREF != snap_anim_destape
                || prefBool("animacion_ciegas_dealer") != snap_anim_ciegas_dealer
                || prefBool("animacion_apuestas") != snap_anim_apuestas
                || prefBool("animacion_contadores") != snap_anim_contadores
                || prefBool("animacion_cascada_overlay", false) != snap_anim_cascada_overlay
                || prefBool("resaltar_jugada_perdedor", true) != snap_resaltar_jugada_perdedor
                || GameFrame.ANIMACIONES != snap_animaciones
                || GameFrame.CHAT_IMAGES_INGAME != snap_chat_images
                || pending_fullscreen != snap_fullscreen
                || GameFrame.CARD_FLIP_DURATION != snap_card_flip_duration
                || GameFrame.CARD_FLIP_ZOOM != snap_card_flip_zoom
                || GameFrame.REPARTO_VELOCIDAD != snap_reparto_velocidad
                || GameFrame.ANIMACION_DOWNGRADE_PREF != snap_anim_downgrade
                || GameFrame.DOWNGRADE_VELOCIDAD != snap_downgrade_velocidad;
    }

    // Revierte (al CANCELAR el diálogo transaccional) los ajustes de apariencia al
    // estado capturado al abrir. En partida re-aplica cada uno por su camino normal
    // (efecto en vivo); fuera de partida solo re-persiste las preferencias.
    public void revert() {
        if (gf != null) {
            revertLive();
        } else {
            revertStandalone();
        }
    }

    // Revert EN PARTIDA: re-aplica cada ajuste por su camino normal (toggles por doClick
    // si difieren; zoom/compacta por su setter; baraja/tapete re-seleccionando el radio).
    // El modo de pantalla pendiente NO se aplica si se cancela.
    private void revertLive() {
        if (GameFrame.ZOOM_LEVEL != snap_zoom_level) {
            gf.setZoomLevel(snap_zoom_level);
        }
        if (GameFrame.VISTA_COMPACTA != snap_vista_compacta) {
            gf.setCompactView(snap_vista_compacta);
        }
        if (!snap_baraja.equals(GameFrame.BARAJA)) {
            selectBaraja(gf, snap_baraja);
        }
        if (!snap_trasera.equals(GameFrame.TRASERA)) {
            gf.setTrasera(snap_trasera);
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
        // Animaciones (transaccional con maestro): las preferencias viven en el isSelected
        // de cada item y SOLO se revierten con doClick sobre un item HABILITADO (estan
        // deshabilitados con el maestro off). Por eso: habilitar (maestro on) -> revertir
        // cada preferencia (comparando isSelected con su snapshot) -> restaurar el maestro a
        // su snapshot, que re-gatea y re-deshabilita si tocaba. (Si dejaramos el maestro off
        // primero, los doClick caerian sobre items deshabilitados = NO-OP y la preferencia
        // no se revertiria.)
        if (GameFrame.ANIMACIONES != snap_animaciones
                || gf.getMenu_cinematicas().isSelected() != snap_cinematicas
                || gf.getAnim_reparto_menu().isSelected() != snap_anim_reparto
                || gf.getAnim_ciegas_dealer_menu().isSelected() != snap_anim_ciegas_dealer
                || gf.getAnim_apuestas_menu().isSelected() != snap_anim_apuestas
                || gf.getAnim_contadores_menu().isSelected() != snap_anim_contadores) {
            gf.setAnimacionesMaster(true);
            if (gf.getMenu_cinematicas().isSelected() != snap_cinematicas) {
                gf.getMenu_cinematicas().doClick();
            }
            if (gf.getAnim_reparto_menu().isSelected() != snap_anim_reparto) {
                gf.getAnim_reparto_menu().doClick();
            }
            if (gf.getAnim_ciegas_dealer_menu().isSelected() != snap_anim_ciegas_dealer) {
                gf.getAnim_ciegas_dealer_menu().doClick();
            }
            if (gf.getAnim_apuestas_menu().isSelected() != snap_anim_apuestas) {
                gf.getAnim_apuestas_menu().doClick();
            }
            if (gf.getAnim_contadores_menu().isSelected() != snap_anim_contadores) {
                gf.getAnim_contadores_menu().doClick();
            }
            gf.setAnimacionesMaster(snap_animaciones);
        }
        // El overlay de cascada no tiene item de menú ni efecto en vivo (solo aparece durante el
        // barajado): se revierte fijando el flag directamente + persistiendo, como CHAT_IMAGES.
        if (GameFrame.ANIMACION_CASCADA_OVERLAY_PREF != snap_anim_cascada_overlay) {
            GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = snap_anim_cascada_overlay;
            Helpers.PROPERTIES.setProperty("animacion_cascada_overlay", String.valueOf(snap_anim_cascada_overlay));
            Helpers.savePropertiesFile();
        }
        // Resaltado del showdown: persist-only, sin item de menú ni efecto en vivo (se lee al
        // vuelo). Se revierte fijando el flag + re-persistiendo el snapshot, como la cascada.
        if (GameFrame.RESALTAR_JUGADA_PERDEDOR != snap_resaltar_jugada_perdedor) {
            GameFrame.RESALTAR_JUGADA_PERDEDOR = snap_resaltar_jugada_perdedor;
            Helpers.PROPERTIES.setProperty("resaltar_jugada_perdedor", String.valueOf(snap_resaltar_jugada_perdedor));
            Helpers.savePropertiesFile();
        }
        // Barajado y destape tampoco tienen item de menú: se revierten fijando el flag +
        // persistiendo, como el overlay de cascada. Al restaurar el barajado a ON se recalienta
        // la caché del shuffle.gif por si el warm-up se saltó mientras estuvo desactivado.
        if (GameFrame.ANIMACION_BARAJADO_PREF != snap_anim_barajado) {
            GameFrame.ANIMACION_BARAJADO_PREF = snap_anim_barajado;
            Helpers.PROPERTIES.setProperty("animacion_barajado", String.valueOf(snap_anim_barajado));
            Helpers.savePropertiesFile();
            if (snap_anim_barajado) {
                Crupier.warmShuffleAnimCache();
            }
        }
        if (GameFrame.ANIMACION_DESTAPE_PREF != snap_anim_destape) {
            GameFrame.ANIMACION_DESTAPE_PREF = snap_anim_destape;
            Helpers.PROPERTIES.setProperty("animacion_destape", String.valueOf(snap_anim_destape));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.CHAT_IMAGES_INGAME != snap_chat_images) {
            gf.getChat_image_menu().doClick();
        }
        // Velocidad del destape: sin item de menú (como el overlay de cascada), se revierte
        // fijando el flag + re-persistiendo el snapshot.
        if (GameFrame.CARD_FLIP_DURATION != snap_card_flip_duration) {
            GameFrame.CARD_FLIP_DURATION = snap_card_flip_duration;
            Helpers.PROPERTIES.setProperty("card_flip_duration", String.valueOf(snap_card_flip_duration));
            Helpers.savePropertiesFile();
        }
        // Efecto acercar: mismo camino que la velocidad (sin item de menú ni efecto en vivo).
        if (GameFrame.CARD_FLIP_ZOOM != snap_card_flip_zoom) {
            GameFrame.CARD_FLIP_ZOOM = snap_card_flip_zoom;
            Helpers.PROPERTIES.setProperty("card_flip_zoom", String.valueOf(snap_card_flip_zoom));
            Helpers.savePropertiesFile();
        }
        // Velocidad del reparto: mismo camino (persist-only).
        if (GameFrame.REPARTO_VELOCIDAD != snap_reparto_velocidad) {
            GameFrame.REPARTO_VELOCIDAD = snap_reparto_velocidad;
            Helpers.PROPERTIES.setProperty("reparto_velocidad", String.valueOf(snap_reparto_velocidad));
            Helpers.savePropertiesFile();
        }
        // Recolocación de la mesa (checkbox + velocidad): persist-only, sin item de menú.
        if (GameFrame.ANIMACION_DOWNGRADE_PREF != snap_anim_downgrade) {
            GameFrame.ANIMACION_DOWNGRADE_PREF = snap_anim_downgrade;
            Helpers.PROPERTIES.setProperty("animacion_downgrade", String.valueOf(snap_anim_downgrade));
            Helpers.savePropertiesFile();
        }
        if (GameFrame.DOWNGRADE_VELOCIDAD != snap_downgrade_velocidad) {
            GameFrame.DOWNGRADE_VELOCIDAD = snap_downgrade_velocidad;
            Helpers.PROPERTIES.setProperty("downgrade_velocidad", String.valueOf(snap_downgrade_velocidad));
            Helpers.savePropertiesFile();
        }
    }

    // Revert FUERA DE PARTIDA: re-persiste cada preferencia a su snapshot (sin efecto en
    // vivo, no hay mesa). Fija los flags estáticos y vuelca PROPERTIES una sola vez.
    private void revertStandalone() {
        // El tapete es el único ajuste con previsualización en vivo fuera de partida (fondo
        // del lanzador); si cambió durante la sesión hay que repintar el inicio al revertir.
        boolean tapete_changed = !snap_color_tapete.equals(GameFrame.COLOR_TAPETE);

        GameFrame.ZOOM_LEVEL = snap_zoom_level;
        GameFrame.VISTA_COMPACTA = snap_vista_compacta;
        GameFrame.BARAJA = snap_baraja;
        GameFrame.TRASERA = snap_trasera;
        GameFrame.COLOR_TAPETE = snap_color_tapete;
        GameFrame.AUTO_ZOOM = snap_auto_zoom;
        GameFrame.SHOW_CLOCK = snap_show_clock;
        GameFrame.MOSTRAR_COSTE_IGUALAR = snap_coste_igualar;
        GameFrame.CHAT_IMAGES_INGAME = snap_chat_images;
        GameFrame.ANIMACIONES = snap_animaciones;
        GameFrame.CINEMATICAS_PREF = snap_cinematicas;
        GameFrame.ANIMACION_BARAJADO_PREF = snap_anim_barajado;
        GameFrame.ANIMACION_REPARTO_PREF = snap_anim_reparto;
        GameFrame.ANIMACION_DESTAPE_PREF = snap_anim_destape;
        GameFrame.ANIMACION_CIEGAS_DEALER_PREF = snap_anim_ciegas_dealer;
        GameFrame.ANIMACION_APUESTAS_PREF = snap_anim_apuestas;
        GameFrame.ANIMACION_CONTADORES_PREF = snap_anim_contadores;
        GameFrame.ANIMACION_CASCADA_OVERLAY_PREF = snap_anim_cascada_overlay;
        GameFrame.RESALTAR_JUGADA_PERDEDOR = snap_resaltar_jugada_perdedor;
        GameFrame.CARD_FLIP_DURATION = snap_card_flip_duration;
        GameFrame.CARD_FLIP_ZOOM = snap_card_flip_zoom;
        GameFrame.REPARTO_VELOCIDAD = snap_reparto_velocidad;
        GameFrame.ANIMACION_DOWNGRADE_PREF = snap_anim_downgrade;
        GameFrame.DOWNGRADE_VELOCIDAD = snap_downgrade_velocidad;

        Helpers.PROPERTIES.setProperty("zoom_level", String.valueOf(snap_zoom_level));
        Helpers.PROPERTIES.setProperty("vista_compacta", String.valueOf(snap_vista_compacta));
        Helpers.PROPERTIES.setProperty("baraja", snap_baraja);
        Helpers.PROPERTIES.setProperty("trasera", snap_trasera);
        Helpers.PROPERTIES.setProperty("color_tapete", snap_color_tapete);
        Helpers.PROPERTIES.setProperty("auto_zoom", String.valueOf(snap_auto_zoom));
        Helpers.PROPERTIES.setProperty("show_time", String.valueOf(snap_show_clock));
        Helpers.PROPERTIES.setProperty("mostrar_coste_igualar", String.valueOf(snap_coste_igualar));
        Helpers.PROPERTIES.setProperty("chat_images_ingame", String.valueOf(snap_chat_images));
        Helpers.PROPERTIES.setProperty("animaciones", String.valueOf(snap_animaciones));
        Helpers.PROPERTIES.setProperty("cinematicas", String.valueOf(snap_cinematicas));
        Helpers.PROPERTIES.setProperty("animacion_barajado", String.valueOf(snap_anim_barajado));
        Helpers.PROPERTIES.setProperty("animacion_reparto", String.valueOf(snap_anim_reparto));
        Helpers.PROPERTIES.setProperty("animacion_destape", String.valueOf(snap_anim_destape));
        Helpers.PROPERTIES.setProperty("animacion_ciegas_dealer", String.valueOf(snap_anim_ciegas_dealer));
        Helpers.PROPERTIES.setProperty("animacion_apuestas", String.valueOf(snap_anim_apuestas));
        Helpers.PROPERTIES.setProperty("animacion_contadores", String.valueOf(snap_anim_contadores));
        Helpers.PROPERTIES.setProperty("animacion_cascada_overlay", String.valueOf(snap_anim_cascada_overlay));
        Helpers.PROPERTIES.setProperty("resaltar_jugada_perdedor", String.valueOf(snap_resaltar_jugada_perdedor));
        Helpers.PROPERTIES.setProperty("card_flip_duration", String.valueOf(snap_card_flip_duration));
        Helpers.PROPERTIES.setProperty("card_flip_zoom", String.valueOf(snap_card_flip_zoom));
        Helpers.PROPERTIES.setProperty("reparto_velocidad", String.valueOf(snap_reparto_velocidad));
        Helpers.PROPERTIES.setProperty("animacion_downgrade", String.valueOf(snap_anim_downgrade));
        Helpers.PROPERTIES.setProperty("downgrade_velocidad", String.valueOf(snap_downgrade_velocidad));
        Helpers.savePropertiesFile();

        if (tapete_changed) {
            refreshLauncherTapete();
        }
    }

    // Repinta al vuelo el fondo de la pantalla de inicio (InitPanel) con el COLOR_TAPETE
    // actual: es la previsualización en vivo del tapete fuera de partida. No-op si el
    // lanzador aún no existe (arranque). El InitPanel recarga la textura en segundo plano,
    // y para los colores base es independiente del tamaño del panel, así que es seguro
    // aunque el lanzador esté oculto (p. ej. abriendo el diálogo desde la sala de espera).
    // Reproduce ademas el mismo efecto de sonido (mat.wav) que TablePanel.refresh() en
    // partida, para que cambiar (o revertir) el tapete suene igual dentro y fuera de la
    // mesa. El sonido va DENTRO del guard y aqui, NO en InitPanel.refresh(): ese metodo
    // tambien corre en el resize del tapete secreto "*", donde no debe sonar.
    private static void refreshLauncherTapete() {
        if (Init.VENTANA_INICIO != null) {
            Audio.playWavResource("misc/mat.wav");
            Init.VENTANA_INICIO.getTapete().refresh();
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

    // Color base del tapete para el índice del combo (0=verde..4=madera). Fuera de
    // partida se persiste este valor base (sin sufijos de easter-egg, que solo se
    // resuelven en la mesa viva).
    private static String tapeteColorForIndex(int idx) {
        switch (idx) {
            case 1:
                return "azul";
            case 2:
                return "rojo";
            case 3:
                return "negro";
            case 4:
                return "madera";
            default:
                return "verde";
        }
    }

    // Lee una preferencia booleana de PROPERTIES (todas las de animación tienen default
    // true). Equivalente a leer el isSelected del item de menú (ver nota de clase) y no
    // depende de que haya GameFrame.
    private static boolean prefBool(String key) {
        return prefBool(key, true);
    }

    private static boolean prefBool(String key, boolean def) {
        return Boolean.parseBoolean(Helpers.PROPERTIES.getProperty(key, String.valueOf(def)));
    }

    // Persiste una preferencia (clave -> valor) sin efecto en vivo. Lo usan los controles
    // en modo fuera-de-partida.
    private static void persist(String key, String value) {
        Helpers.PROPERTIES.setProperty(key, value);
        Helpers.savePropertiesFile();
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

    // Recuadro de agrupación de BORDE NEGRO FINO y esquinas redondeadas para los checkboxes de
    // animación que tienen subcontroles anidados (Barajado/Reparto/Destapar): envuelve el padre y
    // sus subcontroles para que se lean como un grupo. Transparente (deja ver el fondo del diálogo
    // Nimbus, texto intacto); solo dibuja el contorno. Alto natural (no se estira en el BoxLayout Y).
    private JPanel groupBox() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(0, 0, 0, 150));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }

            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Short.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return p;
    }

    // Añade una fila (checkbox padre o un subcontrol) al recuadro de grupo, con una separación
    // fina entre filas (más ceñida que el strut de addLeft, para que el grupo se lea compacto).
    private void addToGroup(JPanel group, JComponent row) {
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (group.getComponentCount() > 0) {
            group.add(Box.createVerticalStrut(4));
        }
        group.add(row);
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

    // Checkbox que REFLEJA un toggle de apariencia. En partida (menu != null) un clic =
    // un clic en el item de menú (aplica en vivo + persiste + refleja en el popup). Fuera
    // de partida (menu == null) ejecuta el persist-only suministrado. Ambos conmutan un
    // paso, así que quedan sincronizados con el estado.
    private JComponent delegatingCheckbox(String iconPath, String i18nKey, boolean selected, JMenuItem menu, Runnable standalone) {
        return delegatingCheckbox(iconPath, i18nKey, selected, menu, standalone, null);
    }

    private JComponent delegatingCheckbox(String iconPath, String i18nKey, boolean selected, JMenuItem menu, Runnable standalone, String tooltipKey) {
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), selected);
        cb.setEnabled(menu == null || menu.isEnabled());
        cb.addActionListener(e -> {
            if (menu != null) {
                menu.doClick();
            } else {
                standalone.run();
            }
        });
        // Icono a la izquierda (el mismo del antiguo ítem de menú) para dar paridad con
        // la pestaña Partida y con los menús que este diálogo sustituye.
        JPanel row = naturalRow();
        JLabel iconLabel = new JLabel(icon(iconPath));
        row.add(iconLabel);
        row.add(cb);
        // Tooltip opcional: se pone en la fila y en sus dos hijos para que aparezca en toda
        // la zona clicable (icono + checkbox + texto).
        if (tooltipKey != null) {
            Helpers.setTranslatedToolTip(row, tooltipKey);
            Helpers.setTranslatedToolTip(iconLabel, tooltipKey);
            Helpers.setTranslatedToolTip(cb, tooltipKey);
        }
        return row;
    }

    // Como delegatingCheckbox pero para los toggles de animacion gobernados por el maestro:
    // el estado MARCADO refleja la PREFERENCIA (leída del item de menú en partida, o de
    // PROPERTIES fuera de ella) y el checkbox se registra para que el maestro lo habilite/
    // deshabilite. Arranca deshabilitado si el maestro esta off. En partida (menu != null)
    // delega en el item; fuera de partida persiste la preferencia y fija el flag efectivo.
    private JComponent animCheckbox(String iconPath, String i18nKey, JMenuItem menu, String prefKey, Consumer<Boolean> effSetter) {
        return animCheckbox(iconPath, i18nKey, menu, prefKey, effSetter, true);
    }

    private JComponent animCheckbox(String iconPath, String i18nKey, JMenuItem menu, String prefKey, Consumer<Boolean> effSetter, boolean defaultPref) {
        boolean pref = (menu != null) ? menu.isSelected() : prefBool(prefKey, defaultPref);
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), pref);
        cb.setEnabled((menu == null || menu.isEnabled()) && GameFrame.ANIMACIONES);
        cb.addActionListener(e -> {
            if (menu != null) {
                menu.doClick();
            } else {
                boolean now = cb.isSelected();
                persist(prefKey, String.valueOf(now));
                effSetter.accept(now);
            }
        });
        anim_sub_cb.add(cb);
        anim_sub_menu.add(menu);
        // Sangrado: los individuales cuelgan visualmente del maestro "Usar animaciones".
        JPanel row = naturalRow();
        row.add(Box.createHorizontalStrut(18));
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
