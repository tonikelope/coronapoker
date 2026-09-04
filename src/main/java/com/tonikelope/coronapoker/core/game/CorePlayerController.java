/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.bot.context.DealerView;
import java.util.List;
import java.util.Objects;

/** Headless player controller shared by the canonical engine and GDX. */
public final class CorePlayerController implements GamePlayerController {

    private static final String ALBERTA_SUITS = "TDCP";

    private final PlayerState state;
    private final CoreCardController firstCard;
    private final CoreCardController secondCard;
    private final boolean local;
    private volatile Bot bot;
    private final Object revealLock = new Object();
    private volatile DealerView dealer;
    private volatile boolean spectatorBigBlind;
    private volatile boolean loser;
    private volatile boolean chipForcedHidden;
    private volatile boolean underTheGun;
    private volatile int winCount;
    private volatile int rabbitCount;
    private volatile int parguelaCount;
    private volatile Runnable turnCompletionSignal = () -> { };
    private volatile Runnable potRegistration = () -> { };

    private CorePlayerController(String nickname, boolean local, boolean automated) {
        this.local = local;
        this.state = local ? new LocalPlayerState(nickname)
                : new RemotePlayerState(nickname);
        this.firstCard = new CoreCardController();
        this.secondCard = new CoreCardController();
        this.state.bindHoleCards(firstCard.getState(), secondCard.getState());
        this.state.setActive(true);
        if (state instanceof RemotePlayerState remote) remote.setBot(automated);
        this.bot = automated ? new Bot(this) : null;
    }

    public static CorePlayerController local(String nickname) {
        return new CorePlayerController(nickname, true, false);
    }

    public static CorePlayerController remote(String nickname) {
        return new CorePlayerController(nickname, false, false);
    }

    public static CorePlayerController bot(String nickname) {
        return new CorePlayerController(nickname, false, true);
    }

    public boolean isLocal() {
        return local;
    }

    public boolean isBot() {
        return bot != null;
    }

    /** Binds the dealer wait that must be released after a renderer decision. */
    public void bindTurnCompletionSignal(Runnable signal) {
        turnCompletionSignal = Objects.requireNonNull(signal, "signal");
    }

    /** Binds this player to the dealer's current per-hand pot. */
    public void bindPotRegistration(Runnable registration) {
        potRegistration = Objects.requireNonNull(registration, "registration");
    }

    @Override
    public void bindDealer(DealerView dealer) {
        this.dealer = Objects.requireNonNull(dealer, "dealer");
        if (bot != null) bot.setContext(dealer, null);
    }

    /** Applies one already validated local renderer decision. */
    public synchronized boolean submitDecision(int decision, double raiseAmount) {
        if (!local || !isTurno() || getDecision() != NODEC || isExit()
                || isSpectator()) return false;
        DealerView currentDealer = requireDealer();
        switch (decision) {
            case FOLD -> setDecision(FOLD, "FOLD");
            case CHECK -> {
                double target = currentDealer.getApuesta_actual();
                if (MoneyMath.compare(target - getBet(), getStack()) >= 0) return false;
                boolean calling = MoneyMath.compare(target, getBet()) > 0;
                setBet(target);
                setDecision(CHECK, calling ? "CALL" : "CHECK");
            }
            case BET -> {
                if (!Double.isFinite(raiseAmount) || raiseAmount <= 0d) return false;
                double target = MoneyMath.clean(
                        currentDealer.getApuesta_actual() + raiseAmount);
                if (MoneyMath.compare(target, getBet()) <= 0
                        || MoneyMath.compare(target - getBet(), getStack()) >= 0) return false;
                setBet(target);
                setDecision(BET, "BET");
            }
            case ALLIN -> {
                if (MoneyMath.compare(getStack(), 0d) <= 0) return false;
                setBet(MoneyMath.clean(getBet() + getStack()));
                setDecision(ALLIN, "ALL IN");
            }
            default -> { return false; }
        }
        setTurn(false);
        turnCompletionSignal.run();
        return true;
    }

    public void showCards(String handName) {
        state.setShowingCards(true);
        state.setHandName(handName);
    }

    public void showWinner(String message) {
        loser = false;
        state.setWinner(true);
        state.setHandName(message);
    }

