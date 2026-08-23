package com.tonikelope.coronapoker.e2e;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single Java-side contract for every production-loopback scenario. */
final class RealGameScenarioContract {

    static final Set<String> ACTION_GATED = Set.of(
            "abrupt-exit",
            "controlled-exit",
            "dual-abrupt-exit",
            "mixed-exit-crash",
            "crash-rejoin-recover",
            "allin-controlled-exit",
            "allin-reconnect",
            "allin-abrupt-exit",
            "pause-resume",
            "reconnect-midhand",
            "reconnect-twice",
            "reconnect-storm",
            "dual-reconnect",
            "host-channel-flap",
            "reconnect-every-street",
            "reconnect-force-recover",
            "force-recover",
            "double-force-recover",
            "force-recover-add-client",
            "force-recover-add-two",
            "force-recover-swap-client",
            "spectator-rebuy-cycle",
            "spectator-recovery-mix",
            "bot-bust-recover-regrow",
            "bot-bust-recover-drop",
            "human-bust-exit-rejoin-rebuy",
            "spectator-double-recovery-crash-mix",
            "transport-chaos",
            "lifecycle-chaos");

    static final Set<String> DIALOG_ORDERED = Set.of(
            "rit-network-cut",
            "straddle-network-cut");

    static final Set<String> AUTONOMOUS = Set.of(
            "normal",
            "raise-mix",
            "allin-single-board",
            "allin-rebuy",
            "allin-rit",
            "straddle-post");

    /** Scenarios whose successful production outcome dismantles the table. */
    static final Set<String> MISDEAL_TERMINAL = Set.of(
            "abrupt-exit",
            "dual-abrupt-exit",
            "mixed-exit-crash",
            "allin-abrupt-exit");

    static final Set<String> ALL;

    static {
        HashSet<String> all = new HashSet<>();
        all.addAll(ACTION_GATED);
        all.addAll(DIALOG_ORDERED);
        all.addAll(AUTONOMOUS);
        ALL = Collections.unmodifiableSet(all);
    }

    private RealGameScenarioContract() {
    }

    static boolean isSupported(String scenario) {
        return ALL.contains(scenario);
    }

    static boolean isActionGated(String scenario) {
        return ACTION_GATED.contains(scenario);
    }

    static boolean isOrderedAllIn(String scenario) {
        return scenario.equals("allin-controlled-exit")
                || scenario.equals("allin-reconnect")
                || scenario.equals("allin-abrupt-exit");
    }

    static boolean expectsTerminalMisdeal(String scenario) {
        return MISDEAL_TERMINAL.contains(scenario);
    }

    /**
     * A deliberate QA socket cut may race one in-flight production write. That
     * failure is expected only after the cut is armed and before the fresh
     * channel is confirmed. Every unarmed, repeated or late write failure is a
     * real terminal signal.
     */
    static boolean hasUnexpectedClientWriteFailure(List<String> lines) {
        boolean cutArmed = false;
        boolean failureSeen = false;
        for (String line : lines) {
            if (line.contains("CP_E2E_SOCKET_DROP_ARMED")) {
                if (cutArmed) {
                    return true;
                }
                cutArmed = true;
                failureSeen = false;
            }
            if (line.contains("Client write failed")) {
                if (!cutArmed || failureSeen) {
                    return true;
                }
                failureSeen = true;
            }
            if (line.contains("Reconnected successfully to server") && cutArmed) {
                cutArmed = false;
                failureSeen = false;
            }
        }
        return false;
    }
}
