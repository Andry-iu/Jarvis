import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto,createHash} from 'node:crypto';
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
  const names=(await r.json()).result.tools.map(t=>t.name).filter(n=>!n.startsWith('jarviswake_cloud_'));
  assert.deepEqual(names,['jarviswake_devices','jarviswake_request_status','jarviswake_status_result','jarviswake_get_contact','jarviswake_contacts_names_export_start','jarviswake_contacts_export_start','jarviswake_contacts_export_page','jarviswake_speak','jarviswake_find_contacts','jarviswake_navigate','jarviswake_call_by_name','jarviswake_call_contact','jarviswake_whatsapp','jarviswake_list_favorites','jarviswake_add_favorite','jarviswake_create_favorite','jarviswake_rename_favorite','jarviswake_change_favorite_number','jarviswake_delete_favorite','jarviswake_list_notes','jarviswake_read_note','jarviswake_save_note','jarviswake_guide']);
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

async function advertiseNotes(e,c,versionCode=104) {
 const r=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode,operations:['status','navigate','find_contacts','call_contact','whatsapp','list_notes','read_note','save_note']},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(r.status,200);
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
 assert.equal((await noteRespond(e,c,read,{code:'note_read',note:{...note,text:'x'.repeat(8001)}})).status,400);
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
 assert.equal(r.guide_version,'0.5.119');assert.equal(r.version_code,141);assert.equal(r.phone_required,false);assert.ok(r.topics.length>=19);
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

test('Google speech reuses pairing, claims once and reports completed playback without text in results',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:129,operations:['status','speak']},auth),e);
 const args={device_id:c.deviceId,session_id:crypto.randomUUID(),turn_id:crypto.randomUUID(),text:'Risposta simulata privata',request_id:crypto.randomUUID()};
 assert.equal(value(await tool(e,'jarviswake_speak',args)).state,'pending');
 assert.equal(value(await tool(e,'jarviswake_speak',args)).request_id,args.request_id);
 assert.equal((await tool(e,'jarviswake_speak',{...args,text:'Altro testo'})).isError,true);
 assert.equal((await (await claim(e,c,args.request_id)).json()).state,'claimed');
 assert.equal((await (await claim(e,c,args.request_id)).json()).state,'already_claimed');
 const result={code:'speech_completed',accepted:true,synthesized:true,playback_started:true,completed:true};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:{...result,text:'private'}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result},auth),e)).status,200);
 const reply=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id}));assert.deepEqual(reply.result,result);
 assert.equal((await (await e.BUCKET.get(`requests/${c.deviceId}/${args.request_id}.json`)).json()).params,undefined);
 // Reconnection may replay the result, never replace the first terminal answer.
 await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:{code:'cancelled'}},auth),e);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id})).result,result);
});
test('Google speech validates UTF-8 bytes/session ids and keeps old APKs incapable',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 const args={device_id:c.deviceId,session_id:crypto.randomUUID(),turn_id:crypto.randomUUID(),text:'Ciao',request_id:crypto.randomUUID()};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:128,operations:['speak']},auth),e);
 assert.equal((await tool(e,'jarviswake_speak',args)).isError,true);
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:129,operations:['speak']},auth),e);
 for(const delta of [{session_id:'old'},{turn_id:'none'},{text:'è'.repeat(2300)}]) assert.equal((await tool(e,'jarviswake_speak',{...args,...delta})).isError,true);
 assert.equal((await tool(e,'jarviswake_speak',args,{'oai-authenticated-user-id':'other'})).isError,true);
});
test('Expired speech is not delivered and impossible playback success is rejected',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:129,operations:['speak']},auth),e);
 const args={device_id:c.deviceId,session_id:crypto.randomUUID(),turn_id:crypto.randomUUID(),text:'Ciao',request_id:crypto.randomUUID()};
 await tool(e,'jarviswake_speak',args);await claim(e,c,args.request_id);
 const bad={code:'speech_completed',accepted:true,synthesized:false,playback_started:false,completed:true};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:bad},auth),e)).status,400);
 const key=`requests/${c.deviceId}/${args.request_id}.json`,cmd=await (await e.BUCKET.get(key)).json();cmd.expiresAt=1;await e.BUCKET.put(key,JSON.stringify(cmd));
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id})).state,'outcome_uncertain');
 assert.equal((await (await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:129,operations:['speak']},auth),e)).json()).request,null);
});
test('Speech progress is claim-bound, monotonic and never a playback completion',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:129,operations:['speak']},auth),e);
 const args={device_id:c.deviceId,session_id:crypto.randomUUID(),turn_id:crypto.randomUUID(),text:'Ciao',request_id:crypto.randomUUID()};
 await tool(e,'jarviswake_speak',args);
 const send=stage=>worker.fetch(request('/device/progress',{deviceId:c.deviceId,requestId:args.request_id,stage},auth),e);
 assert.equal((await send('accepted')).status,400);await claim(e,c,args.request_id);
 for(const stage of ['accepted','synthesized','playback_started','accepted']) assert.equal((await send(stage)).status,200);
 const pending=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id}));
 assert.equal(pending.state,'pending');assert.equal(pending.progress.stage,'playback_started');assert.equal(pending.result,undefined);
 assert.equal((await send('completed')).status,400);
});

