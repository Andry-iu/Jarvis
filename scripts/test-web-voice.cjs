const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/it/jarviswake/WebChatActivity.kt', 'utf8');
const diagnosticLogSource = fs.readFileSync('app/src/main/java/it/jarviswake/DiagnosticLog.kt', 'utf8');
const diagnosticExportSource = fs.readFileSync('app/src/main/java/it/jarviswake/DiagnosticExportActivity.kt', 'utf8');
const mainActivitySource = fs.readFileSync('app/src/main/java/it/jarviswake/MainActivity.kt', 'utf8');
const script = source.split('private val VOICE_CONTROL_LOCATION_SCRIPT = """')[1].split('""".trimIndent()')[0];
const stopScript = source.split('private val VOICE_STOP_BOTTOM_CONTROL_SCRIPT = """')[1].split('""".trimIndent()')[0];
const stopBlock = source.split('fun endVoiceForWake(token: String, callback: (Boolean) -> Unit) {')[1]
  .split('fun showStatus(token: String, message: String) {')[0];
const fillGreetingScript = source.split('private val FILL_GREETING_SCRIPT = """')[1].split('""".trimIndent()')[0];
const submitGreetingScript = source.split('private val SUBMIT_GREETING_SCRIPT = """')[1].split('""".trimIndent()')[0];
const responseDiagnosticsScript = source.split('private val ASSISTANT_RESPONSE_DIAGNOSTICS_SCRIPT = """')[1].split('""".trimIndent()')[0];
function element(label, {hidden = false, x = 340, y = 740, text = ''} = {}) {
  return {innerText: text, value: '', disabled: false, hidden,
    getAttribute: name => name === 'aria-label' ? label : null,
    querySelectorAll:() => [], contains:other => other === this,
    getBoundingClientRect: () => ({left:x, top:y, right:x+40, bottom:y+40, width:40, height:40})};
}
function page({controls = [], headings = [], composer = null, locked = false,
    audioElements = [], gum = async () => ({getAudioTracks: () => []})} = {}) {
  const body = {innerText:''};
  const context = vm.createContext({window: {}, navigator:{mediaDevices:{getUserMedia:gum}},
    innerWidth:400, innerHeight:800, getComputedStyle:e => ({visibility:e.hidden?'hidden':'visible',display:'block',opacity:'1'}),
    document:{body, getElementById:() => null, elementFromPoint:(x,y) =>
      controls.find(e => {const r=e.getBoundingClientRect();return x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom;}) || null,
      querySelectorAll:query => query === 'audio,video' ? audioElements :
        query.startsWith('h1') ? headings :
        query.startsWith('textarea') ? (composer ? [composer] : []) :
        query.startsWith('button') ? controls : []}});
  const runScript = script.replace('__JARVIS_LOCKED__', String(locked)).replace('__JARVIS_MIC_MUTED__', 'false');
  return {context, inspect: () => { const value = vm.runInContext(runScript, context); return value ? JSON.parse(value) : null; },
    stop: () => JSON.parse(vm.runInContext(stopScript, context))};
}
(async () => {
  assert.match(source, /ASSISTANT_MONITOR_QUERY/);
  assert.match(source, /ASSISTANT_MONITOR_RESULT/);
  assert.match(source, /ASSISTANT_MONITOR_INVALID_RESULT/);
  assert.match(diagnosticLogSource, /fun afterPendingWrites\(/);
  assert.match(diagnosticExportSource, /DiagnosticLog\.afterPendingWrites/);
  assert.match(mainActivitySource, /DiagnosticLog\.afterPendingWrites/,
    'Both diagnostic export paths wait for queued log writes');
  assert.match(stopBlock, /evaluateJavascript\(VOICE_STOP_BOTTOM_CONTROL_SCRIPT\)/,
    'Stop searches the current Voice UI for its bottom control');
  assert.match(stopBlock, /MotionEvent\.ACTION_DOWN[\s\S]*MotionEvent\.ACTION_UP/,
    'Stop sends a native DOWN/UP touch at the freshly located point');
  assert.match(stopScript, /innerHeight \* 0\.70/);
  assert.match(stopScript, /innerWidth \* 0\.68/);
  assert.match(stopScript, /sort\(\(a, b\) => b\.right - a\.right\)/,
    'Among compact controls along the lower right, the rightmost X is preferred');
  assert.match(stopScript, /elementFromPoint/,
    'Stop ignores controls covered by another element');
  assert.doesNotMatch(stopBlock, /voiceTogglePoint|COORDINATES_REPLAYED/,
    'Stop does not reuse the saved Voice activation location');
  const closeAtBottom = page({controls:[element('Voice',{x:340,y:100}), element('',{x:250,y:740}), element('',{x:340,y:740})]}).stop();
  assert.equal(closeAtBottom.result, 'found', 'Unlabelled current bottom X can be targeted');
  assert.equal(closeAtBottom.strategy, 'bottom_right');
  assert.equal(closeAtBottom.x, 360);
  assert.equal(closeAtBottom.y, 760);
  assert.equal(closeAtBottom.candidateCount, 1, 'Top Voice activation control and left mute button are ignored');
  assert.equal(page({controls:[element('',{x:250,y:740}), element('',{x:340,y:740})]}).stop().x, 360,
    'Bottom row mute control is not confused with the rightmost X');
  const disabledBottom = element('',{x:340,y:740}); disabledBottom.disabled=true;
  assert.equal(page({controls:[disabledBottom]}).stop().result, 'target_unavailable',
    'Disabled bottom controls cannot be tapped');
  assert.equal(page({controls:[element('',{x:340,y:500})]}).stop().result, 'target_unavailable',
    'A control above the lower screen is not mistaken for the close button');
  assert.match(stopBlock, /VOICE_STOP_BOTTOM_CONTROL_FOUND/);
  const start = element('Start voice mode');
  assert.equal(page({controls:[start]}).inspect().strategy, 'semantic');
  const topVoice = element('Start voice mode', {x:340,y:100});
  const bottomVoice = element('Voice', {x:340,y:740});
  assert.equal(page({controls:[topVoice,bottomVoice],locked:true}).inspect().y, 760,
    'When keyguard is showing, search at the bottom first');
  assert.equal(page({controls:[topVoice,bottomVoice]}).inspect().y, 120,
    'When unlocked, the strongest semantic label wins');
  const exitPage = page({controls:[element('Close voice',{x:320,y:720})]}).inspect();
  assert.equal(exitPage.state, 'active');
  assert.equal(exitPage.strategy, 'voice_exit', 'Expose the current Voice exit control for a fresh stop tap');
  assert.equal(exitPage.x, 340);
  assert.equal(exitPage.y, 740);
  assert.equal(exitPage.exitCandidates, 1);
  const ambiguousExit = page({controls:[
    element('Close voice',{x:320,y:720}), element('End voice mode',{x:250,y:650})
  ]}).inspect();
  assert.equal(ambiguousExit.state, 'active');
  assert.equal(ambiguousExit.strategy, undefined, 'Do not tap if multiple Voice exit controls are visible');
  assert.equal(ambiguousExit.exitCandidates, 2);
  const unrelatedClose = page({controls:[element('Close',{x:320,y:720})],
    headings:[element('Voice mode')]}).inspect();
  assert.equal(unrelatedClose.state, 'active');
  assert.equal(unrelatedClose.strategy, undefined, 'Do not guess from a generic close button');
  assert.equal(page({headings:[element('Voice mode')]}).inspect().outputPlaying, false,
    'Active Voice alone does not claim audible output');
  assert.equal(page({headings:[element('Voice mode')], audioElements:[{
    paused:false, readyState:4, muted:false, volume:1
  }]}).inspect().outputPlaying, true, 'Report only observable page media playback');
  assert.equal(page({headings:[element('Voice mode',{hidden:true})]}).inspect(), null);
  assert.equal(page({controls:[element('')]}).inspect(), null, 'No composer: no geometric start');
  let clock = 1000;
  const response = text => Object.assign(element('', {x:0,y:0}), {
    innerText:text, textContent:text,
    getBoundingClientRect:() => ({left:0,top:100,right:300,bottom:200,width:300,height:100})
  });
  const assistantNodes = [response('A diagnostic sample response, without exposing its text.')];
  const responseContext = vm.createContext({window:{}, innerWidth:400, innerHeight:800,
    Date:{now:() => clock}, getComputedStyle:() => ({visibility:'visible',display:'block',opacity:'1'}),
    document:{querySelectorAll:selector => selector === '[data-message-author-role]' ? assistantNodes :
      selector === '[data-message-author-role="assistant"]' ? assistantNodes : []}});
  const readResponses = () => JSON.parse(vm.runInContext(responseDiagnosticsScript, responseContext));
  const firstScan = readResponses();
  assert.equal(firstScan.responses.length, 0, 'Wait for the answer text to stabilize');
  assert.deepEqual({...firstScan}, {roleNodes:1,selectorNodes:1,visibleNodes:1,
    eligibleNodes:1,stableNodes:0,responses:[]}, 'Expose monitor health without returning message text');
  clock = 2300;
  const stableScan = readResponses();
  assert.equal(stableScan.stableNodes, 1, 'Report when a visible assistant node becomes stable');
  const firstResponse = stableScan.responses[0];
  assert.deepEqual({...firstResponse}, {id:1,revision:1,chars:56,duplicateOf:null,repeats:0});
  const repeatedResponse = response('A diagnostic sample response, without exposing its text.');
  assistantNodes.push(repeatedResponse);
  clock = 2400;
  assert.equal(readResponses().responses.length, 0);
  clock = 3700;
  const duplicateResponse = readResponses().responses[0];
  assert.equal(duplicateResponse.duplicateOf, 1, 'Exact duplicate assistant messages are correlated locally');
  assert.equal(JSON.stringify(duplicateResponse).includes('diagnostic sample'), false,
    'Only numeric metadata leaves the page; no response text is returned');
  assert.equal(JSON.stringify(stableScan).includes('diagnostic sample'), false,
    'Monitor health and response summaries never return response text');
  repeatedResponse.innerText = repeatedResponse.textContent = 'An edited assistant response with changed content.';
  clock = 3800; readResponses();
  clock = 5100;
  const updatedResponse = readResponses().responses[0];
  assert.equal(updatedResponse.id, 2);
  assert.equal(updatedResponse.revision, 2, 'A stable change to the same visible message is counted as an update');
  repeatedResponse.innerText = repeatedResponse.textContent =
    'Operativo... come sempre. Operativo... come sempre.';
  clock = 5200; readResponses();
  clock = 6500;
  assert.ok(readResponses().responses[0].repeats > 0,
    'Repeated sentences inside one assistant message are flagged without recording their text');
  const mismatchedRoleNodes = [response('A role-tagged node with a different role.')];
  const noAssistantContext = vm.createContext({window:{}, innerWidth:400, innerHeight:800,
    Date:{now:() => clock}, getComputedStyle:() => ({visibility:'visible',display:'block',opacity:'1'}),
    document:{querySelectorAll:selector => selector === '[data-message-author-role]' ? mismatchedRoleNodes : []}});
  const noAssistant = JSON.parse(vm.runInContext(responseDiagnosticsScript, noAssistantContext));
  assert.deepEqual({...noAssistant}, {roleNodes:1,selectorNodes:0,visibleNodes:0,
    eligibleNodes:0,stableNodes:0,responses:[]},
    'Distinguish a selector mismatch from a monitor that did not return data');
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
  function greetingPage({placeholder = 'Chiedi a ChatGPT', focused = false, message = 'ciao jarvis'} = {}) {
    const controls = [];
    let present = false, connected = true;
    const composer = Object.assign(Object.create(HTMLTextAreaElement.prototype), {
      disabled:false, innerText:'', textContent:'', isContentEditable:false, hidden:false,
      getAttribute:name => name === 'placeholder' ? placeholder : null,
      closest:() => null, focus() { throw new Error('Greeting must not focus the composer or open the keyboard'); },
      getBoundingClientRect:() => ({left:20, top:700, right:380, bottom:750, width:360, height:50}),
      dispatchEvent:() => true
    });
    const document = {activeElement:focused ? composer : null,
      querySelectorAll:query => query.startsWith('textarea') ? (present ? [composer] : []) : controls,
      contains:element => connected && element === composer};
    const context = vm.createContext({window:{}, document, innerWidth:400, innerHeight:800,
      getComputedStyle:e => ({visibility:e.hidden?'hidden':'visible',display:'block',opacity:'1'}),
      HTMLTextAreaElement, HTMLInputElement,
      InputEvent:class { constructor(type) { this.type=type; } },
      Event:class { constructor(type) { this.type=type; } }
    });
    return {controls, composer, context, document,
      show() { present=true; }, disconnect() { connected=false; },
      fill:() => JSON.parse(vm.runInContext(
        fillGreetingScript.replace('__JARVIS_GREETING_JSON__', JSON.stringify(message)), context)).result,
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
  const customGreeting = greetingPage({message:"Jarvis, rispondi all'utente: \"pronto\""});
  customGreeting.show();
  assert.equal(customGreeting.fill(), 'composer_waiting');
  assert.equal(customGreeting.fill(), 'filled', 'Configured greeting is safely quoted into the page script');
  assert.equal(customGreeting.composer.value, "Jarvis, rispondi all'utente: \"pronto\"");
  const focusedItalian = greetingPage({placeholder:'Digita', focused:true});
  focusedItalian.show();
  assert.equal(focusedItalian.fill(), 'composer_waiting');
  assert.equal(focusedItalian.fill(), 'filled', 'A focused empty composer is accepted even with an unfamiliar localized label');
  assert.equal(focusedItalian.composer.value, 'ciao jarvis');
  const focusedUnknown = greetingPage({placeholder:'Scrivi qui', focused:true});
  focusedUnknown.show();
  assert.equal(focusedUnknown.fill(), 'composer_waiting');
  assert.equal(focusedUnknown.fill(), 'filled', 'The already focused editable is a safe fallback when its label changes');
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
  editable.document.execCommand = () => { throw new Error('Unfocused greeting must not use the active selection'); };
  editable.fill();
  assert.equal(editable.fill(), 'filled', 'Contenteditable text is accepted without execCommand');
  assert.equal(editable.composer.textContent, 'ciao jarvis', 'Contenteditable greeting is inserted without focus');
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
