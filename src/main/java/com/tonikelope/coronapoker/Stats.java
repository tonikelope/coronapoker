/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.Init.SQLITE;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class Stats extends javax.swing.JDialog {

    private final HashMap<String, HashMap<String, Object>> game = new HashMap<>();
    private final HashMap<String, HashMap<String, Object>> hand = new HashMap<>();
    private final LinkedHashMap<String, SQLStats> sqlstats = new LinkedHashMap<>();
    private volatile boolean init = false;
    private volatile String last_mp3_loop = null;

    /**
     * Creates new form Stats
     */
    public Stats(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        init = true;

        Stats tthis = this;

        sqlstats.put(Translator.translate("GANANCIAS/PÉRDIDAS"), this::balance);
        sqlstats.put(Translator.translate("TIEMPO MEDIO DE RESPUESTA (SEGUNDOS)"), this::tiempoMedioRespuesta);
        sqlstats.put(Translator.translate("JUGADAS GANADORAS"), this::mejoresJugadas);
        sqlstats.put(Translator.translate("% MANOS JUGADAS/GANADAS"), this::manosJugadas);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL PREFLOP"), this::subidasPreflop);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL FLOP"), this::subidasFlop);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL TURN"), this::subidasTurn);
        sqlstats.put(Translator.translate("% APUESTAS/SUBIDAS EN EL RIVER"), this::subidasRiver);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                scroll_stats_panel.getVerticalScrollBar().setUnitIncrement(16);
                scroll_stats_panel.getHorizontalScrollBar().setUnitIncrement(16);
                res_table_warning.setVisible(false);
                showdown_panel.setVisible(false);
                table_panel.setVisible(false);
                hand_data_panel.setVisible(false);
                hand_combo.addItem(Translator.translate("TODAS LAS MANOS"));
                hand_combo.setVisible(false);
                game_combo.addItem(Translator.translate("TODAS LAS TIMBAS"));
                game_combo.setSelectedIndex(0);
                game_data_panel.setVisible(false);
                res_table.setRowHeight(res_table.getRowHeight() + 20);
                showdown_table.setRowHeight(showdown_table.getRowHeight() + 20);

                for (Map.Entry<String, SQLStats> entry : sqlstats.entrySet()) {

                    stats_combo.setSelectedIndex(-1);
                    stats_combo.addItem(entry.getKey());
                }

                Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);
                Helpers.translateComponents(tthis, false);
                setTitle(Translator.translate(getTitle()));

                loadGames();

                init = false;

                stats_combo.setSelectedIndex(0);

                pack();

            }
        });

    }

    private void mejoresJugadas() {

        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }
        ResultSet rs;

        if (game_combo.getSelectedIndex() > 0) {

            try {
                String sql = "select player as JUGADOR, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val as JUGADA, hand.counter as MANO, round(showdown.profit,1) as BENEFICIO from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 and game.id=? order by hand_val DESC,BENEFICIO DESC;";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                mejoresJugadas(rs);
                res_table_warning.setVisible(false);

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {

            try {
                String sql = "select player as JUGADOR, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val as JUGADA, game.start as TIMBA, hand.counter as MANO, round(showdown.profit,1) as BENEFICIO from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and showdown.winner=1 order by hand_val DESC,BENEFICIO DESC; LIMIT 1000";
                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
                mejoresJugadas(rs);
                res_table_warning.setText(Translator.translate("Nota: se muestran las 1000 mejores jugadas ganadoras"));
                res_table_warning.setVisible(true);
            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private void mejoresJugadas(ResultSet rs) {

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

                    if (tableModel.getColumnName(i).equals(Translator.translate("TIMBA"))) {
                        Timestamp ts = new Timestamp((long) row[i]);
                        DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                        Date date = new Date(ts.getTime());
                        row[i] = timeZoneFormat.format(date);
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS_RECIBIDAS")) || tableModel.getColumnName(i).equals(Translator.translate("CARTAS_JUGADA"))) {
                        row[i] = row[i] != null ? ((String) row[i]).replaceAll("#", " | ") : "";
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("JUGADA"))) {
                        row[i] = (int) row[i] - 1 >= 0 ? Hand.NOMBRES_JUGADAS[(int) row[i] - 1] : "";
                    }
                }

                tableModel.addRow(row);
            }

            res_table.setModel(tableModel);

            TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

            Helpers.disableSortAllColumns(res_table, tableRowSorter);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADOR")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("MANO")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("BENEFICIO")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADA")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADA")), (Comparator<String>) (o1, o2) -> Integer.compare(Hand.getHandValue(o1), Hand.getHandValue(o2)));

            if (Helpers.getTableColumnIndex(res_table, Translator.translate("TIMBA")) != -1) {

                tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("TIMBA")), true);

                tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("TIMBA")), (Comparator<String>) (o1, o2) -> {
                    try {
                        return Long.compare(new java.sql.Timestamp(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(o1).getTime()).getTime(), new java.sql.Timestamp(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(o2).getTime()).getTime());
                    } catch (ParseException ex) {
                        Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return 0;
                });
            }
            res_table.setRowSorter(tableRowSorter);

            table_panel.setVisible(true);

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void manosJugadas() {
        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        ResultSet rs;

        hand_combo.setVisible(false);

        HashMap<String, Integer[]> manos = new HashMap<>();

        if (game_combo.getSelectedIndex() > 0) {

            try {

                String sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and action.round=1 and action.action>=2 and hand.id_game=? group by player";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                while (rs.next()) {
                    manos.put(rs.getString("player"), new Integer[]{rs.getInt("jugadas"), 0, 0});
                }

                sql = "select player, count(distinct id_hand) as jugadas from hand,showdown where showdown.id_hand=hand.id and hand.id_game=? and showdown.winner=1 group by player";

                statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                while (rs.next()) {
                    Integer[] jugadas;
                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[1] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, rs.getInt("jugadas"), 0};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

                sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and hand.id_game=? group by player";

                statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                while (rs.next()) {
                    Integer[] jugadas;
                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[2] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, 0, rs.getInt("jugadas")};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            try {

                String sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and action.round=1 and action.action>=2 group by player";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);

                while (rs.next()) {
                    manos.put(rs.getString("player"), new Integer[]{rs.getInt("jugadas"), 0, 0});
                }

                sql = "select player, count(distinct id_hand) as jugadas from hand,showdown where showdown.id_hand=hand.id and showdown.winner=1 group by player";

                statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);

                while (rs.next()) {
                    Integer[] jugadas;
                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[1] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, rs.getInt("jugadas"), 0};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

                sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id group by player";

                statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);

                while (rs.next()) {
                    Integer[] jugadas;

                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[2] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, 0, rs.getInt("jugadas")};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        DefaultTableModel tableModel = new DefaultTableModel();

        tableModel.addColumn(Translator.translate("JUGADOR"));
        tableModel.addColumn(Translator.translate("MANOS_JUGADAS"));
        tableModel.addColumn(Translator.translate("MANOS_GANADORAS"));
        tableModel.addColumn(Translator.translate("EFICIENCIA"));

        Object[] row = new Object[4];

        for (Map.Entry<String, Integer[]> entry : manos.entrySet()) {

            row[0] = entry.getKey();
            row[1] = Helpers.float2String(((float) entry.getValue()[0] / (float) entry.getValue()[2]) * 100) + "%";
            row[2] = Helpers.float2String(((float) entry.getValue()[1] / (float) entry.getValue()[2]) * 100) + "%";
            row[3] = (float) entry.getValue()[0] > 0f ? Helpers.float2String(((float) entry.getValue()[1] / (float) entry.getValue()[0]) * 100) + "%" : "0%";
            tableModel.addRow(row);
        }

        res_table.setModel(tableModel);

        TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

        Helpers.disableSortAllColumns(res_table, tableRowSorter);

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADOR")), true);

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS_JUGADAS")), true);

        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS_JUGADAS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS_GANADORAS")), true);

        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS_GANADORAS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("EFICIENCIA")), true);

        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("EFICIENCIA")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

        res_table.setRowSorter(tableRowSorter);
        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table, Translator.translate("EFICIENCIA")));
        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table, Translator.translate("EFICIENCIA")));
        table_panel.setVisible(true);

    }

    private void subidasRonda(int ronda) {
        if (hand_combo.getSelectedIndex() != 0) {
            hand_combo.setSelectedIndex(-1);
        }

        ResultSet rs;

        hand_combo.setVisible(false);

        HashMap<String, Integer[]> manos = new HashMap<>();

        if (game_combo.getSelectedIndex() > 0) {

            try {

                String sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and action.round=" + String.valueOf(ronda) + " and action.action>=3 and hand.id_game=? group by player";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                while (rs.next()) {
                    manos.put(rs.getString("player"), new Integer[]{rs.getInt("jugadas"), 0});
                }

                sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and hand.id_game=? group by player";

                statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                while (rs.next()) {
                    Integer[] jugadas;
                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[1] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, rs.getInt("jugadas")};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            try {

                String sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id and action.round=" + String.valueOf(ronda) + " and action.action>=3 group by player";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);

                while (rs.next()) {
                    manos.put(rs.getString("player"), new Integer[]{rs.getInt("jugadas"), 0, 0});
                }

                sql = "select player, count(distinct id_hand) as jugadas from action,hand where action.id_hand=hand.id group by player";

                statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);

                while (rs.next()) {
                    Integer[] jugadas;

                    if (manos.containsKey(rs.getString("player"))) {

                        jugadas = manos.get(rs.getString("player"));
                        jugadas[1] = rs.getInt("jugadas");
                    } else {
                        jugadas = new Integer[]{0, rs.getInt("jugadas")};
                    }

                    manos.put(rs.getString("player"), jugadas);
                }

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        DefaultTableModel tableModel = new DefaultTableModel();

        tableModel.addColumn(Translator.translate("JUGADOR"));
        tableModel.addColumn(Translator.translate("MANOS"));

        Object[] row = new Object[4];

        for (Map.Entry<String, Integer[]> entry : manos.entrySet()) {

            row[0] = entry.getKey();
            row[1] = Helpers.float2String(((float) entry.getValue()[0] / (float) entry.getValue()[1]) * 100) + "%";
            tableModel.addRow(row);
        }

        res_table.setModel(tableModel);

        TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

        Helpers.disableSortAllColumns(res_table, tableRowSorter);

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADOR")), true);

        tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS")), true);

        tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS")), (Comparator<String>) (o1, o2) -> Float.compare(Float.parseFloat(o1.replaceAll(" *%$", "")), Float.parseFloat(o2.replaceAll(" *%$", ""))));

        res_table.setRowSorter(tableRowSorter);
        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS")));
        res_table.getRowSorter().toggleSortOrder(Helpers.getTableColumnIndex(res_table, Translator.translate("MANOS")));
        table_panel.setVisible(true);

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

        try {
            if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {

                hand_combo.setVisible(true);
                hand_combo.setSelectedIndex(0);
            }
            ResultSet rs;

            if (hand_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as JUGADOR, ROUND(stack, 1) as STACK, buyin as BUYIN, ROUND(stack-buyin,1) as BENEFICIO FROM balance WHERE id_hand=? GROUP BY JUGADOR ORDER BY BENEFICIO DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else if (game_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as JUGADOR, ROUND(stack,1) AS STACK, buyin AS BUYIN, ROUND(stack-buyin,1) AS BENEFICIO FROM balance,hand WHERE balance.id_hand=hand.id AND hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?) GROUP BY JUGADOR ORDER BY BENEFICIO DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                statement.setInt(2, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else {
                String sql = "SELECT player AS JUGADOR, ROUND(SUM(stack),1) AS STACK, SUM(buyin) AS BUYIN, ROUND(SUM(stack-buyin),1) AS BENEFICIO from balance WHERE id_hand IN (SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand group by id_game) GROUP BY JUGADOR ORDER BY BENEFICIO DESC";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
            }

            Helpers.resultSetToTableModel(rs, res_table);

            TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

            Helpers.disableSortAllColumns(res_table, tableRowSorter);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADOR")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("BUYIN")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("STACK")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("STACK")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("BENEFICIO")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            res_table.setRowSorter(tableRowSorter);

            table_panel.setVisible(true);

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadGameData(int id) {

        try {
            String sql = "SELECT *, (SELECT COUNT(*) from hand where id_game=? AND end IS NOT NULL) as tot_hands FROM game WHERE id=?";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id);

            statement.setInt(2, id);

            ResultSet rs = statement.executeQuery();

            game_playtime_val.setText(Helpers.seconds2FullTime((rs.getLong("end") / 1000 - rs.getLong("start") / 1000)) + " (" + Helpers.seconds2FullTime(rs.getInt("play_time")) + ")");

            String[] jugadores = rs.getString("players").split("#");

            String players = "";

            for (String j : jugadores) {

                players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
            }

            game_players_val.setText(players.replaceAll("  \\|  $", ""));

            game_buyin_val.setText(String.valueOf(rs.getInt("buyin")));

            game_hand_val.setText(String.valueOf(rs.getInt("tot_hands")));

            game_blinds_val.setText(String.valueOf(rs.getFloat("sb")) + " / " + String.valueOf(rs.getFloat("sb") * 2));

            game_blinds_double_val.setText(rs.getInt("blinds_time") != -1 ? String.valueOf(rs.getInt("blinds_time")) + " min" : "NO");

            game_rebuy_val.setText(rs.getBoolean("rebuy") ? Translator.translate("SÍ") : "NO");

        } catch (SQLException | UnsupportedEncodingException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadHandData(int id_game, int id_hand) {

        try {
            String sql = "SELECT * FROM hand WHERE id_game=? AND id=?";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id_game);

            statement.setInt(2, id_hand);

            ResultSet rs = statement.executeQuery();

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
                hand_comcards_val.setText(rs.getString("com_cards").replaceAll("#", " | "));
            }

            hand_bote_val.setText(String.valueOf(Helpers.floatClean1D(rs.getFloat("pot"))));

            loadShowdownData(id_hand);

        } catch (SQLException | UnsupportedEncodingException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadShowdownData(int id_hand) {

        try {
            ResultSet rs;
            String sql = "SELECT player AS JUGADOR, winner as GANA, hole_cards as CARTAS_RECIBIDAS, hand_cards as CARTAS_JUGADA, hand_val AS JUGADA, ROUND(pay,1) as PAGAR, ROUND(profit,1) as BENEFICIO FROM showdown WHERE id_hand=? order by GANA DESC,PAGAR DESC";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id_hand);

            rs = statement.executeQuery();

            showdownData(rs);

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void showdownData(ResultSet rs) {

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

                    if (tableModel.getColumnName(i).equals(Translator.translate("GANA"))) {
                        row[i] = (int) row[i] == 1 ? Translator.translate("SÍ") : "NO";
                    } else if (tableModel.getColumnName(i).equals(Translator.translate("CARTAS_RECIBIDAS")) || tableModel.getColumnName(i).equals(Translator.translate("CARTAS_JUGADA"))) {
                        row[i] = row[i] != null ? ((String) row[i]).replaceAll("#", " | ") : "";

                    } else if (tableModel.getColumnName(i).equals(Translator.translate("JUGADA"))) {
                        row[i] = (int) row[i] - 1 >= 0 ? Hand.NOMBRES_JUGADAS[(int) row[i] - 1] : "";
                    }
                }

                tableModel.addRow(row);
            }

            showdown_table.setModel(tableModel);

            TableRowSorter tableRowSorter = new TableRowSorter(showdown_table.getModel());

            Helpers.disableSortAllColumns(res_table, tableRowSorter);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table, Translator.translate("JUGADOR")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table, Translator.translate("GANA")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table, Translator.translate("PAGAR")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table, Translator.translate("PAGAR")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table, Translator.translate("BENEFICIO")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table, Translator.translate("BENEFICIO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(showdown_table, Translator.translate("JUGADA")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(showdown_table, Translator.translate("JUGADA")), (Comparator<String>) (o1, o2) -> Integer.compare(Hand.getHandValue(o1), Hand.getHandValue(o2)));

            showdown_table.setRowSorter(tableRowSorter);

            showdown_panel.setVisible(true);

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void tiempoMedioRespuesta() {

        try {

            if (!hand_combo.isVisible() && game_combo.getSelectedIndex() > 0) {

                hand_combo.setVisible(true);
                hand_combo.setSelectedIndex(0);
            }

            ResultSet rs;

            if (hand_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action WHERE id_hand=? GROUP BY JUGADOR order by TIEMPO DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else if (game_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action,hand WHERE action.id_hand=hand.id AND hand.id_game=? GROUP BY JUGADOR order by TIEMPO DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else {
                String sql = "SELECT player as JUGADOR, ROUND(AVG(response_time),1) as TIEMPO from action GROUP BY JUGADOR order by TIEMPO DESC";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
            }

            Helpers.resultSetToTableModel(rs, res_table);

            TableRowSorter tableRowSorter = new TableRowSorter(res_table.getModel());

            Helpers.disableSortAllColumns(res_table, tableRowSorter);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("JUGADOR")), true);

            tableRowSorter.setSortable(Helpers.getTableColumnIndex(res_table, Translator.translate("TIEMPO")), true);

            tableRowSorter.setComparator(Helpers.getTableColumnIndex(res_table, Translator.translate("TIEMPO")), (Comparator<Double>) (o1, o2) -> o1.compareTo(o2));

            res_table.setRowSorter(tableRowSorter);

            table_panel.setVisible(true);

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadHands(int id) {

        try {

            String sql = "SELECT * FROM hand WHERE id_game=? AND end IS NOT NULL ORDER BY id DESC";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id);

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {

                try {

                    hand_combo.addItem(Translator.translate("MANO") + " " + String.valueOf(rs.getInt("counter")));

                    HashMap<String, Object> map = new HashMap<>();

                    map.put("id", rs.getInt("id"));

                    hand.put(Translator.translate("MANO") + " " + String.valueOf(rs.getInt("counter")), map);
                } catch (SQLException ex) {
                    Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadGames() {

        try {

            String sql = "SELECT id,start,server FROM game ORDER BY start DESC";

            Statement statement = SQLITE.createStatement();

            statement.setQueryTimeout(30);

            ResultSet rs = statement.executeQuery(sql);

            while (rs.next()) {
                // read the result set

                try {
                    Timestamp ts = new Timestamp(rs.getLong("start"));
                    DateFormat timeZoneFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                    Date date = new Date(ts.getTime());

                    HashMap<String, Object> map = new HashMap<>();
                    map.put("id", rs.getInt("id"));
                    game.put(rs.getString("server") + " @ " + timeZoneFormat.format(date), map);
                    game_combo.addItem(rs.getString("server") + " @ " + timeZoneFormat.format(date));
                } catch (SQLException ex) {
                    Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

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
        jPanel2 = new javax.swing.JPanel();
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

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Lo que no son cuentas, son cuentos");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        title.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        title.setText("ESTADÍSTICAS");
        title.setDoubleBuffered(true);
        title.setFocusable(false);

        scroll_stats_panel.setBorder(null);

        game_combo.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                game_comboItemStateChanged(evt);
            }
        });

        game_data_panel.setOpaque(false);

        game_hand_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_hand_label.setText("Manos:");

        game_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_players_label.setText("Jugadores:");

        game_buyin_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_buyin_label.setText("Compra:");

        game_blinds_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_blinds_label.setText("Ciegas:");

        game_blinds_double_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_blinds_double_label.setText("Doblar ciegas:");

        game_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_rebuy_label.setText("Recomprar:");

        game_hand_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_hand_val.setText(" ");

        game_players_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_players_val.setText(" ");

        game_buyin_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_buyin_val.setText(" ");

        game_blinds_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_blinds_val.setText(" ");

        game_blinds_double_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_blinds_double_val.setText(" ");

        game_rebuy_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_rebuy_val.setText(" ");

        game_playtime_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_playtime_label.setText("Duración:");

        game_playtime_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_playtime_val.setText(" ");

        javax.swing.GroupLayout game_data_panelLayout = new javax.swing.GroupLayout(game_data_panel);
        game_data_panel.setLayout(game_data_panelLayout);
        game_data_panelLayout.setHorizontalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(game_data_panelLayout.createSequentialGroup()
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(game_blinds_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_buyin_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_buyin_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_blinds_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(game_data_panelLayout.createSequentialGroup()
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(game_rebuy_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_blinds_double_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_blinds_double_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_rebuy_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(game_data_panelLayout.createSequentialGroup()
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(game_playtime_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_players_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_hand_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_hand_val, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE)
                            .addComponent(game_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_playtime_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        game_data_panelLayout.setVerticalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_playtime_label)
                    .addComponent(game_playtime_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_hand_label, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_hand_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_players_label)
                    .addComponent(game_players_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_buyin_label, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_buyin_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_blinds_label)
                    .addComponent(game_blinds_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_blinds_double_label, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_blinds_double_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_rebuy_label)
                    .addComponent(game_rebuy_val))
                .addContainerGap())
        );

        hand_combo.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                hand_comboItemStateChanged(evt);
            }
        });

        hand_data_panel.setOpaque(false);

        hand_blinds_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_blinds_label.setText("Ciegas:");
        hand_blinds_label.setPreferredSize(new java.awt.Dimension(45, 17));

        hand_blinds_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_blinds_val.setText(" ");

        hand_time_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_time_label.setText("Duración:");

        hand_cp_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_cp_label.setText("Ciega pequeña:");

        hand_cg_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_cg_label.setText("Ciega grande:");

        hand_comcards_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_comcards_label.setText("Cartas comunitarias:");

        hand_preflop_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_preflop_players_label.setText("Jugadores PREFLOP:");

        hand_flop_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_flop_players_label.setText("Jugadores FLOP:");

        hand_turn_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_turn_players_label.setText("Jugadores TURN:");

        hand_river_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_river_players_label.setText("Jugadores RIVER:");

        hand_bote_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_bote_label.setText("BOTE:");

        hand_time_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_time_val.setText(" ");

        hand_cp_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_cp_val.setText(" ");

        hand_cg_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_cg_val.setText(" ");

        hand_comcards_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_comcards_val.setText(" ");

        hand_preflop_players_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_preflop_players_val.setText(" ");

        hand_flop_players_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_flop_players_val.setText(" ");

        hand_turn_players_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_turn_players_val.setText(" ");

        hand_river_players_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_river_players_val.setText(" ");

        hand_bote_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_bote_val.setText(" ");

        showdown_table.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
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
                    .addComponent(showdown_panel)
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
                .addGap(0, 0, 0)
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

        stats_combo.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        stats_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                stats_comboItemStateChanged(evt);
            }
        });

        res_table.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
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

        res_table_warning.setFont(new java.awt.Font("Dialog", 2, 12)); // NOI18N
        res_table_warning.setText("Nota:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(game_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(hand_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(stats_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(table_panel)
            .addComponent(game_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(hand_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(res_table_warning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(game_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_data_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hand_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(hand_data_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(stats_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(table_panel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(res_table_warning)
                .addContainerGap())
        );

        scroll_stats_panel.setViewportView(jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(title, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scroll_stats_panel))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(title)
                .addGap(18, 18, 18)
                .addComponent(scroll_stats_panel)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void game_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_game_comboItemStateChanged
        // TODO add your handling code here:

        if (game_combo.getSelectedIndex() != -1) {

            if (game.get((String) game_combo.getSelectedItem()) != null) {
                loadGameData((int) game.get((String) game_combo.getSelectedItem()).get("id"));
                game_data_panel.setVisible(true);
                hand_combo.removeAllItems();
                hand_combo.addItem(Translator.translate("TODAS LAS MANOS"));
                loadHands((int) game.get((String) game_combo.getSelectedItem()).get("id"));
                hand_combo.setVisible(true);
            } else {
                game_data_panel.setVisible(false);
                hand_combo.setSelectedIndex(-1);
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

        if (hand_combo.getSelectedIndex() != -1) {

            if (game.get((String) game_combo.getSelectedItem()) != null && hand.get((String) hand_combo.getSelectedItem()) != null) {
                loadHandData((int) game.get((String) game_combo.getSelectedItem()).get("id"), (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));
                hand_data_panel.setVisible(true);
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

    }//GEN-LAST:event_hand_comboItemStateChanged

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:

        last_mp3_loop = Helpers.getCurrentLoopMp3Playing();

        if (Game.SONIDOS && last_mp3_loop != null && !Helpers.MP3_LOOP_MUTED.contains(last_mp3_loop)) {
            Helpers.muteLoopMp3(last_mp3_loop);
        } else {
            last_mp3_loop = null;
        }

        Helpers.playLoopMp3Resource("misc/stats_music.mp3");
    }//GEN-LAST:event_formWindowOpened

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:
        Helpers.stopCurrentLoopMp3Resource();

        if (last_mp3_loop != null) {
            Helpers.unmuteLoopMp3(last_mp3_loop);
        }
    }//GEN-LAST:event_formWindowClosed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel game_blinds_double_label;
    private javax.swing.JLabel game_blinds_double_val;
    private javax.swing.JLabel game_blinds_label;
    private javax.swing.JLabel game_blinds_val;
    private javax.swing.JLabel game_buyin_label;
    private javax.swing.JLabel game_buyin_val;
    private javax.swing.JComboBox<String> game_combo;
    private javax.swing.JPanel game_data_panel;
    private javax.swing.JLabel game_hand_label;
    private javax.swing.JLabel game_hand_val;
    private javax.swing.JLabel game_players_label;
    private javax.swing.JLabel game_players_val;
    private javax.swing.JLabel game_playtime_label;
    private javax.swing.JLabel game_playtime_val;
    private javax.swing.JLabel game_rebuy_label;
    private javax.swing.JLabel game_rebuy_val;
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
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTable res_table;
    private javax.swing.JLabel res_table_warning;
    private javax.swing.JScrollPane scroll_stats_panel;
    private javax.swing.JScrollPane showdown_panel;
    private javax.swing.JTable showdown_table;
    private javax.swing.JComboBox<String> stats_combo;
    private javax.swing.JScrollPane table_panel;
    private javax.swing.JLabel title;
    // End of variables declaration//GEN-END:variables
}
