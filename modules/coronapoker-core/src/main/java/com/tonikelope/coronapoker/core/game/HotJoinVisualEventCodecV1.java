/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableSessionSummary;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded wire projection of the ordinary public presentation stream for a
 * peer that is still warming up.
 *
 * <p>The decoder recreates the same {@link TableVisualEvent} consumed by every
 * regular GDX table.  There is deliberately no second animation model for hot
 * join.  Events that are local controls rather than public table presentation
 * are not encodable.  Concealed card codes are stripped at this boundary.</p>
 */
public final class HotJoinVisualEventCodecV1 {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_TEXT_BYTES = 16 * 1024;
    private static final int MAX_LIST = 32;

    private static final int ALL_IN_PAUSE = 1;
    private static final int SHUFFLE = 2;
    private static final int POSITION_ROTATION = 3;
    private static final int COLLECT_BETS = 4;
    private static final int DEAL_HOLE_CARD = 5;
    private static final int DEAL_COMMUNITY_CARD = 6;
    private static final int RUN_IT_TWICE_BOARD = 7;
    private static final int FOLD_HOLE_CARDS = 8;
    private static final int REVEAL_COMMUNITY_CARDS = 9;
    private static final int TURN_TIMER = 10;
    private static final int SHARED_PROGRESS = 11;
    private static final int PLAYER_ACTION = 12;
    private static final int CINEMATIC = 13;
    private static final int REVEAL_HOLE_CARDS = 14;
    private static final int PARTIAL_HAND = 15;
    private static final int HAND_RESULT = 16;
    private static final int RABBIT_CARDS = 17;
    private static final int RABBIT_RESULT = 18;
    private static final int RABBIT_NOTICE = 19;
    private static final int SHOWDOWN_HIGHLIGHT = 20;
    private static final int PAYOUT = 21;
    private static final int PAYOUT_BATCH = 22;
    private static final int AUDIO_CUE = 23;
    private static final int SPECIAL_CARD_SOUND = 24;
    private static final int REBUY = 25;
    private static final int REBUY_DECISION = 26;
    private static final int INITIAL_STACK_FILL = 27;
    private static final int SEAT_ROSTER = 28;
    private static final int PAUSE_STATUS = 29;
    private static final int TELEMETRY_STATUS = 30;
    private static final int PLAYER_TIMEOUT = 31;
    private static final int PLAYER_DEPARTURE = 32;
    private static final int UNDER_THE_GUN_STATUS = 33;
    private static final int SWAP_HOLE_CARDS = 34;
    private static final int TABLE_INFO = 35;
    private static final int CALL_COST = 36;
    private static final int IMMEDIATE_REBUY_STATUS = 37;
    private static final int DECK_CHANGED = 38;
    private static final int LAST_HAND_STATUS = 39;
    private static final int HAND_LIMIT_STATUS = 40;
    private static final int GAME_CONFIGURATION_STATUS = 41;
    private static final int RUN_IT_TWICE_LOCK_STATUS = 42;
    private static final int COMMUNICATION_RULES_STATUS = 43;
    private static final int GAME_CLOCK = 44;
    private static final int CLOSE_TABLE = 45;

    private HotJoinVisualEventCodecV1() {
    }

