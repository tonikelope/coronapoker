package com.tonikelope.coronapoker.table;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TableRendererRegistryTest {

    @Test
    void providerIdsAreCaseInsensitiveAndTrimmed() {
        TableRendererProvider provider = provider(" GDX ");
        TableRendererRegistry registry = new TableRendererRegistry(List.of(provider));

        assertEquals(provider, registry.find("gDx").orElseThrow());
        assertTrue(registry.find("swing").isEmpty());
    }

    @Test
    void duplicateProviderIdsFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new TableRendererRegistry(List.of(provider("gdx"), provider("GDX"))));
    }

    private static TableRendererProvider provider(String id) {
        return new TableRendererProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public TableRenderer create(TableCommandSink commandSink) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
