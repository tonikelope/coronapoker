// Generates the per-hand crypto protocol SEQUENCE diagram (.drawio) for CoronaPoker.
// Topology: HOST (dealer + player, server) + 2 human clients. Every step is drawn explicitly.
import { writeFileSync } from 'fs';

const esc = s => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// Lifelines
const ACT = {
  H:  { x:300,  name:'HOST', sub:'Dealer + Player · server', col:'#2E3A48', fill:'#E8ECF1' },
  C1: { x:780,  name:'CLIENT 1', sub:'human', col:'#5B82BE', fill:'#E6EFFB' },
  C2: { x:1260, name:'CLIENT 2', sub:'human', col:'#5B82BE', fill:'#E6EFFB' },
};
const HEAD_Y = 56, HEAD_H = 58, LIFELINE_TOP = HEAD_Y + HEAD_H;
const PAGE_W = 1700;
const BCAST_X = 1520; // broadcast arrow target (right of C2)
const NOTE_W = 380;

// Phase palette
const P = {
  p0:{ b:'#F4ECFC', s:'#7E57A6' },
  p1:{ b:'#E5F3E3', s:'#5E9E5E' },
  p2:{ b:'#DAF2F3', s:'#0E9C9C' },
  bet:{ b:'#FFEFD6', s:'#D79B00' },
  com:{ b:'#E6EFFB', s:'#5B82BE' },
  p5:{ b:'#FBE2E1', s:'#C0504D' },
  p6:{ b:'#EDE3FA', s:'#7E57A6' },
  xx:{ b:'#F0F0F0', s:'#777777' },
};

// b=bold constant, op=description below (smaller). msg=solid send / ret=dashed response / bc=broadcast.
const m = (from,to,b,op)   => ({t:'msg', from,to,b,op});
const r = (from,to,b,op)   => ({t:'ret', from,to,b,op});
const bc = (b,op)          => ({t:'bcast', b,op});
const note = (at,txt)      => ({t:'note', at,txt});

// One full betting orbit, fully explicit: each player acts once and the host relays every action.
const OPTS = '( FOLD · CHECK · CALL · BET · RAISE · ALL-IN )';
const bettingOrbit = () => [
  m('C1','H','ACTION', OPTS + ' · C1 acts, signs «ACTION\\0»'),
  m('H','C2','ACTION (relay)','host forwards C1 signed action to C2'),
  m('C2','H','ACTION', OPTS + ' · C2 acts, signs «ACTION\\0»'),
  m('H','C1','ACTION (relay)','host forwards C2 signed action to C1'),
  bc('ACTION', OPTS + ' · HOST acts, signs «ACTION\\0»'),
];

