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
 * FULLY EXPERIMENTAL. B-A-S-E-D on the mythical Alberta's Loki Bot
 * https://poker.cs.ualberta.ca/publications/papp.msc.pdf
 *
 * Very very optimizable, but enough to have fun with.
 *
 * @author tonikelope
 */
public class Bot {

    public static final String PALOS = "TDCP";
    public static final int MAX_CONTA_BET = 2;
    public static final int BOT_THINK_TIME = 1500;
    public static final org.alberta.poker.Hand BOT_COMMUNITY_CARDS = new org.alberta.poker.Hand();
    public static final org.alberta.poker.HandEvaluator HANDEVALUATOR = new org.alberta.poker.HandEvaluator();
    public static final org.alberta.poker.ai.HandPotential HANDPOTENTIAL = new org.alberta.poker.ai.HandPotential();

    private volatile RemotePlayer cpu_player = null;
    private volatile boolean semi_bluff = false;
    private volatile org.alberta.poker.Card card1 = null;
    private volatile org.alberta.poker.Card card2 = null;
    private volatile int conta_call = 0;
    private volatile boolean slow_play = false;
    private volatile boolean cbet = false;

    public Bot(RemotePlayer player) {
        cpu_player = player;
    }

    //LLAMAR DESDE EL CRUPIER UNA VEZ REPARTIDAS LAS CARTAS AL JUGADOR
    public void resetBot() {

        card1 = new org.alberta.poker.Card(cpu_player.getPlayingCard1().getValorNumerico() - 2, getLokiCardSuitFromCoronaCard(cpu_player.getPlayingCard1()));
        card2 = new org.alberta.poker.Card(cpu_player.getPlayingCard2().getValorNumerico() - 2, getLokiCardSuitFromCoronaCard(cpu_player.getPlayingCard2()));

        semi_bluff = false;
        slow_play = Helpers.CSPRNG_GENERATOR.nextBoolean();
        conta_call = 0;
    }

