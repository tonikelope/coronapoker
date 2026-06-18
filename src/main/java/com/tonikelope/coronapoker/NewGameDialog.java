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
import java.awt.Rectangle;
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

    private int getCurrentBotLevel() {

        if (Bot.DIFFICULTY == Bot.Difficulty.EASY) {
            return 0;
        }

        if (Bot.DIFFICULTY == Bot.Difficulty.MEDIUM) {
            return 1;
        }

        if (Bot.DIFFICULTY == Bot.Difficulty.HARD) {
            return 2;
        }

        return 1;
    }

    public boolean isDialog_ok() {
        return dialog_ok;
    }

    public NewGameDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();

        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());

        Helpers.attachPasswordStrengthHint(pass_text);
        Helpers.attachPasswordRevealButton(pass_text);

        update = true;

        partida_local = (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().isServer());

        titulo_ventana.setText(Translator.translate("game.modificar_opciones_de_la_timba"));

        recover_checkbox_label.setText(Translator.translate("game.continuar_timba_anterior"));

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);
        url_panel.setVisible(false);
        nick_pass_panel.setVisible(false);
        config_partida_panel.setVisible(true);

        this.recover_panel.setVisible(false);
        this.vamos.setText(Translator.translate("ui.guardar"));

        if (partida_local) {
            DefaultComboBoxModel<String> bots_combobox_model = new DefaultComboBoxModel<>();

            bots_combobox_model.addElement(Translator.translate("ui.bots_facil"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_media"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_dificil"));

            bots_combobox.setModel(bots_combobox_model);

            bots_combobox.setSelectedIndex(this.getCurrentBotLevel());

            bots_label.setText(Translator.translate("ui.bots_dificultad"));

        } else {
            bots_label.setVisible(false);
            bots_combobox.setVisible(false);
        }

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

        this.bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
        this.bot_rebuy_checkbox.setEnabled(GameFrame.REBUY);

        this.fixed_buyin_checkbox.setSelected(GameFrame.FIXED_BUYIN);
        this.buyin_spinner.setEnabled(GameFrame.FIXED_BUYIN);

        this.rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
        this.rebuy_limit_checkbox.setEnabled(GameFrame.REBUY);
        this.rebuy_limit_spinner.setEnabled(GameFrame.REBUY && GameFrame.REBUY_LIMIT > 0);
        this.rebuy_limit_spinner.setModel(new SpinnerNumberModel(GameFrame.REBUY_LIMIT > 0 ? GameFrame.REBUY_LIMIT : 3, 1, null, 1));
        ((DefaultEditor) this.rebuy_limit_spinner.getEditor()).getTextField().setEditable(false);

        this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
        this.blind_cap_checkbox.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
        this.blind_cap_spinner.setEnabled(GameFrame.CIEGAS_DOUBLE > 0 && GameFrame.BLIND_CAP > 0f);

        // Selector de estructura: refleja la estructura activa (si la hay) y deja el
        // combo de niveles con sus ciegas, para que la búsqueda de abajo seleccione
        // el nivel actual también en estructuras personalizadas.
        initBlindStructureUI();

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

        modelBlindCapSpinner(blindCapDoublingsFromCap());

        Helpers.setTranslatedTitle(this, update ? "update.actualizar_timba" : (partida_local ? "ui.crear_timba" : "ui.unirme_a_timba"));
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

    public void setPass(String password) {
        pass_text.setEnabled(true);
        pass_text.setText(password);

    }

    /**
     * Creates new form CrearTimba
     */
    public NewGameDialog(java.awt.Frame parent, boolean modal, boolean loc) {
        super(parent, modal);

        initComponents();

        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());

        Helpers.attachPasswordStrengthHint(pass_text);
        Helpers.attachPasswordRevealButton(pass_text);

        // Timba nueva: arranca siempre en "Por defecto" (ignora cualquier estructura
        // que quedara activa de una partida anterior). Si el usuario elige una
        // personalizada, applySelectedStructure repuebla el combo de niveles.
        pending_structure = null;
        populateStructureCombo(null);

        titulo_ventana.setText(loc ? Translator.translate("game.crear_timba") : Translator.translate("game.unirme_a_timba"));

        recover_checkbox_label.setText(Translator.translate("game.continuar_timba_anterior"));

        partida_local = loc;

        scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        if (partida_local) {
            DefaultComboBoxModel<String> bots_combobox_model = new DefaultComboBoxModel<>();

            bots_combobox_model.addElement(Translator.translate("ui.bots_facil"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_media"));

            bots_combobox_model.addElement(Translator.translate("ui.bots_dificil"));

            bots_combobox.setModel(bots_combobox_model);

            bots_combobox.setSelectedIndex(this.getCurrentBotLevel());

            bots_label.setText(Translator.translate("ui.bots_dificultad"));

        } else {
            bots_label.setVisible(false);
            bots_combobox.setVisible(false);
        }

        game_combo.setEnabled(false);

        password.setEnabled(false);

        manos_spinner.setEnabled(false);

        double_blinds_radio_manos.setSelected(true);

        double_blinds_radio_minutos.setSelected(false);

        doblar_ciegas_spinner_minutos.setEnabled(false);

        if (partida_local) {
            upnp_checkbox.setSelected(Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("upnp", "true")));
        } else {
            upnp_checkbox.setVisible(false);
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

            rebuy_checkbox.setSelected(true);
            doblar_checkbox.setSelected(false);
            bot_rebuy_checkbox.setSelected(true);
            fixed_buyin_checkbox.setSelected(true);
            buyin_spinner.setEnabled(true);
            double_blinds_radio_minutos.setEnabled(false);
            double_blinds_radio_manos.setEnabled(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            blind_cap_checkbox.setSelected(false);
            blind_cap_checkbox.setEnabled(false);
            blind_cap_spinner.setEnabled(false);
            rebuy_limit_checkbox.setSelected(false);
            rebuy_limit_spinner.setEnabled(false);
            ((DefaultEditor) blind_cap_spinner.getEditor()).getTextField().setEditable(false);
            ((DefaultEditor) rebuy_limit_spinner.getEditor()).getTextField().setEditable(false);
            ((DefaultEditor) doblar_ciegas_spinner_minutos.getEditor()).getTextField().setEditable(false);
            ((DefaultEditor) doblar_ciegas_spinner_manos.getEditor()).getTextField().setEditable(false);

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            float ciega_grande = Float.valueOf(valores[1].trim());

            buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

            ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

            modelBlindCapSpinner(5);

            ((DefaultEditor) manos_spinner.getEditor()).getTextField().setEditable(false);

            Helpers.setTranslatedTitle(this, "ui.crear_timba");

            Helpers.updateFonts(this, Helpers.GUI_FONT, null);

            Helpers.translateComponents(this, false);

        } else {
            server_port_textfield.setText(Helpers.PROPERTIES.getProperty("server_port", String.valueOf(DEFAULT_PORT)));
            server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("server_ip", "localhost"));
            Helpers.setTranslatedTitle(this, "ui.unirme_a_timba");
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

    private void loadLastGame() {

        String sql = "SELECT id,start,server,recover_settings FROM game WHERE (ugi IS NOT NULL AND local == 1) ORDER BY start DESC LIMIT 1";

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
                    String settings = rs.getString("recover_settings");
                    map.put("recover_settings", settings);
                    GameFrame.applyRecoverSettings(settings);
                    if (partida_local) {
                        bots_combobox.setSelectedIndex(getCurrentBotLevel());
                    }

                    this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
                    modelBlindCapSpinner(blindCapDoublingsFromCap());
                    this.rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
                    if (GameFrame.REBUY_LIMIT > 0) {
                        this.rebuy_limit_spinner.setValue(GameFrame.REBUY_LIMIT);
                    }
                    this.bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
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
        vamos.putClientProperty("i18n.key", "ui.vamos");
        url_panel = new javax.swing.JPanel();
        server_port_puntos = new javax.swing.JLabel();
        server_port_textfield = new javax.swing.JTextField();
        upnp_checkbox = new javax.swing.JCheckBox();
        server_ip_textfield = new javax.swing.JTextField();
        server_label = new javax.swing.JLabel();
        config_partida_panel = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        ciegas_label = new javax.swing.JLabel();
        buyin_label = new javax.swing.JLabel();
        rebuy_checkbox = new javax.swing.JCheckBox();
        buyin_spinner = new javax.swing.JSpinner();
        ciegas_combobox = new javax.swing.JComboBox<>();
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        recomprar_label = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        doblar_checkbox = new javax.swing.JCheckBox();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        manos_checkbox = new javax.swing.JCheckBox();
        limite_manos_label = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        blind_cap_checkbox = new javax.swing.JCheckBox();
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        recompra_panel = new javax.swing.JPanel();
        rebuy_limit_checkbox = new javax.swing.JCheckBox();
        rebuy_limit_spinner = new javax.swing.JSpinner();
        bot_rebuy_checkbox = new javax.swing.JCheckBox();
        fixed_buyin_checkbox = new javax.swing.JCheckBox();
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
        cancel_button = new javax.swing.JButton();
        cancel_button.putClientProperty("i18n.key", "ui.cancelar");
        bots_combobox = new javax.swing.JComboBox<>();
        bots_label = new javax.swing.JLabel();
        titulo_ventana = new javax.swing.JLabel();

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
        vamos.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
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

        server_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        server_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/780.png"))); // NOI18N
        server_label.setText("Servidor:");
        server_label.setDoubleBuffered(true);

        javax.swing.GroupLayout url_panelLayout = new javax.swing.GroupLayout(url_panel);
        url_panel.setLayout(url_panelLayout);
        url_panelLayout.setHorizontalGroup(
            url_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(url_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(server_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(server_ip_textfield, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
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
                .addComponent(server_label))
            .addGroup(url_panelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(upnp_checkbox))
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, url_panelLayout.createSequentialGroup()
                .addComponent(server_port_puntos)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(server_port_textfield)
        );

        server_label.putClientProperty("i18n.key", "ui.servidor");

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
        Helpers.setTranslatedToolTip(rebuy_checkbox, "tooltip.rebuy_description");
        rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_checkbox.setDoubleBuffered(true);
        rebuy_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_checkboxActionPerformed(evt);
            }
        });

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

        estructura_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        estructura_label.setText("Estructura:");
        estructura_label.setDoubleBuffered(true);

        estructura_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        estructura_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        estructura_combobox.setDoubleBuffered(true);
        estructura_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                estructura_comboboxActionPerformed(evt);
            }
        });

        recomprar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recomprar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png"))); // NOI18N
        recomprar_label.setText("Recomprar");
        recomprar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        recomprar_label.setDoubleBuffered(true);
        recomprar_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                recomprar_labelMouseClicked(evt);
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
                            .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
                            .addComponent(doblar_ciegas_spinner_minutos)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(blind_cap_checkbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(blind_cap_label))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(blind_cap_checkbox)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blind_cap_label)
                .addContainerGap())
        );

        doblar_checkbox.putClientProperty("i18n.key", "stats.aumentar_ciegas");
        double_blinds_radio_minutos.putClientProperty("i18n.key", "ui.minutos");
        double_blinds_radio_manos.putClientProperty("i18n.key", "stats.manos");

        manos_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.setDoubleBuffered(true);
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        limite_manos_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        limite_manos_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        limite_manos_label.setText("Límite de manos:");
        limite_manos_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        limite_manos_label.setDoubleBuffered(true);
        limite_manos_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                limite_manos_labelMouseClicked(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        recompra_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        blind_cap_checkbox.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        blind_cap_checkbox.setText("Tope ciega grande");
        blind_cap_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_checkbox.setDoubleBuffered(true);
        blind_cap_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blind_cap_checkboxActionPerformed(evt);
            }
        });
        blind_cap_checkbox.putClientProperty("i18n.key", "blinds.tope_ciega_grande");

        blind_cap_spinner.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        blind_cap_spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, null, 1));
        blind_cap_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_spinner.setDoubleBuffered(true);

        blind_cap_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        blind_cap_label.setText("0 / 0");
        blind_cap_label.setDoubleBuffered(true);

        rebuy_limit_checkbox.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        rebuy_limit_checkbox.setText("Límite recompra por jugador");
        rebuy_limit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_limit_checkbox.setDoubleBuffered(true);
        rebuy_limit_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rebuy_limit_checkboxActionPerformed(evt);
            }
        });
        rebuy_limit_checkbox.putClientProperty("i18n.key", "rebuy.limite_por_jugador");

        rebuy_limit_spinner.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_limit_spinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, null, 1));
        rebuy_limit_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_limit_spinner.setDoubleBuffered(true);

        bot_rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        bot_rebuy_checkbox.setText("Recomprar bots");
        bot_rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bot_rebuy_checkbox.setDoubleBuffered(true);
        bot_rebuy_checkbox.putClientProperty("i18n.key", "rebuy.permitir_bots");

        fixed_buyin_checkbox.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        fixed_buyin_checkbox.setText("Buy-in fijo");
        fixed_buyin_checkbox.setToolTipText("Marcado: todos arrancan con el mismo buy-in. Desmarcado: cada jugador elige su buy-in (10BB-100BB) al entrar al tablero.");
        fixed_buyin_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fixed_buyin_checkbox.setDoubleBuffered(true);
        fixed_buyin_checkbox.putClientProperty("i18n.key", "newgame.buyin_fijo");
        fixed_buyin_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixed_buyin_checkboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout recompra_panelLayout = new javax.swing.GroupLayout(recompra_panel);
        recompra_panel.setLayout(recompra_panelLayout);
        recompra_panelLayout.setHorizontalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(rebuy_checkbox)
                .addGap(0, 0, 0)
                .addComponent(recomprar_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rebuy_limit_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(bot_rebuy_checkbox)
                .addGap(0, 0, Short.MAX_VALUE)
                .addContainerGap())
        );
        recompra_panelLayout.setVerticalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(rebuy_checkbox)
                    .addComponent(recomprar_label)
                    .addComponent(rebuy_limit_checkbox)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bot_rebuy_checkbox))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(manos_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(limite_manos_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manos_spinner, javax.swing.GroupLayout.DEFAULT_SIZE, 166, Short.MAX_VALUE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buyin_label)
                            .addComponent(ciegas_label)
                            .addComponent(estructura_label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(ciegas_combobox, 0, 220, Short.MAX_VALUE)
                            .addComponent(estructura_combobox, 0, 220, Short.MAX_VALUE)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(buyin_spinner)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(fixed_buyin_checkbox)))))
                .addGap(18, 18, 18)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(recompra_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(estructura_label)
                            .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ciegas_label)
                            .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(buyin_label)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(buyin_spinner)
                                    .addComponent(fixed_buyin_checkbox))
                                .addGap(18, 18, 18)))
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(manos_spinner)
                            .addComponent(limite_manos_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(manos_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(recompra_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");
        estructura_label.putClientProperty("i18n.key", "blinds.estructura");
        buyin_label.putClientProperty("i18n.key", "ui.compra_inicial_10_a_100");
        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");
        limite_manos_label.putClientProperty("i18n.key", "game.limite_de_manos");

        javax.swing.GroupLayout config_partida_panelLayout = new javax.swing.GroupLayout(config_partida_panel);
        config_partida_panel.setLayout(config_partida_panelLayout);
        config_partida_panelLayout.setHorizontalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        config_partida_panelLayout.setVerticalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        nick.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        nick.setDoubleBuffered(true);
        nick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nickActionPerformed(evt);
            }
        });

        nick_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        Helpers.setTranslatedText(nick_label, "ui.nick");
        Helpers.setTranslatedToolTip(nick_label, "tooltip.change_avatar");
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
        password.putClientProperty("i18n.key", "ui.password");

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
        Helpers.setTranslatedToolTip(recover_checkbox, "tooltip.recovery_description_full");
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

        javax.swing.GroupLayout recover_panelLayout = new javax.swing.GroupLayout(recover_panel);
        recover_panel.setLayout(recover_panelLayout);
        recover_panelLayout.setHorizontalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addGap(85, 85, 85)
                .addComponent(recover_checkbox)
                .addGap(0, 0, 0)
                .addComponent(recover_checkbox_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(game_combo, 0, 428, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        recover_panelLayout.setVerticalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addGroup(recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(recover_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(recover_checkbox_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(game_combo))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        cancel_button.setBackground(new java.awt.Color(204, 0, 0));
        cancel_button.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("CANCELAR");
        cancel_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        cancel_button.setDoubleBuffered(true);
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        bots_combobox.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        bots_combobox.setSelectedItem(1);
        bots_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bots_comboboxActionPerformed(evt);
            }
        });

        bots_label.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        bots_label.setText("Dificultad bots:");

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
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, main_panelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bots_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bots_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick_pass_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cancel_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );

        scroll_panel.setViewportView(main_panel);

        titulo_ventana.setBackground(new java.awt.Color(102, 153, 255));
        titulo_ventana.setFont(new java.awt.Font("Dialog", 1, 36)); // NOI18N
        titulo_ventana.setForeground(new java.awt.Color(255, 255, 255));
        titulo_ventana.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titulo_ventana.setText("CREAR TIMBA");
        titulo_ventana.setDoubleBuffered(true);
        titulo_ventana.setOpaque(true);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroll_panel)
                .addContainerGap())
            .addComponent(titulo_ventana, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(titulo_ventana)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(scroll_panel)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void vamosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vamosActionPerformed
        // Guard de re-entrada: en el flujo force_recover, formWindowActivated invoca
        // este método directamente. Si un modal devolviera el foco al diálogo se
        // encolaría una segunda activación que, sin guard, re-entraría y crearía un
        // SEGUNDO WaitingRoomFrame (rama else, update=false). dialog_ok solo se pone
        // a true tras un commit exitoso, así que cortar aquí no afecta a la primera
        // llamada ni a reintentos tras error de validación (dejan dialog_ok=false).
        if (dialog_ok) {
            return;
        }
        vamos.setEnabled(false);

        if (update) {

            if (this.manos_checkbox.isSelected()) {

                GameFrame.MANOS = (int) this.manos_spinner.getValue();

            } else {

                GameFrame.MANOS = -1;
            }

            GameFrame.REBUY = this.rebuy_checkbox.isSelected();

            GameFrame.BOT_REBUY = this.bot_rebuy_checkbox.isSelected();

            GameFrame.REBUY_LIMIT = this.rebuy_limit_checkbox.isSelected() ? (int) this.rebuy_limit_spinner.getValue() : 0;

            GameFrame.BLIND_CAP = this.blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0f;

            GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

            GameFrame.FIXED_BUYIN = this.fixed_buyin_checkbox.isSelected();

            String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            GameFrame.CIEGA_GRANDE = Float.valueOf(valores_ciegas[1].trim());

            GameFrame.CIEGA_PEQUEÑA = Float.valueOf(valores_ciegas[0].trim());

            // Estructura personalizada activa (null = escalera por defecto). Viaja a
            // los clientes y se persiste en recover desde aquí (ver C5/C6).
            GameFrame.ACTIVE_BLIND_STRUCTURE = pending_structure != null ? pending_structure.getLevels() : null;

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

            commitBotDifficultyFromCombo();

            this.dialog_ok = true;

            setVisible(false);

            WaitingRoomFrame.getInstance().pack();

        } else {

            if (!this.nick.getText().trim().isEmpty() && !this.server_ip_textfield.getText().trim().isEmpty() && !this.server_port_textfield.getText().trim().isEmpty()) {

                vamos.setEnabled(false);

                Audio.playWavResource("misc/laser.wav");

                String elnick = this.nick.getText().trim().replaceAll("\\$", "");

                Helpers.PROPERTIES.setProperty("nick", elnick);

                // Identity: load or generate the Ed25519 keypair bound to the nick the user
                // is about to enter the waiting room. Per-nick files in CORONA_DIR let different
                // test instances on the same machine use distinct identities, and switching back
                // to a known nick reloads the existing keypair. Abort the join if storage fails —
                // networked games cannot proceed without a stable identity.
                IdentityManager im = IdentityManager.initializeForNick(elnick);
                if (!im.isReady()) {
                    vamos.setEnabled(true);
                    Helpers.mostrarMensajeError(getContentPane(),
                            Translator.translate("ui.identity.load_error", im.getLoadError()));
                    return;
                }

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

                if (this.avatar != null) {
                    Helpers.PROPERTIES.setProperty("avatar", this.avatar.getAbsolutePath());
                } else {
                    Helpers.PROPERTIES.setProperty("avatar", "");
                }

                Helpers.savePropertiesFile();

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

                GameFrame.BOT_REBUY = this.bot_rebuy_checkbox.isSelected();

                GameFrame.REBUY_LIMIT = this.rebuy_limit_checkbox.isSelected() ? (int) this.rebuy_limit_spinner.getValue() : 0;

                GameFrame.BLIND_CAP = this.blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0f;

                GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

                GameFrame.FIXED_BUYIN = this.fixed_buyin_checkbox.isSelected();

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

                // Issue#9: en recover, BUYIN/CIEGAS/CIEGAS_DOUBLE/REBUY del spinner
                // son los valores por defecto del form (no se cargan desde la timba
                // a continuar — los controles solo se deshabilitan visualmente).
                // Cargar la verdad desde la fila game antes de WaitingRoomFrame +
                // GameFrame para que un late-joiner que se siente en la mesa
                // capture el BUYIN correcto en su slot (RemotePlayer field
                // initializer + loop simetrico setStack/setBuyin en GameFrame
                // constructor).
                if (GameFrame.RECOVER) {
                    GameFrame.applyRecoveredGameStats(GameFrame.RECOVER_ID);
                }

                commitBotDifficultyFromCombo();

                this.dialog_ok = true;

                // Identity: warn the host if the game password is weak.
                // Non-blocking informational popup — the user dismisses with OK and proceeds.
                if (this.partida_local && pass_text.getPassword().length > 0) {
                    String pwd = new String(pass_text.getPassword());
                    int entropyBits = Helpers.estimatePasswordEntropyBits(pwd);
                    if (entropyBits < 60) {
                        Helpers.mostrarMensajeInformativo(
                                getContentPane(),
                                Translator.translate("ui.password_debil_aviso", entropyBits));
                    }
                }

                WaitingRoomFrame espera = new WaitingRoomFrame(partida_local, elnick, server_ip_textfield.getText().trim() + ":" + server_port_textfield.getText().trim(), avatar, pass_text.getPassword().length == 0 ? null : new String(pass_text.getPassword()), upnp_checkbox.isSelected());

                WaitingRoomFrame.setInstance(espera);

                espera.setLocationRelativeTo(this);

                setVisible(false);

                espera.setVisible(true);

            } else {
                Helpers.mostrarMensajeError(getContentPane(), Translator.translate("ui.error.faltan_campos"));
            }

        }

    }//GEN-LAST:event_vamosActionPerformed

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doblar_checkboxActionPerformed
        // TODO add your handling code here:
        this.doblar_ciegas_spinner_minutos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_minutos.isSelected());
        this.doblar_ciegas_spinner_manos.setEnabled(this.doblar_checkbox.isSelected() && this.double_blinds_radio_manos.isSelected());
        this.double_blinds_radio_manos.setEnabled(this.doblar_checkbox.isSelected());
        this.double_blinds_radio_minutos.setEnabled(this.doblar_checkbox.isSelected());
        this.blind_cap_checkbox.setEnabled(this.doblar_checkbox.isSelected());
        this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
    }//GEN-LAST:event_doblar_checkboxActionPerformed

    private void rebuy_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_checkboxActionPerformed
        this.rebuy_limit_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
        this.bot_rebuy_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
    }//GEN-LAST:event_rebuy_checkboxActionPerformed

    private void fixed_buyin_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixed_buyin_checkboxActionPerformed
        // Variable (desmarcado): el buy-in inicial se pide a cada jugador al entrar
        // al tablero, asi que el spinner de aqui no aplica -> deshabilitado.
        this.buyin_spinner.setEnabled(this.fixed_buyin_checkbox.isSelected());
    }//GEN-LAST:event_fixed_buyin_checkboxActionPerformed

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
                    Helpers.mostrarMensajeError(getContentPane(), Translator.translate("ui.max_avatar_size") + " " + NewGameDialog.AVATAR_MAX_FILESIZE + " KB");
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
                loadLastGame();
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

                this.blind_cap_checkbox.setEnabled(false);

                this.blind_cap_spinner.setEnabled(false);

                this.rebuy_checkbox.setEnabled(false);

                this.rebuy_limit_checkbox.setEnabled(false);

                this.rebuy_limit_spinner.setEnabled(false);

                this.bot_rebuy_checkbox.setEnabled(false);

                this.fixed_buyin_checkbox.setEnabled(false);

                this.recover_checkbox_label.setOpaque(true);

                this.recover_checkbox_label.setBackground(Color.YELLOW);

                String[] parts = ((String) this.game_combo.getSelectedItem()).split(" @ ");

                this.nick.setText(parts[0]);

                this.nick.setEnabled(false);

                packPreservingCenter();

                if (!this.force_recover) {

                    Helpers.mostrarMensajeInformativo(this, Translator.translate("player.en_el_bmodo_recuperacionb_se"), "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(getClass().getResource("/images/action/robot.png")));
                }
            } else {

                this.recover_checkbox_label.setOpaque(false);
                this.recover_checkbox_label.setBackground(null);
                this.recover_checkbox.setSelected(false);
                this.game_combo.setEnabled(false);
                this.recover_checkbox.setEnabled(false);
                Helpers.mostrarMensajeError(this, Translator.translate("game.no_hay_timbas_que_se"));

                packPreservingCenter();

            }

        } else {
            this.game_combo.setEnabled(false);

            this.fixed_buyin_checkbox.setEnabled(true);

            // El spinner sigue el modo: deshabilitado si es buy-in variable.
            this.buyin_spinner.setEnabled(this.fixed_buyin_checkbox.isSelected());

            this.buyin_label.setEnabled(true);

            this.ciegas_label.setEnabled(true);

            this.ciegas_combobox.setEnabled(true);

            this.doblar_checkbox.setEnabled(true);

            this.rebuy_checkbox.setEnabled(true);

            this.recover_checkbox_label.setOpaque(false);
            this.recover_checkbox_label.setBackground(null);

            if (this.doblar_checkbox.isSelected()) {

                this.double_blinds_radio_minutos.setEnabled(true);

                this.double_blinds_radio_manos.setEnabled(true);

                this.doblar_ciegas_spinner_manos.setEnabled(this.double_blinds_radio_manos.isSelected());

                this.doblar_ciegas_spinner_minutos.setEnabled(this.double_blinds_radio_minutos.isSelected());

                this.blind_cap_checkbox.setEnabled(true);

                this.blind_cap_spinner.setEnabled(this.blind_cap_checkbox.isSelected());

            } else {
                this.double_blinds_radio_minutos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.doblar_ciegas_spinner_minutos.setEnabled(false);

                this.blind_cap_checkbox.setEnabled(false);

                this.blind_cap_spinner.setEnabled(false);
            }

            if (this.rebuy_checkbox.isSelected()) {
                this.rebuy_limit_checkbox.setEnabled(true);
                this.rebuy_limit_spinner.setEnabled(this.rebuy_limit_checkbox.isSelected());
                this.bot_rebuy_checkbox.setEnabled(true);
            } else {
                this.rebuy_limit_checkbox.setEnabled(false);
                this.rebuy_limit_spinner.setEnabled(false);
                this.bot_rebuy_checkbox.setEnabled(false);
            }

            this.nick.setEnabled(true);

            packPreservingCenter();
        }

    }//GEN-LAST:event_recover_checkboxActionPerformed

    private void pass_textActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pass_textActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_pass_textActionPerformed

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

            float ciega_pequena = Float.valueOf(valores[0].trim());

            float ciega_grande = Float.valueOf(valores[1].trim());

            // Paso del spinner derivado de la magnitud de la ciega pequeña, no del
            // índice del combo: para la escalera por defecto 1-2-3-5 da exactamente
            // lo mismo que el viejo pow(10, floor(index/4)), pero también funciona
            // con estructuras personalizadas de niveles arbitrarios.
            buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (BUYIN_SPINNER_STEP = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(ciega_pequena)) + 1)))));

            ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

            modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());

            packPreservingCenter();

        }
    }//GEN-LAST:event_ciegas_comboboxActionPerformed

    // Estructura de ciegas elegida para esta timba (null = escalera por defecto
    // 1-2-3-5). Determina los niveles del combo de ciegas y, al crear la timba,
    // GameFrame.ACTIVE_BLIND_STRUCTURE.
    private BlindStructure pending_structure = null;

    // Marcadores especiales del combo de estructura; el resto de ítems son nombres
    // de estructuras personalizadas.
    private String item_por_defecto;
    private String item_gestionar;

    // (Re)llena el combo de estructura: "Por defecto" + personalizadas + "Gestionar…".
    // Reselecciona por nombre la que se indique si sigue existiendo. No dispara la
    // lógica de selección (baja init mientras repuebla).
    private void populateStructureCombo(String selectName) {
        boolean prev_init = init;
        init = false;
        try {
            item_por_defecto = Translator.translate("blinds.estructura_por_defecto");
            item_gestionar = Translator.translate("blinds.gestionar");
            estructura_combobox.removeAllItems();
            estructura_combobox.addItem(item_por_defecto);
            for (String name : BlindStructure.loadAll().keySet()) {
                estructura_combobox.addItem(name);
            }
            estructura_combobox.addItem(item_gestionar);
            estructura_combobox.setSelectedItem(selectName != null ? selectName : item_por_defecto);
            if (estructura_combobox.getSelectedItem() == null
                    || item_gestionar.equals(estructura_combobox.getSelectedItem())) {
                estructura_combobox.setSelectedItem(item_por_defecto);
            }
        } finally {
            init = prev_init;
        }
    }

    private void estructura_comboboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (!init) {
            return;
        }
        Object sel = estructura_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        if (sel.equals(item_gestionar)) {
            // Abrir el editor; al cerrar, recargar conservando la estructura activa
            // (si fue borrada/renombrada, caer a "Por defecto").
            String previous = pending_structure != null ? pending_structure.getName() : item_por_defecto;
            BlindStructureManagerDialog mgr = new BlindStructureManagerDialog(this);
            mgr.setVisible(true);
            if (!item_por_defecto.equals(previous) && !BlindStructure.loadAll().containsKey(previous)) {
                previous = item_por_defecto;
            }
            populateStructureCombo(previous);
            applySelectedStructure();
            return;
        }
        applySelectedStructure();
    }

    // Aplica la estructura seleccionada al combo de niveles (ciegas_combobox) y a
    // pending_structure. "Por defecto" => null + escalera 1-2-3-5; personalizada =>
    // sus niveles. Conserva el formato "sb / bb" local (mismo que el combo original).
    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        float[][] levels;
        if (sel == null || sel.equals(item_por_defecto) || sel.equals(item_gestionar)) {
            pending_structure = null;
            levels = BlindStructure.defaultLevels();
        } else {
            BlindStructure bs = BlindStructure.loadAll().get((String) sel);
            pending_structure = bs;
            levels = bs != null ? bs.getLevels() : BlindStructure.defaultLevels();
        }
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            items[i] = Helpers.float2String(levels[i][0]) + " / " + Helpers.float2String(levels[i][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        ciegas_combobox.setSelectedIndex(0);
        // Recalcular buy-in + tope para la nueva escalera (setModel no dispara el
        // listener de forma fiable).
        ciegas_comboboxActionPerformed(null);
    }

    // Inicializa el selector de estructura desde el estado actual
    // (GameFrame.ACTIVE_BLIND_STRUCTURE). Si hay una estructura personalizada activa
    // y sigue existiendo, puebla el combo de niveles con la suya y la selecciona; si
    // no, deja la escalera por defecto. Llamar ANTES de la lógica que busca y
    // selecciona el nivel de ciega actual en el combo.
    private void initBlindStructureUI() {
        pending_structure = null;
        String selectName = null;
        float[][] active = GameFrame.ACTIVE_BLIND_STRUCTURE;
        if (active != null) {
            for (java.util.Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
                if (java.util.Arrays.deepEquals(e.getValue().getLevels(), active)) {
                    pending_structure = e.getValue();
                    selectName = e.getKey();
                    break;
                }
            }
        }
        if (pending_structure != null) {
            float[][] levels = pending_structure.getLevels();
            String[] items = new String[levels.length];
            for (int k = 0; k < levels.length; k++) {
                items[k] = Helpers.float2String(levels[k][0]) + " / " + Helpers.float2String(levels[k][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        }
        populateStructureCombo(selectName);
    }

    // El tope de ciegas se elige como "nº de subidas" (cuántas veces suben las
    // ciegas como máximo, desde las iniciales elegidas). El spinner es ese entero
    // y blind_cap_label muestra al vuelo el nivel resultante. Internamente
    // GameFrame.BLIND_CAP sigue siendo la ciega grande de ese nivel (float), así
    // que la lógica de congelar ciegas / recover / red no cambia.

    // Ciega grande (segundo número) de un item del combo de niveles.
    private float parseBlindLevelBB(String item) {
        return Float.parseFloat(item.replace(",", ".").split("/")[1].trim());
    }

    // Índice del combo del nivel tras n subidas desde las ciegas iniciales, sin
    // pasarse del último nivel disponible.
    private int blindCapTargetIndex(int n) {
        int last = ciegas_combobox.getModel().getSize() - 1;
        return Math.min(Math.max(0, ciegas_combobox.getSelectedIndex()) + n, last);
    }

    // Ciega grande del nivel-tope para el nº de subidas actual (para guardar).
    private float blindCapSelectedBB() {
        return parseBlindLevelBB(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private void updateBlindCapLabel() {
        blind_cap_label.setText(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    // Reconstruye el nº de subidas desde el GameFrame.BLIND_CAP guardado (busca el
    // nivel cuya ciega grande coincide); si no hay tope guardado, el default (5).
    private int blindCapDoublingsFromCap() {
        int initial = Math.max(0, ciegas_combobox.getSelectedIndex());
        if (GameFrame.BLIND_CAP > 0f) {
            for (int k = initial + 1; k < ciegas_combobox.getModel().getSize(); k++) {
                if (Helpers.float1DSecureCompare(parseBlindLevelBB(ciegas_combobox.getItemAt(k)), GameFrame.BLIND_CAP) == 0) {
                    return k - initial;
                }
            }
        }
        return 5;
    }

    // Modela el spinner como nº de subidas (1..niveles por encima de la inicial) y
    // refresca el label.
    private void modelBlindCapSpinner(int n) {
        int levels_above = Math.max(1, ciegas_combobox.getModel().getSize() - 1 - Math.max(0, ciegas_combobox.getSelectedIndex()));
        n = Math.min(Math.max(1, n), levels_above);
        this.blind_cap_spinner.setModel(new SpinnerNumberModel(n, 1, levels_above, 1));
        ((DefaultEditor) this.blind_cap_spinner.getEditor()).getTextField().setEditable(false);
        updateBlindCapLabel();
    }

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

    private void blind_cap_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blind_cap_checkboxActionPerformed
        this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
    }//GEN-LAST:event_blind_cap_checkboxActionPerformed

    private void rebuy_limit_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_limit_checkboxActionPerformed
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
    }//GEN-LAST:event_rebuy_limit_checkboxActionPerformed

    private void recover_checkbox_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_recover_checkbox_labelMouseClicked
        // TODO add your handling code here:
        recover_checkbox.doClick();
    }//GEN-LAST:event_recover_checkbox_labelMouseClicked

    private void recomprar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_recomprar_labelMouseClicked
        // TODO add your handling code here:
        rebuy_checkbox.doClick();
    }//GEN-LAST:event_recomprar_labelMouseClicked

    private void limite_manos_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_limite_manos_labelMouseClicked
        // TODO add your handling code here:
        manos_checkbox.doClick();
    }//GEN-LAST:event_limite_manos_labelMouseClicked

    private void nickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nickActionPerformed
        // TODO add your handling code here:
        vamos.doClick();
    }//GEN-LAST:event_nickActionPerformed

    private void avatar_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatar_labelMouseClicked
        // TODO add your handling code here:
        this.nick_labelMouseClicked(evt);
    }//GEN-LAST:event_avatar_labelMouseClicked

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        // TODO add your handling code here:
        dispose();
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void game_comboItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_game_comboItemStateChanged
        // TODO add your handling code here:

        if (this.recover_checkbox.isSelected()) {

            String[] parts = ((String) this.game_combo.getSelectedItem()).split(" @ ");

            this.nick.setText(parts[0]);

            this.nick.setEnabled(false);
        }

        packPreservingCenter();
    }//GEN-LAST:event_game_comboItemStateChanged

    private void bots_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bots_comboboxActionPerformed
        // Selection is committed only when the user accepts the dialog (see vamosActionPerformed).
    }//GEN-LAST:event_bots_comboboxActionPerformed

    private void packPreservingCenter() {

        int center_x = getX() + getWidth() / 2;

        int center_y = getY() + getHeight() / 2;

        pack();

        Rectangle screen = getGraphicsConfiguration().getBounds();

        int x = Math.max(screen.x, Math.min(center_x - getWidth() / 2, screen.x + screen.width - getWidth()));

        int y = Math.max(screen.y, Math.min(center_y - getHeight() / 2, screen.y + screen.height - getHeight()));

        setLocation(x, y);
    }

    private void commitBotDifficultyFromCombo() {
        if (!partida_local) {
            return;
        }
        switch (bots_combobox.getSelectedIndex()) {
            case 0:
                Bot.DIFFICULTY = Bot.Difficulty.EASY;
                break;
            case 1:
                Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
                break;
            case 2:
                Bot.DIFFICULTY = Bot.Difficulty.HARD;
                break;
            default:
                Bot.DIFFICULTY = Bot.Difficulty.MEDIUM;
                break;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JComboBox<String> bots_combobox;
    private javax.swing.JLabel bots_label;
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
    private javax.swing.JComboBox<String> estructura_combobox;
    private javax.swing.JLabel estructura_label;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JComboBox<String> game_combo;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JLabel limite_manos_label;
    private javax.swing.JPanel main_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JTextField nick;
    private javax.swing.JLabel nick_label;
    private javax.swing.JPanel nick_pass_panel;
    private javax.swing.JPasswordField pass_text;
    private javax.swing.JLabel password;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JCheckBox rebuy_limit_checkbox;
    private javax.swing.JSpinner rebuy_limit_spinner;
    private javax.swing.JPanel recompra_panel;
    private javax.swing.JLabel recomprar_label;
    private javax.swing.JCheckBox recover_checkbox;
    private javax.swing.JLabel recover_checkbox_label;
    private javax.swing.JPanel recover_panel;
    private javax.swing.JScrollPane scroll_panel;
    private javax.swing.JTextField server_ip_textfield;
    private javax.swing.JLabel server_label;
    private javax.swing.JLabel server_port_puntos;
    private javax.swing.JTextField server_port_textfield;
    private javax.swing.JLabel titulo_ventana;
    private javax.swing.JCheckBox upnp_checkbox;
    private javax.swing.JPanel url_panel;
    private javax.swing.JButton vamos;
    // End of variables declaration//GEN-END:variables
}
