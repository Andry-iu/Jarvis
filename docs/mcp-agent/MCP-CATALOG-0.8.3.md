# Catalogo operativo MCP — 0.8.3

## Origine e verifica

Il catalogo autorevole e `tools/list` del server attivo, confrontato con
`windows-mcp-agent/main.go` e `runtime_api.go` del sorgente pubblicato.
Il 14 novembre 2026 Desktop1 restituisce per `device_call`:

> Forward a public agent tool to a configured device, including updates, over authenticated gateway hops. Verify target identity; loops/retired peers refused. Keep target request_id/update_id on uncertainty.

Il catalogo esposto alla chat conserva invece la descrizione storica
“Never forwards update batches or nested routing”. La parte sugli aggiornamenti
e obsoleta: il runtime inoltra gli strumenti pubblici, inclusi gli aggiornamenti.
Non e necessario modificare l'agente o riassociare il PC per usarli. Il refresh
della cache ChatGPT e distinto dalla verifica del catalogo server: non dichiararlo
eseguito senza rileggere gli strumenti effettivamente esposti dal client.

## Operazioni per un dispositivo gia associato

| Operazione finale | Uso e prova richiesta |
|---|---|
| `machine_info` | Identita stabile, versione e privilegi del target prima di agire |
| `update_prepare` | Batch CRLF, ID 32-hex e SHA-256 esatti; massimo 262144 byte UTF-8 |
| `update_execute` | Avvio separato dello stesso batch; non certifica installazione |
| `update_status` | Ricevuta durevole e stato del lancio/uscita; nessun replay incerto |
| `log_read` | Log consentiti del target, incluso `diagnostics\self-update.log` |
| `file_read` / `file_write` | File di progetto e appunti con SHA atteso, backup e ricevuta |

Esempio di inoltro, usando identita e ID risolti dai record correnti:

```json
{
  "machine_id": "<identita-gateway>",
  "device_id": "<alias-target>",
  "call": {"operation": "machine_info"}
}
```

Per aggiornare, sostituire la call con `operation: update_prepare` e i campi
`update_id`, `sha256`, `content`, poi `update_execute` con lo stesso ID/hash.
Per strumenti che hanno un proprio campo `operation`, usare
`call={operation:<tool>,arguments:{...}}`. Non mescolare formato piatto e arguments.
Il machine_id interno, se presente, deve essere quello finale. Non annidare
manualmente `device_call`: i passaggi intermedi sono nei record `via` autenticati;
restano i controlli di pin TLS, identita, cicli e dispositivi dismessi.

Se manca uno strumento runtime nel catalogo client, e disponibile la compatibilita
`task_start(operation=runtime, request={operation:...,machine_id:...})`.
Un vecchio testo descrittivo non dimostra assenza di una capacita: leggere
catalogo live, sorgente pubblicato e ricevute prima di dichiarare un limite.

## Aggiornamento senza passaggi manuali

Un PC gia installato e associato si aggiorna attraverso il gateway esistente.
Il gateway puo trasferire un candidato verificato in blocchi tramite batch
preparati dall'agente target, fuori da Projects, quindi far invocare al target
l'unico `scripts/update-agent.ps1`. Ogni blocco ha hash, ID e ricevuta propri;
archivio assemblato, eseguibile e updater sono verificati prima della promozione.
Il trasferimento non e un secondo algoritmo di installazione.

Usare credenziali mantenute localmente dagli agenti, senza leggerle nel modello,
nuovi listener o apertura di porte. Non richiedere Git/Go al destinatario.
Preservare configurazione, fiducia, avvio, privilegi e stato. Nessun nuovo pairing,
consenso sintetizzato, task di aggiornamento o `taskkill /T`.

Il completamento richiede riconnessione autenticata, identita/versione/hash,
backup verificato, rehearsal rollback, ricevuta canonica e manutenzione sane.
Una risposta “launched” e il solo preflight non autorizzano “aggiornamento riuscito”.
Vedere [procedura unica](SELF-UPDATE.md) e [verbale remoto](REMOTE-UPDATE-0.8.3-20261114.md).
