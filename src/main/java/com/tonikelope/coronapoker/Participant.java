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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author tonikelope
 */
public class Participant implements Runnable {

    private final String nick;
    private File avatar;
    private volatile Socket socket = null;
    private final WaitingRoom sala_espera;
    private volatile boolean exit = false;
    private final HashMap<String, Integer> last_received = new HashMap<>();
    private final Integer id;
    private volatile BufferedReader input_stream = null;
    private volatile boolean reconnected = false;
    private final Object keep_alive_lock = new Object();
    private volatile int pong;
    private volatile boolean cpu = false;

    public Participant(String nick, File avatar, Socket socket, WaitingRoom espera, Integer id, boolean cpu) {
        this.nick = nick;
        this.setSocket(socket);
        this.sala_espera = espera;
        this.avatar = avatar;
        this.id = id;
        this.cpu = cpu;
    }

    public boolean isCpu() {
        return cpu;
    }

    public void setAvatar(File avatar) {
        this.avatar = avatar;
    }

    public void setExit() {
        this.exit = true;

        if (this.socket != null) {
            try {

                if (!WaitingRoom.isPartida_empezada()) {
                    this.socket.getOutputStream().write("EXIT\n".getBytes("UTF-8"));
                }
                this.socket.getOutputStream().close();
            } catch (IOException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }

            synchronized (keep_alive_lock) {
                keep_alive_lock.notifyAll();
            }
        }
    }

    public Integer getId() {
        return id;
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

    public synchronized Socket getSocket() {

        return socket;

    }

    private synchronized void setSocket(Socket socket) {

        this.socket = socket;

        if (this.socket != null) {

            try {
                this.input_stream = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            } catch (IOException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public synchronized void resetSocket(Socket socket) {

        if (this.socket != null) {

            try {
                this.socket.close();
            } catch (IOException ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        this.socket = socket;

        try {
            this.input_stream = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        } catch (IOException ex) {
            Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.reconnected = true;
    }

    @Override
    public void run() {

        if (socket != null) {

            //Cada X segundos mandamos un comando KEEP ALIVE al cliente
            Helpers.threadRun(new Runnable() {
                public void run() {

                    while (!WaitingRoom.isExit() && !exit && !WaitingRoom.isPartida_empezada()) {

                        int ping = Helpers.PRNG_GENERATOR.nextInt();

                        try {

                            getSocket().getOutputStream().write(("PING#" + String.valueOf(ping) + "\n").getBytes("UTF-8"));

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

                            Logger.getLogger(WaitingRoom.class.getName()).log(Level.WARNING, nick + " NO respondió al PING");

                        }
                    }
                }
            });

            try {

                String recibido;

                do {
                    recibido = null;

                    try {

                        recibido = this.input_stream.readLine().trim();

                        if (recibido != null) {

                            String[] partes_comando = recibido.split("#");

                            switch (partes_comando[0]) {
                                case "PONG":
                                    pong = Integer.parseInt(partes_comando[1]);
                                    break;
                                case "PING":
                                    this.socket.getOutputStream().write(("PONG#" + String.valueOf(Integer.parseInt(partes_comando[1]) + 1) + "\n").getBytes("UTF-8"));
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
                                    
                                    synchronized (WaitingRoom.getInstance().getReceived_confirmations()) {
                                        WaitingRoom.getInstance().getReceived_confirmations().add(new Object[]{nick, Integer.parseInt(partes_comando[1])});
                                        WaitingRoom.getInstance().getReceived_confirmations().notifyAll();
                                    }
                                    break;
                                case "GAME":
                                    //Es un comando de juego del cliente
                                    String subcomando = partes_comando[2];
                                    int command_id = Integer.valueOf(partes_comando[1]); //Los comandos del juego llevan confirmación de recepción
                                    this.socket.getOutputStream().write(("CONF#" + String.valueOf(command_id + 1) + "#OK\n").getBytes("UTF-8"));
                                    if (!last_received.containsKey(subcomando) || last_received.get(subcomando) != command_id) {

                                        last_received.put(subcomando, command_id);

                                        switch (subcomando) {
                                            case "PING":
                                                //ES UN PING DE JUEGO -> NO tenemos que hacer nada más
                                                break;
                                            case "CINEMATICEND":
                                                Game.getInstance().getCrupier().remoteCinematicEnd(nick);
                                                break;
                                            case "SHOWMYCARDS":
                                                Game.getInstance().getCrupier().showAndBroadcastPlayerCards(nick);
                                                break;
                                            case "EXIT":
                                                Game.getInstance().getCrupier().playerExit(nick);
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
                        }

                    } catch (Exception ex) {
                        recibido = null;
                    }

                    if (recibido == null && !exit && !WaitingRoom.isExit()) {

                        boolean timeout = false;

                        //El cliente ha perdido la conexión. Esperamos que consiga reconectar
                        long start = System.currentTimeMillis();

                        do {

                            while (!reconnected) {
                                synchronized (sala_espera.getSocket_reconnect_lock()) {

                                    sala_espera.getSocket_reconnect_lock().wait(Game.WAIT_QUEUES);
                                }
                            }

                            if (System.currentTimeMillis() - start > Game.CLIENT_RECON_TIMEOUT) {
                                int input = Helpers.mostrarMensajeErrorSINO(Game.getInstance(), nick + Translator.translate(" parece que perdió la conexión y no ha vuelto a conectar (se le eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?"));

                                // 0=yes, 1=no, 2=cancel
                                if (input == 1) {

                                    timeout = true;

                                } else {
                                    start = System.currentTimeMillis();
                                }
                            }

                        } while (!reconnected && !timeout);

                        if (reconnected) {
                            recibido = "";
                            reconnected = false;
                        }
                    }

                } while (recibido != null && !exit && !WaitingRoom.isExit());

            } catch (Exception ex) {
                Logger.getLogger(Participant.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (!WaitingRoom.isExit()) {

                if (WaitingRoom.isPartida_empezada() && !exit) {

                    Game.getInstance().getCrupier().playerExit(nick);
                }

                sala_espera.borrarParticipante(nick);
            }

            exit = true;

            synchronized (keep_alive_lock) {
                keep_alive_lock.notifyAll();
            }

        }

    }

}
