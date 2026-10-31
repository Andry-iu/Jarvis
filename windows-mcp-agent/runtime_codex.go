package main

import (
 "bufio"
 "context"
 "encoding/json"
 "errors"
 "fmt"
 "io"
 "os"
 "os/exec"
 "path/filepath"
 "sort"
 "strconv"
 "strings"
 "sync"
 "time"
)
type codexMessage struct{ID json.RawMessage `json:"id,omitempty"`;Method string `json:"method,omitempty"`;Params json.RawMessage `json:"params,omitempty"`;Result json.RawMessage `json:"result,omitempty"`;Error json.RawMessage `json:"error,omitempty"`}
type codexEvent struct{Sequence int64 `json:"sequence"`;At string `json:"at"`;Method string `json:"method"`;Params json.RawMessage `json:"params"`;ApprovalID string `json:"approval_id,omitempty"`;Truncated bool `json:"truncated,omitempty"`}
type codexPending struct{ID json.RawMessage;Method,ThreadID string;Params json.RawMessage}
type codexLink struct{ThreadID string `json:"thread_id"`;Project string `json:"project"`;Directory string `json:"directory"`;Model string `json:"model"`;MachineID string `json:"machine_id"`;At string `json:"at"`}
type codexClient struct{
 mu sync.Mutex
 writeMu sync.Mutex
 cmd *exec.Cmd
 input io.WriteCloser
 cancel context.CancelFunc
 next int
 waiters map[string]chan codexMessage
 pending map[string]codexPending
 loaded map[string]bool
 active map[string]string
 finished map[string]string
 dir string
 closed bool
 version string
}
func codexExecutable()(string,error){
 exe:=os.Getenv("JARVIS_CODEX_EXE");if exe!=""{if !filepath.IsAbs(exe){return "",errors.New("JARVIS_CODEX_EXE must be absolute")};if s,e:=os.Stat(exe);e!=nil||!s.Mode().IsRegular(){return "",errors.New("configured Codex executable unavailable")};return exe,nil}
 // Discover only the installed signed app package's bundled executable, no download.
 paths,_:=filepath.Glob(`C:\Program Files\WindowsApps\OpenAI.Codex_*_x64__2p2nqsd0c76g0\app\resources\codex.exe`);if len(paths)!=1{return "",errors.New("set JARVIS_CODEX_EXE locally to the installed bundled Codex executable")};return paths[0],nil
}
func startCodex(dir string)(*codexClient,error){
 exe,e:=codexExecutable();if e!=nil{return nil,e};checkCtx,cc:=context.WithTimeout(context.Background(),8*time.Second);defer cc();check:=exec.CommandContext(checkCtx,exe,"--version");check.Env=childEnvironment();configureDesktopCommand(check);out,e:=check.Output();if e!=nil{return nil,errors.New("Codex version check failed")};ver:=strings.TrimSpace(string(out));if ver!="codex-cli 0.165.0-alpha.1.2"{return nil,fmt.Errorf("unsupported Codex protocol version %q; validate generated schema before enabling",ver)}
 ctx,cancel:=context.WithCancel(context.Background())
 args:=[]string{"-c",`forced_login_method="chatgpt"`,"-c",`model_provider="openai"`,"-c",`service_tier="default"`,"-c",`mcp_servers={}`,"-c",`features.apps=false`,"-c",`features.multi_agent=false`,"-c",`web_search="disabled"`,"-c",`notify=[]`,"app-server","--listen","stdio://"}
 cmd:=exec.CommandContext(ctx,exe,args...);cmd.Env=childEnvironment();cmd.Dir=dir;configureDesktopCommand(cmd);configureTreeCancellation(cmd);input,e:=cmd.StdinPipe();if e!=nil{cancel();return nil,e};output,e:=cmd.StdoutPipe();if e!=nil{cancel();return nil,e};cmd.Stderr=io.Discard
 c:=&codexClient{cmd:cmd,input:input,cancel:cancel,waiters:map[string]chan codexMessage{},pending:map[string]codexPending{},loaded:map[string]bool{},active:map[string]string{},finished:map[string]string{},dir:dir,version:ver}
 if e=cmd.Start();e!=nil{cancel();return nil,e};go c.readLoop(output)
 _,e=c.rpc("initialize",map[string]any{"clientInfo":map[string]any{"name":"jarvis_windows_mcp","title":"Jarvis local bridge","version":version}})
 if e==nil{e=c.send(map[string]any{"jsonrpc":"2.0","method":"initialized"})};if e!=nil{c.Close();return nil,e};return c,nil
}
func(c *codexClient)send(v any)error{c.writeMu.Lock();defer c.writeMu.Unlock();b,e:=json.Marshal(v);if e!=nil{return e};b=append(b,'\n');_,e=c.input.Write(b);return e}
func(c *codexClient)rpc(method string,p any)(json.RawMessage,error){
 c.mu.Lock();if c.closed{c.mu.Unlock();return nil,errors.New("Codex broker disconnected; inspect state and resume explicitly")};c.next++;id:=strconv.Itoa(c.next);ch:=make(chan codexMessage,1);c.waiters[id]=ch;c.mu.Unlock();defer func(){c.mu.Lock();delete(c.waiters,id);c.mu.Unlock()}()
 if e:=c.send(map[string]any{"jsonrpc":"2.0","id":c.nextID(id),"method":method,"params":p});e!=nil{return nil,errors.New("Codex write failed; outcome uncertain; do not replay")}
 select{case m:=<-ch:if len(m.Error)>0&&string(m.Error)!="null"{var v struct{Code int `json:"code"`;Message string `json:"message"`};json.Unmarshal(m.Error,&v);msg:=v.Message;if len(msg)>1000{msg=msg[:1000]};return nil,fmt.Errorf("Codex RPC %s: %s",method,msg)};return m.Result,nil;case<-time.After(25*time.Second):return nil,errors.New("Codex response timeout; outcome uncertain; do not replay")}
}
func(c *codexClient)nextID(s string)int{v,_:=strconv.Atoi(s);return v}
func(c *codexClient)readLoop(out io.Reader){
 scanner:=bufio.NewScanner(out);scanner.Buffer(make([]byte,65536),8*1024*1024)
 for scanner.Scan(){var m codexMessage;if json.Unmarshal(scanner.Bytes(),&m)!=nil{continue}
  if m.Method==""&&len(m.ID)>0{c.mu.Lock();ch:=c.waiters[string(m.ID)];c.mu.Unlock();if ch!=nil{ch<-m};continue}
  if m.Method==""{continue}
  var scope struct{ThreadID string `json:"threadId"`;TurnID string `json:"turnId"`;RequestID json.RawMessage `json:"requestId"`;Turn struct{ID string `json:"id"`;Status string `json:"status"`} `json:"turn"`};json.Unmarshal(m.Params,&scope)
  if scope.ThreadID==""{if len(m.ID)>0{_ = c.send(map[string]any{"id":m.ID,"error":map[string]any{"code":-32601,"message":"unsupported host request"}})};continue}
  c.mu.Lock();if !c.loaded[scope.ThreadID]{c.mu.Unlock();if len(m.ID)>0{_ = c.send(map[string]any{"id":m.ID,"error":map[string]any{"code":-32600,"message":"thread is not attached to Jarvis"}})};continue}
  approval:="";if len(m.ID)>0{
   switch m.Method{case "item/commandExecution/requestApproval","item/fileChange/requestApproval","item/tool/requestUserInput":approval=newID();c.pending[approval]=codexPending{m.ID,m.Method,scope.ThreadID,append(json.RawMessage{},m.Params...)};default:c.mu.Unlock();_ = c.send(map[string]any{"id":m.ID,"error":map[string]any{"code":-32601,"message":"unsupported host request"}});continue}
  }
  if m.Method=="serverRequest/resolved"{for id,v:=range c.pending{if v.ThreadID==scope.ThreadID&&string(v.ID)==string(scope.RequestID){delete(c.pending,id)}}}
  if m.Method=="turn/started"{c.active[scope.ThreadID]=scope.Turn.ID};if m.Method=="turn/completed"{c.finished[scope.ThreadID]=scope.Turn.ID;delete(c.active,scope.ThreadID);for id,p:=range c.pending{if p.ThreadID==scope.ThreadID{delete(c.pending,id)}}}
  e:=c.appendEventLocked(scope.ThreadID,m.Method,m.Params,approval);c.mu.Unlock();if e!=nil{c.cancel();break}
 }
 _=c.cmd.Wait();c.mu.Lock();c.closed=true;for _,ch:=range c.waiters{select{case ch<-codexMessage{Error:json.RawMessage(`{"message":"broker disconnected; outcome requires inspection"}`)}:default:}}
 for id:=range c.loaded{_ = c.appendEventLocked(id,"jarvis/broker_disconnected",json.RawMessage(`{"outcome":"requires_verification","automatic_replay":false}`),"")};c.mu.Unlock()
}
func(c *codexClient)appendEventLocked(thread,method string,params json.RawMessage,approval string)error{
 path:=filepath.Join(c.dir,"events",thread+".json");events:=[]codexEvent{};b,e:=os.ReadFile(path);if e==nil{if json.Unmarshal(b,&events)!=nil{return errors.New("corrupt Codex event state")}}else if !os.IsNotExist(e){return e}
 seq:=int64(1);if len(events)>0{seq=events[len(events)-1].Sequence+1};truncated:=false;if len(params)>32768{params=json.RawMessage(`{"truncated":true,"retrieve_thread":true}`);truncated=true}
 events=append(events,codexEvent{seq,utcNow(),method,append(json.RawMessage{},params...),approval,truncated})
 total:=0;first:=len(events);for first>0{first--;total+=len(events[first].Params)+256;if total>1024*1024||len(events)-first>500{first++;break}};return atomicJSON(path,events[first:])
}
func(c *codexClient)Close(){c.input.Close();c.cancel()}
func(r *Runtime)getCodexLocked()(*codexClient,error){
 if os.Getenv("JARVIS_CODEX_ENABLED")!="1"{return nil,errors.New("local Codex bridge disabled; set JARVIS_CODEX_ENABLED=1 after cost/protocol validation")}
 if r.codex!=nil{r.codex.mu.Lock();closed:=r.codex.closed;r.codex.mu.Unlock();if closed{return nil,errors.New("Codex broker disconnected; restart agent and explicitly resume the intended session")};return r.codex,nil}
 c,e:=startCodex(r.dir);if e==nil{r.codex=c};return c,e
}
type codexAccount struct{Account *struct{Type string `json:"type"`;Plan string `json:"planType"`} `json:"account"`}
type quotaWindow struct{Used *float64 `json:"usedPercent"`;Resets *int64 `json:"resetsAt"`}
type codexQuota struct{LimitID string `json:"limitId"`;Primary *quotaWindow `json:"primary"`;Secondary *quotaWindow `json:"secondary"`;Credits *struct{Has *bool `json:"hasCredits"`;Unlimited *bool `json:"unlimited"`;Balance *string `json:"balance"`} `json:"credits"`;SpendControl *bool `json:"spendControlReached"`;Reached json.RawMessage `json:"rateLimitReachedType"`}
func codexCostGuard(a codexAccount,q codexQuota)error{
 if a.Account==nil||a.Account.Type!="chatgpt"||a.Account.Plan!="self_serve_business_prolite"{return errors.New("unverified account type/plan; ChatGPT Business subscription required, API fallback disabled")}
 if q.LimitID!="codex"||q.Credits==nil||q.Credits.Has==nil||*q.Credits.Has||q.Credits.Unlimited==nil||*q.Credits.Unlimited||q.Credits.Balance==nil||*q.Credits.Balance!="0"{return errors.New("zero-credit subscription-only condition cannot be established; inference blocked")}
 if q.SpendControl!=nil&&*q.SpendControl||len(q.Reached)>0&&string(q.Reached)!="null"{return errors.New("quota/spend control reached; no purchase or fallback")}
 if q.Primary==nil{return errors.New("quota unavailable; inference blocked")}
 for _,w:=range []*quotaWindow{q.Primary,q.Secondary}{if w!=nil&&(w.Used==nil||*w.Used<0||*w.Used>=90||w.Resets==nil||*w.Resets<=time.Now().Unix()){return errors.New("quota insufficient, expired or unknown; inference blocked")}}
 return nil
}
func codexAccountStatus(c *codexClient)(any,error){
 b,e:=c.rpc("account/read",map[string]any{"refreshToken":false});if e!=nil{return nil,e};var a codexAccount;if json.Unmarshal(b,&a)!=nil{return nil,errors.New("invalid account response")}
 b,e=c.rpc("account/rateLimits/read",map[string]any{});if e!=nil{return nil,e};var rates struct{RateLimits codexQuota `json:"rateLimits"`;ByID map[string]codexQuota `json:"rateLimitsByLimitId"`};if json.Unmarshal(b,&rates)!=nil{return nil,errors.New("invalid quota response")};q:=rates.RateLimits;if v,ok:=rates.ByID["codex"];ok{q=v}
 err:=codexCostGuard(a,q);reason:="";if err!=nil{reason=err.Error()};typ,plan:="","";if a.Account!=nil{typ=a.Account.Type;plan=a.Account.Plan}
 return map[string]any{"version":c.version,"account_type":typ,"plan_type":plan,"quota":q,"inference_allowed":err==nil,"blocked_reason":reason,"api_fallback":false,"paid_credits_allowed":false,"checked_at":utcNow()},nil
}
func ensureCodexCost(c *codexClient)error{v,e:=codexAccountStatus(c);if e!=nil{return e};m:=v.(map[string]any);if m["inference_allowed"]!=true{return errors.New(m["blocked_reason"].(string))};return nil}
func validThreadID(s string)bool{return requestIDPattern.MatchString(s)}
func readCodexThread(c *codexClient,id string,turns bool)(map[string]any,error){
 if !validThreadID(id){return nil,errors.New("invalid thread_id")};b,e:=c.rpc("thread/read",map[string]any{"threadId":id,"includeTurns":turns});if e!=nil{return nil,e};var v struct{Thread map[string]any `json:"thread"`};if json.Unmarshal(b,&v)!=nil||v.Thread==nil{return nil,errors.New("invalid thread response")};return v.Thread,nil
}
func sameCodexDirectory(thread map[string]any,dir string)bool{cwd,_:=thread["cwd"].(string);real,e:=filepath.EvalSymlinks(cwd);return e==nil&&samePath(real,dir)}
func codexThreadIdle(t map[string]any)bool{
 if status,ok:=t["status"].(map[string]any);ok{typ,_:=status["type"].(string);if typ=="active"||typ=="systemError"{return false}}
 if turns,ok:=t["turns"].([]any);ok{for _,v:=range turns{m,_:=v.(map[string]any);if m["status"]=="inProgress"{return false}}};return true
}
func(r *Runtime)codexLink(p runtimeRequest,dir string)(codexLink,error){var l codexLink;if !validThreadID(p.ThreadID){return l,errors.New("invalid thread_id")};b,e:=os.ReadFile(filepath.Join(r.dir,"threads",p.ThreadID+".json"));if e!=nil{return l,errors.New("session is not attached; use codex_resume first")};if json.Unmarshal(b,&l)!=nil||l.Project!=p.Project||l.MachineID!=r.MachineID||!samePath(l.Directory,dir){return l,errors.New("thread/project/computer mismatch")};return l,nil}
func codexFixedParams(dir string)map[string]any{return map[string]any{"cwd":dir,"modelProvider":"openai","approvalPolicy":"on-request","approvalsReviewer":"user","sandbox":"workspace-write","serviceTier":"default","config":map[string]any{"service_tier":"default","mcp_servers":map[string]any{},"features.apps":false,"features.multi_agent":false,"web_search":"disabled","sandbox_workspace_write.network_access":false}}}
func(r *Runtime)codexCall(p runtimeRequest)(any,error){
 mut:=false;for _,t:=range runtimeCatalog{if t.Name==p.Operation{mut=!t.ReadOnly}}
 if mut{return r.mutation(p,func(x *Receipt)(any,error){return r.codexCallLocked(p)})};r.mu.Lock();defer r.mu.Unlock();return r.codexCallLocked(p)
}
func(r *Runtime)codexCallLocked(p runtimeRequest)(any,error){
 c,e:=r.getCodexLocked();if e!=nil{return nil,e}
 if p.Operation=="codex_status"{return codexAccountStatus(c)}
 root,e:=r.projectRoot(p.Project);if e!=nil{return nil,e};dir:=root.Name();root.Close()
 if p.Operation=="codex_threads"{limit:=p.Limit;if limit==0{limit=30};args:=map[string]any{"cwd":dir,"limit":limit,"modelProviders":[]string{"openai"}};if p.Cursor!=""{args["cursor"]=p.Cursor};b,e:=c.rpc("thread/list",args);if e!=nil{return nil,e};var v any;if e=json.Unmarshal(b,&v);e!=nil{return nil,e};return v,nil}
 if p.Operation=="codex_read"{t,e:=readCodexThread(c,p.ThreadID,true);if e!=nil{return nil,e};if !sameCodexDirectory(t,dir){return nil,errors.New("session cwd does not match selected project")};b,_:=json.Marshal(t);if len(b)>256*1024{return nil,errors.New("session history exceeds 256 KiB; use Codex app for older history; current attached events remain pageable")};return t,nil}
 if p.Operation=="codex_start"||p.Operation=="codex_resume"{
  if e=ensureCodexCost(c);e!=nil{return nil,e};params:=codexFixedParams(dir);method:="thread/start"
  if p.Operation=="codex_resume"{t,e:=readCodexThread(c,p.ThreadID,true);if e!=nil{return nil,e};if !sameCodexDirectory(t,dir)||!codexThreadIdle(t){return nil,errors.New("session project mismatch or active turn; no takeover")};params["threadId"]=p.ThreadID;params["excludeTurns"]=true;method="thread/resume"}
  b,e:=c.rpc(method,params);if e!=nil{return nil,e};var result struct{Thread map[string]any `json:"thread"`;Model string `json:"model"`};if json.Unmarshal(b,&result)!=nil||result.Thread==nil{return nil,errors.New("invalid attached thread")};id,_:=result.Thread["id"].(string);if !validThreadID(id)||!sameCodexDirectory(result.Thread,dir){return nil,errors.New("created thread identity/cwd mismatch")}
  l:=codexLink{id,p.Project,dir,result.Model,r.MachineID,utcNow()};if e=atomicJSON(filepath.Join(r.dir,"threads",id+".json"),l);e!=nil{return nil,e};c.mu.Lock();c.loaded[id]=true;c.mu.Unlock();return map[string]any{"thread_id":id,"project":p.Project,"machine_id":r.MachineID,"model":result.Model,"inference_started":false},nil
 }
 link,e:=r.codexLink(p,dir);if e!=nil{return nil,e}
 if p.Operation=="codex_events"{return c.events(p)}
 c.mu.Lock();loaded:=c.loaded[p.ThreadID];active:=c.active[p.ThreadID];c.mu.Unlock();if !loaded{return nil,errors.New("session from earlier agent process; resume explicitly before continuing")}
 switch p.Operation{
 case "codex_turn","codex_steer":
  if len(p.Text)==0||len(p.Text)>32768{return nil,errors.New("text must contain 1-32768 bytes")};if e=ensureCodexCost(c);e!=nil{return nil,e}
  params:=map[string]any{"threadId":p.ThreadID,"input":[]any{map[string]any{"type":"text","text":p.Text,"text_elements":[]any{}}},"clientUserMessageId":p.RequestID};method:="turn/start"
  if p.Operation=="codex_steer"{if active==""||active!=p.TurnID{return nil,errors.New("active turn mismatch")};params["expectedTurnId"]=p.TurnID;method="turn/steer"
  }else{if active!=""{return nil,errors.New("turn already active")};t,e:=readCodexThread(c,p.ThreadID,false);if e!=nil||!sameCodexDirectory(t,dir)||!codexThreadIdle(t){return nil,errors.New("session no longer idle or project changed")};params["cwd"]=dir;params["model"]=link.Model;params["approvalPolicy"]="on-request";params["approvalsReviewer"]="user";params["serviceTier"]="default";params["sandboxPolicy"]=map[string]any{"type":"workspaceWrite","writableRoots":[]string{dir},"networkAccess":false,"excludeTmpdirEnvVar":true,"excludeSlashTmp":true}}
  b,e:=c.rpc(method,params);if e!=nil{return nil,e};var v map[string]any;if json.Unmarshal(b,&v)!=nil{return nil,errors.New("invalid turn response")}
  if p.Operation=="codex_turn"{if turn,ok:=v["turn"].(map[string]any);ok{id,_:=turn["id"].(string);c.mu.Lock();if c.finished[p.ThreadID]!=id&&turn["status"]=="inProgress"{c.active[p.ThreadID]=id};c.mu.Unlock();go c.turnWatchdog(p.ThreadID,id)}};return v,nil
 case "codex_interrupt":if active==""||active!=p.TurnID{return nil,errors.New("active turn mismatch; read events first")};b,e:=c.rpc("turn/interrupt",map[string]any{"threadId":p.ThreadID,"turnId":p.TurnID});if e!=nil{return nil,e};return map[string]any{"cancellation_requested":true,"response":b,"verify_event":"turn/completed"},nil
 case "codex_respond":return c.respond(p)
 };return nil,errors.New("unsupported Codex operation")
}
func(c *codexClient)turnWatchdog(thread,turn string){timer:=time.NewTimer(5*time.Minute);defer timer.Stop();<-timer.C;c.mu.Lock();active:=c.active[thread]==turn&&!c.closed;c.mu.Unlock();if active{_,_=c.rpc("turn/interrupt",map[string]any{"threadId":thread,"turnId":turn})}}
func(c *codexClient)events(p runtimeRequest)(any,error){
 c.mu.Lock();defer c.mu.Unlock();events:=[]codexEvent{};b,e:=os.ReadFile(filepath.Join(c.dir,"events",p.ThreadID+".json"));if e==nil{if e=json.Unmarshal(b,&events);e!=nil{return nil,e}}else if !os.IsNotExist(e){return nil,e};limit:=p.Limit;if limit==0{limit=50};out:=[]codexEvent{};next:=p.After;for _,v:=range events{if v.Sequence>p.After&&len(out)<limit{out=append(out,v);next=v.Sequence}}
 pending:=[]any{};for id,v:=range c.pending{if v.ThreadID==p.ThreadID{pending=append(pending,map[string]any{"approval_id":id,"method":v.Method,"params":v.Params})}};sort.Slice(pending,func(i,j int)bool{return pending[i].(map[string]any)["approval_id"].(string)<pending[j].(map[string]any)["approval_id"].(string)})
 gap:=len(events)>0&&p.After<events[0].Sequence-1;return map[string]any{"events":out,"next_after":next,"history_gap":gap,"pending":pending,"active_turn_id":c.active[p.ThreadID],"broker_connected":!c.closed},nil
}
func(c *codexClient)respond(p runtimeRequest)(any,error){
 c.mu.Lock();pending,ok:=c.pending[p.ApprovalID];c.mu.Unlock();if !ok||pending.ThreadID!=p.ThreadID{return nil,errors.New("approval missing, expired or thread mismatch")}
 var result any
 if pending.Method=="item/tool/requestUserInput"{if len(p.Answers)==0{return nil,errors.New("answers required")};result=map[string]any{"answers":p.Answers}
 }else{if p.Decision!="accept"&&p.Decision!="decline"&&p.Decision!="cancel"{return nil,errors.New("only one-action accept, decline or cancel is supported")};result=map[string]any{"decision":p.Decision}}
 c.mu.Lock();current,ok:=c.pending[p.ApprovalID];if !ok||string(current.ID)!=string(pending.ID){c.mu.Unlock();return nil,errors.New("approval expired")};delete(c.pending,p.ApprovalID);c.mu.Unlock()
 // The ID is consumed before dispatch; a connection error never auto-repeats approval.
 if e:=c.send(map[string]any{"id":pending.ID,"result":result});e!=nil{return nil,errors.New("approval outcome uncertain; inspect events")};return map[string]any{"approval_id":p.ApprovalID,"response_dispatched":true},nil
}
