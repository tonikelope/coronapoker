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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.DefaultTableModel;
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

    /**
     * Creates new form Stats
     */
    public Stats(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        init = true;

        Stats tthis = this;

        sqlstats.put("GANANCIAS/PÉRDIDAS", this::balance);
        sqlstats.put("TIEMPO MEDIO DE RESPUESTA", this::tiempoMedioRespuesta);
        sqlstats.put("MANOS JUGADAS/GANADAS", this::manosJugadas);
        sqlstats.put("TOP-10 JUGADAS MOSTRADAS", this::mejoresJugadas);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                scroll_stats_panel.getVerticalScrollBar().setUnitIncrement(16);
                scroll_stats_panel.getHorizontalScrollBar().setUnitIncrement(16);
                showdown_panel.setVisible(false);
                table_panel.setVisible(false);
                hand_data_panel.setVisible(false);
                hand_combo.setVisible(false);
                game_combo.setSelectedIndex(0);
                game_data_panel.setVisible(false);

                Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);
                Helpers.translateComponents(tthis, false);
                res_table.setRowHeight(res_table.getRowHeight() + 20);
                showdown_table.setRowHeight(showdown_table.getRowHeight() + 20);

                for (Map.Entry<String, SQLStats> entry : sqlstats.entrySet()) {

                    stats_combo.setSelectedIndex(-1);
                    stats_combo.addItem(entry.getKey());
                }

                stats_combo.setSelectedIndex(-1);

            }
        });

        loadGames();

        init = false;
    }

    private void mejoresJugadas() {

        ResultSet rs;

        hand_combo.setVisible(false);

        if (game_combo.getSelectedIndex() > 0) {

            try {
                String sql = "select player as PLAYER, hole_cards as HOLE_CARDS, hand_cards as HAND_CARDS, hand_val as HAND_VAL, game.start as GAME, hand.counter as MANO, showdown.winner as WIN, round(showdown.pay,1) as PAY from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game and game.id=? order by hand_val DESC,game.start DESC;";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

                mejoresJugadas(rs);

            } catch (SQLException ex) {
                Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            try {
                String sql = "select player as PLAYER, hole_cards as HOLE_CARDS, hand_cards as HAND_CARDS, hand_val as HAND_VAL, game.start as GAME, hand.counter as MANO, showdown.winner as WIN, round(showdown.pay,1) as PAY from game,showdown,hand where hand.id=showdown.id_hand and game.id=hand.id_game order by hand_val DESC,game.start DESC;";
                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
                mejoresJugadas(rs);
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
                tableModel.addColumn(metaData.getColumnLabel(columnIndex));
            }

            Object[] row = new Object[columnCount];

            while (rs.next()) {

                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);

                    if (tableModel.getColumnName(i).equals("GAME")) {

                        Timestamp ts = new Timestamp((long) row[i]);
                        Date date = new Date(ts.getTime());

                        row[i] = date.toString();
                    } else if (tableModel.getColumnName(i).equals("HAND_VAL")) {
                        row[i] = Hand.NOMBRES_JUGADAS[(int) row[i] - 1];
                    } else if (tableModel.getColumnName(i).equals("WIN")) {
                        row[i] = (int) row[i] == 1 ? "SÍ" : "NO";
                    }
                }

                tableModel.addRow(row);
            }

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    res_table.setModel(tableModel);
                    table_panel.setVisible(true);

                    pack();

                }
            });
        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void manosJugadas() {

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

        tableModel.addColumn("PLAYER");
        tableModel.addColumn("MANOS JUGADAS");
        tableModel.addColumn("MANOS GANADORAS");

        Object[] row = new Object[3];

        for (Map.Entry<String, Integer[]> entry : manos.entrySet()) {

            row[0] = entry.getKey();
            row[1] = Helpers.float2String(((float) entry.getValue()[0] / (float) entry.getValue()[2]) * 100) + "%";
            row[2] = Helpers.float2String(((float) entry.getValue()[1] / (float) entry.getValue()[2]) * 100) + "%";
            tableModel.addRow(row);
        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                res_table.setModel(tableModel);
                table_panel.setVisible(true);

                pack();

            }
        });

    }

    private void balance() {

        hand_combo.setVisible(true);
        try {
            ResultSet rs;

            if (hand_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as PLAYER, ROUND(stack, 1) as STACK, buyin as BUYIN, ROUND(stack-buyin,1) as PROFIT FROM balance WHERE id_hand=? GROUP BY PLAYER";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else if (game_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as PLAYER, ROUND(stack,1) AS STACK, buyin AS BUYIN, ROUND(stack-buyin,1) AS PROFIT FROM balance,hand WHERE balance.id_hand=hand.id AND hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand and hand.id_game=?) GROUP BY PLAYER";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                statement.setInt(2, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else {
                String sql = "SELECT player AS PLAYER, ROUND(SUM(stack),1) AS STACK, SUM(buyin) AS BUYIN, ROUND(SUM(stack-buyin),1) AS PROFIT from balance WHERE id_hand IN (SELECT max(hand.id) from hand,balance where hand.id=balance.id_hand group by id_game) GROUP BY PLAYER";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
            }

            Helpers.resultSetToTableModel(rs, res_table);

            table_panel.setVisible(true);

            pack();

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadGameData(int id) {

        try {
            String sql = "SELECT *, (SELECT COUNT(*) from hand where id_game=?) as tot_hands FROM game WHERE id=?";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id);

            statement.setInt(2, id);

            ResultSet rs = statement.executeQuery();

            Timestamp ts = new Timestamp(rs.getLong("start"));
            Date date = new Date(ts.getTime());
            game_start_val.setText(date.toString());
            ts = new Timestamp(rs.getLong("end"));
            date = new Date(ts.getTime());
            game_end_val.setText(date.toString());
            game_playtime_val.setText(String.valueOf(Helpers.seconds2FullTime(rs.getInt("play_time"))));

            String[] jugadores = rs.getString("players").split("#");

            String players = "";

            for (String j : jugadores) {

                players += new String(Base64.decodeBase64(j.getBytes("UTF-8")), "UTF-8") + "  |  ";
            }

            game_players_val.setText(players.replaceAll("  \\|  $", ""));

            game_buyin_val.setText(String.valueOf(rs.getInt("buyin")));

            game_hands_val.setText(String.valueOf(rs.getInt("tot_hands")));

            game_blinds_val.setText(String.valueOf(rs.getFloat("sb")) + " / " + String.valueOf(rs.getFloat("sb") * 2));

            game_blinds_double_val.setText(rs.getInt("blinds_time") != -1 ? String.valueOf(rs.getInt("blinds_time")) + " min" : "NO");

            game_rebuy_val.setText(rs.getBoolean("rebuy") ? "SÍ" : "NO");

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

            hand_dealer_val.setText(rs.getString("dealer"));
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
            String sql = "SELECT player AS PLAYER, winner as WIN, hole_cards as HOLE_CARDS, hand_cards as HAND_CARDS, hand_val AS HAND_VAL, ROUND(pay,1) as PROFIT FROM showdown WHERE id_hand=?";

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
                tableModel.addColumn(metaData.getColumnLabel(columnIndex));
            }

            Object[] row = new Object[columnCount];

            while (rs.next()) {

                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);

                    if (i == 1) {
                        row[i] = (int) row[i] == 1 ? "SÍ" : "NO";
                    } else if ((i == 3 || i == 2) && row[i] != null) {
                        row[i] = ((String) row[i]).replaceAll("#", " | ");
                    } else if (i == 4) {
                        row[i] = Hand.NOMBRES_JUGADAS[(int) row[i] - 1];
                    }
                }

                tableModel.addRow(row);
            }

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    showdown_table.setModel(tableModel);
                    showdown_panel.setVisible(true);

                    pack();

                }
            });
        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void tiempoMedioRespuesta() {

        hand_combo.setVisible(true);
        try {
            ResultSet rs;

            if (hand_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as PLAYER, ROUND(AVG(response_time),1) as AVG_TIME from action WHERE id_hand=? GROUP BY PLAYER order by AVG_TIME DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) hand.get((String) hand_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else if (game_combo.getSelectedIndex() > 0) {

                String sql = "SELECT player as PLAYER, ROUND(AVG(response_time),1) as AVG_TIME from action,hand WHERE action.id_hand=hand.id AND hand.id_game=? GROUP BY PLAYER order by AVG_TIME DESC";

                PreparedStatement statement = SQLITE.prepareStatement(sql);

                statement.setQueryTimeout(30);

                statement.setInt(1, (int) game.get((String) game_combo.getSelectedItem()).get("id"));

                rs = statement.executeQuery();

            } else {
                String sql = "SELECT player as PLAYER, ROUND(AVG(response_time),1) as AVG_TIME from action GROUP BY PLAYER order by AVG_TIME DESC";

                Statement statement = SQLITE.createStatement();

                statement.setQueryTimeout(30);

                rs = statement.executeQuery(sql);
            }

            Helpers.resultSetToTableModel(rs, res_table);

            table_panel.setVisible(true);

            pack();

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadHands(int id) {

        try {

            String sql = "SELECT * FROM hand WHERE id_game=? ORDER BY id DESC";

            PreparedStatement statement = SQLITE.prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setInt(1, id);

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {

                        try {

                            hand_combo.addItem("MANO " + String.valueOf(rs.getInt("counter")));

                            HashMap<String, Object> map = new HashMap<>();

                            map.put("id", rs.getInt("id"));

                            hand.put("MANO " + String.valueOf(rs.getInt("counter")), map);
                        } catch (SQLException ex) {
                            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                });
            }

        } catch (SQLException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void loadGames() {

        try {

            String sql = "SELECT id,start FROM game ORDER BY start DESC";

            Statement statement = SQLITE.createStatement();

            statement.setQueryTimeout(30);

            ResultSet rs = statement.executeQuery(sql);

            while (rs.next()) {
                // read the result set

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {

                        try {
                            Timestamp ts = new Timestamp(rs.getLong("start"));
                            Date date = new Date(ts.getTime());
                            game_combo.addItem(date.toString());

                            HashMap<String, Object> map = new HashMap<>();

                            map.put("id", rs.getInt("id"));
                            game.put(date.toString(), map);
                        } catch (SQLException ex) {
                            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                });
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
        game_start_label = new javax.swing.JLabel();
        game_end_label = new javax.swing.JLabel();
        game_playtime_label = new javax.swing.JLabel();
        game_players_label = new javax.swing.JLabel();
        game_buyin_label = new javax.swing.JLabel();
        game_blinds_label = new javax.swing.JLabel();
        game_blinds_double_label = new javax.swing.JLabel();
        game_rebuy_label = new javax.swing.JLabel();
        game_start_val = new javax.swing.JLabel();
        game_end_val = new javax.swing.JLabel();
        game_playtime_val = new javax.swing.JLabel();
        game_players_val = new javax.swing.JLabel();
        game_buyin_val = new javax.swing.JLabel();
        game_blinds_val = new javax.swing.JLabel();
        game_blinds_double_val = new javax.swing.JLabel();
        game_rebuy_val = new javax.swing.JLabel();
        game_hands_label = new javax.swing.JLabel();
        game_hands_val = new javax.swing.JLabel();
        hand_combo = new javax.swing.JComboBox<>();
        hand_data_panel = new javax.swing.JPanel();
        hand_blinds_label = new javax.swing.JLabel();
        hand_blinds_val = new javax.swing.JLabel();
        hand_dealer_label = new javax.swing.JLabel();
        hand_cp_label = new javax.swing.JLabel();
        hand_cg_label = new javax.swing.JLabel();
        hand_comcards_label = new javax.swing.JLabel();
        hand_preflop_players_label = new javax.swing.JLabel();
        hand_flop_players_label = new javax.swing.JLabel();
        hand_turn_players_label = new javax.swing.JLabel();
        hand_river_players_label = new javax.swing.JLabel();
        hand_bote_label = new javax.swing.JLabel();
        hand_dealer_val = new javax.swing.JLabel();
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

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("ESTADÍSTICAS");

        title.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        title.setText("ESTADÍSTICAS (beta)");
        title.setDoubleBuffered(true);
        title.setFocusable(false);

        game_combo.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "TODAS LAS TIMBAS" }));
        game_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                game_comboItemStateChanged(evt);
            }
        });

        game_data_panel.setOpaque(false);

        game_start_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_start_label.setText("Inicio:");

        game_end_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_end_label.setText("Fin:");

        game_playtime_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_playtime_label.setText("Tiempo de juego:");

        game_players_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_players_label.setText("Participantes:");

        game_buyin_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_buyin_label.setText("Compra:");

        game_blinds_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_blinds_label.setText("Ciegas:");

        game_blinds_double_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_blinds_double_label.setText("Doblar ciegas:");

        game_rebuy_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_rebuy_label.setText("Recomprar:");

        game_start_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_start_val.setText(" ");

        game_end_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_end_val.setText(" ");

        game_playtime_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_playtime_val.setText(" ");

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

        game_hands_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        game_hands_label.setText("Manos:");

        game_hands_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        game_hands_val.setText(" ");

        javax.swing.GroupLayout game_data_panelLayout = new javax.swing.GroupLayout(game_data_panel);
        game_data_panel.setLayout(game_data_panelLayout);
        game_data_panelLayout.setHorizontalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(game_data_panelLayout.createSequentialGroup()
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(game_end_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_start_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_start_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_end_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
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
                            .addComponent(game_hands_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_players_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_playtime_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(game_playtime_val, javax.swing.GroupLayout.DEFAULT_SIZE, 885, Short.MAX_VALUE)
                            .addComponent(game_players_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(game_hands_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        game_data_panelLayout.setVerticalGroup(
            game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(game_data_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_start_label)
                    .addComponent(game_start_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_end_label)
                    .addComponent(game_end_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_hands_label)
                    .addComponent(game_hands_val))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(game_data_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(game_playtime_label, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_playtime_val))
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
        hand_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "TODAS LAS MANOS" }));
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

        hand_dealer_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        hand_dealer_label.setText("Dealer:");

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

        hand_dealer_val.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        hand_dealer_val.setText(" ");

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
                            .addComponent(hand_dealer_label)
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
                            .addComponent(hand_dealer_val, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                    .addComponent(hand_dealer_label)
                    .addComponent(hand_dealer_val))
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

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(game_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(hand_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(stats_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(table_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 1061, Short.MAX_VALUE)
                    .addComponent(game_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(hand_data_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
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
                .addComponent(table_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE))
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

                hand_combo.setVisible(true);
                hand_combo.removeAllItems();
                hand_combo.addItem("TODAS LAS MANOS");
                loadHands((int) game.get((String) game_combo.getSelectedItem()).get("id"));
            } else {
                game_data_panel.setVisible(false);
                hand_combo.setVisible(false);
            }

            if (stats_combo.getSelectedIndex() >= 0) {

                sqlstats.get((String) stats_combo.getSelectedItem()).call();

            }

            pack();

        } else {

            hand_combo.setVisible(false);
            game_data_panel.setVisible(false);
        }

    }//GEN-LAST:event_game_comboItemStateChanged

    private void stats_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_stats_comboItemStateChanged
        // TODO add your handling code here:

        if (!init && stats_combo.getSelectedIndex() != -1) {
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

                sqlstats.get((String) stats_combo.getSelectedItem()).call();
            }

            pack();

        } else {
            hand_data_panel.setVisible(false);
        }

    }//GEN-LAST:event_hand_comboItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel game_blinds_double_label;
    private javax.swing.JLabel game_blinds_double_val;
    private javax.swing.JLabel game_blinds_label;
    private javax.swing.JLabel game_blinds_val;
    private javax.swing.JLabel game_buyin_label;
    private javax.swing.JLabel game_buyin_val;
    private javax.swing.JComboBox<String> game_combo;
    private javax.swing.JPanel game_data_panel;
    private javax.swing.JLabel game_end_label;
    private javax.swing.JLabel game_end_val;
    private javax.swing.JLabel game_hands_label;
    private javax.swing.JLabel game_hands_val;
    private javax.swing.JLabel game_players_label;
    private javax.swing.JLabel game_players_val;
    private javax.swing.JLabel game_playtime_label;
    private javax.swing.JLabel game_playtime_val;
    private javax.swing.JLabel game_rebuy_label;
    private javax.swing.JLabel game_rebuy_val;
    private javax.swing.JLabel game_start_label;
    private javax.swing.JLabel game_start_val;
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
    private javax.swing.JLabel hand_dealer_label;
    private javax.swing.JLabel hand_dealer_val;
    private javax.swing.JLabel hand_flop_players_label;
    private javax.swing.JLabel hand_flop_players_val;
    private javax.swing.JLabel hand_preflop_players_label;
    private javax.swing.JLabel hand_preflop_players_val;
    private javax.swing.JLabel hand_river_players_label;
    private javax.swing.JLabel hand_river_players_val;
    private javax.swing.JLabel hand_turn_players_label;
    private javax.swing.JLabel hand_turn_players_val;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTable res_table;
    private javax.swing.JScrollPane scroll_stats_panel;
    private javax.swing.JScrollPane showdown_panel;
    private javax.swing.JTable showdown_table;
    private javax.swing.JComboBox<String> stats_combo;
    private javax.swing.JScrollPane table_panel;
    private javax.swing.JLabel title;
    // End of variables declaration//GEN-END:variables
}
