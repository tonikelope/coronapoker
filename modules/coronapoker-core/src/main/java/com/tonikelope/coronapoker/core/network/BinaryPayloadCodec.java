package com.tonikelope.coronapoker.core.network;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Plain binary payload layout used inside encrypted binary frames. */
public final class BinaryPayloadCodec {
    public static final byte TYPE_VOICE = 'V';
    public static final byte TYPE_DATABASE = 'D';
    private BinaryPayloadCodec() { }

    public record Payload(byte type, String nickname, byte[] body) {
        public Payload {
            nickname = Objects.requireNonNull(nickname, "nickname");
            body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
        }
        @Override public byte[] body() { return body.clone(); }
    }

    public static byte[] encode(byte type, String nickname, byte[] body) {
        byte[] nick = Objects.requireNonNull(nickname, "nickname").getBytes(StandardCharsets.UTF_8);
        Objects.requireNonNull(body, "body");
        if (nick.length > 0xffff) throw new IllegalArgumentException("nickname too long");
        byte[] result = new byte[3 + nick.length + body.length];
        result[0] = type;
        result[1] = (byte) (nick.length >>> 8);
        result[2] = (byte) nick.length;
        System.arraycopy(nick, 0, result, 3, nick.length);
        System.arraycopy(body, 0, result, 3 + nick.length, body.length);
        return result;
    }

    public static Payload decode(byte[] data) {
        if (data == null || data.length < 3) throw new IllegalArgumentException("binary payload too short");
        int nickLength = ((data[1] & 0xff) << 8) | (data[2] & 0xff);
        if (3 + nickLength > data.length) throw new IllegalArgumentException("binary nickname out of bounds");
        return new Payload(data[0], new String(data, 3, nickLength, StandardCharsets.UTF_8),
                Arrays.copyOfRange(data, 3 + nickLength, data.length));
    }
}
