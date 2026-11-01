'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');
const {Client,Provisioner}=require('./Cloudflare-Provision.cjs');
const account='a'.repeat(32),zone='b'.repeat(32),appID='12345678-1234-1234-1234-123456789abc',tunnelID='87654321-1234-1234-1234-123456789abc',dnsID='c'.repeat(32),policyID='11111111-1234-1234-1234-123456789abc',secret='cfut_'+'S'.repeat(40);
function fixture(t){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'jarvis-cf-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const input={identity:{computer:'PC2',user_sid:'S-1-5-21-test',home:'D:\\Jarvis'},state_dir:dir,config_dir:dir,port:8765,zone_id:zone,label:'jarvis-desktop2',email:'one@example.com'};
  const db={apps:[],tunnels:[],dns:[],config:{config:{ingress:[]}},calls:[],fault:null};
  const root='/accounts/'+account,apps=root+'/access/apps',tunnels=root+'/cfd_tunnel',dns='/zones/'+zone+'/dns_records';
  const ok=result=>({status:200,json:{success:true,result}});
  const transport=async(token,method,url,body)=>{
    assert.equal(token,secret);db.calls.push({method,url,body});
    if(db.fault){const response=await db.fault(method,url,body,'before');if(response)return response;}
    const endpoint=url.split('?')[0];let result;
    if(method==='GET'){
      if(endpoint==='/user/tokens/verify')result={status:'active'};
      else if(endpoint==='/zones')result=[{id:zone,name:'example.com',account:{id:account,name:'Account'}}];
      else if(endpoint===root+'/access/organizations')result={auth_domain:'myteam.cloudflareaccess.com'};
      else if(endpoint===apps)result=db.apps;
      else if(endpoint===apps+'/'+appID)result=db.apps.find(a=>a.id===appID);
      else if(endpoint===apps+'/'+appID+'/policies')result=db.apps[0]?.policies;
      else if(endpoint===apps+'/'+appID+'/policies/'+policyID)result=db.apps[0]?.policies[0];
      else if(endpoint===tunnels)result=db.tunnels;
      else if(endpoint===tunnels+'/'+tunnelID)result=db.tunnels[0];
      else if(endpoint===tunnels+'/'+tunnelID+'/configurations')result=db.config;
      else if(endpoint===tunnels+'/'+tunnelID+'/token')result=Buffer.from(JSON.stringify({a:account,t:tunnelID,s:'runtime-secret'})).toString('base64');
      else if(endpoint===dns)result=db.dns;
      else if(endpoint===dns+'/'+dnsID)result=db.dns[0];
      else throw new Error('Unexpected GET '+url);
    }else if(method==='POST'){
      if(endpoint===apps){result={...structuredClone(body),id:appID,aud:'d'.repeat(64)};result.policies[0].id=policyID;db.apps.push(result);}
      else if(endpoint===tunnels){result={...body,id:tunnelID,account_tag:account,deleted_at:null};db.tunnels.push(result);}
      else if(endpoint===dns){result={...body,id:dnsID};db.dns.push(result);}
      else throw new Error('Unexpected POST '+url);
    }else if(method==='PATCH'&&endpoint===tunnels+'/'+tunnelID){Object.assign(db.tunnels[0],body);result=db.tunnels[0];}
    else if(method==='PUT'&&endpoint===apps+'/'+appID){db.apps[0]={...structuredClone(body),id:appID,aud:db.apps[0].aud};result=db.apps[0];}
    else if(method==='PUT'&&endpoint===apps+'/'+appID+'/policies/'+policyID){db.apps[0].policies[0]={...structuredClone(body),id:policyID};result=db.apps[0].policies[0];}
    else if(method==='PUT'&&endpoint===tunnels+'/'+tunnelID+'/configurations'){db.config=structuredClone(body);result=db.config;}
    else throw new Error('Unexpected mutation '+method+' '+url);
    if(db.fault){const response=await db.fault(method,url,body,'after');if(response)return response;}
    return ok(structuredClone(result));
  };
  const run=()=>new Provisioner(new Client(secret,transport),input).run();
  const rename=()=>new Provisioner(new Client(secret,transport),{...input,tunnel_name:input.tunnel_name||input.label,policy_name:input.policy_name||input.label,application_name:input.application_name||input.label}).renameExisting();
  const inspect=()=>new Provisioner(new Client(secret,transport),{...input,tunnel_name:input.tunnel_name||input.label,policy_name:input.policy_name||input.label,application_name:input.application_name||input.label}).renameExisting(true);
  return {input,db,run,rename,inspect,dir,apps,tunnels,dns,ok};
}
test('Access exact email and OAuth verified before publication; no secret journal',async t=>{
  const f=fixture(t),result=await f.run();assert.equal(result.remote_verified,false);assert.equal(result.hostname,'https://jarvis-desktop2.example.com');
  const writes=f.db.calls.filter(c=>c.method!=='GET');assert.deepEqual(writes.map(c=>c.method+':'+c.url),['POST:'+f.apps,'POST:'+f.tunnels,'PUT:'+f.tunnels+'/'+tunnelID+'/configurations','POST:'+f.dns]);
  const app=writes[0].body;assert.deepEqual(app.policies[0].include,[{email:{email:'one@example.com'}}]);assert.equal(app.oauth_configuration.dynamic_client_registration.allow_any_on_localhost,false);
  for(const name of ['cloudflare-auto.json','cloudflare-auto.json.previous','cloudflare-pending.json']){const text=fs.readFileSync(path.join(f.dir,name),'utf8');assert.ok(!text.includes(secret));assert.ok(!text.includes('runtime-secret'));}
  assert.ok(!JSON.stringify(result).includes(secret));
});
test('completed resume only reads and preserves exact runtime credential',async t=>{const f=fixture(t);await f.run();const before=f.db.calls.filter(c=>c.method!=='GET').length;await f.run();assert.equal(f.db.calls.filter(c=>c.method!=='GET').length,before);});
test('lost friendly app response cannot adopt a name as ownership proof',async t=>{const f=fixture(t);let once=true;f.db.fault=(m,u,b,phase)=>{if(once&&m==='POST'&&u===f.apps&&phase==='after'){once=false;throw new Error(secret);}};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await assert.rejects(f.run(),/CF_HOSTNAME_ACCESS_CONFLICT|CF_UNCERTAIN_APP_ID_NO_REPLAY/);assert.equal(f.db.calls.filter(c=>c.method==='POST'&&c.url===f.apps).length,1);});
test('unobserved uncertain POST stops instead of replaying',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>{if(m==='POST'&&u===f.apps&&p==='before')throw new Error(secret);};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await assert.rejects(f.run(),/CF_UNCERTAIN_APP_NO_REPLAY/);assert.equal(f.db.calls.filter(c=>c.method==='POST').length,1);});
test('definite permission denial can resume after local user fixes token',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>m==='POST'&&u===f.apps&&p==='before'?{status:403,json:{success:false}}:null;await assert.rejects(f.run(),/CF_HTTP_403/);assert.equal(f.db.dns.length,0);f.db.fault=null;await f.run();});
test('foreign DNS or Desktop1 names are never adopted or edited',async t=>{const f=fixture(t);f.db.dns.push({id:'f'.repeat(32),name:'jarvis-desktop2.example.com',comment:'Desktop1'});await assert.rejects(f.run(),/CF_HOSTNAME_DNS_CONFLICT/);assert.equal(f.db.calls.filter(c=>c.method!=='GET').length,0);});
test('wildcard Access app conflict stops before any mutation',async t=>{const f=fixture(t);f.db.apps.push({name:'Desktop1',domain:'*.example.com'});await assert.rejects(f.run(),/CF_HOSTNAME_ACCESS_CONFLICT/);assert.equal(f.db.calls.filter(c=>c.method!=='GET').length,0);});
test('policy broadening blocks resume before tunnel or DNS changes',async t=>{const f=fixture(t);await f.run();f.db.apps[0].policies[0].include=[{everyone:{}}];const count=f.db.calls.filter(c=>c.method!=='GET').length;await assert.rejects(f.run(),/CF_ACCESS_POLICY_DRIFT/);assert.equal(f.db.calls.filter(c=>c.method!=='GET').length,count);});
test('identity, email and project location cannot change silently',async t=>{const f=fixture(t);await f.run();f.input.identity.home='D:\\Other';await assert.rejects(f.run(),/CF_RESUME_IDENTITY_OR_INPUT_CHANGED/);});
test('lost PUT response is reconciled by exact configuration read',async t=>{const f=fixture(t);let once=true;f.db.fault=(m,u,b,p)=>{if(once&&m==='PUT'&&p==='after'){once=false;throw new Error('lost');}};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await f.run();assert.equal(f.db.calls.filter(c=>c.method==='PUT').length,1);});
test('uncertain PUT not observed is not repeated',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>{if(m==='PUT'&&p==='before')throw new Error('lost');};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await assert.rejects(f.run(),/CF_UNCERTAIN_CONFIGURATION_NO_REPLAY/);assert.equal(f.db.calls.filter(c=>c.method==='PUT').length,1);});
test('DNS lost response reconciles once and never republishes',async t=>{const f=fixture(t);let once=true;f.db.fault=(m,u,b,p)=>{if(once&&m==='POST'&&u===f.dns&&p==='after'){once=false;throw new Error('lost');}};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await f.run();assert.equal(f.db.calls.filter(c=>c.method==='POST'&&c.url===f.dns).length,1);});
test('changed tunnel route is detected before exporting credentials',async t=>{const f=fixture(t);await f.run();f.db.config.config.ingress[0].service='http://other:8080';await assert.rejects(f.run(),/CF_CONFIGURATION_DRIFT/);});
test('API token input rejects newline and errors do not reveal response bodies',async()=>{assert.throws(()=>new Client('secret\r\nInjected: value'),/CF_TOKEN_FORMAT/);const c=new Client(secret,async()=>({status:401,json:{success:false,errors:[{message:secret}]}}));await assert.rejects(c.discover(),e=>e.message==='CF_HTTP_401'&&!e.message.includes(secret));});
test('inactive token stops read-only discovery',async()=>{const c=new Client(secret,async()=>({status:200,json:{success:true,result:{status:'disabled'}}}));await assert.rejects(c.discover(),/CF_TOKEN_INACTIVE/);});

