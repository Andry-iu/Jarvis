# Validazione 0.7.4 — 5 novembre 2026

## Stato finale

Implementato, testato, pubblicato, installato e collaudato via MCP su Desktop1 e
Desktop2. Regola permanente a commit/push registrata dopo esplicita conferma
utente in questa chat. Sorgente runtime remoto 6a04718e1eb5330179c6a3e621749e1b63fe22e7,
albero identico al locale c662fae collaudato; mapping e prove sotto. Le sezioni
sui blocchi e tentativi intermedi descrivono la cronologia, non lo stato finale.

Implementato: contatori Codex separati per progetto/incarico/categoria, conservando
i dialoghi; contratto TOKEN-METER-0.7.4.md. Branch feature/development-token-meter-20261105,
base 7a2947945137d39b49392c9fe05157696956d78d.

Test locali Linux Go 1.27.0: suite, vet e race PASS. Il test preesistente
TestQueueIndependentProjectsCanDispatchTogether presentava una corsa nel cleanup:
terminava mentre i processi git scrivevano ancora i job. Il test ora attende
l'esito dei due job prima della rimozione dei temporanei, mantenendo l'asserzione
del dispatch contemporaneo. Nessuna modifica al runtime dei comandi.
Test nuovi e processo RPC simulato senza modelli/API. Compilazione incrociata
Windows amd64 PASS. Test nativi e MCP isolati completati come sotto;
pubblicazione/installazione poi completate come riportato nello stato finale.

Preflight remoto: entrambi agenti 0.7.3, coda vuota e sana, sorgenti canonici HEAD
7a2947945137d39b49392c9fe05157696956d78d. Desktop1 conserva update.go.bak non tracciato;
Desktop2 conserva Historical-0.7.0/. Nessuna pulizia o sovrascrittura. Build candidate
in checkout isolati; percorsi installati e configurazioni preservati.

Hash precedenti: Desktop1 a0a5ee1b95a8222b769b320165cf92a614ce58be7c0c004c37696c4c430d091b;
Desktop2 c77b43a851de409ceda18b712259a72fedcc07e2c00d80bec0f0c580437d66ca.
Nessuna inferenza reale, chiave, credito o modifica delle guardie economiche.
Non certificati reboot fisico, ciclo reale 24 ore o misurazione di questa chat/Voice.

## Correzione del metodo di compilazione

L'utente ha richiamato il riuso degli appunti e delle procedure gia riuscite.
Il primo tentativo Desktop2 usava Go 1.27.3 e non seguiva il metodo del verbale
0.7.3. E stato un errore operativo. Il messaggio "package ... not in std" non
dimostrava SDK incompleto: le directory standard e i file Go risultavano presenti.
Un ulteriore tentativo con Go 1.27.3 ufficiale e cache isolata ha prodotto
compile.exe exit status 0xc0000005 nel pacchetto net. Causa precisa non isolata.

Ripreso il profilo gia riuscito del 3 novembre: Go ufficiale 1.27.0 verificato,
SDK/cache isolati, GOMAXPROCS=1, GOGC=25, test/vet/build con -p 1. Gestione stderr
ripresa da build-runtime-059.ps1; profilo ora raccolto in scripts/build-runtime.ps1
e richiamato nelle istruzioni iniziali AGENTS.md. Nessun SDK permanente modificato.
SHA-256 ZIP ufficiale Windows Go 1.27.0:
f0c0a0d33ba94f4d2c5dbc887334ce678b21813504ddb3aafcb06e60a5a667c4.

Desktop1: test nativi completi 15.679 s, vet e build PASS. SHA-256 candidato
58d19dbec644f996debda0b8b8aff7833e7f04db5bf9ab031952a03d2b4c23bf,
12353024 byte, Go 1.27.0 windows/amd64, sorgente runtime
c662fae247d5edf5e9c99248d00ddc358823e391. MCP candidato isolato PASS: versione,
capability, report diretto senza inferenza/null, alias con filtro dialogue e
conservazione del file conversazione di prova. Non e un'installazione.

