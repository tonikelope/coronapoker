import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const html = fs.readFileSync(new URL('../../modules/coronapoker-gdx/src/main/resources/poker-video-player.html', import.meta.url), 'utf8');
const inline = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const ids = ['ABCDEFGHIJK', 'LMNOPQRSTUV', 'WXYZ0123456'];

/** Executes the shipped player script, with a deterministic clock and YouTube transport. */
function playerPage(options = {}) {
  let now = 0, serial = 0, reloads = 0;
  const timers = new Map(), messages = [], loads = [];
  const budget = options.budget || { attempts: 0 };
  const status = { className: '', children: [], _text: '',
    get textContent() { return this._text; },
    set textContent(value) { this._text = value; this.children = []; },
    appendChild(child) { this.children.push(child); } };
  const schedule = (callback, delay, interval = false) => {
    const id = ++serial;
    timers.set(id, { callback, due: now + delay, interval: interval ? delay : 0 });
    return id;
  };
  let native;
  const context = {
    console, document: { getElementById: () => status,
      createElement: tag => ({ tag }), head: { appendChild: script => { context.apiScript = script; } } },
    location: { reload: () => { reloads++; } },
    setTimeout: (callback, delay) => schedule(callback, delay),
    clearTimeout: id => timers.delete(id),
    setInterval: (callback, delay) => schedule(callback, delay, true),
    clearInterval: id => timers.delete(id),
    cefQuery: query => {
      messages.push(query.request);
      if (options.bridgeThrows) throw new Error('bridge unavailable');
      if (query.request.startsWith('READY|') && options.dropReadyAck) return;
      if (query.request.startsWith('RETRY_API|') && options.dropRecoveryAck) return;
      if (query.request.startsWith('RETRY_API_MANUAL|')) { budget.attempts = 0; query.onSuccess('reload'); }
      else if (query.request.startsWith('RETRY_API|')) query.onSuccess(budget.attempts++ < 1 ? 'reload' : 'stop');
      else query.onSuccess('ok');
    },
    YT: { Player: function(ignored, setup) {
      if (options.constructorThrows) throw new Error('YT construction failed');
      native = {
        setup, state: -1, muted: true, volume: 100, seconds: 17, id: ids[0], plays: 0,
        throwLoad: false, getPlayerState() { return this.state; },
        getCurrentTime() { return this.seconds; }, getVolume() { return this.volume; },
        isMuted() { return this.muted; }, mute() { this.muted = true; },
        unMute() { this.muted = false; }, setVolume(value) { this.volume = value; },
        playVideo() { this.plays++; },
        pauseVideo() { this.emit(2); },
        loadVideoById(value) {
          if (this.throwLoad) throw new Error('load failed');
          this.id = typeof value === 'string' ? value : value.videoId;
          const startSeconds = typeof value === 'string' ? 0 : value.startSeconds;
          if (!options.stalePlaying) {
            this.seconds = startSeconds;
            this.state = -1;
          }
          loads.push({ id: this.id, seconds: startSeconds });
        },
        emit(state) { this.state = state; this.setup.events.onStateChange({ data: state, target: this }); }
      };
      return native;
    } }
  };
  context.window = context;
  vm.createContext(context);
  let script = inline;
  const replacements = { VIDEO_IDS: ids.map(id => `'${id}'`).join(','), ORIGIN: 'http://127.0.0.1:10000',
    PLAYER_LANGUAGE: 'es', INITIAL_INDEX: '0', START_SECONDS: '17', INITIAL_MUTED: 'true', INITIAL_VOLUME: '100',
    ORDERED_PLAYLIST: String(Boolean(options.ordered)), PLAYLIST_REVISION: '0' };
  for (const [key, value] of Object.entries(replacements)) script = script.replaceAll(`@@${key}@@`, value);
  vm.runInContext(script, context);
  return {
    context, status, messages, loads, budget,
    get native() { return native; }, get reloads() { return reloads; },
    ready() { context.onYouTubeIframeAPIReady(); if (native) native.setup.events.onReady({ target: native }); },
    retry() { assert.equal(status.children.length, 1); status.children[0].onclick(); },
    tick(milliseconds) {
      const until = now + milliseconds;
      while (true) {
        let nextId, next;
        for (const [id, timer] of timers) {
          if (timer.due <= until && (!next || timer.due < next.due)) { nextId = id; next = timer; }
        }
        if (!next) break;
        now = next.due;
        if (next.interval) next.due += next.interval; else timers.delete(nextId);
        next.callback();
      }
      now = until;
    }
  };
}

