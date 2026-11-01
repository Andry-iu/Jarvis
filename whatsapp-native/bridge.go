// Package wabridge binds a WhatsApp linked device to the private Android app.
package wabridge

import (
 "context"
 "encoding/json"
 "errors"
 "net/url"
 "os"
 "path/filepath"
 "strings"
 "sync"
 "time"
 _ "github.com/mattn/go-sqlite3"
 "go.mau.fi/whatsmeow"
 "go.mau.fi/whatsmeow/store/sqlstore"
 "go.mau.fi/whatsmeow/types"
 "go.mau.fi/whatsmeow/types/events"
 "go.mau.fi/whatsmeow/proto/waE2E"
 "google.golang.org/protobuf/proto"
)

// Engine owns a single persistent client. No logger or incoming message export.
type Engine struct {
 mu sync.Mutex
 operation sync.Mutex
 client *whatsmeow.Client
 container *sqlstore.Container
 state string
 qr string
 expires int64
 busy bool
 cancelPair context.CancelFunc
}

func Open(directory string) (*Engine,error) {
 if !filepath.IsAbs(directory) { return nil,errors.New("private storage required") }
 if err:=os.MkdirAll(directory,0700);err!=nil { return nil,errors.New("storage unavailable") }
 address:=(&url.URL{Scheme:"file",Path:filepath.Join(directory,"linked-device.db")}).String()+"?_foreign_keys=on&_busy_timeout=5000"
 container,err:=sqlstore.New(context.Background(),"sqlite3",address,nil)
 if err!=nil { return nil,errors.New("storage unavailable") }
 device,err:=container.GetFirstDevice(context.Background())
 if err!=nil { container.Close();return nil,errors.New("session unavailable") }
 e:=&Engine{container:container,client:whatsmeow.NewClient(device,nil),state:"not_linked"}
 if device.ID!=nil { e.state="disconnected" }
 e.bind(e.client)
 return e,nil
}
func (e *Engine) clientValue() *whatsmeow.Client {e.mu.Lock();defer e.mu.Unlock();return e.client}
func (e *Engine) bind(client *whatsmeow.Client) {
 client.AddEventHandler(func(event interface{}) {
  if e.clientValue()!=client { return }
  switch event.(type) {
  case *events.Connected:e.set("connected","",0)
  case *events.Disconnected:e.set("disconnected","",0)
  case *events.LoggedOut:e.set("not_linked","",0)
  }
 })
}
func (e *Engine) set(state,qr string,expires int64) { e.mu.Lock();defer e.mu.Unlock();e.state=state;e.qr=qr;e.expires=expires }
// State is private to the Android UI; QR material must never be logged or sent to an LLM.
func (e *Engine) State() string {
 e.mu.Lock();defer e.mu.Unlock()
 qr:=e.qr
 if time.Now().UnixMilli()>=e.expires { qr="" }
 b,_:=json.Marshal(map[string]interface{}{"state":e.state,"qr":qr,"expires":e.expires});return string(b)
}
// Connect starts asynchronously. Pairing is initiated only by the user's local button.
func (e *Engine) Connect(pair bool) {
 e.mu.Lock()
 if e.busy || e.state=="connected" { e.mu.Unlock();return }
 e.busy=true;e.mu.Unlock()
 go func() {
  e.operation.Lock();defer e.operation.Unlock()
  defer func(){e.mu.Lock();e.busy=false;e.mu.Unlock()}()
  client:=e.clientValue()
  if client.Store.Deleted {
   device,err:=e.container.GetFirstDevice(context.Background())
   if err!=nil {e.set("session_unavailable","",0);return}
   client=whatsmeow.NewClient(device,nil)
   e.mu.Lock();e.client=client;e.mu.Unlock();e.bind(client)
  }
  if client.Store.ID==nil {
   if !pair { e.set("not_linked","",0);return }
   client.Disconnect()
   ctx,cancel:=context.WithTimeout(context.Background(),3*time.Minute);defer cancel()
   e.mu.Lock();e.cancelPair=cancel;e.mu.Unlock()
   defer func(){e.mu.Lock();e.cancelPair=nil;e.mu.Unlock()}()
   ch,err:=client.GetQRChannel(ctx)
   if err!=nil { e.set("pairing_failed","",0);return }
   e.set("connecting","",0)
   if err=client.Connect();err!=nil { e.set("connection_failed","",0);return }
   for item:=range ch {
    switch item.Event {
    case "code": e.set("qr_ready",item.Code,time.Now().Add(item.Timeout).UnixMilli())
    case "success": e.set("paired","",0)
    case "timeout": e.set("qr_expired","",0)
    default: e.set("pairing_failed","",0)
    }
   }
   if client.IsLoggedIn() { e.set("connected","",0) }
  } else {
   e.set("connecting","",0)
   if err:=client.Connect();err!=nil { e.set("connection_failed","",0) }
  }
 }()
}
func validNumber(n string) bool {
 if len(n)<8 || len(n)>16 || n[0]!='+' { return false }
 for _,c:=range n[1:] { if c<'0'||c>'9' {return false} };return true
}
type Gate interface { Allowed() bool }

