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

# 31 ottobre 2026 — installer Desktop2 1.0.0

Richiesta utente: installazione indipendente del secondo PC. Aggiunti wizard locale, credenziali/ID nuovi, task interattivo, configurazione tunnel locale, verifiche/ripristino non distruttivo e pacchetto con binario 0.6.1 verificato. Suite Windows isolata completata. Account Cloudflare/Access e nuovo plugin restano passaggi dell'utente; nessun secondo PC ancora configurato e nessuna modifica al servizio Desktop1. Sorgenti/test/manifest e builder su Git; [verbale e limiti](INSTALLER-DESKTOP2-1.0.0.md).

# Registro significativo — Jarvis Windows MCP

## 31 ottobre 2026 — 0.6.1

Problema: attese Codex trattenevano il lock globale e lo scheduler; i turni avevano uno stop fisso di cinque minuti; la coda tratteneva tutti i terminali; progetti distinti potevano condividere risorse senza coordinamento.

Decisioni approvate: nessuno stop temporale e nessun avviso temporizzato; archivio tecnico locale per computer e registro significativo sul Git del progetto; coordinamento read/write delle risorse note. Implementati RPC fuori lock, avvio asincrono, archivio prima della compattazione con dipendenze storiche, activity_history, identita Git comune e risorse dichiarate. Guardia economica invariata.

Sorgenti binario 164cb980bd1a936749d41b8692979e98b929cb04, branch reconcile-windows-mcp-agent-20261031. Test/vet/build Windows passati. Prova RPC simulata di 310 secondi e collaudo MCP isolato passati. Backup verificato, rollback isolato e installazione 0.6.1 su Desktop1 riusciti. Turno Codex reale JARVIS_MCP_061_OK e quota finale archiviata. Nessuna modifica Android o ad altri progetti. Dettagli, checksum e limiti in VALIDATION-0.6.1-20261031.md.

Prossimi collaudi distinti: altri PC, riavvio completo del computer e disponibilita dei nuovi strumenti diretti in altri client. Risorse esterne non dichiarate richiedono coordinamento; nessun controllo universale del sistema operativo.