    public float getBetSize() {

        float min_raise = Helpers.float1DSecureCompare(0f, GameFrame.getInstance().getCrupier().getUltimo_raise()) < 0 ? GameFrame.getInstance().getCrupier().getUltimo_raise() : GameFrame.getInstance().getCrupier().getCiega_grande();

        float b;

        switch (GameFrame.getInstance().getCrupier().getFase()) {
            case Crupier.PREFLOP:
                b = Helpers.floatClean((1 + Helpers.CSPRNG_GENERATOR.nextInt(2) + GameFrame.getInstance().getCrupier().getLimpersCount()) * GameFrame.getInstance().getCrupier().getCiega_grande());

                break;
            default:

                b = (float) (Math.ceil(Math.ceil(GameFrame.getInstance().getCrupier().getBote_total() / GameFrame.CIEGA_PEQUEÑA) / (2f + (float) Helpers.CSPRNG_GENERATOR.nextInt(2))) * GameFrame.CIEGA_PEQUEÑA);
                break;
        }

        if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), 0f) == 0 || (GameFrame.getInstance().getCrupier().getFase() == Crupier.PREFLOP && Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual(), GameFrame.getInstance().getCrupier().getCiega_grande()) == 0)) {
            return Math.max(GameFrame.getInstance().getCrupier().getCiega_grande(), b);
        } else {
            return Math.max(GameFrame.getInstance().getCrupier().getApuesta_actual() + min_raise, b);
        }

    }

    public int calculateBotDecision(int opponents) {

        int dec = Player.FOLD;

        int fase = GameFrame.getInstance().getCrupier().getFase();

        int activos = GameFrame.getInstance().getCrupier().getJugadoresActivos();

        double strength = HANDEVALUATOR.handRank(card1, card2, Bot.BOT_COMMUNITY_CARDS, opponents);

        double ppot = HANDPOTENTIAL.ppot_raw(card1, card2, Bot.BOT_COMMUNITY_CARDS, true);

        double npot = HANDPOTENTIAL.getLastNPot();

        double effectiveStrength = strength + (1 - strength) * ppot - strength * npot;

        double poseffectiveStrength = strength + (1 - strength) * ppot;

        //PREFLOP
        if (fase == Crupier.PREFLOP) {

            if (GameFrame.getInstance().getCrupier().getConta_bet() > 0 && Helpers.float1DSecureCompare(cpu_player.getStack(), GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) <= 0) {

                //Si la apuesta actual nos obliga a ir ALL-IN sólo lo hacemos con manos PREMIUM o el el 50% de las otras veces con manos buenas que no llegan a PREMIUM
                if (poseffectiveStrength >= 0.9f || (Helpers.CSPRNG_GENERATOR.nextBoolean() && poseffectiveStrength >= 0.8f)) {
                    conta_call++;
                    dec = Player.CHECK;
                }

            } else if (poseffectiveStrength >= 0.75f) {

                //Manos buenas (sin ser todas PREMIUM)
                conta_call++;

                dec = (GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET && !this.slow_play) ? Player.BET : Player.CHECK;

            } else if ((Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 2) <= 0) && poseffectiveStrength >= 0.60f) {

                //El X% de las veces apostamos con una mano no tan fuerte (siempre que no haya que poner más del 50% de nuestro stack)
                boolean vamos;

                conta_call++;

                if (activos <= 4) {

                    //50%
                    vamos = Helpers.CSPRNG_GENERATOR.nextBoolean();

                } else if (activos <= 6) {

                    //40%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 3;

                } else if (activos <= 8) {

                    //30%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 2;

                } else {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 1;
                }

                dec = (GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET && vamos && !this.slow_play) ? Player.BET : Player.CHECK;

            } else if (GameFrame.getInstance().getCrupier().getConta_bet() == 0) {

                //Limpeamos el X% de las manos a ver si suena la flauta
                boolean vamos;

                if (activos <= 4) {
                    //50%
                    vamos = Helpers.CSPRNG_GENERATOR.nextBoolean();

                } else if (activos <= 6) {

                    //40%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 3;

                } else if (activos <= 8) {

                    //30%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 2;

                } else {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(10) <= 1;
                }

                if (vamos) {
                    conta_call++;
                    dec = Player.CHECK;
                }

            } else if (Helpers.float1DSecureCompare(GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet(), cpu_player.getStack() / 10) <= 0) {

                //Vemos el X% de apuestas con el resto de cartas siempre que no haya que poner más del 10% de nuestro stack para ver la apuesta
                boolean vamos;

                if (activos <= 4) {

                    //20%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 3;

                } else if (activos <= 6) {

                    //15%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 2;

                } else if (activos <= 8) {

                    //10%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) <= 1;

                } else {

                    //5%
                    vamos = Helpers.CSPRNG_GENERATOR.nextInt(20) == 0;
                }

                if (vamos) {
                    conta_call++;

                    dec = Player.CHECK;
                }
            }

            if (dec == Player.BET && Helpers.CSPRNG_GENERATOR.nextBoolean()) {
                cbet = true;
            }

            return dec;
        }

        //POST FLOP
        if (fase > Crupier.FLOP) {
            this.slow_play = false;
            this.cbet = false;
        }

        if (poseffectiveStrength >= 0.85f) {

            conta_call++;

            dec = (GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET && !this.slow_play) ? Player.BET : Player.CHECK;

        } else if (poseffectiveStrength >= 0.70f && !(effectiveStrength < 0.80f && fase == Crupier.RIVER && Helpers.float1DSecureCompare(cpu_player.getStack(), GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) <= 0)) {

            if (GameFrame.getInstance().getCrupier().getConta_bet() == 0) {

                conta_call++;

                dec = !this.slow_play ? Player.BET : Player.CHECK;

            } else if (!(conta_call == 0 && GameFrame.getInstance().getCrupier().getConta_bet() >= 2)) {

                conta_call++;

                dec = Player.CHECK;
            }
        } else if (GameFrame.getInstance().getCrupier().getConta_bet() == 0 && Helpers.CSPRNG_GENERATOR.nextBoolean()) {

            conta_call++;

            if (this.semi_bluff || (fase != Crupier.RIVER && ppot >= 2 * potOdds2()) || Helpers.CSPRNG_GENERATOR.nextBoolean()) {

                this.semi_bluff = true;

                dec = Player.BET;
            } else {

                dec = Player.CHECK;
            }
        } else if (fase == Crupier.RIVER && effectiveStrength >= 3 * potOdds()) {

            conta_call++;

            dec = Player.CHECK;
        } else if (fase != Crupier.RIVER && ppot >= 1.5 * potOdds()) {

            conta_call++;

            dec = Player.CHECK;
        } else {
            float showdown_cost;

            if (fase != Crupier.RIVER) {

                if (fase == Crupier.FLOP) {
                    showdown_cost = 4 * GameFrame.getInstance().getCrupier().getApuesta_actual();
                } else {
                    showdown_cost = GameFrame.getInstance().getCrupier().getApuesta_actual();
                }

                if (effectiveStrength >= 2.5 * showdownOdds(showdown_cost) && !this.semi_bluff) {

                    conta_call++;

                    dec = Player.CHECK;
                }
            }

        }

        if (dec == Player.CHECK && cbet && GameFrame.getInstance().getCrupier().getConta_bet() < Bot.MAX_CONTA_BET) {
            dec = Player.BET;
        }

        return dec;

    }

    private float potOdds2() {

        return (4 * GameFrame.getInstance().getCrupier().getCiega_grande()) / (GameFrame.getInstance().getCrupier().getBote_total() + 12 * GameFrame.getInstance().getCrupier().getCiega_grande());

    }

    private float potOdds() {

        return (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) / (GameFrame.getInstance().getCrupier().getBote_total() + (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()));

    }

    private float showdownOdds(float cost) {

        return ((GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + cost) / (GameFrame.getInstance().getCrupier().getBote_total() + (GameFrame.getInstance().getCrupier().getApuesta_actual() - cpu_player.getBet()) + 2 * cost);
    }

    public static int getLokiCardSuitFromCoronaCard(Card carta) {

        return Bot.PALOS.indexOf(carta.getPalo());

    }

    public static org.alberta.poker.Card getLokiCardFromCoronaIntegerCard(int c) {

        int v = (c - 1) % 13;

        int corona_valor = (v == 0 ? 14 : v + 1);

        String corona_palo = Card.PALOS[(int) ((float) (c - 1) / 13)];

        return new org.alberta.poker.Card(corona_valor - 2, Bot.PALOS.indexOf(corona_palo));
    }

}
