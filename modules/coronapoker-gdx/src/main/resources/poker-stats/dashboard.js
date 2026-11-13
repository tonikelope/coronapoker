/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
'use strict';

const strings = {
  es: {
    stats:'Estadísticas', overview:'Resumen', sessions:'Sesiones', analysis:'Análisis',
    compare:'Comparativas', classics:'Clásicas',
    player:'Jugador', from:'Desde', through:'Hasta', apply:'Aplicar', allTime:'Todo',
    excludeBots:'Excluir bots', excludeBotsHelp:'Excluye a los bots y conserva las manos de los jugadores humanos.',
    noHumans:'No hay jugadores humanos registrados en esta selección.', noVisibleRows:'Sin registros visibles.',
    game:'Partida', allGames:'Todas las partidas', allHands:'Todas las manos', allPlayers:'Todos los jugadores',
    humanPlayers:'Jugadores humanos',
    previousHand:'Mano anterior', nextHand:'Mano siguiente',
    performance:'RENDIMIENTO', bankroll:'Evolución del bankroll', cumulative:'Acumulado',
    perSession:'Por sesión', bankrollNote:'Beneficio acumulado de las mesas del periodo.',
    profile:'PERFIL DE JUEGO', behavior:'Estadísticas clave', sessionResults:'Resultados por sesión',
    prev:'Anterior', next:'Siguiente', heatmaps:'MAPAS DE CALOR', startingHands:'Manos iniciales',
    cardsNote:'Solo manos con cartas registradas. Pasa el cursor para ver la muestra.',
    loss:'Pérdida', profit:'Ganancia', positions:'Rendimiento por posición',
    positionsNote:'Beneficio medio por mano en cada posición registrada.',
    compareMark:'COMPARATIVAS', trends:'Evolución de estadísticas', metric:'Métrica',
    playerCompare:'Jugadores en las mismas sesiones',
    net:'Beneficio neto', hourly:'Ganancia por hora', hands:'Manos jugadas',
    sessionCount:'Sesiones', settled:'Manos liquidadas',
    timed:'de mesa registradas', profitable:'con beneficio', noBalance:'Sin saldo registrado',
    noDuration:'Sin duración registrada', loading:'Consultando el historial local…',
    emptyHistory:'Las estadísticas aparecerán cuando haya manos liquidadas en el historial.',
    emptyFilter:'No hay sesiones para este jugador en el periodo seleccionado.',
    error:'No se pudieron cargar los datos. Vuelve a pulsar Aplicar.',
    invalid:'Introduce fechas válidas (AAAA-MM-DD) y comprueba el orden del intervalo.',
    noSamples:'Sin muestra', noChart:'Sin datos para este periodo', noCard:'Sin cartas registradas',
    noTrend:'Se necesitan dos sesiones con muestra suficiente para esta métrica.',
    vpip:'Inversión voluntaria preflop', pfr:'Subidas preflop', threeBet:'Resubidas ante una subida',
    af:'Apuestas y subidas divididas por calls postflop', afShort:'Agresión postflop', wtsd:'Showdowns tras ver el flop',
    wsd:'Showdowns ganados', date:'Fecha', duration:'Duración', status:'Estado',
    closed:'Cerrada', live:'Sin cierre', imported:'Importada', local:'Local',
    position:'Posición', perHand:'Por mano', sample:'Muestra mínima', page:'Página',
    comparisonNote:'Los puntos se muestran en sesiones con al menos {n} manos y muestra para la métrica.',
    zoom:'Zoom del panel', zoomIn:'Ampliar texto y panel', zoomOut:'Reducir texto y panel', zoomReset:'Restablecer zoom',
    chartResize:'Ajustar el tamaño de la gráfica',
    chartResizeHint:'Arrastra o usa las flechas para ajustar el ancho y el alto.',
    chartReset:'Restablecer el tamaño de la gráfica',
    chartResetAll:'Restaurar gráficas',
    noPlayers:'Todavía no hay jugadores con la muestra mínima en estas sesiones.',
    coverage:'Cobertura de los datos', coverageNote:'Los denominadores cuentan las muestras registradas para el jugador y el periodo. El intervalo se aplica a la fecha de inicio de las sesiones.',
    coveredNet:'Sesiones con saldo', coveredTime:'Sesiones cerradas con duración y saldo',
    coveredPreflop:'Manos con acciones preflop', coveredFlop:'Flops observados',
    coveredShowdown:'Showdowns disputados', coveredCards:'Manos con cartas y saldo',
    coveredPosition:'Manos con posición y saldo',
    classicDetail:'Resultados detallados',
    classicBalance:'Ganancias y pérdidas', classicPerformance:'Rendimiento de jugadores', classicResponse:'Tiempo medio de respuesta',
    classicBest:'Jugadas ganadoras', classicPreflop:'Apuestas y subidas · Preflop', classicFlop:'Apuestas y subidas · Flop',
    classicTurn:'Apuestas y subidas · Turn', classicRiver:'Apuestas y subidas · River',
    classicAudience:'Todos los jugadores del periodo. El jugador seleccionado se resalta en la tabla.',
    classicHumanAudience:'Jugadores humanos del periodo. El jugador seleccionado se resalta en la tabla.',
    scatterNote:'Cada punto es un jugador. Verde indica ROI positivo y rosa indica ROI negativo.',
    classicLeaders:'Se grafican hasta ocho jugadores. La tabla incluye todos los resultados.',
    stack:'Stack', buyin:'Buy-in', roi:'ROI', precision:'Precisión', played:'Participación',
    wins:'Manos ganadas', effectiveness:'Efectividad', seconds:'s', cards:'Cartas iniciales',
    handRank:'Jugada', handNumber:'Mano', stackHistory:'Stack por mano',
    stackNote:'Las líneas muestran hasta seis jugadores. Usa la leyenda para activar o desactivar series.',
    bestNote:'El gráfico incluye todas las jugadas ganadoras con categoría registrada. La tabla muestra hasta 1.000 resultados.',
    bestHumanNote:'El gráfico incluye las jugadas ganadoras de humanos con categoría registrada. La tabla muestra hasta 1.000 resultados.',
    noRank:'Sin categoría registrada', items:'resultados',
    handRanks:['Carta alta','Pareja','Doble pareja','Trío','Escalera','Color','Full','Póker','Escalera de color','Escalera real'],
    handDetail:'Detalle de la mano', board:'Cartas comunitarias', handSeats:'Posiciones',
    handPlayers:'Jugadores por calle', handActions:'Secuencia de acciones', handBalances:'Saldos registrados',
    showdown:'Showdown', handNet:'Beneficio de la mano', blinds:'Ciegas', pot:'Bote',
    button:'Botón', smallBlind:'Ciega pequeña', bigBlind:'Ciega grande', street:'Calle',
    decision:'Acción', committed:'Aportación en la calle', response:'Respuesta',
    winner:'Gana', payout:'Cobro', yes:'Sí', no:'No', fold:'Retirada', checkCall:'Paso / Igualo',
    betRaise:'Apuesta / Subida', allIn:'All-in', noHand:'No hay datos para la mano seleccionada.',
    noHandData:'Sin registros para esta mano.', handError:'No se pudo cargar la mano. Vuelve a seleccionarla.'
  },
  en: {
    stats:'Statistics', overview:'Overview', sessions:'Sessions', analysis:'Analysis',
    compare:'Compare', classics:'Classics',
    player:'Player', from:'From', through:'To', apply:'Apply', allTime:'All time',
    excludeBots:'Exclude bots', excludeBotsHelp:'Exclude bots and keep hands played by human players.',
    noHumans:'No human players are recorded in this selection.', noVisibleRows:'No visible records.',
    game:'Game', allGames:'All games', allHands:'All hands', allPlayers:'All players',
    humanPlayers:'Human players',
    previousHand:'Previous hand', nextHand:'Next hand',
    performance:'PERFORMANCE', bankroll:'Bankroll evolution', cumulative:'Cumulative',
    perSession:'Per session', bankrollNote:'Cumulative table profit for the selected period.',
    profile:'PLAYER PROFILE', behavior:'Key statistics', sessionResults:'Session results',
    prev:'Previous', next:'Next', heatmaps:'HEATMAPS', startingHands:'Starting hands',
    cardsNote:'Hands with recorded cards only. Hover over a cell to see its sample.',
    loss:'Loss', profit:'Profit', positions:'Performance by position',
    positionsNote:'Average profit per hand for each recorded position.',
    compareMark:'COMPARISONS', trends:'Stats over time', metric:'Metric',
    playerCompare:'Players in the same sessions',
    net:'Net profit', hourly:'Profit per hour', hands:'Hands played', sessionCount:'Sessions',
    settled:'Settled hands', timed:'of recorded table time',
    profitable:'profitable', noBalance:'No recorded balance', noDuration:'No recorded duration',
    loading:'Reading local history…', emptyHistory:'Statistics appear once your history contains settled hands.',
    emptyFilter:'This player has no sessions in the selected period.',
    error:'Could not load the data. Press Apply to try again.',
    invalid:'Enter valid dates (YYYY-MM-DD) and check the order of the date range.',
    noSamples:'No sample', noChart:'No data for this period', noCard:'No recorded cards',
    noTrend:'This metric needs at least two sessions with a sufficient sample.',
    vpip:'Voluntary preflop investment', pfr:'Preflop raises', threeBet:'Re-raises facing a raise',
    af:'Postflop bets and raises divided by calls', afShort:'Postflop aggression', wtsd:'Showdowns after seeing the flop',
    wsd:'Showdowns won', date:'Date', duration:'Duration', status:'Status',
    closed:'Closed', live:'Open', imported:'Imported', local:'Local',
    position:'Position', perHand:'Per hand', sample:'Minimum sample', page:'Page',
    comparisonNote:'Points require at least {n} hands in the session and a sample for the selected metric.',
    zoom:'Dashboard zoom', zoomIn:'Enlarge text and dashboard', zoomOut:'Shrink text and dashboard', zoomReset:'Reset zoom',
    chartResize:'Resize chart',
    chartResizeHint:'Drag or use the arrow keys to adjust the width and height.',
    chartReset:'Reset chart size',
    chartResetAll:'Reset charts',
    noPlayers:'No players reach the minimum sample in these sessions yet.',
    coverage:'Data coverage', coverageNote:'Denominators count recorded samples for the player and period. The date range applies to session start dates.',
    coveredNet:'Sessions with a balance', coveredTime:'Closed sessions with duration and balance',
    coveredPreflop:'Hands with preflop actions', coveredFlop:'Observed flops',
    coveredShowdown:'Contested showdowns', coveredCards:'Hands with recorded cards and balance',
    coveredPosition:'Hands with recorded position and balance',
    classicDetail:'Detailed results',
    classicBalance:'Profit and loss', classicPerformance:'Player performance', classicResponse:'Average response time',
    classicBest:'Winning hands', classicPreflop:'Bets and raises · Preflop', classicFlop:'Bets and raises · Flop',
    classicTurn:'Bets and raises · Turn', classicRiver:'Bets and raises · River',
    classicAudience:'All players in the period. The selected player is highlighted in the table.',
    classicHumanAudience:'Human players in the period. The selected player is highlighted in the table.',
    scatterNote:'Each dot is a player. Green indicates positive ROI and pink indicates negative ROI.',
    classicLeaders:'The chart shows up to eight players. The table includes every result.',
    stack:'Stack', buyin:'Buy-in', roi:'ROI', precision:'Precision', played:'Participation',
    wins:'Hands won', effectiveness:'Effectiveness', seconds:'s', cards:'Hole cards',
    handRank:'Hand rank', handNumber:'Hand', stackHistory:'Stack by hand',
    stackNote:'Lines show up to six players. Use the legend to toggle each series.',
    bestNote:'The chart includes every winning hand with a recorded rank. The table shows up to 1,000 results.',
    bestHumanNote:'The chart includes winning hands by human players with a recorded rank. The table shows up to 1,000 results.',
    noRank:'No recorded rank', items:'results',
    handRanks:['High card','Pair','Two pair','Three of a kind','Straight','Flush','Full house','Four of a kind','Straight flush','Royal flush'],
    handDetail:'Hand details', board:'Community cards', handSeats:'Positions',
    handPlayers:'Players by street', handActions:'Action sequence', handBalances:'Recorded balances',
    showdown:'Showdown', handNet:'Hand profit', blinds:'Blinds', pot:'Pot',
    button:'Button', smallBlind:'Small blind', bigBlind:'Big blind', street:'Street',
    decision:'Action', committed:'Street contribution', response:'Response',
    winner:'Won', payout:'Payout', yes:'Yes', no:'No', fold:'Fold', checkCall:'Check / Call',
    betRaise:'Bet / Raise', allIn:'All-in', noHand:'No data for the selected hand.',
    noHandData:'No records for this hand.', handError:'Could not load the hand. Select it again to retry.'
  }
};

