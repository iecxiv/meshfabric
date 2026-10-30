'use strict';
const assert = require('node:assert/strict');
const {encode} = require('../../main/assets/terminal/terminal-keys.js');
const ESC = String.fromCharCode(27);
let checks = 0;
function equal(a,b) { assert.equal(a,b); checks++; }
for (const [key,code] of Object.entries({enter:13,backspace:8,tab:9,esc:27})) equal(encode(key),String.fromCharCode(code));
for (const [key,suffix] of Object.entries({up:'A',down:'B',right:'C',left:'D',home:'H',end:'F'})) {
  equal(encode(key,false),ESC+'['+suffix);
  equal(encode(key,true),ESC+'O'+suffix);
}
for (const [key,n] of Object.entries({insert:'2',delete:'3',pageup:'5',pagedown:'6'})) equal(encode(key,true),ESC+'['+n+'~');
for (let code=97;code<=122;code++) equal(encode('ctrl-'+String.fromCharCode(code)),String.fromCharCode(code-96));
for (const bad of [null,undefined,{},'', '__proto__','constructor','ctrl-aa','ctrl-A','ctrl-1','up;alert(1)']) equal(encode(bad),null);
console.log(checks+' special-key encoding checks passed');
