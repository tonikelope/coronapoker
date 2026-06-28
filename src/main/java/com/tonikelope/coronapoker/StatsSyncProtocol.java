package com.tonikelope.coronapoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Wire format of a stats-sync message — the {@code rest} payload of a
 * {@link BinaryWire#TYPE_DB} binary frame (which is itself channel-encrypted).
 *
 * <p>A message is a single leading subtype byte followed by a body:
 * <ul>
 *   <li>{@link #MANIFEST} {@code 'M'} — body is a gzipped list of the sender's
 *       shareable game ugis. Its mere arrival means "I want to receive; here is
 *       what I already have", so the receiver can push back what the sender lacks.</li>
 *   <li>{@link #GAMES} {@code 'G'} — body is a {@link StatsSync#exportGames} blob
 *       (already gzipped) carrying one batch of game subtrees to import.</li>
 * </ul>
 *
 * <p>This class is the pure codec; sending/receiving, encryption and the
 * push/import orchestration live in the waiting-room layer.
 */
public final class StatsSyncProtocol {

    public static final byte MANIFEST = 'M';
    public static final byte GAMES = 'G';

    // A manifest claims at most this many ugis — a sanity bound against a hostile
    // peer declaring a huge count to force a giant allocation on decode.
    private static final int MAX_MANIFEST_UGIS = 5_000_000;

    private StatsSyncProtocol() {
    }

    /** The subtype byte, or 0 for an empty message. */
    public static byte subtype(byte[] message) {
        return (message != null && message.length > 0) ? message[0] : 0;
    }

    public static byte[] manifestMessage(Collection<String> ugis, boolean wantsReceive) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(MANIFEST);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos))) {
            out.writeBoolean(wantsReceive);
            out.writeInt(ugis.size());
            for (String ugi : ugis) {
                out.writeUTF(ugi);
            }
        }
        return baos.toByteArray();
    }

    public static Manifest readManifest(byte[] message) throws IOException {
        if (subtype(message) != MANIFEST) {
            throw new IOException("not a manifest message");
        }
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(message, 1, message.length - 1)))) {
            boolean wantsReceive = in.readBoolean();
            int n = in.readInt();
            if (n < 0 || n > MAX_MANIFEST_UGIS) {
                throw new IOException("manifest ugi count out of bounds: " + n);
            }
            List<String> ugis = new ArrayList<>(Math.min(n, 1024));
            for (int i = 0; i < n; i++) {
                ugis.add(in.readUTF());
            }
            return new Manifest(ugis, wantsReceive);
        }
    }

    /**
     * A decoded manifest: the sender's shareable ugis plus whether the sender
     * wants to receive games back (so the peer only pushes to a willing receiver).
     */
    public static final class Manifest {

        public final List<String> ugis;
        public final boolean wantsReceive;

        public Manifest(List<String> ugis, boolean wantsReceive) {
            this.ugis = ugis;
            this.wantsReceive = wantsReceive;
        }
    }

    /** Wraps a {@link StatsSync#exportGames} blob into a GAMES message. */
    public static byte[] gamesMessage(byte[] exportBlob) {
        byte[] out = new byte[1 + exportBlob.length];
        out[0] = GAMES;
        System.arraycopy(exportBlob, 0, out, 1, exportBlob.length);
        return out;
    }

    /** Extracts the export blob from a GAMES message (to feed {@link StatsSync#importGames}). */
    public static byte[] gamesBlob(byte[] message) throws IOException {
        if (subtype(message) != GAMES) {
            throw new IOException("not a games message");
        }
        byte[] blob = new byte[message.length - 1];
        System.arraycopy(message, 1, blob, 0, blob.length);
        return blob;
    }
}
