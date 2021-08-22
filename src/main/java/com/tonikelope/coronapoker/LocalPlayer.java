package com.tonikelope.coronapoker;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

/**
 *
 * @author tonikelope
 */
public class LocalPlayer extends JPanel implements ZoomableInterface, Player {

    public static final String[][] ACTIONS_LABELS_ES = new String[][]{new String[]{"NO VAS"}, new String[]{"PASAS", "VAS"}, new String[]{"APUESTAS", "SUBES"}, new String[]{"ALL IN"}};
    public static final String[][] ACTIONS_LABELS_EN = new String[][]{new String[]{"FOLD"}, new String[]{"CHECK", "CALL"}, new String[]{"BET", "RAISE"}, new String[]{"ALL IN"}};
    public static String[][] ACTIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? ACTIONS_LABELS_ES : ACTIONS_LABELS_EN;
    public static final String[] POSITIONS_LABELS_ES = new String[]{"CP", "CG", "DE"};
    public static final String[] POSITIONS_LABELS_EN = new String[]{"SB", "BB", "DE"};
    public static String[] POSITIONS_LABELS = GameFrame.LANGUAGE.equals("es") ? POSITIONS_LABELS_ES : POSITIONS_LABELS_EN;
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.ORANGE, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 550;
    public static final int MIN_ACTION_HEIGHT = 45;

    private final ConcurrentHashMap<JButton, Color[]> action_button_colors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JButton, Boolean> action_button_armed = new ConcurrentHashMap<>();
    private final Object pre_pulsar_lock = new Object();

    private volatile String nickname;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile float stack = 0f;
    private volatile float bet = 0f;
    private volatile boolean utg = false;
    private volatile int decision = Player.NODEC;
    private volatile boolean spectator = false;
    private volatile float pagar = 0f;
    private volatile float bote = 0f;
    private volatile boolean exit = false;
    private volatile boolean turno = false;
    private volatile Timer auto_action = null;
    private volatile boolean timeout = false;
    private volatile boolean boton_mostrar = false;
    private volatile boolean winner = false;
    private volatile boolean loser = false;
    private volatile Float apuesta_recuperada = null;
    private volatile boolean click_recuperacion = false;
    private volatile float call_required;
    private volatile float min_raise;
    private volatile int pre_pulsado = Player.NODEC;
    private volatile boolean muestra = false;
    private volatile int parguela_counter = GameFrame.PEPILLO_COUNTER_MAX;
    private volatile int pause_counter = GameFrame.PAUSE_COUNTER_MAX;
    private volatile boolean auto_pause = false;
    private volatile boolean auto_pause_warning = false;
    private volatile Timer hurryup_timer = null;
    private volatile int response_counter = 0;
    private volatile boolean spectator_bb = false;
    private volatile Color border_color = null;
    private volatile boolean player_stack_click = false;
    private volatile String player_action_emoji = null;

    public boolean isBotonMostrarActivado() {
        return getPlayer_allin_button().isEnabled() && isBoton_mostrar();
    }

    public boolean isTimeout() {
        return timeout;
    }

    public JSpinner getBet_spinner() {
        return bet_spinner;
    }

    public int getResponseTime() {

        return GameFrame.TIEMPO_PENSAR - response_counter;
    }

    public Timer getAuto_action() {
        return auto_action;
    }

    public Timer getHurryup_timer() {
        return hurryup_timer;
    }

    public boolean isAuto_pause_warning() {
        return auto_pause_warning;
    }

    public void setAuto_pause_warning(boolean auto_pause_warning) {
        this.auto_pause_warning = auto_pause_warning;
    }

    public boolean isAuto_pause() {
        return auto_pause;
    }

    public void setAuto_pause(boolean auto_pause) {
        this.auto_pause = auto_pause;
    }

    public int getPause_counter() {
        return pause_counter;
    }

    public void setPause_counter(int pause_counter) {
        this.pause_counter = pause_counter;
    }

    public boolean isTurno() {
        return turno;
    }

    public int getParguela_counter() {
        return parguela_counter;
    }

    public void updateParguela_counter() {
        this.parguela_counter--;
    }

    public void setClick_recuperacion(boolean click_recuperacion) {
        this.click_recuperacion = click_recuperacion;
    }

    public void setApuesta_recuperada(Float apuesta_recuperada) {
        this.apuesta_recuperada = apuesta_recuperada;
    }

    public JButton getPlayer_allin_button() {
        return player_allin_button;
    }

    public JButton getPlayer_check_button() {
        return player_check_button;
    }

    public JButton getPlayer_fold_button() {
        return player_fold_button;
    }

    public void setMuestra(boolean muestra) {
        this.muestra = muestra;
    }

    public boolean isMuestra() {
        return muestra;
    }

    public boolean isWinner() {
        return winner;
    }

    public boolean isLoser() {
        return loser;
    }

    public void activar_boton_mostrar(boolean parguela) {

        boton_mostrar = true;

        desactivarControles();

        Helpers.GUIRun(new Runnable() {
            public void run() {
                if (parguela) {
                    player_allin_button.setText(Translator.translate("MOSTRAR") + " (" + parguela_counter + ")");
                } else {
                    player_allin_button.setText(Translator.translate("MOSTRAR"));

                }
                player_allin_button.setForeground(Color.WHITE);
                player_allin_button.setBackground(new Color(51, 153, 255));
                player_allin_button.setEnabled(true);

                if (GameFrame.TEST_MODE) {
                    player_allin_button.doClick();
                }
            }
        });

    }

    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            desactivarControles();

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_blind.setVisible(false);
                    player_pot.setText("----");
                    player_pot.setBackground(null);
                    player_pot.setEnabled(false);
                    utg_icon.setVisible(false);
                    playingCard1.resetearCarta();
                    playingCard2.resetearCarta();
                    player_action.setEnabled(true);
                    player_action.setText(msg != null ? msg : Translator.translate("ESPECTADOR"));
                    player_action.setBackground(null);
                    player_action.setIcon(null);
                    player_action.setForeground(Color.GRAY);
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

