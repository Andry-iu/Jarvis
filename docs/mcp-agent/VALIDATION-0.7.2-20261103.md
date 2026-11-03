# Validazione agente 0.7.2 - 3 novembre 2026

## Stato
Implementazione e candidati verificati. Pubblicazione, installazione e collaudo
operativo da completare. Base fbf8217b21cca497eac9d5e87ba037dcf5460f54.
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
