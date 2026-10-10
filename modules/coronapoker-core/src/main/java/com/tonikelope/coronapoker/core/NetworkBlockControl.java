/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Host-local administrative boundary for temporary network blocks. */
public interface NetworkBlockControl {

    List<NetworkBlock> activeBlocks();

    CompletionStage<Void> unblock(String address);

    static NetworkBlockControl unavailable() {
        return new NetworkBlockControl() {
            @Override
            public List<NetworkBlock> activeBlocks() {
                return List.of();
            }

            @Override
            public CompletionStage<Void> unblock(String address) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException(
                                "Network block controls are unavailable"));
            }
        };
    }
}
