# Agente 0.7.4 — token di sviluppo separati dai dialoghi

5 novembre 2026. Implementazione e test completati: suite/vet/race Linux,
test/vet/build Windows e MCP isolato su entrambi i PC. I dialoghi restano intatti.
Desktop2: riusato il profilo gia riuscito della 0.7.3, Go ufficiale 1.27.0,
SDK/cache isolati, GOMAXPROCS=1, GOGC=25, -p 1. Non ripartire con Go 1.27.3.
Profilo ripetibile: scripts/build-runtime.ps1; dettagli in AGENTS.md e nel verbale.

Sorgente runtime locale c662fae247d5edf5e9c99248d00ddc358823e391, branch
feature/development-token-meter-20261105. Pubblicazione respinta dalla revisione
automatica: gli appunti riportano autorizzazione permanente, ma il revisore
richiede un'approvazione utente esplicita per la condivisione verso Andry-iu/Jarvis.
Nessun aggiramento; nessun rilascio installato. Entrambi operativi ancora 0.7.3.
Checksum candidati in CHECKSUMS-0.7.4.json; esiti e limiti nel verbale
VALIDATION-0.7.4-20261105.md. Installare solo dopo pubblicazione, tramite updater
canonico, backup e rehearsal; mai modificare la procedura fra PC.

Nuovo comando codex_token_usage, anche via task_start/runtime. Categoria default
report development; categoria omessa al dispatch unclassified. Righe distinte
per sviluppo, dialogo, altro, non classificato. Misura solo Codex inoltrato da
Jarvis: non questa chat/Voice, trascrizioni, TTS o altri client. Nessun recupero
retroattivo o conversione quota/token. Guardie economiche invariate.
Specifica e comandi: docs/mcp-agent/TOKEN-METER-0.7.4.md.

Le sezioni successive sono storiche.

# Agente 0.7.3 - installato e collaudato sui due desktop

3 novembre 2026. Desktop1 e Desktop2 aggiornati alla 0.7.3. Letture live di
machine_info e agent_status: privileges.elevated=true dal token del processo,
PID rispettivamente 187600 e 9332 (istantanee, da rileggere prima di nuove azioni).
agent_restart_admin via task_start/runtime: already_elevated, restarted=false;
nessun riavvio superfluo. Contatori precedenti conservati, nuova chiamata conteggiata.
Sorgenti pubblicati: b00e7e6d4fffd93e127c460408e274176e87ee9d sul branch
feature/agent-admin-control-20261103 di Andry-iu/Jarvis. Checkout locali allineati.
Suite Go/vet/build e integrazione Windows standard -> helper -> nuovo admin PASS
su entrambi; backup/rehearsal, hash, identita/configurazione/manutenzione PASS.

Per chiedere i privilegi usare machine_info o agent_status. Per richiedere
l'elevazione usare agent_restart_admin con machine_id e request_id; leggere
agent_restart_status con lo stesso request_id fino all'esito verificato.
I due nuovi comandi sono disponibili anche come task_start operation=runtime,
request={operation:<nome>,machine_id:<id>,request_id:<id-univoco>}.
agent_status resta uno strumento diretto, non un'operazione runtime.
Un ack awaiting_elevation non prova il completamento. UAC puo richiedere consenso
locale sul PC; stesso utente richiesto. Con lavori attivi/accodati/incerti il
riavvio viene rifiutato. Nessun arresto forzato dei lavori o nuovo servizio/task.
Applicazioni ordinarie sempre standard per default. UI UAC non automatizzata.
Specifica: docs/mcp-agent/ADMIN-CONTROL-0.7.3.md.
Prove e limiti: docs/mcp-agent/VALIDATION-0.7.3-20261103.md.
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

# Verifica finale 0.7.1 — installata su entrambi i computer

1 novembre 2026. Desktop1 DESKTOP-1ESSB0K e Desktop2 AUTECHVPC2 sono stati aggiornati e riavviati tramite i rispettivi batch e lo stesso updater canonico. machine_info da ChatGPT conferma 0.7.1 e le identita precedenti. Entrambi i self-update.log riportano BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS e PASS common_batch_update_identity_config_maintenance. Una prova avviata dai due agenti installati conferma console_visible=false ed elevated=true per il processo PowerShell discendente.

