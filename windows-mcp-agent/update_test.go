package main
import (
 "crypto/sha256"
 "encoding/hex"
 "strings"
 "testing"
)
func TestUpdateDigestAndHeader(t *testing.T){
 id:="0123456789abcdef0123456789abcdef"
 body:="REM JARVIS-UPDATE-ID: "+id+"\r\n@echo off\r\n"
 sum:=sha256.Sum256([]byte(body))
 if !updateIDPattern.MatchString(id)||!updateHashPattern.MatchString(hex.EncodeToString(sum[:])){t.Fatal("valid header/digest rejected")}
 if strings.HasPrefix("REM JARVIS-UPDATE-ID: "+id+"\n","REM JARVIS-UPDATE-ID: "+id+"\r\n"){t.Fatal("LF-only header accepted")}
}
