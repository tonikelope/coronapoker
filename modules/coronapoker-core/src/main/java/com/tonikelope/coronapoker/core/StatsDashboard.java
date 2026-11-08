/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import static com.tonikelope.coronapoker.core.game.GamePlayerController.ALLIN;
import static com.tonikelope.coronapoker.core.game.GamePlayerController.BET;
import static com.tonikelope.coronapoker.core.game.GamePlayerController.CHECK;
import static com.tonikelope.coronapoker.core.game.GamePlayerController.FOLD;

import com.tonikelope.coronapoker.core.game.CardCode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Read-only analysis of completed, settled hands in the existing game ledger. */
public final class StatsDashboard {

    public static final int COMPARISON_HANDS = 30;
    private static final String SETTLED = " WHERE h.end>0 AND h.pot>0 ";
    private static final String HAND_ORDER = " ORDER BY h.id_game,h.counter,h.id";
    private static final List<String> POSITIONS = List.of("BTN", "SB", "BB",
            "UTG", "MP", "CO");

    private final List<Session> sessions;
    private final List<String> players;

    private StatsDashboard(List<Session> sessions, Set<String> players) {
        this.sessions = List.copyOf(sessions);
        this.players = List.copyOf(players);
    }

    static StatsDashboard load(DatabaseService database) throws SQLException {
        Map<Integer, Session> sessions = new HashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        synchronized (database.lock()) {
            Connection connection = database.connection();
            // Walk all four result sets in the same hand order. Only one
            // hand's events are retained, even for a large imported history.
            try (Statement handQuery = statement(connection);
                    Statement actionQuery = statement(connection);
                    Statement balanceQuery = statement(connection);
                    Statement showdownQuery = statement(connection);
                    ResultSet hands = handQuery.executeQuery("""
                    SELECT g.id AS game_id,g.start,g.end AS game_end,g.play_time,
                           g.imported,h.id,h.sbval,h.dealer,h.sb,h.bb,
                           h.preflop_players,h.flop_players
                    FROM game g JOIN hand h ON h.id_game=g.id
                    """ + SETTLED + HAND_ORDER);
                    ResultSet actions = actionQuery.executeQuery("""
                    SELECT a.id_hand,a.player,a.round,a.action,a.bet
                    FROM action a JOIN hand h ON h.id=a.id_hand
                    """ + SETTLED + HAND_ORDER + ",a.counter,a.id");
                    ResultSet balances = balanceQuery.executeQuery("""
                    SELECT b.id_hand,b.player,b.stack,b.buyin
                    FROM balance b JOIN hand h ON h.id=b.id_hand
                    """ + SETTLED + HAND_ORDER + ",b.id");
                    ResultSet showdowns = showdownQuery.executeQuery("""
                    SELECT s.id_hand,s.player,s.winner,s.hole_cards
                    FROM showdown s JOIN hand h ON h.id=s.id_hand
                    """ + SETTLED + HAND_ORDER + ",s.id")) {
                boolean hasAction = actions.next();
                boolean hasBalance = balances.next();
                boolean hasReveal = showdowns.next();
                while (hands.next()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new SQLException("Statistics load interrupted");
                    }
                    int gameId = hands.getInt("game_id");
                    Session session = sessions.get(gameId);
                    if (session == null) {
                        long start = hands.getLong("start");
                        session = new Session(gameId, start,
                                Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
                                Math.max(0L, hands.getLong("play_time")),
                                hands.getLong("game_end") > 0, hands.getBoolean("imported"));
                        sessions.put(gameId, session);
                    }
                    int id = hands.getInt("id");
                    Hand hand = new Hand(session, cents(hands.getDouble("sbval") * 2),
                            hands.getString("dealer"), hands.getString("sb"), hands.getString("bb"),
                            StatsRepository.decodePlayers(hands.getString("preflop_players")),
                            Set.copyOf(StatsRepository.decodePlayers(hands.getString("flop_players"))));
                    while (hasAction && actions.getInt("id_hand") == id) {
                        hand.actions.add(new Action(actions.getString("player"), actions.getInt("round"),
                                actions.getInt("action"), cents(actions.getDouble("bet"))));
                        hasAction = actions.next();
                    }
                    while (hasBalance && balances.getInt("id_hand") == id) {
                        if (balances.getObject("stack") != null && balances.getObject("buyin") != null) {
                            hand.balances.put(balances.getString("player"),
                                    cents(balances.getDouble("stack")) - cents(balances.getDouble("buyin")));
                        }
                        hasBalance = balances.next();
                    }
                    while (hasReveal && showdowns.getInt("id_hand") == id) {
                        hand.showdown.put(showdowns.getString("player"),
                                new Reveal(showdowns.getBoolean("winner"), showdowns.getString("hole_cards")));
                        hasReveal = showdowns.next();
                    }
                    hand.accumulate();
                }
            }
        }

