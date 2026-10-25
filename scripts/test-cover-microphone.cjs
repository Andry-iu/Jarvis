const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/it/jarviswake/WebChatActivity.kt', 'utf8');
const script = name => source.split(`private val ${name} = """`)[1].split('""".trimIndent()')[0];
const observer = script('VOICE_CONTROL_LOCATION_SCRIPT')
  .replace('__JARVIS_LOCKED__', 'false').replace('__JARVIS_MIC_MUTED__', 'false');
const control = script('MICROPHONE_CONTROL_SCRIPT');
function page(getUserMedia) {
  const output = {paused:false,readyState:4,muted:false,volume:1};
  const context = vm.createContext({window:{}, navigator:{mediaDevices:{getUserMedia}},
    document:{body:{innerText:''},querySelectorAll:s => s === 'audio,video' ? [output] : []},
    innerWidth:400,innerHeight:800});
  return {context,output,
    observe: () => vm.runInContext(observer,context),
    mute: value => JSON.parse(vm.runInContext(control.replace('__JARVIS_MIC_MUTED__',String(value)),context))};
}
(async () => {
  let release;
  const tracks = [{readyState:'live',enabled:true}, {readyState:'live',enabled:false}];
  const p = page(() => new Promise(resolve => release=resolve));
  assert.deepEqual(p.mute(true),{available:false},'Missing media cannot claim mute');
  p.observe();
  const wrapped = p.context.navigator.mediaDevices.getUserMedia;
  p.observe();
  assert.equal(p.context.navigator.mediaDevices.getUserMedia,wrapped,'Observation wraps acquisition once');
  assert.deepEqual(p.mute(true),{available:false},'No live microphone cannot claim mute');
  const pending = wrapped({audio:true});
  release({getAudioTracks:() => tracks}); await pending;
  assert.deepEqual(p.mute(true),{available:true,muted:true});
  assert.equal(tracks[0].enabled,false);
  assert.equal(tracks[1].enabled,false);
  assert.equal(p.output.muted,false,'Mute input leaves playback untouched');
  const replacement = {readyState:'live',enabled:true};
  const acquiring = wrapped({audio:true});
  release({getAudioTracks:() => [replacement]}); await acquiring;
  assert.equal(replacement.enabled,false,'Replacement microphones inherit mute before the stream is returned');
  assert.deepEqual(p.mute(false),{available:true,muted:false});
  assert.equal(tracks[0].enabled,true);
  assert.equal(replacement.enabled,true);
  assert.equal(tracks[1].enabled,false,'Do not undo a microphone disabled by the page itself');
  tracks.forEach(t => t.readyState='ended'); replacement.readyState='ended';
  assert.deepEqual(p.mute(true),{available:false},'Ended microphones cannot claim a successful change');
  console.log('PASS: cover mute/unmute, missing/ended inputs, replacement streams and preserved playback.');
})().catch(error => { console.error(error); process.exitCode=1; });
