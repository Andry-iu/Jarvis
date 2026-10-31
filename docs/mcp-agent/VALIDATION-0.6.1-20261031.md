# Windows MCP 0.6.1 — installata e collaudata, 31 ottobre 2026

## Identita e provenienza

Desktop1 DESKTOP-1ESSB0K, machine_id 1b927a030fa3b7dfa15c40cfccb4ff60. Agente Windows distinto dall'app Android. Repository Andry-iu/Jarvis, branch reconcile-windows-mcp-agent-20261031; nessun merge a main o modifica Android/NestingOpt/altri PC.

Sorgenti del binario: 164cb980bd1a936749d41b8692979e98b929cb04. Build effettuata su Desktop1, Windows AMD64, Go 1.27.0. Binario operativo D:\Jarvis\jarvis-mcp.exe, 12.092.416 byte, SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1. Le modifiche documentali successive non cambiano il binario.

## Ambito consegnato

- RPC Codex senza mutex globale durante l'attesa; avvio Codex asincrono rispetto allo scheduler. Prenotazione starting persistita prima del dispatch, ciclo di vita della stessa sessione protetto, stato/interrupt disponibili; nessun replay dopo esito incerto.
- Eliminato il watchdog di cinque minuti dei turni. Nessun arresto per durata o silenzio e nessun avviso temporizzato (proposta annullata dall'utente). Interruzione utente preservata. I timeout dei profili di comando restano distinti e invariati.
- Terminali spostati in archivio privato per computer, prima della rimozione da queue.json. Dipendenze risolte anche nello storico. Conflitto/corruzione archivio ferma la coda senza sovrascrittura. Esiti incerti/persistence_failed restano operativi fino a verifica. activity_history: pagine limitate o queue_id; final_usage conservata per nuovi turni. Storico job su disco, non tutto residente in memoria.
- Risorse aggiuntive read/write dichiarate per porta/processo/risorsa logica; riconoscimento repository Git comune, incluse linked worktree. Percorsi sovrapposti esclusivi, massimo quattro lavori indipendenti. Risorse note dichiarate dall'assistente, senza dedurle dal testo libero.
- Registro significativo su Git del progetto, aggiornato dal workflow con commit e rilasci; archivio tecnico completo e ricevute sul computer, mai esportati automaticamente su Git. Regola in PROJECT-RECORD.md; registro di questo progetto in ACTIVITY.md.

## Evidenze

| Verifica | Esito |
|---|---|
| Base reale | Desktop1 0.6.0, HEAD ea8b3ae, tracciati puliti, nessun lavoro attivo. |
| Test Windows finali | Job 042789f36f8b61e74c349919c9bb3225 completed/exit 0, 18:54:27–18:54:43 UTC. Coperti archivio, crash tra copia e compattazione, conflitti senza perdita, pagine/progetti, dipendenze storiche, risorse condivise/worktree, RPC lenta, interrupt durante RPC, scheduler indipendente e compatibilita delle risorse opzionali. |
| Vet/build | Job d98fa5907a8c6b9a8a23e515542198cb e 11c8bd42a81f5322d3a882cbd67f8660 completed/exit 0. Artefatto Staging/build-11c8bd42a81f5322d3a882cbd67f8660.exe. |
| Turno lungo senza inferenza | Test esplicito TestLongCodexTurnContinuesUntilUserInterrupt, processo RPC simulato, nessuna rete/account reale. Turno mantenuto attivo per 310 secondi; interruzione esplicita poi archiviata con final_usage. Durata suite 312.133s, exit 0; diagnostica 829e20ac045e483481a1b5ab9507dcc2 PASS. |
| Collaudo MCP isolato | scripts/test-runtime-061.ps1 e diagnostics/v61-queue-test.log: PASS riordino/cancellazione, letture concorrenti, serializzazione, risorse dichiarate con progetto indipendente, riavvio senza replay, ripresa esplicita, coda compatta e archivio paginato. Diagnostica 01e1cc57e1f944e89e24bb3371059f1a PASS. |
| Correzioni durante il collaudo | Import JSON inizialmente mancante corretto; directory di formattazione isolata con go.mod; test Windows aggiornato a cercare gli esiti terminali nell'archivio; risorse Codex mantenute opzionali con regressione per vecchi client. I test finali superano questi casi. |
| Backup | D:\Jarvis\Backups\mcp-0.6.1-20261031, binario 0.6.0, configurazioni/startup/prova costi e snapshot JSON dello stato copiati con verifica hash. Log BACKUP_VERIFIED. |
| Migrazione/rollback isolati | Stato copiato nel percorso di prova; avvio candidato 0.6.1 e precedente 0.6.0 sullo stesso percorso isolato, health/MCP verificati: ROLLBACK_REHEARSAL_PASS. Archivio preservato; il vecchio server non espone activity_history. |
| Installazione | Iniziale tentativo respinto dalla revisione automatica, nessun processo lanciato. Dopo autorizzazione esplicita utente all'installazione: diagnostica 6551d9734e474046bf3708f9913e2fc3 exit 0/installed. Log 19:01:53 UTC: INSTALLED_PID 503652, hash corretto e PASS installed_health_and_mcp. machine_info dal plugin conferma 0.6.1 e identita invariata. |
| Storico reale dopo migrazione | queue_list vuota/sana; precedente attivita 7903339aae39ee439e2bb844b33ebeb2 recuperata tramite activity_history e pagina di due voci con has_more=true. |
| Comando installato | git_status queue b874dd08908ae159eb0a020f82ea2a37, job 649d80b9a685068afb83e9db31cb0632 completed; voce rimossa dalla coda e consultabile nell'archivio. |
| Codex reale installato | Sessione precedente riagganciata esplicitamente, thread 01a1931a-108e-71e1-bbe8-fb6f44f680d6. Queue a0c82f1dcbd89460b6abc90ed262757d, turn 01a19358-f94c-7a83-ab56-4eb2b9de7cc8 completed; risposta JARVIS_MCP_061_OK. Nessuno strumento o modifica file richiesti al modello. |
| Quota finale persistita | jarvis/usage sequenza 42, final_usage nell'archivio e codex_events, usage_pending=false. Lettura 19:04:07 UTC (20:04 italiane): 86% residuo, crediti balance=null/unknown, reset 4 novembre 13:39:22 UTC (14:39 italiane). Prima del turno balance era numerico zero: non riutilizzarlo per la lettura finale. |

## Costi, compatibilita e limiti

Nessun acquisto, ricarica, upgrade, API key o fallback a consumo. Guardia account/quota/costi e prova privata non modificate ne prorogate. Riscontro per saldo null invariato fino al 7 novembre 2026 15:55 UTC (16:55 italiane). I test lunghi non usano inferenza; un solo turno breve reale e stato eseguito sulla quota inclusa dopo le guardie.

Catalogo server aggiunge activity_history: usare anche task_start operation=runtime con request completa. Non e stato verificato il refresh del catalogo diretto client. Le chiamate MCP di questa conversazione sono verificate; altre chat/client, altri PC fisici e reboot completo del computer restano da collaudare.

Risorse cooperative del solo agente: processi esterni/altre istanze non dichiarate non sono bloccati dal sistema. Ordine delle pagine su disco, non cronologico e non snapshot atomico; per un risultato preciso usare queue_id/job_id. L'archivio non ha cancellazione automatica; pianificare eventuale retention separatamente. I nuovi riepiloghi non includono automaticamente i vecchi final_usage gia presenti solo negli eventi, ma conservano i relativi thread/turn per consultarli.

## Ripresa e rollback

Leggere windows-mcp-agent/AGENTS.md, CONTINUITY.md, IMPROVEMENTS-0.6.1.md e PROJECT-RECORD.md. Prima machine_info e project_catalog, poi queue_list/job_list, activity_history per terminali e sessioni del progetto esatto. Mantenere configurazioni, ricevute, archivio e prove fuori da Git. Conservare Staging, update.go.bak, stash e backup precedenti.

Per rollback usare il binario 0.6.0 del backup verificando il suo SHA-256 68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE, con arresto controllato e verifica health/MCP. Conservare sempre l'archivio della 0.6.1: il server 0.6.0 non lo consulta ma non lo elimina. Non ripristinare ciecamente uno snapshot di stato precedente o ripetere operazioni dall'esito incerto. Lo script di deploy effettua rollback del binario/config su errore di avvio, preservando lo stato piu recente.

## Controllo finale confermato

scripts/verify-release-061.ps1 e diagnostics/v61-final-check.log, 19:07:34 UTC: PASS. Listener PID 503652 con token elevato, hash installato invariato, 44 strumenti server (cinque queue_* e activity_history), 130 file JSON nel backup, 28 voci gia nell'archivio, zero listener temporanei, documenti locali presenti, update.go.bak preservato e prova costi identica al backup. HEAD/origin 1ca266200cc4b906a6436df774a3f7f63d85ad3d e tracciati puliti; successivo project_status alle 19:07:55 UTC conferma zero modifiche. Il primo riepilogo dopo il pull aveva mostrato nove modifiche transitorie, risolte dalla verifica completa senza ripristinare o riscrivere sorgenti.

Coda finale vuota e sana, nessun turno o approvazione pendente. Questo aggiornamento successivo riguarda soltanto il verbale finale, senza modificare codice, binario o configurazioni. Verificare anche il successivo HEAD/origin al termine della sincronizzazione documentale.