const metrics = [['vpip','VPIP'], ['pfr','PFR'], ['threeBet','3-Bet'], ['af','AF'], ['wtsd','WTSD'], ['wsd','W$SD']];
const classicModes = new Map([
  ['BALANCE','classicBalance'], ['PERFORMANCE','classicPerformance'],
  ['RESPONSE','classicResponse'], ['BEST_HANDS','classicBest'],
  ['PREFLOP','classicPreflop'], ['FLOP','classicFlop'], ['TURN','classicTurn'], ['RIVER','classicRiver']
]);
const pageSize = 14;
const rank = [...'AKQJT98765432'];
const paint = {mint:'#71e7a8', gain:'#3f9a75', loss:'#f08596', muted:'#bdcbdd',
  axis:'#395673', grid:'#29415b', cyan:'#36d9ff', amber:'#ffe07a', violet:'#b7a3ff'};
const tabs = [...document.querySelectorAll('[role=tab]')];
const charts = new Map();
const chartResets = new Map();
const $ = id => document.getElementById(id);
const create = (tag, text, cls) => {
  const el = document.createElement(tag);
  if (text !== null) el.textContent = text;
  if (cls) el.className = cls;
  return el;
};
const send = request => new Promise((resolve, reject) => {
  if (typeof window.cefQuery !== 'function') {
    reject(new Error('Bridge unavailable'));
    return;
  }
  window.cefQuery({request, onSuccess: resolve, onFailure: (_, message) => reject(new Error(message))});
});
const query = new URLSearchParams(location.search);
const botMarker = query.get('botMarker');
const amountZeros = query.get('amountZeros');
const keepTrailingZero = amountZeros === 'if_space' || amountZeros === 'always';
const locale = query.get('lang') === 'en' ? 'en' : 'es';
const t = key => strings[locale][key];
let number, amount, integer, date, shortDate;
let activeTab = 'overview', view, page = 0, requestNumber = 0, ready = false;
let hasPlayers = false, scope = 0, zoom = 100;
let excludeBots = false;
let catalogueLoaded = false, hasGames = false, handOptions = [], selectedHand = 0;
let handView, tabBeforeHand = 'overview';
let appliedRange = ['', ''], classic, classicKey = '', classicRequest = 0, classicPage = 0;
const reducedMotion = query.get('motion') === '0' || matchMedia('(prefers-reduced-motion: reduce)').matches;