Sorgente runtime a8b0bca1901d96de9604388bbec5ec9b8aecc847; installer 1.3.1 nel commit b858e546c524982a3bc255fe92231aae6c7f9287. Desktop1 SHA-256 aa3625aed0d3d008a615ed0e5666d820b69bd6abdb86cb44fe4b55f596ebde00; Desktop2 SHA-256 83f6ff40c96d53c9930230737108f49c95f02c2f655576dd674aa7e5eb9eed27. La differenza deriva dalle build locali gia registrate (Go 1.27.0/1.27.3; Desktop2 standard dopo errore interno nel tentativo -trimpath). Updater identico dopo normalizzazione CRLF/LF; nessuna migrazione di percorsi o privilegi applicativi.

Backup finali: Desktop1 D:\\Jarvis\\Backups\\self-update-948d80db4d8c436da61506cb99b95432; Desktop2 D:\\Jarvis\\Backups\\self-update-e0d051ba9786415c9f69c0baa2c786b5. Configurazioni durevoli e manutenzione verificate dall'updater. Le sezioni seguenti documentano gli stati precedenti e non descrivono piu lo stato operativo corrente. Resta esclusa una prova di reboot fisico, un ciclo reale di 24 ore e programmi che richiedano esplicitamente una nuova console; gli errori restano consultabili tramite log e ricevute, senza apertura automatica di una console.

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

Test Node nel cloud: **45/45 passati**. Collegamento MCP Desktop2 verificato direttamente, macchina AUTECHVPC2, agente 0.6.1, coda/job vuoti. Esecuzione Windows isolata e lettura nomi Cloudflare registrate nel [verbale](INSTALLER-1.2.2.md); distinguere test, codice pubblicato e installazione. Nessuna operazione su Desktop1, nessuna inferenza e guardie economiche invariate.

# Installer 1.2.1 — parametri e segreti locali, senza domande console

Aggiunto `setup.local.env`, rilevato automaticamente accanto a `INSTALLA.cmd`, con modello `setup.example.env` e guida `CONFIGURAZIONE.txt`. Lettura letterale UTF-8 con whitelist/duplicati rifiutati, ACL utente/SYSTEM, modalita senza prompt, parametri auto/manual/skip e rilevamento conflitti di ripresa. Il file compilato non entra in manifest, ZIP, Git o log; contiene in chiaro i segreti scelti dall'utente e resta esclusivamente sul PC. UAC e login ChatGPT nel browser restano possibili. Nessuna API key OpenAI o modifica alle guardie economiche.

**Eseguiti:** 20/20 regressioni Node Cloudflare nel cloud Linux, integrita manifest/ZIP e verifica esclusione file locali. **Non eseguiti:** `Test-SetupSettings.ps1`, PowerShell/Windows, ACL/no-prompt sul secondo PC, API Cloudflare reali e plugin ChatGPT. Nessuna nuova operazione su Desktop1. [Procedura e limiti](INSTALLER-1.2.1.md).

# Installer 1.2.0 — Cloudflare automatico dal setup

Implementati rilevazione dominio/account/team, app Access per singola email con Managed OAuth, tunnel dedicato e DNS dopo verifica della protezione. Token API solo in memoria/stdin; registro vincolato al PC e riconciliazione senza ripetere esiti incerti. Node 24 LTS privato con checksum se manca un runtime compatibile. Agente operativo 0.6.1 e guardie economiche invariati. Nessuna nuova prova o installazione su Desktop1.

Test effettivi: **20/20 test Node simulati nel cloud**, sintassi JS e integrita pacchetto. **Non eseguiti**: PowerShell/Windows 1.2.0, API Cloudflare reali e nuovo plugin ChatGPT/Voice. Sorgenti pubblicati non significano installazione o collaudo remoto completati. Ripresa: nuovo ZIP in nuova cartella, INSTALLA.cmd con la stessa destinazione; token API inserito solo sul PC. Non cancellare il registro. [Procedura, backup/rollback e limiti](INSTALLER-1.2.0.md).

