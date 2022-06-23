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
                nuevo_panel.getLocalPlayer().setPause_counter(panel.getLocalPlayer().getPause_counter());
                nuevo_panel.getLocalPlayer().setAuto_pause(panel.getLocalPlayer().isAuto_pause());
                nuevo_panel.getLocalPlayer().setAuto_pause_warning(panel.getLocalPlayer().isAuto_pause_warning());

                if (GameFrame.VISTA_COMPACTA != 2) {
                    for (Card carta : nuevo_panel.getCommunityCards().getCartasComunes()) {
                        carta.setCompactable(false);
                    }
                }

                Helpers.GUIRunAndWait(new Runnable() {
                    @Override
                    public void run() {
                        nuevo_panel.getCommunityCards().getRandom_button().setVisible(panel.getCommunityCards().getRandom_button().isVisible());
                        nuevo_panel.getCommunityCards().getPause_button().setForeground(panel.getCommunityCards().getPause_button().getForeground());
                        nuevo_panel.getCommunityCards().getPause_button().setBackground(panel.getCommunityCards().getPause_button().getBackground());
                        nuevo_panel.getCommunityCards().getTiempo_partida().setVisible(panel.getCommunityCards().getTiempo_partida().isVisible());
                        nuevo_panel.getCommunityCards().getMax_hands_button().setVisible(panel.getCommunityCards().getMax_hands_button().isVisible());
                        nuevo_panel.getCommunityCards().getHand_limit_spinner().setVisible(panel.getCommunityCards().getHand_limit_spinner().isVisible());
                        nuevo_panel.getCommunityCards().getPause_button().setText(panel.getCommunityCards().getPause_button().getText());
                        nuevo_panel.getCommunityCards().getPause_button().setVisible(panel.getCommunityCards().getPause_button().isVisible());
                        nuevo_panel.getCommunityCards().getPot_label().setText(" ");
                        nuevo_panel.getCommunityCards().getHand_label().setText(" ");
                        nuevo_panel.getCommunityCards().getBlinds_label().setText(" ");
                    }
                });
            }

            return nuevo_panel;

        } else {
            return null;
        }
    }

}
