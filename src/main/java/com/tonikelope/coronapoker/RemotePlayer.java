/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

/**
 *
 * @author tonikelope
 */
public class RemotePlayer extends JPanel implements ZoomableInterface, Player {

    public static final String[][] ACTIONS_LABELS_ES = new String[][]{new String[]{"NO VA"}, new String[]{"PASA", "VA"}, new String[]{"APUESTA", "SUBE"}, new String[]{"ALL IN"}};
    public static final String[][] ACTIONS_LABELS_EN = new String[][]{new String[]{"FOLD"}, new String[]{"CHECK", "CALL"}, new String[]{"BET", "RAISE"}, new String[]{"ALL IN"}};
    public static String[][] ACTIONS_LABELS = Game.LANGUAGE.equals("es") ? ACTIONS_LABELS_ES : ACTIONS_LABELS_EN;
    public static final String[] POSITIONS_LABELS_ES = new String[]{"CP", "CG", "DE"};
    public static final String[] POSITIONS_LABELS_EN = new String[]{"SB", "BB", "DE"};
    public static String[] POSITIONS_LABELS = Game.LANGUAGE.equals("es") ? POSITIONS_LABELS_ES : POSITIONS_LABELS_EN;
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.ORANGE, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 300;
    public static final int MIN_ACTION_HEIGHT = 45;

    private String nickname;
    private float stack;
    private int buyin = Game.BUYIN;
    private Crupier crupier = null;
    private float bet;
    private volatile int decision = -1;
    private boolean utg;
    private volatile boolean spectator = false;
    private float pagar = 0f;
    private float bote = 0f;
    private volatile boolean exit = false;
    private volatile Timer auto_action = null;
    private boolean timeout_val = false;
    private boolean winner = false;
    private boolean loser = false;
    private volatile int pos;
    private float call_required;
    private volatile boolean disabled;
    private volatile boolean turno = false;
    private volatile Bot bot = null;

    public int getPos() {
        return pos;
    }

    public Bot getBot() {
        return bot;
    }

    public boolean isTurno() {
        return turno;
    }

    public void refreshPos() {

        this.bote = 0f;

        if (Helpers.float1DSecureCompare(0f, this.bet) < 0) {
            this.setStack(this.stack + this.bet);
        }

        this.bet = 0f;

        if (pos == crupier.getDealer_pos()) {
            this.setPosition(DEALER);
        } else if (pos == crupier.getBig_pos()) {
            this.setPosition(BIG_BLIND);
        } else if (pos == crupier.getSmall_pos()) {
            this.setPosition(SMALL_BLIND);
        }

        if (pos == crupier.getUtg_pos()) {
            this.setUTG();
        } else {
            this.disableUTG();
        }
    }

    public boolean isWinner() {
        return winner;
    }

    public boolean isLoser() {
        return loser;
    }

    public JLabel getAvatar() {
        return avatar;
    }

    public int getBuyin() {
        return buyin;
    }

    public synchronized boolean isExit() {
        return exit;
    }

