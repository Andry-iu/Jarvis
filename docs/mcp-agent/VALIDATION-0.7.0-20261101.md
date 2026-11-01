# Windows MCP 0.7.0 — verbale del 1 novembre 2026

## Ambito, autorizzazioni e provenienza

Aggiornamento autorizzato di Desktop1 e Desktop2: comandi diretti per processi/finestre/controlli/dialoghi, default non amministratore per i processi, conservazione log/batch 30 giorni anche senza impostazione e controllo ogni 24 ore. Pubblicazione nel repository pubblico Andry-iu/Jarvis e trasferimento dei sorgenti runtime a Desktop2 autorizzati esplicitamente dopo il blocco iniziale della revisione automatica. Non richiedere nuovamente questi consensi.

Branch reconcile-windows-mcp-agent-20261031. Sorgenti binari pubblicati nel commit 54aed96d94f2b02f35b31168ca8cafee217b4158, parent bf2ceb22fab800b453654757c4d3296372f344a8, albero 6cdde8ce7efc42335a32804fc125967a6687c293. Lo stesso albero del commit locale 010f73c67a18422f733c9a001b69d610dab53c1c e stato pubblicato tramite il connettore GitHub, normalizzando CRLF come Git. Il tentativo Git CLI, rimasto in autenticazione, e stato fermato prima della pubblicazione alternativa. Commit locale conservato nel branch backup/windows070-local-010f73c; checkout riallineato senza cambiare contenuti e senza force-push. Nessuna modifica Android/main, credenziali, Cloudflare remoto, guardie economiche o inferenza.

## Binari e prove prima dell'installazione

| Computer | Candidato | SHA-256 |
|---|---|---|
| Desktop1 DESKTOP-1ESSB0K | Go 1.27.0 Windows amd64 | 1542248E430042D1DAB7E913C889A1242318E6CDFD276EBEC22A927F4FF16B22 |
| Desktop2 AUTECHVPC2 | Go 1.27.3 Windows amd64 | 095AC88D21BC75AB4EF2AF232EF568C18CE03FC36A202FF5C038C98E2075BC08 |

Desktop1: go test job 176f50e208b9f7fad8bc72f910830cad, vet f41075e1ee7857d24e975a941380ca2d, build 0b97e0ba436b0f479a96d82a6bc2ace3, tutti completati con exit 0. Coperti token reale del figlio non elevato, duplicazione richieste, letture durante mutazione esterna, default/limiti retention, batch vecchi/nuovi/preparati/in esecuzione, parser PowerShell/C# e rettangoli UIA vuoti. Coordinate infinite di Rect.Empty convertite in rettangoli nulli, mai JSON non valido. Limite worker 65536 byte conservato.

Desktop2: trasferiti soltanto 45 file runtime, senza configurazioni/credenziali/stato/test. Bundle b972ca9ecde27820e9910b409425314690df57565c5f62653b13c1471339f217: la scrittura aveva dato errore di verifica del lettore protetto, ma ricevuta e checksum indipendente hanno confermato gli stessi byte, prima dell'estrazione. Nessun replay della scrittura incerta. go vet e build superati; non dichiarare eseguita l'intera suite Go su Desktop2, perche non sono stati trasferiti i test.

Collaudo MCP candidato isolato su entrambi: manutenzione default 30/30/24, cartella Desktop effettiva, avvio senza elevazione usando token shell verificato, richiesta duplicata con stesso PID, scrittura ciao, salvataggio mediante dialogo dell'applicazione e testo risultante verificato, chiusura ordinaria. Entrambi i log v70-direct-test.log terminano con PASS ALL. Notepad moderno su Desktop1 e classico su Desktop2. I batch qui lanciano il collaudo/candidato; le operazioni desktop del collaudo sono i nuovi strumenti, non script che scrivono il documento.

## Desktop1 installato e collaudato da ChatGPT

Machine_id 1b927a030fa3b7dfa15c40cfccb4ff60 invariato. Installata 0.7.0, hash sopra, configurazioni verificate immutate; backup D:\Jarvis\Backups\mcp-0.7.0-1542248E4300. v70-deploy.log: BACKUP_VERIFIED, ROLLBACK_REHEARSAL_PASS e PASS installed_health_identity_config_maintenance. Migrazione e ritorno alla 0.6.1 provati su copia isolata dello stato.

