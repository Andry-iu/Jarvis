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
  save(){this.s.updated_at=new Date().toISOString();atomic(this.file,this.s);}
  async run(){
    const i=this.i;
    if(!i.identity||!i.identity.computer||!i.identity.user_sid||!i.identity.home||!Number.isInteger(i.port)||i.port<1024||i.port>65535)fail('CF_IDENTITY');
    if(!/^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$/.test(i.label)||!/^[-A-Za-z0-9._+]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$/.test(i.email))fail('CF_INPUT');
    const d=await this.c.discover(),z=d.zones.find(z=>z.id===i.zone_id);if(!z)fail('CF_ZONE_SCOPE');
    const hostname=i.label+'.'+z.name,root='/accounts/'+z.account_id;
    const org=await this.c.get(root+'/access/organizations');
    if(!/^[a-z0-9-]+\.cloudflareaccess\.com$/.test(org?.auth_domain||''))fail('CF_TEAM_NOT_CONFIGURED');
    this.desired={zone_id:z.id,zone:z.name,account_id:z.account_id,hostname,email:i.email.toLowerCase(),port:i.port,team:'https://'+org.auth_domain};
    if(fs.existsSync(this.file)){
      this.s=JSON.parse(fs.readFileSync(this.file,'utf8'));
      if(this.s.format!==1||stable(this.s.identity)!==stable(i.identity)||stable(this.s.desired)!==stable(this.desired)||!/^jarvis-[a-f0-9]{24}$/.test(this.s.owner))fail('CF_RESUME_IDENTITY_OR_INPUT_CHANGED');
    }else{this.s={format:1,identity:i.identity,desired:this.desired,owner:'jarvis-'+crypto.randomBytes(12).toString('hex'),steps:{}};this.save();}
    this.root=root;this.dns='/zones/'+z.id+'/dns_records';this.apps=root+'/access/apps';this.tunnels=root+'/cfd_tunnel';
    await this.preflight();
    const policy={name:this.s.owner,decision:'allow',include:[{email:{email:this.desired.email}}],exclude:[],require:[],precedence:1};
    const appBody={name:this.s.owner,type:'self_hosted',domain:hostname,session_duration:'12h',app_launcher_visible:false,oauth_configuration:oauth,policies:[policy]};
    const app=await this.ensure('app',this.apps,appBody,async()=> (await this.c.list(this.apps)).filter(a=>a.name===this.s.owner),a=>this.checkApp(a));
    const tunnel=await this.ensure('tunnel',this.tunnels,{name:this.s.owner,config_src:'cloudflare'},async()=> (await this.c.list(this.tunnels+'?is_deleted=false')).filter(t=>t.name===this.s.owner),async t=>{if(!uuid(t.id)||t.name!==this.s.owner||t.deleted_at||(t.config_src!==undefined?t.config_src!=='cloudflare':t.remote_config!==true))fail('CF_TUNNEL_DRIFT');});
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
    const output={hostname:'https://'+hostname,team:this.desired.team,aud:app.aud,tunnel_id:tunnel.id,account_id:z.account_id,zone_id:z.id,provisioned:true,remote_verified:false};
    atomic(path.join(i.config_dir,'cloudflare-pending.json'),output);this.s.completed=true;this.save();return output;
  }
  async checkApp(a){
    const o=a.oauth_configuration,d=o?.dynamic_client_registration;
    if(!uuid(a.id)||a.name!==this.s.owner||a.domain!==this.desired.hostname||a.type!=='self_hosted'||!/^[a-f0-9]{64}$/i.test(a.aud||'')||o?.enabled!==true||d?.enabled!==true||d.allow_any_on_localhost!==false||d.allow_any_on_loopback!==false||stable(d.allowed_uris)!==stable(oauth.dynamic_client_registration.allowed_uris)||stable(o.grant)!==stable(oauth.grant))fail('CF_ACCESS_DRIFT');
    const policies=await this.c.list(this.apps+'/'+a.id+'/policies');
    const p=policies[0];
    if(policies.length!==1||p.name!==this.s.owner||p.decision!=='allow'||stable(p.include)!==stable([{email:{email:this.desired.email}}])||(p.exclude||[]).length||(p.require||[]).length)fail('CF_ACCESS_POLICY_DRIFT');
  }
  async preflight(){
    const records=await this.c.list(this.dns+'?name='+encodeURIComponent(this.desired.hostname));
    if(records.some(r=>r.name!==this.desired.hostname||r.comment!==this.s.owner||!this.s.steps.dns))fail('CF_HOSTNAME_DNS_CONFLICT');
    const apps=await this.c.list(this.apps);
    const host=this.desired.hostname;
    for(const a of apps){
      const domains=[a.domain,...(a.self_hosted_domains||[]),...(a.destinations||[]).map(d=>d.uri||d.hostname)].filter(Boolean);
      const overlaps=domains.some(v=>{const domain=v.replace(/^https?:\/\//,'').split('/')[0].split(':')[0];return domain===host||domain===this.desired.zone||domain.startsWith('*.')&&host.endsWith(domain.slice(1));});
      if(overlaps&&(a.name!==this.s.owner||!this.s.steps.app))fail('CF_HOSTNAME_ACCESS_CONFLICT');
    }
  }
  async ensure(key,endpoint,body,lookup,check){
    let step=this.s.steps[key];
    if(step){
      if(step.body_hash!==hash(body))fail('CF_RESUME_BODY_CHANGED');
      const found=await lookup();if(found.length>1)fail('CF_MULTIPLE_OWNED_RESOURCES');
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
  if(input.command!=='provision')fail('CF_COMMAND');
  // An independent child can survive a parent loss: the file handle guards
  // duplicate provisioning until this process exits (exclusive create).
  const lock=path.join(input.state_dir,'cloudflare-auto.lock');
  let fd;try{fd=fs.openSync(lock,'wx',0o600);}catch{fail('CF_PROVISION_LOCK_EXISTS');}
  try{fs.writeFileSync(fd,JSON.stringify({pid:process.pid,started_at:new Date().toISOString()}));return await new Provisioner(c,input).run();}
  finally{fs.closeSync(fd);fs.unlinkSync(lock);}
}
if(require.main===module)main().then(result=>process.stdout.write(JSON.stringify({ok:true,result}))).catch(e=>{process.stdout.write(JSON.stringify({ok:false,error:/^CF_[A-Z0-9_]+$/.test(e.code||'')?e.code:'CF_LOCAL_FAILURE'}));process.exitCode=1;});
module.exports={Client,Provisioner,oauth,atomic};
