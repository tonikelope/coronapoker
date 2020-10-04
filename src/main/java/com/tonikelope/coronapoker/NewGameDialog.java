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

import com.tonikelope.coronapoker.Helpers.JTextFieldRegularPopupMenu;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class NewGameDialog extends javax.swing.JDialog {

    public static final int DEFAULT_PORT = 7234;
    public final static int DEFAULT_AVATAR_WIDTH = 50;
    public final static int AVATAR_MAX_FILESIZE = 256; //KB
    public final static int MAX_NICK_LENGTH = 20;
    public final static int MAX_PORT_LENGTH = 5;

    private boolean dialog_ok = false;
    private boolean partida_local;
    private File avatar = null;

    public boolean isDialog_ok() {
        return dialog_ok;
    }

    /**
     * Creates new form CrearTimba
     */
    public NewGameDialog(java.awt.Frame parent, boolean modal, boolean loc) {
        super(parent, modal);

        NewGameDialog tthis = this;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                initComponents();

                nick.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        if (nick.getText().length() >= MAX_NICK_LENGTH && nick.getSelectedText() == null) {
                            e.consume();
                        }
                    }
                });

                server_port_textfield.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        if (e.getKeyChar() < '0' || e.getKeyChar() > '9' || (server_port_textfield.getText().length() >= MAX_PORT_LENGTH && server_port_textfield.getSelectedText() == null)) {
                            e.consume();
                        }
                    }
                });

                partida_local = loc;

                JTextFieldRegularPopupMenu.addTo(server_ip_textfield);
                JTextFieldRegularPopupMenu.addTo(server_port_textfield);
                JTextFieldRegularPopupMenu.addTo(randomorg_apikey);
                JTextFieldRegularPopupMenu.addTo(nick);

                String elnick = Helpers.PROPERTIES.getProperty("nick", "");

                nick.setText(elnick.substring(0, Math.min(MAX_NICK_LENGTH, elnick.length())));

                String avatar_path = Helpers.PROPERTIES.getProperty("avatar", "");

                if (!avatar_path.isEmpty()) {

                    avatar = new File(avatar_path);

                    if (avatar.exists() && avatar.canRead() && avatar.length() <= AVATAR_MAX_FILESIZE * 1024) {
                        avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
                        avatar_img.setIcon(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

                    } else {
                        avatar = null;
                        avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
                        avatar_img.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

                    }
                } else {
                    avatar = null;
                    avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
                    avatar_img.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

                }

                Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);

                if (partida_local) {
                    server_ip_textfield.setText("localhost");
                    server_ip_textfield.setEnabled(false);
                    server_port_textfield.setText(Helpers.PROPERTIES.getProperty("local_port", String.valueOf(DEFAULT_PORT)));
                    config_partida_panel.setVisible(true);
                    random_combobox.setSelectedIndex(Integer.parseInt(Helpers.PROPERTIES.getProperty("random_generator", String.valueOf(Helpers.SPRNG))) - 1);
                    randomorg_apikey.setText(Helpers.PROPERTIES.getProperty("randomorg_api", ""));
                    rebuy_checkbox.setSelected(true);
                    doblar_checkbox.setSelected(true);
                    ((DefaultEditor) doblar_ciegas_spinner.getEditor()).getTextField().setEditable(false);

                    String[] valores = ((String) ciegas_combobox.getSelectedItem()).split("/");

                    float ciega_grande = Float.valueOf(valores[1].trim());

                    buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 3))));

                    ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

                    Helpers.setTranslatedTitle(tthis, "Crear timba");

                } else {
                    server_port_textfield.setText(Helpers.PROPERTIES.getProperty("server_port", String.valueOf(DEFAULT_PORT)));
                    server_ip_textfield.setText(Helpers.PROPERTIES.getProperty("server_ip", "localhost"));
                    Helpers.setTranslatedTitle(tthis, "Unirme a timba");
                }

                Helpers.translateComponents(tthis, false);

                pack();

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

        vamos = new javax.swing.JButton();
        server_port_textfield = new javax.swing.JTextField();
        server_ip_textfield = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        config_partida_panel = new javax.swing.JPanel();
        randomorg_label = new javax.swing.JLabel();
        random_label = new javax.swing.JLabel();
        random_combobox = new javax.swing.JComboBox<>();
        randomorg_apikey = new javax.swing.JTextField();
        buyin_spinner = new javax.swing.JSpinner();
        buyin_label = new javax.swing.JLabel();
        doblar_checkbox = new javax.swing.JCheckBox();
        doblar_ciegas_spinner = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        recover_checkbox = new javax.swing.JCheckBox();
        rebuy_checkbox = new javax.swing.JCheckBox();
        ciegas_label = new javax.swing.JLabel();
        ciegas_combobox = new javax.swing.JComboBox<>();
        jPanel1 = new javax.swing.JPanel();
        avatar_img = new javax.swing.JLabel();
        nick = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("CoronaPoker - Nueva timba");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        setMinimumSize(new java.awt.Dimension(533, 0));

        vamos.setFont(new java.awt.Font("Dialog", 1, 48)); // NOI18N
        vamos.setText("¡VAMOS!");
        vamos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        vamos.setDoubleBuffered(true);
        vamos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vamosActionPerformed(evt);
            }
        });

        server_port_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_port_textfield.setText("72345");
        server_port_textfield.setDoubleBuffered(true);

        server_ip_textfield.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        server_ip_textfield.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        server_ip_textfield.setText("localhost");
        server_ip_textfield.setDoubleBuffered(true);

        jLabel2.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        jLabel2.setText(":");
        jLabel2.setDoubleBuffered(true);

        config_partida_panel.setVisible(false);
        config_partida_panel.setOpaque(false);

        randomorg_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        randomorg_label.setText("RANDOM.ORG API KEY:");
        randomorg_label.setDoubleBuffered(true);

        random_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        random_label.setText("Generador de números aleatorios:");
        random_label.setToolTipText("Se usará para barajar las cartas");
        random_label.setDoubleBuffered(true);

        random_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        random_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Muy seguro", "Seguro", "Normal" }));
        random_combobox.setDoubleBuffered(true);
        random_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                random_comboboxActionPerformed(evt);
            }
        });

        randomorg_apikey.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        randomorg_apikey.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        randomorg_apikey.setDoubleBuffered(true);

        buyin_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        buyin_spinner.setModel(new javax.swing.SpinnerNumberModel(10, 5, null, 1));
        buyin_spinner.setDoubleBuffered(true);
        buyin_spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                buyin_spinnerStateChanged(evt);
            }
        });

        buyin_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        buyin_label.setText("Compra inicial:");
        buyin_label.setToolTipText("[10-100] ciegas grandes");
        buyin_label.setDoubleBuffered(true);

        doblar_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        doblar_checkbox.setText("Doblar ciegas (minutos):");
        doblar_checkbox.setDoubleBuffered(true);
        doblar_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doblar_checkboxActionPerformed(evt);
            }
        });

        doblar_ciegas_spinner.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        doblar_ciegas_spinner.setModel(new javax.swing.SpinnerNumberModel(60, 5, null, 5));
        doblar_ciegas_spinner.setDoubleBuffered(true);

        jLabel3.setFont(new java.awt.Font("Dialog", 2, 12)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("Nota: no olvides mapear el puerto en tu router si quieres compartir la timba por Internet");
        jLabel3.setDoubleBuffered(true);

        recover_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        recover_checkbox.setText("RECUPERAR TIMBA (o intentarlo)");
        recover_checkbox.setToolTipText("El MODO RECUPERACIÓN permite arrancar una timba que se interrumpió previamente");
        recover_checkbox.setDoubleBuffered(true);
        recover_checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recover_checkboxActionPerformed(evt);
            }
        });

        rebuy_checkbox.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        rebuy_checkbox.setText("Permitir recomprar");
        rebuy_checkbox.setToolTipText("Si algún jugador se queda sin fichas");
        rebuy_checkbox.setDoubleBuffered(true);

        ciegas_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        ciegas_label.setText("Ciegas:");
        ciegas_label.setDoubleBuffered(true);

        ciegas_combobox.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        ciegas_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0.10 / 0.20", "0.20 / 0.40", "0.50 / 1", "1 / 2", "2 / 4", "5 / 10", "10 / 20", "20 / 40", "50 / 100", "100 / 200", "200 / 400", "500 / 1000", "1000 / 2000", "2000 / 4000", "5000 / 10000", "10000 / 20000", "20000 / 40000", "50000 / 100000", "100000 / 200000", "200000 / 400000", "500000 / 1000000" }));
        ciegas_combobox.setDoubleBuffered(true);
        ciegas_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ciegas_comboboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout config_partida_panelLayout = new javax.swing.GroupLayout(config_partida_panel);
        config_partida_panel.setLayout(config_partida_panelLayout);
        config_partida_panelLayout.setHorizontalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addComponent(random_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(random_combobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(config_partida_panelLayout.createSequentialGroup()
                        .addComponent(buyin_label)
                        .addGap(38, 38, 38))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, config_partida_panelLayout.createSequentialGroup()
                        .addComponent(randomorg_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(randomorg_apikey)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.Alignment.TRAILING)))
            .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, 612, Short.MAX_VALUE)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addComponent(doblar_checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(doblar_ciegas_spinner))
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(recover_checkbox)
                    .addComponent(rebuy_checkbox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addComponent(ciegas_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        config_partida_panelLayout.setVerticalGroup(
            config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(config_partida_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(random_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(random_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(randomorg_label)
                    .addComponent(randomorg_apikey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ciegas_label)
                    .addComponent(ciegas_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buyin_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buyin_label))
                .addGap(18, 18, 18)
                .addComponent(rebuy_checkbox)
                .addGap(18, 18, 18)
                .addGroup(config_partida_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(doblar_checkbox)
                    .addComponent(doblar_ciegas_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(recover_checkbox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        avatar_img.setToolTipText("Haz click para cambiar el avatar");
        avatar_img.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar_img.setDoubleBuffered(true);
        avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
        avatar_img.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                avatar_imgMouseClicked(evt);
            }
        });

        nick.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        nick.setDoubleBuffered(true);

        jLabel1.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        jLabel1.setText("Nick:");
        jLabel1.setToolTipText("Haz click para cambiar el avatar");
        jLabel1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel1.setDoubleBuffered(true);
        jLabel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel1MouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(avatar_img, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(avatar_img, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel1)
                        .addComponent(nick, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(vamos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(server_ip_textfield)
                        .addGap(0, 0, 0)
                        .addComponent(jLabel2)
                        .addGap(0, 0, 0)
                        .addComponent(server_port_textfield, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(config_partida_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(server_ip_textfield, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel2))
                    .addComponent(server_port_textfield, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(config_partida_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(vamos, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void vamosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vamosActionPerformed
        // TODO add your handling code here:

        if (!this.nick.getText().trim().isEmpty() && !this.server_ip_textfield.getText().trim().isEmpty() && !this.server_port_textfield.getText().trim().isEmpty()) {

            Helpers.playWavResource("misc/allin.wav");

            String elnick = this.nick.getText().trim();

            Helpers.PROPERTIES.setProperty("nick", elnick);

            Helpers.PROPERTIES.setProperty("server_ip", this.server_ip_textfield.getText().trim());

            Helpers.PROPERTIES.setProperty(this.partida_local ? "local_port" : "server_port", this.server_port_textfield.getText().trim());

            if (this.config_partida_panel.isVisible()) {
                Helpers.PROPERTIES.setProperty("random_generator", String.valueOf(Helpers.DECK_RANDOM_GENERATOR));
                Helpers.PROPERTIES.setProperty("randomorg_api", this.randomorg_apikey.getText().trim());
                Helpers.DECK_RANDOM_GENERATOR = this.random_combobox.getSelectedIndex() + 1;
                Helpers.RANDOM_ORG_APIKEY = this.randomorg_apikey.getText().trim();
            }

            if (this.avatar != null) {
                Helpers.PROPERTIES.setProperty("avatar", this.avatar.getAbsolutePath());
            } else {
                Helpers.PROPERTIES.setProperty("avatar", "");
            }

            Helpers.savePropertiesFile();

            Game.setRECOVER(this.recover_checkbox.isSelected());

            Game.REBUY = this.rebuy_checkbox.isSelected();

            Game.BUYIN = (int) this.buyin_spinner.getValue();

            String[] valores_ciegas = ((String) ciegas_combobox.getSelectedItem()).split("/");

            Game.CIEGA_GRANDE = Float.valueOf(valores_ciegas[1].trim());

            Game.CIEGA_PEQUEÑA = Float.valueOf(valores_ciegas[0].trim());

            if (this.doblar_checkbox.isSelected()) {
                Game.CIEGAS_TIME = (int) this.doblar_ciegas_spinner.getValue();
            } else {
                Game.CIEGAS_TIME = 0;
            }

            this.dialog_ok = true;

            WaitingRoom espera = new WaitingRoom((Init) getParent(), partida_local, elnick, server_ip_textfield.getText().trim() + ":" + server_port_textfield.getText().trim(), avatar);

            espera.setLocationRelativeTo(this);

            setVisible(false);

            espera.setVisible(true);

        } else {
            Helpers.mostrarMensajeError((JFrame) this.getParent(), "Te falta algún campo obligatorio por completar");
        }

    }//GEN-LAST:event_vamosActionPerformed

    private void random_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_random_comboboxActionPerformed
        // TODO add your handling code here:

        Helpers.DECK_RANDOM_GENERATOR = this.random_combobox.getSelectedIndex() + 1;

        this.randomorg_label.setVisible(Helpers.DECK_RANDOM_GENERATOR == Helpers.TRNG);
        this.randomorg_apikey.setVisible(Helpers.DECK_RANDOM_GENERATOR == Helpers.TRNG);

        if (Helpers.DECK_RANDOM_GENERATOR == Helpers.TRNG) {
            this.randomorg_apikey.setText(Helpers.PROPERTIES.getProperty("randomorg_api", ""));
            Helpers.RANDOM_ORG_APIKEY = Helpers.PROPERTIES.getProperty("randomorg_api", "");
        }

        pack();
    }//GEN-LAST:event_random_comboboxActionPerformed

    private void avatar_imgMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatar_imgMouseClicked
        // TODO add your handling code here:
        JFileChooser fileChooser = new JFileChooser();

        FileFilter imageFilter = new FileNameExtensionFilter("Image files", ImageIO.getReaderFileSuffixes());

        fileChooser.setFileFilter(imageFilter);

        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            if (selectedFile.length() > NewGameDialog.AVATAR_MAX_FILESIZE * 1024) {
                Helpers.mostrarMensajeError((JFrame) this.getParent(), "MAX: " + NewGameDialog.AVATAR_MAX_FILESIZE + " KB");
            } else {
                this.avatar = selectedFile;

                avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
                avatar_img.setIcon(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

            }

        } else {

            this.avatar = null;

            avatar_img.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            avatar_img.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

        }
    }//GEN-LAST:event_avatar_imgMouseClicked

    private void doblar_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doblar_checkboxActionPerformed
        // TODO add your handling code here:
        this.doblar_ciegas_spinner.setEnabled(this.doblar_checkbox.isSelected());
    }//GEN-LAST:event_doblar_checkboxActionPerformed

    private void buyin_spinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_buyin_spinnerStateChanged
        // TODO add your handling code here:
        Helpers.playWavResource("misc/cash_register.wav");
        // updateCiegasLabel();
    }//GEN-LAST:event_buyin_spinnerStateChanged

    private void jLabel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel1MouseClicked
        // TODO add your handling code here:
        avatar_imgMouseClicked(evt);
    }//GEN-LAST:event_jLabel1MouseClicked

    private void recover_checkboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recover_checkboxActionPerformed
        // TODO add your handling code here:

        if (this.recover_checkbox.isSelected()) {

            if (Files.exists(Paths.get(Crupier.RECOVER_BALANCE_FILE))) {

                try {
                    String datos = Files.readString(Paths.get(Crupier.RECOVER_BALANCE_FILE));

                    String[] partes = datos.split("#");

                    this.buyin_spinner.setEnabled(false);

                    this.buyin_label.setEnabled(false);

                    this.rebuy_checkbox.setEnabled(false);

                    this.ciegas_label.setEnabled(false);

                    this.ciegas_combobox.setEnabled(false);

                    this.doblar_ciegas_spinner.setEnabled(false);

                    this.doblar_checkbox.setEnabled(false);

                    this.nick.setText(new String(Base64.decodeBase64(partes[0]), "UTF-8"));

                    this.nick.setEnabled(false);

                    Helpers.mostrarMensajeInformativo((JFrame) this.getParent(), "En el MODO RECUPERACIÓN se continuará la timba anterior desde donde se paró:\n\n1) Es OBLIGATORIO que los jugadores antiguos usen los MISMOS NICKS.\n\n2) Para poder continuar desde el PUNTO EXACTO (con la mismas cartas) es OBLIGATORIO que se conecten TODOS los jugadores antiguos.\nSi esto no es posible, se \"perderá\" la mano que estaba en curso cuando se interrumpió la timba.\n\n3) Está permitido que se unan a la timba jugadores nuevos (estarán la primera mano de espectadores).");

                } catch (IOException ex) {
                    Logger.getLogger(NewGameDialog.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                this.recover_checkbox.setSelected(false);
            }
        } else {
            this.buyin_spinner.setEnabled(true);

            this.buyin_label.setEnabled(true);

            this.rebuy_checkbox.setEnabled(true);

            this.ciegas_label.setEnabled(true);

            this.ciegas_combobox.setEnabled(true);

            this.doblar_ciegas_spinner.setEnabled(true);

            this.doblar_checkbox.setEnabled(true);

            this.nick.setEnabled(true);
        }
    }//GEN-LAST:event_recover_checkboxActionPerformed

    private void ciegas_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ciegas_comboboxActionPerformed
        // TODO add your handling code here:

        String[] valores = ((String) ciegas_combobox.getSelectedItem()).split("/");

        float ciega_grande = Float.valueOf(valores[1].trim());

        buyin_spinner.setModel(new SpinnerNumberModel((int) (ciega_grande * 50f), (int) (ciega_grande * 10f), (int) (ciega_grande * 100f), (int) Math.pow(10, Math.floor(ciegas_combobox.getSelectedIndex() / 3))));

        ((DefaultEditor) buyin_spinner.getEditor()).getTextField().setEditable(false);

    }//GEN-LAST:event_ciegas_comboboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_img;
    private javax.swing.JLabel buyin_label;
    private javax.swing.JSpinner buyin_spinner;
    private javax.swing.JComboBox<String> ciegas_combobox;
    private javax.swing.JLabel ciegas_label;
    private javax.swing.JPanel config_partida_panel;
    private javax.swing.JCheckBox doblar_checkbox;
    private javax.swing.JSpinner doblar_ciegas_spinner;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField nick;
    private javax.swing.JComboBox<String> random_combobox;
    private javax.swing.JLabel random_label;
    private javax.swing.JTextField randomorg_apikey;
    private javax.swing.JLabel randomorg_label;
    private javax.swing.JCheckBox rebuy_checkbox;
    private javax.swing.JCheckBox recover_checkbox;
    private javax.swing.JTextField server_ip_textfield;
    private javax.swing.JTextField server_port_textfield;
    private javax.swing.JButton vamos;
    // End of variables declaration//GEN-END:variables
}