Dopo installazione, chiamate MCP reali tramite task_start operation=runtime: avvio Notepad standard, focus esatto, digitazione, Salva con nome, verifica e chiusura. File C:\Users\Andrea\Desktop\ciao-070.txt, 4 byte, testo esattamente ciao, SHA-256 b133a0c0e9bee3be20163d2ad31d6248db292aa6dcb1ee087a2aa50e0fc75ae2, method=application_file_dialog, text_matches=true, verified=true; window_close status=closed e forced=false. Nessun batch per queste azioni. La verifica immediata dopo type_text era false, ma il salvataggio successivo ha verificato il contenuto: non ripetere automaticamente una digitazione in attesa.

maintenance_status sano: retention log/batch 30 giorni, interval_hours=24, last_check 2026-11-01T10:29:43Z, next_check 2026-11-02T10:29:43Z. Batch preparati/in esecuzione protetti. Non e ancora trascorso un ciclo reale di 24 ore. Riavvio fisico e refresh diretto del catalogo plugin non collaudati.

## Desktop2: installazione interrotta, non dichiarare completata

Machine_id d41455adf8721c205183224d3c69a1a5, ultima versione operativa confermata 0.6.1. Candidato 0.7.0 compilato e collaudato, backup D:\Jarvis\Backups\mcp-0.7.0-095AC88D21BC verificato. Il primo deploy generico si e fermato sul runner.lock; escluso il lock effimero dal backup. Il successivo tentativo di rehearsal e stato fermato conservando l'agente operativo, prima di applicare un percorso gestito adatto all'installer.

Il percorso gestito usava Scripts\Stop.ps1: Stop-OwnedTree dell'agente termina anche il suo discendente updater. Il collegamento remoto si e interrotto nel passaggio di arresto; non si puo verificare da MCP il successivo stato locale. Non dichiarare installata la 0.7.0 o eseguito il collaudo installato su Desktop2. Non ripetere ciecamente il batch originale.

Correzione salvata in scripts/deploy-managed-runtime-070.ps1: arresto del solo PID verificato dell'agente, senza /T, supervisore esistente incaricato di chiudere il tunnel/rilasciare il lock; aggiornamento atomico di installation.json (versione/hash/provenienza), riavvio dello stesso Start.ps1 e attesa dei record agente/tunnel per evitare una verifica prematura. Stato corrente, identita e configurazioni durevoli preservati; rollback binario/manifest gestito senza ripristino cieco dello stato. Il deploy legacy ora rifiuta Config\installation.json e indica quello gestito. Parser Windows delle due versioni corrette verificato; installazione/rollback reale della correzione restano da eseguire su Desktop2.

Recupero limitato da Desktop1 tentato senza nuovi segreti o modifiche di sicurezza: WinRM non disponibile con le credenziali integrate e configurazione corrente; RPC Task Scheduler scaduto nel limite di otto secondi. Nessun TrustedHosts, credenziale o permesso cambiato. Stato remoto ancora irraggiungibile. Richiesto all'utente un avvio locale in PowerShell amministratore:

    powershell -NoProfile -ExecutionPolicy Bypass -File "D:\Jarvis\Scripts\Start.ps1" -HomeDir "D:\Jarvis"

Il comando usa configurazione e collegamento gia installati. Se segnala un processo registrato attivo, non forzare: controllare running.json e identita dei PID tramite i meccanismi previsti.

## Ripresa esatta

1. Richiamare machine_info Desktop2 e verificare l'identita sopra. Leggere v70-managed-deploy.log, installation.json e ricevute localmente senza esportare segreti. Verificare versione/hash realmente operativi e nessuna coda o attivita attiva.
2. Trasferire su Desktop2 lo script gestito corretto dal repository: la copia locale Agent070-source del tentativo interrotto e precedente alla correzione. Non usare il deploy legacy, ne ripetere l'update ID precedente.
3. Usare il candidato/hash Desktop2 gia verificati; completare rehearsal, installazione gestita, record di agente/tunnel e controllo MCP dopo riconnessione. Aggiornare questo verbale con gli esiti effettivi.
4. Ripetere prova diretta con documento di test separato ciao-070.txt, nessuna sovrascrittura predefinita, poi chiusura ordinaria. Aggiornare infine gli strumenti di Jarvis-Desktop1 e Jarvis-Desktop2 e verificare il catalogo diretto. La compatibilita task_start/runtime e gia verificata su Desktop1.
5. Pubblicare sorgenti/test/documenti/checksum pertinenti senza nuove conferme gia acquisite; mantenere backup, archivio, ricevute e configurazioni privati. Nessun riavvio fisico, Voice o nuova inferenza richiesti da questo rilascio.