test('favorite navigation keeps the saved order across wrap and reports the new playlist revision', () => {
  const page = playerPage({ ordered: true });
  page.ready();
  assert.ok(page.messages.includes('READY|0'));
  page.context.coronaSelectRelative(-1);
  assert.equal(page.loads.at(-1).id, ids[2]);
  page.context.coronaSelectRelative(1);
  assert.equal(page.loads.at(-1).id, ids[0]);
  page.context.coronaReplaceVideos([ids[2], ids[1]], true, 7, 1);
  assert.equal(page.loads.at(-1).id, ids[1]);
  assert.ok(page.messages.includes('INDEX|1:1:7'));
  page.context.coronaSelectRelative(1);
  assert.equal(page.loads.at(-1).id, ids[2]);
  assert.ok(page.messages.includes('INDEX|0:0:7'));
  page.context.coronaReportPosition();
  assert.ok(page.messages.includes('POSITION|0:0:7'));
});

test('API ready without first playback retries, skips and terminates with a retry action', () => {
  const page = playerPage(); page.ready(); page.tick(20000);
  assert.deepEqual(page.loads, [{ id: ids[0], seconds: 17 }]);
  page.tick(41000);
  assert.equal(page.loads.at(-1).id, ids[1]);
  assert.equal(page.status.className, 'error');
  const count = page.loads.length; page.tick(120000);
  assert.equal(page.loads.length, count, 'Fatal error must not run an endless recovery loop');
  page.retry(); assert.equal(page.loads.length, count + 1);
});

test('API initialization retries only once across documents and supports an explicit retry', () => {
  const budget = { attempts: 0 };
  const first = playerPage({ budget }); first.tick(30000); assert.equal(first.reloads, 1);
  const second = playerPage({ budget }); second.tick(30000);
  assert.equal(second.reloads, 0); assert.equal(second.status.className, 'error');
  second.retry(); assert.equal(second.reloads, 1); assert.equal(budget.attempts, 0);
});

test('failed script download uses the same bounded API recovery', () => {
  const page = playerPage(); page.context.apiScript.onerror();
  assert.equal(page.reloads, 1); page.tick(60000); assert.equal(page.reloads, 1);
});

test('a missing READY acknowledgment cannot remain loading forever', () => {
  const page = playerPage({ dropReadyAck: true }); page.ready(); page.tick(10000);
  assert.equal(page.status.className, 'error'); page.retry(); assert.equal(page.reloads, 1);
});

test('a missing recovery acknowledgment also exposes an explicit retry', () => {
  const page = playerPage({ dropRecoveryAck: true });
  page.tick(40000);
  assert.equal(page.status.className, 'error');
  page.retry();
  assert.equal(page.reloads, 1);
});

test('a modal during the READY handshake resumes its deadline on return', () => {
  const page = playerPage({ dropReadyAck: true }); page.ready();
  page.context.coronaSetActive(false); page.tick(90000);
  assert.notEqual(page.status.className, 'error');
  page.context.coronaSetActive(true); page.tick(10000);
  assert.equal(page.status.className, 'error');
});

test('bridge errors and constructor exceptions produce recoverable errors', () => {
  const bridge = playerPage({ bridgeThrows: true }); bridge.context.apiScript.onerror();
  assert.equal(bridge.status.className, 'error');
  const constructor = playerPage({ constructorThrows: true }); constructor.ready();
  assert.equal(constructor.status.className, 'error'); constructor.retry(); assert.equal(constructor.reloads, 1);
});

