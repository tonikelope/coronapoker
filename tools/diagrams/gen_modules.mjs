// Generates the high-level MODULE MAP (.drawio) for CoronaPoker.
import { writeFileSync } from 'fs';

const esc = s => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const PAGE_W = 1800;
const PANEL_X = 40, PANEL_W = 1720;

// band palette: header / body / chip / stroke
const C = {
  entry: { h:'#7E57A6', b:'#EDE3FA', chip:'#F4ECFC', s:'#7E57A6' },
  core:  { h:'#C0504D', b:'#FBE2E1', chip:'#FCEDEC', s:'#C0504D' },
  eng:   { h:'#5E9E5E', b:'#E5F3E3', chip:'#EFF8EE', s:'#5E9E5E' },
  sup:   { h:'#5B82BE', b:'#E6EFFB', chip:'#F0F5FD', s:'#5B82BE' },
  found: { h:'#5C5C5C', b:'#EFEFEF', chip:'#F7F7F7', s:'#5C5C5C' },
};

// Bands: background swimlanes with a title.
const bands = [
  { y:70,  h:132, c:C.entry, title:'ENTRY  &amp;  LIFECYCLE' },
  { y:250, h:140, c:C.core,  title:'RUNTIME CORE' },
  { y:440, h:150, c:C.eng,   title:'ENGINE SUBSYSTEMS' },
  { y:640, h:140, c:C.sup,   title:'SUPPORT SERVICES' },
  { y:830, h:120, c:C.found, title:'FOUNDATION' },
];

// Module boxes (absolute coords for precise edge routing).
const boxes = [
  // Z1 entry
  { id:'init',    c:C.entry, x:430,  y:112, w:220, h:72, name:'Init', sub:'Bootstrap: logging · SQLite · CSPRNG · fonts · MODs · audio warm-up · launcher' },
  { id:'newgame', c:C.entry, x:710,  y:112, w:240, h:72, name:'NewGameDialog', sub:'Nick/avatar/host/blinds/buy-in · commits Ed25519 identity' },
  { id:'lobby',   c:C.entry, x:1010, y:112, w:360, h:72, name:'Lobby &amp; Networking', sub:'WaitingRoomFrame · NetServer/NetClient · Participant · WireFrame/BinaryWire · Reconnect' },
  // Z2 core
  { id:'gameframe', c:C.core, x:490, y:292, w:320, h:80, name:'GameFrame', sub:'In-game hub / service locator: jugadores · comunitarias · log · owns Crupier' },
  { id:'crupier',   c:C.core, x:930, y:292, w:380, h:80, name:'Crupier', sub:'Authoritative hand engine (16k LOC): deal · betting · showdown · payout · run-it-twice · recovery' },
  // Z3 engine subsystems (left = under GameFrame, right = under Crupier)
  { id:'table',  c:C.eng, x:265,  y:482, w:230, h:84, name:'Table rendering', sub:'TablePanel2..10 · CommunityCardsPanel · Card' },
  { id:'player', c:C.eng, x:525,  y:482, w:230, h:84, name:'Player model &amp; seats', sub:'Player · LocalPlayer · RemotePlayer · Participant' },
  { id:'bots',   c:C.eng, x:785,  y:482, w:230, h:84, name:'Bots / AI', sub:'Bot · bot/eval (Alberta) · DealerView / BotPlayerView' },
  { id:'money',  c:C.eng, x:1045, y:482, w:230, h:84, name:'Money / Settlement', sub:'HandPot · PotMath · BlindStructure · BetRules / BuyinRules' },
  { id:'crypto', c:C.eng, x:1305, y:482, w:230, h:84, name:'Crypto / SRA', sub:'Verifiable dealing (Ristretto255 · DLEQ · Bayer-Groth) · Ed25519 · H_t/H_final' },
  // Z4 support
  { id:'chat',  c:C.sup, x:450,  y:682, w:260, h:78, name:'Chat / Voice', sub:'EmojiChatBox · EmojiPanel · ChatImageDialog · FastChat · VoiceMessageManager' },
  { id:'audio', c:C.sup, x:770,  y:682, w:260, h:78, name:'Audio', sub:'Audio · AudioDeviceManager · AudioSettingsDialog · CoronaMP3FilePlayer' },
  { id:'stats', c:C.sup, x:1090, y:682, w:260, h:78, name:'Stats / History', sub:'StatsDialog · GameLogDialog · Hand · SQLStats' },
  // Z5 foundation
  { id:'helpers', c:C.found, x:300, y:872, w:1200, h:70, name:'Helpers  —  shared utilities &amp; persistence', sub:'SQLite · secure channel (AES-CBC + HMAC) · threading/EDT · imaging/fonts · money formatting · OS abstraction  ·  used by EVERY module' },
];

