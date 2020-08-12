/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
