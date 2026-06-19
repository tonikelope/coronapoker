// Generates the crypto ARCHITECTURE block diagram (.drawio) for CoronaPoker.
import { writeFileSync } from 'fs';

const PANEL_X = 40, PANEL_W = 1840;
const CHIP_W = 180, CHIP_H = 66, CHIP_PITCH = 196;
const CHIP_RX = 30, CHIP_RY = 46;
const BAND_H = 120;
const DY = 36; // top offset so title/subtitle clear the first band

const esc = s => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// header (dark, title bar) / body (light band fill) / chip (light chip fill) / stroke
const C = {
  orch:  { h:'#7E57A6', b:'#EDE3FA', chip:'#F4ECFC', s:'#7E57A6' },
  net:   { h:'#5B82BE', b:'#E6EFFB', chip:'#F0F5FD', s:'#5B82BE' },
  cons:  { h:'#D79B00', b:'#FFEFD6', chip:'#FFF6E8', s:'#D79B00' },
  id:    { h:'#C0504D', b:'#FBE2E1', chip:'#FCEDEC', s:'#C0504D' },
  codec: { h:'#0E9C9C', b:'#DAF2F3', chip:'#E9F8F8', s:'#0E9C9C' },
  deal:  { h:'#5E9E5E', b:'#E5F3E3', chip:'#EFF8EE', s:'#5E9E5E' },
  proof: { h:'#C9A23A', b:'#FFF6D9', chip:'#FFFAE8', s:'#C9A23A' },
  prim:  { h:'#5C5C5C', b:'#EFEFEF', chip:'#F7F7F7', s:'#5C5C5C' },
};

// The first 6 bands form the real verifiable-deal dependency stack (each uses the one below).
// The last 2 bands are cross-cutting infrastructure (independent of the math stack).
const STACK_LEN = 6;
const bands = [
  { y:40,  c:C.orch, title:'ORCHESTRATION  &amp;  LOBBY',
    chips:[
      ['Crupier','Dealer · orchestrates the ENTIRE crypto pipeline live'],
      ['WaitingRoomFrame','Lobby · handshake + signed JOIN + TOFU'],
    ]},
  { y:220, c:C.cons, title:'CONSENSUS  /  SETTLEMENT',
    chips:[
      ['HandStateChain','H_t ratchet (dom. «HAND\\0») · H_final = consensus'],
      ['CanonicalActionRecord','Canonical action, 92 B (cents, NFC)'],
      ['SettlementRecord','Canonical settlement table → H_final'],
      ['ShuffleVerificationQueue','Dual-lock verification FIFO (snapshot)'],
    ]},
  { y:400, c:C.codec, title:'WIRE CODECS  (crypto payloads)',
    chips:[
      ['ProofCodec','Byte codec for the ShuffleArgument tree'],
      ['DualLockWire','byte[] wrapper for dual-lock verification'],
      ['UnlockChainWire','REQ/RESP wire for the unlock chains'],
    ]},
  { y:580, c:C.deal, title:'VERIFIABLE SHUFFLE  &amp;  DEALING',
    chips:[
      ['RistrettoSRA','Commutative SRA over Ristretto255 (lock=·k, unlock=k⁻¹)'],
      ['DeterministicShuffle','Fisher-Yates AES-256-CTR (permutation witness)'],
      ['ShuffleCascade','Genesis anchor + Bayer-Groth chain'],
      ['ShuffleArgument','Bayer-Groth ZK: B[i] = k·A[π(i)]'],
      ['RotationProof','Batch-DLEQ: in-place rotation (re-key)'],
      ['DualLockCascade','Verifies the genesis → MEGAPACKET chain'],
      ['VerifiableUnlock','Unlock + DLEQ proof of the committed key'],
      ['DealChain','Per-card DLEQ chain (anchor = commitment)'],
      ['DeckTransform','out[i] = k·deck[perm[i]] + helpers'],
    ]},
  { y:760, c:C.proof, title:'PROOF BUILDING BLOCKS  (ZK)',
    chips:[
      ['Transcript','Fiat-Shamir SHA-512 (absorbs public values)'],
      ['Dleq','Chaum-Pedersen: equality of dlogs (c‖s, 64 B)'],
      ['PedersenVectorCommit','C = r·H + Σ aᵢ·Gᵢ (perfectly hiding)'],
      ['MultiplicationProof','Σ-protocol: c = a·b mod L'],
      ['ProductArgument','Grand product: Π aᵢ = b'],
      ['PermutationArgument','d′ is a permutation of d (Neff)'],
      ['WeightedSumArgument','Q = Σ fᵢ·Bᵢ (multi-exp)'],
    ]},
  { y:940, c:C.prim, title:'GROUP  /  FIELD  (edwards25519)',
    chips:[
      ['Ristretto255','Prime-order group (RFC 9496) · hash-to-group'],
      ['EdwardsPoint','edwards25519 point (extended coords)'],
      ['Fe25519','GF(2²⁵⁵−19) field'],
    ]},
  // ---- cross-cutting infrastructure (independent of the math stack) ----
  { y:1180, c:C.id, title:'IDENTITY  ·  Ed25519   —   cross-cutting infrastructure',
    chips:[
      ['IdentityManager','Ed25519 per nick · signs action/JOIN/receipt/showdown'],
      ['TOFUResolver','TOFU pinning (SQLite) · NEW / MATCH / CHANGED'],
      ['IdenticonDialog','OOB visual fingerprint — identity / anti-MITM channel · host-only peer mosaic'],
    ]},
  { y:1360, c:C.net, title:'TRANSPORT  ·  SECURE CHANNEL   —   cross-cutting infrastructure',
    chips:[
      ['NetServer','Host socket · carries the encrypted wire'],
      ['NetClient','Client socket · carries the encrypted wire'],
      ['WireFrame','Frame codec · text «*» / binary 0x00 (DoS-bounded)'],
      ['BinaryWire','Binary layout (only after HMAC is verified)'],
      ['Helpers · Channel','AES/CBC + HMAC-SHA256 (encrypt-then-MAC); key ⟵ ECDH+SHA-512'],
    ]},
];

