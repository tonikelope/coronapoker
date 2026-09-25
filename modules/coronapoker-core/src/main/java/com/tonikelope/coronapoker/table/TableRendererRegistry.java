/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Discovers optional renderer modules without adding their libraries to core. */
public final class TableRendererRegistry {

    private final Map<String, TableRendererProvider> providers;

    public static TableRendererRegistry discover() {
        return new TableRendererRegistry(ServiceLoader.load(TableRendererProvider.class));
    }

    TableRendererRegistry(Iterable<TableRendererProvider> discovered) {
        Objects.requireNonNull(discovered, "discovered");
        Map<String, TableRendererProvider> indexed = new LinkedHashMap<>();
        for (TableRendererProvider provider : discovered) {
            Objects.requireNonNull(provider, "renderer provider");
            String id = normalizedId(provider.id());
            if (id.isEmpty()) {
                throw new IllegalStateException("A table renderer provider has no id");
            }
            if (indexed.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("Duplicate table renderer provider: " + id);
            }
        }
        providers = Map.copyOf(indexed);
    }

    public Optional<TableRendererProvider> find(String id) {
        return Optional.ofNullable(providers.get(normalizedId(id)));
    }

    private static String normalizedId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
