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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/*
    "De bien nacido es ser agradecido"

    SPECIAL THANKS TO: 

    https://www.java.com/

    https://netbeans.apache.org/

    https://poker.cs.ualberta.ca/

    https://github.com/

    https://stackoverflow.com/

    https://www.3dgifmaker.com/ 

    http://www.lcdf.org/gifsicle/

    https://www.picturetopeople.org/

    https://www.blender.org/ ( https://www.youtube.com/watch?v=0JnmWfWuMDw )

    ZERO GPT CODE

 */
public class AboutDialog extends JDialog {

    public static final String VERSION = "19.07";
    public static final String UPDATE_URL = "https://github.com/tonikelope/coronapoker/releases/latest";
    public static final String TITLE = "¿De dónde ha salido esto?";
    public static final int MAX_MOD_LOGO_HEIGHT = 75;
    public static final int MEM_TIMER = 2000;
    private volatile String last_mp3_loop = null;
    private volatile int c = 0;
    private volatile Timer memory_timer = null;

    /**
     * Creates new form About
     */
    public AboutDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);

        initComponents();
        Helpers.setTranslatedTitle(this, TITLE);

        mod_bar.setVisible(false);
        main_scroll_panel.getVerticalScrollBar().setUnitIncrement(16);
        main_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);
        memory_usage.setText(Helpers.getMemoryUsage());
        threads.setText(String.valueOf(Helpers.THREAD_POOL.getActiveCount() + 2) + "/" + String.valueOf(Helpers.THREAD_POOL.getPoolSize() + 2) + " threads");

        if (Init.MOD != null) {
            mod_label.setText(Init.MOD.get("name") + " " + Init.MOD.get("version"));

            if (Files.exists(Paths.get(Helpers.getCurrentJarParentPath() + "/mod/mod.png"))) {
                Image logo = new ImageIcon(Helpers.getCurrentJarParentPath() + "/mod/mod.png").getImage();

                if (logo.getHeight(null) > MAX_MOD_LOGO_HEIGHT || logo.getWidth(null) > MAX_MOD_LOGO_HEIGHT) {

                    int new_height = MAX_MOD_LOGO_HEIGHT;

                    int new_width = Math.round(((float) logo.getWidth(null) * MAX_MOD_LOGO_HEIGHT) / logo.getHeight(null));

                    mod_label.setIcon(new ImageIcon(logo.getScaledInstance(new_width, new_height, Image.SCALE_SMOOTH)));

                } else {
                    mod_label.setIcon(new ImageIcon(logo));

                }
            }
        } else {
            mod_label.setVisible(false);
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(new Dimension(w, h));

            setPreferredSize(getSize());

            pack();
        }

        setResizable(false);

        memory_timer = new Timer(MEM_TIMER, (ActionEvent ae) -> {
            memory_usage.setText(Helpers.getMemoryUsage());
            threads.setText(String.valueOf(Helpers.THREAD_POOL.getActiveCount() + 2) + "/" + String.valueOf(Helpers.THREAD_POOL.getPoolSize() + 2) + " threads");
        });

        memory_timer.setRepeats(true);
        memory_timer.setCoalesce(false);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        main_scroll_panel = new javax.swing.JScrollPane();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        dedicado = new javax.swing.JLabel();
        jvm = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        merecemos = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        mod_label = new javax.swing.JLabel();
        corona_icon_label = new javax.swing.JLabel();
        mod_bar = new javax.swing.JProgressBar();
        jPanel4 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        memory_usage = new javax.swing.JLabel();
        threads = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("¿De dónde ha salido esto?");
        setBackground(new java.awt.Color(255, 255, 255));
        setModal(true);
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

        main_scroll_panel.setBorder(null);
        main_scroll_panel.setDoubleBuffered(true);

        jPanel2.setBackground(new java.awt.Color(255, 255, 255));

        jLabel2.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("Gracias a todos los amigos que han colaborado en esta aventura, en especial a Pepsi por sus barajas y el \"hilo fino\",");
        jLabel2.setDoubleBuffered(true);

        dedicado.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        dedicado.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        dedicado.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/luto.png"))); // NOI18N
        dedicado.setText("En memoria de todas las víctimas de la COVID-19");
        dedicado.setDoubleBuffered(true);

        jvm.setText(Helpers.getSystemInfo());
        jvm.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jvm.setDoubleBuffered(true);
        jvm.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jvmMouseClicked(evt);
            }
        });

        jLabel3.setText("Jn 8:32");
        jLabel3.setDoubleBuffered(true);

        jLabel4.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("(Todos los céntimos desaparecidos en las betas fueron para una buena causa).");
        jLabel4.setDoubleBuffered(true);

        merecemos.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        merecemos.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        merecemos.setText("El videojuego de Texas hold 'em NL que nos merecemos, no el que necesitamos ¿o era al revés?");
        merecemos.setDoubleBuffered(true);

        jLabel6.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel6.setText("Nota: si posees el copyright de esta música (o cualquier otro elemento) y no permites su utilización, escríbeme a -> tonikelope@gmail.com");
        jLabel6.setDoubleBuffered(true);

        jLabel5.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel5.setText("a Pepillo por ese talento para cazar los bugs más raros, a Lato por las pruebas en su Mac y a mi madre... por todo lo demás.");
        jLabel5.setDoubleBuffered(true);

        jLabel7.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel7.setText("El hilo musical que suena durante el juego fue compuesto por David Luong.");
        jLabel7.setDoubleBuffered(true);

        jLabel8.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel8.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel8.setText("La canción que suena en el visor de estadísticas es el tema principal de la mítica película EL GOLPE.");
        jLabel8.setDoubleBuffered(true);

        jLabel10.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("La canción que suena aquí es \"La Sala del Trono\" compuesta por John Williams para Star Wars.");
        jLabel10.setDoubleBuffered(true);

        jLabel11.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("La canción que suena en la sala de espera es \"The Dream\" compuesta por Jerry Goldsmith para la película Total Recall.");
        jLabel11.setDoubleBuffered(true);

        jPanel1.setOpaque(false);

        jPanel3.setOpaque(false);

        mod_label.setFont(new java.awt.Font("Dialog", 0, 18)); // NOI18N
        mod_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        mod_label.setText("MOD");
        mod_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        mod_label.setDoubleBuffered(true);
        mod_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                mod_labelMouseClicked(evt);
            }
        });

        corona_icon_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        corona_icon_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_logo.gif"))); // NOI18N
        corona_icon_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        corona_icon_label.setDoubleBuffered(true);
        corona_icon_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                corona_icon_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(mod_bar, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(corona_icon_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(mod_label, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(corona_icon_label)
                .addGap(0, 0, 0)
                .addComponent(mod_bar, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(mod_label)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel4.setOpaque(false);

        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/open-book.png"))); // NOI18N
        jLabel12.setToolTipText("Reglas de Robert");
        jLabel12.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel12.setDoubleBuffered(true);
        jLabel12.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel12MouseClicked(evt);
            }
        });

        jLabel9.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/cruz.png"))); // NOI18N
        jLabel9.setText("HECHO A MANO EN ESPAÑA CON AMOR por tonikelope (c) 2020");
        jLabel9.setToolTipText("PLVS VLTRA");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addGap(0, 0, 0))
        );

        memory_usage.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        memory_usage.setDoubleBuffered(true);

        threads.setDoubleBuffered(true);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(dedicado, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(threads)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(memory_usage)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jvm))
            .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(merecemos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jLabel11, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(merecemos)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(dedicado)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6)
                .addGap(18, 18, 18)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jvm)
                        .addComponent(memory_usage)
                        .addComponent(threads))
                    .addComponent(jLabel3)))
        );

        main_scroll_panel.setViewportView(jPanel2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(main_scroll_panel)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(main_scroll_panel)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jLabel12MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel12MouseClicked
        // TODO add your handling code here:
        Helpers.openBrowserURL("https://github.com/tonikelope/coronapoker/raw/master/robert_rules.pdf");
    }//GEN-LAST:event_jLabel12MouseClicked

    private void jvmMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jvmMouseClicked
        // TODO add your handling code here:
        if (Init.M1 != null && ++c == 5) {

            try {
                Init.M1.invoke(null, this, SwingUtilities.isLeftMouseButton(evt) ? "c" : "g");
            } catch (Exception ex) {
                Logger.getLogger(AboutDialog.class.getName()).log(Level.SEVERE, null, ex);
            }

            c = 0;
        }
    }//GEN-LAST:event_jvmMouseClicked

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
    }//GEN-LAST:event_formWindowActivated

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:

        memory_timer.start();

        last_mp3_loop = Audio.getCurrentLoopMp3Playing();

        if (GameFrame.SONIDOS && last_mp3_loop != null && !Audio.MP3_LOOP_MUTED.contains(last_mp3_loop)) {
            Audio.muteLoopMp3(last_mp3_loop);
        } else {
            last_mp3_loop = null;
        }

        Audio.playLoopMp3Resource("misc/about_music.mp3");

    }//GEN-LAST:event_formWindowOpened

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        // TODO add your handling code here:
        Audio.stopLoopMp3("misc/about_music.mp3");

        if (last_mp3_loop != null) {
            Audio.unmuteLoopMp3(last_mp3_loop);
        }

        memory_timer.stop();
    }//GEN-LAST:event_formWindowClosed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        if (!mod_bar.isVisible()) {
            dispose();
        }
    }//GEN-LAST:event_formWindowClosing

    private void corona_icon_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_corona_icon_labelMouseClicked
        // TODO add your handling code here:
        Helpers.openBrowserURL("https://tonikelope.github.io/coronapoker/");
    }//GEN-LAST:event_corona_icon_labelMouseClicked

    private void mod_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mod_labelMouseClicked
        // TODO add your handling code here:

        if (!mod_bar.isVisible()) {
            mod_bar.setIndeterminate(true);
            mod_bar.setVisible(true);
            pack();

            Helpers.threadRun(() -> {
                Helpers.checkMODVersion(getContentPane());
                Helpers.GUIRun(() -> {
                    mod_bar.setVisible(false);
                    pack();
                });
            });
        }
    }//GEN-LAST:event_mod_labelMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel corona_icon_label;
    private javax.swing.JLabel dedicado;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JLabel jvm;
    private javax.swing.JScrollPane main_scroll_panel;
    private javax.swing.JLabel memory_usage;
    private javax.swing.JLabel merecemos;
    private javax.swing.JProgressBar mod_bar;
    private javax.swing.JLabel mod_label;
    private javax.swing.JLabel threads;
    // End of variables declaration//GEN-END:variables
}

/*

"If you are out to describe the truth, leave elegance to the tailor".

Albert Einstein

 */
