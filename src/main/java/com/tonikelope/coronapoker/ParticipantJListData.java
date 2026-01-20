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

import javax.swing.ImageIcon;

/**
 *
 * @author Antonio
 */
public class ParticipantJListData {

    private String nick;
    private int latency;
    private int latency2;
    private ImageIcon avatar;

    // Convención: -1 = sin dato
    public static final int NO_LATENCY = -2;

    public ParticipantJListData(String nick) {
        this.nick = nick;
        this.avatar = null;
        this.latency = NO_LATENCY;
        this.latency2 = NO_LATENCY;
    }

    /* =======================
       Getters
       ======================= */
    public String getNick() {
        return nick;
    }

    public int getLatency() {
        return latency;
    }

    public int getLatency2() {
        return latency2;
    }

    public ImageIcon getAvatar() {
        return avatar;
    }

    /* =======================
       Setters
       ======================= */
    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setLatency(int latency) {
        this.latency = latency;
    }

    public void setLatency2(int latency2) {
        this.latency2 = latency2;
    }

    public void setAvatar(ImageIcon avatar) {
        this.avatar = avatar;
    }

    /* =======================
       Utilidad
       ======================= */
    public boolean hasLatency() {
        return latency != NO_LATENCY;
    }

    public boolean hasLatency2() {
        return latency2 != NO_LATENCY;
    }

    @Override
    public String toString() {
        return nick;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ParticipantJListData)) {
            return false;
        }
        ParticipantJListData other = (ParticipantJListData) obj;
        return nick != null && nick.equals(other.nick);
    }

    @Override
    public int hashCode() {
        return nick != null ? nick.hashCode() : 0;
    }
}
