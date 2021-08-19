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
import java.util.concurrent.ConcurrentLinkedQueue;
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
    public static volatile String[][] ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? ACTIONS_LABELS_ES : ACTIONS_LABELS_EN;
    public static final String[] POSITIONS_LABELS_ES = new String[]{"CP", "CG", "DE"};
    public static final String[] POSITIONS_LABELS_EN = new String[]{"SB", "BB", "DE"};
    public static volatile String[] POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? POSITIONS_LABELS_ES : POSITIONS_LABELS_EN;
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.ORANGE, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 200;
    public static final int MIN_ACTION_HEIGHT = 45;

    private volatile String nickname;
    private volatile float stack = 0f;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile float bet = 0f;
    private volatile int decision = Player.NODEC;
    private volatile boolean utg = false;
    private volatile boolean spectator = false;
    private volatile float pagar = 0f;
    private volatile float bote = 0f;
    private volatile boolean exit = false;
    private volatile Timer auto_action = null;
    private volatile boolean timeout = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    private volatile float call_required;
    private volatile boolean turno = false;
    private volatile Bot bot = null;
    private volatile int response_counter;
    private volatile boolean spectator_bb = false;
    private volatile Color border_color = null;
    private volatile boolean player_stack_click = false;
    private volatile String iwtsth_action_text = null;
    private volatile String emoji = null;

    public boolean isTimeout() {
        return timeout;
    }

    private void setPlayerBorder(Color color, int size) {

        if (!timeout) {
            border_color = color;
        }

        setBorder(javax.swing.BorderFactory.createLineBorder(color, size));
    }

    public JLabel getPlayer_name() {
        return player_name;
    }

    public int getResponseTime() {

        return GameFrame.TIEMPO_PENSAR - response_counter;
    }

    public Bot getBot() {
        return bot;
    }

    public boolean isTurno() {
        return turno;
    }

    public void refreshPos() {

        if (this.isActivo()) {

            this.bote = 0f;

            if (Helpers.float1DSecureCompare(0f, this.bet) < 0) {
                setStack(this.stack + this.bet);
            }

            this.bet = 0f;

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                this.setPosition(BIG_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                this.setPosition(SMALL_BLIND);
            } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                this.setPosition(DEALER);
            } else {
                this.setPosition(-1);
            }

            if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
                this.setUTG();
            } else {
                this.disableUTG();
            }
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

            if (auto_action != null) {
                auto_action.stop();
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    playingCard1.resetearCarta();
                    playingCard2.resetearCarta();

                    player_action.setBackground(new Color(255, 102, 0));
                    player_action.setForeground(Color.WHITE);
                    player_action.setText(Translator.translate("ABANDONA LA TIMBA"));
                    player_action.setVisible(true);
                    player_action.setEnabled(true);
                }
            });

        }

    }

    public synchronized float getPagar() {
        return pagar;
    }

    public synchronized float getBote() {
        return bote;
    }

    public synchronized void setStack(float stack) {
        this.stack = Helpers.floatClean1D(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    if (buyin > GameFrame.BUYIN) {
                        player_stack.setBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        player_stack.setBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    player_stack.setText(Helpers.float2String(stack));

                }
            });
        }
    }

    public synchronized void setBet(float new_bet) {

        float old_bet = bet;

        bet = Helpers.floatClean1D(new_bet);

        if (Helpers.float1DSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.floatClean1D(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(new Runnable() {
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

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        if (this.getDecision() == Player.NODEC) {

            call_required = GameFrame.getInstance().getCrupier().getApuesta_actual() - bet;

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    setPlayerBorder(Color.ORANGE, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_pot.setBackground(Color.WHITE);

                    player_pot.setForeground(Color.BLACK);

                    player_action.setBackground(Color.WHITE);

                    player_action.setForeground(Color.BLACK);

                    player_action.setText(Translator.translate("PENSANDO"));

                    player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/thinking.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));

                    emoji = "thinking";

                    player_action.setBackground(null);

                    player_action.setEnabled(true);

                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                }
            });

            if (!GameFrame.TEST_MODE) {

                //Tiempo máximo para pensar
                Helpers.threadRun(new Runnable() {
                    public void run() {

                        response_counter = GameFrame.TIEMPO_PENSAR;

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        auto_action = new Timer(1000, new ActionListener() {

                            long t = GameFrame.getInstance().getCrupier().getTurno();

                            public void actionPerformed(ActionEvent ae) {

                                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isPlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && !WaitingRoomFrame.getInstance().isExit() && response_counter > 0 && t == GameFrame.getInstance().getCrupier().getTurno() && auto_action.isRunning() && getDecision() == Player.NODEC) {

                                    response_counter--;

                                    GameFrame.getInstance().getBarra_tiempo().setValue(response_counter);

                                    if (response_counter == 10 && Helpers.float1DSecureCompare(0f, call_required) < 0) {
                                        Helpers.playWavResource("misc/hurryup.wav");
                                    }

                                    if (response_counter == 0) {

                                        Helpers.threadRun(new Runnable() {
                                            public void run() {
                                                Helpers.playWavResourceAndWait("misc/timeout.wav");

                                                if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                                    GameFrame.getInstance().checkPause();

                                                    if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                                        auto_action.stop();
                                                    }
                                                }
                                            }
                                        });

                                    }

                                }
                            }
                        });

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
                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
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

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                            player_action.setText(ACTIONS_LABELS[dec - 1][1]);
                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                        }

                        player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/up.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                        emoji = "up";
                    }
                });

                break;
            case Player.BET:
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {
                            player_action.setText((GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.float2String(bet - GameFrame.getInstance().getCrupier().getApuesta_actual()) + ")");
                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.float2String(bet));
                        }
                        player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/glasses.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                        emoji = "glasses";
                    }
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        setPlayerBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                        if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet + stack) < 0) {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0] + " (+" + Helpers.float2String(bet + stack - GameFrame.getInstance().getCrupier().getApuesta_actual()) + ")");
                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                        }
                        player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/glasses.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                        emoji = "glasses";
                    }
                });
                break;
            default:
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        setPlayerBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                        player_action.setText(ACTIONS_LABELS[dec - 1][0]);

                        player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/down.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                        emoji = "down";
                    }
                });
                break;
        }

        Helpers.GUIRunAndWait(new Runnable() {
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
                    setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                }

                turno = false;

                synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                    GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                }
            }
        });
    }

    private void fold() {

        Helpers.playWavResource("misc/fold.wav");

        setDecision(Player.FOLD);

        playingCard1.setVisibleCard(false);
        playingCard2.setVisibleCard(false);

        finTurno();
    }

    private void check() {

        Helpers.playWavResource("misc/check.wav");

        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

        setDecision(Player.CHECK);

        finTurno();

    }

    public synchronized float getEffectiveStack() {

        return this.stack + this.bote + this.pagar;

    }

    private void bet(float new_bet) {

        Helpers.playWavResource("misc/bet.wav");

        setBet(new_bet);

        setDecision(Player.BET);

        if (GameFrame.SONIDOS_CHORRA && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {
            Helpers.playWavResource("misc/raise.wav");
        }

        finTurno();

    }

    private void allin() {

        Helpers.playWavResource("misc/allin.wav");

        GameFrame.getInstance().getCrupier().setPlaying_cinematic(true);

        Helpers.threadRun(new Runnable() {

            public void run() {

                if (!GameFrame.getInstance().getCrupier().remoteCinematicAllin()) {
                    GameFrame.getInstance().getCrupier().soundAllin();
                }
            }
        });

        setDecision(Player.ALLIN);

        setBet(this.stack + this.bet);

        finTurno();

    }

    public int getDecision() {
        return decision;
    }

    public float getBet() {
        return bet;
    }

    public void setTimeout(boolean val) {

        if (this.timeout != val) {

            this.timeout = val;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    timeout_icon.setVisible(val);

                    if (val) {

                        setPlayerBorder(Color.MAGENTA, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                    } else {
                        setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    }

                }
            });

            if (val) {
                Helpers.playWavResource("misc/network_error.wav");
            } else if (!GameFrame.getInstance().isPartida_local()) {
                Helpers.playWavResource("misc/yahoo.wav");
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

                border_color = ((LineBorder) getBorder()).getLineColor();

                danger.setVisible(false);

                timeout_icon.setVisible(false);

                player_pot.setText("----");

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                player_action.setIcon(null);

                emoji = null;

                utg_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/utg.png")).getImage().getScaledInstance(41, 31, Image.SCALE_SMOOTH)));

                utg_icon.setVisible(false);

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);
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

                if (GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(nickname).isCpu()) {

                    player_name.setToolTipText("CLICK -> AES-KEY");
                    player_name.setCursor(new Cursor(Cursor.HAND_CURSOR));

                } else if (!GameFrame.getInstance().isPartida_local()) {

                    if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {

                        if (GameFrame.getInstance().getSala_espera().isUnsecure_server() || GameFrame.getInstance().getParticipantes().get(nickname).isUnsecure_player()) {

                            danger.setVisible(true);

                        }

                        player_name.setForeground(Color.YELLOW);

                    }

                }
            }
        });

        if (GameFrame.getInstance().isPartida_local() && GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu()) {
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
        timeout_icon = new javax.swing.JLabel();
        player_pot = new javax.swing.JLabel();
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        player_action = new javax.swing.JLabel();
        danger = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
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

        timeout_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/timeout.png"))); // NOI18N
        timeout_icon.setToolTipText("ESTE JUGADOR TIENE PROBLEMAS DE CONEXIÓN");
        timeout_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        timeout_icon.setDoubleBuffered(true);
        timeout_icon.setFocusable(false);
        timeout_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                timeout_iconMouseClicked(evt);
            }
        });

        player_pot.setBackground(new java.awt.Color(255, 255, 255));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        player_pot.setDoubleBuffered(true);
        player_pot.setFocusable(false);
        player_pot.setOpaque(true);

        player_stack.setBackground(new java.awt.Color(51, 153, 0));
        player_stack.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_stack.setForeground(new java.awt.Color(255, 255, 255));
        player_stack.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_stack.setText("1000");
        player_stack.setToolTipText("CLICK PARA VER SU BUYIN");
        player_stack.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_stack.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_stack.setDoubleBuffered(true);
        player_stack.setFocusable(false);
        player_stack.setOpaque(true);
        player_stack.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_stackMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout avatar_panelLayout = new javax.swing.GroupLayout(avatar_panel);
        avatar_panel.setLayout(avatar_panelLayout);
        avatar_panelLayout.setHorizontalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(avatar)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(player_stack)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(timeout_icon)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_pot))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addGroup(avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(player_stack)
                    .addComponent(timeout_icon))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        nick_panel.setFocusable(false);
        nick_panel.setOpaque(false);

        player_name.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_name.setForeground(new java.awt.Color(255, 255, 255));
        player_name.setText("123456789012345");
        player_name.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_name.setDoubleBuffered(true);
        player_name.setFocusable(false);
        player_name.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_nameMouseClicked(evt);
            }
        });

        utg_icon.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        utg_icon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        utg_icon.setDoubleBuffered(true);
        utg_icon.setFocusable(false);

        javax.swing.GroupLayout nick_panelLayout = new javax.swing.GroupLayout(nick_panel);
        nick_panel.setLayout(nick_panelLayout);
        nick_panelLayout.setHorizontalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addComponent(player_name)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(utg_icon))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(player_name)
                    .addComponent(utg_icon))
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
                    .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        indicadores_arribaLayout.setVerticalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(avatar_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        player_action.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        player_action.setOpaque(true);
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_actionMouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                player_actionMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                player_actionMouseExited(evt);
            }
        });

        danger.setBackground(new java.awt.Color(255, 0, 0));
        danger.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        danger.setForeground(new java.awt.Color(255, 255, 255));
        danger.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger.setText("POSIBLE TRAMPOS@");
        danger.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        danger.setOpaque(true);

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
            .addComponent(danger, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(danger)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_cartas, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void timeout_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_timeout_iconMouseClicked
        // TODO add your handling code here:

        // 0=yes, 1=no, 2=cancel
        if (GameFrame.getInstance().isPartida_local()) {
            if (Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "Este usuario tiene problemas de conexión que bloquean la partida. ¿Quieres expulsarlo?") == 0) {

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        GameFrame.getInstance().getCrupier().remotePlayerQuit(nickname);
                    }
                });
            }
        } else {
            Helpers.mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "Este usuario tiene problemas de conexión que bloquean la partida.\n(El servidor decidirá si esperar a que se recupere o echarle).");
        }
    }//GEN-LAST:event_timeout_iconMouseClicked

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(player_name.getText()).isCpu()) {

            IdenticonDialog identicon = new IdenticonDialog(GameFrame.getInstance().getFrame(), true, player_name.getText(), GameFrame.getInstance().getParticipantes().get(player_name.getText()).getAes_key());

            identicon.setLocationRelativeTo(GameFrame.getInstance().getFrame());

            identicon.setVisible(true);
        }
    }//GEN-LAST:event_player_nameMouseClicked

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:
        if (!player_stack_click) {
            player_stack_click = true;

            player_stack.setText(String.valueOf(this.buyin));
            player_stack.setBackground(Color.GRAY);
            player_stack.setForeground(Color.WHITE);

            Helpers.threadRun(new Runnable() {
                public void run() {
                    Helpers.pausar(1500);

                    float s = getStack();

                    float e_s = getEffectiveStack();

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            if (Helpers.float1DSecureCompare(0f, e_s) == 0 && !isSpectator()) {

                                player_stack.setBackground(Color.RED);

                                player_stack.setForeground(Color.WHITE);

                                player_stack.setText(Helpers.float2String(0f));

                            } else {

                                if (buyin > GameFrame.BUYIN) {
                                    player_stack.setBackground(Color.CYAN);

                                    player_stack.setForeground(Color.BLACK);
                                } else {

                                    player_stack.setBackground(new Color(51, 153, 0));

                                    player_stack.setForeground(Color.WHITE);
                                }

                                player_stack.setText(Helpers.float2String(s));
                            }

                        }
                    });

                    player_stack_click = false;

                }
            });
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void player_actionMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseEntered
        // TODO add your handling code here:
        if (isIwtsthCandidate() && GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized() && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {
            iwtsth_action_text = player_action.getText();
            player_action.setCursor(new Cursor(Cursor.HAND_CURSOR));
            player_action.setText("IWTSTH");
            player_action.setBackground(Color.WHITE);
            player_action.setForeground(Color.RED);
        }
    }//GEN-LAST:event_player_actionMouseEntered

    private void player_actionMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseExited
        // TODO add your handling code here:
        if (iwtsth_action_text != null && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {
            player_action.setText(iwtsth_action_text);
            player_action.setBackground(Color.RED);
            player_action.setForeground(Color.WHITE);
            iwtsth_action_text = null;
        }

        player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_player_actionMouseExited

    private void player_actionMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseClicked
        // TODO add your handling code here:

        if (isIwtsthCandidate() && GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized() && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {

            player_actionMouseExited(evt);
            GameFrame.getInstance().getCrupier().IWTSTH_REQUEST(GameFrame.getInstance().getLocalPlayer().getNickname());

        }
    }//GEN-LAST:event_player_actionMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JLabel danger;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JPanel panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JLabel player_stack;
    private com.tonikelope.coronapoker.Card playingCard1;
    private com.tonikelope.coronapoker.Card playingCard2;
    private javax.swing.JLabel timeout_icon;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    public boolean isIwtsthCandidate() {
        return isActivo() && getPlayingCard1().isVisible_card() && getPlayingCard1().isTapada() && getPlayingCard1().isDesenfocada();
    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        if (Helpers.float1DSecureCompare(0f, zoom_factor) < 0) {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    player_action.setIcon(null);

                    player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                    setPlayerBorder(((LineBorder) getBorder()).getLineColor(), Math.round(Player.BORDER * zoom_factor));
                    getAvatar().setVisible(false);

                    utg_icon.setVisible(false);
                }
            });

            playingCard1.zoom(zoom_factor, mynotifier);
            playingCard2.zoom(zoom_factor, mynotifier);

            int h = player_pot.getHeight();

            Helpers.zoomFonts(this, zoom_factor, null);

            while (player_pot.getHeight() == h) {
                Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);
            }

            do {
                h = player_pot.getHeight();
                Helpers.pausar(GameFrame.GUI_ZOOM_WAIT);
            } while (h != player_pot.getHeight());

            setAvatar();
            utgIconZoom();
            emojiZoom();

            while (mynotifier.size() < 2) {

                synchronized (mynotifier) {

                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
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

    @Override
    public void setWinner(String msg) {
        this.winner = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(Color.GREEN, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                player_action.setBackground(Color.GREEN);
                player_action.setForeground(Color.BLACK);
                player_action.setText(msg);
                player_action.setEnabled(true);
                player_action.setIcon(null);
                emoji = null;
            }
        });
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(Color.RED, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                player_action.setBackground(Color.RED);
                player_action.setForeground(Color.WHITE);
                player_action.setText(msg);
                player_action.setEnabled(true);

                player_action.setIcon(null);
                emoji = null;

                playingCard1.desenfocar();
                playingCard2.desenfocar();

                if (Helpers.float1DSecureCompare(stack, 0f) == 0 && !player_stack_click) {
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
                        player_name.setOpaque(true);
                        player_name.setBackground(new Color(230, 229, 235));
                        player_name.setForeground(Color.BLACK);
                    }
                });

                this.getPlayingCard1().setPosChip(Player.DEALER, 1);

                if (GameFrame.getInstance().getCrupier().getDealer_nick().equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                    if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                        setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                    } else {

                        //Vamos ALLIN
                        setDecision(Player.ALLIN);
                        setBet(stack);
                    }
                } else {
                    setBet(0f);
                }

                break;
            case Player.BIG_BLIND:

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        player_name.setOpaque(true);
                        player_name.setBackground(new Color(241, 185, 30));
                        player_name.setForeground(Color.BLACK);
                    }
                });

                this.getPlayingCard1().setPosChip(Player.BIG_BLIND, 1);

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

                } else {

                    //Vamos ALLIN
                    setDecision(Player.ALLIN);
                    setBet(stack);
                }

                break;
            case Player.SMALL_BLIND:

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {

                        player_name.setOpaque(true);
                        player_name.setBackground(new Color(24, 52, 178));
                        player_name.setForeground(Color.WHITE);
                    }
                });

                this.getPlayingCard1().setPosChip(Player.SMALL_BLIND, 1);

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    //Vamos ALLIN
                    setDecision(Player.ALLIN);

                    setBet(stack);
                }

                break;
            default:
                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        player_name.setOpaque(false);
                        player_name.setBackground(null);

                        if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                            player_name.setForeground(Color.YELLOW);
                        } else {
                            player_name.setForeground(Color.WHITE);
                        }
                    }
                });

                this.getPlayingCard1().resetPosChip();

                setBet(0f);

                break;
        }

    }

    public synchronized void reComprar(int cantidad) {

        this.stack += cantidad;
        this.buyin += cantidad;

        GameFrame.getInstance().getRegistro().print(this.nickname + Translator.translate(" RECOMPRA (") + String.valueOf(cantidad) + ")");

        Helpers.playWavResource("misc/cash_register.wav");

        if (!player_stack_click) {
            Helpers.GUIRun(new Runnable() {
                public void run() {
                    player_stack.setText(Helpers.float2String(stack));
                    player_stack.setBackground(Color.CYAN);
                    player_stack.setForeground(Color.BLACK);
                }
            });
        }
    }

    public synchronized float getStack() {
        return stack;
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    @Override
    public void nuevaMano() {

        this.decision = Player.NODEC;

        this.winner = false;

        this.loser = false;

        this.bote = 0f;

        this.bet = 0f;

        setStack(stack + pagar);

        pagar = 0f;

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {
            reComprar((Integer) GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname));
        }

        iwtsth_action_text = null;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                setPlayerBorder(new java.awt.Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                player_action.setIcon(null);

                emoji = null;

                utg_icon.setVisible(false);

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);

                player_pot.setText("----");

                if (!player_stack_click) {
                    if (buyin > GameFrame.BUYIN) {
                        player_stack.setBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        player_stack.setBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }
                }

            }
        });

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            this.setPosition(BIG_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            this.setPosition(SMALL_BLIND);
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            this.setPosition(DEALER);
        } else {
            this.setPosition(-1);
        }

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getUtg_nick())) {
            this.setUTG();
        } else {
            this.disableUTG();
        }

        if (this.spectator_bb) {

            this.spectator_bb = false;

            if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack + bet) < 0) {
                setBet(GameFrame.getInstance().getCrupier().getCiega_grande());
            } else {

                //Vamos ALLIN
                setDecision(Player.ALLIN);
                setBet(stack);

            }

        }

    }

    public void resetBetDecision() {
        this.decision = Player.NODEC;

        Helpers.GUIRun(new Runnable() {
            public void run() {

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                player_action.setIcon(null);

                emoji = null;

            }
        });

    }

    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    utg_icon.setVisible(false);

                }
            });
        }
    }

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                utg_icon.setVisible(true);

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

    }

    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_pot.setText("----");
                    player_pot.setBackground(null);
                    player_pot.setEnabled(false);
                    utg_icon.setVisible(false);
                    playingCard1.resetearCarta();
                    playingCard2.resetearCarta();

                    player_action.setText(msg != null ? msg : Translator.translate("ESPECTADOR"));
                    player_action.setBackground(null);
                    player_action.setEnabled(false);
                    player_action.setIcon(null);
                    emoji = null;
                    player_name.setOpaque(false);
                    player_name.setBackground(null);

                    if (buyin > GameFrame.BUYIN) {
                        player_stack.setBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        player_stack.setBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                        player_name.setForeground(Color.YELLOW);
                    } else {
                        player_name.setForeground(Color.WHITE);
                    }
                }
            });
        }
    }

    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                player_pot.setVisible(true);
                player_pot.setEnabled(true);
                player_stack.setEnabled(true);
                player_action.setText(" ");
            }
        });
    }

    private void emojiZoom() {

        if (emoji != null) {

            ImageIcon icon = new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/" + emoji + ".png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH));

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    player_action.setIcon(icon);

                }
            });

        }

    }

    private void utgIconZoom() {

        ImageIcon icon = new ImageIcon(IMAGEN_UTG.getImage().getScaledInstance((int) Math.round(nick_panel.getHeight() * (480f / 360f)), nick_panel.getHeight(), Image.SCALE_SMOOTH));

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                utg_icon.setIcon(icon);

                utg_icon.setPreferredSize(new Dimension((int) Math.round(nick_panel.getHeight() * (480f / 360f)), nick_panel.getHeight()));

                utg_icon.setVisible(utg);
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
        this.bet = 0f;
        this.bote = 0f;
    }

    @Override
    public void setAvatar() {

        int h = player_pot.getHeight();

        ImageIcon avatar;

        String avatar_path = GameFrame.getInstance().getNick2avatar().get(nickname);

        if (!"".equals(avatar_path) && !"*".equals(avatar_path)) {

            avatar = new ImageIcon(new ImageIcon(avatar_path).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH));

        } else if ("*".equals(avatar_path)) {

            avatar = new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_bot.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH));

        } else {

            avatar = new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH));
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                getAvatar().setPreferredSize(new Dimension(h, h));

                getAvatar().setIcon(avatar);

                getAvatar().setVisible(true);

            }
        });
    }

    @Override
    public boolean isCalentando() {

        return (spectator && Helpers.float1DSecureCompare(0f, stack) < 0);
    }

    @Override
    public boolean isActivo() {
        return (!exit && !spectator);
    }

    @Override
    public synchronized void setPagar(float pagar) {
        this.pagar = pagar;
    }

    @Override
    public void destaparCartas(boolean sound) {

        if (getPlayingCard1().isIniciada() && getPlayingCard1().isTapada()) {

            if (sound) {
                Helpers.playWavResource("misc/uncover.wav", false);
            }

            getPlayingCard1().setPosChip_visible(false);
            getPlayingCard1().destapar(false);
            getPlayingCard2().setPosChip_visible(false);
            getPlayingCard2().destapar(false);
        }
    }

    @Override
    public void ordenarCartas() {
        if (getPlayingCard1().getValorNumerico() != -1 && getPlayingCard2().getValorNumerico() != -1 && getPlayingCard1().getValorNumerico() < getPlayingCard2().getValorNumerico()) {

            //Ordenamos las cartas para mayor comodidad
            String valor1 = this.playingCard1.getValor();
            String palo1 = this.playingCard1.getPalo();
            boolean desenfocada1 = this.playingCard1.isDesenfocada();

            this.playingCard1.actualizarValorPaloEnfoque(this.playingCard2.getValor(), this.playingCard2.getPalo(), this.playingCard2.isDesenfocada());
            this.playingCard2.actualizarValorPaloEnfoque(valor1, palo1, desenfocada1);
        }
    }

    @Override
    public void setSpectatorBB(boolean bb) {
        this.spectator_bb = bb;
    }

}
