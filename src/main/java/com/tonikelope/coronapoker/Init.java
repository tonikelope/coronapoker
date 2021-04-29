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

import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 *
 * @author tonikelope
 */
public class Init extends javax.swing.JFrame {

    public static final boolean DEV_MODE = false;
    public static volatile String WINDOW_TITLE = "CoronaPoker " + AboutDialog.VERSION;
    public static volatile ConcurrentHashMap<String, Object> MOD = null;
    public static volatile Connection SQLITE = null;
    public static volatile boolean INIT = false;
    public static final String CORONA_DIR = System.getProperty("user.home") + "/.coronapoker";
    public static final String LOGS_DIR = CORONA_DIR + "/Logs";
    public static final String DEBUG_DIR = CORONA_DIR + "/Debug";
    public static final String SQL_FILE = CORONA_DIR + "/coronapoker.db";
    public static final int ANTI_SCREENSAVER_DELAY = 55000; //Ms

    private static volatile boolean force_close_dialog = false;

    /**
     * Creates new form Inicio
     */
    public Init() {

        Init tthis = this;

        initComponents();

        setTitle(Init.WINDOW_TITLE);

        HashMap<KeyStroke, Action> actionMap = new HashMap<>();

        KeyStroke force_exit = KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK);
        actionMap.put(force_exit, new AbstractAction("FORCE_EXIT") {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!force_close_dialog) {

                    force_close_dialog = true;

                    if (Helpers.mostrarMensajeInformativoSINO(null, "¿FORZAR CIERRE?") == 0) {
                        System.exit(1);
                    }

                    force_close_dialog = false;
                }
            }
        });

        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        kfm.addKeyEventDispatcher(
                new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

                if (actionMap.containsKey(keyStroke)) {
                    final Action a = actionMap.get(keyStroke);
                    final ActionEvent ae = new ActionEvent(e.getSource(), e.getID(), null);

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {
                            a.actionPerformed(ae);
                        }
                    });

                    return true;
                }

                return false;
            }
        }
        );

        if (GameFrame.LANGUAGE.equals(GameFrame.DEFAULT_LANGUAGE)) {
            language_combobox.setSelectedIndex(0);
        } else {
            language_combobox.setSelectedIndex(1);
        }

        setExtendedState(JFrame.MAXIMIZED_BOTH);

        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));

        if (!GameFrame.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.unMuteAll();

        }
        Helpers.updateFonts(tthis, Helpers.GUI_FONT, null);

        pack();

    }

    public JProgressBar getProgress_bar() {
        return progress_bar;
    }

    public JLabel getSound_icon() {
        return sound_icon;
    }

    public void translateGlobalLabels() {
        LocalPlayer.ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? LocalPlayer.ACTIONS_LABELS_ES : LocalPlayer.ACTIONS_LABELS_EN;
        LocalPlayer.POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? LocalPlayer.POSITIONS_LABELS_ES : LocalPlayer.POSITIONS_LABELS_EN;
        RemotePlayer.ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? RemotePlayer.ACTIONS_LABELS_ES : RemotePlayer.ACTIONS_LABELS_EN;
        RemotePlayer.POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? RemotePlayer.POSITIONS_LABELS_ES : RemotePlayer.POSITIONS_LABELS_EN;
        Hand.NOMBRES_JUGADAS = GameFrame.LANGUAGE.equals("es") ? Hand.NOMBRES_JUGADAS_ES : Hand.NOMBRES_JUGADAS_EN;

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tapete = new com.tonikelope.coronapoker.InitPanel();
        corona_init_panel = new javax.swing.JPanel();
        sound_icon = new javax.swing.JLabel();
        krusty = new javax.swing.JLabel();
        create_button = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        join_button = new javax.swing.JButton();
        pegi_panel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        language_combobox = new javax.swing.JComboBox<>();
        jButton1 = new javax.swing.JButton();
        progress_bar = new javax.swing.JProgressBar();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CoronaPoker");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });

        corona_init_panel.setOpaque(false);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        krusty.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/krusty.png"))); // NOI18N
        krusty.setToolTipText("Krusty sabe lo que se hace");

        create_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        create_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/crear.png"))); // NOI18N
        create_button.setText("CREAR TIMBA");
        create_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        create_button.setDoubleBuffered(true);
        create_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                create_buttonActionPerformed(evt);
            }
        });

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        jLabel1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel1.setDoubleBuffered(true);
        jLabel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel1MouseClicked(evt);
            }
        });

        join_button.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        join_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/unirme.png"))); // NOI18N
        join_button.setText("UNIRME A TIMBA");
        join_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        join_button.setDoubleBuffered(true);
        join_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                join_buttonActionPerformed(evt);
            }
        });

        pegi_panel.setOpaque(false);

        jLabel3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_badlanguage.png"))); // NOI18N
        jLabel3.setToolTipText("Puede contener lenguaje soez");
        jLabel3.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_gambling.png"))); // NOI18N
        jLabel4.setToolTipText("Contiene apuestas con dinero ficticio");
        jLabel4.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi16.png"))); // NOI18N
        jLabel2.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/pegi_online.png"))); // NOI18N
        jLabel6.setToolTipText("Permite jugar online");
        jLabel6.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        javax.swing.GroupLayout pegi_panelLayout = new javax.swing.GroupLayout(pegi_panel);
        pegi_panel.setLayout(pegi_panelLayout);
        pegi_panelLayout.setHorizontalGroup(
            pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pegi_panelLayout.createSequentialGroup()
                .addComponent(jLabel2)
                .addGap(18, 18, 18)
                .addComponent(jLabel3)
                .addGap(18, 18, 18)
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(jLabel6))
        );
        pegi_panelLayout.setVerticalGroup(
            pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pegi_panelLayout.createSequentialGroup()
                .addGroup(pegi_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel6)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addGap(0, 0, 0))
        );

        language_combobox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Español", "English" }));
        language_combobox.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        language_combobox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                language_comboboxActionPerformed(evt);
            }
        });

        jButton1.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        jButton1.setText("ESTADÍSTICAS");
        jButton1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton1.setDoubleBuffered(true);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout corona_init_panelLayout = new javax.swing.GroupLayout(corona_init_panel);
        corona_init_panel.setLayout(corona_init_panelLayout);
        corona_init_panelLayout.setHorizontalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(corona_init_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(progress_bar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createSequentialGroup()
                        .addComponent(krusty)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pegi_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(corona_init_panelLayout.createSequentialGroup()
                                .addComponent(language_combobox, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(join_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(create_button, javax.swing.GroupLayout.PREFERRED_SIZE, 315, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        corona_init_panelLayout.setVerticalGroup(
            corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(corona_init_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(corona_init_panelLayout.createSequentialGroup()
                        .addComponent(create_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(join_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jLabel1))
                .addGap(18, 18, Short.MAX_VALUE)
                .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(krusty, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(pegi_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(corona_init_panelLayout.createSequentialGroup()
                            .addComponent(jButton1)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(corona_init_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(sound_icon, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(language_combobox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progress_bar, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout tapeteLayout = new javax.swing.GroupLayout(tapete);
        tapete.setLayout(tapeteLayout);
        tapeteLayout.setHorizontalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tapeteLayout.createSequentialGroup()
                .addContainerGap(681, Short.MAX_VALUE)
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(681, Short.MAX_VALUE))
        );
        tapeteLayout.setVerticalGroup(
            tapeteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tapeteLayout.createSequentialGroup()
                .addContainerGap(569, Short.MAX_VALUE)
                .addComponent(corona_init_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(569, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tapete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tapete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void create_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_create_buttonActionPerformed
        // TODO add your handling code here:

        NewGameDialog dialog = new NewGameDialog(this, true, true);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            setVisible(false);
        }

    }//GEN-LAST:event_create_buttonActionPerformed

    private void join_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_join_buttonActionPerformed
        // TODO add your handling code here:
        NewGameDialog dialog = new NewGameDialog(this, true, false);

        dialog.setLocationRelativeTo(dialog.getParent());

        dialog.setVisible(true);

        if (!dialog.isDialog_ok()) {
            setVisible(true);
        } else {
            setVisible(false);
        }
    }//GEN-LAST:event_join_buttonActionPerformed

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.GUIRun(new Runnable() {
            public void run() {

                sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));

            }
        });

        if (!GameFrame.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.unMuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void jLabel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel1MouseClicked
        // TODO add your handling code here:
        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_jLabel1MouseClicked

    private void language_comboboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_language_comboboxActionPerformed
        // TODO add your handling code here:
        GameFrame.LANGUAGE = language_combobox.getSelectedIndex() == 0 ? "es" : "en";

        Helpers.PROPERTIES.setProperty("lenguaje", GameFrame.LANGUAGE);

        Helpers.savePropertiesFile();

        Helpers.translateComponents(this, true);

        translateGlobalLabels();

        Crupier.loadMODSounds();

        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
    }//GEN-LAST:event_language_comboboxActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        StatsDialog dialog = new StatsDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));
    }//GEN-LAST:event_formComponentShown

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        if (!INIT) {

            INIT = true;

            /* Set the Nimbus look and feel */
            //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
            /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
             */
            try {
                for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        javax.swing.UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(Init.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>
            //</editor-fold>

            Helpers.initSQLITE();

            try {

                Security.setProperty("securerandom.drbg.config", "Hash_DRBG,SHA-512,256,none");
                Helpers.CSPRNG_GENERATOR = SecureRandom.getInstance("DRBG");

            } catch (NoSuchAlgorithmException ex) {

                Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                Helpers.CSPRNG_GENERATOR = new SecureRandom();

            }

            Helpers.GUI_FONT = Helpers.createAndRegisterFont(Helpers.class.getResourceAsStream("/fonts/McLaren-Regular.ttf"));

            UIManager.put("OptionPane.messageFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), 14));

            UIManager.put("OptionPane.buttonFont", Helpers.GUI_FONT.deriveFont(Helpers.GUI_FONT.getStyle(), 14));

            Init.MOD = Helpers.loadMOD();

            if (Init.MOD != null) {

                WINDOW_TITLE += " @ " + MOD.get("name") + " " + MOD.get("version");

                //Cargamos las barajas del MOD
                for (Map.Entry<String, HashMap> entry : ((HashMap<String, HashMap>) Init.MOD.get("decks")).entrySet()) {

                    HashMap<String, Object> baraja = entry.getValue();

                    Card.BARAJAS.put((String) baraja.get("name"), new Object[]{baraja.get("aspect"), true, baraja.containsKey("sound") ? baraja.get("sound") : null});
                }

                if (Init.MOD.containsKey("fusion_sounds")) {
                    Crupier.FUSION_MOD_SOUNDS = (boolean) Init.MOD.get("fusion_sounds");
                }

                if (Init.MOD.containsKey("fusion_cinematics")) {
                    Crupier.FUSION_MOD_CINEMATICS = (boolean) Init.MOD.get("fusion_cinematics");
                }

                Crupier.loadMODSounds();

                Crupier.loadMODCinematics();

                //Actualizamos la fuente
                if (Init.MOD.containsKey("font") && Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")))) {

                    try {
                        Helpers.GUI_FONT = Helpers.createAndRegisterFont(new FileInputStream(Helpers.getCurrentJarParentPath() + "/mod/fonts/" + Init.MOD.get("font")));
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }

            if (!Card.BARAJAS.containsKey(GameFrame.BARAJA)) {
                GameFrame.BARAJA = GameFrame.BARAJA_DEFAULT;
            }

            Card.updateCachedImages(1f + GameFrame.getZoom_level() * GameFrame.getZOOM_STEP(), true);

            Helpers.playWavResource("misc/init.wav");

            Helpers.playLoopMp3Resource("misc/background_music.mp3");

            Init ventana = new Init();

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    ventana.setEnabled(false);

                    ventana.getProgress_bar().setIndeterminate(true);

                    Helpers.centrarJFrame(ventana, 0);

                    ventana.setVisible(true);
                }
            });

            final String new_version = Helpers.checkNewVersion(AboutDialog.UPDATE_URL);

            if (new_version != null) {

                if (Helpers.mostrarMensajeInformativoSINO(ventana, "HAY UNA VERSIÓN NUEVA DE CORONAPOKER. ¿Quieres actualizar?") == 0) {

                    try {

                        String current_jar_path = new File(Init.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();

                        String new_jar_path = current_jar_path.contains(AboutDialog.VERSION) ? current_jar_path.replaceAll(AboutDialog.VERSION, new_version) : current_jar_path;

                        if (Files.isWritable(Paths.get(current_jar_path)) && Files.isWritable(Paths.get(new_jar_path))) {

                            downloadUpdater();

                            StringBuilder java_bin = new StringBuilder();

                            java_bin.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java");

                            Runtime.getRuntime().exec(java_bin.append(" -jar ").append(System.getProperty("java.io.tmpdir") + "/coronaupdater.jar").append(" " + new_version + " " + current_jar_path + " " + new_jar_path).toString());

                        } else {

                            Helpers.mostrarMensajeError(ventana, "NO TENGO PERMISOS DE ESCRITURA.\n(TENDRÁS QUE DESCARGARTE LA ÚLTIMA VERSIÓN MANUALMENTE)");

                            Helpers.openBrowserURLAndWait("https://github.com/tonikelope/coronapoker/releases/latest");
                        }

                        System.exit(0);

                    } catch (Exception ex) {
                        Logger.getLogger(Init.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    ventana.setEnabled(true);

                    ventana.getProgress_bar().setIndeterminate(false);

                    ventana.getProgress_bar().setVisible(false);
                }
            });

            antiScreensaver();

        }
    }

    private static void downloadUpdater() throws IOException {

        HttpURLConnection con = null;

        try {

            URL url_api = new URL("https://github.com/tonikelope/coronapoker/raw/master/coronaupdater.jar");

            con = (HttpURLConnection) url_api.openConnection();

            con.addRequestProperty("User-Agent", Helpers.USER_AGENT_WEB_BROWSER);

            con.setUseCaches(false);

            try (BufferedInputStream bis = new BufferedInputStream(con.getInputStream()); BufferedOutputStream bfos = new BufferedOutputStream(new FileOutputStream(System.getProperty("java.io.tmpdir") + "/coronaupdater.jar"))) {

                byte[] buffer = new byte[1024];

                int reads;

                while ((reads = bis.read(buffer)) != -1) {

                    bfos.write(buffer, 0, reads);

                }
            }

        } finally {

            if (con != null) {
                con.disconnect();
            }
        }

    }

    private static void antiScreensaver() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent ke) {

                switch (ke.getID()) {
                    case KeyEvent.KEY_PRESSED:
                        if (ke.getKeyCode() == KeyEvent.VK_CONTROL) {
                            Helpers.ctrlPressed = true;
                        }
                        break;

                    case KeyEvent.KEY_RELEASED:
                        if (ke.getKeyCode() == KeyEvent.VK_CONTROL) {
                            Helpers.ctrlPressed = false;
                        }
                        break;
                }
                return false;

            }
        });

        java.util.Timer screensaver = new java.util.Timer();

        screensaver.schedule(new TimerTask() {
            @Override
            public void run() {
                Helpers.antiScreensaver();
            }

        }, ANTI_SCREENSAVER_DELAY, ANTI_SCREENSAVER_DELAY);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel corona_init_panel;
    private javax.swing.JButton create_button;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JButton join_button;
    private javax.swing.JLabel krusty;
    private javax.swing.JComboBox<String> language_combobox;
    private javax.swing.JPanel pegi_panel;
    private javax.swing.JProgressBar progress_bar;
    private javax.swing.JLabel sound_icon;
    private com.tonikelope.coronapoker.InitPanel tapete;
    // End of variables declaration//GEN-END:variables
}
