// Generates the BOT per-turn DECISION FLOW chart (.drawio) for CoronaPoker.
// Output: docs/diagrams/bot-decision-flow.drawio
import { writeFileSync } from 'fs';

const esc = s => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// fill / stroke per node role
const COL = {
  se:   { f:'#E5F3E3', s:'#5E9E5E' }, // start / end (terminator)
  proc: { f:'#E6EFFB', s:'#5B82BE' }, // process
  dec:  { f:'#FFEFD6', s:'#D79B00' }, // decision (diamond)
  pre:  { f:'#EDE3FA', s:'#7E57A6' }, // preflop
  chk:  { f:'#E9F8F8', s:'#0E9C9C' }, // checked-to subroutine
  fac:  { f:'#F4ECFC', s:'#7E57A6' }, // facing-bet subroutine
  mis:  { f:'#FBE2E1', s:'#C0504D' }, // mistake injection
};

// id, x, y, w, h, kind(role), shape, label
const nodes = [
  ['start', 500, 30, 500, 54, 'se', 'term', '<b>Crupier reaches a CPU seat</b><br>Bot.calculateBotDecision(opponents)'],
  ['decPre', 615, 120, 270, 96, 'dec', 'rhombus', '<b>street ==<br>PREFLOP ?</b>'],

  // preflop branch (left column)
  ['preEval', 40, 132, 430, 72, 'pre', 'box', '<b>determinePosition()  ·  evaluateHandTier()</b><br><span style="font-size:9px">tier 1 premium … 5 trash · position EARLY/MIDDLE/LATE/BLINDS</span>'],
  ['preAct', 40, 250, 430, 132, 'pre', 'box', '<b>calculatePreflopAction</b><br><span style="font-size:9px">position × profile × bet level →<br>open · 3-bet/4-bet/5-bet value · call · fold<br>squeeze · 3-bet bluff (blockers)<br>HU button steal &amp; BB defend (difficulty looseness offset)</span>'],

  // postflop spine
  ['pf1', 510, 270, 480, 52, 'proc', 'box', '<b>handStrengthVsN → strength</b><br><span style="font-size:9px">equity vs N random hands · cached per street</span>'],
  ['pf2', 510, 352, 480, 52, 'proc', 'box', '<b>potential() → PPot / NPot</b><br><span style="font-size:9px">2-card look-ahead on the flop · river = 0</span>'],
  ['pf3', 490, 434, 520, 64, 'proc', 'box', '<b>effective strength = strength + (1−s)·PPot − s·NPot</b><br><span style="font-size:9px">− overcard / weak-kicker penalties · floor 0.10</span>'],
  ['pf4', 490, 528, 520, 64, 'proc', 'box', '<b>scare-card detection</b> (Δ &lt; −0.15 on a new street)<br><span style="font-size:9px">on the FLOP: generateStreetPlan → { BET·BET·BET | BET·CHK·BET | CHK·CALL | none }</span>'],
  ['pf5', 490, 622, 520, 70, 'proc', 'box', '<b>winProb adjustments</b><br><span style="font-size:9px">multiway · 3-bet+ pots · scare card · out of position · villain read (nit / maniac / station) · shark edge · EASY noise</span>'],
  ['pf6', 540, 722, 420, 52, 'proc', 'box', '<b>EV(call) · EV(raise) · fold equity</b><br><span style="font-size:9px">implied odds for safe draws · fold equity = 0 vs a station</span>'],
  ['decFace', 605, 812, 290, 104, 'dec', 'rhombus', '<b>facing a bet ?</b><br><span style="font-size:9px">betCount &gt; 0</span>'],

  // two postflop sub-routines
  ['chkBox', 70, 962, 540, 150, 'chk', 'box', '<b>decisionWhenCheckedTo</b><br><span style="font-size:9px">slow-play trap · flop C-bet (range advantage)<br>semi-bluff (street-aware PPot threshold)<br>river: value · thin value · polarized bluff (fold-equity gated)<br>street-plan barrels · EV value bet<br>→ <b>BET</b> or <b>CHECK</b></span>'],
  ['facBox', 870, 962, 560, 150, 'fac', 'box', '<b>decisionWhenFacingBet</b><br><span style="font-size:9px">dynamic check-raise · float · pot-committed shove/call<br>value raise · medium raise (heads-up only) · squeeze<br>implied-odds draw call · MDF bluff-catch (HARD)<br>→ <b>BET</b> · <b>CHECK/CALL</b> · <b>FOLD</b></span>'],

  ['raw', 500, 1160, 500, 56, 'proc', 'box', '<b>raw decision</b>: BET / CHECK-CALL / FOLD'],
  ['decMis', 585, 1256, 330, 110, 'dec', 'rhombus', '<b>recreational mistake ?</b><br><span style="font-size:9px">roll &lt; rate · EASY 45% · MEDIUM 22% · HARD 0%</span>'],
  ['misBox', 70, 1270, 430, 82, 'mis', 'box', '<b>injectRecreationalMistake</b><br><span style="font-size:9px">sticky calldown · hero fold · missed value bet · spewy preflop call</span>'],

  ['end', 490, 1420, 520, 64, 'se', 'term', '<b>final ACTION → dealer</b><br><span style="font-size:9px">executes the cached getBetSize() after BOT_THINK_TIME (1500 ms)</span>'],
];

