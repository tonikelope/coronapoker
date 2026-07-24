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

import javax.swing.SpinnerNumberModel;

/**
 * Contenido de "Ajustes de partida" como JPanel (pestaña del diálogo unificado):
 * las REGLAS de juego a la izquierda (sin subpanel) y las CIEGAS a la derecha (en
 * un subpanel titulado). Para clientes se construye en SOLO-LECTURA. Toda la lógica
 * de ciegas (dinero-sensible) es la del antiguo EditBlindsDialog. Se aplica con
 * {@link #applyToGame()} (lo dispara el botón GUARDAR del diálogo unificado);
 * apariencia y audio se aplican en vivo, las reglas/ciegas solo al guardar.
 *
 * @author tonikelope
 */
public class GameSettingsPanel extends javax.swing.JPanel {

    private volatile boolean init = false;

    private final boolean read_only;

    // Firma de los valores de los controles al ABRIR; isDirty() compara con la actual
    // para saber si Partida tiene cambios sin guardar (la pestaña aplica al GUARDAR).
    private String snap_signature;

    // Selector de estructura de ciegas: permite ELEGIR (no crear) una estructura ya
    // guardada o "Por defecto" durante la partida; al GUARDAR fija ACTIVE_BLIND_STRUCTURE
    // y la propaga a los clientes. pending_structure = elegida (null = por defecto).
    private String item_estructura_por_defecto;
    private String item_estructura_actual;
    private BlindStructure pending_structure;
    // Estructura ACTIVA no guardada (ítem sintético "(actual)"); se guarda aparte para
    // poder restaurarla si el usuario navega Por defecto -> (actual) sin perderla.
    private BlindStructure actual_structure;

    // Reglas (izquierda, sin subpanel)
    private javax.swing.JPanel rules_panel;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JLabel manos_label;
    private javax.swing.JSpinner manos_spinner;
    // Tiempo de pensar: SOLO lectura en partida (no cambiable una vez empezada).
    private javax.swing.JCheckBox think_time_checkbox;
    private javax.swing.JLabel think_time_label;
    private javax.swing.JSpinner think_time_spinner;
    // Tiempo de showdown: SOLO lectura en partida (no cambiable una vez empezada). Sin casilla:
    // la pausa no se puede desactivar.
    private javax.swing.JLabel showdown_time_label;
    private javax.swing.JSpinner showdown_time_spinner;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JLabel iwtsth_label;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rit_label;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;

    // Ciegas (derecha, subpanel titulado)
    private javax.swing.JPanel ciegas_panel;
    private javax.swing.JLabel estructura_label;
    private javax.swing.JComboBox<String> estructura_combobox;
    private javax.swing.JLabel ciegas_label;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JRadioButton double_blinds_radio_minutos;
    private javax.swing.JRadioButton double_blinds_radio_manos;
    private javax.swing.JSpinner doblar_ciegas_spinner_minutos;
    private javax.swing.JSpinner doblar_ciegas_spinner_manos;
    private javax.swing.JPanel blind_cap_panel;
    private javax.swing.JCheckBox blind_cap_checkbox;
    private javax.swing.JSpinner blind_cap_spinner;
    private javax.swing.JLabel blind_cap_label;
    private javax.swing.JCheckBox ante_checkbox;
    private javax.swing.JCheckBox straddle_checkbox;
    private javax.swing.JLabel straddle_label;

    // Bots (subpanel, debajo de reglas|ciegas). Dificultad y recompra en SOLO LECTURA en partida (no
    // cambiables una vez empezada); "repartir saldo" SÍ es editable (inocuo: solo afecta a la 2ª tabla
    // del registro al terminar, no toca la auditoría). Para clientes todo va en solo-lectura.
    private javax.swing.JPanel bots_panel;
    private javax.swing.JLabel bots_avatar_label;
    private javax.swing.JLabel bots_label;
    private javax.swing.JComboBox<String> bots_combobox;
    private javax.swing.JCheckBox bot_rebuy_checkbox;
    private javax.swing.JCheckBox bot_balance_checkbox;

    // Compra + recompra (subpanel, SOLO INFORMATIVO en partida: el buy-in y la economía de recompra
    // quedan fijados al empezar la timba; se muestran DESHABILITADOS como información).
    private javax.swing.JPanel compra_panel;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JCheckBox fixed_buyin_checkbox;
    private javax.swing.JLabel buyin_range_label;
    private javax.swing.JSpinner buyin_min_bb_spinner;
    private javax.swing.JLabel buyin_range_sep_label;
    private javax.swing.JSpinner buyin_max_bb_spinner;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JLabel recomprar_label;
    private javax.swing.JCheckBox rebuy_limit_checkbox;
    private javax.swing.JSpinner rebuy_limit_spinner;
    private javax.swing.JLabel rebuy_cap_label;
    private javax.swing.JComboBox<String> rebuy_cap_combo;