                    setAuto_pause(false);
                    GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setBackground(new Color(255, 102, 0));
                    GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setForeground(Color.WHITE);

                    if (!GameFrame.getInstance().isPartida_local()) {
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(false);
                    }

                }
            });

            Helpers.threadRun(new Runnable() {
                @Override
                public void run() {
                    while (player_action.getHeight() == 0) {
                        Helpers.pausar(125);
                    }

                    Helpers.GUIRun(new Runnable() {
                        @Override
                        public void run() {

                            player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/ghost.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                            player_action_emoji = "ghost";

                        }
                    });
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
                player_action.setIcon(null);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(true);
            }
        });
    }

    public void desactivar_boton_mostrar() {

        if (boton_mostrar) {
            boton_mostrar = false;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    player_allin_button.setText(" ");
                    player_allin_button.setEnabled(false);
                    player_allin_button.setBackground(Color.BLACK);
                    player_allin_button.setForeground(Color.WHITE);
                }
            });
        }
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    public void setTimeout(boolean val) {

        if (this.timeout != val) {

            this.timeout = val;

            Helpers.GUIRun(new Runnable() {
                public void run() {
                    timeout_label.setVisible(val);

                    if (val) {

                        setPlayerBorder(Color.MAGENTA, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                    } else {
                        setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    }

                }
            });

            if (val) {

                Helpers.playWavResource("misc/network_error.wav");
            }
        }

    }

    private void setPlayerBorder(Color color, int size) {

        if (!timeout) {
            border_color = color;
        }

        setBorder(javax.swing.BorderFactory.createLineBorder(color, size));
    }

    public JLabel getAvatar() {
        return avatar;
    }

    public synchronized float getPagar() {
        return pagar;
    }

    public synchronized float getBote() {
        return bote;
    }

    public synchronized boolean isExit() {
        return exit;
    }

    public synchronized void setExit() {

        if (!this.exit) {
            this.exit = true;

            desactivarControles();

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {
                    setPlayerBorder(new Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    playingCard1.resetearCarta();
                    playingCard2.resetearCarta();
                    player_action.setBackground(new Color(255, 102, 0));
                    player_action.setForeground(Color.WHITE);
                    player_action.setText(Translator.translate("ABANDONAS LA TIMBA"));
                    player_action.setVisible(true);
                    player_action.setEnabled(true);

                }
            });
        }
    }

    @Override
    public boolean isSpectator() {
        return this.spectator;
    }

    public int getDecision() {
        return decision;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public void setNickname(String nickname) {
        this.nickname = nickname;

        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_name.setText(nickname);

                if (!GameFrame.getInstance().isPartida_local()) {

                    player_name.setToolTipText("CLICK -> AES-KEY");
                    player_name.setCursor(new Cursor(Cursor.HAND_CURSOR));

                } else {
                    player_name.setForeground(Color.YELLOW);
                }
            }
        });
    }

    public synchronized float getStack() {
        return stack;
    }

    public synchronized void setStack(float stack) {
        this.stack = Helpers.floatClean1D(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    if (getNickname() != null && GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                        player_stack.setBackground(Color.YELLOW);
                        player_stack.setForeground(Color.BLACK);
                        player_stack.setText(Helpers.float2String(stack + (int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname())));

                    } else {

                        if (buyin > GameFrame.BUYIN) {
                            player_stack.setBackground(Color.CYAN);

                            player_stack.setForeground(Color.BLACK);
                        } else {

                            player_stack.setBackground(new Color(51, 153, 0));

                            player_stack.setForeground(Color.WHITE);
                        }

                        player_stack.setText(Helpers.float2String(stack));
                    }
                }
            });
        }
    }

    public int getBuyin() {
        return buyin;
    }

    public float getBet() {
        return bet;
    }

    public void player_stack_click() {

        player_stackMouseClicked(null);
    }

    /**
     * Creates new form JugadorLocalView
     */
    public LocalPlayer() {

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {
                initComponents();

                border_color = ((LineBorder) getBorder()).getLineColor();

                action_button_armed.put(player_check_button, false);

                action_button_armed.put(player_bet_button, false);

                action_button_armed.put(player_allin_button, false);

                action_button_armed.put(player_fold_button, false);

                timeout_label.setVisible(false);

                player_check_button.setEnabled(false);

                bet_spinner.setValue(new BigDecimal(0));

                bet_spinner.setEnabled(false);

                player_bet_button.setEnabled(false);

                player_allin_button.setEnabled(false);

                player_fold_button.setEnabled(false);

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setIcon(null);

                player_action_emoji = null;

                player_action.setEnabled(false);

                player_blind.setVisible(false);

                utg_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/utg.png")).getImage().getScaledInstance(41, 31, Image.SCALE_SMOOTH)));

                utg_icon.setVisible(false);

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);

                player_pot.setText("----");

                playingCard1.setPosChip_visible(GameFrame.LOCAL_POSITION_CHIP);

                desactivarControles();
            }
        });
    }

    public JButton getPlayer_allin() {
        return player_allin_button;
    }

    public JButton getPlayer_bet_button() {
        return player_bet_button;
    }

    public JButton getPlayer_check() {
        return player_check_button;
    }

    public JButton getPlayer_fold() {
        return player_fold_button;
    }

    public Card getPlayingCard1() {
        return playingCard1;
    }

    public Card getPlayingCard2() {
        return playingCard2;
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

    public JLabel getPlayer_stack() {
        return player_stack;
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

    private void guardarColoresBotonesAccion() {
        action_button_colors.clear();

        action_button_colors.put(player_check_button, new Color[]{player_check_button.getBackground(), player_check_button.getForeground()});

        action_button_colors.put(player_bet_button, new Color[]{player_bet_button.getBackground(), player_bet_button.getForeground()});

        action_button_colors.put(player_allin_button, new Color[]{player_allin_button.getBackground(), player_allin_button.getForeground()});

        action_button_colors.put(player_fold_button, new Color[]{player_fold_button.getBackground(), player_fold_button.getForeground()});

    }

    public void esTuTurno() {

        turno = true;

        GameFrame.getInstance().getCrupier().disableAllPlayersTimeout();

        if (this.getDecision() == Player.NODEC) {
            Helpers.playWavResource("misc/yourturn.wav");

            call_required = Helpers.floatClean1D(GameFrame.getInstance().getCrupier().getApuesta_actual() - bet);

            min_raise = Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getUltimo_raise()) < 0 ? GameFrame.getInstance().getCrupier().getUltimo_raise() : Helpers.floatClean1D(GameFrame.getInstance().getCrupier().getCiega_grande());

            desarmarBotonesAccion();

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    setPlayerBorder(Color.ORANGE, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_check_button.setEnabled(false);

                    player_check_button.setText(" ");

                    bet_spinner.setValue(new BigDecimal(0));

                    bet_spinner.setEnabled(false);

                    player_bet_button.setEnabled(false);

                    player_bet_button.setText(" ");

                    player_allin_button.setText("ALL IN");

                    player_allin_button.setEnabled(true);
                    
                    player_allin_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/glasses.png")).getImage().getScaledInstance(Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()), Image.SCALE_SMOOTH)));

                    player_fold_button.setText("NO IR");

                    player_fold_button.setEnabled(true);

                    player_fold_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/down.png")).getImage().getScaledInstance(Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()), Image.SCALE_SMOOTH)));

                    player_action.setBackground(Color.WHITE);

                    player_action.setForeground(Color.BLACK);

                    player_action.setEnabled(true);

                    player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/thinking.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));

                    player_action_emoji = "thinking";

                    player_action.setText("HABLAS TÚ");

                    player_pot.setBackground(Color.WHITE);

                    player_pot.setForeground(Color.BLACK);

                    //Comprobamos si podemos ver la apuesta actual
                    if (Helpers.float1DSecureCompare(call_required, stack) < 0) {

                        player_check_button.setEnabled(true);

                        player_check_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/up.png")).getImage().getScaledInstance(Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()), Image.SCALE_SMOOTH)));

                        if (Helpers.float1DSecureCompare(0f, call_required) == 0) {
                            player_check_button.setText("PASAR");
                            player_check_button.setBackground(new Color(0, 130, 0));
                            player_check_button.setForeground(Color.WHITE);
                            player_fold_button.setBackground(Color.RED);
                            player_fold_button.setForeground(Color.WHITE);
                        } else {
                            player_check_button.setText(Translator.translate("IR") + " (+" + Helpers.float2String(call_required) + ")");
                            player_check_button.setBackground(null);
                            player_check_button.setForeground(null);
                            player_fold_button.setBackground(Color.DARK_GRAY);
                            player_fold_button.setForeground(Color.WHITE);
                        }

                    }

                    guardarColoresBotonesAccion();

                    if ((GameFrame.getInstance().getCrupier().getLast_aggressor() == null || !nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())) && GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) > 1 && ((Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0)
                            || (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0 && Helpers.float1DSecureCompare(call_required + min_raise, stack) < 0))) {

                        //Actualizamos el spinner y el botón de apuestas
                        BigDecimal spinner_min;
                        BigDecimal spinner_max = new BigDecimal(stack - call_required).setScale(1, RoundingMode.HALF_UP);

                        player_bet_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/bet.png")).getImage().getScaledInstance(Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()), Image.SCALE_SMOOTH)));

                        if (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                            spinner_min = new BigDecimal(GameFrame.getInstance().getCrupier().getCiega_grande()).setScale(1, RoundingMode.HALF_UP);
                            player_bet_button.setEnabled(true);
                            player_bet_button.setText(Translator.translate("APOSTAR"));

                        } else {
                            spinner_min = new BigDecimal(min_raise).setScale(1, RoundingMode.HALF_UP);
                            player_bet_button.setEnabled(true);
                            player_bet_button.setText(Translator.translate((GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "RE" : "") + "SUBIR"));
                        }

                        if (spinner_min.compareTo(spinner_max) < 0) {

                            SpinnerNumberModel nummodel = new SpinnerNumberModel(spinner_min, spinner_min, spinner_max, new BigDecimal(GameFrame.CIEGA_PEQUEÑA).setScale(1, RoundingMode.HALF_UP)) {
                                public Object getNextValue() {
                                    BigDecimal current = (BigDecimal) super.getValue();

                                    current = current.add((BigDecimal) super.getStepSize());

                                    if (current.compareTo((BigDecimal) super.getMaximum()) <= 0) {
                                        return current;
                                    } else {
                                        return null;
                                    }

                                }

                                public Object getPreviousValue() {
                                    BigDecimal current = (BigDecimal) super.getValue();

                                    current = current.subtract((BigDecimal) super.getStepSize());

                                    if (((BigDecimal) super.getMinimum()).compareTo(current) <= 0) {
                                        return current;
                                    } else {
                                        return null;
                                    }

                                }

                            };
                            bet_spinner.setModel(nummodel);
                            bet_spinner.setEnabled(true);

                            ((JSpinner.DefaultEditor) bet_spinner.getEditor()).getTextField().setEditable(false);
                            ((JSpinner.DefaultEditor) bet_spinner.getEditor()).getTextField().setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

                        } else {
                            player_bet_button.setEnabled(false);
                            player_bet_button.setText("");
                            bet_spinner.setValue(new BigDecimal(0));
                            bet_spinner.setEnabled(false);
                        }

                    }

                    if ((GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) == 1 || ((GameFrame.getInstance().getCrupier().getLast_aggressor() != null && nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())))) && Helpers.float1DSecureCompare(call_required, stack) < 0) {
                        player_allin_button.setText(" ");
                        player_allin_button.setEnabled(false);
                        player_allin_button.setIcon(null);
                    }

                    GameFrame.getInstance().getBarra_tiempo().setMaximum(GameFrame.TIEMPO_PENSAR);

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    Helpers.translateComponents(botonera, false);

                    Helpers.translateComponents(player_action, false);
                }
            });

            if (auto_pause) {
                GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
            }

            if (GameFrame.TEST_MODE) {

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        Helpers.pausar(GameFrame.TEST_MODE_PAUSE);

                        ArrayList<JButton> botones = new ArrayList<>(Arrays.asList(new JButton[]{player_check_button, player_bet_button, player_allin_button, player_fold_button}));

                        Iterator<JButton> iterator = botones.iterator();

                        Helpers.GUIRun(new Runnable() {
                            public void run() {

                                while (iterator.hasNext()) {
                                    JButton boton = iterator.next();

                                    if (!boton.isEnabled()) {
                                        iterator.remove();
                                    }
                                }

                                int eleccion = Helpers.CSPRNG_GENERATOR.nextInt(botones.size());

                                botones.get(eleccion).doClick();
                            }
                        });

                    }
                });
            } else {

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

                                if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isPlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && response_counter > 0 && auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {

                                    response_counter--;

                                    GameFrame.getInstance().getBarra_tiempo().setValue(response_counter);

                                    if (response_counter == 10) {
                                        Helpers.playWavResource("misc/hurryup.wav");

                                        if ((hurryup_timer == null || !hurryup_timer.isRunning()) && Helpers.float1DSecureCompare(0f, call_required) < 0) {

                                            if (hurryup_timer != null) {
                                                hurryup_timer.stop();
                                            }

                                            Color orig_color = border_color;

                                            hurryup_timer = new Timer(1000, new ActionListener() {

                                                @Override
                                                public void actionPerformed(ActionEvent ae) {

                                                    if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().isTimba_pausada() && hurryup_timer.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {
                                                        if (player_action.getBackground() == Color.WHITE) {
                                                            setPlayerBorder(Color.GRAY, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                                                            player_action.setBackground(Color.GRAY);
                                                            player_action.setForeground(Color.WHITE);
                                                        } else {
                                                            setPlayerBorder(orig_color, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                                                            player_action.setBackground(Color.WHITE);
                                                            player_action.setForeground(Color.BLACK);
                                                        }
                                                    }
                                                }
                                            });

                                            hurryup_timer.start();
                                        }
                                    }

                                    if (response_counter == 0 || GameFrame.getInstance().getCrupier().getJugadoresActivos() < 2) {

                                        Helpers.threadRun(new Runnable() {
                                            public void run() {

                                                if (response_counter == 0) {
                                                    Helpers.playWavResourceAndWait("misc/timeout.wav"); //Mientras dura la bocina aún estaríamos a tiempo de elegir
                                                }

                                                if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno() && getDecision() == Player.NODEC) {

                                                    GameFrame.getInstance().checkPause();

                                                    if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno() && getDecision() == Player.NODEC) {

                                                        if (Helpers.float1DSecureCompare(0f, call_required) == 0) {

                                                            Helpers.GUIRun(new Runnable() {
                                                                public void run() {
                                                                    //Pasamos automáticamente 
                                                                    action_button_armed.put(player_check_button, true);
                                                                    player_check_button.doClick();
                                                                }
                                                            });

                                                        } else {

                                                            Helpers.GUIRun(new Runnable() {
                                                                public void run() {

                                                                    //Nos tiramos automáticamente
                                                                    action_button_armed.put(player_fold_button, true);
                                                                    player_fold_button.doClick();
                                                                }
                                                            });
                                                        }

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

            if (GameFrame.AUTO_ACTION_BUTTONS && pre_pulsado != Player.NODEC) {
                Helpers.GUIRun(new Runnable() {
                    public void run() {

                        if (pre_pulsado == Player.FOLD) {

                            if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                                player_fold_button.doClick();
                            } else {
                                player_check_button.doClick();
                            }

                        } else if (pre_pulsado == Player.CHECK && (Helpers.float1DSecureCompare(0f, call_required) == 0 || (GameFrame.getInstance().getCrupier().getFase() == Crupier.PREFLOP && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0))) {

                            player_check_button.doClick();

                        } else {
                            desPrePulsarTodo();
                        }
                    }

                });
            }

        } else {

            finTurno();
        }

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

                if (GameFrame.AUTO_ACTION_BUTTONS && getDecision() != Player.ALLIN && getDecision() != Player.FOLD) {
                    activarPreBotones();
                }
            }
        });
    }

    public void desactivarControles() {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                bet_spinner.setValue(new BigDecimal(0));

                bet_spinner.setEnabled(false);

                for (Component c : botonera.getComponents()) {

                    if (c instanceof JButton) {
                        ((JButton) c).setText(" ");
                        c.setEnabled(false);
                        ((JButton) c).setIcon(null);
                    }
                }
            }
        });

        desarmarBotonesAccion();
    }

    public void desPrePulsarTodo() {

        if (pre_pulsado != Player.NODEC) {

            desPrePulsarBoton(player_check_button);
            desPrePulsarBoton(player_fold_button);
        }
    }

    public void desPrePulsarBoton(JButton boton) {
        pre_pulsado = Player.NODEC;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                Color[] colores;

                if (boton == player_check_button) {
                    colores = new Color[]{null, null};
                } else if (boton == player_fold_button) {
                    colores = new Color[]{Color.RED, Color.WHITE};
                } else {
                    colores = action_button_colors.get(boton);
                }

                boton.setBackground(colores[0]);

                boton.setForeground(colores[1]);

            }
        });

    }

    public void prePulsarBoton(JButton boton, int dec) {
        pre_pulsado = dec;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                boton.setBackground(Color.YELLOW);
                boton.setForeground(Color.BLACK);

            }
        });

    }

    public void desarmarBotonesAccion() {
        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                    JButton b = entry.getKey();

                    if (action_button_armed.get(b)) {

                        Color[] colores = entry.getValue();

                        action_button_armed.put(b, false);

                        b.setBackground(colores[0]);
                        b.setForeground(colores[1]);

                    }

                }
            }
        });
    }

    public void armarBoton(JButton boton) {

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                    JButton b = entry.getKey();

                    Color[] colores = entry.getValue();

                    if (b == boton) {
                        action_button_armed.put(b, true);

                        b.setBackground(new Color(120, 0, 184));
                        b.setForeground(Color.WHITE);

                    } else {
                        action_button_armed.put(b, false);

                        b.setBackground(colores[0]);
                        b.setForeground(colores[1]);

                    }
                }

            }
        });

    }

    public void resetBetDecision() {
        this.decision = Player.NODEC;

        Helpers.GUIRun(new Runnable() {
            public void run() {

                player_action.setText(" ");

                player_action.setBackground(null);
                player_action.setEnabled(false);
                player_action.setIcon(null);

                player_action_emoji = null;
            }
        });
    }

    public void activarPreBotones() {

        if (!turno && decision != Player.FOLD && decision != Player.ALLIN && !GameFrame.getInstance().getCrupier().isShow_time()) {

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    player_check_button.setBackground(null);
                    player_check_button.setForeground(null);
                    player_check_button.setText(Translator.translate("[A] PASAR +CG"));
                    player_check_button.setEnabled(true);
                    player_check_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/up.png")).getImage().getScaledInstance(Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()), Image.SCALE_SMOOTH)));

                    player_fold_button.setBackground(Color.RED);
                    player_fold_button.setForeground(Color.WHITE);
                    player_fold_button.setText(Translator.translate("[A] NO IR"));
                    player_fold_button.setEnabled(true);
                    player_fold_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/down.png")).getImage().getScaledInstance(Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()), Image.SCALE_SMOOTH)));

                    if (pre_pulsado != Player.NODEC) {

                        if (pre_pulsado == Player.CHECK) {
                            prePulsarBoton(player_check_button, Player.CHECK);
                        } else if (pre_pulsado == Player.FOLD) {
                            prePulsarBoton(player_fold_button, Player.FOLD);
                        }
                    }

                }
            });
        }

    }

    public void desActivarPreBotones() {

        if (!turno) {

            this.desPrePulsarTodo();

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    player_check_button.setText(" ");
                    player_check_button.setEnabled(false);
                    player_check_button.setIcon(null);

                    player_fold_button.setText(" ");
                    player_fold_button.setEnabled(false);
                    player_fold_button.setIcon(null);
                }
            });
        }
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

    @Override
    public void nuevaMano() {

        desPrePulsarTodo();

        this.decision = Player.NODEC;

        this.muestra = false;

        this.winner = false;

        this.loser = false;

        this.bote = 0f;

        this.bet = 0f;

        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(nickname)) {

            int rebuy = (Integer) GameFrame.getInstance().getCrupier().getRebuy_now().get(nickname);

            GameFrame.getInstance().getCrupier().getRebuy_now().remove(nickname);

            reComprar(rebuy);

        }

        setStack(stack + pagar);

        pagar = 0f;

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                setPlayerBorder(new java.awt.Color(204, 204, 204), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                desactivar_boton_mostrar();

                desactivarControles();

                player_action.setText(" ");

                player_action.setBackground(null);

                player_action.setEnabled(false);

                player_action.setIcon(null);

                player_action_emoji = null;

                utg_icon.setVisible(false);

                player_blind.setVisible(false);

                player_pot.setText("----");

                player_pot.setBackground(Color.WHITE);

                player_pot.setForeground(Color.BLACK);

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

    public synchronized float getEffectiveStack() {

        return this.stack + this.bote + this.pagar;

    }

    public boolean isBoton_mostrar() {
        return boton_mostrar;
    }

    @Override
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

    private void emojiZoom() {

        if (player_action_emoji != null) {

            ImageIcon icon = new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/" + player_action_emoji + ".png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH));

            Helpers.GUIRun(new Runnable() {
                @Override
                public void run() {

                    player_action.setIcon(icon);

                }
            });

        }

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                if (player_check_button.isEnabled()) {
                    player_check_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/up.png")).getImage().getScaledInstance(Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()), Image.SCALE_SMOOTH)));
                }
                if (player_bet_button.isEnabled()) {
                    player_bet_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/bet.png")).getImage().getScaledInstance(Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()), Image.SCALE_SMOOTH)));
                }

                if (player_allin_button.isEnabled()) {
                    player_allin_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/glasses.png")).getImage().getScaledInstance(Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()), Image.SCALE_SMOOTH)));
                }

                if (player_fold_button.isEnabled()) {

                    player_fold_button.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/down.png")).getImage().getScaledInstance(Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()), Image.SCALE_SMOOTH)));
                }
            }
        });

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
    public void zoom(float zoom_factor, final ConcurrentLinkedQueue<Long> notifier) {

        final ConcurrentLinkedQueue<Long> mynotifier = new ConcurrentLinkedQueue<>();

        if (Helpers.float1DSecureCompare(0f, zoom_factor) < 0) {

            Helpers.GUIRunAndWait(new Runnable() {
                @Override
                public void run() {
                    player_action.setIcon(null);
                    player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * zoom_factor)));
                    setPlayerBorder(((LineBorder) getBorder()).getLineColor(), Math.round(Player.BORDER * zoom_factor));
                    getAvatar().setVisible(false);
                    utg_icon.setVisible(false);

                    player_check_button.setIcon(null);

                    player_bet_button.setIcon(null);

                    player_allin_button.setIcon(null);

                    player_fold_button.setIcon(null);

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
                        player_blind.setBackground(new Color(230, 229, 235));
                        player_blind.setForeground(Color.BLACK);
                        player_blind.setText(POSITIONS_LABELS[2]);
                        player_name.setOpaque(true);
                        player_name.setBackground(player_blind.getBackground());
                        player_name.setForeground(player_blind.getForeground());

                    }
                });

                this.getPlayingCard1().setPosChip(Player.DEALER, 2);

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
                        player_blind.setVisible(true);
                        player_blind.setBackground(new Color(241, 185, 30));
                        player_blind.setForeground(Color.black);
                        player_blind.setText(POSITIONS_LABELS[1]);
                        player_name.setOpaque(true);
                        player_name.setBackground(player_blind.getBackground());
                        player_name.setForeground(player_blind.getForeground());
                    }
                });

                this.getPlayingCard1().setPosChip(Player.BIG_BLIND, 2);

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
                        player_blind.setVisible(true);
                        player_blind.setBackground(new Color(24, 52, 178));
                        player_blind.setForeground(Color.white);
                        player_blind.setText(POSITIONS_LABELS[0]);
                        player_name.setOpaque(true);
                        player_name.setBackground(player_blind.getBackground());
                        player_name.setForeground(player_blind.getForeground());
                    }
                });

                this.getPlayingCard1().setPosChip(Player.SMALL_BLIND, 2);

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
                        player_blind.setVisible(false);
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel_cartas = new javax.swing.JPanel();
        playingCard2 = new com.tonikelope.coronapoker.Card();
        playingCard1 = new com.tonikelope.coronapoker.Card();
        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        timeout_label = new javax.swing.JLabel();
        player_pot = new javax.swing.JLabel();
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        player_blind = new javax.swing.JLabel();
        botonera = new javax.swing.JPanel();
        player_allin_button = new javax.swing.JButton();
        player_fold_button = new javax.swing.JButton();
        player_check_button = new javax.swing.JButton();
        player_bet_button = new javax.swing.JButton();
        bet_spinner = new javax.swing.JSpinner();
        player_action = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

        panel_cartas.setFocusable(false);
        panel_cartas.setOpaque(false);

        playingCard2.setFocusable(false);

        playingCard1.setFocusable(false);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(playingCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(playingCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
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

        timeout_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/timeout.png"))); // NOI18N
        timeout_label.setDoubleBuffered(true);
        timeout_label.setFocusable(false);

        player_pot.setBackground(new java.awt.Color(255, 255, 255));
        player_pot.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        player_pot.setText("----");
        player_pot.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
                .addComponent(timeout_label)
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
                    .addComponent(timeout_label))
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

        player_blind.setBackground(new java.awt.Color(51, 51, 255));
        player_blind.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_blind.setForeground(new java.awt.Color(255, 255, 255));
        player_blind.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_blind.setText("CP");
        player_blind.setToolTipText("Click para mostrar/ocultar la ficha");
        player_blind.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_blind.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_blind.setDoubleBuffered(true);
        player_blind.setFocusable(false);
        player_blind.setOpaque(true);
        player_blind.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                player_blindMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout nick_panelLayout = new javax.swing.GroupLayout(nick_panel);
        nick_panel.setLayout(nick_panelLayout);
        nick_panelLayout.setHorizontalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addComponent(player_name)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(utg_icon)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_blind))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(player_name)
                    .addComponent(utg_icon)
                    .addComponent(player_blind))
                .addGap(0, 0, 0))
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
                .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        botonera.setFocusable(false);
        botonera.setOpaque(false);

        player_allin_button.setBackground(new java.awt.Color(0, 0, 0));
        player_allin_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_allin_button.setForeground(new java.awt.Color(255, 255, 255));
        player_allin_button.setText("ALL IN");
        player_allin_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_allin_button.setDoubleBuffered(true);
        player_allin_button.setFocusable(false);
        player_allin_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_allin_buttonActionPerformed(evt);
            }
        });

        player_fold_button.setBackground(new java.awt.Color(255, 0, 0));
        player_fold_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_fold_button.setForeground(new java.awt.Color(255, 255, 255));
        player_fold_button.setText("NO IR");
        player_fold_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_fold_button.setDoubleBuffered(true);
        player_fold_button.setFocusable(false);
        player_fold_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_fold_buttonActionPerformed(evt);
            }
        });

        player_check_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_check_button.setText("PASAR");
        player_check_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_check_button.setDoubleBuffered(true);
        player_check_button.setFocusable(false);
        player_check_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_check_buttonActionPerformed(evt);
            }
        });

        player_bet_button.setBackground(java.awt.Color.orange);
        player_bet_button.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        player_bet_button.setText("APOSTAR");
        player_bet_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        player_bet_button.setDoubleBuffered(true);
        player_bet_button.setFocusable(false);
        player_bet_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player_bet_buttonActionPerformed(evt);
            }
        });

        bet_spinner.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        bet_spinner.setModel(new javax.swing.SpinnerNumberModel());
        bet_spinner.setBorder(null);
        bet_spinner.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        bet_spinner.setDoubleBuffered(true);

        javax.swing.GroupLayout botoneraLayout = new javax.swing.GroupLayout(botonera);
        botonera.setLayout(botoneraLayout);
        botoneraLayout.setHorizontalGroup(
            botoneraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(player_bet_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_allin_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_check_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_fold_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(bet_spinner, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        botoneraLayout.setVerticalGroup(
            botoneraLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(botoneraLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_check_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bet_spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(player_bet_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_allin_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_fold_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP)), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        player_action.setOpaque(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(panel_cartas, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(indicadores_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(indicadores_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(panel_cartas, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_fold_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_fold_buttonActionPerformed
        // TODO add your handling code here:

        if (!turno) {

            synchronized (pre_pulsar_lock) {

                if (pre_pulsado == Player.FOLD) {

                    Helpers.playWavResource("misc/button_off.wav");

                    this.desPrePulsarBoton(player_fold_button);

                } else {
                    Helpers.playWavResource("misc/button_on.wav");

                    this.desPrePulsarTodo();

                    this.prePulsarBoton(player_fold_button, Player.FOLD);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_fold_button.isEnabled()) {

            if (pre_pulsado == Player.FOLD || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_fold_button) || click_recuperacion) {

                Helpers.playWavResource("misc/fold.wav");

                desactivarControles();

                GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                if (auto_action != null) {
                    auto_action.stop();
                }

                if (hurryup_timer != null) {
                    hurryup_timer.stop();
                }

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        GameFrame.getInstance().getCrupier().soundFold();

                        setDecision(Player.FOLD);

                        playingCard1.desenfocar();
                        playingCard2.desenfocar();

                        finTurno();
                    }
                });

            } else {

                this.armarBoton(player_fold_button);
            }

        }

    }//GEN-LAST:event_player_fold_buttonActionPerformed

    private void player_allin_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_allin_buttonActionPerformed
        // TODO add your handling code here:

        if (!GameFrame.getInstance().isTimba_pausada() || boton_mostrar) {

            if (player_allin_button.isEnabled()) {

                if (boton_mostrar && GameFrame.getInstance().getCrupier().isShow_time()) {

                    this.muestra = true;

                    if (decision == Player.FOLD) {
                        updateParguela_counter();
                    }

                    desactivar_boton_mostrar();

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            synchronized (GameFrame.getInstance().getCrupier().getLock_mostrar()) {
                                if (GameFrame.getInstance().getCrupier().isShow_time()) {

                                    Helpers.threadRun(new Runnable() {
                                        @Override
                                        public void run() {

                                            if (GameFrame.getInstance().isPartida_local()) {
                                                GameFrame.getInstance().getCrupier().showAndBroadcastPlayerCards(nickname);
                                            } else {
                                                GameFrame.getInstance().getCrupier().sendGAMECommandToServer("SHOWMYCARDS");
                                                GameFrame.getInstance().getCrupier().setTiempo_pausa(GameFrame.PAUSA_ENTRE_MANOS);
                                            }

                                        }
                                    });

                                    ArrayList<Card> cartas = new ArrayList<>();

                                    cartas.add(getPlayingCard1());
                                    cartas.add(getPlayingCard2());

                                    String lascartas = Card.collection2String(cartas);

                                    for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                                        if (!carta_comun.isTapada()) {
                                            cartas.add(carta_comun);
                                        }
                                    }

                                    Hand jugada = new Hand(cartas);

                                    player_action.setForeground(Color.WHITE);

                                    player_action.setBackground(new Color(51, 153, 255));

                                    player_action.setText(Translator.translate(" MUESTRAS (") + jugada.getName() + ")");

                                    if (GameFrame.SONIDOS_CHORRA && decision == Player.FOLD) {

                                        Helpers.playWavResource("misc/showyourcards.wav");

                                    }

                                    if (!GameFrame.getInstance().getCrupier().getPerdedores().containsKey(GameFrame.getInstance().getLocalPlayer())) {
                                        GameFrame.getInstance().getRegistro().print(nickname + Translator.translate(" MUESTRA (") + lascartas + ") -> " + jugada);
                                    }

                                    Helpers.translateComponents(botonera, false);

                                    Helpers.translateComponents(player_action, false);

                                }

                            }
                        }
                    });

                } else if (getDecision() == Player.NODEC) {

                    if (GameFrame.TEST_MODE || this.action_button_armed.get(player_allin_button) || click_recuperacion) {

                        Helpers.playWavResource("misc/allin.wav");

                        desactivarControles();

                        GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        if (hurryup_timer != null) {
                            hurryup_timer.stop();
                        }

                        GameFrame.getInstance().getCrupier().setPlaying_cinematic(true);

                        Helpers.threadRun(new Runnable() {

                            public void run() {

                                if (!GameFrame.getInstance().getCrupier().localCinematicAllin()) {
                                    GameFrame.getInstance().getCrupier().soundAllin();
                                }
                            }
                        });

                        Helpers.threadRun(new Runnable() {
                            public void run() {

                                setDecision(Player.ALLIN);

                                setBet(stack + bet);

                                finTurno();
                            }
                        });
                    } else {

                        this.armarBoton(player_allin_button);
                    }

                }

            }
        }

    }//GEN-LAST:event_player_allin_buttonActionPerformed

    private void player_check_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_check_buttonActionPerformed
        // TODO add your handling code here:
        if (!turno) {

            synchronized (pre_pulsar_lock) {

                if (pre_pulsado == Player.CHECK) {

                    Helpers.playWavResource("misc/button_off.wav");

                    this.desPrePulsarBoton(player_check_button);

                } else {

                    Helpers.playWavResource("misc/button_on.wav");

                    this.desPrePulsarTodo();

                    this.prePulsarBoton(player_check_button, Player.CHECK);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_check_button.isEnabled()) {

            if (pre_pulsado == Player.CHECK || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_check_button) || click_recuperacion) {

                if (Helpers.float1DSecureCompare(this.stack - (GameFrame.getInstance().getCrupier().getApuesta_actual() - this.bet), 0f) == 0) {
                    player_allin_buttonActionPerformed(null);
                } else {

                    Helpers.playWavResource("misc/check.wav");

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

                            setDecision(Player.CHECK);

                            finTurno();
                        }
                    });
                }
            } else {

                this.armarBoton(player_check_button);
            }
        }

    }//GEN-LAST:event_player_check_buttonActionPerformed

    private void player_bet_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_bet_buttonActionPerformed
        // TODO add your handling code here:

        if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_bet_button.isEnabled()) {

            if (Helpers.float1DSecureCompare(stack, (((BigDecimal) bet_spinner.getValue()).floatValue()) + call_required) == 0) {

                player_allin_buttonActionPerformed(null);

            } else {

                if (!GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_bet_button) || click_recuperacion) {

                    float bet_spinner_val = Helpers.floatClean1D(((BigDecimal) bet_spinner.getValue()).floatValue());

                    Helpers.playWavResource("misc/bet.wav");

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            if (apuesta_recuperada == null) {

                                setBet(bet_spinner_val + bet + call_required);
                            } else {

                                setBet(apuesta_recuperada);

                                apuesta_recuperada = null;
                            }

                            setDecision(Player.BET);

                            if (!GameFrame.getInstance().getCrupier().isSincronizando_mano() && GameFrame.SONIDOS_CHORRA && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {
                                Helpers.playWavResource("misc/raise.wav");
                            }

                            finTurno();

                        }
                    });
                } else {
                    this.armarBoton(player_bet_button);
                }
            }

        }

    }//GEN-LAST:event_player_bet_buttonActionPerformed

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (!GameFrame.getInstance().isPartida_local()) {

            IdenticonDialog identicon = new IdenticonDialog(GameFrame.getInstance().getFrame(), true, player_name.getText(), GameFrame.getInstance().getSala_espera().getLocal_client_aes_key());

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

                            } else if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                                player_stack.setBackground(Color.YELLOW);
                                player_stack.setForeground(Color.BLACK);
                                player_stack.setText(Helpers.float2String(stack + (int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname())));

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

    private void player_blindMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_blindMouseClicked
        // TODO add your handling code here:

        GameFrame.LOCAL_POSITION_CHIP = !GameFrame.LOCAL_POSITION_CHIP;

        this.playingCard1.setPosChip_visible(GameFrame.LOCAL_POSITION_CHIP);

        Helpers.PROPERTIES.setProperty("local_pos_chip", String.valueOf(GameFrame.LOCAL_POSITION_CHIP));

        Helpers.savePropertiesFile();

        Helpers.playWavResource(this.playingCard1.isPosChip_visible() ? "misc/button_on.wav" : "misc/button_off.wav");
    }//GEN-LAST:event_player_blindMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JSpinner bet_spinner;
    private javax.swing.JPanel botonera;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JPanel panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JButton player_allin_button;
    private javax.swing.JButton player_bet_button;
    private javax.swing.JLabel player_blind;
    private javax.swing.JButton player_check_button;
    private javax.swing.JButton player_fold_button;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JLabel player_stack;
    private com.tonikelope.coronapoker.Card playingCard1;
    private com.tonikelope.coronapoker.Card playingCard2;
    private javax.swing.JLabel timeout_label;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setWinner(String msg) {
        this.winner = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                setPlayerBorder(Color.GREEN, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                player_action.setEnabled(true);
                player_action.setBackground(Color.GREEN);
                player_action.setForeground(Color.BLACK);
                player_action.setText(msg);
                player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/happy.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                player_action_emoji = "happy";

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
                player_action.setEnabled(true);
                player_action.setBackground(Color.RED);
                player_action.setForeground(Color.WHITE);
                player_action.setText(msg);
                player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/cry.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                player_action_emoji = "cry";

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

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                utg_icon.setVisible(true);

            }
        });
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
                        player_action_emoji = "up";
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
                        player_action.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/emoji/bet.png")).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)));
                        player_action_emoji = "glasses";
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
                        player_action_emoji = "glasses";
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
                        player_action_emoji = "down";
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

    @Override
    public void showCards(String jugada) {
        Helpers.GUIRun(new Runnable() {
            public void run() {
                player_action.setBackground(new Color(51, 153, 255));
                player_action.setForeground(Color.WHITE);
                player_action.setText("MUESTRA (" + jugada + ")");
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

        if (GameFrame.getInstance().getSala_espera().getAvatar() != null) {

            avatar = new ImageIcon(new ImageIcon(GameFrame.getInstance().getSala_espera().getAvatar().getAbsolutePath()).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH));
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

            getPlayingCard1().destapar(false);

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
