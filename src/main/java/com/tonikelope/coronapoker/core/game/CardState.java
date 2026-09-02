/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/** Mutable, renderer-neutral state of one physical card slot. */
public final class CardState {

    public record Snapshot(CardCode code, boolean initialized, boolean faceUp,
            boolean disabled, boolean visible, boolean secureHidden,
            boolean showdownHighlighted) { }

    private volatile CardCode code;
    private volatile boolean initialized;
    private volatile boolean faceUp;
    private volatile boolean disabled;
    private volatile boolean visible;
    private volatile boolean secureHidden;
    private volatile boolean showdownHighlighted;

    public synchronized void initialize(CardCode next, boolean nextFaceUp) {
        code = Objects.requireNonNull(next, "code");
        initialized = true;
        faceUp = nextFaceUp;
        disabled = false;
    }

    public synchronized void initializeUnknown(boolean nextVisible) {
        code = null;
        initialized = true;
        faceUp = false;
        disabled = false;
        visible = nextVisible;
    }

    public synchronized void updateCode(CardCode next) {
        code = Objects.requireNonNull(next, "code");
    }

    public synchronized void reset(boolean nextVisible, boolean nextFaceUp) {
        code = null;
        initialized = false;
        faceUp = nextFaceUp;
        disabled = false;
        visible = nextVisible;
        showdownHighlighted = false;
    }

    public CardCode code() { return code; }
    public boolean initialized() { return initialized; }
    public boolean faceUp() { return faceUp; }
    public boolean disabled() { return disabled; }
    public boolean visible() { return visible; }
    public boolean secureHidden() { return secureHidden; }
    public boolean showdownHighlighted() { return showdownHighlighted; }

    public void setFaceUp(boolean value) { faceUp = value; }
    public void setDisabled(boolean value) { disabled = value; }
    public void setVisible(boolean value) { visible = value; }
    public void setSecureHidden(boolean value) { secureHidden = value; }
    public void setShowdownHighlighted(boolean value) { showdownHighlighted = value; }

    public Snapshot snapshot() {
        return new Snapshot(code, initialized, faceUp, disabled, visible,
                secureHidden, showdownHighlighted);
    }
}
