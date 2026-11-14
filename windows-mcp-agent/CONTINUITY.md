# Agente 0.8.4 — installato e verificato sui tre PC

La chat invia lo stesso agent_update a ogni destinatario, diretto o tramite piu
gateway: download del commit Git esatto, test/build locali e updater canonico.
Pacchetto conservato se il destinatario non raggiunge Git. Desktop1, Desktop2 e
il target LAN rispondono 0.8.4 con identita conservate e amministratore. Audit
finali PASS su tutti: ricevute/hash/versione, ambiente, manutenzione e pulizia.
Appunti dei tre PC salvati e riletti; cataloghi client Desktop1/Desktop2 aggiornati
e osservati. Indisponibilita esterna Desktop1 superata, causa non accertata.
Nessun nuovo deploy alla ripresa; nessun reboot fisico certificato.
[Specifica](../docs/mcp-agent/UPDATE-FROM-GIT-0.8.4.md), [validazione](../docs/mcp-agent/VALIDATION-0.8.4-20261114.md).

## Stato precedente

# Stato corrente — aggiornamento LAN automatico verificato, 14 novembre 2026

Desktop1, Desktop2 e il PC LAN gia associato rispondono 0.8.3 con identita
conservate e privilegi elevati. Aggiornamento del target 0.8.2→0.8.3 attraverso
Desktop1 completato con trasferimento automatico, unico updater, backup,
rehearsal rollback, ricevuta/hash/versione e pulizia verificati. Nessun passaggio
manuale o nuovo pairing. [Verbale](../docs/mcp-agent/REMOTE-UPDATE-0.8.3-20261114.md).
Il catalogo server di entrambi i desktop e corretto; il testo del client che
nega l'inoltro update e obsoleto, refresh della cache non certificato.
[Catalogo operativo](../docs/mcp-agent/MCP-CATALOG-0.8.3.md). Non ripetere il deploy: l'esito e provato.
Desktop1 osservato sotto Agent; le descrizioni storiche della root non indicano
piu il percorso attivo. Nessun reboot fisico o nuovo consenso umano certificato.

## Stato documentato prima di questo intervento

# Stato corrente 0.8.3 — 14 novembre 2026

Inserimento obbligatorio del codice ricevuto in chat e conto alla rovescia 1:50
implementati, testati e pubblicati. Desktop1 e Desktop2 aggiornati e riconnessi
alla 0.8.3; identita/configurazioni e collegamenti salvati conservati.
La finestra non mostra il valore atteso, il launcher Go verifica anche l'output;
vuoto/errato/ALLOW legacy/scaduto non approvano. Timer 110 secondi, tre errori.
Suite Linux/race/vet e Windows nativa/vet/build PASS. Su entrambi Windows anche
installazioni isolate e upgrade 0.8.2->0.8.3 con backup/rollback/cleanup PASS.
Runtime pubblicato 50edce2c868664fb9ad2c040746284642a0e555c; istruzioni installer
aggiornate in 2a7ae7613f1ea3603976cd2eec6f314c882cd082, Go/GUI invariati.
Branch feature/pairing-code-required-20261114. Versione unica 0.8.3.
Pacchetto Jarvis-Agent-Setup-0.8.3.zip, Library libfile_2d76bfedae888191a05dad1676ccbb65.
Leggere PAIRING-0.8.3.md, VALIDATION-0.8.3-20261114.md e CHECKSUMS-0.8.3.json.
I PC gia associati non richiedono nuovo consenso. Il destinatario deve essere
aggiornato per la nuova finestra: target 0.8.2 mantiene il confronto a vista.
Nessun inserimento umano corretto o nuovo abbinamento fisico certificato in questa
release; prova GUI dummy senza trust, matching corretto verificato in isolamento.

## Stato precedente

# Stato corrente — release0.8.2, 14 novembre2026

