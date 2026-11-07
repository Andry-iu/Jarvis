# Handoff .119/141 — singolo numero diretto

## Completamento cloud — 7 novembre 2026

La richiesta «Intanto compilami l’APK firmato» ha avviato il rilascio dal mainbd7d5668c775f3f2bf4b06e223fd8fbd5801ba75, checkout pulito/fetch corrente, toolchain/cache/firma Jarvis riusati. Mirati DirectContactNumberTest e regressioni export/diagnostica/paginazione passati SDK34/36, poi suite completa670test/70suite/0failure/error/skipped, lint55warning/0errori/fatal e assemble riusciti.5suite Node DOM/audio,65test Worker e24test Python passati; guida --check34argomenti/13930parole. Nessuna correzione dei sorgenti ricevuti necessaria.

Stesso backend owner-private pubblicato: source dce04d87ccc047d4b3a99283519c4ccd59bb0272, deployment appgdep_6aef227566f481919bbaaac43de3a032 succeeded/has_mcp=true/env2, pairing/audience/segreti conservati e guida nativa riletta .119/141. Nuovo tool get_contact nel server; una chat con catalogo precedente può richiedere aggiornamento. Non è una prova sul telefono o una garanzia≤1s.

Unico APK Telefono .119/141 firmato permanentemente:90.019.583byte, SHA256 88c060a32943cec83d8fa6b1fb27394402c2aa64c6a45abe1672da3bbd26a722. Versione/ABI/CRC ZIP/v2/v3/certificato/zipalign e3ELF16KB verificati; temporanei privati rimossi, checksum in releases/0.5.119/SHA256SUMS e APK/report fuori Git. Nessuna API Google reale, query di contatti reali, chiamata o modifica Watch/Windows/Voice/cover per compilare.

Restano prove fisiche e residui sotto elencati. Il ponte MCP per comandare build/verifiche da altre chat è un lavoro separato approvato ma non ancora implementato: ultimo probe HTTPS dal cloud al Site rifiutato dal proxy403, dominio da consentire nelle impostazioni cloud. Questo rilascio non lo considera funzionante e non crea automaticamente un esecutore. Le sezioni successive conservano lo stato storico della preparazione, seguito da questo completamento cloud.

## Stato iniziale e ambito autorizzato

Via esplicito «Implementa anche questa modifica che la proviamo» e «No, tu implementala». Baseline remota aggiornata095dfe7; checkout di lavoro separato allineato prima delle modifiche. Regola generale concordata: meno transazioni verso il telefono per operazioni simili, senza allargare autorizzazioni o scegliere contatti ambigui. Sorgenti app/backend/importatore/guida implementati. Ultima release effettiva .118/140; .119 NON compilata, firmata, distribuita o provata sul Galaxy.

## Cosa cambia

- `jarviswake_get_contact`, APK minimo141, `name` XOR `contact_ref`, offset0 iniziale/continuazione20. Una ricerca live univoca restituisce direttamente un solo numero. Ambiguità/zero risultati producono solo contacts_found; l’utente sceglie poi riferimento. Ultima pagina singola non significa ricerca univoca. Nessuna scrittura Android o chiamata.
- Stessi consenso copia/READ_CONTACTS/sblocco, controllo del riferimento prima e dopo la raccolta. Un payload mode number da≤8192byte con byte/SHA256/Base64, verificato nel backend e nella utility; aggiornamento locale cifrato incrementale. `phonebook.py begin --direct` conserva UUID prima del tool; `accept` gestisce sia risposta immediata sia esito successivo senza perdere l’archivio precedente. Export massivo/nomi invariato e snapshot esistente conservato.
- Server: attesa breve1200ms soltanto per get_contact, poi pending sullo stesso UUID. Indice pending separato dallo storico con migrazione R2 paginata una tantum; nessuna scansione dello storico in dispatch ordinario. Pulizia privata tramite indice scadenze, limiti/claim/fingerprint/tombstone e job export senza timeout totale conservati. In caso di rollout su storico grande la prima migrazione può costare tempo: misurare separatamente, nessuna garanzia subsecondo.
- `timings.queue_ms` da creazione a claim (include consegna/rete claim, NON sola coda), `claimed_to_response_ms` da claim a ricezione della risposta (include raccolta e rete, NON sola CPU), `server_total_ms` somma su orologio server; `age_ms` età della risposta. Log telefono contact_export stage/elapsed_ms per polling/claim/progress/result/send/ack, senza contenuti privati. Confrontare durate monotone telefono senza sottrarre orologi di host diversi.
- Versione app e guida .119/141; nuovo argomento `numero_rapido`,34argomenti sincronizzati. Corretto anche il vecchio testo1MiB dello strumento export allo stato256KiB già rilasciato. Nessuna modifica a Voice, cover, wake, Watch, Windows o credenziali.

