# Validazione agente 0.7.2 - 3 novembre 2026

## Stato
Implementazione, test, pubblicazione, installazione e collaudo operativo completati. Base fbf8217b21cca497eac9d5e87ba037dcf5460f54.
Repository Andry-iu/Jarvis, branch reconcile-windows-mcp-agent-20261031.

## Prove effettive
- Desktop1: suite Go completa PASS (14.392 s), go vet e build Windows PASS.
- Desktop2: suite Go completa PASS (23.923 s), go vet e build Windows PASS con
  Go 1.27.0 ufficiale e cache isolata. Go 1.27.3 ha fallito; non isolata una causa
  unica fra SDK, estrazione e cache. Nessuna sostituzione permanente del SDK.
- Entrambi: MCP isolato iniziale e rehearsal candidato/precedente PASS.
- scripts/test-tool-usage.ps1 PASS su entrambi: catalogo, alias senza duplicati,
  risposte riuscite/fallite, filtro esatto, strumenti inutilizzati a zero,
  nome sconosciuto rifiutato, classifica e auto-conteggio, persistenza dopo
  riavvio, assenza di argomenti, stato corrotto preservato senza bloccare
  operazioni, processi terminati e pulizia verificata.
- Parser PowerShell dell'updater aggiornato PASS su entrambi.
- Nessun cambiamento Go dopo le build; modifiche successive solo a script e documenti.
- Desktop2 era detached alla base sopra: collegato al branch concordato sullo
  stesso commit, preservando integralmente le modifiche locali.

## Candidati
Percorso su entrambi: D:\Jarvis\Releases\0.7.2\jarvis-mcp.exe.
Desktop1 SHA-256:
1b8ee2a0b0354fd071fe2d71c5a6075f97dd43d3cd58a1152176ff1f16a3fe4f
Desktop2 SHA-256:
6f45785d52fe5ef87cc320ef1b7b6faac175c4ff221af8f5448524bee0848a72

## Contratto e limiti
Solo invocazioni autenticate giunte al dispatcher, a partire dall'attivazione.
Successo significa risposta immediata, non completamento del job o consumo quota.
unfinished include chiamate attive/interrotte; tool_usage include se stessa in corso.
I retry contano come nuove chiamate; lo storico precedente non e ricostruito.
Nessuna inferenza Codex o modifica a guardie economiche, Android/main o root Projects.
Reboot fisico, ciclo reale di 24 ore e refresh catalogo client non certificati.

## Ripetizione
Su Windows PowerShell 5.1:
powershell.exe -NoProfile -File scripts\test-tool-usage.ps1 -Candidate <exe> -ExpectedHash <sha256>
La suite usa istanze isolate sotto D:\Jarvis\Temp e verifica la pulizia nel finally.

## Installazione e collaudo finale completati
3 novembre 2026. Il push fast-forward del commit runtime
31c900d240b2188d6e3ef7d8577bd0951fc87dcd e stato verificato tramite ls-remote.
Entrambi gli agenti installati e interrogati da ChatGPT rispondono con 0.7.2.

| Verifica | Desktop1 | Desktop2 |
|---|---|---|
| Identita | 1b927a030fa3b7dfa15c40cfccb4ff60 | d41455adf8721c205183224d3c69a1a5 |
| Percorso | D:\Jarvis\jarvis-mcp.exe | D:\Jarvis\Agent\jarvis-mcp.exe |
| Avvio | diretto | supervisore Start.ps1 esistente |
| Backup installazione | self-update-b27e6eb337fa4b4dad1fb467a95263e8 | self-update-f1d2c52b63a14d88a60e0e199a0eefe7 |
| Backup prova riavvio | self-update-62a864e661414ba896e363f01d82ed82 | self-update-1198fd002114416b96c7a6776c0fd42c |
| Installazione e manutenzione | PASS | PASS |
| Chiamate dirette/alias senza duplicati | PASS | PASS |
| Persistenza dopo riavvio reale | PASS | PASS |

Tutti i backup sopra sono sotto D:\Jarvis\Backups.
self-update.log termina PASS common_batch_update_identity_config_maintenance.
Lo script ripetibile verify-tool-usage-live.ps1 verifica i contatori, esegue
il riavvio della stessa 0.7.2 tramite updater canonico, verifica identita e
che nessun valore sia diminuito. Nessun azzeramento o ripristino cieco dello stato.
Prima prova Desktop1: errore del solo script di collaudo, che indicava lo stesso
file come sorgente/destinazione. Updater ha ripristinato correttamente la 0.7.2.
Corretto lo script per usare il candidato separato in Releases e verificare
preventivamente percorsi/hash; ripetizione completa PASS su entrambi.
I binari Go non sono cambiati: hash e dimensioni restano quelli di CHECKSUMS-0.7.2.json.

## Periodo statistico e uso
started_at preesistente conservato: Desktop1 17:53:58.4575006 UTC,
Desktop2 18:12:52.4840496 UTC del 3 novembre 2026, precedenti alla promozione finale.
Le invocazioni di validazione contano come le altre; non interpretare il file come
sole operazioni produttive successive all'installazione.
Interrogazioni ChatGPT tool_usage funzionanti tramite task_start/runtime.
Il catalogo server comprende tool_usage; refresh del catalogo diretto client non provato.