// Edges: {from,to, style, label}. style = 'flow' (solid), 'bidir', 'dash'.
const E = (from,to,kind,label,exitX,exitY,entryX,entryY) => ({from,to,kind,label,exitX,exitY,entryX,entryY});
const edges = [
  // lifecycle (horizontal)
  E('init','newgame','flow','', 1,0.5, 0,0.5),
  E('newgame','lobby','flow','', 1,0.5, 0,0.5),
  // launch + wire routing
  E('lobby','gameframe','flow','launches', 0.2,1, 0.5,0),
  E('lobby','crupier','dash','wire commands', 0.6,1, 0.5,0),
  // core pair
  E('gameframe','crupier','bidir','creates / runs  ·  shared state', 1,0.5, 0,0.5),
  // GameFrame owns view/seats
  E('gameframe','table','flow','', 0.3,1, 0.5,0),
  E('gameframe','player','flow','', 0.7,1, 0.5,0),
  // Crupier drives engine subsystems
  E('crupier','bots','flow','', 0.15,1, 0.5,0),
  E('crupier','money','flow','', 0.5,1, 0.5,0),
  E('crupier','crypto','flow','', 0.85,1, 0.5,0),
];

let cells = [];

// Title
cells.push(`<mxCell id="t1" value="${esc('CoronaPoker — Module map (high-level architecture)')}" style="text;html=1;fontSize=24;fontStyle=1;fontColor=#1a1a2e;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="6" width="1400" height="30" as="geometry"/></mxCell>`);
cells.push(`<mxCell id="t2" value="${esc('How the whole app fits together. The launch flow runs left-to-right at the top, into the runtime core (GameFrame ⇄ Crupier); the engine subsystems hang off the core, support services and the Helpers foundation sit below. The Crypto / SRA box is detailed in the two dedicated diagrams.')}" style="text;html=1;fontSize=13;fontStyle=2;fontColor=#5a5a6a;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="40" y="38" width="1500" height="20" as="geometry"/></mxCell>`);

// Bands (background)
bands.forEach((b, i) => {
  cells.push(`<mxCell id="band${i}" value="${esc(b.title)}" style="swimlane;rounded=1;arcSize=3;startSize=30;html=1;fillColor=${b.c.h};swimlaneFillColor=${b.c.b};strokeColor=${b.c.s};strokeWidth=1.5;fontColor=#ffffff;fontStyle=1;fontSize=14;align=left;verticalAlign=middle;spacingLeft=18;" vertex="1" parent="1"><mxGeometry x="${PANEL_X}" y="${b.y}" width="${PANEL_W}" height="${b.h}" as="geometry"/></mxCell>`);
});

// Boxes (on top of bands)
boxes.forEach((m) => {
  const label = `<b>${m.name}</b><br><span style="font-size:9px;color:#3a3a3a">${m.sub}</span>`;
  cells.push(`<mxCell id="${m.id}" value="${esc(label)}" style="rounded=1;arcSize=14;whiteSpace=wrap;html=1;fillColor=${m.c.chip};strokeColor=${m.c.s};strokeWidth=1.5;fontColor=#1f1f1f;fontSize=11;verticalAlign=middle;align=center;spacingLeft=6;spacingRight=6;" vertex="1" parent="1"><mxGeometry x="${m.x}" y="${m.y}" width="${m.w}" height="${m.h}" as="geometry"/></mxCell>`);
});