// Generated test-only RSA key; all token endpoint responses are simulated.
const googleTestKeys=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const googleTestPem='-----BEGIN PRIVATE KEY-----\n'+Buffer.from(await crypto.subtle.exportKey('pkcs8',googleTestKeys.privateKey)).toString('base64')+'\n-----END PRIVATE KEY-----\n';
const googleTestAccount={type:'service_account',project_id:'voice-test-project',client_email:'jarvis@voice-test-project.iam.gserviceaccount.com',private_key:googleTestPem,private_key_id:'a'.repeat(40),token_uri:'https://oauth2.googleapis.com/token'};
const googleTestEnv=()=>({...env(),GOOGLE_CREDENTIAL_WRAP_KEY:Buffer.alloc(32,42).toString('base64url')});
const googleConfigure=(e,c,account=googleTestAccount,headers={...user,Origin:origin,'X-Jarvis-Link':'google'})=>worker.fetch(request('/api/google/configure',{deviceId:c.deviceId,account},headers),e);
const googleGet=(e,c,extra={})=>worker.fetch(request('/device/google-token',{deviceId:c.deviceId,project:googleTestAccount.project_id,...extra},{Authorization:'Bearer '+c.deviceToken}),e);
const fakeGoogleToken=()=>new Response(JSON.stringify({access_token:'simulated-private-google-token-value',token_type:'Bearer',expires_in:3600}),{headers:{'content-type':'application/json'}});
test('Google key configuration requires owner and same-origin; storage is encrypted and bound to device',async()=>{
 const e=googleTestEnv(),c=await pair(e);
 for(const h of [{},user,{...user,Origin:'https://evil.example','X-Jarvis-Link':'google'},{...user,Origin:origin}]) assert.ok([401,403].includes((await googleConfigure(e,c,googleTestAccount,h)).status));
 assert.equal((await googleConfigure(e,c,googleTestAccount,{...user,'oai-authenticated-user-id':'other',Origin:origin,'X-Jarvis-Link':'google'})).status,404);
 const realFetch=globalThis.fetch;globalThis.fetch=()=>{throw Error('No Google request during configuration');};
 try {const r=await googleConfigure(e,c);assert.equal(r.status,200);assert.deepEqual(await r.json(),{state:'configured'});}finally{globalThis.fetch=realFetch;}
 const stored=e.BUCKET.data.get(`google-credentials/${c.deviceId}.json`);assert.ok(stored);assert.ok(!stored.includes('PRIVATE KEY'));assert.ok(!stored.includes(googleTestAccount.client_email));
 const r=await worker.fetch(new Request(origin+'/api/google/devices',{headers:user}),e);const j=await r.json();assert.deepEqual(j,{devices:[{id:c.deviceId,configured:true}]});assert.ok(!JSON.stringify(j).includes('private_key'));
 const other=await pair(e);e.BUCKET.data.set(`google-credentials/${other.deviceId}.json`,stored);assert.equal((await googleGet(e,other)).status,500);
});
test('Google JSON rejects endpoint overrides, weak/malformed keys and missing runtime wrapping secret',async()=>{
 const e=googleTestEnv(),c=await pair(e);
 for(const value of [{...googleTestAccount,token_uri:'https://attacker.example/token'},{...googleTestAccount,type:'authorized_user'},{...googleTestAccount,private_key:'PRIVATE'},{...googleTestAccount,project_id:'../other'}]) assert.equal((await googleConfigure(e,c,value)).status,400);
 delete e.GOOGLE_CREDENTIAL_WRAP_KEY;assert.equal((await googleConfigure(e,c)).status,503);
});
test('Google token exchange verifies JWT, caches single-flight, refreshes near expiry, and never exposes secrets via MCP',async()=>{
 const e=googleTestEnv(),c=await pair(e);assert.equal((await googleConfigure(e,c)).status,200);
 const realFetch=globalThis.fetch,realNow=Date.now;let calls=0,clock=realNow();Date.now=()=>clock;
 globalThis.fetch=async(url,options)=>{
  calls++;assert.equal(url,'https://oauth2.googleapis.com/token');assert.equal(options.redirect,'error');
  const p=new URLSearchParams(options.body);assert.equal(p.get('grant_type'),'urn:ietf:params:oauth:grant-type:jwt-bearer');
  const jwt=p.get('assertion').split('.');const claims=JSON.parse(Buffer.from(jwt[1],'base64url'));assert.equal(claims.aud,url);assert.equal(claims.iss,googleTestAccount.client_email);assert.equal(claims.scope,'https://www.googleapis.com/auth/cloud-platform');assert.equal(claims.exp-claims.iat,3600);assert.equal(claims.sub,undefined);
  assert.equal(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',googleTestKeys.publicKey,Buffer.from(jwt[2],'base64url'),new TextEncoder().encode(jwt[0]+'.'+jwt[1])),true);return fakeGoogleToken();
 };
 try {
  const replies=await Promise.all([googleGet(e,c),googleGet(e,c),googleGet(e,c)]);assert.ok(replies.every(r=>r.status===200));assert.equal(calls,1);
  const j=await replies[0].json();assert.equal(j.accessToken,'simulated-private-google-token-value');assert.deepEqual(Object.keys(j).sort(),['accessToken','expiresIn','project']);assert.ok(j.expiresIn<=3600);
  assert.equal((await googleGet(e,c,{scope:'administrator'})).status,400);assert.equal((await googleGet(e,c,{project:'different-project'})).status,409);assert.equal(calls,1);
  clock+=3500*1000;assert.equal((await googleGet(e,c)).status,200);assert.equal(calls,2);
  const result=await tool(e,'jarviswake_google_token',{device_id:c.deviceId});assert.equal(result.isError,true);assert.ok(!JSON.stringify(result).includes(j.accessToken));
 }finally{globalThis.fetch=realFetch;Date.now=realNow;}
});
test('Google broker rejects unpaired clients, raw Google errors and malformed/oversized grants without retries',async()=>{
 const e=googleTestEnv(),c=await pair(e);
 assert.equal((await googleGet(e,c)).status,409);
 assert.equal((await worker.fetch(request('/device/google-token',{deviceId:c.deviceId,project:googleTestAccount.project_id},user),e)).status,401);
 await googleConfigure(e,c);const realFetch=globalThis.fetch;let calls=0;
 try {
  for(const reply of [()=>new Response('SECRET_PROVIDER_ERROR',{status:403}),()=>new Response('not json'),()=>new Response('x'.repeat(17000)),()=>new Response(JSON.stringify({access_token:'short',expires_in:3600,token_type:'Bearer'}))]) {
   globalThis.fetch=async()=>{calls++;return reply();};const r=await googleGet(e,c);assert.equal(r.status,502);assert.ok(!(await r.text()).includes('SECRET_PROVIDER_ERROR'));
  }
  assert.equal(calls,4);
 }finally{globalThis.fetch=realFetch;}
});
test('Google key removal, replacement and pairing revocation invalidate in-flight exchanges and cached grants',async()=>{
 const e=googleTestEnv(),c=await pair(e);await googleConfigure(e,c);const realFetch=globalThis.fetch;
 try {
  let release,started;const gate=new Promise(r=>started=r);globalThis.fetch=async()=>{started();return new Promise(r=>release=r);};
  const pending=googleGet(e,c);await gate;assert.equal((await googleConfigure(e,c,null)).status,200);release(fakeGoogleToken());assert.equal((await pending).status,409);assert.equal((await googleGet(e,c)).status,409);
  await googleConfigure(e,c);globalThis.fetch=async()=>fakeGoogleToken();assert.equal((await googleGet(e,c)).status,200);
  await googleConfigure(e,c);globalThis.fetch=async()=>new Response('',{status:403});assert.equal((await googleGet(e,c)).status,502);
  await worker.fetch(request('/api/revoke',{}, {...user,Origin:origin,'X-Jarvis-Link':'pair'}),e);assert.equal((await googleGet(e,c)).status,401);assert.equal(e.BUCKET.data.has(`google-credentials/${c.deviceId}.json`),false);
 }finally{globalThis.fetch=realFetch;}
});
test('Google credential page is private, uncached, uses same-origin upload and exposes no key or token',async()=>{
 const e=googleTestEnv();assert.equal((await worker.fetch(new Request(origin+'/google'),e)).status,401);
 const r=await worker.fetch(new Request(origin+'/google',{headers:user}),e);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Content-Security-Policy'),/frame-ancestors 'none'/);
 const html=await r.text();assert.match(html,/type="file"/);assert.match(html,/\/api\/google\/configure/);assert.ok(!html.includes('localStorage'));assert.ok(!html.includes(googleTestPem));
});
test('Interrupted speech is terminal but never claims playback completion or cancels another command',async()=>{
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:132,operations:['speak']},auth),e);
 const args={device_id:c.deviceId,session_id:crypto.randomUUID(),turn_id:crypto.randomUUID(),text:'Risposta interrotta simulata',request_id:crypto.randomUUID()};
 await tool(e,'jarviswake_speak',args);await claim(e,c,args.request_id);
 const result={code:'interrupted',accepted:true,synthesized:true,playback_started:true,completed:false};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:{...result,accepted:false,synthesized:false,playback_started:false}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result},auth),e)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:args.request_id})).result,result);
 assert.equal(value(await tool(e,'jarviswake_speak',args)).request_id,args.request_id);
});

