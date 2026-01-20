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
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author tonikelope
 */
public class ParticipantsListLabel extends JLabel implements ListCellRenderer<ParticipantJListData> {

    @Override
    public Component getListCellRendererComponent(
            JList<? extends ParticipantJListData> list,
            ParticipantJListData participant,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        if (participant == null) {
            setText("");
            setIcon(null);
            return this;
        }

        // Texto con nick y latencias
        String text = participant.getNick();

        if (participant.hasLatency()) {
            text += " (" + (participant.getLatency() >= 0 ? String.valueOf(participant.getLatency()) : "-") + " ms)";
        }

        setText(text);

        // Icono
        setIcon(participant.getAvatar());

        // Margen y fuente
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setFont(list.getFont());

        // Colores según estado
        Color background = list.getBackground();
        Color foreground = list.getForeground();
        boolean opaque = false;

        if (isSelected) {
            opaque = true;
            background = Color.YELLOW;
            foreground = Color.BLACK;
        } else {
            // Consulta tu mapa de participantes
            if (WaitingRoomFrame.getInstance() != null) {
                Object stateObj = WaitingRoomFrame.getInstance().getParticipantes().get(participant.getNick());
                if (stateObj != null) {
                    // Aquí suponemos que tu objeto tiene los métodos isAsync_wait() / isUnsecure_player()
                    boolean asyncWait = false;
                    boolean unsecure = false;

                    try {
                        asyncWait = (boolean) stateObj.getClass().getMethod("isAsync_wait").invoke(stateObj);
                        unsecure = (boolean) stateObj.getClass().getMethod("isUnsecure_player").invoke(stateObj);
                    } catch (Exception e) {
                        // ignora errores de reflexión
                    }

                    if (asyncWait) {
                        opaque = true;
                        background = Color.DARK_GRAY;
                        foreground = Color.WHITE;
                    } else if (unsecure && Boolean.parseBoolean(Helpers.PROPERTIES.getProperty("binary_check", "true"))) {
                        opaque = true;
                        background = Color.RED;
                        foreground = Color.WHITE;
                    }
                }
            }
        }

        setOpaque(opaque);
        setBackground(background);
        setForeground(foreground);

        return this;
    }
}
