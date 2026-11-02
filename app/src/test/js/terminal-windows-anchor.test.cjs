'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/assets/terminal/terminal-windows-pty.js'), 'utf8');
function environment(windows) {
  function Terminal() {
    this.options = { windowsPty: {} }; this.cols = 60; this.rows = 55;
    this.buffer = { active: { type: 'normal', baseY: 178, cursorY: 28, viewportY: 10 } };
    this.listeners = {}; this.moves = [];
    this.element = {
      ownerDocument: { activeElement: {} }, contains: () => true,
      addEventListener: (event, listener) => { this.listeners[event] = listener; },
      removeEventListener: event => { delete this.listeners[event]; }
    };
  }
  Terminal.prototype.open = function () { return 42; };
  Terminal.prototype.resize = function (cols, rows) { this.cols = cols; this.rows = rows; return 17; };
  Terminal.prototype.write = function (data, callback) { this.pending = callback; return 23; };
  Terminal.prototype.scrollToLine = function (line) {
    this.buffer.active.viewportY = Math.max(0, Math.min(this.buffer.active.baseY, line));
    this.moves.push(this.buffer.active.viewportY);
  };
  Terminal.prototype.dispose = function () { return 31; };
  const originals = { open: Terminal.prototype.open, resize: Terminal.prototype.resize, write: Terminal.prototype.write };
  const context = { window: { Terminal } };
  if (windows) context.window.WindowsTerminalResizeDiagnostics = { snapshot() {} };
  vm.runInNewContext(source, context);
  return { Terminal, originals, context };
}
const other = environment(false);
for (const method of ['open', 'resize', 'write']) assert.equal(other.Terminal.prototype[method], other.originals[method]);
const env = environment(true), term = new env.Terminal();
assert.equal(term.open(), 42);
assert.equal(term.resize(60, 29), 17);
assert.equal(term.buffer.active.viewportY, 178);
assert.equal(term.options.windowsPty.buildNumber, 0);
let callbacks = 0;
assert.equal(term.write('output', function () {
  callbacks++; this.seen = true; term.scrollToLine(178);
}), 23);
term.buffer.active.cursorY = 10;
const receiver = {};
term.pending.call(receiver);
assert.equal(callbacks, 1); assert.equal(receiver.seen, true);
assert.equal(term.buffer.active.viewportY, 160);
term.listeners.touchmove(); term.scrollToLine(40);
term.resize(60, 55);
assert.equal(term.buffer.active.viewportY, 40);
term.write('later'); term.pending();
assert.equal(term.buffer.active.viewportY, 40);
term.resize(60, 29);
assert.equal(term.buffer.active.viewportY, 160);
term.resize(60, 55);
assert.equal(term.buffer.active.viewportY, 134);
term.listeners.wheel(); term.scrollToLine(20);
term.element.ownerDocument.activeElement = null;
term.resize(60, 29);
assert.equal(term.buffer.active.viewportY, 20);
term.buffer.active.type = 'alternate';
const moves = term.moves.length;
term.resize(60, 20); term.write('screen'); term.pending();
assert.equal(term.moves.length, moves);
const installed = env.Terminal.prototype.resize;
vm.runInNewContext(source, env.context);
assert.equal(env.Terminal.prototype.resize, installed);
assert.equal(term.dispose(), 31);
assert.deepEqual(Object.keys(term.listeners), []);
console.log('Windows cursor anchoring, cancellation, callback order and scope assertions passed');
