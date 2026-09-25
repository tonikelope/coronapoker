/** *************************************************************************
 * Copyright (c) 2000:
 * University of Alberta,
 * Deptartment of Computing Science
 * Computer Poker Research Group
 *
 * See "Liscence.txt"
 ************************************************************************** */
package org.alberta.poker;

import org.alberta.poker.util.Reporter;

public class GameRecord {

    private int id;
    private int numPlayers;
    private int button;
    private int pot;
    private Hand board;

    private String[] names;
    private String[] actions;
    private Hand[] cards;
    private double[] values;

    public GameRecord(GameInfo gi) {
        id = gi.getID();
        numPlayers = gi.getNumPlayers();
        button = gi.getButton();
        board = gi.getBoard();
        pot = gi.getPot();

        names = new String[numPlayers];
        actions = new String[numPlayers];
        cards = new Hand[numPlayers];
        values = new double[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            names[i] = gi.getPlayerName(i);
            actions[i] = gi.getPlayerInfo(i).getActions();
            values[i] = gi.getPlayerInfo(i).getNetGain();
            cards[i] = gi.getPlayerInfo(i).getRevealedHand();
        }
    }

    public String toString() {
        String s1 = "-----------------------------------------------\n";
        s1 += "#" + id + " [" + board + " ]\n";
        for (int i = 0; i < numPlayers; i++) {
            int j = (i + button + 1) % numPlayers;
            s1 += Reporter.pad(names[j], 12) + " / " + Reporter.pad((values[j] >= 0 ? " " : "")
                    + ((cards[j] == null) ? "      " : cards[j].toString()) + " / "
                    + Reporter.round(values[j], 1), 6) + " / " + format(actions[j]) + "\n";
        }
        //	s1 += "-----------------------------------------------\n";
        return s1;
    }

    //Kh8h/Qc9d/9s6d3cKd6c/sBrC/bRc/kBrC/bC.
    public String toString2() {
        String s1 = id + "/" + boardString() + "\n";
        for (int i = 0; i < numPlayers; i++) {
            int j = (i + button + 1) % numPlayers;
            s1 += Reporter.pad(names[j], 12) + " / " + Reporter.pad((values[j] >= 0 ? " " : "")
                    + ((cards[j] == null) ? "      " : cards[j].toString()) + " / "
                    + Reporter.round(values[j], 1), 6) + " / " + format(actions[j]) + "\n";
        }
        return s1;
    }

    public String boardString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 1; i <= 5; i++) {
            if (i > board.size()) {
                sb.append("??");
            } else {
                sb.append(board.getCard(i).toString());
            }
        }
        return sb.toString();
    }

    private String format(String s) {
        String n = " ";
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '/') {
                n += "\t/ ";
            } else {
                n += s.charAt(i);
            }
        }
        return n;
    }

    public int getPlayerIndex(String name) {
        for (int i = 0; i < numPlayers; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public int getAction(int pi, int n) {
        int i = 0, j = 0;
        char c = '?';
        while (i < n) {
            c = actions[pi].charAt(j);
            if (c != '/') {
                i++;
            }
            j++;
        }
        switch (c) {
            case 'f':
                return Holdem.FOLD;
            case 'k':
                return Holdem.CHECK;
            case 'c':
                return Holdem.CALL;
            case 'b':
                return Holdem.BET;
            case 'r':
                return Holdem.RAISE;
        }
        return -1;
    }

    public GameInfo getContexts() {
        GameInfo gi = new GameInfo();
        gi.LOG_GAME = false;
        for (int i = 0; i < numPlayers; i++) {
            gi.addPlayer(names[i], null);
            gi.getPlayerInfo(names[i]);
            if (cards[i] != null) {
                gi.revealHand(names[i], cards[i].getCard(1), cards[i].getCard(2));
            }
        }
        gi.startNewGame(id);
        gi.advanceCurrentPlayer();
        gi.smallBlind();
        gi.advanceCurrentPlayer();
        gi.bigBlind();
        gi.advanceCurrentPlayer();

        int nthAct = 0;

        while (gi.getNumToAct() > 1 && gi.getStage() < Holdem.SHOWDOWN) {
            int toAct = gi.getNumToAct();
            while (gi.getNumToAct() > 0) {
                int i = gi.getCurrentPlayerPosition();
                int a = getAction(i, nthAct);
                gi.doAction(a);
                toAct--;
                if (toAct == 0) {
                    nthAct++;
                    toAct = gi.getNumToAct();
                }
                gi.advanceCurrentPlayer();
            }
            if (gi.getNumToAct() > 1) {
                switch (gi.getStage()) {
                    case Holdem.PREFLOP:
                        gi.flop(board.getCard(1), board.getCard(2), board.getCard(3));
                        break;
                    case Holdem.FLOP:
                        gi.turn(board.getCard(4));
                        break;
                    case Holdem.TURN:
                        gi.river(board.getCard(5));
                        break;
                    case Holdem.RIVER:
                        gi.gameOver();
                        break;
                }
            }
        }
        return null;
    }

}