function translate() {
  document.documentElement.lang = locale;
  document.documentElement.classList.toggle('no-motion', reducedMotion);
  document.title = 'CoronaPoker | ' + t('stats');
  $('tabs').setAttribute('aria-label', t('stats'));
  $('bot-filter').dataset.tooltip = t('excludeBotsHelp');
  $('zoom-tools').setAttribute('aria-label', t('zoom'));
  for (const [id, label] of [['zoom-in','zoomIn'], ['zoom-out','zoomOut'], ['zoom-reset','zoomReset']]) {
    $(id).setAttribute('aria-label', t(label));
    $(id).dataset.tooltip = t(label);
  }
  for (const [id, label] of [['previous-hand','previousHand'], ['next-hand','nextHand']]) {
    $(id).setAttribute('aria-label', t(label));
    $(id).dataset.tooltip = t(label);
  }
  for (const el of document.querySelectorAll('[data-t]')) el.textContent = t(el.dataset.t);
  for (const id of ['from', 'through']) $(id).placeholder = locale === 'en' ? 'YYYY-MM-DD' : 'AAAA-MM-DD';
  number = new Intl.NumberFormat(locale, {maximumFractionDigits: 2});
  amount = new Intl.NumberFormat(locale, {
    minimumFractionDigits: keepTrailingZero ? 2 : 0,
    maximumFractionDigits: 2,
    trailingZeroDisplay: 'stripIfInteger'
  });
  integer = new Intl.NumberFormat(locale);
  date = new Intl.DateTimeFormat(locale, {dateStyle:'medium', timeStyle:'short'});
  shortDate = new Intl.DateTimeFormat(locale, {day:'numeric', month:'short', year:'2-digit'});
  $('trend-metric').replaceChildren(...metrics.map(([key, name]) => new Option(name, key)));
  $('classic-metric').replaceChildren(...[...classicModes].map(([mode, key]) => new Option(t(key), mode)));
  headings('sessions-head', [t('date'), t('status'), t('hands'), t('duration'), t('net'), t('cumulative')]);
  headings('positions-head', [t('position'), t('hands'), t('net'), t('perHand')]);
  headings('compare-head', [t('player'), t('hands'), t('net'), ...metrics.map(([, name]) => name)]);
  headings('hand-showdown-head', [t('player'), t('winner'), t('cards'), t('handRank'), t('payout'), t('net')]);
  headings('hand-actions-head', [t('street'), t('player'), t('decision'), t('committed'), t('response')]);
  headings('hand-balances-head', [t('player'), t('stack'), t('buyin'), t('net')]);
  headings('hand-response-head', [t('player'), t('response')]);
}

function headings(id, labels) {
  $(id).replaceChildren(...labels.map(label => {
    const th = create('th', label);
    th.scope = 'col';
    return th;
  }));
}

function val(n) { return n == null ? '–' : number.format(n); }
function money(n) { return n == null ? '–' : amount.format(n); }

const moneyMeasure = document.createElement('canvas').getContext('2d');
const moneyFitObserver = new ResizeObserver(entries => {
  for (const {target} of entries) fitMoneyLabel(target);
});

function fitMoneyLabel(label) {
  const available = label.getBoundingClientRect().width;
  if (available <= 0) return;
  const {fontFamily, fontSize, fontWeight, letterSpacing} = getComputedStyle(label);
  moneyMeasure.font = `${fontWeight} ${fontSize} ${fontFamily}`;
  moneyMeasure.letterSpacing = letterSpacing === 'normal' ? '0px' : letterSpacing;
  let text = label.dataset.fullAmount;
  if (amountZeros === 'if_space') {
    const trailingZero = locale === 'en' ? /\.([0-9]*?)0\b/g : /,([0-9]*?)0\b/g;
    while (moneyMeasure.measureText(text).width > available) {
      const matches = [...text.matchAll(trailingZero)];
      const match = matches.at(-1);
      if (!match) break;
      const keep = match[1] ? match[0].slice(0, -1) : '';
      text = text.slice(0, match.index) + keep + text.slice(match.index + match[0].length);
    }
  }
  if (label.textContent !== text) label.textContent = text;
}

function observeMoneyLabel(label) {
  if (amountZeros === 'if_space') {
    label.dataset.fullAmount = label.textContent;
    moneyFitObserver.observe(label);
  }
  return label;
}
function signed(n, formatter = val) { return (n > 0 ? '+' : '') + formatter(n); }
function tone(n) { return n > 0 ? 'positive' : n < 0 ? 'negative' : ''; }
function rateText(rate, key) {
  return rate?.value == null ? '–' : val(rate.value) + (key === 'af' ? '' : '%');
}
function duration(seconds) {
  if (!seconds) return '–';
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds % 3600 / 60);
  return hours ? hours + ' h ' + minutes + ' min' : minutes ? minutes + ' min' : '< 1 min';
}

function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').classList.toggle('error', error);
}

function emptyScopeNotice() {
  return t(excludeBots ? 'noHumans' : scope ? 'emptyFilter' : 'emptyHistory');
}

function controls(enabled) {
  for (const id of ['from', 'through', 'apply', 'reset']) $(id).disabled = !enabled;
  $('game').disabled = !enabled || !hasGames;
  $('hand-select').disabled = !enabled || !handOptions.length;
  $('previous-hand').disabled = !enabled || !neighborHand(1);
  $('next-hand').disabled = !enabled || !neighborHand(-1);
  $('player').disabled = !enabled || !hasPlayers || selectedHand !== 0;
  $('exclude-bots').disabled = !enabled || !hasGames;
}

// The list mirrors the native history, with the newest hand first.
function neighborHand(offset) {
  if (!selectedHand) return undefined;
  const index = handOptions.findIndex(hand => hand.id === selectedHand);
  return index < 0 ? undefined : handOptions[index + offset];
}

function navigateHand(offset) {
  const hand = neighborHand(offset);
  if (ready && hand) loadScope(scope, hand.id);
}
function selectTab(id, focus = false) {
  if (activeTab !== id) $('stats-view').scrollTo(0, 0);
  activeTab = id;
  $('classic-filter').hidden = id !== 'classics';
  for (const tab of tabs) {
    const active = tab.dataset.tab === id;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    $(tab.dataset.tab).hidden = !active;
    if (active && focus) tab.focus();
  }
  render();
  if (id === 'classics') {
    if (ready) notice(excludeBots && !hasPlayers ? emptyScopeNotice() : '');
    refreshClassic();
  } else if (ready && id !== 'hand') {
    notice(!hasPlayers ? emptyScopeNotice()
      : view && !view.sessionCount ? t('emptyFilter') : '');
  }
}

function statCard(label, value, caption, icon, hero, cls, monetary = false) {
  const card = create('div', null, hero ? 'kpi hero' : 'kpi');
  const top = create('div', label, 'kpi-label');
  const glyph = create('span', icon, 'kpi-icon');
  glyph.setAttribute('aria-hidden', 'true');
  top.append(glyph);
  const amountLabel = create('strong', value, cls);
  card.append(top, monetary ? observeMoneyLabel(amountLabel) : amountLabel);
  if (caption) card.append(create('small', caption));
  return card;
}