test('manually selecting another video cancels the old video deadline', () => {
  const page = playerPage(); page.ready(); page.tick(5000);
  page.context.coronaSelectRelative(1); page.tick(15000);
  assert.equal(page.loads.length, 1); assert.equal(page.loads[0].id, ids[1]);
  page.tick(5000); assert.equal(page.loads[1].id, ids[1]);
});

test('a stale PLAYING state cannot stop the watchdog for the newly selected video', () => {
  const page = playerPage({ stalePlaying: true });
  page.ready();
  page.native.emit(1);
  page.native.seconds = 84;
  page.context.coronaSelectRelative(1);
  page.tick(20000);
  assert.deepEqual(page.loads, [{ id: ids[1], seconds: 0 }, { id: ids[1], seconds: 0 }]);
  assert.ok(page.messages.some(message => message.startsWith('RECOVERY|playback timeout 1')));
});

test('a hidden modal suspends load deadlines and resumes them only when visible', () => {
  const page = playerPage(); page.ready(); page.context.coronaSetActive(false); page.tick(90000);
  assert.equal(page.loads.length, 0); assert.notEqual(page.status.className, 'error');
  page.context.coronaSetActive(true); page.tick(20000); assert.equal(page.loads.length, 1);
});

test('a user pause after playback is not mistaken for a load failure', () => {
  const page = playerPage(); page.ready(); page.native.emit(1); page.native.emit(2); page.tick(180000);
  assert.equal(page.loads.length, 0); assert.equal(page.status.className, 'hidden');
});

test('buffer recovery preserves the current position and chosen audible state', () => {
  const page = playerPage(); page.ready(); page.native.emit(1);
  page.native.unMute(); page.tick(250); page.native.seconds = 83; page.native.emit(3);
  page.tick(44999); assert.equal(page.loads.length, 0);
  page.tick(1); assert.deepEqual(page.loads[0], { id: ids[0], seconds: 83 });
  assert.equal(page.native.muted, false); assert.equal(page.native.volume, 100);
});

test('embedding errors skip promptly without a stale watchdog retrying the forbidden video', () => {
  const page = playerPage(); page.ready(); page.tick(19950);
  page.native.setup.events.onError({ data: 150 }); page.tick(100);
  assert.equal(page.loads.length, 1); assert.equal(page.loads[0].id, ids[1]);
});

test('autoplay policy failure exposes an explicit retry and keeps mute intact', () => {
  const page = playerPage(); page.ready(); page.native.setup.events.onAutoplayBlocked();
  assert.equal(page.status.className, 'error'); page.tick(60000); assert.equal(page.loads.length, 0);
  page.retry(); assert.equal(page.loads.length, 1); assert.equal(page.native.muted, true);
});

test('a load exception does not disable the retry action', () => {
  const page = playerPage(); page.ready(); page.native.throwLoad = true;
  page.context.coronaSelectRelative(1); assert.equal(page.status.className, 'error');
  page.native.throwLoad = false; page.retry(); assert.equal(page.loads.at(-1).id, ids[1]);
});

test('normal and favorite catalogue replacement retain audio and clear transient failures', () => {
  const page = playerPage(); page.ready(); page.native.emit(1); page.native.unMute(); page.tick(250);
  page.context.coronaReplaceVideos([ids[2]]); assert.equal(page.loads.at(-1).id, ids[2]);
  assert.equal(page.native.muted, false);
  page.native.emit(1); page.context.coronaReplaceVideos(ids); assert.equal(page.loads.at(-1).id, ids[0]);
  assert.equal(page.native.muted, false);
});

test('a native unmute immediately after playback survives a carousel change before the first poll', () => {
  const page = playerPage(); page.ready(); page.native.emit(1); page.native.unMute();
  page.context.coronaSelectRelative(1);
  assert.equal(page.native.muted, false);
});
