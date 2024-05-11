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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.AdjustmentEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLEditorKit;
import org.apache.commons.codec.binary.Base64;

/**
 * Appearances can be deceiving...
 *
 * @author tonikelope
 */
public class WaitingRoomFrame extends javax.swing.JFrame {

    public static final int MAX_PARTICIPANTES = 10;
    public static final String MAGIC_BYTES = "5c1f158dd9855cc9";
    public static final int PING_PONG_TIMEOUT = 15000;
    public static final int ASYNC_WAIT_LOCK = 15000;
    public static final int MAX_PING_PONG_ERROR = 3;
    public static final int EC_KEY_LENGTH = 256;
    public static final int GEN_PASS_LENGTH = 10;
    public static final int CLIENT_REC_WAIT = 15;
    public static final int ANTI_FLOOD_CHAT = 1000;
    public static volatile boolean CHAT_GAME_NOTIFICATIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("chat_game_notifications", "true"));
    private static volatile WaitingRoomFrame THIS = null;

    private final File local_avatar;
    private final Map<String, Participant> participantes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Object local_client_socket_lock = new Object();
    private final Object keep_alive_lock = new Object();
    private final Object lock_new_client = new Object();
    private final Object lock_reconnect = new Object();
    private final Object lock_client_reconnect = new Object();
    private final Object lock_client_async_wait = new Object();
    private final boolean server;
    private final String local_nick;
    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> client_threads = new ConcurrentLinkedQueue<>();
    private volatile ServerSocket server_socket = null;
    private volatile SecretKeySpec local_client_aes_key = null;
    private volatile SecretKeySpec local_client_hmac_key = null;
    private volatile SecretKeySpec local_client_hmac_key_orig = null;
    private volatile SecretKeySpec local_client_permutation_key = null;
    private volatile String local_client_permutation_key_hash = null;
    private volatile Socket local_client_socket = null;
    private volatile BufferedReader local_client_buffer_read_is = null;
    private volatile String server_ip_port;
    private volatile String server_nick;
    private volatile Reconnect2ServerDialog reconnect_dialog = null;
    private volatile boolean reconnecting = false;
    private volatile boolean unsecure_server = false;
    private volatile Integer pong;
    private volatile String gameinfo_original = null;
    private volatile String video_chat_link = null;
    private volatile boolean chat_enabled = true;
    private volatile boolean upnp = false;
    private volatile int server_port = 0;
    private volatile boolean booting = false;
    private volatile boolean partida_empezada = false;
    private volatile boolean partida_empezando = false;
    private volatile String password = null;
    private volatile boolean exit = false;
    private volatile StringBuffer chat_text = new StringBuffer();
    private final String background_chat_src;
    private volatile String local_avatar_chat_src;
    private volatile Border chat_scroll_border = null;
    private volatile boolean protect_focus = false;

    public Object getLock_client_async_wait() {
        return lock_client_async_wait;
    }

    public String getLocal_client_permutation_key_hash() {
        return local_client_permutation_key_hash;
    }

    public String getBackground_chat_src() {
        return background_chat_src;
    }

    public JButton getEmoji_button() {
        return emoji_button;
    }

    public String getLocal_nick() {
        return local_nick;
    }

    public StringBuffer getChat_text() {
        return chat_text;
    }

    public JList<String> getConectados() {
        return conectados;
    }

    public void soundIconClick() {
        Helpers.GUIRun(() -> {
            sound_iconMouseClicked(null);
        });
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    public void closeServerSocket() {

        if (server_socket != null) {
            try {
                server_socket.close();
            } catch (Exception ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public void closeClientSocket() {

        if (local_client_socket != null) {
            try {
                local_client_socket.close();
            } catch (Exception ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void resetInstance() {
        THIS.protect_focus = false;
        THIS.setVisible(false);
        THIS.dispose();
        THIS = null;
    }

    public JCheckBox getChat_notifications() {
        return chat_notifications;
    }

    public JLabel getTts_warning() {
        return tts_warning;
    }

    public boolean isChat_enabled() {
        return chat_enabled;
    }

    public void setChat_enabled(boolean chat_enabled) {
        this.chat_enabled = chat_enabled;
    }

    public Map<String, Participant> getParticipantes() {
        return participantes;
    }

    public File getLocal_avatar() {
        return local_avatar;
    }

    public boolean isPartida_empezando() {
        return partida_empezando;
    }

    public String getVideo_chat_link() {
        return video_chat_link;
    }

    public void setVideo_chat_link(String video_chat_link) {

        if (!server && (this.video_chat_link == null || !this.video_chat_link.equals(video_chat_link))) {
            Audio.playWavResource("misc/chat_alert.wav");
        }

        this.video_chat_link = video_chat_link;

        Helpers.GUIRun(() -> {
            video_chat_button.setEnabled(true);
        });
    }

    public boolean isUnsecure_server() {
        return unsecure_server;
    }

    public int getServer_port() {
        return server_port;
    }

    public boolean isUpnp() {
        return upnp;
    }

    public void setUnsecure_server(boolean val) {
        this.unsecure_server = val;

        if (Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("binary_check", "true"))) {

            Helpers.GUIRun(() -> {
                danger_server.setVisible(unsecure_server);
                pack();
            });

            Helpers.threadRun(() -> {
                mostrarMensajeInformativo(THIS, "CUIDADO: el ejecutable del juego del servidor es diferente\n(Es posible que intente hacer trampas con una versión hackeada del juego)");
            });

        }
    }

    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return received_confirmations;
    }

    public SecretKeySpec getLocal_client_hmac_key() {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return local_client_hmac_key;

    }

    public SecretKeySpec getLocal_client_aes_key() {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return local_client_aes_key;

    }

    public BufferedReader getLocal_client_buffer_read_is() {
        return local_client_buffer_read_is;
    }

    public boolean isExit() {
        return exit;
    }

    public JEditorPane getChat() {
        return chat;
    }

    public static WaitingRoomFrame getInstance() {
        return THIS;
    }

    public JLabel getStatus() {
        return status;
    }

    public boolean isReconnecting() {
        return reconnecting;
    }

    public Object getLock_reconnect() {
        return lock_reconnect;
    }

    public File getAvatar() {
        return local_avatar;
    }

    public boolean isServer() {
        return server;
    }

    public ServerSocket getServer_socket() {
        return server_socket;
    }

    public String getServer_nick() {
        return server_nick;
    }

    public Object getLocalClientSocketLock() {
        return local_client_socket_lock;
    }

    private void HTMLEditorKitAppend(String text) {

        Helpers.GUIRun(() -> {
            HTMLEditorKit editor = (HTMLEditorKit) chat.getEditorKit();
            StringReader reader = new StringReader(text);
            try {
                editor.read(reader, chat.getDocument(), chat.getDocument().getLength());
            } catch (Exception ex) {
            }
            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    chat.revalidate();
                    chat.repaint();
                    chat_scroll.revalidate();
                    chat_scroll.repaint();
                });
            });
        });

    }

    public void chatHTMLAppend(String text) {

        chat_text.append(text);

        HTMLEditorKitAppend(txtChat2HTML(text));
    }

    public void chatHTMLAppendNewUser(String nick) {

        String hora = Helpers.getLocalTimeString();

        String avatar_src = this.participantes.get(nick).getAvatar_chat_src();

        HTMLEditorKitAppend("<div align='center' style='margin-top:7px;margin-bottom:7px;'><img id='avatar_" + nick + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick + "&nbsp;<span style='color:green;'>" + Translator.translate("SE UNE A LA TIMBA") + "</span></b>&nbsp;<span style='font-size:0.8em'>(" + hora + ")</span></div>");
    }

    public void chatHTMLAppendExitUser(String nick, String avatar_src) {

        String hora = Helpers.getLocalTimeString();

        HTMLEditorKitAppend("<div align='center' style='margin-top:7px;margin-bottom:7px;'><img id='avatar_" + nick + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick + "&nbsp;<span style='color:red;'>" + Translator.translate("ABANDONA LA TIMBA") + "</span></b>&nbsp;<span style='font-size:0.8em'>(" + hora + ")</span></div>");
    }

    public synchronized String txtChat2HTML(String chat) {

        String html = "";

        String[] lines = chat.split("\n");

        for (String line : lines) {

            String nick = line.replaceAll("^([^:()]+:+).*$", "$1").replaceAll(":$", "");

            String msg = line.replaceAll("^[^:()]+:+[0-9:()]+ *(.*)$", "$1");

            String hora = line.replaceAll("^[^:()]+:+([0-9:()]+) *.*$", "$1");

            String avatar_src = "";

            String align = "";

            String image_align = "";

            if (nick.equals(this.local_nick)) {

                align = "align='right' style='margin-right:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = local_avatar_chat_src;

                image_align = "0.995";

            } else if (this.participantes.containsKey(nick)) {

                align = "align='left' style='margin-left:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = this.participantes.get(nick).getAvatar_chat_src();

                image_align = "0.005";
            } else {
                align = "align='left' style='margin-left:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();

                image_align = "0.005";
            }

            msg = Helpers.escapeHTML(msg);

            msg = msg.replaceAll("https?://([^/]+)[^ \r\n]*", "#171#<a href='$0'><b>$1</b></a>");

            msg = msg.replaceAll("[^@ ]+@[^ @.]+(?:\\.[^.@ ]+)+", "#1215# <i>$0</i>");

            msg = parseImagesChat(msg, image_align, nick.equals(this.local_nick));

            msg = parseEmojiChat(msg);

            msg = parseBBCODEChat(msg);

            html += "<div " + align + "><div style='margin-bottom:4px'><img id='avatar_" + nick + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick + "</b> <span style='font-size:0.8em'>" + hora + "</span></div>" + msg + "</div>";
        }

        return html;

    }

    private String parseBBCODEChat(String message) {

        return message.replaceAll("(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]", "<i>$2</i>").replaceAll("(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]", "<b>$2</b>").replaceAll("(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]", "<span style='color:$2'>$3</span>");
    }

    private String removeBBCODEChat(String message) {
        return message.replaceAll("(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]", "$2").replaceAll("(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]", "$2").replaceAll("(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]", "$3");

    }

    private String parseImagesChat(String message, String align, boolean send) {

        String msg = message;

        Pattern pattern = Pattern.compile("img(s?)://([^ \r\n]+)");

        Matcher matcher = pattern.matcher(message);

        ArrayList<String> lista = new ArrayList<>();

        ArrayList<String> img_src_lista = new ArrayList<>();

        while (matcher.find()) {

            if (!lista.contains(matcher.group(0))) {

                String img_src = "http" + (matcher.groupCount() > 1 ? matcher.group(1) : "") + "://" + matcher.group(matcher.groupCount() > 1 ? 2 : 1);

                try {
                    msg = msg.replaceAll(Pattern.quote(matcher.group(0)), "<tonimg>" + (Base64.encodeBase64String(img_src.getBytes("UTF-8")) + "@" + align) + "</tonimg><img src='" + getClass().getResource("/images/emoji_chat/image_space.png").toExternalForm() + "' />");
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

                lista.add(matcher.group(0));

                if (!send) {

                    img_src_lista.add(img_src);
                }

            }
        }

        if (img_src_lista.size() > 0) {

            Helpers.threadRun(() -> {
                ChatImageDialog.updateHistorialRecibidos(img_src_lista);
            });
        }

        return msg;
    }

    private String removeLinksImagesChat(String message) {
        return message.replaceAll("(?:http|img)s?://[^ \r\n]+", "");
    }

    private String parseEmojiChat(String message) {

        String msg = message;

        Pattern pattern = Pattern.compile("#([0-9]+)#");

        Matcher matcher = pattern.matcher(message);

        ArrayList<Integer> lista = new ArrayList<>();

        while (matcher.find()) {

            try {

                if (!lista.contains(Integer.parseInt(matcher.group(1))) && Integer.parseInt(matcher.group(1)) > 0 && Integer.parseInt(matcher.group(1)) <= EmojiPanel.EMOJI_SRC.size()) {

                    String emoji_src = EmojiPanel.EMOJI_SRC.get(Integer.parseInt(matcher.group(1)) - 1);

                    msg = msg.replaceAll(" ?#" + matcher.group(1) + "# ?", "<span><img align='middle' src='" + emoji_src + "' /></span>&nbsp;");

                    lista.add(Integer.parseInt(matcher.group(1)));
                }
            } catch (Exception ex) {
            }
        }

        return msg;
    }

    private String removeEmojiChat(String message) {

        return message.replaceAll("#[0-9]+#", "");
    }

    public String cleanTTSChatMessage(String msg) {
        return removeEmojiChat(removeLinksImagesChat(removeBBCODEChat(msg))).trim();
    }

    public JTextField getChat_box() {
        return chat_box;
    }

    /**
     * Creates new form SalaEspera
     */
    public WaitingRoomFrame(boolean local, String nick, String servidor_ip_port, File avatar, String pass, boolean use_upnp) {
        THIS = this;
        upnp = use_upnp;
        server = local;
        local_nick = nick;
        server_ip_port = servidor_ip_port;
        local_avatar = avatar;
        password = pass;

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate(" - Sala de espera (") + nick + ")");

        class SendButtonListener implements DocumentListener {

            public void changedUpdate(DocumentEvent e) {

                send_label.setVisible(!chat_box.getText().isBlank());
                max_min_label.setVisible(chat_box.getText().isBlank());
            }

            public void insertUpdate(DocumentEvent e) {
                send_label.setVisible(!chat_box.getText().isBlank());
                max_min_label.setVisible(chat_box.getText().isBlank());
            }

            public void removeUpdate(DocumentEvent e) {
                send_label.setVisible(!chat_box.getText().isBlank());
                max_min_label.setVisible(chat_box.getText().isBlank());
            }
        }

        radar.setEnabled(GameFrame.RADAR_AVAILABLE);

        radar.setToolTipText(Translator.translate(GameFrame.RADAR_AVAILABLE ? "Informes ANTI-TRAMPAS activados" : "Informes ANTI-TRAMPAS desactivados"));

        chat_box.getDocument().addDocumentListener(new SendButtonListener());

        emoji_button.setEnabled(false);

        Helpers.setScaledIconLabel(send_label, getClass().getResource("/images/start.png"), chat_box.getHeight(), chat_box.getHeight());

        Helpers.setScaledIconLabel(max_min_label, getClass().getResource("/images/maximize.png"), chat_box.getHeight(), chat_box.getHeight());

        send_label.setVisible(false);

        chat_scroll_border = chat_scroll.getBorder();

        emoji_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        main_scroll_panel.getVerticalScrollBar().setUnitIncrement(16);

        main_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        emoji_scroll_panel.setVisible(false);

        chat.setEditorKit(new CoronaHTMLEditorKit());

        chat.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {

                Helpers.openBrowserURL(e.getURL().toString());

                chat_box.requestFocus();
            }
        });

        chat_scroll.getVerticalScrollBar().addAdjustmentListener((AdjustmentEvent e) -> {
            if (!chat.hasFocus()) {

                e.getAdjustable().setValue(e.getAdjustable().getMaximum());

            }
        });

        background_chat_src = getClass().getResource("/images/chat_bg.jpg").toExternalForm();
        chat.setText("<html><body style='background-image: url(" + background_chat_src + ")'></body></html>");

        barra.setVisible(false);
        Helpers.barraIndeterminada(barra);
        tts_warning.setVisible(false);
        chat_notifications.setSelected(CHAT_GAME_NOTIFICATIONS);
        chat_notifications.setVisible(false);

        danger_server.setVisible(false);

        if (GameFrame.isRECOVER()) {
            game_info_buyin.setText("(CONTINUANDO TIMBA ANTERIOR)");
            game_info_buyin.setIcon(null);
            game_info_blinds.setVisible(false);
            game_info_hands.setVisible(false);
        }

        if (server) {

            if (!GameFrame.isRECOVER()) {
                game_info_buyin.setToolTipText("Click para actualizar datos de la timba");
                game_info_blinds.setToolTipText("Click para actualizar datos de la timba");
                game_info_hands.setToolTipText("Click para actualizar datos de la timba");

            }

            pass_icon.setVisible(true);

            if (password != null) {
                pass_icon.setToolTipText(password);
            } else {
                pass_icon.setEnabled(false);
            }

        } else {
            pass_icon.setVisible(false);
        }

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        kick_user.setEnabled(false);

        empezar_timba.setEnabled(false);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat_box);

        if (avatar != null) {
            avatar_label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            try {
                ImageIO.write(Helpers.toBufferedImage(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage()), "png", new File(local_avatar.getAbsolutePath() + "_chat"));
                local_avatar_chat_src = new File(local_avatar.getAbsolutePath() + "_chat").toURI().toURL().toExternalForm();

            } catch (IOException ex) {
                local_avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            avatar_label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            local_avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
        }

        avatar_label.setText("");

        status1.setText(server_ip_port);

        DefaultListModel listModel = new DefaultListModel();

        if (server) {

            new_bot_button.setEnabled(true);

            status.setText("Esperando jugadores...");

            gameinfo_original = GameFrame.BUYIN + (GameFrame.REBUY ? "" : "*") + "|" + Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / " + Helpers.float2String(GameFrame.CIEGA_GRANDE) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") : "") + (GameFrame.MANOS != -1 ? "|" + String.valueOf(GameFrame.MANOS) : "");

            if (game_info_buyin.isEnabled() && !GameFrame.isRECOVER()) {

                String[] game_info = gameinfo_original.split("\\|");

                boolean rebuy = !game_info[0].trim().endsWith("*");

                game_info_buyin.setText(Helpers.float2String(Float.parseFloat(game_info[0].replace("*", ""))) + (rebuy ? "" : "*"));

                game_info_blinds.setText(game_info[1]);

                if (game_info.length > 2) {
                    game_info_hands.setText(game_info[2]);
                } else {
                    game_info_hands.setVisible(false);
                }
            }

            participantes.put(local_nick, null);

            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            ParticipantsListLabel label = new ParticipantsListLabel();

            label.setText(local_nick);

            label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));

            if (local_avatar != null) {
                Helpers.setScaledIconLabel(label, local_avatar.getAbsolutePath(), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            } else {
                Helpers.setScaledIconLabel(label, getClass().getResource("/images/avatar_default.png"), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            }

            listModel.addElement(label);
            conectados.setModel(listModel);
            conectados.revalidate();
            conectados.repaint();

        } else {
            video_chat_button.setEnabled(false);
            empezar_timba.setVisible(false);
            new_bot_button.setVisible(false);
            kick_user.setVisible(false);
            chat_box.setEnabled(false);
            emoji_button.setEnabled(false);
            image_button.setEnabled(false);
            max_min_label.setEnabled(false);
            barra.setVisible(true);
            conectados.setModel(listModel);
            conectados.revalidate();
            conectados.repaint();
            game_info_buyin.setToolTipText(null);
            game_info_blinds.setToolTipText(null);
            game_info_hands.setToolTipText(null);
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        Helpers.setScaledIconButton(empezar_timba, getClass().getResource("/images/start.png"), Math.round(empezar_timba.getHeight() * 0.8f), Math.round(empezar_timba.getHeight() * 0.8f));

        Helpers.setScaledIconButton(kick_user, getClass().getResource("/images/kick.png"), kick_user.getHeight(), kick_user.getHeight());

        chat_box.setPreferredSize(new Dimension(Math.round((float) (chat_box.getSize().getWidth() * 0.5f)), (int) chat_box.getSize().getHeight()));

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);

            setPreferredSize(getSize());

            pack();

            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(), (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

        } else {
            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(), (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);
        }

        Audio.muteLoopMp3("misc/background_music.mp3");

        Audio.playLoopMp3Resource("misc/waiting_room.mp3");

        if (server) {
            servidor();
        } else {
            cliente();
        }
    }

    public JScrollPane getEmoji_scroll_panel() {
        return emoji_scroll_panel;
    }

    private void sqlSavePermutationkey() {
        synchronized (GameFrame.SQL_LOCK) {

            String sql = "INSERT INTO permutationkey(hash, key) VALUES (?, ?)";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, this.local_client_permutation_key_hash);

                statement.setString(2, Base64.encodeBase64String(this.local_client_permutation_key.getEncoded()));

                statement.executeUpdate();
            } catch (SQLException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

    }

    private String sqlReadPermutationkey(String hash) {

        synchronized (GameFrame.SQL_LOCK) {

            String ret = null;

            String sql = "SELECT key FROM permutationkey WHERE hash=?";

            try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                statement.setQueryTimeout(30);

                statement.setString(1, hash);

                ResultSet rs = statement.executeQuery();

                if (rs.next()) {
                    ret = rs.getString("key");
                }
            } catch (SQLException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

            return ret;

        }

    }

    public void writeCommandToServer(String command) throws IOException {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        synchronized (getLocalClientSocketLock()) {
            this.local_client_socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
        }
    }

    public void writeCommandFromServer(String command, Socket socket) throws IOException {
        socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
    }

    public String readCommandFromClient(Socket socket, SecretKeySpec key, SecretKeySpec hmac_key) throws KeyException, IOException {

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        return Helpers.decryptCommand(in.readLine(), key, hmac_key);
    }

    public String readCommandFromServer() throws KeyException, IOException {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return Helpers.decryptCommand(this.getLocal_client_buffer_read_is().readLine(), this.getLocal_client_aes_key(), this.getLocal_client_hmac_key());
    }

    //Función AUTO-RECONNECT
    public boolean reconectarCliente() {

        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Intentando reconectar con el servidor...");

        this.reconnecting = true;

        synchronized (getLocalClientSocketLock()) {

            try {

                boolean ok_rec;

                if (!local_client_socket.isClosed()) {
                    try {
                        local_client_socket.shutdownInput();
                        local_client_socket.shutdownOutput();
                        local_client_socket.close();

                    } catch (Exception ex) {
                    }
                }

                local_client_socket = null;

                long start = System.currentTimeMillis();

                ok_rec = false;

                Mac orig_sha256_HMAC = Mac.getInstance("HmacSHA256");

                orig_sha256_HMAC.init(local_client_hmac_key_orig);

                String b64_nick = Base64.encodeBase64String(local_nick.getBytes("UTF-8"));

                String b64_hmac_nick = Base64.encodeBase64String(orig_sha256_HMAC.doFinal(local_nick.getBytes("UTF-8")));

                do {

                    try {

                        String[] server_address = server_ip_port.split(":");

                        local_client_socket = new Socket(server_address[0], Integer.valueOf(server_address[1]));

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "¡Conectado al servidor! Vamos a intercambiar las claves...");

                        //Le mandamos los bytes "mágicos"
                        local_client_socket.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));

                        /* INICIO INTERCAMBIO CLAVES */
                        KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("EC");

                        clientKpairGen.initialize(EC_KEY_LENGTH);

                        KeyPair clientKpair = clientKpairGen.generateKeyPair();

                        KeyAgreement clientKeyAgree = KeyAgreement.getInstance("ECDH");

                        clientKeyAgree.init(clientKpair.getPrivate());

                        byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();

                        DataOutputStream dOut = new DataOutputStream(local_client_socket.getOutputStream());

                        dOut.writeInt(clientPubKeyEnc.length);

                        dOut.write(clientPubKeyEnc);

                        DataInputStream dIn = new DataInputStream(local_client_socket.getInputStream());

                        int length = dIn.readInt();

                        byte[] serverPubKeyEnc = new byte[length];

                        dIn.readFully(serverPubKeyEnc, 0, serverPubKeyEnc.length);

                        KeyFactory clientKeyFac = KeyFactory.getInstance("EC");

                        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(serverPubKeyEnc);

                        PublicKey serverPubKey = clientKeyFac.generatePublic(x509KeySpec);

                        clientKeyAgree.doPhase(serverPubKey, true);

                        byte[] clientSharedSecret = clientKeyAgree.generateSecret();

                        byte[] secret_hash = MessageDigest.getInstance("SHA-512").digest(clientSharedSecret);

                        local_client_aes_key = new SecretKeySpec(secret_hash, 0, 16, "AES");

                        local_client_hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");

                        /* FIN INTERCAMBIO CLAVES */
                        //Le mandamos nuestro nick al server autenticado con la clave HMAC antigua
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Enviando datos de reconexión...");

                        local_client_socket.getOutputStream().write((Helpers.encryptCommand(b64_nick + "#" + AboutDialog.VERSION + "#*#*#" + b64_hmac_nick, local_client_aes_key, local_client_hmac_key) + "\n").getBytes("UTF-8"));

                        local_client_buffer_read_is = new BufferedReader(new InputStreamReader(local_client_socket.getInputStream()));

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "HEMOS CONSEGUIDO RECONECTAR CORRECTAMENTE CON EL SERVIDOR");

                        ok_rec = true;

                    } catch (Exception ex) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "EL SOCKET DE RECONEXIÓN PROVOCÓ UNA EXCEPCIÓN");
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);

                    } finally {

                        if (!ok_rec) {

                            if (local_client_socket != null && !local_client_socket.isClosed()) {

                                try {

                                    local_client_socket.close();

                                } catch (Exception ex) {
                                }

                                local_client_socket = null;
                            }

                            if (!exit && (!WaitingRoomFrame.getInstance().isPartida_empezada() || !GameFrame.getInstance().getLocalPlayer().isExit())) {

                                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT && WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    if (this.reconnect_dialog == null) {

                                        Helpers.GUIRun(() -> {
                                            reconnect_dialog = new Reconnect2ServerDialog(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : THIS, true, server_ip_port);
                                            reconnect_dialog.setLocationRelativeTo(reconnect_dialog.getParent());
                                            reconnect_dialog.setVisible(true);
                                        });

                                    } else {
                                        reconnect_dialog.setReconectar(false);

                                        Helpers.GUIRun(() -> {
                                            reconnect_dialog.reset();
                                            reconnect_dialog.setLocationRelativeTo(reconnect_dialog.getParent());
                                            reconnect_dialog.setVisible(true);
                                        });
                                    }

                                    while (reconnect_dialog == null || !reconnect_dialog.isReconectar()) {
                                        synchronized (this.lock_reconnect) {
                                            try {
                                                this.lock_reconnect.wait(1000);
                                            } catch (InterruptedException ex) {
                                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                            }
                                        }
                                    }

                                    start = System.currentTimeMillis();
                                    server_ip_port = reconnect_dialog.getIp_port().getText().trim();

                                } else {

                                    Helpers.pausar(GameFrame.CLIENT_RECON_ERROR_PAUSE);
                                }

                            }

                        }
                    }

                } while (!exit && !ok_rec && (!WaitingRoomFrame.getInstance().isPartida_empezada() || !GameFrame.getInstance().getLocalPlayer().isExit()));

                if (this.reconnect_dialog != null) {

                    Helpers.GUIRunAndWait(() -> {
                        reconnect_dialog.dispose();
                        reconnect_dialog = null;
                    });
                }

                if (ok_rec) {
                    Audio.playWavResource("misc/yahoo.wav");
                }

                this.reconnecting = false;

                getLocalClientSocketLock().notifyAll();

                return ok_rec;

            } catch (InvalidKeyException | NoSuchAlgorithmException | UnsupportedEncodingException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        return false;

    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par) {

        broadcastASYNCGAMECommandFromServer(command, par, true);

    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();

        for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

            Participant p = entry.getValue();

            if (p != null && !p.isCpu() && p != par && !p.isExit()) {

                pendientes.add(p.getNick());

            }

        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();

            byte[] iv = new byte[16];

            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

                Participant p = entry.getValue();

                if (p != null && !p.isCpu() && pendientes.contains(p.getNick())) {

                    if (!confirmation) {

                        String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                        p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));

                    } else {

                        synchronized (p.getAsync_command_queue()) {
                            p.getAsync_command_queue().add(command);
                            p.getAsync_command_queue().notifyAll();
                        }

                    }

                }
            }

        }
    }

    public JButton getImage_button() {
        return image_button;
    }

    public void sendASYNCGAMECommandFromServer(String command, Participant p) {

        sendASYNCGAMECommandFromServer(command, p, true);
    }

    public void sendASYNCGAMECommandFromServer(String command, Participant p, boolean confirmation) {

        if (!confirmation) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();

            String full_command = "GAME#" + String.valueOf(id) + "#" + command;

            p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), p.getHmac_key()));

        } else {

            synchronized (p.getAsync_command_queue()) {
                p.getAsync_command_queue().add(command);
                p.getAsync_command_queue().notifyAll();
            }

        }

    }

    public JProgressBar getBarra() {
        return barra;
    }

    private void mostrarMensajeInformativo(Container container, String msg) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        Helpers.mostrarMensajeInformativo(container, msg, "center", null, null);

        this.protect_focus = focus_protection;
    }

    private void mostrarMensajeInformativo(Container container, String msg, String align, Integer width) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        Helpers.mostrarMensajeInformativo(container, msg, align, width, null);

        this.protect_focus = focus_protection;
    }

    private int mostrarMensajeInformativoSINO(Container container, String msg) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        int r = Helpers.mostrarMensajeInformativoSINO(container, msg, "center", null, null);

        this.protect_focus = focus_protection;

        return r;
    }

    private int mostrarMensajeInformativoSINO(Container container, String msg, ImageIcon icon) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        int r = Helpers.mostrarMensajeInformativoSINO(container, msg, "center", null, icon);

        this.protect_focus = focus_protection;

        return r;
    }

    private int mostrarMensajeInformativoSINO(Container container, String msg, String align, Integer width) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        int r = Helpers.mostrarMensajeInformativoSINO(container, msg, align, width, null);

        this.protect_focus = focus_protection;

        return r;
    }

    private void mostrarMensajeError(Container container, String msg) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        Helpers.mostrarMensajeError(container, msg, "center", null);

        this.protect_focus = focus_protection;

    }

    private void mostrarMensajeError(Container container, String msg, String align, Integer width) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        Helpers.mostrarMensajeError(container, msg, align, width);

        this.protect_focus = focus_protection;

    }

    private int mostrarMensajeErrorSINO(Container container, String msg) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        int r = Helpers.mostrarMensajeErrorSINO(container, msg, "center", null);

        this.protect_focus = focus_protection;

        return r;
    }

    private int mostrarMensajeErrorSINO(Container container, String msg, String align, Integer width) {

        boolean focus_protection = this.protect_focus;

        this.protect_focus = false;

        int r = Helpers.mostrarMensajeErrorSINO(container, msg, align, width);

        this.protect_focus = focus_protection;

        return r;
    }

    private void cliente() {

        Helpers.threadRun(() -> {
            do {
                Helpers.GUIRun(() -> {
                    status.setForeground(new Color(51, 153, 0));
                    Helpers.barraIndeterminada(barra);
                    status.setText(Translator.translate("Conectando..."));
                });
                booting = true;
                HashMap<String, Integer> last_received = new HashMap<>();
                String recibido;
                String[] partes;
                try {
                    String[] direccion = server_ip_port.split(":");
                    local_client_socket = new Socket(direccion[0], Integer.valueOf(direccion[1]));
                    //Le mandamos los bytes "mágicos"
                    local_client_socket.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));
                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("Intercambio de claves..."));
                    });
                    /* INICIO INTERCAMBIO CLAVES */
                    KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("EC");
                    clientKpairGen.initialize(EC_KEY_LENGTH);
                    KeyPair clientKpair = clientKpairGen.generateKeyPair();
                    KeyAgreement clientKeyAgree = KeyAgreement.getInstance("ECDH");
                    clientKeyAgree.init(clientKpair.getPrivate());
                    byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();
                    DataOutputStream dOut = new DataOutputStream(local_client_socket.getOutputStream());
                    dOut.writeInt(clientPubKeyEnc.length);
                    dOut.write(clientPubKeyEnc);
                    DataInputStream dIn = new DataInputStream(local_client_socket.getInputStream());
                    int length = dIn.readInt();
                    byte[] serverPubKeyEnc = new byte[length];
                    dIn.readFully(serverPubKeyEnc, 0, serverPubKeyEnc.length);
                    KeyFactory clientKeyFac = KeyFactory.getInstance("EC");
                    X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(serverPubKeyEnc);
                    PublicKey serverPubKey = clientKeyFac.generatePublic(x509KeySpec);
                    clientKeyAgree.doPhase(serverPubKey, true);
                    byte[] clientSharedSecret = clientKeyAgree.generateSecret();
                    byte[] secret_hash = MessageDigest.getInstance("SHA-512").digest(clientSharedSecret);
                    local_client_aes_key = new SecretKeySpec(secret_hash, 0, 16, "AES");
                    local_client_hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    local_client_hmac_key_orig = local_client_hmac_key;
                    /* FIN INTERCAMBIO CLAVES */
                    byte[] avatar_bytes = null;
                    if (local_avatar != null && local_avatar.length() > 0) {
                        try (FileInputStream is = new FileInputStream(local_avatar)) {
                            avatar_bytes = is.readAllBytes();
                        }
                    }
                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("Chequeo de integridad..."));
                    });
                    String jar_hmac = Init.coronaHMACJ1(local_client_aes_key.getEncoded(), local_client_hmac_key.getEncoded());
                    //Le mandamos nuestro nick + VERSION + AVATAR + password al server
                    writeCommandToServer(Helpers.encryptCommand(Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + "#" + AboutDialog.VERSION + "@" + jar_hmac + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : "#*") + (password != null ? "#" + Base64.encodeBase64String(password.getBytes("UTF-8")) : "#*"), local_client_aes_key, local_client_hmac_key));
                    local_client_buffer_read_is = new BufferedReader(new InputStreamReader(local_client_socket.getInputStream()));
                    //Leemos la respuesta del server
                    recibido = readCommandFromServer();
                    partes = recibido.split("#");
                    switch (partes[0]) {
                        case "BADVERSION":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("Versión de CoronaPoker incorrecta") + " " + Translator.translate("SE REQUIERE") + " -> " + partes[1]);
                            break;
                        case "YOUARELATE":
                            exit = true;
                            mostrarMensajeError(THIS, "Llegas TARDE. La partida ya ha empezado.");
                            break;
                        case "NOSPACE":
                            exit = true;
                            mostrarMensajeError(THIS, "NO HAY SITIO");
                            break;
                        case "NICKFAIL":
                            exit = true;
                            mostrarMensajeError(THIS, "El nick elegido ya lo está usando otro usuario.");
                            break;
                        case "BADPASSWORD":
                            exit = true;
                            mostrarMensajeError(THIS, "PASSWORD INCORRECTA");
                            break;
                        case "NICKOK":
                            String server_jar_hmac = Init.coronaHMACJ1(Base64.decodeBase64(jar_hmac), local_client_hmac_key.getEncoded());
                            if (!server_jar_hmac.equals(partes[2])) {

                                THIS.setUnsecure_server(true);

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "SERVER GAME BINARY IS MODIFIED (cheating?)");

                            }
                            if ("0".equals(partes[1])) {
                                Helpers.GUIRun(() -> {
                                    pass_icon.setVisible(false);
                                });
                            }
                            gameinfo_original = new String(Base64.decodeBase64(partes[3]), "UTF-8");
                            Helpers.GUIRun(() -> {
                                status.setText(Translator.translate("Recibiendo info del servidor..."));
                                String[] game_info = gameinfo_original.split("\\|");

                                if (game_info[0].trim().matches("[0-9,.*]+")) {
                                    boolean rebuy = !game_info[0].trim().endsWith("*");
                                    game_info_buyin.setText(Helpers.float2String(Float.parseFloat(game_info[0].replace("*", ""))) + (rebuy ? "" : "*"));
                                    game_info_blinds.setText(game_info[1]);

                                    if (game_info.length > 2) {
                                        game_info_hands.setText(game_info[2]);
                                    } else {
                                        game_info_hands.setVisible(false);
                                    }
                                } else {

                                    game_info_blinds.setVisible(false);
                                    game_info_hands.setVisible(false);
                                    game_info_buyin.setIcon(null);
                                }
                            }); //Leemos el nick del server
                            recibido = readCommandFromServer();
                            partes = recibido.split("#");
                            server_nick = new String(Base64.decodeBase64(partes[0]), "UTF-8").trim();
                            //Generamos y guardamos nuestra clave de permutacion para en caso de que la partida se corte el servidor pueda recuperarla
                            try {

                                MessageDigest md = MessageDigest.getInstance("MD5");
                                md.update(local_nick.getBytes("UTF-8"));
                                md.update(server_nick.getBytes("UTF-8"));
                                md.update(local_client_aes_key.getEncoded());
                                md.update(local_client_hmac_key.getEncoded());
                                local_client_permutation_key = new SecretKeySpec(md.digest(), "AES");

                                md = MessageDigest.getInstance("MD5");
                                local_client_permutation_key_hash = Base64.encodeBase64String(md.digest(local_client_permutation_key.getEncoded()));

                                sqlSavePermutationkey();

                            } catch (IOException ex) {
                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }   //Leemos el avatar del server
                            String server_avatar_base64 = partes.length > 1 ? partes[1] : "";
                            File server_avatar = null;
                            try {

                                if (server_avatar_base64.length() > 0) {

                                    int file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                    if (file_id < 0) {
                                        file_id *= -1;
                                    }

                                    server_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + server_nick + "_avatar" + String.valueOf(file_id));

                                    try (FileOutputStream os = new FileOutputStream(server_avatar)) {
                                        os.write(Base64.decodeBase64(server_avatar_base64));
                                    }
                                }

                            } catch (Exception ex) {
                                server_avatar = null;
                            }   //Leemos el contenido del chat
                            recibido = readCommandFromServer();
                            if (!"*".equals(recibido)) {

                                chat_text = new StringBuffer(new String(Base64.decodeBase64(recibido), "UTF-8"));
                            }

                            //Leemos el enlace del videochat (si existe)
                            recibido = readCommandFromServer();
                            if (!"*".equals(recibido)) {
                                String video_chat_link1 = new String(Base64.decodeBase64(recibido), "UTF-8");
                                if (video_chat_link1.toLowerCase().startsWith("http")) {
                                    setVideo_chat_link(video_chat_link1);
                                }
                            }

                            //Leemos si el RADAR está activado
                            recibido = readCommandFromServer();

                            GameFrame.RADAR_AVAILABLE = Boolean.parseBoolean(recibido);

                            if (GameFrame.RADAR_AVAILABLE) {
                                Helpers.threadRun(() -> {
                                    Helpers.mostrarMensajeInformativo(THIS, "El servidor ha activado el RADAR anti-trampas para esta partida.\nCualquier jugador podrá solicitar un informe anti-trampas de otro jugador durante la partida,\nel cual incluye una captura de pantalla del jugador (sin mostrar sus cartas) así como su listado de procesos del sistema.", new ImageIcon(getClass().getResource("/images/shield.png")));
                                });
                            }

                            //Añadimos al servidor
                            nuevoParticipante(server_nick, server_avatar, null, null, null, false, THIS.isUnsecure_server());
                            //Nos añadimos nosotros
                            nuevoParticipante(local_nick, local_avatar, null, null, null, false, false);
                            //Cada X segundos mandamos un comando KEEP ALIVE al server
                            Helpers.threadRun(() -> {
                                while (!exit && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    int ping = Helpers.CSPRNG_GENERATOR.nextInt();

                                    pong = null;

                                    try {

                                        writeCommandToServer("PING#" + String.valueOf(ping));

                                    } catch (IOException ex) {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                    synchronized (keep_alive_lock) {
                                        try {
                                            keep_alive_lock.wait(WaitingRoomFrame.PING_PONG_TIMEOUT);
                                        } catch (InterruptedException ex) {
                                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }

                                    if (!exit && !WaitingRoomFrame.getInstance().isPartida_empezada() && pong != null && ping + 1 != pong) {

                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL SERVIDOR NO RESPONDIÓ EL PING");

                                    }

                                }
                            });
                            Helpers.GUIRunAndWait(() -> {
                                status.setText(Translator.translate("CONECTADO"));
                                status.setIcon(new ImageIcon(getClass().getResource("/images/emoji_chat/1.png")));
                                barra.setVisible(false);
                                chat_box.setEnabled(true);
                                emoji_button.setEnabled(true);
                                image_button.setEnabled(true);
                                max_min_label.setEnabled(true);
                                radar.setEnabled(GameFrame.RADAR_AVAILABLE);
                                radar.setToolTipText(Translator.translate(GameFrame.RADAR_AVAILABLE ? "Informes ANTI-TRAMPAS activados" : "Informes ANTI-TRAMPAS desactivados"));
                            });
                            refreshChatPanel();
                            booting = false;
                            //Nos quedamos en bucle esperando mensajes del server
                            do {
                                try {
                                    recibido = readCommandFromServer();
                                    if (recibido != null) {
                                        String[] partes_comando = recibido.split("#");
                                        switch (partes_comando[0]) {
                                            case "PONG":
                                                pong = Integer.parseInt(partes_comando[1]);
                                                break;
                                            case "PING":
                                                writeCommandToServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                                break;
                                            case "CHAT":
                                                String mensaje;
                                                if (partes_comando.length == 3) {

                                                    mensaje = new String(Base64.decodeBase64(partes_comando[2]), "UTF-8");

                                                } else {
                                                    mensaje = "";
                                                }
                                                recibirMensajeChat(new String(Base64.decodeBase64(partes_comando[1]), "UTF-8"), mensaje);
                                                break;
                                            case "EXIT":
                                                exit = true;
                                                mostrarMensajeError(THIS, "El servidor ha cancelado la timba antes de empezar.");
                                                break;
                                            case "KICKED":
                                                exit = true;
                                                Audio.playWavResource("loser/payaso.wav");
                                                mostrarMensajeInformativo(THIS, "¡A LA PUTA CALLE!");
                                                break;
                                            case "GAME":
                                                //Confirmamos recepción al servidor
                                                String subcomando = partes_comando[2];
                                                int id = Integer.valueOf(partes_comando[1]);
                                                writeCommandToServer("CONF#" + String.valueOf(id + 1) + "#OK");
                                                if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != id) {
                                                    last_received.put(subcomando, id);
                                                    if (isPartida_empezada()) {
                                                        switch (subcomando) {
                                                            case "RADAR":

                                                                if (partes_comando.length == 4) {

                                                                    String requester = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");

                                                                    GameFrame.getInstance().getLocalPlayer().RADAR(requester);

                                                                } else {

                                                                    String suspicious = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");

                                                                    GameFrame.getInstance().getCrupier().saveRADARLog(suspicious, partes_comando[4].equals("*") ? null : Base64.decodeBase64(partes_comando[4]), new String(Base64.decodeBase64(partes_comando[5]), "UTF-8"), Long.parseLong(partes_comando[6]));

                                                                }

                                                                break;
                                                            case "PING":
                                                                break;
                                                            case "IWTSTH":

                                                                if (GameFrame.getInstance().getCrupier().isShow_time()) {

                                                                    GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                                }
                                                                break;
                                                            case "IWTSTHSHOW":

                                                                GameFrame.getInstance().getCrupier().IWTSTH_SHOW(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"), Boolean.parseBoolean(partes_comando[4]));

                                                                break;
                                                            case "IWTSTHRULE":
                                                                Helpers.threadRun(() -> {
                                                                    synchronized (GameFrame.getInstance().getCrupier().getIwtsth_lock()) {
                                                                        GameFrame.IWTSTH_RULE = "1".equals(partes_comando[3]);
                                                                        Helpers.GUIRun(() -> {
                                                                            GameFrame.getInstance().getIwtsth_rule_menu().setSelected(GameFrame.IWTSTH_RULE);
                                                                            Helpers.TapetePopupMenu.IWTSTH_RULE_MENU.setSelected(GameFrame.IWTSTH_RULE);
                                                                        });
                                                                    }
                                                                });
                                                                break;
                                                            case "TIMEOUT":

                                                                Player jugador = GameFrame.getInstance().getCrupier().getNick2player().get(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));

                                                                if (jugador != null) {

                                                                    jugador.setTimeout(true);
                                                                }

                                                                break;
                                                            case "TTS":
                                                                GameFrame.TTS_SERVER = partes_comando[3].equals("1");
                                                                Helpers.GUIRun(() -> {
                                                                    GameFrame.getInstance().getTts_menu().setEnabled(GameFrame.TTS_SERVER);

                                                                    Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setEnabled(GameFrame.TTS_SERVER);

                                                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance().getFrame(), false, GameFrame.TTS_SERVER ? "TTS ACTIVADO POR EL SERVIDOR" : "TTS DESACTIVADO POR EL SERVIDOR", GameFrame.TTS_SERVER ? new Color(0, 130, 0) : Color.RED, Color.WHITE, null, 2000);

                                                                    dialog.setLocation(dialog.getParent().getLocation());

                                                                    dialog.setVisible(true);
                                                                });
                                                                break;
                                                            case "VIDEOCHAT":
                                                                setVideo_chat_link(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));

                                                                break;
                                                            case "PAUSE":
                                                                Helpers.threadRun(() -> {
                                                                    synchronized (GameFrame.getInstance().getLock_pause()) {
                                                                        if (("0".equals(partes_comando[3]) && GameFrame.getInstance().isTimba_pausada()) || ("1".equals(partes_comando[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                                            GameFrame.getInstance().pauseTimba(null);
                                                                        }
                                                                    }
                                                                });
                                                                break;
                                                            case "PERMUTATIONKEY":
                                                                Helpers.threadRun(() -> {
                                                                    String key = sqlReadPermutationkey(partes_comando[3]);

                                                                    GameFrame.getInstance().getCrupier().sendGAMECommandToServer("PERMUTATIONKEY#" + (key != null ? key : "*"));
                                                                });
                                                                break;
                                                            case "SHOWCARDS":
                                                                GameFrame.getInstance().getCrupier().showPlayerCards(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"), partes_comando[4], partes_comando[5]);
                                                                break;
                                                            case "REBUYNOW":
                                                                GameFrame.getInstance().getCrupier().rebuyNow(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"), Integer.parseInt(partes_comando[4]));
                                                                break;
                                                            case "EXIT":
                                                                GameFrame.getInstance().getCrupier().remotePlayerQuit(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                                break;
                                                            case "LASTHAND":

                                                                if (partes_comando[3].equals("0")) {
                                                                    GameFrame.getInstance().getTapete().getCommunityCards().last_hand_off();
                                                                } else {
                                                                    GameFrame.getInstance().getTapete().getCommunityCards().last_hand_on();
                                                                }

                                                                break;
                                                            case "MAXHANDS":

                                                                GameFrame.MANOS = Integer.parseInt(partes_comando[3]);

                                                                GameFrame.getInstance().getCrupier().actualizarContadoresTapete();

                                                                break;
                                                            case "UPDATEBLINDS":

                                                                GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(Float.parseFloat(partes_comando[5]), Float.parseFloat(partes_comando[6]), Integer.parseInt(partes_comando[3]), Integer.parseInt(partes_comando[4]));

                                                                break;
                                                            case "SERVEREXIT":
                                                                exit = true;

                                                                if (!GameFrame.CINEMATICAS) {
                                                                    mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "EL SERVIDOR HA TERMINADO LA TIMBA");
                                                                }
                                                                break;
                                                            default:
                                                                
                                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                                }

                                                                break;
                                                        }
                                                    } else {
                                                        switch (subcomando) {
                                                            case "GAMEINFO":
                                                                String ginfo = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");
                                                                String[] game_info = ginfo.split("\\|");
                                                                Helpers.GUIRun(() -> {
                                                                    if (game_info[0].trim().matches("[0-9,.*]+")) {

                                                                        boolean rebuy = !game_info[0].trim().endsWith("*");
                                                                        game_info_buyin.setText(Helpers.float2String(Float.parseFloat(game_info[0].replace("*", ""))) + (rebuy ? "" : "*"));
                                                                        game_info_blinds.setText(game_info[1]);

                                                                        if (game_info.length > 2) {
                                                                            game_info_hands.setText(game_info[2]);
                                                                        } else {
                                                                            game_info_hands.setVisible(false);
                                                                        }
                                                                    } else {
                                                                        game_info_blinds.setVisible(false);
                                                                        game_info_blinds.setVisible(false);
                                                                        game_info_buyin.setIcon(null);
                                                                    }
                                                                });
                                                                break;
                                                            case "VIDEOCHAT":
                                                                setVideo_chat_link(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                                break;
                                                            case "DELUSER":
                                                                borrarParticipante(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                                break;
                                                            case "NEWUSER":
                                                                Audio.playWavResource("misc/new_user.wav");

                                                                String nick = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");

                                                                File avatar = null;

                                                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                                                if (file_id < 0) {
                                                                    file_id *= -1;
                                                                }

                                                                if (partes_comando.length == 6) {
                                                                    avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));

                                                                    try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                        os.write(Base64.decodeBase64(partes_comando[5]));
                                                                    }
                                                                }

                                                                if (!participantes.containsKey(nick)) {
                                                                    //Añadimos al participante

                                                                    nuevoParticipante(nick, avatar, null, null, null, false, "1".equals(partes_comando[4]));

                                                                }

                                                                break;
                                                            case "USERSLIST":
                                                                String[] current_users_parts = partes_comando[3].split("@");

                                                                for (String user : current_users_parts) {

                                                                    String[] user_parts = user.split("\\|");

                                                                    nick = new String(Base64.decodeBase64(user_parts[0]), "UTF-8");

                                                                    avatar = null;

                                                                    if (user_parts.length == 3) {
                                                                        file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                                                        if (file_id < 0) {
                                                                            file_id *= -1;
                                                                        }

                                                                        avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));

                                                                        try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                            os.write(Base64.decodeBase64(user_parts[2]));
                                                                        }

                                                                    }

                                                                    if (!participantes.containsKey(nick)) {
                                                                        //Añadimos al participante

                                                                        nuevoParticipante(nick, avatar, null, null, null, false, "1".equals(user_parts[1]));

                                                                    }

                                                                }

                                                                break;
                                                            case "INIT":
                                                                Helpers.GUIRun(() -> {
                                                                    setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                                                                    sound_icon.setVisible(false);
                                                                    status.setText(Translator.translate("Inicializando timba..."));
                                                                    status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));
                                                                    barra.setVisible(true);
                                                                });
                                                                GameFrame.BUYIN = Integer.parseInt(partes_comando[3]);
                                                                GameFrame.CIEGA_PEQUEÑA = Float.parseFloat(partes_comando[4]);
                                                                GameFrame.CIEGA_GRANDE = Float.parseFloat(partes_comando[5]);
                                                                String[] ciegas_double = partes_comando[6].split("@");
                                                                GameFrame.CIEGAS_DOUBLE = Integer.parseInt(ciegas_double[0]);
                                                                GameFrame.CIEGAS_DOUBLE_TYPE = Integer.parseInt(ciegas_double[1]);
                                                                GameFrame.RECOVER = Boolean.parseBoolean(partes_comando[7].split("@")[0]);
                                                                GameFrame.UGI = partes_comando[7].split("@")[1];
                                                                GameFrame.REBUY = Boolean.parseBoolean(partes_comando[8]);
                                                                GameFrame.MANOS = Integer.parseInt(partes_comando[9]);

                                                                //Inicializamos partida
                                                                Helpers.GUIRunAndWait(new Runnable() {
                                                                    public void run() {
                                                                        new GameFrame(THIS, local_nick, false);
                                                                    }
                                                                });
                                                                partida_empezada = true;
                                                                GameFrame.getInstance().AJUGAR();
                                                                break;
                                                        }
                                                    }
                                                }
                                                break;
                                            case "CONF":
                                                //Es una confirmación del servidor

                                                WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{server_nick, Integer.parseInt(partes_comando[1])});
                                                synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                                                    WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                                }
                                                break;
                                            default:
                                                break;
                                        }
                                    } else {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL SOCKET RECIBIÓ NULL");
                                    }
                                } catch (Exception ex) {

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EXCEPCION AL LEER DEL SOCKET");

                                    recibido = null;

                                } finally {
                                    if (recibido == null) {
                                        last_received.clear();
                                    }

                                    if (recibido == null && (!exit && (!isPartida_empezada() || !GameFrame.getInstance().getLocalPlayer().isExit())) && !reconectarCliente()) {
                                        exit = true;
                                    }
                                }
                                if (!exit) {
                                    Helpers.pausar(1000);
                                }
                            } while (!exit);
                            break;
                        default:
                            break;
                    }
                } catch (IOException ex) {
                    //Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    mostrarMensajeError(THIS, "ERROR INESPERADO");
                    if (Helpers.OSValidator.isWindows()) {
                        Helpers.restoreWindowsGlobalZoom();
                    }
                    System.exit(1);
                }
                if (WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    GameFrame.getInstance().finTransmision(exit);
                } else if (!exit) {
                    if (local_client_socket == null) {
                        booting = false;
                        Helpers.GUIRunAndWait(() -> {
                            status.setForeground(Color.red);
                            Helpers.resetBarra(barra, CLIENT_REC_WAIT);
                        });
                        for (int i = CLIENT_REC_WAIT; i > 0 && !exit; i--) {
                            int j = i;
                            Helpers.GUIRun(() -> {
                                status.setText(Translator.translate("ERROR (Reconexión en ") + j + Translator.translate(" segs...)"));
                                barra.setValue(j);
                            });
                            if (!exit) {
                                synchronized (lock_client_reconnect) {
                                    try {
                                        lock_client_reconnect.wait(1000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                        }
                    } else {
                        mostrarMensajeError(THIS, "ALGO HA FALLADO. Has perdido la conexión con el servidor.");
                    }
                }
            } while (!exit && local_client_socket == null);
            exit = true;
            synchronized (keep_alive_lock) {
                keep_alive_lock.notifyAll();
            }
            Helpers.GUIRunAndWait(() -> {
                Init.VENTANA_INICIO.setVisible(true);

                dispose();
            });
            Audio.stopLoopMp3("misc/waiting_room.mp3");
            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.unmuteLoopMp3("misc/background_music.mp3");
            }
        });
    }

    private void enviarListaUsuariosActualesAlNuevoUsuario(Participant par) {

        String command = "USERSLIST#";

        for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

            Participant p = entry.getValue();

            try {

                if (p != null && p != par) {

                    command += Base64.encodeBase64String(p.getNick().getBytes("UTF-8")) + "|" + (p.isUnsecure_player() ? "1" : "0");

                    if (p.getAvatar() != null || p.isCpu()) {
                        byte[] avatar_b;

                        try (InputStream is = !p.isCpu() ? new FileInputStream(p.getAvatar()) : WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                            avatar_b = is.readAllBytes();
                        }

                        command += "|" + Base64.encodeBase64String(avatar_b);
                    }

                    command += "@";
                }

            } catch (IOException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        this.sendASYNCGAMECommandFromServer(command, par);

    }

    private void serverSocketHandler(final Socket client_socket) {

        Helpers.threadRun(() -> {
            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "Un cliente intenta conectar...");
            client_threads.add(Thread.currentThread().getId());
            String recibido;
            String[] partes;
            try {
                //Leemos los bytes "mágicos"
                byte[] magic = new byte[Helpers.toByteArray(MAGIC_BYTES).length];
                client_socket.getInputStream().read(magic);
                if (Helpers.toHexString(magic).toLowerCase().equals(MAGIC_BYTES)) {
                    /* INICIO INTERCAMBIO DE CLAVES */
                    DataInputStream dIn = new DataInputStream(client_socket.getInputStream());
                    int length = dIn.readInt();
                    byte[] clientPubKeyEnc = new byte[length];
                    dIn.readFully(clientPubKeyEnc, 0, clientPubKeyEnc.length);
                    KeyFactory serverKeyFac = KeyFactory.getInstance("EC");
                    X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(clientPubKeyEnc);
                    PublicKey clientPubKey = serverKeyFac.generatePublic(x509KeySpec);
                    KeyPairGenerator serverKpairGen = KeyPairGenerator.getInstance("EC");
                    serverKpairGen.initialize(EC_KEY_LENGTH);
                    KeyPair serverKpair = serverKpairGen.generateKeyPair();
                    KeyAgreement serverKeyAgree = KeyAgreement.getInstance("ECDH");
                    serverKeyAgree.init(serverKpair.getPrivate());
                    byte[] serverPubKeyEnc = serverKpair.getPublic().getEncoded();
                    DataOutputStream dOut = new DataOutputStream(client_socket.getOutputStream());
                    dOut.writeInt(serverPubKeyEnc.length);
                    dOut.write(serverPubKeyEnc);
                    serverKeyAgree.doPhase(clientPubKey, true);
                    byte[] serverSharedSecret = serverKeyAgree.generateSecret();
                    byte[] secret_hash = MessageDigest.getInstance("SHA-512").digest(serverSharedSecret);
                    SecretKeySpec aes_key = new SecretKeySpec(secret_hash, 0, 16, "AES");
                    SecretKeySpec hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    String client_jar_hmac = Init.coronaHMACJ1(aes_key.getEncoded(), hmac_key.getEncoded());
                    /* FIN INTERCAMBIO DE CLAVES */
                    //Leemos el nick del usuario
                    recibido = readCommandFromClient(client_socket, aes_key, hmac_key);
                    partes = recibido.split("#");
                    String client_nick = new String(Base64.decodeBase64(partes[0]), "UTF-8");
                    File client_avatar = null;
                    if (partes.length == 5) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Un supuesto cliente quiere reconectar...");
                        if (participantes.containsKey(client_nick)) {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "El cliente existe");
                            Mac orig_sha256_HMAC = Mac.getInstance("HmacSHA256");
                            orig_sha256_HMAC.init(participantes.get(client_nick).getHmac_key_orig());
                            byte[] orig_hmac = orig_sha256_HMAC.doFinal(client_nick.getBytes("UTF-8"));
                            boolean rec_error = true;
                            if (MessageDigest.isEqual(orig_hmac, Base64.decodeBase64(partes[4]))) {

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "El HMAC del cliente es auténtico");

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Reseteando el socket del cliente...");

                                //Es un usuario intentado reconectar
                                if (participantes.get(client_nick).resetSocket(client_socket, aes_key, hmac_key)) {

                                    if (WaitingRoomFrame.getInstance().isPartida_empezada() && GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null && GameFrame.getInstance().getCrupier().getNick2player() != null && GameFrame.getInstance().getCrupier().getNick2player().get(client_nick) != null) {
                                        try {
                                            GameFrame.getInstance().getCrupier().getNick2player().get(client_nick).setTimeout(false);
                                        } catch (Exception ex) {
                                        }
                                    }

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE {0} HA RECONECTADO CORRECTAMENTE.", client_nick);

                                    rec_error = false;

                                } else {

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE {0} NO HA PODIDO RECONECTAR", client_nick);

                                    try {
                                        if (!client_socket.isClosed()) {
                                            client_socket.close();
                                        }
                                    } catch (Exception ex) {
                                    }

                                }

                            } else {

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE {0} NO HA PODIDO RECONECTAR (BAD HMAC)", client_nick);

                                try {
                                    if (!client_socket.isClosed()) {
                                        client_socket.close();
                                    }
                                } catch (Exception ex) {
                                }
                            }
                            if (rec_error) {
                                Helpers.threadRun(() -> {
                                    Helpers.mostrarMensajeError(THIS, Translator.translate("ERROR AL INTENTAR RECONECTAR -> ") + client_nick);
                                });
                            }
                        } else {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "El usuario {0} INTENTA RECONECTAR UNA TIMBA ANTERIOR -> DENEGADO", client_nick);

                            try {
                                if (!client_socket.isClosed()) {
                                    client_socket.close();
                                }
                            } catch (Exception ex) {
                            }
                        }
                    } else if (!partes[1].split("@")[0].equals(AboutDialog.VERSION)) {
                        writeCommandFromServer(Helpers.encryptCommand("BADVERSION#" + AboutDialog.VERSION, aes_key, hmac_key), client_socket);
                    } else if (password != null && ("*".equals(partes[3]) || !password.equals(new String(Base64.decodeBase64(partes[3]), "UTF-8")))) {
                        writeCommandFromServer(Helpers.encryptCommand("BADPASSWORD", aes_key, hmac_key), client_socket);
                    } else if (WaitingRoomFrame.getInstance().isPartida_empezando() || WaitingRoomFrame.getInstance().isPartida_empezada()) {
                        writeCommandFromServer(Helpers.encryptCommand("YOUARELATE", aes_key, hmac_key), client_socket);
                    } else if (participantes.size() == MAX_PARTICIPANTES) {
                        writeCommandFromServer(Helpers.encryptCommand("NOSPACE", aes_key, hmac_key), client_socket);
                    } else if (participantes.containsKey(client_nick)) {
                        writeCommandFromServer(Helpers.encryptCommand("NICKFAIL", aes_key, hmac_key), client_socket);
                    } else {
                        //Procesamos su avatar
                        String client_avatar_base64 = partes[2];
                        try {

                            if (!"*".equals(client_avatar_base64)) {

                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                if (file_id < 0) {
                                    file_id *= -1;
                                }
                                client_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + client_nick + "_avatar" + String.valueOf(file_id));

                                try (FileOutputStream os = new FileOutputStream(client_avatar)) {
                                    os.write(Base64.decodeBase64(client_avatar_base64));
                                }
                            }
                        } catch (Exception ex) {
                            client_avatar = null;
                        }
                        String jar_hmac = Init.coronaHMACJ1(Base64.decodeBase64(client_jar_hmac), hmac_key.getEncoded());
                        writeCommandFromServer(Helpers.encryptCommand("NICKOK#" + (password == null ? "0" : "1") + "#" + jar_hmac + "#" + Base64.encodeBase64String((game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|" + game_info_hands.getText()).getBytes("UTF-8")), aes_key, hmac_key), client_socket);
                        byte[] avatar_bytes = null;
                        if (local_avatar != null && local_avatar.length() > 0) {

                            try (FileInputStream is = new FileInputStream(local_avatar)) {
                                avatar_bytes = is.readAllBytes();
                            }
                        }
                        //Mandamos nuestro nick + avatar
                        writeCommandFromServer(Helpers.encryptCommand(Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : ""), aes_key, hmac_key), client_socket);
                        //Mandamos el contenido del chat
                        writeCommandFromServer(Helpers.encryptCommand(chat_text.toString().isEmpty() ? "*" : Base64.encodeBase64String(chat_text.toString().getBytes("UTF-8")), aes_key, hmac_key), client_socket);
                        //Mandamos el link del videochat
                        writeCommandFromServer(Helpers.encryptCommand(getVideo_chat_link() != null ? Base64.encodeBase64String(getVideo_chat_link().getBytes("UTF-8")) : "*", aes_key, hmac_key), client_socket);
                        //Mandamos si el RADAR está activado
                        writeCommandFromServer(Helpers.encryptCommand(String.valueOf(GameFrame.RADAR_AVAILABLE), aes_key, hmac_key), client_socket);

                        synchronized (lock_new_client) {
                            try {
                                Helpers.GUIRunAndWait(() -> {
                                    empezar_timba.setEnabled(false);
                                    game_info_buyin.setEnabled(false);
                                    game_info_blinds.setEnabled(false);
                                    game_info_hands.setEnabled(false);
                                });
                                if (participantes.size() < MAX_PARTICIPANTES && !WaitingRoomFrame.getInstance().isPartida_empezando() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                                    //Añadimos al participante
                                    nuevoParticipante(client_nick, client_avatar, client_socket, aes_key, hmac_key, false, false);
                                    Audio.playWavResource("misc/new_user.wav");
                                    if (!partes[1].split("@")[1].equals(client_jar_hmac)) {

                                        participantes.get(client_nick).setUnsecure_player(true);

                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "{0} GAME BINARY IS MODIFIED (cheating?)", client_nick);

                                    }
                                    //Mandamos la lista de participantes actuales al nuevo participante
                                    if (participantes.size() > 2) {
                                        enviarListaUsuariosActualesAlNuevoUsuario(participantes.get(client_nick));

                                        //Mandamos el nuevo participante al resto de participantes
                                        String comando = "NEWUSER#" + Base64.encodeBase64String(client_nick.getBytes("UTF-8")) + "#" + (participantes.get(client_nick).isUnsecure_player() ? "1" : "0");

                                        if (client_avatar != null) {

                                            byte[] avatar_b;

                                            try (FileInputStream is = new FileInputStream(client_avatar)) {
                                                avatar_b = is.readAllBytes();
                                            }

                                            comando += "#" + Base64.encodeBase64String(avatar_b);
                                        }

                                        broadcastASYNCGAMECommandFromServer(comando, participantes.get(client_nick));
                                    }
                                    Helpers.GUIRun(() -> {
                                        kick_user.setEnabled(true);
                                        new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                                    });
                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "{0} CONECTADO", client_nick);
                                } else {
                                    try (client_socket) {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "{0} NO PUDO CONECTAR CORRECTAMENTE (PARTIDA LLENA O EMPEZADA)", client_nick);
                                    }
                                }
                            } catch (Exception ex) {
                            } finally {
                                Helpers.GUIRun(() -> {
                                    empezar_timba.setEnabled((participantes.size() > 1));
                                    game_info_buyin.setEnabled(true);
                                    game_info_blinds.setEnabled(true);
                                    game_info_hands.setEnabled(true);
                                });
                            }
                        }
                    }
                } else {
                    try (client_socket) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "BAD MAGIC BYTES FROM CLIENT!");
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            client_threads.remove(Thread.currentThread().getId());
        });

    }

    private void servidor() {

        server_nick = local_nick;

        Helpers.threadRun(() -> {
            while (!exit) {
                booting = true;
                try {
                    String[] direccion = server_ip_port.trim().split(":");
                    server_port = Integer.valueOf(direccion[1]);
                    if (upnp) {
                        String stat = status1.getText();
                        Helpers.GUIRun(() -> {
                            status1.setText(Translator.translate("Probando UPnP..."));
                        });
                        upnp = Helpers.UPnPOpen(server_port);
                        if (upnp) {
                            Helpers.GUIRun(() -> {
                                status1.setForeground(Color.BLUE);
                                status1.setText(Helpers.getMyPublicIP() + ":" + String.valueOf(server_port) + " (UPnP OK)");
                            });
                        } else {
                            Helpers.GUIRun(() -> {
                                status1.setText(stat + " (UPnP ERROR)");
                            });
                            mostrarMensajeError(THIS, "NO HA SIDO POSIBLE MAPEAR AUTOMÁTICAMENTE EL PUERTO USANDO UPnP\n\n(Si quieres compartir la timba por Internet deberás activar UPnP en tu router o mapear el puerto de forma manual)");
                        }
                    }
                    Helpers.PROPERTIES.setProperty("upnp", String.valueOf(upnp));
                    Helpers.savePropertiesFile();
                    booting = false;
                    server_socket = new ServerSocket();
                    server_socket.setReuseAddress(true);
                    server_socket.bind(new InetSocketAddress(server_port));
                    while (!server_socket.isClosed()) {

                        serverSocketHandler(server_socket.accept());

                    }
                } catch (IOException ex) {

                    if (server_socket == null) {

                        exit = true;

                        mostrarMensajeError(THIS, "ALGO HA FALLADO. (Probablemente ya hay una timba creada en el mismo puerto).");
                    }

                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (upnp) {
                Helpers.UPnPClose(server_port);
            }
            Helpers.GUIRun(() -> {
                Init.VENTANA_INICIO.setVisible(true);

                dispose();
            });
            Audio.stopLoopMp3("misc/waiting_room.mp3");
            if (GameFrame.MUSICA_AMBIENTAL) {
                Audio.unmuteLoopMp3("misc/background_music.mp3");
            }
        });
    }

    public void refreshChatPanel() {

        Helpers.threadRun(() -> {
            synchronized (chat_box_panel) {
                Helpers.GUIRun(() -> {
                    chat_box_panel.setVisible(false);
                });
                final String html = "<html><body style='background-image: url(" + background_chat_src + ")'>" + (chat_text.toString().isEmpty() ? "" : txtChat2HTML(chat_text.toString())) + "</body></html>";
                Helpers.GUIRun(() -> {
                    CoronaHTMLEditorKit.USE_GIF_CACHE = true;
                    chat.setText(html);
                    CoronaHTMLEditorKit.USE_GIF_CACHE = false;
                    chat_box_panel.setVisible(true);
                    chat.revalidate();
                    chat.repaint();
                    chat_scroll.revalidate();
                    chat_scroll.repaint();
                });
            }
        });

    }

    public void recibirMensajeChat(String nick, String msg) {

        chatHTMLAppend(nick + ":(" + Helpers.getLocalTimeString() + ") " + msg + "\n");

        Helpers.GUIRun(() -> {
            if (WaitingRoomFrame.getInstance().isPartida_empezada() && !isActive() && WaitingRoomFrame.CHAT_GAME_NOTIFICATIONS) {

                if (msg.startsWith("img://") || msg.startsWith("imgs://")) {

                    try {
                        GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{nick, new URL(msg.replaceAll("^img", "http"))});
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } else {

                    String tts_msg = cleanTTSChatMessage(msg);

                    GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{nick, tts_msg});

                }

                synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {
                    GameFrame.NOTIFY_CHAT_QUEUE.notifyAll();
                }
            }
        });

        if (this.server) {

            byte[] iv = new byte[16];

            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            //Reenviamos el mensaje al resto de participantes
            participantes.entrySet().forEach((entry) -> {
                try {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && !p.getNick().equals(nick)) {

                        String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));

                        p.writeCommandFromServer(Helpers.encryptCommand(comando, p.getAes_key(), iv, p.getHmac_key()));
                    }

                } catch (IOException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
    }

    public boolean isPartida_empezada() {
        return partida_empezada;
    }

    public void enviarMensajeChat(String nick, String msg) {

        Helpers.threadRun(() -> {
            byte[] iv = new byte[16];

            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            if (!server) {
                try {
                    String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));
                    writeCommandToServer(Helpers.encryptCommand(comando, getLocal_client_aes_key(), iv, getLocal_client_hmac_key()));
                } catch (IOException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {

                participantes.entrySet().forEach((entry) -> {
                    try {
                        Participant participante = entry.getValue();
                        if (participante != null && !participante.isCpu()) {
                            String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));
                            participante.writeCommandFromServer(Helpers.encryptCommand(comando, participante.getAes_key(), iv, participante.getHmac_key()));
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            }
        });
    }

    public synchronized void borrarParticipante(String nick) {

        if (this.participantes.containsKey(nick)) {

            Audio.playWavResource("misc/toilet.wav");

            String avatar_src = participantes.get(nick).getAvatar_chat_src();

            participantes.remove(nick);

            Helpers.GUIRun(() -> {
                tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

                DefaultListModel model = (DefaultListModel) conectados.getModel();

                ParticipantsListLabel rem_element = null;

                for (int i = 0; i < model.getSize(); i++) {

                    if (((ParticipantsListLabel) model.getElementAt(i)).getText().equals(nick)) {
                        rem_element = (ParticipantsListLabel) model.getElementAt(i);
                        break;
                    }
                }

                model.removeElement(rem_element);

                conectados.revalidate();

                conectados.repaint();

                if (server && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                    if (participantes.size() < 2) {
                        empezar_timba.setEnabled(false);
                        kick_user.setEnabled(false);
                    }

                    new_bot_button.setEnabled(true);
                }

                chatHTMLAppendExitUser(nick, avatar_src);
            });

            if (this.isServer() && !WaitingRoomFrame.getInstance().isPartida_empezada() && !exit) {

                try {
                    String comando = "DELUSER#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                    this.broadcastASYNCGAMECommandFromServer(comando, participantes.get(nick));
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }

    }

    private synchronized void nuevoParticipante(String nick, File avatar, Socket socket, SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(this, nick, avatar, socket, aes_k, hmac_k, cpu);

        participantes.put(nick, participante);

        participante.setUnsecure_player(unsecure);

        if (socket != null) {

            Helpers.threadRun(participante);

        }

        Helpers.GUIRun(() -> {
            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            ParticipantsListLabel label = new ParticipantsListLabel();

            label.setText(nick);

            label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));

            if (avatar != null) {
                Helpers.setScaledIconLabel(label, avatar.getAbsolutePath(), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            } else {
                Helpers.setScaledIconLabel(label, getClass().getResource((server && cpu) ? "/images/avatar_bot.png" : "/images/avatar_default.png"), NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            }

            ((DefaultListModel) conectados.getModel()).addElement(label);

            conectados.revalidate();

            conectados.repaint();

            if (!nick.equals(server_nick) && !nick.equals(local_nick)) {

                chatHTMLAppendNewUser(nick);

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

        main_scroll_panel = new javax.swing.JScrollPane();
        main_panel = new javax.swing.JPanel();
        panel_arriba = new javax.swing.JPanel();
        status = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        panel_con = new javax.swing.JPanel();
        panel_conectados = new javax.swing.JScrollPane();
        conectados = new javax.swing.JList<>();
        kick_user = new javax.swing.JButton();
        empezar_timba = new javax.swing.JButton();
        barra = new javax.swing.JProgressBar();
        jPanel2 = new javax.swing.JPanel();
        new_bot_button = new javax.swing.JButton();
        game_info_blinds = new javax.swing.JLabel();
        game_info_hands = new javax.swing.JLabel();
        video_chat_button = new javax.swing.JButton();
        logo = new javax.swing.JLabel();
        game_info_buyin = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        pass_icon = new javax.swing.JLabel();
        tot_conectados = new javax.swing.JLabel();
        status1 = new javax.swing.JLabel();
        radar = new javax.swing.JLabel();
        danger_server = new javax.swing.JLabel();
        chat_notifications = new javax.swing.JCheckBox();
        chat_scroll = new javax.swing.JScrollPane();
        chat = new javax.swing.JEditorPane();
        jPanel1 = new javax.swing.JPanel();
        chat_box_panel = new javax.swing.JPanel();
        chat_box = new javax.swing.JTextField();
        emoji_button = new javax.swing.JButton();
        image_button = new javax.swing.JButton();
        send_label = new javax.swing.JLabel();
        max_min_label = new javax.swing.JLabel();
        avatar_label = new javax.swing.JLabel();
        emoji_scroll_panel = new javax.swing.JScrollPane();
        emoji_panel = new com.tonikelope.coronapoker.EmojiPanel();
        tts_warning = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("CoronaPoker - Sala de espera");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                formComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowStateListener(new java.awt.event.WindowStateListener() {
            public void windowStateChanged(java.awt.event.WindowEvent evt) {
                formWindowStateChanged(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowDeactivated(java.awt.event.WindowEvent evt) {
                formWindowDeactivated(evt);
            }
            public void windowDeiconified(java.awt.event.WindowEvent evt) {
                formWindowDeiconified(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        main_scroll_panel.setBorder(null);
        main_scroll_panel.setDoubleBuffered(true);
        main_scroll_panel.setPreferredSize(new java.awt.Dimension(700, 750));

        panel_arriba.setPreferredSize(new java.awt.Dimension(700, 487));

        status.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        status.setForeground(new java.awt.Color(51, 153, 0));
        status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        status.setDoubleBuffered(true);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setDoubleBuffered(true);
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        panel_con.setFocusable(false);
        panel_con.setOpaque(false);

        panel_conectados.setDoubleBuffered(true);
        panel_conectados.setFocusable(false);
        panel_conectados.setOpaque(false);

        conectados.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        conectados.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        conectados.setToolTipText("Participantes conectados");
        conectados.setCellRenderer(new com.tonikelope.coronapoker.ParticipantsListLabel());
        conectados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        conectados.setDoubleBuffered(true);
        conectados.setFocusable(false);
        conectados.setOpaque(false);
        panel_conectados.setViewportView(conectados);

        kick_user.setBackground(new java.awt.Color(255, 0, 0));
        kick_user.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        kick_user.setForeground(new java.awt.Color(255, 255, 255));
        kick_user.setText("Expulsar jugador");
        kick_user.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        kick_user.setDoubleBuffered(true);
        kick_user.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kick_userActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel_conLayout = new javax.swing.GroupLayout(panel_con);
        panel_con.setLayout(panel_conLayout);
        panel_conLayout.setHorizontalGroup(
            panel_conLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_conLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_conLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(kick_user, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_conectados, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)))
        );
        panel_conLayout.setVerticalGroup(
            panel_conLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_conLayout.createSequentialGroup()
                .addComponent(panel_conectados, javax.swing.GroupLayout.PREFERRED_SIZE, 328, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(kick_user)
                .addGap(0, 0, 0))
        );

        empezar_timba.setBackground(new java.awt.Color(0, 130, 0));
        empezar_timba.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        empezar_timba.setForeground(new java.awt.Color(255, 255, 255));
        empezar_timba.setText("¡A JUGAR!");
        empezar_timba.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        empezar_timba.setDoubleBuffered(true);
        empezar_timba.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                empezar_timbaActionPerformed(evt);
            }
        });

        new_bot_button.setBackground(new java.awt.Color(51, 51, 51));
        new_bot_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        new_bot_button.setForeground(new java.awt.Color(255, 255, 255));
        new_bot_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/robot.png"))); // NOI18N
        new_bot_button.setText("AÑADIR BOT");
        new_bot_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        new_bot_button.setDoubleBuffered(true);
        new_bot_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                new_bot_buttonActionPerformed(evt);
            }
        });

        game_info_blinds.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_blinds.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png"))); // NOI18N
        game_info_blinds.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_blinds.setDoubleBuffered(true);
        game_info_blinds.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_info_blindsMouseClicked(evt);
            }
        });

        game_info_hands.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_hands.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        game_info_hands.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_hands.setDoubleBuffered(true);
        game_info_hands.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_info_handsMouseClicked(evt);
            }
        });

        video_chat_button.setBackground(new java.awt.Color(102, 153, 255));
        video_chat_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        video_chat_button.setForeground(new java.awt.Color(255, 255, 255));
        video_chat_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/duo.png"))); // NOI18N
        video_chat_button.setText("Videollamada");
        video_chat_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        video_chat_button.setDoubleBuffered(true);
        video_chat_button.setFocusable(false);
        video_chat_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                video_chat_buttonActionPerformed(evt);
            }
        });

        logo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        logo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        logo.setDoubleBuffered(true);
        logo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                logoMouseClicked(evt);
            }
        });

        game_info_buyin.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_buyin.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png"))); // NOI18N
        game_info_buyin.setText(" ");
        game_info_buyin.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_buyin.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_info_buyinMouseClicked(evt);
            }
        });

        pass_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lock.png"))); // NOI18N
        pass_icon.setToolTipText("Click para gestionar contraseña");
        pass_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pass_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pass_iconMouseClicked(evt);
            }
        });

        tot_conectados.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        tot_conectados.setForeground(new java.awt.Color(0, 102, 255));
        tot_conectados.setText("0/10");

        status1.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        status1.setText("1.1.1.1");
        status1.setToolTipText("Click para obtener datos de conexión");
        status1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        status1.setDoubleBuffered(true);
        status1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                status1MouseClicked(evt);
            }
        });

        radar.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/shield.png")).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)));
        radar.setDoubleBuffered(true);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(radar)
                .addGap(0, 0, 0)
                .addComponent(pass_icon)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(status1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tot_conectados)
                .addGap(0, 0, 0))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(status1, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(radar))
                    .addComponent(tot_conectados)
                    .addComponent(pass_icon, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(video_chat_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(new_bot_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(logo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(game_info_buyin)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(game_info_blinds)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(game_info_hands))))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(logo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(game_info_blinds)
                    .addComponent(game_info_hands, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(game_info_buyin))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(new_bot_button, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(video_chat_button)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout panel_arribaLayout = new javax.swing.GroupLayout(panel_arriba);
        panel_arriba.setLayout(panel_arribaLayout);
        panel_arribaLayout.setHorizontalGroup(
            panel_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_arribaLayout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(panel_con, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(empezar_timba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(panel_arribaLayout.createSequentialGroup()
                .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        panel_arribaLayout.setVerticalGroup(
            panel_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_arribaLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(panel_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(panel_con, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(panel_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(empezar_timba, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        danger_server.setBackground(new java.awt.Color(255, 0, 0));
        danger_server.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        danger_server.setForeground(new java.awt.Color(255, 255, 255));
        danger_server.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger_server.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/danger.png"))); // NOI18N
        danger_server.setText("POSIBLE SERVIDOR TRAMPOSO");
        danger_server.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        danger_server.setOpaque(true);

        chat_notifications.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        chat_notifications.setText("Notificaciones del chat durante el juego");
        chat_notifications.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat_notifications.setDoubleBuffered(true);
        chat_notifications.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_notificationsActionPerformed(evt);
            }
        });

        chat_scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chat_scroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        chat_scroll.setDoubleBuffered(true);

        chat.setEditable(false);
        chat.setBorder(null);
        chat.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        chat.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat.setDoubleBuffered(true);
        chat.setFocusable(false);
        chat.addCaretListener(new javax.swing.event.CaretListener() {
            public void caretUpdate(javax.swing.event.CaretEvent evt) {
                chatCaretUpdate(evt);
            }
        });
        chat.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chatFocusLost(evt);
            }
        });
        chat.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                chatMouseClicked(evt);
            }
        });
        chat_scroll.setViewportView(chat);

        chat_box.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        chat_box.setDoubleBuffered(true);
        chat_box.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_boxActionPerformed(evt);
            }
        });

        emoji_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1.png"))); // NOI18N
        emoji_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        emoji_button.setDoubleBuffered(true);
        emoji_button.setMargin(new java.awt.Insets(2, 2, 2, 2));
        emoji_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                emoji_buttonActionPerformed(evt);
            }
        });

        image_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/chat_image.png"))); // NOI18N
        image_button.setToolTipText("Enviar imagen");
        image_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        image_button.setDoubleBuffered(true);
        image_button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        image_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                image_buttonActionPerformed(evt);
            }
        });

        send_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        send_label.setDoubleBuffered(true);
        send_label.setFocusable(false);
        send_label.setRequestFocusEnabled(false);
        send_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                send_labelMouseClicked(evt);
            }
        });

        max_min_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        max_min_label.setDoubleBuffered(true);
        max_min_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                max_min_labelMouseClicked(evt);
            }
        });

        avatar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        avatar_label.setText("Toni");
        avatar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        avatar_label.setDoubleBuffered(true);

        javax.swing.GroupLayout chat_box_panelLayout = new javax.swing.GroupLayout(chat_box_panel);
        chat_box_panel.setLayout(chat_box_panelLayout);
        chat_box_panelLayout.setHorizontalGroup(
            chat_box_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(chat_box_panelLayout.createSequentialGroup()
                .addComponent(avatar_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(emoji_button)
                .addGap(0, 0, 0)
                .addComponent(image_button)
                .addGap(0, 0, 0)
                .addComponent(chat_box)
                .addGap(0, 0, 0)
                .addComponent(send_label)
                .addGap(0, 0, 0)
                .addComponent(max_min_label))
        );
        chat_box_panelLayout.setVerticalGroup(
            chat_box_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(emoji_button, javax.swing.GroupLayout.DEFAULT_SIZE, 43, Short.MAX_VALUE)
            .addComponent(image_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(chat_box)
            .addGroup(chat_box_panelLayout.createSequentialGroup()
                .addGroup(chat_box_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(send_label)
                    .addComponent(max_min_label))
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(avatar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        emoji_scroll_panel.setBorder(null);
        emoji_scroll_panel.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        emoji_scroll_panel.setDoubleBuffered(true);
        emoji_scroll_panel.setFocusable(false);
        emoji_scroll_panel.setRequestFocusEnabled(false);
        emoji_scroll_panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                emoji_scroll_panelComponentHidden(evt);
            }
        });
        emoji_scroll_panel.setViewportView(emoji_panel);

        tts_warning.setFont(new java.awt.Font("Dialog", 2, 10)); // NOI18N
        tts_warning.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tts_warning.setText("Aviso: la privacidad del CHAT no está garantizada si algún jugador usa la función de voz TTS (click para más info).");
        tts_warning.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        tts_warning.setDoubleBuffered(true);
        tts_warning.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tts_warningMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(emoji_scroll_panel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(tts_warning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(chat_box_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(chat_box_panel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(emoji_scroll_panel, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tts_warning))
        );

        javax.swing.GroupLayout main_panelLayout = new javax.swing.GroupLayout(main_panel);
        main_panel.setLayout(main_panelLayout);
        main_panelLayout.setHorizontalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(main_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(danger_server, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
                    .addComponent(chat_notifications, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(chat_scroll, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        main_panelLayout.setVerticalGroup(
            main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(main_panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(danger_server)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panel_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chat_notifications)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chat_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        main_scroll_panel.setViewportView(main_panel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(main_scroll_panel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(main_scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, 784, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void kick_userActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kick_userActionPerformed

        // TODO add your handling code here:
        if (conectados.getSelectedIndex() != -1) {

            String expulsado = ((JLabel) ((DefaultListModel) conectados.getModel()).get(conectados.getSelectedIndex())).getText();

            if (!expulsado.equals(local_nick)) {

                //Cambiamos la contraseña por una aleatoria
                if (password != null && !participantes.get(expulsado).isCpu()) {
                    password = Helpers.genRandomString(password.length());

                }

                kick_user.setEnabled(false);

                Helpers.threadRun(() -> {
                    try {

                        participantes.get(expulsado).setExit(true);

                        if (!participantes.get(expulsado).isCpu()) {

                            String comando = "KICKED#" + Base64.encodeBase64String(expulsado.getBytes("UTF-8"));
                            participantes.get(expulsado).writeCommandFromServer(Helpers.encryptCommand(comando, participantes.get(expulsado).getAes_key(), participantes.get(expulsado).getHmac_key()));
                        }

                        participantes.get(expulsado).exitAndCloseSocket();

                        borrarParticipante(expulsado);

                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRun(() -> {
                        kick_user.setEnabled(participantes.size() > 1);

                        if (password != null) {
                            pass_icon.setToolTipText(password);

                        }

                        chat_box.requestFocus();
                    });
                    if (password != null && !participantes.get(expulsado).isCpu()) {
                        Helpers.copyTextToClipboard(password);
                        mostrarMensajeInformativo(THIS, "NUEVA PASSWORD COPIADA EN EL PORTAPAPELES");
                    }
                });
            }
        } else {

            mostrarMensajeError(THIS, "Tienes que seleccionar algún participante antes");
            chat_box.requestFocus();
        }

    }//GEN-LAST:event_kick_userActionPerformed

    private void empezar_timbaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_empezar_timbaActionPerformed
        // TODO add your handling code here:

        if (mostrarMensajeInformativoSINO(THIS, "¿SEGURO QUE QUIERES EMPEZAR YA?") == 0 && participantes.size() >= 2 && !WaitingRoomFrame.getInstance().isPartida_empezada() && !WaitingRoomFrame.getInstance().isPartida_empezando()) {

            String missing_players = "";

            if (GameFrame.RECOVER) {

                int game_id = GameFrame.RECOVER_ID;

                String sql = "SELECT preflop_players as PLAYERS FROM hand WHERE hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand where hand.id_game=?)";

                try (PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql)) {
                    statement.setQueryTimeout(30);

                    statement.setInt(1, game_id);

                    statement.setInt(2, game_id);

                    ResultSet rs = statement.executeQuery();

                    if (rs.next()) {

                        String datos = rs.getString("PLAYERS");

                        String[] partes = datos.split("#");

                        for (String player_data : partes) {

                            partes = player_data.split("\\|");

                            String nick = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                            if (!"".equals(nick) && !participantes.containsKey(nick)) {
                                missing_players += nick + "\n\n";
                            }
                        }
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

            }

            boolean vamos = ("".equals(missing_players) || mostrarMensajeInformativoSINO(this, missing_players + Translator.translate("Hay jugadores de la timba anterior que no se han vuelto a conectar.\n(Si no se conectan no se podrá recuperar la última mano en curso).\n\n¿EMPEZAMOS YA?")) == 0);

            if (vamos) {

                partida_empezando = true;

                setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                this.empezar_timba.setEnabled(false);
                this.empezar_timba.setVisible(false);
                this.new_bot_button.setEnabled(false);
                this.new_bot_button.setVisible(false);
                game_info_buyin.setToolTipText(null);
                game_info_blinds.setToolTipText(null);
                game_info_hands.setToolTipText(null);
                this.kick_user.setEnabled(false);
                this.kick_user.setVisible(false);
                this.sound_icon.setVisible(false);
                this.status.setText(Translator.translate("Inicializando timba..."));
                this.barra.setVisible(true);
                status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));

                Helpers.threadRun(() -> {
                    synchronized (lock_new_client) {

                        boolean ocupados;

                        do {

                            ocupados = false;

                            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

                                Participant p = entry.getValue();

                                if (p != null && !p.isCpu()) {

                                    if (!p.getAsync_command_queue().isEmpty()) {

                                        ocupados = true;

                                        p.setAsync_wait(true);

                                    } else {

                                        p.setAsync_wait(false);
                                    }

                                }

                            }

                            if (ocupados) {

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Hay algun participante con comandos sin confirmar. NO podemos empezar aún...");

                                synchronized (lock_client_async_wait) {
                                    try {
                                        lock_client_async_wait.wait(ASYNC_WAIT_LOCK);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                        } while (ocupados);

                        //Inicializamos partida
                        Helpers.GUIRunAndWait(new Runnable() {
                            public void run() {
                                new GameFrame(THIS, local_nick, true);

                            }
                        });

                        partida_empezada = true;

                        GameFrame.getInstance().AJUGAR();
                    }
                });
            }
        } else {

            chat_box.requestFocus();
        }

    }//GEN-LAST:event_empezar_timbaActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        protect_focus = false;

        if (!barra.isVisible() || !booting) {

            if (!booting && client_threads.isEmpty() && !partida_empezando) {

                if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {

                    if (exit || reconnecting) {

                        if (mostrarMensajeInformativoSINO(THIS, "¿FORZAR CIERRE?", new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
                            exit = true;
                            Helpers.savePropertiesFile();
                            if (Helpers.OSValidator.isWindows()) {
                                Helpers.restoreWindowsGlobalZoom();
                            }
                            System.exit(1);
                        }

                    } else if (mostrarMensajeInformativoSINO(THIS, "¿SEGURO QUE QUIERES SALIR AHORA?", new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {

                        exit = true;

                        Helpers.threadRun(() -> {
                            if (isServer()) {

                                participantes.entrySet().forEach((entry) -> {

                                    Participant p = entry.getValue();

                                    if (p != null) {

                                        p.exitAndCloseSocket();
                                    }

                                });

                                closeServerSocket();

                            } else if (local_client_socket != null && !reconnecting) {

                                try {
                                    writeCommandToServer(Helpers.encryptCommand("EXIT", getLocal_client_aes_key(), getLocal_client_hmac_key()));
                                    local_client_socket.close();
                                } catch (Exception ex) {
                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        });
                    }

                } else {
                    setVisible(false);
                }

            } else if (mostrarMensajeInformativoSINO(THIS, "¿FORZAR CIERRE?", new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
                exit = true;
                Helpers.savePropertiesFile();
                if (Helpers.OSValidator.isWindows()) {
                    Helpers.restoreWindowsGlobalZoom();
                }
                System.exit(1);
            }

            synchronized (lock_client_reconnect) {
                lock_client_reconnect.notifyAll();
            }
        }

    }//GEN-LAST:event_formWindowClosing

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoMouseClicked
        // TODO add your handling code here:

        boolean auto_f = protect_focus;

        protect_focus = false;

        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);

        protect_focus = auto_f;
    }//GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_new_bot_buttonActionPerformed

        if (participantes.size() < MAX_PARTICIPANTES) {

            new_bot_button.setEnabled(false);

            Audio.playWavResource("misc/laser.wav");

            Helpers.threadRun(() -> {
                try {
                    // TODO add your handling code here:
                    String bot_nick;
                    int conta_bot = 0;
                    do {
                        conta_bot++;

                        bot_nick = "CoronaBot$" + String.valueOf(conta_bot);

                    } while (participantes.get(bot_nick) != null);
                    //Mandamos el nuevo participante al resto de participantes
                    String comando = "NEWUSER#" + Base64.encodeBase64String(bot_nick.getBytes("UTF-8")) + "#0";
                    byte[] avatar_b = null;
                    try (InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                        avatar_b = is.readAllBytes();
                    } catch (IOException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    comando += "#" + Base64.encodeBase64String(avatar_b);
                    synchronized (lock_new_client) {
                        nuevoParticipante(bot_nick, null, null, null, null, true, false);
                        broadcastASYNCGAMECommandFromServer(comando, participantes.get(bot_nick));
                        Helpers.GUIRun(() -> {
                            empezar_timba.setEnabled(true);
                            kick_user.setEnabled(true);
                            new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);

                            chat_box.requestFocus();
                        });
                    }
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
    }//GEN-LAST:event_new_bot_buttonActionPerformed

    private void video_chat_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_video_chat_buttonActionPerformed
        // TODO add your handling code here:

        boolean auto_f = protect_focus;

        protect_focus = false;

        QRChatDialog chat_dialog = new QRChatDialog(this, true, this.getVideo_chat_link(), server);

        chat_dialog.setLocationRelativeTo(this);

        chat_dialog.setVisible(true);

        protect_focus = auto_f;

        if (server && !chat_dialog.isCancel() && chat_dialog.getLink() != null) {

            this.setVideo_chat_link(chat_dialog.getLink());

            try {

                if (isPartida_empezada()) {

                    GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("VIDEOCHAT#" + Base64.encodeBase64String(this.getVideo_chat_link().getBytes("UTF-8")), null);

                } else {

                    broadcastASYNCGAMECommandFromServer("VIDEOCHAT#" + Base64.encodeBase64String(this.getVideo_chat_link().getBytes("UTF-8")), null);

                }

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }

            String chat_msg = Translator.translate("SE HA ACTUALIZADO EL ENLACE DEL VIDEOCHAT");

            chatHTMLAppend(local_nick + ":(" + Helpers.getLocalTimeString() + ") " + chat_msg + "\n");

            enviarMensajeChat(local_nick, chat_msg);
        }

        chat_box.requestFocus();
    }//GEN-LAST:event_video_chat_buttonActionPerformed

    private void pass_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pass_iconMouseClicked
        // TODO add your handling code here:

        if (server && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
            if (mostrarMensajeInformativoSINO(this, Translator.translate("¿GENERAR CONTRASEÑA NUEVA?")) == 0) {
                password = Helpers.genRandomString(GEN_PASS_LENGTH);
                pass_icon.setToolTipText(password);
            }

            if (password != null) {
                pass_icon.setEnabled(true);
                Helpers.copyTextToClipboard(password);
                mostrarMensajeInformativo(this, Translator.translate("PASSWORD COPIADA EN EL PORTAPAPELES"));
            }
        }
    }//GEN-LAST:event_pass_iconMouseClicked

    private void tts_warningMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tts_warningMouseClicked
        // TODO add your handling code here:

        mostrarMensajeInformativo(this, "Aunque CoronaPoker usa cifrado extremo a extremo en todas las comunicaciones, el chat de voz utiliza APIs externas TTS para convertir el texto en audio, por lo que los mensajes enviados a esos servidores podrían ser (en teoría) leidos por terceros.\n\nPOR FAVOR, TENLO EN CUENTA A LA HORA DE USAR EL CHAT", "justify", (int) Math.round(getWidth() * 0.8f));
    }//GEN-LAST:event_tts_warningMouseClicked

    private void status1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_status1MouseClicked

        if (server) {
            // TODO add your handling code here:

            Helpers.copyTextToClipboard("[CoronaPoker] INTERNET -> " + Helpers.getMyPublicIP() + ":" + String.valueOf(server_socket.getLocalPort()) + "\n\nLAN -> " + Helpers.getMyLocalIP() + ":" + String.valueOf(server_socket.getLocalPort()));
            mostrarMensajeInformativo(this, Translator.translate("DATOS DE CONEXIÓN COPIADOS EN EL PORTAPAPELES"));
        }
    }//GEN-LAST:event_status1MouseClicked

    private void game_info_buyinMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_buyinMouseClicked
        // TODO add your handling code here:

        if (server && !GameFrame.isRECOVER() && !isPartida_empezada() && !isPartida_empezando() && game_info_buyin.isEnabled()) {

            game_info_buyin.setEnabled(false);

            game_info_blinds.setEnabled(false);

            game_info_hands.setEnabled(false);

            NewGameDialog dialog = new NewGameDialog(this, true);

            dialog.setLocationRelativeTo(dialog.getParent());

            dialog.setVisible(true);

            if (dialog.isDialog_ok()) {

                game_info_buyin.setText(Helpers.float2String((float) GameFrame.BUYIN) + (GameFrame.REBUY ? "" : "*"));

                game_info_blinds.setText(Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / " + Helpers.float2String(GameFrame.CIEGA_GRANDE) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") : ""));

                game_info_hands.setText(GameFrame.MANOS != -1 ? String.valueOf(GameFrame.MANOS) : "");

                game_info_hands.setVisible(game_info_hands.getText() != "");

                Helpers.threadRun(() -> {
                    try {
                        broadcastASYNCGAMECommandFromServer("GAMEINFO#" + Base64.encodeBase64String((game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|" + game_info_hands.getText()).getBytes("UTF-8")), null);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRun(() -> {
                        game_info_buyin.setEnabled(true);
                        game_info_blinds.setEnabled(true);
                        game_info_hands.setEnabled(true);
                    });
                });

                pack();

                Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(), (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

            } else {
                game_info_buyin.setEnabled(true);
                game_info_blinds.setEnabled(true);
                game_info_hands.setEnabled(true);
            }

        }

    }//GEN-LAST:event_game_info_buyinMouseClicked

    private void chat_notificationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_notificationsActionPerformed
        // TODO add your handling code here:

        CHAT_GAME_NOTIFICATIONS = chat_notifications.isSelected();

        Helpers.PROPERTIES.setProperty("chat_game_notifications", String.valueOf(CHAT_GAME_NOTIFICATIONS));

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_chat_notificationsActionPerformed

    private void emoji_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_emoji_buttonActionPerformed
        // TODO add your handling code here:

        emoji_scroll_panel.getHorizontalScrollBar().setValue(0);

        emoji_scroll_panel.setVisible(!emoji_scroll_panel.isVisible());

        chat_box.requestFocus();

        revalidate();

        repaint();

        Helpers.threadRun(() -> {
            Helpers.GUIRun(() -> {
                main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
            });
        });
    }//GEN-LAST:event_emoji_buttonActionPerformed

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_boxActionPerformed
        // TODO add your handling code here:

        String mensaje = chat_box.getText().trim();

        if (chat_enabled && mensaje.length() > 0) {

            chatHTMLAppend(local_nick + ":(" + Helpers.getLocalTimeString() + ") " + mensaje.replaceAll("(?i)img(s?)://", "http$1://") + "\n");

            this.enviarMensajeChat(local_nick, mensaje);

            this.chat_box.setText("");

            if (emoji_scroll_panel.isVisible()) {

                emoji_scroll_panel.setVisible(false);

                revalidate();

                repaint();
            }

            chat_enabled = false;

            Helpers.threadRun(() -> {
                Helpers.pausar(ANTI_FLOOD_CHAT);
                Helpers.GUIRun(() -> {
                    chat_enabled = true;
                });
            });
        }
    }//GEN-LAST:event_chat_boxActionPerformed

    private void image_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_image_buttonActionPerformed
        // TODO add your handling code here:
        boolean auto_f = protect_focus;

        protect_focus = false;
        ChatImageDialog chat_image_dialog = new ChatImageDialog(this, true, (int) Math.round(this.getHeight() * 0.9f));
        chat_image_dialog.setLocationRelativeTo(this);
        chat_image_dialog.setVisible(true);

        protect_focus = auto_f;

    }//GEN-LAST:event_image_buttonActionPerformed

    private void chatFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chatFocusLost
        // TODO add your handling code here:
        this.chat_scroll.getVerticalScrollBar().setValue(this.chat_scroll.getVerticalScrollBar().getMaximum());
        this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.chat_scroll.setBorder(chat_scroll_border);
        ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.UPDATE_WHEN_ON_EDT);
        chat.setFocusable(false);
    }//GEN-LAST:event_chatFocusLost

    private void emoji_scroll_panelComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_emoji_scroll_panelComponentHidden
        // TODO add your handling code here:
        emoji_panel.refreshEmojiHistory();
    }//GEN-LAST:event_emoji_scroll_panelComponentHidden

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:
        chat_box.requestFocus();
    }//GEN-LAST:event_formWindowOpened

    private void send_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_send_labelMouseClicked
        // TODO add your handling code here:
        chat_boxActionPerformed(null);
    }//GEN-LAST:event_send_labelMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        Helpers.setScaledIconLabel(sound_icon, getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        if (!chat_text.toString().isEmpty() && !protect_focus) {
            refreshChatPanel();
        }

        protect_focus = isPartida_empezada();

        setAlwaysOnTop(protect_focus);

        main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());

        chat_box.requestFocus();

        revalidate();

        repaint();
    }//GEN-LAST:event_formComponentShown

    private void max_min_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_max_min_labelMouseClicked
        // TODO add your handling code here:
        if (max_min_label.isEnabled()) {
            panel_arriba.setVisible(!panel_arriba.isVisible());
            Helpers.setScaledIconLabel(max_min_label, getClass().getResource("/images/" + (panel_arriba.isVisible() ? "maximize" : "minimize") + ".png"), chat_box.getHeight(), chat_box.getHeight());

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
                });
            });
        }

    }//GEN-LAST:event_max_min_labelMouseClicked

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentHidden
        // TODO add your handling code here:

        if (!protect_focus) {
            chat.setText("<html><body style='background-image: url(" + background_chat_src + ")'></body></html>");
            chat_box.requestFocus();
        }

        if (partida_empezando) {
            partida_empezando = false;
        }
    }//GEN-LAST:event_formComponentHidden

    private void formWindowStateChanged(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowStateChanged
        // TODO add your handling code here:

        if ((evt.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            avatar_label.setText(this.local_nick);
        } else {
            avatar_label.setText("");
        }
    }//GEN-LAST:event_formWindowStateChanged

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_chatMouseClicked
        // TODO add your handling code here:

        if (!chat.isFocusable()) {
            this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Color.GREEN, 3));
            ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            chat.setFocusable(true);
            chat.requestFocus();
        }
    }//GEN-LAST:event_chatMouseClicked

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (protect_focus) {

            setVisible(false);

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    setVisible(true);
                });
            });
        }
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowDeiconified(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeiconified
        // TODO add your handling code here:
        if (protect_focus) {
            protect_focus = false;
            setVisible(false);
        }
    }//GEN-LAST:event_formWindowDeiconified

    private void game_info_blindsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_blindsMouseClicked
        // TODO add your handling code here:
        game_info_buyinMouseClicked(evt);
    }//GEN-LAST:event_game_info_blindsMouseClicked

    private void game_info_handsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_handsMouseClicked
        // TODO add your handling code here:
        game_info_buyinMouseClicked(evt);
    }//GEN-LAST:event_game_info_handsMouseClicked

    private void chatCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_chatCaretUpdate
        // TODO add your handling code here:

        chat.revalidate();
        chat.repaint();
        chat_scroll.revalidate();
        chat_scroll.repaint();
    }//GEN-LAST:event_chatCaretUpdate

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JProgressBar barra;
    private javax.swing.JEditorPane chat;
    private javax.swing.JTextField chat_box;
    private javax.swing.JPanel chat_box_panel;
    private javax.swing.JCheckBox chat_notifications;
    private javax.swing.JScrollPane chat_scroll;
    private javax.swing.JList<String> conectados;
    private javax.swing.JLabel danger_server;
    private javax.swing.JButton emoji_button;
    private com.tonikelope.coronapoker.EmojiPanel emoji_panel;
    private javax.swing.JScrollPane emoji_scroll_panel;
    private javax.swing.JButton empezar_timba;
    private javax.swing.JLabel game_info_blinds;
    private javax.swing.JLabel game_info_buyin;
    private javax.swing.JLabel game_info_hands;
    private javax.swing.JButton image_button;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JButton kick_user;
    private javax.swing.JLabel logo;
    private javax.swing.JPanel main_panel;
    private javax.swing.JScrollPane main_scroll_panel;
    private javax.swing.JLabel max_min_label;
    private javax.swing.JButton new_bot_button;
    private javax.swing.JPanel panel_arriba;
    private javax.swing.JPanel panel_con;
    private javax.swing.JScrollPane panel_conectados;
    private javax.swing.JLabel pass_icon;
    private javax.swing.JLabel radar;
    private javax.swing.JLabel send_label;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel status;
    private javax.swing.JLabel status1;
    private javax.swing.JLabel tot_conectados;
    private javax.swing.JLabel tts_warning;
    private javax.swing.JButton video_chat_button;
    // End of variables declaration//GEN-END:variables
}
