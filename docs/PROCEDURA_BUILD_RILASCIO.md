# Procedura collaudata: build, diagnosi e consegna JarvisWake
Aggiornata l’8 novembre 2026 (Europe/Rome), dopo il rilascio .121/143 e il ripristino del runner per la build .122. Questa è la procedura corrente; gli stati precedenti restano storici.

## Scheda eseguibile per una nuova sessione — partire da qui

I nomi qui sotto sono le operazioni del plugin, non comandi shell. Cercarle nel catalogo strumenti della sessione per nome esatto; i prefissi di namespace/connector possono cambiare. Non riusare un vecchio percorso opaco di connessione. Repository fisso: `Andry-iu/Jarvis`; branch `main`. Prima stabilire se serve consegnare una build esistente o crearne una nuova.

### A. Consegnare di nuovo la .121 già compilata: nessuna build
1. `jarviswake_cloud_job({"job_id":"c6f79b80-2039-48d4-a762-e2cdab9e5307"})`. Riscontro atteso: `state:"succeeded"`, `artifact.version:"0.5.121"`, `version_code:143`, `release_commit:"b881b921a65966df70fc2a7dffebb3d2186fecf6"`.
2. `jarviswake_cloud_artifact({"job_id":"c6f79b80-2039-48d4-a762-e2cdab9e5307"})`. Consegnare l'URL effettivamente restituito; checksum e dimensione sono nella tabella sotto. Un errore `cloud_artifact_expired` è scadenza del binario, non build fallita: verificare prima l'artefatto conservato nel cloud o una consegna già esistente, senza nuova compilazione automatica.
3. Il link richiede accesso al Site con lo stesso account ChatGPT del job. Se il client non finalizza il download, proporre menu ⋮ → Apri nel browser/Chrome e Chrome → Download. Sono istruzioni di prova; in questo caso l'utente non ha identificato il passaggio decisivo. Non affermare che Drive sia la soluzione: qui non è stato caricato nulla su Drive.

### B. Pubblicare una correzione dalla chat senza shell cloud: percorso GitHub effettivamente riuscito
Questa sequenza è stata usata al posto dei molti `container.exec` abortiti. Non richiede un clone, il reset di un checkout o credenziali di firma nella chat.
1. `github.fetch({"url":"https://api.github.com/repos/Andry-iu/Jarvis/git/ref/heads/main"})`. Leggere `object.sha`: chiamarlo HEAD_ATTESO.
2. `github.fetch({"url":"https://api.github.com/repos/Andry-iu/Jarvis/git/commits/HEAD_ATTESO"})`, sostituendo HEAD_ATTESO con lo SHA ricevuto. Leggere `tree.sha`: TREE_BASE.
3. `github.fetch_file({"repository_full_name":"Andry-iu/Jarvis","path":"PERCORSO","ref":"HEAD_ATTESO"})` per ogni file da leggere/modificare; usare valori veri al posto dei segnaposto. Conservare il contenuto integrale in memoria/file, senza ricostruirlo manualmente dai messaggi. Comporre solo le differenze richieste e i documenti pertinenti.
4. `github.create_tree({"repository_full_name":"Andry-iu/Jarvis","base_tree_sha":"TREE_BASE","tree_elements":[{"path":"PERCORSO","mode":"100644","type":"blob","content":"CONTENUTO_COMPLETO_AGGIORNATO"}]})`. Includere tutti i file modificati; per rimuovere SOLO la richiesta diagnostica usare l'entry `{"path":"cloud-runner/diagnostic-request.json","mode":"100644","type":"blob","sha":null}`. Omettere tutti i file invariati: il base tree li conserva.
5. `github.create_commit({"repository_full_name":"Andry-iu/Jarvis","parent_sha":"HEAD_ATTESO","tree_sha":"SHA_TREE_RESTITUITO","message":"DESCRIZIONE_CONCRETA"})`.
6. `github.update_ref({"repository_full_name":"Andry-iu/Jarvis","branch_name":"main","expected_sha":"HEAD_ATTESO","sha":"SHA_COMMIT_RESTITUITO","force":false})`. Solo `success:true` prova lo spostamento del branch. Rileggere il ref del punto1 e confrontarlo col commit. Se la lease fallisce: rileggere head/file e ricomporre sul nuovo tree senza perdere commit concorrenti; mai force/reset.
7. Se il tool risponde abortito o errore, controllare il ref prima di ripetere mutazioni. Un commit creato non prova che main lo contenga. Fermare tentativi equivalenti su un tool indisponibile.

