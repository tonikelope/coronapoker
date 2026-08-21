package com.tonikelope.coronapoker.protocolsim;

import com.tonikelope.coronapoker.BettingRoundState;
import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.DeterministicShuffle;
import com.tonikelope.coronapoker.HandStateChain;
import com.tonikelope.coronapoker.HandverifyReceiptEnvelope;
import com.tonikelope.coronapoker.IdentityManager;
import com.tonikelope.coronapoker.MoneyCents;
import com.tonikelope.coronapoker.PotMath;
import com.tonikelope.coronapoker.SettlementRecord;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First vertical slice of the protocol simulator. It deliberately composes the
 * production betting reducer, canonical records, Ed25519 identities, hand
 * ratchet, deterministic deck permutation, community records, RIT pot split,
 * settlement transcript and HANDVERIFY receipt parser.
 *
 * It is not the old bot simulator: every peer owns an independent production
 * HandStateChain and must converge after every signed event. The remaining live
 * Crupier orchestration (recovery, SRA cascade and transport fault scheduler) is
 * tracked separately and must not be claimed by this campaign.
 */
@Tag("slow")
@Tag("protocol-sim")
final class ProtocolSimulationCampaignTest {

    private static final String HOST = "sim-host";
    private static final String CLIENT = "sim-client";
    private static final String BOT = "CoronaBot$sim";

    private static Actor host;
    private static Actor client;
    private static Actor bot;
    private static List<Actor> actors;

    private record Actor(String nick, byte[] playerId, IdentityManager signer,
            byte[] signerPublicKey) {
    }

    private record ActionSpec(Actor actor, BettingRoundState.Action reducerAction,
            long committedTotalCents, int wireAction, boolean allIn) {
    }

    @BeforeAll
    static void identities() {
        IdentityManager hostIdentity = IdentityManager.initializeForNick(HOST);
        assertTrue(hostIdentity.isReady(), hostIdentity.getLoadError());
        IdentityManager clientIdentity = IdentityManager.initializeForNick(CLIENT);
        assertTrue(clientIdentity.isReady(), clientIdentity.getLoadError());

        host = actor(HOST, hostIdentity, hostIdentity.getPublicKey());
        client = actor(CLIENT, clientIdentity, clientIdentity.getPublicKey());
        // Bots are authored by the host identity in the live protocol.
        bot = actor(BOT, hostIdentity, hostIdentity.getPublicKey());
        actors = List.of(host, client, bot);
    }

    @Test
    void seededCampaignKeepsHonestPeersAndMoneyConvergent() {
        int hands = positiveIntProperty("qa.sim.hands", 2_000, 100_000);
        long seed = longProperty("qa.sim.seed", 0xC0A0_2026L);
        Random random = new Random(seed);

        for (int hand = 0; hand < hands; hand++) {
            playHand(random, seed, hand);
        }
    }