const exportOps=['contacts_export_start','contacts_export_page'];
const exportManifest=()=>({code:'contacts_export_ready',snapshot_id:crypto.randomUUID(),bytes:100,chunk_bytes:6144,sha256:'a'.repeat(64),contacts:1,phones:1,emails:1,mode:'full',expires_at:Date.now()+1800000});
async function exportFixture(versionCode=136) {
 const e=env(),c=await pair(e),auth={Authorization:'Bearer '+c.deviceToken};
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode,operations:exportOps},auth),e);
 return {e,c,auth};
}
test('Explicit one-way export is capability and owner gated and never broadens old contact lookup',async()=>{
 const {e,c,auth}=await exportFixture(135),args={device_id:c.deviceId,contact_ref:'',request_id:crypto.randomUUID()};
 assert.equal((await tool(e,'jarviswake_contacts_export_start',args)).content[0].text,'app_update_required');
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:136,operations:exportOps},auth),e);
 assert.equal((await tool(e,'jarviswake_contacts_export_start',args,{'oai-authenticated-user-id':'other'})).isError,true);
 assert.equal((await tool(e,'jarviswake_contacts_export_start',{...args,contact_ref:'wildcard'})).isError,true);
 assert.equal(value(await tool(e,'jarviswake_contacts_export_start',args)).state,'pending');
 assert.equal(value(await tool(e,'jarviswake_contacts_export_start',args)).request_id,args.request_id);
 const p=await (await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:136,operations:exportOps},auth),e)).json();
 assert.equal(p.request.operation,'contacts_export_start');assert.deepEqual(p.request.params,{contact_ref:''});
 assert.equal((await tool(e,'jarviswake_find_contacts',{device_id:c.deviceId,name:'',request_id:crypto.randomUUID()})).isError,true);
});
test('Export manifest/page contracts reject extra content, impossible chunks and wrong operation results',async()=>{
 const {e,c,auth}=await exportFixture();let id=crypto.randomUUID();
 await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id});await claim(e,c,id);
 const send=result=>worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},auth),e);
 const manifest=exportManifest();
 for(const delta of [{text:'PRIVATE'},{bytes:8388609},{chunk_bytes:9999},{mode:'number'},{sha256:'bad'}]) assert.equal((await send({...manifest,...delta})).status,400);
 assert.equal((await send(manifest)).status,200);
 const reply=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id}));assert.deepEqual(reply.result,manifest);
 id=crypto.randomUUID();await tool(e,'jarviswake_contacts_export_page',{device_id:c.deviceId,snapshot_id:manifest.snapshot_id,offset:0,request_id:id});await claim(e,c,id);
 const page={code:'contacts_export_page',snapshot_id:manifest.snapshot_id,offset:0,next_offset:null,data_base64:Buffer.from('simulated-only').toString('base64'),expires_at:manifest.expires_at};
 for(const delta of [{next_offset:6144},{data_base64:'!BAD!'},{data_base64:'A'.repeat(349532)},{offset:1},{raw:'PRIVATE'}]) assert.equal((await send({...page,...delta})).status,400);
 assert.equal((await send(page)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,page);
 // Same request stays terminal; reconnect cannot replace a first page/result.
 assert.equal((await send({code:'contacts_export_expired'})).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,page);
});
test('Full-number export exchanges expire and payloads disappear without reexecution on the same id',async()=>{
 const {e,c,auth}=await exportFixture(),id=crypto.randomUUID();
 await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id});await claim(e,c,id);
 await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:exportManifest()},auth),e);
 const key=`results/${c.deviceId}/${id}.json`,terminal=await (await e.BUCKET.get(key)).json();terminal.received_at=Date.now()-120001;await e.BUCKET.put(key,JSON.stringify(terminal));
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'response_expired');
 assert.equal(await e.BUCKET.get(`results/${c.deviceId}/${id}.json`),null);
 assert.equal(value(await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id})).state,'outcome_uncertain');
});