### C. Una nuova build su sorgenti pubblicati
1. Scoprire `jarviswake_cloud_status`; chiamarlo con `{}`. Se `active_job` è già il lavoro pertinente, riprendere quello; non accodarne un duplicato. `runner_online:false` non significa toolchain da rifare.
2. Ottenere lo SHA da pubblicazione B. Generare un UUIDv4 UNA volta (per esempio Python `str(uuid.uuid4())`) e conservarlo insieme a operation/revision. `jarviswake_cloud_start({"operation":"build_signed","revision":"SHA_SORGENTE_PUBBLICATO","request_id":"UUID_NUOVO_CONSERVATO"})`. Non incollare un vecchio UUID di esempio per un lavoro nuovo.
3. Conservare il `job_id` restituito. `jarviswake_cloud_job({"job_id":"JOB_RESTITUITO"})` finché l'esito è terminale, con controlli distanziati. Fasi osservate nel job riuscito: queued → checks → android → recording → upload → complete/succeeded. La firma è provata da artifact/certificato e successo, non dall'assenza di una fase osservata nel polling.
4. In `recording` erano già presenti695test/0failure/0skipped, lint0errori/55warning e metadata APK, ma `release_commit:null`: non consegnare ancora. In `upload` comparve release_commit b881b921..., ancora state=running: attendere. Solo state=succeeded/error=null → chiamare artifact.
5. Se `failed/android_failed`, seguire D; se awaiting_runner/outcome_uncertain, conservare job/claim e diagnosticare l'esecuzione esistente. Non reimpostare il job. Il bootstrap è solo nel cloud Jarvis reale e serve soltanto se necessario; la chat senza shell usa il ponte.

### D. Recuperare la causa di android_failed senza accesso diretto al log
1. Cercare prima un report già pubblicato: `github.fetch_file({"repository_full_name":"Andry-iu/Jarvis","path":"releases/cloud-diagnostics/JOB_FALLITO.json"})`. Se pertinente e completo, riusarlo. Un report con array vuoti non prova assenza di errore: può mancare il log/report locale.
2. Se non disponibile, leggere `cloud-runner/collect_diagnostic.py`, `app/build.gradle.kts` e i relativi test da main. Il raccoglitore ESISTE: non ricrearlo o sostituire il runner per ogni errore.
3. Creare via B il file `cloud-runner/diagnostic-request.json` con questo schema esatto, già usato:
```json
{"job_ids":["ec528b06-3d5c-4365-844a-c0ab5b54811a"]}
```
Per un incidente nuovo sostituire il valore con l'UUIDv4 del job realmente fallito; ammessi1–2 job. Nessun campo percorso, comando o testo libero. Nel caso originale il commit a44061a8556bc239eb8126e1335a420a2294a11e conteneva la richiesta.
4. Il task `cloudBuildDiagnostics` è dipendenza preBuild; oggi `onlyIf { rootProject.file("cloud-runner/diagnostic-request.json").exists() }`. Il raccoglitore si attiva solo se il sorgente è sotto `/workspace/.jarvis-cloud/builds/<job>` e `/workspace/Jarvis` esiste. Eseguirlo nello scratch della chat non raccoglie i log cloud.
5. Avviare un job diagnostico sullo SHA appena pubblicato mediante C. Nel caso reale:
```json
{"operation":"build_signed","request_id":"f405a463-cbb6-411d-bbb7-93da2675c1c2","revision":"a44061a8556bc239eb8126e1335a420a2294a11e"}
```
È un'operazione già svolta, NON da rieseguire: UUID storico. Il preBuild lesse `/workspace/.jarvis-cloud/cloud-runner/logs/ec528b06-3d5c-4365-844a-c0ab5b54811a/build.log` e i report del worktree corrispondente. Pubblicò `releases/cloud-diagnostics/ec528b06-3d5c-4365-844a-c0ab5b54811a.json` nel commit41a55fcf13cdd7e7c9105d720d1b3e8cde533a0b, mentre il job diagnostico compilava ancora il difetto e terminò android_failed.
6. Leggere il JSON e poi le righe sorgenti indicate. Qui compiler aveva due record `unresolved_reference`, path `app/src/test/java/it/jarviswake/BridgeNavigationTest.kt`, line38/82,column69; failed_tasks=`[":app:compileDebugUnitTestKotlin"]`, tests=`[]`. Il report omette deliberatamente i messaggi/valori privati; la causa concreta si ricava dal sorgente e dal confronto con FavoriteOperationsTest/PhoneContactExportTest: `setIsKeyguardLocked` errato, `setKeyguardLocked` corretto.
7. PRIMA di pubblicare la correzione rileggere main: è avanzato per il report automatico. Nel caso reale il nuovo parent era41a55fc..., tree5730d75ea427f335586394e6a48c415b17e4d1bf. Correggere entrambe le chiamate, rimuovere diagnostic-request.json e conservare il guard onlyIf. Pubblicazione via B produsse cf4d12e1fd55f24134976eb40776261448f1be84.
8. Nuovo job corretto (storico, non riavviare):
```json
{"operation":"build_signed","request_id":"c6f79b80-2039-48d4-a762-e2cdab9e5307","revision":"cf4d12e1fd55f24134976eb40776261448f1be84"}
```
Seguire C fino a succeeded e A per artifact. Questo completò la build; non sono stati necessari reset, installazione toolchain alternativa o una nuova chiave.