# Installer 1.1.1 — correzione estrazione e ripresa Git

Segnalazione dal secondo PC: estrazione dichiarata conclusa ma verifica Git fallita. Difetto individuato: argomenti del vecchio SFX nella 1.1.0, incompatibili col parser attuale Git for Windows. Corretto in -y -o"percorso"; worker completed soltanto dopo versione nativa verificata. Ripresa limitata alla richiesta/ricevuta 1.1.0 confermata, vincolata a PC/utente/percorso/hash, senza eliminare registro o prove precedenti; nessun retry di operazioni attive/incerte. [Verbale e procedura](INSTALLER-1.1.1.md).

Regressione eseguita nel cloud compilando il ciclo parser ufficiale C++: vecchi argomenti riproducono destinazione assente, nuovi argomenti conservano percorso/spazi/Unicode. Nuovi test Windows predisposti ma NON eseguiti in questa correzione. Nessuna installazione, test o mutazione aggiuntiva su Desktop1; niente nuova inferenza o modifiche alle guardie economiche. Il precedente collaudo 1.1.0 con fixture non aveva riprodotto il comportamento del vero estrattore: non prova la correzione sul secondo PC.

# Installer 1.1.0 — un solo avvio, prerequisiti e ripresa automatici

Richiesta utente: evitare comandi PowerShell manuali per preparare Codex e recuperare le installazioni. INSTALLA.cmd ora rileva/installa Git, Go e CLI Codex compatibile da fonti ufficiali con hash, gestisce login browser e verifica account/quota senza inferenza. Registro persistente per PC/utente/cartella, cache verificata, ricevute dei worker e nessun replay di esiti incerti. Ripresa della 1.0.1 fallita e dei tentativi 1.1.0 mai operativi; backup/rollback anche dei task, aggiornamento protetto degli script delle copie complete. Nome Desktop2 configurabile; binario agente 0.6.1 e servizio operativo Desktop1 invariati.

[Specifica, procedura e limiti](INSTALLER-1.1.0.md). Cloudflare remoto (DNS/tunnel/Access/Managed OAuth), plugin e accessi account restano passaggi distinti: nessuna falsa dichiarazione di collegamento completo, nessun costo extra o modifica alle guardie. Collaudo secondo PC fisico e installazione reale dei runtime da assenti restano da eseguire; i test Windows isolati distinguono fixture da letture reali.

# Installer 1.0.3 — collegamento obbligatorio alla CLI Codex

Sostituisce la scelta opzionale della 1.0.2: l'obiettivo e usare Codex. Rilevamento CLI nativa prima di Windows App, versione esatta 0.165.0-alpha.1.2; nessuna modifica dei permessi WindowsApps. COLLEGA-CODEX.cmd recupera la prima copia 1.0.1 fallita oppure configura una copia completa, verifica account ChatGPT e quota senza inferenza. Se la configurazione di un agente attivo e diversa, rifiuta senza fermarlo. Dati osservati separati da inference_allowed; guardie economiche immutate e nessuna prova costi copiata.

[Procedura, test e limiti](INSTALLER-DESKTOP2-1.0.3.md). Agente resta 0.6.1; il collegamento sul secondo PC fisico richiede esecuzione locale e riscontro dell'utente. Nome Desktop2 personalizzabile, plugin suggerito Jarvis-Desktop2. Lo storico seguente conserva le decisioni precedenti; la modalita Codex opzionale della 1.0.2 e superata.

# Installer 1.0.2 — correzione Accesso negato e ripresa protetta

Segnalazione utente dalla 1.0.1: codex.exe --version non accessibile interrompeva la prima installazione. Rilevamento opzionale ora intercettato: Jarvis prosegue con ponte Codex disabilitato. RIPRENDI.cmd recupera soltanto la copia 1.0.1 mai avviata, con backup integrale e ripristino su errore. Test Windows con vero Win32 5, fallimento originale e ripresa/rollback superati; servizio Desktop1 invariato. [Verbale, istruzioni e checksum](INSTALLER-DESKTOP2-1.0.2.md). Il secondo PC richiede esecuzione e verifica dell'utente. Default Desktop2 personalizzabile; agente sempre 0.6.1.

