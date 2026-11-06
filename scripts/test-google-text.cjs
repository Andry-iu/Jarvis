const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/assets/google-text.js','utf8');
const url='https://chatgpt.com/g/g-p-abc123/c/12345678-1234-4234-8234-123456789abc';
function fixture(){
 let sends=0,writes=0;
 const state={hasMessages:true,bodies:['messaggio precedente'],buttons:[],editors:[],dialogs:[]};
 class Editor {
  constructor(){this._value='';}get value(){return this._value;}set value(s){writes++;this._value=s;}
  closest(){return state.form;}getAttribute(){return null;}getBoundingClientRect(){return {width:500,height:80};}dispatchEvent(){return true;}
 }
 const form={querySelectorAll:()=>state.buttons};
 state.form=form;
 const editor=new Editor();state.editors=[editor];
 const button={getAttribute:k=>k==='data-testid'?'send-button':null,getBoundingClientRect:()=>({width:48,height:48}),click:()=>sends++};state.buttons=[button];
 const location={href:url},window={};
 const listeners=new Map();
 const document={addEventListener:(kind,fn)=>listeners.set(kind,fn),removeEventListener:(kind,fn)=>{if(listeners.get(kind)===fn)listeners.delete(kind);},contains:e=>state.editors.includes(e),querySelector:()=>state.hasMessages?{}:null,querySelectorAll:s=>{
  if(s.startsWith('textarea'))return state.editors;
  if(s==='button,[role="button"]')return state.buttons;
  if(s==='[role="dialog"],[aria-modal="true"],dialog[open]')return state.dialogs;
  if(s==='[data-message-author-role="user"]')return state.bodies.map(textContent=>({textContent,querySelector:()=>null}));
  throw Error('Unexpected selector '+s);
 }};
 const context={window,document,location,URL,Map,HTMLInputElement:class{},HTMLTextAreaElement:Editor,InputEvent:class{},Event:class{},getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})};
 const cfg={project:'https://chatgpt.com/g/g-p-abc123/project',session:'session',turn:'turn',text:'Ciao "Jarvis"\n`${literal}` è italiano.',action:'send'};
 const editable=(node=editor)=>{
  node.isContentEditable=true;
  Object.defineProperty(node,'value',{get:()=>undefined});
  Object.defineProperty(node,'textContent',{get:()=>node._value,set:text=>{writes++;node._value=text;}});
  Object.defineProperty(node,'innerText',{get:()=>state.renderedValue ?? node._value});
  return node;
 };
 const details=()=>JSON.parse(vm.runInNewContext(source.replace('__JW_CONFIG__',JSON.stringify(cfg)),context));
 return {state,editor,button,location,cfg,window,listeners,details,editable,run:()=>details().phase,counts:()=>({sends,writes}),emit:(kind,event)=>listeners.get(kind)?.(event)};
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
// A wake starts at the configured project. The first intended message creates this cycle's chat.
{
 const f=fixture();f.location.href=f.cfg.project;f.state.hasMessages=false;f.cfg.action='probe';
 assert.equal(f.run(),'ready');assert.deepEqual(f.counts(),{sends:0,writes:0});
 f.cfg.action='send';f.cfg.fresh=true;assert.equal(f.run(),'written');f.cfg.resume=true;assert.equal(f.run(),'dispatched');
 f.location.href='https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc';f.state.hasMessages=true;
 f.editor._value='';assert.equal(f.run(),'waiting_observation');f.state.bodies.push(f.cfg.text);assert.equal(f.run(),'observed');
 f.cfg.turn='next';f.cfg.text='secondo messaggio';f.cfg.resume=false;f.cfg.fresh=false;
 assert.equal(f.run(),'written');assert.equal(f.run(),'dispatched');assert.deepEqual(f.counts(),{sends:2,writes:2});
 f.location.href='https://chatgpt.com/c/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';assert.equal(f.run(),'wrong_conversation');assert.equal(f.counts().sends,2);
}
{
 const f=fixture();f.cfg.action='restart';assert.equal(f.run(),'restart_ready');assert.deepEqual(f.counts(),{sends:0,writes:0});
 f.editor._value='bozza da conservare';assert.equal(f.run(),'draft');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
{
 const f=fixture();f.cfg.fresh=true;assert.equal(f.run(),'wrong_conversation');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
{
 const f=fixture();f.location.href=f.cfg.project;f.state.hasMessages=false;f.cfg.fresh=true;
 assert.equal(f.run(),'written');assert.equal(f.run(),'dispatched');f.cfg.resume=true;
 f.location.href=url.replace('abc123','other-project');f.state.hasMessages=true;f.editor._value='';f.state.bodies.push(f.cfg.text);
 assert.equal(f.run(),'wrong_conversation');assert.equal(f.counts().sends,1);
}
{
 const f=fixture();f.location.href=f.cfg.project;f.state.hasMessages=false;f.cfg.resume=true;
 assert.equal(f.run(),'ledger_lost');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
{
 const f=fixture();f.location.href=f.cfg.project;assert.equal(f.run(),'project_has_messages');assert.deepEqual(f.counts(),{sends:0,writes:0});
}
{
 const f=fixture();f.location.href='https://chatgpt.com/c/12345678-1234-4234-8234-123456789abc';assert.equal(f.run(),'wrong_conversation');assert.equal(f.counts().writes,0);
}
console.log('PASS Google cycle: explicit new project chat, one first message, observed URL binding, same-chat turns, drafts and lost-ledger guards.');

// Diagnostic builds keep all send guards: a changed editor/form/text still blocks before click.
{
 const f=fixture();f.editable();const written=f.details();assert.equal(written.phase,'written');
 assert.equal(written.diagnostics.writeVerified,true);assert.equal(written.diagnostics.draftOwned,true);
 assert.equal(written.diagnostics.draftChars,f.cfg.text.length);assert.equal(written.diagnostics.textMatches,true);
 const next=f.editable(new f.editor.constructor());next._value=f.cfg.text;f.state.editors=[next];
 const r=f.details();assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.sameEditor,false);
 assert.equal(r.diagnostics.sameForm,true);assert.equal(r.diagnostics.originalEditorConnected,false);
 assert.equal(r.diagnostics.currentEditorConnected,true);assert.equal(r.diagnostics.textMatches,true);
 assert.equal(f.run(),'uncertain');assert.deepEqual(f.counts(),{sends:0,writes:1});
}
{
 const f=fixture();assert.equal(f.run(),'written');f.state.form={querySelectorAll:()=>f.state.buttons};
 const r=f.details();assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.sameEditor,true);
 assert.equal(r.diagnostics.sameForm,false);assert.equal(r.diagnostics.textMatches,true);assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.cfg.text='Prima riga\nSeconda riga';assert.equal(f.run(),'written');
 f.editor._value=f.cfg.text.replace('\n','\r\n');const r=f.details();
 assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.sameEditor,true);assert.equal(r.diagnostics.sameForm,true);
 assert.equal(r.diagnostics.textMatches,false);assert.equal(r.diagnostics.lineEndingsMatch,true);
 assert.equal(r.diagnostics.normalizedTextMatches,true);assert.equal(f.counts().sends,0);
}
{
 const f=fixture();f.cfg.text='Prima riga\nSeconda riga';assert.equal(f.run(),'written');
 f.editor._value=f.cfg.text.replace('\n','\n\n');const r=f.details();
 assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.lineEndingsMatch,false);
 assert.equal(r.diagnostics.normalizedTextMatches,true);assert.equal(r.diagnostics.expectedLineBreaks,1);
 assert.equal(r.diagnostics.draftLineBreaks,2);assert.equal(f.counts().sends,0);
}
{
 const f=fixture();assert.equal(f.run(),'written');f.editor._value='Una modifica reale e privata';
 f.emit('input',{isTrusted:false,data:'not logged'});f.emit('beforeinput',{isTrusted:true,data:'not logged'});
 const r=f.details();assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.normalizedTextMatches,false);
 assert.equal(r.diagnostics.trustedInputSeen,true);assert.equal(r.diagnostics.inputObservationAvailable,true);
 assert.equal(JSON.stringify(r).includes('privata'),false);assert.equal(JSON.stringify(r).includes('not logged'),false);
 assert.equal(f.counts().sends,0);f.window.__jarvisGoogleText.disposeDiagnostics();assert.equal(f.listeners.size,0);
}
{
 const f=fixture();assert.equal(f.run(),'written');f.emit('input',{isTrusted:false});
 const r=f.details();assert.equal(r.phase,'dispatched');assert.equal(r.diagnostics.trustedInputSeen,false);
 assert.equal(r.diagnostics.sendDispatched,true);assert.deepEqual(f.counts(),{sends:1,writes:1});
}
console.log('PASS Google composer diagnostics: replacement editor/form, CRLF/line breaks, changed text and trusted input distinguished without contents or relaxed send guards.');

{
 const f=fixture();f.editable();assert.equal(f.run(),'written');
 f.state.renderedValue=f.cfg.text.replace('\n','\n\n');const r=f.details();
 assert.equal(r.phase,'draft_changed');assert.equal(r.diagnostics.contentEditable,true);
 assert.equal(r.diagnostics.sameEditor,true);assert.equal(r.diagnostics.normalizedTextMatches,true);
 assert.equal(r.diagnostics.textMatches,false);assert.deepEqual(f.counts(),{sends:0,writes:1});
}
{
 const f=fixture();f.editable();assert.equal(f.run(),'written');assert.equal(f.run(),'dispatched');
 f.editor._value='';f.state.bodies.push(f.cfg.text);assert.equal(f.run(),'observed');assert.deepEqual(f.counts(),{sends:1,writes:1});
}
console.log('PASS contenteditable simulation: rendered line-break change blocks once; unchanged text sends once and observation remains distinct.');
