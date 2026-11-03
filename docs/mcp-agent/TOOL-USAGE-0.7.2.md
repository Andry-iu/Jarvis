# Statistiche strumenti - 0.7.2

Ogni computer conserva i propri contatori in D:\Jarvis\State\tool-usage.json,
fuori da Git/Projects e persistenti ai riavvii. L'updater ne esegue il backup.

## Lettura
tool_usage richiede machine_id; query facoltativa e nome esatto dello strumento.
Senza query: tutti ordinati per total decrescente, poi nome a parita.
Gli strumenti mai invocati compaiono a zero; un nome sconosciuto e rifiutato.

Compatibilita task_start:
{"operation":"runtime","request":{"operation":"tool_usage","machine_id":"<id>"}}
Filtro:
{"operation":"runtime","request":{"operation":"tool_usage","machine_id":"<id>","query":"codex_turn"}}

Per domande aggregate leggere entrambi i computer e indicare le rispettive date
di inizio raccolta. codex_start apre una sessione; codex_turn richiede un turno:
il conteggio non dimostra quante inferenze siano state effettivamente eseguite.

## Significato
total: invocazioni autenticate giunte al dispatcher.
succeeded/failed: esito immediato della risposta.
unfinished: totale meno esiti, per chiamate attive/interrotte.
task_start/runtime conta una volta sotto il nome effettivo.
Retry e letture contano; tool_usage include se stessa ancora in corso.
Un job accodato puo fallire successivamente: consultarne lo storico.
La quota si consulta con codex_status, non con questi contatori.
Lo storico precedente all'attivazione non viene ricostruito.

## Conservazione
Solo nomi, conteggi e date, senza argomenti, risultati o contenuti dei file.
Scritture atomiche sincronizzate; stato invalido conservato senza azzeramento.
Le operazioni continuano su guasto della telemetria e tool_usage segnala il problema.
Non ripetere una mutazione gia eseguita per un problema dei contatori.
Test: tool_usage_test.go e scripts/test-tool-usage.ps1.
