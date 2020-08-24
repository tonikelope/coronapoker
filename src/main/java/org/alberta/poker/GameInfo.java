/** *************************************************************************
 * Copyright (c) 2000:
 * University of Alberta,
 * Deptartment of Computing Science
 * Computer Poker Research Group
 *
 * See "Liscence.txt"
 ************************************************************************** */
package org.alberta.poker;

import java.io.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Vector;
import org.alberta.poker.util.Reporter;

/**
 * Stores all of the info defining a single game of poker. This includes
 * information of the game's players and the cards which are/were dealt. This
 * class can be used to both archive a game's history and its state in progress.
 *
 * This class is a little messy, as it's been incrementally modified over two
 * years.
 *
 * @author Aaron Davidson <davidson@cs.ualberta.ca>
 * @version 1.0.2
 */
public class GameInfo {

    // constants for game update messages
    public final static int U_STAGE = 0;
    public final static int U_FOLD = 1;
    public final static int U_CHECK = 2;
    public final static int U_CALL = 3;
    public final static int U_BET = 4;
    public final static int U_RAISE = 5;
    public final static int U_SHOWDOWN = 6;
    public final static int U_SBLIND = 7;
    public final static int U_BBLIND = 8;
    public final static int U_GAME_OVER = 9;
    public final static int U_ALL_IN = 10;
    public final static int U_REVEAL_HAND = 11;

    // default bet sizes
    private int low_bet = 10;
    private int high_bet = 20;

    public String LOG_DIR = "logs/";
    public String LOG_FILE = "GAMES.LOG";
    public String LOG_DUMP = "DUMP.LOG";
    public boolean LOG_GAME = true;

    // player lists
    private Vector pOrder = new Vector(Holdem.MAX_PLAYERS);
    private Hashtable pInfo = new Hashtable(Holdem.MAX_PLAYERS);

    // the board cards
    private Card[] board = new Card[5];

    // state info
    private int curPlayer = 0;
    private int numPlayers = 0;
    private int activePlayers = 0;
    private int toAct;
    private int button = -1;
    private String winners;
    private int bet_amount = 0;
    private int num_raises = 1;
    private int gameID = 0;
    private int stage = Holdem.PREFLOP;
    private int pot = 0;

    // hand evaluation
    private HandEvaluator he = new HandEvaluator();
    private int[][] hand_ranks;

    private Reporter r = new Reporter();

    /**
     * Default Constructor
     */
    public GameInfo() {
    }

