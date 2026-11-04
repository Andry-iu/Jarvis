import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import worker from '../worker/index.js';
globalThis.crypto ??= webcrypto;
class Bucket {
  data=new Map();
  async get(k) { const v=this.data.get(k);return v==null?null:{json:async()=>JSON.parse(v)}; }
  async put(k,v,options) { if(options?.onlyIf?.etagDoesNotMatch==='*' && this.data.has(k))return null;this.data.set(k,v);return {}; }
  async delete(k) {this.data.delete(k);}
  async list({prefix,limit}) {return {objects:[...this.data.keys()].filter(k=>k.startsWith(prefix)).slice(0,limit).map(key=>({key}))};}
}
const origin='https://test.example';
const env=()=>({BUCKET:new Bucket(),SITE_DEVICE_ACCESS:'private-platform-credential'});
const request=(path,body,headers={})=>new Request(origin+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body)});
const user={'oai-authenticated-user-id':'owner'};
async function pair(e) {
  const r=await worker.fetch(request('/api/pair',{}, {...user,Origin:origin,'X-Jarvis-Link':'pair'}),e);
  assert.equal(r.status,200);const code=(await r.json()).code;
  return JSON.parse(Buffer.from(code.slice(4),'base64').toString());
}
async function tool(e,name,args,headers=user) {
  const r=await worker.fetch(request('/mcp',{jsonrpc:'2.0',id:1,method:'tools/call',params:{name,arguments:args}},headers),e);
  return (await r.json()).result;
}
const value=r=>JSON.parse(r.content[0].text);
const snap={app:{version:'0.5.80',versionCode:102},wake:{serviceRunning:true,capture:'not_recording'},cover:{accessibility:true,web:false,launch:false},voiceCycle:{active:true,phase:'VOICE',hiddenForWake:false},screenInteractive:true};
test('Discovery exposes bounded status and action tools without fabricated phone data',async()=>{
  const r=await worker.fetch(request('/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'}),env());
  const names=(await r.json()).result.tools.map(t=>t.name);
  assert.deepEqual(names,['jarviswake_devices','jarviswake_request_status','jarviswake_status_result','jarviswake_find_contacts','jarviswake_navigate','jarviswake_call_by_name','jarviswake_call_contact','jarviswake_whatsapp','jarviswake_list_favorites','jarviswake_add_favorite','jarviswake_create_favorite','jarviswake_rename_favorite','jarviswake_change_favorite_number','jarviswake_delete_favorite','jarviswake_list_notes','jarviswake_read_note','jarviswake_save_note','jarviswake_guide']);
});
test('Pairing requires authenticated same-origin browser action',async()=>{
  for(const h of [{},user,{...user,Origin:'https://attacker.example','X-Jarvis-Link':'pair'}]) {
    const r=await worker.fetch(request('/api/pair',{},h),env()); assert.ok([401,403].includes(r.status));
  }
});
test('No identity means no tool execution',async()=>{
  const r=await tool(env(),'jarviswake_devices',{},{});assert.equal(r.isError,true);assert.equal(r.content[0].text,'sign_in_required');
});
test('Round trip matches exact request and cannot contain chat/audio data',async()=>{
  const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
  assert.equal(value(await tool(e,'jarviswake_devices',{})).devices[0].device_id,c.deviceId);
  const command=value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId}));
  assert.equal(command.state,'pending');
  const p=await worker.fetch(request('/device/poll',{deviceId:c.deviceId},auth),e);
  assert.equal((await p.json()).request.requestId,command.request_id);
  const bad=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:command.request_id,snapshot:{...snap,chat:'private'}},auth),e);
  assert.equal(bad.status,400);
  const good=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:command.request_id,snapshot:snap},auth),e);
  assert.equal(good.status,200);
  const result=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:command.request_id}));
  assert.equal(result.state,'responded');assert.equal(result.source,'paired_device');assert.deepEqual(result.snapshot,snap);assert.equal(result.live,false);
});
test('Wrong user/device token and made-up commands are rejected',async()=>{
  const e=env(),c=await pair(e);
  assert.equal((await tool(e,'jarviswake_request_status',{device_id:c.deviceId},{'oai-authenticated-user-id':'other'})).isError,true);
  assert.equal((await worker.fetch(request('/device/poll',{deviceId:c.deviceId},{Authorization:'Bearer '+'x'.repeat(43)}),e)).status,401);
  assert.equal((await tool(e,'open_maps',{device_id:c.deviceId})).isError,true);
  assert.equal((await tool(e,'jarviswake_devices',{audio:true})).isError,true);
});
test('Unknown or expired responses never become a successful answer',async()=>{
  const e=env(),c=await pair(e),rid=crypto.randomUUID(),auth={Authorization:'Bearer '+c.deviceToken};
  assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:rid})).state,'unknown_request');
  await e.BUCKET.put(`requests/${c.deviceId}/${rid}.json`,JSON.stringify({requestId:rid,operation:'status',createdAt:1,expiresAt:2}));
  assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:rid})).state,'expired');
  assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,snapshot:snap},auth),e)).status,409);
});
test('Duplicate acknowledgement preserves first snapshot',async()=>{
  const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
  const cmd=value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId}));
  for(const screenInteractive of [true,false]) await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:cmd.request_id,snapshot:{...snap,screenInteractive}},auth),e);
  const r=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:cmd.request_id}));assert.equal(r.snapshot.screenInteractive,true);
});
test('The queue has a bounded number of pending requests',async()=>{
  const e=env(),c=await pair(e);
  for(let i=0;i<4;i++) assert.equal(value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId})).state,'pending');
  const r=await tool(e,'jarviswake_request_status',{device_id:c.deviceId});assert.equal(r.isError,true);assert.equal(r.content[0].text,'too_many_pending_requests');
});

