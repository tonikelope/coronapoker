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

import static com.tonikelope.coronapoker.GameFrame.WAIT_QUEUES;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.KeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class Participant implements Runnable {

    public static final int ASYNC_COMMAND_QUEUE_WAIT = 1000;
    public static final int RECIBIDO_TIMEOUT = 5000;

    private final Object keep_alive_lock = new Object();
    private final Object participant_socket_lock = new Object();
    private final HashMap<String, Integer> last_received = new HashMap<>();
    private final ConcurrentLinkedQueue<String> async_command_queue = new ConcurrentLinkedQueue<>();
    private final WaitingRoomFrame sala_espera;
    private final String nick;
    private final File avatar;

    private volatile Socket socket = null;
    private volatile boolean exit = false;
    private volatile BufferedReader input_stream = null;
    private volatile int pong;
    private volatile boolean cpu = false;
    private volatile Boolean resetting_socket = false;
    private volatile SecretKeySpec aes_key = null;
    private volatile SecretKeySpec hmac_key = null;
    private volatile SecretKeySpec hmac_key_orig = null;
    private volatile SecretKeySpec permutation_key = null;
    private volatile String permutation_key_hash = null;
    private volatile int new_hand_ready = 0;
    private volatile boolean unsecure_player = false;
    private volatile boolean reset_socket = false;

    public Participant(WaitingRoomFrame espera, String nick, File avatar, Socket socket, SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu) {

        this.nick = nick;
        this.setSocket(socket);
        this.sala_espera = espera;
        this.avatar = avatar;
        this.cpu = cpu;
        this.aes_key = aes_k;
        this.hmac_key = hmac_k;
        this.hmac_key_orig = hmac_k;

        if (this.aes_key != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(this.nick.getBytes("UTF-8"));
                md.update(this.sala_espera.getServer_nick().getBytes("UTF-8"));
                md.update(this.aes_key.getEncoded());
                md.update(this.hmac_key.getEncoded());
                this.permutation_key = new SecretKeySpec(md.digest(), "AES");
                md = MessageDigest.getInstance("MD5");
                this.permutation_key_hash = Base64.encodeBase64String(md.digest(this.permutation_key.getEncoded()));
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean isUnsecure_player() {
        return unsecure_player;
    }

    public void setUnsecure_player(boolean unsecure_player) {
        this.unsecure_player = unsecure_player;
    }

    public String getPermutation_key_hash() {
        return permutation_key_hash;
    }

    public SecretKeySpec getPermutation_key() {
        return permutation_key;
    }

    public int getNew_hand_ready() {
        return new_hand_ready;
    }

    public Object getParticipant_socket_lock() {

        return participant_socket_lock;
    }

    public ConcurrentLinkedQueue<String> getAsync_command_queue() {
        return async_command_queue;
    }

    public SecretKeySpec getHmac_key_orig() {
        return hmac_key_orig;
    }

    public SecretKeySpec getHmac_key() {

        while (resetting_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return hmac_key;
    }

    public SecretKeySpec getAes_key() {

        while (resetting_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return aes_key;

    }

    public BufferedReader getInput_stream() {

        while (resetting_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return input_stream;
    }

    public boolean isCpu() {
        return cpu;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    public void exitAndCloseSocket() {

        this.exit = true;

        if (this.socket != null) {
            try {

                if (!WaitingRoomFrame.getInstance().isPartida_empezada()) {
                    this.writeCommandFromServer(Helpers.encryptCommand("EXIT", this.getAes_key(), this.getHmac_key()), false);
                }
                this.socketClose();
            } catch (IOException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }

            synchronized (keep_alive_lock) {
                keep_alive_lock.notifyAll();
            }
        }

    }

    public boolean isExit() {
        return exit;
    }

    public File getAvatar() {
        return avatar;
    }

    public String getNick() {
        return nick;
    }

    public void writeCommandFromServer(String command) {

        writeCommandFromServer(command, true);

    }

    public void writeCommandFromServer(String command, boolean retry) {
        boolean ok;
        do {
            ok = true;
            while (resetting_socket) {
                synchronized (getParticipant_socket_lock()) {
                    try {
                        getParticipant_socket_lock().wait(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            try {
                synchronized (getParticipant_socket_lock()) {
                    this.socket.getOutputStream().write((command + "\n").getBytes("UTF-8"));
                }
            } catch (IOException ex) {

                ok = false;

                if (retry && !resetting_socket && !isExit() && !WaitingRoomFrame.getInstance().isExit()) {

                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);

                    Helpers.pausar(1000);
                }
            }
        } while (retry && !ok && !isExit() && !WaitingRoomFrame.getInstance().isExit());
    }

    public String readCommandFromClient() throws KeyException, IOException {

        while (resetting_socket) {
            synchronized (getParticipant_socket_lock()) {
                try {
                    getParticipant_socket_lock().wait(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return Helpers.decryptCommand(this.getInput_stream().readLine(), this.getAes_key(), this.getHmac_key());
    }

    public void socketClose() throws IOException {
        synchronized (getParticipant_socket_lock()) {
            this.socket.close();
        }
    }

    private void setSocket(Socket socket) {

        synchronized (getParticipant_socket_lock()) {
            this.socket = socket;

            if (this.socket != null) {

                try {
                    this.input_stream = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                } catch (IOException ex) {
                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public boolean resetSocket(Socket sock, SecretKeySpec aes_k, SecretKeySpec hmac_k) {

        this.resetting_socket = true;

        if (this.socket != null && !this.socket.isClosed()) {

            try {
                this.socket.close();
            } catch (Exception ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        synchronized (getParticipant_socket_lock()) {

            try {

                Logger.getLogger(WaitingRoomFrame.class.getName()).log(Level.WARNING, "Enviando datos del chat...");

                //Mandamos el chat
                sock.getOutputStream().write((Helpers.encryptCommand(WaitingRoomFrame.getInstance().getChat().getText().isEmpty() ? "*" : Base64.encodeBase64String(WaitingRoomFrame.getInstance().getChat().getText().getBytes("UTF-8")), aes_k, hmac_k) + "\n").getBytes("UTF-8"));

                this.socket = sock;

                this.input_stream = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

                this.aes_key = aes_k;

                this.hmac_key = hmac_k;

                this.resetting_socket = false;

                this.reset_socket = true;

                getParticipant_socket_lock().notifyAll();

                return true;

            } catch (IOException ex) {

                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);

                this.resetting_socket = false;

                getParticipant_socket_lock().notifyAll();

                return false;
            }
        }
    }

    public boolean waitAsyncConfirmations(int id, ArrayList<String> pending) {

        //Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        ArrayList<Object[]> rejected = new ArrayList<>();

        while (!exit && !pending.isEmpty() && !timeout) {

            Object[] confirmation;

            synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                while (!exit && !WaitingRoomFrame.getInstance().getReceived_confirmations().isEmpty()) {

                    confirmation = WaitingRoomFrame.getInstance().getReceived_confirmations().poll();

                    if (confirmation != null && confirmation[0] != null && confirmation[1] != null) {

                        if ((int) confirmation[1] == id + 1) {
                            pending.remove((String) confirmation[0]);
                        } else {
                            rejected.add(confirmation);
                        }
                    }
                }

                if (!exit) {

                    if (!rejected.isEmpty()) {
                        WaitingRoomFrame.getInstance().getReceived_confirmations().addAll(rejected);
                        rejected.clear();
                    }

                    if (System.currentTimeMillis() - start_time > GameFrame.CONFIRMATION_TIMEOUT) {
                        timeout = true;
                    } else if (!pending.isEmpty()) {

                        try {
                            WaitingRoomFrame.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }

        }

        return !pending.isEmpty();
    }

    @Override
    public void run() {

        if (socket != null) {

            //Cada X segundos mandamos un comando KEEP ALIVE al cliente
            Helpers.threadRun(new Runnable() {
                public void run() {

                    while (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                        int ping = Helpers.CSPRNG_GENERATOR.nextInt();

                        writeCommandFromServer("PING#" + String.valueOf(ping));
                        if (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                            synchronized (keep_alive_lock) {
                                try {
                                    keep_alive_lock.wait(WaitingRoomFrame.PING_PONG_TIMEOUT);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        if (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada() && ping + 1 != pong) {

                            Logger.getLogger(Participant.class.getName()).log(Level.WARNING, "{0} NO respondió al PING {1} {2}", new Object[]{nick, String.valueOf(ping), String.valueOf(pong)});

                        }

                    }
                }
            });

            //Creamos un hilo por cada participante para enviar comandos de juego con confirmación y no bloquear el servidor por si se conectan nuevos usuarios
            Helpers.threadRun(new Runnable() {
                public void run() {

                    while (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {

                        while (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada() && !getAsync_command_queue().isEmpty()) {

                            String command = getAsync_command_queue().peek();

                            int id = Helpers.CSPRNG_GENERATOR.nextInt();

                            String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                            ArrayList<String> pendientes = new ArrayList<>();

                            pendientes.add(getNick());

                            do {
                                synchronized (getParticipant_socket_lock()) {

                                    writeCommandFromServer(Helpers.encryptCommand(full_command, getAes_key(), getHmac_key()));
                                }
                                waitAsyncConfirmations(id, pendientes);

                            } while (!pendientes.isEmpty() && !exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada());

                            getAsync_command_queue().poll();

                        }

                        if (!exit && !WaitingRoomFrame.getInstance().isExit() && !WaitingRoomFrame.getInstance().isPartida_empezada()) {
                            synchronized (getAsync_command_queue()) {

                                try {
                                    getAsync_command_queue().wait(ASYNC_COMMAND_QUEUE_WAIT);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    }

                }

            });

            String recibido = null;
            boolean timeout = false;

            do {

                reset_socket = false;

                try {

                    recibido = this.readCommandFromClient();

                    if (recibido != null) {

                        if (timeout) {
                            timeout = false;
                            GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(timeout);
                        }

                        String[] partes_comando = recibido.split("#");

                        switch (partes_comando[0]) {
                            case "PONG":
                                pong = Integer.parseInt(partes_comando[1]);
                                break;
                            case "PING":
                                this.writeCommandFromServer(("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1)));
                                break;
                            case "EXIT":
                                exit = true;
                                break;
                            case "CHAT":
                                //Los mensajes de chat no llevan confirmación al no considerarse prioritarios (TO-DO)
                                String mensaje;
                                if (partes_comando.length == 3) {

                                    mensaje = new String(Base64.decodeBase64(partes_comando[2]), "UTF-8");

                                } else {
                                    mensaje = "";
                                }
                                sala_espera.recibirMensajeChat(new String(Base64.decodeBase64(partes_comando[1]), "UTF-8"), mensaje);
                                break;
                            case "CONF":
                                //Es una confirmación de un cliente
                                WaitingRoomFrame.getInstance().getReceived_confirmations().add(new Object[]{nick, Integer.parseInt(partes_comando[1])});
                                synchronized (WaitingRoomFrame.getInstance().getReceived_confirmations()) {

                                    WaitingRoomFrame.getInstance().getReceived_confirmations().notifyAll();
                                }

                                break;
                            case "GAME":
                                //Es un comando de juego del cliente
                                String subcomando = partes_comando[2];
                                int command_id = Integer.valueOf(partes_comando[1]); //Los comandos del juego llevan confirmación de recepción
                                this.writeCommandFromServer(("CONF#" + String.valueOf(command_id + 1) + "#OK"));
                                if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != command_id) {

                                    last_received.put(subcomando, command_id);

                                    switch (subcomando) {
                                        case "PING":
                                            //ES UN PING DE JUEGO -> NO tenemos que hacer nada más
                                            break;

                                        case "CINEMATICEND":
                                            GameFrame.getInstance().getCrupier().remoteCinematicEnd(nick);
                                            break;

                                        case "PERMUTATIONKEY":
                                            Helpers.threadRun(new Runnable() {
                                                public void run() {
                                                    synchronized (GameFrame.getInstance().getCrupier().getPermutation_key_lock()) {

                                                        GameFrame.getInstance().getCrupier().setPermutation_key(partes_comando[3]);
                                                        GameFrame.getInstance().getCrupier().getPermutation_key_lock().notifyAll();
                                                    }
                                                }
                                            });
                                            break;
                                        case "PAUSE":

                                            Helpers.threadRun(new Runnable() {
                                                public void run() {
                                                    synchronized (GameFrame.getInstance().getLock_pause()) {

                                                        if (("0".equals(partes_comando[3]) && GameFrame.getInstance().isTimba_pausada()) && nick.equals(GameFrame.getInstance().getNick_pause()) || ("1".equals(partes_comando[3]) && !GameFrame.getInstance().isTimba_pausada())) {
                                                            GameFrame.getInstance().pauseTimba(nick);

                                                            if (GameFrame.getInstance().isTimba_pausada()) {
                                                                GameFrame.getInstance().getRegistro().print("PAUSE (" + nick + ")");
                                                            }
                                                        }
                                                    }
                                                }
                                            });

                                            break;

                                        case "IWTSTH":

                                            if (!GameFrame.getInstance().getCrupier().isIwtsthing()) {
                                                GameFrame.getInstance().getCrupier().IWTSTH_HANDLER(nick);
                                            }

                                            break;

                                        case "REBUYNOW":
                                            GameFrame.getInstance().getCrupier().rebuyNow(nick, Integer.parseInt(partes_comando[3]));
                                            break;
                                        case "SHOWMYCARDS":
                                            GameFrame.getInstance().getCrupier().showAndBroadcastPlayerCards(nick);
                                            break;
                                        case "NEWHANDREADY":

                                            this.new_hand_ready = Integer.parseInt(partes_comando[3]);

                                            synchronized (GameFrame.getInstance().getCrupier().getLock_nueva_mano()) {
                                                GameFrame.getInstance().getCrupier().getLock_nueva_mano().notifyAll();
                                            }
                                            break;
                                        case "EXIT":
                                            GameFrame.getInstance().getCrupier().remotePlayerQuit(nick);
                                            exit = true;
                                            break;
                                        default:
                                                //Metemos el mensaje en la cola
                                                synchronized (GameFrame.getInstance().getCrupier().getReceived_commands()) {
                                                GameFrame.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                GameFrame.getInstance().getCrupier().getReceived_commands().notifyAll();
                                            }
                                            break;
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    } else {

                        if (!exit && !WaitingRoomFrame.getInstance().isExit()) {

                            Logger.getLogger(Participant.class.getName()).log(Level.WARNING, nick + " -> EL SOCKET RECIBIÓ NULL");

                        }

                    }

                } catch (Exception ex) {

                    recibido = null;

                    if (!exit && !WaitingRoomFrame.getInstance().isExit()) {

                        Logger.getLogger(Participant.class.getName()).log(Level.WARNING, nick + " -> EXCEPCION AL LEER DEL SOCKET", ex);

                    }

                } finally {

                    if (recibido == null && !reset_socket && !exit && !WaitingRoomFrame.getInstance().isExit() && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                        if (!timeout) {

                            timeout = true;

                            GameFrame.getInstance().getCrupier().getNick2player().get(nick).setTimeout(timeout);

                            try {
                                GameFrame.getInstance().getCrupier().broadcastGAMECommandFromServer("TIMEOUT#" + Base64.encodeBase64String(nick.getBytes("UTF-8")), nick, false);
                            } catch (UnsupportedEncodingException ex) {
                                Logger.getLogger(Crupier.class.getName()).log(Level.SEVERE, null, ex);
                            }

                            if (!reset_socket) {
                                synchronized (getParticipant_socket_lock()) {

                                    try {
                                        getParticipant_socket_lock().wait(resetting_socket ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                }

                            }

                        } else {

                            int input = Helpers.mostrarMensajeErrorSINO(GameFrame.getInstance().getFrame(), nick + Translator.translate(" parece que perdió la conexión y no ha vuelto a conectar (se le eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?"));

                            // 0=yes, 1=no, 2=cancel
                            if (input == 1) {

                                exit = true;

                                GameFrame.getInstance().getCrupier().remotePlayerQuit(nick);

                            } else if (!reset_socket) {

                                synchronized (getParticipant_socket_lock()) {

                                    try {
                                        getParticipant_socket_lock().wait(resetting_socket ? GameFrame.CLIENT_RECON_TIMEOUT : RECIBIDO_TIMEOUT);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                }

                            }

                        }

                    }

                }

            } while (!exit && !WaitingRoomFrame.getInstance().isExit() && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision()));

            if (!WaitingRoomFrame.getInstance().isExit() && (GameFrame.getInstance() == null || GameFrame.getInstance().getCrupier() == null || !GameFrame.getInstance().getCrupier().isFin_de_la_transmision())) {

                if (WaitingRoomFrame.getInstance().isPartida_empezada() && !exit) {

                    GameFrame.getInstance().getCrupier().remotePlayerQuit(nick);

                }

                sala_espera.borrarParticipante(nick);

            }

            exit = true;

            synchronized (keep_alive_lock) {
                keep_alive_lock.notifyAll();
            }

        } else {
            this.exit = true;
        }

    }

}
