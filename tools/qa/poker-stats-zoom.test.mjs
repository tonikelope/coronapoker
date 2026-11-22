import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = fs.readFileSync(new URL(
  '../../modules/coronapoker-gdx/src/main/resources/poker-stats/dashboard.js', import.meta.url), 'utf8');
const zoomHandlers = source.slice(source.indexOf('function changeZoom('),
  source.indexOf('for (const tab of tabs) tab.addEventListener'));

function dashboard() {
  const events = () => ({
    listeners: new Map(),
    addEventListener(name, listener) {
      const group = this.listeners.get(name) ?? [];
      group.push(listener);
      this.listeners.set(name, group);
    },
    emit(name, detail = {}) {
      for (const listener of this.listeners.get(name) ?? []) listener(detail);
    }
  });
  const button = () => ({
    ...events(), disabled: false,
    setPointerCapture(pointerId) { this.captured = pointerId; }
  });
  const plus = button(), minus = button(), reset = button();
  const window = events(), document = {...events(), body: {style: {}}};
  let now = 0, serial = 0;
  const timers = new Map();
  vm.runInNewContext(`let zoom = 100; ${zoomHandlers}
bindZoomHold($('zoom-out'), -10); bindZoomHold($('zoom-in'), 10);`, {
    window, document,
    $: id => ({'zoom-out': minus, 'zoom-in': plus, 'zoom-reset': reset})[id],
    setTimeout(callback, delay) {
      timers.set(++serial, {callback, at: now + delay});
      return serial;
    },
    clearTimeout: id => timers.delete(id)
  });
  return {
    plus, minus, window, document,
    zoom: () => reset.textContent,
    advance(ms) {
      const until = now + ms;
      while (timers.size) {
        const [id, timer] = [...timers].sort((a, b) => a[1].at - b[1].at)[0];
        if (timer.at > until) break;
        now = timer.at;
        timers.delete(id);
        timer.callback();
      }
      now = until;
    }
  };
}

test('a press steps once and another pointer cannot stop its hold', () => {
  const page = dashboard();
  page.plus.emit('pointerdown', {button: 0, pointerId: 4});
  assert.equal(page.plus.captured, 4);
  assert.equal(page.zoom(), '110%');
  page.plus.emit('pointerup', {pointerId: 7});
  page.advance(419);
  assert.equal(page.zoom(), '110%');
  page.advance(1);
  assert.equal(page.zoom(), '120%');
  page.advance(45);
  assert.equal(page.zoom(), '130%');
  page.plus.emit('pointerup', {pointerId: 4});
  page.plus.emit('click', {detail: 1});
  page.advance(1000);
  assert.equal(page.zoom(), '130%');
});

test('zoom holds stop at bounds, on cancellation and on focus loss', () => {
  const page = dashboard();
  page.plus.emit('pointerdown', {button: 0, pointerId: 1});
  page.advance(1000);
  assert.equal(page.zoom(), '160%');
  assert.equal(page.plus.disabled, true);

  page.minus.emit('pointerdown', {button: 0, pointerId: 2});
  page.minus.emit('pointercancel', {pointerId: 2});
  page.advance(1000);
  assert.equal(page.zoom(), '150%');
  page.minus.emit('pointerdown', {button: 0, pointerId: 3});
  page.window.emit('blur');
  page.advance(1000);
  assert.equal(page.zoom(), '140%');
});

test('a keyboard click steps and a non-primary press does not', () => {
  const page = dashboard();
  page.minus.emit('click', {detail: 0});
  assert.equal(page.zoom(), '90%');
  page.minus.emit('pointerdown', {button: 2, pointerId: 1});
  page.advance(1000);
  assert.equal(page.zoom(), '90%');
});
