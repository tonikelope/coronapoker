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

import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 *
 * @author tonikelope
 */
public class StatsDialog extends JFrame {

    private final HashMap<String, HashMap<String, Object>> game = new HashMap<>();
    private final HashMap<String, HashMap<String, Object>> hand = new HashMap<>();
    private final LinkedHashMap<String, SQLStats> sqlstats = new LinkedHashMap<>();
    private volatile boolean init = false;
    private volatile String last_mp3_loop = null;
    // Set by disposeIfOpen(): the caller already settled the loop state, so the
    // window's own formWindowClosed must not restore last_mp3_loop a second time.
    private volatile boolean suppress_music_restore = false;
    private volatile boolean game_combo_blocked = false;
    private volatile boolean hand_combo_blocked = false;
    private volatile boolean backup = false;
    private volatile int last_button = 0;

    // Chart area below the results table. Hand-added (not in the .form): see the constructor.
    private javax.swing.JPanel chart_panel;
    private javax.swing.JSplitPane chart_split;
    private int chart_area_height = 360;
    private volatile boolean adjusting_divider = false;
    // Barra con el spinner de "Global Chart Zoom" (escala de fuentes de las gráficas).
    private javax.swing.JPanel chart_toolbar;
    private javax.swing.JSpinner chart_zoom_spinner;
    // Sincronización P2P de estadísticas: dos checkboxes hand-añadidos (no en el
    // .form), en una barra al pie del content pane envuelto. Recibir/Compartir al
    // conectar a un servidor; persisten en Helpers.PROPERTIES al togglear.
    private javax.swing.JCheckBox receive_stats_checkbox;
    private javax.swing.JCheckBox share_stats_checkbox;

    // "Partida privada": una timba marcada como privada NUNCA se comparte por la
    // sync P2P, aunque "Compartir" esté activo (ver StatsSync.listShareableUgis).
    // Componentes hand-añadidos (no en el .form, como el resto de extras de este
    // diálogo): un banner sobre "Duración" (clic derecho -> quitar la marca), un
    // botón por-timba y dos botones globales (como purgar) junto al filtro.
    private javax.swing.JLabel private_game_label;
    private javax.swing.JButton private_game_button;
    private javax.swing.JButton private_all_button;
    private javax.swing.JButton unprivate_all_button;

