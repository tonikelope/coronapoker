package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.network.WireFrameCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Compatibility facade over the shared UI-neutral wire codec. */
public final class WireFrame {
    public static final int BINARY_SENTINEL = WireFrameCodec.BINARY_SENTINEL;
    private WireFrame() { }

    public enum Kind { TEXT, BINARY }

    public static final class Result {
        private final Kind kind;
        private final String text;
        private final byte[] binary;
        private Result(Kind kind, String text, byte[] binary) {
            this.kind = kind;
            this.text = text;
            this.binary = binary;
        }
        public Kind kind() { return kind; }
        public boolean isText() { return kind == Kind.TEXT; }
        public boolean isBinary() { return kind == Kind.BINARY; }
        public String text() { return text; }
        public byte[] binary() { return binary == null ? null : binary.clone(); }
    }

    public static void writeBinary(OutputStream output, byte[] body) throws IOException {
        WireFrameCodec.writeBinary(output, body);
    }

    public static Result read(InputStream input, int cap) throws IOException {
        WireFrameCodec.Frame frame = WireFrameCodec.read(input, cap);
        if (frame == null) return null;
        return frame.isText()
                ? new Result(Kind.TEXT, frame.text(), null)
                : new Result(Kind.BINARY, null, frame.binary());
    }
}
