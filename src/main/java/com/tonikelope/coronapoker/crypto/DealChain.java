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
package com.tonikelope.coronapoker.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Wire serialization + verification of a per-point de-locking chain for verifiable
 * dealing (Phase 4.2). A chain records, for ONE 32-byte deck point, the ordered
 * sequence of peers that stripped their lock: each entry is (nick, residualAfter,
 * DLEQ proof). The chain's implicit start X0 is the committed MEGAPACKET point at
 * the relevant position (held locally by every peer), so it is NOT transmitted —
 * a peer reconstructs it and the chain cannot be re-anchored to a blinded value.
 *
 * Serialization (one point's chain): entries joined by ';', fields by ':' —
 *   nickB64:residualAfterB64:proofB64
 * Base64 uses none of ':', ';', '#', so the chain is safe inside the existing
 * '#'-split unlock-batch wire.
 *
 * Verification chains {@link VerifiableUnlock#verifyChain} from the committed
 * MEGAPACKET point, resolving each step's committed key K by nick. This is what
 * lets a peer refuse to act as a blinded decryption oracle: the chain must start
 * at the committed point and every link must carry a valid DLEQ under a committed
 * key, neither of which a host can forge for a blinded input.
 */
public final class DealChain {

    private DealChain() {
    }

    /** One de-locking step in a point's chain. */
    public static final class Entry {
        public final String nick;
        public final byte[] residualAfter; // 32-byte encoding after this peer's unlock
        public final byte[] proof;         // 64-byte DLEQ proof

        public Entry(String nick, byte[] residualAfter, byte[] proof) {
            this.nick = nick;
            this.residualAfter = residualAfter;
            this.proof = proof;
        }
    }

    /** Serializes a point's chain (possibly empty) to a wire string. */
    public static String serialize(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(Base64.getEncoder().encodeToString(e.nick.getBytes(StandardCharsets.UTF_8)))
                    .append(':').append(Base64.getEncoder().encodeToString(e.residualAfter))
                    .append(':').append(Base64.getEncoder().encodeToString(e.proof));
        }
        return sb.toString();
    }

    /**
     * Parses a wire string back to a chain. Returns null on any malformed entry
     * (wrong field count or out-of-spec lengths), so callers treat a bad chain as a
     * zero-trust rejection rather than silently dropping links.
     */
    public static List<Entry> parse(String wire) {
        List<Entry> out = new ArrayList<>();
        if (wire == null || wire.isEmpty()) {
            return out;
        }
        for (String token : wire.split(";")) {
            if (token.isEmpty()) {
                continue;
            }
            String[] f = token.split(":");
            if (f.length != 3) {
                return null;
            }
            try {
                String nick = new String(Base64.getDecoder().decode(f[0]), StandardCharsets.UTF_8);
                byte[] residual = Base64.getDecoder().decode(f[1]);
                byte[] proof = Base64.getDecoder().decode(f[2]);
                if (residual.length != 32 || proof.length != Dleq.PROOF_BYTES) {
                    return null;
                }
                out.add(new Entry(nick, residual, proof));
            } catch (Exception e) {
                return null;
            }
        }
        return out;
    }

    /**
     * Verifies a point's chain against the committed MEGAPACKET point.
     *
     * @param megapacketPoint the committed deck point (chain start X0)
     * @param entries         ordered de-locking steps
     * @param commitments     nick -> committed key K = k*B (32-byte encoding)
     * @return true iff the chain starts at megapacketPoint and every step carries a
     *         valid DLEQ under the committed key of the peer that produced it
     */
    public static boolean verify(byte[] megapacketPoint, List<Entry> entries,
                                 Map<String, byte[]> commitments) {
        if (megapacketPoint == null || megapacketPoint.length != 32 || entries == null) {
            return false;
        }
        int n = entries.size();
        byte[][] residuals = new byte[n + 1][];
        byte[][] ks = new byte[n][];
        byte[][] proofs = new byte[n][];
        residuals[0] = megapacketPoint;
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            byte[] k = commitments.get(e.nick);
            if (k == null) {
                return false; // unknown / uncommitted peer
            }
            residuals[i + 1] = e.residualAfter;
            ks[i] = k;
            proofs[i] = e.proof;
        }
        return VerifiableUnlock.verifyChain(megapacketPoint, residuals, ks, proofs);
    }

    /**
     * The residual after the last step (what the next peer or the recipient sees),
     * or the committed point itself when the chain is empty.
     */
    public static byte[] tail(byte[] megapacketPoint, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return megapacketPoint;
        }
        return entries.get(entries.size() - 1).residualAfter;
    }

    /** Result of {@link #extend}: the new chain wire and the residual after our step. */
    public static final class Extended {
        public final String wire;
        public final byte[] residual;

        Extended(String wire, byte[] residual) {
            this.wire = wire;
            this.residual = residual;
        }
    }

    /**
     * Peer-side step for one point: verify the incoming chain from the committed
     * MEGAPACKET point, then strip our lock and append our proven step. This is the
     * gate that closes the blinded oracle — we refuse (return null) unless the chain
     * provably descends from the committed deck point.
     *
     * @param megapacketPoint the committed deck point this chain must start from
     * @param incomingWire    the serialized incoming chain (empty string if we are first)
     * @param commitments     nick -> committed key K (of the peers already in the chain)
     * @param myNick          our nick (recorded in the appended entry)
     * @param myLock          our lock scalar k (we derive k^-1 and prove with K=k*B)
     * @return the extended chain + new residual, or null to reject
     */
    public static Extended extend(byte[] megapacketPoint, String incomingWire,
                                  Map<String, byte[]> commitments, String myNick, byte[] myLock) {
        List<Entry> chain = parse(incomingWire);
        if (chain == null) {
            return null; // malformed incoming chain
        }
        if (!verify(megapacketPoint, chain, commitments)) {
            return null; // not anchored to the committed point / bad proof / uncommitted key
        }
        byte[] currentResidual = tail(megapacketPoint, chain);
        VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(currentResidual, myLock);
        if (step == null) {
            return null; // off-group residual
        }
        chain.add(new Entry(myNick, step.residual, step.proof));
        return new Extended(serialize(chain), step.residual);
    }
}
