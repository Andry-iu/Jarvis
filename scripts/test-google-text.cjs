const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/assets/google-text.js','utf8');
const url='https://chatgpt.com/g/g-p-abc123/c/12345678-1234-4234-8234-123456789abc';
function fixture(){
 let sends=0,writes=0;
 const state={bodies:['messaggio precedente'],buttons:[],editors:[],dialogs:[]};
 class Editor {
  constructor(){this._value='';}get value(){return this._value;}set value(s){writes++;this._value=s;}
  closest(){return form;}getAttribute(){return null;}getBoundingClientRect(){return {width:500,height:80};}dispatchEvent(){return true;}
 }
 const form={querySelectorAll:()=>state.buttons};
 const editor=new Editor();state.editors=[editor];
 const button={getAttribute:k=>k==='data-testid'?'send-button':null,getBoundingClientRect:()=>({width:48,height:48}),click:()=>sends++};state.buttons=[button];
 const location={href:url},window={};
 const document={contains:e=>state.editors.includes(e),querySelector:()=>({}),querySelectorAll:s=>{
  if(s.startsWith('textarea'))return state.editors;
  if(s==='button,[role="button"]')return state.buttons;
  if(s==='[role="dialog"],[aria-modal="true"],dialog[open]')return state.dialogs;
  if(s==='[data-message-author-role="user"]')return state.bodies.map(textContent=>({textContent,querySelector:()=>null}));
  throw Error('Unexpected selector '+s);
 }};
 const context={window,document,location,URL,Map,HTMLInputElement:class{},HTMLTextAreaElement:Editor,InputEvent:class{},Event:class{},getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})};
 const cfg={url,session:'session',turn:'turn',text:'Ciao "Jarvis"\n`${literal}` è italiano.',action:'send'};
 return {state,editor,button,location,cfg,run:()=>JSON.parse(vm.runInNewContext(source.replace('__JW_CONFIG__',JSON.stringify(cfg)),context)).phase,counts:()=>({sends,writes})};
}
{
 const f=fixture();assert.equal(f.run(),'written');assert.equal(f.run(),'dispatched');
 for(let i=0;i<5;i++)assert.equal(f.run(),'waiting_observation');
 f.editor._value='';assert.equal(f.run(),'waiting_observation'); // empty field alone is NOT sent evidence
 f.state.bodies.push(f.cfg.text);assert.equal(f.run(),'observed');assert.deepEqual(f.counts(),{sends:1,writes:1});
}
{
 const f=fixture();f.editor._value='bozza privata';assert.equal(f.run(),'draft');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
{
 const f=fixture();assert.equal(f.run(),'written');f.editor._value='bozza modificata';assert.equal(f.run(),'draft_changed');f.editor._value=f.cfg.text;
 assert.equal(f.run(),'uncertain');assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.location.href=url.replace('abc123','different');assert.equal(f.run(),'wrong_conversation');assert.equal(f.counts().writes,0);
}
{
 const f=fixture();f.state.buttons.push({...f.button});assert.equal(f.run(),'written');assert.equal(f.run(),'waiting_send');assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.button.click=()=>{throw Error('uncertain');};assert.equal(f.run(),'written');assert.equal(f.run(),'send_uncertain');
 for(let i=0;i<4;i++)assert.equal(f.run(),'waiting_observation');assert.equal(f.counts().writes,1);
}
{
 const f=fixture();assert.equal(f.run(),'written');assert.equal(f.run(),'dispatched');f.state.buttons.push({getAttribute:k=>k==='data-testid'?'stop-button':null,getBoundingClientRect:()=>({width:40,height:40})});
 f.editor._value='';f.state.bodies.push(f.cfg.text);assert.equal(f.run(),'observed'); // generation must not invalidate send evidence
}
{
 const f=fixture();f.state.buttons.push({getAttribute:k=>k==='data-testid'?'stop-button':null,getBoundingClientRect:()=>({width:40,height:40})});
 assert.equal(f.run(),'busy');assert.equal(f.counts().writes,0);
}
{
 const f=fixture();assert.equal(f.run(),'written');f.cfg.session='old';assert.equal(f.run(),'old_session');assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.state.dialogs.push({getBoundingClientRect:()=>({width:400,height:300})});
 assert.equal(f.run(),'approval_or_dialog');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
assert.ok(!source.includes('getUserMedia')&&!source.includes('fetch(')&&!source.includes('.focus('));
console.log('PASS Google text: one send, draft/destination/session/ambiguity guards, no retry after uncertain dispatch; observed user message distinct from empty editor.');
