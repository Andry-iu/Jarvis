# Aggiornamento LAN 0.8.2 → 0.8.3 — 14 novembre 2026

## Risultato verificato

Il PC LAN gia associato e stato aggiornato automaticamente attraverso Desktop1,
senza azioni manuali sul destinatario, nuovo abbinamento o nuovo tunnel.
Ricevuta dell'updater alle 17:19:25 UTC; riconnessione autenticata alle 17:20 UTC:
versione 0.8.3, identita invariata, nuovo PID e privilegi elevati confermati.
Eseguibile nel percorso `<home>\Agent\jarvis-mcp.exe`, supervisore esistente
`Scripts\Start.ps1` e configurazioni conservati.

Usato il candidato Windows nativo Desktop1 gia collaudato, 13424128 byte,
SHA-256 `76e05315055ba3b2c2cc61ae97e17de9082c19c85fe6ec6592743a06ba858b29`,
sorgente runtime `50edce2c868664fb9ad2c040746284642a0e555c`.
Nessuna ricompilazione o nuova versione. Lo ZIP installer distribuito resta il
pacchetto distinto gia registrato nei checksum: non confonderne hash e dimensioni
con l'archivio temporaneo usato per questo trasferimento.

## Trasporto ed esecuzione

Un batch separato avviato dall'agente Desktop1 usa il proprio MCP loopback e
`device_call` sul collegamento TLS gia associato. Credenziale locale ereditata,
mai esposta al modello o nei parametri/log. Archivio candidato/updater di 7388153
byte trasferito in 57 blocchi (massimo 131072 byte), ognuno con hash e ricevuta;
batch sotto il limite di 262144 byte UTF-8. Verificati hash dell'archivio
assemblato, candidato e updater prima di eseguire il motore canonico.

L'updater trasferito e quello pubblicato, identico dopo normalizzazione CRLF/LF:
SHA-256 Windows CRLF `bcc6f9491982397c925486b938318bfe0316a811f2b536f79189b70ec85932a9`;
SHA-256 sorgente LF `e815957813fafd3cdde95271355b72033029fd3df6d2d27650c4c1cc0724ac1f`.
Il trasferimento prepara solo i file; la sostituzione resta esclusivamente
`update_prepare` → `update_execute` → batch separato → `update-agent.ps1`.
Nessun Git/Go richiesto sul target, nuovo listener, task di aggiornamento,
modifica del profilo firewall o arresto dell'albero processi.

## Prove e pulizia

- `BACKUP_VERIFIED`, `ROLLBACK_REHEARSAL_PASS`, hash installato e
  `PASS common_batch_update_identity_config_maintenance` nel log canonico.
- Ricevuta `installed-receipt.json` riletta e confrontata con versione, identita,
  sorgente e hash attesi; eseguibile del listener attivo ricontrollato.
- `machine_info` inoltrato riconnesso 0.8.3, stesso computer e token elevato.
- Manutenzione sana: nessun errore, intervallo 24 ore, retention log/batch 30 giorni.
- Zero processi di probe residui, porta isolata 8767 libera, zero blocchi/ZIP
  temporanei; backup definitivo conservato. Nessuna regola firewall residua
  riferita ai due eseguibili di probe di questa distribuzione.
- Appunti privati aggiornati su entrambi i desktop e sul target con hash atteso,
  backup e rilettura; inventario e ricevute dettagliate restano sui computer.

La ricevuta di lancio dell'ultimo batch resta `launched/verification_required`
dopo l'arresto del vecchio agente: il processo che ne attendeva l'uscita e stato
sostituito. Non e un motivo per rilanciare. Ricevuta canonica, hash/versione live
e pulizia risolvono l'incertezza e dimostrano l'esito effettivo.

## Causa dell'indicazione precedente errata

La descrizione client storica di `device_call` negava l'inoltro dei batch.
Era stata trattata come un limite attuale senza confrontarla con il catalogo
live e i sorgenti, che consentono gli aggiornamenti dal gateway gia nella 0.8.0.
Inoltre preflight e avvio erano stati riferiti come aggiornamento concluso:
prima di questo intervento il target rispondeva ancora 0.8.2, senza trasferimento
0.8.3 o ricevuta finale. La precedente affermazione di riuscita era quindi priva
di verifica e va considerata superata da questo verbale.

`tools/list` verificato su Desktop1 e Desktop2 restituisce gia la descrizione
corretta. Le descrizioni memorizzate negli strumenti di questa chat sono ancora
vecchie: nessun refresh client certificato. Gli strumenti di gestione disponibili
non espongono il refresh e la ricerca Jarvis non restituisce un plugin modificabile;
non alterare un'altra integrazione o dichiarare una modifica della cache non avvenuta.
Il [catalogo operativo](MCP-CATALOG-0.8.3.md), le istruzioni e gli appunti ora
registrano il comportamento corretto e permettono di operare senza riconnessioni.

Non eseguiti reboot fisico o nuovo abbinamento umano con il campo codice 0.8.3;
restano distinti dal riavvio agente e dall'aggiornamento riuscito.