Desktop1 e Desktop2 aggiornati, riconnessi e verificati0.8.2. Difetto0.8.1 della
finestra nascosta riprodotto e risolto: helper senza console ma senza SW_HIDE.
Suite Windows con test nativo visibilita, installer, aggiornamento0.8.1->0.8.2,
backup/rollback e controllo finestra sugli agenti installati PASS su entrambi.
Sorgente20a862a, branch feature/agent-gateway-20261114. Manifest/checksum e prove
in docs/mcp-agent/{CHECKSUMS-0.8.2.json,VALIDATION-0.8.2-20261114.md,FIX-0.8.2.md}.
Jarvis-Agent-Setup-0.8.2.zip, Library libfile_95fc00b0ebac81918eca7d9b917eecae.
Primo collegamento LAN reale0.8.2 completato: consenso locale, scambio privato,
identita/TLS e letture autenticate tramite gateway verificati. Inventario, percorso
e identita dei dispositivi nei rispettivi appunti privati; nessun nuovo consenso
richiesto per operare su un collegamento gia associato.
Nessuna approvazione UI automatizzata, nuovo tunnel o altro progetto ripreso.

## Storico0.8.1 — superato dalla0.8.2 per la visibilita della finestra

# Stato storico — release0.8.1, 14 novembre2026

Desktop1 e Desktop2 aggiornati e verificati via MCP alla0.8.1. Backup, rollback
rehearsal, test Windows e prove di installazione/aggiornamento locale PASS.
Sorgente runtime aa2f2cb; pacchetto/updater definitivo8ed7743, ramo
feature/agent-gateway-20261114. Stessa release per sorgenti, agente e installer.
Jarvis-Agent-Setup-0.8.1.zip: Library libfile_03e14735ba00819189378f110c7b970e.
Procedura PAIRING-0.8.1.md, prove VALIDATION-0.8.1-20261114.md e checksum JSON
sotto docs/mcp-agent dalla root del repository.
SoftPlcTest1,192.168.1.101, identita e803423d08ab396b98262b3cf55df658:0.8.0 non
associato. Prossimo passo dell'utente: AGGIORNA.cmd nella cartella estratta,
indicare la cartella Jarvis esistente; poi chiedere collegamento tramiteDesktop1.
Begin -> confronto codice/conferma locale -> status -> complete CAS, nessuna
copia di file. Non automatizzare la conferma tramite UI remota.
Avvio all'accesso Windows preservato, non servizio headless; nessun reboot fisico
o clic umano di conferma certificato. Nessun altro progetto ripreso.

## Storico precedente

# 0.8.0 — installata e verificata su Desktop1 e Desktop2

14 novembre 2026. Runtime definitivo 07760a3eb212bb63f9919e2702a46f1f0d290338,
branch feature/agent-gateway-20261114. Entrambi aggiornati con updater canonico,
backup, rehearsal rollback e verifica MCP di identita/versione/hash/privilegi.
Canale LAN pronto, configurazioni iniziali vuote; nessun nuovo PC ancora associato.

Agente universale senza ruoli: controllo e inoltro completo su piu gateway,
aggiornamenti persistenti, nomi liberi univoci e dismissione, discovery e pairing
privato. Installer 2.0.0 disponibile: nome, locale oppure Cloudflare, avvio automatico
all'accesso Windows. Suite Go/race/vet Linux, test/vet/build Windows e collaudo
installer reale isolato su entrambi PASS. Nessun reboot fisico certificato.

Candidati finali e ricevute D:\Jarvis\Releases\0.8.0-r2; sorgenti operativi
D:\Jarvis\MCP-Source\Gateway-0.8.0. Checkout precedenti preservati. Non ripetere
la distribuzione gia riuscita. Le sezioni successive sono storico.

 [Specifica](../docs/mcp-agent/GATEWAY-0.8.0.md), [istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.8.0.md), [validazione](../docs/mcp-agent/VALIDATION-0.8.0-20261114.md), [checksum](../docs/mcp-agent/CHECKSUMS-0.8.0.json).

# Agente 0.7.4 — installato e collaudato su entrambi i desktop