test('Revocation affects only the authenticated owner and invalidates device credentials',async()=>{
 const e=env(),c=await pair(e);
 const r=await worker.fetch(request('/api/revoke',{}, {...user,Origin:origin,'X-Jarvis-Link':'pair'}),e);assert.equal(r.status,200);
 const p=await worker.fetch(request('/device/poll',{deviceId:c.deviceId},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(p.status,401);
 assert.equal(value(await tool(e,'jarviswake_devices',{})).devices.length,0);
});

async function advertise(e,c,versionCode=103) {
 const r=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode,operations:['status','navigate','find_contacts','call_contact','whatsapp']},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(r.status,200);
}
async function claim(e,c,id) {return worker.fetch(request('/device/claim',{deviceId:c.deviceId,requestId:id},{Authorization:'Bearer '+c.deviceToken}),e);}
test('New actions require an upgraded device; old polling only receives status',async()=>{
 const e=env(),c=await pair(e),id=crypto.randomUUID();
 assert.equal((await tool(e,'jarviswake_navigate',{device_id:c.deviceId,destination:'Test address',request_id:id})).isError,true);
 await advertise(e,c);
 assert.equal(value(await tool(e,'jarviswake_navigate',{device_id:c.deviceId,destination:'Test address',request_id:id})).state,'pending');
 const read=value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId}));
 const poll=await worker.fetch(request('/device/poll',{deviceId:c.deviceId},{Authorization:'Bearer '+c.deviceToken}),e);
 assert.equal((await poll.json()).request.requestId,read.request_id);
});
test('Maps uses a stable idempotency key, claim and correlated honest result',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID();
 const args={device_id:c.deviceId,destination:'Test address & symbols',request_id:id};
 assert.equal(value(await tool(e,'jarviswake_navigate',args)).state,'pending');
 assert.equal(value(await tool(e,'jarviswake_navigate',args)).request_id,id);
 const conflict=await tool(e,'jarviswake_navigate',{...args,destination:'Another address'});assert.equal(conflict.isError,true);
 assert.equal((await (await claim(e,c,id)).json()).state,'claimed');
 assert.equal((await (await claim(e,c,id)).json()).state,'already_claimed');
 const poll=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,operations:['navigate']},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal((await poll.json()).request,null);
 const ack=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'intent_dispatched'}},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(ack.status,200);
 const r=value(await tool(e,'jarviswake_navigate',args));assert.equal(r.result.code,'intent_dispatched');
 const stored=await e.BUCKET.get(`requests/${c.deviceId}/${id}.json`);assert.equal((await stored.json()).params,undefined);
});
test('A send or call result requires a claim and rejects invented delivery success',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c,109);const id=crypto.randomUUID(),contact_ref=crypto.randomUUID();
 await tool(e,'jarviswake_whatsapp',{device_id:c.deviceId,contact_ref,text:'Explicit test text',request_id:id});
 const auth={Authorization:'Bearer '+c.deviceToken};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'composer_cleared_after_send'}},auth),e)).status,409);
 await claim(e,c,id);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'delivered'}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'composer_cleared_after_send',text:'private'}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'whatsapp_server_accepted'}},auth),e)).status,200);
});
test('Contacts return bounded opaque references, not entire phonebook records',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID();
 await tool(e,'jarviswake_find_contacts',{device_id:c.deviceId,name:'Explicit name',request_id:id});await claim(e,c,id);
 const auth={Authorization:'Bearer '+c.deviceToken};
 const contact={contact_ref:crypto.randomUUID(),name:'Test Contact',label:'Mobile',suffix:'1234'};
 const invalid=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'contacts_found',contacts:[{...contact,phone:'secret'}],truncated:false}},auth),e);assert.equal(invalid.status,400);
 const valid=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'contacts_found',contacts:[contact],truncated:false}},auth),e);assert.equal(valid.status,200);
});
test('A claimed expired command reports uncertainty; an unclaimed one expires',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID();
 await tool(e,'jarviswake_call_contact',{device_id:c.deviceId,contact_ref:crypto.randomUUID(),request_id:id});await claim(e,c,id);
 const key=`requests/${c.deviceId}/${id}.json`,cmd=await (await e.BUCKET.get(key)).json();cmd.expiresAt=1;await e.BUCKET.put(key,JSON.stringify(cmd));
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'outcome_uncertain');
});
test('Concurrent requests with the same id create only one command and one claim',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID();
 const args={device_id:c.deviceId,contact_ref:crypto.randomUUID(),request_id:id};
 const rs=await Promise.all([tool(e,'jarviswake_call_contact',args),tool(e,'jarviswake_call_contact',args)]);assert.ok(rs.every(r=>!r.isError));
 assert.equal((await e.BUCKET.list({prefix:`requests/${c.deviceId}/`,limit:100})).objects.length,1);
 const claims=await Promise.all([claim(e,c,id),claim(e,c,id)]);const states=await Promise.all(claims.map(r=>r.json()));assert.equal(states.filter(x=>x.state==='claimed').length,1);
});