function showOverview() {
  const v = view;
  $('kpis').replaceChildren(
    statCard(t('net'), signed(v?.net, money), v?.net == null ? t('noBalance') : '', '↗', true, tone(v?.net), true),
    statCard(t('hourly'), signed(v?.hourly, money), v?.hourly == null ? t('noDuration') : duration(v.timedSeconds) + ' ' + t('timed'), '◷', false, tone(v?.hourly), true),
    statCard(t('hands'), v ? integer.format(v.hands) : '–', t('settled'), '♠', false, ''),
    statCard(t('sessionCount'), v ? integer.format(v.sessionCount) : '–',
      v?.sessionsWithBalance ? integer.format(v.winningSessions) + '/' + integer.format(v.sessionsWithBalance) + ' ' + t('profitable') : t('noBalance'), '▤', false, '')
  );
  $('rates').replaceChildren(...metrics.map(([key, name]) => {
    const rate = v?.rates[key];
    const block = create('div', null, 'rate');
    const title = create('div', null, 'rate-top');
    title.append(create('span', name, 'metric-name'), create('span', rateText(rate, key), 'value'));
    const sample = rate?.sample ? integer.format(rate.numerator) + ' / ' + integer.format(rate.sample) : t('noSamples');
    const rail = create('i', null);
    const meter = create('span', null);
    meter.style.width = key === 'af' ? Math.min(100, (rate?.value ?? 0) * 20) + '%' : (rate?.value ?? 0) + '%';
    rail.setAttribute('aria-hidden', 'true');
    rail.append(meter);
    const description = create('small', t(key === 'af' ? 'afShort' : key));
    description.dataset.tooltip = t(key);
    block.append(title, description, rail, create('small', sample));
    return block;
  }));
  const rows = v?.sessions ?? [];
  const options = timeChart(rows, t('bankroll'), money);
  options.series = [
    {name:t('perSession'), type:'bar', barMaxWidth:22, data:rows.map(row => ({
      value:row.net ?? null, itemStyle:{color:row.net < 0 ? paint.loss : paint.gain, borderRadius:2}
    }))},
    {name:t('cumulative'), type:'line', data:rows.map(row => row.running ?? null),
      connectNulls:true, showSymbol:rows.length < 24, symbolSize:6,
      lineStyle:{width:2.5, color:paint.mint}, itemStyle:{color:paint.mint},
      areaStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[
        {offset:0,color:'#5fdfa440'}, {offset:1,color:'#5fdfa400'}
      ])}}
  ];
  draw('bankroll-chart', options, rows.some(row => row.net != null) ? '' : t('noChart'));
  showCoverage();
}

function showCoverage() {
  const countedHands = buckets => buckets?.reduce((count, bucket) => count + bucket.hands, 0);
  const rows = [
    ['coveredNet', view?.sessionsWithBalance, view?.sessionCount],
    ['coveredTime', view?.timedSessions, view?.sessionCount],
    ['coveredPreflop', view?.rates.vpip.sample, view?.hands],
    ['coveredFlop', view?.rates.wtsd.sample, view?.hands],
    ['coveredShowdown', view?.rates.wsd.sample, view?.hands],
    ['coveredCards', countedHands(view?.cards), view?.hands],
    ['coveredPosition', countedHands(view?.positions), view?.hands]
  ];
  $('coverage-rows').replaceChildren(...rows.map(([key, count, total]) => {
    const item = create('div', null);
    item.append(create('span', t(key)), create('strong', total ? integer.format(count)
      + ' / ' + integer.format(total) : '–', 'coverage-value'));
    return item;
  }));
}

function chartFont(factor = .79) {
  return Math.round(parseFloat(getComputedStyle(document.documentElement).fontSize) * factor);
}

function timeChart(rows, label, formatter) {
  const zoomable = rows.length > 1;
  return {
    aria:{enabled:true, label:{description:label}}, animation:!reducedMotion,
    color:[paint.mint, paint.gain],
    grid:{left:chartFont(4.4), right:18, top:18, bottom:zoomable ? 59 : 33},
    tooltip:{...tooltip(), trigger:'axis', valueFormatter:formatter,
      formatter:items => {
        const row = rows[items[0]?.dataIndex];
        if (!row) return '';
        return [date.format(row.startedAt), ...items.map(item => {
          const value = typeof item.value === 'object' ? item.value?.value : item.value;
          return item.seriesName + '  ' + formatter(value);
        })].join('\n');
      }},
    xAxis:{type:'category', data:rows.map(row => shortDate.format(row.startedAt)),
      axisLabel:{color:paint.muted, hideOverlap:true, fontSize:chartFont()},
      axisLine:{lineStyle:{color:paint.axis}}, axisTick:{show:false}},
    yAxis:{type:'value', axisLabel:{color:paint.muted, fontSize:chartFont(), formatter},
      splitLine:{lineStyle:{color:paint.grid, type:'dashed'}}},
    dataZoom:zoomable ? [{type:'inside', zoomOnMouseWheel:'ctrl', moveOnMouseWheel:false},
      {type:'slider', height:20, bottom:7, showDetail:false, brushSelect:false,
      borderColor:paint.axis, fillerColor:'#54bb8b30', handleStyle:{color:paint.mint},
      dataBackground:{lineStyle:{color:paint.gain}, areaStyle:{color:paint.grid}}}] : []
  };
}

function tooltip() {
  return {renderMode:'html', appendTo:document.body, confine:false,
    backgroundColor:'#0b1423', borderColor:paint.axis,
    textStyle:{color:'#edf5f1', fontSize:chartFont(.86)}, padding:12,
    transitionDuration:0, hideDelay:0,
    extraCssText:'white-space:pre-wrap;overflow-wrap:anywhere;'};
}

function addChartResize(plot) {
  const panel = plot.closest('.panel');
  const frame = create('div', null, 'chart-frame');
  const slot = create('div', null, 'chart-slot');
  const tools = create('div', null, 'chart-tools');
  const grip = create('button', null, 'chart-resizer');
  const resetButton = create('button', '↺', 'ghost chart-reset');
  grip.type = 'button';
  resetButton.type = 'button';
  grip.dataset.tooltip = t('chartResizeHint');
  resetButton.dataset.tooltip = t('chartReset');
  resetButton.hidden = true;
  grip.setAttribute('aria-label', t('chartResize'));
  resetButton.setAttribute('aria-label', t('chartReset'));
  grip.setAttribute('aria-controls', plot.id);
  plot.before(frame);
  frame.append(slot, tools);
  tools.append(resetButton, grip);
  slot.append(plot);

  function resize(width, height) {
    if (width != null) {
      const available = $('stats-view').clientWidth;
      const maxWidth = Math.max(available, Math.min(4096, available * 3));
      const pixels = Math.round(Math.max(Math.min(300, slot.clientWidth), Math.min(maxWidth, width)));
      // The two layout measurements can round in opposite directions.
      const panelChrome = panel.offsetWidth - slot.clientWidth + 1;
      panel.style.minWidth = (pixels + panelChrome) + 'px';
      panel.classList.add('chart-panel-resized');
      plot.style.width = pixels + 'px';
    }
    if (height != null) plot.style.height = Math.round(Math.max(130, Math.min(1100, height))) + 'px';
    chartResets.set(plot, reset);
    $('reset-charts').hidden = false;
    resetButton.hidden = false;
  }

  function reset() {
    plot.style.removeProperty('width');
    plot.style.removeProperty('height');
    panel.style.removeProperty('min-width');
    panel.classList.remove('chart-panel-resized');
    chartResets.delete(plot);
    $('reset-charts').hidden = chartResets.size === 0;
    if (document.activeElement === resetButton) grip.focus({preventScroll:true});
    resetButton.hidden = true;
  }

  let drag;
  grip.addEventListener('pointerdown', event => {
    if (event.button !== 0 || !event.isPrimary) return;
    grip.setPointerCapture(event.pointerId);
    const width = plot.offsetWidth;
    drag = {x:event.clientX, y:event.clientY, dx:0, dy:0, width, height:plot.offsetHeight,
      scale:plot.getBoundingClientRect().width / width};
  });
  grip.addEventListener('pointermove', event => {
    if (!drag || !grip.hasPointerCapture(event.pointerId)) return;
    const dx = (event.clientX - drag.x) / drag.scale;
    const dy = (event.clientY - drag.y) / drag.scale;
    if (dx === drag.dx && dy === drag.dy) return;
    resize(dx !== drag.dx ? drag.width + dx : null, dy !== drag.dy ? drag.height + dy : null);
    drag.dx = dx;
    drag.dy = dy;
  });
  grip.addEventListener('lostpointercapture', () => { drag = undefined; });
  resetButton.addEventListener('click', reset);
  grip.addEventListener('keydown', event => {
    const step = event.shiftKey ? 96 : 24;
    const dx = event.key === 'ArrowRight' ? step : event.key === 'ArrowLeft' ? -step : 0;
    const dy = event.key === 'ArrowDown' ? step : event.key === 'ArrowUp' ? -step : 0;
    if (event.key === 'Home') reset();
    else if (dx || dy) resize(dx ? plot.offsetWidth + dx : null, dy ? plot.offsetHeight + dy : null);
    else return;
    event.preventDefault();
  });
}

