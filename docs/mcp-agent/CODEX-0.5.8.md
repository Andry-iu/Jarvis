# Codex 0.5.8 — installata e collaudata, 31 ottobre 2026

Il 31 ottobre 2026 l'utente ha confermato l'aggiornamento degli strumenti del plugin e fornito la schermata autenticata Utilizzo e limiti: 88% quota settimanale residua, 0 crediti, ricarica automatica Disattivato. Questo e il nuovo riscontro che mancava al tentativo respinto nella 0.5.7. L'immagine e i dati dell'account non vanno pubblicati in Git.

La 0.5.8 conserva la guardia rigorosa originale sui dati API. Solo per balance=null puo utilizzare un riscontro locale distinto, in Config/codex-cost-proof.json, legato all'identita del computer e all'hash dell'account ChatGPT/piano. Deve dichiarare zero crediti e ricarica automatica disattivata, provenire dalla schermata confermata dall'utente e avere validita massima di sette giorni. Il file non e scrivibile tramite l'API file dei progetti e non viene accettato nei parametri degli strumenti Codex.

Restano obbligatori, a ogni turno: account ChatGPT Business verificato, hasCredits=false, unlimited=false, quota valida sotto il 90%, nessun blocco di spesa/limite. Un saldo positivo o flag credito ignoti non possono essere superati dal riscontro. Alla scadenza/cambio account la guardia torna chiusa. Il saldo API rimane visibile come nullo nel risultato, insieme alla provenienza/scadenza del riscontro: non viene falsificata la risposta del servizio.

Nessuna chiave API, acquisto, ricarica o modifica alle impostazioni di pagamento. Provider/tier e sandbox restano controllati. Test negativi superati per riscontro mancante/scaduto, account/PC errato, ricarica non disattivata, crediti positivi e quota sconosciuta. La prova iniziale respinta senza riscontro non e stata aggirata: la nuova schermata dell'utente ha fornito il fatto mancante, registrato separatamente e ricontrollato.

## Risultati verificati

| Verifica | Esito |
|---|---|
| Catalogo plugin | 38 strumenti diretti disponibili; machine_info e codex_status chiamati direttamente, senza il percorso di compatibilita. |
| Test e compilazione | go test -count=1 ./... superato (5.914s), go vet superato, build Windows AMD64 con Go 1.27.0. |
| Risposta reale | Turno Codex completato, risposta JARVIS_MCP_OK. |
| Storico | Turni letti da codex_read; stato conservato dopo riavvio dell'agente isolato. |
| Steer e interrupt | Istruzioni aggiunte al turno esatto, poi interrupt accettato e evento finale interrupted verificato. |
| Resume | Sessione ripresa dopo arresto e riavvio dell'agente, storico precedente presente; nuovo turno completato con JARVIS_RESUMED_OK. |
| Modifica file | Codex ha modificato un file di prova nel progetto autorizzato tramite apply_patch. Lettura indipendente via MCP: JARVIS_CODEX_FILE_OK, SHA-256 e4e64dfe5d568a93a3b707ff83bc41e049d01ab7145c6dbb45d3844060b86abb. |
| Backup/rollback | Backup 0.5.7 verificato, configurazioni conservate, 16 file di stato copiati e verificati per hash. Avvio candidato e ripristino backup sullo stesso percorso isolato, health/MCP verificati. |
| Installazione | 0.5.8 operativa in D:\Jarvis\jarvis-mcp.exe. TokenElevation del listener: true. Nessun listener temporaneo 8766/8767 al termine. |
| Collaudo dal plugin | Dopo installazione, nuova sessione e turno reali tramite codex_start/codex_turn; risposta JARVIS_INSTALL_OK, evento completed, nessuna approvazione pendente. |
| Continuita | Machine ID 0.5.7 invariato, vecchio job .NET ancora completed/exit 0; backup update.go.bak e stash precedente presenti. |

Commit sorgente incorporato: 6b5530e8347502f35511aabdf696c58877836cee. Binario 11.971.584 byte; SHA-256 04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE. Le successive modifiche di verbale sono documentali. VCS modified=true riflette i file non tracciati gia presenti; sorgenti tracciati puliti.

Backup: D:\Jarvis\Backups\mcp-0.5.8-20261031, vecchio eseguibile SHA-256 4A5A56B735879B256EA15A4F3B5C99568BC996D67BC5D4663CCAFFA8488CDD5F. Stato attivo D:\Jarvis\State\runtime-v1, senza migrazione del formato. Riscontro costi valido fino al 7 novembre 2026 15:55 UTC; le condizioni API restano ricontrollate a ogni turno e la scadenza blocca il saldo nullo finche non c'e un riscontro aggiornato.

Restano fuori da questo collaudo: nuova conversazione/Voice nel client e altri computer fisici, avvio dopo reboot, toolchain C++ non configurate nel PATH. Il clone NestingOpt sospeso non e stato ripreso. Non promettere accesso a tutte le chat ChatGPT o sessioni fuori dalle radici autorizzate; le prove riguardano App Server Codex locale e il progetto di test.
