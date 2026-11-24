package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxPocketOrderTest {
    @Test void waitsForBothLandingsAndKeepsTheDealtIdentityAfterTheCoreSwap() {
        var order = dealt("A_P", "2_T");
        order.remember(cards("A_P", "2_T"));
        for (int i=0;i<100;i++) order.update(false,true,.016f);
        assertTrue(order.busy());
        assertEquals(GdxPocketOrder.Pose.resting(0), order.pose("A_P",0));
        assertEquals(GdxPocketOrder.Pose.resting(1), order.pose("2_T",1));
        assertEquals(1, order.dealtSlot("2_T",1));
        finish(order);
        assertTrue(order.pose("A_P",0).x() < order.pose("2_T",1).x());
    }

    @Test void higherCardEndsOnTheLeftForEitherDealOrderIncludingAceAndTies() {
        for (String[] pair : new String[][]{{"K_C","4_P"},{"4_P","K_C"},{"A_P","K_C"},{"K_C","A_P"},{"9_P","9_C"}}) {
            var order=dealt(pair[0],pair[1]);
            finish(order);
            String higher=pair[0].startsWith("4") || pair[1].startsWith("A") || pair[0].startsWith("9") ? pair[1] : pair[0];
            assertEquals(-.18f, order.pose(higher,0).x(), .00001f);
            assertFalse(order.busy());
        }
    }

    @Test void cardsSlideDirectlyThenTheKingReturnsUnderTheAce() {
        var order = dealt("A_P", "K_C");
        order.update(true, true, 0);
        var ace = order.pose("A_P", 0);
        var king = order.pose("K_C", 1);
        float layerHeight = GdxPerspectiveTable.pocketLayerHeight(1, 1);
        boolean changedLayersWithClearance = false;
        boolean kingReturnedUnderAce = false;
        for (int i = 0; i < 800; i++) {
            order.update(true, true, .001f);
            var nextAce = order.pose("A_P", 0);
            var nextKing = order.pose("K_C", 1);
            assertTrue(Math.abs(nextAce.x() - ace.x()) < .02f, "Horizontal teleport");
            assertTrue(Math.abs(nextKing.layers() - king.layers()) * layerHeight < .005f,
                    "Vertical teleport");
            assertTrue(nextKing.layers() <= king.layers() + .00001f, "The king lifted");

            float gap = Math.abs(nextAce.x() - nextKing.x()) - halfWidth(nextAce) - halfWidth(nextKing);
            if (nextKing.layers() > .00001f) {
                assertTrue(nextAce.x() <= ace.x() + .00001f && nextKing.x() >= king.x() - .00001f,
                        "The cards reversed before they changed layers");
                if (nextKing.layers() < .99999f) {
                    assertTrue(gap >= .05f, "The cards need clearance to change layers");
                    changedLayersWithClearance = true;
                }
            }
            if (gap < 0f) {
                float heightDifference = (nextAce.layers() - nextKing.layers()) * layerHeight;
                assertTrue(Math.abs(heightDifference) >= GdxPlasticCard.thickness(1), "Intersecting sheets");
                kingReturnedUnderAce |= heightDifference > 0f && order.busy();
            }
            ace = nextAce;
            king = nextKing;
        }
        assertTrue(changedLayersWithClearance);
        assertTrue(kingReturnedUnderAce);
        assertEquals(GdxPocketOrder.Pose.resting(1), ace);
        assertEquals(GdxPocketOrder.Pose.resting(0), king);
    }

    private static float halfWidth(GdxPocketOrder.Pose pose) {
        double angle = Math.toRadians(pose.fan());
        return (float) (Math.cos(angle) + 1.40 * Math.abs(Math.sin(angle))) * GdxPerspectiveTable.EDGE_MARGIN_SCALE * .5f;
    }

    @Test void reconnectAndDisabledAnimationHaveStableSortedPresentation() {
        var order=new GdxPocketOrder();
        order.remember(cards("K_C","4_P"));
        order.update(true,true,.01f);
        assertFalse(order.busy());
        assertEquals(-.18f,order.pose("K_C",0).x());
        order.deal(0,"2_C"); order.deal(1,"10_P");
        order.update(true,false,.01f);
        assertFalse(order.busy());
        assertEquals(-.18f,order.pose("10_P",1).x());
    }

    @Test void concealedStraddleWaitsForAuthorizedIdentities() {
        var order=dealt("","");
        order.update(true,true,.02f);
        assertFalse(order.busy());
        order.remember(cards("K_C","4_P"));
        assertTrue(order.busy());
        finish(order);
        assertEquals(-.18f,order.pose("K_C",0).x());
    }

    @Test void disabledSortingStillWaitsForTheLastCardToLand() {
        var order=dealt("A_P","2_T");
        order.update(false,false,.02f);
        assertEquals(GdxPocketOrder.Pose.resting(0),order.pose("A_P",0));
        order.update(true,false,.02f);
        assertEquals(GdxPocketOrder.Pose.resting(1),order.pose("A_P",0));
    }

    @Test void entering3dAfterA2dDealDoesNotReplaySorting() {
        var order=new GdxPocketOrder();
        order.deal(0,"K_P",false); order.deal(1,"9_C",false);
        order.update(true,true,.01f);
        assertFalse(order.busy());
        assertEquals(-.18f,order.pose("K_P",0).x());
    }

    @Test void markerThicknessScalesWithItsDiameter() {
        assertEquals(3.8f,GdxPerspectiveTable.positionMarkerThickness(40),.0001f);
        assertEquals(7.6f,GdxPerspectiveTable.positionMarkerThickness(80),.0001f);
    }

    @Test void alreadyOrderedCardsAndPairsNeverSpreadOrBlockPeeking() {
        for (String[] pair : new String[][]{{"2_T","A_P"},{"9_C","9_P"}}) {
            var order = dealt(pair[0],pair[1]);
            assertFalse(order.busy());
            for (int i=0;i<80;i++) {
                order.update(true,true,.016f);
                assertEquals(GdxPocketOrder.Pose.resting(0),order.pose(pair[0],0));
                assertEquals(GdxPocketOrder.Pose.resting(1),order.pose(pair[1],1));
                assertFalse(order.busy());
            }
        }
    }

    private static GdxPocketOrder dealt(String a,String b) {
        var order=new GdxPocketOrder(); order.deal(0,a); order.deal(1,b); return order;
    }
    private static void finish(GdxPocketOrder order) {
        for(int i=0;i<100;i++) order.update(true,true,.016f);
    }
    private static List<TableSnapshot.CardSnapshot> cards(String a,String b) {
        return List.of(new TableSnapshot.CardSnapshot(a,true,true),new TableSnapshot.CardSnapshot(b,true,true));
    }
}