## Verifiche effettive fuori cloud

- `node --test --test-isolation=none phone-bridge/test/bridge.test.mjs`:65passati,0falliti. Include le57prove precedenti e8nuove su capability/risultato diretto, ambiguità/scelta, schema/encoding/hash privati, assenza scansioni storico300record, migrazione oltre100record e ripresa stesso UUID con indice mancante. Servizi e contatti solo sintetici.
- `python3 -m unittest discover -s scripts/tests -v`:24passati,0falliti. Include20precedenti e4nuove su aggiornamento cifrato atomico, ambiguità senza numeri, corruzione/hash/schema e correlazione della risposta/UUID.
- `python3 scripts/sync-guide.py --check`:34argomenti coerenti. `npm run build --prefix phone-bridge`: artefatto ESM verificato (non è compilazione Android). `git diff --check` pulito.
- `DirectContactNumberTest`:8metodi preparati per SDK34/36, provider sintetico reale search→resolve→collect→resolve, non eseguiti/compilati qui. Coprono numero/hash/no email/no scritture, scelta esplicita, ultima pagina, zero risultati, riga cambiata, consenso/permesso/revoca, sola lettura senza azioni e snapshot export conservato.

## Prossimo passo obbligatorio nel cloud Jarvis

In questa chat non sono esposti controllo/stato/esecuzione dell’ambiente Jarvis; i percorsi cloud non sono montati. Ricerca delle integrazioni non ha esposto un collegamento utilizzabile. Non ricreata toolchain su altra macchina e nessuna richiesta di segreti. Le regole correnti riservano anche le suite Gradle/JVM al cloud Jarvis.

1. Riaprire cloud Jarvis già configurato, verificare identità/observations/segreti senza stamparli e aggiornare `/workspace/Jarvis` da main pulito. Usare toolchain/cache `/workspace/.jarvis-cloud`, mai Actions o nuova chiave. Leggere AGENTS/CONTINUITY/VERIFICATION e questo handoff.
2. Eseguire prima DirectContactNumberTest e ContactExportDiagnosticsTest/BridgeLinkWorkerIntegrationTest/PhoneContactExportTest/ContactSearchPaginationTest su SDK34/36, poi suite/lint/build app complete. Comando consolidato: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PjarvisUnsigned=true -Pkotlin.compiler.execution.strategy=in-process -PjarvisTestJava=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -PjarvisTestSdks=34,36`, con JDK17 per Gradle e ambiente SDK/cache già predisposto. Correggere eventuali problemi mantenendo lo stesso gruppo autorizzato.
3. Ripetere backend/Python/guide check, firmare con chiave permanente e verificare certificato/CRC/ABI/16KB/versione .119/141. Salvare soli checksum e risultati in Git, fast-forward main verificato, APK fuori Git e consegna diretta secondo regole.
4. Pubblicare backend sullo STESSO Site/plugin/pairing già canonico in phone-bridge/.openai/hosting.json, senza creare duplicati, cambiare accessi o segreti. Verificare guida .119/141/discovery/get_contact e compatibilità vecchie APK. Eventuali approvazioni di pubblicazione restano quelle effettivamente richieste dalla piattaforma, non inventarle.
5. Sul Galaxy aggiornato: un nome univoco (1comando), omonimo/multinumero (scelta poi2°comando), timeout/rete lenta con stesso ID, consenso OFF/lock/riga modificata e export massivo conservato. Raccogliere diagnosi tecnica attuale e tempi prima/dopo separando attesa fra tool/parlato dal trasporto. Non promettere≤1s sulla sola simulazione.

Tutti gli sviluppi e collaudi già aperti della roadmap sono conservati nelle sezioni precedenti: boot/Doze/mute temporaneo .115, importazione reale/revoche/process death .116, indice nomi .117, paginazione/limiti/log .118, residui Voice/Google/Watch/Windows e appunto regole sul telefono non confermato. Nessun loro completamento è implicito in questa modifica. La regola generale è stata salvata nel file centrale JarvisRuntime_regole.txt versione38 senza modificare i contatti del telefono.
