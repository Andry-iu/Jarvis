'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');
const {Client,Provisioner}=require('./Cloudflare-Provision.cjs');
const account='a'.repeat(32),zone='b'.repeat(32),appID='12345678-1234-1234-1234-123456789abc',tunnelID='87654321-1234-1234-1234-123456789abc',dnsID='c'.repeat(32),secret='cfut_'+'S'.repeat(40);
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
      else if(endpoint===tunnels)result=db.tunnels;
      else if(endpoint===tunnels+'/'+tunnelID)result=db.tunnels[0];
      else if(endpoint===tunnels+'/'+tunnelID+'/configurations')result=db.config;
      else if(endpoint===tunnels+'/'+tunnelID+'/token')result=Buffer.from(JSON.stringify({a:account,t:tunnelID,s:'runtime-secret'})).toString('base64');
      else if(endpoint===dns)result=db.dns;
      else if(endpoint===dns+'/'+dnsID)result=db.dns[0];
      else throw new Error('Unexpected GET '+url);
    }else if(method==='POST'){
      if(endpoint===apps){result={...structuredClone(body),id:appID,aud:'d'.repeat(64)};db.apps.push(result);}
      else if(endpoint===tunnels){result={...body,id:tunnelID,deleted_at:null};db.tunnels.push(result);}
      else if(endpoint===dns){result={...body,id:dnsID};db.dns.push(result);}
      else throw new Error('Unexpected POST '+url);
    }else if(method==='PUT'&&endpoint===tunnels+'/'+tunnelID+'/configurations'){db.config=structuredClone(body);result=db.config;}
    else throw new Error('Unexpected mutation '+method+' '+url);
    if(db.fault){const response=await db.fault(method,url,body,'after');if(response)return response;}
    return ok(structuredClone(result));
  };
  const run=()=>new Provisioner(new Client(secret,transport),input).run();
  return {input,db,run,dir,apps,tunnels,dns,ok};
}
test('Access exact email and OAuth verified before publication; no secret journal',async t=>{
  const f=fixture(t),result=await f.run();assert.equal(result.remote_verified,false);assert.equal(result.hostname,'https://jarvis-desktop2.example.com');
  const writes=f.db.calls.filter(c=>c.method!=='GET');assert.deepEqual(writes.map(c=>c.method+':'+c.url),['POST:'+f.apps,'POST:'+f.tunnels,'PUT:'+f.tunnels+'/'+tunnelID+'/configurations','POST:'+f.dns]);
  const app=writes[0].body;assert.deepEqual(app.policies[0].include,[{email:{email:'one@example.com'}}]);assert.equal(app.oauth_configuration.dynamic_client_registration.allow_any_on_localhost,false);
  for(const name of ['cloudflare-auto.json','cloudflare-auto.json.previous','cloudflare-pending.json']){const text=fs.readFileSync(path.join(f.dir,name),'utf8');assert.ok(!text.includes(secret));assert.ok(!text.includes('runtime-secret'));}
  assert.ok(!JSON.stringify(result).includes(secret));
});
test('completed resume only reads and preserves exact runtime credential',async t=>{const f=fixture(t);await f.run();const before=f.db.calls.filter(c=>c.method!=='GET').length;await f.run();assert.equal(f.db.calls.filter(c=>c.method!=='GET').length,before);});
test('lost app response reconciles owned resource on next invocation, never repeats POST',async t=>{const f=fixture(t);let once=true;f.db.fault=(m,u,b,phase)=>{if(once&&m==='POST'&&u===f.apps&&phase==='after'){once=false;throw new Error(secret);}};await assert.rejects(f.run(),/CF_NETWORK/);f.db.fault=null;await f.run();assert.equal(f.db.calls.filter(c=>c.method==='POST'&&c.url===f.apps).length,1);});
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
