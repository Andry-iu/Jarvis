# Validazione 0.7.4 — 5 novembre 2026

Implementato: contatori Codex separati per progetto/incarico/categoria, conservando
i dialoghi; contratto TOKEN-METER-0.7.4.md. Branch feature/development-token-meter-20261105,
base 7a2947945137d39b49392c9fe05157696956d78d.

Test locali Linux Go 1.27.0: suite, vet e race PASS. Il test preesistente
TestQueueIndependentProjectsCanDispatchTogether presentava una corsa nel cleanup:
terminava mentre i processi git scrivevano ancora i job. Il test ora attende
l'esito dei due job prima della rimozione dei temporanei, mantenendo l'asserzione
del dispatch contemporaneo. Nessuna modifica al runtime dei comandi.
Test nuovi e processo RPC simulato senza modelli/API. Compilazione incrociata
Windows amd64 PASS. Test nativi e installazione ancora da completare: non
dichiarati da questo verbale iniziale.

Preflight remoto: entrambi agenti 0.7.3, coda vuota e sana, sorgenti canonici HEAD
7a2947945137d39b49392c9fe05157696956d78d. Desktop1 conserva update.go.bak non tracciato;
Desktop2 conserva Historical-0.7.0/. Nessuna pulizia o sovrascrittura. Build candidate
in checkout isolati; percorsi installati e configurazioni preservati.

Hash precedenti: Desktop1 a0a5ee1b95a8222b769b320165cf92a614ce58be7c0c004c37696c4c430d091b;
Desktop2 c77b43a851de409ceda18b712259a72fedcc07e2c00d80bec0f0c580437d66ca.
Nessuna inferenza reale, chiave, credito o modifica delle guardie economiche.
Non certificati reboot fisico, ciclo reale 24 ore o misurazione di questa chat/Voice.