function draw(id, options, empty) {
  let chart = charts.get(id);
  if (!chart) {
    addChartResize($(id));
    chart = echarts.init($(id));
    charts.set(id, chart);
    new ResizeObserver(() => {
      if ($(id).clientWidth && $(id).clientHeight) chart.resize();
    }).observe($(id));
  }
  if (!empty && options.tooltip) {
    const format = options.tooltip.formatter;
    options.tooltip.position = (point, _params, tip) => {
      const {left, top} = chart.getDom().getBoundingClientRect();
      const zoom = Number(getComputedStyle(document.body).zoom) || 1;
      tip.style.maxWidth = `min(30rem, ${(innerWidth - 24) / zoom}px)`;
      const {width, height} = tip.getBoundingClientRect();
      const [x, y] = window.coronaTooltipPosition(
        point[0] * zoom + left, point[1] * zoom + top, width, height);
      return [(x - left) / zoom, (y - top) / zoom];
    };
    if (format) options.tooltip.formatter = params => create('div', format(params));
  }
  chart.resize();
  chart.setOption(empty ? {
    aria:{enabled:true, label:{description:empty}},
    title:{text:empty, left:'center', top:'middle', textStyle:{color:paint.muted, fontSize:chartFont(.84), fontWeight:500}},
    animation:false
  } : options, {notMerge:true});
}

function row(cells, selected = false) {
  const tr = create('tr', null, selected ? 'selected' : '');
  for (const [text, cls] of cells) tr.append(create('td', text, cls));
  return tr;
}

function emptyRow(columns, message) {
  const tr = create('tr', null);
  const td = create('td', message);
  td.colSpan = columns;
  tr.append(td);
  return tr;
}

function showSessions() {
  const sessions = view?.sessions ?? [];
  const last = Math.max(0, Math.ceil(sessions.length / pageSize) - 1);
  const upper = sessions.length - page * pageSize;
  const rows = sessions.slice(Math.max(0, upper - pageSize), upper).reverse().map(session => {
    const tr = row([[date.format(session.startedAt)], [session.closed ? t('closed') : t('live')],
      [integer.format(session.hands)], [duration(session.seconds)], [signed(session.net, money), tone(session.net)],
      [signed(session.running, money), tone(session.running)]]);
    tr.children[1].append(create('span', session.imported ? t('imported') : t('local'), 'session-status'));
    return tr;
  });
  $('session-rows').replaceChildren(...(rows.length ? rows : [emptyRow(6, t('noChart'))]));
  $('session-count').textContent = integer.format(sessions.length) + ' ' + t('sessions').toLowerCase();
  $('page-label').textContent = sessions.length ? t('page') + ' ' + (page + 1) + ' / ' + (last + 1) : '';
  $('prev').disabled = page === 0;
  $('next').disabled = page === last;
}

function heatColor(value, magnitude) {
  if (value == null) return '#18283e';
  if (value === 0 || magnitude === 0) return '#34495f';
  const intensity = Math.min(1, Math.abs(value) / magnitude);
  return value < 0 ? 'rgb(' + [Math.round(82 + intensity * 91), Math.round(46 + intensity * 25), Math.round(58 + intensity * 36)] + ')'
    : 'rgb(' + [Math.round(34 + intensity * 32), Math.round(76 + intensity * 62), Math.round(59 + intensity * 34)] + ')';
}

function heatmap(x, y, cells, label, empty, showAxes) {
  const magnitude = Math.max(0, ...cells.map(item => Math.abs(item.bucket?.perHand ?? 0)));
  return {
    aria:{enabled:true, label:{description:label}}, animation:!reducedMotion,
    tooltip:{...tooltip(), formatter:params => {
      const {name, bucket} = params.data;
      return bucket ? [name, t('hands') + '  ' + integer.format(bucket.hands),
        t('net') + '  ' + signed(bucket.net, money), t('perHand') + '  ' + signed(bucket.perHand, money)].join('\n')
        : name + '\n' + empty;
    }},
    grid:{left:showAxes ? 27 : 0, right:4, top:5, bottom:showAxes ? 26 : 11},
    xAxis:{type:'category', data:x, axisTick:{show:false}, axisLine:{show:false},
      axisLabel:{show:showAxes, color:paint.muted, interval:0, fontSize:chartFont(.75)}},
    yAxis:{type:'category', data:y, inverse:true, axisTick:{show:false}, axisLine:{show:false},
      axisLabel:{show:showAxes, color:paint.muted, interval:0, fontSize:chartFont(.75)}},
    series:[{type:'heatmap', label:{show:true, color:'#edf5f1', fontSize:chartFont(.71),
      formatter:params => params.data.name}, emphasis:{itemStyle:{borderColor:paint.mint, borderWidth:2}},
      data:cells.map(({x, y, name, bucket}) => ({
        value:[x, y, bucket?.perHand ?? 0], name, bucket,
        label:{color:bucket ? '#fff' : '#b4c2d5'},
        itemStyle:{color:heatColor(bucket?.perHand, magnitude), borderColor:'#101a2e', borderWidth:2}
      }))}]
  };
}

function showAnalysis() {
  const byCard = new Map((view?.cards ?? []).map(bucket => [bucket.key, bucket]));
  const cardCells = rank.flatMap((high, y) => rank.map((low, x) => {
    const name = x === y ? high + low : x > y ? high + low + 's' : low + high + 'o';
    return {x, y, name, bucket:byCard.get(name)};
  }));
  draw('cards-chart', heatmap(rank, rank, cardCells, t('startingHands'), t('noCard'), true), '');
  const order = ['BTN','SB','BB','UTG','MP','CO'];
  const positions = view?.positions ?? [];
  const byPosition = new Map(positions.map(bucket => [bucket.key, bucket]));
  const cells = order.map((name, x) => ({x, y:0, name, bucket:byPosition.get(name)}));
  draw('position-chart', heatmap(order, [''], cells, t('positions'), t('noSamples'), false), positions.length ? '' : t('noChart'));
  $('position-rows').replaceChildren(...(positions.length ? positions.map(bucket => row([
    [bucket.key], [integer.format(bucket.hands)], [signed(bucket.net, money), tone(bucket.net)],
    [signed(bucket.perHand, money), tone(bucket.perHand)]
  ])) : [emptyRow(4, t('noSamples'))]));
}

function showCompare() {
  const rows = view?.sessions ?? [];
  const key = $('trend-metric').value;
  const name = metrics.find(item => item[0] === key)?.[1] ?? '';
  const min = view?.comparisonHands ?? 30;
  const values = rows.map(session => session.hands >= min ? session.rates[key]?.value ?? null : null);
  const options = timeChart(rows, t('trends'), n => n == null ? '–' : val(n) + (key === 'af' ? '' : '%'));
  options.series = [{name, type:'line', data:values, connectNulls:false, symbolSize:7,
    lineStyle:{color:paint.mint, width:2}, itemStyle:{color:paint.mint}}];
  draw('trend-chart', options, values.filter(value => value != null).length < 2 ? t('noTrend') : '');
  $('trend-note').textContent = t('comparisonNote').replace('{n}', integer.format(min));
  $('sample-badge').textContent = t('sample') + ' ' + integer.format(min);
  const comparison = view?.comparison ?? [];
  const player = $('player').selectedOptions[0]?.textContent;
  $('compare-rows').replaceChildren(...(comparison.length ? comparison.map(entry => row([
    [entry.player], [integer.format(entry.hands)], [signed(entry.net, money), tone(entry.net)],
    ...metrics.map(([id]) => [rateText(entry.rates[id], id)])
  ], entry.player === player)) : [emptyRow(9, t('noPlayers'))]));
}

