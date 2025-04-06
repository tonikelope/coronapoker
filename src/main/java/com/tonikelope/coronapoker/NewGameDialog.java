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

import com.tonikelope.coronapoker.Helpers.JTextFieldRegularPopupMenu;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;

/**
 *
 * @author tonikelope
 */
public class NewGameDialog extends JDialog {

    public final static int DEFAULT_PORT = 7234;
    public final static int DEFAULT_AVATAR_WIDTH = 50;
    public final static int AVATAR_MAX_FILESIZE = 256; //KB
    public final static int MAX_NICK_LENGTH = 15;
    public final static int MAX_PASS_LENGTH = 30;
    public final static int MAX_PORT_LENGTH = 5;
    public static volatile int BUYIN_SPINNER_STEP;

    private final HashMap<String, HashMap<String, Object>> game = new HashMap<>();
    private volatile boolean dialog_ok = false;
    private volatile boolean partida_local;
    private volatile File avatar = null;
    private volatile boolean update = false;
    private volatile boolean init = false;
    private final static ConcurrentLinkedQueue<String> SERVER_HISTORY_QUEUE = loadServerHistory();
    private volatile int conta_history = SERVER_HISTORY_QUEUE.isEmpty() ? 0 : SERVER_HISTORY_QUEUE.size() - 1;
    private volatile boolean force_recover = false;

    public void setForce_recover(boolean force_recover) {
        this.force_recover = force_recover;
    }

    public JCheckBox getRecover_checkbox() {
        return recover_checkbox;
    }

    public JButton getVamos() {
        return vamos;
    }

    public boolean isDialog_ok() {
        return dialog_ok;
    }

