const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const TTL = 25000;
const json = (value, status=200) => new Response(JSON.stringify(value), {status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
const fail = (code,status=400) => { throw {code,status}; };
async function digest(s) { return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,'0')).join(''); }
const secret = () => btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const owner = req => req.headers.get('oai-authenticated-user-id') || fail('sign_in_required',401);
async function body(req) {
  if (!req.headers.get('content-type')?.startsWith('application/json')) fail('json_required',415);
  const text=await req.text(); if(text.length>16384) fail('body_too_large',413);
  try { return JSON.parse(text); } catch { fail('invalid_json'); }
}
const get = async (env,key) => (await env.BUCKET.get(key))?.json();
const put = (env,key,value) => env.BUCKET.put(key,JSON.stringify(value));
const deviceKey = id => `devices/${id}.json`;
const requestKey = (id,rid) => `requests/${id}/${rid}.json`;
const resultKey = (id,rid) => `results/${id}/${rid}.json`;
async function deviceForUser(env,uid,id) {
  if(!UUID.test(id)) fail('invalid_device');
  const d=await get(env,deviceKey(id)); if(!d || d.owner!==await digest(uid)) fail('device_not_found',404);
  return d;
}
async function deviceForToken(env,req,id) {
  if(!UUID.test(id)) fail('invalid_device');
  const token=req.headers.get('authorization')?.replace(/^Bearer /,'') || '';
  if(!TOKEN.test(token)) fail('device_unauthorized',401);
  const d=await get(env,deviceKey(id)); if(!d) fail('device_unauthorized',401);
  const hash=await digest(token); let delta=0;
  for(let i=0;i<64;i++) delta|=hash.charCodeAt(i)^d.tokenHash.charCodeAt(i);
  if(delta) fail('device_unauthorized',401);
  return d;
}
async function devices(env,uid) {
  const prefix=`owners/${await digest(uid)}/`;
  const page=await env.BUCKET.list({prefix,limit:5});
  const found=await Promise.all(page.objects.map(o=>get(env,o.key)));
  return found.filter(Boolean);
}
function snapshot(raw) {
  if(!raw || Object.keys(raw).sort().join(',')!=='app,cover,screenInteractive,voiceCycle,wake') fail('invalid_snapshot');
  if(!raw.app || Object.keys(raw.app).sort().join(',')!=='version,versionCode' || !/^\d+\.\d+\.\d+$/.test(raw.app.version) || !Number.isInteger(raw.app.versionCode) || raw.app.versionCode<1) fail('invalid_app');
  if(!raw.wake || Object.keys(raw.wake).sort().join(',')!=='capture,serviceRunning' || typeof raw.wake.serviceRunning!=='boolean' || !['active_verified','not_recording','unverified'].includes(raw.wake.capture)) fail('invalid_wake');
  if(!raw.cover || Object.keys(raw.cover).sort().join(',')!=='accessibility,launch,web' || !Object.values(raw.cover).every(v=>typeof v==='boolean')) fail('invalid_cover');
  if(!raw.voiceCycle || Object.keys(raw.voiceCycle).sort().join(',')!=='active,hiddenForWake,phase' || typeof raw.voiceCycle.active!=='boolean' || typeof raw.voiceCycle.hiddenForWake!=='boolean' || !['IDLE','UNLOCK','WEB','VOICE'].includes(raw.voiceCycle.phase)) fail('invalid_cycle');
  if(typeof raw.screenInteractive!=='boolean') fail('invalid_screen');
  return raw;
}
async function statusResult(env,d,rid) {
  if(!UUID.test(rid)) fail('invalid_request');
  const command=await get(env,requestKey(d.deviceId,rid));
  if(!command) return {state:'unknown_request',device_id:d.deviceId,request_id:rid};
  const result=await get(env,resultKey(d.deviceId,rid));
  if(result) return {...result,age_ms:Date.now()-result.received_at,live:false};
  return {state:Date.now()>command.expiresAt?'expired':'pending',device_id:d.deviceId,request_id:rid,expires_at:command.expiresAt};
}
const toolDefinitions=[
  {name:'jarviswake_devices',description:'Elenca soltanto i telefoni abbinati a questo utente. last_seen non prova che il telefono sia online. Nessun dato audio o testo chat.',inputSchema:{type:'object',properties:{},additionalProperties:false},annotations:{readOnlyHint:true,destructiveHint:false,openWorldHint:false}},
  {name:'jarviswake_request_status',description:'Richiede una nuova fotografia degli stati reali JarvisWake. Ritorna un request_id: usare jarviswake_status_result per ottenere la risposta dal telefono. pending/expired non sono successo; non inventare stati. Non avvia app, wake o Voice.',inputSchema:{type:'object',properties:{device_id:{type:'string'}},required:['device_id'],additionalProperties:false},annotations:{readOnlyHint:true,destructiveHint:false,openWorldHint:false}},
  {name:'jarviswake_status_result',description:'Legge la risposta del telefono alla specifica richiesta. state=responded prova un andata/ritorno per quel request_id, non che gli stati siano ancora attuali. Riferire age_ms. Non dichiarare Voice effettiva dallo stato del ciclo, né wake attiva dal solo servizio.',inputSchema:{type:'object',properties:{device_id:{type:'string'},request_id:{type:'string'}},required:['device_id','request_id'],additionalProperties:false},annotations:{readOnlyHint:true,destructiveHint:false,openWorldHint:false}}
];
async function callTool(env,uid,name,args) {
  if(!args || typeof args!=='object' || Array.isArray(args)) fail('invalid_arguments');
  const expected=name==='jarviswake_devices'?[]:name==='jarviswake_request_status'?['device_id']:['device_id','request_id'];
  if(Object.keys(args).sort().join(',')!==expected.sort().join(',')) fail('invalid_arguments');
  if(name==='jarviswake_devices') return {devices:(await devices(env,uid)).map(d=>({device_id:d.deviceId,last_seen:d.lastSeen||null}))};
  if(!['jarviswake_request_status','jarviswake_status_result'].includes(name)) fail('unknown_tool');
  const d=await deviceForUser(env,uid,args.device_id);
  if(name==='jarviswake_status_result') return statusResult(env,d,args.request_id);
  const page=await env.BUCKET.list({prefix:`requests/${d.deviceId}/`,limit:100});
  let pending=0;
  for(const o of page.objects) {
    const c=await get(env,o.key);
    if(c && c.expiresAt>Date.now() && !await get(env,resultKey(d.deviceId,c.requestId))) pending++;
    if(c && c.createdAt<Date.now()-600000) { await env.BUCKET.delete(o.key); await env.BUCKET.delete(resultKey(d.deviceId,c.requestId)); }
  }
  if(pending>=4) fail('too_many_pending_requests',429);
  const requestId=crypto.randomUUID(),now=Date.now();
  await put(env,requestKey(d.deviceId,requestId),{requestId,operation:'status',createdAt:now,expiresAt:now+TTL});
  return {state:'pending',device_id:d.deviceId,request_id:requestId,expires_at:now+TTL};
}
async function mcp(req,env) {
  const rpc=await body(req);
  if(rpc.jsonrpc!=='2.0' || typeof rpc.method!=='string') fail('invalid_rpc');
  let result;
  if(rpc.method==='server/discover') result={supportedVersions:['2026-07-28'],capabilities:{tools:{}}};
  else if(rpc.method==='initialize') result={protocolVersion:'2025-03-26',serverInfo:{name:'jarviswake-link',version:'0.1.0'},capabilities:{tools:{}}};
  else if(rpc.method==='notifications/initialized') return new Response(null,{status:204});
  else if(rpc.method==='tools/list') result={tools:toolDefinitions};
  else if(rpc.method==='tools/call') {
    try { const data=await callTool(env,owner(req),rpc.params?.name,rpc.params?.arguments||{}); result={content:[{type:'text',text:JSON.stringify(data)}]}; }
    catch(e) { result={isError:true,content:[{type:'text',text:e?.code||'service_error'}]}; }
  } else return json({jsonrpc:'2.0',id:rpc.id??null,error:{code:-32601,message:'Method not found'}});
  return json({jsonrpc:'2.0',id:rpc.id??null,result});
}
function page() {
  return new Response(`<!doctype html><html lang="it"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JarvisWake Link</title><style>body{margin:0;background:#0d1824;color:#edf5fa;font:17px system-ui}main{max-width:620px;margin:8vh auto;padding:26px}h1{color:#50daee}button{font:inherit;padding:13px 18px;background:#50daee;border:0;border-radius:10px;margin:14px 0}textarea{box-sizing:border-box;width:100%;height:120px;font:13px monospace}p{line-height:1.6}.muted{color:#b3c3d0}</style><main><h1>JarvisWake Link</h1><p>Prova privata di comunicazione con il telefono: versione dell’app, stato wake e cover. Il collegamento non invia audio o testo delle conversazioni e non controlla altre app.</p><ol><li>Crea un codice di collegamento.</li><li>Copialo in JarvisWake → Collegamento ChatGPT → Incolla codice.</li><li>Attiva il collegamento nell’app e lascia avviato il servizio wake.</li><li>In ChatGPT Live chiedi di usare JarvisWake Link per leggere lo stato del telefono.</li></ol><button id="pair">Crea codice di collegamento</button><p id="status" role="status"></p><textarea id="code" readonly hidden aria-label="Codice privato di collegamento"></textarea><button id="copy" hidden>Copia codice</button><p><button id="revoke">Revoca collegamenti precedenti</button></p><p class="muted">Il codice contiene credenziali private: copialo soltanto nell’app JarvisWake, senza inviarlo in chat. Puoi disattivare e cancellare il collegamento dall’app. Una risposta pending o expired non significa che il telefono abbia risposto.</p></main><script>const p=document.getElementById('pair'),s=document.getElementById('status'),c=document.getElementById('code'),b=document.getElementById('copy');p.onclick=async()=>{p.disabled=true;try{const r=await fetch('/api/pair',{method:'POST',headers:{'Content-Type':'application/json','X-Jarvis-Link':'pair'},body:'{}'});const j=await r.json();if(!r.ok)throw Error(j.error);c.value=j.code;c.hidden=false;b.hidden=false;s.textContent='Codice pronto. Copialo nell’app.'}catch(e){s.textContent='Collegamento non pronto: '+e.message}finally{p.disabled=false}};b.onclick=async()=>{try{await navigator.clipboard.writeText(c.value);s.textContent='Copiato. Incollalo in JarvisWake.'}catch{c.select();s.textContent='Seleziona e copia il codice.'}};document.getElementById('revoke').onclick=async()=>{if(!confirm('Revocare tutti i collegamenti di questo utente? I telefoni dovranno essere abbinati di nuovo.'))return;try{const r=await fetch('/api/revoke',{method:'POST',headers:{'Content-Type':'application/json','X-Jarvis-Link':'pair'},body:'{}'});if(!r.ok)throw Error('revoca non riuscita');c.value='';c.hidden=true;b.hidden=true;s.textContent='Collegamenti revocati.'}catch(e){s.textContent=e.message}};</script></html>`,{headers:{'Content-Type':'text/html;charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"}});
}
export default {async fetch(req,env) {
  try {
    const url=new URL(req.url);
    if(url.pathname==='/mcp' && req.method==='POST') return await mcp(req,env);
    if(url.pathname==='/' && req.method==='GET') { owner(req); return page(); }
    if(url.pathname==='/api/revoke' && req.method==='POST') {
      const uid=owner(req);
      if(req.headers.get('origin')!==url.origin || req.headers.get('X-Jarvis-Link')!=='pair') fail('origin_required',403);
      await body(req);
      const hash=await digest(uid);
      for(const d of await devices(env,uid)) {
        await env.BUCKET.delete(deviceKey(d.deviceId));
        await env.BUCKET.delete(`owners/${hash}/${d.deviceId}.json`);
      }
      return json({state:'revoked'});
    }
    if(url.pathname==='/api/pair' && req.method==='POST') {
      const uid=owner(req);
      if(req.headers.get('origin')!==url.origin || req.headers.get('X-Jarvis-Link')!=='pair') fail('origin_required',403);
      if(!env.SITE_DEVICE_ACCESS) fail('service_access_not_configured',503);
      await body(req);
      if((await devices(env,uid)).length>=4) fail('device_limit_reached',409);
      const deviceId=crypto.randomUUID(),token=secret(),hash=await digest(uid);
      const d={deviceId,owner:hash,tokenHash:await digest(token),createdAt:Date.now()};
      await put(env,deviceKey(deviceId),d); await put(env,`owners/${hash}/${deviceId}.json`,{deviceId});
      const envelope={v:1,origin:url.origin,deviceId,deviceToken:token,siteAccess:env.SITE_DEVICE_ACCESS};
      return json({code:'JW1.'+btoa(JSON.stringify(envelope))});
    }
    if(['/device/poll','/device/respond','/device/disconnect'].includes(url.pathname) && req.method==='POST') {
      const data=await body(req),d=await deviceForToken(env,req,data.deviceId);
      if(url.pathname==='/device/disconnect') {
        await env.BUCKET.delete(deviceKey(d.deviceId)); await env.BUCKET.delete(`owners/${d.owner}/${d.deviceId}.json`);
        return json({state:'disconnected'});
      }
      if(url.pathname==='/device/poll') {
        const page=await env.BUCKET.list({prefix:`requests/${d.deviceId}/`,limit:100});
        for(const o of page.objects) {
          const c=await get(env,o.key);
          if(c?.expiresAt>Date.now() && !await get(env,resultKey(d.deviceId,c.requestId))) return json({request:c});
        }
        return json({request:null});
      }
      if(!UUID.test(data.requestId)) fail('invalid_request');
      const c=await get(env,requestKey(d.deviceId,data.requestId));
      if(!c || c.expiresAt<Date.now() || c.operation!=='status') fail('request_expired',409);
      const clean=snapshot(data.snapshot),key=resultKey(d.deviceId,data.requestId);
      const result={state:'responded',source:'paired_device',device_id:d.deviceId,request_id:c.requestId,received_at:Date.now(),snapshot:clean};
      // Conditional creation: duplicate acknowledgements never overwrite a first answer.
      await env.BUCKET.put(key,JSON.stringify(result),{onlyIf:{etagDoesNotMatch:'*'}});
      await put(env,`owners/${d.owner}/${d.deviceId}.json`,{deviceId:d.deviceId,lastSeen:Date.now()});
      return json({state:'accepted',request_id:c.requestId});
    }
    return json({error:'not_found'},404);
  } catch(e) { return json({error:e?.code||'service_error'},e?.status||500); }
}};
