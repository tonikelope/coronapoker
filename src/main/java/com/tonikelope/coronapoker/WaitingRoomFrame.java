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
// NetBeans form DISABLED: the matching .form was renamed to .form.bak on purpose.
// This class's initComponents (the generated //GEN block) is hand-edited (i18n keys via
// putClientProperty, DIALOG_ZOOM scaling, wrapped/translated tooltips and/or manual layout),
// none of which the .form carries. Opening this form in the NetBeans GUI designer and saving
// it would regenerate initComponents from the .form and silently wipe those edits. Maintain
// this class by hand and do NOT restore the .form (the original is kept in git history).
public class WaitingRoomFrame extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(WaitingRoomFrame.class.getName());

    public static final int MAX_PARTICIPANTES = 10;

    enum RemoteRosterAdmission {
        ADMIT,
        DUPLICATE,
        REJECT
    }

    static RemoteRosterAdmission remoteRosterAdmission(int currentSize, boolean exactDuplicate,
            boolean nfcCollision) {
        if (exactDuplicate) {
            return RemoteRosterAdmission.DUPLICATE;
        }
        return currentSize >= MAX_PARTICIPANTES || nfcCollision
                ? RemoteRosterAdmission.REJECT : RemoteRosterAdmission.ADMIT;
    }

    /**
     * The dollar sign is reserved for host-created bot nicknames. Bots never
     * arrive through the network JOIN path, so an incoming client nick that
     * contains it is unauthorized and must be rejected before admission.
     */
    static boolean hasReservedBotNickCharacter(String nick) {
        return nick != null && nick.indexOf('$') >= 0;
    }
    public static final String MAGIC_BYTES = "5c1f158dd9855cc9";
    public static final String POISON_PILL = "___SOCKET_BYE___";
    public static final int PING_PONG_TIMEOUT = 10000;
    // Read deadline applied to the socket during the handshake (magic bytes + ECDH +
    // session_id + JOIN + NICKOK/intro/chat history). A legitimate peer finishes this
    // exchange in <1s; the only real case that gets close is a first-time Ed25519
    // keypair generation on a very slow CPU. 30s of margin.
    //
    // Critical to defend against peers that open a socket but NEVER send bytes (local
    // DoS via mute sockets) or that trickle bytes one at a time (starving the handshake
    // thread forever). The timeout is RESET to 0 (no limit) as soon as the handshake
    // succeeds and before the Participant's normal reader takes over, so legitimate
    // inter-hand pauses never trip it.
    public static final int HANDSHAKE_TIMEOUT_MS = 30000;
    // Cap on the length-prefix int the peer sends on each handshake read. The real EC
    // X.509 (P-256) pubkey is ~91 bytes; 256 leaves room in case the curve ever changes.
    // Without this cap a hostile peer could send Integer.MAX_VALUE and force a
    // new byte[2GB] -> instant OOM.
    public static final int HANDSHAKE_MAX_PUBKEY_BYTES = 256;
    // The session_id the server issues is a fixed 16 bytes. 64 leaves future headroom
    // without allowing abuse.
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
    // Threshold of consecutive missed PONGs before we close the socket ourselves. Safety
    // net for "mute" sockets (peer killed without RST, one-way network partition, peer
    // stuck in an infinite GC pause). With N=3, PING_INTERVAL_MS=5s and
    // PING_PONG_TIMEOUT=10s, worst-case detection is ~3*(10+5)=45s. The primary path is
    // still the IOException caught on write (~0ms detection on the next outgoing PING)
    // and the socket's SO_KEEPALIVE.
    public static final int MAX_CONSECUTIVE_PING_FAILURES = 3;

    // Deadline for WRITING the heartbeat (distinct from waiting on the PONG). Deliberately
    // generous: that outgoing turn is shared with binary sends (320 KB voice notes,
    // avatars, recovery data, stats batches), which with several peers and slow upload
    // can take tens of seconds even when everyone is healthy. Only closes after
    // MAX_CONSECUTIVE_PING_FAILURES in a row, and never during a reconnect.
    public static final int PING_WRITE_STALL_TIMEOUT = 60000;
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
    private static final SessionGuard SESSION_GUARD = new SessionGuard();

    private final File local_avatar;
    private final SessionGuard.Generation session_generation = SESSION_GUARD.beginSession();
    // Every avatar received from the wire is validated before Swing/ImageIO can
    // rasterize it and is stored in this room-owned directory.
    private final AvatarIO avatar_io = AvatarIO.createDefault();
    private final Map<String, Participant> participantes = Collections.synchronizedMap(new LinkedHashMap<>());

    // Pre-auth anti-DoS: cap on SIMULTANEOUS handshakes (EC keygen + thread). The accept
    // loop reserves a slot BEFORE spending resources; once exhausted, the incoming
    // connection is dropped instantly (no thread, no keygen). The slot is released when
    // the handshake finishes (finally in serverSocketHandler). Generous for a game among
    // friends (legitimate joins are few); bounds a connection flood to
    // MAX_CONCURRENT_HANDSHAKES.
    public static final int MAX_CONCURRENT_HANDSHAKES = 16;
    private final java.util.concurrent.Semaphore handshake_slots = new java.util.concurrent.Semaphore(MAX_CONCURRENT_HANDSHAKES);
    private final Object ping_pong_lock = new Object();
    private final Object lock_new_client = new Object();
    private final boolean server;
    private final String local_nick;
    // Stats DB sync (P2P): the protocol logic lives in StatsSyncManager; the
    // per-peer channel keys and TYPE_DB framing glue live in this class.
    private final StatsSyncManager stats_sync_manager = new StatsSyncManager(this);
    private volatile String server_ip_port;  // ip:port — host (to parse the local port) and client (to connect).
    private volatile String server_nick;
    private volatile String gameinfo_original = null;
    // Tag in gameinfo_original's buyin field when the game uses a variable buy-in: the
    // waiting room hides the buy-in feature (the pot) because each player picks their
    // own buy-in when sitting down at the table.
    private static final String VARIABLE_BUYIN_TAG = "VAR";
    // FULL mirror of the game config the HOST broadcasts (serialized
    // GamePreset.Settings) so the client's READ-ONLY "Game" tab in the settings wheel
    // can show every detail. Arrives in the handshake (NICKOK) and refreshes on every
    // GAMECONFIG. The client does NOT write GameFrame.* from this (the real config
    // arrives in the INIT at startup); only the read-only panel reads it. null until
    // the first sync.
    public static volatile String GAMECONFIG_MIRROR = null;
    private volatile boolean chat_enabled = true;
    private volatile boolean upnp = false;
    private volatile int server_port = 0;
    private volatile boolean booting = false;
    private volatile boolean partida_empezada = false;
    // Telemetry: latest snapshot received from the host. Updated in the "TELEMETRY"
    // branch of cliente()'s GAME sub-switch. Readers (future LatencyDot, F7 label)
    // access it via getLatest_telemetry().
    private volatile Helpers.TelemetryFrame latest_telemetry = null;
    private volatile boolean partida_empezando = false;
    private volatile String password = null;
    private final java.util.concurrent.atomic.AtomicLong password_version = new java.util.concurrent.atomic.AtomicLong();
    // The socket consumer must remain asynchronous for REBUYNOW (it also reads the CONF that
    // the rebuy sender is waiting for). A cached pool may execute those tasks out of order, so
    // stamp every rebuy relay/denial as it arrives and let Crupier discard late tasks.
    private final java.util.concurrent.atomic.AtomicLong rebuy_relay_sequence = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong pause_relay_sequence = new java.util.concurrent.atomic.AtomicLong();
    private final Object pause_relay_order_lock = new Object();
    private long pause_relay_applied_sequence = 0L;
    private volatile boolean exit = false;
    // The host has started a CLEAN exit (game over or "stop game" with force_recover):
    // arrives as GAME#<id>#SERVEREXIT[RECOVER] and the host closes the socket right
    // after (confirmation=false), so the reader sees a null-read almost immediately.
    // Set as soon as the reader READS that frame, before the null-read, so the null-read
    // does NOT mistake it for a network drop and fire a spurious reconnect: the consumer
    // drives the ordered shutdown (finTransmision -> waiting room) when it dequeues the
    // frame. Automatic reconnection is reserved for real network drops.
    private volatile boolean server_graceful_exit = false;
    private volatile StringBuffer chat_text = new StringBuffer();
    private final String background_chat_src;
    private volatile String local_avatar_chat_src;
    private volatile Border chat_scroll_border = null;

    // Exactly one of the two is non-null, depending on the role (server flag).
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

    /**
     * Called by the single socket-consumer thread before dispatching a rebuy
     * task.
     */
    private long nextRebuyRelaySequence() {
        return rebuy_relay_sequence.incrementAndGet();
    }

    /**
     * Called by the single socket-consumer thread before dispatching a PAUSE
     * task.
     */
    private long nextPauseRelaySequence() {
        return pause_relay_sequence.incrementAndGet();
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

    // Refreshes the waiting room's speaker icon from SONIDOS. Called by
    // GameFrame.setSonidos so a change made in the audio settings dialog shows up here.
    public static void refreshSoundIcon() {

        WaitingRoomFrame sala = getInstance();

        if (sala != null) {
            Helpers.GUIRun(() -> {
                Helpers.setScaledIconLabel(sala.sound_icon, WaitingRoomFrame.class.getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM));
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
        ArrayList<Participant> snapshot;
        synchronized (participantes) {
            snapshot = new ArrayList<>(participantes.values());
        }
        closeAcceptedClientSockets(snapshot);
    }

    static void closeAcceptedClientSockets(Iterable<Participant> connectedParticipants) {
        if (connectedParticipants == null) {
            return;
        }
        for (Participant participant : connectedParticipants) {
            if (participant != null) {
                participant.setExit(true);
                participant.socketCloseForTeardown();
            }
        }
    }

    public void closeClientSocket() {
        if (net_client != null) {
            net_client.closeClientSocket();
        }
    }

    public void closeClientSocketForTeardown() {
        if (net_client != null) {
            net_client.closeClientSocketForTeardown();
        }
    }

    private void closeCriticalHostChannel() {
        exit = true;
        closeClientSocket();
    }

    public static void resetInstance() {
        // A return-to-menu cancel path may already have nulled THIS (see cliente()/servidor()); this
        // reset then has nothing to do and must not NPE dereferencing it.
        if (THIS == null) {
            return;
        }
        THIS.invalidateSession();
        if (THIS.net_server != null) {
            THIS.net_server.getLate_clients_warning().clear();
        }
        if (THIS.net_client != null) {
            THIS.net_client.getLate_clients_warning().clear();
        }
        THIS.setVisible(false);
        THIS.avatar_io.close();
        THIS.dispose();
        THIS = null;
    }

    public java.util.concurrent.Future runSessionCritical(Runnable callback) {
        return Helpers.threadRun(() -> SESSION_GUARD.runIfCurrent(session_generation, callback));
    }

    public boolean runSessionCriticalNow(Runnable callback) {
        return SESSION_GUARD.runIfCurrent(session_generation, callback);
    }

    public boolean isCurrentSession() {
        return SESSION_GUARD.isCurrent(session_generation);
    }

    private void invalidateSession() {
        SESSION_GUARD.invalidate(session_generation);
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

    public ConfirmationTracker getReceived_confirmations() {
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
     * Handles a right-click anywhere inside the participant list (including
     * empty space below the rows). The opened dialog does not depend on where
     * the click lands: a host always gets the mosaic of every channel, a client
     * always gets its single channel with the host.
     *
     * For now the dialog opens directly. The popup menu in
     * {@link #buildSessionIdenticonMenu()} is intentionally kept but not shown,
     * ready for when more than one per-list action is needed.
     */
    private void handleParticipantListRightClick(java.awt.event.MouseEvent evt) {
        if (!evt.isPopupTrigger()) {
            return;
        }

        openSessionIdenticon();
    }

    /**
     * Reserved: per-row right-click menu for the participant list. Currently
     * unused because a single action ("view session identicon") opens directly,
     * but kept so extra actions can be added later by showing this menu instead
     * of opening the dialog directly in
     * {@link #handleParticipantListRightClick(java.awt.event.MouseEvent)}.
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
     * per-client channel ({@link SessionIdenticonMosaicDialog}); a client gets
     * the single AES identicon of its channel with the host.
     */
    private void openSessionIdenticon() {
        SessionIdenticonMosaicDialog mosaic = SessionIdenticonMosaicDialog.buildForHost(this, this);

        if (mosaic != null) {
            mosaic.setLocationRelativeTo(this);
            mosaic.setVisible(true);
            return;
        }

        // Fetching the key BLOCKS while a reconnect is in progress, and this runs on the
        // EDT (right-click handler): the whole room would freeze, and whoever needs to
        // finish the reconnect needs that same thread to keep pumping events. Fetch off
        // the EDT and open the dialog when it returns.
        Helpers.threadRun(() -> {
            SecretKeySpec my_key = getLocal_client_aes_key();

            if (my_key == null) {
                return;
            }

            String title = server_nick != null ? local_nick + " ↔ " + server_nick : local_nick;

            Helpers.GUIRun(() -> {
                IdenticonDialog dialog = new IdenticonDialog(this, true, title, my_key);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
            });
        });
    }

    private void HTMLEditorKitAppend(String text) {

        Helpers.GUIRun(() -> {
            CoronaHTMLEditorKit editor = (CoronaHTMLEditorKit) chat.getEditorKit();
            StringReader reader = new StringReader(text);
            try {
                editor.read(reader, chat.getDocument(), chat.getDocument().getLength());
                chat.setCaretPosition(chat.getDocument().getLength());
                // Force a full repaint: the partial clip the append triggers sometimes leaves
                // RoundedBubbleView's fillRoundRect clipped. Pointless when the component isn't
                // showing (chat hidden during the game): the deferred setSize in
                // formComponentShown handles the relayout when it reopens.
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

        // Translated placeholder until the first PING arrives (the .form text is just the
        // design-time default; the real format is set by the PONG handler).
        latency_label.setText(Translator.translate("ui.latencia_servidor") + " 0 ms | 0 ms");

        SettingsUI.toggleize(chat_notifications);

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
            // Buy-in and blinds show their recovered values (same as a new game, via
            // applyGameInfoBuyinLabel on the host / GAMEINFO on the client); the "Resuming
            // previous game" indicator has its own label, in the row below.
            game_info_recover.setVisible(true);
        }

        if (server) {

            // (The "Click to refresh game data" tooltip on buyin/blinds/hands was removed:
            // it was stale — clicking those labels is a no-op now that config is edited in
            // the settings wheel's "Game" tab; see game_info_buyinMouseClicked.)
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
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM));

        kick_user.setEnabled(false);

        empezar_timba.setEnabled(false);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat_box);

        image_button.setToolTipText(Translator.translate("tooltip.send_image"));
        sound_icon.setToolTipText(Translator.translate("sound.click_para_activardesactivar_el_sonido"));

        Helpers.setScaledBlackIconLabel(settings_icon, getClass().getResource("/images/menu/gear.png"), Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM));
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

        // The chat box's avatar is decorative (no listener attached): the hand cursor
        // inherited from the .form promised a click that doesn't exist.
        avatar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

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

            if (game_info_buyin.isEnabled()) {
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

        Helpers.applyDialogZoom(this);

        Helpers.translateComponents(this, false);

        Helpers.setScaledIconButton(empezar_timba, getClass().getResource("/images/start.png"),
                Math.round(empezar_timba.getHeight() * 0.8f), Math.round(empezar_timba.getHeight() * 0.8f));

        Helpers.setScaledIconButton(kick_user, getClass().getResource("/images/kick.png"), kick_user.getHeight(),
                kick_user.getHeight());

        chat_box.setPreferredSize(new Dimension(Math.round((float) (chat_box.getSize().getWidth() * 0.5f)),
                (int) chat_box.getSize().getHeight()));

        pack();

        int w = (int) Math.min(getWidth(), Math.round(Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.9f));

        // The room is created at 70% of screen height ONLY if that 70% is greater than
        // its packed height (so it looks taller); otherwise, normal load (the packed
        // size, capped at 90%). Width stays at its default (the packed size, capped at
        // 90%). If the content doesn't fit the chosen height, main_scroll_panel covers
        // the rest.
        int packed_h = getHeight();
        int screen_h = (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight();
        // The "extra" height (70% of screen) also follows the dialog zoom: otherwise,
        // shrinking the content would leave the window at 70% of screen while the chat
        // stretched to fill it (a huge, empty area). At 100% it's 0.7 (identical to the
        // design).
        int screen_70 = Math.round(screen_h * 0.7f * Helpers.DIALOG_ZOOM);
        int h = (screen_70 > packed_h) ? screen_70 : Math.min(packed_h, Math.round(screen_h * 0.9f));

        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);

            setPreferredSize(getSize());

            pack();

            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth());

        } else {
            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth());
        }

        // The stats window is ownerless and survives a screen change: close it
        // before installing the waiting-room loops so stats_music cannot keep
        // looping on top of them. false = leave the background muted (we set up
        // our own loops right below).
        StatsDialog.disposeIfOpen(false);

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

    // True if the game has ALREADY ended for this peer (its Crupier left run() with
    // fin_de_la_transmision): auto-reconnect does not apply — the server closing its
    // socket is the normal end of the game, not a drop. NOTE: this class's exit flag is
    // set later (GameFrame.finTransmision, after logs/SQL/chat are flushed), so without
    // this check the reader detects the host's close BEFORE exit=true (a host-finishes-
    // first race, e.g. a busted host who becomes a spectator and leaves the game with a
    // single player) and fires spurious reconnects with their banners over the
    // BalanceScreen.
    private boolean timbaTerminada() {
        return isPartida_empezada() && GameFrame.getInstance() != null
                && GameFrame.getInstance().getCrupier() != null
                && GameFrame.getInstance().getCrupier().isFin_de_la_transmision();
    }

    // AUTO-RECONNECT function
    public boolean reconectarCliente() {

        net_client.setReconnecting(true);

        LOGGER.log(Level.WARNING, "Attempting to reconnect to server...");

        // PERSISTENT "reconnecting" indicator (timeout=null -> no auto-close): the toast
        // used to die after NOTIFICATION_TIMEOUT (5s) while the client kept silently
        // retrying for up to 80s, leaving the user staring at nothing for ~75s. Now it
        // stays visible for the whole auto-retry phase and closes when it resolves
        // (success, the manual dialog taking over, or exit via finally). Held in a
        // 1-slot array so it can be assigned inside GUIRun and disposed later.
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

                        // Publish the unconnected socket before connect(): definitive teardown can
                        // then close it and release a connect in progress. The bounded connect also
                        // fits inside the executor's termination window if the OS does not react to
                        // close immediately.
                        Socket newSock = new Socket();
                        net_client.setLocal_client_socket(newSock);
                        newSock.connect(new InetSocketAddress(server_address[0],
                                Integer.parseInt(server_address[1])),
                                Helpers.COOPERATIVE_SESSION_IO_TIMEOUT_MS);

                        newSock.setTcpNoDelay(true);
                        newSock.setKeepAlive(true);

                        // Anti-hang/anti-DoS lock on the RECONNECT handshake, mirroring cliente():
                        // without SO_TIMEOUT the exchange's reads (server pubkey, session_id) would
                        // block indefinitely if the server accepted the TCP connection but sent no
                        // data — and here that also holds local_client_socket_lock with
                        // reconnecting=true, freezing the ENTIRE client transport with no recovery.
                        // Reset to 0 (blocking) after the ack, once in steady state.
                        newSock.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                        LOGGER.log(Level.WARNING, "Connected to server! Exchanging keys...");

                        // Send the "magic" bytes
                        newSock.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));

                        newSock.getOutputStream().flush();

                        /* KEY EXCHANGE START */
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

                        // Defensive cap (mirrors cliente()): a malicious/corrupt length would
                        // allocate a giant byte[] -> OOM. The reconnect handshake used to skip
                        // this validation.
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

                        /* KEY EXCHANGE END */
                        // Send our nick to the server, authenticated with the old HMAC key
                        LOGGER.log(Level.WARNING, "Sending reconnection data...");

                        newSock.getOutputStream().write(
                                (Helpers.encryptCommand(b64_nick + "#" + AboutDialog.VERSION + "#*#*#" + b64_hmac_nick,
                                        net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()) + "\n").getBytes("UTF-8"));

                        newSock.getOutputStream().flush();

                        net_client.setLocal_client_buffer_read_is(new BufferedInputStream(newSock.getInputStream()));

                        // Wait for an explicit server ack: RECONNECT_OK accepts, any
                        // RECONNECT_DENIED#<reason> or a clean socket close means the server did
                        // NOT accept us as a reconnect. Without this ack the client marked
                        // ok_rec=true just because the crypto handshake finished without an
                        // exception; when the server closed the socket immediately (the "DENIED"
                        // branch — typical after a server halt-and-recover where our nick is no
                        // longer in participantes), the reader read null right away and looped
                        // back into reconectarCliente() with no pause (the 5s pause only applies
                        // when ok_rec=false). Each iteration created a new Socket and a full ECDH
                        // key exchange — UI freeze and 100% CPU, reported by yxmgl in issue#9 20.59.
                        //
                        // We use a bounded SO_TIMEOUT so we don't wait forever for an ack that
                        // will never arrive (very slow server, or a silently dead socket). The
                        // finally block restores the timeout to 0 (unbounded blocking) so later
                        // reads from runSocketReaderClientThread stay normal blocking reads.
                        String ackLine;
                        try {
                            newSock.setSoTimeout(GameFrame.CLIENT_RECEPTION_TIMEOUT);
                            // Defensive cap, same as the rest of the transport's readers
                            // (NetServer/NetClient/Participant): if a server sent bytes without a
                            // '\n' after the reconnect handshake, an uncapped readLine would eat
                            // memory until OOM. The SoTimeout guard covers hangs but NOT OOM from
                            // long lines.
                            //
                            // The ack (RECONNECT_OK/DENIED) is a TEXT frame. If the server relayed a
                            // BINARY frame (voice note/avatar) right before the ack, ackFrame.text()
                            // would be null and a live reconnect would be treated as failed. We skip
                            // binaries (best-effort side channel, same as the normal reader) and keep
                            // reading until the text frame (the ack) or null (dead socket). SO_TIMEOUT
                            // bounds each read.
                            //
                            // We skip up to 8 binary frames (voice/avatar side channel) before the
                            // TEXT ack. BOUNDED: SO_TIMEOUT only limits each individual read (idle
                            // gap), NOT the total; without a cap, a host streaming binaries (zero-trust
                            // model) would hang this loop WHILE holding local_client_socket_lock with
                            // reconnecting=true -> frozen client transport. After 8 binaries in a row
                            // with no text ack, we treat it as failed.
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
                            // Steady state: the reader does blocking (unbounded) reads. This used to
                            // restore oldTimeout (==0 on a new socket); now the socket arrives here
                            // with HANDSHAKE_TIMEOUT_MS set (the handshake lock), so 0 must be set
                            // explicitly. On the failure branch the socket is closed afterward, so the
                            // value there doesn't matter.
                            try {
                                newSock.setSoTimeout(0);
                            } catch (Exception ignored) {
                            }
                        }

                        if (ackLine == null) {
                            throw new IOException("Server closed socket without sending reconnect ack");
                        }

                        // The ack MUST be ENCRYPTED. decryptCommand already rejects unencrypted
                        // frames, except the keepalive, which travels in the clear by design; this
                        // guard is stricter and doesn't allow even that here, because a reconnect
                        // ack can only ever be an authenticated frame.
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

                        // Reset the client's defensive PING/PONG counters: if they were
                        // carrying failures against the old socket, the first failure against
                        // the new one (could be legitimate post-reconnect jitter) must not
                        // reach the threshold or close the freshly installed socket.
                        // Equivalent to the reset Participant.resetSocket does on the server.
                        net_client.setReset_ping_counters(true);

                        ok_rec = true;

                    } catch (Exception ex) {

                        LOGGER.log(Level.SEVERE, "Reconnection socket threw an exception");
                        LOGGER.log(Level.SEVERE, null, ex);

                    } finally {

                        if (!ok_rec) {

                            // (There used to be a red "could not reconnect" toast here on every
                            // failed attempt: besides being alarming, its single slot
                            // (LATEST_NOTIFICATION) hid the persistent "reconnecting" indicator, and
                            // once it auto-closed after 5s, the silent gap came back. The persistent
                            // indicator already communicates that we're still trying.)
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

                                    // The manual dialog (modal, with its own progress bar) takes over
                                    // as the indicator: close the persistent toast so it doesn't hang
                                    // around behind the modal.
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

                                } else if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT
                                        && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    // In the WAITING ROOM (game not started yet) the reconnect loop
                                    // had no terminal state: the manual dialog is gated on
                                    // isPartida_empezada (above), and here there's no game state to
                                    // preserve, nor does that dialog make sense (its buttons assume a
                                    // live GameFrame) — so a permanently dead host spun the client
                                    // forever. After the same deadline used in-game we give up
                                    // cleanly: exit breaks the do-while, the reader queues the close
                                    // signal, and the consumer returns to the menu the normal way
                                    // (dispose + Init.VENTANA_INICIO), no System.exit. The persistent
                                    // toast is closed by this method's finally block.
                                    exit = true;

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
                    // Telemetry: client's successful-reconnection counter. Incremented ONLY on
                    // the success branch (the failure branch never enters this if). Mirrors
                    // Participant.reconnection_count on the server side.
                    net_client.incrementReconnectionCount();

                    // If the client's defensive ping died from the missed-PONG threshold
                    // (closeClientSocket+break), reconnecting does NOT restart it on its own:
                    // without this the reconnected client is left with no active keepalive (a
                    // new socket that goes mute would only be caught by a write failure, or
                    // never). We resurrect it after a successful reconnect if it died; !exit so
                    // it isn't started during teardown. (Analogous to the host's resurrection in
                    // resetSocket.)
                    if (!exit && !net_client.isPingPongThreadAlive()) {
                        LOGGER.log(Level.INFO, "Client runPingPongThreadCliente was dead after reconnect — resurrecting");
                        runPingPongThreadCliente();
                    }

                    // Resume a stats DB sync that the drop may have cut short: re-send our
                    // manifest so the host pushes whatever did not make it across before the
                    // socket died. Best-effort, background, and idempotent (imports dedup by
                    // ugi; no-op if both sync prefs are off) — same call as the initial
                    // connect. Its socket write waits on reconnecting=false (cleared in the
                    // finally below) since it is offloaded to a background thread.
                    if (!exit) {
                        statsSyncOnConnectedToServer();
                    }

                    if (GameFrame.conexionSonidoOn()) {
                        Audio.playWavResource("misc/yahoo.wav");
                    }

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
                // CRITICAL: clearing reconnecting + waking up waiters MUST happen in the
                // finally block. reconnecting is the flag that blocks ALL of the client's
                // writes/reads and key getters (NetClient + getLocal_client_*_key) via
                // while(reconnecting) wait(); if an exception between the end of the loop and
                // this point (e.g. yahoo.wav throwing, or an NPE evaluating the loop condition
                // during teardown) left it true, the client transport would hang FOREVER. The
                // notifyAll still runs inside synchronized(getLocalClientSocketLock()).
                net_client.setReconnecting(false);
                getLocalClientSocketLock().notifyAll();

                // Close the persistent indicator on ANY exit (success, exception, or the loop
                // ending via exit/game-over). On success the green toast has already replaced
                // it visually; this also prevents a magenta toast from being left hanging after
                // an exception or an exit.
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

    /**
     * Parses a base64 CSV (DUALLOCK_BUNDLE format) into a list of byte[].
     */
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

                    String[] partes_comando = mensaje_recibido.split("#", -1);

                    // CLEAN exit started by the host: game over or "stop game" with force_recover
                    // arrive as GAME#<id>#SERVEREXIT[RECOVER]; cancelling the game (EXIT) and
                    // kicking this client (KICKED) arrive as top-level commands. In all four cases
                    // the host closes the socket without waiting for an ACK, so we flag this
                    // BEFORE the null-read so it doesn't mistake it for a network drop and
                    // reconnect: the consumer already has the frame queued and will do the
                    // ordered shutdown.
                    if (("GAME".equals(partes_comando[0])
                            && TableTerminationWire.isValidTerminationFrame(mensaje_recibido))
                            || "KICKED".equals(partes_comando[0]) || "EXIT".equals(partes_comando[0])) {
                        server_graceful_exit = true;
                    }

                    if (null == partes_comando[0]) {

                        net_client.encolarLeido(mensaje_recibido);
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
                                net_client.encolarLeido(mensaje_recibido);
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
                                net_client.encolarLeido(mensaje_recibido);
                                break;
                        }
                    }

                } else {
                    if (!net_client.getLocal_client_socket_reader_queue().contains(POISON_PILL)) {
                        net_client.encolarSenalCierre();
                    }

                    // The anti-replay table is NOT cleared here. A reconnect follows right after
                    // this, and the first thing the host does when it comes back is resend
                    // whatever was left unconfirmed WITH THE SAME identifier: clearing it would
                    // let those already-applied commands through a second time. This is exactly
                    // the moment it's needed for. Its growth is bounded the same way as on the
                    // host side, by the cap on distinct names.
                }

                if (mensaje_recibido == null) {
                    if (!exit && !server_graceful_exit && !timbaTerminada() && ((WaitingRoomFrame.getInstance() != null && !isPartida_empezada())
                            || (GameFrame.getInstance() != null && !GameFrame.getInstance().getLocalPlayer().isExit()))) {

                        if (!reconectarCliente()) {
                            exit = true;
                        }
                    } else {
                        // If we were already exiting or reconnection doesn't apply, kill the thread
                        exit = true;
                    }
                    // If we're giving up (exit), wake the consumer blocked on take(): the single
                    // POISON_PILL from the null-read was already consumed during the long
                    // reconnect attempt, so without this pill the consumer thread would hang
                    // (a teardown zombie).
                    if (exit) {
                        net_client.encolarSenalCierre();
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
            int ping_write_stall_counter = 0;

            try {
                while (!exit && WaitingRoomFrame.getInstance() != null) {

                    // If reconectarCliente completed a reconnect during the last cycle, the
                    // counters accumulated against the old socket no longer apply. Reset before
                    // sending the first PING on the new socket.
                    if (net_client.isReset_ping_counters()) {
                        consecutive_ping_failures = 0;
                        ping_write_stall_counter = 0;
                        net_client.setReset_ping_counters(false);
                    }

                    int ping = Helpers.CSPRNG_GENERATOR.nextInt();

                    net_client.setRemote_server_pong(null);
                    net_client.setRemote_server_pong2(null);
                    net_client.setRemote_server_latency(-1);
                    net_client.setRemote_server_latency2(-1);

                    long pingStartNs = System.nanoTime();

                    // The PING write goes to a pool thread with a deadline, like the host's twin
                    // (Participant.runPingPongThread). writeCommandToServer is SYNCHRONOUS and holds
                    // local_client_socket_lock during the os.write, so if the server stops reading,
                    // that write hangs the heartbeat thread forever and, with the lock held, every
                    // send and the defensive close itself too. After the deadline is missed several
                    // times in a row, the socket is closed so the reader detects the null and starts
                    // reconnection. The socket is captured BEFORE and closed by direct reference
                    // (closeStalledSocket), because closeClientSocket would take the lock the stuck
                    // write is holding; it's only closed if it's still the live socket, in case a
                    // reconnect swapped it out meanwhile.
                    java.net.Socket ping_socket = net_client.getLocal_client_socket();
                    java.util.concurrent.Future<?> ping_write;
                    try {
                        ping_write = Helpers.THREAD_POOL.submit(() -> writeCommandToServer("PING#" + ping));
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE,
                                "Error dispatching PING", ex);
                        break;
                    }
                    try {
                        ping_write.get(WaitingRoomFrame.PING_WRITE_STALL_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS);
                        ping_write_stall_counter = 0;
                    } catch (java.util.concurrent.TimeoutException ex) {
                        if (!exit && !net_client.isReconnecting()
                                && ping_socket != null && ping_socket == net_client.getLocal_client_socket()
                                && ++ping_write_stall_counter >= MAX_CONSECUTIVE_PING_FAILURES) {
                            LOGGER.log(Level.SEVERE,
                                    "PING write to server stalled {0} times in a row ({1} ms each) — server not reading; closing socket to force reconnect",
                                    new Object[]{ping_write_stall_counter, WaitingRoomFrame.PING_WRITE_STALL_TIMEOUT});
                            net_client.setPingPongThreadAlive(false);
                            net_client.closeStalledSocket(ping_socket);
                            break;
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE,
                                "Error dispatching PING", ex);
                        break;
                    }

                    long end = System.currentTimeMillis() + WaitingRoomFrame.PING_PONG_TIMEOUT;

                    while (!exit && (net_client.getRemote_server_pong() == null || net_client.getRemote_server_pong2() == null)
                            && System.currentTimeMillis() < end) {
                        synchronized (ping_pong_lock) {
                            // Re-check inside the monitor (same as the Participant's
                            // runPingPongThread): closes the PONG missed-notify race and avoids
                            // wait(0)/wait(<0) in the remaining-time race window.
                            long remaining = end - System.currentTimeMillis();
                            if ((net_client.getRemote_server_pong() == null || net_client.getRemote_server_pong2() == null) && remaining > 0) {
                                try {
                                    ping_pong_lock.wait(remaining);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                    return;
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

                        // Safety net: if the socket is mute (PONGs missed N rounds in a row) we
                        // close locally and let runSocketReaderClientThread detect the null read
                        // and start reconectarCliente. The primary path is still the IOException on
                        // write (NetClient.writeCommand).
                        if (round_ok) {
                            consecutive_ping_failures = 0;
                        } else {
                            consecutive_ping_failures++;
                            if (consecutive_ping_failures >= MAX_CONSECUTIVE_PING_FAILURES) {
                                LOGGER.log(Level.WARNING,
                                        "Client lost {0} consecutive PONGs — closing socket to force reconnect",
                                        consecutive_ping_failures);
                                // alive=false BEFORE closing: this way reconectarCliente sees the
                                // thread as dead and resurrects it. Without this, in the break->finally
                                // window the resurrection check saw alive=true and never relaunched it.
                                net_client.setPingPongThreadAlive(false);
                                closeClientSocket();
                                break;
                            }
                        }

                        // Telemetry: also update the client's LocalPlayer LatencyDot with its own
                        // measurement to the server + its reconnection count. This gives IMMEDIATE
                        // feedback (doesn't wait for the host's TELEMETRY broadcast) on local link
                        // quality.
                        try {
                            if (GameFrame.getInstance() != null
                                    && GameFrame.getInstance().getLocalPlayer() instanceof LocalPlayer) {
                                ((LocalPlayer) GameFrame.getInstance().getLocalPlayer()).applyTelemetry(
                                        net_client.getRemote_server_latency(),
                                        net_client.getRemote_server_latency2(),
                                        net_client.getReconnectionCount());
                            }
                        } catch (Exception ex) {
                            // Best-effort visualization; does not affect game logic.
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
                    // Anti-DoS lock: if the server does NOT finish the handshake within
                    // HANDSHAKE_TIMEOUT_MS, the blocked reads (server pubkey, session_id,
                    // NICKOK/intro/chat history) throw SocketTimeoutException and we fall into
                    // the catch instead of starving the thread forever. RESET to 0 further down
                    // once NICKOK has been processed and nuevoParticipante created.
                    sock.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

                    sock.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));
                    sock.getOutputStream().flush();

                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("status.intercambio_claves"));
                    });

                    /* CLEAN KEY EXCHANGE START */
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
                    /* KEY EXCHANGE END */

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
                        case "NICKUNAUTHORIZED":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("conn.nick_unauthorized"));
                            break;
                        case "NICKOK":
                            if ("0".equals(partes[1])) {
                                Helpers.GUIRun(() -> {
                                    pass_icon.setVisible(false);
                                });
                            }

                            gameinfo_original = new String(Base64.getDecoder().decode(partes[2].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8");

                            // NICKOK's fourth (optional) field: FULL config mirror for the
                            // read-only Game tab. Only stored (GameFrame.* is NOT written here);
                            // the real config arrives in the INIT at startup.
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

                            String server_avatar_encoded = partes.length > 1 ? partes[1] : "*";

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

                            // Keep the bounded avatar decoder and the deliberately
                            // non-blocking TOFU policy below.
                            File server_avatar = decodeRemoteAvatar(
                                    server_avatar_encoded, server_nick, "server intro");
                            nuevoParticipanteRemoto(server_nick, server_avatar, null, null, null, false,
                                    THIS.isUnsecure_server());
                            nuevoParticipante(local_nick, local_avatar, null, null, null, false, false);

                            // Handshake complete: the client's subsequent reads (GAME, PING/PONG,
                            // chat) must be able to wait indefinitely. Clear the handshake
                            // deadline. Reconnection manages its own timeout in its own flow.
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
                                    String[] partes_comando = recibido.split("#", -1);

                                    // A single malformed/unprocessable frame (missing segment,
                                    // bad number, etc.) must NOT tear down the whole game session
                                    // via the outer catch: log it and skip to the next frame, like
                                    // the host does per-command. The switch body keeps its original
                                    // indentation on purpose to keep this a minimal, merge-safe diff.
                                    try {
                                        switch (partes_comando[0]) {
                                            case "PING":
                                                // Same safety net as its twin in the in-game loop: a heartbeat
                                                // missing its counter, or with a non-numeric one, no longer
                                                // takes down the client sitting in the waiting room.
                                                if (partes_comando.length >= 2) {
                                                    try {
                                                        writeCommandToServer("PONG2#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 2));
                                                    } catch (NumberFormatException nfe) {
                                                    }
                                                }
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

                                            case "NEWPASS":
                                                // The host changed the room password (kicking someone, changing
                                                // it by hand, generating a strong one, or clearing it) and sends
                                                // us the new one. Without this we'd be stuck with the old one,
                                                // and since the channel is derived from it, a network drop would
                                                // mean we could never rejoin. The "*" sentinel means "no password
                                                // anymore", which also changes how the channel is derived.
                                                // Arrives over the channel already encrypted with this session's
                                                // keys, which don't depend on the password changing.
                                                if (partes_comando.length > 1) {
                                                    try {
                                                        password = "*".equals(partes_comando[1])
                                                                ? null
                                                                : new String(Base64.getDecoder().decode(partes_comando[1]), "UTF-8");
                                                    } catch (Exception ex) {
                                                        LOGGER.log(Level.WARNING, "Could not read the new room password", ex);
                                                    }
                                                }
                                                break;

                                            case "GAME":
                                                if (partes_comando.length < 3) {
                                                    LOGGER.log(Level.SEVERE, "Malformed GAME frame from host; closing connection");
                                                    exit = true;
                                                    closeClientSocket();
                                                    break;
                                                }
                                                String subcomando = partes_comando[2];
                                                final int id;
                                                try {
                                                    id = Integer.parseInt(partes_comando[1]);
                                                } catch (NumberFormatException ex) {
                                                    LOGGER.log(Level.SEVERE, "Invalid GAME id from host; closing connection", ex);
                                                    exit = true;
                                                    closeClientSocket();
                                                    break;
                                                }
                                                GameCommandGate.Decision gateDecision
                                                        = net_client.getGameCommandGate().accept(subcomando, id, recibido);
                                                if (gateDecision.closeConnection()) {
                                                    LOGGER.log(Level.SEVERE,
                                                            "Unknown GAME subcommand or conflicting GAME id/frame {0} from host; closing connection",
                                                            subcomando);
                                                    exit = true;
                                                    closeClientSocket();
                                                    break;
                                                }

                                                try {
                                                    String confMsg = "CONF#" + String.valueOf(id + 1) + "#OK";
                                                    this.writeCommandToServer(Helpers.encryptCommand(confMsg, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                } catch (Exception e) {
                                                }

                                                if (gateDecision.enqueue()) {
                                                    if (isPartida_empezada()) {
                                                        switch (subcomando) {
                                                            case "DECK_CASCADE_REQ":
                                                                final String[] partes_cascade = partes_comando;
                                                                Helpers.threadRun(() -> {
                                                                    try {
                                                                        // ZERO-TRUST: if we've already caught this host cheating this
                                                                        // session, never generate a key for it again. The zero-trust
                                                                        // promise ("once we detect cheating, we hand out no more keys")
                                                                        // only holds if the lockdown is a hard gate, not just a popup.
                                                                        if (Crupier.SECURITY_LOCKDOWN) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ refused — security lockdown active");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // ZERO-TRUST: refuse cascade mid-hand. If we already have an
                                                                        // active MEGAPACKET, accepting a new cascade would overwrite our
                                                                        // sra_unlock and destroy the hand in progress. An honest host
                                                                        // NEVER requests DECK_CASCADE_REQ after the MEGAPACKET until
                                                                        // NUEVA_MANO (which clears local_mega_packet to null).
                                                                        Crupier crupierCheck = GameFrame.getInstance().getCrupier();
                                                                        if (crupierCheck != null && crupierCheck.hasMegaPacket()) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ received mid-hand (MEGAPACKET already locked) — refusing to overwrite my sra_unlock");
                                                                            crupierCheck.triggerSecurityLockdown(Translator.translate("zero_trust.host_cascade_mid_hand"));
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }

                                                                        // ZERO-TRUST: wire with fewer fields than we read (partes_cascade[3])
                                                                        // -> reject cleanly instead of AIOOBE, same as sibling DECK_ROTATION_REQ.
                                                                        if (partes_cascade.length != 4) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ malformed wire (parts={0}) — refusing", partes_cascade.length);
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }

                                                                        byte[] incomingDeck = Base64.getDecoder().decode(partes_cascade[3]);

                                                                        // Dual-lock (Option G): the client needs the Crupier to store the
                                                                        // community lock it will apply during the rotation phase. If for
                                                                        // some reason the Crupier doesn't exist yet, refuse — a sane host
                                                                        // never sends DECK_CASCADE_REQ before the client has a Crupier.
                                                                        if (crupierCheck == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ received before Crupier exists — refusing");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        crupierCheck.acceptCascadeRequestOnce();

                                                                        // ZERO-TRUST: the host asks us to apply our lock to the deck it
                                                                        // sends. If the deck isn't 52 valid Curve25519 points, it's garbage
                                                                        // (host downgrade: sending us invalid bytes so we waste our
                                                                        // shuffle/lock on unrecoverable data, or smuggling). Reject before
                                                                        // committing our freshly generated sra_unlock. decodeDeck validates
                                                                        // (exactly 52 points, each on-curve/canonical) in a SINGLE decode and
                                                                        // hands us the points (incomingPoints) to reuse for the lock. Kept
                                                                        // HERE, before generating/storing the scalars, to reject a garbage
                                                                        // deck without committing our freshly generated sra_unlock.
                                                                        com.tonikelope.coronapoker.crypto.EdwardsPoint[] incomingPoints
                                                                                = (incomingDeck != null && incomingDeck.length == 1664)
                                                                                        ? com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(incomingDeck) : null;
                                                                        if (incomingPoints == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_CASCADE_REQ payload is not a valid 52-point curve deck (len={0}) — refusing",
                                                                                    incomingDeck == null ? -1 : incomingDeck.length);
                                                                            crupierCheck.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }

                                                                        byte[] lockScalar = RistrettoSRA.generateLockScalar();
                                                                        byte[] unlockScalar = RistrettoSRA.getUnlockScalar(lockScalar);
                                                                        this.participantes.get(local_nick).setSra_unlock(unlockScalar);

                                                                        // Dual-lock (Option G): second pair of scalars for the community
                                                                        // pieces rotation that will come after the cascade. Stored on the
                                                                        // Crupier so the DECK_ROTATION_REQ handler can retrieve them without
                                                                        // needing to request fresh entropy at that point.
                                                                        byte[] communityLockScalar = RistrettoSRA.generateLockScalar();
                                                                        byte[] communityUnlockScalar = RistrettoSRA.getUnlockScalar(communityLockScalar);
                                                                        crupierCheck.local_sra_lock_community = communityLockScalar;
                                                                        crupierCheck.local_sra_unlock_community = communityUnlockScalar;
                                                                        this.participantes.get(local_nick).setSra_unlock_community(communityUnlockScalar);
                                                                        // Lock over the points already decoded and validated above (incomingPoints):
                                                                        // bytes identical to applyCommutativeLock(incomingDeck, lockScalar), no re-decoding.
                                                                        byte[] locked = com.tonikelope.coronapoker.crypto.ShuffleCascade.encodeDeck(
                                                                                RistrettoSRA.lockPoints(incomingPoints, lockScalar));

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

                                                                        // (last-mile lockdown re-check removed — see the equivalent note
                                                                        // in REQ_SRA_UNLOCK. The gate at the top of the handler already
                                                                        // stops new requests from being processed post-lockdown. Keeping
                                                                        // it here left the host hanging indefinitely when a concurrent
                                                                        // duplicate triggered lockdown while the legitimate request was
                                                                        // being processed.)
                                                                        String b64Deck = Base64.getEncoder().encodeToString(shuffled);
                                                                        String myNickB64 = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));

                                                                        int respId = GameCommandId.next();
                                                                        // Send the K=k*B commitments (pocket and community) along with the
                                                                        // cascaded deck, so the host can aggregate them and anchor them in H_0.
                                                                        String kPocketB64 = Base64.getEncoder().encodeToString(RistrettoSRA.commitment(lockScalar));
                                                                        String kCommunityB64 = Base64.getEncoder().encodeToString(RistrettoSRA.commitment(communityLockScalar));
                                                                        // B1: send the RESP with the deck + commitments RIGHT AWAY (without
                                                                        // the proof), so the host does NOT wait for the prove step (132/377/8900
                                                                        // ms) INSIDE the deal. The shuffle proof (deckOut = shuffle(k·deckIn))
                                                                        // travels separately, ASYNC, in a DECK_CASCADE_PROOF matched by
                                                                        // hash(deckOut) (see Crupier.collectAsyncCascadeProofs). The host appends
                                                                        // it to the chain that EVERYONE verifies, so a modified host cannot slip
                                                                        // a card in. Failure to generate or send it closes the channel below;
                                                                        // there is no proofless fallback.
                                                                        writeCommandToServer(Helpers.encryptCommand("GAME#" + respId + "#DECK_CASCADE_RESP#" + myNickB64 + "#" + b64Deck + "#" + kPocketB64 + "#" + kCommunityB64, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                        try {
                                                                            int myPermN = incomingDeck.length / 32;
                                                                            int[] myPerm = DeterministicShuffle.shufflePermutation(myPermN, mySeed);
                                                                            byte[] cascadeProof = com.tonikelope.coronapoker.crypto.ShuffleCascade
                                                                                    .proveStepWire(incomingDeck, shuffled, myPerm, lockScalar);
                                                                            if (cascadeProof == null) {
                                                                                LOGGER.log(Level.SEVERE, "Critical cascade proof generation returned null; closing host channel");
                                                                                closeCriticalHostChannel();
                                                                                return;
                                                                            }
                                                                            String deckHashB64 = Base64.getEncoder().encodeToString(
                                                                                    java.security.MessageDigest.getInstance("SHA-256").digest(shuffled));
                                                                            int proofId = GameCommandId.next();
                                                                            writeCommandToServer(Helpers.encryptCommand("GAME#" + proofId + "#DECK_CASCADE_PROOF#" + myNickB64 + "#" + deckHashB64 + "#" + Base64.getEncoder().encodeToString(cascadeProof), net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                        } catch (Exception proofEx) {
                                                                            LOGGER.log(Level.SEVERE, "Failed to generate/send critical cascade proof; closing host channel", proofEx);
                                                                            closeCriticalHostChannel();
                                                                        }
                                                                    } catch (Exception e) {
                                                                        LOGGER.log(Level.SEVERE, "Failed to process critical DECK_CASCADE_REQ; closing host channel", e);
                                                                        closeCriticalHostChannel();
                                                                    }
                                                                });
                                                                break;

                                                            case "DECK_ROTATION_REQ":
                                                                // Dual-lock (Option G): after the main cascade, the host asks each peer
                                                                // in order to apply uPocket (remove its pocket lock) + kCommunity (add
                                                                // its community lock) to the community pieces. Result: the community
                                                                // pieces end up encrypted ONLY with community scalars, and their unlock
                                                                // is delivered later, separately from the pocket unlock.
                                                                final String[] partes_rotation = partes_comando;
                                                                Helpers.threadRun(() -> {
                                                                    try {
                                                                        if (Crupier.SECURITY_LOCKDOWN) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ refused — security lockdown active");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        Crupier crupierRot = GameFrame.getInstance().getCrupier();
                                                                        if (crupierRot == null
                                                                                || crupierRot.local_sra_lock_community == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ without community lock (Crupier or local_sra_lock_community null) — refusing");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        try {
                                                                            crupierRot.acceptRotationRequestOnce();
                                                                        } catch (IllegalArgumentException replay) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ replay — closing pocket-unlock oracle", replay);
                                                                            crupierRot.warnSuspiciousHost(Translator.translate("zero_trust.host_rotation_replay"));
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // The client's pocket unlock lives on the local Participant (the
                                                                        // cascade handler stores it there, not on the Crupier — the pocket
                                                                        // half isn't "published" to the Crupier until the MEGAPACKET arrives
                                                                        // and the client copies it from Participant). For the rotation we
                                                                        // need uPocket (remove our pocket lock) + kCommunity (add our
                                                                        // community lock), so we read uPocket straight from Participant.
                                                                        byte[] myPocketUnlock = this.participantes.get(local_nick).getSra_unlock();
                                                                        if (myPocketUnlock == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ without local pocket unlock (Participant.sra_unlock null) — refusing");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        if (partes_rotation.length != 4) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ malformed wire (parts={0}) — refusing", partes_rotation.length);
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        byte[] incomingPieces = Base64.getDecoder().decode(partes_rotation[3]);
                                                                        // ZERO-TRUST: decodeDeck validates in a SINGLE decode that the
                                                                        // payload is a multiple of 32 bytes and each point is on-curve /
                                                                        // canonical (null otherwise); we reuse those points (inR) for the
                                                                        // lock and the proof without re-decoding. The exact length depends
                                                                        // on the host's ring; we don't re-derive it, but a non-curve payload
                                                                        // is always rejected.
                                                                        com.tonikelope.coronapoker.crypto.EdwardsPoint[] inR
                                                                                = com.tonikelope.coronapoker.crypto.ShuffleCascade.decodeDeck(incomingPieces);
                                                                        if (inR == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: DECK_ROTATION_REQ payload not a valid curve-point block (len={0}) — refusing",
                                                                                    incomingPieces == null ? -1 : incomingPieces.length);
                                                                            crupierRot.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // Rotation in ONE lock: uPocket then kCommunity = multiply by
                                                                        // s = uPocket*kCommunity (mod L). We work on the points already
                                                                        // decoded (inR); the result stays on-curve and its bytes are
                                                                        // identical to applyCommutativeLock. The same s signs the proof.
                                                                        java.math.BigInteger sRot = RistrettoSRA.bytesToScalar(myPocketUnlock)
                                                                                .multiply(RistrettoSRA.bytesToScalar(crupierRot.local_sra_lock_community))
                                                                                .mod(com.tonikelope.coronapoker.crypto.EdwardsPoint.L);
                                                                        com.tonikelope.coronapoker.crypto.EdwardsPoint[] outR
                                                                                = RistrettoSRA.lockPoints(inR, RistrettoSRA.scalarToBytes(sRot));
                                                                        byte[] rotated = com.tonikelope.coronapoker.crypto.ShuffleCascade.encodeDeck(outR);
                                                                        // Closing the rotation flank: proves our step is an honest in-place
                                                                        // re-key (out[i]=s*in[i], s=uPocket*kCommunity), with no relocation or
                                                                        // duplication. The host appends it to the bundle so everyone can
                                                                        // verify the genesis->MEGAPACKET chain.
                                                                        String rotProofB64;
                                                                        try {
                                                                            byte[] rp = com.tonikelope.coronapoker.crypto.DualLockWire.encodeRotationProof(
                                                                                    com.tonikelope.coronapoker.crypto.RotationProof.prove(sRot, inR, outR));
                                                                            if (rp == null) {
                                                                                LOGGER.log(Level.SEVERE, "Critical rotation proof generation returned null; closing host channel");
                                                                                closeCriticalHostChannel();
                                                                                return;
                                                                            }
                                                                            rotProofB64 = Base64.getEncoder().encodeToString(rp);
                                                                        } catch (Exception rotProofEx) {
                                                                            LOGGER.log(Level.SEVERE, "Failed to generate critical rotation proof; closing host channel", rotProofEx);
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }

                                                                        String b64Rot = Base64.getEncoder().encodeToString(rotated);
                                                                        String myNickB64Rot = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));
                                                                        int respIdRot = GameCommandId.next();
                                                                        writeCommandToServer(Helpers.encryptCommand("GAME#" + respIdRot + "#DECK_ROTATION_RESP#" + myNickB64Rot + "#" + b64Rot + "#" + rotProofB64, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                    } catch (Exception e) {
                                                                        LOGGER.log(Level.SEVERE, "Failed to process critical DECK_ROTATION_REQ; closing host channel", e);
                                                                        closeCriticalHostChannel();
                                                                    }
                                                                });
                                                                break;

                                                            case "DUALLOCK_BUNDLE":
                                                                // Each peer verifies ON ITS OWN that the deal is an honest
                                                                // shuffle+rotation genesis->MEGAPACKET. pocketCount is derived
                                                                // LOCALLY (active_crypto_ring.length*2), NEVER from the host, and the
                                                                // genesis is recomputed. Any rejected bundle closes the critical
                                                                // channel; no hand may continue with a missing verification job.
                                                                final String[] partes_bundle = partes_comando;
                                                                Helpers.threadRun(() -> {
                                                                    Crupier cruB = GameFrame.getInstance().getCrupier();
                                                                    if (cruB == null || cruB.local_mega_packet == null || cruB.active_crypto_ring == null) {
                                                                        LOGGER.log(Level.SEVERE, "DUALLOCK_BUNDLE arrived without installed MEGAPACKET; closing host channel");
                                                                        closeCriticalHostChannel();
                                                                        return;
                                                                    }
                                                                    try {
                                                                        cruB.acceptDualLockBundleOnce();
                                                                    } catch (IllegalArgumentException duplicateBundle) {
                                                                        LOGGER.log(Level.SEVERE, "Duplicate critical DUALLOCK_BUNDLE; closing host channel", duplicateBundle);
                                                                        closeCriticalHostChannel();
                                                                        return;
                                                                    }
                                                                    // A bundle for this deck ARRIVED from the host: mark it before
                                                                    // parsing/verifying. This distinguishes, on receipt, a slow peer
                                                                    // (received, queue pending -> benign) from a host that never sends the
                                                                    // proof (received != live deck -> warn the table). Even if it arrives
                                                                    // malformed/unparseable, the host sent SOMETHING -> counts as received
                                                                    // (those cases already trigger their own warnSuspiciousHost live below).
                                                                    cruB.dual_lock_bundle_received_for = cruB.local_mega_packet;
                                                                    // A bundle that was RECEIVED but malformed (AES+HMAC channel -> came
                                                                    // from the host intact) is anomalous: an honest host always sends 7
                                                                    // valid fields.
                                                                    if (partes_bundle.length != 7) {
                                                                        LOGGER.log(Level.SEVERE, "DUALLOCK_BUNDLE malformed (fields={0}) — closing host channel", partes_bundle.length);
                                                                        cruB.markShuffleProofFailed(cruB.local_mega_packet);
                                                                        cruB.triggerSecurityLockdown(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                        closeCriticalHostChannel();
                                                                        return;
                                                                    }
                                                                    try {
                                                                        // Immutable SNAPSHOT of THIS deck+bundle, queued serially. The
                                                                        // verify runs against this snapshot, NOT against the live
                                                                        // local_mega_packet: a new hand can no longer clobber this
                                                                        // verification, and a slow team still finishes it even if the hand
                                                                        // has moved on (catching a past smuggle). The verdict comes back
                                                                        // through the Sink (see Crupier).
                                                                        byte[] genesisB = Crupier.contextBoundShuffleGenesis(
                                                                                GameFrame.UGI, cruB.current_hand_id,
                                                                                cruB.active_crypto_ring);
                                                                        int pocketCount = cruB.active_crypto_ring.length * 2; // PEER-DERIVED
                                                                        ShuffleVerificationQueue.Job job = new ShuffleVerificationQueue.Job(
                                                                                genesisB, csvToBytes(partes_bundle[3]), csvToBytes(partes_bundle[4]),
                                                                                pocketCount, cruB.local_mega_packet,
                                                                                csvToBytes(partes_bundle[5]), csvToBytes(partes_bundle[6]),
                                                                                cruB.getMano());
                                                                        if (!cruB.getShuffleVerifyQueue().enqueue(job)) {
                                                                            cruB.markShuffleProofFailed(cruB.local_mega_packet);
                                                                            cruB.triggerSecurityLockdown(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                            closeCriticalHostChannel();
                                                                        }
                                                                    } catch (Exception bundleEx) {
                                                                        // Malformation is not by itself proof of cheating, but there is no
                                                                        // alternate current wire and the critical hand cannot continue.
                                                                        LOGGER.log(Level.SEVERE, "DUALLOCK_BUNDLE unparseable — closing host channel", bundleEx);
                                                                        cruB.markShuffleProofFailed(cruB.local_mega_packet);
                                                                        cruB.triggerSecurityLockdown(Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                        closeCriticalHostChannel();
                                                                    }
                                                                });
                                                                break;

                                                            case "REQ_SRA_UNLOCK_CHAIN":
                                                                // VERIFIABLE unlock batch. For each point, the host sends the
                                                                // DealChain of previous peers; this peer verifies it against ITS
                                                                // committed MEGAPACKET and, if valid, applies its unlock with a DLEQ
                                                                // proof and extends the chain. The host never sends it the point to
                                                                // decrypt (only offset + proofs), so blinding is impossible.
                                                                final String[] partes_chain = partes_comando;
                                                                Helpers.threadRun(() -> {
                                                                    try {
                                                                        if (Crupier.SECURITY_LOCKDOWN) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN refused — security lockdown active");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        if (partes_chain.length < 6) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN malformed wire (parts={0}) — refusing", partes_chain.length);
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        int phase;
                                                                        int hand_id;
                                                                        try {
                                                                            phase = Integer.parseInt(partes_chain[3]);
                                                                            hand_id = Integer.parseInt(partes_chain[4]);
                                                                        } catch (NumberFormatException nfe) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN non-numeric phase/hand_id — refusing");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        String payloadChain = partes_chain[5];
                                                                        Crupier crupier = GameFrame.getInstance().getCrupier();
                                                                        if (crupier == null) {
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        Crupier.UnlockWaitResult waitResult = crupier.awaitStreetForUnlockPhase(phase, hand_id, Crupier.UNLOCK_WAIT_TIMEOUT_MS);
                                                                        if (waitResult != Crupier.UnlockWaitResult.READY) {
                                                                            if (waitResult == Crupier.UnlockWaitResult.TIMEOUT) {
                                                                                // A timeout is ambiguous evidence (host out of order or lag), so it
                                                                                // remains a warning rather than proof of cheating. Because this is a
                                                                                // critical unlock, however, refusing it also closes the channel below;
                                                                                // the hand must recover instead of continuing without the response.
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN phase {0} timed out — host out of order or lag, refusing + warning", phase);
                                                                                crupier.warnSuspiciousHost(Translator.translate("zero_trust.host_unlock_out_of_order"));
                                                                            }
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        if (hand_id != crupier.getMano()) {
                                                                            LOGGER.log(Level.INFO, "REQ_SRA_UNLOCK_CHAIN: hand advanced — closing stale critical channel");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // POCKET_STRADDLE (the straddler's deferred unlock) uses the POCKET
                                                                        // half, same as POCKET: strips my pocket-lock from the straddler's slot.
                                                                        boolean pocketPhase = (phase == Crupier.UNLOCK_PHASE_POCKET
                                                                                || phase == Crupier.UNLOCK_PHASE_POCKET_STRADDLE);
                                                                        byte[] myUnlock = pocketPhase
                                                                                ? this.participantes.get(local_nick).getSra_unlock()
                                                                                : this.participantes.get(local_nick).getSra_unlock_community();
                                                                        if (myUnlock == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN no local unlock for phase {0} — refusing", phase);
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        byte[] myLock = RistrettoSRA.getUnlockScalar(myUnlock); // k = (k^-1)^-1
                                                                        java.util.Map<String, byte[]> commitments = pocketPhase
                                                                                ? crupier.peer_k_pocket : crupier.peer_k_community;
                                                                        byte[] megapacket = crupier.local_mega_packet;
                                                                        String[] ring = crupier.active_crypto_ring;
                                                                        if (megapacket == null || ring == null) {
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN before MEGAPACKET — refusing");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // Fail closed at the smuggling read window. A proof still queued may
                                                                        // finish during the bounded wait; missing, malformed or dishonest proof
                                                                        // enters lockdown and this peer never contributes a community unlock.
                                                                        // Pocket-chain transport is allowed to finish while the
                                                                        // proof is built. Crupier's mandatory pre-betting barrier
                                                                        // prevents those cards from influencing any action. Every
                                                                        // community phase remains gated here as well.
                                                                        if (phase != Crupier.UNLOCK_PHASE_POCKET
                                                                                && crupier.awaitShuffleProofGate(phase,
                                                                                Crupier.SHUFFLE_PROOF_GATE_TIMEOUT_MS)
                                                                                != Crupier.ShuffleProofGateDecision.ALLOW) {
                                                                            crupier.markShuffleProofFailed(megapacket);
                                                                            LOGGER.log(Level.SEVERE, "ZERO-TRUST: refusing community unlock without a verified honest-shuffle proof");
                                                                            crupier.triggerSecurityLockdown(
                                                                                    Translator.translate("zero_trust.host_shuffle_proof_failed"));
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        java.util.List<UnlockChainWire.ReqItem> items = UnlockChainWire.parseReq(payloadChain);
                                                                        if (items == null) {
                                                                            // Structural malformation is not proof of cheating, but the single
                                                                            // supported protocol cannot continue a hand after rejecting a critical
                                                                            // unlock request. Close below and let recovery decide deterministically.
                                                                            LOGGER.log(Level.WARNING, "REQ_SRA_UNLOCK_CHAIN malformed items — closing critical channel");
                                                                            closeCriticalHostChannel();
                                                                            return;
                                                                        }
                                                                        // My own slot in the ring: I must NEVER strip my own lock from MY
                                                                        // pocket (megapacket[mySlot*2], [mySlot*2+1]). The host controls
                                                                        // offsetBase independently of peerIdx, so the correct guard is on the
                                                                        // stripped POINT (pointIdx), not on the peerIdx label: otherwise a
                                                                        // hostile host sends peerIdx=someone-else + offsetBase=mySlot*2 and
                                                                        // extracts my cards.
                                                                        int mySlot = -1;
                                                                        for (int s = 0; s < ring.length; s++) {
                                                                            if (ring[s].equals(local_nick)) {
                                                                                mySlot = s;
                                                                                break;
                                                                            }
                                                                        }
                                                                        // ANTI "peek at the future board": in a COMMUNITY phase, the slot the
                                                                        // host asks me to strip MUST fall within the slots THAT phase is
                                                                        // allowed to touch (derived LOCALLY: see Crupier.communitySlotRange).
                                                                        // The host controls offsetBase; if it asks for a slot from another
                                                                        // street (turn/river during the flop) it's reading the board ahead of
                                                                        // time -> an attack on me -> lockdown. POCKET (commRange==null) is
                                                                        // already covered by the disjoint scalar space + the self-strip check
                                                                        // below.
                                                                        final int[] commRange = Crupier.communitySlotRange(phase, ring.length);
                                                                        // Blind straddle: under POCKET_STRADDLE the ONLY slot I may strip is
                                                                        // the straddler's, whose SIGNED decision I verified. The state gate
                                                                        // already required a verified decision to exist; here I pin the slot:
                                                                        // if the host asks for a different slot under this phase, it's trying
                                                                        // to extract someone else's pocket -> lockdown.
                                                                        int straddlePocketSlot = -1;
                                                                        if (phase == Crupier.UNLOCK_PHASE_POCKET_STRADDLE) {
                                                                            String sNick = crupier.getStraddleDecisionVerifiedNick();
                                                                            if (sNick != null) {
                                                                                for (int s = 0; s < ring.length; s++) {
                                                                                    if (ring[s].equals(sNick)) {
                                                                                        straddlePocketSlot = s;
                                                                                        break;
                                                                                    }
                                                                                }
                                                                            }
                                                                            if (straddlePocketSlot < 0) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: POCKET_STRADDLE without a verified straddler slot — refusing");
                                                                                closeCriticalHostChannel();
                                                                                return;
                                                                            }
                                                                        }
                                                                        // Blind straddle (RESPONDER-side defense, closes the POCKET-phase
                                                                        // bypass): I compute the blind straddler's slot for this hand MYSELF
                                                                        // (I have utg_nick via POSITIONS, STRADDLE, and the set of active
                                                                        // players). Under the normal POCKET phase that slot is UNTOUCHABLE
                                                                        // (only strippable under POCKET_STRADDLE with a signed decision).
                                                                        // Without this, a hostile host — especially when IT is the UTG — could
                                                                        // request the unlock under POCKET (no signature gate) and resolve its
                                                                        // own cards before committing, defeating the blind.
                                                                        final int blindStraddlerSlot = crupier.blindStraddlerSlot();
                                                                        java.util.List<UnlockChainWire.RespItem> resp = new java.util.ArrayList<>();
                                                                        for (UnlockChainWire.ReqItem it : items) {
                                                                            if (it.peerIdx >= 0 && it.peerIdx < ring.length && ring[it.peerIdx].equals(local_nick)) {
                                                                                LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN asks me to unlock my own slot — extraction, refusing");
                                                                                crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                closeCriticalHostChannel();
                                                                                return;
                                                                            }
                                                                            if (commRange != null) {
                                                                                // long for the same reason as the loop below: offsetBase comes
                                                                                // from the wire and in int the sum would overflow to negative,
                                                                                // which would let this window guard wave through a huge offset.
                                                                                // UnlockChainWire already bounds it at parse time; this was the
                                                                                // last bit of arithmetic in the handler still depending on that.
                                                                                long reqLast = (long) it.offsetBase + it.chains.size() - 1;
                                                                                if (it.chains.isEmpty() || it.offsetBase < commRange[0] || reqLast >= commRange[0] + commRange[1]) {
                                                                                    LOGGER.log(Level.SEVERE,
                                                                                            "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN offset {0}(+{1}) outside phase {2} community slots [{3},{4}) — host reading the future board, refusing",
                                                                                            new Object[]{it.offsetBase, it.chains.size(), phase, commRange[0], commRange[0] + commRange[1]});
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_board_peek"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                            }
                                                                            java.util.List<String> outChains = new java.util.ArrayList<>();
                                                                            for (int j = 0; j < it.chains.size(); j++) {
                                                                                // Deliberate long arithmetic: offsetBase comes from the wire and
                                                                                // pointIdx * 32 in int overflows at 2^27 points (= 2^32 bytes),
                                                                                // wrapping back into the valid range and defeating both this
                                                                                // guard and the slot equality checks below. UnlockChainWire
                                                                                // already bounds offsetBase at parse time; this closes it here
                                                                                // too, without relying on that.
                                                                                long pointIdx = (long) it.offsetBase + j;
                                                                                if (pointIdx < 0 || (pointIdx + 1) * 32L > megapacket.length) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN offset out of range — refusing");
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_bad_wire"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                // Real defense against the back-door oracle: even if the
                                                                                // megapacket anchoring is valid, I NEVER strip a point from MY pocket.
                                                                                if ((phase == Crupier.UNLOCK_PHASE_POCKET || phase == Crupier.UNLOCK_PHASE_POCKET_STRADDLE)
                                                                                        && mySlot >= 0
                                                                                        && (pointIdx == mySlot * 2 || pointIdx == mySlot * 2 + 1)) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN asks me to strip my OWN pocket (offset {0}) — extraction, refusing", pointIdx);
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                // Blind straddle: under POCKET_STRADDLE the stripped point MUST be
                                                                                // one of the 2 from the verified straddler's slot; any other one
                                                                                // means extraction of someone else's pocket.
                                                                                if (phase == Crupier.UNLOCK_PHASE_POCKET_STRADDLE
                                                                                        && pointIdx != straddlePocketSlot * 2 && pointIdx != straddlePocketSlot * 2 + 1) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: POCKET_STRADDLE asked to strip non-straddler slot (offset {0}) — extraction, refusing", pointIdx);
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                // Blind straddle (bypass closure): under NORMAL POCKET the blind
                                                                                // straddler's slot is UNTOUCHABLE — it's only stripped under
                                                                                // POCKET_STRADDLE with a signed decision. Requesting it via POCKET =
                                                                                // an attempt to skip the signature gate (seeing the cards before
                                                                                // committing, especially a host-straddler on its own) -> extraction
                                                                                // -> lockdown.
                                                                                if (phase == Crupier.UNLOCK_PHASE_POCKET && blindStraddlerSlot >= 0
                                                                                        && (pointIdx == blindStraddlerSlot * 2 || pointIdx == blindStraddlerSlot * 2 + 1)) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: POCKET asked to strip the blind-straddler slot (offset {0}) — requires POCKET_STRADDLE with a signed decision, refusing", pointIdx);
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                byte[] point = java.util.Arrays.copyOfRange(megapacket, (int) (pointIdx * 32L), (int) ((pointIdx + 1) * 32L));
                                                                                DealChain.Extended ext = DealChain.extend(point, it.chains.get(j), commitments, local_nick, myLock);
                                                                                if (ext == null) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN chain not anchored/invalid (offset {0}) — extraction or tampering, refusing", pointIdx);
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_pocket_extraction"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                // GATE 6 (community/rabbit): after stripping MY community-lock the
                                                                                // residual must NEVER be genesis — that would mean the host handed
                                                                                // me the "every lock except mine" chain so I'd reveal the card ahead
                                                                                // of time. With the binding, blinding is impossible, so a genesis
                                                                                // here is guaranteed extraction. (Under POCKET the self-strip guard
                                                                                // already covers the analogous flank and the intermediate residual
                                                                                // never reaches genesis.)
                                                                                if (phase != Crupier.UNLOCK_PHASE_POCKET
                                                                                        && RistrettoSRA.resolveCardIndex(ext.residual) >= 0) {
                                                                                    LOGGER.log(Level.SEVERE, "ZERO-TRUST: REQ_SRA_UNLOCK_CHAIN community strip reveals genesis (offset {0}) — extraction, refusing", pointIdx);
                                                                                    crupier.triggerSecurityLockdown(Translator.translate("zero_trust.host_community_extraction"));
                                                                                    closeCriticalHostChannel();
                                                                                    return;
                                                                                }
                                                                                outChains.add(ext.wire);
                                                                            }
                                                                            resp.add(new UnlockChainWire.RespItem(it.peerIdx, outChains));
                                                                        }
                                                                        String respPayload = UnlockChainWire.serializeResp(resp);
                                                                        int respIdChain = GameCommandId.next();
                                                                        String myNickB64 = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));
                                                                        writeCommandToServer(Helpers.encryptCommand("GAME#" + respIdChain + "#RESP_SRA_UNLOCK_CHAIN#" + myNickB64 + "#" + respPayload, net_client.getLocal_client_aes_key(), net_client.getLocal_client_hmac_key()));
                                                                    } catch (Exception e) {
                                                                        LOGGER.log(Level.SEVERE, "Failed to process critical REQ_SRA_UNLOCK_CHAIN; closing host channel", e);
                                                                        closeCriticalHostChannel();
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
                                                                // Telemetry. The payload's wire format uses '#' as an internal
                                                                // separator (timestamp#entries), so if the GAME command's
                                                                // split('#') produced more than 4 parts, parts[3..end] must be
                                                                // rejoined with '#' to reconstruct the original payload before
                                                                // decoding.
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
                                                                        if (GameFrame.entrarSalaSonidoOn()) {
                                                                            Audio.playWavResource("misc/new_user.wav");
                                                                        }
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
                                                                // Global host rule. The "Game settings" dialog reflects the flag
                                                                // when it opens; there's no menu/popup control left to sync.
                                                                GameFrame.IWTSTH_RULE = "1".equals(partes_comando[3]);
                                                                break;
                                                            case "RUNITWICERULE":
                                                                GameFrame.RUN_IT_TWICE = "1".equals(partes_comando[3]);
                                                                break;
                                                            case "BOTBALRULE":
                                                                // Whether bots' balance is split among humans (editable mid-game by
                                                                // the host). The "Game settings" dialog reflects the flag on open.
                                                                GameFrame.BOT_BALANCE_TO_HUMANS = "1".equals(partes_comando[3]);
                                                                break;
                                                            case "BOTREBUYRULE":
                                                                // Whether bots rebuy (editable mid-game by the host).
                                                                GameFrame.BOT_REBUY = "1".equals(partes_comando[3]);
                                                                break;
                                                            case "VOICEMSGRULE":
                                                                // Global host rule. The audio settings dialog reflects the flag
                                                                // when it opens; there's no menu/popup control left to sync.
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
                                                                    RitVoteCloseEnvelope.Result result
                                                                            = RitVoteCloseEnvelope.parse(partes_comando);
                                                                    if (!result.isOk()) {
                                                                        throw new IllegalArgumentException(result.error());
                                                                    }
                                                                    GameFrame.getInstance().getCrupier()
                                                                            .acceptRitVoteCloseOnce(result.agreed());
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical RIT_VOTE_CLOSE; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "RABBITRULE":
                                                                GameFrame.RABBIT_HUNTING = Integer.parseInt(partes_comando[3]);
                                                                break;
                                                            case "RABBIT_AUTH":
                                                                try {
                                                                    if (partes_comando.length != 4) {
                                                                        throw new IllegalArgumentException("wrong Rabbit authorization arity");
                                                                    }
                                                                    RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> decoded
                                                                            = RabbitFeeLedger.Authorization.decode(
                                                                                    Base64.getDecoder().decode(partes_comando[3]));
                                                                    if (!decoded.isOk()) {
                                                                        throw new IllegalArgumentException(decoded.error());
                                                                    }
                                                                    GameFrame.getInstance().getCrupier()
                                                                            .RABBIT_AUTHORIZATION_HANDLER(decoded.value());
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE, "Invalid critical Rabbit authorization; closing connection", ex);
                                                                    exit = true;
                                                                    closeClientSocket();
                                                                }
                                                                break;
                                                            case "REQ_SHOWDOWN_KEY":
                                                            case "POTCARDS":
                                                                try {
                                                                    Crupier crupierShowdown = GameFrame.getInstance().getCrupier();
                                                                    if ("REQ_SHOWDOWN_KEY".equals(subcomando)) {
                                                                        if (partes_comando.length != 3) {
                                                                            throw new IllegalArgumentException("REQ_SHOWDOWN_KEY requires exactly 3 fields");
                                                                        }
                                                                        crupierShowdown.acceptShowdownKeyRequestOnce();
                                                                    } else {
                                                                        crupierShowdown.acceptPotCardsOnce();
                                                                    }
                                                                    synchronized (crupierShowdown.getReceived_commands()) {
                                                                        crupierShowdown.enqueueReceivedCommand(recibido,
                                                                                () -> Helpers.threadRun(() -> {
                                                                                    exit = true;
                                                                                    closeClientSocket();
                                                                                }));
                                                                        crupierShowdown.getReceived_commands().notifyAll();
                                                                    }
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid or duplicate critical " + subcomando + "; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "MEGAPACKET":
                                                                // The REQ_SRA_UNLOCK handler that follows runs on its own threadRun
                                                                // and needs to see local_mega_packet + active_crypto_ring for its
                                                                // state machine. If we left the Crupier to set them from its queue,
                                                                // there'd be a race (another thread processes REQ_SRA_UNLOCK first
                                                                // and rejects it for hand-not-started). We populate them
                                                                // synchronously here and forward to the queue so the rest of the
                                                                // Crupier's flow (decrypting my pocket cards) keeps working exactly
                                                                 // as before.
                                                                 try {
                                                                     Crupier crupierMP = GameFrame.getInstance().getCrupier();
                                                                     crupierMP.installMegaPacketFromHost(Crupier.parseMegaPacketWire(partes_comando));
                                                                 } catch (Exception e) {
                                                                     LOGGER.log(Level.SEVERE, "Invalid critical MEGAPACKET; closing host connection", e);
                                                                     exit = true;
                                                                     closeClientSocket();
                                                                     break;
                                                                 }
                                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                                }
                                                                break;
                                                            case "POCKET_CARDS":
                                                                try {
                                                                    Crupier crupierPC = GameFrame.getInstance().getCrupier();
                                                                    Crupier.ParsedPocketCards parsedPocket = Crupier.parsePocketCardsWire(
                                                                            partes_comando, crupierPC.active_crypto_ring);
                                                                    Crupier.installPocketCardsOnce(
                                                                            crupierPC.single_locked_pocket_cards, parsedPocket);
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid or duplicate critical POCKET_CARDS; closing host connection", e);
                                                                    exit = true;
                                                                    closeClientSocket();
                                                                    break;
                                                                }
                                                                // Forward to the queue so the Crupier can continue its normal local flow
                                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                                }
                                                                break;
                                                            case "POCKET_DEFERRED":
                                                                try {
                                                                    Crupier crupierPD = GameFrame.getInstance().getCrupier();
                                                                    Crupier.parsePocketDeferredWire(partes_comando,
                                                                            crupierPD.active_crypto_ring, local_nick);
                                                                    crupierPD.installPocketDeferredOnce();
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical POCKET_DEFERRED; closing host connection", e);
                                                                    exit = true;
                                                                    closeClientSocket();
                                                                    break;
                                                                }
                                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                                }
                                                                break;
                                                            case "REBUYNOW":
                                                                // On a separate thread, mirroring the host side: rebuyNow holds
                                                                // lock_rebuynow, and our OWN rebuy (client-side branch, EDT) holds it
                                                                // during a SYNCHRONOUS send that waits for the host's CONF, which
                                                                // THIS consumer thread reads. If the consumer handled an incoming
                                                                // REBUYNOW from another player inline, it would block on
                                                                // lock_rebuynow and never read our own rebuy's CONF -> self-deadlock
                                                                // (same class of bug as the pause).
                                                                try {
                                                                    ImmediateRebuyWire.Relay relay
                                                                            = ImmediateRebuyWire.parseHostRelay(partes_comando);
                                                                    final String rbNick = relay.nick();
                                                                    final int rbBuyin = relay.amount();
                                                                    if (relay.denied() || !GameFrame.getInstance().getCrupier()
                                                                            .getNick2player().containsKey(rbNick)) {
                                                                        throw new IllegalArgumentException("invalid REBUYNOW relay target");
                                                                    }
                                                                    final long rbSequence = nextRebuyRelaySequence();
                                                                    Helpers.threadRun(() -> GameFrame.getInstance().getCrupier()
                                                                            .applyRemoteRebuyNow(rbNick, rbBuyin, rbSequence));
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical REBUYNOW relay; closing host channel", e);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "REBUYDENIED":
                                                                try {
                                                                    ImmediateRebuyWire.Relay relay
                                                                            = ImmediateRebuyWire.parseHostRelay(partes_comando);
                                                                    final String dnNick = relay.nick();
                                                                    final int dnLimit = relay.amount();
                                                                    if (!relay.denied() || !GameFrame.getInstance().getCrupier()
                                                                            .getNick2player().containsKey(dnNick)) {
                                                                        throw new IllegalArgumentException("invalid REBUYDENIED relay target");
                                                                    }
                                                                    final long dnSequence = nextRebuyRelaySequence();
                                                                    Helpers.threadRun(() -> {
                                                                        // A denial is an ordered zero relay. Applying it through the same
                                                                        // sequence gate prevents an older positive task from restoring the
                                                                        // optimistic entry after the host has rejected it.
                                                                        GameFrame.getInstance().getCrupier()
                                                                                .applyRemoteRebuyNow(dnNick, 0, dnSequence);
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
                                                                    });
                                                                } catch (Exception e) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical REBUYDENIED relay; closing host channel", e);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "SHOWCARDS":
                                                                Helpers.threadRun(() -> {
                                                                    try {
                                                                        if (partes_comando.length != 6) {
                                                                            throw new IllegalArgumentException("SHOWCARDS requires exactly 6 fields");
                                                                        }
                                                                        String shNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                        String sraKeyB64 = partes_comando[4];
                                                                        String sigB64 = partes_comando[5];
                                                                        boolean revealed = GameFrame.getInstance().getCrupier()
                                                                                .showPlayerCards(shNick, sraKeyB64, sigB64);
                                                                        if (!revealed) {
                                                                            LOGGER.log(Level.SEVERE,
                                                                                    "Invalid critical SHOWCARDS from host; closing channel");
                                                                            closeCriticalHostChannel();
                                                                        }
                                                                    } catch (Exception e) {
                                                                        LOGGER.log(Level.SEVERE, "Error processing SHOWCARDS on client", e);
                                                                        closeCriticalHostChannel();
                                                                    }
                                                                });
                                                                break;
                                                            case "RABBIT_FLOP_PIECE":
                                                            case "RABBIT_TURN_PIECE":
                                                            case "RABBIT_RIVER_PIECE": {
                                                                // v3: the host sends each remote human its piece
                                                                // (RABBIT_*_PIECE#nickB64#payloadB64) with everyone else's locks
                                                                // already removed. Only the recipient can decrypt it.
                                                                final String[] partes_rp = partes_comando;
                                                                final String cmdName = partes_comando[2];
                                                                Helpers.threadRun(() -> {
                                                                    try {
                                                                        if (partes_rp.length < 5) {
                                                                            LOGGER.log(Level.WARNING, "rabbit piece malformed wire (parts={0}) — refusing (cosmetic, not shown)", partes_rp.length);
                                                                            return;
                                                                        }
                                                                        String targetNick = new String(Base64.getDecoder().decode(partes_rp[3]), "UTF-8");
                                                                        if (!targetNick.equals(local_nick)) {
                                                                            return; // someone else's piece, silent drop
                                                                        }
                                                                        byte[] piece = Base64.getDecoder().decode(partes_rp[4]);
                                                                        int expectedLen = "RABBIT_FLOP_PIECE".equals(cmdName) ? 96 : 32;
                                                                        Crupier crupierRP = GameFrame.getInstance().getCrupier();
                                                                        if (crupierRP == null || piece == null || piece.length != expectedLen) {
                                                                            // Policy: the rabbit is a COSMETIC post-hand reveal (the hand is
                                                                            // already settled); a bad piece cannot steal money ->
                                                                            // SILENT-REFUSE (that rabbit just isn't shown), we do NOT end the
                                                                            // game. Almost certainly a bug, not an attack.
                                                                            LOGGER.log(Level.WARNING, "rabbit piece {0} bad length {1} — refusing (cosmetic, not shown)", new Object[]{cmdName, piece == null ? -1 : piece.length});
                                                                            return;
                                                                        }
                                                                        // Dual-lock: rabbit pieces are community pieces, encrypted with
                                                                        // community scalars after the rotation.
                                                                        byte[] unlockedRP = RistrettoSRA.applyCommutativeLock(piece, this.participantes.get(local_nick).getSra_unlock_community());
                                                                        int numCards = "RABBIT_FLOP_PIECE".equals(cmdName) ? 3 : 1;
                                                                        int[] indices = new int[numCards];
                                                                        for (int k = 0; k < numCards; k++) {
                                                                            byte[] chunk = Arrays.copyOfRange(unlockedRP, k * 32, (k + 1) * 32);
                                                                            int idx = RistrettoSRA.resolveCardIndex(chunk);
                                                                            if (idx < 0) {
                                                                                // Post-hand cosmetic -> SILENT-REFUSE (that rabbit just isn't shown), no lockdown.
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
                                                            // v3: community pieces during a live hand. The handler just
                                                            // re-queues onto the Crupier — decryption and verification live in
                                                            // Crupier.recibirCartasComunitarias, which blocks in
                                                            // rondaApuestas and drains the queue.
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
                                                                    GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                                }
                                                                break;
                                                            case "LASTHAND":
                                                                // Guard: the reader thread can have LASTHAND buffered when
                                                                // RESET_GAME has already run GameFrame.resetInstance() — without
                                                                // this guard, NPE in getInstance().getCrupier(). Rare race, cheap
                                                                // to cover.
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
                                                                GameConfigWireV1.Result updateConfig = partes_comando.length == 4
                                                                        ? GameConfigWireV1.decodeBase64(partes_comando[3])
                                                                        : null;
                                                                if (updateConfig == null || !updateConfig.isOk()) {
                                                                    LOGGER.log(Level.SEVERE, "Invalid UPDATEBLINDS configuration; closing connection");
                                                                    exit = true;
                                                                    closeClientSocket();
                                                                    break;
                                                                }
                                                                if (GameFrame.ANTE != updateConfig.value().ante()
                                                                        || GameFrame.STRADDLE != updateConfig.value().straddle()) {
                                                                    GameFrame.getInstance().getCrupier().marcarCambioAnteStraddle();
                                                                }
                                                                updateConfig.value().applyBlindUpdateToGlobals();
                                                                GameFrame.getInstance().getCrupier().actualizarCiegasManualmente(
                                                                        updateConfig.value().smallBlind(), updateConfig.value().bigBlind(),
                                                                        updateConfig.value().blindsDouble(), updateConfig.value().blindsDoubleType());
                                                                break;
                                                            case "SERVEREXIT":
                                                                try {
                                                                    TableTerminationWire.ExitCommand termination
                                                                            = TableTerminationWire.parse(partes_comando);
                                                                    if (termination.recover()) {
                                                                        throw new IllegalArgumentException("SERVEREXIT parsed as recover");
                                                                    }
                                                                    exit = true;
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid SERVEREXIT; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "SERVEREXITRECOVER":
                                                                try {
                                                                    TableTerminationWire.ExitCommand termination
                                                                            = TableTerminationWire.parse(partes_comando);
                                                                    if (!termination.recover()) {
                                                                        throw new IllegalArgumentException("SERVEREXITRECOVER parsed as final exit");
                                                                    }
                                                                    password = termination.password();
                                                                    GameFrame.getInstance().getCrupier().setForce_recover(true);
                                                                    exit = true;
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid SERVEREXITRECOVER; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "TTS":
                                                                // The host turns TTS on/off (global) for everyone. The audio
                                                                // settings dialog reflects the flag when it opens; there's no
                                                                // menu/popup control left to sync.
                                                                GameFrame.TTS_SERVER = "1".equals(partes_comando[3]);
                                                                break;
                                                            case "PAUSE":
                                                                // The host is AUTHORITATIVE for pause coordination: "0" resumes, "1"
                                                                // pauses, with no nick comparison (the consensus seat makes them
                                                                // identical, and tying it to that was fragile).
                                                                //
                                                                // KEY (deadlock): this is processed on a SEPARATE thread
                                                                // (Helpers.threadRun), like the host's handler (Participant), NOT
                                                                // inline on this consumer thread. pauseTimba, when the pauser itself
                                                                // applies it upon receiving the resume, does a SYNCHRONOUS
                                                                // sendGAMECommandToServer that waits on the host's CONF, and that
                                                                // CONF is read by this SAME consumer thread (case "CONF"): doing it
                                                                // inline would self-deadlock the consumer waiting on a CONF only it
                                                                // could process, leaving the pauser hanging. threadRun frees the
                                                                // consumer to read the CONF. The check-then-act runs under
                                                                // lock_pause, same as the host.
                                                                try {
                                                                    PauseWire.Relay relay = PauseWire.parseHostRelay(partes_comando);
                                                                    final String pause_value = relay.paused() ? "1" : "0";
                                                                    final String pauser = relay.owner();
                                                                    if (!GameFrame.getInstance().getCrupier().getNick2player()
                                                                            .containsKey(pauser)) {
                                                                        throw new IllegalArgumentException("unknown PAUSE owner");
                                                                    }
                                                                    final long pause_sequence = nextPauseRelaySequence();
                                                                    Helpers.threadRun(() -> {
                                                                        // Keep this asynchronous to avoid the CONF self-deadlock, but
                                                                        // serialize the sequence gate with the state transition so a
                                                                        // late pool task cannot undo a newer host decision.
                                                                        synchronized (pause_relay_order_lock) {
                                                                            if (!Crupier.shouldApplyAsyncSequence(pause_sequence, pause_relay_applied_sequence)) {
                                                                                return;
                                                                            }
                                                                            synchronized (GameFrame.getInstance().getLock_pause()) {
                                                                                pause_relay_applied_sequence = pause_sequence;
                                                                                if (("0".equals(pause_value) && GameFrame.getInstance().isTimba_pausada())
                                                                                        || ("1".equals(pause_value) && !GameFrame.getInstance().isTimba_pausada())) {
                                                                                    GameFrame.getInstance().pauseTimba(pauser);
                                                                                }
                                                                            }
                                                                        }
                                                                    });
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical PAUSE relay; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "SHUFFLE_TURN":
                                                                // VISUAL shuffle overlay: the host announces which player is
                                                                // processing its cascade step right now, so this peer can paint it
                                                                // over that player (local or remote). Purely for display; the
                                                                // GameFrame controller honors the local preference. Doesn't touch
                                                                // the cascade or consensus.
                                                                try {
                                                                    if (partes_comando.length >= 4) {
                                                                        String shuffleNick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                        GameFrame.getInstance().onShuffleTurn(shuffleNick);
                                                                    }
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE, "Error processing SHUFFLE_TURN", ex);
                                                                }
                                                                break;
                                                            case "SHUFFLE_TURN_END":
                                                                // Shuffle end: hides the shuffle overlay on this peer.
                                                                GameFrame.getInstance().onShuffleTurnEnd();
                                                                break;
                                                            case "HANDVERIFY":
                                                                try {
                                                                    Crupier handverifyCrupier = GameFrame.getInstance().getCrupier();
                                                                    if (partes_comando.length == 3) {
                                                                        handverifyCrupier.acceptHandverifyTriggerOnce();
                                                                    } else {
                                                                        HandverifyReceiptEnvelope receipt
                                                                                = HandverifyReceiptEnvelope.parse(partes_comando);
                                                                        handverifyCrupier.acceptHandverifyReceiptOnce(receipt.nick());
                                                                    }
                                                                    synchronized (handverifyCrupier.getReceived_commands()) {
                                                                        handverifyCrupier.enqueueReceivedCommand(recibido,
                                                                                () -> Helpers.threadRun(this::closeCriticalHostChannel));
                                                                        handverifyCrupier.getReceived_commands().notifyAll();
                                                                    }
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE,
                                                                            "Invalid critical HANDVERIFY; closing host channel", ex);
                                                                    closeCriticalHostChannel();
                                                                }
                                                                break;
                                                            case "MISDEAL":
                                                                // The host aborts the hand. Cancel locally and forward to the
                                                                // queue to wake up any consumer (receiveMyCards,
                                                                // recibirConsensoFinal, etc.) waiting on a timeout.
                                                                try {
                                                                    String motivoMisdeal = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    GameFrame.getInstance().getCrupier().cancelarManoYDevolverApuestas(motivoMisdeal, false);
                                                                } catch (Exception ex) {
                                                                    LOGGER.log(Level.SEVERE, "Error processing MISDEAL", ex);
                                                                }
                                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
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
                                                                                // Dual-lock: the testament is the community half of the departing
                                                                                // peer. The pocket half is never shared via EXIT. A USABLE
                                                                                // scalar is required, not just one of the right size: 32 zero
                                                                                // bytes passed the size check and blew up the Crupier thread when
                                                                                // inverted, taking the process down with it.
                                                                                if (RistrettoSRA.isValidScalar(testament)) {
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
                                                            case "STRADDLE_DECISION":
                                                                // Blind straddle: the host broadcasts the straddler's SIGNED
                                                                // decision. I verify it and, if valid, enable the deferred unlock of
                                                                // its slot (recordVerifiedStraddleDecision). Dispatched IMMEDIATELY
                                                                // (not queued) to unblock a REQ_SRA_UNLOCK_CHAIN handler that might
                                                                // already be waiting on the flag in awaitStreetForUnlockPhase.
                                                                if (GameFrame.getInstance().getCrupier() != null) {
                                                                    GameFrame.getInstance().getCrupier().onStraddleDecisionCommand(partes_comando);
                                                                }
                                                                break;
                                                            default:
                                                            synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                                    GameFrame.getInstance().getCrupier().enqueueReceivedCommand(recibido,
                                                                            () -> Helpers.threadRun(() -> {
                                                                                exit = true;
                                                                                closeClientSocket();
                                                                            }));
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
                                                                // FULL config mirror (the HOST changed it). Only stored in the
                                                                // holder (GameFrame.* is NOT written) and, if the settings wheel
                                                                // is open, its Game tab is refreshed.
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
                                                                if (GameFrame.entraSonidoOn()) {
                                                                    Audio.playWavResource("misc/laser.wav");
                                                                }
                                                                try {
                                                                    String nickNew = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    boolean isBot = nickNew.startsWith("CoronaBot$");
                                                                    RemoteRosterAdmission rosterAdmission = admitRemoteRosterParticipant(
                                                                            nickNew,
                                                                            partes_comando.length >= 6 ? partes_comando[5] : "*",
                                                                            isBot, "1".equals(partes_comando[4]), "NEWUSER");
                                                                    if (rosterAdmission == RemoteRosterAdmission.REJECT) {
                                                                        rejectRemoteRoster(nickNew, "NEWUSER");
                                                                        break;
                                                                    } else if (rosterAdmission == RemoteRosterAdmission.DUPLICATE) {
                                                                        break;
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
                                                                        boolean isListBot = list_nick.startsWith("CoronaBot$");
                                                                        RemoteRosterAdmission rosterAdmission = admitRemoteRosterParticipant(
                                                                                list_nick,
                                                                                user_parts.length >= 3 ? user_parts[2] : "*",
                                                                                isListBot, "1".equals(user_parts[1]), "USERSLIST");
                                                                        if (rosterAdmission == RemoteRosterAdmission.REJECT) {
                                                                            rejectRemoteRoster(list_nick, "USERSLIST");
                                                                            break;
                                                                        } else if (rosterAdmission == RemoteRosterAdmission.DUPLICATE) {
                                                                            continue;
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
                                                                GameConfigWireV1.Result initConfig = partes_comando.length == 4
                                                                        ? GameConfigWireV1.decodeBase64(partes_comando[3])
                                                                        : null;
                                                                if (initConfig == null || !initConfig.isOk()) {
                                                                    LOGGER.log(Level.SEVERE, "Invalid INIT configuration; closing connection");
                                                                    exit = true;
                                                                    closeClientSocket();
                                                                    break;
                                                                }
                                                                initConfig.value().applyToGlobals();
                                                                Helpers.GUIRun(() -> {
                                                                    setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                                                                    sound_icon.setVisible(false);
                                                                    status.setText(Translator.translate("status.inicializando_juego"));
                                                                    status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));
                                                                    barra.setVisible(true);
                                                                });
                                                                Helpers.GUIRunAndWait(new Runnable() {
                                                                    public void run() {
                                                                        // If the client had the settings wheel open (with the waiting
                                                                        // room's Game tab), it closes itself when the game starts: those
                                                                        // settings no longer apply. Direct dispose() = no "discard
                                                                        // changes" dialog.
                                                                        SettingsDialog.closeIfOpen();
                                                                        new GameFrame(THIS, local_nick, false);
                                                                    }
                                                                });
                                                                partida_empezada = true;
                                                                Helpers.GUIRunAndWait(() -> setVisible(false));
                                                                GameFrame.getInstance().AJUGAR();
                                                                break;
                                                        }
                                                    }
                                                }
                                                break;
                                            case "CONF":
                                                if (WaitingRoomFrame.getInstance() != null) {
                                                    WaitingRoomFrame.getInstance().getReceived_confirmations()
                                                            .confirm(server_nick, Integer.parseInt(partes_comando[1]));
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
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
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
                                // setValue(j) is redundant: smoothCountdown already repaints every
                                // 50ms via its internal Timer. The loop keeps running to update the
                                // status text every second and to detect exit.
                            });
                            if (!exit) {
                                synchronized (net_client.getLock_client_reconnect()) {
                                    try {
                                        net_client.getLock_client_reconnect().wait(1000);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                        // Cancel the internal Timer after the loop — if exit=true,
                        // WaitingRoomFrame gets disposed right after, so this avoids an
                        // orphaned background Timer.
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
                invalidateSession();
                Helpers.GUIRunAndWait(() -> {
                    // On cancel, reopen the launch screen at the same spot and size (or
                    // maximized if it was) on the screen the waiting room is on.
                    Helpers.showFrameOnScreen(Init.VENTANA_INICIO, getGraphicsConfiguration(),
                            Init.LAUNCH_FRAME_SIZE, Init.LAUNCH_FRAME_MAXIMIZED);
                    avatar_io.close();
                    dispose();
                    // Release the singleton on this return-to-menu path (connect failed/cancelled):
                    // otherwise getInstance() keeps handing out a DISPOSED frame and its whole
                    // NetClient/participants/chat graph lingers until the next room opens.
                    if (THIS == WaitingRoomFrame.this) {
                        THIS = null;
                    }
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
     * their initial handshake. Decodes the base64-encoded pubkey (32 bytes) and
     * signature (64 bytes), then delegates to
     * {@link IdentityManager#verifyJoin} under the current game's session_id
     * and the NFC-normalized nick.
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
     * Identity: stores the validated identity on the participant entry, runs
     * the local TOFU resolution, and logs the outcome (NEW / MATCH / CHANGED).
     * Called by the host right after a successful JOIN.
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

    /**
     * Identity/consensus guard: returns true when {@code client_nick} would
     * collapse to the same canonical PLAYER_ID as an already-seated participant
     * (the host included — its own nick lives in {@code participantes} with a
     * null value).
     *
     * <p>
     * The settlement layer derives PLAYER_ID from NFC(nick) (see
     * {@link CanonicalActionRecord#playerIdFromNick}). Two raw nicks whose
     * bytes differ but whose NFC form is identical — e.g. a precomposed «é»
     * (U+00E9) vs. «e» + combining acute (U+0301) — share a single PLAYER_ID.
     * The exact-string NICKFAIL check upstream does NOT catch that (the strings
     * differ), so both would seat; the SettlementRecord then rejects the second
     * as a duplicate id and DISABLES settlement consensus for the whole table.
     * Rejecting the join keeps every seated identity distinct at the PLAYER_ID
     * level.
     *
     * <p>
     * Uses the same NFC form (no trim) as {@code playerIdFromNick} / the JOIN
     * self-sig payload, so it flags exactly the pairs that collide in
     * settlement and nothing else. Compares KEYS only, under the map's monitor
     * (a synchronizedMap iterator is not otherwise safe against a concurrent
     * join).
     */
    private boolean nickCollisionNFC(String client_nick) {
        String nfc_incoming = java.text.Normalizer.normalize(client_nick, java.text.Normalizer.Form.NFC);
        synchronized (participantes) {
            for (String existing : participantes.keySet()) {
                if (java.text.Normalizer.normalize(existing, java.text.Normalizer.Form.NFC).equals(nfc_incoming)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean serverSocketHandler(final Socket client_socket) {

        // The accept loop already reserved a handshake slot (handshake_slots). This try/finally
        // guarantees it's released no matter what (success, rejection, exception, or any of the
        // body's early returns).
        if (!net_server.trackPendingHandshake(client_socket)) {
            handshake_slots.release();
            return false;
        }
        boolean submitted = HandshakeAdmission.submit(Helpers::threadRun, () -> {
            try {

                LOGGER.log(Level.INFO, "A client is trying to connect...");
                net_server.getClient_threads().add(Thread.currentThread().getId());
                String recibido;
                String[] partes;
                try {
                    client_socket.setTcpNoDelay(true);
                    client_socket.setKeepAlive(true);
                    // Anti-DoS lock: if the peer does NOT finish the handshake within
                    // HANDSHAKE_TIMEOUT_MS, the blocked read throws SocketTimeoutException and we
                    // fall into the catch that closes the socket and frees the thread. RESET to 0
                    // further down on the two success branches (nuevoParticipante for a clean JOIN
                    // and resetSocket for a reconnect).
                    client_socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                    byte[] magic = new byte[Helpers.toByteArray(MAGIC_BYTES).length];
                    // readFully (not read()): a magic split by TCP segmentation used to leave the
                    // buffer half-filled and wrongly reject a valid client. The SoTimeout above
                    // still covers a peer that never sends enough.
                    DataInputStream dIn = new DataInputStream(client_socket.getInputStream());
                    dIn.readFully(magic);
                    if (Helpers.toHexString(magic).toLowerCase().equals(MAGIC_BYTES)) {

                        /* CLEAN KEY EXCHANGE START */
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
                        /* KEY EXCHANGE END */

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
                            net_server.getClient_threads().remove(Thread.currentThread().getId());
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
                            net_server.getClient_threads().remove(Thread.currentThread().getId());
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

                                    // Authenticated grace refresh BEFORE resetSocket: if the
                                    // Participant's reader is in wait and the base grace is about to
                                    // expire, this crypto-valid attempt extends it to
                                    // CLIENT_RECON_TIMEOUT. Covers the slow-network case where the
                                    // handshake+payload arrive right at the edge.
                                    participantes.get(client_nick).signalReconnectIntent();

                                    LOGGER.log(Level.WARNING, "Resetting client socket...");

                                    // Handshake complete: the Participant takes control of the
                                    // socket and its normal reads (PING/PONG, GAME, etc.) must not
                                    // inherit the handshake deadline.
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

                                        // Explicit ack to the client so its reconectarCliente knows the
                                        // reconnect was truly accepted. Without this ack the client
                                        // marked ok_rec=true just because the crypto handshake finished
                                        // without an exception, and if the server closed the socket
                                        // immediately (any of the DENIED branches), the client's reader
                                        // read null and looped back into reconectarCliente() with no
                                        // pause — a busy-loop doing ECDH every iteration that froze the
                                        // UI and spiked CPU to 100%.
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
                                        // Explicit denial ack before closing (see the note on the OK
                                        // branch above for why the ack is needed).
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
                                    // BAD HMAC: the client brought an old session key (its orig HMAC
                                    // doesn't match the Participant's current one). EXPECTED case
                                    // after a long interruption — the client's Reconnect2ServerDialog
                                    // retries automatically every few seconds. We do NOT pop up a
                                    // dialog on the host: every attempt would generate a new popup and
                                    // they'd pile up until the server became unusable. The client will
                                    // see the explicit denial (RECONNECT_DENIED) in its
                                    // reconectarCliente and land on its own dialog with a pause
                                    // between attempts.
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
                                // Explicit denial ack before closing the socket. Without this the
                                // client thinks it reconnected (its handshake finished OK), its
                                // reader reads null immediately when the server closes, and it
                                // calls reconectarCliente() again in a no-pause busy-loop (the 5s
                                // pause only applies when ok_rec=false) — yxmgl bug, 20.59 issue 1:
                                // freeze + CPU spike after a server "recover".
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
                        } else if (hasReservedBotNickCharacter(client_nick)) {
                            LOGGER.log(Level.WARNING,
                                    "Rejected unauthorized remote nick {0}: '$' is reserved for bots",
                                    client_nick);
                            writeCommandFromServer(
                                    Helpers.encryptCommand("NICKUNAUTHORIZED", aes_key, hmac_key),
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
                                    if (GameFrame.entrarSalaSonidoOn()) {
                                        Audio.playWavResource("misc/new_user.wav");
                                    }
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
                        } else if (participantes.containsKey(client_nick) || nickCollisionNFC(client_nick)) {
                            // NICKFAIL covers both the exact-same nick AND one that collides in NFC
                            // form (same PLAYER_ID -> would break settlement consensus). See
                            // nickCollisionNFC.
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
                            // Fourth field (#) ADDED to the same NICKOK command: the FULL config
                            // mirror (serialized GamePreset.Settings) so the newly joined client can
                            // populate its Game tab greyed out. It's an extra field on the SAME
                            // message (not a new read), so the handshake sequence is unchanged.
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
                                    // The containsKey from the early check (NICKFAIL) runs OUTSIDE
                                    // lock_new_client, and simultaneous JOINs each get their own
                                    // thread: two clients with the same nick could pass that check
                                    // before either inserted, and end up overwriting each other in
                                    // participantes (the first socket/thread was left orphaned but
                                    // alive). The nick is RE-checked here, under the same lock as
                                    // the insertion, closing the TOCTOU window.
                                    if (participantes.size() < MAX_PARTICIPANTES
                                            && !WaitingRoomFrame.getInstance().isPartida_empezando()
                                            && !WaitingRoomFrame.getInstance().isPartida_empezada()
                                            && !participantes.containsKey(client_nick)
                                            && !nickCollisionNFC(client_nick)) {
                                        client_avatar = decodeRemoteAvatar(partes[2], client_nick, "JOIN");
                                        // Handshake complete: the Participant takes control of the
                                        // socket and its normal reads (PING/PONG, GAME, etc.) must not
                                        // inherit the handshake deadline.
                                        try {
                                            client_socket.setSoTimeout(0);
                                        } catch (Exception ex) {
                                            LOGGER.log(Level.WARNING, "Could not clear handshake SoTimeout on new join", ex);
                                        }
                                        nuevoParticipanteRemoto(client_nick, client_avatar, client_socket, aes_key, hmac_key,
                                                false, false);
                                        // Identity: cache pubkey+self_sig on the new Participant
                                        // and run local TOFU resolution. partes[4] / partes[5] were
                                        // validated above by verifyJoinSelfSig.
                                        recordJoinIdentity(participantes.get(client_nick), partes[4], partes[5]);
                                        if (GameFrame.entraSonidoOn()) {
                                            Audio.playWavResource("misc/laser.wav");
                                        }

                                        if (participantes.size() > 2) {
                                            // USERSLIST is only sent when there's at least one other peer
                                            // besides the new one (host + new == size 2 -> nothing to
                                            // list; the host's identity already travels in the
                                            // synchronous intro).
                                            enviarListaUsuariosActualesAlNuevoUsuario(participantes.get(client_nick));

                                            // Identity: NEWUSER carries the new peer's pubkey +
                                            // self_sig so already-connected peers can independently verify
                                            // and TOFU-resolve in the same packet that announces the join.
                                            // Avatar slot uses "*" placeholder for a fixed 5-field layout
                                            // (nick|flag|avatar|pubkey|sig).
                                            Participant newPar = participantes.get(client_nick);
                                            if (newPar == null) {
                                                // The newcomer is already gone (dropped between joining and
                                                // this announcement). Without this check, a failure would
                                                // hit here that the catch below swallowed SILENTLY, and the
                                                // join was left half-done: in the list but never announced
                                                // to the rest.
                                                LOGGER.log(Level.WARNING,
                                                        "{0} vanished before its join could be announced — skipping the announcement",
                                                        client_nick);
                                                return;
                                            }
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
                                    // This catch used to be SILENT. A join that broke midway left the
                                    // newcomer half-done (in the list, never announced to the rest)
                                    // with no trace of why.
                                    LOGGER.log(Level.SEVERE, "Failed to complete the join of " + client_nick, ex);
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
                    // Any exception landing here happened in the early handshake (reading
                    // magic/pubkey, ECDH, version parsing, rejection branches,
                    // verifyJoinSelfSig) — ALWAYS before the synchronized(lock_new_client) block,
                    // whose handoff to Participant has its own inner catch. So the socket was
                    // never handed to a peer: closing it plugs the residual FD leak with no risk
                    // of closing a live socket already held by a Participant.
                    if (client_socket != null) {
                        try {
                            client_socket.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            } finally {
                // Pre-auth anti-DoS: releases the slot reserved in the accept loop, no matter
                // how the handshake ended. Without this, a handshake exiting via return/exception
                // would leak the permit.
                handshake_slots.release();
                net_server.untrackPendingHandshake(client_socket);
                // The thread is also deregistered here, for the same reason: it used to be
                // outside, so any exit via return skipped it and the thread stayed registered
                // forever. With even one stuck, closing the room sees live threads remaining
                // and skips its whole shutdown handler: the X stops responding for the rest of
                // the session.
                net_server.getClient_threads().remove(Thread.currentThread().getId());
            }
        }, client_socket, handshake_slots);
        if (!submitted) {
            net_server.untrackPendingHandshake(client_socket);
        }
        return submitted;

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
                    // The socket is published AFTER it's bound to the port (and cleared before
                    // trying). Publishing it earlier made the catch guard below UNREACHABLE — the
                    // one that specifically checks whether it never got created: with the port
                    // taken, nothing was reported, exit was never set, and this loop retried
                    // without pause, rewriting the preferences file every time around. The "could
                    // not open the port" message had never once fired.
                    net_server.setServer_socket(null);
                    ServerSocket ss = new ServerSocket();
                    ss.setReuseAddress(true);
                    ss.bind(new InetSocketAddress(server_port));
                    net_server.setServer_socket(ss);
                    while (!ss.isClosed()) {
                        Socket incoming = ss.accept();
                        // Pre-auth anti-DoS: we only process the handshake if a slot is free. Once
                        // exhausted, we drop the connection WITHOUT spending a thread or EC keygen
                        // (the legitimate peer retries).
                        if (handshake_slots.tryAcquire()) {
                            if (!serverSocketHandler(incoming)) {
                                LOGGER.log(Level.FINE,
                                        "Handshake submission rejected while the executor is stopping");
                            }
                        } else {
                            LOGGER.log(Level.WARNING,
                                    "Handshake slots exhausted ({0}) — dropping an inbound connection (anti-DoS pre-auth flood)",
                                    MAX_CONCURRENT_HANDSHAKES);
                            try {
                                incoming.close();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (IOException ex) {
                    if (net_server.getServer_socket() == null) {
                        exit = true;
                        mostrarMensajeError(THIS,
                                Translator.translate("conn.server_socket_bind_failed"));
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    // Back off so an unexpected accept() failure that recurs immediately can't spin
                    // this loop at ~100% CPU (the IOException/bind path above already sets exit).
                    Helpers.parkThreadMillis(1000);
                }
            }
            if (upnp) {
                Helpers.UPnPClose(server_port);
            }
            if (GameFrame.getInstance() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()) {
                invalidateSession();
                Helpers.GUIRun(() -> {
                    // On cancel, reopen the launch screen at the same spot and size (or
                    // maximized if it was) on the screen the waiting room is on.
                    Helpers.showFrameOnScreen(Init.VENTANA_INICIO, getGraphicsConfiguration(),
                            Init.LAUNCH_FRAME_SIZE, Init.LAUNCH_FRAME_MAXIMIZED);
                    avatar_io.close();
                    dispose();
                    // Release the singleton on this return-to-menu path (room cancelled / bind
                    // failed): otherwise getInstance() keeps handing out a DISPOSED frame and its
                    // whole NetServer/participants/chat graph lingers until the next room opens.
                    if (THIS == WaitingRoomFrame.this) {
                        THIS = null;
                    }
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

    @Override
    public void setVisible(boolean visible) {
        // Enforce the rule at the window boundary too: even a future direct caller
        // must not be able to reopen the waiting-room chat during a running game.
        if (visible && partida_empezada) {
            return;
        }
        super.setVisible(visible);
    }

    /**
     * Telemetry: latest snapshot received from the host (lat1/lat2/recon per
     * peer). Can be null if none has been received yet. Readers must tolerate
     * null and missing map entries (a peer that just joined has not been
     * measured yet).
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
                VoiceNotesViewerDialog.refreshIfOpen();
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
                    VoiceNotesViewerDialog.refreshIfOpen();
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

    /**
     * CLIENT → host: one stats-sync message over an encrypted TYPE_DB binary
     * frame.
     */
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
     * in-flight push can stop promptly instead of churning the remaining
     * batches.
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

    /**
     * HOST: nicks of the currently connected (non-CPU) clients.
     */
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

                    // This forces the JList to repaint
                    model.set(i, p);
                    break;
                }
            }
        });
    }

    /**
     * Removes a participant. Delegates to NetServer (state + DELUSER broadcast
     * + UI callback). Kept as a facade for external callers (Participant.java).
     *
     * NOTE: used both by the host (Participant.java when a client disconnects)
     * and by the client (on receiving DELUSER from the server). That's why the
     * logic lives here rather than in NetServer — the client has no net_server.
     * The DELUSER broadcast, which only applies to the host, is guarded by
     * isServer().
     */
    public synchronized void borrarParticipante(String nick) {
        // get + null-check instead of containsKey: on the host, its own entry is a
        // null placeholder by design (no local Participant), and a null here always
        // means "nothing to remove". This also closes the check-then-act gap against
        // NetServer's remove, which mutates the map outside this monitor.
        Participant pToDel = participantes.get(nick);

        if (pToDel == null) {
            return;
        }

        if (GameFrame.saleSonidoOn()) {
            Audio.playWavResource("misc/toilet.wav");
        }

        String avatar_src = pToDel.getAvatar_chat_src();

        participantes.remove(nick);

        onParticipantRemoved(nick, avatar_src,
                !isPartida_empezada() && avatar_io.owns(pToDel.getAvatar()) ? pToDel.getAvatar() : null);

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

    private File decodeRemoteAvatar(String encoded, String nick, String source) {
        try {
            return avatar_io.decodeValidateStore(encoded);
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Rejected remote avatar for {0} via {1}: {2}",
                    new Object[]{nick, source, ex.getMessage()});
            return null;
        }
    }

    private synchronized RemoteRosterAdmission admitRemoteRosterParticipant(String nick, String encodedAvatar,
            boolean cpu, boolean unsecure, String source) {
        boolean exactDuplicate = participantes.containsKey(nick);
        RemoteRosterAdmission admission = remoteRosterAdmission(participantes.size(),
                exactDuplicate, !exactDuplicate && nickCollisionNFC(nick));
        if (admission != RemoteRosterAdmission.ADMIT) {
            return admission;
        }

        File avatar = decodeRemoteAvatar(encodedAvatar, nick, source);
        nuevoParticipanteRemoto(nick, avatar, null, null, null, cpu, unsecure);
        return RemoteRosterAdmission.ADMIT;
    }

    private void rejectRemoteRoster(String nick, String source) {
        LOGGER.log(Level.SEVERE,
                "Rejected impossible remote roster entry for {0} via {1}; closing host channel",
                new Object[]{nick, source});
        closeClientSocket();
    }

    boolean isOwnedRemoteAvatar(File avatar) {
        return avatar_io.owns(avatar);
    }

    File writeRemoteAvatarThumbnail(File avatar, java.awt.image.BufferedImage thumbnail) throws IOException {
        return avatar_io.writeThumbnail(avatar, thumbnail);
    }

    private void nuevoParticipanteRemoto(String nick, File avatar, Socket socket, SecretKeySpec aes_k,
            SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {
        boolean adopted = false;
        try {
            nuevoParticipante(nick, avatar, socket, aes_k, hmac_k, cpu, unsecure);
            adopted = true;
        } finally {
            if (!adopted) {
                avatar_io.deleteOwned(avatar);
            }
        }
    }

    /**
     * Adds a participant.
     *
     * NOTE: used both by the host (serverSocketHandler when accepting a new
     * client, with a non-null socket) and by the client (registering the server
     * and itself in the local list when the client receives the room info, with
     * a null socket). That's why the logic lives here rather than in NetServer.
     */
    private synchronized void nuevoParticipante(String nick, File avatar, Socket socket, SecretKeySpec aes_k,
            SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(this, nick, avatar, socket, aes_k, hmac_k, cpu);

        participantes.put(nick, participante);
        participante.setUnsecure_player(unsecure);

        // Only the host starts the Participant's thread (non-null socket -> a real connection)
        if (socket != null) {
            Helpers.threadRun(participante);
        }

        onParticipantAdded(nick, avatar, cpu);
    }

    /**
     * NetServer callback when a Participant is added: updates the UI (connected
     * list, counter, and chat notification).
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
     * NetServer callback when a Participant is removed: updates the UI (removes
     * it from the list, adjusts the counter and buttons, notes the exit in
     * chat).
     */
    public void onParticipantRemoved(String nick, String avatar_chat_src) {
        onParticipantRemoved(nick, avatar_chat_src, null);
    }

    private void onParticipantRemoved(String nick, String avatar_chat_src, File avatarToDelete) {
        final String exitAvatarSrc = avatarToDelete == null
                ? avatar_chat_src : getClass().getResource("/images/avatar_default_chat.png").toExternalForm();
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

            if (avatarToDelete != null) {
                // Existing chat bubbles still reference this participant's thumbnail.
                // Replace that URL in the current document before deleting it. This keeps
                // join/leave notices (which are HTML-only, not part of chat_text) intact.
                String currentHtml = chat.getText();
                String safeHtml = currentHtml.replace(avatar_chat_src, exitAvatarSrc);
                if (safeHtml.equals(currentHtml)) {
                    // Defensive fallback for an editor that canonicalized the file URL.
                    safeHtml = "<html><body style='background-image: url(" + background_chat_src + ")'>"
                            + (chat_text.toString().isEmpty() ? "" : txtChat2HTML(chat_text.toString()))
                            + "</body></html>";
                }
                chat.setText(safeHtml);
                avatar_io.deleteAvatarArtifacts(avatarToDelete);
            }

            chatHTMLAppendExitUser(nick, exitAvatarSrc);
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
        game_info_recover = new javax.swing.JLabel();
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
        main_scroll_panel.setPreferredSize(new java.awt.Dimension(Math.round(700 * Helpers.DIALOG_ZOOM), Math.round(750 * Helpers.DIALOG_ZOOM)));

        // No fixed preferred height (was 700x487): panel_arriba sizes to its real CONTENT via the
        // main_panel constraint. So the host (with the ADD BOT / START buttons) and the client
        // (which hides them) each use their own height, and the client no longer drags a grey gap
        // from the leftover. Width is fixed by the horizontal constraint (688), not this preferred.
        status.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        status.setForeground(new java.awt.Color(51, 153, 0));
        status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        status.setDoubleBuffered(true);

        settings_icon.setToolTipText("Ajustes");
        settings_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        settings_icon.setDoubleBuffered(true);
        settings_icon.setPreferredSize(new java.awt.Dimension(Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM)));
        settings_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                settings_iconMouseClicked(evt);
            }
        });

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText(Helpers.wrapToolTip("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)"));
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setDoubleBuffered(true);
        sound_icon.setPreferredSize(new java.awt.Dimension(Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM)));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
                                        .addComponent(panel_conectados, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(376 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)))
        );
        panel_conLayout.setVerticalGroup(
                panel_conLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panel_conLayout.createSequentialGroup()
                                .addComponent(panel_conectados, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(328 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                game_info_blindsMouseClicked(evt);
            }
        });

        game_info_hands.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_hands.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        game_info_hands.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_hands.setDoubleBuffered(true);
        game_info_hands.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                game_info_handsMouseClicked(evt);
            }
        });

        logo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        logo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        logo.setDoubleBuffered(true);
        logo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                logoMouseClicked(evt);
            }
        });

        game_info_buyin.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_buyin.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1202.png"))); // NOI18N
        game_info_buyin.setText(" ");
        game_info_buyin.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_buyin.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                game_info_buyinMouseClicked(evt);
            }
        });

        game_info_recover.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_recover.setText("CONTINUANDO TIMBA ANTERIOR");
        game_info_recover.putClientProperty("i18n.key", "game.continuando_timba_anterior");
        game_info_recover.setOpaque(true);
        game_info_recover.setBackground(java.awt.Color.YELLOW);
        game_info_recover.setVisible(false);

        pass_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lock.png"))); // NOI18N
        Helpers.setTranslatedToolTip(pass_icon, "tooltip.manage_password");
        pass_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pass_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
                                        .addComponent(server_address_label, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(39 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(tot_conectados)
                                        .addComponent(pass_icon, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(36 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                                        .addComponent(game_info_recover)
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
                                        .addComponent(game_info_hands, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(32 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(game_info_buyin))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(game_info_recover)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(new_bot_button, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(84 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
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
                                .addComponent(empezar_timba, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(60 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(barra, javax.swing.GroupLayout.PREFERRED_SIZE, Math.round(20 * Helpers.DIALOG_ZOOM), javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(Math.round(8 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE))
        );

        empezar_timba.putClientProperty("i18n.key", "ui.a_jugar");

        danger_server.setBackground(new java.awt.Color(255, 0, 0));
        danger_server.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        danger_server.setForeground(new java.awt.Color(255, 255, 255));
        danger_server.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger_server.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/danger.png"))); // NOI18N
        Helpers.setTranslatedText(danger_server, "ui.posible_servidor_tramposo");
        danger_server.setBorder(javax.swing.BorderFactory.createEmptyBorder(Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM), Math.round(10 * Helpers.DIALOG_ZOOM)));
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                send_labelMouseClicked(evt);
            }
        });

        max_min_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        max_min_label.setDoubleBuffered(true);
        max_min_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
                                .addGap(Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM))
                                .addComponent(chat_box)
                                .addGap(Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM), Math.round(8 * Helpers.DIALOG_ZOOM))
                                .addComponent(send_label)
                                .addGap(0, 0, 0)
                                .addComponent(max_min_label))
        );
        chat_box_panelLayout.setVerticalGroup(
                chat_box_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(emoji_button, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(43 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
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
            public void mouseReleased(java.awt.event.MouseEvent evt) {
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
                                        .addComponent(panel_arriba, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(688 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
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
                                .addGap(Math.round(1 * Helpers.DIALOG_ZOOM), Math.round(1 * Helpers.DIALOG_ZOOM), Math.round(1 * Helpers.DIALOG_ZOOM))
                                .addComponent(danger_server)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(panel_arriba, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chat_notifications)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chat_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(22 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
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
                                .addComponent(main_scroll_panel, javax.swing.GroupLayout.DEFAULT_SIZE, Math.round(784 * Helpers.DIALOG_ZOOM), Short.MAX_VALUE)
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

                // Replace the password with a STRONG random one (CSPRNG + rich alphabet) —
                // the previous genRandomString only used a-z with a pseudo-random Random,
                // 47 bits at length=10.
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
                    // The new password only goes to WHOEVER REMAINS (the kicked player is
                    // already off the list, so they never find out).
                    difundirNuevaPassword();

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

            // Read the recover "missing players" OFF the EDT: it touches the shared SQLite
            // connection, which must be read under SQL_LOCK — and SQL_LOCK must never be requested
            // from the EDT. Disable "Start" first so a second click can't slip in while we read
            // off-EDT, snapshot the participant nicks on the EDT (participantes is not a concurrent
            // map), then resume the start flow on the EDT via continueStartGame with the result.
            this.empezar_timba.setEnabled(false);
            final java.util.HashSet<String> present_nicks;
            synchronized (participantes) {
                present_nicks = new java.util.HashSet<>(participantes.keySet());
            }
            if (Helpers.threadRun(() -> {
                try {
                    String missing_players = "";

                    if (GameFrame.RECOVER) {
                        int game_id = GameFrame.RECOVER_ID;
                        String sql = "SELECT preflop_players as PLAYERS FROM hand WHERE hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand where hand.id_game=?)";

                        synchronized (GameFrame.SQL_LOCK) {
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
                                        if (!"".equals(nick) && !present_nicks.contains(nick)) {
                                            missing_players += nick + "\n\n";
                                        }
                                    }
                                }
                            } catch (SQLException | UnsupportedEncodingException ex) {
                                LOGGER.log(Level.SEVERE, null, ex);
                            }
                        }
                    }

                    final String missing_players_final = missing_players;
                    Helpers.GUIRun(() -> continueStartGame(missing_players_final));
                } catch (Throwable t) {
                    // Any UNEXPECTED failure while reading the recover data (e.g. a null PLAYERS
                    // column NPE, not caught by the SQL/encoding catch above) must not leave "Start"
                    // stuck disabled: re-enable it so the host can retry. Mirrors the old EDT path,
                    // where the button was only disabled AFTER this read had already succeeded.
                    LOGGER.log(Level.SEVERE, "empezar_timba: failed to prepare game start", t);
                    Helpers.GUIRun(() -> this.empezar_timba.setEnabled(true));
                }
            }) == null) {
                // Pool shutting down (teardown): the read will never run — re-enable Start to retry.
                this.empezar_timba.setEnabled(true);
            }
        } else {
            chat_box.requestFocus();
        }
        revalidate();
        repaint();

    }//GEN-LAST:event_empezar_timbaActionPerformed

    /**
     * EDT continuation of the "Start game" flow, once the recover "missing
     * players" list has been read off the EDT. Confirms (when there are missing
     * players), then kicks off the game exactly as before; on cancel it
     * re-enables the "Start" button so the host can retry.
     */
    private void continueStartGame(String missing_players) {
        // Re-filter the "missing players" against a FRESH participant snapshot: the list was computed
        // off the EDT (before/while reading the recover data), so a player may have (re)joined in the
        // meantime — recompute who is still absent right before prompting, so the warning isn't stale.
        if (!missing_players.isEmpty()) {
            java.util.HashSet<String> present_now;
            synchronized (participantes) {
                present_now = new java.util.HashSet<>(participantes.keySet());
            }
            StringBuilder still_missing = new StringBuilder();
            for (String n : missing_players.split("\n\n")) {
                if (!n.isEmpty() && !present_now.contains(n)) {
                    still_missing.append(n).append("\n\n");
                }
            }
            missing_players = still_missing.toString();
        }

        boolean vamos = ("".equals(missing_players) || mostrarMensajeInformativoSINO(this,
                missing_players + Translator.translate("game.reconexion_pendiente"),
                new ImageIcon(Init.class.getResource("/images/action/timeout.png"))) == 0);

        if (vamos) {
            // Snapshot the EXACT pre-start UI state so a catastrophic start failure (GameFrame
            // construction / AJUGAR throwing) or a pool-teardown null can restore it, instead of
            // stranding the room on the "initializing" spinner forever with no feedback. Captured
            // BEFORE the mutations below so we restore precisely what was there. (empezar_timba is
            // the exception -- see its revert below.)
            final boolean prev_newbot_enabled = this.new_bot_button.isEnabled();
            final boolean prev_newbot_visible = this.new_bot_button.isVisible();
            final boolean prev_kick_enabled = this.kick_user.isEnabled();
            final boolean prev_kick_visible = this.kick_user.isVisible();
            final boolean prev_sound_visible = this.sound_icon.isVisible();
            final boolean prev_barra_visible = this.barra.isVisible();
            final String prev_status_text = this.status.getText();
            final javax.swing.Icon prev_status_icon = this.status.getIcon();
            final String prev_title = getTitle();
            final Runnable revert = () -> {
                partida_empezando = false;
                setTitle(prev_title);
                // Recompute instead of restoring a snapshot: empezar_timba was already disabled by
                // empezar_timbaActionPerformed before this flow ran, so its snapshot would always be
                // false and restoring it would leave "Start" stuck disabled after a failed start --
                // the very case this revert exists for. Same resting rule as the join path (:4507).
                this.empezar_timba.setEnabled(participantes.size() > 1);
                this.new_bot_button.setEnabled(prev_newbot_enabled);
                this.new_bot_button.setVisible(prev_newbot_visible);
                this.kick_user.setEnabled(prev_kick_enabled);
                this.kick_user.setVisible(prev_kick_visible);
                this.sound_icon.setVisible(prev_sound_visible);
                this.barra.setVisible(prev_barra_visible);
                this.status.setText(prev_status_text);
                this.status.setIcon(prev_status_icon);
                revalidate();
                repaint();
            };

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
            // Reflow after the above visibility/text changes (the read is now off-EDT, so the
            // method-level revalidate/repaint no longer runs after these mutations).
            revalidate();
            repaint();

            // Mirror the empezar_timba sibling's failure contract: the actual game start runs off
            // the EDT, so a throw inside it -- or a pool-teardown null return -- must revert the
            // "starting" UI and tell the host, never freeze the room silently. Only revert if the
            // game did NOT actually start (partida_empezada stays false until GameFrame is built):
            // once it's up, tearing the waiting room back down would be worse than the error.
            if (Helpers.threadRun(() -> {
                try {
                    startGameWork();
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "continueStartGame: game start failed", t);
                    if (!partida_empezada) {
                        Helpers.GUIRun(() -> {
                            revert.run();
                            mostrarMensajeError(this, Translator.translate("game.no_se_ha_podido_iniciar_timba"));
                        });
                    }
                }
            }) == null) {
                // Pool shutting down (teardown): the start will never run -- revert the UI.
                revert.run();
            }
        } else {
            this.empezar_timba.setEnabled(true);
        }
    }

    // Off-EDT game-start work: waits for pending pre-game client commands to drain, then builds the
    // GameFrame on the EDT and kicks off play. Extracted from continueStartGame so that dispatch can
    // wrap it in a try/catch that reverts the "starting" UI on failure. Runs on a THREAD_POOL
    // thread; the body is unchanged from the original inline version.
    private void startGameWork() {
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
                            Thread.currentThread().interrupt();
                            throw new java.util.concurrent.CancellationException(
                                    "waiting-room start cancelled during table teardown");
                        }
                    }
                }
            } while (ocupados);

            // new GameFrame() runs on the EDT via GUIRunAndWait, which SWALLOWS (logs, never
            // rethrows) the InvocationTargetException that invokeAndWait wraps a runnable throw in.
            // Capture a construction failure explicitly INSIDE the runnable: otherwise a throw in
            // new GameFrame(...) would be lost here and execution would fall through to
            // partida_empezada=true / AJUGAR() on a half-built singleton, freezing the room
            // silently. With the capture, we rethrow below (before partida_empezada flips) so
            // continueStartGame's dispatch catch reverts the "starting" UI and notifies the host.
            final Throwable[] build_error = new Throwable[1];
            Helpers.GUIRunAndWait(() -> {
                try {
                    // Defensive (on the host the settings wheel is modal and blocks
                    // "Start", so it normally wouldn't be open): close without asking.
                    SettingsDialog.closeIfOpen();
                    new GameFrame(WaitingRoomFrame.this, local_nick, true);
                } catch (Throwable t) {
                    build_error[0] = t;
                }
            });
            if (build_error[0] != null) {
                // A construction that threw partway left a half-built GameFrame published in the
                // singleton (THIS is set first thing in the constructor). Clear it so getInstance()
                // reports "no game" while the room recovers, instead of exposing a broken instance
                // to the getInstance()!=null checks elsewhere.
                GameFrame.clearFailedInstance();
                throw new RuntimeException("GameFrame construction failed", build_error[0]);
            }
            partida_empezada = true;
            Helpers.GUIRunAndWait(() -> setVisible(false));
            GameFrame.getInstance().AJUGAR();
        }
    }

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing

        if (!barra.isVisible() || !booting) {

            // client_threads only exists on NetServer; doesn't apply on the client (defaults to vacuously "true")
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
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // Opens the settings dialog in general mode (Appearance + Sound): there's no
        // GameFrame in the waiting room, so the Game tab isn't mounted.
        SettingsDialog.open(this);
    }

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked

        // evt is null when the sound toggle is invoked programmatically (keyboard
        // shortcuts); in that case there's no real click to validate.
        if (evt != null && !Helpers.isRealClick(evt)) {
            return;
        }

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.setScaledIconLabel(sound_icon,
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM));

        if (!GameFrame.SONIDOS) {

            Audio.muteAll();

        } else {

            Audio.unmuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoMouseClicked

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_new_bot_buttonActionPerformed
        if (participantes.size() < MAX_PARTICIPANTES) {
            new_bot_button.setEnabled(false);
            if (GameFrame.entraSonidoOn()) {
                Audio.playWavResource("misc/laser.wav");
            }

            Helpers.threadRun(() -> {
                try {
                    byte[] avatar_b = null;
                    try (java.io.InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                        if (is != null) {
                            avatar_b = is.readAllBytes();
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Failed to load bot avatar", ex);
                    }

                    synchronized (lock_new_client) {
                        // Capacity is checked AGAIN HERE, same as its twin for a client join: the
                        // button's check ran before waiting for the turn, and in that gap someone
                        // could have joined over the network. Going over capacity leaves the room
                        // without enough seats for everyone and hangs when the game starts. The
                        // nick is also chosen here, or two simultaneous joins could end up with
                        // the same one.
                        if (participantes.size() >= MAX_PARTICIPANTES) {
                            LOGGER.log(Level.WARNING,
                                    "Table filled up while adding a bot ({0} participants) — not adding it",
                                    participantes.size());
                            Helpers.GUIRun(() -> {
                                new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                            });
                            return;
                        }

                        String bot_nick;
                        int conta_bot = 0;
                        do {
                            conta_bot++;
                            bot_nick = "CoronaBot$" + String.valueOf(conta_bot);
                        } while (participantes.get(bot_nick) != null);

                        String comando = "NEWUSER#" + Base64.getEncoder().encodeToString(bot_nick.getBytes("UTF-8")) + "#0";
                        comando += "#" + (avatar_b != null ? Base64.getEncoder().encodeToString(avatar_b) : "*");

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

        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

        if (!server || WaitingRoomFrame.getInstance().isPartida_empezada()) {
            return;
        }

        if (javax.swing.SwingUtilities.isRightMouseButton(evt)) {
            // Right click -> context menu with 3 options.
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

        // Left click -> shortcut: copy the current one to the clipboard (silent, brief
        // message). If there's no password, generate a strong one.
        if (password != null) {
            copyCurrentPasswordToClipboard();
        } else {
            generateAndShowStrongPassword();
        }
    }

    /**
     * Shortcut for the left click and the menu's "Copy password" item: copies
     * the current password to the clipboard + a brief popup.
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
     * Menu's "Change password" item: prompts the user for a new password with a
     * JPasswordField that changes color (weak yellow / strong green) live,
     * matching NewGameDialog. If the result has &lt;60 bits of entropy, an
     * informational popup (non-blocking). Empty input -> game without a
     * password.
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
            // Clearing it must also be announced: without a password the channel is derived
            // a different way, so anyone left with the old one couldn't rejoin.
            difundirNuevaPassword();
            pass_icon.setEnabled(false);
            pass_icon.setToolTipText(null);
            mostrarMensajeInformativo(this,
                    Translator.translate("auth.password_eliminada"));
            return;
        }
        password = trimmed;
        difundirNuevaPassword();
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
     * Broadcasts the room's CURRENT password (or the notice that there is none)
     * to everyone still connected.
     *
     * <p>
     * Called from all FOUR places that change it: kicking someone, changing it
     * by hand, generating a strong one, and clearing it. Without this the
     * others are left with the old one and, since the channel is derived from
     * it, the first one to have their network drop would be locked out and
     * unable to rejoin. Clearing it counts the same way: without a password the
     * channel is derived a different way, so it also needs to be announced (the
     * "*" sentinel travels for that case).
     *
     * <p>
     * ALWAYS runs on a separate thread: writing to a peer waits while that peer
     * is reconnecting, and three of the four callers come from the EDT (the
     * lock menu), so doing it there would freeze the whole room. Same reason
     * the session identicon was moved off the EDT at the top of this class.
     */
    private void difundirNuevaPassword() {

        // The version and the list snapshot are taken TOGETHER, under the map's monitor
        // (its own, since it's a synchronized map). Taking them separately let the
        // opposite order slip through: the higher version carrying the older list, so
        // the broadcast that wins is the one that knows about fewer people.
        final long version;
        final java.util.ArrayList<Participant> destinatarios;

        synchronized (participantes) {
            version = password_version.incrementAndGet();
            destinatarios = new java.util.ArrayList<>(participantes.values());
        }

        Helpers.threadRun(() -> {
            // Two changes in a row can end up delivered out of order, and whoever is left
            // with the old one can't rejoin once their network drops, because the channel
            // is derived from it. Each Participant takes care of that on its own (see
            // writeRoomPassword): the change number is recorded inside its own lock, so
            // the order stays correct on each socket without one stuck peer holding up
            // the broadcast to everyone else.
            //
            // This early-out here is just to avoid a wasted trip: if a newer change
            // already landed while we were waiting, that one broadcasts to everyone and
            // this one has nothing to do.
            if (version != password_version.get()) {
                return;
            }

            final String actual = password;
            final String payload;

            if (actual == null) {
                payload = "*";
            } else {
                try {
                    payload = Base64.getEncoder().encodeToString(actual.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    return;
                }
            }

            // Each peer on its own thread. Writing to one can stall for quite a while
            // (while it's reconnecting, or stuck behind a voice note holding its socket's
            // outgoing turn), and in a one-by-one loop that stuck peer would hold up
            // everyone behind it: they'd be left without the new password for exactly
            // those tens of seconds, and anyone who dropped during that window could no
            // longer rejoin. Order isn't lost by broadcasting in parallel: each
            // Participant guarantees it with its own lock (see writeRoomPassword).
            for (Participant resto : destinatarios) {
                if (resto != null && !resto.isCpu() && !resto.getNick().equals(local_nick)) {
                    Helpers.threadRun(() -> resto.writeRoomPassword(version, payload));
                }
            }
        });
    }

    /**
     * Menu's "Generate strong password" item (and shortcut when there's no
     * password yet). Uses CSPRNG + a rich alphabet — ~86 bits at length=14.
     */
    private void generateAndShowStrongPassword() {
        password = Helpers.genStrongPassword(GEN_PASS_LENGTH);
        difundirNuevaPassword();
        pass_icon.setEnabled(true);
        pass_icon.setToolTipText(password);
        Helpers.copyTextToClipboard(password);
        mostrarMensajeInformativo(this,
                Translator.translate("auth.nueva_password_generada", password));
    }
    //GEN-LAST:event_pass_iconMouseClicked

    private void tts_warningMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tts_warningMouseClicked

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        mostrarMensajeInformativo(this,
                Translator.translate("ui.tts_warning_detail"),
                "justify", (int) Math.round(getWidth() * 0.8f));
    }//GEN-LAST:event_tts_warningMouseClicked

    private void server_address_labelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_server_address_labelMouseClicked

        if (!Helpers.isRealClick(evt)) {
            return;
        }

        if (server) {
            // The socket might not exist yet (or the port might have failed to open):
            // since it's published AFTER being bound, this must be checked here.
            if (net_server.getServer_socket() == null) {
                return;
            }
            int port = net_server.getServer_socket().getLocalPort();
            Helpers.copyTextToClipboard("[CoronaPoker] INTERNET -> " + Helpers.getMyPublicIP() + ":"
                    + String.valueOf(port) + "\n\nLAN -> " + Helpers.getMyLocalIP() + ":"
                    + String.valueOf(port));
            mostrarMensajeInformativo(this, Translator.translate("conn.datos_de_conexion_copiados_en"));
        }
    }//GEN-LAST:event_server_address_labelMouseClicked

    // Paints the game's characteristics in the waiting room from the gameinfo
    // "BUYIN|BLINDS|HANDS". If the buyin field is the variable-buyin tag, hides the
    // pot (each player picks their own buy-in) while keeping blinds and hands.
    // Numeric -> normal buyin. Non-numeric -> the (existing) recover case.
    private void applyGameInfoBuyinLabel(String[] game_info) {
        String buyin_field = game_info[0].trim();
        if (VARIABLE_BUYIN_TAG.equals(buyin_field)) {
            // Text = tag (even though hidden): this is how it travels in the
            // NICKOK/GAMEINFO payload, which is rebuilt from game_info_buyin.getText().
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
        if (!Helpers.isRealClick(evt)) {
            return;
        }
        // DISABLED: the room's game config is now edited from the settings wheel
        // (SettingsDialog -> Game tab), not by clicking the blinds. The listener stays
        // registered in the .form but the handler is a no-op (the buyin/blinds/hands
        // labels all delegate to this method, so all three are now inert).
    }//GEN-LAST:event_game_info_buyinMouseClicked

    // Refreshes the room's buyin/blinds/hands labels from GameFrame.* and broadcasts the
    // GAMEINFO (display) + GAMECONFIG (FULL mirror) to clients. Called by the settings
    // wheel's Game tab panel on SAVE (host only). Replaces the broadcast the old blinds
    // click used to do.
    public void broadcastGameConfigAndLabels() {

        final String[] payload = new String[1];

        Helpers.GUIRunAndWait(() -> {
            if (GameFrame.FIXED_BUYIN) {
                game_info_buyin.setVisible(true);
                game_info_buyin.setText(Helpers.money2String(GameFrame.BUYIN) + (GameFrame.REBUY ? "" : "*"));
            } else {
                // Variable: the pot doesn't apply. The tag travels in the GAMEINFO via getText().
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

            // Capture the display payload ON THE EDT (never read Swing off it).
            payload[0] = game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|"
                    + game_info_hands.getText();

            pack();
            Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                    (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth());
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
        if (!Helpers.isRealClick(evt)) {
            return;
        }
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
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), Math.round(30 * Helpers.DIALOG_ZOOM), Math.round(30 * Helpers.DIALOG_ZOOM));

        if (!chat_text.toString().isEmpty()) {
            refreshChatPanel();
        }

        main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());

        chat_box.requestFocus();

        revalidate();

        repaint();

        if (isPartida_empezada()) {
            // While the game is running, the JTextPane keeps receiving
            // HTMLEditorKitAppend even while the window is hidden. The <img>s resolve
            // their bitmap via ImageObserver and fire preferenceChanged, but since the
            // component isn't displayable the relayout cascade never recomputes the
            // RoundedBubbleView allocations -> image bubbles end up with stale geometry
            // when the chat is reopened from the in-game menu.
            //
            // Exact mimic of what chatMouseClicked does (the event users used to
            // trigger by hand to "fix" the rendering): briefly flipping the scrollpane's
            // policy from NEVER to AS_NEEDED forces JScrollPane to recompute its layout,
            // the viewport hands the chat a new setSize with a possible width change as
            // the scrollbar shows/hides, and the HTMLDocument relays its view tree with
            // the real, now-resolved sizes. After a second invokeLater we restore the
            // original policy so no bars are left visible in case chatFocusLost doesn't
            // cover it. Calling setSize on chat directly doesn't work here: inside a
            // JScrollPane the viewport overrides the size from its extent and the
            // HTMLDocument never gets invalidated.
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
        if (!Helpers.isRealClick(evt)) {
            return;
        }
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

        if (!Helpers.isReleaseInsideComponent(evt)) {
            return;
        }

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
        // The waiting room is a normal window: it doesn't cling to focus. During the
        // game, focus is managed by GameFrame; the in-game chat is FastChat. (This used
        // to hide and re-show itself to steal focus back, which fought with GameFrame
        // and caused erratic focus behavior.)
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
    // Indicador "Continuando timba anterior" en su propia fila, debajo de buy-in/ciegas; solo
    // visible al recuperar (el buy-in y las ciegas muestran los valores recuperados como siempre).
    private javax.swing.JLabel game_info_recover;
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
