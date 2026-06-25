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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

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

    private final JButton compact_button;

    public AppearanceSettingsPanel() {

        super();
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GameFrame gf = GameFrame.getInstance();

        // ---------------- Pantalla y zoom ----------------
        JPanel pantalla = titledColumn("settings.apariencia_pantalla");

        JButton full_screen_button = new JButton(Translator.translate("menu.pantalla_completa"));
        full_screen_button.addActionListener(e -> gf.getFull_screen_menu().doClick());
        addLeft(pantalla, full_screen_button);

        addLeft(pantalla, delegatingCheckbox("menu.activar_pantalla_completa_al_empezar", GameFrame.AUTO_FULLSCREEN, gf.getAuto_fullscreen_menu()));

        // Fila de zoom: - / Reset / +
        JPanel zoom_row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        zoom_row.add(new JLabel(Translator.translate("menu.zoom") + ":"));
        JButton zoom_out = new JButton("−");
        zoom_out.addActionListener(e -> gf.getZoom_menu_out().doClick());
        JButton zoom_reset = new JButton(Translator.translate("menu.reset"));
        zoom_reset.addActionListener(e -> gf.getZoom_menu_reset().doClick());
        JButton zoom_in = new JButton("+");
        zoom_in.addActionListener(e -> gf.getZoom_menu_in().doClick());
        zoom_row.add(zoom_out);
        zoom_row.add(zoom_reset);
        zoom_row.add(zoom_in);
        addLeft(pantalla, zoom_row);

        addLeft(pantalla, delegatingCheckbox("menu.auto_ajustar", GameFrame.AUTO_ZOOM, gf.getAuto_fit_zoom_menu()));

        // Vista compacta: tri-estado (0/1/2). El item de menú cicla en cada doClick;
        // el botón muestra el estado actual y lo re-lee tras delegar.
        compact_button = new JButton(compactLabel());
        compact_button.addActionListener(e -> {
            gf.getCompact_menu().doClick();
            compact_button.setText(compactLabel());
        });
        addLeft(pantalla, compact_button);

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
        addLeft(mesa, labeledRow("menu.barajas", baraja_combo));

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
        addLeft(mesa, labeledRow("menu.tapetes", tapete_combo));

        addLeft(mesa, delegatingCheckbox("menu.mostrar_reloj", GameFrame.SHOW_CLOCK, gf.getTime_menu()));
        addLeft(mesa, delegatingCheckbox("menu.coste_igualar", GameFrame.MOSTRAR_COSTE_IGUALAR, gf.getCoste_igualar_menu()));

        // ---------------- Animaciones y chat ----------------
        JPanel anim = titledColumn("settings.apariencia_animaciones");

        addLeft(anim, delegatingCheckbox("menu.cinematicas", GameFrame.CINEMATICAS, gf.getMenu_cinematicas()));
        addLeft(anim, delegatingCheckbox("menu.efectos_animacion_reparto", GameFrame.ANIMACION_REPARTO, gf.getAnim_reparto_menu()));
        addLeft(anim, delegatingCheckbox("menu.efectos_animacion_ciegas_dealer", GameFrame.ANIMACION_CIEGAS_DEALER, gf.getAnim_ciegas_dealer_menu()));
        addLeft(anim, delegatingCheckbox("menu.efectos_animacion_apuestas", GameFrame.ANIMACION_APUESTAS, gf.getAnim_apuestas_menu()));
        addLeft(anim, delegatingCheckbox("menu.imagenes_del_chat_en_el_juego", GameFrame.CHAT_IMAGES_INGAME, gf.getChat_image_menu()));

        // Columna derecha = Mesa sobre Animaciones (apilados); izquierda = Pantalla.
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setAlignmentY(JComponent.TOP_ALIGNMENT);
        mesa.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        anim.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        right.add(mesa);
        right.add(Box.createVerticalStrut(10));
        right.add(anim);

        pantalla.setAlignmentY(JComponent.TOP_ALIGNMENT);
        right.setAlignmentY(JComponent.TOP_ALIGNMENT);

        add(pantalla);
        add(Box.createHorizontalStrut(12));
        add(right);

        building = false;
    }

    private JPanel titledColumn(String titleKey) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(Translator.translate(titleKey)));
        return p;
    }

    // Añade un componente alineado a la izquierda + un pequeño hueco vertical.
    private void addLeft(JPanel column, JComponent comp) {
        comp.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        // Las filas no deben estirarse a lo alto dentro del BoxLayout vertical.
        comp.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, comp.getPreferredSize().height));
        column.add(comp);
        column.add(Box.createVerticalStrut(4));
    }

    private JCheckBox delegatingCheckbox(String i18nKey, boolean selected, JMenuItem menu) {
        JCheckBox cb = new JCheckBox(Translator.translate(i18nKey), selected);
        cb.setEnabled(menu.isEnabled());
        // Un clic en el checkbox => un clic en el item de menú (aplica + persiste +
        // refleja en el popup). Ambos conmutan un paso, así que quedan sincronizados.
        cb.addActionListener(e -> menu.doClick());
        return cb;
    }

    private JPanel labeledRow(String labelKey, JComponent control) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel(Translator.translate(labelKey) + ":"));
        row.add(control);
        return row;
    }

    private String compactLabel() {
        String state;
        switch (GameFrame.VISTA_COMPACTA) {
            case 1:
                state = Translator.translate("settings.compacta_on");
                break;
            case 2:
                state = Translator.translate("settings.compacta_full");
                break;
            default:
                state = Translator.translate("settings.compacta_off");
                break;
        }
        return Translator.translate("menu.vista_compacta") + ": " + state;
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