let cells = [];
let n = 2;
const id = () => 'c' + (n++);

// Title + subtitle
cells.push(`<mxCell id="title" value="${esc('CoronaPoker — Cryptography subsystem architecture')}" style="text;html=1;fontSize=24;fontStyle=1;fontColor=#1a1a2e;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="6" width="1300" height="30" as="geometry"/></mxCell>`);
cells.push(`<mxCell id="subtitle" value="${esc('Top → bottom: the REAL verifiable-deal dependency stack — each layer is built upon the one below, down to edwards25519 arithmetic. Transport & Identity sit apart as cross-cutting infrastructure used across every layer.')}" style="text;html=1;fontSize=13;fontStyle=2;fontColor=#5a5a6a;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="40" width="1500" height="20" as="geometry"/></mxCell>`);

// Bands + chips
bands.forEach((b) => {
  b.cid = id();
  cells.push(`<mxCell id="${b.cid}" value="${esc(b.title)}" style="swimlane;rounded=1;arcSize=4;startSize=30;html=1;fillColor=${b.c.h};swimlaneFillColor=${b.c.b};strokeColor=${b.c.s};strokeWidth=1.5;fontColor=#ffffff;fontStyle=1;fontSize=14;align=left;verticalAlign=middle;spacingLeft=18;" vertex="1" parent="1"><mxGeometry x="${PANEL_X}" y="${b.y + DY}" width="${PANEL_W}" height="${BAND_H}" as="geometry"/></mxCell>`);
  // Center each band's chips as a group (uniform size + gap), so bands with fewer chips stay visually centered.
  const k = b.chips.length;
  const groupW = k * CHIP_W + (k - 1) * (CHIP_PITCH - CHIP_W);
  const startRel = Math.round((PANEL_W - groupW) / 2);
  b.chips.forEach((c, i) => {
    const label = `<b>${c[0]}</b><br><span style="font-size:9px;color:#3a3a3a">${c[1]}</span>`;
    cells.push(`<mxCell id="${id()}" value="${esc(label)}" style="rounded=1;arcSize=16;whiteSpace=wrap;html=1;fillColor=${b.c.chip};strokeColor=${b.c.s};strokeWidth=1.5;fontColor=#1f1f1f;fontSize=11;verticalAlign=middle;align=center;spacingLeft=4;spacingRight=4;" vertex="1" parent="${b.cid}"><mxGeometry x="${startRel + i*CHIP_PITCH}" y="${CHIP_RY}" width="${CHIP_W}" height="${CHIP_H}" as="geometry"/></mxCell>`);
  });
});

// REAL dependency arrows down the verifiable-deal stack (each uses the one below). Centered, curved turns.
for (let i = 0; i < STACK_LEN - 1; i++) {
  cells.push(`<mxCell id="${id()}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0.5;entryY=0;entryDx=0;entryDy=0;strokeColor=#555555;strokeWidth=2.5;endArrow=block;endFill=1;" edge="1" parent="1" source="${bands[i].cid}" target="${bands[i+1].cid}"><mxGeometry relative="1" as="geometry"/></mxCell>`);
}

