/* Local xterm renderer. Only complete commands cross the restricted Android bridge. */
(function () {
    'use strict';
    var term = null, fit = null, pending = null, current = null, writing = false;
    var disposed = false, frame = null, observer = null, dirty = false, follow = false;
    var available = false, dataListener = null;
    var container = document.getElementById('terminal');
    var editor = window.ServerConsoleInput ? window.ServerConsoleInput.create() : null;
    var bridge = window.AndroidServerConsole;
    var interactive = !!(editor && bridge && typeof bridge.submit === 'function');
    window.serverConsoleReady = false;
    window.serverConsoleFailed = false;
    window.serverConsoleRendered = 0;
    function failed() {
        if (disposed) return;
        window.serverConsoleFailed = true; window.serverConsoleReady = false;
        available = false; pending = null;
        if (editor) editor.dispose();
        container.hidden = true;
        var error = document.getElementById('error');
        error.hidden = false; error.textContent = 'Console renderer unavailable.';
    }
    function schedule() {
        if (disposed || window.serverConsoleFailed || frame !== null) return;
        frame = window.requestAnimationFrame(function () { frame = null; pump(); });
    }
    function measurable() {
        if (!term || !fit || container.clientWidth <= 0 || container.clientHeight <= 0) return false;
        var dims = fit.proposeDimensions();
        if (!dims || !Number.isFinite(dims.cols) || !Number.isFinite(dims.rows) || dims.cols < 2 || dims.rows < 1) return false;
        if (term.cols !== dims.cols || term.rows !== dims.rows) { term.resize(dims.cols, dims.rows); dirty = true; }
        return true;
    }
    function pump() {
        if (disposed || window.serverConsoleFailed || writing || !term) return;
        try {
            if (pending && term.options.fontSize !== pending.data.font) {
                term.options.fontSize = pending.data.font; schedule(); return;
            }
            if (!measurable()) { schedule(); return; }
            window.serverConsoleReady = true;
            if (pending) { current = pending; pending = null; dirty = true; }
            if (!current || !dirty) return;
            dirty = false;
            var next = current, data = next.data;
            var viewport = term.buffer.active.viewportY;
            var bottom = follow || viewport >= term.buffer.active.baseY;
            follow = false;
            document.documentElement.lang = data.language === 'es' ? 'es' : 'en';
            var state = editor && editor.snapshot();
            var prompt = interactive ? '\r\n\r\n> ' + state.before + '\x1b7' + state.after + '\x1b8' : '';
            var cursor = interactive && state.enabled && !state.pending;
            writing = true;
            term.options.cursorBlink = !!cursor;
            term.reset();
            term.write('\x1b[36m' + data.title + '\x1b[0m\r\n\r\n' + (data.text || data.empty) + prompt + (cursor ? '\x1b[?25h' : '\x1b[?25l'), function () {
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
    function changed() { dirty = true; follow = true; schedule(); }
    function input(data) {
        if (disposed || window.serverConsoleFailed || !interactive || !available) return;
        var command = editor.key(data);
        changed();
        if (!command) return;
        try { bridge.submit(command.id, command.text); }
        catch (_) { editor.resolve(command.id, false); available = false; editor.setEnabled(false); changed(); }
    }
    window.serverConsoleSetInputEnabled = function (value) {
        if (disposed || window.serverConsoleFailed) return false;
        available = interactive && value === true;
        if (editor) editor.setEnabled(available);
        dirty = true; schedule(); return true;
    };
    window.serverConsoleSubmitted = function (id, accepted) {
        if (disposed || window.serverConsoleFailed || !editor || !editor.resolve(id, accepted)) return false;
        if (!accepted) editor.setEnabled(available);
        changed(); return true;
    };
    window.serverConsolePaste = function (text) {
        if (disposed || window.serverConsoleFailed || !interactive || !available) return false;
        var accepted = editor.insert(text);
        if (accepted) changed();
        return accepted;
    };
    function paste(event) {
        event.preventDefault(); event.stopPropagation();
        if (event.clipboardData) window.serverConsolePaste(event.clipboardData.getData('text/plain'));
    }
    window.serverConsoleFocus = function () {
        if (disposed || window.serverConsoleFailed || !term || !interactive || !available) return false;
        term.focus(); follow = true; dirty = true; schedule(); return true;
    };
    window.serverConsoleRender = function (data, id) {
        if (disposed || window.serverConsoleFailed || !Number.isSafeInteger(id) || id < 1 || !data || typeof data.text !== 'string' || data.text.length > 64000 || typeof data.title !== 'string' || typeof data.empty !== 'string' || data.title.length > 200 || data.empty.length > 200 || !Number.isInteger(data.font) || data.font < 10 || data.font > 20) return false;
        pending = {data:data, id:id}; schedule(); return true;
    };
    function initialize() {
        if (disposed) return;
        try {
            if (bridge && !editor) throw new Error('Input assets unavailable');
            var FitClass = typeof window.FitAddon === 'function' ? window.FitAddon : window.FitAddon && window.FitAddon.FitAddon;
            if (!window.Terminal || !FitClass) throw new Error('Assets unavailable');
            term = new window.Terminal({cols:80,rows:24,convertEol:true,disableStdin:!interactive,cursorBlink:false,scrollback:2000,fontSize:13,screenReaderMode:true,theme:{background:'#0d1117',foreground:'#c9d1d9',cursor:'#c9d1d9',selectionBackground:'#264f78'}});
            fit = new FitClass(); term.loadAddon(fit); term.open(container);
            if (interactive) {
                dataListener = term.onData(input);
                container.addEventListener('paste', paste, true);
            }
            if (typeof window.ResizeObserver === 'function') { observer = new window.ResizeObserver(schedule); observer.observe(container); }
            window.addEventListener('resize', schedule); schedule();
        } catch (_) { failed(); }
    }
    window.serverConsoleDispose = function () {
        disposed = true; available = false; window.serverConsoleReady = false;
        pending = null; current = null;
        if (editor) editor.dispose();
        if (frame !== null) window.cancelAnimationFrame(frame);
        if (observer) observer.disconnect();
        if (dataListener) dataListener.dispose();
        if (interactive) container.removeEventListener('paste', paste, true);
        window.removeEventListener('resize', schedule);
        if (term) term.dispose();
    };
    window.addEventListener('pagehide', window.serverConsoleDispose, {once:true});
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(initialize, failed);
    else initialize();
})();
