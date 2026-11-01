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
  assert.deepEqual(names,['jarviswake_devices','jarviswake_request_status','jarviswake_status_result','jarviswake_find_contacts','jarviswake_navigate','jarviswake_call_contact','jarviswake_whatsapp']);
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

async function advertise(e,c) {
 const r=await worker.fetch(request('/device/poll',{deviceId:c.deviceId,versionCode:103,operations:['status','navigate','find_contacts','call_contact','whatsapp']},{Authorization:'Bearer '+c.deviceToken}),e);assert.equal(r.status,200);
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
 const e=env(),c=await pair(e);await advertise(e,c);const id=crypto.randomUUID(),contact_ref=crypto.randomUUID();
 await tool(e,'jarviswake_whatsapp',{device_id:c.deviceId,contact_ref,text:'Explicit test text',request_id:id});
 const auth={Authorization:'Bearer '+c.deviceToken};
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'composer_cleared_after_send'}},auth),e)).status,409);
 await claim(e,c,id);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'delivered'}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'composer_cleared_after_send',text:'private'}},auth),e)).status,400);
 assert.equal((await worker.fetch(request('/device/respond',{deviceId:c.deviceId,requestId:id,result:{code:'send_dispatched_unconfirmed'}},auth),e)).status,200);
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