5 novembre 2026. Contatori token separati per progetto/incarico/categoria,
conservando i dialoghi. Implementazione, test/vet/race Linux, test/vet/build
Windows, MCP isolato, pubblicazione e installazione completati. machine_info e
letture codex_token_usage live tramite task_start/runtime PASS su entrambi.
Regola permanente a commit/push degli interventi approvati registrata in
AGENTS.md, OPERATING-RULES.md e RELEASE_POLICY.md su esplicita conferma utente.

Sorgente runtime pubblico 6a04718e1eb5330179c6a3e621749e1b63fe22e7, branch
feature/development-token-meter-20261105. Albero b7477a0177c6c6354504771dda3dce185faf6d31
identico al locale c662fae collaudato; differenze SHA nei metadata, nessuna modifica
Go dopo i test. CHECKSUMS-0.7.4.json e VALIDATION-0.7.4-20261105.md riportano gli esiti.
Updater canonico: backup verificati, rollback rehearsal, sostituzione/riavvio,
identita/configurazioni/hash/manutenzione PASS su entrambi. Avvio diretto Desktop1,
supervisore Desktop2 preservati; nessuna migrazione di percorsi o SDK permanente.
Desktop2 compilato con il metodo gia riuscito: Go 1.27.0 ufficiale, SDK/cache
isolati, GOMAXPROCS=1, GOGC=25 e -p 1. Usare scripts/build-runtime.ps1.

codex_turn: usage_category=development per sviluppo; dialogue/other/unclassified
restano separati. Categoria omessa: unclassified. codex_token_usage legge il
progetto, default development; usare task_start/runtime se il catalogo e vecchio.
I report live sono vuoti con observed_tokens=null: nessun consumo reale inventato.
Misura solo Codex inoltrato dall'agente, non questa chat/Voice, trascrizioni, TTS o
altri client. Nessun recupero retroattivo o conversione quota/token. Nessuna
inferenza reale di collaudo; guardie economiche invariate. Reboot fisico e ciclo
24 ore non certificati. Specifica: docs/mcp-agent/TOKEN-METER-0.7.4.md.

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

# Continuita Windows MCP — 0.6.0 installata e collaudata, 31 ottobre 2026

Leggere AGENTS.md di questa cartella e docs/mcp-agent/{STATUS,QUEUE-0.6.0,VALIDATION-0.6.0-20261031,RELEASE_POLICY}.md. Agente Windows distinto da Android. Branch reconcile-windows-mcp-agent-20261031. Ripresa verificata in una nuova chat con Jarvis-Desktop1.

Coda persistente per progetto, riordino/cancellazione in attesa e letture concorrenti operativi. Percorsi sovrapposti esclusi, progetti indipendenti paralleli (massimo quattro). command_start/codex_turn accodano: leggere queue_list per job_id/turn_id. Cancellazione in attesa distinta da interruzione attiva; nessun replay automatico. Dipendenze esplicite e pausa dopo errore/riavvio. Per catalogo precedente usare task_start operation=runtime e request completa.

Verificati test Windows/vet/build, collaudo isolato con riavvio senza replay, backup/rollback e installazione. Poi dalla chat: riordino C prima di B, B cancellata senza avvio, letture durante A, progetto indipendente concluso durante A, turno Codex accodato completato e final_usage presente. Sorgente binario a63fb7b36dce3e543c28194ae80f8519524949fc; installazione da c01400edcd185b7d6b6bde114b8064931f742195 dopo correzione del solo script di collaudo. SHA-256 68D7D4DB1B63576B81EC67E7DCAB137763F7BE38A6B841BA5A8B46F4B5A1DEFE. Backup D:\Jarvis\Backups\mcp-0.6.0-20261031.

Quota finale storica 17:56:53 UTC: 87%, saldo servizio "0" noto, reset 4 novembre 13:39:22 UTC. Le letture precedenti con saldo null restano sconosciute; non sostituirle con zero. Sempre codex_status per il dato attuale, senza inferenza. Guardia iniziale e lettura finale conservate; nessun acquisto/ricarica/API. Prova costi associata account/PC per saldo null non prorogata, scadenza 7 novembre 15:55 UTC. Automazione quota giornaliera esistente verificata abilitata, ID 6ae61868fe2081919636cd881282ad27; nessun duplicato, esecuzione futura non verificata.