test('Pruning old payloads never permits another execution of an action id',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID();
 const args={device_id:c.deviceId,contact_ref:crypto.randomUUID(),request_id:id};
 await tool(e,'jarviswake_call_contact',args);await claim(e,c,id);
 await e.BUCKET.delete(`requests/${c.deviceId}/${id}.json`);await e.BUCKET.delete(`claims/${c.deviceId}/${id}.json`);
 const retry=value(await tool(e,'jarviswake_call_contact',args));assert.equal(retry.state,'outcome_uncertain');
 assert.equal((await e.BUCKET.list({prefix:`requests/${c.deviceId}/`,limit:100})).objects.length,0);
 assert.equal((await tool(e,'jarviswake_call_contact',{...args,contact_ref:crypto.randomUUID()})).isError,true);
});

async function advertiseNotes(e,c) {
 const r=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:104,operations:['status','navigate','find_contacts','call_contact','whatsapp','list_notes','read_note','save_note']},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(r.status,200);
}
const noteMeta=()=>({note_id:crypto.randomUUID(),title:'Synthetic note',revision:1,updated_at:123456});
async function noteCommand(e,c,name,fields) {
 const args={device_id:c.deviceId,request_id:crypto.randomUUID(),...fields};
 assert.equal(value(await tool(e,name,args)).state,'pending');await claim(e,c,args.request_id);return args;
}
async function noteRespond(e,c,args,result) {
 return worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result},{Authorization:'Bearer '+c.deviceToken}),e);
}
test('Notes require version104 without disrupting the version103 actions',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);
 const args={device_id:c.deviceId,offset:0,request_id:crypto.randomUUID()};
 const old=await tool(e,'jarviswake_list_notes',args);assert.equal(old.isError,true);assert.equal(old.content[0].text,'app_update_required');
 await advertiseNotes(e,c);assert.equal(value(await tool(e,'jarviswake_list_notes',args)).state,'pending');
 assert.equal(value(await tool(e,'jarviswake_navigate',{device_id:c.deviceId,destination:'Test address',request_id:crypto.randomUUID()})).state,'pending');
});
test('Notes schema enforces revisions, limits and exact bounded literal fields',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);
 const valid={device_id:c.deviceId,note_id:'',title:'Title',text:'Body',expected_revision:0,request_id:crypto.randomUUID()};
 for(const change of [{expected_revision:1},{expected_revision:-1},{expected_revision:'0'},{note_id:crypto.randomUUID(),expected_revision:0},{title:' x'},{title:'a\nb'},{text:'x'.repeat(4001)},{text:'\u0000'}, {extra:true}])
  assert.equal((await tool(e,'jarviswake_save_note',{...valid,...change})).isError,true);
 for(const offset of [-1,101,0.5,'0']) assert.equal((await tool(e,'jarviswake_list_notes',{device_id:c.deviceId,request_id:crypto.randomUUID(),offset})).isError,true);
 assert.equal(value(await tool(e,'jarviswake_save_note',valid)).state,'pending');
});
test('Notes read/write responses distinguish persisted metadata from read content',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);const note=noteMeta();
 const save=await noteCommand(e,c,'jarviswake_save_note',{note_id:'',title:note.title,text:'Explicit synthetic body',expected_revision:0});
 assert.equal((await noteRespond(e,c,save,{code:'note_saved',note})).status,200);
 const stored=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:save.request_id}));assert.equal(stored.result.code,'note_saved');assert.equal(stored.result.note.text,undefined);
 const read=await noteCommand(e,c,'jarviswake_read_note',{note_id:note.note_id});
 assert.equal((await noteRespond(e,c,read,{code:'note_read',note:{...note,text:'Explicit synthetic body'}})).status,200);
 const result=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:read.request_id}));assert.equal(result.result.note.text,'Explicit synthetic body');assert.equal(result.source,'paired_device');
 assert.equal((await (await e.BUCKET.get(`requests/${c.deviceId}/${save.request_id}.json`)).json()).params,undefined);
});
test('Notes cannot smuggle extra private fields, arbitrary success codes or bodies in listings',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);const note=noteMeta();
 const args=await noteCommand(e,c,'jarviswake_list_notes',{offset:0});
 for(const result of [{code:'notes_listed',notes:[{...note,text:'Hidden content'}],next_offset:null},{code:'notes_listed',notes:Array(21).fill(note),next_offset:null},{code:'note_saved',note},{code:'notes_disabled',secret:'bad'},{code:'notes_listed',notes:[note],next_offset:'20'}])
  assert.equal((await noteRespond(e,c,args,result)).status,400);
 assert.equal((await noteRespond(e,c,args,{code:'notes_listed',notes:[note],next_offset:null})).status,200);
 const read=await noteCommand(e,c,'jarviswake_read_note',{note_id:note.note_id});
 assert.equal((await noteRespond(e,c,read,{code:'note_read',note:{...note,text:'界'.repeat(4001)}})).status,400);
 const bounded={code:'note_read',note:{...note,text:'界'.repeat(4000)}};
 assert.ok(Buffer.byteLength(JSON.stringify(bounded))<16384);assert.equal((await noteRespond(e,c,read,bounded)).status,200);
});
test('Conflicts and disabled notes return no fabricated content and preserve action deduplication',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);
 const args=await noteCommand(e,c,'jarviswake_save_note',{note_id:crypto.randomUUID(),title:'Title',text:'Requested body',expected_revision:1});
 assert.equal((await noteRespond(e,c,args,{code:'note_conflict'})).status,200);
 const retry=value(await tool(e,'jarviswake_save_note',args));assert.equal(retry.result.code,'note_conflict');
 assert.equal((await tool(e,'jarviswake_save_note',{...args,text:'Another body'})).isError,true);
 const read=await noteCommand(e,c,'jarviswake_list_notes',{offset:0});assert.equal((await noteRespond(e,c,read,{code:'notes_disabled'})).status,200);
});
test('Note exchanges expire after two minutes and are removed on result lookup without permitting replay',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);const note=noteMeta();
 const args=await noteCommand(e,c,'jarviswake_read_note',{note_id:note.note_id});
 await noteRespond(e,c,args,{code:'note_read',note:{...note,text:'Transient result'}});
 const key=`requests/${c.deviceId}/${args.request_id}.json`,cmd=await (await e.BUCKET.get(key)).json();cmd.createdAt=1;await e.BUCKET.put(key,JSON.stringify(cmd));
 const expired=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id}));assert.equal(expired.state,'response_expired');assert.equal(expired.result,undefined);
 for(const kind of ['requests','results','claims']) assert.equal(await e.BUCKET.get(`${kind}/${c.deviceId}/${args.request_id}.json`),null);
 assert.equal(value(await tool(e,'jarviswake_read_note',args)).state,'outcome_uncertain');
 assert.equal(await e.BUCKET.get(key),null);
});
test('Device polling cleans expired note payloads even when no result is requested',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c);
 const args={device_id:c.deviceId,request_id:crypto.randomUUID(),note_id:'',title:'Title',text:'Pending body',expected_revision:0};
 await tool(e,'jarviswake_save_note',args);
 const key=`requests/${c.deviceId}/${args.request_id}.json`,cmd=await (await e.BUCKET.get(key)).json();cmd.createdAt=1;cmd.expiresAt=2;await e.BUCKET.put(key,JSON.stringify(cmd));
 await advertiseNotes(e,c);assert.equal(await e.BUCKET.get(key),null);assert.ok(await e.BUCKET.get(`seen/${c.deviceId}/${args.request_id}.json`));
});
test('Status remains compatible with old snapshots and reports only the notes consent flag',async()=>{
 const e=env(),c=await pair(e),cmd=value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId}));
 const permissions={actionsEnabled:false,contacts:false,callPhone:false,phoneState:false,notesEnabled:true};
 const r=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:cmd.request_id,snapshot:{...snap,permissions}},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(r.status,200);
 const result=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:cmd.request_id}));assert.equal(result.snapshot.permissions.notesEnabled,true);
});