# Installer 1.0.1 — Desktop2 predefinito, nome configurabile

La procedura chiede il nome del dispositivo; Invio mantiene Desktop2. Nome scelto usato per cartella proposta, task/collegamento, messaggi e nome suggerito del plugin. Verificati su Windows default e nome con spazi (Officina CNC), installazioni isolate e controlli precedenti. Pacchetto Jarvis-MCP-Setup-1.0.1.zip; agente resta 0.6.1. Nessuna rinomina Windows o cambio machine_id. [Verbale e checksum](INSTALLER-DESKTOP2-1.0.1.md). Sostituisce il precedente pacchetto installer 1.0.0; i limiti di collaudo su secondo PC/account restano invariati.

# Installer Desktop2 1.0.0 — disponibile per la 0.6.1

Pacchetto di prima installazione per un secondo PC indipendente: copia binario verificato, credenziali/identita nuove, root progetti, avvio al login, configurazione Cloudflare locale e comandi di verifica/arresto. Suite Windows isolata superata; Cloudflare remoto, plugin ChatGPT, secondo PC fisico e reboot ancora da collaudare. Nessuna installazione o modifica del servizio Desktop1. [Procedura e verbale](INSTALLER-DESKTOP2-1.0.0.md). Agente operativo resta 0.6.1; versione installer separata 1.0.0.

# Windows MCP 0.6.1 — installata e collaudata, 31 ottobre 2026

RPC Codex reattive e scheduler indipendente; nessun limite temporale dei turni e nessun avviso programmato. Coda solo operativa, terminali nell'archivio privato locale via activity_history; risorse condivise read/write e repository Git comuni riconosciuti. Registro significativo su Git distinto dai log tecnici. [Specifica](IMPROVEMENTS-0.6.1.md), [verbale](VALIDATION-0.6.1-20261031.md), [registro](ACTIVITY.md), [regola di progetto](PROJECT-RECORD.md).

Test Windows/vet/build, prova simulata di 310 secondi, MCP isolato, backup/rollback, installazione e turno Codex reale da questa conversazione superati. Codice binario 164cb980bd1a936749d41b8692979e98b929cb04, SHA-256 9FF4A136951F464925CC55B96E1A968174204199FA9C7FF681B998B3542742C1. Backup D:\Jarvis\Backups\mcp-0.6.1-20261031. Prova costi invariata; letture storiche non sono valori correnti. Altri PC, reboot fisico e refresh catalogo diretto client restano da verificare.

## Storico precedente

# 0.6.0 — installata e collaudata da questa nuova chat

31 ottobre 2026: candidata esistente recuperata senza sovrascritture; completati test Windows/vet/build, collaudo MCP isolato, backup/rollback, installazione e prove reali da ChatGPT. Coda persistente, elenco/cancellazione/riordino, letture concorrenti e progetti indipendenti verificati. Turno Codex accodato completato con lettura finale quota/crediti. [Verbale 0.6.0](VALIDATION-0.6.0-20261031.md), [contratto](QUEUE-0.6.0.md), [istruzioni Windows](../../windows-mcp-agent/AGENTS.md).

Binario sorgente a63fb7b36dce3e543c28194ae80f8519524949fc; checkout installazione c01400edcd185b7d6b6bde114b8064931f742195 (solo script successivo). SHA-256 68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE. Backup D:\Jarvis\Backups\mcp-0.6.0-20261031. Branch reconcile-windows-mcp-agent-20261031, nessun merge su main.

Quota finale del collaudo alle 17:56:53 UTC: 87% residuo, saldo servizio zero noto, reset 4 novembre 2026 13:39:22 UTC. Sono dati storici: ogni richiesta richiede nuova lettura. Riscontro privato per saldo null ancora in scadenza il 7 novembre 15:55 UTC; non prorogato. Automazione giornaliera esistente verificata abilitata, nessun duplicato; esecuzione futura non provata.

