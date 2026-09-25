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
 * List cell renderer for participants: shows nick, latency and avatar, and
 * highlights the row based on selection or the participant's connection state.
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

        // Label text: nick, plus latency in ms when available
        String text = participant.getNick();

        if (participant.hasLatency()) {
            text += " (" + (participant.getLatency() >= 0 ? String.valueOf(participant.getLatency()) : "-") + " ms)";
        }

        setText(text);

        setIcon(participant.getAvatar());

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setFont(list.getFont());

        // Row color: selection wins; otherwise flag async-wait or unsecure participants
        Color background = list.getBackground();
        Color foreground = list.getForeground();
        boolean opaque = false;

        if (isSelected) {
            opaque = true;
            background = Color.YELLOW;
            foreground = Color.BLACK;
        } else {
            if (WaitingRoomFrame.getInstance() != null) {
                Participant state = WaitingRoomFrame.getInstance().getParticipantes().get(participant.getNick());
                if (state != null) {
                    if (state.isAsync_wait()) {
                        opaque = true;
                        background = Color.DARK_GRAY;
                        foreground = Color.WHITE;
                    } else if (state.isUnsecure_player()) {
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