const phases = [
  { title:'PHASE 0 · Handshake, identity &amp; genesis anchor  (once per connection, before the hand)', c:P.p0, items:[
    m('C1','H','TCP CONNECT + ECDH','C1 sends its ephemeral Curve25519 pubkey'),
    r('H','C1','ECDH pubkey + encrypted INTRO','host ephemeral Curve25519 pubkey (raw); then, on the now-encrypted channel, the host INTRO: nick ‖ avatar ‖ session_id ‖ host Ed25519 pubkey ‖ self-sig «JOIN\\0»(session_id‖nick‖pubkey)'),
    note('H','Both sides run ECDH → deriveChannelSecret: HMAC-SHA512(shared ‖ password) → AES-256 channel key + HMAC-256 key. C1 then captures session_id and pins the host identity FROM THE ENCRYPTED INTRO — verifyJoin(host self-sig) + TOFU (NEW / MATCH / CHANGED). The raw ECDH bytes carry no identity.'),
    m('C1','H','JOIN','C1 pubkey + Ed25519 self-sig «JOIN\\0» ‖ session_id ‖ nick ‖ pubkey'),
    note('H','verifyJoin(C1) + TOFU pin (NEW / MATCH / CHANGED).'),
    m('C2','H','TCP CONNECT + ECDH','C2 sends its ephemeral Curve25519 pubkey'),
    r('H','C2','ECDH pubkey + encrypted INTRO','host ephemeral Curve25519 pubkey (raw); then host INTRO: nick ‖ avatar ‖ session_id ‖ host Ed25519 pubkey ‖ self-sig'),
    note('H','deriveChannelSecret(C2) → C2 AES-256 + HMAC-256 keys. C2 captures session_id + pins the host identity from the encrypted INTRO (verifyJoin + TOFU).'),
    m('C2','H','JOIN','C2 pubkey + Ed25519 self-sig «JOIN\\0» ‖ session_id ‖ nick ‖ pubkey'),
    note('H','verifyJoin(C2) + TOFU pin.'),
    note('all','GENESIS: HOST, C1 and C2 each derive the 52 Ristretto255 points locally — it NEVER travels. Public anchor decks[0].'),
    m('C1','H','HAND_READY','C1: new-hand barrier'),
    m('C2','H','HAND_READY','C2: new-hand barrier'),
    bc('START_SRA_CASCADE','releases the cascade · HOST mints HAND_ID (random 16 B, delivered in MEGAPACKET)'),
  ]},

  { title:'PHASE 1 · Shuffle — commutative SRA lock cascade  (ring order HOST → C1 → C2)', c:P.p1, items:[
    note('H','HOST applies its own k_pocket over the genesis + Fisher-Yates AES-256-CTR shuffle.\nEvery member holds two ephemeral scalars: k_pocket and k_community.'),
    m('H','C1','DECK_CASCADE_REQ','deck after HOST'),
    note('C1','validates 52 points · generates k_pocket / k_community · lock + shuffle\n· commitments K_pocket=k·B, K_community=k·B'),
    r('C1','H','DECK_CASCADE_RESP','nick ‖ deck′ ‖ K_pocket(C1) ‖ K_community(C1)   — fast, NO proof'),
    r('C1','H','DECK_CASCADE_PROOF','hash(deck′) ‖ Bayer-Groth step proof — ASYNC, sent after the RESP · does NOT block the deal'),
    m('H','C2','DECK_CASCADE_REQ','deck after C1'),
    note('C2','validates · generates k_pocket / k_community · lock + shuffle\n· K commitments'),
    r('C2','H','DECK_CASCADE_RESP','nick ‖ deck″ ‖ K_pocket(C2) ‖ K_community(C2)   — fast, NO proof'),
    r('C2','H','DECK_CASCADE_PROOF','hash(deck″) ‖ Bayer-Groth step proof — ASYNC · does NOT block the deal'),
    note('all','opt. B1 — the step proof is DECOUPLED from the RESP: each peer answers the cascade FAST (deck + commitments) and sends its Bayer-Groth proof separately (DECK_CASCADE_PROOF). The host collects them OFF the deal path (collectAsyncCascadeProofs — matched by hash(deckOut), 45 s window), so the ~0.1–9 s prove never stalls dealing. They feed the DUALLOCK_BUNDLE, assembled + broadcast during betting.'),
    note('H','HOST rotates its own community slots locally: strip k_pocket, re-lock under k_community + RotationProof.'),
    m('H','C1','DECK_ROTATION_REQ','community slots only'),
    note('C1','strip k_pocket + re-lock under k_community (combined scalar s = u·k_comm)\n+ RotationProof (batch-DLEQ of an in-place re-key, no reordering)'),
    r('C1','H','DECK_ROTATION_RESP','rotated deck ‖ RotationProof(C1)'),
    m('H','C2','DECK_ROTATION_REQ','community slots only'),
    note('C2','strip k_pocket + re-lock under k_community + RotationProof'),
    r('C2','H','DECK_ROTATION_RESP','rotated deck ‖ RotationProof(C2)'),
    bc('MEGAPACKET','final deck (pocket intact + rotated community) ‖ HAND_ID ‖ ring order ‖ K commitments'),
    note('all','H_0 = SHA-256( «HAND\\0» ‖ HAND_ID ‖ N ‖ Σ sorted[id‖K_pocket‖K_community] ‖ SHA-256(deck) )  — seeds the state chain'),
  ]},

  { title:'PHASE 2 · Dealing hole cards  (chained DLEQ, verifiable)', c:P.p2, items:[
    note('H','HOST extends one DealChain per slot (anchor = MEGAPACKET point) and strips its own lock locally, each with a per-card DLEQ proof.'),
    m('H','C1','REQ_SRA_UNLOCK_CHAIN','phase = POCKET · strip C1 k_pocket from the OTHER slots'),
    note('C1','verifies each chain anchors to its MEGAPACKET · applies k_pocket⁻¹\n+ attaches a DLEQ proof (Chaum-Pedersen, dom. «SRA_DLEQ\\0»).\nGuard: REFUSES its own slot (anti self-strip) → otherwise LOCKDOWN.'),
    r('C1','H','RESP_SRA_UNLOCK_CHAIN','extended chain + DLEQ'),
    m('H','C2','REQ_SRA_UNLOCK_CHAIN','phase = POCKET · strip C2 k_pocket from the OTHER slots'),
    note('C2','anchor check · applies k_pocket⁻¹ + DLEQ · refuses its own slot'),
    r('C2','H','RESP_SRA_UNLOCK_CHAIN','extended chain + DLEQ'),
    note('H','verifies chains vs each K_pocket · takes the tail = «single-locked» residual per player.'),
    bc('POCKET_CARDS','single-locked residual per player · each recipient removes its own k_pocket and resolves against genesis'),
    note('H','guardarFosilSRA: persists the state for crash recovery.'),
  ]},

  { title:'PHASE 3a · Preflop betting  (Ed25519-signed actions, ratcheted into H_t)', c:P.bet, items:[
    bc('DUALLOCK_BUNDLE','full genesis→MEGAPACKET proof (Bayer-Groth + batch-DLEQ) — assembled from the async step proofs, broadcast DURING betting, off the deal path'),
    note('all','Once the host has collected the async DECK_CASCADE_PROOF from every peer it assembles the honest-shuffle bundle and broadcasts it here. C1 and C2 enqueue it in ShuffleVerificationQueue and verify it in the BACKGROUND against their own recomputed genesis + pocket/community boundary — zero impact on play. A missing/failed proof only sets receipt flags (bit1 deck-pending / bit1+bit2 no-proof), never blocks the hand.'),
    ...bettingOrbit(),
    note('all','Each ACTION is a 92 B canonical record (cents, NFC); the actor signs it «ACTION\\0», every peer verifies the sig and binds type/amount/player/hand to the played action (bad sig or mismatch ⇒ synthetic FOLD + flag), then absorbs H_{t+1} = SHA-256( record ‖ sig ). Acting order follows the button &amp; blinds.'),
  ]},

  { title:'PHASE 4a · Flop — community reveal  (verifiable, signed announce)', c:P.com, items:[
    note('H','HOST strips its own k_community on the flop slots locally (DLEQ).'),
    m('H','C1','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (flop offset)'),
    note('C1','strips its k_community over the copies of the others · DLEQ\n+ GATE-6: refuses if the strip would reveal a genesis card too early.'),
    r('C1','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ'),
    m('H','C2','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (flop offset)'),
    r('C2','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ + GATE-6'),
    m('H','C1','FLOP_PIECE','C1 piece (only its k_community remains)'),
    m('H','C2','FLOP_PIECE','C2 piece (only its k_community remains)'),
    bc('COMM_REVEAL','signed ACTION_COMMUNITY record (3 cards packed in AMOUNT_CENTS)'),
    note('all','Each peer cross-checks the announce against its own piece indices (anti board-fork) and absorbs it into H_t.'),
  ]},

  { title:'PHASE 3b · Flop betting', c:P.bet, items:[ ...bettingOrbit() ]},

  { title:'PHASE 4b · Turn — community reveal', c:P.com, items:[
    note('H','HOST strips its own k_community on the turn slot locally (DLEQ).'),
    m('H','C1','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (turn offset)'),
    r('C1','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ + GATE-6'),
    m('H','C2','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (turn offset)'),
    r('C2','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ + GATE-6'),
    m('H','C1','TURN_PIECE','C1 piece'),
    m('H','C2','TURN_PIECE','C2 piece'),
    bc('COMM_REVEAL','signed ACTION_COMMUNITY record (1 card)'),
    note('all','Cross-checked against each piece (anti board-fork) and absorbed into H_t.'),
  ]},

  { title:'PHASE 3c · Turn betting', c:P.bet, items:[ ...bettingOrbit() ]},

  { title:'PHASE 4c · River — community reveal', c:P.com, items:[
    note('H','HOST strips its own k_community on the river slot locally (DLEQ).'),
    m('H','C1','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (river offset)'),
    r('C1','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ + GATE-6'),
    m('H','C2','REQ_SRA_UNLOCK_CHAIN','phase = COMMUNITY (river offset)'),
    r('C2','H','RESP_SRA_UNLOCK_CHAIN','chain + DLEQ + GATE-6'),
    m('H','C1','RIVER_PIECE','C1 piece'),
    m('H','C2','RIVER_PIECE','C2 piece'),
    bc('COMM_REVEAL','signed ACTION_COMMUNITY record (1 card)'),
    note('all','Cross-checked against each piece (anti board-fork) and absorbed into H_t.'),
  ]},

  { title:'PHASE 3d · River betting', c:P.bet, items:[ ...bettingOrbit() ]},

  { title:'PHASE 5 · Showdown  (k_pocket reveal, signed)', c:P.p5, items:[
    note('H','HOST contributes its own k_pocket directly (it is a player — no request to itself).'),
    m('H','C1','REQ_SHOWDOWN_KEY','to surviving human C1'),
    r('C1','H','RESP_SHOWDOWN_KEY','k_pocket(C1) + Ed25519 sig «SHOWDOWN\\0» ‖ HAND_ID ‖ nick ‖ k_pocket'),
    m('H','C2','REQ_SHOWDOWN_KEY','to surviving human C2'),
    r('C2','H','RESP_SHOWDOWN_KEY','k_pocket(C2) + Ed25519 sig «SHOWDOWN\\0» ‖ HAND_ID ‖ nick ‖ k_pocket'),
    note('H','verifies each sig + applies the key to the stored single-locked residual + resolves against genesis (forfeit on mismatch). Then it publishes every surviving hand at once.'),
    bc('POTCARDS','ONE atomic message — per surviving player: nick ‖ plaintext cards ‖ sraKey (k_pocket) ‖ Ed25519 sig'),
    note('all','Delivered as ONE atomic POTCARDS message (no early peeking); the single bundle each client gets packs every showing hand (HOST + C1 + C2). To verify, each peer SRA-decrypts the stored single-locked residual of each revealed player with the sraKey that player just published and checks it matches the plaintext (proof nobody cheated).'),
    note('all','The hole cards also travel as PLAINTEXT, so SPECTATORS — who hold no SRA keys and cannot decrypt — can still watch the showdown.'),
  ]},

  { title:'PHASE 6 · Settlement + receipt consensus  (certifies the hand)', c:P.p6, items:[
    bc('HANDVERIFY (trigger)','pre-payout barrier · a late MISDEAL here aborts cleanly'),
    note('all','HOST, C1 and C2 each independently compute their SettlementRecord (pot/payout in cents + odd-chip remainder) and absorb it:\nH_final = SHA-256( «SETTLE\\0» ‖ H_t ‖ settlement table ).'),
    m('C1','H','HANDVERIFY # receipt','C1 receipt 113 B: HAND_ID(16) ‖ H_final(32) ‖ flags(1) ‖ sig(64) Ed25519 «RECEIPT\\0»'),
    m('C2','H','HANDVERIFY # receipt','C2 receipt 113 B (same layout)'),
    note('H','HOST builds and signs its own receipt too, then relays each one so every peer ends up holding all three.'),
    m('H','C2','HANDVERIFY # receipt (relay)','C1 receipt → C2'),
    m('H','C1','HANDVERIFY # receipt (relay)','C2 receipt → C1'),
    m('H','C1','HANDVERIFY # receipt','HOST receipt → C1'),
    m('H','C2','HANDVERIFY # receipt','HOST receipt → C2'),
    note('all','Each peer now holds all three receipts and runs the consensus verdict (priority):  DIVERGENT > MISSING > INVALID_SIG_SEEN > DECK_NO_PROOF > DECK_UNVERIFIED > clean.'),
  ]},

  { title:'CROSS-CUTTING · Anti-replay / lockdown / misdeal', c:P.xx, items:[
    note('all','• Single-use rotation: rotation_served_this_cascade (2nd rotation refused + warn).   • Per-command anti-replay/tamper: encrypt-then-MAC with a per-command IV (bad HMAC ⇒ drop).'),
    note('all','• SECURITY_LOCKDOWN (hard): refuses every later crypto request (self-strip / GATE-6 / unanchored chain).   • warnSuspiciousHost (soft): missing/failed proof, replay, out-of-order unlock.   • MISDEAL # reason ⇒ host refunds (cancelarManoYDevolverApuestas).'),
  ]},
];