Cinque queue_* disponibili sul server e verificati tramite compatibilita task_start; refresh degli strumenti diretti client non verificato. Voice, altri PC e avvio dopo reboot restano aperti. Solo dispositivo locale configurato. Le sezioni seguenti sono storico.

# 0.5.9 — quota/crediti installati e collaudati

Consultazione diretta codex_status migliorata e lettura automatica finale senza confronti, verificata dopo turno reale dal plugin. Test Windows/vet/build, backup/stato, rollback e riconnessione superati. [Verbale](USAGE-0.5.9.md). Sorgente binario 924f23aa6409f77342d964cc225991ecb4296002; SHA-256 CB7B9B3E0C3A9CA9133E738E0412CD518AF6BEA7E79FEE2C65830776635540C5. Coda/riordino sono la modifica successiva approvata, ancora in sviluppo: [specifica](QUEUE-SPEC.md).

# 0.5.8 — installata e verificata, Codex operativo

31 ottobre 2026: plugin aggiornato dall'utente, 38 strumenti diretti disponibili. Schermata fornita dall'utente: quota residua 88%, zero crediti, ricarica automatica disattivata. La nuova guardia usa un riscontro privato associato ad account/PC soltanto per il saldo API nullo, con controlli API invariati e scadenza 7 novembre 2026 ore 15:55 UTC. Nessuna chiave API, acquisto o ricarica.

Codex verificato realmente: risposta, storico, steer, interrupt confermato, ripresa dopo riavvio e modifica di un file di prova con digest controllato. Dopo installazione, turno completato dal plugin con risposta JARVIS_INSTALL_OK. Agente 0.5.8 elevato, identita e job 0.5.7 conservati; backup 0.5.7 e stato verificati, rollback isolato superato. Sorgente binario 6b5530e8347502f35511aabdf696c58877836cee; SHA-256 04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE.

[Verbale 0.5.8](CODEX-0.5.8.md). Altri PC fisici, nuova chat/Voice e avvio dopo reboot restano da verificare; clone NestingOpt sospeso. Le sezioni seguenti sono lo storico dei rilasci precedenti.

# 0.5.7 — installata e verificata, Codex in attesa del controllo costi

31 ottobre 2026: test Go, go vet, compilazione Windows, backup e rollback isolato superati; agente operativo 0.5.7 con token elevato verificato. File/cartelle, immagini MCP dirette, test/build Go e build .NET 9 verificati anche tramite plugin da questa chat. Stato, job e ricevute persistenti; routing autenticato verificato fra due istanze sullo stesso PC. Nessun altro PC fisico configurato o verificato in questo rilascio.

Codex locale: account/quote e lista sessioni verificati; avvio/ripresa/turni reali bloccati per balance=null e revisione automatica. Zero inferenze Jarvis avviate. UI Impostazioni/Utilizzo osservata: 88% quota residua e 0 crediti; ricarica automatica non verificata. Restano necessari verifica/autorizzazione appropriata per questo punto, aggiornamento catalogo client e collaudo in nuova chat/Voice. [Verbale completo](VALIDATION-0.5.7-20261031.md), [contratto](RUNTIME-0.5.7.md), [continuita Windows](../../windows-mcp-agent/CONTINUITY.md).

# Jarvis Windows MCP Agent — stato corrente 0.5.6

**31 ottobre 2026: 0.5.6 installata sul PC Jarvis-Desktop1, elevata e collaudata.**
Questa sezione sostituisce tutte le sezioni storiche seguenti.

