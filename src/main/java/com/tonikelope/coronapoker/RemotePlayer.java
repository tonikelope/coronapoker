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

import static com.tonikelope.coronapoker.GameFrame.TTS_NO_SOUND_TIMEOUT;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import org.apache.commons.codec.binary.Base64;

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
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.YELLOW, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
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
    private volatile Float last_bote = null;
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
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile Timer iwtsth_blink_timer = null;
    private volatile boolean notify_blocked = false;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final Object zoom_lock = new Object();
    private final JLabel chat_notify_label = new JLabel();
    private final JLabel chip_label = new JLabel();
    private final JLabel sec_pot_win_label = new JLabel();
    private final ConcurrentLinkedQueue<Integer> botes_secundarios = new ConcurrentLinkedQueue<>();
    private volatile boolean reraise;
    private volatile boolean muestra = false;
    private volatile int conta_win = 0;
    private volatile AntiCheatLogDialog anticheat_dialog = null;

    public AntiCheatLogDialog getAnticheat_dialog() {
        return anticheat_dialog;
    }

    public void setAnticheat_dialog(AntiCheatLogDialog anticheat_dialog) {
        this.anticheat_dialog = anticheat_dialog;
    }

    public void refreshNotifyChatLabel() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                if (getChat_notify_label().isVisible()) {

                    Helpers.threadRun(new Runnable() {
                        @Override
                        public void run() {

                            if (chat_notify_image_url != null) {
                                setNotifyImageChatLabel(chat_notify_image_url);
                            } else {
                                setNotifyTTSChatLabel();
                            }
                        }
                    });

                }
            }
        });

    }

    public boolean isMuestra() {
        return muestra;
    }

    public void setNotifyTTSChatLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                int sound_icon_size_h = getHoleCard1().getHeight();

                int sound_icon_size_w = Math.round((596 * sound_icon_size_h) / 460);

                int pos_x = Math.round((panel_cartas.getWidth() - sound_icon_size_w) / 2);

                int pos_y = 0;

                getChat_notify_label().setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/talk.png")).getImage().getScaledInstance(sound_icon_size_w, sound_icon_size_h, Image.SCALE_SMOOTH)));

                getChat_notify_label().setSize(sound_icon_size_w, sound_icon_size_h);

                getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

                getChat_notify_label().setOpaque(false);

                getChat_notify_label().revalidate();

                getChat_notify_label().repaint();

                getChat_notify_label().setLocation(pos_x, pos_y);

            }
        });
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {

        if (!this.isNotify_blocked()) {

            try {

                chat_notify_image_url = u;

                String url = chat_notify_image_url.toString();

                final boolean isgif;

                int gif_l = ChatImageDialog.GIF_CACHE.containsKey(url) ? (int) ChatImageDialog.GIF_CACHE.get(url)[1] : -1;

                int timeout = (isgif = (gif_l != -1 || Helpers.isImageGIF(new URL(url)))) ? Math.max(gif_l != -1 ? gif_l : (gif_l = Helpers.getGIFLength(new URL(url))), TTS_NO_SOUND_TIMEOUT) : TTS_NO_SOUND_TIMEOUT;

                Helpers.threadRun(new Runnable() {
                    @Override
                    public void run() {

                        chat_notify_thread = Thread.currentThread().getId();

                        synchronized (getChat_notify_label()) {

                            getChat_notify_label().notifyAll();

                            Helpers.GUIRunAndWait(new Runnable() {
                                @Override
                                public void run() {

                                    try {
                                        int max_width = panel_cartas.getWidth();

                                        int max_height = holeCard1.getHeight();

                                        ImageIcon image = new ImageIcon(new URL(url + "#" + String.valueOf(System.currentTimeMillis())));;

                                        if (image.getIconHeight() > max_height || image.getIconWidth() > max_width) {

                                            int new_height = max_height;

                                            int new_width = (int) Math.round((image.getIconWidth() * max_height) / image.getIconHeight());

                                            if (new_width > max_width) {

                                                new_height = (int) Math.round((new_height * max_width) / new_width);

                                                new_width = max_width;
                                            }

                                            image = new ImageIcon(image.getImage().getScaledInstance(new_width, new_height, isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));
                                        }

                                        int pos_x = Math.round((panel_cartas.getWidth() - image.getIconWidth()) / 2);

                                        int pos_y = Math.round((getHoleCard1().getHeight() - image.getIconHeight()) / 2);

                                        getChat_notify_label().setIcon(image);

                                        getChat_notify_label().setSize(image.getIconWidth(), image.getIconHeight());

                                        getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

                                        getChat_notify_label().setOpaque(false);

                                        getChat_notify_label().revalidate();

                                        getChat_notify_label().repaint();

                                        getChat_notify_label().setLocation(pos_x, pos_y);

                                        getChat_notify_label().setVisible(true);
                                    } catch (MalformedURLException ex) {
                                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                }
                            });

                            try {
                                getChat_notify_label().wait(timeout);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        }

                        if (Thread.currentThread().getId() == chat_notify_thread) {
                            Helpers.GUIRunAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    getChat_notify_label().setVisible(false);

                                }
                            });
                        }

                    }
                });

            } catch (Exception ex) {
                Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    public void refreshSecPotLabel() {

        if (Helpers.float1DSecureCompare(0f, pagar) < 0 && GameFrame.getInstance().getCrupier().getBote().getSide_pot_count() > 0) {

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    sec_pot_win_label.setBackground(Color.BLACK);

                    sec_pot_win_label.setForeground(Color.WHITE);

                    sec_pot_win_label.setSize(player_action.getSize());

                    sec_pot_win_label.setPreferredSize(sec_pot_win_label.getSize());

                    int pos_x = Math.round((panel_cartas.getWidth() - sec_pot_win_label.getWidth()) / 2);

                    int pos_y = Math.round(GameFrame.VISTA_COMPACTA > 0 ? (getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) : ((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2));

                    sec_pot_win_label.setLocation(pos_x, pos_y);

                    String[] botes = new String[botes_secundarios.size()];

                    int i = 0;

                    for (Integer b : botes_secundarios) {
                        botes[i++] = "#" + String.valueOf(b);
                    }

                    float mibote = last_bote != null ? last_bote : bote;

                    sec_pot_win_label.setText(String.join("+", botes) + " = " + Helpers.float2String(pagar) + " (" + Helpers.float2String(pagar - mibote) + ")");

                    sec_pot_win_label.setVisible(true);
                }
            });

        }

    }

    public boolean isNotify_blocked() {
        return notify_blocked;
    }

    public JLabel getChip_label() {
        return chip_label;
    }

    public JLabel getChat_notify_label() {
        return chat_notify_label;
    }

    public JLayeredPane getPanel_cartas() {
        return panel_cartas;
    }

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
            this.timeout = false;

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    holeCard1.resetearCarta();
                    holeCard2.resetearCarta();

                    player_action.setBackground(new Color(255, 102, 0));
                    player_action.setForeground(Color.WHITE);
                    player_action.setText(Translator.translate("SE PIRA"));
                    setPlayerActionIcon("exit.png");
                    player_action.setVisible(true);

                    chip_label.setVisible(false);
                    sec_pot_win_label.setVisible(false);

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
        this.stack = Helpers.floatClean(stack);

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

        bet = Helpers.floatClean(new_bet);

        if (Helpers.float1DSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.floatClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                if (Helpers.float1DSecureCompare(0f, bote) < 0) {

                    player_pot.setText(Helpers.float2String(bote));

                } else {

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

                    player_action.setBackground(new Color(204, 204, 204, 75));

                    player_action.setForeground(Color.LIGHT_GRAY);

                    player_action.setText(Translator.translate("PENSANDO"));

                    setPlayerActionIcon("action/thinking.png");

                    Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.TIEMPO_PENSAR);
                }
            });

            if (!GameFrame.TEST_MODE) {

                //Tiempo máximo para pensar
                Helpers.GUIRun(new Runnable() {
                    public void run() {

                        response_counter = GameFrame.TIEMPO_PENSAR;

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        auto_action = new Timer(1000, new ActionListener() {

                            long t = GameFrame.getInstance().getCrupier().getTurno();

                            public void actionPerformed(ActionEvent ae) {

                                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && !WaitingRoomFrame.getInstance().isExit() && response_counter > 0 && t == GameFrame.getInstance().getCrupier().getTurno() && auto_action.isRunning() && getDecision() == Player.NODEC) {

                                    response_counter--;

                                    GameFrame.getInstance().getBarra_tiempo().setValue(response_counter);

                                    if (response_counter == 10 && Helpers.float1DSecureCompare(0f, call_required) < 0) {
                                        Audio.playWavResource("misc/hurryup.wav");
                                    }

                                    if (response_counter == 0) {

                                        Helpers.threadRun(new Runnable() {
                                            public void run() {
                                                Audio.playWavResourceAndWait("misc/timeout.wav");

                                                GameFrame.getInstance().checkPause();

                                                Helpers.GUIRun(new Runnable() {
                                                    public void run() {

                                                        if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                                            auto_action.stop();
                                                        }
                                                    }
                                                });

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

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);
                if (auto_action != null) {
                    auto_action.stop();
                }

            }
        });

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

    private void setDecision(int dec) {

        this.decision = dec;

        reraise = false;

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

                        setPlayerActionIcon("action/up.png");
                    }
                });

                break;
            case Player.BET:
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {
                            player_action.setText((GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.float2String(bet - GameFrame.getInstance().getCrupier().getApuesta_actual()) + ")");

                            if (GameFrame.getInstance().getCrupier().getConta_raise() > 0) {
                                reraise = true;
                            }

                        } else {
                            player_action.setText(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.float2String(bet));
                        }
                        setPlayerActionIcon("action/bet.png");
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
                        setPlayerActionIcon("action/glasses.png");
                    }
                });
                break;
            default:
                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        setPlayerBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                        player_action.setText(ACTIONS_LABELS[dec - 1][0]);

                        setPlayerActionIcon("action/down.png");
                    }
                });
                break;
        }

        Helpers.GUIRunAndWait(new Runnable() {
            @Override
            public void run() {

                if (!reraise) {
                    player_action.setBackground(ACTIONS_COLORS[dec - 1][0]);
                    player_action.setForeground(ACTIONS_COLORS[dec - 1][1]);

                    player_pot.setBackground(ACTIONS_COLORS[dec - 1][0]);
                    player_pot.setForeground(ACTIONS_COLORS[dec - 1][1]);
                } else {
                    player_action.setBackground(RERAISE_BACK_COLOR);
                    player_action.setForeground(RERAISE_FORE_COLOR);

                    player_pot.setBackground(RERAISE_BACK_COLOR);
                    player_pot.setForeground(RERAISE_FORE_COLOR);
                }

                revalidate();
                repaint();

            }
        });
    }

    public void finTurno() {

        Audio.stopWavResource("misc/hurryup.wav");

        Helpers.GUIRun(new Runnable() {
            public void run() {

                if (decision != Player.ALLIN && decision != Player.FOLD) {
                    setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                }

                turno = false;

                synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                    GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
                }
            }
        });
    }

    private void fold() {

        Audio.playWavResource("misc/fold.wav");

        holeCard1.setVisibleCard(false);
        holeCard2.setVisibleCard(false);

        setDecision(Player.FOLD);

        finTurno();
    }

    private void check() {

        Audio.playWavResource("misc/check.wav");

        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

        setDecision(Player.CHECK);

        finTurno();

    }

    public synchronized float getEffectiveStack() {

        return Helpers.floatClean(this.stack) + Helpers.floatClean(this.bote) + Helpers.floatClean(this.pagar);

    }

    private void bet(float new_bet) {

        Audio.playWavResource("misc/bet.wav");

        setBet(new_bet);

        setDecision(Player.BET);

        if (GameFrame.SONIDOS_CHORRA && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {

            Audio.playWavResource("misc/raise.wav");

        }

        finTurno();

    }

    private void allin() {

        Audio.playWavResource("misc/allin.wav");

        Init.PLAYING_CINEMATIC = true;

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

                    if (val) {
                        setPlayerBorder(Color.MAGENTA, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                        setPlayerActionIcon("action/timeout.png");
                    } else {
                        setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                        setPlayerActionIcon(player_action_icon);
                    }

                }
            });

            if (val && GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(this.nickname).isForce_reset_socket()) {
                Audio.playWavResource("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav");
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

                setOpaque(false);

                setBackground(null);

                hands_win.setVisible(false);

                sec_pot_win_label.setVisible(false);

                sec_pot_win_label.setDoubleBuffered(true);

                sec_pot_win_label.setHorizontalAlignment(JLabel.CENTER);

                sec_pot_win_label.setOpaque(true);

                sec_pot_win_label.setFocusable(false);

                sec_pot_win_label.setFont(player_action.getFont().deriveFont(player_action.getFont().getStyle(), Math.round(player_action.getFont().getSize() * 0.7f)));

                panel_cartas.add(sec_pot_win_label, new Integer(1003));

                chat_notify_label.setVisible(false);

                chat_notify_label.setDoubleBuffered(true);

                chat_notify_label.setFocusable(false);

                chat_notify_label.setCursor(new Cursor(Cursor.HAND_CURSOR));

                chat_notify_label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {

                        chat_notify_label.setVisible(false);

                        if (SwingUtilities.isRightMouseButton(e)) {
                            notify_blocked = true;
                        }

                        Helpers.threadRun(new Runnable() {
                            @Override
                            public void run() {

                                synchronized (chat_notify_label) {

                                    chat_notify_label.notifyAll();
                                }
                            }
                        });
                    }
                });

                panel_cartas.add(chat_notify_label, new Integer(1002));

                chip_label.setVisible(false);

                chip_label.setDoubleBuffered(true);

                chip_label.setCursor(new Cursor(Cursor.HAND_CURSOR));

                chip_label.setOpaque(false);

                chip_label.setFocusable(false);

                chip_label.setSize(new Dimension(100, 100));

                panel_cartas.add(chip_label, new Integer(1001));

                border_color = ((LineBorder) getBorder()).getLineColor();

                danger.setVisible(false);

                player_pot.setText("----");

                disablePlayerAction();

                Helpers.setScaledIconLabel(utg_icon, getClass().getResource("/images/utg.png"), 41, 31);

                utg_icon.setVisible(false);

                icon_zoom_timer = new Timer(GameFrame.GUI_ZOOM_WAIT, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {

                        icon_zoom_timer.stop();
                        zoomIcons();
                        holeCard1.updateImagePreloadCache();
                        holeCard2.updateImagePreloadCache();
                        refreshNotifyChatLabel();

                    }
                });

                icon_zoom_timer.setRepeats(false);
                icon_zoom_timer.setCoalesce(false);

                iwtsth_blink_timer = new Timer(1500, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {

                        if (player_action.getBackground() == Color.RED) {
                            player_action.setBackground(Color.WHITE);
                            player_action.setForeground(Color.RED);
                        } else {
                            player_action.setBackground(Color.RED);
                            player_action.setForeground(Color.WHITE);
                        }

                        player_action.setText(player_action.getText().equals(Translator.translate("PIERDE")) ? Translator.translate("¿IWTSTH?") : Translator.translate("PIERDE"));
                    }
                });
            }
        });

    }

    public void playerActionClick() {
        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_actionMouseClicked(null);
            }
        });
    }

    public Card getHoleCard1() {
        return holeCard1;
    }

    public Card getHoleCard2() {
        return holeCard2;
    }

    public ArrayList<Card> getHoleCards() {
        ArrayList<Card> cartas = new ArrayList<>();

        cartas.add(getHoleCard1());

        cartas.add(getHoleCard2());
        return cartas;
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

                    avatar.setToolTipText("CLICK -> AES-KEY");
                    avatar.setCursor(new Cursor(Cursor.HAND_CURSOR));

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

        panel_cartas = new javax.swing.JLayeredPane();
        holeCard1 = new com.tonikelope.coronapoker.Card();
        holeCard2 = new com.tonikelope.coronapoker.Card();
        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        player_pot = new javax.swing.JLabel();
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        hands_win = new javax.swing.JLabel();
        player_action = new javax.swing.JLabel();
        danger = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204, 75), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        panel_cartas.setDoubleBuffered(true);

        panel_cartas.setLayer(holeCard1, javax.swing.JLayeredPane.DEFAULT_LAYER);
        panel_cartas.setLayer(holeCard2, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        indicadores_arriba.setFocusable(false);
        indicadores_arriba.setOpaque(false);

        avatar_panel.setFocusable(false);
        avatar_panel.setOpaque(false);

        avatar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_null.png"))); // NOI18N
        avatar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar.setDoubleBuffered(true);
        avatar.setFocusable(false);
        avatar.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                avatarMouseClicked(evt);
            }
        });

        player_pot.setBackground(new Color(204,204,204,75));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 32)); // NOI18N
        player_pot.setForeground(new java.awt.Color(255, 255, 255));
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
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
                .addComponent(player_pot))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(player_stack)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        nick_panel.setFocusable(false);
        nick_panel.setOpaque(false);

        player_name.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_name.setForeground(new java.awt.Color(255, 255, 255));
        player_name.setText("123456789012345");
        player_name.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_name.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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

        hands_win.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        hands_win.setForeground(new java.awt.Color(255, 255, 255));
        hands_win.setText("(0)");
        hands_win.setToolTipText("MANOS GANADAS");
        hands_win.setDoubleBuffered(true);

        javax.swing.GroupLayout nick_panelLayout = new javax.swing.GroupLayout(nick_panel);
        nick_panel.setLayout(nick_panelLayout);
        nick_panelLayout.setHorizontalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_name)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(utg_icon)
                .addGap(5, 5, 5)
                .addComponent(hands_win))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(player_name)
                    .addComponent(utg_icon)
                    .addComponent(hands_win))
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
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_action.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP)), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        player_action.setOpaque(true);
        player_action.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_actionMouseClicked(evt);
            }
        });

        danger.setBackground(new java.awt.Color(255, 0, 0));
        danger.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        danger.setForeground(new java.awt.Color(255, 255, 255));
        danger.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger.setText("POSIBLE TRAMPOS@");
        danger.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        danger.setFocusable(false);
        danger.setOpaque(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(danger, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(indicadores_arriba, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_cartas, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(danger)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_cartas)
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:
        if (!player_stack_click) {
            player_stack_click = true;

            player_stack.setText(Helpers.float2String((float) this.buyin));
            player_stack.setBackground(Color.GRAY);
            player_stack.setForeground(Color.WHITE);

            Helpers.threadRun(new Runnable() {
                public void run() {
                    Helpers.pausar(1500);

                    float s = getStack();

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            if (buyin > GameFrame.BUYIN) {
                                player_stack.setBackground(Color.CYAN);

                                player_stack.setForeground(Color.BLACK);
                            } else {

                                player_stack.setBackground(new Color(51, 153, 0));

                                player_stack.setForeground(Color.WHITE);
                            }

                            player_stack.setText(Helpers.float2String(s));

                        }
                    });

                    player_stack_click = false;

                }
            });
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void player_actionMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_actionMouseClicked
        // TODO add your handling code here:

        if (GameFrame.IWTSTH_RULE && isIwtsthCandidate() && GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized() && !GameFrame.getInstance().getCrupier().isIwtsthing() && !GameFrame.getInstance().getCrupier().isIwtsthing_request() && !GameFrame.getInstance().getCrupier().isIwtsth() && GameFrame.getInstance().getCrupier().isShow_time()) {

            GameFrame.getInstance().getCrupier().IWTSTH_REQUEST(GameFrame.getInstance().getLocalPlayer().getNickname());
        }
    }//GEN-LAST:event_player_actionMouseClicked

    private void avatarMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatarMouseClicked
        // TODO add your handling code here:
        if (GameFrame.getInstance().isPartida_local() && !GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu()) {

            IdenticonDialog identicon = new IdenticonDialog(GameFrame.getInstance().getFrame(), true, this.nickname, GameFrame.getInstance().getParticipantes().get(this.nickname).getAes_key());

            identicon.setLocationRelativeTo(GameFrame.getInstance().getFrame());

            identicon.setVisible(true);
        }
    }//GEN-LAST:event_avatarMouseClicked

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (GameFrame.getInstance().isPartida_local() && SwingUtilities.isRightMouseButton(evt)) {

            if (!GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu() && this.timeout && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "Este usuario tiene problemas de conexión. ¿EXPULSAR DE LA TIMBA?") == 0) {
                GameFrame.getInstance().getCrupier().remotePlayerQuit(this.nickname);
            }

        } else {

            if (this.anticheat_dialog != null) {

                this.anticheat_dialog.setLocationRelativeTo(GameFrame.getInstance().getFrame());
                this.anticheat_dialog.setVisible(true);

            } else if ((!GameFrame.getInstance().isPartida_local() || !GameFrame.getInstance().getParticipantes().get(this.nickname).isCpu()) && !GameFrame.getInstance().getCrupier().isGenerando_informe_anticheat() && Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿SOLICITAR INFORME ANTICHEAT?\n(AVISO: sólo puedes pedir uno por jugador y timba, así que elige bien el momento).") == 0) {

                try {

                    GameFrame.getInstance().getCrupier().setGenerando_informe_anticheat(true);

                    if (GameFrame.getInstance().isPartida_local()) {

                        GameFrame.getInstance().getParticipantes().get(this.nickname).writeGAMECommandFromServer("SNAPSHOT#" + Base64.encodeBase64String(GameFrame.getInstance().getLocalPlayer().getNickname().getBytes("UTF-8")));

                    } else {

                        GameFrame.getInstance().getCrupier().sendGAMECommandToServer("SNAPSHOT#" + Base64.encodeBase64String(this.nickname.getBytes("UTF-8")));

                    }

                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (GameFrame.getInstance().getCrupier().isGenerando_informe_anticheat()) {
                Helpers.mostrarMensajeError(GameFrame.getInstance().getFrame(), Translator.translate("Espera a que termine la solicitud que tienes en curso."));
            }

        }

    }//GEN-LAST:event_player_nameMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JLabel danger;
    private javax.swing.JLabel hands_win;
    private com.tonikelope.coronapoker.Card holeCard1;
    private com.tonikelope.coronapoker.Card holeCard2;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JLayeredPane panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JLabel player_stack;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    public boolean isIwtsthCandidate() {
        return isLoser() && isActivo() && getHoleCard1().isVisible_card() && getHoleCard1().isTapada();
    }

    public void zoomIcons() {

        Helpers.threadRun(new Runnable() {
            @Override
            public void run() {

                synchronized (zoom_lock) {

                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {

                            setAvatar();
                            utgIconZoom();
                            actionIconZoom();
                            nickChipIconZoom();
                            refreshPositionChipIcons();
                            refreshSecPotLabel();

                        }
                    });
                }
            }
        });
    }

    @Override
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        if (Helpers.float1DSecureCompare(0f, zoom_factor) < 0) {

            holeCard1.zoom(zoom_factor, mynotifier);
            holeCard2.zoom(zoom_factor, mynotifier);

            synchronized (zoom_lock) {

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {

                        if (icon_zoom_timer.isRunning()) {
                            icon_zoom_timer.stop();
                        }

                        hidePlayerActionIcon();

                        player_action.setMinimumSize(new Dimension(Math.round(RemotePlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(RemotePlayer.MIN_ACTION_HEIGHT * zoom_factor)));

                        setPlayerBorder(((LineBorder) getBorder()).getLineColor(), Math.round(Player.BORDER * zoom_factor));
                        getAvatar().setVisible(false);

                        utg_icon.setVisible(false);

                        player_name.setIcon(null);

                        chip_label.setVisible(false);
                    }
                });

                Helpers.zoomFonts(this, zoom_factor, null);

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        if (icon_zoom_timer.isRunning()) {
                            icon_zoom_timer.restart();
                        } else {
                            icon_zoom_timer.start();
                        }

                    }
                });

            }

            while (mynotifier.size() < 2) {

                synchronized (mynotifier) {

                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
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
        this.conta_win++;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(Color.GREEN, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                player_action.setBackground(Color.GREEN);
                player_action.setForeground(Color.BLACK);
                player_action.setText(msg);

                setPlayerActionIcon("action/happy.png");

                if (conta_win > 0) {

                    hands_win.setText(String.valueOf(conta_win));
                    hands_win.setVisible(true);
                }

            }
        });

    }

    public Timer getIwtsth_blink_timer() {
        return iwtsth_blink_timer;
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        var tthis = this;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(Color.RED, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                if (!holeCard1.isTapada() || !GameFrame.getInstance().getCrupier().isIWTSTH4LocalPlayerAuthorized()) {

                    player_action.setBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                    holeCard1.desenfocar();
                    holeCard2.desenfocar();

                } else {
                    player_action.setBackground(Color.WHITE);
                    player_action.setForeground(Color.RED);
                    player_action.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    holeCard1.setIwtsth_candidate(tthis);
                    holeCard2.setIwtsth_candidate(tthis);
                }

                player_action.setText(msg);
                setPlayerActionIcon("action/angry.png");

            }
        });

    }

    @Override
    public void pagar(float pasta, Integer sec_pot) {

        this.pagar += pasta;

        if (sec_pot != null) {
            botes_secundarios.add(sec_pot);

            refreshSecPotLabel();
        }

    }

    public void setPosition(int pos) {

        switch (pos) {
            case Player.DEALER:

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

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_grande());

                } else {

                    //Vamos ALLIN
                    setDecision(Player.ALLIN);

                    setBet(stack);
                }

                break;
            case Player.SMALL_BLIND:

                if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_pequeña(), stack) < 0) {
                    setBet(GameFrame.getInstance().getCrupier().getCiega_pequeña());

                } else {

                    //Vamos ALLIN
                    setDecision(Player.ALLIN);

                    setBet(stack);
                }

                break;
            default:

                setBet(0f);

                break;
        }

        refreshPositionChipIcons();

    }

    public synchronized void reComprar(int cantidad) {

        this.stack += cantidad;
        this.buyin += cantidad;

        GameFrame.getInstance().getRegistro().print(this.nickname + Translator.translate(" RECOMPRA (") + String.valueOf(cantidad) + ")");

        Audio.playWavResource("misc/cash_register.wav");

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

    public void resetGUI() {
        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                sec_pot_win_label.setVisible(false);

                setOpaque(false);

                setBackground(null);

                setPlayerBorder(new java.awt.Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                if (iwtsth_blink_timer.isRunning()) {

                    iwtsth_blink_timer.stop();
                }

                player_name.setIcon(null);

                utg_icon.setVisible(false);

                player_pot.setText("----");

                player_pot.setBackground(new Color(204, 204, 204, 75));

                player_pot.setForeground(Color.WHITE);

                player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

                if (conta_win > 0) {
                    hands_win.setText(String.valueOf(conta_win));
                    hands_win.setVisible(true);
                } else {
                    hands_win.setVisible(false);
                }

                disablePlayerAction();

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
    }

    @Override
    public void nuevaMano() {

        this.decision = Player.NODEC;

        this.notify_blocked = false;

        this.botes_secundarios.clear();

        this.winner = false;

        this.loser = false;

        this.bote = 0f;

        this.last_bote = null;

        this.bet = 0f;

        resetGUI();

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = (Integer) GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            reComprar(rebuy);

        }

        setStack(stack + pagar);

        pagar = 0f;

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

    public void refreshPositionChipIcons() {

        ImageIcon chip_label_icon;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_BB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_SB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_DEALER;
        } else {
            chip_label_icon = null;
        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                if (isActivo() && !(holeCard1.isIniciada() && !holeCard1.isTapada()) && chip_label_icon != null) {
                    chip_label.setIcon(chip_label_icon);
                    chip_label.setSize(chip_label.getIcon().getIconWidth(), chip_label.getIcon().getIconHeight());
                    chip_label.setLocation(0, 0);
                    chip_label.revalidate();
                    chip_label.repaint();
                    chip_label.setVisible(true);

                } else {

                    chip_label.setVisible(false);
                }

            }
        });

    }

    public void resetBetDecision() {
        int old_dec = this.decision;

        this.decision = Player.NODEC;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                if (old_dec != Player.BET || Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                    player_pot.setBackground(new Color(204, 204, 204, 75));
                    player_pot.setForeground(Color.WHITE);
                }

                disablePlayerAction();

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
                action += player_action.getText() + " (" + Helpers.float2String(this.bote) + ")";
                ;
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

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    setOpaque(false);
                    setBackground(null);
                    setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_pot.setText("----");
                    player_pot.setForeground(Color.white);
                    player_pot.setBackground(new Color(204, 204, 204, 75));
                    utg_icon.setVisible(false);
                    holeCard1.resetearCarta();
                    holeCard2.resetearCarta();

                    player_name.setOpaque(false);
                    player_name.setBackground(null);
                    player_name.setIcon(null);

                    chip_label.setVisible(false);

                    sec_pot_win_label.setVisible(false);

                    if (buyin > GameFrame.BUYIN) {
                        player_stack.setBackground(Color.CYAN);
                        player_stack.setForeground(Color.BLACK);
                    } else {

                        player_stack.setBackground(new Color(51, 153, 0));
                        player_stack.setForeground(Color.WHITE);
                    }

                    player_stack.setText(Helpers.float2String(stack));

                    if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                        player_name.setForeground(Color.YELLOW);
                    } else {
                        player_name.setForeground(Color.WHITE);
                    }

                    disablePlayerAction();

                }
            });

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    while (player_name.getHeight() == 0) {
                        Helpers.pausar(125);
                    }

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {
                            if (isSpectator()) {
                                player_action.setText(msg != null ? msg : Translator.translate("ESPECTADOR"));
                                setPlayerActionIcon(Helpers.float1DSecureCompare(0f, getEffectiveStack()) == 0 ? "action/ghost.png" : "action/calentando.png");
                            }

                        }
                    });

                }
            });

        }
    }

    public void disablePlayerAction() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                player_action.setText(" ");
                player_action.setForeground(Color.LIGHT_GRAY);
                player_action.setBackground(new Color(204, 204, 204, 75));
                setPlayerActionIcon(null);

            }
        });
    }

    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                player_name.setIcon(null);
                player_stack.setEnabled(true);
                disablePlayerAction();
            }
        });
    }

    private void actionIconZoom() {

        if (player_action_icon != null) {

            setPlayerActionIcon(player_action_icon);

        }
    }

    private void nickChipIconZoom() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                if (isActivo()) {
                    if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
                        Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                    } else if (nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
                        Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                    } else if (nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
                        Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));
                    } else {
                        player_name.setIcon(null);
                    }
                } else {
                    player_name.setIcon(null);
                }

                player_name.revalidate();
                player_name.repaint();
            }
        });
    }

    private void utgIconZoom() {

        ImageIcon icon = new ImageIcon(IMAGEN_UTG.getImage().getScaledInstance((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight(), Image.SCALE_SMOOTH));

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                utg_icon.setIcon(icon);

                utg_icon.setPreferredSize(new Dimension((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight()));

                utg_icon.setVisible(utg);
            }
        });
    }

    @Override
    public void showCards(String jugada) {
        this.muestra = true;
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
        this.last_bote = this.bote;
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

        if (getHoleCard1().isIniciada() && getHoleCard1().isTapada()) {

            if (sound) {
                Audio.playWavResource("misc/uncover.wav", false);
            }

            chip_label.setVisible(false);

            getHoleCard1().destapar(false);

            getHoleCard2().destapar(false);

            if (iwtsth_blink_timer.isRunning()) {

                iwtsth_blink_timer.stop();

                if (isLoser()) {
                    player_action.setBackground(Color.RED);
                    player_action.setForeground(Color.WHITE);
                    player_action.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }
    }

    @Override
    public void ordenarCartas() {
        if (getHoleCard1().getValorNumerico() != -1 && getHoleCard2().getValorNumerico() != -1 && getHoleCard1().getValorNumerico() < getHoleCard2().getValorNumerico()) {

            //Ordenamos las cartas para mayor comodidad
            String valor1 = this.holeCard1.getValor();
            String palo1 = this.holeCard1.getPalo();
            boolean desenfocada1 = this.holeCard1.isDesenfocada();

            this.holeCard1.actualizarValorPaloEnfoque(this.holeCard2.getValor(), this.holeCard2.getPalo(), this.holeCard2.isDesenfocada());
            this.holeCard2.actualizarValorPaloEnfoque(valor1, palo1, desenfocada1);
        }
    }

    @Override
    public void setSpectatorBB(boolean bb) {
        this.spectator_bb = bb;
    }

    @Override
    public void checkGameOver() {
        if (isActivo() && Helpers.float1DSecureCompare(0f, getEffectiveStack()) == 0) {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    setPlayerActionIcon("action/skull.png");
                    setOpaque(true);
                    setBackground(Color.RED);
                }
            });

        }
    }

    @Override
    public void setPlayerActionIcon(String icon) {

        if (!isTimeout() || "action/timeout.png".equals(icon) || icon == null) {

            if (!"action/timeout.png".equals(icon)) {
                player_action_icon = icon;
            }

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    player_action.setIcon(icon != null ? new ImageIcon(new ImageIcon(getClass().getResource("/images/" + icon)).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)) : null);
                    revalidate();
                    repaint();
                }
            });
        }
    }

    public void hidePlayerActionIcon() {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                player_action.setIcon(null);
            }
        });

    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                player_action.setBackground(ganador ? new Color(120, 200, 0) : new Color(230, 70, 0));
                player_action.setForeground(ganador ? Color.BLACK : Color.WHITE);
                player_action.setText(jugada.getName() + (win_per >= 0 ? " (" + win_per + "%)" : " (--%)"));
                setPlayerActionIcon(null);

            }
        });
    }

    @Override
    public void setContaWin(int conta) {
        this.conta_win = conta;

        if (this.conta_win > 0) {
            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    hands_win.setText(String.valueOf(conta_win));
                    hands_win.setVisible(true);
                }
            });
        }
    }

    @Override
    public int getContaWin() {
        return this.conta_win;
    }

}
