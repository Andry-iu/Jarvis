# Windows MCP 0.5.7 — candidato in sviluppo, 31 ottobre 2026

Autorizzazione esplicita dell'utente: sviluppare insieme file/cartelle, comandi, catture dirette, ponte Codex locale, identita dei computer, continuita e integrazione plugin. Rilascio separato dall'app Android; branch reconcile-windows-mcp-agent-20261031. La 0.5.6 resta operativa fino al collaudo e alla prova di rollback. Nessuna compilazione GitHub Actions.

## Contratto

machine_info restituisce machine_id persistente. Tutte le nuove operazioni richiedono questo ID esatto. project_catalog restituisce progetti confinati a JARVIS_PROJECTS_ROOT e ID derivati da computer/percorso. Un progetto e identificato dal percorso relativo restituito, incluso `.` per la root autorizzata. Nessun accesso implicito a un altro PC.

Le funzioni sono pubblicate come strumenti MCP distinti. Compatibilita per cataloghi ChatGPT gia memorizzati: task_start con operation=runtime e request contenente l'intera richiesta, incluso operation e machine_id. Questo percorso non dimostra che ChatGPT abbia aggiornato il catalogo.

File: file_list/read/search/write, directory_create, path_move e file_restore. Testo UTF-8 e dati base64 fino a 1 MiB. Rinomina/spostamento comprende file E cartelle all'interno dello stesso progetto e volume; destinazioni esistenti e spostamenti fra volumi sono rifiutati. Le directory nuove possono avere genitori mancanti, registrati per l'annullamento. Root, percorsi di sistema, ADS, nomi DOS, link/reparse point, hardlink e percorsi delle credenziali sono esclusi. Accesso tramite os.Root e verifica del percorso finale degli handle su Windows. La protezione riguarda l'API file: il codice autorizzato in esecuzione mantiene i privilegi OS dell'agente.

Scritture con expected_sha256 obbligatorio (`missing` per creare), temporaneo sincronizzato, sostituzione e verifica, backup privato verificato prima della modifica. Verifica ottimistica immediatamente prima della sostituzione, serializzazione delle operazioni Jarvis; non e una transazione CAS con tutti gli editor esterni. Il sistema operativo/filesystem determina l'atomicita della sostituzione. Per spostamenti: rename nativo per handle, no-replace, senza copia/cancellazione fra volumi. Il journal conserva il percorso inverso e il digest; restore rifiuta contenuti o destinazioni modificati. Nessuna cancellazione generica o sovrascrittura forzata.

Ogni mutazione ha request_id monouso persistito prima del dispatch. Ripetere esattamente una richiesta completata restituisce la ricevuta; ID riusato con altri argomenti viene rifiutato. Esito incerto/disconnessione non autorizza replay. Stato in JARVIS_HOME/State/runtime-v1, oppure JARVIS_STATE_DIR esplicitamente configurata, con lock OS fra istanze. Conservare integralmente questo stato nei backup. I job interrotti da riavvio diventano interrupted, mai eseguiti nuovamente; cio non certifica la terminazione di eventuali processi sopravvissuti a un crash.

Comandi: profili fissi Go test/build/vet, Git status/diff/fetch/pull ff-only, .NET test/build, CMake configure/build e MSBuild. Nessuna stringa shell o argomento arbitrario. Solo toolchain effettivamente installate, directory autorizzata, ambiente dei figli filtrato, output limitato, timeout 1-600 secondi, cancellazione albero processi Windows. job_status/list/cancel e request_status persistono oltre la chat. Test/build di progetto possono eseguire codice con privilegi dell'agente: non sono una sandbox OS.

screen_capture restituisce text+image JPEG MCP direttamente, senza log base64 o URL pubblici. Monitor/area, dimensioni originali, origine anche negativa, dimensioni ridotte, fattori di scala e timestamp. Richiede desktop interattivo Default sbloccato e JARVIS_DESKTOP_ENABLED=1. Nessun bypass blocco/UAC e nessuna deduzione di coordinate per clic. Le salvaguardie desktop 0.5.6 restano separate e invariate.

## Codex locale senza acquisti aggiuntivi