- Sorgenti compilati: eb5370396736283c1aed111e30001b95bd72c1b4.
- SHA-256 operativo: C230A1FF4EE852744318931FB1BF620022985B01606DFED5D1B1061182F410A5.
- Binario D:\Jarvis\jarvis-mcp.exe; sorgenti D:\Jarvis\MCP-Source\windows-mcp-agent.
- Branch reconcile-windows-mcp-agent-20261031; nessun merge su main.
- Nuove operazioni del task_start esistente: desktop_inspect e desktop_action.
- Azioni su controlli identificati: invoke, set_text, select, toggle, secondo il supporto reale.
- UIA oppure messaggi mirati a controlli Edit/Button nativi verificati; nessun input globale o clic su coordinate.
- Snapshot monouso 90 s, verifiche finestra/PID/avvio/runtime ID/geometria/primo piano/stato; rifiuto su incertezza.
- Desktop abilitato nella configurazione locale; password escluse, contenuti Edit non restituiti, controllo hash contro modifiche successive all'ispezione.
- set_text sostituisce l'intero campo: non usarlo per sovrascrivere bozze senza autorizzazione.
- WinForms e WPF: clic/testo/checkbox e casi avversi superati, pulsante esca mai invocato, due monitor X=0/-1920.
- Collaudo WinForms superato anche sul processo installato, porta 8765.
- go_test diretto dal plugin dopo installazione: completed, 100%, 3.313s.
- desktop_inspect diretto dal plugin sul desktop reale: completed, 11 controlli, senza troncamento. Una precedente osservazione non disponibile è stata rifiutata senza azioni; causa non identificata.
- TokenElevation=true verificato sul processo 476784; porte di prova libere; sorgenti tracciati puliti; backup originali preservati.
- Backup 0.5.5/configurazione in D:\Jarvis\Backups\mcp-0.5.6-20261031; ripristino isolato 0.5.5→0.5.6→0.5.5 verificato.
- Riferimento completo: [Desktop 0.5.6, collaudi, limiti e ripristino](DESKTOP-0.5.6.md).

## Limiti da mantenere espliciti

Nessuna promessa di controllo universale o assenza assoluta di errori dei provider. Se un controllo non è identificabile/supportato, fermarsi; nessun fallback a pixel o retry di azioni incerte.
Mouse libero, trascinamenti e tasti/scorciatoie globali non sono implementati. Le pagine web non sono garantite.
Il desktop deve essere sbloccato e il bersaglio in primo piano. Non aggirare UAC o desktop protetti.
Cattura diagnostica acquisita e mostrata all'utente; manca un comando MCP dedicato.
Avvio automatico dopo riavvio ancora da verificare. Android e NestingOpt restano fuori da questo intervento.

---

# Storico fino alla 0.5.5 — non usare come stato corrente

# Jarvis Windows MCP Agent — stato corrente 31 ottobre 2026

Questa sezione sostituisce le indicazioni operative storiche riportate sotto.

## Esito verificato

**0.5.5 installata sul notebook Jarvis-Desktop1 e collaudata direttamente da ChatGPT tramite MCP.**
Sottoprogetto Windows distinto dall'app Android; nessun intervento su Android o sul clone NestingOpt.

- Eseguibile operativo: `D:\Jarvis\jarvis-mcp.exe`.
- Checkout: `D:\Jarvis\MCP-Source\windows-mcp-agent`; root Git `D:\Jarvis\MCP-Source`.
- Branch: `reconcile-windows-mcp-agent-20261031`.
- Commit sorgenti del binario: `b982ed77bdbf4921eabf16e04461bfa89b944675`.
- SHA-256 operativo e staging: `0769E1597B37628B3197A545F10FB42491D85913B8491B3E722C0D398B47E47E`.
- Go 1.27.0, Windows/amd64. Nessuna GitHub Actions.
- Root operativa autorizzata: `D:\Jarvis\MCP-Source`; precedente configurazione `D:\Jarvis\Projects` salvata nel backup.
- Endpoint: 127.0.0.1:8765/mcp, accesso remoto tramite il tunnel e Cloudflare Access esistenti.

## Implementato e verificato

task_start accetta operation=go_test oppure go_build e project relativo alla root.
go_test esegue go test ./...; go_build compila in Staging/jarvis-mcp.exe.
Annullamento Windows corretto: termina l'albero dei processi tramite taskkill.exe di sistema.
Timeout 120 s, terminazione con limite 5 s e WaitDelay 2 s; contesto riportato nel risultato.
I test Go eseguono codice dei moduli autorizzati con i permessi dell'agente: il controllo dei percorsi non costituisce una sandbox del codice.

