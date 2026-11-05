## 0.7.4 — 2026-11-05

- Registro privato dei token Codex per incarico/progetto/categoria, senza testo dei dialoghi.
- Report codex_token_usage: filtro development predefinito, riepiloghi sull'intero insieme filtrato e paginazione dei dettagli; contatori assenti null.
- Snapshot cumulativi sostituiti, non sommati; cache e ragionamento sono sottoinsiemi, senza doppio conteggio. Confini incerti, riprese, turni esterni e reset espliciti.
- Raccolta prima del dispatch, esiti falliti/interrotti inclusi se osservati; persistenza atomica e nessun replay. Telemetria incoerente impedisce nuovi dispatch, non cancella stato.
- Suite senza modelli/API: contatori, categorie, persistenza, privacy, errori e integrazione reader con processo RPC simulato.

# Agente 0.7.3 - stato amministratore e riavvio elevato

3 novembre 2026. Implementati privileges in machine_info/agent_status e nuovi
agent_restart_admin/agent_restart_status, anche via task_start/runtime.
Token effettivo del processo: true/false/null; mai dedurre i privilegi dall'account.
Gia amministratore: no-op. Lavori attivi/accodati/incerti: rifiuto senza stop.
Ricevute persistenti, gate delle mutazioni e handoff locale autenticato.
UAC puo richiedere conferma sul PC; annullamento prima del passaggio conserva
l'originale. Successo solo dopo nuovo PID/identita/versione/token verificati.
Nessun nuovo task/servizio, nessuna modifica UAC o alle applicazioni standard.
Specifica e suite: docs/mcp-agent/ADMIN-CONTROL-0.7.3.md.
Stato di test/installazione aggiornato in VALIDATION-0.7.3-20261103.md.
Sviluppo sul branch feature/agent-admin-control-20261103; base 6ff603a.
Le sezioni successive sono storiche.

# Agente 0.7.2 - installato e collaudato su entrambi i desktop

3 novembre 2026. Desktop1 e Desktop2 rispondono con 0.7.2, identita e root Projects
preservate. Sorgenti runtime pubblicati nel commit 31c900d240b2188d6e3ef7d8577bd0951fc87dcd
sul branch reconcile-windows-mcp-agent-20261031 di Andry-iu/Jarvis.

Contatori separati in D:\Jarvis\State\tool-usage.json, fuori da Projects e Git.
tool_usage legge classifica o query esatta, anche via task_start/runtime senza
doppioni. Risposte immediate riuscite/fallite e chiamate senza esito; nessun
argomento o contenuto registrato. Non misura quota ne completamento dei job.
Retry e prove contano; tool_usage include se stessa ancora in corso.
Stato preesistente preservato, comprese le date di inizio raccolta:
Desktop1 2026-11-03T17:53:58.4575006Z, Desktop2 2026-11-03T18:12:52.4840496Z.
Le chiamate di validazione sono invocazioni e sono comprese nei contatori.
Scritture atomiche sincronizzate, stato corrotto conservato senza azzerarlo,
operazioni disponibili anche su guasto della telemetria.

Verificati: suite Go/vet/build dei candidati su entrambi, suite MCP isolata
scripts/test-tool-usage.ps1 e collaudo installato scripts/verify-tool-usage-live.ps1.
Updater canonico, backup/rehearsal, installazione, riconnessione MCP, identita,
hash/configurazione/manutenzione e persistenza dopo riavvio effettivo PASS.
Nessuna inferenza Codex. Verbale VALIDATION-0.7.2-20261103.md e CHECKSUMS-0.7.2.json.
Il catalogo client diretto non risulta aggiornato; compatibilita funzionante.
Reboot fisico e ciclo reale 24 ore non certificati.
Installer 1.3.1/agente incluso 0.7.1: release distinta.
Le sezioni successive sono storiche.

# Agente 0.7.1 / installer 1.3.1 - batch silenziosi

1 novembre 2026. Lanciatore batch con CREATE_NO_WINDOW e HideWindow, senza DETACHED_PROCESS; token ereditato e sopravvivenza all'uscita del genitore verificati dal test Windows. Codice runtime pubblicato nel commit a8b0bca1901d96de9604388bbec5ec9b8aecc847. go test, vet e build Windows superati su entrambi i computer (Desktop2 build standard; il tentativo separato con -trimpath e fallito per eccezione interna del compilatore Go 1.27.3); suite installer SetupSettings, Bootstrap e Installer superate con installazioni isolate 0.7.1. Manifest completo di hash/dimensioni, updater canonico invariato. Agenti operativi ancora 0.7.0: backup verificato e prova isolata candidato/precedente superati su entrambi i computer; distribuzione 0.7.1 e push bloccati dalla revisione automatica in attesa di conferma esplicita dell'utente.

