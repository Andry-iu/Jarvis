# Windows MCP 0.7.0 — comandi diretti e manutenzione

Ambito autorizzato il 1 novembre 2026: aggiornare gli agenti Desktop1 e Desktop2, poi ripetere apertura/scrittura/salvataggio/chiusura di Blocco note senza batch per le operazioni desktop. Non modificare Android, Cloudflare, guardie economiche, identita o configurazioni degli altri progetti.

## Contratto

- `process_start`: eseguibile, argomenti separati e cartella di lavoro; avvio libero. Default non amministratore, anche con agente elevato: token della shell interattiva verificato non elevato, stessa sessione. Se indisponibile, errore senza fallback amministratore. `elevated=true` soltanto su richiesta esplicita; se agente non elevato, errore senza UAC automatico.
- `window_list`, `window_inspect`, `window_focus`, `window_close`: identita legata a HWND/PID/istante di avvio, controllo sessione e desktop sbloccato. Chiusura ordinaria con esiti closed/needs_input/pending; nessuna terminazione forzata o perdita automatica di bozze.
- `ui_action`: invoke/select/toggle/focus/set_text/type_text per controlli ispezionati, inclusi Document moderni. Snapshot monouso di 90 secondi, identita/contenuto/focus ricontrollati. La sostituzione dell'intero testo richiede intento esplicito.
- `ui_keys`: una combinazione di tasti sulla finestra verificata e in primo piano.
- `ui_file_dialog`: apertura/salvataggio tramite dialogo standard dell'applicazione, percorso assoluto e verifica del file risultante; `expected_text` facoltativo. Non scrive direttamente il documento. Nessuna sovrascrittura predefinita; overwrite richiede hash corrente e autorizzazione esplicita. Esiti incerti non vengono ripetuti.
- `known_folder`: Desktop/Documents/Downloads effettivi dell'utente, inclusi reindirizzamenti.
- `maintenance_status`: conservazione, ultimo/prossimo controllo ed esiti. Log e batch generati dall'agente: default 30 giorni se configurazione assente/non valida; pulizia all'avvio e ogni 24 ore. Batch preparati/in esecuzione, file estranei, startup, stato e backup esclusi.

Ogni nuova mutazione richiede request_id persistente: replay identico completato restituisce la ricevuta, in corso/incerto non viene rieseguito, riuso con parametri diversi e rifiutato. Le attese UI non trattengono il mutex globale del runtime; i controlli UI dello stesso agente sono serializzati. Le implementazioni desktop usano un worker PowerShell incorporato e API Windows/UIA: l'assistente non deve preparare/eseguire batch per questi comandi. Update batch rimangono necessari per distribuire l'agente precedente e per collaudi tecnici non coperti dai profili fissi.

## Collaudi e distribuzione

Candidato costruito su Desktop1. Test Windows, verifica token reale del figlio non elevato, parser PowerShell/C# del worker, retention con batch vecchi/nuovi/preparati/in esecuzione e duplicazione richieste. Regressione aggiunta per Rect.Empty di UIA: coordinate infinite sono convertite in rettangolo nullo, mai in JSON non valido; azioni su rettangoli nulli rifiutate. Il limite output worker resta 65536 byte.

Stato installazione/collaudo reale: consultare STATUS.md e ACTIVITY.md. Non interpretare una build come installazione, il dispatch come successo UI, o il controllo all'avvio come trascorrere reale di 24 ore. Desktop bloccato, sessione 0 e dialoghi non standard non sono controllabili. Stato persistente/ricevute/backup restano privati sul PC, fuori da Git. Nessuna inferenza o costo aggiuntivo.

## Ripresa

Usare machine_info su ciascun PC, verificare versione/identita; poi queue_list/job_status e ricevute. Cataloghi client precedenti: task_start con operation=runtime e request completa. Dopo installazione, aggiornare gli strumenti di ciascun plugin e verificare il catalogo diretto. Gli script di test e deploy sono in windows-mcp-agent/scripts/test-direct-070.ps1 e deploy-runtime-070.ps1; il deploy verifica backup, migrazione/rollback isolati, hash binario, identita, configurazioni immutate e manutenzione.