    public void showLoser(String message) {
        loser = true;
        state.setWinner(false);
        state.setHandName(message);
    }

    @Override public void setContaWin(int count) { winCount = count; }
    @Override public int getContaWin() { return winCount; }

    @Override
    public void ordenarCartas() {
        if (firstCard.getValorNumerico() < 0 || secondCard.getValorNumerico() < 0
                || firstCard.getValorNumerico() >= secondCard.getValorNumerico()) return;
        CardCode firstCode = firstCard.getState().code();
        boolean firstDisabled = firstCard.isDesenfocada();
        firstCard.getState().updateCode(secondCard.getState().code());
        firstCard.getState().setDisabled(secondCard.isDesenfocada());
        secondCard.getState().updateCode(firstCode);
        secondCard.getState().setDisabled(firstDisabled);
    }

    @Override
    public int getResponseTime() {
        if (state instanceof LocalPlayerState value) return value.responseTime();
        return ((RemotePlayerState) state).responseTime();
    }

    @Override public boolean isCalentando() {
        return isSpectator() && MoneyMath.compare(0d, getStack()) < 0;
    }
    @Override public boolean isActivo() { return !isExit() && !isSpectator(); }
    @Override public void stopActionTimer() { }
    @Override public boolean isTurno() {
        return state instanceof LocalPlayerState value ? value.turn()
                : ((RemotePlayerState) state).turn();
    }
    @Override public void resetBote() {
        state.setBet(0d);
        state.setPotContribution(0d);
    }
    @Override public void pagar(double amount, Integer sidePot) {
        state.setPendingPayment(MoneyMath.clean(getPagar() + amount));
    }
    @Override public void marcarBotePot(int sidePot) { }
    @Override public void repaintLastAction() { }
    @Override public void checkGameOver() { }
    @Override public int getBuyin() { return state.buyIn(); }
    @Override public double getBote() { return state.potContribution(); }
    @Override public void setTimeout(boolean value) { state.setTimedOut(value); }
    @Override public String getNickname() { return state.nickname(); }
    @Override public PlayerState getState() { return state; }
    @Override
    public synchronized void setNickname(String name) {
        state.setNickname(name);
        if (!local && state instanceof RemotePlayerState remote) {
            boolean automated = name.contains("$");
            remote.setBot(automated);
            if (automated && bot == null) {
                bot = new Bot(this);
                DealerView current = dealer;
                if (current != null) bot.setContext(current, null);
            } else if (!automated) {
                bot = null;
            }
        }
        state.setActive(!isExit() && !isSpectator());
    }
    @Override public CoreCardController getHoleCard1() { return firstCard; }
    @Override public CoreCardController getHoleCard2() { return secondCard; }
    @Override public List<CoreCardController> getHoleCards() {
        return List.of(firstCard, secondCard);
    }

    @Override
    public synchronized void nuevaMano() {
        setTurn(false);
        state.setDecision(PlayerState.Decision.NONE);
        state.setShowingCards(false);
        state.setWinner(false);
        state.setHandName("");
        state.setLastAction("");
        loser = false;
        resetBote();
        double winnings = getPagar();
        if (MoneyMath.compare(winnings, 0d) > 0) setStack(getStack() + winnings);
        state.setPendingPayment(0d);
        firstCard.resetearCarta();
        secondCard.resetearCarta();
        DealerView current = dealer;
        PlayerState.Position position = PlayerState.Position.NONE;
        if (current != null) {
            String nickname = getNickname();
            if (nickname.equals(current.getDealer_nick())) position = PlayerState.Position.DEALER;
            if (nickname.equals(current.getSb_nick())) position = PlayerState.Position.SMALL_BLIND;
            if (nickname.equals(current.getBb_nick())) position = PlayerState.Position.BIG_BLIND;
            underTheGun = nickname.equals(current.getUtg_nick());
        }
        state.setPosition(position);
        postForcedBlind(current, position);
        if (spectatorBigBlind && current != null) {
            spectatorBigBlind = false;
            double blind = current.getCiega_grande();
            if (MoneyMath.compare(blind, getStack()) < 0) setBet(blind);
            else {
                setBet(getStack());
                setDecision(ALLIN, "ALL IN");
            }
        }
    }

