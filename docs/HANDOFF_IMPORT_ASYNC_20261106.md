# Importazione rubrica asincrona — sorgenti 0.5.116 / 138

6 novembre 2026, Europe/Rome. Stato storico della preparazione: modifica implementata nei sorgenti; test interpretati eseguiti, build Android e firma NON eseguite. Questo documento è il punto corrente di ripresa per il cloud Jarvis. La precedente .115 resta l'ultima versione compilata documentata.

## Completamento nel cloud Jarvis — 6 novembre 2026

Richiesta esplicita di scaricare e compilare anche la .116. Main b08d0ec scaricato e integrato fast-forward nel checkout Jarvis permanente, senza modifiche locali preesistenti. Verificati identità/operatività ambiente, cache/toolchain e firma. I test mirati BridgeLinkWorkerIntegrationTest e PhoneContactExportTest SDK34/36 sono passati; verifica finale completa643test/66suite,0failure/error/skipped, lint55warning/0errori/fatal, build assemble riuscita. Passate5suite Node DOM/audio,50test Worker e13test Python rubrica, guida --check33argomenti/13579parole. Nessuna correzione ai sorgenti ricevuti è risultata necessaria.

Backend pubblicato e verificato sullo STESSO Site privato: source ea3fcfbd5382db33e922e353a3b74bce43fd843d, deployment appgdep_6aee29d62910819188f41a04d32a9240 succeeded/has_mcp=true/env2; pairing, audience e segreti conservati. Tool guida reale riletto .116/138. Nessun nuovo Site/plugin/backend, nessuna API Google a pagamento o importazione reale usata per verificare il rilascio.

Unico APK Telefono .116/138 firmato permanentemente:90.003.199byte, SHA256 28ad4654772d3842818b7065425f83c00200020a06ff3629b6fe8fb04b8ee96a. Certificato atteso, v2/v3, versione/ABI/CRC ZIP e zipalign/ELF16KB verificati; temporanei di firma rimossi, checksum in releases/0.5.116/SHA256SUMS e binario/report fuori Git. Ambiente e dettagli completi in VERIFICATION.md. Nessun APK Watch o agente Windows modificato.

Rimangono ESCLUSIVAMENTE da collaudare sul telefono le condizioni fisiche elencate sotto; questa compilazione non prova una rubrica realmente importata. Conservati anche i residui .115, l’appunto regole non confermato e gli altri sviluppi della roadmap. Le sezioni seguenti documentano la preparazione precedente, seguita dal completamento cloud qui sopra.

## Richiesta e riscontro

L'utente ha autorizzato la correzione, commit e aggiornamento completo della documentazione, quindi compilazione nel cloud Jarvis. Ha precisato che un'importazione attiva deve essere seguita fino alla conclusione, anche se lunga, senza interromperla per un timeout totale.

Sul telefono è stata ricevuta una fotografia .115/137 con servizio attivo e permesso READ_CONTACTS concesso. Le richieste di importazione non hanno prodotto una copia locale verificata. Il consenso export era già attivo secondo l'utente: non attribuire il problema a un consenso mancante. I log di produzione mostrano /device/respond con durate frequentemente fra 5 e 7 secondi, e alcuni rifiuti 409; il trasporto Android aveva readTimeout=4000. Il codice flushOutbox propagava i timeout prima di /device/poll: una risposta non confermata poteva quindi impedire nuovi comandi. Questi sono difetti verificabili nel codice coerenti con i log, non la prova che ogni timeout del Galaxy avesse la stessa unica causa. Nessun contatto o log privato è incluso nel commit.

## Comportamento implementato

- Dopo il claim, contacts_export_start avvia un solo lavoro su un thread dedicato. Poll, risposte di stato e altri comandi continuano. Il journal viene consumato prima del claim; morte processo o esito incerto non autorizzano una seconda esecuzione automatica.
- Il lavoro non usa la scadenza di consegna del comando come durata massima. Le query al provider non vengono cancellate dopo un tempo fisso; revoca, blocco del telefono, disabilitazione o invalidazione della generazione conservano la cancellazione. Non vengono aggirati permessi o schermata di blocco.
- Heartbeat ogni 5 secondi con sole fasi starting/contacts/phones/emails/verifying/freezing. running indica una conferma recente (entro 45 secondi), non completamento né avanzamento percentuale; awaiting_device indica assenza di una conferma recente, non arresto o autorizzazione a ripetere. Un provider in attesa resta un lavoro attivo: non viene inventata una percentuale.
- Il backend conserva i metadati del lavoro già preso in carico fino al risultato, anche oltre 25/120 secondi. I risultati terminali dell'export-start scadono 120 secondi dopo la risposta. Le regole di scadenza per chiamate, WhatsApp, note e altri comandi restano distinte. I payload non vengono resi pubblici e il pairing esistente resta invariato.
- Lo snapshot RAM ha 30 minuti di inattività, rinnovati a ogni pagina valida. La utility accetta la scadenza rinnovata senza allargare dimensioni, hash, schema, identità o verifica dei contatori. Non esiste un limite fisso di durata totale mentre il trasferimento progredisce; process death, revoca e inattività rimangono limiti espliciti.
- Ogni tentativo HTTP mantiene limiti finiti (connessione 4 secondi, lettura 15 secondi); il fallimento di un tentativo non annulla il lavoro. Un errore recuperabile nell'outbox conserva il risultato e cede il controllo al poll, senza ripetere l'azione. Connessioni concorrenti sono tutte invalidabili alla chiusura della generazione.
- Un nuovo export-start durante la raccolta riceve contacts_export_busy. Il risultato del lavoro lungo viene datato al completamento, così non è scartato solo perché la raccolta è durata più di due minuti.
- Versione predisposta .116/138 e guida unica aggiornata. Nessuna modifica a gesti cover, Voice, Watch, agenti Windows o credenziali.

