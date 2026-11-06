/* Read-only local renderer. No command bridge or network channel. */
(function () {
    'use strict';
    var term = null, fit = null, pending = null, writing = false;
    var disposed = false, frame = null, observer = null;
    var container = document.getElementById('terminal');
    window.serverConsoleReady = false;
    window.serverConsoleFailed = false;
    window.serverConsoleRendered = 0;
    function failed() {
        if (disposed) return;
        window.serverConsoleFailed = true;
        window.serverConsoleReady = false;
        pending = null;
        container.hidden = true;
        var error = document.getElementById('error');
        error.hidden = false;
        error.textContent = 'Console renderer unavailable.';
    }
    function schedule() {
        if (disposed || window.serverConsoleFailed || frame !== null) return;
        frame = window.requestAnimationFrame(function () { frame = null; pump(); });
    }
    function measurable() {
        if (!term || !fit || container.clientWidth <= 0 || container.clientHeight <= 0) return false;
        var dims = fit.proposeDimensions();
        if (!dims || !Number.isFinite(dims.cols) || !Number.isFinite(dims.rows) || dims.cols < 2 || dims.rows < 1) return false;
        if (term.cols !== dims.cols || term.rows !== dims.rows) term.resize(dims.cols, dims.rows);
        return true;
    }
    function pump() {
        if (disposed || window.serverConsoleFailed || writing || !term) return;
        try {
            if (pending) term.options.fontSize = pending.data.font;
            if (!measurable()) { schedule(); return; }
            window.serverConsoleReady = true;
            if (!pending) return;
            var next = pending;
            pending = null;
            var viewport = term.buffer.active.viewportY;
            var bottom = viewport >= term.buffer.active.baseY;
            var data = next.data;
            document.documentElement.lang = data.language === 'es' ? 'es' : 'en';
            writing = true;
            term.reset();
            term.write('\x1b[36m' + data.title + '\x1b[0m\r\n\r\n' + (data.text || data.empty), function () {
                writing = false;
                if (disposed || window.serverConsoleFailed) return;
                try {
                    if (bottom) term.scrollToBottom();
                    else term.scrollToLine(Math.min(viewport, term.buffer.active.baseY));
                    term.refresh(0, term.rows - 1);
                    window.serverConsoleRendered = next.id;
                    schedule();
                } catch (_) { failed(); }
            });
        } catch (_) { writing = false; failed(); }
    }
    window.serverConsoleRender = function (data, id) {
        if (disposed || window.serverConsoleFailed || !Number.isSafeInteger(id) || id < 1 || !data || typeof data.text !== 'string' || data.text.length > 64000 || typeof data.title !== 'string' || typeof data.empty !== 'string' || data.title.length > 200 || data.empty.length > 200 || !Number.isInteger(data.font) || data.font < 10 || data.font > 20) return false;
        pending = {data:data, id:id};
        schedule();
        return true;
    };
    function initialize() {
        if (disposed) return;
        try {
            var FitClass = typeof window.FitAddon === 'function' ? window.FitAddon : window.FitAddon && window.FitAddon.FitAddon;
            if (!window.Terminal || !FitClass) throw new Error('Assets unavailable');
            term = new window.Terminal({cols:80,rows:24,convertEol:true,disableStdin:true,cursorBlink:false,scrollback:2000,fontSize:13,screenReaderMode:true,theme:{background:'#0d1117',foreground:'#c9d1d9',cursor:'#0d1117',selectionBackground:'#264f78'}});
            fit = new FitClass();
            term.loadAddon(fit);
            term.open(container);
            if (typeof window.ResizeObserver === 'function') { observer = new window.ResizeObserver(schedule); observer.observe(container); }
            window.addEventListener('resize', schedule);
            schedule();
        } catch (_) { failed(); }
    }
    window.serverConsoleDispose = function () {
        disposed = true;
        window.serverConsoleReady = false;
        pending = null;
        if (frame !== null) window.cancelAnimationFrame(frame);
        if (observer) observer.disconnect();
        window.removeEventListener('resize', schedule);
        if (term) term.dispose();
    };
    window.addEventListener('pagehide', window.serverConsoleDispose, {once:true});
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(initialize, failed);
    else initialize();
})();
