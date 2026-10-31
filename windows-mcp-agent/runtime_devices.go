package main
import(
 "bytes"
 "context"
 "encoding/json"
 "errors"
 "io"
 "net/http"
 "net/url"
 "os"
 "path/filepath"
 "strings"
 "time"
)
type peerDevice struct{
 ID string `json:"device_id"`
 MachineID string `json:"machine_id"`
 Endpoint string `json:"endpoint"`
 TokenEnv string `json:"token_env"`
 AccessIDEnv string `json:"access_id_env,omitempty"`
 AccessSecretEnv string `json:"access_secret_env,omitempty"`
}
func configuredPeers()([]peerDevice,error){
 path:=filepath.Join(os.Getenv("JARVIS_HOME"),"Config","devices.json");b,e:=os.ReadFile(path);if os.IsNotExist(e){return []peerDevice{},nil};if e!=nil{return nil,e};if len(b)>65536{return nil,errors.New("device configuration too large")}
 var peers []peerDevice;d:=json.NewDecoder(bytes.NewReader(b));d.DisallowUnknownFields();if e=d.Decode(&peers);e!=nil{return nil,errors.New("invalid devices.json")};seen:=map[string]bool{}
 for _,p:=range peers{if !requestIDPattern.MatchString(p.MachineID)||p.ID==""||seen[p.ID]{return nil,errors.New("invalid/duplicate peer identity")};seen[p.ID]=true;u,e:=url.Parse(p.Endpoint);if e!=nil||u.User!=nil||u.Fragment!=""||u.RawQuery!=""||u.Path!="/mcp"{return nil,errors.New("invalid peer MCP endpoint")};local:=os.Getenv("JARVIS_TEST_PEERS")=="1"&&u.Scheme=="http"&&(u.Hostname()=="127.0.0.1"||u.Hostname()=="::1");if u.Scheme!="https"&&!local{return nil,errors.New("peers require HTTPS")};if p.TokenEnv!=""&&!envName.MatchString(p.TokenEnv){return nil,errors.New("invalid peer token reference")};if p.AccessIDEnv!=""&&(!envName.MatchString(p.AccessIDEnv)||!envName.MatchString(p.AccessSecretEnv)){return nil,errors.New("invalid Access reference")}}
 return peers,nil
}
func peerRPC(p peerDevice,method string,args any)(map[string]any,error){
 b,_:=json.Marshal(map[string]any{"jsonrpc":"2.0","id":1,"method":"tools/call","params":map[string]any{"name":method,"arguments":args}});ctx,cancel:=context.WithTimeout(context.Background(),25*time.Second);defer cancel();req,e:=http.NewRequestWithContext(ctx,"POST",p.Endpoint,bytes.NewReader(b));if e!=nil{return nil,e};req.Header.Set("Content-Type","application/json");req.Header.Set("Accept","application/json, text/event-stream")
 if p.TokenEnv!=""{v:=os.Getenv(p.TokenEnv);if len(v)<24{return nil,errors.New("peer bearer reference unavailable")};req.Header.Set("Authorization","Bearer "+v)}
 if p.AccessIDEnv!=""{id,secret:=os.Getenv(p.AccessIDEnv),os.Getenv(p.AccessSecretEnv);if id==""||secret==""{return nil,errors.New("peer Access references unavailable")};req.Header.Set("CF-Access-Client-Id",id);req.Header.Set("CF-Access-Client-Secret",secret)}
 if p.TokenEnv==""&&p.AccessIDEnv==""{return nil,errors.New("authenticated peer configuration required")}
 client:=&http.Client{Timeout:25*time.Second,CheckRedirect:func(*http.Request,[]*http.Request)error{return errors.New("peer redirects refused")}};res,e:=client.Do(req);if e!=nil{return nil,errors.New("peer unavailable")};defer res.Body.Close();if res.StatusCode!=200{return nil,errors.New("peer authentication or HTTP failure")};data,e:=io.ReadAll(io.LimitReader(res.Body,4*1024*1024+1));if e!=nil||len(data)>4*1024*1024{return nil,errors.New("peer result too large")};var rpc struct{Result map[string]any `json:"result"`;Error any `json:"error"`};if json.Unmarshal(data,&rpc)!=nil||rpc.Error!=nil||rpc.Result==nil{return nil,errors.New("invalid peer MCP response")};return rpc.Result,nil
}
func peerIdentity(p peerDevice)error{v,e:=peerRPC(p,"machine_info",map[string]any{});if e!=nil{return e};if v["isError"]==true{return errors.New("peer refused identity request")};content,ok:=v["content"].([]any);if !ok{return errors.New("invalid peer identity")};for _,c:=range content{m,_:=c.(map[string]any);if m["type"]=="text"{s,_:=m["text"].(string);var identity struct{MachineID string `json:"machine_id"`};if json.Unmarshal([]byte(s),&identity)==nil&&identity.MachineID==p.MachineID{return nil}}};return errors.New("peer identity mismatch; operation blocked")}
func(r *Runtime)deviceCall(p runtimeRequest)(any,error){
 peers,e:=configuredPeers();if e!=nil{return nil,e}
 if p.Operation=="device_list"{out:=[]any{map[string]any{"device_id":"local","machine_id":r.MachineID,"online":true,"local":true}};for _,v:=range peers{e:=peerIdentity(v);item:=map[string]any{"device_id":v.ID,"machine_id":v.MachineID,"online":e==nil};if e!=nil{item["error"]=e.Error()};out=append(out,item)};return map[string]any{"devices":out,"checked_at":utcNow()},nil}
 var selected *peerDevice;for i:=range peers{if peers[i].ID==p.DeviceID{selected=&peers[i]}};if selected==nil{return nil,errors.New("unknown device; configure trusted peer locally")}
 q,e:=decodeRuntime("",p.Call);if e!=nil{return nil,e};if q.Operation=="device_call"||q.Operation=="device_list"||strings.HasPrefix(q.Operation,"update_"){return nil,errors.New("nested routing/update forwarding is not allowed")};if q.MachineID!=selected.MachineID{return nil,errors.New("forwarded machine_id must match configured peer")};if e=peerIdentity(*selected);e!=nil{return nil,e};return peerRPC(*selected,q.Operation,q)
}