### E. Backend dello stesso rilascio: azioni effettivamente svolte
Il Site esistente è `appgprj_6ae75ae6cdd08191ab126c1d392a603a`, URL `https://jarviswake-link-andrea.autech-sm267-2695.chatgpt.site`. Dopo verifica dell'APK è stata ripubblicata la versione .121 già salvata, senza ricreare Site/plugin né cambiare audience/segreti:
```json
{"project_id":"appgprj_6ae75ae6cdd08191ab126c1d392a603a","version_id":"appgprj_6ae75ae6cdd08191ab126c1d392a603a~appgver_2ef62a90c09c81918143711680e61bad"}
```
Tool: `sites.deploy_private_site_version`; risultato iniziale pending, deployment_id appgdep_6aef50758b488191b4d6bbd827c846bb. Quindi `sites.get_deployment_status({"project_id":"appgprj_6ae75ae6cdd08191ab126c1d392a603a","deployment_id":"appgdep_6aef50758b488191b4d6bbd827c846bb"})` → succeeded/has_mcp=true/env_set_revision4. Infine `jarviswake_guide({"topic":"maps","query":""})` → guide_version0.5.121/version_code143.
Questi ID sono prova storica, non default per il prossimo rilascio. Per un'altra versione leggere get_site e le versioni salvate, scegliere quella corrispondente al sorgente verificato e rispettare l'audience corrente; mai ridistribuire automaticamente questa vecchia versione.

### F. Mappa problemi → risposte provate o limiti
| Sintomo osservato | Riscontro discriminante | Passo concreto |
| --- | --- | --- |
| container.exec ripetutamente aborted | Nessun output/esito di Git o modifica file | Passare alla sequenza GitHub B; non eseguire reset/restore alla cieca |
| android_failed senza log nel tool | Report tecnico D, poi sorgente alle righe indicate | Correggere il simbolo identificato; non dedurre errore test/lint o strumenti mancanti |
| main avanza durante diagnosi | Ref passa da a44061a... a41a55fc... | Nuovo base tree/parent dal ref aggiornato prima della correzione |
| recording/upload ma APK metadata presenti | state ancora running; release_commit può essere null | Attendere succeeded; non dire pronto |
| Download al100% ma file non disponibile, secondo utente | Utente poi conferma riuscita; nessuna causa isolata | Tentativo browser esterno/Download; nessuna ricompilazione, nessun fix server inventato |
| Recupero HTTP assistente=401/accesso richiesto | Sessione non autenticata del client assistente | Non equiparare a errore APK né cercare bypass |
| Browser remoto dopo login bloccato dalla policy sul protocollo | Azione espressamente rifiutata | Fermare quel percorso; non usare superfici alternative per aggirarlo |
| Alternativa Drive annunciata ma APK non recuperato | Nessun upload/file_id/readback | Dire non completata; nessun link Drive o trasferimento riuscito dichiarato |


