package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.StatsSync;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Process-local statistics synchronization access for a network gateway. */
public final class StatsSyncService {
    private final DatabaseService database;
    private final BooleanSupplier excludePrivate;
    private final Supplier<Set<String>> excludedNicks;

    public StatsSyncService(DatabaseService database,
            BooleanSupplier excludePrivate,
            Supplier<Set<String>> excludedNicks) {
        this.database = Objects.requireNonNull(database, "database");
        this.excludePrivate = Objects.requireNonNull(excludePrivate,
                "excludePrivate");
        this.excludedNicks = Objects.requireNonNull(excludedNicks,
                "excludedNicks");
    }

    public List<String> listShareableUgis() {
        Set<String> excluded = excludedNicks.get();
        return StatsSync.listShareableUgis(database,
                excludePrivate.getAsBoolean(),
                excluded == null ? Set.of() : excluded);
    }

    public byte[] exportGames(Collection<String> ugis) {
        return StatsSync.exportGames(database, ugis);
    }

    public int importGames(byte[] blob, String fromNick) {
        return StatsSync.importGames(database, blob, fromNick);
    }
}