    public synchronized void setExit() {

        if (!this.exit) {
            this.exit = true;

            if (crupier.getJugadoresActivos() + crupier.getTotalCalentando() < 2) {
                crupier.setJugadores_suficientes(false);
            }

            if (auto_action != null) {
                auto_action.stop();
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                    playingCard1.descargarCarta();
                    playingCard2.descargarCarta();

                    player_action.setBackground(new Color(255, 102, 0));
                    player_action.setForeground(Color.WHITE);
                    player_action.setText(Translator.translate("ABANDONA LA TIMBA"));
                    player_action.setVisible(true);
                    player_action.setEnabled(true);
                }
            });

        }

    }

    public float getPagar() {
        return pagar;
    }

    public float getBote() {
        return bote;
    }

    public void setStack(float stack) {
        this.stack = Helpers.clean1DFloat(stack);

        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_stack.setText(Helpers.float2String(stack));

            }
        });
    }

    public void setBet(float new_bet) {

        float old_bet = this.bet;

        this.bet = Helpers.clean1DFloat(new_bet);

        if (Helpers.float1DSecureCompare(old_bet, this.bet) < 0) {
            this.bote += Helpers.clean1DFloat(this.bet - old_bet);
        }

        crupier.getBote().addPlayer(this);

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (Helpers.float1DSecureCompare(0f, bote) < 0) {

                    player_pot.setText(Helpers.float2String(bote));

                } else {
                    player_pot.setBackground(Color.WHITE);
                    player_pot.setForeground(Color.BLACK);
                    player_pot.setText("----");

                }

            }
        });

    }

    public void esTuTurno() {
        turno = true;

        if (this.getDecision() == Player.NODEC) {

            call_required = crupier.getApuesta_actual() - bet;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    setBorder(javax.swing.BorderFactory.createLineBorder(Color.ORANGE, Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                    player_pot.setBackground(Color.WHITE);

                    player_pot.setForeground(Color.BLACK);

                    player_action.setBackground(Color.WHITE);

                    player_action.setForeground(Color.BLACK);

                    player_action.setText(Translator.translate("PENSANDO"));

                    player_action.setBackground(null);

                    player_action.setEnabled(false);

                    Game.getInstance().getBarra_tiempo().setMaximum(Game.TIEMPO_PENSAR);

                    Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
                }
            });

            if (!Game.TEST_MODE) {

                //Tiempo máximo para pensar
                Helpers.threadRun(new Runnable() {
                    public void run() {

                        ActionListener listener = new ActionListener() {

                            int counter = Game.TIEMPO_PENSAR;

                            long t = crupier.getTurno();

                            public void actionPerformed(ActionEvent ae) {

                                if (!crupier.isFin_de_la_transmision() && !Game.getInstance().isTimba_pausada() && !WaitingRoom.isExit() && counter > 0 && t == crupier.getTurno() && auto_action.isRunning() && getDecision() == Player.NODEC) {

                                    counter--;

                                    Game.getInstance().getBarra_tiempo().setValue(counter);

                                    if (counter == 10) {
                                        Helpers.playWavResource("misc/hurryup.wav");
                                    }

                                    if (counter == 0) {

                                        Helpers.threadRun(new Runnable() {
                                            public void run() {
                                                Helpers.playWavResourceAndWait("misc/timeout.wav");

                                                if (auto_action.isRunning() && t == crupier.getTurno()) {

                                                    Game.getInstance().checkPause();

                                                    if (auto_action.isRunning() && t == crupier.getTurno()) {

                                                        auto_action.stop();
                                                    }
                                                }
                                            }
                                        });

                                    }

                                }
                            }
                        };

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        auto_action = new Timer(1000, listener);

                        auto_action.start();

                    }
                });
            }
        } else {

            finTurno();
        }

    }

    public void setDecisionFromRemotePlayer(int decision, float bet) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                Game.getInstance().getBarra_tiempo().setValue(Game.TIEMPO_PENSAR);
            }
        });

        if (auto_action != null) {
            auto_action.stop();
        }

        this.decision = decision;

        switch (this.decision) {
            case Player.CHECK:
                check();
                break;
            case Player.FOLD:
                fold();
                break;
            case Player.BET:
                bet(bet);
                break;
            case Player.ALLIN:
                allin();
                break;
            default:
                break;
        }

    }

    public void setDecision(int dec) {

        this.decision = dec;

        switch (dec) {
            case Player.CHECK:

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                            player_action.setText(ACTIONS_LABELS[dec - 1][1]);
                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                        }
                    }
                });

                break;
            case Player.BET:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        if (Helpers.float1DSecureCompare(crupier.getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, crupier.getApuesta_actual()) < 0) {
                            player_action.setText((crupier.getConta_raise() > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.float2String(bet - crupier.getApuesta_actual()) + ")");
                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.float2String(bet));
                        }

                    }
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        setBorder(javax.swing.BorderFactory.createLineBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                        player_action.setText(ACTIONS_LABELS[dec - 1][0] + " (" + Helpers.float2String(bet + getStack()) + ")");
                    }
                });
                break;
            default:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        setBorder(javax.swing.BorderFactory.createLineBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                        player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                    }
                });
                break;
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                player_action.setBackground(ACTIONS_COLORS[dec - 1][0]);

                player_pot.setBackground(ACTIONS_COLORS[dec - 1][0]);

                player_action.setForeground(ACTIONS_COLORS[dec - 1][1]);

                player_pot.setForeground(ACTIONS_COLORS[dec - 1][1]);

                player_action.setEnabled(true);
            }
        });
    }

    public void finTurno() {

        Helpers.stopWavResource("misc/hurryup.wav");

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (decision != Player.ALLIN && decision != Player.FOLD) {
                    setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));
                }

                turno = false;

                synchronized (Game.getInstance().getCrupier().getLock_apuestas()) {
                    Game.getInstance().getCrupier().getLock_apuestas().notifyAll();
                }
            }
        });
    }

    private void fold() {

        Helpers.playWavResource("misc/fold.wav");

        setDecision(Player.FOLD);

        playingCard1.desenfocar();
        playingCard2.desenfocar();

        finTurno();
    }

    private void check() {

        Helpers.playWavResource("misc/check.wav");

        setStack(this.stack - (crupier.getApuesta_actual() - this.bet));

        setBet(crupier.getApuesta_actual());

        setDecision(Player.CHECK);

        finTurno();

    }

    private void bet(float new_bet) {

        Helpers.playWavResource("misc/bet.wav");

        setStack(this.stack - (new_bet - bet));

        setBet(new_bet);

        setDecision(Player.BET);

        if (Game.SONIDOS_CHORRA && crupier.getConta_raise() > 0 && Helpers.float1DSecureCompare(crupier.getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, crupier.getApuesta_actual()) < 0) {
            Helpers.playWavResource("misc/raise.wav");
        }

        finTurno();

    }

    private void allin() {

        Helpers.playWavResource("misc/allin.wav");

        crupier.setPlaying_cinematic(true);

        Helpers.threadRun(new Runnable() {

            public void run() {

                if (!crupier.remoteCinematicAllin()) {
                    crupier.soundAllin();
                }
            }
        });

        setBet(this.stack + this.bet);

        setStack(0f);

        setDecision(Player.ALLIN);

        finTurno();

    }

    public int getDecision() {
        return decision;
    }

    public float getBet() {
        return bet;
    }

    public void setTimeout(boolean val) {

        if (this.timeout_val != val) {

            this.timeout_val = val;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    timeout.setVisible(val);

                }
            });

            if (val) {
                Helpers.playWavResource("misc/network_error.wav");
            }
        }

    }

    /**
     * Creates new form JugadorInvitadoView
     */
    public RemotePlayer() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                decision = Player.NODEC;

                bet = 0;

                timeout.setVisible(false);

                player_pot.setText("----");

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                utg_textfield.setVisible(false);

                player_blind.setVisible(false);

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);

                player_buyin.setText(String.valueOf(Game.BUYIN));
            }
        });

    }

    public Card getPlayingCard1() {
        return playingCard1;
    }

    public Card getPlayingCard2() {
        return playingCard2;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_name.setText(nickname);

                if (Game.getInstance().isPartida_local() && !Game.getInstance().getParticipantes().get(nickname).isCpu()) {
                    player_name.setToolTipText("CLICK -> AES-KEY");
                    player_name.setCursor(new Cursor(Cursor.HAND_CURSOR));
                }

            }
        });

        if (Game.getInstance().isPartida_local() && Game.getInstance().getParticipantes().get(this.nickname).isCpu()) {
            this.bot = new Bot(this);
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

        panel_cartas = new javax.swing.JPanel();
        playingCard1 = new com.tonikelope.coronapoker.Card();
        playingCard2 = new com.tonikelope.coronapoker.Card();
        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        timeout = new javax.swing.JLabel();
        player_pot = new javax.swing.JLabel();
        player_buyin = new javax.swing.JLabel();
        player_stack = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_textfield = new javax.swing.JLabel();
        player_blind = new javax.swing.JLabel();
        player_action = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.Game.ZOOM_LEVEL*com.tonikelope.coronapoker.Game.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        panel_cartas.setFocusable(false);
        panel_cartas.setOpaque(false);

        playingCard1.setFocusable(false);

        playingCard2.setFocusable(false);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addComponent(playingCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(playingCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(playingCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(playingCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        indicadores_arriba.setFocusable(false);
        indicadores_arriba.setOpaque(false);

        avatar_panel.setFocusable(false);
        avatar_panel.setOpaque(false);

        avatar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_null.png"))); // NOI18N
        avatar.setDoubleBuffered(true);
        avatar.setFocusable(false);

        timeout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/timeout.png"))); // NOI18N
        timeout.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        timeout.setDoubleBuffered(true);
        timeout.setFocusable(false);
        timeout.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                timeoutMouseClicked(evt);
            }
        });

        player_pot.setBackground(new java.awt.Color(255, 255, 255));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 28)); // NOI18N
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        player_pot.setDoubleBuffered(true);
        player_pot.setFocusable(false);
        player_pot.setOpaque(true);

        player_buyin.setBackground(new java.awt.Color(204, 204, 204));
        player_buyin.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        player_buyin.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_buyin.setText("10");
        player_buyin.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_buyin.setDoubleBuffered(true);
        player_buyin.setFocusable(false);
        player_buyin.setOpaque(true);

        player_stack.setBackground(new java.awt.Color(51, 153, 0));
        player_stack.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        player_stack.setForeground(new java.awt.Color(255, 255, 255));
        player_stack.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_stack.setText("10000.0");
        player_stack.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_stack.setDoubleBuffered(true);
        player_stack.setFocusable(false);
        player_stack.setOpaque(true);

        javax.swing.GroupLayout avatar_panelLayout = new javax.swing.GroupLayout(avatar_panel);
        avatar_panel.setLayout(avatar_panelLayout);
        avatar_panelLayout.setHorizontalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(avatar)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(player_stack)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_buyin)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeout)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_pot))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addGroup(avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(timeout)
                    .addGroup(avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(player_buyin)
                        .addComponent(player_stack)))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel3.setFocusable(false);
        jPanel3.setOpaque(false);

        player_name.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        player_name.setForeground(new java.awt.Color(255, 255, 255));
        player_name.setText("12345678901234567890");
        player_name.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        player_name.setDoubleBuffered(true);
        player_name.setFocusable(false);
        player_name.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_nameMouseClicked(evt);
            }
        });

        utg_textfield.setBackground(new java.awt.Color(255, 204, 204));
        utg_textfield.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        utg_textfield.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        utg_textfield.setText("UTG");
        utg_textfield.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        utg_textfield.setDoubleBuffered(true);
        utg_textfield.setFocusable(false);
        utg_textfield.setOpaque(true);

        player_blind.setBackground(new java.awt.Color(51, 51, 255));
        player_blind.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        player_blind.setForeground(new java.awt.Color(255, 255, 255));
        player_blind.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_blind.setText("CP");
        player_blind.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_blind.setDoubleBuffered(true);
        player_blind.setFocusable(false);
        player_blind.setOpaque(true);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(player_name)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(utg_textfield)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_blind))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(player_name)
                    .addComponent(utg_textfield)
                    .addComponent(player_blind))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout indicadores_arribaLayout = new javax.swing.GroupLayout(indicadores_arriba);
        indicadores_arriba.setLayout(indicadores_arribaLayout);
        indicadores_arribaLayout.setHorizontalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(avatar_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        indicadores_arribaLayout.setVerticalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        player_action.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(com.tonikelope.coronapoker.Game.VISTA_COMPACTA?null:new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.Game.getZoom_level() * com.tonikelope.coronapoker.Game.getZOOM_STEP())), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.Game.getZoom_level() * com.tonikelope.coronapoker.Game.getZOOM_STEP()))));
        player_action.setOpaque(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panel_cartas, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(indicadores_arriba, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_cartas, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void timeoutMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_timeoutMouseClicked
        // TODO add your handling code here:

        // 0=yes, 1=no, 2=cancel
        if (Helpers.mostrarMensajeInformativoSINO(Game.getInstance(), "Este usuario tiene problemas de conexión que bloquean la partida. ¿Quieres expulsarlo?") == 0) {

            Helpers.threadRun(new Runnable() {
                public void run() {
                    crupier.clientPlayerQuit(nickname);
                }
            });
        }
    }//GEN-LAST:event_timeoutMouseClicked

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (Game.getInstance().isPartida_local() && !Game.getInstance().getParticipantes().get(player_name.getText()).isCpu()) {

            Identicon identicon = new Identicon(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance(), true, player_name.getText(), Game.getInstance().getParticipantes().get(player_name.getText()).getAes_key());

            identicon.setLocationRelativeTo(Game.getInstance().getFull_screen_frame() != null ? Game.getInstance().getFull_screen_frame() : Game.getInstance());

            identicon.setVisible(true);
        }
    }//GEN-LAST:event_player_nameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JLabel player_blind;
    private javax.swing.JLabel player_buyin;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JLabel player_stack;
    private com.tonikelope.coronapoker.Card playingCard1;
    private com.tonikelope.coronapoker.Card playingCard2;
    private javax.swing.JLabel timeout;
    private javax.swing.JLabel utg_textfield;
    // End of variables declaration//GEN-END:variables

    @Override
    public void zoom(float zoom_factor) {

        if (Helpers.float1DSecureCompare(0f, zoom_factor) < 0) {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    player_action.setMinimumSize(disabled ? null : new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));
                    LineBorder border = (LineBorder) getBorder();
                    setBorder(javax.swing.BorderFactory.createLineBorder(border.getLineColor(), Math.round(Player.BORDER * zoom_factor)));
                    getAvatar().setVisible(false);
                }
            });

            playingCard1.zoom(zoom_factor);
            playingCard2.zoom(zoom_factor);

            int altura_avatar = avatar_panel.getHeight();

            Helpers.zoomFonts(this, zoom_factor);

            if (!this.disabled) {
                while (altura_avatar == avatar_panel.getHeight()) {
                    try {
                        Thread.sleep(Game.GUI_ZOOM_WAIT);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                setAvatar();
            }
        }

    }

    @Override
    public void setWinner(String msg) {
        this.winner = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setBorder(javax.swing.BorderFactory.createLineBorder(Color.GREEN, Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));
                player_action.setBackground(Color.GREEN);
                player_action.setForeground(Color.BLACK);
                player_action.setText(msg);
                player_action.setEnabled(true);
            }
        });
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setBorder(javax.swing.BorderFactory.createLineBorder(Color.RED, Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                player_action.setBackground(Color.RED);
                player_action.setForeground(Color.WHITE);
                player_action.setText(msg);
                player_action.setEnabled(true);

                playingCard1.desenfocar();
                playingCard2.desenfocar();

                if (Helpers.float1DSecureCompare(stack, 0f) == 0) {
                    player_stack.setBackground(Color.RED);
                    player_stack.setForeground(Color.WHITE);
                }

            }
        });

    }

    @Override
    public void setBoteSecundario(String msg) {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                player_action.setText(player_action.getText() + " " + msg);
            }
        });
    }

    @Override
    public void pagar(float pasta) {

        this.pagar += pasta;

    }

    public void setPosition(int pos) {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                player_pot.setBackground(Color.WHITE);
                player_pot.setForeground(Color.black);
            }
        });

        switch (pos) {
            case Player.DEALER:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        player_blind.setVisible(true);
                        player_blind.setBackground(Color.white);
                        player_blind.setForeground(Color.black);
                        player_blind.setText(POSITIONS_LABELS[2]);
                    }
                });

                if (crupier.getDealer_pos() == crupier.getSmall_pos()) {
                    if (Helpers.float1DSecureCompare(crupier.getCiega_pequeña(), stack) < 0) {
                        setBet(crupier.getCiega_pequeña());
                        setStack(stack - bet);

                    } else {

                        //Vamos ALLIN
                        setBet(stack);
                        setStack(0f);
                        setDecision(Player.ALLIN);
                    }
                } else {
                    setBet(0.0f);
                }

                break;
            case Player.BIG_BLIND:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        player_blind.setVisible(true);
                        player_blind.setBackground(Color.yellow);
                        player_blind.setForeground(Color.black);
                        player_blind.setText(POSITIONS_LABELS[1]);
                    }
                });

                if (Helpers.float1DSecureCompare(crupier.getCiega_grande(), stack) < 0) {
                    setBet(crupier.getCiega_grande());
                    setStack(stack - bet);

                } else {

                    //Vamos ALLIN
                    setBet(stack);
                    setStack(0f);
                    setDecision(Player.ALLIN);
                }

                break;
            case Player.SMALL_BLIND:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        player_blind.setVisible(true);
                        player_blind.setBackground(Color.BLUE);
                        player_blind.setForeground(Color.white);
                        player_blind.setText(POSITIONS_LABELS[0]);
                    }
                });

                if (Helpers.float1DSecureCompare(crupier.getCiega_pequeña(), stack) < 0) {
                    setBet(crupier.getCiega_pequeña());
                    setStack(stack - bet);

                } else {

                    //Vamos ALLIN
                    setBet(stack);
                    setStack(0f);
                    setDecision(Player.ALLIN);
                }

                break;
            default:
                setBet(0.0f);

                break;
        }

    }

    public void reComprar(int cantidad) {

        this.stack = cantidad;
        this.buyin += cantidad;

        Game.getInstance().getRegistro().print(this.nickname + Translator.translate(" RECOMPRA (") + String.valueOf(Game.BUYIN) + ")");

        Helpers.playWavResource("misc/cash_register.wav");

        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_stack.setText(Helpers.float2String(stack));
                player_buyin.setText(String.valueOf(buyin));
                player_buyin.setBackground(Color.cyan);

            }
        });
    }

    @Override
    public void disablePlayer(boolean visible) {

        disabled = true;

        if (auto_action != null) {
            auto_action.stop();
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                if (visible) {
                    setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0, 0, 0, 0), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));
                    playingCard1.descargarCarta();
                    playingCard2.descargarCarta();

                    player_blind.setBackground(new Color(0, 0, 0, 0));
                    player_blind.setText(" ");
                    player_blind.setVisible(true);

                    player_pot.setBackground(new Color(0, 0, 0, 0));
                    player_pot.setText(" ");
                    player_pot.setVisible(true);

                    avatar.setVisible(false);

                    player_name.setVisible(false);
                    player_stack.setVisible(false);
                    player_buyin.setVisible(false);
                    timeout.setVisible(false);
                    utg_textfield.setVisible(false);

                    player_action.setBorder(null);
                    player_action.setMinimumSize(null);
                    player_action.setBackground(new Color(0, 0, 0, 0));
                    player_action.setText(" ");
                    player_action.setVisible(true);

                } else {

                    setVisible(false);
                }
            }
        });
    }

    public float getStack() {
        return stack;
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    @Override
    public void nuevaMano(int p) {

        pos = p;

        if (this.crupier == null) {
            this.crupier = Game.getInstance().getCrupier();
        }

        this.decision = Player.NODEC;

        this.winner = false;

        this.loser = false;

        this.bote = 0f;

        this.bet = 0f;

        setStack(stack + pagar);

        pagar = 0f;

        if (Helpers.float1DSecureCompare(getStack(), 0f) == 0) {
            reComprar(Game.BUYIN);
        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(204, 204, 204), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                utg_textfield.setVisible(false);

                player_blind.setVisible(false);

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);

                player_pot.setText("----");

                player_stack.setBackground(new Color(51, 153, 0));

                player_stack.setForeground(Color.WHITE);

            }
        });

        if (pos == crupier.getDealer_pos()) {
            this.setPosition(DEALER);
        } else if (pos == crupier.getBig_pos()) {
            this.setPosition(BIG_BLIND);
        } else if (pos == crupier.getSmall_pos()) {
            this.setPosition(SMALL_BLIND);
        }

        if (pos == crupier.getUtg_pos()) {
            this.setUTG();
        } else {
            this.disableUTG();
        }
    }

    public void resetBetDecision() {
        this.decision = Player.NODEC;

        Helpers.GUIRun(new Runnable() {
            public void run() {

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

            }
        });

    }

    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    utg_textfield.setVisible(false);

                }
            });
        }
    }

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                utg_textfield.setVisible(true);
                utg_textfield.setText("UTG");
                utg_textfield.setBackground(Color.PINK);
                utg_textfield.setForeground(Color.BLACK);

            }
        });
    }

    @Override
    public boolean isSpectator() {
        return this.spectator;
    }

    @Override
    public String getLastActionString() {

        String action = nickname + " ";

        switch (this.getDecision()) {
            case Player.FOLD:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.CHECK:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.BET:
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                break;
            case Player.ALLIN:
                action += player_action.getText();
                break;
            default:
                break;
        }

        return action;
    }

    public void setBuyin(int buyin) {
        this.buyin = buyin;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_buyin.setText(String.valueOf(buyin));

                if (buyin > Game.BUYIN) {
                    player_buyin.setBackground(Color.cyan);
                }
            }
        });
    }

    @Override
    public void setServer() {
        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_name.setForeground(Color.YELLOW);
            }
        });

    }

    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            if (crupier.getJugadoresActivos() + crupier.getTotalCalentando() < 2) {
                crupier.setJugadores_suficientes(false);
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                    player_blind.setVisible(false);
                    player_pot.setText("----");
                    player_pot.setBackground(null);
                    player_pot.setEnabled(false);
                    utg_textfield.setVisible(false);
                    playingCard1.descargarCarta();
                    playingCard2.descargarCarta();
                    player_stack.setBackground(null);
                    player_stack.setEnabled(false);
                    player_action.setText(msg != null ? msg : Translator.translate("ESPECTADOR"));
                    player_action.setBackground(null);
                    player_action.setEnabled(false);
                }
            });
        }
    }

    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + Game.ZOOM_LEVEL * Game.ZOOM_STEP))));

                player_pot.setVisible(true);
                player_pot.setEnabled(true);
                player_stack.setEnabled(true);
                player_action.setText(" ");
            }
        });
    }

    @Override
    public void showCards(String jugada) {
        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_action.setBackground(new Color(51, 153, 255));
                player_action.setForeground(Color.WHITE);
                player_action.setText(jugada);
            }
        });
    }

    @Override
    public void resetBote() {
        this.bote = 0f;
    }

    @Override
    public void setAvatar() {
        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                while (avatar_panel.getHeight() == 0) {
                    Helpers.pausar(Game.GUI_ZOOM_WAIT);
                }

                Participant p = Game.getInstance().getParticipantes().get(nickname);
                if (p != null) {
                    if (p.getAvatar() != null) {
                        getAvatar().setPreferredSize(new Dimension(avatar_panel.getHeight(), avatar_panel.getHeight()));
                        getAvatar().setIcon(new ImageIcon(new ImageIcon(p.getAvatar().getAbsolutePath()).getImage().getScaledInstance(avatar_panel.getHeight(), avatar_panel.getHeight(), Image.SCALE_SMOOTH)));
                    } else if (Game.getInstance().isPartida_local() && p.isCpu()) {
                        getAvatar().setPreferredSize(new Dimension(avatar_panel.getHeight(), avatar_panel.getHeight()));
                        getAvatar().setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage().getScaledInstance(avatar_panel.getHeight(), avatar_panel.getHeight(), Image.SCALE_SMOOTH)));
                    } else {
                        getAvatar().setPreferredSize(new Dimension(avatar_panel.getHeight(), avatar_panel.getHeight()));
                        getAvatar().setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(avatar_panel.getHeight(), avatar_panel.getHeight(), Image.SCALE_SMOOTH)));
                    }

                } else {

                    if (Game.getInstance().getSala_espera().getAvatar() != null) {
                        getAvatar().setPreferredSize(new Dimension(avatar_panel.getHeight(), avatar_panel.getHeight()));
                        getAvatar().setIcon(new ImageIcon(new ImageIcon(Game.getInstance().getSala_espera().getAvatar().getAbsolutePath()).getImage().getScaledInstance(avatar_panel.getHeight(), avatar_panel.getHeight(), Image.SCALE_SMOOTH)));
                    } else {
                        getAvatar().setPreferredSize(new Dimension(avatar_panel.getHeight(), avatar_panel.getHeight()));
                        getAvatar().setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(avatar_panel.getHeight(), avatar_panel.getHeight(), Image.SCALE_SMOOTH)));
                    }

                }

                getAvatar().setVisible(true);

            }
        });
    }

    @Override
    public boolean isActivo() {
        return (!exit && !spectator);
    }

}