function classicAxes() {
  return {axisLabel:{color:paint.muted, fontSize:chartFont()}, axisTick:{show:false},
    axisLine:{lineStyle:{color:paint.axis}},
    splitLine:{lineStyle:{color:paint.grid, type:'dashed'}}};
}

function classicBars(rows, valueOf, formatter, colorOf, scale, limit = 8) {
  const entries = rows.slice(0, limit);
  return {
    grid:{left:10, right:20, top:10, bottom:25, containLabel:true},
    tooltip:{...tooltip(), trigger:'axis', valueFormatter:formatter},
    xAxis:{...classicAxes(), type:'value', max:scale,
      axisLabel:{...classicAxes().axisLabel, formatter}},
    yAxis:{...classicAxes(), type:'category', inverse:true, data:entries.map(entry => entry.player),
      axisLabel:{...classicAxes().axisLabel, width:chartFont(9), overflow:'truncate'}},
    series:[{type:'bar', barMaxWidth:chartFont(1.25), data:entries.map(entry => ({
      value:valueOf(entry), itemStyle:{color:colorOf(entry), borderRadius:4}
    }))}]
  };
}

function stackChart(rows, players) {
  const series = players.slice(0, 6).map(({player}) => ({
    name:player, type:'line', showSymbol:rows.length < 30, symbolSize:5,
    encode:{tooltip:1},
    data:rows.filter(entry => entry.player === player).map(entry => [entry.handCounter, entry.stack])
  })).filter(entry => entry.data.length);
  return {
    color:[paint.cyan, paint.amber, paint.violet, paint.mint, paint.loss, '#83a5ff'],
    legend:{type:'scroll', textStyle:{color:paint.muted, fontSize:chartFont()}, top:0},
    grid:{left:chartFont(3.9), right:25, top:42, bottom:53},
    tooltip:{...tooltip(), trigger:'axis', valueFormatter:money},
    xAxis:{...classicAxes(), type:'value', name:t('handNumber'), nameLocation:'middle', nameGap:22,
      nameTextStyle:{color:paint.muted, fontSize:chartFont()}, minInterval:1},
    yAxis:{...classicAxes(), type:'value', min:0,
      axisLabel:{...classicAxes().axisLabel, formatter:money}},
    dataZoom:[{type:'inside', zoomOnMouseWheel:'ctrl', moveOnMouseWheel:false}],
    series
  };
}

function performanceScatter(entries) {
  return {
    grid:{left:chartFont(4.7), right:22, top:17, bottom:54},
    xAxis:{...classicAxes(), type:'value', min:0, max:100, name:t('played'),
      nameLocation:'middle', nameGap:29, nameTextStyle:{color:paint.muted, fontSize:chartFont()},
      axisLabel:{...classicAxes().axisLabel, formatter:'{value}%'}},
    yAxis:{...classicAxes(), type:'value', min:0, max:value => Math.max(100, value.max),
      name:t('precision'), nameLocation:'middle', nameGap:48,
      nameTextStyle:{color:paint.muted, fontSize:chartFont()},
      axisLabel:{...classicAxes().axisLabel, formatter:'{value}%'}},
    tooltip:{...tooltip(), formatter:params => [
      params.data.name, t('played') + '  ' + val(params.value[0]) + '%',
      t('precision') + '  ' + val(params.value[1]) + '%',
      t('roi') + '  ' + signed(params.value[2]) + '%'
    ].join('\n')},
    series:[{type:'scatter', symbolSize:chartFont(.95), data:entries.map(entry => ({
      name:entry.player, value:[entry.playedPercent, entry.precisionPercent, entry.roiPercent],
      itemStyle:{color:entry.roiPercent < 0 ? paint.loss : entry.roiPercent > 0 ? paint.mint : paint.cyan,
        opacity:.73, borderColor:'#071323', borderWidth:1}
    }))}]
  };
}

function handRank(value) { return t('handRanks')[value - 1] ?? t('noRank'); }

function winningDonut(entries) {
  const slices = entries.map(entry => ({value:entry.count, name:handRank(entry.handValue)}));
  return {
    color:['#36d9ff','#6a9fff','#b7a3ff','#e791c1','#ffe07a','#fba578','#ff8995','#71e7a8','#4fc5be','#b6e080'],
    title:{text:integer.format(slices.reduce((total, slice) => total + slice.value, 0)),
      left:'center', top:'36%', textStyle:{color:'#edf3fb', fontSize:chartFont(1.8)}},
    legend:{type:'scroll', bottom:2, textStyle:{color:paint.muted, fontSize:chartFont()}},
    tooltip:{...tooltip(), formatter:params => params.name + '\n' + integer.format(params.value) + ' (' + val(params.percent) + '%)'},
    series:[{type:'pie', center:['50%', '43%'], radius:['48%', '70%'],
      label:{show:false}, itemStyle:{borderColor:'#101a2e', borderWidth:3, borderRadius:4},
      data:slices}]
  };
}

function renderCards(cell, cards) {
  const suits = {P:'♠', C:'♥', T:'♣', D:'♦'};
  const badges = (cards ?? []).flatMap(card => {
    const match = /^(10|[2-9AJQKT])[_\s]*([PCTD♠♥♣♦])$/.exec(card.trim().toUpperCase());
    if (!match) return [];
    const suit = suits[match[2]] ?? match[2];
    return [create('span', (match[1] === 'T' ? '10' : match[1]) + suit,
      suit === '♥' || suit === '♦' ? 'poker-card red' : 'poker-card')];
  });
  if (badges.length) cell.replaceChildren(...badges);
}

function setHandMode(selected) {
  if (selected && activeTab !== 'hand') tabBeforeHand = activeTab;
  for (const tab of tabs) tab.hidden = selected !== (tab.dataset.tab === 'hand');
  $('period-filters').hidden = selected;
  if (selected || activeTab === 'hand') selectTab(selected ? 'hand' : tabBeforeHand);
  else render();
}