    // set the gameID to a unique ID, and edit gameID file
    private void newGameID() {
        gameID = -1;
        try {
            RandomAccessFile f = new RandomAccessFile("data/GameID.dat", "rw");
            gameID = f.readInt();
            f.seek(0);
            f.writeInt(gameID + 1);
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Reporter getReporter() {
        return r;
    }

    public void setLogDir(String s) {
        LOG_DIR = s;
        r.setLogFile(LOG_DIR + LOG_DUMP);
    }

    /**
     * Test if a player is in this game or not.
     *
     * @param name the name of the player in question
     * @return true if the player is in the game, false otherwise
     */
    public boolean inGame(String name) {
        return (getPlayerPosition(name) != -1);
    }

    /**
     * Before a new game is played, this should always be called. Initializes
     * the game state data.
     */
    public void startNewGame() {
        newGameID();
        startNewGame(gameID);
    }

    /**
     * Before a new game is played, this should always be called. Initializes
     * the game state data.
     */
    public void startNewGame(int id) {
        button = nextPlayer(button);
        curPlayer = button;
        activePlayers = numPlayers;
        toAct = activePlayers;
        bet_amount = 0;
        pot = 0;
        gameID = id;
        stage = Holdem.PREFLOP;
        num_raises = 1;
        int i;
        for (i = 0; i < numPlayers; i++) {
            getPlayerInfo(i).startNewGame();
        }
        for (i = 0; i < 5; i++) {
            board[i] = null;
        }
        winners = null;
        hand_ranks = null;
    }

    /**
     * Adds a new player to the game.
     *
     * @param name the name of the new player
     * @param file the file name of the player's persistent info
     * @return true if added, false if not added
     */
    public boolean addPlayer(String name, String file) {
        if (numPlayers == Holdem.MAX_PLAYERS || pInfo.contains(name)) {
            return false;
        }
        PlayerInfo pi;
        if (LOG_GAME) {
            pi = new PlayerInfo(name, file, this);
        } else {
            pi = new PlayerInfo(name, this);
        }
        pInfo.put(name, pi);
        pOrder.addElement(name);
        numPlayers++;
        return true;
    }

    /**
     * Insert a new player into the game.
     *
     * @param name the name of the new player
     * @param file the file name of the player's persistent info
     * @param the position to insert at (0..numPlayers)
     * @return true if added, false if not added
     */
    public boolean insertPlayer(String name, String file, int pos) {
        if (numPlayers == Holdem.MAX_PLAYERS || pInfo.contains(name)) {
            return false;
        }
        pInfo.put(name, new PlayerInfo(name, file, this));
        pOrder.insertElementAt(name, pos);
        numPlayers++;
        return true;
    }

    /**
     * Remove a player from the game.
     *
     * @param name the name of the player to remove
     * @return true if removed, false if not.
     */
    public boolean removePlayer(String name) {
        if (numPlayers == 0 || !pInfo.containsKey(name)) {
            return false;
        }
        pOrder.removeElement(name);
        pInfo.remove(name);
        numPlayers--;
        return true;
    }

    /**
     * Remove all the players from the game
     */
    public void removeAllPlayers() {
        pOrder.removeAllElements();
        pInfo.clear();
        numPlayers = 0;
    }

    /**
     * @return the button position
     */
    public int getButton() {
        return button;
    }

    /**
     * Set the Button.
     *
     * @param i position to become button
     */
    public void setButton(int i) {
        button = i;
    }

    /**
     * Set the bet multiplier
     *
     * @param bm the bet multiplier
     */
    public void setBetMultiplier(int bm) {
        low_bet = bm * 2;
        high_bet = low_bet * 2;
    }

    public int getLowBet() {
        return low_bet;
    }

    public int getHighBet() {
        return high_bet;
    }

    /**
     * @return the current stage {PREFLOP,FLOP,TURN,RIVER}
     */
    public int getStage() {
        return stage;
    }

    /**
     * @return the pot size
     */
    public int getPot() {
        return pot;
    }

    /**
     * @return the game ID
     */
    public int getID() {
        return gameID;
    }

    /**
     * @return the number of players in the game
     */
    public int getNumPlayers() {
        return numPlayers;
    }

    /**
     * @return the number of players still in the game (not folded)
     */
    public int getNumActivePlayers() {
        return activePlayers;
    }

    /**
     * @return the number of raises made during this stage (including Big Blind)
     */
    public int getNumRaises() {
        return num_raises;
    }

    /**
     * Obtain the nth card on the table
     *
     * @param i the card desired {0..4}
     * @return the card at position i
     */
    public Card getBoardCard(int i) {
        return board[i];
    }

    /**
     * obtain a Hand containing the board cards.
     *
     * @return the board cards.
     */
    public Hand getBoard() {
        Hand h = new Hand();
        if (board != null) {
            if (stage >= Holdem.FLOP) {
                h.addCard(board[0]);
                h.addCard(board[1]);
                h.addCard(board[2]);
            }
            if (stage >= Holdem.TURN) {
                h.addCard(board[3]);
            }
            if (stage >= Holdem.RIVER) {
                h.addCard(board[4]);
            }
        }
        return h;
    }

    /**
     * From a name, find out a player's position
     *
     * @param name the player's name
     * @return the player's position
     */
    public int getPlayerPosition(String name) {
        return pOrder.indexOf(name);
    }

    /**
     * Get the player's name from a position
     *
     * @param p the position
     * @return the name
     */
    public String getPlayerName(int p) {
        return (String) pOrder.elementAt(p);
    }

    /**
     * @return the name of the current player
     */
    public String getCurrentPlayerName() {
        return (String) pOrder.elementAt(curPlayer);
    }

    public PlayerInfo getPlayerInfo(int i) {
        if (i >= 0 && i < numPlayers) {
            return (PlayerInfo) pInfo.get((String) pOrder.elementAt(i));
        } else {
            return null;
        }
    }

    /**
     * Given a name, obtain the PlayerInfo object for that player
     *
     * @param the player's name
     * @return the Player's Information
     */
    public PlayerInfo getPlayerInfo(String s) {
        return (PlayerInfo) pInfo.get(s);
    }

    /**
     * @return the PlayerInfo for the current player
     */
    public PlayerInfo getCurrentPlayerInfo() {
        return getPlayerInfo(curPlayer);
    }

    /**
     * @return the position of the current player
     */
    public int getCurrentPlayerPosition() {
        return curPlayer;
    }

    /**
     * Set the current player position
     *
     * @param cp the position to make current.
     */
    public void setCurrentPlayerPosition(int cp) {
        curPlayer = cp;
    }

    /**
     * Advance the current player to the next active player in the game.
     *
     * @return returns the position of the new current player
     */
    public int advanceCurrentPlayer() {
        curPlayer = nextActivePlayer(curPlayer);
        return curPlayer;
    }

    /**
     * Get the position of the next player after the given position
     *
     * @param pos the specified position
     * @return the next player position after the specified position
     */
    public int nextPlayer(int pos) {
        pos++;
        if (pos > numPlayers - 1) {
            pos = 0;
        }
        return pos;
    }

    /**
     * Given a position, returns the position of the next active player
     *
     * @param pos specified position
     * @return the next active position after the specified position
     */
    public int nextActivePlayer(int pos) {
        pos = nextPlayer(pos);
        while (getPlayerInfo(pos).getLastAction() == Holdem.FOLD) {
            pos = nextPlayer(pos);
        }
        return pos;
    }

    /**
     * Test if a player at a specific position is active in the game.
     *
     * @param pos the position to check.
     * @return true if the player is active, false if not
     */
    public boolean activePlayer(int pos) {
        return (getPlayerInfo(pos).getLastAction() != Holdem.FOLD);
    }

    public void doAction(int a) {
        switch (a) {
            case Holdem.FOLD:
                fold();
                break;

            case Holdem.CALL:
                call();
                break;

            case Holdem.RAISE:
                raise();
                break;
        }
    }

    /**
     * Fold the current player
     *
     * @return true if game is over.
     */
    public boolean fold() {
        getCurrentPlayerInfo().fold();
        activePlayers--;
        toAct--;
        return (activePlayers == 1);
    }

    /**
     * Fold a player (when it is not their turn)
     *
     * @param name the name of the player to fold
     * @return true if game is over.
     */
    public boolean fold(String name) {
        if (!inGame(name)) {
            return false;
        }
        if (getPlayerInfo(name).getLastAction() == Holdem.FOLD) {
            return false;
        }
        getPlayerInfo(name).fold();
        activePlayers--;
        toAct--;
        return (activePlayers == 1);
    }

    /**
     * Small Blind the current player.
     */
    public void smallBlind() {
        int amount = low_bet / 2;
        bet_amount += amount;
        pot += getCurrentPlayerInfo().pay(bet_amount);
        getCurrentPlayerInfo().smallBlind();
    }

    /**
     * Big Blind the current player
     */
    public void bigBlind() {
        int amount = low_bet;
        bet_amount = amount;
        pot += getCurrentPlayerInfo().pay(bet_amount);
        getCurrentPlayerInfo().bigBlind();
    }

    /**
     * Call the current player
     *
     * @return the amount called
     */
    public int call() {
        int am = getCurrentPlayerInfo().call(bet_amount);
        pot += am;
        toAct--;
        return am;
    }

    /**
     * Raise the current player
     *
     * @return true if player bet, false if he raised
     */
    public boolean raise() {
        bet_amount += getBetSize();
        if (num_raises == 0) {
            pot += getCurrentPlayerInfo().bet(bet_amount);
        } else {
            pot += getCurrentPlayerInfo().raise(bet_amount);
        }
        num_raises++;
        toAct = activePlayers - 1;
        return (num_raises == 1);
    }

    /**
     * Proceed to stage FLOP
     *
     * @param c1 table card 1
     * @param c2 table card 2
     * @param c3 table card 3
     */
    public void flop(Card c1, Card c2, Card c3) {
        stage = Holdem.FLOP;
        board[0] = c1;
        board[1] = c2;
        board[2] = c3;
        int j = 0, i;
        for (i = nextActivePlayer(-1); j < getNumActivePlayers(); i = nextActivePlayer(i), j++) {
            getPlayerInfo(i).advanceStage();
        }
        curPlayer = nextActivePlayer(button);
        num_raises = 0;
        toAct = activePlayers;
        hand_ranks = he.getRanks(getBoard());
    }

    /**
     * Proceed to stage TURN
     *
     * @param c table card 4
     */
    public void turn(Card c) {
        stage = Holdem.TURN;
        board[3] = c;
        int j = 0, i;
        for (i = nextActivePlayer(-1); j < getNumActivePlayers(); i = nextActivePlayer(i), j++) {
            getPlayerInfo(i).advanceStage();
        }
        curPlayer = nextActivePlayer(button);
        num_raises = 0;
        toAct = activePlayers;
        hand_ranks = he.getRanks(getBoard());
    }

    /**
     * Proceed to stage RIVER
     *
     * @param c table card 4
     */
    public void river(Card c) {
        stage = Holdem.RIVER;
        board[4] = c;
        int j = 0, i;
        for (i = nextActivePlayer(-1); j < getNumActivePlayers(); i = nextActivePlayer(i), j++) {
            getPlayerInfo(i).advanceStage();
        }
        curPlayer = nextActivePlayer(button);
        num_raises = 0;
        toAct = activePlayers;
        hand_ranks = he.getRanks(getBoard());
    }

    /**
     * Signal All-In condition after the flop
     *
     * @param c1 the turn card
     * @param c2 the river card
     */
    public void allIn(Card c1, Card c2) {
        stage = Holdem.RIVER;
        board[3] = c1;
        board[4] = c2;
        int j = 0, i;
        for (i = nextActivePlayer(-1); j < getNumActivePlayers(); i = nextActivePlayer(i), j++) {
            getPlayerInfo(i).advanceStage();
            getPlayerInfo(i).advanceStage();
        }
        curPlayer = nextActivePlayer(button);
        num_raises = 0;
        toAct--;
        hand_ranks = he.getRanks(getBoard());
    }

    /**
     * Signal All-In condition before the flop
     *
     * @param c1 the first flop card
     * @param c2 the second flop card
     * @param c3 the third flop card
     * @param c4 the turn card
     * @param c5 the river card
     */
    public void allIn(Card c1, Card c2, Card c3, Card c4, Card c5) {
        stage = Holdem.RIVER;
        board[0] = c1;
        board[1] = c2;
        board[2] = c3;
        board[3] = c4;
        board[4] = c5;
        int j = 0, i;
        for (i = nextActivePlayer(-1); j < getNumActivePlayers(); i = nextActivePlayer(i), j++) {
            getPlayerInfo(i).advanceStage();
            getPlayerInfo(i).advanceStage();
            getPlayerInfo(i).advanceStage();
        }
        curPlayer = nextActivePlayer(button);
        num_raises = 0;
        toAct--;
        hand_ranks = he.getRanks(getBoard());
    }

    /**
     * Report the end of the game and log player and game history. Should only
     * be called once at the end of a game
     */
    public void gameOver() {
        if (getNumActivePlayers() > 1) {
            stage = Holdem.SHOWDOWN;
        }
    }

    // stores game history as:
    // gameID/pot/cards/player-list-starting-with-button/winner-list
    public void logGame() {
        if (!LOG_GAME) {
            return;
        }

        int i;
        getPlayerInfo(button).save();
        for (i = nextPlayer(button); i != button; i = nextPlayer(i)) {
            getPlayerInfo(i).save();
        }
        GameRecord gr = new GameRecord(this);

        Calendar cal = new GregorianCalendar();
        int date = cal.get(Calendar.DATE);
        int month = cal.get(Calendar.MONTH) + 1;
        int year = cal.get(Calendar.YEAR);
        LOG_FILE = month + "." + year + ".GAMES.LOG";
        LOG_DUMP = date + "." + month + "." + year + ".DUMP.LOG";
        r.setLogFile(LOG_DIR + LOG_DUMP);

        Reporter.log(gr.toString(), LOG_DIR + LOG_FILE);
        System.out.println(gr.toString());
    }

    /**
     * Reveal a players hand in a showdown.
     *
     * @param name the name of the player
     * @param c1 the first card
     * @param c2 the second card
     */
    public void revealHand(String name, Card c1, Card c2) {
        revealHand(getPlayerPosition(name), c1, c2);
    }

    /**
     * Reveal a players hand in a showdown.
     *
     * @param n the position of the player
     * @param c1 the first card
     * @param c2 the second card
     */
    public void revealHand(int n, Card c1, Card c2) {
        PlayerInfo pi = getPlayerInfo(n);
        if (pi == null) {
            return;
        }
        pi.revealHand(c1, c2);
    }

    /**
     * Add a player to the list of winners
     *
     * @param name the name of the player.
     */
    public void addWinner(String name) {
        if (winners == null) {
            winners = new String(name);
        } else {
            winners += "," + name;
        }
    }

    /**
     * @return A string containing the names of the game's winners.
     */
    public String getWinners() {
        return winners;
    }

    /**
     * Obtain the number of opponents who have not yet acted in this betting
     * round.
     *
     * @return the number of unacted players
     */
    public int getUnacted() {
        int u = 0;
        String s;
        for (int i = 0; i < numPlayers; i++) {
            s = getPlayerInfo(i).getActions();
            if (s.length() == 0 || s.charAt(s.length() - 1) == '/') {
                u++;
            }
        }
        return u;
    }

    /**
     * Determine the position of the small blind
     *
     * @return the small blind position
     */
    public int getSmallBlindPosition() {
        return nextPlayer(button);
    }

    /**
     * Get the amount a player must call to stay in
     *
     * @param pos the position of the player
     * @return the amount to call
     */
    public int getAmountToCall(int pos) {
        return getPlayerInfo(pos).getAmountToCall(bet_amount);
    }

    /**
     * Get the number of bets a player must call to stay in
     *
     * @param pos the position of the player
     * @return the number of bets to call
     */
    public double getBetsToCall(int pos) {
        return (double) (getPlayerInfo(pos).getAmountToCall(bet_amount) / (double) getBetSize());
    }

    /**
     * Test if a player has committed in the current betting round.
     *
     * @param pos the position of the player
     * @return true if the player has voluntarily committed in the round, false
     * otherwise.
     */
    public boolean playerCommitted(int pos) {
        return getPlayerInfo(pos).isCommitted();
    }

    /**
     * Obtain the total amount players must have in the pot to stay in
     *
     * @return the total bet amount
     */
    public int getBetAmount() {
        return bet_amount;
    }

    /**
     * Get the current size of the bet.
     *
     * @return the size of the bet for the current stage.
     */
    public int getBetSize() {
        if (stage < Holdem.TURN) {
            return low_bet;
        }
        return high_bet;
    }

    /**
     * Check if a player position is valid in this game
     *
     * @param pos the position to test.
     * @return true if the position given is valid, false otherwise
     */
    public boolean validPlayer(int pos) {
        return (pos >= 0 && pos < numPlayers);
    }

    /**
     * Returns a 52x52 array of hand rank values for every hand against the
     * current board;
     */
    public int[][] getHandRanks() {
        return hand_ranks;
    }

    /**
     * Look up a the rank of a hand against the current board
     */
    public int getHandRank(Card c1, Card c2) {
        return hand_ranks[c1.getIndex()][c2.getIndex()];
    }

    public int getNumToAct() {
        return toAct;
    }

}
