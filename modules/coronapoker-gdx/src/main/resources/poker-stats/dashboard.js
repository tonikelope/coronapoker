/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
'use strict';

const strings = {
  es: {
    stats:'Estadísticas', overview:'Resumen', sessions:'Sesiones', analysis:'Análisis',
    compare:'Comparativas', history:'Historial', close:'Cerrar', title:'Estadísticas de juego',
    eyebrow:'TEXAS HOLD’EM / REGISTRO DE MESA', intro:'Resultados, tendencias y decisiones mano a mano.',
    player:'Jugador', from:'Desde', through:'Hasta', apply:'Aplicar', allTime:'Todo',
    performance:'RENDIMIENTO', bankroll:'Evolución del bankroll', cumulative:'Acumulado',
    perSession:'Por sesión', bankrollNote:'Beneficio acumulado de las mesas del periodo, en fichas.',
    profile:'PERFIL DE JUEGO', behavior:'Estadísticas clave', sessionResults:'Resultados por sesión',
    prev:'Anterior', next:'Siguiente', heatmaps:'MAPAS DE CALOR', startingHands:'Manos iniciales',
    cardsNote:'Solo manos con cartas registradas. Pasa el cursor para ver la muestra.',
    loss:'Pérdida', profit:'Ganancia', positions:'Rendimiento por posición',
    positionsNote:'Beneficio medio por mano en cada posición registrada.',
    compareMark:'COMPARATIVAS', trends:'Evolución de estadísticas', metric:'Métrica',
    playerCompare:'Jugadores en las mismas sesiones', footer:'Estadísticas del historial local',
    net:'Beneficio neto', hourly:'Ganancia por hora', hands:'Manos jugadas',
    sessionCount:'Sesiones', chips:'fichas', perHour:'fichas/h', settled:'Manos liquidadas',
    timed:'de mesa registradas', profitable:'con beneficio', noBalance:'Sin saldo registrado',
    noDuration:'Sin duración registrada', loading:'Consultando el historial local…',
    emptyHistory:'Las estadísticas aparecerán cuando haya manos liquidadas en el historial.',
    emptyFilter:'No hay sesiones para este jugador en el periodo seleccionado.',
    error:'No se pudo cargar el historial. Puedes reintentar o abrir Historial.',
    invalid:'Introduce fechas válidas (AAAA-MM-DD) y comprueba el orden del intervalo.',
    noSamples:'Sin muestra', noChart:'Sin datos para este periodo', noCard:'Sin cartas registradas',
    noTrend:'Se necesitan dos sesiones con muestra suficiente para esta métrica.',
    vpip:'Inversión voluntaria preflop', pfr:'Subidas preflop', threeBet:'Resubidas ante una subida',
    af:'Apuestas y subidas / calls postflop', wtsd:'Showdowns tras ver el flop',
    wsd:'Showdowns ganados', date:'Fecha', duration:'Duración', status:'Estado',
    closed:'Cerrada', live:'Sin cierre', imported:'Importada', local:'Local',
    position:'Posición', perHand:'Por mano', sample:'Muestra mínima', page:'Página',
    comparisonNote:'Los puntos se muestran en sesiones con al menos {n} manos y muestra para la métrica.',
    zoom:'Zoom del panel', zoomIn:'Ampliar texto y panel', zoomOut:'Reducir texto y panel', zoomReset:'Restablecer zoom',
    noPlayers:'Todavía no hay jugadores con la muestra mínima en estas sesiones.'
  },
  en: {
    stats:'Statistics', overview:'Overview', sessions:'Sessions', analysis:'Analysis',
    compare:'Compare', history:'History', close:'Close', title:'Poker statistics',
    eyebrow:'TEXAS HOLD’EM / TABLE HISTORY', intro:'Results, trends and decisions, hand by hand.',
    player:'Player', from:'From', through:'To', apply:'Apply', allTime:'All time',
    performance:'PERFORMANCE', bankroll:'Bankroll evolution', cumulative:'Cumulative',
    perSession:'Per session', bankrollNote:'Cumulative table profit for the selected period, in chips.',
    profile:'PLAYER PROFILE', behavior:'Key statistics', sessionResults:'Session results',
    prev:'Previous', next:'Next', heatmaps:'HEATMAPS', startingHands:'Starting hands',
    cardsNote:'Hands with recorded cards only. Hover over a cell to see its sample.',
    loss:'Loss', profit:'Profit', positions:'Performance by position',
    positionsNote:'Average profit per hand for each recorded position.',
    compareMark:'COMPARISONS', trends:'Stats over time', metric:'Metric',
    playerCompare:'Players in the same sessions', footer:'Statistics from local history',
    net:'Net profit', hourly:'Profit per hour', hands:'Hands played', sessionCount:'Sessions',
    chips:'chips', perHour:'chips/h', settled:'Settled hands', timed:'of recorded table time',
    profitable:'profitable', noBalance:'No recorded balance', noDuration:'No recorded duration',
    loading:'Reading local history…', emptyHistory:'Statistics appear once your history contains settled hands.',
    emptyFilter:'This player has no sessions in the selected period.',
    error:'Could not read the history. Try again or open History.',
    invalid:'Enter valid dates (YYYY-MM-DD) and check the order of the date range.',
    noSamples:'No sample', noChart:'No data for this period', noCard:'No recorded cards',
    noTrend:'This metric needs at least two sessions with a sufficient sample.',
    vpip:'Voluntary preflop investment', pfr:'Preflop raises', threeBet:'Re-raises facing a raise',
    af:'Postflop bets and raises / calls', wtsd:'Showdowns after seeing the flop',
    wsd:'Showdowns won', date:'Date', duration:'Duration', status:'Status',
    closed:'Closed', live:'Open', imported:'Imported', local:'Local',
    position:'Position', perHand:'Per hand', sample:'Minimum sample', page:'Page',
    comparisonNote:'Points require at least {n} hands in the session and a sample for the selected metric.',
    zoom:'Dashboard zoom', zoomIn:'Enlarge text and dashboard', zoomOut:'Shrink text and dashboard', zoomReset:'Reset zoom',
    noPlayers:'No players reach the minimum sample in these sessions yet.'
  }
};

