'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/assets/terminal/terminal-windows-pty.js'), 'utf8');
function environment(bridge) {
  function Terminal(pty) { this.options = pty === undefined ? {} : { windowsPty: pty }; }
  Terminal.prototype.open = function (...args) { this.openArgs = args; this.openThis = this; return 42; };
  const original = Terminal.prototype.open;
  const context = { window: { Terminal } };
  if (bridge !== undefined) context.window.WindowsTerminalResizeDiagnostics = bridge;
  vm.runInNewContext(source, context);
  return { Terminal, original, context };
}
for (const bridge of [undefined, null, {}, { snapshot: true }]) {
  const env = environment(bridge);
  assert.equal(env.Terminal.prototype.open, env.original);
}
const env = environment({ snapshot() {} });
const term = new env.Terminal({});
assert.equal(term.open('container', 'extra'), 42);
assert.equal(term.options.windowsPty.buildNumber, 0);
assert.equal(term.options.windowsPty.backend, undefined);
assert.equal(term.openThis, term);
assert.deepEqual(term.openArgs, ['container', 'extra']);
const wrapped = env.Terminal.prototype.open;
vm.runInNewContext(source, env.context);
assert.equal(env.Terminal.prototype.open, wrapped);
for (const pty of [{ backend: 'conpty', buildNumber: 26300 }, { backend: 'winpty' }, { buildNumber: 19045 }]) {
  const known = new env.Terminal(pty);
  known.open();
  assert.equal(known.options.windowsPty, pty);
}
const unsupported = new env.Terminal();
assert.equal(unsupported.open(), 42);
assert.equal(unsupported.options.windowsPty, undefined);
const originalOptions = { custom: 'retained' };
const extra = new env.Terminal(originalOptions);
extra.open();
assert.equal(extra.options.windowsPty.custom, 'retained');
assert.equal(originalOptions.buildNumber, undefined);
console.log('Windows PTY routing and open delegation assertions passed');
