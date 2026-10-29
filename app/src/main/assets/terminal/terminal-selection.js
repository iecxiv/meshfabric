(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.MeshTerminalSelection = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var NL = String.fromCharCode(10);
  function capture(buffer, maxLines, maxChars) {
    maxLines = maxLines === undefined ? 2000 : maxLines;
    maxChars = maxChars === undefined ? 200000 : maxChars;
    if (!buffer || typeof buffer.getLine !== 'function' || !Number.isInteger(buffer.length) ||
        !Number.isInteger(maxLines) || maxLines < 1 || !Number.isInteger(maxChars) || maxChars < 2) {
      throw new RangeError('Invalid terminal snapshot bounds');
    }
    var end = buffer.length;
    while (end > 0) {
      var tail = buffer.getLine(end - 1);
      if (tail && tail.translateToString(true) !== '') break;
      end--;
    }
    var start = Math.max(0, end - maxLines), pieces = [], used = 0, truncated = start > 0;
    for (var i = start; i < end; i++) {
      var line = buffer.getLine(i), next = i + 1 < end ? buffer.getLine(i + 1) : null;
      var piece = (i > start && !(line && line.isWrapped) ? NL : '') +
        (line ? line.translateToString(!(next && next.isWrapped)) : '');
      var remaining = maxChars - used;
      if (piece.length > remaining) {
        var cut = remaining;
        var before = piece.charCodeAt(cut - 1), after = piece.charCodeAt(cut);
        if (cut > 0 && cut < piece.length && before >= 0xD800 && before <= 0xDBFF &&
            after >= 0xDC00 && after <= 0xDFFF) cut--;
        pieces.push(piece.slice(0, cut));
        truncated = true;
        break;
      }
      pieces.push(piece);
      used += piece.length;
    }
    return { text: pieces.join(''), truncated: truncated };
  }
  return { capture: capture };
}));