Il pacchetto usa il candidato Desktop1 SHA-256 aa3625aed0d3d008a615ed0e5666d820b69bd6abdb86cb44fe4b55f596ebde00, 12194304 byte. Non certificati reboot fisico, refresh cataloghi o programmi che richiedono esplicitamente una nuova console. Nessuna inferenza o modifica alle guardie economiche, ad Android o a main; nessun binario/ZIP in Git.

# Installer 1.3.0 / agente 0.7.0 — allineamento completo

1 novembre 2026. Installer aggiornato ad agente 0.7.0 e updater canonico comune. Nuove installazioni sotto `<root scelta>\Agent\jarvis-mcp.exe`; nessuna migrazione implicita dei percorsi esistenti. Un agente operativo precedente deve aggiornarsi tramite il proprio batch CMD separato prima del setup, che rifiuta un percorso alternativo. Task Windows solo per l'avvio ordinario.

Superati: contratto sorgenti/manifest, 45/45 test Node, suite Windows SetupSettings (51 asserzioni), Bootstrap e Installer (32 verifiche ciascuna + COMPLETE), due installazioni isolate con MCP reale. Produzione Desktop1 conservata, nessuna inferenza. Non sono prove di installazione 1.3.0 in produzione, API Cloudflare reali, refresh cataloghi, reboot fisico o ciclo reale 24 ore.

Regole consolidate in docs/mcp-agent/OPERATING-RULES.md: procedura comune e cause dei problemi dichiarate, privilegi applicazioni standard per default, salvataggio nell'app, manutenzione log/batch 30 giorni avvio/24 ore, guardie economiche, nessun replay incerto, identita distinte e Git completo di soli sorgenti/test/documentazione/risorse. Verbale docs/mcp-agent/INSTALLER-1.3.0.md; AGENTS e RELEASE_POLICY aggiornati. Nessun ZIP/binario generato o segreto aggiunto a Git, nessuna modifica Android/main.

Le sezioni seguenti sono storiche; prevale lo stato attuale qui sopra.

# Windows MCP 0.7.0 — entrambi operativi; aggiornamento unificato

1 novembre 2026. Desktop1 DESKTOP-1ESSB0K (1b927a030fa3b7dfa15c40cfccb4ff60) e Desktop2 AUTECHVPC2 (d41455adf8721c205183224d3c69a1a5) rispondono entrambi con 0.7.0, identita preservate. Prove Notepad candidato e installato superate su entrambi: avvio non amministratore, testo ciao, salvataggio tramite applicazione sul Desktop come ciao-070.txt, verifica dei 4 byte e chiusura ordinaria. SHA-256 documento b133a0c0e9bee3be20163d2ad31d6248db292aa6dcb1ee087a2aa50e0fc75ae2. Azioni installate eseguite tramite MCP task_start/runtime senza batch per scrittura/salvataggio/chiusura.

Procedura di aggiornamento richiesta dall'utente: agente -> update_prepare/update_execute -> batch CMD separato -> UNICO scripts/update-agent.ps1. Nessuna attivita Windows dedicata all'aggiornamento. Prova effettiva con sostituzione/riavvio 0.7.0 su entrambi: self-update.log termina PASS common_batch_update_identity_config_maintenance, dopo BACKUP_VERIFIED e ROLLBACK_REHEARSAL_PASS. Verificate riconnessione MCP, versione e identita. I due deploy precedenti sono ora semplici alias dello script comune.

Differenze di configurazione dichiarate: Desktop1 usa D:\Jarvis\jarvis-mcp.exe e avvio diretto; Desktop2 usa D:\Jarvis\Agent\jarvis-mcp.exe e l'esistente supervisore Scripts\Start.ps1, con manifest installation.json e tunnel. Lo stesso algoritmo rileva il processo attivo, verifica hash/identita, registra il profilo e applica gli stessi passaggi; nessun ramo per nome del computer. Percorsi e avvio delle installazioni non sono stati migrati implicitamente.

