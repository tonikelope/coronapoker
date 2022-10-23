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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author tonikelope
 */
public class ParticipantsListLabel extends JLabel implements ListCellRenderer {

    @Override
    public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        if (value instanceof JLabel) {
            this.setText(((JLabel) value).getText());
            this.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
            this.setIcon(((JLabel) value).getIcon());
            this.setFont(list.getFont());

            if (WaitingRoomFrame.getInstance() != null && WaitingRoomFrame.getInstance().getParticipantes().get(((JLabel) value).getText()) != null) {
                this.setOpaque(true);

                if (WaitingRoomFrame.getInstance().getParticipantes().get(((JLabel) value).getText()).isAsync_wait()) {
                    this.setBackground(Color.DARK_GRAY);
                } else if (WaitingRoomFrame.getInstance().getParticipantes().get(((JLabel) value).getText()).isUnsecure_player()) {
                    this.setBackground(Color.RED);
                }

                this.setForeground(Color.WHITE);

            } else if (isSelected) {

                this.setOpaque(true);
                this.setBackground(Color.YELLOW);

            } else {

                this.setOpaque(false);
                this.setForeground(Color.black);

            }

            this.revalidate();
        }

        return this;
    }

}
