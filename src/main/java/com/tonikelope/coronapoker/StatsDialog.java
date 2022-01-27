/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Dialog;
import java.awt.Font;
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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class StatsDialog extends javax.swing.JDialog {

    private final HashMap<String, HashMap<String, Object>> game = new HashMap<>();
    private final HashMap<String, HashMap<String, Object>> hand = new HashMap<>();
    private final LinkedHashMap<String, SQLStats> sqlstats = new LinkedHashMap<>();
    private volatile boolean init = false;
    private volatile String last_mp3_loop = null;
    private volatile boolean game_combo_blocked = false;
    private volatile boolean hand_combo_blocked = false;
    private volatile boolean backup = false;
    private volatile Font original_dialog_font;
    private volatile int last_button = 0;

    /**
     * Creates new form Stats
     */
    public StatsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        init = true;

        sqlstats.put(Translator.translate("GANANCIAS/PÉRDIDAS"), this::balance);
        sqlstats.put(Translator.translate("TIEMPO MEDIO DE RESPUESTA"), this::tiempoMedioRespuesta);
        sqlstats.put(Translator.translate("JUGADAS GANADORAS"), this::mejoresJugadas);
        sqlstats.put(Translator.translate("RENDIMIENTO DE LOS JUGADORES"), this::rendimiento);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL PREFLOP"), this::subidasPreflop);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL FLOP"), this::subidasFlop);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL TURN"), this::subidasTurn);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL RIVER"), this::subidasRiver);

        initComponents();

        game_textarea.setEditorKit(new CoronaHTMLEditorKit());
        game_textarea.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {

                Helpers.openBrowserURL(e.getURL().toString());
            }
        });

        scroll_stats_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_stats_panel.getHorizontalScrollBar().setUnitIncrement(16);
        res_table_warning.setVisible(false);
        showdown_panel.setVisible(false);
        game_textarea_scrollpane.setVisible(false);
        Helpers.JTextFieldRegularPopupMenu.addTo(game_textarea);
        table_panel.setVisible(false);
        hand_data_panel.setVisible(false);
        hand_combo.addItem(Translator.translate("TODAS LAS MANOS"));
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

        original_dialog_font = res_table.getFont();
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        res_table.setFont(original_dialog_font);
        showdown_table.setFont(original_dialog_font);
        hand_comcards_val.setFont(original_dialog_font);
        game_textarea.setFont(original_dialog_font);
        Helpers.translateComponents(this, false);
        setTitle(Translator.translate(getTitle()));
        stats_combo.setSelectedIndex(-1);

        Helpers.setResourceIconLabel(title, getClass().getResource("/images/stats.png"), title.getHeight(), title.getHeight());

        cargando.setIndeterminate(true);

        cargando.setVisible(false);

        purge_games_button.setEnabled(game_combo_filter.getBackground() == Color.YELLOW);

        Helpers.threadRun(new Runnable() {

            public void run() {
                loadGames();
            }
        });

        init = false;
    }

    private void mejoresJugadas() {

        cargando.setVisible(true);
        setEnabled(false);

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        hand_combo.setVisible(false);

        Helpers.threadRun(new Runnable() {

            public void run() {

                ResultSet rs;

                if (game_combo.getSelectedIndex() > 0) {

                    try {
                        String sql = "select player as JUGADOR, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val as JUGADA, hand.counter as MANO, round(showdown.profit,1) as BENEFICIO from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 and game.id=? order by hand_val DESC,BENEFICIO DESC;";

                        PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                        mejoresJugadasResult(rs);

                        statement.close();

                        Helpers.GUIRunAndWait(new Runnable() {
                            public void run() {
                                res_table_warning.setVisible(false);
                            }
                        });

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else {

                    try {
                        String sql = "select player as JUGADOR, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val as JUGADA, (game.server || '|' || game.start) as TIMBA, hand.counter as MANO, round(showdown.profit,1) as BENEFICIO from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 order by hand_val DESC,BENEFICIO DESC; LIMIT 1000";
                        Statement statement = Helpers.getSQLITE().createStatement();

                        statement.setQueryTimeout(30);

                        rs = statement.executeQuery(sql);

                        mejoresJugadasResult(rs);

                        statement.close();

                        Helpers.GUIRunAndWait(new Runnable() {
                            public void run() {
                                res_table_warning.setText(Translator.translate("Nota: se muestran las 1000 mejores jugadas ganadoras"));
                                res_table_warning.setVisible(true);
                            }
                        });

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                    }
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
                tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex).replace("_", " ")));
            }

            Object[] row = new Object[columnCount];

            while (rs.next()) {

                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);

                    if (tableModel.getColumnName(i).equals(Translator.translate("TIMBA"))) {
                        String timestamp = rs.getString("TIMBA").replaceAll("^.+\\|([0-9]+)$", "$1");
                        String server = rs.getString("TIMBA").replaceAll("^(.+)\\|[0-9]+$", "$1");
                        Timestamp ts = new Timestamp(Long.parseLong(timestamp));
                        DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                        Date date = new Date(ts.getTime());
                        row[i] = server + " @ " + timeZoneFormat.format(date);
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS RECIBIDAS"))) {
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

                    } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS JUGADA"))) {

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
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("JUGADA"))) {
                        row[i] = (int) row[i] - 1 >= 0 ? Hand.NOMBRES_JUGADAS[(int) row[i] - 1] : "-----";
                    }
                }

                tableModel.addRow(row);
            }

            Helpers.GUIRunAndWait(new Runnable() {

                public void run() {
                    res_table.setModel(tableModel);
                    TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());
                    Helpers.disableSortAllColumns(res_table, tableRowSorter);
                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADOR")), true);
                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANO")), true);
                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANO")), (Comparator<Integer>) (o1, o2) -> o1.compareTo(o2));
                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("BENEFICIO")), true);
                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));
                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADA")), true);
                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADA")), (Comparator<String>) (o1, o2) -> Integer.compare(Hand.handNAME2HandVal(o1), Hand.handNAME2HandVal(o2)));
                    if (Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("TIMBA")) != -1) {

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("TIMBA")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("TIMBA")), (Comparator<String>) (o1, o2) -> {
                            try {
                                return Long.compare(new java.sql.Timestamp(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(o1.split(" @ ")[1]).getTime()).getTime(), new java.sql.Timestamp(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(o2.split(" @ ")[1]).getTime()).getTime());
                            } catch (ParseException ex) {
                                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            return 0;
                        });
                    }
                    res_table.setRowSorter(tableRowSorter);
                    table_panel.setVisible(true);
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

        Helpers.threadRun(new Runnable() {

            public void run() {
                ResultSet rs = null;
                Statement st = null;

                if (game_combo.getSelectedIndex() > 0) {

                    try {

                        String sql = "select t1.JUGADOR, ROUND((JUGADAS/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS_JUGADAS, ROUND((COALESCE(GANADAS,0)/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS_GANADAS, CASE when JUGADAS>0 then ROUND((COALESCE(GANADAS,0)/CAST(JUGADAS AS FLOAT))*100,1)||'%' else '0.0%' end AS PRECISIÓN, roi||'%' AS ROI, case when JUGADAS>0 then (case when roi>=0 then round(((roi/100) / (JUGADAS/CAST(MANOS_TOTALES AS FLOAT))),2) else round(((roi/100) * (JUGADAS/CAST(MANOS_TOTALES AS FLOAT))),2) end) else 0.0 end as EFECTIVIDAD from (select action.player as JUGADOR, coalesce(tb.JUGADAS,0) as JUGADAS from action,hand left join (select player,count(distinct id_hand) as JUGADAS from action,hand where action.id_hand=hand.id and hand.id_game=? and action>=2 and round=1 group by player) as tb on action.player=tb.player where action.id_hand=hand.id and hand.id_game=? group by action.player) t1 left join (select showdown.player as JUGADOR, coalesce(tc.GANADAS,0) as GANADAS from showdown,hand left join (select player,count(distinct id_hand) as GANADAS from showdown,hand where showdown.id_hand=hand.id and hand.id_game=? and winner=1 group by player) as tc on showdown.player=tc.player where showdown.id_hand=hand.id and hand.id_game=? group by showdown.player) t2 on t2.JUGADOR=t1.JUGADOR left join (select player as JUGADOR, count(distinct id_hand) as MANOS_TOTALES from action,hand where action.id_hand=hand.id and hand.id_game=? group by JUGADOR) t3 on t3.JUGADOR=t1.JUGADOR left join (SELECT player AS JUGADOR, ROUND((SUM(stack-buyin)/SUM(buyin))*100,0) as roi from balance,hand WHERE balance.id_hand=hand.id and id_hand IN (SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?) GROUP BY JUGADOR ) t4 on t4.JUGADOR=t1.JUGADOR group by t1.JUGADOR order by EFECTIVIDAD DESC";

                        st = Helpers.getSQLITE().prepareStatement(sql);

                        PreparedStatement statement = (PreparedStatement) st;

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));
                        statement.setInt(2, (int) game.get((String) game_combo.getSelectedItem()).get("id"));
                        statement.setInt(3, (int) game.get((String) game_combo.getSelectedItem()).get("id"));
                        statement.setInt(4, (int) game.get((String) game_combo.getSelectedItem()).get("id"));
                        statement.setInt(5, (int) game.get((String) game_combo.getSelectedItem()).get("id"));
                        statement.setInt(6, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else {

                    try {

                        String sql = "select t1.JUGADOR, ROUND((JUGADAS/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS_JUGADAS, ROUND((COALESCE(GANADAS,0)/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS_GANADAS, CASE when JUGADAS>0 then ROUND((COALESCE(GANADAS,0)/CAST(JUGADAS AS FLOAT))*100,1)||'%' else '0.0%' end AS PRECISIÓN, roi||'%' AS ROI, case when JUGADAS>0 then (case when roi>=0 then round(((roi/100) / (JUGADAS/CAST(MANOS_TOTALES AS FLOAT))),2) else round(((roi/100) * (JUGADAS/CAST(MANOS_TOTALES AS FLOAT))),2) end) else 0.0 end as EFECTIVIDAD from (select action.player as JUGADOR, coalesce(tb.JUGADAS,0) as JUGADAS from action left join (select player,count(distinct id_hand) as JUGADAS from action where action>=2 and round=1 group by player) as tb on action.player=tb.player group by action.player) t1 left join (select showdown.player as JUGADOR, coalesce(tc.GANADAS,0) as GANADAS from showdown left join (select player,count(distinct id_hand) as GANADAS from showdown where winner=1 group by player) as tc on showdown.player=tc.player group by showdown.player) t2 on t2.JUGADOR=t1.JUGADOR left join (select player as JUGADOR, count(distinct id_hand) as MANOS_TOTALES from action group by JUGADOR) t3 on t3.JUGADOR=t1.JUGADOR left join (SELECT player AS JUGADOR, ROUND((SUM(stack-buyin)/SUM(buyin))*100,0) as roi from balance,hand WHERE balance.id_hand=hand.id and id_hand IN (SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand group by id_game) GROUP BY JUGADOR ) t4 on t4.JUGADOR=t1.JUGADOR group by t1.JUGADOR order by EFECTIVIDAD DESC";

                        st = Helpers.getSQLITE().createStatement();

                        st.setQueryTimeout(30);

                        rs = st.executeQuery(sql);

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }

                try {
                    Helpers.resultSetToTableModel(rs, res_table);

                    st.close();
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

                        Helpers.disableSortAllColumns(res_table, tableRowSorter);

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADOR")), true);

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("EFECTIVIDAD")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("EFECTIVIDAD")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS JUGADAS")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS JUGADAS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS GANADAS")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS GANADAS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("PRECISIÓN")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("PRECISIÓN")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ROI")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ROI")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                        res_table.setRowSorter(tableRowSorter);

                        table_panel.setVisible(true);

                        res_table_warning.setText(Translator.translate("Nota: EFECTIVIDAD = (ROI / MANOS_JUGADAS) si ROI >=0, si no, EFECTIVIDAD = (ROI x MANOS_JUGADAS) (la EFECTIVIDAD mínima es -1)"));

                        res_table_warning.setVisible(true);
                        cargando.setVisible(false);
                        setEnabled(true);
                    }
                });

            }
        });

    }

    private void subidasRonda(int ronda) {

        cargando.setVisible(true);
        setEnabled(false);

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        hand_combo.setVisible(false);

        Helpers.threadRun(new Runnable() {

            public void run() {

                ResultSet rs = null;

                PreparedStatement statement = null;

                if (game_combo.getSelectedIndex() > 0) {

                    try {

                        String sql = "select t1.JUGADOR, ROUND((JUGADAS/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS from (select action.player as JUGADOR, coalesce(tb.JUGADAS,0) as JUGADAS from action,hand left join (select player,count(distinct id_hand) as JUGADAS from action,hand where action.id_hand=hand.id and round=? and hand.id_game=? and action>=3 group by player) as tb on action.player=tb.player where action.id_hand=hand.id and hand.id_game=? group by action.player) t1 left join (select player as JUGADOR, count(distinct id_hand) as MANOS_TOTALES from action,hand where action.id_hand=hand.id and action>=2 and round=? and hand.id_game=? group by JUGADOR) t2 on t2.JUGADOR=t1.JUGADOR group by t1.JUGADOR order by MANOS DESC";

                        statement = Helpers.getSQLITE().prepareStatement(sql);

                        statement.setQueryTimeout(30);

                        statement.setInt(1, ronda);

                        statement.setInt(2, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        statement.setInt(3, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        statement.setInt(4, ronda);

                        statement.setInt(5, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else {
                    try {

                        String sql = "select t1.JUGADOR, ROUND((JUGADAS/CAST(MANOS_TOTALES AS FLOAT))*100,1)||'%' AS MANOS from (select action.player as JUGADOR, coalesce(tb.JUGADAS,0) as JUGADAS from action left join (select player,count(distinct id_hand) as JUGADAS from action where round=? and action>=3 group by player) as tb on action.player=tb.player group by action.player) t1 left join (select player as JUGADOR, count(distinct id_hand) as MANOS_TOTALES from action WHERE action>=2 and round=? group by JUGADOR) t2 on t2.JUGADOR=t1.JUGADOR group by t1.JUGADOR order by MANOS DESC";

                        statement = Helpers.getSQLITE().prepareStatement(sql);

                        statement.setQueryTimeout(30);

                        statement.setInt(1, ronda);

                        statement.setInt(2, ronda);

                        rs = statement.executeQuery();

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                try {
                    Helpers.resultSetToTableModel(rs, res_table);
                    statement.close();
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

                        Helpers.disableSortAllColumns(res_table, tableRowSorter);

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADOR")), true);

                        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS")), true);

                        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                        res_table.setRowSorter(tableRowSorter);
                        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS")));
                        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("MANOS")));
                        table_panel.setVisible(true);

                        res_table_warning.setText(Translator.translate("Nota: lo que se muestra es el porcentaje de manos subidas en relación a las manos jugadas."));

                        res_table_warning.setVisible(true);

                        cargando.setVisible(false);
                        setEnabled(true);
                    }
                });

            }
        });

    }

    private void subidasPreflop() {

        this.subidasRonda(Crupier.PREFLOP);

    }

    private void subidasFlop() {

        this.subidasRonda(Crupier.FLOP);

    }

    private void subidasTurn() {

        this.subidasRonda(Crupier.TURN);

    }

    private void subidasRiver() {

        this.subidasRonda(Crupier.RIVER);

    }

    private void balance() {

        cargando.setVisible(true);
        setEnabled(false);

        if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {

            hand_combo.setVisible(true);
            hand_combo.setSelectedIndex(0);
        }

        Helpers.threadRun(new Runnable() {

            public void run() {

                try {

                    ResultSet rs;
                    Statement st = null;

                    if (hand_combo.getSelectedIndex() > 0) {

                        String sql = "SELECT player as JUGADOR, ROUND(stack, 1) as STACK, buyin as BUYIN, ROUND(stack-buyin,1) as BENEFICIO, ROUND(((stack-buyin)/(buyin))*100,0) as ROI FROM balance WHERE id_hand=? GROUP BY JUGADOR ORDER BY ROI DESC";

                        st = Helpers.getSQLITE().prepareStatement(sql);

                        PreparedStatement statement = (PreparedStatement) st;

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                        if (hand_combo.isVisible() && hand_combo.getSelectedIndex() > 0) {
                            Helpers.GUIRunAndWait(new Runnable() {
                                public void run() {
                                    res_table_warning.setText(Translator.translate("Nota: lo que se muestra es el balance general después de terminar la mano actual."));

                                    res_table_warning.setVisible(true);
                                }
                            });
                        }

                    } else if (game_combo.getSelectedIndex() > 0) {

                        String sql = "SELECT player as JUGADOR, ROUND(stack,1) AS STACK, buyin AS BUYIN, ROUND(stack-buyin,1) AS BENEFICIO, ROUND(((stack-buyin)/(buyin))*100,0) as ROI FROM balance,hand WHERE balance.id_hand=hand.id AND hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?) GROUP BY JUGADOR ORDER BY ROI DESC";

                        st = Helpers.getSQLITE().prepareStatement(sql);

                        PreparedStatement statement = (PreparedStatement) st;

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        statement.setInt(2, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                    } else {
                        String sql = "SELECT player AS JUGADOR, ROUND(SUM(stack),1) AS STACK, SUM(buyin) AS BUYIN, ROUND(SUM(stack-buyin),1) AS BENEFICIO, ROUND((SUM(stack-buyin)/SUM(buyin))*100,0) as ROI from balance WHERE id_hand IN (SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand group by id_game) GROUP BY JUGADOR ORDER BY ROI DESC";

                        st = Helpers.getSQLITE().createStatement();

                        st.setQueryTimeout(30);

                        rs = st.executeQuery(sql);

                    }

                    try {
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

                                if (tableModel.getColumnName(i).equals(Translator.translate("ROI"))) {
                                    row[i] = String.valueOf(rs.getFloat("ROI")) + "%";
                                }
                            }

                            tableModel.addRow(row);
                        }

                        Helpers.GUIRunAndWait(new Runnable() {
                            public void run() {
                                res_table.setModel(tableModel);

                                TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

                                Helpers.disableSortAllColumns(res_table, tableRowSorter);

                                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADOR")), true);

                                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("BUYIN")), true);

                                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("STACK")), true);

                                tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("STACK")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("BENEFICIO")), true);

                                tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ROI")), true);

                                tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("ROI")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

                                res_table.setRowSorter(tableRowSorter);

                                table_panel.setVisible(true);
                            }
                        });

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    st.close();

                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                    }
                });

            }
        });

    }

    private void loadGameData(int id) {

        cargando.setVisible(true);

        setEnabled(false);

        game_combo_blocked = true;

        Helpers.threadRun(new Runnable() {

            public void run() {

                try {
                    String sql = "SELECT *, (SELECT COUNT(*) from hand where id_game=? AND end IS NOT NULL) as tot_hands FROM game WHERE id=?";

                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                    statement.setQueryTimeout(30);

                    statement.setInt(1, id);

                    statement.setInt(2, id);

                    ResultSet rs = statement.executeQuery();

                    Helpers.GUIRunAndWait(new Runnable() {
                        public void run() {

                            try {

                                game_textarea_scrollpane.setVisible(false);

                                String item = (String) game_combo.getSelectedItem();

                                String[] parts = item.split("@");

                                String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");

                                log_game_button.setEnabled(Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + parts[0].trim() + "_" + fecha + ".log")));

                                chat_game_button.setEnabled(Files.isReadable(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + parts[0].trim() + "_" + fecha + ".log")) && Files.size(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + parts[0].trim() + "_" + fecha + ".log")) > 0L);

                                game_playtime_val.setText((rs.getObject("end") != null ? Helpers.seconds2FullTime((rs.getLong("end") / 1000 - rs.getLong("start") / 1000)) : "--:--:--") + " (" + Helpers.seconds2FullTime(rs.getLong("play_time")) + ")");

                                String[] jugadores;

                                jugadores = rs.getString("players").split("#");

                                String players = "";

                                for (String j : jugadores) {

                                    players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";

                                }

                                game_players_val.setText(players.replaceAll("  \\|  $", ""));

                                game_buyin_val.setText(String.valueOf(rs.getInt("buyin")));

                                game_hand_val.setText(String.valueOf(rs.getInt("tot_hands")));

                                game_blinds_val.setText(String.valueOf(rs.getFloat("sb")) + " / " + String.valueOf(rs.getFloat("sb") * 2));

                                game_blinds_double_val.setText(rs.getInt("blinds_time") != -1 ? String.valueOf(rs.getInt("blinds_time")) + (rs.getInt("blinds_time_type") <= 1 ? " min" : " *") : "NO");

                                game_rebuy_val.setText(rs.getBoolean("rebuy") ? Translator.translate("SÍ") : "NO");

                            } catch (Exception ex) {
                                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    });

                    statement.close();
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                        game_data_panel.setVisible(true);
                        game_combo_blocked = false;
                        purge_games_button.setEnabled(game_combo_filter.getBackground() == Color.YELLOW);

                    }
                });

            }
        });

    }

    private void loadHandData(int id_game, int id_hand) {

        cargando.setVisible(true);
        setEnabled(false);

        Helpers.threadRun(new Runnable() {

            public void run() {
                try {
                    String sql = "SELECT * FROM hand WHERE id_game=? AND id=?";

                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                    statement.setQueryTimeout(30);

                    statement.setInt(1, id_game);

                    statement.setInt(2, id_hand);

                    ResultSet rs = statement.executeQuery();
                    Helpers.GUIRunAndWait(new Runnable() {
                        public void run() {
                            try {
                                String[] jugadores;

                                String players = "";

                                if (rs.getString("preflop_players") != null) {

                                    jugadores = rs.getString("preflop_players").split("#");

                                    for (String j : jugadores) {

                                        players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
                                    }

                                    hand_preflop_players_val.setText(players.replaceAll("  \\|  $", ""));
                                }

                                if (rs.getString("flop_players") != null) {
                                    jugadores = rs.getString("flop_players").split("#");

                                    players = "";

                                    for (String j : jugadores) {

                                        players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
                                    }

                                    hand_flop_players_val.setText(players.replaceAll("  \\|  $", ""));
                                }

                                if (rs.getString("turn_players") != null) {

                                    jugadores = rs.getString("turn_players").split("#");

                                    players = "";

                                    for (String j : jugadores) {

                                        players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
                                    }

                                    hand_turn_players_val.setText(players.replaceAll("  \\|  $", ""));
                                }

                                if (rs.getString("river_players") != null) {

                                    jugadores = rs.getString("river_players").split("#");

                                    players = "";

                                    for (String j : jugadores) {

                                        players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
                                    }

                                    hand_river_players_val.setText(players.replaceAll("  \\|  $", ""));
                                }

                                hand_blinds_val.setText(String.valueOf(rs.getFloat("sbval")) + " / " + String.valueOf(rs.getFloat("sbval") * 2) + " (" + String.valueOf(rs.getInt("blinds_double")) + ")");

                                hand_time_val.setText(Helpers.seconds2FullTime((rs.getLong("end") / 1000 - rs.getLong("start") / 1000)));
                                hand_cp_val.setText(rs.getString("sb"));
                                hand_cg_val.setText(rs.getString("bb"));

                                if (rs.getString("com_cards") != null) {

                                    ArrayList<Card> cartas = new ArrayList<>();

                                    for (String c : ((String) rs.getString("com_cards")).split("#")) {

                                        String[] partes = c.split("_");

                                        Card carta = new Card(false);

                                        carta.actualizarValorPalo(partes[0], partes[1]);

                                        cartas.add(carta);
                                    }

                                    hand_comcards_val.setText(Card.collection2String(cartas));
                                }

                                hand_bote_val.setText(String.valueOf(Helpers.floatClean(rs.getFloat("pot"))));

                                loadShowdownData(id_hand);
                            } catch (Exception ex) {
                                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        }
                    });

                    statement.close();

                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                        hand_data_panel.setVisible(true);
                    }
                });

            }
        });

    }

    private void loadShowdownData(int id_hand) {

        Helpers.threadRun(new Runnable() {

            public void run() {

                try {
                    ResultSet rs;
                    String sql = "SELECT player AS JUGADOR, winner as GANA, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val AS JUGADA, ROUND(pay,1) as PAGAR, ROUND(profit,1) as BENEFICIO FROM showdown WHERE id_hand=? order by GANA DESC,PAGAR DESC";

                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                    statement.setQueryTimeout(30);

                    statement.setInt(1, id_hand);

                    rs = statement.executeQuery();

                    showdownData(rs);

                    statement.close();

                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                    }
                });
            }
        });

    }

    private void showdownData(ResultSet rs) {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                try {

                    DefaultTableModel tableModel = new DefaultTableModel();

                    ResultSetMetaData metaData = rs.getMetaData();

                    int columnCount = metaData.getColumnCount();

                    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                        tableModel.addColumn(Translator.translate(metaData.getColumnLabel(columnIndex).replace("_", " ")));
                    }

                    Object[] row = new Object[columnCount];

                    while (rs.next()) {

                        for (int i = 0; i < columnCount; i++) {
                            row[i] = rs.getObject(i + 1);

                            if (tableModel.getColumnName(i).equals(Translator.translate("GANA"))) {
                                row[i] = (int) row[i] == 1 ? Translator.translate("SÍ") : "NO";
                            } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS RECIBIDAS"))) {

                                ArrayList<Card> cartas = new ArrayList<>();

                                if (row[i] != null) {
                                    for (String c : ((String) row[i]).split("#")) {

                                        String[] partes = c.split("_");

                                        Card carta = new Card(false);

                                        carta.actualizarValorPalo(partes[0], partes[1]);

                                        cartas.add(carta);
                                    }

                                    Card.sortCollection(cartas);
                                }

                                row[i] = row[i] != null ? Card.collection2String(cartas) : "*****";

                            } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS JUGADA"))) {

                                ArrayList<Card> cartas = new ArrayList<>();

                                if (row[i] != null) {
                                    for (String c : ((String) row[i]).split("#")) {

                                        String[] partes = c.split("_");

                                        Card carta = new Card(false);

                                        carta.actualizarValorPalo(partes[0], partes[1]);

                                        cartas.add(carta);
                                    }
                                }

                                row[i] = row[i] != null ? Card.collection2String(cartas) : "-----";
                            } else if (tableModel.getColumnName(i).equals(Translator.translate("JUGADA"))) {
                                row[i] = (int) row[i] - 1 >= 0 ? Hand.NOMBRES_JUGADAS[(int) row[i] - 1] : "-----";
                            }
                        }

                        tableModel.addRow(row);

                    }

                    showdown_table.setModel(tableModel);

                    TableRowSorter tableRowSorter = new TableRowSorter(showdown_table.getModel());

                    Helpers.disableSortAllColumns(res_table, tableRowSorter);

                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("JUGADOR")), true);

                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("GANA")), true);

                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("PAGAR")), true);

                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("PAGAR")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("BENEFICIO")), true);

                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                    tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("JUGADA")), true);

                    tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table.getModel(), Translator.translate("JUGADA")), (Comparator<String>) (o1, o2) -> Integer.compare(Hand.handNAME2HandVal(o1), Hand.handNAME2HandVal(o2)));

                    showdown_table.setRowSorter(tableRowSorter);

                    showdown_panel.setVisible(true);

                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        });

    }

    private void tiempoMedioRespuesta() {

        cargando.setVisible(true);
        setEnabled(false);
        if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {

            hand_combo.setVisible(true);
            hand_combo.setSelectedIndex(0);
        }

        Helpers.threadRun(new Runnable() {

            public void run() {

                try {

                    ResultSet rs;

                    Statement st = null;

                    if (hand_combo.getSelectedIndex() > 0) {

                        String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action WHERE id_hand=? GROUP BY JUGADOR order by TIEMPO DESC";

                        st = Helpers.getSQLITE().prepareStatement(sql);

                        PreparedStatement statement = (PreparedStatement) st;

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                    } else if (game_combo.getSelectedIndex() > 0) {

                        String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action,hand WHERE action.id_hand=hand.id AND hand.id_game=? GROUP BY JUGADOR order by TIEMPO DESC";

                        st = Helpers.getSQLITE().prepareStatement(sql);

                        PreparedStatement statement = (PreparedStatement) st;

                        statement.setQueryTimeout(30);

                        statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                        rs = statement.executeQuery();

                    } else {
                        String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action GROUP BY JUGADOR order by TIEMPO DESC";

                        st = Helpers.getSQLITE().createStatement();

                        st.setQueryTimeout(30);

                        rs = st.executeQuery(sql);
                    }

                    Helpers.resultSetToTableModel(rs, res_table);

                    Helpers.GUIRunAndWait(new Runnable() {
                        public void run() {

                            TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

                            Helpers.disableSortAllColumns(res_table, tableRowSorter);

                            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("JUGADOR")), true);

                            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("TIEMPO") + " " + Translator.translate("(SEGUNDOS)")), true);

                            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table.getModel(), Translator.translate("TIEMPO") + " " + Translator.translate("(SEGUNDOS)")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

                            res_table.setRowSorter(tableRowSorter);

                            table_panel.setVisible(true);
                        }
                    });

                    st.close();
                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                    }
                });

            }
        });

    }

    private void loadHands(int id) {

        cargando.setVisible(true);
        setEnabled(false);
        hand_combo_blocked = true;

        Helpers.threadRun(new Runnable() {

            public void run() {

                try {

                    String sql = "SELECT * FROM hand WHERE id_game=? AND end IS NOT NULL ORDER BY id DESC";

                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                    statement.setQueryTimeout(30);

                    statement.setInt(1, id);

                    ResultSet rs = statement.executeQuery();

                    Helpers.GUIRunAndWait(new Runnable() {
                        public void run() {

                            hand.clear();

                            hand_combo.removeAllItems();

                            hand_combo.addItem(Translator.translate("TODAS LAS MANOS"));

                            try {

                                while (rs.next()) {

                                    try {
                                        hand_combo.addItem(Translator.translate("MANO") + " " + String.valueOf(rs.getInt("counter")));

                                        HashMap<String, Object> map = new HashMap<>();

                                        map.put("id", rs.getInt("id"));

                                        hand.put(Translator.translate("MANO") + " " + String.valueOf(rs.getInt("counter")), map);

                                    } catch (Exception ex) {
                                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                }
                            } catch (Exception ex) {
                                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    });

                    statement.close();

                } catch (SQLException ex) {
                    Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {
                        cargando.setVisible(false);
                        setEnabled(true);
                        hand_combo_blocked = false;
                    }
                });

            }
        });

    }

    private boolean deleteAllGames() {

        if (game_combo.getItemCount() > 1) {

            String[] ids = new String[game_combo.getItemCount() - 1];

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    cargando.setVisible(true);

                    setEnabled(false);

                    for (int i = 1; i <= ids.length; i++) {

                        ids[i - 1] = String.valueOf((int) game.get((String) game_combo.getItemAt(i)).get("id"));

                    }

                    while (game_combo.getItemCount() > 1) {
                        game_combo.removeItemAt(game_combo.getItemCount() - 1);
                    }

                }
            });

            try {

                String sql = "DELETE FROM game WHERE id in (" + String.join(",", ids) + ")";

                Statement statement = Helpers.getSQLITE().createStatement();

                statement.setQueryTimeout(30);

                statement.executeUpdate(sql);

                statement.close();

            } catch (SQLException ex) {
                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    cargando.setVisible(false);

                    setEnabled(true);

                }
            });

            return true;

        }

        return false;
    }

    private boolean deleteSelectedGame() {

        if (game.get((String) game_combo.getSelectedItem()) != null) {

            int id_game = (int) game.get((String) game_combo.getSelectedItem()).get("id");

            game.remove((String) game_combo.getSelectedItem());

            try {

                String sql = "DELETE FROM game WHERE id=?";

                PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, id_game);

                statement.executeUpdate();

                statement.close();

            } catch (SQLException ex) {
                Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            return true;
        }

        return false;

    }

    private void loadGames() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                cargando.setVisible(true);
                setEnabled(false);
                game_combo_blocked = true;
            }
        });

        try {

            PreparedStatement statement;

            statement = Helpers.getSQLITE().prepareStatement("SELECT * FROM game ORDER BY start DESC");

            statement.setQueryTimeout(30);

            ResultSet rs = statement.executeQuery();

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    game.clear();

                    game_combo.removeAllItems();

                    game_combo.addItem(Translator.translate("TODAS LAS TIMBAS"));

                    String filtro = null;

                    if (!game_combo_filter.getText().isBlank() && game_combo_filter.getBackground() != Color.RED) {
                        filtro = game_combo_filter.getText().trim().toUpperCase();
                    }

                    try {
                        int i = 0;

                        while (rs.next()) {

                            boolean ok = false;

                            if (filtro != null) {

                                String players[] = rs.getString("players").split("#");

                                ArrayList<String> decoded_players = new ArrayList<>();

                                for (String p : players) {

                                    decoded_players.add(new String(Base64.decodeBase64(p), "UTF-8").trim().toUpperCase());
                                }

                                ok = decoded_players.contains(filtro);

                            } else {
                                ok = true;
                            }

                            if (ok) {

                                i++;
                                // read the result set

                                Timestamp ts = new Timestamp(rs.getLong("start"));
                                DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                                Date date = new Date(ts.getTime());

                                HashMap<String, Object> map = new HashMap<>();
                                map.put("id", rs.getInt("id"));
                                map.put("start_timestamp", rs.getLong("start"));

                                String game_length = Helpers.seconds2FullTime(rs.getLong("play_time"));
                                game.put(rs.getString("server") + " @ " + timeZoneFormat.format(date) + " @ " + game_length, map);
                                game_combo.addItem(rs.getString("server") + " @ " + timeZoneFormat.format(date) + " @ " + game_length);
                            }
                        }

                        if (filtro != null && i == 0) {

                            game_combo_filter.setBackground(Color.RED);

                        }

                        purge_games_button.setEnabled(game_combo_filter.getBackground() == Color.YELLOW);

                    } catch (SQLException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            });

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                cargando.setVisible(false);
                setEnabled(true);
                game_combo_blocked = false;
            }
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

        title.setBackground(new java.awt.Color(255, 153, 51));
        title.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        title.setForeground(new java.awt.Color(255, 255, 255));
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        title.setText("ESTADÍSTICAS");
        title.setDoubleBuffered(true);
        title.setFocusable(false);
        title.setOpaque(true);

        scroll_stats_panel.setBorder(null);
        scroll_stats_panel.setDoubleBuffered(true);

        game_combo.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_combo.setDoubleBuffered(true);
        game_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                game_comboItemStateChanged(evt);
            }
        });

        game_data_panel.setOpaque(false);

        game_hand_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_hand_label.setText("Manos:");
        game_hand_label.setDoubleBuffered(true);

        game_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_players_label.setText("Jugadores:");
        game_players_label.setDoubleBuffered(true);

        game_buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_buyin_label.setText("Compra:");
        game_buyin_label.setDoubleBuffered(true);

        game_blinds_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_blinds_label.setText("Ciegas:");
        game_blinds_label.setDoubleBuffered(true);

        game_blinds_double_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_blinds_double_label.setText("Aumentar ciegas:");
        game_blinds_double_label.setDoubleBuffered(true);

        game_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_rebuy_label.setText("Recomprar:");
        game_rebuy_label.setDoubleBuffered(true);

        game_hand_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_hand_val.setText(" ");
        game_hand_val.setDoubleBuffered(true);

        game_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_players_val.setText(" ");
        game_players_val.setDoubleBuffered(true);

        game_buyin_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_buyin_val.setText(" ");
        game_buyin_val.setDoubleBuffered(true);

        game_blinds_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_blinds_val.setText(" ");
        game_blinds_val.setDoubleBuffered(true);

        game_blinds_double_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_blinds_double_val.setText(" ");
        game_blinds_double_val.setDoubleBuffered(true);

        game_rebuy_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_rebuy_val.setText(" ");
        game_rebuy_val.setDoubleBuffered(true);

        game_playtime_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        game_playtime_label.setText("Duración:");
        game_playtime_label.setDoubleBuffered(true);

        game_playtime_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_playtime_val.setText(" ");
        game_playtime_val.setDoubleBuffered(true);

        delete_game_button.setBackground(new java.awt.Color(255, 0, 0));
        delete_game_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        delete_game_button.setForeground(new java.awt.Color(255, 255, 255));
        delete_game_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/remove.png"))); // NOI18N
        delete_game_button.setText("ELIMINAR TIMBA");
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
        log_game_button.setText("REGISTRO DE LA TIMBA");
        log_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        log_game_button.setDoubleBuffered(true);
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
        chat_game_button.setText("CHAT DE LA TIMBA");
        chat_game_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat_game_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_game_buttonActionPerformed(evt);
            }
        });

        game_textarea_scrollpane.setDoubleBuffered(true);

        game_textarea.setEditable(false);
        game_textarea.setBorder(null);
        game_textarea.setDoubleBuffered(true);
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
                            .addComponent(game_blinds_double_val, javax.swing.GroupLayout.DEFAULT_SIZE, 1003, Short.MAX_VALUE)
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
        game_combo_filter.setToolTipText("Listar sólo timbas donde participó este jugador");
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
        purge_games_button.setText("PURGAR");
        purge_games_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        purge_games_button.setDoubleBuffered(true);
        purge_games_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                purge_games_button_buttonActionPerformed(evt);
            }
        });

        hands_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        hand_combo.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_combo.setDoubleBuffered(true);
        hand_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                hand_comboItemStateChanged(evt);
            }
        });

        hand_data_panel.setOpaque(false);

        hand_blinds_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_blinds_label.setText("Ciegas:");
        hand_blinds_label.setDoubleBuffered(true);
        hand_blinds_label.setPreferredSize(new java.awt.Dimension(45, 17));

        hand_blinds_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_blinds_val.setText(" ");
        hand_blinds_val.setDoubleBuffered(true);

        hand_time_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_time_label.setText("Duración:");
        hand_time_label.setDoubleBuffered(true);

        hand_cp_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_cp_label.setText("Ciega pequeña:");
        hand_cp_label.setDoubleBuffered(true);

        hand_cg_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_cg_label.setText("Ciega grande:");
        hand_cg_label.setDoubleBuffered(true);

        hand_comcards_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_comcards_label.setText("Cartas comunitarias:");
        hand_comcards_label.setDoubleBuffered(true);

        hand_preflop_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_preflop_players_label.setText("Jugadores PREFLOP:");
        hand_preflop_players_label.setDoubleBuffered(true);

        hand_flop_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_flop_players_label.setText("Jugadores FLOP:");
        hand_flop_players_label.setDoubleBuffered(true);

        hand_turn_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_turn_players_label.setText("Jugadores TURN:");
        hand_turn_players_label.setDoubleBuffered(true);

        hand_river_players_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_river_players_label.setText("Jugadores RIVER:");
        hand_river_players_label.setDoubleBuffered(true);

        hand_bote_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        hand_bote_label.setText("BOTE:");
        hand_bote_label.setDoubleBuffered(true);

        hand_time_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_time_val.setText(" ");
        hand_time_val.setDoubleBuffered(true);

        hand_cp_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_cp_val.setText(" ");
        hand_cp_val.setDoubleBuffered(true);

        hand_cg_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_cg_val.setText(" ");
        hand_cg_val.setDoubleBuffered(true);

        hand_comcards_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_comcards_val.setText(" ");
        hand_comcards_val.setDoubleBuffered(true);

        hand_preflop_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_preflop_players_val.setText(" ");
        hand_preflop_players_val.setDoubleBuffered(true);

        hand_flop_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_flop_players_val.setText(" ");
        hand_flop_players_val.setDoubleBuffered(true);

        hand_turn_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_turn_players_val.setText(" ");
        hand_turn_players_val.setDoubleBuffered(true);

        hand_river_players_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_river_players_val.setText(" ");
        hand_river_players_val.setDoubleBuffered(true);

        hand_bote_val.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        hand_bote_val.setText(" ");
        hand_bote_val.setDoubleBuffered(true);

        showdown_panel.setDoubleBuffered(true);

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
                    .addComponent(showdown_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 1138, Short.MAX_VALUE)
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
        stats_combo.setDoubleBuffered(true);
        stats_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                stats_comboItemStateChanged(evt);
            }
        });

        table_panel.setDoubleBuffered(true);

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
        res_table_warning.setText("Nota:");
        res_table_warning.setDoubleBuffered(true);

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
        // TODO add your handling code here:

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
                    sqlstats.get((String) stats_combo.getSelectedItem()).call();

                }

            } else {

                hand_combo.setVisible(false);
                hand_data_panel.setVisible(false);
                game_data_panel.setVisible(false);
                stats_combo.setVisible(false);
                table_panel.setVisible(false);
                res_table_warning.setVisible(false);
            }
        }
    }//GEN-LAST:event_game_comboItemStateChanged

    private void stats_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_stats_comboItemStateChanged
        // TODO add your handling code here:

        if (!init && stats_combo.getSelectedIndex() != -1) {
            res_table_warning.setVisible(false);
            res_table.setRowSorter(null);
            sqlstats.get((String) stats_combo.getSelectedItem()).call();
        }
    }//GEN-LAST:event_stats_comboItemStateChanged

    private void hand_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_hand_comboItemStateChanged
        // TODO add your handling code here:

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
        Audio.stopLoopMp3("misc/stats_music.mp3");

        if (last_mp3_loop != null) {
            Audio.unmuteLoopMp3(last_mp3_loop);
        }
    }//GEN-LAST:event_formWindowClosed

    private void delete_game_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_delete_game_buttonActionPerformed
        // TODO add your handling code here:

        delete_game_button.setEnabled(false);

        if (Helpers.mostrarMensajeInformativoSINO((JFrame) this.getParent(), "¿ELIMINAR ESTA TIMBA?\n(Nota: las timbas eliminadas no se pueden continuar)") == 0) {

            Audio.playWavResource("misc/toilet.wav");

            Helpers.threadRun(new Runnable() {

                public void run() {

                    if (!backup) {

                        try {
                            Files.copy(Paths.get(Init.SQL_FILE), Paths.get(Init.SQL_FILE + "_" + String.valueOf(System.currentTimeMillis()) + ".bak"));
                            backup = true;
                        } catch (IOException ex) {
                            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (deleteSelectedGame()) {

                        Helpers.GUIRun(new Runnable() {

                            public void run() {

                                game_data_panel.setVisible(false);
                            }
                        });

                        loadGames();

                        Helpers.GUIRun(new Runnable() {

                            public void run() {

                                if (!game.isEmpty()) {
                                    game_combo.setSelectedIndex(1);
                                }

                                delete_game_button.setEnabled(true);
                            }
                        });
                    }
                }
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

        Helpers.threadRun(new Runnable() {

            public void run() {
                loadGames();

                Helpers.GUIRunAndWait(new Runnable() {

                    public void run() {
                        game_combo.setSelectedIndex(0);
                        hand_combo.setVisible(false);
                        stats_combo.setSelectedIndex(-1);
                        table_panel.setVisible(false);
                        res_table_warning.setVisible(false);
                        game_data_panel.setVisible(false);
                    }
                });

                if (!game_combo_filter.getText().isBlank() && game_combo.getItemCount() == 1) {
                    loadGames();

                    Helpers.GUIRunAndWait(new Runnable() {

                        public void run() {
                            game_combo.setSelectedIndex(0);
                            hand_combo.setVisible(false);
                            stats_combo.setSelectedIndex(-1);
                            table_panel.setVisible(false);
                            res_table_warning.setVisible(false);
                            game_data_panel.setVisible(false);
                        }
                    });

                    Helpers.mostrarMensajeError((JFrame) getParent(), "NO HAY TIMBAS EN LAS CUALES HAYA PARTICIPADO ESE JUGADOR");
                }

            }
        });

    }//GEN-LAST:event_game_combo_filterActionPerformed

    private void log_game_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_log_game_buttonActionPerformed
        // TODO add your handling code here:

        if (game_textarea_scrollpane.isVisible() && last_button == 1) {
            game_textarea_scrollpane.setVisible(false);
        } else {

            last_button = 1;

            cargando.setVisible(true);

            String item = (String) game_combo.getSelectedItem();

            String[] parts = item.split("@");

            String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");

            Dialog tthis = this;
            Helpers.threadRun(new Runnable() {

                public void run() {
                    try {

                        String log = Files.readString(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + parts[0].trim() + "_" + fecha + ".log"), StandardCharsets.UTF_8).replaceAll("[*]{15} [^*]+ [*]{15}", "<b>$0</b>").replaceAll("\n", "<br>");

                        Helpers.GUIRun(new Runnable() {

                            public void run() {
                                game_textarea.setText("<html><body style='color:white;background-color:rgb(102,102,102)'>" + log + "</body></html>");

                                game_textarea_scrollpane.setVisible(true);

                                game_textarea.setCaretPosition(0);

                                cargando.setVisible(false);
                            }
                        });

                    } catch (IOException ex) {
                        Helpers.mostrarMensajeError(tthis, Init.LOGS_DIR + "/CORONAPOKER_TIMBA_" + parts[0].trim() + "_" + fecha + ".log");
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                        Helpers.GUIRun(new Runnable() {

                            public void run() {
                                cargando.setVisible(false);
                            }
                        });
                    }

                }
            });
        }

        game_data_panel.revalidate();

        game_data_panel.repaint();
    }//GEN-LAST:event_log_game_buttonActionPerformed

    private void purge_games_button_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_purge_games_button_buttonActionPerformed
        // TODO add your handling code here:
        purge_games_button.setEnabled(false);

        if (Helpers.mostrarMensajeInformativoSINO((JFrame) this.getParent(), Translator.translate("¿ELIMINAR TODAS LAS TIMBAS DONDE PARTICIPÓ ESE JUGADOR?\n(Nota: las timbas eliminadas no se pueden continuar)")) == 0) {
            Audio.playWavResource("misc/toilet.wav");

            Helpers.threadRun(new Runnable() {

                public void run() {

                    if (!backup) {

                        try {
                            Files.copy(Paths.get(Init.SQL_FILE), Paths.get(Init.SQL_FILE + "_" + String.valueOf(System.currentTimeMillis()) + ".bak"));
                            backup = true;
                        } catch (IOException ex) {
                            Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    if (deleteAllGames()) {

                        Helpers.GUIRunAndWait(new Runnable() {

                            public void run() {
                                game_combo_filter.setBackground(Color.BLACK);
                                game_combo_filter.setForeground(Color.WHITE);
                            }
                        });

                        Helpers.mostrarMensajeInformativo((JFrame) getParent(), "SE HAN BORRADO TODAS LAS TIMBAS DONDE PARTICIPÓ ESE JUGADOR");

                        Helpers.GUIRunAndWait(new Runnable() {

                            public void run() {
                                game_combo_filter.setText("");
                                game_combo_filter.setBackground(null);
                                game_combo_filter.setForeground(null);
                                game_data_panel.setVisible(false);
                            }
                        });

                        loadGames();

                        Helpers.GUIRun(new Runnable() {

                            public void run() {

                                game_combo.setSelectedIndex(0);

                                delete_game_button.setEnabled(true);
                            }
                        });
                    }
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
        } else {

            last_button = 2;

            cargando.setVisible(true);

            String item = (String) game_combo.getSelectedItem();

            String[] parts = item.split("@");

            String fecha = parts[1].trim().replaceAll("-", "_").replaceAll(" ", "__").replaceAll(":", "_");

            String avatar_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();

            Dialog tthis = this;

            Helpers.threadRun(new Runnable() {

                public void run() {
                    try {

                        String log = Files.readString(Paths.get(Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + parts[0].trim() + "_" + fecha + ".log"), StandardCharsets.UTF_8).replaceAll("<img[^<>]+avatar[^<>]+>", "<img src='" + avatar_src + "' />");

                        Helpers.GUIRun(new Runnable() {

                            public void run() {
                                game_textarea.setText(log);

                                game_textarea_scrollpane.setVisible(true);

                                game_textarea.setCaretPosition(0);

                                cargando.setVisible(false);
                            }
                        });

                    } catch (IOException ex) {
                        Helpers.mostrarMensajeError(tthis, Init.LOGS_DIR + "/CORONAPOKER_CHAT_" + parts[0].trim() + "_" + fecha + ".log");
                        Logger.getLogger(StatsDialog.class.getName()).log(Level.SEVERE, null, ex);
                        Helpers.GUIRun(new Runnable() {

                            public void run() {

                                cargando.setVisible(false);
                            }
                        });
                    }

                }
            });
        }

        game_data_panel.revalidate();

        game_data_panel.repaint();
    }//GEN-LAST:event_chat_game_buttonActionPerformed

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }
    }//GEN-LAST:event_formWindowActivated

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
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
