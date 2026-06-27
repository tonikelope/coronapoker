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
public interface Player extends com.tonikelope.coronapoker.bot.context.BotPlayerView {

    public static final int NODEC = -1;
    public static final int FOLD = 1;
    public static final int CHECK = 2;
    public static final int BET = 3;
    public static final int ALLIN = 4;

    public static final int DEALER = 11;
    public static final int SMALL_BLIND = 12;
    public static final int BIG_BLIND = 13;
    public static final int DEAD_DEALER = 14;
    public static final int STRADDLE = 15;
    public static final int DEALER_STRADDLE = 16; // dealer que ademas straddlea (3-manos: el dealer es el UTG): ficha combinada mitad blanca (dealer) / mitad roja (straddle)

    public static final int BORDER = 12;
    public static final int ARC = 30;

    public static final Color RERAISE_BACK_COLOR = new Color(125, 5, 225);

    public static final Color RERAISE_FORE_COLOR = Color.WHITE;

    public static final ImageIcon IMAGEN_UTG = new ImageIcon(Player.class.getResource("/images/utg.png"));

    public void setContaWin(int conta);

    public int getContaWin();

    public void resetGUI();

    public void ordenarCartas();

    public void destaparCartas(boolean sound);

    public int getResponseTime();

    public boolean isCalentando();

    public boolean isActivo();

    public void stopActionTimer();

    public boolean isTurno();

    public void resetBote();

    public void checkGameOver();

    public void showCards(String jugada);

    public int getBuyin();

    public double getBote();

    public void setTimeout(boolean val);

    public String getNickname();

    public void setNickname(String name);

    public Card getHoleCard1();

    public Card getHoleCard2();

    public ArrayList<Card> getHoleCards();

    public void setWinner(String msg);

    public void setLoser(String msg);

    // Run-it-twice rewind: re-aplica el render de la última acción y limpia el
    // estado ganador/perdedor de SIDE-A antes de correr SIDE-B.
    public void repaintLastAction();

    public void pagar(double pasta, Integer sec_pot);

    // Run-it-twice: marca (con dedup) que este jugador se lleva el pot 'sec_pot'
    // (la "franja negra"), sin pagar — el dinero se paga aparte con pagar(.,null).
    // Evita el indice duplicado al ganar el mismo pot en los dos boards.
    public void marcarBotePot(int sec_pot);

    public double getBet();

    void disableUTG();

    void setUTG();

    void refreshPos();

    // Refresca el icono de ficha de posición (dealer/ciegas) y su visibilidad.
    public void refreshPositionChipIcons();

    // Etiqueta de la ficha de posición (dealer/ciega) sobre el asiento.
    public JLabel getChip_label();

    // Centro en pantalla donde reposa la ficha de posición de este asiento para
    // una ficha de tamaño (chip_w, chip_h); null si el asiento no está en
    // pantalla. Debe llamarse en el EDT (lee la jerarquía Swing).
    public java.awt.geom.Point2D getPositionChipScreenCenter(int chip_w, int chip_h);

    public void nuevaMano();

    public void esTuTurno();

    public int getDecision();

    public void setStack(double stack);

    // Pinta SOLO el label del stack con 'value' (sin tocar el modelo): lo usa la
    // animacion de llenado de stacks (apertura de timba / recompra) para rodar el
    // contador frame a frame. El stack real lo fija el caller aparte.
    public void setStackDisplay(double value);

    public double getStack();

    public void setBet(double bet);

    // Postea un ante (dinero muerto: del stack al bote, SIN tocar bet) por el
    // importe dado, o todo el stack si no lo cubre (all-in por el ante). Devuelve
    // lo realmente posteado. Solo se invoca cuando GameFrame.ANTE esta activo.
    public double postAnte(double ante);

    // Postea un straddle (ciega VIVA: SI cuenta como apuesta a igualar, via bet)
    // por el importe dado, o todo el stack si no lo cubre (all-in). Devuelve lo
    // realmente posteado. Solo se invoca cuando GameFrame.STRADDLE esta activo.
    public double postStraddle(double amount);

    public void resetBetDecision();

    public boolean isSpectator();

    public boolean isExit();

    public void setExit();

    public String getLastActionString();

    public void setBuyin(int buyin);

    public double getPagar();

    public void setPagar(double pagar);

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