test('official legacy tunnel response remote_config accepted without config_src',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>{if(m==='GET'&&u===f.tunnels+'/'+tunnelID&&p==='after'){const t={...f.db.tunnels[0],remote_config:true};delete t.config_src;return f.ok(t);}};await f.run();});
test('empty tunnel configuration can be absent on a fresh owned tunnel',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>m==='GET'&&u===f.tunnels+'/'+tunnelID+'/configurations'&&p==='before'&&!f.db.config.config.ingress.length?{status:404,json:{success:false}}:null;await f.run();});
test('foreign local tunnel credential is preserved',async t=>{const f=fixture(t);fs.writeFileSync(path.join(f.dir,'tunnel-token.txt'),'foreign');await assert.rejects(f.run(),/CF_LOCAL_TOKEN_CONFLICT/);assert.equal(fs.readFileSync(path.join(f.dir,'tunnel-token.txt'),'utf8'),'foreign');});
test('token for a different account is never exported',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>m==='GET'&&u.endsWith('/token')&&p==='before'?f.ok(Buffer.from(JSON.stringify({a:'e'.repeat(32),t:tunnelID})).toString('base64')):null;await assert.rejects(f.run(),/CF_TUNNEL_TOKEN_MISMATCH/);assert.ok(!fs.existsSync(path.join(f.dir,'tunnel-token.txt')));});

