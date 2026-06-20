// Generates the BOT subsystem ARCHITECTURE block diagram (.drawio) for CoronaPoker.
// Mirrors the visual language of gen_arch.mjs (swimlane bands + rounded chips +
// dependency arrows + legend). Output: docs/diagrams/bot-architecture.drawio
import { writeFileSync } from 'fs';

const PANEL_X = 40, PANEL_W = 1840;
const CHIP_W = 330, CHIP_H = 74, CHIP_PITCH = 360;
const BAND_H = 128, BAND_PITCH = 188;
const CHIP_RY = 42;
const DY = 60; // top offset so title/subtitle clear the first band

const esc = s => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// header (dark, title bar) / body (light band fill) / chip (light chip fill) / stroke
const C = {
  game:    { h:'#5B82BE', b:'#E6EFFB', chip:'#F0F5FD', s:'#5B82BE' },
  engine:  { h:'#7E57A6', b:'#EDE3FA', chip:'#F4ECFC', s:'#7E57A6' },
  model:   { h:'#D79B00', b:'#FFEFD6', chip:'#FFF6E8', s:'#D79B00' },
  eval:    { h:'#0E9C9C', b:'#DAF2F3', chip:'#E9F8F8', s:'#0E9C9C' },
  alberta: { h:'#5E9E5E', b:'#E5F3E3', chip:'#EFF8EE', s:'#5E9E5E' },
};

const bands = [
  { c: C.game, title: 'GAME INTEGRATION  —  live table (Spanish domain)',
    chips: [
      ['Crupier', 'Dealer · drives each bot turn · implements DealerView'],
      ['GameFrame', 'Global DIFFICULTY · BOT_THINK_TIME (1500 ms)'],
      ['DealerView  «interface»', 'Read-only table slice (street, pot, bets, blinds, positions)'],
      ['BotPlayerView  «interface»', 'Read-only player slice (stack, bet, hole cards, active)'],
    ]},
  { c: C.engine, title: 'DECISION ENGINE  —  Bot.java  (one instance per CPU seat)',
    chips: [
      ['Bot', 'calculateBotDecision(opponents) → BET / CHECK-CALL / FOLD'],
      ['Personality', 'Difficulty {EASY,MEDIUM,HARD} · Skill {REC,REG,SHARK} · Profile {NIT,STATION,TAG,LAG}'],
      ['Per-hand state', 'streetPlan · scareCard · tilt · slowPlay · float · cBetInitiative'],
      ['Bet sizing', 'getBetSize() · board-texture fractions · RNG jitter'],
    ]},
  { c: C.model, title: 'OPPONENT MODELLING  —  populated by the dealer every action',
    chips: [
      ['OpponentTracker', 'VPIP · PFR · AF (aggression factor)'],
      ['Reads', 'isStation / isNit / isManiac / looksPassiveStation'],
      ['TRACKER_MEMORY', 'Per-nick stats · persists across hands of the session'],
    ]},
  { c: C.eval, title: 'HAND EVALUATION  —  BotEvaluator abstraction (Card-encoding 0..51)',
    chips: [
      ['BotEvaluator  «interface»', 'HandStrength + DrawPotential + HandRank (umbrella)'],
      ['MemoizedAlbertaEvaluator', 'Production · memoized PPot/NPot (~8× on the flop)'],
      ['AlbertaEvaluatorAdapter', 'Reference oracle (uncached) · the equivalence baseline'],
      ['Potential', 'PPot / NPot pair (positive / negative potential)'],
    ]},
  { c: C.alberta, title: 'ALBERTA ENGINE  —  U. Alberta Poker Research Group (org.alberta.poker)',
    chips: [
      ['HandPotential', 'ppot_raw / ppot · Papp 1998 §5.3 roll-out'],
      ['MemoizedHandPotential', 'Cached 7-card ranks (own + opponent table)'],
      ['HandEvaluator', 'rankHand() · native libeval | Java fallback'],
      ['Deck · Hand · Card', 'Deterministic combinatorial primitives'],
    ]},
];

let cells = [];
let n = 2;
const id = () => 'c' + (n++);

cells.push(`<mxCell id="title" value="${esc('CoronaPoker — Bot subsystem architecture')}" style="text;html=1;fontSize=24;fontStyle=1;fontColor=#1a1a2e;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="6" width="1300" height="30" as="geometry"/></mxCell>`);
cells.push(`<mxCell id="subtitle" value="${esc('Top → bottom: the live game drives the decision engine, which leans on opponent modelling and a swappable hand-evaluation stack down to the Alberta combinatorial core. The «interface» seams (DealerView / BotPlayerView / BotEvaluator) let the offline QA harness fake the whole table without Swing.')}" style="text;html=1;fontSize=13;fontStyle=2;fontColor=#5a5a6a;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="40" width="1760" height="20" as="geometry"/></mxCell>`);

