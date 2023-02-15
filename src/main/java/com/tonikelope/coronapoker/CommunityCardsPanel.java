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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 *
 * @author tonikelope
 */
public class CommunityCardsPanel extends javax.swing.JPanel implements ZoomableInterface {

    public static final int SOUND_ICON_WIDTH = 30;

    private volatile Color color_contadores = null;
    private volatile int hand_label_click_type = 0;
    private volatile boolean ready = false;
    private volatile Timer icon_zoom_timer = null;
    private final Object zoom_lock = new Object();

    public void lightsButtonClick() {
        if (!GameFrame.getInstance().getLocalPlayer().isRADAR_ckecking()) {
            Helpers.GUIRun(() -> {
                lights_labelMouseReleased(null);
            });
        }
    }

    public JLabel getLast_hand_label() {
        return last_hand_label;
    }

    public JProgressBar getBarra_tiempo() {
        return barra_tiempo;
    }

    public JLabel getBet_label() {
        return bet_label;
    }

    public JLabel getBlinds_label() {
        return blinds_label;
    }

    public Card getFlop1() {
        return flop1;
    }

    public Card getFlop2() {
        return flop2;
    }

    public Card getFlop3() {
        return flop3;
    }

    public JLabel getHand_label() {
        return hand_label;
    }

    public JLabel getPot_label() {
        return pot_label;
    }

    public Card getRiver() {
        return river;
    }

    public JLabel getSound_icon() {
        return sound_icon;
    }

    public JLabel getTiempo_partida() {
        return tiempo_partida;
    }

    public Card getTurn() {
        return turn;
    }

    public Card[] getCartasComunes() {

        return new Card[]{flop1, flop2, flop3, turn, river};
    }

    public JButton getRandom_button() {
        return random_button;
    }

    public Color getColor_contadores() {
        return color_contadores;
    }

    public void cambiarColorContadores(Color color) {

        this.color_contadores = color;

        Helpers.GUIRun(() -> {
            if (!pot_label.isOpaque()) {
                pot_label.setForeground(color);
            }

            bet_label.setForeground(color);

            if (!blinds_label.isOpaque()) {
                blinds_label.setForeground(color);
            }

            tiempo_partida.setForeground(color);

            if (!hand_label.isOpaque()) {
                hand_label.setForeground(color);
            }
        });
    }

    public JLabel getLights_label() {
        return lights_label;
    }

