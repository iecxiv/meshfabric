(function (root) {
  'use strict';
  if (!root || !root.WindowsTerminalResizeDiagnostics ||
      typeof root.WindowsTerminalResizeDiagnostics.snapshot !== 'function') return;
  var proto = root.Terminal && root.Terminal.prototype;
  if (!proto || typeof proto.open !== 'function' || proto.__meshWindowsPtyCompatibility) return;
  var open = proto.open;
  proto.open = function () {
    var pty = this.options && this.options.windowsPty;
    if (pty && typeof pty === 'object' &&
        pty.backend === undefined && pty.buildNumber === undefined) {
      // Zero is an unknown-build sentinel, not the remote Windows version.
      // It enables xterm's Windows row-growth path without guessing a PTY backend.
      this.options.windowsPty = Object.assign({}, pty, { buildNumber: 0 });
    }
    return open.apply(this, arguments);
  };
  Object.defineProperty(proto, '__meshWindowsPtyCompatibility', { value: true });
}(typeof window === 'object' ? window : null));