    private static void playHand(Random random, long campaignSeed, int handNumber) {
        String context = "seed=" + campaignSeed + " hand=" + handNumber;
        byte[] handId = randomBytes(random, CanonicalActionRecord.HAND_ID_BYTES);
        byte[] shuffleSeed = randomBytes(random, 48);
        int[] deck = DeterministicShuffle.shufflePermutation(52, shuffleSeed);
        assertPermutation(deck, context);

        List<byte[]> playerIds = actors.stream().map(a -> a.playerId().clone()).toList();
        List<byte[]> pocketCommits = new ArrayList<>();
        List<byte[]> communityCommits = new ArrayList<>();
        for (int i = 0; i < actors.size(); i++) {
            pocketCommits.add(randomBytes(random, 32));
            communityCommits.add(randomBytes(random, 32));
        }
        byte[] cascadedDeck = randomBytes(random, 52 * 32);

        List<HandStateChain> peers = new ArrayList<>();
        for (int i = 0; i < actors.size(); i++) {
            peers.add(HandStateChain.start(handId, playerIds,
                    pocketCommits, communityCommits, cascadedDeck));
        }
        assertPeerHashes(peers, context + " genesis");

        LinkedHashMap<String, Long> committed = new LinkedHashMap<>();
        committed.put(HOST, 0L);
        committed.put(CLIENT, 10L);
        committed.put(BOT, 20L);
        BettingRoundState betting = BettingRoundState.start(committed, 20L, 20L);

        List<ActionSpec> actions = actionPattern(handNumber);
        for (ActionSpec action : actions) {
            BettingRoundState.Transition transition = betting.apply(action.actor().nick(),
                    action.reducerAction(), action.committedTotalCents());
            assertTrue(transition.isAccepted(), context + " rejected " + action);
            betting = transition.state();
            committed.put(action.actor().nick(), action.committedTotalCents());
            broadcastSignedAction(peers, handId, action, context);
        }

        broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_FLOP,
                new int[]{deck[0], deck[1], deck[2]}, context);
        broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_TURN,
                new int[]{deck[3]}, context);
        broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_RIVER,
                new int[]{deck[4]}, context);

        boolean runItTwice = handNumber % 4 == 2;
        if (runItTwice) {
            broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_RIT2_FLOP,
                    new int[]{deck[5], deck[6], deck[7]}, context);
            broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_RIT2_TURN,
                    new int[]{deck[8]}, context);
            broadcastCommunity(peers, handId, CanonicalActionRecord.STREET_RIT2_RIVER,
                    new int[]{deck[9]}, context);
        }

        long potCents = committed.values().stream().mapToLong(Long::longValue).sum();
        List<SettlementRecord.Entry> settlement = settle(handNumber, committed, potCents,
                runItTwice);
        long paid = settlement.stream().mapToLong(SettlementRecord.Entry::getPagarCents).sum();
        long closingRemainder = potCents - paid;
        assertTrue(SettlementRecord.amountsBalance(settlement, 0L, closingRemainder),
                context + " money conservation");

        byte[] table = SettlementRecord.encode(handId, settlement, 0L, closingRemainder);
        assertEquals(actors.size(), SettlementRecord.readParticipantCount(table), context);
        for (HandStateChain peer : peers) {
            peer.absorbSettlement(table);
        }
        assertPeerHashes(peers, context + " settlement");
        verifyReceipts(peers.get(0).getCurrentHash(), handId, context);
        probeTamperAndStalePreviousHash(peers.get(0), handId, context);
    }

    private static List<ActionSpec> actionPattern(int handNumber) {
        return switch (handNumber & 3) {
            case 0 -> List.of(
                    action(host, BettingRoundState.Action.RAISE, 40L,
                            CanonicalActionRecord.ACTION_BET, false),
                    action(client, BettingRoundState.Action.CHECK_CALL, 40L,
                            CanonicalActionRecord.ACTION_CHECK, false),
                    action(bot, BettingRoundState.Action.CHECK_CALL, 40L,
                            CanonicalActionRecord.ACTION_CHECK, false));
            case 1 -> List.of(
                    action(host, BettingRoundState.Action.RAISE, 40L,
                            CanonicalActionRecord.ACTION_BET, false),
                    action(client, BettingRoundState.Action.ALL_IN, 50L,
                            CanonicalActionRecord.ACTION_ALLIN, true),
                    action(bot, BettingRoundState.Action.CHECK_CALL, 50L,
                            CanonicalActionRecord.ACTION_CHECK, false),
                    action(host, BettingRoundState.Action.CHECK_CALL, 50L,
                            CanonicalActionRecord.ACTION_CHECK, false));
            case 2 -> List.of(
                    action(host, BettingRoundState.Action.ALL_IN, 100L,
                            CanonicalActionRecord.ACTION_ALLIN, true),
                    action(client, BettingRoundState.Action.FOLD, 10L,
                            CanonicalActionRecord.ACTION_FOLD, false),
                    action(bot, BettingRoundState.Action.CHECK_CALL, 100L,
                            CanonicalActionRecord.ACTION_CHECK, false));
            default -> List.of(
                    action(host, BettingRoundState.Action.FOLD, 0L,
                            CanonicalActionRecord.ACTION_FOLD, false),
                    action(client, BettingRoundState.Action.FOLD, 10L,
                            CanonicalActionRecord.ACTION_FOLD, false));
        };
    }

    private static ActionSpec action(Actor actor, BettingRoundState.Action reducerAction,
            long committedTotalCents, int wireAction, boolean allIn) {
        return new ActionSpec(actor, reducerAction, committedTotalCents, wireAction, allIn);
    }

    private static void broadcastSignedAction(List<HandStateChain> peers, byte[] handId,
            ActionSpec action, String context) {
        byte[] record = CanonicalActionRecord.encode(peers.get(0).getCurrentHash(), handId,
                action.actor().playerId(), CanonicalActionRecord.STREET_PREFLOP,
                action.wireAction(), action.reducerAction() == BettingRoundState.Action.FOLD
                        ? 0L : action.committedTotalCents(),
                action.allIn(), true);
        byte[] signature = action.actor().signer().signAction(record);
        assertTrue(IdentityManager.verifyAction(action.actor().signerPublicKey(), record, signature),
                context + " signer mismatch " + action.actor().nick());
        for (HandStateChain peer : peers) {
            assertTrue(IdentityManager.verifyAction(action.actor().signerPublicKey(), record, signature),
                    context + " receiver signature");
            peer.absorb(record, signature);
        }
        assertPeerHashes(peers, context + " action " + action.actor().nick());
    }

    private static void broadcastCommunity(List<HandStateChain> peers, byte[] handId,
            int street, int[] cards, String context) {
        byte[] record = CanonicalActionRecord.encode(peers.get(0).getCurrentHash(), handId,
                host.playerId(), street, CanonicalActionRecord.ACTION_COMMUNITY,
                CanonicalActionRecord.packCommunityCards(cards), false, false);
        byte[] signature = host.signer().signAction(record);
        assertTrue(IdentityManager.verifyAction(host.signerPublicKey(), record, signature), context);
        for (HandStateChain peer : peers) {
            peer.absorb(record, signature);
        }
        assertPeerHashes(peers, context + " community street=" + street);
    }

    private static List<SettlementRecord.Entry> settle(int handNumber,
            Map<String, Long> committed, long potCents, boolean runItTwice) {
        Map<String, Long> paid = new LinkedHashMap<>();
        actors.forEach(a -> paid.put(a.nick(), 0L));
        if (runItTwice) {
            double[] split = PotMath.splitForRunItTwice(potCents / 100.0d);
            long sideCents = MoneyCents.fromDouble(split[0]).cents();
            paid.put(HOST, sideCents);
            paid.put(BOT, sideCents);
        } else if (handNumber % 5 == 0 && handNumber % 4 != 3) {
            double[] split = PotMath.splitAmongWinners(potCents / 100.0d, 2);
            long share = MoneyCents.fromDouble(split[0]).cents();
            paid.put(HOST, share);
            paid.put(BOT, share);
        } else {
            String winner = handNumber % 4 == 3 ? BOT : actors.get(handNumber % actors.size()).nick();
            paid.put(winner, potCents);
        }
        List<SettlementRecord.Entry> result = new ArrayList<>();
        for (Actor actor : actors) {
            result.add(new SettlementRecord.Entry(actor.playerId(),
                    committed.get(actor.nick()), paid.get(actor.nick())));
        }
        return result;
    }

    private static void verifyReceipts(byte[] finalHash, byte[] handId, String context) {
        for (Actor actor : List.of(host, client)) {
            byte flags = 0;
            byte[] signature = actor.signer().signReceipt(handId, finalHash, flags);
            byte[] receipt = new byte[HandverifyReceiptEnvelope.RECEIPT_BYTES];
            System.arraycopy(handId, 0, receipt, 0, handId.length);
            System.arraycopy(finalHash, 0, receipt, handId.length, finalHash.length);
            receipt[handId.length + finalHash.length] = flags;
            System.arraycopy(signature, 0, receipt, handId.length + finalHash.length + 1,
                    signature.length);
            String command = "GAME#1#HANDVERIFY#"
                    + Base64.getEncoder().encodeToString(actor.nick().getBytes(StandardCharsets.UTF_8))
                    + "#" + Base64.getEncoder().encodeToString(receipt);
            HandverifyReceiptEnvelope envelope = HandverifyReceiptEnvelope.parse(command.split("#", -1));
            assertEquals(actor.nick(), envelope.nick(), context);
            assertArrayEquals(handId, envelope.handId(), context);
            assertArrayEquals(finalHash, envelope.finalHash(), context);
            assertTrue(IdentityManager.verifyReceipt(actor.signerPublicKey(), envelope.handId(),
                    envelope.finalHash(), envelope.flags(), envelope.signature()), context);
        }
    }

    private static void probeTamperAndStalePreviousHash(HandStateChain settled,
            byte[] handId, String context) {
        byte[] stalePrevious = new byte[32];
        byte[] record = CanonicalActionRecord.encode(stalePrevious, handId, client.playerId(),
                CanonicalActionRecord.STREET_SHOWDOWN, CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        byte[] signature = client.signer().signAction(record);
        assertTrue(IdentityManager.verifyAction(client.signerPublicKey(), record, signature), context);
        assertThrows(IllegalStateException.class, () -> settled.absorb(record, signature),
                context + " stale PREV_H accepted");

        byte[] tampered = record.clone();
        tampered[CanonicalActionRecord.OFFSET_AMOUNT_CENTS + 7] ^= 1;
        assertFalse(IdentityManager.verifyAction(client.signerPublicKey(), tampered, signature),
                context + " tampered signed action accepted");
    }

    private static Actor actor(String nick, IdentityManager signer, byte[] signerPublicKey) {
        return new Actor(nick, CanonicalActionRecord.playerIdFromNick(nick), signer,
                signerPublicKey.clone());
    }

    private static void assertPeerHashes(List<HandStateChain> peers, String context) {
        byte[] expected = peers.get(0).getCurrentHash();
        for (int i = 1; i < peers.size(); i++) {
            assertArrayEquals(expected, peers.get(i).getCurrentHash(), context + " peer=" + i);
        }
    }

    private static void assertPermutation(int[] deck, String context) {
        assertEquals(52, deck.length, context);
        boolean[] seen = new boolean[52];
        for (int card : deck) {
            assertTrue(card >= 0 && card < 52 && !seen[card], context + " card=" + card);
            seen[card] = true;
        }
        assertTrue(Arrays.stream(deck).anyMatch(card -> card == 51), context + " missing card 52");
    }

    private static byte[] randomBytes(Random random, int size) {
        byte[] value = new byte[size];
        random.nextBytes(value);
        return value;
    }

    private static int positiveIntProperty(String name, int defaultValue, int maximum) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0 || parsed > maximum) {
            throw new IllegalArgumentException(name + " must be in 1.." + maximum);
        }
        return parsed;
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() || value.startsWith("${")
                ? defaultValue : Long.parseLong(value);
    }
}