const metrics = [['vpip','VPIP'], ['pfr','PFR'], ['threeBet','3-Bet'], ['af','AF'], ['wtsd','WTSD'], ['wsd','W$SD']];
const pageSize = 14;
const rank = [...'AKQJT98765432'];
const paint = {mint:'#71e7a8', gain:'#3b8b66', loss:'#e27b88', muted:'#b4c2d5', axis:'#31445f', grid:'#23344b'};
const tabs = [...document.querySelectorAll('[role=tab]')];
const charts = new Map();
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
const locale = query.get('lang') === 'en' ? 'en' : 'es';
const t = key => strings[locale][key];
let number, integer, date, shortDate;
let activeTab = 'overview', view, page = 0, requestNumber = 0, ready = false;
let hasPlayers = false, scope = 0, zoom = 100;
const reducedMotion = query.get('motion') === '0' || matchMedia('(prefers-reduced-motion: reduce)').matches;

function translate() {
  document.documentElement.lang = locale;
  document.documentElement.classList.toggle('no-motion', reducedMotion);
  document.title = 'CoronaPoker | ' + t('stats');
  $('tabs').setAttribute('aria-label', t('stats'));
  $('zoom-tools').setAttribute('aria-label', t('zoom'));
  for (const [id, label] of [['zoom-in','zoomIn'], ['zoom-out','zoomOut'], ['zoom-reset','zoomReset']]) {
    $(id).setAttribute('aria-label', t(label));
    $(id).title = t(label);
  }
  for (const el of document.querySelectorAll('[data-t]')) el.textContent = t(el.dataset.t);
  for (const id of ['from', 'through']) $(id).placeholder = locale === 'en' ? 'YYYY-MM-DD' : 'AAAA-MM-DD';
  number = new Intl.NumberFormat(locale, {maximumFractionDigits: 2});
  integer = new Intl.NumberFormat(locale);
  date = new Intl.DateTimeFormat(locale, {dateStyle:'medium', timeStyle:'short'});
  shortDate = new Intl.DateTimeFormat(locale, {day:'numeric', month:'short', year:'2-digit'});
  $('trend-metric').replaceChildren(...metrics.map(([key, name]) => new Option(name, key)));
  headings('sessions-head', [t('date'), t('status'), t('hands'), t('duration'), t('net'), t('cumulative')]);
  headings('positions-head', [t('position'), t('hands'), t('net'), t('perHand')]);
  headings('compare-head', [t('player'), t('hands'), t('net'), ...metrics.map(([, name]) => name)]);
}