Restano da verificare Voice, altri PC fisici e reboot del PC; device_list mostra solo locale. Il percorso di compatibilita funziona in questa chat, ma non dimostra il refresh dei cinque strumenti diretti queue_* nel client. MSBuild/CMake/cl non nel PATH. NestingOpt sospeso. Checkout sparse: includere docs/mcp-agent oltre a windows-mcp-agent. Conservare Staging, update.go.bak, stash e backup precedenti.

## Storico dei rilasci precedenti

# Continuita Windows MCP — 0.5.9 installata e collaudata, 31 ottobre 2026

Autorizzato dall'utente: lettura iniziale per ammissibilita (gia esistente), lettura finale automatica senza confronti, consultazione diretta su richiesta e breve dato attuale nel riepilogo di ogni elaborazione Codex. Il comando diretto e codex_status; non crearne duplicati. La risposta precedente che lo dichiarava assente era errata.

Codice candidato: riepilogo usage in codex_status; lettura asincrona unica dopo turn/completed, evento persistente jarvis/usage, final_usage e usage_pending in codex_events. Non attendere RPC nel reader. Non nascondere dati disponibili quando inference_allowed=false. Saldo null rimane sconosciuto. Quote riferite all'account, non al singolo progetto. Nessun acquisto, fallback API o modifica delle prove costi. Guardie 0.5.8 conservate.

Stato corrente: test Windows/vet/build, backup/rollback e installazione superati; turno reale con lettura finale jarvis/usage verificato. Sorgente binario 924f23aa6409f77342d964cc225991ecb4296002; SHA-256 CB7B9B3E0C3A9CA9133E738E0412CD518AF6BEA7E79FEE2C65830776635540C5. Ultima lettura 87% disponibile, saldo crediti API sconosciuto, reset 4 novembre 2026 13:39 UTC. Non trattare questi numeri come stato corrente senza nuova lettura. Seguire docs/mcp-agent/USAGE-0.5.9.md e RELEASE_POLICY.md, preservare backup/stato/identita. Branch reconcile-windows-mcp-agent-20261031.

Modifica successiva approvata in Voice: coda per progetto, consultazione/cancellazione/riordino e letture in parallelo, specifica in docs/mcp-agent/QUEUE-SPEC.md. Non inclusa nella 0.5.9; implementazione successiva in corso. Rischio attuale: sessioni Codex diverse possono toccare lo stesso progetto; il runner comandi limita un job attivo per PC e ogni sessione Codex limita un turno attivo.

# Continuita Windows MCP — 0.5.8, 31 ottobre 2026

Stato corrente: [STATUS](../docs/mcp-agent/STATUS.md), [collaudo Codex 0.5.8](../docs/mcp-agent/CODEX-0.5.8.md). Branch reconcile-windows-mcp-agent-20261031. Binario operativo D:\Jarvis\jarvis-mcp.exe, versione 0.5.8, elevazione verificata. Source commit 6b5530e8347502f35511aabdf696c58877836cee; SHA-256 04F369F365816BC076CBCE3F89589BAC63FE628385FFF8B96316E2E79115C1BE. Modifiche documentali successive non cambiano il codice compilato.

L'utente ha aggiornato il plugin: 38 strumenti diretti verificati. Ha fornito la schermata con 88% quota residua, 0 crediti e ricarica automatica disattivata. Config/codex-cost-proof.json conserva solo il riscontro locale e un hash dell'account, mai token/email in Git. Per il saldo API nullo la 0.5.8 richiede riscontro valido per account/PC, zero crediti e ricarica disattivata; API hasCredits/unlimited, piano, limiti e quota vengono ricontrollati prima dei turni. Riscontro corrente valido fino al 7 novembre 2026 15:55 UTC; scaduto/ignoto/cambio account la guardia si chiude. Non acquistare crediti, usare API key o aggirare la guardia.

