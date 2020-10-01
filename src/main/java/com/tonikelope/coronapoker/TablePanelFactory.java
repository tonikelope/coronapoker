/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

/**
 *
 * @author tonikelope
 */
public class TablePanelFactory {

    public static TablePanel getPanel(int players) {

        switch (players) {

            case 2:
                return new TablePanel2();

            case 3:
                return new TablePanel3();

            case 4:
                return new TablePanel4();

            case 5:
                return new TablePanel5();

            case 6:
                return new TablePanel6();

            case 7:
                return new TablePanel7();

            case 8:
                return new TablePanel8();

            case 9:
                return new TablePanel9();

            case 10:
                return new TablePanel10();
        }

        return null;

    }

}
