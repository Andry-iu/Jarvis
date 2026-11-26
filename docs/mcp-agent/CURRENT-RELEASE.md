## Desktop1 — Jarvis Agent 0.9.8 (26 novembre 2026)

Desktop1 risponde con Jarvis Agent **0.9.8** e privilegi elevati. La ricevuta dell'updater è completata e la pulizia è verificata. Il commit installato è [`865bf9fd1f5bdcf6c0c83d5ece96fb20d6c50375`](https://github.com/Andry-iu/Jarvis/commit/865bf9fd1f5bdcf6c0c83d5ece96fb20d6c50375). Il comando IRCESP32 USB-TTL è raggiungibile dalla chat tramite il percorso MCP di compatibilità.

Il firmware 0.4 è compilato e la scheda deve ancora essere programmata e collaudata sul banco. Per gli altri PC resta valido l'ultimo stato verificato nelle voci storiche. [Installazione, test e ripresa](IRCESP32-USB-0.9.8.md).

> **Stato storico — 22/11/2026, dopo i collaudi Paint:** consultare prima [STATUS-CONSOLIDATO-0.9.6-20261122.md](STATUS-CONSOLIDATO-0.9.6-20261122.md). **Desktop1 e Desktop2** sono verificati su **0.9.6**. Su Desktop1 il disegno Paint (rettangolo nero, quattro cerchi rossi, `Jarvis` blu piccolo al centro) e stato **ottenuto e verificato visivamente**, con varie chiamate e correzioni; **non esiste ancora una baseline riuscita al primo tentativo**. In seguito Paint e stato chiuso scegliendo **Non salvare** e confermando l'assenza della finestra. Nel test sono comparse **conferme ChatGPT per azioni potenzialmente distruttive** (in particolare `CTRL+A` + `DELETE`): l'assenza del token e del `machine_id` non le sopprime. I permessi dei plugin risultano `Consenti azioni a basso rischio` ereditato, **non sono stati modificati**. Le sezioni successive rappresentano lo stato storico al momento di ciascuna registrazione.

## Jarvis Windows MCP 0.9.6 — identita locale implicita su tutti gli strumenti (22/11/2026)

