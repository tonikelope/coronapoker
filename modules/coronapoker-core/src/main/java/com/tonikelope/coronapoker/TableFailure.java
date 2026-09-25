/*
 * Copyright (C) 2020 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Fail-closed disposition for an unrecoverable error in one poker table.
 *
 * The process remains alive so the table can release its own resources and
 * return to the launcher. An open hand is deliberately preserved for recovery.
 */
public final class TableFailure {

    private final int handId;
    private final boolean preserveOpenHand;
    private final String diagnosticBundle;

    private TableFailure(int handId, boolean preserveOpenHand, String diagnosticBundle) {
        this.handId = handId;
        this.preserveOpenHand = preserveOpenHand;
        this.diagnosticBundle = diagnosticBundle;
    }

    public static TableFailure capture(Throwable cause, int handId, boolean openHand) {
        Objects.requireNonNull(cause, "cause");

        StringWriter stack = new StringWriter();
        cause.printStackTrace(new PrintWriter(stack));
        String bundle = "TABLE_FAILURE_V1\n"
                + "handId=" + handId + "\n"
                + "openHand=" + openHand + "\n"
                + "cause=" + cause.getClass().getName() + "\n"
                + "message=" + String.valueOf(cause.getMessage()) + "\n"
                + stack;

        return new TableFailure(handId, openHand, bundle);
    }

    public boolean exitJvm() {
        return false;
    }

    public boolean forceRecovery() {
        return true;
    }

    public boolean closeTable() {
        return true;
    }

    public boolean returnToLauncher() {
        return true;
    }

    public boolean preserveOpenHand() {
        return preserveOpenHand;
    }

    public int handId() {
        return handId;
    }

    public String diagnosticBundle() {
        return diagnosticBundle;
    }
}