// ---- layout ----
let cells = [];
let nn = 2;
const id = () => 's' + (nn++);

let y = LIFELINE_TOP + 26;
function actX(a){ return ACT[a].x; }

const bandRects = [];
for (const ph of phases) {
  const top = y;
  y += 30; // band header room
  for (const it of ph.items) {
    if (it.t === 'msg' || it.t === 'ret') {
      const x1 = actX(it.from), x2 = actX(it.to);
      const lineY = y + 30;
      const dashed = it.t === 'ret' ? 'dashed=1;dashPattern=8 5;' : '';
      const label = `<b>${it.b}</b><br><span style="font-size:9px;color:#333">${it.op}</span>`;
      cells.push(`<mxCell id="${id()}" value="${esc(label)}" style="endArrow=block;endFill=1;html=1;rounded=0;${dashed}strokeColor=#34343a;strokeWidth=1.8;fontSize=10;verticalAlign=bottom;labelBackgroundColor=#FFFFFF;spacingBottom=4;" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="${x1}" y="${lineY}" as="sourcePoint"/><mxPoint x="${x2}" y="${lineY}" as="targetPoint"/></mxGeometry></mxCell>`);
      y += 56;
    } else if (it.t === 'bcast') {
      // A host "broadcast" is drawn explicitly: one separate message to each client.
      for (const rc of ['C1','C2']) {
        const x1 = actX('H'), x2 = actX(rc), lineY = y + 28;
        const label = `<b>${it.b}</b>  <span style="font-size:9px;color:#7E57A6">▶ to ${rc}</span><br><span style="font-size:9px;color:#333">${it.op}</span>`;
        cells.push(`<mxCell id="${id()}" value="${esc(label)}" style="endArrow=block;endFill=1;html=1;rounded=0;strokeColor=#7E57A6;strokeWidth=2.4;fontSize=10;verticalAlign=bottom;labelBackgroundColor=#FFFFFF;spacingBottom=4;" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="${x1}" y="${lineY}" as="sourcePoint"/><mxPoint x="${x2}" y="${lineY}" as="targetPoint"/></mxGeometry></mxCell>`);
        y += 54;
      }
    } else if (it.t === 'note') {
      let nx, nw = NOTE_W;
      if (it.at === 'all') { nw = 640; nx = (PAGE_W - nw)/2; }
      else { nx = actX(it.at) + 16; }
      // Altura consciente del WRAP: el texto de la nota se ajusta al ancho de la
      // caja, así que una línea larga ocupa varias filas visuales. Estimamos las
      // filas por línea explícita a partir de un ancho medio de glifo (conservador
      // ~7.2 px a 10 pt) para que la caja crezca y el texto NUNCA se salga.
      const charsPerLine = Math.max(12, Math.floor((nw - 24) / 7.2));
      let rows = 0;
      for (const ln of it.txt.split('\n')) {
        rows += Math.max(1, Math.ceil(ln.length / charsPerLine));
      }
      const nh = 16 + rows*15;
      cells.push(`<mxCell id="${id()}" value="${esc(it.txt)}" style="shape=note;whiteSpace=wrap;html=1;fillColor=#FFF8D6;strokeColor=#D9C45A;strokeWidth=1.2;fontColor=#3a3320;fontSize=10;align=left;verticalAlign=middle;spacingLeft=8;spacingRight=6;size=12;" vertex="1" parent="1"><mxGeometry x="${nx}" y="${y+4}" width="${nw}" height="${nh}" as="geometry"/></mxCell>`);
      y += nh + 14;
    }
  }
  y += 12;
  bandRects.push({ top: top, bottom: y, c: ph.c, title: ph.title });
  y += 18;
}

