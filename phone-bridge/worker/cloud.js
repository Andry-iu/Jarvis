// Cloud Jarvis queue. R2 conditional writes are the cross-instance lock; no lease stealing.
const CUUID=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const CHEX=/^[0-9a-f]{64}$/;
const CREF=/^(main|[0-9a-f]{40})$/;
const CROOT='cloud/v1/';
const CMAX=128*1024*1024;
const CCERT='a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a';
const cfail=(code,status=400)=>{throw {code,status};};
const cjson=(v,status=200)=>new Response(JSON.stringify(v),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
async function chash(s){return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,'0')).join('');}
const ckey=(o,id)=>`${CROOT}jobs/${o}/${id}.json`;
const cpending=(o,id)=>`${CROOT}pending/${o}/${id}.json`;
async function cread(env,key){const obj=await env.BUCKET.get(key);return obj?{value:await obj.json(),etag:obj.etag}:null;}
const cput=(env,key,v,etag)=>env.BUCKET.put(key,JSON.stringify(v),{onlyIf:etag?{etagMatches:etag}:{etagDoesNotMatch:'*'}});
function cfields(v,fields){if(!v || typeof v!=='object'||Array.isArray(v)||Object.keys(v).sort().join(',')!==fields.split(',').sort().join(','))cfail('cloud_invalid_arguments');}
async function cbody(req){if(!req.headers.get('content-type')?.startsWith('application/json'))cfail('json_required',415);const reader=req.body?.getReader();if(!reader)cfail('invalid_json');let parts=[],n=0;try{for(;;){const r=await reader.read();if(r.done)break;n+=r.value.length;if(n>8192)cfail('body_too_large',413);parts.push(r.value);}}finally{await reader.cancel();}try{return JSON.parse(new TextDecoder().decode(new Uint8Array(await new Blob(parts).arrayBuffer())));}catch{cfail('invalid_json');}}
async function cjob(env,o,id){if(!CUUID.test(id))cfail('cloud_invalid_job');const row=await cread(env,ckey(o,id));if(!row)cfail('cloud_job_not_found',404);return row;}
async function cview(env,j){const heartbeat=(await cread(env,CROOT+'heartbeat.json'))?.value;const age=heartbeat?Math.max(0,Date.now()-heartbeat.at):null;return {...j,state:j.state==='running' && (age===null||age>45000)?'awaiting_runner':j.state,runner_online:age!==null&&age<=45000,runner_age_ms:age};}
const ctool=(name,description,properties={},required=[],read=true)=>({name,description,inputSchema:{type:'object',properties,required,additionalProperties:false},annotations:{readOnlyHint:read,destructiveHint:false,openWorldHint:!read}});
export const cloudTools=[
 ctool('jarviswake_cloud_status','Stato esecutore cloud Jarvis. Online richiede heartbeat recente; cloud sospeso/offline lascia le richieste in coda. Non risveglia il cloud.'),
 ctool('jarviswake_cloud_start','Accoda verifiche o APK Telefono con firma permanente, solo Andry-iu/Jarvis main o SHA completo appartenente a main. UUID nuovo per richiesta esplicita; riusare lo stesso dopo timeout. Nessun comando shell, Watch o agente Windows. main viene risolto una volta dal runner. queued non prova esecuzione. Compilazione e firma nel cloud Jarvis; nessun SLA a chat chiusa.',{operation:{type:'string',enum:['verify','build_signed']},revision:{type:'string',pattern:'^(main|[0-9a-f]{40})$'},request_id:{type:'string',format:'uuid'}},['operation','revision','request_id'],false),
 ctool('jarviswake_cloud_job','Legge un job cloud di questo utente. awaiting_runner/outcome_uncertain non sono fallimenti ripetibili: mai avviare automaticamente una seconda build. Errori e conteggi tecnici, nessun log privato.',{job_id:{type:'string',format:'uuid'}},['job_id']),
 ctool('jarviswake_cloud_cancel','Annulla soltanto un job ancora in coda. Un job già claimed/running non viene interrotto né ripetuto.',{job_id:{type:'string',format:'uuid'}},['job_id'],false),
 ctool('jarviswake_cloud_artifact','Restituisce link privato APK solo dopo test, firma permanente, verifica checksum e registrazione del verbale su main. Download richiede accesso al Site con lo stesso account. Scade dopo 7 giorni. Nessun APK su Git.',{job_id:{type:'string',format:'uuid'}},['job_id'])
];
export async function cloudCall(env,uid,name,args,origin){
 const o=await chash(uid);if(name==='jarviswake_cloud_status'){cfields(args,'');const h=(await cread(env,CROOT+'heartbeat.json'))?.value;const a=(await cread(env,CROOT+'active.json'))?.value;return {repository:'Andry-iu/Jarvis',runner_online:!!h&&Date.now()-h.at<=45000,last_seen:h?.at||null,active_job:a?.state==='busy'&&a.owner===o?a.job_id:null,always_on:false};}
 if(name==='jarviswake_cloud_start'){
  cfields(args,'operation,revision,request_id');if(!['verify','build_signed'].includes(args.operation)||!CREF.test(args.revision)||!CUUID.test(args.request_id))cfail('cloud_invalid_arguments');
  const key=ckey(o,args.request_id);let row=await cread(env,key);
  if(row && (row.value.operation!==args.operation||row.value.revision!==args.revision))cfail('cloud_request_id_conflict',409);
  if(!row){const p=await env.BUCKET.list({prefix:CROOT+'pending/',limit:65});if(p.objects.length>=64)cfail('cloud_queue_full',409);await cput(env,key,{job_id:args.request_id,operation:args.operation,revision:args.revision,state:'queued',created_at:Date.now(),stage:'queued'});row=await cread(env,key);}
  if(row.value.operation!==args.operation||row.value.revision!==args.revision)cfail('cloud_request_id_conflict',409);
  if(row.value.state==='queued')await cput(env,cpending(o,args.request_id),{owner:o,job_id:args.request_id});return cview(env,row.value);
 }
 if(!['jarviswake_cloud_job','jarviswake_cloud_cancel','jarviswake_cloud_artifact'].includes(name))cfail('cloud_invalid_tool');
 cfields(args,'job_id');let row=await cjob(env,o,args.job_id);
 if(name==='jarviswake_cloud_cancel'){
  if(row.value.state==='cancelled')return cview(env,row.value);
  if(row.value.state!=='queued')cfail('cloud_already_claimed',409);
  if(!await cput(env,ckey(o,args.job_id),{...row.value,state:'cancelled',stage:'cancelled',finished_at:Date.now()},row.etag))cfail('cloud_state_changed',409);
  await env.BUCKET.delete(cpending(o,args.job_id));row=await cjob(env,o,args.job_id);
 }
 if(name==='jarviswake_cloud_artifact'){
  const a=row.value.artifact;if(row.value.state!=='succeeded'||!a)cfail('cloud_artifact_unavailable',409);
  if(a.expires_at<Date.now()){await env.BUCKET.delete(`${CROOT}apk/${o}/${args.job_id}.apk`);cfail('cloud_artifact_expired',410);}
  return {job_id:args.job_id,resolved_commit:row.value.resolved_commit,release_commit:row.value.release_commit,...a,url:`${origin}/cloud/artifacts/${o}/${args.job_id}.apk`,private:true};
 }
 return cview(env,row.value);
}
async function cauth(req,env){const token=req.headers.get('authorization')?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1];if(!token||!CHEX.test(env.CLOUD_RUNNER_TOKEN_HASH||'')||await chash(token)!==env.CLOUD_RUNNER_TOKEN_HASH)cfail('cloud_runner_unauthorized',401);}
function cidentity(d){if(!CHEX.test(d.owner)||!CUUID.test(d.job_id)||!CUUID.test(d.claim_id))cfail('cloud_invalid_claim');}
async function clock(env,d){const a=await cread(env,CROOT+'active.json');if(!a||a.value.state!=='busy'||['owner','job_id','claim_id'].some(k=>a.value[k]!==d[k]))cfail('cloud_claim_mismatch',409);return a;}
async function crelease(env,a){await cput(env,CROOT+'active.json',{state:'idle'},a.etag);}
const cstages=['claimed','fetch','checks','android','signing','recording','upload','complete'];
const cerrors=['fetch_failed','revision_not_on_main','checks_failed','android_failed','signing_failed','recording_failed','upload_failed','outcome_uncertain','runner_state_missing'];
function ccounts(v){if(!v||typeof v!=='object'||Array.isArray(v)||Object.keys(v).some(k=>!['android_tests','android_failures','android_skipped','lint_errors','lint_warnings','worker_tests','phonebook_tests'].includes(k))||Object.values(v).some(n=>!Number.isInteger(n)||n<0||n>100000))cfail('cloud_invalid_counts');return v;}
export async function cloudRoute(req,env,url){
 if(!url.pathname.startsWith('/cloud/'))return null;
 const m=url.pathname.match(/^\/cloud\/artifacts\/([0-9a-f]{64})\/([0-9a-f-]{36})\.apk$/);
 if(m&&req.method==='GET'){
  const uid=req.headers.get('oai-authenticated-user-id');if(!uid||await chash(uid)!==m[1])cfail('sign_in_required',401);
  const j=(await cjob(env,m[1],m[2])).value;if(j.state!=='succeeded'||!j.artifact)cfail('cloud_artifact_unavailable',409);
  if(j.artifact.expires_at<Date.now())cfail('cloud_artifact_expired',410);
  const apk=await env.BUCKET.get(`${CROOT}apk/${m[1]}/${m[2]}.apk`);if(!apk)cfail('cloud_artifact_unavailable',404);
  return new Response(apk.body,{headers:{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':`attachment; filename="JarvisWake-${j.artifact.version}-permanent.apk"`,'Content-Length':String(j.artifact.bytes),'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'}});
 }
 await cauth(req,env);
 if(url.pathname==='/cloud/runner/artifact'&&req.method==='PUT'){
  const d={owner:req.headers.get('x-job-owner'),job_id:req.headers.get('x-job-id'),claim_id:req.headers.get('x-claim-id')};cidentity(d);await clock(env,d);
  const j=(await cjob(env,d.owner,d.job_id)).value,a=j.artifact;
  if(j.state!=='running'||j.operation!=='build_signed'||!a||!j.release_commit)cfail('cloud_artifact_not_prepared',409);
  if(Number(req.headers.get('content-length'))!==a.bytes||!req.body)cfail('cloud_invalid_artifact');
  // Streaming avoids retaining a ~90 MiB APK in the Worker heap. R2 checks the supplied digest.
  const key=`${CROOT}apk/${d.owner}/${d.job_id}.apk`;
  if(!await env.BUCKET.head(key))await env.BUCKET.put(key,req.body,{sha256:a.sha256,onlyIf:{etagDoesNotMatch:'*'},httpMetadata:{contentType:'application/vnd.android.package-archive'}});
  return cjson({state:'uploaded'});
 }
 if(req.method!=='POST')cfail('not_found',404);const d=await cbody(req);
 await env.BUCKET.put(CROOT+'heartbeat.json',JSON.stringify({at:Date.now()}));
 if(url.pathname==='/cloud/runner/poll'){
  cfields(d,'');const a=(await cread(env,CROOT+'active.json'))?.value;
  if(a?.state==='busy')return cjson({active:a,job:(await cjob(env,a.owner,a.job_id)).value});
  const p=await env.BUCKET.list({prefix:CROOT+'pending/',limit:100});
  for(const item of p.objects){const entry=(await cread(env,item.key))?.value;if(!entry)continue;const row=await cjob(env,entry.owner,entry.job_id);
   if(row.value.state!=='queued'){await env.BUCKET.delete(item.key);continue;}
   if(Date.now()-row.value.created_at>86400000){await cput(env,ckey(entry.owner,entry.job_id),{...row.value,state:'expired',stage:'expired'},row.etag);await env.BUCKET.delete(item.key);continue;}
   return cjson({next:entry,job:row.value});
  }return cjson({next:null});
 }
 cidentity(d);
 if(url.pathname==='/cloud/runner/claim'){
  cfields(d,'owner,job_id,claim_id');const initial=await cjob(env,d.owner,d.job_id);if(initial.value.state==='queued'&&Date.now()-initial.value.created_at>86400000)cfail('cloud_job_expired',409);let a=await cread(env,CROOT+'active.json');
  if(a?.value.state==='busy'){if(['owner','job_id','claim_id'].some(k=>a.value[k]!==d[k]))cfail('cloud_busy',409);}
  else {if(!await cput(env,CROOT+'active.json',{...d,state:'busy'},a?.etag))cfail('cloud_busy',409);a=await clock(env,d);}
  let row=await cjob(env,d.owner,d.job_id);
  if(row.value.state==='queued'){
   if(!await cput(env,ckey(d.owner,d.job_id),{...row.value,state:'running',stage:'claimed',claim_id:d.claim_id,started_at:Date.now()},row.etag))cfail('cloud_state_changed',409);
   row=await cjob(env,d.owner,d.job_id);
  }
  if(row.value.state!=='running'||row.value.claim_id!==d.claim_id){await crelease(env,a);cfail('cloud_already_terminal',409);}
  await env.BUCKET.delete(cpending(d.owner,d.job_id));return cjson({job:row.value});
 }
 if(url.pathname==='/cloud/runner/finish'){
  cfields(d,'owner,job_id,claim_id,state,error');if(!['succeeded','failed','outcome_uncertain'].includes(d.state)||!(d.error===null||cerrors.includes(d.error))||(d.state==='succeeded')!==(d.error===null))cfail('cloud_invalid_result');
  let row=await cjob(env,d.owner,d.job_id);
  if(row.value.state!=='running'){
   if(row.value.claim_id!==d.claim_id||row.value.state!==d.state||row.value.error!==d.error)cfail('cloud_terminal_conflict',409);
   const a=await cread(env,CROOT+'active.json');if(a?.value.claim_id===d.claim_id&&a.value.job_id===d.job_id)await crelease(env,a);return cjson({state:row.value.state});
  }
  const a=await clock(env,d);
  if(d.state==='succeeded' && (!row.value.resolved_commit || (row.value.operation==='build_signed'&&(!row.value.release_commit||!row.value.artifact||!await env.BUCKET.head(`${CROOT}apk/${d.owner}/${d.job_id}.apk`)))))cfail('cloud_incomplete_result',409);
  if(!await cput(env,ckey(d.owner,d.job_id),{...row.value,state:d.state,error:d.error,stage:'complete',finished_at:Date.now()},row.etag))cfail('cloud_state_changed',409);
  await crelease(env,a);return cjson({state:d.state});
 }
 if(url.pathname==='/cloud/runner/progress'){
  cfields(d,'owner,job_id,claim_id,stage,resolved_commit,counts,artifact,release_commit');await clock(env,d);const row=await cjob(env,d.owner,d.job_id);
  if(row.value.state!=='running')cfail('cloud_already_terminal',409);
  if(!cstages.includes(d.stage)||cstages.indexOf(d.stage)<cstages.indexOf(row.value.stage)||!(d.resolved_commit===null||/^[0-9a-f]{40}$/.test(d.resolved_commit))||!(d.release_commit===null||/^[0-9a-f]{40}$/.test(d.release_commit)))cfail('cloud_invalid_progress');
  if(row.value.resolved_commit&&row.value.resolved_commit!==d.resolved_commit)cfail('cloud_revision_changed',409);
  if(row.value.release_commit&&row.value.release_commit!==d.release_commit)cfail('cloud_release_changed',409);
  ccounts(d.counts);
  if(d.artifact!==null){const a=d.artifact;cfields(a,'version,version_code,bytes,sha256,certificate_sha256,expires_at');if(row.value.operation!=='build_signed'||!/^\d+\.\d+\.\d+$/.test(a.version)||!Number.isInteger(a.version_code)||a.version_code<1||!Number.isInteger(a.bytes)||a.bytes<1||a.bytes>CMAX||!CHEX.test(a.sha256)||a.certificate_sha256!==CCERT||!Number.isInteger(a.expires_at)||a.expires_at<=Date.now()||a.expires_at>Date.now()+7*86400000)cfail('cloud_invalid_artifact');if(row.value.artifact&&JSON.stringify(row.value.artifact)!==JSON.stringify(a))cfail('cloud_artifact_changed',409);}
  if(row.value.artifact&&d.artifact===null)cfail('cloud_artifact_changed',409);
  if(!await cput(env,ckey(d.owner,d.job_id),{...row.value,stage:d.stage,resolved_commit:d.resolved_commit,counts:d.counts,artifact:d.artifact,release_commit:d.release_commit,updated_at:Date.now()},row.etag))cfail('cloud_state_changed',409);
  return cjson({state:'accepted'});
 }
 cfail('not_found',404);
}
