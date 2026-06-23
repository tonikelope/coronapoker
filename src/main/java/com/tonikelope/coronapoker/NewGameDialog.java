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
    private String last_game_key = null; // descripción ("server @ fecha") de la última timba recuperable cargada (mostrada en game_label), o null si no hay ninguna cargada
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

        // Los presets solo aplican al CREAR una timba, no al modificar opciones en vivo.
        presets_panel.setVisible(false);

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
            bots_panel.setVisible(false);
        }

        this.doblar_checkbox.setSelected(GameFrame.CIEGAS_DOUBLE > 0);

        double_blinds_radio_minutos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);

        double_blinds_radio_manos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);

        if (GameFrame.CIEGAS_DOUBLE_TYPE <= 1) {
            doblar_ciegas_spinner_minutos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
            doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(GameFrame.CIEGAS_DOUBLE > 0 ? GameFrame.CIEGAS_DOUBLE : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            double_blinds_radio_minutos.setSelected(true);
            double_blinds_radio_manos.setSelected(false);
        } else {
            doblar_ciegas_spinner_manos.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
            doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(GameFrame.CIEGAS_DOUBLE > 0 ? GameFrame.CIEGAS_DOUBLE : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            double_blinds_radio_minutos.setSelected(false);
            double_blinds_radio_manos.setSelected(true);
        }

        this.manos_spinner.setEnabled(GameFrame.MANOS > 0);
        this.manos_checkbox.setSelected(GameFrame.MANOS > 0);
        manos_spinner.setModel(new SpinnerNumberModel(GameFrame.MANOS > 0 ? GameFrame.MANOS : 60, 1, null, 1));
        Helpers.makeNumericSpinnerEditable(manos_spinner, false);

        this.rebuy_checkbox.setSelected(GameFrame.REBUY);
        this.ante_checkbox.setSelected(GameFrame.ANTE);
        this.straddle_checkbox.setSelected(GameFrame.STRADDLE);
        this.doblar_checkbox.setSelected(GameFrame.CIEGAS_DOUBLE > 0);

        this.bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
        this.bot_rebuy_checkbox.setEnabled(GameFrame.REBUY);

        this.fixed_buyin_checkbox.setSelected(GameFrame.FIXED_BUYIN);
        this.buyin_spinner.setEnabled(GameFrame.FIXED_BUYIN);

        initBuyinRangeAndCapUI();

        this.rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
        this.rebuy_limit_checkbox.setEnabled(GameFrame.REBUY);
        this.rebuy_limit_spinner.setEnabled(GameFrame.REBUY && GameFrame.REBUY_LIMIT > 0);
        this.rebuy_limit_spinner.setModel(new SpinnerNumberModel(GameFrame.REBUY_LIMIT > 0 ? GameFrame.REBUY_LIMIT : 3, 1, null, 1));
        Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);

        this.rebuy_cap_label.setEnabled(GameFrame.REBUY);
        this.rebuy_cap_combo.setEnabled(GameFrame.REBUY);

        this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
        this.blind_cap_checkbox.setEnabled(GameFrame.CIEGAS_DOUBLE > 0);
        setBlindCapControlsEnabled(GameFrame.CIEGAS_DOUBLE > 0 && GameFrame.BLIND_CAP > 0f);

        // Selector de estructura: refleja la estructura activa (si la hay) y deja el
        // combo de niveles con sus ciegas, para que la búsqueda de abajo seleccione
        // el nivel actual también en estructuras personalizadas.
        initBlindStructureUI();

        String ciegas = BlindStructure.formatLevel(GameFrame.CIEGA_PEQUEÑA, GameFrame.CIEGA_GRANDE);

        int i = 0, t = this.ciegas_combobox.getModel().getSize();

        while (i < t) {
            String item = this.ciegas_combobox.getItemAt(i);

            if (item.equals(ciegas)) {
                break;
            }

            i++;
        }

        int buyin_lo_ctor = BuyinRules.min(GameFrame.CIEGA_GRANDE, GameFrame.BUYIN_MIN_BB);
        int buyin_hi_ctor = Math.max(buyin_lo_ctor, BuyinRules.max(GameFrame.CIEGA_GRANDE, GameFrame.BUYIN_MAX_BB));
        buyin_spinner.setModel(new SpinnerNumberModel(Math.max(buyin_lo_ctor, Math.min((int) GameFrame.BUYIN, buyin_hi_ctor)), buyin_lo_ctor, buyin_hi_ctor, (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

        Helpers.makeNumericSpinnerEditable(buyin_spinner, false);

        if (i < t) {
            this.ciegas_combobox.setSelectedIndex(i);
        }

        modelBlindCapSpinner(blindCapDoublingsFromCap());

        Helpers.setTranslatedTitle(this, update ? "update.actualizar_timba" : (partida_local ? "ui.crear_timba" : "ui.unirme_a_timba"));
        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        applyGroupTitledBorders();

        updateAnteStraddleLabels();

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
            bots_panel.setVisible(false);
        }

        // Presets: solo al crear timba como host (no al unirse). Ocultos en otros
        // modos para no ofrecer cargar una config completa donde no aplica.
        presets_panel.setVisible(partida_local);
        if (partida_local) {
            populatePresetsCombo(null);
        }

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

        initBuyinRangeAndCapUI();

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
            setBlindCapControlsEnabled(false);
            rebuy_limit_checkbox.setSelected(false);
            rebuy_limit_spinner.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
            Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            double ciega_grande = Double.valueOf(valores[1].trim());

            buyin_spinner.setModel(new SpinnerNumberModel(BuyinRules.defaultBuyin(ciega_grande, GameFrame.BUYIN_MIN_BB, GameFrame.BUYIN_MAX_BB), BuyinRules.min(ciega_grande, GameFrame.BUYIN_MIN_BB), Math.max(BuyinRules.min(ciega_grande, GameFrame.BUYIN_MIN_BB), BuyinRules.max(ciega_grande, GameFrame.BUYIN_MAX_BB)), (BUYIN_SPINNER_STEP = (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 4)))));

            Helpers.makeNumericSpinnerEditable(buyin_spinner, false);

            modelBlindCapSpinner(5);

            Helpers.makeNumericSpinnerEditable(manos_spinner, false);

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

        applyGroupTitledBorders();

        updateAnteStraddleLabels();

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

    /**
     * Titled borders for the four configuration groups (blinds, buy-in, game and
     * bots), so each block reads on its own. A TitledBorder is not a component, so
     * Helpers.updateFonts cannot reach it: the title font is set here from an
     * already-scaled label font (same approach as AudioSettingsDialog). Must be
     * called after Helpers.updateFonts and before pack() so the border insets are
     * accounted for in the dialog's preferred size.
     */
    private void applyGroupTitledBorders() {
        java.awt.Font title_font = ciegas_label.getFont();

        ciegas_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_ciegas")));
        compra_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_compra")));
        partida_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_partida")));
        bots_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_bots")));

        ((javax.swing.border.TitledBorder) ciegas_panel.getBorder()).setTitleFont(title_font);
        ((javax.swing.border.TitledBorder) compra_panel.getBorder()).setTitleFont(title_font);
        ((javax.swing.border.TitledBorder) partida_panel.getBorder()).setTitleFont(title_font);
        ((javax.swing.border.TitledBorder) bots_panel.getBorder()).setTitleFont(title_font);
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
                    this.last_game_key = rs.getString("server") + " @ " + timeZoneFormat.format(date);

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
        ciegas_panel = new javax.swing.JPanel();
        aumento_panel = new javax.swing.JPanel();
        ciegas_label = new javax.swing.JLabel();
        buyin_label = new javax.swing.JLabel();
        rebuy_checkbox = new javax.swing.JCheckBox();
        buyin_spinner = new javax.swing.JSpinner();
        ciegas_combobox = new javax.swing.JComboBox<>();
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        recomprar_label = new javax.swing.JLabel();
        partida_panel = new javax.swing.JPanel();
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
        ante_checkbox = new javax.swing.JCheckBox();
        straddle_checkbox = new javax.swing.JCheckBox();
        compra_panel = new javax.swing.JPanel();
        recompra_panel = new javax.swing.JPanel();
        rebuy_limit_checkbox = new javax.swing.JCheckBox();
        rebuy_limit_spinner = new javax.swing.JSpinner();
        bot_rebuy_checkbox = new javax.swing.JCheckBox();
        rebuy_cap_label = new javax.swing.JLabel();
        rebuy_cap_combo = new javax.swing.JComboBox<>();
        fixed_buyin_checkbox = new javax.swing.JCheckBox();
        buyin_range_label = new javax.swing.JLabel();
        buyin_min_bb_spinner = new javax.swing.JSpinner();
        buyin_range_sep_label = new javax.swing.JLabel();
        buyin_max_bb_spinner = new javax.swing.JSpinner();
        nick_pass_panel = new javax.swing.JPanel();
        nick = new javax.swing.JTextField();
        nick_label = new javax.swing.JLabel();
        password = new javax.swing.JLabel();
        pass_text = new javax.swing.JPasswordField();
        avatar_label = new javax.swing.JLabel();
        recover_panel = new javax.swing.JPanel();
        recover_checkbox = new javax.swing.JCheckBox();
        recover_checkbox_label = new javax.swing.JLabel();
        game_label = new javax.swing.JLabel();
        cancel_button = new javax.swing.JButton();
        cancel_button.putClientProperty("i18n.key", "ui.cancelar");
        bots_panel = new javax.swing.JPanel();
        bots_combobox = new javax.swing.JComboBox<>();
        bots_label = new javax.swing.JLabel();
        titulo_ventana = new javax.swing.JLabel();
        presets_panel = new javax.swing.JPanel();
        preset_label = new javax.swing.JLabel();
        preset_label.putClientProperty("i18n.key", "newgame.preset_label");
        presets_combobox = new javax.swing.JComboBox<>();
        preset_save_button = new javax.swing.JButton();
        preset_save_button.putClientProperty("i18n.key", "newgame.preset_guardar");
        preset_delete_button = new javax.swing.JButton();
        preset_delete_button.putClientProperty("i18n.key", "newgame.preset_borrar");

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

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png"))); // NOI18N
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.setDoubleBuffered(true);

        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png"))); // NOI18N
        buyin_label.setText("Compra inicial:");
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

        buyin_range_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_range_label.setText("Rango compra (CG):");
        buyin_range_label.setDoubleBuffered(true);

        buyin_min_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_min_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 10, 500, 5));
        buyin_min_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_min_bb_spinner.setDoubleBuffered(true);
        buyin_min_bb_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_min_bb_spinnerStateChanged(evt);
            }
        });

        buyin_range_sep_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_range_sep_label.setText("a");
        buyin_range_sep_label.setDoubleBuffered(true);

        buyin_max_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_max_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 10, 500, 5));
        buyin_max_bb_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        buyin_max_bb_spinner.setDoubleBuffered(true);
        buyin_max_bb_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_max_bb_spinnerStateChanged(evt);
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

        aumento_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        javax.swing.GroupLayout aumento_panelLayout = new javax.swing.GroupLayout(aumento_panel);
        aumento_panel.setLayout(aumento_panelLayout);
        aumento_panelLayout.setHorizontalGroup(
            aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aumento_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(doblar_checkbox)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(blind_cap_checkbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_label))
                .addContainerGap())
        );
        aumento_panelLayout.setVerticalGroup(
            aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aumento_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(doblar_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(aumento_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(blind_cap_checkbox)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blind_cap_label)
                .addContainerGap())
        );

        ante_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        ante_checkbox.setText("Ante");
        ante_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        ante_checkbox.setDoubleBuffered(true);

        straddle_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        straddle_checkbox.setText("Straddle");
        straddle_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        straddle_checkbox.setDoubleBuffered(true);

        javax.swing.GroupLayout ciegas_panelLayout = new javax.swing.GroupLayout(ciegas_panel);
        ciegas_panel.setLayout(ciegas_panelLayout);
        ciegas_panelLayout.setHorizontalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(estructura_label)
                            .addComponent(ciegas_label))
                        .addGap(18, 18, 18)
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(estructura_combobox, 0, 200, Short.MAX_VALUE)
                            .addComponent(ciegas_combobox, 0, 200, Short.MAX_VALUE)))
                    .addComponent(aumento_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(ante_checkbox)
                        .addGap(18, 18, 18)
                        .addComponent(straddle_checkbox)))
                .addContainerGap())
        );
        ciegas_panelLayout.setVerticalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(estructura_label)
                    .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(aumento_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ante_checkbox)
                    .addComponent(straddle_checkbox))
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

        rebuy_cap_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        rebuy_cap_label.setText("Tope recompra:");
        rebuy_cap_label.setDoubleBuffered(true);
        rebuy_cap_label.putClientProperty("i18n.key", "rebuy.tope_recompra");

        rebuy_cap_combo.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        rebuy_cap_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rebuy_cap_combo.setDoubleBuffered(true);

        fixed_buyin_checkbox.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        fixed_buyin_checkbox.setText("Buy-in fijo");
        fixed_buyin_checkbox.setToolTipText("Marcado: todos arrancan con el mismo buy-in. Desmarcado: cada jugador elige su buy-in (dentro del rango configurado) al entrar al tablero.");
        fixed_buyin_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        fixed_buyin_checkbox.setDoubleBuffered(true);
        fixed_buyin_checkbox.putClientProperty("i18n.key", "newgame.buyin_fijo");
        fixed_buyin_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixed_buyin_checkboxActionPerformed(evt);
            }
        });

        recompra_panel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        javax.swing.GroupLayout recompra_panelLayout = new javax.swing.GroupLayout(recompra_panel);
        recompra_panel.setLayout(recompra_panelLayout);
        recompra_panelLayout.setHorizontalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(recompra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(recomprar_label))
                    .addComponent(rebuy_limit_checkbox)
                    .addComponent(rebuy_cap_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        recompra_panelLayout.setVerticalGroup(
            recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recompra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_checkbox)
                    .addComponent(recomprar_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_limit_checkbox)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recompra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_cap_label)
                    .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout compra_panelLayout = new javax.swing.GroupLayout(compra_panel);
        compra_panel.setLayout(compra_panelLayout);
        compra_panelLayout.setHorizontalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buyin_label)
                            .addComponent(buyin_range_label))
                        .addGap(18, 18, 18)
                        .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(fixed_buyin_checkbox))
                            .addGroup(compra_panelLayout.createSequentialGroup()
                                .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(14, 14, 14)
                                .addComponent(buyin_range_sep_label)
                                .addGap(14, 14, 14)
                                .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addComponent(recompra_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        compra_panelLayout.setVerticalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_label)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fixed_buyin_checkbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_range_label)
                    .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buyin_range_sep_label)
                    .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(recompra_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout partida_panelLayout = new javax.swing.GroupLayout(partida_panel);
        partida_panel.setLayout(partida_panelLayout);
        partida_panelLayout.setHorizontalGroup(
            partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(partida_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGap(11, 11, 11)
                .addComponent(manos_checkbox)
                .addGap(0, 0, 0)
                .addComponent(limite_manos_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(manos_spinner, javax.swing.GroupLayout.DEFAULT_SIZE, 166, Short.MAX_VALUE)
                .addContainerGap())
        );
        partida_panelLayout.setVerticalGroup(
            partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(partida_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(manos_checkbox)
                    .addComponent(limite_manos_label)
                    .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");
        estructura_label.putClientProperty("i18n.key", "blinds.estructura");
        buyin_label.putClientProperty("i18n.key", "blinds.compra_inicial");
        buyin_range_label.putClientProperty("i18n.key", "blinds.rango_compra");
        buyin_range_sep_label.putClientProperty("i18n.key", "blinds.rango_a");
        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");
        limite_manos_label.putClientProperty("i18n.key", "game.limite_de_manos");

        javax.swing.GroupLayout config_partida_panelLayout = new javax.swing.GroupLayout(config_partida_panel);
        config_partida_panel.setLayout(config_partida_panelLayout);
        config_partida_panelLayout.setHorizontalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(compra_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ciegas_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bots_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        config_partida_panelLayout.setVerticalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(compra_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ciegas_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(partida_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bots_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

        game_label.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N

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
                .addComponent(game_label, 0, 428, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        recover_panelLayout.setVerticalGroup(
            recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recover_panelLayout.createSequentialGroup()
                .addGroup(recover_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(recover_checkbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(recover_checkbox_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(game_label))
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

        javax.swing.GroupLayout bots_panelLayout = new javax.swing.GroupLayout(bots_panel);
        bots_panel.setLayout(bots_panelLayout);
        bots_panelLayout.setHorizontalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(bots_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(bot_rebuy_checkbox)
                .addContainerGap())
        );
        bots_panelLayout.setVerticalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bots_label)
                    .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bot_rebuy_checkbox))
                .addContainerGap())
        );

        preset_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        preset_label.setText("Perfil de ajustes:");
        preset_label.setDoubleBuffered(true);

        presets_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        presets_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        presets_combobox.setDoubleBuffered(true);
        presets_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                presets_comboboxActionPerformed(evt);
            }
        });

        preset_save_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        preset_save_button.setText("Guardar…");
        preset_save_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_save_button.setDoubleBuffered(true);
        preset_save_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preset_save_buttonActionPerformed(evt);
            }
        });

        preset_delete_button.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        preset_delete_button.setText("Borrar");
        preset_delete_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        preset_delete_button.setDoubleBuffered(true);
        preset_delete_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preset_delete_buttonActionPerformed(evt);
            }
        });

        presets_panel.setOpaque(false);

        javax.swing.GroupLayout presets_panelLayout = new javax.swing.GroupLayout(presets_panel);
        presets_panel.setLayout(presets_panelLayout);
        presets_panelLayout.setHorizontalGroup(
            presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(presets_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(preset_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(presets_combobox, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(preset_save_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(preset_delete_button)
                .addContainerGap())
        );
        presets_panelLayout.setVerticalGroup(
            presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(presets_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(presets_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(preset_label)
                    .addComponent(presets_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(preset_save_button)
                    .addComponent(preset_delete_button))
                .addContainerGap())
        );

        javax.swing.GroupLayout main_panelLayout = new javax.swing.GroupLayout(main_panel);
        main_panel.setLayout(main_panelLayout);
        main_panelLayout.setHorizontalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(url_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(presets_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addComponent(presets_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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

            GameFrame.ANTE = this.ante_checkbox.isSelected();

            GameFrame.STRADDLE = this.straddle_checkbox.isSelected();

            GameFrame.BOT_REBUY = this.bot_rebuy_checkbox.isSelected();

            GameFrame.REBUY_LIMIT = this.rebuy_limit_checkbox.isSelected() ? (int) this.rebuy_limit_spinner.getValue() : 0;

            GameFrame.BLIND_CAP = this.blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0f;

            GameFrame.BUYIN = (int) this.buyin_spinner.getValue();

            GameFrame.FIXED_BUYIN = this.fixed_buyin_checkbox.isSelected();

            GameFrame.BUYIN_MIN_BB = ((Number) this.buyin_min_bb_spinner.getValue()).intValue();

            GameFrame.BUYIN_MAX_BB = ((Number) this.buyin_max_bb_spinner.getValue()).intValue();

            GameFrame.REBUY_CAP_POLICY = this.rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;

            String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            GameFrame.CIEGA_GRANDE = Double.valueOf(valores_ciegas[1].trim());

            GameFrame.CIEGA_PEQUEÑA = Double.valueOf(valores_ciegas[0].trim());

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
                    GameFrame.RECOVER_ID = (int) game.get(this.last_game_key).get("id");
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

                // Modo de buy-in, rango [min,max] BB y política de tope de recompra: en
                // RECOVER NO se tocan (los restaura applyRecoverSettings al cargar la
                // timba anterior; sus controles están deshabilitados con valores stale).
                // Sin este guard, los spinners/combo deshabilitados pisarían la config
                // recuperada con los valores por defecto y la re-persistirían corrupta.
                if (!GameFrame.RECOVER) {
                    GameFrame.ANTE = this.ante_checkbox.isSelected();

                    GameFrame.STRADDLE = this.straddle_checkbox.isSelected();

                    GameFrame.FIXED_BUYIN = this.fixed_buyin_checkbox.isSelected();

                    GameFrame.BUYIN_MIN_BB = ((Number) this.buyin_min_bb_spinner.getValue()).intValue();

                    GameFrame.BUYIN_MAX_BB = ((Number) this.buyin_max_bb_spinner.getValue()).intValue();

                    GameFrame.REBUY_CAP_POLICY = this.rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;
                }

                String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

                GameFrame.CIEGA_GRANDE = Double.parseDouble(valores_ciegas[1].trim());

                GameFrame.CIEGA_PEQUEÑA = Double.parseDouble(valores_ciegas[0].trim());

                // Estructura personalizada activa (null = escalera por defecto). En
                // RECOVER no se toca: la restaura applyRecoverSettings al cargar la
                // timba anterior. En timba nueva refleja la estructura del combo y
                // viaja a los clientes en el INIT (C5).
                if (!GameFrame.RECOVER) {
                    GameFrame.ACTIVE_BLIND_STRUCTURE = pending_structure != null ? pending_structure.getLevels() : null;
                }

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
        setBlindCapControlsEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
    }//GEN-LAST:event_doblar_checkboxActionPerformed

    private void rebuy_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuy_checkboxActionPerformed
        this.rebuy_limit_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_limit_spinner.setEnabled(this.rebuy_checkbox.isSelected() && this.rebuy_limit_checkbox.isSelected());
        this.bot_rebuy_checkbox.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_label.setEnabled(this.rebuy_checkbox.isSelected());
        this.rebuy_cap_combo.setEnabled(this.rebuy_checkbox.isSelected());
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

            if (this.last_game_key == null) {
                loadLastGame();
            }

            if (this.last_game_key != null) {

                this.game_label.setText(this.last_game_key);

                this.buyin_spinner.setEnabled(false);

                this.buyin_label.setEnabled(false);

                this.ciegas_label.setEnabled(false);

                this.ciegas_combobox.setEnabled(false);

                this.estructura_combobox.setEnabled(false);

                syncStructureComboForRecover();

                this.doblar_ciegas_spinner_minutos.setEnabled(false);

                this.double_blinds_radio_minutos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_checkbox.setEnabled(false);

                this.blind_cap_checkbox.setEnabled(false);

                setBlindCapControlsEnabled(false);

                this.rebuy_checkbox.setEnabled(false);

                this.rebuy_limit_checkbox.setEnabled(false);

                this.rebuy_limit_spinner.setEnabled(false);

                this.bot_rebuy_checkbox.setEnabled(false);

                this.fixed_buyin_checkbox.setEnabled(false);

                this.buyin_min_bb_spinner.setEnabled(false);

                this.buyin_max_bb_spinner.setEnabled(false);

                this.buyin_range_label.setEnabled(false);

                this.buyin_range_sep_label.setEnabled(false);

                this.rebuy_cap_combo.setEnabled(false);

                this.rebuy_cap_label.setEnabled(false);

                // Ante/straddle, bots, límite de manos y presets: también bloqueados al
                // recuperar (la config de la timba recuperada es fija; solo se puede jugar).
                this.ante_checkbox.setEnabled(false);
                this.straddle_checkbox.setEnabled(false);
                this.bots_combobox.setEnabled(false);
                this.bots_label.setEnabled(false);
                this.manos_checkbox.setEnabled(false);
                this.manos_spinner.setEnabled(false);
                this.presets_combobox.setEnabled(false);
                this.preset_save_button.setEnabled(false);
                this.preset_delete_button.setEnabled(false);
                this.preset_label.setEnabled(false);

                this.recover_checkbox_label.setOpaque(true);

                this.recover_checkbox_label.setBackground(Color.YELLOW);

                String[] parts = this.last_game_key.split(" @ ");

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
                this.game_label.setText("");
                this.recover_checkbox.setEnabled(false);
                Helpers.mostrarMensajeError(this, Translator.translate("game.no_hay_timbas_que_se"));

                packPreservingCenter();

            }

        } else {
            this.game_label.setText("");

            this.fixed_buyin_checkbox.setEnabled(true);

            // El spinner sigue el modo: deshabilitado si es buy-in variable.
            this.buyin_spinner.setEnabled(this.fixed_buyin_checkbox.isSelected());

            // El rango de buy-in vuelve a ser editable. La política de tope de recompra
            // se reactiva más abajo solo si la recompra está activada.
            this.buyin_min_bb_spinner.setEnabled(true);
            this.buyin_max_bb_spinner.setEnabled(true);
            this.buyin_range_label.setEnabled(true);
            this.buyin_range_sep_label.setEnabled(true);

            this.buyin_label.setEnabled(true);

            this.ciegas_label.setEnabled(true);

            this.ciegas_combobox.setEnabled(true);

            // Vuelve a timba nueva: reactiva el selector de estructura, quita el ítem
            // sintético "(recuperada)" y restablece "Por defecto" + ciegas por defecto.
            this.estructura_combobox.setEnabled(true);
            item_recuperada = null;
            pending_structure = null;
            populateStructureCombo(null);
            applySelectedStructure();

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

                setBlindCapControlsEnabled(this.blind_cap_checkbox.isSelected());

            } else {
                this.double_blinds_radio_minutos.setEnabled(false);

                this.double_blinds_radio_manos.setEnabled(false);

                this.doblar_ciegas_spinner_manos.setEnabled(false);

                this.doblar_ciegas_spinner_minutos.setEnabled(false);

                this.blind_cap_checkbox.setEnabled(false);

                setBlindCapControlsEnabled(false);
            }

            if (this.rebuy_checkbox.isSelected()) {
                this.rebuy_limit_checkbox.setEnabled(true);
                this.rebuy_limit_spinner.setEnabled(this.rebuy_limit_checkbox.isSelected());
                this.bot_rebuy_checkbox.setEnabled(true);
                this.rebuy_cap_label.setEnabled(true);
                this.rebuy_cap_combo.setEnabled(true);
            } else {
                this.rebuy_limit_checkbox.setEnabled(false);
                this.rebuy_limit_spinner.setEnabled(false);
                this.bot_rebuy_checkbox.setEnabled(false);
                this.rebuy_cap_label.setEnabled(false);
                this.rebuy_cap_combo.setEnabled(false);
            }

            // Restaura ante/straddle, bots, límite de manos y presets a su estado de timba nueva.
            this.ante_checkbox.setEnabled(true);
            this.straddle_checkbox.setEnabled(true);
            this.bots_combobox.setEnabled(true);
            this.bots_label.setEnabled(true);
            this.manos_checkbox.setEnabled(true);
            this.manos_spinner.setEnabled(this.manos_checkbox.isSelected());
            this.presets_combobox.setEnabled(true);
            this.preset_save_button.setEnabled(true);
            this.preset_delete_button.setEnabled(this.presets_combobox.getSelectedIndex() > 0);
            this.preset_label.setEnabled(true);

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

        // Etiqueta informativa al vuelo, FUERA del gate init: así también se refresca
        // cuando el combo cambia por carga de preset o de estructura (que suprimen init
        // pero disparan este handler al reconstruir/seleccionar el nivel).
        updateAnteStraddleLabels();

        if (init) {

            String[] valores = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

            double ciega_pequena = Double.valueOf(valores[0].trim());

            double ciega_grande = Double.valueOf(valores[1].trim());

            // Paso del spinner derivado de la magnitud de la ciega pequeña, no del
            // índice del combo: para la escalera por defecto 1-2-3-5 da exactamente
            // lo mismo que el viejo pow(10, floor(index/4)), pero también funciona
            // con estructuras personalizadas de niveles arbitrarios.
            int buyin_lo_cg = BuyinRules.min(ciega_grande, GameFrame.BUYIN_MIN_BB);
            int buyin_hi_cg = Math.max(buyin_lo_cg, BuyinRules.max(ciega_grande, GameFrame.BUYIN_MAX_BB));
            buyin_spinner.setModel(new SpinnerNumberModel(BuyinRules.defaultBuyin(ciega_grande, GameFrame.BUYIN_MIN_BB, GameFrame.BUYIN_MAX_BB), buyin_lo_cg, buyin_hi_cg, (BUYIN_SPINNER_STEP = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(ciega_pequena)) + 1)))));

            Helpers.makeNumericSpinnerEditable(buyin_spinner, false);

            modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());

            packPreservingCenter();

        }
    }//GEN-LAST:event_ciegas_comboboxActionPerformed

    // Etiqueta INFORMATIVA derivada: muestra entre paréntesis el importe ACTUAL del ante
    // (= ciega pequeña) y del straddle (= 2x ciega grande), leídos del nivel de ciegas
    // seleccionado, y se refresca al vuelo cuando cambia. Los importes por código son
    // FIJOS (ciega pequeña / doble ciega grande), no configurables: esto es solo el texto.
    private void updateAnteStraddleLabels() {
        Object sel = ciegas_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        String[] v = ((String) sel).replace(",", ".").split("/");
        if (v.length < 2) {
            return;
        }
        try {
            double sb = Double.valueOf(v[0].trim());
            double bb = Double.valueOf(v[1].trim());
            ante_checkbox.setText("Ante (" + Helpers.money2String(sb) + ")");
            straddle_checkbox.setText("Straddle (" + Helpers.money2String(Helpers.doubleClean(2 * bb)) + ")");
        } catch (NumberFormatException ignored) {
        }
    }

    // Estructura de ciegas elegida para esta timba (null = escalera por defecto
    // 1-2-3-5). Determina los niveles del combo de ciegas y, al crear la timba,
    // GameFrame.ACTIVE_BLIND_STRUCTURE.
    private BlindStructure pending_structure = null;

    // Marcadores especiales del combo de estructura; el resto de ítems son nombres
    // de estructuras personalizadas.
    private String item_por_defecto;
    private String item_gestionar;
    // Etiqueta sintética para una estructura recuperada que ya no está guardada
    // (solo aparece en modo recover, en solo-lectura). null si no aplica.
    private String item_recuperada;

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

    private void estructura_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_estructura_comboboxActionPerformed
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
    }//GEN-LAST:event_estructura_comboboxActionPerformed

    // Aplica la estructura seleccionada al combo de niveles (ciegas_combobox) y a
    // pending_structure. "Por defecto" => null + escalera 1-2-3-5; personalizada =>
    // sus niveles. Conserva el formato "sb / bb" local (mismo que el combo original).
    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        if (item_recuperada != null && item_recuperada.equals(sel)) {
            // Ítem sintético de una estructura recuperada (solo-lectura): no toca nada.
            return;
        }
        double[][] levels;
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
            items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
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
        initBlindStructureUIFrom(GameFrame.ACTIVE_BLIND_STRUCTURE);
    }

    // Igual que initBlindStructureUI pero tomando la estructura activa como
    // parámetro (null = escalera por defecto), para que cargar un preset refleje
    // SU estructura en el combo sin pasar por GameFrame.
    private void initBlindStructureUIFrom(double[][] active) {
        pending_structure = null;
        String selectName = null;
        if (active != null) {
            for (java.util.Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
                if (java.util.Arrays.deepEquals(e.getValue().getLevels(), active)) {
                    pending_structure = e.getValue();
                    selectName = e.getKey();
                    break;
                }
            }
            if (pending_structure == null) {
                // R1: la estructura activa ya no está guardada (borrada o editada
                // desde que se configuró la timba). La conservamos como estructura
                // anónima "en uso" para que GUARDAR opciones NO la revierta en
                // silencio a la escalera por defecto. (El combo muestra "Por
                // defecto"; elegir otra entrada la reemplaza como siempre.)
                try {
                    pending_structure = new BlindStructure(Translator.translate("blinds.estructura_actual"), active);
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        if (pending_structure != null) {
            double[][] levels = pending_structure.getLevels();
            String[] items = new String[levels.length];
            for (int k = 0; k < levels.length; k++) {
                items[k] = BlindStructure.formatLevel(levels[k][0], levels[k][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        }
        populateStructureCombo(selectName);
    }

    // Tras cargar una timba a recuperar (loadLastGame ya restauró
    // GameFrame.ACTIVE_BLIND_STRUCTURE), refleja la estructura recuperada en el
    // combo aunque NO esté entre las guardadas del usuario: si coincide con una
    // guardada muestra su nombre; si no, un ítem sintético "(recuperada)" de
    // solo-lectura. El combo de niveles muestra las ciegas recuperadas. El motor
    // recupera con ACTIVE pase lo que pase aquí (esto es solo la etiqueta).
    private void syncStructureComboForRecover() {
        item_recuperada = null;
        double[][] active = GameFrame.ACTIVE_BLIND_STRUCTURE;
        if (active == null) {
            pending_structure = null;
            populateStructureCombo(null);
            return;
        }
        String matchName = null;
        for (java.util.Map.Entry<String, BlindStructure> e : BlindStructure.loadAll().entrySet()) {
            if (java.util.Arrays.deepEquals(e.getValue().getLevels(), active)) {
                matchName = e.getKey();
                pending_structure = e.getValue();
                break;
            }
        }
        if (matchName != null) {
            populateStructureCombo(matchName);
        } else {
            pending_structure = null;
            boolean prev_init = init;
            init = false;
            try {
                populateStructureCombo(null);
                item_recuperada = Translator.translate("blinds.estructura_recuperada");
                estructura_combobox.insertItemAt(item_recuperada, 1);
                estructura_combobox.setSelectedItem(item_recuperada);
            } finally {
                init = prev_init;
            }
        }
        String[] items = new String[active.length];
        for (int k = 0; k < active.length; k++) {
            items[k] = BlindStructure.formatLevel(active[k][0], active[k][1]);
        }
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
    }

    // El tope de ciegas se elige como "nº de subidas" (cuántas veces suben las
    // ciegas como máximo, desde las iniciales elegidas). El spinner es ese entero
    // y blind_cap_label muestra al vuelo el nivel resultante. Internamente
    // GameFrame.BLIND_CAP sigue siendo la ciega grande de ese nivel (double), así
    // que la lógica de congelar ciegas / recover / red no cambia.

    // Ciega grande (segundo número) de un item del combo de niveles.
    private double parseBlindLevelBB(String item) {
        return Double.parseDouble(item.replace(",", ".").split("/")[1].trim());
    }

    // Índice del combo del nivel tras n subidas desde las ciegas iniciales, sin
    // pasarse del último nivel disponible.
    private int blindCapTargetIndex(int n) {
        int last = ciegas_combobox.getModel().getSize() - 1;
        return Math.min(Math.max(0, ciegas_combobox.getSelectedIndex()) + n, last);
    }

    // Ciega grande del nivel-tope para el nº de subidas actual (para guardar).
    private double blindCapSelectedBB() {
        return parseBlindLevelBB(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private void updateBlindCapLabel() {
        blind_cap_label.setText(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    // Habilita/deshabilita JUNTOS el spinner del tope de ciega grande y su label
    // (el "n / m"), para que el label se atenúe con el spinner cuando el tope o el
    // checkbox padre "Aumentar ciegas" están desactivados (como los demás paneles).
    private void setBlindCapControlsEnabled(boolean enabled) {
        blind_cap_spinner.setEnabled(enabled);
        blind_cap_label.setEnabled(enabled);
    }

    // Reconstruye el nº de subidas desde el GameFrame.BLIND_CAP guardado (busca el
    // nivel cuya ciega grande coincide); si no hay tope guardado, el default (5).
    private int blindCapDoublingsFromCap() {
        return blindCapDoublingsFromCap(GameFrame.BLIND_CAP);
    }

    // Igual pero tomando el tope (ciega grande del nivel-tope) como parámetro, para
    // reconstruir el nº de subidas al cargar un preset sin pasar por GameFrame.
    private int blindCapDoublingsFromCap(double cap) {
        int initial = Math.max(0, ciegas_combobox.getSelectedIndex());
        if (cap > 0f) {
            for (int k = initial + 1; k < ciegas_combobox.getModel().getSize(); k++) {
                if (Helpers.doubleSecureCompare(parseBlindLevelBB(ciegas_combobox.getItemAt(k)), cap) == 0) {
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
        Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
        updateBlindCapLabel();
    }

    private void buyin_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_spinnerStateChanged
        // Sin sonido: el repiqueteo del spinner sonaba a máquina tragaperras.
    }//GEN-LAST:event_buyin_spinnerStateChanged

    // Topes inferior/superior del rango de buy-in, en ciegas grandes (BB). El motor
    // de dinero sigue trabajando en fichas: estos spinners solo fijan los
    // multiplicadores que BuyinRules convierte a fichas. Se cruzan para mantener
    // siempre inferior < superior dentro de [FLOOR_MIN_BB, CEIL_MAX_BB].
    private static final int BUYIN_RANGE_STEP = 5;
    private boolean adjusting_buyin_range = false;

    // Inicializa los spinners de rango y el combo de política de tope de recompra
    // desde el estado de GameFrame, validando los límites. Compartido por ambos
    // constructores (crear/unirse y modificar).
    private void initBuyinRangeAndCapUI() {
        int lo = Math.max(BuyinRules.FLOOR_MIN_BB, Math.min(GameFrame.BUYIN_MIN_BB, BuyinRules.CEIL_MAX_BB - BUYIN_RANGE_STEP));
        int hi = Math.max(lo + BUYIN_RANGE_STEP, Math.min(GameFrame.BUYIN_MAX_BB, BuyinRules.CEIL_MAX_BB));

        adjusting_buyin_range = true;
        try {
            buyin_min_bb_spinner.setModel(new SpinnerNumberModel(lo, BuyinRules.FLOOR_MIN_BB, BuyinRules.CEIL_MAX_BB, BUYIN_RANGE_STEP));
            buyin_max_bb_spinner.setModel(new SpinnerNumberModel(hi, BuyinRules.FLOOR_MIN_BB, BuyinRules.CEIL_MAX_BB, BUYIN_RANGE_STEP));
            Helpers.makeNumericSpinnerEditable(buyin_min_bb_spinner, false);
            Helpers.makeNumericSpinnerEditable(buyin_max_bb_spinner, false);
        } finally {
            adjusting_buyin_range = false;
        }

        GameFrame.BUYIN_MIN_BB = lo;
        GameFrame.BUYIN_MAX_BB = hi;

        // Combo de política: índice 0 = BUYIN, índice 1 = stack del jugador más alto
        // (los índices coinciden con las constantes GameFrame.REBUY_CAP_*).
        rebuy_cap_combo.removeAllItems();
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_buyin"));
        rebuy_cap_combo.addItem(Translator.translate("rebuy.cap_policy_highest"));
        rebuy_cap_combo.setSelectedIndex(GameFrame.REBUY_CAP_POLICY == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        Helpers.setTranslatedToolTip(rebuy_cap_combo, "rebuy.tope_recompra_tooltip");
    }

    // Reconstruye el modelo del spinner de buy-in con los límites [min,max] BB
    // actuales sobre la ciega grande seleccionada, conservando el valor (clamp).
    private void rebuildBuyinSpinnerModel() {
        if (ciegas_combobox.getSelectedItem() == null) {
            return;
        }
        String[] v = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");
        double cp = Double.parseDouble(v[0].trim());
        double cg = Double.parseDouble(v[1].trim());
        int lo = BuyinRules.min(cg, GameFrame.BUYIN_MIN_BB);
        int hi = Math.max(lo, BuyinRules.max(cg, GameFrame.BUYIN_MAX_BB));
        int cur = ((Number) buyin_spinner.getValue()).intValue();
        int val = Math.max(lo, Math.min(cur, hi));
        BUYIN_SPINNER_STEP = (int) Math.max(1, Math.pow(10, Math.floor(Math.log10(cp)) + 1));
        buyin_spinner.setModel(new SpinnerNumberModel(val, lo, hi, BUYIN_SPINNER_STEP));
        Helpers.makeNumericSpinnerEditable(buyin_spinner, false);
    }

    private void buyin_min_bb_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_min_bb_spinnerStateChanged
        onBuyinRangeChanged(true);
    }//GEN-LAST:event_buyin_min_bb_spinnerStateChanged

    private void buyin_max_bb_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_max_bb_spinnerStateChanged
        onBuyinRangeChanged(false);
    }//GEN-LAST:event_buyin_max_bb_spinnerStateChanged

    // Aplica un cambio en cualquiera de los dos spinners de rango: mantiene
    // inferior < superior (empujando el otro extremo si hace falta, dentro de los
    // topes), propaga a GameFrame y reconstruye el spinner de buy-in.
    private void onBuyinRangeChanged(boolean minChanged) {
        if (!init || adjusting_buyin_range) {
            return;
        }

        adjusting_buyin_range = true;
        try {
            int lo = ((Number) buyin_min_bb_spinner.getValue()).intValue();
            int hi = ((Number) buyin_max_bb_spinner.getValue()).intValue();

            if (lo >= hi) {
                if (minChanged) {
                    hi = Math.min(BuyinRules.CEIL_MAX_BB, lo + BUYIN_RANGE_STEP);
                    if (hi - BUYIN_RANGE_STEP < lo) {
                        lo = hi - BUYIN_RANGE_STEP;
                        buyin_min_bb_spinner.setValue(lo);
                    }
                    buyin_max_bb_spinner.setValue(hi);
                } else {
                    lo = Math.max(BuyinRules.FLOOR_MIN_BB, hi - BUYIN_RANGE_STEP);
                    if (lo + BUYIN_RANGE_STEP > hi) {
                        hi = lo + BUYIN_RANGE_STEP;
                        buyin_max_bb_spinner.setValue(hi);
                    }
                    buyin_min_bb_spinner.setValue(lo);
                }
            }

            GameFrame.BUYIN_MIN_BB = lo;
            GameFrame.BUYIN_MAX_BB = hi;
        } finally {
            adjusting_buyin_range = false;
        }

        rebuildBuyinSpinnerModel();
        packPreservingCenter();
    }

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
        setBlindCapControlsEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
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

    private void bots_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bots_comboboxActionPerformed
        // Selection is committed only when the user accepts the dialog (see vamosActionPerformed).
    }//GEN-LAST:event_bots_comboboxActionPerformed

    // ===== Presets de nueva partida (solo al crear timba como host) ===========
    // Un preset guarda TODA la configuración de la timba (ciegas, estructura
    // elegida, buy-in, recompra, aumento, tope, manos, ante, straddle, bots), como
    // las estructuras de ciegas propias. El diálogo mapea sus controles
    // hacia/desde GamePreset.Settings y NO toca GameFrame (salvo el rango de buy-in,
    // que el diálogo ya usa GameFrame como almacén de trabajo): cargar un preset y
    // luego cancelar no deja rastro.

    private static final int MAX_PRESET_NAME_LENGTH = 40;
    // Suprime la carga al repoblar el combo o cuando el guard interno lo exige.
    private boolean suppress_preset_combo = false;

    // (Re)llena el combo de presets: marcador "(elegir preset)" + nombres guardados.
    // No dispara la carga (baja el guard mientras repuebla). Reselecciona por nombre
    // si se indica.
    private void populatePresetsCombo(String selectName) {
        suppress_preset_combo = true;
        try {
            presets_combobox.removeAllItems();
            presets_combobox.addItem(Translator.translate("newgame.preset_por_defecto"));
            for (String name : GamePreset.loadAll().keySet()) {
                presets_combobox.addItem(name);
            }
            if (selectName != null) {
                presets_combobox.setSelectedItem(selectName);
            }
            if (presets_combobox.getSelectedItem() == null) {
                presets_combobox.setSelectedIndex(0);
            }
            preset_delete_button.setEnabled(presets_combobox.getSelectedIndex() > 0);
        } finally {
            suppress_preset_combo = false;
        }
    }

    private void presets_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_presets_comboboxActionPerformed
        if (suppress_preset_combo) {
            return;
        }
        int idx = presets_combobox.getSelectedIndex();
        preset_delete_button.setEnabled(idx > 0);
        if (idx <= 0) {
            // "Por defecto": restablece la configuración de fábrica de una timba nueva
            // (igual que elegir la escalera "Por defecto" en el combo de estructuras).
            applySettingsToControls(new GamePreset.Settings());
            return;
        }
        GamePreset preset = GamePreset.loadAll().get((String) presets_combobox.getSelectedItem());
        if (preset != null) {
            applySettingsToControls(GamePreset.Settings.parse(preset.getSettings()));
        }
    }//GEN-LAST:event_presets_comboboxActionPerformed

    private void preset_save_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preset_save_buttonActionPerformed
        String name = promptPresetName();
        if (name == null) {
            return;
        }
        java.util.LinkedHashMap<String, GamePreset> all = GamePreset.loadAll();
        boolean exists = all.containsKey(name);
        if (exists) {
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("newgame.preset_sobrescribir", name)) != 0) {
                return;
            }
        } else if (all.size() >= GamePreset.MAX_PRESETS) {
            Helpers.mostrarMensajeError(this, Translator.translate("newgame.preset_limite", GamePreset.MAX_PRESETS));
            return;
        }
        all.put(name, new GamePreset(name, captureSettingsFromControls().serialize()));
        GamePreset.saveAll(all.values());
        populatePresetsCombo(name);
    }//GEN-LAST:event_preset_save_buttonActionPerformed

    private void preset_delete_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preset_delete_buttonActionPerformed
        int idx = presets_combobox.getSelectedIndex();
        if (idx <= 0) {
            return;
        }
        String name = (String) presets_combobox.getSelectedItem();
        if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("newgame.preset_confirmar_borrar", name)) != 0) {
            return;
        }
        java.util.LinkedHashMap<String, GamePreset> all = GamePreset.loadAll();
        all.remove(name);
        GamePreset.saveAll(all.values());
        populatePresetsCombo(null);
    }//GEN-LAST:event_preset_delete_buttonActionPerformed

    // Pide un nombre de preset (caja con la fuente de la app, igual que el editor de
    // estructuras). Devuelve null al cancelar o si queda vacío.
    private String promptPresetName() {
        javax.swing.JLabel prompt = new javax.swing.JLabel(Translator.translate("newgame.preset_nombre"));
        javax.swing.JTextField field = new javax.swing.JTextField("", 18);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        panel.add(prompt, java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);
        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(panel, javax.swing.JOptionPane.PLAIN_MESSAGE, javax.swing.JOptionPane.OK_CANCEL_OPTION);
        javax.swing.JDialog d = pane.createDialog(this, getTitle());
        Helpers.updateFonts(pane, Helpers.GUI_FONT, 1.15f);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
        d.dispose();
        if (!Integer.valueOf(javax.swing.JOptionPane.OK_OPTION).equals(pane.getValue())) {
            return null;
        }
        String name = field.getText().trim();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() > MAX_PRESET_NAME_LENGTH ? name.substring(0, MAX_PRESET_NAME_LENGTH) : name;
    }

    // Lee la configuración ACTUAL de los controles a un Settings (sin tocar
    // GameFrame). Mismo mapeo que el commit de vamosActionPerformed.
    private GamePreset.Settings captureSettingsFromControls() {
        GamePreset.Settings s = new GamePreset.Settings();
        String[] v = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");
        s.smallBlind = Double.parseDouble(v[0].trim());
        s.bigBlind = Double.parseDouble(v[1].trim());
        s.structure = pending_structure != null ? pending_structure.getLevels() : null;
        s.buyin = ((Number) buyin_spinner.getValue()).intValue();
        s.fixedBuyin = fixed_buyin_checkbox.isSelected();
        s.minBb = ((Number) buyin_min_bb_spinner.getValue()).intValue();
        s.maxBb = ((Number) buyin_max_bb_spinner.getValue()).intValue();
        s.rebuy = rebuy_checkbox.isSelected();
        s.rebuyLimit = rebuy_limit_checkbox.isSelected() ? ((Number) rebuy_limit_spinner.getValue()).intValue() : 0;
        s.botRebuy = bot_rebuy_checkbox.isSelected();
        s.rebuyCapPolicy = rebuy_cap_combo.getSelectedIndex() == 1 ? GameFrame.REBUY_CAP_HIGHEST_STACK : GameFrame.REBUY_CAP_BUYIN;
        if (doblar_checkbox.isSelected()) {
            if (double_blinds_radio_minutos.isSelected()) {
                s.doubleEvery = ((Number) doblar_ciegas_spinner_minutos.getValue()).intValue();
                s.doubleType = 1;
            } else {
                s.doubleEvery = ((Number) doblar_ciegas_spinner_manos.getValue()).intValue();
                s.doubleType = 2;
            }
        } else {
            s.doubleEvery = 0;
            s.doubleType = 1;
        }
        s.blindCap = blind_cap_checkbox.isSelected() ? blindCapSelectedBB() : 0;
        s.handLimit = manos_checkbox.isSelected() ? ((Number) manos_spinner.getValue()).intValue() : -1;
        s.ante = ante_checkbox.isSelected();
        s.straddle = straddle_checkbox.isSelected();
        s.difficulty = partida_local ? botDifficultyFromComboIndex(bots_combobox.getSelectedIndex()) : Bot.DIFFICULTY;
        return s;
    }

    private Bot.Difficulty botDifficultyFromComboIndex(int idx) {
        switch (idx) {
            case 0:
                return Bot.Difficulty.EASY;
            case 2:
                return Bot.Difficulty.HARD;
            default:
                return Bot.Difficulty.MEDIUM;
        }
    }

    private int botComboIndexFromDifficulty(Bot.Difficulty d) {
        switch (d) {
            case EASY:
                return 0;
            case HARD:
                return 2;
            default:
                return 1;
        }
    }

    // Selecciona en el combo de niveles la ciega "sb / bg" indicada, si existe.
    private void selectCurrentBlindLevel(double sb, double bg) {
        String ciegas = BlindStructure.formatLevel(sb, bg);
        int t = ciegas_combobox.getModel().getSize();
        for (int i = 0; i < t; i++) {
            if (ciegas_combobox.getItemAt(i).equals(ciegas)) {
                ciegas_combobox.setSelectedIndex(i);
                return;
            }
        }
    }

    // Vuelca un Settings a los controles del diálogo (sin tocar GameFrame salvo el
    // rango de buy-in, que el diálogo ya usa como almacén de trabajo). Mismo orden
    // que el read-back del constructor de modificar: primero los toggles/enable y al
    // final estructura -> nivel -> buy-in/tope (que dependen del nivel elegido).
    private void applySettingsToControls(GamePreset.Settings s) {
        boolean prev_init = init;
        init = false;
        try {
            // Aumentar ciegas + minutos/manos.
            doblar_checkbox.setSelected(s.doubleEvery > 0);
            double_blinds_radio_minutos.setEnabled(s.doubleEvery > 0);
            double_blinds_radio_manos.setEnabled(s.doubleEvery > 0);
            if (s.doubleType <= 1) {
                doblar_ciegas_spinner_minutos.setEnabled(s.doubleEvery > 0);
                doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(s.doubleEvery > 0 ? s.doubleEvery : 60, 1, null, 1));
                doblar_ciegas_spinner_manos.setEnabled(false);
                double_blinds_radio_minutos.setSelected(true);
                double_blinds_radio_manos.setSelected(false);
            } else {
                doblar_ciegas_spinner_manos.setEnabled(s.doubleEvery > 0);
                doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(s.doubleEvery > 0 ? s.doubleEvery : 60, 1, null, 1));
                doblar_ciegas_spinner_minutos.setEnabled(false);
                double_blinds_radio_minutos.setSelected(false);
                double_blinds_radio_manos.setSelected(true);
            }
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

            // Límite de manos.
            manos_checkbox.setSelected(s.handLimit > 0);
            manos_spinner.setEnabled(s.handLimit > 0);
            manos_spinner.setModel(new SpinnerNumberModel(s.handLimit > 0 ? s.handLimit : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(manos_spinner, false);

            // Recompra + ante + straddle.
            rebuy_checkbox.setSelected(s.rebuy);
            ante_checkbox.setSelected(s.ante);
            straddle_checkbox.setSelected(s.straddle);
            bot_rebuy_checkbox.setSelected(s.botRebuy);
            bot_rebuy_checkbox.setEnabled(s.rebuy);

            // Modo de buy-in.
            fixed_buyin_checkbox.setSelected(s.fixedBuyin);
            buyin_spinner.setEnabled(s.fixedBuyin);

            // Rango de buy-in + política de tope de recompra: GameFrame es el almacén
            // de trabajo de estos (como ya hace initBuyinRangeAndCapUI / el spinner).
            GameFrame.BUYIN_MIN_BB = s.minBb;
            GameFrame.BUYIN_MAX_BB = s.maxBb;
            GameFrame.REBUY_CAP_POLICY = s.rebuyCapPolicy;
            initBuyinRangeAndCapUI();

            // Límite de recompras.
            rebuy_limit_checkbox.setSelected(s.rebuyLimit > 0);
            rebuy_limit_checkbox.setEnabled(s.rebuy);
            rebuy_limit_spinner.setEnabled(s.rebuy && s.rebuyLimit > 0);
            rebuy_limit_spinner.setModel(new SpinnerNumberModel(s.rebuyLimit > 0 ? s.rebuyLimit : 3, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(rebuy_limit_spinner, false);
            rebuy_cap_label.setEnabled(s.rebuy);
            rebuy_cap_combo.setEnabled(s.rebuy);

            // Tope de ciega (checkbox + enable; el modelo del spinner se fija abajo).
            blind_cap_checkbox.setSelected(s.blindCap > 0);
            blind_cap_checkbox.setEnabled(s.doubleEvery > 0);

            // Estructura -> niveles del combo -> nivel actual.
            initBlindStructureUIFrom(s.structure);
            double[][] levels = s.structure != null ? s.structure : BlindStructure.defaultLevels();
            String[] items = new String[levels.length];
            for (int i = 0; i < levels.length; i++) {
                items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
            selectCurrentBlindLevel(s.smallBlind, s.bigBlind);

            // Buy-in para el nivel elegido (clamp del valor del preset al rango).
            rebuildBuyinSpinnerModel();
            SpinnerNumberModel bm = (SpinnerNumberModel) buyin_spinner.getModel();
            int blo = ((Number) bm.getMinimum()).intValue();
            int bhi = ((Number) bm.getMaximum()).intValue();
            buyin_spinner.setValue(Math.max(blo, Math.min(s.buyin, bhi)));

            // Tope de ciega: nº de subidas reconstruido desde el tope del preset.
            modelBlindCapSpinner(blindCapDoublingsFromCap(s.blindCap));
            setBlindCapControlsEnabled(s.doubleEvery > 0 && s.blindCap > 0);

            // Dificultad de los bots.
            if (partida_local) {
                bots_combobox.setSelectedIndex(botComboIndexFromDifficulty(s.difficulty));
            }
        } finally {
            init = prev_init;
        }
        packPreservingCenter();
    }

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
    private javax.swing.JPanel aumento_panel;
    private javax.swing.JLabel avatar_label;
    private javax.swing.JCheckBox ante_checkbox;
    private javax.swing.JCheckBox straddle_checkbox;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JComboBox<String> bots_combobox;
    private javax.swing.JLabel bots_label;
    private javax.swing.JPanel bots_panel;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_max_bb_spinner;
    private javax.swing.JSpinner buyin_min_bb_spinner;
    private javax.swing.JLabel buyin_range_label;
    private javax.swing.JLabel buyin_range_sep_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JButton cancel_button;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JLabel ciegas_label;
    private javax.swing.JPanel ciegas_panel;
    private javax.swing.JPanel compra_panel;
    private javax.swing.JPanel config_partida_panel;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JSpinner doblar_ciegas_spinner_manos;
    private javax.swing.JSpinner doblar_ciegas_spinner_minutos;
    private javax.swing.JRadioButton double_blinds_radio_manos;
    private javax.swing.JRadioButton double_blinds_radio_minutos;
    private javax.swing.JComboBox<String> estructura_combobox;
    private javax.swing.JLabel estructura_label;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JLabel game_label;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JLabel limite_manos_label;
    private javax.swing.JPanel main_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JTextField nick;
    private javax.swing.JLabel nick_label;
    private javax.swing.JPanel nick_pass_panel;
    private javax.swing.JPanel partida_panel;
    private javax.swing.JPasswordField pass_text;
    private javax.swing.JLabel password;
    private javax.swing.JButton preset_delete_button;
    private javax.swing.JLabel preset_label;
    private javax.swing.JButton preset_save_button;
    private javax.swing.JComboBox<String> presets_combobox;
    private javax.swing.JPanel presets_panel;
    private javax.swing.JComboBox<String> rebuy_cap_combo;
    private javax.swing.JLabel rebuy_cap_label;
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
