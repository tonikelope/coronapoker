import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..", "..");
const diagrams = path.join(root, "docs", "diagrams");

const escapeXml = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;");

const geometry = (x, y, width, height) =>
  `<mxGeometry x="${x}" y="${y}" width="${width}" height="${height}" as="geometry"/>`;

const vertex = (id, value, style, x, y, width, height) =>
  `        <mxCell id="${id}" value="${escapeXml(value)}" style="${style}" vertex="1" parent="1">${geometry(x, y, width, height)}</mxCell>`;

const edge = (id, value, source, target, style = "") =>
  `        <mxCell id="${id}" value="${escapeXml(value)}" style="${style}" edge="1" parent="1" source="${source}" target="${target}"><mxGeometry relative="1" as="geometry"/></mxCell>`;

const textStyle = "text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;fontColor=#17233B;";
const laneStyle = (fill, stroke, font) => `swimlane;html=1;rounded=1;startSize=42;horizontal=1;fillColor=${fill};swimlaneFillColor=#FFFFFF;strokeColor=${stroke};strokeWidth=1.5;fontColor=${font};fontSize=18;fontStyle=1;align=center;verticalAlign=middle;`;
const boxStyle = (fill, stroke, font = "#17233B") => `rounded=1;arcSize=14;whiteSpace=wrap;html=1;fillColor=${fill};strokeColor=${stroke};strokeWidth=2;fontColor=${font};fontSize=15;align=center;verticalAlign=middle;spacing=8;`;
const arrowStyle = (color, font, dashed = false) => `edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=1;endSize=13;strokeColor=${color};strokeWidth=2.4;fontColor=${font};fontSize=13;fontStyle=1;labelBackgroundColor=#FFFFFF;${dashed ? "dashed=1;" : ""}`;

function document(pageWidth, pageHeight, diagramId, name, cells) {
  return `<mxfile host="Electron" modified="2026-10-11T00:00:00.000Z" agent="draw.io" version="29.6.6">
  <diagram id="${diagramId}" name="${escapeXml(name)}">
    <mxGraphModel dx="${pageWidth}" dy="${pageHeight}" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${pageWidth}" pageHeight="${pageHeight}" background="#FFFFFF" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
${cells.join("\n")}
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
`;
}