test('Claimed export remains a live job beyond delivery and payload lifetimes, with monotonic heartbeats',async()=>{
 const {e,c,auth}=await exportFixture(),id=crypto.randomUUID();
 await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id});await claim(e,c,id);
 const key=`requests/${c.deviceId}/${id}.json`,cmd=await (await e.BUCKET.get(key)).json();
 cmd.createdAt=Date.now()-3*60*60*1000;cmd.expiresAt=cmd.createdAt+25000;await e.BUCKET.put(key,JSON.stringify(cmd));
 const progress=stage=>worker.fetch(request('/device/progress',{deviceId:c.deviceId,requestId:id,stage},auth),e);
 assert.equal((await progress('phones')).status,200);
 let state=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id}));
 assert.equal(state.state,'running');assert.equal(state.progress.stage,'phones');assert.ok(state.age_ms>=0);
 await progress('contacts');state=value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id}));
 assert.equal(state.progress.stage,'phones');
 // Polling and unrelated enqueue cleanup cannot delete or dispatch this claimed job again.
 const polled=await (await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:138,operations:exportOps},auth),e)).json();
 assert.equal(polled.request,null);await tool(e,'jarviswake_request_status',{device_id:c.deviceId});
 assert.notEqual(await e.BUCKET.get(key),null);
 assert.equal((await claim(e,c,id)).status,409);
 const manifest=exportManifest();assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:manifest},auth),e)).status,200);
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'responded');
 assert.equal((await progress('freezing')).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,manifest);
});

