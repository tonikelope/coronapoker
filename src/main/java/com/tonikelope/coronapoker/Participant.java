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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyException;
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

    private final Object keep_alive_lock = new Object();
    private final Object participant_socket_lock = new Object();
    private final HashMap<String, Integer> last_received = new HashMap<>();
    private final ConcurrentLinkedQueue<String> async_command_queue = new ConcurrentLinkedQueue<>();
    private final WaitingRoom sala_espera;
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
    private volatile int new_hand_ready = 0;

    public Participant(WaitingRoom espera, String nick, File avatar, Socket socket, SecretKeySpec aes_k, SecretKeySpec hmac_k, boolean cpu) {
        this.nick = nick;
        this.setSocket(socket);
        this.sala_espera = espera;
        this.avatar = avatar;
        this.cpu = cpu;
        this.aes_key = aes_k;
        this.hmac_key = hmac_k;
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

    public boolean isCpu() {
        return cpu;
    }

    public void setExit() {

        if (!this.exit) {
            this.exit = true;

            if (this.socket != null) {
                try {

                    if (!WaitingRoom.isPartida_empezada()) {
                        this.writeCommandFromServer(Helpers.encryptCommand("EXIT", this.getAes_key(), this.getHmac_key()));
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

    public void writeCommandFromServer(String command) throws IOException {

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
            } catch (SocketException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                ok = false;
            }

        } while (!ok);

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

        String recibido = this.input_stream.readLine();

        if (recibido != null && recibido.startsWith("*")) {
            recibido = Helpers.decryptCommand(recibido.trim(), this.getAes_key(), this.getHmac_key());
        } else if (recibido != null) {
            recibido = recibido.trim();
        }

        return recibido;
    }

    public void socketClose() throws IOException {
        synchronized (getParticipant_socket_lock()) {
            this.socket.getOutputStream().close();
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

    public void resetSocket(Socket socket, SecretKeySpec aes_key, SecretKeySpec hmac_key) {

        this.resetting_socket = true;

        if (this.socket != null && !this.socket.isClosed()) {

            try {
                this.socket.close();
            } catch (Exception ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        synchronized (participant_socket_lock) {

            this.aes_key = aes_key;

            this.hmac_key = hmac_key;

            this.socket = socket;

            try {
                this.input_stream = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            } catch (IOException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }

            this.resetting_socket = false;
        }
    }

    public boolean waitAsyncConfirmations(int id, ArrayList<String> pending) {

        //Esperamos confirmación
        long start_time = System.currentTimeMillis();

        boolean timeout = false;

        while (!exit && !pending.isEmpty() && !timeout) {

            ArrayList<Object[]> rejected = new ArrayList<>();

            Object[] confirmation;

            while (!exit && !WaitingRoom.getInstance().getReceived_confirmations().isEmpty()) {

                confirmation = WaitingRoom.getInstance().getReceived_confirmations().poll();

                if (confirmation != null) {
                    try {

                        if ((int) confirmation[1] == id + 1) {

                            pending.remove(confirmation[0]);

                        } else {

                            rejected.add(confirmation);

                        }

                    } catch (Exception ex) {
                    }
                }
            }

            if (!exit) {

                if (!rejected.isEmpty()) {
                    WaitingRoom.getInstance().getReceived_confirmations().addAll(rejected);
                    rejected.clear();
                }

                if (System.currentTimeMillis() - start_time > Game.CONFIRMATION_TIMEOUT) {
                    timeout = true;
                } else if (!pending.isEmpty()) {

                    synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {
                        try {
                            WaitingRoom.getInstance().getReceived_confirmations().wait(WAIT_QUEUES);
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

                    while (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada()) {

                        int ping = Helpers.SPRNG_GENERATOR.nextInt();

                        try {

                            writeCommandFromServer("PING#" + String.valueOf(ping));

                            if (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada()) {
                                synchronized (keep_alive_lock) {
                                    try {
                                        keep_alive_lock.wait(WaitingRoom.PING_PONG_TIMEOUT);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                            if (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada() && ping + 1 != pong) {

                                Logger.getLogger(Participant.class.getName()).log(Level.WARNING, nick + " NO respondió al PING " + String.valueOf(ping) + " " + String.valueOf(pong));

                            }

                        } catch (IOException ex) {
                            Logger.getLogger(Participant.class.getName()).log(Level.WARNING, nick + " NO respondió al PING (excepción) " + String.valueOf(ping) + " " + String.valueOf(pong));
                        }

                    }
                }
            });

            //Creamos un hilo por cada participante para enviar comandos de juego con confirmación y no bloquear el servidor por si se conectan nuevos usuarios
            Helpers.threadRun(new Runnable() {
                public void run() {

                    while (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada()) {

                        while (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada() && !getAsync_command_queue().isEmpty()) {

                            String command = getAsync_command_queue().peek();

                            int id = Helpers.SPRNG_GENERATOR.nextInt();

                            String full_command = "GAME#" + String.valueOf(id) + "#" + command;

                            ArrayList<String> pendientes = new ArrayList<>();

                            pendientes.add(getNick());

                            do {
                                try {

                                    synchronized (getParticipant_socket_lock()) {

                                        writeCommandFromServer(Helpers.encryptCommand(full_command, getAes_key(), getHmac_key()));
                                    }

                                    waitAsyncConfirmations(id, pendientes);

                                } catch (IOException ex) {

                                    Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);

                                }

                            } while (!pendientes.isEmpty() && !exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada());

                            getAsync_command_queue().poll();

                        }

                        if (!exit && !WaitingRoom.isExit() && !WaitingRoom.isPartida_empezada()) {
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

            String recibido;

            do {

                try {

                    recibido = this.readCommandFromClient();

                    if (recibido != null) {

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
                                WaitingRoom.getInstance().getReceived_confirmations().add(new Object[]{nick, Integer.parseInt(partes_comando[1])});
                                synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {

                                    WaitingRoom.getInstance().getReceived_confirmations().notifyAll();
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
                                            Game.getInstance().getCrupier().remoteCinematicEnd(nick);
                                            break;
                                        case "PAUSE":
                                            
                                            synchronized (Game.getInstance().getLock_pause()) {
                                                if (!Game.getInstance().isTimba_pausada() || nick.equals(Game.getInstance().getNick_pause())) {

                                                    Game.getInstance().pauseTimba(nick);

                                                    if (!Game.getInstance().isTimba_pausada()) {
                                                        Game.getInstance().getRegistro().print("PAUSE (" + nick + ")");
                                                    }
                                                }
                                            }

                                            break;
                                        case "SHOWMYCARDS":
                                            Game.getInstance().getCrupier().showAndBroadcastPlayerCards(nick);
                                            break;
                                        case "NEWHANDREADY":

                                            this.new_hand_ready = Integer.parseInt(partes_comando[3]);

                                            synchronized (Game.getInstance().getCrupier().getLock_nueva_mano()) {
                                                Game.getInstance().getCrupier().getLock_nueva_mano().notifyAll();
                                            }
                                            break;
                                        case "EXIT":
                                            Game.getInstance().getCrupier().remotePlayerQuit(nick);
                                            exit = true;
                                            break;
                                        default:
                                                //Metemos el mensaje en la cola
                                                synchronized (Game.getInstance().getCrupier().getReceived_commands()) {
                                                Game.getInstance().getCrupier().getReceived_commands().add(recibido);
                                                Game.getInstance().getCrupier().getReceived_commands().notifyAll();
                                            }
                                            break;
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                    } else {
                        Logger.getLogger(Participant.class.getName()).log(Level.WARNING, "EL SOCKET RECIBIÓ NULL");
                        Helpers.pausar(1000);
                    }

                } catch (Exception ex) {

                    Logger.getLogger(Participant.class.getName()).log(Level.WARNING, "EXCEPCION AL LEER DEL SOCKET", ex);
                    Helpers.pausar(1000);

                }

            } while (!exit && !WaitingRoom.isExit());

            if (!WaitingRoom.isExit()) {

                if (WaitingRoom.isPartida_empezada() && !exit) {

                    Game.getInstance().getCrupier().remotePlayerQuit(nick);

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
