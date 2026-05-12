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

import java.util.logging.Logger;

/**
 * Lado servidor (host) de la sala de espera. Gestiona el ServerSocket, el accept
 * loop de conexiones entrantes, el alta/baja de Participants y los broadcasts
 * pre-game a todos los clientes conectados.
 *
 * Esta clase se instancia desde WaitingRoomFrame cuando server == true.
 *
 * REFACTOR EN CURSO: clase recién creada como esqueleto. Las responsabilidades
 * de red del host irán migrando aquí desde WaitingRoomFrame en fases sucesivas.
 */
public class NetServer {

    private static final Logger LOGGER = Logger.getLogger(NetServer.class.getName());

    private final WaitingRoomFrame waiting_room;

    public NetServer(WaitingRoomFrame waiting_room) {
        this.waiting_room = waiting_room;
    }

    public WaitingRoomFrame getWaiting_room() {
        return waiting_room;
    }
}