Causa dell'interruzione iniziale Desktop2: arresto /T dell'agente terminava anche il suo updater discendente. Risolta con arresto del solo PID verificato e gestione dell'avvio esistente. La soluzione intermedia con task temporaneo e stata rimossa, dopo chiarimento dell'utente; task eliminato verificato e prototipo fuori dagli entrypoint attivi. Job Windows del batch osservato con limit flags 0: nessun cambio di limiti, TrustedHosts o permessi. Il recupero iniziale non costituisce procedura ordinaria.

Binari invariati del sorgente runtime pubblicato 54aed96d94f2b02f35b31168ca8cafee217b4158: Desktop1 SHA-256 1542248E430042D1DAB7E913C889A1242318E6CDFD276EBEC22A927F4FF16B22 (Go 1.27.0); Desktop2 095AC88D21BC75AB4EF2AF232EF568C18CE03FC36A202FF5C038C98E2075BC08 (Go 1.27.3). Test Windows Desktop1 e vet/build entrambi superati; Desktop2 suite Go completa non trasferita. Log/batch default 30 giorni, controllo all'avvio e ogni 24 ore, stato sano su entrambi; un ciclo reale di 24 ore e reboot fisico restano da osservare.

Git pubblico autorizzato; nessuna modifica Android/main, segreti o inferenza. Codice/runtime equivalenti al commit locale 010f73c, preservato nel branch backup/windows070-local-010f73c. Verbale docs/mcp-agent/VALIDATION-0.7.0-20261101.md e procedura SELF-UPDATE.md. Prossimo passo utente: aggiornare gli strumenti di Jarvis-Desktop1 e Jarvis-Desktop2 e verificare il catalogo diretto. Non richiedere nuovamente autorizzazioni di pubblicazione/aggiornamento gia acquisite.

# Installer 1.2.2 — nomi Cloudflare derivati dal dispositivo

Tunnel, applicazione Access e policy inline sono creati come `jarvis-<DEVICE_NAME normalizzato>`: Desktop2 → jarvis-desktop2, Desktop3 → jarvis-desktop3, Notebook → jarvis-notebook. Tre override facoltativi nel file locale; hostname pubblico indipendente. Ripresa tramite ID registrati, compatibile con le rinomine manuali e con i vecchi journal. Nomi leggibili non costituiscono prova di proprieta: creazione con risposta persa senza ID resta bloccata, senza replay. Comando separato RINOMINA-CLOUDFLARE.cmd per risorse gia configurate, senza reinstallare o fermare l'agente.

Test Node nel cloud: **45/45 passati**. Collegamento MCP Desktop2 verificato direttamente, macchina AUTECHVPC2, agente 0.6.1, coda/job vuoti. Esecuzione Windows isolata e lettura nomi Cloudflare registrate nel [verbale](../docs/mcp-agent/INSTALLER-1.2.2.md); distinguere test, codice pubblicato e installazione. Nessuna operazione su Desktop1, nessuna inferenza e guardie economiche invariate.

# Installer 1.2.1 — parametri e segreti locali, senza domande console

Aggiunto `setup.local.env`, rilevato automaticamente accanto a `INSTALLA.cmd`, con modello `setup.example.env` e guida `CONFIGURAZIONE.txt`. Lettura letterale UTF-8 con whitelist/duplicati rifiutati, ACL utente/SYSTEM, modalita senza prompt, parametri auto/manual/skip e rilevamento conflitti di ripresa. Il file compilato non entra in manifest, ZIP, Git o log; contiene in chiaro i segreti scelti dall'utente e resta esclusivamente sul PC. UAC e login ChatGPT nel browser restano possibili. Nessuna API key OpenAI o modifica alle guardie economiche.

**Eseguiti:** 20/20 regressioni Node Cloudflare nel cloud Linux, integrita manifest/ZIP e verifica esclusione file locali. **Non eseguiti:** `Test-SetupSettings.ps1`, PowerShell/Windows, ACL/no-prompt sul secondo PC, API Cloudflare reali e plugin ChatGPT. Nessuna nuova operazione su Desktop1. [Procedura e limiti](../docs/mcp-agent/INSTALLER-1.2.1.md).

# Installer 1.2.0 — Cloudflare automatico dal setup

Implementati rilevazione dominio/account/team, app Access per singola email con Managed OAuth, tunnel dedicato e DNS dopo verifica della protezione. Token API solo in memoria/stdin; registro vincolato al PC e riconciliazione senza ripetere esiti incerti. Node 24 LTS privato con checksum se manca un runtime compatibile. Agente operativo 0.6.1 e guardie economiche invariati. Nessuna nuova prova o installazione su Desktop1.