test('Lost export heartbeat reports awaiting device without success, deletion or automatic reexecution',async()=>{
 const {e,c,auth}=await exportFixture(),id=crypto.randomUUID();
 await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id});await claim(e,c,id);
 await e.BUCKET.put(`progress/${c.deviceId}/${id}.json`,JSON.stringify({stage:'contacts',observed_at:Date.now()-60000}));
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'awaiting_device');
 assert.equal(value(await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:id})).state,'awaiting_device');
 for(const data of [{stage:'phones',number:'PRIVATE'},{stage:'invented'},{stage:'contacts'}]) {
  const other=crypto.randomUUID();
  assert.notEqual((await worker.fetch(request('/device/progress',{deviceId:c.deviceId,requestId:other,...data},auth),e)).status,200);
 }
 assert.equal((await worker.fetch(request('/device/progress',{deviceId:c.deviceId,requestId:id,stage:'emails'},auth),e)).status,200);
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).state,'running');
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'contacts_export_busy'}},auth),e)).status,200);
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result.code,'contacts_export_busy');
});



test('Names-only tool is version gated, injects minimal params and rejects full-book fallback',async()=>{
 const {e,c,auth}=await exportFixture(138),args={device_id:c.deviceId,request_id:crypto.randomUUID()};
 assert.equal((await tool(e,'jarviswake_contacts_names_export_start',args)).content[0].text,'app_update_required');
 await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:139,operations:exportOps},auth),e);
 assert.equal((await tool(e,'jarviswake_contacts_names_export_start',{...args,contact_ref:''})).isError,true);
 assert.equal((await tool(e,'jarviswake_contacts_names_export_start',args,{'oai-authenticated-user-id':'other'})).isError,true);
 assert.equal(value(await tool(e,'jarviswake_contacts_names_export_start',args)).state,'pending');
 const p=await (await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:139,operations:exportOps},auth),e)).json();
 assert.equal(p.request.operation,'contacts_export_start');assert.deepEqual(p.request.params,{contact_ref:'',names_only:true});
 await claim(e,c,args.request_id);
 const manifest={...exportManifest(),mode:'names',phones:0,emails:0};
 const send=result=>worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result},auth),e);
 for(const delta of [{mode:'full'},{phones:1},{emails:1}]) assert.equal((await send({...manifest,...delta})).status,400);
 assert.equal((await send(manifest)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',args)).result,manifest);
 assert.equal((await tool(e,'jarviswake_contacts_export_start',{...args,contact_ref:''})).content[0].text,'request_id_conflict');
});

test('256KiB pages transfer whole small files and exact boundary chunks while preserving legacy manifests',async()=>{
 const {e,c,auth}=await exportFixture(140),chunk=256*1024;
 for(const size of [163483,chunk-1,chunk,chunk+1,2*chunk]) {
  const manifest={...exportManifest(),bytes:size,chunk_bytes:chunk};
  let rid=crypto.randomUUID();
  await tool(e,'jarviswake_contacts_export_start',{device_id:c.deviceId,contact_ref:'',request_id:rid});await claim(e,c,rid);
  assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:manifest},auth),e)).status,200);
  for(let offset=0;offset<size;offset+=chunk) {
   rid=crypto.randomUUID();await tool(e,'jarviswake_contacts_export_page',{device_id:c.deviceId,snapshot_id:manifest.snapshot_id,offset,request_id:rid});await claim(e,c,rid);
   const bytes=Math.min(chunk,size-offset),page={code:'contacts_export_page',snapshot_id:manifest.snapshot_id,offset,
    next_offset:offset+bytes<size?offset+bytes:null,data_base64:Buffer.alloc(bytes,120).toString('base64'),expires_at:manifest.expires_at};
   assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:page},auth),e)).status,200);
   assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:rid})).result,page);
  }
 }
});
test('Large page validation rejects oversize, mismatched snapshot/offset and incomplete non-final pages',async()=>{
 const {e,c,auth}=await exportFixture(140),rid=crypto.randomUUID(),snapshot_id=crypto.randomUUID(),chunk=256*1024;
 await tool(e,'jarviswake_contacts_export_page',{device_id:c.deviceId,snapshot_id,offset:0,request_id:rid});await claim(e,c,rid);
 const page={code:'contacts_export_page',snapshot_id,offset:0,next_offset:chunk,data_base64:Buffer.alloc(chunk,120).toString('base64'),expires_at:Date.now()+1800000};
 for(const delta of [{snapshot_id:crypto.randomUUID()},{offset:chunk,next_offset:2*chunk},
  {data_base64:Buffer.alloc(chunk+1).toString('base64')},{data_base64:Buffer.alloc(chunk-1).toString('base64')},
  {next_offset:chunk+1},{data_base64:'!'.repeat(349528)}]) {
  assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:{...page,...delta}},auth),e)).status,400);
 }
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:{...page,data_base64:'A'.repeat(358000)}},auth),e)).status,413);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:page},{Authorization:'Bearer '+'x'.repeat(43)}),e)).status,401);
 assert.equal((await worker.fetch(request('/device/poll',{deviceId:c.deviceId,padding:'x'.repeat(20000)},auth),e)).status,413);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:{code:'note_saved',padding:'x'.repeat(20000)}},auth),e)).status,413);
 assert.equal((await worker.fetch(request('/mcp',{jsonrpc:'2.0',id:1,padding:'x'.repeat(20000)},user),e)).status,413);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:rid,result:page},auth),e)).status,200);
});