        List<Session> ordered = sessions.values().stream().filter(session -> session.start > 0)
                .sorted(Comparator.comparingLong((Session session) -> session.start)
                        .thenComparingInt(session -> session.id))
                .toList();
        Set<String> players = new TreeSet<>();
        ordered.forEach(session -> players.addAll(session.players.keySet()));
        return new StatsDashboard(ordered, players);
    }

    public List<String> players(int gameId) {
        if (gameId == 0) return players;
        return sessions.stream().filter(session -> session.id == gameId)
                .findFirst().map(session -> session.players.keySet().stream().sorted().toList())
                .orElseGet(List::of);
    }

    public View view(String player, int gameId, LocalDate from, LocalDate through) {
        Totals total = new Totals();
        Map<String, Totals> opponents = new HashMap<>();
        List<SessionRow> rows = new ArrayList<>();
        long elapsed = 0;
        long hourlyNet = 0;
        long running = 0;
        int funded = 0;
        int wins = 0;
        for (Session session : sessions) {
            if (gameId != 0 && session.id != gameId
                    || from != null && session.date.isBefore(from)
                    || through != null && session.date.isAfter(through)) continue;
            Totals entry = session.players.get(player);
            if (entry == null) continue;
            total.add(entry, true);
            if (entry.net != null) {
                running += entry.net;
                funded++;
                if (entry.net > 0) wins++;
                if (session.seconds > 0 && session.closed) {
                    elapsed += session.seconds;
                    hourlyNet += entry.net;
                }
            }
            rows.add(new SessionRow(session.id, session.start, session.date.toString(),
                    session.seconds, session.closed, session.imported, entry.hands,
                    units(entry.net), entry.net == null ? null : running / 100d,
                    entry.rates()));
            session.players.forEach((name, stats) ->
                    opponents.computeIfAbsent(name, unused -> new Totals()).add(stats, false));
        }
        List<PlayerRow> comparison = opponents.entrySet().stream()
                .filter(entry -> entry.getValue().hands >= COMPARISON_HANDS)
                .sorted(Map.Entry.<String, Totals>comparingByValue(
                        Comparator.comparingInt(stats -> -stats.hands))
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new PlayerRow(entry.getKey(), entry.getValue().hands,
                        units(entry.getValue().net), entry.getValue().rates()))
                .toList();
        List<Bucket> positions = POSITIONS.stream().filter(total.positions::containsKey)
                .map(key -> total.positions.get(key).row(key)).toList();
        List<Bucket> cards = total.cards.entrySet().stream()
                .map(entry -> entry.getValue().row(entry.getKey())).toList();
        return new View(rows.size(), funded, wins, total.hands, elapsed, units(total.net),
                elapsed == 0 ? null : hourlyNet * 36d / elapsed, total.rates(),
                rows, positions, cards, comparison, COMPARISON_HANDS);
    }

    public record View(int sessionCount, int sessionsWithBalance, int winningSessions,
            int hands, long timedSeconds, Double net, Double hourly, Rates rates,
            List<SessionRow> sessions, List<Bucket> positions, List<Bucket> cards,
            List<PlayerRow> comparison, int comparisonHands) { }

    public record SessionRow(int id, long startedAt, String date, long seconds,
            boolean closed, boolean imported, int hands, Double net, Double running,
            Rates rates) { }

    public record PlayerRow(String player, int hands, Double net, Rates rates) { }

    public record Bucket(String key, int hands, double net, double perHand) { }

    public record Rate(Double value, int numerator, int sample) { }

    public record Rates(Rate vpip, Rate pfr, Rate threeBet, Rate af, Rate wtsd,
            Rate wsd) { }

    private record Action(String player, int street, int decision, long bet) { }

    private record Reveal(boolean winner, String cards) { }

    private static final class Session {
        final int id;
        final long start;
        final LocalDate date;
        final long seconds;
        final boolean closed;
        final boolean imported;
        final Map<String, Totals> players = new HashMap<>();

        Session(int id, long start, LocalDate date, long seconds, boolean closed,
                boolean imported) {
            this.id = id;
            this.start = start;
            this.date = date;
            this.seconds = seconds;
            this.closed = closed;
            this.imported = imported;
        }
    }

    private static final class Hand {
        final Session session;
        final long blind;
        final String dealer;
        final String small;
        final String big;
        final List<String> roster;
        final Set<String> flop;
        final List<Action> actions = new ArrayList<>();
        final Map<String, Long> balances = new HashMap<>();
        final Map<String, Reveal> showdown = new HashMap<>();

        Hand(Session session, long blind, String dealer, String small, String big,
                List<String> roster, Set<String> flop) {
            this.session = session;
            this.blind = blind;
            this.dealer = dealer;
            this.small = small;
            this.big = big;
            this.roster = roster;
            this.flop = flop;
        }

        void accumulate() {
            Set<String> dealt = new HashSet<>(roster);
            dealt.addAll(flop);
            showdown.keySet().stream().filter(Hand::isPlayer).forEach(dealt::add);
            actions.stream().map(Action::player).filter(Hand::isPlayer).forEach(dealt::add);
            Set<String> voluntary = new HashSet<>();
            Set<String> raised = new HashSet<>();
            Set<String> opportunity = new HashSet<>();
            Set<String> reraised = new HashSet<>();
            Map<String, Long> commitments = new HashMap<>();
            int street = 0;
            int raises = 0;
            long tableBet = 0;
            boolean preflopRecorded = false;
            String utg = seatAfter(big, 1);

            for (Action action : actions) {
                if (!dealt.contains(action.player) || action.street < 1 || action.street > 4
                        || action.decision < FOLD || action.decision > ALLIN) continue;
                if (action.street != street) {
                    street = action.street;
                    commitments.clear();
                    tableBet = street == 1 ? blind : 0;
                    if (street == 1) {
                        commitments.put(small, blind / 2);
                        commitments.put(big, blind);
                    }
                }
                if (street == 1 && raises == 0 && action.decision == CHECK
                        && blind > 0 && action.bet == 2 * blind && utg != null
                        && roster.size() > 2) {
                    // Older hand rows carry no separate straddle flag. A call
                    // at twice BB before any raise identifies the posted blind.
                    tableBet = action.bet;
                    commitments.put(utg, tableBet);
                }
                long previous = commitments.getOrDefault(action.player, 0L);
                boolean increase = action.decision == BET
                        || action.decision == ALLIN && action.bet > tableBet;
                boolean paid = action.decision != FOLD && action.bet > previous;
                if (street == 1) {
                    preflopRecorded = true;
                    if (raises == 1) opportunity.add(action.player);
                    if (paid || increase) voluntary.add(action.player);
                    if (increase) {
                        raised.add(action.player);
                        if (++raises == 2) reraised.add(action.player);
                    }
                } else {
                    Totals stats = session.players.computeIfAbsent(action.player,
                            unused -> new Totals());
                    if (increase) stats.aggression++;
                    else if (paid) stats.calls++;
                }
                if (action.decision != FOLD) {
                    commitments.put(action.player, action.bet);
                    tableBet = Math.max(tableBet, action.bet);
                }
            }

            boolean contested = showdown.keySet().stream().filter(Hand::isPlayer).count() >= 2;
            for (String player : dealt) {
                Totals stats = session.players.computeIfAbsent(player, unused -> new Totals());
                stats.hands++;
                if (preflopRecorded) stats.preflop++;
                if (voluntary.contains(player)) stats.vpip++;
                if (raised.contains(player)) stats.pfr++;
                if (opportunity.contains(player)) stats.threeBetChances++;
                if (reraised.contains(player)) stats.threeBets++;
                Reveal reveal = showdown.get(player);
                boolean reached = contested && reveal != null;
                if (flop.contains(player) || reached) stats.flops++;
                if (reached) {
                    stats.showdowns++;
                    if (reveal.winner) stats.showdownWins++;
                }
                Long balance = balances.get(player);
                if (balance != null) {
                    // The ledger stores stack minus cumulative funding. Its
                    // change stays correct across rebuys and top-ups.
                    if (stats.hands == 1 || stats.previousBalanceKnown) {
                        long delta = balance - (stats.net == null ? 0L : stats.net);
                        Totals.bucket(stats.positions, position(player), delta);
                        if (reveal != null) Totals.bucket(stats.cards, handClass(reveal.cards), delta);
                    }
                    stats.net = balance;
                }
                stats.previousBalanceKnown = balance != null;
            }
        }

        private static boolean isPlayer(String player) {
            return player != null && !player.isBlank() && !"-----".equals(player);
        }

        private String seatAfter(String player, int distance) {
            int index = player == null ? -1 : roster.indexOf(player);
            return index < 0 ? null : roster.get((index + distance) % roster.size());
        }

        private String position(String player) {
            if (player.equals(dealer)) return "BTN";
            if (player.equals(small)) return "SB";
            if (player.equals(big)) return "BB";
            // The hand's roster is persisted in seat order. Verify its blind
            // anchors before using relative positions on older/imported rows.
            if (roster.size() < 4 || !isPlayer(small) || !isPlayer(big)
                    || !small.equals(seatAfter(dealer, 1))
                    || !big.equals(seatAfter(dealer, 2)) || !roster.contains(player)) return null;
            if (player.equals(seatAfter(dealer, roster.size() - 1))) return "CO";
            if (player.equals(seatAfter(big, 1))) return "UTG";
            return "MP";
        }
    }

    static String handClass(String cards) {
        if (cards == null) return null;
        String[] parts = cards.split("#", -1);
        if (parts.length != 2) return null;
        try {
            CardCode first = CardCode.parseShortCode(parts[0]);
            CardCode second = CardCode.parseShortCode(parts[1]);
            if (first.equals(second)) return null;
            boolean firstHigh = first.rank().aceHighValue() >= second.rank().aceHighValue();
            String ranks = firstHigh ? rank(first) + rank(second) : rank(second) + rank(first);
            return first.rank() == second.rank() ? ranks
                    : ranks + (first.suit() == second.suit() ? "s" : "o");
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String rank(CardCode card) {
        return card.rank() == CardCode.Rank.TEN ? "T" : card.rank().wire();
    }

    private static final class Cell {
        long net;
        int hands;

        Bucket row(String key) {
            return new Bucket(key, hands, net / 100d, net / (100d * hands));
        }
    }

    private static final class Totals {
        int hands;
        int preflop;
        int vpip;
        int pfr;
        int threeBets;
        int threeBetChances;
        int aggression;
        int calls;
        int flops;
        int showdowns;
        int showdownWins;
        Long net;
        boolean previousBalanceKnown;
        final Map<String, Cell> positions = new HashMap<>();
        final Map<String, Cell> cards = new HashMap<>();

        void add(Totals other, boolean heatmaps) {
            hands += other.hands;
            preflop += other.preflop;
            vpip += other.vpip;
            pfr += other.pfr;
            threeBets += other.threeBets;
            threeBetChances += other.threeBetChances;
            aggression += other.aggression;
            calls += other.calls;
            flops += other.flops;
            showdowns += other.showdowns;
            showdownWins += other.showdownWins;
            if (other.net != null) net = (net == null ? 0L : net) + other.net;
            if (heatmaps) {
                merge(positions, other.positions);
                merge(cards, other.cards);
            }
        }

        Rates rates() {
            return new Rates(rate(vpip, preflop, 100), rate(pfr, preflop, 100),
                    rate(threeBets, threeBetChances, 100), rate(aggression, calls, 1),
                    rate(showdowns, flops, 100), rate(showdownWins, showdowns, 100));
        }

        private static void merge(Map<String, Cell> target, Map<String, Cell> source) {
            source.forEach((key, cell) -> {
                Cell result = target.computeIfAbsent(key, unused -> new Cell());
                result.net += cell.net;
                result.hands += cell.hands;
            });
        }

        private static void bucket(Map<String, Cell> cells, String key, long delta) {
            if (key == null) return;
            Cell cell = cells.computeIfAbsent(key, unused -> new Cell());
            cell.net += delta;
            cell.hands++;
        }
    }

    private static Rate rate(int numerator, int sample, double scale) {
        return new Rate(sample == 0 ? null : numerator * scale / sample, numerator, sample);
    }

    private static long cents(double amount) {
        return Math.round(amount * 100d);
    }

    private static Double units(Long amount) {
        return amount == null ? null : amount / 100d;
    }

    private static Statement statement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.setQueryTimeout(30);
            return statement;
        } catch (SQLException failure) {
            statement.close();
            throw failure;
        }
    }
}