Test effettivi: **20/20 test Node simulati nel cloud**, sintassi JS e integrita pacchetto. **Non eseguiti**: PowerShell/Windows 1.2.0, API Cloudflare reali e nuovo plugin ChatGPT/Voice. Sorgenti pubblicati non significano installazione o collaudo remoto completati. Ripresa: nuovo ZIP in nuova cartella, INSTALLA.cmd con la stessa destinazione; token API inserito solo sul PC. Non cancellare il registro. [Procedura, backup/rollback e limiti](../docs/mcp-agent/INSTALLER-1.2.0.md).

# Installer 1.1.1 — correzione estrazione e ripresa Git

Segnalazione dal secondo PC: estrazione dichiarata conclusa ma verifica Git fallita. Difetto individuato: argomenti del vecchio SFX nella 1.1.0, incompatibili col parser attuale Git for Windows. Corretto in -y -o"percorso"; worker completed soltanto dopo versione nativa verificata. Ripresa limitata alla richiesta/ricevuta 1.1.0 confermata, vincolata a PC/utente/percorso/hash, senza eliminare registro o prove precedenti; nessun retry di operazioni attive/incerte. [Verbale e procedura](../docs/mcp-agent/INSTALLER-1.1.1.md).

Regressione eseguita nel cloud compilando il ciclo parser ufficiale C++: vecchi argomenti riproducono destinazione assente, nuovi argomenti conservano percorso/spazi/Unicode. Nuovi test Windows predisposti ma NON eseguiti in questa correzione. Nessuna installazione, test o mutazione aggiuntiva su Desktop1; niente nuova inferenza o modifiche alle guardie economiche. Il precedente collaudo 1.1.0 con fixture non aveva riprodotto il comportamento del vero estrattore: non prova la correzione sul secondo PC.

# Installer 1.1.0 — un solo avvio, prerequisiti e ripresa automatici

Richiesta utente: evitare comandi PowerShell manuali per preparare Codex e recuperare le installazioni. INSTALLA.cmd ora rileva/installa Git, Go e CLI Codex compatibile da fonti ufficiali con hash, gestisce login browser e verifica account/quota senza inferenza. Registro persistente per PC/utente/cartella, cache verificata, ricevute dei worker e nessun replay di esiti incerti. Ripresa della 1.0.1 fallita e dei tentativi 1.1.0 mai operativi; backup/rollback anche dei task, aggiornamento protetto degli script delle copie complete. Nome Desktop2 configurabile; binario agente 0.6.1 e servizio operativo Desktop1 invariati.

[Specifica, procedura e limiti](../docs/mcp-agent/INSTALLER-1.1.0.md). Cloudflare remoto (DNS/tunnel/Access/Managed OAuth), plugin e accessi account restano passaggi distinti: nessuna falsa dichiarazione di collegamento completo, nessun costo extra o modifica alle guardie. Collaudo secondo PC fisico e installazione reale dei runtime da assenti restano da eseguire; i test Windows isolati distinguono fixture da letture reali.

# Installer 1.0.3 — 31 ottobre 2026

Richiede CLI Codex compatibile prima di creare l'installazione. Priorita alla CLI nativa, percorso esplicito supportato; COLLEGA-CODEX per ripresa 1.0.1 o abilitazione su copia completa e controllo account/quota senza inferenza. Nessun arresto automatico, nessuna riabilitazione implicita del task disabilitato, nessuna modifica alle guardie dei costi. Binario agente 0.6.1 invariato. Verbale docs/mcp-agent/INSTALLER-DESKTOP2-1.0.3.md.

# Windows MCP changelog

## 0.6.1 — installata e collaudata, 31 ottobre 2026

Attese Codex senza mutex globale; nessun watchdog temporale dei turni e nessun avviso temporizzato. Coda operativa compatta con archivio immutabile locale, dipendenze storiche e activity_history; job storici caricati su richiesta. Coordinamento read/write di risorse dichiarate e identita Git comune. Registro significativo di progetto su Git distinto dallo stato privato. Vedere docs/mcp-agent/IMPROVEMENTS-0.6.1.md; test Windows/vet/build, prova oltre cinque minuti, MCP isolato, backup/rollback, installazione e turno Codex reale superati. Verbale VALIDATION-0.6.1-20261031.md in docs/mcp-agent.

