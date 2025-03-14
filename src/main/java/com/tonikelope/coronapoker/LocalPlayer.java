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

import com.drew.imaging.ImageProcessingException;
import static com.tonikelope.coronapoker.GameFrame.NOTIFY_INGAME_GIF_REPEAT;
import static com.tonikelope.coronapoker.GameFrame.TTS_NO_SOUND_TIMEOUT;
import static com.tonikelope.coronapoker.Helpers.bufferedImagesEqual;
import static com.tonikelope.coronapoker.RemotePlayer.RERAISE_BACK_COLOR;
import static com.tonikelope.coronapoker.RemotePlayer.RERAISE_FORE_COLOR;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.management.ObjectName;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import org.apache.commons.codec.binary.Base64;

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
    public static final Color[][] ACTIONS_COLORS = new Color[][]{new Color[]{Color.GRAY, Color.WHITE}, new Color[]{Color.WHITE, Color.BLACK}, new Color[]{Color.YELLOW, Color.BLACK}, new Color[]{Color.BLACK, Color.WHITE}};
    public static final int MIN_ACTION_WIDTH = 550;
    public static final int MIN_ACTION_HEIGHT = 45;

    private final ConcurrentHashMap<JButton, Color[]> action_button_colors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JButton, Boolean> action_button_armed = new ConcurrentHashMap<>();
    private final Object pre_pulsar_lock = new Object();
    private final Object zoom_lock = new Object();
    private final Object radar_lock = new Object();

    private volatile String nickname;
    private volatile int buyin = GameFrame.BUYIN;
    private volatile float stack = 0f;
    private volatile float bet = 0f;
    private volatile boolean utg = false;
    private volatile int decision = Player.NODEC;
    private volatile boolean spectator = false;
    private volatile float pagar = 0f;
    private volatile float bote = 0f;
    private volatile Float last_bote = null;
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
    private volatile String player_action_icon = null;
    private volatile Timer icon_zoom_timer = null;
    private volatile URL chat_notify_image_url = null;
    private volatile Long chat_notify_thread = null;
    private final GifLabel chat_notify_label = new GifLabel();
    private final JLabel chip_label = new JLabel();
    private final JLabel sec_pot_win_label = new JLabel();
    private final ConcurrentLinkedQueue<Integer> botes_secundarios = new ConcurrentLinkedQueue<>();
    private volatile boolean reraise;
    private volatile int conta_win = 0;
    private volatile boolean radar_ckecking = false;

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dibujar el fondo redondeado si el componente tiene un color de fondo
        if (isOpaque()) {
            g2d.setColor(getBackground());
            g2d.fill(new RoundRectangle2D.Double(
                    0, 0,
                    getWidth(),
                    getHeight(),
                    Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP),
                    Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)
            ));
        }

        g2d.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        float border_size = Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
        float arc = Player.ARC * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dibuja el borde redondeado
        g2d.setColor(border_color); // Usa el color del borde
        g2d.setStroke(new BasicStroke(border_size)); // Usa el grosor del borde
        g2d.draw(new RoundRectangle2D.Double(
                border_size / 2.0, // Ajusta la posición para que el borde no se corte
                border_size / 2.0,
                getWidth() - border_size,
                getHeight() - border_size,
                arc,
                arc
        ));

        g2d.dispose();
    }

    private void setActionBackground(Color color) {
        Helpers.GUIRun(() -> {
            player_action.setOpaque(false);
            player_action_panel.setOpaque(true);
            player_action_panel.setBackground(color);
        });
    }

    private void setPlayerPotBackground(Color color) {
        Helpers.GUIRun(() -> {
            player_pot.setOpaque(false);
            player_pot_panel.setOpaque(true);
            player_pot_panel.setBackground(color);
        });
    }

    private void setPlayerStackBackground(Color color) {
        Helpers.GUIRun(() -> {
            player_stack.setOpaque(false);
            player_stack_panel.setOpaque(true);
            player_stack_panel.setBackground(color);
        });
    }

    public boolean isRADAR_ckecking() {
        return radar_ckecking;
    }

    public boolean secureHideHoleCards(long pause, long timeout) throws Exception {

        long time_limit = System.currentTimeMillis() + timeout;

        Rectangle r1 = new Rectangle((int) panel_cartas.getLocationOnScreen().getX(), (int) panel_cartas.getLocationOnScreen().getY(), panel_cartas.getWidth(), panel_cartas.getHeight());

        Robot robot = new Robot();

        BufferedImage c1 = robot.createScreenCapture(r1); //Captura el panel de las cartas con las cartas visibles

        Helpers.GUIRun(() -> {
            holeCard1.setSecure_hidden(true);
            holeCard1.getCard_image().setVisible(false);
            holeCard2.setSecure_hidden(true);
            holeCard2.getCard_image().setVisible(false);
            paintImmediately(panel_cartas.getBounds());
        });

        BufferedImage c2 = robot.createScreenCapture(r1); //Aquí normalmente las cartas seguirán aún visibles (depende del SO)

        while (System.currentTimeMillis() < time_limit && bufferedImagesEqual(c1, c2)) {

            //Mientras las cartas sigan visibles en pantalla
            Helpers.pausar(pause);

            if (System.currentTimeMillis() < time_limit) {
                c2 = robot.createScreenCapture(r1);
            }
        }

        Helpers.pausar(50); //Hay que esperar un extra para asegurarnos que la imagen nueva es estable y evitar capturar las cartas en una transición (con algo de transparencia) (RARO)

        return (System.currentTimeMillis() >= time_limit);
    }

    public void RADAR(String requester) {

        Helpers.threadRun(() -> {
            synchronized (radar_lock) {
                try {
                    radar_ckecking = true;
                    boolean screenshot_error = false;
                    BufferedImage capture = null;
                    long timestamp = System.currentTimeMillis();
                    try {
                        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        GraphicsDevice[] screens = ge.getScreenDevices();
                        Rectangle allScreenBounds = new Rectangle();
                        for (GraphicsDevice screen : screens) {
                            Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
                            allScreenBounds.width += screenBounds.width;
                            allScreenBounds.height = Math.max(allScreenBounds.height, screenBounds.height);
                        }
                        secureHideHoleCards(25, 2000);
                        timestamp = System.currentTimeMillis();
                        capture = new Robot().createScreenCapture(allScreenBounds);
                        Helpers.GUIRun(() -> {
                            holeCard1.getCard_image().setVisible(holeCard1.isVisible_card());
                            holeCard1.setSecure_hidden(false);
                            holeCard2.getCard_image().setVisible(holeCard2.isVisible_card());
                            holeCard2.setSecure_hidden(false);
                            paintImmediately(panel_cartas.getBounds());
                        });
                    } catch (Exception ex) {
                        screenshot_error = true;
                    }
                    radar_ckecking = false;
                    byte[] imageInByte = null;
                    if (!screenshot_error) {

                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            ImageIO.write(Helpers.convertToGrayScale(capture), "jpg", baos);

                            baos.flush();

                            imageInByte = baos.toByteArray();
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    if (Init.coronaHMACVM()) {
                        sb.append("@\n");
                    }
                    sb.append("  ____                            ____       _               ____      _    ____    _    ____  \n"
                            + " / ___|___  _ __ ___  _ __   __ _|  _ \\ ___ | | _____ _ __  |  _ \\    / \\  |  _ \\  / \\  |  _ \\ \n"
                            + "| |   / _ \\| '__/ _ \\| '_ \\ / _` | |_) / _ \\| |/ / _ \\ '__| | |_) |  / _ \\ | | | |/ _ \\ | |_) |\n"
                            + "| |__| (_) | | | (_) | | | | (_| |  __/ (_) |   <  __/ |    |  _ <  / ___ \\| |_| / ___ \\|  _ < \n"
                            + " \\____\\___/|_|  \\___/|_| |_|\\__,_|_|   \\___/|_|\\_\\___|_|    |_| \\_\\/_/   \\_\\____/_/   \\_\\_| \\_\\\n"
                            + "                                                                                               ");
                    sb.append("\n\nCoronaPoker Radar -> [" + nickname + "] " + Helpers.getFechaHoraActual() + "\n\n");
                    sb.append(Translator.translate("ADVERTENCIA: este registro debe comprobarse manualmente para detectar las aplicaciones y/o bibliotecas utilizadas para hacer trampas.\n\n"));
                    sb.append("******************** " + Translator.translate("PROCESOS DEL SISTEMA") + " ********************\n\n");
                    sb.append(Helpers.getProcessesList());
                    sb.append("\n\n******************** " + Translator.translate("LIBRERÍAS CARGADAS CON CORONAPOKER") + " ********************\n\n");
                    String java_libs = null;
                    try {
                        java_libs = (String) ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "vmDynlibs", null, null);
                    } catch (Exception ex) {
                    }
                    sb.append(java_libs);
                    Audio.playWavResource("misc/radar.wav");
                    if (GameFrame.getInstance().isPartida_local()) {
                        GameFrame.getInstance().getParticipantes().get(requester).writeGAMECommandFromServer("RADAR#" + Base64.encodeBase64String(nickname.getBytes("UTF-8")) + "#" + (imageInByte != null ? Base64.encodeBase64String(imageInByte) : "*") + "#" + Base64.encodeBase64String(sb.toString().getBytes("UTF-8")) + "#" + String.valueOf(timestamp));
                    } else {
                        GameFrame.getInstance().getCrupier().sendGAMECommandToServer("RADAR#" + Base64.encodeBase64String(requester.getBytes("UTF-8")) + "#" + (imageInByte != null ? Base64.encodeBase64String(imageInByte) : "*") + "#" + Base64.encodeBase64String(sb.toString().getBytes("UTF-8")) + "#" + String.valueOf(timestamp));
                    }
                } catch (Exception ex) {
                    Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public void refreshNotifyChatLabel() {
        Helpers.GUIRun(() -> {
            if (getChat_notify_label().isVisible()) {
                Helpers.threadRun(() -> {
                    if (chat_notify_image_url != null) {
                        setNotifyImageChatLabel(chat_notify_image_url);
                    } else {
                        setNotifyTTSChatLabel();
                    }
                });
            }
        });

    }

    public void setNotifyTTSChatLabel() {

        chat_notify_image_url = null;

        synchronized (getChat_notify_label()) {

            getChat_notify_label().notifyAll();
        }

        Helpers.GUIRun(() -> {
            int sound_icon_size_h = Math.round(getHoleCard1().getHeight() / 2);

            int sound_icon_size_w = Math.round((596 * sound_icon_size_h) / 460);

            int pos_x = panel_cartas.getWidth() - sound_icon_size_w;

            int pos_y = Math.round(getHoleCard1().getHeight() / 2);

            getChat_notify_label().setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/talk.png")).getImage().getScaledInstance(sound_icon_size_w, sound_icon_size_h, Image.SCALE_SMOOTH)));

            getChat_notify_label().setSize(sound_icon_size_w, sound_icon_size_h);

            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());

            getChat_notify_label().setOpaque(false);

            getChat_notify_label().revalidate();

            getChat_notify_label().repaint();

            getChat_notify_label().setLocation(pos_x, pos_y);
        });
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {

        try {

            chat_notify_image_url = u;

            final boolean isgif = (ChatImageDialog.GIF_CACHE.containsKey(u.toString()) || Helpers.isImageGIF(u));

            final CyclicBarrier gif_barrier = new CyclicBarrier(2);

            getChat_notify_label().setBarrier(gif_barrier);

            Helpers.threadRun(() -> {
                chat_notify_thread = Thread.currentThread().getId();
                synchronized (getChat_notify_label()) {
                    try {
                        getChat_notify_label().notifyAll();

                        final ImageIcon orig = new ImageIcon(new URL(u.toString() + "#" + String.valueOf(System.currentTimeMillis())));

                        int max_width = panel_cartas.getWidth();

                        int max_height = Math.round(getHoleCard1().getHeight() / 2);

                        int new_height = max_height;

                        int new_width = (int) Math.round((orig.getIconWidth() * max_height) / orig.getIconHeight());

                        if (new_width > max_width) {

                            new_height = (int) Math.round((new_height * max_width) / new_width);

                            new_width = max_width;
                        }

                        final ImageIcon image = new ImageIcon(orig.getImage().getScaledInstance(new_width, new_height, isgif ? Image.SCALE_DEFAULT : Image.SCALE_SMOOTH));

                        int pos_x = panel_cartas.getWidth() - image.getIconWidth();

                        int pos_y = Math.round(getHoleCard1().getHeight() / 2);

                        int gif_frames_count = isgif ? Helpers.getGIFFramesCount(u) : 0;

                        Helpers.GUIRunAndWait(() -> {
                            if (isgif) {
                                getChat_notify_label().setIcon(image, gif_frames_count);
                            } else {
                                getChat_notify_label().setIcon(image);
                            }
                            getChat_notify_label().setRepeat(NOTIFY_INGAME_GIF_REPEAT);
                            getChat_notify_label().setSize(image.getIconWidth(), image.getIconHeight());
                            getChat_notify_label().setPreferredSize(getChat_notify_label().getSize());
                            getChat_notify_label().setOpaque(false);
                            getChat_notify_label().revalidate();
                            getChat_notify_label().repaint();
                            getChat_notify_label().setLocation(pos_x, pos_y);
                            getChat_notify_label().setVisible(true);
                        });
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException | ImageProcessingException ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                if (isgif) {

                    try {
                        gif_barrier.await();
                    } catch (Exception ex) {
                        Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    synchronized (getChat_notify_label()) {
                        try {
                            getChat_notify_label().wait(TTS_NO_SOUND_TIMEOUT);
                        } catch (Exception ex) {
                            Logger.getLogger(GifAnimationDialog.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                if (Thread.currentThread().getId() == chat_notify_thread) {
                    Helpers.GUIRunAndWait(() -> {
                        getChat_notify_label().setVisible(false);
                    });
                }
            });

        } catch (Exception ex) {
            Logger.getLogger(RemotePlayer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public JLabel getChip_label() {
        return chip_label;
    }

    public JLayeredPane getPanel_cartas() {
        return panel_cartas;
    }

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

    public void refreshPositionChipIcons() {

        ImageIcon chip_label_icon;

        if (this.nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/bb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_BB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource("/images/sb.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = Helpers.IMAGEN_SB;
        } else if (this.nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {
            Helpers.setScaledIconLabel(player_name, getClass().getResource(GameFrame.getInstance().getCrupier().isDead_dealer() ? "/images/dead_dealer.png" : "/images/dealer.png"), Math.round(0.7f * player_name.getHeight()), Math.round(0.7f * player_name.getHeight()));

            chip_label_icon = GameFrame.getInstance().getCrupier().isDead_dealer() ? Helpers.IMAGEN_DEAD_DEALER : Helpers.IMAGEN_DEALER;
        } else {
            chip_label_icon = null;
        }

        Helpers.GUIRun(() -> {
            if (isActivo() && chip_label_icon != null) {
                chip_label.setIcon(chip_label_icon);
                chip_label.setSize(chip_label.getIcon().getIconWidth(), chip_label.getIcon().getIconHeight());
                chip_label.setLocation(0, getHoleCard1().getHeight() - chip_label.getHeight());
                chip_label.revalidate();
                chip_label.repaint();
                chip_label.setVisible(GameFrame.LOCAL_POSITION_CHIP);

            } else {
                chip_label.setVisible(false);
            }
        });

    }

    public void activar_boton_mostrar(boolean parguela) {

        boton_mostrar = true;

        desactivarControles();

        Helpers.GUIRun(() -> {
            if (parguela) {
                player_allin_button.setText(Translator.translate("MOSTRAR") + " (" + parguela_counter + ")");
            } else {
                player_allin_button.setText(Translator.translate("MOSTRAR"));

            }
            player_allin_button.setIcon(null);
            player_allin_button.setForeground(Color.WHITE);
            player_allin_button.setBackground(new Color(51, 153, 255));
            player_allin_button.setEnabled(true);

            if (GameFrame.TEST_MODE) {
                player_allin_button.doClick();
            }
        });

    }

    public void setSpectator(String msg) {
        if (!this.exit) {
            this.spectator = true;
            this.bote = 0f;

            Helpers.GUIRunAndWait(() -> {
                desactivarControles();
                setOpaque(false);
                setBackground(null);
                setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                player_pot.setText("----");
                player_pot.setForeground(Color.white);
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                utg_icon.setVisible(false);
                holeCard1.resetearCarta();
                holeCard2.resetearCarta();
                player_name.setOpaque(false);
                player_name.setBackground(null);
                player_name.setIcon(null);
                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);

                if (buyin > GameFrame.BUYIN) {
                    setPlayerStackBackground(Color.CYAN);
                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));
                    player_stack.setForeground(Color.WHITE);
                }

                player_stack.setText(Helpers.float2String(stack));

                if (GameFrame.getInstance().getSala_espera().getServer_nick().equals(nickname)) {
                    player_name.setForeground(Color.YELLOW);
                } else {
                    player_name.setForeground(Color.WHITE);
                }

                setAuto_pause(false);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setBackground(null);
                GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setForeground(null);

                if (!GameFrame.getInstance().isPartida_local()) {
                    GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(false);
                }

                disablePlayerAction();
            });

            Helpers.threadRun(() -> {
                while (player_name.getHeight() == 0) {
                    Helpers.pausar(125);
                }
                Helpers.GUIRun(() -> {
                    if (isSpectator()) {
                        player_action.setText(msg != null ? msg : Translator.translate("ESPECTADOR"));
                        setPlayerActionIcon(Helpers.float1DSecureCompare(0f, getEffectiveStack()) == 0 ? "action/ghost.png" : "action/calentando.png");
                    }
                });
            });

        }
    }

    public void unsetSpectator() {
        this.spectator = false;

        Helpers.GUIRun(() -> {
            setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            player_name.setIcon(null);
            player_stack.setEnabled(true);
            GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().setVisible(true);
            disablePlayerAction();
        });
    }

    public void desactivar_boton_mostrar() {

        if (boton_mostrar) {
            boton_mostrar = false;

            Helpers.GUIRun(() -> {
                player_allin_button.setText(" ");
                player_allin_button.setEnabled(false);
                player_allin_button.setBackground(Color.BLACK);
                player_allin_button.setForeground(Color.WHITE);
            });
        }
    }

    public JLabel getPlayer_action() {
        return player_action;
    }

    public void setTimeout(boolean val) {

        if (this.timeout != val) {

            this.timeout = val;

            Helpers.GUIRun(() -> {
                if (val) {

                    setPlayerBorder(Color.MAGENTA, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                    setPlayerActionIcon("action/timeout.png");
                } else {
                    setPlayerBorder(border_color != null ? border_color : new java.awt.Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                    setPlayerActionIcon(player_action_icon);
                }
            });

            if (val) {

                Audio.playWavResource("misc/network_error_" + GameFrame.LANGUAGE.toLowerCase() + ".wav");
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
            this.timeout = false;

            desactivarControles();

            Helpers.GUIRun(() -> {
                setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                holeCard1.resetearCarta();
                holeCard2.resetearCarta();
                setActionBackground(new Color(255, 102, 0));
                player_action.setForeground(Color.WHITE);
                player_action.setText(Translator.translate("ABANDONAS LA TIMBA"));
                setPlayerActionIcon("exit.png");
                player_action.setVisible(true);
                chip_label.setVisible(false);
                sec_pot_win_label.setVisible(false);
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

        Helpers.GUIRun(() -> {
            player_name.setText(nickname);

            if (!GameFrame.getInstance().isPartida_local()) {

                avatar.setToolTipText("CLICK -> AES-KEY");
            } else {
                player_name.setForeground(Color.YELLOW);
                avatar.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });
    }

    public synchronized float getStack() {
        return stack;
    }

    public synchronized void setStack(float stack) {
        this.stack = Helpers.floatClean(stack);

        if (!player_stack_click) {
            Helpers.GUIRunAndWait(() -> {
                if (getNickname() != null && GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                    setPlayerStackBackground(Color.YELLOW);
                    player_stack.setForeground(Color.BLACK);
                    player_stack.setText(Helpers.float2String(stack) + " + " + Helpers.float2String(new Float((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname()))));

                } else {

                    if (buyin > GameFrame.BUYIN) {
                        setPlayerStackBackground(Color.CYAN);

                        player_stack.setForeground(Color.BLACK);
                    } else {

                        setPlayerStackBackground(new Color(51, 153, 0));

                        player_stack.setForeground(Color.WHITE);
                    }

                    player_stack.setText(Helpers.float2String(stack));
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

        MouseEvent me = new MouseEvent(player_stack, // which
                MouseEvent.MOUSE_CLICKED, // what
                System.currentTimeMillis(), // when
                MouseEvent.BUTTON1_MASK,
                0, 0, // where: at (0, 0}
                1, // only 1 click 
                false); // not a popup trigger

        player_stack.dispatchEvent(me);

    }

    public GifLabel getChat_notify_label() {
        return chat_notify_label;
    }

    /**
     * Creates new form JugadorLocalView
     */
    public LocalPlayer() {

        Helpers.GUIRunAndWait(() -> {
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
                    Helpers.threadRun(() -> {
                        synchronized (chat_notify_label) {

                            chat_notify_label.notifyAll();
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
            chip_label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {

                    player_nameMouseClicked(e);
                }
            });
            panel_cartas.add(chip_label, new Integer(1001));
            border_color = ((LineBorder) getBorder()).getLineColor();
            action_button_armed.put(player_check_button, false);
            action_button_armed.put(player_bet_button, false);
            action_button_armed.put(player_allin_button, false);
            action_button_armed.put(player_fold_button, false);
            disablePlayerAction();
            desactivarControles();
            Helpers.setScaledIconLabel(utg_icon, getClass().getResource("/images/utg.png"), 41, 31);
            utg_icon.setVisible(false);
            player_pot.setText("----");
            player_name.setCursor(new Cursor(Cursor.HAND_CURSOR));
            icon_zoom_timer = new Timer(GameFrame.GUI_ZOOM_WAIT, (ActionEvent ae) -> {
                icon_zoom_timer.stop();
                zoomIcons();
                holeCard1.updateImagePreloadCache();
                holeCard2.updateImagePreloadCache();
                refreshNotifyChatLabel();
            });
            icon_zoom_timer.setRepeats(false);
            icon_zoom_timer.setCoalesce(false);

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

    public synchronized void setBet(float new_bet) {

        float old_bet = bet;

        bet = Helpers.floatClean(new_bet);

        if (Helpers.float1DSecureCompare(old_bet, bet) < 0) {
            this.bote += Helpers.floatClean(bet - old_bet);
            setStack(stack - (bet - old_bet));
        }

        GameFrame.getInstance().getCrupier().getBote().addPlayer(this);

        Helpers.GUIRunAndWait(() -> {
            if (Helpers.float1DSecureCompare(0f, bote) < 0) {
                player_pot.setText(Helpers.float2String(bote));

            } else {

                player_pot.setText("----");
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
        Audio.playWavResource("misc/cash_register.wav");

        if (!player_stack_click) {
            Helpers.GUIRun(() -> {
                player_stack.setText(Helpers.float2String(stack));
                setPlayerStackBackground(Color.CYAN);
                player_stack.setForeground(Color.BLACK);
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
            Audio.playWavResource("misc/yourturn.wav");

            call_required = Helpers.floatClean(GameFrame.getInstance().getCrupier().getApuesta_actual() - bet);

            min_raise = Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getUltimo_raise()) < 0 ? GameFrame.getInstance().getCrupier().getUltimo_raise() : Helpers.floatClean(GameFrame.getInstance().getCrupier().getCiega_grande());

            Helpers.GUIRunAndWait(() -> {
                desarmarBotonesAccion();
                setPlayerBorder(Color.ORANGE, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                player_allin_button.setText("ALL IN");
                player_allin_button.setEnabled(true);
                Helpers.setScaledIconButton(player_allin_button, getClass().getResource("/images/action/glasses.png"), Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()));
                player_fold_button.setText("NO IR");
                player_fold_button.setEnabled(true);
                player_fold_button.setBackground(Color.DARK_GRAY);
                player_fold_button.setForeground(Color.WHITE);
                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));
                setActionBackground(new Color(204, 204, 204, 75));
                player_action.setForeground(Color.WHITE);
                //Comprobamos si podemos ver la apuesta actual
                if (Helpers.float1DSecureCompare(call_required, stack) < 0) {

                    player_check_button.setEnabled(true);

                    Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));

                    if (Helpers.float1DSecureCompare(0f, call_required) == 0) {
                        player_check_button.setText("PASAR");
                        player_check_button.setBackground(new Color(0, 130, 0));
                        player_check_button.setForeground(Color.WHITE);

                        if (pre_pulsado == Player.FOLD) {
                            desPrePulsarBotonAuto(player_fold_button);
                        }

                        player_fold_button.setBackground(Color.RED);
                        player_fold_button.setForeground(Color.WHITE);
                    } else {
                        player_check_button.setText(Translator.translate("IR") + " (+" + Helpers.float2String(call_required) + ")");
                        player_check_button.setBackground(null);
                        player_check_button.setForeground(null);
                        player_fold_button.setBackground(Color.DARK_GRAY);
                        player_fold_button.setForeground(Color.WHITE);
                    }

                } else {

                    if (pre_pulsado == Player.CHECK) {
                        desPrePulsarBotonAuto(player_check_button);
                    }

                    player_check_button.setIcon(null);
                    player_check_button.setText(" ");
                    player_check_button.setEnabled(false);
                }
                if ((GameFrame.getInstance().getCrupier().getLast_aggressor() == null || !nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())) && GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) > 1 && ((Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getCiega_grande(), stack) < 0)
                        || (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0 && Helpers.float1DSecureCompare(call_required + min_raise, stack) < 0))) {

                    //Actualizamos el spinner y el botón de apuestas
                    BigDecimal spinner_min;
                    BigDecimal spinner_max = new BigDecimal(stack - call_required).setScale(1, RoundingMode.HALF_UP);

                    Helpers.setScaledIconButton(player_bet_button, getClass().getResource("/images/action/bet.png"), Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()));

                    if (Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                        spinner_min = new BigDecimal(GameFrame.getInstance().getCrupier().getCiega_grande()).setScale(1, RoundingMode.HALF_UP);
                        player_bet_button.setEnabled(true);
                        player_bet_button.setText(Translator.translate("APOSTAR"));
                        player_bet_button.setBackground(Color.WHITE);
                        player_bet_button.setForeground(Color.BLACK);

                    } else {
                        spinner_min = new BigDecimal(min_raise).setScale(1, RoundingMode.HALF_UP);
                        player_bet_button.setEnabled(true);
                        player_bet_button.setText(Translator.translate((GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "RE" : "") + "SUBIR"));

                        if (GameFrame.getInstance().getCrupier().getConta_raise() > 0) {
                            player_bet_button.setBackground(RERAISE_BACK_COLOR);
                            player_bet_button.setForeground(RERAISE_FORE_COLOR);
                        } else {
                            player_bet_button.setBackground(Color.WHITE);
                            player_bet_button.setForeground(Color.BLACK);
                        }
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
                guardarColoresBotonesAccion();
                if ((GameFrame.getInstance().getCrupier().puedenApostar(GameFrame.getInstance().getJugadores()) == 1 || ((GameFrame.getInstance().getCrupier().getLast_aggressor() != null && nickname.equals(GameFrame.getInstance().getCrupier().getLast_aggressor().getNickname())))) && Helpers.float1DSecureCompare(call_required, stack) < 0) {
                    player_allin_button.setText(" ");
                    player_allin_button.setEnabled(false);
                    player_allin_button.setIcon(null);
                }
                Helpers.resetBarra(GameFrame.getInstance().getBarra_tiempo(), GameFrame.TIEMPO_PENSAR);
                player_action.setText("HABLAS TÚ");
                Helpers.translateComponents(botonera, false);
                Helpers.translateComponents(player_action, false);
                setPlayerActionIcon("action/thinking.png");

                Helpers.setSpinnerColors(bet_spinner, player_bet_button.getBackground(), player_bet_button.getForeground());

                if (GameFrame.TEST_MODE) {
                    Helpers.threadRun(() -> {
                        Helpers.pausar(GameFrame.TEST_MODE_PAUSE);
                        ArrayList<JButton> botones = new ArrayList<>(Arrays.asList(new JButton[]{player_check_button, player_bet_button, player_allin_button, player_fold_button}));
                        Iterator<JButton> iterator = botones.iterator();
                        Helpers.GUIRunAndWait(() -> {
                            while (iterator.hasNext()) {
                                JButton boton = iterator.next();

                                if (!boton.isEnabled()) {
                                    iterator.remove();
                                }
                            }

                            int eleccion = Helpers.CSPRNG_GENERATOR.nextInt(botones.size());

                            botones.get(eleccion).doClick();
                        });
                    });
                } else {
                    //Tiempo máximo para pensar
                    response_counter = GameFrame.TIEMPO_PENSAR;
                    if (auto_action != null) {
                        auto_action.stop();
                    }
                    auto_action = new Timer(1000, new ActionListener() {
                        long t = GameFrame.getInstance().getCrupier().getTurno();

                        public void actionPerformed(ActionEvent ae) {
                            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().getCrupier().isSomePlayerTimeout() && !GameFrame.getInstance().isTimba_pausada() && !isRADAR_ckecking() && response_counter > 0 && auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {
                                response_counter--;
                                GameFrame.getInstance().getBarra_tiempo().setValue(response_counter);
                                if (response_counter == 10) {
                                    Audio.playWavResource("misc/hurryup.wav");
                                    if ((hurryup_timer == null || !hurryup_timer.isRunning()) && Helpers.float1DSecureCompare(0f, call_required) < 0) {
                                        if (hurryup_timer != null) {
                                            hurryup_timer.stop();
                                        }
                                        Color orig_color = border_color;
                                        hurryup_timer = new Timer(1000, (ActionEvent ae1) -> {
                                            if (!GameFrame.getInstance().getCrupier().isFin_de_la_transmision() && !GameFrame.getInstance().isTimba_pausada() && !isRADAR_ckecking() && hurryup_timer.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno()) {
                                                if (player_action.getBackground() != Color.GRAY) {
                                                    setPlayerBorder(Color.GRAY, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                                                    setActionBackground(Color.GRAY);
                                                    player_action.setForeground(Color.WHITE);
                                                } else {
                                                    setPlayerBorder(orig_color, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
                                                    setActionBackground(new Color(204, 204, 204, 75));
                                                    player_action.setForeground(Color.WHITE);
                                                }

                                                revalidate();
                                                repaint();
                                            }
                                        });
                                        hurryup_timer.start();
                                    }
                                }
                                if (response_counter == 0 || GameFrame.getInstance().getCrupier().getJugadoresActivos() < 2) {
                                    Helpers.threadRun(() -> {
                                        if (response_counter == 0) {
                                            Audio.playWavResourceAndWait("misc/timeout.wav"); //Mientras dura la bocina aún estaríamos a tiempo de elegir
                                        }
                                        GameFrame.getInstance().checkPause();
                                        Helpers.GUIRun(() -> {
                                            if (auto_action.isRunning() && t == GameFrame.getInstance().getCrupier().getTurno() && getDecision() == Player.NODEC) {

                                                if (Helpers.float1DSecureCompare(0f, call_required) == 0) {

                                                    //Pasamos automáticamente
                                                    action_button_armed.put(player_check_button, true);
                                                    player_check_button.doClick();

                                                } else {

                                                    //Nos tiramos automáticamente
                                                    action_button_armed.put(player_fold_button, true);
                                                    player_fold_button.doClick();

                                                }

                                            }
                                        });
                                    });
                                }
                            }
                        }
                    });
                    auto_action.start();
                    if (!auto_pause && GameFrame.AUTO_ACTION_BUTTONS && pre_pulsado != Player.NODEC) {

                        if (player_fold_button.isEnabled() && pre_pulsado == Player.FOLD) {

                            player_fold_button.doClick();

                        } else if (player_check_button.isEnabled() && pre_pulsado == Player.CHECK && (Helpers.float1DSecureCompare(0f, call_required) == 0 || (GameFrame.getInstance().getCrupier().getStreet() == Crupier.PREFLOP && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0))) {

                            player_check_button.doClick();

                        } else {

                            desPrePulsarAutoTodo();
                        }
                    }
                    if (auto_pause) {
                        GameFrame.getInstance().getLocalPlayer().setAuto_pause(false);
                        GameFrame.getInstance().getTapete().getCommunityCards().getPause_button().doClick();
                    }
                    revalidate();
                    repaint();
                }
            });

        } else {

            finTurno();
        }

    }

    public void finTurno() {

        Audio.stopWavResource("misc/hurryup.wav");

        action_button_colors.clear();

        Helpers.GUIRun(() -> {
            if (decision != Player.ALLIN && decision != Player.FOLD) {
                setPlayerBorder(new Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));
            }

            turno = false;

            synchronized (GameFrame.getInstance().getCrupier().getLock_apuestas()) {
                GameFrame.getInstance().getCrupier().getLock_apuestas().notifyAll();
            }

            if (GameFrame.AUTO_ACTION_BUTTONS && getDecision() != Player.ALLIN && getDecision() != Player.FOLD) {
                activarPreBotones();
            }
        });
    }

    public void desactivarControles() {

        Helpers.GUIRunAndWait(() -> {
            bet_spinner.setValue(new BigDecimal(0));

            bet_spinner.setEnabled(false);

            for (Component c : botonera.getComponents()) {

                if (c instanceof JButton) {
                    ((JButton) c).setText(" ");
                    ((JButton) c).setIcon(null);
                    c.setEnabled(false);
                }
            }

            desarmarBotonesAccion();
        });

    }

    public void desPrePulsarAutoTodo() {

        if (pre_pulsado != Player.NODEC) {

            desPrePulsarBotonAuto(player_check_button);
            desPrePulsarBotonAuto(player_fold_button);
        }
    }

    public void desPrePulsarBotonAuto(JButton boton) {
        pre_pulsado = Player.NODEC;

        Helpers.GUIRunAndWait(() -> {
            Color[] colores = action_button_colors.get(boton);
            if (colores != null) {
                boton.setBackground(colores[0]);
                boton.setForeground(colores[1]);
            } else {
                boton.setBackground(null);
                boton.setForeground(null);
            }
        });

    }

    public void prePulsarBotonAuto(JButton boton, int dec) {
        pre_pulsado = dec;

        Helpers.GUIRunAndWait(() -> {
            boton.setBackground(Color.YELLOW);
            boton.setForeground(Color.BLACK);
        });

    }

    public void desarmarBotonesAccion() {
        Helpers.GUIRunAndWait(() -> {
            for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                JButton b = entry.getKey();

                if (action_button_armed.get(b)) {

                    Color[] colores = entry.getValue();

                    action_button_armed.put(b, false);

                    b.setBackground(colores[0]);
                    b.setForeground(colores[1]);

                }

            }
        });
    }

    public void armarBoton(JButton boton) {

        Helpers.GUIRunAndWait(() -> {
            for (Map.Entry<JButton, Color[]> entry : action_button_colors.entrySet()) {

                JButton b = entry.getKey();

                Color[] colores = entry.getValue();

                if (b == boton) {
                    action_button_armed.put(b, true);

                    b.setBackground(Color.BLUE);
                    b.setForeground(Color.WHITE);

                } else {
                    action_button_armed.put(b, false);

                    b.setBackground(colores[0]);
                    b.setForeground(colores[1]);

                }
            }
        });

    }

    public void resetBetDecision() {

        int old_dec = this.decision;

        this.decision = Player.NODEC;

        Helpers.GUIRun(() -> {
            if (old_dec != Player.BET || Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) == 0) {
                setPlayerPotBackground(new Color(204, 204, 204, 75));
                player_pot.setForeground(Color.WHITE);
            }

            disablePlayerAction();
        });

    }

    public void activarPreBotones() {

        if (!turno && decision != Player.FOLD && decision != Player.ALLIN && !GameFrame.getInstance().getCrupier().isShow_time()) {

            Helpers.GUIRunAndWait(() -> {
                player_check_button.setBackground(null);
                player_check_button.setForeground(null);
                player_check_button.setText("[AUTO](+BB)");
                player_check_button.setEnabled(true);
                Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));

                player_fold_button.setBackground(null);
                player_fold_button.setForeground(null);
                player_fold_button.setText("[AUTO]");
                player_fold_button.setEnabled(true);
                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));

                if (pre_pulsado != Player.NODEC) {

                    if (pre_pulsado == Player.CHECK) {
                        prePulsarBotonAuto(player_check_button, Player.CHECK);
                    } else if (pre_pulsado == Player.FOLD) {
                        prePulsarBotonAuto(player_fold_button, Player.FOLD);
                    }
                }
            });

        }

    }

    public void desActivarPreBotones() {

        if (!turno) {

            Helpers.GUIRunAndWait(() -> {
                desPrePulsarAutoTodo();

                player_check_button.setText(" ");
                player_check_button.setIcon(null);
                player_check_button.setEnabled(false);

                player_fold_button.setText(" ");
                player_fold_button.setIcon(null);
                player_fold_button.setEnabled(false);
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

    public void disablePlayerAction() {

        Helpers.GUIRun(() -> {
            player_action.setText(" ");
            player_action.setForeground(Color.LIGHT_GRAY);
            setActionBackground(new Color(204, 204, 204, 75));
            setPlayerActionIcon(null);
        });
    }

    public void resetGUI() {
        Helpers.GUIRunAndWait(() -> {
            sec_pot_win_label.setVisible(false);

            setOpaque(false);

            setBackground(null);

            setPlayerBorder(new java.awt.Color(204, 204, 204, 75), Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

            player_name.setIcon(null);

            desactivar_boton_mostrar();

            desactivarControles();

            utg_icon.setVisible(false);

            player_pot.setText("----");

            setPlayerPotBackground(new Color(204, 204, 204, 75));

            player_pot.setForeground(Color.WHITE);

            if (conta_win > 0) {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            } else {
                hands_win.setVisible(false);
            }

            if (!player_stack_click) {
                if (buyin > GameFrame.BUYIN) {
                    setPlayerStackBackground(Color.CYAN);

                    player_stack.setForeground(Color.BLACK);
                } else {

                    setPlayerStackBackground(new Color(51, 153, 0));

                    player_stack.setForeground(Color.WHITE);
                }
            }

            disablePlayerAction();

        });
    }

    @Override
    public void nuevaMano() {

        desPrePulsarAutoTodo();

        this.decision = Player.NODEC;

        this.botes_secundarios.clear();

        this.muestra = false;

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

    public synchronized float getEffectiveStack() {

        return Helpers.floatClean(this.stack) + Helpers.floatClean(this.bote) + Helpers.floatClean(this.pagar);

    }

    public boolean isBoton_mostrar() {
        return boton_mostrar;
    }

    @Override
    public void disableUTG() {

        if (this.utg) {
            this.utg = false;

            Helpers.GUIRun(() -> {
                utg_icon.setVisible(false);
            });
        }
    }

    private void actionIconZoom() {

        if (player_action_icon != null) {

            setPlayerActionIcon(player_action_icon);

        }

    }

    private void buttonIconZoom() {

        Helpers.GUIRun(() -> {
            if (player_check_button.isEnabled()) {
                Helpers.setScaledIconButton(player_check_button, getClass().getResource("/images/action/up.png"), Math.round(0.6f * player_check_button.getHeight()), Math.round(0.6f * player_check_button.getHeight()));
            }
            if (player_bet_button.isEnabled()) {
                Helpers.setScaledIconButton(player_bet_button, getClass().getResource("/images/action/bet.png"), Math.round(0.6f * player_bet_button.getHeight()), Math.round(0.6f * player_bet_button.getHeight()));
            }

            if (player_allin_button.isEnabled() && !boton_mostrar) {
                Helpers.setScaledIconButton(player_allin_button, getClass().getResource("/images/action/glasses.png"), Math.round(0.6f * player_allin_button.getHeight()), Math.round(0.6f * player_allin_button.getHeight()));
            }

            if (player_fold_button.isEnabled()) {

                Helpers.setScaledIconButton(player_fold_button, getClass().getResource("/images/action/down.png"), Math.round(0.6f * player_fold_button.getHeight()), Math.round(0.6f * player_fold_button.getHeight()));
            }
        });
    }

    private void nickChipIconZoom() {
        Helpers.GUIRun(() -> {
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
        });
    }

    private void utgIconZoom() {

        ImageIcon icon = new ImageIcon(IMAGEN_UTG.getImage().getScaledInstance((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight(), Image.SCALE_SMOOTH));

        Helpers.GUIRun(() -> {
            utg_icon.setIcon(icon);

            utg_icon.setPreferredSize(new Dimension((int) Math.round(player_name.getHeight() * (480f / 360f)), player_name.getHeight()));

            utg_icon.setVisible(utg);
        });
    }

    private void secPotIconZoom() {

        Helpers.GUIRun(() -> {
            sec_pot_win_label.setSize(player_action.getSize());
            sec_pot_win_label.setPreferredSize(player_action.getSize());
            Helpers.setScaledIconLabel(sec_pot_win_label, getClass().getResource("/images/pot.png"), sec_pot_win_label.getHeight(), sec_pot_win_label.getHeight());
            int pos_x = Math.round((panel_cartas.getWidth() - sec_pot_win_label.getWidth()) / 2);
            int pos_y = Math.round((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2);
            sec_pot_win_label.setLocation(pos_x, pos_y);
        });
    }

    private void zoomIcons() {

        Helpers.threadRun(() -> {
            synchronized (zoom_lock) {
                Helpers.GUIRunAndWait(() -> {
                    setAvatar();
                    utgIconZoom();
                    actionIconZoom();
                    buttonIconZoom();
                    nickChipIconZoom();
                    refreshPositionChipIcons();
                    refreshSecPotLabel();
                });
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

                Helpers.GUIRunAndWait(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.stop();
                    }

                    hidePlayerActionIcon();
                    player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH * zoom_factor), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * zoom_factor)));
                    setPlayerBorder(((LineBorder) getBorder()).getLineColor(), Math.round(Player.BORDER * zoom_factor));
                    getAvatar().setVisible(false);
                    utg_icon.setVisible(false);

                    player_check_button.setIcon(null);

                    player_bet_button.setIcon(null);

                    player_allin_button.setIcon(null);

                    player_fold_button.setIcon(null);

                    player_name.setIcon(null);

                    chip_label.setVisible(false);
                });

                Helpers.zoomFonts(this, zoom_factor, null);

                Helpers.GUIRun(() -> {
                    if (icon_zoom_timer.isRunning()) {
                        icon_zoom_timer.restart();
                    } else {
                        icon_zoom_timer.start();
                    }
                });

            }

            while (mynotifier.size() < 2) {

                synchronized (mynotifier) {

                    try {
                        mynotifier.wait(1000);

                    } catch (InterruptedException ex) {
                        Logger.getLogger(LocalPlayer.class.getName()).log(Level.SEVERE, null, ex);
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        indicadores_arriba = new javax.swing.JPanel();
        avatar_panel = new javax.swing.JPanel();
        avatar = new javax.swing.JLabel();
        player_pot_panel = new RoundedPanel(20);
        player_pot = new javax.swing.JLabel();
        player_stack_panel = new RoundedPanel(20);
        player_stack = new javax.swing.JLabel();
        nick_panel = new javax.swing.JPanel();
        player_name = new javax.swing.JLabel();
        utg_icon = new javax.swing.JLabel();
        hands_win = new javax.swing.JLabel();
        botonera = new javax.swing.JPanel();
        player_allin_button = new javax.swing.JButton();
        player_fold_button = new javax.swing.JButton();
        player_check_button = new javax.swing.JButton();
        player_bet_button = new javax.swing.JButton();
        bet_spinner = new javax.swing.JSpinner();
        panel_cartas = new javax.swing.JLayeredPane();
        holeCard1 = new com.tonikelope.coronapoker.Card();
        holeCard2 = new com.tonikelope.coronapoker.Card();
        player_action_panel = new RoundedPanel(20);
        player_action = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new Color(204, 204, 204, 75), Math.round(com.tonikelope.coronapoker.Player.BORDER * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL*com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        setFocusable(false);
        setOpaque(false);

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
        player_pot.setFocusable(false);
        player_pot.setOpaque(true);

        javax.swing.GroupLayout player_pot_panelLayout = new javax.swing.GroupLayout(player_pot_panel);
        player_pot_panel.setLayout(player_pot_panelLayout);
        player_pot_panelLayout.setHorizontalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot)
                .addGap(0, 0, 0))
        );
        player_pot_panelLayout.setVerticalGroup(
            player_pot_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_pot_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_pot, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

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

        javax.swing.GroupLayout player_stack_panelLayout = new javax.swing.GroupLayout(player_stack_panel);
        player_stack_panel.setLayout(player_stack_panelLayout);
        player_stack_panelLayout.setHorizontalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );
        player_stack_panelLayout.setVerticalGroup(
            player_stack_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_stack_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_stack)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout avatar_panelLayout = new javax.swing.GroupLayout(avatar_panel);
        avatar_panel.setLayout(avatar_panelLayout);
        avatar_panelLayout.setHorizontalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(avatar)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_pot_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        avatar_panelLayout.setVerticalGroup(
            avatar_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(avatar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(player_pot_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(avatar_panelLayout.createSequentialGroup()
                .addComponent(player_stack_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(utg_icon)
                .addGap(5, 5, 5)
                .addComponent(hands_win)
                .addGap(0, 0, 0))
        );
        nick_panelLayout.setVerticalGroup(
            nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(nick_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(nick_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(player_name)
                    .addComponent(utg_icon)
                    .addComponent(hands_win))
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout indicadores_arribaLayout = new javax.swing.GroupLayout(indicadores_arriba);
        indicadores_arriba.setLayout(indicadores_arribaLayout);
        indicadores_arribaLayout.setHorizontalGroup(
            indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(indicadores_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(indicadores_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nick_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(avatar_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
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

        panel_cartas.setDoubleBuffered(true);

        panel_cartas.setLayer(holeCard1, javax.swing.JLayeredPane.DEFAULT_LAYER);
        panel_cartas.setLayer(holeCard2, javax.swing.JLayeredPane.DEFAULT_LAYER);

        javax.swing.GroupLayout panel_cartasLayout = new javax.swing.GroupLayout(panel_cartas);
        panel_cartas.setLayout(panel_cartasLayout);
        panel_cartasLayout.setHorizontalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 12, Short.MAX_VALUE)
                .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 12, Short.MAX_VALUE))
        );
        panel_cartasLayout.setVerticalGroup(
            panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_cartasLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_cartasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(holeCard1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(holeCard2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        player_action.setFont(new java.awt.Font("Dialog", 1, 26)); // NOI18N
        player_action.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        player_action.setText("ESCALERA DE COLOR");
        player_action.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5));
        player_action.setDoubleBuffered(true);
        player_action.setFocusable(false);
        player_action.setMinimumSize(new Dimension(Math.round(LocalPlayer.MIN_ACTION_WIDTH*(1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP)), Math.round(LocalPlayer.MIN_ACTION_HEIGHT * (1f + com.tonikelope.coronapoker.GameFrame.ZOOM_LEVEL * com.tonikelope.coronapoker.GameFrame.ZOOM_STEP))));
        player_action.setOpaque(true);

        javax.swing.GroupLayout player_action_panelLayout = new javax.swing.GroupLayout(player_action_panel);
        player_action_panel.setLayout(player_action_panelLayout);
        player_action_panelLayout.setHorizontalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        player_action_panelLayout.setVerticalGroup(
            player_action_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(player_action_panelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(player_action, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(indicadores_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(panel_cartas))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(player_action_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                        .addComponent(panel_cartas))
                    .addComponent(botonera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(player_action_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void player_fold_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player_fold_buttonActionPerformed
        // TODO add your handling code here:

        if (!turno) {

            synchronized (pre_pulsar_lock) {

                if (pre_pulsado == Player.FOLD) {

                    Audio.playWavResource("misc/button_off.wav");

                    this.desPrePulsarBotonAuto(player_fold_button);

                } else {
                    Audio.playWavResource("misc/button_on.wav");

                    this.desPrePulsarAutoTodo();

                    this.prePulsarBotonAuto(player_fold_button, Player.FOLD);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_fold_button.isEnabled()) {

            if (pre_pulsado == Player.FOLD || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_fold_button) || click_recuperacion) {

                if (GameFrame.TEST_MODE || Helpers.float1DSecureCompare(0f, call_required) < 0 || Helpers.mostrarMensajeInformativoSINO(GameFrame.getInstance().getFrame(), "¿SEGURO?") == 0) {

                    Audio.playWavResource("misc/fold.wav");

                    holeCard1.desenfocar();
                    holeCard2.desenfocar();

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        GameFrame.getInstance().getCrupier().soundFold();

                        setDecision(Player.FOLD);

                        finTurno();
                    });

                }

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

                    Helpers.threadRun(() -> {
                        synchronized (GameFrame.getInstance().getCrupier().getLock_mostrar()) {
                            if (GameFrame.getInstance().getCrupier().isShow_time()) {
                                Helpers.threadRun(() -> {
                                    if (GameFrame.getInstance().isPartida_local()) {
                                        GameFrame.getInstance().getCrupier().showAndBroadcastPlayerCards(nickname);
                                    } else {
                                        GameFrame.getInstance().getCrupier().sendGAMECommandToServer("SHOWMYCARDS");
                                        GameFrame.getInstance().getCrupier().setTiempo_pausa(Crupier.PAUSA_ENTRE_MANOS);
                                    }
                                });
                                ArrayList<Card> cartas_jugada = new ArrayList<>(getHoleCards());
                                String hole_cards_string = Card.collection2String(getHoleCards());
                                for (Card carta_comun : GameFrame.getInstance().getCartas_comunes()) {

                                    if (!carta_comun.isTapada()) {
                                        cartas_jugada.add(carta_comun);
                                    }
                                }
                                Hand jugada = new Hand(cartas_jugada);
                                player_action.setForeground(Color.WHITE);
                                setActionBackground(new Color(51, 153, 255));
                                player_action.setText(Translator.translate(" MUESTRAS (") + jugada.getName() + ")");
                                if (GameFrame.SONIDOS_CHORRA && decision == Player.FOLD) {

                                    Audio.playWavResource("misc/showyourcards.wav");

                                }
                                if (!GameFrame.getInstance().getCrupier().getPerdedores().containsKey(GameFrame.getInstance().getLocalPlayer())) {
                                    GameFrame.getInstance().getRegistro().print(nickname + Translator.translate(" MUESTRA (") + hole_cards_string + ") -> " + jugada);
                                }
                                Helpers.translateComponents(botonera, false);
                                Helpers.translateComponents(player_action, false);
                            }
                        }
                    });

                } else if (getDecision() == Player.NODEC) {

                    if (GameFrame.TEST_MODE || this.action_button_armed.get(player_allin_button) || click_recuperacion) {

                        GameFrame.getInstance().getCrupier().setCurrent_local_cinematic_b64(null);

                        Audio.playWavResource("misc/allin.wav");

                        desactivarControles();

                        GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                        if (auto_action != null) {
                            auto_action.stop();
                        }

                        if (hurryup_timer != null) {
                            hurryup_timer.stop();
                        }

                        Init.PLAYING_CINEMATIC = true;

                        Helpers.threadRun(() -> {
                            if (!GameFrame.getInstance().getCrupier().localCinematicAllin()) {
                                GameFrame.getInstance().getCrupier().soundAllin();
                            }
                        });

                        Helpers.threadRun(() -> {
                            setDecision(Player.ALLIN);

                            setBet(stack + bet);

                            finTurno();
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

                    Audio.playWavResource("misc/button_off.wav");

                    this.desPrePulsarBotonAuto(player_check_button);

                } else {

                    Audio.playWavResource("misc/button_on.wav");

                    this.desPrePulsarAutoTodo();

                    this.prePulsarBotonAuto(player_check_button, Player.CHECK);
                }
            }

        } else if (!GameFrame.getInstance().isTimba_pausada() && getDecision() == Player.NODEC && player_check_button.isEnabled()) {

            if (pre_pulsado == Player.CHECK || !GameFrame.CONFIRM_ACTIONS || this.action_button_armed.get(player_check_button) || click_recuperacion) {

                if (Helpers.float1DSecureCompare(this.stack - (GameFrame.getInstance().getCrupier().getApuesta_actual() - this.bet), 0f) == 0) {
                    player_allin_buttonActionPerformed(null);
                } else {

                    if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                        Audio.playWavResource("misc/call.wav");
                    } else {
                        Audio.playWavResource("misc/check.wav");
                    }

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        setBet(GameFrame.getInstance().getCrupier().getApuesta_actual());

                        setDecision(Player.CHECK);

                        finTurno();
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

                    float bet_spinner_val = Helpers.floatClean(((BigDecimal) bet_spinner.getValue()).floatValue());

                    Audio.playWavResource("misc/bet.wav");

                    desactivarControles();

                    GameFrame.getInstance().getBarra_tiempo().setValue(GameFrame.TIEMPO_PENSAR);

                    if (auto_action != null) {
                        auto_action.stop();
                    }

                    if (hurryup_timer != null) {
                        hurryup_timer.stop();
                    }

                    Helpers.threadRun(() -> {
                        if (apuesta_recuperada == null) {

                            setBet(bet_spinner_val + bet + call_required);
                        } else {

                            setBet(apuesta_recuperada);

                            apuesta_recuperada = null;
                        }

                        setDecision(Player.BET);

                        if (GameFrame.SONIDOS_CHORRA && !GameFrame.getInstance().getCrupier().isSincronizando_mano() && GameFrame.getInstance().getCrupier().getConta_raise() > 0 && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {

                            Audio.playWavResource("misc/raise.wav");

                        }

                        finTurno();
                    });
                } else {
                    this.armarBoton(player_bet_button);
                }
            }

        }

    }//GEN-LAST:event_player_bet_buttonActionPerformed

    private void player_nameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_nameMouseClicked
        // TODO add your handling code here:

        if (nickname.equals(GameFrame.getInstance().getCrupier().getBb_nick()) || nickname.equals(GameFrame.getInstance().getCrupier().getSb_nick()) || nickname.equals(GameFrame.getInstance().getCrupier().getDealer_nick())) {

            GameFrame.LOCAL_POSITION_CHIP = !GameFrame.LOCAL_POSITION_CHIP;

            this.refreshPositionChipIcons();

            Helpers.PROPERTIES.setProperty("local_pos_chip", String.valueOf(GameFrame.LOCAL_POSITION_CHIP));

            Helpers.savePropertiesFile();

            Audio.playWavResource(chip_label.isVisible() ? "misc/button_on.wav" : "misc/button_off.wav");
        }
    }//GEN-LAST:event_player_nameMouseClicked

    private void player_stackMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_player_stackMouseClicked
        // TODO add your handling code here:

        if (SwingUtilities.isLeftMouseButton(evt)) {
            if (!player_stack_click) {
                player_stack_click = true;

                player_stack.setText(Helpers.float2String((float) this.buyin));
                setPlayerStackBackground(Color.GRAY);
                player_stack.setForeground(Color.WHITE);

                Helpers.threadRun(() -> {
                    Helpers.pausar(1500);
                    float s = getStack();
                    Helpers.GUIRun(() -> {
                        if (GameFrame.getInstance().getCrupier().getRebuy_now().containsKey(getNickname())) {
                            setPlayerStackBackground(Color.YELLOW);
                            player_stack.setForeground(Color.BLACK);
                            player_stack.setText(Helpers.float2String(stack) + " + " + Helpers.float2String(new Float((int) GameFrame.getInstance().getCrupier().getRebuy_now().get(getNickname()))));

                        } else {

                            if (buyin > GameFrame.BUYIN) {
                                setPlayerStackBackground(Color.CYAN);

                                player_stack.setForeground(Color.BLACK);
                            } else {

                                setPlayerStackBackground(new Color(51, 153, 0));

                                player_stack.setForeground(Color.WHITE);
                            }

                            player_stack.setText(Helpers.float2String(s));
                        }
                    });
                    player_stack_click = false;
                });
            }
        } else {
            GameFrame.getInstance().getRebuy_now_menu().doClick();
        }
    }//GEN-LAST:event_player_stackMouseClicked

    private void avatarMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_avatarMouseClicked
        // TODO add your handling code here:
        if (!GameFrame.getInstance().isPartida_local()) {

            IdenticonDialog identicon = new IdenticonDialog(GameFrame.getInstance().getFrame(), true, player_name.getText(), GameFrame.getInstance().getSala_espera().getLocal_client_aes_key());

            identicon.setLocationRelativeTo(GameFrame.getInstance().getFrame());

            identicon.setVisible(true);
        }
    }//GEN-LAST:event_avatarMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar;
    private javax.swing.JPanel avatar_panel;
    private javax.swing.JSpinner bet_spinner;
    private javax.swing.JPanel botonera;
    private javax.swing.JLabel hands_win;
    private com.tonikelope.coronapoker.Card holeCard1;
    private com.tonikelope.coronapoker.Card holeCard2;
    private javax.swing.JPanel indicadores_arriba;
    private javax.swing.JPanel nick_panel;
    private javax.swing.JLayeredPane panel_cartas;
    private javax.swing.JLabel player_action;
    private javax.swing.JPanel player_action_panel;
    private javax.swing.JButton player_allin_button;
    private javax.swing.JButton player_bet_button;
    private javax.swing.JButton player_check_button;
    private javax.swing.JButton player_fold_button;
    private javax.swing.JLabel player_name;
    private javax.swing.JLabel player_pot;
    private javax.swing.JPanel player_pot_panel;
    private javax.swing.JLabel player_stack;
    private javax.swing.JPanel player_stack_panel;
    private javax.swing.JLabel utg_icon;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setWinner(String msg) {
        this.winner = true;
        this.conta_win++;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.GREEN, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

            setActionBackground(Color.GREEN);
            player_action.setForeground(Color.BLACK);
            player_action.setText(msg);
            setPlayerActionIcon("action/happy.png");

            if (conta_win > 0) {

                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            }
        });
    }

    public void refreshSecPotLabel() {

        if (Helpers.float1DSecureCompare(0f, pagar) < 0 && GameFrame.getInstance().getCrupier().getBote().getSide_pot_count() > 0) {

            Helpers.GUIRun(() -> {
                sec_pot_win_label.setBackground(Color.BLACK);

                sec_pot_win_label.setForeground(Color.WHITE);

                sec_pot_win_label.setSize(player_action.getSize());

                sec_pot_win_label.setPreferredSize(sec_pot_win_label.getSize());

                int pos_x = Math.round((panel_cartas.getWidth() - sec_pot_win_label.getWidth()) / 2);

                int pos_y = Math.round((getHoleCard1().getHeight() - sec_pot_win_label.getHeight()) / 2);

                sec_pot_win_label.setLocation(pos_x, pos_y);

                String[] botes = new String[botes_secundarios.size()];

                int i = 0;

                for (Integer b : botes_secundarios) {
                    botes[i++] = "#" + String.valueOf(b);
                }

                float mibote = last_bote != null ? last_bote : bote;

                sec_pot_win_label.setText(String.join("+", botes) + " = " + Helpers.float2String(pagar) + " (" + Helpers.float2String(pagar - mibote) + ")");

                sec_pot_win_label.setVisible(true);
            });

        }
    }

    @Override
    public void setLoser(String msg) {
        this.loser = true;

        Helpers.GUIRun(() -> {
            setPlayerBorder(Color.RED, Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

            setActionBackground(Color.RED);
            player_action.setForeground(Color.WHITE);

            holeCard1.desenfocar();
            holeCard2.desenfocar();

            player_action.setText(msg);
            setPlayerActionIcon("action/angry.png");
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

    public void setUTG() {

        this.utg = true;

        Helpers.GUIRun(() -> {
            utg_icon.setVisible(true);
        });
    }

    public void setDecision(int dec) {

        this.decision = dec;

        reraise = false;

        switch (dec) {
            case Player.CHECK:

                Helpers.GUIRunAndWait(() -> {
                    if (Helpers.float1DSecureCompare(0f, call_required) < 0) {
                        player_action.setText(ACTIONS_LABELS[dec - 1][1]);
                    } else {
                        player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                    }

                    setPlayerActionIcon("action/up.png");
                });

                break;
            case Player.BET:
                Helpers.GUIRunAndWait(() -> {
                    if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet) < 0 && Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getApuesta_actual()) < 0) {
                        player_action.setText((GameFrame.getInstance().getCrupier().getConta_raise() > 0 ? "RE" : "") + ACTIONS_LABELS[dec - 1][1] + " (+" + Helpers.float2String(bet - GameFrame.getInstance().getCrupier().getApuesta_actual()) + ")");

                        if (GameFrame.getInstance().getCrupier().getConta_raise() > 0) {
                            reraise = true;
                        }
                    } else {
                        player_action.setText(ACTIONS_LABELS[dec - 1][0] + " " + Helpers.float2String(bet));
                    }
                    setPlayerActionIcon("action/bet.png");
                });
                break;
            case Player.ALLIN:
                Helpers.GUIRunAndWait(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), bet + stack) < 0) {
                        player_action.setText(ACTIONS_LABELS[dec - 1][0] + " (+" + Helpers.float2String(bet + stack - GameFrame.getInstance().getCrupier().getApuesta_actual()) + ")");
                    } else {
                        player_action.setText(ACTIONS_LABELS[dec - 1][0]);
                    }
                    setPlayerActionIcon("action/glasses.png");
                });
                break;
            default:
                Helpers.GUIRunAndWait(() -> {
                    setPlayerBorder(ACTIONS_COLORS[dec - 1][0], Math.round(Player.BORDER * (1f + GameFrame.ZOOM_LEVEL * GameFrame.ZOOM_STEP)));

                    player_action.setText(ACTIONS_LABELS[dec - 1][0]);

                    setPlayerActionIcon("action/down.png");
                });
                break;
        }

        Helpers.GUIRunAndWait(() -> {
            if (!reraise) {

                if (dec == Player.CHECK && Helpers.float1DSecureCompare(0f, call_required) == 0) {
                    setActionBackground(new Color(0, 130, 0));
                    player_action.setForeground(Color.WHITE);
                } else {

                    setActionBackground(ACTIONS_COLORS[dec - 1][0]);
                    player_action.setForeground(ACTIONS_COLORS[dec - 1][1]);
                }

                setPlayerPotBackground(ACTIONS_COLORS[dec - 1][0]);
                player_pot.setForeground(ACTIONS_COLORS[dec - 1][1]);
            } else {
                setActionBackground(RERAISE_BACK_COLOR);
                player_action.setForeground(RERAISE_FORE_COLOR);

                setPlayerPotBackground(RERAISE_BACK_COLOR);
                player_pot.setForeground(RERAISE_FORE_COLOR);
            }

            revalidate();
            repaint();
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

    @Override
    public void showCards(String jugada) {
        this.muestra = true;
        Helpers.GUIRun(() -> {
            setActionBackground(new Color(51, 153, 255));
            player_action.setForeground(Color.WHITE);
            player_action.setText("MUESTRA (" + jugada + ")");
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

        if (GameFrame.getInstance().getSala_espera().getAvatar() != null) {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(GameFrame.getInstance().getSala_espera().getAvatar().getAbsolutePath()).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));
        } else {

            avatar = new ImageIcon(Helpers.makeImageRoundedCorner(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(h, h, Image.SCALE_SMOOTH)).getImage(), 20));
        }

        Helpers.GUIRun(() -> {
            getAvatar().setPreferredSize(new Dimension(h, h));

            getAvatar().setIcon(avatar);

            getAvatar().setVisible(true);
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

            getHoleCard1().destapar(false);

            getHoleCard2().destapar(false);
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

            Helpers.GUIRun(() -> {
                setPlayerActionIcon("action/skull.png");
                setOpaque(true);
                setBackground(Color.RED);
            });
        }
    }

    @Override
    public void setPlayerActionIcon(String icon) {

        if (!isTimeout() || "action/timeout.png".equals(icon) || icon == null) {
            if (!"action/timeout.png".equals(icon)) {
                player_action_icon = icon;
            }

            Helpers.GUIRun(() -> {
                player_action.setIcon(icon != null ? new ImageIcon(new ImageIcon(getClass().getResource("/images/" + icon)).getImage().getScaledInstance(Math.round(0.7f * player_action.getHeight()), Math.round(0.7f * player_action.getHeight()), Image.SCALE_SMOOTH)) : null);
                revalidate();
                repaint();
            });
        }
    }

    public void hidePlayerActionIcon() {

        Helpers.GUIRun(() -> {
            player_action.setIcon(null);
        });

    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {
        Helpers.GUIRun(() -> {
            setActionBackground(ganador ? new Color(120, 200, 0) : new Color(230, 70, 0));
            player_action.setForeground(ganador ? Color.BLACK : Color.WHITE);
            player_action.setText(jugada.getName() + (win_per >= 0 ? " (" + win_per + "%)" : " (--%)"));
            setPlayerActionIcon(null);
        });
    }

    @Override
    public void setContaWin(int conta) {
        this.conta_win = conta;

        if (this.conta_win > 0) {
            Helpers.GUIRun(() -> {
                hands_win.setText(String.valueOf(conta_win));
                hands_win.setVisible(true);
            });
        }
    }

    @Override
    public int getContaWin() {
        return this.conta_win;
    }
}