- Test locali sul notebook, incluso nuovo test regressione cancellazione: ok jarvis-mcp-prototype 3.009s.
- Collaudo MCP isolato dopo la correzione: figlio terminato dopo task_stop.
- Timeout reale: 120.4126 s, cancelled, context deadline exceeded, nessun figlio rimasto attivo.
- Rifiutati: operazione sconosciuta, percorso assoluto, traversal fuori root e directory senza go.mod.
- Backup e ripristino provati su porta 8767: 0.5.4 → 0.5.5 → 0.5.4.
- Dopo installazione, agent_status remoto: versione 0.5.5.
- go_test remoto reale: task-1 completed, 100%, ok jarvis-mcp-prototype 2.925s, 13:21:45–13:21:49 Europe/Rome.
- go_build remoto reale: task-2 completed, 100%, go_build succeeded, 13:22:11 Europe/Rome.
- Hash staging/operativo uguali; nessun listener temporaneo 8766/8767; sorgenti tracciati puliti.
- vcs.modified=true deriva da file non tracciati; nessuna modifica ai sorgenti tracciati al controllo finale.

## Backup e ripresa

Backup operativo: `D:\Jarvis\Backups\mcp-0.5.5-20261031`.
Conservare jarvis-mcp-0.5.4.exe, replaced-0.5.4.exe, agent.env, avvio originale e checksum.
SHA-256 0.5.4: `7A29039C153409C35135D11C9CD891BC7366A3C9713AA77C3CFF947780B0288E`.
Conservati stash `backup update.go locale 20261031` e update.go.bak.
La procedura di installazione include ripristino automatico su errore di avvio; non è stato necessario attivarlo in produzione.
Dettagli, log e ripristino manuale: [validazione 0.5.5](VALIDATION-0.5.5-20261031.md).

## Limiti e attività aperte

- Cattura schermo ancora non verificata; nessuna dichiarazione di funzionamento.
- Il catalogo plugin non ha restituito Jarvis-Desktop1: nessun aggiornamento del pacchetto plugin eseguito. I parametri operation/project sono comunque stati accettati nelle chiamate dirette verificate in questa sessione.
- Il timeout usa lo stato cancelled; distinguere timeout/stop dal testo context deadline exceeded/context canceled.
- go_test può utilizzare la cache Go. La regressione è stata anche eseguita con go test -count=1 ./... nella verifica del candidato.
- L'output salvato nel risultato è limitato agli ultimi 12000 byte; CombinedOutput acquisisce prima l'output in memoria.
- Annullamento con taskkill non è isolamento dei processi ostili: usare moduli fidati.
- La validazione non ha eseguito un riavvio del notebook né provato l'avvio automatico dopo reboot.
- Modifiche pubblicate sul branch di lavoro, senza merge implicito su main.

---

## Registro storico — non usare come stato corrente

# Jarvis Windows MCP Agent — stato verificato (2026-10-31)

## Ambito
Sottoprogetto distinto dall'app Android Jarvis. Agente Windows (Go) con endpoint MCP Streamable HTTP locale `127.0.0.1:8765/mcp`. Il collegamento ChatGPT usa Cloudflare Tunnel HTTPS e Cloudflare Access Managed OAuth. Non inserire credenziali, token, JWT, AUD o segreti nel repository.

## Versioni
- **0.3.1**: JWT Cloudflare Access e log delle richieste; collegamento MCP ChatGPT effettivamente riuscito e `agent_status` verificato da ChatGPT.
- **0.4.0**: `project_list` e `project_status`, limitati a `JARVIS_PROJECTS_ROOT`.
- **0.4.1**: `project_clone` asincrono/interrompibile, limitato a repository GitHub dell'owner `Andry-iu`; prevenzione sovrascritture. Ultima versione eseguita sul notebook; comando `project_clone` realmente invocato da ChatGPT.