    // StatsDialog hace TODO su trabajo de fondo (consultas a la conexión SQLite
    // compartida + lectura de logs/chat) en UN solo hilo. Antes,
    // game_comboItemStateChanged disparaba loadGameData + loadHands + el stat A LA VEZ
    // sobre la misma conexión, colisionando — causa del freeze histórico de la ventana
    // de stats tras el balance (el driver serializa el acceso a la conexión y, con el
    // ResultSet leído en el EDT, un worker retenía la conexión mientras pintaba,
    // bloqueando a los otros hasta el timeout de 30s). Serializar en un executor de un
    // hilo elimina la contención. Visor de instancia única; el hilo es daemon y se
    // apaga en formWindowClosed.
    private final java.util.concurrent.ExecutorService stats_db_executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stats-db");
        t.setDaemon(true);
        return t;
    });

    private static StatsDialog INSTANCE;

    /**
     * Opens the stats window, or — if one is already open — brings the existing
     * one to the front (restoring it if minimized). Single-instance: only one
     * StatsDialog can exist at a time.
     */
    public static synchronized void showStats(java.awt.Component locationRelativeTo) {
        if (INSTANCE != null && INSTANCE.isDisplayable()) {
            INSTANCE.setExtendedState(INSTANCE.getExtendedState() & ~java.awt.Frame.ICONIFIED);
            INSTANCE.toFront();
            INSTANCE.requestFocus();
            return;
        }

        INSTANCE = new StatsDialog();
        INSTANCE.setLocationRelativeTo(locationRelativeTo);
        INSTANCE.setVisible(true);
    }

    /**
     * Closes the stats window if one is open. This is an ownerless, non-modal,
     * APPLICATION_EXCLUDE frame, so it survives screen transitions on its own: if
     * the user opens it and then starts/joins a game (or leaves the balance
     * screen) without closing it, stats_music.mp3 would keep looping on top of the
     * next screen's music — two loops at once. Callers performing such a
     * transition close it here.
     *
     * stats_music is stopped synchronously so it cannot outlive the window.
     * restore_previous_loop unmutes the loop the dialog muted on open: a caller
     * that installs its own loops afterwards (the waiting room) passes false to
     * keep the background muted; a caller that just hands control back to the
     * running screen (the balance dialog) passes true.
     */
    public static synchronized void disposeIfOpen(boolean restore_previous_loop) {

        StatsDialog dlg = INSTANCE;

        if (dlg == null) {
            return;
        }

        // The window's formWindowClosed must not redo the loop handling below.
        dlg.suppress_music_restore = true;

        Audio.stopLoopMp3("misc/stats_music.mp3");

        if (restore_previous_loop && dlg.last_mp3_loop != null) {
            Audio.unmuteLoopMp3(dlg.last_mp3_loop);
        }

        Helpers.GUIRun(dlg::dispose);
    }

    /**
     * Creates new form Stats
     */
    public StatsDialog() {
        super();

        // Standalone, ownerless JFrame. It can be opened from the end-of-game
        // BalanceDialog, which is APPLICATION_MODAL: an application-modal dialog blocks
        // every window outside its owner/child chain, so without this exclusion the
        // stats frame would open blocked — no focus, pushed behind the modal (looking
        // as if it minimized itself) — instead of usable. Excluding it from application
        // modality keeps it independently focusable. (Opened from the launcher there is
        // no modal dialog, so that path was unaffected.)
        setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);

        init = true;

        sqlstats.put(Translator.translate("ui.gananciasperdidas"), this::balance);
        sqlstats.put(Translator.translate("ui.tiempo_medio_de_respuesta"), this::tiempoMedioRespuesta);
        sqlstats.put(Translator.translate("ui.jugadas_ganadoras"), this::mejoresJugadas);
        sqlstats.put(Translator.translate("stats.rendimiento_de_los_jugadores"), this::rendimiento);
        sqlstats.put(Translator.translate("stats.apuestassubidas_en_el_preflop"), this::subidasPreflop);
        sqlstats.put(Translator.translate("stats.apuestassubidas_en_el_flop"), this::subidasFlop);
        sqlstats.put(Translator.translate("stats.apuestassubidas_en_el_turn"), this::subidasTurn);
        sqlstats.put(Translator.translate("stats.apuestassubidas_en_el_river"), this::subidasRiver);

        initComponents();

        // Recuadro fino de agrupación con esquinas REDONDEADAS (antes cuadradas). Se aplica aquí
        // (tras initComponents) para no depender del .form generado; mismo gris y grosor.
        hands_panel.setBorder(new RoundedLineBorder(new java.awt.Color(153, 153, 153), 1, 12));

        // Chart area below the results table, built by hand instead of in the .form: a vertical
        // split with the table on top and the charts at the bottom, swapped into the GroupLayout
        // slot table_panel used to occupy. The user can drag the divider up to enlarge the
        // charts; the table (a scroll pane) just scrolls when it shrinks.
        chart_panel = new javax.swing.JPanel(new java.awt.GridLayout(1, 0, 12, 0));
        chart_panel.setOpaque(true);
        chart_panel.setBackground(java.awt.Color.WHITE);
        chart_panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 0, 0, 0));
        chart_panel.setVisible(false);

        chart_split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT);
        chart_split.setBorder(null);
        chart_split.setOpaque(false);
        chart_split.setContinuousLayout(true);
        chart_split.setResizeWeight(0.8);
        chart_split.setDividerSize(0);

        // "Global Chart Zoom": spinner que escala TODAS las fuentes de las gráficas
        // (StatsCharts.FONT_SCALE) y re-renderiza la gráfica actual al cambiar. Va en
        // una barra propia bajo el split (no se toca el GroupLayout del .form). El
        // listener no actúa durante la construcción (init) ni al fijar el valor inicial.
        chart_zoom_spinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel((double) StatsCharts.getFontScale(), 0.8d, 3.0d, 0.1d));
        ((javax.swing.JSpinner.DefaultEditor) chart_zoom_spinner.getEditor()).getTextField().setEditable(false);
        chart_zoom_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chart_zoom_spinner.addChangeListener(e -> {
            if (init) {
                return;
            }
            StatsCharts.setFontScale(((Number) chart_zoom_spinner.getValue()).floatValue());
            refreshCurrentStat();
        });
        javax.swing.JLabel chart_zoom_label = new javax.swing.JLabel(Translator.translate("stats.global_chart_zoom"));
        chart_zoom_label.putClientProperty("i18n.key", "stats.global_chart_zoom");
        chart_toolbar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 2));
        chart_toolbar.setOpaque(false);
        chart_toolbar.add(chart_zoom_label);
        chart_toolbar.add(chart_zoom_spinner);
        chart_toolbar.setVisible(false);

        javax.swing.JPanel chart_split_container = new javax.swing.JPanel(new java.awt.BorderLayout());
        chart_split_container.setOpaque(false);
        chart_split_container.add(chart_split, java.awt.BorderLayout.CENTER);
        chart_split_container.add(chart_toolbar, java.awt.BorderLayout.SOUTH);

        ((javax.swing.GroupLayout) hands_panel.getLayout()).replace(table_panel, chart_split_container);
        chart_split.setTopComponent(table_panel);
        chart_split.setBottomComponent(chart_panel);

        // Remember the chart height when the user drags the divider, so it survives stat switches.
        chart_split.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            if (!adjusting_divider && chart_panel.isVisible() && chart_split.getHeight() > 0) {
                int h = chart_split.getHeight() - chart_split.getDividerLocation() - chart_split.getDividerSize();
                if (h > 80) {
                    chart_area_height = h;
                }
            }
        });

        game_textarea.setEditorKit(new CoronaHTMLEditorKit());
        game_textarea.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {

                Helpers.openBrowserURL(e.getURL().toString());
            }
        });

        scroll_stats_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_stats_panel.getHorizontalScrollBar().setUnitIncrement(16);

        // En resoluciones bajas el panel de estadísticas (una maquetación vertical) sacaba
        // un scroll HORIZONTAL espurio: al ser un JPanel normal, el viewport lo dimensionaba
        // a su ancho PREFERIDO (~1200px por los tamaños fijos del .form) y, al no caber en
        // pantalla, mostraba la barra lateral. Lo envolvemos en un contenedor que SIGUE el
        // ancho del viewport (Scrollable.getScrollableTracksViewportWidth) -> el GroupLayout
        // reflota al ancho disponible y la barra horizontal ya no aparece; la vertical sigue
        // saliendo si el contenido no cabe en alto (que es lo esperado y único razonable).
        scroll_stats_panel.setViewportView(new FitWidthPanel(stats_panel));
        scroll_stats_panel.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        res_table_warning.setVisible(false);
        showdown_panel.setVisible(false);
        game_textarea_scrollpane.setVisible(false);
        game_textarea_scrollpane.setPreferredSize(game_textarea_scrollpane.getSize());
        Helpers.JTextFieldRegularPopupMenu.addTo(game_textarea);
        table_panel.setVisible(false);
        hand_data_panel.setVisible(false);
        hand_combo.addItem(Translator.translate("game.todas_las_manos"));
        hand_combo.setVisible(false);
        game_data_panel.setVisible(false);
        res_table.setDefaultEditor(Object.class, null);
        res_table.setRowHeight(res_table.getRowHeight() + 20);
        showdown_table.setRowHeight(showdown_table.getRowHeight() + 20);
        showdown_table.setDefaultEditor(Object.class, null);

        for (Map.Entry<String, SQLStats> entry : sqlstats.entrySet()) {

            stats_combo.setSelectedIndex(-1);
            stats_combo.addItem(entry.getKey());
        }

        // Sincronización P2P de estadísticas: dos preferencias globales (recibir /
        // compartir al conectar a un servidor) en una barra al pie. Se cuelga
        // envolviendo el content pane (BorderLayout.SOUTH), sin tocar el .form ni el
        // GroupLayout. Los listeners NO persisten durante la construcción (init).
        receive_stats_checkbox = new javax.swing.JCheckBox(Translator.translate("stats.sync_receive"));
        receive_stats_checkbox.putClientProperty("i18n.key", "stats.sync_receive");
        receive_stats_checkbox.setOpaque(false);
        receive_stats_checkbox.setSelected(GameFrame.SYNC_STATS_RECEIVE_PREF);
        receive_stats_checkbox.addItemListener(e -> {
            if (init) {
                return;
            }
            GameFrame.SYNC_STATS_RECEIVE_PREF = receive_stats_checkbox.isSelected();
            Helpers.PROPERTIES.setProperty("sync_stats_receive", String.valueOf(GameFrame.SYNC_STATS_RECEIVE_PREF));
            Helpers.savePropertiesFile();
        });

        share_stats_checkbox = new javax.swing.JCheckBox(Translator.translate("stats.sync_share"));
        share_stats_checkbox.putClientProperty("i18n.key", "stats.sync_share");
        share_stats_checkbox.setOpaque(false);
        share_stats_checkbox.setSelected(GameFrame.SYNC_STATS_SHARE_PREF);
        share_stats_checkbox.addItemListener(e -> {
            if (init) {
                return;
            }
            GameFrame.SYNC_STATS_SHARE_PREF = share_stats_checkbox.isSelected();
            Helpers.PROPERTIES.setProperty("sync_stats_share", String.valueOf(GameFrame.SYNC_STATS_SHARE_PREF));
            Helpers.savePropertiesFile();
        });

        // Botón "Excluir...": abre el diálogo de exclusiones de COMPARTIR (privadas /
        // por nick). Solo tiene sentido junto a COMPARTIR, pero se deja siempre visible
        // (las exclusiones se persisten aunque COMPARTIR esté momentáneamente en OFF).
        javax.swing.JButton exclude_stats_button = new javax.swing.JButton(Translator.translate("stats.sync_exclude"));
        exclude_stats_button.putClientProperty("i18n.key", "stats.sync_exclude");
        exclude_stats_button.addActionListener(e -> showShareExclusionsDialog());

        // "Compartir" + "Excluir..." forman un solo grupo (la exclusión acota lo que se
        // comparte), enmarcado con una línea negra fina para leerse como una unidad.
        javax.swing.JPanel share_group = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 2));
        share_group.setOpaque(false);
        share_group.setBorder(new RoundedLineBorder(java.awt.Color.BLACK, 1, 12));
        share_group.add(share_stats_checkbox);
        share_group.add(exclude_stats_button);

        javax.swing.JPanel sync_stats_bar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 16, 4));
        sync_stats_bar.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
        sync_stats_bar.add(receive_stats_checkbox);
        sync_stats_bar.add(share_group);

        javax.swing.JComponent old_content = (javax.swing.JComponent) getContentPane();
        javax.swing.JPanel content_wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());
        content_wrapper.add(old_content, java.awt.BorderLayout.CENTER);
        content_wrapper.add(sync_stats_bar, java.awt.BorderLayout.SOUTH);
        setContentPane(content_wrapper);

        // ====================================================================
        // "Partida privada" (hand-añadido, fuera del .form como el resto de
        // extras de este diálogo). Se construye ANTES de updateFonts/
        // translateComponents para que herede fuente e i18n como los demás.
        // ====================================================================
        // Banner sobre "Duración" (solo visible si la timba seleccionada es privada).
        private_game_label = new javax.swing.JLabel(Translator.translate("stats.partida_privada"),
                new javax.swing.ImageIcon(getClass().getResource("/images/lock.png")), javax.swing.SwingConstants.LEFT);
        private_game_label.putClientProperty("i18n.key", "stats.partida_privada");
        private_game_label.setForeground(new java.awt.Color(204, 0, 0));
        private_game_label.setIconTextGap(8);
        private_game_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
        private_game_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        Helpers.setTranslatedToolTip(private_game_label, "stats.quitar_privada");
        private_game_label.setVisible(false);

        // Candado TACHADO (diagonal roja sobre lock.png) para las acciones de "quitar
        // privada": candado = hacer privada, candado tachado = quitarla.
        javax.swing.ImageIcon struck_lock = struckIcon(getClass().getResource("/images/lock.png"));

        // Clic derecho sobre el banner -> "Quitar privada" (desmarca la timba seleccionada).
        javax.swing.JPopupMenu unprivate_popup = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem unprivate_item = new javax.swing.JMenuItem(Translator.translate("stats.quitar_privada"), struck_lock);
        unprivate_item.putClientProperty("i18n.key", "stats.quitar_privada");
        unprivate_item.addActionListener(e -> setSelectedGamePrivateAsync(false));
        unprivate_popup.add(unprivate_item);
        private_game_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybePopup(e);
            }

            private void maybePopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() && private_game_label.isVisible()) {
                    unprivate_popup.show(private_game_label, e.getX(), e.getY());
                }
            }
        });

        // Inserta el banner a TODO EL ANCHO justo encima del bloque de datos de la
        // timba (sobre "Duración"): envuelve game_data_panel en un BorderLayout con el
        // banner al norte. replace() conserva el hueco del GroupLayout del .form sin
        // tocarlo. El banner es hermano de game_data_panel, así que un ComponentListener
        // lo oculta cuando el panel de datos se oculta (p.ej. al elegir "todas las timbas").
        javax.swing.JPanel private_banner_wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());
        private_banner_wrapper.setOpaque(false);
        ((javax.swing.GroupLayout) stats_panel.getLayout()).replace(game_data_panel, private_banner_wrapper);
        private_banner_wrapper.add(private_game_label, java.awt.BorderLayout.NORTH);
        private_banner_wrapper.add(game_data_panel, java.awt.BorderLayout.CENTER);
        game_data_panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                private_game_label.setVisible(false);
            }
        });

        // Botón por-timba: marcar la timba seleccionada como privada (junto a eliminar).
        private_game_button = new javax.swing.JButton(Translator.translate("stats.hacer_privada"),
                new javax.swing.ImageIcon(getClass().getResource("/images/lock.png")));
        private_game_button.putClientProperty("i18n.key", "stats.hacer_privada");
        private_game_button.setBackground(new java.awt.Color(123, 31, 162)); // morado
        private_game_button.setForeground(new java.awt.Color(255, 255, 255));
        private_game_button.setFont(new java.awt.Font("Dialog", 1, 14));
        private_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        private_game_button.addActionListener(e -> setSelectedGamePrivateAsync(true));

        // Cap a tamaño preferido: el "glue" izquierdo de la fila absorbe el espacio
        // extra y mantiene los botones pegados a la derecha.
        javax.swing.JPanel delete_button_group = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 18, 0)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        delete_button_group.setOpaque(false);
        ((javax.swing.GroupLayout) game_data_panel.getLayout()).replace(delete_game_button, delete_button_group);
        delete_button_group.add(private_game_button);
        delete_button_group.add(delete_game_button);

        // Botones globales (como purgar): marcar / desmarcar privadas TODAS las
        // timbas del jugador filtrado. Activos solo con filtro de jugador.
        private_all_button = new javax.swing.JButton(Translator.translate("stats.hacer_privadas"),
                new javax.swing.ImageIcon(getClass().getResource("/images/lock.png")));
        private_all_button.putClientProperty("i18n.key", "stats.hacer_privadas");
        private_all_button.setBackground(new java.awt.Color(0, 0, 0));
        private_all_button.setForeground(new java.awt.Color(255, 255, 255));
        private_all_button.setFont(new java.awt.Font("Dialog", 1, 14));
        private_all_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        private_all_button.addActionListener(e -> markFilteredGamesPrivateAsync(true));

        unprivate_all_button = new javax.swing.JButton(Translator.translate("stats.quitar_privadas"), struck_lock);
        unprivate_all_button.putClientProperty("i18n.key", "stats.quitar_privadas");
        unprivate_all_button.setBackground(new java.awt.Color(0, 0, 0));
        unprivate_all_button.setForeground(new java.awt.Color(255, 255, 255));
        unprivate_all_button.setFont(new java.awt.Font("Dialog", 1, 14));
        unprivate_all_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        unprivate_all_button.addActionListener(e -> markFilteredGamesPrivateAsync(false));

        // getMaximumSize -> preferido: que NO crezca y robe espacio al game_combo
        // (el único que debe estirarse en esta fila).
        javax.swing.JPanel purge_button_group = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0)) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        purge_button_group.setOpaque(false);
        ((javax.swing.GroupLayout) stats_panel.getLayout()).replace(purge_games_button, purge_button_group);
        purge_button_group.add(purge_games_button);
        purge_button_group.add(private_all_button);
        purge_button_group.add(unprivate_all_button);

        Font original_dialog_font = res_table.getFont();
        Helpers.updateFonts(this, Helpers.GUI_FONT, Helpers.DIALOG_ZOOM);
        Helpers.translateComponents(this, false);

        // Los dos checkboxes de sync, un pelín más grandes que el resto del diálogo.
        // updateFonts ya les puso GUI_FONT; derivamos +2pt sobre ese tamaño (debe ir
        // DESPUÉS de updateFonts o lo sobrescribiría).
        Font sync_checkbox_font = receive_stats_checkbox.getFont().deriveFont(receive_stats_checkbox.getFont().getSize2D() + 2f);
        receive_stats_checkbox.setFont(sync_checkbox_font);
        share_stats_checkbox.setFont(sync_checkbox_font);
        // El botón "Excluir..." un pelín más grande también, para casar con "Compartir"
        // dentro de su marco.
        exclude_stats_button.setFont(sync_checkbox_font);

        // Banner "PARTIDA PRIVADA" en negrita (updateFonts lo dejó en redonda).
        private_game_label.setFont(private_game_label.getFont().deriveFont(java.awt.Font.BOLD));

        pack();
        res_table.setFont(original_dialog_font);
        showdown_table.setFont(original_dialog_font);
        hand_comcards_val.setFont(original_dialog_font);
        game_textarea.setFont(original_dialog_font);

        setTitle("CoronaPoker " + AboutDialog.VERSION + " - " + Translator.translate("ui.lo_que_no_son_cuentas"));
        stats_combo.setSelectedIndex(-1);

        Helpers.setScaledIconLabel(title, getClass().getResource("/images/stats.png"), title.getHeight(), title.getHeight());

        Helpers.barraIndeterminada(cargando);

        cargando.setVisible(false);

        refreshFilterButtonsEnabled();

        pack();

        // Ventana redimensionable con barra de título completa (min/max/cerrar).
        // Tamaño de restauración al 85% de la pantalla; se abre maximizada.
        java.awt.Dimension stats_screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.round(stats_screen.width * 0.85f), Math.round(stats_screen.height * 0.85f));

        setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);

        stats_db_executor.submit(() -> {
            loadGames();
            // Vista por defecto al abrir: la última timba (la más reciente, índice 1 tras
            // "todas las timbas") con la subconsulta de ganancias/pérdidas, consultada
            // automáticamente. Seleccionar la timba encola loadGameData + loadHands en este
            // mismo executor de un solo hilo; fijamos la stat en una tarea POSTERIOR para
            // que el hand_combo ya esté poblado (índice 0) cuando corra la consulta —igual
            // que en el flujo manual— y no se dispare dos veces. Si no hay timbas, se queda
            // en "todas las timbas".
            Helpers.GUIRunAndWait(() -> {
                if (game_combo.getItemCount() > 1) {
                    game_combo.setSelectedIndex(1);
                }
            });
            stats_db_executor.submit(() -> Helpers.GUIRun(()
                    -> stats_combo.setSelectedItem(Translator.translate("ui.gananciasperdidas"))));
        });

        init = false;
    }

    // Contenedor de la vista del scroll de estadísticas que SIGUE el ancho del viewport
    // (no su propio preferido), de modo que el panel reflota a lo ancho disponible y nunca
    // provoca scroll horizontal; en alto conserva su preferido (puede scrollear en vertical).
    private static final class FitWidthPanel extends javax.swing.JPanel implements javax.swing.Scrollable {

        FitWidthPanel(java.awt.Component view) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(view, java.awt.BorderLayout.CENTER);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // Rellenar el alto del viewport SOLO cuando sobra sitio (viewport más alto que
            // el contenido preferido): así el hueco muerto bajo las gráficas desaparece y el
            // slot de las gráficas (max=32767 en el GroupLayout) crece hasta el pie. Si el
            // contenido NO cabe en alto (resoluciones bajas) devolvemos false -> el contenido
            // usa su alto preferido y el scroll vertical sigue saliendo como debe.
            java.awt.Container parent = getParent();
            return parent instanceof javax.swing.JViewport && parent.getHeight() > getPreferredSize().height;
        }
    }

    /**
     * Replaces the chart(s) shown under the results table (EDT only). Null entries are
     * skipped; if nothing is left to show, the chart area is hidden. Several charts tile
     * side by side.
     */
    private void showChart(java.awt.Component... charts) {
        chart_panel.removeAll();
        int added = 0;
        if (charts != null) {
            for (java.awt.Component c : charts) {
                if (c != null) {
                    chart_panel.add(c);
                    added++;
                }
            }
        }

        boolean show = added > 0;
        chart_panel.setVisible(show);
        // La barra del zoom solo tiene sentido cuando hay gráfica visible.
        if (chart_toolbar != null) {
            chart_toolbar.setVisible(show);
        }

        if (chart_split != null) {
            // Show/hide the divider with the charts, and restore the remembered chart height.
            chart_split.setDividerSize(show ? 9 : 0);
            int splitH = chart_split.getHeight();
            if (show && splitH > 0) {
                adjusting_divider = true;
                chart_split.setDividerLocation(Math.max(80, splitH - chart_area_height - chart_split.getDividerSize()));
                adjusting_divider = false;
            }
        }

        chart_panel.revalidate();
        chart_panel.repaint();
    }

    // Re-lanza la consulta de la stat actualmente seleccionada para re-renderizar su
    // gráfica con el FONT_SCALE nuevo (lo dispara el spinner de Global Chart Zoom).
    // Reusa el flujo normal: re-consulta + reconstruye tabla y gráfica. Como cada
    // método de stat hace setEnabled(false) mientras consulta, el spinner queda
    // bloqueado durante el refresco (evita encolar refrescos a lo loco).
    private void refreshCurrentStat() {
        if (!init && stats_combo.getSelectedIndex() >= 0) {
            SQLStats s = sqlstats.get((String) stats_combo.getSelectedItem());
            if (s != null) {
                res_table.setRowSorter(null);
                showChart(null);
                s.call();
            }
        }
    }

    /**
     * Parses a percentage-formatted string (e.g. "12.5%", "0%", "NULL%") into a float
     * for sort comparisons. Returns {@link Float#NEGATIVE_INFINITY} for null/empty
     * values or unparseable content, so malformed rows sort to the bottom.
     */
    private static float safeParsePercent(String value) {
        if (value == null) {
            return Float.NEGATIVE_INFINITY;
        }
        try {
            return Float.parseFloat(value.replaceAll(" *%$", "").trim());
        } catch (NumberFormatException ex) {
            return Float.NEGATIVE_INFINITY;
        }
    }

    /**
     * Coerces a numeric Object (Double/Float/Integer/BigDecimal/String) to double
     * for sort comparators. Returns Double.NEGATIVE_INFINITY for null/unparseable
     * so problematic rows sort to the bottom.
     */
    private static double toDouble(Object value) {
        if (value == null) {
            return Double.NEGATIVE_INFINITY;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ex) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * Drains a ResultSet into a DefaultTableModel, translating column labels via i18n.
     * Used by the stats methods that build the result table from a query.
     */
    private static void populateTableModel(DefaultTableModel tableModel, ResultSet rs) throws SQLException {
        if (rs == null) {
            return;
        }
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex)));
        }
        Object[] row = new Object[columnCount];
        while (rs.next()) {
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            tableModel.addRow(row);
        }
    }

    private void mejoresJugadas() {

        cargando.setVisible(true);
        setEnabled(false);

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        hand_combo.setVisible(false);

        // Snapshot the combo selection on the EDT; the worker thread must never read Swing state.
        final int gameIdx = game_combo.getSelectedIndex();
        final int gameId = gameIdx > 0 ? (int) game.get((String) game_combo.getSelectedItem()).get("id") : -1;

        stats_db_executor.submit(() -> {
            try {
                ResultSet rs;
                if (gameIdx > 0) {

                    String sql = "select player as \"player.jugador\", hole_cards as \"ui.cartas_recibidas\", hand_cards as \"ui.cartas_jugada\", hand_val as \"ui.jugada\", hand.counter as \"game.mano_2\", round(showdown.profit,1) as \"ui.beneficio\" from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 and game.id=? order by hand_val DESC,\"ui.beneficio\" DESC;";
                    try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                        statement.setQueryTimeout(30);
                        statement.setInt(1, gameId);
                        rs = statement.executeQuery();
                        mejoresJugadasResult(rs);
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRunAndWait(() -> {
                        res_table_warning.setVisible(false);
                    });

                } else {

                    String sql = "select player as \"player.jugador\", hole_cards as \"ui.cartas_recibidas\", hand_cards as \"ui.cartas_jugada\", hand_val as \"ui.jugada\", (game.server || '|' || game.start) as \"game.timba\", hand.counter as \"game.mano_2\", round(showdown.profit,1) as \"ui.beneficio\" from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 order by hand_val DESC,\"ui.beneficio\" DESC LIMIT 1000;";
                    try (Statement statement = Helpers.getSQLITE().createStatement()) {
                        statement.setQueryTimeout(30);
                        rs = statement.executeQuery(sql);
                        mejoresJugadasResult(rs);
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRunAndWait(() -> {
                        res_table_warning.setText(Translator.translate("ui.nota_se_muestran_las_1000"));
                        res_table_warning.setVisible(true);
                    });

                }
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });

    }

    private void mejoresJugadasResult(ResultSet rs) {

        try {
            DefaultTableModel tableModel = new DefaultTableModel();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex)));
            }

            Object[] row = new Object[columnCount];

            // One formatter for the whole drain instead of allocating one per row.
            DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

            while (rs.next()) {
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);

                    if (tableModel.getColumnName(i).equals(Translator.translate("game.timba"))) {
                        // Safe fetch by index to avoid Alias issues
                        String val = rs.getString(i + 1);
                        String timestamp = val.replaceAll("^.+\\|([0-9]+)$", "$1");
                        String server = val.replaceAll("^(.+)\\|[0-9]+$", "$1");
                        Timestamp ts = new Timestamp(Long.parseLong(timestamp));
                        Date date = new Date(ts.getTime());
                        row[i] = server + " @ " + timeZoneFormat.format(date);
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.cartas_recibidas"))) {
                        ArrayList<Card> cartas = new ArrayList<>();
                        if (row[i] != null) {
                            for (String c : ((String) row[i]).split("#")) {
                                String[] partes = c.split("_");
                                if (partes.length == 2) {
                                    Card carta = new Card(false);
                                    carta.actualizarValorPalo(partes[0], partes[1]);
                                    cartas.add(carta);
                                }
                            }
                            Card.sortCollection(cartas);
                        }
                        row[i] = row[i] != null ? Card.collection2String(cartas) : "*****";

                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.cartas_jugada"))) {
                        ArrayList<Card> cartas = new ArrayList<>();
                        if (row[i] != null) {
                            for (String c : ((String) row[i]).split("#")) {
                                String[] partes = c.split("_");
                                if (partes.length == 2) {
                                    Card carta = new Card(false);
                                    carta.actualizarValorPalo(partes[0], partes[1]);
                                    cartas.add(carta);
                                }
                            }
                        }
                        row[i] = row[i] != null ? Card.collection2String(cartas) : "-----";
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.jugada"))) {
                        // Defensivo: solo un Integer en rango [1, NOMBRES_JUGADAS.length] se
                        // convierte a nombre de jugada; cualquier otra cosa (null, no-Integer
                        // por una colisión de cabeceras i18n, o fuera de rango) -> "-----".
                        // Evita el ClassCastException/AIOOBE que dejaba la tabla sin cargar.
                        row[i] = (row[i] instanceof Integer && (Integer) row[i] >= 1 && (Integer) row[i] <= Hand.NOMBRES_JUGADAS.length)
                                ? Hand.NOMBRES_JUGADAS[(Integer) row[i] - 1] : "-----";
                    }
                }
                tableModel.addRow(row);
            }

            Helpers.GUIRunAndWait(() -> {
                res_table.setModel(tableModel);
                TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());
                Helpers.disableSortAllColumns(res_table, tableRowSorter);

                // Get indexes securely
                int idxPlayer = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("player.jugador"));
                int idxMano = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("game.mano_2"));
                int idxBeneficio = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ui.beneficio"));
                int idxJugada = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ui.jugada"));
                int idxTimba = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("game.timba"));

                // Apply constraints if indices exist
                if (idxPlayer != -1) {
                    tableRowSorter.setSortable(idxPlayer, true);
                }

                if (idxMano != -1) {
                    tableRowSorter.setSortable(idxMano, true);
                    tableRowSorter.setComparator(idxMano, (Comparator<Integer>) (o1, o2) -> o1.compareTo(o2));
                }

                if (idxBeneficio != -1) {
                    tableRowSorter.setSortable(idxBeneficio, true);
                    tableRowSorter.setComparator(idxBeneficio, (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));
                }

                if (idxJugada != -1) {
                    tableRowSorter.setSortable(idxJugada, true);
                    tableRowSorter.setComparator(idxJugada, (Comparator<String>) (o1, o2) -> Integer.compare(Hand.handnameToHandValue(o1), Hand.handnameToHandValue(o2)));
                }

                if (idxTimba != -1) {
                    tableRowSorter.setSortable(idxTimba, true);
                    SimpleDateFormat timbaSorterFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                    tableRowSorter.setComparator(idxTimba, (Comparator<String>) (o1, o2) -> {
                        try {
                            return Long.compare(timbaSorterFormat.parse(o1.split(" @ ")[1]).getTime(), timbaSorterFormat.parse(o2.split(" @ ")[1]).getTime());
                        } catch (ParseException ex) {
                            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return 0;
                    });
                }

                res_table.setRowSorter(tableRowSorter);
                table_panel.setVisible(true);

                // Winning-hand distribution: how often each hand type appears among the wins,
                // ordered weakest to strongest.
                if (idxJugada != -1) {
                    HashMap<String, Integer> counts = new HashMap<>();
                    for (int r = 0; r < tableModel.getRowCount(); r++) {
                        String jugada = String.valueOf(tableModel.getValueAt(r, idxJugada));
                        if (jugada != null && !jugada.equals("-----")) {
                            counts.merge(jugada, 1, Integer::sum);
                        }
                    }
                    LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
                    counts.entrySet().stream()
                            .sorted((a, b) -> Integer.compare(Hand.handnameToHandValue(a.getKey()), Hand.handnameToHandValue(b.getKey())))
                            .forEach(e -> ordered.put(e.getKey(), e.getValue()));
                    showChart(ordered.isEmpty() ? null : StatsCharts.countBars(ordered, Translator.translate("stats.chart_jugadas"), "", StatsCharts.PURPLE));
                } else {
                    showChart(null);
                }
            });
        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void rendimiento() {

        cargando.setVisible(true);
        setEnabled(false);

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        hand_combo.setVisible(false);

        // Snapshot the combo selection on the EDT; the worker thread must never read Swing state.
        final int gameIdx = game_combo.getSelectedIndex();
        final int gameId = gameIdx > 0 ? (int) game.get((String) game_combo.getSelectedItem()).get("id") : -1;

        // efectividad = ROI / SQRT(participation_rate). Symmetric around zero, continuous,
        // rewards positive ROI with low participation and punishes negative ROI equally
        // when participation was low (concentrated losses).
        // SQRT/COALESCE protect against division by zero and NULL ROI from missing balance.
        final String SQL_PER_GAME
                = "SELECT t1.JUGADOR AS \"player.jugador\","
                + " ROUND((JUGADAS / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"stats.manos_jugadas\","
                + " ROUND((COALESCE(GANADAS, 0) / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"stats.manos_ganadas_2\","
                + " CASE WHEN JUGADAS > 0"
                + "      THEN ROUND((COALESCE(GANADAS, 0) / CAST(JUGADAS AS FLOAT)) * 100, 1) || '%'"
                + "      ELSE '0.0%' END AS \"stats.precision\","
                + " COALESCE(roi, 0) || '%' AS \"stats.roi\","
                + " CASE WHEN COALESCE(JUGADAS, 0) > 0 AND COALESCE(MANOS_TOTALES, 0) > 0"
                + "      THEN ROUND((COALESCE(roi, 0) / 100.0) / SQRT(JUGADAS * 1.0 / MANOS_TOTALES), 2)"
                + "      ELSE 0.0 END AS \"stats.efectividad\""
                + " FROM ("
                + "   SELECT action.player AS JUGADOR, COALESCE(tb.JUGADAS, 0) AS JUGADAS"
                + "   FROM action, hand"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS JUGADAS"
                + "     FROM action, hand"
                + "     WHERE action.id_hand = hand.id AND hand.id_game = ? AND action >= 2 AND round = 1"
                + "     GROUP BY player"
                + "   ) AS tb ON action.player = tb.player"
                + "   WHERE action.id_hand = hand.id AND hand.id_game = ?"
                + "   GROUP BY action.player"
                + " ) t1"
                + " LEFT JOIN ("
                + "   SELECT showdown.player AS JUGADOR, COALESCE(tc.GANADAS, 0) AS GANADAS"
                + "   FROM showdown, hand"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS GANADAS"
                + "     FROM showdown, hand"
                + "     WHERE showdown.id_hand = hand.id AND hand.id_game = ? AND winner = 1"
                + "     GROUP BY player"
                + "   ) AS tc ON showdown.player = tc.player"
                + "   WHERE showdown.id_hand = hand.id AND hand.id_game = ?"
                + "   GROUP BY showdown.player"
                + " ) t2 ON t2.JUGADOR = t1.JUGADOR"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR, COUNT(DISTINCT id_hand) AS MANOS_TOTALES"
                + "   FROM action, hand"
                + "   WHERE action.id_hand = hand.id AND hand.id_game = ?"
                + "   GROUP BY JUGADOR"
                + " ) t3 ON t3.JUGADOR = t1.JUGADOR"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR,"
                + "     ROUND(CASE WHEN SUM(buyin) > 0 THEN (SUM(stack - buyin) * 1.0 / SUM(buyin)) * 100 ELSE 0 END, 0) AS roi"
                + "   FROM balance, hand"
                + "   WHERE balance.id_hand = hand.id AND id_hand IN ("
                + "     SELECT MAX(hand.id) FROM hand, balance"
                + "     WHERE hand.id = balance.id_hand AND hand.id_game = ?"
                + "   )"
                + "   GROUP BY JUGADOR"
                + " ) t4 ON t4.JUGADOR = t1.JUGADOR"
                + " GROUP BY t1.JUGADOR"
                + " ORDER BY \"stats.efectividad\" DESC";

        final String SQL_ALL_GAMES
                = "SELECT t1.JUGADOR AS \"player.jugador\","
                + " ROUND((JUGADAS / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"stats.manos_jugadas\","
                + " ROUND((COALESCE(GANADAS, 0) / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"stats.manos_ganadas_2\","
                + " CASE WHEN JUGADAS > 0"
                + "      THEN ROUND((COALESCE(GANADAS, 0) / CAST(JUGADAS AS FLOAT)) * 100, 1) || '%'"
                + "      ELSE '0.0%' END AS \"stats.precision\","
                + " COALESCE(roi, 0) || '%' AS \"stats.roi\","
                + " CASE WHEN COALESCE(JUGADAS, 0) > 0 AND COALESCE(MANOS_TOTALES, 0) > 0"
                + "      THEN ROUND((COALESCE(roi, 0) / 100.0) / SQRT(JUGADAS * 1.0 / MANOS_TOTALES), 2)"
                + "      ELSE 0.0 END AS \"stats.efectividad\""
                + " FROM ("
                + "   SELECT action.player AS JUGADOR, COALESCE(tb.JUGADAS, 0) AS JUGADAS"
                + "   FROM action"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS JUGADAS"
                + "     FROM action"
                + "     WHERE action >= 2 AND round = 1"
                + "     GROUP BY player"
                + "   ) AS tb ON action.player = tb.player"
                + "   GROUP BY action.player"
                + " ) t1"
                + " LEFT JOIN ("
                + "   SELECT showdown.player AS JUGADOR, COALESCE(tc.GANADAS, 0) AS GANADAS"
                + "   FROM showdown"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS GANADAS"
                + "     FROM showdown"
                + "     WHERE winner = 1"
                + "     GROUP BY player"
                + "   ) AS tc ON showdown.player = tc.player"
                + "   GROUP BY showdown.player"
                + " ) t2 ON t2.JUGADOR = t1.JUGADOR"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR, COUNT(DISTINCT id_hand) AS MANOS_TOTALES"
                + "   FROM action"
                + "   GROUP BY JUGADOR"
                + " ) t3 ON t3.JUGADOR = t1.JUGADOR"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR,"
                + "     ROUND(CASE WHEN SUM(buyin) > 0 THEN (SUM(stack - buyin) * 1.0 / SUM(buyin)) * 100 ELSE 0 END, 0) AS roi"
                + "   FROM balance, hand"
                + "   WHERE balance.id_hand = hand.id AND id_hand IN ("
                + "     SELECT MAX(hand.id) FROM hand, balance"
                + "     WHERE hand.id = balance.id_hand"
                + "     GROUP BY id_game"
                + "   )"
                + "   GROUP BY JUGADOR"
                + " ) t4 ON t4.JUGADOR = t1.JUGADOR"
                + " GROUP BY t1.JUGADOR"
                + " ORDER BY \"stats.efectividad\" DESC";

        stats_db_executor.submit(() -> {
            try {
                DefaultTableModel tableModel = new DefaultTableModel();
                if (gameIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_GAME)) {
                        st.setQueryTimeout(30);
                        for (int i = 1; i <= 6; i++) {
                            st.setInt(i, gameId);
                        }
                        try (ResultSet rs = st.executeQuery()) {
                            populateTableModel(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try (Statement st = Helpers.getSQLITE().createStatement()) {
                        st.setQueryTimeout(30);
                        try (ResultSet rs = st.executeQuery(SQL_ALL_GAMES)) {
                            populateTableModel(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Helpers.GUIRunAndWait(() -> {
                    res_table.setModel(tableModel);
                    TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());
                    Helpers.disableSortAllColumns(res_table, tableRowSorter);

                    int idxPlayer = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("player.jugador"));
                    int idxEfec = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.efectividad"));
                    int idxManosJ = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.manos_jugadas"));
                    int idxManosG = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.manos_ganadas_2"));
                    int idxPrec = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.precision"));
                    int idxRoi = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.roi"));

                    if (idxPlayer != -1) {
                        tableRowSorter.setSortable(idxPlayer, true);
                    }

                    if (idxEfec != -1) {
                        tableRowSorter.setSortable(idxEfec, true);
                        tableRowSorter.setComparator(idxEfec, (Comparator<Object>) (o1, o2) -> Double.compare(toDouble(o1), toDouble(o2)));
                    }
                    if (idxManosJ != -1) {
                        tableRowSorter.setSortable(idxManosJ, true);
                        tableRowSorter.setComparator(idxManosJ, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }
                    if (idxManosG != -1) {
                        tableRowSorter.setSortable(idxManosG, true);
                        tableRowSorter.setComparator(idxManosG, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }
                    if (idxPrec != -1) {
                        tableRowSorter.setSortable(idxPrec, true);
                        tableRowSorter.setComparator(idxPrec, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }
                    if (idxRoi != -1) {
                        tableRowSorter.setSortable(idxRoi, true);
                        tableRowSorter.setComparator(idxRoi, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }

                    res_table.setRowSorter(tableRowSorter);
                    table_panel.setVisible(true);

                    // Performance radar: participation / win rate / accuracy per player (%).
                    LinkedHashMap<String, double[]> radarData = new LinkedHashMap<>();
                    if (idxPlayer != -1 && idxManosJ != -1 && idxManosG != -1 && idxPrec != -1) {
                        for (int r = 0; r < tableModel.getRowCount() && radarData.size() < 10; r++) {
                            radarData.put(String.valueOf(tableModel.getValueAt(r, idxPlayer)), new double[]{
                                safeParsePercent(String.valueOf(tableModel.getValueAt(r, idxManosJ))),
                                safeParsePercent(String.valueOf(tableModel.getValueAt(r, idxManosG))),
                                safeParsePercent(String.valueOf(tableModel.getValueAt(r, idxPrec)))
                            });
                        }
                    }
                    String[] radarAxes = {Translator.translate("stats.manos_jugadas"), Translator.translate("stats.manos_ganadas_2"), Translator.translate("stats.precision")};
                    showChart(radarData.isEmpty() ? null : StatsCharts.radar(Translator.translate("stats.chart_rendimiento"), radarAxes, radarData));

                    res_table_warning.setText(Translator.translate("stats.nota_efectividad_roi_manosjugadas_si"));
                    res_table_warning.setVisible(true);
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });
    }

    private void subidasRonda(int ronda, String chartTitle) {

        cargando.setVisible(true);
        setEnabled(false);

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        hand_combo.setVisible(false);

        // Snapshot the combo selection on the EDT; the worker thread must never read Swing state.
        final int gameIdx = game_combo.getSelectedIndex();
        final int gameId = gameIdx > 0 ? (int) game.get((String) game_combo.getSelectedItem()).get("id") : -1;

        final String SQL_PER_GAME
                = "SELECT t1.JUGADOR AS \"player.jugador\","
                + " ROUND((JUGADAS / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"game.manos_3\""
                + " FROM ("
                + "   SELECT action.player AS JUGADOR, COALESCE(tb.JUGADAS, 0) AS JUGADAS"
                + "   FROM action, hand"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS JUGADAS"
                + "     FROM action, hand"
                + "     WHERE action.id_hand = hand.id AND round = ? AND hand.id_game = ? AND action >= 3"
                + "     GROUP BY player"
                + "   ) AS tb ON action.player = tb.player"
                + "   WHERE action.id_hand = hand.id AND hand.id_game = ?"
                + "   GROUP BY action.player"
                + " ) t1"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR, COUNT(DISTINCT id_hand) AS MANOS_TOTALES"
                + "   FROM action, hand"
                + "   WHERE action.id_hand = hand.id AND action >= 2 AND round = ? AND hand.id_game = ?"
                + "   GROUP BY JUGADOR"
                + " ) t2 ON t2.JUGADOR = t1.JUGADOR"
                + " GROUP BY t1.JUGADOR"
                + " ORDER BY \"game.manos_3\" DESC";

        final String SQL_ALL_GAMES
                = "SELECT t1.JUGADOR AS \"player.jugador\","
                + " ROUND((JUGADAS / CAST(MANOS_TOTALES AS FLOAT)) * 100, 1) || '%' AS \"game.manos_3\""
                + " FROM ("
                + "   SELECT action.player AS JUGADOR, COALESCE(tb.JUGADAS, 0) AS JUGADAS"
                + "   FROM action"
                + "   LEFT JOIN ("
                + "     SELECT player, COUNT(DISTINCT id_hand) AS JUGADAS"
                + "     FROM action"
                + "     WHERE round = ? AND action >= 3"
                + "     GROUP BY player"
                + "   ) AS tb ON action.player = tb.player"
                + "   GROUP BY action.player"
                + " ) t1"
                + " LEFT JOIN ("
                + "   SELECT player AS JUGADOR, COUNT(DISTINCT id_hand) AS MANOS_TOTALES"
                + "   FROM action"
                + "   WHERE action >= 2 AND round = ?"
                + "   GROUP BY JUGADOR"
                + " ) t2 ON t2.JUGADOR = t1.JUGADOR"
                + " GROUP BY t1.JUGADOR"
                + " ORDER BY \"game.manos_3\" DESC";

        stats_db_executor.submit(() -> {
            try {
                DefaultTableModel tableModel = new DefaultTableModel();
                if (gameIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_GAME)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, ronda);
                        st.setInt(2, gameId);
                        st.setInt(3, gameId);
                        st.setInt(4, ronda);
                        st.setInt(5, gameId);
                        try (ResultSet rs = st.executeQuery()) {
                            populateTableModel(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_ALL_GAMES)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, ronda);
                        st.setInt(2, ronda);
                        try (ResultSet rs = st.executeQuery()) {
                            populateTableModel(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Helpers.GUIRunAndWait(() -> {
                    res_table.setModel(tableModel);
                    TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

                    Helpers.disableSortAllColumns(res_table, tableRowSorter);

                    int idxPlayer = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("player.jugador"));
                    int idxHands = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("game.manos_3"));

                    if (idxPlayer != -1) {
                        tableRowSorter.setSortable(idxPlayer, true);
                    }
                    if (idxHands != -1) {
                        tableRowSorter.setSortable(idxHands, true);
                        tableRowSorter.setComparator(idxHands, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }

                    res_table.setRowSorter(tableRowSorter);

                    if (idxHands != -1) {
                        res_table.getRowSorter().toggleSortOrder(idxHands);
                        res_table.getRowSorter().toggleSortOrder(idxHands);
                    }

                    table_panel.setVisible(true);

                    // Raise/bet frequency per player on this street (%).
                    LinkedHashMap<String, Double> pct = new LinkedHashMap<>();
                    if (idxPlayer != -1 && idxHands != -1) {
                        for (int r = 0; r < tableModel.getRowCount(); r++) {
                            float v = safeParsePercent(String.valueOf(tableModel.getValueAt(r, idxHands)));
                            // Excluye del grafico a quien no tiene dato en esta calle (% nulo ->
                            // -Infinity desde safeParsePercent): no aporta barra y reventaba el
                            // eje del grafico con "Must be finite" en cada repaint.
                            if (Double.isFinite(v)) {
                                pct.put(String.valueOf(tableModel.getValueAt(r, idxPlayer)), (double) v);
                            }
                        }
                    }
                    showChart(pct.isEmpty() ? null : StatsCharts.valueBars(pct, chartTitle, "%", "{2}%", StatsCharts.ORANGE));

                    res_table_warning.setText(Translator.translate("stats.nota_lo_que_se_muestra"));
                    res_table_warning.setVisible(true);
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });

    }

    private void subidasPreflop() {

        this.subidasRonda(Crupier.PREFLOP, Translator.translate("stats.apuestassubidas_en_el_preflop"));

    }

    private void subidasFlop() {

        this.subidasRonda(Crupier.FLOP, Translator.translate("stats.apuestassubidas_en_el_flop"));

    }

    private void subidasTurn() {

        this.subidasRonda(Crupier.TURN, Translator.translate("stats.apuestassubidas_en_el_turn"));

    }

    private void subidasRiver() {

        this.subidasRonda(Crupier.RIVER, Translator.translate("stats.apuestassubidas_en_el_river"));

    }

    private void balance() {

        cargando.setVisible(true);
        setEnabled(false);

        if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {
            hand_combo.setVisible(true);
            hand_combo.setSelectedIndex(0);
        }

        // CASE WHEN buyin > 0 (or SUM > 0) protects against div-by-zero so a 0-buyin
        // record does not silently null-out the whole ROI column.
        final String SQL_PER_HAND
                = "SELECT player AS \"player.jugador\","
                + " ROUND(stack, 1) AS \"stats.stack\","
                + " buyin AS \"stats.buyin\","
                + " ROUND(stack - buyin, 1) AS \"ui.beneficio\","
                + " ROUND(CASE WHEN buyin > 0 THEN ((stack - buyin) * 1.0 / buyin) * 100 ELSE 0 END, 0) AS \"stats.roi\""
                + " FROM balance"
                + " WHERE id_hand = ?"
                + " GROUP BY \"player.jugador\""
                + " ORDER BY \"stats.roi\" DESC";

        final String SQL_PER_GAME
                = "SELECT player AS \"player.jugador\","
                + " ROUND(stack, 1) AS \"stats.stack\","
                + " buyin AS \"stats.buyin\","
                + " ROUND(stack - buyin, 1) AS \"ui.beneficio\","
                + " ROUND(CASE WHEN buyin > 0 THEN ((stack - buyin) * 1.0 / buyin) * 100 ELSE 0 END, 0) AS \"stats.roi\""
                + " FROM balance, hand"
                + " WHERE balance.id_hand = hand.id AND hand.id_game = ?"
                + "   AND hand.id = ("
                + "     SELECT MAX(hand.id) FROM hand, balance"
                + "     WHERE hand.id = balance.id_hand AND hand.id_game = ?"
                + "   )"
                + " GROUP BY \"player.jugador\""
                + " ORDER BY \"stats.roi\" DESC";

        final String SQL_ALL_GAMES
                = "SELECT player AS \"player.jugador\","
                + " ROUND(SUM(stack), 1) AS \"stats.stack\","
                + " SUM(buyin) AS \"stats.buyin\","
                + " ROUND(SUM(stack - buyin), 1) AS \"ui.beneficio\","
                + " ROUND(CASE WHEN SUM(buyin) > 0 THEN (SUM(stack - buyin) * 1.0 / SUM(buyin)) * 100 ELSE 0 END, 0) AS \"stats.roi\""
                + " FROM balance"
                + " WHERE id_hand IN ("
                + "   SELECT MAX(hand.id) FROM hand, balance"
                + "   WHERE hand.id = balance.id_hand"
                + "   GROUP BY id_game"
                + " )"
                + " GROUP BY \"player.jugador\""
                + " ORDER BY \"stats.roi\" DESC";

        // Snapshot combo state on the EDT; the worker thread must never read Swing state.
        final int handIdx = hand_combo.getSelectedIndex();
        final boolean handVisible = hand_combo.isVisible();
        final int gameIdx = game_combo.getSelectedIndex();
        final int handId = handIdx > 0 ? (int) hand.get((String) hand_combo.getSelectedItem()).get("id") : -1;
        final int gameId = gameIdx > 0 ? (int) game.get((String) game_combo.getSelectedItem()).get("id") : -1;

        stats_db_executor.submit(() -> {
            try {
                DefaultTableModel tableModel = new DefaultTableModel();
                if (handIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_HAND)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, handId);
                        try (ResultSet rs = st.executeQuery()) {
                            populateBalanceTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    if (handVisible && handIdx > 0) {
                        Helpers.GUIRunAndWait(() -> {
                            res_table_warning.setText(Translator.translate("game.nota_lo_que_se_muestra"));
                            res_table_warning.setVisible(true);
                        });
                    }
                } else if (gameIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_GAME)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, gameId);
                        st.setInt(2, gameId);
                        try (ResultSet rs = st.executeQuery()) {
                            populateBalanceTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try (Statement st = Helpers.getSQLITE().createStatement()) {
                        st.setQueryTimeout(30);
                        try (ResultSet rs = st.executeQuery(SQL_ALL_GAMES)) {
                            populateBalanceTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                // Stack evolution (session graph): one line per player across the hands of a
                // single game. Only meaningful at game scope, so skip it otherwise.
                final org.jfree.data.xy.XYSeriesCollection sessionData = new org.jfree.data.xy.XYSeriesCollection();
                if (gameIdx > 0 && handIdx <= 0) {
                    String sessionSql = "SELECT balance.player AS player, hand.counter AS counter, balance.stack AS stack"
                            + " FROM balance, hand WHERE balance.id_hand = hand.id AND hand.id_game = ? AND hand.end IS NOT NULL"
                            + " ORDER BY hand.counter";
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(sessionSql)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, gameId);
                        try (ResultSet rs = st.executeQuery()) {
                            LinkedHashMap<String, org.jfree.data.xy.XYSeries> series = new LinkedHashMap<>();
                            while (rs.next()) {
                                org.jfree.data.xy.XYSeries s = series.computeIfAbsent(rs.getString("player"), org.jfree.data.xy.XYSeries::new);
                                s.add(rs.getInt("counter"), rs.getDouble("stack"));
                            }
                            for (org.jfree.data.xy.XYSeries s : series.values()) {
                                sessionData.addSeries(s);
                            }
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Helpers.GUIRunAndWait(() -> {
                    res_table.setModel(tableModel);
                    TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());
                    Helpers.disableSortAllColumns(res_table, tableRowSorter);

                    int idxPlayer = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("player.jugador"));
                    int idxBuyin = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.buyin"));
                    int idxStack = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.stack"));
                    int idxProfit = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ui.beneficio"));
                    int idxRoi = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("stats.roi"));

                    if (idxPlayer != -1) {
                        tableRowSorter.setSortable(idxPlayer, true);
                    }
                    if (idxBuyin != -1) {
                        tableRowSorter.setSortable(idxBuyin, true);
                        tableRowSorter.setComparator(idxBuyin, (Comparator<Object>) (o1, o2) -> Double.compare(toDouble(o1), toDouble(o2)));
                    }
                    if (idxStack != -1) {
                        tableRowSorter.setSortable(idxStack, true);
                        tableRowSorter.setComparator(idxStack, (Comparator<Object>) (o1, o2) -> Double.compare(toDouble(o1), toDouble(o2)));
                    }
                    if (idxProfit != -1) {
                        tableRowSorter.setSortable(idxProfit, true);
                        tableRowSorter.setComparator(idxProfit, (Comparator<Object>) (o1, o2) -> Double.compare(toDouble(o1), toDouble(o2)));
                    }
                    if (idxRoi != -1) {
                        tableRowSorter.setSortable(idxRoi, true);
                        tableRowSorter.setComparator(idxRoi, (Comparator<String>) (o1, o2) -> Float.compare(safeParsePercent(o1), safeParsePercent(o2)));
                    }

                    res_table.setRowSorter(tableRowSorter);
                    table_panel.setVisible(true);

                    // Scoreboard chart: profit per player (green positive, red negative).
                    // Descending — JFreeChart draws the first category at the top of the
                    // horizontal bars, so the leader ends up on top.
                    java.util.ArrayList<Object[]> profitRows = new java.util.ArrayList<>();
                    if (idxPlayer != -1 && idxProfit != -1) {
                        for (int r = 0; r < tableModel.getRowCount(); r++) {
                            profitRows.add(new Object[]{String.valueOf(tableModel.getValueAt(r, idxPlayer)), toDouble(tableModel.getValueAt(r, idxProfit))});
                        }
                        profitRows.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));
                    }
                    org.jfree.chart.ChartPanel benefitChart = null;
                    if (!profitRows.isEmpty()) {
                        LinkedHashMap<String, Double> profit = new LinkedHashMap<>();
                        for (Object[] o : profitRows) {
                            profit.put((String) o[0], (double) o[1]);
                        }
                        benefitChart = StatsCharts.benefitBars(profit, Translator.translate("stats.chart_beneficio"), Translator.translate("ui.beneficio"));
                    }
                    org.jfree.chart.ChartPanel sessionChart = sessionData.getSeriesCount() > 0
                            ? StatsCharts.lineChart(sessionData, Translator.translate("stats.chart_stack"), Translator.translate("game.mano_2"), Translator.translate("stats.stack"))
                            : null;
                    showChart(benefitChart, sessionChart);
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });

    }

    /**
     * Populates the table model with balance columns; ROI column is suffixed with "%"
     * so the display matches the other ROI usages in the dialog.
     */
    private static void populateBalanceTable(DefaultTableModel tableModel, ResultSet rs) throws SQLException {
        if (rs == null) {
            return;
        }
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex)));
        }
        Object[] row = new Object[columnCount];
        String roiHeader = Translator.translate("stats.roi");
        while (rs.next()) {
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
                if (tableModel.getColumnName(i).equals(roiHeader)) {
                    row[i] = String.valueOf(row[i]) + "%";
                }
            }
            tableModel.addRow(row);
        }
    }

    private void loadGameData(int id) {

        cargando.setVisible(true);

        setEnabled(false);

        game_combo_blocked = true;

        // Snapshot the selected game label on the EDT (needed to locate the log/chat files).
        final String item = (String) game_combo.getSelectedItem();

        stats_db_executor.submit(() -> {
            try {
                String sql = "SELECT *, (SELECT COUNT(*) from hand where id_game=? AND end IS NOT NULL) as tot_hands FROM game WHERE id=?";

                // Read the row and resolve the log/chat files on this worker thread;
                // only the label updates run on the EDT.
                final String[] parts = item.split("@");
                final String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");
                final String nick = Helpers.safeNickForFilename(parts[0].trim());

                boolean found = false;
                boolean logEnabled = false;
                boolean chatEnabled = false;
                boolean isPrivate = false;
                String playtimeText = "";
                String playersText = "";
                String buyinText = "";
                String handText = "";
                String blindsText = "";
                String blindsDoubleText = "";
                String rebuyText = "";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, id);
                    statement.setInt(2, id);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            found = true;
                            logEnabled = Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + nick + "_" + fecha + ".log"));
                            chatEnabled = (Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + nick + "_" + fecha + ".html")) && Files.size(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + nick + "_" + fecha + ".html")) > 0L) || (Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + nick + "_" + fecha + ".log")) && Files.size(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + nick + "_" + fecha + ".log")) > 0L);
                            playtimeText = (rs.getObject("end") != null ? Helpers.seconds2FullTime((rs.getLong("end") / 1000 - rs.getLong("start") / 1000)) : "--:--:--") + " (" + Helpers.seconds2FullTime(rs.getLong("play_time")) + ")";

                            StringBuilder players = new StringBuilder();
                            for (String j : rs.getString("players").split("#")) {
                                players.append(new String(Base64.getDecoder().decode(j.getBytes("UTF-8")), "UTF-8")).append("  |  ");
                            }
                            playersText = players.toString().replaceAll("  \\|  $", "");
                            buyinText = String.valueOf(rs.getInt("buyin"));
                            handText = String.valueOf(rs.getInt("tot_hands"));
                            // money2String redondea (doubleClean) + formatea como dinero, así
                            // las ciegas de timbas antiguas guardadas como REAL (float) no
                            // arrastran decimales basura (0.10000000149...) -> "0.10 / 0.20".
                            blindsText = Helpers.money2String(rs.getDouble("sb")) + " / " + Helpers.money2String(rs.getDouble("sb") * 2);
                            blindsDoubleText = rs.getInt("blinds_time") != -1 ? String.valueOf(rs.getInt("blinds_time")) + (rs.getInt("blinds_time_type") <= 1 ? " min" : " *") : "NO";
                            rebuyText = rs.getBoolean("rebuy") ? Translator.translate("ui.si") : "NO";
                            isPrivate = rs.getInt("private") == 1;
                        }
                    }
                } catch (Exception ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                final boolean fFound = found;
                final boolean fLog = logEnabled;
                final boolean fChat = chatEnabled;
                final String fPlaytime = playtimeText;
                final String fPlayers = playersText;
                final String fBuyin = buyinText;
                final String fHand = handText;
                final String fBlinds = blindsText;
                final String fBlindsDouble = blindsDoubleText;
                final String fRebuy = rebuyText;
                final boolean fPrivate = isPrivate;

                Helpers.GUIRunAndWait(() -> {
                    game_textarea_scrollpane.setVisible(false);
                    if (fFound) {
                        log_game_button.setEnabled(fLog);
                        chat_game_button.setEnabled(fChat);
                        game_playtime_val.setText(fPlaytime);
                        game_players_val.setText(fPlayers);
                        game_buyin_val.setText(fBuyin);
                        game_hand_val.setText(fHand);
                        game_blinds_val.setText(fBlinds);
                        game_blinds_double_val.setText(fBlindsDouble);
                        game_rebuy_val.setText(fRebuy);
                    }
                    refreshPrivateUI(fFound && fPrivate);
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                    game_data_panel.setVisible(true);
                    game_combo_blocked = false;
                    refreshFilterButtonsEnabled();
                });
            }
        });

    }

    /**
     * Decodes a '#'-separated list of Base64-encoded nicks into a "a  |  b  |  c" display
     * string. Returns "" for a null/empty value (matches the empty-label behaviour).
     */
    private static String decodePlayers(String raw) throws UnsupportedEncodingException {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String j : raw.split("#")) {
            sb.append(new String(Base64.getDecoder().decode(j.getBytes("UTF-8")), "UTF-8")).append("  |  ");
        }
        return sb.toString().replaceAll("  \\|  $", "");
    }

    private void loadHandData(int id_game, int id_hand) {

        cargando.setVisible(true);
        setEnabled(false);

        stats_db_executor.submit(() -> {
          try {
            String sql = "SELECT * FROM hand WHERE id_game=? AND id=?";

            // Read the row (including card parsing) on this worker thread; only the label
            // updates run on the EDT.
            boolean found = false;
            String preflopText = "";
            String flopText = "";
            String turnText = "";
            String riverText = "";
            String blindsText = "";
            String timeText = "";
            String cpText = "";
            String cgText = "";
            String comcardsText = "";
            String boteText = "";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, id_game);
                statement.setInt(2, id_hand);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        found = true;
                        preflopText = decodePlayers(rs.getString("preflop_players"));
                        flopText = decodePlayers(rs.getString("flop_players"));
                        turnText = decodePlayers(rs.getString("turn_players"));
                        riverText = decodePlayers(rs.getString("river_players"));
                        blindsText = Helpers.money2String(rs.getDouble("sbval")) + " / " + Helpers.money2String(rs.getDouble("sbval") * 2) + " (" + String.valueOf(rs.getInt("blinds_double")) + ")";
                        timeText = Helpers.seconds2FullTime((rs.getLong("end") / 1000 - rs.getLong("start") / 1000));
                        cpText = rs.getString("sb");
                        cgText = rs.getString("bb");
                        if (rs.getString("com_cards") != null) {
                            ArrayList<Card> cartas = new ArrayList<>();
                            for (String c : rs.getString("com_cards").split("#")) {
                                String[] partes = c.split("_");
                                Card carta = new Card(false);
                                carta.actualizarValorPalo(partes[0], partes[1]);
                                cartas.add(carta);
                            }
                            comcardsText = Card.collection2String(cartas);
                        }
                        boteText = String.valueOf(Helpers.doubleClean(rs.getDouble("pot")));
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            final boolean fFound = found;
            final String fPreflop = preflopText;
            final String fFlop = flopText;
            final String fTurn = turnText;
            final String fRiver = riverText;
            final String fBlinds = blindsText;
            final String fTime = timeText;
            final String fCp = cpText;
            final String fCg = cgText;
            final String fComcards = comcardsText;
            final String fBote = boteText;

            if (fFound) {
                Helpers.GUIRunAndWait(() -> {
                    hand_preflop_players_val.setText(fPreflop);
                    hand_flop_players_val.setText(fFlop);
                    hand_turn_players_val.setText(fTurn);
                    hand_river_players_val.setText(fRiver);
                    hand_blinds_val.setText(fBlinds);
                    hand_time_val.setText(fTime);
                    hand_cp_val.setText(fCp);
                    hand_cg_val.setText(fCg);
                    hand_comcards_val.setText(fComcards);
                    hand_bote_val.setText(fBote);
                });

                loadShowdownData(id_hand);
            }
          } finally {
            Helpers.GUIRunAndWait(() -> {
                cargando.setVisible(false);
                setEnabled(true);
                hand_data_panel.setVisible(true);
            });
          }
        });

    }

    private void loadShowdownData(int id_hand) {

        stats_db_executor.submit(() -> {
            try {
                String sql = "SELECT player AS \"player.jugador\", winner as \"ui.gana_3\", hole_cards as \"ui.cartas_recibidas\", hand_cards as \"ui.cartas_jugada\", hand_val AS \"ui.jugada\", ROUND(pay,1) as \"action.pagar\", ROUND(profit,1) as \"ui.beneficio\" FROM showdown WHERE id_hand=? order by \"ui.gana_3\" DESC,\"action.pagar\" DESC";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, id_hand);
                    try (ResultSet rs = statement.executeQuery()) {
                        showdownData(rs);
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });

    }

    private void showdownData(ResultSet rs) {
        try {

            // Build the table model on this worker thread; only the Swing wiring runs on the EDT.
            DefaultTableModel tableModel = new DefaultTableModel();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex)));
            }

            Object[] row = new Object[columnCount];

            while (rs.next()) {
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);

                    if (tableModel.getColumnName(i).equals(Translator.translate("ui.gana_3"))) {
                        row[i] = (int) row[i] == 1 ? Translator.translate("ui.si") : "NO";
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.cartas_recibidas"))) {
                        ArrayList<Card> cartas = new ArrayList<>();
                        if (row[i] != null) {
                            for (String c : ((String) row[i]).split("#")) {
                                String[] partes = c.split("_");
                                if (partes.length == 2) {
                                    Card carta = new Card(false);
                                    carta.actualizarValorPalo(partes[0], partes[1]);
                                    cartas.add(carta);
                                }
                            }
                            Card.sortCollection(cartas);
                        }
                        row[i] = row[i] != null ? Card.collection2String(cartas) : "*****";

                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.cartas_jugada"))) {
                        ArrayList<Card> cartas = new ArrayList<>();
                        if (row[i] != null) {
                            for (String c : ((String) row[i]).split("#")) {
                                String[] partes = c.split("_");
                                if (partes.length == 2) {
                                    Card carta = new Card(false);
                                    carta.actualizarValorPalo(partes[0], partes[1]);
                                    cartas.add(carta);
                                }
                            }
                        }
                        row[i] = row[i] != null ? Card.collection2String(cartas) : "-----";
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("ui.jugada"))) {
                        // Defensivo: solo un Integer en rango [1, NOMBRES_JUGADAS.length] se
                        // convierte a nombre de jugada; cualquier otra cosa (null, no-Integer
                        // por una colisión de cabeceras i18n, o fuera de rango) -> "-----".
                        // Evita el ClassCastException/AIOOBE que dejaba la tabla sin cargar.
                        row[i] = (row[i] instanceof Integer && (Integer) row[i] >= 1 && (Integer) row[i] <= Hand.NOMBRES_JUGADAS.length)
                                ? Hand.NOMBRES_JUGADAS[(Integer) row[i] - 1] : "-----";
                    }
                }
                tableModel.addRow(row);
            }

            Helpers.GUIRunAndWait(() -> {
                showdown_table.setModel(tableModel);
                TableRowSorter tableRowSorter = new TableRowSorter(showdown_table.getModel());

                Helpers.disableSortAllColumns(showdown_table, tableRowSorter);

                int idxPlayer = Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("player.jugador"));
                int idxGana = Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("ui.gana_3"));
                int idxPagar = Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("action.pagar"));
                int idxBeneficio = Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("ui.beneficio"));
                int idxJugada = Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("ui.jugada"));

                if (idxPlayer != -1) {
                    tableRowSorter.setSortable(idxPlayer, true);
                }
                if (idxGana != -1) {
                    tableRowSorter.setSortable(idxGana, true);
                }

                if (idxPagar != -1) {
                    tableRowSorter.setSortable(idxPagar, true);
                    tableRowSorter.setComparator(idxPagar, (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));
                }

                if (idxBeneficio != -1) {
                    tableRowSorter.setSortable(idxBeneficio, true);
                    tableRowSorter.setComparator(idxBeneficio, (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));
                }

                if (idxJugada != -1) {
                    tableRowSorter.setSortable(idxJugada, true);
                    tableRowSorter.setComparator(idxJugada, (Comparator<String>) (o1, o2) -> Integer.compare(Hand.handnameToHandValue(o1), Hand.handnameToHandValue(o2)));
                }

                showdown_table.setRowSorter(tableRowSorter);
                showdown_panel.setVisible(true);
            });

        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void tiempoMedioRespuesta() {

        cargando.setVisible(true);
        setEnabled(false);
        if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {

            hand_combo.setVisible(true);
            hand_combo.setSelectedIndex(0);
        }

        final String SQL_PER_HAND = "SELECT player AS \"player.jugador\", ROUND(AVG(response_time), 1) AS \"ui.tiempo\""
                + " FROM action WHERE id_hand = ? GROUP BY \"player.jugador\" ORDER BY \"ui.tiempo\" DESC";

        final String SQL_PER_GAME = "SELECT player AS \"player.jugador\", ROUND(AVG(response_time), 1) AS \"ui.tiempo\""
                + " FROM action, hand WHERE action.id_hand = hand.id AND hand.id_game = ?"
                + " GROUP BY \"player.jugador\" ORDER BY \"ui.tiempo\" DESC";

        final String SQL_ALL = "SELECT player AS \"player.jugador\", ROUND(AVG(response_time), 1) AS \"ui.tiempo\""
                + " FROM action GROUP BY \"player.jugador\" ORDER BY \"ui.tiempo\" DESC";

        // Snapshot combo state on the EDT; the worker thread must never read Swing state.
        final int handIdx = hand_combo.getSelectedIndex();
        final int gameIdx = game_combo.getSelectedIndex();
        final int handId = handIdx > 0 ? (int) hand.get((String) hand_combo.getSelectedItem()).get("id") : -1;
        final int gameId = gameIdx > 0 ? (int) game.get((String) game_combo.getSelectedItem()).get("id") : -1;

        stats_db_executor.submit(() -> {
            try {
                DefaultTableModel tableModel = new DefaultTableModel();
                if (handIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_HAND)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, handId);
                        try (ResultSet rs = st.executeQuery()) {
                            populateTiempoTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (gameIdx > 0) {
                    try (PreparedStatement st = Helpers.getSQLITE().prepareStatement(SQL_PER_GAME)) {
                        st.setQueryTimeout(30);
                        st.setInt(1, gameId);
                        try (ResultSet rs = st.executeQuery()) {
                            populateTiempoTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    try (Statement st = Helpers.getSQLITE().createStatement()) {
                        st.setQueryTimeout(30);
                        try (ResultSet rs = st.executeQuery(SQL_ALL)) {
                            populateTiempoTable(tableModel, rs);
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Helpers.GUIRunAndWait(() -> {
                    res_table.setModel(tableModel);
                    TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());
                    Helpers.disableSortAllColumns(res_table, tableRowSorter);

                    int idxPlayer = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("player.jugador"));
                    int idxTime = Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ui.tiempo") + " " + Translator.translate("ui.segundos"));

                    if (idxPlayer != -1) {
                        tableRowSorter.setSortable(idxPlayer, true);
                    }
                    if (idxTime != -1) {
                        tableRowSorter.setSortable(idxTime, true);
                        tableRowSorter.setComparator(idxTime, (Comparator<Object>) (o1, o2) -> Double.compare(toDouble(o1), toDouble(o2)));
                    }

                    res_table.setRowSorter(tableRowSorter);
                    table_panel.setVisible(true);

                    // Average response time per player (seconds).
                    LinkedHashMap<String, Double> times = new LinkedHashMap<>();
                    if (idxPlayer != -1 && idxTime != -1) {
                        for (int r = 0; r < tableModel.getRowCount(); r++) {
                            times.put(String.valueOf(tableModel.getValueAt(r, idxPlayer)), toDouble(tableModel.getValueAt(r, idxTime)));
                        }
                    }
                    showChart(times.isEmpty() ? null : StatsCharts.valueBars(times, Translator.translate("ui.tiempo_medio_de_respuesta"), Translator.translate("ui.segundos"), "{2}", StatsCharts.BLUE));
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                });
            }
        });

    }

    /**
     * Populates the response-time table. The "ui.tiempo" header is suffixed with
     * the "seconds" label to remind the user of the unit.
     */
    private static void populateTiempoTable(DefaultTableModel tableModel, ResultSet rs) throws SQLException {
        if (rs == null) {
            return;
        }
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            String colLabel = metaData.getColumnLabel(columnIndex);
            if (colLabel.equals("ui.tiempo")) {
                tableModel.addColumn(Translator.translate(colLabel) + " " + Translator.translate("ui.segundos"));
            } else {
                tableModel.addColumn(Translator.translate(colLabel));
            }
        }
        Object[] row = new Object[columnCount];
        while (rs.next()) {
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            tableModel.addRow(row);
        }
    }

    private void loadHands(int id) {

        cargando.setVisible(true);
        setEnabled(false);
        hand_combo_blocked = true;

        stats_db_executor.submit(() -> {
            try {
                String sql = "SELECT * FROM hand WHERE id_game=? AND end IS NOT NULL ORDER BY id DESC";

                // Drain on the worker thread; publish to the combo on the EDT.
                final LinkedHashMap<String, HashMap<String, Object>> loaded = new LinkedHashMap<>();

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);
                    statement.setInt(1, id);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            String label = Translator.translate("game.mano_2") + " " + String.valueOf(rs.getInt("counter"));
                            HashMap<String, Object> map = new HashMap<>();
                            map.put("id", rs.getInt("id"));
                            loaded.put(label, map);
                        }
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(() -> {
                    hand.clear();
                    hand_combo.removeAllItems();
                    hand_combo.addItem(Translator.translate("game.todas_las_manos"));
                    for (Map.Entry<String, HashMap<String, Object>> entry : loaded.entrySet()) {
                        hand.put(entry.getKey(), entry.getValue());
                        hand_combo.addItem(entry.getKey());
                    }
                });
            } finally {
                Helpers.GUIRunAndWait(() -> {
                    cargando.setVisible(false);
                    setEnabled(true);
                    hand_combo_blocked = false;
                });
            }
        });

    }

    private boolean deleteAllGames() {

        // Resolve the ids and clear the combo on the EDT (Swing state + shared map live
        // there); only the SQL runs on this worker thread.
        final String[][] idsHolder = new String[1][];
        Helpers.GUIRunAndWait(() -> {
            if (game_combo.getItemCount() > 1) {
                cargando.setVisible(true);
                setEnabled(false);
                String[] ids = new String[game_combo.getItemCount() - 1];
                for (int i = 1; i <= ids.length; i++) {
                    ids[i - 1] = String.valueOf((int) game.get((String) game_combo.getItemAt(i)).get("id"));
                }
                while (game_combo.getItemCount() > 1) {
                    game_combo.removeItemAt(game_combo.getItemCount() - 1);
                }
                idsHolder[0] = ids;
            }
        });

        if (idsHolder[0] == null) {
            return false;
        }

        String sql = "DELETE FROM game WHERE id in (" + String.join(",", idsHolder[0]) + ")";

        try (Statement statement = Helpers.getSQLITE().createStatement()) {
            statement.setQueryTimeout(30);
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }

        // A purge cascades across game/hand/action/... and frees a large number
        // of pages, but SQLite keeps them on its free-list — the file does NOT
        // shrink on its own (auto_vacuum is NONE). The game-end VACUUM only runs
        // when a whole timba finishes, so a purge done from the lobby would
        // otherwise leave the reclaimed space stuck on disk until the next game.
        // Reclaim it now, while we are already off the EDT and the "cargando"
        // spinner is up. SQLITEVAC() is self-gating (it only rewrites the file
        // when there is significant free space), so this is a cheap no-op when
        // little was actually freed.
        Helpers.SQLITEVAC();

        Helpers.GUIRunAndWait(() -> {
            cargando.setVisible(false);
            setEnabled(true);
        });

        return true;
    }

    private boolean deleteSelectedGame() {

        // Resolve the selected game and mutate the shared map on the EDT; only the SQL
        // runs on this worker thread.
        final int[] idHolder = {-1};
        Helpers.GUIRunAndWait(() -> {
            String item = (String) game_combo.getSelectedItem();
            HashMap<String, Object> g = game.get(item);
            if (g != null) {
                idHolder[0] = (int) g.get("id");
                game.remove(item);
            }
        });

        if (idHolder[0] == -1) {
            return false;
        }

        String sql = "DELETE FROM game WHERE id=?";

        try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            statement.setInt(1, idHolder[0]);
            statement.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Reclaim the free-list pages left by the cascading delete (see
        // deleteAllGames). Self-gating: a no-op unless this deletion actually
        // freed a significant fraction of the file, so deleting one small timba
        // does not trigger a full-file rewrite.
        Helpers.SQLITEVAC();

        return true;
    }

    // =====================================================================
    // "Partida privada" — una timba privada queda fuera de la sync P2P por
    // defecto: la exclusión "Partidas privadas" viene activada (StatsSync.
    // listShareableUgis la aplica), aunque el usuario puede desactivarla en el
    // diálogo "Excluir...". El flag es puramente local (no viaja en el payload).
    // =====================================================================

    /**
     * Diálogo modal de EXCLUSIONES de "Compartir": elige qué subconjunto de MIS
     * partidas queda fuera de lo que propago (privadas y/o partidas donde participó
     * ALGUNO de una lista de nicks separados por comas). Persiste las tres preferencias
     * globales al aceptar; surten efecto en el siguiente intercambio de manifiestos
     * (StatsSync.listShareableUgis las aplica). Construido a mano, fuera del .form,
     * como el resto de extras de este diálogo.
     */
    private void showShareExclusionsDialog() {
        final javax.swing.JDialog dlg = new javax.swing.JDialog(this, Translator.translate("stats.sync_exclude_title"), true);

        javax.swing.JCheckBox private_check = new javax.swing.JCheckBox(Translator.translate("stats.sync_exclude_private"));
        private_check.putClientProperty("i18n.key", "stats.sync_exclude_private");
        private_check.setSelected(GameFrame.SYNC_STATS_EXCLUDE_PRIVATE_PREF);

        javax.swing.JCheckBox nicks_check = new javax.swing.JCheckBox(Translator.translate("stats.sync_exclude_nicks"));
        nicks_check.putClientProperty("i18n.key", "stats.sync_exclude_nicks");
        nicks_check.setSelected(GameFrame.SYNC_STATS_EXCLUDE_NICKS_ENABLED_PREF);

        javax.swing.JTextField nicks_field = new javax.swing.JTextField(GameFrame.SYNC_STATS_EXCLUDE_NICKS_PREF, 36);
        Helpers.JTextFieldRegularPopupMenu.addTo(nicks_field);
        // La lista solo edita/aplica cuando su casilla está marcada.
        nicks_field.setEnabled(nicks_check.isSelected());
        nicks_check.addItemListener(ev -> nicks_field.setEnabled(nicks_check.isSelected()));

        javax.swing.JButton ok = new javax.swing.JButton(Translator.translate("ui.aceptar"));
        ok.putClientProperty("i18n.key", "ui.aceptar");
        javax.swing.JButton cancel = new javax.swing.JButton(Translator.translate("ui.cancelar_2"));
        cancel.putClientProperty("i18n.key", "ui.cancelar_2");
        ok.addActionListener(ev -> {
            String nicks = nicks_field.getText().trim();
            // Blindaje de estado coherente: casilla marcada pero lista vacía no excluye
            // nada, así que se persiste como DESACTIVADA (evita el estado engañoso
            // "excluyo por nick" sin ningún nick). El texto tecleado se conserva.
            boolean nicks_enabled = nicks_check.isSelected() && !nicks.isEmpty();
            GameFrame.SYNC_STATS_EXCLUDE_PRIVATE_PREF = private_check.isSelected();
            GameFrame.SYNC_STATS_EXCLUDE_NICKS_ENABLED_PREF = nicks_enabled;
            GameFrame.SYNC_STATS_EXCLUDE_NICKS_PREF = nicks;
            Helpers.PROPERTIES.setProperty("sync_stats_exclude_private", String.valueOf(GameFrame.SYNC_STATS_EXCLUDE_PRIVATE_PREF));
            Helpers.PROPERTIES.setProperty("sync_stats_exclude_nicks_enabled", String.valueOf(GameFrame.SYNC_STATS_EXCLUDE_NICKS_ENABLED_PREF));
            Helpers.PROPERTIES.setProperty("sync_stats_exclude_nicks", GameFrame.SYNC_STATS_EXCLUDE_NICKS_PREF);
            Helpers.savePropertiesFile();
            dlg.dispose();
        });
        cancel.addActionListener(ev -> dlg.dispose());

        javax.swing.JPanel body = new javax.swing.JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 18, 8, 18));
        private_check.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        nicks_check.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        // La caja de nicks sangrada bajo su casilla, para leerse como sub-opción.
        javax.swing.JPanel field_row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        field_row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        field_row.add(javax.swing.Box.createHorizontalStrut(26));
        field_row.add(nicks_field);

        body.add(private_check);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(nicks_check);
        body.add(javax.swing.Box.createVerticalStrut(4));
        body.add(field_row);

        javax.swing.JPanel button_row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        button_row.add(ok);
        button_row.add(cancel);

        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.add(body, java.awt.BorderLayout.CENTER);
        root.add(button_row, java.awt.BorderLayout.SOUTH);
        dlg.setContentPane(root);

        Helpers.setUniformFont(root, Helpers.GUI_FONT, Math.round(14 * Helpers.DIALOG_ZOOM));
        dlg.getRootPane().setDefaultButton(ok);
        // Se crea un diálogo nuevo en cada apertura: liberarlo al cerrar con la X (el
        // default HIDE_ON_CLOSE lo dejaría oculto en memoria, acumulándose).
        dlg.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /**
     * Genera un candado TACHADO: dibuja una diagonal roja (con halo blanco para que
     * se vea sobre cualquier fondo) sobre el icono dado. Para las acciones de "quitar
     * privada" (candado = hacer privada, candado tachado = quitarla).
     */
    private static javax.swing.ImageIcon struckIcon(java.net.URL url) {
        javax.swing.ImageIcon base = new javax.swing.ImageIcon(url);
        int w = base.getIconWidth();
        int h = base.getIconHeight();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(base.getImage(), 0, 0, null);
        // Halo blanco bajo la diagonal + diagonal roja encima, de esquina a esquina.
        g.setStroke(new java.awt.BasicStroke(4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new java.awt.Color(255, 255, 255, 220));
        g.drawLine(3, h - 3, w - 3, 3);
        g.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new java.awt.Color(204, 0, 0));
        g.drawLine(3, h - 3, w - 3, 3);
        g.dispose();
        return new javax.swing.ImageIcon(img);
    }

    /** EDT. Sincroniza los 3 botones de la barra (purgar + privadas globales) con el filtro de jugador. */
    private void refreshFilterButtonsEnabled() {
        boolean filter_active = game_combo_filter.getBackground() == Color.YELLOW;
        purge_games_button.setEnabled(filter_active);
        private_all_button.setEnabled(filter_active);
        unprivate_all_button.setEnabled(filter_active);
    }

    /** EDT. Muestra/oculta el banner y habilita el botón "HACER PRIVADA" según el estado de la timba seleccionada. */
    private void refreshPrivateUI(boolean is_private) {
        private_game_label.setVisible(is_private);
        private_game_button.setEnabled(!is_private);
        java.awt.Container parent = private_game_label.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    /** Marca/desmarca como privada la timba seleccionada (off-EDT) y refresca el banner/botón. */
    private void setSelectedGamePrivateAsync(boolean priv) {
        Helpers.GUIRun(() -> private_game_button.setEnabled(false));
        stats_db_executor.submit(() -> {
            boolean ok = setSelectedGamePrivate(priv);
            // Si falla, refleja el estado real (sin cambios).
            Helpers.GUIRun(() -> refreshPrivateUI(ok ? priv : !priv));
        });
    }

    /** Off-EDT. UPDATE del flag private de la timba seleccionada + coherencia del mapa en memoria. */
    private boolean setSelectedGamePrivate(boolean priv) {
        final int[] idHolder = {-1};
        Helpers.GUIRunAndWait(() -> {
            HashMap<String, Object> g = game.get((String) game_combo.getSelectedItem());
            if (g != null) {
                idHolder[0] = (int) g.get("id");
            }
        });

        if (idHolder[0] == -1) {
            return false;
        }

        try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement("UPDATE game SET private=? WHERE id=?")) {
            statement.setQueryTimeout(30);
            statement.setInt(1, priv ? 1 : 0);
            statement.setInt(2, idHolder[0]);
            statement.executeUpdate();
        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        // Mantener el mapa en memoria coherente (lo consulta la selección de timba).
        Helpers.GUIRun(() -> {
            HashMap<String, Object> g = game.get((String) game_combo.getSelectedItem());
            if (g != null) {
                g.put("private", priv ? 1 : 0);
            }
        });

        return true;
    }

    /** Marca/desmarca privadas TODAS las timbas del jugador filtrado (como purgar), con confirmación. */
    private void markFilteredGamesPrivateAsync(boolean priv) {
        Helpers.GUIRun(() -> {
            private_all_button.setEnabled(false);
            unprivate_all_button.setEnabled(false);
        });

        String confirm_key = priv ? "player.marcar_privadas_todas_las_timbas" : "player.quitar_privadas_todas_las_timbas";

        if (Helpers.mostrarMensajeInformativoSINO(getContentPane(), Translator.translate(confirm_key), new ImageIcon(Init.class.getResource("/images/lock.png"))) == 0) {
            stats_db_executor.submit(() -> {
                if (setFilteredGamesPrivate(priv)) {
                    Helpers.mostrarMensajeInformativo(getContentPane(), Translator.translate(priv ? "player.se_han_marcado_privadas_todas" : "player.se_han_quitado_privadas_todas"), new ImageIcon(Init.class.getResource("/images/lock.png")));
                    loadGames();
                    Helpers.GUIRun(() -> {
                        game_combo.setSelectedIndex(0);
                        game_data_panel.setVisible(false);
                    });
                }
                Helpers.GUIRun(this::refreshFilterButtonsEnabled);
            });
        } else {
            Helpers.GUIRun(this::refreshFilterButtonsEnabled);
        }
    }

    /** Off-EDT. UPDATE del flag private para todas las timbas actualmente en el combo (= las del jugador filtrado). */
    private boolean setFilteredGamesPrivate(boolean priv) {
        final String[][] idsHolder = new String[1][];
        Helpers.GUIRunAndWait(() -> {
            if (game_combo.getItemCount() > 1) {
                String[] ids = new String[game_combo.getItemCount() - 1];
                for (int i = 1; i <= ids.length; i++) {
                    ids[i - 1] = String.valueOf((int) game.get((String) game_combo.getItemAt(i)).get("id"));
                }
                idsHolder[0] = ids;
            }
        });

        if (idsHolder[0] == null) {
            return false;
        }

        // ids vienen de nuestro propio mapa (enteros), no de texto del usuario — como en deleteAllGames.
        String sql = "UPDATE game SET private=" + (priv ? 1 : 0) + " WHERE id in (" + String.join(",", idsHolder[0]) + ")";

        try (Statement statement = Helpers.getSQLITE().createStatement()) {
            statement.setQueryTimeout(30);
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    private void loadGames() {

        Helpers.GUIRunAndWait(() -> {
            cargando.setVisible(true);
            setEnabled(false);
            game_combo_blocked = true;
        });

        // Capture the filter on the EDT (Swing state must not be read off-EDT).
        final String[] filtroHolder = new String[1];
        Helpers.GUIRunAndWait(() -> {
            if (!game_combo_filter.getText().isBlank() && game_combo_filter.getBackground() != Color.RED) {
                filtroHolder[0] = game_combo_filter.getText().trim().toUpperCase();
            }
        });
        final String filtro = filtroHolder[0];

        // Drain the ResultSet fully on this worker thread; Swing is only touched afterwards.
        // try-with-resources guarantees the statement/ResultSet close on every exit path.
        final LinkedHashMap<String, HashMap<String, Object>> loaded = new LinkedHashMap<>();
        final DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement("SELECT * FROM game ORDER BY start DESC")) {
            statement.setQueryTimeout(30);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    boolean ok = true;
                    if (filtro != null) {
                        ArrayList<String> decoded_players = new ArrayList<>();
                        for (String p : rs.getString("players").split("#")) {
                            decoded_players.add(new String(Base64.getDecoder().decode(p), "UTF-8").trim().toUpperCase());
                        }
                        ok = decoded_players.contains(filtro);
                    }
                    if (ok) {
                        Date date = new Date(new Timestamp(rs.getLong("start")).getTime());
                        HashMap<String, Object> map = new HashMap<>();
                        map.put("id", rs.getInt("id"));
                        map.put("start_timestamp", rs.getLong("start"));
                        map.put("private", rs.getInt("private"));
                        String game_length = Helpers.seconds2FullTime(rs.getLong("play_time"));
                        loaded.put(rs.getString("server") + " @ " + timeZoneFormat.format(date) + " @ " + game_length, map);
                    }
                }
            }
        } catch (SQLException | UnsupportedEncodingException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }

        // Publish the drained games to the combo on the EDT.
        Helpers.GUIRunAndWait(() -> {
            game.clear();
            game_combo.removeAllItems();
            game_combo.addItem(Translator.translate("game.todas_las_timbas"));
            for (Map.Entry<String, HashMap<String, Object>> entry : loaded.entrySet()) {
                game.put(entry.getKey(), entry.getValue());
                game_combo.addItem(entry.getKey());
            }
            if (filtro != null && loaded.isEmpty()) {
                game_combo_filter.setBackground(Color.RED);
            }
            refreshFilterButtonsEnabled();
        });

        Helpers.GUIRunAndWait(() -> {
            cargando.setVisible(false);
            setEnabled(true);
            game_combo_blocked = false;
        });

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        title = new javax.swing.JLabel();
        scroll_stats_panel = new javax.swing.JScrollPane();
        stats_panel = new javax.swing.JPanel();
        game_combo = new javax.swing.JComboBox<>();
        game_data_panel = new javax.swing.JPanel();
        game_hand_label = new javax.swing.JLabel();
        game_players_label = new javax.swing.JLabel();
        game_buyin_label = new javax.swing.JLabel();
        game_blinds_label = new javax.swing.JLabel();
        game_blinds_double_label = new javax.swing.JLabel();
        game_rebuy_label = new javax.swing.JLabel();
        game_hand_val = new javax.swing.JLabel();
        game_players_val = new javax.swing.JLabel();
        game_buyin_val = new javax.swing.JLabel();
        game_blinds_val = new javax.swing.JLabel();
        game_blinds_double_val = new javax.swing.JLabel();
        game_rebuy_val = new javax.swing.JLabel();
        game_playtime_label = new javax.swing.JLabel();
        game_playtime_val = new javax.swing.JLabel();
        delete_game_button = new javax.swing.JButton();
        log_game_button = new javax.swing.JButton();
        chat_game_button = new javax.swing.JButton();
        game_textarea_scrollpane = new javax.swing.JScrollPane();
        game_textarea = new javax.swing.JEditorPane();
        game_combo_filter = new javax.swing.JTextField();
        purge_games_button = new javax.swing.JButton();
        hands_panel = new javax.swing.JPanel();
        hand_combo = new javax.swing.JComboBox<>();
        hand_data_panel = new javax.swing.JPanel();
        hand_blinds_label = new javax.swing.JLabel();
        hand_blinds_val = new javax.swing.JLabel();
        hand_time_label = new javax.swing.JLabel();
        hand_cp_label = new javax.swing.JLabel();
        hand_cg_label = new javax.swing.JLabel();
        hand_comcards_label = new javax.swing.JLabel();
        hand_preflop_players_label = new javax.swing.JLabel();
        hand_flop_players_label = new javax.swing.JLabel();
        hand_turn_players_label = new javax.swing.JLabel();
        hand_river_players_label = new javax.swing.JLabel();
        hand_bote_label = new javax.swing.JLabel();
        hand_time_val = new javax.swing.JLabel();
        hand_cp_val = new javax.swing.JLabel();
        hand_cg_val = new javax.swing.JLabel();
        hand_comcards_val = new javax.swing.JLabel();
        hand_preflop_players_val = new javax.swing.JLabel();
        hand_flop_players_val = new javax.swing.JLabel();
        hand_turn_players_val = new javax.swing.JLabel();
        hand_river_players_val = new javax.swing.JLabel();
        hand_bote_val = new javax.swing.JLabel();
        showdown_panel = new javax.swing.JScrollPane();
        showdown_table = new javax.swing.JTable();
        stats_combo = new javax.swing.JComboBox<>();
        table_panel = new javax.swing.JScrollPane();
        res_table = new javax.swing.JTable();
        res_table_warning = new javax.swing.JLabel();
        cargando = new javax.swing.JProgressBar();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Lo que no son cuentas, son cuentos");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        title.setBackground(new java.awt.Color(255, 102, 0));
        title.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        title.setForeground(new java.awt.Color(255, 255, 255));
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        title.setText(Translator.translate("ui.estadisticas"));
        title.putClientProperty("i18n.key", "ui.estadisticas");
        title.setFocusable(false);
        title.setOpaque(true);

        scroll_stats_panel.setBorder(null);

        game_combo.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                game_comboItemStateChanged(evt);
            }
        });

        game_data_panel.setOpaque(false);

        game_hand_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_hand_label.setText(Translator.translate("stats.manos"));
        game_hand_label.putClientProperty("i18n.key", "stats.manos");

        game_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_players_label.setText(Translator.translate("stats.jugadores"));
        game_players_label.putClientProperty("i18n.key", "stats.jugadores");

        game_buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_buyin_label.setText(Translator.translate("stats.buyin"));
        game_buyin_label.putClientProperty("i18n.key", "stats.compra");

        game_blinds_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_blinds_label.setText(Translator.translate("stats.ciegas"));
        game_blinds_label.putClientProperty("i18n.key", "stats.ciegas");

        game_blinds_double_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_blinds_double_label.setText(Translator.translate("stats.aumentar_ciegas"));
        game_blinds_double_label.putClientProperty("i18n.key", "stats.aumentar_ciegas");

        game_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_rebuy_label.setText(Translator.translate("stats.recomprar"));
        game_rebuy_label.putClientProperty("i18n.key", "stats.recomprar");

        game_hand_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_hand_val.setText(" ");

        game_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_players_val.setText(" ");

        game_buyin_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_buyin_val.setText(" ");

        game_blinds_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_blinds_val.setText(" ");

        game_blinds_double_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_blinds_double_val.setText(" ");

        game_rebuy_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_rebuy_val.setText(" ");

        game_playtime_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_playtime_label.setText(Translator.translate("stats.duracion"));
        game_playtime_label.putClientProperty("i18n.key", "stats.duracion");

        game_playtime_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_playtime_val.setText(" ");

        delete_game_button.setBackground(new java.awt.Color(255, 0, 0));
        delete_game_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        delete_game_button.setForeground(new java.awt.Color(255, 255, 255));
        delete_game_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/remove.png"))); // NOI18N
        delete_game_button.setText(Translator.translate("stats.eliminar_timba"));
        delete_game_button.putClientProperty("i18n.key", "stats.eliminar_timba");
        delete_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        delete_game_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                delete_game_buttonActionPerformed(evt);
            }
        });

        log_game_button.setBackground(new java.awt.Color(102, 102, 102));
        log_game_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        log_game_button.setForeground(new java.awt.Color(255, 255, 255));
        log_game_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/log2.png"))); // NOI18N
        log_game_button.setText(Translator.translate("stats.registro_de_la_timba"));
        log_game_button.putClientProperty("i18n.key", "stats.registro_de_la_timba");
        log_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        log_game_button.setPreferredSize(new java.awt.Dimension(242, 34));
        log_game_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                log_game_buttonActionPerformed(evt);
            }
        });

        chat_game_button.setBackground(new java.awt.Color(0, 102, 153));
        chat_game_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        chat_game_button.setForeground(new java.awt.Color(255, 255, 255));
        chat_game_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/chat.png"))); // NOI18N
        chat_game_button.setText(Translator.translate("stats.chat_de_la_timba"));
        chat_game_button.putClientProperty("i18n.key", "stats.chat_de_la_timba");
        chat_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat_game_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_game_buttonActionPerformed(evt);
            }
        });


        game_textarea.setEditable(false);
        game_textarea.setBorder(null);
        game_textarea_scrollpane.setViewportView(game_textarea);

        javax.swing.GroupLayout game_data_panelLayout = new javax.swing.GroupLayout(game_data_panel);
        game_data_panel.setLayout(game_data_panelLayout);
        game_data_panelLayout.setHorizontalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(game_textarea_scrollpane)
                    .addGroup(game_data_panelLayout.createSequentialGroup()
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(game_blinds_double_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_blinds_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_buyin_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_hand_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_playtime_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_players_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_rebuy_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_hand_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_playtime_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_buyin_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_blinds_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_blinds_double_val, javax.swing.GroupLayout.DEFAULT_SIZE, 1056, Short.MAX_VALUE)
                            .addComponent(game_rebuy_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, game_data_panelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(log_game_button, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(chat_game_button)
                        .addGap(18, 18, 18)
                        .addComponent(delete_game_button)))
                .addContainerGap())
        );
        game_data_panelLayout.setVerticalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(delete_game_button)
                    .addComponent(log_game_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chat_game_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(game_textarea_scrollpane, javax.swing.GroupLayout.PREFERRED_SIZE, 480, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_playtime_label)
                    .addComponent(game_playtime_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_hand_label)
                    .addComponent(game_hand_val, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_players_label)
                    .addComponent(game_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_buyin_label)
                    .addComponent(game_buyin_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_blinds_label)
                    .addComponent(game_blinds_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_blinds_double_label)
                    .addComponent(game_blinds_double_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_rebuy_label)
                    .addComponent(game_rebuy_val))
                .addContainerGap())
        );

        game_combo_filter.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        game_combo_filter.putClientProperty("i18n.tooltip_key", "tooltip.search_games_player");
        game_combo_filter.putClientProperty("i18n.tooltip_key", "stats.listar_solo_timbas_donde_participo");
        game_combo_filter.setPreferredSize(new java.awt.Dimension(5, 3));
        game_combo_filter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                game_combo_filterActionPerformed(evt);
            }
        });

        purge_games_button.setBackground(new java.awt.Color(0, 0, 0));
        purge_games_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        purge_games_button.setForeground(new java.awt.Color(255, 255, 255));
        purge_games_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/remove.png"))); // NOI18N
        purge_games_button.setText(Translator.translate("ui.purgar"));
        purge_games_button.putClientProperty("i18n.key", "stats.purgar");
        purge_games_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        purge_games_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                purge_games_button_buttonActionPerformed(evt);
            }
        });

        hands_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        hand_combo.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                hand_comboItemStateChanged(evt);
            }
        });

        hand_data_panel.setOpaque(false);

        hand_blinds_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_blinds_label.setText(Translator.translate("stats.ciegas"));
        hand_blinds_label.putClientProperty("i18n.key", "stats.ciegas");
        hand_blinds_label.setPreferredSize(new java.awt.Dimension(45, 17));

        hand_blinds_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_blinds_val.setText(" ");

        hand_time_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_time_label.setText(Translator.translate("stats.duracion"));
        hand_time_label.putClientProperty("i18n.key", "stats.duracion");

        hand_cp_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_cp_label.setText(Translator.translate("stats.ciega_pequena"));
        hand_cp_label.putClientProperty("i18n.key", "stats.ciega_pequena");

        hand_cg_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_cg_label.setText(Translator.translate("stats.ciega_grande"));
        hand_cg_label.putClientProperty("i18n.key", "stats.ciega_grande");

        hand_comcards_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_comcards_label.setText(Translator.translate("stats.cartas_comunitarias"));
        hand_comcards_label.putClientProperty("i18n.key", "stats.cartas_comunitarias");

        hand_preflop_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_preflop_players_label.setText(Translator.translate("stats.jugadores_preflop"));
        hand_preflop_players_label.putClientProperty("i18n.key", "stats.jugadores_preflop");

        hand_flop_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_flop_players_label.setText(Translator.translate("stats.jugadores_flop"));
        hand_flop_players_label.putClientProperty("i18n.key", "stats.jugadores_flop");

        hand_turn_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_turn_players_label.setText(Translator.translate("stats.jugadores_turn"));
        hand_turn_players_label.putClientProperty("i18n.key", "stats.jugadores_turn");

        hand_river_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_river_players_label.setText(Translator.translate("stats.jugadores_river"));
        hand_river_players_label.putClientProperty("i18n.key", "stats.jugadores_river");

        hand_bote_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_bote_label.setText(Translator.translate("stats.bote"));
        hand_bote_label.putClientProperty("i18n.key", "stats.bote");

        hand_time_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_time_val.setText(" ");

        hand_cp_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_cp_val.setText(" ");

        hand_cg_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_cg_val.setText(" ");

        hand_comcards_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_comcards_val.setText(" ");

        hand_preflop_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_preflop_players_val.setText(" ");

        hand_flop_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_flop_players_val.setText(" ");

        hand_turn_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_turn_players_val.setText(" ");

        hand_river_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_river_players_val.setText(" ");

        hand_bote_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_bote_val.setText(" ");


        showdown_table.setFont(new java.awt.Font("DejaVu Sans", 0, 16)); // NOI18N
        showdown_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        showdown_panel.setViewportView(showdown_table);

        javax.swing.GroupLayout hand_data_panelLayout = new javax.swing.GroupLayout(hand_data_panel);
        hand_data_panel.setLayout(hand_data_panelLayout);
        hand_data_panelLayout.setHorizontalGroup(
            hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hand_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(showdown_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 1191, Short.MAX_VALUE)
                    .addGroup(hand_data_panelLayout.createSequentialGroup()
                        .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hand_blinds_label, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(hand_time_label)
                            .addComponent(hand_cp_label)
                            .addComponent(hand_cg_label)
                            .addComponent(hand_comcards_label)
                            .addComponent(hand_preflop_players_label)
                            .addComponent(hand_flop_players_label)
                            .addComponent(hand_turn_players_label)
                            .addComponent(hand_bote_label)
                            .addComponent(hand_river_players_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hand_blinds_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_time_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_cp_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_cg_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_comcards_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_preflop_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_flop_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_turn_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_river_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hand_bote_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        hand_data_panelLayout.setVerticalGroup(
            hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hand_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_blinds_label, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(hand_blinds_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_time_label)
                    .addComponent(hand_time_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_cp_label)
                    .addComponent(hand_cp_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_cg_label)
                    .addComponent(hand_cg_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_comcards_label)
                    .addComponent(hand_comcards_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_preflop_players_label)
                    .addComponent(hand_preflop_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_flop_players_label)
                    .addComponent(hand_flop_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_turn_players_label)
                    .addComponent(hand_turn_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_river_players_label)
                    .addComponent(hand_river_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(hand_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(hand_bote_label)
                    .addComponent(hand_bote_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(showdown_panel, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        stats_combo.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        stats_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        stats_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                stats_comboItemStateChanged(evt);
            }
        });


        res_table.setFont(new java.awt.Font("DejaVu Sans", 0, 16)); // NOI18N
        res_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_panel.setViewportView(res_table);

        res_table_warning.setFont(new java.awt.Font("Dialog", 2, 14)); // NOI18N
        res_table_warning.setText(Translator.translate("ui.nota"));

        javax.swing.GroupLayout hands_panelLayout = new javax.swing.GroupLayout(hands_panel);
        hands_panel.setLayout(hands_panelLayout);
        hands_panelLayout.setHorizontalGroup(
            hands_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hands_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(hands_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hand_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(stats_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(table_panel)
                    .addComponent(hand_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(res_table_warning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        hands_panelLayout.setVerticalGroup(
            hands_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hands_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(hand_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(hand_data_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(stats_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(table_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 475, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(res_table_warning)
                .addContainerGap())
        );

        javax.swing.GroupLayout stats_panelLayout = new javax.swing.GroupLayout(stats_panel);
        stats_panel.setLayout(stats_panelLayout);
        stats_panelLayout.setHorizontalGroup(
            stats_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(game_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(stats_panelLayout.createSequentialGroup()
                .addComponent(game_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_combo_filter, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(purge_games_button)
                .addContainerGap())
            .addComponent(hands_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        stats_panelLayout.setVerticalGroup(
            stats_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(stats_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(stats_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_combo_filter, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(purge_games_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_data_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hands_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        scroll_stats_panel.setViewportView(stats_panel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_stats_panel)
                .addContainerGap())
            .addComponent(title, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(cargando, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(title)
                .addGap(2, 2, 2)
                .addComponent(cargando, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(scroll_stats_panel)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void game_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_game_comboItemStateChanged

        // itemStateChanged fires twice per change (DESELECTED for the old item, SELECTED
        // for the new); both report the new index, so without this guard every selection
        // ran loadGameData/loadHands and the stat query twice.
        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) {
            return;
        }

        if (!game_combo_blocked) {
            if (game_combo.getSelectedIndex() != -1) {

                if (game.get((String) game_combo.getSelectedItem()) != null && game_combo.getSelectedIndex() > 0) {
                    loadGameData((int) game.get((String) game_combo.getSelectedItem()).get("id"));
                    loadHands((int) game.get((String) game_combo.getSelectedItem()).get("id"));
                    hand_combo.setSelectedIndex(-1);
                    hand_data_panel.setVisible(false);

                } else {
                    hand_combo.setVisible(false);
                    hand_combo.setSelectedIndex(-1);
                    hand_data_panel.setVisible(false);
                    game_data_panel.setVisible(false);
                }

                if (stats_combo.getSelectedIndex() >= 0) {
                    res_table_warning.setVisible(false);
                    res_table.setRowSorter(null);
                    showChart(null);
                    sqlstats.get((String) stats_combo.getSelectedItem()).call();

                }

            } else {

                hand_combo.setVisible(false);
                hand_data_panel.setVisible(false);
                game_data_panel.setVisible(false);
                stats_combo.setVisible(false);
                table_panel.setVisible(false);
                res_table_warning.setVisible(false);
                showChart(null);
            }
        }
    }//GEN-LAST:event_game_comboItemStateChanged

    private void stats_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_stats_comboItemStateChanged

        // Only act on SELECTED: itemStateChanged also fires DESELECTED, which would run
        // the stat query a second time for the same selection.
        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) {
            return;
        }

        if (!init && stats_combo.getSelectedIndex() != -1) {
            res_table_warning.setVisible(false);
            res_table.setRowSorter(null);
            showChart(null);
            sqlstats.get((String) stats_combo.getSelectedItem()).call();
        }
    }//GEN-LAST:event_stats_comboItemStateChanged

    private void hand_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_hand_comboItemStateChanged

        // Only act on SELECTED: itemStateChanged also fires DESELECTED, which would run
        // loadHandData and the stat query a second time for the same selection.
        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) {
            return;
        }

        if (!hand_combo_blocked) {

            if (hand_combo.getSelectedIndex() != -1) {

                if (game.get((String) game_combo.getSelectedItem()) != null && hand.get((String) hand_combo.getSelectedItem()) != null) {

                    loadHandData((int) game.get((String) game_combo.getSelectedItem()).get("id"), (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                } else {

                    hand_data_panel.setVisible(false);
                }

                if (stats_combo.getSelectedIndex() >= 0) {

                    res_table_warning.setVisible(false);
                    res_table.setRowSorter(null);
                    showChart(null);
                    sqlstats.get((String) stats_combo.getSelectedItem()).call();
                }

            } else {

                hand_combo.setVisible(false);
                hand_data_panel.setVisible(false);
            }

        }

    }//GEN-LAST:event_hand_comboItemStateChanged

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:
        INSTANCE = null;

        // shutdownNow (no bloqueante): interrumpe cualquier consulta en vuelo al cerrar
        // la ventana. El hilo es daemon, así que aunque quedara algo no impide salir.
        stats_db_executor.shutdownNow();

        Audio.stopLoopMp3("misc/stats_music.mp3");

        // Skipped when disposeIfOpen() already settled the loop state for a screen
        // transition (it would otherwise unmute a loop the new screen just muted).
        if (!suppress_music_restore && last_mp3_loop != null) {
            Audio.unmuteLoopMp3(last_mp3_loop);
        }
    }//GEN-LAST:event_formWindowClosed

    private void delete_game_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_delete_game_buttonActionPerformed
        // TODO add your handling code here:

        delete_game_button.setEnabled(false);

        if (Helpers.mostrarMensajeInformativoSINO(getContentPane(), Translator.translate("error.eliminar_esta_timba_nota_las"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {

            Audio.playWavResource("misc/toilet.wav");

            stats_db_executor.submit(() -> {
                if (!backup) {

                    try {
                        Files.copy(Paths.get(Init.SQL_FILE), Paths.get(Init.SQL_FILE + "_" + String.valueOf(System.currentTimeMillis()) + ".bak"));
                        backup = true;
                    } catch (IOException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (deleteSelectedGame()) {
                    Helpers.GUIRun(() -> {
                        game_data_panel.setVisible(false);
                    });
                    loadGames();
                    Helpers.GUIRun(() -> {
                        if (!game.isEmpty()) {
                            game_combo.setSelectedIndex(1);
                        }
                    });
                }
                // Always re-enable, even if nothing was deleted, so the button never sticks disabled.
                Helpers.GUIRun(() -> delete_game_button.setEnabled(true));
            });
        } else {
            delete_game_button.setEnabled(true);
        }
    }//GEN-LAST:event_delete_game_buttonActionPerformed

    private void game_combo_filterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_game_combo_filterActionPerformed
        // TODO add your handling code here:

        if (game_combo_filter.getText().isBlank()) {
            game_combo_filter.setBackground(null);
        } else {
            game_combo_filter.setBackground(Color.YELLOW);
        }

        stats_db_executor.submit(() -> {
            loadGames();

            final boolean[] noMatches = {false};
            Helpers.GUIRunAndWait(() -> {
                game_combo.setSelectedIndex(0);
                hand_combo.setVisible(false);
                stats_combo.setSelectedIndex(-1);
                table_panel.setVisible(false);
                res_table_warning.setVisible(false);
                game_data_panel.setVisible(false);
                showChart(null);
                noMatches[0] = !game_combo_filter.getText().isBlank() && game_combo.getItemCount() == 1;
            });

            if (noMatches[0]) {
                Helpers.mostrarMensajeError(getContentPane(), Translator.translate("player.no_hay_timbas_en_las"));
            }
        });

    }//GEN-LAST:event_game_combo_filterActionPerformed

    private void log_game_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_log_game_buttonActionPerformed
        // TODO add your handling code here:

        if (game_textarea_scrollpane.isVisible() && last_button == 1) {
            game_textarea_scrollpane.setVisible(false);

            revalidate();
            repaint();
        } else {

            last_button = 1;

            boolean chat_button_enabled = chat_game_button.isEnabled();

            chat_game_button.setEnabled(false);

            log_game_button.setEnabled(false);

            cargando.setVisible(true);

            String item = (String) game_combo.getSelectedItem();

            String[] parts = item.split("@");

            String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");

            stats_db_executor.submit(() -> {
                try {
                    String log1 = Files.readString(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".log"), StandardCharsets.UTF_8).replaceAll(">>>>>>>>>>>>>>>>>>>>>>>>>>>>", "&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;&gt;").replaceAll("<<<<<<<<<<<<<<<<<<<<<<<<<<<<", "&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;").replaceAll("[*]{15} [^*]+ [*]{15}", "<b>$0</b>").replaceAll("\n", "<br>");
                    Helpers.GUIRun(() -> {
                        game_textarea.setText("<html><body style='color:white;background-color:rgb(102,102,102)'>" + log1 + "</body></html>");
                        game_textarea_scrollpane.setVisible(true);
                        game_textarea.setCaretPosition(0);
                        chat_game_button.setEnabled(chat_button_enabled);
                        log_game_button.setEnabled(true);
                        cargando.setVisible(false);
                    });
                } catch (IOException ex) {
                    Helpers.mostrarMensajeError(getContentPane(), Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".log");
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    Helpers.GUIRun(() -> {
                        chat_game_button.setEnabled(true);

                        log_game_button.setEnabled(true);

                        cargando.setVisible(false);
                    });
                }
            });
        }

    }//GEN-LAST:event_log_game_buttonActionPerformed

    private void purge_games_button_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_purge_games_button_buttonActionPerformed
        // TODO add your handling code here:
        purge_games_button.setEnabled(false);

        if (Helpers.mostrarMensajeInformativoSINO(getContentPane(), Translator.translate("player.eliminar_todas_las_timbas_donde"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png"))) == 0) {
            Audio.playWavResource("misc/toilet.wav");

            stats_db_executor.submit(() -> {
                if (!backup) {

                    try {
                        Files.copy(Paths.get(Init.SQL_FILE), Paths.get(Init.SQL_FILE + "_" + String.valueOf(System.currentTimeMillis()) + ".bak"));
                        backup = true;
                    } catch (IOException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (deleteAllGames()) {
                    Helpers.GUIRunAndWait(() -> {
                        game_combo_filter.setBackground(Color.BLACK);
                        game_combo_filter.setForeground(Color.WHITE);
                    });
                    Helpers.mostrarMensajeInformativo(getContentPane(), Translator.translate("player.se_han_borrado_todas_las"), new ImageIcon(Init.class.getResource("/images/mantenimiento.png")));
                    Helpers.GUIRunAndWait(() -> {
                        game_combo_filter.setText("");
                        game_combo_filter.setBackground(null);
                        game_combo_filter.setForeground(null);
                        game_data_panel.setVisible(false);
                    });
                    loadGames();
                    Helpers.GUIRun(() -> {
                        game_combo.setSelectedIndex(0);

                        delete_game_button.setEnabled(true);
                    });
                }
            });
        } else {
            purge_games_button.setEnabled(true);
        }
    }//GEN-LAST:event_purge_games_button_buttonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:

        if (isEnabled() && !cargando.isVisible()) {
            dispose();
        }
    }//GEN-LAST:event_formWindowClosing

    private void chat_game_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_game_buttonActionPerformed
        // TODO add your handling code here:
        if (game_textarea_scrollpane.isVisible() && last_button == 2) {
            game_textarea_scrollpane.setVisible(false);

            revalidate();
            repaint();
        } else {

            chat_game_button.setEnabled(false);

            boolean log_button_enabled = log_game_button.isEnabled();

            log_game_button.setEnabled(false);

            cargando.setVisible(true);

            last_button = 2;

            String item = (String) game_combo.getSelectedItem();

            String[] parts = item.split("@");

            String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");

            stats_db_executor.submit(() -> {
                try {
                    String chat_log;
                    if (Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".html"))) {
                        chat_log = Helpers.updateJarImgSrc(Files.readString(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".html"), StandardCharsets.UTF_8)).replaceAll("<img *?id *?= *?'avatar[^<>]+>", "");
                    } else {
                        chat_log = "<html><body style='background-color:rgb(0,102,153);color:white'>" + Files.readString(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".log"), StandardCharsets.UTF_8).replaceAll("\n", "<br><br>") + "</body></html>";
                    }
                    Helpers.GUIRun(() -> {
                        game_textarea.setText(chat_log);

                        game_textarea_scrollpane.setVisible(true);

                        game_textarea.setCaretPosition(0);

                        chat_game_button.setEnabled(true);

                        log_game_button.setEnabled(log_button_enabled);

                        cargando.setVisible(false);
                    });
                } catch (IOException ex) {
                    Helpers.mostrarMensajeError(getContentPane(), Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + Helpers.safeNickForFilename(parts[0].trim()) + "_" + fecha + ".log");
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    Helpers.GUIRun(() -> {
                        chat_game_button.setEnabled(true);

                        log_game_button.setEnabled(true);

                        cargando.setVisible(false);
                    });
                }
            });
        }

    }//GEN-LAST:event_chat_game_buttonActionPerformed

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // Non-modal JFrame: it does not participate in the modal-dialog stack.
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // Non-modal JFrame: it does not participate in the modal-dialog stack.
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:
        last_mp3_loop = Audio.getCurrentLoopMp3Playing();

        if (GameFrame.SONIDOS && last_mp3_loop != null && !Audio.MP3_LOOP_MUTED.contains(last_mp3_loop)) {
            Audio.muteLoopMp3(last_mp3_loop);
        } else {
            last_mp3_loop = null;
        }

        Audio.playLoopMp3Resource("misc/stats_music.mp3");
    }//GEN-LAST:event_formWindowOpened

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar cargando;
    private javax.swing.JButton chat_game_button;
    private javax.swing.JButton delete_game_button;
    private javax.swing.JLabel game_blinds_double_label;
    private javax.swing.JLabel game_blinds_double_val;
    private javax.swing.JLabel game_blinds_label;
    private javax.swing.JLabel game_blinds_val;
    private javax.swing.JLabel game_buyin_label;
    private javax.swing.JLabel game_buyin_val;
    private javax.swing.JComboBox<String> game_combo;
    private javax.swing.JTextField game_combo_filter;
    private javax.swing.JPanel game_data_panel;
    private javax.swing.JLabel game_hand_label;
    private javax.swing.JLabel game_hand_val;
    private javax.swing.JLabel game_players_label;
    private javax.swing.JLabel game_players_val;
    private javax.swing.JLabel game_playtime_label;
    private javax.swing.JLabel game_playtime_val;
    private javax.swing.JLabel game_rebuy_label;
    private javax.swing.JLabel game_rebuy_val;
    private javax.swing.JEditorPane game_textarea;
    private javax.swing.JScrollPane game_textarea_scrollpane;
    private javax.swing.JLabel hand_blinds_label;
    private javax.swing.JLabel hand_blinds_val;
    private javax.swing.JLabel hand_bote_label;
    private javax.swing.JLabel hand_bote_val;
    private javax.swing.JLabel hand_cg_label;
    private javax.swing.JLabel hand_cg_val;
    private javax.swing.JComboBox<String> hand_combo;
    private javax.swing.JLabel hand_comcards_label;
    private javax.swing.JLabel hand_comcards_val;
    private javax.swing.JLabel hand_cp_label;
    private javax.swing.JLabel hand_cp_val;
    private javax.swing.JPanel hand_data_panel;
    private javax.swing.JLabel hand_flop_players_label;
    private javax.swing.JLabel hand_flop_players_val;
    private javax.swing.JLabel hand_preflop_players_label;
    private javax.swing.JLabel hand_preflop_players_val;
    private javax.swing.JLabel hand_river_players_label;
    private javax.swing.JLabel hand_river_players_val;
    private javax.swing.JLabel hand_time_label;
    private javax.swing.JLabel hand_time_val;
    private javax.swing.JLabel hand_turn_players_label;
    private javax.swing.JLabel hand_turn_players_val;
    private javax.swing.JPanel hands_panel;
    private javax.swing.JButton log_game_button;
    private javax.swing.JButton purge_games_button;
    private javax.swing.JTable res_table;
    private javax.swing.JLabel res_table_warning;
    private javax.swing.JScrollPane scroll_stats_panel;
    private javax.swing.JScrollPane showdown_panel;
    private javax.swing.JTable showdown_table;
    private javax.swing.JComboBox<String> stats_combo;
    private javax.swing.JPanel stats_panel;
    private javax.swing.JScrollPane table_panel;
    private javax.swing.JLabel title;
    // End of variables declaration//GEN-END:variables
}
