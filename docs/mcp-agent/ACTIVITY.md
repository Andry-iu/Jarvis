# Windows MCP 0.7.0 — collaudata, distribuzione in attesa

1 novembre 2026. Sviluppo e build su Desktop1, versione operativa ancora 0.6.1 su entrambi i PC. Comandi diretti e manutenzione implementati; test Windows/vet/build superati. Collaudo MCP isolato completo: avvio standard con token verificato, nessun duplicato con stessa richiesta, scrittura ciao in Notepad moderno, salvataggio tramite applicazione con verifica testo e chiusura ordinaria. Backup verificato e migrazione/rollback isolati superati (v70-deploy.log: BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS, PASS rehearsal_only). Nessuna installazione o prova desktop riuscita viene dedotta dalla sola build.

Candidato: Staging/build-0b97e0ba436b0f479a96d82a6bc2ace3.exe, SHA-256 1542248E430042D1DAB7E913C889A1242318E6CDFD276EBEC22A927F4FF16B22. Backup: D:\Jarvis\Backups\mcp-0.7.0-1542248E4300. Test finale 176f50e208b9f7fad8bc72f910830cad; vet f41075e1ee7857d24e975a941380ca2d; collaudo v70-direct-test.log PASS ALL. Retention default 30 giorni, all'avvio e ogni 24 ore; trascorrere reale di 24 ore non ancora osservato. Documento contratto: docs/mcp-agent/DIRECT-0.7.0.md.

BLOCCO DI DISTRIBUZIONE: revisione automatica ha respinto push pubblico di sorgenti/test/docs e trasferimento sorgenti runtime a Desktop2. Verificati origin fetch/push esatti https://github.com/Andry-iu/Jarvis.git, repository pubblico, account collegato con admin/push. La revisione richiede comunque approvazione utente esplicita per payload e destinazione. Anche un payload ridotto ai soli runtime senza test/config/credenziali e stato respinto. Non aggirare con batch, API diverse, clone o trasferimenti indiretti. Nessun push o trasferimento eseguito; su Desktop2 e stata creata soltanto la directory vuota D:\Jarvis\Projects\Agent070-source. Non dichiarare installata 0.7.0 e non richiedere ancora refresh plugin.

PROSSIMO PASSO: chiedere approvazione specifica per pubblicare sorgenti/test/documentazione 0.7.0 su Andry-iu/Jarvis, branch reconcile-windows-mcp-agent-20261031, e trasferire i soli sorgenti runtime a Desktop2 AUTECHVPC2 (machine_id d41455adf8721c205183224d3c69a1a5) per compilazione/installazione. Dopo il consenso: verificare HEAD/remote e push fast-forward; installare Desktop1 con deploy-runtime-070.ps1 e hash sopra, source_commit corretto e identita 1b927a030fa3b7dfa15c40cfccb4ff60. Desktop2: distribuire sorgente verificato senza segreti, build Windows, test e backup/rollback prima del deploy; Go 1.27.3 puo produrre hash diverso. Poi collaudo diretto ciao.txt dal Salva di Notepad su entrambi e aggiornamento cataloghi plugin. Stato/identita/ricevute/configurazioni/guardie economiche invariati, nessuna inferenza.

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



