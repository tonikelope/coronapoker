/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeded table/socket lifecycle campaign. It composes the current termination
 * wire, session guard, reconnect outbox and Crupier's extracted stop decisions.
 */
@Tag("slow")
@Tag("protocol-sim")
class ProtocolLifecycleCampaignTest {

    private enum Event {
        NORMAL, FINAL_EXIT, FORCE_RECOVER, ABRUPT_DISCONNECT,
        SOCKET_RECONNECT, RIT_SIDE_B_STOP, MALFORMED_TERMINATION
    }

    @Test
    void randomizedLifecycleNeverLetsAnOldSessionMutateTheNextOne() {
        int cases = intProperty("qa.sim.faults", 2_000, 1, 100_000);
        long seed = longProperty("qa.sim.seed", 3231711270L);
        Integer replay = optionalIntProperty("qa.sim.lifecycle.case", 0, 99_999);
        int first = replay == null ? 0 : replay;
        int end = replay == null ? cases : replay + 1;
        for (int caseNumber = first; caseNumber < end; caseNumber++) {
            runOne(seed, caseNumber);
        }
    }

    private static void runOne(long campaignSeed, int caseNumber) {
        long caseSeed = mix64(campaignSeed + 0x9E3779B97F4A7C15L * caseNumber);
        Random random = new Random(caseSeed);
        Event event = Event.values()[random.nextInt(Event.values().length)];
        String context = "seed=" + campaignSeed + " lifecycle_case=" + caseNumber
                + " caseSeed=" + caseSeed + " event=" + event;

        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation table = guard.beginSession();
        SessionOutbox outbox = new SessionOutbox(16, 16_384);
        int queued = 1 + random.nextInt(8);
        List<String> commands = new ArrayList<>(queued);
        for (int i = 0; i < queued; i++) {
            String command = "GAME#" + (i + 1) + "#ACTION#payload-" + caseNumber + "-" + i;
            assertTrue(outbox.offer(command), context);
            commands.add(command);
        }
        SessionOutbox.Entry leasedBeforeEvent = outbox.peek();
        int firstWireId = leasedBeforeEvent.wireId();

        AtomicInteger tableEffects = new AtomicInteger();
        assertTrue(guard.runIfCurrent(table, tableEffects::incrementAndGet), context);

        switch (event) {
            case NORMAL -> {
                drainCurrent(outbox, context);
                assertTrue(guard.runIfCurrent(table, tableEffects::incrementAndGet), context);
                assertEquals(2, tableEffects.get(), context);
            }
            case SOCKET_RECONNECT -> {
                long generation = outbox.advanceGenerationPreservingEntries();
                assertEquals(1L, generation, context);
                assertFalse(outbox.isCurrent(leasedBeforeEvent), context);
                assertEquals(commands.size(), outbox.size(), context);
                assertEquals(firstWireId, outbox.peek().wireId(), context);
                for (String expected : commands) {
                    SessionOutbox.Entry rebound = outbox.peek();
                    assertTrue(rebound != null && outbox.isCurrent(rebound), context);
                    assertEquals(expected, rebound.command(), context);
                    assertTrue(outbox.removeIfHead(rebound), context);
                }
                assertTrue(guard.runIfCurrent(table, tableEffects::incrementAndGet), context);
                assertEquals(2, tableEffects.get(), context);
            }
            case FINAL_EXIT -> {
                TableTerminationWire.ExitCommand parsed = TableTerminationWire.parse(
                        new String[]{"GAME", "91", "SERVEREXIT"});
                assertFalse(parsed.recover(), context);
                terminate(guard, table, outbox, tableEffects, context);
            }
            case FORCE_RECOVER -> {
                String password = "recover-" + caseNumber;
                String encoded = Base64.getEncoder().encodeToString(
                        password.getBytes(StandardCharsets.UTF_8));
                TableTerminationWire.ExitCommand parsed = TableTerminationWire.parse(
                        new String[]{"GAME", "91", "SERVEREXITRECOVER", encoded});
                assertTrue(parsed.recover(), context);
                assertEquals(password, parsed.password(), context);
                assertTrue(Crupier.shouldAbortAfterBettingRound(false, true), context);
                assertFalse(Crupier.shouldAdvanceBettingStreet(false, true, 3,
                        Crupier.FLOP, 3), context);
                terminate(guard, table, outbox, tableEffects, context);
            }
            case ABRUPT_DISCONNECT -> {
                RecoveryReceiveState receive = new RecoveryReceiveState("session-" + caseNumber);
                receive.rejectTransportClosed();
                assertTrue(receive.isTerminal(), context);
                assertFalse(receive.isSuccess(), context);
                assertEquals("TRANSPORT_CLOSED", receive.error(), context);
                String reason = "peer.transport_closed." + caseNumber;
                String encoded = Base64.getEncoder().encodeToString(
                        reason.getBytes(StandardCharsets.UTF_8));
                assertEquals(reason, MisdealWire.parse(
                        new String[]{"GAME", "91", "MISDEAL", encoded}), context);
                terminate(guard, table, outbox, tableEffects, context);
            }
            case RIT_SIDE_B_STOP -> {
                boolean refunded = random.nextBoolean();
                boolean pending = random.nextBoolean();
                boolean finished = random.nextBoolean();
                boolean interrupted = random.nextBoolean();
                boolean expectedInProgress = !refunded && (pending || finished || interrupted);
                assertEquals(expectedInProgress,
                        Crupier.shouldLeaveRunItTwiceHandInProgress(false, refunded,
                                pending, finished, interrupted), context);
                assertEquals(pending || finished,
                        Crupier.shouldAbortRunItTwiceSideBDeal(pending, finished), context);
                // A generated RIT stop with no termination signal is just an
                // ordinary live state; otherwise it must invalidate the table.
                if (pending || finished || interrupted) {
                    terminate(guard, table, outbox, tableEffects, context);
                } else {
                    assertTrue(guard.isCurrent(table), context);
                }
            }
            case MALFORMED_TERMINATION -> {
                String malformed = random.nextBoolean()
                        ? "GAME#91#SERVEREXIT#trailing"
                        : "GAME#91#SERVEREXITRECOVER#%%%";
                assertFalse(TableTerminationWire.isValidTerminationFrame(malformed), context);
                assertThrows(IllegalArgumentException.class,
                        () -> TableTerminationWire.parse(malformed.split("#", -1)), context);
                // Invalid critical input closes explicitly; it cannot be ignored
                // while the old table continues processing later commands.
                terminate(guard, table, outbox, tableEffects, context);
            }
        }

        if (Boolean.parseBoolean(System.getProperty("qa.sim.trace", "false"))) {
            System.out.println("PROTOCOL_LIFECYCLE_SIM PASS " + context);
        }
    }

