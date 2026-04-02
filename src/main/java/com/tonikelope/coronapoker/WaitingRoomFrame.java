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
import javax.swing.JFrame;
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
import static com.tonikelope.coronapoker.InGameNotifyDialog.NOTIFICATION_TIMEOUT;
import static com.tonikelope.coronapoker.Init.DEV_MODE;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

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
    public static final long PING_INTERVAL_MS = 5000;
    public static final long SEC_PING_INTERVAL_MS = 15000;
    public static final int PRE_GAME_COMMANDS_LOCK = 15000;
    public static final int EC_KEY_LENGTH = 256;
    public static final int GEN_PASS_LENGTH = 10;
    public static final int CLIENT_REC_WAIT = 5;
    public static final int ANTI_FLOOD_CHAT = 1000;
    public static volatile boolean CHAT_GAME_NOTIFICATIONS = Boolean
            .parseBoolean(Helpers.PROPERTIES.getProperty("chat_game_notifications", "true"));
    private static volatile WaitingRoomFrame THIS = null;

    private final File local_avatar;
    private final Map<String, Participant> participantes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, byte[]> localP2POriginalNonces = new ConcurrentHashMap<>();
    public final P2PSwarmManager p2pSwarmManager = new P2PSwarmManager();
    private final Object local_client_socket_lock = new Object();
    private final Object ping_pong_lock = new Object();
    private final Object lock_new_client = new Object();
    private final Object lock_reconnect = new Object();
    private final Object lock_client_reconnect = new Object();
    private final Object lock_client_pre_game_commands_wait = new Object();
    private final HashMap<String, Integer> cliente_last_received = new HashMap<>();
    private final boolean server;
    private final String local_nick;
    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> client_threads = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> late_clients_warning = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> local_client_socket_reader_queue = new LinkedBlockingQueue<>();
    private volatile ServerSocket server_socket = null;
    private volatile SecretKeySpec local_client_aes_key = null;
    private volatile SecretKeySpec local_client_hmac_key = null;
    private volatile SecretKeySpec local_client_hmac_key_orig = null;
    private volatile SecretKeySpec local_client_permutation_key = null;
    public volatile byte[] local_player_public_key = null;
    private volatile String local_client_permutation_key_hash = null;
    private volatile Socket local_client_socket = null;
    private volatile BufferedReader local_client_buffer_read_is = null;
    private volatile String server_ip_port;
    private volatile String server_nick;
    private volatile Reconnect2ServerDialog reconnect_dialog = null;
    private volatile boolean reconnecting = false;
    private volatile boolean unsecure_server = false;
    private volatile Integer remote_server_pong;
    private volatile Integer remote_server_pong2;
    private volatile String gameinfo_original = null;
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
    private volatile int remote_server_latency;
    private volatile int remote_server_latency2;

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

    public class P2PSwarmManager {

        private final Map<String, Map<String, String>> generatedChallenges = new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> solvedResponses = new ConcurrentHashMap<>();
        private java.util.concurrent.CountDownLatch challengesLatch;
        private java.util.concurrent.CountDownLatch responsesLatch;
        private java.util.concurrent.CountDownLatch verifyDoneLatch;

        private void sendP2PCommandToServer(String command) {
            if (isServer()) {
                String[] partes = command.split("#");
                if (partes[0].equals("P2P_CHALLENGES")) {
                    receiveChallenges(local_nick, partes.length > 1 ? partes[1] : "*");
                } else if (partes[0].equals("P2P_RESPONSES")) {
                    receiveResponses(local_nick, partes.length > 1 ? partes[1] : "*");
                } else if (partes[0].equals("P2P_VERIFY_DONE")) {
                    receiveVerifyDone(local_nick);
                }
            } else {
                try {
                    writeCommandToServer(Helpers.encryptCommand(command, local_client_aes_key, local_client_hmac_key));
                } catch (Exception e) {
                }
            }
        }

        private void broadcastP2PCommandFromServer(String command, Participant skip) {
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);
            synchronized (participantes) {
                for (Participant p : participantes.values()) {
                    if (p != null && !p.isCpu() && !p.isExit() && p != skip) {
                        try {
                            p.writeCommandFromServer(Helpers.encryptCommand(command, p.getAes_key(), iv, p.getHmac_key()));
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }

        private void sendP2PCommandToClient(String command, Participant target) {
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);
            try {
                target.writeCommandFromServer(Helpers.encryptCommand(command, target.getAes_key(), iv, target.getHmac_key()));
            } catch (Exception e) {
            }
        }

        // --- LOGIC: SERVER SIDE ---
        public void startSwarm() {
            int humans = 0;
            int totalPlayers = 0;
            StringBuilder pubKeys = new StringBuilder();

            LOGGER.info("[ZERO-TRUST] >>> INITIATING P2P KEM SWARM <<<");

            synchronized (participantes) {
                for (String pNick : participantes.keySet()) {
                    Participant p = participantes.get(pNick);
                    totalPlayers++;

                    byte[] pk = null;
                    boolean isCpu = false;

                    if (pNick.equals(local_nick)) {
                        pk = local_player_public_key;
                        humans++;
                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Player [{0}] is HOST (HUMAN).", pNick);
                    } else if (p != null) {
                        pk = p.getPanoptes_public_key();
                        isCpu = p.isCpu();
                        if (!isCpu) {
                            humans++;
                            LOGGER.log(Level.INFO, "[ZERO-TRUST] Player [{0}] is HUMAN.", pNick);
                        } else {
                            LOGGER.log(Level.INFO, "[ZERO-TRUST] Player [{0}] is CPU (BOT).", pNick);
                        }
                    }

                    if (pk != null && pk.length == 32) {
                        try {
                            pubKeys.append(java.util.Base64.getEncoder().encodeToString(pNick.getBytes("UTF-8")))
                                    .append(":")
                                    .append(java.util.Base64.getEncoder().encodeToString(pk))
                                    .append("$");
                        } catch (Exception e) {
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "[ZERO-TRUST] WARNING: {0} lacks a valid Panoptes Public Key.", pNick);
                    }
                }
            }

            LOGGER.log(Level.INFO, "[ZERO-TRUST] Network Summary: {0} Humans, {1} Bots.", new Object[]{humans, (totalPlayers - humans)});

            if (humans < 1) {
                LOGGER.info("[ZERO-TRUST] Not enough humans to form a mesh. Skipping KEM verification...");
                finishSwarmAndStartGame();
                return;
            }

            challengesLatch = new java.util.concurrent.CountDownLatch(humans);
            responsesLatch = new java.util.concurrent.CountDownLatch(totalPlayers);
            verifyDoneLatch = new java.util.concurrent.CountDownLatch(humans);
            generatedChallenges.clear();
            solvedResponses.clear();

            LOGGER.info("[ZERO-TRUST] Broadcasting P2P_START command to the network...");
            String cmdPayload = "P2P_START#" + pubKeys.toString();
            broadcastP2PCommandFromServer(cmdPayload, participantes.get(local_nick));
            handleClientP2PStart(cmdPayload.split("#"));
        }

        public void receiveChallenges(String senderNick, String payload) {
            LOGGER.log(Level.INFO, "[ZERO-TRUST] Server received challenges from [{0}]", senderNick);
            Map<String, String> cmap = new HashMap<>();
            if (!payload.isEmpty() && !payload.equals("*")) {
                String[] chunks = payload.split("\\$");
                for (String chunk : chunks) {
                    String[] kv = chunk.split(":");
                    if (kv.length == 2) {
                        try {
                            cmap.put(new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8"), kv[1]);
                        } catch (Exception e) {
                        }
                    }
                }
            }
            generatedChallenges.put(senderNick, cmap);
            challengesLatch.countDown();
            if (challengesLatch.getCount() == 0) {
                routeInboxes();
            }
        }

        private void routeInboxes() {
            LOGGER.info("[ZERO-TRUST] All challenges received. Routing Inboxes...");
            synchronized (participantes) {
                for (String targetNick : participantes.keySet()) {
                    Participant targetP = participantes.get(targetNick);
                    if (!targetNick.equals(local_nick) && (targetP == null || targetP.isExit())) {
                        continue;
                    }

                    StringBuilder inbox = new StringBuilder();
                    for (String senderNick : generatedChallenges.keySet()) {
                        if (senderNick.equals(targetNick)) {
                            continue;
                        }
                        Map<String, String> senderMap = generatedChallenges.get(senderNick);
                        if (senderMap != null && senderMap.containsKey(targetNick)) {
                            try {
                                inbox.append(java.util.Base64.getEncoder().encodeToString(senderNick.getBytes("UTF-8"))).append(":").append(senderMap.get(targetNick)).append("$");
                            } catch (Exception e) {
                            }
                        }
                    }
                    String cmdPayload = "P2P_INBOX#" + (inbox.length() > 0 ? inbox.toString() : "*");

                    if (targetP != null && targetP.isCpu()) {
                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Server resolving KEM challenges internally for Bot [{0}]...", targetNick);
                        Helpers.threadRun(() -> {
                            Map<String, String> botResponses = new HashMap<>();
                            String[] chunks = inbox.toString().split("\\$");
                            for (String chunk : chunks) {
                                String[] kv = chunk.split(":");
                                if (kv.length == 2) {
                                    try {
                                        String sender = new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8");
                                        byte[] encChal = java.util.Base64.getDecoder().decode(kv[1]);
                                        Participant senderP = participantes.get(sender);
                                        byte[] senderPk = sender.equals(local_nick) ? local_player_public_key : (senderP != null ? senderP.getPanoptes_public_key() : null);

                                        if (senderPk != null && senderPk.length == 32 && targetP.getPanoptes_private_key() != null) {
                                            byte[] resp = Panoptes.getInstance().p2pSolveBotChallenge(encChal, senderPk, targetP.getPanoptes_private_key());
                                            if (resp != null) {
                                                botResponses.put(sender, java.util.Base64.getEncoder().encodeToString(resp));
                                            }
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            }
                            solvedResponses.put(targetNick, botResponses);
                            responsesLatch.countDown();
                            if (responsesLatch.getCount() == 0) {
                                routeVerifications();
                            }
                        });
                    } else if (targetNick.equals(local_nick)) {
                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Host (Server) [{0}] resolving its own incoming KEM challenges...", targetNick);
                        handleClientP2PInbox(cmdPayload.split("#"));
                    } else {
                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Dispatching KEM Inbox to remote human [{0}]", targetNick);
                        sendP2PCommandToClient(cmdPayload, targetP);
                    }
                }
            }
        }

        public void receiveResponses(String solverNick, String payload) {
            LOGGER.log(Level.INFO, "[ZERO-TRUST] Server received KEM responses from [{0}]", solverNick);
            Map<String, String> rmap = new HashMap<>();
            if (!payload.isEmpty() && !payload.equals("*")) {
                String[] chunks = payload.split("\\$");
                for (String chunk : chunks) {
                    String[] kv = chunk.split(":");
                    if (kv.length == 2) {
                        try {
                            rmap.put(new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8"), kv[1]);
                        } catch (Exception e) {
                        }
                    }
                }
            }
            solvedResponses.put(solverNick, rmap);
            responsesLatch.countDown();
            if (responsesLatch.getCount() == 0) {
                routeVerifications();
            }
        }

        private void routeVerifications() {
            LOGGER.info("[ZERO-TRUST] All responses received. Initiating CROSS-VERIFICATION phase...");
            synchronized (participantes) {
                for (String challengerNick : participantes.keySet()) {
                    Participant challengerP = participantes.get(challengerNick);
                    if (!challengerNick.equals(local_nick) && (challengerP == null || challengerP.isCpu() || challengerP.isExit())) {
                        continue;
                    }

                    StringBuilder verifications = new StringBuilder();
                    for (String solverNick : solvedResponses.keySet()) {
                        if (solverNick.equals(challengerNick)) {
                            continue;
                        }
                        Map<String, String> solverMap = solvedResponses.get(solverNick);
                        if (solverMap != null && solverMap.containsKey(challengerNick)) {
                            try {
                                verifications.append(java.util.Base64.getEncoder().encodeToString(solverNick.getBytes("UTF-8"))).append(":").append(solverMap.get(challengerNick)).append("$");
                            } catch (Exception e) {
                            }
                        }
                    }
                    String cmdPayload = "P2P_VERIFY#" + (verifications.length() > 0 ? verifications.toString() : "*");

                    if (challengerNick.equals(local_nick)) {
                        LOGGER.info("[ZERO-TRUST] Host (Server) auditing KEM memory attestations...");
                        handleClientP2PVerify(cmdPayload.split("#"));
                    } else {
                        LOGGER.log(Level.INFO, "[ZERO-TRUST] Dispatching VERIFY command to remote human [{0}]", challengerNick);
                        sendP2PCommandToClient(cmdPayload, challengerP);
                    }
                }
            }
        }

        public void receiveVerifyDone(String nick) {
            LOGGER.log(Level.INFO, "[ZERO-TRUST] Player [{0}] successfully completed local swarm verification.", nick);
            verifyDoneLatch.countDown();
            if (verifyDoneLatch.getCount() == 0) {
                LOGGER.info("[ZERO-TRUST] >>> SWARM VERIFICATION SUCCESSFUL. STARTING GAME. <<<");
                finishSwarmAndStartGame();
            }
        }

        private void finishSwarmAndStartGame() {
            Helpers.GUIRunAndWait(() -> new GameFrame(THIS, local_nick, true));
            partida_empezada = true;
            GameFrame.getInstance().AJUGAR();
        }

        // --- LOGIC: CLIENT SIDE ---
        public void handleClientP2PStart(String[] partes) {
            Helpers.threadRun(() -> {
                try {
                    LOGGER.info("[ZERO-TRUST CLIENT] Executing handleClientP2PStart...");
                    Helpers.GUIRun(() -> status.setText(Translator.translate("zero_trust.generating_kem")));
                    if (partes.length > 1 && !partes[1].equals("*")) {
                        String[] chunks = partes[1].split("\\$");
                        for (String chunk : chunks) {
                            String[] kv = chunk.split(":");
                            if (kv.length == 2) {
                                String n = new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8");
                                byte[] pk = java.util.Base64.getDecoder().decode(kv[1]);
                                Participant p = participantes.get(n);
                                if (p != null) {
                                    p.setPanoptes_public_key(pk);
                                }
                            }
                        }
                    }
                    StringBuilder batch = new StringBuilder();
                    synchronized (participantes) {
                        for (String targetNick : participantes.keySet()) {
                            if (targetNick.equals(local_nick)) {
                                continue;
                            }

                            Participant p = participantes.get(targetNick);
                            byte[] pk = (p != null) ? p.getPanoptes_public_key() : (targetNick.equals(server_nick) ? local_player_public_key : null);

                            if (pk != null && pk.length == 32) {
                                LOGGER.log(Level.INFO, "[ZERO-TRUST CLIENT] Generating KEM Challenge for [{0}]", targetNick);
                                byte[] chal = Panoptes.getInstance().p2pGenerateChallenge(pk);
                                if (chal != null && chal.length == 80) {
                                    localP2POriginalNonces.put(targetNick, java.util.Arrays.copyOfRange(chal, 0, 32));
                                    batch.append(java.util.Base64.getEncoder().encodeToString(targetNick.getBytes("UTF-8"))).append(":").append(java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(chal, 32, 80))).append("$");
                                }
                            }
                        }
                    }
                    sendP2PCommandToServer("P2P_CHALLENGES#" + (batch.length() > 0 ? batch.toString() : "*"));
                } catch (Exception e) {
                }
            });
        }

        public void handleClientP2PInbox(String[] partes) {
            Helpers.threadRun(() -> {
                try {
                    LOGGER.info("[ZERO-TRUST CLIENT] Executing handleClientP2PInbox (Solving incoming KEM challenges)...");
                    Helpers.GUIRun(() -> status.setText(Translator.translate("zero_trust.auditing_memory")));
                    StringBuilder batch = new StringBuilder();
                    if (partes.length > 1 && !partes[1].equals("*")) {
                        String[] chunks = partes[1].split("\\$");
                        for (String chunk : chunks) {
                            String[] kv = chunk.split(":");
                            if (kv.length == 2) {
                                String sender = new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8");
                                byte[] encChal = java.util.Base64.getDecoder().decode(kv[1]);
                                Participant p = participantes.get(sender);
                                byte[] pk = (p != null) ? p.getPanoptes_public_key() : (sender.equals(server_nick) ? local_player_public_key : null);

                                if (pk != null && pk.length == 32 && encChal.length == 48) {
                                    LOGGER.log(Level.INFO, "[ZERO-TRUST CLIENT] Solving KEM challenge from [{0}]", sender);
                                    byte[] resp = Panoptes.getInstance().p2pSolveChallenge(encChal, pk);
                                    if (resp != null) {
                                        batch.append(kv[0]).append(":").append(java.util.Base64.getEncoder().encodeToString(resp)).append("$");
                                    }
                                }
                            }
                        }
                    }
                    sendP2PCommandToServer("P2P_RESPONSES#" + (batch.length() > 0 ? batch.toString() : "*"));
                } catch (Exception e) {
                }
            });
        }

        public void handleClientP2PVerify(String[] partes) {
            Helpers.threadRun(() -> {
                try {
                    LOGGER.info("[ZERO-TRUST CLIENT] Executing handleClientP2PVerify (Auditing memory attestations)...");
                    Helpers.GUIRun(() -> status.setText(Translator.translate("zero_trust.verifying_swarm")));

                    boolean allClean = true;
                    if (partes.length > 1 && !partes[1].equals("*")) {
                        String[] chunks = partes[1].split("\\$");
                        for (String chunk : chunks) {
                            String[] kv = chunk.split(":");
                            if (kv.length == 2) {
                                String target = new String(java.util.Base64.getDecoder().decode(kv[0]), "UTF-8");
                                byte[] resp = java.util.Base64.getDecoder().decode(kv[1]);
                                Participant p = participantes.get(target);
                                byte[] pk = (p != null) ? p.getPanoptes_public_key() : (target.equals(server_nick) ? local_player_public_key : null);
                                byte[] origNonce = localP2POriginalNonces.get(target);

                                if (pk != null && pk.length == 32 && origNonce != null && origNonce.length == 32 && resp.length == 17) {
                                    boolean ok = Panoptes.getInstance().p2pVerifyResponse(pk, origNonce, resp);
                                    LOGGER.log(Level.INFO, "[ZERO-TRUST CLIENT] Verification result for [{0}]: {1}", new Object[]{target, (ok ? "CLEAN" : "CHEAT DETECTED")});
                                    if (!ok) {
                                        allClean = false;
                                        if (p != null) {
                                            p.setUnsecure_player(true); // Cambia el dato Y dispara el fondo rojo de la lista
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (allClean) {
                        sendP2PCommandToServer("P2P_VERIFY_DONE");
                    } else {
                        Helpers.GUIRun(() -> {
                            int r = mostrarMensajeErrorSINO(THIS, Translator.translate("zero_trust.cheater_detected"));

                            if (r == 0) {
                                // User chose to continue anyway despite the warning
                                sendP2PCommandToServer("P2P_VERIFY_DONE");
                            } else {
                                // User chose to abort the game start
                                if (isServer()) {

                                    // Restore UI controls that were disabled during startup
                                    empezar_timba.setEnabled(true);
                                    new_bot_button.setEnabled(participantes.size() < MAX_PARTICIPANTES);
                                    new_bot_button.setVisible(true);
                                    kick_user.setEnabled(true);
                                    kick_user.setVisible(true);
                                    sound_icon.setVisible(true);

                                    // Restore tooltips
                                    game_info_buyin.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));
                                    game_info_blinds.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));
                                    game_info_hands.setToolTipText(Translator.translate("update.click_para_actualizar_datos_de"));

                                    status.setText(Translator.translate("zero_trust.game_aborted"));
                                    status.setIcon(null);
                                    barra.setVisible(false);
                                    partida_empezando = false;
                                } else {
                                    setExit(true);
                                    closeClientSocket();
                                    System.exit(0);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[ZERO-TRUST CLIENT] Error in handleClientP2PVerify", e);
                }
            });
        }
    }

    public byte[] getLocal_player_public_key() {
        return local_player_public_key;
    }

    public int getServer_latency2() {
        return remote_server_latency2;
    }

    public int getServer_latency() {
        return remote_server_latency;
    }

    public String getPassword() {
        return password;
    }

    public Object getLock_client_pre_game_commands_wait() {
        return lock_client_pre_game_commands_wait;
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

    public JList<ParticipantJListData> getConectados() {
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
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

    }

    public void closeClientSocket() {

        if (local_client_socket != null) {
            try {
                local_client_socket.close();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void resetInstance() {
        THIS.late_clients_warning.clear();
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

        if (!this.unsecure_server && val) {

            Helpers.GUIRunAndWait(() -> {
                danger_server.setVisible(val);
                pack();
            });

        }

        this.unsecure_server = val;

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
                    LOGGER.log(Level.SEVERE, null, ex);
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
                    LOGGER.log(Level.SEVERE, null, ex);
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

    public static void setInstance(WaitingRoomFrame instance) {
        WaitingRoomFrame.THIS = instance;
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
            CoronaHTMLEditorKit editor = (CoronaHTMLEditorKit) chat.getEditorKit();
            StringReader reader = new StringReader(text);
            try {
                editor.read(reader, chat.getDocument(), chat.getDocument().getLength());
                chat.setCaretPosition(chat.getDocument().getLength());
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

        String html = "";

        String[] lines = chat.split("\n");

        for (String line : lines) {

            String nick = line.replaceAll("^([^:()]+:+).*$", "$1").replaceAll(":$", "");

            String msg = line.replaceAll("^[^:()]+:+[0-9:()]+ *(.*)$", "$1");

            String hora = line.replaceAll("^[^:()]+:+([0-9:()]+) *.*$", "$1");

            String avatar_src, align, image_align;

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

            html += "<div " + align + "><div style='margin-bottom:4px'><img id='avatar_" + nick
                    + "' align='middle' src='" + avatar_src + "' />&nbsp;<b>" + nick
                    + "</b> <span style='font-size:0.8em'>" + hora + "</span></div>" + msg + "</div>";
        }

        return html;

    }

    private String parseBBCODEChat(String message) {

        return message.replaceAll("(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]", "<i>$2</i>")
                .replaceAll("(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]", "<b>$2</b>")
                .replaceAll("(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]",
                        "<span style='color:$2'>$3</span>");
    }

    private String removeBBCODEChat(String message) {
        return message.replaceAll("(?i)\\[ *([i]) *\\](.*?)\\[ */ *\\1 *\\]", "$2")
                .replaceAll("(?i)\\[ *([b]) *\\](.*?)\\[ */ *\\1 *\\]", "$2")
                .replaceAll("(?i)\\[ *([c](?:olor)?) *= *(.*?) *\\](.*?)\\[ */ *\\1 *\\]", "$3");

    }

    private String parseImagesChat(String message, String align, boolean send) {

        String msg = message;

        Pattern pattern = Pattern.compile("img(s?)://([^ \r\n]+)");

        Matcher matcher = pattern.matcher(message);

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
        return message.replaceAll("(?:http|img)s?://[^ \r\n]+", "");
    }

    private String parseEmojiChat(String message) {

        String msg = message;

        Pattern pattern = Pattern.compile("#([0-9]+)#");

        Matcher matcher = pattern.matcher(message);

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

    public JTextField getChat_box() {
        return chat_box;
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

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate("game.sala_de_espera") + nick + ")");

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

        latency_label.setVisible(false);

        radar.setEnabled(GameFrame.RADAR_AVAILABLE);

        radar.setToolTipText(Translator.translate(
                GameFrame.RADAR_AVAILABLE ? "radar.active" : "radar.inactive"));

        chat_box.getDocument().addDocumentListener(new SendButtonListener());

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

            gameinfo_original = GameFrame.BUYIN + (GameFrame.REBUY ? "" : "*") + "|"
                    + Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / "
                    + Helpers.float2String(GameFrame.CIEGA_GRANDE)
                    + (GameFrame.CIEGAS_DOUBLE > 0
                            ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE)
                            + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*")
                            : "")
                    + (GameFrame.MANOS != -1 ? "|" + String.valueOf(GameFrame.MANOS) : "");

            if (game_info_buyin.isEnabled() && !GameFrame.isRECOVER()) {

                String[] game_info = gameinfo_original.split("\\|");

                boolean rebuy = !game_info[0].trim().endsWith("*");

                game_info_buyin.setText(
                        Helpers.float2String(Float.parseFloat(game_info[0].replace("*", ""))) + (rebuy ? "" : "*"));

                game_info_blinds.setText(game_info[1]);

                if (game_info.length > 2) {
                    game_info_hands.setText(game_info[2]);
                } else {
                    game_info_hands.setVisible(false);
                }
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

    private synchronized void gen_priv_session_key() {
        try {
            String fileName = "/panoptes_session.key";
            if (Init.DEV_MODE) {
                String safeNick = local_nick != null ? local_nick.replaceAll("[^a-zA-Z0-9.-]", "_") : "default";
                fileName = "/panoptes_session_" + safeNick + ".key";
            }
            java.io.File sessionFile = new java.io.File(Init.CORONA_DIR + fileName);

            boolean sessionLoaded = false;

            // FIX: ALWAYS attempt to load existing session to preserve Cryptographic Identity.
            // Do not check GameFrame.RECOVER here, as the client doesn't know the server state yet.
            // If we delete the file, the KEM decryption will mathematically fail during a recovery.
            if (sessionFile.exists()) {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(sessionFile.toPath());

                // V76: The file contains exactly 80 bytes (32 PubKey + 48 Encrypted PrivKey)
                if (fileBytes.length == 80) {
                    byte[] sessionEncryptedBlob = java.util.Arrays.copyOfRange(fileBytes, 32, 80);
                    if (Panoptes.getInstance().sessionLoad(sessionEncryptedBlob)) {
                        local_player_public_key = java.util.Arrays.copyOfRange(fileBytes, 0, 32);
                        sessionLoaded = true;
                        LOGGER.log(Level.INFO, "Session restored successfully from file.");
                    } else {
                        LOGGER.log(Level.WARNING, "Session file rejected by C engine. Generating fresh session...");
                    }
                }
            }

            // Generate fresh session ONLY if it's the first time playing or the file was corrupted
            if (!sessionLoaded) {
                java.nio.file.Files.deleteIfExists(sessionFile.toPath());

                // sessionInitialize() returns 80 bytes: [32-byte PubKey] + [48-byte Encrypted PrivKey]
                byte[] sessionBlobFull = Panoptes.getInstance().sessionInitialize();

                if (sessionBlobFull != null && sessionBlobFull.length == 80) {
                    local_player_public_key = java.util.Arrays.copyOfRange(sessionBlobFull, 0, 32);

                    // Save the entire 80 bytes so we can recover the public key later
                    java.nio.file.Files.write(sessionFile.toPath(), sessionBlobFull);
                    LOGGER.log(Level.INFO, "Fresh ephemeral session generated and saved to disk.");
                } else {
                    throw new Exception("Failed to initialize native session.");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Critical failure during session key generation", e);
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

                statement.setString(2, Base64.getEncoder().encodeToString(this.local_client_permutation_key.getEncoded()));

                statement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

    }

    public void writeCommandToServer(String command) {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        try {
            synchronized (local_client_socket.getOutputStream()) {

                local_client_socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                local_client_socket.getOutputStream().flush();

            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public void writeCommandFromServer(String command, Socket socket) {
        try {
            synchronized (socket.getOutputStream()) {
                socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                socket.getOutputStream().flush();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public String readCommandFromClient(Socket socket, SecretKeySpec key, SecretKeySpec hmac_key) {

        try {
            synchronized (socket.getInputStream()) {

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                return Helpers.decryptCommand(in.readLine(), key, hmac_key);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public String readCommandFromServer() {

        while (this.reconnecting) {
            synchronized (getLocalClientSocketLock()) {
                try {
                    getLocalClientSocketLock().wait(1000);
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        synchronized (getLocal_client_buffer_read_is()) {
            try {
                return Helpers.decryptCommand(getLocal_client_buffer_read_is().readLine(), getLocal_client_aes_key(),
                        getLocal_client_hmac_key());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }

    // Función AUTO-RECONNECT
    public boolean reconectarCliente() {

        reconnecting = true;

        LOGGER.log(Level.WARNING, "Attempting to reconnect to server...");

        Helpers.GUIRun(() -> {
            InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false,
                    Translator.translate("conn.reconectando_con_el_servidor"), Color.MAGENTA, Color.BLACK,
                    getClass().getResource("/images/action/plug.png"), NOTIFICATION_TIMEOUT);
            dialog.setLocation(dialog.getParent().getLocation());
            dialog.setVisible(true);
        });

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

                String b64_nick = Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"));

                String b64_hmac_nick = Base64.getEncoder().encodeToString(orig_sha256_HMAC.doFinal(local_nick.getBytes("UTF-8")));

                do {

                    try {

                        String[] server_address = server_ip_port.split(":");

                        local_client_socket = new Socket(server_address[0], Integer.parseInt(server_address[1]));

                        local_client_socket.setTcpNoDelay(true);

                        LOGGER.log(Level.WARNING, "Connected to server! Exchanging keys...");

                        // Le mandamos los bytes "mágicos"
                        local_client_socket.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));

                        local_client_socket.getOutputStream().flush();

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
                        // Le mandamos nuestro nick al server autenticado con la clave HMAC antigua
                        LOGGER.log(Level.WARNING, "Sending reconnection data...");

                        local_client_socket.getOutputStream().write(
                                (Helpers.encryptCommand(b64_nick + "#" + AboutDialog.VERSION + "#*#*#" + b64_hmac_nick,
                                        local_client_aes_key, local_client_hmac_key) + "\n").getBytes("UTF-8"));

                        local_client_socket.getOutputStream().flush();

                        local_client_buffer_read_is = new BufferedReader(
                                new InputStreamReader(local_client_socket.getInputStream()));

                        LOGGER.log(Level.INFO, "RECONNECTED SUCCESSFULLY TO SERVER");

                        ok_rec = true;

                    } catch (Exception ex) {

                        LOGGER.log(Level.SEVERE, "RECONNECTION SOCKET THREW AN EXCEPTION");
                        LOGGER.log(Level.SEVERE, null, ex);

                    } finally {

                        if (!ok_rec) {

                            if (WaitingRoomFrame.getInstance().isPartida_empezada()
                                    && GameFrame.getInstance() != null) {
                                Helpers.GUIRun(() -> {
                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false,
                                            Translator.translate("conn.no_se_pudo_reconectar_con"), Color.RED,
                                            Color.WHITE, getClass().getResource("/images/action/plug.png"),
                                            NOTIFICATION_TIMEOUT);
                                    dialog.setLocation(dialog.getParent().getLocation());
                                    dialog.setVisible(true);
                                });
                            }

                            if (local_client_socket != null && !local_client_socket.isClosed()) {

                                try {

                                    local_client_socket.close();

                                } catch (Exception ex) {
                                }

                                local_client_socket = null;
                            }

                            if (!exit && (!WaitingRoomFrame.getInstance().isPartida_empezada()
                                    || !GameFrame.getInstance().getLocalPlayer().isExit())) {

                                if (System.currentTimeMillis() - start > GameFrame.CLIENT_RECON_TIMEOUT
                                        && WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    if (this.reconnect_dialog == null) {

                                        Helpers.GUIRun(() -> {
                                            reconnect_dialog = new Reconnect2ServerDialog(
                                                    GameFrame.getInstance() != null ? GameFrame.getInstance() : THIS,
                                                    true, server_ip_port);
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
                                                LOGGER.log(Level.SEVERE,
                                                        null, ex);
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

                } while (!exit && !ok_rec && (!WaitingRoomFrame.getInstance().isPartida_empezada()
                        || !GameFrame.getInstance().getLocalPlayer().isExit()));

                if (this.reconnect_dialog != null) {

                    Helpers.GUIRunAndWait(() -> {
                        reconnect_dialog.dispose();
                        reconnect_dialog = null;
                    });
                }

                if (ok_rec) {
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

                this.reconnecting = false;

                getLocalClientSocketLock().notifyAll();

                return ok_rec;

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        }

        return false;

    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par) {

        broadcastASYNCGAMECommandFromServer(command, par, true);

    }

    public void broadcastASYNCGAMECommandFromServer(String command, Participant par, boolean confirmation) {

        ArrayList<Participant> targets = new ArrayList<>();

        // Safely lock the map to extract valid targets without CME
        synchronized (participantes) {
            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                Participant p = entry.getValue();
                if (p != null && !p.isCpu() && p != par && !p.isExit()) {
                    targets.add(p);
                }
            }
        }

        if (!targets.isEmpty()) {

            int id = Helpers.CSPRNG_GENERATOR.nextInt();
            byte[] iv = new byte[16];
            Helpers.CSPRNG_GENERATOR.nextBytes(iv);

            for (Participant p : targets) {
                if (!confirmation) {
                    String full_command = "GAME#" + String.valueOf(id) + "#" + command;
                    p.writeCommandFromServer(Helpers.encryptCommand(full_command, p.getAes_key(), iv, p.getHmac_key()));
                } else {
                    synchronized (p.getPre_game_socket_writer_queue()) {
                        p.getPre_game_socket_writer_queue().add(command);
                        p.getPre_game_socket_writer_queue().notifyAll();
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

            synchronized (p.getPre_game_socket_writer_queue()) {
                p.getPre_game_socket_writer_queue().add(command);
                p.getPre_game_socket_writer_queue().notifyAll();
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
                            local_client_socket_reader_queue.put(mensaje_recibido);
                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE,
                                    (String) null, ex);
                        }
                    } else {
                        switch (partes_comando[0]) {
                            case "PING":
                                writeCommandToServer(
                                        "PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));
                                try {
                                    local_client_socket_reader_queue.put(mensaje_recibido);
                                } catch (InterruptedException ex) {
                                    System.getLogger(Participant.class.getName())
                                            .log(System.Logger.Level.ERROR, (String) null, ex);
                                }
                                break;
                            case "SECPING":
                                // --- PANOPTES: CLIENT SIGNS SERVER HEARTBEAT ---
                                try {
                                    byte[] challengeBytes = Base64.getDecoder().decode(partes_comando[1]);
                                    byte[] signatureBytes = Panoptes.getInstance().signChallenge(challengeBytes);
                                    String signatureBase64 = signatureBytes != null
                                            ? Base64.getEncoder().encodeToString(signatureBytes).replaceAll("\\s+", "")
                                            : "";

                                    writeCommandToServer(
                                            Helpers.encryptCommand("SECPONG#" + signatureBase64,
                                                    local_client_aes_key, local_client_hmac_key));
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE,
                                            "Failed to sign Panoptes heartbeat", e);
                                }
                                break;
                            case "PONG":
                                remote_server_pong = Integer.valueOf(partes_comando[1]);
                                synchronized (ping_pong_lock) {
                                    ping_pong_lock.notifyAll();
                                }
                                break;
                            case "PONG2":
                                remote_server_pong2 = Integer.valueOf(partes_comando[1]);
                                synchronized (ping_pong_lock) {
                                    ping_pong_lock.notifyAll();
                                }
                                break;
                            default:
                                try {
                                    local_client_socket_reader_queue.put(mensaje_recibido);
                                } catch (Exception ex) {
                                    LOGGER.log(Level.SEVERE,
                                            (String) null, ex);
                                }
                                break;
                        }
                    }

                } else {
                    try {
                        if (!local_client_socket_reader_queue.contains(POISON_PILL)) {
                            local_client_socket_reader_queue.put(POISON_PILL);
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE,
                                (String) null, ex);
                    }

                    cliente_last_received.clear();
                }

                if (mensaje_recibido == null) {
                    if (!exit && ((WaitingRoomFrame.getInstance() != null && !isPartida_empezada())
                            || (GameFrame.getInstance() != null && !GameFrame.getInstance().getLocalPlayer().isExit()))) {

                        if (!reconectarCliente()) {
                            exit = true;
                        }
                    } else {
                        // Si ya estábamos saliendo o la reconexión no aplica, aniquilamos el hilo
                        exit = true;
                    }
                }
            }

        });
    }

    private void runSecPingPongThreadCliente() {

        //Not really required for Panoptes
        Helpers.threadRun(() -> {

            Panoptes panoptes_instance = Panoptes.getInstance();
            java.security.SecureRandom rng = new java.security.SecureRandom();

            while (!exit && WaitingRoomFrame.getInstance() != null) {
                try {
                    String[] direccion_server = server_ip_port.split(":");
                    String serverIp = direccion_server[0];

                    String miIpToUse;
                    if (serverIp.equals("localhost") || serverIp.equals("127.0.0.1")
                            || serverIp.startsWith("192.168.") || serverIp.startsWith("10.")
                            || serverIp.startsWith("172.16.")) {
                        miIpToUse = local_client_socket.getLocalAddress().getHostAddress();
                    } else {
                        miIpToUse = Helpers.getMyPublicIP();
                    }

                    int myLocalPort = local_client_socket.getLocalPort();

                    /* Generate the challenge using a static ownerID for the server */
                    byte[] heartbeatChallenge = panoptes_instance.generateChallenge("SERVER_HEARTBEAT",
                            miIpToUse, myLocalPort);
                    String challengeB64 = java.util.Base64.getEncoder().encodeToString(heartbeatChallenge).replaceAll("\\s+", "");

                    writeCommandToServer(Helpers.encryptCommand("SECPING#" + challengeB64,
                            local_client_aes_key, local_client_hmac_key));

                } catch (Exception e) {
                    LOGGER.log(java.util.logging.Level.SEVERE,
                            "Error dispatching SECPING to server", e);
                }

                /* Jitter: Randomized sleep between base interval and 2x base interval */
                int jitter = rng.nextInt((int) SEC_PING_INTERVAL_MS);
                Helpers.pausar(SEC_PING_INTERVAL_MS + jitter);
            }
        });
    }

    private void runPingPongThreadCliente() {

        // --- PING/PONG KEEPALIVE THREAD ---
        Helpers.threadRun(() -> {

            while (!exit && WaitingRoomFrame.getInstance() != null) {

                int ping = Helpers.CSPRNG_GENERATOR.nextInt();

                remote_server_pong = null;
                remote_server_pong2 = null;
                remote_server_latency = -1;
                remote_server_latency2 = -1;

                long pingStartNs = System.nanoTime();

                try {
                    writeCommandToServer("PING#" + ping);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE,
                            "Error dispatching PING", ex);
                    break;
                }

                long end = System.currentTimeMillis() + WaitingRoomFrame.PING_PONG_TIMEOUT;

                while (!exit && (remote_server_pong == null || remote_server_pong2 == null)
                        && System.currentTimeMillis() < end) {
                    synchronized (ping_pong_lock) {
                        try {
                            ping_pong_lock.wait(end - System.currentTimeMillis());
                        } catch (InterruptedException ignored) {
                        }
                    }

                    if (remote_server_latency == -1 && remote_server_pong != null
                            && remote_server_pong == ping + 1) {

                        remote_server_latency = Math
                                .round((System.nanoTime() - pingStartNs) / 1_000_000);
                    }

                    if (remote_server_latency2 == -1 && remote_server_pong2 != null
                            && remote_server_pong2 == ping + 2) {

                        remote_server_latency2 = Math
                                .round((System.nanoTime() - pingStartNs) / 1_000_000);
                    }
                }

                if (remote_server_latency != -1) {

                    Helpers.GUIRun(() -> {
                        this.latency_label.setVisible(true);
                        this.latency_label.setText(Translator.translate("ui.latencia_servidor")
                                + " " + String.valueOf(remote_server_latency) + " ms");
                    });
                }

                if (!exit && WaitingRoomFrame.getInstance() != null) {

                    if (remote_server_pong == null) {
                        LOGGER.log(Level.WARNING,
                                "SERVER FAILED TO RESPOND TO PING");
                    } else if (remote_server_pong != ping + 1) {
                        LOGGER.log(Level.WARNING,
                                "INVALID PONG FROM SERVER");
                    } else if (remote_server_pong2 == null) {
                        LOGGER.log(Level.WARNING,
                                "SERVER FAILED TO RESPOND TO PING2");
                    } else if (remote_server_pong2 != ping + 2) {
                        LOGGER.log(Level.WARNING,
                                "INVALID PONG2 FROM SERVER");
                    } else if (DEV_MODE) {
                        LOGGER.log(Level.INFO,
                                "SERVER PONGS RECEIVED. (Latency: {0} ms / {1} ms)",
                                new Object[]{remote_server_latency, remote_server_latency2});
                    }

                    Helpers.pausar(PING_INTERVAL_MS);
                }

            }
        });
    }

    private void cliente() {
        Helpers.threadRun(() -> {
            gen_priv_session_key();
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
                    local_client_socket = new Socket(direccion[0], Integer.parseInt(direccion[1]));
                    local_client_socket.setTcpNoDelay(true);

                    local_client_socket.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));
                    local_client_socket.getOutputStream().flush();

                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("status.intercambio_claves"));
                    });

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

                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("status.firmando_desafio"));
                    });

                    int chalLen = dIn.readInt();
                    byte[] serverChallengeBytes = new byte[chalLen];
                    dIn.readFully(serverChallengeBytes, 0, chalLen);

                    Panoptes panoptes = Panoptes.getInstance();
                    byte[] miFirmaBytes = null;
                    try {
                        miFirmaBytes = panoptes.signChallenge(serverChallengeBytes);
                    } catch (Exception e) {
                    }
                    String miFirmaBase64 = miFirmaBytes != null ? Base64.getEncoder().encodeToString(miFirmaBytes) : "";

                    KeyFactory clientKeyFac = KeyFactory.getInstance("EC");
                    X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(serverPubKeyEnc);
                    PublicKey serverPubKey = clientKeyFac.generatePublic(x509KeySpec);
                    clientKeyAgree.doPhase(serverPubKey, true);
                    byte[] clientSharedSecret = clientKeyAgree.generateSecret();
                    byte[] secret_hash = MessageDigest.getInstance("SHA-512").digest(clientSharedSecret);
                    local_client_aes_key = new SecretKeySpec(secret_hash, 0, 16, "AES");
                    local_client_hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    local_client_hmac_key_orig = local_client_hmac_key;

                    byte[] avatar_bytes = null;
                    if (local_avatar != null && local_avatar.length() > 0) {
                        try (FileInputStream is = new FileInputStream(local_avatar)) {
                            avatar_bytes = is.readAllBytes();
                        }
                    }

                    Helpers.GUIRun(() -> {
                        status.setText(Translator.translate("status.generando_desafio_cliente"));
                    });

                    String challengeBase64 = "";
                    try {
                        String serverIp = direccion[0];
                        String miIpToUse;
                        if (serverIp.equals("localhost") || serverIp.equals("127.0.0.1")
                                || serverIp.startsWith("192.168.") || serverIp.startsWith("10.")
                                || serverIp.startsWith("172.16.")) {
                            miIpToUse = local_client_socket.getLocalAddress().getHostAddress();
                        } else {
                            miIpToUse = Helpers.getMyPublicIP();
                        }

                        int myLocalPort = local_client_socket.getLocalPort();
                        byte[] challengeBytes = panoptes.generateChallenge("LOCAL", miIpToUse, myLocalPort);
                        challengeBase64 = Base64.getEncoder().encodeToString(challengeBytes);
                    } catch (Exception e) {
                        Helpers.mostrarMensajeError(THIS, Translator.translate("error.fatal_panoptes_challenge"));
                        System.exit(1);
                    }

                    String myPublicKeyBase64 = Base64.getEncoder().encodeToString(local_player_public_key).replaceAll("\\s+", "");
                    String securityPayload = challengeBase64 + "$" + miFirmaBase64 + "$" + myPublicKeyBase64;

                    writeCommandToServer(Helpers.encryptCommand(Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"))
                            + "#" + AboutDialog.VERSION + "@" + securityPayload
                            + (avatar_bytes != null ? "#" + Base64.getEncoder().encodeToString(avatar_bytes) : "#*")
                            + (password != null ? "#" + Base64.getEncoder().encodeToString(password.getBytes("UTF-8")) : "#*"),
                            local_client_aes_key, local_client_hmac_key));

                    local_client_buffer_read_is = new BufferedReader(new InputStreamReader(local_client_socket.getInputStream()));
                    recibido = readCommandFromServer();
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
                        case "BADPASSWORD":
                            exit = true;
                            mostrarMensajeError(THIS, Translator.translate("conn.bad_pass"));
                            break;
                        case "NICKOK":
                            byte[] serverSignature = Base64.getDecoder().decode(partes[2].replaceAll("[^A-Za-z0-9+/=]", ""));

                            if ("0".equals(partes[1])) {
                                Helpers.GUIRun(() -> {
                                    pass_icon.setVisible(false);
                                });
                            }

                            gameinfo_original = new String(Base64.getDecoder().decode(partes[3].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8");

                            Helpers.GUIRun(() -> {
                                status.setText(Translator.translate("status.recibiendo_info_servidor"));
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
                            });

                            recibido = readCommandFromServer();
                            while (recibido != null && recibido.startsWith("PING#")) {
                                String[] ping_parts = recibido.split("#");
                                writeCommandToServer("PONG#" + String.valueOf(Integer.parseInt(ping_parts[1]) + 1));
                                recibido = readCommandFromServer();
                            }

                            partes = recibido.split("#");
                            server_nick = new String(Base64.getDecoder().decode(partes[0].replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8").trim();

                            int authStatus = Panoptes.getInstance().verifyResponse("LOCAL", serverSignature);

                            if (authStatus == Panoptes.STATUS_FAILED) {
                                THIS.setUnsecure_server(true);
                            } else if (authStatus == Panoptes.STATUS_VM_DETECTED) {
                                Helpers.threadRun(() -> {
                                    mostrarMensajeInformativo(THIS, Translator.translate("ui.error.vm_detected_waiting_room") + " [" + server_nick + "]");
                                });
                            }

                            try {
                                MessageDigest md = MessageDigest.getInstance("MD5");
                                md.update(local_nick.getBytes("UTF-8"));
                                md.update(server_nick.getBytes("UTF-8"));
                                md.update(local_client_aes_key.getEncoded());
                                md.update(local_client_hmac_key.getEncoded());
                                local_client_permutation_key = new SecretKeySpec(md.digest(), "AES");
                                md = MessageDigest.getInstance("MD5");
                                local_client_permutation_key_hash = Base64.getEncoder().encodeToString(md.digest(local_client_permutation_key.getEncoded()));
                                sqlSavePermutationkey();
                            } catch (Exception ex) {
                            }

                            String server_avatar_base64 = partes.length > 1 ? partes[1].replaceAll("[^A-Za-z0-9+/=]", "") : "";
                            File server_avatar = null;
                            try {
                                if (server_avatar_base64.length() > 0) {
                                    int file_id = Math.abs(Helpers.CSPRNG_GENERATOR.nextInt());
                                    server_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + server_nick + "_avatar" + file_id);
                                    try (FileOutputStream os = new FileOutputStream(server_avatar)) {
                                        os.write(Base64.getDecoder().decode(server_avatar_base64));
                                    }
                                }
                            } catch (Exception ex) {
                                server_avatar = null;
                            }

                            recibido = readCommandFromServer();
                            while (recibido != null && recibido.startsWith("PING#")) {
                                String[] ping_parts = recibido.split("#");
                                writeCommandToServer("PONG#" + String.valueOf(Integer.parseInt(ping_parts[1]) + 1));
                                recibido = readCommandFromServer();
                            }

                            if (!"*".equals(recibido)) {
                                chat_text = new StringBuffer(new String(Base64.getDecoder().decode(recibido.replaceAll("[^A-Za-z0-9+/=]", "")), "UTF-8"));
                            }

                            recibido = readCommandFromServer();
                            while (recibido != null && recibido.startsWith("PING#")) {
                                String[] ping_parts = recibido.split("#");
                                writeCommandToServer("PONG#" + String.valueOf(Integer.parseInt(ping_parts[1]) + 1));
                                recibido = readCommandFromServer();
                            }

                            GameFrame.RADAR_AVAILABLE = Boolean.parseBoolean(recibido);

                            if (GameFrame.RADAR_AVAILABLE) {
                                Helpers.threadRun(() -> {
                                    Helpers.mostrarMensajeInformativo(this, Translator.translate("radar.el_servidor_ha_activado_el"), "justify", (int) Math.round(getWidth() * 0.8f), new ImageIcon(Init.class.getResource("/images/shield.png")));
                                });
                            }

                            nuevoParticipante(server_nick, server_avatar, null, null, null, false, THIS.isUnsecure_server());
                            nuevoParticipante(local_nick, local_avatar, null, null, null, false, false);

                            if (participantes.get(local_nick) != null) {
                                participantes.get(local_nick).setPanoptes_public_key(local_player_public_key);
                            }

                            Helpers.GUIRunAndWait(() -> {
                                status.setText(Translator.translate("status.conectado"));
                                status.setIcon(new ImageIcon(getClass().getResource("/images/emoji_chat/1.png")));
                                barra.setVisible(false);
                                chat_box.setEnabled(true);
                                emoji_button.setEnabled(true);
                                image_button.setEnabled(true);
                                max_min_label.setEnabled(true);
                                radar.setEnabled(GameFrame.RADAR_AVAILABLE);
                                radar.setToolTipText(Translator.translate(radar.isEnabled() ? "radar.active" : "radar.inactive"));
                            });

                            refreshChatPanel();
                            booting = false;

                            runSocketReaderClientThread();
                            runPingPongThreadCliente();

                            do {
                                recibido = local_client_socket_reader_queue.take();

                                if (!POISON_PILL.equals(recibido)) {
                                    String[] partes_comando = recibido.split("#");
                                    switch (partes_comando[0]) {
                                        case "PING":
                                            writeCommandToServer("PONG2#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 2));
                                            break;
                                        case "SECPONG":
                                            try {
                                                byte[] signature = Base64.getDecoder().decode(partes_comando[1]);
                                                int isLegit = Panoptes.getInstance().verifyResponse("SERVER_HEARTBEAT", signature);
                                                if (isLegit == Panoptes.STATUS_FAILED) {
                                                    if (!THIS.isUnsecure_server()) {
                                                        THIS.setUnsecure_server(true);
                                                    }
                                                }
                                            } catch (Exception e) {
                                            }
                                            break;
                                        case "CHAT":
                                            String mensaje = (partes_comando.length == 3) ? new String(Base64.getDecoder().decode(partes_comando[2]), "UTF-8") : "";
                                            recibirMensajeChat(new String(Base64.getDecoder().decode(partes_comando[1]), "UTF-8"), mensaje);
                                            break;
                                        case "EXIT":
                                            exit = true;
                                            mostrarMensajeError(THIS, "The server cancelled the game before starting.");
                                            break;
                                        case "KICKED":
                                            exit = true;
                                            Audio.playWavResource("loser/payaso.wav");
                                            mostrarMensajeInformativo(THIS, Translator.translate("ui.error.kicked_out"));
                                            break;

                                        // --- V81 P2P CLIENT-SIDE EXECUTION (ROOT COMMANDS) ---
                                        case "P2P_START":
                                            p2pSwarmManager.handleClientP2PStart(partes_comando);
                                            break;
                                        case "P2P_INBOX":
                                            p2pSwarmManager.handleClientP2PInbox(partes_comando);
                                            break;
                                        case "P2P_VERIFY":
                                            p2pSwarmManager.handleClientP2PVerify(partes_comando);
                                            break;
                                        // -----------------------------------------------------

                                        case "GAME":
                                            String subcomando = partes_comando[2];
                                            int id = Integer.parseInt(partes_comando[1]);
                                            writeCommandToServer("CONF#" + String.valueOf(id + 1) + "#OK");
                                            if (!cliente_last_received.containsKey(subcomando) || cliente_last_received.get(subcomando) != id) {
                                                cliente_last_received.put(subcomando, id);
                                                if (isPartida_empezada()) {
                                                    switch (subcomando) {
                                                        case "YOUARELATE":
                                                            String client_nick2 = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                            String ipCliente = partes_comando[4];
                                                            try {
                                                                if (!late_clients_warning.contains(ipCliente)) {
                                                                    Audio.playWavResource("misc/new_user.wav");
                                                                    late_clients_warning.add(ipCliente);
                                                                }
                                                                Helpers.GUIRun(() -> {
                                                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, "[" + client_nick2 + "] " + Translator.translate("WANTS TO ENTER THE GAME"), Color.RED, Color.WHITE, getClass().getResource("/images/action/cry.png"), NOTIFICATION_TIMEOUT);
                                                                    dialog.setLocation(dialog.getParent().getLocation());
                                                                    dialog.setVisible(true);
                                                                });
                                                            } catch (Exception e) {
                                                            }
                                                            break;
                                                        case "RADAR":
                                                            if (partes_comando.length == 5) {
                                                                String requester = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                GameFrame.getInstance().getLocalPlayer().RADAR(requester, Base64.getDecoder().decode(partes_comando[4]));
                                                            } else if (partes_comando.length == 7) {
                                                                try {
                                                                    String suspicious = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                                    byte[] imageBytes = partes_comando[4].equals("*") ? null : Base64.getDecoder().decode(partes_comando[4]);
                                                                    byte[] encryptedRadarData = partes_comando[5].equals("*") ? null : Base64.getDecoder().decode(partes_comando[5]);
                                                                    long timestamp = Long.parseLong(partes_comando[6]);
                                                                    StringBuilder sb = new StringBuilder();
                                                                    sb.append("  ____                            ____       _                ____     _    ____    _    ____  \n"
                                                                            + " / ___|___  _ __ ___  _ __   __ _|  _ \\ ___ | | _____ _ __  |  _ \\    / \\  |  _ \\  / \\  |  _ \\ \n"
                                                                            + "| |   / _ \\| '__/ _ \\| '_ \\ / _` | |_) / _ \\| |/ / _ \\ '__| | |_) |  / _ \\ | | | |/ _ \\ | |_) |\n"
                                                                            + "| |__| (_) | | | (_) | | | | (_| |  __/ (_) |   <  __/ |    |  _ <  / ___ \\| |_| / ___ \\|  _ < \n"
                                                                            + " \\____\\___/|_|  \\___/|_| |_|\\__,_|_|   \\___/|_|\\_\\___|_|    |_| \\_\\/_/   \\_\\____/_/   \\_\\_| \\_\\\n"
                                                                            + "                                                                                               \n\n");
                                                                    sb.append("CoronaPoker Radar -> [").append(suspicious).append("] ").append(Helpers.getFechaHoraActual()).append("\n\n");
                                                                    if (encryptedRadarData != null) {
                                                                        try {
                                                                            String rawIntel = Panoptes.getInstance().parseRadarReport(encryptedRadarData);
                                                                            if (rawIntel != null) {
                                                                                sb.append(rawIntel);
                                                                            } else {
                                                                                sb.append("************************************************************************\n[!] CRITICAL SECURITY ERROR: INTEGRITY CHECK FAILED (MAC POLY1305)\nPacket was altered in transit or Chaos/KEM signature is invalid.\n************************************************************************\n");
                                                                            }
                                                                        } catch (Exception ex) {
                                                                            sb.append("[!] Exception decrypting data: ").append(ex.getMessage()).append("\n");
                                                                        }
                                                                    } else {
                                                                        sb.append("[!] No encrypted process data received.\n");
                                                                    }
                                                                    GameFrame.getInstance().getCrupier().saveRADARLog(suspicious, imageBytes, sb.toString(), timestamp);
                                                                } catch (Exception ex) {
                                                                }
                                                            }
                                                            break;
                                                        case "IWTSTH":
                                                            if (GameFrame.getInstance().getCrupier().isShow_time()) {
                                                                GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"));
                                                            }
                                                            break;
                                                        case "IWTSTHSHOW":
                                                            GameFrame.getInstance().getCrupier().IWTSTH_SHOW(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"), Boolean.parseBoolean(partes_comando[4]));
                                                            break;
                                                        case "IWTSTHRULE":
                                                            final String[] partes_final_iwtsthrule = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                GameFrame.IWTSTH_RULE = "1".equals(partes_final_iwtsthrule[3]);
                                                                Helpers.GUIRun(() -> {
                                                                    GameFrame.getInstance().getIwtsth_rule_menu().setSelected(GameFrame.IWTSTH_RULE);
                                                                    Helpers.TapetePopupMenu.IWTSTH_RULE_MENU.setSelected(GameFrame.IWTSTH_RULE);
                                                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, GameFrame.IWTSTH_RULE ? Translator.translate("iwtsth.regla_iwtsth_activada") : Translator.translate("iwtsth.regla_iwtsth_desactivada"), GameFrame.IWTSTH_RULE ? new Color(0, 130, 0) : Color.RED, Color.WHITE, getClass().getResource("/images/menu/eyes.png"), NOTIFICATION_TIMEOUT);
                                                                    dialog.setLocation(dialog.getParent().getLocation());
                                                                    dialog.setVisible(true);
                                                                });
                                                            });
                                                            break;
                                                        case "RABBITRULE":
                                                            final String[] partes_final_rabbitrule = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                GameFrame.RABBIT_HUNTING = Integer.parseInt(partes_final_rabbitrule[3]);
                                                                Helpers.GUIRun(() -> {
                                                                    GameFrame.getInstance().getMenu_rabbit_off().setSelected(false);
                                                                    GameFrame.getInstance().getMenu_rabbit_free().setSelected(false);
                                                                    GameFrame.getInstance().getMenu_rabbit_sb().setSelected(false);
                                                                    GameFrame.getInstance().getMenu_rabbit_bb().setSelected(false);
                                                                    String notification = "";
                                                                    switch (GameFrame.RABBIT_HUNTING) {
                                                                        case 0:
                                                                            GameFrame.getInstance().getMenu_rabbit_off().setSelected(true);
                                                                            notification = Translator.translate("rabbit.rabbit_hunting_desactivado");
                                                                            break;
                                                                        case 1:
                                                                            GameFrame.getInstance().getMenu_rabbit_free().setSelected(true);
                                                                            notification = Translator.translate("rabbit.rabbit_hunting_activado_free");
                                                                            break;
                                                                        case 2:
                                                                            GameFrame.getInstance().getMenu_rabbit_sb().setSelected(true);
                                                                            notification = Translator.translate("rabbit.rabbit_hunting_activado_free_sb");
                                                                            break;
                                                                        case 3:
                                                                            GameFrame.getInstance().getMenu_rabbit_bb().setSelected(true);
                                                                            notification = Translator.translate("rabbit.rabbit_hunting_activado_free_sb_2");
                                                                            break;
                                                                    }
                                                                    Helpers.TapetePopupMenu.RABBIT_OFF.setSelected(GameFrame.getInstance().getMenu_rabbit_off().isSelected());
                                                                    Helpers.TapetePopupMenu.RABBIT_FREE.setSelected(GameFrame.getInstance().getMenu_rabbit_free().isSelected());
                                                                    Helpers.TapetePopupMenu.RABBIT_SB.setSelected(GameFrame.getInstance().getMenu_rabbit_sb().isSelected());
                                                                    Helpers.TapetePopupMenu.RABBIT_BB.setSelected(GameFrame.getInstance().getMenu_rabbit_bb().isSelected());
                                                                    InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, notification, GameFrame.RABBIT_HUNTING != 0 ? Color.BLUE : Color.RED, Color.WHITE, getClass().getResource("/images/action/rabbit_action.png"), NOTIFICATION_TIMEOUT);
                                                                    dialog.setLocation(dialog.getParent().getLocation());
                                                                    dialog.setVisible(true);
                                                                });
                                                            });
                                                            break;
                                                        case "RABBIT":
                                                            if (GameFrame.getInstance().getCrupier().isShow_time()) {
                                                                GameFrame.getInstance().getCrupier().RABBIT_HANDLER(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"), Integer.parseInt(partes_comando[4]));
                                                            }
                                                            break;
                                                        case "TIMEOUT":
                                                            Player jugador = GameFrame.getInstance().getCrupier().getNick2player().get(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"));
                                                            if (jugador != null) {
                                                                jugador.setTimeout(true);
                                                            }
                                                            break;
                                                        case "TTS":
                                                            GameFrame.TTS_SERVER = partes_comando[3].equals("1");
                                                            Helpers.GUIRun(() -> {
                                                                GameFrame.getInstance().getTts_menu().setEnabled(GameFrame.TTS_SERVER);
                                                                Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setEnabled(GameFrame.TTS_SERVER);
                                                                InGameNotifyDialog dialog = new InGameNotifyDialog(GameFrame.getInstance(), false, GameFrame.TTS_SERVER ? Translator.translate("sound.tts_activado_por_el_servidor") : Translator.translate("sound.tts_desactivado_por_el_servidor"), GameFrame.TTS_SERVER ? new Color(0, 130, 0) : Color.RED, Color.WHITE, null, NOTIFICATION_TIMEOUT);
                                                                dialog.setLocation(dialog.getParent().getLocation());
                                                                dialog.setVisible(true);
                                                            });
                                                            break;
                                                        case "PAUSE":
                                                            final String[] partes_final_pause = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                synchronized (GameFrame.getInstance().getLock_pause()) {
                                                                    if (("0".equals(partes_final_pause[3]) && GameFrame.getInstance().isTimba_pausada()) || ("1".equals(partes_final_pause[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                                        GameFrame.getInstance().pauseTimba(null);
                                                                    }
                                                                }
                                                            });
                                                            break;
                                                        case "PERMUTATIONKEY":
                                                            final String[] partes_final_permutationkey = partes_comando;
                                                            Helpers.threadRun(() -> {
                                                                try {
                                                                    byte[] masterKey = Panoptes.getInstance().stateGetShuffleKeyShare();
                                                                    String mkBase64 = (masterKey != null) ? org.apache.commons.codec.binary.Base64.encodeBase64String(masterKey) : "*";
                                                                    String response = "GAME#" + String.valueOf(Helpers.CSPRNG_GENERATOR.nextInt()) + "#PERMUTATIONKEY#" + mkBase64;
                                                                    writeCommandToServer(Helpers.encryptCommand(response, local_client_aes_key, local_client_hmac_key));
                                                                } catch (Exception e) {
                                                                    try {
                                                                        writeCommandToServer(Helpers.encryptCommand("GAME#" + String.valueOf(Helpers.CSPRNG_GENERATOR.nextInt()) + "#PERMUTATIONKEY#*", local_client_aes_key, local_client_hmac_key));
                                                                    } catch (Exception ex) {
                                                                    }
                                                                }
                                                            });
                                                            break;
                                                        case "SHOWCARDS":
                                                            GameFrame.getInstance().getCrupier().showPlayerCards(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"), partes_comando[4], partes_comando[5]);
                                                            break;
                                                        case "REBUYNOW":
                                                            GameFrame.getInstance().getCrupier().rebuyNow(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"), Integer.parseInt(partes_comando[4]));
                                                            break;
                                                        case "EXIT":
                                                            GameFrame.getInstance().getCrupier().remotePlayerQuit(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"));
                                                            break;
                                                        case "LASTHAND":
                                                            if (partes_comando[3].equals("0")) {
                                                                GameFrame.getInstance().getCrupier().setForce_recover(false);
                                                                GameFrame.getInstance().getTapete().getCommunityCards().last_hand_off();
                                                            } else {
                                                                if (partes_comando[3].equals("2")) {
                                                                    GameFrame.getInstance().getCrupier().setForce_recover(true);
                                                                    if (partes_comando.length > 4) {
                                                                        password = new String(Base64.getDecoder().decode(partes_comando[4]), "UTF-8");
                                                                    }
                                                                }
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
                                                            break;
                                                        case "SERVEREXITRECOVER":
                                                            exit = true;
                                                            GameFrame.getInstance().getCrupier().setForce_recover(true);
                                                            if (partes_comando.length > 3) {
                                                                password = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
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
                                                                if (game_info2[0].trim().matches("[0-9,.*]+")) {
                                                                    boolean rebuy = !game_info2[0].trim().endsWith("*");
                                                                    game_info_buyin.setText(Helpers.float2String(Float.parseFloat(game_info2[0].replace("*", ""))) + (rebuy ? "" : "*"));
                                                                    game_info_blinds.setText(game_info2[1]);
                                                                    if (game_info2.length > 2) {
                                                                        game_info_hands.setText(game_info2[2]);
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
                                                        case "DELUSER":
                                                            borrarParticipante(new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8"));
                                                            break;
                                                        case "NEWUSER":
                                                            Audio.playWavResource("misc/laser.wav");
                                                            String nick = new String(Base64.getDecoder().decode(partes_comando[3]), "UTF-8");
                                                            File avatar = null;
                                                            int file_id = Helpers.CSPRNG_GENERATOR.nextInt();
                                                            if (file_id < 0) {
                                                                file_id *= -1;
                                                            }
                                                            if (partes_comando.length == 6) {
                                                                avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));
                                                                try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                    os.write(Base64.getDecoder().decode(partes_comando[5]));
                                                                }
                                                            }
                                                            if (!participantes.containsKey(nick)) {
                                                                nuevoParticipante(nick, avatar, null, null, null, false, "1".equals(partes_comando[4]));
                                                            }
                                                            break;
                                                        case "USERSLIST":
                                                            String[] current_users_parts = partes_comando[3].split("@");
                                                            for (String user : current_users_parts) {
                                                                String[] user_parts = user.split("\\|");
                                                                nick = new String(Base64.getDecoder().decode(user_parts[0]), "UTF-8");
                                                                avatar = null;
                                                                if (user_parts.length == 3) {
                                                                    file_id = Helpers.CSPRNG_GENERATOR.nextInt();
                                                                    if (file_id < 0) {
                                                                        file_id *= -1;
                                                                    }
                                                                    avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));
                                                                    try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                        os.write(Base64.getDecoder().decode(user_parts[2]));
                                                                    }
                                                                }
                                                                if (!participantes.containsKey(nick)) {
                                                                    nuevoParticipante(nick, avatar, null, null, null, false, "1".equals(user_parts[1]));
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
                                                            GameFrame.CIEGA_PEQUEÑA = Float.parseFloat(partes_comando[4]);
                                                            GameFrame.CIEGA_GRANDE = Float.parseFloat(partes_comando[5]);
                                                            String[] ciegas_double = partes_comando[6].split("@");
                                                            GameFrame.CIEGAS_DOUBLE = Integer.parseInt(ciegas_double[0]);
                                                            GameFrame.CIEGAS_DOUBLE_TYPE = Integer.parseInt(ciegas_double[1]);
                                                            GameFrame.RECOVER = Boolean.parseBoolean(partes_comando[7].split("@")[0]);
                                                            GameFrame.UGI = partes_comando[7].split("@")[1];
                                                            GameFrame.REBUY = Boolean.parseBoolean(partes_comando[8]);
                                                            GameFrame.MANOS = Integer.parseInt(partes_comando[9]);
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
                                } else {
                                    if (!exit && !WaitingRoomFrame.getInstance().isExit()) {
                                        LOGGER.log(Level.WARNING, "SOCKET RECEIVED POISON PILL");
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
                    if (local_client_socket == null) {
                        booting = false;
                        Helpers.GUIRunAndWait(() -> {
                            status.setForeground(Color.red);
                            Helpers.resetBarra(barra, CLIENT_REC_WAIT);
                        });
                        for (int i = CLIENT_REC_WAIT; i > 0 && !exit; i--) {
                            int j = i;
                            Helpers.GUIRun(() -> {
                                status.setIcon(new ImageIcon(getClass().getResource("/images/gears.gif")));
                                status.setText(Translator.translate("status.error_reconectando") + " " + j + " " + Translator.translate("status.segs"));
                                barra.setValue(j);
                            });
                            if (!exit) {
                                synchronized (lock_client_reconnect) {
                                    try {
                                        lock_client_reconnect.wait(1000);
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                        }
                    } else {
                        mostrarMensajeError(THIS, "SOMETHING FAILED. You have lost connection with the server.");
                    }
                }
            } while (!exit && local_client_socket == null);
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

        StringBuilder commandBuilder = new StringBuilder("USERSLIST#");

        // Lock the map while iterating to prevent crashes if someone disconnects
        synchronized (participantes) {
            for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                Participant p = entry.getValue();
                try {
                    if (p != null && p != par) {
                        commandBuilder.append(Base64.getEncoder().encodeToString(p.getNick().getBytes("UTF-8")))
                                .append("|")
                                .append(p.isUnsecure_player() ? "1" : "0");

                        if (p.getAvatar() != null || p.isCpu()) {
                            byte[] avatar_b;
                            try (InputStream is = !p.isCpu() ? new FileInputStream(p.getAvatar())
                                    : WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                                avatar_b = is.readAllBytes();
                            }
                            commandBuilder.append("|").append(Base64.getEncoder().encodeToString(avatar_b));
                        }
                        commandBuilder.append("@");
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        this.sendASYNCGAMECommandFromServer(commandBuilder.toString(), par);
    }

    private void serverSocketHandler(final Socket client_socket) {

        Helpers.threadRun(() -> {

            LOGGER.log(Level.INFO, "A client is trying to connect...");
            client_threads.add(Thread.currentThread().threadId());
            String recibido;
            String[] partes;
            try {
                client_socket.setTcpNoDelay(true);
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
                    dOut.flush();

                    // --- PANOPTES: SERVER CHALLENGES CLIENT ---
                    // FIX RACE CONDITION: Usamos la IP/Puerto REMOTOS del cliente SÓLO para aislar la sesión en RAM
                    String clientRemoteIp = client_socket.getInetAddress().getHostAddress();
                    int clientRemotePort = client_socket.getPort();
                    String tempSessionId = clientRemoteIp + ":" + clientRemotePort;

                    // FIX FALSO POSITIVO: Pero al motor C le pasamos la IP y Puerto LOCALES del servidor.
                    // Esto es vital porque es el destino que el cliente buscará en su netstat/tabla TCP.
                    String serverLocalIp = client_socket.getLocalAddress().getHostAddress();
                    int serverLocalPort = client_socket.getLocalPort();

                    Panoptes panoptes = Panoptes.getInstance();
                    byte[] serverChallengeToClient = null;
                    try {
                        serverChallengeToClient = panoptes.generateChallenge(tempSessionId, serverLocalIp, serverLocalPort);
                    } catch (Exception e) {
                        Helpers.mostrarMensajeError(THIS, Translator.translate("error.fatal_panoptes_challenge"));
                        LOGGER.log(Level.SEVERE, "FATAL ERROR: Failed to generate Panoptes challenge", e);
                        System.exit(1);
                    }

                    if (serverChallengeToClient != null) {
                        dOut.writeInt(serverChallengeToClient.length);
                        dOut.write(serverChallengeToClient);
                        dOut.flush();
                    }

                    serverKeyAgree.doPhase(clientPubKey, true);
                    byte[] serverSharedSecret = serverKeyAgree.generateSecret();
                    byte[] secret_hash = MessageDigest.getInstance("SHA-512").digest(serverSharedSecret);
                    SecretKeySpec aes_key = new SecretKeySpec(secret_hash, 0, 16, "AES");
                    SecretKeySpec hmac_key = new SecretKeySpec(secret_hash, 32, 32, "HmacSHA256");
                    /* FIN INTERCAMBIO DE CLAVES */

                    // A partir de aquí sigue igual, leyendo el comando del cliente...
                    recibido = readCommandFromClient(client_socket, aes_key, hmac_key);
                    partes = recibido.split("#");
                    String client_nick = new String(Base64.getDecoder().decode(partes[0]), "UTF-8");

                    // ... (resto de tu código serverSocketHandler)
                    String[] versionAndPayload = partes[1].split("@");
                    String client_version = versionAndPayload[0];
                    String securityPayload = versionAndPayload.length > 1 ? versionAndPayload[1] : "";

                    String[] tokensSeguridad = securityPayload.split("\\$");
                    String clientChallengeBase64 = tokensSeguridad.length > 0 ? tokensSeguridad[0] : "";
                    String clientSignatureBase64 = tokensSeguridad.length > 1 ? tokensSeguridad[1] : "";
                    String clientPubKeyBase64 = tokensSeguridad.length > 2 ? tokensSeguridad[2] : "";

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
                                LOGGER.log(Level.WARNING, "Resetting client socket...");

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

                                    LOGGER.log(Level.WARNING, "CLIENT {0} HAS RECONNECTED SUCCESSFULLY.", client_nick);

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
                                    LOGGER.log(Level.WARNING, "CLIENT {0} FAILED TO RECONNECT", client_nick);
                                    try {
                                        if (!client_socket.isClosed()) {
                                            client_socket.close();
                                        }
                                    } catch (Exception ex) {
                                    }
                                }

                            } else {
                                LOGGER.log(Level.WARNING, "CLIENT {0} FAILED TO RECONNECT (BAD HMAC)", client_nick);
                                try {
                                    if (!client_socket.isClosed()) {
                                        client_socket.close();
                                    }
                                } catch (Exception ex) {
                                }
                            }
                            if (rec_error) {
                                Helpers.threadRun(() -> {
                                    Helpers.mostrarMensajeError(THIS,
                                            Translator.translate("conn.error_al_intentar_reconectar") + client_nick);
                                });
                            }
                        } else {
                            LOGGER.log(Level.WARNING, "User {0} TRYING TO RECONNECT TO PREVIOUS GAME -> DENIED", client_nick);
                            try {
                                if (!client_socket.isClosed()) {
                                    client_socket.close();
                                }
                            } catch (Exception ex) {
                            }
                        }
                    } else if (!client_version.equals(AboutDialog.VERSION)) {
                        writeCommandFromServer(
                                Helpers.encryptCommand("BADVERSION#" + AboutDialog.VERSION, aes_key, hmac_key),
                                client_socket);
                    } else if (password != null && ("*".equals(partes[3])
                            || !password.equals(new String(Base64.getDecoder().decode(partes[3]), "UTF-8")))) {
                        writeCommandFromServer(Helpers.encryptCommand("BADPASSWORD", aes_key, hmac_key), client_socket);
                    } else if (WaitingRoomFrame.getInstance().isPartida_empezando()
                            || WaitingRoomFrame.getInstance().isPartida_empezada()) {
                        writeCommandFromServer(Helpers.encryptCommand("YOUARELATE", aes_key, hmac_key), client_socket);

                        try {
                            String ipCliente = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                                    .digest(client_socket.getInetAddress().getHostAddress().getBytes()));

                            if (!late_clients_warning.contains(ipCliente)) {
                                Audio.playWavResource("misc/new_user.wav");
                                late_clients_warning.add(ipCliente);
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
                                "El usuario {0} LLEGA TARDE -> DENEGADO", client_nick);

                    } else if (participantes.size() == MAX_PARTICIPANTES) {
                        writeCommandFromServer(Helpers.encryptCommand("NOSPACE", aes_key, hmac_key), client_socket);
                    } else if (participantes.containsKey(client_nick)) {
                        writeCommandFromServer(Helpers.encryptCommand("NICKFAIL", aes_key, hmac_key), client_socket);
                    } else {
                        // Procesamos su avatar
                        String client_avatar_base64 = partes[2];
                        try {
                            if (!"*".equals(client_avatar_base64)) {
                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();
                                if (file_id < 0) {
                                    file_id *= -1;
                                }
                                client_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + client_nick
                                        + "_avatar" + String.valueOf(file_id));

                                try (FileOutputStream os = new FileOutputStream(client_avatar)) {
                                    os.write(Base64.getDecoder().decode(client_avatar_base64));
                                }
                            }
                        } catch (Exception ex) {
                            client_avatar = null;
                        }

                        // --- PANOPTES PHASE 1: SIGN CLIENT'S CHALLENGE ---
                        String serverSignatureBase64 = "";
                        try {
                            byte[] challengeBytes = Base64.getDecoder().decode(clientChallengeBase64);
                            byte[] signatureBytes = panoptes.signChallenge(challengeBytes);
                            if (signatureBytes != null) {
                                serverSignatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
                            } else {
                                serverSignatureBase64 = Base64.getEncoder().encodeToString(new byte[72]);
                            }
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE,
                                    "Failed to sign Panoptes challenge", e);
                            serverSignatureBase64 = Base64.getEncoder().encodeToString(new byte[72]);
                        }

                        writeCommandFromServer(Helpers.encryptCommand(
                                "NICKOK#" + (password == null ? "0" : "1") + "#" + serverSignatureBase64 + "#"
                                + Base64.getEncoder().encodeToString(
                                        (game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|"
                                                + game_info_hands.getText()).getBytes("UTF-8")),
                                aes_key, hmac_key), client_socket);

                        byte[] avatar_bytes = null;

                        if (local_avatar != null && local_avatar.length() > 0) {
                            try (FileInputStream is = new FileInputStream(local_avatar)) {
                                avatar_bytes = is.readAllBytes();
                            }
                        }

                        writeCommandFromServer(Helpers.encryptCommand(
                                Base64.getEncoder().encodeToString(local_nick.getBytes("UTF-8"))
                                + (avatar_bytes != null ? "#" + Base64.getEncoder().encodeToString(avatar_bytes) : ""),
                                aes_key, hmac_key), client_socket);

                        writeCommandFromServer(Helpers.encryptCommand(
                                chat_text.toString().isEmpty() ? "*"
                                : Base64.getEncoder().encodeToString(chat_text.toString().getBytes("UTF-8")),
                                aes_key, hmac_key), client_socket);

                        writeCommandFromServer(
                                Helpers.encryptCommand(String.valueOf(GameFrame.RADAR_AVAILABLE), aes_key, hmac_key),
                                client_socket);

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
                                if (participantes.size() < MAX_PARTICIPANTES
                                        && !WaitingRoomFrame.getInstance().isPartida_empezando()
                                        && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                                    nuevoParticipante(client_nick, client_avatar, client_socket, aes_key, hmac_key,
                                            false, false);
                                    Audio.playWavResource("misc/laser.wav");

                                    // --- PANOPTES: GUARDAMOS LA CLAVE PÚBLICA DEL CLIENTE ---
                                    if (!clientPubKeyBase64.isEmpty()) {
                                        participantes.get(client_nick).setPanoptes_public_key(Base64.getDecoder().decode(clientPubKeyBase64));
                                    }
                                    // --------------------------------------------------------

                                    // --- PANOPTES: SERVER VERIFIES CLIENT ---
                                    int isClientLegit = panoptes.verifyResponse(tempSessionId, Base64.getDecoder().decode(clientSignatureBase64));

                                    if (isClientLegit == Panoptes.STATUS_FAILED) {
                                        participantes.get(client_nick).setUnsecure_player(true);
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).warning(client_nick + " GAME BINARY IS MODIFIED OR GUARDIAN TRIGGERED (cheating?)");
                                    } else if (isClientLegit == Panoptes.STATUS_VM_DETECTED) {
                                        Helpers.threadRun(() -> {
                                            mostrarMensajeInformativo(THIS, Translator.translate("ui.error.vm_detected_client") + " [" + client_nick + "]");
                                        });
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).warning(client_nick + " is running on a Virtual Machine.");
                                    }

                                    if (participantes.size() > 2) {
                                        enviarListaUsuariosActualesAlNuevoUsuario(participantes.get(client_nick));

                                        String comando = "NEWUSER#"
                                                + Base64.getEncoder().encodeToString(client_nick.getBytes("UTF-8")) + "#"
                                                + (participantes.get(client_nick).isUnsecure_player() ? "1" : "0");

                                        if (client_avatar != null) {
                                            byte[] avatar_b;
                                            try (FileInputStream is = new FileInputStream(client_avatar)) {
                                                avatar_b = is.readAllBytes();
                                            }
                                            comando += "#" + Base64.getEncoder().encodeToString(avatar_b);
                                        }
                                        broadcastASYNCGAMECommandFromServer(comando, participantes.get(client_nick));
                                    }
                                    Helpers.GUIRun(() -> {
                                        kick_user.setEnabled(true);
                                        new_bot_button
                                                .setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                                    });
                                    LOGGER.log(Level.INFO, "{0} CONNECTED", client_nick);
                                } else {
                                    try (client_socket) {
                                        LOGGER.log(Level.INFO,
                                                "{0} NO PUDO CONECTAR CORRECTAMENTE (PARTIDA LLENA O EMPEZADA)",
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
                                "BAD MAGIC BYTES FROM CLIENT!");
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            client_threads.remove(Thread.currentThread().threadId());
        });

    }

    private void servidor() {

        server_nick = local_nick;

        gen_priv_session_key();

        if (participantes.get(local_nick) != null) {
            participantes.get(local_nick).setPanoptes_public_key(local_player_public_key);
        }

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
                                    "NO HA SIDO POSIBLE MAPEAR AUTOMÁTICAMENTE EL PUERTO USANDO UPnP\n\n(Si quieres compartir la timba por Internet deberás activar UPnP en tu router o mapear el puerto de forma manual)");
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

                        mostrarMensajeError(THIS,
                                "ALGO HA FALLADO. (Probablemente ya hay una timba creada en el mismo puerto).");
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

    public synchronized void borrarParticipante(String nick) {

        if (this.participantes.containsKey(nick)) {

            Audio.playWavResource("misc/toilet.wav");

            // Get the reference BEFORE removing it from the map
            Participant pToDel = participantes.get(nick);
            String avatar_src = pToDel.getAvatar_chat_src();

            participantes.remove(nick);

            Helpers.GUIRun(() -> {
                tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);
                // Removed tot_conectados.revalidate/repaint

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

                chatHTMLAppendExitUser(nick, avatar_src);

                // Removed global revalidate() and repaint()
            });

            if (this.isServer() && !WaitingRoomFrame.getInstance().isPartida_empezada() && !exit) {
                try {
                    String comando = "DELUSER#" + Base64.getEncoder().encodeToString(nick.getBytes("UTF-8"));
                    // We safely pass the cached reference 'pToDel', preventing a NullPointerException
                    this.broadcastASYNCGAMECommandFromServer(comando, pToDel);
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private synchronized void nuevoParticipante(String nick, File avatar, Socket socket, SecretKeySpec aes_k,
            SecretKeySpec hmac_k, boolean cpu, boolean unsecure) {

        Participant participante = new Participant(this, nick, avatar, socket, aes_k, hmac_k, cpu);

        participantes.put(nick, participante);

        participante.setUnsecure_player(unsecure);

        if (socket != null) {

            Helpers.threadRun(participante);

        }

        Helpers.GUIRun(() -> {
            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);
            // Removed manual revalidate/repaint

            ParticipantJListData participant_data = new ParticipantJListData(nick);
            ImageIcon participant_avatar = null;

            if (avatar != null) {
                try {
                    participant_avatar = Helpers.scaleIcon(avatar.getAbsolutePath(), NewGameDialog.DEFAULT_AVATAR_WIDTH,
                            NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null,
                            ex);
                }
            } else {
                try {
                    participant_avatar = Helpers.scaleIcon(
                            getClass().getResource(
                                    (server && cpu) ? "/images/avatar_bot.png" : "/images/avatar_default.png"),
                            NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH);
                } catch (MalformedURLException ex) {
                    System.getLogger(WaitingRoomFrame.class.getName()).log(System.Logger.Level.ERROR, (String) null,
                            ex);
                }
            }

            participant_data.setAvatar(participant_avatar);

            ((DefaultListModel) conectados.getModel()).addElement(participant_data);

            if (!nick.equals(server_nick) && !nick.equals(local_nick)) {
                chatHTMLAppendNewUser(nick);
            }

            // Removed global revalidate() and repaint()
        });

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        main_scroll_panel = new javax.swing.JScrollPane();
        main_panel = new javax.swing.JPanel();
        panel_arriba = new javax.swing.JPanel();
        status = new javax.swing.JLabel();
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
        main_scroll_panel.setPreferredSize(new java.awt.Dimension(700, 750));

        panel_arriba.setPreferredSize(new java.awt.Dimension(700, 487));

        status.setFont(new java.awt.Font("Dialog", 1, 20)); // NOI18N
        status.setForeground(new java.awt.Color(51, 153, 0));
        status.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido. (SHIFT + ARRIBA/ABAJO PARA CAMBIAR VOLUMEN)");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        panel_con.setFocusable(false);
        panel_con.setOpaque(false);

        panel_conectados.setFocusable(false);
        panel_conectados.setOpaque(false);

        conectados.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        conectados.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        conectados.setToolTipText("Participantes conectados");
        conectados.setCellRenderer(new com.tonikelope.coronapoker.ParticipantsListLabel());
        conectados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        conectados.setFocusable(false);
        conectados.setOpaque(false);
        panel_conectados.setViewportView(conectados);

        kick_user.setBackground(new java.awt.Color(255, 0, 0));
        kick_user.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        kick_user.setForeground(new java.awt.Color(255, 255, 255));
        kick_user.setText("Expulsar jugador");
        kick_user.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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
        new_bot_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                new_bot_buttonActionPerformed(evt);
            }
        });

        game_info_blinds.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_blinds.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/ciegas.png"))); // NOI18N
        game_info_blinds.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_blinds.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_info_blindsMouseClicked(evt);
            }
        });

        game_info_hands.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info_hands.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu/meter.png"))); // NOI18N
        game_info_hands.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info_hands.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_info_handsMouseClicked(evt);
            }
        });

        logo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        logo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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

        server_address_label.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        server_address_label.setText("1.1.1.1");
        server_address_label.setToolTipText("Click para obtener datos de conexión");
        server_address_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        server_address_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                server_address_labelMouseClicked(evt);
            }
        });

        radar.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/shield.png")).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)));

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
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(server_address_label, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
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
        danger_server.setText("POSIBLE SERVIDOR TRAMPOSO");
        danger_server.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        danger_server.setOpaque(true);

        chat_notifications.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        chat_notifications.setText("Notificaciones del chat durante el juego");
        chat_notifications.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat_notifications.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_notificationsActionPerformed(evt);
            }
        });

        chat_scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chat_scroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        chat.setEditable(false);
        chat.setBorder(null);
        chat.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        chat.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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
        chat_box.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_boxActionPerformed(evt);
            }
        });

        emoji_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/emoji_chat/1.png"))); // NOI18N
        emoji_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        emoji_button.setMargin(new java.awt.Insets(2, 2, 2, 2));
        emoji_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                emoji_buttonActionPerformed(evt);
            }
        });

        image_button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/chat_image.png"))); // NOI18N
        image_button.setToolTipText("Enviar imagen");
        image_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        image_button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        image_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                image_buttonActionPerformed(evt);
            }
        });

        send_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        send_label.setFocusable(false);
        send_label.setRequestFocusEnabled(false);
        send_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                send_labelMouseClicked(evt);
            }
        });

        max_min_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        max_min_label.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                max_min_labelMouseClicked(evt);
            }
        });

        avatar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        avatar_label.setText("Toni");
        avatar_label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

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

    private void kick_userActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_kick_userActionPerformed

        int selectedIndex = conectados.getSelectedIndex();

        // TODO add your handling code here:
        if (selectedIndex != -1) {

            DefaultListModel<ParticipantJListData> model = (DefaultListModel<ParticipantJListData>) conectados
                    .getModel();
            ParticipantJListData p = model.getElementAt(selectedIndex);

            String expulsado = p.getNick();

            if (!expulsado.equals(local_nick)) {

                // Cambiamos la contraseña por una aleatoria
                if (password != null && !participantes.get(expulsado).isCpu()) {
                    password = Helpers.genRandomString(password.length());

                }

                kick_user.setEnabled(false);

                Helpers.threadRun(() -> {
                    try {

                        participantes.get(expulsado).setExit(true);

                        if (!participantes.get(expulsado).isCpu()) {

                            String comando = "KICKED#" + Base64.getEncoder().encodeToString(expulsado.getBytes("UTF-8"));
                            participantes.get(expulsado).writeCommandFromServer(
                                    Helpers.encryptCommand(comando, participantes.get(expulsado).getAes_key(),
                                            participantes.get(expulsado).getHmac_key()));
                        }

                        participantes.get(expulsado).exitAndCloseSocket();

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
                    if (password != null && !participantes.get(expulsado).isCpu()) {
                        Helpers.copyTextToClipboard(password);
                        mostrarMensajeInformativo(THIS, Translator.translate("ui.error.password_copiada"));
                    }
                });
            }
        } else {

            mostrarMensajeError(THIS, Translator.translate("ui.tienes_que_seleccionar_algun_participante"));
            chat_box.requestFocus();
        }

    }// GEN-LAST:event_kick_userActionPerformed

    private void empezar_timbaActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_empezar_timbaActionPerformed
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

                            if (ocupados) {
                                synchronized (lock_client_pre_game_commands_wait) {
                                    try {
                                        lock_client_pre_game_commands_wait.wait(PRE_GAME_COMMANDS_LOCK);
                                    } catch (InterruptedException ex) {
                                    }
                                }
                            }
                        } while (ocupados);

                        p2pSwarmManager.startSwarm();
                    }
                });
            }
        } else {
            chat_box.requestFocus();
        }
        revalidate();
        repaint();

    }// GEN-LAST:event_empezar_timbaActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowClosing
        protect_focus = false;

        if (!barra.isVisible() || !booting) {

            if (!booting && client_threads.isEmpty() && !partida_empezando) {

                if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {

                    if (exit || reconnecting) {
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
                            } else if (local_client_socket != null && !reconnecting) {
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
                                    local_client_socket.close();
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

            synchronized (lock_client_reconnect) {
                lock_client_reconnect.notifyAll();
            }
        } else if (booting && mostrarMensajeInformativoSINO(THIS, Translator.translate("ui.forzar_cierre"), new ImageIcon(Init.class.getResource("/images/exit.png"))) == 0) {
            exit = true;
            Helpers.savePropertiesFile();
            System.exit(1);
        }

    }// GEN-LAST:event_formWindowClosing

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

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
    }// GEN-LAST:event_sound_iconMouseClicked

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_logoMouseClicked
        // TODO add your handling code here:

        boolean auto_f = protect_focus;

        protect_focus = false;

        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);

        protect_focus = auto_f;
    }// GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_new_bot_buttonActionPerformed

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
                    try (InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                        avatar_b = is.readAllBytes();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                    comando += "#" + Base64.getEncoder().encodeToString(avatar_b);

                    synchronized (lock_new_client) {
                        nuevoParticipante(bot_nick, null, null, null, null, true, false);

                        /* PANOPTES: ZERO-TRUST DETERMINISTIC BOT KEYS */
                        byte[] botPriv = java.security.MessageDigest.getInstance("SHA-256").digest(bot_nick.getBytes("UTF-8"));
                        participantes.get(bot_nick).setPanoptes_private_key(botPriv);
                        participantes.get(bot_nick).setPanoptes_public_key(Panoptes.getInstance().utilsGetPublicKey(botPriv));
                        /* ------------------------------------------- */

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
                } catch (NoSuchAlgorithmException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            });
        }
    }// GEN-LAST:event_new_bot_buttonActionPerformed

    private void pass_iconMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_pass_iconMouseClicked
        // TODO add your handling code here:

        if (server && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
            if (mostrarMensajeInformativoSINO(this, Translator.translate("auth.generar_contrasena_nueva")) == 0) {
                password = Helpers.genRandomString(GEN_PASS_LENGTH);
                pass_icon.setToolTipText(password);
            }

            if (password != null) {
                pass_icon.setEnabled(true);
                Helpers.copyTextToClipboard(password);
                mostrarMensajeInformativo(this, Translator.translate("auth.password_copiada_en_el_portapapeles"));
            }
        }
    }// GEN-LAST:event_pass_iconMouseClicked

    private void tts_warningMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_tts_warningMouseClicked
        // TODO add your handling code here:

        mostrarMensajeInformativo(this,
                Translator.translate("ui.tts_warning_detail"),
                "justify", (int) Math.round(getWidth() * 0.8f));
    }// GEN-LAST:event_tts_warningMouseClicked

    private void server_address_labelMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_server_address_labelMouseClicked

        if (server) {
            // TODO add your handling code here:

            Helpers.copyTextToClipboard("[CoronaPoker] INTERNET -> " + Helpers.getMyPublicIP() + ":"
                    + String.valueOf(server_socket.getLocalPort()) + "\n\nLAN -> " + Helpers.getMyLocalIP() + ":"
                    + String.valueOf(server_socket.getLocalPort()));
            mostrarMensajeInformativo(this, Translator.translate("conn.datos_de_conexion_copiados_en"));
        }
    }// GEN-LAST:event_server_address_labelMouseClicked

    private void game_info_buyinMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_game_info_buyinMouseClicked
        // TODO add your handling code here:

        if (server && !GameFrame.isRECOVER() && !isPartida_empezada() && !isPartida_empezando()
                && game_info_buyin.isEnabled()) {

            game_info_buyin.setEnabled(false);

            game_info_blinds.setEnabled(false);

            game_info_hands.setEnabled(false);

            NewGameDialog dialog = new NewGameDialog(this, true);

            dialog.setLocationRelativeTo(dialog.getParent());

            dialog.setVisible(true);

            if (dialog.isDialog_ok()) {

                game_info_buyin.setText(Helpers.float2String((float) GameFrame.BUYIN) + (GameFrame.REBUY ? "" : "*"));

                game_info_blinds.setText(Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / "
                        + Helpers.float2String(GameFrame.CIEGA_GRANDE)
                        + (GameFrame.CIEGAS_DOUBLE > 0
                                ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE)
                                + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*")
                                : ""));

                game_info_hands.setText(GameFrame.MANOS != -1 ? String.valueOf(GameFrame.MANOS) : "");

                game_info_hands.setVisible(!"".equals(game_info_hands.getText()));

                Helpers.threadRun(() -> {
                    try {
                        broadcastASYNCGAMECommandFromServer("GAMEINFO#" + Base64.getEncoder().encodeToString((game_info_buyin.getText() + "|" + game_info_blinds.getText() + "|"
                                + game_info_hands.getText()).getBytes("UTF-8")),
                                null);
                    } catch (UnsupportedEncodingException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                    Helpers.GUIRun(() -> {
                        game_info_buyin.setEnabled(true);
                        game_info_blinds.setEnabled(true);
                        game_info_hands.setEnabled(true);
                    });
                });

                pack();

                Helpers.windowAutoFitToRemoveHScrollBar(this, main_scroll_panel.getHorizontalScrollBar(),
                        (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth(), 0.1f);

            } else {
                game_info_buyin.setEnabled(true);
                game_info_blinds.setEnabled(true);
                game_info_hands.setEnabled(true);
            }

        }

    }// GEN-LAST:event_game_info_buyinMouseClicked

    private void chat_notificationsActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_chat_notificationsActionPerformed
        // TODO add your handling code here:

        CHAT_GAME_NOTIFICATIONS = chat_notifications.isSelected();

        Helpers.PROPERTIES.setProperty("chat_game_notifications", String.valueOf(CHAT_GAME_NOTIFICATIONS));

        Helpers.savePropertiesFile();
    }// GEN-LAST:event_chat_notificationsActionPerformed

    private void emoji_buttonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_emoji_buttonActionPerformed
        // TODO add your handling code here:

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
    }// GEN-LAST:event_emoji_buttonActionPerformed

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_chat_boxActionPerformed
        // TODO add your handling code here:

        String mensaje = chat_box.getText().trim();

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
    }// GEN-LAST:event_chat_boxActionPerformed

    private void image_buttonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_image_buttonActionPerformed
        // TODO add your handling code here:
        boolean auto_f = protect_focus;

        protect_focus = false;
        ChatImageDialog chat_image_dialog = new ChatImageDialog(this, true, (int) Math.round(this.getHeight() * 0.9f));
        chat_image_dialog.setLocationRelativeTo(this);
        chat_image_dialog.setVisible(true);

        protect_focus = auto_f;

    }// GEN-LAST:event_image_buttonActionPerformed

    private void chatFocusLost(java.awt.event.FocusEvent evt) {// GEN-FIRST:event_chatFocusLost
        // TODO add your handling code here:
        this.chat_scroll.getVerticalScrollBar().setValue(this.chat_scroll.getVerticalScrollBar().getMaximum());
        this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.chat_scroll.setBorder(chat_scroll_border);
        ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.UPDATE_WHEN_ON_EDT);
        chat.setFocusable(false);
    }// GEN-LAST:event_chatFocusLost

    private void emoji_scroll_panelComponentHidden(java.awt.event.ComponentEvent evt) {// GEN-FIRST:event_emoji_scroll_panelComponentHidden
        // TODO add your handling code here:
        emoji_panel.refreshEmojiHistory();
    }// GEN-LAST:event_emoji_scroll_panelComponentHidden

    private void formWindowOpened(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowOpened
        // TODO add your handling code here:
        chat_box.requestFocus();
    }// GEN-LAST:event_formWindowOpened

    private void send_labelMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_send_labelMouseClicked
        // TODO add your handling code here:
        chat_boxActionPerformed(null);
    }// GEN-LAST:event_send_labelMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {// GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        if (isPartida_empezada() && panel_arriba.isVisible()) {

            panel_arriba.setVisible(false);

            Helpers.setScaledIconLabel(max_min_label,
                    getClass().getResource("/images/" + (panel_arriba.isVisible() ? "maximize" : "minimize") + ".png"),
                    chat_box.getHeight(), chat_box.getHeight());

            main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());
        }

        Helpers.setScaledIconLabel(sound_icon,
                getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png"), 30, 30);

        if (!chat_text.toString().isEmpty() && !protect_focus) {
            refreshChatPanel();
        }

        protect_focus = isPartida_empezada();

        setAlwaysOnTop(protect_focus);

        main_scroll_panel.getVerticalScrollBar().setValue(main_scroll_panel.getVerticalScrollBar().getMaximum());

        chat_box.requestFocus();

        revalidate();

        repaint();
    }// GEN-LAST:event_formComponentShown

    private void max_min_labelMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_max_min_labelMouseClicked
        // TODO add your handling code here:
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

    }// GEN-LAST:event_max_min_labelMouseClicked

    private void formComponentHidden(java.awt.event.ComponentEvent evt) {// GEN-FIRST:event_formComponentHidden
        // TODO add your handling code here:

        if (!protect_focus) {
            chat.setText("<html><body style='background-image: url(" + background_chat_src + ")'></body></html>");
            chat_box.requestFocus();
        }

        if (partida_empezando) {
            partida_empezando = false;
        }
    }// GEN-LAST:event_formComponentHidden

    private void formWindowStateChanged(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowStateChanged
        // TODO add your handling code here:

        if ((evt.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            avatar_label.setText(this.local_nick);
        } else {
            avatar_label.setText("");
        }
    }// GEN-LAST:event_formWindowStateChanged

    private void chatMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_chatMouseClicked
        // TODO add your handling code here:

        if (!chat.isFocusable()) {
            this.chat_scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            this.chat_scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Color.GREEN, 3));
            ((DefaultCaret) chat.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            chat.setFocusable(true);
            chat.requestFocus();
        }
    }// GEN-LAST:event_chatMouseClicked

    private void formWindowDeactivated(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowDeactivated
        // TODO add your handling code here:
        if (protect_focus) {

            setVisible(false);

            Helpers.threadRun(() -> {
                Helpers.GUIRun(() -> {
                    setVisible(true);
                });
            });
        }
    }// GEN-LAST:event_formWindowDeactivated

    private void formWindowDeiconified(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowDeiconified
        // TODO add your handling code here:
        if (protect_focus) {
            protect_focus = false;
            setVisible(false);
        }
    }// GEN-LAST:event_formWindowDeiconified

    private void game_info_blindsMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_game_info_blindsMouseClicked
        // TODO add your handling code here:
        game_info_buyinMouseClicked(evt);
    }// GEN-LAST:event_game_info_blindsMouseClicked

    private void game_info_handsMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_game_info_handsMouseClicked
        // TODO add your handling code here:
        game_info_buyinMouseClicked(evt);
    }// GEN-LAST:event_game_info_handsMouseClicked

    private void chatCaretUpdate(javax.swing.event.CaretEvent evt) {// GEN-FIRST:event_chatCaretUpdate
        // TODO add your handling code here:

    }// GEN-LAST:event_chatCaretUpdate

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JProgressBar barra;
    private javax.swing.JEditorPane chat;
    private javax.swing.JTextField chat_box;
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
    private javax.swing.JLabel radar;
    private javax.swing.JLabel send_label;
    private javax.swing.JLabel server_address_label;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel status;
    private javax.swing.JLabel tot_conectados;
    private javax.swing.JLabel tts_warning;
    // End of variables declaration//GEN-END:variables
}
