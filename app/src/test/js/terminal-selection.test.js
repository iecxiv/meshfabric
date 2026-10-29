'use strict';
const assert = require('node:assert/strict');
const {capture} = require('../../main/assets/terminal/terminal-selection.js');
const NL = String.fromCharCode(10), emoji = String.fromCodePoint(0x1F600);
let count = 0;
function test(name, body) { body(); count++; console.log('PASS ' + name); }
function buffer(rows) {
  const lines = rows.map(row => typeof row === 'string' ? {text: row, wrapped: false} : row);
  return Object.freeze({length: lines.length, getLine: i => lines[i] && Object.freeze({
    isWrapped: !!lines[i].wrapped,
    translateToString: trim => trim ? lines[i].text.replace(/ +$/, '') : lines[i].text
  })});
}
test('keeps c and 1', () => assert.equal(capture(buffer(['ac1b','cc11'])).text, ['ac1b','cc11'].join(NL)));
test('empty buffer is empty', () => assert.deepEqual(capture(buffer([])), {text:'',truncated:false}));
test('omits blank viewport padding', () => assert.equal(capture(buffer(['abc','','   '])).text, 'abc'));
test('preserves real blank lines', () => assert.equal(capture(buffer(['a','','b'])).text, ['a','','b'].join(NL)));
test('soft wraps do not introduce newlines', () => assert.equal(capture(buffer(['abc',{text:'def',wrapped:true}])).text, 'abcdef'));
test('preserves wrapped spaces', () => assert.equal(capture(buffer(['a  ',{text:'b',wrapped:true}])).text, 'a  b'));
test('line bound reports truncation', () => assert.deepEqual(capture(buffer(['old','c','1']),2), {text:['c','1'].join(NL),truncated:true}));
test('character bound reports truncation', () => assert.deepEqual(capture(buffer(['abcdef']),2000,3), {text:'abc',truncated:true}));
test('does not split UTF16 pair', () => assert.deepEqual(capture(buffer(['a'+emoji+'b']),2000,2), {text:'a',truncated:true}));
test('keeps complete UTF16 pair', () => assert.equal(capture(buffer(['a'+emoji+'b']),2000,3).text, 'a'+emoji));
test('captures supplied active buffer', () => assert.equal(capture(buffer(['nano'])).text, 'nano'));
test('invalid bounds rejected', () => assert.throws(() => capture(buffer(['x']),0,2),RangeError));
console.log(count + ' selection tests passed');
