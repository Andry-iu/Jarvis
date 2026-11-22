# Catalogo operativo MCP — 0.8.4

Il catalogo tools/list di ogni agente espone gli stessi strumenti. Il coordinatore
resta la chat. Non esistono nomi di PC, ruoli o livelli speciali nel contratto.

| Strumento | Parametri obbligatori oltre a machine_id | Effetto |
|---|---|---|
| agent_update | source_commit (40-hex), target_version, request_id | Il destinatario scarica da Git, verifica, compila/testa ed esegue l'updater canonico in batch separato |
| agent_update_status | request_id | Stato durevole del target; completed richiede hash/versione/identita live e cleanup |
| device_call | device_id, call | Inoltro autenticato dello stesso comando o lettura attraverso i percorsi via configurati |
| update_prepare | update_id (32-hex), sha256, content | Modalita pacchetto conservata: prepara batch immutabile CRLF fino a 262144 byte |
| update_execute | update_id, sha256 | Avvia il batch preparato; acknowledgement distinto da installazione |
| update_status | update_id | Ricevuta durevole del trasporto; esito incerto non rilanciato |
| log_read | path, tail_bytes facoltativo | Diagnostica consentita sul computer destinatario |

Per agent_update e agent_update_status il tool diretto include machine_id.
Per le API legacy il contratto effettivo e quello di tools/list; nel forwarding
l'identita finale viene verificata e aggiunta dal gateway.

Stessa call interna a ogni profondita:

```json
{"operation":"agent_update","source_commit":"<commit-pubblicato-40-hex>","target_version":"0.8.4","request_id":"<id-destinazione>"}
```

MCP diretto: inviare al destinatario con il suo machine_id. Gateway: inserire
questa call in device_call con l'identita del gateway esterno e l'alias finale.
Non annidare a mano device_call; i record via definiscono il percorso. Leggere
agent_update_status lungo lo stesso percorso e con lo stesso request_id.

Cache client precedente: per il nodo locale usare task_start con operation=runtime
e request completa. Gli intermedi precedenti alla 0.8.4 non conoscono ancora il
nuovo nome pubblico; il bootstrap compatibile invia a tutti lo stesso piccolo
comando di download del runner mediante update_prepare/update_execute.

Se il destinatario non raggiunge Git, la chat puo usare il pacchetto verificato
tramite gli strumenti di aggiornamento preesistenti. Il gateway trasporta allora
i blocchi del pacchetto; il motore di sostituzione e sempre update-agent.ps1.
Un errore di build o una promozione incerta non autorizzano a saltare i controlli.

La descrizione client storica “Never forwards update batches” e obsoleta. Quella
del server ora cita agent_update e i batch offline. L'aggiornamento dei sorgenti
e del catalogo server non dimostra il refresh della cache degli strumenti gia
esposti in una chat. Non richiedere una riconnessione per operare quando e
utilizzabile la compatibilita; non dichiarare aggiornato il client senza prova.

Procedura: UPDATE-FROM-GIT-0.8.4.md. Problemi e metodi riusciti:
KNOWN-BUILD-UPDATE-PROBLEMS.md. Prove: VALIDATION-0.8.4-20261114.md.

## Riscontro client del 14 novembre 2026

Desktop1 e Desktop2: Refresh tools eseguito dalle impostazioni dell'app ChatGPT; la scheda
strumenti espone agent_update/agent_update_status e la descrizione device_call
include agent_update e i batch offline. Desktop1 verificato dopo il ripristino
del collegamento; anche agent_update_status del target e stato letto con successo
attraverso il gateway aggiornato. Non attribuire al client lo stato del solo server.
Il catalogo della chat corrente puo restare quello caricato all'avvio: la
compatibilita task_start/runtime resta utilizzabile.