## 0.6.0 — installata e collaudata, 31 ottobre 2026

- Coda persistente per progetto, percorsi sovrapposti protetti, massimo quattro progetti indipendenti attivi.
- queue_add/list/cancel/move/resume, etichette, ID e dipendenze esplicite; riordino senza preemption.
- command_start/codex_turn e Go legacy accodano; usare queue_list per ricavare job/turn dopo dispatch.
- Letture senza lock globale, esclusione nativa Windows del file aperto in scrittura; mutazioni dirette di progetti attivi rifiutate.
- Pausa su errore/riavvio, nessun replay automatico degli esiti incerti; verifica quota all'avvio effettivo e fotografia finale 0.5.9 preservata.
- Test Windows/vet/build, collaudo MCP isolato con riavvio, backup/rollback, installazione e prove reali dalla nuova chat superati.
- Corretto lo script del collaudo crash per gestire i figli gia terminati e isolare ogni esecuzione; nessuna modifica alle guardie economiche.
- Turno Codex accodato completato, final_usage presente; automazione quota esistente verificata senza duplicati. Voice/altri PC/reboot e refresh dei cinque strumenti diretti client restano non verificati. Verbale docs/mcp-agent/VALIDATION-0.6.0-20261031.md.


## 0.5.9 — installata e collaudata, 31 ottobre 2026

- codex_status resta il comando MCP diretto per richieste su quota residua e crediti; aggiunto riepilogo usage con percentuali residue, reset UTC e saldo noto/sconosciuto.
- Una lettura automatica dopo turn/completed (completato, interrotto o fallito), salvata come jarvis/usage e disponibile anche in codex_events.final_usage; usage_pending indica la lettura in corso.
- Nessun confronto storico. Nessuna nuova lettura iniziale: restano le guardie gia presenti. Dati leggibili anche quando un nuovo turno sarebbe bloccato; saldo nullo mai trasformato in zero.
- A fine elaborazione il client deve riferire brevemente i valori aggiornati o l'indisponibilita, senza riusare valori precedenti.
- Test tramite processo RPC simulato per notifiche duplicate, assenza di deadlock, esiti finali, saldo nullo e servizio non disponibile. Test Windows/vet/build superati; backup/rollback e installazione verificati. Turno reale dal plugin con evento finale jarvis/usage e final_usage verificati.

## 0.5.8 — 31 ottobre 2026

- Plugin aggiornato e strumenti diretti verificati.
- Riscontro costi indipendente fornito dall'utente, associato ad account/PC e con scadenza; saldo API nullo mai accettato da solo. Crediti positivi/flag ignoti/quote esaurite restano bloccati.
- Codex reale collaudato: risposte, storico, modifica file, steer, interrupt e resume dopo riavvio.
- Installazione elevata, backup 0.5.7 e stato, rollback e riconnessione verificati. Nessuna API key, acquisto o modifica alle impostazioni di pagamento.

## 0.5.7 — 31 ottobre 2026

- Identita stabile, catalogo progetti, ricevute con request_id e job persistenti, lock OS e ACL dello stato.
- Lettura/scrittura/ricerca file, creazione annidata e rinomina/spostamento di file e cartelle, hash atteso, backup e ripristino protetti.
- Profili fissi di sviluppo Go/Git/.NET/CMake/MSBuild, output limitato, timeout e annullamento tramite Job Object Windows. Test/build Go e build .NET verificati.
- Cattura JPEG MCP diretta con monitor, regione, origine e fattori di scala; protezioni desktop 0.5.6 mantenute.
- Routing autenticato a peer configurati, verifica identita, stato online/offline e inoltro limitato.
- Ponte Codex App Server locale, protocollo fissato, account/quote/sessioni/eventi/approvazioni. Letture reali verificate; turni bloccati da saldo nullo e revisione costi, non collaudati con inferenza.
- Catalogo server aggiornato e percorso di compatibilita verificato dal plugin. Refresh del catalogo client e prova nuova chat/Voice ancora necessari.
- Sorgenti e collaudi su branch separato; backup/rollback, installazione, amministratore e riconnessione verificati. Nessun costo API/acquisto aggiuntivo avviato.

## 0.5.6

Controllo desktop semantico senza coordinate, snapshot monouso e revalidazione; vedere ../docs/mcp-agent/DESKTOP-0.5.6.md.
