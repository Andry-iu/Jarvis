# Windows MCP Agent 0.5.7 — collaudo del 31 ottobre 2026

## Rilascio installato

- Repository Andry-iu/Jarvis; branch reconcile-windows-mcp-agent-20261031. Nessun merge/force-push a main, nessuna modifica all'app Android.
- Commit dei sorgenti incorporato nel binario: a10c9bdb1a87362bf904fbc24b697489f9b7080f. Le successive modifiche di verbale/continuita sono solo documentali.
- Compilatore Go 1.27.0, Windows AMD64, checkout D:\Jarvis\MCP-Source\windows-mcp-agent. Nessuna build GitHub Actions.
- Eseguibile operativo D:\Jarvis\jarvis-mcp.exe, 11.962.880 byte, SHA-256 4A5A56B735879B256EA15A4F3B5C99568BC996D67BC5D4663CCAFFA8488CDD5F.
- Health e agent_status tramite plugin: 0.5.7. TokenElevation del processo realmente in ascolto: true. Non e dedotto dalla sola PowerShell del chiamante.
- Backup privato D:\Jarvis\Backups\mcp-0.5.7-20261031: eseguibile 0.5.6, configurazioni locali, startup e prova di sostituzione/ripristino. SHA precedente C230A1FF4EE852744318931FB1BF620022985B01606DFED5D1B1061182F410A5.
- Verificata conservazione stash backup update.go locale 20261031 e update.go.bak. Nessuna credenziale/configurazione privata, immagine del desktop o binario pubblicati in Git.

## Prove effettive

| Area | Esito e limite |
|---|---|
| Suite Go | go test -count=1 ./... e go vet ./... superati; build Windows superata. Test Go dell'agente installato tramite plugin completato con exit 0, ok jarvis-mcp-prototype 7.667s. |
| Sicurezza file | Rifiuto percorsi esterni, ADS, nomi DOS, credenziali note, symlink/reparse point e hardlink. Test binario 1 MiB su trasporto HTTP MCP superato; richiesta autenticata limitata a 8 MiB per includere base64/escaping JSON. |
| Concorrenza | Hash obsoleto rifiutato, destinazione gia esistente preservata, argomenti obbligatori/per operazione verificati. Attesa di sharing lock Windows verificata con lock OS reale; precondizioni ricontrollate, nessun retry di azioni desktop/modello. La sostituzione non e una transazione CAS globale con editor esterni. |
| File/cartelle | Creazione annidata, lettura/scrittura Unicode, rinomina e spostamento di file E directory, undo e verifica digest. Prova via plugin reale e pulizia mediante ricevute completata. |
| Backup/restore | Backup verificato prima di sostituire file. Restore rifiuta modifiche dell'utente successive. Rename no-replace nativo per handle; spostamenti fra volumi esclusi. |
| Comandi | Test, build e cancellazione/timeout Go verificati. Job Object Windows assegnato prima del resume del processo sospeso; verifica che i discendenti siano terminati. Ambienti figli filtrati e output limitato/persistito, stdout/stderr separati. |
| Build Go installata | command_start go_build tramite plugin: exit 0, job 989fa4f0d4b4ac3b9fda34ede3d43231. Eseguibile Staging/build-989fa4f0d4b4ac3b9fda34ede3d43231.exe verificato presente, 11.962.880 byte. |
| .NET | SDK 9.0.317 rilevato. Progetto console net9.0 creato tramite API file e compilato tramite dotnet_build: exit 0, 0 avvisi, 0 errori; JarvisSmoke.dll prodotta. Job 8bbda9f9e74a9864c141b979aec7840a. Nessun collaudo reale di un test framework .NET o di MSBuild/C++ dichiarato. |
| Persistenza | Identita, ricevute completate e risultati job conservati dopo arresto/riavvio dell'istanza isolata. Replay completato restituisce ricevuta, ID modificato o esito incerto non viene rieseguito. Job rimasti running diventano interrupted, senza replay. Lock fra istanze e ACL private verificati nei test. |
| Cattura | Risultati MCP text+image JPEG ricevuti e mostrati da questa chat. Virtual desktop 3840x1080, origine -1920, immagine 960x270; monitor selezionato anche a 1920x1080. Nessun log base64/URL pubblico. Blocco desktop/secure desktop rifiutato; nessun clic ricavato dalle coordinate. |
| Piu computer | Due istanze isolate con ID distinti, bearer autenticato, inoltro, rifiuto identita errata e stato offline superati. E un test sullo stesso PC, non su un secondo computer fisico. Produzione: solo dispositivo locale configurato. |
| Aggiornamento | Prima dell'intervento ricontrollati 0.5.6, percorso/PID e hash. Backup verificato; candidato 0.5.7 e backup 0.5.6 eseguiti sullo stesso percorso di prova isolato e MCP verificato in entrambi. Successiva sostituzione reale, health, riconnessione plugin, hash e amministratore verificati. Porte 8766/8767 libere al termine. |
| Codex senza inferenza | CLI installata 0.165.0-alpha.1.2, App Server stdio, account ChatGPT Business self_serve_business_prolite, quote e lista sessioni del progetto verificati. Un solo broker Jarvis attivo, figlio dell'agente operativo, al controllo finale. |

## Blocchi rimasti

Codex restituisce quota primaria usata 12%, hasCredits=false, unlimited=false e balance=null. La UI autenticata Impostazioni/Utilizzo mostra 88% residuo e 0 crediti dell'area di lavoro, ma lo stato della ricarica automatica non e stato verificato. Il codice installato rifiuta il saldo API nullo: avvio/ripresa/turni/steer non sono collaudati con inferenza reale. Nessuna richiesta al modello, chiave API o acquisto e stato avviato da Jarvis.

La revisione automatica ha respinto un batch proposto per accettare il saldo nullo e avviare un test. Quel batch non e stato eseguito; la modifica e stata annullata con commit di ripristino e test di regressione. Non aggirare il blocco. Serve una verifica/autorizzazione appropriata delle condizioni di utilizzo incluso prima di proseguire con quel collaudo.

Catalogo server: 38 strumenti, dei quali 27 runtime nuovi. Il client di questa chat espone ancora i dieci strumenti precedenti: il percorso di compatibilita task_start operation=runtime e stato verificato, ma non equivale all'aggiornamento del catalogo del plugin o delle sue istruzioni installate. La ricerca del plugin per nome non ha restituito un backend ID modificabile. Richiedere aggiornamento del catalogo in Impostazioni/Plugin/Jarvis-Desktop1, poi collaudare una nuova chat e Voice se il client li supporta. Nessuna garanzia di disponibilita in tutte le chat o superfici.

Altri PC fisici richiedono agente/connessione/identita/progetti autorizzati configurati localmente. MSBuild, CMake e cl non erano nel PATH dell'agente; non e stata esclusa la loro presenza altrove e non e stata installata una toolchain aggiuntiva. Avvio dopo reboot non collaudato. Clone NestingOpt precedentemente sospeso: non ripreso.

Log operativi locali, non pubblicati: diagnostics/v57-build.log, v57-runtime-test.log, v57-deploy.log e v57-final-check.log. Stato persistente D:\Jarvis\State\runtime-v1; i segreti restano in D:\Jarvis\Config.