test('8000-character Unicode notes cross the 16KiB boundary in both directions within the dedicated 24KiB limit',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c,140);
 const text='界'.repeat(8000),note=noteMeta();
 const save=await noteCommand(e,c,'jarviswake_save_note',{note_id:'',title:note.title,text,expected_revision:0});
 const polled=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:140,operations:['save_note']},{Authorization:'Bearer '+c.deviceToken}),e);
 // Claiming above hides this command from subsequent polls; inspect the exact persisted command instead.
 assert.equal(polled.status,200);
 const command=await (await e.BUCKET.get(`requests/${c.deviceId}/${save.request_id}.json`)).json();
 const body={request:{...command,remainingMs:25000}};
 assert.ok(Buffer.byteLength(JSON.stringify(body))>16384);assert.ok(Buffer.byteLength(JSON.stringify(body))<=24576);
 assert.equal(command.params.text,text);
 assert.equal((await noteRespond(e,c,save,{code:'note_saved',note})).status,200);
 await advertiseNotes(e,c,140);
 const read=await noteCommand(e,c,'jarviswake_read_note',{note_id:note.note_id});
 assert.equal((await noteRespond(e,c,read,{code:'note_read',note:{...note,text}})).status,200);
 assert.equal(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:read.request_id})).result.note.text,text);
 const invalid=await tool(e,'jarviswake_save_note',{device_id:c.deviceId,request_id:crypto.randomUUID(),note_id:'',title:'Other',text:'x'.repeat(8001),expected_revision:0});
 assert.equal(invalid.isError,true);assert.equal(invalid.content[0].text,'invalid_text');
});
test('24KiB note envelopes do not broaden other routes and oversized notes never reserve their request id',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c,140);
 const args={device_id:c.deviceId,request_id:crypto.randomUUID(),note_id:'',title:'Title',text:'Body',expected_revision:0};
 const rpc={jsonrpc:'2.0',id:1,method:'tools/call',params:{name:'jarviswake_save_note',arguments:args},padding:''};
 rpc.padding='x'.repeat(24576-Buffer.byteLength(JSON.stringify(rpc)));
 assert.equal(Buffer.byteLength(JSON.stringify(rpc)),24576);
 assert.equal((await worker.fetch(request('/mcp',{...rpc,padding:rpc.padding+'x'},user),e)).status,413);
 assert.equal(await e.BUCKET.get(`seen/${c.deviceId}/${args.request_id}.json`),null);
 assert.equal((await worker.fetch(request('/mcp',rpc,user),e)).status,200);
 const other={...rpc,params:{name:'jarviswake_navigate',arguments:{device_id:c.deviceId,request_id:crypto.randomUUID(),destination:'Address'}}};
 assert.equal((await worker.fetch(request('/mcp',other,user),e)).status,413);
 const read=await noteCommand(e,c,'jarviswake_read_note',{note_id:crypto.randomUUID()});
 const big={code:'note_read',note:{...noteMeta(),text:'界'.repeat(8200)}};
 assert.equal((await noteRespond(e,c,read,big)).status,413);
 assert.equal((await noteRespond(e,c,read,{code:'note_payload_too_large'})).status,200);
});
test('Older APKs keep short notes but cannot receive the new note or contact pagination formats',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c,139);
 const long=await tool(e,'jarviswake_save_note',{device_id:c.deviceId,request_id:crypto.randomUUID(),note_id:'',title:'Title',text:'x'.repeat(4001),expected_revision:0});
 assert.equal(long.isError,true);assert.equal(long.content[0].text,'app_update_required');
 const more=await tool(e,'jarviswake_find_contacts',{device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Name',offset:20});
 assert.equal(more.isError,true);assert.equal(more.content[0].text,'app_update_required');
 const first=await tool(e,'jarviswake_find_contacts',{device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Name'});
 assert.equal(value(first).state,'pending');
});
test('Contact search accepts20 masked results per page with validated continuation and no new names inferred',async()=>{
 const e=env(),c=await pair(e);await advertiseNotes(e,c,140);
 const id=crypto.randomUUID(),args={device_id:c.deviceId,request_id:id,name:'Exact requested name',offset:20};
 assert.equal(value(await tool(e,'jarviswake_find_contacts',args)).state,'pending');await claim(e,c,id);
 const contacts=Array.from({length:20},(_,i)=>({contact_ref:crypto.randomUUID(),name:'Synthetic '+i,label:'Test',suffix:'0000'}));
 const send=result=>worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result},{Authorization:'Bearer '+c.deviceToken}),e);
 const result={code:'contacts_found',contacts,truncated:true,next_offset:40};
 for(const delta of [{contacts:[...contacts,contacts[0]]},{next_offset:20},{next_offset:41},{truncated:false},{contacts:contacts.slice(1)}])
  assert.equal((await send({...result,...delta})).status,400);
 assert.equal((await send(result)).status,200);
 assert.deepEqual(value(await tool(e,'jarviswake_status_result',{device_id:c.deviceId,request_id:id})).result,result);
 for(const offset of [-20,1,21,10020]) assert.equal((await tool(e,'jarviswake_find_contacts',{...args,request_id:crypto.randomUUID(),offset})).isError,true);
});