const LIFELINE_BOT = y + 10;

// emit bands FIRST (background), then lifelines, then headers, then messages (already in `cells`)
let bg = [];
for (const br of bandRects) {
  bg.push(`<mxCell id="${id()}" value="${esc(br.title)}" style="rounded=1;arcSize=2;whiteSpace=wrap;html=1;fillColor=${br.c.b};strokeColor=${br.c.s};strokeWidth=1.5;opacity=55;fontColor=#222222;fontStyle=1;fontSize=12;align=left;verticalAlign=top;spacingLeft=14;spacingTop=6;" vertex="1" parent="1"><mxGeometry x="20" y="${br.top}" width="${PAGE_W-40}" height="${br.bottom-br.top}" as="geometry"/></mxCell>`);
}
// lifelines
for (const k of Object.keys(ACT)) {
  const a = ACT[k];
  bg.push(`<mxCell id="${id()}" style="endArrow=none;html=1;strokeColor=${a.col};strokeWidth=2;dashed=1;dashPattern=4 4;" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="${a.x}" y="${LIFELINE_TOP}" as="sourcePoint"/><mxPoint x="${a.x}" y="${LIFELINE_BOT}" as="targetPoint"/></mxGeometry></mxCell>`);
}
// headers (top) + foot boxes
for (const k of Object.keys(ACT)) {
  const a = ACT[k];
  const lbl = `<b>${a.name}</b><br><span style="font-size:9px">${a.sub}</span>`;
  bg.push(`<mxCell id="${id()}" value="${esc(lbl)}" style="rounded=1;arcSize=20;whiteSpace=wrap;html=1;fillColor=${a.fill};strokeColor=${a.col};strokeWidth=2;fontColor=#1f1f1f;fontSize=12;" vertex="1" parent="1"><mxGeometry x="${a.x-95}" y="${HEAD_Y}" width="190" height="${HEAD_H}" as="geometry"/></mxCell>`);
  bg.push(`<mxCell id="${id()}" value="${esc(lbl)}" style="rounded=1;arcSize=20;whiteSpace=wrap;html=1;fillColor=${a.fill};strokeColor=${a.col};strokeWidth=2;fontColor=#1f1f1f;fontSize=12;" vertex="1" parent="1"><mxGeometry x="${a.x-95}" y="${LIFELINE_BOT}" width="190" height="${HEAD_H}" as="geometry"/></mxCell>`);
}