function moduleMap() {
  const cells = [];
  cells.push(vertex("title", "CoronaPoker GDX architecture", `${textStyle}fontSize=30;fontStyle=1;`, 45, 20, 1050, 45));
  cells.push(vertex("subtitle", "Runtime responsibilities, renderer-neutral boundary, Maven modules and verification.", `${textStyle}fontSize=16;fontColor=#52627A;`, 47, 65, 1400, 28));

  cells.push(vertex("releaseLane", "RELEASE ARTIFACT", laneStyle("#294A79", "#294A79", "#FFFFFF"), 20, 110, 1860, 155));
  cells.push(vertex("jar", "<b>CoronaPoker_&lt;version&gt;.jar</b><br><font size=\"2\">Main class: com.tonikelope.coronapoker.gdx.GdxLauncher<br>One shaded cross-platform desktop application with core, GDX and resources</font>", boxStyle("#FFFFFF", "#25A9D6"), 500, 165, 900, 78));

  cells.push(vertex("runtimeLane", "RUNTIME ARCHITECTURE", laneStyle("#70499B", "#70499B", "#FFFFFF"), 20, 300, 1860, 440));
  cells.push(vertex("gdx", "<b>GDX PRESENTATION</b><br><br>GdxApplicationShell<br>GdxFrontendScreen<br>GdxTableRenderer<br>CoronaPokerGdxTable<br><br><font size=\"2\">Window lifecycle, input, GPU rendering and desktop media</font>", boxStyle("#E8F7FC", "#25A9D6"), 80, 405, 470, 230));
  cells.push(vertex("contract", "<b>RENDERER-NEUTRAL TABLE CONTRACT</b><br><br>TableSession owns the handoff<br>TableCommandSink receives player commands<br>TableEventBridge attaches one presentation<br>TablePresentation preserves event order<br>TableRenderer exposes presentation barriers<br><br><font size=\"2\">No libGDX type crosses this boundary</font>", boxStyle("#FFF9E7", "#D79B00"), 715, 385, 470, 270));
  cells.push(vertex("core", "<b>AUTHORITATIVE GAME CORE</b><br><br>NetworkLobbyGateway and LobbySession<br>CoreGameTableFactory and Crupier<br>rules, bots, exact money and side pots<br>transport, cryptography and recovery<br>SQLite persistence, logs and statistics<br><br><font size=\"2\">The core imports no libGDX classes</font>", boxStyle("#FFF0F0", "#C94C4C"), 1350, 405, 470, 230));
  cells.push(edge("commandToContract", "TableCommand", "gdx", "contract", arrowStyle("#70499B", "#4E3670")));
  cells.push(edge("commandToCore", "validated dispatch", "contract", "core", arrowStyle("#70499B", "#4E3670")));
  cells.push(edge("eventsToContract", "snapshots and events", "core", "contract", `${arrowStyle("#C94C4C", "#983733")}exitX=0;exitY=0.74;entryX=1;entryY=0.74;`));
  cells.push(edge("renderToGdx", "ordered render calls", "contract", "gdx", `${arrowStyle("#25A9D6", "#147A99")}exitX=0;exitY=0.74;entryX=1;entryY=0.74;`));

  cells.push(vertex("modulesLane", "MAVEN MODULES AND VERIFICATION", laneStyle("#347C53", "#347C53", "#FFFFFF"), 20, 785, 1860, 611));
  cells.push(vertex("coreModule", "<b>coronapoker-core</b><br><font size=\"2\">Rules, sessions, networking, bots, security,<br>persistence and the neutral table API</font>", boxStyle("#FFFFFF", "#C94C4C"), 95, 900, 430, 105));
  cells.push(vertex("assetsModule", "<b>coronapoker-assets</b><br><font size=\"2\">Cards, sounds, images, fonts and bundled resources<br>No Java source</font>", boxStyle("#FFFFFF", "#347C53"), 95, 1085, 430, 105));
  cells.push(vertex("gdxModule", "<b>coronapoker-gdx</b><br><font size=\"2\">Depends on core and assets<br>Owns the production launcher and desktop frontend<br>Publishes the executable application JAR</font>", boxStyle("#FFFFFF", "#25A9D6"), 715, 985, 470, 125));
  cells.push(vertex("qaModule", "<b>coronapoker-qa</b><br><font size=\"2\">Runs in the normal product reactor<br>Checks boundaries, source ownership and distribution<br>Contains no production runtime code</font>", boxStyle("#FFFFFF", "#347C53"), 1350, 885, 430, 120));
  cells.push(vertex("toolsQa", "<b>tools/qa</b><br><font size=\"2\">Opt-in extended test reactor<br>Uses current core and GDX artifacts<br>Provides protocol, recovery, bot and simulation lanes</font>", boxStyle("#FFFFFF", "#347C53"), 1350, 1085, 430, 120));
  cells.push(edge("coreDep", "depends on", "gdxModule", "coreModule", arrowStyle("#C94C4C", "#983733")));
  cells.push(edge("assetsDep", "packages", "gdxModule", "assetsModule", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("qaDep", "checks product reactor", "qaModule", "gdxModule", arrowStyle("#347C53", "#285F40", true)));
  cells.push(edge("toolsDep", "tests product artifacts", "toolsQa", "gdxModule", arrowStyle("#347C53", "#285F40", true)));
  cells.push(vertex("moduleNote", "The root build runs module tests and coronapoker-qa. tools/qa runs only when explicitly requested.", `${boxStyle("#F4FAF6", "#7AA98B", "#285F40")}fontStyle=2;`, 580, 1240, 740, 42));

  return document(1900, 1406, "module-map", "GDX architecture", cells);
}

function classArchitecture() {
  const cells = [];
  cells.push(vertex("title", "CoronaPoker GDX runtime class and data flow", `${textStyle}fontSize=30;fontStyle=1;`, 45, 18, 1450, 45));
  cells.push(vertex("subtitle", "Composition, session handoff, commands and ordered presentation without crossed ownership.", `${textStyle}fontSize=16;fontColor=#52627A;`, 47, 64, 1700, 28));

  cells.push(vertex("assemblyLane", "APPLICATION ASSEMBLY", laneStyle("#294A79", "#294A79", "#FFFFFF"), 20, 110, 2360, 315));
  cells.push(vertex("launcher", "<b>GdxLauncher</b><br><font size=\"2\">main(args)<br>composition root and production entry point</font>", boxStyle("#E8F7FC", "#25A9D6"), 60, 180, 270, 105));
  cells.push(vertex("bootstrap", "<b>CoronaPokerBootstrap</b><br><font size=\"2\">creates process services</font>", boxStyle("#EEF8F2", "#347C53"), 410, 145, 310, 90));
  cells.push(vertex("application", "<b>CoronaPokerApplication</b><br><font size=\"2\">owns service lifecycle, preferences,<br>database, updates and audio</font>", boxStyle("#EEF8F2", "#347C53"), 800, 145, 340, 90));
  cells.push(vertex("factory", "<b>CoreGameTableFactory</b><br><font size=\"2\">implements GameTableFactory<br>builds the canonical table graph</font>", boxStyle("#EEF8F2", "#347C53"), 410, 285, 310, 90));
  cells.push(vertex("gateway", "<b>NetworkLobbyGateway</b><br><font size=\"2\">implements NewGameSessionGateway<br>opens host or client transport<br>uses GameTableFactory</font>", boxStyle("#EEF8F2", "#347C53"), 800, 280, 340, 100));
  cells.push(vertex("shell", "<b>GdxApplicationShell</b><br><font size=\"2\">coordinates screens and active table scene</font>", boxStyle("#E8F7FC", "#25A9D6"), 1320, 145, 330, 90));
  cells.push(vertex("frontend", "<b>GdxFrontendScreen</b><br><font size=\"2\">menu, setup, lobby and statistics</font>", boxStyle("#E8F7FC", "#25A9D6"), 1740, 145, 330, 90));
  cells.push(vertex("assemblyNote", "<b>Composition root</b><br><font size=\"2\">GdxLauncher creates the application services, table factory, network gateway and GDX shell.<br>The core does not import libGDX.</font>", boxStyle("#F7FAFC", "#90A4BC"), 1320, 285, 750, 90));
  cells.push(edge("launcherBootstrap", "calls", "launcher", "bootstrap", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("bootstrapApplication", "creates", "bootstrap", "application", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("launcherFactory", "creates", "launcher", "factory", `${arrowStyle("#347C53", "#285F40")}exitX=1;exitY=0.75;entryX=0;entryY=0.5;`));
  cells.push(edge("factoryGateway", "injected into", "factory", "gateway", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("shellFrontend", "owns", "shell", "frontend", arrowStyle("#25A9D6", "#147A99")));

  cells.push(vertex("sessionLane", "SESSION OWNERSHIP AND TABLE ATTACHMENT", laneStyle("#347C53", "#347C53", "#FFFFFF"), 20, 455, 2360, 300));
  cells.push(vertex("frontendSession", "<b>GdxFrontendScreen</b><br><font size=\"2\">submits NewGameRequest</font>", boxStyle("#E8F7FC", "#25A9D6"), 60, 535, 280, 90));
  cells.push(vertex("gatewaySession", "<b>NetworkLobbyGateway</b><br><font size=\"2\">validates setup and owns transport</font>", boxStyle("#EEF8F2", "#347C53"), 420, 535, 300, 90));
  cells.push(vertex("lobby", "<b>LobbySession</b><br><font size=\"2\">owns lobby state, commands and transport<br>publishes one TableSession</font>", boxStyle("#EEF8F2", "#347C53"), 800, 525, 310, 110));
  cells.push(vertex("tableSession", "<b>TableSession</b><br><font size=\"2\">owns initial snapshot, commands and event bridge<br>starts the engine only after renderer.open completes</font>", boxStyle("#FFF9E7", "#D79B00"), 1190, 515, 360, 130));
  cells.push(vertex("shellSession", "<b>GdxApplicationShell</b><br><font size=\"2\">awaits the handoff and calls attach(renderer)</font>", boxStyle("#E8F7FC", "#25A9D6"), 1630, 525, 310, 110));
  cells.push(vertex("rendererAttach", "<b>GdxTableRenderer</b><br><font size=\"2\">created for the session<br>posts work to the render thread</font>", boxStyle("#E8F7FC", "#25A9D6"), 2020, 525, 300, 110));
  cells.push(edge("openSession", "open(request)", "frontendSession", "gatewaySession", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("createLobby", "returns", "gatewaySession", "lobby", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("publishSession", "publishes", "lobby", "tableSession", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("awaitSession", "awaits", "shellSession", "tableSession", arrowStyle("#607D9B", "#52627A", true)));
  cells.push(edge("createRenderer", "creates", "shellSession", "rendererAttach", arrowStyle("#25A9D6", "#147A99")));
  cells.push(vertex("sessionNote", "TableSession.attach opens exactly one renderer. The engine starts only after that presentation barrier completes.", `${boxStyle("#F4FAF6", "#7AA98B", "#285F40")}fontStyle=2;`, 650, 675, 1100, 44));

  cells.push(vertex("loopLane", "RENDERER-NEUTRAL TABLE LOOP", laneStyle("#70499B", "#70499B", "#FFFFFF"), 20, 785, 2360, 592));
  cells.push(vertex("gdxTable", "<b>CoronaPokerGdxTable</b><br><font size=\"2\">draws the table and final summary<br>captures player input</font>", boxStyle("#E8F7FC", "#25A9D6"), 70, 885, 300, 110));
  cells.push(vertex("commandSink", "<b>&lt;&lt;interface&gt;&gt;</b><br><b>TableCommandSink</b><br><font size=\"2\">submit(TableCommand)</font>", boxStyle("#FFF9E7", "#D79B00"), 450, 885, 300, 110));
  cells.push(vertex("commandAdapter", "<b>Core command adapter</b><br><font size=\"2\">validates and dispatches each request</font>", boxStyle("#EEF8F2", "#347C53"), 830, 885, 320, 110));
  cells.push(vertex("crupier", "<b>Crupier and controllers</b><br><font size=\"2\">authoritative hand progression,<br>rules and network coordination</font>", boxStyle("#F7F0FC", "#9A63BF"), 1230, 875, 340, 130));
  cells.push(vertex("loopNote", "<b>Authority and barriers</b><br><br><font size=\"2\">Commands are requests. The core validates them and decides the game result.<br><br>TableVisualEvent values describe presentation. CompletionStage values are real visual barriers only where hand progression must wait.<br><br>GDX never becomes authoritative for poker rules.</font>", boxStyle("#F7FAFC", "#90A4BC"), 1710, 875, 600, 350));
  cells.push(vertex("gdxRenderer", "<b>GdxTableRenderer</b><br><font size=\"2\">implements TableRenderer<br>updates the GDX scene on the render thread</font>", boxStyle("#E8F7FC", "#25A9D6"), 70, 1135, 300, 120));
  cells.push(vertex("renderer", "<b>&lt;&lt;interface&gt;&gt;</b><br><b>TableRenderer</b><br><font size=\"2\">open(snapshot)<br>render(event)<br>close()</font>", boxStyle("#FFF9E7", "#D79B00"), 450, 1135, 300, 120));
  cells.push(vertex("presentation", "<b>TablePresentation</b><br><font size=\"2\">assigns monotonic sequence values<br>queues while opening and preserves order</font>", boxStyle("#FFF9E7", "#D79B00"), 830, 1135, 320, 120));
  cells.push(vertex("eventBridge", "<b>TableEventBridge</b><br><font size=\"2\">attaches one presentation<br>publishes TableVisualEvent values</font>", boxStyle("#FFF9E7", "#D79B00"), 1230, 1135, 340, 120));
  cells.push(edge("input1", "submits", "gdxTable", "commandSink", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("input2", "dispatches", "commandSink", "commandAdapter", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("input3", "invokes", "commandAdapter", "crupier", arrowStyle("#347C53", "#285F40")));
  cells.push(edge("output1", "publishes", "crupier", "eventBridge", `${arrowStyle("#70499B", "#4E3670")}exitX=0.5;exitY=1;entryX=0.5;entryY=0;`));
  cells.push(edge("output2", "orders", "eventBridge", "presentation", arrowStyle("#70499B", "#4E3670")));
  cells.push(edge("output3", "renders through", "presentation", "renderer", arrowStyle("#70499B", "#4E3670")));
  cells.push(edge("output4", "implemented by", "renderer", "gdxRenderer", arrowStyle("#25A9D6", "#147A99", true)));
  cells.push(edge("updateScene", "updates scene", "gdxRenderer", "gdxTable", `${arrowStyle("#25A9D6", "#147A99")}exitX=0.5;exitY=0;entryX=0.5;entryY=1;`));
  cells.push(vertex("loopFooter", "The initial TableSnapshot opens the scene. Later state arrives as ordered events. The core remains authoritative throughout.", `${boxStyle("#F7FAFC", "#90A4BC", "#52627A")}fontStyle=2;`, 500, 1295, 1080, 42));

  return document(2400, 1385, "frontend-uml", "GDX runtime class and data flow", cells);
}

fs.writeFileSync(path.join(diagrams, "coronapoker-module-map.drawio"), moduleMap(), "utf8");
fs.writeFileSync(path.join(diagrams, "coronapoker-frontend-uml.drawio"), classArchitecture(), "utf8");
console.log("Updated CoronaPoker Draw.io architecture sources.");
