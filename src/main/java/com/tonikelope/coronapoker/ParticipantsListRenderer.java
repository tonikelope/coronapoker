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
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author tonikelope
 */
public class ParticipantsListRenderer extends JLabel implements ListCellRenderer {

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

            if (isSelected) {
                this.setOpaque(true);
                this.setBackground(Color.ORANGE);
            } else {
                this.setOpaque(false);
            }

            this.revalidate();
        }

        return this;
    }

}
