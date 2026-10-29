'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.resolve(__dirname, '../../main/assets/terminal');
const policy = require(path.join(assets, 'terminal-resize.js'));
let passed = 0;
function test(name, body) { body(); passed++; console.log('PASS ' + name); }
function harness() {
  let size = { cols: 80, rows: 25 }, busy = false, accepted = true, id = 0;
  const timers = new Map(), applied = [], sent = [];
  const coordinator = policy.create({
    measure: () => size, isBusy: () => busy,
    apply: (cols, rows) => applied.push([cols, rows]),
    notify: (cols, rows) => { if (!accepted) return false; sent.push([cols, rows]); return true; },
    schedule: callback => { timers.set(++id, callback); return id; },
    cancel: key => timers.delete(key)
  });
  return { coordinator, applied, sent, timers,
    size: value => { size = value; }, busy: value => { busy = value; },
    accept: value => { accepted = value; },
    tick: () => { const callbacks = Array.from(timers.values()); timers.clear(); callbacks.forEach(callback => callback()); }
  };
}
test('bursts publish only the final geometry', () => {
  const h = harness(); h.coordinator.request();
  h.size({ cols: 100, rows: 14 }); h.coordinator.request();
  h.size({ cols: 120, rows: 18 }); h.coordinator.request(); h.tick();
  assert.deepEqual(h.sent, [[120, 18]]);
});
test('identical geometry does not notify twice', () => {
  const h = harness(); h.coordinator.request(); h.tick(); h.coordinator.request(); h.tick();
  assert.equal(h.sent.length, 1);
});
test('zero, missing and undersized geometry preserves last valid size', () => {
  const h = harness(); h.coordinator.request(); h.tick();
  for (const value of [null, {cols: 0, rows: 0}, {cols: 80, rows: 1}, {cols: 4, rows: 25}, {cols: NaN, rows: 25}]) {
    h.size(value); h.coordinator.request(); h.tick();
  }
  assert.deepEqual(h.sent, [[80, 25]]);
});
test('resize waits for the active terminal write', () => {
  const h = harness(); h.busy(true); h.coordinator.request(); h.tick();
  assert.equal(h.applied.length, 0); h.busy(false); h.coordinator.flush();
  assert.deepEqual(h.applied, [[80, 25]]);
});
test('write completion cannot bypass the resize debounce', () => {
  const h = harness(); h.coordinator.request(); h.coordinator.flush();
  assert.equal(h.applied.length, 0); h.tick(); assert.equal(h.applied.length, 1);
});
test('keyboard round trip reports both valid sizes', () => {
  const h = harness(); h.coordinator.request(); h.tick();
  h.size({cols: 80, rows: 10}); h.coordinator.request(); h.tick();
  h.size({cols: 80, rows: 25}); h.coordinator.request(); h.tick();
  assert.deepEqual(h.sent, [[80, 25], [80, 10], [80, 25]]);
});
test('unavailable bridge does not mark size as delivered', () => {
  const h = harness(); h.accept(false); h.coordinator.request(); h.tick();
  assert.equal(h.sent.length, 0); h.accept(true); h.coordinator.flush();
  assert.deepEqual(h.sent, [[80, 25]]);
});
test('dispose cancels scheduled resize', () => {
  const h = harness(); h.coordinator.request(); h.coordinator.dispose(); h.tick();
  h.coordinator.request(); h.coordinator.flush(); assert.equal(h.applied.length, 0);
});
function runtimeHarness() {
  const timers = new Map(); let id = 0, instance;
  class FakeTerminal {
    constructor(options) {
      this.options = options; this.cols = 80; this.rows = 25;
      this.buffer = {active: {type: 'normal', viewportY: 0, baseY: 0}};
      this.writes = []; this.callbacks = []; this.scrolls = 0;
      instance = this;
    }
    loadAddon() {} open() {} resize(c, r) { this.cols = c; this.rows = r; }
    write(data, callback) { this.writes.push(data); this.callbacks.push(callback); }
    scrollToBottom() { this.scrolls++; } focus() {} clear() {} dispose() {} onData() {}
    complete() { this.callbacks.shift()(); }
  }
  const panel = {textContent: '', hidden: true};
  const window = {
    Terminal: FakeTerminal, FitAddon: class {proposeDimensions() {return {cols: 80, rows: 25};}},
    MeshTerminalResize: policy,
    AndroidTerminal: {onTerminalResize() {}, onHtmlReady() {}, sendInput() {}},
    setTimeout: callback => {timers.set(++id, callback); return id;},
    clearTimeout: key => timers.delete(key), addEventListener() {}, removeEventListener() {}
  };
  const document = {getElementById: key => key === 'terminal-error' ? panel : {clientWidth: 800, clientHeight: 600}};
  vm.runInNewContext(fs.readFileSync(path.join(assets, 'terminal-runtime.js'), 'utf8'), {window, document, console: {error() {}}});
  return {window, terminal: instance, panel};
}
test('runtime serializes writes until the parser callback', () => {
  const h = runtimeHarness(); h.window.writeTerminalData('a'); h.window.writeTerminalData('b'); h.window.writeTerminalData('c');
  assert.deepEqual(h.terminal.writes, ['a']); h.terminal.complete();
  assert.deepEqual(h.terminal.writes, ['a', 'bc']); h.terminal.complete();
});
test('alternate screen output never forces scroll', () => {
  const h = runtimeHarness(); h.terminal.buffer.active.type = 'alternate';
  h.window.writeTerminalData('nano'); h.terminal.complete(); assert.equal(h.terminal.scrolls, 0);
});
test('reading scrollback is not interrupted by output', () => {
  const h = runtimeHarness(); h.terminal.buffer.active.baseY = 50;
  h.window.writeTerminalData('output'); h.terminal.complete(); assert.equal(h.terminal.scrolls, 0);
});
test('normal output at the bottom follows only after processing', () => {
  const h = runtimeHarness(); h.window.writeTerminalData('output');
  assert.equal(h.terminal.scrolls, 0); h.terminal.complete(); assert.equal(h.terminal.scrolls, 1);
});
test('switching buffer while a write is pending does not force scroll', () => {
  const h = runtimeHarness(); h.window.writeTerminalData('enter alternate');
  h.terminal.buffer.active = {type: 'alternate', viewportY: 0, baseY: 0};
  h.terminal.complete(); assert.equal(h.terminal.scrolls, 0);
});
test('queue overflow is visible rather than silently dropping stream data', () => {
  const h = runtimeHarness(); h.window.writeTerminalData('x'.repeat(2 * 1024 * 1024 + 1));
  assert.equal(h.panel.hidden, false); assert.equal(h.terminal.writes.length, 0);
});
console.log(passed + ' tests passed');
