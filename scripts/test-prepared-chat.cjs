const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/assets/prepare-chat.js','utf8');
const project='https://chatgpt.com/g/g-p-abc123/project';
const url='https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc';
const message='leggi le istruzioni del progetto';
function fixture(phrase=message){
 let writes=0,sends=0,editorQueries=0;
 class Editor {constructor(){this._value='';}get value(){return this._value;}set value(v){writes++;this._value=v;}
  getAttribute(){return null;}getBoundingClientRect(){return {width:500,height:80};}
  dispatchEvent(){return true;}closest(){return document;}}
 const editor=new Editor(),button={disabled:false,innerText:'',getAttribute:n=>n==='data-testid'?'send-button':null,
  getBoundingClientRect:()=>({width:48,height:48}),click:()=>{sends++;}};
 const state={editors:[editor],buttons:[button],previousMessages:[],rejectTranscriptRead:false};
 const document={contains:e=>e===editor,querySelectorAll:s=>{
  if(s==='[data-message-author-role]') {if(state.rejectTranscriptRead)throw Error('Transcript must not be inspected');return state.previousMessages;}
  if(s.startsWith('textarea')){editorQueries++;return state.editors;}
  if(s==='button,[role="button"]')return state.buttons;
  throw Error('Unexpected selector '+s);
 }};
 const location={href:project},window={};
 const context={window,document,location,URL,Date,HTMLInputElement:class{},HTMLTextAreaElement:Editor,
  InputEvent:class{},Event:class{},getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})};
 const script=source.replace('__JARVIS_PROJECT_JSON__',JSON.stringify(project)).replace('__JARVIS_ATTEMPT_JSON__','"attempt"').replace('__JARVIS_MESSAGE_JSON__',JSON.stringify(phrase));
 return {state,editor,button,location,window,run:()=>JSON.parse(vm.runInNewContext(script,context)),counts:()=>({writes,sends,editorQueries})};
}
function submitted(){const f=fixture();assert.equal(f.run().phase,'written');assert.equal(f.editor.value,message);
 assert.equal(f.run().phase,'submitted');return f;}
{
 const f=submitted();assert.equal(f.run().phase,'waiting_url');f.location.href=url;
 f.state.rejectTranscriptRead=true;f.state.editors=[];
 const queries=f.counts().editorQueries;
 const result=f.run();assert.equal(result.phase,'ready');assert.equal(result.url,url);assert.equal(result.submitted,true);
 assert.ok(!('reply' in result));assert.equal(f.counts().editorQueries,queries);
 assert.equal(f.counts().writes,1);assert.equal(f.counts().sends,1);
}
{
 const f=fixture();f.editor._value='draft';assert.equal(f.run().phase,'draft');assert.equal(f.counts().writes,0);
}
{
 const f=fixture();f.state.buttons.push({...f.button});assert.equal(f.run().phase,'written');
 assert.equal(f.run().phase,'waiting_send');assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.location.href=url;assert.equal(f.run().phase,'wrong_project');assert.equal(f.counts().writes,0);
}
{
 const f=fixture();f.state.previousMessages=[{}];assert.equal(f.run().phase,'existing_conversation');assert.equal(f.counts().writes,0);
}
{
 const f=fixture();f.button.click=()=>{throw Error('uncertain');};assert.equal(f.run().phase,'written');
 assert.equal(f.run().phase,'send_uncertain');assert.equal(f.run().phase,'waiting_url');assert.equal(f.counts().writes,1);
}
{
 const f=submitted();f.location.href='https://other.example/c/12345678-1234-4234-8234-123456789abc';assert.equal(f.run().phase,'login');
 f.location.href=url.replace('/c/','/share/');assert.equal(f.run().phase,'waiting_url');
}
{
 const custom='Istruzioni "Jarvis"\nNon dire `un attimo`; ${nessuna_esecuzione} \u2028 sì.';
 const f=fixture(custom);assert.equal(f.run().phase,'written');assert.equal(f.editor.value,custom);
 assert.equal(f.run().phase,'submitted');f.location.href=url;assert.equal(f.run().phase,'ready');
 assert.equal(f.counts().writes,1);assert.equal(f.counts().sends,1);
}
assert.ok(!source.includes('getUserMedia') && !source.includes('.focus(') && !source.includes('fetch('));
console.log('PASS: one write/send, immediate URL capture without transcript/editor/response wait, project/draft/ambiguity guards and no media.');