Collaudi reali Codex: risposta e storico, steer, interruzione confermata, ripresa dopo riavvio, modifica file verificata tramite MCP. Dopo installazione Codex ha risposto JARVIS_INSTALL_OK dal plugin. Backup 0.5.7 e 16 file di stato verificati in D:\Jarvis\Backups\mcp-0.5.8-20261031; rollback isolato superato. Identita stabile e job .NET precedente conservati. Conservare tutti i backup, update.go.bak e stash backup update.go locale 20261031.

Riprendere leggendo machine_info e project_catalog, poi job_list e codex_threads del progetto esatto. Non duplicare operazioni con esito incerto. Altri PC fisici, nuova chat/Voice e avvio dopo reboot non collaudati. Clone NestingOpt ancora sospeso. Lo storico 0.5.7 seguente descrive il precedente blocco, risolto nella 0.5.8 dopo la nuova prova fornita dall'utente.

# Continuita Windows MCP — 0.5.7, 31 ottobre 2026

Leggere [STATUS](../docs/mcp-agent/STATUS.md), [verbale](../docs/mcp-agent/VALIDATION-0.5.7-20261031.md), [contratto](../docs/mcp-agent/RUNTIME-0.5.7.md), [istruzioni client](../docs/mcp-agent/CLIENT-INSTRUCTIONS-0.5.7.md) e [regola release](../docs/mcp-agent/RELEASE_POLICY.md). Questo agente Windows e distinto dall'app Android.

Branch reconcile-windows-mcp-agent-20261031. Agente operativo 0.5.7, eseguibile D:\Jarvis\jarvis-mcp.exe, source commit a10c9bdb1a87362bf904fbc24b697489f9b7080f, SHA-256 4A5A56B735879B256EA15A4F3B5C99568BC996D67BC5D4663CCAFFA8488CDD5F. Fonte D:\Jarvis\MCP-Source\windows-mcp-agent; root autorizzata D:\Jarvis\MCP-Source; stato D:\Jarvis\State\runtime-v1. Configurazione e credenziali locali in D:\Jarvis\Config, mai in chat/Git.

Verificati installazione, amministratore, riconnessione, file/cartelle, ripristino, immagini MCP dirette mostrate in chat, test/build Go, build .NET 9, persistenzа e routing fra due istanze isolate. Backup 0.5.6 e rollback provati in D:\Jarvis\Backups\mcp-0.5.7-20261031. Preservare tutti i backup precedenti, update.go.bak e stash backup update.go locale 20261031. Non ripetere collaudi superati senza una ragione tecnica e non modificare l'agente funzionante senza backup e rollback verificati.

Per riprendere, leggere agent_status e machine_info; non fissare l'identita da questa pagina. Nel client con vecchio catalogo usare task_start con operation=runtime e request contenente operation e machine_id; project_catalog mostra i progetti, job_list/request_status lo storico. Non inviare comandi normali attraverso update_prepare/update_execute quando il runner controllato e sufficiente.

Punto aperto: Codex App Server funziona per account/quote e lista sessioni, ma balance=null blocca l'inferenza. UI osservata: 0 crediti e 88% quota inclusa residua; ricarica automatica non verificata. Il tentativo di accettare saldo nullo/testare un turno e stato respinto dalla revisione automatica; codice ripristinato rigorosamente chiuso su saldo sconosciuto. Nessuna inferenza avviata. Attendere verifica/autorizzazione appropriata, senza aggiramenti, acquisti o chiavi API.

Servono inoltre aggiornamento catalogo client (38 strumenti sul server, dieci precedenti esposti in questa chat), verifica nuova chat/Voice, e configurazione esplicita di altri PC reali. Non dichiarare questi punti completati. Avvio dopo reboot non verificato. MSBuild/CMake/cl non nel PATH; .NET SDK 9.0.317 disponibile e compilazione riuscita. Clone NestingOpt ancora sospeso.