test('The complete guide is available without pairing or a running phone',async()=>{
 const e=env();const r=value(await tool(e,'jarviswake_guide',{topic:'indice',query:''}));
 assert.equal(r.guide_version,'0.5.99');assert.equal(r.version_code,121);assert.equal(r.phone_required,false);assert.ok(r.topics.length>=19);
 assert.equal(e.BUCKET.data.size,0);
 for(const topic of ['wake','cover','audio','chatgpt','collegamento','appunti','plugin','maps','chiamate','whatsapp','diagnostica']) assert.ok(r.topics.some(t=>t.topic===topic));
});
test('Guide answers quote the canonical topics and never invent current phone status',async()=>{
 const e=env();const r=value(await tool(e,'jarviswake_guide',{topic:'cerca',query:'Come si pronuncia la parola wake Hey Jarvis?'}));
 assert.equal(r.matched,true);assert.ok(r.sections.some(s=>s.topic==='wake'));assert.ok(r.sections.some(s=>s.text.includes('hey jarvis')));
 assert.equal(r.snapshot,undefined);assert.equal(r.source,'versioned_jarviswake_guide');assert.ok(r.sections.length<=2);
 const cover=value(await tool(e,'jarviswake_guide',{topic:'cover',query:''}));assert.ok(cover.sections[0].text.includes('Swipe verso il basso'));assert.ok(cover.sections[0].text.includes('grigia'));
});
test('Unknown guide questions cannot produce fabricated instructions or device writes',async()=>{
 const e=env();const missing=value(await tool(e,'jarviswake_guide',{topic:'cerca',query:'xyznonsenseqwerty'}));assert.equal(missing.matched,false);assert.deepEqual(missing.sections,[]);
 for(const args of [{topic:'shell',query:''},{topic:'indice',query:'x'.repeat(301)},{topic:'indice',query:'',device_id:crypto.randomUUID()}]) assert.equal((await tool(e,'jarviswake_guide',args)).isError,true);
 assert.equal((await tool(e,'jarviswake_guide',{topic:'indice',query:''},{})).isError,true);assert.equal(e.BUCKET.data.size,0);
});