    /** Returns empty for renderer-local or snapshot-only event types. */
    public static Optional<String> encode(TableVisualEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                if (event instanceof TableVisualEvent.AllInRunoutPause value) {
                    out.writeByte(ALL_IN_PAUSE);
                    out.writeLong(value.durationMillis());
                } else if (event instanceof TableVisualEvent.Shuffle value) {
                    out.writeByte(SHUFFLE);
                    writeText(out, value.deck());
                    writeEnum(out, value.phase());
                } else if (event instanceof TableVisualEvent.PositionRotation value) {
                    out.writeByte(POSITION_ROTATION);
                    writePositionTransfers(out, value.transfers());
                    out.writeLong(value.durationMillis());
                } else if (event instanceof TableVisualEvent.CollectBets value) {
                    out.writeByte(COLLECT_BETS);
                    writeChipTransfers(out, value.transfers());
                    out.writeDouble(value.potBefore());
                    out.writeDouble(value.potAfterLanding());
                } else if (event instanceof TableVisualEvent.DealHoleCard value) {
                    out.writeByte(DEAL_HOLE_CARD);
                    writeText(out, value.nickname());
                    out.writeInt(value.slot());
                    // DealHoleCard is renderer-local: the host's own source
                    // event contains its face-up private card. An observer
                    // always receives the same deal animation with a back.
                    writeCard(out, observerHoleCard(value.card()));
                } else if (event instanceof TableVisualEvent.DealCommunityCard value) {
                    out.writeByte(DEAL_COMMUNITY_CARD);
                    out.writeInt(value.slot());
                } else if (event instanceof TableVisualEvent.RunItTwiceBoard value) {
                    out.writeByte(RUN_IT_TWICE_BOARD);
                    writeEnum(out, value.side());
                    writeText(out, value.potPrefix());
                    out.writeDouble(value.potAmount());
                    writeIntegers(out, value.redealSlots());
                } else if (event instanceof TableVisualEvent.FoldHoleCards value) {
                    out.writeByte(FOLD_HOLE_CARDS);
                    writeText(out, value.nickname());
                } else if (event instanceof TableVisualEvent.RevealCommunityCards value) {
                    out.writeByte(REVEAL_COMMUNITY_CARDS);
                    writeEnum(out, value.street());
                    out.writeInt(value.firstSlot());
                    writeCards(out, value.cards().stream()
                            .map(HotJoinVisualEventCodecV1::publicCard).toList());
                    out.writeLong(value.leadInMillis());
                } else if (event instanceof TableVisualEvent.TurnTimer value) {
                    out.writeByte(TURN_TIMER);
                    writeText(out, value.nickname());
                    out.writeLong(value.totalMillis());
                    out.writeLong(value.remainingMillis());
                    writeEnum(out, value.phase());
                } else if (event instanceof TableVisualEvent.SharedProgress value) {
                    out.writeByte(SHARED_PROGRESS);
                    writeEnum(out, value.mode());
                    out.writeInt(value.seconds());
                } else if (event instanceof TableVisualEvent.PlayerAction value) {
                    out.writeByte(PLAYER_ACTION);
                    writeText(out, value.nickname());
                    writeEnum(out, value.kind());
                    writeText(out, value.label());
                    out.writeDouble(value.amount());
                    out.writeDouble(value.contributionDelta());
                    out.writeDouble(value.stackAfter());
                    out.writeDouble(value.streetBetAfter());
                    out.writeDouble(value.potContributionAfter());
                } else if (event instanceof TableVisualEvent.Cinematic value) {
                    out.writeByte(CINEMATIC);
                    writeEnum(out, value.type());
                    writeEnum(out, value.phase());
                    writeText(out, value.nickname());
                    writeText(out, value.assetName());
                    out.writeLong(value.durationMillis());
                } else if (event instanceof TableVisualEvent.RevealHoleCards value) {
                    out.writeByte(REVEAL_HOLE_CARDS);
                    writeText(out, value.nickname());
                    writeCard(out, publicCard(value.left()));
                    writeCard(out, publicCard(value.right()));
                    writeText(out, value.handName());
                } else if (event instanceof TableVisualEvent.PartialHand value) {
                    out.writeByte(PARTIAL_HAND);
                    writeText(out, value.nickname());
                    writeText(out, value.handName());
                    out.writeBoolean(value.winner());
                    out.writeFloat(value.winPercentage());
                } else if (event instanceof TableVisualEvent.HandResult value) {
                    out.writeByte(HAND_RESULT);
                    writeText(out, value.nickname());
                    writeText(out, value.handName());
                    out.writeBoolean(value.winner());
                    writeEnum(out, value.street());
                    writeIntegers(out, value.wonPotIndexes());
                    out.writeBoolean(value.soleSurvivor());
                } else if (event instanceof TableVisualEvent.RabbitCards value) {
                    out.writeByte(RABBIT_CARDS);
                    writeCount(out, value.cards().size());
                    for (TableVisualEvent.RabbitCard card : value.cards()) {
                        out.writeInt(card.slot());
                        writeCard(out, publicCard(card.card()));
                    }
                    // A warming spectator may observe a public reveal but may
                    // never inherit the host renderer's local click target.
                    out.writeBoolean(false);
                } else if (event instanceof TableVisualEvent.RabbitResult value) {
                    out.writeByte(RABBIT_RESULT);
                    writeText(out, value.nickname());
                    out.writeDouble(value.fee());
                    out.writeDouble(value.stackAfter());
                    out.writeInt(value.requestCount());
                } else if (event instanceof TableVisualEvent.RabbitNotice value) {
                    out.writeByte(RABBIT_NOTICE);
                    writeText(out, value.nickname());
                    out.writeLong(value.durationMillis());
                } else if (event instanceof TableVisualEvent.ShowdownHighlight value) {
                    out.writeByte(SHOWDOWN_HIGHLIGHT);
                    writeText(out, value.nickname());
                    out.writeBoolean(value.enabled());
                    writeIntegers(out, value.holeCardSlots());
                    writeIntegers(out, value.communityCardSlots());
                } else if (event instanceof TableVisualEvent.Payout value) {
                    out.writeByte(PAYOUT);
                    writeText(out, value.nickname());
                    out.writeDouble(value.amount());
                    out.writeInt(value.potIndex());
                    out.writeDouble(value.stackAfter());
                    out.writeDouble(value.potAfter());
                } else if (event instanceof TableVisualEvent.PayoutBatch value) {
                    out.writeByte(PAYOUT_BATCH);
                    writeCount(out, value.transfers().size());
                    for (TableVisualEvent.PayoutBatch.Transfer transfer
                            : value.transfers()) {
                        writeText(out, transfer.nickname());
                        out.writeDouble(transfer.amount());
                        out.writeDouble(transfer.returnedAmount());
                        out.writeDouble(transfer.stackAfter());
                    }
                    out.writeDouble(value.potAfter());
                    out.writeDouble(value.investedAmountAfter());
                } else if (event instanceof TableVisualEvent.AudioCue value) {
                    out.writeByte(AUDIO_CUE);
                    writeEnum(out, value.operation());
                    writeText(out, value.resource());
                    out.writeBoolean(value.waitForCompletion());
                    out.writeBoolean(value.forceClose());
                    out.writeBoolean(value.bypassMuted());
                    out.writeBoolean(value.forceSilent());
                } else if (event instanceof TableVisualEvent.SpecialCardSound value) {
                    out.writeByte(SPECIAL_CARD_SOUND);
                    writeText(out, value.cardCode());
                } else if (event instanceof TableVisualEvent.Rebuy value) {
                    out.writeByte(REBUY);
                    writeChipTransfers(out, value.transfers());
                    out.writeLong(value.durationMillis());
                } else if (event instanceof TableVisualEvent.RebuyDecision value) {
                    out.writeByte(REBUY_DECISION);
                    writeText(out, value.nickname());
                    writeEnum(out, value.phase());
                } else if (event instanceof TableVisualEvent.InitialStackFill value) {
                    out.writeByte(INITIAL_STACK_FILL);
                    writeChipTransfers(out, value.transfers());
                    out.writeLong(value.durationMillis());
                    writeText(out, value.soundResource());
                } else if (event instanceof TableVisualEvent.SeatRoster value) {
                    out.writeByte(SEAT_ROSTER);
                    writePublicPlayers(out, value.players());
                } else if (event instanceof TableVisualEvent.PauseStatus value) {
                    out.writeByte(PAUSE_STATUS);
                    out.writeBoolean(value.paused());
                } else if (event instanceof TableVisualEvent.TelemetryStatus value) {
                    out.writeByte(TELEMETRY_STATUS);
                    writeCount(out, value.players().size());
                    for (TableVisualEvent.PlayerTelemetry player : value.players()) {
                        writeText(out, player.nickname());
                        out.writeInt(player.latency());
                        out.writeInt(player.previousLatency());
                        out.writeInt(player.reconnectionCount());
                        out.writeLong(player.measuredAtMillis());
                    }
                } else if (event instanceof TableVisualEvent.PlayerTimeout value) {
                    out.writeByte(PLAYER_TIMEOUT);
                    writeText(out, value.nickname());
                    out.writeBoolean(value.timedOut());
                } else if (event instanceof TableVisualEvent.PlayerDeparture value) {
                    out.writeByte(PLAYER_DEPARTURE);
                    writeText(out, value.nickname());
                    writeText(out, value.label());
                } else if (event instanceof TableVisualEvent.UnderTheGunStatus value) {
                    out.writeByte(UNDER_THE_GUN_STATUS);
                    writeText(out, value.nickname());
                } else if (event instanceof TableVisualEvent.SwapHoleCards value) {
                    out.writeByte(SWAP_HOLE_CARDS);
                    writeText(out, value.nickname());
                    out.writeBoolean(value.blocking());
                } else if (event instanceof TableVisualEvent.TableInfo value) {
                    out.writeByte(TABLE_INFO);
                    out.writeDouble(value.smallBlind());
                    out.writeDouble(value.bigBlind());
                    out.writeInt(value.handNumber());
                    out.writeInt(value.blindIncreaseInterval());
                    out.writeInt(value.blindIncreaseType());
                    out.writeInt(value.blindIncreaseCount());
                } else if (event instanceof TableVisualEvent.CallCost value) {
                    out.writeByte(CALL_COST);
                    writeText(out, value.text());
                    writeText(out, value.aggressorNickname());
                } else if (event instanceof TableVisualEvent.ImmediateRebuyStatus value) {
                    out.writeByte(IMMEDIATE_REBUY_STATUS);
                    writeText(out, value.nickname());
                    out.writeInt(value.amount());
                } else if (event instanceof TableVisualEvent.DeckChanged value) {
                    out.writeByte(DECK_CHANGED);
                    writeText(out, value.deck());
                } else if (event instanceof TableVisualEvent.LastHandStatus value) {
                    out.writeByte(LAST_HAND_STATUS);
                    out.writeBoolean(value.enabled());
                } else if (event instanceof TableVisualEvent.HandLimitStatus value) {
                    out.writeByte(HAND_LIMIT_STATUS);
                    out.writeInt(value.maximumHands());
                } else if (event instanceof TableVisualEvent.GameConfigurationStatus value) {
                    out.writeByte(GAME_CONFIGURATION_STATUS);
                    writeText(out, GameConfigCodecV1.encodeBase64(
                            value.configuration()));
                } else if (event instanceof TableVisualEvent.RunItTwiceLockStatus value) {
                    out.writeByte(RUN_IT_TWICE_LOCK_STATUS);
                    out.writeBoolean(value.locked());
                } else if (event instanceof TableVisualEvent.CommunicationRulesStatus value) {
                    out.writeByte(COMMUNICATION_RULES_STATUS);
                    out.writeBoolean(value.textToSpeech());
                    out.writeBoolean(value.voiceMessages());
                } else if (event instanceof TableVisualEvent.GameClock value) {
                    out.writeByte(GAME_CLOCK);
                    out.writeLong(value.playTimeSeconds());
                } else if (event instanceof TableVisualEvent.CloseTable value) {
                    out.writeByte(CLOSE_TABLE);
                    writeSummary(out, value.summary());
                    writeEnum(out, value.terminalStreet());
                } else {
                    return Optional.empty();
                }
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "Hot-join visual event is too large");
            }
            return Optional.of(Base64.getEncoder().encodeToString(payload));
        } catch (IOException impossible) {
            throw new IllegalStateException(
                    "In-memory hot-join visual encoding failed", impossible);
        }
    }

    /** Decodes one public event with the receiver's own ordered sequence. */
    public static TableVisualEvent decode(String encoded, long sequence) {
        Objects.requireNonNull(encoded, "encoded");
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Invalid hot-join visual base64", invalid);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid hot-join visual size");
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported hot-join visual version");
            }
            TableVisualEvent event = switch (in.readUnsignedByte()) {
                case ALL_IN_PAUSE -> new TableVisualEvent.AllInRunoutPause(
                        sequence, in.readLong());
                case SHUFFLE -> new TableVisualEvent.Shuffle(sequence,
                        readText(in), readEnum(in,
                                TableVisualEvent.Shuffle.Phase.values(),
                                "shuffle phase"));
                case POSITION_ROTATION -> new TableVisualEvent.PositionRotation(
                        sequence, readPositionTransfers(in), in.readLong());
                case COLLECT_BETS -> new TableVisualEvent.CollectBets(sequence,
                        readChipTransfers(in), finite(in.readDouble(), "pot before"),
                        finite(in.readDouble(), "pot after"));
                case DEAL_HOLE_CARD -> new TableVisualEvent.DealHoleCard(
                        sequence, readText(in), in.readInt(), readCard(in));
                case DEAL_COMMUNITY_CARD -> new TableVisualEvent.DealCommunityCard(
                        sequence, in.readInt());
                case RUN_IT_TWICE_BOARD -> new TableVisualEvent.RunItTwiceBoard(
                        sequence, readEnum(in,
                                TableVisualEvent.RunItTwiceBoard.Side.values(),
                                "run-it-twice side"), readText(in),
                        finite(in.readDouble(), "run-it-twice pot"),
                        readIntegers(in));
                case FOLD_HOLE_CARDS -> new TableVisualEvent.FoldHoleCards(
                        sequence, readText(in));
                case REVEAL_COMMUNITY_CARDS ->
                    new TableVisualEvent.RevealCommunityCards(sequence,
                            readEnum(in, TableSnapshot.Street.values(),
                                    "community street"),
                            in.readInt(), readCards(in), in.readLong());
                case TURN_TIMER -> new TableVisualEvent.TurnTimer(sequence,
                        readText(in), in.readLong(), in.readLong(),
                        readEnum(in, TableVisualEvent.TurnTimer.Phase.values(),
                                "turn phase"));
                case SHARED_PROGRESS -> new TableVisualEvent.SharedProgress(
                        sequence, readEnum(in,
                                TableVisualEvent.SharedProgress.Mode.values(),
                                "progress mode"), in.readInt());
                case PLAYER_ACTION -> new TableVisualEvent.PlayerAction(sequence,
                        readText(in), readEnum(in,
                                TableVisualEvent.PlayerAction.ActionKind.values(),
                                "action kind"), readText(in),
                        finite(in.readDouble(), "action amount"),
                        finite(in.readDouble(), "action contribution"),
                        finite(in.readDouble(), "action stack"),
                        finite(in.readDouble(), "action street bet"),
                        finite(in.readDouble(), "action pot contribution"));
                case CINEMATIC -> new TableVisualEvent.Cinematic(sequence,
                        readEnum(in, TableVisualEvent.Cinematic.Type.values(),
                                "cinematic type"),
                        readEnum(in, TableVisualEvent.Cinematic.Phase.values(),
                                "cinematic phase"),
                        readText(in), readText(in), in.readLong());
                case REVEAL_HOLE_CARDS -> new TableVisualEvent.RevealHoleCards(
                        sequence, readText(in), readCard(in), readCard(in),
                        readText(in));
                case PARTIAL_HAND -> new TableVisualEvent.PartialHand(sequence,
                        readText(in), readText(in), in.readBoolean(),
                        finite(in.readFloat(), "win percentage"));
                case HAND_RESULT -> new TableVisualEvent.HandResult(sequence,
                        readText(in), readText(in), in.readBoolean(),
                        readEnum(in, TableSnapshot.Street.values(),
                                "result street"),
                        readIntegers(in), in.readBoolean());
                case RABBIT_CARDS -> readRabbitCards(in, sequence);
                case RABBIT_RESULT -> new TableVisualEvent.RabbitResult(sequence,
                        readText(in), finite(in.readDouble(), "rabbit fee"),
                        finite(in.readDouble(), "rabbit stack"), in.readInt());
                case RABBIT_NOTICE -> new TableVisualEvent.RabbitNotice(sequence,
                        readText(in), in.readLong());
                case SHOWDOWN_HIGHLIGHT ->
                    new TableVisualEvent.ShowdownHighlight(sequence,
                            readText(in), in.readBoolean(), readIntegers(in),
                            readIntegers(in));
                case PAYOUT -> new TableVisualEvent.Payout(sequence,
                        readText(in), finite(in.readDouble(), "payout amount"),
                        in.readInt(), finite(in.readDouble(), "payout stack"),
                        finite(in.readDouble(), "payout pot"));
                case PAYOUT_BATCH -> readPayoutBatch(in, sequence);
                case AUDIO_CUE -> new TableVisualEvent.AudioCue(sequence,
                        readEnum(in, TableVisualEvent.AudioCue.Operation.values(),
                                "audio operation"),
                        readText(in), in.readBoolean(), in.readBoolean(),
                        in.readBoolean(), in.readBoolean());
                case SPECIAL_CARD_SOUND -> new TableVisualEvent.SpecialCardSound(
                        sequence, readText(in));
                case REBUY -> new TableVisualEvent.Rebuy(sequence,
                        readChipTransfers(in), in.readLong());
                case REBUY_DECISION -> new TableVisualEvent.RebuyDecision(
                        sequence, readText(in),
                        readEnum(in,
                                TableVisualEvent.RebuyDecision.Phase.values(),
                                "rebuy phase"));
                case INITIAL_STACK_FILL -> new TableVisualEvent.InitialStackFill(
                        sequence, readChipTransfers(in), in.readLong(),
                        readText(in));
                case SEAT_ROSTER -> new TableVisualEvent.SeatRoster(sequence,
                        readPlayers(in));
                case PAUSE_STATUS -> new TableVisualEvent.PauseStatus(sequence,
                        in.readBoolean());
                case TELEMETRY_STATUS -> new TableVisualEvent.TelemetryStatus(
                        sequence, readTelemetry(in));
                case PLAYER_TIMEOUT -> new TableVisualEvent.PlayerTimeout(
                        sequence, readText(in), in.readBoolean());
                case PLAYER_DEPARTURE -> new TableVisualEvent.PlayerDeparture(
                        sequence, readText(in), readText(in));
                case UNDER_THE_GUN_STATUS ->
                    new TableVisualEvent.UnderTheGunStatus(sequence,
                            readText(in));
                case SWAP_HOLE_CARDS -> new TableVisualEvent.SwapHoleCards(
                        sequence, readText(in), in.readBoolean());
                case TABLE_INFO -> new TableVisualEvent.TableInfo(sequence,
                        finite(in.readDouble(), "small blind"),
                        finite(in.readDouble(), "big blind"), in.readInt(),
                        in.readInt(), in.readInt(), in.readInt());
                case CALL_COST -> new TableVisualEvent.CallCost(sequence,
                        readText(in), readText(in));
                case IMMEDIATE_REBUY_STATUS ->
                    new TableVisualEvent.ImmediateRebuyStatus(sequence,
                            readText(in), in.readInt());
                case DECK_CHANGED -> new TableVisualEvent.DeckChanged(sequence,
                        readText(in));
                case LAST_HAND_STATUS -> new TableVisualEvent.LastHandStatus(
                        sequence, in.readBoolean());
                case HAND_LIMIT_STATUS -> new TableVisualEvent.HandLimitStatus(
                        sequence, in.readInt());
                case GAME_CONFIGURATION_STATUS ->
                    new TableVisualEvent.GameConfigurationStatus(sequence,
                            readConfiguration(in));
                case RUN_IT_TWICE_LOCK_STATUS ->
                    new TableVisualEvent.RunItTwiceLockStatus(sequence,
                            in.readBoolean());
                case COMMUNICATION_RULES_STATUS ->
                    new TableVisualEvent.CommunicationRulesStatus(sequence,
                            in.readBoolean(), in.readBoolean());
                case GAME_CLOCK -> new TableVisualEvent.GameClock(sequence,
                        in.readLong());
                case CLOSE_TABLE -> new TableVisualEvent.CloseTable(sequence,
                        readSummary(in), readEnum(in,
                                TableSnapshot.Street.values(),
                                "terminal street"));
                default -> throw new IllegalArgumentException(
                        "Unsupported hot-join visual event");
            };
            if (in.available() != 0) {
                throw new IllegalArgumentException(
                        "Trailing hot-join visual data");
            }
            return event;
        } catch (IOException invalid) {
            throw new IllegalArgumentException(
                    "Truncated hot-join visual event", invalid);
        }
    }

    private static TableVisualEvent.RabbitCards readRabbitCards(
            DataInputStream in, long sequence) throws IOException {
        int count = readCount(in);
        ArrayList<TableVisualEvent.RabbitCard> cards = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cards.add(new TableVisualEvent.RabbitCard(in.readInt(),
                    readCard(in)));
        }
        return new TableVisualEvent.RabbitCards(sequence, cards,
                in.readBoolean());
    }

    private static TableVisualEvent.PayoutBatch readPayoutBatch(
            DataInputStream in, long sequence) throws IOException {
        int count = readCount(in);
        ArrayList<TableVisualEvent.PayoutBatch.Transfer> transfers
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            transfers.add(new TableVisualEvent.PayoutBatch.Transfer(
                    readText(in), finite(in.readDouble(), "payout amount"),
                    finite(in.readDouble(), "returned amount"),
                    finite(in.readDouble(), "payout stack")));
        }
        return new TableVisualEvent.PayoutBatch(sequence, transfers,
                finite(in.readDouble(), "payout pot"),
                finite(in.readDouble(), "invested amount"));
    }

    private static List<TableVisualEvent.PlayerTelemetry> readTelemetry(
            DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<TableVisualEvent.PlayerTelemetry> players
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            players.add(new TableVisualEvent.PlayerTelemetry(readText(in),
                    in.readInt(), in.readInt(), in.readInt(), in.readLong()));
        }
        return List.copyOf(players);
    }

    private static GameConfigCodecV1.Configuration readConfiguration(
            DataInputStream in) throws IOException {
        GameConfigCodecV1.Result decoded = GameConfigCodecV1.decodeBase64(
                readText(in));
        if (!decoded.isOk()) {
            throw new IOException("Invalid relayed game configuration: "
                    + decoded.error());
        }
        return decoded.value();
    }

    private static void writeSummary(DataOutputStream out,
            TableSessionSummary summary) throws IOException {
        writeText(out, summary.localNickname());
        out.writeInt(summary.handCount());
        out.writeLong(summary.durationSeconds());
        out.writeLong(summary.endedAtMillis());
        writeEnum(out, summary.reason());
        writeCount(out, summary.balances().size());
        for (TableSessionSummary.PlayerBalance balance : summary.balances()) {
            writeText(out, balance.nickname());
            out.writeDouble(balance.finalStack());
            out.writeDouble(balance.totalBuyin());
            out.writeInt(balance.rebuyCount());
        }
    }

    private static TableSessionSummary readSummary(DataInputStream in)
            throws IOException {
        String localNickname = readText(in);
        int handCount = in.readInt();
        long durationSeconds = in.readLong();
        long endedAtMillis = in.readLong();
        TableSessionSummary.CloseReason reason = readEnum(in,
                TableSessionSummary.CloseReason.values(), "close reason");
        int count = readCount(in);
        ArrayList<TableSessionSummary.PlayerBalance> balances
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            balances.add(new TableSessionSummary.PlayerBalance(readText(in),
                    finite(in.readDouble(), "final stack"),
                    finite(in.readDouble(), "total buy-in"), in.readInt()));
        }
        return new TableSessionSummary(localNickname, handCount,
                durationSeconds, endedAtMillis, reason, balances);
    }

    private static void writePositionTransfers(DataOutputStream out,
            List<TableVisualEvent.PositionTransfer> transfers)
            throws IOException {
        writeCount(out, transfers.size());
        for (TableVisualEvent.PositionTransfer transfer : transfers) {
            writeText(out, transfer.fromNickname());
            writeText(out, transfer.toNickname());
            writeEnum(out, transfer.position());
            out.writeBoolean(transfer.fromCenter());
        }
    }

    private static List<TableVisualEvent.PositionTransfer>
            readPositionTransfers(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<TableVisualEvent.PositionTransfer> transfers
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            transfers.add(new TableVisualEvent.PositionTransfer(readText(in),
                    readText(in),
                    readEnum(in, TableSnapshot.Position.values(), "position"),
                    in.readBoolean()));
        }
        return List.copyOf(transfers);
    }

    private static void writeChipTransfers(DataOutputStream out,
            List<TableVisualEvent.ChipTransfer> transfers) throws IOException {
        writeCount(out, transfers.size());
        for (TableVisualEvent.ChipTransfer transfer : transfers) {
            writeText(out, transfer.nickname());
            out.writeDouble(transfer.amount());
            out.writeDouble(transfer.stackAfter());
            out.writeDouble(transfer.streetBetAfter());
            out.writeDouble(transfer.potContributionAfter());
        }
    }

    private static List<TableVisualEvent.ChipTransfer> readChipTransfers(
            DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<TableVisualEvent.ChipTransfer> transfers
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            transfers.add(new TableVisualEvent.ChipTransfer(readText(in),
                    finite(in.readDouble(), "chip amount"),
                    finite(in.readDouble(), "chip stack"),
                    finite(in.readDouble(), "chip street bet"),
                    finite(in.readDouble(), "chip contribution")));
        }
        return List.copyOf(transfers);
    }

    /**
     * A roster change is an ordinary table event and must remain one for a
     * warming client too.  Rebuilding it from that client's recovery model can
     * briefly be stale while a second newcomer is joining, so the host relays
     * its canonical roster through the same ordered presentation stream.
     * Pocket slots are retained as backs but their values never cross this
     * public channel.
     */
    private static void writePublicPlayers(DataOutputStream out,
            List<TableSnapshot.PlayerSnapshot> players) throws IOException {
        writeCount(out, players.size());
        for (TableSnapshot.PlayerSnapshot player : players) {
            writeText(out, player.nickname());
            out.writeDouble(player.stack());
            out.writeDouble(player.streetBet());
            out.writeDouble(player.potContribution());
            out.writeBoolean(player.active());
            out.writeBoolean(player.spectator());
            out.writeBoolean(player.exited());
            out.writeBoolean(player.timedOut());
            out.writeInt(player.latency());
            out.writeInt(player.previousLatency());
            out.writeInt(player.reconnectionCount());
            out.writeLong(player.telemetryAt());
            out.writeBoolean(player.winner());
            out.writeBoolean(player.underTheGun());
            writeEnum(out, player.position());
            writeText(out, player.lastAction());
            writeText(out, player.handName());
            writeCards(out, player.holeCards().stream()
                    .map(HotJoinVisualEventCodecV1::publicCard).toList());
            out.writeInt(player.buyIn());
            out.writeInt(player.rebuyCount());
            out.writeBoolean(player.warming());
        }
    }

    private static List<TableSnapshot.PlayerSnapshot> readPlayers(
            DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<TableSnapshot.PlayerSnapshot> players
                = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            players.add(new TableSnapshot.PlayerSnapshot(readText(in),
                    finite(in.readDouble(), "player stack"),
                    finite(in.readDouble(), "player street bet"),
                    finite(in.readDouble(), "player contribution"),
                    in.readBoolean(), in.readBoolean(), in.readBoolean(),
                    in.readBoolean(), in.readInt(), in.readInt(), in.readInt(),
                    in.readLong(), in.readBoolean(), in.readBoolean(),
                    readEnum(in, TableSnapshot.Position.values(), "position"),
                    readText(in), readText(in), readCards(in), in.readInt(),
                    in.readInt(), in.readBoolean()));
        }
        return List.copyOf(players);
    }

    private static TableSnapshot.CardSnapshot publicCard(
            TableSnapshot.CardSnapshot card) {
        Objects.requireNonNull(card, "card");
        return card.faceUp() ? card : new TableSnapshot.CardSnapshot("", false,
                card.disabled(), card.visible());
    }

    private static TableSnapshot.CardSnapshot observerHoleCard(
            TableSnapshot.CardSnapshot card) {
        Objects.requireNonNull(card, "card");
        return new TableSnapshot.CardSnapshot("", false, card.disabled(),
                card.visible());
    }

    private static void writeCards(DataOutputStream out,
            List<TableSnapshot.CardSnapshot> cards) throws IOException {
        writeCount(out, cards.size());
        for (TableSnapshot.CardSnapshot card : cards) writeCard(out, card);
    }

    private static List<TableSnapshot.CardSnapshot> readCards(
            DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<TableSnapshot.CardSnapshot> cards = new ArrayList<>(count);
        for (int index = 0; index < count; index++) cards.add(readCard(in));
        return List.copyOf(cards);
    }

    private static void writeCard(DataOutputStream out,
            TableSnapshot.CardSnapshot card) throws IOException {
        writeText(out, card.code());
        out.writeBoolean(card.faceUp());
        out.writeBoolean(card.disabled());
        out.writeBoolean(card.visible());
    }

    private static TableSnapshot.CardSnapshot readCard(DataInputStream in)
            throws IOException {
        return new TableSnapshot.CardSnapshot(readText(in), in.readBoolean(),
                in.readBoolean(), in.readBoolean());
    }

    private static void writeIntegers(DataOutputStream out,
            List<Integer> values) throws IOException {
        writeCount(out, values.size());
        for (Integer value : values) out.writeInt(value);
    }

    private static List<Integer> readIntegers(DataInputStream in)
            throws IOException {
        int count = readCount(in);
        ArrayList<Integer> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(in.readInt());
        return List.copyOf(values);
    }

    private static void writeCount(DataOutputStream out, int count)
            throws IOException {
        if (count < 0 || count > MAX_LIST) {
            throw new IllegalArgumentException("Invalid hot-join visual list");
        }
        out.writeInt(count);
    }

    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_LIST) {
            throw new IllegalArgumentException("Invalid hot-join visual list");
        }
        return count;
    }

    private static void writeText(DataOutputStream out, String value)
            throws IOException {
        byte[] bytes = Objects.requireNonNullElse(value, "")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Hot-join visual text field is too large");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Invalid hot-join visual text size");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("Truncated text");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeEnum(DataOutputStream out, Enum<?> value)
            throws IOException {
        out.writeInt(Objects.requireNonNull(value, "value").ordinal());
    }

    private static <T> T readEnum(DataInputStream in, T[] values,
            String label) throws IOException {
        int ordinal = in.readInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid hot-join " + label);
        }
        return values[ordinal];
    }

    private static double finite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Non-finite hot-join " + label);
        }
        return value;
    }

    private static float finite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Non-finite hot-join " + label);
        }
        return value;
    }
}
