/*
 * Copyright (C) 2020 tonikelope
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
import java.awt.Dimension;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
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
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTextArea;
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
    public static final int MAX_PING_PONG_ERROR = 3;
    public static final int EC_KEY_LENGTH = 256;
    public static final int GEN_PASS_LENGTH = 10;
    private static volatile boolean CHAT_GAME_NOTIFICATIONS = Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("chat_game_notifications", "true"));
    private static volatile WaitingRoomFrame THIS = null;

    private final Init ventana_inicio;
    private final File local_avatar;
    private final Map<String, Participant> participantes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Object local_client_socket_lock = new Object();
    private final Object keep_alive_lock = new Object();
    private final Object lock_new_client = new Object();
    private final Object lock_reconnect = new Object();
    private final boolean server;
    private final String local_nick;
    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();
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
    private volatile int pong;
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

    public static boolean isCHAT_GAME_NOTIFICATIONS() {
        return CHAT_GAME_NOTIFICATIONS;
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
            Helpers.playWavResource("misc/chat_alert.wav");
        }

        this.video_chat_link = video_chat_link;

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {
                video_chat_button.setEnabled(true);
            }
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

        Helpers.GUIRun(new Runnable() {
            @Override
            public void run() {

                danger_server.setVisible(unsecure_server);
                pack();

            }
        });
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

    public JTextArea getChat() {
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

    /**
     * Creates new form SalaEspera
     */
    public WaitingRoomFrame(Init ventana_ini, boolean local, String nick, String servidor_ip_port, File avatar, String pass, boolean use_upnp) {
        THIS = this;
        upnp = use_upnp;
        ventana_inicio = ventana_ini;
        server = local;
        local_nick = nick;
        server_ip_port = servidor_ip_port;
        local_avatar = avatar;
        password = pass;

        initComponents();

        setTitle(Init.WINDOW_TITLE + Translator.translate(" - Sala de espera (") + nick + ")");

        chat_notifications.setSelected(CHAT_GAME_NOTIFICATIONS);
        chat_notifications.setVisible(false);

        danger_server.setVisible(false);

        if (GameFrame.isRECOVER()) {
            game_info.setText("(CONTINUANDO TIMBA ANTERIOR)");
        }

        if (server) {

            if (GameFrame.isRECOVER()) {
                game_info.setToolTipText("Click para actualizar datos de la timba");
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

        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));

        kick_user.setEnabled(false);

        empezar_timba.setEnabled(false);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat);

        Helpers.JTextFieldRegularPopupMenu.addTo(chat_box);

        if (avatar != null) {
            avatar_label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            avatar_label.setIcon(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
        } else {
            avatar_label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));
            avatar_label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));

        }

        avatar_label.setText(local_nick);

        status1.setText(server_ip_port);

        DefaultListModel listModel = new DefaultListModel();

        if (server) {

            new_bot_button.setEnabled(true);

            status.setText("Esperando jugadores...");

            gameinfo_original = GameFrame.BUYIN + " " + (!GameFrame.REBUY ? "NO-REBUY | " : "| ") + Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / " + Helpers.float2String(GameFrame.CIEGA_GRANDE) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") : "") + (GameFrame.MANOS != -1 ? " | " + String.valueOf(GameFrame.MANOS) : "");

            if (game_info.isEnabled() && !GameFrame.isRECOVER()) {
                game_info.setText(gameinfo_original);
            }

            participantes.put(local_nick, null);

            tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

            ParticipantsListRenderer label = new ParticipantsListRenderer();

            label.setText(local_nick);

            label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));

            if (local_avatar != null) {
                label.setIcon(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
            } else {
                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
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
            status.setText("Conectando...");
            conectados.setModel(listModel);
            conectados.revalidate();
            conectados.repaint();
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();

        Helpers.muteLoopMp3("misc/background_music.mp3");

        Helpers.playLoopMp3Resource("misc/waiting_room.mp3");

        if (server) {
            servidor();
        } else {
            cliente();
        }
    }

    private void sqlSavePermutationkey() {

        try {

            String sql = "INSERT INTO permutationkey(hash, key) VALUES (?, ?)";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setString(1, this.local_client_permutation_key_hash);

            statement.setString(2, Base64.encodeBase64String(this.local_client_permutation_key.getEncoded()));

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private String sqlReadPermutationkey(String hash) {

        String ret = null;

        try {

            String sql = "SELECT key FROM permutationkey WHERE hash=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setString(1, hash);

            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                ret = rs.getString("key");
            }

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;

    }

    public void sqlRemovePermutationkey() {

        try {

            String sql = "DELETE FROM permutationkey WHERE hash=?";

            PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

            statement.setQueryTimeout(30);

            statement.setString(1, this.local_client_permutation_key_hash);

            statement.executeUpdate();

            statement.close();

        } catch (SQLException ex) {
            Logger.getLogger(GameFrame.class.getName()).log(Level.SEVERE, null, ex);
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

                        //Leemos el contenido del chat
                        String recibido;

                        ok_rec = false;

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Leyendo datos del chat...");

                        recibido = this.local_client_buffer_read_is.readLine();

                        if (recibido != null) {

                            recibido = Helpers.decryptCommand(recibido, local_client_aes_key, local_client_hmac_key);

                            if (recibido != null) {

                                if (!"*".equals(recibido)) {

                                    String chat_text;

                                    chat_text = new String(Base64.decodeBase64(recibido), "UTF-8");

                                    Helpers.GUIRun(new Runnable() {
                                        public void run() {

                                            chat.setText(chat_text);
                                        }
                                    });

                                }

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "HEMOS CONSEGUIDO RECONECTAR CORRECTAMENTE CON EL SERVIDOR");

                                ok_rec = true;

                            }

                        } else {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "EL SOCKET DE RECONEXiÓN RECIBIÓ NULL");
                        }

                    } catch (Exception ex) {

                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "EL SOCKET DE RECONEXiÓN PROVOCÓ UNA EXCEPCIÓN");
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

                                        Helpers.GUIRun(new Runnable() {
                                            public void run() {
                                                reconnect_dialog = new Reconnect2ServerDialog(GameFrame.getInstance() != null ? GameFrame.getInstance().getFrame() : THIS, true, server_ip_port);
                                                reconnect_dialog.setLocationRelativeTo(reconnect_dialog.getParent());
                                                reconnect_dialog.setVisible(true);

                                            }
                                        });

                                    } else {
                                        reconnect_dialog.setReconectar(false);

                                        Helpers.GUIRun(new Runnable() {
                                            public void run() {
                                                reconnect_dialog.reset();
                                                reconnect_dialog.setLocationRelativeTo(reconnect_dialog.getParent());
                                                reconnect_dialog.setVisible(true);

                                            }
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

                    Helpers.GUIRunAndWait(new Runnable() {
                        @Override
                        public void run() {
                            reconnect_dialog.dispose();
                            reconnect_dialog = null;
                        }
                    });
                }

                if (ok_rec) {
                    Helpers.playWavResource("misc/yahoo.wav");
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

    private void cliente() {

        booting = true;

        Helpers.threadRun(new Runnable() {

            public void run() {
                HashMap<String, Integer> last_received = new HashMap<>();

                String recibido;

                String[] partes;

                try {

                    String[] direccion = server_ip_port.split(":");

                    local_client_socket = new Socket(direccion[0], Integer.valueOf(direccion[1]));

                    //Le mandamos los bytes "mágicos"
                    local_client_socket.getOutputStream().write(Helpers.toByteArray(MAGIC_BYTES));

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            status.setText(Translator.translate("Intercambio de claves..."));

                        }
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

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            status.setText(Translator.translate("Chequeo de integridad..."));
                        }
                    });

                    String jar_hmac = Helpers.coronaHMACJ1(local_client_aes_key.getEncoded(), local_client_hmac_key.getEncoded());

                    //Le mandamos nuestro nick + VERSION + AVATAR + password al server
                    writeCommandToServer(Helpers.encryptCommand(Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + "#" + AboutDialog.VERSION + "@" + jar_hmac + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : "#*") + (password != null ? "#" + Base64.encodeBase64String(password.getBytes("UTF-8")) : "#*"), local_client_aes_key, local_client_hmac_key));

                    local_client_buffer_read_is = new BufferedReader(new InputStreamReader(local_client_socket.getInputStream()));

                    //Leemos la respuesta del server
                    recibido = readCommandFromServer();

                    partes = recibido.split("#");

                    if (partes[0].equals("BADVERSION")) {
                        exit = true;
                        Helpers.mostrarMensajeError(THIS, Translator.translate("Versión de CoronaPoker incorrecta") + "(" + partes[1] + ")");
                    } else if (partes[0].equals("YOUARELATE")) {
                        exit = true;
                        Helpers.mostrarMensajeError(THIS, "Llegas TARDE. La partida ya ha empezado.");

                    } else if (partes[0].equals("NOSPACE")) {
                        exit = true;
                        Helpers.mostrarMensajeError(THIS, "NO HAY SITIO");

                    } else if (partes[0].equals("NICKFAIL")) {
                        exit = true;
                        Helpers.mostrarMensajeError(THIS, "El nick elegido ya lo está usando otro usuario.");

                    } else if (partes[0].equals("BADPASSWORD")) {
                        exit = true;
                        Helpers.mostrarMensajeError(THIS, "PASSWORD INCORRECTA");
                    } else if (partes[0].equals("NICKOK")) {

                        String server_jar_hmac = Helpers.coronaHMACJ1(Base64.decodeBase64(jar_hmac), local_client_hmac_key.getEncoded());

                        if (!server_jar_hmac.equals(partes[2])) {

                            THIS.setUnsecure_server(true);

                            Helpers.threadRun(new Runnable() {
                                public void run() {

                                    Helpers.mostrarMensajeInformativo(THIS, "CUIDADO: el ejecutable del juego del servidor es diferente\n(Es posible que intente hacer trampas con una versión hackeada del juego)");
                                }
                            });
                        }

                        if ("0".equals(partes[1])) {
                            Helpers.GUIRun(new Runnable() {
                                public void run() {

                                    pass_icon.setVisible(false);
                                }
                            });
                        }

                        gameinfo_original = new String(Base64.decodeBase64(partes[3]), "UTF-8");

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                status.setText(Translator.translate("Recibiendo info del servidor..."));
                                game_info.setText(gameinfo_original);
                            }
                        });

                        //Leemos el nick del server
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
                            Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        //Leemos el avatar del server
                        String server_avatar_base64 = partes.length > 1 ? partes[1] : "";

                        File server_avatar = null;

                        try {

                            if (server_avatar_base64.length() > 0) {

                                int file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                if (file_id < 0) {
                                    file_id *= -1;
                                }

                                server_avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + server_nick + "_avatar" + String.valueOf(file_id));

                                FileOutputStream os = new FileOutputStream(server_avatar);

                                os.write(Base64.decodeBase64(server_avatar_base64));

                                os.close();
                            }

                        } catch (Exception ex) {
                            server_avatar = null;
                        }

                        //Leemos el contenido del chat
                        recibido = readCommandFromServer();

                        if (!"*".equals(recibido)) {

                            String chat_text = new String(Base64.decodeBase64(recibido), "UTF-8");

                            Helpers.GUIRun(new Runnable() {
                                public void run() {

                                    chat.setText(chat_text);
                                }
                            });
                        }

                        //Leemos el enlace del videochat (si existe)
                        recibido = readCommandFromServer();

                        if (!"*".equals(recibido)) {

                            String video_chat_link = new String(Base64.decodeBase64(recibido), "UTF-8");

                            if (video_chat_link.toLowerCase().startsWith("http")) {

                                setVideo_chat_link(video_chat_link);
                            }
                        }

                        //Añadimos al servidor
                        nuevoParticipante(server_nick, server_avatar, null, null, null, false);

                        //Nos añadimos nosotros
                        nuevoParticipante(local_nick, local_avatar, null, null, null, false);

                        //Cada X segundos mandamos un comando KEEP ALIVE al server 
                        Helpers.threadRun(new Runnable() {
                            public void run() {

                                while (!exit && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                    int ping = Helpers.CSPRNG_GENERATOR.nextInt();

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

                                    if (!exit && !WaitingRoomFrame.getInstance().isPartida_empezada() && ping + 1 != pong) {

                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL SERVIDOR NO RESPONDIÓ EL PING");

                                    }

                                }

                            }
                        });

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                status.setText(Translator.translate("CONECTADO"));

                            }
                        });

                        booting = false;

                        //Nos quedamos en bucle esperando mensajes del server
                        do {

                            try {

                                recibido = readCommandFromServer();

                                if (recibido != null) {

                                    String[] partes_comando = recibido.split("#");

                                    if (partes_comando[0].equals("PONG")) {

                                        pong = Integer.parseInt(partes_comando[1]);

                                    } else if (partes_comando[0].equals("PING")) {

                                        writeCommandToServer("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1));

                                    } else if (partes_comando[0].equals("CHAT")) {

                                        String mensaje;

                                        if (partes_comando.length == 3) {

                                            mensaje = new String(Base64.decodeBase64(partes_comando[2]), "UTF-8");

                                        } else {
                                            mensaje = "";
                                        }

                                        recibirMensajeChat(new String(Base64.decodeBase64(partes_comando[1]), "UTF-8"), mensaje);

                                    } else if (partes_comando[0].equals("EXIT")) {

                                        exit = true;

                                        Helpers.mostrarMensajeError(THIS, "El servidor ha cancelado la timba antes de empezar.");

                                    } else if (partes_comando[0].equals("KICKED")) {

                                        exit = true;

                                        Helpers.playWavResource("loser/payaso.wav");

                                        Helpers.mostrarMensajeInformativo(THIS, "¡A LA PUTA CALLE!");

                                    } else if (partes_comando[0].equals("GAME")) {

                                        //Confirmamos recepción al servidor
                                        String subcomando = partes_comando[2];

                                        int id = Integer.valueOf(partes_comando[1]);

                                        writeCommandToServer("CONF#" + String.valueOf(id + 1) + "#OK");

                                        if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != id) {

                                            last_received.put(subcomando, id);

                                            if (isPartida_empezada()) {

                                                switch (subcomando) {

                                                    case "PING":
                                                        break;

                                                    case "IWTSTH":

                                                        GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));

                                                        break;

                                                    case "IWTSTHSHOW":

                                                        GameFrame.getInstance().getCrupier().IWTSTH_SHOW(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"), Boolean.parseBoolean(partes_comando[4]));

                                                        break;

                                                    case "TIMEOUT":

                                                        Player jugador = GameFrame.getInstance().getCrupier().getNick2player().get(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));

                                                        if (jugador != null) {

                                                            jugador.setTimeout(true);
                                                        }

                                                        break;

                                                    case "TTS":
                                                        GameFrame.TTS_SERVER = partes_comando[3].equals("1");

                                                        Helpers.GUIRun(new Runnable() {
                                                            public void run() {

                                                                GameFrame.getInstance().getTts_menu().setEnabled(GameFrame.TTS_SERVER);

                                                                Helpers.TapetePopupMenu.SONIDOS_TTS_MENU.setEnabled(GameFrame.TTS_SERVER);

                                                                if (GameFrame.SONIDOS_TTS) {
                                                                    TTSNotifyDialog dialog = new TTSNotifyDialog(GameFrame.getInstance().getFrame(), false, GameFrame.TTS_SERVER);

                                                                    dialog.setLocation(dialog.getParent().getLocation());

                                                                    dialog.setVisible(true);
                                                                }

                                                            }
                                                        });

                                                        break;

                                                    case "VIDEOCHAT":
                                                        setVideo_chat_link(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        break;

                                                    case "PAUSE":
                                                        Helpers.threadRun(new Runnable() {
                                                            public void run() {
                                                                synchronized (GameFrame.getInstance().getLock_pause()) {
                                                                    if (("0".equals(partes_comando[3]) && GameFrame.getInstance().isTimba_pausada()) || ("1".equals(partes_comando[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                                        GameFrame.getInstance().pauseTimba(null);
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        break;

                                                    case "PERMUTATIONKEY":

                                                        Helpers.threadRun(new Runnable() {
                                                            public void run() {

                                                                String key = sqlReadPermutationkey(partes_comando[3]);

                                                                GameFrame.getInstance().getCrupier().sendGAMECommandToServer("PERMUTATIONKEY#" + (key != null ? key : "*"));
                                                            }
                                                        });
                                                        break;

                                                    case "CINEMATICEND":
                                                        GameFrame.getInstance().getCrupier().remoteCinematicEnd(null);
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

                                                    case "SERVEREXIT":
                                                        exit = true;

                                                        if (!GameFrame.CINEMATICAS) {
                                                            Helpers.mostrarMensajeInformativo(GameFrame.getInstance().getFrame(), "EL SERVIDOR HA TERMINADO LA TIMBA");
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
                                                        if (!ginfo.equals(game_info.getText())) {

                                                            Helpers.GUIRun(new Runnable() {
                                                                public void run() {

                                                                    game_info.setText(ginfo);

                                                                    if (!gameinfo_original.equals(game_info.getText())) {
                                                                        game_info.setOpaque(true);
                                                                        game_info.setBackground(Color.YELLOW);
                                                                    } else {
                                                                        game_info.setOpaque(false);
                                                                        game_info.setBackground(null);
                                                                    }
                                                                }
                                                            });

                                                            if (gameinfo_original.equals(ginfo)) {
                                                                Helpers.playWavResource("misc/last_hand_off.wav");
                                                            } else {
                                                                Helpers.playWavResource("misc/last_hand_on.wav");
                                                            }
                                                        }

                                                        break;
                                                    case "VIDEOCHAT":
                                                        setVideo_chat_link(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        break;

                                                    case "DELUSER":
                                                        borrarParticipante(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        break;

                                                    case "NEWUSER":
                                                        Helpers.playWavResource("misc/new_user.wav");

                                                        String nick = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");

                                                        File avatar = null;

                                                        int file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                                        if (file_id < 0) {
                                                            file_id *= -1;
                                                        }

                                                        if (partes_comando.length == 5) {
                                                            avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));

                                                            try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                os.write(Base64.decodeBase64(partes_comando[4]));
                                                            }
                                                        }

                                                        if (!participantes.containsKey(nick)) {
                                                            //Añadimos al participante
                                                            nuevoParticipante(nick, avatar, null, null, null, false);
                                                        }

                                                        break;
                                                    case "USERSLIST":
                                                        String[] current_users_parts = partes_comando[3].split("@");

                                                        for (String user : current_users_parts) {

                                                            String[] user_parts = user.split("\\|");

                                                            nick = new String(Base64.decodeBase64(user_parts[0]), "UTF-8");

                                                            avatar = null;

                                                            if (user_parts.length == 2) {
                                                                file_id = Helpers.CSPRNG_GENERATOR.nextInt();

                                                                if (file_id < 0) {
                                                                    file_id *= -1;
                                                                }

                                                                avatar = new File(System.getProperty("java.io.tmpdir") + "/corona_" + nick + "_avatar" + String.valueOf(file_id));

                                                                try (FileOutputStream os = new FileOutputStream(avatar)) {
                                                                    os.write(Base64.decodeBase64(user_parts[1]));
                                                                }

                                                            }

                                                            if (!participantes.containsKey(nick)) {
                                                                //Añadimos al participante
                                                                nuevoParticipante(nick, avatar, null, null, null, false);
                                                            }

                                                        }
                                                        break;

                                                    case "INIT":
                                                        setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");

                                                        Helpers.GUIRun(new Runnable() {
                                                            public void run() {
                                                                sound_icon.setVisible(false);
                                                                status.setText(Translator.translate("Inicializando timba..."));
                                                            }
                                                        });

                                                        GameFrame.BUYIN = Integer.parseInt(partes_comando[3]);

                                                        GameFrame.CIEGA_PEQUEÑA = Float.parseFloat(partes_comando[4]);

                                                        GameFrame.CIEGA_GRANDE = Float.parseFloat(partes_comando[5]);

                                                        String[] ciegas_double = partes_comando[6].split("@");

                                                        GameFrame.CIEGAS_DOUBLE = Integer.parseInt(ciegas_double[0]);

                                                        GameFrame.CIEGAS_DOUBLE_TYPE = Integer.parseInt(ciegas_double[1]);

                                                        GameFrame.RECOVER = Boolean.parseBoolean(partes_comando[7].split("@")[0]);

                                                        GameFrame.REBUY = Boolean.parseBoolean(partes_comando[8]);

                                                        GameFrame.MANOS = Integer.parseInt(partes_comando[9]);

                                                        //Inicializamos partida
                                                        Helpers.GUIRunAndWait(new Runnable() {
                                                            public void run() {
                                                                new GameFrame(THIS, local_nick, false);
                                                                chat_notifications.setVisible(true);

                                                            }
                                                        });

                                                        partida_empezada = true;

                                                        GameFrame.getInstance().AJUGAR();

                                                        break;
                                                }
                                            }
                                        }

                                    } else if (partes_comando[0].equals("CONF")) {
                                        //Es una confirmación del servidor

                                        WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{server_nick, Integer.parseInt(partes_comando[1])});
                                        synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                                            WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                        }

                                    }

                                } else {
                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL SOCKET RECIBIÓ NULL");
                                }

                            } catch (Exception ex) {

                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EXCEPCION AL LEER DEL SOCKET");

                                recibido = null;

                            } finally {
                                if (recibido == null && (!exit && (!isPartida_empezada() || !GameFrame.getInstance().getLocalPlayer().isExit())) && !reconectarCliente()) {
                                    exit = true;
                                }
                            }

                            if (!exit) {
                                Helpers.pausar(1000);
                            }

                        } while (!exit);

                    }

                } catch (IOException ex) {
                    //Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    Helpers.mostrarMensajeError(THIS, "ERROR INESPERADO");
                    System.exit(1);
                }

                if (WaitingRoomFrame.getInstance().isPartida_empezada()) {

                    GameFrame.getInstance().finTransmision(exit);

                } else if (!exit) {

                    if (local_client_socket == null) {

                        Helpers.mostrarMensajeError(THIS, "ALGO HA FALLADO. (Probablemente la timba no esté aún creada).");

                    } else {

                        Helpers.mostrarMensajeError(THIS, "ALGO HA FALLADO. Has perdido la conexión con el servidor.");
                    }
                }

                exit = true;

                synchronized (keep_alive_lock) {
                    keep_alive_lock.notifyAll();
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {

                        ventana_inicio.setVisible(true);

                        dispose();
                    }
                });

                Helpers.stopLoopMp3("misc/waiting_room.mp3");

                Helpers.unmuteLoopMp3("misc/background_music.mp3");
            }
        });
    }

    private void enviarListaUsuariosActualesAlNuevoUsuario(Participant par) {

        String command = "USERSLIST#";

        for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

            Participant p = entry.getValue();

            try {

                if (p != null && p != par) {

                    command += Base64.encodeBase64String(p.getNick().getBytes("UTF-8"));

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

        Helpers.threadRun(new Runnable() {
            public void run() {

                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, "Un cliente intenta conectar...");

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

                        String client_jar_hmac = Helpers.coronaHMACJ1(aes_key.getEncoded(), hmac_key.getEncoded());

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

                                if (MessageDigest.isEqual(orig_hmac, Base64.decodeBase64(partes[4]))) {

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "El HMAC del cliente es auténtico");

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Reseteando el socket del cliente...");

                                    //Es un usuario intentado reconectar
                                    if (participantes.get(client_nick).resetSocket(client_socket, aes_key, hmac_key)) {

                                        Helpers.playWavResource("misc/yahoo.wav");

                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE " + client_nick + " HA RECONECTADO CORRECTAMENTE.");
                                    } else {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE " + client_nick + " NO HA PODIDO RECONECTAR");

                                        try {
                                            client_socket.close();
                                        } catch (Exception ex) {
                                        }
                                    }

                                } else {
                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "EL CLIENTE " + client_nick + " NO HA PODIDO RECONECTAR (BAD HMAC)");

                                    try {
                                        client_socket.close();
                                    } catch (Exception ex) {
                                    }
                                }

                            } else {
                                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "El usuario " + client_nick + " INTENTA RECONECTAR UNA TIMBA ANTERIOR -> DENEGADO");

                                try {
                                    client_socket.close();
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

                                    FileOutputStream os = new FileOutputStream(client_avatar);

                                    os.write(Base64.decodeBase64(client_avatar_base64));

                                    os.close();
                                }
                            } catch (Exception ex) {
                                client_avatar = null;
                            }

                            String jar_hmac = Helpers.coronaHMACJ1(Base64.decodeBase64(client_jar_hmac), hmac_key.getEncoded());

                            writeCommandFromServer(Helpers.encryptCommand("NICKOK#" + (password == null ? "0" : "1") + "#" + jar_hmac + "#" + Base64.encodeBase64String(game_info.getText().getBytes("UTF-8")), aes_key, hmac_key), client_socket);

                            byte[] avatar_bytes = null;

                            if (local_avatar != null && local_avatar.length() > 0) {

                                try (FileInputStream is = new FileInputStream(local_avatar)) {
                                    avatar_bytes = is.readAllBytes();
                                }
                            }

                            //Mandamos nuestro nick + avatar
                            writeCommandFromServer(Helpers.encryptCommand(Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : ""), aes_key, hmac_key), client_socket);

                            //Mandamos el contenido del chat
                            writeCommandFromServer(Helpers.encryptCommand(chat.getText().isEmpty() ? "*" : Base64.encodeBase64String(chat.getText().getBytes("UTF-8")), aes_key, hmac_key), client_socket);

                            //Mandamos el link del videochat
                            writeCommandFromServer(Helpers.encryptCommand(getVideo_chat_link() != null ? Base64.encodeBase64String(getVideo_chat_link().getBytes("UTF-8")) : "*", aes_key, hmac_key), client_socket);

                            synchronized (lock_new_client) {

                                try {

                                    Helpers.GUIRunAndWait(new Runnable() {
                                        public void run() {
                                            empezar_timba.setEnabled(false);
                                            game_info.setEnabled(false);

                                        }
                                    });

                                    if (participantes.size() < MAX_PARTICIPANTES && !WaitingRoomFrame.getInstance().isPartida_empezando() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                                        //Añadimos al participante
                                        nuevoParticipante(client_nick, client_avatar, client_socket, aes_key, hmac_key, false);

                                        //Mandamos la lista de participantes actuales al nuevo participante
                                        if (participantes.size() > 2) {
                                            enviarListaUsuariosActualesAlNuevoUsuario(participantes.get(client_nick));

                                            //Mandamos el nuevo participante al resto de participantes
                                            String comando = "NEWUSER#" + Base64.encodeBase64String(client_nick.getBytes("UTF-8"));

                                            if (client_avatar != null) {

                                                byte[] avatar_b;

                                                try (FileInputStream is = new FileInputStream(client_avatar)) {
                                                    avatar_b = is.readAllBytes();
                                                }

                                                comando += "#" + Base64.encodeBase64String(avatar_b);
                                            }

                                            broadcastASYNCGAMECommandFromServer(comando, participantes.get(client_nick));
                                        }

                                        Helpers.GUIRun(new Runnable() {
                                            public void run() {
                                                kick_user.setEnabled(true);
                                                new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);
                                            }
                                        });

                                        Helpers.playWavResource("misc/new_user.wav");

                                        if (!partes[1].split("@")[1].equals(client_jar_hmac)) {

                                            participantes.get(client_nick).setUnsecure_player(true);

                                            Helpers.threadRun(new Runnable() {
                                                public void run() {

                                                    Helpers.mostrarMensajeInformativo(THIS, client_nick + " " + Translator.translate("CUIDADO: el ejecutable del juego de este usuario es diferente\n(Es posible que intente hacer trampas con una versión hackeada del juego)"));
                                                }
                                            });

                                        }

                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, client_nick + " CONECTADO");
                                    } else {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.INFO, client_nick + " NO PUDO CONECTAR CORRECTAMENTE (PARTIDA LLENA O EMPEZADA)");
                                        client_socket.close();
                                    }

                                } catch (Exception ex) {
                                } finally {
                                    Helpers.GUIRunAndWait(new Runnable() {
                                        public void run() {
                                            if (participantes.size() > 1) {
                                                empezar_timba.setEnabled(true);
                                            }

                                            game_info.setEnabled(true);
                                        }
                                    });
                                }

                            }

                        }

                    } else {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, "BAD MAGIC BYTES FROM CLIENT!");
                        client_socket.close();
                    }
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        });

    }

    private void servidor() {

        server_nick = local_nick;

        Helpers.threadRun(new Runnable() {
            public void run() {

                while (!exit) {

                    booting = true;

                    try {
                        String[] direccion = server_ip_port.trim().split(":");

                        server_port = Integer.valueOf(direccion[1]);

                        if (upnp) {

                            String stat = status1.getText();

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    status1.setText(Translator.translate("Probando UPnP..."));
                                }
                            });

                            upnp = Helpers.UPnPOpen(server_port);

                            if (upnp) {

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        status1.setForeground(Color.BLUE);
                                        status1.setText(Helpers.getMyPublicIP() + ":" + String.valueOf(server_port) + " (UPnP OK)");
                                    }
                                });

                            } else {
                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        status1.setText(stat + " (UPnP ERROR)");
                                    }
                                });

                                Helpers.mostrarMensajeError(THIS, "NO HA SIDO POSIBLE MAPEAR AUTOMÁTICAMENTE EL PUERTO USANDO UPnP\n\n(Si quieres compartir la timba por Internet deberás activar UPnP en tu router o mapear el puerto de forma manual)");
                            }
                        }

                        Helpers.PROPERTIES.setProperty("upnp", String.valueOf(upnp));

                        Helpers.savePropertiesFile();

                        booting = false;

                        server_socket = new ServerSocket(server_port);

                        while (!server_socket.isClosed()) {

                            serverSocketHandler(server_socket.accept());

                        }

                    } catch (IOException ex) {

                        if (server_socket == null) {

                            exit = true;

                            Helpers.mostrarMensajeError(THIS, "ALGO HA FALLADO. (Probablemente ya hay una timba creada en el mismo puerto).");
                        }

                    } catch (Exception ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                if (upnp) {
                    Helpers.UPnPClose(server_port);
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    public void run() {

                        ventana_inicio.setVisible(true);

                        dispose();
                    }
                });

                Helpers.stopLoopMp3("misc/waiting_room.mp3");

                Helpers.unmuteLoopMp3("misc/background_music.mp3");

            }
        });
    }

    public void recibirMensajeChat(String nick, String msg) {

        Helpers.GUIRun(new Runnable() {
            public void run() {

                chat.append("[" + nick + "]: " + msg + "\n");

                if (!chat.isFocusOwner()) {
                    chat.setCaretPosition(chat.getText().length());
                }

                if (WaitingRoomFrame.getInstance().isPartida_empezada() && !isActive() && isCHAT_GAME_NOTIFICATIONS()) {

                    Helpers.TTS_CHAT_QUEUE.add(new Object[]{nick, msg});

                    synchronized (Helpers.TTS_CHAT_QUEUE) {
                        Helpers.TTS_CHAT_QUEUE.notifyAll();
                    }

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

        Helpers.threadRun(new Runnable() {
            public void run() {

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

            }
        });
    }

    public synchronized void borrarParticipante(String nick) {

        if (this.participantes.containsKey(nick)) {

            Helpers.playWavResource("misc/toilet.wav", true);

            participantes.remove(nick);

            Helpers.GUIRun(new Runnable() {
                public void run() {

                    tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

                    DefaultListModel model = (DefaultListModel) conectados.getModel();

                    ParticipantsListRenderer rem_element = null;

                    for (int i = 0; i < model.getSize(); i++) {

                        if (((ParticipantsListRenderer) model.getElementAt(i)).getText().equals(nick)) {
                            rem_element = (ParticipantsListRenderer) model.getElementAt(i);
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
                }
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

    private synchronized void nuevoParticipante(String nick, File avatar, Socket socket, SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu) {

        Participant participante = new Participant(this, nick, avatar, socket, aes_k, hmac_k, cpu);

        participantes.put(nick, participante);

        if (socket != null) {

            Helpers.threadRun(participante);

        }

        Helpers.GUIRun(new Runnable() {
            public void run() {

                tot_conectados.setText(participantes.size() + "/" + WaitingRoomFrame.MAX_PARTICIPANTES);

                ParticipantsListRenderer label = new ParticipantsListRenderer();

                label.setText(nick);

                label.setPreferredSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH));

                if (avatar != null) {
                    label.setIcon(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                } else {
                    label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource((server && cpu) ? "/images/avatar_bot.png" : "/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_WIDTH, Image.SCALE_SMOOTH)));
                }

                ((DefaultListModel) conectados.getModel()).addElement(label);

                conectados.revalidate();

                conectados.repaint();

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

        jScrollPane1 = new javax.swing.JScrollPane();
        chat = new javax.swing.JTextArea();
        chat_box = new javax.swing.JTextField();
        avatar_label = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        pass_icon = new javax.swing.JLabel();
        new_bot_button = new javax.swing.JButton();
        logo = new javax.swing.JLabel();
        status = new javax.swing.JLabel();
        status1 = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        video_chat_button = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        panel_conectados = new javax.swing.JScrollPane();
        conectados = new javax.swing.JList<>();
        kick_user = new javax.swing.JButton();
        empezar_timba = new javax.swing.JButton();
        tot_conectados = new javax.swing.JLabel();
        game_info = new javax.swing.JLabel();
        danger_server = new javax.swing.JLabel();
        tts_warning = new javax.swing.JLabel();
        chat_notifications = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("CoronaPoker - Sala de espera");
        setIconImage(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage());
        setMinimumSize(new java.awt.Dimension(548, 701));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                formComponentShown(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jScrollPane1.setDoubleBuffered(true);

        chat.setEditable(false);
        chat.setColumns(20);
        chat.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        chat.setLineWrap(true);
        chat.setRows(5);
        chat.setDoubleBuffered(true);
        jScrollPane1.setViewportView(chat);

        chat_box.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        chat_box.setDoubleBuffered(true);
        chat_box.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_boxActionPerformed(evt);
            }
        });

        avatar_label.setFont(new java.awt.Font("Dialog", 1, 16)); // NOI18N
        avatar_label.setText("Toni");
        avatar_label.setDoubleBuffered(true);

        pass_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lock.png"))); // NOI18N
        pass_icon.setToolTipText("Click para gestionar contraseña");
        pass_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        pass_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pass_iconMouseClicked(evt);
            }
        });

        new_bot_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        new_bot_button.setText("AÑADIR BOT");
        new_bot_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        new_bot_button.setDoubleBuffered(true);
        new_bot_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                new_bot_buttonActionPerformed(evt);
            }
        });

        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        logo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        logo.setDoubleBuffered(true);
        logo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                logoMouseClicked(evt);
            }
        });

        status.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        status.setForeground(new java.awt.Color(51, 153, 0));
        status.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status.setText("Estado");
        status.setDoubleBuffered(true);

        status1.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        status1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status1.setText("1.1.1.1");
        status1.setToolTipText("Click para obtener datos de conexión");
        status1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        status1.setDoubleBuffered(true);
        status1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                status1MouseClicked(evt);
            }
        });

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setToolTipText("Click para activar/desactivar el sonido");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.setDoubleBuffered(true);
        sound_icon.setPreferredSize(new java.awt.Dimension(30, 30));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        video_chat_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
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

        jPanel2.setFocusable(false);
        jPanel2.setOpaque(false);

        panel_conectados.setDoubleBuffered(true);
        panel_conectados.setFocusable(false);
        panel_conectados.setOpaque(false);

        conectados.setFont(new java.awt.Font("Dialog", 0, 16)); // NOI18N
        conectados.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        conectados.setToolTipText("Participantes conectados");
        conectados.setCellRenderer(new com.tonikelope.coronapoker.ParticipantsListRenderer());
        conectados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        conectados.setDoubleBuffered(true);
        conectados.setFocusable(false);
        conectados.setOpaque(false);
        panel_conectados.setViewportView(conectados);

        kick_user.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        kick_user.setText("Expulsar jugador");
        kick_user.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        kick_user.setDoubleBuffered(true);
        kick_user.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kick_userActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(kick_user, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_conectados)))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(panel_conectados, javax.swing.GroupLayout.PREFERRED_SIZE, 381, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(kick_user))
        );

        empezar_timba.setBackground(new java.awt.Color(0, 130, 0));
        empezar_timba.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        empezar_timba.setForeground(new java.awt.Color(255, 255, 255));
        empezar_timba.setText("EMPEZAR YA");
        empezar_timba.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        empezar_timba.setDoubleBuffered(true);
        empezar_timba.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                empezar_timbaActionPerformed(evt);
            }
        });

        tot_conectados.setFont(new java.awt.Font("Dialog", 1, 22)); // NOI18N
        tot_conectados.setForeground(new java.awt.Color(0, 102, 255));
        tot_conectados.setText("0/10");

        game_info.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        game_info.setText(" ");
        game_info.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        game_info.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                game_infoMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(pass_icon)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(status1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(video_chat_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(new_bot_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(game_info, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tot_conectados, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(logo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(empezar_timba, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(logo)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tot_conectados)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(game_info)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(new_bot_button)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(video_chat_button)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(status1)
                            .addComponent(pass_icon))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(sound_icon, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(status, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(empezar_timba, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        danger_server.setBackground(new java.awt.Color(255, 0, 0));
        danger_server.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        danger_server.setForeground(new java.awt.Color(255, 255, 255));
        danger_server.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        danger_server.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/danger.png"))); // NOI18N
        danger_server.setText("POSIBLE SERVIDOR TRAMPOSO");
        danger_server.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        danger_server.setOpaque(true);

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

        chat_notifications.setText("Notificaciones durante el juego");
        chat_notifications.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chat_notifications.setDoubleBuffered(true);
        chat_notifications.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chat_notificationsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(danger_server, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tts_warning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(avatar_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chat_box))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(chat_notifications, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(danger_server)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(chat_notifications)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 145, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(avatar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(chat_box))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tts_warning)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void chat_boxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_boxActionPerformed
        // TODO add your handling code here:

        String mensaje = chat_box.getText().trim();

        if (chat_enabled && mensaje.length() > 0) {

            chat.append("[" + local_nick + "]: " + mensaje + "\n");

            if (!chat.isFocusOwner()) {
                chat.setCaretPosition(chat.getText().length());
            }

            this.enviarMensajeChat(local_nick, mensaje);

            this.chat_box.setText("");

            chat_enabled = false;

            Helpers.threadRun(new Runnable() {
                public void run() {

                    Helpers.pausar(1000);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            chat_enabled = true;

                        }
                    });

                }
            });
        }
    }//GEN-LAST:event_chat_boxActionPerformed

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

                Helpers.threadRun(new Runnable() {
                    public void run() {
                        try {

                            participantes.get(expulsado).setExit(true);

                            if (!participantes.get(expulsado).isCpu()) {

                                String comando = "KICKED#" + Base64.encodeBase64String(expulsado.getBytes("UTF-8"));
                                participantes.get(expulsado).writeCommandFromServer(Helpers.encryptCommand(comando, participantes.get(expulsado).getAes_key(), participantes.get(expulsado).getHmac_key()), false);
                            }

                            participantes.get(expulsado).exitAndCloseSocket();

                            borrarParticipante(expulsado);

                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {
                                kick_user.setEnabled(participantes.size() > 1);

                                if (password != null) {
                                    pass_icon.setToolTipText(password);

                                }
                            }
                        });

                        if (password != null && !participantes.get(expulsado).isCpu()) {
                            Helpers.copyTextToClipboard(password);
                            Helpers.mostrarMensajeInformativo(THIS, "NUEVA PASSWORD COPIADA EN EL PORTAPAPELES");
                        }
                    }
                });
            }
        } else {
            Helpers.mostrarMensajeError(THIS, "Tienes que seleccionar algún participante antes");
        }

    }//GEN-LAST:event_kick_userActionPerformed

    private void empezar_timbaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_empezar_timbaActionPerformed
        // TODO add your handling code here:

        if (Helpers.mostrarMensajeInformativoSINO(THIS, "¿SEGURO QUE QUIERES EMPEZAR YA?") == 0 && participantes.size() >= 2 && !WaitingRoomFrame.getInstance().isPartida_empezada() && !WaitingRoomFrame.getInstance().isPartida_empezando()) {

            String missing_players = "";

            if (GameFrame.RECOVER) {

                int game_id = GameFrame.RECOVER_ID;

                try {

                    String sql = "SELECT preflop_players as PLAYERS FROM hand WHERE hand.id_game=? AND hand.id=(SELECT max(hand.id) from hand where hand.id_game=?)";

                    PreparedStatement statement = Helpers.getSQLITE().prepareStatement(sql);

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

                    statement.close();

                } catch (SQLException | UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            boolean vamos = ("".equals(missing_players) || Helpers.mostrarMensajeInformativoSINO(this, missing_players + Translator.translate("Hay jugadores de la timba anterior que no se han vuelto a conectar.\n(Si no se conectan no se podrá recuperar la última mano en curso).\n\n¿EMPEZAMOS YA?")) == 0);

            if (vamos) {

                partida_empezando = true;

                setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");
                this.empezar_timba.setEnabled(false);
                this.empezar_timba.setVisible(false);
                this.new_bot_button.setEnabled(false);
                this.new_bot_button.setVisible(false);
                this.kick_user.setEnabled(false);
                this.kick_user.setVisible(false);
                this.sound_icon.setVisible(false);
                this.status.setText(Translator.translate("Inicializando timba..."));
                pack();

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        synchronized (lock_new_client) {

                            boolean ocupados;

                            do {

                                ocupados = false;

                                for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

                                    Participant p = entry.getValue();

                                    if (p != null && !p.isCpu() && !p.getAsync_command_queue().isEmpty()) {

                                        ocupados = true;

                                        break;

                                    }

                                }

                                if (ocupados) {

                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Hay algun participante con comandos sin confirmar. NO podemos empezar aún...");
                                    Helpers.pausar(1000);
                                }

                            } while (ocupados);

                            //Inicializamos partida
                            Helpers.GUIRunAndWait(new Runnable() {
                                public void run() {
                                    new GameFrame(THIS, local_nick, true);
                                    chat_notifications.setVisible(true);

                                }
                            });

                            partida_empezada = true;

                            GameFrame.getInstance().AJUGAR();
                        }
                    }
                });
            }
        }

    }//GEN-LAST:event_empezar_timbaActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:

        if (!booting) {

            if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {

                if (exit || reconnecting) {

                    if (Helpers.mostrarMensajeInformativoSINO(THIS, "¿FORZAR CIERRE?") == 0) {
                        System.exit(1);
                    }

                } else if (Helpers.mostrarMensajeInformativoSINO(THIS, "¿SEGURO QUE QUIERES SALIR AHORA?") == 0) {

                    exit = true;

                    Helpers.threadRun(new Runnable() {
                        public void run() {

                            if (isServer()) {

                                participantes.entrySet().forEach((entry) -> {

                                    Participant p = entry.getValue();

                                    if (p != null) {

                                        p.exitAndCloseSocket();
                                    }

                                });

                                if (getServer_socket() != null) {
                                    try {
                                        getServer_socket().close();
                                    } catch (Exception ex) {
                                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }

                            } else if (local_client_socket != null && !reconnecting) {

                                try {
                                    writeCommandToServer(Helpers.encryptCommand("EXIT", getLocal_client_aes_key(), getLocal_client_hmac_key()));
                                    local_client_socket.close();
                                } catch (Exception ex) {
                                    Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    });
                }

            } else {
                setVisible(false);
            }

        } else if (Helpers.mostrarMensajeInformativoSINO(THIS, "¿FORZAR CIERRE?") == 0) {
            System.exit(1);
        }
    }//GEN-LAST:event_formWindowClosing

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        GameFrame.SONIDOS = !GameFrame.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", GameFrame.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));

        if (!GameFrame.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.MUTED_ALL = false;

            Helpers.unmuteLoopMp3("misc/waiting_room.mp3");

            Helpers.unmuteAllWav();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:

        conectados.revalidate();

        conectados.repaint();

        chat.revalidate();

        chat.repaint();

        sound_icon.setIcon(new ImageIcon(new ImageIcon(getClass().getResource(GameFrame.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")).getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));

        chat_box.requestFocus();
    }//GEN-LAST:event_formComponentShown

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoMouseClicked
        // TODO add your handling code here:
        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_new_bot_buttonActionPerformed

        if (participantes.size() < MAX_PARTICIPANTES) {

            new_bot_button.setEnabled(false);

            Helpers.playWavResource("misc/laser.wav");

            Helpers.threadRun(new Runnable() {
                public void run() {
                    try {
                        // TODO add your handling code here:
                        String bot_nick;

                        int conta_bot = 0;

                        do {
                            conta_bot++;

                            bot_nick = "CoronaBot#" + String.valueOf(conta_bot);

                        } while (participantes.get(bot_nick) != null);

                        //Mandamos el nuevo participante al resto de participantes
                        String comando = "NEWUSER#" + Base64.encodeBase64String(bot_nick.getBytes("UTF-8"));

                        byte[] avatar_b = null;

                        try (InputStream is = WaitingRoomFrame.class.getResourceAsStream("/images/avatar_bot.png")) {
                            avatar_b = is.readAllBytes();
                        } catch (IOException ex) {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        comando += "#" + Base64.encodeBase64String(avatar_b);

                        synchronized (lock_new_client) {

                            nuevoParticipante(bot_nick, null, null, null, null, true);

                            broadcastASYNCGAMECommandFromServer(comando, participantes.get(bot_nick));

                            Helpers.GUIRun(new Runnable() {
                                public void run() {
                                    empezar_timba.setEnabled(true);
                                    kick_user.setEnabled(true);

                                    new_bot_button.setEnabled(participantes.size() < WaitingRoomFrame.MAX_PARTICIPANTES);

                                }
                            });
                        }

                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
    }//GEN-LAST:event_new_bot_buttonActionPerformed

    private void video_chat_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_video_chat_buttonActionPerformed
        // TODO add your handling code here:

        QRChatDialog chat_dialog = new QRChatDialog(this, true, this.getVideo_chat_link(), server);

        chat_dialog.setLocationRelativeTo(this);

        chat_dialog.setVisible(true);

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
        }
    }//GEN-LAST:event_video_chat_buttonActionPerformed

    private void pass_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pass_iconMouseClicked
        // TODO add your handling code here:

        if (server && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
            if (Helpers.mostrarMensajeInformativoSINO(this, Translator.translate("¿GENERAR CONTRASEÑA NUEVA?")) == 0) {
                password = Helpers.genRandomString(GEN_PASS_LENGTH);
                pass_icon.setToolTipText(password);
            }

            if (password != null) {
                pass_icon.setEnabled(true);
                Helpers.copyTextToClipboard(password);
                Helpers.mostrarMensajeInformativo(this, Translator.translate("PASSWORD COPIADA EN EL PORTAPAPELES"));
            }
        }
    }//GEN-LAST:event_pass_iconMouseClicked

    private void tts_warningMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tts_warningMouseClicked
        // TODO add your handling code here:
        Helpers.mostrarMensajeInformativo(this,
                "Aunque CoronaPoker usa cifrado extremo a extremo en todas las comunicaciones, el chat de\nvoz utiliza APIs externas TTS para convertir el texto en audio, por lo que los mensajes\nenviados a esos servidores podrían ser (en teoría) leidos por terceros.\n\nPOR FAVOR, TENLO EN CUENTA A LA HORA DE USAR EL CHAT");
    }//GEN-LAST:event_tts_warningMouseClicked

    private void status1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_status1MouseClicked

        if (server) {
            try {
                // TODO add your handling code here:

                Helpers.copyTextToClipboard("[CoronaPoker] INTERNET -> " + Helpers.getMyPublicIP() + ":" + String.valueOf(server_socket.getLocalPort()) + "\n\nRED LOCAL -> " + InetAddress.getLocalHost().getHostAddress() + ":" + String.valueOf(server_socket.getLocalPort()));
                Helpers.mostrarMensajeInformativo(this, Translator.translate("DATOS DE CONEXIÓN COPIADOS EN EL PORTAPAPELES"));
            } catch (UnknownHostException ex) {
                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_status1MouseClicked

    private void game_infoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_game_infoMouseClicked
        // TODO add your handling code here:

        if (server && !GameFrame.isRECOVER() && !isPartida_empezada() && !isPartida_empezando() && game_info.isEnabled()) {

            game_info.setEnabled(false);

            NewGameDialog dialog = new NewGameDialog(this, true);

            dialog.setLocationRelativeTo(dialog.getParent());

            dialog.setVisible(true);

            if (dialog.isDialog_ok()) {

                game_info.setText(GameFrame.BUYIN + " " + (!GameFrame.REBUY ? "NO-REBUY | " : "| ") + Helpers.float2String(GameFrame.CIEGA_PEQUEÑA) + " / " + Helpers.float2String(GameFrame.CIEGA_GRANDE) + (GameFrame.CIEGAS_DOUBLE > 0 ? " @ " + String.valueOf(GameFrame.CIEGAS_DOUBLE) + (GameFrame.CIEGAS_DOUBLE_TYPE <= 1 ? "'" : "*") : "") + (GameFrame.MANOS != -1 ? " | " + String.valueOf(GameFrame.MANOS) : ""));

                if (!gameinfo_original.equals(game_info.getText())) {
                    game_info.setOpaque(true);
                    game_info.setBackground(Color.YELLOW);
                } else {
                    game_info.setOpaque(false);
                    game_info.setBackground(null);
                }

                Helpers.threadRun(new Runnable() {
                    public void run() {

                        try {
                            broadcastASYNCGAMECommandFromServer("GAMEINFO#" + Base64.encodeBase64String(game_info.getText().getBytes("UTF-8")), null);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        Helpers.GUIRun(new Runnable() {
                            public void run() {

                                game_info.setEnabled(true);
                            }
                        });
                    }
                });
            } else {
                game_info.setEnabled(true);
            }

        }

    }//GEN-LAST:event_game_infoMouseClicked

    private void chat_notificationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chat_notificationsActionPerformed
        // TODO add your handling code here:

        CHAT_GAME_NOTIFICATIONS = chat_notifications.isSelected();

        Helpers.PROPERTIES.setProperty("chat_game_notifications", String.valueOf(CHAT_GAME_NOTIFICATIONS));

        Helpers.savePropertiesFile();
    }//GEN-LAST:event_chat_notificationsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JTextArea chat;
    private javax.swing.JTextField chat_box;
    private javax.swing.JCheckBox chat_notifications;
    private javax.swing.JList<String> conectados;
    private javax.swing.JLabel danger_server;
    private javax.swing.JButton empezar_timba;
    private javax.swing.JLabel game_info;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton kick_user;
    private javax.swing.JLabel logo;
    private javax.swing.JButton new_bot_button;
    private javax.swing.JScrollPane panel_conectados;
    private javax.swing.JLabel pass_icon;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel status;
    private javax.swing.JLabel status1;
    private javax.swing.JLabel tot_conectados;
    private javax.swing.JLabel tts_warning;
    private javax.swing.JButton video_chat_button;
    // End of variables declaration//GEN-END:variables
}
