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


