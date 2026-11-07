const test = require('node:test');
const assert = require('node:assert/strict');
const input = require('../../app/src/main/assets/terminal/server-console-input.js');
function active() { const editor = input.create(); editor.setEnabled(true); return editor; }

test('disabled editor cannot edit or prepare a command', () => {
    const editor = input.create();
    assert.equal(editor.insert('help'), false);
    assert.equal(editor.key('\r'), null);
    assert.equal(editor.snapshot().text, '');
});
test('Enter prepares exact text once and keeps the draft pending', () => {
    const editor = active(); editor.insert('  help  ');
    assert.deepEqual(editor.key('\r'), {id:1, text:'  help  '});
    assert.equal(editor.key('\r'), null);
    assert.equal(editor.insert('x'), false);
    assert.equal(editor.snapshot().text, '  help  ');
    assert.equal(editor.snapshot().pending, true);
});
test('reject keeps the draft and stale or duplicate acknowledgements do nothing', () => {
    const editor = active(); editor.insert('help'); const command = editor.prepare();
    assert.equal(editor.resolve(command.id + 1, true), false);
    assert.equal(editor.resolve(command.id, 'yes'), false);
    assert.equal(editor.resolve(command.id, false), true);
    assert.equal(editor.snapshot().text, 'help');
    assert.equal(editor.resolve(command.id, true), false);
    assert.equal(editor.prepare().id, 2);
});
test('accept clears the draft and requires explicit availability before another send', () => {
    const editor = active(); editor.insert('help'); const command = editor.prepare();
    assert.equal(editor.resolve(command.id, true), true);
    assert.equal(editor.snapshot().text, '');
    assert.equal(editor.insert('next'), false);
    editor.setEnabled(true); assert.equal(editor.insert('next'), true);
    assert.deepEqual(editor.prepare(), {id:2, text:'next'});
});
test('pasting multiline or a single newline never prepares a command', () => {
    const editor = active();
    assert.equal(editor.insert('help\r\ninfo\nversions\tstatus'), true);
    assert.equal(editor.insert('\r'), true);
    assert.equal(editor.snapshot().text, 'help info versions status ');
    assert.equal(editor.snapshot().pending, false);
});
test('a keyboard batch containing newlines is inserted without automatic submission', () => {
    const editor = active(); assert.equal(editor.key('help\r\ninfo'), null);
    assert.equal(editor.snapshot().text, 'help info');
    assert.equal(editor.snapshot().pending, false);
});
test('overlength insert is rejected entirely without truncating the command', () => {
    const editor = active(); editor.insert('a'.repeat(999));
    assert.equal(editor.insert('bc'), false);
    assert.equal(editor.snapshot().text.length, 999);
    assert.equal(editor.insert('b'), true);
    assert.equal(editor.snapshot().text.length, 1000);
    assert.equal(editor.insert('x'.repeat(1001)), false);
});
test('cursor editing preserves Unicode code points', () => {
    const editor = active(); editor.insert('a😀b'); editor.move(-1);
    assert.equal(editor.snapshot().before, 'a😀');
    editor.backspace(); assert.equal(editor.snapshot().text, 'ab');
    editor.insert('ñ'); assert.equal(editor.snapshot().text, 'añb');
    editor.remove(); assert.equal(editor.snapshot().text, 'añ');
    editor.home(); editor.insert('中'); editor.end(); editor.insert('!');
    assert.equal(editor.snapshot().text, '中añ!');
});
test('UTF-16 limit is compatible with native String.length', () => {
    const editor = active(); assert.equal(editor.insert('😀'.repeat(500)), true);
    assert.equal(editor.insert('x'), false);
    editor.backspace(); assert.equal(editor.snapshot().text.length, 998);
    assert.equal(editor.insert('😀'), true);
});
test('blank commands are not prepared and control sequences do not become input', () => {
    const editor = active(); editor.insert('  '); assert.equal(editor.prepare(), null);
    editor.clear(); editor.key('\x1b[A'); assert.equal(editor.snapshot().text, '');
    editor.insert('a\x00b\x7fc\x9fd'); assert.equal(editor.snapshot().text, 'abcd');
});
test('receiving availability updates and reading snapshots preserves the draft', () => {
    const editor = active(); editor.insert('partially typed'); editor.move(-5);
    const before = editor.snapshot(); editor.setEnabled(false); editor.snapshot(); editor.setEnabled(true);
    assert.deepEqual(editor.snapshot(), before);
});
test('dispose erases sensitive draft and ignores late callbacks', () => {
    const editor = active(); editor.insert('temporary'); const command = editor.prepare(); editor.dispose();
    assert.equal(editor.resolve(command.id, true), false);
    editor.setEnabled(true); assert.equal(editor.insert('next'), false);
    assert.equal(editor.prepare(), null);
    assert.equal(editor.snapshot().text, ''); assert.equal(editor.snapshot().disposed, true);
});