    public NewGameDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        update = true;

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);
        url_panel.setVisible(false);
        nick_pass_panel.setVisible(false);
        config_partida_panel.setVisible(true);
        this.random_panel.setVisible(false);
        this.random_combobox.setVisible(false);
        this.random_label.setVisible(false);
        this.randomorg_apikey.setVisible(false);
        this.randomorg_label.setVisible(false);
        this.recover_panel.setVisible(false);
        this.vamos.setText("GUARDAR");

        radar_label.setEnabled(GameFrame.RADAR_AVAILABLE);

        radar_label.setToolTipText(Translator.translate(radar_checkbox.isSelected() ? "Informes ANTI-TRAMPAS activados" : "Informes ANTI-TRAMPAS desactivados"));

        radar_checkbox.setToolTipText(radar_label.getToolTipText());

        this.doblar_checkbox.setSelected(GameFrame.CIEGAS_DOUBLE > 0);

        double_blinds_radio_minutos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);

        double_blinds_radio_manos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);

        if (GameFrame.CIEGAS_DOUBLE_TYPE <= 1) {
            doblar_ciegas_spinner_minutos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
            doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(GameFrame.CIEGAS_DOUBLE > 0 ? GameFrame.CIEGAS_DOUBLE : 60, 1, null, 1));
            ((DefaultEditor) doblar_ciegas_spinner_minutos.getEditor()).getTextField().setEditable(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            ((DefaultEditor) doblar_ciegas_spinner_manos.getEditor()).getTextField().setEditable(false);
            double_blinds_radio_minutos.setSelected(true);
            double_blinds_radio_manos.setSelected(false);
        } else {
            doblar_ciegas_spinner_manos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
            doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(GameFrame.CIEGAS_DOUBLE > 0 ? GameFrame.CIEGAS_DOUBLE : 60, 1, null, 1));
            ((DefaultEditor) doblar_ciegas_spinner_manos.getEditor()).getTextField().setEditable(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            ((DefaultEditor) doblar_ciegas_spinner_minutos.getEditor()).getTextField().setEditable(false);
            double_blinds_radio_minutos.setSelected(false);
            double_blinds_radio_manos.setSelected(true);
        }

        this.manos_spinner.setEnabled(GameFrame.MANOS > 0);
        this.manos_checkbox.setSelected(GameFrame.MANOS > 0);
        manos_spinner.setModel(new SpinnerNumberModel(GameFrame.MANOS > 0 ? GameFrame.MANOS : 60, 1, null, 1));
        ((DefaultEditor) manos_spinner.getEditor()).getTextField().setEditable(false);

        this.rebuy_checkbox.setSelected(GameFrame.REBUY);
        this.doblar_checkbox.setSelected(GameFrame.CIEGAS_DOUBLE > 0);

        String ciegas = (GameFrame.CIEGA_PEQUEÑA >= 1 ? String.valueOf((int) Math.round(GameFrame.CIEGA_PEQUEÑA)) : Helpers.float2String(GameFrame.CIEGA_PEQUEÑA)) + " / " + (GameFrame.CIEGA_GRANDE >= 1 ? String.valueOf((int) Math.round(GameFrame.CIEGA_GRANDE)) : Helpers.float2String(GameFrame.CIEGA_GRANDE));

        int i = 0, t = this.ciegas_combobox.getModel().getSize();

        while (i < t) {
            String item = this.ciegas_combobox.getItemAt(i);

            if (item.equals(ciegas)) {
                break;
            }

            i++;
        }

        buyin_spinner.setModel(new SpinnerNumberModel((int) GameFrame.BUYIN, (int) (GameFrame.CIEGA_GRANDE * 10f), (int) (GameFrame.CIEGA_GRANDE * 100f), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

        ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

        if (i < t) {
            this.ciegas_combobox.setSelectedIndex(i);
        }

        Helpers.setTranslatedTitle(this, "Actualizar timba");
        Helpers.updateFonts(this.vamos, Helpers.GUI_FONT, 0.75f);
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);

            setPreferredSize(getSize());

            pack();

            Helpers.windowAutoFitToRemoveHScrollBar(this, scroll_panel.getHorizontalScrollBar(), (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

        }

        init = true;

    }

    private static ConcurrentLinkedQueue<String> loadServerHistory() {

        ConcurrentLinkedQueue<String> history = new ConcurrentLinkedQueue<>();

        if (!GameFrame.SERVER_HISTORY.isBlank()) {

            history.addAll(Arrays.asList(GameFrame.SERVER_HISTORY.split("@")));
        }

        return history;
    }

    private String getServerHistoryString() {

        if (!SERVER_HISTORY_QUEUE.isEmpty()) {

            String ret = "";

            for (String s : SERVER_HISTORY_QUEUE) {

                ret += s + "@";
            }

            return ret.substring(0, ret.length() - 1);
        } else {
            return "";
        }
    }

    /**
     * Creates new form CrearTimba
     */
    public NewGameDialog(java.awt.Frame parent, boolean modal, boolean loc) {
        super(parent, modal);

        initComponents();

        partida_local = loc;

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        DefaultComboBoxModel<JLabel> random_combobox_model = new DefaultComboBoxModel<>();
        JLabel random_label1 = new JLabel("MODO PARANOICO [TRNG + CSPRNG]");
        random_label1.setIcon(new ImageIcon(getClass().getResource("/images/5stars.png")));
        random_combobox_model.addElement(random_label1);
        JLabel random_label2 = new JLabel("MODO CASINO [TRNG]");
        random_label2.setIcon(new ImageIcon(getClass().getResource("/images/4_5stars.png")));
        random_combobox_model.addElement(random_label2);
        JLabel random_label3 = new JLabel("MODO NORMAL [CSPRNG]");
        random_combobox_model.addElement(random_label3);
        random_label3.setIcon(new ImageIcon(getClass().getResource("/images/4stars.png")));
        random_combobox.setModel(random_combobox_model);

        random_combobox.setRenderer(new ComboBoxIconRenderer());

        game_combo.setEnabled(false);

        password.setEnabled(false);

        manos_spinner.setEnabled(false);

        double_blinds_radio_manos.setSelected(true);

        double_blinds_radio_minutos.setSelected(false);

        doblar_ciegas_spinner_minutos.setEnabled(false);

        radar_checkbox.setSelected(GameFrame.RADAR_AVAILABLE);

        radar_label.setEnabled(GameFrame.RADAR_AVAILABLE);

        radar_label.setToolTipText(Translator.translate(radar_checkbox.isSelected() ? "Informes ANTI-TRAMPAS activados" : "Informes ANTI-TRAMPAS desactivados"));

        radar_checkbox.setToolTipText(radar_label.getToolTipText());

        if (partida_local) {
            upnp_checkbox.setSelected(Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("upnp", "true")));
        } else {
            upnp_checkbox.setEnabled(false);
            radar_checkbox.setEnabled(false);
            recover_panel.setVisible(false);
        }

        class VamosButtonListener implements DocumentListener {

            public void changedUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }

            public void insertUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }

            public void removeUpdate(DocumentEvent e) {
                vamos.setEnabled(!nick.getText().isBlank() && !server_ip_textfield.getText().isBlank() && !server_port_textfield.getText().isBlank());
                password.setEnabled(pass_text.getPassword().length > 0);
            }
        }

        JTextFieldRegularPopupMenu.addTo(server_ip_textfield);
        server_ip_textfield.getDocument().addDocumentListener(new VamosButtonListener());

        JTextFieldRegularPopupMenu.addTo(server_port_textfield);
        server_port_textfield.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) server_port_textfield.getDocument()).setDocumentFilter(new Helpers.numericFilter(server_port_textfield, MAX_PORT_LENGTH));

        JTextFieldRegularPopupMenu.addTo(randomorg_apikey);

        JTextFieldRegularPopupMenu.addTo(nick);
        nick.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) nick.getDocument()).setDocumentFilter(new Helpers.maxLenghtFilter(nick, MAX_NICK_LENGTH));

        JTextFieldRegularPopupMenu.addTo(pass_text);
        pass_text.getDocument().addDocumentListener(new VamosButtonListener());
        ((AbstractDocument) pass_text.getDocument()).setDocumentFilter(new Helpers.maxLenghtFilter(pass_text, MAX_PASS_LENGTH));

        String elnick = Helpers.PROPERTIES.getProperty("nick", "");

        nick.setText(elnick.substring(0, Math.min(MAX_NICK_LENGTH, elnick.length())));

        String avatar_path = Helpers.PROPERTIES.getProperty("avatar", "");

        if (!avatar_path.isEmpty()) {

            avatar = new File(avatar_path);

            if (avatar.exists() && avatar.canRead() && avatar.length() <= AVATAR_MAX_FILESIZE * 1024) {
                avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

            } else {
                avatar = null;
                avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

            }
        } else {
            avatar = null;
            avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

        }

        if (partida_local) {
            server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("local_ip", "localhost"));
            server_ip_textfield.setEnabled(false);
            server_port_textfield.setText(Helpers.PROPERTIES.getProperty("local_port", String.valueOf(DEFAULT_PORT)));

            random_combobox.setSelectedIndex(Integer.parseInt(Helpers.PROPERTIES.getProperty("random_generator", String.valueOf(Helpers.TRNG_CSPRNG))) - 1);
            randomorg_apikey.setText(Helpers.PROPERTIES.getProperty("randomorg_api", ""));
            rebuy_checkbox.setSelected(true);
            doblar_checkbox.setSelected(true);
            ((DefaultEditor) doblar_ciegas_spinner_minutos.getEditor()).getTextField().setEditable(false);
            ((DefaultEditor) doblar_ciegas_spinner_manos.getEditor()).getTextField().setEditable(false);

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            float ciega_grande = Float.valueOf(valores[1].trim());

            buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

            ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

            ((DefaultEditor) manos_spinner.getEditor()).getTextField().setEditable(false);

            Helpers.setTranslatedTitle(this, "Crear timba");

            Helpers.updateFonts(this, Helpers.GUI_FONT, null);

            Helpers.translateComponents(this, false);

        } else {
            server_port_textfield.setText(Helpers.PROPERTIES.getProperty("server_port", String.valueOf(DEFAULT_PORT)));
            server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("server_ip", "localhost"));
            Helpers.setTranslatedTitle(this, "Unirme a timba");
            Helpers.updateFonts(this, Helpers.GUI_FONT, null);
            Helpers.translateComponents(this, false);
            config_partida_panel.setVisible(false);
        }

        revalidate();
        repaint();

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);

            setPreferredSize(getSize());

            pack();

            Helpers.windowAutoFitToRemoveHScrollBar(this, scroll_panel.getHorizontalScrollBar(), (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

        }

        init = true;

    }

    private void loadGames() {

        String sql = "SELECT id,start,server FROM game WHERE (ugi IS NOT NULL AND local == 1) ORDER BY start DESC";

        try (Statement statement = Helpers.getSQLITE().createStatement()) {
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
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        } catch (SQLException ex) {
            Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
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

        jPanel2 = new javax.swing.JPanel();
        scroll_panel = new javax.swing.JScrollPane();
        main_panel = new javax.swing.JPanel();
        vamos = new javax.swing.JButton();
        url_panel = new javax.swing.JPanel();
        server_port_puntos = new javax.swing.JLabel();
        server_port_textfield = new javax.swing.JTextField();
        upnp_checkbox = new javax.swing.JCheckBox();
        server_ip_textfield = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        config_partida_panel = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        ciegas_label = new javax.swing.JLabel();
        buyin_label = new javax.swing.JLabel();
        rebuy_checkbox = new javax.swing.JCheckBox();
        buyin_spinner = new javax.swing.JSpinner();
        ciegas_combobox = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        doblar_checkbox = new javax.swing.JCheckBox();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        manos_checkbox = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        random_panel = new javax.swing.JPanel();
        randomorg_label = new javax.swing.JLabel();
        random_label = new javax.swing.JLabel();
        randomorg_apikey = new javax.swing.JTextField();
        random_combobox = new javax.swing.JComboBox<>();
        info_shuffle_icon = new javax.swing.JLabel();
        nick_pass_panel = new javax.swing.JPanel();
        nick = new javax.swing.JTextField();
        nick_label = new javax.swing.JLabel();
        password = new javax.swing.JLabel();
        pass_text = new javax.swing.JPasswordField();
        avatar_label = new javax.swing.JLabel();
        recover_panel = new javax.swing.JPanel();
        recover_checkbox = new javax.swing.JCheckBox();
        recover_checkbox_label = new javax.swing.JLabel();
        game_combo = new javax.swing.JComboBox<>();
        radar_label = new javax.swing.JLabel();
        radar_checkbox = new javax.swing.JCheckBox();
        cancel_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("CoronaPoker - Nueva timba");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        setUndecorated(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowActivated(java.awt.event.WindowEvent evt) {
                formWindowActivated(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
        });

        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));

        scroll_panel.setBorder(null);
        scroll_panel.setDoubleBuffered(true);

        vamos.setBackground(new java.awt.Color(0, 130, 0));
        vamos.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        vamos.setForeground(new java.awt.Color(255, 255, 255));
        vamos.setText("¡VAMOS!");
        vamos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        vamos.setDoubleBuffered(true);
        vamos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vamosActionPerformed(evt);
            }
        });

        server_port_puntos.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        server_port_puntos.setText(":");
        server_port_puntos.setDoubleBuffered(true);

        server_port_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_port_textfield.setDoubleBuffered(true);
        server_port_textfield.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                server_port_textfieldActionPerformed(evt);
            }
        });

        upnp_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        upnp_checkbox.setText("UPnP");
        upnp_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        upnp_checkbox.setDoubleBuffered(true);

        server_ip_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_ip_textfield.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        server_ip_textfield.setText("localhost");
        server_ip_textfield.setDoubleBuffered(true);
        server_ip_textfield.setPreferredSize(new java.awt.Dimension(500, 31));
        server_ip_textfield.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                server_ip_textfieldActionPerformed(evt);
            }
        });
        server_ip_textfield.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                server_ip_textfieldKeyReleased(evt);
            }
        });

        jLabel5.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        jLabel5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/780.png"))); // NOI18N
        jLabel5.setText("Servidor:");
        jLabel5.setDoubleBuffered(true);

        javax.swing.GroupLayout url_panelLayout = new javax.swing.GroupLayout(url_panel);
        url_panel.setLayout(url_panelLayout);
        url_panelLayout.setHorizontalGroup(
            url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(url_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(server_ip_textfield, javax.swing.GroupLayout.DEFAULT_SIZE, 512, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(server_port_puntos)
                .addGap(0, 0, 0)
                .addComponent(server_port_textfield, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upnp_checkbox))
        );
        url_panelLayout.setVerticalGroup(
            url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(server_ip_textfield, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel5))
            .addGroup(url_panelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(upnp_checkbox))
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, url_panelLayout.createSequentialGroup()
                .addGroup(url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(server_port_puntos, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(server_port_textfield, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        config_partida_panel.setOpaque(false);

        jPanel3.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153), 2));

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png"))); // NOI18N
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.setDoubleBuffered(true);

        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png"))); // NOI18N
        buyin_label.setText("Compra inicial (10 a 100 CGs):");
        buyin_label.setToolTipText("[10-100] ciegas grandes");
        buyin_label.setDoubleBuffered(true);

        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        rebuy_checkbox.setToolTipText("Si algún jugador se queda sin fichas");
        rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_checkbox.setDoubleBuffered(true);

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, null, 1));
        buyin_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_spinner.setDoubleBuffered(true);
        buyin_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_spinnerStateChanged(evt);
            }
        });

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,10 / 0,20":"0.10 / 0.20", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,20 / 0,40":"0.20 / 0.40", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,30 / 0,60":"0.30 / 0.60", GameFrame.LANGUAGE.toLowerCase().equals("es")?"0,50 / 1":"0.50 / 1", "1 / 2", "2 / 4", "3 / 6", "5 / 10", "10 / 20", "20 / 40", "30 / 60", "50 / 100", "100 / 200", "200 / 400", "300 / 600", "500 / 1000", "1000 / 2000", "2000 / 4000", "3000 / 6000", "5000 / 10000" }));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ciegas_combobox.setDoubleBuffered(true);
        ciegas_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ciegas_comboboxActionPerformed(evt);
            }
        });

        jLabel2.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png"))); // NOI18N
        jLabel2.setText("Recomprar");
        jLabel2.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel2.setDoubleBuffered(true);
        jLabel2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel2MouseClicked(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        doblar_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        doblar_checkbox.setText("Aumentar ciegas");
        doblar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_checkbox.setDoubleBuffered(true);
        doblar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doblar_checkboxActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_minutos.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        doblar_ciegas_spinner_minutos.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        doblar_ciegas_spinner_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_ciegas_spinner_minutos.setDoubleBuffered(true);

        double_blinds_radio_minutos.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        double_blinds_radio_minutos.setText("Minutos:");
        double_blinds_radio_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_minutos.setDoubleBuffered(true);
        double_blinds_radio_minutos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_minutosActionPerformed(evt);
            }
        });

        double_blinds_radio_manos.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        double_blinds_radio_manos.setText("Manos:");
        double_blinds_radio_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_manos.setDoubleBuffered(true);
        double_blinds_radio_manos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_manosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_manos.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        doblar_ciegas_spinner_manos.setModel(new javax.swing.SpinnerNumberModel(30, 1, null, 1));
        doblar_ciegas_spinner_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_ciegas_spinner_manos.setDoubleBuffered(true);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(doblar_checkbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(double_blinds_radio_minutos)
                            .addComponent(double_blinds_radio_manos))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(doblar_ciegas_spinner_manos)
                            .addComponent(doblar_ciegas_spinner_minutos))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(doblar_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        manos_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.setDoubleBuffered(true);
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        jLabel4.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        jLabel4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        jLabel4.setText("Límite de manos:");
        jLabel4.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel4.setDoubleBuffered(true);
        jLabel4.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel4MouseClicked(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(manos_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manos_spinner, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buyin_label)
                            .addComponent(ciegas_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(ciegas_combobox, 0, 220, Short.MAX_VALUE)
                            .addComponent(buyin_spinner))))
                .addGap(18, 18, 18)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ciegas_label)
                            .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(buyin_label)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(buyin_spinner)
                                .addGap(18, 18, 18)))
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(manos_spinner)
                            .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(manos_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(rebuy_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        random_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153), 2));

        randomorg_label.setFont(new java.awt.Font("Dialog", 3, 12)); // NOI18N
        randomorg_label.setText("RANDOM.ORG API KEY (opcional):");
        randomorg_label.setToolTipText("Random.org API KEY");
        randomorg_label.setDoubleBuffered(true);

        random_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        random_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1058.png"))); // NOI18N
        random_label.setText("ALGORITMO para barajar:");
        random_label.setToolTipText("Click para más info");
        random_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        random_label.setDoubleBuffered(true);
        random_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                random_labelMouseClicked(evt);
            }
        });

        randomorg_apikey.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        randomorg_apikey.setDoubleBuffered(true);

        random_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        random_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        random_combobox.setDoubleBuffered(true);
        random_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                random_comboboxActionPerformed(evt);
            }
        });

        info_shuffle_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/info.png"))); // NOI18N
        info_shuffle_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        info_shuffle_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                info_shuffle_iconMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout random_panelLayout = new javax.swing.GroupLayout(random_panel);
        random_panel.setLayout(random_panelLayout);
        random_panelLayout.setHorizontalGroup(
            random_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(random_panelLayout.createSequentialGroup()
                .addGroup(random_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(random_panelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(info_shuffle_icon)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(random_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(random_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(random_panelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(randomorg_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(randomorg_apikey, javax.swing.GroupLayout.PREFERRED_SIZE, 436, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        random_panelLayout.setVerticalGroup(
            random_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(random_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(random_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(random_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(random_label)
                    .addComponent(info_shuffle_icon, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(random_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(randomorg_label)
                    .addComponent(randomorg_apikey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout config_partida_panelLayout = new javax.swing.GroupLayout(config_partida_panel);
        config_partida_panel.setLayout(config_partida_panelLayout);
        config_partida_panelLayout.setHorizontalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(random_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        config_partida_panelLayout.setVerticalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(random_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        nick.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        nick.setDoubleBuffered(true);
        nick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nickActionPerformed(evt);
            }
        });

        nick_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        nick_label.setText("Nick:");
        nick_label.setToolTipText("Haz click para cambiar el avatar");
        nick_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        nick_label.setDoubleBuffered(true);
        nick_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                nick_labelMouseClicked(evt);
            }
        });

        password.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        password.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lock.png"))); // NOI18N
        password.setText("Password:");
        password.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        password.setDoubleBuffered(true);

        pass_text.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        pass_text.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pass_textActionPerformed(evt);
            }
        });

        avatar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar_label.setDoubleBuffered(true);
        avatar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                avatar_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout nick_pass_panelLayout = new javax.swing.GroupLayout(nick_pass_panel);
        nick_pass_panel.setLayout(nick_pass_panelLayout);
        nick_pass_panelLayout.setHorizontalGroup(
            nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_pass_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(password)
                    .addComponent(nick_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nick)
                    .addComponent(pass_text))
                .addGap(0, 0, 0))
        );
        nick_pass_panelLayout.setVerticalGroup(
            nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_pass_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(avatar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(nick_pass_panelLayout.createSequentialGroup()
                        .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(nick_label)
                            .addComponent(nick, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(nick_pass_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(password)
                            .addComponent(pass_text, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
        );

        recover_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recover_checkbox.setToolTipText("El MODO RECUPERACIÓN permite arrancar una timba que se interrumpió previamente");
        recover_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recover_checkbox.setDoubleBuffered(true);
        recover_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recover_checkboxActionPerformed(evt);
            }
        });

        recover_checkbox_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recover_checkbox_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/dealer.png"))); // NOI18N
        recover_checkbox_label.setText("CONTINUAR TIMBA ANTERIOR:");
        recover_checkbox_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recover_checkbox_label.setDoubleBuffered(true);
        recover_checkbox_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                recover_checkbox_labelMouseClicked(evt);
            }
        });

        game_combo.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        game_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_combo.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                game_comboItemStateChanged(evt);
            }
        });

        radar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        radar_label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/shield.png")).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)));
        radar_label.setText("RADAR");
        radar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        radar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                radar_labelMouseClicked(evt);
            }
        });

        radar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        radar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radar_checkboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout recover_panelLayout = new javax.swing.GroupLayout(recover_panel);
        recover_panel.setLayout(recover_panelLayout);
        recover_panelLayout.setHorizontalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addComponent(radar_checkbox)
                .addGap(0, 0, 0)
                .addComponent(radar_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(recover_checkbox)
                .addGap(0, 0, 0)
                .addComponent(recover_checkbox_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        recover_panelLayout.setVerticalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addGroup(recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(radar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(recover_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(radar_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(recover_checkbox_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(game_combo))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        cancel_button.setBackground(new java.awt.Color(204, 0, 0));
        cancel_button.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("CANCELAR");
        cancel_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        cancel_button.setDoubleBuffered(true);
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout main_panelLayout = new javax.swing.GroupLayout(main_panel);
        main_panel.setLayout(main_panelLayout);
        main_panelLayout.setHorizontalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(url_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(nick_pass_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(recover_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(main_panelLayout.createSequentialGroup()
                .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE))
        );
        main_panelLayout.setVerticalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(main_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(url_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(recover_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(nick_pass_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );

        scroll_panel.setViewportView(main_panel);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void vamosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vamosActionPerformed
        // TODO add your handling code here:
        vamos.setEnabled(false);

        if (update) {

            if (this.manos_checkbox.isSelected()) {

                GameFrame.MANOS = (int) this.manos_spinner.getValue();

            } else {

                GameFrame.MANOS = -1;
            }

            GameFrame.REBUY = this.rebuy_checkbox.isSelected();

            GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

            String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            GameFrame.CIEGA_GRANDE = Float.valueOf(valores_ciegas[1].trim());

            GameFrame.CIEGA_PEQUEÑA = Float.valueOf(valores_ciegas[0].trim());

            if (this.doblar_checkbox.isSelected()) {

                if (this.double_blinds_radio_minutos.isSelected()) {
                    GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_minutos.getValue();
                    GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                } else {
                    GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_manos.getValue();
                    GameFrame.CIEGAS_DOUBLE_TYPE = 2;
                }
            } else {
                GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                GameFrame.CIEGAS_DOUBLE = 0;
            }

            this.dialog_ok = true;

            setVisible(false);

            WaitingRoomFrame.getInstance().pack();

        } else {

            if (!this.nick.getText().trim().isEmpty() && !this.server_ip_textfield.getText().trim().isEmpty() && !this.server_port_textfield.getText().trim().isEmpty()) {

                vamos.setEnabled(false);

                Audio.playWavResource("misc/laser.wav");

                String elnick = this.nick.getText().trim().replaceAll("\\$", "");

                Helpers.PROPERTIES.setProperty("nick", elnick);

                if (this.partida_local) {
                    Helpers.PROPERTIES.setProperty("local_ip", this.server_ip_textfield.getText().trim());
                } else {

                    Helpers.PROPERTIES.setProperty("server_ip", this.server_ip_textfield.getText().trim());

                    if (SERVER_HISTORY_QUEUE.contains(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim())) {

                        SERVER_HISTORY_QUEUE.remove(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim());
                    }

                    SERVER_HISTORY_QUEUE.add(this.server_ip_textfield.getText().trim() + ":" + this.server_port_textfield.getText().trim());

                    Helpers.PROPERTIES.setProperty("server_history", getServerHistoryString());
                }

                Helpers.PROPERTIES.setProperty(this.partida_local ? "local_port" : "server_port", this.server_port_textfield.getText().trim());

                if (this.config_partida_panel.isVisible()) {

                    Helpers.DECK_RANDOM_GENERATOR = this.random_combobox.getSelectedIndex() + 1;
                    Helpers.RANDOM_ORG_APIKEY = this.randomorg_apikey.getText().trim();

                    Helpers.PROPERTIES.setProperty("random_generator", String.valueOf(Helpers.DECK_RANDOM_GENERATOR));
                    Helpers.PROPERTIES.setProperty("randomorg_api", this.randomorg_apikey.getText().trim());
                }

                if (this.avatar != null) {
                    Helpers.PROPERTIES.setProperty("avatar", this.avatar.getAbsolutePath());
                } else {
                    Helpers.PROPERTIES.setProperty("avatar", "");
                }

                Helpers.savePropertiesFile();

                GameFrame.RADAR_AVAILABLE = radar_checkbox.isSelected();

                GameFrame.setRECOVER(this.recover_checkbox.isSelected());

                if (GameFrame.RECOVER) {
                    GameFrame.RECOVER_ID = (int) game.get((String) game_combo.getSelectedItem()).get("id");
                }

                if (this.manos_checkbox.isSelected()) {

                    GameFrame.MANOS = (int) this.manos_spinner.getValue();
                } else {
                    GameFrame.MANOS = -1;
                }

                GameFrame.REBUY = this.rebuy_checkbox.isSelected();

                GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

                String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

                GameFrame.CIEGA_GRANDE = Float.parseFloat(valores_ciegas[1].trim());

                GameFrame.CIEGA_PEQUEÑA = Float.parseFloat(valores_ciegas[0].trim());

                if (this.doblar_checkbox.isSelected()) {

                    if (this.double_blinds_radio_minutos.isSelected()) {
                        GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_minutos.getValue();
                        GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                    } else {
                        GameFrame.CIEGAS_DOUBLE = (int) this.doblar_ciegas_spinner_manos.getValue();
                        GameFrame.CIEGAS_DOUBLE_TYPE = 2;
                    }
                } else {
                    GameFrame.CIEGAS_DOUBLE_TYPE = 1;
                    GameFrame.CIEGAS_DOUBLE = 0;
                }

                this.dialog_ok = true;

                WaitingRoomFrame espera = new WaitingRoomFrame(partida_local, elnick, server_ip_textfield.getText().trim() + ":" + server_port_textfield.getText().trim(), avatar, pass_text.getPassword().length == 0 ? null : new String(pass_text.getPassword()), upnp_checkbox.isSelected());

                WaitingRoomFrame.setInstance(espera);

                espera.setLocationRelativeTo(this);

                setVisible(false);

                espera.setVisible(true);

            } else {
                Helpers.mostrarMensajeError(getContentPane(), "Te falta algún campo obligatorio por completar");
            }

        }

    }//GEN-LAST:event_vamosActionPerformed

    private void random_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_random_comboboxActionPerformed
        // TODO add your handling code here:

        if (!update) {
            Helpers.DECK_RANDOM_GENERATOR = this.random_combobox.getSelectedIndex() + 1;

            this.randomorg_label.setEnabled(Helpers.DECK_RANDOM_GENERATOR <= Helpers.TRNG);
            this.randomorg_apikey.setEnabled(Helpers.DECK_RANDOM_GENERATOR <= Helpers.TRNG);

            if (Helpers.DECK_RANDOM_GENERATOR <= Helpers.TRNG) {
                this.randomorg_apikey.setText(Helpers.PROPERTIES.getProperty("randomorg_api", ""));
                Helpers.RANDOM_ORG_APIKEY = Helpers.PROPERTIES.getProperty("randomorg_api", "");
            }

            pack();
        }
    }//GEN-LAST:event_random_comboboxActionPerformed

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doblar_checkboxActionPerformed
        // TODO add your handling code here:
        this.doblar_ciegas_spinner_minutos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_minutos.isSelected());
        this.doblar_ciegas_spinner_manos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_manos.isSelected());
        this.double_blinds_radio_manos.setEnabled(this.doblar_checkbox.isSelected());
        this.double_blinds_radio_minutos.setEnabled(this.doblar_checkbox.isSelected());
    }//GEN-LAST:event_doblar_checkboxActionPerformed

    private void nick_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_nick_labelMouseClicked
        // TODO add your handling code here:

        if (SwingUtilities.isRightMouseButton(evt)) {
            this.avatar = null;

            avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());
        } else {
            JFileChooser fileChooser = new JFileChooser();

            FileFilter imageFilter = new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes());

            fileChooser.setFileFilter(imageFilter);

            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

            int result = fileChooser.showOpenDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();

                if (selectedFile.length() > NewGameDialog.AVATAR_MAX_FILESIZE * 1024) {
                    Helpers.mostrarMensajeError(getContentPane(), "MAX: " + NewGameDialog.AVATAR_MAX_FILESIZE + " KB");
                } else {
                    this.avatar = selectedFile;

                    avatar_label.setPreferredSize(new Dimension(nick_pass_panel.getHeight(), nick_pass_panel.getHeight()));
                    Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), nick_pass_panel.getHeight(), nick_pass_panel.getHeight());

                }

            }

        }
    }//GEN-LAST:event_nick_labelMouseClicked

    private void recover_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recover_checkboxActionPerformed
        // TODO add your handling code here:

        if (this.recover_checkbox.isSelected()) {

            if (game_combo.getModel().getSize() == 0) {
                loadGames();
            }

            if (game_combo.getModel().getSize() > 0) {

                this.game_combo.setEnabled(true);

                this.buyin_spinner.setEnabled(false);

                this.buyin_label.setEnabled(false);

                this.ciegas_label.setEnabled(false);

                this.ciegas_combobox.setEnabled(false);

                this.doblar_ciegas_spinner_minutos.setEnabled(false);

                this.double_blinds_radio_minutos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_checkbox.setEnabled(false);

                this.recover_checkbox_label.setOpaque(true);

                this.recover_checkbox_label.setBackground(Color.YELLOW);

                String[] parts = ((String) this.game_combo.getSelectedItem()).split(" @ ");

                this.nick.setText(parts[0]);

                this.nick.setEnabled(false);

                pack();

                if (!this.force_recover) {

                    Helpers.mostrarMensajeInformativo(this, "En el <b>MODO RECUPERACIÓN</b> se continuará la timba anterior desde donde se paró:\n\n1) Es <b>OBLIGATORIO</b> que los jugadores antiguos usen los <b>MISMOS NICKS</b>.\n\n2) Para poder continuar desde el <b>PUNTO EXACTO</b> de la mano es <b>OBLIGATORIO</b> que se conecten <b>TODOS</b> los jugadores antiguos. Si esto no fuera posible, se \"perderá\" la mano que estaba en curso cuando se interrumpió la timba.\n\n3) Está permitido que se unan a la timba <b>jugadores nuevos</b> (estarán la primera mano de espectadores).", "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(getClass().getResource("/images/action/robot.png")));
                }
            } else {

                this.recover_checkbox_label.setOpaque(false);
                this.recover_checkbox_label.setBackground(null);
                this.recover_checkbox.setSelected(false);
                this.game_combo.setEnabled(false);
                this.recover_checkbox.setEnabled(false);
                Helpers.mostrarMensajeError(this, "NO HAY TIMBAS QUE SE PUEDAN CONTINUAR");

                pack();

            }

        } else {
            this.game_combo.setEnabled(false);

            this.buyin_spinner.setEnabled(true);

            this.buyin_label.setEnabled(true);

            this.ciegas_label.setEnabled(true);

            this.ciegas_combobox.setEnabled(true);

            this.doblar_checkbox.setEnabled(true);

            this.recover_checkbox_label.setOpaque(false);
            this.recover_checkbox_label.setBackground(null);

            if (this.doblar_checkbox.isSelected()) {

                this.double_blinds_radio_minutos.setEnabled(true);

                this.double_blinds_radio_manos.setEnabled(true);

                this.doblar_ciegas_spinner_manos.setEnabled(this.double_blinds_radio_manos.isSelected());

                this.doblar_ciegas_spinner_minutos.setEnabled(this.double_blinds_radio_minutos.isSelected());

            } else {
                this.double_blinds_radio_minutos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.doblar_ciegas_spinner_minutos.setEnabled(false);
            }

            this.nick.setEnabled(true);

            pack();
        }

    }//GEN-LAST:event_recover_checkboxActionPerformed

    private void pass_textActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pass_textActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_pass_textActionPerformed

    private void game_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_game_comboItemStateChanged
        // TODO add your handling code here:

        if (this.recover_checkbox.isSelected()) {

            String[] parts = ((String) this.game_combo.getSelectedItem()).split(" @ ");

            this.nick.setText(parts[0]);

            this.nick.setEnabled(false);
        }

        pack();
    }//GEN-LAST:event_game_comboItemStateChanged

    private void server_port_textfieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_server_port_textfieldActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_server_port_textfieldActionPerformed

    private void double_blinds_radio_minutosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_double_blinds_radio_minutosActionPerformed
        // TODO add your handling code here:

        if (this.double_blinds_radio_minutos.isSelected()) {
            this.doblar_ciegas_spinner_minutos.setEnabled(true);
            this.double_blinds_radio_manos.setSelected(false);
            this.doblar_ciegas_spinner_manos.setEnabled(false);
        } else {
            this.double_blinds_radio_minutos.setSelected(true);
        }

    }//GEN-LAST:event_double_blinds_radio_minutosActionPerformed

    private void double_blinds_radio_manosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_double_blinds_radio_manosActionPerformed
        // TODO add your handling code here:

        if (this.double_blinds_radio_manos.isSelected()) {
            this.doblar_ciegas_spinner_manos.setEnabled(true);
            this.double_blinds_radio_minutos.setSelected(false);
            this.doblar_ciegas_spinner_minutos.setEnabled(false);
        } else {
            this.double_blinds_radio_manos.setSelected(true);
        }
    }//GEN-LAST:event_double_blinds_radio_manosActionPerformed

    private void server_ip_textfieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_server_ip_textfieldKeyReleased
        // TODO add your handling code here:

        if (!SERVER_HISTORY_QUEUE.isEmpty()) {

            if (evt.getKeyCode() == KeyEvent.VK_UP && conta_history <= SERVER_HISTORY_QUEUE.size() - 2) {

                conta_history++;

                String[] history = SERVER_HISTORY_QUEUE.toArray(new String[0]);

                String[] parts = history[conta_history].split(":");

                server_ip_textfield.setText(parts[0]);

                try {
                    ((AbstractDocument) server_port_textfield.getDocument()).remove(0, ((AbstractDocument) server_port_textfield.getDocument()).getLength());
                    ((AbstractDocument) server_port_textfield.getDocument()).insertString(0, parts[1], null);
                } catch (BadLocationException ex) {
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else if (evt.getKeyCode() == KeyEvent.VK_DOWN && conta_history >= 1) {

                conta_history--;

                String[] history = SERVER_HISTORY_QUEUE.toArray(new String[0]);

                String[] parts = history[conta_history].split(":");

                server_ip_textfield.setText(parts[0]);

                try {
                    ((AbstractDocument) server_port_textfield.getDocument()).remove(0, ((AbstractDocument) server_port_textfield.getDocument()).getLength());
                    ((AbstractDocument) server_port_textfield.getDocument()).insertString(0, parts[1], null);
                } catch (BadLocationException ex) {
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
    }//GEN-LAST:event_server_ip_textfieldKeyReleased

    private void server_ip_textfieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_server_ip_textfieldActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_server_ip_textfieldActionPerformed

    private void ciegas_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ciegas_comboboxActionPerformed
        // TODO add your handling code here:

        if (init) {

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            float ciega_grande = Float.valueOf(valores[1].trim());

            buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

            ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

            pack();

        }
    }//GEN-LAST:event_ciegas_comboboxActionPerformed

    private void buyin_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_spinnerStateChanged
        // TODO add your handling code here:
        Audio.playWavResource("misc/cash_register.wav");
        // updateCiegasLabel();
    }//GEN-LAST:event_buyin_spinnerStateChanged

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (isModal()) {
            try {
                Init.CURRENT_MODAL_DIALOG.removeLast();
            } catch (Exception ex) {
            }
        }
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowActivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowActivated
        // TODO add your handling code here:
        if (isModal()) {
            Init.CURRENT_MODAL_DIALOG.add(this);
        }

        if (force_recover) {
            vamosActionPerformed(null);
        }

    }//GEN-LAST:event_formWindowActivated

    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manos_checkboxActionPerformed
        // TODO add your handling code here:
        this.manos_spinner.setEnabled(this.manos_checkbox.isSelected());
    }//GEN-LAST:event_manos_checkboxActionPerformed

    private void recover_checkbox_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_recover_checkbox_labelMouseClicked
        // TODO add your handling code here:
        recover_checkbox.doClick();
    }//GEN-LAST:event_recover_checkbox_labelMouseClicked

    private void jLabel2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel2MouseClicked
        // TODO add your handling code here:
        rebuy_checkbox.doClick();
    }//GEN-LAST:event_jLabel2MouseClicked

    private void jLabel4MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel4MouseClicked
        // TODO add your handling code here:
        manos_checkbox.doClick();
    }//GEN-LAST:event_jLabel4MouseClicked

    private void nickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nickActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_nickActionPerformed

    private void avatar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatar_labelMouseClicked
        // TODO add your handling code here:
        this.nick_labelMouseClicked(evt);
    }//GEN-LAST:event_avatar_labelMouseClicked

    private void random_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_random_labelMouseClicked
        // TODO add your handling code here:
        Helpers.mostrarMensajeInformativo(this, "Cuando empecé a desarrollar el juego, una de las cosas que me preocupaba era conseguir barajar las cartas de la mejor forma posible teniendo en cuenta la mala fama que tienen los ordenadores generando números aleatorios. Entre mis amigos había coñas con este asunto y yo quería zanjar cualquier suspicacia.\n"
                + "<h2>¿En qué consisten los modos de barajado de CoronaPoker?</h2>"
                + "<b>MODO NORMAL:</b> este modo utiliza el algoritmo de Fisher-Yates para mezclar una baraja (por defecto ordenada) empleando para ello un generador de números PSEUDOALEATORIOS criptográficamente seguro basado en el algoritmo HASH DRBG SHA-512. Este método de barajar es capaz (teóricamente) de generar de forma impredecible y equiprobable cualquiera de las permutaciones posibles de una baraja de póker, a saber:\n52! = 80 658 175 170 943 878 571 660 636 856 403 766 975 289 505 440 883 277 824 000 000 000 000\n"
                + "\n"
                + "<b>MODO CASINO:</b> este modo utiliza la API de Random.org para obtener una permutación de 52 elementos. La aleatoriedad de Random.org proviene de un generador de números ALEATORIOS AUTÉNTICOS obtenidos a partir de RUIDO ATMOSFÉRICO, siendo por tanto capaz de generar cualquiera de las permutaciones posibles de una baraja de póker de forma impredecible y equiprobable.\n"
                + "\n"
                + "<b>MODO PARANOICO:</b> este modo es un HÍBRIDO entre el MODO CASINO y el NORMAL. Primero se baraja usando el MODO CASINO y después se vuelve a barajar usando el MODO NORMAL. De esta forma, en un hipotético y MUY improbable caso de que la permutación devuelta por Random.org no fuera totalmente aleatoria por cualquier motivo (fortuito o malicioso), al volver a barajar quedaría neutralizado.", "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(Init.class.getResource("/images/dados.png")));
    }//GEN-LAST:event_random_labelMouseClicked

    private void radar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_radar_labelMouseClicked
        // TODO add your handling code here:
        radar_checkbox.doClick();
    }//GEN-LAST:event_radar_labelMouseClicked

    private void radar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radar_checkboxActionPerformed
        // TODO add your handling code here:

        if (radar_checkbox.isSelected()) {
            Helpers.mostrarMensajeInformativo(this, "Esta funcionalidad permite a cualquier jugador obtener un captura de la pantalla y la lista de procesos de otro jugador, si sospecha que está haciendo trampas o ayudándose de software de terceros para jugar. Como anfitrión, ten en cuenta por favor las implicaciones de privacidad que esto puede suponer antes de activar esta opción (no se puede cambiar durante la partida).\n\nNota: esta funcionalidad es bastante dependiente de la plataforma, por lo que no está garantizado que funcione perfectamente en todos los clientes.", "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(Init.class.getResource("/images/shield.png")));
        }

        radar_label.setEnabled(radar_checkbox.isSelected());
        radar_label.setToolTipText(Translator.translate(radar_checkbox.isSelected() ? "Informes ANTI-TRAMPAS activados" : "Informes ANTI-TRAMPAS desactivados"));
        radar_checkbox.setToolTipText(radar_label.getToolTipText());

    }//GEN-LAST:event_radar_checkboxActionPerformed

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        // TODO add your handling code here:
        dispose();
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void info_shuffle_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_info_shuffle_iconMouseClicked
        // TODO add your handling code here:
        random_labelMouseClicked(evt);
    }//GEN-LAST:event_info_shuffle_iconMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JButton cancel_button;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JLabel ciegas_label;
    private javax.swing.JPanel config_partida_panel;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JSpinner doblar_ciegas_spinner_manos;
    private javax.swing.JSpinner doblar_ciegas_spinner_minutos;
    private javax.swing.JRadioButton double_blinds_radio_manos;
    private javax.swing.JRadioButton double_blinds_radio_minutos;
    private javax.swing.JComboBox<String> game_combo;
    private javax.swing.JLabel info_shuffle_icon;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel main_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JTextField nick;
    private javax.swing.JLabel nick_label;
    private javax.swing.JPanel nick_pass_panel;
    private javax.swing.JPasswordField pass_text;
    private javax.swing.JLabel password;
    private javax.swing.JCheckBox radar_checkbox;
    private javax.swing.JLabel radar_label;
    private javax.swing.JComboBox<JLabel> random_combobox;
    private javax.swing.JLabel random_label;
    private javax.swing.JPanel random_panel;
    private javax.swing.JTextField randomorg_apikey;
    private javax.swing.JLabel randomorg_label;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JCheckBox recover_checkbox;
    private javax.swing.JLabel recover_checkbox_label;
    private javax.swing.JPanel recover_panel;
    private javax.swing.JScrollPane scroll_panel;
    private javax.swing.JTextField server_ip_textfield;
    private javax.swing.JLabel server_port_puntos;
    private javax.swing.JTextField server_port_textfield;
    private javax.swing.JCheckBox upnp_checkbox;
    private javax.swing.JPanel url_panel;
    private javax.swing.JButton vamos;
    // End of variables declaration//GEN-END:variables
}