    private static void terminate(SessionGuard guard, SessionGuard.Generation oldTable,
            SessionOutbox outbox, AtomicInteger effects, String context) {
        guard.invalidate(oldTable);
        outbox.advanceGeneration();
        assertTrue(outbox.isEmpty(), context + " old commands survived table termination");
        assertFalse(guard.runIfCurrent(oldTable, effects::incrementAndGet),
                context + " stale callback mutated terminated table");
        assertEquals(1, effects.get(), context);

        SessionGuard.Generation nextTable = guard.beginSession();
        assertTrue(guard.runIfCurrent(nextTable, effects::incrementAndGet), context);
        assertEquals(2, effects.get(), context);
    }

    private static void drainCurrent(SessionOutbox outbox, String context) {
        while (!outbox.isEmpty()) {
            SessionOutbox.Entry head = outbox.peek();
            assertTrue(outbox.isCurrent(head), context);
            assertTrue(outbox.removeIfHead(head), context);
        }
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return fallback;
        }
        return parseInt(name, text, min, max);
    }

    private static Integer optionalIntProperty(String name, int min, int max) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return null;
        }
        return parseInt(name, text, min, max);
    }

    private static int parseInt(String name, String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(name + " must be " + min + ".." + max);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer: " + text, ex);
        }
    }

    private static long longProperty(String name, long fallback) {
        String text = System.getProperty(name);
        if (text == null || text.isBlank() || text.startsWith("${")) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a long: " + text, ex);
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
