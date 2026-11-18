/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Arrays;

/** Assigns new piles to the nearest free place before growing existing piles. */
final class GdxTableChipSlots {
    static final int POT_SLOT_COUNT = 9;
    static final int POT_UPPER_CENTER = 1;
    private static final int POT_LEFT_INNER = 0, POT_RIGHT_INNER = 2;
    private static final int POT_LEFT_REAR = 3, POT_RIGHT_REAR = 6;
    private static final int POT_LEFT_OUTER = 7, POT_RIGHT_OUTER = 8;
    private static final int POT_UPPER_LEFT = 4, POT_UPPER_RIGHT = 5;

    // Lowest to highest denomination. Each row starts with its dedicated
    // front position and exhausts both sides before going above the label.
    private static final int[][] POT_SLOT_ORDER = {
        {POT_RIGHT_OUTER, POT_RIGHT_REAR, POT_RIGHT_INNER, POT_LEFT_OUTER, POT_LEFT_REAR, POT_LEFT_INNER,
            POT_UPPER_CENTER, POT_UPPER_RIGHT, POT_UPPER_LEFT},
        {POT_RIGHT_INNER, POT_RIGHT_REAR, POT_RIGHT_OUTER, POT_LEFT_INNER, POT_LEFT_REAR, POT_LEFT_OUTER,
            POT_UPPER_CENTER, POT_UPPER_RIGHT, POT_UPPER_LEFT},
        {POT_LEFT_INNER, POT_LEFT_REAR, POT_LEFT_OUTER, POT_RIGHT_INNER, POT_RIGHT_REAR, POT_RIGHT_OUTER,
            POT_UPPER_CENTER, POT_UPPER_LEFT, POT_UPPER_RIGHT},
        {POT_LEFT_OUTER, POT_LEFT_REAR, POT_LEFT_INNER, POT_RIGHT_OUTER, POT_RIGHT_REAR, POT_RIGHT_INNER,
            POT_UPPER_CENTER, POT_UPPER_LEFT, POT_UPPER_RIGHT},
    };

    static final int LOCAL_COLUMNS_PER_SIDE = 2;
    static final int LOCAL_SLOTS_PER_ROW = LOCAL_COLUMNS_PER_SIDE * 2;
    static final int LOCAL_SLOT_COUNT = LOCAL_SLOTS_PER_ROW * 3;
    private static final int COLOURS = GdxChipDenominations.COLUMN_COUNT;
    private static final int CAPACITY = GdxTableChipRack.CHIPS_PER_PILE;

    private final int[][] slots;
    private final int[] used = new int[COLOURS];
    private final boolean[] occupied;
    private final int[] localPileOrder;
    private boolean localSlotsDirty;

    static GdxTableChipSlots local() {
        return new GdxTableChipSlots(LOCAL_SLOT_COUNT, true);
    }

    static GdxTableChipSlots pot() {
        return new GdxTableChipSlots(POT_SLOT_COUNT, false);
    }

    private GdxTableChipSlots(int slotCount, boolean local) {
        slots = new int[COLOURS][slotCount];
        occupied = new boolean[slotCount];
        localPileOrder = local ? new int[slotCount] : null;
    }

    void arrange(int[] counts) {
        for (int column = 0; column < COLOURS; column++) {
            while (used[column] > pilesNeeded(counts[column])) releaseLast(column);
        }
        // Every denomination gets a base before a second pile gets a place.
        for (int priority = 0; priority < COLOURS; priority++) {
            int column = allocationColumn(priority);
            if (counts[column] <= 0 || used[column] > 0) continue;
            if (localPileOrder == null) {
                take(column, claimPotBase(column));
                continue;
            }
            if (nextFree() < 0) {
                int donor = 0;
                for (int i = 1; i < COLOURS; i++) {
                    if (used[i] > used[donor]) donor = i;
                }
                releaseLast(donor);
            }
            take(column, nextFree());
        }
        for (int depth = 1; depth < occupied.length; depth++) {
            for (int priority = 0; priority < COLOURS; priority++) {
                int column = allocationColumn(priority);
                if (used[column] != depth || pilesNeeded(counts[column]) <= depth) continue;
                int free = localPileOrder == null ? nextPotSlot(column) : nextFree();
                if (free < 0) return;
                take(column, free);
            }
        }
    }

    private static int allocationColumn(int priority) {
        return COLOURS - 1 - priority;
    }

    private int claimPotBase(int column) {
        int home = POT_SLOT_ORDER[column][0];
        if (occupied[home]) {
            // Only surplus piles can borrow another colour's front position.
            for (int borrower = 0; borrower < COLOURS; borrower++) {
                for (int pile = 1; pile < used[borrower]; pile++) {
                    if (slots[borrower][pile] == home) {
                        release(borrower, pile);
                        return home;
                    }
                }
            }
        }
        return home;
    }

