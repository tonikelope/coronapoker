package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxChipCatalogTest {

    @Test
    void fullHdViewerSeparatesRackStackBuyInAndCloseControl() {
        CoronaPokerGdxTable.ChipCatalogLayout layout =
                CoronaPokerGdxTable.chipCatalogLayout(1_920f, 1_080f);

        assertEquals(1_540f, layout.panel().width, 0.001f);
        assertEquals(760f, layout.panel().height, 0.001f);
        assertFalse(layout.stackArea().overlaps(layout.totalArea()));
        assertFalse(layout.stackArea().overlaps(layout.buyInArea()));
        assertTrue(layout.totalArea().y > layout.buyInArea().y
                + layout.buyInArea().height);
        assertEquals(layout.totalArea().height,
                layout.buyInArea().height, 0.001f);
        assertFalse(layout.close().overlaps(layout.title()));
        assertTrue(layout.buyInArea().contains(layout.rebuyButton()),
                "the rebuy action belongs below the buy-in amount");
    }

    @Test
    void compactScreensKeepBothCounterPanelsInsideTheViewer() {
        CoronaPokerGdxTable.ChipCatalogLayout layout =
                CoronaPokerGdxTable.chipCatalogLayout(640f, 360f);

        assertTrue(layout.panel().x >= 0f);
        assertTrue(layout.panel().y >= 0f);
        assertTrue(layout.panel().x + layout.panel().width <= 640f);
        assertTrue(layout.panel().y + layout.panel().height <= 360f);
        assertTrue(layout.stackArea().width > layout.totalArea().width);
        assertTrue(layout.panel().contains(layout.totalArea()));
        assertTrue(layout.panel().contains(layout.buyInArea()));
        assertTrue(layout.buyInArea().contains(layout.rebuyButton()));
    }

    @Test
    void potUsesOneFullHeightStackCounterWithoutBuyInPanel() {
        CoronaPokerGdxTable.ChipCatalogLayout layout =
                CoronaPokerGdxTable.chipCatalogLayout(1_920f, 1_080f);

        Rectangle playerCounter = CoronaPokerGdxTable
                .chipCatalogStackCounterArea(layout, false);
        assertEquals(layout.totalArea(), playerCounter);

        Rectangle potCounter = CoronaPokerGdxTable
                .chipCatalogStackCounterArea(layout, true);
        assertEquals(layout.buyInArea().y, potCounter.y, 0.001f);
        assertEquals(layout.totalArea().y + layout.totalArea().height,
                potCounter.y + potCounter.height, 0.001f);
        assertEquals(layout.totalArea().x, potCounter.x, 0.001f);
        assertEquals(layout.totalArea().width, potCounter.width, 0.001f);
        assertTrue(potCounter.y <= layout.buyInArea().y);
        assertTrue(potCounter.y + potCounter.height
                >= layout.totalArea().y + layout.totalArea().height);
        assertFalse(CoronaPokerGdxTable
                .chipCatalogCounterTitleVisible(true),
                "the pot counter must show only its centred amount");
        assertTrue(CoronaPokerGdxTable
                .chipCatalogCounterTitleVisible(false),
                "player counters keep the STACK heading");
    }

    @Test
    void topFaceAndCylindricalWallMarksMeetWithoutColourSeams() {
        for (int variant = 0; variant < 8; variant++) {
            float phase = CoronaPokerGdxTable.stackChipRotationPhase(variant);
            for (int offsetX = -116; offsetX <= 116; offsetX++) {
                float normalizedX = offsetX / 116f;
                float frontY = (float) Math.sqrt(Math.max(0f,
                        116f * 116f - offsetX * offsetX));
                assertEquals(CoronaPokerGdxTable.stackChipEdgeMarkAt(
                        normalizedX, phase),
                        CoronaPokerGdxTable.stackChipFaceMarkAt(
                                offsetX, frontY, phase),
                        "face and wall must use the same white insert at their join");
            }
        }
    }

    @Test
    void tallDenominationsBecomeRepeatedUncompressedPiles() {
        GdxStackChipLayout.Layout stack = stackWithCounts(25, 1, 0, 11);

        List<CoronaPokerGdxTable.DetailedStackPile> piles =
                CoronaPokerGdxTable.detailedStackPiles(stack);

        assertEquals(6, piles.size());
        assertTrue(piles.stream().allMatch(pile
                -> pile.chipCount() >= 1 && pile.chipCount() <= 10));
        assertEquals(37, piles.stream().mapToInt(
                CoronaPokerGdxTable.DetailedStackPile::chipCount).sum());
        assertEquals(stack.representedMinor(), piles.stream().mapToLong(pile
                -> (long) pile.chipCount()
                        * 10L * GdxChipDenominations.units(
                                pile.denominationColumn())).sum());
        assertEquals(List.of(3, 3, 1, 0, 0, 0),
                piles.stream().map(
                        CoronaPokerGdxTable.DetailedStackPile
                                ::denominationColumn).toList());
    }

    @Test
    void detailedPilesUseStaggeredPerspectiveWithoutLeavingRack() {
        GdxStackChipLayout.Layout stack = stackWithCounts(5, 8, 4, 6);
        Rectangle area = new Rectangle(120f, 90f, 1_000f, 560f);

        List<CoronaPokerGdxTable.DetailedStackPlacement> placements =
                CoronaPokerGdxTable.detailedStackPlacements(stack, area);

        assertFalse(placements.isEmpty());
        assertTrue(placements.stream().anyMatch(
                CoronaPokerGdxTable.DetailedStackPlacement::rear));
        assertTrue(placements.stream().anyMatch(placement
                -> !placement.rear()));
        float rearBase = placements.stream().filter(
                CoronaPokerGdxTable.DetailedStackPlacement::rear)
                .map(CoronaPokerGdxTable.DetailedStackPlacement::baseCenterY)
                .min(Float::compare).orElseThrow();
        float frontBase = placements.stream().filter(
                placement -> !placement.rear())
                .map(CoronaPokerGdxTable.DetailedStackPlacement::baseCenterY)
                .min(Float::compare).orElseThrow();
        assertTrue(rearBase > frontBase);
        assertEquals(placements.get(0).chipDiameter() * 5f / 22f,
                rearBase - frontBase, 0.001f,
                "the rear row must use the same perspective rise as HUD racks");
        List<CoronaPokerGdxTable.DetailedStackPlacement> foreground =
                placements.stream().filter(placement -> !placement.rear())
                        .toList();
        assertEquals(2, foreground.size());
        assertEquals(foreground.get(0).baseCenterY(),
                foreground.get(1).baseCenterY(), 0.001f);
        assertEquals(CoronaPokerGdxTable.detailedStackPhysicalBottom(
                foreground.get(0)),
                CoronaPokerGdxTable.detailedStackPhysicalBottom(
                        foreground.get(1)), 0.001f,
                "both foreground columns must rest on the same plane");
        assertTrue(Math.abs(foreground.get(0).centerX()
                - foreground.get(1).centerX())
                > foreground.get(0).chipDiameter(),
                "foreground columns must be separated, not perched");
        assertTrue(placements.stream().allMatch(placement
                -> placement.centerX() - placement.chipDiameter() / 2f
                        >= area.x - 0.001f
                && placement.centerX() + placement.chipDiameter() / 2f
                        <= area.x + area.width + 0.001f));
        assertTrue(placements.stream().allMatch(placement
                -> Math.abs(placement.chipDiameter() - 140f) < 0.001f),
                "full-size chips must remain native while the rack fits");
        List<CoronaPokerGdxTable.DetailedStackPlacement> leftToRight =
                placements.stream().sorted(
                        java.util.Comparator.comparingDouble(
                                CoronaPokerGdxTable.DetailedStackPlacement
                                        ::centerX)).toList();
        for (int index = 1; index < leftToRight.size(); index++) {
            float diameter = leftToRight.get(index).chipDiameter();
            assertTrue(leftToRight.get(index).centerX()
                    - leftToRight.get(index - 1).centerX()
                    >= diameter * 1.04f - 0.001f,
                    "rear/front neighbours must have independent physical bases");
        }

        List<CoronaPokerGdxTable.DetailedStackPlacement> constrained =
                CoronaPokerGdxTable.detailedStackPlacements(stack,
                        new Rectangle(0f, 0f, 300f, 180f));
        assertTrue(constrained.stream().allMatch(placement
                -> placement.chipDiameter() < 140f),
                "only an overflowing rack may reduce its chip diameter");
        assertEquals(CoronaPokerGdxTable.STACK_CHIP_PHYSICAL_THICKNESS_RATIO,
                CoronaPokerGdxTable.stackChipPhysicalThickness(140f) / 140f,
                0.000_001f);
    }

    @Test
    void detailedPileIrregularityIsStableGroundedAndChangesPerOpening() {
        float diameter = 140f;
        int firstSeed = CoronaPokerGdxTable.nextChipCatalogRackSeed(
                0, 0x12345678, 1);
        int secondSeed = CoronaPokerGdxTable.nextChipCatalogRackSeed(
                firstSeed, 0x12345678, 2);

        assertTrue(firstSeed != secondSeed);
        assertEquals(0f, CoronaPokerGdxTable
                .detailedStackChipLateralOffset(firstSeed, 0, 0, diameter),
                0.000_001f, "the bottom chip anchors the stack");
        float first = CoronaPokerGdxTable.detailedStackChipLateralOffset(
                firstSeed, 0, 4, diameter);
        assertEquals(first, CoronaPokerGdxTable
                .detailedStackChipLateralOffset(firstSeed, 0, 4, diameter),
                0.000_001f, "an open dialog must not jitter frame to frame");
        assertTrue(Math.abs(first) <= diameter * 0.026f + 0.000_001f);
        assertTrue(first != CoronaPokerGdxTable
                .detailedStackChipLateralOffset(secondSeed, 0, 4, diameter),
                "a new opening must generate a new arrangement");
        long distinctOffsets = java.util.stream.IntStream.range(1, 10)
                .mapToObj(level -> CoronaPokerGdxTable
                        .detailedStackChipLateralOffset(firstSeed, 0, level,
                                diameter))
                .distinct().count();
        assertTrue(distinctOffsets > 1,
                "upper chips must not form a perfect cylinder");
    }

    @Test
    void overflowPilesStayWithTheirColourAndTheWholeSetIsCentred() {
        GdxStackChipLayout.Layout stack = stackWithCounts(25, 8, 14, 11);
        Rectangle area = new Rectangle(120f, 90f, 1_000f, 560f);

        List<CoronaPokerGdxTable.DetailedStackPlacement> placements =
                CoronaPokerGdxTable.detailedStackPlacements(stack, area);
        List<Integer> groupedColumns = placements.stream()
                .map(placement -> placement.pile().denominationColumn())
                .toList();

        assertEquals(List.of(1, 3, 3, 0, 0, 0, 2, 2), groupedColumns,
                "overflow must extend each colour group, not clone the rack");
        List<CoronaPokerGdxTable.DetailedStackPlacement> greenOverflow =
                placements.stream().filter(placement
                        -> placement.pile().denominationColumn() == 3)
                        .sorted(java.util.Comparator.comparingDouble(
                                CoronaPokerGdxTable.DetailedStackPlacement
                                        ::centerX))
                        .toList();
        assertEquals(greenOverflow.get(0).chipDiameter() * 1.04f,
                greenOverflow.get(1).centerX()
                        - greenOverflow.get(0).centerX(), 0.001f,
                "overflow piles need a readable base instead of looking "
                        + "propped against their neighbour");
        placements.stream().collect(java.util.stream.Collectors.groupingBy(
                CoronaPokerGdxTable.DetailedStackPlacement::baseCenterY))
                .values().forEach(row -> {
                    List<CoronaPokerGdxTable.DetailedStackPlacement> ordered =
                            row.stream().sorted(
                                    java.util.Comparator.comparingDouble(
                                            CoronaPokerGdxTable
                                                    .DetailedStackPlacement
                                                    ::centerX))
                                    .toList();
                    for (int index = 1; index < ordered.size(); index++) {
                        float diameter = ordered.get(index).chipDiameter();
                        assertTrue(ordered.get(index).centerX()
                                - ordered.get(index - 1).centerX()
                                >= diameter * 1.04f - 0.001f,
                                "neighbouring bases must have a visible gap");
                    }
                });
        double left = placements.stream().mapToDouble(placement
                -> placement.centerX() - placement.chipDiameter() / 2f)
                .min().orElseThrow();
        double right = placements.stream().mapToDouble(placement
                -> placement.centerX() + placement.chipDiameter() / 2f)
                .max().orElseThrow();
        assertEquals(area.x + area.width / 2f, (left + right) / 2f,
                0.001f, "the complete multi-column set must be centred");
    }

    @Test
    void hugeStacksUseDepthRowsOnlyWhenThatPreservesLargerChips() {
        GdxStackChipLayout.Layout stack = stackWithCounts(80, 80, 80, 80);
        Rectangle area = new Rectangle(120f, 90f, 1_000f, 560f);

        List<CoronaPokerGdxTable.DetailedStackPlacement> placements =
                CoronaPokerGdxTable.detailedStackPlacements(stack, area);

        assertEquals(32, placements.size());
        assertTrue(placements.stream().map(
                CoronaPokerGdxTable.DetailedStackPlacement::baseCenterY)
                .distinct().count() > 1,
                "only an overflowing stack should gain depth rows");
        assertTrue(placements.get(0).chipDiameter() > 25f,
                "adaptive rows must recover useful chip size");
        assertTrue(placements.stream().allMatch(placement -> {
            float diameter = placement.chipDiameter();
            float bottom = CoronaPokerGdxTable.detailedStackPhysicalBottom(
                    placement);
            float top = placement.baseCenterY()
                    + diameter * 0.62f / 2f
                    + (placement.pile().chipCount() - 1)
                            * CoronaPokerGdxTable
                                    .stackChipPhysicalThickness(diameter);
            return placement.centerX() - diameter / 2f >= area.x - 0.001f
                    && placement.centerX() + diameter / 2f
                            <= area.x + area.width + 0.001f
                    && bottom >= area.y - 0.001f
                    && top <= area.y + area.height + 0.001f;
        }));
        List<Integer> orderedColumns = placements.stream().map(placement
                -> placement.pile().denominationColumn()).toList();
        assertTrue(lastIndexOf(orderedColumns, 1)
                < firstIndexOf(orderedColumns, 3));
        assertTrue(lastIndexOf(orderedColumns, 3)
                < firstIndexOf(orderedColumns, 0));
        assertTrue(lastIndexOf(orderedColumns, 0)
                < firstIndexOf(orderedColumns, 2));
    }

    @Test
    void openViewerReconcilesImmediatelyWhenTheDisplayedStackChanges() {
        GdxStackChipLayout.Layout initial = GdxStackChipLayout.compose(
                10d, 0.10d, 10d);

        GdxStackChipLayout.Layout unchanged = CoronaPokerGdxTable
                .detailedStackLiveLayout(initial, 10d, 0.10d, 10d);
        GdxStackChipLayout.Layout updated = CoronaPokerGdxTable
                .detailedStackLiveLayout(initial, 13.70d, 0.10d, 10d);

        assertTrue(unchanged == initial,
                "an unchanged live amount must reuse the physical rack");
        assertEquals(1_370L, updated.representedMinor(),
                "an open viewer must reflect the new counter immediately");
        assertEquals(1_000L, initial.representedMinor(),
                "the viewer fallback must not mutate the HUD rack");
    }

    @Test
    void finalSummaryAvatarsUseVisibleButRestrainedCornerRounding() {
        assertEquals(12.48f,
                CoronaPokerGdxTable.finalSummaryAvatarCornerRadius(104f),
                0.001f);
        assertEquals(7f,
                CoronaPokerGdxTable.finalSummaryAvatarCornerRadius(20f),
                0.001f);
        assertEquals(15f,
                CoronaPokerGdxTable.finalSummaryAvatarCornerRadius(200f),
                0.001f);
    }

    @Test
    void printedDenominationSharesTheTopChipRotationButVariesByOpening() {
        int seed = 0x12345678;
        int column = 2;
        int level = 7;
        float angle = CoronaPokerGdxTable
                .detailedStackPrintedValueAngleDegrees(seed, column, level);
        int variant = CoronaPokerGdxTable.stackChipRotationVariant(seed,
                column, level);
        float facePhase = CoronaPokerGdxTable.stackChipRotationPhase(variant)
                * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        assertEquals(angle, CoronaPokerGdxTable
                .detailedStackPrintedValueAngleDegrees(seed, column, level),
                0.000_001f);
        assertEquals(facePhase, angle % 45f, 0.000_01f,
                "the print must remain registered to the repeating face marks");
        long distinct = java.util.stream.IntStream.range(0, 24)
                .mapToObj(opening -> CoronaPokerGdxTable
                        .detailedStackPrintedValueAngleDegrees(
                                seed + opening, column, level))
                .distinct().count();
        assertTrue(distinct > 8,
                "new openings must not reuse one fixed print orientation");
    }

    @Test
    void rebuyButtonIsExclusiveToAnEligibleLocalStack() {
        assertTrue(CoronaPokerGdxTable.detailedStackRebuyButtonVisible(
                false, true, true));
        assertFalse(CoronaPokerGdxTable.detailedStackRebuyButtonVisible(
                false, false, true));
        assertFalse(CoronaPokerGdxTable.detailedStackRebuyButtonVisible(
                true, true, true));
        assertFalse(CoronaPokerGdxTable.detailedStackRebuyButtonVisible(
                false, true, false));
    }

    @Test
    void viewerReturnsOnlyAfterItsRebuyDialogAndAnyQueuedModalClose() {
        assertFalse(CoronaPokerGdxTable.chipCatalogReturnReady(
                true, true, false, false),
                "the viewer must not reopen before the rebuy dialog arrives");
        assertFalse(CoronaPokerGdxTable.chipCatalogReturnReady(
                true, false, true, false),
                "the viewer must remain behind its open rebuy dialog");
        assertFalse(CoronaPokerGdxTable.chipCatalogReturnReady(
                true, false, false, true),
                "a queued modal must retain foreground ownership");
        assertTrue(CoronaPokerGdxTable.chipCatalogReturnReady(
                true, false, false, false));
        assertFalse(CoronaPokerGdxTable.chipCatalogReturnReady(
                false, false, false, false));
    }

    @Test
    void rebuyReturnTracksEitherTheAmountDialogOrAnImmediateNotice() {
        GdxTableDialog rebuy = new GdxTableDialog("RECOMPRAR", "", 700,
                0, false, "CANCELAR", 1, 10, 5);
        GdxTableDialog error = new GdxTableDialog(GdxTableDialog.Kind.ERROR,
                "NO SE PUEDE RECOMPRAR",
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                700, 0);
        GdxTableDialog info = new GdxTableDialog(GdxTableDialog.Kind.INFO,
                "RECOMPRA NO DISPONIBLE",
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                700, 0);
        GdxTableDialog unrelated = new GdxTableDialog(
                GdxTableDialog.Kind.CONFIRM, "CONFIRMAR",
                com.tonikelope.coronapoker.core.game.GameDialogSink.Icon.NONE,
                700, 0);

        assertTrue(CoronaPokerGdxTable
                .chipCatalogReturnDialogCandidate(rebuy));
        assertTrue(CoronaPokerGdxTable
                .chipCatalogReturnDialogCandidate(error),
                "an immediate rebuy error must return to the stack viewer");
        assertTrue(CoronaPokerGdxTable
                .chipCatalogReturnDialogCandidate(info));
        assertFalse(CoronaPokerGdxTable
                .chipCatalogReturnDialogCandidate(unrelated));
        assertFalse(CoronaPokerGdxTable
                .chipCatalogReturnDialogCandidate(null));
    }

    private static int firstIndexOf(List<Integer> values, int value) {
        return values.indexOf(value);
    }

    private static int lastIndexOf(List<Integer> values, int value) {
        return values.lastIndexOf(value);
    }

    private static GdxStackChipLayout.Layout stackWithCounts(int... counts) {
        java.util.ArrayList<GdxStackChipLayout.Column> columns =
                new java.util.ArrayList<>();
        for (int column = 0; column < GdxStackChipLayout.COLUMN_COUNT;
                column++) {
            java.util.ArrayList<GdxStackChipLayout.Chip> chips =
                    new java.util.ArrayList<>();
            int count = column < counts.length ? counts[column] : 0;
            long amount = 10L * GdxChipDenominations.units(column);
            int color = GdxChipDenominations.color(column);
            for (int index = 0; index < count; index++) {
                chips.add(new GdxStackChipLayout.Chip(amount, color));
            }
            columns.add(new GdxStackChipLayout.Column(color, chips));
        }
        return new GdxStackChipLayout.Layout(columns);
    }
}
