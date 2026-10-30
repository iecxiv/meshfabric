(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.MeshTerminalKeys = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var ESC = String.fromCharCode(27);
  var simple = { enter: 13, backspace: 8, tab: 9, esc: 27 };
  var cursor = { up: 'A', down: 'B', right: 'C', left: 'D', home: 'H', end: 'F' };
  var tilde = { insert: '2', delete: '3', pageup: '5', pagedown: '6' };
  function owns(object, key) { return Object.prototype.hasOwnProperty.call(object, key); }
  function encode(name, application) {
    if (typeof name !== 'string') return null;
    if (owns(simple, name)) return String.fromCharCode(simple[name]);
    if (owns(cursor, name)) return ESC + (application === true ? 'O' : '[') + cursor[name];
    if (owns(tilde, name)) return ESC + '[' + tilde[name] + '~';
    if (name.length === 6 && name.slice(0, 5) === 'ctrl-') {
      var code = name.charCodeAt(5);
      if (code >= 97 && code <= 122) return String.fromCharCode(code - 96);
    }
    return null;
  }
  return { encode: encode };
}));