function headings(id, labels) {
  $(id).replaceChildren(...labels.map(label => {
    const th = create('th', label);
    th.scope = 'col';
    return th;
  }));
}

function val(n) { return n == null ? '–' : number.format(n); }
function signed(n) { return n != null && n > 0 ? '+' + val(n) : val(n); }
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
function controls(enabled) {
  for (const id of ['player', 'from', 'through', 'apply', 'reset']) $(id).disabled = !enabled;
}
function selectTab(id, focus = false) {
  activeTab = id;
  for (const tab of tabs) {
    const active = tab.dataset.tab === id;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    $(tab.dataset.tab).hidden = !active;
    if (active && focus) tab.focus();
  }
  render();
}

function statCard(label, value, caption, icon, hero, cls) {
  const card = create('div', null, hero ? 'kpi hero' : 'kpi');
  const top = create('div', label, 'kpi-label');
  const glyph = create('span', icon, 'kpi-icon');
  glyph.setAttribute('aria-hidden', 'true');
  top.append(glyph);
  card.append(top, create('strong', value, cls), create('small', caption));
  return card;
}

function showOverview() {
  const v = view;
  $('kpis').replaceChildren(
    statCard(t('net'), signed(v?.net), v?.net == null ? t('noBalance') : t('chips'), '↗', true, tone(v?.net)),
    statCard(t('hourly'), signed(v?.hourly), v?.hourly == null ? t('noDuration') : duration(v.timedSeconds) + ' ' + t('timed') + ' / ' + t('perHour'), '◷', false, tone(v?.hourly)),
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
    block.append(title, create('small', t(key)), rail, create('small', sample));
    return block;
  }));
  const rows = v?.sessions ?? [];
  const options = timeChart(rows, t('bankroll'), val);
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
}

function timeChart(rows, label, formatter) {
  const zoomable = rows.length > 1;
  return {
    aria:{enabled:true, label:{description:label}}, animation:!reducedMotion,
    color:[paint.mint, paint.gain],
    grid:{left:59, right:15, top:14, bottom:zoomable ? 53 : 30},
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
      axisLabel:{color:paint.muted, hideOverlap:true, fontSize:10},
      axisLine:{lineStyle:{color:paint.axis}}, axisTick:{show:false}},
    yAxis:{type:'value', axisLabel:{color:paint.muted, fontSize:10, formatter},
      splitLine:{lineStyle:{color:paint.grid, type:'dashed'}}},
    dataZoom:zoomable ? [{type:'inside', zoomOnMouseWheel:'ctrl', moveOnMouseWheel:false},
      {type:'slider', height:16, bottom:7, showDetail:false, brushSelect:false,
      borderColor:paint.axis, fillerColor:'#54bb8b30', handleStyle:{color:paint.mint},
      dataBackground:{lineStyle:{color:paint.gain}, areaStyle:{color:paint.grid}}}] : []
  };
}

function tooltip() {
  return {renderMode:'richText', confine:true, backgroundColor:'#0b1423',
    borderColor:paint.axis, textStyle:{color:'#edf5f1', fontSize:12}, padding:10};
}

function draw(id, options, empty) {
  let chart = charts.get(id);
  if (!chart) {
    chart = echarts.init($(id));
    charts.set(id, chart);
    new ResizeObserver(() => {
      if ($(id).clientWidth && $(id).clientHeight) chart.resize();
    }).observe($(id));
  }
  chart.resize();
  chart.setOption(empty ? {
    aria:{enabled:true, label:{description:empty}},
    title:{text:empty, left:'center', top:'middle', textStyle:{color:paint.muted, fontSize:13, fontWeight:500}},
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
      [integer.format(session.hands)], [duration(session.seconds)], [signed(session.net), tone(session.net)],
      [signed(session.running), tone(session.running)]]);
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
        t('net') + '  ' + signed(bucket.net), t('perHand') + '  ' + signed(bucket.perHand)].join('\n')
        : name + '\n' + empty;
    }},
    grid:{left:showAxes ? 19 : 0, right:4, top:5, bottom:showAxes ? 20 : 11},
    xAxis:{type:'category', data:x, axisTick:{show:false}, axisLine:{show:false},
      axisLabel:{show:showAxes, color:paint.muted, interval:0, fontSize:10}},
    yAxis:{type:'category', data:y, inverse:true, axisTick:{show:false}, axisLine:{show:false},
      axisLabel:{show:showAxes, color:paint.muted, interval:0, fontSize:10}},
    series:[{type:'heatmap', label:{show:true, color:'#edf5f1', fontSize:10,
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
    [bucket.key], [integer.format(bucket.hands)], [signed(bucket.net), tone(bucket.net)],
    [signed(bucket.perHand), tone(bucket.perHand)]
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
    [entry.player], [integer.format(entry.hands)], [signed(entry.net), tone(entry.net)],
    ...metrics.map(([id]) => [rateText(entry.rates[id], id)])
  ], entry.player === player)) : [emptyRow(9, t('noPlayers'))]));
}