    // Tooltips i18n (setTranslatedToolTip => se re-traducen al cambiar idioma) de los controles de
    // configuración cuya función no es obvia por su etiqueta. Se llama tras initComponents().
    private void setupTooltips() {
        Helpers.setTranslatedToolTip(manos_checkbox, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(manos_label, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(manos_spinner, "tooltip.cfg.hand_limit");
        Helpers.setTranslatedToolTip(think_time_checkbox, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_label, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(think_time_spinner, "tooltip.cfg.think_time");
        Helpers.setTranslatedToolTip(showdown_time_label, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(showdown_time_spinner, "tooltip.cfg.showdown_time");
        Helpers.setTranslatedToolTip(iwtsth_checkbox, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(iwtsth_label, "tooltip.cfg.iwtsth");
        Helpers.setTranslatedToolTip(rit_checkbox, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rit_label, "tooltip.cfg.rit");
        Helpers.setTranslatedToolTip(rabbit_combo, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(rabbit_label, "tooltip.cfg.rabbit");
        Helpers.setTranslatedToolTip(estructura_combobox, "tooltip.cfg.structure");
        Helpers.setTranslatedToolTip(ciegas_combobox, "tooltip.cfg.blinds_level");
        Helpers.setTranslatedToolTip(doblar_checkbox, "tooltip.cfg.double_blinds");
        Helpers.setTranslatedToolTip(blind_cap_checkbox, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(blind_cap_spinner, "tooltip.cfg.blind_cap");
        Helpers.setTranslatedToolTip(ante_checkbox, "tooltip.cfg.ante");
        Helpers.setTranslatedToolTip(straddle_checkbox, "tooltip.cfg.straddle");
        Helpers.setTranslatedToolTip(bots_combobox, "tooltip.cfg.bots");
        Helpers.setTranslatedToolTip(bot_rebuy_checkbox, "tooltip.cfg.bot_rebuy");
        Helpers.setTranslatedToolTip(bot_balance_checkbox, "tooltip.cfg.bot_balance");
        Helpers.setTranslatedToolTip(fixed_buyin_checkbox, "tooltip.cfg.buyin_fixed");
        Helpers.setTranslatedToolTip(buyin_min_bb_spinner, "tooltip.buyin_range");
        Helpers.setTranslatedToolTip(buyin_max_bb_spinner, "tooltip.buyin_range");
        Helpers.setTranslatedToolTip(rebuy_checkbox, "tooltip.rebuy_description");
        Helpers.setTranslatedToolTip(recomprar_label, "tooltip.rebuy_description");
        Helpers.setTranslatedToolTip(rebuy_limit_checkbox, "tooltip.cfg.rebuy_limit");
        Helpers.setTranslatedToolTip(rebuy_limit_spinner, "tooltip.cfg.rebuy_limit");
    }

    public GameSettingsPanel(boolean read_only) {
        this.read_only = read_only;
        initComponents();

        setupTooltips();

        // ============================ CIEGAS ============================
        // El combo de niveles refleja SIEMPRE la escalera efectiva (la estructura
        // activa o, sin ella, la escalera por defecto), no la lista fija del
        // diseñador, para que incluya todos los niveles de defaultLevels().
        {
            double[][] levels = GameFrame.ACTIVE_BLIND_STRUCTURE != null
                    ? GameFrame.ACTIVE_BLIND_STRUCTURE : BlindStructure.defaultLevels();
            String[] items = new String[levels.length];
            for (int k = 0; k < levels.length; k++) {
                items[k] = BlindStructure.formatLevel(levels[k][0], levels[k][1]);
            }
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
        }

        Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
        Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);

        blind_cap_spinner.addChangeListener((javax.swing.event.ChangeEvent e) -> updateBlindCapLabel());
        ciegas_combobox.addActionListener((java.awt.event.ActionEvent e) -> {
            if (init) {
                modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());
            }
            updateAnteStraddleLabels();
        });

        // Estructura de ciegas: refleja la activa y permite ELEGIR otra ya guardada
        // durante la partida (no crear). Al cambiar, repuebla el combo de niveles.
        initStructureCombo();
        estructura_combobox.addActionListener((java.awt.event.ActionEvent e) -> {
            if (init) {
                applySelectedStructure();
            }
        });

        double peque, grande;
        int ciegas_double, ciegas_double_type;

        if (GameFrame.getInstance().getCrupier().getCiegas_update() != null) {
            peque = (double) GameFrame.getInstance().getCrupier().getCiegas_update()[0];
            grande = (double) GameFrame.getInstance().getCrupier().getCiegas_update()[1];
            ciegas_double = (int) GameFrame.getInstance().getCrupier().getCiegas_update()[2];
            ciegas_double_type = (int) GameFrame.getInstance().getCrupier().getCiegas_update()[3];
        } else {
            peque = GameFrame.getInstance().getCrupier().getCiega_pequeña();
            grande = GameFrame.getInstance().getCrupier().getCiega_grande();
            ciegas_double = GameFrame.CIEGAS_DOUBLE;
            ciegas_double_type = GameFrame.CIEGAS_DOUBLE_TYPE;
        }

        this.ante_checkbox.setSelected(GameFrame.ANTE);
        this.straddle_checkbox.setSelected(GameFrame.STRADDLE);

        this.doblar_checkbox.setSelected(ciegas_double > 0);
        double_blinds_radio_minutos.setEnabled(ciegas_double > 0);
        double_blinds_radio_manos.setEnabled(ciegas_double > 0);

        if (ciegas_double_type <= 1) {
            doblar_ciegas_spinner_minutos.setEnabled(ciegas_double > 0);
            doblar_ciegas_spinner_minutos.setModel(new SpinnerNumberModel(ciegas_double > 0 ? ciegas_double : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            double_blinds_radio_minutos.setSelected(true);
            double_blinds_radio_manos.setSelected(false);
        } else {
            doblar_ciegas_spinner_manos.setEnabled(ciegas_double > 0);
            doblar_ciegas_spinner_manos.setModel(new SpinnerNumberModel(ciegas_double > 0 ? ciegas_double : 60, 1, null, 1));
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_manos, false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            Helpers.makeNumericSpinnerEditable(doblar_ciegas_spinner_minutos, false);
            double_blinds_radio_minutos.setSelected(false);
            double_blinds_radio_manos.setSelected(true);
        }

        String ciegas = BlindStructure.formatLevel(peque, grande);
        int i = 0, t = this.ciegas_combobox.getModel().getSize();
        while (i < t) {
            if (this.ciegas_combobox.getItemAt(i).equals(ciegas)) {
                break;
            }
            i++;
        }
        if (i < t) {
            this.ciegas_combobox.setSelectedIndex(i);
        }

        this.blind_cap_checkbox.setSelected(GameFrame.BLIND_CAP > 0f);
        this.blind_cap_checkbox.setEnabled(ciegas_double > 0);
        modelBlindCapSpinner(blindCapDoublingsFromCap());
        this.blind_cap_spinner.setEnabled(ciegas_double > 0 && GameFrame.BLIND_CAP > 0f);
        this.blind_cap_label.setEnabled(ciegas_double > 0 && GameFrame.BLIND_CAP > 0f);

        // ============================ PARTIDA (reglas) ============================
        int mano_actual = GameFrame.getInstance().getCrupier().getMano();
        int manos_min = Math.max(1, mano_actual + 1);
        boolean manos_on = GameFrame.MANOS != -1;
        manos_spinner.setModel(new SpinnerNumberModel(manos_on ? Math.max(GameFrame.MANOS, manos_min) : Math.max(100, manos_min), manos_min, null, 1));
        Helpers.makeNumericSpinnerEditable(manos_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) manos_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        manos_checkbox.setSelected(manos_on);
        manos_spinner.setEnabled(manos_on);

        // Tiempo de pensar: SOLO lectura en partida (no cambiable una vez empezada). Muestra
        // el valor vigente y se DESHABILITA para host y cliente (no entra en el apply).
        think_time_checkbox.setSelected(GameFrame.THINK_TIME_ENABLED);
        think_time_spinner.setModel(new SpinnerNumberModel(Math.max(GameFrame.THINK_TIME_MIN, Math.min(GameFrame.THINK_TIME_MAX, GameFrame.THINK_TIME)), GameFrame.THINK_TIME_MIN, GameFrame.THINK_TIME_MAX, 5));
        Helpers.makeNumericSpinnerEditable(think_time_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) think_time_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        think_time_checkbox.setEnabled(false);
        think_time_spinner.setEnabled(false);

        // Tiempo de showdown: SOLO lectura en partida (no cambiable una vez empezada). Muestra el
        // valor vigente y se DESHABILITA (no entra en ningún apply). Sin casilla: no desactivable.
        showdown_time_spinner.setModel(new SpinnerNumberModel(Math.max(GameFrame.SHOWDOWN_TIME_MIN, Math.min(GameFrame.SHOWDOWN_TIME_MAX, GameFrame.SHOWDOWN_TIME)), GameFrame.SHOWDOWN_TIME_MIN, GameFrame.SHOWDOWN_TIME_MAX, 5));
        Helpers.makeNumericSpinnerEditable(showdown_time_spinner, false);
        ((javax.swing.JSpinner.DefaultEditor) showdown_time_spinner.getEditor()).getTextField().setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        showdown_time_spinner.setEnabled(false);

        iwtsth_checkbox.setSelected(GameFrame.IWTSTH_RULE);
        rit_checkbox.setSelected(GameFrame.RUN_IT_TWICE);

        rabbit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("menu.off"),
            Translator.translate("menu.free"),
            Translator.translate("menu.free_sb"),
            Translator.translate("menu.free_sb_bb")
        }));
        rabbit_combo.setSelectedIndex(Math.min(Math.max(GameFrame.RABBIT_HUNTING, 0), 3));

        java.awt.Font title_font = ante_checkbox.getFont();
        ciegas_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_ciegas")));
        ((javax.swing.border.TitledBorder) ciegas_panel.getBorder()).setTitleFont(title_font);

        // Las reglas (límite de manos, IWTSTH, RIT, rabbit) van en un subpanel "Varios".
        rules_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("settings.varios")));
        ((javax.swing.border.TitledBorder) rules_panel.getBorder()).setTitleFont(title_font);

        // Bots: dificultad y "recomprar bots" en SOLO LECTURA (no cambiables una vez empezada la timba);
        // "repartir saldo entre humanos" SÍ es editable en partida (inocuo). El combo refleja la
        // dificultad vigente (server-local). Para cliente todo queda deshabilitado en el bloque read_only.
        bots_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_bots")));
        ((javax.swing.border.TitledBorder) bots_panel.getBorder()).setTitleFont(title_font);
        bots_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("ui.bots_facil"), Translator.translate("ui.bots_media"), Translator.translate("ui.bots_dificil")}));
        bots_combobox.setSelectedIndex(Bot.DIFFICULTY == Bot.Difficulty.EASY ? 0 : (Bot.DIFFICULTY == Bot.Difficulty.HARD ? 2 : 1));
        bots_combobox.setEnabled(false);
        bot_rebuy_checkbox.setSelected(GameFrame.BOT_REBUY);
        bot_rebuy_checkbox.setEnabled(false);
        bot_balance_checkbox.setSelected(GameFrame.BOT_BALANCE_TO_HUMANS);