## Stato corrente (non confondere con completato)
- Notebook: `Jarvis-Desktop1`; root autorizzata `D:\Jarvis\Projects`.
- Plugin privato `Jarvis-Desktop1`: OAuth Cloudflare stabilito, comunicazione diretta funzionante, strumenti aggiornati fino a `project_clone`.
- Repository da clonare: `Andry-iu/NestingOpt` (privato), branch principale `main`.
- **Clone non riuscito**: l'agente ha avviato l'operazione `task-1`, conclusa `failed` con `fatal: unable to get password from user`. Non dichiarare NestingOpt presente nella cartella.
- Nessuna modifica allo sviluppo NestingOpt è stata effettuata.
- Codex, PowerShell remoto, mouse/tastiera, gestione modifiche in corsa e motore autonomo **NON ancora implementati**.

## Punto esatto di ripresa
La prossima operazione concordata era, sul notebook, in una seconda PowerShell:
```powershell
git config --global credential.helper manager
```
Poi completare l'autenticazione GitHub in Git Credential Manager **senza condividere credenziali in chat** e ritentare via MCP:
`project_clone({ "repository_url": "https://github.com/Andry-iu/NestingOpt.git", "project": "NestingOpt" })`.
Monitorare con `agent_status` e verificare con `project_status` o `project_list`.

**L'utente ha richiesto esplicitamente di sospendere qui l'operativita sul clone e prioritizzare la conservazione del progetto su GitHub.** Non riprendere il clone senza una nuova richiesta.

## Verifiche effettive
- 0.3.1: `agent_status` ha restituito `idle`, versione `0.3.1`.
- 0.4.1: `agent_status` ha restituito `idle`, versione `0.4.1`.
- 0.4.1: `project_clone` ha restituito `running` (`task-1`), quindi `agent_status` ha restituito `failed` per mancata autenticazione Git.
- Build/test locali delle release riportati come superati durante la produzione dei pacchetti, ma l'ambiente attuale non sostituisce un collaudo completo Windows/ChatGPT per ogni funzione.

## Ripartenza fra computer/account
1. Recuperare repository Jarvis, sottoprogetto Windows MCP, documentazione di continuita e artefatti di release.
2. Installare Go >=1.23.2 e Git, configurare credenziali GitHub tramite Git Credential Manager.
3. Compilare: `go test -race ./...` e `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build ...`.
4. Configurare localmente, mai in Git: `JARVIS_MCP_TOKEN`, `JARVIS_CF_TEAM_DOMAIN` (URL https completo), `JARVIS_CF_APP_AUD`, `JARVIS_PROJECTS_ROOT`.
5. Verificare Cloudflare Access e Managed OAuth, poi aggiornare gli strumenti del plugin ChatGPT a ogni modifica dello schema MCP.
6. Confermare `/health`, `agent_status` e un comando reale dal plugin prima di dichiarare operativo il rilascio.

## Sicurezza
Il token MCP precedentemente esposto in una chat e stato sostituito; non replicarlo. La cartella autorizzata limita i repository; il prototipo non esegue shell arbitraria. Il notebook deve mantenere una sessione desktop attiva; la sospensione interrompe il tunnel.

## Allineamento GitHub (2026-10-31)
I sorgenti integrali della versione **0.4.1** (13 file: Go, test, `go.mod`, README e guide di upgrade) sono stati pubblicati come **file singoli in chiaro** sotto `windows-mcp-agent/`, senza ZIP. Al momento del commit, tutti i 13 contenuti hanno superato il controllo di lunghezza byte rispetto ai file originali. Il test `go test ./...` e passato su Linux/amd64 con Go 1.23.2. Il collaudo completo su Windows della copia Git e la compilazione da checkout remoto restano da eseguire. Nessuna credenziale reale e stata inserita nei sorgenti.


## MCP 0.5.0 source release (2026-10-31)

Published update_prepare/update_execute, Cloudflare Access JWT required for both, start-agent PowerShell/CMD examples and local environment templates. No actual 0.5.0 install performed on Windows; desktop still runs 0.4.1. Windows integration of detached restart/rollback is pending. Existing failed NestingOpt clone remains paused.