// from, to, label, exitX,exitY, entryX,entryY  (exit/entry optional, -1 = let drawio route)
const edges = [
  ['start', 'decPre', '', 0.5, 1, 0.5, 0],
  ['decPre', 'preEval', 'preflop', 0, 0.5, 1, 0.5],
  ['decPre', 'pf1', 'postflop', 0.5, 1, 0.5, 0],
  ['preEval', 'preAct', '', 0.5, 1, 0.5, 0],
  ['preAct', 'raw', '', 0.5, 1, 0, 0.5],
  ['pf1', 'pf2', '', 0.5, 1, 0.5, 0],
  ['pf2', 'pf3', '', 0.5, 1, 0.5, 0],
  ['pf3', 'pf4', '', 0.5, 1, 0.5, 0],
  ['pf4', 'pf5', '', 0.5, 1, 0.5, 0],
  ['pf5', 'pf6', '', 0.5, 1, 0.5, 0],
  ['pf6', 'decFace', '', 0.5, 1, 0.5, 0],
  ['decFace', 'chkBox', 'no — checked to us', 0, 0.5, 0.5, 0],
  ['decFace', 'facBox', 'yes — a bet to call', 1, 0.5, 0.5, 0],
  ['chkBox', 'raw', '', 0.5, 1, 0.5, 0],
  ['facBox', 'raw', '', 0.5, 1, 0.5, 0],
  ['raw', 'decMis', '', 0.5, 1, 0.5, 0],
  // 'yes' enters the mistake box on its right side → clean horizontal arrow.
  ['decMis', 'misBox', 'yes', 0, 0.5, 1, 0.5],
  ['decMis', 'end', 'no', 0.5, 1, 0.5, 0],
  // Clean orthogonal L into the end node's left side (waypoint forces the right-angle
  // turn instead of the auto-router's slanted segment).
  ['misBox', 'end', '', 0.5, 1, 0, 0.5, [[285, 1452]]],
];

let cells = [];
cells.push(`<mxCell id="title" value="${esc('CoronaPoker — Bot per-turn decision flow')}" style="text;html=1;fontSize=24;fontStyle=1;fontColor=#1a1a2e;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="-30" width="1200" height="30" as="geometry"/></mxCell>`);

for (const [idn, x, y, w, h, role, shape, label] of nodes) {
  const c = COL[role];
  let style;
  if (shape === 'rhombus') {
    style = `rhombus;whiteSpace=wrap;html=1;fillColor=${c.f};strokeColor=${c.s};strokeWidth=1.5;fontColor=#1f1f1f;fontSize=12;align=center;verticalAlign=middle;`;
  } else if (shape === 'term') {
    style = `rounded=1;arcSize=50;whiteSpace=wrap;html=1;fillColor=${c.f};strokeColor=${c.s};strokeWidth=2;fontColor=#1f1f1f;fontSize=12;align=center;verticalAlign=middle;`;
  } else {
    style = `rounded=1;arcSize=10;whiteSpace=wrap;html=1;fillColor=${c.f};strokeColor=${c.s};strokeWidth=1.5;fontColor=#1f1f1f;fontSize=12;align=center;verticalAlign=middle;spacingLeft=6;spacingRight=6;`;
  }
  cells.push(`<mxCell id="${idn}" value="${esc(label)}" style="${style}" vertex="1" parent="1"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`);
}

let n = 0;
for (const [from, to, label, ex, ey, nx, ny, wp] of edges) {
  const pts = (ex >= 0) ? `exitX=${ex};exitY=${ey};exitDx=0;exitDy=0;entryX=${nx};entryY=${ny};entryDx=0;entryDy=0;` : '';
  let geo = '<mxGeometry relative="1" as="geometry"/>';
  if (wp && wp.length) {
    const points = wp.map(([x, y]) => `<mxPoint x="${x}" y="${y}"/>`).join('');
    geo = `<mxGeometry relative="1" as="geometry"><Array as="points">${points}</Array></mxGeometry>`;
  }
  cells.push(`<mxCell id="e${n++}" value="${esc(label)}" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;${pts}strokeColor=#555555;strokeWidth=2;endArrow=block;endFill=1;fontColor=#333333;fontSize=10;fontStyle=2;labelBackgroundColor=#ffffff;" edge="1" parent="1" source="${from}" target="${to}">${geo}</mxCell>`);
}

const PAGE_W = 1500, PAGE_H = 1540;
const xml =
`<mxfile host="app.diagrams.net" type="device">
  <diagram id="bot-flow" name="Bot decision flow">
    <mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${PAGE_W}" pageHeight="${PAGE_H}" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        ${cells.join('\n        ')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>`;

const out = process.argv[2] || 'docs/diagrams/bot-decision-flow.drawio';
writeFileSync(out, xml, 'utf8');
console.log('wrote', out, '(' + cells.length + ' cells)');
