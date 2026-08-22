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
 * A poker player at the table — local or remote — covering hand state,
 * stack/bet, actions and the GUI hooks the table drives to render them.
 *
 * @author tonikelope
 */
public interface Player extends com.tonikelope.coronapoker.bot.context.BotPlayerView {

    /**
     * Formats the secondary-pot indexes without taking a separate size
     * snapshot. The player implementations store them in a weakly-consistent
     * concurrent queue which may be cleared while the EDT renders a payout;
     * allocating an array from {@code size()} and then iterating can therefore
     * overflow that array.
     */
    static String formatSecondaryPotIndexes(Iterable<Integer> secondaryPots) {
        StringBuilder text = new StringBuilder();
        for (Integer pot : secondaryPots) {
            if (text.length() > 0) {
                text.append('+');
            }
            text.append('#').append(pot);
        }
        return text.toString();
    }

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
    public static final int DEALER_STRADDLE = 16; // dealer who is also straddling (3-handed: dealer is UTG); combined chip, half white (dealer) / half red (straddle)

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

    /**
     * Records the cards that make up this LOSER's hand at showdown (no kickers,
     * same as {@code jugada.getWinners()} for a winner), so hovering the hand
     * label can highlight them (RESALTAR_JUGADA_SHOWDOWN). {@code null} means
     * no highlight (hidden hand or not applicable); cleared between hands.
     *
     * @param cartas the losing hand's cards, or {@code null}
     */
    public void setShowdownHand(java.util.List<Card> cartas);

    /**
     * Run-it-twice rewind: re-applies the last action's render and clears
     * SIDE-A's winner/loser state before SIDE-B runs.
     */
    public void repaintLastAction();

    public void pagar(double pasta, Integer sec_pot);

    /**
     * Run-it-twice: flags (deduplicated) that this player wins side pot
     * {@code sec_pot} (the "black stripe"), without paying it out — the money
     * is paid separately via {@link #pagar(double, Integer)}. Avoids a
     * duplicate index when the same pot is won on both boards.
     *
     * @param sec_pot side pot index
     */
    public void marcarBotePot(int sec_pot);

    public double getBet();

    void disableUTG();

    void setUTG();

    void refreshPos();

    /**
     * Refreshes the position chip icon (dealer/blinds) and its visibility.
     */
    public void refreshPositionChipIcons();

    /**
     * @return the position chip label (dealer/blind) shown over the seat
     */
    public JLabel getChip_label();

    /**
     * Screen-space center where this seat's position chip rests, for a chip of
     * size ({@code chip_w}, {@code chip_h}). Must be called on the EDT (reads
     * the Swing hierarchy).
     *
     * @param chip_w chip width in pixels
     * @param chip_h chip height in pixels
     * @return the screen center point, or {@code null} if the seat isn't
     * showing
     */
    public java.awt.geom.Point2D getPositionChipScreenCenter(int chip_w, int chip_h);

    public void nuevaMano();

    public void esTuTurno();

    public int getDecision();

    /**
     * Recovery: silently marks this seat as FOLDED (decision=FOLD, painted
     * gray, no sound or animation). Used by the mutual-timeout skip so a player
     * who left mid-hand and reconnects shows as folded for that hand, instead
     * of looking like they never acted.
     */
    public void markFoldedOnRecover();

    public void setStack(double stack);

    /**
     * Paints only the stack label with {@code value}, without touching the
     * model. Used by the stack fill-in animation (table opening / rebuy) to
     * roll the counter frame by frame; the caller sets the real stack
     * separately.
     *
     * @param value the value to display
     */
    public void setStackDisplay(double value);

    /**
     * Marks that the live roll animation for stack and bet must NOT fire from
     * setStack/setBet (labels stay at their previous value). Set by the action
     * handler right before a chip flies, so the counters don't get ahead of it.
     *
     * @param deferred {@code true} to suppress rolling until
     * {@link #rollCountersToModel()}
     */
    public void setCounterRollDeferred(boolean deferred);

    /**
     * Rolls the stack and bet counters to their current model value (in step
     * with the pot bump/flash on chip landing). Clears the deferred flag.
     */
    public void rollCountersToModel();

    public double getStack();

    public void setBet(double bet);

    /**
     * Posts an ante (dead money: stack to pot, without touching bet) for the
     * given amount, or the whole stack if it doesn't cover it (all-in on the
     * ante). Only called when {@code GameFrame.ANTE} is on.
     *
     * @param ante amount to post
     * @return the amount actually posted
     */
    public double postAnte(double ante);

    /**
     * Posts a straddle (a LIVE blind: it DOES count as a bet to call, via bet)
     * for the given amount, or the whole stack if it doesn't cover it (all-in).
     * Only called when {@code GameFrame.STRADDLE} is on.
     *
     * @param amount amount to post
     * @return the amount actually posted
     */
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

    /**
     * Shows the shuffle GIF overlay and white highlight border while this
     * player's step of the SRA shuffle cascade is running. Kept in sync across
     * peers via the SHUFFLE_TURN command; GameFrame's controller
     * ({@code onShuffleTurn}) invokes it on whichever player (local or remote)
     * has the turn. May block loading the GIF — do not call from the EDT.
     */
    public void showShuffleCascadeOverlay();

    public void hideShuffleCascadeOverlay();

}
