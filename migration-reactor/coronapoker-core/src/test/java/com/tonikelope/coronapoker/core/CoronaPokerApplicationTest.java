package com.tonikelope.coronapoker.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CoronaPokerApplicationTest {

    @Test
    void followsStartupSessionTableReturnAndShutdownLifecycle() {
        CoronaPokerApplication application = CoronaPokerApplication.withoutServices();

        application.start();
        assertEquals(ApplicationLifecycle.State.STARTING, application.lifecycle().state());
        application.menuReady();
        application.sessionOpened();
        application.tableEntered();
        application.returnedToMenu();
        application.close();
        application.close();

        assertEquals(ApplicationLifecycle.State.TERMINATED, application.lifecycle().state());
    }

    @Test
    void startsInOrderAndClosesInReverseOrderExactlyOnce() {
        List<String> calls = new ArrayList<>();
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(
                service("database", calls), service("audio", calls)));

        application.start();
        application.close();
        application.close();

        assertEquals(List.of("start database", "start audio", "close audio", "close database"), calls);
    }

    @Test
    void rollsBackStartedServicesWhenStartupFails() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new RuntimeException("crypto failed");
        ApplicationService database = service("database", calls);
        ApplicationService crypto = new ApplicationService() {
            @Override
            public void start() {
                calls.add("start crypto");
                throw failure;
            }

            @Override
            public void close() {
                calls.add("close crypto");
            }
        };
        CoronaPokerApplication application = new CoronaPokerApplication(List.of(database, crypto));

        ApplicationStartupException thrown = assertThrows(ApplicationStartupException.class, application::start);

        assertSame(failure, thrown.getCause());
        assertSame(failure, application.lifecycle().failure());
        assertEquals(ApplicationLifecycle.State.FAILED, application.lifecycle().state());
        assertEquals(List.of("start database", "start crypto", "close database"), calls);
    }

    @Test
    void rejectsFrontendEventsThatBreakCausalOrder() {
        CoronaPokerApplication application = CoronaPokerApplication.withoutServices();

        application.start();

        assertThrows(IllegalStateException.class, application::tableEntered);
    }

    private static ApplicationService service(String name, List<String> calls) {
        return new ApplicationService() {
            @Override
            public void start() {
                calls.add("start " + name);
            }

            @Override
            public void close() {
                calls.add("close " + name);
            }
        };
    }
}
