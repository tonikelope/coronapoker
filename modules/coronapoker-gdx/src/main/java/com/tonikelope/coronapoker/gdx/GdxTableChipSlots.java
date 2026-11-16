/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/** Assigns new piles to the nearest free place before growing existing piles. */
final class GdxTableChipSlots {
    static final int POT_SLOT_COUNT = 9;
    static final int LOCAL_LEFT_SLOT_COUNT = 6;
    static final int LOCAL_SLOT_COUNT = LOCAL_LEFT_SLOT_COUNT * 2;
    private static final int COLOURS = GdxChipDenominations.COLUMN_COUNT;
    private static final int CAPACITY = GdxTableChipRack.CHIPS_PER_PILE;

    private final int[][] slots;
    private final int[] used = new int[COLOURS];
    private final boolean[] occupied;

    GdxTableChipSlots(int slotCount) {
        slots = new int[COLOURS][slotCount];
        occupied = new boolean[slotCount];
    }

    void arrange(int[] counts) {
        for (int column = 0; column < COLOURS; column++) {
            while (used[column] > pilesNeeded(counts[column])) releaseLast(column);
        }
        // Every denomination gets a base before a second pile gets a place.
        for (int column = 0; column < COLOURS; column++) {
            if (counts[column] <= 0 || used[column] > 0) continue;
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
            for (int column = 0; column < COLOURS; column++) {
                if (used[column] != depth || pilesNeeded(counts[column]) <= depth) continue;
                int free = nextFree();
                if (free < 0) return;
                take(column, free);
            }
        }
    }

    void compactLocalLeft() {
        // Bring remaining overflow back as soon as a base on the left frees up.
        int free = nextFree();
        if (free < 0 || free >= LOCAL_LEFT_SLOT_COUNT) return;
        for (int column = 0; column < COLOURS; column++) {
            for (int pile = 0; pile < used[column]; pile++) {
                int slot = slots[column][pile];
                if (slot < LOCAL_LEFT_SLOT_COUNT) continue;
                occupied[slot] = false;
                occupied[free] = true;
                slots[column][pile] = free;
                free = nextFree();
                if (free >= LOCAL_LEFT_SLOT_COUNT) return;
            }
        }
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
        occupied[slots[column][--used[column]]] = false;
    }

    private void take(int column, int slot) {
        slots[column][used[column]++] = slot;
        occupied[slot] = true;
    }

    int piles(int column) {
        return used[column];
    }

    int slot(int column, int pile) {
        return used[column] == 0 ? column : slots[column][pile];
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
            case 0, 3, 7 -> -1;
            case 2, 6, 8 -> 1;
            default -> 0;
        };
    }

    static float offsetX(int slot, float diameter, float labelHalfWidth, float gap) {
        float pitch = diameter * 1.15f;
        float near = labelHalfWidth + gap + diameter * .5f;
        return switch (slot) {
            case 4 -> -pitch;
            case 5 -> pitch;
            case 7 -> -(near + pitch);
            case 8 -> near + pitch;
            default -> near * side(slot);
        };
    }

    static float offsetY(int slot, float diameter, float labelHalfHeight, float gap) {
        float frontRow = diameter * .12f;
        return switch (slot) {
            case 1, 4, 5 -> labelHalfHeight + gap + diameter * .5f;
            case 3, 6 -> frontRow + diameter * 1.15f;
            default -> frontRow;
        };
    }
}
