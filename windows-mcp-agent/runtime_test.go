package main

import (
 "encoding/json"
 "os"
 "path/filepath"
 "strings"
 "testing"
 "time"
)
func fixtureRuntime(t *testing.T)(*Runtime,string){t.Helper();home:=t.TempDir();projects:=filepath.Join(home,"projects");if e:=os.MkdirAll(filepath.Join(projects,"sample"),0700);e!=nil{t.Fatal(e)};t.Setenv("JARVIS_HOME",home);t.Setenv("JARVIS_PROJECTS_ROOT",projects);r,e:=newRuntime(filepath.Join(home,"private-state"));if e!=nil{t.Fatal(e)};t.Cleanup(r.Close);return r,filepath.Join(projects,"sample")}
func mutateFile(t *testing.T,r *Runtime,p runtimeRequest)any{t.Helper();if p.RequestID==""{p.RequestID=newID()};p.MachineID=r.MachineID;if p.Project==""{p.Project="sample"};v,e:=r.call(p);if e!=nil{t.Fatal(p.Operation,e)};return v}
func TestRuntimeFilesJournalAndStaleContent(t *testing.T){
 r,dir:=fixtureRuntime(t);original:=[]byte("bozza dell'utente\n");os.WriteFile(filepath.Join(dir,"draft.txt"),original,0600)
 p:=runtimeRequest{Operation:"file_write",MachineID:r.MachineID,Project:"sample",Path:"draft.txt",Expected:digest(original),Content:"nuovo è literal $() `",RequestID:newID()};v:=mutateFile(t,r,p)
 b,_:=os.ReadFile(filepath.Join(dir,"draft.txt"));if string(b)!=p.Content{t.Fatal("write mismatch")};if _,e:=r.call(p);e!=nil{t.Fatal("idempotent receipt",e)}
 p.Content="different";if _,e:=r.call(p);e==nil{t.Fatal("request reuse accepted")}
 stale:=p;stale.RequestID=newID();if _,e:=r.call(stale);e==nil{t.Fatal("stale overwrite accepted")};b,_=os.ReadFile(filepath.Join(dir,"draft.txt"));if string(b)!="nuovo è literal $() `"{t.Fatal("stale write changed file")}
 id:=v.(map[string]any)["request_id"].(string);mutateFile(t,r,runtimeRequest{Operation:"file_restore",RestoreID:id});b,_=os.ReadFile(filepath.Join(dir,"draft.txt"));if string(b)!=string(original){t.Fatal("restore mismatch")}
 created:=mutateFile(t,r,runtimeRequest{Operation:"directory_create",Path:filepath.Join("new","nested")});mutateFile(t,r,runtimeRequest{Operation:"file_restore",RestoreID:created.(map[string]any)["request_id"].(string)});if _,e:=os.Stat(filepath.Join(dir,"new"));!os.IsNotExist(e){t.Fatal("created parents not removed")}
}
func TestRuntimePathsSecretsAndLinks(t *testing.T){
 r,dir:=fixtureRuntime(t)
 for _,path:=range []string{"../outside","draft.txt:ads","CON.txt","folder/../draft.txt",".env",".git/config","config/agent.env","key.pem","bad. ","bad?name"}{p:=runtimeRequest{Operation:"file_write",Project:"sample",Path:filepath.FromSlash(path),Expected:"missing",Content:"x",RequestID:newID()};if _,e:=r.call(p);e==nil{t.Fatalf("unsafe path accepted: %s",path)}}
 os.WriteFile(filepath.Join(dir,".env"),[]byte("private-value"),0600);if e:=os.Symlink(filepath.Join(dir,".env"),filepath.Join(dir,"alias.txt"));e==nil{if _,e=r.filesRead(runtimeRequest{Operation:"file_read",Project:"sample",Path:"alias.txt"});e==nil{t.Fatal("secret symlink read")}}
 outside:=filepath.Join(t.TempDir(),"outside.txt");os.WriteFile(outside,[]byte("outside"),0600);if e:=os.Symlink(outside,filepath.Join(dir,"external.txt"));e==nil{if _,e=r.filesRead(runtimeRequest{Operation:"file_read",Project:"sample",Path:"external.txt"});e==nil{t.Fatal("external symlink read")}}
 root,e:=r.projectRoot("sample");if e!=nil{t.Fatal(e)};defer root.Close();if e=writeRootFile(root,"existing.txt",[]byte("a"),"missing");e!=nil{t.Fatal(e)};if e=writeRootFile(root,"existing.txt",[]byte("b"),"missing");e==nil{t.Fatal("creation overwrote file")}
}
func TestRuntimePersistenceAndLock(t *testing.T){
 r,_:=fixtureRuntime(t);if other,e:=newRuntime(r.dir);e==nil{other.Close();t.Fatal("concurrent state owner accepted")}
 j:=&Job{ID:newID(),MachineID:r.MachineID,Project:"sample",Kind:"go_test",Status:"running",CreatedAt:utcNow()};if e:=r.saveJob(j);e!=nil{t.Fatal(e)}
 id,dir:=r.MachineID,r.dir;r.Close();again,e:=newRuntime(dir);if e!=nil{t.Fatal(e)};defer again.Close();if again.MachineID!=id||again.Jobs[j.ID].Status!="interrupted"{t.Fatal("identity/recovery mismatch")}
}
func TestRuntimeMachineMismatchAndCatalog(t *testing.T){
 r,_:=fixtureRuntime(t);a:=&Agent{runtime:r};b,_:=json.Marshal(runtimeRequest{Operation:"file_write",MachineID:"wrong",Project:"sample",Path:"x.txt",Expected:"missing",Content:"x",RequestID:newID()});out:=a.invokeRuntime("",b).(map[string]any);if out["isError"]!=true{t.Fatal("wrong machine accepted")}
 for _,t:=range toolsList().(map[string]any)["tools"].([]any){m:=t.(map[string]any);if isRuntimeTool(m["name"].(string))&&m["name"]!="machine_info"{s:=m["inputSchema"].(map[string]any);found:=false;for _,p:=range s["required"].([]string){if p=="machine_id"{found=true}};if !found{panic("machine id missing from schema")}}}
 if _,e:=decodeRuntime("",json.RawMessage(`{"operation":"machine_info","unexpected":1}`));e==nil{t.Fatal("unknown fields accepted")}
}
func TestCodexCostGuardFailClosed(t *testing.T){
 yes,no:=true,false;zero:="0";used:=11.0;reset:=time.Now().Add(time.Hour).Unix();a:=codexAccount{};json.Unmarshal([]byte(`{"account":{"type":"chatgpt","planType":"self_serve_business_prolite"}}`),&a);q:=codexQuota{LimitID:"codex",Primary:&quotaWindow{&used,&reset}};q.Credits=&struct{Has *bool `json:"hasCredits"`;Unlimited *bool `json:"unlimited"`;Balance *string `json:"balance"`}{&no,&no,&zero}
 if e:=codexCostGuard(a,q);e!=nil{t.Fatal(e)};q.Credits.Balance=nil;if e:=codexCostGuard(a,q);e==nil{t.Fatal("unknown balance accepted")};q.Credits.Balance=&zero;q.Credits.Has=&yes;if e:=codexCostGuard(a,q);e==nil{t.Fatal("paid credit enabled")};q.Credits.Has=&no;used=95;if e:=codexCostGuard(a,q);e==nil{t.Fatal("quota low accepted")};used=11;a.Account.Type="apiKey";if e:=codexCostGuard(a,q);e==nil{t.Fatal("API fallback accepted")};a.Account.Type="chatgpt";q.Credits=nil;if e:=codexCostGuard(a,q);e==nil{t.Fatal("unknown credit state accepted")}
}
func TestCommandProfilesRejectShellAndEnvironment(t *testing.T){
 for _,p:=range []string{"powershell","cmd /c whoami","go_test;echo x"}{if _,_,e:=commandSpec(p,t.TempDir(),newID());e==nil{t.Fatal("arbitrary command accepted")}}
 t.Setenv("JARVIS_TEST_PRIVATE_TOKEN","must-not-inherit");t.Setenv("OPENAI_API_KEY","must-not-inherit");for _,v:=range childEnvironment(){if strings.HasPrefix(v,"JARVIS_")||strings.HasPrefix(v,"OPENAI_"){t.Fatal("sensitive environment inherited")}}
}
