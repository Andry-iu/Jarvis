package main

import("encoding/json";"strconv";"testing";"time")

func TestCodexIndependentCostProof(t *testing.T){
	var a codexAccount
	json.Unmarshal([]byte(`{"account":{"type":"chatgpt","planType":"self_serve_business_prolite","email":"fixture@example.invalid"}}`),&a)
	var q codexQuota
	reset:=time.Now().Add(24*time.Hour).Unix()
	json.Unmarshal([]byte(`{"limitId":"codex","primary":{"usedPercent":12,"resetsAt":`+strconv.FormatInt(reset,10)+`},"credits":{"hasCredits":false,"unlimited":false,"balance":null},"spendControlReached":false,"rateLimitReachedType":null}`),&q)
	now:=time.Now().UTC();mid:=newID()
	p:=codexCostProof{Version:1,MachineID:mid,AccountFingerprint:codexAccountFingerprint(a),Source:"user-confirmed-usage-screenshot",ZeroCredits:true,AutomaticRechargeDisabled:true,ConfirmedAt:now,ValidUntil:now.Add(24*time.Hour)}
	if e:=codexCostGuard(a,q);e==nil{t.Fatal("bare null balance accepted")}
	if e:=codexCostGuardWithProof(a,q,&p,mid,now);e!=nil{t.Fatal(e)}
	if q.Credits.Balance!=nil{t.Fatal("raw API balance was modified")}
	for _,change:=range []func(*codexCostProof){func(p *codexCostProof){p.MachineID=newID()},func(p *codexCostProof){p.AccountFingerprint="wrong"},func(p *codexCostProof){p.AutomaticRechargeDisabled=false},func(p *codexCostProof){p.ZeroCredits=false},func(p *codexCostProof){p.ValidUntil=now.Add(-time.Second)},func(p *codexCostProof){p.ValidUntil=now.Add(8*24*time.Hour)},func(p *codexCostProof){p.ConfirmedAt=now.Add(time.Hour)}}{
		bad:=p;change(&bad);if e:=codexCostGuardWithProof(a,q,&bad,mid,now);e==nil{t.Fatal("invalid proof accepted")}
	}
	if e:=codexCostGuardWithProof(a,q,nil,mid,now);e==nil{t.Fatal("missing proof accepted")}
	yes:=true;q.Credits.Has=&yes;if e:=codexCostGuardWithProof(a,q,&p,mid,now);e==nil{t.Fatal("paid credits bypassed")};no:=false;q.Credits.Has=&no
	q.Primary.Used=nil;if e:=codexCostGuardWithProof(a,q,&p,mid,now);e==nil{t.Fatal("unknown quota bypassed")}
}