    private void postForcedBlind(DealerView current,
            PlayerState.Position position) {
        if (current == null) {
            return;
        }
        boolean headsUpDealer = position == PlayerState.Position.DEALER
                && getNickname().equals(current.getSb_nick());
        double blind = position == PlayerState.Position.BIG_BLIND
                ? current.getCiega_grande()
                : position == PlayerState.Position.SMALL_BLIND || headsUpDealer
                        ? current.getCiega_pequeña() : 0d;
        if (MoneyMath.compare(blind, 0d) <= 0) {
            return;
        }
        if (MoneyMath.compare(blind, getStack()) < 0) {
            setBet(blind);
        } else {
            setBet(getStack());
            setDecision(ALLIN, "ALL IN");
        }
    }

    @Override public void esTuTurno() {
        if (!isExit()) setTurn(true);
    }
    @Override public int getDecision() { return decisionValue(state.decision()); }
    @Override public void markFoldedOnRecover() { setDecision(FOLD, "FOLD"); }
    @Override public void setStack(double stack) {
        state.setStack(MoneyMath.clean(stack));
    }
    @Override public void setCounterRollDeferred(boolean deferred) { }
    @Override public void rollCountersToModel() { }
    @Override public double getStack() { return state.stack(); }
    @Override public double getBet() { return state.bet(); }

    @Override
    public synchronized void setBet(double newBet) {
        double clean = MoneyMath.clean(newBet);
        double old = getBet();
        state.setBet(clean);
        if (MoneyMath.compare(old, clean) < 0) {
            double difference = MoneyMath.clean(clean - old);
            state.setPotContribution(MoneyMath.clean(getBote() + difference));
            setStack(getStack() - difference);
        }
        potRegistration.run();
    }

    @Override
    public synchronized double postAnte(double ante) {
        if (MoneyMath.compare(getStack(), 0d) <= 0) return 0d;
        double requested = MoneyMath.clean(ante);
        double actual = MoneyMath.compare(requested, getStack()) < 0
                ? requested : MoneyMath.clean(getStack());
        if (MoneyMath.compare(requested, getStack()) >= 0) {
            setDecision(ALLIN, "ALL IN");
        }
        state.setPotContribution(MoneyMath.clean(getBote() + actual));
        setStack(getStack() - actual);
        potRegistration.run();
        return actual;
    }

    @Override
    public synchronized double postStraddle(double amount) {
        double requested = MoneyMath.clean(amount);
        if (MoneyMath.compare(requested, getStack()) < 0) {
            setBet(requested);
            return requested;
        }
        double actual = MoneyMath.clean(getStack());
        setBet(actual);
        setDecision(ALLIN, "ALL IN");
        return actual;
    }

    @Override public void resetBetDecision() {
        state.setDecision(PlayerState.Decision.NONE);
        state.setLastAction("");
    }
    @Override public void resetAutomatedDecisionState() {
        if (bot != null) bot.resetBot();
    }
    @Override public boolean hasAutomatedDecisionProvider() { return bot != null; }
    @Override public int calculateAutomatedDecision(int opponentCount) {
        if (bot == null) return GamePlayerController.super.calculateAutomatedDecision(opponentCount);
        return bot.calculateBotDecision(opponentCount);
    }
    @Override public double automatedBetSize() {
        if (bot == null) return GamePlayerController.super.automatedBetSize();
        return bot.getBetSize();
    }
    @Override public void recordAutomatedHandResult(boolean winner) {
        if (bot != null) bot.recordHandResult(winner);
    }

    @Override
    public synchronized void applyRemoteDecision(int decision, double betAmount) {
        DealerView current = requireDealer();
        switch (decision) {
            case FOLD -> setDecision(FOLD, "FOLD");
            case CHECK -> {
                boolean calling = MoneyMath.compare(current.getApuesta_actual(), getBet()) > 0;
                setBet(current.getApuesta_actual());
                setDecision(CHECK, calling ? "CALL" : "CHECK");
            }
            case BET -> {
                setBet(betAmount);
                setDecision(BET, "BET");
            }
            case ALLIN -> {
                setBet(MoneyMath.clean(getStack() + getBet()));
                setDecision(ALLIN, "ALL IN");
            }
            default -> throw new IllegalArgumentException("Unsupported decision: " + decision);
        }
        setTurn(false);
    }

