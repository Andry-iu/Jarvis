# Jarvis Windows MCP 0.8.6 — validazione Desktop1, 22 novembre 2026

## Ambito e codice
- Desktop1 / DESKTOP-1ESSB0K, machine_id `1b927a030fa3b7dfa15c40cfccb4ff60`.
- Ramo di lavoro `fix/ui-mouse-move-20261122`, sorgente esatto installato `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`.
- `ui_mouse` accetta `mouse_action=move`, usa il meccanismo preesistente di snapshot monouso, validazione della finestra, verifica occlusione, punto dentro rettangolo e protezioni anti-replay. Il movimento chiama `SetCursorPos` e non invia clic o rotellina.
- Versione agente 0.8.6. Script installer e test di packaging aggiornati nelle sorgenti; ZIP non prodotto/verificato come artefatto di rilascio.

## Verifiche effettivamente eseguite
- Desktop1: `go test` sulla prima modifica `246bf05f8a6953881cdee00642e74f20d1726cfb`, esito 0, `ok jarvis-mcp-prototype 31.673s`; `go vet` e `go build`, exit_code 0.
- Desktop1: `go test` sul commit candidato `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`, exit_code 0, `ok jarvis-mcp-prototype 30.365s`; `go vet` e `go build`, exit_code 0.
- Parser PowerShell su `desktop_extensions.ps1`, `desktop_worker.ps1`, `installer-network/Install.ps1`, `installer-network/Update.ps1`, entrambi gli script Test-* e `scripts/build-network-installer.ps1`: 7 file, nessun errore (marcatore temporaneo `_release_ps_seven_PASS.txt` nella worktree QA, poi eliminabile).
- Update standard `agent_update`: request_id `jarvis-mouse-086-desktop1-20261122-152330`, src `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`, target `0.8.6`.
- Esito confermato tramite `agent_update_status`: `phase=completed`, `cleanup_verified=true`, `installed_sha256=a538b7e70e20976ad79b8221437afb461e9e8bb8ce4f5fc26a6aa096d15838fc`, backup `D:\Jarvis\Backups\self-update-183b6985de3e41678f7b2378ad7e6d3c`.
- `machine_info` da Desktop1 dopo il deploy: `version=0.8.6`, `pid=149104`, `elevated=true`. Desktop2 non coinvolto. VM non modificate.

## Limite separato — tool esposto da ChatGPT
- `machine_info.capabilities` pubblica `ui_mouse`; la sessione ChatGPT espone ancora 69 funzioni per Desktop1 senza `mcp__Jarvis_Desktop1__ui_mouse` (pur includendo `ui_keys` e `ui_action`). E' una differenza fra capability MCP e elenco degli strumenti disponibili al client.
- Manca l'aggiornamento/riconnessione del connettore che pubblica la definizione del comando. Nessuna azione di puntatore o clic da `ui_mouse` e stata eseguita in questa sessione; non dichiarare il collaudo operativo completo.
- Dopo aggiornamento del connettore: fare `window_list`, `window_inspect` (snapshot fresco), `ui_mouse(move)` a coordinate innocue, screenshot; poi nuovo snapshot, `ui_mouse(click)` su target innocuo, screenshot/UIA di riscontro.
- Resta da realizzare e collaudare il pacchetto ZIP installer 0.8.6, che non va confuso con il deploy dell'agente da Git.

Questo documento distingue modifiche di sorgente, test, deployment reale e funzionalita effettivamente richiamabili in ChatGPT.
