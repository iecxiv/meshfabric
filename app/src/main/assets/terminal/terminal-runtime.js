(function () {
  'use strict';
  var term = null, fit = null, resize = null, observer = null;
  var queue = [], queuedChars = 0, writing = false, disposed = false, failed = false;
  var fontSize = 11, readySent = false, readyTimer = null, readyAttempts = 0;
  var MAX_PENDING_CHARS = 2 * 1024 * 1024;

  function fail(error) {
    if (failed || disposed) return;
    failed = true;
    console.error('MeshTerminal runtime failure: ' + (error && error.name ? error.name : 'Error'));
    var panel = document.getElementById('terminal-error');
    panel.textContent = 'Terminal unavailable / Terminal no disponible. Return and reopen the terminal / Vuelve y abre la terminal de nuevo.';
    panel.hidden = false;
  }

  function notifyReady() {
    if (readySent || failed || disposed) return;
    var bridge = window.AndroidTerminal;
    if (bridge && typeof bridge.onHtmlReady === 'function') {
      bridge.onHtmlReady();
      readySent = true;
    } else if (++readyAttempts <= 50) {
      readyTimer = window.setTimeout(notifyReady, 100);
    } else {
      fail(new Error('Bridge unavailable'));
    }
  }

  function drain() {
    if (!term || writing || failed || disposed) return;
    try {
      if (resize) resize.flush();
      if (!queue.length) return;
      var chunks = [], tokens = [], length = 0;
      while (queue.length && length < 65536) {
        var chunk = queue.shift();
        chunks.push(chunk.data);
        length += chunk.data.length;
        if (chunk.token !== null) tokens.push(chunk.token);
      }
      var buffer = term.buffer.active;
      var follow = buffer.type === 'normal' && buffer.viewportY >= buffer.baseY;
      writing = true;
      term.write(chunks.join(''), function () {
        writing = false;
        queuedChars -= length;
        if (disposed || failed) return;
        try {
          if (follow && term.buffer.active === buffer && buffer.type === 'normal') term.scrollToBottom();
          if (tokens.length) {
            var bridge = window.AndroidTerminal;
            if (!bridge || typeof bridge.onOutputConsumed !== 'function') throw new Error('ACK bridge unavailable');
            tokens.forEach(function (token) { bridge.onOutputConsumed(token); });
          }
          drain();
        } catch (error) { fail(error); }
      });
    } catch (error) { fail(error); }
  }

  function enqueueTerminalData(data, token) {
    if (disposed || failed || typeof data !== 'string' || !data.length) return false;
    if (queuedChars + data.length > MAX_PENDING_CHARS) {
      fail(new RangeError('Terminal output queue limit exceeded'));
      return false;
    }
    queue.push({ data: data, token: token });
    queuedChars += data.length;
    drain();
    return !failed;
  }
  window.writeTerminalData = function (data) { return enqueueTerminalData(data, null); };
  window.writeTerminalBatch = function (token, data) {
    if (typeof token !== 'string' || !token.length || token.length > 128) return false;
    return enqueueTerminalData(data, token);
  };
  window.showTerminalDeliveryError = function () {
    fail(new Error('Output continuity lost'));
    var panel = document.getElementById('terminal-error');
    panel.textContent = 'Output continuity lost. Disconnect and create a new terminal session. / Se ha perdido la continuidad de la salida. Desconecta y crea una nueva sesion de terminal.';
  };
  window.setFontSize = function (size) {
    var parsed = Number(size);
    if (!Number.isFinite(parsed)) return;
    fontSize = Math.max(8, Math.min(20, Math.round(parsed)));
    if (term && !failed && !disposed) {
      term.options.fontSize = fontSize;
      requestResize();
    }
  };
  window.getTerminalTextForCopy = function () {
    if (!term || disposed || failed || !window.MeshTerminalSelection) return null;
    return window.MeshTerminalSelection.capture(term.buffer.active, 2000, 200000);
  };
  window.pasteTerminalText = function (data) {
  if (!term || !readySent || disposed || failed || typeof data !== 'string' || !data.length || data.length > 16384) return false;
  for (var i = 0; i < data.length; i++) {
    var c = data.charCodeAt(i);
    if ((c < 32 && c !== 9 && c !== 10 && c !== 13) || (c >= 127 && c <= 159) ||
        (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069)) return false;
    if (c >= 0xD800 && c <= 0xDBFF) {
      var next = data.charCodeAt(++i);
      if (!(next >= 0xDC00 && next <= 0xDFFF)) return false;
    } else if (c >= 0xDC00 && c <= 0xDFFF) return false;
  }
  term.paste(data);
  return true;
};
window.getFontSize = function () { return fontSize; };
  window.clearTerminal = function () { if (term && !failed && !disposed) term.clear(); };
  window.focusTerminal = function () { if (term && !failed && !disposed) term.focus(); };
  function requestResize() {
    if (!resize || disposed || failed) return;
    try { resize.request(); } catch (error) { fail(error); }
  }
  window.fitTerminal = requestResize;

  function dispose() {
    disposed = true;
    window.clearTimeout(readyTimer);
    if (resize) resize.dispose();
    if (observer) observer.disconnect();
    window.removeEventListener('resize', requestResize);
    window.removeEventListener('orientationchange', requestResize);
    if (window.visualViewport) window.visualViewport.removeEventListener('resize', requestResize);
    if (document.fonts && document.fonts.removeEventListener) document.fonts.removeEventListener('loadingdone', requestResize);
    if (term) term.dispose();
    queue = [];
    queuedChars = 0;
  }

  function initialize() {
    if (disposed) return;
    try {
      var FitClass = typeof window.FitAddon === 'function' ? window.FitAddon : window.FitAddon && window.FitAddon.FitAddon;
      if (!window.Terminal || !FitClass || !window.MeshTerminalResize) throw new Error('Terminal assets unavailable');
      var container = document.getElementById('terminal-container');
      term = new window.Terminal({
        cols: 80, rows: 25, fontSize: fontSize,
        fontFamily: 'monospace, "Courier New", Courier, Menlo, Monaco',
        cursorBlink: true, cursorStyle: 'block', convertEol: false,
        scrollback: 5000, scrollSensitivity: 5, fastScrollSensitivity: 15,
        theme: {
          background: '#0d1117', foreground: '#c9d1d9', cursor: '#58a6ff',
          cursorAccent: '#0d1117', selectionBackground: '#1f6beb',
          black: '#484f58', red: '#ff7b72', green: '#3fb950', yellow: '#d29922',
          blue: '#58a6ff', magenta: '#bc8cff', cyan: '#39c5cf', white: '#b1bac4',
          brightBlack: '#6e7681', brightRed: '#ffa198', brightGreen: '#56d364',
          brightYellow: '#e3b341', brightBlue: '#79c0ff', brightMagenta: '#d2a8ff',
          brightCyan: '#56d4dd', brightWhite: '#f0f6fc'
        }
      });
      fit = new FitClass();
      term.loadAddon(fit);
      term.open(container);
      resize = window.MeshTerminalResize.create({
        isBusy: function () { return writing; },
        measure: function () {
          if (container.clientWidth <= 0 || container.clientHeight <= 0) return null;
          return fit.proposeDimensions();
        },
        apply: function (cols, rows) {
          if (term.cols !== cols || term.rows !== rows) term.resize(cols, rows);
        },
        notify: function (cols, rows) {
          var bridge = window.AndroidTerminal;
          if (!bridge || typeof bridge.onTerminalResize !== 'function') return false;
          bridge.onTerminalResize(cols, rows);
          notifyReady();
          return true;
        },
        schedule: function (callback, delay) {
          return window.setTimeout(function () {
            try { callback(); } catch (error) { fail(error); }
          }, delay);
        },
        cancel: function (timer) { window.clearTimeout(timer); }
      });
      term.onData(function (data) {
        if (failed || disposed) return;
        var bridge = window.AndroidTerminal;
        if (bridge && typeof bridge.sendInput === 'function') bridge.sendInput(data);
      });
      if (typeof window.ResizeObserver === 'function') {
        observer = new window.ResizeObserver(requestResize);
        observer.observe(container);
      }
      window.addEventListener('resize', requestResize);
      window.addEventListener('orientationchange', requestResize);
      if (window.visualViewport) window.visualViewport.addEventListener('resize', requestResize);
      if (document.fonts && document.fonts.addEventListener) document.fonts.addEventListener('loadingdone', requestResize);
      requestResize();
      drain();
    } catch (error) { fail(error); }
  }
  window.addEventListener('pagehide', dispose, { once: true });
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(initialize, fail);
  else initialize();
}());