    @Override public Object revealLock() { return revealLock; }
    @Override public void applyTelemetry(int latency1, int latency2, int reconnectionCount) {
        int response = Math.max(0, latency1);
        if (state instanceof LocalPlayerState value) value.setResponseTime(response);
        else ((RemotePlayerState) state).setResponseTime(response);
    }
    @Override public boolean isIwtsthCandidate() {
        return !local && bot == null && !isExit();
    }
    @Override public boolean isSpectator() { return state.spectator(); }
    @Override public boolean isExit() { return state.exited(); }
    @Override public void setExit() {
        state.setExited(true);
        state.setTimedOut(false);
        state.setActive(false);
        setTurn(false);
        turnCompletionSignal.run();
    }
    @Override public String getLastActionString() { return state.lastAction(); }
    @Override public void setBuyin(int buyin) { state.setBuyIn(buyin); }
    @Override public double getPagar() { return state.pendingPayment(); }
    @Override public void setPagar(double payment) {
        state.setPendingPayment(MoneyMath.clean(payment));
    }
    @Override public void setSpectator(String message) {
        state.setSpectator(true);
        state.setActive(false);
        state.setLastAction(message);
        setTurn(false);
    }
    @Override public void unsetSpectator() {
        state.setSpectator(false);
        state.setActive(!isExit());
    }
    @Override public void setSpectatorBB(boolean bigBlind) {
        spectatorBigBlind = bigBlind;
    }
    @Override public boolean isTimeout() { return state.timedOut(); }
    @Override public void setJugadaParcial(GameHandResult hand, boolean winner,
            float winPercentage) {
        state.setHandName(hand == null ? "" : hand.getName());
        state.setWinner(winner);
        loser = !winner;
    }
    @Override public boolean isWinner() { return state.winner(); }
    @Override public boolean isLoser() { return loser; }
    @Override public boolean isMuestra() { return state.showingCards(); }
    @Override public void setMuestra(boolean showing) { state.setShowingCards(showing); }
    @Override public void setConta_rabbit(int count) { rabbitCount = count; }
    @Override public void setRabbitJugada(String handName,
            List<? extends GameCardController> rabbitHandCards) {
        state.setHandName(handName);
    }
    @Override public void setChipForcedHidden(boolean hidden) { chipForcedHidden = hidden; }
    @Override public int getParguela_counter() { return parguelaCount; }
    @Override public void disableUTG() { underTheGun = false; }
    @Override public void setUTG() { underTheGun = true; }

    @Override public int getHoleCard1Index() { return albertaIndex(firstCard); }
    @Override public int getHoleCard2Index() { return albertaIndex(secondCard); }

    private void setTurn(boolean value) {
        if (state instanceof LocalPlayerState localState) localState.setTurn(value);
        else ((RemotePlayerState) state).setTurn(value);
    }

    private void setDecision(int value, String label) {
        state.setDecision(decisionState(value));
        state.setLastAction(getNickname() + " " + label + " ("
                + MoneyMath.clean(getBote()) + ")");
    }

    private DealerView requireDealer() {
        return Objects.requireNonNull(dealer, "player dealer");
    }

    private static PlayerState.Decision decisionState(int value) {
        return switch (value) {
            case FOLD -> PlayerState.Decision.FOLD;
            case CHECK -> PlayerState.Decision.CHECK;
            case BET -> PlayerState.Decision.BET;
            case ALLIN -> PlayerState.Decision.ALL_IN;
            default -> PlayerState.Decision.NONE;
        };
    }

    private static int decisionValue(PlayerState.Decision value) {
        return switch (value) {
            case FOLD -> FOLD;
            case CHECK -> CHECK;
            case BET -> BET;
            case ALL_IN -> ALLIN;
            case NONE -> NODEC;
        };
    }

    private static int albertaIndex(CoreCardController card) {
        CardCode code = card.getState().code();
        if (code == null) return -1;
        int rank = code.rank().aceHighValue() - 2;
        int suit = ALBERTA_SUITS.indexOf(code.suit().wire());
        return suit * 13 + rank;
    }
}
