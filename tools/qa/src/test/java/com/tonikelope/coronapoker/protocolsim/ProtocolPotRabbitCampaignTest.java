/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Randomized production HandPot and signed Rabbit campaign. */
@Tag("slow")
@Tag("protocol-sim")
class ProtocolPotRabbitCampaignTest {

    private static final long DEFAULT_SEED = 3231711270L;
    private static final double EPSILON = 1.0e-7;
    private static IdentityManager hostIdentity;
    private static IdentityManager clientIdentity;

    @BeforeAll
    static void identities() {
        hostIdentity = IdentityManager.initializeForNick("sim-pot-rabbit-host");
        clientIdentity = IdentityManager.initializeForNick("sim-pot-rabbit-client");
        assertTrue(hostIdentity.isReady(), hostIdentity.getLoadError());
        assertTrue(clientIdentity.isReady(), clientIdentity.getLoadError());
    }

    @Test
    void randomizedSidePotsAndRabbitStayConvergent() {
        int hands = intProperty("qa.sim.hands", 2_000, 1, 100_000);
        long seed = longProperty("qa.sim.seed", DEFAULT_SEED);
        Integer replay = optionalIntProperty("qa.sim.hand", 0, hands - 1);
        int first = replay == null ? 0 : replay;
        int end = replay == null ? hands : replay + 1;
        for (int hand = first; hand < end; hand++) {
            runOne(seed, hand);
        }
    }

    private static void runOne(long campaignSeed, int hand) {
        long handSeed = mix64(campaignSeed ^ hand);
        Random random = new Random(handSeed);
        byte[] handId = new byte[CanonicalActionRecord.HAND_ID_BYTES];
        random.nextBytes(handId);
        String context = "seed=" + campaignSeed + " hand=" + hand
                + " handSeed=" + handSeed;
        exerciseSidePots(random, context);
        exerciseRabbit(random, handId, context);
    }

    private static void exerciseSidePots(Random random, String context) {
        int playerCount = 2 + random.nextInt(8);
        List<FakePotPlayer> players = new ArrayList<>(playerCount);
        long committedCents = 0L;
        for (int seat = 0; seat < playerCount; seat++) {
            long cents = switch (seat) {
                case 0 -> 1L;
                case 1 -> random.nextBoolean() ? 0L : 100_000L;
                default -> random.nextInt(100_001);
            };
            int decision;
            boolean active;
            if (seat == 0) {
                decision = Player.ALLIN;
                active = true;
            } else {
                int roll = random.nextInt(4);
                decision = roll == 0 ? Player.FOLD
                        : roll == 1 ? Player.ALLIN
                        : roll == 2 ? Player.BET : Player.CHECK;
                active = decision != Player.FOLD && random.nextBoolean();
                if (decision != Player.FOLD && decision != Player.ALLIN) {
                    active = true;
                }
            }
            FakePotPlayer player = new FakePotPlayer(
                    "sim-" + seat, cents / 100.0, decision, active);
            players.add(player);
            committedCents += cents;
        }

        HandPot pot = new HandPot(0.0);
        players.forEach(pot::addPlayer);
        pot.genSidePots();
        double layerTotal = 0.0;
        int layers = 0;
        for (HandPot layer = pot; layer != null; layer = layer.getSidePot()) {
            layers++;
            assertTrue(Double.isFinite(layer.getTotal()) && layer.getTotal() >= 0.0,
                    context + " invalid pot layer total");
            assertTrue(Double.isFinite(layer.getBet()) && layer.getBet() >= 0.0,
                    context + " invalid pot layer cap");
            layerTotal += layer.getTotal();
        }
        assertEquals(committedCents / 100.0, layerTotal, EPSILON,
                context + " side-pot conservation failed");
        assertEquals(layers - 1, pot.getSide_pot_count(),
                context + " side-pot chain count mismatch");
        assertTrue(layers <= playerCount, context + " too many side-pot layers");
    }

