const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/assets/prepare-chat.js','utf8');
const project='https://chatgpt.com/g/g-p-abc123/project';
const url='https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc';
const message='leggi le istruzioni del progetto';
function fixture(){
  let now=0,writes=0,sends=0;
  class Editor {constructor(){this._value='';}get value(){return this._value;}set value(v){writes++;this._value=v;}
    getAttribute(){return null;}getBoundingClientRect(){return {width:500,height:80};}
    dispatchEvent(){return true;}closest(){return document;}}
  const editor=new Editor();const button={disabled:false,innerText:'',getAttribute:n=>n==='data-testid'?'send-button':null,
    getBoundingClientRect:()=>({width:48,height:48}),click:()=>{sends++;}};
  const state={editors:[editor],buttons:[button],users:[],assistants:[],blocking:false};
  const document={contains:e=>e===editor,querySelector:()=>state.blocking?{}:null,querySelectorAll:s=>{
    if(s==='[data-message-author-role]')return [...state.users,...state.assistants];
    if(s==='[data-message-author-role="user"]')return state.users;
    if(s==='[data-message-author-role="assistant"]')return state.assistants;
    if(s.startsWith('textarea'))return state.editors;
    if(s==='button,[role="button"]')return state.buttons;
    throw Error(s);
  }};
  const location={href:project};const window={};
  const context={window,document,location,URL,Date:{now:()=>now},HTMLInputElement:class{},HTMLTextAreaElement:Editor,
    InputEvent:class{},Event:class{},getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})};
  const script=source.replace('__JARVIS_PROJECT_JSON__',JSON.stringify(project)).replace('__JARVIS_ATTEMPT_JSON__','"attempt"');
  const run=()=>JSON.parse(vm.runInNewContext(script,context));
  return {state,editor,button,location,window,run,advance:n=>now+=n,counts:()=>({writes,sends})};
}
function submitted(){const f=fixture();assert.equal(f.run().phase,'written');assert.equal(f.editor.value,message);
 assert.equal(f.run().phase,'submitted');return f;}
function readyDocument(f){f.editor._value='';f.location.href=url;
 f.state.users=[{innerText:message}];f.state.assistants=[{innerText:'Ho letto le istruzioni del progetto e sono pronto a continuare con la conversazione.',querySelector:()=>null}];}
{
 const f=submitted();assert.equal(f.run().phase,'waiting_url');readyDocument(f);
 assert.equal(f.run().phase,'waiting_reply');f.advance(2999);assert.equal(f.run().phase,'waiting_reply');
 f.advance(1);const r=f.run();assert.equal(r.phase,'ready');assert.equal(r.url,url);
 assert.deepEqual(f.counts(),{writes:1,sends:1});assert.equal(f.run().phase,'ready');assert.deepEqual(f.counts(),{writes:1,sends:1});
}
{
 const f=fixture();f.editor._value='draft';assert.equal(f.run().phase,'draft');assert.deepEqual(f.counts(),{writes:0,sends:0});
}
{
 const f=fixture();f.state.buttons.push({...f.button});assert.equal(f.run().phase,'written');
 assert.equal(f.run().phase,'waiting_send');assert.deepEqual(f.counts(),{writes:1,sends:0});
}
{
 const f=fixture();f.location.href='https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc';
 assert.equal(f.run().phase,'wrong_project');assert.deepEqual(f.counts(),{writes:0,sends:0});
}
{
 const f=submitted();readyDocument(f);f.state.blocking=true;f.advance(10000);
 assert.equal(f.run().phase,'waiting_reply');f.state.blocking=false;assert.equal(f.run().phase,'waiting_reply');
 f.advance(3000);assert.equal(f.run().phase,'ready');
}
{
 const f=fixture();f.button.click=()=>{throw Error('uncertain');};assert.equal(f.run().phase,'written');
 assert.equal(f.run().phase,'send_uncertain');assert.equal(f.run().phase,'waiting_url');assert.equal(f.counts().writes,1);
}
{
 const f=submitted();readyDocument(f);f.state.users=[{innerText:'different message'}];assert.equal(f.run().phase,'waiting_user');f.advance(15000);assert.equal(f.run().phase,'wrong_conversation');
}
{
 const f=fixture();f.state.users=[{innerText:'previous conversation'}];assert.equal(f.run().phase,'existing_conversation');
 assert.deepEqual(f.counts(),{writes:0,sends:0});
}
assert.ok(!source.includes('getUserMedia') && !source.includes('.focus(') && !source.includes('fetch('));

// A conversation URL can be mounted before the corresponding user message nodes.
{
 const f=submitted();readyDocument(f);f.state.users=[];
 assert.equal(f.run().phase,'waiting_user');
 f.state.users=[{innerText:message}];assert.equal(f.run().phase,'waiting_reply');
 f.advance(3000);assert.equal(f.run().phase,'ready');assert.deepEqual(f.counts(),{writes:1,sends:1});
}

// Message body is distinct from accessible labels/buttons; exact text is still mandatory.
{
 const f=submitted();readyDocument(f);
 f.state.users=[{innerText:'Hai detto: '+message+' Modifica Copia',querySelectorAll:()=>[{innerText:message}]}];
 assert.equal(f.run().phase,'waiting_reply');f.advance(3000);assert.equal(f.run().phase,'ready');
}
{
 const f=submitted();readyDocument(f);
 f.state.users=[{innerText:'leggi le istruzioni\ndel progetto'}];
 assert.equal(f.run().phase,'waiting_reply');f.advance(3000);assert.equal(f.run().phase,'ready');
}
// Observe the echo while the URL is pending, before the site removes the bubble from the DOM.
{
 const f=submitted();readyDocument(f);f.location.href=project;
 assert.equal(f.run().phase,'waiting_url');f.state.users=[];f.location.href=url;
 assert.equal(f.run().phase,'waiting_reply');f.advance(3000);assert.equal(f.run().phase,'ready');
 f.location.href=url.replace('123456789abc','123456789abd');assert.equal(f.run().phase,'wrong_conversation');
}
{
 const f=submitted();readyDocument(f);f.state.users=[{innerText:message},{innerText:message}];
 assert.equal(f.run().phase,'waiting_user');f.advance(15000);assert.equal(f.run().phase,'wrong_conversation');
 assert.deepEqual(f.counts(),{writes:1,sends:1});
}
// A missing echo is never enough to declare ready, however long the reply has been present.
{
 const f=submitted();readyDocument(f);f.state.users=[];f.advance(20000);
 const result=f.run();assert.equal(result.phase,'waiting_user');assert.equal(result.users,0);assert.equal(result.echoObserved,false);
 assert.equal(result.reason,'message_absent');assert.ok(!('reply' in result));assert.ok(!('url' in result));
}
console.log('PASS: single send, delayed/virtualized user echo, message-only extraction, strict mismatch/count/URL guards, settled reply and privacy-safe diagnostics.');
