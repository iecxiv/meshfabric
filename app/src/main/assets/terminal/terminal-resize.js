(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.MeshTerminalResize = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  function create(options) {
    var timer = null, dirty = false, disposed = false, last = null;
    function flush() {
      if (disposed || !dirty || timer !== null || options.isBusy()) return false;
      var size = options.measure();
      if (!size || !Number.isInteger(size.cols) || !Number.isInteger(size.rows) ||
          size.cols < 5 || size.rows < 2) return false;
      dirty = false;
      if (last && last.cols === size.cols && last.rows === size.rows) return false;
      options.apply(size.cols, size.rows);
      if (options.notify(size.cols, size.rows) === false) {
        dirty = true;
        return false;
      }
      last = { cols: size.cols, rows: size.rows };
      return true;
    }
    return {
      request: function () {
        if (disposed) return;
        dirty = true;
        if (timer !== null) options.cancel(timer);
        timer = options.schedule(function () { timer = null; flush(); }, 120);
      },
      flush: flush,
      dispose: function () {
        disposed = true;
        if (timer !== null) options.cancel(timer);
        timer = null;
      }
    };
  }
  return { create: create };
}));