function render() {
  if (!window.echarts) return;
  if (activeTab === 'overview') showOverview();
  if (activeTab === 'sessions') showSessions();
  if (activeTab === 'analysis') showAnalysis();
  if (activeTab === 'compare') showCompare();
}

function validDate(value) {
  if (!value) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return false;
  const parsed = new Date(value + 'T00:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

async function applyFilters() {
  if (!hasPlayers) return;
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
  const sequence = ++requestNumber;
  notice(t('loading'));
  try {
    const response = await send(['view', scope, $('player').value, from, through].join('\t'));
    if (sequence !== requestNumber) return;
    view = JSON.parse(response);
    page = 0;
    render();
    notice(view.sessionCount ? '' : t('emptyFilter'));
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
  await loadScope();
}

async function loadScope() {
  const sequence = ++requestNumber;
  const previous = $('player').selectedOptions[0]?.textContent;
  ready = false;
  hasPlayers = false;
  view = undefined;
  render();
  controls(false);
  notice(t('loading'));
  try {
    const data = JSON.parse(await send('init'));
    if (sequence !== requestNumber) return;
    scope = data.scope;
    $('player').replaceChildren(...data.players.map((player, i) => new Option(player, i)));
    hasPlayers = data.players.length > 0;
    ready = true;
    controls(hasPlayers);
    if (hasPlayers) {
      const remembered = data.players.indexOf(previous);
      $('player').selectedIndex = remembered >= 0 ? remembered : data.selected;
      await applyFilters();
    } else {
      view = undefined;
      render();
      notice(scope ? t('emptyFilter') : t('emptyHistory'));
    }
  } catch (failure) {
    if (sequence !== requestNumber) return;
    ready = false;
    $('apply').disabled = false;
    notice(t('error'), true);
  }
}

function changeZoom(delta) {
  zoom = delta === 0 ? 100 : Math.max(80, Math.min(160, zoom + delta));
  document.body.style.zoom = (zoom / 100).toString();
  $('zoom-reset').textContent = zoom + '%';
  $('zoom-out').disabled = zoom === 80;
  $('zoom-in').disabled = zoom === 160;
}

window.coronaStatsScope = loadScope;
for (const tab of tabs) tab.addEventListener('click', () => selectTab(tab.dataset.tab));
$('tabs').addEventListener('keydown', event => {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const current = tabs.findIndex(tab => tab.dataset.tab === activeTab);
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
    : (current + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
  selectTab(tabs[next].dataset.tab, true);
});
$('history').addEventListener('click', () => send('history').catch(() => {}));
$('zoom-out').addEventListener('click', () => changeZoom(-10));
$('zoom-in').addEventListener('click', () => changeZoom(10));
$('zoom-reset').addEventListener('click', () => changeZoom(0));
document.addEventListener('keydown', event => {
  if (!(event.ctrlKey || event.metaKey) || !['+', '=', '-', '0'].includes(event.key)) return;
  event.preventDefault();
  changeZoom(event.key === '0' ? 0 : event.key === '-' ? -10 : 10);
});
$('apply').addEventListener('click', () => ready ? applyFilters() : loadScope());
$('reset').addEventListener('click', () => {
  $('from').value = $('through').value = '';
  applyFilters();
});
$('player').addEventListener('change', applyFilters);
$('prev').addEventListener('click', () => { page--; showSessions(); });
$('next').addEventListener('click', () => { page++; showSessions(); });
$('trend-metric').addEventListener('change', showCompare);
for (const id of ['from','through']) $(id).addEventListener('keydown', event => {
  if (event.key === 'Enter') applyFilters();
});
initialize().catch(() => notice(t('error'), true));
