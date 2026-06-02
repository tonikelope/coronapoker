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

/**
 * Wire (de)serialization for the verifiable unlock batch (Phase 4.2). The whole
 * item list travels as ONE base64 field of the REQ/RESP command, so it never
 * interacts with the surrounding '#'-split; inside, items are '\n'-separated and
 * fields '\t'-separated (DealChain chains use only ':' ';' Base64, never '\t'/'\n').
 *
 * REQ item: peerIdx, offsetBase (first MEGAPACKET point index), and one DealChain
 * wire per point. RESP item: peerIdx and the extended chains. This is pure string
 * plumbing, unit-tested for round-trip and for the full host->peer->host flow, so
 * the only thing left to manual smoke is the socket I/O and orchestration.
 */
public final class UnlockChainWire {

    private UnlockChainWire() {
    }

    /** A REQ item: which recipient slot, where its points live, and the chain per point. */
    public static final class ReqItem {
        public final int peerIdx;
        public final int offsetBase;       // index of the first point in the MEGAPACKET
        public final List<String> chains;  // one DealChain wire per point ("" if empty)

        public ReqItem(int peerIdx, int offsetBase, List<String> chains) {
            this.peerIdx = peerIdx;
            this.offsetBase = offsetBase;
            this.chains = chains;
        }
    }

    /** A RESP item: the recipient slot and its extended chains. */
    public static final class RespItem {
        public final int peerIdx;
        public final List<String> chains;

        public RespItem(int peerIdx, List<String> chains) {
            this.peerIdx = peerIdx;
            this.chains = chains;
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    /** Serializes REQ items to a single base64 wire field. */
    public static String serializeReq(List<ReqItem> items) {
        StringBuilder sb = new StringBuilder();
        for (ReqItem it : items) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(it.peerIdx).append('\t').append(it.offsetBase).append('\t').append(it.chains.size());
            for (String c : it.chains) {
                sb.append('\t').append(b64(c)); // b64 so empty chains survive the split
            }
        }
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Parses REQ items, or null on any malformed input. */
    public static List<ReqItem> parseReq(String field) {
        try {
            String body = new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8);
            List<ReqItem> out = new ArrayList<>();
            if (body.isEmpty()) {
                return out;
            }
            for (String line : body.split("\n", -1)) {
                String[] f = line.split("\t", -1);
                if (f.length < 3) {
                    return null;
                }
                int peerIdx = Integer.parseInt(f[0]);
                int offsetBase = Integer.parseInt(f[1]);
                int numChains = Integer.parseInt(f[2]);
                if (f.length != 3 + numChains) {
                    return null;
                }
                List<String> chains = new ArrayList<>(numChains);
                for (int i = 0; i < numChains; i++) {
                    chains.add(unb64(f[3 + i]));
                }
                out.add(new ReqItem(peerIdx, offsetBase, chains));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Serializes RESP items to a single base64 wire field. */
    public static String serializeResp(List<RespItem> items) {
        StringBuilder sb = new StringBuilder();
        for (RespItem it : items) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(it.peerIdx).append('\t').append(it.chains.size());
            for (String c : it.chains) {
                sb.append('\t').append(b64(c));
            }
        }
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Parses RESP items, or null on any malformed input. */
    public static List<RespItem> parseResp(String field) {
        try {
            String body = new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8);
            List<RespItem> out = new ArrayList<>();
            if (body.isEmpty()) {
                return out;
            }
            for (String line : body.split("\n", -1)) {
                String[] f = line.split("\t", -1);
                if (f.length < 2) {
                    return null;
                }
                int peerIdx = Integer.parseInt(f[0]);
                int numChains = Integer.parseInt(f[1]);
                if (f.length != 2 + numChains) {
                    return null;
                }
                List<String> chains = new ArrayList<>(numChains);
                for (int i = 0; i < numChains; i++) {
                    chains.add(unb64(f[2 + i]));
                }
                out.add(new RespItem(peerIdx, chains));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