bands.forEach((b, bi) => {
  b.y = 40 + bi * BAND_PITCH;
  b.cid = id();
  cells.push(`<mxCell id="${b.cid}" value="${esc(b.title)}" style="swimlane;rounded=1;arcSize=4;startSize=30;html=1;fillColor=${b.c.h};swimlaneFillColor=${b.c.b};strokeColor=${b.c.s};strokeWidth=1.5;fontColor=#ffffff;fontStyle=1;fontSize=14;align=left;verticalAlign=middle;spacingLeft=18;" vertex="1" parent="1"><mxGeometry x="${PANEL_X}" y="${b.y + DY}" width="${PANEL_W}" height="${BAND_H}" as="geometry"/></mxCell>`);
  const k = b.chips.length;
  const groupW = k * CHIP_W + (k - 1) * (CHIP_PITCH - CHIP_W);
  const startRel = Math.round((PANEL_W - groupW) / 2);
  b.chips.forEach((c, i) => {
    const label = `<b>${c[0]}</b><br><span style="font-size:9px;color:#3a3a3a">${c[1]}</span>`;
    cells.push(`<mxCell id="${id()}" value="${esc(label)}" style="rounded=1;arcSize=16;whiteSpace=wrap;html=1;fillColor=${b.c.chip};strokeColor=${b.c.s};strokeWidth=1.5;fontColor=#1f1f1f;fontSize=11;verticalAlign=middle;align=center;spacingLeft=4;spacingRight=4;" vertex="1" parent="${b.cid}"><mxGeometry x="${startRel + i*CHIP_PITCH}" y="${CHIP_RY}" width="${CHIP_W}" height="${CHIP_H}" as="geometry"/></mxCell>`);
  });
});

// Solid dependency arrows down the stack (each layer uses the one below).
for (let i = 0; i < bands.length - 1; i++) {
  cells.push(`<mxCell id="${id()}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0.5;entryY=0;entryDx=0;entryDy=0;strokeColor=#555555;strokeWidth=2.5;endArrow=block;endFill=1;" edge="1" parent="1" source="${bands[i].cid}" target="${bands[i+1].cid}"><mxGeometry relative="1" as="geometry"/></mxCell>`);
}

// Dashed side arrow: the decision engine consults opponent modelling (read).
const engMidY = bands[1].y + DY + BAND_H / 2;
const modMidY = bands[2].y + DY + BAND_H / 2;
cells.push(`<mxCell id="${id()}" value="${esc('reads villain reads')}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;dashed=1;dashPattern=10 6;exitX=1;exitY=0.5;entryX=1;entryY=0.5;strokeColor=#D79B00;strokeWidth=2;endArrow=block;endFill=1;fontColor=#9c7200;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;" edge="1" parent="1" source="${bands[1].cid}" target="${bands[2].cid}"><mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="1912" y="${engMidY}"/><mxPoint x="1912" y="${modMidY}"/></Array></mxGeometry></mxCell>`);

const legend =
  '<b>How a turn flows</b>  (▼ solid = depends on / built upon · ┄ dashed = consults)<br>' +
  '<b>Crupier</b> reaches a CPU seat → calls <b>Bot.calculateBotDecision(opponents)</b> → the engine queries the <b>BotEvaluator</b> for hand strength and PPot/NPot, blends them into an <i>effective strength</i>, adjusts by <b>OpponentTracker</b> reads, profile and board texture, and returns BET / CHECK-CALL / FOLD plus a cached bet size.<br>' +
  '<b>Effective strength</b> = strength + (1 − strength)·PPot − strength·NPot   (Papp 1998).<br>' +
  '<b>Swappable evaluation:</b> production wires <b>MemoizedAlbertaEvaluator</b>; <b>AlbertaEvaluatorAdapter</b> stays untouched as the numeric reference the equivalence test compares against (identical PPot/NPot, ~8× faster on the flop two-card look-ahead).<br>' +
  '<b>The «interface» seams</b> (DealerView · BotPlayerView · BotEvaluator) are the testability boundary: the offline QA harness (tools/qa) injects fake dealers, players and a deterministic RNG so thousands of hands run head-less, without Swing or sockets.';
const legendY = bands[bands.length - 1].y + DY + BAND_H + 26;
cells.push(`<mxCell id="legend" value="${esc(legend)}" style="rounded=1;arcSize=3;whiteSpace=wrap;html=1;fillColor=#FBFBFD;strokeColor=#9aa0b0;strokeWidth=1.5;fontColor=#2a2a35;fontSize=11.5;align=left;verticalAlign=top;spacingLeft=16;spacingTop=12;spacingRight=12;" vertex="1" parent="1"><mxGeometry x="40" y="${legendY}" width="1840" height="190" as="geometry"/></mxCell>`);

const PAGE_W = 1960, PAGE_H = legendY + 190 + 30;
const xml =
`<mxfile host="app.diagrams.net" type="device">
  <diagram id="bot-arch" name="Bot architecture">
    <mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${PAGE_W}" pageHeight="${PAGE_H}" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        ${cells.join('\n        ')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>`;

const out = process.argv[2] || 'docs/diagrams/bot-architecture.drawio';
writeFileSync(out, xml, 'utf8');
console.log('wrote', out, '(' + cells.length + ' cells, pageH=' + PAGE_H + ')');
