const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const input = require('../../app/src/main/assets/terminal/server-console-input.js');
const script = fs.readFileSync(require.resolve('../../app/src/main/assets/terminal/server-console.js'), 'utf8');
function harness(withBridge=true) {
    const frames=[], writes=[], callbacks=[], commands=[], handlers={}; let terminal;
    const container={clientWidth:320,clientHeight:240,addEventListener:(k,f)=>handlers[k]=f,removeEventListener:k=>delete handlers[k]};
    class Terminal {
        constructor(options) { this.options=options; this.cols=80; this.rows=24; this.buffer={active:{viewportY:0,baseY:0}}; terminal=this; }
        loadAddon() {} open() {} reset() {} refresh() {} focus() {} dispose() {}
        resize(c,r) { this.cols=c; this.rows=r; }
        scrollToBottom() {} scrollToLine() {}
        write(text,done) { writes.push(text); callbacks.push(done); }
        onData(listener) { this.input=listener; return {dispose:()=>this.input=()=>{}}; }
    }
    const window={Terminal,ServerConsoleInput:input,FitAddon:class {proposeDimensions(){return {cols:40,rows:12};}},
        AndroidServerConsole:withBridge?{submit:(id,text)=>commands.push({id,text})}:undefined,
        requestAnimationFrame:f=>{frames.push(f);return frames.length;},cancelAnimationFrame:()=>{},addEventListener:()=>{},removeEventListener:()=>{}};
    const document={documentElement:{},getElementById:id=>id==='terminal'?container:{},fonts:{ready:{then:f=>f()}}};
    vm.runInNewContext(script,{window,document});
    function flush(){let limit=100;while(frames.length||callbacks.length){assert.ok(limit-->0);if(callbacks.length)callbacks.shift()();else frames.shift()();}}
    function render(text,id=1){assert.equal(window.serverConsoleRender({text,title:'Console',empty:'Empty',font:13,language:'en'},id),true);}
    flush(); render('initial'); flush(); window.serverConsoleSetInputEnabled(true); flush();
    return {window,terminal,commands,writes,handlers,frames,callbacks,flush,render};
}
test('output acknowledgement waits for the write callback',()=>{
    const h=harness(); h.render('updated',2); h.frames.shift()();
    assert.equal(h.window.serverConsoleRendered,1); h.flush(); assert.equal(h.window.serverConsoleRendered,2);
});
test('output and typing during a write preserve the latest draft',()=>{
    const h=harness(); h.terminal.input('hel'); h.flush(); h.render('reply',2); h.frames.shift()();
    h.terminal.input('p'); h.flush(); assert.match(h.writes.at(-1),/reply/); assert.match(h.writes.at(-1),/> help/);
    assert.equal(h.commands.length,0);
});
test('Enter dispatches once and a rejected acknowledgement preserves the draft',()=>{
    const h=harness(); h.terminal.input('help'); h.terminal.input('\r'); h.terminal.input('\r');
    assert.deepEqual(h.commands,[{id:1,text:'help'}]);
    assert.equal(h.window.serverConsoleSubmitted(1,false),true); h.flush(); assert.match(h.writes.at(-1),/> help/);
    h.terminal.input('\r'); assert.equal(h.commands.length,2); assert.equal(h.commands[1].id,2);
});
test('paste event cannot execute embedded newlines',()=>{
    const h=harness(); let prevented=false;
    h.handlers.paste({preventDefault:()=>prevented=true,stopPropagation:()=>{},clipboardData:{getData:()=> 'help\r\ninfo'}});
    h.flush(); assert.equal(prevented,true); assert.equal(h.commands.length,0); assert.match(h.writes.at(-1),/> help info/);
    h.terminal.input('\r'); assert.deepEqual(h.commands,[{id:1,text:'help info'}]);
});
test('accepted command stays disabled until Android provides availability again',()=>{
    const h=harness(); h.terminal.input('help'); h.terminal.input('\r'); h.window.serverConsoleSetInputEnabled(false);
    h.window.serverConsoleSubmitted(1,true); h.terminal.input('next'); h.terminal.input('\r'); assert.equal(h.commands.length,1);
    h.window.serverConsoleSetInputEnabled(true); h.terminal.input('next'); h.terminal.input('\r'); assert.equal(h.commands.length,2);
});
test('server output never acts as input',()=>{
    const h=harness(); h.render('help\r\n> info\r\n',2); h.flush(); assert.equal(h.commands.length,0);
});
test('oversized paste is rejected without changing the draft',()=>{
    const h=harness(); h.terminal.input('help'); assert.equal(h.window.serverConsolePaste('x'.repeat(1001)),false);
    h.flush(); assert.match(h.writes.at(-1),/> help/); assert.equal(h.commands.length,0);
});
test('disposing ignores late acknowledgements and disables input',()=>{
    const h=harness(); h.terminal.input('help'); h.terminal.input('\r'); h.window.serverConsoleDispose();
    assert.equal(h.window.serverConsoleSubmitted(1,true),false); assert.equal(h.window.serverConsolePaste('next'),false);
    h.terminal.input('\r'); assert.equal(h.commands.length,1);
});

test('without the Android bridge the existing read-only console is preserved',()=>{
    const h=harness(false); assert.equal(h.terminal.options.disableStdin,true);
    assert.equal(h.window.serverConsoleFocus(),false); assert.equal(h.window.serverConsolePaste('help'),false);
    h.render('existing output',2); h.flush(); assert.match(h.writes.at(-1),/existing output/);
    assert.equal(h.commands.length,0); assert.equal(h.window.serverConsoleRendered,2);
});
