# Validazione progressiva Jarvis 0.8.6 — 2026-11-22

## Codice e Git
- Macchina di destinazione: Desktop1 / DESKTOP-1ESSB0K, machine_id `1b927a030fa3b7dfa15c40cfccb4ff60`, partenza verificata v0.8.5.
- Ramo `fix/ui-mouse-move-20261122`; source-change commit `246bf05f8a6953881cdee00642e74f20d1726cfb`.
- Aggiunta `move` ad `ui_mouse`. `move` chiama `SetCursorPos` dopo verifica finestra e termina prima di `SendInput`: nessun clic viene inviato.
- I test Go `go test ./...`, `go vet` e `go build` sono passati su Desktop1 nella worktree QA del commit sorgente `246bf05f8a6953881cdee00642e74f20d1726cfb` con exit_code=0.
- La validazione sintattica via parser PowerShell di `desktop_extensions.ps1` e `desktop_worker.ps1` sulla stessa worktree ha prodotto `_mouse_ps_both_PASS.txt`.
- Questo commit aggiorna la versione 0.8.6 e i riferimenti del pacchetto: l'esito di build e installazione per il commit completo deve essere registrato separatamente.

## Mancanze da verificare
- Validare il commit 0.8.6 completo e gli script/test installer; non esiste un installer ZIP 0.8.6 dichiarato verificato in questo verbale.
- Aggiornare l'agente su Desktop1 tramite `agent_update` da commit esatto solo dopo build/test e verificare identity/version/hash e receipt.
- Il connettore ChatGPT **non espone** `mcp__Jarvis_Desktop1__ui_mouse` anche quando la capability `ui_mouse` e pubblicata dall'agente. La pubblicazione/refresh del tool e la sua chiamata da ChatGPT restano un blocco distinto. Non usare Desktop2 per sostituire la verifica su Desktop1.
- Prova runtime innocua: movimento puntatore, clic e screenshot/riscontro UI. Non dichiarare il risultato finche non eseguito.

Non compromettere VM, progetti paralleli, file non tracciati o credenziali.
