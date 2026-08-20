/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** Closed registry for the single supported GAME protocol version. */
public enum GameCommandType {
    ACTION(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    ACTIONDATA(Direction.HOST_TO_CLIENT),
    BOTBALRULE(Direction.HOST_TO_CLIENT),
    BOTREBUYRULE(Direction.HOST_TO_CLIENT),
    BUYIN(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    COMM_REVEAL(Direction.HOST_TO_CLIENT),
    DECK_CASCADE_PROOF(Direction.CLIENT_TO_HOST),
    DECK_CASCADE_REQ(Direction.HOST_TO_CLIENT),
    DECK_CASCADE_RESP(Direction.CLIENT_TO_HOST),
    DECK_ROTATION_REQ(Direction.HOST_TO_CLIENT),
    DECK_ROTATION_RESP(Direction.CLIENT_TO_HOST),
    DELUSER(Direction.HOST_TO_CLIENT),
    DUALLOCK_BUNDLE(Direction.HOST_TO_CLIENT),
    EXIT(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    FLOP_PIECE(Direction.HOST_TO_CLIENT),
    GAMECONFIG(Direction.HOST_TO_CLIENT),
    GAMEINFO(Direction.HOST_TO_CLIENT),
    HAND_READY(Direction.CLIENT_TO_HOST),
    HANDVERIFY(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    H_CHECK(Direction.HOST_TO_CLIENT),
    INIT(Direction.HOST_TO_CLIENT),
    IWTSTH(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    IWTSTHSHOW(Direction.HOST_TO_CLIENT),
    IWTSTHRULE(Direction.HOST_TO_CLIENT),
    LASTHAND(Direction.HOST_TO_CLIENT),
    MAXHANDS(Direction.HOST_TO_CLIENT),
    MEGAPACKET(Direction.HOST_TO_CLIENT),
    MISDEAL(Direction.HOST_TO_CLIENT),
    NEWUSER(Direction.HOST_TO_CLIENT),
    PAUSE(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    POCKET_CARDS(Direction.HOST_TO_CLIENT),
    POCKET_DEFERRED(Direction.HOST_TO_CLIENT),
    POSITIONS(Direction.HOST_TO_CLIENT),
    POTCARDS(Direction.HOST_TO_CLIENT),
    RABBIT_AUTH(Direction.HOST_TO_CLIENT),
    RABBIT_FLOP_PIECE(Direction.HOST_TO_CLIENT),
    RABBIT_REQ(Direction.CLIENT_TO_HOST),
    RABBIT_RIVER_PIECE(Direction.HOST_TO_CLIENT),
    RABBIT_TURN_PIECE(Direction.HOST_TO_CLIENT),
    RABBITRULE(Direction.HOST_TO_CLIENT),
    REBUY(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    REBUYDENIED(Direction.HOST_TO_CLIENT),
    REBUYNOW(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    RECOVERDATA(Direction.HOST_TO_CLIENT),
    REQ_SHOWDOWN_KEY(Direction.HOST_TO_CLIENT),
    REQ_SRA_UNLOCK_CHAIN(Direction.HOST_TO_CLIENT),
    RESP_SHOWDOWN_KEY(Direction.CLIENT_TO_HOST),
    RESP_SRA_UNLOCK_CHAIN(Direction.CLIENT_TO_HOST),
    RIT_VOTE_CLOSE(Direction.HOST_TO_CLIENT),
    RIT_VOTE_REQ(Direction.HOST_TO_CLIENT),
    RIT_VOTE_RESP(Direction.CLIENT_TO_HOST),
    RIT_VOTE_TALLY(Direction.HOST_TO_CLIENT),
    RIT2_FLOP_PIECE(Direction.HOST_TO_CLIENT),
    RIT2_RIVER_PIECE(Direction.HOST_TO_CLIENT),
    RIT2_TURN_PIECE(Direction.HOST_TO_CLIENT),
    RUNITWICERULE(Direction.HOST_TO_CLIENT),
    SEATS(Direction.HOST_TO_CLIENT),
    SEAT_COMMIT(Direction.CLIENT_TO_HOST),
    SEAT_COMMITS(Direction.HOST_TO_CLIENT),
    SEAT_DRAW_BEGIN(Direction.HOST_TO_CLIENT),
    SEAT_REVEAL(Direction.CLIENT_TO_HOST),
    SEAT_REVEALS(Direction.HOST_TO_CLIENT),
    SERVEREXIT(Direction.HOST_TO_CLIENT),
    SERVEREXITRECOVER(Direction.HOST_TO_CLIENT),
    SHOWCARDS(Direction.CLIENT_TO_HOST, Direction.HOST_TO_CLIENT),
    SHUFFLE_TURN(Direction.HOST_TO_CLIENT),
    SHUFFLE_TURN_END(Direction.HOST_TO_CLIENT),
    START_SRA_CASCADE(Direction.HOST_TO_CLIENT),
    STRADDLE_DECISION(Direction.HOST_TO_CLIENT),
    STRADDLE_RESP(Direction.CLIENT_TO_HOST),
    STRADDLE_RESULT(Direction.HOST_TO_CLIENT),
    TELEMETRY(Direction.HOST_TO_CLIENT),
    TIMEOUT(Direction.HOST_TO_CLIENT),
    TTS(Direction.HOST_TO_CLIENT),
    TURN_PIECE(Direction.HOST_TO_CLIENT),
    UPDATEBLINDS(Direction.HOST_TO_CLIENT),
    USERSLIST(Direction.HOST_TO_CLIENT),
    VOICEMSGRULE(Direction.HOST_TO_CLIENT),
    YOUARELATE(Direction.HOST_TO_CLIENT),
    RIVER_PIECE(Direction.HOST_TO_CLIENT);

    public enum Direction { CLIENT_TO_HOST, HOST_TO_CLIENT }

    private static final Map<String, GameCommandType> FROM_CLIENT;
    private static final Map<String, GameCommandType> FROM_HOST;

    static {
        Map<String, GameCommandType> client = new HashMap<>();
        Map<String, GameCommandType> host = new HashMap<>();
        for (GameCommandType type : values()) {
            if (type.directions.contains(Direction.CLIENT_TO_HOST)) client.put(type.name(), type);
            if (type.directions.contains(Direction.HOST_TO_CLIENT)) host.put(type.name(), type);
        }
        FROM_CLIENT = Collections.unmodifiableMap(client);
        FROM_HOST = Collections.unmodifiableMap(host);
    }

    private final EnumSet<Direction> directions;

    GameCommandType(Direction first, Direction... rest) {
        directions = EnumSet.of(first, rest);
    }

    public static GameCommandType from(Direction direction, String wireName) {
        if (wireName == null) return null;
        return (direction == Direction.CLIENT_TO_HOST ? FROM_CLIENT : FROM_HOST).get(wireName);
    }

    public static int registeredCount(Direction direction) {
        return (direction == Direction.CLIENT_TO_HOST ? FROM_CLIENT : FROM_HOST).size();
    }
}
