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

# Installer 1.0.3 — collegamento obbligatorio alla CLI Codex

Sostituisce la scelta opzionale della 1.0.2: l'obiettivo e usare Codex. Rilevamento CLI nativa prima di Windows App, versione esatta 0.165.0-alpha.1.2; nessuna modifica dei permessi WindowsApps. COLLEGA-CODEX.cmd recupera la prima copia 1.0.1 fallita oppure configura una copia completa, verifica account ChatGPT e quota senza inferenza. Se la configurazione di un agente attivo e diversa, rifiuta senza fermarlo. Dati osservati separati da inference_allowed; guardie economiche immutate e nessuna prova costi copiata.

[Procedura, test e limiti](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.3.md). Agente resta 0.6.1; il collegamento sul secondo PC fisico richiede esecuzione locale e riscontro dell'utente. Nome Desktop2 personalizzabile, plugin suggerito Jarvis-Desktop2. Lo storico seguente conserva le decisioni precedenti; la modalita Codex opzionale della 1.0.2 e superata.

# Installer 1.0.2 — correzione Accesso negato e ripresa protetta

Segnalazione utente dalla 1.0.1: codex.exe --version non accessibile interrompeva la prima installazione. Rilevamento opzionale ora intercettato: Jarvis prosegue con ponte Codex disabilitato. RIPRENDI.cmd recupera soltanto la copia 1.0.1 mai avviata, con backup integrale e ripristino su errore. Test Windows con vero Win32 5, fallimento originale e ripresa/rollback superati; servizio Desktop1 invariato. [Verbale, istruzioni e checksum](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.2.md). Il secondo PC richiede esecuzione e verifica dell'utente. Default Desktop2 personalizzabile; agente sempre 0.6.1.

# Installer 1.0.1 — Desktop2 predefinito, nome configurabile

La procedura chiede il nome del dispositivo; Invio mantiene Desktop2. Nome scelto usato per cartella proposta, task/collegamento, messaggi e nome suggerito del plugin. Verificati su Windows default e nome con spazi (Officina CNC), installazioni isolate e controlli precedenti. Pacchetto Jarvis-MCP-Setup-1.0.1.zip; agente resta 0.6.1. Nessuna rinomina Windows o cambio machine_id. [Verbale e checksum](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.1.md). Sostituisce il precedente pacchetto installer 1.0.0; i limiti di collaudo su secondo PC/account restano invariati.

# Installer Desktop2 1.0.0 — disponibile per la 0.6.1

Pacchetto di prima installazione per un secondo PC indipendente: copia binario verificato, credenziali/identita nuove, root progetti, avvio al login, configurazione Cloudflare locale e comandi di verifica/arresto. Suite Windows isolata superata; Cloudflare remoto, plugin ChatGPT, secondo PC fisico e reboot ancora da collaudare. Nessuna installazione o modifica del servizio Desktop1. [Procedura e verbale](../docs/mcp-agent/INSTALLER-DESKTOP2-1.0.0.md). Agente operativo resta 0.6.1; versione installer separata 1.0.0.

# Windows MCP 0.6.1 — installata e collaudata, 31 ottobre 2026

RPC Codex reattive e scheduler indipendente; nessun limite temporale dei turni e nessun avviso programmato. Coda solo operativa, terminali nell'archivio privato locale via activity_history; risorse condivise read/write e repository Git comuni riconosciuti. Registro significativo su Git distinto dai log tecnici. [Specifica](../docs/mcp-agent/IMPROVEMENTS-0.6.1.md), [verbale](../docs/mcp-agent/VALIDATION-0.6.1-20261031.md), [registro](../docs/mcp-agent/ACTIVITY.md), [regola di progetto](../docs/mcp-agent/PROJECT-RECORD.md).

Test Windows/vet/build, prova simulata di 310 secondi, MCP isolato, backup/rollback, installazione e turno Codex reale da questa conversazione superati. Codice binario 164cb980bd1a936749d41b8692979e98b929cb04, SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1. Backup D:\Jarvis\Backups\mcp-0.6.1-20261031. Prova costi invariata; letture storiche non sono valori correnti. Altri PC, reboot fisico e refresh catalogo diretto client restano da verificare.

## Storico precedente

# Jarvis Windows MCP Agent 0.6.0

Installata su Desktop1 e collaudata da una nuova chat ChatGPT il 31 ottobre 2026. Coda persistente, elenco/cancellazione/riordino e letture concorrenti; progetti indipendenti in parallelo, percorsi sovrapposti protetti. Test Windows/vet/build, backup/rollback, installazione e turno reale Codex accodato con quota finale verificati.

[Stato](../docs/mcp-agent/STATUS.md), [verbale 0.6.0](../docs/mcp-agent/VALIDATION-0.6.0-20261031.md), [contratto coda](../docs/mcp-agent/QUEUE-0.6.0.md), [istruzioni Windows](AGENTS.md), [continuita](CONTINUITY.md). La versione 0.5.9 ha introdotto il riepilogo diretto codex_status e la lettura finale automatica, qui preservati.