// Divider between the math stack and the cross-cutting infrastructure
const dividerY = bands[STACK_LEN-1].y + DY + BAND_H + 8; // just below Primitives band
cells.push(`<mxCell id="divider" value="${esc('╌╌╌   CROSS-CUTTING INFRASTRUCTURE — consumed by every layer, independent of the verifiable-deal dependency chain   ╌╌╌')}" style="text;html=1;fontSize=12;fontStyle=2;fontColor=#666666;align=center;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="${dividerY}" width="1840" height="34" as="geometry"/></mxCell>`);

// Cross-cutting infra usage (dashed, right gutter): Orchestration consumes channel + identity
const orchMidY = bands[0].y + DY + BAND_H/2;
const idMidY  = bands[6].y + DY + BAND_H/2;
const netMidY = bands[7].y + DY + BAND_H/2;
cells.push(`<mxCell id="${id()}" value="${esc('Crupier / WaitingRoomFrame\nuse Ed25519 identity')}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;dashed=1;dashPattern=10 6;exitX=1;exitY=0.5;entryX=1;entryY=0.5;strokeColor=#C0504D;strokeWidth=2;endArrow=block;endFill=1;fontColor=#9c403d;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;" edge="1" parent="1" source="${bands[0].cid}" target="${bands[6].cid}"><mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="1912" y="${orchMidY}"/><mxPoint x="1912" y="${idMidY}"/></Array></mxGeometry></mxCell>`);
cells.push(`<mxCell id="${id()}" value="${esc('… and the secure channel\nfor every wire payload')}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;dashed=1;dashPattern=10 6;exitX=1;exitY=0.5;entryX=1;entryY=0.5;strokeColor=#5B82BE;strokeWidth=2;endArrow=block;endFill=1;fontColor=#3f5e94;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;" edge="1" parent="1" source="${bands[0].cid}" target="${bands[7].cid}"><mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="1948" y="${orchMidY}"/><mxPoint x="1948" y="${netMidY}"/></Array></mxGeometry></mxCell>`);

// Legend
const legend =
  '<b>Dependency map</b>  (▼ solid = uses / built upon · ┄ dashed = cross-cutting infrastructure)<br>' +
  '<b>Verifiable-deal stack:</b>  Orchestration → Consensus → Wire Codecs → Shuffle &amp; Dealing → Proof blocks → Group/Field primitives.<br>' +
  '&nbsp;&nbsp;• ShuffleCascade → ShuffleArgument → { PermutationArgument → ProductArgument → MultiplicationProof , WeightedSumArgument } → PedersenVectorCommit → Ristretto255<br>' +
  '&nbsp;&nbsp;• DualLockCascade → { ShuffleCascade , RotationProof }   ·   DealChain → VerifiableUnlock → Dleq → Transcript   ·   ShuffleVerificationQueue → DualLockWire → DualLockCascade<br>' +
  '&nbsp;&nbsp;• HandStateChain → { CanonicalActionRecord , SettlementRecord , IdentityManager }   ·   ProofCodec → ShuffleArgument   ·   UnlockChainWire → DealChain<br>' +
  '&nbsp;&nbsp;• All arithmetic descends to:  Ristretto255 → EdwardsPoint → Fe25519<br>' +
  '<b>Cross-cutting infrastructure:</b>  the secure channel (AES-CBC + HMAC-SHA256) carries EVERY wire payload; Ed25519 Identity signs actions/JOIN/receipts/showdown. Both are consumed by Orchestration &amp; Consensus but are independent of the math stack.<br>' +
  '<b>Notes:</b>  the <i>genesis</i> (52 Ristretto255 points) never travels — each peer recomputes it as the trust anchor.  The legacy <i>CryptoSRA</i> engine (Montgomery x-only) was retired → today <b>RistrettoSRA</b> + <b>DeterministicShuffle</b>.';
const legendY = bands[7].y + DY + BAND_H + 22;
cells.push(`<mxCell id="legend" value="${esc(legend)}" style="rounded=1;arcSize=3;whiteSpace=wrap;html=1;fillColor=#FBFBFD;strokeColor=#9aa0b0;strokeWidth=1.5;fontColor=#2a2a35;fontSize=11.5;align=left;verticalAlign=top;spacingLeft=16;spacingTop=12;spacingRight=12;" vertex="1" parent="1"><mxGeometry x="40" y="${legendY}" width="1840" height="200" as="geometry"/></mxCell>`);

const PAGE_W = 2000, PAGE_H = legendY + 200 + 30;
const xml =
`<mxfile host="app.diagrams.net" type="device">
  <diagram id="crypto-arch" name="Crypto architecture">
    <mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${PAGE_W}" pageHeight="${PAGE_H}" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        ${cells.join('\n        ')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>`;

const out = process.argv[2] || 'docs/diagrams/crypto-architecture.drawio';
writeFileSync(out, xml, 'utf8');
console.log('wrote', out, '(' + cells.length + ' cells, pageH=' + PAGE_H + ')');