## Verifiche eseguite qui

- 50 test Node del Worker superati: inclusi lavoro simulato di tre ore, heartbeat monotoni, awaiting_device/ripresa, nessun secondo dispatch, risultato terminale non sovrascritto e scadenza dei risultati calcolata dal completamento.
- 13 test Python della rubrica superati: stati non terminali con lo stesso UUID, riapertura del piano, trasferimento a blocchi oltre la scadenza iniziale grazie al rinnovo, validazione privacy e protezione dell'ultima copia completa.
- Generazione e --check della guida unica riusciti (33 argomenti); controllo sintattico JavaScript riuscito.
- Aggiunte regressioni Android per outbox in timeout senza starvation/replay, importazione lunga mentre il worker risponde allo stato, e rinnovo dello snapshot. Queste regressioni e l'intera suite Gradle NON sono state compilate o eseguite in questo ambiente.

## Passaggio obbligatorio nel cloud Jarvis

La procedura di compilazione/firma è già in AGENTS.md e docs/RECUPERO_FIRMA.md. Non chiedere nuovamente password e non trasferire la compilazione sui Desktop. Configurazione storicamente documentata: f68203bf-ad95-493f-b5d1-b05e44686cd2~cecfg_6adcda8d13e081918b4bd1e231e145b8. Checkout previsto /workspace/Jarvis e cache /workspace/.jarvis-cloud. Questi riferimenti non provano che l'ambiente sia collegato alla chat corrente: verificarne lo stato operativo prima di eseguire.

1. Aprire il cloud Jarvis già configurato, verificare ambiente e disponibilità dei segreti senza stamparli. Qui non erano disponibili strumenti di esecuzione/selezione dell'ambiente remoto e i due percorsi cloud non erano presenti; nessuna compilazione remota avviata.
2. Leggere AGENTS.md, questo documento, CONTINUITY.md e VERIFICATION.md. Aggiornare main con fast-forward, preservando eventuali modifiche concorrenti. Verificare che contenga il commit di questo handoff e che non ci siano sorgenti pendenti.
3. Nel medesimo cloud eseguire le suite mirate BridgeLinkWorkerIntegrationTest e PhoneContactExportTest su SDK34/36, poi la suite completa con la procedura già collaudata:

```bash
bash gradlew --no-daemon --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PjarvisUnsigned=true -Pkotlin.compiler.execution.strategy=in-process -PjarvisTestJava=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -PjarvisTestSdks=34,36
node --test phone-bridge/test/bridge.test.mjs
python3 -m unittest discover -s scripts/tests -p 'test_phonebook.py'
python3 scripts/sync-guide.py --check
```

4. Completare eventuali correzioni di compilazione/regressione nello stesso gruppo, senza allargare lo scope. Verificare uscita cover e recupero audio, servizio dedicato, mute temporaneo e modalità Google già consolidate. Le nuove prove Android usano solo provider e trasporto sintetici.
5. Aggiornare il backend sullo STESSO Site privato appgprj_6ae75ae6cdd08191ab126c1d392a603a secondo phone-bridge/README.md, preservando audience, segreti e pairing. Servono backend e APK compatibili per il nuovo progress asincrono. Questo commit non costituisce un deploy; la pubblicazione va eseguita e verificata nell'ambiente abilitato.
6. Firmare l'APK Telefono con il certificato permanente e la procedura esistente. Verificare .116/138, arm64, integrità ZIP, zipalign/ELF16KB e certificato SHA256 a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a. Non generare nuove chiavi o chiedere la passphrase in chat.
7. Aggiornare prove, checksum, continuità e roadmap; commit/push su main e verifica SHA remoto prima della consegna APK. Non committare APK, chiavi, contatti, cache o diagnostici privati.
8. Collaudo Galaxy richiesto: raccolta lenta oltre 25/120 secondi, stati durante raccolta, risposte di rete lente, perdita/ripresa rete, revoca/blocco/process death, nessun doppio avvio, pagine oltre la prima scadenza, hash e contatori finali. Solo dopo un'importazione reale validata dichiarare popolata la rubrica locale.

## Stato aperto preservato

Nessun APK .116, firma .116, deploy backend .116 o importazione reale dichiarati. Restano inoltre i collaudi fisici .115 del collegamento dopo uscita pagina/boot/Doze e mute nelle tre modalità, l'appunto regole sul telefono non confermato e gli altri sviluppi già elencati nella roadmap. Le associazioni dei progetti salvate nelle regole generali/appunti desktop sono un intervento separato, non una prova di salvataggio degli appunti Android.
