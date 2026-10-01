package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.table.TableSnapshot;
import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.Set;

/** Functional oracle for the semantic text exposed by a GDX table. */
final class GdxFunctionalLabelOracle {

    private GdxFunctionalLabelOracle() { }

    /**
     * Validate both shipped catalogues. The result is independent from the
     * developer's persisted language setting.
     */
    static void assertProjectedLabelContract(TableVisualEvent event,
            GdxTableViewState projection) {
        assertProjectedLabelContract(event, projection,
                new GdxGameText("es"));
        assertProjectedLabelContract(event, projection,
                new GdxGameText("en"));
    }

    /** Validate the catalogue used by the actual product table. */
    static void assertProjectedLabelContract(TableVisualEvent event,
            GdxTableViewState projection, GdxGameText text) {
        if (event instanceof TableVisualEvent.PlayerAction action) {
            String visible = CoronaPokerGdxTable.localizedActionLabel(
                    projection.actionKind(action.nickname()),
                    projection.actionLabel(action.nickname()), text);
            String expected = CoronaPokerGdxTable.localizedActionLabel(
                    action.kind(), action.label(), text);
            assertEquals(expected, visible,
                    "GDX action caption differs from its semantic event");
            assertFalse(visible.contains(action.nickname()),
                    "GDX action caption leaked the player nickname");
        }
        for (TableSnapshot.PlayerSnapshot player
                : projection.snapshot().players()) {
            if (CoronaPokerGdxTable.actionKindFromLegacyLabel(
                    player.lastAction()) == null) {
                continue;
            }
            String visible = CoronaPokerGdxTable.localizedActionLabel(
                    null, player.lastAction(), text);
            assertFalse(visible.contains(player.nickname()),
                    "recovered GDX action caption leaked its nickname: "
                    + visible);
        }
        if (event instanceof TableVisualEvent.PlayerDeparture departure) {
            assertEquals(departure.label(),
                    projection.actionLabel(departure.nickname()),
                    "departure caption was not projected verbatim");
            assertEquals(departure.label(),
                    CoronaPokerGdxTable.localizedActionLabel(null,
                            projection.actionLabel(departure.nickname()), text),
                    "departure caption was reinterpreted as a poker action");
            assertTrue(Set.of(
                    new GdxGameText("es").translate("ui.se_pira"),
                    new GdxGameText("en").translate("ui.se_pira"))
                    .contains(departure.label()),
                    "a definitive departure must say that the player leaves");
            assertFalse(Set.of(
                    new GdxGameText("es").translate(
                            "table.player_reconnecting"),
                    new GdxGameText("en").translate(
                            "table.player_reconnecting"))
                    .contains(departure.label()),
                    "a definitive departure cannot say RECONNECTING");
        } else if (event instanceof TableVisualEvent.RebuyDecision decision) {
            TableVisualEvent.RebuyDecision.Phase projected
                    = projection.rebuyDecision(decision.nickname());
            if (decision.phase()
                    == TableVisualEvent.RebuyDecision.Phase.CLEARED) {
                assertEquals(null, projected,
                        "cleared rebuy decision remained visible");
            } else {
                assertEquals(decision.phase(), projected,
                        "remote rebuy caption state is stale");
            }
            if (decision.phase()
                    == TableVisualEvent.RebuyDecision.Phase.WAITING) {
                assertEquals(text.translate("rebuy.recompra_3"),
                        CoronaPokerGdxTable.remoteRebuyLabel(
                                decision.phase(), 10, true, text));
                assertEquals(text.translate("rebuy.recompra_3") + " (10)",
                        CoronaPokerGdxTable.remoteRebuyLabel(
                                decision.phase(), 10, false, text));
            } else if (decision.phase()
                    == TableVisualEvent.RebuyDecision.Phase.REBOUGHT) {
                assertEquals(text.translate("rebuy.recompra_4"),
                        CoronaPokerGdxTable.remoteRebuyLabel(
                                decision.phase(), 0, false, text));
            }
        } else if (event instanceof TableVisualEvent.HandResult result) {
            assertEquals(result.handName(),
                    projection.resolvedHandName(result.nickname()),
                    "showdown hand caption differs from the result event");
        } else if (event instanceof TableVisualEvent.TableInfo info) {
            assertEquals(info.smallBlind(), projection.smallBlind(),
                    "visible small blind is stale");
            assertEquals(info.bigBlind(), projection.bigBlind(),
                    "visible big blind is stale");
            assertEquals(info.handNumber(), projection.handNumber(),
                    "visible hand counter is stale");
            assertEquals(info.blindIncreaseInterval(),
                    projection.blindIncreaseInterval(),
                    "visible blind-rise interval is stale");
            assertEquals(info.blindIncreaseType(),
                    projection.blindIncreaseType(),
                    "visible blind-rise unit is stale");
            assertEquals(info.blindIncreaseCount(),
                    projection.blindIncreaseCount(),
                    "visible blind-rise count is stale");
            String visibleBlinds = CoronaPokerGdxTable.communityBlindsText(
                    text, projection.smallBlind(), projection.bigBlind(),
                    projection.anteEnabled(),
                    projection.blindIncreaseInterval(),
                    projection.blindIncreaseType(),
                    projection.blindIncreaseCount());
            assertTrue(visibleBlinds.contains(
                    CoronaPokerGdxTable.formatAmount(info.smallBlind()))
                    && visibleBlinds.contains(
                            CoronaPokerGdxTable.formatAmount(info.bigBlind())),
                    "GDX blind label omits the authoritative values: "
                    + visibleBlinds);
            String visibleHand = CoronaPokerGdxTable.communityHandText(text,
                    projection.handNumber(), projection.maximumHands(),
                    projection.lastHand());
            if (info.handNumber() > 0 && !projection.lastHand()) {
                assertTrue(visibleHand.contains(
                        Integer.toString(info.handNumber())),
                        "GDX hand label omits the authoritative hand number: "
                        + visibleHand);
            }
        } else if (event instanceof TableVisualEvent.CallCost callCost) {
            assertEquals(callCost.text(), projection.callCostText(),
                    "call-cost label did not appear/disappear with its event");
            assertEquals(callCost.aggressorNickname(),
                    projection.callCostAggressorNickname(),
                    "call-cost label targets the wrong aggressor");
        } else if (event instanceof TableVisualEvent.HandLimitStatus limit) {
            assertEquals(limit.maximumHands(), projection.maximumHands(),
                    "hand-limit label is stale");
        } else if (event instanceof TableVisualEvent.LastHandStatus last) {
            assertEquals(last.enabled(), projection.lastHand(),
                    "last-hand label is stale");
        } else if (event instanceof TableVisualEvent.HandBoundary boundary
                && boundary.phase()
                == TableVisualEvent.HandBoundary.Phase.PREPARE) {
            assertEquals("", projection.callCostText(),
                    "new hand retained the previous call-cost label");
            assertEquals("", projection.runItTwicePotPrefix(),
                    "new hand retained the previous RIT pot label");
            for (TableSnapshot.PlayerSnapshot player
                    : projection.snapshot().players()) {
                assertEquals("", projection.actionLabel(player.nickname()),
                        "new hand retained a prior action caption for "
                        + player.nickname());
                assertFalse(projection.hasHandResult(player.nickname()),
                        "new hand retained a prior showdown caption for "
                        + player.nickname());
            }
        }

        String visiblePot = CoronaPokerGdxTable.communityPotText(text,
                projection.runItTwicePotPrefix(), projection.snapshot().pot());
        assertFalse(visiblePot.isBlank(), "GDX pot label cannot be blank");
        assertTrue(visiblePot.endsWith(CoronaPokerGdxTable.formatAmount(
                projection.snapshot().pot())),
                "GDX pot label omits the projected amount: " + visiblePot);
        if (!projection.runItTwicePotPrefix().isBlank()) {
            assertTrue(visiblePot.startsWith(
                    projection.runItTwicePotPrefix()),
                    "GDX pot label lost its active RIT board: " + visiblePot);
        }
    }
}
