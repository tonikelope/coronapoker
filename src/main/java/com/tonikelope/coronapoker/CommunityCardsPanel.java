/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 *
 * @author tonikelope
 */
public class CommunityCardsPanel extends javax.swing.JPanel implements ZoomableInterface {

    public static final int SOUND_ICON_WIDTH = 30;

    private volatile Color color_contadores = null;
    private volatile int hand_label_click_type = 0;

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

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (!pot_label.isOpaque()) {
                    pot_label.setForeground(color);
                }

                bet_label.setForeground(color);
                blinds_label.setForeground(color);
                tiempo_partida.setForeground(color);

                if (!hand_label.isOpaque()) {
                    hand_label.setForeground(color);
                }

            }
        });
    }

    /**
     * Creates new form CommunityCards
     */
    public CommunityCardsPanel() {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();
                last_hand_label.setVisible(false);
                random_button.setVisible(false);
                hand_limit_spinner.setVisible(false);
                max_hands_button.setVisible(false);
            }
        });

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                while (pot_label.getHeight() == 0) {
                    Helpers.pausar(125);
                }
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        sound_icon.setPreferredSize(new Dimension(pot_label.getHeight(), pot_label.getHeight()));
                        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(pot_label.getHeight(), pot_label.getHeight(), Image.SCALE_SMOOTH)));
                        panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) pot_label.getHeight() * 0.65)));
                    }
                });
            }
        });

    }

    public void last_hand_on() {
        GameFrame.getInstance().getCrupier().setLast_hand(true);

        CommunityCardsPanel tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                tthis.getHand_label().setOpaque(true);
                tthis.getHand_label().setBackground(Color.YELLOW);
                tthis.getHand_label().setForeground(Color.BLACK);
                tthis.getHand_label().setToolTipText(Translator.translate("ÚLTIMA MANO"));
                tthis.getLast_hand_label().setVisible(true);
                GameFrame.getInstance().getLast_hand_menu().setSelected(true);
                Helpers.TapetePopupMenu.LAST_HAND_MENU.setSelected(true);
            }
        });

        Helpers.playWavResource("misc/last_hand_on.wav");

    }

    public void last_hand_off() {

        GameFrame.getInstance().getCrupier().setLast_hand(false);

        CommunityCardsPanel tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

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
            }
        });

        Helpers.playWavResource("misc/last_hand_off.wav");

    }

    public void hand_label_left_click() {
        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                hand_label_click_type = 1;
                hand_labelMouseClicked(null);
            }
        });
    }

    public void hand_label_right_click() {
        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                hand_label_click_type = 2;
                hand_labelMouseClicked(null);
            }
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

        setFocusable(false);
        setOpaque(false);

        pot_label.setFont(new java.awt.Font("Dialog", 1, 28)); // NOI18N
        pot_label.setForeground(new java.awt.Color(153, 204, 0));
        pot_label.setText("Bote:");
        pot_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 5, 1, 5));
        pot_label.setDoubleBuffered(true);
        pot_label.setFocusable(false);

        bet_label.setFont(new java.awt.Font("Dialog", 1, 28)); // NOI18N
        bet_label.setForeground(new java.awt.Color(153, 204, 0));
        bet_label.setText("---------");
        bet_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        bet_label.setDoubleBuffered(true);
        bet_label.setFocusable(false);

        blinds_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        blinds_label.setForeground(new java.awt.Color(153, 204, 0));
        blinds_label.setText("Ciegas:");
        blinds_label.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        blinds_label.setDoubleBuffered(true);
        blinds_label.setFocusable(false);

        hand_label.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        hand_label.setForeground(new java.awt.Color(153, 204, 0));
        hand_label.setText("Mano:");
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

        tiempo_partida.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        tiempo_partida.setForeground(new java.awt.Color(153, 204, 0));
        tiempo_partida.setText("00:00:00");
        tiempo_partida.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        tiempo_partida.setDoubleBuffered(true);
        tiempo_partida.setFocusable(false);

        sound_icon.setToolTipText("Click para activar/desactivar el sonido");
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
            .addComponent(barra_tiempo, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE)
        );

        cards_panel.setFocusable(false);
        cards_panel.setOpaque(false);

        flop3.setFocusable(false);

        river.setFocusable(false);

        flop2.setFocusable(false);

        turn.setFocusable(false);

        flop1.setFocusable(false);

        javax.swing.GroupLayout cards_panelLayout = new javax.swing.GroupLayout(cards_panel);
        cards_panel.setLayout(cards_panelLayout);
        cards_panelLayout.setHorizontalGroup(
            cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cards_panelLayout.createSequentialGroup()
                .addComponent(flop1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        cards_panelLayout.setVerticalGroup(
            cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(cards_panelLayout.createSequentialGroup()
                .addGroup(cards_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(flop2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(flop3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(turn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(river, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(flop1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0))
        );

        pause_button.setBackground(new java.awt.Color(255, 102, 0));
        pause_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        pause_button.setForeground(new java.awt.Color(255, 255, 255));
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pot_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bet_label))
            .addGroup(layout.createSequentialGroup()
                .addComponent(blinds_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pause_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(random_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tiempo_partida)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hand_limit_spinner)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(max_hands_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(hand_label))
            .addComponent(panel_barra, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(last_hand_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(pot_label)
                        .addComponent(bet_label)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(last_hand_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cards_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(hand_label)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(blinds_label)
                        .addComponent(pause_button)
                        .addComponent(random_button)
                        .addComponent(hand_limit_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(tiempo_partida)
                        .addComponent(max_hands_button)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panel_barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.getInstance().getSonidos_menu().doClick();
    }//GEN-LAST:event_sound_iconMouseClicked

    private void hand_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_hand_labelMouseClicked
        // TODO add your handling code here:

        CommunityCardsPanel tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        if (GameFrame.getInstance().isPartida_local() && tthis.getHand_label().isEnabled()) {

            if ((evt == null && hand_label_click_type == 1) || (evt != null && SwingUtilities.isLeftMouseButton(evt))) {

                tthis.getHand_label().setEnabled(false);

                if (GameFrame.MANOS == GameFrame.getInstance().getCrupier().getMano() || GameFrame.getInstance().getCrupier().isLast_hand() || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿ÚLTIMA MANO?") == 0) {

                    Helpers.threadRun(new Runnable() {

                        public void run() {

                            if (!GameFrame.getInstance().getCrupier().isLast_hand()) {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#1", null);
                                last_hand_on();

                            } else {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("LASTHAND#0", null);
                                last_hand_off();
                            }

                            Helpers.GUIRun(new Runnable() {

                                public void run() {
                                    tthis.getHand_label().setEnabled(true);
                                }
                            });
                        }
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
        CommunityCardsPanel tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        if (tthis.getHand_limit_spinner().isVisible()) {

            tthis.getHand_limit_spinner().setVisible(false);

            max_hands_button.setVisible(false);

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

                Helpers.threadRun(new Runnable() {

                    public void run() {

                        GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("MAXHANDS#" + String.valueOf(GameFrame.MANOS), null);

                        GameFrame.getInstance().getCrupier().actualizarContadoresTapete();

                        Helpers.GUIRun(new Runnable() {

                            public void run() {
                                tthis.getHand_label().setEnabled(true);
                            }
                        });

                    }
                });

            } else {
                tthis.getHand_label().setEnabled(true);
            }

        } else {
            tthis.getHand_limit_spinner().setModel(new SpinnerNumberModel(GameFrame.MANOS != -1 ? GameFrame.MANOS : 0, 0, null, 1));

            ((JSpinner.DefaultEditor) tthis.getHand_limit_spinner().getEditor()).getTextField().setEditable(false);

            tthis.getHand_limit_spinner().setVisible(true);

            tthis.getMax_hands_button().setVisible(true);
        }
    }

    public JButton getPause_button() {
        return pause_button;
    }

    private void pause_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pause_buttonActionPerformed
        // TODO add your handling code here:

        CommunityCardsPanel tthis = GameFrame.getInstance().getTapete().getCommunityCards();

        int pause_now = -2;

        if (!(GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) && GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().getLocalPlayer().isTurno() && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && !GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            pause_now = Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿PAUSAR AHORA MISMO?");

        }

        if (pause_now < 1 && !GameFrame.getInstance().getLocalPlayer().isAuto_pause() && ((GameFrame.getInstance().getLocalPlayer().isTurno() && pause_now == -2) || (GameFrame.getInstance().isPartida_local() && ((GameFrame.getInstance().getCrupier().isLast_hand() && GameFrame.getInstance().getCrupier().isShow_time()) || GameFrame.getInstance().isTimba_pausada() || pause_now == 0 || GameFrame.getInstance().getLocalPlayer().isSpectator())))) {

            tthis.getPause_button().setBackground(new Color(255, 102, 0));
            tthis.getPause_button().setForeground(Color.WHITE);

            if (!GameFrame.getInstance().isTimba_pausada() && !GameFrame.getInstance().isPartida_local()) {
                GameFrame.getInstance().getLocalPlayer().setPause_counter(GameFrame.getInstance().getLocalPlayer().getPause_counter() - 1);
                tthis.getPause_button().setText(Translator.translate("PAUSAR") + " (" + GameFrame.getInstance().getLocalPlayer().getPause_counter() + ")");
            }

            tthis.getPause_button().setEnabled(false);

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    GameFrame.getInstance().pauseTimba(GameFrame.getInstance().isPartida_local() ? null : GameFrame.getInstance().getLocalPlayer().getNickname());

                }
            });

        } else if (!GameFrame.getInstance().getLocalPlayer().isSpectator()) {

            if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause()) {

                tthis.getPause_button().setBackground(Color.WHITE);
                tthis.getPause_button().setForeground(new Color(255, 102, 0));
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(true);
                Helpers.playWavResource("misc/button_on.wav");

                if (!GameFrame.getInstance().getLocalPlayer().isAuto_pause_warning()) {
                    GameFrame.getInstance().getLocalPlayer().setAuto_pause_warning(true);
                    Helpers.mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "PAUSA PROGRAMADA PARA TU PRÓXIMO TURNO");
                }

            } else {
                tthis.getPause_button().setBackground(new Color(255, 102, 0));
                tthis.getPause_button().setForeground(Color.WHITE);
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                Helpers.playWavResource("misc/button_off.wav");
            }
        }

    }//GEN-LAST:event_pause_buttonActionPerformed

    private void random_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_random_buttonActionPerformed
        // TODO add your handling code here:

        Helpers.DECK_RANDOM_GENERATOR = Helpers.TRNG;

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

    @Override
    public void zoom(float factor, final ConcurrentLinkedQueue<String> notifier) {

        final ConcurrentLinkedQueue<String> mynotifier = new ConcurrentLinkedQueue<>();

        for (ZoomableInterface zoomeable : new ZoomableInterface[]{flop1, flop2, flop3, turn, river}) {
            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    zoomeable.zoom(factor, mynotifier);

                }
            });
        }

        while (mynotifier.size() < 5) {

            synchronized (mynotifier) {

                try {
                    mynotifier.wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        int altura_sound = pot_label.getHeight();

        Helpers.zoomFonts(this, factor);

        while (altura_sound == pot_label.getHeight()) {
            try {
                Thread.sleep(GameFrame.GUI_ZOOM_WAIT);
            } catch (InterruptedException ex) {
                Logger.getLogger(CommunityCardsPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                sound_icon.setPreferredSize(new Dimension(pot_label.getHeight(), pot_label.getHeight()));
                sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound.png" : "/images/mute.png")).getImage().getScaledInstance(pot_label.getHeight(), pot_label.getHeight(), Image.SCALE_SMOOTH)));
                panel_barra.setPreferredSize(new Dimension(-1, (int) Math.round((float) pot_label.getHeight() * 0.65)));
            }
        });

        if (notifier != null) {

            notifier.add(Thread.currentThread().getName());

            synchronized (notifier) {

                notifier.notifyAll();

            }
        }
    }
}
