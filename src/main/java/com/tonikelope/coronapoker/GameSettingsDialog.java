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

import javax.swing.JDialog;
import javax.swing.SpinnerNumberModel;

/**
 * Diálogo "Ajustes de partida": consolida las reglas de juego que el host puede
 * cambiar EN VIVO (límite de manos, IWTSTH, Run It Twice, Rabbit Hunting). Para
 * los clientes (no host) se abre en SOLO-LECTURA, informativo, exactamente igual
 * que el diálogo de ciegas (EditBlindsDialog): ven la configuración actual sin
 * poder cambiarla. La fuente de verdad son los campos estáticos de GameFrame
 * (sincronizados a los clientes vía INIT + comandos *RULE), por lo que el diálogo
 * nunca consulta la red al abrirse. Al guardar, el host aplica cada regla con los
 * setters estáticos de GameFrame (set*Rule / MANOS), que difunden + persisten.
 *
 * Hecho a mano (sin .form NetBeans): es un diálogo sencillo y estable; el mismo
 * patrón que el resto de UI añadida a mano del proyecto.
 *
 * @author tonikelope
 */
public class GameSettingsDialog extends JDialog {

    private volatile boolean init = false;

    // Modo informativo: los clientes (no host) ABREN el diálogo para ver la
    // configuración actual, con todo deshabilitado y sin guardar.
    private final boolean read_only;

    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JCheckBox manos_checkbox;
    private javax.swing.JSpinner manos_spinner;
    private javax.swing.JCheckBox iwtsth_checkbox;
    private javax.swing.JCheckBox rit_checkbox;
    private javax.swing.JLabel rabbit_label;
    private javax.swing.JComboBox<String> rabbit_combo;
    private javax.swing.JButton vamos_button;
    private javax.swing.JButton cancel_button;

    public GameSettingsDialog(java.awt.Frame parent, boolean modal) {
        this(parent, modal, false);
    }

