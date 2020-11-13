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

    public static TablePanel downgradePanel(TablePanel panel) {

        RemotePlayer[] remotos = panel.getRemotePlayers();

        int conta_exit = 0;

        for (RemotePlayer player : remotos) {

            if (player.isExit()) {
                conta_exit++;
            }
        }

        if (conta_exit > 0) {

            TablePanel nuevo_panel = TablePanelFactory.getPanel(panel.getPlayers().length - conta_exit);

            if (nuevo_panel != null) {

                Player[] nuevos_jugadores = nuevo_panel.getPlayers();

                int i = 0;

                for (Player player : panel.getPlayers()) {

                    if (player instanceof LocalPlayer || !((RemotePlayer) player).isExit()) {
                        nuevos_jugadores[i].setNickname(player.getNickname());
                        nuevos_jugadores[i].setStack(player.getStack());
                        nuevos_jugadores[i].setBuyin(player.getBuyin());
                        nuevos_jugadores[i].setPagar(player.getPagar());

                        if (player.isSpectator()) {
                            nuevos_jugadores[i].setSpectator(null);
                        }

                        i++;
                    }
                }

                nuevo_panel.getLocalPlayer().getPlayingCard1().setCompactable(false);
                nuevo_panel.getLocalPlayer().getPlayingCard2().setCompactable(false);
            }

            return nuevo_panel;

        } else {
            return null;
        }
    }

}
