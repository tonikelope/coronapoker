/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.List;

/** Composer history with a separate slot for the unsent draft. */
final class GdxSentChatHistory {

    private static final int LIMIT = 200;
    private final List<String> messages = new ArrayList<>();
    private int index;
    private String pendingDraft = "";

    boolean isEmpty() {
        return messages.isEmpty();
    }

    void remember(String message) {
        messages.add(message);
        if (messages.size() > LIMIT) messages.remove(0);
        resetNavigation();
    }

    String recall(String draft, int direction) {
        if (isEmpty()) return draft;
        if (index == messages.size()) pendingDraft = draft;
        index = Math.max(0, Math.min(messages.size(),
                index + Integer.signum(direction)));
        return index == messages.size() ? pendingDraft : messages.get(index);
    }

    void resetNavigation() {
        index = messages.size();
        pendingDraft = "";
    }

    void clear() {
        messages.clear();
        resetNavigation();
    }
}