### G. Runner offline: riattivazione riuscita dalla sessione cloud esistente (8 novembre 2026)
Il ponte cloud accoda richieste ma non risveglia l’ambiente. Se non sono esposti strumenti diretti di gestione cloud, verificare anche la UI di Codex prima di dichiarare il ripristino impossibile. In questo caso il browser autenticato esponeva la sessione già usata per compilare la .120:
- Titolo: «Ripristina il collegamento del runner Jarvis. Verifica sull…».
- URL osservato: https://chatgpt.com/local/01a1b65d-703a-74dd-96c6-f7a8d4e6804b .
- Percorso riuscito: ChatGPT → Codex → Recenti → sessione esistente → campo «Richiedi modifiche di follow-up». Nessun nuovo task, ambiente o job di build è stato creato.

Nel follow-up specificare il ripristino nel cloud Jarvis originale, il job già accodato e lo SHA completo fissato; vietare duplicazione/cancellazione della richiesta, rotazione dei segreti e ricostruzione della toolchain altrove. La sessione cloud disponeva di Environment status e shell su /workspace/Jarvis; la chat chiamante non li esponeva. Prima verificare identità e percorsi reali, stato processi, journal e prontezza: aprire una chat da solo non prova che il runner sia online.

Azioni osservate nel cloud: fetch di origin/main, aggiornamento fast-forward del checkout pulito, lettura delle procedure, bootstrap --check, poi avvio separato di `bash /workspace/Jarvis/cloud-runner/bootstrap.sh --repair --start`, stdin chiuso e log in /workspace/.jarvis-cloud/cloud-runner/runner.log. Preservati journal e configurazione privata; confronto SHA-256 della configurazione prima/dopo confermava assenza di modifiche. Non stampare il contenuto della configurazione né i suoi segreti.

Risultato provato dal ponte: runner_online=true, heartbeat aggiornato e active_job=6c800b3f-05c5-49a4-ae4e-7b8e39d9cd9f; lo stesso job passò queued → running/checks → android, resolved_commit=d70a27714fa216ca62dbf9f8eff71ea13424e816. Claim originale della presa in carico: 546f328f-acc2-4a0d-97e6-2360a8e70457. La diagnosi della sessione rilevò processo runner assente, strumenti e configurazione integri; non è accertato perché il processo fosse terminato. Nessun nuovo UUID e nessuna reinstallazione degli strumenti.

Heartbeat/claim provano il ripristino e l’avvio, non l’APK riuscito: seguire C fino all’esito terminale. Questo avvio non salva uno snapshot e non garantisce always-on. Per future riprese usare il job realmente pertinente, non reinviare quello storico; se la sessione non espone il cloud autentico o presenta un blocco concreto, conservarne lo stato e descrivere il limite osservato senza inventare accessi.


## Principi e dettagli complementari
Leggere AGENTS.md, la continuità corrente e questo documento prima di cercare nuove soluzioni. Sviluppo e commit possono avvenire dalla chat disponibile; compilazione Android/JVM, firma e rilascio usano esclusivamente il cloud Jarvis già configurato. Non ricostruire la toolchain altrove, non usare Actions o Desktop per sostituirlo. Il consenso permanente comprende diagnosi, correzioni, verifiche, commit/push e rilascio nello stesso lavoro approvato; capacità tecnica e autorizzazione sono distinte.

