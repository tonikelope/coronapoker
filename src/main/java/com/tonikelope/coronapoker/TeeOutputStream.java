/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that duplicates every write to two underlying streams, like the
 * UNIX {@code tee} command, while filtering out non-breaking-space (0xA0)
 * sequences and replacing them with a regular space.
 */
public class TeeOutputStream extends OutputStream {

    private final OutputStream out1;
    private final OutputStream out2;

    // True while we're holding back a 0xC2 byte, waiting to see whether it's
    // the first half of a UTF-8 <0xA0> (non-breaking space) sequence.
    private boolean pendingC2 = false;

    public TeeOutputStream(OutputStream out1, OutputStream out2) {
        this.out1 = out1;
        this.out2 = out2;
    }

    @Override
    public void write(int b) throws IOException {
        int unsignedByte = b & 0xFF;

        // UTF-8 encodes <0xA0> as two bytes: 0xC2 followed by 0xA0. On
        // seeing 0xC2, hold it back until the next byte confirms it.
        if (unsignedByte == 0xC2) {
            pendingC2 = true;
            return;
        }

        if (pendingC2) {
            pendingC2 = false;
            if (unsignedByte == 0xA0) {
                // Confirmed 0xC2 0xA0 sequence: write a regular space instead.
                out1.write(0x20);
                out2.write(0x20);
                return;
            } else {
                // False alarm: the 0xC2 belonged to a different character
                // (e.g. an accented letter). Flush the buffered byte and continue.
                out1.write(0xC2);
                out2.write(0xC2);
            }
        }

        // Handle a console that emits the raw single-byte ANSI form (0xA0) instead.
        if (unsignedByte == 0xA0) {
            out1.write(0x20);
            out2.write(0x20);
            return;
        }

        // Any other byte passes through the tee unchanged.
        out1.write(b);
        out2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // Delegate byte-by-byte to write(int) so the filter still works
        // correctly if a <0xA0> sequence is split across two buffers.
        for (int i = off; i < off + len; i++) {
            this.write(b[i]);
        }

        this.flush();
    }

    @Override
    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    @Override
    public void close() throws IOException {
        try (out2) {
            out1.close();
        }
    }
}
