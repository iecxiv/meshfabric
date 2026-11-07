/* Local command editor. No transport, DOM access, persistence or automatic execution. */
(function (root, factory) {
    'use strict';
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ServerConsoleInput = factory();
})(typeof window !== 'undefined' ? window : globalThis, function () {
    'use strict';
    function create() {
        var chars = [], cursor = 0, enabled = false, pending = null, serial = 0, disposed = false;
        function editable() { return !disposed && enabled && pending === null; }
        function snapshot() {
            return Object.freeze({text:chars.join(''), before:chars.slice(0, cursor).join(''),
                after:chars.slice(cursor).join(''), cursor:cursor, enabled:enabled && !disposed,
                pending:pending !== null, disposed:disposed});
        }
        function insert(text) {
            if (!editable() || typeof text !== 'string' || text.length > 1000) return false;
            var clean = text.replace(/\r\n|[\r\n\t\u0085\u2028\u2029]/g, ' ')
                .replace(/[\x00-\x1f\x7f-\x9f]/g, '');
            var added = Array.from(clean), next = chars.slice(0, cursor).concat(added, chars.slice(cursor));
            if (next.join('').length > 1000) return false;
            chars = next; cursor += added.length;
            return true;
        }
        function move(delta) {
            if (!editable() || !Number.isSafeInteger(delta)) return false;
            cursor = Math.max(0, Math.min(chars.length, cursor + delta));
            return true;
        }
        function home() { if (!editable()) return false; cursor = 0; return true; }
        function end() { if (!editable()) return false; cursor = chars.length; return true; }
        function backspace() {
            if (!editable() || cursor === 0) return false;
            chars.splice(--cursor, 1); return true;
        }
        function remove() {
            if (!editable() || cursor >= chars.length) return false;
            chars.splice(cursor, 1); return true;
        }
        function clear() { if (!editable()) return false; chars = []; cursor = 0; return true; }
        function prepare() {
            if (!editable() || chars.join('').trim() === '' || serial >= 2147483647) return null;
            pending = Object.freeze({id:++serial, text:chars.join('')});
            return pending;
        }
        function resolve(id, accepted) {
            if (disposed || !pending || pending.id !== id || typeof accepted !== 'boolean') return false;
            pending = null;
            if (accepted) { chars = []; cursor = 0; enabled = false; }
            return true;
        }
        function setEnabled(value) { if (!disposed) enabled = value === true; }
        function dispose() { disposed = true; enabled = false; pending = null; chars = []; cursor = 0; }
        function key(data) {
            if (!editable() || typeof data !== 'string') return null;
            if (data === '\r' || data === '\n') return prepare();
            if (data === '\x7f' || data === '\b') backspace();
            else if (data === '\x1b[D') move(-1);
            else if (data === '\x1b[C') move(1);
            else if (data === '\x1b[H' || data === '\x1b[1~') home();
            else if (data === '\x1b[F' || data === '\x1b[4~') end();
            else if (data === '\x1b[3~') remove();
            else if (data === '\x03' || data === '\x15') clear();
            else if (data.indexOf('\x1b') === -1) insert(data);
            return null;
        }
        return Object.freeze({snapshot:snapshot, insert:insert, move:move, home:home, end:end,
            backspace:backspace, remove:remove, clear:clear, prepare:prepare, resolve:resolve,
            setEnabled:setEnabled, key:key, dispose:dispose});
    }
    return Object.freeze({create:create});
});