test('QR WhatsApp requires version109 and never offers the legacy UI path',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c);
 const args={device_id:c.deviceId,contact_ref:crypto.randomUUID(),text:'Explicit test',request_id:crypto.randomUUID()};
 assert.equal((await tool(e,'jarviswake_whatsapp',args)).isError,true);
 await advertise(e,c,109);
 assert.equal(value(await tool(e,'jarviswake_whatsapp',args)).state,'pending');
 await claim(e,c,args.request_id);
 const auth={Authorization:'Bearer '+c.deviceToken};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:{code:'whatsapp_not_linked'}},auth),e)).status,200);
 const result=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id}));
 assert.equal(result.result.code,'whatsapp_not_linked');
});


test('Combined call requires110, exact name and preserves ambiguity result and request id',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 const id=crypto.randomUUID(),args={device_id:c.deviceId,name:'Test Contact',request_id:id};
 await advertise(e,c,109);
 assert.equal((await tool(e,'jarviswake_call_by_name',args)).isError,true);
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:110,operations:['call_by_name','call_contact']},auth),e);
 assert.equal(value(await tool(e,'jarviswake_call_by_name',args)).state,'pending');
 const p=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:110,operations:['call_by_name']},auth),e);
 const command=(await p.json()).request;assert.equal(command.operation,'call_by_name');assert.deepEqual(command.params,{name:'Test Contact'});
 assert.equal((await claim(e,c,id)).status,200);
 const result={code:'contacts_found',contacts:[{contact_ref:crypto.randomUUID(),name:'Test Contact',label:'mobile',suffix:'0000'}],truncated:false};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},auth),e)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,result);
 assert.equal(value(await tool(e,'jarviswake_call_by_name',args)).state,'responded');
 assert.equal((await tool(e,'jarviswake_call_by_name',{...args,number:'+39000000000'})).isError,true);
 assert.equal((await tool(e,'jarviswake_call_by_name',{...args,name:'Another Contact'})).isError,true);
});