## Pubblicazione

Commit locale runtime c662fae247d5edf5e9c99248d00ddc358823e391. Pubblicazione
respinta due volte dalla revisione automatica per assenza di approvazione utente
esplicita alla condivisione verso GitHub ritenuta valida dal revisore. Le regole
del repository e il contesto storico riportano autorizzazione permanente a
commit/push; get_repo conferma i permessi admin/push e il remoto coincide con
Andry-iu/Jarvis, ma il secondo rifiuto non accetta tale prova storica. Nessun
aggiramento tramite altri canali. Serve conferma esplicita per quel push.
Ramo remoto non ancora creato; nessuna installazione/backup/rehearsal di rilascio
0.7.4 dichiarati. Agenti operativi preservati alla 0.7.3.

## Esito del riuso del metodo documentato — Desktop2

PASS: suite Go completa 22.515 s, vet e build usando il profilo gia riuscito
0.7.3. Sorgente runtime c662fae247d5edf5e9c99248d00ddc358823e391, Go 1.27.0
windows/amd64; SHA-256 b3099b8a3f6dd8e9a435296242366afcd44f3c9faa9418a1da4f34f4422bd9d6,
12404736 byte. Log privati token-meter-native-073-profile.log e
token-meter-documented-build.log. MCP isolato PASS: versione/capability,
report diretto con Codex disabilitato e null in assenza di misure, alias runtime
con filtro dialogue, file di conversazione preservato. Il SDK/cache del tentativo
documentato vengono rimossi nel finally; nessun SDK permanente cambiato.

I diversi hash dei due candidati non dimostrano sorgenti diversi: ambiente,
percorso di build e impostazioni sono registrati. Nessun sorgente Go modificato
dopo queste prove. Commit successivo solo istruzioni, script e verbali/checksum.
L'agente operativo 0.7.3 non e stato sostituito; dati di prova esclusivamente in
istanze isolate con Codex disabilitato. Nessuna prova di inferenza reale,
nessun token reale di sviluppo attribuito da questi collaudi simulati.

## Chiusura del collaudo prima della pubblicazione

Entrambi: processi isolati terminati, porta 8767 libera, directory Probe/SDK/cache
dei collaudi rimosse e candidate hash/dimensioni ricontrollati PASS. Rimossi anche
SDK/cache temporanei dei tentativi falliti con Go 1.27.3, senza toccare il SDK
installato o lo stato dell'agente. Sorgente privato e candidati di staging
conservati per la ripresa. Log token-meter-final-preflight.log su ciascun PC.
machine_info riletta: agenti operativi ancora 0.7.3, identita preservate.
ls-remote non restituisce il nuovo branch: sorgente non pubblicato.
Nessuna sostituzione di binario, riavvio operativo o inferenza Codex eseguiti.

## Autorizzazione esplicita e ripresa — 5 novembre 2026

Dopo la richiesta per il repository pubblico Andry-iu/Jarvis e il branch corrente,
l'utente: «Autorizzo sempre, registrare le regole.» Autorizzazione permanente
registrata in AGENTS.md, OPERATING-RULES.md e RELEASE_POLICY.md. Il precedente
blocco e documentato come fatto storico; push e distribuzione ripresi dopo
questa conferma esplicita, senza aggirare i rifiuti precedenti.

## Pubblicazione riuscita dopo autorizzazione esplicita

Il Git CLI della sessione non dispone di credenziali per la scrittura (errore
could not read Username); pubblicazione mediante collegamento GitHub gia attivo,
come per la 0.7.3. Nessuna nuova autenticazione, configurazione di credenziali o
forzatura. Per ciascun commit creato via API, albero confrontato con l'originale
locale: identico. Differenze SHA riguardano metadata di commit.

