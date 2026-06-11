/*
 * Copyright (C) 2026 tonikelope
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Plaintext layout of a binary {@link WireFrame} body, before channel encryption.
 *
 * <pre>
 *   payload = type(1) | uint16 nicklen (big-endian) | nick(UTF-8) | rest
 * </pre>
 *
 * The body that travels the wire is {@code encryptBytes(payload)} wrapped in a
 * binary frame, so this layout is only ever seen after HMAC verification — but it
 * is still parsed defensively (bounded nicklen) because the sender is a remote peer.
 *
 * The leading {@code type} byte ('V' voice, future 'A' avatar) lets one binary
 * frame format carry several blob kinds; the carried {@code nick} is the claimed
 * sender (the host trusts its own relays; a server receiving a note re-anchors to
 * the connection's authenticated nick instead).
 */
public final class BinaryWire {

    /** Voice note: {@code rest} is the µ-law WAV audio. */
    public static final byte TYPE_VOICE = 'V';

    private BinaryWire() {
    }

    /** Decoded binary payload: a type tag, the claimed nick, and the blob bytes. */
    public static final class Decoded {

        public final byte type;
        public final String nick;
        public final byte[] payload;

        Decoded(byte type, String nick, byte[] payload) {
            this.type = type;
            this.nick = nick;
            this.payload = payload;
        }
    }

    public static byte[] encode(byte type, String nick, byte[] rest) {
        if (nick == null || rest == null) {
            throw new IllegalArgumentException("nick and rest must not be null");
        }
        byte[] nickBytes = nick.getBytes(StandardCharsets.UTF_8);
        if (nickBytes.length > 0xFFFF) {
            throw new IllegalArgumentException("nick too long for uint16 length: " + nickBytes.length);
        }
        byte[] out = new byte[3 + nickBytes.length + rest.length];
        out[0] = type;
        out[1] = (byte) (nickBytes.length >>> 8);
        out[2] = (byte) nickBytes.length;
        System.arraycopy(nickBytes, 0, out, 3, nickBytes.length);
        System.arraycopy(rest, 0, out, 3 + nickBytes.length, rest.length);
        return out;
    }

    public static byte[] encodeVoice(String nick, byte[] audio) {
        return encode(TYPE_VOICE, nick, audio);
    }

    public static Decoded decode(byte[] data) {
        if (data == null || data.length < 3) {
            throw new IllegalArgumentException("binary payload too short");
        }
        byte type = data[0];
        int nicklen = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
        if (3 + nicklen > data.length) {
            throw new IllegalArgumentException("binary payload nicklen out of bounds: " + nicklen);
        }
        String nick = new String(data, 3, nicklen, StandardCharsets.UTF_8);
        byte[] payload = Arrays.copyOfRange(data, 3 + nicklen, data.length);
        return new Decoded(type, nick, payload);
    }
}
