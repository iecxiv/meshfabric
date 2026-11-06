/* Dedicated read-only renderer. No command bridge or network channel. */
(function () {
    'use strict';
    window.serverConsoleReady = false;
    var term, fit, pending = null, writing = false;
    function failed() {
        window.serverConsoleReady = false;
        document.getElementById('terminal').hidden = true;
        var error = document.getElementById('error');
        error.hidden = false;
        error.textContent = 'Console renderer unavailable.';
    }
    function resize() { try { if (fit) fit.fit(); } catch (_) { failed(); } }
    function pump() {
        if (writing || !pending || !window.serverConsoleReady) return;
        var data = pending;
        pending = null;
        writing = true;
        try {
            var viewport = term.buffer.active.viewportY;
            var bottom = viewport >= term.buffer.active.baseY;
            document.documentElement.lang = data.language === 'es' ? 'es' : 'en';
            term.options.fontSize = data.font;
            resize();
            term.reset();
            var body = data.text || data.empty;
            term.write('\x1b[36m' + data.title + '\x1b[0m\r\n\r\n' + body, function () {
                if (bottom) term.scrollToBottom();
                else term.scrollToLine(Math.min(viewport, term.buffer.active.baseY));
                writing = false;
                if (pending) window.requestAnimationFrame(pump);
            });
        } catch (_) { writing = false; failed(); }
    }
    try {
        term = new Terminal({convertEol:true,disableStdin:true,cursorBlink:false,scrollback:2000,fontSize:13,screenReaderMode:true,theme:{background:'#0d1117',foreground:'#c9d1d9',cursor:'#0d1117',selectionBackground:'#264f78'}});
        fit = new FitAddon.FitAddon();
        term.loadAddon(fit);
        term.open(document.getElementById('terminal'));
        window.serverConsoleRender = function (data) {
            if (!window.serverConsoleReady || !data || typeof data.text !== 'string' || data.text.length > 64000 || typeof data.title !== 'string' || typeof data.empty !== 'string' || data.title.length > 200 || data.empty.length > 200 || !Number.isInteger(data.font) || data.font < 10 || data.font > 20) return false;
            pending = data;
            pump();
            return true;
        };
        window.serverConsoleReady = true;
        window.addEventListener('resize', resize);
        if (window.ResizeObserver) new ResizeObserver(resize).observe(document.getElementById('terminal'));
        window.requestAnimationFrame(resize);
    } catch (_) { failed(); }
})();
