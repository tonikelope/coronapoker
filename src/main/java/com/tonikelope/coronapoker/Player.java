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
import java.net.URL;
import java.util.ArrayList;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 *
 * @author tonikelope
 */
public interface Player {

    public static final int NODEC = -1;
    public static final int FOLD = 1;
    public static final int CHECK = 2;
    public static final int BET = 3;
    public static final int ALLIN = 4;

    public static final int DEALER = 11;
    public static final int SMALL_BLIND = 12;
    public static final int BIG_BLIND = 13;

    public static final int BORDER = 10;

    public static final Color RERAISE_BACK_COLOR = new Color(125, 5, 225);

    public static final Color RERAISE_FORE_COLOR = Color.WHITE;

    public static final ImageIcon IMAGEN_UTG = new ImageIcon(Player.class.getResource("/images/utg.png"));

    public void resetGUI();

    public void ordenarCartas();

    public void destaparCartas(boolean sound);

    public int getResponseTime();

    public boolean isCalentando();

    public boolean isActivo();

    public boolean isTurno();

    public void resetBote();

    public void checkGameOver();

    public void showCards(String jugada);

    public int getBuyin();

    public float getBote();

    public void setTimeout(boolean val);

    public String getNickname();

    public void setNickname(String name);

    public Card getHoleCard1();

    public Card getHoleCard2();

    public ArrayList<Card> getHoleCards();

    public void setWinner(String msg);

    public void setLoser(String msg);

    public void pagar(float pasta, Integer sec_pot);

    public float getBet();

    void disableUTG();

    void refreshPos();

    public void nuevaMano();

    public void esTuTurno();

    public int getDecision();

    public void setStack(float stack);

    public float getStack();

    public void setBet(float bet);

    public void resetBetDecision();

    public boolean isSpectator();

    public boolean isExit();

    public void setExit();

    public String getLastActionString();

    public void setBuyin(int buyin);

    public float getPagar();

    public void setPagar(float pagar);

    public void setSpectator(String msg);

    public void unsetSpectator();

    public void setAvatar();

    public void setSpectatorBB(boolean bb);

    public boolean isTimeout();

    public void setPlayerActionIcon(String icon);

    public void hidePlayerActionIcon();

    public void setNotifyImageChatLabel(URL u);

    public void setNotifyTTSChatLabel();

    public JLabel getChat_notify_label();

    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per);

    public boolean isWinner();

    public boolean isMuestra();

}