function showHand() {
  $('hand-detail').hidden = !handView;
  if (!handView) return;
  const {hand} = handView;
  const visible = player => !excludeBots || !botMarker || !player?.includes(botMarker);
  const visibleRows = rows => rows.filter(entry => visible(entry.player));
  const showdown = visibleRows(handView.showdown);
  const streets = ['Preflop', 'Flop', 'Turn', 'River'];
  const elapsed = hand.startedAtMillis > 0 && hand.endedAtMillis >= hand.startedAtMillis
    ? Math.floor((hand.endedAtMillis - hand.startedAtMillis) / 1000) : null;
  const clock = elapsed == null ? '–' : [Math.floor(elapsed / 3600),
    Math.floor(elapsed / 60) % 60, elapsed % 60].map(n => String(n).padStart(2, '0')).join(':');
  $('hand-heading').textContent = t('handNumber') + ' ' + integer.format(hand.counter);
  $('hand-facts').replaceChildren(...[
    ['blinds', money(hand.smallBlind) + ' / ' + money(2 * hand.smallBlind)],
    ['pot', money(hand.pot)], ['duration', clock]
  ].map(([key, value]) => {
    const fact = create('div', null);
    const label = create('strong', value);
    fact.append(create('span', t(key)), key === 'duration' ? label : observeMoneyLabel(label));
    return fact;
  }));
  $('hand-board').textContent = t('noCard');
  renderCards($('hand-board'), hand.communityCards);
  $('hand-seats').replaceChildren(...[
    ['button', hand.dealer], ['smallBlind', hand.smallBlindPlayer], ['bigBlind', hand.bigBlindPlayer]
  ].flatMap(([key, player]) => [create('dt', t(key)), create('dd', visible(player) && player || '–')]));
  const rosters = [hand.preflopPlayers, hand.flopPlayers, hand.turnPlayers, hand.riverPlayers];
  $('hand-rosters').replaceChildren(...rosters.map((players, i) => {
    const item = create('div', null);
    const chips = create('div', null, 'hand-chips');
    const displayed = players.filter(visible);
    chips.append(...(displayed.length ? displayed : ['–']).map(player => create('span', player, 'hand-chip')));
    item.append(create('span', streets[i], 'hand-street'), chips);
    return item;
  }));
  handRows('hand-showdown-rows', 6, showdown.map(entry => {
    const tr = row([[entry.player], [t(entry.winner ? 'yes' : 'no'), entry.winner ? 'positive' : ''],
      ['–'], [handRank(entry.handValue)], [money(entry.pay)], [signed(entry.profit, money), tone(entry.profit)]]);
    renderCards(tr.children[2], entry.holeCards);
    return tr;
  }));
  const hasProfit = showdown.some(entry => entry.profit !== 0);
  $('hand-result-panel').hidden = !hasProfit;
  if (hasProfit) {
    const outcomes = [...showdown].sort((a, b) => b.profit - a.profit);
    draw('hand-chart', {...classicBars(outcomes, entry => entry.profit, money,
      entry => entry.profit < 0 ? paint.loss : paint.gain, undefined, outcomes.length),
    animation:!reducedMotion, aria:{enabled:true, label:{description:t('handNet')}}}, '');
  }
  const decisions = ['', 'fold', 'checkCall', 'betRaise', 'allIn'];
  const responseText = seconds => seconds == null ? '–' : val(seconds) + ' ' + t('seconds');
  handRows('hand-actions-rows', 5, visibleRows(handView.actions).map(entry => row([
    [streets[entry.street - 1] ?? '–'], [entry.player],
    [decisions[entry.decision] ? t(decisions[entry.decision]) : '–'],
    [money(entry.committed)], [responseText(entry.responseSeconds)]
  ])));
  handRows('hand-balances-rows', 4, visibleRows(handView.balances).map(entry => row([
    [entry.player], [money(entry.stack)], [money(entry.buyin)], [signed(entry.profit, money), tone(entry.profit)]
  ])));
  handRows('hand-response-rows', 2, visibleRows(handView.responseTimes).map(entry => row([
    [entry.player], [responseText(entry.value)]
  ])));
}

function handRows(id, columns, rows) {
  $(id).replaceChildren(...(rows.length ? rows : [emptyRow(columns, t(excludeBots ? 'noVisibleRows' : 'noHandData'))]));
}

function classicPresentation(data, mode) {
  const metric = t(classicModes.get(mode));
  const player = t('player');
  if (mode === 'BALANCE') {
    const leaders = [...data.balances].sort((a, b) => Math.abs(b.profit) - Math.abs(a.profit));
    const hasStacks = data.stacks.length > 0;
    return {
      title:hasStacks ? t('stackHistory') : metric, entries:data.balances,
      columns:[player,t('stack'),t('buyin'),t('net'),t('roi')],
      cells:entry => [[entry.player], [money(entry.stack)], [money(entry.buyin)],
        [signed(entry.profit, money),tone(entry.profit)], [signed(entry.roiPercent) + '%',tone(entry.roiPercent)]],
      options:hasStacks ? stackChart(data.stacks, leaders)
        : classicBars(leaders, entry => entry.profit, money, entry => entry.profit < 0 ? paint.loss : paint.gain),
      note:hasStacks ? t('stackNote') : t('classicLeaders')
    };
  }
  if (mode === 'PERFORMANCE') {
    return {
      title:metric, entries:data.performance,
      columns:[player,t('played'),t('wins'),t('precision'),t('roi'),t('effectiveness')],
      cells:entry => [[entry.player], [val(entry.playedPercent) + '%'], [val(entry.wonPercent) + '%'],
        [val(entry.precisionPercent) + '%'], [signed(entry.roiPercent) + '%',tone(entry.roiPercent)],
        [signed(entry.effectiveness),tone(entry.effectiveness)]],
      options:performanceScatter(data.performance), note:t('scatterNote')
    };
  }
  if (mode === 'BEST_HANDS') {
    const options = winningDonut(data.winningRanks);
    return {
      title:metric, entries:data.bestHands,
      columns:[player,t('cards'),t('handRank'),t('handNumber'),t('net')],
      cells:entry => [[entry.player], ['–'], [handRank(entry.handValue)], [integer.format(entry.handCounter)],
        [signed(entry.profit, money),tone(entry.profit)]],
      options, empty:!options.series[0].data.length, note:t(excludeBots ? 'bestHumanNote' : 'bestNote')
    };
  }
  const response = mode === 'RESPONSE';
  const unit = response ? ' ' + t('seconds') : '%';
  return {
    title:metric, entries:data.metrics, columns:[player, response ? metric : '%'],
    cells:entry => [[entry.player], [val(entry.value) + unit]],
    options:classicBars(data.metrics, entry => entry.value, value => val(value) + unit,
      () => response ? paint.amber : paint.cyan, response ? undefined : 100),
    note:t('classicLeaders')
  };
}

function showClassic() {
  $('classic-audience').textContent = t(excludeBots ? 'classicHumanAudience' : 'classicAudience');
  const mode = $('classic-metric').value;
  if (!classic) {
    $('classic-chart-title').textContent = t(classicModes.get(mode));
    $('classic-note').textContent = '';
    $('classic-count').textContent = '';
    headings('classic-head', []);
    $('classic-rows').replaceChildren();
    $('classic-page').textContent = '';
    $('classic-prev').disabled = $('classic-next').disabled = true;
    draw('classic-chart', {}, classicKey ? t('loading') : t('noChart'));
    return;
  }
  const display = classicPresentation(classic, mode);
  const player = $('player').selectedOptions[0]?.textContent;
  const entries = display.entries;
  const last = Math.max(0, Math.ceil(entries.length / pageSize) - 1);
  const rows = entries.slice(classicPage * pageSize, (classicPage + 1) * pageSize).map(entry => {
    const tr = row(display.cells(entry), entry.player === player);
    if (mode === 'BEST_HANDS') renderCards(tr.children[1], entry.holeCards);
    return tr;
  });
  $('classic-chart-title').textContent = display.title;
  $('classic-note').textContent = display.note;
  $('classic-count').textContent = integer.format(entries.length) + ' ' + t('items');
  headings('classic-head', display.columns);
  $('classic-rows').replaceChildren(...(rows.length ? rows : [emptyRow(display.columns.length, t('noChart'))]));
  $('classic-page').textContent = entries.length ? t('page') + ' ' + (classicPage + 1) + ' / ' + (last + 1) : '';
  $('classic-prev').disabled = classicPage === 0;
  $('classic-next').disabled = classicPage === last;
  draw('classic-chart', {...display.options, aria:{enabled:true, label:{description:display.title}},
    animation:!reducedMotion}, !entries.length || display.empty ? t('noChart') : '');
}

async function refreshClassic() {
  if (!ready || activeTab !== 'classics') return;
  const request = ['classic', scope, $('classic-metric').value, ...appliedRange].join('\t');
  if (request === classicKey) return;
  const sequence = ++classicRequest;
  classicKey = request;
  classic = undefined;
  classicPage = 0;
  showClassic();
  try {
    const result = JSON.parse(await send(request));
    if (sequence !== classicRequest) return;
    classic = result;
    if (activeTab === 'classics') showClassic();
  } catch (failure) {
    if (sequence !== classicRequest) return;
    classicKey = '';
    if (activeTab === 'classics') {
      showClassic();
      notice(t('error'), true);
    }
  }
}