1. Leggere main remoto e documenti pertinenti. Preservare modifiche concorrenti; nessun reset/restore automatico per forzare una baseline. Se non esiste checkout utile, i tool GitHub consentono lettura e pubblicazione dei soli file pertinenti.
2. Riutilizzare JarvisWake Link: cloud_status, cloud_start, cloud_job, cloud_artifact. Il telefono non deve fare da esecutore della compilazione.
3. Se il runner è già raggiungibile usare il ponte. Non cercare accesso shell al cloud senza necessità. Se occorre riparare il runner e si dispone davvero del cloud Jarvis: `bash /workspace/Jarvis/cloud-runner/bootstrap.sh --repair --start`; diagnosi sola lettura `--check`. I percorsi sono del cloud Jarvis, non di qualsiasi scratch.
4. Riutilizzare /workspace/.jarvis-cloud, firma permanente e configurazione privata. Readiness prima della claim; heartbeat non garantisce strumenti/cache completi o servizio sempre acceso. Gradle non va forzato offline con cache incompleta. Usare il Go della cache Jarvis, non /usr/bin/go. Dettagli, binding e snapshot in CLOUD_BUILDS.md; non chiedere password già configurate e non stampare valori.
5. Pubblicare sorgenti/test/documentazione preparati; pin di uno SHA completo appartenente a main. cloud_start: operation=build_signed e UUIDv4 conservato. Per verifiche senza APK usare verify. Il runner risolve main una volta se non è stato indicato uno SHA.
6. Seguire lo stesso job con cloud_job a intervalli ragionevoli e aggiornamenti significativi. Timeout della chiamata, queued, running, awaiting_runner o outcome_uncertain non autorizzano un nuovo UUID né provano fallimento. Mai rubare una claim. Gli esiti terminali restano immutabili.
7. Non dichiarare APK pronto in checks/android/signing/recording/upload. Attendere succeeded, verificare versione/conteggi/certificato/hash/verbale e ottenere cloud_artifact. Il runner registra il verbale su main prima della disponibilità del file. Una correzione sostanziale verificata può richiedere un nuovo job sul nuovo SHA, conservando il vecchio fallimento: non è un replay del medesimo job.
8. Se il backend richiede la nuova APK, conservare la versione compatibile finché il nuovo APK è verificato; pubblicare poi la versione salvata dello STESSO Site privato, controllare esito terminale e guida/versione reali. Preservare pairing, R2, audience e segreti. Nessuna rotazione o duplicazione implicita.

## Diagnosi del fallimento Android: percorso riusabile
android_failed è una categoria, non una causa. Prima di altre build leggere il log del job nel cloud oppure usare il raccoglitore già versionato:
- `cloud-runner/collect_diagnostic.py`, abilitato solo da `cloud-runner/diagnostic-request.json`; relativo hook preBuild condizionato all'esistenza della richiesta.
- Leggere schema, allowlist e test correnti prima di compilare la richiesta: non inventare parametri. Esegue nel medesimo cloud e pubblica solo metadati tecnici limitati in releases/cloud-diagnostics/<job>.json con push fast-forward.
- Non modificare un runner attivo, non pubblicare log integrali, messaggi liberi, dati del telefono o credenziali. Un job diagnostico usa lo SHA che contiene il raccoglitore e può ancora fallire compilando il vecchio difetto: il report recuperato è un risultato distinto.
- Recuperata la causa: correzione minima, confrontata con suite/firme già funzionanti; rimuovere la richiesta temporanea e lasciare il raccoglitore inattivo senza richiesta. Verificare il nuovo SHA nel cloud prima della consegna.

Caso accertato .121: il report del job ec528b06-3d5c-4365-844a-c0ab5b54811a individuava due unresolved_reference in BridgeNavigationTest.kt, righe38/82, task :app:compileDebugUnitTestKotlin. I nuovi test chiamavano setIsKeyguardLocked invece di setKeyguardLocked, già usato dalle suite del progetto. Correzione cf4d12e1fd55f24134976eb40776261448f1be84. Il compilatore si era fermato prima dei test: non parlare di test Android eseguiti e falliti per quel tentativo.
Il job diagnostico f405a463-cbb6-411d-bbb7-93da2675c1c2 sul vecchio SHA a44061a8556bc239eb8126e1335a420a2294a11e terminò failed/android_failed. Nessun esito è stato riscritto.

