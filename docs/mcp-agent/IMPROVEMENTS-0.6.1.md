# Windows MCP 0.6.1 — specifica approvata e implementata

31 ottobre 2026. Autorizzato in Voice: sviluppare tutti i miglioramenti discussi; niente modifiche Android, NestingOpt o altri PC.

## Decisioni

- Le attese RPC Codex non devono mantenere il mutex globale del runtime. La prenotazione della coda rimane starting/running; i cambi di ciclo di vita della stessa sessione sono esclusivi. Stato, coda, job e interruzioni restano accessibili. Timeout RPC non equivale a stop del turno e non consente replay.
- Nessun arresto del turno per durata o silenzio. Rimosso watchdog di cinque minuti. Nessun avviso temporizzato: proposta a 30s/1m/5m/10m esplicitamente annullata. Interruzione solo su comando utente; errori/disconnessioni conservano esito incerto e richiedono verifica. I timeout dei profili di compilazione/comandi restano distinti e invariati.
- Coda operativa: solo attesa, avvio, esecuzione ed esiti da verificare. Terminali completed/failed/cancelled/interrupted/timed_out archiviati sul disco del computer esecutore, fuori dai repository, con identita PC/progetto/attivita, richiesta tecnica, esito e riferimenti a job/sessione/ricevute. final_usage del turno conservata nello storico. Nessuna cancellazione o replay automatico.
- Scrittura archivio prima della rimozione da queue.json. Duplicati identici dopo crash accettati, conflitti fermano elaborazioni senza sovrascrivere. Dipendenze risolte anche nell'archivio. activity_history legge pagine limitate o queue_id esatto, senza caricare tutto lo storico. Il limite di 5000 ora riguarda soltanto le attivita operative. Job conclusi recuperati da disco, non tutti mantenuti in memoria.
- Ogni computer futuro avra il proprio stato/archivio; nessun altro PC viene configurato in questa release.
- Su Git del singolo progetto: registro curato delle attivita significative con modifiche, decisioni, test/esiti, limiti e riferimenti ai commit. Aggiornato con commit/rilascio; niente log grezzi, prompt, ricevute, account o credenziali. Non generare commit a ogni lettura o operazione tecnica. Vedere PROJECT-RECORD.md.
- Risorse: protezione percorsi sovrapposti e identita comune Git (incluse linked worktree); dichiarazioni aggiuntive resources=[{name,mode}]. Nomi port:tcp:N/port:udp:N, process:etichetta, shared:etichetta; mode read/write. Solo aggiunta di coordinamento, mai riduzione delle protezioni o concessione di accesso. Lettori compatibili su risorse dichiarate possono procedere in progetti indipendenti; scrittori confliggenti attendono. Risorse repository lette da build/test/status, esclusive per Codex e fetch/pull. Massimo quattro lavori indipendenti.

## Interfaccia e limiti

queue_list mostra solo coda operativa, resources e blocked_by. Terminali via activity_history (project opzionale, queue_id oppure offset/limit max 200). job_list usa pagine su disco in ordine di memorizzazione, non temporale, e has_more; nessuno snapshot atomico durante nuovi inserimenti. Consultare il singolo job_id per stato attuale. Cataloghi precedenti: task_start operation=runtime con request completa.

Risorse dichiarate coordinano soltanto questo agente. Nessuna scoperta universale di porte/processi e nessun blocco del sistema operativo contro processi esterni. L'assistente deve dichiarare le risorse note prima di accodare; nomi logici condivisi devono essere coerenti sul medesimo PC. I profili git_status/diff rimangono accodati per percorso; letture API dirette disponibili. Le guardie economiche, prove e scadenze non cambiano.

## Stato

Installata su Desktop1 e collaudata: test Windows/vet/build, stress RPC, turno simulato oltre cinque minuti, collaudo MCP isolato, backup/migrazione/rollback e turno Codex reale superati. Vedere VALIDATION-0.6.1-20261031.md. Nessuna promessa di disponibilita universale in altri client, su altri PC o dopo reboot fisico.
