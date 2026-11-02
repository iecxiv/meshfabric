(function (root) {
  'use strict';
  if (!root || !root.WindowsTerminalResizeDiagnostics ||
      typeof root.WindowsTerminalResizeDiagnostics.snapshot !== 'function') return;
  var proto = root.Terminal && root.Terminal.prototype;
  if (!proto || typeof proto.open !== 'function' || proto.__meshWindowsPtyCompatibility) return;
  var states = new WeakMap();
  function state(term) {
    var value = states.get(term);
    if (!value) { value = { anchor: false, element: null, cancel: null }; states.set(term, value); }
    return value;
  }
  function normal(term) { return term.buffer && term.buffer.active.type === 'normal'; }
  function focused(term) {
    var element = term.element;
    var doc = element && element.ownerDocument;
    return !!(doc && doc.activeElement && element.contains(doc.activeElement));
  }
  function align(term) {
    if (!state(term).anchor || !normal(term) || typeof term.scrollToLine !== 'function') return;
    var buffer = term.buffer.active;
    term.scrollToLine(Math.max(0, buffer.baseY + buffer.cursorY - term.rows + 1));
  }
  var events = ['pointerdown', 'touchmove', 'wheel'];
  var open = proto.open;
  proto.open = function () {
    var pty = this.options && this.options.windowsPty;
    if (pty && typeof pty === 'object' &&
        pty.backend === undefined && pty.buildNumber === undefined) {
      // Zero is an unknown-build sentinel, not the remote Windows version.
      // It enables xterm's Windows row-growth path without guessing a PTY backend.
      this.options.windowsPty = Object.assign({}, pty, { buildNumber: 0 });
    }
    var result = open.apply(this, arguments);
    var value = state(this), element = this.element;
    if (element && !value.element) {
      value.element = element;
      value.cancel = function () { value.anchor = false; };
      events.forEach(function (event) { element.addEventListener(event, value.cancel, { passive: true }); });
    }
    return result;
  };
  if (typeof proto.resize === 'function') {
    var resize = proto.resize;
    proto.resize = function (cols, rows) {
      var value = state(this), buffer = this.buffer && this.buffer.active;
      var following = normal(this) && (value.anchor || buffer.viewportY >= buffer.baseY);
      // A focused, same-width vertical shrink is the IME-open signal available here.
      var keyboardOpen = normal(this) && cols === this.cols && rows < this.rows && focused(this);
      var result = resize.apply(this, arguments);
      value.anchor = !!(following || keyboardOpen) && normal(this);
      align(this);
      return result;
    };
  }
  if (typeof proto.write === 'function') {
    var write = proto.write;
    proto.write = function (data, callback) {
      var term = this;
      return write.call(term, data, function () {
        try {
          if (typeof callback === 'function') return callback.apply(this, arguments);
        } finally {
          // Run after runtime's callback, which may itself scroll to the bottom.
          align(term);
        }
      });
    };
  }
  if (typeof proto.dispose === 'function') {
    var dispose = proto.dispose;
    proto.dispose = function () {
      var value = states.get(this);
      if (value) {
        value.anchor = false;
        if (value.element) events.forEach(function (event) { value.element.removeEventListener(event, value.cancel); });
        states.delete(this);
      }
      return dispose.apply(this, arguments);
    };
  }
  Object.defineProperty(proto, '__meshWindowsPtyCompatibility', { value: true });
}(typeof window === 'object' ? window : null));
