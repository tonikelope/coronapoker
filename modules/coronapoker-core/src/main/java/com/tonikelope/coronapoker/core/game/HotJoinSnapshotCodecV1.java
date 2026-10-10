/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.table.TableSnapshot;
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

/** Compact, bounded public table-state bootstrap for a live newcomer. */
public final class HotJoinSnapshotCodecV1 {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_TEXT_BYTES = 16 * 1024;

    private HotJoinSnapshotCodecV1() {
    }

    public static String encode(TableSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                out.writeLong(snapshot.revision());
                out.writeInt(snapshot.street().ordinal());
                out.writeDouble(snapshot.pot());
                writeText(out, snapshot.currentTurnNickname());
                out.writeBoolean(snapshot.paused());
                out.writeInt(snapshot.players().size());
                for (TableSnapshot.PlayerSnapshot player : snapshot.players()) {
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
                    out.writeInt(player.position().ordinal());
                    out.writeInt(player.decision().ordinal());
                    out.writeInt(player.actionKind().ordinal());
                    writeText(out, player.lastAction());
                    writeText(out, player.handName());
                    writeCards(out, player.holeCards());
                    out.writeInt(player.buyIn());
                    out.writeInt(player.rebuyCount());
                    out.writeBoolean(player.warming());
                    writePlayerPresentation(out, player.presentation());
                }
                writeCards(out, snapshot.communityCards());
                writeText(out, snapshot.presentation().potPrefix());
                out.writeInt(snapshot.presentation()
                        .sharedProgressRemainingSeconds());
                writeIntegers(out, snapshot.presentation().rabbitCardSlots());
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_BYTES) {
                throw new IllegalArgumentException("Hot-join snapshot is too large");
            }
            return Base64.getEncoder().encodeToString(encoded);
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory snapshot encoding failed", impossible);
        }
    }

    public static TableSnapshot decode(String encoded, String localNickname) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(localNickname, "localNickname");
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid hot-join snapshot base64", invalid);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid hot-join snapshot size");
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported hot-join snapshot version");
            }
            long revision = in.readLong();
            TableSnapshot.Street street = enumValue(TableSnapshot.Street.values(),
                    in.readInt(), "street");
            double pot = finite(in.readDouble(), "pot");
            String currentTurn = readText(in);
            boolean paused = in.readBoolean();
            int playerCount = boundedCount(in.readInt(), 1, 10, "players");
            ArrayList<TableSnapshot.PlayerSnapshot> players
                    = new ArrayList<>(playerCount);
            for (int index = 0; index < playerCount; index++) {
                String nickname = readText(in);
                double stack = finite(in.readDouble(), "stack");
                double streetBet = finite(in.readDouble(), "street bet");
                double contribution = finite(in.readDouble(), "contribution");
                boolean active = in.readBoolean();
                boolean spectator = in.readBoolean();
                boolean exited = in.readBoolean();
                boolean timedOut = in.readBoolean();
                int latency = in.readInt();
                int previousLatency = in.readInt();
                int reconnections = in.readInt();
                long telemetryAt = in.readLong();
                boolean winner = in.readBoolean();
                boolean underTheGun = in.readBoolean();
                TableSnapshot.Position position = enumValue(
                        TableSnapshot.Position.values(), in.readInt(), "position");
                TableSnapshot.Decision decision = enumValue(
                        TableSnapshot.Decision.values(), in.readInt(), "decision");
                TableSnapshot.ActionKind actionKind = enumValue(
                        TableSnapshot.ActionKind.values(), in.readInt(), "action kind");
                String lastAction = readText(in);
                String handName = readText(in);
                List<TableSnapshot.CardSnapshot> cards = readCards(in, 2);
                int buyIn = in.readInt();
                int rebuyCount = in.readInt();
                boolean warming = in.readBoolean();
                TableSnapshot.PlayerPresentation presentation
                        = readPlayerPresentation(in);
                players.add(new TableSnapshot.PlayerSnapshot(nickname, stack,
                        streetBet, contribution, active, spectator, exited,
                        timedOut, latency, previousLatency, reconnections,
                        telemetryAt, winner, underTheGun, position, decision,
                        actionKind, lastAction,
                        handName, cards, buyIn, rebuyCount, warming,
                        presentation));
            }
            List<TableSnapshot.CardSnapshot> board = readCards(in, 5);
            TableSnapshot.TablePresentation presentation
                    = new TableSnapshot.TablePresentation(readText(in),
                            boundedCount(in.readInt(), 0, 86_400,
                                    "shared progress"),
                            readIntegers(in, 5, "rabbit card slots"));
            if (in.available() != 0) {
                throw new IllegalArgumentException("Trailing hot-join snapshot data");
            }
            return new TableSnapshot(revision, localNickname, street, pot,
                    currentTurn, paused, players, board, presentation);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Truncated hot-join snapshot", invalid);
        }
    }

    private static void writeCards(DataOutputStream out,
            List<TableSnapshot.CardSnapshot> cards) throws IOException {
        out.writeInt(cards.size());
        for (TableSnapshot.CardSnapshot card : cards) {
            writeText(out, card.code());
            out.writeBoolean(card.faceUp());
            out.writeBoolean(card.disabled());
            out.writeBoolean(card.visible());
        }
    }

    private static List<TableSnapshot.CardSnapshot> readCards(
            DataInputStream in, int maximum) throws IOException {
        int count = boundedCount(in.readInt(), 0, maximum, "cards");
        ArrayList<TableSnapshot.CardSnapshot> cards = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cards.add(new TableSnapshot.CardSnapshot(readText(in),
                    in.readBoolean(), in.readBoolean(), in.readBoolean()));
        }
        return List.copyOf(cards);
    }

    private static void writePlayerPresentation(DataOutputStream out,
            TableSnapshot.PlayerPresentation presentation)
            throws IOException {
        out.writeBoolean(presentation.showingCards());
        out.writeBoolean(presentation.partialHand());
        out.writeFloat(presentation.partialWinPercentage());
        out.writeBoolean(presentation.resultResolved());
        writeText(out, presentation.publicHandName());
        writeIntegers(out, presentation.wonPotIndexes());
        out.writeBoolean(presentation.returnedSidePot());
        out.writeBoolean(presentation.showdownHighlightEnabled());
        writeIntegers(out, presentation.winningHoleCardSlots());
        writeIntegers(out, presentation.winningCommunityCardSlots());
        out.writeInt(presentation.rebuyPhase().ordinal());
        out.writeInt(presentation.immediateRebuyAmount());
        writeText(out, presentation.publicActionLabel());
    }

    private static TableSnapshot.PlayerPresentation readPlayerPresentation(
            DataInputStream in) throws IOException {
        boolean showingCards = in.readBoolean();
        boolean partialHand = in.readBoolean();
        float percentage = in.readFloat();
        boolean resolved = in.readBoolean();
        String publicHandName = readText(in);
        List<Integer> pots = readIntegers(in, 32, "won pot indexes");
        boolean returnedSidePot = in.readBoolean();
        boolean highlighted = in.readBoolean();
        List<Integer> holeSlots = readIntegers(in, 2,
                "winning hole-card slots");
        List<Integer> communitySlots = readIntegers(in, 5,
                "winning community-card slots");
        TableSnapshot.RebuyPhase rebuyPhase = enumValue(
                TableSnapshot.RebuyPhase.values(), in.readInt(),
                "rebuy phase");
        int immediateRebuyAmount = boundedCount(in.readInt(), 0,
                Integer.MAX_VALUE, "immediate rebuy amount");
        String publicActionLabel = readText(in);
        return new TableSnapshot.PlayerPresentation(showingCards,
                partialHand, percentage, resolved, publicHandName, pots,
                returnedSidePot, highlighted, holeSlots, communitySlots,
                rebuyPhase, immediateRebuyAmount, publicActionLabel);
    }

    private static void writeIntegers(DataOutputStream out,
            List<Integer> values) throws IOException {
        out.writeInt(values.size());
        for (Integer value : values) out.writeInt(value);
    }

    private static List<Integer> readIntegers(DataInputStream in, int maximum,
            String label) throws IOException {
        int count = boundedCount(in.readInt(), 0, maximum, label);
        ArrayList<Integer> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(in.readInt());
        return List.copyOf(values);
    }

    private static void writeText(DataOutputStream out, String value)
            throws IOException {
        byte[] bytes = Objects.requireNonNullElse(value, "")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Hot-join text field is too large");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = boundedCount(in.readInt(), 0, MAX_TEXT_BYTES, "text");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("Truncated text field");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int boundedCount(int value, int minimum, int maximum,
            String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid hot-join " + label);
        }
        return value;
    }

    private static double finite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite hot-join " + label);
        }
        return value;
    }

    private static <T> T enumValue(T[] values, int ordinal, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid hot-join " + label);
        }
        return values[ordinal];
    }
}