// Title
const titleCells = [
  `<mxCell id="t1" value="${esc('CoronaPoker — Per-hand cryptographic protocol (verifiable SRA)')}" style="text;html=1;fontSize=22;fontStyle=1;fontColor=#1a1a2e;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="20" y="6" width="1400" height="28" as="geometry"/></mxCell>`,
  `<mxCell id="t2" value="${esc('One COMPLETE hand — all four streets (preflop · flop · turn · river) through to showdown — with every step shown explicitly. 3-player game: HOST (dealer + player, server) + 2 human clients. Time flows downward. ── solid = send · ┄ dashed = response / async · purple = host push (one explicit arrow per client).')}" style="text;html=1;fontSize=12;fontStyle=2;fontColor=#5a5a6a;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="20" y="32" width="1640" height="20" as="geometry"/></mxCell>`,
];

const PAGE_H = LIFELINE_BOT + HEAD_H + 30;
const xml =
`<mxfile host="app.diagrams.net" type="device">
  <diagram id="crypto-seq" name="Crypto sequence (1 hand)">
    <mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${PAGE_W}" pageHeight="${PAGE_H}" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        ${bg.join('\n        ')}
        ${titleCells.join('\n        ')}
        ${cells.join('\n        ')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>`;

const out = process.argv[2] || 'docs/diagrams/crypto-hand-sequence.drawio';
writeFileSync(out, xml, 'utf8');
console.log('wrote', out, '(' + (cells.length + bg.length) + ' cells, pageH=' + PAGE_H + ')');
