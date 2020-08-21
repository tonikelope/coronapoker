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

import static com.tonikelope.coronapoker.Game.WAIT_QUEUES;
import java.awt.Dimension;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class WaitingRoom extends javax.swing.JFrame {

    public static final int MAX_PARTICIPANTES = 10;
    public static final String MAGIC_BYTES = "5c1f158dd9855cc9";
    public static final int PING_PONG_TIMEOUT = 15000;
    public static final int MAX_PING_PONG_ERROR = 3;
    private static boolean partida_empezada = false;
    private static boolean exit = false;
    private static WaitingRoom THIS;

    private final boolean server;
    private final String local_nick;
    private final Map<String, Participant> participantes;
    private volatile ServerSocket server_socket;
    private volatile Socket client_socket;
    private volatile String server_ip_port;
    private final Init ventana_inicio;
    private final File local_avatar;

    private volatile String server_nick;
    private volatile Integer client_id;
    private final Object socket_reconnect_lock = new Object();
    private final Object keep_alive_lock = new Object();
    private volatile BufferedReader client_inputstream = null;
    private volatile Reconnect2ServerDialog reconnect_dialog = null;
    private volatile boolean reconnecting = false;
    private final Object lock_reconnect = new Object();
    private volatile int pong;
    private final ConcurrentLinkedQueue<Object[]> received_confirmations = new ConcurrentLinkedQueue<>();

    public ConcurrentLinkedQueue<Object[]> getReceived_confirmations() {
        return received_confirmations;
    }

    public static boolean isExit() {
        return exit;
    }

    public JTextArea getChat() {
        return chat;
    }

    public static WaitingRoom getInstance() {
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

    public Reconnect2ServerDialog getReconnect_dialog() {
        return reconnect_dialog;
    }

    public File getAvatar() {
        return local_avatar;
    }

    public Socket getClient_socket() {
        synchronized (getSocket_reconnect_lock()) {
            return client_socket;
        }
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

    public Object getSocket_reconnect_lock() {
        return socket_reconnect_lock;
    }

    /**
     * Creates new form SalaEspera
     */
    public WaitingRoom(Init ventana_inicio, boolean local, String nick, String servidor_ip_port, File avatar) {
        initComponents();
        setTitle(Init.WINDOW_TITLE + Translator.translate(" - Sala de espera (") + nick + ")");

        THIS = this;

        exit = false;

        partida_empezada = false;

        sound_icon.setIcon(new ImageIcon(getClass().getResource(Game.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")));

        this.empezar_timba.setVisible(false);
        this.new_bot_button.setVisible(false);
        this.ventana_inicio = ventana_inicio;
        this.server = local;
        this.local_avatar = avatar;
        this.server_ip_port = servidor_ip_port;
        this.client_socket = null;
        this.server_socket = null;
        this.local_nick = nick;

        participantes = Collections.synchronizedMap(new LinkedHashMap<>());

        Helpers.JTextFieldRegularPopupMenu.addTo(this.chat);
        Helpers.JTextFieldRegularPopupMenu.addTo(this.mensaje);

        if (avatar != null) {
            avatar_label.setSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT));
            avatar_label.setIcon(new ImageIcon(new ImageIcon(avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
        }

        avatar_label.setText(local_nick);

        this.status1.setText(this.server_ip_port);

        if (this.server) {

            this.new_bot_button.setVisible(true);

            this.status.setText("Esperando jugadores...");

            participantes.put(local_nick, null);

            DefaultListModel listModel = new DefaultListModel();

            ParticipantsListRenderer label = new ParticipantsListRenderer();

            label.setText(local_nick);

            label.setSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT));

            if (local_avatar != null) {
                label.setIcon(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
            } else {
                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
            }

            listModel.addElement(label);

            conectados.setModel(listModel);

            this.servidor();

        } else {
            this.status.setText("Conectando...");
            this.empezar_timba.setVisible(false);
            this.kick_user.setVisible(false);
            this.cliente();
        }

        Helpers.updateFonts(this, Helpers.GUI_FONT, null);

        Helpers.translateComponents(this, false);

        pack();
    }

    //Función AUTO-RECONNECT
    public boolean reconectarCliente() {

        WaitingRoom tthis = this;

        this.reconnecting = true;

        boolean ok;

        synchronized (getSocket_reconnect_lock()) {

            if (!client_socket.isClosed()) {
                try {
                    client_socket.close();

                } catch (Exception ex) {
                }
            }

            client_socket = null;

            long start = System.currentTimeMillis();

            ok = false;

            do {

                try {

                    String[] server_address = server_ip_port.split(":");

                    client_socket = new Socket(server_address[0], Integer.valueOf(server_address[1]));

                    client_inputstream = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));

                    //Le mandamos los bytes "mágicos"
                    byte[] magic = Helpers.toByteArray(MAGIC_BYTES);

                    client_socket.getOutputStream().write(magic);

                    //Le mandamos nuestro nick al server y el código secreto de reconexión
                    client_socket.getOutputStream().write((Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + "#" + AboutDialog.VERSION + "#*#" + String.valueOf(client_id) + "\n").getBytes("UTF-8"));

                    //Leemos el contenido del chat
                    String recibido = client_inputstream.readLine().trim();

                    String chat_text = new String(Base64.decodeBase64(recibido), "UTF-8");

                    Helpers.GUIRun(new Runnable() {
                        public void run() {

                            chat.setText(chat_text);
                        }
                    });

                    ok = true;

                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);

                    if (client_socket != null && !client_socket.isClosed()) {

                        try {
                            client_socket.close();

                        } catch (Exception ex2) {
                        }

                        client_socket = null;
                    }
                }

                if (!ok) {

                    if (System.currentTimeMillis() - start > Game.CLIENT_RECON_TIMEOUT && partida_empezada) {

                        if (this.reconnect_dialog == null) {
                            this.reconnect_dialog = new Reconnect2ServerDialog(Game.getInstance() != null ? Game.getInstance() : tthis, true, server_ip_port);

                            Helpers.GUIRun(new Runnable() {
                                public void run() {

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

                        while (!reconnect_dialog.isReconectar()) {
                            synchronized (this.lock_reconnect) {
                                try {
                                    this.lock_reconnect.wait(1000);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }

                        start = System.currentTimeMillis();
                        server_ip_port = reconnect_dialog.getIp_port().getText().trim();

                    } else {

                        Helpers.pausar(Game.CLIENT_RECON_ERROR_PAUSE);
                    }
                }

            } while (!ok && (!partida_empezada || !Game.getInstance().getLocalPlayer().isExit()));

            if (this.reconnect_dialog != null) {

                Helpers.GUIRun(new Runnable() {
                    @Override
                    public void run() {
                        reconnect_dialog.dispose();
                        reconnect_dialog = null;
                    }
                });
            }

            if (ok) {
                Helpers.playWavResource("misc/yahoo.wav");
            }

            getSocket_reconnect_lock().notifyAll();
        }

        this.reconnecting = false;

        return ok;

    }

    public void broadcastCommandFromServer(String command, String skip_nick, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();

        for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

            Participant p = entry.getValue();

            if (p != null && !p.isCpu() && !p.getNick().equals(skip_nick) && !p.isExit()) {

                pendientes.add(p.getNick());

            }

        }

        if (!pendientes.isEmpty()) {

            int id = Helpers.PRNG_GENERATOR.nextInt();

            int conta_timeout = 0;

            do {

                String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && pendientes.contains(p.getNick())) {

                        try {

                            p.getSocket().getOutputStream().write((full_command + "\n").getBytes("UTF-8"));
                        } catch (IOException ex) {
                        }

                    }
                }

                if (confirmation) {
                    //Esperamos confirmaciones y en caso de que alguna no llegue pasado un tiempo volvermos a enviar todos los que fallaron la confirmación la primera vez
                    if (this.waitConfirmations(id, pendientes)) {
                        conta_timeout++;
                    }

                }

            } while (confirmation && !pendientes.isEmpty() && conta_timeout < Game.MAX_TIMEOUT_CONFIRMATION_ERROR);

            if (!pendientes.isEmpty()) {

                for (String n : pendientes) {
                    participantes.get(n).setExit();
                    borrarParticipante(n);
                }
            }
        }
    }

    public void sendCommand(String command, String nick, Socket socket, boolean confirmation) {

        ArrayList<String> pendientes = new ArrayList<>();

        pendientes.add(nick);

        int id = Helpers.PRNG_GENERATOR.nextInt();

        int conta_timeout = 0;

        do {

            String full_command = "GAME#" + String.valueOf(id) + "#" + command;

            try {

                socket.getOutputStream().write((full_command + "\n").getBytes("UTF-8"));

            } catch (IOException ex) {
            }

            if (confirmation) {
                //Esperamos confirmaciones y en caso de que alguna no llegue pasado un tiempo volvermos a enviar todos los que fallaron la confirmación la primera vez
                if (this.waitConfirmations(id, pendientes)) {
                    conta_timeout++;
                }
            }

        } while (confirmation && !pendientes.isEmpty() && conta_timeout < Game.MAX_TIMEOUT_CONFIRMATION_ERROR);

        if (!pendientes.isEmpty()) {

            for (String n : pendientes) {
                if (participantes.get(n) != null) {
                    {
                        participantes.get(n).setExit();

                        borrarParticipante(n);
                    }
                }
            }
        }

    }

    private boolean waitConfirmations(int id, ArrayList<String> pending) {

        //Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        while (!pending.isEmpty() && !timeout) {

            synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {

                ArrayList<Object[]> rejected = new ArrayList<>();

                Object[] confirmation;

                while (!WaitingRoom.getInstance().getReceived_confirmations().isEmpty()) {

                    confirmation = WaitingRoom.getInstance().getReceived_confirmations().poll();

                    if ((int) confirmation[1] == id + 1) {

                        pending.remove(confirmation[0]);

                    } else {
                        rejected.add(confirmation);
                    }
                }

                if (System.currentTimeMillis() - start_time > Game.CONFIRMATION_TIMEOUT) {
                    timeout = true;
                } else if (!pending.isEmpty()) {

                    if (!rejected.isEmpty()) {
                        WaitingRoom.getInstance().getReceived_confirmations().addAll(rejected);
                        rejected.clear();
                    }

                    try {
                        WaitingRoom.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }

            }
        }

        return !pending.isEmpty();
    }

    private void cliente() {

        WaitingRoom tthis = this;

        HashMap<String, Integer> last_received = new HashMap<>();

        Helpers.threadRun(new Runnable() {

            public void run() {

                String recibido = "";

                String[] partes = null;

                exit = false;

                try {

                    String[] direccion = server_ip_port.split(":");

                    client_socket = new Socket(direccion[0], Integer.valueOf(direccion[1]));

                    //Le mandamos los bytes "mágicos"
                    byte[] magic = Helpers.toByteArray(MAGIC_BYTES);

                    client_socket.getOutputStream().write(magic);

                    Helpers.GUIRun(new Runnable() {
                        public void run() {
                            status.setText(Translator.translate("Conectado"));
                            pack();

                        }
                    });

                    byte[] avatar_bytes = null;

                    if (local_avatar != null && local_avatar.length() > 0) {
                        try (FileInputStream is = new FileInputStream(local_avatar)) {
                            avatar_bytes = is.readAllBytes();
                        }
                    }

                    //Le mandamos nuestro nick + VERSION + AVATAR al server
                    client_socket.getOutputStream().write((Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + "#" + AboutDialog.VERSION + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : "") + "\n").getBytes("UTF-8"));

                    client_inputstream = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));

                    //Leemos la respuesta del server
                    recibido = client_inputstream.readLine().trim();

                    partes = recibido.split("#");

                    if (partes[0].equals("BADVERSION")) {
                        exit = true;
                        Helpers.mostrarMensajeError(tthis, Translator.translate("Versión de CoronaPoker incorrecta") + "(" + partes[1] + ")");

                    } else if (partes[0].equals("YOUARELATE")) {
                        exit = true;
                        Helpers.mostrarMensajeError(tthis, "Llegas TARDE. La partida ya ha empezado.");

                    } else if (partes[0].equals("NOSPACE")) {
                        exit = true;
                        Helpers.mostrarMensajeError(tthis, "NO HAY SITIO");

                    } else if (partes[0].equals("NICKFAIL")) {
                        exit = true;
                        Helpers.mostrarMensajeError(tthis, "El nick elegido ya lo está usando otro usuario.");

                    } else if (partes[0].equals("NICKOK")) {

                        client_id = Integer.parseInt(partes[1]);

                        //Leemos el nick del server
                        recibido = client_inputstream.readLine().trim();

                        partes = recibido.split("#");

                        server_nick = new String(Base64.decodeBase64(partes[0]), "UTF-8").trim();

                        //Leemos el avatar del server
                        String server_avatar_base64 = partes.length > 1 ? partes[1] : "";

                        File server_avatar = null;

                        try {

                            if (server_avatar_base64.length() > 0) {

                                int file_id = Helpers.PRNG_GENERATOR.nextInt();

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
                        recibido = client_inputstream.readLine().trim();

                        String chat_text = new String(Base64.decodeBase64(recibido), "UTF-8");

                        Helpers.GUIRun(new Runnable() {
                            public void run() {

                                chat.setText(chat_text);
                            }
                        });

                        //Añadimos al servidor
                        nuevoParticipante(server_nick, server_avatar, null, null, false);

                        //Nos añadimos nosotros
                        nuevoParticipante(local_nick, local_avatar, null, null, false);

                        //Cada X segundos mandamos un comando KEEP ALIVE al server 
                        Helpers.threadRun(new Runnable() {
                            public void run() {

                                while (!exit && !WaitingRoom.isPartida_empezada()) {

                                    int ping = Helpers.PRNG_GENERATOR.nextInt();

                                    try {

                                        synchronized (getSocket_reconnect_lock()) {

                                            getClient_socket().getOutputStream().write(("PING#" + String.valueOf(ping) + "\n").getBytes("UTF-8"));
                                        }

                                    } catch (IOException ex) {
                                        Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                    synchronized (keep_alive_lock) {
                                        try {
                                            keep_alive_lock.wait(WaitingRoom.PING_PONG_TIMEOUT);
                                        } catch (InterruptedException ex) {
                                            Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }

                                    if (!exit && !WaitingRoom.isPartida_empezada() && ping + 1 != pong) {

                                        Logger.getLogger(WaitingRoom.class.getName()).log(Level.WARNING, "El servidor no respondió el PING");

                                    }

                                }

                            }
                        });

                        //Nos quedamos en bucle esperando mensajes del server
                        do {

                            recibido = null;

                            try {

                                recibido = client_inputstream.readLine().trim();

                                if (recibido != null) {

                                    String[] partes_comando = recibido.split("#");

                                    if (partes_comando[0].equals("PONG")) {

                                        pong = Integer.parseInt(partes_comando[1]);

                                    } else if (partes_comando[0].equals("PING")) {

                                        client_socket.getOutputStream().write(("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1) + "\n").getBytes("UTF-8"));

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

                                        Helpers.mostrarMensajeError(tthis, "El servidor ha cancelado la timba antes de empezar.");

                                    } else if (partes_comando[0].equals("KICKED")) {

                                        exit = true;

                                        Helpers.playWavResource("loser/payaso.wav");

                                        Helpers.mostrarMensajeInformativo(tthis, "¡A LA PUTA CALLE!");

                                    } else if (partes_comando[0].equals("GAME")) {

                                        //Confirmamos recepción al servidor
                                        String subcomando = partes_comando[2];

                                        int id = Integer.valueOf(partes_comando[1]);

                                        client_socket.getOutputStream().write(("CONF#" + String.valueOf(id + 1) + "#OK\n").getBytes("UTF-8"));

                                        if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != id) {

                                            last_received.put(subcomando, id);

                                            if (partida_empezada) {

                                                switch (subcomando) {
                                                    case "PAUSE":
                                                        Game.getInstance().pauseTimba();
                                                        break;
                                                    case "CINEMATICEND":
                                                        Game.getInstance().getCrupier().remoteCinematicEnd(null);
                                                        break;
                                                    case "SHOWCARDS":
                                                        Game.getInstance().getCrupier().showPlayerCards(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"), partes_comando[4], partes_comando[5]);
                                                        break;
                                                    case "EXIT":
                                                        Game.getInstance().getCrupier().playerExit(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        borrarParticipante(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        break;
                                                    case "SERVEREXIT":
                                                        exit = true;
                                                        Helpers.mostrarMensajeInformativo(Game.getInstance(), "EL SERVIDOR HA TERMINADO LA TIMBA");
                                                        break;
                                                    default:

                                                        synchronized (Game.getInstance().getCrupier().getReceived_commands()) {
                                                            Game.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                            Game.getInstance().getCrupier().getReceived_commands().notifyAll();
                                                        }

                                                        break;
                                                }

                                            } else {

                                                switch (subcomando) {

                                                    case "DELUSER":
                                                        if (partida_empezada) {
                                                            Game.getInstance().getCrupier().playerExit(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        }

                                                        borrarParticipante(new String(Base64.decodeBase64(partes_comando[3]), "UTF-8"));
                                                        break;

                                                    case "NEWUSER":
                                                        Helpers.playWavResource("misc/new_user.wav");

                                                        String nick = new String(Base64.decodeBase64(partes_comando[3]), "UTF-8");

                                                        File avatar = null;

                                                        int file_id = Helpers.PRNG_GENERATOR.nextInt();

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
                                                            nuevoParticipante(nick, avatar, null, null, false);
                                                        } else {
                                                            participantes.get(nick).setAvatar(avatar);
                                                        }

                                                        break;
                                                    case "USERSLIST":
                                                        String[] current_users_parts = partes_comando[3].split("@");

                                                        for (String user : current_users_parts) {

                                                            String[] user_parts = user.split("\\|");

                                                            nick = new String(Base64.decodeBase64(user_parts[0]), "UTF-8");

                                                            avatar = null;

                                                            if (user_parts.length == 2) {
                                                                file_id = Helpers.PRNG_GENERATOR.nextInt();

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
                                                                nuevoParticipante(nick, avatar, null, null, false);
                                                            } else {
                                                                participantes.get(nick).setAvatar(avatar);
                                                            }

                                                        }
                                                        break;

                                                    case "INIT":
                                                        setTitle(Init.WINDOW_TITLE + " - Chat (" + local_nick + ")");

                                                        partida_empezada = true;

                                                        Helpers.GUIRun(new Runnable() {
                                                            public void run() {
                                                                sound_icon.setVisible(false);
                                                                status.setText(Translator.translate("Inicializando timba..."));
                                                            }
                                                        });

                                                        Game.BUYIN = Integer.valueOf(partes_comando[3]);

                                                        Game.CIEGA_PEQUEÑA = Float.parseFloat(partes_comando[4]);

                                                        Game.CIEGA_GRANDE = Float.parseFloat(partes_comando[5]);

                                                        Game.CIEGAS_TIME = Integer.valueOf(partes_comando[6]);

                                                        Game.RECOVER = Boolean.parseBoolean(partes_comando[7]);

                                                        Game.REBUY = Boolean.parseBoolean(partes_comando[8]);

                                                        boolean ok;

                                                        do {
                                                            ok = true;

                                                            try {
                                                                //Inicializamos partida
                                                                new Game(participantes, tthis, local_nick, false);

                                                            } catch (ClassCastException ex) {
                                                                ok = false;
                                                                Helpers.pausar(500);
                                                            }
                                                        } while (!ok);

                                                        Game.getInstance().AJUGAR();
                                                        break;
                                                }
                                            }
                                        }

                                    } else if (partes_comando[0].equals("CONF")) {
                                        //Es una confirmación del servidor

                                        synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {
                                            WaitingRoom.getInstance().getReceived_confirmations().add(new Object[]{server_nick, Integer.parseInt(partes_comando[1])});
                                            WaitingRoom.getInstance().getReceived_confirmations().notifyAll();
                                        }
                                    }

                                }

                            } catch (IOException ex) {
                                //Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                                recibido = null;
                            } catch (Exception ex) {
                                Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                                recibido = null;
                            }

                            if (recibido == null && !exit && (!isPartida_empezada() || !Game.getInstance().getLocalPlayer().isExit())) {

                                if (reconectarCliente()) {
                                    recibido = "";
                                }

                            }

                        } while (!exit && recibido != null);

                    }

                } catch (IOException ex) {
                    //Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                    Helpers.mostrarMensajeError(tthis, "ERROR INESPERADO");
                    System.exit(1);
                }

                if (partida_empezada) {

                    Game.getInstance().finTransmision(exit);

                } else if (!exit) {

                    if (client_socket == null) {

                        Helpers.mostrarMensajeError(tthis, "ALGO HA FALLADO. (Probablemente la timba no esté aún creada).");

                    } else {

                        Helpers.mostrarMensajeError(tthis, "ALGO HA FALLADO. Has perdido la conexión con el servidor.");
                    }
                }

                exit = true;

                synchronized (keep_alive_lock) {
                    keep_alive_lock.notifyAll();
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {

                        ventana_inicio.setVisible(true);

                        dispose();
                    }
                });
            }
        });
    }

    private void enviarListaUsuariosActualesAlNuevoUsuario(String nick, Socket socket) {

        String command = "USERSLIST#";

        for (Map.Entry<String, Participant> entry : participantes.entrySet()) {

            Participant p = entry.getValue();

            try {

                if (p != null && !p.getNick().equals(nick)) {

                    command += Base64.encodeBase64String(p.getNick().getBytes("UTF-8"));

                    if (p.getAvatar() != null) {
                        byte[] avatar_b;

                        try (FileInputStream is = new FileInputStream(p.getAvatar())) {
                            avatar_b = is.readAllBytes();
                        }

                        command += "|" + Base64.encodeBase64String(avatar_b);
                    }

                    command += "@";
                }

            } catch (IOException ex) {
                Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        this.sendCommand(command, nick, socket, true);

    }

    private void servidor() {

        this.server_nick = this.local_nick;

        WaitingRoom tthis = this;

        Helpers.threadRun(new Runnable() {
            public void run() {

                String recibido = "";

                String[] partes = null;

                try {
                    String[] direccion = server_ip_port.trim().split(":");

                    server_socket = new ServerSocket(Integer.valueOf(direccion[1]));

                    while (!server_socket.isClosed()) {

                        Socket client = server_socket.accept();

                        //Leemos los bytes "mágicos"
                        byte[] magic = new byte[Helpers.toByteArray(MAGIC_BYTES).length];

                        client.getInputStream().read(magic);

                        if (Helpers.toHexString(magic).toLowerCase().equals(MAGIC_BYTES)) {

                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                            //Leemos el nick del usuario
                            recibido = in.readLine().trim();

                            partes = recibido.split("#");

                            String client_nick = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                            File client_avatar = null;

                            if (partes.length == 4) {

                                if (participantes.containsKey(client_nick) && Integer.parseInt(partes[3]) == participantes.get(client_nick).getId()) {

                                    //Es un usuario intentado reconectar
                                    participantes.get(client_nick).resetSocket(client);

                                    synchronized (getSocket_reconnect_lock()) {

                                        getSocket_reconnect_lock().notifyAll();
                                    }

                                    //Mandamos el chat
                                    client.getOutputStream().write((Base64.encodeBase64String(chat.getText().getBytes("UTF-8")) + "\n").getBytes("UTF-8"));

                                    if (!isPartida_empezada() && participantes.size() > 2) {

                                        enviarListaUsuariosActualesAlNuevoUsuario(client_nick, client);
                                    }

                                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.WARNING, null, "El usuario " + client_nick + " ha reconectado correctamente su socket.");

                                } else {
                                    try {
                                        client.close();
                                    } catch (Exception ex) {
                                    }
                                }

                            } else if (partida_empezada) {
                                client.getOutputStream().write(("YOUARELATE\n").getBytes("UTF-8"));
                            } else if (!partes[1].equals(AboutDialog.VERSION)) {
                                client.getOutputStream().write(("BADVERSION#" + AboutDialog.VERSION + "\n").getBytes("UTF-8"));
                            } else if (participantes.size() == MAX_PARTICIPANTES) {
                                client.getOutputStream().write(("NOSPACE\n").getBytes("UTF-8"));
                            } else if (participantes.containsKey(client_nick)) {
                                client.getOutputStream().write(("NICKFAIL\n").getBytes("UTF-8"));
                            } else {

                                //Leemos su avatar
                                String client_avatar_base64 = partes.length > 2 ? partes[2] : "";

                                try {

                                    if (client_avatar_base64.length() > 0) {

                                        int file_id = Helpers.PRNG_GENERATOR.nextInt();

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

                                if (!partida_empezada) {
                                    Helpers.playWavResource("misc/new_user.wav");
                                }

                                int cid = Helpers.PRNG_GENERATOR.nextInt();

                                //Mandamos al cliente su ID
                                client.getOutputStream().write(("NICKOK#" + String.valueOf(cid) + "\n").getBytes("UTF-8"));

                                byte[] avatar_bytes = null;

                                if (local_avatar != null && local_avatar.length() > 0) {

                                    try (FileInputStream is = new FileInputStream(local_avatar)) {
                                        avatar_bytes = is.readAllBytes();
                                    }
                                }

                                //Mandamos nuestro nick + avatar
                                client.getOutputStream().write((Base64.encodeBase64String(local_nick.getBytes("UTF-8")) + (avatar_bytes != null ? "#" + Base64.encodeBase64String(avatar_bytes) : "") + "\n").getBytes("UTF-8"));

                                //Mandamos el contenido del chat
                                client.getOutputStream().write((Base64.encodeBase64String(chat.getText().getBytes("UTF-8")) + "\n").getBytes("UTF-8"));

                                //Añadimos al participante
                                nuevoParticipante(client_nick, client_avatar, client, cid, false);

                                //Mandamos la lista de participantes actuales al nuevo participante
                                if (participantes.size() > 2) {
                                    enviarListaUsuariosActualesAlNuevoUsuario(client_nick, client);
                                }

                                File client_avatar_new = client_avatar;

                                //Mandamos el nuevo participante al resto de participantes
                                String comando = "NEWUSER#" + Base64.encodeBase64String(client_nick.getBytes("UTF-8"));

                                if (client_avatar_new != null) {

                                    byte[] avatar_b;

                                    try (FileInputStream is = new FileInputStream(client_avatar_new)) {
                                        avatar_b = is.readAllBytes();
                                    }

                                    comando += "#" + Base64.encodeBase64String(avatar_b);
                                }

                                broadcastCommandFromServer(comando, client_nick, true);

                                Helpers.GUIRun(new Runnable() {
                                    public void run() {
                                        empezar_timba.setVisible(true);
                                        kick_user.setVisible(true);
                                    }
                                });
                            }

                        } else {

                            try {
                                client_socket.close();
                            } catch (Exception e) {
                            }
                        }

                    }

                } catch (IOException ex) {
                    //Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);

                    if (server_socket == null) {

                        Helpers.mostrarMensajeError(tthis, "ALGO HA FALLADO. (Probablemente ya hay una timba creada en el mismo puerto).");
                    }
                } catch (Exception ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                    Helpers.mostrarMensajeError(tthis, "ERROR INESPERADO");
                    System.exit(1);
                }

                Helpers.GUIRun(new Runnable() {
                    public void run() {

                        ventana_inicio.setVisible(true);

                        dispose();

                    }
                });

            }
        });
    }

    public void recibirMensajeChat(String nick, String msg) {

        Helpers.GUIRun(new Runnable() {
            public void run() {
                chat.append("[" + nick + Translator.translate("] dice: ") + msg + "\n");

                if (!chat.isFocusOwner()) {
                    chat.setCaretPosition(chat.getText().length());
                }

                if (!isVisible()) {
                    Helpers.playWavResource("misc/chat_alert.wav");
                }
            }
        });

        if (this.server) {

            //Reenviamos el mensaje al resto de participantes
            participantes.entrySet().forEach((entry) -> {
                try {

                    Participant p = entry.getValue();

                    if (p != null && !p.isCpu() && !p.getNick().equals(nick)) {

                        String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));

                        p.getSocket().getOutputStream().write((comando + "\n").getBytes("UTF-8"));
                    }

                } catch (IOException ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
    }

    public static boolean isPartida_empezada() {
        return partida_empezada;
    }

    public void enviarMensajeChat(String nick, String msg) {

        Helpers.threadRun(new Runnable() {
            public void run() {

                if (!server) {
                    try {
                        String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));
                        getClient_socket().getOutputStream().write((comando + "\n").getBytes("UTF-8"));
                    } catch (IOException ex) {
                        Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {

                    participantes.entrySet().forEach((entry) -> {
                        try {
                            Participant participante = entry.getValue();
                            if (participante != null && !participante.isCpu()) {
                                String comando = "CHAT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")) + "#" + Base64.encodeBase64String(msg.getBytes("UTF-8"));
                                participante.getSocket().getOutputStream().write((comando + "\n").getBytes("UTF-8"));
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                }

            }
        });
    }

    public synchronized void borrarParticipante(String nick) {

        if (this.participantes.containsKey(nick)) {

            Helpers.GUIRunAndWait(new Runnable() {
                public void run() {

                    participantes.remove(nick);

                    DefaultListModel listModel = new DefaultListModel();

                    for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                        ParticipantsListRenderer label = new ParticipantsListRenderer();

                        label.setText(entry.getKey());
                        label.setSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT));
                        if (entry.getValue() != null) {

                            if (entry.getValue().getAvatar() != null) {
                                label.setIcon(new ImageIcon(new ImageIcon(entry.getValue().getAvatar().getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                            } else {
                                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                            }
                        } else {

                            if (local_avatar != null) {
                                label.setIcon(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                            } else {
                                label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                            }

                        }
                        listModel.addElement(label);

                    }

                    conectados.setModel(listModel);

                    if (participantes.size() < 2) {
                        empezar_timba.setVisible(false);
                        kick_user.setVisible(false);
                    }
                }
            });

            if (this.isServer() && !WaitingRoom.isPartida_empezada() && !exit) {

                String comando;
                try {
                    comando = "DELUSER#" + Base64.encodeBase64String(nick.getBytes("UTF-8"));
                    this.broadcastCommandFromServer(comando, nick, true);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }

    }

    private void nuevoParticipante(String nick, File avatar, Socket socket, Integer cid, boolean cpu) {

        Participant participante = new Participant(nick, avatar, socket, this, cid, cpu);

        if (socket != null) {

            Helpers.threadRun(participante);

        }

        Helpers.GUIRunAndWait(new Runnable() {
            public void run() {

                participantes.put(nick, participante);

                DefaultListModel listModel = new DefaultListModel();

                for (Map.Entry<String, Participant> entry : participantes.entrySet()) {
                    ParticipantsListRenderer label = new ParticipantsListRenderer();

                    label.setText(entry.getKey());
                    label.setSize(new Dimension(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT));

                    if (entry.getValue() != null) {

                        if (entry.getValue().getAvatar() != null) {
                            label.setIcon(new ImageIcon(new ImageIcon(entry.getValue().getAvatar().getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                        } else {
                            label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                        }
                    } else {

                        if (local_avatar != null) {
                            label.setIcon(new ImageIcon(new ImageIcon(local_avatar.getAbsolutePath()).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                        } else {
                            label.setIcon(new ImageIcon(new ImageIcon(getClass().getResource("/images/avatar_default.png")).getImage().getScaledInstance(NewGameDialog.DEFAULT_AVATAR_WIDTH, NewGameDialog.DEFAULT_AVATAR_HEIGHT, Image.SCALE_SMOOTH)));
                        }

                    }
                    listModel.addElement(label);
                }

                conectados.setModel(listModel);

                if (nick.equals(local_nick)) {
                    pack();
                }

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
        logo = new javax.swing.JLabel();
        mensaje = new javax.swing.JTextField();
        status = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        conectados = new javax.swing.JList<>();
        empezar_timba = new javax.swing.JButton();
        kick_user = new javax.swing.JButton();
        avatar_label = new javax.swing.JLabel();
        status1 = new javax.swing.JLabel();
        sound_icon = new javax.swing.JLabel();
        new_bot_button = new javax.swing.JButton();

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
        chat.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        chat.setLineWrap(true);
        chat.setRows(5);
        chat.setDoubleBuffered(true);
        jScrollPane1.setViewportView(chat);

        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/corona_poker_15.png"))); // NOI18N
        logo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        logo.setDoubleBuffered(true);
        logo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                logoMouseClicked(evt);
            }
        });

        mensaje.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        mensaje.setDoubleBuffered(true);
        mensaje.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mensajeActionPerformed(evt);
            }
        });

        status.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        status.setForeground(new java.awt.Color(51, 153, 0));
        status.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status.setText("Estado");
        status.setDoubleBuffered(true);

        jScrollPane3.setDoubleBuffered(true);

        conectados.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        conectados.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        conectados.setToolTipText("Participantes conectados");
        conectados.setCellRenderer(new com.tonikelope.coronapoker.ParticipantsListRenderer());
        conectados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        conectados.setDoubleBuffered(true);
        jScrollPane3.setViewportView(conectados);

        empezar_timba.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        empezar_timba.setText("EMPEZAR YA");
        empezar_timba.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        empezar_timba.setDoubleBuffered(true);
        empezar_timba.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                empezar_timbaActionPerformed(evt);
            }
        });

        kick_user.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        kick_user.setText("Expulsar jugador");
        kick_user.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        kick_user.setDoubleBuffered(true);
        kick_user.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kick_userActionPerformed(evt);
            }
        });

        avatar_label.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        avatar_label.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/avatar_default.png"))); // NOI18N
        avatar_label.setText("Toni");

        status1.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        status1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status1.setText("1.1.1.1");
        status1.setDoubleBuffered(true);

        sound_icon.setBackground(new java.awt.Color(153, 153, 153));
        sound_icon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/mute_b.png"))); // NOI18N
        sound_icon.setToolTipText("Click para activar/desactivar el sonido");
        sound_icon.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        sound_icon.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                sound_iconMouseClicked(evt);
            }
        });

        new_bot_button.setFont(new java.awt.Font("Dialog", 1, 18)); // NOI18N
        new_bot_button.setText("Añadir bot");
        new_bot_button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        new_bot_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                new_bot_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(empezar_timba, javax.swing.GroupLayout.DEFAULT_SIZE, 346, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(sound_icon)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(status1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(new_bot_button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(logo)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 213, Short.MAX_VALUE)
                            .addComponent(kick_user, javax.swing.GroupLayout.DEFAULT_SIZE, 213, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(avatar_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mensaje)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(kick_user))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(logo)
                        .addGap(20, 20, 20)
                        .addComponent(new_bot_button)
                        .addGap(18, 18, 18)
                        .addComponent(status1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(status)
                            .addComponent(sound_icon))
                        .addGap(18, 18, 18)
                        .addComponent(empezar_timba, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(12, 12, 12)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(avatar_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(mensaje))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void mensajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mensajeActionPerformed
        // TODO add your handling code here:

        chat.append("[" + local_nick + Translator.translate("] dice: ") + this.mensaje.getText() + "\n");

        if (!chat.isFocusOwner()) {
            chat.setCaretPosition(chat.getText().length());
        }

        this.enviarMensajeChat(local_nick, this.mensaje.getText());

        this.mensaje.setText("");
    }//GEN-LAST:event_mensajeActionPerformed

    private void kick_userActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kick_userActionPerformed

        // TODO add your handling code here:
        String expulsado = ((JLabel) ((DefaultListModel) conectados.getModel()).get(conectados.getSelectedIndex())).getText();

        if (!expulsado.equals(local_nick)) {

            Helpers.threadRun(new Runnable() {
                public void run() {
                    try {

                        if (!participantes.get(expulsado).isCpu()) {

                            String comando = "KICKED#" + Base64.encodeBase64String(expulsado.getBytes("UTF-8"));
                            participantes.get(expulsado).getSocket().getOutputStream().write((comando + "\n").getBytes("UTF-8"));
                        }

                        participantes.get(expulsado).setExit();

                        borrarParticipante(expulsado);

                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }

    }//GEN-LAST:event_kick_userActionPerformed

    private void empezar_timbaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_empezar_timbaActionPerformed
        // TODO add your handling code here:

        if (participantes.size() >= 2 && !partida_empezada) {

            boolean faltan_jugadores = false;

            if (Game.RECOVER && Files.exists(Paths.get(Crupier.RECOVER_BALANCE_FILE))) {

                try {
                    String datos = Files.readString(Paths.get(Crupier.RECOVER_BALANCE_FILE));
                    String[] partes = datos.split("#");
                    String[] auditor_partes = partes[10].split("@");

                    for (String player_data : auditor_partes) {

                        partes = player_data.split("\\|");

                        String nick = new String(Base64.decodeBase64(partes[0]), "UTF-8");

                        if (!participantes.containsKey(nick)) {
                            faltan_jugadores = true;
                            break;
                        }
                    }

                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            boolean vamos = (!faltan_jugadores || Helpers.mostrarMensajeInformativoSINO(this, "Hay jugadores de la timba anterior que no se han vuelto a conectar.\n(Si no se conectan no se podrá recuperar la última mano en curso).\n\n¿EMPEZAMOS YA?") == 0);

            if (vamos) {

                WaitingRoom tthis = this;
                WaitingRoom.partida_empezada = true;

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

                        boolean ok;

                        do {
                            ok = true;

                            try {
                                //Inicializamos partida
                                new Game(participantes, tthis, local_nick, true);

                            } catch (ClassCastException ex) {
                                ok = false;
                                Helpers.pausar(500);
                            }
                        } while (!ok);

                        Game.getInstance().AJUGAR();
                    }
                });
            }
        }
    }//GEN-LAST:event_empezar_timbaActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        if (!WaitingRoom.partida_empezada) {

            exit = true;

            Helpers.threadRun(new Runnable() {
                public void run() {

                    if (isServer()) {

                        participantes.entrySet().forEach((entry) -> {

                            Participant p = entry.getValue();

                            if (p != null) {

                                p.setExit();
                            }

                        });

                        if (getServer_socket() != null) {
                            try {
                                getServer_socket().close();
                            } catch (Exception ex) {
                                Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                    } else if (getClient_socket() != null) {

                        try {
                            getClient_socket().getOutputStream().write("EXIT\n".getBytes("UTF-8"));
                            getClient_socket().getOutputStream().close();
                        } catch (Exception ex) {
                            Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            });

        } else {
            setVisible(false);
        }
    }//GEN-LAST:event_formWindowClosing

    private void sound_iconMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sound_iconMouseClicked
        // TODO add your handling code here:

        Game.SONIDOS = !Game.SONIDOS;

        Helpers.PROPERTIES.setProperty("sonidos", Game.SONIDOS ? "true" : "false");

        Helpers.savePropertiesFile();

        Helpers.GUIRun(new Runnable() {
            public void run() {

                sound_icon.setIcon(new ImageIcon(getClass().getResource(Game.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")));

            }
        });

        if (!Game.SONIDOS) {

            Helpers.muteAll();

        } else {

            Helpers.unMuteAll();

        }
    }//GEN-LAST:event_sound_iconMouseClicked

    private void formComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentShown
        // TODO add your handling code here:
        sound_icon.setIcon(new ImageIcon(getClass().getResource(Game.SONIDOS ? "/images/sound_b.png" : "/images/mute_b.png")));

        mensaje.requestFocusInWindow();
    }//GEN-LAST:event_formComponentShown

    private void logoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoMouseClicked
        // TODO add your handling code here:
        AboutDialog dialog = new AboutDialog(this, true);

        dialog.setLocationRelativeTo(this);

        dialog.setVisible(true);
    }//GEN-LAST:event_logoMouseClicked

    private void new_bot_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_new_bot_buttonActionPerformed

        if (participantes.size() < MAX_PARTICIPANTES) {
            try {
                // TODO add your handling code here:
                String bot_nick;

                do {
                    bot_nick = "CoronaBot#" + Helpers.genRandomString(3);

                } while (participantes.get(bot_nick) != null);

                nuevoParticipante(bot_nick, null, null, null, true);

                //Mandamos el nuevo participante al resto de participantes
                String comando = "NEWUSER#" + Base64.encodeBase64String(bot_nick.getBytes("UTF-8"));

                broadcastCommandFromServer(comando, bot_nick, true);

                Helpers.GUIRun(new Runnable() {
                    public void run() {
                        empezar_timba.setVisible(true);
                        kick_user.setVisible(true);
                    }
                });

            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(WaitingRoom.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_new_bot_buttonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel avatar_label;
    private javax.swing.JTextArea chat;
    private javax.swing.JList<String> conectados;
    private javax.swing.JButton empezar_timba;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton kick_user;
    private javax.swing.JLabel logo;
    private javax.swing.JTextField mensaje;
    private javax.swing.JButton new_bot_button;
    private javax.swing.JLabel sound_icon;
    private javax.swing.JLabel status;
    private javax.swing.JLabel status1;
    // End of variables declaration//GEN-END:variables
}