async function advertiseDirect(e,c,versionCode=141) {
  return worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode,operations:['status','get_contact','find_contacts','contacts_export_start','contacts_export_page']},{Authorization:'Bearer '+c.deviceToken}),e);
}
function directPayload() {
  const body={schema:1,mode:'number',contacts:[{id:'101',lookup_key:'synthetic-lookup',name:'Example Person',phones:[{id:'102',number:'+390000001',label:'mobile'}],emails:[]}]};
  const raw=Buffer.from(JSON.stringify(body));
  return {code:'contact_number',bytes:raw.length,sha256:requireDigest(raw),data_base64:raw.toString('base64')};
}
function requireDigest(raw) { // Synthetic fixture only; no phone contents.
  return createHash('sha256').update(raw).digest('hex');
}
async function directRoundTrip(e,c,result,args={name:'Example Person',contact_ref:''}) {
  await advertiseDirect(e,c);
  const request_id=crypto.randomUUID(),params={device_id:c.deviceId,request_id,...args};
  const waiting=tool(e,'jarviswake_get_contact',params);
  const key=`pending/${c.deviceId}/${request_id}.json`;
  const until=Date.now()+3000;
  while(!await e.BUCKET.get(key) && Date.now()<until) await new Promise(r=>setTimeout(r,2));
  const poll=await advertiseDirect(e,c);assert.equal((await poll.json()).request.requestId,request_id);
  const auth={Authorization:'Bearer '+c.deviceToken};
  assert.equal((await worker.fetch(request('/device/claim',{deviceId:c.deviceId,requestId:request_id},auth),e)).status,200);
  const sent=await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:request_id,result},auth),e);
  return {sent,reply:await waiting,params};
}
test('Direct number requires APK141 and exactly a name or a selected reference',async()=>{
  const e=env(),c=await pair(e);await advertiseDirect(e,c,140);
  const args={device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Example Person',contact_ref:''};
  assert.equal((await tool(e,'jarviswake_get_contact',args)).content[0].text,'app_update_required');
  await advertiseDirect(e,c);
  for(const bad of [{name:'',contact_ref:''},{name:'Example',contact_ref:crypto.randomUUID()},{name:'',contact_ref:crypto.randomUUID(),offset:20},{name:'Example',contact_ref:'',offset:1}])
    assert.equal((await tool(e,'jarviswake_get_contact',{...args,...bad})).isError,true);
});
test('One direct command can return a complete single number without export start/page calls',async()=>{
  const e=env(),c=await pair(e),payload=directPayload();
  const {sent,reply,params}=await directRoundTrip(e,c,payload);
  assert.equal(sent.status,200);const r=value(reply);assert.equal(r.state,'responded');assert.equal(r.operation,'get_contact');assert.deepEqual(r.result,payload);
  assert.ok(r.timings.queue_ms>=0);assert.ok(r.timings.claimed_to_response_ms>=0);assert.equal(r.timings.server_total_ms,r.timings.queue_ms+r.timings.claimed_to_response_ms);
  assert.equal([...e.BUCKET.data.keys()].filter(k=>k.startsWith(`requests/${c.deviceId}/`)).length,1);
  assert.equal([...e.BUCKET.data.keys()].filter(k=>k.startsWith(`pending/${c.deviceId}/`)).length,0);
  assert.equal(value(await tool(e,'jarviswake_get_contact',params)).request_id,params.request_id);
});
test('Direct ambiguity sends only masked candidates and selection is a second direct command',async()=>{
  const e=env(),c=await pair(e),ref=crypto.randomUUID();
  const metadata={code:'contacts_found',contacts:[{contact_ref:ref,name:'Example Person',label:'mobile',suffix:'0001'},{contact_ref:crypto.randomUUID(),name:'Example Person',label:'home',suffix:'0002'}],truncated:false,next_offset:null};
  const first=await directRoundTrip(e,c,metadata);assert.equal(first.sent.status,200);assert.deepEqual(value(first.reply).result,metadata);
  const second=await directRoundTrip(e,c,directPayload(),{name:'',contact_ref:ref});assert.equal(second.sent.status,200);assert.equal(value(second.reply).result.code,'contact_number');
});
test('Direct result rejects bad encoding, full-book payloads and unsolicited private fields',async()=>{
  const e=env(),c=await pair(e),payload=directPayload();
  await advertiseDirect(e,c);const args={device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Example',contact_ref:''};
  const pending=value(await tool(e,'jarviswake_get_contact',args));assert.equal(pending.state,'pending');
  const auth={Authorization:'Bearer '+c.deviceToken};
  await worker.fetch(request('/device/claim',{deviceId:c.deviceId,requestId:args.request_id},auth),e);
  const pack=body=>{const raw=Buffer.from(JSON.stringify(body));return {...payload,bytes:raw.length,sha256:requireDigest(raw),data_base64:raw.toString('base64')};};
  const original=JSON.parse(Buffer.from(payload.data_base64,'base64'));
  for(const bad of [{...payload,sha256:'0'.repeat(64)},{...payload,audio:'forbidden'},{...payload,bytes:payload.bytes+1},{...payload,data_base64:'%%%='},pack({...original,mode:'full'}),pack({...original,contacts:[...original.contacts,...original.contacts]}),pack({...original,contacts:[{...original.contacts[0],emails:[{id:'e',label:'home',address:'private@example.invalid'}]}]})])
    assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:args.request_id,result:bad},auth),e)).status,400);
});
test('Pending queue delivery does not scan completed request history after one-time migration',async()=>{
  const e=env(),c=await pair(e);await advertiseDirect(e,c);
  for(let i=0;i<300;i++) await e.BUCKET.put(`requests/${c.deviceId}/history-${i}.json`,JSON.stringify({requestId:`history-${i}`,operation:'status',createdAt:1,expiresAt:2}));
  const get=e.BUCKET.get.bind(e.BUCKET),list=e.BUCKET.list.bind(e.BUCKET);let historyReads=0,historyLists=0;
  e.BUCKET.get=async k=>{if(k.includes('/history-'))historyReads++;return get(k);};
  e.BUCKET.list=async q=>{if(q.prefix.startsWith('requests/'))historyLists++;return list(q);};
  const q=value(await tool(e,'jarviswake_request_status',{device_id:c.deviceId}));
  const polled=await advertiseDirect(e,c);assert.equal((await polled.json()).request.requestId,q.request_id);
  assert.equal(historyReads,0);assert.equal(historyLists,0);
});
test('First indexed poll migrates an existing legacy pending request without dropping it',async()=>{
  const e=env(),c=await pair(e),rid=crypto.randomUUID(),now=Date.now();
  await e.BUCKET.put(`requests/${c.deviceId}/${rid}.json`,JSON.stringify({requestId:rid,operation:'status',params:{},createdAt:now,expiresAt:now+25000}));
  const poll=await advertiseDirect(e,c);assert.equal((await poll.json()).request.requestId,rid);
  assert.ok(await e.BUCKET.get(`pending-index/${c.deviceId}.json`));
});