test('API empty originRequest and empty path defaults do not cause false drift',async t=>{const f=fixture(t);f.db.fault=(m,u,b,p)=>{if(m==='GET'&&u.endsWith('/configurations')&&p==='after'){const result=structuredClone(f.db.config);result.config.originRequest={};result.config.ingress=result.config.ingress.map(x=>({...x,path:'',originRequest:x.originRequest||{}}));return f.ok(result);}};await f.run();await f.run();});

const fingerprint=value=>require('node:crypto').createHash('sha256').update(JSON.stringify(value,(_,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v)).digest('hex');
function legacy(f){
  const file=path.join(f.dir,'cloudflare-auto.json'),s=JSON.parse(fs.readFileSync(file,'utf8'));
  f.db.apps[0].name=s.owner;f.db.apps[0].policies[0].name=s.owner;f.db.tunnels[0].name=s.owner;
  const appBody=structuredClone(f.db.calls.find(c=>c.method==='POST'&&c.url===f.apps).body);
  appBody.name=s.owner;appBody.policies[0].name=s.owner;
  s.steps.app.body_hash=fingerprint(appBody);s.steps.tunnel.body_hash=fingerprint({name:s.owner,config_src:'cloudflare'});
  delete s.creation_names;delete s.policy_id;delete s.app_aud;delete s.renames;
  fs.writeFileSync(file,JSON.stringify(s));return s;
}
test('new resources use readable device names for tunnel, application and policy',async t=>{const f=fixture(t);await f.run();assert.equal(f.db.apps[0].name,'jarvis-desktop2');assert.equal(f.db.apps[0].policies[0].name,'jarvis-desktop2');assert.equal(f.db.tunnels[0].name,'jarvis-desktop2');});
test('custom names are independent of the public hostname',async t=>{const f=fixture(t);Object.assign(f.input,{label:'remote-host',tunnel_name:'Jarvis Notebook tunnel',application_name:'Jarvis Notebook app',policy_name:'Jarvis Notebook access'});await f.run();assert.equal(f.db.apps[0].domain,'remote-host.example.com');assert.equal(f.db.apps[0].name,'Jarvis Notebook app');assert.equal(f.db.apps[0].policies[0].name,'Jarvis Notebook access');assert.equal(f.db.tunnels[0].name,'Jarvis Notebook tunnel');});
test('legacy complete setup renames all resources in place preserving IDs, OAuth and policy',async t=>{const f=fixture(t);await f.run();legacy(f);const before=structuredClone(f.db.apps[0]);const index=f.db.calls.length;await f.rename();const writes=f.db.calls.slice(index).filter(c=>c.method!=='GET');assert.deepEqual(writes.map(c=>[c.method,c.url]),[['PUT',f.apps+'/'+appID],['PUT',f.apps+'/'+appID+'/policies/'+policyID],['PATCH',f.tunnels+'/'+tunnelID]]);assert.equal(f.db.apps[0].id,appID);assert.equal(f.db.apps[0].aud,before.aud);assert.deepEqual(f.db.apps[0].oauth_configuration,before.oauth_configuration);assert.deepEqual(f.db.apps[0].policies[0].include,before.policies[0].include);assert.equal(f.db.apps[0].policies[0].id,policyID);});
test('manual renames already done by user are accepted by UUID without mutations',async t=>{const f=fixture(t);await f.run();legacy(f);f.db.apps[0].name=f.db.apps[0].policies[0].name=f.db.tunnels[0].name='jarvis-desktop2';const index=f.db.calls.length;await f.rename();await f.run();assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('known UUID avoids an unrelated tunnel sharing the old random name',async t=>{const f=fixture(t);await f.run();const s=legacy(f);f.db.tunnels.push({id:'eeeeeeee-1234-1234-1234-123456789abc',name:s.owner,config_src:'cloudflare'});f.db.tunnels[0].name='manual-name';await f.rename();assert.equal(f.db.tunnels[1].name,s.owner);assert.equal(f.db.tunnels[0].name,'jarvis-desktop2');});
test('friendly tunnel name collision never adopts a foreign resource',async t=>{const f=fixture(t);f.db.tunnels.push({id:'eeeeeeee-1234-1234-1234-123456789abc',name:'jarvis-desktop2',config_src:'cloudflare'});await assert.rejects(f.run(),/CF_TUNNEL_NAME_CONFLICT/);assert.equal(f.db.calls.filter(c=>c.method==='POST'&&c.url===f.tunnels).length,0);});
test('lost friendly tunnel creation response cannot be adopted by name or replayed',async t=>{const f=fixture(t);let once=true;f.db.fault=(m,u,b,p)=>{if(once&&m==='POST'&&u===f.tunnels&&p==='after'){once=false;throw Error('lost');}};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await assert.rejects(f.run(),/CF_UNCERTAIN_TUNNEL_ID_NO_REPLAY/);assert.equal(f.db.calls.filter(c=>c.method==='POST'&&c.url===f.tunnels).length,1);});
for(const [key,method,resource] of [['application','PUT','app'],['policy','PUT','policy'],['tunnel','PATCH','tunnel']]){
 test('lost '+key+' rename is reconciled, never repeated',async t=>{const f=fixture(t);await f.run();legacy(f);const endpoint=resource==='app'?f.apps+'/'+appID:resource==='policy'?f.apps+'/'+appID+'/policies/'+policyID:f.tunnels+'/'+tunnelID;let once=true;f.db.fault=(m,u,b,p)=>{if(once&&m===method&&u===endpoint&&p==='after'){once=false;throw Error('lost');}};await assert.rejects(f.rename(),/CF_NETWORK/);f.db.fault=null;await f.rename();assert.equal(f.db.calls.filter(c=>c.method===method&&c.url===endpoint).length,1);});
 test('unobserved '+key+' rename is not replayed',async t=>{const f=fixture(t);await f.run();legacy(f);const endpoint=resource==='app'?f.apps+'/'+appID:resource==='policy'?f.apps+'/'+appID+'/policies/'+policyID:f.tunnels+'/'+tunnelID;f.db.fault=(m,u,b,p)=>{if(m===method&&u===endpoint&&p==='before')throw Error('lost');};await assert.rejects(f.rename(),/CF_NETWORK/);f.db.fault=null;await assert.rejects(f.rename(),new RegExp('CF_UNCERTAIN_RENAME_'+key.toUpperCase()+'_NO_REPLAY'));assert.equal(f.db.calls.filter(c=>c.method===method&&c.url===endpoint).length,1);});
}
test('changed policy permissions block all renames',async t=>{const f=fixture(t);await f.run();legacy(f);f.db.apps[0].policies[0].include=[{everyone:{}}];const index=f.db.calls.length;await assert.rejects(f.rename(),/CF_ACCESS_POLICY_DRIFT/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('different computer cannot rename recorded resources',async t=>{const f=fixture(t);await f.run();legacy(f);f.input.identity.computer='OTHER';const index=f.db.calls.length;await assert.rejects(f.rename(),/CF_RESUME_IDENTITY_OR_INPUT_CHANGED/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('rename-only never touches DNS, ingress, runtime token or Codex',async t=>{const f=fixture(t);await f.run();legacy(f);const index=f.db.calls.length;await f.rename();assert.ok(f.db.calls.slice(index).every(c=>!c.url.includes('dns_records')&&!c.url.includes('/configurations')&&!c.url.endsWith('/token')));});
test('legacy random-name interrupted creation still reconciles its unique marker',async t=>{const f=fixture(t);await f.run();const s=legacy(f);delete s.steps.app.id;delete s.steps.tunnel.id;s.steps.app.phase=s.steps.tunnel.phase='uncertain';fs.writeFileSync(path.join(f.dir,'cloudflare-auto.json'),JSON.stringify(s));const index=f.db.calls.length;await f.run();assert.equal(f.db.calls.slice(index).filter(c=>c.method==='POST').length,0);});
for(const [device,expected] of [['Desktop3','jarvis-desktop3'],['Notebook','jarvis-notebook'],['PC Ufficio','jarvis-pc-ufficio']])test('device '+device+' derives all three names independently of hostname',async t=>{const f=fixture(t);f.input.device_name=device;await f.run();assert.equal(f.db.apps[0].name,expected);assert.equal(f.db.apps[0].policies[0].name,expected);assert.equal(f.db.tunnels[0].name,expected);assert.equal(f.db.apps[0].domain,'jarvis-desktop2.example.com');});
test('concurrent policy changes are not overwritten during rename',async t=>{const f=fixture(t);await f.run();legacy(f);let once=true;f.db.fault=(m,u,b,p)=>{if(once&&m==='GET'&&u===f.apps+'/'+appID&&p==='before'){once=false;f.db.apps[0].policies[0].include=[{everyone:{}}];}};const index=f.db.calls.length;await assert.rejects(f.rename(),/CF_ACCESS_POLICY_DRIFT/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('rename checks local runtime tunnel ID and Access AUD before any write',async t=>{const f=fixture(t);await f.run();legacy(f);f.input.expected_tunnel_id='eeeeeeee-1234-1234-1234-123456789abc';let index=f.db.calls.length;await assert.rejects(f.rename(),/CF_RENAME_LOCAL_CONFIG_MISMATCH/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);f.input.expected_tunnel_id=tunnelID;f.input.expected_aud='e'.repeat(64);index=f.db.calls.length;await assert.rejects(f.rename(),/CF_RENAME_LOCAL_CONFIG_MISMATCH/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('application and policy rename carry existing optional settings unchanged',async t=>{const f=fixture(t);await f.run();legacy(f);Object.assign(f.db.apps[0],{allowed_idps:['existing-idp'],allow_iframe:false,http_only_cookie_attribute:true});Object.assign(f.db.apps[0].policies[0],{approval_required:false,isolation_required:false,session_duration:'12h'});const app=structuredClone(f.db.apps[0]);await f.rename();for(const key of ['allowed_idps','allow_iframe','http_only_cookie_attribute'])assert.deepEqual(f.db.apps[0][key],app[key]);for(const key of ['approval_required','isolation_required','session_duration'])assert.deepEqual(f.db.apps[0].policies[0][key],app.policies[0][key]);});
test('reusable policy is never renamed through the dedicated application policy endpoint',async t=>{const f=fixture(t);await f.run();legacy(f);f.db.apps[0].policies[0].reusable=true;const index=f.db.calls.length;await assert.rejects(f.rename(),/CF_ACCESS_POLICY_DRIFT/);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
test('inspect names is read-only even with legacy metadata and different names',async t=>{const f=fixture(t);await f.run();legacy(f);const file=path.join(f.dir,'cloudflare-auto.json'),before=fs.readFileSync(file,'utf8'),index=f.db.calls.length;const result=await f.inspect();assert.equal(result.matches,false);assert.equal(result.read_only,true);assert.equal(fs.readFileSync(file,'utf8'),before);assert.equal(f.db.calls.slice(index).filter(c=>c.method!=='GET').length,0);});