JARVIS_CODEX_ENABLED=1 e percorso assoluto JARVIS_CODEX_EXE, oppure scoperta univoca del binario incluso nel pacchetto OpenAI.Codex installato. Protocollo fissato al CLI 0.165.0-alpha.1.2; per altre versioni rigenerare/verificare gli schemi prima dell'abilitazione. App Server stdio locale, nessuna porta aggiuntiva, nessuna chiave API nel processo figlio. Provider OpenAI e login ChatGPT, tier normale, MCP/app esterni e multi-agent disabilitati, sandbox workspace-write e approvazioni per singola azione. Nessun acquisto, reset quota o fallback API.

Prima di avviare/riprendere sessioni o inferenza: account chatgpt, piano verificato self_serve_business_prolite, bucket codex valido sotto il 90%, credito zero, nessun credito illimitato/pagato e nessun limite di spesa raggiunto. Condizioni ignote bloccano il turno. Questi controlli sono una politica locale prudente, non un contratto sulle politiche di fatturazione future. Nessuna modifica al piano o ai sistemi di pagamento.

codex_status legge piano/quote senza inferenza. codex_threads/read sono circoscritti al cwd del progetto; codex_start/resume collegano sessioni; codex_turn/steer/interrupt gestiscono turni; codex_events espone eventi persistenti e richieste pendenti; codex_respond risponde a una richiesta esatta, senza grant di sessione. La conferma di un comando deve corrispondere ad autorizzazione esplicita dell'utente per l'azione mostrata. Dopo errore/riavvio richiedere resume esplicito, mai duplicare turni. Timeout locale turno 5 minuti con richiesta interrupt, da verificare negli eventi.

La visibilita dipende dal profilo locale Codex; non comprende automaticamente chat ChatGPT o lavori cloud. Il controllo di attivita di altre istanze e limitato a quanto espone App Server: non promettere coordinamento globale fra processi. Storico read limitato a 256 KiB; eventi locali ultimo 1 MiB/500 eventi per thread, con history_gap e cursore espliciti. Approvazioni perdute dopo riavvio non vengono ripetute automaticamente.

## Piu computer e chat

Config locale Config/devices.json: elenco di device_id, machine_id, endpoint HTTPS /mcp, token_env oppure access_id_env/access_secret_env. Solo riferimenti a segreti, mai valori in Git o chat. device_list rileva online e identita; device_call verifica identita prima del dispatch e non segue redirect, non inoltra batch update o routing annidato. Nuovi PC richiedono agente, autenticazione e progetto autorizzato configurati su quel PC. Test loopback soltanto con JARVIS_TEST_PEERS=1 nell'ambiente di collaudo.

Disponibilita in una nuova chat o in Voice dipende dalle capacita del client ChatGPT e dall'esposizione del plugin. Il backend non puo garantire strumenti in ogni superficie o autenticare automaticamente altri computer. Collaudare nel client effettivo; non presentare l'autonomia di un job locale come permanenza illimitata della conversazione.

## Stato delle verifiche

Collaudo Windows candidato superato: suite Go e go vet, build Windows, percorsi/credenziali/alias/hardlink, hash obsoleto e no-replace, backup/ripristino, replay/restart, test/build/timeout/cancellazione, cattura JPEG diretta su due monitor (origine -1920, larghezza originale 3840), peer autenticato/identita errata/offline. I nuovi comandi sono contenuti in Job Object Windows, avviati sospesi e associati al job prima di creare figli; la chiusura del job termina i discendenti. Stato protetto con ACL per account corrente e SYSTEM. Nessuna installazione 0.5.7 ancora dichiarata in questo punto della documentazione: vedere STATUS per l'esito successivo.

Codex App Server reale verificato per versione, avvio stdio, piano e lettura quote. Al 31 ottobre 2026 il piano e self_serve_business_prolite, quota primaria 12%, hasCredits=false, unlimited=false, balance=null. Il controllo rigoroso blocca avvio/ripresa/turni con saldo nullo. Nessuna inferenza Jarvis avviata. La revisione automatica ha respinto una proposta di accettare il saldo nullo e avviare un test: la proposta e stata annullata nel sorgente, con regressione dedicata per il blocco del saldo sconosciuto. Non allentare la guardia o considerare collaudati i turni reali. Occorrono condizioni di costo verificabili/autorizzazione appropriata prima di completare questo collaudo. Conservare update.go.bak, stash backup update.go locale 20261031 e backup release precedenti.

Fonti primarie consultate per il protocollo: https://learn.chatgpt.com/docs/app-server, https://learn.chatgpt.com/docs/auth?surface=cli, https://learn.chatgpt.com/docs/config-file/config-reference. Schemi generati dalla CLI realmente installata sul PC.