    private static void exerciseRabbit(Random random, byte[] handId, String context) {
        int mode = random.nextInt(4);
        long smallBlind = random.nextInt(10_001);
        long bigBlind = smallBlind + random.nextInt(10_001);
        RabbitFeeLedger authority = new RabbitFeeLedger(
                handId, mode, smallBlind, bigBlind);
        List<RabbitFeeLedger> peers = List.of(
                new RabbitFeeLedger(handId, mode, smallBlind, bigBlind),
                new RabbitFeeLedger(handId, mode, smallBlind, bigBlind));
        int requests = 1 + random.nextInt(6);

        for (int requestIndex = 0; requestIndex < requests; requestIndex++) {
            boolean clientRequest = random.nextBoolean();
            String nick = clientRequest ? "sim-pot-rabbit-client" : "sim-pot-rabbit-host";
            IdentityManager signer = clientRequest ? clientIdentity : hostIdentity;
            byte[] nonce = new byte[RabbitFeeLedger.NONCE_BYTES];
            random.nextBytes(nonce);
            byte[] signature = signer.signRabbitRequest(handId, nick, nonce);
            RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                    handId, nick, nonce, signature);
            RabbitFeeLedger.Result<RabbitFeeLedger.Request> decodedRequest
                    = RabbitFeeLedger.Request.decode(request.encode());
            assertTrue(decodedRequest.isOk(), context + " Rabbit request did not round-trip");
            assertTrue(IdentityManager.verifyRabbitRequest(signer.getPublicKey(),
                    handId, nick, nonce, decodedRequest.value().requesterSignature()),
                    context + " Rabbit requester signature failed");

            RabbitFeeLedger.Authorization authorization = authority.authorize(
                    decodedRequest.value()).value();
            RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> decodedAuthorization
                    = RabbitFeeLedger.Authorization.decode(authorization.encode());
            assertTrue(decodedAuthorization.isOk(),
                    context + " Rabbit authorization did not round-trip");
            for (RabbitFeeLedger peer : peers) {
                assertEquals(RabbitFeeLedger.Acceptance.ACCEPTED,
                        peer.accept(decodedAuthorization.value()), context);
                assertEquals(RabbitFeeLedger.Acceptance.DUPLICATE,
                        peer.accept(decodedAuthorization.value()),
                        context + " exact Rabbit duplicate was not idempotent");
            }

            byte[] altered = authorization.encode();
            altered[altered.length - 1] ^= 1;
            RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> alteredAuthorization
                    = RabbitFeeLedger.Authorization.decode(altered);
            if (alteredAuthorization.isOk()) {
                for (RabbitFeeLedger peer : peers) {
                    assertEquals(RabbitFeeLedger.Acceptance.REJECTED,
                            peer.accept(alteredAuthorization.value()),
                            context + " altered Rabbit authorization accepted");
                }
            }

            byte[] alteredNonce = nonce.clone();
            alteredNonce[0] ^= 1;
            assertFalse(IdentityManager.verifyRabbitRequest(signer.getPublicKey(),
                    handId, nick, alteredNonce, signature),
                    context + " Rabbit signature accepted a mutated nonce");
        }

        byte[] otherHand = handId.clone();
        otherHand[0] ^= 1;
        RabbitFeeLedger other = new RabbitFeeLedger(otherHand, mode, smallBlind, bigBlind);
        byte[] nonce = new byte[RabbitFeeLedger.NONCE_BYTES];
        Arrays.fill(nonce, (byte) 7);
        byte[] signature = hostIdentity.signRabbitRequest(handId,
                "sim-pot-rabbit-host", nonce);
        RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(handId,
                "sim-pot-rabbit-host", nonce, signature);
        assertEquals(RabbitFeeLedger.Acceptance.REJECTED,
                other.accept(authority.authorize(request).value()),
                context + " cross-hand Rabbit replay accepted");
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return fallback;
        }
        return parseInt(name, text, min, max);
    }

    private static Integer optionalIntProperty(String name, int min, int max) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return null;
        }
        return parseInt(name, text, min, max);
    }

    private static int parseInt(String name, String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " must be " + min + ".." + max);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer: " + text, ex);
        }
    }

    private static long longProperty(String name, long fallback) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a long: " + text, ex);
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
