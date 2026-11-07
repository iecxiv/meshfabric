const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/assets/terminal/server-console.js'), 'utf8');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate) {
    const end = Date.now() + 2000;
    while (!predicate()) { if (Date.now() > end) throw new Error('condition timed out'); await pause(5); }
}
function harness(width = 320, height = 240) {
    let resolveFonts;
    const fonts = new Promise(resolve => { resolveFonts = resolve; });
    const container = {clientWidth:width, clientHeight:height, hidden:false};
    const error = {hidden:true, textContent:''};
    const instances = [];
    class Terminal {
        constructor(options) { this.options=options; this.cols=80; this.rows=24; this.buffer={active:{viewportY:0,baseY:0}}; this.writes=[]; this.callbacks=[]; this.throwWrite=false; instances.push(this); }
        loadAddon(addon) { addon.activate(this); }
        open() {}
        resize(cols,rows) { this.cols=cols; this.rows=rows; }
        reset() {}
        write(text,callback) { if(this.throwWrite) throw new Error('write failed'); this.writes.push(text); this.callbacks.push(callback); }
        complete() { const callback=this.callbacks.shift(); assert.ok(callback); callback(); }
        scrollToBottom() {}
        scrollToLine() {}
        refresh() {}
        dispose() {}
    }
    class FitAddon { activate(term) { this.term=term; } proposeDimensions() { return {cols:40,rows:12}; } }
    const window = {Terminal,FitAddon:{FitAddon},requestAnimationFrame:callback=>setTimeout(callback,0),cancelAnimationFrame:clearTimeout,addEventListener(){},removeEventListener(){},ResizeObserver:class { observe(){} disconnect(){} }};
    const document = {getElementById:id=>id==='terminal'?container:error,documentElement:{lang:'en'},fonts:{ready:fonts}};
    vm.runInNewContext(source,{window,document});
    return {window,container,instances,resolveFonts,dispose:()=>window.serverConsoleDispose()};
}
const data = text => ({text,title:'Console',empty:'Empty',font:13,language:'en'});

test('waits for fonts and acknowledges only after write callback', async t => {
    const h=harness(); t.after(h.dispose);
    assert.equal(h.window.serverConsoleReady,false);
    assert.equal(h.window.serverConsoleRender(data('answer'),1),true);
    assert.equal(h.instances.length,0);
    h.resolveFonts();
    await until(()=>h.instances.length===1 && h.instances[0].writes.length===1);
    assert.equal(h.window.serverConsoleRendered,0);
    h.instances[0].complete();
    assert.equal(h.window.serverConsoleRendered,1);
});
test('does not declare a zero-sized container ready', async t => {
    const h=harness(0,0); t.after(h.dispose); h.resolveFonts();
    await pause(25);
    assert.equal(h.window.serverConsoleReady,false);
    h.container.clientWidth=320; h.container.clientHeight=240;
    await until(()=>h.window.serverConsoleReady);
});
test('retains only the latest pending snapshot while writing', async t => {
    const h=harness(); t.after(h.dispose); h.resolveFonts();
    await until(()=>h.window.serverConsoleReady);
    h.window.serverConsoleRender(data('first'),1);
    await until(()=>h.instances[0].writes.length===1);
    h.window.serverConsoleRender(data('discarded'),2);
    h.window.serverConsoleRender(data('latest'),3);
    assert.equal(h.instances[0].writes.length,1);
    h.instances[0].complete();
    await until(()=>h.instances[0].writes.length===2);
    assert.ok(h.instances[0].writes[1].endsWith('latest'));
    h.instances[0].complete();
    assert.equal(h.window.serverConsoleRendered,3);
});
test('reports write failures without claiming consumption', async t => {
    const h=harness(); t.after(h.dispose); h.resolveFonts();
    await until(()=>h.window.serverConsoleReady);
    h.instances[0].throwWrite=true;
    h.window.serverConsoleRender(data('answer'),1);
    await until(()=>h.window.serverConsoleFailed);
    assert.equal(h.window.serverConsoleReady,false);
    assert.equal(h.window.serverConsoleRendered,0);
});