// Edges
let ne = 0;
edges.forEach((e) => {
  const id = 'e' + (ne++);
  let style;
  if (e.kind === 'bidir') {
    style = `edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;startArrow=block;startFill=1;endArrow=block;endFill=1;strokeColor=#C0504D;strokeWidth=2.5;fontColor=#9c403d;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;`;
  } else if (e.kind === 'dash') {
    style = `edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;dashed=1;dashPattern=8 6;endArrow=block;endFill=1;strokeColor=#5B82BE;strokeWidth=2;fontColor=#3f5e94;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;`;
  } else {
    style = `edgeStyle=orthogonalEdgeStyle;rounded=1;curved=1;html=1;endArrow=block;endFill=1;strokeColor=#555555;strokeWidth=2.2;fontColor=#444444;fontStyle=2;fontSize=10;labelBackgroundColor=#ffffff;`;
  }
  style += `exitX=${e.exitX};exitY=${e.exitY};exitDx=0;exitDy=0;entryX=${e.entryX};entryY=${e.entryY};entryDx=0;entryDy=0;`;
  cells.push(`<mxCell id="${id}" value="${esc(e.label || '')}" style="${style}" edge="1" parent="1" source="${e.from}" target="${e.to}"><mxGeometry relative="1" as="geometry"/></mxCell>`);
});

// Legend
const legend =
  '<b>Key relationships</b>  (solid = uses / drives · ┄ dashed = wire commands · ⇄ red = the core pair)<br>' +
  '• Launch: <b>Init</b> → NewGameDialog → Lobby → <b>GameFrame</b> (host: WaitingRoomFrame.java:5161 · client: 3317) → creates &amp; runs <b>Crupier</b> (GameFrame.java:2390 / 3169).<br>' +
  '• <b>Crupier</b> ⇄ GameFrame (reads/mutates jugadores·board·log via the singleton) · driven by the network readers (Participant / WaitingRoomFrame → crupier.received_commands).<br>' +
  '• <b>Crupier</b> drives → Crypto/SRA (verifiable dealing + H_t / settlement digest) · Money (HandPot·PotMath·Player.pagar) · Bots (DealerView).  <b>GameFrame</b> owns → Player model · Table rendering.<br>' +
  '• <b>Support</b>: Audio (game SFX, driven by Crupier/Init/players) · Chat/Voice (lobby + in-game, via Networking) · Stats/History (Crupier writes hands; Hand = showdown ranking).<br>' +
  '• <b>Helpers</b> is the universal leaf: the secure channel (AES-CBC + HMAC) carries every wire payload, plus SQLite, EDT/threading, imaging and money formatting — used by every module.';
cells.push(`<mxCell id="legend" value="${esc(legend)}" style="rounded=1;arcSize=3;whiteSpace=wrap;html=1;fillColor=#FBFBFD;strokeColor=#9aa0b0;strokeWidth=1.5;fontColor=#2a2a35;fontSize=11.5;align=left;verticalAlign=top;spacingLeft=16;spacingTop=12;spacingRight=12;" vertex="1" parent="1"><mxGeometry x="40" y="990" width="1720" height="180" as="geometry"/></mxCell>`);

const PAGE_H = 1200;
const xml =
`<mxfile host="app.diagrams.net" type="device">
  <diagram id="crypto-modules" name="Module map">
    <mxGraphModel dx="1400" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${PAGE_W}" pageHeight="${PAGE_H}" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        ${cells.join('\n        ')}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>`;

const out = process.argv[2] || 'docs/diagrams/coronapoker-module-map.drawio';
writeFileSync(out, xml, 'utf8');
console.log('wrote', out, '(' + cells.length + ' cells)');
