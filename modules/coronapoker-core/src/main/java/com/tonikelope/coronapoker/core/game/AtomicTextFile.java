/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Crash-resistant replacement of a small text file. */
public final class AtomicTextFile {

    private AtomicTextFile() {
    }

    public static void write(Path target, CharSequence data) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        Path temporary = target.resolveSibling(target.getFileName().toString()
                + ".tmp-" + Long.toHexString(System.nanoTime()));
        try {
            Files.writeString(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
            }
            throw failure;
        }
    }
}