test('Manual favorite lookup uses the same opaque contact result and never returns a full number',async()=>{
 const e=env(),c=await pair(e);await advertise(e,c,111);const auth={Authorization:'Bearer '+c.deviceToken};
 const id=crypto.randomUUID(),ref=crypto.randomUUID();
 await tool(e,'jarviswake_find_contacts',{device_id:c.deviceId,name:'Home Test',request_id:id});
 assert.equal((await claim(e,c,id)).status,200);
 const result={code:'contacts_found',contacts:[{contact_ref:ref,name:'Home Test',label:'Preferito manuale',suffix:'0001'}],truncated:false};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},auth),e)).status,200);
 const r=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id}));assert.deepEqual(r.result,result);
 assert.equal(value(await tool(e,'jarviswake_call_contact',{device_id:c.deviceId,contact_ref:ref,request_id:crypto.randomUUID()})).state,'pending');
 assert.equal((await tool(e,'jarviswake_call_contact',{device_id:c.deviceId,contact_ref:ref,number:'+39000000001',request_id:crypto.randomUUID()})).isError,true);
});

const favoriteOps=['list_favorites','add_favorite','create_favorite','rename_favorite','change_favorite_number','delete_favorite'];
async function advertiseFavorites(e,c,version=113) {
 return worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:version,operations:favoriteOps},{Authorization:'Bearer '+c.deviceToken}),e);
}
const favMetadata={favorite_id:'book:1',name:'Test Favorite',suffix:'0001',source:'book',revision:1,book_conflict:'none'};
test('Favorite tools require113 and explicit fields; Android contacts are never a mutation target',async()=>{
 const e=env(),c=await pair(e);await advertiseFavorites(e,c,112);
 const args={device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Test Favorite',number:'+39000000001'};
 assert.equal((await tool(e,'jarviswake_create_favorite',args)).content[0].text,'app_update_required');
 await advertiseFavorites(e,c);
 for(const bad of [{...args,number:'12345;123'},{...args,number:'*123#'},{...args,android_contacts:true}]) assert.equal((await tool(e,'jarviswake_create_favorite',bad)).isError,true);
 assert.equal(value(await tool(e,'jarviswake_create_favorite',args)).state,'pending');
 const p=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:113,operations:favoriteOps},{Authorization:'Bearer '+c.deviceToken}),e);
 assert.deepEqual((await p.json()).request.params,{name:'Test Favorite',number:'+39000000001'});
 assert.equal((await claim(e,c,args.request_id)).status,200);assert.equal((await claim(e,c,args.request_id)).status,200);
});
test('Favorite result contracts reject full numbers and report cache/book conflicts without inventing success',async()=>{
 const e=env(),c=await pair(e);await advertiseFavorites(e,c);const auth={Authorization:'Bearer '+c.deviceToken};
 const id=crypto.randomUUID();await tool(e,'jarviswake_change_favorite_number',{device_id:c.deviceId,request_id:id,favorite_id:'book:1',expected_revision:1,number:'+39000000009'});await claim(e,c,id);
 const result={code:'favorite_updated',favorite:{...favMetadata,source:'manual',revision:2,book_conflict:'different',suffix:'0009'},cache_status:'unconfirmed'};
 const bad={...result,favorite:{...result.favorite,number:'+39000000009'}};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:bad},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},auth),e)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,result);
 const stored=await (await e.BUCKET.get(`requests/${c.deviceId}/${id}.json`)).json();assert.equal(stored.params,undefined);
});
test('Favorite lookup/add/delete use opaque references, revisions and stable request ids',async()=>{
 const e=env(),c=await pair(e);await advertiseFavorites(e,c);const auth={Authorization:'Bearer '+c.deviceToken};
 const cases=[
  ['list_favorites',{name:'Test',offset:0},{code:'favorites_listed',favorites:[favMetadata],next_offset:null}],
  ['add_favorite',{contact_ref:crypto.randomUUID()},{code:'favorite_saved',favorite:favMetadata}],
  ['rename_favorite',{favorite_id:'book:1',expected_revision:1,name:'Alias'},{code:'favorite_updated',favorite:{...favMetadata,name:'Alias',revision:2},cache_status:'cleared'}],
  ['delete_favorite',{favorite_id:'book:1',expected_revision:2},{code:'favorite_deleted',favorite_id:'book:1',cache_status:'cleared'}]
 ];
 for(const [op,params,result] of cases) {
  const id=crypto.randomUUID(),args={device_id:c.deviceId,request_id:id,...params};
  assert.equal(value(await tool(e,'jarviswake_'+op,args)).state,'pending');await claim(e,c,id);
  assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},auth),e)).status,200);
  assert.deepEqual(value(await tool(e,'jarviswake_'+op,args)).result,result);
 }
 assert.equal((await tool(e,'jarviswake_delete_favorite',{device_id:c.deviceId,request_id:crypto.randomUUID(),favorite_id:'*',expected_revision:1})).isError,true);
});
test('Favorite exchanges expire privately and cannot be replayed after payload cleanup',async()=>{
 const e=env(),c=await pair(e);await advertiseFavorites(e,c);const id=crypto.randomUUID(),params={device_id:c.deviceId,request_id:id,name:'Test',number:'+39000000009'};
 await tool(e,'jarviswake_create_favorite',params);
 const key=`requests/${c.deviceId}/${id}.json`,command=await (await e.BUCKET.get(key)).json();command.createdAt=Date.now()-120001;command.expiresAt=Date.now()-95001;await e.BUCKET.put(key,JSON.stringify(command));
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'response_expired');assert.equal(await e.BUCKET.get(key),null);
 assert.equal(value(await tool(e,'jarviswake_create_favorite',params)).state,'outcome_uncertain');
});