        // Compra + recompra: SOLO INFORMATIVO (todo fijado al empezar la timba). Se puebla con la config
        // vigente y se deshabilita entero (host y cliente).
        compra_panel.setBorder(javax.swing.BorderFactory.createTitledBorder(Translator.translate("newgame.grupo_compra")));
        ((javax.swing.border.TitledBorder) compra_panel.getBorder()).setTitleFont(title_font);
        buyin_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN), 1, null, 1));
        fixed_buyin_checkbox.setSelected(GameFrame.FIXED_BUYIN);
        buyin_min_bb_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN_MIN_BB), 1, null, 1));
        buyin_max_bb_spinner.setModel(new SpinnerNumberModel(Math.max(1, GameFrame.BUYIN_MAX_BB), 1, null, 1));
        rebuy_checkbox.setSelected(GameFrame.REBUY);
        rebuy_limit_checkbox.setSelected(GameFrame.REBUY_LIMIT > 0);
        if (GameFrame.REBUY_LIMIT > 0) {
            rebuy_limit_spinner.setModel(new SpinnerNumberModel(GameFrame.REBUY_LIMIT, 1, null, 1));
        }
        rebuy_cap_combo.setSelectedIndex(GameFrame.REBUY_CAP_POLICY == GameFrame.REBUY_CAP_HIGHEST_STACK ? 1 : 0);
        for (javax.swing.JComponent comp : new javax.swing.JComponent[]{buyin_label, buyin_spinner, fixed_buyin_checkbox, buyin_range_label, buyin_min_bb_spinner, buyin_range_sep_label, buyin_max_bb_spinner, rebuy_checkbox, recomprar_label, rebuy_limit_checkbox, rebuy_limit_spinner, rebuy_cap_label, rebuy_cap_combo}) {
            comp.setEnabled(false);
        }

        Helpers.translateComponents(this, false);

        // Importe ACTUAL de ante (= ciega pequeña) y straddle (= 2x ciega grande) entre
        // paréntesis, igual que en el diálogo de nueva timba; se refresca al cambiar el
        // nivel de ciegas (listener del combo).
        updateAnteStraddleLabels();

        init = true;

        if (read_only) {
            ciegas_combobox.setEnabled(false);
            doblar_checkbox.setEnabled(false);
            double_blinds_radio_minutos.setEnabled(false);
            double_blinds_radio_manos.setEnabled(false);
            doblar_ciegas_spinner_minutos.setEnabled(false);
            doblar_ciegas_spinner_manos.setEnabled(false);
            blind_cap_checkbox.setEnabled(false);
            blind_cap_spinner.setEnabled(false);
            blind_cap_label.setEnabled(false);
            ante_checkbox.setEnabled(false);
            straddle_checkbox.setEnabled(false);
            manos_checkbox.setEnabled(false);
            manos_spinner.setEnabled(false);
            iwtsth_checkbox.setEnabled(false);
            rit_checkbox.setEnabled(false);
            rabbit_combo.setEnabled(false);
            estructura_combobox.setEnabled(false);
            // Cliente: también "repartir saldo" en solo-lectura (solo el host lo cambia y lo difunde).
            bot_balance_checkbox.setEnabled(false);
        } else if (GameFrame.RUN_IT_TWICE_LOCKED) {
            rit_checkbox.setEnabled(false);
        }

        snap_signature = controlsSignature();
    }

    // Firma compacta de TODOS los controles editables; comparar dos firmas dice si algo
    // cambió. (deshabilitar controles no cambia sus valores, así que es estable.)
    private String controlsSignature() {
        return manos_checkbox.isSelected() + "|" + manos_spinner.getValue() + "|"
                + iwtsth_checkbox.isSelected() + "|" + rit_checkbox.isSelected() + "|"
                + rabbit_combo.getSelectedIndex() + "|" + ciegas_combobox.getSelectedIndex() + "|"
                + doblar_checkbox.isSelected() + "|" + double_blinds_radio_minutos.isSelected() + "|"
                + double_blinds_radio_manos.isSelected() + "|" + doblar_ciegas_spinner_minutos.getValue() + "|"
                + doblar_ciegas_spinner_manos.getValue() + "|" + blind_cap_checkbox.isSelected() + "|"
                + blind_cap_spinner.getValue() + "|" + ante_checkbox.isSelected() + "|"
                + straddle_checkbox.isSelected() + "|"
                + String.valueOf(estructura_combobox.getSelectedItem()) + "|"
                + bot_balance_checkbox.isSelected();
    }

    // ¿Hay cambios sin guardar en la pestaña Partida? Lo usa el diálogo para preguntar
    // antes de descartar al cancelar.
    public boolean isDirty() {
        return !controlsSignature().equals(snap_signature);
    }

    public boolean isReadOnly() {
        return read_only;
    }

    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));

        // Ambos subpaneles (reglas | ciegas) van lado a lado en un BoxLayout X (ver 'row'): dejamos su
        // alto MÁXIMO libre (solo capamos el ancho) para que el MÁS CORTO de los dos se estire hasta
        // igualar el alto del más alto, de modo que sus bordes titulados queden alineados también por
        // abajo. El hueco sobrante cae DENTRO del borde del más corto (su contenido queda pegado arriba).
        // El maxWidth se hereda intacto (no toca el reparto horizontal de la fila).
        rules_panel = new javax.swing.JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(super.getMaximumSize().width, Short.MAX_VALUE);
            }
        };
        manos_checkbox = new javax.swing.JCheckBox();
        manos_label = new javax.swing.JLabel();
        manos_spinner = new javax.swing.JSpinner();
        think_time_checkbox = new javax.swing.JCheckBox();
        think_time_label = new javax.swing.JLabel();
        think_time_spinner = new javax.swing.JSpinner();
        showdown_time_label = new javax.swing.JLabel();
        showdown_time_spinner = new javax.swing.JSpinner();
        iwtsth_checkbox = new javax.swing.JCheckBox();
        iwtsth_label = new javax.swing.JLabel();
        rit_checkbox = new javax.swing.JCheckBox();
        rit_label = new javax.swing.JLabel();
        rabbit_label = new javax.swing.JLabel();
        rabbit_combo = new javax.swing.JComboBox<>();

        // Mismo criterio que rules_panel: alto máximo libre para que, si resulta el más corto de la
        // fila, se estire hasta igualar al más alto y ambos bordes titulados queden alineados por abajo.
        ciegas_panel = new javax.swing.JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(super.getMaximumSize().width, Short.MAX_VALUE);
            }
        };
        estructura_label = new javax.swing.JLabel();
        estructura_combobox = new javax.swing.JComboBox<>();
        ciegas_label = new javax.swing.JLabel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        doblar_checkbox = new javax.swing.JCheckBox();
        double_blinds_radio_minutos = new javax.swing.JRadioButton();
        double_blinds_radio_manos = new javax.swing.JRadioButton();
        doblar_ciegas_spinner_minutos = new javax.swing.JSpinner();
        doblar_ciegas_spinner_manos = new javax.swing.JSpinner();
        blind_cap_panel = new javax.swing.JPanel();
        blind_cap_checkbox = new javax.swing.JCheckBox();
        blind_cap_spinner = new javax.swing.JSpinner();
        blind_cap_label = new javax.swing.JLabel();
        ante_checkbox = new javax.swing.JCheckBox();
        straddle_checkbox = new javax.swing.JCheckBox();
        straddle_label = new javax.swing.JLabel();

        bots_panel = new javax.swing.JPanel();
        bots_avatar_label = new javax.swing.JLabel();
        bots_label = new javax.swing.JLabel();
        bots_combobox = new javax.swing.JComboBox<>();
        bot_rebuy_checkbox = new javax.swing.JCheckBox();
        bot_balance_checkbox = new javax.swing.JCheckBox();

        compra_panel = new javax.swing.JPanel();
        buyin_label = new javax.swing.JLabel();
        buyin_spinner = new javax.swing.JSpinner();
        fixed_buyin_checkbox = new javax.swing.JCheckBox();
        buyin_range_label = new javax.swing.JLabel();
        buyin_min_bb_spinner = new javax.swing.JSpinner();
        buyin_range_sep_label = new javax.swing.JLabel();
        buyin_max_bb_spinner = new javax.swing.JSpinner();
        rebuy_checkbox = new javax.swing.JCheckBox();
        recomprar_label = new javax.swing.JLabel();
        rebuy_limit_checkbox = new javax.swing.JCheckBox();
        rebuy_limit_spinner = new javax.swing.JSpinner();
        rebuy_cap_label = new javax.swing.JLabel();
        rebuy_cap_combo = new javax.swing.JComboBox<>();

        // ---------------- Reglas (izquierda, sin subpanel) ----------------
        manos_label.setFont(new java.awt.Font("Dialog", 1, 16));
        manos_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png")));
        manos_label.setText("Límite de manos:");
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        manos_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                manos_checkbox.doClick();
            }
        });

        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        think_time_label.setFont(new java.awt.Font("Dialog", 1, 16));
        think_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png")));
        think_time_label.setText("Tiempo de pensar:");
        think_time_label.putClientProperty("i18n.key", "newgame.tiempo_pensar");

        think_time_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        think_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        think_time_spinner.setModel(new javax.swing.SpinnerNumberModel(40, 10, 120, 5));
        think_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        showdown_time_label.setFont(new java.awt.Font("Dialog", 1, 16));
        showdown_time_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/clock.png")));
        showdown_time_label.setText("Tiempo de showdown:");
        showdown_time_label.putClientProperty("i18n.key", "newgame.tiempo_showdown");

        showdown_time_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        showdown_time_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, 30, 5));
        showdown_time_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        iwtsth_label.setFont(new java.awt.Font("Dialog", 1, 16));
        iwtsth_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/eyes.png")));
        iwtsth_label.setText("Regla IWTSTH");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");
        iwtsth_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        iwtsth_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                iwtsth_checkbox.doClick();
            }
        });

        iwtsth_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/baraja.png")));
        rit_label.setText("ALL-IN Run-it-twice");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rit_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        rit_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                rit_checkbox.doClick();
            }
        });

        rit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rabbit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rabbit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rabbit.png")));
        rabbit_label.setText("Rabbit Hunting");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");

        rabbit_combo.setFont(new java.awt.Font("Dialog", 0, 16));
        rabbit_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout rules_panelLayout = new javax.swing.GroupLayout(rules_panel);
        rules_panel.setLayout(rules_panelLayout);
        rules_panelLayout.setHorizontalGroup(
            rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rules_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(manos_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(manos_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(think_time_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(think_time_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(showdown_time_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(140 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(iwtsth_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(iwtsth_label))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(rit_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(rit_label))
                    .addGroup(rules_panelLayout.createSequentialGroup()
                        .addComponent(rabbit_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        rules_panelLayout.setVerticalGroup(
            rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rules_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(manos_checkbox)
                    .addComponent(manos_label)
                    .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(think_time_checkbox)
                    .addComponent(think_time_label)
                    .addComponent(think_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(showdown_time_label)
                    .addComponent(showdown_time_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iwtsth_checkbox)
                    .addComponent(iwtsth_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rit_checkbox)
                    .addComponent(rit_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(rules_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Ciegas (derecha, subpanel titulado) ----------------
        estructura_label.setFont(new java.awt.Font("Dialog", 1, 14));
        estructura_label.setText("Estructura:");
        estructura_label.putClientProperty("i18n.key", "blinds.estructura");

        estructura_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        estructura_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16));
        ciegas_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png")));
        ciegas_label.setText("Ciegas iniciales:");
        ciegas_label.putClientProperty("i18n.key", "blinds.ciegas_iniciales");

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,10 / 0,20" : "0.10 / 0.20", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,20 / 0,40" : "0.20 / 0.40", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,30 / 0,60" : "0.30 / 0.60", GameFrame.LANGUAGE.toLowerCase().equals("es") ? "0,50 / 1" : "0.50 / 1", "1 / 2", "2 / 4", "3 / 6", "5 / 10", "10 / 20", "20 / 40", "30 / 60", "50 / 100", "100 / 200", "200 / 400", "300 / 600", "500 / 1000", "1000 / 2000", "2000 / 4000", "3000 / 6000", "5000 / 10000"}));
        ciegas_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        doblar_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        doblar_checkbox.setText("Aumentar ciegas");
        doblar_checkbox.putClientProperty("i18n.key", "blinds.aumentar_ciegas");
        doblar_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        doblar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doblar_checkboxActionPerformed(evt);
            }
        });

        double_blinds_radio_manos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_manos.setText("Manos:");
        double_blinds_radio_manos.putClientProperty("i18n.key", "game.manos");
        double_blinds_radio_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_manos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_manosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_manos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_manos.setModel(new javax.swing.SpinnerNumberModel(30, 1, null, 1));
        doblar_ciegas_spinner_manos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        double_blinds_radio_minutos.setFont(new java.awt.Font("Dialog", 1, 14));
        double_blinds_radio_minutos.setText("Minutos:");
        double_blinds_radio_minutos.putClientProperty("i18n.key", "ui.minutos");
        double_blinds_radio_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        double_blinds_radio_minutos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                double_blinds_radio_minutosActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner_minutos.setFont(new java.awt.Font("Dialog", 0, 16));
        doblar_ciegas_spinner_minutos.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        doblar_ciegas_spinner_minutos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        blind_cap_panel.setBorder(new RoundedLineBorder(new java.awt.Color(153, 153, 153), 1, 12));

        blind_cap_checkbox.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_checkbox.setText("Tope ciega grande");
        blind_cap_checkbox.putClientProperty("i18n.key", "blinds.tope_ciega_grande");
        blind_cap_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blind_cap_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blind_cap_checkboxActionPerformed(evt);
            }
        });

        blind_cap_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        blind_cap_spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, null, 1));
        blind_cap_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        blind_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        blind_cap_label.setText("0 / 0");

        javax.swing.GroupLayout blind_cap_panelLayout = new javax.swing.GroupLayout(blind_cap_panel);
        blind_cap_panel.setLayout(blind_cap_panelLayout);
        blind_cap_panelLayout.setHorizontalGroup(
            blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(blind_cap_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(blind_cap_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(100 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(blind_cap_label))
                .addContainerGap())
        );
        blind_cap_panelLayout.setVerticalGroup(
            blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(blind_cap_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(blind_cap_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(blind_cap_checkbox)
                    .addComponent(blind_cap_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blind_cap_label)
                .addContainerGap())
        );

        ante_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        ante_checkbox.setText("Ante");
        ante_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        straddle_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        // Ficha del straddle como los demas iconos de regla (checkbox sin texto + label
        // con icono y texto). El icono es straddle.png reducido, al tamano de menu (24px).
        straddle_label.setFont(new java.awt.Font("Dialog", 1, 16));
        straddle_label.setIcon(new javax.swing.ImageIcon(new javax.swing.ImageIcon(getClass().getResource("/images/straddle_small.png")).getImage().getScaledInstance(24, 24, java.awt.Image.SCALE_SMOOTH)));
        straddle_label.setText("Straddle");
        straddle_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        straddle_label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                if (!Helpers.isRealClick(evt)) {
                    return;
                }
                straddle_checkbox.doClick();
            }
        });

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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(estructura_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ciegas_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(doblar_checkbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(double_blinds_radio_minutos)
                            .addComponent(double_blinds_radio_manos))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(blind_cap_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(ciegas_panelLayout.createSequentialGroup()
                        .addComponent(ante_checkbox)
                        .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
                        .addComponent(straddle_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(straddle_label)))
                .addContainerGap())
        );
        ciegas_panelLayout.setVerticalGroup(
            ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ciegas_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(estructura_label)
                    .addComponent(estructura_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(doblar_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_manos)
                    .addComponent(doblar_ciegas_spinner_manos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(double_blinds_radio_minutos)
                    .addComponent(doblar_ciegas_spinner_minutos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(blind_cap_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ciegas_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ante_checkbox)
                    .addComponent(straddle_checkbox)
                    .addComponent(straddle_label))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Compra + recompra (SOLO INFORMATIVO en partida) ----------------
        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png")));
        buyin_label.setText("Compra inicial:");
        buyin_label.putClientProperty("i18n.key", "blinds.compra_inicial");

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));

        fixed_buyin_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        fixed_buyin_checkbox.setText("Buy-in fijo");
        fixed_buyin_checkbox.putClientProperty("i18n.key", "newgame.buyin_fijo");

        buyin_range_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_label.setText("Rango compra (CG):");
        buyin_range_label.putClientProperty("i18n.key", "blinds.rango_compra");

        buyin_min_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_min_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));

        buyin_range_sep_label.setFont(new java.awt.Font("Dialog", 1, 16));
        buyin_range_sep_label.setText("a");
        buyin_range_sep_label.putClientProperty("i18n.key", "blinds.rango_a");

        buyin_max_bb_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        buyin_max_bb_spinner.setModel(new javax.swing.SpinnerNumberModel(100, 1, null, 1));

        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));

        recomprar_label.setFont(new java.awt.Font("Dialog", 1, 16));
        recomprar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rebuy.png")));
        recomprar_label.setText("Recomprar");
        recomprar_label.putClientProperty("i18n.key", "rebuy.recomprar_2");

        rebuy_limit_checkbox.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_limit_checkbox.setText("Límite recompra por jugador");
        rebuy_limit_checkbox.putClientProperty("i18n.key", "rebuy.limite_por_jugador");

        rebuy_limit_spinner.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_limit_spinner.setModel(new javax.swing.SpinnerNumberModel(3, 1, null, 1));

        rebuy_cap_label.setFont(new java.awt.Font("Dialog", 1, 14));
        rebuy_cap_label.setText("Tope recompra:");
        rebuy_cap_label.putClientProperty("i18n.key", "rebuy.tope_recompra");

        rebuy_cap_combo.setFont(new java.awt.Font("Dialog", 0, 14));
        rebuy_cap_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("rebuy.cap_policy_buyin"), Translator.translate("rebuy.cap_policy_highest")}));

        javax.swing.GroupLayout compra_panelLayout = new javax.swing.GroupLayout(compra_panel);
        compra_panel.setLayout(compra_panelLayout);
        compra_panelLayout.setHorizontalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(buyin_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(90 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM), Math.round(18 * Helpers.DIALOG_ZOOM))
                        .addComponent(fixed_buyin_checkbox))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(buyin_range_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_range_sep_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_checkbox)
                        .addGap(0, 0, 0)
                        .addComponent(recomprar_label))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_limit_checkbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(80 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(compra_panelLayout.createSequentialGroup()
                        .addComponent(rebuy_cap_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        compra_panelLayout.setVerticalGroup(
            compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(compra_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_label)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fixed_buyin_checkbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_range_label)
                    .addComponent(buyin_min_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buyin_range_sep_label)
                    .addComponent(buyin_max_bb_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(rebuy_checkbox)
                    .addComponent(recomprar_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_limit_checkbox)
                    .addComponent(rebuy_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(compra_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rebuy_cap_label)
                    .addComponent(rebuy_cap_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // ---------------- Bots (subpanel, debajo de reglas|ciegas) ----------------
        Helpers.setScaledIconLabel(bots_avatar_label, getClass().getResource("/images/avatar_bot.png"), 48, 48);

        bots_label.setFont(new java.awt.Font("Dialog", 1, 16));
        bots_label.setText("Dificultad de los bots:");
        bots_label.putClientProperty("i18n.key", "ui.bots_dificultad");

        bots_combobox.setFont(new java.awt.Font("Dialog", 0, 16));
        bots_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        bot_rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 14));
        bot_rebuy_checkbox.setText("Recomprar bots");
        bot_rebuy_checkbox.putClientProperty("i18n.key", "rebuy.permitir_bots");
        bot_rebuy_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        bot_balance_checkbox.setFont(new java.awt.Font("Dialog", 1, 14));
        bot_balance_checkbox.setText("Repartir saldo de bots entre humanos");
        bot_balance_checkbox.putClientProperty("i18n.key", "balance.repartir_saldo_bots");
        bot_balance_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout bots_panelLayout = new javax.swing.GroupLayout(bots_panel);
        bots_panel.setLayout(bots_panelLayout);
        bots_panelLayout.setHorizontalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(bots_panelLayout.createSequentialGroup()
                        .addComponent(bots_avatar_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bots_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(bot_rebuy_checkbox)
                    .addComponent(bot_balance_checkbox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        bots_panelLayout.setVerticalGroup(
            bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(bots_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(bots_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(bots_avatar_label)
                    .addComponent(bots_label)
                    .addComponent(bots_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bot_rebuy_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bot_balance_checkbox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // Rejilla 2x2 con el MISMO orden y disposición que la pestaña "Partida" de la SALA DE ESPERA
        // (WaitingGameSettingsPanel): Compra | Ciegas / Varios(reglas) | Bots. GridBagLayout liga el
        // ancho de cada columna ENTRE las dos filas (col. izquierda idéntica en Compra y Varios; col.
        // derecha idéntica en Ciegas y Bots) y fill BOTH estira cada subpanel hasta el alto de su vecino
        // de fila, de modo que los bordes titulados queden alineados. weighty 0 -> las filas quedan a su
        // alto natural y el sobrante vertical del diálogo cae limpio DEBAJO (al CENTER).
        javax.swing.JPanel grid = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.fill = java.awt.GridBagConstraints.BOTH;
        gc.weightx = 0.5;
        gc.weighty = 0.0;

        gc.gridx = 0;
        gc.gridy = 0;
        gc.insets = new java.awt.Insets(0, 0, 8, 6);
        grid.add(compra_panel, gc);
        gc.gridx = 1;
        gc.insets = new java.awt.Insets(0, 6, 8, 0);
        grid.add(ciegas_panel, gc);
        gc.gridx = 0;
        gc.gridy = 1;
        gc.insets = new java.awt.Insets(0, 0, 0, 6);
        grid.add(rules_panel, gc);
        gc.gridx = 1;
        gc.insets = new java.awt.Insets(0, 6, 0, 0);
        grid.add(bots_panel, gc);

        add(grid, java.awt.BorderLayout.NORTH);

        // i18n de las etiquetas con icono (se traducen en translateComponents).
        manos_label.putClientProperty("i18n.key", "game.limite_de_manos");
        iwtsth_label.putClientProperty("i18n.key", "menu.regla_iwtsth");
        rit_label.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");
    }

    // ===================== Aplicar (lo dispara GUARDAR del diálogo unificado) =====================
    public void applyToGame() {

        if (read_only) {
            return;
        }

        // ---- Estructura de ciegas elegida (null = escalera por defecto). Se fija ANTES
        // de las ciegas (el combo de niveles ya refleja esta estructura) y se propaga a
        // los clientes dentro de UPDATEBLINDS; el crupier la lee directo para la subida
        // automática. ----
        double[][] new_structure = selectedStructureLevels();
        boolean structure_changed = !java.util.Arrays.deepEquals(new_structure, GameFrame.ACTIVE_BLIND_STRUCTURE);
        GameFrame.ACTIVE_BLIND_STRUCTURE = new_structure;
        final String structure_str = (new_structure != null) ? BlindStructure.levelsToString(new_structure) : "";

        // ---- Ciegas (idéntico a EditBlindsDialog) ----
        int ciegas_double, ciegas_double_type;

        if (this.doblar_checkbox.isSelected()) {
            if (this.double_blinds_radio_minutos.isSelected()) {
                ciegas_double = (int) this.doblar_ciegas_spinner_minutos.getValue();
                ciegas_double_type = 1;
            } else {
                ciegas_double = (int) this.doblar_ciegas_spinner_manos.getValue();
                ciegas_double_type = 2;
            }
        } else {
            ciegas_double = 0;
            ciegas_double_type = 1;
        }

        double blind_cap = (this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected()) ? blindCapSelectedBB() : 0;
        GameFrame.BLIND_CAP = blind_cap;

        boolean ante_nuevo = this.ante_checkbox.isSelected();
        boolean straddle_nuevo = this.straddle_checkbox.isSelected();

        // El ante/straddle se siguen aplicando y difundiendo al instante (más abajo,
        // en UPDATEBLINDS); si cambian, además, se marca el aviso diferido para que
        // salga el indicador amarillo y el popup en la próxima mano, igual que con
        // las ciegas.
        if (GameFrame.ANTE != ante_nuevo || GameFrame.STRADDLE != straddle_nuevo) {
            GameFrame.getInstance().getCrupier().marcarCambioAnteStraddle();
        }

        GameFrame.ANTE = ante_nuevo;
        GameFrame.STRADDLE = straddle_nuevo;

        String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).replace(",", ".").split("/");

        GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(Double.valueOf(valores_ciegas[0].trim()), Double.valueOf(valores_ciegas[1].trim()), ciegas_double, ciegas_double_type);

        // ---- Límite de manos: misma semántica que CommunityCardsPanel.click_max_hands ----
        int old_manos = GameFrame.MANOS;
        int desired_manos;
        if (!manos_checkbox.isSelected()) {
            desired_manos = -1;
        } else {
            int v = (int) manos_spinner.getValue();
            desired_manos = (GameFrame.getInstance().getCrupier().getMano() < v) ? v : old_manos;
        }
        final boolean manos_changed = desired_manos != old_manos;
        if (manos_changed) {
            GameFrame.MANOS = desired_manos;
        }

        boolean iwtsth = iwtsth_checkbox.isSelected();
        boolean rit = rit_checkbox.isSelected();
        int rabbit = rabbit_combo.getSelectedIndex();

        final int ciegas_double_f = ciegas_double, ciegas_double_type_f = ciegas_double_type;
        final double blind_cap_f = blind_cap;
        final String sb = valores_ciegas[0].trim(), bb = valores_ciegas[1].trim();

        Helpers.threadRun(() -> {
            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("UPDATEBLINDS#" + String.valueOf(ciegas_double_f) + "#" + String.valueOf(ciegas_double_type_f) + "#" + sb + "#" + bb + "#" + String.valueOf(blind_cap_f) + "#" + String.valueOf(GameFrame.ANTE) + "#" + String.valueOf(GameFrame.STRADDLE) + "#" + structure_str, null);
            if (manos_changed) {
                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);
            }
            GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
        });

        if (iwtsth != GameFrame.IWTSTH_RULE) {
            GameFrame.setIwtsthRule(iwtsth);
        }
        if (rit != GameFrame.RUN_IT_TWICE) {
            GameFrame.setRunItTwiceRule(rit);
        }
        if (rabbit != GameFrame.RABBIT_HUNTING) {
            GameFrame.setRabbitHunting(rabbit);
        }

        // Reparto del saldo de bots entre humanos: editable en partida (inocuo). setBotBalanceToHumans
        // difunde a los clientes y persiste en recover.
        if (bot_balance_checkbox.isSelected() != GameFrame.BOT_BALANCE_TO_HUMANS) {
            GameFrame.setBotBalanceToHumans(bot_balance_checkbox.isSelected());
        }

        // La estructura va en el fósil de recover (serializeRecoverSettings la incluye)
        // para que sobreviva a un detener+recuperar; persistir si cambió.
        if (structure_changed) {
            GameFrame.persistRecoverSettings(GameFrame.getInstance().getCrupier().getSqlite_game_id());
        }
    }

    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            manos_spinner.setEnabled(manos_checkbox.isSelected());
        }
    }

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            this.doblar_ciegas_spinner_minutos.setEnabled(this.double_blinds_radio_minutos.isSelected() && this.doblar_checkbox.isSelected());
            this.doblar_ciegas_spinner_manos.setEnabled(this.double_blinds_radio_manos.isSelected() && this.doblar_checkbox.isSelected());
            this.double_blinds_radio_manos.setEnabled(this.doblar_checkbox.isSelected());
            this.double_blinds_radio_minutos.setEnabled(this.doblar_checkbox.isSelected());
            this.blind_cap_checkbox.setEnabled(this.doblar_checkbox.isSelected());
            this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
            this.blind_cap_label.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
        }
    }

    private void double_blinds_radio_minutosActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            if (this.double_blinds_radio_minutos.isSelected()) {
                this.doblar_ciegas_spinner_minutos.setEnabled(true);
                this.double_blinds_radio_manos.setSelected(false);
                this.doblar_ciegas_spinner_manos.setEnabled(false);
            } else {
                this.double_blinds_radio_minutos.setSelected(true);
            }
        }
    }

    private void double_blinds_radio_manosActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            if (this.double_blinds_radio_manos.isSelected()) {
                this.doblar_ciegas_spinner_manos.setEnabled(true);
                this.double_blinds_radio_minutos.setSelected(false);
                this.doblar_ciegas_spinner_minutos.setEnabled(false);
            } else {
                this.double_blinds_radio_manos.setSelected(true);
            }
        }
    }

    private void blind_cap_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            this.blind_cap_spinner.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
            this.blind_cap_label.setEnabled(this.doblar_checkbox.isSelected() && this.blind_cap_checkbox.isSelected());
        }
    }

    private double parseBlindLevelBB(String item) {
        return Double.parseDouble(item.replace(",", ".").split("/")[1].trim());
    }

    private int blindCapTargetIndex(int n) {
        int last = ciegas_combobox.getModel().getSize() - 1;
        return Math.min(Math.max(0, ciegas_combobox.getSelectedIndex()) + n, last);
    }

    private double blindCapSelectedBB() {
        return parseBlindLevelBB(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private void updateBlindCapLabel() {
        blind_cap_label.setText(ciegas_combobox.getItemAt(blindCapTargetIndex(((Number) blind_cap_spinner.getValue()).intValue())));
    }

    private int blindCapDoublingsFromCap() {
        int initial = Math.max(0, ciegas_combobox.getSelectedIndex());
        if (GameFrame.BLIND_CAP > 0f) {
            for (int k = initial + 1; k < ciegas_combobox.getModel().getSize(); k++) {
                if (Helpers.doubleSecureCompare(parseBlindLevelBB(ciegas_combobox.getItemAt(k)), GameFrame.BLIND_CAP) == 0) {
                    return k - initial;
                }
            }
        }
        return 5;
    }

    private void modelBlindCapSpinner(int n) {
        int levels_above = Math.max(1, ciegas_combobox.getModel().getSize() - 1 - Math.max(0, ciegas_combobox.getSelectedIndex()));
        n = Math.min(Math.max(1, n), levels_above);
        this.blind_cap_spinner.setModel(new SpinnerNumberModel(n, 1, levels_above, 1));
        Helpers.makeNumericSpinnerEditable(blind_cap_spinner, false);
        updateBlindCapLabel();
    }

    // Texto informativo de ante/straddle con su importe ACTUAL entre paréntesis (ante =
    // ciega pequeña, straddle = 2x ciega grande), leído del nivel de ciegas seleccionado.
    // Mismo criterio que NewGameDialog.updateAnteStraddleLabels().
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
            straddle_label.setText("Straddle (" + Helpers.money2String(Helpers.doubleClean(2 * bb)) + ")");
        } catch (NumberFormatException ignored) {
        }
    }

    // Inicializa el selector de estructura desde la ACTIVA, sin tocar el combo de
    // niveles (ya poblado en el constructor). Si la activa no coincide con ninguna
    // guardada, la representa con un ítem sintético "(actual)" para no perderla.
    private void initStructureCombo() {
        pending_structure = null;
        item_estructura_actual = null;
        String selectName = null;
        double[][] active = GameFrame.ACTIVE_BLIND_STRUCTURE;
        if (active != null) {
            for (java.util.Map.Entry<String, BlindStructure> en : BlindStructure.loadAll().entrySet()) {
                if (java.util.Arrays.deepEquals(en.getValue().getLevels(), active)) {
                    pending_structure = en.getValue();
                    selectName = en.getKey();
                    break;
                }
            }
            if (pending_structure == null) {
                try {
                    pending_structure = new BlindStructure(Translator.translate("blinds.estructura_actual"), active);
                    item_estructura_actual = pending_structure.getName();
                    actual_structure = pending_structure;
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        populateStructureCombo(selectName);
    }

    // (Re)llena el combo: "Por defecto" + (estructura activa no guardada, si la hay) +
    // estructuras guardadas. NO incluye "Gestionar…": aquí solo se ELIGE.
    private void populateStructureCombo(String selectName) {
        boolean prev_init = init;
        init = false;
        try {
            item_estructura_por_defecto = Translator.translate("blinds.estructura_por_defecto");
            estructura_combobox.removeAllItems();
            estructura_combobox.addItem(item_estructura_por_defecto);
            if (item_estructura_actual != null) {
                estructura_combobox.addItem(item_estructura_actual);
            }
            for (String name : BlindStructure.loadAll().keySet()) {
                estructura_combobox.addItem(name);
            }
            if (selectName != null) {
                estructura_combobox.setSelectedItem(selectName);
            } else if (item_estructura_actual != null) {
                estructura_combobox.setSelectedItem(item_estructura_actual);
            } else {
                estructura_combobox.setSelectedItem(item_estructura_por_defecto);
            }
        } finally {
            init = prev_init;
        }
    }

    // Aplica al combo de NIVELES la estructura elegida, conservando el ESCALÓN actual
    // por POSICIÓN (no por valor). Si la timba va por el nivel N de la escalera vieja
    // (índice N del combo = número de subidas de ciega si se arrancó desde el primer
    // nivel), el combo salta al nivel N de la nueva estructura, topado al último si es
    // más corta. Así el cambio mantiene el mismo número de saltos en lugar del valor
    // exacto; si el usuario quiere otro nivel, lo ajusta a mano antes de guardar.
    // Fija pending_structure.
    private void applySelectedStructure() {
        Object sel = estructura_combobox.getSelectedItem();
        if (sel == null) {
            return;
        }
        // Escalón actual en la escalera que el combo muestra AHORA (antes de repoblar).
        int prev_index = Math.max(0, ciegas_combobox.getSelectedIndex());
        double[][] levels;
        if (sel.equals(item_estructura_por_defecto)) {
            pending_structure = null;
            levels = BlindStructure.defaultLevels();
        } else if (item_estructura_actual != null && sel.equals(item_estructura_actual)) {
            pending_structure = actual_structure;
            levels = actual_structure != null ? actual_structure.getLevels() : BlindStructure.defaultLevels();
        } else {
            BlindStructure bs = BlindStructure.loadAll().get((String) sel);
            pending_structure = bs;
            levels = bs != null ? bs.getLevels() : BlindStructure.defaultLevels();
        }
        String[] items = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            items[i] = BlindStructure.formatLevel(levels[i][0], levels[i][1]);
        }
        int target = Math.min(prev_index, levels.length - 1);
        boolean prev_init = init;
        init = false;
        try {
            ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(items));
            ciegas_combobox.setSelectedIndex(target);
        } finally {
            init = prev_init;
        }
        // Recalcular el tope de ciega grande para la nueva escalera + etiquetas.
        modelBlindCapSpinner(((Number) blind_cap_spinner.getValue()).intValue());
        updateAnteStraddleLabels();
    }

    // Niveles de la estructura ELEGIDA (null = escalera por defecto), para aplicarlos a
    // GameFrame.ACTIVE_BLIND_STRUCTURE al guardar.
    private double[][] selectedStructureLevels() {
        return pending_structure != null ? pending_structure.getLevels() : null;
    }

}