    private int nextPotSlot(int column) {
        for (int slot : POT_SLOT_ORDER[column]) {
            if (!occupied[slot]) return slot;
        }
        return -1;
    }

    void compactPotSides() {
        for (int column = COLOURS - 1; column >= 0; column--) {
            for (int pile = 1; pile < used[column]; pile++) {
                int current = slots[column][pile];
                if (side(current) != 0) continue;
                int free = nextPotSlot(column);
                // Every colour tries all six side positions first.
                if (free < 0 || side(free) == 0) return;
                occupied[current] = false;
                occupied[free] = true;
                slots[column][pile] = free;
            }
        }
    }

    void compactLocalRows() {
        if (!localSlotsDirty) return;
        int size = 0;
        // Reserve one pile per denomination first, then fill toward the back.
        for (int pile = 0; pile < occupied.length; pile++) {
            for (int priority = 0; priority < COLOURS; priority++) {
                if (pile < used[allocationColumn(priority)]) {
                    localPileOrder[size++] = priority * LOCAL_SLOT_COUNT + pile;
                }
            }
        }
        Arrays.fill(occupied, false);
        for (int first = 0; first < size; first += LOCAL_SLOTS_PER_ROW) {
            int end = Math.min(size, first + LOCAL_SLOTS_PER_ROW);
            Arrays.sort(localPileOrder, first, end);
            for (int slot = first; slot < end; slot++) {
                int pileKey = localPileOrder[slot];
                int column = allocationColumn(pileKey / LOCAL_SLOT_COUNT);
                slots[column][pileKey % LOCAL_SLOT_COUNT] = slot;
                occupied[slot] = true;
            }
        }
        localSlotsDirty = false;
    }

    static int pilesNeeded(int chips) {
        return chips == 0 ? 0 : 1 + (chips - 1) / CAPACITY;
    }

    private int nextFree() {
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot]) return slot;
        }
        return -1;
    }

    private void releaseLast(int column) {
        release(column, used[column] - 1);
    }

    private void release(int column, int pile) {
        int slot = slots[column][pile];
        slots[column][pile] = slots[column][--used[column]];
        occupied[slot] = false;
        localSlotsDirty = localPileOrder != null;
    }

    private void take(int column, int slot) {
        slots[column][used[column]++] = slot;
        occupied[slot] = true;
        localSlotsDirty = localPileOrder != null;
    }

    int piles(int column) {
        return used[column];
    }

    int slot(int column, int pile) {
        if (used[column] > 0) return slots[column][pile];
        return localPileOrder == null ? POT_SLOT_ORDER[column][0] : allocationColumn(column);
    }

    boolean ownsSlot(int column, int slot) {
        for (int pile = 0; pile < used[column]; pile++) {
            if (slots[column][pile] == slot) return true;
        }
        return false;
    }

    int pileForChip(int column, int index) {
        int piles = Math.max(1, used[column]);
        int capacity = piles * CAPACITY;
        return index < capacity ? index / CAPACITY : (index - capacity) % piles;
    }

    int levelForChip(int column, int index) {
        int piles = Math.max(1, used[column]);
        int capacity = piles * CAPACITY;
        return index < capacity ? index % CAPACITY : CAPACITY + (index - capacity) / piles;
    }

    int height(int column, int pile, int chips) {
        int base = Math.min(CAPACITY, Math.max(0, chips - pile * CAPACITY));
        int piles = used[column];
        int overflow = Math.max(0, chips - piles * CAPACITY);
        return base + (overflow + piles - 1 - pile) / piles;
    }

    static int side(int slot) {
        return switch (slot) {
            case POT_LEFT_INNER, POT_LEFT_REAR, POT_LEFT_OUTER -> -1;
            case POT_RIGHT_INNER, POT_RIGHT_REAR, POT_RIGHT_OUTER -> 1;
            default -> 0;
        };
    }

    static float offsetX(int slot, float diameter, float labelHalfWidth, float gap) {
        float pitch = diameter * 1.15f;
        float near = labelHalfWidth + gap + diameter * .5f;
        return switch (slot) {
            case POT_UPPER_LEFT -> -pitch;
            case POT_UPPER_RIGHT -> pitch;
            case POT_LEFT_OUTER -> -(near + pitch);
            case POT_RIGHT_OUTER -> near + pitch;
            default -> near * side(slot);
        };
    }

    static float offsetY(int slot, float diameter, float labelHalfHeight, float gap) {
        float frontRow = diameter * .12f;
        return switch (slot) {
            case POT_UPPER_CENTER, POT_UPPER_LEFT, POT_UPPER_RIGHT -> labelHalfHeight + gap + diameter * .5f;
            case POT_LEFT_REAR, POT_RIGHT_REAR -> frontRow + diameter * 1.15f;
            default -> frontRow;
        };
    }
}
