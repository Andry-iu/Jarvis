# Procedura collaudata: build, diagnosi e consegna JarvisWake
Aggiornata il 7 novembre 2026 (Europe/Rome), dopo il rilascio .121/143 e la conferma utente del download. Questa è la procedura corrente; gli stati precedenti restano storici.

## Prima scelta: riusare il percorso riuscito
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