## Prova riuscita di riferimento
| Campo | Risultato osservato |
| --- | --- |
| Job corretto | c6f79b80-2039-48d4-a762-e2cdab9e5307, succeeded |
| Sorgente | cf4d12e1fd55f24134976eb40776261448f1be84 |
| Verbale su main | b881b921a65966df70fc2a7dffebb3d2186fecf6 |
| Report | releases/cloud-jobs/c6f79b80-2039-48d4-a762-e2cdab9e5307.json |
| APK | 0.5.121 / 143, 90052351 byte |
| SHA-256 APK | 1b39d0c8e039a20200ac6d3d64b5a5a2b51ae73f5dc18b51b6937323b1b73ca4 |
| Certificato permanente | a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a |
| Android | 695 test, 0 failure, 0 skipped |
| Lint | 0 errori, 55 warning |
| Backend | deploy appgdep_6aef50758b488191b4d6bbd827c846bb, succeeded/has_mcp/env4 |
| Guida riletta | maps, 0.5.121 / 143 |
| Download utente | confermato funzionante il 7 novembre 2026 |
| Galaxy funzionale | installazione e navigazione .121 non ancora confermate |

Il report non espone ogni conteggio interpretato: non inventarli. Nessuna API Google a pagamento o prova fisica è dedotta dalla build. APK e materiali di firma restano fuori Git.

## Download privato e problemi di consegna
cloud_artifact restituisce il link APK autenticato dello stesso account Site, valido7giorni; conservare job_id e rigenerare il riferimento dal tool quando serve, senza ricompilare. Un link nuovamente richiesto non rinnova la scadenza dell'APK. Non copiare il link temporaneo come procedura permanente, non rendere pubblico il Site e non aggirare l'autenticazione.

In questa chat il primo download appariva fermo al termine. È stata suggerita l'apertura in Chrome e la consultazione di Download, con lo stesso account ChatGPT. L'utente ha poi confermato «ora funziona». Non ha precisato quale passaggio abbia risolto: la causa esatta e il ruolo di Chrome NON sono dimostrati. Non sono stati applicati fix al server del download o all'APK per questo problema.

Il tentativo ausiliario dell'assistente non ha prodotto una copia Drive: ricerca Drive senza .121; recupero HTTP senza sessione=401/accesso richiesto; browser remoto ha incontrato il login e poi un blocco di policy relativo al protocollo. Questo non prova un difetto del telefono o del server. Fermarsi al blocco, non aggirarlo; i risultati di quel browser non descrivono la sessione del telefono.

Per un futuro blocco: distinguere file assente/scaduto, accesso richiesto e mancata finalizzazione client; usare i riscontri reali prima di cambiare server, ricompilare o proporre nuovi canali. Proporre il browser esterno come tentativo, non soluzione garantita. Se il file funziona, fermare la diagnosi. Drive resta alternativa solo se il vero APK è disponibile: verificare 90052351byte/hash per QUESTO artefatto, account/destinazione già autorizzati, upload riuscito e metadati riletti. Non annunciare «lo carico» o «link Drive pronto» prima di poter concretizzare il trasferimento; nessun nuovo consenso alla stessa destinazione già autorizzata.

## Errori operativi da non ripetere
- Decine di comandi shell abortiti non sono avanzamento: nessun fetch/reset/restore/edit/test da essi deducibile. Un fallimento di esecuzione richiede un riscontro minimo, poi passaggio al canale disponibile, non una sequenza di tentativi equivalenti.
- In questa chat il canale GitHub è riuscito: fetch dei riferimenti/file → create_tree basato sul tree remoto verificato → create_commit col parent corrente → update_ref(force=false, expected_sha=HEAD osservato) → rilettura main. Se main avanza, integrare sul nuovo parent e preservare tutti i file non modificati. Nessun force-push.
- Non riscrivere a mano interi documenti storici da copie in chat: preservare i byte ricevuti e fare modifiche mirate. La documentazione corrente deve riassumere lo stato una volta e rimandare al caso dettagliato; gli stati superati vanno marcati come storici.
- Rileggere il risultato effettivo di ogni tool prima di dichiarare correzione applicata, commit pubblicato, firma, upload, deploy o salvataggio. «Pronta» prematuro e «snapshot salvato» non verificato sono errori già documentati.
- Salvare procedure generali nelle regole, dettagli in questo repository e un rimando negli appunti Cloud Jarvis. Conservare identità/revisioni; nessuna promessa di memoria universale senza rilettura delle fonti.