**Codice integrato:** commit `a6588c3590ce338aa127b3cfd1615a4481e592f1` (PR #13) su `main`. Test GitHub Actions `45072776302`: **Go e PowerShell passati** dopo aggiornamento test legacy `TestRuntimeMachineMismatchAndCatalog`.

- **Desktop1:** `agent_update_status` **completed**, `cleanup_verified=true`, versione live **0.9.6**, privilegi elevati confermati. SHA-256 binario installato `fb48aa3c96c5fbc56a6d9681ed2624de804bffe92cca1801919ea2f951666aa6`. Request `jarvis-d1-096-implicit-id-20261122-01`.
- **Desktop2:** `agent_update_status` **completed**, `cleanup_verified=true`, versione live **0.9.6**, privilegi elevati confermati. SHA-256 binario installato `1267019010f717fa4aef5d91596deb635f4f19a3f7a1ae8cea9628bb14202c71`. Request `jarvis-d2-096-implicit-id-20261122-01`.
- **Smoke test senza machine_id:** `task_start(operation=runtime,request={operation:"project_catalog"})` riuscito su entrambi senza inviare l'identificatore: Desktop1 7 progetti, Desktop2 5. Il bridge e stato usato solo per provare il nuovo input contro lo **schema ChatGPT ancora precedente**: lo strumento `project_catalog` diretto nella sessione continuava a richiedere `machine_id`, e rifiutava l'omissione prima dell'invio. **Dopo il refresh del catalogo MCP il client potra usare direttamente lo schema nuovo**, senza wrapper. La conferma della disponibilita in ChatGPT resta pendente.
- Tutti gli strumenti dedicati (file, processi, coda, Codex, gateway, aggiornamenti, UI) **non richiedono piu machine_id nello schema MCP pubblicato**: il server deriva la macchina locale dalla connessione autenticata. `machine_id` legacy corrispondente continua ad essere accettato e mismatch rifiutati. `device_call` conserva la selezione esplicita del computer remoto tramite `device_id`.
- Restano autenticazione Cloudflare/Bearer, controllo destinatario, ricevute anti-replay e protezioni contro esecuzioni sulla finestra sbagliata. Cache JWT e JWKS e ottimizzazioni TLS precedenti conservate.
- **Limite esplicito:** queste modifiche non equivalgono alla rimozione di tutte le verifiche e non sono in grado di disattivare o garantire la scomparsa dei popup `Consenti` di ChatGPT. Nessun confronto di performance end-to-end completo effettuato in questo rilascio.

Dettagli: [ALL-TOOLS-IMPLICIT-MACHINE-0.9.6-20261122.md](ALL-TOOLS-IMPLICIT-MACHINE-0.9.6-20261122.md).

---

## Jarvis Windows MCP 0.9.5 — token conversazionale rimosso; Desktop1 e Desktop2 verificati (22/11/2026)

**Versione distribuita:** `0.9.5` dal commit `c8887f01298f3ec10ad3ee5ca3f8f44fca020186` su `main` (PR #12). GitHub Actions `45070664311`: **Go success**, **PowerShell success**. Include descrizione del tool `task_start` nel catalogo MCP server: **compatibility fallback only**, preferire funzioni dedicate (in particolare `ui_session_open`, `ui_sequence`, `ui_mouse`, `ui_mouse_screen`, `ui_keys`) e ricorrere al wrapper solo in assenza di equivalenti.

- **Desktop1 / DESKTOP-1ESSB0K:** aggiornamento `jarvis-d1-no-token-095-20261122-01` **completed**, `cleanup_verified=true`, versione live `0.9.5`, `elevated=true`, PID `181628`, hash SHA-256 dell'eseguibile installato `84990406b753dfc4c3381b0f9bfeb2ed82aa4902e4153c262f5b3aa093b70cef`; backup `D:\\Jarvis\\Backups\\self-update-6fae2eb5b550477ca4f367c6af2b92a3`.
- **Desktop2 / AUTECHVPC2:** aggiornamento `jarvis-d2-no-token-095-20261122-01` **completed**, `cleanup_verified=true`, versione live `0.9.5`, `elevated=true`, PID `8440`, hash SHA-256 dell'eseguibile installato `259c3f92f0f2a3e66032fb33e900af24e340bd7a9da55606b20a64e14429ead0`; backup `D:\\Jarvis\\Backups\\self-update-36566f1a9f0b4c2a85531ff641159a8e`.
- **Smoke test diretto senza `session_token`:** `project_catalog(machine_id)` su Desktop1 e Desktop2 **riuscito**; rispettivamente 7 e 5 progetti. Nessuna risposta contiene `session_token` o `session_idle_timeout_seconds`. `machine_info` ha confermato versione e privilegi su entrambi.
- **Conservate** le ottimizzazioni delle versioni 0.9.2–0.9.4: riuso TLS/peer, cache identita pinned, cache JWT gia verificati con inattivita mobile di 30 minuti e tetto assoluto `exp`, cache JWKS 30 minuti, `ui_sequence` con pause e controlli UI. Restano tutti i controlli Cloudflare/Windows/per operazione; **nessuna** modifica delle policy Cloudflare Zero Trust.
- **Limite lato ChatGPT:** il client puo tenere in cache la vecchia descrizione e i vecchi schemi dei tool MCP, incluso il parametro facoltativo `session_token`. L'agente **non lo pubblica e non lo accetta piu**: omettere sempre il campo. La descrizione `task_start` e' pubblicata nel `tools/list` di Jarvis, ma la visibilita nel collegamento ChatGPT richiede il refresh del catalogo; questo non e' stato eseguito direttamente dall'agente Windows. L'aggiornamento del server da solo non annulla approvazioni obbligatorie del plugin host.
- **Stato test Paint:** non e' incluso un nuovo collaudo grafico nel rilascio 0.9.5; il collaudo Paint + quattro cerchi rossi + scritta blu `Jarvis` e la baseline temporale completa rimangono da verificare con strumenti UI effettivamente esposti dal client.

Decisioni: [AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md](AUTH-OPTIMIZATIONS-NO-TOKEN-0.9.5-20261122.md).

---

## Jarvis Windows MCP 0.9.4 - distribuita e collaudata su Desktop1 e Desktop2 (22/11/2026)

**Fonte certificata:** repository `Andry-iu/Jarvis`, branch `main`, PR #9 (sessione/caching), PR #10 (compatibilita con schema MCP vecchio), commit di rilascio **`fc4ecf67e7654ab2a1d0f85f0e037edb0ba5802f`**. Verifica CI candidato PR #10: GitHub Actions `45061676910`, job **Go success** (test, vet, build nativa e Windows) e **PowerShell success**. CI 0.9.3 sul candidato finale `45061147733` anch'essa superata.

- **Desktop1 / DESKTOP-1ESSB0K**: `jarvis-d1-update-094-20261122-legacy-token-01` **completed** alle **19:39:20 UTC**, `cleanup_verified=true`; hash SHA-256 eseguibile installato `5453663dd9acd86e7e3d46173d94e4751e8df1979f63345f60aeeab0653ba0cc`; backup `D:\\Jarvis\\Backups\\self-update-ec128f653ddc4d9abf6a3feb5dd8f4dd`. `machine_info` live: **0.9.4**, PID **174172**, elevated **true**, alle **19:40:02 UTC**.
- **Desktop2 / AUTECHVPC2**: `jarvis-d2-update-094-20261122-legacy-token-01` **completed** alle **19:39:21 UTC**, `cleanup_verified=true`; hash SHA-256 eseguibile installato `ba5cb733384c573b375e6efe8e8a44c8360c948a3202c859d881533e9444b250`; backup `D:\\Jarvis\\Backups\\self-update-98bc97d8469d461983c3c73ec4aeada3`. `machine_info` live: **0.9.4**, PID **6728**, elevated **true**, alle **19:40:01 UTC**.
- **Test funzionale reale su entrambe**: `task_start(operation=runtime,request={operation:machine_info,session_token:"0"})` ha emesso un token nuovo e il valore `session_idle_timeout_seconds=1800`; riuso del medesimo token con una **diversa funzione** (`project_catalog`) riuscito senza una seconda emissione; invio di token sconosciuto ha emesso un nuovo token; suo riutilizzo con `project_catalog` riuscito. Desktop1 7 progetti, Desktop2 5. Nessun token segreto e conservato in Git o nei verbali.
- **Funzionamento**: 30 minuti di inattivita, rinnovati con ogni comando valido, indipendentemente dalla famiglia di strumenti. `0`, token scaduto o sconosciuto avviano una nuova sessione con autenticazione esistente; la prima azione si esegue nella stessa chiamata. Token temporanei casuali a 256 bit, hash in RAM e vincolo all'identita autenticata, separati per macchina, persi al riavvio. I client MCP con schema precedente usano il bridge `task_start(operation=runtime,request={...session_token...})`; il nuovo parametro diretto resta in attesa del refresh dello schema ChatGPT.
- **Cloudflare**: cache di JWT **gia verificati** con scadenza mobile dopo 30 minuti di inattivita, sempre e comunque limitata a `exp` del JWT; cache **chiavi JWKS** con refresh periodico a 30 minuti. Resta obbligatoria l'autenticazione Cloudflare Access esterna e la verifica dei permessi, machine_id, anti-replay, grant. La **sessione globale/app/policy di Cloudflare non e stata modificata**: va verificata sul dashboard Zero Trust per evitare login frequenti; le durate predefinite documentate sono 24h salvo override. Il token Jarvis non rinnova ne aggira tali policy e non sopprime popup del plugin host.
- **Limitazioni**: nessuna attestazione di isolamento per chat senza identificatore fidato e conferma host; due chat con lo stesso token e le stesse credenziali potrebbero condividere la lease. Non e ancora stato eseguito un benchmark comparativo di latenza end-to-end: nessuna riduzione percentuale e stata certificata. L'installer ZIP 0.9.4 non e stato generato/attestato.
- **Documentazione tecnica/decisioni**: [SESSION-TOKEN-0.9.3-20261122.md](SESSION-TOKEN-0.9.3-20261122.md), [CHAT-WIDE-AUTHORIZATION-20261122.md](CHAT-WIDE-AUTHORIZATION-20261122.md) e changelog.

---

## Jarvis Windows MCP 0.9.2 - distribuita su entrambi i desktop (22/11/2026)

- **Fonte**: `main` di `Andry-iu/Jarvis`, merge PR #8, commit esatto `7705886eef8703a3770b35c8d84ec195abf68de2`. CI sul candidato `5b2d180d2683d3ba1d422d48628101f443089fcb`: job Go e PowerShell entrambi `success` (test, vet, build Linux e cross-build Windows).
- **Desktop1**: `jarvis-d1-update-092-20261122-speed-01`, `completed` alle 19:09:36 UTC, `cleanup_verified=true`, SHA256 installato `45130731c35e360adfa850014d46b0425b8b6bf8b5bf2ea0380c522641db2e57`; lettura live `machine_info.version=0.9.2`, PID 171296, elevated true alle 19:09:52 UTC.
- **Desktop2**: `jarvis-d2-update-092-20261122-speed-01`, `completed` alle 19:09:44 UTC, `cleanup_verified=true`, SHA256 installato `40b7c6e4708a2b7176e8a8d31c3a301ab052421d483b5d5cea8cf4f43fdb2556`; lettura live `machine_info.version=0.9.2`, PID 11132, elevated true alle 19:09:51 UTC.
- **Smoke test**: `agent_status` e `project_catalog` hanno risposto correttamente su entrambe le macchine; la misura osservata dal coordinatore è circa 2,5 secondi per chiamata, **non** un confronto causale prima/dopo e non misura la sola latenza Go.
- **Modifiche**: connessioni HTTPS/TLS peer riutilizzabili (pin e endpoint nella chiave), check positivo dell'identita macchina con scadenza 300 secondi soltanto con pin TLS verificato, catalogo tool immutabile precomputato, conversioni JSON evitabili eliminate dalla telemetria. Bearer e permessi, matching machine_id, parametri e ricevute continuano a essere controllati per ogni comando.
- **Non implementato**: consenso generale della singola chat con inattivita 300s, in attesa di identita verificabile della chat e segnale di concessione da ChatGPT/plugin host; il timeout della cache di identita **non** e un consenso. Non alterati i permessi persistenti dei plugin.
- **Da misurare**: latenza reale prima/dopo su percorsi gateway controllati, disponibilita del percorso chat-wide auth, eventuale overhead di orchestrazione e telemetria. Nessuna certificazione di un guadagno percentuale senza benchmark comparativo. Installer ZIP 0.9.2 non creato automaticamente da questa release.

---

## Desktop1 0.8.8 — aggiornamento completato, riconnessione da verificare (22/11/2026)

- L'utente ha verificato in PowerShell su Desktop1 il processo `jarvis-mcp` attivo con PID `158788`, avviato alle 18:03:31 locali dopo il comando `Start-ScheduledTask` (lo script precedente avviava l'attività solo in assenza di updater ancora in esecuzione).
- Il file locale `D:\Jarvis\Logs\diagnostics\self-update.log` riporta `START 2026-11-22T16:57:10.3199909Z`, `BACKUP_VERIFIED D:\Jarvis\Backups\self-update-1ec26428affa4969907734ce67938ff2`, `ROLLBACK_REHEARSAL_PASS`, `INSTALLED_SHA256 fe0300155de37747907ddbfbe6efa09a65f40559ec146729dc98cafaf95278bf`, `PASS common_batch_update_identity_config_maintenance`.
- Queste evidenze attestano esito positivo del batch di aggiornamento e processo Jarvis nuovamente in esecuzione, ma **non** confermano ancora la raggiungibilità del connettore MCP esterno o una nuova lettura live di `machine_info.version=0.8.8`. Dopo questa segnalazione, `machine_info`, `agent_status` e `agent_update_status` continuano infatti a restituire `The tool failed internally` da ChatGPT.
- Diagnosi successiva: verificare servizio health localhost sulla porta 8765, processo `cloudflared` e stato dell'attività pianificata, senza reinstallare o ripetere l'aggiornamento. Soltanto dopo la riconnessione provare `screen_capture` → `ui_mouse_screen` su Desktop1.

---

## Stato operativo 0.8.8 — 22 novembre 2026 (rilevamento post-distribuzione)

- Sorgente integrato in `main` con pull request #2, merge `10175433558d48f35ef15674be6962c5134f3e29`; CI sul commit candidato `573298d4f9d8a2071f3ef2ec99391b9dcfc20b82` conclusa con successo. Nuovo tool `ui_mouse_screen`: coordinate JPEG della cattura `screen_capture`, `capture_id` monouso 45 s, conversione automatica e accesso taskbar/anteprime con desktop interattivo verificato. Dettagli in [IMAGE-SPACE-MOUSE-0.8.8-20261122.md](IMAGE-SPACE-MOUSE-0.8.8-20261122.md).
- Verifica non invasiva su Desktop2 0.8.8: `screen_capture(max_width=640)` ha restituito `capture_id=2424b01981d9d0ee94f552619658fa84`, scadenza 45 s, dimensioni JPEG 640×391, schermo 1715×1047, scale_x=0.37317784256559766 e scale_y=0.3734479465138491. **Nessun clic o spostamento mouse è stato eseguito su Desktop2**; questo è solo un test dell'emissione metadata.
- Desktop2 (AUTECHVPC2): richiesta `jarvis-d2-update-088-20261122-image-mouse-01` **completed** (16:57:05 UTC), `cleanup_verified=true`, SHA256 `5dc6e3e4ebd0d404570918a6dc1ac945ec6e0ed7b02579db20fbf1b4fe8524c5`, backup `D:\\Jarvis\\Backups\\self-update-f22431e48e12483ca37b95c22f0bf91e`; `machine_info.version=0.8.8`, nuovo PID 1704 e capacità `ui_mouse_screen` presente. **Installazione verificata**, non equivale a collaudo mouse.
- Desktop1 (DESKTOP-1ESSB0K): richiesta separata `jarvis-d1-update-088-20261122-image-mouse-01` accettata e osservata in fase `promoting` alle 16:57:10 UTC. Dopo il passaggio, il connettore diretto ha restituito `The tool failed internally` a `machine_info`, `agent_status`, `agent_update_status`, `window_list`. **Non attribuire fase completed, versione live 0.8.8 o risultato del collaudo**, finché una nuova lettura non lo conferma. Non ripetere lo stesso update_id; esaminare prima la ricevuta e la situazione dell'avvio su Desktop1.
- Prossimo controllo: ripristinare il collegamento a Desktop1 preservando la procedura di avvio gestita, verificare `agent_update_status` e versione live, quindi test screenshot → `ui_mouse_screen` su bersaglio innocuo nel desktop, un'azione alla volta su ordine dell'utente. L'installer ZIP e i componenti Android coordinati sono lavoro separato, non testati qui.

---

## Sviluppo Windows MCP 0.8.8 — screenshot → mouse (22/11/2026)

- **Ramo**: `feature/desktop-image-mouse-088-20261122`, PR #2, partito da `main`. La 0.8.8 è nuova versione binaria dell'agente Windows, non una nuova distribuzione Android.
- **Funzione**: nuovo `ui_mouse_screen` con coordinate immagine JPEG, `capture_id` unico e monouso valido 45 s, trasformazione automatica verso pixel reali inclusi origine e display secondari; supporta taskbar/anteprime, mantenendo i controlli sul desktop interattivo e ricevute anti-replay. Il vecchio `ui_mouse` rimane compatibile.
- **Convalida sviluppatore**: su Desktop1 nella worktree Git temporanea, `go test -count=1 ./...` **PASS**, `go vet ./...` **PASS**, `go build` Windows **PASS**, parsing PowerShell **PASS** (log locale `D:\Jarvis\Projects\JarvisMouseQA\_verify088_results3.log`). Non confondere questa build di test con l'installazione del nuovo agente.
- **Distribuzione**: resta da eseguire e attestare con `agent_update_status` e `machine_info` su Desktop1 e Desktop2. Al momento dell'elaborazione di questa voce entrambe le macchine erano ancora alla **0.8.7**.
- **Installatore**: la versione storica del pacchetto installer (1.3.1 con manifest 0.7.1) non è stato automaticamente portato a 0.8.8 e non va presentato come ZIP 0.8.8 pronto. Packaging e allineamento successivo da collaudare separatamente.
- **Documentazione tecnica**: [IMAGE-SPACE-MOUSE-0.8.8-20261122.md](IMAGE-SPACE-MOUSE-0.8.8-20261122.md).

---

## Stato operativo verificato: Desktop1 e Desktop2 aggiornati alla 0.8.7 (22/11/2026)

**Desktop1**: installazione 0.8.7 eseguita e verificata con `agent_update` request_id `jarvis-d1-update-087-20261122-155814`, sorgente `d8936a750c9c8513342b5a699941486105838faf`. Esito `completed` alle 16:00:44 UTC, `cleanup_verified=true`, versione live `0.8.7`, PID `147264`, SHA256 installato `4364e65959c2955a0094fec5a2c737bfc05ac4fe4ae8bf0da865bba15cb12fd4`. Backup `D:\Jarvis\Backups\self-update-5dd8dbf312f348ea9e88b70c69728efd`. Un tentativo successivo di interrogare il connettore ha restituito errore interno; non annulla la ricevuta precedente.

**Desktop2**: partenza confermata da `0.8.4` con agente amministratore inattivo. Il vecchio aggiornamento alla `0.8.5` (`jarvis-source-update-d2-085-20261119-01`) risultava da oltre 48 ore in `building`; runner lock libero e nessun processo corrispondente. Solo il riferimento `active.json` del vecchio tentativo e stato **archiviato**, senza cancellare la ricevuta del precedente aggiornamento ne reinstallare la 0.8.5. Nuova richiesta separata `jarvis-d2-update-087-20261122-170630` dal commit `d8936a750c9c8513342b5a699941486105838faf`: `completed` alle 16:08:11 UTC, `cleanup_verified=true`, `machine_info.version=0.8.7`, PID `5476`, SHA256 `072277d7ef9f04481f344b469d14accb83f8030ff62db90d3f2666316fa7c789`. Backup `D:\Jarvis\Backups\self-update-83cea8fcba0f4cc48b490cb66ed80d76`. Dopo il riavvio `agent_status.state=idle`; rimossi i quattro file diagnostici temporanei dalla root progetti di Desktop2.

**Release complessiva:** il codice coordinato 0.8.7 e in `main`; le due installazioni Windows sono verificate. Rimangono **non completati** il pacchetto ZIP installer 0.8.7, gli APK Android firmati/da distribuire, il collaudo hardware ESP32 e l'esposizione del tool `ui_mouse` nel connettore ChatGPT con prova reale di spostamento e clic. Non presentare questi controlli come gia superati. Le annotazioni inferiori, con stato precedente dei desktop, sono storico e non il quadro corrente.

---

## Rettifica operativa del 22 novembre 2026: Desktop1

**Desktop1 e stato gia modificato durante questa conversazione:** il deploy 0.8.6 e stato eseguito e verificato tramite `agent_update` (`jarvis-mouse-086-desktop1-20261122-152330`), con versione live 0.8.6, backup, hash installato e pulizia verificati. Solo **in seguito** il connettore Desktop1 ha iniziato a restituire `The tool failed internally`. Non confondere questo errore successivo con il mancato intervento iniziale.

Il successivo ordine di aggiornare Desktop1 alla **0.8.7** e stato ricevuto ma **non eseguito**: il comando `agent_update` 0.8.7 non e stato inviato, perche la connessione diretta risultava indisponibile. Non esistono quindi ricevute che dimostrino una 0.8.7 installata.

L'utente ha chiesto espressamente di **non toccare Desktop2**. Nel tentativo precedente sono state compiute interrogazioni di sola lettura su Desktop2 per cercare una via di comunicazione verso Desktop1; non sono state inviate modifiche, accoppiamenti o aggiornamenti a Desktop2. Dopo la correzione dell'utente, **non contattare ulteriormente Desktop2, neppure come gateway, per questa attivita**.

Il ripristino del collegamento diretto Desktop1 resta necessario per procedere all'aggiornamento alla 0.8.7 e per controllare i comandi mouse. Nessun aggiornamento di un computer deve essere inferito da test Git o compilazioni.

> **Stato del repository 22/11/2026:** il sorgente coordinato **0.8.7** e stato integrato in `main` con PR #1 e merge commit `f0408ac4a2d1826bc74b71319462cc638fc33a28`. Test Go tripli, vet, build Linux/Windows e parser PowerShell superati (GitHub Actions `45040466751`). **Non e ancora una release binaria installata, firmata o distribuita.** L'ultima versione Windows effettivamente verificata su Desktop1 resta 0.8.6. Vedere [UNIFIED-RELEASE-0.8.7-20261122.md](UNIFIED-RELEASE-0.8.7-20261122.md). Non confondere la numerazione sorgente con APK firmati o un installer ZIP collaudato.

> **Aggiornamento verificato 22 novembre 2026:** Desktop1 / DESKTOP-1ESSB0K e stato aggiornato alla versione **0.8.6** dal commit sorgente `f8bec6ee37c0c4bc15315815f3768e8a7d3ba3b2`, tramite `agent_update` con request_id `jarvis-mouse-086-desktop1-20261122-152330`. Riscontro: `agent_update_status.phase=completed`, `cleanup_verified=true`, `machine_info.version=0.8.6`, nuovo PID 149104, installato SHA256 `a538b7e70e20976ad79b8221437afb461e9e8bb8ce4f5fc26a6aa096d15838fc`. Sorgenti/test Go/vet/build e parser PowerShell superati prima del deploy. **Non e stata verificata la disponibilita di `ui_mouse` nell'elenco strumenti ChatGPT**: il nuovo supporto a `move` resta non collaudabile da questa chat finche il connettore non viene aggiornato/ricollegato. La documentazione sottostante mantiene lo storico osservato il 19/11 per confronto; non usarlo come ultima versione corrente. Installer 0.8.6: script allineati nel ramo, pacchetto ZIP finale non ancora verificato/pubblicato.

## Regola di ripresa architetturale (19 novembre 2026)

Prima di introdurre un modulo MCP o predisporre un nuovo installer, leggere [HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md](HANDOFF-GENERIC-AGENT-PROCEDURES-20261119.md) e [PROCEDURES.md](PROCEDURES.md). Il core di Jarvis deve restare **indipendente dalle applicazioni**. Per azioni occasionali usare gli strumenti generici e batch/PowerShell in `<Jarvis>\Procedures`, non un modulo dedicato (es. Proxmox). Aggiornare agente/installer solo per primitive universali, con test e verifica distinta. I commit di sviluppo per input mouse/UI web non costituiscono una nuova release: ultima installata/verificata Desktop1 0.8.4.

# Jarvis Windows MCP Agent — release corrente e ripresa obbligatoria

> Stato osservato il **19 novembre 2026**. Questo documento e il punto di ingresso
> per rispondere a "qual e l'ultima versione Jarvis?", riprendere lo sviluppo
> dell'agente Windows o consegnare l'installer. **Non** e una dichiarazione che
> questa rimarra sempre la release piu recente: prima di ogni risposta verificare
> Git remoto e, quando possibile, gli agenti in esecuzione.

## Identificazione del progetto

- Progetto: **Jarvis Windows MCP Agent / JarvisRuntime**, distinto da **JarvisWake Android**.
- Repository: `Andry-iu/Jarvis`.
- **Ramo di lavoro piu avanzato verificato per Windows MCP:** `feature/unified-agent-source-update-20261114`.
- **Ultima release pubblicata e collaudata verificata al 19/11/2026:** **0.8.4**.
- Versione **unificata** di sorgenti, agente e installer: `0.8.4` (non usare
  separatamente un numero `Setup 1.x` o `2.0.0` come numero dell'agente).
- Sorgente della release: commit esatto
  `ff2038d4b250c9d3e7e6590c2409ad55e5081380`.
- `main` non comprende necessariamente le evoluzioni dell'agente Windows:
  **non consultare solo `main` e non dedurre l'ultima versione dal README di JarvisWake**.
- Il ramo `feature/agent-gateway-20261114` contiene lo storico fino a `0.8.2`;
  e **superato** dal ramo di lavoro sopra. La `0.8.3` ha introdotto il codice
  da digitare nel pairing; la `0.8.4` ha introdotto `agent_update` da commit Git
  anche attraverso gateway. Non consegnare `0.8.2` come ultima release solo
  perche e la versione trovata su un ramo precedente.

## Artefatto installabile verificato

- Nome: `Jarvis-Agent-Setup-0.8.4.zip` per Windows x64.
- SHA-256 del **pacchetto ZIP**:
  `d10fa8e6d5e9d174660f0ca77e2d2ed1873ed9e8c4b305d66cb26c2494c2ccbe`.
- Dimensione: `7472565` byte.
- Posizione della copia privata nella Library: `/JarvisRuntime/Jarvis-Agent-Setup-0.8.4.zip`.
- Manifest verificato: [CHECKSUMS-0.8.4.json](CHECKSUMS-0.8.4.json).
- Verbale: [VALIDATION-0.8.4-20261114.md](VALIDATION-0.8.4-20261114.md).
- Nuovo PC: estrarre ZIP ed eseguire `INSTALLA.cmd`; scelta LAN via altro
  gateway o accesso diretto Cloudflare. Ogni agente e anche gateway.
- Agente gia installato: **non** usare `INSTALLA.cmd` sulla cartella esistente;
  utilizzare `AGGIORNA.cmd` quando compatibile oppure
  [agent_update](UPDATE-FROM-GIT-0.8.4.md) dal commit esatto, con backup,
  ricevute, rollback rehearsal e verifica finale.
- In Git solo sorgenti/test/manifest/documentazione: **mai** binari, ZIP,
  credenziali, private key, token Cloudflare, copie di `secrets.env` o
  configurazioni locali segrete.

## Stato delle installazioni (separare storico e lettura live)

| Dispositivo | Ultima versione verificata | Origine della verifica |
| --- | --- | --- |
| Desktop1 / DESKTOP-1ESSB0K | **0.8.4** | **Lettura live** `machine_info` e `agent_status` da ChatGPT il 19/11/2026 alle 09:56 UTC: `version=0.8.4`, `elevated=true`, `state=idle` |
| Desktop2 / AUTECHVPC2 | **0.8.4** | Deploy e audit finali verificati il 14/11/2026; **non** una lettura live del 19/11 |
| Target LAN gia associato tramite gateway | **0.8.4** | Deploy e letture autenticate via gateway verificati il 14/11/2026; **non** una lettura live del 19/11 |

Per Desktop1, `machine_id=1b927a030fa3b7dfa15c40cfccb4ff60`.
Prima di operare sui PC, rileggere `machine_info` per confermare identita,
versione, privilegi e capacita. Un errore del connettore non dimostra che l'agente
sia offline, disinstallato o retrocesso. PID e stati operativi sono transitori.

## Procedura **obbligatoria** a ogni nuova chat / ripresa

1. **Riconoscere il prodotto:** distinguere Jarvis Windows MCP Agent, JarvisWake
   Android, ESP32 e altri progetti con nome Jarvis. Non incrociare le versioni.
2. Leggere questo file, [STATUS.md](STATUS.md),
   [PROJECT-RECORD.md](PROJECT-RECORD.md) e
   [UPDATE-FROM-GIT-0.8.4.md](UPDATE-FROM-GIT-0.8.4.md) per l'ultimo stato noto.
3. Su GitHub **verificare i rami Windows MCP esistenti**, in particolare quello
   indicato qui e ogni ramo piu recente: confrontare commit, README, AGENTS,
   `CHECKSUMS-<versione>.json` e validazioni. Non presumere che `main` sia
   il ramo corretto o che un ramo datato sia il piu recente. Distinguere un
   commit documentale da una nuova release del binario.
4. Se l'utente chiede la **versione installata**, usare `machine_info` o
   `agent_status` sul PC specifico; se la macchina e raggiungibile via
   gateway, usare `device_list`/`device_call` preservando l'identita del
   destinatario. Non sostituire osservazioni live con documentazione storica.
5. Se chiede uno **ZIP**, preferire l'artefatto originale verificato e confrontare
   nome/dimensione/SHA-256 con il manifest. Non creare uno ZIP di soli sorgenti
   spacciandolo per installer eseguibile. Non inventare link di download.
6. Riportare sempre separatamente: ultima release verificata nel repository,
   ramo/commit, ultima versione osservata su ciascun dispositivo e data,
   eventuale installazione/connessione non verificabile.
7. Dopo **ogni nuova release** aggiornare **nello stesso lavoro** questo
   documento, `STATUS.md`, `windows-mcp-agent/README.md`,
   `windows-mcp-agent/AGENTS.md`, continuita e registro del progetto:
   versione, branch, commit di sorgente, manifest SHA-256, artefatto,
   dispositivi realmente verificati e limiti, prima di dichiarare completato.
   Eseguire commit e push su ramo autorizzato, verificarne il risultato.
   Nessun merge su `main` o force-push implicito.
8. La documentazione e una **memoria persistente consultabile**, non una
   garanzia che ChatGPT rilegga Git automaticamente: nelle nuove chat
   richiamare il progetto e verificare nuovamente le fonti correnti.

## Funzionalita recuperabili della 0.8.4

Gateway universali su TLS LAN (porta predefinita 8766), collegamenti su
piu nodi con `via`, pairing con codice inserito sul PC target, permessi e
identita proprie, `agent_update` + `agent_update_status` da commit Git su
qualsiasi destinatario, alternativa con pacchetto verificato quando Git non
e raggiungibile, e un unico updater canonico con controlli di backup/rollback.

L'accesso Cloudflare per l'agente di ingresso, l'avvio all'accesso Windows,
la UI con sessione sbloccata e le autorizzazioni del destinatario sono
condizioni distinte. Non affermare collaudi di reboot fisico non effettuati.

## Origine della registrazione

Richiesta esplicita del 19/11/2026: conservare su Git impostazioni, note e
appunti per **ritrovare sempre l'ultima versione su cui stavamo lavorando**.
La correzione nasce da una risposta che riportava erroneamente `0.8.2` come
ultima dopo aver letto soltanto `feature/agent-gateway-20261114`, mentre
`feature/unified-agent-source-update-20261114` documentava gia `0.8.4`.