| Locale collaudato/registrato | Remoto | Albero identico |
|---|---|---|
| c662fae247d5edf5e9c99248d00ddc358823e391 | 6a04718e1eb5330179c6a3e621749e1b63fe22e7 | b7477a0177c6c6354504771dda3dce185faf6d31 |
| 7b1fa1837aad3f1a0490243d008d9cd07904cb37 | 93774301bff1d994bc4baf9c35cafbd64c0c32a3 | 83c97f38503e5cef1660abe14e3281af5d26a64e |
| 4d84b4b977d6c1423db6bb21d3f6d2deace39fba | 7fe596ceef208482e635972cc5d7e62731bb0d2a | 8dedccb458a62947e6b06840f3ed7d7da5b6962d |
| a6fa7adc8dccf9882d3d5dd30010dc18f84ce202 | a6baa21e9f6e0e43731479de292a8302a21d47ab | bd5a4498ffac1926f347df94ff7417749acf6b07 |

Branch remoto feature/development-token-meter-20261105 creato e riletto via API;
fetch in lettura riuscito. Copia cloud allineata con reset soft soltanto dopo
confronto alberi, backup branch backup/token-meter-local-a6fa7ad e indice pulito.
Nessun reset hard, clean, force push o merge su main.

Entrambi i checkout canonici Windows aggiornati al branch pubblicato: sorgenti
Go/go.mod identici al commit runtime remoto e preservati update.go.bak e storico
non tracciato. Candidati copiati e verificati in Releases/0.7.4. Updater canonico
identico dopo normalizzazione LF, SHA-256
b958224f6fdb629ee791e057034e7aa3c35f352aeb596a6b4cfbf10804ec3366.
Gli aggiornamenti usano il commit sorgente pubblico 6a04718; i candidati mantengono
nei metadata VCS il locale c662fae dal medesimo albero collaudato.

## Installazione e collaudo operativo — completati

Stesso updater canonico scripts/update-agent.ps1, invocato da batch CMD separati
tramite update_prepare/update_execute, sequenziale Desktop1 poi Desktop2.
SourceCommit pubblico 6a04718; nessun replay o task di aggiornamento alternativo.

| Esito | Desktop1 | Desktop2 |
|---|---|---|
| Versione machine_info da ChatGPT | 0.7.4 | 0.7.4 |
| Machine ID preservato | 1b927a030fa3b7dfa15c40cfccb4ff60 | d41455adf8721c205183224d3c69a1a5 |
| PID dopo riavvio, istantanea | 452104 | 12796 |
| Token processo | elevated=true | elevated=true |
| Binario attivo | D:\Jarvis\jarvis-mcp.exe | D:\Jarvis\Agent\jarvis-mcp.exe |
| Backup in D:\Jarvis\Backups | self-update-61cc70c9835446cc8a6232acc06ee3c8 | self-update-617b5474484d4fdbad4014b40959563a |
| Backup e rollback rehearsal | PASS | PASS |
| Hash/configurazione/identita/manutenzione | PASS | PASS |
| Nuova capability codex_token_usage | presente | presente |
| Report development e dialogue via task_start/runtime | PASS | PASS |
| Contatore invocazioni codex_token_usage | 2 riuscite, nessun doppione | 2 riuscite, nessun doppione |
| tool_usage.started_at preservato | 2026-11-03T17:53:58.4575006Z | 2026-11-03T18:12:52.4840496Z |

Entrambi self-update.log terminano PASS common_batch_update_identity_config_maintenance.
I report reali risultano records=[] e observed_tokens=null, con scope esplicito,
historical_backfill=false e categoria corretta. Nessuna inferenza di collaudo o
iniezione di consumi simulati nello stato operativo. Test di contatori positivi,
duplicati e categorie svolti nelle suite isolate/native sopra.

La lettura finale codex_status su Desktop1 segnala eseguibile Codex configurato
non disponibile. Su Desktop2 quota corrente residua 90%, crediti/saldo null
(sconosciuti), inference_allowed=false; guardie invariate. Nessuna conversione
in token e nessun confronto con vecchie letture. Dati account privati esclusi.
Il catalogo diretto client non risulta aggiornato; compatibilita runtime verificata.
Nessuna prova di reboot fisico, durata reale di 24 ore o inferenza reale Codex.
