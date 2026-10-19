const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/it/jarviswake/WebChatActivity.kt', 'utf8');
const script = source.split('private val VOICE_CONTROL_LOCATION_SCRIPT = """')[1].split('""".trimIndent()')[0];
const fillGreetingScript = source.split('private val FILL_GREETING_SCRIPT = """')[1].split('""".trimIndent()')[0];
const submitGreetingScript = source.split('private val SUBMIT_GREETING_SCRIPT = """')[1].split('""".trimIndent()')[0];
function element(label, {hidden = false, x = 340, y = 740, text = ''} = {}) {
  return {innerText: text, value: '', disabled: false, hidden,
    getAttribute: name => name === 'aria-label' ? label : null,
    querySelectorAll:() => [], contains:other => other === this,
    getBoundingClientRect: () => ({left:x, top:y, right:x+40, bottom:y+40, width:40, height:40})};
}
function page({controls = [], headings = [], composer = null, locked = false,
    gum = async () => ({getAudioTracks: () => []})} = {}) {
  const body = {innerText:''};
  const context = vm.createContext({window: {}, navigator:{mediaDevices:{getUserMedia:gum}},
    innerWidth:400, innerHeight:800, getComputedStyle:e => ({visibility:e.hidden?'hidden':'visible',display:'block',opacity:'1'}),
    document:{body, getElementById:() => null, elementFromPoint:(x,y) =>
      controls.find(e => {const r=e.getBoundingClientRect();return x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom;}) || null,
      querySelectorAll:query => query.startsWith('h1') ? headings :
        query.startsWith('textarea') ? (composer ? [composer] : []) :
        query.startsWith('button') ? controls : []}});
  const runScript = script.replace('__JARVIS_LOCKED__', String(locked));
  return {context, inspect: () => { const value = vm.runInContext(runScript, context); return value ? JSON.parse(value) : null; }};
}
(async () => {
  const start = element('Start voice mode');
  assert.equal(page({controls:[start]}).inspect().strategy, 'semantic');
  const topVoice = element('Start voice mode', {x:340,y:100});
  const bottomVoice = element('Voice', {x:340,y:740});
  assert.equal(page({controls:[topVoice,bottomVoice],locked:true}).inspect().y, 760,
    'When keyguard is showing, search at the bottom first');
  assert.equal(page({controls:[topVoice,bottomVoice]}).inspect().y, 120,
    'When unlocked, the strongest semantic label wins');
  assert.equal(page({controls:[element('Close voice')]}).inspect().state, 'active');
  assert.equal(page({headings:[element('Voice mode',{hidden:true})]}).inspect(), null);
  assert.equal(page({controls:[element('')]}).inspect(), null, 'No composer: no geometric start');
  assert.equal(page({controls:[element('Microphone')],composer:element('')}).inspect(), null);
  const scope = {parentElement:null, contains:() => true,
    getBoundingClientRect:() => ({left:0,top:680,right:400,bottom:790,width:400,height:110})};
  const composer = Object.assign(element('',{x:20,y:710}), {parentElement:scope});
  assert.equal(page({controls:[element('')],composer}).inspect().strategy, 'composer');
  assert.equal(page({controls:[element('')],composer:element('',{text:'Draft'})}).inspect(), null);
  let release;
  const track = {readyState:'live'};
  const stream = {getAudioTracks: () => [track]};
  const p = page({controls:[start],gum:() => new Promise(resolve => release=resolve)});
  p.inspect();
  const pending = p.context.navigator.mediaDevices.getUserMedia({audio:true});
  assert.equal(p.inspect().state, 'opening', 'Slow acquisition suppresses start');
  release(stream);
  assert.equal(await pending, stream);
  assert.equal(p.inspect().state, 'active', 'Actual live track confirms Voice');
  track.readyState = 'ended';
  assert.equal(p.inspect().strategy, 'semantic', 'Ended track allows close detection');
  const again = p.context.navigator.mediaDevices.getUserMedia({audio:true});
  release(stream); track.readyState = 'live'; await again;
  assert.equal(p.inspect().state, 'active', 'Cached permission needs no native callback');
  const failure = new Error('denied'); failure.name = 'NotAllowedError';
  const denied = page({gum:async () => {throw failure;}});
  denied.inspect();
  await assert.rejects(denied.context.navigator.mediaDevices.getUserMedia({audio:true}), {name:'NotAllowedError'});
  assert.equal(denied.inspect(), null, 'Denied permission is not active Voice');
  assert.equal(denied.context.window.__jarvisWakeMedia.pending, 0);
  class HTMLTextAreaElement {}
  class HTMLInputElement {}
  Object.defineProperty(HTMLTextAreaElement.prototype, 'value', {
    get() { return this._value || ''; }, set(value) { this._value = value; }
  });
  function greetingPage() {
    const controls = [];
    let present = false, connected = true;
    const composer = Object.assign(Object.create(HTMLTextAreaElement.prototype), {
      disabled:false, innerText:'', textContent:'', isContentEditable:false, hidden:false,
      getAttribute:name => name === 'placeholder' ? 'Chiedi a ChatGPT' : null,
      closest:() => null, focus() {},
      getBoundingClientRect:() => ({left:20, top:700, right:380, bottom:750, width:360, height:50}),
      dispatchEvent:() => true
    });
    const document = {querySelectorAll:query => query.startsWith('textarea') ? (present ? [composer] : []) : controls,
      contains:element => connected && element === composer};
    const context = vm.createContext({window:{}, document, innerWidth:400, innerHeight:800,
      getComputedStyle:e => ({visibility:e.hidden?'hidden':'visible',display:'block',opacity:'1'}),
      HTMLTextAreaElement, HTMLInputElement,
      InputEvent:class { constructor(type) { this.type=type; } },
      Event:class { constructor(type) { this.type=type; } }
    });
    return {controls, composer, context, document,
      show() { present=true; }, disconnect() { connected=false; },
      fill:() => JSON.parse(vm.runInContext(fillGreetingScript, context)).result,
      submit:() => JSON.parse(vm.runInContext(submitGreetingScript, context)).result};
  }
  function control(label, options = {}) {
    let clicks = 0;
    return Object.assign(element(label, options), {type:'submit', click() { clicks++; }, count:() => clicks});
  }
  const greeting = greetingPage();
  assert.equal(greeting.fill(), 'composer_missing', 'Late composer must remain retryable');
  greeting.show();
  assert.equal(greeting.fill(), 'composer_waiting', 'Wait until the reduced Voice composer is stable');
  assert.equal(greeting.fill(), 'filled');
  assert.equal(greeting.composer.value, 'ciao jarvis');
  assert.equal(greeting.submit(), 'send_not_ready', 'No fake Enter or blind form submission');
  const close = control('Close voice');
  const hiddenSend = control('Send message', {hidden:true});
  const send = control('Send message'); send.disabled=true;
  greeting.controls.push(close, hiddenSend, send);
  for (let i=0;i<5;i++) {
    assert.equal(greeting.fill(), 'filled', 'Do not insert the text repeatedly');
    assert.equal(greeting.submit(), 'send_not_ready', 'Wait while Send is disabled');
  }
  send.disabled=false;
  assert.equal(greeting.submit(), 'submitted_button');
  assert.equal(send.count(), 1);
  assert.equal(close.count(), 0, 'Generic submit type must not select Close voice');
  assert.equal(hiddenSend.count(), 0, 'Hidden send control must be ignored');
  for (let i=0;i<10;i++) {
    assert.equal(greeting.fill(), 'submitted');
    assert.equal(greeting.submit(), 'submitted');
  }
  assert.equal(send.count(), 1, 'Late callbacks cannot duplicate the send');
  const occupied = greetingPage(); occupied.show(); occupied.composer.value='my draft';
  occupied.fill();
  assert.equal(occupied.fill(), 'composer_not_empty');
  assert.equal(occupied.composer.value, 'my draft');
  const replaced = greetingPage(); replaced.show(); replaced.fill(); replaced.fill(); replaced.disconnect();
  assert.equal(replaced.fill(), 'composer_lost');
  assert.equal(replaced.submit(), 'composer_lost');
  const ambiguous = greetingPage(); ambiguous.show(); ambiguous.fill(); ambiguous.fill();
  ambiguous.controls.push(control('Send message'), control('Send message'));
  assert.equal(ambiguous.submit(), 'send_not_ready', 'Ambiguous Send does not authorize an arbitrary click');
  const editable = greetingPage(); editable.show();
  editable.composer.isContentEditable=true;
  editable.document.execCommand = () => { editable.composer.textContent='ciao jarvis'; return false; };
  editable.fill();
  assert.equal(editable.fill(), 'filled', 'Use observed editor text even if execCommand returns false');
  const connecting = greetingPage(); connecting.show();
  const capture = {pending:1, error:null, requested:true, tracks:new Set()};
  connecting.context.window.__jarvisWakeMedia=capture;
  assert.equal(connecting.fill(), 'composer_waiting');
  assert.equal(connecting.composer.value, '', 'No greeting while audio acquisition is pending');
  capture.pending=0;
  assert.equal(connecting.fill(), 'composer_waiting', 'No send while requested audio has no live track');
  capture.tracks.add({readyState:'live'});
  connecting.fill();
  assert.equal(connecting.fill(), 'filled');
  const lateSend=control('Send message'); connecting.controls.push(lateSend);
  capture.pending=1;
  assert.equal(connecting.submit(), 'send_not_ready');
  assert.equal(lateSend.count(), 0);
  capture.pending=0;
  assert.equal(connecting.submit(), 'submitted_button');
  assert.equal(lateSend.count(), 1);
  console.log('PASS: Voice DOM/media cases; greeting delayed/disabled/hidden/ambiguous controls, draft protection and single send.');
})().catch(error => { console.error(error); process.exit(1); });