test('Legacy migration follows every R2 cursor before enabling indexed-only delivery',async()=>{
  const e=env(),c=await pair(e),now=Date.now(),rid=crypto.randomUUID();
  for(let i=0;i<205;i++) await e.BUCKET.put(`requests/${c.deviceId}/a-history-${String(i).padStart(3,'0')}.json`,JSON.stringify({requestId:`a-history-${i}`,operation:'status',createdAt:1,expiresAt:2}));
  await e.BUCKET.put(`requests/${c.deviceId}/z-pending.json`,JSON.stringify({requestId:rid,operation:'status',params:{},createdAt:now,expiresAt:now+25000}));
  // The canonical key is used during dispatch after migration.
  await e.BUCKET.put(`requests/${c.deviceId}/${rid}.json`,JSON.stringify({requestId:rid,operation:'status',params:{},createdAt:now,expiresAt:now+25000}));
  e.BUCKET.list=async ({prefix,limit,cursor})=>{const keys=[...e.BUCKET.data.keys()].filter(k=>k.startsWith(prefix)).sort();const start=Number(cursor||0),end=start+limit;return {objects:keys.slice(start,end).map(key=>({key})),truncated:end<keys.length,cursor:end<keys.length?String(end):undefined};};
  const poll=await advertiseDirect(e,c);assert.equal((await poll.json()).request.requestId,rid);
  assert.ok(await e.BUCKET.get(`pending-index/${c.deviceId}.json`));
});
test('Same direct UUID repairs a missing queue index without creating or claiming a new request',async()=>{
  const e=env(),c=await pair(e);await advertiseDirect(e,c);
  const args={device_id:c.deviceId,request_id:crypto.randomUUID(),name:'Example',contact_ref:''};
  assert.equal(value(await tool(e,'jarviswake_get_contact',args)).state,'pending');
  await e.BUCKET.delete(`pending/${c.deviceId}/${args.request_id}.json`);
  assert.equal(value(await tool(e,'jarviswake_get_contact',args)).state,'pending');
  const poll=await advertiseDirect(e,c);assert.equal((await poll.json()).request.requestId,args.request_id);
  assert.equal([...e.BUCKET.data.keys()].filter(k=>k.startsWith(`requests/${c.deviceId}/`)).length,1);
  assert.equal(await e.BUCKET.get(`claims/${c.deviceId}/${args.request_id}.json`),null);
});
