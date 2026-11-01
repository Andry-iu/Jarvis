package wabridge

import (
 "encoding/json"
 "os"
 "path/filepath"
 "testing"
 "time"
)
type testGate bool
func (g testGate) Allowed() bool {return bool(g)}
func TestOpenUsesPrivatePersistentDatabaseWithoutConnecting(t *testing.T) {
 directory:=filepath.Join(t.TempDir(),"session")
 e,err:=Open(directory);if err!=nil {t.Fatal(err)}
 defer e.container.Close()
 if e.state!="not_linked" || e.client.IsConnected() {t.Fatal("unexpected network session")}
 if _,err=os.Stat(filepath.Join(directory,"linked-device.db"));err!=nil {t.Fatal(err)}
 if _,err=Open("relative");err==nil {t.Fatal("relative path allowed")}
}
func TestExpiredQRCodeIsNeverReturned(t *testing.T) {
 e:=&Engine{};e.set("qr_ready","synthetic-test-qr",time.Now().Add(-time.Second).UnixMilli())
 var value map[string]interface{};if err:=json.Unmarshal([]byte(e.State()),&value);err!=nil {t.Fatal(err)}
 if value["qr"]!="" {t.Fatal("expired material returned")}
 e.set("connected","",0);if err:=json.Unmarshal([]byte(e.State()),&value);err!=nil {t.Fatal(err)}
 if value["qr"]!="" || value["state"]!="connected" {t.Fatal("QR not cleared")}
}
func TestInputDeadlineAndCancellationNeverConnectOrSend(t *testing.T) {
 e:=&Engine{state:"not_linked"}
 id:="33c67166-7666-4497-950d-0825781fe769"
 if e.Send("invalid","Test",id,1000,testGate(true))!="unavailable" {t.Fatal("invalid recipient allowed")}
 if e.Send("+39000000000","Test",id,0,testGate(true))!="expired_before_dispatch" {t.Fatal("expired send allowed")}
 if e.Send("+39000000000","Test",id,1000,testGate(false))!="cancelled" {t.Fatal("cancelled send allowed")}
}

func TestDeletedSessionIsRecreatedBeforeAnotherPairingWithoutConnecting(t *testing.T) {
 e,err:=Open(filepath.Join(t.TempDir(),"session"));if err!=nil {t.Fatal(err)}
 defer e.container.Close()
 e.client.Store.Deleted=true
 e.Connect(false)
 deadline:=time.Now().Add(2*time.Second)
 for time.Now().Before(deadline) {
  e.mu.Lock();busy:=e.busy;e.mu.Unlock()
  if !busy {break};time.Sleep(10*time.Millisecond)
 }
 if e.clientValue().Store.Deleted || e.clientValue().IsConnected() {t.Fatal("old session reused or network connected")}
}