    /**
     * Creates new form CommunityCards
     */
    public CommunityCardsPanel() {
        Helpers.GUIRunAndWait(() -> {
            initComponents();
            last_hand_label.setVisible(false);
            random_button.setVisible(false);
            hand_limit_spinner.setVisible(false);
            hand_limit_spinner.addChangeListener(e -> {

                Component mySpinnerEditor = hand_limit_spinner.getEditor();
                JFormattedTextField jftf = ((JSpinner.DefaultEditor) mySpinnerEditor).getTextField();
                jftf.setColumns(String.valueOf((int) hand_limit_spinner.getValue()).length());
                revalidate();
                repaint();

            });
            max_hands_button.setVisible(false);
            icon_zoom_timer = new Timer(GameFrame.GUI_ZOOM_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                flop1.updateImagePreloadCache();
                flop2.updateImagePreloadCache();
                flop3.updateImagePreloadCache();
                turn.updateImagePreloadCache();
                river.updateImagePreloadCache();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);
            setVisible(false);
        });

        Helpers.threadRun(() -> {
            while (pot_label.getHeight() == 0) {
                Helpers.pausar(125);
            }
            Helpers.GUIRun(() -> {
                sound_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
                Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), blinds_label.getHeight(), blinds_label.getHeight());
                panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) blinds_label.getHeight() * 0.7f)));
                Helpers.setScaledIconButton(pause_button, getClass().getResource("/images/pause.png"), Math.round(0.6f * pause_button.getHeight()), Math.round(0.6f * pause_button.getHeight()));
                Helpers.setScaledIconLabel(pot_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
                Helpers.setScaledIconLabel(bet_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
                Helpers.setScaledIconLabel(blinds_label, getClass().getResource("/images/ciegas_big.png"), Math.round(0.8f * pot_label.getHeight() * (342f / 256)), Math.round(0.8f * pot_label.getHeight()));
                Helpers.setScaledIconLabel(lights_label, getClass().getResource(GameFrame.getInstance().getCapa_brillo().getBrightness() == 0f ? "/images/lights_on.png" : "/images/lights_off.png"), Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()));

                ready = true;
            });
        });

    }

    public void refreshLightsIcon() {
        Helpers.GUIRun(() -> {
            Helpers.setScaledIconLabel(lights_label, getClass().getResource(GameFrame.getInstance().getCapa_brillo().getBrightness() == 0f ? "/images/lights_on.png" : "/images/lights_off.png"), Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()));
        });
    }

    public void last_hand_on() {
        GameFrame.getInstance().getCrupier().setLast_hand(true);

        var tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        Helpers.GUIRun(() -> {
            tthis.getHand_label().setOpaque(true);
            tthis.getHand_label().setBackground(Color.YELLOW);
            tthis.getHand_label().setForeground(Color.BLACK);
            tthis.getHand_label().setToolTipText(Translator.translate("ÚLTIMA MANO"));
            tthis.getLast_hand_label().setVisible(true);
            GameFrame.getInstance().getLast_hand_menu().setSelected(true);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(true);
        });

        Audio.playWavResource("misc/last_hand_on.wav");

    }

    public void last_hand_off() {

        GameFrame.getInstance().getCrupier().setLast_hand(false);

        var tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        Helpers.GUIRun(() -> {
            tthis.getHand_label().setOpaque(false);
            tthis.getHand_label().setForeground(color_contadores);
            tthis.getHand_label().setToolTipText(null);
            tthis.getLast_hand_label().setVisible(false);

            if (GameFrame.MANOS != -1 && GameFrame.getInstance().getCrupier().getMano() > GameFrame.MANOS) {
                tthis.getHand_label().setBackground(Color.red);
                tthis.getHand_label().setForeground(Color.WHITE);
                tthis.getHand_label().setOpaque(true);
            }
            GameFrame.getInstance().getLast_hand_menu().setSelected(false);
            Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(false);
        });

        Audio.playWavResource("misc/last_hand_off.wav");

    }

    public void hand_label_left_click() {
        Helpers.GUIRun(() -> {
            hand_label_click_type = 1;
            hand_labelMouseClicked(null);
        });
    }

    public void hand_label_right_click() {
        Helpers.GUIRun(() -> {
            hand_label_click_type = 2;
            hand_labelMouseClicked(null);
        });
    }

    public JSpinner getHand_limit_spinner() {
        return hand_limit_spinner;
    }

    public JButton getMax_hands_button() {
        return max_hands_button;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pot_label = new javax.swing.JLabel();
        bet_label = new javax.swing.JLabel();
        blinds_label = new javax.swing.JLabel();
        hand_label = new javax.swing.JLabel();
        tiempo_partida = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        panel_barra = new javax.swing.JPanel();
        barra_tiempo = new javax.swing.JProgressBar();
        cards_panel = new javax.swing.JPanel();
        flop3 = new com.tonikelope.coronapoker.Card();
        river = new com.tonikelope.coronapoker.Card();
        flop2 = new com.tonikelope.coronapoker.Card();
        turn = new com.tonikelope.coronapoker.Card();
        flop1 = new com.tonikelope.coronapoker.Card();
        pause_button = new javax.swing.JButton();
        last_hand_label = new javax.swing.JLabel();
        random_button = new javax.swing.JButton();
        hand_limit_spinner = new javax.swing.JSpinner();
        max_hands_button = new javax.swing.JButton();
        lights_label = new javax.swing.JLabel();

        setFocusable(false);
        setOpaque(false);

        pot_label.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        pot_label.setForeground(new java.awt.Color(153, 204, 0));
        pot_label.setText(" ");
        pot_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 5, 1, 5));
        pot_label.setDoubleBuffered(true);
        pot_label.setFocusable(false);

        bet_label.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        bet_label.setForeground(new java.awt.Color(153, 204, 0));
        bet_label.setText(" ");
        bet_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        bet_label.setDoubleBuffered(true);
        bet_label.setFocusable(false);

        blinds_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        blinds_label.setForeground(new java.awt.Color(153, 204, 0));
        blinds_label.setText(" ");
        blinds_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        blinds_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        blinds_label.setDoubleBuffered(true);
        blinds_label.setFocusable(false);
        blinds_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                blinds_labelMouseClicked(evt);
            }
        });

        hand_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        hand_label.setForeground(new java.awt.Color(153, 204, 0));
        hand_label.setText(" ");
        hand_label.setToolTipText("CLICK IZQ: ÚLTIMA MANO / CLICK DCHO: LÍMITE DE MANOS");
        hand_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        hand_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_label.setDoubleBuffered(true);
        hand_label.setFocusable(false);
        hand_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                hand_labelMouseClicked(evt);
            }
        });

        tiempo_partida.setFont(new java.awt.Font("Monospaced", 1, 26)); // NOI18N
        tiempo_partida.setForeground(new java.awt.Color(153, 204, 0));
        tiempo_partida.setText("00:00:00");
        tiempo_partida.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        tiempo_partida.setDoubleBuffered(true);
        tiempo_partida.setFocusable(false);

        sound_icon.setToolTipText("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setDoubleBuffered(true);
        sound_icon.setFocusable(false);
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        panel_barra.setFocusable(false);
        panel_barra.setOpaque(false);

        barra_tiempo.setDoubleBuffered(true);
        barra_tiempo.setFocusable(false);
        barra_tiempo.setMinimumSize(new java.awt.Dimension(1, 1));
        barra_tiempo.setPreferredSize(new Dimension(-1, (int)Math.round((float)pot_label.getHeight()*0.65)));

        javax.swing.GroupLayout panel_barraLayout = new javax.swing.GroupLayout(panel_barra);
        panel_barra.setLayout(panel_barraLayout);
        panel_barraLayout.setHorizontalGroup(
            panel_barraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(barra_tiempo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        panel_barraLayout.setVerticalGroup(
            panel_barraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(barra_tiempo, javax.swing.GroupLayout.DEFAULT_SIZE, 30, Short.MAX_VALUE)
        );

        cards_panel.setFocusable(false);
        cards_panel.setOpaque(false);

        javax.swing.GroupLayout cards_panelLayout = new javax.swing.GroupLayout(cards_panel);
        cards_panel.setLayout(cards_panelLayout);
        cards_panelLayout.setHorizontalGroup(
            cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cards_panelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(flop1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        cards_panelLayout.setVerticalGroup(
            cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cards_panelLayout.createSequentialGroup()
                .addGroup(cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(flop1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0))
        );

        pause_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        pause_button.setText("PAUSAR");
        pause_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pause_button.setDoubleBuffered(true);
        pause_button.setFocusable(false);
        pause_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pause_buttonActionPerformed(evt);
            }
        });

        last_hand_label.setBackground(new java.awt.Color(255, 255, 0));
        last_hand_label.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        last_hand_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        last_hand_label.setText("ÚLTIMA MANO");
        last_hand_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        last_hand_label.setDoubleBuffered(true);
        last_hand_label.setFocusable(false);
        last_hand_label.setOpaque(true);
        last_hand_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                last_hand_labelMouseClicked(evt);
            }
        });

        random_button.setBackground(new java.awt.Color(255, 0, 0));
        random_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        random_button.setForeground(new java.awt.Color(255, 255, 255));
        random_button.setText("RANDOM.ORG");
        random_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        random_button.setDoubleBuffered(true);
        random_button.setFocusable(false);
        random_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                random_buttonActionPerformed(evt);
            }
        });

        hand_limit_spinner.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        hand_limit_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        hand_limit_spinner.setDoubleBuffered(true);
        hand_limit_spinner.setFocusable(false);

        max_hands_button.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        max_hands_button.setText("OK");
        max_hands_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        max_hands_button.setDoubleBuffered(true);
        max_hands_button.setFocusable(false);
        max_hands_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                max_hands_buttonActionPerformed(evt);
            }
        });

        lights_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        lights_label.setText(" ");
        lights_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lights_label.setDoubleBuffered(true);
        lights_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                lights_labelMouseReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pot_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(bet_label))
            .addGroup(layout.createSequentialGroup()
                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(blinds_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pause_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(random_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lights_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tiempo_partida)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hand_limit_spinner)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(max_hands_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hand_label))
            .addComponent(panel_barra, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(last_hand_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pot_label)
                    .addComponent(bet_label))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(last_hand_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(hand_label)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(blinds_label)
                                .addComponent(pause_button)
                                .addComponent(random_button)
                                .addComponent(hand_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(tiempo_partida)
                                .addComponent(max_hands_button)
                                .addComponent(lights_label)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(panel_barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.getInstance().getSonidos_menu().doClick();
    }//GEN-LAST:event_sound_iconMouseClicked

    private void hand_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_hand_labelMouseClicked
        // TODO add your handling code here:

        var tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        if (GameFrame.getInstance().isPartida_local() && tthis.getHand_label().isEnabled()) {

            if ((evt == null && hand_label_click_type == 1) || (evt != null && SwingUtilities.isLeftMouseButton(evt))) {

                tthis.getHand_label().setEnabled(false);

                if (GameFrame.MANOS == GameFrame.getInstance().getCrupier().getMano() || GameFrame.getInstance().getCrupier().isLast_hand() || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿ÚLTIMA MANO?") == 0) {

                    Helpers.threadRun(() -> {
                        if (!GameFrame.getInstance().getCrupier().isLast_hand()) {
                            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#1", null);
                            last_hand_on();

                        } else {
                            GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#0", null);
                            last_hand_off();
                        }
                        Helpers.GUIRun(() -> {
                            tthis.getHand_label().setEnabled(true);
                        });
                    });

                } else {
                    tthis.getHand_label().setEnabled(true);
                    GameFrame.getInstance().getLast_hand_menu().setSelected(false);
                    Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(false);
                }

            } else if ((evt == null && hand_label_click_type == 2) || (evt != null && SwingUtilities.isRightMouseButton(evt))) {

                click_max_hands();

            }

        }
    }//GEN-LAST:event_hand_labelMouseClicked

    private void click_max_hands() {
        var tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        if (tthis.getHand_limit_spinner().isVisible()) {

            GameFrame.getInstance().getMax_hands_menu().setOpaque(false);
            GameFrame.getInstance().getMax_hands_menu().setBackground(null);
            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setOpaque(false);
            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setBackground(null);

            tthis.getHand_limit_spinner().setVisible(false);

            max_hands_button.setVisible(false);

            tiempo_partida.setVisible(GameFrame.SHOW_CLOCK);

            tthis.getHand_label().setEnabled(false);

            int manos = (int) (GameFrame.getInstance().getTapete().getCommunityCards().getHand_limit_spinner().getValue());

            int old_manos = GameFrame.MANOS;

            if (manos == 0) {
                GameFrame.MANOS = -1;

            } else if (GameFrame.getInstance().getCrupier().getMano() < manos) {

                GameFrame.MANOS = manos;
            }

            GameFrame.getInstance().getTapete().getCommunityCards().getHand_limit_spinner().setVisible(false);

            if (GameFrame.MANOS != old_manos) {

                Helpers.threadRun(() -> {
                    GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);
                    GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
                    Helpers.GUIRun(() -> {
                        tthis.getHand_label().setEnabled(true);
                    });
                });

            } else {
                tthis.getHand_label().setEnabled(true);
            }

        } else {

            tiempo_partida.setVisible(false);

            tthis.getHand_limit_spinner().setModel(new SpinnerNumberModel(GameFrame.MANOS != -1 ? GameFrame.MANOS : 0, 0, null, 1));

            ((JSpinner.DefaultEditor) tthis.getHand_limit_spinner().getEditor()).getTextField().setEditable(false);

            tthis.getHand_limit_spinner().setVisible(true);

            tthis.getMax_hands_button().setVisible(true);

            GameFrame.getInstance().getMax_hands_menu().setBackground(Color.YELLOW);
            GameFrame.getInstance().getMax_hands_menu().setOpaque(true);

            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setBackground(Color.YELLOW);
            Helpers.TapetePopupMenu.MAX_HANDS_MENU.setOpaque(true);
        }
    }

    public JButton getPause_button() {
        return pause_button;
    }

    private void pause_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pause_buttonActionPerformed
        // TODO add your handling code here:

        var tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        int pause_now = -2;

        if (!GameFrame.getInstance().getCrupier().isIwtsthing() && !(GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) && (GameFrame.getInstance().isPartida_local()) && !GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().getLocalPlayer().isTurno() && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            pause_now = Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿PAUSAR AHORA MISMO?");

        } else if (GameFrame.getInstance().getCrupier().isIwtsthing()) {

            pause_now = 0;
        }

        if (!(!GameFrame.getInstance().isPartida_local() && (GameFrame.getInstance().getCrupier().isIwtsthing() || GameFrame.getInstance().getCrupier().isIwtsthing_request())) && pause_now < 1 && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && ((!GameFrame.getInstance().isPartida_local() && pause_now == 0) || (GameFrame.getInstance().getLocalPlayer().isTurno() && pause_now == -2) || (GameFrame.getInstance().isPartida_local() && ((GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) || GameFrame.getInstance().isTimba_pausada() || pause_now == 0 || GameFrame.getInstance().getLocalPlayer().isSpectator())))) {

            tthis.getPause_button().setBackground(null);
            tthis.getPause_button().setForeground(null);

            if (!GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().isPartida_local()) {

                GameFrame.getInstance().getLocalPlayer().setPause_counter(GameFrame.getInstance().getLocalPlayer().getPause_counter() - 1);
                Helpers.setScaledIconButton(tthis.getPause_button(), getClass().getResource("/images/pause.png"), Math.round(0.6f * tthis.getPause_button().getHeight()), Math.round(0.6f * tthis.getPause_button().getHeight()));
                tthis.getPause_button().setText(Translator.translate("PAUSAR") + " (" + GameFrame.getInstance().getLocalPlayer().getPause_counter() + ")");
            }

            tthis.getPause_button().setEnabled(false);

            Helpers.threadRun(() -> {
                GameFrame.getInstance().pauseTimba(GameFrame.getInstance().isPartida_local() ? null : GameFrame.getInstance().getLocalPlayer().getNickname());
            });

        } else if (!GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause()) {

                tthis.getPause_button().setBackground(Color.WHITE);
                tthis.getPause_button().setForeground(new Color(255, 102, 0));
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(true);
                Audio.playWavResource("misc/button_on.wav");

                if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause_warning()) {
                    GameFrame.getInstance().getLocalPlayer().setAuto_pause_warning(true);
                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "PAUSA PROGRAMADA PARA TU PRÓXIMO TURNO");
                }

            } else {
                tthis.getPause_button().setBackground(null);
                tthis.getPause_button().setForeground(null);
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                Audio.playWavResource("misc/button_off.wav");
            }
        }

    }//GEN-LAST:event_pause_buttonActionPerformed

    private void random_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_random_buttonActionPerformed
        // TODO add your handling code here:

        Helpers.DECK_RANDOM_GENERATOR = Integer.valueOf(Helpers.PROPERTIES.getProperty("random_generator"));

        GameFrame.getInstance().getTapete().getCommunityCards().getRandom_button().setVisible(false);

        Helpers.mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "SE HA REACTIVADO RANDOM.ORG");

    }//GEN-LAST:event_random_buttonActionPerformed

    private void last_hand_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_last_hand_labelMouseClicked
        // TODO add your handling code here:
        hand_labelMouseClicked(evt);
    }//GEN-LAST:event_last_hand_labelMouseClicked

    private void max_hands_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_max_hands_buttonActionPerformed
        // TODO add your handling code here:

        click_max_hands();

    }//GEN-LAST:event_max_hands_buttonActionPerformed

    private void lights_labelMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lights_labelMouseReleased
        // TODO add your handling code here:

        if (evt == null || new Rectangle(new Dimension(Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()))).contains(evt.getPoint())) {
            if (GameFrame.getInstance().getCapa_brillo().getBrightness() == 0f) {

                Audio.playWavResource("misc/button_off.wav");
                GameFrame.getInstance().getCapa_brillo().lightsOFF();
                Helpers.setScaledIconLabel(lights_label, getClass().getResource("/images/lights_off.png"), Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()));

            } else {

                Audio.playWavResource("misc/button_on.wav");
                GameFrame.getInstance().getCapa_brillo().lightsON();
                Helpers.setScaledIconLabel(lights_label, getClass().getResource("/images/lights_on.png"), Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()));

            }

            if (GameFrame.getInstance().getFastchat_dialog() != null) {
                GameFrame.getInstance().getFastchat_dialog().refreshColors();
            }

            if (GameFrame.getInstance().getNotify_dialog() != null) {
                GameFrame.getInstance().getNotify_dialog().repaint();
            }

            GameFrame.getInstance().getTapete().repaint();
        }

    }//GEN-LAST:event_lights_labelMouseReleased

    private void blinds_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_blinds_labelMouseClicked
        // TODO add your handling code here:

        if (GameFrame.getInstance().isPartida_local()) {
            EditBlindsDialog dialog = new EditBlindsDialog(GameFrame.getInstance().getFrame(), true);

            dialog.setLocationRelativeTo(GameFrame.getInstance().getFrame());

            dialog.setVisible(true);
        }

    }//GEN-LAST:event_blinds_labelMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar barra_tiempo;
    private javax.swing.JLabel bet_label;
    private javax.swing.JLabel blinds_label;
    private javax.swing.JPanel cards_panel;
    private com.tonikelope.coronapoker.Card flop1;
    private com.tonikelope.coronapoker.Card flop2;
    private com.tonikelope.coronapoker.Card flop3;
    private javax.swing.JLabel hand_label;
    private javax.swing.JSpinner hand_limit_spinner;
    private javax.swing.JLabel last_hand_label;
    private javax.swing.JLabel lights_label;
    private javax.swing.JButton max_hands_button;
    private javax.swing.JPanel panel_barra;
    private javax.swing.JButton pause_button;
    private javax.swing.JLabel pot_label;
    private javax.swing.JButton random_button;
    private com.tonikelope.coronapoker.Card river;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel tiempo_partida;
    private com.tonikelope.coronapoker.Card turn;
    // End of variables declaration//GEN-END:variables

    private void zoomIcons() {

        Helpers.threadRun(() -> {
            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    sound_icon.setPreferredSize(new Dimension(blinds_label.getHeight(), blinds_label.getHeight()));
                    Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png"), blinds_label.getHeight(), blinds_label.getHeight());
                    panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) blinds_label.getHeight() * 0.7f)));
                    sound_icon.setVisible(true);
                    panel_barra.setVisible(true);
                    Helpers.setScaledIconButton(pause_button, getClass().getResource("/images/pause.png"), Math.round(0.6f * pause_button.getHeight()), Math.round(0.6f * pause_button.getHeight()));
                    Helpers.setScaledIconLabel(pot_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
                    Helpers.setScaledIconLabel(bet_label, getClass().getResource("/images/pot.png"), pot_label.getHeight(), pot_label.getHeight());
                    Helpers.setScaledIconLabel(lights_label, getClass().getResource(GameFrame.getInstance().getCapa_brillo().getBrightness() == 0f ? "/images/lights_on.png" : "/images/lights_off.png"), Math.round(0.7f * pot_label.getHeight() * (512f / 240)), Math.round(0.7f * pot_label.getHeight()));
                    Helpers.setScaledIconLabel(blinds_label, getClass().getResource("/images/ciegas_big.png"), Math.round(0.8f * pot_label.getHeight() * (342f / 256)), Math.round(0.8f * pot_label.getHeight()));
                });
            }
        });

    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        while (!ready) {
            Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);
        }

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        ZoomableInterface[] zoomables = new ZoomableInterface[]{flop1, flop2, flop3, turn, river};

        for (ZoomableInterface zoomable : zoomables) {
            Helpers.threadRun(() -> {
                zoomable.zoom(zoom_factor, mynotifier);
            });
        }

        synchronized (zoom_lock) {

            Helpers.GUIRunAndWait(() -> {
                if (icon_zoom_timer.isRunning()) {
                    icon_zoom_timer.stop();
                }

                sound_icon.setVisible(false);
                panel_barra.setVisible(false);
                pause_button.setIcon(null);
                pot_label.setIcon(null);
                bet_label.setIcon(null);
                lights_label.setIcon(null);
                blinds_label.setIcon(null);
            });

            Helpers.zoomFonts(this, zoom_factor, null);

            Helpers.GUIRun(() -> {
                if (icon_zoom_timer.isRunning()) {
                    icon_zoom_timer.restart();
                } else {
                    icon_zoom_timer.start();
                }

                revalidate();
                repaint();
            });
        }

        while (mynotifier.size() < zoomables.length) {

            synchronized (mynotifier) {

                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(CommunityCardsPanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (notifier != null) {

            notifier.add(Thread.currentThread().getId());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }
}