## Runtime

machine_info e project_catalog identificano computer e progetti. File/cartelle: file_list/read/search/write, directory_create, path_move e file_restore. Comandi: command_start restituisce queue_id; queue_list espone poi job_id per job_status/list/cancel. codex_turn segue lo stesso percorso con turn_id. queue_cancel rimuove solo attivita in attesa, queue_move ne cambia l'ordine, queue_resume richiede revisione dopo pausa. Ogni mutazione richiede request_id e, per i file, hash atteso. Nessuna shell arbitraria, sovrascrittura implicita o bypass dei percorsi protetti. Job, ricevute e identita persistono oltre una chat o un riavvio. screen_capture restituisce immagini MCP dirette, con dimensioni, monitor e origine.

Ponte Codex locale verificato per avvio, storico, risposta reale, modifica file, steer, interrupt e ripresa dopo riavvio. Account ChatGPT esistente, quota inclusa, niente chiavi API o acquisti. Riscontro locale dei costi associato ad account/PC, con validita massima sette giorni; dati API e quota ricontrollati a ogni turno. Routing fra due istanze collaudato; altri PC fisici devono essere configurati e autenticati.

Il catalogo server aggiunge cinque queue_* ai precedenti strumenti; in questa nuova chat i comandi coda sono stati verificati tramite compatibilita. Il refresh dei cinque strumenti diretti nel client non e dimostrato. Resta disponibile la compatibilita task_start con operation=runtime e request={operation:...,machine_id:...}. [Istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.5.7.md). Non promettere disponibilita in qualunque chat o in Voice senza il relativo collaudo.

## Controllo desktop senza coordinate

```json
{"operation":"desktop_inspect"}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<ID esatto>","action":"invoke"}}
{"operation":"desktop_action","desktop":{"snapshot_id":"<nuovo snapshot>","element_id":"<campo esatto>","action":"set_text","text":"Testo richiesto"}}
```

Richiamare tramite task_start e monitorare agent_status. Azioni: invoke, set_text, select, toggle secondo i pattern disponibili.
set_text sostituisce l'intero campo, non simula digitazione: richiede un'intenzione esplicita di sostituzione. Il contenuto originale non viene restituito.
Ogni snapshot dura 90 secondi ed è monouso. Ripetere l'ispezione dopo ogni azione o cambiamento.
I controlli sono individuati tramite UI Automation e, per Edit/Button nativi noti, HWND/classe/PID verificati. Si usano API dirette al controllo, senza muovere il cursore, SendInput/SendKeys o coordinate.
Se finestra, controllo, posizione, contenuto Edit o primo piano cambiano, l'azione viene rifiutata.
Non scegliere fra pulsanti omonimi senza contesto sufficiente; il solo nome non identifica il bersaglio.
Nessun retry automatico dopo esiti incerti; il dispatch non prova da solo l'effetto applicativo.

Il desktop deve essere sbloccato. Password, controlli non supportati, tasti/scorciatoie globali, trascinamenti e mouse libero sono esclusi; le pagine web non sono garantite.
UIA dipende dall'applicazione: non è una garanzia di infallibilità.
Configurazione locale JARVIS_DESKTOP_ENABLED=1; per disabilitare impostare 0 e riavviare in modo controllato.

## Compatibilita con i task Go 0.5.5/0.5.6

```json
{"operation":"go_test","project":"windows-mcp-agent"}
{"operation":"go_build","project":"windows-mcp-agent"}
```

go_test usa go test ./...; go_build compila in Staging/jarvis-mcp.exe. Timeout 120 s e annullamento dell'albero dei processi Windows, ereditati dalla 0.5.5.
Per test senza cache: go test -count=1 ./....
Il codice dei moduli gira con i permessi dell'agente: la root autorizzata non è una sandbox del codice.
L'output dei task Go conserva gli ultimi 12000 byte, dopo acquisizione in memoria.

## Installazione verificata

- Binario operativo D:\Jarvis\jarvis-mcp.exe, fuori da Staging.
- Sorgenti D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source.
- Configurazione D:\Jarvis\Config; credenziali fuori da Git.
- Endpoint locale 127.0.0.1:8765/mcp; Cloudflare Tunnel e Access esistenti.
- Backup precedente 0.5.9 in D:\Jarvis\Backups\mcp-0.6.0-20261031, con configurazione e snapshot dello stato; aggiornamento e rollback isolato collaudati.
- Go 1.27.0 Windows/amd64. Nessuna compilazione tramite GitHub Actions.

Gli script bootstrap in Agent\ sono esempi: il percorso operativo verificato è quello sopra.
La cattura MCP diretta e stata acquisita e mostrata in chat; l'avvio automatico dopo reboot resta da verificare/implementare.
Seguire [RELEASE_POLICY](../docs/mcp-agent/RELEASE_POLICY.md).