    public GameSettingsDialog(java.awt.Frame parent, boolean modal, boolean read_only) {
        super(parent, modal);
        this.read_only = read_only;
        initComponents();

        Helpers.setTranslatedTitle(this, "settings.ajustes_partida");

        // Límite de manos: misma semántica que el spinner de NewGameDialog y de
        // click_max_hands — no se puede fijar un tope por debajo de las manos ya
        // jugadas, así que el mínimo del spinner es (mano actual + 1). Desmarcado =
        // ilimitado (-1).
        int mano_actual = GameFrame.getInstance().getCrupier().getMano();
        int manos_min = Math.max(1, mano_actual + 1);
        boolean manos_on = GameFrame.MANOS != -1;
        manos_spinner.setModel(new SpinnerNumberModel(manos_on ? Math.max(GameFrame.MANOS, manos_min) : manos_min, manos_min, null, 1));
        Helpers.makeNumericSpinnerEditable(manos_spinner, false);
        manos_checkbox.setSelected(manos_on);
        manos_spinner.setEnabled(manos_on);

        iwtsth_checkbox.setSelected(GameFrame.IWTSTH_RULE);
        rit_checkbox.setSelected(GameFrame.RUN_IT_TWICE);

        // El combo se reconstruye en cada apertura con los textos del idioma actual
        // (índice 0..3 = GameFrame.RABBIT_HUNTING off/free/free+sb/free+sb+bb).
        rabbit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            Translator.translate("menu.off"),
            Translator.translate("menu.free"),
            Translator.translate("menu.free_sb"),
            Translator.translate("menu.free_sb_bb")
        }));
        rabbit_combo.setSelectedIndex(Math.min(Math.max(GameFrame.RABBIT_HUNTING, 0), 3));

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);
        Helpers.translateComponents(this, false);

        pack();

        init = true;

        if (read_only) {
            // Solo-lectura para clientes: ven la configuración actual pero no la
            // pueden cambiar. Cancelar sigue activo para cerrar.
            manos_checkbox.setEnabled(false);
            manos_spinner.setEnabled(false);
            iwtsth_checkbox.setEnabled(false);
            rit_checkbox.setEnabled(false);
            rabbit_combo.setEnabled(false);
            vamos_button.setEnabled(false);
        }
    }

    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        manos_checkbox = new javax.swing.JCheckBox();
        manos_spinner = new javax.swing.JSpinner();
        iwtsth_checkbox = new javax.swing.JCheckBox();
        rit_checkbox = new javax.swing.JCheckBox();
        rabbit_label = new javax.swing.JLabel();
        rabbit_combo = new javax.swing.JComboBox<>();
        vamos_button = new javax.swing.JButton();
        cancel_button = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setUndecorated(true);

        jPanel2.setBackground(new java.awt.Color(255, 255, 255));
        jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 102, 0), 10));

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));

        manos_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        manos_checkbox.setText("Límite de manos:");
        manos_checkbox.putClientProperty("i18n.key", "game.limite_de_manos");
        manos_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        manos_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manos_checkboxActionPerformed(evt);
            }
        });

        manos_spinner.setFont(new java.awt.Font("Dialog", 0, 16));
        manos_spinner.setModel(new javax.swing.SpinnerNumberModel(60, 1, null, 1));
        manos_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        iwtsth_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        iwtsth_checkbox.setText("Regla IWTSTH");
        iwtsth_checkbox.putClientProperty("i18n.key", "menu.regla_iwtsth");
        iwtsth_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rit_checkbox.setFont(new java.awt.Font("Dialog", 1, 16));
        rit_checkbox.setText("ALL-IN Run-it-twice");
        rit_checkbox.putClientProperty("i18n.key", "menu.regla_run_it_twice");
        rit_checkbox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        rabbit_label.setFont(new java.awt.Font("Dialog", 1, 16));
        rabbit_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/rabbit.png")));
        rabbit_label.setText("Rabbit Hunting");
        rabbit_label.putClientProperty("i18n.key", "menu.rabbit_hunting");

        rabbit_combo.setFont(new java.awt.Font("Dialog", 0, 16));
        rabbit_combo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(manos_checkbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(iwtsth_checkbox)
                    .addComponent(rit_checkbox)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(rabbit_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manos_checkbox)
                    .addComponent(manos_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(iwtsth_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rit_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rabbit_label)
                    .addComponent(rabbit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jLabel1.setBackground(new java.awt.Color(255, 255, 255));
        jLabel1.setFont(new java.awt.Font("Dialog", 1, 30));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/gear.png")));
        jLabel1.setText("AJUSTES DE PARTIDA");
        jLabel1.putClientProperty("i18n.key", "settings.ajustes_partida_titulo");
        jLabel1.setOpaque(true);

        vamos_button.setBackground(new java.awt.Color(0, 130, 0));
        vamos_button.setFont(new java.awt.Font("Dialog", 1, 18));
        vamos_button.setForeground(new java.awt.Color(255, 255, 255));
        vamos_button.setText("GUARDAR");
        vamos_button.putClientProperty("i18n.key", "ui.guardar");
        vamos_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        vamos_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vamos_buttonActionPerformed(evt);
            }
        });

        cancel_button.setFont(new java.awt.Font("Dialog", 1, 18));
        cancel_button.setText("Cancelar");
        cancel_button.putClientProperty("i18n.key", "ui.cancelar_2");
        cancel_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(vamos_button)
                        .addGap(18, 18, 18)
                        .addComponent(cancel_button))
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(vamos_button)
                    .addComponent(cancel_button))
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
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }

    private void manos_checkboxActionPerformed(java.awt.event.ActionEvent evt) {
        if (init) {
            manos_spinner.setEnabled(manos_checkbox.isSelected());
        }
    }

    private void vamos_buttonActionPerformed(java.awt.event.ActionEvent evt) {

        // Límite de manos: misma semántica que CommunityCardsPanel.click_max_hands —
        // 0/desmarcado = ilimitado (-1); no se puede bajar por debajo de las manos ya
        // jugadas (el mínimo del spinner ya lo garantiza, pero re-comprobamos); solo
        // se difunde MAXHANDS si el valor cambia.
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

        // Reglas globales: aplicar solo las que cambian (cada setter difunde +
        // persiste). Se leen ANTES de los setters (que mutan el flag en segundo
        // plano) para comparar con el estado actual.
        boolean iwtsth = iwtsth_checkbox.isSelected();
        boolean rit = rit_checkbox.isSelected();
        int rabbit = rabbit_combo.getSelectedIndex();

        setVisible(false);

        if (iwtsth != GameFrame.IWTSTH_RULE) {
            GameFrame.setIwtsthRule(iwtsth);
        }

        if (rit != GameFrame.RUN_IT_TWICE) {
            GameFrame.setRunItTwiceRule(rit);
        }

        if (rabbit != GameFrame.RABBIT_HUNTING) {
            GameFrame.setRabbitHunting(rabbit);
        }

        if (manos_changed) {
            Helpers.threadRun(() -> {
                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);
                GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
            });
        }
    }

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {
        setVisible(false);
    }
}
