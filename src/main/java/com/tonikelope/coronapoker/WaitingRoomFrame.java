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

import com.tonikelope.coronapoker.crypto.RistrettoSRA;
import com.tonikelope.coronapoker.crypto.DealChain;
import com.tonikelope.coronapoker.crypto.UnlockChainWire;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.AdjustmentEvent;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import static com.tonikelope.coronapoker.InGameNotifyDialog.NOTIFICATION_TIMEOUT;
import static com.tonikelope.coronapoker.Init.DEV_MODE;
import java.util.Arrays;
import java.util.Base64;

/**
 * Appearances can be deceiving...
 *
 * ...sometimes.
 *
 * Perhaps in another life I can refactor all this.
 *
 * @author tonikelope
 */
public class WaitingRoomFrame extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(WaitingRoomFrame.class.getName());

    public static final int MAX_PARTICIPANTES = 10;
    public static final String MAGIC_BYTES = "5c1f158dd9855cc9";
    public static final String POISON_PILL = "___SOCKET_BYE___";
    public static final int PING_PONG_TIMEOUT = 10000;
    // Read deadline aplicado al socket durante el handshake (magic bytes + ECDH +
    // session_id + JOIN + NICKOK/intro/chat-history). Un peer legitimo completa
    // este intercambio en <1s; el unico caso real que se aproxima al limite es una
    // primera generacion de Ed25519 keypair en CPU muy lenta. Damos 30s de margen.
    //
    // Critico para defender contra peers que abren socket pero NUNCA mandan bytes
    // (DoS local con sockets mudos) o que mandan bytes goteando uno-a-uno (eternizan
    // el thread del handshake). El timeout se RESETEA a 0 (sin limite) en cuanto el
    // handshake completa con exito y antes de que el reader normal del Participant
    // tome control, asi las pausas legitimas inter-mano nunca lo disparan.
    public static final int HANDSHAKE_TIMEOUT_MS = 30000;
    // Cap del int de longitud que el peer envía en cada read del handshake.
    // El pubkey EC X.509 (P-256) real son ~91 bytes; permitimos 256 de margen
    // por si alguna vez se sube de curva. Sin este cap un peer hostil puede
    // mandar Integer.MAX_VALUE y forzar new byte[2GB] → OOM instantáneo.
    public static final int HANDSHAKE_MAX_PUBKEY_BYTES = 256;
    // session_id que el server emite tiene tamaño fijo 16 (ver línea 712:
    // this.session_id = new byte[16];). Cap a 64 da margen futuro sin
    // permitir abuso.
    public static final int HANDSHAKE_MAX_SESSIONID_BYTES = 64;

    // Pre-compiled patterns used per chat message in txtChat2HTML and its helpers.
    // Hot path: avoid String.replaceAll which recompiles the regex on every call.
    private static final Pattern CHAT_NICK_PATTERN = Pattern.compile("^([^:()]+:+).*$");
    private static final Pattern CHAT_NICK_TRAIL_COLON = Pattern.compile(":$");
    private static final Pattern CHAT_MSG_PATTERN = Pattern.compile("^[^:()]+:+[0-9:()]+ *(.*)$");
    private static final Pattern CHAT_TIME_PATTERN = Pattern.compile("^[^:()]+:+([0-9:()]+) *.*$");
    private static final Pattern CHAT_URL_PATTERN = Pattern.compile("https?://([^/]+)[^ \r\n]*");
    private static final Pattern CHAT_EMAIL_PATTERN = Pattern.compile("[^@ ]+@[^ @.]+(?:\\.[^.@ ]+)+");
    private static final Pattern CHAT_BBCODE_I_PATTERN = Pattern.compile("(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern CHAT_BBCODE_B_PATTERN = Pattern.compile("(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern CHAT_BBCODE_C_PATTERN = Pattern.compile("(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]");
    private static final Pattern CHAT_IMG_PATTERN = Pattern.compile("img(s?)://([^ \r\n]+)");
    private static final Pattern CHAT_LINK_OR_IMG_PATTERN = Pattern.compile("(?:http|img)s?://[^ \r\n]+");
    private static final Pattern CHAT_EMOJI_PATTERN = Pattern.compile("#([0-9]+)#");
    private static final Pattern CHAT_VOICE_NOTE_PATTERN = Pattern.compile("@@voicenote:([A-Za-z0-9._-]+)@@");

    public static final long PING_INTERVAL_MS = 5000;
    // Umbral de PONGs consecutivos perdidos antes de cerrar el socket por nuestra cuenta.
    // Red de seguridad para sockets "mudos" (peer killed sin RST, particion unidireccional,
    // GC stall infinito del peer). Con N=3, PING_INTERVAL_MS=5s y PING_PONG_TIMEOUT=10s,
    // la deteccion peor caso es ~3*(10+5)=45s. La via primaria sigue siendo la IOException
    // capturada en write (deteccion ~0ms en el siguiente PING saliente) y el SO_KEEPALIVE
    // del socket.
    public static final int MAX_CONSECUTIVE_PING_FAILURES = 3;
    public static final int PRE_GAME_COMMANDS_LOCK = 15000;
    public static final int EC_KEY_LENGTH = 256;
    public static final int GEN_PASS_LENGTH = 14;
    public static final int CLIENT_REC_WAIT = 5;
    public static final int ANTI_FLOOD_CHAT = 500;
    // 15s of u-law 16kHz is ~240KB; headroom for the WAV header
    public static final int MAX_VOICE_MESSAGE_BYTES = 320 * 1024;
    public static volatile boolean CHAT_GAME_NOTIFICATIONS = Boolean
            .parseBoolean(Helpers.PROPERTIES.getProperty("chat_game_notifications", "true"));
    private static volatile WaitingRoomFrame THIS = null;

    private final File local_avatar;
    private final Map<String, Participant> participantes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Object ping_pong_lock = new Object();
    private final Object lock_new_client = new Object();
    private final boolean server;
    private final String local_nick;
    // Stats DB sync (P2P): the protocol logic lives in StatsSyncManager; the
    // per-peer channel keys and TYPE_DB framing glue live in this class.
    private final StatsSyncManager stats_sync_manager = new StatsSyncManager(this);
    private volatile String server_ip_port;  // ip:port — host (para parsear puerto local) y cliente (para conectar).
    private volatile String server_nick;
    private volatile String gameinfo_original = null;
    // Tag en el campo buyin de gameinfo_original cuando la partida es de buy-in
    // variable: la sala de espera oculta la caracteristica de buyin (la bolsa),
    // porque cada jugador elige su buy-in al entrar al tablero.
    private static final String VARIABLE_BUYIN_TAG = "VAR";
    // Espejo COMPLETO de la config de timba que el HOST difunde (GamePreset.Settings
    // serializado) para que la pestaña "Partida" de la rueda en SOLO-LECTURA del cliente
    // muestre todo el detalle. Llega en el handshake (NICKOK) y se refresca con cada
    // GAMECONFIG. El cliente NO escribe GameFrame.* con esto (la config real le llega en el
    // INIT al arrancar); solo lo lee el panel read-only. null hasta el primer sync.
    public static volatile String GAMECONFIG_MIRROR = null;
    private volatile boolean chat_enabled = true;
    private volatile boolean upnp = false;
    private volatile int server_port = 0;
    private volatile boolean booting = false;
    private volatile boolean partida_empezada = false;
    // Telemetría: última snapshot recibida desde el host. Actualizada
    // en la rama "TELEMETRY" del GAME sub-switch de cliente(). Lectores
    // (futuro LatencyDot, F7 label) acceden via getLatest_telemetry().
    private volatile Helpers.TelemetryFrame latest_telemetry = null;
    private volatile boolean partida_empezando = false;
    private volatile String password = null;
    private volatile boolean exit = false;
    private volatile StringBuffer chat_text = new StringBuffer();
    private final String background_chat_src;
    private volatile String local_avatar_chat_src;
    private volatile Border chat_scroll_border = null;

    // Uno u otro será no-null según el rol (server flag).
    private final NetServer net_server;
    private final NetClient net_client;

    // Identity: per-game 16-byte session identifier. The host generates it once
    // at construction and ships it inside the ECDH handshake. Clients capture it from
    // the handshake and use it to compute their JOIN_IDENTITY self_sig (which binds
    // their pubkey to this specific game session and thus blocks replay across sessions).
    private volatile byte[] session_id = null;

    // Identity: the host computes its own self_sig at game creation so it can be
    // shipped to every joining client embedded in the sync intro (atomic transport).
    // Host is special-cased because it is not in `participantes` — it IS the room.
    private volatile byte[] host_self_sig = null;
    private volatile byte[] host_identity_pubkey = null;

    public byte[] getSession_id() {
        return session_id;
    }

    public byte[] getHost_identity_pubkey() {
        return host_identity_pubkey;
    }

    public byte[] getHost_self_sig() {
        return host_self_sig;
    }

    public NetServer getNet_server() {
        return net_server;
    }

    public NetClient getNet_client() {
        return net_client;
    }

    public void markPlayerAsCheater(String nick) {
        Helpers.GUIRun(() -> {

            DefaultListModel<ParticipantJListData> model = (DefaultListModel<ParticipantJListData>) conectados.getModel();

            for (int i = 0; i < model.getSize(); i++) {

                ParticipantJListData p = model.getElementAt(i);

                if (p.getNick().equals(nick)) {

                    model.set(i, p);
                    break;
                }
            }
        });
    }

    public int getServer_latency2() {
        return net_client != null ? net_client.getRemote_server_latency2() : 0;
    }

    public int getServer_latency() {
        return net_client != null ? net_client.getRemote_server_latency() : 0;
    }

    public String getPassword() {
        return password;
    }

    public Object getLock_client_pre_game_commands_wait() {
        return net_server != null ? net_server.getLock_client_pre_game_commands_wait() : null;
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

    public JList<ParticipantJListData> getConectados() {
        return conectados;
    }

    public void soundIconClick() {
        Helpers.GUIRun(() -> {
            sound_iconMouseClicked(null);
        });
    }

    // Refresca el icono de altavoz de la sala según SONIDOS. Lo usa
    // GameFrame.setSonidos para que el cambio hecho desde el diálogo de ajustes
    // de audio se vea aquí.
    public static void refreshSoundIcon() {

        WaitingRoomFrame sala = getInstance();

        if (sala != null) {
            Helpers.GUIRun(() -> {
                Helpers.setScaledIconLabel(sala.sound_icon, WaitingRoomFrame.class.getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);
            });
        }
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    public void closeServerSocket() {
        if (net_server != null) {
            net_server.closeServerSocket();
        }
    }

    public void closeClientSocket() {
        if (net_client != null) {
            net_client.closeClientSocket();
        }
    }

    public static void resetInstance() {
        if (THIS.net_server != null) {
            THIS.net_server.getLate_clients_warning().clear();
        }
        if (THIS.net_client != null) {
            THIS.net_client.getLate_clients_warning().clear();
        }
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

    public boolean isUnsecure_server() {
        return net_client != null && net_client.isUnsecure_server();
    }

    public int getServer_port() {
        return server_port;
    }

    public boolean isUpnp() {
        return upnp;
    }

    public void setUnsecure_server(boolean val) {

        if (net_client != null && !net_client.isUnsecure_server() && val) {

            Helpers.GUIRunAndWait(() -> {
                danger_server.setVisible(val);
                pack();
            });

        }

        if (net_client != null) {
            net_client.setUnsecure_server(val);
        }

    }

    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return server ? net_server.getReceived_confirmations() : net_client.getReceived_confirmations();
    }

    public SecretKeySpec getLocal_client_hmac_key() {

        while (net_client != null && net_client.isReconnecting()) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "reconnect key wait", ex);
                    break;
                }
            }
        }

        return net_client != null ? net_client.getLocal_client_hmac_key() : null;

    }

    public SecretKeySpec getLocal_client_aes_key() {

        while (net_client != null && net_client.isReconnecting()) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    Helpers.logCooperativeCancellation(LOGGER, "reconnect key wait", ex);
                    break;
                }
            }
        }

        return net_client != null ? net_client.getLocal_client_aes_key() : null;

    }

    public BufferedInputStream getLocal_client_buffer_read_is() {
        return net_client != null ? net_client.getLocal_client_buffer_read_is() : null;
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

    public static void setInstance(WaitingRoomFrame instance) {
        WaitingRoomFrame.THIS = instance;
    }

    public JLabel getStatus() {
        return status;
    }

    public boolean isReconnecting() {
        return net_client != null && net_client.isReconnecting();
    }

    public Object getLock_reconnect() {
        return net_client != null ? net_client.getLock_reconnect() : null;
    }

    public File getAvatar() {
        return local_avatar;
    }

    public boolean isServer() {
        return server;
    }

    public ServerSocket getServer_socket() {
        return net_server != null ? net_server.getServer_socket() : null;
    }

    public String getServer_nick() {
        return server_nick;
    }

    public Object getLocalClientSocketLock() {
        return net_client != null ? net_client.getLocal_client_socket_lock() : null;
    }

    /**
     * Handles a right-click anywhere inside the participant list (including empty
     * space below the rows). The opened dialog does not depend on where the click
     * lands: a host always gets the mosaic of every channel, a client always gets
     * its single channel with the host.
     *
     * For now the dialog opens directly. The popup menu in
     * {@link #buildSessionIdenticonMenu()} is intentionally kept but not shown, ready
     * for when more than one per-list action is needed.
     */
    private void handleParticipantListRightClick(java.awt.event.MouseEvent evt) {
        if (!evt.isPopupTrigger()) {
            return;
        }

        openSessionIdenticon();
    }

    /**
     * Reserved: per-row right-click menu for the participant list. Currently unused
     * because a single action ("view session identicon") opens directly, but kept so
     * extra actions can be added later by showing this menu instead of opening the
     * dialog directly in {@link #handleParticipantListRightClick(java.awt.event.MouseEvent)}.
     */
    private JPopupMenu buildSessionIdenticonMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem ver = new JMenuItem(Translator.translate("ui.identicon.popup_ver"));
        ver.addActionListener(e -> openSessionIdenticon());
        menu.add(ver);
        return menu;
    }

    /**
     * Opens the session-key identicon dialog. The host gets the mosaic of every
     * per-client channel ({@link SessionIdenticonMosaicDialog}); a client gets the
     * single AES identicon of its channel with the host.
     */
    private void openSessionIdenticon() {
        SessionIdenticonMosaicDialog mosaic = SessionIdenticonMosaicDialog.buildForHost(this, this);

        if (mosaic != null) {
            mosaic.setLocationRelativeTo(this);
            mosaic.setVisible(true);
            return;
        }

        SecretKeySpec my_key = getLocal_client_aes_key();

        if (my_key == null) {
            return;
        }

        String title = server_nick != null ? local_nick + " ↔ " + server_nick : local_nick;
        IdenticonDialog dialog = new IdenticonDialog(this, true, title, my_key);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void HTMLEditorKitAppend(String text) {

        Helpers.GUIRun(() -> {
            CoronaHTMLEditorKit editor = (CoronaHTMLEditorKit) chat.getEditorKit();
            StringReader reader = new StringReader(text);
            try {
                editor.read(reader, chat.getDocument(), chat.getDocument().getLength());
                chat.setCaretPosition(chat.getDocument().getLength());
                // Forzar repintado completo: el clip parcial que dispara el append deja a veces
                // el fillRoundRect de RoundedBubbleView recortado. Sin sentido cuando el
                // componente no esta mostrando (chat oculto durante la partida): el deferred
                // setSize en formComponentShown se encarga del relayout al reabrir.
                if (chat.isShowing()) {
                    chat.revalidate();
                    chat.repaint();
                }
            } catch (Exception ex) {
            }
        });

    }

    public void chatHTMLAppend(String text) {

        chat_text.append(text);

        HTMLEditorKitAppend(txtChat2HTML(text));
    }

    public void chatHTMLAppendNewUser(String nick) {

        String hora = Helpers.getLocalTimeString();

        String avatar_src = this.participantes.get(nick).getAvatar_chat_src();

        HTMLEditorKitAppend("<div align='center' style='margin-top:7px;margin-bottom:7px;'><img id='avatar_" + nick
                + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick + "&nbsp;<span style='color:green;'>"
                + Translator.translate("game.se_une_a_la_timba") + "</span></b>&nbsp;<span style='font-size:0.8em'>(" + hora
                + ")</span></div>");
    }

    public void chatHTMLAppendExitUser(String nick, String avatar_src) {

        String hora = Helpers.getLocalTimeString();

        HTMLEditorKitAppend("<div align='center' style='margin-top:7px;margin-bottom:7px;'><img id='avatar_" + nick
                + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick + "&nbsp;<span style='color:red;'>"
                + Translator.translate("game.abandona_la_timba_2") + "</span></b>&nbsp;<span style='font-size:0.8em'>(" + hora
                + ")</span></div>");
    }

    public synchronized String txtChat2HTML(String chat) {

        StringBuilder html = new StringBuilder();

        String[] lines = chat.split("\n");

        for (String line : lines) {

            String nick = CHAT_NICK_TRAIL_COLON.matcher(CHAT_NICK_PATTERN.matcher(line).replaceAll("$1")).replaceAll("");

            String msg = CHAT_MSG_PATTERN.matcher(line).replaceAll("$1");

            String hora = CHAT_TIME_PATTERN.matcher(line).replaceAll("$1");

            String avatar_src, align, image_align, bubble_class;

            if (nick.equals(this.local_nick)) {

                align = "align='right' style='margin-right:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = local_avatar_chat_src;

                image_align = "0.995";

                bubble_class = "bubble bubble-mine";

            } else if (this.participantes.containsKey(nick)) {

                align = "align='left' style='margin-left:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = this.participantes.get(nick).getAvatar_chat_src();

                image_align = "0.005";

                bubble_class = "bubble bubble-other";
            } else {
                align = "align='left' style='margin-left:8px;margin-top:7px;margin-bottom:7px;'";

                avatar_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();

                image_align = "0.005";

                bubble_class = "bubble bubble-other";
            }

            msg = Helpers.escapeHTML(msg);

            msg = CHAT_URL_PATTERN.matcher(msg).replaceAll("#171#<a href='$0'><b>$1</b></a>");

            msg = CHAT_EMAIL_PATTERN.matcher(msg).replaceAll("#1215# <i>$0</i>");

            msg = parseImagesChat(msg, image_align, nick.equals(this.local_nick));

            // Before the emoji pass: the voice note line emits a #1138# emoji
            msg = parseVoiceNoteChat(msg);

            msg = parseEmojiChat(msg);

            msg = parseBBCODEChat(msg);

            // Outer table is kept solely as a 'shrink-to-fit' container (HTMLEditorKit
            // does not support display:inline-block). The inner <div class='bubble-...'>
            // is rendered by RoundedBubbleView, which paints the rounded background.
            html.append("<table ").append(align).append(" border='0' cellpadding='0' cellspacing='0'>")
                    .append("<tr>")
                    .append("<td>")
                    .append("<div class='").append(bubble_class).append("' style='padding:5px;'>")
                    // Header section with Avatar, Nickname and Time
                    .append("<div>")
                    .append("<img id='avatar_").append(nick).append("' align='middle' src='").append(avatar_src).append("' />")
                    .append("&nbsp;<b>").append(nick).append("</b> ")
                    .append("<span style='font-size:0.8em'>").append(hora).append("</span>")
                    .append("</div>")
                    // Body section with the message
                    .append("<div>").append(msg).append("</div>")
                    .append("</div>")
                    .append("</td>")
                    .append("</tr>")
                    .append("</table>");
        }

        return html.toString();

    }

    private String parseBBCODEChat(String message) {

        String out = CHAT_BBCODE_I_PATTERN.matcher(message).replaceAll("<i>$2</i>");
        out = CHAT_BBCODE_B_PATTERN.matcher(out).replaceAll("<b>$2</b>");
        out = CHAT_BBCODE_C_PATTERN.matcher(out).replaceAll("<span style='color:$2'>$3</span>");
        return out;
    }

    private String removeBBCODEChat(String message) {
        String out = CHAT_BBCODE_I_PATTERN.matcher(message).replaceAll("$2");
        out = CHAT_BBCODE_B_PATTERN.matcher(out).replaceAll("$2");
        out = CHAT_BBCODE_C_PATTERN.matcher(out).replaceAll("$3");
        return out;
    }

    private String parseImagesChat(String message, String align, boolean send) {

        String msg = message;

        Matcher matcher = CHAT_IMG_PATTERN.matcher(message);

        ArrayList<String> lista = new ArrayList<>();

        ArrayList<String> img_src_lista = new ArrayList<>();

        while (matcher.find()) {

            if (!lista.contains(matcher.group(0))) {

                String img_src = "http" + (matcher.groupCount() > 1 ? matcher.group(1) : "") + "://"
                        + matcher.group(matcher.groupCount() > 1 ? 2 : 1);

                try {
                    msg = msg
                            .replaceAll(Pattern.quote(matcher.group(0)),
                                    "<tonimg>" + (Base64.getEncoder().encodeToString(img_src.getBytes("UTF-8")) + "@" + align)
                                    + "</tonimg><img src='" + getClass()
                                            .getResource("/images/emoji_chat/image_space.png").toExternalForm()
                                    + "' />");
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

                lista.add(matcher.group(0));

                if (!send) {

                    img_src_lista.add(img_src);
                }

            }
        }

        if (!img_src_lista.isEmpty()) {

            Helpers.threadRun(() -> {
                ChatImageDialog.updateHistorialRecibidos(img_src_lista);
            });
        }

        return msg;
    }

    private String removeLinksImagesChat(String message) {
        return CHAT_LINK_OR_IMG_PATTERN.matcher(message).replaceAll("");
    }

    // Whole line (emoji included) lives INSIDE the anchor so clicking the
    // emoji also plays
    private String voiceNoteAnchorHTML(String filename) {

        String emoji = EmojiPanel.EMOJI_SRC.size() >= 1138
                ? "<img align='middle' src='" + EmojiPanel.EMOJI_SRC.get(1138 - 1) + "' />&nbsp;" : "";

        return "<a id='voicenote_" + filename + "' href='voicenote:" + filename + "'>" + emoji + "<b>"
                + Translator.translate("audio.nota_de_voz") + "</b></a>";
    }

    private String parseVoiceNoteChat(String message) {

        Matcher matcher = CHAT_VOICE_NOTE_PATTERN.matcher(message);

        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(voiceNoteAnchorHTML(matcher.group(1))));
        }

        matcher.appendTail(out);

        return out.toString();
    }

    // Swaps the chat line of a voice note between [Nota de voz] and
    // [Reproduciendo...] while it plays. Pure text surgery: getElement(id)
    // lands on the FIRST leaf carrying the id (the emoji img, which inherits
    // the anchor attributes), so re-inserting HTML there ACCUMULATED labels.
    // Instead, the text run after the img is replaced in place keeping its
    // attributes (anchor, id and bold survive, the emoji is untouched).
    public void setVoiceNoteChatLabel(String filename, boolean playing) {

        Helpers.GUIRun(() -> {
            try {
                javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument) chat.getDocument();

                javax.swing.text.Element first = doc.getElement("voicenote_" + filename);

                if (first == null) {
                    return;
                }

                String target_id = "voicenote_" + filename;

                int pos = first.getStartOffset();

                int text_start = -1, text_end = -1;

                javax.swing.text.AttributeSet text_attrs = null;

                while (pos < doc.getLength()) {

                    javax.swing.text.Element run = doc.getCharacterElement(pos);

                    javax.swing.text.AttributeSet a = (javax.swing.text.AttributeSet) run.getAttributes().getAttribute(javax.swing.text.html.HTML.Tag.A);

                    if (a == null || !target_id.equals(a.getAttribute(javax.swing.text.html.HTML.Attribute.ID))) {
                        break;
                    }

                    if ("img".equals(run.getName())) {
                        // The label is the contiguous text segment AFTER the emoji
                        text_start = -1;
                    } else {
                        if (text_start < 0) {
                            text_start = run.getStartOffset();
                        }
                        text_end = run.getEndOffset();
                        text_attrs = run.getAttributes();
                    }

                    pos = run.getEndOffset();
                }

                if (text_start < 0 || text_attrs == null) {
                    return;
                }

                javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet(text_attrs);

                doc.remove(text_start, text_end - text_start);

                doc.insertString(text_start, Translator.translate(playing ? "audio.reproduciendo" : "audio.nota_de_voz"), attrs);

            } catch (Exception ex) {
            }
        });
    }

    // The plain-text chat views (FastChat) show voice notes with their clean
    // label instead of the internal token
    public static String cleanVoiceNoteTokens(String text) {

        return CHAT_VOICE_NOTE_PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(Translator.translate("audio.nota_de_voz")));
    }

    private String parseEmojiChat(String message) {

        String msg = message;

        Matcher matcher = CHAT_EMOJI_PATTERN.matcher(message);

        ArrayList<Integer> lista = new ArrayList<>();

        while (matcher.find()) {

            try {

                if (!lista.contains(Integer.valueOf(matcher.group(1))) && Integer.parseInt(matcher.group(1)) > 0
                        && Integer.parseInt(matcher.group(1)) <= EmojiPanel.EMOJI_SRC.size()) {

                    String emoji_src = EmojiPanel.EMOJI_SRC.get(Integer.parseInt(matcher.group(1)) - 1);

                    msg = msg.replaceAll(" ?#" + matcher.group(1) + "# ?",
                            "<span><img align='middle' src='" + emoji_src + "' /></span>&nbsp;");

                    lista.add(Integer.valueOf(matcher.group(1)));
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

    public EmojiChatBox getChat_box() {
        return (EmojiChatBox) chat_box;
    }

    /**
     * Creates new form SalaEspera
     */
    public WaitingRoomFrame(boolean local, String nick, String servidor_ip_port, File avatar, String pass,
            boolean use_upnp) {

        upnp = use_upnp;
        server = local;
        local_nick = nick;
        server_ip_port = servidor_ip_port;
        local_avatar = avatar;
        password = pass;

        this.net_server = server ? new NetServer(this) : null;
        this.net_client = server ? null : new NetClient(this);

        // Identity: host pre-generates session_id once at construction so it can be
        // shipped to every joining client during the ECDH handshake. Clients leave it null
        // here and capture the value from the wire when they connect.
        //
        // The host also pre-computes its own self_sig over (session_id || nick || pubkey) so
        // that the host's identity can be relayed to every joining peer embedded in the sync
        // intro (atomic transport, no separate async IDENTITY command).
        if (server) {
            this.session_id = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(this.session_id);
            IdentityManager im = IdentityManager.getInstance();
            if (im.isReady()) {
                this.host_identity_pubkey = im.getPublicKey();
                this.host_self_sig = im.signJoin(this.session_id, local_nick);
            } else {
                LOGGER.log(Level.SEVERE, "Host identity not ready: {0}", im.getLoadError());
            }
        }

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate("game.sala_de_espera") + nick + ")");

        // Placeholder traducido hasta que llegue el primer PING (el texto del .form es solo
        // el default de diseño; el formato real lo pone el handler de PONGs).
        latency_label.setText(Translator.translate("ui.latencia_servidor") + " 0 ms | 0 ms");

        // Session-key identicon access (anti-MITM): right-click any participant in the
        // list. A client opens the AES identicon of its single channel with the host;
        // the host opens the mosaic of every per-client session identicon.
        Helpers.setTranslatedToolTip(conectados, "ui.identicon.tooltip_lista");
        conectados.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                handleParticipantListRightClick(evt);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                handleParticipantListRightClick(evt);
            }
        });

        class SendButtonListener implements DocumentListener {

            public void changedUpdate(DocumentEvent e) {
                refresh();
            }

            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            private void refresh() {
                boolean blank = ((EmojiChatBox) chat_box).isRawBlank();
                send_label.setVisible(!blank);
                max_min_label.setVisible(blank);
            }
        }

        latency_label.setVisible(false);

        chat_box.getDocument().addDocumentListener(new SendButtonListener());

        javax.swing.AbstractAction send_chat_action = new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                chat_boxActionPerformed(null);
            }
        };
        chat_box.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "send-chat");
        chat_box.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.SHIFT_DOWN_MASK), "send-chat");
        chat_box.getActionMap().put("send-chat", send_chat_action);

        emoji_button.setEnabled(false);

        Helpers.setScaledIconLabel(send_label, getClass().getResource("/images/start.png"), chat_box.getHeight(),
                chat_box.getHeight());

        Helpers.setScaledIconLabel(max_min_label, getClass().getResource("/images/maximize.png"), chat_box.getHeight(),
                chat_box.getHeight());

        send_label.setVisible(false);

        chat_scroll_border = chat_scroll.getBorder();

        emoji_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        main_scroll_panel.getVerticalScrollBar().setUnitIncrement(16);

        main_scroll_panel.getHorizontalScrollBar().setUnitIncrement(16);

        emoji_scroll_panel.setVisible(false);

        chat.setContentType("text/html");

        chat.setEditorKit(new CoronaHTMLEditorKit());

        chat.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {

                // Voice note anchors are handled by the manual hit-test in
                // chatMouseClicked (the stock LinkController is unreliable
                // with custom schemes): guard against double handling here.
                if (e.getURL() != null) {

                    Helpers.openBrowserURL(e.getURL().toString());

                    chat_box.requestFocus();
                }
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
            game_info_buyin.setText(Translator.translate("game.continuando_timba_anterior"));
            game_info_buyin.setOpaque(true);
            game_info_buyin.setBackground(Color.YELLOW);
            game_info_buyin.setIcon(null);
            game_info_blinds.setVisible(false);
            game_info_hands.setVisible(false);
        }

        if (server) {

            if (!GameFrame.isRECOVER()) {
                game_info_buyin.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));
                game_info_blinds.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));
                game_info_hands.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));

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

        Helpers.setScaledIconLabel(sound_icon,
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        kick_user.setEnabled(false);

        empezar_timba.setEnabled(false);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat_box);

        image_button.setToolTipText(Translator.translate("tooltip.send_image"));
        sound_icon.setToolTipText(Translator.translate("sound.click_para_activardesactivar_el_sonido"));

        Helpers.setScaledBlackIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), 30, 30);
        settings_icon.setToolTipText(Translator.translate("settings.ajustes"));

        if (avatar != null) {
            avatar_label.setPreferredSize(
                    new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            Helpers.setScaledIconLabel(avatar_label, avatar.getAbsolutePath(), NewGameDialog.DEFAULT_AVATAR_WIDTH,
                    NewGameDialog.DEFAULT_AVATAR_WIDTH);
            try {
                ImageIO.write(
                        Helpers.toBufferedImage(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage()
                                .getScaledInstance(32, 32, Image.SCALE_SMOOTH)).getImage()),
                        "png", new File(local_avatar.getAbsolutePath() + "_chat"));
                local_avatar_chat_src = new File(local_avatar.getAbsolutePath() + "_chat").toURI().toURL()
                        .toExternalForm();

            } catch (IOException ex) {
                local_avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } else {
            avatar_label.setPreferredSize(
                    new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            Helpers.setScaledIconLabel(avatar_label, getClass().getResource("/images/avatar_default.png"),
                    NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
            local_avatar_chat_src = getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
        }

        avatar_label.setText("");

        server_address_label.setText(server_ip_port);

        DefaultListModel<ParticipantJListData> listModel = new DefaultListModel<>();

        if (server) {

            new_bot_button.setEnabled(true);

            status.setText(Translator.translate("ui.waiting_for_players"));

            gameinfo_original = (GameFrame.FIXED_BUYIN ? (GameFrame.BUYIN + (GameFrame.REBUY ? "" : "*")) : VARIABLE_BUYIN_TAG) + "|"
                    + Helpers.money2String(GameFrame.CIEGA_PEQUEÑA) + " / "
                    + Helpers.money2String(GameFrame.CIEGA_GRANDE)
                    + (GameFrame.CIEGAS_DOUBLE > 0
                            ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE)
                            + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*")
                            : "")
                    + (GameFrame.MANOS != -1 ? "|" + String.valueOf(GameFrame.MANOS) : "");

            if (game_info_buyin.isEnabled() && !GameFrame.isRECOVER()) {
                applyGameInfoBuyinLabel(gameinfo_original.split("\\|"));
            }

            participantes.put(local_nick, null);

            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            ParticipantJListData participant_data = new ParticipantJListData(local_nick);

            ImageIcon participant_avatar = null;

            if (local_avatar != null) {
                try {
                    participant_avatar = Helpers.scaleIcon(local_avatar.getAbsolutePath(),
                            NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null,
                            ex);
                }
            } else {
                try {
                    participant_avatar = Helpers.scaleIcon(getClass().getResource("/images/avatar_default.png"),
                            NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null,
                            ex);
                }
            }

            participant_data.setAvatar(participant_avatar);

            listModel.addElement(participant_data);

            conectados.setModel(listModel);

        } else {
            empezar_timba.setVisible(false);
            new_bot_button.setVisible(false);
            kick_user.setVisible(false);
            chat_box.setEnabled(false);
            emoji_button.setEnabled(false);
            image_button.setEnabled(false);
            max_min_label.setEnabled(false);
            barra.setVisible(true);
            conectados.setModel(listModel);
            game_info_buyin.setToolTipText(null);
            game_info_blinds.setToolTipText(null);
            game_info_hands.setToolTipText(null);
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        Helpers.setScaledIconButton(empezar_timba, getClass().getResource("/images/start.png"),
                Math.round(empezar_timba.getHeight() * 0.8f), Math.round(empezar_timba.getHeight() * 0.8f));

        Helpers.setScaledIconButton(kick_user, getClass().getResource("/images/kick.png"), kick_user.getHeight(),
                kick_user.getHeight());

        chat_box.setPreferredSize(new Dimension(Math.round((float) (chat_box.getSize().getWidth() * 0.5f)),
                (int) chat_box.getSize().getHeight()));

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        int h = (int) Math.min(getHeight(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);

            setPreferredSize(getSize());

            pack();

            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

        } else {
            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);
        }

        Audio.muteLoopMp3("misc/background_music.mp3");

        Audio.playLoopMp3Resource("misc/waiting_room.mp3");

        if (server) {
            servidor();
        } else {
            cliente();
        }

        revalidate();
        repaint();
    }

    public JScrollPane getEmoji_scroll_panel() {
        return emoji_scroll_panel;
    }

    public void writeCommandToServer(String command) {
        net_client.writeCommand(command);
    }

    public void writeBinaryToServer(byte[] frameBody) {
        net_client.writeBinary(frameBody);
    }

    public void writeCommandFromServer(String command, Socket socket) {
        net_server.writeCommand(command, socket);
    }

    public String readCommandFromClient(Socket socket, SecretKeySpec key, SecretKeySpec hmac_key) {
        return net_server.readCommand(socket, key, hmac_key);
    }

    public String readCommandFromServer() {
        return net_client.readCommand();
    }

    // True si la timba YA terminó para este peer (su Crupier salió del bucle
    // run() con fin_de_la_transmision): la reconexión automática no aplica —
    // el cierre del socket del server es el final normal de la partida, no
    // una caída. OJO: el flag exit de esta clase se levanta más tarde
    // (GameFrame.finTransmision, tras el volcado de logs/SQL/chat), así que
    // sin esta consulta el reader detecta el cierre del host ANTES de
    // exit=true (carrera host-termina-primero, p.ej. host arruinado que pasa
    // a espectador y deja la timba con un solo jugador) y dispara
    // reconexiones espurias con sus banners encima del BalanceDialog.
    private boolean timbaTerminada() {
        return isPartida_empezada() && GameFrame.getInstance() != null
                && GameFrame.getInstance().getCrupier() != null
                && GameFrame.getInstance().getCrupier().isFin_de_la_transmision();
    }

    // Función AUTO-RECONNECT
    public boolean reconectarCliente() {

        net_client.setReconnecting(true);

        LOGGER.log(Level.WARNING, "Attempting to reconnect to server...");

        // Indicador PERSISTENTE de "reconectando" (timeout=null -> sin auto-cierre):
        // antes el toast moria a los NOTIFICATION_TIMEOUT (5s) mientras el cliente
        // seguia reintentando en silencio hasta 80s, dejando al usuario sin ver nada
        // ~75s. Ahora se mantiene visible toda la fase de auto-reintento y se cierra
        // al resolver (exito, aparicion del dialogo manual que toma el relevo, o
        // salida via finally). Holder en array de 1 para poder asignarlo dentro del
        // GUIRun y disponerlo despues.
        final InGameNotifyDialog[] reconnect_notify = {null};

        Helpers.GUIRun(() -> {
            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false,
                    Translator.translate("conn.reconectando_con_el_servidor"), Color.MAGENTA, Color.BLACK,
                    getClass().getResource("/images/action/plug.png"), null);
            reconnect_notify[0] = dialog;
            dialog.setLocation(dialog.getParent().getLocation());
            dialog.setVisible(true);
        });

        synchronized (getLocalClientSocketLock()) {

            try {

                boolean ok_rec;

                Socket curSock = net_client.getLocal_client_socket();
                if (curSock != null && !curSock.isClosed()) {
                    try {
                        curSock.shutdownInput();
                        curSock.shutdownOutput();
                        curSock.close();

                    } catch (Exception ex) {
                    }
                }

                net_client.setLocal_client_socket(null);

                long start = System.currentTimeMillis();

                ok_rec = false;

                Mac orig_sha256_HMAC = Mac.getInstance("HmacSHA256");

                orig_sha256_HMAC.init(net_client.getLocal_client_hmac_key_orig());

                String b64_nick = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));

                String b64_hmac_nick = Base64.getEncoder().encodeToString(orig_sha256_HMAC.doFinal(local_nick.getBytes("UTF-8")));

                do {

                    try {

                        String[] server_address = server_ip_port.split(":");

                        Socket newSock = new Socket(server_address[0], Integer.parseInt(server_address[1]));
                        net_client.setLocal_client_socket(newSock);

                        newSock.setTcpNoDelay(true);
                        newSock.setKeepAlive(true);

                        // Cerrojo anti-cuelgue/anti-DoS del handshake de RECONEXION, en
                        // paridad con cliente(): sin SO_TIMEOUT los reads del intercambio
                        // (pubkey del server, session_id) bloqueaban indefinidamente si el
                        // server aceptaba el TCP pero no enviaba datos -- y aqui ademas con
                        // local_client_socket_lock tomado y reconnecting=true, lo que
                        // congelaba TODO el transporte del cliente sin recuperacion. Se
                        // resetea a 0 (bloqueante) tras el ack, ya en estado estable.
                        newSock.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                        LOGGER.log(Level.WARNING, "Connected to server! Exchanging keys...");

                        // Le mandamos los bytes "mágicos"
                        newSock.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));

                        newSock.getOutputStream().flush();

                        /* INICIO INTERCAMBIO CLAVES */
                        KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("EC");

                        clientKpairGen.initialize(EC_KEY_LENGTH);

                        KeyPair clientKpair = clientKpairGen.generateKeyPair();

                        KeyAgreement clientKeyAgree = KeyAgreement.getInstance("ECDH");

                        clientKeyAgree.init(clientKpair.getPrivate());

                        byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();

                        DataOutputStream dOut = new DataOutputStream(newSock.getOutputStream());

                        dOut.writeInt(clientPubKeyEnc.length);

                        dOut.write(clientPubKeyEnc);

                        DataInputStream dIn = new DataInputStream(newSock.getInputStream());

                        int length = dIn.readInt();

                        // Cap defensivo (paridad con cliente()): un length malicioso/corrupto
                        // reservaria un byte[] gigante -> OOM. El handshake de reconexion no
                        // lo validaba.
                        if (length <= 0 || length > HANDSHAKE_MAX_PUBKEY_BYTES) {
                            throw new IOException("Reconnect handshake: invalid server pubkey length " + length
                                    + " (cap " + HANDSHAKE_MAX_PUBKEY_BYTES + ")");
                        }

                        byte[] serverPubKeyEnc = new byte[length];

                        dIn.readFully(serverPubKeyEnc, 0, serverPubKeyEnc.length);

                        // Identity: read session_id off the stream. On reconnect we don't
                        // recompute self_sig because the host already has our pinned identity,
                        // but we MUST consume these bytes to keep the stream in sync.
                        int sidLen = dIn.readInt();
                        if (sidLen <= 0 || sidLen > HANDSHAKE_MAX_SESSIONID_BYTES) {
                            throw new IOException("Reconnect handshake: invalid session_id length " + sidLen
                                    + " (cap " + HANDSHAKE_MAX_SESSIONID_BYTES + ")");
                        }
                        byte[] receivedSessionId = new byte[sidLen];
                        dIn.readFully(receivedSessionId, 0, sidLen);
                        this.session_id = receivedSessionId;

                        KeyFactory clientKeyFac = KeyFactory.getInstance("EC");

                        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(serverPubKeyEnc);

                        PublicKey serverPubKey = clientKeyFac.generatePublic(x509KeySpec);

                        clientKeyAgree.doPhase(serverPubKey, true);

                        byte[] clientSharedSecret = clientKeyAgree.generateSecret();

                        byte[] secret_hash = Helpers.deriveChannelSecret(clientSharedSecret, password);

                        net_client.setLocal_client_aes_key(new SecretKeySpec(secret_hash, 0, 32, "AES"));

                        net_client.setLocal_client_hmac_key(new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256"));

                        /* FIN INTERCAMBIO CLAVES */
                        // Le mandamos nuestro nick al server autenticado con la clave HMAC antigua
                        LOGGER.log(Level.WARNING, "Sending reconnection data...");

                        newSock.getOutputStream().write(
                                (Helpers.encryptCommand(b64_nick + "#" + AboutDialog.VERSION + "#*#*#" + b64_hmac_nick,
                                        net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()) + "\n").getBytes("UTF-8"));

                        newSock.getOutputStream().flush();

                        net_client.setLocal_client_buffer_read_is(new BufferedInputStream(newSock.getInputStream()));

                        // Esperar ack explícito del server: RECONNECT_OK acepta, cualquier
                        // RECONNECT_DENIED#<reason> o cierre limpio del socket significa
                        // que el server NO nos aceptó como reconnect. Sin este ack el
                        // cliente marcaba ok_rec=true en base solo a que el handshake
                        // criptográfico terminase sin excepción; cuando el server cerraba
                        // el socket inmediatamente (rama "DENIED" en el handler — caso
                        // típico tras halt-and-recover del server donde nuestro nick ya no
                        // está en participantes), el reader leía null al instante y
                        // volvía a llamar a reconectarCliente() en bucle sin pausa (la
                        // pausa de 5s solo aplica con ok_rec=false). Cada iteración
                        // creaba un Socket nuevo y un ECDH key exchange completo —
                        // freeze de UI y CPU al 100% reportado por yxmgl en issue#9 20.59.
                        //
                        // Usamos SO_TIMEOUT acotado para no quedarnos esperando un ack que
                        // no va a llegar (server muy lento o socket muerto silenciosamente).
                        // El finally restaura el timeout a 0 (blocking sin límite) para que
                        // las lecturas posteriores del runSocketReaderClientThread sigan
                        // siendo bloqueantes normales.
                        String ackLine;
                        try {
                            newSock.setSoTimeout(GameFrame.CLIENT_RECEPTION_TIMEOUT);
                            // Cap defensivo igual que el resto de readers del transporte
                            // (NetServer/NetClient/Participant). Si el server hipotético
                            // mandase bytes sin '\n' tras el handshake de reconexion,
                            // este readLine sin cap consumiría memoria hasta OOM. La
                            // protección de SoTimeout cubre hangs pero NO OOM por líneas
                            // largas.
                            // El ack (RECONNECT_OK/DENIED) es un frame de TEXTO. Si el server
                            // relayara un frame BINARIO (nota de voz/avatar) justo antes del
                            // ack, ackFrame.text() sería null y el reconnect VIVO se trataría
                            // como fallido. Saltamos los binarios (canal lateral best-effort,
                            // igual que el reader normal) y seguimos leyendo hasta el frame de
                            // texto (el ack) o null (socket muerto). El SO_TIMEOUT acota cada
                            // lectura.
                            // Saltamos hasta 8 frames binarios (canal lateral voz/avatar) antes
                            // del ack de TEXTO. ACOTADO: el SO_TIMEOUT solo limita cada lectura
                            // (hueco ocioso), NO el total; sin tope, un host (modelo zero-trust)
                            // que stremea binarios colgaba este bucle CON local_client_socket_lock
                            // tomado y reconnecting=true -> transporte del cliente congelado.
                            // Tras 8 binarios seguidos sin ack de texto, lo damos por fallido.
                            WireFrame.Result ackFrame = null;
                            for (int bin_skip = 0; bin_skip < 8; bin_skip++) {
                                ackFrame = WireFrame.read(
                                        net_client.getLocal_client_buffer_read_is(),
                                        Helpers.MAX_COMMAND_LINE_CHARS);
                                if (ackFrame == null || !ackFrame.isBinary()) {
                                    break;
                                }
                            }
                            ackLine = (ackFrame == null || ackFrame.isBinary()) ? null : ackFrame.text();
                        } catch (java.net.SocketTimeoutException ste) {
                            LOGGER.log(Level.WARNING, "Reconnect ack from server timed out — treating as failed reconnect");
                            ackLine = null;
                        } finally {
                            // Estado estable: el reader lee bloqueante (sin limite). Antes se
                            // restauraba oldTimeout (==0 en socket nuevo); ahora el socket llega
                            // aqui con HANDSHAKE_TIMEOUT_MS puesto (cerrojo del handshake), asi
                            // que hay que fijar 0 explicitamente. En la rama de fallo el socket
                            // se cierra despues, por lo que el valor es irrelevante alli.
                            try {
                                newSock.setSoTimeout(0);
                            } catch (Exception ignored) {
                            }
                        }

                        if (ackLine == null) {
                            throw new IOException("Server closed socket without sending reconnect ack");
                        }

                        // El ack DEBE venir autenticado: decryptCommand devuelve el texto tal
                        // cual si NO empieza por '*' (frame en claro), de modo que un
                        // "RECONNECT_OK" inyectado en claro por un atacante on-path pasaria el
                        // startsWith sin prueba criptografica. Exigimos frame cifrado ('*' ->
                        // decryptString verifica HMAC); si no, intento fallido.
                        if (!ackLine.trim().startsWith("*")) {
                            throw new IOException("Reconnect ack not authenticated (plaintext frame rejected)");
                        }

                        String ackDecrypted = Helpers.decryptCommand(ackLine,
                                net_client.getLocal_client_aes_key(),
                                net_client.getLocal_client_hmac_key());
                        if (ackDecrypted == null || !ackDecrypted.startsWith("RECONNECT_OK")) {
                            LOGGER.log(Level.WARNING, "Server denied reconnect: {0}", ackDecrypted);
                            throw new IOException("Server denied reconnect: " + ackDecrypted);
                        }

                        LOGGER.log(Level.INFO, "Reconnected successfully to server");

                        // Reset de contadores del PING/PONG defensivo cliente: si llevaban
                        // fallos contra el socket viejo, el primer fail contra el nuevo
                        // (puede ser jitter post-reconexion legitimo) no debe alcanzar el
                        // threshold ni cerrar el socket recien instalado. Equivalente al
                        // reset que hace Participant.resetSocket en el server.
                        net_client.setReset_ping_counters(true);

                        ok_rec = true;

                    } catch (Exception ex) {

                        LOGGER.log(Level.SEVERE, "Reconnection socket threw an exception");
                        LOGGER.log(Level.SEVERE, null, ex);

                    } finally {

                        if (!ok_rec) {

                            // (Antes aqui un toast rojo "no se pudo reconectar" por cada
                            // intento fallido: ademas de alarmante, su slot unico
                            // (LATEST_NOTIFICATION) ocultaba el indicador persistente de
                            // "reconectando" y, al auto-cerrarse a los 5s, reaparecia el
                            // hueco silencioso. El indicador persistente ya comunica que
                            // se sigue intentando.)
                            Socket failedSock = net_client.getLocal_client_socket();
                            if (failedSock != null && !failedSock.isClosed()) {

                                try {

                                    failedSock.close();

                                } catch (Exception ex) {
                                }

                                net_client.setLocal_client_socket(null);
                            }

                            if (!exit && (!WaitingRoomFrame.getInstance().isPartida_empezada()
                                    || !GameFrame.getInstance().getLocalPlayer().isExit())) {

                                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT
                                        && WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    // El dialogo manual (modal, con su propia barra) toma el
                                    // relevo como indicador: cerramos el toast persistente
                                    // para que no quede colgado detras del modal.
                                    Helpers.GUIRun(() -> {
                                        if (reconnect_notify[0] != null) {
                                            reconnect_notify[0].dispose();
                                            reconnect_notify[0] = null;
                                        }
                                    });

                                    if (net_client.getReconnect_dialog() == null) {

                                        Helpers.GUIRun(() -> {
                                            Reconnect2ServerDialog rd = new Reconnect2ServerDialog(
                                                    GameFrame.getInstance() != null ? GameFrame.getInstance() : THIS,
                                                    true, server_ip_port);
                                            net_client.setReconnect_dialog(rd);
                                            rd.setLocationRelativeTo(rd.getParent());
                                            rd.setVisible(true);
                                        });

                                    } else {
                                        net_client.getReconnect_dialog().setReconectar(false);

                                        Helpers.GUIRun(() -> {
                                            Reconnect2ServerDialog rd = net_client.getReconnect_dialog();
                                            rd.reset();
                                            rd.setLocationRelativeTo(rd.getParent());
                                            rd.setVisible(true);
                                        });
                                    }

                                    while (net_client.getReconnect_dialog() == null || !net_client.getReconnect_dialog().isReconectar()) {
                                        synchronized (net_client.getLock_reconnect()) {
                                            try {
                                                net_client.getLock_reconnect().wait(1000);
                                            } catch (InterruptedException ex) {
                                                Helpers.logCooperativeCancellation(LOGGER, "reconnect dialog wait", ex);
                                                break;
                                            }
                                        }
                                    }

                                    start = System.currentTimeMillis();
                                    server_ip_port = net_client.getReconnect_dialog().getIp_port().getText().trim();

                                } else {

                                    Helpers.pausar(GameFrame.CLIENT_RECON_ERROR_PAUSE);
                                }

                            }

                        }
                    }

                } while (!exit && !ok_rec && !timbaTerminada() && (!WaitingRoomFrame.getInstance().isPartida_empezada()
                        || !GameFrame.getInstance().getLocalPlayer().isExit()));

                if (net_client.getReconnect_dialog() != null) {

                    Helpers.GUIRunAndWait(() -> {
                        net_client.getReconnect_dialog().dispose();
                        net_client.setReconnect_dialog(null);
                    });
                }

                if (ok_rec) {
                    // Telemetría: contador de reconexiones exitosas
                    // del cliente. Se incrementa SÓLO en la rama positiva
                    // (la rama de fallo no entra a este if). Mirror del
                    // Participant.reconnection_count del lado servidor.
                    net_client.incrementReconnectionCount();

                    // Si el ping defensivo del cliente murió por el threshold de PONGs
                    // perdidos (closeClientSocket+break), reconectar NO lo reinicia solo:
                    // sin esto el cliente reconectado queda sin keepalive activo (un socket
                    // nuevo que se quede mudo solo se detectaría por write-fail, o nunca).
                    // Lo resucitamos tras un reconnect OK si murió; !exit para no arrancarlo
                    // en teardown. (Análogo a la resurrección del host en resetSocket.)
                    if (!exit && !net_client.isPingPongThreadAlive()) {
                        LOGGER.log(Level.INFO, "Client runPingPongThreadCliente was dead after reconnect — resurrecting");
                        runPingPongThreadCliente();
                    }

                    Audio.playWavResource("misc/yahoo.wav");

                    if (WaitingRoomFrame.getInstance().isPartida_empezada() && GameFrame.getInstance() != null) {
                        Helpers.GUIRun(() -> {
                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false,
                                    Translator.translate("conn.conexion_con_el_servidor_recuperada"), Color.GREEN,
                                    Color.WHITE, getClass().getResource("/images/action/plug.png"),
                                    NOTIFICATION_TIMEOUT);
                            dialog.setLocation(dialog.getParent().getLocation());
                            dialog.setVisible(true);
                        });
                    }
                }

                return ok_rec;

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } finally {
                // CRITICO: limpiar reconnecting + despertar a los que esperan DEBE ir en
                // el finally. reconnecting es el flag en el que se bloquean TODOS los
                // writes/reads y los getters de clave del cliente (NetClient +
                // getLocal_client_*_key) con while(reconnecting) wait(); si una excepcion
                // entre el fin del bucle y este punto (p.ej. yahoo.wav lanzando, o un NPE
                // al evaluar la condicion del bucle durante el teardown) lo dejaba en true,
                // el transporte del cliente quedaba colgado para SIEMPRE. El notifyAll se
                // hace aun dentro de synchronized(getLocalClientSocketLock()).
                net_client.setReconnecting(false);
                getLocalClientSocketLock().notifyAll();

                // Cierra el indicador persistente en CUALQUIER salida (exito, excepcion, o
                // fin del bucle por exit/timba terminada). En exito el toast verde ya lo ha
                // sustituido visualmente; esto ademas evita que un toast magenta quede
                // colgado tras una excepcion o una salida.
                Helpers.GUIRun(() -> {
                    if (reconnect_notify[0] != null) {
                        reconnect_notify[0].dispose();
                        reconnect_notify[0] = null;
                    }
                });
            }

        }

        return false;

    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par) {
        net_server.broadcastASYNCGAMECommand(command, par);
    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par, boolean confirmation) {
        net_server.broadcastASYNCGAMECommand(command, par, confirmation);
    }

    public JButton getImage_button() {
        return image_button;
    }

    public void sendASYNCGAMECommandFromServer(String command, Participant p) {
        net_server.sendASYNCGAMECommand(command, p);
    }

    public void sendASYNCGAMECommandFromServer(String command, Participant p, boolean confirmation) {
        net_server.sendASYNCGAMECommand(command, p, confirmation);
    }

    public JProgressBar getBarra() {
        return barra;
    }

    private void mostrarMensajeInformativo(Container container, String msg) {

        Helpers.mostrarMensajeInformativo(container, msg, "center", null, null);
    }

    private void mostrarMensajeInformativo(Container container, String msg, String align, Integer width) {

        Helpers.mostrarMensajeInformativo(container, msg, align, width, null);
    }

    private int mostrarMensajeInformativoSINO(Container container, String msg, ImageIcon icon) {

        return Helpers.mostrarMensajeInformativoSINO(container, msg, "center", null, icon);
    }

    private void mostrarMensajeError(Container container, String msg) {

        Helpers.mostrarMensajeError(container, msg, "center", null);

    }

    /** Parsea un CSV de base64 (formato del DUALLOCK_BUNDLE) a una lista de byte[]. */
    private static java.util.List<byte[]> csvToBytes(String csv) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String part : csv.split(",")) {
            if (!part.isEmpty()) {
                out.add(Base64.getDecoder().decode(part));
            }
        }
        return out;
    }

    private void runSocketReaderClientThread() {
        Helpers.threadRun(() -> {

            while (!exit) {

                String mensaje_recibido = null;

                try {
                    mensaje_recibido = readCommandFromServer();
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE,
                            (String) null, ex);
                }

                if (mensaje_recibido != null) {

                    String[] partes_comando = mensaje_recibido.split("#");

                    if (null == partes_comando[0]) {

                        try {
                            net_client.getLocal_client_socket_reader_queue().put(mensaje_recibido);
                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE,
                                    (String) null, ex);
                        }
                    } else {
                        switch (partes_comando[0]) {
                            // A malformed control frame (PING/PONG/PONG2 without its
                            // counter) must NOT kill the reader thread: that would leave
                            // the client zombie, blocked forever on the consumer's take()
                            // with no null-read to trigger reconnection. A peer on the
                            // same version always sends the counter, so ignoring the
                            // corrupt frame is strictly safer (mirrors the server-side
                            // guard in Participant.runSocketReaderThread).
                            case "PING":
                                if (partes_comando.length >= 2) {
                                    try {
                                        writeCommandToServer(
                                                "PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                    } catch (NumberFormatException nfe) {
                                    }
                                }
                                try {
                                    net_client.getLocal_client_socket_reader_queue().put(mensaje_recibido);
                                } catch (InterruptedException ex) {
                                    System.getLogger(Participant.class.getName())
                                            .log(System.Logger.Level.ERROR, (String) null, ex);
                                }
                                break;

                            case "PONG":
                                if (partes_comando.length >= 2) {
                                    try {
                                        net_client.setRemote_server_pong(Integer.valueOf(partes_comando[1]));
                                    } catch (NumberFormatException nfe) {
                                    }
                                    synchronized (ping_pong_lock) {
                                        ping_pong_lock.notifyAll();
                                    }
                                }
                                break;
                            case "PONG2":
                                if (partes_comando.length >= 2) {
                                    try {
                                        net_client.setRemote_server_pong2(Integer.valueOf(partes_comando[1]));
                                    } catch (NumberFormatException nfe) {
                                    }
                                    synchronized (ping_pong_lock) {
                                        ping_pong_lock.notifyAll();
                                    }
                                }
                                break;
                            default:
                                try {
                                    net_client.getLocal_client_socket_reader_queue().put(mensaje_recibido);
                                } catch (Exception ex) {
                                    LOGGER.log(Level.SEVERE,
                                            (String) null, ex);
                                }
                                break;
                        }
                    }

                } else {
                    try {
                        if (!net_client.getLocal_client_socket_reader_queue().contains(POISON_PILL)) {
                            net_client.getLocal_client_socket_reader_queue().put(POISON_PILL);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE,
                                (String) null, ex);
                    }

                    net_client.getCliente_last_received().clear();
                }

                if (mensaje_recibido == null) {
                    if (!exit && !timbaTerminada() && ((WaitingRoomFrame.getInstance() != null && !isPartida_empezada())
                            || (GameFrame.getInstance() != null && !GameFrame.getInstance().getLocalPlayer().isExit()))) {

                        if (!reconectarCliente()) {
                            exit = true;
                        }
                    } else {
                        // Si ya estábamos saliendo o la reconexión no aplica, aniquilamos el hilo
                        exit = true;
                    }
                    // Si nos rendimos (exit), despertar al consumidor bloqueado en take():
                    // el único POISON_PILL del null-read ya lo consumió durante el reconnect
                    // largo, así que sin este pill el hilo consumidor quedaría colgado
                    // (zombie de teardown).
                    if (exit) {
                        try {
                            net_client.getLocal_client_socket_reader_queue().put(POISON_PILL);
                        } catch (Exception ex) {
                        }
                    }
                }
            }

        });
    }

    private void runPingPongThreadCliente() {

        // --- PING/PONG KEEPALIVE THREAD ---
        net_client.setPingPongThreadAlive(true);
        Helpers.threadRun(() -> {

            int consecutive_ping_failures = 0;

            try {
            while (!exit && WaitingRoomFrame.getInstance() != null) {

                // Si reconectarCliente completo una reconexion durante el ultimo
                // ciclo, los contadores acumulados contra el socket viejo ya no
                // aplican. Reseteamos antes de enviar el primer PING al socket nuevo.
                if (net_client.isReset_ping_counters()) {
                    consecutive_ping_failures = 0;
                    net_client.setReset_ping_counters(false);
                }

                int ping = Helpers.CSPRNG_GENERATOR.nextInt();

                net_client.setRemote_server_pong(null);
                net_client.setRemote_server_pong2(null);
                net_client.setRemote_server_latency(-1);
                net_client.setRemote_server_latency2(-1);

                long pingStartNs = System.nanoTime();

                try {
                    writeCommandToServer("PING#" + ping);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE,
                            "Error dispatching PING", ex);
                    break;
                }

                long end = System.currentTimeMillis() + WaitingRoomFrame.PING_PONG_TIMEOUT;

                while (!exit && (net_client.getRemote_server_pong() == null || net_client.getRemote_server_pong2() == null)
                        && System.currentTimeMillis() < end) {
                    synchronized (ping_pong_lock) {
                        // Re-check dentro del monitor (igual que runPingPongThread del
                        // Participant): cierra el missed-notify del PONG y evita
                        // wait(0)/wait(<0) en la ventana de carrera del tiempo restante.
                        long remaining = end - System.currentTimeMillis();
                        if ((net_client.getRemote_server_pong() == null || net_client.getRemote_server_pong2() == null) && remaining > 0) {
                            try {
                                ping_pong_lock.wait(remaining);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }

                    Integer pong1 = net_client.getRemote_server_pong();
                    if (net_client.getRemote_server_latency() == -1 && pong1 != null
                            && pong1 == ping + 1) {

                        net_client.setRemote_server_latency(Math
                                .round((System.nanoTime() - pingStartNs) / 1_000_000));
                    }

                    Integer pong2 = net_client.getRemote_server_pong2();
                    if (net_client.getRemote_server_latency2() == -1 && pong2 != null
                            && pong2 == ping + 2) {

                        net_client.setRemote_server_latency2(Math
                                .round((System.nanoTime() - pingStartNs) / 1_000_000));
                    }
                }

                if (net_client.getRemote_server_latency() != -1) {

                    Helpers.GUIRun(() -> {
                        this.latency_label.setVisible(true);
                        this.latency_label.setText(Translator.translate("ui.latencia_servidor")
                                + " " + String.valueOf(net_client.getRemote_server_latency()) + " ms");
                    });
                }

                if (!exit && WaitingRoomFrame.getInstance() != null) {

                    Integer pong1 = net_client.getRemote_server_pong();
                    Integer pong2 = net_client.getRemote_server_pong2();
                    boolean round_ok = pong1 != null && pong1 == ping + 1
                            && pong2 != null && pong2 == ping + 2;

                    if (pong1 == null) {
                        LOGGER.log(Level.WARNING,
                                "Server failed to respond to PING");
                    } else if (pong1 != ping + 1) {
                        LOGGER.log(Level.WARNING,
                                "Invalid PONG from server");
                    } else if (pong2 == null) {
                        LOGGER.log(Level.WARNING,
                                "Server failed to respond to PING2");
                    } else if (pong2 != ping + 2) {
                        LOGGER.log(Level.WARNING,
                                "Invalid PONG2 from server");
                    } else if (DEV_MODE) {
                        LOGGER.log(Level.INFO,
                                "Server PONGs received (latency: {0} ms / {1} ms)",
                                new Object[]{net_client.getRemote_server_latency(), net_client.getRemote_server_latency2()});
                    }

                    // Red de seguridad: si el socket esta mudo (PONGs perdidos N rondas
                    // seguidas) cerramos local y dejamos que runSocketReaderClientThread
                    // detecte el null read y arranque reconectarCliente. La via primaria
                    // sigue siendo la IOException en write (NetClient.writeCommand).
                    if (round_ok) {
                        consecutive_ping_failures = 0;
                    } else {
                        consecutive_ping_failures++;
                        if (consecutive_ping_failures >= MAX_CONSECUTIVE_PING_FAILURES) {
                            LOGGER.log(Level.WARNING,
                                    "Client lost {0} consecutive PONGs — closing socket to force reconnect",
                                    consecutive_ping_failures);
                            // alive=false ANTES de cerrar: así reconectarCliente ve el thread
                            // muerto y lo resucita. Sin esto, en la ventana break->finally el
                            // chequeo de resurrección veía alive=true y no relanzaba.
                            net_client.setPingPongThreadAlive(false);
                            closeClientSocket();
                            break;
                        }
                    }

                    // Telemetría: actualizar también el LatencyDot del
                    // LocalPlayer del cliente con su propia medición al server
                    // + su contador de reconexiones. Esto da feedback INMEDIATO
                    // (no espera al TELEMETRY broadcast del host) sobre la
                    // calidad del enlace local.
                    try {
                        if (GameFrame.getInstance() != null
                                && GameFrame.getInstance().getLocalPlayer() instanceof LocalPlayer) {
                            ((LocalPlayer) GameFrame.getInstance().getLocalPlayer()).applyTelemetry(
                                    net_client.getRemote_server_latency(),
                                    net_client.getRemote_server_latency2(),
                                    net_client.getReconnectionCount());
                        }
                    } catch (Exception ex) {
                        // Best-effort visualization; no afecta lógica de juego.
                    }

                    Helpers.pausar(PING_INTERVAL_MS);
                }

            }
            } finally {
                net_client.setPingPongThreadAlive(false);
            }
        });
    }

    private void cliente() {
        Helpers.threadRun(() -> {

            do {
                Helpers.GUIRun(() -> {
                    status.setForeground(new Color(51, 153, 0));
                    Helpers.barraIndeterminada(barra);
                    status.setText(Translator.translate("status.conectando"));
                });
                booting = true;

                String recibido;
                String[] partes;

                try {
                    String[] direccion = server_ip_port.split(":");
                    Socket sock = new Socket(direccion[0], Integer.parseInt(direccion[1]));
                    net_client.setLocal_client_socket(sock);
                    sock.setTcpNoDelay(true);
                    sock.setKeepAlive(true);
                    // Cerrojo anti-DoS: si el servidor NO termina el handshake en
                    // HANDSHAKE_TIMEOUT_MS, los reads bloqueados (pubkey server, session_id,
                    // NICKOK/intro/chat-history) lanzan SocketTimeoutException y caemos al
                    // catch en lugar de eternizar el thread. Se RESETEA a 0 mas abajo en
                    // cuanto el NICKOK ha sido procesado y nuevoParticipante creado.
                    sock.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                    sock.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));
                    sock.getOutputStream().flush();

                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("status.intercambio_claves"));
                    });

                    /* INICIO INTERCAMBIO DE CLAVES LIMPIO */
                    KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("EC");
                    clientKpairGen.initialize(EC_KEY_LENGTH);
                    KeyPair clientKpair = clientKpairGen.generateKeyPair();
                    KeyAgreement clientKeyAgree = KeyAgreement.getInstance("ECDH");
                    clientKeyAgree.init(clientKpair.getPrivate());
                    byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();
                    DataOutputStream dOut = new DataOutputStream(sock.getOutputStream());
                    dOut.writeInt(clientPubKeyEnc.length);
                    dOut.write(clientPubKeyEnc);

                    DataInputStream dIn = new DataInputStream(sock.getInputStream());
                    int length = dIn.readInt();
                    if (length <= 0 || length > HANDSHAKE_MAX_PUBKEY_BYTES) {
                        throw new IOException("Handshake: invalid server pubkey length " + length
                                + " (cap " + HANDSHAKE_MAX_PUBKEY_BYTES + ")");
                    }
                    byte[] serverPubKeyEnc = new byte[length];
                    dIn.readFully(serverPubKeyEnc, 0, serverPubKeyEnc.length);
                    // Identity: capture session_id sent right after the server pubkey.
                    int sidLen = dIn.readInt();
                    if (sidLen <= 0 || sidLen > HANDSHAKE_MAX_SESSIONID_BYTES) {
                        throw new IOException("Handshake: invalid session_id length " + sidLen
                                + " (cap " + HANDSHAKE_MAX_SESSIONID_BYTES + ")");
                    }
                    byte[] receivedSessionId = new byte[sidLen];
                    dIn.readFully(receivedSessionId, 0, sidLen);
                    this.session_id = receivedSessionId;

                    KeyFactory clientKeyFac = KeyFactory.getInstance("EC");
                    X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(serverPubKeyEnc);
                    PublicKey serverPubKey = clientKeyFac.generatePublic(x509KeySpec);
                    clientKeyAgree.doPhase(serverPubKey, true);
                    byte[] clientSharedSecret = clientKeyAgree.generateSecret();
                    byte[] secret_hash = Helpers.deriveChannelSecret(clientSharedSecret, password);
                    SecretKeySpec aesKey = new SecretKeySpec(secret_hash, 0, 32, "AES");
                    SecretKeySpec hmacKey = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    net_client.setLocal_client_aes_key(aesKey);
                    net_client.setLocal_client_hmac_key(hmacKey);
                    net_client.setLocal_client_hmac_key_orig(hmacKey);
                    /* FIN INTERCAMBIO DE CLAVES */

                    byte[] avatar_bytes = null;
                    if (local_avatar != null && local_avatar.length() > 0) {
                        try (FileInputStream is = new FileInputStream(local_avatar)) {
                            avatar_bytes = is.readAllBytes();
                        }
                    }

                    // PSK-DH already authenticates the password: a wrong password produces a different
                    // channel key and the server cannot decrypt this message. No need to send the password.
                    //
                    // Identity: augment the first command with the JOIN_IDENTITY marker, this
                    // installation's Ed25519 pubkey, and a self_sig that binds it to the session_id
                    // received during the handshake. Field layout (6 fields):
                    //   nick_b64 # version # avatar_b64_or_* # JOIN # pubkey_b64 # self_sig_b64
                    // The server validates self_sig before adding the client to the participants
                    // list; an invalid signature closes the socket.
                    IdentityManager im = IdentityManager.getInstance();
                    if (!im.isReady()) {
                        exit = true;
                        mostrarMensajeError(THIS, Translator.translate("ui.error.identity_not_ready", im.getLoadError()));
                        throw new IOException("Identity not ready, refusing to JOIN");
                    }
                    String pubkeyB64 = Base64.getEncoder().encodeToString(im.getPublicKey());
                    String selfSigB64 = Base64.getEncoder().encodeToString(im.signJoin(this.session_id, local_nick));
                    writeCommandToServer(Helpers.encryptCommand(Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"))
                            + "#" + AboutDialog.VERSION
                            + (avatar_bytes != null ? "#" + Base64.getEncoder().encodeToString(avatar_bytes) : "#*")
                            + "#JOIN"
                            + "#" + pubkeyB64
                            + "#" + selfSigB64,
                            aesKey, hmacKey));

                    net_client.setLocal_client_buffer_read_is(new BufferedInputStream(sock.getInputStream()));
                    recibido = readCommandFromServer();

                    if (recibido == null) {
                        // The server closed the channel before answering. With PSK-DH this almost always
                        // means a wrong password (the server could not decrypt the auth message) or, much
                        // less likely, an active MITM on the network path.
                        exit = true;
                        mostrarMensajeError(THIS, Translator.translate("conn.secure_channel_failed"));
                        throw new IOException("Secure channel not established");
                    }

                    partes = recibido.split("#");

                    switch (partes[0]) {
                        case "BADVERSION":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("error.version_incorrecta") + " " + Translator.translate("ui.requerida") + " -> " + partes[1]);
                            break;
                        case "YOUARELATE":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("conn.late"));
                            break;
                        case "NOSPACE":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("conn.full"));
                            break;
                        case "NICKFAIL":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("conn.nick_taken"));
                            break;
                        case "NICKOK":
                            if ("0".equals(partes[1])) {
                                Helpers.GUIRun(() -> {
                                    pass_icon.setVisible(false);
                                });
                            }

                            gameinfo_original = new String(Base64.getDecoder().decode(partes[2].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8");

                            // Cuarto campo (opcional) del NICKOK: espejo COMPLETO de la config
                            // para la pestaña Partida en solo-lectura. Solo se guarda (NO se
                            // escribe GameFrame.*); la config real llega en el INIT al arrancar.
                            if (partes.length > 3) {
                                GAMECONFIG_MIRROR = new String(Base64.getDecoder().decode(partes[3].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8");
                            }

                            Helpers.GUIRun(() -> {
                                status.setText(Translator.translate("status.recibiendo_info_servidor"));
                                applyGameInfoBuyinLabel(gameinfo_original.split("\\|"));
                            });

                            recibido = readCommandFromServer();
                            if (recibido == null) {
                                // Server dropped after NICKOK, before sending its identity payload.
                                exit = true;
                                throw new IOException("Server closed channel during nick handshake");
                            }
                            partes = recibido.split("#");
                            server_nick = new String(Base64.getDecoder().decode(partes[0].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8").trim();

                            String server_avatar_base64 = partes.length > 1 && !"*".equals(partes[1])
                                    ? partes[1].replaceAll("[^A-Za-z0-9+/=]", "")
                                    : "";
                            File server_avatar = null;
                            try {
                                if (server_avatar_base64.length() > 0) {
                                    int file_id = Math.abs(Helpers.CSPRNG_GENERATOR.nextInt());
                                    server_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + Helpers.safeNickForFilename(server_nick) + "_avatar" + file_id);
                                    server_avatar.deleteOnExit();
                                    try (FileOutputStream os = new FileOutputStream(server_avatar)) {
                                        os.write(Base64.getDecoder().decode(server_avatar_base64));
                                    }
                                }
                            } catch (Exception ex) {
                                server_avatar = null;
                            }

                            // Identity: host identity rides on the same intro packet that
                            // carries nick + avatar. Capture pubkey+sig here; verify and apply
                            // once the Participant exists.
                            byte[] hostIdPubkey = null;
                            byte[] hostIdSig = null;
                            if (partes.length >= 4 && !"*".equals(partes[2]) && !"*".equals(partes[3])) {
                                try {
                                    hostIdPubkey = Base64.getDecoder().decode(partes[2]);
                                    hostIdSig = Base64.getDecoder().decode(partes[3]);
                                } catch (Exception ex) {
                                    hostIdPubkey = null;
                                    hostIdSig = null;
                                }
                            }

                            recibido = readCommandFromServer();
                            if (recibido == null) {
                                // Server dropped before sending chat history sentinel.
                                exit = true;
                                throw new IOException("Server closed channel during chat handshake");
                            }

                            if (!"*".equals(recibido)) {
                                chat_text = new StringBuffer(new String(Base64.getDecoder().decode(recibido.replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8"));
                            }

                            nuevoParticipante(server_nick, server_avatar, null, null, null, false, THIS.isUnsecure_server());
                            nuevoParticipante(local_nick, local_avatar, null, null, null, false, false);

                            // Handshake completado: los reads subsiguientes del cliente (GAME,
                            // PING/PONG, chat) deben poder esperar indefinido. Quitamos el deadline
                            // de handshake. La reconexion gestiona su propio timeout en su flujo.
                            try {
                                Socket localSock = net_client.getLocal_client_socket();
                                if (localSock != null && !localSock.isClosed()) {
                                    localSock.setSoTimeout(0);
                                }
                            } catch (Exception ex) {
                                LOGGER.log(Level.WARNING, "Could not clear handshake SoTimeout on client post-NICKOK", ex);
                            }

                            // Identity: apply the host's identity to the freshly-created
                            // Participant. Verify self_sig against current session_id; on success,
                            // store on Participant and run TOFU.
                            if (hostIdPubkey != null && hostIdSig != null
                                    && hostIdPubkey.length == 32 && hostIdSig.length == 64) {
                                if (!IdentityManager.verifyJoin(this.session_id, server_nick, hostIdPubkey, hostIdSig)) {
                                    LOGGER.log(Level.WARNING, "Intro identity bad self_sig for host {0}", server_nick);
                                } else {
                                    TOFUResolver.Resolution res = TOFUResolver.resolve(server_nick, hostIdPubkey);
                                    Participant hostPar = participantes.get(server_nick);
                                    if (hostPar != null) {
                                        hostPar.setIdentity_pubkey(hostIdPubkey);
                                        hostPar.setIdentity_self_sig(hostIdSig);
                                    }
                                    LOGGER.log(Level.INFO, "TOFU: {0} -> {1} (sessions={2}, verified={3}) via intro",
                                            new Object[]{server_nick, res.getOutcome(), res.getSessionsCount(), res.isVerifiedOob()});
                                }
                            } else {
                                LOGGER.log(Level.WARNING, "Intro carried no host identity for {0}", server_nick);
                            }

                            Helpers.GUIRunAndWait(() -> {
                                status.setText(Translator.translate("status.conectado"));
                                status.setIcon(new ImageIcon(getClass().getResource("/images/emoji_chat/1.png")));
                                barra.setVisible(false);
                                chat_box.setEnabled(true);
                                emoji_button.setEnabled(true);
                                image_button.setEnabled(true);
                                max_min_label.setEnabled(true);

                            });

                            refreshChatPanel();
                            booting = false;

                            runSocketReaderClientThread();
                            runPingPongThreadCliente();

                            // Fully connected and reading: kick off the stats DB sync
                            // (background, non-blocking; no-op if both prefs are off).
                            statsSyncOnConnectedToServer();

                            do {
                                recibido = net_client.getLocal_client_socket_reader_queue().take();

                                if (!POISON_PILL.equals(recibido)) {
                                    String[] partes_comando = recibido.split("#");

                                    // A single malformed/unprocessable frame (missing segment,
                                    // bad number, etc.) must NOT tear down the whole game session
                                    // via the outer catch: log it and skip to the next frame, like
                                    // the host does per-command. The switch body keeps its original
                                    // indentation on purpose to keep this a minimal, merge-safe diff.
                                    try {
                                    switch (partes_comando[0]) {
                                        case "PING":
                                            writeCommandToServer("PONG2#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 2));
                                            break;

                                        case "CHAT":
                                            String mensaje = (partes_comando.length == 3) ? new String(Base64.getDecoder().decode(partes_comando[2]), "UTF-8") : "";
                                            recibirMensajeChat(new String(Base64.getDecoder().decode(partes_comando[1]), "UTF-8"), mensaje);
                                            break;
                                        case "EXIT":
                                            exit = true;
                                            mostrarMensajeError(THIS, Translator.translate("game.el_servidor_ha_cancelado_la"));
                                            break;
                                        case "KICKED":
                                            exit = true;
                                            Audio.playWavResource("loser/payaso.wav");
                                            mostrarMensajeInformativo(THIS, Translator.translate("ui.error.kicked_out"));
                                            break;

                                        case "GAME":
                                            String subcomando = partes_comando[2];
                                            int id = Integer.parseInt(partes_comando[1]);

                                            try {
                                                String confMsg = "CONF#" + String.valueOf(id + 1) + "#OK";
                                                this.writeCommandToServer(Helpers.encryptCommand(confMsg, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                            } catch (Exception e) {
                                            }

                                            if (!net_client.getCliente_last_received().containsKey(subcomando) || !net_client.getCliente_last_received().get(subcomando).equals(id)) {
                                                net_client.getCliente_last_received().put(subcomando, id);
                                                if (isPartida_empezada()) {
                                                    switch (subcomando) {
                                                        case "DECK_CASCADE_REQ":
                                                            final String[] partes_cascade = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    // ZERO-TRUST: si ya cazamos una trampa de este host en esta
                                                                    // sesión, nunca más generamos una clave para él. La
                                                                    // promesa zero-trust ("si detectamos trampa, no entregamos
                                                                    // más claves") sólo se cumple si el lockdown es un gate
                                                                    // duro, no sólo un popup.
                                                                    if (Crupier.SECURITY_LOCKDOWN) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ refused — security lockdown active");
                                                                        return;
                                                                    }
                                                                    // ZERO-TRUST: refuse cascade mid-hand. Si ya tenemos un
                                                                    // MEGAPACKET activo, aceptar un nuevo cascade sobreescribiría
                                                                    // nuestro sra_unlock y destruiría la mano en curso. Un host
                                                                    // honesto NUNCA pide DECK_CASCADE_REQ después del MEGAPACKET
                                                                    // hasta NUEVA_MANO (que limpia local_mega_packet a null).
                                                                    Crupier crupierCheck = GameFrame.getInstance().getCrupier();
                                                                    if (crupierCheck != null && crupierCheck.hasMegaPacket()) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ received mid-hand (MEGAPACKET already locked) — refusing to overwrite my sra_unlock");
                                                                        crupierCheck.triggerSecurityLockdown(Translator.translate("zero_trust.host_cascade_mid_hand"));
                                                                        return;
                                                                    }

                                                                    byte[] incomingDeck = Base64.getDecoder().decode(partes_cascade[3]);

                                                                    // Dual-lock (Opción G): el cliente necesita el Crupier para guardar
                                                                    // el lock community que aplicará durante la fase de rotación. Si por
                                                                    // alguna razón el Crupier no existe aún, refusar — un host sano nunca
                                                                    // pide DECK_CASCADE_REQ antes de que el cliente tenga Crupier.
                                                                    if (crupierCheck == null) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ received before Crupier exists — refusing");
                                                                        return;
                                                                    }

                                                                    // ZERO-TRUST: el host nos pide aplicar nuestro lock al deck que envía.
                                                                    // Si el deck no son 52 puntos válidos de Curve25519, es basura
                                                                    // (downgrade del host: enviarnos bytes inválidos para que gastemos
                                                                    // nuestro shuffle/lock sobre datos no recuperables, o smuggling).
                                                                    // Rechazar antes de comprometer nuestro sra_unlock recién generado.
                                                                    if (incomingDeck == null || incomingDeck.length != 1664 || !RistrettoSRA.arePointsValid(incomingDeck)) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ payload is not a valid 52-point curve deck (len={0}) — refusing",
                                                                                incomingDeck == null ? -1 : incomingDeck.length);
                                                                        crupierCheck.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                        return;
                                                                    }

                                                                    byte[] lockScalar = RistrettoSRA.generateLockScalar();
                                                                    byte[] unlockScalar = RistrettoSRA.getUnlockScalar(lockScalar);
                                                                    this.participantes.get(local_nick).setSra_unlock(unlockScalar);

                                                                    // Dual-lock (Opción G): segundo par de scalars para la rotación de
                                                                    // community pieces que vendrá después de la cascade. Se guardan en
                                                                    // el Crupier para que el handler de DECK_ROTATION_REQ los recupere
                                                                    // sin tener que pedir más entropía nueva entonces.
                                                                    byte[] communityLockScalar = RistrettoSRA.generateLockScalar();
                                                                    byte[] communityUnlockScalar = RistrettoSRA.getUnlockScalar(communityLockScalar);
                                                                    crupierCheck.local_sra_lock_community = communityLockScalar;
                                                                    crupierCheck.local_sra_unlock_community = communityUnlockScalar;
                                                                    this.participantes.get(local_nick).setSra_unlock_community(communityUnlockScalar);
                                                                    // Anti-replay: nueva cascada (o reintento legítimo) habilita UNA
                                                                    // rotación. Una segunda rotación sin pasar por aquí será rechazada.
                                                                    crupierCheck.rotation_served_this_cascade = false;

                                                                    byte[] locked = RistrettoSRA.applyCommutativeLock(incomingDeck, lockScalar);

                                                                    // Generate fresh local entropy for THIS shuffle on the spot.
                                                                    // The handler runs on an async thread that may fire before the
                                                                    // local Crupier reaches readyForNextHand() and sets
                                                                    // local_hand_seed for the new hand. Reading c.getLocal_hand_seed()
                                                                    // there used to yield null (first hand) or stale (previous
                                                                    // hand's seed), which either threw an NPE inside shuffleDeck
                                                                    // — silently aborting the hand from the host's perspective —
                                                                    // or reused stale entropy. The seed never leaves this process
                                                                    // so there is no protocol reason to share it with the Crupier.
                                                                    byte[] mySeed = new byte[48];
                                                                    Helpers.CSPRNG_GENERATOR.nextBytes(mySeed);
                                                                    byte[] shuffled = DeterministicShuffle.shuffleDeck(locked, mySeed);

                                                                    // (last-mile lockdown re-check eliminado — ver nota
                                                                    // equivalente en REQ_SRA_UNLOCK. La gate al inicio del
                                                                    // handler ya impide procesar requests nuevas
                                                                    // post-lockdown. Mantenerla aquí dejaba al host colgado
                                                                    // indefinidamente cuando un duplicate concurrente
                                                                    // disparaba lockdown durante el procesamiento de la
                                                                    // request legítima.)

                                                                    String b64Deck = Base64.getEncoder().encodeToString(shuffled);
                                                                    String myNickB64 = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));

                                                                    int respId = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                    // Enviar los commitments K=k*B (pocket y community) junto al
                                                                    // deck cascadeado, para que el host los agregue y se anclen en H_0.
                                                                    String kPocketB64 = Base64.getEncoder().encodeToString(RistrettoSRA.commitment(lockScalar));
                                                                    String kCommunityB64 = Base64.getEncoder().encodeToString(RistrettoSRA.commitment(communityLockScalar));
                                                                    // Prueba de barajado verificable de ESTE paso de cascada
                                                                    // (deckOut = shuffle(k·deckIn)). El host la agrega a la cadena que TODOS
                                                                    // verifican, así un host modificado no puede colar una carta. "" si la
                                                                    // generación falla (peer legacy / degradado): el host lo trata como
                                                                    // ausente (sin enforcement todavía).
                                                                    int myPermN = incomingDeck.length / 32;
                                                                    int[] myPerm = DeterministicShuffle.shufflePermutation(myPermN, mySeed);
                                                                    byte[] cascadeProof = com.tonikelope.coronapoker.crypto.ShuffleCascade
                                                                            .proveStepWire(incomingDeck, shuffled, myPerm, lockScalar);
                                                                    String proofB64 = (cascadeProof != null)
                                                                            ? Base64.getEncoder().encodeToString(cascadeProof) : "";
                                                                    writeCommandToServer(Helpers.encryptCommand("GAME#" + respId + "#DECK_CASCADE_RESP#" + myNickB64 + "#" + b64Deck + "#" + kPocketB64 + "#" + kCommunityB64 + "#" + proofB64, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE, "Failed to process DECK_CASCADE_REQ; host will time out and abort the hand", e);
                                                                }
                                                            });
                                                            break;

                                                        case "DECK_ROTATION_REQ":
                                                            // Dual-lock (Opción G): tras la cascade principal, el host pide a cada
                                                            // peer en orden que aplique sobre las community pieces uPocket (quita su
                                                            // lock pocket) + kCommunity (añade su lock community). Resultado: las
                                                            // community pieces quedan cifradas SOLO con scalars de community y su
                                                            // unlock se entrega luego separadamente del de pocket.
                                                            final String[] partes_rotation = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    if (Crupier.SECURITY_LOCKDOWN) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ refused — security lockdown active");
                                                                        return;
                                                                    }
                                                                    Crupier crupierRot = GameFrame.getInstance().getCrupier();
                                                                    if (crupierRot == null
                                                                            || crupierRot.local_sra_lock_community == null) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ without community lock (Crupier or local_sra_lock_community null) — refusing");
                                                                        return;
                                                                    }
                                                                    // Anti-replay: solo UNA rotación por cascada. Una segunda sin
                                                                    // nueva cascada = host hostil usando la rotación como oráculo de
                                                                    // pocket-unlock encubierto (intento de leer cartas de un peer que sale).
                                                                    if (crupierRot.rotation_served_this_cascade) {
                                                                        // No es fatal: rechazamos la rotación extra (el oráculo no obtiene
                                                                        // nada) y la partida PUEDE continuar con la rotación legítima ya
                                                                        // servida. Política: avisar + continuar (presumiendo buena fe), no
                                                                        // congelar. El usuario decide si abandona.
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ replay (2nd rotation this cascade) — refusing extra rotation, warning user (game may continue)");
                                                                        crupierRot.warnSuspiciousHost(Translator.translate("zero_trust.host_rotation_replay"));
                                                                        return;
                                                                    }
                                                                    // El unlock pocket del cliente vive en el Participant local (la cascade
                                                                    // handler lo guarda ahí, no en el Crupier — la mitad pocket no se
                                                                    // "publica" en Crupier hasta que el MEGAPACKET llega y el cliente
                                                                    // lo copia desde Participant). Para la rotación necesitamos uPocket
                                                                    // (quitar nuestro lock pocket) + kCommunity (añadir nuestro lock
                                                                    // community), así que leemos uPocket del Participant directamente.
                                                                    byte[] myPocketUnlock = this.participantes.get(local_nick).getSra_unlock();
                                                                    if (myPocketUnlock == null) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ without local pocket unlock (Participant.sra_unlock null) — refusing");
                                                                        return;
                                                                    }
                                                                    if (partes_rotation.length < 4) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ malformed wire (parts={0}) — refusing", partes_rotation.length);
                                                                        return;
                                                                    }
                                                                    byte[] incomingPieces = Base64.getDecoder().decode(partes_rotation[3]);
                                                                    // ZERO-TRUST: payload debe ser un múltiplo de 32 bytes (32-byte points)
                                                                    // y todos los chunks deben estar en la curva. La longitud exacta
                                                                    // depende del número de jugadores del ring del host; no la
                                                                    // re-derivamos aquí porque el cliente no la conoce, pero un payload
                                                                    // no-curve es siempre rechazado.
                                                                    if (incomingPieces == null
                                                                            || incomingPieces.length == 0
                                                                            || incomingPieces.length % 32 != 0
                                                                            || !RistrettoSRA.arePointsValid(incomingPieces)) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ payload not a valid curve-point block (len={0}) — refusing",
                                                                                incomingPieces == null ? -1 : incomingPieces.length);
                                                                        crupierRot.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                        return;
                                                                    }
                                                                    // Rotación: aplicar uPocket + kCommunity en ese orden. El resultado
                                                                    // mantiene la longitud y sigue siendo válido en la curva (el output
                                                                    // de scalar mult sobre puntos en la curva permanece en la curva).
                                                                    byte[] rotated = RistrettoSRA.applyCommutativeLock(incomingPieces, myPocketUnlock);
                                                                    rotated = RistrettoSRA.applyCommutativeLock(rotated, crupierRot.local_sra_lock_community);
                                                                    // Rotación servida: cualquier otra esta cascada se rechaza (anti-replay).
                                                                    crupierRot.rotation_served_this_cascade = true;

                                                                    // Cierre del flanco rotacion: pruebo que mi paso es un re-key en sitio honesto
                                                                    // (out[i]=s*in[i], s=uPocket*kCommunity), sin relocalizar ni duplicar. El host
                                                                    // lo anexa al bundle para que todos verifiquen la cadena genesis->MEGAPACKET.
                                                                    String rotProofB64 = "";
                                                                    try {
                                                                        java.math.BigInteger sRot = com.tonikelope.coronapoker.crypto.RistrettoSRA.bytesToScalar(myPocketUnlock)
                                                                                .multiply(com.tonikelope.coronapoker.crypto.RistrettoSRA.bytesToScalar(crupierRot.local_sra_lock_community))
                                                                                .mod(com.tonikelope.coronapoker.crypto.EdwardsPoint.L);
                                                                        com.tonikelope.coronapoker.crypto.EdwardsPoint[] inR = com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(incomingPieces);
                                                                        com.tonikelope.coronapoker.crypto.EdwardsPoint[] outR = com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(rotated);
                                                                        if (inR != null && outR != null) {
                                                                            byte[] rp = com.tonikelope.coronapoker.crypto.DualLockWire.encodeRotationProof(
                                                                                    com.tonikelope.coronapoker.crypto.RotationProof.prove(sRot, inR, outR));
                                                                            if (rp != null) {
                                                                                rotProofB64 = Base64.getEncoder().encodeToString(rp);
                                                                            }
                                                                        }
                                                                    } catch (Exception rotProofEx) {
                                                                        rotProofB64 = ""; // sin prueba -> el host marca el paso como remoto-pendiente, no rompe nada
                                                                    }

                                                                    String b64Rot = Base64.getEncoder().encodeToString(rotated);
                                                                    String myNickB64Rot = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));
                                                                    int respIdRot = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                    writeCommandToServer(Helpers.encryptCommand("GAME#" + respIdRot + "#DECK_ROTATION_RESP#" + myNickB64Rot + "#" + b64Rot + "#" + rotProofB64, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE, "Failed to process DECK_ROTATION_REQ; host will time out and abort the hand", e);
                                                                }
                                                            });
                                                            break;

                                                        case "DUALLOCK_BUNDLE":
                                                            // Cada peer verifica POR SU CUENTA que el reparto es un
                                                            // barajado+rotacion honesto genesis->MEGAPACKET. pocketCount se deriva
                                                            // LOCAL (active_crypto_ring.length*2), NUNCA del host, y el genesis se
                                                            // recomputa. Si falla -> avisar+recomendar salir pero PERMITIR seguir
                                                            // (por si es bug), no abort duro. Background, no toca UI.
                                                            final String[] partes_bundle = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                Crupier cruB = GameFrame.getInstance().getCrupier();
                                                                // Estado aun no listo (carrera con el procesado del MEGAPACKET): NO es
                                                                // sospechoso, lo ignoramos sin avisar.
                                                                if (cruB == null || cruB.local_mega_packet == null || cruB.active_crypto_ring == null) {
                                                                    return;
                                                                }
                                                                // Un bundle para este mazo LLEGO del host: marcalo antes de parsear/verificar.
                                                                // Distingue en el recibo el peer lento (recibido, cola pendiente -> benigno) del
                                                                // host que no manda la prueba (received != mazo vivo -> aviso a la mesa). Aunque
                                                                // venga malformado/no parseable, el host mando ALGO -> cuenta como recibido (esos
                                                                // casos ya disparan su propio warnSuspiciousHost en vivo mas abajo).
                                                                cruB.dual_lock_bundle_received_for = cruB.local_mega_packet;
                                                                // Un bundle RECIBIDO pero malformado (canal AES+HMAC -> vino del host
                                                                // intacto) es anomalo: un host honesto siempre manda 7 campos validos.
                                                                if (partes_bundle.length < 7) {
                                                                    LOGGER.log(Level.SEVERE, "DUALLOCK_BUNDLE malformed (fields={0}) — warning user", partes_bundle.length);
                                                                    cruB.warnSuspiciousHost(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                    return;
                                                                }
                                                                try {
                                                                    // SNAPSHOT inmutable de ESTE mazo+bundle y a la cola serial. El verify
                                                                    // corre contra este snapshot, NO contra el local_mega_packet vivo: una
                                                                    // mano nueva ya no puede clobbear la verificacion de esta, y un equipo
                                                                    // lento la termina igual aunque la mano haya avanzado (cazando un
                                                                    // smuggle pasado). El veredicto vuelve por el Sink (ver Crupier).
                                                                    byte[] genesisB = com.tonikelope.coronapoker.crypto.RistrettoSRA.getGenesisDeck();
                                                                    int pocketCount = cruB.active_crypto_ring.length * 2; // PEER-DERIVED
                                                                    ShuffleVerificationQueue.Job job = new ShuffleVerificationQueue.Job(
                                                                            genesisB, csvToBytes(partes_bundle[3]), csvToBytes(partes_bundle[4]),
                                                                            pocketCount, cruB.local_mega_packet,
                                                                            csvToBytes(partes_bundle[5]), csvToBytes(partes_bundle[6]),
                                                                            cruB.getMano());
                                                                    cruB.getShuffleVerifyQueue().enqueue(job);
                                                                } catch (Exception bundleEx) {
                                                                    // No parseable (base64 invalido, etc.) = anomalo pero ambiguo -> avisar.
                                                                    // (Solo los jobs que SI parsean y fallan la prueba se reportan como
                                                                    // "deshonesto probado" desde la cola; esto es solo malformacion.)
                                                                    LOGGER.log(Level.SEVERE, "DUALLOCK_BUNDLE unparseable — warning user", bundleEx);
                                                                    cruB.warnSuspiciousHost(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                }
                                                            });
                                                            break;

                                                        case "REQ_SRA_UNLOCK_CHAIN":
                                                            // Unlock batch VERIFICABLE. Por cada punto, el host envia la
                                                            // cadena DealChain de los peers previos; este peer la verifica contra SU
                                                            // MEGAPACKET comprometido y, si es valida, aplica su unlock con prueba
                                                            // DLEQ y extiende la cadena. El host nunca le manda el punto a descifrar
                                                            // (solo offset + pruebas), asi que el cegado es imposible.
                                                            final String[] partes_chain = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    if (Crupier.SECURITY_LOCKDOWN) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN refused — security lockdown active");
                                                                        return;
                                                                    }
                                                                    if (partes_chain.length < 6) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN malformed wire (parts={0}) — refusing", partes_chain.length);
                                                                        return;
                                                                    }
                                                                    int phase;
                                                                    int hand_id;
                                                                    try {
                                                                        phase = Integer.parseInt(partes_chain[3]);
                                                                        hand_id = Integer.parseInt(partes_chain[4]);
                                                                    } catch (NumberFormatException nfe) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN non-numeric phase/hand_id — refusing");
                                                                        return;
                                                                    }
                                                                    String payloadChain = partes_chain[5];
                                                                    Crupier crupier = GameFrame.getInstance().getCrupier();
                                                                    if (crupier == null) {
                                                                        return;
                                                                    }
                                                                    Crupier.UnlockWaitResult waitResult = crupier.awaitStreetForUnlockPhase(phase, hand_id, Crupier.UNLOCK_WAIT_TIMEOUT_MS);
                                                                    if (waitResult != Crupier.UnlockWaitResult.READY) {
                                                                        if (waitResult == Crupier.UnlockWaitResult.TIMEOUT) {
                                                                            // Politica: un TIMEOUT es evidencia ambigua (host fuera de orden O simple lag de
                                                                            // red, indistinguibles). La operacion YA se rechaza (return abajo), asi que no
                                                                            // perdemos proteccion; bajamos de lockdown a SOFT-WARN (avisar+recomendar salir
                                                                            // pero permitir seguir) en vez de terminar la partida por algo que podria ser lag.
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN phase {0} timed out — host out of order or lag, refusing + warning", phase);
                                                                            crupier.warnSuspiciousHost(Translator.translate("zero_trust.host_unlock_out_of_order"));
                                                                        }
                                                                        return;
                                                                    }
                                                                    if (hand_id != crupier.getMano()) {
                                                                        LOGGER.log(Level.INFO, "REQ_SRA_UNLOCK_CHAIN: hand advanced — dropping");
                                                                        return;
                                                                    }
                                                                    byte[] myUnlock = (phase == Crupier.UNLOCK_PHASE_POCKET)
                                                                            ? this.participantes.get(local_nick).getSra_unlock()
                                                                            : this.participantes.get(local_nick).getSra_unlock_community();
                                                                    if (myUnlock == null) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN no local unlock for phase {0} — refusing", phase);
                                                                        return;
                                                                    }
                                                                    byte[] myLock = RistrettoSRA.getUnlockScalar(myUnlock); // k = (k^-1)^-1
                                                                    java.util.Map<String, byte[]> commitments = (phase == Crupier.UNLOCK_PHASE_POCKET)
                                                                            ? crupier.peer_k_pocket : crupier.peer_k_community;
                                                                    byte[] megapacket = crupier.local_mega_packet;
                                                                    String[] ring = crupier.active_crypto_ring;
                                                                    if (megapacket == null || ring == null) {
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN before MEGAPACKET — refusing");
                                                                        return;
                                                                    }
                                                                    // GATE "exigir prueba": voy a ayudar a revelar community = la ventana
                                                                    // donde se leeria una carta colada. Si este mazo viene de un reparto FRESCO que NO he
                                                                    // verificado como barajado honesto (el host no mando el bundle, o llego mal), aviso UNA
                                                                    // vez. Avisar-pero-permitir: podria ser un bug/retraso de red -> recomiendo salir pero
                                                                    // dejo seguir (no rompo la mano). El bundle llega ~1s tras repartir, mucho antes del
                                                                    // primer unlock community -> cero falsos positivos. Recover no marca expect -> no avisa.
                                                                    if (Crupier.shouldWarnMissingShuffleProof(phase, megapacket,
                                                                            crupier.dual_lock_expect_bundle_for, crupier.dual_lock_verified_megapacket,
                                                                            crupier.dual_lock_warned_megapacket)) {
                                                                        crupier.dual_lock_warned_megapacket = megapacket;
                                                                        LOGGER.log(Level.SEVERE, "ZERO-TRUST: revealing community without a verified honest-shuffle proof for this deck — warning (host may not have sent the bundle)");
                                                                        crupier.warnSuspiciousHost(Translator.translate("zero_trust.host_shuffle_proof_missing"));
                                                                    }
                                                                    java.util.List<UnlockChainWire.ReqItem> items = UnlockChainWire.parseReq(payloadChain);
                                                                    if (items == null) {
                                                                        // Malformacion ESTRUCTURAL (no parsea) -> casi seguro bug/version-mismatch.
                                                                        // La op ya se rechaza (return); SILENT-REFUSE, no lockdown. Coherente con el
                                                                        // gemelo malformado de este mismo handler (wire < 6 campos, tambien silent).
                                                                        LOGGER.log(Level.WARNING, "REQ_SRA_UNLOCK_CHAIN malformed items — refusing (silent: likely a bug)");
                                                                        return;
                                                                    }
                                                                    // Mi propio slot en el ring: NUNCA debo pelar mi lock de MI pocket
                                                                    // (megapacket[mySlot*2], [mySlot*2+1]). El host controla offsetBase
                                                                    // independientemente de peerIdx, asi que el guard correcto es sobre el
                                                                    // PUNTO pelado (pointIdx), no sobre la etiqueta peerIdx: si no, un host
                                                                    // hostil manda peerIdx=otro + offsetBase=mySlot*2 y me saca mis cartas.
                                                                    int mySlot = -1;
                                                                    for (int s = 0; s < ring.length; s++) {
                                                                        if (ring[s].equals(local_nick)) {
                                                                            mySlot = s;
                                                                            break;
                                                                        }
                                                                    }
                                                                    java.util.List<UnlockChainWire.RespItem> resp = new java.util.ArrayList<>();
                                                                    for (UnlockChainWire.ReqItem it : items) {
                                                                        if (it.peerIdx >= 0 && it.peerIdx < ring.length && ring[it.peerIdx].equals(local_nick)) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN asks me to unlock my own slot — extraction, refusing");
                                                                            crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                            return;
                                                                        }
                                                                        java.util.List<String> outChains = new java.util.ArrayList<>();
                                                                        for (int j = 0; j < it.chains.size(); j++) {
                                                                            int pointIdx = it.offsetBase + j;
                                                                            if (pointIdx < 0 || (pointIdx + 1) * 32 > megapacket.length) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN offset out of range — refusing");
                                                                                crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                                return;
                                                                            }
                                                                            // Defensa real contra el oraculo por la puerta de atras: aunque el
                                                                            // anclaje al megapacket sea valido, NUNCA pelo un punto de MI pocket.
                                                                            if (phase == Crupier.UNLOCK_PHASE_POCKET && mySlot >= 0
                                                                                    && (pointIdx == mySlot * 2 || pointIdx == mySlot * 2 + 1)) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN asks me to strip my OWN pocket (offset {0}) — extraction, refusing", pointIdx);
                                                                                crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                return;
                                                                            }
                                                                            byte[] point = java.util.Arrays.copyOfRange(megapacket, pointIdx * 32, (pointIdx + 1) * 32);
                                                                            DealChain.Extended ext = DealChain.extend(point, it.chains.get(j), commitments, local_nick, myLock);
                                                                            if (ext == null) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN chain not anchored/invalid (offset {0}) — extraction or tampering, refusing", pointIdx);
                                                                                crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                return;
                                                                            }
                                                                            // GATE 6 (community/rabbit): tras pelar MI community-lock el residuo
                                                                            // NUNCA debe ser genesis — eso significaría que el host me presentó la
                                                                            // cadena "todos los locks menos el mío" para que revele la carta antes
                                                                            // de tiempo. Con el binding el cegado es imposible, así que un genesis
                                                                            // aquí es extracción segura. (En POCKET el self-strip guard ya cubre el
                                                                            // flanco análogo y el residuo intermedio nunca llega a genesis.)
                                                                            if (phase != Crupier.UNLOCK_PHASE_POCKET
                                                                                    && RistrettoSRA.resolveCardIndex(ext.residual) >= 0) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN community strip reveals genesis (offset {0}) — extraction, refusing", pointIdx);
                                                                                crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_community_extraction"));
                                                                                return;
                                                                            }
                                                                            outChains.add(ext.wire);
                                                                        }
                                                                        resp.add(new UnlockChainWire.RespItem(it.peerIdx, outChains));
                                                                    }
                                                                    String respPayload = UnlockChainWire.serializeResp(resp);
                                                                    int respIdChain = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                    String myNickB64 = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));
                                                                    writeCommandToServer(Helpers.encryptCommand("GAME#" + respIdChain + "#RESP_SRA_UNLOCK_CHAIN#" + myNickB64 + "#" + respPayload, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE, "Failed to process REQ_SRA_UNLOCK_CHAIN; host will time out and abort", e);
                                                                }
                                                            });
                                                            break;
                                                        case "H_CHECK":
                                                            // Identity: debug-only chain divergence probe. The host
                                                            // broadcasts its H_t after every action when
                                                            // HandStateChain.DEBUG_HANDCHAIN is on; clients compare it to
                                                            // their own absorbed chain and log SEVERE on mismatch. The case
                                                            // is always wired (cheap no-op when the flag is off in release
                                                            // builds) so probes from a debug host never crash a release client.
                                                            try {
                                                                String hcheckNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                byte[] hostHash = Base64.getDecoder().decode(partes_comando[4]);
                                                                Crupier hcheckC = GameFrame.getInstance().getCrupier();
                                                                if (HandStateChain.DEBUG_HANDCHAIN && hcheckC != null && hcheckC.hand_state_chain != null) {
                                                                    byte[] localHash = hcheckC.hand_state_chain.getCurrentHash();
                                                                    if (!java.util.Arrays.equals(localHash, hostHash)) {
                                                                        LOGGER.log(Level.SEVERE,
                                                                                "H_CHECK DIVERGENCE after {0}'s action: host={1} local={2}",
                                                                                new Object[]{hcheckNick,
                                                                                    Base64.getEncoder().encodeToString(hostHash),
                                                                                    Base64.getEncoder().encodeToString(localHash)});
                                                                    } else {
                                                                        LOGGER.log(Level.INFO,
                                                                                "H_CHECK match after {0}'s action: {1}",
                                                                                new Object[]{hcheckNick,
                                                                                    Base64.getEncoder().encodeToString(localHash)});
                                                                    }
                                                                }
                                                            } catch (Exception e) {
                                                                // Debug-only command: never tear down the socket thread.
                                                            }
                                                            break;
                                                        case "TELEMETRY":
                                                            // Telemetría. El wire-format del payload contiene '#'
                                                            // como separador interno (timestamp#entries), así que si el
                                                            // split('#') del comando GAME generó más de 4 partes, hay que
                                                            // recomponer partes[3..end] con '#' para reconstruir el payload
                                                            // original antes de decodificar.
                                                            try {
                                                                if (partes_comando.length >= 4) {
                                                                    String payload;
                                                                    if (partes_comando.length == 4) {
                                                                        payload = partes_comando[3];
                                                                    } else {
                                                                        StringBuilder sb = new StringBuilder();
                                                                        for (int i = 3; i < partes_comando.length; i++) {
                                                                            if (i > 3) {
                                                                                sb.append('#');
                                                                            }
                                                                            sb.append(partes_comando[i]);
                                                                        }
                                                                        payload = sb.toString();
                                                                    }
                                                                    Helpers.TelemetryFrame frame = Helpers.decodeTelemetry(payload);
                                                                    if (frame != null) {
                                                                        this.latest_telemetry = frame;
                                                                        if (GameFrame.getInstance() != null
                                                                                && GameFrame.getInstance().getCrupier() != null) {
                                                                            GameFrame.getInstance().getCrupier().applyTelemetryFrameLocally(frame);
                                                                        }
                                                                    }
                                                                }
                                                            } catch (Exception e) {
                                                                LOGGER.log(Level.WARNING, "Bad TELEMETRY payload — ignored", e);
                                                            }
                                                            break;
                                                        case "TIMEOUT":
                                                            // Process the timeout command directly in the client UI thread
                                                            try {
                                                                String timeoutNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                Helpers.GUIRun(() -> {
                                                                    if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                                                        Player p = GameFrame.getInstance().getCrupier().getNick2player().get(timeoutNick);
                                                                        if (p != null) {
                                                                            // Triggers the visual change (red/purple border and timeout icon)
                                                                            p.setTimeout(true);
                                                                        }
                                                                    }
                                                                });
                                                            } catch (Exception e) {
                                                                // Ignore decoding errors to prevent socket thread crash
                                                            }
                                                            break;
                                                        case "YOUARELATE":
                                                            try {
                                                                String client_nick2 = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                String ipCliente = partes_comando[4];
                                                                if (!net_client.getLate_clients_warning().contains(ipCliente)) {
                                                                    Audio.playWavResource("misc/new_user.wav");
                                                                    net_client.getLate_clients_warning().add(ipCliente);
                                                                }
                                                                Helpers.GUIRun(() -> {
                                                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, "[" + client_nick2 + "] " + Translator.translate("game.quiere_entrar_en_la_timba"), Color.RED, Color.WHITE, getClass().getResource("/images/action/cry.png"), NOTIFICATION_TIMEOUT);
                                                                    dialog.setLocation(dialog.getParent().getLocation());
                                                                    dialog.setVisible(true);
                                                                });
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "IWTSTH":
                                                            if (GameFrame.getInstance().getCrupier().isShow_time() && !GameFrame.getInstance().getCrupier().isIwtsthing()) {
                                                                try {
                                                                    String authNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(authNick);
                                                                } catch (Exception e) {
                                                                }
                                                            }
                                                            break;
                                                        case "IWTSTHSHOW":
                                                            try {
                                                                String showNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                GameFrame.getInstance().getCrupier().IWTSTH_SHOW(showNick, Boolean.parseBoolean(partes_comando[4]));
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "IWTSTHRULE":
                                                            // Regla global del host. El diálogo "Ajustes de partida"
                                                            // refleja el flag al abrirse; ya no hay control en
                                                            // menú/popup que sincronizar.
                                                            GameFrame.IWTSTH_RULE = "1".equals(partes_comando[3]);
                                                            break;
                                                        case "RUNITWICERULE":
                                                            GameFrame.RUN_IT_TWICE = "1".equals(partes_comando[3]);
                                                            break;
                                                        case "VOICEMSGRULE":
                                                            // Regla global del host. El diálogo de ajustes de
                                                            // audio refleja el flag al abrirse; no hay control
                                                            // en menú/popup que sincronizar.
                                                            GameFrame.VOICE_MESSAGES = "1".equals(partes_comando[3]);
                                                            break;
                                                        case "RIT_VOTE_REQ":
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    int rit_timeout = Integer.parseInt(partes_comando[3]);
                                                                    int rit_total = Integer.parseInt(partes_comando[4]);
                                                                    double rit_pot = Double.parseDouble(partes_comando[5]);
                                                                    GameFrame.getInstance().getCrupier().showRitClientVoteDialog(rit_timeout, rit_total, rit_pot);
                                                                } catch (Exception e) {
                                                                }
                                                            });
                                                            break;
                                                        case "RIT_VOTE_TALLY":
                                                            try {
                                                                GameFrame.getInstance().getCrupier().updateRitClientTally(Integer.parseInt(partes_comando[3]), Integer.parseInt(partes_comando[4]));
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "RIT_VOTE_CLOSE":
                                                            try {
                                                                GameFrame.getInstance().getCrupier().closeRitClientDialog("1".equals(partes_comando[3]));
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "RABBITRULE":
                                                            GameFrame.RABBIT_HUNTING = Integer.parseInt(partes_comando[3]);
                                                            break;
                                                        case "RABBIT":
                                                            if (GameFrame.getInstance().getCrupier().isShow_time()) {
                                                                try {
                                                                    String rNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    GameFrame.getInstance().getCrupier().RABBIT_HANDLER(rNick, Integer.parseInt(partes_comando[4]));
                                                                } catch (Exception e) {
                                                                }
                                                            }
                                                            break;
                                                        case "MEGAPACKET":
                                                            // El handler REQ_SRA_UNLOCK que sigue corre en su propio threadRun
                                                            // y necesita ver local_mega_packet + active_crypto_ring para el
                                                            // state machine. Si lo dejásemos para que el Crupier los setease
                                                            // desde su queue, habría una carrera (otro thread procesa REQ_
                                                            // SRA_UNLOCK antes y rechaza por mano-no-iniciada). Aquí los
                                                            // populamos síncronos y reenviamos a la queue para que el resto
                                                            // del flujo del Crupier (descifrado de mis pocket cards) siga
                                                            // funcionando idéntico a antes.
                                                            try {
                                                                Crupier crupierMP = GameFrame.getInstance().getCrupier();
                                                                String orderStr = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                String[] orderTokens = orderStr.split(",");
                                                                java.util.ArrayList<String> ringList = new java.util.ArrayList<>();
                                                                for (String token : orderTokens) {
                                                                    if (!token.isEmpty()) {
                                                                        ringList.add(new String(Base64.getDecoder().decode(token), "UTF-8"));
                                                                    }
                                                                }
                                                                crupierMP.active_crypto_ring = ringList.toArray(new String[0]);
                                                                crupierMP.local_mega_packet = Base64.getDecoder().decode(partes_comando[4]);
                                                                // Poblar los commitments K de forma SINCRONA aqui. El handler
                                                                // REQ_SRA_UNLOCK_CHAIN corre en su propio threadRun y los necesita; si
                                                                // dependieramos de recibirMisCartas (consumer async de la cola) habria
                                                                // carrera y el binding verificaria contra un mapa vacio -> lockdown falso.
                                                                if (partes_comando.length >= 7) {
                                                                    crupierMP.parseCommitments(partes_comando[6]);
                                                                }
                                                            } catch (Exception e) {
                                                                LOGGER.log(Level.SEVERE, "Error pre-parsing MEGAPACKET in WaitingRoomFrame; queue handler will retry", e);
                                                            }
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                            }
                                                            break;
                                                        case "POCKET_CARDS":
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    String targetNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    byte[] unlockedByOthers = Base64.getDecoder().decode(partes_comando[4]);
                                                                    if (unlockedByOthers != null) {
                                                                        GameFrame.getInstance().getCrupier().single_locked_pocket_cards.put(targetNick, unlockedByOthers);
                                                                    }
                                                                } catch (Exception e) {
                                                                }
                                                            });
                                                            // Reenviamos a la cola para que el Crupier pueda continuar su flujo local normal
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                            }
                                                            break;
                                                        case "REBUYNOW":
                                                            try {
                                                                String rbNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                GameFrame.getInstance().getCrupier().rebuyNow(rbNick, Integer.parseInt(partes_comando[4]));
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "REBUYDENIED":
                                                            try {
                                                                String dnNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                int dnLimit = Integer.parseInt(partes_comando[4]);
                                                                if (GameFrame.getInstance().getLocalPlayer() != null
                                                                        && dnNick.equals(GameFrame.getInstance().getLocalPlayer().getNickname())) {
                                                                    Helpers.GUIRun(() -> {
                                                                        if (GameFrame.getInstance().getRebuy_now_menu() != null) {
                                                                            GameFrame.getInstance().getRebuy_now_menu().setSelected(false);
                                                                            GameFrame.getInstance().getRebuy_now_menu().setEnabled(true);
                                                                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setSelected(false);
                                                                            Helpers.TapetePopupMenu.REBUY_NOW_MENU.setEnabled(true);
                                                                        }
                                                                        Helpers.mostrarMensajeError(GameFrame.getInstance(), Translator.translate("rebuy.limite_alcanzado", String.valueOf(dnLimit)));
                                                                    });
                                                                }
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "SHOWCARDS":
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    String shNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    String sraKeyB64 = partes_comando[4];
                                                                    // PHASE A.1: SHOWCARDS lleva ahora una sig Ed25519 al final.
                                                                    // Si vino sin sig (cliente pre-20.65 o host stripping), pasamos
                                                                    // null → showPlayerCards rechaza sin destapar.
                                                                    String sigB64 = (partes_comando.length >= 6) ? partes_comando[5] : null;
                                                                    GameFrame.getInstance().getCrupier().showPlayerCards(shNick, sraKeyB64, sigB64);
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE, "Error processing SHOWCARDS on client", e);
                                                                }
                                                            });
                                                            break;
                                                        case "RABBIT_FLOP_PIECE":
                                                        case "RABBIT_TURN_PIECE":
                                                        case "RABBIT_RIVER_PIECE": {
                                                            // v3: el host envía a cada humano remoto su pieza
                                                            // (RABBIT_*_PIECE#nickB64#payloadB64) con los locks de
                                                            // los demás ya quitados. Solo descifra el destinatario.
                                                            final String[] partes_rp = partes_comando;
                                                            final String cmdName = partes_comando[2];
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    String targetNick = new String(Base64.getDecoder().decode(partes_rp[3]), "UTF-8");
                                                                    if (!targetNick.equals(local_nick)) {
                                                                        return; // pieza ajena, drop silencioso
                                                                    }
                                                                    byte[] piece = Base64.getDecoder().decode(partes_rp[4]);
                                                                    int expectedLen = "RABBIT_FLOP_PIECE".equals(cmdName) ? 96 : 32;
                                                                    Crupier crupierRP = GameFrame.getInstance().getCrupier();
                                                                    if (crupierRP == null || piece == null || piece.length != expectedLen) {
                                                                        // Politica: el rabbit es un reveal COSMETICO post-mano (la mano ya esta liquidada);
                                                                        // una pieza mala no puede robar dinero -> SILENT-REFUSE (no mostramos ese rabbit),
                                                                        // NO terminamos la partida. Casi seguro un bug, no un ataque.
                                                                        LOGGER.log(Level.WARNING, "rabbit piece {0} bad length {1} — refusing (cosmetic, not shown)", new Object[]{cmdName, piece == null ? -1 : piece.length});
                                                                        return;
                                                                    }
                                                                    // Dual-lock: las rabbit pieces son comunitarias, cifradas
                                                                    // con scalars de community tras la rotación.
                                                                    byte[] unlockedRP = RistrettoSRA.applyCommutativeLock(piece, this.participantes.get(local_nick).getSra_unlock_community());
                                                                    int numCards = "RABBIT_FLOP_PIECE".equals(cmdName) ? 3 : 1;
                                                                    int[] indices = new int[numCards];
                                                                    for (int k = 0; k < numCards; k++) {
                                                                        byte[] chunk = Arrays.copyOfRange(unlockedRP, k * 32, (k + 1) * 32);
                                                                        int idx = RistrettoSRA.resolveCardIndex(chunk);
                                                                        if (idx < 0) {
                                                                            // Cosmetico post-mano -> SILENT-REFUSE (no mostramos ese rabbit), no lockdown.
                                                                            LOGGER.log(Level.WARNING, "rabbit piece {0} chunk {1} does NOT resolve to genesis — refusing (cosmetic, not shown)", new Object[]{cmdName, k});
                                                                            return;
                                                                        }
                                                                        indices[k] = idx;
                                                                    }
                                                                    if ("RABBIT_FLOP_PIECE".equals(cmdName)) {
                                                                        crupierRP.setFlop_revealed(true);
                                                                        Helpers.GUIRun(() -> {
                                                                            GameFrame.getInstance().getFlop1().actualizarConValorNumerico(indices[0] + 1);
                                                                            GameFrame.getInstance().getFlop2().actualizarConValorNumerico(indices[1] + 1);
                                                                            GameFrame.getInstance().getFlop3().actualizarConValorNumerico(indices[2] + 1);
                                                                            GameFrame.getInstance().getFlop1().taparRabbit();
                                                                            GameFrame.getInstance().getFlop2().taparRabbit();
                                                                            GameFrame.getInstance().getFlop3().taparRabbit();
                                                                        });
                                                                    } else if ("RABBIT_TURN_PIECE".equals(cmdName)) {
                                                                        crupierRP.setTurn_revealed(true);
                                                                        Helpers.GUIRun(() -> {
                                                                            GameFrame.getInstance().getTurn().actualizarConValorNumerico(indices[0] + 1);
                                                                            GameFrame.getInstance().getTurn().taparRabbit();
                                                                        });
                                                                    } else {
                                                                        crupierRP.setRiver_revealed(true);
                                                                        Helpers.GUIRun(() -> {
                                                                            GameFrame.getInstance().getRiver().actualizarConValorNumerico(indices[0] + 1);
                                                                            GameFrame.getInstance().getRiver().taparRabbit();
                                                                        });
                                                                    }
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE, "Error processing " + cmdName, e);
                                                                }
                                                            });
                                                            break;
                                                        }
                                                        case "FLOP_PIECE":
                                                        case "TURN_PIECE":
                                                        case "RIVER_PIECE":
                                                            // v3: piezas comunitarias durante una mano viva. El
                                                            // handler se limita a re-encolar en Crupier — el
                                                            // descifrado y la verificación viven en
                                                            // Crupier.recibirCartasComunitarias, que bloquea en
                                                            // rondaApuestas y consume la cola.
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                            }
                                                            break;
                                                        case "LASTHAND":
                                                            // Guard: el reader thread puede tener LASTHAND en buffer cuando
                                                            // RESET_GAME ya hizo GameFrame.resetInstance() — sin guard, NPE en
                                                            // getInstance().getCrupier(). Race rara pero barata de cubrir.
                                                            GameFrame inst_lasthand = GameFrame.getInstance();
                                                            if (inst_lasthand == null) {
                                                                break;
                                                            }
                                                            if (partes_comando[3].equals("0")) {
                                                                inst_lasthand.getCrupier().setForce_recover(false);
                                                                inst_lasthand.getTapete().getCommunityCards().last_hand_off();
                                                            } else {
                                                                if (partes_comando[3].equals("2")) {
                                                                    inst_lasthand.getCrupier().setForce_recover(true);
                                                                    if (partes_comando.length > 4) {
                                                                        try {
                                                                            password = new String(Base64.getDecoder().decode(partes_comando[4]), "UTF-8");
                                                                        } catch (Exception e) {
                                                                        }
                                                                    }
                                                                }
                                                                inst_lasthand.getTapete().getCommunityCards().last_hand_on();
                                                            }
                                                            break;
                                                        case "MAXHANDS":
                                                            GameFrame.MANOS = Integer.parseInt(partes_comando[3]);
                                                            GameFrame.getInstance().getCrupier().actualizarContadoresTapete();
                                                            break;
                                                        case "UPDATEBLINDS":
                                                            GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(Double.parseDouble(partes_comando[5]), Double.parseDouble(partes_comando[6]), Integer.parseInt(partes_comando[3]), Integer.parseInt(partes_comando[4]));
                                                            GameFrame.BLIND_CAP = partes_comando.length > 7 ? Double.parseDouble(partes_comando[7]) : 0;
                                                            GameFrame.ANTE = partes_comando.length > 8 && Boolean.parseBoolean(partes_comando[8]);
                                                            GameFrame.STRADDLE = partes_comando.length > 9 && Boolean.parseBoolean(partes_comando[9]);
                                                            // El host puede cambiar la estructura de ciegas en vivo (Ajustes >
                                                            // Partida). Cuando es la escalera POR DEFECTO el campo va vacío y el
                                                            // split('#') de Java DESCARTA el campo final vacío, así que el guard
                                                            // length>10 no basta: hay que resetear a null (igual que en INIT) para
                                                            // que host y clientes escalen ciegas por la MISMA escalera (si no,
                                                            // desincronizan al subir las ciegas).
                                                            if (partes_comando.length > 10 && !partes_comando[10].isEmpty()) {
                                                                try {
                                                                    GameFrame.ACTIVE_BLIND_STRUCTURE = BlindStructure.parseValidatedLevels(partes_comando[10]);
                                                                } catch (Exception ex) {
                                                                    GameFrame.ACTIVE_BLIND_STRUCTURE = null;
                                                                    LOGGER.log(Level.WARNING, "Bad blind structure in UPDATEBLINDS", ex);
                                                                }
                                                            } else {
                                                                GameFrame.ACTIVE_BLIND_STRUCTURE = null;
                                                            }
                                                            break;
                                                        case "SERVEREXIT":
                                                            exit = true;
                                                            break;
                                                        case "SERVEREXITRECOVER":
                                                            exit = true;
                                                            GameFrame.getInstance().getCrupier().setForce_recover(true);
                                                            if (partes_comando.length > 3) {
                                                                try {
                                                                    password = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                } catch (Exception e) {
                                                                }
                                                            }
                                                            break;
                                                        case "TTS":
                                                            // El host activa o desactiva el TTS (global) para todos.
                                                            // El diálogo de ajustes de audio refleja el flag al
                                                            // abrirse; no hay control en menú/popup que sincronizar.
                                                            GameFrame.TTS_SERVER = "1".equals(partes_comando[3]);
                                                            break;
                                                        case "PAUSE":
                                                            // El host avisa al resto de clientes de que alguien pulsó pausa
                                                            // (o reanudó). Aplicamos el toggle local.
                                                            try {
                                                                String pauserNick = (partes_comando.length >= 5)
                                                                        ? new String(Base64.getDecoder().decode(partes_comando[4]), "UTF-8")
                                                                        : server_nick;
                                                                if (("0".equals(partes_comando[3]) && GameFrame.getInstance().isTimba_pausada() && pauserNick.equals(GameFrame.getInstance().getNick_pause()))
                                                                        || ("1".equals(partes_comando[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                                    GameFrame.getInstance().pauseTimba(pauserNick);
                                                                }
                                                            } catch (Exception ex) {
                                                                LOGGER.log(Level.SEVERE, "Error processing PAUSE", ex);
                                                            }
                                                            break;
                                                        case "MISDEAL":
                                                            // El host aborta la mano. Cancelamos localmente y reenviamos
                                                            // al queue para despertar a cualquier consumer (receiveMyCards,
                                                            // recibirConsensoFinal, etc.) que esté esperando timeout.
                                                            try {
                                                                String motivoMisdeal = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                GameFrame.getInstance().getCrupier().cancelarManoYDevolverApuestas(motivoMisdeal, false);
                                                            } catch (Exception ex) {
                                                                LOGGER.log(Level.SEVERE, "Error processing MISDEAL", ex);
                                                            }
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                            }
                                                            break;
                                                        case "EXIT":
                                                            String exitingNick = local_nick;
                                                            if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                                                int offset = 3;
                                                                if (!GameFrame.getInstance().isPartida_local() && partes_comando.length >= 4) {
                                                                    try {
                                                                        exitingNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    } catch (Exception e) {
                                                                    }
                                                                    offset = 4;
                                                                }

                                                                if (partes_comando.length > offset) {
                                                                    Participant p = GameFrame.getInstance().getParticipantes().get(exitingNick);
                                                                    if (p != null && !partes_comando[offset].equals("*")) {
                                                                        try {
                                                                            byte[] testament = Base64.getDecoder().decode(partes_comando[offset]);
                                                                            // Dual-lock: el testamento es la mitad community del peer que sale.
                                                                            // La mitad pocket nunca se comparte vía EXIT.
                                                                            if (testament.length == 32) {
                                                                                p.setSra_unlock_community(testament);
                                                                            }
                                                                        } catch (Exception e) {
                                                                        }
                                                                    }
                                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(exitingNick, partes_comando[offset]);
                                                                } else {
                                                                    GameFrame.getInstance().getCrupier().remotePlayerQuit(exitingNick);
                                                                }
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
                                                            String ginfo = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                            String[] game_info2 = ginfo.split("\\|");
                                                            Helpers.GUIRun(() -> {
                                                                applyGameInfoBuyinLabel(game_info2);
                                                            });
                                                            break;
                                                        case "GAMECONFIG":
                                                            // Espejo COMPLETO de la config (el HOST la cambió). Solo
                                                            // se guarda en el holder (NO se escribe GameFrame.*) y, si
                                                            // la rueda está abierta, se refresca su pestaña Partida.
                                                            GAMECONFIG_MIRROR = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                            Helpers.GUIRun(() -> SettingsDialog.refreshWaitingMirror());
                                                            break;
                                                        case "DELUSER":
                                                            try {
                                                                borrarParticipante(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"));
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "NEWUSER":
                                                            // Identity: layout
                                                            //   [3] nickB64
                                                            //   [4] unsecureFlag
                                                            //   [5] avatarB64_or_*
                                                            //   [6] pubkeyB64_or_*
                                                            //   [7] selfSigB64_or_*
                                                            Audio.playWavResource("misc/laser.wav");
                                                            try {
                                                                String nickNew = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                File avatarNew = null;
                                                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                if (file_id < 0) {
                                                                    file_id *= -1;
                                                                }
                                                                if (partes_comando.length >= 6 && !"*".equals(partes_comando[5])) {
                                                                    avatarNew = new File(System.getProperty("java.io.tmpdir") + "/corona_" + Helpers.safeNickForFilename(nickNew) + "_avatar" + String.valueOf(file_id));
                                                                    avatarNew.deleteOnExit();
                                                                    try (FileOutputStream os = new FileOutputStream(avatarNew)) {
                                                                        os.write(Base64.getDecoder().decode(partes_comando[5]));
                                                                    } catch (Exception e) {
                                                                    }
                                                                }
                                                                boolean isBot = nickNew.startsWith("CoronaBot$");
                                                                if (!participantes.containsKey(nickNew)) {
                                                                    nuevoParticipante(nickNew, avatarNew, null, null, null, isBot, "1".equals(partes_comando[4]));
                                                                }

                                                                if (partes_comando.length >= 8
                                                                        && !"*".equals(partes_comando[6]) && !"*".equals(partes_comando[7])) {
                                                                    try {
                                                                        byte[] idPubkey = Base64.getDecoder().decode(partes_comando[6]);
                                                                        byte[] idSig = Base64.getDecoder().decode(partes_comando[7]);
                                                                        if (idPubkey.length != 32 || idSig.length != 64) {
                                                                            LOGGER.log(Level.WARNING, "NEWUSER identity malformed for {0}", nickNew);
                                                                        } else if (!IdentityManager.verifyJoin(this.session_id, nickNew, idPubkey, idSig)) {
                                                                            LOGGER.log(Level.WARNING, "NEWUSER identity bad self_sig for {0}", nickNew);
                                                                        } else {
                                                                            TOFUResolver.Resolution res = TOFUResolver.resolve(nickNew, idPubkey);
                                                                            Participant p = participantes.get(nickNew);
                                                                            if (p != null) {
                                                                                p.setIdentity_pubkey(idPubkey);
                                                                                p.setIdentity_self_sig(idSig);
                                                                            }
                                                                            LOGGER.log(Level.INFO, "TOFU: {0} -> {1} (sessions={2}, verified={3}) via NEWUSER",
                                                                                    new Object[]{nickNew, res.getOutcome(), res.getSessionsCount(), res.isVerifiedOob()});
                                                                        }
                                                                    } catch (Exception idex) {
                                                                        LOGGER.log(Level.WARNING, "NEWUSER identity decode failed for " + nickNew, idex);
                                                                    }
                                                                }
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "USERSLIST":
                                                            // Identity: each entry now carries pubkey + self_sig in
                                                            // fields [3] and [4] (or "*" for bots / unknown). Apply them
                                                            // to the Participant once it exists, after TOFU.
                                                            //
                                                            // USERSLIST may arrive empty when the joining client is the
                                                            // only peer besides the host (host is never an entry here — its
                                                            // identity comes through the intro packet). Skip when there is
                                                            // no payload.
                                                            if (partes_comando.length < 4) {
                                                                break;
                                                            }
                                                            String[] current_users_parts = partes_comando[3].split("@");
                                                            for (String user : current_users_parts) {
                                                                if (user.isEmpty()) {
                                                                    continue;
                                                                }
                                                                String[] user_parts = user.split("\\|");
                                                                try {
                                                                    String list_nick = new String(Base64.getDecoder().decode(user_parts[0]), "UTF-8");
                                                                    File list_avatar = null;
                                                                    if (user_parts.length >= 3 && !"*".equals(user_parts[2])) {
                                                                        int fid = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                        if (fid < 0) {
                                                                            fid *= -1;
                                                                        }
                                                                        list_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + Helpers.safeNickForFilename(list_nick) + "_avatar" + String.valueOf(fid));
                                                                        list_avatar.deleteOnExit();
                                                                        try (FileOutputStream os = new FileOutputStream(list_avatar)) {
                                                                            os.write(Base64.getDecoder().decode(user_parts[2]));
                                                                        } catch (Exception e) {
                                                                        }
                                                                    }
                                                                    boolean isListBot = list_nick.startsWith("CoronaBot$");
                                                                    if (!participantes.containsKey(list_nick)) {
                                                                        nuevoParticipante(list_nick, list_avatar, null, null, null, isListBot, "1".equals(user_parts[1]));
                                                                    }

                                                                    if (user_parts.length >= 5
                                                                            && !"*".equals(user_parts[3]) && !"*".equals(user_parts[4])) {
                                                                        try {
                                                                            byte[] idPubkey = Base64.getDecoder().decode(user_parts[3]);
                                                                            byte[] idSig = Base64.getDecoder().decode(user_parts[4]);
                                                                            if (idPubkey.length != 32 || idSig.length != 64) {
                                                                                LOGGER.log(Level.WARNING, "USERSLIST identity malformed for {0}", list_nick);
                                                                            } else if (!IdentityManager.verifyJoin(this.session_id, list_nick, idPubkey, idSig)) {
                                                                                LOGGER.log(Level.WARNING, "USERSLIST identity bad self_sig for {0}", list_nick);
                                                                            } else {
                                                                                TOFUResolver.Resolution res = TOFUResolver.resolve(list_nick, idPubkey);
                                                                                Participant p = participantes.get(list_nick);
                                                                                if (p != null) {
                                                                                    p.setIdentity_pubkey(idPubkey);
                                                                                    p.setIdentity_self_sig(idSig);
                                                                                }
                                                                                LOGGER.log(Level.INFO, "TOFU: {0} -> {1} (sessions={2}, verified={3}) via USERSLIST",
                                                                                        new Object[]{list_nick, res.getOutcome(), res.getSessionsCount(), res.isVerifiedOob()});
                                                                            }
                                                                        } catch (Exception idex) {
                                                                            LOGGER.log(Level.WARNING, "USERSLIST identity decode failed for " + list_nick, idex);
                                                                        }
                                                                    }
                                                                } catch (Exception e) {
                                                                }
                                                            }
                                                            break;
                                                        case "INIT":
                                                            Helpers.GUIRun(() -> {
                                                                setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                                                                sound_icon.setVisible(false);
                                                                status.setText(Translator.translate("status.inicializando_juego"));
                                                                status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));
                                                                barra.setVisible(true);
                                                            });
                                                            GameFrame.BUYIN = Integer.parseInt(partes_comando[3]);
                                                            GameFrame.CIEGA_PEQUEÑA = Double.parseDouble(partes_comando[4]);
                                                            GameFrame.CIEGA_GRANDE = Double.parseDouble(partes_comando[5]);
                                                            String[] ciegas_double = partes_comando[6].split("@");
                                                            GameFrame.CIEGAS_DOUBLE = Integer.parseInt(ciegas_double[0]);
                                                            GameFrame.CIEGAS_DOUBLE_TYPE = Integer.parseInt(ciegas_double[1]);
                                                            GameFrame.RECOVER = Boolean.parseBoolean(partes_comando[7].split("@")[0]);
                                                            GameFrame.UGI = partes_comando[7].split("@")[1];
                                                            GameFrame.REBUY = Boolean.parseBoolean(partes_comando[8]);
                                                            GameFrame.MANOS = Integer.parseInt(partes_comando[9]);
                                                            GameFrame.BLIND_CAP = partes_comando.length > 10 ? Double.parseDouble(partes_comando[10]) : 0;
                                                            GameFrame.REBUY_LIMIT = partes_comando.length > 11 ? Integer.parseInt(partes_comando[11]) : 0;
                                                            GameFrame.BOT_REBUY = partes_comando.length > 12 ? Boolean.parseBoolean(partes_comando[12]) : true;
                                                            GameFrame.FIXED_BUYIN = partes_comando.length > 13 ? Boolean.parseBoolean(partes_comando[13]) : true;
                                                            // Rango de buy-in editable y política de tope de recompra (campos
                                                            // fijos; el cap/headroom de los clientes debe coincidir con el host).
                                                            GameFrame.BUYIN_MIN_BB = partes_comando.length > 14 ? Integer.parseInt(partes_comando[14]) : BuyinRules.DEFAULT_MIN_BB;
                                                            GameFrame.BUYIN_MAX_BB = partes_comando.length > 15 ? Integer.parseInt(partes_comando[15]) : BuyinRules.DEFAULT_MAX_BB;
                                                            GameFrame.REBUY_CAP_POLICY = partes_comando.length > 16 ? Integer.parseInt(partes_comando[16]) : GameFrame.REBUY_CAP_BUYIN;
                                                            // Ante y straddle (campos fijos; el cliente debe coincidir con el host).
                                                            GameFrame.ANTE = partes_comando.length > 17 && Boolean.parseBoolean(partes_comando[17]);
                                                            GameFrame.STRADDLE = partes_comando.length > 18 && Boolean.parseBoolean(partes_comando[18]);
                                                            // Reglas de juego elegidas al crear la timba (campos fijos; el
                                                            // cliente debe arrancar con las mismas reglas que el host).
                                                            GameFrame.IWTSTH_RULE = partes_comando.length > 19 && "1".equals(partes_comando[19]);
                                                            GameFrame.RUN_IT_TWICE = partes_comando.length > 20 && "1".equals(partes_comando[20]);
                                                            GameFrame.RABBIT_HUNTING = partes_comando.length > 21 ? Integer.parseInt(partes_comando[21]) : 0;
                                                            // Estructura de ciegas personalizada (campo opcional al final, ahora
                                                            // en el índice 22): el cliente recomputa la escalada con la MISMA
                                                            // lista que el host. Ausente = escalera por defecto (null). Nunca
                                                            // conservar una estructura stale de una partida anterior.
                                                            if (partes_comando.length > 22 && !partes_comando[22].isEmpty()) {
                                                                try {
                                                                    GameFrame.ACTIVE_BLIND_STRUCTURE = BlindStructure.parseValidatedLevels(partes_comando[22]);
                                                                } catch (IllegalArgumentException blinds_ex) {
                                                                    LOGGER.log(Level.WARNING, "INIT custom blind structure parse failed or invalid; falling back to default", blinds_ex);
                                                                    GameFrame.ACTIVE_BLIND_STRUCTURE = null;
                                                                }
                                                            } else {
                                                                GameFrame.ACTIVE_BLIND_STRUCTURE = null;
                                                            }
                                                            Helpers.GUIRunAndWait(new Runnable() {
                                                                public void run() {
                                                                    // Si el cliente tenía la rueda abierta (con la pestaña
                                                                    // Partida de sala), se cierra sola al arrancar: los
                                                                    // ajustes ya no aplican. dispose() directo = sin el
                                                                    // diálogo de "descartar cambios".
                                                                    SettingsDialog.closeIfOpen();
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
                                            if (WaitingRoomFrame.getInstance() != null) {
                                                WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{server_nick, Integer.parseInt(partes_comando[1])});
                                                synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {
                                                    WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                                }
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                    } catch (Exception frame_ex) {
                                        // If the game was reset/torn down under us (RESET race:
                                        // resetInstance() ran while frames were still buffered),
                                        // let it propagate to the outer handler to end/reconnect
                                        // the consumer, exactly as master did — instead of
                                        // NPE-spinning over the remaining frames against a null
                                        // GameFrame. Only fires when no game is live, so it can
                                        // never tear down an active session.
                                        if (GameFrame.getInstance() == null) {
                                            throw frame_ex;
                                        }
                                        LOGGER.log(Level.WARNING, "Discarding unprocessable command frame from server", frame_ex);
                                    }
                                } else {
                                    if (!exit && !WaitingRoomFrame.getInstance().isExit()) {
                                        LOGGER.log(Level.WARNING, "Socket received poison pill");
                                    }
                                }
                            } while (!exit);
                            break;
                        default:
                            break;
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }

                if (WaitingRoomFrame.getInstance() != null && GameFrame.getInstance() != null
                        && WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    GameFrame.getInstance().finTransmision(exit);
                } else if (!exit) {
                    if (net_client.getLocal_client_socket() == null) {
                        booting = false;
                        Helpers.GUIRunAndWait(() -> {
                            status.setForeground(Color.red);
                            Helpers.smoothCountdown(barra, CLIENT_REC_WAIT);
                        });
                        for (int i = CLIENT_REC_WAIT; i > 0 && !exit; i--) {
                            int j = i;
                            Helpers.GUIRun(() -> {
                                status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));
                                status.setText(Translator.translate("status.error_reconectando") + " " + j + " " + Translator.translate("status.segs"));
                                // setValue(j) redundante: smoothCountdown ya repinta
                                // cada 50ms via Timer interno. El loop sigue para
                                // actualizar el texto del status cada segundo y para
                                // detectar exit.
                            });
                            if (!exit) {
                                synchronized (net_client.getLock_client_reconnect()) {
                                    try {
                                        net_client.getLock_client_reconnect().wait(1000);
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                        }
                        // Cancela el Timer interno tras el loop — si exit=true se
                        // dispone WaitingRoomFrame justo despues, evitamos Timer
                        // huerfano en background.
                        Helpers.GUIRun(() -> Helpers.resetBarra(barra, 0));
                    } else {
                        mostrarMensajeError(THIS, Translator.translate("conn.algo_ha_fallado_has_perdido"));
                    }
                }
            } while (!exit && net_client.getLocal_client_socket() == null);
            exit = true;
            synchronized (ping_pong_lock) {
                ping_pong_lock.notifyAll();
            }
            if (GameFrame.getInstance() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                Helpers.GUIRunAndWait(() -> {
                    Init.VENTANA_INICIO.setVisible(true);
                    dispose();
                });
                Audio.stopLoopMp3("misc/waiting_room.mp3");
                if (GameFrame.MUSICA_AMBIENTAL) {
                    Audio.unmuteLoopMp3("misc/background_music.mp3");
                }
            }
        });
    }

    private void enviarListaUsuariosActualesAlNuevoUsuario(Participant par) {
        net_server.enviarListaUsuariosToNewUser(par);
    }

    /**
     * Identity: verifies a JOIN_IDENTITY self_sig sent by a new client during
     * their initial handshake. Decodes the base64-encoded pubkey (32 bytes) and signature
     * (64 bytes), then delegates to {@link IdentityManager#verifyJoin} under the current
     * game's session_id and the NFC-normalized nick.
     *
     * Returns false on any decode error or signature mismatch. Never throws.
     */
    private boolean verifyJoinSelfSig(String nick, String pubkeyB64, String selfSigB64) {
        try {
            byte[] pubkey = Base64.getDecoder().decode(pubkeyB64);
            byte[] sig = Base64.getDecoder().decode(selfSigB64);
            if (pubkey.length != 32 || sig.length != 64) {
                return false;
            }
            return IdentityManager.verifyJoin(this.session_id, nick, pubkey, sig);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "verifyJoinSelfSig decode/verify error: {0}", ex.getMessage());
            return false;
        }
    }

    /**
     * Identity: stores the validated identity on the participant entry, runs the
     * local TOFU resolution, and logs the outcome (NEW / MATCH / CHANGED). Called by
     * the host right after a successful JOIN.
     */
    private void recordJoinIdentity(Participant par, String pubkeyB64, String selfSigB64) {
        try {
            byte[] pubkey = Base64.getDecoder().decode(pubkeyB64);
            byte[] sig = Base64.getDecoder().decode(selfSigB64);
            par.setIdentity_pubkey(pubkey);
            par.setIdentity_self_sig(sig);
            TOFUResolver.Resolution res = TOFUResolver.resolve(par.getNick(), pubkey);
            LOGGER.log(Level.INFO, "TOFU: {0} -> {1} (sessions={2}, verified={3})",
                    new Object[]{par.getNick(), res.getOutcome(), res.getSessionsCount(), res.isVerifiedOob()});
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "recordJoinIdentity failed for " + par.getNick(), ex);
        }
    }

    private void serverSocketHandler(final Socket client_socket) {

        Helpers.threadRun(() -> {

            LOGGER.log(Level.INFO, "A client is trying to connect...");
            net_server.getClient_threads().add(Thread.currentThread().threadId());
            String recibido;
            String[] partes;
            try {
                client_socket.setTcpNoDelay(true);
                client_socket.setKeepAlive(true);
                // Cerrojo anti-DoS: si el peer NO termina el handshake en HANDSHAKE_TIMEOUT_MS,
                // el read bloqueado lanza SocketTimeoutException y caemos al catch que cierra
                // el socket y libera el thread. Se RESETEA a 0 mas abajo en las dos ramas de
                // exito (nuevoParticipante para JOIN limpio y resetSocket para reconexion).
                client_socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                byte[] magic = new byte[Helpers.toByteArray(MAGIC_BYTES).length];
                // readFully (no read()): un magic partido por segmentación TCP dejaba el
                // buffer a medias y rechazaba erróneamente a un cliente válido. El
                // SoTimeout de arriba sigue cubriendo a un peer que no envíe suficiente.
                DataInputStream dIn = new DataInputStream(client_socket.getInputStream());
                dIn.readFully(magic);
                if (Helpers.toHexString(magic).toLowerCase().equals(MAGIC_BYTES)) {

                    /* INICIO INTERCAMBIO DE CLAVES LIMPIO */
                    int length = dIn.readInt();
                    if (length <= 0 || length > HANDSHAKE_MAX_PUBKEY_BYTES) {
                        throw new IOException("Handshake: invalid client pubkey length " + length
                                + " (cap " + HANDSHAKE_MAX_PUBKEY_BYTES + ")");
                    }
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
                    // Identity: ship the game session_id immediately after the server
                    // pubkey. Clients on this version expect these bytes; old clients are
                    // blocked by the strict-equality VERSION gate further down.
                    dOut.writeInt(session_id.length);
                    dOut.write(session_id);
                    dOut.flush();

                    serverKeyAgree.doPhase(clientPubKey, true);
                    byte[] serverSharedSecret = serverKeyAgree.generateSecret();
                    byte[] secret_hash = Helpers.deriveChannelSecret(serverSharedSecret, password);
                    SecretKeySpec aes_key = new SecretKeySpec(secret_hash, 0, 32, "AES");
                    SecretKeySpec hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    /* FIN INTERCAMBIO DE CLAVES */

                    recibido = readCommandFromClient(client_socket, aes_key, hmac_key);

                    if (recibido == null) {
                        // readCommand returns null on socket failure (peer dropped between
                        // key exchange and payload). Bail out cleanly instead of NPE-ing on split.
                        LOGGER.log(Level.WARNING,
                                "Handshake aborted: client closed connection before sending payload.");
                        try {
                            if (!client_socket.isClosed()) {
                                client_socket.close();
                            }
                        } catch (Exception ex) {
                        }
                        net_server.getClient_threads().remove(Thread.currentThread().threadId());
                        return;
                    }

                    partes = recibido.split("#");

                    // Guard before touching partes[1]: a payload without the version
                    // segment would throw AIOOBE into the general catch, which does NOT
                    // close the socket (FD leak). Close it here, in a branch where the
                    // socket has not yet been handed to a Participant, mirroring the
                    // recibido == null path above.
                    if (partes.length < 2) {
                        LOGGER.log(Level.WARNING,
                                "Handshake aborted: malformed payload (expected nick#version#...).");
                        try {
                            if (!client_socket.isClosed()) {
                                client_socket.close();
                            }
                        } catch (Exception ex) {
                        }
                        net_server.getClient_threads().remove(Thread.currentThread().threadId());
                        return;
                    }

                    String client_nick = new String(Base64.getDecoder().decode(partes[0]), "UTF-8");

                    String client_version = partes[1];
                    File client_avatar = null;

                    if (partes.length == 5) {
                        LOGGER.log(Level.WARNING, "A potential client wants to reconnect...");
                        if (participantes.containsKey(client_nick)) {
                            LOGGER.log(Level.WARNING, "Client already exists");
                            Mac orig_sha256_HMAC = Mac.getInstance("HmacSHA256");
                            orig_sha256_HMAC.init(participantes.get(client_nick).getHmac_key_orig());
                            byte[] orig_hmac = orig_sha256_HMAC.doFinal(client_nick.getBytes("UTF-8"));
                            boolean rec_error = true;
                            if (MessageDigest.isEqual(orig_hmac, Base64.getDecoder().decode(partes[4]))) {

                                LOGGER.log(Level.WARNING, "Client HMAC is authentic");

                                // Refresh autenticado del grace ANTES de resetSocket:
                                // si el reader del Participant esta en wait y el grace
                                // base esta a punto de expirar, este intent cripto-valido
                                // lo extiende a CLIENT_RECON_TIMEOUT. Cubre el caso de
                                // red lenta donde handshake+payload llegan justo al borde.
                                participantes.get(client_nick).signalReconnectIntent();

                                LOGGER.log(Level.WARNING, "Resetting client socket...");

                                // Handshake completado: el Participant toma control del socket
                                // y sus reads normales (PING/PONG, GAME, etc.) no deben heredar
                                // el deadline del handshake.
                                try {
                                    client_socket.setSoTimeout(0);
                                } catch (Exception ex) {
                                    LOGGER.log(Level.WARNING, "Could not clear handshake SoTimeout on reconnect", ex);
                                }
                                if (participantes.get(client_nick).resetSocket(client_socket, aes_key, hmac_key)) {

                                    if (WaitingRoomFrame.getInstance().isPartida_empezada()
                                            && GameFrame.getInstance() != null
                                            && GameFrame.getInstance().getCrupier() != null
                                            && GameFrame.getInstance().getCrupier().getNick2player() != null
                                            && GameFrame.getInstance().getCrupier().getNick2player()
                                                    .get(client_nick) != null) {
                                        try {
                                            GameFrame.getInstance().getCrupier().getNick2player().get(client_nick)
                                                    .setTimeout(false);
                                        } catch (Exception ex) {
                                        }
                                    }

                                    LOGGER.log(Level.WARNING, "Client {0} has reconnected successfully", client_nick);

                                    // Ack explícito al cliente para que su reconectarCliente sepa
                                    // que el reconnect fue aceptado de verdad. Sin este ack el
                                    // cliente marcaba ok_rec=true en base solo a que el handshake
                                    // criptográfico terminase sin excepción, y si el server cerraba
                                    // el socket inmediatamente (cualquiera de las ramas DENIED),
                                    // el reader cliente leía null y volvía a llamar a
                                    // reconectarCliente() sin pausa — busy-loop con ECDH en cada
                                    // iteración que freezaba la UI y disparaba el CPU al 100%.
                                    try {
                                        participantes.get(client_nick).writeCommandFromServer(
                                                Helpers.encryptCommand("RECONNECT_OK", aes_key, hmac_key));
                                    } catch (Exception ackEx) {
                                        LOGGER.log(Level.WARNING, "Failed to send RECONNECT_OK ack to " + client_nick, ackEx);
                                    }

                                    rec_error = false;

                                    if (WaitingRoomFrame.getInstance().isPartida_empezada()
                                            && GameFrame.getInstance() != null) {
                                        Helpers.GUIRun(() -> {
                                            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(),
                                                    false, client_nick + " " + Translator.translate("conn.ha_reconectado"),
                                                    Color.GREEN, Color.WHITE,
                                                    getClass().getResource("/images/action/plug.png"),
                                                    NOTIFICATION_TIMEOUT);
                                            dialog.setLocation(dialog.getParent().getLocation());
                                            dialog.setVisible(true);
                                        });
                                    }

                                } else {
                                    LOGGER.log(Level.WARNING, "Client {0} failed to reconnect", client_nick);
                                    // Ack explícito de denegación antes de cerrar (ver nota en
                                    // la rama OK más arriba sobre por qué hace falta el ack).
                                    try {
                                        writeCommandFromServer(
                                                Helpers.encryptCommand("RECONNECT_DENIED#RESET_FAIL", aes_key, hmac_key),
                                                client_socket);
                                    } catch (Exception ackEx) {
                                    }
                                    try {
                                        if (!client_socket.isClosed()) {
                                            client_socket.close();
                                        }
                                    } catch (Exception ex) {
                                    }
                                }

                            } else {
                                // BAD HMAC: el cliente trae una clave de sesión
                                // vieja (su HMAC orig no coincide con el actual
                                // del Participant). Caso ESPERADO tras una
                                // interrupción larga — el Reconnect2ServerDialog
                                // del cliente intenta automáticamente cada pocos
                                // segundos. NO disparamos popup al host: cada
                                // intento generaría un popup nuevo y se acumulan
                                // hasta inutilizar el server. El cliente verá la
                                // denegación explícita (RECONNECT_DENIED) en su
                                // reconectarCliente y caerá en su propio dialog
                                // con pausa entre intentos.
                                LOGGER.log(Level.WARNING, "Client {0} failed to reconnect (bad HMAC) — silencing popup (expected after long interruption; client will land on its own reconnect-failed dialog)", client_nick);
                                try {
                                    writeCommandFromServer(
                                            Helpers.encryptCommand("RECONNECT_DENIED#BAD_HMAC", aes_key, hmac_key),
                                            client_socket);
                                } catch (Exception ackEx) {
                                }
                                try {
                                    if (!client_socket.isClosed()) {
                                        client_socket.close();
                                    }
                                } catch (Exception ex) {
                                }
                                rec_error = false;
                            }
                            if (rec_error) {
                                Helpers.threadRun(() -> {
                                    Helpers.mostrarMensajeError(THIS,
                                            Translator.translate("conn.error_al_intentar_reconectar") + client_nick);
                                });
                            }
                        } else {
                            LOGGER.log(Level.WARNING, "User {0} trying to reconnect to a previous game — denied", client_nick);
                            // Ack explícito de denegación antes de cerrar el socket. Sin esto el
                            // cliente cree que reconectó (su handshake terminó OK), su reader
                            // lee null inmediatamente al cerrar el server, llama a
                            // reconectarCliente() de nuevo en busy-loop sin pausa (la pausa de
                            // 5s solo aplica si ok_rec=false) — bug yxmgl 20.59 issue 1:
                            // freeze + CPU spike tras "recover" del server.
                            try {
                                writeCommandFromServer(
                                        Helpers.encryptCommand("RECONNECT_DENIED#UNKNOWN_NICK", aes_key, hmac_key),
                                        client_socket);
                            } catch (Exception ackEx) {
                            }
                            try {
                                if (!client_socket.isClosed()) {
                                    client_socket.close();
                                }
                            } catch (Exception ex) {
                            }
                        }
                    } else if (!AboutDialog.VERSION.equals(client_version)) {
                        writeCommandFromServer(
                                Helpers.encryptCommand("BADVERSION#" + AboutDialog.VERSION, aes_key, hmac_key),
                                client_socket);
                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }
                    } else if (WaitingRoomFrame.getInstance().isPartida_empezando()
                            || WaitingRoomFrame.getInstance().isPartida_empezada()) {
                        writeCommandFromServer(Helpers.encryptCommand("YOUARELATE", aes_key, hmac_key), client_socket);

                        try {
                            String ipCliente = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                                    .digest(client_socket.getInetAddress().getHostAddress().getBytes()));

                            if (!net_server.getLate_clients_warning().contains(ipCliente)) {
                                Audio.playWavResource("misc/new_user.wav");
                                net_server.getLate_clients_warning().add(ipCliente);
                            }

                            Helpers.GUIRun(() -> {
                                InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false,
                                        "[" + client_nick + "] " + Translator.translate("game.quiere_entrar_en_la_timba"),
                                        Color.RED, Color.WHITE, getClass().getResource("/images/action/cry.png"),
                                        NOTIFICATION_TIMEOUT);
                                dialog.setLocation(dialog.getParent().getLocation());
                                dialog.setVisible(true);
                            });

                            Helpers.threadRun(() -> {
                                try {
                                    GameFrame.getInstance().getCrupier()
                                            .broadcastGAMECommandFromServer("YOUARELATE#"
                                                    + Base64.getEncoder().encodeToString(client_nick.getBytes("UTF-8")) + "#"
                                                    + ipCliente, null);
                                } catch (UnsupportedEncodingException ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            });
                        } catch (Exception e) {
                        }

                        LOGGER.log(Level.WARNING,
                                "User {0} arrived too late — denied", client_nick);

                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }

                    } else if (participantes.size() == MAX_PARTICIPANTES) {
                        writeCommandFromServer(Helpers.encryptCommand("NOSPACE", aes_key, hmac_key), client_socket);
                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }
                    } else if (participantes.containsKey(client_nick)) {
                        writeCommandFromServer(Helpers.encryptCommand("NICKFAIL", aes_key, hmac_key), client_socket);
                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }
                    } else if (partes.length != 6 || !"JOIN".equals(partes[3])) {
                        // Identity: clients on the new wire MUST send a JOIN payload
                        // with pubkey + self_sig. Anything else is a misformatted client and
                        // gets the same response as a version mismatch.
                        LOGGER.log(Level.WARNING, "Client {0} sent malformed JOIN (fields={1}, marker={2})",
                                new Object[]{client_nick, partes.length, partes.length > 3 ? partes[3] : "(missing)"});
                        writeCommandFromServer(Helpers.encryptCommand("BADVERSION#" + AboutDialog.VERSION, aes_key, hmac_key), client_socket);
                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }
                    } else if (!verifyJoinSelfSig(client_nick, partes[4], partes[5])) {
                        // Identity: self_sig invalid means either the client is on the
                        // wrong session_id (replay from another game) or has a tampered key.
                        // Reject without explanation to deny an oracle to attackers.
                        LOGGER.log(Level.WARNING, "Client {0} sent invalid JOIN self_sig -> rejecting", client_nick);
                        try {
                            client_socket.close();
                        } catch (Exception ex) {
                        }
                    } else {
                        String client_avatar_base64 = partes[2];
                        try {
                            if (!"*".equals(client_avatar_base64)) {
                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();
                                if (file_id < 0) {
                                    file_id *= -1;
                                }
                                client_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + Helpers.safeNickForFilename(client_nick)
                                        + "_avatar" + String.valueOf(file_id));
                                client_avatar.deleteOnExit();

                                try (FileOutputStream os = new FileOutputStream(client_avatar)) {
                                    os.write(Base64.getDecoder().decode(client_avatar_base64));
                                }
                            }
                        } catch (Exception ex) {
                            client_avatar = null;
                        }

                        // Cuarto campo (#) AÑADIDO al mismo comando NICKOK: el espejo COMPLETO
                        // de la config (GamePreset.Settings serializado) para que el cliente recién
                        // unido pueble en gris su pestaña Partida. Es un campo extra del MISMO
                        // mensaje (no un read nuevo), así no se altera la secuencia del handshake.
                        writeCommandFromServer(Helpers.encryptCommand(
                                "NICKOK#" + (password == null ? "0" : "1") + "#"
                                + Base64.getEncoder().encodeToString(
                                        (game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|"
                                                + game_info_hands.getText()).getBytes("UTF-8"))
                                + "#" + Base64.getEncoder().encodeToString(
                                        GamePreset.Settings.fromGameFrame().serialize().getBytes("UTF-8")),
                                aes_key, hmac_key), client_socket);

                        byte[] avatar_bytes = null;

                        if (local_avatar != null && local_avatar.length() > 0) {
                            try (FileInputStream is = new FileInputStream(local_avatar)) {
                                avatar_bytes = is.readAllBytes();
                            }
                        }

                        // Identity: piggyback host's pubkey + self_sig on the sync intro so
                        // the new client has the host's identity in the same packet as nick + avatar
                        // — no dependency on any async queue. Avatar slot uses "*" placeholder when
                        // there is no avatar, keeping a fixed 4-field layout
                        // (nick_b64 # avatar_b64_or_* # pubkey_b64_or_* # self_sig_b64_or_*).
                        writeCommandFromServer(Helpers.encryptCommand(
                                Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"))
                                + "#" + (avatar_bytes != null ? Base64.getEncoder().encodeToString(avatar_bytes) : "*")
                                + "#" + (host_identity_pubkey != null ? Base64.getEncoder().encodeToString(host_identity_pubkey) : "*")
                                + "#" + (host_self_sig != null ? Base64.getEncoder().encodeToString(host_self_sig) : "*"),
                                aes_key, hmac_key), client_socket);

                        writeCommandFromServer(Helpers.encryptCommand(
                                chat_text.toString().isEmpty() ? "*"
                                : Base64.getEncoder().encodeToString(chat_text.toString().getBytes("UTF-8")),
                                aes_key, hmac_key), client_socket);

                        synchronized (lock_new_client) {
                            try {
                                Helpers.GUIRunAndWait(() -> {
                                    empezar_timba.setEnabled(false);
                                    game_info_buyin.setEnabled(false);
                                    game_info_blinds.setEnabled(false);
                                    game_info_hands.setEnabled(false);
                                    revalidate();
                                    repaint();
                                });
                                // El containsKey de la comprobación temprana
                                // (NICKFAIL) corre FUERA de lock_new_client y
                                // varios JOIN simultáneos tienen un hilo cada
                                // uno: dos clientes con el mismo nick podían
                                // pasar aquel check antes de que ninguno
                                // insertara y acabar sobrescribiéndose en
                                // participantes (el primer socket/hilos quedaba
                                // huérfano pero vivo). Se RE-comprueba el nick
                                // aquí dentro, bajo el mismo lock que la
                                // inserción, cerrando la ventana TOCTOU.
                                if (participantes.size() < MAX_PARTICIPANTES
                                        && !WaitingRoomFrame.getInstance().isPartida_empezando()
                                        && !WaitingRoomFrame.getInstance().isPartida_empezada()
                                        && !participantes.containsKey(client_nick)) {
                                    // Handshake completado: el Participant toma control del socket
                                    // y sus reads normales (PING/PONG, GAME, etc.) no deben heredar
                                    // el deadline del handshake.
                                    try {
                                        client_socket.setSoTimeout(0);
                                    } catch (Exception ex) {
                                        LOGGER.log(Level.WARNING, "Could not clear handshake SoTimeout on new join", ex);
                                    }
                                    nuevoParticipante(client_nick, client_avatar, client_socket, aes_key, hmac_key,
                                            false, false);
                                    // Identity: cache pubkey+self_sig on the new Participant
                                    // and run local TOFU resolution. partes[4] / partes[5] were
                                    // validated above by verifyJoinSelfSig.
                                    recordJoinIdentity(participantes.get(client_nick), partes[4], partes[5]);
                                    Audio.playWavResource("misc/laser.wav");

                                    if (participantes.size() > 2) {
                                        // Sólo enviamos USERSLIST cuando hay al menos otro peer
                                        // aparte del nuevo (host + nuevo == size 2 → nada que listar;
                                        // la identidad del host ya viaja en el intro síncrono).
                                        enviarListaUsuariosActualesAlNuevoUsuario(participantes.get(client_nick));

                                        // Identity: NEWUSER carries the new peer's pubkey +
                                        // self_sig so already-connected peers can independently verify
                                        // and TOFU-resolve in the same packet that announces the join.
                                        // Avatar slot uses "*" placeholder for a fixed 5-field layout
                                        // (nick|flag|avatar|pubkey|sig).
                                        Participant newPar = participantes.get(client_nick);
                                        String avatarB64 = "*";
                                        if (client_avatar != null) {
                                            byte[] avatar_b;
                                            try (FileInputStream is = new FileInputStream(client_avatar)) {
                                                avatar_b = is.readAllBytes();
                                            }
                                            avatarB64 = Base64.getEncoder().encodeToString(avatar_b);
                                        }
                                        byte[] newPubkey = newPar.getIdentity_pubkey();
                                        byte[] newSig = newPar.getIdentity_self_sig();
                                        String comando = "NEWUSER#"
                                                + Base64.getEncoder().encodeToString(client_nick.getBytes("UTF-8")) + "#"
                                                + (newPar.isUnsecure_player() ? "1" : "0") + "#"
                                                + avatarB64 + "#"
                                                + (newPubkey != null ? Base64.getEncoder().encodeToString(newPubkey) : "*") + "#"
                                                + (newSig != null ? Base64.getEncoder().encodeToString(newSig) : "*");
                                        broadcastASYNCGAMECommandFromServer(comando, newPar);
                                    }
                                    Helpers.GUIRun(() -> {
                                        kick_user.setEnabled(true);
                                        new_bot_button
                                                .setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                                    });
                                    LOGGER.log(Level.INFO, "{0} connected", client_nick);
                                } else {
                                    try (client_socket) {
                                        LOGGER.log(Level.INFO,
                                                "{0} could not connect properly (game full, already started, or nick claimed by a concurrent join)",
                                                client_nick);
                                    }
                                }
                            } catch (Exception ex) {
                            } finally {
                                Helpers.GUIRun(() -> {
                                    empezar_timba.setEnabled((participantes.size() > 1));
                                    game_info_buyin.setEnabled(true);
                                    game_info_blinds.setEnabled(true);
                                    game_info_hands.setEnabled(true);
                                    revalidate();
                                    repaint();
                                });
                            }
                        }
                    }
                } else {
                    try (client_socket) {
                        LOGGER.log(Level.SEVERE,
                                "Bad magic bytes from client");
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                // Cualquier excepción que llega aquí ocurrió en el handshake temprano
                // (lectura de magic/pubkey, ECDH, parse de versión, ramas de rechazo,
                // verifyJoinSelfSig) — SIEMPRE antes del bloque synchronized(lock_new_client),
                // cuyo handoff a Participant tiene su propio catch interno. Por tanto el
                // socket nunca se entregó a un peer: cerrarlo cierra la fuga residual de FDs
                // sin riesgo de cerrar un socket vivo ya en manos de un Participant.
                if (client_socket != null) {
                    try {
                        client_socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            net_server.getClient_threads().remove(Thread.currentThread().threadId());
        });

    }

    private void servidor() {
        server_nick = local_nick;
        Helpers.threadRun(() -> {
            while (!exit) {
                booting = true;
                try {
                    String[] direccion = server_ip_port.trim().split(":");
                    server_port = Integer.parseInt(direccion[1]);
                    if (upnp) {
                        String stat = server_address_label.getText();
                        Helpers.GUIRun(() -> {
                            server_address_label.setText(Translator.translate("conn.probando_upnp"));
                        });
                        upnp = Helpers.UPnPOpen(server_port);
                        if (upnp) {
                            Helpers.GUIRun(() -> {
                                server_address_label.setForeground(Color.BLUE);
                                server_address_label.setText(
                                        Helpers.getMyPublicIP() + ":" + String.valueOf(server_port) + " (UPnP OK)");
                            });
                        } else {
                            Helpers.GUIRun(() -> {
                                server_address_label.setText(stat + " (UPnP ERROR)");
                            });
                            mostrarMensajeError(THIS,
                                    Translator.translate("conn.upnp_mapping_failed"));
                        }
                    }
                    Helpers.PROPERTIES.setProperty("upnp", String.valueOf(upnp));
                    Helpers.savePropertiesFile();
                    booting = false;
                    ServerSocket ss = new ServerSocket();
                    net_server.setServer_socket(ss);
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(server_port));
                    while (!ss.isClosed()) {
                        serverSocketHandler(ss.accept());
                    }
                } catch (IOException ex) {
                    if (net_server.getServer_socket() == null) {
                        exit = true;
                        mostrarMensajeError(THIS,
                                Translator.translate("conn.server_socket_bind_failed"));
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if (upnp) {
                Helpers.UPnPClose(server_port);
            }
            if (GameFrame.getInstance() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                Helpers.GUIRun(() -> {
                    Init.VENTANA_INICIO.setVisible(true);
                    dispose();
                });
                Audio.stopLoopMp3("misc/waiting_room.mp3");
                if (GameFrame.MUSICA_AMBIENTAL) {
                    Audio.unmuteLoopMp3("misc/background_music.mp3");
                }
            }
        });
    }

    public void refreshChatPanel() {

        Helpers.threadRun(() -> {
            synchronized (chat_box_panel) {

                final String html = "<html><body style='background-image: url(" + background_chat_src + ")'>"
                        + (chat_text.toString().isEmpty() ? "" : txtChat2HTML(chat_text.toString())) + "</body></html>";

                Helpers.GUIRun(() -> {
                    CoronaHTMLEditorKit.USE_GIF_CACHE = true;
                    chat.setText(html);
                    CoronaHTMLEditorKit.USE_GIF_CACHE = false;
                    chat.setCaretPosition(chat.getDocument().getLength());
                });
            }
        });

    }

    public void recibirMensajeChat(String nick, String msg) {

        chatHTMLAppend(nick + ":(" + Helpers.getLocalTimeString() + ") " + msg + "\n");

        Helpers.GUIRun(() -> {
            if (WaitingRoomFrame.getInstance().isPartida_empezada() && !isActive()) {

                if (GameFrame.getInstance().getFastchat_dialog() != null) {
                    GameFrame.getInstance().getFastchat_dialog().refreshChatHistory();
                }

                if (WaitingRoomFrame.CHAT_GAME_NOTIFICATIONS) {
                    if (msg.startsWith("img://") || msg.startsWith("imgs://")) {
                        try {
                            GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{nick, new URL(msg.replaceAll("^img", "http"))});
                        } catch (MalformedURLException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    } else {
                        String tts_msg = cleanTTSChatMessage(msg);
                        GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{nick, tts_msg});
                    }

                    synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {
                        GameFrame.NOTIFY_CHAT_QUEUE.notifyAll();
                    }
                }
            }
        });

        if (this.server) {
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            // Thread-safe iteration snapshot
            ArrayList<Participant> targets;
            synchronized (participantes) {
                targets = new ArrayList<>(participantes.values());
            }

            for (Participant p : targets) {
                try {
                    if (p != null && !p.isCpu() && !p.getNick().equals(nick)) {
                        String comando = "CHAT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                + Base64.getEncoder().encodeToString(msg.getBytes("UTF-8"));

                        p.writeCommandFromServer(Helpers.encryptCommand(comando, p.getAes_key(), iv, p.getHmac_key()));
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public boolean isPartida_empezada() {
        return partida_empezada;
    }

    /**
     * Telemetría: última snapshot recibida del host (lat1/lat2/recon
     * por peer). Puede ser null si aún no se ha recibido ninguna. Lectores
     * deben tolerar null y campos faltantes en el map (peer recién entrado
     * todavía no medido).
     */
    public Helpers.TelemetryFrame getLatest_telemetry() {
        return latest_telemetry;
    }

    public void recibirNotaVoz(String nick, byte[] audio) {

        // The rule guard also runs on the host: with voice messages disabled,
        // notes from rogue clients are neither processed nor relayed.
        if (!GameFrame.VOICE_MESSAGES || audio == null || audio.length == 0 || audio.length > MAX_VOICE_MESSAGE_BYTES) {
            return;
        }

        // Random suffix on top of millis+nick: two notes from the same nick within
        // the same millisecond (a rogue client flooding raw VOICEMSG frames) would
        // otherwise collide and the first chat anchor would replay the second audio.
        final String voice_filename = System.currentTimeMillis() + "_" + nick.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + Helpers.genRandomString(8) + ".wav";

        // The token goes into the PLAIN history on purpose: the chat window
        // rebuilds its whole HTML from chat_text (in-game reopen), so the
        // anchor must be regenerable from there. FastChat cleans the token.
        final String anchor = nick + ":(" + Helpers.getLocalTimeString() + ") @@voicenote:" + voice_filename + "@@\n";

        if (nick.equals(local_nick)) {
            // Our OWN note (rendered on a pool thread): write SYNCHRONOUSLY before
            // publishing the clickable anchor so an immediate click on it cannot
            // race the write and wrongly report "note not found". No reader thread
            // is blocked here.
            try {
                Files.write(Paths.get(Init.VOICE_DIR + "/" + voice_filename), audio);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not persist voice message: {0}", ex.getMessage());
            }
            chatHTMLAppend(anchor);
        } else {
            // A note RECEIVED from a peer: recibirNotaVoz runs on that peer's socket
            // reader thread, so write ASYNC (as master did) to avoid head-of-line
            // blocking its game commands. The click-before-write race here is the
            // rare pre-existing one and not worth stalling the reader for.
            chatHTMLAppend(anchor);
            Helpers.threadRun(() -> {
                try {
                    Files.write(Paths.get(Init.VOICE_DIR + "/" + voice_filename), audio);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Could not persist voice message: {0}", ex.getMessage());
                }
            });
        }

        Helpers.GUIRun(() -> {
            if (WaitingRoomFrame.getInstance().isPartida_empezada() && !isActive()) {

                if (GameFrame.getInstance().getFastchat_dialog() != null) {
                    GameFrame.getInstance().getFastchat_dialog().refreshChatHistory();
                }

                // Self-block silences every incoming note; own notes auto-play
                // locally as send confirmation only if that option is enabled
                if (WaitingRoomFrame.CHAT_GAME_NOTIFICATIONS
                        && !AudioDeviceManager.isBlockVoiceMessages()
                        && (!nick.equals(local_nick) || AudioDeviceManager.isPlayOwnVoiceMessages())) {

                    GameFrame.NOTIFY_CHAT_QUEUE.add(new Object[]{nick, audio});

                    synchronized (GameFrame.NOTIFY_CHAT_QUEUE) {
                        GameFrame.NOTIFY_CHAT_QUEUE.notifyAll();
                    }
                }
            }
        });

        if (this.server) {
            // Relay off the peer's reader thread: a large note (~427KB) to N
            // peers (some slow or mid-reconnect, where getAes_key can block up
            // to ~1s) would otherwise stall the sender's reader thread and delay
            // its game commands (head-of-line). Same pattern as enviarNotaVoz.
            Helpers.threadRun(() -> {
                byte[] voicePayload = BinaryWire.encodeVoice(nick, audio);

                // Thread-safe iteration snapshot
                ArrayList<Participant> targets;
                synchronized (participantes) {
                    targets = new ArrayList<>(participantes.values());
                }

                for (Participant p : targets) {
                    try {
                        if (p != null && !p.isCpu() && !p.getNick().equals(nick)) {
                            p.writeBinaryFromServer(Helpers.encryptBytes(voicePayload, p.getAes_key(), p.getHmac_key()));
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
    }

    public void enviarNotaVoz(String nick, byte[] audio) {

        Helpers.threadRun(() -> {
            byte[] voicePayload = BinaryWire.encodeVoice(nick, audio);

            if (!server) {
                writeBinaryToServer(Helpers.encryptBytes(voicePayload,
                        getLocal_client_aes_key(), getLocal_client_hmac_key()));
            } else {
                // Snapshot values to prevent ConcurrentModificationException
                ArrayList<Participant> targets;
                synchronized (participantes) {
                    targets = new ArrayList<>(participantes.values());
                }

                for (Participant participante : targets) {
                    try {
                        if (participante != null && !participante.isCpu()) {
                            participante.writeBinaryFromServer(Helpers.encryptBytes(voicePayload,
                                    participante.getAes_key(), participante.getHmac_key()));
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
    }

    // ===================================================================
    // Stats DB sync (P2P): wire/keys glue. The protocol logic lives in
    // StatsSyncManager; this layer owns the per-peer channel keys and the
    // BinaryWire TYPE_DB framing, mirroring the voice-note send sites.
    // ===================================================================

    public void statsSyncOnConnectedToServer() {
        stats_sync_manager.onConnectedToServer();
    }

    public void statsSyncOnMessage(String peerNick, byte[] dbMessage, boolean iAmHost) {
        stats_sync_manager.onMessage(peerNick, dbMessage, iAmHost);
    }

    public void statsSyncOnPeerGone(String nick) {
        stats_sync_manager.onPeerGone(nick);
    }

    /** CLIENT → host: one stats-sync message over an encrypted TYPE_DB binary frame. */
    public void statsSyncRawSendToServer(byte[] dbMessage) {
        try {
            writeBinaryToServer(Helpers.encryptBytes(
                    BinaryWire.encode(BinaryWire.TYPE_DB, local_nick, dbMessage),
                    getLocal_client_aes_key(), getLocal_client_hmac_key()));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "StatsSync: send to host failed", ex);
        }
    }

    /**
     * HOST → one client: a stats-sync message over an encrypted TYPE_DB binary
     * frame. Returns false if the client is gone (its socket is closed), so an
     * in-flight push can stop promptly instead of churning the remaining batches.
     */
    public boolean statsSyncRawSendToClient(String nick, byte[] dbMessage) {
        Participant p = participantes.get(nick);
        if (p == null || p.isCpu()) {
            return false;
        }
        try {
            // writeBinaryFromServer returns true on a write failure (socket closed).
            boolean failed = p.writeBinaryFromServer(Helpers.encryptBytes(
                    BinaryWire.encode(BinaryWire.TYPE_DB, local_nick, dbMessage),
                    p.getAes_key(), p.getHmac_key()));
            return !failed;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "StatsSync: send to client " + nick + " failed", ex);
            return false;
        }
    }

    /** HOST: nicks of the currently connected (non-CPU) clients. */
    public java.util.List<String> statsSyncClientNicks() {
        java.util.ArrayList<Participant> snapshot;
        synchronized (participantes) {
            snapshot = new java.util.ArrayList<>(participantes.values());
        }
        java.util.ArrayList<String> nicks = new java.util.ArrayList<>();
        for (Participant p : snapshot) {
            if (p != null && !p.isCpu()) {
                nicks.add(p.getNick());
            }
        }
        return nicks;
    }

    public void enviarMensajeChat(String nick, String msg) {

        Helpers.threadRun(() -> {
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            if (!server) {
                try {
                    String comando = "CHAT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                            + Base64.getEncoder().encodeToString(msg.getBytes("UTF-8"));
                    writeCommandToServer(
                            Helpers.encryptCommand(comando, getLocal_client_aes_key(), iv, getLocal_client_hmac_key()));
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            } else {
                // Snapshot values to prevent ConcurrentModificationException
                ArrayList<Participant> targets;
                synchronized (participantes) {
                    targets = new ArrayList<>(participantes.values());
                }

                for (Participant participante : targets) {
                    try {
                        if (participante != null && !participante.isCpu()) {
                            String comando = "CHAT#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8")) + "#"
                                    + Base64.getEncoder().encodeToString(msg.getBytes("UTF-8"));
                            participante.writeCommandFromServer(Helpers.encryptCommand(comando,
                                    participante.getAes_key(), iv, participante.getHmac_key()));
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
    }

    public void updateParticipantLatency(String nick, int latency, int latency2) {
        Helpers.GUIRun(() -> {
            DefaultListModel<ParticipantJListData> model = (DefaultListModel<ParticipantJListData>) conectados
                    .getModel();

            for (int i = 0; i < model.getSize(); i++) {
                ParticipantJListData p = model.getElementAt(i);
                if (p.getNick().equals(nick)) {
                    p.setLatency(latency);
                    p.setLatency2(latency2);

                    // Esto fuerza que el JList se repinte
                    model.set(i, p); // ✅ importante
                    break;
                }
            }
        });
    }

    /**
     * Baja de un participante. Delega a NetServer (estado + broadcast DELUSER + UI callback).
     * Mantenido como facade para callers externos (Participant.java).
     *
     * NOTA: usado tanto por host (Participant.java al desconectarse un cliente)
     * como por cliente (al recibir DELUSER del servidor). Por eso la lógica vive
     * aquí y no en NetServer — el cliente no tiene net_server. El broadcast
     * DELUSER que solo aplica al host está guardado por isServer().
     */
    public synchronized void borrarParticipante(String nick) {
        // get + null-check en vez de containsKey: en el host su propia entrada
        // es un placeholder null por diseño (no hay Participant local) y un
        // null aquí es siempre "nada que borrar". De paso cierra el hueco
        // check-then-act frente al remove de NetServer, que muta el mapa
        // fuera de este monitor.
        Participant pToDel = participantes.get(nick);

        if (pToDel == null) {
            return;
        }

        Audio.playWavResource("misc/toilet.wav");

        String avatar_src = pToDel.getAvatar_chat_src();

        participantes.remove(nick);

        onParticipantRemoved(nick, avatar_src);

        // A client can leave the lobby at any time: drop its stats-sync tracking
        // so the host stops considering it for re-forwards.
        statsSyncOnPeerGone(nick);

        if (isServer() && !isPartida_empezada() && !exit) {
            try {
                String comando = "DELUSER#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                net_server.broadcastASYNCGAMECommand(comando, pToDel);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Alta de un participante.
     *
     * NOTA: usado tanto por host (serverSocketHandler al aceptar un cliente
     * nuevo, con socket no-null) como por cliente (registrar al servidor y a sí
     * mismo en la lista local cuando el cliente recibe la info de la sala, con
     * socket null). Por eso la lógica vive aquí y no en NetServer.
     */
    private synchronized void nuevoParticipante(String nick, File avatar, Socket socket, SecretKeySpec aes_k,
            SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(this, nick, avatar, socket, aes_k, hmac_k, cpu);

        participantes.put(nick, participante);
        participante.setUnsecure_player(unsecure);

        // Solo el host arranca el thread del Participant (socket no-null → conexión real)
        if (socket != null) {
            Helpers.threadRun(participante);
        }

        onParticipantAdded(nick, avatar, cpu);
    }

    /**
     * Callback de NetServer al añadir un Participant: actualiza la UI (lista de
     * conectados, contador y notificación de chat).
     */
    public void onParticipantAdded(String nick, File avatar, boolean cpu) {
        Helpers.GUIRun(() -> {
            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            ParticipantJListData participant_data = new ParticipantJListData(nick);
            ImageIcon participant_avatar = null;

            if (avatar != null) {
                try {
                    participant_avatar = Helpers.scaleIcon(avatar.getAbsolutePath(),
                            NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            } else {
                try {
                    participant_avatar = Helpers.scaleIcon(
                            getClass().getResource(
                                    (server && cpu) ? "/images/avatar_bot.png" : "/images/avatar_default.png"),
                            NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            }

            participant_data.setAvatar(participant_avatar);

            ((DefaultListModel) conectados.getModel()).addElement(participant_data);

            if (!nick.equals(server_nick) && !nick.equals(local_nick)) {
                chatHTMLAppendNewUser(nick);
            }
        });
    }

    /**
     * Callback de NetServer al eliminar un Participant: actualiza la UI (quita de
     * la lista, ajusta contador y botones, anota salida en chat).
     */
    public void onParticipantRemoved(String nick, String avatar_chat_src) {
        Helpers.GUIRun(() -> {
            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            DefaultListModel<ParticipantJListData> model = (DefaultListModel<ParticipantJListData>) conectados.getModel();
            ParticipantJListData toRemove = null;
            for (int i = 0; i < model.getSize(); i++) {
                ParticipantJListData p = model.getElementAt(i);
                if (p.getNick().equals(nick)) {
                    toRemove = p;
                    break;
                }
            }
            if (toRemove != null) {
                model.removeElement(toRemove);
            }

            if (server && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                if (participantes.size() < 2) {
                    empezar_timba.setEnabled(false);
                    kick_user.setEnabled(false);
                }
                new_bot_button.setEnabled(true);
            }

            chatHTMLAppendExitUser(nick, avatar_chat_src);
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
        settings_icon = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        panel_con = new javax.swing.JPanel();
        panel_conectados = new javax.swing.JScrollPane();
        conectados = new javax.swing.JList<ParticipantJListData>();
        kick_user = new javax.swing.JButton();
        empezar_timba = new javax.swing.JButton();
        barra = new javax.swing.JProgressBar();
        jPanel2 = new javax.swing.JPanel();
        new_bot_button = new javax.swing.JButton();
        game_info_blinds = new javax.swing.JLabel();
        game_info_hands = new javax.swing.JLabel();
        logo = new javax.swing.JLabel();
        game_info_buyin = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        pass_icon = new javax.swing.JLabel();
        tot_conectados = new javax.swing.JLabel();
        server_address_label = new javax.swing.JLabel();
        danger_server = new javax.swing.JLabel();
        chat_notifications = new javax.swing.JCheckBox();
        chat_scroll = new javax.swing.JScrollPane();
        chat = new javax.swing.JEditorPane();
        jPanel1 = new javax.swing.JPanel();
        chat_box_panel = new javax.swing.JPanel();
        chat_box = new com.tonikelope.coronapoker.EmojiChatBox();
        emoji_button = new javax.swing.JButton();
        image_button = new javax.swing.JButton();
        send_label = new javax.swing.JLabel();
        max_min_label = new javax.swing.JLabel();
        avatar_label = new javax.swing.JLabel();
        emoji_scroll_panel = new javax.swing.JScrollPane();
        emoji_panel = new com.tonikelope.coronapoker.EmojiPanel();
        tts_warning = new javax.swing.JLabel();
        latency_label = new javax.swing.JLabel();

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

        settings_icon.setToolTipText("Ajustes");
        settings_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        settings_icon.setDoubleBuffered(true);
        settings_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        settings_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                settings_iconMouseClicked(evt);
            }
        });

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
        Helpers.setTranslatedToolTip(conectados, "tooltip.connected_participants");
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

        kick_user.putClientProperty("i18n.key", "ui.expulsar_jugador");

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
        Helpers.setTranslatedToolTip(pass_icon, "tooltip.manage_password");
        pass_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pass_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pass_iconMouseClicked(evt);
            }
        });

        tot_conectados.setFont(new java.awt.Font("Dialog", 1, 30)); // NOI18N
        tot_conectados.setForeground(new java.awt.Color(0, 102, 255));
        tot_conectados.setText("0/10");

        server_address_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        server_address_label.setText("1.1.1.1");
        Helpers.setTranslatedToolTip(server_address_label, "tooltip.connection_data");
        server_address_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        server_address_label.setDoubleBuffered(true);
        server_address_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                server_address_labelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addComponent(pass_icon)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(server_address_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tot_conectados)
                                .addGap(0, 0, 0))
        );
        jPanel3Layout.setVerticalGroup(
                jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(0, 0, 0)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(server_address_label, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                                .addComponent(new_bot_button, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, 0))
        );

        new_bot_button.putClientProperty("i18n.key", "ui.anadir_bot");

        javax.swing.GroupLayout panel_arribaLayout = new javax.swing.GroupLayout(panel_arriba);
        panel_arriba.setLayout(panel_arribaLayout);
        panel_arribaLayout.setHorizontalGroup(
                panel_arribaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panel_arribaLayout.createSequentialGroup()
                                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(panel_con, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addComponent(empezar_timba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(barra, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(panel_arribaLayout.createSequentialGroup()
                                .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(settings_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                                        .addComponent(settings_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(sound_icon, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(empezar_timba, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(8, Short.MAX_VALUE))
        );

        empezar_timba.putClientProperty("i18n.key", "ui.a_jugar");

        danger_server.setBackground(new java.awt.Color(255, 0, 0));
        danger_server.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        danger_server.setForeground(new java.awt.Color(255, 255, 255));
        danger_server.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger_server.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/danger.png"))); // NOI18N
        Helpers.setTranslatedText(danger_server, "ui.posible_servidor_tramposo");
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
        Helpers.setTranslatedToolTip(image_button, "tooltip.send_image");
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
                                .addGap(8, 8, 8)
                                .addComponent(chat_box)
                                .addGap(8, 8, 8)
                                .addComponent(send_label)
                                .addGap(0, 0, 0)
                                .addComponent(max_min_label))
        );
        chat_box_panelLayout.setVerticalGroup(
                chat_box_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(emoji_button, javax.swing.GroupLayout.DEFAULT_SIZE, 43, Short.MAX_VALUE)
                        .addComponent(image_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(chat_box, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(chat_box_panelLayout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
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

        tts_warning.putClientProperty("i18n.key", "chat.aviso_la_privacidad_del_chat");

        latency_label.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        latency_label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        latency_label.setText("Latencia del servidor: 0 ms | 0 ms");
        latency_label.setDoubleBuffered(true);

        javax.swing.GroupLayout main_panelLayout = new javax.swing.GroupLayout(main_panel);
        main_panel.setLayout(main_panelLayout);
        main_panelLayout.setHorizontalGroup(
                main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(main_panelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(panel_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, 688, Short.MAX_VALUE)
                                        .addComponent(chat_notifications, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(chat_scroll, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(danger_server, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(latency_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addContainerGap())
        );
        main_panelLayout.setVerticalGroup(
                main_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(main_panelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(latency_label)
                                .addGap(1, 1, 1)
                                .addComponent(danger_server)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(panel_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, 508, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chat_notifications)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chat_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, 22, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, 0))
        );

        chat_notifications.putClientProperty("i18n.key", "ui.notificaciones_del_chat_durante_el_juego");

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

        int selectedIndex = conectados.getSelectedIndex();

        if (selectedIndex != -1) {

            DefaultListModel<ParticipantJListData> model = (DefaultListModel<ParticipantJListData>) conectados
                    .getModel();
            ParticipantJListData p = model.getElementAt(selectedIndex);

            String expulsado = p.getNick();

            if (!expulsado.equals(local_nick)) {

                // Cambiamos la contraseña por una aleatoria FUERTE (CSPRNG +
                // alphabet rico) — la anterior genRandomString solo usaba
                // a-z + Random pseudoaleatorio, 47 bits con length=10.
                if (password != null && !participantes.get(expulsado).isCpu()) {
                    password = Helpers.genStrongPassword(Math.max(password.length(), GEN_PASS_LENGTH));

                }

                kick_user.setEnabled(false);

                Helpers.threadRun(() -> {
                    Participant p_kicked = participantes.get(expulsado);
                    if (p_kicked == null) {
                        return;
                    }
                    boolean was_cpu = p_kicked.isCpu();
                    try {
                        p_kicked.setExit(true);

                        if (!was_cpu) {
                            String comando = "KICKED#" + Base64.getEncoder().encodeToString(expulsado.getBytes("UTF-8"));
                            p_kicked.writeCommandFromServer(
                                    Helpers.encryptCommand(comando, p_kicked.getAes_key(), p_kicked.getHmac_key()));
                        }

                        p_kicked.exitAndCloseSocket();

                        borrarParticipante(expulsado);

                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRun(() -> {
                        kick_user.setEnabled(participantes.size() > 1);

                        if (password != null) {
                            pass_icon.setToolTipText(password);

                        }

                        chat_box.requestFocus();
                    });
                    if (password != null && !was_cpu) {
                        Helpers.copyTextToClipboard(password);
                        mostrarMensajeInformativo(THIS, Translator.translate("ui.error.password_copiada"));
                    }
                });
            }
        } else {

            mostrarMensajeError(THIS, Translator.translate("ui.tienes_que_seleccionar_algun_participante"));
            chat_box.requestFocus();
        }

    }//GEN-LAST:event_kick_userActionPerformed

    private void empezar_timbaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_empezar_timbaActionPerformed
        if (mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.seguro_que_quieres_empezar_ya"),
                new ImageIcon(Init.class.getResource("/images/start.png"))) == 0 && participantes.size() >= 2
                && !WaitingRoomFrame.getInstance().isPartida_empezada()
                && !WaitingRoomFrame.getInstance().isPartida_empezando()) {

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
                            String nick = new String(Base64.getDecoder().decode(partes[0]), "UTF-8");
                            if (!"".equals(nick) && !participantes.containsKey(nick)) {
                                missing_players += nick + "\n\n";
                            }
                        }
                    }
                } catch (SQLException | UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            boolean vamos = ("".equals(missing_players) || mostrarMensajeInformativoSINO(this,
                    missing_players + Translator.translate("game.reconexion_pendiente"),
                    new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0);

            if (vamos) {
                partida_empezando = true;
                setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                this.empezar_timba.setEnabled(false);
                this.new_bot_button.setEnabled(false);
                this.new_bot_button.setVisible(false);
                game_info_buyin.setToolTipText(null);
                game_info_blinds.setToolTipText(null);
                game_info_hands.setToolTipText(null);
                this.kick_user.setEnabled(false);
                this.kick_user.setVisible(false);
                this.sound_icon.setVisible(false);
                this.status.setText(Translator.translate("game.inicializando_timba"));
                this.barra.setVisible(true);
                status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));

                Helpers.threadRun(() -> {
                    synchronized (lock_new_client) {
                        boolean ocupados;
                        do {
                            ocupados = false;
                            ArrayList<Participant> snapshot;
                            synchronized (participantes) {
                                snapshot = new ArrayList<>(participantes.values());
                            }

                            for (Participant p : snapshot) {
                                if (p != null && !p.isCpu()) {
                                    if (!p.getPre_game_socket_writer_queue().isEmpty()) {
                                        ocupados = true;
                                        p.setAsync_wait(true);
                                    } else {
                                        p.setAsync_wait(false);
                                    }
                                }
                            }

                            if (ocupados && net_server != null) {
                                synchronized (net_server.getLock_client_pre_game_commands_wait()) {
                                    try {
                                        net_server.getLock_client_pre_game_commands_wait().wait(PRE_GAME_COMMANDS_LOCK);
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                        } while (ocupados);

                        Helpers.GUIRunAndWait(() -> {
                            // Defensivo (en el host la rueda es modal y bloquea "Empezar",
                            // así que normalmente no estaría abierta): cerrar sin preguntar.
                            SettingsDialog.closeIfOpen();
                            new GameFrame(WaitingRoomFrame.this, local_nick, true);
                        });
                        partida_empezada = true;
                        GameFrame.getInstance().AJUGAR();
                    }
                });
            }
        } else {
            chat_box.requestFocus();
        }
        revalidate();
        repaint();

    }//GEN-LAST:event_empezar_timbaActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing

        if (!barra.isVisible() || !booting) {

            // client_threads sólo existe en NetServer; en cliente no aplica (queda como "true" vacuo)
            boolean clientThreadsEmpty = (net_server == null) || net_server.getClient_threads().isEmpty();
            if (!booting && clientThreadsEmpty && !partida_empezando) {

                if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {

                    if (exit || (net_client != null && net_client.isReconnecting())) {
                        if (mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
                            exit = true;
                            Helpers.savePropertiesFile();
                            System.exit(1);
                        }
                    } else if (mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.seguro_que_quieres_salir_ahora"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
                        exit = true;

                        Helpers.threadRun(() -> {
                            // Thread-safe shutdown iteration
                            if (isServer()) {
                                synchronized (participantes) {
                                    for (Participant p : participantes.values()) {
                                        if (p != null) {
                                            p.exitAndCloseSocket();
                                        }
                                    }
                                }
                                closeServerSocket();
                            } else if (net_client != null && net_client.getLocal_client_socket() != null && !net_client.isReconnecting()) {
                                try {
                                    // We force the client to send the Testament
                                    String exitCmd = "EXIT";
                                    if (GameFrame.getInstance() != null && GameFrame.getInstance().getCrupier() != null) {
                                        String testamento = GameFrame.getInstance().getCrupier().getTestamentoCriptografico();
                                        if (!testamento.equals("*#*#*#*")) {
                                            exitCmd += "#" + testamento;
                                        }
                                    }
                                    writeCommandToServer(Helpers.encryptCommand(exitCmd, getLocal_client_aes_key(), getLocal_client_hmac_key()));
                                    net_client.getLocal_client_socket().close();
                                } catch (Exception ex) {
                                    LOGGER.log(Level.SEVERE, null, ex);
                                }
                            }
                        });
                    }

                } else {
                    setVisible(false);
                }

            } else if (mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
                exit = true;
                Helpers.savePropertiesFile();
                System.exit(1);
            }

            if (net_client != null) {
                synchronized (net_client.getLock_client_reconnect()) {
                    net_client.getLock_client_reconnect().notifyAll();
                }
            }
        } else if (booting && mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
            exit = true;
            Helpers.savePropertiesFile();
            System.exit(1);
        }

    }//GEN-LAST:event_formWindowClosing

    private void settings_iconMouseClicked(java.awt.event.MouseEvent evt) {
        // Abre el diálogo de ajustes en modo general (Apariencia + Sonido): no hay
        // GameFrame en la sala de espera, así que la pestaña Partida no se monta.
        SettingsDialog.open(this);
    }

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.setScaledIconLabel(sound_icon,
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoMouseClicked

        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_new_bot_buttonActionPerformed
        if (participantes.size() < MAX_PARTICIPANTES) {
            new_bot_button.setEnabled(false);
            Audio.playWavResource("misc/laser.wav");

            Helpers.threadRun(() -> {
                try {
                    String bot_nick;
                    int conta_bot = 0;
                    do {
                        conta_bot++;
                        bot_nick = "CoronaBot$" + String.valueOf(conta_bot);
                    } while (participantes.get(bot_nick) != null);

                    String comando = "NEWUSER#" + Base64.getEncoder().encodeToString(bot_nick.getBytes("UTF-8")) + "#0";
                    byte[] avatar_b = null;
                    try (java.io.InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                        if (is != null) {
                            avatar_b = is.readAllBytes();
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Failed to load bot avatar", ex);
                    }
                    comando += "#" + (avatar_b != null ? Base64.getEncoder().encodeToString(avatar_b) : "*");

                    synchronized (lock_new_client) {
                        nuevoParticipante(bot_nick, null, null, null, null, true, false);
                        broadcastASYNCGAMECommandFromServer(comando, participantes.get(bot_nick));
                        Helpers.GUIRun(() -> {
                            empezar_timba.setEnabled(true);
                            kick_user.setEnabled(true);
                            new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                            chat_box.requestFocus();
                            revalidate();
                            repaint();
                        });
                    }
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            });
        }

    }//GEN-LAST:event_new_bot_buttonActionPerformed

    private void pass_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pass_iconMouseClicked

        if (!server || WaitingRoomFrame.getInstance().isPartida_empezada()) {
            return;
        }

        if (javax.swing.SwingUtilities.isRightMouseButton(evt)) {
            // Click derecho → menú contextual con 3 opciones.
            javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

            javax.swing.JMenuItem copiarItem = new javax.swing.JMenuItem(
                    Translator.translate("auth.menu_copiar_password"));
            copiarItem.setEnabled(password != null);
            copiarItem.addActionListener(ae -> copyCurrentPasswordToClipboard());
            menu.add(copiarItem);

            javax.swing.JMenuItem cambiarItem = new javax.swing.JMenuItem(
                    Translator.translate("auth.menu_cambiar_password"));
            cambiarItem.addActionListener(ae -> promptAndSetNewPassword());
            menu.add(cambiarItem);

            javax.swing.JMenuItem generarItem = new javax.swing.JMenuItem(
                    Translator.translate("auth.menu_generar_password_fuerte"));
            generarItem.addActionListener(ae -> generateAndShowStrongPassword());
            menu.add(generarItem);

            menu.show(pass_icon, evt.getX(), evt.getY());
            return;
        }

        // Click izquierdo → atajo: copiar la actual al portapapeles
        // (silencioso, mensaje breve). Si no hay password, genera fuerte.
        if (password != null) {
            copyCurrentPasswordToClipboard();
        } else {
            generateAndShowStrongPassword();
        }
    }

    /**
     * Atajo del click izquierdo y del item "Copiar contraseña" del menú:
     * copia la password actual al portapapeles + popup breve.
     */
    private void copyCurrentPasswordToClipboard() {
        if (password == null) {
            return;
        }
        pass_icon.setEnabled(true);
        pass_icon.setToolTipText(password);
        Helpers.copyTextToClipboard(password);
        mostrarMensajeInformativo(this,
                Translator.translate("auth.password_actual_copiada", password));
    }

    /**
     * Item "Cambiar contraseña" del menú: pide al usuario una nueva password
     * con un JPasswordField que cambia de color (amarillo débil / verde
     * fuerte) en tiempo real, paridad con NewGameDialog. Si el resultado
     * tiene <60 bits de entropía, popup informativo (no bloquea).
     * Input vacío → partida sin password.
     */
    private void promptAndSetNewPassword() {
        javax.swing.JPasswordField field = new javax.swing.JPasswordField(20);
        Helpers.attachPasswordStrengthHint(field);
        Helpers.attachPasswordRevealButton(field);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
        panel.add(new javax.swing.JLabel(Translator.translate("auth.input_nueva_password")),
                java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);

        int result = javax.swing.JOptionPane.showConfirmDialog(
                this, panel,
                Translator.translate("auth.menu_cambiar_password"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (result != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        char[] chars = field.getPassword();
        String trimmed = (chars == null) ? "" : new String(chars).trim();
        if (trimmed.isEmpty()) {
            password = null;
            pass_icon.setEnabled(false);
            pass_icon.setToolTipText(null);
            mostrarMensajeInformativo(this,
                    Translator.translate("auth.password_eliminada"));
            return;
        }
        password = trimmed;
        pass_icon.setEnabled(true);
        pass_icon.setToolTipText(password);
        Helpers.copyTextToClipboard(password);
        int bits = Helpers.estimatePasswordEntropyBits(password);
        if (bits < 60) {
            mostrarMensajeInformativo(this,
                    Translator.translate("ui.password_debil_aviso", bits));
        }
        mostrarMensajeInformativo(this,
                Translator.translate("auth.password_cambiada", password));
    }

    /**
     * Item "Generar contraseña fuerte" del menú (y atajo si no hay
     * password). Usa CSPRNG + alphabet rico — ~86 bits con length=14.
     */
    private void generateAndShowStrongPassword() {
        password = Helpers.genStrongPassword(GEN_PASS_LENGTH);
        pass_icon.setEnabled(true);
        pass_icon.setToolTipText(password);
        Helpers.copyTextToClipboard(password);
        mostrarMensajeInformativo(this,
                Translator.translate("auth.nueva_password_generada", password));
    }
    //GEN-LAST:event_pass_iconMouseClicked

    private void tts_warningMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tts_warningMouseClicked

        mostrarMensajeInformativo(this,
                Translator.translate("ui.tts_warning_detail"),
                "justify", (int) Math.round(getWidth() * 0.8f));
    }//GEN-LAST:event_tts_warningMouseClicked

    private void server_address_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_server_address_labelMouseClicked

        if (server) {
            int port = net_server.getServer_socket().getLocalPort();
            Helpers.copyTextToClipboard("[CoronaPoker] INTERNET -> " + Helpers.getMyPublicIP() + ":"
                    + String.valueOf(port) + "\n\nLAN -> " + Helpers.getMyLocalIP() + ":"
                    + String.valueOf(port));
            mostrarMensajeInformativo(this, Translator.translate("conn.datos_de_conexion_copiados_en"));
        }
    }//GEN-LAST:event_server_address_labelMouseClicked

    // Pinta las caracteristicas de la partida en la sala de espera a partir del
    // gameinfo "BUYIN|BLINDS|HANDS". Si el campo buyin es el tag de buy-in
    // variable, oculta la bolsa (cada jugador elige su buy-in) manteniendo ciegas
    // y manos. Numerico -> buyin normal. No numerico -> caso recover (existente).
    private void applyGameInfoBuyinLabel(String[] game_info) {
        String buyin_field = game_info[0].trim();
        if (VARIABLE_BUYIN_TAG.equals(buyin_field)) {
            // Texto = tag (aunque oculto): asi viaja en el payload NICKOK/GAMEINFO
            // que se reconstruye desde game_info_buyin.getText().
            game_info_buyin.setText(VARIABLE_BUYIN_TAG);
            game_info_buyin.setVisible(false);
            game_info_blinds.setVisible(true);
            game_info_blinds.setText(game_info[1]);
            if (game_info.length > 2) {
                game_info_hands.setVisible(true);
                game_info_hands.setText(game_info[2]);
            } else {
                game_info_hands.setVisible(false);
            }
        } else if (buyin_field.matches("[0-9,.*]+")) {
            boolean rebuy = !buyin_field.endsWith("*");
            game_info_buyin.setVisible(true);
            game_info_buyin.setText(Helpers.money2String(Double.parseDouble(buyin_field.replace("*", ""))) + (rebuy ? "" : "*"));
            game_info_blinds.setVisible(true);
            game_info_blinds.setText(game_info[1]);
            if (game_info.length > 2) {
                game_info_hands.setVisible(true);
                game_info_hands.setText(game_info[2]);
            } else {
                game_info_hands.setVisible(false);
            }
        } else {
            game_info_blinds.setVisible(false);
            game_info_hands.setVisible(false);
            game_info_buyin.setIcon(null);
        }
    }

    private void game_info_buyinMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_buyinMouseClicked
        // DESACTIVADO: la config de la timba en la sala se edita ahora desde la rueda
        // (SettingsDialog -> pestaña Partida), no haciendo click sobre las ciegas. El
        // listener sigue registrado en el .form pero el handler es un no-op (los labels
        // de buyin/ciegas/manos delegan en este método, así que los tres quedan inertes).
    }//GEN-LAST:event_game_info_buyinMouseClicked

    // Refresca los labels buyin/ciegas/manos de la sala desde GameFrame.* y difunde a los
    // clientes el GAMEINFO (display) + el GAMECONFIG (espejo COMPLETO). Lo invoca el panel
    // de la pestaña Partida de la rueda al GUARDAR (solo el HOST). Reemplaza al broadcast
    // que hacía el viejo click sobre las ciegas.
    public void broadcastGameConfigAndLabels() {

        final String[] payload = new String[1];

        Helpers.GUIRunAndWait(() -> {
            if (GameFrame.FIXED_BUYIN) {
                game_info_buyin.setVisible(true);
                game_info_buyin.setText(Helpers.money2String(GameFrame.BUYIN) + (GameFrame.REBUY ? "" : "*"));
            } else {
                // Variable: la bolsa no aplica. El tag viaja en el GAMEINFO via getText().
                game_info_buyin.setText(VARIABLE_BUYIN_TAG);
                game_info_buyin.setVisible(false);
            }

            game_info_blinds.setText(Helpers.money2String(GameFrame.CIEGA_PEQUEÑA) + " / "
                    + Helpers.money2String(GameFrame.CIEGA_GRANDE)
                    + (GameFrame.CIEGAS_DOUBLE > 0
                            ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE)
                            + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*")
                            : ""));

            game_info_hands.setText(GameFrame.MANOS != -1 ? String.valueOf(GameFrame.MANOS) : "");
            game_info_hands.setVisible(!"".equals(game_info_hands.getText()));

            // Capturar el payload del display EN EL EDT (no leer Swing fuera de él).
            payload[0] = game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|"
                    + game_info_hands.getText();

            pack();
            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);
        });

        Helpers.threadRun(() -> {
            try {
                broadcastASYNCGAMECommandFromServer("GAMEINFO#"
                        + Base64.getEncoder().encodeToString(payload[0].getBytes("UTF-8")), null);
                broadcastASYNCGAMECommandFromServer("GAMECONFIG#"
                        + Base64.getEncoder().encodeToString(
                                GamePreset.Settings.fromGameFrame().serialize().getBytes("UTF-8")), null);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
    }

    private void chat_notificationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_notificationsActionPerformed

        CHAT_GAME_NOTIFICATIONS = chat_notifications.isSelected();

        Helpers.PROPERTIES.setProperty("chat_game_notifications", String.valueOf(CHAT_GAME_NOTIFICATIONS));

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_chat_notificationsActionPerformed

    private void emoji_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_emoji_buttonActionPerformed

        emoji_scroll_panel.getHorizontalScrollBar().setValue(0);

        emoji_scroll_panel.setVisible(!emoji_scroll_panel.isVisible());

        chat_box.requestFocus();

        revalidate();

        repaint();

        Helpers.threadRun(() -> {
            Helpers.GUIRun(() -> {
                main_scroll_panel.getVerticalScrollBar()
                        .setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
            });
        });
    }//GEN-LAST:event_emoji_buttonActionPerformed

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_boxActionPerformed

        String mensaje = ((EmojiChatBox) chat_box).getRawText().trim();

        if (chat_enabled && mensaje.length() > 0) {

            chatHTMLAppend(local_nick + ":(" + Helpers.getLocalTimeString() + ") "
                    + mensaje.replaceAll("(?i)img(s?)://", "http$1://") + "\n");

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
        ChatImageDialog chat_image_dialog = new ChatImageDialog(this, true, (int) Math.round(this.getHeight() * 0.9f));
        chat_image_dialog.setLocationRelativeTo(this);
        chat_image_dialog.setVisible(true);
    }//GEN-LAST:event_image_buttonActionPerformed

    private void chatFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chatFocusLost
        this.chat_scroll.getVerticalScrollBar().setValue(this.chat_scroll.getVerticalScrollBar().getMaximum());
        this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.chat_scroll.setBorder(chat_scroll_border);
        ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.UPDATE_WHEN_ON_EDT);
        chat.setFocusable(false);
    }//GEN-LAST:event_chatFocusLost

    private void emoji_scroll_panelComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_emoji_scroll_panelComponentHidden
        emoji_panel.refreshEmojiHistory();
    }//GEN-LAST:event_emoji_scroll_panelComponentHidden

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        chat_box.requestFocus();
    }//GEN-LAST:event_formWindowOpened

    private void send_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_send_labelMouseClicked
        chat_boxActionPerformed(null);
    }//GEN-LAST:event_send_labelMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown

        if (isPartida_empezada() && panel_arriba.isVisible()) {

            panel_arriba.setVisible(false);

            Helpers.setScaledIconLabel(max_min_label,
                    getClass().getResource("/images/" + (panel_arriba.isVisible() ? "maximize" : "minimize") + ".png"),
                    chat_box.getHeight(), chat_box.getHeight());

            main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
        }

        Helpers.setScaledIconLabel(sound_icon,
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        if (!chat_text.toString().isEmpty()) {
            refreshChatPanel();
        }

        // Durante la partida el juego esta en borderless fullscreen; mantener la
        // sala de espera siempre-encima mientras es visible evita que se vaya
        // detras al clicar el juego. Es solo z-order: NO roba el foco (eso era el
        // hide/show reclaim de formWindowDeactivated, ya eliminado). En el lobby
        // (sin partida) queda normal para no tapar sus dialogos modales.
        setAlwaysOnTop(isPartida_empezada());

        main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());

        chat_box.requestFocus();

        revalidate();

        repaint();

        if (isPartida_empezada()) {
            // Durante el juego el JTextPane sigue recibiendo HTMLEditorKitAppend
            // mientras la ventana esta oculta. Los <img> resuelven su bitmap via
            // ImageObserver y disparan preferenceChanged, pero al no ser
            // displayable el componente la cascada de relayout no recalcula las
            // allocations de los RoundedBubbleView -> burbujas con imagen quedan
            // con geometria stale al reabrir el chat desde el menu in-game.
            //
            // Mimica exacta de lo que hace chatMouseClicked (el evento que el
            // usuario disparaba a mano para "arreglar" el pintado): cambiar
            // brevemente la policy del scrollpane de NEVER a AS_NEEDED fuerza
            // a JScrollPane a recalcular layout, el viewport reentrega un
            // setSize al chat con potencial cambio de width al mostrar/ocultar
            // la scrollbar, y el HTMLDocument relaya el view tree con los
            // tamanos reales ya resueltos. Tras un segundo invokeLater
            // restauramos la policy original para no dejar barras visibles si
            // chatFocusLost no las cubre. setSize sobre chat directamente no
            // funciona aqui: dentro de un JScrollPane el viewport sobreescribe
            // el size desde su extent y el HTMLDocument no se invalida.
            final int v_policy = chat_scroll.getVerticalScrollBarPolicy();
            final int h_policy = chat_scroll.getHorizontalScrollBarPolicy();
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (!chat_scroll.isDisplayable()) {
                    return;
                }
                chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    chat_scroll.setVerticalScrollBarPolicy(v_policy);
                    chat_scroll.setHorizontalScrollBarPolicy(h_policy);
                });
            });
        }
    }//GEN-LAST:event_formComponentShown

    private void max_min_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_max_min_labelMouseClicked
        if (max_min_label.isEnabled()) {
            panel_arriba.setVisible(!panel_arriba.isVisible());
            Helpers.setScaledIconLabel(max_min_label,
                    getClass().getResource("/images/" + (panel_arriba.isVisible() ? "maximize" : "minimize") + ".png"),
                    chat_box.getHeight(), chat_box.getHeight());

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    main_scroll_panel.getVerticalScrollBar()
                            .setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
                });
            });
        }

    }//GEN-LAST:event_max_min_labelMouseClicked

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentHidden

        chat.setText("<html><body style='background-image: url(" + background_chat_src + ")'></body></html>");
        chat_box.requestFocus();

        if (partida_empezando) {
            partida_empezando = false;
        }
    }//GEN-LAST:event_formComponentHidden

    private void formWindowStateChanged(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowStateChanged

        if ((evt.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            avatar_label.setText(this.local_nick);
        } else {
            avatar_label.setText("");
        }
    }//GEN-LAST:event_formWindowStateChanged

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_chatMouseClicked

        // Manual hit-test for voice note anchors: the html32 DTD parser and
        // the stock LinkController do not get along with custom schemes, so
        // the hyperlink route is unreliable here. A link click must also NOT
        // toggle the scroll-freeze below.
        if (javax.swing.SwingUtilities.isLeftMouseButton(evt) && clickVoiceNoteAt(evt)) {
            return;
        }

        if (!chat.isFocusable()) {
            this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Color.GREEN, 3));
            ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            chat.setFocusable(true);
            chat.requestFocus();
        }
    }//GEN-LAST:event_chatMouseClicked

    private boolean clickVoiceNoteAt(java.awt.event.MouseEvent evt) {

        try {
            int pos = chat.viewToModel2D(evt.getPoint());

            if (pos < 0) {
                return false;
            }

            javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument) chat.getDocument();

            // viewToModel returns an insertion position: a click on the right
            // half of the last glyph maps to the run end, so probe both sides
            javax.swing.text.Element run = doc.getCharacterElement(pos);

            javax.swing.text.AttributeSet anchor = (javax.swing.text.AttributeSet) run.getAttributes().getAttribute(javax.swing.text.html.HTML.Tag.A);

            if (anchor == null && pos > 0) {
                run = doc.getCharacterElement(pos - 1);
                anchor = (javax.swing.text.AttributeSet) run.getAttributes().getAttribute(javax.swing.text.html.HTML.Tag.A);
            }

            if (anchor == null) {
                return false;
            }

            Object href = anchor.getAttribute(javax.swing.text.html.HTML.Attribute.HREF);

            if (href == null || !href.toString().startsWith("voicenote:")) {
                return false;
            }

            // The click must land on the anchor's painted box (with margin for
            // baseline-aligned rows and HiDPI rounding), not on the empty space
            // viewToModel clamps from
            java.awt.Rectangle box = chat.modelToView2D(run.getStartOffset()).getBounds()
                    .union(chat.modelToView2D(run.getEndOffset()).getBounds());

            box.grow(12, 16);

            if (!box.contains(evt.getPoint())) {
                return false;
            }

            VoiceMessageManager.playFromChat(href.toString().substring("voicenote:".length()));

            return true;

        } catch (Exception ex) {
            return false;
        }
    }

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeactivated
        // La sala de espera es una ventana normal: no se aferra al foco. Durante
        // la partida el foco lo gestiona el GameFrame; el chat in-game es el chat
        // rapido. (Antes aqui se ocultaba y re-mostraba para robar el foco de
        // vuelta, lo que peleaba con el GameFrame y causaba focos erraticos.)
    }//GEN-LAST:event_formWindowDeactivated

    private void formWindowDeiconified(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowDeiconified
    }//GEN-LAST:event_formWindowDeiconified

    private void game_info_blindsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_blindsMouseClicked
        game_info_buyinMouseClicked(evt);
    }//GEN-LAST:event_game_info_blindsMouseClicked

    private void game_info_handsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_info_handsMouseClicked
        game_info_buyinMouseClicked(evt);
    }//GEN-LAST:event_game_info_handsMouseClicked

    private void chatCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_chatCaretUpdate

    }//GEN-LAST:event_chatCaretUpdate

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JProgressBar barra;
    private javax.swing.JEditorPane chat;
    private javax.swing.JTextPane chat_box;
    private javax.swing.JPanel chat_box_panel;
    private javax.swing.JCheckBox chat_notifications;
    private javax.swing.JScrollPane chat_scroll;
    private javax.swing.JList<ParticipantJListData> conectados;
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
    private javax.swing.JLabel latency_label;
    private javax.swing.JLabel logo;
    private javax.swing.JPanel main_panel;
    private javax.swing.JScrollPane main_scroll_panel;
    private javax.swing.JLabel max_min_label;
    private javax.swing.JButton new_bot_button;
    private javax.swing.JPanel panel_arriba;
    private javax.swing.JPanel panel_con;
    private javax.swing.JScrollPane panel_conectados;
    private javax.swing.JLabel pass_icon;
    private javax.swing.JLabel send_label;
    private javax.swing.JLabel server_address_label;
    private javax.swing.JLabel settings_icon;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel status;
    private javax.swing.JLabel tot_conectados;
    private javax.swing.JLabel tts_warning;
    // End of variables declaration//GEN-END:variables
}
