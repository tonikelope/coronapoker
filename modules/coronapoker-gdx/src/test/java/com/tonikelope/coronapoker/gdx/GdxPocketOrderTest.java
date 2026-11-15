package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxPocketOrderTest {
    @Test void waitsForBothLandingsAndKeepsTheDealtIdentityAfterTheCoreSwap() {
        var order = dealt("2_T", "A_P");
        order.remember(cards("A_P", "2_T"));
        for (int i=0;i<100;i++) order.update(false,true,.016f);
        assertTrue(order.busy());
        assertEquals(GdxPocketOrder.Pose.resting(0), order.pose("2_T",1));
        assertEquals(GdxPocketOrder.Pose.resting(1), order.pose("A_P",0));
        assertEquals(0, order.dealtSlot("2_T",1));
        finish(order);
        assertTrue(order.pose("A_P",0).x() < order.pose("2_T",1).x());
    }

    @Test void higherCardEndsOnTheLeftForEitherDealOrderIncludingAceAndTies() {
        for (String[] pair : new String[][]{{"K_C","4_P"},{"4_P","K_C"},{"A_P","K_C"},{"K_C","A_P"},{"9_P","9_C"}}) {
            var order=dealt(pair[0],pair[1]);
            finish(order);
            String higher=pair[0].startsWith("4") || pair[1].startsWith("A") ? pair[1] : pair[0];
            assertEquals(-.18f, order.pose(higher,0).x(), .00001f);
            assertFalse(order.busy());
        }
    }

    @Test void crossingSheetsStayApartAndReturnContinuouslyToTheirStack() {
        var order=dealt("A_P","2_T");
        order.update(true,true,0);
        var a=order.pose("A_P",0);
        var b=order.pose("2_T",1);
        for(int i=0;i<800;i++) {
            order.update(true,true,.001f);
            var nextA=order.pose("A_P",0); var nextB=order.pose("2_T",1);
            assertTrue(Math.abs(nextA.x()-a.x()) < .02f, "Horizontal teleport");
            assertTrue(Math.abs(nextB.lift()-b.lift()) < .005f, "Vertical teleport");
            float ha=nextA.layers()*GdxPerspectiveTable.pocketLayerHeight(1,1)+nextA.lift();
            float hb=nextB.layers()*GdxPerspectiveTable.pocketLayerHeight(1,1)+nextB.lift();
            // A conservative rotated-card footprint. Layers can only change
            // while the sheets are fully separated on the table.
            if(Math.abs(nextA.x()-nextB.x()) < 1.08f) {
                assertTrue(Math.abs(ha-hb) >= GdxPlasticCard.thickness(1), "Intersecting sheets");
            }
            a=nextA; b=nextB;
        }
        assertEquals(GdxPocketOrder.Pose.resting(1),a);
        assertEquals(GdxPocketOrder.Pose.resting(0),b);
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