function render() {
  moneyFitObserver.disconnect();
  if (!window.echarts) return;
  if (activeTab === 'overview') showOverview();
  if (activeTab === 'sessions') showSessions();
  if (activeTab === 'analysis') showAnalysis();
  if (activeTab === 'compare') showCompare();
  if (activeTab === 'classics') showClassic();
  if (activeTab === 'hand') showHand();
}

function validDate(value) {
  if (!value) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return false;
  const parsed = new Date(value + 'T00:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

async function applyFilters() {
  if (!ready || selectedHand !== 0) return;
  const from = $('from').value.trim();
  const through = $('through').value.trim();
  const reversed = from && through && from > through;
  $('from').setAttribute('aria-invalid', String(!validDate(from) || Boolean(reversed)));
  $('through').setAttribute('aria-invalid', String(!validDate(through) || Boolean(reversed)));
  if (!validDate(from) || !validDate(through) || reversed) {
    requestNumber++;
    notice(t('invalid'), true);
    return;
  }
  appliedRange = [from, through];
  if (activeTab === 'classics') {
    notice('');
    refreshClassic();
  }
  if (!hasPlayers) {
    if (excludeBots || activeTab !== 'classics') notice(emptyScopeNotice());
    return;
  }
  const sequence = ++requestNumber;
  if (activeTab !== 'classics') notice(t('loading'));
  try {
    const response = await send(['view', scope, $('player').value, from, through].join('\t'));
    if (sequence !== requestNumber) return;
    view = JSON.parse(response);
    page = 0;
    render();
    if (activeTab !== 'classics') notice(view.sessionCount ? '' : t('emptyFilter'));
  } catch (failure) {
    if (sequence === requestNumber) notice(t('error'), true);
  }
}

async function initialize() {
  translate();
  changeZoom(0);
  render();
  notice(t('loading'));
  if (!window.echarts) {
    send('broken').catch(() => {});
    return;
  }
  await send('ready');
  await loadCatalogue();
}

async function loadCatalogue() {
  notice(t('loading'));
  try {
    const games = JSON.parse(await send('games'));
    const options = document.createDocumentFragment();
    options.append(new Option(t('allGames'), '0'));
    for (const game of games) {
      const name = game.server || t('game');
      const caption = game.startedAtMillis > 0 ? name + ' (' + date.format(game.startedAtMillis) + ')' : name;
      options.append(new Option(caption, game.id));
    }
    $('game').replaceChildren(options);
    catalogueLoaded = true;
    hasGames = games.length > 0;
    await loadScope(0, 0);
  } catch (failure) {
    $('apply').disabled = false;
    notice(t('error'), true);
  }
}

async function loadScope(gameId, handId, hideBots = excludeBots) {
  const sequence = ++requestNumber;
  const previousReady = ready;
  const focus = document.activeElement;
  ready = false;
  classicRequest++;
  controls(false);
  notice(t('loading'));
  try {
    const data = JSON.parse(await send(['select', gameId, handId, hideBots ? 1 : 0].join('\t')));
    if (sequence !== requestNumber) return;
    view = undefined;
    handView = data.detail;
    page = 0;
    classic = undefined;
    classicKey = '';
    const gameChanged = scope !== data.scope;
    scope = data.scope;
    excludeBots = hideBots;
    selectedHand = data.handId;
    handOptions = data.hands;
    $('game').value = String(scope);
    if (gameChanged) {
      $('hand-select').replaceChildren(new Option(t('allHands'), '0'), ...handOptions.map(hand =>
        new Option(t('handNumber') + ' ' + integer.format(hand.counter), hand.id)));
    }
    $('hand-select').value = String(selectedHand);
    if (selectedHand) {
      $('player').replaceChildren(new Option(t(excludeBots ? 'humanPlayers' : 'allPlayers'), ''));
    } else {
      $('player').replaceChildren(...data.players.map((player, i) => new Option(player, i)));
      if (data.players.length) $('player').selectedIndex = data.selected;
    }
    hasPlayers = !selectedHand && data.players.length > 0;
    setHandMode(selectedHand !== 0);
    ready = true;
    controls(true);
    if ((focus === $('previous-hand') || focus === $('next-hand') || focus === $('exclude-bots'))
        && !focus.disabled) {
      focus.focus({preventScroll:true});
    }
    if (selectedHand) {
      notice(handView ? '' : t('noHand'));
    } else {
      await applyFilters();
    }
  } catch (failure) {
    if (sequence !== requestNumber) return;
    $('game').value = String(scope);
    $('hand-select').value = String(selectedHand);
    $('exclude-bots').checked = excludeBots;
    ready = previousReady;
    classicKey = '';
    controls(ready);
    $('apply').disabled = false;
    notice(t(handId ? 'handError' : 'error'), true);
  }
}

function changeZoom(delta) {
  zoom = delta === 0 ? 100 : Math.max(80, Math.min(160, zoom + delta));
  document.body.style.zoom = (zoom / 100).toString();
  $('zoom-reset').textContent = zoom + '%';
  $('zoom-out').disabled = zoom === 80;
  $('zoom-in').disabled = zoom === 160;
}

for (const tab of tabs) tab.addEventListener('click', () => selectTab(tab.dataset.tab));
$('tabs').addEventListener('keydown', event => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const visible = tabs.filter(tab => !tab.hidden);
  const current = visible.findIndex(tab => tab.dataset.tab === activeTab);
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? visible.length - 1
    : (current + (event.key === 'ArrowRight' ? 1 : -1) + visible.length) % visible.length;
  selectTab(visible[next].dataset.tab, true);
});
$('zoom-out').addEventListener('click', () => changeZoom(-10));
$('zoom-in').addEventListener('click', () => changeZoom(10));
$('zoom-reset').addEventListener('click', () => changeZoom(0));
$('stats-view').addEventListener('scroll', () => {
  for (const chart of charts.values()) chart.dispatchAction({type:'hideTip'});
}, {passive:true});
$('reset-charts').addEventListener('click', () => {
  for (const reset of chartResets.values()) reset();
  $('stats-view').scrollLeft = 0;
  $('zoom-reset').focus({preventScroll:true});
});
document.addEventListener('keydown', event => {
  if (!(event.ctrlKey || event.metaKey) || !['+', '=', '-', '0'].includes(event.key)) return;
  event.preventDefault();
  changeZoom(event.key === '0' ? 0 : event.key === '-' ? -10 : 10);
});
$('apply').addEventListener('click', () => {
  if (ready) applyFilters();
  else if (catalogueLoaded) loadScope(scope, selectedHand);
  else loadCatalogue();
});
$('reset').addEventListener('click', () => {
  $('from').value = $('through').value = '';
  applyFilters();
});
$('player').addEventListener('change', applyFilters);
$('exclude-bots').addEventListener('change', event => loadScope(scope, selectedHand, event.target.checked));
$('game').addEventListener('change', () => loadScope(Number($('game').value), 0));
$('hand-select').addEventListener('change', () => loadScope(scope, Number($('hand-select').value)));
$('previous-hand').addEventListener('click', () => navigateHand(1));
$('next-hand').addEventListener('click', () => navigateHand(-1));
$('prev').addEventListener('click', () => { page--; showSessions(); });
$('next').addEventListener('click', () => { page++; showSessions(); });
$('trend-metric').addEventListener('change', showCompare);
$('classic-metric').addEventListener('change', refreshClassic);
$('classic-prev').addEventListener('click', () => { classicPage--; showClassic(); });
$('classic-next').addEventListener('click', () => { classicPage++; showClassic(); });
for (const id of ['from','through']) $(id).addEventListener('keydown', event => {
  if (event.key === 'Enter') applyFilters();
});
initialize().catch(() => notice(t('error'), true));