// Send performs a single dispatch. The Android command journal consumes requestID first.
// A successful return means server acknowledgement, not delivery or read receipt.
func (e *Engine) Send(number,text,requestID string,timeoutMillis int64, gate Gate) string {
 if !validNumber(number) || strings.TrimSpace(text)=="" || len([]rune(text))>2000 || len(requestID)!=36 { return "unavailable" }
 if timeoutMillis<1 || timeoutMillis>20000 { return "expired_before_dispatch" }
 ctx,cancel:=context.WithTimeout(context.Background(),time.Duration(timeoutMillis)*time.Millisecond);defer cancel()
 if gate==nil || !gate.Allowed() { return "cancelled" }
 e.Connect(false)
 client:=e.clientValue()
 if client==nil {return "whatsapp_connection_unavailable"}
 for !client.IsLoggedIn() {
  if !gate.Allowed() { return "cancelled" }
  e.mu.Lock();state:=e.state;e.mu.Unlock()
  if state=="not_linked" {return "whatsapp_not_linked"}
  select {case <-ctx.Done():return "whatsapp_connection_unavailable";case <-time.After(100*time.Millisecond):}
 }
 numbers,err:=client.IsOnWhatsApp(ctx,[]string{number})
 if err!=nil || len(numbers)!=1 || !numbers[0].IsIn {return "recipient_unverified"}
 if ctx.Err()!=nil {return "expired_before_dispatch"}
 if !gate.Allowed() {return "cancelled"}
 if e.clientValue()!=client || !client.IsLoggedIn() {return "whatsapp_connection_unavailable"}
 _,err=client.SendMessage(ctx,numbers[0].JID,&waE2E.Message{Conversation:proto.String(text)},whatsmeow.SendRequestExtra{
  ID:types.MessageID(strings.ToUpper(strings.ReplaceAll(requestID,"-",""))),Timeout:time.Duration(timeoutMillis)*time.Millisecond,
 })
 if err!=nil {return "outcome_uncertain"};return "whatsapp_server_accepted"
}
// Unlink is an explicit local operation; an unconfirmed logout keeps the stored session.
func (e *Engine) Unlink() string {
 e.mu.Lock();cancelPair:=e.cancelPair;e.mu.Unlock()
 if cancelPair!=nil {cancelPair();e.clientValue().Disconnect()}
 e.operation.Lock();defer e.operation.Unlock()
 client:=e.clientValue()
 if client.Store.ID==nil {e.set("not_linked","",0);return "unlinked"}
 ctx,cancel:=context.WithTimeout(context.Background(),15*time.Second);defer cancel()
 if err:=client.Logout(ctx);err!=nil {return "logout_unconfirmed"}
 e.set("not_linked","",0);return "unlinked"
}
