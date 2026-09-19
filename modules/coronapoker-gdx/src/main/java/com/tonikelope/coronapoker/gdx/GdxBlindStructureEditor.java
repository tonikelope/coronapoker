/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.BlindStructureCatalog;
import com.tonikelope.coronapoker.core.BlindStructureCatalog.BlindLevel;
import com.tonikelope.coronapoker.core.BlindStructureCatalog.Entry;
import com.tonikelope.coronapoker.core.BlindStructureRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Transactional, renderer-independent state for the native GDX editor. */
final class GdxBlindStructureEditor {

    private final ArrayList<Entry> entries = new ArrayList<>();
    private int selectedStructure = -1;
    private int selectedLevel;
    private boolean dirty;

    void begin(Properties properties, String preferredName) {
        entries.clear();
        entries.addAll(BlindStructureCatalog.read(properties));
        selectedStructure = find(preferredName);
        if (selectedStructure < 0 && !entries.isEmpty()) selectedStructure = 0;
        selectedLevel = 0;
        dirty = false;
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    Entry selected() {
        return selectedStructure >= 0 && selectedStructure < entries.size()
                ? entries.get(selectedStructure) : null;
    }

    int selectedStructureIndex() {
        return selectedStructure;
    }

    int selectedLevelIndex() {
        Entry selected = selected();
        if (selected == null) return -1;
        selectedLevel = Math.max(0,
                Math.min(selectedLevel, selected.levels().size() - 1));
        return selectedLevel;
    }

    BlindLevel selectedLevel() {
        Entry selected = selected();
        int index = selectedLevelIndex();
        return selected == null || index < 0 ? null
                : selected.levels().get(index);
    }

    boolean dirty() {
        return dirty;
    }

    void selectStructure(int direction) {
        if (entries.isEmpty()) {
            selectedStructure = -1;
            selectedLevel = 0;
            return;
        }
        selectedStructure = adjacent(selectedStructure, entries.size(),
                direction);
        selectedLevel = 0;
    }

    void selectLevel(int direction) {
        Entry selected = selected();
        if (selected == null) return;
        selectedLevel = adjacent(selectedLevel, selected.levels().size(),
                direction);
    }

    boolean create(String name) {
        if (!canUseName(name, -1)
                || entries.size() >= BlindStructureCatalog.MAX_STRUCTURES) {
            return false;
        }
        ArrayList<BlindLevel> levels = new ArrayList<>();
        for (double[] pair : BlindStructureRules.defaultLevels()) {
            levels.add(new BlindLevel(pair[0], pair[1]));
        }
        entries.add(new Entry(name, levels));
        selectedStructure = entries.size() - 1;
        selectedLevel = 0;
        dirty = true;
        return true;
    }

    boolean duplicate(String name) {
        Entry selected = selected();
        if (selected == null || !canUseName(name, -1)
                || entries.size() >= BlindStructureCatalog.MAX_STRUCTURES) {
            return false;
        }
        entries.add(selectedStructure + 1,
                new Entry(name, selected.levels()));
        selectedStructure++;
        selectedLevel = 0;
        dirty = true;
        return true;
    }

    boolean rename(String name) {
        Entry selected = selected();
        if (selected == null || !canUseName(name, selectedStructure)) {
            return false;
        }
        String normalized = name.trim();
        if (selected.name().equals(normalized)) return true;
        entries.set(selectedStructure,
                new Entry(normalized, selected.levels()));
        dirty = true;
        return true;
    }

    boolean deleteSelected() {
        if (selected() == null) return false;
        entries.remove(selectedStructure);
        if (entries.isEmpty()) {
            selectedStructure = -1;
        } else {
            selectedStructure = Math.min(selectedStructure,
                    entries.size() - 1);
        }
        selectedLevel = 0;
        dirty = true;
        return true;
    }

    boolean addLevel() {
        Entry selected = selected();
        if (selected == null
                || selected.levels().size() >= BlindStructureRules.MAX_LEVELS) {
            return false;
        }
        ArrayList<BlindLevel> levels = new ArrayList<>(selected.levels());
        BlindLevel previous = levels.isEmpty() ? null
                : levels.get(levels.size() - 1);
        BlindLevel added = previous == null
                ? new BlindLevel(25d, 50d)
                : new BlindLevel(roundBlind(previous.smallBlind() * 2d),
                        roundBlind(previous.bigBlind() * 2d));
        levels.add(added);
        if (!valid(levels)) return false;
        replaceLevels(levels);
        selectedLevel = levels.size() - 1;
        return true;
    }

    boolean removeSelectedLevel() {
        Entry selected = selected();
        if (selected == null || selected.levels().size() <= 1) return false;
        ArrayList<BlindLevel> levels = new ArrayList<>(selected.levels());
        levels.remove(selectedLevelIndex());
        replaceLevels(levels);
        selectedLevel = Math.min(selectedLevel, levels.size() - 1);
        return true;
    }

    boolean adjustSmallBlind(int steps) {
        return adjustLevel(steps, true);
    }

    boolean adjustBigBlind(int steps) {
        return adjustLevel(steps, false);
    }

    void save(Properties properties) {
        BlindStructureCatalog.writeTo(properties, entries);
        dirty = false;
    }

    private boolean adjustLevel(int steps, boolean small) {
        Entry selected = selected();
        int index = selectedLevelIndex();
        if (selected == null || index < 0 || steps == 0) return false;
        ArrayList<BlindLevel> levels = new ArrayList<>(selected.levels());
        BlindLevel current = levels.get(index);
        double delta = BlindStructureRules.BLIND_STEP * steps;
        BlindLevel replacement = small
                ? new BlindLevel(roundBlind(current.smallBlind() + delta),
                        current.bigBlind())
                : new BlindLevel(current.smallBlind(),
                        roundBlind(current.bigBlind() + delta));
        levels.set(index, replacement);
        if (!valid(levels)) return false;
        replaceLevels(levels);
        return true;
    }

    private void replaceLevels(List<BlindLevel> levels) {
        Entry selected = selected();
        entries.set(selectedStructure, new Entry(selected.name(), levels));
        dirty = true;
    }

    private boolean canUseName(String name, int ownIndex) {
        if (!BlindStructureCatalog.isValidName(name)) return false;
        String normalized = name.trim();
        for (int index = 0; index < entries.size(); index++) {
            if (index != ownIndex
                    && entries.get(index).name().equals(normalized)) {
                return false;
            }
        }
        return true;
    }

    private int find(String name) {
        if (name == null) return -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).name().equals(name)) return index;
        }
        return -1;
    }

    private static boolean valid(List<BlindLevel> levels) {
        double[][] raw = new double[levels.size()][2];
        for (int index = 0; index < levels.size(); index++) {
            raw[index][0] = levels.get(index).smallBlind();
            raw[index][1] = levels.get(index).bigBlind();
        }
        return BlindStructureRules.validateLevels(raw) == null;
    }

    private static double roundBlind(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static int adjacent(int current, int size, int direction) {
        if (size <= 0) return -1;
        int normalized = Math.max(0, Math.min(current, size - 1));
        return Math.floorMod(normalized + Integer.signum(direction), size);
    }
}
