package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.RecoveryBalanceReconciler;
import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Renderer-neutral loader for the latest locally recoverable game. */
public final class RecoverableGameRepository {

    private static final Set<String> SETTINGS_KEYS = Set.of(
            "IWTSTH", "RABBIT", "DIFFICULTY", "BLIND_CAP",
            "REBUY_LIMIT", "BOT_REBUY", "BOTBAL", "RUNITWICE",
            "VOICEMSG", "TTS", "FIXED_BUYIN", "BLINDS", "BMINBB",
            "BMAXBB", "RBCAP", "ANTE", "STRADDLE", "MANOS",
            "THINKT", "THINKON", "SHOWDOWN");

    private static final String LATEST_LOCAL = "SELECT id,start,server,buyin,"
            + "round(sb,2) AS sb,blinds_time,blinds_time_type,rebuy,"
            + "recover_settings FROM game WHERE (ugi IS NOT NULL AND local = 1) "
            + "ORDER BY start DESC LIMIT 1";

    private final DatabaseService database;

    public RecoverableGameRepository(DatabaseService database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Performs one short SQLite read; callers choose the worker thread. */
    public Optional<RecoverableGame> latestLocal() throws Exception {
        synchronized (database) {
            try (Statement statement = database.connection().createStatement()) {
                statement.setQueryTimeout(30);
                try (ResultSet row = statement.executeQuery(LATEST_LOCAL)) {
                    if (!row.next()) return Optional.empty();
                    Map<String, String> recovered = parseSettings(
                            row.getString("recover_settings"));
                    NewGameTableDraft.Settings settings = decodeSettings(recovered,
                            row.getInt("buyin"), row.getDouble("sb"),
                            row.getInt("blinds_time"),
                            row.getInt("blinds_time_type"),
                            row.getBoolean("rebuy"));
                    return Optional.of(new RecoverableGame(row.getInt("id"),
                            row.getLong("start"), row.getString("server"),
                            settings, one(recovered, "VOICEMSG"),
                            one(recovered, "TTS")));
                }
            }
        }
    }

    /**
     * Returns the solvent bots that belonged to the latest persisted hand.
     *
     * <p>Swing keeps surviving bots in its waiting-room object when a running
     * game is stopped for recovery.  The native GDX lobby is reconstructed
     * from scratch, so it must rebuild that same bot subset from the canonical
     * hand roster and opening balances before humans reconnect. A bot that
     * busted at the previous boundary can still appear in the next open-hand
     * roster, but its persisted stack is zero; requiring both membership and a
     * positive stack prevents restoring that dead seat. It therefore remains
     * available to be explicitly re-added with the same correlated
     * {@code CoronaBot$n} numbering.</p>
     */
    public List<String> activeBotNicknames(int gameId) throws Exception {
        if (gameId <= 0) {
            throw new IllegalArgumentException("gameId must be positive");
        }
        String sql = "SELECT h.preflop_players,b.player,b.stack FROM hand h "
                + "JOIN balance b ON b.id_hand=h.id WHERE h.id_game=? "
                + "AND h.id=(SELECT MAX(id) FROM hand WHERE id_game=?)";
        synchronized (database) {
            try (PreparedStatement statement
                    = database.connection().prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setInt(1, gameId);
                statement.setInt(2, gameId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return List.of();
                    Set<String> roster = RecoveryBalanceReconciler.decodeRoster(
                            row.getString("preflop_players"));
                    List<String> bots = new ArrayList<>();
                    do {
                        String nickname = row.getString("player");
                        if (row.getDouble("stack") > 0.0d
                                && roster.contains(nickname)
                                && isBotNickname(nickname)) {
                            bots.add(nickname);
                        }
                    } while (row.next());
                    bots.sort(RecoverableGameRepository::compareBotNicknames);
                    return List.copyOf(bots);
                }
            }
        }
    }

    private static boolean isBotNickname(String nickname) {
        if (nickname == null || !nickname.startsWith("CoronaBot$")) {
            return false;
        }
        try {
            return Integer.parseInt(nickname.substring("CoronaBot$".length())) > 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static int compareBotNicknames(String left, String right) {
        int leftNumber = Integer.parseInt(left.substring("CoronaBot$".length()));
        int rightNumber = Integer.parseInt(right.substring("CoronaBot$".length()));
        return Integer.compare(leftNumber, rightNumber);
    }

    static NewGameTableDraft.Settings decodeSettings(String serialized,
            int buyin, double smallBlind, int blindInterval,
            int blindIntervalType, boolean rebuy) {
        return decodeSettings(parseSettings(serialized), buyin, smallBlind,
                blindInterval, blindIntervalType, rebuy);
    }

    /** Serializes the exact recovery schema shared with the Swing frontend. */
    public static String encodeSettings(
            GameConfigCodecV1.Configuration configuration,
            NewGameTableDraft.BotDifficulty difficulty,
            boolean voiceMessages, boolean textToSpeech) {
        GameConfigCodecV1.Configuration value
                = GameConfigCodecV1.requireValid(configuration);
        Objects.requireNonNull(difficulty, "difficulty");
        String levels = value.blindStructure().isEmpty() ? ""
                : value.blindStructure().stream()
                        .map(level -> money(level.smallBlind()) + "/"
                        + money(level.bigBlind()))
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
        return "IWTSTH=" + bit(value.iwtsth())
                + "#RABBIT=" + value.rabbitHunting()
                + "#DIFFICULTY=" + difficulty.name()
                + "#BLIND_CAP=" + money(value.blindCap())
                + "#REBUY_LIMIT=" + value.rebuyLimit()
                + "#BOT_REBUY=" + bit(value.botRebuy())
                + "#BOTBAL=" + bit(value.botBalanceToHumans())
                + "#RUNITWICE=" + bit(value.runItTwice())
                + "#VOICEMSG=" + bit(voiceMessages)
                + "#TTS=" + bit(textToSpeech)
                + "#FIXED_BUYIN=" + bit(value.fixedBuyin())
                + "#BLINDS=" + levels
                + "#BMINBB=" + value.buyinMinBb()
                + "#BMAXBB=" + value.buyinMaxBb()
                + "#RBCAP=" + value.rebuyCapPolicy()
                + "#ANTE=" + bit(value.ante())
                + "#STRADDLE=" + bit(value.straddle())
                + "#MANOS=" + value.hands()
                + "#THINKT=" + value.thinkTime()
                + "#THINKON=" + bit(value.thinkTimeEnabled())
                + "#SHOWDOWN=" + value.showdownTime();
    }

    private static NewGameTableDraft.Settings decodeSettings(
            Map<String, String> values, int buyin, double smallBlind,
            int blindInterval, int blindIntervalType, boolean rebuy) {
        if (buyin <= 0 || smallBlind <= 0d || !Double.isFinite(smallBlind)) {
            throw new IllegalArgumentException("Invalid recovered game economy");
        }

        NewGameTableDraft draft = new NewGameTableDraft();
        String encodedLevels = values.get("BLINDS");
        List<NewGameTableDraft.BlindLevel> levels = encodedLevels.isEmpty()
                ? levels(BlindStructureRules.defaultLevels())
                : parseLevels(encodedLevels);
        int selected = findSmallBlind(levels, smallBlind);
        if (selected < 0) {
            throw new IllegalArgumentException(
                    "Recovered blind is absent from its blind structure");
        }
        draft.setBlindStructure(encodedLevels.isEmpty() ? null : "Recuperada",
                levels, selected);
        draft.setIncreaseBlinds(blindInterval > 0);
        draft.setBlindIncreaseType(blindIntervalType == 2
                ? NewGameTableDraft.BlindIncreaseType.HANDS
                : NewGameTableDraft.BlindIncreaseType.MINUTES);
        if (blindInterval > 0) draft.setBlindInterval(blindInterval);

        double blindCap = number(values, "BLIND_CAP");
        if (blindCap > 0d) {
            int capIndex = findBigBlind(levels, blindCap, selected + 1);
            if (capIndex < 0) {
                throw new IllegalArgumentException(
                        "Recovered blind cap is absent from its blind structure");
            }
            draft.setBlindCap(true);
            draft.setBlindCapRaises(capIndex - selected);
        }

        draft.setMaxBuyinBb(integer(values, "BMAXBB"));
        draft.setMinBuyinBb(integer(values, "BMINBB"));
        draft.setFixedBuyin(one(values, "FIXED_BUYIN"));
        draft.setBuyin(buyin);
        draft.setRebuy(rebuy);
        int rebuyLimit = integer(values, "REBUY_LIMIT");
        draft.setRebuyLimit(rebuyLimit > 0);
        if (rebuyLimit > 0) draft.setRebuyLimitCount(rebuyLimit);
        draft.setBotRebuy(one(values, "BOT_REBUY"));
        draft.setBotBalanceToHumans(one(values, "BOTBAL"));
        draft.setRebuyCapPolicy("1".equals(values.get("RBCAP"))
                ? NewGameTableDraft.RebuyCapPolicy.HIGHEST_STACK
                : NewGameTableDraft.RebuyCapPolicy.BUY_IN);
        int hands = integer(values, "MANOS");
        draft.setHandLimit(hands >= 0);
        if (hands >= 0) draft.setHandLimitCount(hands);
        draft.setThinkSeconds(integer(values, "THINKT"));
        draft.setThinkTime(one(values, "THINKON"));
        draft.setShowdownSeconds(integer(values, "SHOWDOWN"));
        draft.setAnte(one(values, "ANTE"));
        draft.setStraddle(one(values, "STRADDLE"));
        draft.setIwtsth(one(values, "IWTSTH"));
        draft.setRunItTwice(one(values, "RUNITWICE"));
        draft.setRabbitHunting(enumAt(NewGameTableDraft.RabbitHunting.values(),
                integer(values, "RABBIT"), "rabbit hunting"));
        draft.setBotDifficulty(NewGameTableDraft.BotDifficulty.valueOf(
                values.get("DIFFICULTY")));
        return draft.snapshot();
    }

    private static Map<String, String> parseSettings(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            throw new IllegalArgumentException("Missing recovered settings");
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : serialized.split("#", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || pair.indexOf('=', separator + 1) >= 0) {
                throw new IllegalArgumentException("Malformed recovered settings");
            }
            String key = pair.substring(0, separator);
            if (!SETTINGS_KEYS.contains(key)
                    || values.putIfAbsent(key, pair.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Malformed recovered settings");
            }
        }
        if (!values.keySet().equals(SETTINGS_KEYS)) {
            throw new IllegalArgumentException("Incomplete recovered settings");
        }
        return values;
    }

    private static List<NewGameTableDraft.BlindLevel> parseLevels(String value) {
        List<NewGameTableDraft.BlindLevel> result = new ArrayList<>();
        for (String token : value.split(",", -1)) {
            String[] pair = token.split("/", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Malformed recovered blind structure");
            }
            result.add(new NewGameTableDraft.BlindLevel(
                    Double.parseDouble(pair[0]), Double.parseDouble(pair[1])));
        }
        return List.copyOf(result);
    }

    private static List<NewGameTableDraft.BlindLevel> levels(double[][] raw) {
        List<NewGameTableDraft.BlindLevel> result = new ArrayList<>(raw.length);
        for (double[] level : raw) {
            result.add(new NewGameTableDraft.BlindLevel(level[0], level[1]));
        }
        return List.copyOf(result);
    }

    private static int findSmallBlind(List<NewGameTableDraft.BlindLevel> levels,
            double value) {
        for (int i = 0; i < levels.size(); i++) {
            if (sameMoney(levels.get(i).smallBlind(), value)) return i;
        }
        return -1;
    }

    private static int findBigBlind(List<NewGameTableDraft.BlindLevel> levels,
            double value, int from) {
        for (int i = Math.max(0, from); i < levels.size(); i++) {
            if (sameMoney(levels.get(i).bigBlind(), value)) return i;
        }
        return -1;
    }

    private static boolean sameMoney(double left, double right) {
        return Math.round(left * 100d) == Math.round(right * 100d);
    }

    private static int integer(Map<String, String> values, String key) {
        return Integer.parseInt(values.get(key));
    }

    private static double number(Map<String, String> values, String key) {
        return Double.parseDouble(values.get(key));
    }

    private static boolean one(Map<String, String> values, String key) {
        String value = values.get(key);
        if (!"0".equals(value) && !"1".equals(value)) {
            throw new IllegalArgumentException("Invalid recovered boolean: " + key);
        }
        return "1".equals(value);
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static String money(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value) : Double.toString(value);
    }

    private static <T> T enumAt(T[] values, int index, String label) {
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("Invalid recovered " + label);
        }
        return values[index];
    }

    public record RecoverableGame(int id, long startedAtMillis, String server,
            NewGameTableDraft.Settings settings, boolean voiceMessages,
            boolean textToSpeech) {
        public RecoverableGame {
            if (id <= 0) throw new IllegalArgumentException("id must be positive");
            server = Objects.requireNonNullElse(server, "").trim();
            Objects.requireNonNull(settings, "settings");
        }
    }
}
