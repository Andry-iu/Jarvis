'use strict';
// No dependencies, inference, billing endpoints, implicit retries or secret logging.
const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const crypto = require('node:crypto');
const fail = code => { throw Object.assign(new Error(code), {code}); };
const stable = x => JSON.stringify(x, (_, v) => v && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.keys(v).sort().map(k => [k,v[k]])) : v);
const hash = x => crypto.createHash('sha256').update(stable(x)).digest('hex');
const uuid = x => typeof x === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(x);
const displayName = x => typeof x === 'string' && /^[A-Za-z0-9][A-Za-z0-9 _.-]{0,99}$/.test(x);
function defaultName(input){
  if(input.device_name!==undefined){if(typeof input.device_name!=='string'||!/^[A-Za-z0-9][A-Za-z0-9 _-]{0,39}$/.test(input.device_name.trim()))fail('CF_DEVICE_NAME_INVALID');return 'jarvis-'+input.device_name.trim().toLowerCase().replace(/[ _]+/g,'-').replace(/-+$/,'');}
  return input.label;
}
const id = x => typeof x === 'string' && /^[a-f0-9]{32}$/i.test(x);
function atomic(file, value) {
  const tmp = file + '.new-' + crypto.randomUUID();
  const fd = fs.openSync(tmp, 'wx', 0o600);
  try {fs.writeFileSync(fd, JSON.stringify(value, null, 2)+'\n');fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
  if (fs.existsSync(file)) fs.copyFileSync(file, file+'.previous');
  fs.renameSync(tmp, file);
}
function nativeTransport(token, method, endpoint, body) {
  return new Promise((resolve, reject) => {
    const data = body === undefined ? null : JSON.stringify(body);
    const req = https.request({hostname:'api.cloudflare.com', port:443, path:'/client/v4'+endpoint, method,
      headers:{Authorization:'Bearer '+token,'Content-Type':'application/json',...(data?{'Content-Length':Buffer.byteLength(data)}:{})}}, res => {
      let raw='',size=0;
      res.on('data', chunk => {size+=chunk.length;if(size>4*1024*1024){req.destroy();reject(Object.assign(new Error('CF_RESPONSE_TOO_LARGE'),{code:'CF_RESPONSE_TOO_LARGE'}));}else raw+=chunk;});
      res.on('end', () => {let json;try{json=JSON.parse(raw);}catch{json=null;}resolve({status:res.statusCode,json});});
      res.on('error',()=>reject(Object.assign(new Error('CF_NETWORK'),{code:'CF_NETWORK'})));
    });
    req.setTimeout(30000,()=>req.destroy());
    req.on('error',()=>reject(Object.assign(new Error('CF_NETWORK'),{code:'CF_NETWORK'})));
    if(data)req.write(data);req.end();
  });
}
class Client {
  constructor(token, transport=nativeTransport){if(typeof token!=='string'||!/^[-A-Za-z0-9_]{20,512}$/.test(token))fail('CF_TOKEN_FORMAT');this.token=token;this.transport=transport;}
  async call(method, endpoint, body){
    let r;try{r=await this.transport(this.token,method,endpoint,body);}catch{fail('CF_NETWORK');}
    if(r.status<200||r.status>=300||!r.json||r.json.success!==true){
      const code='CF_HTTP_'+r.status;
      throw Object.assign(new Error(code),{code,definite:r.status>=400&&r.status<500&&r.status!==408});
    }
    return r.json;
  }
  async get(endpoint){return (await this.call('GET',endpoint)).result;}
  async list(endpoint){
    const all=[];for(let page=1;page<=100;page++){
      const r=await this.call('GET',endpoint+(endpoint.includes('?')?'&':'?')+'per_page=50&page='+page);
      if(!Array.isArray(r.result))fail('CF_LIST_FORMAT');all.push(...r.result);
      const pages=r.result_info?.total_pages;
      if(pages!==undefined){if(!Number.isInteger(pages)||pages>100)fail('CF_LIST_LIMIT');if(page>=pages)return all;}
      else if(r.result.length<50)return all;
    }fail('CF_LIST_LIMIT');
  }
  async discover(){
    if((await this.get('/user/tokens/verify'))?.status!=='active')fail('CF_TOKEN_INACTIVE');
    const zones=(await this.list('/zones?status=active')).map(z=>({id:z.id,name:z.name,account_id:z.account?.id,account_name:z.account?.name||''}));
    if(!zones.length||zones.some(z=>!id(z.id)||!id(z.account_id)||!/^[a-z0-9.-]+$/.test(z.name)))fail('CF_NO_VALID_ZONES');
    return {zones};
  }
}
const oauth = {enabled:true,dynamic_client_registration:{enabled:true,allow_any_on_localhost:false,allow_any_on_loopback:false,allowed_uris:['https://chatgpt.com/connector/oauth/*']},grant:{access_token_lifetime:'15m',session_duration:'24h'}};
class Provisioner {
  constructor(client, input){this.c=client;this.i=input;this.file=path.join(input.state_dir,'cloudflare-auto.json');}
  save(){if(this.readOnly)return;this.s.updated_at=new Date().toISOString();atomic(this.file,this.s);}
  async run(){
    const i=this.i;
    if(!i.identity||!i.identity.computer||!i.identity.user_sid||!i.identity.home||!Number.isInteger(i.port)||i.port<1024||i.port>65535)fail('CF_IDENTITY');
    if(!/^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$/.test(i.label)||!/^[-A-Za-z0-9._+]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$/.test(i.email))fail('CF_INPUT');
    const d=await this.c.discover(),z=d.zones.find(z=>z.id===i.zone_id);if(!z)fail('CF_ZONE_SCOPE');
    const hostname=i.label+'.'+z.name,root='/accounts/'+z.account_id;
    const org=await this.c.get(root+'/access/organizations');
    if(!/^[a-z0-9-]+\.cloudflareaccess\.com$/.test(org?.auth_domain||''))fail('CF_TEAM_NOT_CONFIGURED');
    this.tunnelName=i.tunnel_name||defaultName(i);this.policyName=i.policy_name||defaultName(i);this.appName=i.application_name||defaultName(i);
    if(!displayName(this.tunnelName)||!displayName(this.policyName)||!displayName(this.appName))fail('CF_DISPLAY_NAME_INVALID');
    this.desired={zone_id:z.id,zone:z.name,account_id:z.account_id,hostname,email:i.email.toLowerCase(),port:i.port,team:'https://'+org.auth_domain};
    if(fs.existsSync(this.file)){
      this.s=JSON.parse(fs.readFileSync(this.file,'utf8'));
      if(this.s.format!==1||stable(this.s.identity)!==stable(i.identity)||stable(this.s.desired)!==stable(this.desired)||!/^jarvis-[a-f0-9]{24}$/.test(this.s.owner))fail('CF_RESUME_IDENTITY_OR_INPUT_CHANGED');
    }else{this.s={format:1,identity:i.identity,desired:this.desired,owner:'jarvis-'+crypto.randomBytes(12).toString('hex'),creation_names:{tunnel:this.tunnelName,policy:this.policyName,application:this.appName},steps:{}};this.save();}
    this.root=root;this.dns='/zones/'+z.id+'/dns_records';this.apps=root+'/access/apps';this.tunnels=root+'/cfd_tunnel';
    await this.preflight();
    const policy={name:this.s.creation_names?.policy||this.s.owner,decision:'allow',include:[{email:{email:this.desired.email}}],exclude:[],require:[],precedence:1};
    const appBody={name:this.s.creation_names?.application||this.s.owner,type:'self_hosted',domain:hostname,session_duration:'12h',app_launcher_visible:false,oauth_configuration:oauth,policies:[policy]};
    const app=await this.ensure('app',this.apps,appBody,async()=> (await this.c.list(this.apps)).filter(a=>a.name===(this.s.creation_names?.application||this.s.owner)),a=>this.checkApp(a));
    await this.renameApp(app);
    await this.renamePolicy(app);
    const tunnel=await this.ensureTunnel();
    await this.renameTunnel(tunnel);
    const configuration={config:{ingress:[{hostname,service:'http://127.0.0.1:'+i.port,originRequest:{}},{service:'http_status:404'}]}};
    await this.configureTunnel(tunnel.id,configuration);
    // Check hostname conflicts and the full policy again before publishing DNS.
    await this.preflight();await this.checkApp(await this.c.get(this.apps+'/'+app.id));
    await this.ensure('dns',this.dns,{type:'CNAME',name:hostname,content:tunnel.id+'.cfargotunnel.com',proxied:true,ttl:1,comment:this.s.owner},async()=> (await this.c.list(this.dns+'?name='+encodeURIComponent(hostname))).filter(r=>r.comment===this.s.owner),async r=>{
      if(!id(r.id)||r.name!==hostname||r.type!=='CNAME'||r.content!==tunnel.id+'.cfargotunnel.com'||r.proxied!==true||r.comment!==this.s.owner)fail('CF_DNS_DRIFT');
    });
    // Validate all observed objects before exporting local credentials.
    await this.preflight();await this.checkApp(await this.c.get(this.apps+'/'+app.id));
    const token=await this.c.get(this.tunnels+'/'+tunnel.id+'/token');
    let meta;try{meta=JSON.parse(Buffer.from(token,'base64').toString('utf8'));}catch{fail('CF_TUNNEL_TOKEN_FORMAT');}
    if(typeof token!=='string'||token.length>4096||!/^[-A-Za-z0-9_+/=]+$/.test(token)||meta.t!==tunnel.id||meta.a!==z.account_id)fail('CF_TUNNEL_TOKEN_MISMATCH');
    const tokenPath=path.join(i.config_dir,'tunnel-token.txt');
    if(fs.existsSync(tokenPath)){if(fs.readFileSync(tokenPath,'utf8')!==token)fail('CF_LOCAL_TOKEN_CONFLICT');}
    else{const tmp=tokenPath+'.new-'+crypto.randomUUID();const fd=fs.openSync(tmp,'wx',0o600);try{fs.writeFileSync(fd,token);fs.fsyncSync(fd);}finally{fs.closeSync(fd);}fs.renameSync(tmp,tokenPath);}
    const output={hostname:'https://'+hostname,team:this.desired.team,aud:app.aud,tunnel_id:tunnel.id,account_id:z.account_id,zone_id:z.id,tunnel_name:this.tunnelName,policy_name:this.policyName,application_name:this.appName,provisioned:true,remote_verified:false};
    atomic(path.join(i.config_dir,'cloudflare-pending.json'),output);this.s.completed=true;this.save();return output;
  }
  async checkApp(a){
    const o=a.oauth_configuration,d=o?.dynamic_client_registration;
    if(!uuid(a.id)||(this.s.steps.app?.id?a.id!==this.s.steps.app.id:(this.s.creation_names||a.name!==this.s.owner))||a.domain!==this.desired.hostname||a.type!=='self_hosted'||!/^[a-f0-9]{64}$/i.test(a.aud||'')||o?.enabled!==true||d?.enabled!==true||d.allow_any_on_localhost!==false||d.allow_any_on_loopback!==false||stable(d.allowed_uris)!==stable(oauth.dynamic_client_registration.allowed_uris)||stable(o.grant)!==stable(oauth.grant))fail('CF_ACCESS_DRIFT');
    if(this.s.app_aud&&this.s.app_aud!==a.aud)fail('CF_APP_AUD_CHANGED');
    if(!this.s.app_aud){this.s.app_aud=a.aud;this.save();}
    const policies=await this.c.list(this.apps+'/'+a.id+'/policies');
    if(policies.length!==1)fail('CF_ACCESS_POLICY_DRIFT');
    const p=policies[0];this.checkPolicy(p);
    if(this.s.policy_id&&this.s.policy_id!==p.id)fail('CF_POLICY_ID_CHANGED');
    if(!this.s.policy_id){this.s.policy_id=p.id;this.save();}
    return p;
  }
  checkPolicy(p){
    if(!uuid(p?.id)||p.reusable===true||p.decision!=='allow'||stable(p.include)!==stable([{email:{email:this.desired.email}}])||(p.exclude||[]).length||(p.require||[]).length)fail('CF_ACCESS_POLICY_DRIFT');
  }
  checkTunnel(t,expectedID){
    if(!uuid(t?.id)||(expectedID&&t.id!==expectedID)||t.deleted_at||(t.account_tag&&t.account_tag!==this.desired.account_id)||(t.config_src!==undefined?t.config_src!=='cloudflare':t.remote_config!==true))fail('CF_TUNNEL_DRIFT');
  }
  async ensureTunnel(){
    const name=this.s.creation_names?.tunnel||this.s.owner,body={name,config_src:'cloudflare'};
    const step=this.s.steps.tunnel;
    if(step){
      if(step.body_hash!==hash(body))fail('CF_RESUME_BODY_CHANGED');
      if(step.id){
        if(!uuid(step.id))fail('CF_TUNNEL_ID_INVALID');
        const t=await this.c.get(this.tunnels+'/'+step.id);this.checkTunnel(t,step.id);
        step.phase='complete';this.save();return t;
      }
      const found=(await this.c.list(this.tunnels+'?is_deleted=false')).filter(t=>t.name===name);
      // Legacy random names are ownership markers; human names are not.
      if(found.length&&this.s.creation_names)fail('CF_UNCERTAIN_TUNNEL_ID_NO_REPLAY');
      if(found.length>1)fail('CF_MULTIPLE_OWNED_RESOURCES');
      if(found.length){
        const t=await this.c.get(this.tunnels+'/'+found[0].id);this.checkTunnel(t,found[0].id);
        step.id=t.id;step.phase='complete';this.save();return t;
      }
      if(step.phase!=='failed')fail('CF_UNCERTAIN_TUNNEL_NO_REPLAY');
    }
    if((await this.c.list(this.tunnels+'?is_deleted=false')).some(t=>t.name===name))fail('CF_TUNNEL_NAME_CONFLICT');
    const intent=this.s.steps.tunnel={phase:'intent',body_hash:hash(body)};this.save();
    let t;try{t=(await this.c.call('POST',this.tunnels,body)).result;}
    catch(e){intent.phase=e.definite?'failed':'uncertain';this.save();throw e;}
    if(!uuid(t?.id))fail('CF_CREATE_RESPONSE_UNCERTAIN');
    intent.id=t.id;this.save();
    t=await this.c.get(this.tunnels+'/'+t.id);this.checkTunnel(t,intent.id);
    intent.phase='complete';this.save();return t;
  }
  async renameKnown(key,target,current,method,endpoint,body,read,verify){
    this.s.renames ||= {};const previous=this.s.renames[key];
    const normalize=x=>{const copy={...x};delete copy.name;return copy;};
    const unchanged=hash(normalize(body));
    // The prior mutation must be reconciled before accepting a new target.
    if(previous&&['intent','uncertain'].includes(previous.phase)){
      if(current.name===previous.to&&unchanged===previous.fields_hash){previous.phase='complete';this.save();}
      else fail('CF_UNCERTAIN_RENAME_'+key.toUpperCase()+'_NO_REPLAY');
    }
    if(current.name===target)return false;
    const latest=await read();await verify(latest);
    if(latest.name!==current.name)fail('CF_RENAME_CONCURRENT_CHANGE');
    const receipt=this.s.renames[key]={phase:'intent',from:current.name,to:target,fields_hash:unchanged};this.save();
    try{await this.c.call(method,endpoint,body);}catch(e){receipt.phase=e.definite?'failed':'uncertain';this.save();throw e;}
    const actual=await read();await verify(actual);
    if(actual.name!==target)fail('CF_RENAME_READBACK');
    receipt.phase='complete';this.save();return true;
  }
  async renameTunnel(t){
    this.checkTunnel(t,this.s.steps.tunnel.id);
    if((await this.c.list(this.tunnels+'?is_deleted=false')).some(other=>other.name===this.tunnelName&&other.id!==t.id))fail('CF_TUNNEL_NAME_CONFLICT');
    return this.renameKnown('tunnel',this.tunnelName,t,'PATCH',this.tunnels+'/'+t.id,{name:this.tunnelName},()=>this.c.get(this.tunnels+'/'+t.id),actual=>this.checkTunnel(actual,t.id));
  }
  policyBody(p){
    const editable=['name','decision','include','exclude','require','precedence','approval_groups','approval_required','connection_rules','isolation_required','mfa_config','purpose_justification_prompt','purpose_justification_required','session_duration'];
    const readOnly=['id','account_id','app_id','created_at','updated_at','modified_at','reusable'];
    if(Object.keys(p).some(k=>!editable.includes(k)&&!readOnly.includes(k)))fail('CF_POLICY_FIELDS_UNSUPPORTED');
    return Object.fromEntries(editable.filter(k=>p[k]!==undefined).map(k=>[k,p[k]]));
  }
  appBody(current,policy){
    // Carry existing settings through the PUT. Read-only identity fields stay out
    // of the body; the existing inline policy ID and every rule are retained.
    const body={...current,policies:[{...this.policyBody(policy),id:policy.id}]};
    for(const key of ['id','aud','created_at','updated_at','uid'])delete body[key];
    return body;
  }
  async renameApp(app){
    const policy=await this.checkApp(app),endpoint=this.apps+'/'+app.id;
    const fullPolicy=await this.c.get(endpoint+'/policies/'+policy.id);this.checkPolicy(fullPolicy);
    if(fullPolicy.id!==policy.id)fail('CF_POLICY_ID_CHANGED');
    if(app.name===this.appName&&!['intent','uncertain'].includes(this.s.renames?.application?.phase))return false;
    const body={...this.appBody(app,fullPolicy),name:this.appName};
    return this.renameKnown('application',this.appName,app,'PUT',endpoint,body,()=>this.c.get(endpoint),async actual=>{
      const updatedPolicy=await this.checkApp(actual);
      const updatedFull=await this.c.get(endpoint+'/policies/'+updatedPolicy.id);
      this.checkPolicy(updatedFull);
      if(updatedFull.id!==fullPolicy.id||stable({...this.appBody(actual,updatedFull),name:this.appName})!==stable(body))fail('CF_APP_RENAME_CHANGED_SETTINGS');
    });
  }
  async renamePolicy(app){
    const listed=await this.checkApp(app),endpoint=this.apps+'/'+app.id+'/policies/'+listed.id;
    const current=await this.c.get(endpoint);this.checkPolicy(current);
    if(current.id!==this.s.policy_id)fail('CF_POLICY_ID_CHANGED');
    if(current.name===this.policyName&&!['intent','uncertain'].includes(this.s.renames?.policy?.phase))return false;
    const body={...this.policyBody(current),name:this.policyName};
    return this.renameKnown('policy',this.policyName,current,'PUT',endpoint,body,()=>this.c.get(endpoint),actual=>{
      this.checkPolicy(actual);
      if(actual.id!==current.id||stable({...this.policyBody(actual),name:this.policyName})!==stable(body))fail('CF_POLICY_RENAME_CHANGED_RULES');
    });
  }
  async renameExisting(readOnly=false){
    this.readOnly=readOnly;const i=this.i;
    if(!fs.existsSync(this.file))fail('CF_RENAME_REQUIRES_JOURNAL');
    this.s=JSON.parse(fs.readFileSync(this.file,'utf8'));
    if(this.s.format!==1||stable(this.s.identity)!==stable(i.identity)||!/^jarvis-[a-f0-9]{24}$/.test(this.s.owner))fail('CF_RESUME_IDENTITY_OR_INPUT_CHANGED');
    this.desired=this.s.desired;
    if(!this.s.completed||!uuid(this.s.steps?.tunnel?.id)||!uuid(this.s.steps?.app?.id))fail('CF_RENAME_REQUIRES_COMPLETED_SETUP');
    if(i.expected_tunnel_id&&i.expected_tunnel_id!==this.s.steps.tunnel.id)fail('CF_RENAME_LOCAL_CONFIG_MISMATCH');
    const zones=(await this.c.discover()).zones,z=zones.find(z=>z.id===this.desired.zone_id);
    if(!z||z.account_id!==this.desired.account_id||z.name!==this.desired.zone)fail('CF_ZONE_SCOPE');
    this.root='/accounts/'+z.account_id;this.apps=this.root+'/access/apps';this.tunnels=this.root+'/cfd_tunnel';
    this.tunnelName=i.tunnel_name||defaultName(i);this.policyName=i.policy_name||defaultName(i);this.appName=i.application_name||defaultName(i);
    if(!displayName(this.tunnelName)||!displayName(this.policyName)||!displayName(this.appName))fail('CF_DISPLAY_NAME_INVALID');
    const app=await this.c.get(this.apps+'/'+this.s.steps.app.id);
    if(i.expected_aud&&app.aud!==i.expected_aud)fail('CF_RENAME_LOCAL_CONFIG_MISMATCH');
    await this.checkApp(app);
    // Ensure recorded creation proof remains valid; never select by display name.
    const tunnel=await this.ensureTunnel();
    if(readOnly){const p=await this.checkApp(app);return {tunnel_id:tunnel.id,tunnel_name:tunnel.name,application_id:app.id,application_name:app.name,policy_id:p.id,policy_name:p.name,matches:tunnel.name===this.tunnelName&&app.name===this.appName&&p.name===this.policyName,read_only:true,checked_at:new Date().toISOString()};}
    const appRenamed=await this.renameApp(app),policyRenamed=await this.renamePolicy(app),tunnelRenamed=await this.renameTunnel(tunnel);
    return {tunnel_id:tunnel.id,tunnel_name:this.tunnelName,policy_id:this.s.policy_id,policy_name:this.policyName,application_id:app.id,application_name:this.appName,renamed:tunnelRenamed||policyRenamed||appRenamed,checked_at:new Date().toISOString()};
  }
  async preflight(){
    const records=await this.c.list(this.dns+'?name='+encodeURIComponent(this.desired.hostname));
    if(records.some(r=>r.name!==this.desired.hostname||r.comment!==this.s.owner||!this.s.steps.dns))fail('CF_HOSTNAME_DNS_CONFLICT');
    const apps=await this.c.list(this.apps);
    const host=this.desired.hostname;
    for(const a of apps){
      const domains=[a.domain,...(a.self_hosted_domains||[]),...(a.destinations||[]).map(d=>d.uri||d.hostname)].filter(Boolean);
      const overlaps=domains.some(v=>{const domain=v.replace(/^https?:\/\//,'').split('/')[0].split(':')[0];return domain===host||domain===this.desired.zone||domain.startsWith('*.')&&host.endsWith(domain.slice(1));});
      if(overlaps&&!(this.s.steps.app&&(a.id===this.s.steps.app.id||(!this.s.creation_names&&!this.s.steps.app.id&&a.name===this.s.owner))))fail('CF_HOSTNAME_ACCESS_CONFLICT');
    }
  }
  async ensure(key,endpoint,body,lookup,check){
    let step=this.s.steps[key];
    if(step){
      if(step.body_hash!==hash(body))fail('CF_RESUME_BODY_CHANGED');
      if(key==='app'&&step.id){
        if(!uuid(step.id))fail('CF_APP_ID_INVALID');
        const actual=await this.c.get(endpoint+'/'+step.id);await check(actual);step.phase='complete';this.save();return actual;
      }
      const found=await lookup();
      if(key==='app'&&this.s.creation_names&&found.length)fail('CF_UNCERTAIN_APP_ID_NO_REPLAY');if(found.length>1)fail('CF_MULTIPLE_OWNED_RESOURCES');
      if(found.length){
        if(step.id&&step.id!==found[0].id)fail('CF_RESOURCE_ID_CHANGED');
        const actual=await this.c.get(endpoint+'/'+found[0].id);await check(actual);
        step.id=actual.id;step.phase='complete';this.save();return actual;
      }
      if(step.phase!=='failed')fail('CF_UNCERTAIN_'+key.toUpperCase()+'_NO_REPLAY');
    }else if((await lookup()).length)fail('CF_OWNER_COLLISION');
    step=this.s.steps[key]={phase:'intent',body_hash:hash(body)};this.save();
    let result;
    try{result=(await this.c.call('POST',endpoint,body)).result;}
    catch(e){step.phase=e.definite?'failed':'uncertain';this.save();throw e;}
    // Record a known identifier before read-back; a lost response remains an intent.
    if(!result?.id)fail('CF_CREATE_RESPONSE_UNCERTAIN');
    step.id=result.id;this.save();
    const actual=await this.c.get(endpoint+'/'+result.id);await check(actual);
    step.phase='complete';this.save();return actual;
  }
  async configureTunnel(tunnelID,body){
    const endpoint=this.tunnels+'/'+tunnelID+'/configurations';
    let current;try{current=await this.c.get(endpoint);}catch(e){if(e.code!=='CF_HTTP_404')throw e;current={config:{ingress:[]}};}
    const normalize=c=>({config:{...(Object.keys(c?.config?.originRequest||{}).length?{originRequest:c.config.originRequest}:{}),ingress:(c?.config?.ingress||[]).map(x=>({...(x.hostname?{hostname:x.hostname}:{}),...(x.path?{path:x.path}:{}),service:x.service,originRequest:x.originRequest||{}}))}});
    const target=normalize(body),present=normalize(current);let step=this.s.steps.configuration;
    if(stable(present)===stable(target)){if(!step)fail('CF_UNOWNED_CONFIGURATION');step.phase='complete';this.save();return;}
    if(step){
      if(step.body_hash!==hash(body)||step.before_hash!==hash(present))fail('CF_CONFIGURATION_DRIFT');
      if(step.phase!=='failed')fail('CF_UNCERTAIN_CONFIGURATION_NO_REPLAY');
    }else{
      const ingress=present.config.ingress;
      if(present.config.originRequest)fail('CF_CONFIGURATION_DRIFT');
      if(ingress.length&&!(ingress.length===1&&ingress[0].service==='http_status:404'&&!ingress[0].hostname))fail('CF_CONFIGURATION_DRIFT');
    }
    step=this.s.steps.configuration={phase:'intent',body_hash:hash(body),before_hash:hash(present)};this.save();
    try{await this.c.call('PUT',endpoint,body);}catch(e){step.phase=e.definite?'failed':'uncertain';this.save();throw e;}
    if(stable(normalize(await this.c.get(endpoint)))!==stable(target))fail('CF_CONFIGURATION_READBACK');
    step.phase='complete';this.save();
  }
}
async function main(){
  let raw='';for await(const chunk of process.stdin){raw+=chunk;if(raw.length>65536)fail('CF_INPUT_SIZE');}
  let input;try{input=JSON.parse(raw.replace(/^\uFEFF/,''));}catch{fail('CF_INPUT_JSON');}raw='';
  const c=new Client(input.token);input.token=null;
  if(input.command==='discover')return c.discover();
  if(!['provision','rename-resources','inspect-names'].includes(input.command))fail('CF_COMMAND');
  // An independent child can survive a parent loss: the file handle guards
  // duplicate provisioning until this process exits (exclusive create).
  const lock=path.join(input.state_dir,'cloudflare-auto.lock');
  let fd;try{fd=fs.openSync(lock,'wx',0o600);}catch{fail('CF_PROVISION_LOCK_EXISTS');}
  try{fs.writeFileSync(fd,JSON.stringify({pid:process.pid,started_at:new Date().toISOString()}));const p=new Provisioner(c,input);return await (input.command==='provision'?p.run():p.renameExisting(input.command==='inspect-names'));}
  finally{fs.closeSync(fd);fs.unlinkSync(lock);}
}
if(require.main===module)main().then(result=>process.stdout.write(JSON.stringify({ok:true,result}))).catch(e=>{process.stdout.write(JSON.stringify({ok:false,error:/^CF_[A-Z0-9_]+$/.test(e.code||'')?e.code:'CF_LOCAL_FAILURE'}));process.exitCode=1;});
module.exports={Client,Provisioner,oauth,atomic};
