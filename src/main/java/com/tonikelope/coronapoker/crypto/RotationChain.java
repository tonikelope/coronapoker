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
 * Verifiable dual-lock ROTATION chain (Phase 4.3 / Option G, Part B). Closes the
 * blinded-oracle attack via {@code DECK_ROTATION_REQ}: each peer turns a community
 * piece from pocket-space to community-space by stripping its pocket-lock and adding
 * its community-lock, proving BOTH sub-steps with DLEQ under its committed keys. The
 * chain's implicit start X0 is the committed pre-rotation deck point {@code H_pre}
 * (held locally by every peer), so it is NOT transmitted and the chain cannot be
 * re-anchored to a blinded value — exactly as {@link DealChain} does for the dealing.
 *
 * Per-point entry: {@code nick:midB64:outB64:pocketProofB64:communityProofB64} joined
 * by ';'. {@code mid} is the residual after the pocket-unlock; {@code out} after the
 * community-lock. Verification (per step from H_pre):
 *   pocket-unlock:    verifyStep(prevOut, mid, K_pocket,    pocketProof)    // prevOut = k_P*mid
 *   community-lock:   verifyStep(out,     mid, K_community, communityProof) // out     = k_C*mid
 * No new crypto: it reuses {@link VerifiableUnlock} and {@link Dleq}.
 */
public final class RotationChain {

    private RotationChain() {
    }

    /** One rotation step for a point: pocket-unlock then community-lock, each proven. */
    public static final class Entry {
        public final String nick;
        public final byte[] mid;             // 32-byte residual after pocket-unlock
        public final byte[] out;             // 32-byte residual after community-lock
        public final byte[] pocketProof;     // 64-byte DLEQ (pocket-unlock)
        public final byte[] communityProof;  // 64-byte DLEQ (community-lock)

        public Entry(String nick, byte[] mid, byte[] out, byte[] pocketProof, byte[] communityProof) {
            this.nick = nick;
            this.mid = mid;
            this.out = out;
            this.pocketProof = pocketProof;
            this.communityProof = communityProof;
        }
    }

    /** Serializes a point's rotation chain (possibly empty) to a wire string. */
    public static String serialize(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(Base64.getEncoder().encodeToString(e.nick.getBytes(StandardCharsets.UTF_8)))
                    .append(':').append(Base64.getEncoder().encodeToString(e.mid))
                    .append(':').append(Base64.getEncoder().encodeToString(e.out))
                    .append(':').append(Base64.getEncoder().encodeToString(e.pocketProof))
                    .append(':').append(Base64.getEncoder().encodeToString(e.communityProof));
        }
        return sb.toString();
    }

    /** Parses a wire string back to a chain, or null on any malformed entry. */
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
            if (f.length != 5) {
                return null;
            }
            try {
                String nick = new String(Base64.getDecoder().decode(f[0]), StandardCharsets.UTF_8);
                byte[] mid = Base64.getDecoder().decode(f[1]);
                byte[] outR = Base64.getDecoder().decode(f[2]);
                byte[] pProof = Base64.getDecoder().decode(f[3]);
                byte[] cProof = Base64.getDecoder().decode(f[4]);
                if (mid.length != 32 || outR.length != 32
                        || pProof.length != Dleq.PROOF_BYTES || cProof.length != Dleq.PROOF_BYTES) {
                    return null;
                }
                out.add(new Entry(nick, mid, outR, pProof, cProof));
            } catch (Exception e) {
                return null;
            }
        }
        return out;
    }

    /**
     * Verifies a point's rotation chain against the committed pre-rotation point H_pre.
     *
     * @param hPrePoint            committed pre-rotation deck point (chain start X0)
     * @param entries              ordered rotation steps
     * @param pocketCommitments    nick -> committed pocket key K_P = k_P*B
     * @param communityCommitments nick -> committed community key K_C = k_C*B
     * @return true iff the chain starts at H_pre and every step's BOTH sub-proofs verify
     */
    public static boolean verify(byte[] hPrePoint, List<Entry> entries,
                                 Map<String, byte[]> pocketCommitments,
                                 Map<String, byte[]> communityCommitments) {
        if (hPrePoint == null || hPrePoint.length != 32 || entries == null) {
            return false;
        }
        byte[] prevOut = hPrePoint;
        for (Entry e : entries) {
            byte[] kPocket = pocketCommitments.get(e.nick);
            byte[] kCommunity = communityCommitments.get(e.nick);
            if (kPocket == null || kCommunity == null) {
                return false; // unknown / uncommitted peer
            }
            // pocket-unlock: prevOut = k_P * mid
            if (!VerifiableUnlock.verifyStep(prevOut, e.mid, kPocket, e.pocketProof)) {
                return false;
            }
            // community-lock: out = k_C * mid
            if (!VerifiableUnlock.verifyStep(e.out, e.mid, kCommunity, e.communityProof)) {
                return false;
            }
            prevOut = e.out;
        }
        return true;
    }

    /** Residual after the last step (community-space), or H_pre when the chain is empty. */
    public static byte[] tail(byte[] hPrePoint, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return hPrePoint;
        }
        return entries.get(entries.size() - 1).out;
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
     * Peer-side rotation step for one point: verify the incoming chain from the committed
     * H_pre, then strip our pocket-lock and add our community-lock, appending both proofs.
     * Refuses (null) unless the chain provably descends from H_pre — closing the oracle.
     *
     * @param hPrePoint      the committed pre-rotation point this chain must start from
     * @param incomingWire   serialized incoming chain ("" if we are first)
     * @param pocketCommitments    nick -> K_P of the peers already in the chain
     * @param communityCommitments nick -> K_C of the peers already in the chain
     * @param myNick         our nick (recorded in the appended entry)
     * @param myPocketLock   our pocket lock scalar k_P (we strip it: apply k_P^-1)
     * @param myCommunityLock our community lock scalar k_C (we add it: apply k_C)
     * @return the extended chain + new residual, or null to reject
     */
    public static Extended extend(byte[] hPrePoint, String incomingWire,
                                  Map<String, byte[]> pocketCommitments,
                                  Map<String, byte[]> communityCommitments,
                                  String myNick, byte[] myPocketLock, byte[] myCommunityLock) {
        List<Entry> chain = parse(incomingWire);
        if (chain == null) {
            return null; // malformed incoming chain
        }
        if (!verify(hPrePoint, chain, pocketCommitments, communityCommitments)) {
            return null; // not anchored to H_pre / bad proof / uncommitted key
        }
        byte[] current = tail(hPrePoint, chain);
        VerifiableUnlock.Step unlockStep = VerifiableUnlock.unlockWithProof(current, myPocketLock);
        if (unlockStep == null) {
            return null; // off-group residual
        }
        VerifiableUnlock.Step lockStep = VerifiableUnlock.lockWithProof(unlockStep.residual, myCommunityLock);
        if (lockStep == null) {
            return null;
        }
        chain.add(new Entry(myNick, unlockStep.residual, lockStep.residual, unlockStep.proof, lockStep.proof));
        return new Extended(serialize(chain), lockStep.residual);
    }
}
